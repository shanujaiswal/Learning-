# Why Packet Capture Matters

--> Every concept covered so far in this Networking folder (OSI layers, TCP handshakes, DNS resolution, HTTP requests) is invisible in normal use -- packet capture makes it directly OBSERVABLE, which is essential both for troubleshooting network issues and for the security work covered throughout the rest of this Security folder (the Python packet-crafting/sniffing file, the Ethical Hacking track's enumeration and wireless attacks, and defensive network monitoring in the Cyber Security track all assume this skill).

# Wireshark -- The Standard GUI Packet Analyzer

--> Wireshark captures traffic passing through a network interface and decodes it, layer by layer, into a human-readable view -- showing exactly what OSI layer 2-7 headers and payloads look like for real traffic, rather than only in a textbook diagram.
--> Requires either capturing on your own machine's interface, or a position on the network actually able to see the traffic (a hub, a switch's mirrored/SPAN port, or being on the same wireless network in monitor mode) -- you can't casually see traffic between two OTHER machines on a modern switched network without one of these positions, which is exactly why switches (Layer 2, forwarding only to the intended port) are more secure than hubs by design.

# Capture Filters vs Display Filters

--> Capture filters -- applied BEFORE capturing, using Berkeley Packet Filter (BPF) syntax -- limit what's actually recorded (useful to avoid capturing irrelevant/overwhelming traffic volume).

```
host 192.168.1.10          # Only traffic to/from this IP
port 443                    # Only traffic on this port
tcp and port 80             # Combine conditions
```

--> Display filters -- applied AFTER capturing, using Wireshark's own filter syntax -- narrow down what's SHOWN from an already-captured file, without needing to re-capture.

```
ip.addr == 192.168.1.10
tcp.port == 443
http.request.method == "POST"
dns.qry.name contains "example"
```

# Reading a Packet -- Following the Encapsulation Model

--> Wireshark displays each captured packet with an expandable tree exactly matching the encapsulation concept from the OSI Model file -- Frame → Ethernet header → IP header → TCP/UDP header → Application-layer payload (HTTP, DNS, etc.), each layer's header visible and clickable independently.
--> "Follow TCP Stream" -- reassembles all packets belonging to one TCP conversation into the full readable exchange (e.g. an entire unencrypted HTTP request/response) -- a direct, visceral demonstration of why unencrypted protocols expose everything to anyone who can capture the traffic.

# tcpdump -- The Command-Line Equivalent

--> `tcpdump` captures traffic from the terminal, using the same BPF capture-filter syntax as Wireshark -- useful on servers without a GUI, or for quickly capturing a file to analyze later in Wireshark.

```bash
tcpdump -i eth0 port 443 -w capture.pcap    # Capture to a file for later analysis
tcpdump -i eth0 host 192.168.1.10 -A         # Print packet contents (ASCII) live to the terminal
```

# Why Encryption Changes What You Can See

--> Capturing HTTPS/TLS traffic (covered in the Cryptography track) shows encrypted bytes, not readable content -- packet capture reveals metadata (who's talking to whom, when, how much data, which protocol) even for encrypted traffic, but not the actual payload content, unless you control one endpoint and can access its TLS session keys.
--> This is precisely why HTTPS matters as a baseline protection, and also why network-level traffic ANALYSIS (used defensively by SIEM/IDS systems covered in the Cyber Security track) still has real value even against encrypted traffic -- metadata and traffic patterns alone reveal a great deal.

# Legal and Ethical Note

--> Capturing traffic on a network you don't own or lack explicit authorization to monitor is illegal in most jurisdictions -- this skill is intended strictly for your own lab environments, authorized penetration testing engagements, or legitimate network administration of infrastructure you're responsible for.
