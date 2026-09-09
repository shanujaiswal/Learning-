"""
main.py

Runs the full SSH fleet hardening and compliance audit end-to-end:

  1. Generate the synthetic fleet inventory (sshd_config records + authorized_keys).
  2. Audit every host's config against the CIS-style benchmark, printing results
     as each host is checked (as a real fleet-wide tool would stream progress).
  3. Audit the authorized_keys inventory for key-hygiene red flags.
  4. Print a fleet-wide summary (average score, worst offenders, key findings).
  5. Write a full remediation report to ssh_compliance_report.md.

Entirely offline/simulated — no real SSH connections or paramiko network calls.
"""

from config_auditor import audit_fleet, fleet_average_score, worst_offenders
from fleet_inventory import generate_authorized_keys_inventory, generate_fleet
from key_hygiene_auditor import audit_key_hygiene
from remediation_generator import generate_report

SEPARATOR = "=" * 78


def print_header(title: str) -> None:
    print(f"\n{SEPARATOR}\n{title}\n{SEPARATOR}")


def audit_configs(fleet: list[dict]):
    print_header("PHASE 1: sshd_config compliance audit (per host)")
    audits = audit_fleet(fleet)
    for audit in audits:
        status = "COMPLIANT" if audit.score == 100.0 else "VIOLATIONS FOUND"
        print(f"\n[{audit.hostname}] score={audit.score:5.1f}/100  -> {status}")
        for result in audit.results:
            mark = "PASS" if result.passed else "FAIL"
            print(f"    [{mark}] {result.rule_id:12s} {result.title}")
            if not result.passed:
                print(f"           -> {result.detail}")
    return audits


def audit_keys(inventory: list[dict]) -> dict:
    print_header("PHASE 2: authorized_keys hygiene audit (fleet-wide)")
    report = audit_key_hygiene(inventory)
    print(f"Scanned {report['total_keys']} authorized_keys entries across the fleet.\n")

    print(f"Duplicate keys reused across accounts: {len(report['duplicates'])}")
    for finding in report["duplicates"]:
        print(f"  [DUPLICATE] {finding.fingerprint_preview}")
        for host, user in finding.locations:
            print(f"      -> {user}@{host}")

    print(f"\nUnlabeled keys (no owner identification): {len(report['unlabeled'])}")
    for finding in report["unlabeled"]:
        print(f"  [UNLABELED] {finding.user}@{finding.host} — {finding.fingerprint_preview}")

    return report


def print_summary(audits, key_report) -> None:
    print_header("PHASE 3: fleet-wide summary")
    avg_score = fleet_average_score(audits)
    fully_compliant = sum(1 for a in audits if a.score == 100.0)
    print(f"Hosts audited:           {len(audits)}")
    print(f"Fully compliant hosts:   {fully_compliant}/{len(audits)}")
    print(f"Average compliance score: {avg_score}/100")

    print("\nWorst offenders:")
    for i, audit in enumerate(worst_offenders(audits, limit=3), start=1):
        failed = ", ".join(r.rule_id for r in audit.failed_results) or "none"
        print(f"  {i}. {audit.hostname:24s} score={audit.score:5.1f}  failed=[{failed}]")

    print(f"\nKey hygiene: {len(key_report['duplicates'])} duplicate key group(s), "
          f"{len(key_report['unlabeled'])} unlabeled key(s).")


def main() -> None:
    print("SSH Fleet Hardening and Compliance Automation Tool")
    print("(Simulated fleet — no real SSH connections are made.)")

    fleet = generate_fleet()
    inventory = generate_authorized_keys_inventory(fleet)

    audits = audit_configs(fleet)
    key_report = audit_keys(inventory)

    print_summary(audits, key_report)

    report_path = generate_report(audits, key_report)
    print_header("REPORT WRITTEN")
    print(f"Full remediation report written to: {report_path}")


if __name__ == "__main__":
    main()
