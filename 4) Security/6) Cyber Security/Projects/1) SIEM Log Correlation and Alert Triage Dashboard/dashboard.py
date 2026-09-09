"""
dashboard.py
------------
Renders the analyst-facing console view a SOC analyst would actually work
from during a shift: a priority-sorted alert queue, severity/source
breakdown counts, and a mean-time-to-triage (MTTT) stat.

Real-world equivalent: this is the "Notable Events" queue in Splunk
Enterprise Security, or the "Incidents" list in Microsoft Sentinel /
IBM QRadar -- the single screen an analyst triages a shift from.

No ML here either: the "mean time to triage" figure is produced by a
synthetic-but-explainable analyst-review-time model (`estimate_review_seconds`)
that scales a base read time by severity and by how much evidence
(events + rule hits) the analyst has to read through -- not a learned
model, just a stand-in for "harder/bigger alerts take longer to read".
"""

from __future__ import annotations

import random
from dataclasses import dataclass

from alert_triage_engine import Alert

# ---------------------------------------------------------------------------
# Synthetic analyst-review-time model
# ---------------------------------------------------------------------------

# Base seconds an analyst spends just opening/reading an alert of this
# severity, before accounting for how much evidence there is to review.
BASE_REVIEW_SECONDS = {
    "CRITICAL": 240,
    "HIGH": 150,
    "MEDIUM": 80,
    "LOW": 30,
}
SECONDS_PER_EVENT = 4          # extra reading time per correlated log line
SECONDS_PER_RULE_HIT = 15      # extra time per rule explanation to verify
REVIEW_TIME_JITTER = 0.20      # +/-20% analyst-to-analyst variance
REVIEW_SEED = 4242


def estimate_review_seconds(alert: Alert, rng: random.Random) -> float:
    """
    Synthetic model of how long a human analyst would spend triaging one
    alert: a severity-based floor, plus linear time for reading each
    correlated event and verifying each fired rule, plus jitter to stand
    in for analyst experience / fatigue / interruptions.
    """
    base = BASE_REVIEW_SECONDS.get(alert.severity_label, BASE_REVIEW_SECONDS["LOW"])
    evidence_time = (len(alert.events) * SECONDS_PER_EVENT
                      + len(alert.rule_hits) * SECONDS_PER_RULE_HIT)
    jitter = rng.uniform(1 - REVIEW_TIME_JITTER, 1 + REVIEW_TIME_JITTER)
    return (base + evidence_time) * jitter


@dataclass
class TriageTimeStats:
    per_alert_seconds: dict  # alert_id -> seconds
    mean_seconds: float
    total_seconds: float


def compute_triage_time_stats(alerts: list[Alert], seed: int = REVIEW_SEED) -> TriageTimeStats:
    rng = random.Random(seed)
    per_alert = {a.alert_id: estimate_review_seconds(a, rng) for a in alerts}
    total = sum(per_alert.values())
    mean = total / len(per_alert) if per_alert else 0.0
    return TriageTimeStats(per_alert_seconds=per_alert, mean_seconds=mean, total_seconds=total)


def _fmt_mmss(seconds: float) -> str:
    m, s = divmod(int(round(seconds)), 60)
    return f"{m}m {s:02d}s"


# ---------------------------------------------------------------------------
# Breakdown counts
# ---------------------------------------------------------------------------

SEVERITY_ORDER = ["CRITICAL", "HIGH", "MEDIUM", "LOW"]


def severity_breakdown(alerts: list[Alert]) -> dict:
    counts = {label: 0 for label in SEVERITY_ORDER}
    for a in alerts:
        counts[a.severity_label] += 1
    return counts


def source_breakdown(alerts: list[Alert]) -> dict:
    """Counts how many alerts involve each raw log source (an alert can involve more than one)."""
    counts = {"firewall": 0, "ids": 0, "auth": 0}
    for a in alerts:
        for src in a.sources:
            counts[src] = counts.get(src, 0) + 1
    return counts


# ---------------------------------------------------------------------------
# Console rendering
# ---------------------------------------------------------------------------

SEVERITY_ICON = {"CRITICAL": "!!!", "HIGH": " !! ", "MEDIUM": " ! ", "LOW": "  ."}

_BAR_WIDTH = 30


def _bar(count: int, max_count: int, width: int = _BAR_WIDTH) -> str:
    if max_count <= 0:
        return ""
    filled = int(round((count / max_count) * width))
    return "#" * filled + "-" * (width - filled)


def render_alert_queue(alerts: list[Alert]) -> str:
    lines = []
    lines.append("=" * 100)
    lines.append("ALERT QUEUE  (sorted by priority: severity desc, then earliest-first)")
    lines.append("=" * 100)
    if not alerts:
        lines.append("  (no alerts -- queue is empty)")
        return "\n".join(lines)

    header = (f"{'#':<3} {'ALERT ID':<10} {'SEV':<9} {'SCORE':<6} {'SRC IP':<16} "
              f"{'SOURCES':<16} {'FIRST SEEN':<20} {'EVIDENCE'}")
    lines.append(header)
    lines.append("-" * 100)
    for i, a in enumerate(alerts, start=1):
        sources = ",".join(sorted(a.sources))
        first_seen = a.first_seen.strftime("%Y-%m-%d %H:%M:%S")
        lines.append(f"{i:<3} {a.alert_id:<10} {a.severity_label:<9} {a.severity_score:<6} "
                     f"{a.src_ip:<16} {sources:<16} {first_seen:<20} "
                     f"{len(a.rule_hits)} rule(s)/{len(a.events)} event(s)")
        lines.append(f"    -> {a.summary}")
        lines.append(f"    -> Recommended action: {a.recommended_action}")
    return "\n".join(lines)


def render_breakdowns(alerts: list[Alert]) -> str:
    lines = []
    lines.append("=" * 100)
    lines.append("BREAKDOWN")
    lines.append("=" * 100)

    sev_counts = severity_breakdown(alerts)
    max_sev = max(sev_counts.values()) if sev_counts else 0
    lines.append("By severity:")
    for label in SEVERITY_ORDER:
        c = sev_counts[label]
        lines.append(f"  {label:<9} {c:>3}  {_bar(c, max_sev)}")

    src_counts = source_breakdown(alerts)
    max_src = max(src_counts.values()) if src_counts else 0
    lines.append("By source (alerts touching each log source):")
    for src in ("firewall", "ids", "auth"):
        c = src_counts.get(src, 0)
        lines.append(f"  {src:<9} {c:>3}  {_bar(c, max_src)}")

    return "\n".join(lines)


def render_triage_time_stats(stats: TriageTimeStats, alerts: list[Alert]) -> str:
    lines = []
    lines.append("=" * 100)
    lines.append("SIMULATED TRIAGE-TIME STAT (synthetic analyst-review-time model)")
    lines.append("=" * 100)
    lines.append(f"  Alerts triaged this shift : {len(alerts)}")
    lines.append(f"  Mean time to triage (MTTT): {_fmt_mmss(stats.mean_seconds)}")
    lines.append(f"  Total analyst time spent  : {_fmt_mmss(stats.total_seconds)}")
    if alerts:
        slowest_id = max(stats.per_alert_seconds, key=stats.per_alert_seconds.get)
        fastest_id = min(stats.per_alert_seconds, key=stats.per_alert_seconds.get)
        lines.append(f"  Slowest alert to triage   : {slowest_id} "
                      f"({_fmt_mmss(stats.per_alert_seconds[slowest_id])})")
        lines.append(f"  Fastest alert to triage   : {fastest_id} "
                      f"({_fmt_mmss(stats.per_alert_seconds[fastest_id])})")
    return "\n".join(lines)


def render_dashboard(alerts: list[Alert]) -> str:
    stats = compute_triage_time_stats(alerts)
    sections = [
        render_alert_queue(alerts),
        render_breakdowns(alerts),
        render_triage_time_stats(stats, alerts),
    ]
    return "\n\n".join(sections)


if __name__ == "__main__":
    # Standalone smoke test: run the upstream stages so this module can be
    # exercised on its own without main.py.
    from log_sources import generate_shift_logs
    from alert_triage_engine import triage

    events = generate_shift_logs()
    alerts = triage(events)
    print(render_dashboard(alerts))
