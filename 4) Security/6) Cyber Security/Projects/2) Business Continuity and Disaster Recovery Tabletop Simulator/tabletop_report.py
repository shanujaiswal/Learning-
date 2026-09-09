"""
tabletop_report.py

Compiles the "lessons learned" post-exercise report -- the same kind of
artifact a real BC/DR tabletop exercise produces: per-system pass/fail
against RTO and RPO, the root cause of any breach, and recommended fixes,
written out as a Markdown after-action report.
"""

from datetime import datetime
from typing import Dict, List

from recovery_simulator import (
    RecoveryResult,
    CAUSE_STALE_BACKUP,
    CAUSE_SLOW_PROCEDURE,
    CAUSE_DEPENDENCY_DELAY,
)
from critical_systems_registry import get_system
from incident_scenario import INCIDENT_NAME, INCIDENT_NARRATIVE, INCIDENT_START

RECOMMENDATIONS = {
    CAUSE_STALE_BACKUP: (
        "Increase backup frequency so the worst-case gap between backups no "
        "longer exceeds the RPO target (e.g. move from nightly to hourly, or "
        "add continuous log shipping/replication). Also verify offsite/"
        "immutable copies are actually current -- a documented schedule that "
        "silently isn't being run is the same as having no backup at all."
    ),
    CAUSE_SLOW_PROCEDURE: (
        "Re-engineer or automate the slowest recovery steps (e.g. scripted "
        "failover instead of manual failover, pre-staged DR-site images) so "
        "the procedure's total estimated time fits under the RTO target, or "
        "renegotiate the RTO target with the business if the current cost of "
        "a faster procedure isn't justified."
    ),
    CAUSE_DEPENDENCY_DELAY: (
        "Either shorten the upstream system's own recovery time, remove the "
        "hard dependency (e.g. allow degraded/read-only operation before the "
        "upstream system is fully back), or explicitly extend this system's "
        "RTO target to reflect the real, dependency-inclusive recovery time "
        "so the commitment being made to the business is honest."
    ),
}


def _fmt_time(t: datetime) -> str:
    return t.strftime("%Y-%m-%d %H:%M")


def _verdict(passed: bool) -> str:
    return "PASS" if passed else "**BREACH**"


def generate_report(
    results: Dict[str, RecoveryResult], output_path: str = "bcdr_tabletop_report.md"
) -> str:
    lines: List[str] = []

    breached = [r for r in results.values() if not (r.rpo_pass and r.rto_pass)]
    rpo_breaches = [r for r in results.values() if not r.rpo_pass]
    rto_breaches = [r for r in results.values() if not r.rto_pass]

    lines.append("# BC/DR Tabletop Exercise -- Lessons Learned Report")
    lines.append("")
    lines.append(f"**Scenario:** {INCIDENT_NAME}")
    lines.append(f"**Incident start:** {_fmt_time(INCIDENT_START)}")
    lines.append(f"**Systems exercised:** {len(results)}")
    lines.append(f"**Systems breaching commitments:** {len(breached)} / {len(results)}")
    lines.append("")
    lines.append("## Narrative")
    lines.append("")
    lines.append(INCIDENT_NARRATIVE)
    lines.append("")

    lines.append("## Executive Summary")
    lines.append("")
    if breached:
        lines.append(
            f"Of {len(results)} business-critical systems exercised, "
            f"**{len(breached)}** would have breached at least one BC/DR "
            f"commitment in a real incident: **{len(rpo_breaches)}** RPO "
            f"breach(es) and **{len(rto_breaches)}** RTO breach(es)."
        )
    else:
        lines.append(
            "All systems exercised would have met both their RTO and RPO "
            "commitments in this scenario."
        )
    lines.append("")

    lines.append("## Per-System Results")
    lines.append("")
    lines.append(
        "| System | RPO Target | Actual RPO | RPO Verdict | RTO Target | "
        "Actual RTO | RTO Verdict |"
    )
    lines.append(
        "|---|---|---|---|---|---|---|"
    )
    for r in results.values():
        lines.append(
            f"| {r.display_name} "
            f"| {r.rpo_target_minutes} min "
            f"| {r.actual_rpo_minutes:.0f} min "
            f"| {_verdict(r.rpo_pass)} "
            f"| {r.rto_target_minutes} min "
            f"| {r.actual_rto_minutes:.0f} min "
            f"| {_verdict(r.rto_pass)} |"
        )
    lines.append("")

    lines.append("## Detail, Root Cause, and Recommended Fixes")
    lines.append("")
    for r in results.values():
        system = get_system(r.system_id)
        lines.append(f"### {r.display_name} (`{r.system_id}`)")
        lines.append("")
        lines.append(
            f"- Last known-good backup: `{_fmt_time(system.last_backup_time)}` "
            f"(backup frequency: every {system.backup_frequency_minutes} min)"
        )
        lines.append(
            f"- Actual data-loss window (RPO): **{r.actual_rpo_minutes:.0f} min** "
            f"vs target **{r.rpo_target_minutes} min** -> {_verdict(r.rpo_pass)}"
        )
        lines.append(
            f"- Own recovery-step time: {r.own_recovery_minutes} min"
            + (
                f" + dependency-chain wait: {r.dependency_wait_minutes:.0f} min "
                f"(waiting on `{r.depends_on}`)"
                if r.depends_on
                else ""
            )
        )
        lines.append(
            f"- Actual recovery time (RTO): **{r.actual_rto_minutes:.0f} min** "
            f"vs target **{r.rto_target_minutes} min** -> {_verdict(r.rto_pass)}"
        )
        if r.root_causes:
            lines.append(f"- **Root cause(s):** {', '.join(r.root_causes)}")
            lines.append("- **Recommended fix(es):**")
            for cause in r.root_causes:
                lines.append(f"  - {RECOMMENDATIONS[cause]}")
        else:
            lines.append(
                "- Root cause: none -- this system met both commitments in "
                "this exercise."
            )
        lines.append("")

    lines.append("## Action Items")
    lines.append("")
    if breached:
        for r in breached:
            causes = ", ".join(r.root_causes)
            lines.append(
                f"- [ ] **{r.display_name}**: address {causes} -- owner: TBD, "
                f"deadline: TBD"
            )
    else:
        lines.append("- No action items -- all commitments were met.")
    lines.append("")

    report_text = "\n".join(lines)

    with open(output_path, "w", encoding="utf-8") as f:
        f.write(report_text)

    return output_path
