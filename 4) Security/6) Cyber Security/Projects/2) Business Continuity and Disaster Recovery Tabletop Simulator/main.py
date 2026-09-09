"""
main.py

Runs the BC/DR tabletop exercise end to end:
  1. Prints the incident scenario.
  2. Simulates every affected system, printing its recovery timeline and
     RTO/RPO verdict as it's computed.
  3. Prints an overall exercise summary.
  4. Writes the "lessons learned" Markdown report to disk.
"""

from incident_scenario import (
    AFFECTED_SYSTEM_IDS,
    INCIDENT_NAME,
    INCIDENT_NARRATIVE,
    INCIDENT_START,
)
from recovery_simulator import simulate_all
from tabletop_report import generate_report


def _fmt(t):
    return t.strftime("%Y-%m-%d %H:%M")


def print_system_result(r):
    print(f"--- {r.display_name} ({r.system_id}) ---")
    print(f"  Incident time:            {_fmt(INCIDENT_START)}")
    print(
        f"  Last known-good backup:   "
        f"{r.actual_rpo_minutes:.0f} min before incident"
    )
    print(
        f"  RPO target / actual:      "
        f"{r.rpo_target_minutes} min / {r.actual_rpo_minutes:.0f} min -> "
        f"{'PASS' if r.rpo_pass else 'BREACH'}"
    )

    dep_note = ""
    if r.depends_on:
        dep_note = (
            f" (own steps: {r.own_recovery_minutes} min, "
            f"dependency wait on '{r.depends_on}': "
            f"{r.dependency_wait_minutes:.0f} min)"
        )
    print(f"  Recovery complete at:     {_fmt(r.recovery_complete_time)}{dep_note}")
    print(
        f"  RTO target / actual:      "
        f"{r.rto_target_minutes} min / {r.actual_rto_minutes:.0f} min -> "
        f"{'PASS' if r.rto_pass else 'BREACH'}"
    )
    if r.root_causes:
        print(f"  Root cause(s):            {', '.join(r.root_causes)}")
    print()


def main():
    print("=" * 70)
    print(f"BC/DR TABLETOP EXERCISE: {INCIDENT_NAME}")
    print("=" * 70)
    print(INCIDENT_NARRATIVE)
    print()

    results = simulate_all(AFFECTED_SYSTEM_IDS, INCIDENT_START)

    for system_id in AFFECTED_SYSTEM_IDS:
        print_system_result(results[system_id])

    rpo_breaches = [r for r in results.values() if not r.rpo_pass]
    rto_breaches = [r for r in results.values() if not r.rto_pass]
    any_breach = [r for r in results.values() if not (r.rpo_pass and r.rto_pass)]

    print("=" * 70)
    print("EXERCISE SUMMARY")
    print("=" * 70)
    print(f"Systems exercised:            {len(results)}")
    print(f"Systems breaching commitment: {len(any_breach)} / {len(results)}")
    print(f"  RPO breaches: {len(rpo_breaches)} -> "
          f"{[r.display_name for r in rpo_breaches] or 'none'}")
    print(f"  RTO breaches: {len(rto_breaches)} -> "
          f"{[r.display_name for r in rto_breaches] or 'none'}")
    for r in any_breach:
        print(f"    - {r.display_name}: {', '.join(r.root_causes)}")
    print()

    report_path = generate_report(results)
    print(f"Lessons-learned report written to: {report_path}")


if __name__ == "__main__":
    main()
