"""
remediation_generator.py

Turns config_auditor.HostAudit results and key_hygiene_auditor findings into
concrete, copy-pasteable remediation: exact `sshd_config` line changes plus the
shell commands an engineer would run to apply and reload them. Writes everything
to `ssh_compliance_report.md`.
"""

from datetime import datetime, timezone

from config_auditor import HostAudit, fleet_average_score, worst_offenders

REPORT_PATH = "ssh_compliance_report.md"


def _host_remediation_block(audit: HostAudit) -> str:
    lines = [f"### {audit.hostname} ({audit.ip}) — score {audit.score}/100"]

    if not audit.failed_results:
        lines.append("\nAll benchmark rules passed. No remediation required.\n")
        return "\n".join(lines)

    lines.append("\n**Failed checks:**\n")
    for result in audit.failed_results:
        lines.append(f"- `{result.rule_id}` [{result.severity.upper()}] {result.title}")
        lines.append(f"  - Finding: {result.detail}")

    lines.append("\n**Remediation — apply these lines in `/etc/ssh/sshd_config`:**\n")
    lines.append("```")
    for result in audit.failed_results:
        for line in result.remediation:
            lines.append(line)
    lines.append("```")

    lines.append("\n**Commands to apply and reload:**\n")
    lines.append("```bash")
    lines.append(f"# On {audit.hostname}, back up first:")
    lines.append("sudo cp /etc/ssh/sshd_config /etc/ssh/sshd_config.bak.$(date +%s)")
    for result in audit.failed_results:
        for line in result.remediation:
            directive = line.split()[0]
            lines.append(
                f"sudo sed -i 's/^#\\?{directive}.*/{line}/' /etc/ssh/sshd_config"
            )
    lines.append("sudo sshd -t   # validate config syntax before reloading")
    lines.append("sudo systemctl reload sshd")
    lines.append("```")

    return "\n".join(lines)


def _key_hygiene_section(key_report: dict) -> str:
    lines = ["## Key Hygiene Findings\n"]
    lines.append(f"Scanned **{key_report['total_keys']}** authorized_keys entries across the fleet.\n")

    duplicates = key_report["duplicates"]
    lines.append(f"### Duplicate keys reused across accounts ({len(duplicates)})\n")
    if not duplicates:
        lines.append("None found.\n")
    else:
        for finding in duplicates:
            locations = ", ".join(f"`{user}@{host}`" for host, user in finding.locations)
            lines.append(f"- **{finding.fingerprint_preview}** installed on {len(finding.locations)} accounts: {locations}")
        lines.append(
            "\n**Remediation:** generate a distinct key per account/host, "
            "distribute the new public keys, then revoke the shared key everywhere it appears:\n"
        )
        lines.append("```bash")
        for finding in duplicates:
            for host, user in finding.locations:
                lines.append(f"ssh-keygen -t ed25519 -f ~/.ssh/{user}_{host.split('.')[0]}_ed25519 -C '{user}@{host}'")
            lines.append(
                "# then remove the old shared key's line from each account's authorized_keys:"
            )
            for host, user in finding.locations:
                lines.append(
                    f"sudo sed -i '/{finding.key_material[:16]}/d' /home/{user}/.ssh/authorized_keys  # on {host}"
                )
        lines.append("```\n")

    unlabeled = key_report["unlabeled"]
    lines.append(f"### Unlabeled keys — no owner identification ({len(unlabeled)})\n")
    if not unlabeled:
        lines.append("None found.\n")
    else:
        for finding in unlabeled:
            lines.append(f"- `{finding.user}@{finding.host}` — {finding.fingerprint_preview} (empty comment field)")
        lines.append(
            "\n**Remediation:** identify the owner (check deployment/onboarding records), "
            "then append an identifying comment or remove the key if the owner cannot be confirmed:\n"
        )
        lines.append("```bash")
        for finding in unlabeled:
            lines.append(
                f"# on {finding.host}: confirm the owner, then either label it —"
            )
            lines.append(
                f"sudo sed -i 's|{finding.key_material[:16]}.*|& owner-confirmed@{finding.host}|' "
                f"/home/{finding.user}/.ssh/authorized_keys"
            )
            lines.append(
                f"# — or remove it if unowned:"
            )
            lines.append(
                f"sudo sed -i '/{finding.key_material[:16]}/d' /home/{finding.user}/.ssh/authorized_keys"
            )
        lines.append("```\n")

    return "\n".join(lines)


def generate_report(audits: list[HostAudit], key_report: dict, report_path: str = REPORT_PATH) -> str:
    """Build the full markdown compliance report and write it to `report_path`."""
    generated_at = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
    avg_score = fleet_average_score(audits)
    worst = worst_offenders(audits, limit=3)
    fully_compliant = sum(1 for a in audits if a.score == 100.0)

    lines = [
        "# SSH Fleet Compliance Report",
        f"\nGenerated: {generated_at}",
        f"\nFleet size: {len(audits)} hosts | Average compliance score: **{avg_score}/100** "
        f"| Fully compliant hosts: {fully_compliant}/{len(audits)}",
        "\n## Worst Offenders\n",
        "| Rank | Host | Score | Failed Rules |",
        "|------|------|-------|---------------|",
    ]
    for i, audit in enumerate(worst, start=1):
        failed_titles = ", ".join(r.rule_id for r in audit.failed_results) or "none"
        lines.append(f"| {i} | {audit.hostname} | {audit.score} | {failed_titles} |")

    lines.append("\n## Per-Host Findings and Remediation\n")
    for audit in audits:
        lines.append(_host_remediation_block(audit))
        lines.append("")

    lines.append(_key_hygiene_section(key_report))

    report_text = "\n".join(lines)
    with open(report_path, "w", encoding="utf-8") as f:
        f.write(report_text)

    return report_path
