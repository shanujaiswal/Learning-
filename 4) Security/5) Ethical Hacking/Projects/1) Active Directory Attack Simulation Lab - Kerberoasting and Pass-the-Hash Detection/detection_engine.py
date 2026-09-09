"""
detection_engine.py

Two abstract detection rules modeled on real AD-attack detection
signatures (the kind of logic a SIEM / Microsoft Defender for Identity
would run against real Windows Security Event Log data):

  1. Kerberoasting detector
     Real signal: an abnormal volume of TGS-REQ (Event ID 4769)
     requests for SPN accounts, from one source account, in a short
     time window. A normal user requests a handful of service tickets
     a day, naturally spread out; requesting tickets for many distinct
     SPNs within seconds/minutes is not normal human behavior.

  2. Pass-the-Hash detector
     Real signal: an NTLM authentication (Event ID 4624,
     AuthenticationPackageName=NTLM) using a given account authenticating
     to an abnormally large number of DISTINCT target hosts within a
     short window -- a real lateral-movement indicator, since a
     legitimate user/service account rarely needs to log on to many
     different machines within seconds/minutes of each other.

Both detectors operate purely on the simulated event logs produced by
kerberoasting_sim.py and pass_the_hash_sim.py -- no real log source,
no real SIEM, no real network involved.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timedelta

from kerberoasting_sim import SimulatedTicketRequest
from pass_the_hash_sim import SimulatedNTLMAuthEvent


@dataclass
class DetectionAlert:
    rule_name: str
    severity: str
    source_account: str
    window_start: datetime
    window_end: datetime
    distinct_count: int
    threshold: int
    details: str


def detect_kerberoasting(
    ticket_requests: list[SimulatedTicketRequest],
    window: timedelta = timedelta(minutes=5),
    distinct_spn_threshold: int = 3,
) -> list[DetectionAlert]:
    """Flag a source account requesting an abnormal number of distinct
    SPN service tickets within a short sliding time window.

    Rule (mirrors a real SIEM correlation rule on Event ID 4769):
        COUNT(DISTINCT target_spn) BY requesting_account
        WITHIN <window>
        > distinct_spn_threshold  =>  ALERT

    A legitimate user might touch one or two services in a normal
    session; requesting tickets for many distinct SPNs within a few
    minutes is the classic Kerberoasting enumeration-and-request sweep.
    """
    alerts: list[DetectionAlert] = []
    if not ticket_requests:
        return alerts

    by_account: dict[str, list[SimulatedTicketRequest]] = {}
    for req in ticket_requests:
        by_account.setdefault(req.requesting_account, []).append(req)

    for account, requests in by_account.items():
        requests = sorted(requests, key=lambda r: r.timestamp)
        n = len(requests)
        # Sliding window: for each request, look forward and count
        # distinct SPNs requested by this account within `window`.
        for i in range(n):
            window_start = requests[i].timestamp
            window_end = window_start + window
            spns_in_window = {
                r.target_spn
                for r in requests[i:]
                if r.timestamp <= window_end
            }
            if len(spns_in_window) > distinct_spn_threshold:
                last_ts = max(
                    r.timestamp for r in requests[i:]
                    if r.timestamp <= window_end
                )
                alerts.append(
                    DetectionAlert(
                        rule_name="KERBEROASTING_ABNORMAL_TGS_VOLUME",
                        severity="HIGH",
                        source_account=account,
                        window_start=window_start,
                        window_end=last_ts,
                        distinct_count=len(spns_in_window),
                        threshold=distinct_spn_threshold,
                        details=(
                            f"Account '{account}' requested service tickets for "
                            f"{len(spns_in_window)} distinct SPNs within "
                            f"{(last_ts - window_start).total_seconds():.0f}s "
                            f"(threshold: >{distinct_spn_threshold}). "
                            "Real equivalent: Event ID 4769 burst -- classic "
                            "Kerberoasting enumeration sweep."
                        ),
                    )
                )
                break  # one alert per account is enough for this lab

    return alerts


def detect_pass_the_hash(
    auth_events: list[SimulatedNTLMAuthEvent],
    window: timedelta = timedelta(minutes=5),
    distinct_host_threshold: int = 3,
) -> list[DetectionAlert]:
    """Flag an account whose NTLM hash was used to authenticate to an
    abnormally large number of DISTINCT hosts within a short window.

    Rule (mirrors a real lateral-movement detection rule on Event ID
    4624 with AuthenticationPackageName=NTLM):
        COUNT(DISTINCT target_host) BY account
        WHERE auth_package == "NTLM"
        WITHIN <window>
        > distinct_host_threshold  =>  ALERT

    A real user/service account logging on to many different machines
    within minutes of each other -- always via NTLM, never Kerberos --
    is the textbook Pass-the-Hash lateral-movement signature
    (`crackmapexec`/`psexec.py -hashes` spraying one hash network-wide).
    """
    alerts: list[DetectionAlert] = []
    if not auth_events:
        return alerts

    ntlm_events = [e for e in auth_events if e.auth_package == "NTLM"]

    by_account: dict[str, list[SimulatedNTLMAuthEvent]] = {}
    for ev in ntlm_events:
        by_account.setdefault(ev.account, []).append(ev)

    for account, events in by_account.items():
        events = sorted(events, key=lambda e: e.timestamp)
        n = len(events)
        for i in range(n):
            window_start = events[i].timestamp
            window_end = window_start + window
            hosts_in_window = {
                e.target_host
                for e in events[i:]
                if e.timestamp <= window_end
            }
            if len(hosts_in_window) > distinct_host_threshold:
                last_ts = max(
                    e.timestamp for e in events[i:]
                    if e.timestamp <= window_end
                )
                alerts.append(
                    DetectionAlert(
                        rule_name="PASS_THE_HASH_LATERAL_MOVEMENT",
                        severity="CRITICAL",
                        source_account=account,
                        window_start=window_start,
                        window_end=last_ts,
                        distinct_count=len(hosts_in_window),
                        threshold=distinct_host_threshold,
                        details=(
                            f"Account '{account}' authenticated via NTLM to "
                            f"{len(hosts_in_window)} distinct hosts within "
                            f"{(last_ts - window_start).total_seconds():.0f}s "
                            f"(threshold: >{distinct_host_threshold}). Real "
                            "equivalent: one NTLM hash reused network-wide -- "
                            "classic Pass-the-Hash lateral movement."
                        ),
                    )
                )
                break  # one alert per account is enough for this lab

    return alerts
