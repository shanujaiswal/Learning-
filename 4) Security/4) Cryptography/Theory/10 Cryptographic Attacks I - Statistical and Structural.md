### Cryptographic Attacks I - Statistical and Structural

--> Understanding attacks is what separates "I can call an encryption library" from "I understand why the parameters I chose are safe."
--> This note covers the attack-classification vocabulary (what an attacker can access) and a set of concrete structural attacks that exploit math, protocol design, and time — as opposed to attacks on the underlying primitive's algebra itself (which is a separate, deeper topic).

## Attacker Capability Model

--> Cryptographic security proofs are always stated relative to what the attacker is assumed able to do. The stronger the assumed capability, the stronger the guarantee a scheme needs to provide.

--> 1. **Brute-force / exhaustive key search** — the attacker has ciphertext (and maybe knows or guesses the plaintext format) and simply tries every possible key. Defense is purely key-space size: AES-128's 2^128 keyspace is infeasible to exhaust with any known or foreseeable computing capability (a common estimate: exhausting 2^128 keys at 10^18 attempts/sec would still take longer than the age of the universe many times over).
--> 2. **Known-plaintext attack (KPA)** — the attacker has one or more (plaintext, ciphertext) pairs under the SAME key and tries to recover the key or decrypt other ciphertexts. Classical ciphers (Caesar, Vigenere) fall instantly to KPA. Modern block ciphers (AES) are designed to be secure even when the attacker has huge amounts of known plaintext.
--> 3. **Chosen-plaintext attack (CPA)** — the attacker can get the target to encrypt plaintexts of the attacker's choosing and observe the resulting ciphertexts (e.g. an oracle service that encrypts whatever you submit). "IND-CPA secure" is the standard baseline security definition for a symmetric cipher mode — it requires ciphertexts to be indistinguishable from random even under this capability. This is why deterministic modes like ECB fail immediately: encrypting the same plaintext block twice yields identical ciphertext blocks, which is trivially distinguishable from random and leaks patterns (the infamous "ECB penguin" image).
--> 4. **Chosen-ciphertext attack (CCA)** — the attacker can additionally submit ciphertexts of their choosing and observe the resulting plaintext (a decryption oracle). "IND-CCA2 secure" (adaptive CCA) is the gold-standard security notion and is what authenticated encryption (AES-GCM, ChaCha20-Poly1305) is designed to satisfy — an attacker who can't produce a validly-authenticated ciphertext learns nothing from submitting garbage, because it's simply rejected before any decryption result is revealed.
--> --> The canonical real CCA example is **padding oracle attacks** (e.g. against CBC mode with PKCS#7 padding): a server that reveals "valid padding" vs "invalid padding" as distinguishable behavior (different error message, or even just a timing difference) lets an attacker decrypt an entire ciphertext byte-by-byte without ever knowing the key, by submitting many crafted ciphertexts and observing which are accepted.

```python
# Toy demonstration of WHY a padding oracle is dangerous — not a full
# attack implementation, but shows the exploitable signal.
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
from cryptography.hazmat.primitives import padding
import os

key = os.urandom(32)
iv = os.urandom(16)

def encrypt_cbc(plaintext: bytes) -> bytes:
    padder = padding.PKCS7(128).padder()
    padded = padder.update(plaintext) + padder.finalize()
    encryptor = Cipher(algorithms.AES(key), modes.CBC(iv)).encryptor()
    return encryptor.update(padded) + encryptor.finalize()

def decrypt_cbc_leaky(ciphertext: bytes) -> bool:
    """Returns True if padding is valid, False otherwise — this boolean
    being observable to an attacker (via error codes/timing) IS the oracle."""
    decryptor = Cipher(algorithms.AES(key), modes.CBC(iv)).decryptor()
    padded = decryptor.update(ciphertext) + decryptor.finalize()
    try:
        padding.PKCS7(128).unpadder().update(padded)
        return True
    except ValueError:
        return False

ct = encrypt_cbc(b"transfer:100:to:bob")

# An attacker who can flip bits in ct and repeatedly ask "was padding
# valid?" can recover the entire plaintext one byte at a time, without
# the key — this is the real-world basis of attacks like POODLE, and
# why authenticated modes (GCM) that reject tampered ciphertext BEFORE
# revealing any padding/plaintext information are now mandatory practice.
print(decrypt_cbc_leaky(ct))              # True
print(decrypt_cbc_leaky(ct[:-1] + bytes([ct[-1] ^ 1])))  # likely False — this asymmetry is the leak
```

## The Birthday Paradox and Hash Collisions

--> The birthday paradox: in a room of just 23 people, there's already a >50% chance two share a birthday, out of 365 possible days — far fewer people than the naive "365/2" intuition suggests.
--> This matters for cryptography because it describes how fast COLLISIONS appear in a random mapping, and hash functions are exactly that: a mapping from an infinite input space to a fixed-size output space.

# The Math, Worked Out

--> For a hash with output space size `N` (so `N = 2^n` for an `n`-bit hash), the expected number of random samples needed before a collision is found with ~50% probability is approximately:

```
q ≈ 1.1774 * sqrt(N) = 1.1774 * 2^(n/2)
```

--> For a 128-bit hash (`n=128`, `N = 2^128`): `q ≈ 2^64` — NOT `2^128`. This is why the phrase "128-bit hash has 128-bit security against collisions" is WRONG; it actually has only 64-bit security against a collision attack (though it still has full 128-bit security against a much harder attack: finding a SPECIFIC preimage or second preimage for a given target).
--> This is exactly why SHA-1 (160-bit output, so ~2^80 for a birthday collision) was declared broken in practice once real hardware could approach that cost — the 2017 "SHAttered" attack by Google/CWI produced a real SHA-1 collision, motivating the industry-wide move to SHA-256 (256-bit output, ~2^128 birthday resistance) and beyond.
--> Rule of thumb: to get `k` bits of birthday-collision resistance, you need a hash with `2k` bits of OUTPUT. This is precisely why hash outputs (256, 384, 512 bits) look "oversized" relative to symmetric key sizes (128, 192, 256 bits) offering the same effective attack cost.

```python
import hashlib
import itertools
import os

def find_collision(num_bits: int, max_attempts: int = 2_000_000):
    """Empirically demonstrate the birthday effect using a TRUNCATED
    hash (small enough to actually brute-force in a demo), rather than
    a real 128/256-bit hash, which is computationally infeasible to
    collide by design."""
    seen = {}
    for i in range(max_attempts):
        data = os.urandom(8)
        digest = hashlib.sha256(data).digest()[: num_bits // 8]  # truncate
        if digest in seen:
            return seen[digest], data, digest
        seen[digest] = data
    return None

# With a 24-bit truncated hash, expect a collision after roughly
# sqrt(2^24) ≈ 4096 attempts on average — cheap enough to run live.
result = find_collision(num_bits=24)
if result:
    first, second, digest = result
    print(f"Collision found: {first.hex()} and {second.hex()} both hash to {digest.hex()}")
    print(f"SHA256(first)[:3]  = {hashlib.sha256(first).hexdigest()[:6]}")
    print(f"SHA256(second)[:3] = {hashlib.sha256(second).hexdigest()[:6]}")
# Collision found after only a few thousand tries — matches the
# sqrt(N) prediction, and demonstrates WHY 24-bit hashes are useless
# but also why the same growth rate is the exact reason 256-bit
# hashes need to be that large: sqrt(2^256) = 2^128, still infeasible.
```

--> Practical consequence for design: if you need collision resistance (e.g. content-addressed storage, digital signatures over a hash of the message), always pick a hash with output length at least DOUBLE your target security level in bits. If you only need preimage resistance (e.g. password hash storage, where the attacker already has a specific target hash and is trying to find ANYTHING that maps to it, not any two colliding things), full output length security applies and birthday math doesn't help the attacker as much.

## Meet-in-the-Middle Attacks

--> A meet-in-the-middle (MITM — not to be confused with man-in-the-middle) attack targets constructions that chain two independent operations, trading memory for a huge time speedup versus naive brute force.
--> Classic example: **Double DES** (encrypting twice with two independent 56-bit keys, hoping to get 112-bit security). Naive brute force over both keys would need `2^112` attempts. Meet-in-the-middle breaks this down to roughly `2^56` time AND `2^56` memory:
--> 1. For every possible `k1` (2^56 of them), compute `E(k1, plaintext)` and store all results in a lookup table (indexed by the result).
--> 2. For every possible `k2` (2^56 of them), compute `D(k2, ciphertext)` and check if it matches any entry in the table.
--> 3. A match means `E(k1, plaintext) == D(k2, ciphertext)`, i.e. `plaintext` encrypted once under `k1` reaches the same intermediate value that decrypting the target ciphertext once under `k2` reaches — meaning `(k1, k2)` is (very likely) the correct key pair.
--> This reduces total work from `2^112` to roughly `2^57` (dominated by the `2^56` table build plus `2^56` lookups) — a massive practical break, at the cost of needing `2^56` storage slots, which is why real double-encryption schemes moved to **Triple DES (3DES)** using three keys in an Encrypt-Decrypt-Encrypt structure specifically designed to resist this exact reduction (3DES gives ~112-bit effective security, not 168, precisely because of a similar though more expensive MITM-style attack).
--> General lesson: naively chaining two independent n-bit-key operations to "double" security does NOT give you `2n` bits of security against a well-resourced attacker with memory to spare — it gives you roughly `n+1` bits. This is why modern designs don't build security by stacking independent weak primitives; they use single primitives already designed and analyzed for the target security level (AES-256 for 256-bit security, not "AES-128 twice").

```python
# Simplified illustrative meet-in-the-middle against a toy 2-key
# "double XOR cipher" with tiny (16-bit) keys, to make brute force
# demonstrable in a few seconds rather than requiring real DES.

import os

def toy_encrypt(key: int, block: int) -> int:
    return block ^ key   # deliberately trivial "cipher" for demonstration

KEY_BITS = 16
MASK = (1 << KEY_BITS) - 1

k1_real = int.from_bytes(os.urandom(2), "big") & MASK
k2_real = int.from_bytes(os.urandom(2), "big") & MASK
plaintext = 0xBEEF
ciphertext = toy_encrypt(k2_real, toy_encrypt(k1_real, plaintext))

# --- Meet in the middle ---
# Forward table: intermediate value after encrypting with every k1
forward = {}
for k1 in range(MASK + 1):
    forward[toy_encrypt(k1, plaintext)] = k1

# Backward search: for every k2, "undo" the second encryption and check
# whether that intermediate value appears in the forward table
found = None
for k2 in range(MASK + 1):
    intermediate = toy_encrypt(k2, ciphertext)   # XOR is self-inverse here
    if intermediate in forward:
        found = (forward[intermediate], k2)
        break

print("Recovered keys:", found, "| actual keys:", (k1_real, k2_real))
# Recovered keys: (k1_real, k2_real) | actual: same — found in ~2^16
# operations total instead of 2^32 naive brute force over both keys.
```

## Replay Attacks

--> A replay attack doesn't break the cryptography at all — it simply captures a validly authenticated message and resends it later, when it is no longer intended to be valid, exploiting the fact that "authenticated" only proves origin and integrity, not freshness.
--> Concrete example: an attacker captures a legitimate "transfer $100" API request (correctly signed/MAC'd) and replays it 50 times — each replay passes signature verification perfectly, since nothing about the message changed.
--> Defenses:
--> 1. **Nonces** — a unique, single-use value included in each request; the server tracks used nonces (within a validity window) and rejects duplicates.
--> 2. **Timestamps** — include a timestamp in the signed payload and reject requests outside an acceptable clock-skew window (e.g. ±5 minutes); bounds how long a captured message stays replayable, and lets the server keep only a bounded window of seen-nonce history rather than forever.
--> 3. **Sequence numbers** — TLS records and TCP itself use monotonically increasing sequence numbers baked into the authenticated data, so a replayed record is out of sequence and rejected.
--> 4. **Session-bound keys / channel binding** — deriving fresh keys per session (as ECDHE does) means a captured message can't even be replayed into a different session context meaningfully.

```python
import hmac
import hashlib
import time
import os

SEEN_NONCES = {}   # in production: a shared cache like Redis with TTL
NONCE_WINDOW_SECONDS = 300

def build_request(key: bytes, payload: bytes):
    nonce = os.urandom(16).hex()
    timestamp = str(int(time.time()))
    signed_data = payload + nonce.encode() + timestamp.encode()
    tag = hmac.new(key, signed_data, hashlib.sha256).hexdigest()
    return {"payload": payload, "nonce": nonce, "timestamp": timestamp, "tag": tag}

def verify_request(key: bytes, request: dict) -> bool:
    now = int(time.time())
    ts = int(request["timestamp"])

    if abs(now - ts) > NONCE_WINDOW_SECONDS:
        print("rejected: timestamp outside acceptable window")
        return False

    if request["nonce"] in SEEN_NONCES:
        print("rejected: nonce already used (replay detected)")
        return False

    signed_data = request["payload"] + request["nonce"].encode() + request["timestamp"].encode()
    expected_tag = hmac.new(key, signed_data, hashlib.sha256).hexdigest()
    if not hmac.compare_digest(expected_tag, request["tag"]):
        print("rejected: bad signature")
        return False

    SEEN_NONCES[request["nonce"]] = now   # mark as used
    return True

key = os.urandom(32)
req = build_request(key, b"transfer:100:to:bob")

print(verify_request(key, req))   # True — first use, accepted
print(verify_request(key, req))   # False — replay of the exact same request, rejected
```

## Downgrade Attacks (POODLE and the Broader Class)

--> A downgrade attack doesn't break any strong cipher directly — it tricks or forces the two endpoints into negotiating down to a WEAKER, already-broken option that a protocol still supports for legacy compatibility, then breaks that.
--> **POODLE** (2014, Padding Oracle On Downgraded Legacy Encryption) is the canonical example: attackers exploited the fact that many TLS clients, on a failed connection, would automatically retry with an older, weaker protocol version (SSL 3.0). SSL 3.0's CBC-mode padding is not authenticated the same way TLS 1.2+ requires and includes ignorable padding bytes at the end — an active network attacker (typically via a man-in-the-middle position, e.g. malicious Wi-Fi) could force a retry down to SSLv3 and then run a padding-oracle-style attack (same family as covered above) to decrypt small pieces of supposedly-encrypted traffic, like session cookies, one byte at a time.
--> Attack shape common to the whole downgrade class: 1) attacker interferes with the initial (strong) handshake attempt so it appears to fail, 2) client's compatibility fallback logic retries with a weaker option, 3) attacker now only has to break the weak option, which was already broken.
--> Defenses against the class broadly: 1) remove support for the weak protocol/cipher entirely (the actual fix for POODLE — disable SSLv3 everywhere), 2) cryptographically bind the negotiation itself so tampering with it is detectable (TLS 1.2's `TLS_FALLBACK_SCSV` signals "I am only retrying because of a failure, don't let this look like my real preference" so a server can reject a suspicious downgrade; TLS 1.3 goes further and removes silent version fallback from the protocol design entirely, plus signs over the entire negotiation transcript so any tampering is caught).
--> Broader lesson beyond POODLE: any protocol offering multiple algorithm/version choices for "compatibility" is a downgrade-attack surface by construction — the safest posture is to support the minimum necessary set of strong options and make negotiation tamper-evident, rather than supporting broad backward compatibility indefinitely.
