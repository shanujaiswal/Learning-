"""
dns_log_generator.py
---------------------
Maps to Theory chapter: "Routing, DNS, HTTP" (DNS section, and the note on
DNS spoofing/cache poisoning + DNS tunneling as attack surfaces).

Generates a fully synthetic, time-ordered DNS query log -- no real network
traffic, no real DNS resolution. Everything downstream (entropy_analysis.py,
spoofing_detector.py, typosquat_detector.py, main.py) consumes the list of
dict records this module produces.

Each record looks like:
    {
        "timestamp": float,          # seconds since start of capture
        "src_ip": str,                # client that issued the query
        "query": str,                 # full queried name, e.g. "www.example.com"
        "qtype": str,                 # "A", "AAAA", "TXT", ...
        "txn_id": int,                # DNS transaction ID (0-65535)
        "is_response": bool,          # False = query, True = a response record
        "response_ip": str | None,    # answer IP, only set on responses
    }

The log is built out of four blocks, concatenated and then time-sorted so an
analyst sees them interleaved exactly like a real capture would look:

  1. Normal background traffic       -- everyday browsing, should NEVER alert.
  2. An injected tunneling pattern    -- one host hammering one attacker
                                         domain with long, high-entropy
                                         subdomain labels (classic data
                                         exfiltration / C2-over-DNS shape).
  3. An injected spoofing burst       -- a flood of "responses" for one
                                         legitimate-looking query, with
                                         mismatched transaction IDs and
                                         inconsistent answer IPs (a forged
                                         cache-poisoning attempt).
  4. A handful of typosquat lookups   -- lookalike domains such as
                                         "paypa1.com" / "gooogle.com".

A fixed random seed makes the log fully reproducible.
"""

import random
import string

SEED = 1337

NORMAL_DOMAINS = [
    "google.com", "youtube.com", "wikipedia.org", "github.com",
    "stackoverflow.com", "reddit.com", "amazon.com", "microsoft.com",
    "apple.com", "netflix.com", "office.com", "linkedin.com",
    "python.org", "cloudflare.com", "nytimes.com",
]

NORMAL_SUBDOMAINS = ["www", "mail", "cdn", "api", "static", "img", "login", ""]

NORMAL_HOSTS = [f"10.0.0.{i}" for i in range(2, 20)]

# Attacker-controlled host + domain used for the tunneling injection.
TUNNEL_HOST = "10.0.0.77"
TUNNEL_DOMAIN = "datax-relay.net"

# Host + spoofed query used for the cache-poisoning / spoofing injection.
SPOOF_VICTIM_HOST = "10.0.0.13"
SPOOF_TARGET_QUERY = "bank-secure-login.com"
SPOOF_LEGITIMATE_IP = "203.0.113.10"

# Known-brand domains being impersonated by the typosquat injection.
TYPOSQUAT_QUERIES = [
    ("paypa1.com", "10.0.0.9"),
    ("gooogle.com", "10.0.0.4"),
    ("micosoft.com", "10.0.0.11"),
    ("netfliix.com", "10.0.0.6"),
    ("linkedln.com", "10.0.0.15"),
]


def _random_label(rng: random.Random, length: int) -> str:
    """A high-entropy alphanumeric label, the shape base32/base64-encoded
    exfiltrated data takes once it's stuffed into a DNS subdomain label."""
    alphabet = string.ascii_lowercase + string.digits
    return "".join(rng.choice(alphabet) for _ in range(length))


def _gen_normal_traffic(rng: random.Random, count: int, t_start: float, t_end: float):
    """Everyday DNS queries + their legitimate responses. This traffic must
    never trip any detector -- it's the false-positive control group."""
    records = []
    for _ in range(count):
        t = rng.uniform(t_start, t_end)
        host = rng.choice(NORMAL_HOSTS)
        domain = rng.choice(NORMAL_DOMAINS)
        sub = rng.choice(NORMAL_SUBDOMAINS)
        query = f"{sub}.{domain}" if sub else domain
        txn_id = rng.randint(0, 65535)

        records.append({
            "timestamp": round(t, 3), "src_ip": host, "query": query,
            "qtype": "A", "txn_id": txn_id, "is_response": False,
            "response_ip": None,
        })
        # A well-behaved response echoes back the *same* transaction ID.
        records.append({
            "timestamp": round(t + rng.uniform(0.01, 0.05), 3), "src_ip": host,
            "query": query, "qtype": "A", "txn_id": txn_id, "is_response": True,
            "response_ip": f"93.184.{rng.randint(0, 255)}.{rng.randint(1, 254)}",
        })
    return records


def _gen_tunneling_traffic(rng: random.Random, t_start: float):
    """One infected host beaconing to one attacker domain, encoding data in
    long, high-entropy subdomain labels -- and doing it a LOT in a short
    window (high query volume, single host -> single domain)."""
    records = []
    t = t_start
    for _ in range(90):
        label = _random_label(rng, rng.randint(32, 55))
        query = f"{label}.{TUNNEL_DOMAIN}"
        txn_id = rng.randint(0, 65535)
        records.append({
            "timestamp": round(t, 3), "src_ip": TUNNEL_HOST, "query": query,
            "qtype": "TXT", "txn_id": txn_id, "is_response": False,
            "response_ip": None,
        })
        t += rng.uniform(0.2, 0.6)  # rapid-fire beaconing
    return records


def _gen_spoofing_traffic(rng: random.Random, t_start: float):
    """A burst of forged 'responses' for one query, arriving with mismatched
    transaction IDs (the attacker is racing the real resolver, guessing IDs)
    and inconsistent answer IPs -- the classic Kaminsky-style cache-poisoning
    shape: many attempts, one victim query, wrong IDs, conflicting answers."""
    records = []
    t = t_start

    # The real query the victim host actually asked.
    real_txn_id = rng.randint(0, 65535)
    records.append({
        "timestamp": round(t, 3), "src_ip": SPOOF_VICTIM_HOST,
        "query": SPOOF_TARGET_QUERY, "qtype": "A", "txn_id": real_txn_id,
        "is_response": False, "response_ip": None,
    })
    t += 0.01

    # A flood of forged responses guessing at the transaction ID, each
    # claiming a different (fake) answer IP -- none of them match the ID
    # the victim actually used, and they disagree with each other.
    for _ in range(25):
        forged_txn_id = rng.randint(0, 65535)
        while forged_txn_id == real_txn_id:
            forged_txn_id = rng.randint(0, 65535)
        fake_ip = f"198.51.100.{rng.randint(1, 254)}"
        records.append({
            "timestamp": round(t, 3), "src_ip": SPOOF_VICTIM_HOST,
            "query": SPOOF_TARGET_QUERY, "qtype": "A", "txn_id": forged_txn_id,
            "is_response": True, "response_ip": fake_ip,
        })
        t += rng.uniform(0.002, 0.01)  # forged responses arrive in a tight burst

    # The legitimate answer, arriving late (as often happens when an
    # attacker is racing the real authoritative server).
    records.append({
        "timestamp": round(t, 3), "src_ip": SPOOF_VICTIM_HOST,
        "query": SPOOF_TARGET_QUERY, "qtype": "A", "txn_id": real_txn_id,
        "is_response": True, "response_ip": SPOOF_LEGITIMATE_IP,
    })
    return records


def _gen_typosquat_traffic(rng: random.Random, t_start: float, t_end: float):
    """A handful of one-off lookups against lookalike/brand-impersonating
    domains, scattered through the capture like a user who mistyped a URL
    or clicked a phishing link."""
    records = []
    for query, host in TYPOSQUAT_QUERIES:
        t = rng.uniform(t_start, t_end)
        txn_id = rng.randint(0, 65535)
        records.append({
            "timestamp": round(t, 3), "src_ip": host, "query": query,
            "qtype": "A", "txn_id": txn_id, "is_response": False,
            "response_ip": None,
        })
    return records


def generate_log(seed: int = SEED):
    """Build the full synthetic capture and return it time-sorted."""
    rng = random.Random(seed)

    capture_length = 120.0  # seconds

    normal = _gen_normal_traffic(rng, count=220, t_start=0.0, t_end=capture_length)
    tunneling = _gen_tunneling_traffic(rng, t_start=40.0)
    spoofing = _gen_spoofing_traffic(rng, t_start=75.0)
    typosquat = _gen_typosquat_traffic(rng, t_start=0.0, t_end=capture_length)

    all_records = normal + tunneling + spoofing + typosquat
    all_records.sort(key=lambda r: r["timestamp"])
    return all_records


def log_summary(records):
    total = len(records)
    queries = sum(1 for r in records if not r["is_response"])
    responses = total - queries
    hosts = len({r["src_ip"] for r in records})
    domains = len({r["query"] for r in records})
    return {
        "total_records": total, "queries": queries, "responses": responses,
        "unique_hosts": hosts, "unique_query_names": domains,
    }


if __name__ == "__main__":
    log = generate_log()
    summary = log_summary(log)
    print(f"Generated {summary['total_records']} DNS records "
          f"({summary['queries']} queries, {summary['responses']} responses)")
    print(f"Unique hosts: {summary['unique_hosts']}, "
          f"unique query names: {summary['unique_query_names']}")
    print("\nFirst 5 records:")
    for r in log[:5]:
        print(f"  t={r['timestamp']:7.3f}  {r['src_ip']:12}  "
              f"{'RESP' if r['is_response'] else 'QRY '}  {r['query']}")
