# PKI Certificate Authority Simulator

Issuing, validating, and revoking **real X.509 certificates** with Python's
`cryptography` library -- no toy/fake certificate structures. Every certificate
produced by this project is a genuine, ASN.1-encoded, RSA-signed X.509 v3
certificate that `openssl x509 -text` (or a browser) could parse and verify.

## Real-world scenario

A small company runs its own internal Certificate Authority (a private PKI)
instead of buying certs from a public CA, because its services
(`api.internal.warpx.local`, `db-admin.internal.warpx.local`, etc.) never
touch the public internet and have no public DNS a public CA like Let's
Encrypt could validate. This is exactly what companies use **step-ca**,
**HashiCorp Vault's PKI secrets engine**, or **Microsoft AD Certificate
Services** for in production.

The simulator walks through the full lifecycle:

1. Stand up a root CA (real self-signed X.509 certificate).
2. Issue leaf certificates for two internal hostnames, signed by the CA.
3. Validate both -- full chain-of-trust checks pass.
4. An attacker presents a **self-signed impersonation certificate** for one of
   the same hostnames -- rejected, because it was never touched by the CA's
   private key.
5. An **expired** certificate is presented -- rejected, because "now" falls
   outside `[notBefore, notAfter]`.
6. One legitimate certificate is **revoked** (simulated key compromise) and
   the CA publishes an updated, signed CRL. The revoked certificate is now
   rejected via the CRL check, while the *other* legitimate certificate --
   untouched by the revocation -- still validates successfully.

## Architecture

| Module | Role | Real-world equivalent |
|---|---|---|
| `certificate_authority.py` | Generates the CA's RSA keypair + self-signed root cert; issues leaf certs signed by the CA with real validity windows, SAN, key usage, and AKI/SKI extensions | An internal PKI CA -- e.g. **step-ca**, **HashiCorp Vault PKI**, or **AD Certificate Services** |
| `chain_validator.py` | Verifies a presented cert's signature against the CA's public key, checks the validity period, matches the hostname against the SAN, and checks the CRL | A TLS client's certificate-chain verifier -- what a browser or an mTLS-enabled service does during a handshake |
| `revocation_registry.py` | Tracks revoked serial numbers and builds a real, CA-signed `x509.CertificateRevocationList` | A CA's published **CRL** (or, in modern deployments, an **OCSP responder**) |
| `main.py` | Runs the end-to-end story and asserts every scenario produces the expected outcome | An integration test / demo harness for the PKI |

## Run it

Requires Python 3.10+ and the `cryptography` package (already installed in
this environment; otherwise `pip install cryptography`).

```bash
python main.py
```

## Verified result

Actually executed via `python main.py` on 2026-08-17. Output (trimmed to the
key lines; full per-check breakdown is printed by the script):

```
STEP 3 -- Validate both legitimate certificates (expect ACCEPTED)
[ACCEPTED] certificate for 'api.internal.warpx.local'          -- all 4 checks PASS
[ACCEPTED] certificate for 'db-admin.internal.warpx.local'      -- all 4 checks PASS

STEP 4 -- Attacker presents a SELF-SIGNED impersonation cert (expect REJECTED)
[REJECTED] certificate for 'api.internal.warpx.local'
    - signature verifies against CA public key: FAIL
    reason(s): certificate was NOT signed by the trusted CA (possible impersonation)

STEP 5 -- Present an already-EXPIRED certificate (expect REJECTED)
[REJECTED] certificate for 'legacy-reporting.internal.warpx.local'
    - validity period (not expired / not premature): FAIL
    reason(s): certificate expired on 2026-08-16T05:20:55+00:00 (now=2026-08-17T05:20:55+00:00)

STEP 6 -- Revoke the 'api' cert (simulated key compromise), re-check via CRL
CA published a new signed CRL with 1 revoked entry
Re-validating the now-REVOKED 'api' cert (expect REJECTED):
[REJECTED] certificate for 'api.internal.warpx.local'
    - not present on CRL (not revoked): FAIL
    reason(s): certificate serial ... is REVOKED (reason: key compromise)

Re-validating the STILL-LEGITIMATE 'db-admin' cert (expect ACCEPTED):
[ACCEPTED] certificate for 'db-admin.internal.warpx.local'      -- all 4 checks PASS

ALL SCENARIOS VERIFIED
1. Legitimate api cert:            ACCEPTED
2. Legitimate db-admin cert:       ACCEPTED
3. Self-signed impersonation cert: REJECTED (signature does not verify)
4. Expired cert:                   REJECTED (validity period check)
5. Revoked api cert:               REJECTED (CRL check)
6. db-admin cert after revocation: ACCEPTED (unaffected by unrelated revocation)
```

All in-script `assert` statements also pass (the script exits 0), confirming
each scenario's outcome programmatically, not just via printed text.

## Things to try changing

- **Switch RSA for EC keys**: swap `rsa.generate_private_key(...)` for
  `ec.generate_private_key(ec.SECP256R1())` in `certificate_authority.py` and
  update `chain_validator.py`'s `_verify_signature` to use
  `ec.ECDSA(certificate.signature_hash_algorithm)` instead of
  `padding.PKCS1v15()` -- compare cert sizes and signing speed.
- **Add an intermediate CA**: insert an intermediate between root and leaf
  (as shown in the theory doc's worked example) and extend
  `chain_validator.py` to walk a multi-hop chain instead of a single
  CA-to-leaf hop.
- **Shorten leaf validity to hours**: set `LEAF_VALIDITY_DAYS` to a fraction
  of a day to see the "short-lived certs reduce reliance on revocation"
  trend from the theory doc in action.
- **Simulate OCSP instead of CRL**: replace the CRL-membership check in
  `chain_validator.py` with a function call that mimics a live OCSP query
  (`good` / `revoked` / `unknown`), and add a soft-fail vs hard-fail mode.
- **Add Key Usage / Extended Key Usage enforcement**: reject a leaf cert
  presented for TLS server auth if it lacks `serverAuth` in its
  `ExtendedKeyUsage`, or reject a non-CA cert that tries to sign other certs.
- **Corrupt a byte of a legitimate cert's DER encoding** before validating it,
  to see the signature check fail for a different reason than impersonation
  (bit-flip / tampering detection).
