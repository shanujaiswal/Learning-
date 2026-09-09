"""
key_derivation.py
==================

Turns a human master password into a 256-bit local encryption key, WITHOUT the
master password (or anything derived that could reveal it) ever leaving this
module or being written to disk/network.

Design choices
--------------
- PBKDF2-HMAC-SHA256 via stdlib `hashlib` (no extra dependency, FIPS-friendly,
  what Bitwarden used for years as its default KDF).
- 600,000 iterations -- OWASP's 2023 recommended minimum for PBKDF2-SHA256.
  This makes each guess deliberately slow (tens of milliseconds), so an
  attacker who steals the salt (salts are NOT secret) still can't brute-force
  the master password quickly.
- A random 16-byte salt is generated per user at signup time and stored
  alongside the (encrypted) vault. The salt is not a secret -- its only job is
  to make sure the same master password produces a different key for every
  user, defeating precomputed rainbow-table attacks (see the "Salting and
  Rainbow Tables" section of the hashing theory notes).

Nothing in this file ever persists the master password itself -- only the
derived key is returned, and callers are expected to zero/discard it after use
(Python can't truly guarantee memory zeroing, but we still keep the key's
lifetime as short as possible in the surrounding code).
"""

from __future__ import annotations

import hashlib
import os

# --- Tunable KDF parameters -------------------------------------------------

# OWASP (2023) recommends >= 600,000 iterations for PBKDF2-HMAC-SHA256.
PBKDF2_ITERATIONS = 600_000

# AES-256 needs a 32-byte (256-bit) key.
KEY_LENGTH_BYTES = 32

# 16 bytes (128 bits) of randomness is the standard salt size.
SALT_LENGTH_BYTES = 16


def generate_salt() -> bytes:
    """Generate a fresh, cryptographically random per-user salt.

    Called exactly once, at account-creation time. The salt is not secret and
    is safe to store in plaintext next to the encrypted vault.
    """
    return os.urandom(SALT_LENGTH_BYTES)


def derive_key(master_password: str, salt: bytes,
                iterations: int = PBKDF2_ITERATIONS) -> bytes:
    """Derive a 32-byte AES-256 key from a master password + salt.

    This is deliberately slow (hundreds of milliseconds) so that an attacker
    who obtains the salt and the encrypted vault still cannot brute-force
    candidate master passwords quickly. The master password itself is only
    ever held in local process memory for the duration of this call.
    """
    if not master_password:
        raise ValueError("master_password must not be empty")
    if len(salt) != SALT_LENGTH_BYTES:
        raise ValueError(f"salt must be {SALT_LENGTH_BYTES} bytes")

    key = hashlib.pbkdf2_hmac(
        "sha256",
        master_password.encode("utf-8"),
        salt,
        iterations,
        dklen=KEY_LENGTH_BYTES,
    )
    return key


if __name__ == "__main__":
    # Small smoke test / demonstration when run directly.
    import time

    salt = generate_salt()
    start = time.perf_counter()
    key = derive_key("correct horse battery staple", salt)
    elapsed = time.perf_counter() - start

    print(f"Salt (hex):        {salt.hex()}")
    print(f"Derived key (hex): {key.hex()}")
    print(f"Key length:        {len(key)} bytes")
    print(f"Derivation took:   {elapsed * 1000:.1f} ms "
          f"({PBKDF2_ITERATIONS:,} PBKDF2-HMAC-SHA256 iterations)")
