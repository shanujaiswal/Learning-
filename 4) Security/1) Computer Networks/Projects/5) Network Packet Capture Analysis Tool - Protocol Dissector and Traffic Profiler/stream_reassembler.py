"""
stream_reassembler.py
-----------------------------
Maps to Theory chapter: "Packet Capture Fundamentals with Wireshark"
Real-world equivalent: Wireshark's "Follow -> TCP Stream" feature, and the
underlying stream-tracking table it builds behind Statistics > Conversations.

Wireshark identifies a TCP "conversation" by the classic 4-tuple:
    (src_ip, src_port, dst_ip, dst_port)
but since packets flow in BOTH directions, the same logical conversation
shows up under two mirror-image 4-tuples (A->B and B->A). This module
normalizes that by keying each stream on an UNORDERED endpoint pair, then
reconstructs the ordered packet sequence and the flag sequence so we can
tell whether a proper handshake (SYN -> SYN/ACK -> ACK) actually happened.
"""

from collections import defaultdict


def _stream_key(packet):
    """
    Unordered 4-tuple key so that client->server and server->client
    packets of the same conversation land in the same bucket -- exactly
    how Wireshark's Conversations table merges both directions into one row.
    Only meaningful for TCP; UDP flows are keyed the same way for reuse.
    """
    a = (packet["src_ip"], packet["src_port"])
    b = (packet["dst_ip"], packet["dst_port"])
    endpoints = tuple(sorted([a, b]))
    return (packet["protocol"], endpoints[0], endpoints[1])


def group_into_streams(packets):
    """
    Group a flat packet batch into streams keyed by the unordered 4-tuple.
    Returns: dict {stream_key: [packets sorted by timestamp]}
    """
    buckets = defaultdict(list)
    for p in packets:
        buckets[_stream_key(p)].append(p)
    for key in buckets:
        buckets[key].sort(key=lambda p: p["timestamp"])
    return dict(buckets)


def _flag_sequence(stream_packets):
    """Ordered list of TCP flag strings seen in the stream ('-' for UDP/none)."""
    return [p["flags"] or "-" for p in stream_packets if p["protocol"] == "TCP"]


def _has_proper_handshake(flag_sequence):
    """
    A proper TCP handshake requires, in order, somewhere near the start of
    the stream: a bare SYN, then a SYN/ACK, then an ACK. We don't require
    them to be the very first three flags (retransmits/reordering happen),
    but SYN must appear, SYN/ACK must appear after it, and a plain ACK
    must appear after that -- otherwise we never saw the setup at all.
    """
    if "SYN" not in flag_sequence:
        return False
    syn_index = flag_sequence.index("SYN")
    if "SYN,ACK" not in flag_sequence[syn_index + 1:]:
        return False
    synack_index = flag_sequence.index("SYN,ACK", syn_index + 1)
    remaining = flag_sequence[synack_index + 1:]
    return "ACK" in remaining


def _has_teardown(flag_sequence):
    """A graceful close shows at least one FIN,ACK somewhere in the stream."""
    return "FIN,ACK" in flag_sequence


def reassemble_streams(packets):
    """
    Full reassembly pass: group packets into streams and compute the
    per-stream summary an analyst actually wants -- endpoints, protocol,
    ordered flag sequence, handshake validity, byte/packet counts, and
    conversation duration. Returns a list of stream-summary dicts, sorted
    by start time (capture order of first packet), mirroring the order
    Wireshark lists conversations in.
    """
    buckets = group_into_streams(packets)
    streams = []

    for (proto, ep_a, ep_b), stream_packets in buckets.items():
        flags_seq = _flag_sequence(stream_packets)
        total_bytes = sum(p["size"] for p in stream_packets)
        start_ts = stream_packets[0]["timestamp"]
        end_ts = stream_packets[-1]["timestamp"]

        if proto == "TCP":
            handshake_ok = _has_proper_handshake(flags_seq)
            teardown_ok = _has_teardown(flags_seq)
        else:
            # UDP is connectionless -- there's no handshake concept, so we
            # mark it not-applicable rather than penalizing it as invalid.
            handshake_ok = None
            teardown_ok = None

        streams.append({
            "stream_key": (proto, ep_a, ep_b),
            "protocol": proto,
            "endpoint_a": f"{ep_a[0]}:{ep_a[1]}",
            "endpoint_b": f"{ep_b[0]}:{ep_b[1]}",
            "packets": stream_packets,
            "packet_count": len(stream_packets),
            "total_bytes": total_bytes,
            "start_time": start_ts,
            "end_time": end_ts,
            "duration": round(end_ts - start_ts, 6),
            "flag_sequence": flags_seq,
            "handshake_ok": handshake_ok,
            "teardown_ok": teardown_ok,
        })

    streams.sort(key=lambda s: s["start_time"])
    return streams


if __name__ == "__main__":
    from packet_capture_generator import generate_packet_batch

    batch = generate_packet_batch()
    streams = reassemble_streams(batch)
    print(f"Reassembled {len(streams)} streams from {len(batch)} packets.\n")
    for s in streams:
        hs = "N/A" if s["handshake_ok"] is None else ("OK" if s["handshake_ok"] else "MISSING")
        print(f"{s['protocol']:<4} {s['endpoint_a']:<22} <-> {s['endpoint_b']:<22} "
              f"pkts={s['packet_count']:<4} bytes={s['total_bytes']:<7} handshake={hs}")
