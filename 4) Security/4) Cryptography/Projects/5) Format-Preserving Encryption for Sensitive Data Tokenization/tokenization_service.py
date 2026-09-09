"""
tokenization_service.py
------------------------

Wraps the raw `feistel_fpe` primitive with real-world data shapes: credit
card PANs (16 digits) and US Social Security Numbers (9 digits). This is the
layer a payment processor's "tokenization service" would expose to the rest
of the company -- callers never touch the Feistel/HMAC machinery directly,
they just call `tokenize_card_number(pan)` / `detokenize_card_number(token)`
(and the SSN equivalents).

Key management: the service is constructed with a securely generated key
(`secrets.token_bytes`) -- exactly the kind of key a real system would pull
from a hardware security module (HSM) or a secrets manager (AWS KMS, HashiCorp
Vault, etc.) rather than hardcoding. Anyone who does NOT hold this key cannot
reverse a token back to the original value, even though the token is
mathematically derived from the original (see token_vault.py for a concrete
demonstration of that one-wayness-without-the-key property).
"""

from __future__ import annotations

import secrets

from feistel_fpe import decrypt_digits, encrypt_digits, FeistelFPEError

CARD_NUMBER_LENGTH = 16
SSN_LENGTH = 9

# How many bytes of key material to generate. 32 bytes (256 bits) matches
# the output size of the HMAC-SHA256 round function and is the conventional
# key size for that primitive.
KEY_SIZE_BYTES = 32


def generate_key() -> bytes:
    """Securely generate a fresh FPE key using a CSPRNG (stdlib `secrets`).

    In a real deployment this key would be minted once and stored in an
    HSM / KMS, not regenerated per-process -- it's exposed here as a function
    so callers (and main.py) can make that "this key came from secure key
    management" step explicit rather than implicit.
    """
    return secrets.token_bytes(KEY_SIZE_BYTES)


class InvalidCardNumberError(ValueError):
    """Raised when a value handed to the card-number path isn't a 16-digit PAN."""


class InvalidSSNError(ValueError):
    """Raised when a value handed to the SSN path isn't a 9-digit SSN."""


class TokenizationService:
    """A minimal, self-contained stand-in for a PCI-DSS tokenization service.

    Each instance holds exactly one FPE key in memory. `tokenize_*` and
    `detokenize_*` are inverses of one another for callers who hold the same
    service/key -- there is no external lookup table involved, because the
    token itself *is* the FPE ciphertext (see token_vault.py for how a real
    vault would additionally track "which tokens have been issued" without
    ever needing to store a reverse mapping).
    """

    def __init__(self, key: bytes | None = None) -> None:
        self._key = key if key is not None else generate_key()

    # -- Credit card numbers (16-digit PAN) ---------------------------------

    def tokenize_card_number(self, pan: str) -> str:
        """Encrypt a 16-digit card number (PAN) into a 16-digit token.

        The output is always exactly 16 digits, so it drops straight into
        any downstream system's existing "16-digit card number" column,
        form validation, or display logic with zero code changes.
        """
        pan = pan.replace(" ", "").replace("-", "")
        if len(pan) != CARD_NUMBER_LENGTH or not pan.isdigit():
            raise InvalidCardNumberError(
                f"card number must be exactly {CARD_NUMBER_LENGTH} digits (got: {pan!r})"
            )
        return encrypt_digits(pan, self._key)

    def detokenize_card_number(self, token: str) -> str:
        """Recover the exact original 16-digit card number from a token.

        Only callers holding this service's key (in practice: the vault /
        payment-processing backend, not general application code) can do
        this reversal.
        """
        if len(token) != CARD_NUMBER_LENGTH or not token.isdigit():
            raise InvalidCardNumberError(
                f"token must be exactly {CARD_NUMBER_LENGTH} digits (got: {token!r})"
            )
        return decrypt_digits(token, self._key)

    # -- Social Security Numbers (9-digit SSN) ------------------------------

    def tokenize_ssn(self, ssn: str) -> str:
        """Encrypt a 9-digit SSN into a 9-digit token, preserving format
        (including leading zeros -- '0' is a perfectly valid SSN digit)."""
        ssn = ssn.replace("-", "").replace(" ", "")
        if len(ssn) != SSN_LENGTH or not ssn.isdigit():
            raise InvalidSSNError(f"SSN must be exactly {SSN_LENGTH} digits (got: {ssn!r})")
        return encrypt_digits(ssn, self._key)

    def detokenize_ssn(self, token: str) -> str:
        """Recover the exact original 9-digit SSN from a token."""
        if len(token) != SSN_LENGTH or not token.isdigit():
            raise InvalidSSNError(f"token must be exactly {SSN_LENGTH} digits (got: {token!r})")
        return decrypt_digits(token, self._key)


__all__ = [
    "TokenizationService",
    "InvalidCardNumberError",
    "InvalidSSNError",
    "FeistelFPEError",
    "generate_key",
    "CARD_NUMBER_LENGTH",
    "SSN_LENGTH",
]
