"""
main.py

Runs the full recon-to-report pipeline end to end:

    enumerate  ->  scope-filter  ->  probe (in-scope only)  ->  write reports

...and then PROVES that no out-of-scope host was ever touched by a
probe, via an explicit assertion against vulnerability_probes.PROBE_LOG.

Run with:  python main.py
"""

from __future__ import annotations

import program_scope
import subdomain_enumerator
import scope_filter
import vulnerability_probes
import report_writer


def main() -> None:
    print("=" * 78)
    print(f" Bug Bounty Recon-to-Report Automation Toolkit")
    print(f" Program: {program_scope.PROGRAM_NAME}")
    print("=" * 78)

    # --- Step 1: Recon / subdomain enumeration (simulated, offline) ---
    print("\n[1/4] Enumerating candidate subdomains (simulated, fixed seed)...")
    candidates = subdomain_enumerator.enumerate_subdomains()
    print(f"      Discovered {len(candidates)} candidate hosts:")
    for host in candidates:
        print(f"        - {host}")

    # --- Step 2: Strict scope filtering (BEFORE any probing) ---
    print(f"\n[2/4] Filtering candidates against published scope rules...")
    result = scope_filter.filter_candidates(candidates, verbose=True)

    print(f"\n      -> {len(result.in_scope_hosts)} in-scope, "
          f"{len(result.excluded_hosts)} excluded per scope.")

    # --- Step 3: Probe in-scope survivors only ---
    print(f"\n[3/4] Running vulnerability probes against in-scope hosts only...")
    all_findings = []
    for host in result.in_scope_hosts:
        findings = vulnerability_probes.run_all_probes(host)
        if findings:
            print(f"      {host}: {len(findings)} confirmed finding(s)")
            for f in findings:
                print(f"        [{f.severity}] {f.title}")
        else:
            print(f"      {host}: no issues found (clean)")
        all_findings.extend(findings)

    # --- Compliance proof: no out-of-scope host ever touched by a probe ---
    probed = set(vulnerability_probes.PROBE_LOG.probed_hosts)
    excluded = set(result.excluded_hosts)
    violation = probed & excluded

    assert not violation, (
        f"SCOPE VIOLATION: the following out-of-scope hosts were probed: {violation}"
    )
    assert probed.issubset(set(result.in_scope_hosts)), (
        "SCOPE VIOLATION: a host was probed that never appeared in the in-scope list."
    )
    print(f"\n      COMPLIANCE CHECK PASSED: {len(probed)} host(s) probed, "
          f"0 out-of-scope hosts touched (excluded set has {len(excluded)} hosts).")

    # --- Step 4: Write submission-ready reports ---
    print(f"\n[4/4] Writing submission-ready reports for {len(all_findings)} confirmed finding(s)...")
    written = report_writer.write_reports(all_findings)

    print("\n" + "=" * 78)
    print(" FINAL REPORT LIST")
    print("=" * 78)
    for path in written:
        print(f"  - {path}")

    print(f"\nTotal confirmed findings: {len(all_findings)}")
    print(f"Total hosts discovered:   {len(candidates)}")
    print(f"Total in-scope hosts:     {len(result.in_scope_hosts)}")
    print(f"Total excluded per scope: {len(result.excluded_hosts)}")
    print(f"Out-of-scope hosts touched by any probe: {len(violation)} (must be 0)")


if __name__ == "__main__":
    main()
