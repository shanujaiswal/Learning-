"""
vault_crypto.py
================

Encrypts and decrypts the user's vault (a JSON list of site/username/password
entries) using AES-256-GCM with the key produced by `key_derivation.py`.

Why AES-GCM specifically
------------------------
AES-GCM is an "authenticated encryption" (AEAD) mode: on top of confidentiality
it produces a 16-byte authentication tag that cryptographically binds the
ciphertext to the exact key and nonce used. This means:

- Decrypting with the WRONG key does not silently produce garbled-but-present
  JSON -- it raises `InvalidTag` and we treat that as a hard failure. There is
  no "partial decrypt" concept in GCM: either the tag verifies and you get the
  exact original plaintext, or it doesn't and you get nothing.
- Any tampering with the ciphertext (bit flips, truncation, an attacker
  swapping in a different blob) is also caught by the same tag check, so the
  "server" cannot quietly corrupt or splice vault data without detection.

A fresh random 12-byte nonce is generated for every encryption call and stored
alongside the ciphertext (nonces are not secret, but must never be reused with
the same key -- a fresh os.urandom(12) per encryption keeps that guarantee for
all practical purposes).
"""

from __future__ import annotations

import json
import os
from dataclasses import dataclass
from typing import Any

from cryptography.exceptions import InvalidTag
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

NONCE_LENGTH_BYTES = 12  # 96 bits, the recommended nonce size for AES-GCM


class VaultDecryptionError(Exception):
    """Raised when a vault blob cannot be decrypted/authenticated.

    Covers both "wrong master password" (wrong derived key) and "tampered
    ciphertext" -- from the caller's point of view both simply mean "this
    blob cannot be trusted", and callers must not try to fall back to reading
    the raw bytes.
    """


@dataclass(frozen=True)
class EncryptedBlob:
    """Everything the 'server' is allowed to see and store."""
    nonce: bytes
    ciphertext: bytes  # includes the 16-byte GCM auth tag, appended by AESGCM

    def to_dict(self) -> dict:
        return {
            "nonce": self.nonce.hex(),
            "ciphertext": self.ciphertext.hex(),
        }

    @staticmethod
    def from_dict(data: dict) -> "EncryptedBlob":
        return EncryptedBlob(
            nonce=bytes.fromhex(data["nonce"]),
            ciphertext=bytes.fromhex(data["ciphertext"]),
        )


def encrypt_vault(entries: list[dict[str, Any]], key: bytes) -> EncryptedBlob:
    """Serialize `entries` to JSON and encrypt with AES-256-GCM.

    `key` must be the 32-byte key produced by `key_derivation.derive_key`.
    """
    if len(key) != 32:
        raise ValueError("AES-256-GCM requires a 32-byte key")

    plaintext = json.dumps(entries).encode("utf-8")
    nonce = os.urandom(NONCE_LENGTH_BYTES)
    aesgcm = AESGCM(key)
    ciphertext = aesgcm.encrypt(nonce, plaintext, associated_data=None)
    return EncryptedBlob(nonce=nonce, ciphertext=ciphertext)


def decrypt_vault(blob: EncryptedBlob, key: bytes) -> list[dict[str, Any]]:
    """Decrypt and parse the vault. Raises VaultDecryptionError on ANY
    failure -- wrong key, tampered ciphertext, or corrupted data -- so callers
    never see partial/garbage plaintext.
    """
    if len(key) != 32:
        raise ValueError("AES-256-GCM requires a 32-byte key")

    aesgcm = AESGCM(key)
    try:
        plaintext = aesgcm.decrypt(blob.nonce, blob.ciphertext, associated_data=None)
    except InvalidTag as exc:
        # This is the crucial security property: a wrong key (e.g. from a
        # wrong master password) or any tampering fails HERE, loudly, instead
        # of returning corrupted-but-present data.
        raise VaultDecryptionError(
            "Failed to decrypt vault: wrong master password or corrupted/"
            "tampered data (GCM authentication tag did not verify)."
        ) from exc

    try:
        entries = json.loads(plaintext.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        # Defense in depth: should be unreachable if the tag verified, since
        # GCM already guarantees plaintext integrity. Kept as a belt-and-braces
        # check in case of future format changes.
        raise VaultDecryptionError(
            "Vault decrypted but content was not valid JSON."
        ) from exc

    return entries


if __name__ == "__main__":
    from key_derivation import derive_key, generate_salt

    salt = generate_salt()
    key = derive_key("correct horse battery staple", salt)
    wrong_key = derive_key("totally wrong guess", salt)

    entries = [{"site": "example.com", "username": "alice", "password": "hunter2"}]

    blob = encrypt_vault(entries, key)
    print("Ciphertext (hex):", blob.ciphertext.hex())

    decrypted = decrypt_vault(blob, key)
    print("Decrypted with correct key:", decrypted)

    try:
        decrypt_vault(blob, wrong_key)
        print("ERROR: wrong key decrypted successfully -- this should never happen!")
    except VaultDecryptionError as e:
        print("Correctly rejected wrong key:", e)
