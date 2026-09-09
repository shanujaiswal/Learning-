### Symmetric Encryption - AES

--> AES (Advanced Encryption Standard) is the modern, universally trusted symmetric block cipher. It replaced the older, broken DES standard in 2001 and is used everywhere — HTTPS, disk encryption, VPNs, messaging apps.
--> AES itself only defines HOW to scramble one fixed-size block of data with a key. Everything else (how to handle messages longer than one block, how to guarantee uniqueness) is decided by the "mode of operation" — this is the part beginners usually get wrong.

## AES Basics — Key Sizes and Block Size

--> AES always operates on a FIXED block size of 128 bits (16 bytes), regardless of key size.
--> The key size can be 128, 192, or 256 bits — this only changes the number of internal transformation rounds (10, 12, or 14 rounds respectively), not the block size.

1. AES-128 – 128-bit key, 10 rounds. Fast, still considered secure for the foreseeable future.
2. AES-192 – 192-bit key, 12 rounds. Rarely used in practice.
3. AES-256 – 256-bit key, 14 rounds. Chosen for long-term/high-sensitivity data or regulatory requirements.

```python
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

key_128 = AESGCM.generate_key(bit_length=128)
key_256 = AESGCM.generate_key(bit_length=256)

print(len(key_128), len(key_256))   # 16 32   (bytes -> 128 bits, 256 bits)
# block size is ALWAYS 16 bytes no matter which key size is used
```

## Modes of Operation

--> A "mode" tells AES how to chain together the encryption of multiple 16-byte blocks that make up a real-world message.

### ECB (Electronic Codebook) — Insecure, Never Use

--> ECB encrypts every block independently using the same key, with no chaining and no randomness. Identical plaintext blocks ALWAYS produce identical ciphertext blocks.
--> This leaks the STRUCTURE of the plaintext even though you can't read the actual content — famously demonstrated by encrypting an image in ECB mode: the outline of the original picture (e.g. the recognizable silhouette of a penguin) remains completely visible in the "encrypted" version, because repeated colors (= repeated plaintext blocks) become repeated ciphertext blocks in the exact same pattern.

```python
from Crypto.Cipher import AES

key = b"0" * 16
cipher = AES.new(key, AES.MODE_ECB)

block = b"AAAAAAAAAAAAAAAA"     # 16 identical bytes, repeated 3 times
data = block * 3

ct = cipher.encrypt(data)
print(ct[0:16] == ct[16:32] == ct[32:48])   # True - identical input blocks -> identical output blocks
# an attacker who cannot decrypt anything can still SEE which blocks repeat -
# this is the "penguin leak" and is exactly why ECB must never be used
```

### CBC (Cipher Block Chaining)

--> CBC fixes ECB's pattern leak by XOR-ing each plaintext block with the PREVIOUS ciphertext block before encrypting it. The very first block is XOR-ed with a random Initialization Vector (IV) instead, since there's no previous ciphertext yet.
--> Because of this chaining, identical plaintext blocks now produce DIFFERENT ciphertext (as long as the IV is different/random each time). But CBC needs padding (message length must be a multiple of 16 bytes) and is vulnerable to padding-oracle attacks if implemented carelessly, plus it gives no built-in integrity check (an attacker can flip ciphertext bits and you won't know until you try to use the corrupted plaintext).

### CTR (Counter Mode)

--> CTR turns AES into a stream cipher: it encrypts an incrementing counter (nonce + block number) to generate a keystream, then XORs that keystream with the plaintext. No padding needed, blocks can be processed in parallel/random order.
--> Still provides zero integrity checking on its own — same weakness as CBC in that respect.

### GCM (Galois/Counter Mode) — The Modern Default

--> GCM = CTR mode encryption + a built-in authentication tag (using Galois field math) computed over the ciphertext. This gives you BOTH confidentiality AND integrity/authenticity in a single pass — this combination is called AEAD (Authenticated Encryption with Associated Data).
--> If even a single bit of the ciphertext or the associated data is tampered with, decryption FAILS LOUDLY instead of silently returning corrupted plaintext. This is why GCM is the recommended default mode for basically everything today.

--> Summary comparison:
1. ECB – No IV, no chaining, leaks patterns. NEVER use.
2. CBC – Needs IV + padding, no built-in integrity. Use only if you add HMAC yourself (encrypt-then-MAC).
3. CTR – Needs a nonce, no padding, no built-in integrity.
4. GCM – Needs a nonce, no padding, built-in integrity tag. Preferred choice.

## IV / Nonce Concepts

--> An IV (Initialization Vector, used in CBC) or nonce ("number used once", used in CTR/GCM) is a value that must be different for every single encryption operation performed with the same key.
--> It does NOT need to be secret — it is normally sent alongside the ciphertext in plain sight. What it must never be is REUSED with the same key, because that reintroduces exactly the kind of pattern leakage ECB has (in GCM specifically, nonce reuse is catastrophic — it can let an attacker recover the authentication key entirely).
--> For GCM, a 12-byte (96-bit) randomly generated nonce per message is the standard recommendation.

```python
import os
nonce = os.urandom(12)   # generate a fresh, random 12-byte nonce for every AES-GCM call
```

## Worked Example — Encrypting a String with AES-GCM

```python
import os
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

# 1. Generate a key ONCE and store it securely (e.g. in a secrets manager / env var)
key = AESGCM.generate_key(bit_length=256)
aesgcm = AESGCM(key)

def encrypt_message(plaintext: str) -> bytes:
    nonce = os.urandom(12)                       # fresh nonce every single time
    ct = aesgcm.encrypt(nonce, plaintext.encode(), None)
    return nonce + ct                             # prepend nonce so decrypt() can read it back


def decrypt_message(blob: bytes) -> str:
    nonce, ct = blob[:12], blob[12:]
    plaintext = aesgcm.decrypt(nonce, ct, None)   # raises InvalidTag if tampered/wrong key
    return plaintext.decode()


blob = encrypt_message("transfer 500 to account 44219")
print(blob)                                       # b'\x8f\xa2...\xcd' (nonce + ciphertext + tag)

print(decrypt_message(blob))                      # transfer 500 to account 44219

# tampering proof: flip one byte and decryption fails loudly instead of returning garbage
tampered = bytearray(blob)
tampered[-1] ^= 0xFF
try:
    decrypt_message(bytes(tampered))
except Exception as e:
    print("Tampering detected:", type(e).__name__)   # Tampering detected: InvalidTag
```

## Worked Example — Encrypting a File with AES-GCM

```python
import os
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

key = AESGCM.generate_key(bit_length=256)
aesgcm = AESGCM(key)

def encrypt_file(in_path: str, out_path: str):
    with open(in_path, "rb") as f:
        data = f.read()

    nonce = os.urandom(12)
    ciphertext = aesgcm.encrypt(nonce, data, None)

    with open(out_path, "wb") as f:
        f.write(nonce + ciphertext)                # store nonce alongside ciphertext


def decrypt_file(in_path: str, out_path: str):
    with open(in_path, "rb") as f:
        blob = f.read()

    nonce, ciphertext = blob[:12], blob[12:]
    plaintext = aesgcm.decrypt(nonce, ciphertext, None)

    with open(out_path, "wb") as f:
        f.write(plaintext)


# encrypt_file("report.pdf", "report.pdf.enc")
# decrypt_file("report.pdf.enc", "report_decrypted.pdf")
# for large files, prefer streaming/chunked reads instead of loading everything into memory at once
```

## Padding, Explained (for CBC)

--> AES-CBC requires the plaintext length to be an exact multiple of the 16-byte block size. Real messages are almost never a clean multiple of 16, so PADDING adds extra bytes to round up before encrypting, and strips them off after decrypting.
--> PKCS#7 is the standard padding scheme: it pads with N bytes, each containing the VALUE N (where N = number of bytes needed to reach the next multiple of the block size). If the plaintext already happens to be an exact multiple of the block size, a FULL extra block of padding is added anyway — this avoids the ambiguity of "was that a real trailing byte or padding?".

```python
from Crypto.Cipher import AES
from Crypto.Util.Padding import pad, unpad
import os

key = os.urandom(16)
iv = os.urandom(16)

plaintext = b"hello"                     # 5 bytes - not a multiple of 16

padded = pad(plaintext, AES.block_size)
print(padded)                            # b'hello\x0b\x0b\x0b\x0b\x0b\x0b\x0b\x0b\x0b\x0b\x0b'
# 11 padding bytes added, each with value 0x0b (=11), to reach 16 bytes total

cipher = AES.new(key, AES.MODE_CBC, iv)
ciphertext = cipher.encrypt(padded)

cipher2 = AES.new(key, AES.MODE_CBC, iv)
decrypted_padded = cipher2.decrypt(ciphertext)
original = unpad(decrypted_padded, AES.block_size)
print(original)                          # b'hello'
```

--> GCM and CTR never need this step at all, because they turn the block cipher into a stream cipher internally — one more reason GCM is the simpler, safer default for new code.
