"""
profile_builder.py

Consolidates the output of the three recon modules (whois_and_dns_recon,
employee_osint, techstack_fingerprint) into one structured OSINT profile and
risk summary, then writes it to osint_profile_report.md.

Real-world equivalent: the "correlation" step described in OSINT automation
theory -- the actual value of OSINT tooling isn't any single source, it's
combining findings from multiple sources into one cohesive risk picture for
the assessor / client report.

AUTHORIZED USE ONLY: this module only formats data already produced by the
other (fully offline, simulated) recon modules -- it makes no network calls
of its own.
"""

from __future__ import annotations

from datetime import datetime

from mock_data_sources import TARGET_DOMAIN, TARGET_ORG_NAME

REPORT_PATH = "osint_profile_report.md"


def build_profile(dns_result: dict, employee_result: dict, techstack_result: dict) -> dict:
    """Merge the three recon results into one consolidated profile dict,
    along with a derived risk summary and an overall severity rating.
    """
    exposed_subdomains = dns_result["exposed_subdomains"]
    breached_employees = employee_result["breached_employees"]
    vulnerable_components = techstack_result["vulnerable_components"]

    risk_signals = []
    if exposed_subdomains:
        risk_signals.append(
            f"{len(exposed_subdomains)} sensitive subdomain(s) exposed in public DNS"
        )
    if breached_employees:
        risk_signals.append(
            f"{len(breached_employees)} employee credential(s) found in breach corpus"
        )
    if vulnerable_components:
        risk_signals.append(
            f"{len(vulnerable_components)} outdated/vulnerable tech-stack component(s)"
        )

    signal_count = len(risk_signals)
    if signal_count >= 3:
        overall_risk = "HIGH"
    elif signal_count == 2:
        overall_risk = "MEDIUM-HIGH"
    elif signal_count == 1:
        overall_risk = "MEDIUM"
    else:
        overall_risk = "LOW"

    return {
        "org_name": TARGET_ORG_NAME,
        "domain": TARGET_DOMAIN,
        "generated_at": datetime.now(),
        "whois": dns_result["whois"],
        "dns_records": dns_result["dns_records"],
        "exposed_subdomains": exposed_subdomains,
        "employees": employee_result["employees"],
        "breached_employees": breached_employees,
        "techstack": techstack_result["fingerprint"],
        "vulnerable_components": vulnerable_components,
        "risk_signals": risk_signals,
        "overall_risk": overall_risk,
    }


def render_markdown_report(profile: dict) -> str:
    """Render the consolidated profile as a Markdown OSINT report."""
    lines: list[str] = []
    add = lines.append

    add(f"# OSINT Profile Report: {profile['org_name']}")
    add("")
    add(f"- **Target domain:** `{profile['domain']}`")
    add(f"- **Generated at:** {profile['generated_at']:%Y-%m-%d %H:%M:%S}")
    add(f"- **Overall risk rating:** **{profile['overall_risk']}**")
    add("")
    add(
        "> All data in this report is simulated/mock, produced entirely offline for "
        "training purposes. No real domain, organization, or individual was queried."
    )
    add("")

    # --- Risk summary -------------------------------------------------
    add("## Risk Summary")
    add("")
    if profile["risk_signals"]:
        for signal in profile["risk_signals"]:
            add(f"- [!] {signal}")
    else:
        add("- No risk signals identified.")
    add("")

    # --- WHOIS ----------------------------------------------------------
    add("## WHOIS Registration")
    add("")
    add("| Field | Value |")
    add("|---|---|")
    for key, value in profile["whois"].items():
        add(f"| {key} | {value} |")
    add("")

    # --- DNS --------------------------------------------------------------
    add("## DNS Records")
    add("")
    add("| Name | Type | Value |")
    add("|---|---|---|")
    for record in profile["dns_records"]:
        add(f"| {record['name']} | {record['type']} | {record['value']} |")
    add("")

    add("### Exposed Sensitive Subdomains")
    add("")
    if profile["exposed_subdomains"]:
        add("| Subdomain | Record Type | Value | Matched Keyword |")
        add("|---|---|---|---|")
        for item in profile["exposed_subdomains"]:
            add(
                f"| {item['subdomain']} | {item['record_type']} | {item['value']} | "
                f"{item['matched_keyword']} |"
            )
    else:
        add("None found.")
    add("")

    # --- Employees --------------------------------------------------------
    add("## Employee Directory")
    add("")
    add("| Name | Title | Email |")
    add("|---|---|---|")
    for employee in profile["employees"]:
        add(f"| {employee.name} | {employee.title} | {employee.email} |")
    add("")

    add("### Breach Exposure Findings")
    add("")
    if profile["breached_employees"]:
        add("| Name | Email | Breaches | First Seen | Password Hint |")
        add("|---|---|---|---|---|")
        for finding in profile["breached_employees"]:
            breach_list = ", ".join(finding["breaches"])
            add(
                f"| {finding['name']} | {finding['email']} | {breach_list} | "
                f"{finding['first_seen']} | {finding['exposed_password_hint']} |"
            )
    else:
        add("No employee credentials found in breach corpus.")
    add("")

    # --- Tech stack ---------------------------------------------------------
    add("## Tech-Stack Fingerprint")
    add("")
    add("| Technology | Version | Category |")
    add("|---|---|---|")
    for component in profile["techstack"]:
        version = component["version"] or "<not disclosed>"
        add(f"| {component['technology']} | {version} | {component['category']} |")
    add("")

    add("### Vulnerable Components")
    add("")
    if profile["vulnerable_components"]:
        add("| Technology | Installed | Safe At/Above | CVE(s) | Description |")
        add("|---|---|---|---|---|")
        for finding in profile["vulnerable_components"]:
            add(
                f"| {finding['technology']} | {finding['installed_version']} | "
                f"{finding['safe_at_or_above']} | {finding['cve']} | {finding['description']} |"
            )
    else:
        add("No known-vulnerable versions detected.")
    add("")

    add("---")
    add(
        "*Report generated by the Automated OSINT Reconnaissance Framework "
        "(offline/simulated demo). For authorized engagements only.*"
    )
    add("")

    return "\n".join(lines)


def write_report(profile: dict, path: str = REPORT_PATH) -> str:
    """Render the profile to Markdown and write it to disk. Returns the path."""
    markdown = render_markdown_report(profile)
    with open(path, "w", encoding="utf-8") as handle:
        handle.write(markdown)
    return path
