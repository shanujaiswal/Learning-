"""
05_simple_port_scanner.py
---------------------------
Maps to Theory chapters: "Networking Fundamentals/OSI Model" +
                          "TCP/IP, Ports, IP Addressing"

A basic TCP "connect scan": for each port in a range, try to open a TCP
connection with a short timeout. If connect() succeeds, the port is open
(something is listening); if it fails/times out, treat it as closed/filtered.

*** LEGAL / ETHICAL NOTE ***
This script is HARDCODED to scan 127.0.0.1 (your own machine, loopback)
only. Port scanning any host you do not own or do not have explicit written
authorization to test is illegal in most jurisdictions (e.g. under computer
misuse / unauthorized access laws) and can be treated as an attack even if
no harm is intended. Do not modify TARGET_HOST to point at another machine,
your ISP's gateway, or any internet host without permission.

Run:
    python 05_simple_port_scanner.py
"""

import socket

TARGET_HOST = "127.0.0.1"   # DO NOT CHANGE -- see legal note above
PORT_RANGE = range(1, 5100)  # well-known ports + our demo scripts' ports (5050/5051)
# Note: on localhost, closed ports return "connection refused" almost
# instantly (no real timeout wait), so scanning this many ports is fast.
CONNECT_TIMEOUT_SECONDS = 0.3


def scan_port(host: str, port: int) -> bool:
    """Return True if a TCP connect() to (host, port) succeeds."""
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.settimeout(CONNECT_TIMEOUT_SECONDS)
        try:
            result = sock.connect_ex((host, port))  # returns 0 on success
            return result == 0
        except socket.error:
            return False


def main():
    print(f"Scanning TCP ports {PORT_RANGE.start}-{PORT_RANGE.stop - 1} "
          f"on {TARGET_HOST} (localhost only)...")
    print("(Tip: run 01_tcp_client_server.py or 02_udp_echo_demo.py's server "
          "first, then re-run this scan to see the open port show up.)\n")

    open_ports = []
    for port in PORT_RANGE:
        if scan_port(TARGET_HOST, port):
            open_ports.append(port)
            try:
                service = socket.getservbyport(port, "tcp")
            except OSError:
                service = "unknown"
            print(f"  [OPEN] port {port:5d}  ({service})")

    print("\nScan complete.")
    if open_ports:
        print(f"Open ports found: {open_ports}")
    else:
        print("No open ports found in range. Try starting one of the demo "
              "servers in another terminal (e.g. 01_tcp_client_server.py "
              "server listens on port 5050) and scan again.")


if __name__ == "__main__":
    main()
