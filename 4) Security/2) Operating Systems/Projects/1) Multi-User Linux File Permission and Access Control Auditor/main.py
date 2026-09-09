"""
main.py
========
Runs the full audit end to end:
  1. Build the simulated filesystem.
  2. Walk it with Auditor, printing each finding as it's discovered.
  3. Print a severity summary.
  4. Generate the Markdown remediation report.

Usage:
    python main.py
"""

from __future__ import annotations

import os

from auditor import Auditor, Severity
from filesystem_simulator import build_filesystem
from remediation_report import REPORT_FILENAME, write_report

_SEVERITY_TAG = {
    Severity.CRITICAL: "!! CRITICAL",
    Severity.HIGH: "!  HIGH    ",
    Severity.MEDIUM: "   MEDIUM  ",
    Severity.LOW: "   LOW     ",
}


def _print_finding(f):
    print(f"  [{_SEVERITY_TAG[f.severity]}] {f.issue_type:<26} {f.path}")


def main() -> None:
    print("=" * 78)
    print("Multi-User Linux File Permission and Access Control Auditor")
    print("=" * 78)

    entries = build_filesystem()
    print(f"\nSimulated filesystem loaded: {len(entries)} entries.\n")

    print("Scanning for misconfigurations...\n")
    auditor = Auditor(entries)
    findings = auditor.run(on_finding=_print_finding)

    summary = auditor.summary()
    print("\n" + "-" * 78)
    print("SUMMARY")
    print("-" * 78)
    print(f"Entries scanned : {summary['ENTRIES_SCANNED']}")
    print(f"Total findings  : {summary['TOTAL']}")
    for sev in Severity:
        print(f"  {sev.value:<9}: {summary[sev.value]}")

    print("\nGenerating remediation report...")
    report_path = write_report(auditor, REPORT_FILENAME)
    abs_path = os.path.abspath(report_path)
    print(f"Report written to: {abs_path}")

    if not findings:
        print("\nNo misconfigurations found -- filesystem is fully compliant.")
    else:
        worst = findings[0]
        print(
            f"\nMost severe finding: [{worst.severity.value}] {worst.issue_type} "
            f"on '{worst.path}'"
        )


if __name__ == "__main__":
    main()
