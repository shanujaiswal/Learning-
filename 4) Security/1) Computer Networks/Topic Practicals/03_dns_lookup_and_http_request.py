"""
03_dns_lookup_and_http_request.py
-----------------------------------
Maps to Theory chapter: "Routing, DNS, HTTP"

Shows what a browser actually does under the hood for a simple page load:
  1. Manual DNS resolution with socket.gethostbyname() -- turning a hostname
     into an IP address (the routing/DNS step).
  2. Opening a raw TCP socket to that IP on port 80 and hand-building a real
     HTTP/1.1 GET request line + headers (no `requests` library -- this is
     exactly the text that goes over the wire).
  3. Reading the raw response bytes off the socket and splitting them into
     status line / headers / body (the HTTP step).

Run:
    python 03_dns_lookup_and_http_request.py [hostname]

Defaults to example.com if no hostname is given (example.com is designated
by IANA specifically for documentation/demo use, so it's safe to hit).

Network note: this script makes ONE outbound plaintext HTTP request to a
host you specify (default example.com). It does not scan or probe anything;
it just performs the same request your browser would. Use a hostname you
are authorized to contact.
"""

import socket
import sys


def resolve_hostname(hostname: str) -> str:
    """Manual DNS resolution: hostname -> IPv4 address string."""
    ip_address = socket.gethostbyname(hostname)
    print(f"[dns] {hostname} resolved to {ip_address}")
    return ip_address


def build_http_get_request(hostname: str, path: str = "/") -> bytes:
    """
    Hand-build a real HTTP/1.1 GET request. This is literally the text
    a browser sends -- request line, then headers, then a blank line to
    mark the end of headers (no body for a GET).
    """
    request_lines = [
        f"GET {path} HTTP/1.1",
        f"Host: {hostname}",          # required in HTTP/1.1, tells the server which vhost
        "User-Agent: python-socket-demo/1.0",
        "Accept: text/html",
        "Connection: close",          # ask server to close the connection after responding
        "",                            # blank line terminates the header section
        "",                            # trailing newline
    ]
    request_text = "\r\n".join(request_lines)  # HTTP requires CRLF line endings
    return request_text.encode("utf-8")


def send_http_request(ip_address: str, hostname: str, request_bytes: bytes) -> bytes:
    """Open a raw TCP socket to port 80 and send/receive the raw HTTP bytes."""
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.settimeout(5.0)
        sock.connect((ip_address, 80))   # HTTP's well-known port
        sock.sendall(request_bytes)

        response_chunks = []
        while True:
            chunk = sock.recv(4096)
            if not chunk:
                break
            response_chunks.append(chunk)
    return b"".join(response_chunks)


def parse_http_response(raw_response: bytes):
    """Split raw HTTP response bytes into status line, headers, and body."""
    header_bytes, _, body_bytes = raw_response.partition(b"\r\n\r\n")
    header_lines = header_bytes.decode("utf-8", errors="replace").split("\r\n")
    status_line = header_lines[0]
    headers = header_lines[1:]
    return status_line, headers, body_bytes


def main():
    hostname = sys.argv[1] if len(sys.argv) > 1 else "example.com"

    print(f"--- Step 1: DNS resolution ---")
    ip_address = resolve_hostname(hostname)

    print(f"\n--- Step 2: Hand-built HTTP/1.1 request ---")
    request_bytes = build_http_get_request(hostname)
    print(request_bytes.decode("utf-8"))

    print(f"--- Step 3: Send over raw TCP socket to {ip_address}:80 ---")
    raw_response = send_http_request(ip_address, hostname, request_bytes)

    print(f"\n--- Step 4: Parse raw response ---")
    status_line, headers, body_bytes = parse_http_response(raw_response)
    print(f"Status line: {status_line}")
    print("Headers:")
    for h in headers:
        if h:
            print(f"  {h}")
    print(f"\nBody (first 300 bytes):")
    print(body_bytes[:300].decode("utf-8", errors="replace"))


if __name__ == "__main__":
    main()
