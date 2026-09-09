"""
06_hmac_and_kdf.py
---------------------
Demonstrates the "MACs / KDFs" chapter with two real, working pieces:

    1. HMAC (Hash-based Message Authentication Code)
       - Proves a message came from someone who holds a shared secret
         key AND that the message was not altered in transit.
       - Generated with Python's built-in `hmac` + `hashlib` (SHA-256).
       - Verified using `hmac.compare_digest`, a CONSTANT-TIME comparison
         function -- using `==` here would leak timing information an
         attacker could exploit (see script 07 for a live demonstration
         of exactly this class of vulnerability).
       - Demonstrates a tampered message correctly failing verification.

    2. Password-Based Key Derivation (KDF)
       - Turns a low-entropy human password into a cryptographically
         strong symmetric key, suitable for e.g. encrypting a file with
         AES-256-GCM (script 02).
       - Uses Scrypt via the `cryptography` library: an intentionally
         memory-hard and CPU-hard function, so brute-forcing many
         candidate passwords is expensive even for attackers with GPUs/
         ASICs. (PBKDF2 is shown too, for comparison -- Scrypt/Argon2
         are generally preferred for new designs because of their
         memory-hardness.)

Install:
    pip install cryptography
"""

import hmac
import hashlib
import os

from cryptography.hazmat.primitives.kdf.scrypt import Scrypt
from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC
from cryptography.hazmat.primitives import hashes


# ---------------------------------------------------------------------------
# HMAC
# ---------------------------------------------------------------------------

def compute_hmac(key: bytes, message: bytes) -> bytes:
    return hmac.new(key, message, hashlib.sha256).digest()


def verify_hmac(key: bytes, message: bytes, tag: bytes) -> bool:
    """Recompute the HMAC and compare using a constant-time function.
    NEVER compare MACs/hashes/tokens with `==` -- see script 07.
    """
    expected = compute_hmac(key, message)
    return hmac.compare_digest(expected, tag)


def demo_hmac():
    print("=" * 70)
    print("HMAC-SHA256")
    print("=" * 70)

    key = os.urandom(32)
    message = b"account_id=42&action=withdraw&amount=500"

    tag = compute_hmac(key, message)
    print(f"Shared key : {key.hex()}")
    print(f"Message    : {message.decode()}")
    print(f"HMAC tag   : {tag.hex()}")

    ok = verify_hmac(key, message, tag)
    print(f"\nverify_hmac(original message, correct tag) -> {ok} (expected True)")
    assert ok is True

    tampered_message = b"account_id=42&action=withdraw&amount=5000"
    tampered_ok = verify_hmac(key, tampered_message, tag)
    print(f"verify_hmac(tampered message,  same tag)    -> {tampered_ok} (expected False)")
    assert tampered_ok is False

    print("\nRound-trip OK: HMAC detects tampering; genuine message/tag pair verifies.\n")


# ---------------------------------------------------------------------------
# KDF: Scrypt (preferred) and PBKDF2 (comparison)
# ---------------------------------------------------------------------------

def derive_key_scrypt(password: str, salt: bytes, *, length: int = 32,
                       n: int = 2 ** 14, r: int = 8, p: int = 1) -> bytes:
    """n=CPU/memory cost, r=block size, p=parallelization. Higher n/r/p
    means slower, more memory-hungry derivation -- good for attackers'
    hardware, bad for legitimate one-time logins, so tune to taste.
    """
    kdf = Scrypt(salt=salt, length=length, n=n, r=r, p=p)
    return kdf.derive(password.encode("utf-8"))


def derive_key_pbkdf2(password: str, salt: bytes, *, length: int = 32,
                       iterations: int = 600_000) -> bytes:
    kdf = PBKDF2HMAC(algorithm=hashes.SHA256(), length=length, salt=salt,
                      iterations=iterations)
    return kdf.derive(password.encode("utf-8"))


def demo_kdf():
    print("=" * 70)
    print("PASSWORD-BASED KEY DERIVATION (Scrypt & PBKDF2)")
    print("=" * 70)

    password = "correct-horse-battery-staple"
    salt = os.urandom(16)  # unique, random per user/secret -- store alongside the derived data

    scrypt_key = derive_key_scrypt(password, salt)
    print(f"Password : {password!r}")
    print(f"Salt     : {salt.hex()}")
    print(f"Scrypt key (32 bytes): {scrypt_key.hex()}")

    # Re-derive with the SAME salt to prove determinism (needed to later
    # decrypt data that was encrypted with this derived key).
    scrypt_key_again = derive_key_scrypt(password, salt)
    print(f"Re-derived key matches: {scrypt_key == scrypt_key_again} (expected True)")
    assert scrypt_key == scrypt_key_again

    # Different password -> completely different key.
    wrong_key = derive_key_scrypt("wrong-password", salt)
    print(f"Wrong-password key matches original: {wrong_key == scrypt_key} (expected False)")
    assert wrong_key != scrypt_key

    pbkdf2_key = derive_key_pbkdf2(password, salt)
    print(f"\nPBKDF2 key (32 bytes) : {pbkdf2_key.hex()}")
    print("(Scrypt and PBKDF2 intentionally produce different keys -- they are")
    print(" different algorithms. Pick ONE and use it consistently per system.)")

    print("\nThis derived key is now ready to be used directly as an AES-256")
    print("key (see script 02) for password-based file/data encryption.")


def main():
    demo_hmac()
    demo_kdf()


if __name__ == "__main__":
    main()
