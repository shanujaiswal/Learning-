"""
campaign_report.py

Produces a management-facing Markdown report (`phishing_campaign_report.md`)
summarizing simulated phishing-awareness campaign results: overall
click/report/ignore rates, a department-level breakdown, a repeat-offender
list (by employee id/department/role only -- never the hidden susceptibility
score), and, once a second campaign has run, organization-wide improvement
metrics comparing campaign #1 to campaign #2.

This mirrors the kind of dashboard/report a real platform like KnowBe4 or
Proofpoint Security Awareness Training hands to a security team after a
simulated phishing exercise.
"""

from __future__ import annotations

from collections import defaultdict
from pathlib import Path

from campaign_simulator import CampaignResult, summarize
from employee_roster import Employee
from remedial_training_tracker import TrainingTracker

REPORT_PATH = Path(__file__).parent / "phishing_campaign_report.md"


def _department_breakdown(results: list[CampaignResult]) -> dict:
    by_dept = defaultdict(list)
    for r in results:
        by_dept[r.department].append(r)
    return {dept: summarize(rs) for dept, rs in sorted(by_dept.items())}


def _format_rate_table(stats: dict) -> str:
    return (
        f"| Total | Clicked | Reported | Ignored | Click Rate | Report Rate |\n"
        f"|---|---|---|---|---|---|\n"
        f"| {stats['total']} | {stats['clicked']} | {stats['reported']} | "
        f"{stats['ignored']} | {stats['click_rate']:.1%} | {stats['report_rate']:.1%} |\n"
    )


def _format_department_table(dept_stats: dict) -> str:
    lines = [
        "| Department | Total | Clicked | Reported | Ignored | Click Rate |",
        "|---|---|---|---|---|---|",
    ]
    for dept, stats in dept_stats.items():
        lines.append(
            f"| {dept} | {stats['total']} | {stats['clicked']} | "
            f"{stats['reported']} | {stats['ignored']} | {stats['click_rate']:.1%} |"
        )
    return "\n".join(lines) + "\n"


def _format_repeat_offenders(
    tracker: TrainingTracker, roster: list[Employee], min_clicks: int = 2
) -> str:
    offender_ids = tracker.repeat_offenders(min_clicks=min_clicks)
    by_id = {emp.employee_id: emp for emp in roster}

    if not offender_ids:
        return (
            "No employees clicked in 2 or more simulated campaigns "
            "(none reached the repeat-offender threshold in this run).\n"
        )

    lines = [
        "| Employee ID | Department | Role | Campaigns Clicked | Remedial Training |",
        "|---|---|---|---|---|",
    ]
    for emp_id in sorted(offender_ids):
        emp = by_id[emp_id]
        n_clicks = len(tracker.click_history[emp_id])
        trained = "Completed" if emp_id in tracker.trained else "Pending"
        lines.append(
            f"| {emp_id} | {emp.department} | {emp.role} | {n_clicks} | {trained} |"
        )
    return "\n".join(lines) + "\n"


def _improvement_section(
    stats_1: dict, stats_2: dict, trained_count: int, roster_size: int
) -> str:
    delta = stats_2["click_rate"] - stats_1["click_rate"]
    delta_pct_points = delta * 100
    relative_change = (
        (stats_2["click_rate"] - stats_1["click_rate"]) / stats_1["click_rate"] * 100
        if stats_1["click_rate"] > 0
        else 0.0
    )
    direction = "decreased" if delta < 0 else ("increased" if delta > 0 else "held steady")

    return (
        f"- Employees enrolled in remedial training after Campaign #1: "
        f"**{trained_count} / {roster_size}**\n"
        f"- Campaign #1 click rate: **{stats_1['click_rate']:.1%}**\n"
        f"- Campaign #2 click rate: **{stats_2['click_rate']:.1%}**\n"
        f"- Organization-wide click rate {direction} by "
        f"**{abs(delta_pct_points):.1f} percentage points** "
        f"({relative_change:+.1f}% relative change) after remedial training.\n"
    )


def build_report(
    roster: list[Employee],
    tracker: TrainingTracker,
    campaign_1_results: list[CampaignResult],
    campaign_1_template_name: str,
    campaign_2_results: list[CampaignResult] | None = None,
    campaign_2_template_name: str | None = None,
    trained_count: int = 0,
) -> str:
    stats_1 = summarize(campaign_1_results)
    dept_1 = _department_breakdown(campaign_1_results)

    lines = [
        "# Phishing Simulation Campaign Report",
        "",
        "> Internal security-awareness exercise. All employees, emails, and "
        "click/report events in this report are synthetic simulation data -- "
        "no real phishing email was ever sent.",
        "",
        "## Campaign #1",
        "",
        f"Template: **{campaign_1_template_name}**",
        "",
        "### Overall Results",
        "",
        _format_rate_table(stats_1),
        "### Department Breakdown",
        "",
        _format_department_table(dept_1),
        "### Repeat Offenders (2+ clicks across campaigns so far)",
        "",
        _format_repeat_offenders(tracker, roster, min_clicks=2),
    ]

    if campaign_2_results is not None:
        stats_2 = summarize(campaign_2_results)
        dept_2 = _department_breakdown(campaign_2_results)

        lines += [
            "## Campaign #2",
            "",
            f"Template: **{campaign_2_template_name}**",
            "",
            "### Overall Results",
            "",
            _format_rate_table(stats_2),
            "### Department Breakdown",
            "",
            _format_department_table(dept_2),
            "## Organization-Wide Improvement",
            "",
            _improvement_section(stats_1, stats_2, trained_count, len(roster)),
            "### Updated Repeat-Offender List (across both campaigns)",
            "",
            _format_repeat_offenders(tracker, roster, min_clicks=2),
        ]
    else:
        lines += [
            "## Remedial Training Enrollment",
            "",
            f"{trained_count} / {len(roster)} employees enrolled in mandatory "
            "remedial training based on Campaign #1 click behavior.\n",
        ]

    return "\n".join(lines)


def write_report(report_text: str, path: Path = REPORT_PATH) -> Path:
    path.write_text(report_text, encoding="utf-8")
    return path


if __name__ == "__main__":
    from campaign_simulator import run_campaign
    from employee_roster import build_roster
    from phishing_templates import get_template

    roster = build_roster()
    tracker = TrainingTracker()

    template = get_template("TPL-04")
    results = run_campaign(roster, template, "demo-campaign-1", seed=42)
    tracker.record_campaign(results)
    trained = tracker.enroll_clickers_from_campaign(roster, results)

    report = build_report(
        roster, tracker, results, template.name, trained_count=len(trained)
    )
    path = write_report(report)
    print(f"Report written to {path}")
