"""
01_password_hashing_toolkit.py

AUTHORIZED USE ONLY: This script is a self-contained educational demo. Run it only on your own
machine, against data you generated yourself. Do not use it to attack, crack, or attempt to recover
credentials that belong to anyone else or any system you are not explicitly authorized to test.

Integrates Theory Ch.2 (Hashing / HMAC / Secure Randomness) into one coherent tool:

  1. A REAL password hashing/verification utility:
       - random per-password salt generated with `secrets` (cryptographically secure)
       - salted hash computed with hashlib.pbkdf2_hmac (slow, iterated hashing — not a single fast hash)
       - verification done with hmac.compare_digest (constant-time comparison, avoids timing attacks)

  2. A tiny "cracker" demo that brute-forces a deliberately weak, UNSALTED, single-round MD5 hash
     against a small wordlist — to concretely demonstrate, in the same script, why (1) is necessary:
     a weak scheme falls in milliseconds; the PBKDF2+salt scheme in part 1 would not.
"""

import hashlib
import hmac
import secrets

# --------------------------------------------------------------------------------------
# Part 1: Correct, salted, slow password hashing (what you SHOULD do)
# --------------------------------------------------------------------------------------

PBKDF2_ALGO = "sha256"
PBKDF2_ITERATIONS = 200_000   # deliberately slow to resist brute force / GPU cracking
SALT_BYTES = 16


def hash_password(password: str) -> tuple[str, str]:
    """Hash a password with a fresh random salt using PBKDF2-HMAC-SHA256.

    Returns (salt_hex, hash_hex). Store BOTH alongside the username — never store the
    plaintext password.
    """
    salt = secrets.token_bytes(SALT_BYTES)  # cryptographically secure randomness (Ch.2)
    derived = hashlib.pbkdf2_hmac(
        PBKDF2_ALGO,
        password.encode("utf-8"),
        salt,
        PBKDF2_ITERATIONS,
    )
    return salt.hex(), derived.hex()


def verify_password(password: str, salt_hex: str, expected_hash_hex: str) -> bool:
    """Re-derive the hash from the supplied password + stored salt and compare it in
    constant time using hmac.compare_digest, which avoids leaking timing information
    that a naive `==` comparison could expose.
    """
    salt = bytes.fromhex(salt_hex)
    candidate = hashlib.pbkdf2_hmac(
        PBKDF2_ALGO,
        password.encode("utf-8"),
        salt,
        PBKDF2_ITERATIONS,
    )
    return hmac.compare_digest(candidate.hex(), expected_hash_hex)


# --------------------------------------------------------------------------------------
# Part 2: Mini "cracker" demo against a deliberately WEAK scheme (why part 1 matters)
# --------------------------------------------------------------------------------------

# A small, self-contained wordlist. In real tooling this would be rockyou.txt or similar —
# here we keep it tiny and local so the demo is instant and self-contained.
WORDLIST = [
    "letmein", "password", "123456", "qwerty", "dragon",
    "monkey", "football", "iloveyou", "admin", "welcome",
    "sunshine", "master", "hunter2", "trustno1", "shadow",
]


def weak_unsalted_md5(password: str) -> str:
    """Deliberately weak: single-round MD5, no salt at all. NEVER do this in real code.
    This exists purely so we can demonstrate cracking it in the same script.
    """
    return hashlib.md5(password.encode("utf-8")).hexdigest()


def crack_weak_md5(target_hash: str, wordlist: list[str]) -> str | None:
    """Brute-force a wordlist against an unsalted MD5 hash. Because there's no salt and
    MD5 is extremely fast to compute, this finishes essentially instantly — that speed
    IS the vulnerability we're demonstrating.
    """
    for candidate in wordlist:
        if weak_unsalted_md5(candidate) == target_hash:
            return candidate
    return None


def main() -> None:
    print("=== Part 1: Correct salted PBKDF2 password hashing ===")
    demo_password = "CorrectHorseBatteryStaple42!"
    salt_hex, hash_hex = hash_password(demo_password)
    print(f"Password  : {demo_password}")
    print(f"Salt      : {salt_hex}")
    print(f"Hash      : {hash_hex}")

    print("\nVerifying correct password ...")
    print("  Result:", verify_password(demo_password, salt_hex, hash_hex))

    print("Verifying wrong password ...")
    print("  Result:", verify_password("wrong-guess", salt_hex, hash_hex))

    print(
        "\nNote: even if two users picked the SAME password, their salts differ, so their\n"
        "stored hashes would differ too — this defeats precomputed rainbow-table attacks."
    )

    print("\n=== Part 2: Why weak, unsalted, fast hashing is dangerous ===")
    secret_password = secrets.choice(WORDLIST)  # simulate a user with a weak password
    target = weak_unsalted_md5(secret_password)
    print(f"Simulated leaked hash (unsalted MD5): {target}")
    print(f"(In real life the attacker does NOT know the plaintext '{secret_password}' — we do, for demo purposes.)")

    cracked = crack_weak_md5(target, WORDLIST)
    if cracked:
        print(f"Cracked in a wordlist pass of {len(WORDLIST)} entries -> password was: '{cracked}'")
    else:
        print("Not found in wordlist (try a bigger list).")

    print(
        "\nCompare: the same wordlist attack against the PBKDF2-SHA256 hash from Part 1 would\n"
        f"require {PBKDF2_ITERATIONS:,} hash iterations PER GUESS, per unique salt — orders of\n"
        "magnitude slower, and salts mean precomputed tables don't help at all. That combination\n"
        "(random salt + slow, iterated KDF) is exactly why it is the correct approach."
    )


if __name__ == "__main__":
    main()
