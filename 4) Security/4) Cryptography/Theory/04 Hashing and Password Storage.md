### Hashing and Password Storage

--> Hashing takes an input of ANY size and produces a fixed-size output (called a digest or hash) that acts like a unique fingerprint of that input.
--> Unlike encryption, hashing has no key and is designed to be a ONE-WAY street — you can go from data to digest, but never digest back to data.

## Properties of a Good Cryptographic Hash Function

1. Deterministic – The same input always produces the exact same output, every single time, on any machine.
2. Fixed-size output – SHA-256 always outputs 256 bits (32 bytes), whether you hash one character or an entire movie file.
3. Avalanche effect – Changing even a single bit of the input completely changes the output in an unpredictable way (roughly half the output bits flip).
4. One-way (pre-image resistant) – Given a digest, it should be computationally infeasible to find ANY input that produces it.
5. Collision resistant – It should be computationally infeasible to find two different inputs that produce the same digest.

```python
import hashlib

print(hashlib.sha256(b"hello").hexdigest())
# 2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824

print(hashlib.sha256(b"hallo").hexdigest())
# 92c9fe1b8fe3a3423e3aa8e0479ed83fb1e4a91b1ca6bb6a4a26c95d7fea9d9c
# ^ one letter changed ("e" -> "a") and the entire digest looks completely unrelated - the avalanche effect

print(len(hashlib.sha256(b"a").hexdigest()))            # 64 hex chars = 256 bits
print(len(hashlib.sha256(b"a" * 1_000_000).hexdigest())) # still 64 hex chars = 256 bits, no matter the input size
```

## MD5, SHA-1, SHA-256, SHA-3 Overview

1. MD5 – 128-bit output. Cryptographically BROKEN since 2004: practical collisions can be generated in seconds on consumer hardware. Still fine for non-security uses like checksums for accidental corruption, but never for security purposes.
2. SHA-1 – 160-bit output. Broken in practice since Google's 2017 "SHAttered" attack, which produced two different PDF files with the same SHA-1 hash. Deprecated everywhere, browsers reject SHA-1 TLS certificates.
3. SHA-256 (SHA-2 family) – 256-bit output. Currently considered secure, no practical collision or pre-image attacks known. Used in TLS, Bitcoin, code signing, Git object hashing (Git is migrating away from SHA-1 for this reason).
4. SHA-3 – Built on an entirely different internal design (Keccak sponge construction) than SHA-2, standardized in 2015 as a hedge in case SHA-2's design is ever broken in the future. Also secure today; used less widely than SHA-2 simply due to adoption inertia.

```python
import hashlib

data = b"integrity check"

print(hashlib.md5(data).hexdigest())     # 128-bit - broken, avoid for security
print(hashlib.sha1(data).hexdigest())    # 160-bit - broken, avoid for security
print(hashlib.sha256(data).hexdigest())  # 256-bit - currently secure
print(hashlib.sha3_256(data).hexdigest())# 256-bit, different internal design - currently secure
```

## Why MD5/SHA-1 Are Broken — Collisions and the Birthday Paradox

--> A "collision" is when two different inputs produce the same hash digest. Every hash function has infinite possible inputs mapping to a finite number of outputs, so collisions MUST exist mathematically — the question is only whether they're findable in practical time.
--> Naive intuition says: to find a collision in a hash with N possible outputs, you'd need to try roughly N inputs. This is wrong. The Birthday Paradox shows that in a room of just 23 people, there's already a 50% chance two of them share a birthday, even though there are 365 possible birthdays — because you're comparing every pair of people against every other pair, not just checking each individual against one fixed target.
--> Applied to hashing: to find ANY collision (not a collision with one specific target, just any two matching inputs), you only need roughly sqrt(N) attempts, not N attempts. For a 128-bit hash like MD5, that drops the required work from 2^128 down to about 2^64 — a massive, practically reachable reduction, which is why MD5 collisions are now generated in seconds.
--> SHA-1 (160-bit) needed real algorithmic weaknesses on top of the birthday bound to become practically breakable (Google's SHAttered attack used ~2^63 operations instead of the full 2^80 birthday bound), showing there was a genuine structural flaw discovered in the algorithm, not just brute force.
--> SHA-256 (256-bit) has a birthday bound of roughly 2^128 operations to find a collision — this number is so large it's considered physically infeasible with any foreseeable computing power, which is why it remains trusted.

## Salting and Rainbow Tables

--> A rainbow table is a precomputed lookup table mapping millions/billions of common passwords to their hash digests. If an attacker steals a database of UNSALTED password hashes, they just look each stolen hash up in the table and instantly recover the original password for anyone who used a common password.
--> A salt is a random value generated PER USER and stored alongside their hash (salt does not need to be secret). It gets combined with the password before hashing, so the exact same password produces a COMPLETELY DIFFERENT hash for every user — this destroys the rainbow table attack, because the attacker would need a separate precomputed table for every possible salt value, which is infeasible.

```python
import hashlib
import secrets

def hash_password_naive(password: str, salt: bytes = None):
    if salt is None:
        salt = secrets.token_bytes(16)         # random per-user salt
    digest = hashlib.sha256(salt + password.encode()).hexdigest()
    return salt, digest


salt_a, hash_a = hash_password_naive("password123")
salt_b, hash_b = hash_password_naive("password123")   # same password, different user

print(hash_a == hash_b)   # False - different random salts produce completely different hashes
```

## Why Fast General-Purpose Hashes Are Bad for Passwords

--> SHA-256 is DESIGNED to be extremely fast — billions of hashes per second on a modern GPU. That's great for checking file integrity, but disastrous for password hashing: an attacker who steals your salted-SHA256 hash database can still brute-force each user's password individually by trying billions of guesses per second per GPU, because computing SHA-256 costs almost nothing.
--> What passwords actually need is a hash function that is DELIBERATELY, TUNABLY SLOW — slow enough to be a mild inconvenience for a real login (milliseconds), but catastrophically expensive for an attacker trying billions of guesses.

## bcrypt / scrypt / Argon2 — The Correct Approach

--> These are "password hashing functions" (technically Key Derivation Functions), purpose-built with a tunable "cost factor" or "work factor" that controls how slow they are, and this cost can be increased over time as hardware gets faster.
1. bcrypt – Based on the Blowfish cipher, includes salting automatically, cost factor controls the number of internal rounds (2^cost). Widely supported, battle-tested since 1999.
2. scrypt – Also memory-hard (deliberately uses a lot of RAM), which makes cheap parallel GPU/ASIC cracking much harder than bcrypt.
3. Argon2 – Winner of the 2015 Password Hashing Competition, tunable across time cost, memory cost, AND parallelism. Currently the recommended default for new systems (OWASP's top recommendation).

```python
import bcrypt

password = b"password123"

# bcrypt.gensalt() picks a random salt AND embeds the cost factor into the result automatically
hashed = bcrypt.hashpw(password, bcrypt.gensalt(rounds=12))
print(hashed)
# b'$2b$12$KIXQ5Z3z1z8f9y7B8g2H8.somesaltandhashcombined...'
# format: $<algorithm version>$<cost factor>$<22-char salt><31-char hash>

# verifying login attempts - bcrypt extracts the salt+cost from the stored hash automatically
print(bcrypt.checkpw(b"password123", hashed))   # True
print(bcrypt.checkpw(b"wrongpassword", hashed)) # False
```

--> Increasing the cost factor makes brute forcing exponentially harder while only mildly slowing down real logins:

```python
import time
import bcrypt

for rounds in (10, 12, 14):
    start = time.perf_counter()
    bcrypt.hashpw(b"password123", bcrypt.gensalt(rounds=rounds))
    elapsed = time.perf_counter() - start
    print(f"rounds={rounds}: {elapsed:.3f}s")
    # rounds=10: ~0.06s
    # rounds=12: ~0.25s   <- each +1 round roughly DOUBLES the cost
    # rounds=14: ~1.0s
```

--> Practical rule for real applications: NEVER store passwords with plain SHA-256/MD5/SHA-1, even with a salt. Always use bcrypt, scrypt, or Argon2 (via libraries like `bcrypt`, `argon2-cffi`, or `passlib`), and tune the cost factor so hashing takes roughly 100-500ms on your production hardware.
