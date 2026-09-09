### PKI and Digital Certificates Deep Dive

--> Public-key cryptography solves confidentiality and authentication in theory, but leaves one huge practical gap: how do you know a public key actually belongs to the entity you think it does?
--> Public Key Infrastructure (PKI) is the system of certificates, Certificate Authorities (CAs), and trust chains that binds identities to public keys in a way relying parties can verify without a pre-existing direct relationship.

## X.509 Certificate Structure

--> An X.509 certificate is a signed data structure. The signature is what makes it trustworthy — it's issued by a CA that vouches for the binding between the subject and the public key.
--> Core fields:
--> 1. **Version** — almost always v3 today (adds the extensions mechanism).
--> 2. **Serial number** — unique identifier assigned by the issuing CA; used to reference the cert in revocation lists.
--> 3. **Signature algorithm** — e.g. `sha256WithRSAEncryption` or `ecdsa-with-SHA256` — how the CA signed this cert.
--> 4. **Issuer** — Distinguished Name (DN) of the CA that signed this certificate (e.g. `CN=DigiCert Global Root CA, O=DigiCert Inc, C=US`).
--> 5. **Validity** — `notBefore` / `notAfter` timestamps. A cert outside this window must be rejected regardless of signature validity.
--> 6. **Subject** — DN of the entity the cert identifies (e.g. `CN=example.com`).
--> 7. **Subject Public Key Info** — the algorithm and the actual public key bytes.
--> 8. **Extensions (v3)** — the extensible metadata block, includes:
-->    - `Subject Alternative Name (SAN)` — modern browsers ignore the `CN` field for hostname matching and require SAN entries (DNS names, IPs) instead.
-->    - `Key Usage` / `Extended Key Usage` — constrains what the key may be used for (`digitalSignature`, `keyEncipherment`, `serverAuth`, `clientAuth`, `codeSigning`, etc).
-->    - `Basic Constraints` — marks whether this cert `CA:TRUE` (can sign other certs) or `CA:FALSE` (leaf/end-entity cert). A leaf cert with `CA:TRUE` missing/false cannot be abused to mint further certs even if its private key leaks in some misconfigurations.
-->    - `Authority Key Identifier` / `Subject Key Identifier` — used to efficiently link a cert to its issuer's specific key when the issuer has rotated keys.
-->    - `CRL Distribution Points` / `Authority Information Access (AIA)` — where to fetch revocation info (CRL URL) and OCSP responder URL.
--> 9. **Signature** — the CA's signature over a hash of everything above, using the CA's private key.

## Chain of Trust

--> A certificate chain is: `Root CA -> Intermediate CA(s) -> Leaf (end-entity) certificate`.
--> 1. **Root CA** — self-signed, its public key is pre-installed in OS/browser trust stores. Root keys are kept offline (air-gapped HSMs) precisely because compromise would be catastrophic and unrecoverable without a trust-store update across the entire internet.
--> 2. **Intermediate CA** — signed BY the root, used for day-to-day issuance. This indirection lets a CA revoke/rotate an intermediate without touching the root, and lets the root stay offline almost permanently.
--> 3. **Leaf certificate** — the actual server/client cert, signed by an intermediate.
--> --> Why not have servers use root-signed certs directly? Because the root's signing key would then have to be online and exposed to routine issuance, dramatically increasing its attack surface. The intermediate absorbs that risk.

# How a Browser Validates a Chain

--> 1. Server presents its leaf cert plus the intermediate chain (usually sent together in the TLS handshake, minus the root, which the client already has).
--> 2. Client checks: does the leaf's `issuer` field match the intermediate's `subject`? Does the intermediate's signature verify correctly using the intermediate's stated public key against what the root claims to have signed?
--> 3. Walk up the chain until reaching a cert whose issuer is a trust-anchor already present in the local trust store, and whose self-signature validates.
--> 4. Check validity windows on every cert in the chain — ALL must currently be valid, not just the leaf.
--> 5. Check the leaf's SAN against the hostname being connected to.
--> 6. Check revocation status (OCSP/CRL — see below) for every cert in the chain where feasible.
--> 7. Check key usage / extended key usage constraints permit the operation being performed (e.g. leaf must have `serverAuth` for a TLS server connection).
--> --> If any step fails, the connection is aborted (or a security warning is shown) — there is no "partial trust."

## Revocation: OCSP vs CRL

--> Certificates can become untrustworthy before `notAfter` (private key compromise, CA mis-issuance, business closure). Revocation is how a CA says "this cert, though not yet expired, must no longer be trusted."
--> 1. **CRL (Certificate Revocation List)** — a CA-signed list of all serial numbers revoked, published periodically. Client downloads the whole list and checks membership. Downsides: can grow huge, staleness between publications, bandwidth cost of fetching a large list for a single lookup.
--> 2. **OCSP (Online Certificate Status Protocol)** — client queries the CA's OCSP responder in real time with just the cert's serial number, gets back "good / revoked / unknown". Downsides: leaks browsing metadata to the CA (a privacy concern), adds a network round-trip and latency, and if the OCSP responder is unreachable most clients "soft-fail" (treat it as valid anyway) — which defeats the purpose against a well-resourced attacker who can also block the OCSP request.
--> 3. **OCSP Stapling** — the server itself periodically fetches a signed OCSP response from the CA and "staples" it to the TLS handshake. This removes the client's separate network round-trip AND the privacy leak (the CA sees the server's requests, not the individual client's), and is the modern preferred approach.
--> 4. **CRLite / short-lived certs** — the emerging trend (favored by Let's Encrypt-style automation) is to issue certs with very short validity (days, not years) so that revocation infrastructure matters less — an expired-and-not-renewed cert achieves a similar effect to revocation, just slower.

## Self-Signed vs CA-Signed

--> A self-signed cert has `issuer == subject` and is signed with its own private key — there's no third party vouching for it.
--> Use cases: local development/testing, internal-only services with manually distributed trust (you install the cert into your trust store out-of-band), or as the ROOT of your own private PKI (every root CA cert IS self-signed by definition — the "trust" comes from being deliberately installed into a trust store, not from being CA-signed by someone else).
--> Never use a self-signed cert for a public-facing production service — there is no automated way for a random visitor's browser to establish that it should be trusted, hence the "Not Secure" / big red warning browsers show.

## Mutual TLS (mTLS)

--> Standard TLS only authenticates the SERVER to the client — the client verifies the server's cert chain, but the server has no cryptographic proof of who the client is (that's handled at the app layer, e.g. passwords/API keys, if at all).
--> mTLS adds client authentication: during the handshake, the server also requests a certificate from the client, and the client must present one signed by a CA the server trusts, plus prove possession of the matching private key (by signing part of the handshake transcript).
--> Common use cases: service-to-service auth inside a zero-trust internal network (e.g. Istio/service mesh sidecars), B2B API integrations where both parties issue each other client certs, banking/payment network backends.
--> Tradeoff vs API keys/OAuth: mTLS binds authentication to the transport layer itself and is strong against credential replay (the private key never crosses the wire), but complicates cert lifecycle management — every client needs a cert, renewal, and revocation pipeline, which is significant operational overhead compared to rotating a bearer token.

## Worked Example: Building a Mini CA and Issuing a Leaf Certificate

```python
import datetime
from cryptography import x509
from cryptography.x509.oid import NameOID, ExtendedKeyUsageOID
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec

def make_key():
    return ec.generate_private_key(ec.SECP256R1())

# ---------- 1. Create a self-signed ROOT CA ----------
root_key = make_key()
root_name = x509.Name([
    x509.NameAttribute(NameOID.COUNTRY_NAME, "US"),
    x509.NameAttribute(NameOID.ORGANIZATION_NAME, "Example Root CA Org"),
    x509.NameAttribute(NameOID.COMMON_NAME, "Example Root CA"),
])

root_cert = (
    x509.CertificateBuilder()
    .subject_name(root_name)
    .issuer_name(root_name)                      # self-signed: issuer == subject
    .public_key(root_key.public_key())
    .serial_number(x509.random_serial_number())
    .not_valid_before(datetime.datetime.now(datetime.timezone.utc))
    .not_valid_after(datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(days=3650))
    .add_extension(x509.BasicConstraints(ca=True, path_length=1), critical=True)
    .add_extension(x509.KeyUsage(
        digital_signature=False, content_commitment=False, key_encipherment=False,
        data_encipherment=False, key_agreement=False, key_cert_sign=True,
        crl_sign=True, encipher_only=False, decipher_only=False,
    ), critical=True)
    .add_extension(x509.SubjectKeyIdentifier.from_public_key(root_key.public_key()), critical=False)
    .sign(root_key, hashes.SHA256())              # signed with its OWN key
)

# ---------- 2. Create an INTERMEDIATE CA, signed by the root ----------
intermediate_key = make_key()
intermediate_name = x509.Name([
    x509.NameAttribute(NameOID.COUNTRY_NAME, "US"),
    x509.NameAttribute(NameOID.ORGANIZATION_NAME, "Example Intermediate CA"),
    x509.NameAttribute(NameOID.COMMON_NAME, "Example Intermediate CA"),
])

intermediate_cert = (
    x509.CertificateBuilder()
    .subject_name(intermediate_name)
    .issuer_name(root_name)                       # issued BY the root
    .public_key(intermediate_key.public_key())
    .serial_number(x509.random_serial_number())
    .not_valid_before(datetime.datetime.now(datetime.timezone.utc))
    .not_valid_after(datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(days=1825))
    .add_extension(x509.BasicConstraints(ca=True, path_length=0), critical=True)
    .add_extension(x509.KeyUsage(
        digital_signature=False, content_commitment=False, key_encipherment=False,
        data_encipherment=False, key_agreement=False, key_cert_sign=True,
        crl_sign=True, encipher_only=False, decipher_only=False,
    ), critical=True)
    .add_extension(
        x509.AuthorityKeyIdentifier.from_issuer_public_key(root_key.public_key()),
        critical=False,
    )
    .sign(root_key, hashes.SHA256())               # signed with the ROOT's key
)

# ---------- 3. Create a LEAF (server) certificate, signed by the intermediate ----------
leaf_key = make_key()
leaf_name = x509.Name([
    x509.NameAttribute(NameOID.COMMON_NAME, "api.example.com"),
])

leaf_cert = (
    x509.CertificateBuilder()
    .subject_name(leaf_name)
    .issuer_name(intermediate_name)                # issued BY the intermediate
    .public_key(leaf_key.public_key())
    .serial_number(x509.random_serial_number())
    .not_valid_before(datetime.datetime.now(datetime.timezone.utc))
    .not_valid_after(datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(days=90))
    .add_extension(x509.BasicConstraints(ca=False, path_length=None), critical=True)
    .add_extension(
        x509.SubjectAlternativeName([x509.DNSName("api.example.com")]),
        critical=False,
    )
    .add_extension(
        x509.KeyUsage(
            digital_signature=True, content_commitment=False, key_encipherment=False,
            data_encipherment=False, key_agreement=True, key_cert_sign=False,
            crl_sign=False, encipher_only=False, decipher_only=False,
        ),
        critical=True,
    )
    .add_extension(
        x509.ExtendedKeyUsage([ExtendedKeyUsageOID.SERVER_AUTH]),
        critical=False,
    )
    .sign(intermediate_key, hashes.SHA256())        # signed with the INTERMEDIATE's key
)

# ---------- 4. Verify the chain manually ----------
# In real code you'd use a library like `cryptography`'s own verification
# APIs or `pyOpenSSL`/`certvalidator`; shown here is the core primitive —
# checking that each cert's signature verifies under its issuer's public key.
root_key.public_key().verify(
    root_cert.signature, root_cert.tbs_certificate_bytes,
    ec.ECDSA(root_cert.signature_hash_algorithm),
)
root_key.public_key().verify(
    intermediate_cert.signature, intermediate_cert.tbs_certificate_bytes,
    ec.ECDSA(intermediate_cert.signature_hash_algorithm),
)
intermediate_key.public_key().verify(
    leaf_cert.signature, leaf_cert.tbs_certificate_bytes,
    ec.ECDSA(leaf_cert.signature_hash_algorithm),
)
print("Full chain verified: root -> intermediate -> leaf")

# ---------- 5. Export as PEM (what you'd actually deploy) ----------
leaf_pem = leaf_cert.public_bytes(serialization.Encoding.PEM)
chain_pem = intermediate_cert.public_bytes(serialization.Encoding.PEM) + root_cert.public_bytes(serialization.Encoding.PEM)
leaf_key_pem = leaf_key.private_bytes(
    encoding=serialization.Encoding.PEM,
    format=serialization.PrivateFormat.PKCS8,
    encryption_algorithm=serialization.NoEncryption(),
)
print(leaf_pem.decode()[:60], "...")
# -----BEGIN CERTIFICATE----- ...
```

--> Note the `path_length` field on `BasicConstraints`: it caps how many further intermediate CAs may appear below this one in a chain. The root sets `path_length=1` (allow exactly one intermediate below it), the intermediate sets `path_length=0` (it may only sign leaf certs, not further CAs) — this is a real, enforced constraint that limits blast radius if an intermediate is compromised.

## Quick Self-Signed Cert (Dev/Test Only)

```python
import datetime
from cryptography import x509
from cryptography.x509.oid import NameOID
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import rsa

key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "localhost")])

cert = (
    x509.CertificateBuilder()
    .subject_name(name)
    .issuer_name(name)
    .public_key(key.public_key())
    .serial_number(x509.random_serial_number())
    .not_valid_before(datetime.datetime.now(datetime.timezone.utc))
    .not_valid_after(datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(days=365))
    .add_extension(x509.SubjectAlternativeName([x509.DNSName("localhost")]), critical=False)
    .sign(key, hashes.SHA256())
)
print("self-signed dev cert created — browsers will still warn, by design")
```
