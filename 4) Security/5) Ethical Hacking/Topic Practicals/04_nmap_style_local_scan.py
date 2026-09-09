"""
04_nmap_style_local_scan.py -- Self-Contained Local Port Scanner
=================================================================================
(Ch.03: Reconnaissance and Scanning with Nmap)

LEGAL / ETHICAL SCOPE
----------------------
Only test systems you own or are authorized to test. This script scans ONLY
127.0.0.1 (your own machine, loopback interface). Do not modify TARGET_HOST
to point at any other host, and never run a script like this against
infrastructure you do not own or have explicit written authorization to test.
Port scanning other people's systems without authorization can be illegal.

WHAT THIS DEMONSTRATES
------------------------
Nmap-style TCP "connect scan" concepts (the same core idea behind `nmap -sT`)
implemented from scratch with only Python's built-in `socket` module -- no
external nmap binary or third-party scanning library required. This mirrors
the Reconnaissance and Scanning chapter's methodology (identify live hosts,
enumerate open ports, map ports to likely services) in a fully self-contained
way you can run offline.

Run target_app.py first (so port 5000 shows up as open), then run:
    python 04_nmap_style_local_scan.py
"""

import socket
import time

TARGET_HOST = "127.0.0.1"  # loopback ONLY -- never change this
PORT_RANGE = range(1, 1025)  # well-known ports, like a quick nmap default scan
CONNECT_TIMEOUT_SECONDS = 0.25

# A small, illustrative mapping of common ports to the service usually found
# there -- purely informational context, same spirit as nmap's service names.
COMMON_SERVICES = {
    21: "FTP",
    22: "SSH",
    23: "Telnet",
    25: "SMTP",
    53: "DNS",
    80: "HTTP",
    110: "POP3",
    139: "NetBIOS",
    143: "IMAP",
    443: "HTTPS",
    445: "SMB",
    3306: "MySQL",
    3389: "RDP",
    5000: "Flask dev server (this lab's target_app.py)",
    5432: "PostgreSQL",
    8080: "HTTP-alt",
}


def scan_port(host: str, port: int, timeout: float = CONNECT_TIMEOUT_SECONDS) -> bool:
    """
    TCP connect scan: attempt a full TCP handshake. If connect_ex() returns
    0, the three-way handshake succeeded, meaning something is listening
    (i.e. the port is open). This is exactly the technique behind nmap's
    default -sT scan, just implemented directly with a raw socket call.
    """
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.settimeout(timeout)
    try:
        result = sock.connect_ex((host, port))
        return result == 0
    finally:
        sock.close()


def scan_range(host: str, ports) -> list:
    open_ports = []
    for port in ports:
        if scan_port(host, port):
            open_ports.append(port)
    return open_ports


def main():
    print(f"[*] Starting self-contained TCP connect scan of {TARGET_HOST}")
    print(f"[*] Port range: {PORT_RANGE.start}-{PORT_RANGE.stop - 1}")
    print("[*] (Equivalent in spirit to: nmap -p 1-1024 127.0.0.1)\n")

    start = time.time()
    open_ports = scan_range(TARGET_HOST, PORT_RANGE)
    elapsed = time.time() - start

    print(f"[*] Scan complete in {elapsed:.2f}s. Open ports found: {len(open_ports)}\n")
    if not open_ports:
        print("    No open ports found in range. If you expected to see 5000 open,")
        print("    make sure target_app.py is running in another terminal.")
        return

    print(f"{'PORT':<8}{'STATE':<8}{'LIKELY SERVICE'}")
    for port in open_ports:
        service = COMMON_SERVICES.get(port, "unknown")
        print(f"{port:<8}{'open':<8}{service}")


if __name__ == "__main__":
    main()
