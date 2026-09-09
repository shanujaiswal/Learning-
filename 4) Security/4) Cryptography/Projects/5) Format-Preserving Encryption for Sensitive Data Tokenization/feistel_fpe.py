"""
feistel_fpe.py
---------------

A from-scratch Format-Preserving Encryption (FPE) construction, in the spirit
of NIST's standardized FF1 / FF3-1 algorithms, built entirely from stdlib
`hmac` + `hashlib` (HMAC-SHA256 as the pseudo-random round function, which
plays the same role FF1/FF3-1 give to AES).

The core idea -- an unbalanced Feistel network over digit strings:

    1. Split the input digit string into two halves: L (left) and R (right),
       with fixed lengths for the whole run (left gets the extra digit on an
       odd-length input -- e.g. a 9-digit SSN splits into a 5-digit left half
       and a 4-digit right half).
    2. Run N rounds. On EVEN rounds, update R using a PRF of L; on ODD
       rounds, update L using a PRF of R. Only one half changes per round
       (the other passes through untouched) -- this is a standard Feistel
       round, and alternating which half changes across rounds is what
       diffuses every input digit into every output digit:

           round i even:  R <- (R + F(i, L)) mod 10**len(R)
           round i odd:   L <- (L + F(i, R)) mod 10**len(L)

       where F(i, x) = HMAC-SHA256(key, i || x) reduced mod 10**k.
    3. Concatenate the final L and R -- this is the ciphertext. It has
       EXACTLY the same length as the input and is still digits-only, i.e.
       it is format-preserving.

Because each round is just a modular addition of a PRF output onto ONE
untouched half, it is trivially invertible: replay the rounds in reverse
order and subtract the same PRF output (recomputable by anyone holding the
key, since the untouched half needed to compute it is exactly what's still
sitting in the ciphertext at that point).

This mirrors the real NIST FF1/FF3-1 design:
    - FF1/FF3-1 use AES (as a PRF/cipher) inside a Feistel network operating
      on an arbitrary "radix" alphabet (digits, in our case, radix 10).
    - We swap AES-as-PRF for HMAC-SHA256-as-PRF -- same architectural role,
      simpler to implement from stdlib only, same security *shape*
      (a well-vetted PRF driving a Feistel network). This is a
      teaching/demo construction, NOT a byte-for-byte NIST-certified
      implementation -- see the README for the honest caveats.
"""

from __future__ import annotations

import hashlib
import hmac

# Number of Feistel rounds. NIST FF1/FF3-1 recommend a minimum of 8 rounds
# so every output digit ends up depending on every input digit; 10 gives a
# comfortable margin over that minimum while staying fast.
DEFAULT_ROUNDS = 10


class FeistelFPEError(ValueError):
    """Raised for malformed input to the FPE primitive."""


def _require_digits(s: str, name: str) -> None:
    if not isinstance(s, str) or not s.isdigit():
        raise FeistelFPEError(f"{name} must be a non-empty, digits-only string (got: {s!r})")


def _split_halves(digits: str) -> tuple[str, str]:
    """Split a digit string into fixed-length (left, right) halves.

    For odd lengths, the LEFT half gets the extra digit (floor/ceil split),
    matching the convention FF1 uses for its unbalanced Feistel network.
    Lengths stay fixed for every round of the algorithm.
    """
    n = len(digits)
    left_len = (n + 1) // 2
    return digits[:left_len], digits[left_len:]


def _round_function(key: bytes, round_number: int, other_half: str, out_len: int) -> int:
    """F(round_number, other_half) -> integer in [0, 10**out_len).

    This is the PRF at the heart of the construction: HMAC-SHA256 keyed by
    the secret key, fed the round number and the untouched half of the digit
    string, then reduced modulo 10**out_len so it can be added onto a digit
    string of that length. HMAC-SHA256 is a well-vetted, standard PRF -- this
    plays exactly the role AES plays inside real FF1/FF3-1 round functions.
    """
    message = f"{round_number:04d}:{len(other_half)}:{other_half}".encode("utf-8")
    digest = hmac.new(key, message, hashlib.sha256).digest()
    as_int = int.from_bytes(digest, byteorder="big")
    return as_int % (10 ** out_len)


def _add_digits(a: str, b_value: int) -> str:
    """Return (int(a) + b_value) mod 10**len(a), zero-padded back to len(a)."""
    modulus = 10 ** len(a)
    result = (int(a) + b_value) % modulus
    return str(result).zfill(len(a))


def _sub_digits(a: str, b_value: int) -> str:
    """Return (int(a) - b_value) mod 10**len(a), zero-padded back to len(a)."""
    modulus = 10 ** len(a)
    result = (int(a) - b_value) % modulus
    return str(result).zfill(len(a))


def encrypt_digits(plaintext_digits: str, key: bytes, rounds: int = DEFAULT_ROUNDS) -> str:
    """Encrypt a digit-only string into a same-length, digit-only ciphertext.

    Implements an unbalanced Feistel network: each round updates exactly one
    (fixed-length) half by adding a PRF output derived from the round number
    and the OTHER, untouched half; which half is "active" alternates every
    round. This is the same round structure FF1/FF3-1 use, generalized here
    to HMAC-SHA256 as the round PRF instead of AES.
    """
    _require_digits(plaintext_digits, "plaintext_digits")
    if len(plaintext_digits) < 2:
        raise FeistelFPEError("input must be at least 2 digits long for a two-branch Feistel network")

    left, right = _split_halves(plaintext_digits)

    for round_number in range(rounds):
        if round_number % 2 == 0:
            f_out = _round_function(key, round_number, left, len(right))
            right = _add_digits(right, f_out)
        else:
            f_out = _round_function(key, round_number, right, len(left))
            left = _add_digits(left, f_out)

    return left + right


def decrypt_digits(ciphertext_digits: str, key: bytes, rounds: int = DEFAULT_ROUNDS) -> str:
    """Exact inverse of `encrypt_digits` -- recovers the original plaintext.

    Runs the Feistel rounds in reverse order, undoing each modular addition
    with the matching modular subtraction. This works because the "other"
    (untouched) half needed to recompute each round's PRF output is exactly
    the half still sitting unmodified in the ciphertext at that point in the
    reverse walk -- classic Feistel invertibility, independent of whether the
    round function F itself is invertible.
    """
    _require_digits(ciphertext_digits, "ciphertext_digits")
    if len(ciphertext_digits) < 2:
        raise FeistelFPEError("input must be at least 2 digits long for a two-branch Feistel network")

    left, right = _split_halves(ciphertext_digits)

    for round_number in reversed(range(rounds)):
        if round_number % 2 == 0:
            f_out = _round_function(key, round_number, left, len(right))
            right = _sub_digits(right, f_out)
        else:
            f_out = _round_function(key, round_number, right, len(left))
            left = _sub_digits(left, f_out)

    return left + right
