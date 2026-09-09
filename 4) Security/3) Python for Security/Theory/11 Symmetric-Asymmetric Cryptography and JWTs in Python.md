### Symmetric/Asymmetric Cryptography and JWTs in Python

--> `02 Hashing HMAC and Secure Randomness.md` covered one-way hashing and message authentication. This file covers actual ENCRYPTION -- turning data into ciphertext that can be decrypted back -- using the `cryptography` library (the modern, actively-maintained standard) and briefly PyCryptodome (a common alternative/legacy-compatible library), then PyJWT for tokens built on these same primitives.

## Symmetric encryption -- AES with `cryptography`

--> Symmetric encryption uses the SAME key to encrypt and decrypt -- fast, but the key itself must somehow be shared secretly with whoever needs to decrypt, which is the exact problem asymmetric crypto (below) solves differently.
--> AES-GCM is the recommended mode for almost all new work -- it's an AEAD (Authenticated Encryption with Associated Data) mode, meaning it provides both confidentiality (the data is encrypted) AND integrity/authenticity (any tampering with the ciphertext is detected on decryption) in one operation, unlike older modes (e.g. plain AES-CBC) which need a SEPARATE HMAC bolted on to get that same tamper-detection.

```python
# pip install cryptography
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
import os

key = AESGCM.generate_key(bit_length=256)   # 256-bit key -- store/transmit this secretly, never hardcode
aesgcm = AESGCM(key)

nonce = os.urandom(12)                       # MUST be unique per encryption with the same key --
                                              # reusing a nonce with AES-GCM catastrophically breaks
                                              # its security guarantees, not just weakens them
plaintext = b"attack at dawn"
ciphertext = aesgcm.encrypt(nonce, plaintext, associated_data=None)

# Decryption needs the same key, the same nonce, and the ciphertext -- tampering with any byte
# of the ciphertext makes decrypt() raise InvalidTag instead of returning wrong/garbage data
decrypted = aesgcm.decrypt(nonce, ciphertext, associated_data=None)
assert decrypted == plaintext

# In practice you must store/transmit the nonce alongside the ciphertext (it's not secret,
# just required to be unique) -- a common convention is simply prepending it:
blob = nonce + ciphertext
```

--> The module path `hazmat.primitives` is deliberately named "hazmat" (hazardous materials) by the `cryptography` library's authors -- it's a direct warning that these are LOW-LEVEL primitives requiring you to get details (nonce uniqueness, key storage, mode choice) correct yourself; the library's higher-level `Fernet` API (below) exists specifically to make it harder to shoot yourself in the foot.

## The simpler option -- Fernet

--> `Fernet` wraps AES (in CBC mode) with an HMAC for authenticity, handles nonce/IV generation and encoding for you, and produces a single, URL-safe base64 token containing everything needed to decrypt -- the recommended default whenever you don't have a specific reason to need AES-GCM's raw AEAD interface directly.

```python
from cryptography.fernet import Fernet

key = Fernet.generate_key()          # generate once, store securely (env var, secrets manager --
                                      # never commit it to source control)
f = Fernet(key)

token = f.encrypt(b"attack at dawn")
print(token)                         # a single opaque, URL-safe bytes token
plaintext = f.decrypt(token)         # raises InvalidToken if the key is wrong OR the token was tampered with
```

--> `Fernet` also embeds a timestamp in the token and supports `f.decrypt(token, ttl=3600)` -- rejecting tokens older than the given number of seconds, useful for anything like a time-limited signed link or short-lived credential.

## Asymmetric encryption -- RSA

--> Asymmetric (public-key) encryption uses a MATHEMATICALLY RELATED but distinct pair of keys -- anything encrypted with the PUBLIC key can only be decrypted with the corresponding PRIVATE key, so the public key can be shared freely while the private key stays secret. This solves symmetric encryption's key-distribution problem, at the cost of being far slower and only practical for small amounts of data (in real systems, RSA typically encrypts a symmetric SESSION key, and the bulk data is then encrypted with that fast symmetric key -- exactly the hybrid approach TLS itself uses).

```python
from cryptography.hazmat.primitives.asymmetric import rsa, padding
from cryptography.hazmat.primitives import hashes

# Generate a keypair -- 2048 bits is the current practical minimum; 4096 for extra margin
private_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
public_key = private_key.public_key()

message = b"session key material or another small secret"

ciphertext = public_key.encrypt(
    message,
    padding.OAEP(
        mgf=padding.MGF1(algorithm=hashes.SHA256()),
        algorithm=hashes.SHA256(),
        label=None,
    ),
)

plaintext = private_key.decrypt(
    ciphertext,
    padding.OAEP(
        mgf=padding.MGF1(algorithm=hashes.SHA256()),
        algorithm=hashes.SHA256(),
        label=None,
    ),
)
assert plaintext == message
```

--> OAEP padding is required, not optional -- RSA without proper padding (the old, insecure "textbook RSA") is vulnerable to several well-known attacks; always use `OAEP` for encryption (and `PSS` for signatures, next).

## Digital signatures with RSA

--> Signing is the INVERSE use of the same keypair from an authorization standpoint -- the PRIVATE key signs (proving the signer holds that private key), and the PUBLIC key verifies (anyone can check the signature, but only the private key holder could have produced it) -- opposite roles from encryption's "public encrypts, private decrypts."

```python
from cryptography.hazmat.primitives.asymmetric import padding as sig_padding

signature = private_key.sign(
    message,
    sig_padding.PSS(mgf=sig_padding.MGF1(hashes.SHA256()), salt_length=sig_padding.PSS.MAX_LENGTH),
    hashes.SHA256(),
)

try:
    public_key.verify(
        signature, message,
        sig_padding.PSS(mgf=sig_padding.MGF1(hashes.SHA256()), salt_length=sig_padding.PSS.MAX_LENGTH),
        hashes.SHA256(),
    )
    print("Signature valid -- message authentic and unmodified")
except Exception:
    print("Signature INVALID -- message was tampered with or not signed by this key")
```

## PyCryptodome -- a common alternative

--> `PyCryptodome` (import name `Crypto`, a maintained fork of the long-abandoned original PyCrypto) offers a similar feature set with a somewhat lower-level, more manual API -- worth knowing because a lot of existing/legacy security tooling and CTF write-ups use it directly rather than `cryptography`.

```python
# pip install pycryptodome
from Crypto.Cipher import AES
from Crypto.Random import get_random_bytes

key = get_random_bytes(32)                 # AES-256
cipher = AES.new(key, AES.MODE_GCM)
ciphertext, tag = cipher.encrypt_and_digest(b"attack at dawn")

decipher = AES.new(key, AES.MODE_GCM, nonce=cipher.nonce)
plaintext = decipher.decrypt_and_verify(ciphertext, tag)   # raises ValueError if tag doesn't match
```

--> Functionally comparable to the `cryptography` examples above -- pick whichever a given codebase already uses; mixing both in one project just for variety adds a second dependency for no real benefit.

## PyJWT -- Signed Tokens Built on These Same Primitives

--> A JWT (JSON Web Token) is a compact, signed (and optionally encrypted) token typically used for authentication/authorization -- structurally three base64url-encoded parts separated by dots: a header (algorithm/type), a payload (claims -- arbitrary key/value data like user ID, expiry, roles), and a signature over the first two parts.
--> Critically, a JWT's payload is only BASE64-ENCODED, not encrypted -- anyone holding the token can trivially decode and read its contents (this is by design, and it's a common misunderstanding to treat a JWT as if it hides its claims). The SIGNATURE is what a JWT actually protects -- proving the payload hasn't been tampered with since a trusted party issued it, not that it's secret.

```python
# pip install pyjwt
import jwt
import datetime

SECRET = "a-long-random-server-side-secret"   # for HS256 (symmetric) -- protect this like any password

payload = {
    "user_id": 42,
    "role": "admin",
    "exp": datetime.datetime.utcnow() + datetime.timedelta(hours=1),   # standard expiry claim
}

token = jwt.encode(payload, SECRET, algorithm="HS256")
print(token)   # e.g. "eyJhbGciOiJIUzI1NiIs...header.payload.signature..."

decoded = jwt.decode(token, SECRET, algorithms=["HS256"])   # raises if signature invalid or token expired
print(decoded)   # {'user_id': 42, 'role': 'admin', 'exp': ...}
```

--> **`algorithms=` in `jwt.decode()` must be explicitly restricted** -- this is not a style preference, it is the single most common real-world JWT vulnerability. If verification code accepts whatever algorithm the TOKEN ITSELF claims in its header rather than pinning an expected algorithm list, an attacker can submit a token with `"alg": "none"` (no signature at all) or, in the classic "algorithm confusion" attack, re-sign a token using HS256 with the SERVER'S OWN PUBLIC KEY (which is not secret, if the server normally uses RS256/asymmetric signing) as if it were an HMAC secret -- a verifier that blindly trusts the token's self-declared algorithm can be tricked into validating a forged token this way.

```python
# RS256 -- asymmetric signing, using the RSA keypair generated earlier in this file
rs256_token = jwt.encode(payload, private_key, algorithm="RS256")
decoded = jwt.decode(rs256_token, public_key, algorithms=["RS256"])  # note: verify with the PUBLIC key
```

--> With RS256, anyone holding the PUBLIC key can VERIFY tokens, but only the holder of the PRIVATE key can ISSUE valid ones -- useful when many services need to verify tokens but only one central auth server should be able to mint them, versus HS256 where every party that can verify a token could equally forge one (since verification and signing use the identical shared secret).

## Cross-references

--> Directly follows `02 Hashing HMAC and Secure Randomness.md` -- HMAC there is the exact primitive HS256 JWT signing uses under the hood, and `secrets.token_hex()`/`token_urlsafe()` from that file are the right way to generate a JWT signing secret or an AES key's raw entropy source if not using `Fernet.generate_key()`/`AESGCM.generate_key()` directly. The TLS/certificate material in `12 TLS-SSL Certificate Inspection and Validation with the ssl Module.md` is the other major place these same asymmetric-key concepts (public/private keypairs, signatures) reappear in this track.
