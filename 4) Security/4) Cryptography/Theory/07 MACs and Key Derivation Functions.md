### MACs and Key Derivation Functions

--> A hash function alone proves nothing about *who* produced a message — anyone can compute SHA-256(message). To prove authenticity and integrity together, you need a secret shared between the two parties. That is what a MAC (Message Authentication Code) provides.
--> A KDF (Key Derivation Function) solves a different problem: turning a low-entropy secret (a password) or a raw shared secret (an ECDH output) into one or more cryptographically strong keys.
--> These two primitives are usually taught together because they both sit "between" a raw secret and a usable cryptographic key/tag.

## Why Not Just hash(key + message)?

--> The naive construction `tag = H(key || message)` looks secure but is broken for hash functions built on the Merkle-Damgard construction (MD5, SHA-1, SHA-256, SHA-512).
--> The vulnerability is called a **length extension attack**.

# Merkle-Damgard Internals

--> Merkle-Damgard hashes process the message in fixed-size blocks, updating an internal state (the "chaining value"). The final chaining value IS the hash output.
--> Critically, if you know `H(M)` and the length of `M`, you can compute `H(M || padding || M2)` for an attacker-chosen `M2` — WITHOUT knowing `M` itself. You just resume the compression function from the leaked internal state.

```python
# Demonstration: length extension attack against hash(key || message)
# using SHA-256. This is a conceptual reproduction — real attacks use
# tools like `hashpumpy` that replicate the exact MD padding.

import hashlib
import struct

def sha256_padding(msg_len: int) -> bytes:
    """Reconstruct the padding SHA-256 would have appended, given only
    the original message length (which an attacker can often guess or
    know from context, e.g. a fixed-format API request)."""
    pad = b"\x80"
    pad_len = (56 - (msg_len + 1) % 64) % 64
    pad += b"\x00" * pad_len
    pad += struct.pack(">Q", msg_len * 8)  # length in bits, big-endian
    return pad

# Server-side (victim) construction: tag = SHA256(secret_key || message)
secret_key = b"super-secret-16b"          # attacker does NOT know this
message = b"user=alice&admin=false"
tag = hashlib.sha256(secret_key + message).hexdigest()

# --- Attacker only knows: tag, message, and len(secret_key) (often
# guessable — e.g. "keys in this system are always 16 bytes") ---
known_key_len = len(secret_key)
forged_suffix = b"&admin=true"

# Attacker reconstructs the internal state SHA-256 was in right after
# processing (secret_key || message || padding), by seeding a new
# hashlib object with that intermediate digest state. Python's hashlib
# doesn't expose state injection directly, so real exploits use a pure
# python or C sha256 implementation that accepts (state, count). The
# logic below shows what that forged message becomes:
glue_padding = sha256_padding(known_key_len + len(message))
forged_message = message + glue_padding + forged_suffix

print("Forged message the server will see:", forged_message)
# Forged message: user=alice&admin=false\x80\x00...\x00<len-bits>&admin=true
# The attacker computes a valid tag for (secret_key || forged_message)
# without ever learning secret_key, using only `tag` as the resumed state.
```

--> The core lesson: **never build a MAC by concatenating a key and hashing once with a Merkle-Damgard hash.** Use HMAC, which is specifically designed to be immune to this.
--> SHA-3 (Keccak, sponge construction) is NOT vulnerable to length extension because of how the sponge absorbs/squeezes — but HMAC is still the standard, portable answer.

## HMAC

--> HMAC (Hash-based MAC, RFC 2104) wraps the hash function so the attacker never sees a hash output that corresponds to a simple prefix of the real internal computation.

```
HMAC(K, m) = H( (K' XOR opad) || H( (K' XOR ipad) || m ) )
```

--> `K'` is the key padded/hashed to the hash's block size. `ipad` = 0x36 repeated, `opad` = 0x5c repeated.
--> The nested structure means an attacker who extends the *inner* hash's output still has to pass it through an *entire second hash* keyed with a value they don't know (`K' XOR opad`) — length extension gives them nothing usable.

```python
import hmac
import hashlib

key = b"shared-secret-key"
message = b"transfer:100USD:to:bob"

# HMAC-SHA256
tag = hmac.new(key, message, hashlib.sha256).hexdigest()
print(tag)   # e.g. 3a1f9c... (64 hex chars)

# Verification MUST use constant-time comparison — never `==` on tags,
# since Python's == short-circuits on the first differing byte and
# leaks timing information (a timing side-channel).
received_tag = tag  # imagine this arrived over the wire
is_valid = hmac.compare_digest(tag, received_tag)
print(is_valid)   # True
```

--> `hmac.compare_digest` runs in time proportional to the length of the inputs, not to where the first mismatch occurs — this defeats a timing attack that guesses the tag byte-by-byte.

# Where HMAC Is Used

--> 1. API request signing (AWS SigV4, webhook signature headers like Stripe's `Stripe-Signature`).
--> 2. TLS record authentication in cipher suites that use encrypt-then-MAC.
--> 3. JWT `HS256` signing (symmetric — both issuer and verifier share the key, unlike `RS256`).
--> 4. As the PRF inside PBKDF2 and HKDF (see below) — HMAC's job there is not "authenticate a message" but "produce pseudorandom output deterministically from a key".

## Key Derivation Functions: The Core Distinction

--> There are two completely different problems that get called "KDF", and mixing them up is a common real-world vulnerability:

--> 1. **Password-based KDFs** (PBKDF2, scrypt, Argon2) — the input has LOW entropy (a human password, maybe 20-40 bits of real entropy). The attacker can brute-force offline. The KDF's job is to make each guess expensive.
--> 2. **Key-derivation KDFs** (HKDF) — the input already has HIGH entropy (e.g. a 256-bit ECDH shared secret). The KDF's job is not to slow down attackers — it's to convert a possibly biased/structured secret into a clean, uniformly random key of the right length, and to derive multiple independent keys from one secret.

--> Using HKDF on a password is a critical mistake — it does no work-factor scaling and an attacker can brute-force passwords through it in milliseconds. Using PBKDF2 to derive session keys from a DH secret works but wastes CPU and adds nothing security-wise since there's nothing to slow down.

## PBKDF2

--> PBKDF2 (Password-Based KDF 2, RFC 8018) repeatedly applies HMAC to the password and a salt, `iterations` times, to produce derived key material.
--> `DK = PBKDF2(PRF, password, salt, iterations, dklen)`
--> Cost knob: iteration count only. It is cheap in memory (a few hundred bytes), which means it is highly parallelizable on GPUs/ASICs — this is PBKDF2's main weakness against well-funded attackers.

```python
from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC
from cryptography.hazmat.primitives import hashes
import os

password = b"correct horse battery staple"
salt = os.urandom(16)          # unique per user, stored alongside the hash

kdf = PBKDF2HMAC(
    algorithm=hashes.SHA256(),
    length=32,                  # derive a 256-bit key
    salt=salt,
    iterations=600_000,         # OWASP 2023 recommendation for PBKDF2-SHA256
)
derived_key = kdf.derive(password)
print(derived_key.hex())

# Verifying a later login attempt:
kdf_verify = PBKDF2HMAC(
    algorithm=hashes.SHA256(),
    length=32,
    salt=salt,
    iterations=600_000,
)
try:
    kdf_verify.verify(password, derived_key)
    print("password correct")
except Exception:
    print("password incorrect")
```

--> Iteration count must be increased over time as hardware gets faster — treat it as a versioned parameter stored next to the hash (`algo=pbkdf2-sha256$iter=600000$salt=...$hash=...`), so old hashes can be re-derived with new parameters on next login (rehash-on-login pattern).

## scrypt

--> scrypt adds **memory-hardness**: it forces the computation to touch a large pseudorandom memory array, not just CPU cycles. This is specifically designed to kill the GPU/ASIC advantage, since custom hardware for memory-hard functions is far more expensive to build (bandwidth and die area scale with memory, not just gates).

```python
from cryptography.hazmat.primitives.kdf.scrypt import Scrypt
import os

password = b"correct horse battery staple"
salt = os.urandom(16)

kdf = Scrypt(
    salt=salt,
    length=32,
    n=2**17,     # CPU/memory cost parameter — must be a power of 2
    r=8,         # block size — tunes memory usage per iteration
    p=1,         # parallelization parameter
)
derived_key = kdf.derive(password)
print(derived_key.hex())

# Memory used is roughly 128 * n * r bytes = 128 * 131072 * 8 ≈ 128 MiB.
# That's the whole point: an attacker running billions of parallel
# guesses needs 128 MiB PER guess, which is expensive at scale.
```

--> `n`, `r`, `p` interact: `n` controls the number of sequential memory-hard rounds, `r` controls block size (and thus memory per round), `p` allows splitting work across independent parallel lanes without weakening the memory requirement per lane.
--> Practical guidance (OWASP): `n=2^17, r=8, p=1` gives ~128 MiB and ~1 second on typical hardware — tune to your server's login latency budget.

## Argon2

--> Argon2 is the winner of the 2015 Password Hashing Competition and is the current best-practice recommendation for password storage (OWASP's #1 choice, ahead of scrypt).
--> Three variants:
--> 1. **Argon2d** — maximizes resistance to GPU cracking by making memory access data-dependent. Vulnerable in theory to side-channel timing attacks (memory access pattern depends on the password), so avoid where an attacker can observe cache/timing behavior.
--> 2. **Argon2i** — data-independent memory access, immune to that side-channel class, but requires more passes to reach equivalent GPU resistance.
--> 3. **Argon2id** (recommended default) — hybrid: data-independent for the first pass, data-dependent afterward. Good side-channel resistance AND strong GPU resistance.

```python
from cryptography.hazmat.primitives.kdf.argon2 import Argon2id
import os

password = b"correct horse battery staple"
salt = os.urandom(16)

kdf = Argon2id(
    salt=salt,
    length=32,
    iterations=3,          # "time cost" — number of passes over memory
    lanes=4,                # parallelism / degree of threading
    memory_cost=64 * 1024,  # in KiB -> 64 MiB
)
derived_key = kdf.derive(password)
print(derived_key.hex())

# Note: cryptography's Argon2 support requires OpenSSL 3.2+ built with
# the argon2 provider. If unavailable, the `argon2-cffi` package is the
# common fallback and exposes an equivalent, ergonomic API:
#
#   from argon2 import PasswordHasher
#   ph = PasswordHasher(time_cost=3, memory_cost=65536, parallelism=4)
#   hash_ = ph.hash(password.decode())
#   ph.verify(hash_, password.decode())   # raises on mismatch
```

--> Rule of thumb for choosing among the three password KDFs today: prefer **Argon2id** for new systems. Use **scrypt** if Argon2 isn't available in your stack. Use **PBKDF2** only when required for compliance (e.g. FIPS 140-2/3 validated modules, which historically only certify PBKDF2) — and compensate with a very high iteration count.

## HKDF

--> HKDF (HMAC-based KDF, RFC 5869) is built for the *second* problem: expanding/extracting keying material from an already-strong secret. It is not a slow function — it's fast, because slowness isn't the goal.
--> Two stages:
--> 1. **Extract**: `PRK = HMAC(salt, input_key_material)` — condenses a possibly non-uniform secret (e.g. raw ECDH output, which is a curve point coordinate, not uniformly random bits) into a fixed-length pseudorandom key.
--> 2. **Expand**: `OKM = HMAC-based stream derived from PRK and an "info" context string` — stretches PRK into as many bytes as needed, optionally binding the output to a purpose via `info` so the same PRK produces different, independent keys for different uses (e.g. one key for encryption, a different one for MAC-ing, from the same DH secret).

```python
from cryptography.hazmat.primitives.kdf.hkdf import HKDF
from cryptography.hazmat.primitives import hashes
import os

shared_secret = os.urandom(32)   # e.g. output of an ECDH exchange

hkdf_encryption_key = HKDF(
    algorithm=hashes.SHA256(),
    length=32,
    salt=None,                        # optional; None uses a string of zeros
    info=b"handshake encryption key",  # context binding — see below
).derive(shared_secret)

hkdf_mac_key = HKDF(
    algorithm=hashes.SHA256(),
    length=32,
    salt=None,
    info=b"handshake mac key",         # different info -> independent key
).derive(shared_secret)

print(hkdf_encryption_key.hex())
print(hkdf_mac_key.hex())
# Two cryptographically independent keys from a single shared secret —
# this is exactly what TLS 1.3's key schedule does (separate keys for
# client/server, handshake/application traffic, derived via HKDF-Expand-Label).
```

--> `info` is not secret — it's a domain separator. Its purpose is to guarantee that even if two protocols accidentally derive from the same underlying secret, they end up with unrelated keys.
--> `salt` in HKDF is optional and, unlike password KDFs, does not need to be secret or even random — a fixed public salt is fine, since HKDF isn't defending against brute force, it's defending against structural bias in the input.

## Summary Table

| Function | Purpose | Slow by design? | Memory-hard? | Typical input |
|---|---|---|---|---|
| HMAC | Authenticate a message under a shared key | No | No | Any-length message + key |
| PBKDF2 | Stretch a password into a key | Yes (iterations) | No | Low-entropy password |
| scrypt | Stretch a password into a key | Yes | Yes | Low-entropy password |
| Argon2id | Stretch a password into a key | Yes | Yes (tunable) | Low-entropy password |
| HKDF | Expand/normalize a high-entropy secret | No | No | DH output, master secret |

--> Common real-world mistake to avoid: deriving a symmetric encryption key directly from a password by just hashing it once (`key = SHA256(password)`) and feeding that straight into AES. This skips salting AND skips the work factor — trivially brute-forced offline with a rainbow table or GPU cracking rig. Always route passwords through Argon2id/scrypt/PBKDF2 first.
