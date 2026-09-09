"""
01_classical_ciphers.py
------------------------
Demonstrates the "Classical Ciphers" chapter with correct, from-scratch
implementations of:
    1. Caesar cipher (shift cipher)
    2. Vigenere cipher (polyalphabetic substitution)

Both ciphers are implemented for encryption AND decryption, and each is
round-tripped on a test string to prove correctness.

These are NOT secure for real use (they are trivially breakable via
frequency analysis / brute force -- see the Theory chapter on Attacks I).
They are included purely to build intuition for substitution ciphers
before moving on to modern, secure primitives (AES, RSA, etc.) in the
other scripts in this folder.

No external dependencies required.
"""

import string

ALPHABET = string.ascii_uppercase
ALPHABET_SIZE = len(ALPHABET)


# ---------------------------------------------------------------------------
# Caesar Cipher
# ---------------------------------------------------------------------------

def caesar_encrypt(plaintext: str, shift: int) -> str:
    """Shift every letter forward by `shift` positions in the alphabet.

    Non-alphabetic characters (spaces, punctuation, digits) are left
    unchanged. Case is preserved.
    """
    result = []
    for ch in plaintext:
        if ch.isalpha():
            base = ord('A') if ch.isupper() else ord('a')
            shifted = (ord(ch) - base + shift) % ALPHABET_SIZE
            result.append(chr(base + shifted))
        else:
            result.append(ch)
    return "".join(result)


def caesar_decrypt(ciphertext: str, shift: int) -> str:
    """Decryption is just encryption with the negated shift."""
    return caesar_encrypt(ciphertext, -shift)


def caesar_brute_force(ciphertext: str) -> None:
    """Demonstrate why Caesar is broken: try all 26 shifts."""
    print("  Brute-forcing all 26 possible shifts:")
    for shift in range(ALPHABET_SIZE):
        print(f"    shift={shift:2d}: {caesar_decrypt(ciphertext, shift)}")


# ---------------------------------------------------------------------------
# Vigenere Cipher
# ---------------------------------------------------------------------------

def _keystream(text_len: int, key: str):
    """Yield the repeating key letters, one per alphabetic character
    position needed. Only uppercase letters are used for the key
    regardless of the case of the plaintext.
    """
    key = [c.upper() for c in key if c.isalpha()]
    if not key:
        raise ValueError("Vigenere key must contain at least one letter")
    i = 0
    while True:
        yield key[i % len(key)]
        i += 1


def vigenere_encrypt(plaintext: str, key: str) -> str:
    ks = _keystream(len(plaintext), key)
    result = []
    for ch in plaintext:
        if ch.isalpha():
            base = ord('A') if ch.isupper() else ord('a')
            k = ord(next(ks)) - ord('A')
            shifted = (ord(ch) - base + k) % ALPHABET_SIZE
            result.append(chr(base + shifted))
        else:
            result.append(ch)
    return "".join(result)


def vigenere_decrypt(ciphertext: str, key: str) -> str:
    ks = _keystream(len(ciphertext), key)
    result = []
    for ch in ciphertext:
        if ch.isalpha():
            base = ord('A') if ch.isupper() else ord('a')
            k = ord(next(ks)) - ord('A')
            shifted = (ord(ch) - base - k) % ALPHABET_SIZE
            result.append(chr(base + shifted))
        else:
            result.append(ch)
    return "".join(result)


# ---------------------------------------------------------------------------
# Demo / self-test
# ---------------------------------------------------------------------------

def main():
    test_string = "Attack at Dawn, Meet Behind the Old Mill!"

    print("=" * 70)
    print("CAESAR CIPHER")
    print("=" * 70)
    shift = 7
    ct = caesar_encrypt(test_string, shift)
    pt = caesar_decrypt(ct, shift)
    print(f"Plaintext : {test_string}")
    print(f"Shift     : {shift}")
    print(f"Ciphertext: {ct}")
    print(f"Decrypted : {pt}")
    assert pt == test_string, "Caesar round-trip FAILED"
    print("Round-trip OK: decrypted text matches original.\n")

    caesar_brute_force(caesar_encrypt("MEET ME AT NOON", 3))

    print()
    print("=" * 70)
    print("VIGENERE CIPHER")
    print("=" * 70)
    key = "LEMON"
    ct2 = vigenere_encrypt(test_string, key)
    pt2 = vigenere_decrypt(ct2, key)
    print(f"Plaintext : {test_string}")
    print(f"Key       : {key}")
    print(f"Ciphertext: {ct2}")
    print(f"Decrypted : {pt2}")
    assert pt2 == test_string, "Vigenere round-trip FAILED"
    print("Round-trip OK: decrypted text matches original.")


if __name__ == "__main__":
    main()
