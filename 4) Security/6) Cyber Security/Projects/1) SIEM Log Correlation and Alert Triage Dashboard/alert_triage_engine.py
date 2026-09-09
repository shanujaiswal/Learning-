"""
alert_triage_engine.py
------------------------
Turns raw, correlated event groups into structured Alert objects with a
rule-based (NOT machine-learned) severity score, then ranks them into a
priority queue. This mirrors the "notable event" / "incident" layer that
sits on top of correlation searches in Splunk Enterprise Security, or the
"incident" object Sentinel creates once an analytics rule fires.

Deliberately simple and auditable: severity is just the sum of the
weights of whichever correlation_rules.RuleHit fired, mapped onto four
severity bands. Every point in the score can be traced back to one
explainable rule -- there is no black box here.
"""

from __future__ import annotations

import itertools
from dataclasses import dataclass, field
from datetime import timedelta

from correlation_rules import RuleHit, evaluate_group, group_events_by_ip_window
from log_sources import LogEvent

# Severity bands: cumulative rule-weight score -> label.
SEVERITY_BANDS = [
    (70, "CRITICAL"),
    (45, "HIGH"),
    (20, "MEDIUM"),
    (0, "LOW"),
]

RECOMMENDED_ACTION = {
    "CRITICAL": "Escalate to Tier 2/3 immediately -- begin containment (isolate host / block IP).",
    "HIGH": "Investigate as priority within this shift -- confirm scope before next tier escalation.",
    "MEDIUM": "Review in normal analyst queue -- confirm false-positive vs. benign vs. needs escalation.",
    "LOW": "Log for situational awareness -- no immediate action required.",
}

_alert_id_counter = itertools.count(1)


@dataclass
class Alert:
    alert_id: str
    src_ip: str
    first_seen: object       # datetime
    last_seen: object        # datetime
    sources: set
    severity_score: int
    severity_label: str
    rule_hits: list
    events: list
    recommended_action: str = field(default="")

    @property
    def duration_seconds(self) -> float:
        return (self.last_seen - self.first_seen).total_seconds()

    @property
    def summary(self) -> str:
        return "; ".join(hit.explanation for hit in self.rule_hits)


def _severity_label(score: int) -> str:
    for threshold, label in SEVERITY_BANDS:
        if score >= threshold:
            return label
    return "LOW"


def build_alert(group: list[LogEvent], rule_hits: list[RuleHit]) -> Alert:
    score = sum(hit.weight for hit in rule_hits)
    label = _severity_label(score)
    alert_id = f"ALT-{next(_alert_id_counter):04d}"
    return Alert(
        alert_id=alert_id,
        src_ip=group[0].src_ip,
        first_seen=group[0].ts,
        last_seen=group[-1].ts,
        sources={e.source for e in group},
        severity_score=score,
        severity_label=label,
        rule_hits=rule_hits,
        events=group,
        recommended_action=RECOMMENDED_ACTION[label],
    )


def triage(
    events: list[LogEvent],
    window: timedelta = timedelta(seconds=180),
) -> list[Alert]:
    """
    Full pipeline stage: group raw events by source IP + time window, run
    every correlation rule against each candidate group, and keep only the
    groups where at least one rule fired (i.e. something worth an alert).
    Returns alerts ranked by priority: severity score (desc), then whichever
    incident started earliest (ties broken by earliest-first so an analyst
    naturally works the longest-running issue first within a severity tier).
    """
    groups = group_events_by_ip_window(events, window=window)

    alerts: list[Alert] = []
    for group in groups:
        rule_hits = evaluate_group(group)
        if not rule_hits:
            continue  # correlated in time, but no rule found it suspicious
        alerts.append(build_alert(group, rule_hits))

    alerts.sort(key=lambda a: (-a.severity_score, a.first_seen))
    return alerts
