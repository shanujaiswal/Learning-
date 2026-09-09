"""
main.py
--------
DNS Security Monitor -- runs a synthetic DNS query log through all three
detectors (entropy_analysis, spoofing_detector, typosquat_detector), prints
a running, timestamp-ordered alert feed tagged by detection type, then a
summary of how many of each attack type were found and whether any normal
traffic false-alarmed. Finally saves a matplotlib PNG showing query volume
over time with the flagged windows highlighted.

Run:
    python main.py
"""

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np

from dns_log_generator import generate_log, TUNNEL_HOST, SPOOF_VICTIM_HOST
from entropy_analysis import analyze_queries as analyze_tunneling
from spoofing_detector import analyze_responses as analyze_spoofing
from typosquat_detector import analyze_queries as analyze_typosquat

OUTPUT_PNG = "dns_monitor_result.png"


def build_alert_feed(tunnel_result, spoof_result, typo_result):
    """Merge all three detectors' alerts into one time-ordered feed of
    (timestamp, tag, message) tuples, the way a SIEM dashboard would."""
    feed = []

    for a in tunnel_result["alerts"]:
        feed.append((
            a["window_start"], "TUNNELING",
            f"host {a['src_ip']} -> {a['domain']}: {a['count']} high-entropy/long "
            f"labels within {a['window_end'] - a['window_start']:.1f}s "
            f"(e.g. {a['sample_labels'][0][:24]}...)"
        ))

    for a in spoof_result["alerts"]:
        feed.append((
            a["window_start"], "SPOOFING",
            f"query {a['query']} from {a['src_ip']}: {a['burst_size']} responses in "
            f"{a['window_end'] - a['window_start']:.2f}s, "
            f"{a['distinct_txn_ids']} distinct txn IDs, "
            f"{a['distinct_response_ips']} distinct answer IPs "
            f"(mismatch ratio {a['mismatch_ratio']})"
        ))

    for a in typo_result["alerts"]:
        feed.append((
            a["timestamp"], "TYPOSQUAT",
            f"host {a['src_ip']} queried '{a['domain']}' "
            f"(edit distance {a['edit_distance']} from known brand '{a['closest_brand']}')"
        ))

    feed.sort(key=lambda item: item[0])
    return feed


def print_alert_feed(feed):
    print("=" * 78)
    print("DNS SECURITY MONITOR -- ALERT FEED")
    print("=" * 78)
    if not feed:
        print("(no alerts)")
        return
    for t, tag, message in feed:
        print(f"[t={t:7.2f}s] [{tag:10}] {message}")


def check_false_positives(records, tunnel_result, spoof_result, typo_result):
    """A false positive here means: an alert whose host isn't the host the
    corresponding attack was deliberately injected on (tunneling/spoofing),
    or a typosquat alert firing on a domain that's actually in the
    known-brand list verbatim (which would mean the detector is broken,
    since exact brand matches are excluded by typosquat_detector itself)."""
    false_positives = []

    for a in tunnel_result["alerts"]:
        if a["src_ip"] != TUNNEL_HOST:
            false_positives.append(("TUNNELING", a["src_ip"], a["domain"]))

    for a in spoof_result["alerts"]:
        if a["src_ip"] != SPOOF_VICTIM_HOST:
            false_positives.append(("SPOOFING", a["src_ip"], a["query"]))

    for a in typo_result["alerts"]:
        if a["edit_distance"] == 0:
            false_positives.append(("TYPOSQUAT", a["src_ip"], a["domain"]))

    return false_positives


def print_summary(records, tunnel_result, spoof_result, typo_result, false_positives):
    print("\n" + "=" * 78)
    print("SUMMARY")
    print("=" * 78)
    print(f"Total DNS records processed : {len(records)}")
    print(f"Tunneling alerts            : {len(tunnel_result['alerts'])}")
    print(f"Spoofing alerts             : {len(spoof_result['alerts'])}")
    print(f"Typosquat alerts            : {len(typo_result['alerts'])}")
    print(f"False positives on normal traffic : {len(false_positives)}")
    if false_positives:
        for tag, host, target in false_positives:
            print(f"  ! {tag} false positive: host={host} target={target}")
    else:
        print("  (none -- all alerts trace back to the injected attack patterns)")


def plot_results(records, tunnel_result, spoof_result, typo_result, output_path=OUTPUT_PNG):
    """Bar chart of query volume per 2-second time bucket, with the time
    windows containing each attack type's alert(s) shaded and labeled."""
    timestamps = np.array([r["timestamp"] for r in records if not r["is_response"]])

    bucket_size = 2.0
    max_t = timestamps.max() if len(timestamps) else 0.0
    bins = np.arange(0, max_t + bucket_size, bucket_size)
    counts, edges = np.histogram(timestamps, bins=bins)
    centers = (edges[:-1] + edges[1:]) / 2

    fig, ax = plt.subplots(figsize=(13, 6))
    ax.bar(centers, counts, width=bucket_size * 0.9, color="#4C72B0",
           edgecolor="none", label="Query volume (per 2s bucket)", zorder=2)

    def shade_window(start, end, color, label):
        ax.axvspan(start - 0.5, end + 0.5, color=color, alpha=0.28, zorder=1, label=label)

    for a in tunnel_result["alerts"]:
        shade_window(a["window_start"], a["window_end"], "#DD8452", "Tunneling window")

    for a in spoof_result["alerts"]:
        shade_window(a["window_start"], a["window_end"], "#C44E52", "Spoofing burst")

    for a in typo_result["alerts"]:
        ax.axvline(a["timestamp"], color="#8172B2", linestyle="--", linewidth=1.4,
                   alpha=0.85, zorder=3)

    # De-duplicate legend entries (axvspan called multiple times reuses labels).
    handles, labels = ax.get_legend_handles_labels()
    seen = {}
    for h, l in zip(handles, labels):
        seen.setdefault(l, h)
    if any(typo_result["alerts"]):
        seen.setdefault(
            "Typosquat query",
            plt.Line2D([0], [0], color="#8172B2", linestyle="--", linewidth=1.4),
        )
    ax.legend(seen.values(), seen.keys(), loc="upper left", frameon=True)

    ax.set_xlabel("Time (seconds since capture start)")
    ax.set_ylabel("DNS queries per 2s bucket")
    ax.set_title("DNS Security Monitor -- Query Volume with Flagged Windows")
    ax.grid(axis="y", alpha=0.3, zorder=0)
    fig.tight_layout()
    fig.savefig(output_path, dpi=130)
    plt.close(fig)
    print(f"\nSaved chart -> {output_path}")


def main():
    records = generate_log()

    tunnel_result = analyze_tunneling(records)
    spoof_result = analyze_spoofing(records)
    typo_result = analyze_typosquat(records)

    feed = build_alert_feed(tunnel_result, spoof_result, typo_result)
    print_alert_feed(feed)

    false_positives = check_false_positives(
        records, tunnel_result, spoof_result, typo_result
    )
    print_summary(records, tunnel_result, spoof_result, typo_result, false_positives)

    plot_results(records, tunnel_result, spoof_result, typo_result)


if __name__ == "__main__":
    main()
