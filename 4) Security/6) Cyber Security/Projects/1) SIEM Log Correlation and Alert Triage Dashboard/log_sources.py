"""
log_sources.py
---------------
Generates synthetic multi-source SIEM log data for one analyst shift.

Real-world equivalent: this stands in for the raw feeds a SIEM (Splunk,
Sentinel, QRadar...) ingests from a firewall, an IDS/IPS sensor, and an
authentication server. In a real SOC these would arrive as syslog / CEF /
JSON events pushed into the SIEM's indexer; here we synthesize the same
shape of data with a fixed random seed so the pipeline is reproducible.

Design:
    - A single shared clock ("shift") from 08:00:00 across ~4 hours.
    - Background noise: routine firewall allows, occasional low-severity
      IDS hits, and normal successful logins -- the kind of traffic that
      should NOT trigger an alert.
    - A handful of coherent, multi-source INCIDENTS seeded on purpose:
      the same source IP producing a firewall block, an IDS signature
      hit, and/or an authentication burst close together in time, so
      correlation_rules.py has real patterns to find.

No ML, no external data -- everything here is deterministic given SEED.
"""

from __future__ import annotations

import random
from dataclasses import dataclass, field
from datetime import datetime, timedelta

SEED = 1337
SHIFT_START = datetime(2026, 8, 18, 8, 0, 0)

# Known "bad" IDS signatures used for signature-match escalation.
# (severity: HIGH fires a strong escalation, MEDIUM a moderate one)
IDS_SIGNATURES = {
    "ET SCAN Possible Nmap User-Agent Detected": "MEDIUM",
    "ET SCAN Nmap Scripting Engine User-Agent Detected": "MEDIUM",
    "ET WEB_SERVER SQL Injection Attempt": "HIGH",
    "ET WEB_SERVER Possible SQL Injection UNION SELECT": "HIGH",
    "ET TROJAN Possible C2 Beacon Detected": "HIGH",
    "ET POLICY Suspicious Outbound to Known Bad IP": "HIGH",
    "ET SCAN Suspicious inbound to mySQL port 3306": "MEDIUM",
    "ET INFO Executable Download from Suspicious TLD": "MEDIUM",
}
LOW_SEV_SIGNATURES = {
    "ET INFO Common Toolkit User-Agent": "LOW",
    "ET POLICY Outbound DNS to Public Resolver": "LOW",
}

NORMAL_INTERNAL_IPS = ["10.0.0.11", "10.0.0.12", "10.0.0.14", "10.0.0.21", "10.0.0.33"]
NORMAL_EXTERNAL_IPS = ["198.51.100.9", "198.51.100.14", "198.51.100.21", "203.0.113.5"]
NORMAL_USERS = ["alice", "bob", "carol", "dave", "erin"]


@dataclass
class LogEvent:
    """A single structured log line, tagged with its originating source."""
    ts: datetime
    source: str            # "firewall" | "ids" | "auth"
    src_ip: str
    dst_ip: str
    detail: str            # short machine-usable summary (action/signature/outcome)
    raw: str                # the raw log line, as an analyst would see it in the SIEM
    meta: dict = field(default_factory=dict)


def _fmt(ts: datetime) -> str:
    return ts.strftime("%Y-%m-%d %H:%M:%S")


def _firewall_line(ts, action, src_ip, dst_ip, dport, proto="TCP") -> str:
    return (f"{_fmt(ts)} FIREWALL {action} src={src_ip} dst={dst_ip} "
            f"dport={dport} proto={proto}")


def _ids_line(ts, sig, src_ip, dst_ip, sid) -> str:
    return f'{_fmt(ts)} IDS ALERT sig="{sig}" src={src_ip} dst={dst_ip} sid={sid}'


def _auth_line(ts, outcome, user, src_ip, port) -> str:
    return (f"{_fmt(ts)} AUTH sshd[{random.randint(1000, 9999)}]: {outcome} password "
            f"for {user} from {src_ip} port {port} ssh2")


# ---------------------------------------------------------------------------
# Background noise generators
# ---------------------------------------------------------------------------

def _generate_background_noise(rng: random.Random, end: datetime) -> list[LogEvent]:
    events: list[LogEvent] = []
    t = SHIFT_START

    while t < end:
        t += timedelta(seconds=rng.randint(20, 90))
        if t >= end:
            break
        kind = rng.choices(["firewall", "ids", "auth"], weights=[0.55, 0.10, 0.35])[0]

        if kind == "firewall":
            src = rng.choice(NORMAL_INTERNAL_IPS + NORMAL_EXTERNAL_IPS)
            dst = rng.choice(NORMAL_INTERNAL_IPS)
            dport = rng.choice([80, 443, 22, 3389, 8080])
            events.append(LogEvent(
                t, "firewall", src, dst, "ALLOW",
                _firewall_line(t, "ALLOW", src, dst, dport)))

        elif kind == "ids":
            sig = rng.choice(list(LOW_SEV_SIGNATURES.keys()))
            src = rng.choice(NORMAL_INTERNAL_IPS + NORMAL_EXTERNAL_IPS)
            dst = rng.choice(NORMAL_INTERNAL_IPS)
            events.append(LogEvent(
                t, "ids", src, dst, sig,
                _ids_line(t, sig, src, dst, rng.randint(2000000, 2000999)),
                meta={"severity": "LOW"}))

        else:  # auth
            user = rng.choice(NORMAL_USERS)
            src = rng.choice(NORMAL_INTERNAL_IPS)
            outcome = "Accepted" if rng.random() > 0.08 else "Failed"
            port = rng.randint(40000, 60000)
            events.append(LogEvent(
                t, "auth", src, "10.0.0.5", outcome,
                _auth_line(t, outcome, user, src, port),
                meta={"outcome": outcome, "user": user}))

    return events


# ---------------------------------------------------------------------------
# Coherent, correlated incidents (the "signal" the pipeline should catch)
# ---------------------------------------------------------------------------

def _incident_scan_then_bruteforce_then_success(rng: random.Random, start: datetime) -> list[LogEvent]:
    """
    Attacker recon -> brute force -> compromise, all from one external IP:
      1. Nmap-style port scan (IDS signature hit + several firewall blocks)
      2. A burst of failed SSH logins against the same target
      3. One successful login -- the "someone is inside the house" moment
    """
    ip, dst = "203.0.113.77", "10.0.0.5"
    events: list[LogEvent] = []
    t = start

    # Recon: a few blocked probe ports + an IDS nmap signature.
    for dport in (21, 23, 445, 3306, 8443):
        t += timedelta(seconds=rng.randint(1, 4))
        events.append(LogEvent(t, "firewall", ip, dst, "BLOCK",
                                _firewall_line(t, "BLOCK", ip, dst, dport)))
    t += timedelta(seconds=2)
    sig = "ET SCAN Possible Nmap User-Agent Detected"
    events.append(LogEvent(t, "ids", ip, dst, sig,
                            _ids_line(t, sig, ip, dst, 2001219),
                            meta={"severity": IDS_SIGNATURES[sig]}))

    # Brute-force burst: several failed SSH attempts within ~60s.
    t += timedelta(seconds=rng.randint(10, 20))
    for user in ["root", "admin", "root", "test", "root", "oracle"]:
        t += timedelta(seconds=rng.randint(5, 10))
        events.append(LogEvent(t, "auth", ip, dst, "Failed",
                                _auth_line(t, "Failed", user, ip, rng.randint(40000, 60000)),
                                meta={"outcome": "Failed", "user": user}))

    # Compromise: one Accepted login from the same IP shortly after.
    t += timedelta(seconds=rng.randint(4, 9))
    events.append(LogEvent(t, "auth", ip, dst, "Accepted",
                            _auth_line(t, "Accepted", "root", ip, rng.randint(40000, 60000)),
                            meta={"outcome": "Accepted", "user": "root"}))
    return events


def _incident_web_sql_injection(rng: random.Random, start: datetime) -> list[LogEvent]:
    """A web attacker: SQL-injection IDS hit backed by a firewall block, no auth involved."""
    ip, dst = "198.51.100.66", "10.0.0.21"
    events: list[LogEvent] = []
    t = start

    sig = "ET WEB_SERVER SQL Injection Attempt"
    events.append(LogEvent(t, "ids", ip, dst, sig,
                            _ids_line(t, sig, ip, dst, 2010937),
                            meta={"severity": IDS_SIGNATURES[sig]}))
    t += timedelta(seconds=rng.randint(2, 6))
    sig2 = "ET WEB_SERVER Possible SQL Injection UNION SELECT"
    events.append(LogEvent(t, "ids", ip, dst, sig2,
                            _ids_line(t, sig2, ip, dst, 2010938),
                            meta={"severity": IDS_SIGNATURES[sig2]}))
    t += timedelta(seconds=rng.randint(1, 3))
    events.append(LogEvent(t, "firewall", ip, dst, "BLOCK",
                            _firewall_line(t, "BLOCK", ip, dst, 443)))
    return events


def _incident_c2_beacon(rng: random.Random, start: datetime) -> list[LogEvent]:
    """An already-infected internal host beaconing out to a known-bad external IP."""
    internal_ip, bad_ip = "10.0.0.44", "45.33.12.9"
    events: list[LogEvent] = []
    t = start

    for i in range(4):
        events.append(LogEvent(t, "firewall", internal_ip, bad_ip, "ALLOW",
                                _firewall_line(t, "ALLOW", internal_ip, bad_ip, 443)))
        t += timedelta(seconds=rng.randint(15, 25))

    sig = "ET TROJAN Possible C2 Beacon Detected"
    events.append(LogEvent(t, "ids", internal_ip, bad_ip, sig,
                            _ids_line(t, sig, internal_ip, bad_ip, 2027101),
                            meta={"severity": IDS_SIGNATURES[sig]}))
    t += timedelta(seconds=rng.randint(3, 8))
    sig2 = "ET POLICY Suspicious Outbound to Known Bad IP"
    events.append(LogEvent(t, "ids", internal_ip, bad_ip, sig2,
                            _ids_line(t, sig2, internal_ip, bad_ip, 2027102),
                            meta={"severity": IDS_SIGNATURES[sig2]}))
    return events


def _incident_bruteforce_only_no_success(rng: random.Random, start: datetime) -> list[LogEvent]:
    """A brute-force burst that never succeeds -- still frequency-threshold alertable."""
    ip, dst = "203.0.113.201", "10.0.0.5"
    events: list[LogEvent] = []
    t = start
    for user in ["admin", "root", "admin", "postgres", "admin", "ubuntu", "admin"]:
        t += timedelta(seconds=rng.randint(4, 9))
        events.append(LogEvent(t, "auth", ip, dst, "Failed",
                                _auth_line(t, "Failed", user, ip, rng.randint(40000, 60000)),
                                meta={"outcome": "Failed", "user": user}))
    return events


def generate_shift_logs(seed: int = SEED, shift_hours: int = 4) -> list[LogEvent]:
    """
    Builds one full shift of synthetic multi-source logs: background noise
    plus four seeded, coherent incidents. Returns events sorted by time.
    """
    rng = random.Random(seed)
    end = SHIFT_START + timedelta(hours=shift_hours)

    events = _generate_background_noise(rng, end)

    incident_offsets_minutes = [35, 95, 150, 205]
    incident_builders = [
        _incident_scan_then_bruteforce_then_success,
        _incident_web_sql_injection,
        _incident_c2_beacon,
        _incident_bruteforce_only_no_success,
    ]
    for offset, builder in zip(incident_offsets_minutes, incident_builders):
        incident_start = SHIFT_START + timedelta(minutes=offset)
        events.extend(builder(rng, incident_start))

    events.sort(key=lambda e: e.ts)
    return events


if __name__ == "__main__":
    evts = generate_shift_logs()
    print(f"Generated {len(evts)} log events across a {4}-hour shift:")
    by_source: dict[str, int] = {}
    for e in evts:
        by_source[e.source] = by_source.get(e.source, 0) + 1
    for src, count in sorted(by_source.items()):
        print(f"  {src:9s}: {count}")
