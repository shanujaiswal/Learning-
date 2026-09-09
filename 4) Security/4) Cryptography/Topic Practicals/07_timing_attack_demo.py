"""
07_timing_attack_demo.py
---------------------------
Demonstrates the "Cryptographic Attacks II" chapter's timing-attack
material with REAL measured numbers (not just a textual explanation).

THE VULNERABILITY: naive `==` string/byte comparison
    Python's `==` on strings/bytes compares byte-by-byte and returns
    `False` as soon as it finds the first mismatching byte ("short-
    circuit" comparison). This means:
        - A guess that matches many leading bytes of the secret takes
          slightly LONGER to reject than a guess that mismatches on the
          very first byte.
        - By measuring these tiny timing differences (often over many
          repeated network requests to average out noise), an attacker
          can recover a secret token/MAC/password one byte at a time
          WITHOUT ever seeing it directly. This is a real, practically
          exploited class of attack (e.g. against MAC verification in
          early web frameworks, and against AES/RSA implementations at
          much finer granularity).

THE FIX: constant-time comparison
    `hmac.compare_digest` always compares the FULL length of both
    inputs and combines the result without branching on where a
    mismatch occurs, so the time taken does not depend on how many
    leading bytes match. This script measures both approaches side by
    side to make the difference concrete rather than theoretical.

No external dependencies required (uses only `hmac`, `os`, `time`,
`statistics` from the standard library).
"""

import hmac
import os
import statistics
import time

SECRET = os.urandom(32)  # a stand-in for a session token / MAC / API key
REPEATS = 2000            # repetitions per guess, to average out OS/CPU noise


def naive_compare(a: bytes, b: bytes) -> bool:
    """VULNERABLE: short-circuits on the first differing byte."""
    return a == b


def safe_compare(a: bytes, b: bytes) -> bool:
    """SAFE: constant-time comparison, immune to this timing side channel."""
    return hmac.compare_digest(a, b)


def make_guess(matching_prefix_len: int) -> bytes:
    """Build a guess that matches SECRET for exactly `matching_prefix_len`
    leading bytes, then diverges (or is fully correct if the prefix
    length equals len(SECRET)).
    """
    if matching_prefix_len >= len(SECRET):
        return bytes(SECRET)
    prefix = SECRET[:matching_prefix_len]
    # Pick a byte guaranteed to differ from the real next byte.
    real_next = SECRET[matching_prefix_len]
    wrong_next = bytes([(real_next + 1) % 256])
    rest = os.urandom(len(SECRET) - matching_prefix_len - 1)
    return prefix + wrong_next + rest


def time_compare(compare_fn, guess: bytes, repeats: int = REPEATS) -> float:
    """Return the median time (in seconds) of a single comparison call,
    measured over `repeats` calls to reduce noise. Median is more robust
    to occasional OS scheduling spikes than the mean.
    """
    samples = []
    for _ in range(repeats):
        start = time.perf_counter()
        compare_fn(guess, SECRET)
        samples.append(time.perf_counter() - start)
    return statistics.median(samples)


def run_demo(label: str, compare_fn):
    print("=" * 70)
    print(label)
    print("=" * 70)
    prefix_lengths = [0, 4, 8, 16, 24, 31, 32]
    results = []
    for prefix_len in prefix_lengths:
        guess = make_guess(prefix_len)
        median_time = time_compare(compare_fn, guess)
        results.append((prefix_len, median_time))
        print(f"  matching prefix = {prefix_len:2d}/32 bytes  ->  "
              f"median time = {median_time * 1e6:8.3f} microseconds")

    # Report how much slower the "almost fully correct" guess is vs a
    # totally-wrong-from-byte-0 guess -- this delta is the exploitable
    # signal an attacker measures.
    t_worst_guess = results[0][1]   # 0 matching bytes
    t_best_guess = results[-1][1]   # fully correct guess
    if t_worst_guess > 0:
        ratio = t_best_guess / t_worst_guess
        print(f"\n  Full-match guess is {ratio:.2f}x the time of a 0-byte-match guess.")
    return results


def main():
    print("Secret (never revealed to a real attacker, shown here for the demo):")
    print(f"  {SECRET.hex()}\n")

    naive_results = run_demo("VULNERABLE: naive `==` comparison", naive_compare)
    print()
    safe_results = run_demo("SAFE: hmac.compare_digest (constant-time)", safe_compare)

    print()
    print("=" * 70)
    print("INTERPRETATION")
    print("=" * 70)
    naive_spread = max(t for _, t in naive_results) - min(t for _, t in naive_results)
    safe_spread = max(t for _, t in safe_results) - min(t for _, t in safe_results)
    print(f"Naive `==`            timing spread across prefix lengths: {naive_spread * 1e6:.3f} us")
    print(f"hmac.compare_digest   timing spread across prefix lengths: {safe_spread * 1e6:.3f} us")
    print()
    if naive_spread > safe_spread:
        print("As expected, the naive comparison shows a measurably larger timing")
        print("spread as the matching-prefix length grows -- this is the signal an")
        print("attacker exploits, byte by byte, to reconstruct a secret they cannot")
        print("otherwise see. The constant-time comparison collapses that signal.")
    else:
        print("NOTE: on this particular run/machine the timing spread did not show")
        print("the expected pattern clearly (this can happen due to CPU frequency")
        print("scaling, other running processes, or Python interpreter overhead")
        print("dominating such tiny (nanosecond-to-microsecond) differences.")
        print("Real-world timing attacks are typically mounted over many thousands")
        print("to millions of network requests with statistical averaging, and/or")
        print("directly against lower-level (non-interpreted) code where the")
        print("underlying signal is far less noisy than in a Python REPL loop.")
        print("The vulnerability naive `==` comparison is REAL regardless of what")
        print("this particular measurement shows -- see the docstring at the top")
        print("of this file for the mechanism.")


if __name__ == "__main__":
    main()
