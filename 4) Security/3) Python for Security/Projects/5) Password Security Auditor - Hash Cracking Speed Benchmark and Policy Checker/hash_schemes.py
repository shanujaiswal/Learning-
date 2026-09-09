"""
hash_schemes.py

AUTHORIZED USE ONLY: educational, self-contained demo. Only run against data you
generated yourself, on your own machine.

Implements TWO password storage schemes so we can measure, not just claim, the
difference between them:

  1. FAST / WEAK  -> weak_hash()   : raw, unsalted, single-round SHA-256.
                     This is what a lazy/legacy system might do ("we hash our
                     passwords!") while still being trivially crackable, because
                     SHA-256 is *designed* to be fast (gigabytes/sec on a CPU,
                     billions/sec on a GPU) and there is no per-user salt.

  2. SLOW / PROPER -> slow_hash()  : PBKDF2-HMAC-SHA256 with a high iteration
                     count and a random per-password salt, via the stdlib
                     hashlib.pbkdf2_hmac. Each guess now costs `iterations`
                     rounds of HMAC-SHA256 instead of one, which is exactly the
                     "work factor" real password hashing (PBKDF2 / bcrypt /
                     scrypt / Argon2) is built around.

Both schemes are exposed with a matching (hash, verify) interface so the
benchmark and policy modules can treat them interchangeably.
"""

from __future__ import annotations

import hashlib
import hmac
import secrets

# --------------------------------------------------------------------------------------
# Scheme 1: FAST / WEAK — raw, unsalted SHA-256 (do NOT do this in real systems)
# --------------------------------------------------------------------------------------


def weak_hash(password: str, salt: str = "") -> str:
    """Single-round, unsalted (by default) SHA-256 of the password.

    `salt` defaults to "" so callers who don't pass one get the *worst* case:
    completely unsalted hashing, identical to what a naive `hashlib.sha256(pw)`
    call in production code would produce. This is intentionally realistic —
    this exact pattern still shows up in real breached databases.
    """
    return hashlib.sha256((salt + password).encode("utf-8")).hexdigest()


def weak_verify(password: str, salt: str, expected_hash_hex: str) -> bool:
    return hmac.compare_digest(weak_hash(password, salt), expected_hash_hex)


# --------------------------------------------------------------------------------------
# Scheme 2: SLOW / PROPER — salted PBKDF2-HMAC-SHA256 with a high work factor
# --------------------------------------------------------------------------------------

PBKDF2_ALGO = "sha256"
PBKDF2_ITERATIONS = 400_000  # deliberately slow; raise this and crack time grows with it
SALT_BYTES = 16


def slow_hash(password: str, salt: bytes | None = None, iterations: int = PBKDF2_ITERATIONS) -> tuple[bytes, str]:
    """Hash a password with PBKDF2-HMAC-SHA256.

    If `salt` is None a fresh cryptographically secure salt is generated with
    `secrets.token_bytes` (never use `random` for this — see Theory Ch.2).
    Returns (salt_bytes, hash_hex) so the caller can store both.
    """
    if salt is None:
        salt = secrets.token_bytes(SALT_BYTES)
    derived = hashlib.pbkdf2_hmac(PBKDF2_ALGO, password.encode("utf-8"), salt, iterations)
    return salt, derived.hex()


def slow_verify(password: str, salt: bytes, expected_hash_hex: str, iterations: int = PBKDF2_ITERATIONS) -> bool:
    _, candidate = slow_hash(password, salt, iterations)
    return hmac.compare_digest(candidate, expected_hash_hex)


if __name__ == "__main__":
    # Quick smoke test when run directly.
    pw = "hunter2"

    h = weak_hash(pw)
    print("Weak SHA-256 (unsalted):", h)
    print("  verify correct  :", weak_verify(pw, "", h))
    print("  verify incorrect:", weak_verify("wrong", "", h))

    salt, h2 = slow_hash(pw)
    print(f"\nSlow PBKDF2-HMAC-SHA256 ({PBKDF2_ITERATIONS:,} iterations, salt={salt.hex()}):")
    print("  hash            :", h2)
    print("  verify correct  :", slow_verify(pw, salt, h2))
    print("  verify incorrect:", slow_verify("wrong", salt, h2))
