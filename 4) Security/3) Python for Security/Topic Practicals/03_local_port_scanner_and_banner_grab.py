"""
03_local_port_scanner_and_banner_grab.py

AUTHORIZED USE ONLY. This script only scans 127.0.0.1 (localhost) by design. Do not repoint the
TARGET_HOST constant at any host you do not own or are not explicitly authorized to test — even
simple TCP connect scans against third-party systems are unauthorized access attempts in many
jurisdictions.

Integrates Theory Ch.1 (Networking Basics) into a practical tool:

  1. TCP "connect scan" of a configurable port range on localhost using raw `socket`.
  2. For every open port found, attempts a banner grab: connect, wait briefly, and read
     whatever bytes the service sends first (many services — SSH, SMTP, FTP, HTTP — announce
     themselves this way).

No raw sockets, no privilege escalation, no half-open/stealth scanning techniques — this is a
plain, well-behaved TCP connect scan appropriate for learning the fundamentals.
"""

import socket

TARGET_HOST = "127.0.0.1"   # localhost only, intentionally hardcoded
PORT_RANGE = range(1, 1025)  # well-known ports; widen if you want, still localhost-only
CONNECT_TIMEOUT_SECONDS = 0.3
BANNER_READ_TIMEOUT_SECONDS = 1.0
BANNER_READ_BYTES = 256


def scan_port(host: str, port: int) -> bool:
    """Return True if a TCP connection to host:port succeeds (port is open)."""
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.settimeout(CONNECT_TIMEOUT_SECONDS)
        try:
            result = sock.connect_ex((host, port))
            return result == 0
        except socket.error:
            return False


def grab_banner(host: str, port: int) -> str | None:
    """Open a fresh connection and try to read an initial banner from the service.

    Many protocols (SSH, FTP, SMTP) send a greeting line immediately on connect; HTTP
    servers usually wait for a request, so we also try sending a bare newline to
    encourage a response before giving up.
    """
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
            sock.settimeout(BANNER_READ_TIMEOUT_SECONDS)
            sock.connect((host, port))
            try:
                data = sock.recv(BANNER_READ_BYTES)
            except socket.timeout:
                data = b""

            if not data:
                # Nudge protocols that wait for the client to speak first (e.g. plain HTTP).
                try:
                    sock.sendall(b"\r\n")
                    data = sock.recv(BANNER_READ_BYTES)
                except (socket.timeout, socket.error):
                    data = b""

            if data:
                return data.decode("utf-8", errors="replace").strip()
            return None
    except socket.error:
        return None


def main() -> None:
    print(f"=== Local port scan + banner grab: {TARGET_HOST} ports {PORT_RANGE.start}-{PORT_RANGE.stop - 1} ===")
    open_ports = []

    for port in PORT_RANGE:
        if scan_port(TARGET_HOST, port):
            open_ports.append(port)
            print(f"[OPEN] {TARGET_HOST}:{port}")

    if not open_ports:
        print("\nNo open ports found in range. Try starting a local service "
              "(e.g. 'python -m http.server 8000') and re-running with that port included.")
        return

    print(f"\n=== Banner grabbing on {len(open_ports)} open port(s) ===")
    for port in open_ports:
        banner = grab_banner(TARGET_HOST, port)
        if banner:
            print(f"[{port}] banner: {banner!r}")
        else:
            print(f"[{port}] no banner received (service may be silent until it gets a valid request)")


if __name__ == "__main__":
    main()
