"""
04 - Password Policy Validator + TOTP (MFA) Generator/Verifier
Chapter: 06 Identity and Access Management (IAM), SSO and OAuth

WHAT THIS DEMONSTRATES
-----------------------
Part A: A password-strength validator implementing common IAM password
        policy rules (minimum length, character-class complexity, a
        denylist of common/breached passwords, and a check against
        trivial sequences like "12345" or "qwerty").

Part B: A real, RFC 6238-compliant TOTP (Time-based One-Time Password)
        implementation FROM SCRATCH (no external MFA library needed) --
        the exact algorithm behind Google Authenticator / Microsoft
        Authenticator / Authy. It:
            - generates a random base32 shared secret (what would be
              embedded in the QR code a user scans when enabling MFA),
            - derives the current 6-digit code from the secret + time,
            - verifies a submitted code allowing a small clock-drift
              window, exactly like a real MFA backend does.

        The algorithm (RFC 6238 / RFC 4226 HOTP underneath):
            1. counter = floor(unix_time / period)              (period=30s)
            2. HS = HMAC-SHA1(secret, counter_as_8_byte_bigendian)
            3. offset = HS[19] & 0x0F
            4. truncated = (HS[offset..offset+4] as big-endian uint) & 0x7FFFFFFF
            5. code = truncated % 10**digits, zero-padded

If the `pyotp` package is installed, this script will also cross-check its
own from-scratch implementation against pyotp's output for the same secret
and timestamp (they must match) -- proof the from-scratch math is correct.

Run:
    python 04_password_policy_and_mfa_totp_demo.py
"""

from __future__ import annotations

import base64
import hashlib
import hmac
import re
import secrets
import struct
import time

# ---------------------------------------------------------------------------
# Part A: Password policy validator
# ---------------------------------------------------------------------------

COMMON_PASSWORDS = {
    "password", "123456", "12345678", "qwerty", "abc123", "password1",
    "letmein", "admin", "welcome", "monkey", "iloveyou", "111111",
    "sunshine", "princess", "football", "dragon", "passw0rd",
}

SEQUENTIAL_PATTERNS = [
    "0123456789", "abcdefghijklmnopqrstuvwxyz", "qwertyuiop", "asdfghjkl",
]


def _contains_sequential_run(password: str, run_length: int = 4) -> bool:
    lowered = password.lower()
    for pattern in SEQUENTIAL_PATTERNS:
        for i in range(len(pattern) - run_length + 1):
            chunk = pattern[i:i + run_length]
            if chunk in lowered or chunk[::-1] in lowered:
                return True
    return False


def validate_password(password: str, *, min_length: int = 12) -> tuple[bool, list[str]]:
    """
    Returns (is_valid, list_of_violations). is_valid is True only when
    the violation list is empty.
    """
    violations: list[str] = []

    if len(password) < min_length:
        violations.append(f"must be at least {min_length} characters long "
                           f"(got {len(password)})")
    if not re.search(r"[a-z]", password):
        violations.append("must contain at least one lowercase letter")
    if not re.search(r"[A-Z]", password):
        violations.append("must contain at least one uppercase letter")
    if not re.search(r"\d", password):
        violations.append("must contain at least one digit")
    if not re.search(r"[^\w\s]", password):
        violations.append("must contain at least one special character (e.g. ! @ # $ %)")
    if password.lower() in COMMON_PASSWORDS:
        violations.append("is a well-known common/breached password")
    if _contains_sequential_run(password):
        violations.append("contains an easily guessable sequential run (e.g. '1234', 'qwerty')")
    if re.search(r"(.)\1{2,}", password):
        violations.append("contains a character repeated 3+ times in a row (e.g. 'aaa')")

    return (len(violations) == 0, violations)


# ---------------------------------------------------------------------------
# Part B: TOTP (RFC 6238) from scratch
# ---------------------------------------------------------------------------

def generate_totp_secret(length_bytes: int = 20) -> str:
    """Random shared secret, base32-encoded (what a QR code would embed)."""
    random_bytes = secrets.token_bytes(length_bytes)
    return base64.b32encode(random_bytes).decode("utf-8").rstrip("=")


def _hotp(secret_base32: str, counter: int, digits: int = 6) -> str:
    """RFC 4226 HOTP: HMAC-SHA1-based one-time password for a given counter."""
    # base32 requires padding to a multiple of 8 chars.
    padded_secret = secret_base32 + "=" * ((8 - len(secret_base32) % 8) % 8)
    key = base64.b32decode(padded_secret.upper())

    counter_bytes = struct.pack(">Q", counter)  # 8-byte big-endian counter
    digest = hmac.new(key, counter_bytes, hashlib.sha1).digest()

    offset = digest[-1] & 0x0F
    truncated = struct.unpack(">I", digest[offset:offset + 4])[0] & 0x7FFFFFFF
    code = truncated % (10 ** digits)
    return str(code).zfill(digits)


def totp_now(secret_base32: str, *, period: int = 30, digits: int = 6,
             at_time: float | None = None) -> str:
    """Current TOTP code for `secret_base32` at `at_time` (defaults to now)."""
    timestamp = at_time if at_time is not None else time.time()
    counter = int(timestamp // period)
    return _hotp(secret_base32, counter, digits)


def verify_totp(secret_base32: str, submitted_code: str, *, period: int = 30,
                 digits: int = 6, drift_windows: int = 1) -> bool:
    """
    Verifies a submitted TOTP code, tolerating `drift_windows` steps of
    clock drift on either side (a real backend does this too, since the
    user's phone clock is never perfectly in sync).
    """
    now = time.time()
    current_counter = int(now // period)
    for delta in range(-drift_windows, drift_windows + 1):
        candidate = _hotp(secret_base32, current_counter + delta, digits)
        if hmac.compare_digest(candidate, submitted_code):
            return True
    return False


# ---------------------------------------------------------------------------
# Demo runner
# ---------------------------------------------------------------------------

def demo_password_policy() -> None:
    print("=" * 70)
    print("PART A: Password policy validation")
    print("=" * 70)
    candidates = [
        "password",
        "Summer2023",
        "Tr0ub4dor&3",
        "C0rrect-Horse-Battery-Staple!",
        "aaaaaaaaaaaa1A!",
        "qwertyuiopASDF1!",
    ]
    for pw in candidates:
        is_valid, violations = validate_password(pw)
        status = "PASS" if is_valid else "FAIL"
        print(f"\n[{status}] password = {pw!r}")
        for v in violations:
            print(f"    - {v}")
    print()


def demo_totp() -> None:
    print("=" * 70)
    print("PART B: TOTP (MFA) generation & verification, RFC 6238 from scratch")
    print("=" * 70)

    secret = generate_totp_secret()
    print(f"\n[*] Generated shared secret (base32, this is what the QR code "
          f"embeds): {secret}")

    code = totp_now(secret)
    print(f"[*] Current 6-digit TOTP code for this secret: {code}")

    # Correct verification.
    ok = verify_totp(secret, code)
    print(f"[*] Verifying the correct code -> {'ACCEPTED' if ok else 'REJECTED'}")

    # Wrong code should be rejected.
    wrong_code = str((int(code) + 1) % 1_000_000).zfill(6)
    ok_wrong = verify_totp(secret, wrong_code)
    print(f"[*] Verifying a deliberately wrong code ({wrong_code}) -> "
          f"{'ACCEPTED' if ok_wrong else 'REJECTED'}")

    # Cross-check against pyotp if it happens to be installed, to prove
    # correctness of the from-scratch implementation.
    try:
        import pyotp  # type: ignore
        reference_code = pyotp.TOTP(secret).now()
        match = "MATCH" if reference_code == code else "MISMATCH"
        print(f"[*] Cross-check against pyotp library: pyotp={reference_code} "
              f"vs from-scratch={code} -> {match}")
    except ImportError:
        print("[*] (pyotp not installed - skipping cross-check; "
              "install with `pip install pyotp` to verify independently)")
    print()


def main() -> None:
    demo_password_policy()
    demo_totp()


if __name__ == "__main__":
    main()
