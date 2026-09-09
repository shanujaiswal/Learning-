"""
cracking_benchmark.py

AUTHORIZED USE ONLY: this cracks hashes WE generated ourselves, from a small
local wordlist, purely to measure and demonstrate a real speed difference.
Never point this kind of tool at credentials you are not explicitly
authorized to test.

Core idea: take the SAME target password, hash it once with the FAST/WEAK
scheme and once with the SLOW/PROPER scheme (hash_schemes.py), then run a
dictionary attack against each target hash and measure real wall-clock time
and guesses/sec. This is a miniature, local version of what tools like
hashcat or John the Ripper report as their "cracking speed" for a given hash
algorithm.

The wordlist used is the common-password list plus some padding entries, so
the attack has to do real work rather than finding the answer on guess #1.
"""

from __future__ import annotations

import time
from dataclasses import dataclass

from common_password_list import COMMON_PASSWORDS
from hash_schemes import PBKDF2_ITERATIONS, SALT_BYTES, slow_hash, weak_hash


@dataclass
class CrackResult:
    scheme_name: str
    target_password: str
    cracked_password: str | None
    guesses_tried: int
    elapsed_seconds: float

    @property
    def guesses_per_second(self) -> float:
        if self.elapsed_seconds <= 0:
            return float("inf")
        return self.guesses_tried / self.elapsed_seconds

    @property
    def success(self) -> bool:
        return self.cracked_password is not None


def build_wordlist(extra_size: int = 2000) -> list[str]:
    """Common passwords first (as a real attacker would order guesses), padded
    out with synthetic candidates so the dictionary attack has a realistic
    number of guesses to make instead of succeeding on attempt #1 or #2.
    """
    wordlist = list(COMMON_PASSWORDS)
    for i in range(extra_size):
        wordlist.append(f"candidate{i}!")
    return wordlist


def crack_fast_scheme(target_password: str, wordlist: list[str]) -> CrackResult:
    """Dictionary-attack the FAST/WEAK unsalted SHA-256 scheme."""
    target_hash = weak_hash(target_password)  # salt="" -> unsalted, as an attacker would find it

    start = time.perf_counter()
    guesses = 0
    found = None
    for candidate in wordlist:
        guesses += 1
        if weak_hash(candidate) == target_hash:
            found = candidate
            break
    elapsed = time.perf_counter() - start

    return CrackResult("Fast/Weak SHA-256 (unsalted)", target_password, found, guesses, elapsed)


def crack_slow_scheme(target_password: str, wordlist: list[str], iterations: int = PBKDF2_ITERATIONS) -> CrackResult:
    """Dictionary-attack the SLOW/PROPER salted PBKDF2 scheme.

    Note: the attacker DOES know the salt (salts are stored alongside the hash
    in any real system, in the clear — the salt's job is to defeat
    precomputed rainbow tables and force per-account recomputation, not to be
    secret). What they don't get for free is speed: every guess still costs
    `iterations` rounds of HMAC-SHA256.
    """
    salt, target_hash = slow_hash(target_password, iterations=iterations)

    start = time.perf_counter()
    guesses = 0
    found = None
    for candidate in wordlist:
        guesses += 1
        _, candidate_hash = slow_hash(candidate, salt=salt, iterations=iterations)
        if candidate_hash == target_hash:
            found = candidate
            break
    elapsed = time.perf_counter() - start

    return CrackResult(f"Slow/Proper PBKDF2-SHA256 ({iterations:,} iter)", target_password, found, guesses, elapsed)


def run_benchmark(target_password: str, wordlist_size: int = 2000, pbkdf2_iterations: int = PBKDF2_ITERATIONS) -> tuple[CrackResult, CrackResult]:
    """Run both dictionary attacks against the same target password and
    return (fast_result, slow_result) for comparison.
    """
    wordlist = build_wordlist(wordlist_size)
    fast_result = crack_fast_scheme(target_password, wordlist)
    slow_result = crack_slow_scheme(target_password, wordlist, iterations=pbkdf2_iterations)
    return fast_result, slow_result


def print_result(result: CrackResult) -> None:
    status = f"CRACKED -> '{result.cracked_password}'" if result.success else "NOT FOUND"
    print(f"  Scheme        : {result.scheme_name}")
    print(f"  Guesses tried : {result.guesses_tried:,}")
    print(f"  Elapsed time  : {result.elapsed_seconds:.4f} s")
    print(f"  Guesses/sec   : {result.guesses_per_second:,.1f}")
    print(f"  Result        : {status}")


if __name__ == "__main__":
    target = "trustno1"
    print(f"=== Cracking benchmark: target password (known to us) = '{target}' ===\n")
    fast_res, slow_res = run_benchmark(target)

    print("[1] Fast/Weak scheme:")
    print_result(fast_res)
    print("\n[2] Slow/Proper scheme:")
    print_result(slow_res)

    if slow_res.guesses_per_second > 0:
        ratio = fast_res.guesses_per_second / max(slow_res.guesses_per_second, 1e-12)
        print(f"\nFast scheme was ~{ratio:,.0f}x faster to attack per guess than the slow scheme.")
