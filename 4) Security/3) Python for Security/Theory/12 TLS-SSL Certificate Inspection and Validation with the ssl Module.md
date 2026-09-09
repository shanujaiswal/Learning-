### TLS/SSL Certificate Inspection and Validation with the `ssl` Module

--> `03 Working with Requests for Recon and Web Testing.md` used `requests` at a high level, where TLS verification happens transparently behind the scenes. This file drops down to Python's built-in `ssl` module to actually INSPECT what a server presents during the TLS handshake -- expiry dates, issuer, subject alternative names, and the chain of trust -- which is exactly what a certificate-auditing/recon script needs to check across many hosts.

## A minimal TLS connection and certificate fetch

```python
import ssl
import socket

def get_certificate(host, port=443, timeout=5):
    context = ssl.create_default_context()   # loads the OS's trusted CA bundle, same as a browser would
    with socket.create_connection((host, port), timeout=timeout) as sock:
        with context.wrap_socket(sock, server_hostname=host) as tls_sock:
            cert = tls_sock.getpeercert()
            return cert, tls_sock.version(), tls_sock.cipher()

cert, tls_version, cipher = get_certificate("example.com")
print(f"TLS version negotiated: {tls_version}")   # e.g. 'TLSv1.3'
print(f"Cipher suite: {cipher}")                    # e.g. ('TLS_AES_256_GCM_SHA384', 'TLSv1.3', 256)
print(cert["subject"])                              # e.g. (((‘commonName’, ‘example.com’),),)
print(cert["notAfter"])                             # e.g. 'Jan 15 23:59:59 2027 GMT' -- expiry
```

--> `server_hostname=host` in `wrap_socket()` is what enables SNI (Server Name Indication) -- telling the server which hostname's certificate you want during the handshake itself, essential for any server hosting multiple TLS sites on the same IP/port (virtual hosting) -- and it's also what `ssl` uses internally to verify the certificate's hostname actually matches what you asked for.

## Reading expiry and computing days remaining

```python
import datetime

def days_until_expiry(cert):
    expiry_str = cert["notAfter"]                                    # e.g. 'Jan 15 23:59:59 2027 GMT'
    expiry_date = datetime.datetime.strptime(expiry_str, "%b %d %H:%M:%S %Y %Z")
    return (expiry_date - datetime.datetime.utcnow()).days

remaining = days_until_expiry(cert)
if remaining < 30:
    print(f"[!] Certificate for this host expires in {remaining} days -- renew soon")
else:
    print(f"[+] Certificate valid for {remaining} more days")
```

--> This exact pattern -- fetch, parse `notAfter`, alert if under some threshold -- is the core of most "certificate expiry monitoring" tools used to avoid the extremely common and entirely preventable outage of "the website went down because nobody renewed the TLS cert in time."

## Subject Alternative Names (SANs)

--> Modern certificates are validated against the SAN list, not the legacy `commonName` field -- a certificate can legitimately cover many hostnames at once (e.g. `example.com` and `www.example.com` and `api.example.com` all on one cert) via its SAN extension.

```python
def get_san_list(cert):
    # SAN entries appear as a list of (type, value) tuples under 'subjectAltName'
    return [value for (entry_type, value) in cert.get("subjectAltName", []) if entry_type == "DNS"]

sans = get_san_list(cert)
print(f"This certificate also covers: {sans}")
```

## Inspecting the certificate chain and issuer

--> A server typically presents not just its own ("leaf") certificate but also intermediate certificates chaining up to a root CA that the client's trust store already trusts -- `getpeercert()` on a verified connection only returns the leaf; getting the FULL chain (useful for diagnosing "incomplete chain" misconfigurations, a common real-world TLS misconfig) requires `getpeercert(binary_form=True)` combined with lower-level APIs, or a dedicated library like `cryptography`'s `x509` module for full parsing.

```python
from cryptography import x509
from cryptography.hazmat.backends import default_backend

der_bytes = tls_sock.getpeercert(binary_form=True)      # raw DER-encoded certificate bytes
parsed = x509.load_der_x509_certificate(der_bytes, default_backend())

print(parsed.issuer.rfc4514_string())     # e.g. 'CN=R3,O=Let's Encrypt,C=US'
print(parsed.subject.rfc4514_string())    # e.g. 'CN=example.com'
print(parsed.not_valid_after)             # a proper datetime object, easier to work with than the raw string
```

## Deliberately connecting to inspect an INVALID certificate

--> Security tooling often needs to inspect a certificate that WON'T validate (self-signed, expired, wrong hostname) -- e.g. auditing internal infrastructure that intentionally uses self-signed certs, or checking exactly WHY a given host's TLS setup fails validation. `ssl.create_default_context()` refuses this by raising `ssl.SSLCertVerificationError`; you must deliberately construct a non-verifying context to get past that and actually look.

```python
def get_certificate_unverified(host, port=443, timeout=5):
    """Only for INSPECTING a cert you already know may be invalid -- never use this context
    for anything that then trusts/transmits real data over the connection."""
    context = ssl._create_unverified_context()
    with socket.create_connection((host, port), timeout=timeout) as sock:
        with context.wrap_socket(sock, server_hostname=host) as tls_sock:
            return tls_sock.getpeercert(binary_form=True)   # note: binary_form=True works even unverified;
                                                              # the dict form returned by verified connections
                                                              # is NOT populated on an unverified handshake
```

--> **This is the single most dangerous line in this file if misused: `ssl._create_unverified_context()` (or the equivalent `verify=False` pattern from the `requests` file) disables TLS's core security guarantee -- protection against a man-in-the-middle actively substituting their own certificate.** It's appropriate ONLY for a diagnostic script deliberately inspecting a known/expected-to-be-broken certificate, run against a target you understand -- never for any code path that goes on to trust the connection for real data (login credentials, sensitive API calls). Leaving `verify=False`-style code in production tooling by accident is a genuinely common and serious real-world vulnerability class.

## Worked example: a bulk certificate-expiry auditor

```python
import ssl
import socket
import datetime

def check_host(host, port=443, timeout=5, warn_days=30):
    try:
        context = ssl.create_default_context()
        with socket.create_connection((host, port), timeout=timeout) as sock:
            with context.wrap_socket(sock, server_hostname=host) as tls_sock:
                cert = tls_sock.getpeercert()
    except ssl.SSLCertVerificationError as e:
        return {"host": host, "status": "INVALID", "detail": str(e)}
    except (socket.timeout, ConnectionRefusedError, socket.gaierror) as e:
        return {"host": host, "status": "UNREACHABLE", "detail": str(e)}

    expiry = datetime.datetime.strptime(cert["notAfter"], "%b %d %H:%M:%S %Y %Z")
    days_left = (expiry - datetime.datetime.utcnow()).days
    status = "EXPIRING SOON" if days_left < warn_days else "OK"
    return {"host": host, "status": status, "days_left": days_left, "issuer": cert.get("issuer")}

if __name__ == "__main__":
    hosts = ["example.com", "expired.badssl.com", "self-signed.badssl.com"]
    for h in hosts:
        print(check_host(h))
    # {'host': 'example.com', 'status': 'OK', 'days_left': 210, 'issuer': (...)}
    # {'host': 'expired.badssl.com', 'status': 'INVALID', 'detail': '... certificate has expired ...'}
    # {'host': 'self-signed.badssl.com', 'status': 'INVALID', 'detail': '... self-signed certificate ...'}
```

--> `badssl.com`'s subdomains are deliberately, publicly, permanently misconfigured for exactly this kind of testing -- a legitimate, sanctioned target for practicing certificate validation edge cases without needing your own lab infrastructure for it.

## Cross-references

--> Extends the TLS/proxy material touched on in `03 Working with Requests for Recon and Web Testing.md` (where `verify=False` first appeared, in the Burp/mitmproxy context) and the signature/public-key concepts from `11 Symmetric-Asymmetric Cryptography and JWTs in Python.md` -- a TLS certificate is, at its core, a public key plus identity information signed by a CA's private key, exactly the signing/verification pattern demonstrated there with RSA directly.
