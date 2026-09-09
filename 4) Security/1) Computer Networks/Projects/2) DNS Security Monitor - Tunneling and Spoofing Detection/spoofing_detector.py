"""
spoofing_detector.py
---------------------
DNS spoofing / cache-poisoning detector: real-world equivalent of what a
resolver's anti-spoofing checks (0x20 encoding, source-port randomization
validation) and IDS signatures (Suricata's dns_event.rrname / txid checks)
do -- watch for bursts of responses to the same query name where the
transaction IDs don't line up, which is the signature of an attacker
racing the legitimate authoritative server with guessed IDs (the classic
Kaminsky attack shape).

Detection logic:
  1. Group response records by the query name they answer for.
  2. Within a short time window, if there are multiple responses for the
     SAME query name but with DIFFERENT transaction IDs (i.e. they can't
     all be legitimate answers to the one query the client actually sent),
     that's a spoofing burst.
  3. Disagreeing answer IPs for the same name in that same burst is a
     second, corroborating signal (forged responses rarely agree with each
     other on the "correct" answer).

Normal DNS traffic never produces this shape: a resolver asks once, gets
one response carrying the transaction ID it chose, done.
"""

from collections import defaultdict

# How many responses for one query name, arriving within the window below,
# is considered a "burst" worth inspecting for ID mismatches.
BURST_MIN_RESPONSES = 5
BURST_WINDOW_SECONDS = 2.0

# Within a flagged burst, this fraction (or more) of responses must carry a
# transaction ID that never matches the query's real ID to call it spoofing.
MISMATCH_RATIO_THRESHOLD = 0.5


def _find_bursts(responses_for_query: list) -> list:
    """Cluster sorted responses to a single query name into contiguous
    episodes -- consecutive responses no more than BURST_WINDOW_SECONDS
    apart belong to the same episode -- then keep episodes that meet the
    minimum response count. One episode = one real-world attack burst, so
    this reports exactly one alert per burst instead of one per sliding
    sub-window."""
    responses_for_query.sort(key=lambda r: r["timestamp"])
    episodes = []
    current = []
    for r in responses_for_query:
        if current and r["timestamp"] - current[-1]["timestamp"] > BURST_WINDOW_SECONDS:
            episodes.append(current)
            current = []
        current.append(r)
    if current:
        episodes.append(current)

    return [ep for ep in episodes if len(ep) >= BURST_MIN_RESPONSES]


def analyze_responses(records: list) -> dict:
    """
    Detect spoofing bursts: many responses for one query name, arriving
    close together, with transaction IDs that mostly disagree with the
    ID the client's original query actually used.

    Returns:
        {"alerts": [ {"query", "src_ip", "burst_size", "distinct_txn_ids",
                       "distinct_response_ips", "mismatch_ratio",
                       "window_start", "window_end"}, ... ]}
    """
    # The real transaction ID a client used per query name (from the query
    # record itself -- the one thing a spoofer cannot see or control).
    real_txn_id_by_query = {}
    for r in records:
        if not r["is_response"]:
            real_txn_id_by_query.setdefault(r["query"], r["txn_id"])

    responses_by_query = defaultdict(list)
    for r in records:
        if r["is_response"]:
            responses_by_query[r["query"]].append(r)

    seen_bursts = set()
    alerts = []
    for query, responses in responses_by_query.items():
        real_txn_id = real_txn_id_by_query.get(query)
        for burst in _find_bursts(responses):
            key = (query, burst[0]["timestamp"], burst[-1]["timestamp"], len(burst))
            if key in seen_bursts:
                continue
            seen_bursts.add(key)

            txn_ids = [r["txn_id"] for r in burst]
            response_ips = {r["response_ip"] for r in burst}
            mismatches = sum(1 for tid in txn_ids if tid != real_txn_id)
            mismatch_ratio = mismatches / len(burst)

            if mismatch_ratio >= MISMATCH_RATIO_THRESHOLD:
                alerts.append({
                    "query": query, "src_ip": burst[0]["src_ip"],
                    "burst_size": len(burst),
                    "distinct_txn_ids": len(set(txn_ids)),
                    "distinct_response_ips": len(response_ips),
                    "mismatch_ratio": round(mismatch_ratio, 2),
                    "window_start": burst[0]["timestamp"],
                    "window_end": burst[-1]["timestamp"],
                })

    return {"alerts": alerts}


if __name__ == "__main__":
    from dns_log_generator import generate_log

    log = generate_log()
    result = analyze_responses(log)
    print(f"Spoofing alerts: {len(result['alerts'])}")
    for a in result["alerts"]:
        print(f"  query={a['query']} host={a['src_ip']} burst_size={a['burst_size']} "
              f"distinct_txn_ids={a['distinct_txn_ids']} "
              f"distinct_response_ips={a['distinct_response_ips']} "
              f"mismatch_ratio={a['mismatch_ratio']} "
              f"window=[{a['window_start']:.2f}, {a['window_end']:.2f}]s")
