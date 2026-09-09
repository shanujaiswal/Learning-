"""
06_scapy_packet_sniffer.py
-----------------------------
Maps to Theory chapter: "Packet Capture Fundamentals with Wireshark"

A small code-based equivalent of opening Wireshark: uses Scapy to capture
a handful of packets on the loopback interface and prints a one-line
summary of each (source/destination, protocol, key fields) -- the same
information you'd read off a Wireshark packet list pane.

Requires: pip install scapy
Also requires Npcap (Windows) or libpcap (Linux/macOS) to be installed for
live capture, and typically Administrator/root privileges to sniff.

*** LEGAL / ETHICAL NOTE ***
This script only captures traffic on the LOOPBACK interface of YOUR OWN
machine (127.0.0.1 / "Loopback" adapter) -- i.e. traffic your own local
programs send to themselves (e.g. run 01_tcp_client_server.py in another
terminal while this is capturing). Capturing traffic on a shared network
you do not own or do not have authorization to monitor (e.g. sniffing
other people's Wi-Fi) is illegal in most jurisdictions and violates most
network usage policies. Keep captures scoped to localhost/your own LAN
that you administer, exactly like the Theory chapter's Wireshark examples.

Run (as Administrator on Windows, or with sudo on Linux/macOS):
    python 06_scapy_packet_sniffer.py

While it's running, generate some loopback traffic in another terminal,
e.g.:
    python 01_tcp_client_server.py server
    python 01_tcp_client_server.py client
"""

from scapy.all import sniff, IP, TCP, UDP, Raw

PACKET_COUNT = 10        # capture just a few packets, then stop
LOOPBACK_FILTER = "host 127.0.0.1"  # BPF filter: loopback traffic only
CAPTURE_TIMEOUT_SECONDS = 30         # give up waiting after this many seconds


def summarize_packet(packet) -> str:
    """Build a one-line human-readable summary, like a Wireshark list row."""
    if IP not in packet:
        return f"[non-IP packet] {packet.summary()}"

    ip_layer = packet[IP]
    src = ip_layer.src
    dst = ip_layer.dst

    if TCP in packet:
        tcp_layer = packet[TCP]
        flags = tcp_layer.flags  # e.g. S, SA, PA, FA
        proto_info = f"TCP {src}:{tcp_layer.sport} -> {dst}:{tcp_layer.dport} [{flags}]"
    elif UDP in packet:
        udp_layer = packet[UDP]
        proto_info = f"UDP {src}:{udp_layer.sport} -> {dst}:{udp_layer.dport}"
    else:
        proto_info = f"IP proto={ip_layer.proto} {src} -> {dst}"

    length = len(packet)
    payload_note = ""
    if Raw in packet:
        payload_note = f", payload {len(packet[Raw].load)} bytes"

    return f"{proto_info}, total length {length} bytes{payload_note}"


def handle_packet(packet):
    print(summarize_packet(packet))


def main():
    print(f"Sniffing up to {PACKET_COUNT} packets on the loopback interface "
          f"(filter: '{LOOPBACK_FILTER}')...")
    print("Generate some traffic now, e.g. run 01_tcp_client_server.py's "
          "server + client in other terminals.\n")

    # iface=None lets Scapy pick its default interface; on most setups the
    # BPF filter above ("host 127.0.0.1") is what actually restricts capture
    # to loopback traffic. If your OS needs an explicit loopback interface
    # name (e.g. "Loopback" on Windows/Npcap, "lo0" on macOS, "lo" on Linux),
    # pass it explicitly: iface="Loopback Pseudo-Interface 1"
    packets = sniff(
        filter=LOOPBACK_FILTER,
        iface=None,
        prn=handle_packet,
        count=PACKET_COUNT,
        timeout=CAPTURE_TIMEOUT_SECONDS,
    )

    print(f"\nCapture finished. Total packets captured: {len(packets)}")
    if len(packets) == 0:
        print("No packets captured -- make sure you generated loopback "
              "traffic while this was running, and that you have the "
              "required permissions (Administrator/root) and Npcap/libpcap "
              "installed.")


if __name__ == "__main__":
    main()
