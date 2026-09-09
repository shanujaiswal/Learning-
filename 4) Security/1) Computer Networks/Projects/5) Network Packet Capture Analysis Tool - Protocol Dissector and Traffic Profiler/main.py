"""
main.py
-----------------------------
Network Packet Capture Analysis Tool -- Protocol Dissector and Traffic Profiler
Maps to Theory chapter: "Packet Capture Fundamentals with Wireshark"

Runs the full offline pipeline:
    generate synthetic packets -> reassemble TCP streams -> profile traffic
    -> flag anomalies -> print a Wireshark-"Conversations"-style report
    -> save traffic_profile.png (protocol distribution + top talkers)

Fully self-contained: no .pcap file, no scapy, no live capture, no
root/Administrator/npcap. Everything is a Python data structure built by
packet_capture_generator.py with a fixed random seed, so results are
reproducible on any machine.

Run:
    python main.py
"""

import matplotlib
matplotlib.use("Agg")  # write PNG straight to disk, no display/server needed
import matplotlib.pyplot as plt

from packet_capture_generator import generate_packet_batch
from stream_reassembler import reassemble_streams
from traffic_profiler import build_traffic_profile
from anomaly_flagger import build_anomaly_report

OUTPUT_PNG = "traffic_profile.png"


def print_conversations_table(streams):
    """Wireshark Statistics > Conversations -style table, printed to console."""
    print("=" * 100)
    print("CONVERSATIONS")
    print("=" * 100)
    header = (f"{'Protocol':<8} {'Endpoint A':<24} {'Endpoint B':<24} "
              f"{'Packets':>7} {'Bytes':>8} {'Duration(s)':>11} {'Handshake':>10}")
    print(header)
    print("-" * len(header))
    for s in streams:
        if s["handshake_ok"] is None:
            hs = "N/A"
        elif s["handshake_ok"]:
            hs = "OK"
        else:
            hs = "MISSING"
        print(f"{s['protocol']:<8} {s['endpoint_a']:<24} {s['endpoint_b']:<24} "
              f"{s['packet_count']:>7} {s['total_bytes']:>8} {s['duration']:>11.4f} "
              f"{hs:>10}")
    print()


def print_traffic_profile(profile):
    print("=" * 100)
    print("TRAFFIC PROFILE")
    print("=" * 100)
    print(f"Total packets captured:  {profile['total_packets']}")
    print(f"Total bytes captured:    {profile['total_bytes']}")
    print(f"Total conversations:     {profile['total_conversations']}\n")

    print("Protocol distribution:")
    for proto, count in profile["protocol_distribution"].items():
        pct = 100 * count / profile["total_packets"]
        print(f"  {proto:<5} {count:>5} packets  ({pct:5.1f}%)")

    print("\nTop talkers (by bytes sent):")
    for ip, total in profile["top_talkers"]:
        print(f"  {ip:<18} {total:>8} bytes")

    print("\nTop destination ports:")
    for port, count in profile["top_destination_ports"]:
        print(f"  port {port:<6} {count:>4} packets")
    print()


def print_anomaly_findings(report):
    print("=" * 100)
    print("ANOMALY FINDINGS")
    print("=" * 100)
    print(f"Volume threshold used: mean ({report['volume_mean_bytes']:.1f}) + "
          f"{report['n_std_used']} * std ({report['volume_std_bytes']:.1f}) "
          f"= {report['volume_threshold_bytes']:.1f} bytes\n")

    handshake = report["handshake_anomalies"]
    print(f"[!] Handshake anomalies -- streams with no valid SYN->SYN/ACK->ACK "
          f"({len(handshake)} found):")
    if not handshake:
        print("    none")
    for s in handshake:
        print(f"    {s['endpoint_a']} <-> {s['endpoint_b']}  "
              f"packets={s['packet_count']}  flags_seen={s['flag_sequence']}")
        print("    -> Looks like a scan/spoofed source: data packets arrived "
              "with no completed 3-way handshake.")

    volume = report["volume_anomalies"]
    print(f"\n[!] Volume anomalies -- flows exceeding the statistical threshold "
          f"({len(volume)} found):")
    if not volume:
        print("    none")
    for s in volume:
        print(f"    {s['endpoint_a']} <-> {s['endpoint_b']}  "
              f"total_bytes={s['total_bytes']}  packets={s['packet_count']}")
        print("    -> Unusually large single-flow transfer: could be a big "
              "legitimate download, or exfiltration -- worth reviewing.")
    print()


def save_traffic_profile_png(profile, path=OUTPUT_PNG):
    """
    Save a two-panel PNG: protocol distribution (left) and top talkers by
    bytes (right) -- the same two views Wireshark's Statistics menu offers
    as separate windows, combined here into one shareable image.
    """
    fig, (ax_proto, ax_talkers) = plt.subplots(1, 2, figsize=(12, 5))

    # brand-neutral, colorblind-friendly categorical palette
    palette = ["#4C72B0", "#DD8452", "#55A868", "#C44E52", "#8172B2", "#937860"]

    protocols = list(profile["protocol_distribution"].keys())
    counts = list(profile["protocol_distribution"].values())
    bars = ax_proto.bar(protocols, counts, color=palette[:len(protocols)])
    ax_proto.set_title("Protocol Distribution (packets)")
    ax_proto.set_xlabel("Protocol")
    ax_proto.set_ylabel("Packet count")
    for rect, count in zip(bars, counts):
        ax_proto.text(rect.get_x() + rect.get_width() / 2, rect.get_height(),
                      str(count), ha="center", va="bottom", fontsize=9)

    talkers = profile["top_talkers"]
    ips = [ip for ip, _ in talkers]
    byte_totals = [total for _, total in talkers]
    y_pos = range(len(ips))
    ax_talkers.barh(list(y_pos), byte_totals, color=palette[:len(ips)])
    ax_talkers.set_yticks(list(y_pos))
    ax_talkers.set_yticklabels(ips)
    ax_talkers.invert_yaxis()  # largest talker on top
    ax_talkers.set_title("Top Talkers (bytes sent)")
    ax_talkers.set_xlabel("Bytes")
    for i, total in enumerate(byte_totals):
        ax_talkers.text(total, i, f" {total}", va="center", fontsize=9)

    fig.suptitle("Packet Capture Traffic Profile", fontsize=14, fontweight="bold")
    fig.tight_layout(rect=[0, 0, 1, 0.95])
    fig.savefig(path, dpi=150)
    plt.close(fig)
    print(f"Saved traffic profile chart to: {path}")


def main():
    print("Generating synthetic packet capture batch (fixed seed)...\n")
    packets = generate_packet_batch()

    print("Reassembling TCP streams from packets (4-tuple grouping)...\n")
    streams = reassemble_streams(packets)

    print_conversations_table(streams)

    profile = build_traffic_profile(packets, streams)
    print_traffic_profile(profile)

    report = build_anomaly_report(streams, n_std=2.0)
    print_anomaly_findings(report)

    save_traffic_profile_png(profile)


if __name__ == "__main__":
    main()
