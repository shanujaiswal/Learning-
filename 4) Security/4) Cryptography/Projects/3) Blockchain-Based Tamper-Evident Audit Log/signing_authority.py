"""
signing_authority.py

The logging service's real Ed25519 signing identity.

In a real compliance system, the machine/service that writes audit entries
holds a private key (ideally in an HSM / KMS) and signs every entry it
produces. Anyone downstream can verify entries with the corresponding public
key, but nobody without the private key can mint a new, validly-signed
entry -- including an attacker who has gained raw read/write access to the
audit log's storage (a database, a flat file, an S3 bucket, etc.).

This module uses the `cryptography` library's real Ed25519 implementation
(RFC 8032) -- no toy crypto.
"""

from __future__ import annotations

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives.asymmetric.ed25519 import (
    Ed25519PrivateKey,
    Ed25519PublicKey,
)


class SigningAuthority:
    """
    Represents the logging service's identity: a real Ed25519 keypair.

    Generated fresh per-process here for demonstration purposes. In
    production this would be loaded from a protected key store rather than
    generated in memory each run.
    """

    def __init__(self) -> None:
        self._private_key: Ed25519PrivateKey = Ed25519PrivateKey.generate()
        self._public_key: Ed25519PublicKey = self._private_key.public_key()

    @property
    def public_key(self) -> Ed25519PublicKey:
        return self._public_key

    def public_key_bytes(self) -> bytes:
        """Raw public key bytes -- what you'd distribute to auditors/verifiers."""
        from cryptography.hazmat.primitives import serialization

        return self._public_key.public_bytes(
            encoding=serialization.Encoding.Raw,
            format=serialization.PublicFormat.Raw,
        )

    def sign(self, block_hash_hex: str) -> bytes:
        """
        Sign a block's hash (hex string) with the service's private key.

        Called exactly once, at block-creation time, by audit_chain.py.
        """
        return self._private_key.sign(block_hash_hex.encode("utf-8"))


def verify_signature(
    public_key: Ed25519PublicKey, block_hash_hex: str, signature: bytes
) -> bool:
    """
    Public verification function: does `signature` validly correspond to
    `block_hash_hex` under `public_key`?

    Returns True/False rather than raising, so chain_verifier.py can treat
    it as a simple boolean check while walking the chain.
    """
    if signature is None:
        return False
    try:
        public_key.verify(signature, block_hash_hex.encode("utf-8"))
        return True
    except InvalidSignature:
        return False
