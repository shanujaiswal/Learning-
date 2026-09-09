"""
traffic_profiler.py
-----------------------------
Maps to Theory chapter: "Packet Capture Fundamentals with Wireshark"
Real-world equivalent: Wireshark's Statistics menu -- "Protocol Hierarchy"
and "Conversations" (Statistics > Conversations, with its Endpoints tab
for top talkers).

Computes batch-wide traffic statistics directly from the flat packet list
and from the reassembled stream list: protocol distribution, top talkers
by bytes sent, top destination ports, and total conversation count.
"""

from collections import Counter, defaultdict


def protocol_distribution(packets):
    """
    Count packets per protocol -- the same numbers behind Wireshark's
    Protocol Hierarchy percentages. Returns dict {protocol: packet_count},
    sorted by descending count.
    """
    counts = Counter(p["protocol"] for p in packets)
    return dict(sorted(counts.items(), key=lambda kv: kv[1], reverse=True))


def top_talkers(packets, top_n=5):
    """
    Rank IP addresses by total bytes SENT (as src_ip) across the whole
    batch -- Wireshark's Statistics > Conversations > Endpoints "Bytes"
    column, sorted descending. Returns a list of (ip, total_bytes) tuples.
    """
    bytes_by_ip = defaultdict(int)
    for p in packets:
        bytes_by_ip[p["src_ip"]] += p["size"]
    ranked = sorted(bytes_by_ip.items(), key=lambda kv: kv[1], reverse=True)
    return ranked[:top_n]


def top_destination_ports(packets, top_n=5):
    """
    Rank destination ports by number of packets addressed to them --
    mirrors eyeballing the Dst Port column in Wireshark's packet list to
    spot which services see the most traffic. Returns a list of
    (port, packet_count) tuples.
    """
    counts = Counter(p["dst_port"] for p in packets)
    ranked = sorted(counts.items(), key=lambda kv: kv[1], reverse=True)
    return ranked[:top_n]


def build_traffic_profile(packets, streams):
    """
    Bundle every profiling metric into one dict -- the payload `main.py`
    both prints as text and hands to the plotting code.
    """
    return {
        "total_packets": len(packets),
        "total_bytes": sum(p["size"] for p in packets),
        "protocol_distribution": protocol_distribution(packets),
        "top_talkers": top_talkers(packets),
        "top_destination_ports": top_destination_ports(packets),
        "total_conversations": len(streams),
    }


if __name__ == "__main__":
    from packet_capture_generator import generate_packet_batch
    from stream_reassembler import reassemble_streams

    batch = generate_packet_batch()
    streams = reassemble_streams(batch)
    profile = build_traffic_profile(batch, streams)

    print(f"Total packets: {profile['total_packets']}")
    print(f"Total bytes:   {profile['total_bytes']}")
    print(f"Total conversations (streams): {profile['total_conversations']}\n")

    print("Protocol distribution:")
    for proto, count in profile["protocol_distribution"].items():
        print(f"  {proto:<5} {count} packets")

    print("\nTop talkers (by bytes sent):")
    for ip, total in profile["top_talkers"]:
        print(f"  {ip:<18} {total} bytes")

    print("\nTop destination ports:")
    for port, count in profile["top_destination_ports"]:
        print(f"  port {port:<6} {count} packets")
