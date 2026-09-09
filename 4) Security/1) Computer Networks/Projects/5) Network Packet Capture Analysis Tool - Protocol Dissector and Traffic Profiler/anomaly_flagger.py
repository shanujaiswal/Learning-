"""
anomaly_flagger.py
-----------------------------
Maps to Theory chapter: "Packet Capture Fundamentals with Wireshark"
Real-world equivalent: the anomaly-hunting an analyst does manually in
Wireshark by eyeballing "Follow TCP Stream" for streams with no visible
handshake, and sorting Statistics > Conversations by Bytes descending to
spot an outlier flow -- exactly what SIEM/IDS traffic-analysis tooling
automates (per the Theory chapter's closing note on network-level
analysis having value even without payload visibility).

Two anomaly classes are flagged, matching the scenario brief:
  1. Handshake anomaly -- a TCP stream with data/teardown packets but no
     valid SYN -> SYN/ACK -> ACK sequence. Real causes: a port scan
     (attacker only sends probes, no full handshake), a spoofed source
     replaying stray segments, or a capture that started mid-connection.
  2. Volume anomaly -- a single flow whose total transferred bytes sit
     far above the rest of the batch (mean + N * std, a simple
     z-score-style threshold), which is exactly the kind of pattern that
     flags a large, unexpected exfiltration-style transfer.
"""

import numpy as np


def flag_handshake_anomalies(streams):
    """
    Return the subset of TCP streams whose handshake_ok is explicitly
    False (UDP streams have handshake_ok=None and are never flagged here
    since they're connectionless by design, not anomalous).
    """
    return [s for s in streams if s["protocol"] == "TCP" and s["handshake_ok"] is False]


def flag_volume_anomalies(streams, n_std=2.0):
    """
    Flag streams whose total_bytes exceeds mean + n_std * std across ALL
    streams in the batch -- a simple, explainable statistical threshold
    (no ML needed) for "this flow moved a lot more data than everything
    else we saw." Returns (flagged_streams, threshold_bytes, mean, std).
    """
    if not streams:
        return [], 0.0, 0.0, 0.0

    byte_totals = np.array([s["total_bytes"] for s in streams], dtype=float)
    mean = float(byte_totals.mean())
    std = float(byte_totals.std())
    threshold = mean + n_std * std

    flagged = [s for s in streams if s["total_bytes"] > threshold]
    return flagged, threshold, mean, std


def build_anomaly_report(streams, n_std=2.0):
    """
    Run both detectors and package the results into one report dict that
    `main.py` prints and that a downstream alerting system could equally
    consume as JSON.
    """
    handshake_anomalies = flag_handshake_anomalies(streams)
    volume_anomalies, threshold, mean, std = flag_volume_anomalies(streams, n_std=n_std)

    return {
        "handshake_anomalies": handshake_anomalies,
        "volume_anomalies": volume_anomalies,
        "volume_threshold_bytes": threshold,
        "volume_mean_bytes": mean,
        "volume_std_bytes": std,
        "n_std_used": n_std,
    }


if __name__ == "__main__":
    from packet_capture_generator import generate_packet_batch
    from stream_reassembler import reassemble_streams

    batch = generate_packet_batch()
    streams = reassemble_streams(batch)
    report = build_anomaly_report(streams)

    print(f"Volume threshold: mean={report['volume_mean_bytes']:.1f} + "
          f"{report['n_std_used']} * std={report['volume_std_bytes']:.1f} "
          f"= {report['volume_threshold_bytes']:.1f} bytes\n")

    print(f"Handshake anomalies ({len(report['handshake_anomalies'])}):")
    for s in report["handshake_anomalies"]:
        print(f"  {s['endpoint_a']} <-> {s['endpoint_b']}  flags={s['flag_sequence']}")

    print(f"\nVolume anomalies ({len(report['volume_anomalies'])}):")
    for s in report["volume_anomalies"]:
        print(f"  {s['endpoint_a']} <-> {s['endpoint_b']}  bytes={s['total_bytes']}")
