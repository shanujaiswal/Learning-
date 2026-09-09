### Classical Ciphers

--> Classical ciphers are pre-computer, pen-and-paper encryption schemes.
--> Nobody uses these to protect real data today — but they are the best way to build intuition for what "encryption" fundamentally means, and they are exactly the ciphers attacked by frequency analysis, which is a foundational cryptanalysis concept.

## Caesar Cipher

--> The Caesar cipher shifts every letter in the alphabet by a fixed number of positions (the key). "A" shifted by 3 becomes "D", "B" becomes "E", and so on, wrapping around at "Z".
--> The key is just a single number between 1 and 25 — this is why it is trivially breakable, there are only 25 possible keys to try (brute force takes milliseconds).

```python
def caesar_encrypt(text: str, shift: int) -> str:
    result = []
    for ch in text:
        if ch.isalpha():
            base = ord('A') if ch.isupper() else ord('a')
            shifted = (ord(ch) - base + shift) % 26
            result.append(chr(base + shifted))
        else:
            result.append(ch)  # leave spaces/punctuation untouched
    return "".join(result)


def caesar_decrypt(text: str, shift: int) -> str:
    return caesar_encrypt(text, -shift)   # decrypting is just shifting backwards


message = "Attack at dawn"
key = 3

ciphertext = caesar_encrypt(message, key)
print(ciphertext)                          # Dwwdfn dw gdzq

plaintext = caesar_decrypt(ciphertext, key)
print(plaintext)                           # Attack at dawn
```

--> Breaking it by brute force is trivial because the keyspace is tiny:

```python
ciphertext = "Dwwdfn dw gdzq"

for possible_key in range(26):
    guess = caesar_decrypt(ciphertext, possible_key)
    print(possible_key, guess)
    # key=3 -> "Attack at dawn"  <- a human (or a dictionary check) spots the real English sentence
```

## XOR Cipher

--> XOR (exclusive-or) encryption combines each byte of the plaintext with a byte of the key using the XOR bitwise operator. XOR has a beautiful property: `A xor B xor B == A`, so the SAME operation both encrypts and decrypts.
--> If the key is shorter than the message, it is repeated cyclically to match the message length.

```python
def xor_encrypt(data: bytes, key: bytes) -> bytes:
    return bytes(b ^ key[i % len(key)] for i, b in enumerate(data))


def xor_decrypt(data: bytes, key: bytes) -> bytes:
    return xor_encrypt(data, key)   # XOR is symmetric - same function does both


message = b"secret message"
key = b"k3y"

ciphertext = xor_encrypt(message, key)
print(ciphertext)                     # b'\x10\x06\x02\x0e\x02\x06\x1a\x02\x0d\x0e\x0e\x02\x02\x00'

plaintext = xor_decrypt(ciphertext, key)
print(plaintext)                      # b'secret message'
```

--> XOR with a SHORT REPEATING key is weak (repeats leak structure). XOR with a key that is truly random, at least as long as the message, and used exactly once is called a One-Time Pad (OTP) — mathematically unbreakable, but impractical because the key must be as long as all the data you will ever send and never reused.
--> Fun fact: this same XOR-with-keystream idea, done properly with a cryptographically strong pseudorandom keystream, is literally how modern stream ciphers (ChaCha20) and AES-CTR mode work internally.

## Vigenère Cipher

--> The Vigenère cipher is a Caesar cipher where the shift amount changes for every letter, determined by a repeating keyword instead of one fixed number.
--> Each letter of the keyword tells you the shift for the corresponding letter of the plaintext (A=0, B=1, ... Z=25).

```python
def vigenere_encrypt(text: str, key: str) -> str:
    result = []
    key = key.upper()
    key_index = 0
    for ch in text:
        if ch.isalpha():
            base = ord('A') if ch.isupper() else ord('a')
            shift = ord(key[key_index % len(key)]) - ord('A')
            result.append(chr((ord(ch) - base + shift) % 26 + base))
            key_index += 1          # only advance the key on actual letters
        else:
            result.append(ch)
    return "".join(result)


def vigenere_decrypt(text: str, key: str) -> str:
    result = []
    key = key.upper()
    key_index = 0
    for ch in text:
        if ch.isalpha():
            base = ord('A') if ch.isupper() else ord('a')
            shift = ord(key[key_index % len(key)]) - ord('A')
            result.append(chr((ord(ch) - base - shift) % 26 + base))
            key_index += 1
        else:
            result.append(ch)
    return "".join(result)


message = "Attack at dawn"
key = "LEMON"

ciphertext = vigenere_encrypt(message, key)
print(ciphertext)                          # Lxfopv ef rnhr

plaintext = vigenere_decrypt(ciphertext, key)
print(plaintext)                           # Attack at dawn
```

--> Vigenère was considered "unbreakable" for 300 years because a single letter ("E" for example) encrypts to DIFFERENT ciphertext letters depending on its position — unlike Caesar, simple letter-frequency counting no longer directly works.

## Frequency Analysis — The Classic Attack

--> Frequency analysis exploits the fact that in any natural language, letters are NOT used equally often. In English: "E" is the most common letter (~12.7%), followed by "T", "A", "O"... while "Z", "Q", "X" are rare.
--> Against a Caesar cipher: every plaintext letter maps to exactly ONE ciphertext letter, so the entire frequency distribution just shifts. The most frequent ciphertext letter almost certainly corresponds to plaintext "E".

```python
from collections import Counter

def frequency_analysis(ciphertext: str):
    letters = [c.upper() for c in ciphertext if c.isalpha()]
    counts = Counter(letters)
    total = len(letters)
    for letter, count in counts.most_common(5):
        print(f"{letter}: {count/total:.1%}")


ciphertext = caesar_encrypt("the quick brown fox jumps over the lazy dog " * 20, 7)
frequency_analysis(ciphertext)
# the most frequent ciphertext letter will correspond to shifted "E", "T", "O" etc,
# instantly revealing the shift amount without ever brute-forcing all 26 keys
```

--> Against Vigenère: frequency analysis doesn't work directly on the raw ciphertext because the shift changes every letter. The real attack (Kasiski examination) first finds the KEY LENGTH by looking for repeated ciphertext sequences (which happen when the same plaintext fragment aligns with the same part of the key), then splits the ciphertext into that many interleaved groups — each group was encrypted with a single fixed Caesar shift, so ordinary frequency analysis applies to each group separately.
--> This is exactly why classical ciphers are considered broken by modern standards: with enough ciphertext, statistical structure of the underlying language always leaks through. Modern ciphers like AES are specifically designed so ciphertext is statistically indistinguishable from random noise, no matter how much plaintext structure exists (see file 03 for why ECB mode fails this exact test).
