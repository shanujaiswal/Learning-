"""
main.py
--------

Runs the full story described in the README:

    1. A tokenization service is stood up with a securely generated FPE key
       (as if pulled from an HSM/KMS).
    2. Several real-format credit card numbers and SSNs are tokenized
       through a `TokenVault`, which issues + tracks tokens but never stores
       a reverse mapping or the key.
    3. Each token is shown alongside its original -- same length, same
       digits-only format, completely different digits.
    4. Each token is detokenized back through the service and asserted to
       be EXACTLY equal to the original (including tricky edge cases like
       leading zeros).
    5. An "attacker" is simulated who has only the vault's stored token list
       (no key) and cannot recover anything from it.
"""

from __future__ import annotations

from tokenization_service import TokenizationService, generate_key
from token_vault import TokenVault, attempt_reverse_without_key


SAMPLE_CARD_NUMBERS = [
    "4532015112830366",  # Visa-shaped test PAN
    "5500005555555559",  # Mastercard-shaped test PAN
    "4111111111111111",  # classic all-ones test PAN
    "4000000000000002",  # PAN with leading/trailing structural zeros
]

SAMPLE_SSNS = [
    "078051120",  # historically-issued-looking SSN
    "219099999",
    "001010001",  # SSN with a LEADING ZERO -- edge case for format preservation
    "000000001",  # SSN that is almost all zeros -- another edge case
]


def banner(title: str) -> None:
    print()
    print("=" * 78)
    print(title)
    print("=" * 78)


def main() -> None:
    banner("STEP 1 -- Stand up the tokenization service with a secure key")
    key = generate_key()
    print(f"Generated a {len(key)}-byte FPE key via secrets.token_bytes (simulating HSM/KMS custody).")
    print("This key is held ONLY by the tokenization service -- the vault never sees it.")

    service = TokenizationService(key=key)
    vault = TokenVault(service)

    banner("STEP 2 -- Tokenize credit card numbers (16-digit PAN -> 16-digit token)")
    card_results = []
    for pan in SAMPLE_CARD_NUMBERS:
        token = vault.issue_card_token(pan)
        card_results.append((pan, token))
        same_format = (
            len(token) == len(pan) and token.isdigit() and pan.isdigit()
        )
        print(f"  original: {pan}   token: {token}   "
              f"[same length & digits-only format: {same_format}]")

    banner("STEP 3 -- Tokenize SSNs (9-digit SSN -> 9-digit token, incl. leading zeros)")
    ssn_results = []
    for ssn in SAMPLE_SSNS:
        token = vault.issue_ssn_token(ssn)
        ssn_results.append((ssn, token))
        same_format = (
            len(token) == len(ssn) and token.isdigit() and ssn.isdigit()
        )
        print(f"  original: {ssn}   token: {token}   "
              f"[same length & digits-only format: {same_format}]")

    banner("STEP 4 -- Detokenize everything back and verify EXACT equality")
    all_ok = True
    for pan, token in card_results:
        recovered = service.detokenize_card_number(token)
        ok = recovered == pan
        all_ok &= ok
        print(f"  token: {token}   detokenized: {recovered}   "
              f"original: {pan}   [exact match: {ok}]")
        assert recovered == pan, f"FPE round-trip failed for card {pan}!"

    for ssn, token in ssn_results:
        recovered = service.detokenize_ssn(token)
        ok = recovered == ssn
        all_ok &= ok
        print(f"  token: {token}   detokenized: {recovered}   "
              f"original: {ssn}   [exact match: {ok}]")
        assert recovered == ssn, f"FPE round-trip failed for SSN {ssn}!"

    print()
    print("All assertions passed -- every detokenized value is EXACTLY equal to its original,"
          if all_ok else "!! SOME ROUND-TRIPS FAILED !!")
    print("including the leading-zero SSN edge cases ('001010001', '000000001').")

    banner("STEP 5 -- Simulate an attacker who steals ONLY the vault's token list")
    stolen_tokens = vault.issued_tokens()
    print(f"Attacker exfiltrates the vault's `tokens_issued` table: {len(stolen_tokens)} tokens.")
    print("The attacker does NOT have the FPE key (the vault never stored it).")
    print()
    for token in stolen_tokens[:3]:
        print(f"  {attempt_reverse_without_key(token)}")
    print("  ...")
    print()
    print(f"vault repr() for logging safety check -> {vault!r}")
    print("(Notice: no key material, no plaintext originals, ever appear in that repr.)")

    banner("SUMMARY")
    print(f"Tokenized {len(SAMPLE_CARD_NUMBERS)} card numbers and {len(SAMPLE_SSNS)} SSNs.")
    print("Every token preserved the original's exact length and digits-only format.")
    print("Every token detokenized back to the EXACT original value via the FPE key.")
    print("The vault's stored tokens, without the key, reveal nothing about the originals.")


if __name__ == "__main__":
    main()
