# TCP Congestion Control -- Why TCP Doesn't Just Send at Full Speed

--> The Three-Way Handshake file (`02`) covered how TCP guarantees reliable, ordered delivery -- but reliability alone doesn't decide HOW FAST a TCP sender should transmit. Send too fast and you overwhelm the network path (routers drop packets, everyone's throughput suffers); send too slow and you waste available capacity. Congestion control is TCP's answer to that trade-off, entirely self-regulated by the sender based on the signals (ACKs, timeouts, duplicate ACKs) it observes.
--> The sender maintains a "congestion window" (cwnd) -- the amount of unacknowledged data it's willing to have in flight at once -- separate from the "receive window" the receiver advertises (which just reflects the RECEIVER's buffer space, not network conditions). The actual amount a sender can transmit is the smaller of the two.

# Slow Start

--> A brand-new TCP connection has no information about how much the network path can actually carry, so it starts CONSERVATIVELY: cwnd begins at a small value (historically 1 segment, modern stacks often start around 10) and DOUBLES every round-trip time an ACK confirms data arrived successfully -- exponential growth, but from a small base, so it ramps up quickly without immediately flooding an unknown path.

```
RTT 1: cwnd = 1 segment   sent, ACKed
RTT 2: cwnd = 2 segments  sent, ACKed
RTT 3: cwnd = 4 segments  sent, ACKed
RTT 4: cwnd = 8 segments  sent, ACKed
   ... doubles each RTT until it hits the "slow start threshold" (ssthresh) or a loss occurs
```

# Congestion Avoidance

--> Once cwnd reaches ssthresh (or after a loss event resets it), TCP switches from doubling to LINEAR growth -- roughly one additional segment per round-trip, rather than doubling -- probing for more available bandwidth much more cautiously now that it's near a previously observed limit.
--> **Packet loss signals congestion two different ways, handled differently:**
1. A **timeout** (no ACK at all within the expected window) is treated as a severe signal -- cwnd collapses all the way back down to 1 and slow start restarts from scratch.
2. **Three duplicate ACKs** (the receiver repeatedly acknowledging the same last-received byte because a later segment is missing) trigger "fast retransmit" -- the missing segment is resent immediately without waiting for a full timeout, and cwnd is only cut in HALF (not reset to 1) followed by linear growth again ("fast recovery") -- because duplicate ACKs still prove packets ARE getting through, just not all of them, a much milder signal than total silence.

```
cwnd
 |                    /\  <- loss (timeout): cwnd collapses to 1, slow start restarts
 |         __________/    \
 |        /                \___
 |   ____/                      \___/\___  <- loss (3 dup ACKs): cwnd halved, linear growth resumes
 |  /
 |_/______________________________________ time
   (this classic sawtooth pattern is why the algorithm family is often called "AIMD" --
    Additive Increase, Multiplicative Decrease)
```

# Nagle's Algorithm

--> Nagle's Algorithm addresses a DIFFERENT inefficiency -- applications that write tiny amounts of data at a time (a single keystroke in an old telnet session, for example) would otherwise generate a full TCP segment (with ~40 bytes of header overhead) per keystroke, wasting bandwidth on overhead relative to payload.
--> The fix: if there is already unacknowledged data in flight, TCP BUFFERS additional small writes instead of sending them immediately, and only flushes the buffer once the outstanding data is acknowledged (or enough data has accumulated to fill a full segment) -- trading a small amount of added latency for much better bandwidth efficiency on bursty, small-write traffic.
--> This is exactly why interactive/latency-sensitive applications (SSH sessions, real-time games, financial trading systems) explicitly disable it by setting the `TCP_NODELAY` socket option -- Nagle's buffering delay is the opposite of what they want, since they'd rather pay the small header overhead than wait even a few milliseconds for a keystroke or a price update to be sent.

# NAT Variants

--> The TCP/IP file (`02`) introduced NAT's basic purpose (sharing one public IP across many private devices); the actual mapping behavior comes in several distinct flavors.
--> **Static NAT** -- a fixed, permanent one-to-one mapping between one specific private IP and one specific public IP -- used when an internal server (e.g. a mail server) needs a consistent, predictable public address that outside parties can always reach at the same IP.
--> **Dynamic NAT** -- maps private IPs to public IPs drawn from a POOL of available public addresses, assigned on demand as connections are made and released when they end -- useful when an organization has more internal hosts than a strict 1:1 static mapping would need public addresses for, but still has more than one public IP to work with.
--> **PAT / NAPT (Port Address Translation, a.k.a. NAT overload)** -- the variant almost every home router actually runs: MANY private IPs share a SINGLE public IP simultaneously, disambiguated by rewriting the SOURCE PORT of each outgoing connection so the router can track which internal device a given return packet belongs to.

```
Private device A: 192.168.1.10:51000  --\
Private device B: 192.168.1.11:51000  ---+--> NAT table rewrites both to same public IP,
Private device C: 192.168.1.12:51000  --/      but DIFFERENT source ports:

NAT table:
  203.0.113.5:40001  <-->  192.168.1.10:51000   (device A's session)
  203.0.113.5:40002  <-->  192.168.1.11:51000   (device B's session)
  203.0.113.5:40003  <-->  192.168.1.12:51000   (device C's session)

A reply arriving at 203.0.113.5:40002 is unambiguously routed back to device B --
this port-based disambiguation is the entire mechanism that makes PAT work.
```

--> **CGNAT (Carrier-Grade NAT)** -- ISPs apply NAT again at THEIR level, mapping many customers' already-NAT'd private/public IPs onto a smaller pool of truly public IPv4 addresses -- a response to IPv4 exhaustion that lets an ISP serve far more customers than it has public addresses for. The security-relevant consequence: multiple UNRELATED customers can appear to share the same apparent public IP from the outside, which complicates IP-based abuse tracking/blocking (banning "an IP" for one user's bad behavior can incidentally affect other, unrelated customers behind the same CGNAT pool) and generally breaks inbound connections/port forwarding entirely, since the ISP -- not the customer -- controls that outer NAT layer.

# DHCP -- The Full DORA Process

--> DHCP (Dynamic Host Configuration Protocol) is how a device joining a network automatically obtains an IP address (plus subnet mask, default gateway, and DNS servers) without any manual configuration -- the exchange is a 4-step process abbreviated DORA.
1. **Discover** -- the joining device, which has no IP yet, broadcasts a `DHCPDISCOVER` to the entire local network (destination `255.255.255.255`, since it can't address a specific server it doesn't know the address of yet), asking "is any DHCP server out there?"
2. **Offer** -- every DHCP server that hears the broadcast responds with a `DHCPOFFER`, proposing a specific IP address and lease terms it's willing to hand out.
3. **Request** -- the client picks ONE offer (typically the first one received) and broadcasts a `DHCPREQUEST` explicitly naming that server's offered IP -- broadcast rather than unicast specifically so that any OTHER DHCP servers that also made offers see this and know their own offer was not accepted, letting them return that address to their available pool.
4. **Acknowledge** -- the chosen server responds with a `DHCPACK`, finalizing the lease and confirming the full configuration (IP, subnet mask, gateway, DNS, lease duration) -- at this point the client actually configures its network interface with the assigned address.

```
Client                                DHCP Server(s)
  |------- DHCPDISCOVER (broadcast) ------------>|   "anyone offering an address?"
  |<------ DHCPOFFER ----------------------------|   "I offer you 192.168.1.50"
  |------- DHCPREQUEST (broadcast) -------------->|   "I accept 192.168.1.50 from you"
  |<------ DHCPACK -------------------------------|   "confirmed, lease is yours for 24h"
```

--> Security relevance: a **rogue DHCP server** -- an unauthorized or malicious server answering DISCOVER broadcasts on a network it shouldn't be on -- can hand out a gateway/DNS server address it controls, silently routing a victim's entire internet traffic (and DNS resolution) through the attacker for interception -- this is why managed switches offer "DHCP snooping," a feature that only allows DHCPOFFER/DHCPACK traffic from ports explicitly trusted/configured as legitimate uplinks to the real DHCP server.
--> Lease renewal happens well before expiry (typically at 50% of the lease time) via a direct unicast `DHCPREQUEST`/`DHCPACK` exchange with the same server, without repeating the full broadcast DORA process, unless renewal fails and the client falls back to discovering again.

# SDN and Network Virtualization Overlays

--> **SDN (Software-Defined Networking)** separates the "control plane" (the logic deciding HOW traffic should be routed/forwarded) from the "data plane" (the actual hardware forwarding packets) -- instead of each switch/router making forwarding decisions independently based on locally-run protocols (like OSPF/STP above), a centralized SDN controller computes forwarding rules for the entire network and pushes them down to switches via a standard protocol (commonly OpenFlow) -- enabling programmatic, centrally-managed, rapidly-reconfigurable networks, which is exactly the technology underpinning modern cloud data centers and the software-defined virtual networks a Kubernetes cluster or cloud VPC runs on top of.
--> **VXLAN (Virtual Extensible LAN)** -- a way to run an isolated Layer 2 (Ethernet) network ON TOP of an existing Layer 3 (IP) network -- it encapsulates an entire Ethernet frame inside a UDP packet, letting virtual machines/containers on physically distant hosts (even across different data centers) behave as if they share the same local Ethernet segment. This solves classic VLANs' 4094-ID ceiling (the 12-bit VLAN ID field from earlier in this track) with VXLAN's 24-bit identifier, supporting over 16 million distinct virtual segments -- essential at the scale of a multi-tenant cloud provider where a plain 4094-VLAN limit would run out almost immediately.
--> **GRE (Generic Routing Encapsulation)** -- a simpler, more general-purpose tunneling protocol that encapsulates ANY Layer 3 protocol packet inside another IP packet, without VXLAN's specific Layer-2-over-Layer-3 focus or built-in multi-tenant ID scheme -- often used for straightforward site-to-site tunnels or as a building block other tunneling technologies (including some VPN implementations) are layered on top of. Unlike IPsec tunnel mode, plain GRE provides NO encryption on its own -- it's often paired with IPsec (GRE-over-IPsec) specifically to add the confidentiality GRE itself lacks.

```
VXLAN encapsulation (an overlay Ethernet frame riding inside an ordinary IP/UDP packet):

[Outer IP header][Outer UDP header][VXLAN header w/ 24-bit VNI][Original Ethernet frame (payload)]
 ^-- real, physical                                             ^-- the "virtual" L2 frame the VM
     network routing this                                           thinks it's sending directly
     like any other UDP packet                                      on its own local LAN segment
```

# CDN Networking

--> A CDN (Content Delivery Network) serves content from servers geographically/network-topologically close to each requesting user, rather than every user round-tripping to one origin server -- reducing latency and absorbing traffic spikes.
--> **Anycast** -- the same IP address is announced via BGP from MANY physically distinct data centers simultaneously -- normal internet routing (each ISP naturally preferring the shortest/cheapest BGP path) then automatically sends a given user's request to whichever anycast location is "closest" in routing terms, with no application-level redirection needed at all. This is also how many large public DNS resolvers (e.g. `8.8.8.8`) and DDoS-mitigation services scale -- attack traffic aimed at the one anycast IP gets naturally spread across many physical locations instead of overwhelming a single data center.
--> **GeoDNS** -- a DNS server that answers the SAME hostname query with DIFFERENT IP addresses depending on the geographic location (inferred from the resolver's IP) of whoever's asking -- a more explicit, DNS-layer alternative/complement to anycast for steering users toward a nearby edge server.
--> **Origin shielding** -- an additional caching layer sitting BETWEEN the CDN's many edge locations and the actual origin server -- when several edge locations all experience a cache miss for the same content, instead of every one of them separately hitting the origin server (multiplying origin load by the number of edge locations), they all request through one designated "shield" location, which fetches once from the origin and then serves all the requesting edges -- protecting the origin from the very traffic-absorption problem the CDN exists to solve in the first place.

# Email Protocol Mechanics -- SMTP, POP3, IMAP

--> Three distinct protocols cover different parts of the email lifecycle, and conflating them is a common source of confusion.
--> **SMTP (Simple Mail Transfer Protocol, port 25/587)** -- used for SENDING mail and for relaying it between mail servers -- when you hit send, your client talks SMTP to your provider's outgoing server, and that server in turn uses SMTP again to hand the message off to the RECIPIENT's mail server (a store-and-forward relay chain, not a direct client-to-client connection).
--> **POP3 (Post Office Protocol v3, port 110/995)** -- used for RETRIEVING mail, with a "download and (usually) delete from server" model -- historically designed around a single device fully owning its local copy of the mailbox; checking mail from a second device typically wouldn't see messages already pulled down (and possibly deleted from the server) by the first.
--> **IMAP (Internet Message Access Protocol, port 143/993)** -- also used for RETRIEVING mail, but keeps the authoritative mailbox ON THE SERVER and lets multiple devices see a synchronized, consistent view (read/unread status, folders, deletions) -- the model virtually all modern email clients/webmail actually use, since checking mail from a phone, a laptop, and a webmail tab simultaneously all need to agree on the same mailbox state.

```
Sending:                 You --SMTP--> Your provider's server --SMTP--> Recipient's server
Retrieving (IMAP today): Your phone  <--IMAP--\
                         Your laptop <--IMAP---+--  Recipient's server (mailbox stays here,
                         Webmail tab <--IMAP--/      all devices sync against the same state)
```

--> The 465/587/995/993 "secure" port variants layer TLS underneath these protocols (SMTPS, POP3S, IMAPS) -- the same encrypt-the-existing-protocol pattern as the `S` in HTTPS, and directly related to the S/MIME secure protocol variant covered in `07 NAS, SAN, IPAM and Secure Protocol Variants.md`.

# Cross-References

--> TCP congestion control extends the Three-Way Handshake and TCP-vs-UDP material in `02 TCP-IP, Ports and IP Addressing.md`; NAT variants extend that same file's basic NAT section. SDN/VXLAN connects to the "Named Virtualization Platforms" section of `07 NAS, SAN, IPAM and Secure Protocol Variants.md`. DHCP's rogue-server risk is the same category of "unauthenticated broadcast trust" problem discussed for ARP and BGP/DTP in `08 BGP, OSPF, VLANs and VPN Architectures.md`.
