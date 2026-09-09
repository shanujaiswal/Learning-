### Cryptography Fundamentals

--> Cryptography is the science of securing information so that only intended parties can read or verify it.
--> Before touching any algorithm, we need to get the vocabulary and the mental model right, because these words get thrown around loosely everywhere.

## Plaintext vs Ciphertext

--> Plaintext is the original, readable data — a password, a message, a file.
--> Ciphertext is the scrambled output produced after applying an encryption algorithm and a key to the plaintext.
--> The whole point of encryption is a reversible transformation: plaintext --> ciphertext --> plaintext, but only reversible by someone who holds the correct key.

```python
plaintext = "transfer 500 to account 44219"
key = b"a-32-byte-long-secret-key-here!!"

# ciphertext is unreadable garbage without the key
# b'\x9a\xe1\x88\x02\xf3...'  <- this is what an attacker sees on the wire
```

## Encoding vs Encryption vs Hashing

--> This is the single most confused topic for beginners. All three transform data, but they solve completely different problems.

1. Encoding – Converts data into a different format for compatibility or transport. It is fully reversible and needs no secret key. Anyone can decode it.
2. Encryption – Converts data into ciphertext using a secret key. It is reversible, but only by someone who has the key.
3. Hashing – Converts data into a fixed-size digest. It is one-way (irreversible) and needs no key.

--> Base64 is encoding, NOT encryption. It does not hide anything — it just represents bytes using printable characters (useful for putting binary data inside JSON, URLs, emails).

```python
import base64

secret = "my password"

encoded = base64.b64encode(secret.encode())
print(encoded)                     # b'bXkgcGFzc3dvcmQ='

# anyone can reverse this instantly, no key needed at all
decoded = base64.b64decode(encoded)
print(decoded.decode())            # my password
```

--> Real encryption needs a key, and without it the ciphertext is computationally infeasible to reverse.

```python
from cryptography.fernet import Fernet

key = Fernet.generate_key()        # this is the secret
f = Fernet(key)

token = f.encrypt(b"my password")
print(token)                       # gAAAAABk...  (looks similar to base64 but is USELESS without `key`)

# without `key` there is no practical way to recover "my password"
print(f.decrypt(token))            # b'my password'
```

--> Hashing has no key and cannot be reversed at all — you can only verify by re-hashing and comparing.

```python
import hashlib

digest = hashlib.sha256(b"my password").hexdigest()
print(digest)   # ef92b778bafe771e89245b89ecbc08a44a4e166c06659911881f383d4473e94

# there is no "unhash()" function - the only way to check a password
# is to hash the input again and compare digests
```

--> Quick rule of thumb:
1. Need to transport binary data as text safely? --> Encoding (Base64, Hex, URL-encoding).
2. Need to hide data but recover it later? --> Encryption (AES, RSA).
3. Need to verify integrity or store passwords without ever needing the original back? --> Hashing (SHA-256, bcrypt).

## Symmetric vs Asymmetric Encryption

--> Symmetric encryption uses the SAME key to encrypt and decrypt. Fast, but both parties must somehow already share that key secretly.

```python
from cryptography.fernet import Fernet

key = Fernet.generate_key()        # ONE key, shared by both sender and receiver
f = Fernet(key)

ciphertext = f.encrypt(b"attack at dawn")
plaintext = f.decrypt(ciphertext)
print(plaintext)                   # b'attack at dawn'
```

--> Asymmetric encryption uses a MATHEMATICALLY LINKED PAIR of keys — a public key (share with anyone) and a private key (never share). Data encrypted with the public key can only be decrypted with the matching private key.

```python
from cryptography.hazmat.primitives.asymmetric import rsa, padding
from cryptography.hazmat.primitives import hashes

private_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
public_key = private_key.public_key()          # this can be handed out publicly

ciphertext = public_key.encrypt(
    b"attack at dawn",
    padding.OAEP(mgf=padding.MGF1(algorithm=hashes.SHA256()),
                 algorithm=hashes.SHA256(), label=None)
)

# only the matching private key can decrypt this
plaintext = private_key.decrypt(
    ciphertext,
    padding.OAEP(mgf=padding.MGF1(algorithm=hashes.SHA256()),
                 algorithm=hashes.SHA256(), label=None)
)
print(plaintext)   # b'attack at dawn'
```

--> Trade-off table:
1. Symmetric – Very fast, small keys (e.g. 256-bit), but the "how do we agree on a shared key over an insecure network" problem remains (solved later using Diffie-Hellman).
2. Asymmetric – Solves the key-sharing problem elegantly, but 100-1000x slower and needs bigger keys (2048/4096-bit) for equivalent security.

--> This is exactly why real systems like HTTPS use BOTH: asymmetric encryption to safely exchange a random symmetric key, then symmetric encryption for the actual bulk data (covered in file 06).

## Kerckhoffs's Principle

--> Kerckhoffs's Principle (1883): a cryptographic system should be secure even if everything about it is public knowledge — EXCEPT the key.
--> In other words: "the algorithm can be known to the enemy, only the key must remain secret."
--> This is why AES, RSA, SHA-256 etc. are all publicly published, peer-reviewed standards. Security never comes from hiding the algorithm ("security through obscurity" is considered a red flag / anti-pattern).
--> Never invent your own "secret" encryption algorithm and never trust one that a vendor refuses to disclose the details of.

```python
# BAD - "security through obscurity": a made-up scrambling scheme with no public analysis
def my_totally_secret_cipher(data: bytes) -> bytes:
    return bytes((b + 7) % 256 for b in data)   # trivially reversible, no real security

# GOOD - algorithm is 100% public (AES-GCM), only `key` is secret
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
key = AESGCM.generate_key(bit_length=256)
```

## What "Key" Actually Means

--> A key is just a (usually random) sequence of bits fed into an algorithm alongside the plaintext to produce ciphertext.
--> A "password" is something a human picks and remembers; a "key" is the actual cryptographic material used by the algorithm. Passwords are often turned into keys via a Key Derivation Function (KDF) like PBKDF2/scrypt/Argon2 — never used directly as a raw AES key.
--> Key length correlates with brute-force resistance: AES-128 has 2^128 possible keys, AES-256 has 2^256 — both are astronomically large, but 256-bit is chosen when defending against extremely well-resourced/long-horizon attackers (e.g. concerns about future quantum computers halving effective key strength via Grover's algorithm).

```python
import secrets

aes_key = secrets.token_bytes(32)   # 32 bytes = 256 bits, a properly random symmetric key
print(len(aes_key) * 8, "bits")     # 256 bits
```

## Block Ciphers vs Stream Ciphers

--> Both are symmetric encryption designs, but they process data differently.

1. Block ciphers – Encrypt data in fixed-size chunks (blocks), e.g. AES works on 16-byte blocks. If the plaintext isn't an exact multiple of the block size, it needs padding.
2. Stream ciphers – Encrypt data one byte (or bit) at a time by combining it with a pseudorandom keystream, usually via XOR. No padding needed since there's no fixed block.

```python
from Crypto.Cipher import AES, ChaCha20
from Crypto.Util.Padding import pad, unpad

# Block cipher (AES) - needs padding to reach a multiple of 16 bytes
key = b"0" * 16
cipher = AES.new(key, AES.MODE_CBC)
padded = pad(b"hello", AES.block_size)      # padded up to 16 bytes
ct = cipher.encrypt(padded)
print(len(ct))                              # 16 (rounded up to one full block)

# Stream cipher (ChaCha20) - no padding, works on the exact byte length
key = b"0" * 32
nonce = b"0" * 8
cipher = ChaCha20.new(key=key, nonce=nonce)
ct = cipher.encrypt(b"hello")
print(len(ct))                              # 5 (exact same length as plaintext)
```

--> AES itself is a block cipher, but modes like CTR and GCM turn it into something that behaves like a stream cipher internally — this distinction becomes very important in file 03 when comparing AES modes of operation.
