"""
main.py

Runs the full, offline, simulated OSINT reconnaissance pipeline against the
mock target "Nimbus Retail Group" (nimbusretail.example):

    1. WHOIS + DNS recon           (whois_and_dns_recon.py)
    2. Employee OSINT / breach check (employee_osint.py)
    3. Tech-stack fingerprinting   (techstack_fingerprint.py)
    4. Consolidated profile + report (profile_builder.py)

Prints progress per source, then writes the consolidated OSINT profile to
osint_profile_report.md and prints it to the console.

AUTHORIZED USE ONLY: every "source" queried here is a local, fixed,
simulated dataset in mock_data_sources.py. No real network requests, real
organizations, real people, or real credentials are involved. This script is
safe to run repeatedly and produces identical output every time.
"""

from __future__ import annotations

import employee_osint
import techstack_fingerprint
import whois_and_dns_recon
from mock_data_sources import TARGET_DOMAIN, TARGET_ORG_NAME
from profile_builder import build_profile, render_markdown_report, write_report


def banner(text: str) -> None:
    print("\n" + "=" * 78)
    print(text)
    print("=" * 78)


def main() -> None:
    banner(f"Automated OSINT Reconnaissance Framework -- target: {TARGET_ORG_NAME}")
    print(f"Target domain: {TARGET_DOMAIN}")
    print("Mode: fully offline / simulated public sources (no real network calls)")

    # --- Step 1: WHOIS + DNS -------------------------------------------
    banner("[1/3] WHOIS + DNS recon (mock theHarvester/Amass-style lookup)")
    dns_result = whois_and_dns_recon.run(TARGET_DOMAIN)
    print(f"[+] WHOIS registrar: {dns_result['whois']['registrar']}")
    print(f"[+] DNS records retrieved: {len(dns_result['dns_records'])}")
    if dns_result["exposed_subdomains"]:
        for item in dns_result["exposed_subdomains"]:
            print(f"    [RISK] Exposed subdomain: {item['subdomain']} ({item['value']})")
    else:
        print("    No exposed sensitive subdomains found.")

    # --- Step 2: Employee OSINT -----------------------------------------
    banner("[2/3] Employee OSINT (mock LinkedIn scrape + breach-database check)")
    employee_result = employee_osint.run()
    print(f"[+] Employees discovered: {len(employee_result['employees'])}")
    if employee_result["breached_employees"]:
        for finding in employee_result["breached_employees"]:
            print(
                f"    [RISK] {finding['name']} <{finding['email']}> exposed in: "
                f"{', '.join(finding['breaches'])}"
            )
    else:
        print("    No employee credentials found in breach corpus.")

    # --- Step 3: Tech-stack fingerprint -----------------------------------
    banner("[3/3] Tech-stack fingerprint (mock Wappalyzer-style scan)")
    techstack_result = techstack_fingerprint.run(TARGET_DOMAIN)
    print(f"[+] Technologies detected: {len(techstack_result['fingerprint'])}")
    if techstack_result["vulnerable_components"]:
        for finding in techstack_result["vulnerable_components"]:
            print(
                f"    [RISK] {finding['technology']} {finding['installed_version']} "
                f"is outdated/vulnerable ({finding['cve']})"
            )
    else:
        print("    No known-vulnerable versions detected.")

    # --- Consolidate --------------------------------------------------------
    banner("Consolidating OSINT profile + risk summary")
    profile = build_profile(dns_result, employee_result, techstack_result)
    report_path = write_report(profile)
    print(f"[+] Consolidated report written to: {report_path}")
    print(f"[+] Overall risk rating: {profile['overall_risk']}")
    print(f"[+] Risk signals found: {len(profile['risk_signals'])}")
    for signal in profile["risk_signals"]:
        print(f"    - {signal}")

    banner("Full consolidated OSINT profile report")
    print(render_markdown_report(profile))


if __name__ == "__main__":
    main()
