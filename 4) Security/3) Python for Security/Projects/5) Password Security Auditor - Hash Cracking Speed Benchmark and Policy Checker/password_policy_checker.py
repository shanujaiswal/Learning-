"""
password_policy_checker.py

Audits a set of real-looking user account passwords against a password
policy modeled loosely on common corporate IT policy + NIST SP 800-63B
guidance:

  - Minimum length (NIST recommends >=8, many orgs require >=12)
  - Character-class requirements: uppercase, lowercase, digit, symbol
  - Must NOT appear on a common/breached password list (case-insensitive)
  - Must NOT be trivially derived from the username (e.g. "vanisha123",
    the username reversed, or the username with common suffixes/leetspeak)

Produces a structured pass/fail result per account with the specific
reasons for any failure, similar to what an automated password-policy audit
tool would report to a security team.
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field

from common_password_list import COMMON_PASSWORDS_SET

MIN_LENGTH = 12

LEET_MAP = str.maketrans({"0": "o", "1": "i", "3": "e", "4": "a", "5": "s", "7": "t", "@": "a", "$": "s"})


@dataclass
class PolicyResult:
    username: str
    password_masked: str
    passed: bool
    reasons: list[str] = field(default_factory=list)


def _mask(password: str) -> str:
    if len(password) <= 2:
        return "*" * len(password)
    return password[0] + "*" * (len(password) - 2) + password[-1]


def _contains_upper(password: str) -> bool:
    return any(c.isupper() for c in password)


def _contains_lower(password: str) -> bool:
    return any(c.islower() for c in password)


def _contains_digit(password: str) -> bool:
    return any(c.isdigit() for c in password)


def _contains_symbol(password: str) -> bool:
    return bool(re.search(r"[^A-Za-z0-9]", password))


def _is_username_derived(password: str, username: str) -> bool:
    """Catch the most common ways users turn their own username into a
    'password': using it verbatim, reversed, with digits/symbols appended, or
    lightly leetspeak-obfuscated.
    """
    pw_lower = password.lower()
    user_lower = username.lower()

    if len(user_lower) < 3:
        return False  # too short a username to meaningfully match against

    if user_lower in pw_lower:
        return True
    if user_lower[::-1] in pw_lower:
        return True

    # Strip trailing digits/symbols (e.g. "vanisha123!" -> "vanisha") and re-check.
    stripped = re.sub(r"[^A-Za-z]+$", "", pw_lower)
    if stripped and user_lower == stripped:
        return True

    # De-leet the password (0->o, 1->i, 3->e, ...) and check again.
    deleeted = pw_lower.translate(LEET_MAP)
    if user_lower in deleeted:
        return True

    return False


def check_password(username: str, password: str) -> PolicyResult:
    reasons: list[str] = []

    if len(password) < MIN_LENGTH:
        reasons.append(f"too short: {len(password)} chars (minimum {MIN_LENGTH})")

    if not _contains_upper(password):
        reasons.append("missing an uppercase letter")
    if not _contains_lower(password):
        reasons.append("missing a lowercase letter")
    if not _contains_digit(password):
        reasons.append("missing a digit")
    if not _contains_symbol(password):
        reasons.append("missing a symbol/punctuation character")

    if password.lower() in COMMON_PASSWORDS_SET:
        reasons.append("appears on the common/breached password list")

    if _is_username_derived(password, username):
        reasons.append("derived from the username (predictable)")

    return PolicyResult(
        username=username,
        password_masked=_mask(password),
        passed=len(reasons) == 0,
        reasons=reasons,
    )


def audit_accounts(accounts: dict[str, str]) -> list[PolicyResult]:
    """Run check_password over a {username: password} mapping and return one
    PolicyResult per account, in insertion order.
    """
    return [check_password(username, password) for username, password in accounts.items()]


def print_audit(results: list[PolicyResult]) -> None:
    passed = sum(1 for r in results if r.passed)
    for r in results:
        status = "PASS" if r.passed else "FAIL"
        print(f"  [{status}] {r.username:<12} ({r.password_masked})")
        for reason in r.reasons:
            print(f"           - {reason}")
    print(f"\n  Compliance: {passed}/{len(results)} accounts pass policy "
          f"({(passed / len(results) * 100) if results else 0:.0f}%)")


if __name__ == "__main__":
    sample_accounts = {
        "vanisha": "vanisha123",
        "j.smith": "Tr0ub4dor&3xample!",
        "admin": "password",
        "r.patel": "correcthorsebatterystaple",
        "k.lee": "K9#mQ2vLp!7z",
    }
    results = audit_accounts(sample_accounts)
    print("=== Password Policy Audit ===")
    print_audit(results)
