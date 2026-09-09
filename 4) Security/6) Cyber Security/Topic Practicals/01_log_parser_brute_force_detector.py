"""
01 - Log Parser & Brute-Force Detector
Chapter: 05 Incident Response, SIEM and Blue Team Basics

WHAT THIS DEMONSTRATES
-----------------------
Real SIEM tools (Splunk, ELK, Wazuh, Sentinel...) all boil down to the same
core loop at the "detection engineering" level:

    1. Collect log lines from a source (auth logs, firewall logs, etc.)
    2. Parse each line into a structured event (timestamp, source IP, outcome)
    3. Correlate events using a detection rule (e.g. "N+ failed logins from
       the same IP within a sliding time window")
    4. Raise an alert with the supporting evidence

This script:
    - Generates a small synthetic SSH-style authentication log file
      (normal logins scattered over time + a burst of failed attempts
      from a single attacker IP -- a classic brute-force pattern).
    - Parses the log with a regex.
    - Runs a sliding-time-window correlation rule over the parsed events.
    - Prints a SIEM-style alert when the rule fires, including the
      evidence (matching log lines) that triggered it.

Run:
    python 01_log_parser_brute_force_detector.py
"""

from __future__ import annotations

import random
import re
from collections import defaultdict
from dataclasses import dataclass
from datetime import datetime, timedelta

LOG_FILE = "synthetic_auth.log"

# ---------------------------------------------------------------------------
# Step 1: Generate a synthetic authentication log
# ---------------------------------------------------------------------------

LOG_LINE_FMT = "{ts} sshd[{pid}]: {outcome} password for {user} from {ip} port {port} ssh2"

NORMAL_USERS = ["alice", "bob", "carol", "dave"]
NORMAL_IPS = ["10.0.0.11", "10.0.0.12", "10.0.0.14", "192.168.1.20"]

ATTACKER_IP = "203.0.113.77"
ATTACKER_USERS_TRIED = ["root", "admin", "root", "test", "root", "ubuntu", "root", "oracle", "root", "postgres"]


def generate_synthetic_log(path: str, seed: int = 42) -> None:
    """Writes a synthetic auth log: normal traffic + a brute-force burst."""
    random.seed(seed)
    start = datetime(2026, 8, 10, 9, 0, 0)
    lines: list[tuple[datetime, str]] = []

    # Normal, well-spaced successful logins from legitimate users/IPs.
    t = start
    for _ in range(15):
        t += timedelta(minutes=random.randint(2, 20))
        user = random.choice(NORMAL_USERS)
        ip = random.choice(NORMAL_IPS)
        outcome = "Accepted" if random.random() > 0.1 else "Failed"
        pid = random.randint(1000, 9000)
        lines.append((t, LOG_LINE_FMT.format(
            ts=t.strftime("%Y-%m-%d %H:%M:%S"), pid=pid, outcome=outcome,
            user=user, ip=ip, port=random.randint(40000, 60000))))

    # Brute-force burst: many failed attempts from ATTACKER_IP within ~90 seconds.
    burst_start = start + timedelta(minutes=37)
    bt = burst_start
    for user in ATTACKER_USERS_TRIED:
        bt += timedelta(seconds=random.randint(5, 12))
        pid = random.randint(1000, 9000)
        lines.append((bt, LOG_LINE_FMT.format(
            ts=bt.strftime("%Y-%m-%d %H:%M:%S"), pid=pid, outcome="Failed",
            user=user, ip=ATTACKER_IP, port=random.randint(40000, 60000))))

    # A couple more normal lines after the burst.
    t = bt
    for _ in range(4):
        t += timedelta(minutes=random.randint(2, 15))
        user = random.choice(NORMAL_USERS)
        ip = random.choice(NORMAL_IPS)
        pid = random.randint(1000, 9000)
        lines.append((t, LOG_LINE_FMT.format(
            ts=t.strftime("%Y-%m-%d %H:%M:%S"), pid=pid, outcome="Accepted",
            user=user, ip=ip, port=random.randint(40000, 60000))))

    lines.sort(key=lambda pair: pair[0])
    with open(path, "w", encoding="utf-8") as f:
        for _, line in lines:
            f.write(line + "\n")


# ---------------------------------------------------------------------------
# Step 2: Parse the log into structured events
# ---------------------------------------------------------------------------

LOG_REGEX = re.compile(
    r"^(?P<ts>\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}) "
    r"sshd\[(?P<pid>\d+)\]: (?P<outcome>Accepted|Failed) password "
    r"for (?P<user>\S+) from (?P<ip>\S+) port (?P<port>\d+) ssh2$"
)


@dataclass
class AuthEvent:
    ts: datetime
    outcome: str
    user: str
    ip: str
    port: int
    raw: str


def parse_log(path: str) -> list[AuthEvent]:
    events: list[AuthEvent] = []
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            match = LOG_REGEX.match(line)
            if not match:
                continue
            events.append(AuthEvent(
                ts=datetime.strptime(match["ts"], "%Y-%m-%d %H:%M:%S"),
                outcome=match["outcome"],
                user=match["user"],
                ip=match["ip"],
                port=int(match["port"]),
                raw=line,
            ))
    return events


# ---------------------------------------------------------------------------
# Step 3: Detection rule - sliding time window brute-force correlation
# ---------------------------------------------------------------------------

def detect_brute_force(
    events: list[AuthEvent],
    *,
    threshold: int = 5,
    window: timedelta = timedelta(seconds=120),
) -> dict[str, list[AuthEvent]]:
    """
    Classic SIEM correlation rule:
    "Alert if the same source IP has >= `threshold` FAILED auth events
     within any `window`-sized sliding window."

    Returns a mapping of {ip: [events that make up the winning window]}
    for every IP that triggers the rule.
    """
    failed_by_ip: dict[str, list[AuthEvent]] = defaultdict(list)
    for event in events:
        if event.outcome == "Failed":
            failed_by_ip[event.ip].append(event)

    alerts: dict[str, list[AuthEvent]] = {}
    for ip, ip_events in failed_by_ip.items():
        ip_events.sort(key=lambda e: e.ts)
        # Sliding window using two pointers over the sorted failed events.
        left = 0
        for right in range(len(ip_events)):
            while ip_events[right].ts - ip_events[left].ts > window:
                left += 1
            window_events = ip_events[left:right + 1]
            if len(window_events) >= threshold:
                alerts[ip] = window_events
                break  # one alert per IP is enough for this demo
    return alerts


# ---------------------------------------------------------------------------
# Step 4: Tie it all together
# ---------------------------------------------------------------------------

def main() -> None:
    print(f"[*] Generating synthetic auth log -> {LOG_FILE}")
    generate_synthetic_log(LOG_FILE)

    print(f"[*] Parsing log file: {LOG_FILE}")
    events = parse_log(LOG_FILE)
    print(f"[*] Parsed {len(events)} authentication events "
          f"({sum(1 for e in events if e.outcome == 'Failed')} failed, "
          f"{sum(1 for e in events if e.outcome == 'Accepted')} accepted)")

    print("[*] Running brute-force correlation rule "
          "(>=5 failed logins from same IP within 120s)...\n")
    alerts = detect_brute_force(events, threshold=5, window=timedelta(seconds=120))

    if not alerts:
        print("[OK] No brute-force pattern detected.")
        return

    for ip, window_events in alerts.items():
        span = (window_events[-1].ts - window_events[0].ts).total_seconds()
        print("=" * 70)
        print("[ALERT] Possible brute-force attack detected")
        print(f"  Source IP        : {ip}")
        print(f"  Failed attempts  : {len(window_events)}")
        print(f"  Time window      : {span:.0f} seconds")
        print(f"  First attempt    : {window_events[0].ts}")
        print(f"  Last attempt     : {window_events[-1].ts}")
        usernames = {e.user for e in window_events}
        print(f"  Usernames tried  : {', '.join(sorted(usernames))}")
        print("  Evidence (raw log lines):")
        for e in window_events:
            print(f"    {e.raw}")
        print("=" * 70)


if __name__ == "__main__":
    main()
