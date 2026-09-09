"""
packet_capture_generator.py
-----------------------------
Maps to Theory chapter: "Packet Capture Fundamentals with Wireshark"

Generates a synthetic BATCH of "captured" packets -- structured Python
records that stand in for what Wireshark/tcpdump would hand you after a
real capture, but without ever touching a network interface, a .pcap file,
or requiring root/Administrator/npcap. Every packet is a plain dict with
exactly the fields an analyst would read off Wireshark's packet list pane:
timestamp, src/dst IP, src/dst port, protocol, TCP flags, payload size.

This is the offline substitute for `06_scapy_packet_sniffer.py`'s live
`sniff()` call -- same information shape, deterministic and reproducible
(fixed random seed) so the rest of the pipeline can be tested and graded
without any hardware/network dependency.

Scenario baked into the synthetic batch:
  1. Several complete, legitimate TCP conversations -- full
     SYN -> SYN/ACK -> ACK -> PSH/ACK (data) -> FIN handshake lifecycle.
  2. One legitimate small UDP "conversation" (DNS-like) for protocol mix.
  3. One malformed/handshake-less TCP "stream" -- bare data packets with
     no SYN at all, the classic signature of a spoofed/scanning source or
     a capture that started mid-stream after a scan.
  4. One legitimate-looking TCP conversation that transfers an abnormally
     large volume of data -- the kind of single-flow spike that could be
     a large file transfer or could be exfiltration.

Run standalone to preview the generated batch:
    python packet_capture_generator.py
"""

import random

RANDOM_SEED = 42  # fixed seed -> identical batch on every run, everywhere

# TCP flag combinations, spelled out the way Wireshark's flags column does
SYN = "SYN"
SYN_ACK = "SYN,ACK"
ACK = "ACK"
PSH_ACK = "PSH,ACK"
FIN_ACK = "FIN,ACK"

COMMON_PORTS = {
    80: "HTTP",
    443: "HTTPS",
    22: "SSH",
    53: "DNS",
    3389: "RDP",
}


def _make_packet(ts, src_ip, src_port, dst_ip, dst_port, proto, flags, size):
    """Build one packet record -- the atomic unit of the whole pipeline."""
    return {
        "timestamp": round(ts, 6),
        "src_ip": src_ip,
        "src_port": src_port,
        "dst_ip": dst_ip,
        "dst_port": dst_port,
        "protocol": proto,       # "TCP" or "UDP"
        "flags": flags,          # None for UDP, flag string for TCP
        "size": size,            # bytes, including headers (Wireshark "Length")
    }


def _tcp_conversation(packets, t0, client_ip, client_port, server_ip, server_port,
                       data_packet_sizes, rng):
    """Append a full, well-formed TCP conversation: handshake, data, teardown."""
    t = t0
    # 3-way handshake
    packets.append(_make_packet(t, client_ip, client_port, server_ip, server_port,
                                 "TCP", SYN, 60))
    t += rng.uniform(0.001, 0.02)
    packets.append(_make_packet(t, server_ip, server_port, client_ip, client_port,
                                 "TCP", SYN_ACK, 60))
    t += rng.uniform(0.001, 0.02)
    packets.append(_make_packet(t, client_ip, client_port, server_ip, server_port,
                                 "TCP", ACK, 54))

    # data exchange (client -> server pushes, server -> client pushes)
    for i, size in enumerate(data_packet_sizes):
        t += rng.uniform(0.005, 0.05)
        sender_is_client = (i % 2 == 0)
        if sender_is_client:
            packets.append(_make_packet(t, client_ip, client_port, server_ip,
                                         server_port, "TCP", PSH_ACK, size))
        else:
            packets.append(_make_packet(t, server_ip, server_port, client_ip,
                                         client_port, "TCP", PSH_ACK, size))

    # teardown
    t += rng.uniform(0.005, 0.03)
    packets.append(_make_packet(t, client_ip, client_port, server_ip, server_port,
                                 "TCP", FIN_ACK, 54))
    t += rng.uniform(0.001, 0.02)
    packets.append(_make_packet(t, server_ip, server_port, client_ip, client_port,
                                 "TCP", FIN_ACK, 54))
    return t


def generate_packet_batch(seed=RANDOM_SEED):
    """
    Build and return the full synthetic packet batch (list of dicts, in
    capture order sorted by timestamp) -- the single entry point the rest
    of the pipeline consumes, exactly like a parsed .pcap would be.
    """
    rng = random.Random(seed)
    packets = []
    t = 1000.0  # arbitrary epoch-like start time

    # --- 1. Several complete, legitimate TCP conversations ---------------
    legit_conversations = [
        # (client_ip, client_port, server_ip, server_port, num_data_packets)
        ("10.0.0.11", 51000, "93.184.216.34", 443, 6),   # HTTPS browsing
        ("10.0.0.12", 51050, "93.184.216.34", 443, 4),   # HTTPS browsing
        ("10.0.0.13", 51100, "192.168.1.50", 22, 8),     # SSH session
        ("10.0.0.11", 51200, "192.168.1.10", 80, 3),     # HTTP request
    ]
    for client_ip, client_port, server_ip, server_port, n_data in legit_conversations:
        sizes = [rng.randint(100, 1200) for _ in range(n_data)]
        t = _tcp_conversation(packets, t, client_ip, client_port, server_ip,
                               server_port, sizes, rng)
        t += rng.uniform(0.2, 1.0)

    # --- 2. A small legitimate UDP "conversation" (DNS-like) --------------
    for _ in range(3):
        packets.append(_make_packet(t, "10.0.0.14", 53000 + rng.randint(0, 999),
                                     "8.8.8.8", 53, "UDP", None, rng.randint(60, 120)))
        t += rng.uniform(0.01, 0.05)
        packets.append(_make_packet(t, "8.8.8.8", 53, "10.0.0.14",
                                     53000 + rng.randint(0, 999), "UDP", None,
                                     rng.randint(80, 200)))
        t += rng.uniform(0.2, 0.6)

    # --- 3. Malformed / handshake-less TCP "stream" -----------------------
    # Bare PSH/ACK data packets with NO preceding SYN -- looks like either
    # a spoofed source blasting data, or a capture starting mid-scan.
    scanner_ip, scanner_port = "203.0.113.66", 40444
    victim_ip, victim_port = "10.0.0.20", 8080
    for _ in range(5):
        packets.append(_make_packet(t, scanner_ip, scanner_port, victim_ip,
                                     victim_port, "TCP", PSH_ACK, rng.randint(40, 90)))
        t += rng.uniform(0.001, 0.01)
    t += rng.uniform(0.2, 0.5)

    # --- 4. Legitimate-looking but abnormally large single-flow transfer --
    big_client_ip, big_client_port = "10.0.0.15", 52500
    big_server_ip, big_server_port = "198.51.100.9", 443
    huge_sizes = [rng.randint(1400, 1460) for _ in range(400)]  # ~570 KB flow
    t = _tcp_conversation(packets, t, big_client_ip, big_client_port,
                          big_server_ip, big_server_port, huge_sizes, rng)

    # Sort by timestamp, exactly like Wireshark's packet list order.
    packets.sort(key=lambda p: p["timestamp"])
    return packets


if __name__ == "__main__":
    batch = generate_packet_batch()
    print(f"Generated {len(batch)} synthetic packets.\n")
    print(f"{'Time':>10}  {'Src':>21}  {'Dst':>21}  {'Proto':<5} {'Flags':<10} {'Size':>5}")
    for p in batch[:20]:
        src = f"{p['src_ip']}:{p['src_port']}"
        dst = f"{p['dst_ip']}:{p['dst_port']}"
        flags = p["flags"] or "-"
        print(f"{p['timestamp']:>10.4f}  {src:>21}  {dst:>21}  {p['protocol']:<5} "
              f"{flags:<10} {p['size']:>5}")
    print(f"... ({len(batch) - 20} more packets not shown)")
