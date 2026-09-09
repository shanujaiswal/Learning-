### Cryptographic Attacks II - Padding Oracle, Timing, and Side-Channel Attacks

--> These attacks don't break the underlying math of AES or RSA directly — they exploit HOW an implementation leaks information through error messages, response timing, power draw, or CPU microarchitecture behavior. The cipher can be mathematically perfect and the system still gets fully broken.

## CBC Padding Oracle Attacks

--> A "padding oracle" is any system that tells an attacker — directly or indirectly — whether a decrypted ciphertext had VALID padding or not. That single bit of information ("padding ok" vs "padding error") is enough to decrypt an entire CBC-encrypted message byte by byte, without ever knowing the key.

# How PKCS#7 Padding Works in CBC

--> Block ciphers like AES encrypt fixed-size blocks (16 bytes for AES). If the plaintext isn't an exact multiple of the block size, padding bytes are appended before encryption.
--> PKCS#7 padding rule: pad with N bytes, each of value N, where N is however many bytes are needed to fill the last block (1-16 for AES). If the plaintext is already block-aligned, a FULL extra block of padding (16 bytes of value 0x10) is added.
1. Valid padding examples: `... 05 05 05 05 05` (5 padding bytes, each equal to 5), or `... 01` (1 padding byte, value 1).
2. Invalid padding: `... 03 03 05` — the last byte says "3 bytes of padding" but the byte 2 positions back isn't also 3, so this is a padding validation FAILURE.

# How CBC Decryption Actually Works

--> CBC decryption: `plaintext_block[i] = Decrypt(ciphertext_block[i]) XOR ciphertext_block[i-1]`. The previous ciphertext block is XORed into the decrypted output of the current block. For the first block, the IV plays the role of `ciphertext_block[-1]`.
--> Crucially, the attacker can control `ciphertext_block[i-1]` directly (they own the ciphertext they send), and XOR is invertible: flipping a bit in `ciphertext_block[i-1]` flips the SAME bit position in the resulting plaintext. This is the lever the entire attack pulls on.

# The Attack Setup

--> Attacker has: a ciphertext they want to decrypt, and access to an "oracle" — some endpoint that decrypts arbitrary ciphertext and reveals ONLY whether the padding was valid (e.g. a generic error page vs. a "malformed padding" error, or even just a timing difference between the two code paths).
--> Attacker does NOT have: the encryption key, and does NOT need it.

# Step-by-Step: Decrypting the Last Byte of a Block

--> Take two consecutive ciphertext blocks: `C_prev` (used as the "IV" for this attack) and `C_target` (the block to decrypt). Real plaintext relationship: `P_target = Decrypt(C_target) XOR C_prev`.

1. The attacker builds a MODIFIED previous block, `C_prev'`, identical to `C_prev` except the LAST byte is changed, and sends `(C_prev', C_target)` to the oracle repeatedly, trying all 256 possible values (0-255) for that last byte.
2. For each guess, the oracle decrypts and checks padding on the resulting fake plaintext: `P' = Decrypt(C_target) XOR C_prev'`. The last byte of `P'` is `Decrypt(C_target)[15] XOR C_prev'[15]`.
3. The oracle reports "valid padding" the moment `P'[15] == 0x01` (the simplest valid padding: exactly one padding byte of value 1). That happens for exactly one guessed byte value (ignoring rare false-positive collisions with `02 02`, `03 03 03`, etc., which real implementations of this attack handle by also modifying the second-to-last byte to disambiguate).
4. Once the oracle says "valid", the attacker knows: `Decrypt(C_target)[15] XOR C_prev'[15] = 0x01`, so `Decrypt(C_target)[15] = C_prev'[15] XOR 0x01`.
5. Since `P_target[15] = Decrypt(C_target)[15] XOR C_prev[15]` (the REAL previous block, not the modified one), the attacker now recovers the real plaintext byte: `P_target[15] = (C_prev'[15] XOR 0x01) XOR C_prev[15]`.

--> Worst case this takes 256 oracle queries to find the single byte value that produces valid padding — on average 128. To decrypt one 16-byte block fully takes at most 256 * 16 = 4096 queries, working backward one byte at a time (once the last byte is known, the attacker forces two bytes of padding `02 02` to solve for the second-to-last byte, and so on).

```python
# Illustrative padding-oracle byte recovery (attacker's algorithm), against a local
# "oracle" function that only reveals True/False for padding validity - NOT the key.

from Crypto.Cipher import AES
from Crypto.Util.Padding import pad, unpad
import os

KEY = os.urandom(16)  # attacker never sees this

def encrypt(plaintext: bytes) -> bytes:
    iv = os.urandom(16)
    cipher = AES.new(KEY, AES.MODE_CBC, iv)
    return iv + cipher.encrypt(pad(plaintext, 16))

def padding_oracle(ciphertext: bytes) -> bool:
    """Simulates a vulnerable server: returns True if padding is valid, False otherwise.
    In a real attack this might be an HTTP 200 vs 500, or a timing difference."""
    iv, body = ciphertext[:16], ciphertext[16:]
    cipher = AES.new(KEY, AES.MODE_CBC, iv)
    try:
        unpad(cipher.decrypt(body), 16)
        return True
    except ValueError:
        return False

def recover_block(c_prev: bytes, c_target: bytes) -> bytes:
    intermediate = bytearray(16)   # Decrypt(c_target) before XOR with c_prev
    recovered = bytearray(16)

    for pad_len in range(1, 17):
        forged_prev = bytearray(16)
        # set already-known trailing bytes so they XOR into pad_len
        for i in range(16 - pad_len + 1, 16):
            forged_prev[i] = intermediate[i] ^ pad_len

        found = False
        for guess in range(256):
            forged_prev[16 - pad_len] = guess
            fake_ct = bytes(forged_prev) + c_target
            if padding_oracle(fake_ct):
                # skip the trivial guess == original last byte when pad_len == 1 (false positive guard)
                if pad_len == 1 and guess == c_prev[15]:
                    continue
                intermediate[16 - pad_len] = guess ^ pad_len
                recovered[16 - pad_len] = intermediate[16 - pad_len] ^ c_prev[16 - pad_len]
                found = True
                break
        if not found:
            intermediate[16 - pad_len] = 0 ^ pad_len  # fallback for the demo

    return bytes(recovered)

ct = encrypt(b"Attack the east wall at midnight!")
iv, blocks = ct[:16], [ct[16 + i*16 : 32 + i*16] for i in range((len(ct) - 16) // 16)]
prev = iv
for block in blocks:
    print(recover_block(prev, block))
    prev = block
# Recovers the full plaintext block-by-block using ONLY the True/False oracle, never the key.
```

# Real-World Impact: POODLE, Lucky13

--> POODLE (2014) exploited SSLv3's CBC padding, which wasn't even authenticated (padding bytes past the first weren't checked), letting an attacker downgrade a TLS connection to SSLv3 and decrypt cookies/session tokens byte by byte.
--> Lucky13 (2013) exploited that padding validation and MAC (HMAC) verification took slightly different amounts of TIME in TLS's CBC-mode cipher suites, turning a timing side channel into a padding-oracle-equivalent attack — no explicit error message needed, just measuring response latency.
--> Mitigation: Authenticated Encryption (AES-GCM, ChaCha20-Poly1305) removes CBC padding oracles entirely — the MAC is checked BEFORE any padding logic runs, and a single generic failure is returned regardless of which check failed, with constant-time verification.

## Timing Attacks

--> A timing attack extracts secret information by measuring how LONG an operation takes, exploiting the fact that naive code often takes different amounts of time depending on secret data.

# The Classic Vulnerable Pattern: `==` on Secrets

--> Python's `==` on strings/bytes short-circuits: it compares byte-by-byte and returns `False` the INSTANT it finds a mismatch. This means comparing a guess against a secret token takes measurably longer the more LEADING bytes the guess gets correct.

```python
import time

SECRET_TOKEN = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4"

def naive_compare(a: str, b: str) -> bool:
    return a == b   # VULNERABLE: short-circuits on first mismatched byte

def time_guess(guess: str, rounds: int = 200_000) -> float:
    start = time.perf_counter()
    for _ in range(rounds):
        naive_compare(guess, SECRET_TOKEN)
    return time.perf_counter() - start

# A guess that matches zero characters returns almost immediately every time.
print(time_guess("z" * 32))
# A guess matching the first 20 characters correctly takes measurably LONGER on average,
# because == has to walk further into the string before it finds the mismatch.
print(time_guess(SECRET_TOKEN[:20] + "z" * 12))
# Over many repeated network requests (averaging out jitter), an attacker can recover
# the secret token ONE CHARACTER AT A TIME by keeping whichever guess-byte took longest.
```

--> This is exactly how real-world timing attacks against API keys, session tokens, and HMAC signatures work: attacker sends a guess, measures response time over thousands of repetitions to average out network jitter, keeps the byte that consistently takes longest, and moves to the next position.

# The Fix: Constant-Time Comparison

--> `secrets.compare_digest` (and the equivalent `hmac.compare_digest`) compares the ENTIRE length of both inputs regardless of where mismatches occur, taking the same amount of time whether zero bytes match or all-but-one byte matches.

```python
import hmac
import secrets

def safe_compare(a: str, b: str) -> bool:
    return hmac.compare_digest(a, b)   # SAFE: constant-time, no early exit
    # secrets.compare_digest(a, b) is equivalent and works on str/bytes too

# Rule of thumb: ANY comparison of secret material - API keys, session tokens, HMAC
# signatures, password reset tokens, CSRF tokens - must use compare_digest, never ==.
```

--> This matters for MAC verification specifically: if you ever compute an HMAC over user-supplied data and compare it to a stored/expected HMAC using `==`, you've built a padding-oracle-style timing side channel into your authentication check, even though the underlying HMAC algorithm itself is sound.

## Side-Channel Attacks (Conceptual Overview)

--> A side channel is ANY physical or observable byproduct of computation that leaks information about the secret being processed — not a flaw in the math, but a flaw in the PHYSICAL execution of the math.

# Power Analysis

--> CPUs and embedded chips draw measurably different amounts of electrical power depending on which instructions execute and what data they operate on (e.g. multiplying by a bit that's 1 vs 0 in an RSA exponentiation loop draws different current).
1. Simple Power Analysis (SPA) – directly reading a power trace to identify operations, e.g. visually spotting the square-and-multiply pattern of RSA to recover key bits.
2. Differential Power Analysis (DPA) – statistically correlating MANY power traces (thousands) against guessed key bits to extract a key even through significant electrical noise. Famously used against smart cards and hardware tokens.
--> Mitigation: constant-time/constant-power implementations (e.g. always performing both the "multiply" and "square" step in modular exponentiation regardless of the secret bit), and physical shielding/power-line filtering on hardware security modules.

# Cache-Timing Attacks

--> Modern CPUs cache recently accessed memory. Accessing cached data is dramatically faster than accessing uncached (main memory) data — often 100x. If a cryptographic algorithm's memory access PATTERN depends on secret key bits (e.g. an S-box lookup table indexed by a secret byte, as in naive AES software implementations), an attacker who can measure fine-grained timing (even from a co-located process on the SAME physical machine, e.g. another VM on shared cloud hardware) can infer which table entries were accessed and reconstruct the key.
--> Famous example: early software AES implementations using lookup tables (T-tables) were vulnerable to cache-timing attacks that recovered AES keys from a co-resident but unprivileged process — no root access needed, just shared CPU cache. Modern CPUs mitigate this with hardware AES-NI instructions that run AES in fixed time with no data-dependent memory access.

# Spectre and Meltdown (High-Level)

--> Both exploit SPECULATIVE EXECUTION: modern CPUs guess ahead and execute instructions before knowing if they're actually needed (e.g. speculatively executing both branches of an `if`, or reading memory before a permission check completes), then roll back architectural state if the guess was wrong. The catch: the ROLLBACK is imperfect — microarchitectural side effects (what got pulled into cache) persist even after the speculative instructions are "undone."
1. Meltdown (2018) – exploited out-of-order execution on Intel CPUs to read kernel/other-process memory from unprivileged user code, by speculatively reading forbidden memory and using its value to influence which cache line got touched, then timing cache access to infer the (supposedly inaccessible) byte.
2. Spectre (2018) – broader class exploiting BRANCH PREDICTION: tricks the CPU into speculatively executing code paths that leak data through a "cache side channel" the same way, applicable across many CPU vendors and even browser JavaScript engines (leading to JS-level mitigations like reduced timer precision).
--> Both were fixed with a combination of microcode updates, OS kernel patches (e.g. KPTI - kernel page table isolation), and compiler mitigations, at some measurable performance cost — a good real-world example of a side channel that exists purely due to a PERFORMANCE optimization (speculation), unrelated to any bug in the cryptography itself.

## Bleichenbacher's Attack on RSA PKCS#1 v1.5 (Conceptual)

--> RSA PKCS#1 v1.5 padding (used historically in TLS and email encryption) formats plaintext before RSA encryption as: `00 02 [random non-zero padding bytes] 00 [actual message]`. Decryption must verify this exact structure exists before extracting the message.
--> Bleichenbacher (1998) showed that a server which reveals ANY distinguishable signal for "padding was valid" vs "padding was invalid" after RSA-decrypting a ciphertext acts as a padding oracle for RSA itself — this became known as the "million message attack" because early practical variants needed roughly a million oracle queries.

# How the Attack Works Conceptually

1. Attacker has a target ciphertext `C` encrypted under the victim's RSA public key, and wants to recover the plaintext `M` without the private key.
2. RSA has a useful multiplicative property: `Encrypt(M) * Encrypt(s)^e mod N = Encrypt(M * s mod N)` for any chosen multiplier `s` (this is RSA's "homomorphic" multiplication property, the same property that makes RSA vulnerable to certain forgeries without proper padding).
3. Attacker sends the server modified ciphertexts `C * s^e mod N` for many different chosen values of `s`, and observes whether the server's padding-validation error differs ("bad padding" vs "processed successfully" vs even just a distinguishable TLS alert or timing difference).
4. Each oracle response ("this decrypted to something PKCS#1-valid" or not) narrows down the possible RANGE that `M * s mod N` could fall in. By cleverly choosing successive values of `s` (adaptive chosen-ciphertext attack), the attacker narrows the range with each query until it collapses to the single value of `M`.
5. This can fully recover the plaintext (e.g. a TLS session key encrypted under RSA) or, in adapted forms, forge valid-looking RSA signatures.

# Modern Relevance: ROBOT Attack

--> ROBOT (Return Of Bleichenbacher's Oracle Threat, 2017) found that MANY modern TLS implementations from major vendors (F5, Citrix, and others) had accidentally reintroduced Bleichenbacher-style oracles nearly 20 years later, allowing attackers to decrypt TLS traffic or forge signatures for affected servers using RSA key exchange.
--> Mitigation: RSA-OAEP (Optimal Asymmetric Encryption Padding) replaces PKCS#1 v1.5 for encryption, using randomized padding with a structure that doesn't leak partial validity information the same way, and is provably secure against chosen-ciphertext attacks under standard assumptions. For key EXCHANGE specifically, modern TLS 1.3 removes RSA key transport entirely in favor of ephemeral Diffie-Hellman (ECDHE), eliminating this entire attack class regardless of padding scheme.

## General Defensive Principles

1. Never let error responses distinguish WHICH internal check failed (padding vs MAC vs structure) — return one generic error for all decryption/verification failures.
2. Always verify MAC/signature BEFORE decrypting or unpadding (Encrypt-then-MAC ordering) — this is why AEAD modes like AES-GCM are structurally immune to padding oracles, there IS no separate padding step exposed to the attacker.
3. Use constant-time comparison (`hmac.compare_digest`) for ALL secret comparisons, no exceptions.
4. Use constant-time cryptographic primitives (hardware AES-NI, constant-time modular exponentiation, curve25519-style constant-time elliptic curve math) to close power/cache/timing side channels at the implementation level.
5. Prefer modern AEAD ciphers and OAEP/PSS padding over legacy CBC + PKCS#1 v1.5 schemes specifically because entire attack classes (padding oracles) are structurally impossible in the newer designs, rather than merely mitigated.
