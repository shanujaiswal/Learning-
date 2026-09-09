"""
password_health_checker.py
============================

Runs entirely on data that has ALREADY been decrypted locally (this module
never touches the master password, the derived key, or any ciphertext). It
mirrors the "Password Health" / "Watchtower" / "Vault Health Report" features
found in Bitwarden, 1Password, and browser password managers.

Two independent checks:

1. Weak password check -- compares each saved password (case-sensitively)
   against a small built-in list of extremely common/breached passwords. In
   a real product this list would be replaced by a k-anonymity lookup against
   the "Have I Been Pwned" Pwned Passwords API, but the local-list approach
   demonstrates the same principle without a network dependency.
2. Reuse check -- flags any password that appears on more than one vault
   entry, since reusing a password means a breach at one site compromises
   every other site using it (credential-stuffing risk).
"""

from __future__ import annotations

from collections import defaultdict
from typing import Any

# A small sample of the most commonly breached/guessed passwords of all time
# (a stand-in for a real "top 10 million breached passwords" corpus / an HIBP
# Pwned Passwords API lookup).
COMMON_WEAK_PASSWORDS = {
    "123456",
    "123456789",
    "qwerty",
    "password",
    "111111",
    "12345678",
    "abc123",
    "1234567",
    "password1",
    "12345",
    "iloveyou",
    "admin",
    "welcome",
    "monkey",
    "letmein",
    "hunter2",
    "dragon",
    "sunshine",
    "princess",
    "football",
}


def _is_weak(password: str) -> bool:
    """A password is 'weak' if it's in the known-common list, or is simply
    too short / low-complexity to resist brute forcing."""
    if password.lower() in COMMON_WEAK_PASSWORDS:
        return True
    if len(password) < 8:
        return True
    return False


def check_vault_health(entries: list[dict[str, Any]]) -> dict[str, list[dict[str, Any]]]:
    """Analyze decrypted vault entries and return a report.

    `entries` is a list of dicts shaped like
    {"site": str, "username": str, "password": str}.

    Returns a dict with two keys:
      - "weak":   entries whose password is common/short/breached-looking
      - "reused": entries whose password also appears on >= 1 other entry
    """
    weak: list[dict[str, Any]] = []
    reused: list[dict[str, Any]] = []

    passwords_to_sites: dict[str, list[str]] = defaultdict(list)
    for entry in entries:
        passwords_to_sites[entry["password"]].append(entry["site"])

    for entry in entries:
        if _is_weak(entry["password"]):
            weak.append(entry)
        if len(passwords_to_sites[entry["password"]]) > 1:
            reused.append(entry)

    return {"weak": weak, "reused": reused}


def print_health_report(entries: list[dict[str, Any]]) -> None:
    """Pretty-print a human-readable password health report."""
    report = check_vault_health(entries)

    print(f"Scanned {len(entries)} vault entries.")

    if not report["weak"] and not report["reused"]:
        print("  No weak or reused passwords found. Vault looks healthy.")
        return

    if report["weak"]:
        print(f"  WEAK passwords ({len(report['weak'])}):")
        for e in report["weak"]:
            print(f"    - {e['site']} (user: {e['username']}) -> '{e['password']}'")

    if report["reused"]:
        print(f"  REUSED passwords ({len(report['reused'])} entries affected):")
        by_password: dict[str, list[str]] = defaultdict(list)
        for e in report["reused"]:
            by_password[e["password"]].append(e["site"])
        for pw, sites in by_password.items():
            print(f"    - '{pw}' reused across: {', '.join(sites)}")


if __name__ == "__main__":
    sample_entries = [
        {"site": "example.com", "username": "alice", "password": "Tr0ub4dor&3-xyz"},
        {"site": "old-forum.com", "username": "alice", "password": "hunter2"},
        {"site": "another-site.com", "username": "alice", "password": "hunter2"},
    ]
    print_health_report(sample_entries)
