# Secure Password Manager with Zero-Knowledge Architecture

## Real-world scenario

You use a password manager (Bitwarden, 1Password, LastPass) to store dozens of
site logins. You trust it with your entire digital life, yet you also trust
it to sync your vault to the company's cloud servers so you can access it
from your phone, laptop, and browser extension. How can both be true at once
— how can a company whose servers get breached constantly (LastPass, 2022)
still not be able to hand your passwords to an attacker?

The answer is a **zero-knowledge architecture**: your master password never
leaves your device, and it never touches the server in any form — not
hashed, not encrypted, not as a "verifier". Instead, your device runs the
master password through a deliberately slow Key Derivation Function (KDF) to
produce a local encryption key. That key encrypts your vault (all your saved
site passwords) with an authenticated cipher, and only the resulting
ciphertext blob is uploaded. The server's entire job is to store and return
opaque bytes it cannot interpret — it could be fully hacked, subpoenaed, or
run by a malicious insider, and it would still have nothing usable to hand
over.

This project builds a minimal but faithful simulation of that architecture,
end to end, and proves each of its security properties by actually running
the attack/failure paths (wrong password, server-side snooping) rather than
just asserting them.

## Architecture

| Module | Role | Real-world equivalent |
|---|---|---|
| `key_derivation.py` | Derives a 256-bit local encryption key from the master password + a per-user random salt via PBKDF2-HMAC-SHA256 (600,000 iterations) | Bitwarden's / 1Password's PBKDF2 (or Argon2) master-key derivation, run entirely client-side |
| `vault_crypto.py` | Encrypts/decrypts the JSON vault with AES-256-GCM; wrong key or tampered ciphertext fails via the GCM auth tag, never silent garbage | The client-side AES-256-bit encryption Bitwarden/1Password apply to your vault before it ever leaves the device |
| `zero_knowledge_server.py` | Simulated backend: stores only `(salt, nonce, ciphertext)` per user, exposes no decrypt method, and can prove it cannot parse its own stored data | Bitwarden's / 1Password's cloud sync service — "we store your encrypted vault, we cannot read it" |
| `password_health_checker.py` | Scans decrypted vault entries for common/weak passwords and cross-entry reuse | Bitwarden's "Password Health" report / 1Password's "Watchtower" / browser "Password Checkup" |
| `main.py` | Orchestrates the full story: create → lock → upload → unlock (correct) → unlock (wrong, fails) → health check → prove-cannot-read | The end-to-end user flow across a password manager's client + cloud sync |

### Why AES-**GCM** specifically

GCM is an AEAD (Authenticated Encryption with Associated Data) mode: it
produces ciphertext **plus** a 16-byte authentication tag cryptographically
bound to the exact key and nonce used. Decrypting with any other key does not
yield corrupted-but-present JSON — `cryptography`'s `AESGCM.decrypt()` raises
`InvalidTag` before any plaintext is returned at all. This is what makes "wrong
master password → clean failure, not garbage" actually true at the crypto
level, not just something the app chooses to check afterward.

### Why the KDF is slow on purpose

SHA-256 alone can be computed billions of times per second on a GPU, which is
disastrous for password-derived keys — see the "Why Fast General-Purpose
Hashes Are Bad for Passwords" section of `Theory/04 Hashing and Password
Storage.md`. PBKDF2 with 600,000 iterations (OWASP's 2023 minimum
recommendation) makes each *guess* cost hundreds of milliseconds, so an
attacker who steals the salt and ciphertext still can't brute-force the
master password at scale.

## Run it

```bash
cd "3) Security/4) Cryptography/Projects/2) Secure Password Manager with Zero-Knowledge Architecture"
python main.py
```

Requires the `cryptography` package (`pip install cryptography`) — everything
else is Python stdlib.

Optional: run any module standalone for a smaller, focused demo, e.g.
`python vault_crypto.py` or `python zero_knowledge_server.py`.

## Verified result (actual output)

The following is the real, unedited output from running `python main.py`
(hex values such as salts/nonces/ciphertext are random per run, everything
else — including which steps succeed/fail — is deterministic):

```
==============================================================================
STEP 1: Create local vault with saved entries
==============================================================================
  + github.com                       user=alice.dev            password=K7$mQ2!vXzL9pR4w
  + email.provider.com               user=alice@example.com    password=Tr0ub4dor&3-uniq
  + old-forum.example.com            user=alice123             password=123456
  + shopping-site.example.com        user=alice                password=hunter2
  + streaming-service.example.com    user=alice_w              password=hunter2

==============================================================================
STEP 2: Lock the vault (derive key + AES-256-GCM encrypt)
==============================================================================
  Generated per-user random salt: 1cb9ae9894fbd16909ac0e932aa6e9d5
  Deriving key from master password via PBKDF2-HMAC-SHA256 (600,000 iterations)...
  Derived 256-bit key (hex, for demo only): 6b04bcfe00efe129155eeacfb68f1b3338539c783a09b6c52f163f5b269e2b52
  Encrypted vault: nonce=2c22d2e97c0daa8d82efbadf
  Ciphertext (hex, first 60 chars): 53c4f2dd20ef14faad5f9f6774472d1c039aa5a01cfb3065d63e5735215a...
  Ciphertext length: 447 bytes (plaintext length + 16-byte GCM auth tag)

==============================================================================
STEP 3: Upload encrypted blob to the zero-knowledge server
==============================================================================
[server] stored encrypted vault for 'alice' (447 bytes ciphertext, 16-byte salt).

==============================================================================
STEP 4: Unlock vault with the CORRECT master password
==============================================================================
[server] sent encrypted vault for 'alice' to client (server has no way to read its contents).
  Decryption SUCCEEDED. Recovered entries:
    - github.com                       user=alice.dev            password=K7$mQ2!vXzL9pR4w
    - email.provider.com               user=alice@example.com    password=Tr0ub4dor&3-uniq
    - old-forum.example.com            user=alice123             password=123456
    - shopping-site.example.com        user=alice                password=hunter2
    - streaming-service.example.com    user=alice_w              password=hunter2

==============================================================================
STEP 5: Attempt to unlock with a WRONG master password
==============================================================================
[server] sent encrypted vault for 'alice' to client (server has no way to read its contents).
  Decryption FAILED CLEANLY, as expected: Failed to decrypt vault: wrong master password or corrupted/tampered data (GCM authentication tag did not verify).
  No partial or garbled vault data was returned to the caller.

==============================================================================
STEP 6: Password health / breach-pattern check
==============================================================================
Scanned 5 vault entries.
  WEAK passwords (3):
    - old-forum.example.com (user: alice123) -> '123456'
    - shopping-site.example.com (user: alice) -> 'hunter2'
    - streaming-service.example.com (user: alice_w) -> 'hunter2'
  REUSED passwords (2 entries affected):
    - 'hunter2' reused across: shopping-site.example.com, streaming-service.example.com

==============================================================================
STEP 7: Prove the server cannot read the vault
==============================================================================
Attempting to read vault for 'alice' using ONLY server-side data...
  Raw ciphertext (hex, first 32 bytes): 53c4f2dd20ef14faad5f9f6774472d1c039aa5a01cfb3065d63e5735215acb74...
  UTF-8 decode:  FAILED ('utf-8' codec can't decode byte 0xc4 in position 1: invalid continuation byte)
  JSON parse:    FAILED (UnicodeDecodeError: 'utf-8' codec can't decode byte 0xc4 in position 1: invalid continuation byte)
  Conclusion: without the master password (never sent to or stored by this server), the ciphertext is indistinguishable from random noise. The server cannot recover the vault.

==============================================================================
DONE
==============================================================================
Summary: correct master password unlocked the real vault; wrong
master password failed cleanly via the AES-GCM auth tag; the
server-held blob is confirmed to be unreadable ciphertext; and
the health checker flagged the weak/reused saved passwords.
```

This confirms all four required properties:
1. **Correct master password** derives the right key and decrypts real entries.
2. **Wrong master password** fails cleanly via `InvalidTag` / `VaultDecryptionError` — no partial or corrupted plaintext is ever produced or returned.
3. **Server-stored blob is confirmed unreadable** — `prove_cannot_read()` shows both a UTF-8 decode and a JSON parse failing against the raw stored ciphertext.
4. **Password health check** flags `123456` as weak and `hunter2` as both weak (in the common-password list) and reused across two sites.

## Things to try changing

- **Swap PBKDF2 for scrypt**: replace `hashlib.pbkdf2_hmac(...)` in
  `key_derivation.py` with `cryptography.hazmat.primitives.kdf.scrypt.Scrypt`
  to make the KDF memory-hard as well as CPU-slow (harder to accelerate on
  GPUs/ASICs) — see the "bcrypt / scrypt / Argon2" section of the theory notes.
- **Lower `PBKDF2_ITERATIONS`** drastically (e.g. to `1_000`) and re-run —
  notice the derivation time in Step 2 drops from tens of milliseconds toward
  near-instant, illustrating exactly why iteration count matters for
  brute-force resistance.
- **Tamper with the stored ciphertext** before Step 4 (flip one byte in
  `encrypted_blob.ciphertext`) and confirm it now fails the *same* way as a
  wrong password — proving GCM catches tampering, not just wrong keys.
- **Expand `COMMON_WEAK_PASSWORDS`** in `password_health_checker.py` with a
  real "top 10k breached passwords" list, or wire up an actual k-anonymity
  call to the Have I Been Pwned Pwned Passwords API instead of a local list.
- **Add a second user** to `zero_knowledge_server.py`'s demo and confirm each
  user's salt/key/vault are fully independent — no cross-user key reuse.
- **Simulate multi-device sync**: derive the key twice from the same master
  password + stored salt (once "on laptop", once "on phone") and confirm both
  independently decrypt the same server blob without ever exchanging keys.
