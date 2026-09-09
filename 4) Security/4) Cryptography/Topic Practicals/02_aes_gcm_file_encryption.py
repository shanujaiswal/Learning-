"""
02_aes_gcm_file_encryption.py
------------------------------
Demonstrates the "AES" chapter with a REAL, working AES-256-GCM file
encryption utility built on the `cryptography` library.

AES-GCM is an authenticated encryption mode (AEAD): it provides both
confidentiality (nobody can read the ciphertext without the key) and
integrity/authenticity (any tampering with the ciphertext is detected
at decryption time). This script:

    1. Generates a random 256-bit AES key.
    2. Creates a small demo plaintext file on disk.
    3. Encrypts it into <file>.enc  (format: 12-byte nonce || ciphertext+tag)
    4. Decrypts it back and verifies the content matches the original.
    5. Flips a single byte in the ciphertext and shows that decryption
       fails LOUDLY (raises InvalidTag) instead of silently returning
       corrupted data -- this is the whole point of authenticated
       encryption vs. a bare cipher mode like AES-CBC.

Install:
    pip install cryptography
"""

import os
from pathlib import Path

from cryptography.exceptions import InvalidTag
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

DEMO_DIR = Path(__file__).parent / "_demo_files"
PLAINTEXT_FILE = DEMO_DIR / "secret_message.txt"
ENCRYPTED_FILE = DEMO_DIR / "secret_message.txt.enc"
DECRYPTED_FILE = DEMO_DIR / "secret_message.decrypted.txt"

NONCE_SIZE = 12  # 96 bits, the recommended nonce size for AES-GCM


def generate_key() -> bytes:
    """AES-256 key = 32 random bytes from a cryptographically secure RNG."""
    return AESGCM.generate_key(bit_length=256)


def encrypt_file(key: bytes, in_path: Path, out_path: Path,
                  associated_data: bytes = b"") -> None:
    aesgcm = AESGCM(key)
    nonce = os.urandom(NONCE_SIZE)  # MUST be unique per encryption with this key
    plaintext = in_path.read_bytes()
    ciphertext = aesgcm.encrypt(nonce, plaintext, associated_data)
    # Store nonce alongside ciphertext so decrypt() can retrieve it later.
    out_path.write_bytes(nonce + ciphertext)


def decrypt_file(key: bytes, in_path: Path, out_path: Path,
                  associated_data: bytes = b"") -> None:
    aesgcm = AESGCM(key)
    blob = in_path.read_bytes()
    nonce, ciphertext = blob[:NONCE_SIZE], blob[NONCE_SIZE:]
    plaintext = aesgcm.decrypt(nonce, ciphertext, associated_data)
    out_path.write_bytes(plaintext)


def main():
    DEMO_DIR.mkdir(exist_ok=True)

    # 1. Create a demo plaintext file.
    original_text = (
        "TOP SECRET: The launch codes are hidden inside the cookie jar.\n"
        "Do not share this file with anyone outside the project.\n"
    )
    PLAINTEXT_FILE.write_text(original_text, encoding="utf-8")
    print(f"Created demo file: {PLAINTEXT_FILE}")

    # 2. Generate key and encrypt.
    key = generate_key()
    print(f"Generated AES-256 key (hex): {key.hex()}")

    encrypt_file(key, PLAINTEXT_FILE, ENCRYPTED_FILE)
    print(f"Encrypted -> {ENCRYPTED_FILE} "
          f"({ENCRYPTED_FILE.stat().st_size} bytes: 12-byte nonce + ciphertext+16-byte tag)")

    # 3. Decrypt and verify round-trip.
    decrypt_file(key, ENCRYPTED_FILE, DECRYPTED_FILE)
    recovered_text = DECRYPTED_FILE.read_text(encoding="utf-8")
    assert recovered_text == original_text, "Decryption round-trip FAILED"
    print("Decrypted successfully. Content matches original. Round-trip OK.\n")

    # 4. Tamper detection demo: flip one byte in the stored ciphertext.
    print("=" * 70)
    print("TAMPER DETECTION DEMO")
    print("=" * 70)
    tampered_blob = bytearray(ENCRYPTED_FILE.read_bytes())
    flip_index = len(tampered_blob) - 5  # flip a byte inside the ciphertext/tag
    tampered_blob[flip_index] ^= 0xFF  # flip all bits in that byte
    tampered_path = DEMO_DIR / "secret_message.tampered.enc"
    tampered_path.write_bytes(bytes(tampered_blob))
    print(f"Flipped one byte at offset {flip_index} -> {tampered_path}")

    try:
        decrypt_file(key, tampered_path, DEMO_DIR / "should_not_exist.txt")
        print("ERROR: decryption succeeded on tampered data! This should not happen.")
    except InvalidTag:
        print("Decryption FAILED LOUDLY with InvalidTag, as expected.")
        print("This proves AES-GCM detected the tampering and refused to")
        print("return unauthenticated plaintext -- exactly what an AEAD mode")
        print("is designed to guarantee.")

    print()
    print("Wrong key demo (extra sanity check):")
    wrong_key = generate_key()
    try:
        decrypt_file(wrong_key, ENCRYPTED_FILE, DEMO_DIR / "should_not_exist2.txt")
        print("ERROR: decryption succeeded with the wrong key!")
    except InvalidTag:
        print("Decryption FAILED LOUDLY with InvalidTag when using the wrong key, as expected.")


if __name__ == "__main__":
    main()
