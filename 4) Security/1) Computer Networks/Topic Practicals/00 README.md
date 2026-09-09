# Computer Networks -- Practical Labs

Hands-on Python scripts that pair with the Theory chapters in
`3) Security\1) Computer Networks\Theory\`. Each script is a small, real,
runnable program using the Python standard library (`socket`, `ipaddress`)
plus `scapy` for one packet-capture demo.

## Setup

Most scripts need nothing beyond a standard Python 3 install. For the one
Scapy-based script, install it first:

```
pip install scapy
```

Scapy's live sniffing also needs a packet-capture driver installed on your
OS: **Npcap** on Windows (https://npcap.com/), or `libpcap` on
Linux/macOS (usually already present). Live capture typically needs
Administrator (Windows) / root (`sudo`, Linux/macOS) privileges.

## Safety note (read before running anything)

Every scanning or sniffing example in this folder is deliberately scoped to
**localhost (127.0.0.1) or your own machine/LAN only**:

- `05_simple_port_scanner.py` is hardcoded to scan `127.0.0.1` and will not
  scan any other host.
- `06_scapy_packet_sniffer.py` captures only loopback traffic on your own
  machine.

Scanning or sniffing a network or host you do not own and do not have
explicit written authorization to test is illegal in most jurisdictions
(computer misuse / unauthorized access laws) even without malicious intent.
Do not repurpose these scripts against any other host or network.

## Index -- script to Theory chapter mapping

| Script | Theory chapter it demonstrates | What it does |
|---|---|---|
| `01_tcp_client_server.py` | Networking Fundamentals/OSI Model; TCP/IP, Ports, IP Addressing | Real TCP server (bind/listen/accept) and client, one file, run as `server` or `client` -- shows connection-oriented, port-addressed communication. |
| `02_udp_echo_demo.py` | TCP/IP, Ports, IP Addressing | UDP server/client pair with inline comments contrasting it against script 01: no connection setup, no delivery guarantee, message boundaries preserved per-datagram. |
| `03_dns_lookup_and_http_request.py` | Routing, DNS, HTTP | Resolves a hostname with `socket.gethostbyname()`, then hand-builds a raw HTTP/1.1 GET request over a raw TCP socket (no `requests` library) and parses the raw response -- literally what a browser does under the hood. |
| `04_subnet_calculator.py` | TCP/IP, Ports, IP Addressing | Given a CIDR block (e.g. `192.168.1.0/24`), computes network address, broadcast address, usable host range, and usable host count via `ipaddress`; prints several worked examples. |
| `05_simple_port_scanner.py` | Networking Fundamentals/OSI Model; TCP/IP, Ports, IP Addressing | Basic TCP connect-scan against `127.0.0.1` only, showing which local ports are open (pairs well with running scripts 01/02 as targets). |
| `06_scapy_packet_sniffer.py` | Packet Capture Fundamentals with Wireshark | Scapy-based sniffer that captures a few packets on the loopback interface and prints a Wireshark-style one-line summary of each. |

## Suggested run order

1. `04_subnet_calculator.py` -- no networking needed, just run it and read the output.
2. `01_tcp_client_server.py server` in one terminal, `01_tcp_client_server.py client` in another.
3. `02_udp_echo_demo.py server` / `02_udp_echo_demo.py client`, comparing behavior/comments against step 2.
4. `05_simple_port_scanner.py` -- run it while a server from step 2 or 3 is still listening, to see the open port detected.
5. `03_dns_lookup_and_http_request.py` -- run standalone (needs internet access to resolve/contact example.com, or pass your own authorized hostname).
6. `06_scapy_packet_sniffer.py` -- start it, then in another terminal re-run step 2 or 3 to generate loopback traffic to observe.
