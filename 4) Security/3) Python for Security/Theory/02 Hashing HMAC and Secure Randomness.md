### Hashing, HMAC, and Secure Randomness

--> Security scripting constantly needs three primitives: hashing (fingerprinting data), HMAC (proving a message wasn't tampered with, given a shared secret), and secure randomness (tokens, salts, session IDs).
--> Python's standard library covers all three with `hashlib`, `hmac`, and `secrets`.

## `hashlib` — one-way fingerprints

--> A hash function takes arbitrary-length input and produces a fixed-length digest. Good cryptographic hashes are one-way (can't reverse) and collision-resistant (hard to find two inputs with the same output).

```python
import hashlib

data = b"attack at dawn"

print(hashlib.md5(data).hexdigest())     # 5-a... 128-bit digest, 32 hex chars
print(hashlib.sha1(data).hexdigest())    # 160-bit digest, 40 hex chars
print(hashlib.sha256(data).hexdigest())  # 256-bit digest, 64 hex chars
print(hashlib.sha3_256(data).hexdigest())# SHA-3 family, different internal design
```

--> Hashing large data (e.g. a file) incrementally, without loading it all into memory:

```python
import hashlib

def sha256_of_file(path, chunk_size=8192):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        while chunk := f.read(chunk_size):
            h.update(chunk)
    return h.hexdigest()

print(sha256_of_file("Theory/01 Networking Basics for Python Security Scripts.md"))
```

## Why MD5 and SHA-1 are broken for security

--> Both are cryptographically broken, but "broken" means different things depending on use case:

1. MD5 – Practical collision attacks exist (two different inputs producing the same hash) since 2004. Trivially exploitable to forge signed certificates, malware hash mismatches, etc. Never use for passwords, signatures, or integrity checks against an adversary.
2. SHA-1 – Google/CWI demonstrated a practical collision in 2017 (the "SHAttered" attack). Deprecated by browsers and CAs for certificates. Still common in legacy Git internals (not a security control there) but should not be used for anything security-sensitive.

--> Both remain acceptable for **non-adversarial** use — e.g. deduplicating files, checksums against accidental corruption — because those threat models don't involve someone deliberately crafting a collision.
--> For anything security-relevant (integrity against a malicious actor, digital signatures, password storage), use SHA-256/SHA-3 or better, and for passwords specifically use a dedicated password-hashing algorithm (see below).

## `hmac` — keyed message authentication

--> A plain hash proves nothing about *who* sent a message — anyone can compute `sha256(message)`. HMAC (Hash-based Message Authentication Code) combines a hash function with a secret key, so only someone who knows the key can produce a valid tag.
--> Used for API request signing (AWS, webhooks), verifying tokens, and tamper-proofing messages between two parties who share a secret.

```python
import hmac
import hashlib

secret_key = b"super-secret-shared-key"
message = b"transfer $100 to account 42"

tag = hmac.new(secret_key, message, hashlib.sha256).hexdigest()
print(tag)   # e.g. 3f9c1a... deterministic given the same key+message
```

--> Verifying an incoming message + tag (e.g. a webhook payload with an `X-Signature` header):

```python
import hmac
import hashlib

def verify_signature(secret_key, message, received_tag):
    expected_tag = hmac.new(secret_key, message, hashlib.sha256).hexdigest()
    # hmac.compare_digest prevents timing attacks (see below)
    return hmac.compare_digest(expected_tag, received_tag)

secret = b"webhook-secret"
body = b'{"event": "payment.success"}'
incoming_signature = hmac.new(secret, body, hashlib.sha256).hexdigest()

print(verify_signature(secret, body, incoming_signature))  # True
print(verify_signature(secret, body, "tampered" + "0" * 56))  # False
```

--> Never compare two secrets/tags with `==`. String comparison in Python short-circuits on the first mismatched byte, which leaks timing information an attacker can use to guess the correct value byte-by-byte (a timing attack). Always use `hmac.compare_digest()`.

## `secrets` — cryptographically secure randomness

--> Python's default `random` module is a Mersenne Twister PRNG. It is fast and great for simulations/games, but it is **not** cryptographically secure — its internal state can be reconstructed from enough observed outputs, letting an attacker predict future "random" values (this has broken real token/password-reset systems).
--> The `secrets` module (Python 3.6+) wraps the OS's cryptographically secure random source (`os.urandom` under the hood) and should be used for anything security-sensitive: tokens, session IDs, password reset codes, API keys.

```python
import secrets

# A random URL-safe token, e.g. for password reset links
reset_token = secrets.token_urlsafe(32)
print(reset_token)   # e.g. "kR3f9...longrandomstring"

# A random hex string, e.g. for an API key
api_key = secrets.token_hex(16)
print(api_key)        # 32 hex characters = 16 bytes of entropy

# A random integer in range, e.g. a 6-digit OTP
otp = secrets.randbelow(1_000_000)
print(f"{otp:06d}")   # e.g. "042817"

# Secure comparison (same function used above for HMAC)
print(secrets.compare_digest(api_key, api_key))  # True, constant-time
```

--> Rule of thumb: if the value is ever used to make a security decision (auth, tokens, keys, salts, nonces), use `secrets`, never `random`.

## Manual salted password hashing (to understand *why* it's needed)

--> A salt is random data mixed into a password before hashing, unique per user. Without it, two users with the same password produce the same hash, and attackers can use precomputed "rainbow tables" to crack many hashes at once.

```python
import hashlib
import secrets

def hash_password(password: str) -> tuple[str, str]:
    salt = secrets.token_hex(16)                     # unique random salt per user
    salted = (salt + password).encode()
    digest = hashlib.sha256(salted).hexdigest()
    return salt, digest

def verify_password(password: str, salt: str, expected_digest: str) -> bool:
    salted = (salt + password).encode()
    digest = hashlib.sha256(salted).hexdigest()
    return hmac_compare_digest_safe(digest, expected_digest)

def hmac_compare_digest_safe(a, b):
    import hmac
    return hmac.compare_digest(a, b)

salt, stored_hash = hash_password("MyP@ssw0rd")
print(salt, stored_hash)

print(verify_password("MyP@ssw0rd", salt, stored_hash))   # True
print(verify_password("WrongGuess", salt, stored_hash))   # False
```

## Why this manual approach still isn't enough — use `bcrypt` or `argon2`

--> SHA-256, even salted, is a **fast** hash — designed to hash gigabytes per second. That speed is exactly wrong for password storage: it lets an attacker who steals your database try billions of guesses per second on GPUs.
--> Dedicated password-hashing algorithms are deliberately slow and tunable (more CPU/memory cost = harder to brute-force), and they generate/store the salt for you automatically.

1. `bcrypt` – Mature, widely supported, tunable "work factor" (cost). Good default choice.
2. `argon2` – Winner of the 2015 Password Hashing Competition, tunable for both CPU and memory cost, resists GPU/ASIC cracking better than bcrypt. Preferred for new systems.

```python
# pip install bcrypt
import bcrypt

password = b"MyP@ssw0rd"
hashed = bcrypt.hashpw(password, bcrypt.gensalt(rounds=12))  # salt is embedded in the output
print(hashed)   # b'$2b$12$...'

print(bcrypt.checkpw(b"MyP@ssw0rd", hashed))   # True
print(bcrypt.checkpw(b"WrongGuess", hashed))   # False
```

```python
# pip install argon2-cffi
from argon2 import PasswordHasher
from argon2.exceptions import VerifyMismatchError

ph = PasswordHasher()  # sensible defaults for time/memory cost
hashed = ph.hash("MyP@ssw0rd")
print(hashed)   # $argon2id$v=19$m=65536,t=3,p=4$...

try:
    ph.verify(hashed, "MyP@ssw0rd")
    print("Password correct")
except VerifyMismatchError:
    print("Password incorrect")
```

--> Takeaway: `hashlib`/`hmac`/`secrets` are the building blocks and are exactly right for file integrity, message authentication, and token generation. For storing user passwords specifically, always reach for `bcrypt` or `argon2` instead of rolling your own with SHA-256.
