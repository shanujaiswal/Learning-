"""
entropy_analysis.py
--------------------
DNS tunneling detector: real-world equivalent of what Cisco Umbrella / Zeek's
dns.log analyzers / Palo Alto's DNS Security do to catch data exfiltration
and C2-over-DNS -- score subdomain labels by Shannon entropy and length,
then flag hosts that send an unusually large volume of high-scoring queries
to a single domain in a short time.

Why entropy + length together:
  - Legitimate subdomains ("www", "api-v2", "cdn-eu1") are short and made of
    dictionary-ish words -- low entropy, short length.
  - Base32/base64-encoded exfiltrated data crammed into a label looks like
    "hjks8zaqp1mzxlq93bdf..." -- long AND close to maximum entropy for its
    alphabet, because encoded/compressed/encrypted bytes are close to random.
  - Neither signal alone is reliable (a long English phrase has low entropy;
    a short random token has high entropy but little payload capacity), so
    tunneling detection combines both.

Volume matters too: a single high-entropy label could be a CDN hash or a
DKIM/TXT record check -- normal DNS has some of these. What normal DNS does
NOT have is one host firing dozens of them at the same domain in seconds,
which is exactly the shape a beaconing tunneling client takes.
"""

import math
from collections import defaultdict

# A label at or above this length is "long enough to carry a meaningful
# chunk of exfiltrated data per query".
LABEL_LENGTH_THRESHOLD = 30

# Shannon entropy (bits/char) at or above this is "close to random" for a
# lowercase-alphanumeric alphabet (max possible is log2(36) ~= 5.17).
ENTROPY_THRESHOLD = 3.6

# A host must send at least this many entropy-flagged queries to ONE domain
# within the volume window to be escalated from "suspicious label" to a
# full tunneling alert.
VOLUME_THRESHOLD = 8
VOLUME_WINDOW_SECONDS = 30.0


def shannon_entropy(text: str) -> float:
    """Shannon entropy in bits per character. Higher = more random-looking."""
    if not text:
        return 0.0
    counts = defaultdict(int)
    for ch in text:
        counts[ch] += 1
    n = len(text)
    entropy = 0.0
    for c in counts.values():
        p = c / n
        entropy -= p * math.log2(p)
    return entropy


def split_query(query: str):
    """Split 'abc123def.datax-relay.net' into ('abc123def', 'datax-relay.net')."""
    parts = query.split(".")
    if len(parts) <= 2:
        return "", query
    subdomain_label = parts[0]
    registered_domain = ".".join(parts[-2:])
    return subdomain_label, registered_domain


def score_label(label: str) -> dict:
    """Score a single subdomain label for tunneling-candidate characteristics."""
    entropy = shannon_entropy(label)
    length = len(label)
    is_long = length >= LABEL_LENGTH_THRESHOLD
    is_high_entropy = entropy >= ENTROPY_THRESHOLD
    return {
        "label": label, "length": length, "entropy": round(entropy, 3),
        "is_long": is_long, "is_high_entropy": is_high_entropy,
        "suspicious": is_long and is_high_entropy,
    }


def analyze_queries(records: list) -> dict:
    """
    Score every query label, then group suspicious ones by (host, domain)
    and escalate any pair that clears the volume threshold within the
    volume window into a confirmed tunneling alert.

    Returns:
        {
          "scored": [ {record fields..., "entropy": .., "suspicious": ..}, ... ],
          "alerts": [ {"src_ip", "domain", "count", "window_start",
                       "window_end", "sample_labels"}, ... ]
        }
    """
    scored = []
    # (host, domain) -> list of (timestamp, label)
    suspicious_by_pair = defaultdict(list)

    for r in records:
        if r["is_response"]:
            continue
        label, domain = split_query(r["query"])
        label_score = score_label(label)
        entry = dict(r)
        entry.update(label_score)
        entry["domain"] = domain
        scored.append(entry)

        if label_score["suspicious"]:
            suspicious_by_pair[(r["src_ip"], domain)].append((r["timestamp"], label))

    alerts = []
    for (host, domain), hits in suspicious_by_pair.items():
        hits.sort()
        # Slide a window over the sorted hit timestamps looking for a burst
        # that clears VOLUME_THRESHOLD within VOLUME_WINDOW_SECONDS.
        start_idx = 0
        for end_idx in range(len(hits)):
            while hits[end_idx][0] - hits[start_idx][0] > VOLUME_WINDOW_SECONDS:
                start_idx += 1
            window_count = end_idx - start_idx + 1
            if window_count >= VOLUME_THRESHOLD:
                alerts.append({
                    "src_ip": host, "domain": domain, "count": window_count,
                    "window_start": hits[start_idx][0], "window_end": hits[end_idx][0],
                    "sample_labels": [lbl for _, lbl in hits[start_idx:start_idx + 3]],
                })
                break  # one alert per (host, domain) pair is enough

    return {"scored": scored, "alerts": alerts}


if __name__ == "__main__":
    from dns_log_generator import generate_log

    log = generate_log()
    result = analyze_queries(log)
    print(f"Scored {len(result['scored'])} queries")
    suspicious_count = sum(1 for s in result["scored"] if s["suspicious"])
    print(f"Suspicious (long + high-entropy) labels: {suspicious_count}")
    print(f"\nTunneling alerts: {len(result['alerts'])}")
    for a in result["alerts"]:
        print(f"  host={a['src_ip']} domain={a['domain']} "
              f"count={a['count']} window=[{a['window_start']:.1f}, {a['window_end']:.1f}]s")
        print(f"    sample labels: {a['sample_labels']}")
