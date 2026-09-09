"""
driver_integrity_scanner.py

A RUNTIME integrity scanner -- the tool that catches what Secure Boot
didn't stop. This is the "after the fact" layer: if Secure Boot was
disabled (or bypassed) and an unsigned rootkit driver made it into the
kernel's driver table, this scanner is what a defender/EDR/rootkit
detector (conceptually: GMER, TDSSKiller, a kernel integrity monitor)
would run to notice something is wrong.

It works purely by comparison against a known-good BASELINE:

  1. Are there any drivers currently loaded that are NOT in the baseline
     (unexpected/unknown drivers)?
  2. Of the drivers that exist, is each one signed? An unsigned driver is
     immediately suspicious regardless of anything else.
  3. Does any currently-loaded driver's hash differ from its known-good
     baseline hash (tampering of an otherwise-known driver)?
  4. Does any currently-loaded driver report hooking a sensitive kernel
     structure (here: the syscall table) that nothing in the baseline
     touches? Hooking the syscall table to intercept/hide syscalls is a
     classic rootkit technique (hiding files, processes, network
     connections from normal enumeration).

This module is intentionally independent of the boot chain modules -- in
the real world, Secure Boot (boot-time) and a runtime rootkit scanner
(post-boot) are two different lines of defense that don't share code,
and the scanner has to work even on a system that never had a chain of
trust guarantee in the first place.
"""

from __future__ import annotations

from dataclasses import dataclass, field

from boot_chain_simulator import Driver


SENSITIVE_STRUCTURES = {
    "syscall_table": "System Call Table (dispatch table for kernel syscalls)",
}


@dataclass
class Finding:
    driver: str
    severity: str  # "INFO" | "WARNING" | "CRITICAL"
    reason: str

    def __str__(self) -> str:
        return f"[{self.severity}] {self.driver}: {self.reason}"


@dataclass
class ScanReport:
    findings: list = field(default_factory=list)

    @property
    def clean(self) -> bool:
        return len(self.findings) == 0

    def add(self, driver: str, severity: str, reason: str) -> None:
        self.findings.append(Finding(driver, severity, reason))

    def print_report(self) -> None:
        print("--- Runtime Driver Integrity Scan ---")
        if self.clean:
            print("No anomalies found: current driver table matches the known-good "
                  "baseline, all drivers signed, no sensitive structures hooked.")
            return
        for finding in self.findings:
            print(finding)
        criticals = [f for f in self.findings if f.severity == "CRITICAL"]
        print(f"\nResult: {len(self.findings)} finding(s), "
              f"{len(criticals)} CRITICAL. Rootkit indicators present."
              if criticals else f"\nResult: {len(self.findings)} finding(s).")


def scan(baseline: list, current: list) -> ScanReport:
    """Compare the currently-loaded driver table against a known-good
    baseline and produce a ScanReport of anomalies.

    baseline -- list[Driver] representing the known-good, expected state
                (what was present on a trusted, uncompromised system).
    current  -- list[Driver] representing what is ACTUALLY loaded right
                now, which may include everything in the baseline plus
                anything a rootkit slipped in.
    """
    report = ScanReport()
    baseline_by_name = {d.name: d for d in baseline}

    for driver in current:
        base = baseline_by_name.get(driver.name)

        if base is None:
            # Not in the known-good baseline at all -- an unexpected driver.
            report.add(
                driver.name,
                "CRITICAL" if not driver.signed else "WARNING",
                "driver is not present in known-good baseline (unexpected/unknown "
                "driver loaded)" + ("" if driver.signed else " and is UNSIGNED"),
            )
        elif driver.measured_hash() != base.measured_hash():
            report.add(
                driver.name,
                "CRITICAL",
                "driver hash differs from known-good baseline (image was modified "
                "after the baseline was captured -- tampering)",
            )

        if not driver.signed:
            report.add(
                driver.name,
                "CRITICAL",
                "driver has no valid signature (unsigned kernel-mode code)",
            )

        for hook in driver.hooks:
            struct_key = hook.split("[")[0]
            description = SENSITIVE_STRUCTURES.get(struct_key, struct_key)
            report.add(
                driver.name,
                "CRITICAL",
                f"driver hooks a sensitive kernel structure: {hook} "
                f"({description}) -- classic rootkit hooking technique",
            )

    return report
