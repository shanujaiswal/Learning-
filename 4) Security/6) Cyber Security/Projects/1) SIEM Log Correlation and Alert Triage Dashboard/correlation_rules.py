"""
correlation_rules.py
---------------------
Simple, explainable SIEM correlation rules -- no ML, no scoring model that
can't be explained in one sentence to an auditor. This is exactly the kind
of logic that lives behind "correlation searches" in Splunk Enterprise
Security or "analytics rules" in Microsoft Sentinel.

Two stages:
    1. Grouping  -- chain raw events from the SAME source IP together
       whenever consecutive events are within a sliding time window of
       each other (`group_events_by_ip_window`). This turns a flat stream
       of log lines into candidate "incident groups" worth evaluating.
    2. Rules     -- a handful of independent, human-readable detection
       rules that each look at one candidate group and report whether
       they fired, plus *why* (an explanation string used as evidence).

alert_triage_engine.py combines the rule hits per group into an Alert.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import timedelta

from log_sources import IDS_SIGNATURES, LogEvent

DEFAULT_GROUP_WINDOW = timedelta(seconds=180)
BRUTE_FORCE_THRESHOLD = 5
BRUTE_FORCE_WINDOW = timedelta(seconds=120)


@dataclass
class RuleHit:
    """One fired correlation rule, with the weight it contributes to severity."""
    rule_name: str
    weight: int
    explanation: str


# ---------------------------------------------------------------------------
# Stage 1: grouping -- same-source-IP-within-window chaining
# ---------------------------------------------------------------------------

def group_events_by_ip_window(
    events: list[LogEvent],
    window: timedelta = DEFAULT_GROUP_WINDOW,
) -> list[list[LogEvent]]:
    """
    Chains events sharing a source IP into groups: an event joins the
    current group for that IP if it falls within `window` of the group's
    most recent event for that IP. This is the same idea a SIEM uses to
    stitch a firewall block + an IDS hit + a failed login together into
    one "session" worth investigating, instead of three unrelated lines.
    """
    by_ip: dict[str, list[LogEvent]] = {}
    for e in events:
        by_ip.setdefault(e.src_ip, []).append(e)

    groups: list[list[LogEvent]] = []
    for ip, ip_events in by_ip.items():
        ip_events.sort(key=lambda e: e.ts)
        current: list[LogEvent] = [ip_events[0]]
        for e in ip_events[1:]:
            if e.ts - current[-1].ts <= window:
                current.append(e)
            else:
                groups.append(current)
                current = [e]
        groups.append(current)

    # Drop trivial singleton groups of pure background noise: a single
    # ALLOW/Accepted event with nothing else nearby is not worth an alert.
    meaningful_groups = [g for g in groups if _is_worth_evaluating(g)]
    meaningful_groups.sort(key=lambda g: g[0].ts)
    return meaningful_groups


def _is_worth_evaluating(group: list[LogEvent]) -> bool:
    if len(group) >= 2:
        return True
    only = group[0]
    if only.source == "firewall" and only.detail == "BLOCK":
        return True
    if only.source == "ids" and only.meta.get("severity") in ("HIGH", "MEDIUM"):
        return True
    if only.source == "auth" and only.meta.get("outcome") == "Failed":
        return True
    return False


# ---------------------------------------------------------------------------
# Stage 2: individual correlation / detection rules
# ---------------------------------------------------------------------------

def rule_signature_escalation(group: list[LogEvent]) -> RuleHit | None:
    """Escalate on any IDS event matching a known HIGH/MEDIUM severity signature."""
    hi = [e for e in group if e.source == "ids" and e.meta.get("severity") == "HIGH"]
    med = [e for e in group if e.source == "ids" and e.meta.get("severity") == "MEDIUM"]
    if hi:
        sigs = sorted({e.detail for e in hi})
        return RuleHit("signature_match_high", 35,
                        f"HIGH-severity IDS signature match: {', '.join(sigs)}")
    if med:
        sigs = sorted({e.detail for e in med})
        return RuleHit("signature_match_medium", 15,
                        f"MEDIUM-severity IDS signature match: {', '.join(sigs)}")
    return None


def rule_firewall_block_present(group: list[LogEvent]) -> RuleHit | None:
    """A firewall BLOCK in the group means the perimeter already flagged this source."""
    blocks = [e for e in group if e.source == "firewall" and e.detail == "BLOCK"]
    if not blocks:
        return None
    ports = sorted({e.raw.split("dport=")[1].split(" ")[0] for e in blocks})
    weight = 10 if len(blocks) < 3 else 20  # multiple blocked ports looks like a scan
    reason = "port-scan-like probing" if len(blocks) >= 3 else "blocked connection"
    return RuleHit("firewall_block_present", weight,
                    f"{len(blocks)} firewall BLOCK event(s) ({reason}), ports={','.join(ports)}")


def rule_auth_frequency_threshold(
    group: list[LogEvent],
    threshold: int = BRUTE_FORCE_THRESHOLD,
    window: timedelta = BRUTE_FORCE_WINDOW,
) -> RuleHit | None:
    """Classic brute-force rule: >= threshold failed auths within `window` seconds."""
    failed = [e for e in group if e.source == "auth" and e.meta.get("outcome") == "Failed"]
    failed.sort(key=lambda e: e.ts)
    left = 0
    for right in range(len(failed)):
        while failed[right].ts - failed[left].ts > window:
            left += 1
        window_hits = failed[left:right + 1]
        if len(window_hits) >= threshold:
            span = (window_hits[-1].ts - window_hits[0].ts).total_seconds()
            return RuleHit("auth_frequency_threshold", 25,
                            f"{len(window_hits)} failed logins within {span:.0f}s "
                            f"(threshold={threshold}/{window.total_seconds():.0f}s)")
    return None


def rule_auth_success_after_failures(group: list[LogEvent]) -> RuleHit | None:
    """
    The single most important line in a brute-force story: a Failed streak
    immediately followed by an Accepted from the same source IP -- the
    "someone is inside the house now" moment.
    """
    auth_events = [e for e in group if e.source == "auth"]
    auth_events.sort(key=lambda e: e.ts)
    seen_failed = False
    for e in auth_events:
        if e.meta.get("outcome") == "Failed":
            seen_failed = True
        elif e.meta.get("outcome") == "Accepted" and seen_failed:
            return RuleHit("auth_success_after_failures", 40,
                            f"Accepted login for '{e.meta.get('user')}' followed a failed-login "
                            f"streak from the same source IP -- likely compromise")
    return None


def rule_multi_source_correlation(group: list[LogEvent]) -> RuleHit | None:
    """Reward groups where independent sources corroborate each other on the same IP."""
    sources = {e.source for e in group}
    if len(sources) >= 3:
        return RuleHit("multi_source_correlation", 20,
                        f"Corroborated across all 3 sources: {', '.join(sorted(sources))}")
    if len(sources) == 2:
        return RuleHit("multi_source_correlation", 10,
                        f"Corroborated across 2 sources: {', '.join(sorted(sources))}")
    return None


ALL_RULES = [
    rule_signature_escalation,
    rule_firewall_block_present,
    rule_auth_frequency_threshold,
    rule_auth_success_after_failures,
    rule_multi_source_correlation,
]


def evaluate_group(group: list[LogEvent]) -> list[RuleHit]:
    """Runs every rule against one candidate group and returns the hits that fired."""
    hits: list[RuleHit] = []
    for rule in ALL_RULES:
        hit = rule(group)
        if hit is not None:
            hits.append(hit)
    return hits
