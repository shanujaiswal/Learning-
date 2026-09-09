"""
token_vault.py
---------------

Simulates the "token vault" concept described in the theory file -- but
adapted to an FPE-based design rather than a classic random-token vault.

IMPORTANT DISTINCTION from the classic tokenization vault:
    - A CLASSIC tokenization vault (e.g. `TOK_9f8a7b2c1d`) stores a mapping
      of {random_token -> real_value} because the token has NO mathematical
      relationship to the original value -- reversal is ONLY possible by
      looking the token up in the vault's mapping table.
    - Our FPE-based tokens ARE the ciphertext of a reversible cipher. The
      vault therefore does NOT need to store a reverse-mapping table at all
      -- anyone holding the FPE key can detokenize directly. This is exactly
      the trade-off the theory file calls out: "FPE avoids needing a
      vault/lookup at all, since the encrypted value is self-contained and
      reversible with the right key."

So what does this vault module actually DO, if it doesn't need a mapping
table? It plays the two roles a real vault/ledger still plays even in an
FPE-based design:

    1. Issuance tracking -- record which tokens have been issued (and when),
       for audit trails, duplicate detection, and revocation -- WITHOUT ever
       storing the plaintext original or the FPE key alongside them.
    2. Key custody boundary -- the vault object deliberately does NOT hold
       the FPE key at all. It is constructed from a `TokenizationService`
       but only ever calls its `tokenize_*` methods; it has no attribute or
       method that could leak the key, and `repr()`/`str()` are overridden
       to guarantee that dumping the vault (e.g. into a log file) can never
       accidentally print key material.

The demonstration at the bottom of `main.py` shows the key property this
buys: someone who obtains a copy of the vault's stored tokens (e.g. a
database dump of the `tokens_issued` table) *cannot* recover the original
card numbers or SSNs from that data alone -- they would additionally need
the FPE key, which the vault never stores.
"""

from __future__ import annotations

import datetime
from dataclasses import dataclass, field

from tokenization_service import TokenizationService


@dataclass(frozen=True)
class TokenRecord:
    """An audit-log entry for one issued token. Deliberately holds NO
    plaintext original and NO key material -- only what a real compliance
    audit trail would need: the token itself, its data class, and when it
    was issued."""

    token: str
    data_class: str  # e.g. "card_number" or "ssn"
    issued_at: str


class TokenVault:
    """Tracks issued tokens without ever storing a reverse mapping or the key.

    Construct it with a `TokenizationService` instance; the vault calls into
    that service to produce tokens but never reads or exposes its key.
    """

    def __init__(self, service: TokenizationService) -> None:
        self._service = service
        self._issued: dict[str, TokenRecord] = {}

    # -- Issuance --------------------------------------------------------

    def issue_card_token(self, pan: str) -> str:
        """Tokenize a card number and record the issuance. Returns the token."""
        token = self._service.tokenize_card_number(pan)
        self._record(token, "card_number")
        return token

    def issue_ssn_token(self, ssn: str) -> str:
        """Tokenize an SSN and record the issuance. Returns the token."""
        token = self._service.tokenize_ssn(ssn)
        self._record(token, "ssn")
        return token

    def _record(self, token: str, data_class: str) -> None:
        self._issued[token] = TokenRecord(
            token=token,
            data_class=data_class,
            issued_at=datetime.datetime.now(datetime.timezone.utc).isoformat(),
        )

    # -- Read-only introspection (no reverse mapping exists to expose) ---

    def is_token_known(self, token: str) -> bool:
        """Was this token ever issued by this vault? (Duplicate/validity check --
        does NOT reveal or require the original value.)"""
        return token in self._issued

    def issued_tokens(self) -> list[str]:
        """The full list of tokens this vault has issued -- this is exactly
        what an attacker who stole a copy of the vault's database would get.
        Notice there is no corresponding list of original values anywhere."""
        return list(self._issued.keys())

    def audit_log(self) -> list[TokenRecord]:
        """Full audit trail (token, data class, issuance timestamp) -- still
        no plaintext, still no key."""
        return list(self._issued.values())

    # -- Key custody boundary ---------------------------------------------
    #
    # No method here accepts or returns the FPE key. `__repr__`/`__str__`
    # are overridden so that even accidentally logging this object (e.g.
    # `print(vault)` or an exception traceback capturing local variables)
    # cannot leak key material -- only counts and issuance metadata show up.

    def __repr__(self) -> str:  # pragma: no cover - cosmetic
        return f"TokenVault(tokens_issued={len(self._issued)}, key=<not stored in vault>)"

    __str__ = __repr__


def attempt_reverse_without_key(token: str) -> str:
    """Simulate an attacker who has stolen the vault's token list but NOT the
    FPE key -- i.e. they have exactly the data `TokenVault.issued_tokens()`
    exposes and nothing else.

    There is no key-independent way to invert `feistel_fpe.decrypt_digits`:
    without the key, every one of the HMAC-SHA256 round outputs is
    unrecoverable, so the attacker cannot undo even a single Feistel round.
    All they can honestly report is that the token LOOKS like a valid
    16-digit/9-digit value and reveals nothing about which digits are real.
    """
    return (
        f"Token {token!r} is a well-formed {len(token)}-digit value, "
        "but it cannot be decrypted without the FPE key. "
        "No original digits can be recovered from the token alone."
    )
