"""
remediation_report.py
=======================
Turns a list of Finding records into a prioritized Markdown report, complete
with the exact `chmod`/`chown` command a sysadmin would run to fix each one.
This is the "hand this to the ops team" output stage -- the equivalent of a
Lynis/OpenSCAP HTML/text report, or a CIS-benchmark scan's remediation
appendix.
"""

from __future__ import annotations

from datetime import datetime, timezone

from auditor import Auditor, Finding, Severity

REPORT_FILENAME = "permission_audit_report.md"

_SEVERITY_EMOJI = {
    Severity.CRITICAL: "🔴",
    Severity.HIGH: "🟠",
    Severity.MEDIUM: "🟡",
    Severity.LOW: "🟢",
}


def _finding_block(f: Finding, index: int) -> str:
    cmds = f.fix_commands()
    cmd_block = "\n".join(f"    {c}" for c in cmds) if cmds else "    (manual review required)"
    kind = "Directory" if f.is_dir else "File"
    return (
        f"### {index}. {_SEVERITY_EMOJI[f.severity]} [{f.severity.value}] `{f.path}`\n\n"
        f"- **Issue type:** {f.issue_type}\n"
        f"- **Kind:** {kind}\n"
        f"- **Owner:group:** `{f.owner}:{f.group}`\n"
        f"- **Current mode:** `{f.current_mode}`\n"
        f"- **Finding:** {f.description}\n"
        f"- **Fix:**\n\n"
        f"  ```bash\n{cmd_block}\n  ```\n"
    )


def build_report(auditor: Auditor) -> str:
    findings = auditor.findings
    summary = auditor.summary()
    now = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")

    lines = [
        "# Multi-User Linux File Permission & Access Control Audit Report",
        "",
        f"Generated: {now}  ",
        f"Entries scanned: {summary['ENTRIES_SCANNED']}  ",
        f"Total findings: {summary['TOTAL']}",
        "",
        "## Severity Breakdown",
        "",
        "| Severity | Count |",
        "|----------|-------|",
    ]
    for sev in Severity:
        lines.append(f"| {_SEVERITY_EMOJI[sev]} {sev.value} | {summary[sev.value]} |")

    lines += [
        "",
        "## Issue Type Breakdown",
        "",
        "| Issue Type | Count |",
        "|------------|-------|",
    ]
    issue_counts: dict[str, int] = {}
    for f in findings:
        issue_counts[f.issue_type] = issue_counts.get(f.issue_type, 0) + 1
    for issue_type, count in sorted(issue_counts.items(), key=lambda kv: -kv[1]):
        lines.append(f"| {issue_type} | {count} |")

    lines += [
        "",
        "## Prioritized Findings (most severe first)",
        "",
    ]

    if not findings:
        lines.append("No findings -- the filesystem is fully compliant with policy.")
    else:
        for i, f in enumerate(findings, start=1):
            lines.append(_finding_block(f, i))

    lines += [
        "## All Remediation Commands (copy/paste block)",
        "",
        "```bash",
    ]
    for f in findings:
        lines.extend(f.fix_commands())
    lines.append("```")
    lines.append("")

    return "\n".join(lines)


def write_report(auditor: Auditor, path: str = REPORT_FILENAME) -> str:
    content = build_report(auditor)
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(content)
    return path


if __name__ == "__main__":
    a = Auditor()
    a.run()
    out = write_report(a)
    print(f"Report written to: {out}")
