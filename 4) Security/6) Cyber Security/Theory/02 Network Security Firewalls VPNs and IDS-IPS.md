### Network Security - Firewalls, VPNs, and IDS-IPS

--> Network security is about controlling and monitoring traffic that flows in and out of a network so that only legitimate traffic is allowed and malicious/unwanted traffic is blocked or flagged.
--> Almost every network defense tool exists to answer one of two questions: "Should this traffic be allowed?" or "Is this traffic behaving suspiciously?"

## Firewalls

--> A firewall is a device (hardware or software) that monitors and controls incoming and outgoing network traffic based on a defined set of rules.
--> Think of it as a bouncer at a club door — it checks everyone trying to get in (or out) against a guest list (the rules) and decides: allow, block, or log.

There are three main generations/types of firewalls:

1. Packet-Filtering Firewall
   --> The oldest and simplest type. It inspects each packet individually and checks it against rules based on source IP, destination IP, source port, destination port, and protocol (TCP/UDP).
   --> It has no memory — each packet is judged in isolation, with no awareness of whether it belongs to an existing, legitimate connection.
   --> Example rule: "Block all inbound traffic to port 23 (Telnet) from any source."
   --> Weakness: since it can't tell if a packet is part of a real ongoing conversation, it's easier to trick with spoofed packets.

2. Stateful Inspection Firewall
   --> Tracks the state of active connections (a "state table") — so it knows if an incoming packet is part of an already-established, legitimate connection or a brand-new (and possibly unsolicited) one.
   --> Example: If your laptop opens a connection to a website on port 443, the firewall remembers that connection and allows the return traffic back to your laptop automatically — without needing an explicit rule for the return path.
   --> This is the type of firewall used in most home routers and enterprise networks today.

3. Next-Generation Firewall (NGFW)
   --> Combines stateful inspection with deeper capabilities: application-layer inspection (it can tell Netflix traffic apart from Zoom traffic even if both use HTTPS), intrusion prevention (IPS built in), deep packet inspection (DPI), and identity-aware rules (e.g., "Only allow the Finance AD group to reach this server").
   --> Examples of real products: Palo Alto Networks, Fortinet FortiGate, Cisco Firepower.

==> Rules and ACLs (Access Control Lists)
--> Firewall behavior is defined by a rule set, often implemented as an ACL — an ordered list of "allow" or "deny" statements.
--> Rules are typically processed top-to-bottom, and the first matching rule wins (so rule order matters a lot).
--> A rule usually specifies: source, destination, port/protocol, and action.

Example simple ACL (conceptual, similar to Cisco syntax):
```
access-list 101 permit tcp any host 10.0.0.5 eq 443     # allow HTTPS to the web server
access-list 101 permit tcp any host 10.0.0.5 eq 80      # allow HTTP to the web server
access-list 101 deny   tcp any any eq 23                # block all Telnet (insecure, plaintext)
access-list 101 deny   ip any any                       # implicit/explicit "deny all" catch-all at the end
```
--> Best practice: always end an ACL with an explicit "deny all" — this follows the principle of least privilege (default deny, only allow what's explicitly needed).

## VPNs (Virtual Private Networks)

--> A VPN creates an encrypted "tunnel" between your device and a remote network/server over the public internet, so your traffic is protected from anyone snooping on the network in between.

What a VPN protects:
--> Confidentiality of your traffic — an ISP, a coffee shop Wi-Fi eavesdropper, or a MITM attacker on the same network sees only encrypted gibberish, not your actual data.
--> Integrity of your traffic — the encrypted tunnel typically includes checks that detect if data was tampered with in transit.
--> It also masks your real IP address from the destination server (the server sees the VPN server's IP, not yours), which is a privacy benefit but not the primary security purpose.

What a VPN does NOT protect against:
--> Malware already on your device.
--> A malicious website itself (a VPN doesn't stop you from being phished or downloading a trojan).
--> A weak password or a poorly secured VPN server itself.

==> Tunneling Concept
--> "Tunneling" means wrapping your original data packet inside another packet for transport, so it can travel safely/privately across a network that wasn't designed to carry it securely.
--> It's like putting a sealed, unmarked envelope (your real data) inside a second, addressed envelope (the tunnel) — the postal service (the internet) only sees the outer envelope.

==> IPsec VPN vs SSL VPN (high level)
--> IPsec (Internet Protocol Security) VPN:
    --> Operates at the network layer (Layer 3). It encrypts entire IP packets and is commonly used for site-to-site VPNs (connecting two office networks/branches together permanently).
    --> Requires a dedicated VPN client/software configured with specific settings (pre-shared keys or certificates) — less flexible for random remote access, but very robust for fixed infrastructure links.
--> SSL VPN (uses TLS/SSL):
    --> Operates at a higher layer, often accessible through a normal web browser or a lightweight client.
    --> Common for remote access VPNs (an employee connecting from home to the company network) because it's easier to deploy — no complex client configuration, works through most firewalls since it just looks like HTTPS traffic on port 443.
--> Rule of thumb: IPsec = permanent office-to-office tunnels; SSL VPN = flexible remote-worker access.

## IDS vs IPS

--> IDS (Intrusion Detection System) – Monitors network/system traffic for suspicious activity and ALERTS security teams. It is passive — it does not block anything itself.
--> IPS (Intrusion Prevention System) – Does everything an IDS does, but sits inline in the traffic path and can actively BLOCK or drop malicious traffic in real time. It is active.

--> Analogy: IDS is a security camera that calls the guard when it sees something suspicious. IPS is a locked gate that slams shut automatically when it sees the same thing.

Both IDS and IPS use two main detection methods:

1. Signature-based detection
   --> Compares traffic/files against a database of known attack "signatures" (patterns) — similar to how antivirus works.
   --> Very accurate for known threats, very fast, low false-positive rate.
   --> Weakness: completely blind to brand-new, unknown attacks (zero-days) because there's no signature for them yet.
   --> Example: A signature might be "if a packet payload contains the exact byte sequence used by the EternalBlue exploit, flag it."

2. Anomaly-based detection
   --> Builds a baseline of "normal" behavior for the network/system, then flags anything that deviates significantly from that baseline.
   --> Can catch brand-new/unknown attacks (since it doesn't need a pre-existing signature).
   --> Weakness: higher false-positive rate (e.g., a legitimate but unusual traffic spike during a big sale event might get flagged), and it needs time/tuning to learn what "normal" looks like.
   --> Example: A finance server that normally sees 10 logins/day suddenly sees 500 login attempts within a minute — flagged as anomalous, likely a brute-force attempt.

--> Real IDS/IPS tools: Snort, Suricata (both open-source, signature + rule based), Zeek (network analysis framework).

## DMZ (Demilitarized Zone)

--> A DMZ is a separate, isolated network segment that sits between your trusted internal network and the untrusted public internet.
--> Public-facing servers (web servers, mail servers, DNS servers) live in the DMZ — because they must be reachable from the internet, they are the most likely to be attacked.
--> If an attacker compromises a server in the DMZ, firewall rules prevent them from directly reaching the trusted internal network (where the real sensitive data, like the HR database or domain controller, lives).
--> Typical layout: Internet <--> Firewall <--> DMZ (web server, mail server) <--> Firewall <--> Internal Network (databases, employee workstations).
--> This is a real-world application of defense in depth and network segmentation — you never expose your crown jewels directly to the internet.

## Common Network Attacks

==> DoS and DDoS
--> DoS (Denial of Service) – A single attacker overwhelms a system/service with traffic or requests so legitimate users can't use it. Violates Availability.
--> DDoS (Distributed Denial of Service) – The same idea, but using thousands/millions of compromised devices (a botnet) simultaneously, making it far harder to block (you can't just block one IP).
--> Example: A botnet of hacked IoT cameras (like the Mirai botnet) floods a DNS provider with junk requests, taking down major websites like Twitter and Netflix that depended on it.

==> Man-in-the-Middle (MITM)
--> An attacker secretly positions themselves between two communicating parties, able to intercept, read, and possibly modify the traffic — while both original parties believe they're talking directly to each other.

--> ARP Spoofing (a common way to set up a MITM on a local network):
    --> ARP (Address Resolution Protocol) maps IP addresses to MAC addresses on a local network. It was designed with zero authentication.
    --> An attacker sends fake ARP messages claiming "I am the router" (or "I am victim X"), poisoning the ARP tables of nearby devices.
    --> Result: traffic that should go to the real router/victim gets routed through the attacker's machine instead, letting them intercept everything.

--> Session Hijacking:
    --> After a user has already authenticated to a website (e.g., logged into their bank), the attacker steals their session token/cookie (often via sniffing on an unencrypted connection, or via XSS) and uses it to impersonate the victim without needing their password at all.
    --> Example: An attacker on the same public Wi-Fi captures an unencrypted session cookie for a forum, then uses that exact cookie in their own browser to be logged in as the victim instantly.

==> Packet Sniffing
--> The act of capturing and inspecting raw network traffic as it passes through a network interface, using tools like Wireshark or tcpdump.
--> Sniffing itself is a neutral technique (network admins use it to troubleshoot), but attackers use it to passively harvest credentials, session cookies, and sensitive data if traffic isn't encrypted.
--> Example: An attacker on an open/unencrypted Wi-Fi network runs Wireshark and filters for HTTP POST requests, capturing plaintext usernames and passwords typed into a login form that doesn't use HTTPS.
--> Defense: always use encrypted protocols (HTTPS, SFTP, SSH) so that even if traffic is sniffed, the captured data is useless ciphertext.

## Tying It Together

--> A firewall decides what traffic is allowed in the first place (the gate).
--> An IDS/IPS watches the traffic that does get through and reacts to bad behavior (the guard patrolling inside).
--> A VPN protects traffic while it travels across untrusted networks (the armored car).
--> A DMZ limits the blast radius if your public-facing systems do get compromised (the outer courtyard vs the inner vault).
--> Together these form the "network" layer of the defense-in-depth model covered in the fundamentals chapter.
