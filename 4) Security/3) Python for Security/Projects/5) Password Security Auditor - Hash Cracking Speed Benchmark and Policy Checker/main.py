"""
main.py

AUTHORIZED USE ONLY: self-contained educational demo. Only run against data
generated locally by this project; never against credentials you do not own
or are not explicitly authorized to test.

Password Security Auditor
==========================
A security engineer's two-part evaluation of a company's password storage
scheme and password policy:

  Part 1 - Cracking speed benchmark:
           Hash the SAME test password with a FAST/WEAK scheme (raw SHA-256)
           and a SLOW/PROPER scheme (PBKDF2-HMAC-SHA256, high iteration
           count), then run a real dictionary attack against both and report
           the measured guesses/sec and total time for each. This is a
           miniature hashcat/John-the-Ripper-style speed comparison.

  Part 2 - Password policy audit:
           Check a set of real-looking user account passwords against a
           policy (length, character classes, common-password blocklist,
           username-derivation check) and report per-account pass/fail with
           reasons.

  Combined summary:
           Ties both halves together into an overall security posture
           readout, the way a pentest/security-audit report would.
"""

from __future__ import annotations

from cracking_benchmark import print_result, run_benchmark
from hash_schemes import PBKDF2_ITERATIONS
from password_policy_checker import audit_accounts, print_audit

# The set of test passwords used for the cracking benchmark. These are
# deliberately weak/common so the dictionary attack succeeds and produces a
# meaningful, measurable comparison instead of "not found" on both sides.
BENCHMARK_TARGET_PASSWORDS = ["trustno1", "monkey", "shadow"]

# Real-looking (but synthetic) user accounts for the policy audit.
SAMPLE_ACCOUNTS = {
    "vanisha": "vanisha123",
    "j.smith": "Tr0ub4dor&3xample!",
    "admin": "password",
    "r.patel": "correcthorsebatterystaple",
    "k.lee": "K9#mQ2vLp!7z",
    "m.chen": "M.chen2024",
    "s.ahmed": "Str0ng&Un1qu3Pass!",
}


def run_cracking_section() -> list[tuple]:
    print("=" * 78)
    print("PART 1: Hash-Cracking Speed Benchmark (fast/weak vs slow/proper)")
    print("=" * 78)
    print(
        f"Wordlist attack run against the SAME target password, once hashed with a\n"
        f"raw unsalted SHA-256 (fast/weak), once with PBKDF2-HMAC-SHA256 at\n"
        f"{PBKDF2_ITERATIONS:,} iterations (slow/proper). Same attacker, same wordlist,\n"
        f"same target password -- only the storage scheme differs.\n"
    )

    all_results = []
    for target in BENCHMARK_TARGET_PASSWORDS:
        print(f"--- Target password: '{target}' ---")
        fast_res, slow_res = run_benchmark(target)
        print("[Fast/Weak SHA-256]")
        print_result(fast_res)
        print("[Slow/Proper PBKDF2]")
        print_result(slow_res)
        ratio = fast_res.guesses_per_second / max(slow_res.guesses_per_second, 1e-12)
        print(f"-> Fast scheme allowed ~{ratio:,.0f}x more guesses/sec than the slow scheme.\n")
        all_results.append((target, fast_res, slow_res))
    return all_results


def run_policy_section() -> list:
    print("=" * 78)
    print("PART 2: Password Policy Audit")
    print("=" * 78)
    print(
        "Policy rules: min length 12, must include uppercase + lowercase + digit +\n"
        "symbol, must not be on the common/breached password list, must not be\n"
        "derived from the username.\n"
    )
    results = audit_accounts(SAMPLE_ACCOUNTS)
    print_audit(results)
    print()
    return results


def print_combined_summary(benchmark_results, policy_results) -> None:
    print("=" * 78)
    print("COMBINED SUMMARY")
    print("=" * 78)

    avg_fast_rate = sum(f.guesses_per_second for _, f, _ in benchmark_results) / len(benchmark_results)
    avg_slow_rate = sum(s.guesses_per_second for _, _, s in benchmark_results) / len(benchmark_results)
    speedup = avg_fast_rate / max(avg_slow_rate, 1e-12)

    passed = sum(1 for r in policy_results if r.passed)
    total = len(policy_results)

    print(f"Hashing scheme risk : fast/weak SHA-256 sustained ~{avg_fast_rate:,.0f} guesses/sec")
    print(f"                      slow/proper PBKDF2 sustained ~{avg_slow_rate:,.0f} guesses/sec")
    print(f"                      -> {speedup:,.0f}x throughput advantage for the attacker on the weak scheme")
    print(f"Policy compliance   : {passed}/{total} accounts pass ({passed / total * 100:.0f}%)")

    if speedup > 10 and passed < total:
        print(
            "\nOverall posture: HIGH RISK. The storage scheme itself would let an\n"
            "attacker who steals the database try guesses orders of magnitude faster\n"
            "than necessary, AND a meaningful fraction of real accounts use passwords\n"
            "that fail policy -- either issue alone is exploitable; together they\n"
            "compound."
        )
    elif speedup > 10:
        print(
            "\nOverall posture: MODERATE RISK. Password policy compliance is solid, but\n"
            "the underlying hashing scheme is still the weak link -- move to PBKDF2/\n"
            "bcrypt/scrypt/Argon2 with a high work factor before this matters less."
        )
    else:
        print("\nOverall posture: acceptable, but keep monitoring both hashing scheme and policy compliance.")


def main() -> None:
    benchmark_results = run_cracking_section()
    policy_results = run_policy_section()
    print_combined_summary(benchmark_results, policy_results)


if __name__ == "__main__":
    main()
