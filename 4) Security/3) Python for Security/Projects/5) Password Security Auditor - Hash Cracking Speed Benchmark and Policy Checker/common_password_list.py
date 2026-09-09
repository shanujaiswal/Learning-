"""
common_password_list.py

A small, self-contained list of common/breached passwords (the kind that top
every "most common passwords" and rockyou.txt-derived breach list). It is
used for TWO purposes in this project, mirroring how these lists are used in
the real world:

  1. As dictionary-attack input for cracking_benchmark.py — an attacker's
     first move is always to try known-common passwords before brute-forcing
     the full keyspace.

  2. As a policy blocklist in password_policy_checker.py — real password
     policies (NIST SP 800-63B, most corporate IT policies) require rejecting
     passwords that appear on breach/common-password lists, regardless of
     whether they otherwise satisfy length/complexity rules.

In production you would use a real list (e.g. the 10-million-password
"rockyou.txt", or the "Pwned Passwords" k-anonymity API from Have I Been
Pwned) with hundreds of thousands to billions of entries. This list is kept
tiny on purpose so the whole project runs instantly and needs no downloads.
"""

COMMON_PASSWORDS: list[str] = [
    "123456",
    "123456789",
    "qwerty",
    "password",
    "12345",
    "12345678",
    "111111",
    "1234567",
    "letmein",
    "1234567890",
    "dragon",
    "monkey",
    "football",
    "iloveyou",
    "admin",
    "welcome",
    "sunshine",
    "master",
    "hunter2",
    "trustno1",
    "shadow",
    "abc123",
    "password1",
    "qwerty123",
    "123123",
    "baseball",
    "superman",
    "michael",
    "ninja",
    "mustang",
]

# A normalized (lowercased) set for O(1) membership checks in the policy checker.
COMMON_PASSWORDS_SET: set[str] = {p.lower() for p in COMMON_PASSWORDS}
