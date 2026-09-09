### Elliptic Curve Cryptography

--> RSA's security rests on integer factorization. ECC's security rests on a different hard problem — the discrete logarithm problem over points on an elliptic curve.
--> The payoff: ECC reaches the same security level as RSA with dramatically smaller keys, because the best known attacks on ECDLP are fully exponential, whereas the best known attacks on factoring (GNFS) are sub-exponential. A weaker attack means you need less key size margin.

## Security-Level Comparison

--> 1. RSA-3072 ≈ ECC-256 (both roughly "128-bit security").
--> 2. RSA-15360 ≈ ECC-521 (both roughly "256-bit security").
--> 3. Smaller keys mean smaller signatures, faster key generation, less bandwidth on the wire, and less CPU per handshake — this is why TLS, SSH, and most modern protocols default to ECC (ECDHE + ECDSA/EdDSA) over RSA.

## The Curve Equation

--> A (short Weierstrass form) elliptic curve over a prime field is the set of points `(x, y)` satisfying:

```
y² = x³ + ax + b   (mod p)
```

--> plus a special "point at infinity", denoted `O`, which acts as the identity element (like 0 in addition).
--> `a`, `b`, and the prime `p` are fixed public parameters that define the curve. Different standard curves (P-256, secp256k1, etc.) are just different choices of `a`, `b`, `p`, plus a chosen base point.
--> The curve is NOT the smooth continuous picture you'd draw in real-number geometry — cryptographic curves are defined over a *finite field* `GF(p)`, so the "curve" is really a finite, scattered set of `(x, y)` pairs where both coordinates are integers mod `p`. The geometric picture is only useful for building intuition about the group operation.

## Point Addition and the Group Law

--> Points on the curve form an abelian group under a geometric addition rule: draw a line through two points `P` and `Q`, find the third point where it intersects the curve, and reflect it across the x-axis — that's `P + Q`.
--> Special case: `P + P` (called **point doubling**) uses the tangent line at `P` instead of a line through two distinct points.
--> This group structure is what makes the algebra work — you can add points, and there's an identity (`O`) and inverses (`-P` = reflection of `P` across the x-axis), exactly like modular addition in RSA's setting, just with a geometric operation instead of multiplication mod n.

## Scalar Multiplication — The Hard Problem

--> Define `k * P` (scalar multiplication) as adding `P` to itself `k` times: `P + P + P + ... (k times)`.
--> This is computed efficiently via **double-and-add** (analogous to fast exponentiation `a^k mod n` via square-and-multiply) — so computing `k*P` given `k` and `P` is fast, `O(log k)` point operations.
--> The **Elliptic Curve Discrete Logarithm Problem (ECDLP)**: given `P` and `Q = k*P`, find `k`. This is believed to be computationally infeasible for well-chosen curves and large enough `k` — no known classical algorithm does better than roughly `O(sqrt(n))` (Pollard's rho), which is why curve order needs to be ~256 bits for 128-bit security (`sqrt(2^256) = 2^128`).
--> This asymmetry — easy forward (`k, P -> k*P`), hard backward (`k*P, P -> k`) — is the ECC analog of "easy to multiply primes, hard to factor the product" in RSA.

# Generator Point / Base Point

--> Every standard curve defines a public **base point** `G`, chosen to generate a large cyclic subgroup of prime order `n` (the curve's order).
--> A **private key** is simply a random integer `d` in `[1, n-1]`.
--> The corresponding **public key** is the point `Q = d * G` — a single scalar multiplication.
--> Recovering `d` from `Q` and `G` is exactly the ECDLP — infeasible for properly sized curves.

## Common Curves

--> 1. **P-256** (secp256r1, NIST curve) — the most widely deployed curve, used in TLS, most government/enterprise PKI. NIST curves have publicly unexplained random-looking parameters ("Nothing Up My Sleeve" claims are disputed by some cryptographers, fueling distrust after the Dual_EC_DRBG backdoor scandal).
--> 2. **secp256k1** — used by Bitcoin and Ethereum. Chosen (in part) because its parameters are more clearly derived from simple rules than the NIST curves, and it allows some efficiency optimizations (it has `a=0`, making point doubling cheaper).
--> 3. **Curve25519 / Ed25519** — a Montgomery-form curve (Curve25519, used for ECDH under the name X25519) and its twisted-Edwards counterpart (Ed25519, used for EdDSA signatures), designed by Daniel J. Bernstein specifically to avoid the implementation pitfalls of NIST curves: no timing side-channels from branchy point validation, no weak-randomness dependency in signing (EdDSA is fully deterministic, unlike ECDSA), and resistance to invalid-curve attacks by construction.
--> 4. **P-384 / P-521** — higher-security NIST curves, used where 128-bit security is deemed insufficient (e.g. TOP SECRET classification under NSA Suite B / CNSA).

## ECDH — Elliptic Curve Diffie-Hellman

--> Same idea as classic Diffie-Hellman, but the group is curve points instead of integers mod p.
--> Alice: private `d_A`, public `Q_A = d_A * G`.
--> Bob: private `d_B`, public `Q_B = d_B * G`.
--> Shared secret: Alice computes `d_A * Q_B`, Bob computes `d_B * Q_A`. Both equal `d_A * d_B * G` — the same point.
--> An eavesdropper sees `Q_A`, `Q_B`, and `G`, but recovering `d_A` or `d_B` requires solving ECDLP.
--> The shared point's x-coordinate is NOT used directly as a key — it goes through HKDF first (see note 07), because raw ECDH output is not uniformly random and using it directly as an AES key is a well-known mistake.

```python
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.kdf.hkdf import HKDF
from cryptography.hazmat.primitives import hashes

# --- Key generation ---
alice_private_key = ec.generate_private_key(ec.SECP256R1())
bob_private_key = ec.generate_private_key(ec.SECP256R1())

alice_public_key = alice_private_key.public_key()
bob_public_key = bob_private_key.public_key()

# --- Each side computes the shared point using their private key and
# the OTHER side's public key ---
alice_shared_secret = alice_private_key.exchange(ec.ECDH(), bob_public_key)
bob_shared_secret = bob_private_key.exchange(ec.ECDH(), alice_public_key)

assert alice_shared_secret == bob_shared_secret
print(alice_shared_secret.hex())   # raw shared secret, 32 bytes for P-256

# --- Never use the raw secret directly — derive a proper key via HKDF ---
derived_key = HKDF(
    algorithm=hashes.SHA256(),
    length=32,
    salt=None,
    info=b"ecdh session key",
).derive(alice_shared_secret)

print(derived_key.hex())   # this is what you'd feed into AES-GCM
```

--> In real protocols this is almost always **ECDHE** (Ephemeral ECDH) — a fresh keypair generated per session/handshake and discarded afterward. This gives **forward secrecy**: recording today's traffic and later stealing a long-term private key does not let an attacker decrypt past sessions, because the ephemeral keys used at the time are already gone.

## ECDSA — Elliptic Curve Digital Signature Algorithm

--> ECDSA signs a message hash using a private key; anyone with the public key can verify it.
--> Signing (informal): pick a random nonce `k`, compute point `k*G`, derive `r` from its x-coordinate, then compute `s` from `k`, the message hash, `r`, and the private key. The signature is the pair `(r, s)`.
--> **Critical failure mode**: reusing the same nonce `k` for two different messages signed with the same private key leaks the private key completely — simple algebra on the two signatures recovers `d`. This exact bug was used to extract the PS3's signing key (Sony reused `k`) and to steal funds from Android Bitcoin wallets with a broken RNG.
--> This is precisely why **EdDSA / Ed25519** is preferred where available: `k` is derived deterministically from the private key and the message (via a hash), so there is no RNG to fail and no nonce-reuse vulnerability class at all.

```python
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives import hashes
from cryptography.exceptions import InvalidSignature

private_key = ec.generate_private_key(ec.SECP256R1())
public_key = private_key.public_key()

message = b"Transfer 500 USD to account #4471"

# Sign — cryptography handles nonce generation internally per RFC 6979
# style deterministic-k in newer implementations, mitigating the classic
# nonce-reuse disaster for ECDSA specifically when supported.
signature = private_key.sign(message, ec.ECDSA(hashes.SHA256()))
print(signature.hex())

# Verify
try:
    public_key.verify(signature, message, ec.ECDSA(hashes.SHA256()))
    print("signature valid")
except InvalidSignature:
    print("signature INVALID")

# Tampering detection
try:
    public_key.verify(signature, b"Transfer 5000 USD to account #4471", ec.ECDSA(hashes.SHA256()))
except InvalidSignature:
    print("tampered message correctly rejected")
```

## Ed25519 in Practice

```python
from cryptography.hazmat.primitives.asymmetric.ed25519 import (
    Ed25519PrivateKey, Ed25519PublicKey,
)
from cryptography.exceptions import InvalidSignature

private_key = Ed25519PrivateKey.generate()
public_key = private_key.public_key()

message = b"ssh authentication challenge nonce=91af3c"

# Note: no hash algorithm parameter — Ed25519 hashes internally (SHA-512)
# and is fully deterministic. Same message + same key -> same signature,
# every time, with no external randomness required.
signature = private_key.sign(message)

try:
    public_key.verify(signature, message)
    print("valid")
except InvalidSignature:
    print("invalid")

# Serializing keys for storage/transport
from cryptography.hazmat.primitives import serialization

raw_private = private_key.private_bytes(
    encoding=serialization.Encoding.Raw,
    format=serialization.PrivateFormat.Raw,
    encryption_algorithm=serialization.NoEncryption(),
)
raw_public = public_key.public_bytes(
    encoding=serialization.Encoding.Raw,
    format=serialization.PublicFormat.Raw,
)
print(len(raw_private), len(raw_public))   # 32 32 — this is the whole point: tiny keys
```

## X25519 for Key Exchange

```python
from cryptography.hazmat.primitives.asymmetric.x25519 import X25519PrivateKey

alice_private = X25519PrivateKey.generate()
bob_private = X25519PrivateKey.generate()

alice_shared = alice_private.exchange(bob_private.public_key())
bob_shared = bob_private.exchange(alice_private.public_key())

assert alice_shared == bob_shared
print(alice_shared.hex())
# X25519 is the ECDH primitive underlying WireGuard, Signal, and TLS 1.3's
# preferred key exchange group (x25519).
```

## Curve Choice Cheat Sheet

| Use case | Recommended curve |
|---|---|
| General TLS/PKI interoperability today | P-256 (secp256r1) |
| New systems free of legacy constraints | X25519 (exchange) + Ed25519 (signatures) |
| Blockchain / Bitcoin-compatible signatures | secp256k1 |
| High-assurance / long-term government use | P-384 / P-521 |

## Pitfalls Specific to ECC

--> 1. **Invalid curve attacks** — sending a public key point that is NOT actually on the curve (or is on a different, weaker curve) to trick a naive implementation into leaking information through the arithmetic it performs. Always validate that a received point satisfies the curve equation before using it — modern libraries like `cryptography` do this for you, but hand-rolled ECC code frequently skips it.
--> 2. **Nonce reuse in ECDSA** (covered above) — use libraries with RFC 6979 deterministic nonces, or use Ed25519 instead entirely.
--> 3. **Small subgroup / twist attacks** — using a point of small order can leak bits of the private key; well-designed curves like Curve25519 clear the cofactor specifically to make this class of attack a non-issue by construction.
--> 4. **Weak randomness in key generation** — same failure mode as RSA: if `d` is generated with a broken/predictable RNG, the entire scheme collapses regardless of curve strength.
