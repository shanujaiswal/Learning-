### Packet Crafting and Sniffing with Scapy

--> Scapy is a Python library that lets you build, send, sniff, and dissect network packets at almost any layer, byte by byte.
--> Where `socket` gives you a stream/datagram abstraction, Scapy gives you the raw packet itself — you construct the Ethernet frame, IP header, and TCP/UDP/ICMP header as Python objects and Scapy serializes them to wire format for you.

## LEGAL AND ETHICAL WARNING

--> Everything in this note — sending crafted packets, sniffing traffic, ARP scanning — must only be done on networks and hosts you own or have explicit written authorization to test.
--> ARP scanning and packet sniffing on a network you don't control (e.g. public Wi-Fi, a workplace network without permission, an ISP's network) can violate computer misuse laws even when no "attack" is intended, because you are intercepting/probing traffic that isn't yours.
--> Run all examples in this note against your own lab VMs, a home network you own, or an isolated virtual network (e.g. VirtualBox/VMware host-only adapter).

## Installing Scapy

```bash
pip install scapy
```

--> On Windows you also need Npcap (with "WinPcap API-compatible mode" checked during install) for raw packet capture. On Linux, raw sockets require root (`sudo python3 script.py`) because crafting/sniffing packets needs elevated privileges.

## Packet layers

--> Scapy models a packet as layers stacked with `/`. Each layer is a Python class with fields you can set as keyword arguments; unset fields get sensible defaults.

```python
from scapy.all import Ether, IP, TCP, UDP, ICMP

# Ethernet frame
eth = Ether()
eth.show()   # dst, src, type fields with defaults

# IP layer on top of nothing (Scapy fills src automatically at send time)
ip = IP(dst="192.168.1.10")

# Layer stacking with '/' builds the full packet
pkt = Ether() / IP(dst="192.168.1.10") / TCP(dport=80, flags="S")
pkt.show()   # prints every field of every layer, nested
```

--> `pkt.show()` is your best debugging friend — it prints the full layer breakdown (Ethernet -> IP -> TCP) with every field and its current value.
--> Common layers: `Ether` (layer 2), `ARP`, `IP`/`IPv6` (layer 3), `TCP`/`UDP`/`ICMP` (layer 4), and application-layer helpers like `DNS`, `Raw` (arbitrary payload bytes).

## Building and sending packets

--> Scapy gives several send functions depending on whether you need layer 2 or layer 3, and whether you want a reply.

1. `send(pkt)` – sends at layer 3 (IP and above), no reply captured.
2. `sendp(pkt)` – sends at layer 2 (you must supply the Ethernet layer yourself), no reply captured.
3. `sr(pkt)` – send and receive, returns matched answers *and* unanswered packets (a pair of lists).
4. `sr1(pkt)` – send and receive **one** answer only — the most common one for request/response protocols like ICMP ping.

```python
from scapy.all import IP, ICMP, sr1

pkt = IP(dst="192.168.1.1") / ICMP()
reply = sr1(pkt, timeout=2, verbose=0)

if reply:
    print(f"Got reply from {reply.src}, type={reply[ICMP].type}")  # type 0 = echo-reply
else:
    print("No reply (host down or blocking ICMP)")
```

--> `verbose=0` suppresses Scapy's default "Begin emission... Finished sending 1 packets" console spam — useful once you're scripting rather than exploring interactively.

## Sniffing with `sniff()`

--> `sniff()` captures live packets off a network interface and calls a callback function for each one.

```python
from scapy.all import sniff, IP, TCP

def handle_packet(pkt):
    if pkt.haslayer(IP) and pkt.haslayer(TCP):
        print(f"{pkt[IP].src}:{pkt[TCP].sport} -> {pkt[IP].dst}:{pkt[TCP].dport}")

# count=10 -> stop after 10 matching packets; omit for continuous capture (Ctrl+C to stop)
sniff(filter="tcp", prn=handle_packet, count=10)
```

--> Key `sniff()` parameters:

1. `filter` – a BPF (Berkeley Packet Filter) string, the same syntax `tcpdump` uses. Applied at the kernel level, so it's efficient even on busy interfaces.
2. `prn` – callback function invoked once per captured packet.
3. `count` – stop after N packets (0 = unlimited).
4. `timeout` – stop after N seconds regardless of count.
5. `iface` – which network interface to listen on (defaults to Scapy's chosen default route interface).

## BPF filter syntax cheat sheet

--> BPF filters are terse and worth memorizing a handful of patterns:

```
tcp                         # any TCP traffic
udp port 53                 # DNS traffic (UDP on port 53)
host 192.168.1.10           # traffic to/from a specific host
src host 192.168.1.10       # traffic FROM that host only
dst port 443                # traffic TO port 443 (HTTPS)
icmp                        # ICMP only (pings, etc.)
tcp and (port 80 or port 443)   # HTTP or HTTPS TCP traffic
arp                         # ARP requests/replies only
```

```python
from scapy.all import sniff

sniff(filter="udp port 53", prn=lambda p: p.summary(), count=5)
# Each line prints a one-line summary like:
# Ether / IP / UDP 192.168.1.5:54321 > 8.8.8.8:53 / DNS Qry "example.com."
```

## Worked example: an ICMP ping utility

```python
from scapy.all import IP, ICMP, sr1
import time

def ping(host, count=4, timeout=2):
    """Minimal ping clone using raw ICMP echo requests."""
    results = []
    for seq in range(1, count + 1):
        pkt = IP(dst=host) / ICMP(seq=seq)
        start = time.time()
        reply = sr1(pkt, timeout=timeout, verbose=0)
        elapsed_ms = (time.time() - start) * 1000

        if reply is None:
            print(f"Request {seq}: timed out")
            results.append(None)
        else:
            print(f"Reply from {reply.src}: seq={seq} time={elapsed_ms:.1f}ms")
            results.append(elapsed_ms)
        time.sleep(1)
    return results

if __name__ == "__main__":
    ping("192.168.1.1", count=4)
    # Reply from 192.168.1.1: seq=1 time=1.2ms
    # Reply from 192.168.1.1: seq=2 time=0.9ms
    # Reply from 192.168.1.1: seq=3 time=1.1ms
    # Reply from 192.168.1.1: seq=4 time=1.0ms
```

## Worked example: a simple ARP scanner for a local subnet

--> ARP scanning finds live hosts on your local subnet by asking "who has this IP?" — every host on the LAN that owns the IP responds with its MAC address. It only works within a single broadcast domain (your local subnet), never across the internet.
--> Run this only on a subnet you own/administer (your home LAN, a lab network).

```python
from scapy.all import ARP, Ether, srp

def arp_scan(target_subnet):
    """target_subnet example: '192.168.1.0/24'"""
    # Broadcast ARP request: "who has X.X.X.X? tell me (my MAC)"
    arp_request = ARP(pdst=target_subnet)
    broadcast = Ether(dst="ff:ff:ff:ff:ff:ff")
    packet = broadcast / arp_request

    # srp = send/receive at layer 2; timeout keeps it from hanging on non-existent hosts
    answered, _unanswered = srp(packet, timeout=3, verbose=0)

    devices = []
    for _sent, received in answered:
        devices.append({"ip": received.psrc, "mac": received.hwsrc})
    return devices

if __name__ == "__main__":
    hosts = arp_scan("192.168.1.0/24")
    print(f"[*] Found {len(hosts)} live hosts:")
    for h in hosts:
        print(f"    {h['ip']:<15}  {h['mac']}")
    # [*] Found 3 live hosts:
    #     192.168.1.1      aa:bb:cc:dd:ee:01
    #     192.168.1.10     aa:bb:cc:dd:ee:02
    #     192.168.1.15     aa:bb:cc:dd:ee:03
```

--> This is exactly the technique tools like `arp-scan` and nmap's `-PR` host discovery use under the hood — a full /24 scan takes seconds because ARP has no handshake, just one broadcast request and however many replies come back.

## A note on privileges and interfaces

--> All raw send/sniff operations require elevated privileges: run with `sudo` on Linux/macOS, or as Administrator with Npcap installed on Windows.
--> If Scapy picks the wrong network interface (common on multi-homed lab VMs), pass `iface="eth0"` (or the correct adapter name) explicitly to `sniff()`, `srp()`, or `sr1()`.
