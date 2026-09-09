"""
main.py

Runs the full demonstration end to end:

  SCENARIO A -- Secure Boot ENABLED, tampered bootloader
    The bootloader stage's on-disk bytes were modified by a bootkit after
    signing. Secure Boot measures each stage in order and halts the
    INSTANT it hits a hash mismatch -- the kernel and drivers are never
    even reached. This is the chain-of-trust defense working correctly.

  SCENARIO B -- Secure Boot DISABLED, same tampered bootloader
    The exact same tampering is present, but Secure Boot enforcement is
    off, so every stage is allowed to load regardless of verification
    result. The tampered bootloader boots, hands off to the kernel, and
    an unsigned rootkit driver loads and hooks the syscall table.

  RUNTIME SCAN -- after Scenario B "finishes booting", a runtime driver
    integrity scanner compares the current driver table (baseline +
    rootkit driver) against the known-good baseline and flags the
    rootkit driver's lack of signature and its syscall-table hook.

  FINAL COMPARISON -- a short summary contrasting what each mode caught.
"""

from boot_chain_simulator import (
    build_clean_chain,
    build_tampered_chain,
    build_legitimate_drivers,
    build_rootkit_driver,
)
from secure_boot_verifier import (
    run_secure_boot_enabled,
    run_secure_boot_disabled,
    print_run,
)
from driver_integrity_scanner import scan


def banner(title: str) -> None:
    print("=" * 78)
    print(title)
    print("=" * 78)


def main() -> None:
    # ------------------------------------------------------------------
    # SCENARIO A: Secure Boot ENABLED, tampered bootloader present.
    # ------------------------------------------------------------------
    banner("SCENARIO A: Secure Boot ENABLED -- tampered bootloader")
    tampered_chain_a = build_tampered_chain()
    run_a = run_secure_boot_enabled(tampered_chain_a)
    print_run(run_a)

    # ------------------------------------------------------------------
    # (For contrast) Secure Boot ENABLED with a clean, untampered chain,
    # to show the "everything verifies, system boots normally" baseline.
    # ------------------------------------------------------------------
    banner("BASELINE: Secure Boot ENABLED -- clean, untampered chain")
    clean_chain = build_clean_chain()
    run_clean = run_secure_boot_enabled(clean_chain)
    print_run(run_clean)

    # ------------------------------------------------------------------
    # SCENARIO B: Secure Boot DISABLED, same tampering, plus a rootkit
    # driver that loads afterwards because nothing stopped it.
    # ------------------------------------------------------------------
    banner("SCENARIO B: Secure Boot DISABLED -- same tampered bootloader")
    tampered_chain_b = build_tampered_chain()
    run_b = run_secure_boot_disabled(tampered_chain_b)
    print_run(run_b)

    print("Because Secure Boot is disabled, the compromised bootloader was "
          "allowed to hand off to the kernel, which in turn loaded an "
          "additional, unsigned kernel driver:\n")

    baseline_drivers = build_legitimate_drivers()
    rootkit_driver = build_rootkit_driver()
    current_drivers = baseline_drivers + [rootkit_driver]

    for d in current_drivers:
        flag = "signed" if d.signed else "UNSIGNED"
        extra = f", hooks: {d.hooks}" if d.hooks else ""
        print(f"  loaded driver: {d.name:16s} [{flag}]{extra}")
    print()

    # ------------------------------------------------------------------
    # RUNTIME SCAN: after the fact, compare current driver table against
    # the known-good baseline (which does NOT include the rootkit driver).
    # ------------------------------------------------------------------
    banner("RUNTIME SCAN: driver_integrity_scanner vs. known-good baseline")
    report = scan(baseline=baseline_drivers, current=current_drivers)
    report.print_report()
    print()

    # ------------------------------------------------------------------
    # FINAL COMPARISON
    # ------------------------------------------------------------------
    banner("FINAL COMPARISON")
    print(f"Secure Boot ENABLED  : boot halted at "
          f"'{run_a.halted_at}' -- tampering caught IMMEDIATELY, before the "
          f"kernel or any driver ever ran.")
    print(f"Secure Boot DISABLED : boot completed anyway "
          f"(booted_fully={run_b.booted_fully}); tampering was measured and "
          f"reported but NOT enforced, letting an unsigned rootkit driver "
          f"load and hook the syscall table.")
    print(f"Runtime scanner      : caught the rootkit driver AFTER the fact "
          f"with {len(report.findings)} finding(s) "
          f"({sum(1 for f in report.findings if f.severity == 'CRITICAL')} CRITICAL), "
          f"but only because it happened to be run -- unlike Secure Boot, "
          f"nothing forces this scan to happen before damage occurs.")


if __name__ == "__main__":
    main()
