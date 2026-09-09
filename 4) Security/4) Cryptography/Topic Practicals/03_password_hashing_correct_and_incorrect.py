"""
03_password_hashing_correct_and_incorrect.py
----------------------------------------------
Demonstrates the "Hashing / Password Storage" chapter by showing the
WRONG way to store passwords right next to the RIGHT way.

THE WRONG WAY: unsalted MD5
    - MD5 is a fast, general-purpose hash function, NOT a password
      hashing function. Being fast is a feature for checksums and a
      catastrophic flaw for passwords, because it means an attacker who
      steals the hash database can try billions of guesses per second
      on commodity GPUs.
    - Unsalted means two users with the same password get the exact
      same hash. This enables:
        * Precomputed rainbow-table lookups (instant cracking of common
          passwords).
        * Spotting which users share a password just by comparing hashes.
    - MD5 is also cryptographically broken (collision attacks are
      practical), which matters far less here than the speed problem,
      but it's one more reason never to use it for anything sensitive.

THE RIGHT WAY: bcrypt
    - bcrypt is a purpose-built, deliberately SLOW password hashing
      algorithm with a tunable work factor ("cost"), so it can be made
      slower as hardware gets faster.
    - It generates a random salt per password automatically, so
      identical passwords produce different hashes, defeating rainbow
      tables and same-password detection.
    - It is verified with a constant-time comparison built into the
      library, avoiding timing side-channels (see script 07 for a
      demonstration of exactly this class of vulnerability).

Install:
    pip install bcrypt
"""

import hashlib
import time

import bcrypt


# ---------------------------------------------------------------------------
# THE WRONG WAY: unsalted MD5
# ---------------------------------------------------------------------------

def broken_hash_password(password: str) -> str:
    """DO NOT USE THIS IN REAL CODE. Shown only to illustrate the flaw."""
    return hashlib.md5(password.encode("utf-8")).hexdigest()


def demo_broken_md5():
    print("=" * 70)
    print("WRONG WAY: unsalted MD5")
    print("=" * 70)

    password_a = "Password123"
    password_b = "Password123"  # a different user who happens to pick the same password
    password_c = "correct-horse-battery-staple"

    hash_a = broken_hash_password(password_a)
    hash_b = broken_hash_password(password_b)
    hash_c = broken_hash_password(password_c)

    print(f"User A password: {password_a!r} -> MD5: {hash_a}")
    print(f"User B password: {password_b!r} -> MD5: {hash_b}")
    print(f"User C password: {password_c!r} -> MD5: {hash_c}")
    print()
    print(f"User A and User B have IDENTICAL hashes: {hash_a == hash_b}")
    print("-> An attacker who leaks this DB instantly knows A and B share a")
    print("   password, and a single rainbow-table lookup of the hash cracks")
    print("   BOTH accounts at once.")

    # Demonstrate the speed problem: MD5 can be computed millions of times
    # per second, which is exactly what an offline cracker wants.
    n = 200_000
    start = time.perf_counter()
    for _ in range(n):
        hashlib.md5(password_a.encode("utf-8")).hexdigest()
    elapsed = time.perf_counter() - start
    rate = n / elapsed
    print(f"\nComputed {n:,} MD5 hashes in {elapsed:.3f}s "
          f"(~{rate:,.0f} hashes/sec on this machine).")
    print("A GPU-based cracker does many orders of magnitude better than this,")
    print("making brute-force / dictionary attacks on MD5 password hashes")
    print("cheap and fast.\n")


# ---------------------------------------------------------------------------
# THE RIGHT WAY: bcrypt
# ---------------------------------------------------------------------------

def hash_password(password: str, rounds: int = 12) -> bytes:
    """Hash a password with bcrypt. A fresh random salt is generated
    automatically and embedded in the returned hash, so no separate
    salt storage/management is needed.
    """
    salt = bcrypt.gensalt(rounds=rounds)
    return bcrypt.hashpw(password.encode("utf-8"), salt)


def verify_password(password: str, hashed: bytes) -> bool:
    """Constant-time verification against the stored bcrypt hash."""
    return bcrypt.checkpw(password.encode("utf-8"), hashed)


def demo_bcrypt():
    print("=" * 70)
    print("RIGHT WAY: bcrypt (salted, slow, tunable work factor)")
    print("=" * 70)

    password_a = "Password123"
    password_b = "Password123"  # same password as A again

    start = time.perf_counter()
    hash_a = hash_password(password_a)
    hash_b = hash_password(password_b)
    elapsed = time.perf_counter() - start

    print(f"User A password: {password_a!r} -> bcrypt: {hash_a.decode()}")
    print(f"User B password: {password_b!r} -> bcrypt: {hash_b.decode()}")
    print()
    print(f"User A and User B have DIFFERENT hashes despite the same password: "
          f"{hash_a != hash_b}")
    print("-> Each hash embeds its own random salt, so identical passwords")
    print("   never produce identical hashes. Rainbow tables are useless here.")
    print(f"\nHashing both passwords took {elapsed:.3f}s total "
          "(bcrypt is deliberately slow -- tune `rounds` upward as hardware improves).")

    # Correct verification (round trip)
    correct_check = verify_password("Password123", hash_a)
    wrong_check = verify_password("WrongGuess!", hash_a)
    print(f"\nverify_password('Password123', hash_a) -> {correct_check} (expected True)")
    print(f"verify_password('WrongGuess!', hash_a)   -> {wrong_check} (expected False)")
    assert correct_check is True
    assert wrong_check is False
    print("\nRound-trip OK: correct password verifies, wrong password is rejected.")


def main():
    demo_broken_md5()
    demo_bcrypt()


if __name__ == "__main__":
    main()
