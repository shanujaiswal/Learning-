# Routing Protocols -- Interior vs Exterior

--> Routing protocols fall into two families depending on WHERE they operate: Interior Gateway Protocols (IGPs) route WITHIN a single organization's network (an "Autonomous System"), while Exterior Gateway Protocols route BETWEEN autonomous systems -- i.e. across the actual internet backbone. OSPF is the classic IGP; BGP is the (only widely used) EGP.
--> An Autonomous System (AS) is a network (or group of networks) under one administrative control -- an ISP, a large enterprise, a cloud provider -- each identified by a unique AS Number (ASN), e.g. `AS15169` (Google), `AS13335` (Cloudflare). ASNs are what BGP actually routes between, not individual IP addresses.

# OSPF -- Link-State Routing Inside an AS

--> OSPF (Open Shortest Path First) is a link-state protocol -- every router builds a complete map of the ENTIRE network topology (every router, every link, every cost) by flooding Link-State Advertisements (LSAs) to all other routers in the same "area", then independently runs Dijkstra's shortest-path algorithm on that map to compute the best route to every destination.
--> Because every router has the full topology, OSPF converges fast after a link fails -- a router notices the change, floods an updated LSA, and every router recomputes -- typically within seconds.
--> Routers periodically exchange "Hello" packets to discover neighbors and confirm links are still alive; missing several Hellos in a row triggers a topology change and a re-flood.

```
Link-state idea: every router knows the WHOLE map, then computes shortest paths itself.

Router A's view after flooding:          A --(cost 1)-- B
                                           |              |
                                        (cost 5)       (cost 2)
                                           |              |
                                           C --(cost 1)-- D

A independently runs Dijkstra on this graph -> shortest path A->D is A-B-D (cost 3), not A-C-D (cost 6)
```

# BGP -- Path-Vector Routing Between ASes

--> BGP (Border Gateway Protocol) is a path-vector protocol, not link-state -- a BGP router does NOT know the internet's full topology. Instead, each router advertises to its neighbors (called "BGP peers") only the AS-level PATH it would use to reach a given IP prefix, e.g. "to reach `8.8.8.0/24`, go through me, then AS3356, then AS15169."
--> Every AS along that path prepends its own ASN before re-advertising it onward -- so by the time a route reaches you, you have the full ordered list of ASes it will traverse (the "AS path"), and you pick the best available path using BGP's decision process (shortest AS path is one factor among several -- local policy, next-hop cost, and commercial peering agreements often override pure path length).
--> This is fundamentally a matter of TRUST between organizations -- BGP has no built-in cryptographic verification that an AS advertising a route to a prefix is actually authorized to route that prefix. That absence of authentication is the root cause of the two attack classes below.

```
Path-vector idea: routers exchange the PATH itself, not a map to derive a path from.

AS100 advertises: "8.8.8.0/24 reachable via path [AS100]"
AS200 hears it, re-advertises: "8.8.8.0/24 reachable via path [AS200, AS100]"
AS300 hears THAT, re-advertises: "8.8.8.0/24 reachable via path [AS300, AS200, AS100]"

Any AS receiving multiple paths to the same prefix picks the "best" one by BGP's decision rules,
then propagates only that chosen path onward -- not the whole set of alternatives it heard.
```

# BGP Route Hijacking and Route Leaks

--> **Route hijacking** -- an AS advertises a route for an IP prefix it does NOT actually own or is not authorized to route -- either accidentally (a fat-fingered router config leaking an internal, overly-specific route to the whole internet) or maliciously (an attacker's AS claims to own a victim's address space to intercept, black-hole, or eavesdrop on their traffic). Because BGP trusts what neighbors tell it, other ASes will believe the false advertisement and start routing that victim's traffic through the attacker.
--> A famous real-world pattern: advertising a MORE SPECIFIC prefix (e.g. `/24` instead of the legitimate owner's `/16`) -- BGP's longest-prefix-match rule means routers prefer the more specific route even from an illegitimate source, silently pulling traffic away from the real destination without the victim's own advertisement changing at all.
--> **Route leak** -- a distinct but related failure where an AS improperly re-advertises routes it learned from one peer to another peer in violation of normal routing agreements (e.g. leaking a route learned from a customer out to the entire internet as if it were a valid transit path) -- usually a misconfiguration rather than malice, but with the same practical effect of misdirecting traffic through an unintended path.
--> **RPKI (Resource Public Key Infrastructure)** is the current mitigation -- prefix owners cryptographically sign a Route Origin Authorization (ROA) stating which ASN is allowed to originate a given prefix, and RPKI-validating routers can then reject or de-prioritize BGP announcements that don't match a valid ROA. Adoption is growing but far from universal, which is why hijacks/leaks still happen periodically at internet scale.

# BGP and OSPF Convergence -- Why Speed Differs

--> **OSPF convergence** is fast (seconds) because it's link-state -- one flooded update gives every router everything it needs to instantly recompute.
--> **BGP convergence** is much slower (can be minutes) because it's path-vector and internet-scale -- a change has to propagate AS-by-AS, each router re-evaluating and re-advertising only after receiving the update from its own neighbors, and BGP intentionally rate-limits how fast it re-announces changes ("route flap damping") to avoid overwhelming the internet's routing tables with churn from a single unstable link.
--> This convergence gap is exactly why a BGP hijack can cause real damage for minutes before operators notice and manually correct it, whereas an OSPF-only failure inside a single corporate network self-heals almost immediately.

# VLANs -- Segmenting a Switch Logically

--> A VLAN (Virtual LAN) lets a single physical switch behave as if it were several separate switches -- devices on VLAN 10 cannot see broadcast traffic from devices on VLAN 20, even though they're plugged into the exact same physical hardware. This is the standard way to isolate, say, a Finance department's traffic from a Guest Wi-Fi network without running separate cabling.
--> **Access port** -- connects to an end device (a PC, a printer) and belongs to exactly ONE VLAN -- the switch adds/removes the VLAN tag transparently, so the end device never even knows VLANs exist.
--> **Trunk port** -- connects switch-to-switch (or switch-to-router/hypervisor) and carries traffic for MULTIPLE VLANs over a single physical link, distinguishing which frame belongs to which VLAN via an 802.1Q tag.

# 802.1Q Tagging

--> The 802.1Q standard inserts a 4-byte tag into the Ethernet frame header, containing (among other fields) a 12-bit VLAN ID (supporting VLAN IDs 1-4094) -- this tag is added when a frame enters a trunk link and stripped again before it reaches an access-port end device, so untagged end devices are unaffected.

```
Untagged Ethernet frame:  [Dst MAC][Src MAC][Type/Len][Payload][CRC]
802.1Q tagged frame:      [Dst MAC][Src MAC][802.1Q Tag: VLAN ID][Type/Len][Payload][CRC]
                                              ^-- this is what a trunk port reads to know
                                                  which VLAN this frame belongs to
```

--> The "native VLAN" on a trunk is the one exception -- frames for it are sent UNTAGGED across the trunk by convention, which is precisely the gap VLAN hopping abuses below.

# VLAN Hopping Attacks

--> **Switch spoofing** -- an attacker's device negotiates trunking directly with the switch (many switches historically auto-negotiated trunk mode via DTP, Dynamic Trunking Protocol, if asked) -- once the attacker's port becomes a trunk, it receives tagged traffic for EVERY VLAN on that switch, defeating the isolation VLANs were supposed to provide. Mitigation: disable DTP / force access ports to never trunk-negotiate.
--> **Double tagging** -- the attacker crafts a frame with TWO 802.1Q tags stacked: an outer tag matching the native VLAN (which the first switch strips as "normal" untagged native traffic) and an inner tag for the VICTIM VLAN the attacker wants to reach. The first switch strips only the outer tag and forwards the frame along the trunk still carrying the inner tag, and a downstream switch reads that inner tag and delivers the frame straight into the victim VLAN -- effectively smuggling a packet across a VLAN boundary it should never have crossed. Mitigation: never use VLAN 1 (or any in-use VLAN) as the native VLAN on trunks, and explicitly tag the native VLAN too rather than relying on the untagged convention.
--> Both attacks matter for the same underlying reason -- VLANs are a LOGICAL, not cryptographic, boundary; treating VLAN separation as a substitute for a firewall between genuinely sensitive segments is a common and exploitable misconception.

# VPN Architectures -- Tunnel vs Transport, and the Major Variants

--> Building on the VPN definition from the TCP/IP file (an encrypted tunnel making a device appear to be on a remote network for all its traffic) -- the actual protocols implementing that tunnel differ substantially in scope, performance, and where the encryption boundary sits.

## IPsec -- Tunnel Mode vs Transport Mode

--> IPsec is a suite of protocols (AH -- Authentication Header, and ESP -- Encapsulating Security Payload) that can protect IP traffic in two distinct modes:
--> **Transport mode** -- encrypts/authenticates only the PAYLOAD of the original IP packet, leaving the original IP header exposed -- used for END-TO-END protection between two hosts that both understand IPsec directly (e.g. securing traffic between two specific servers), since intermediate routers still need the real header to route it.
--> **Tunnel mode** -- encrypts/authenticates the ENTIRE original IP packet (header included) and wraps it inside a brand new outer IP packet with a new header -- used for GATEWAY-to-gateway VPNs, since the original source/destination addresses are hidden from anything between the two gateways, and the outer header only reveals the gateways' own IPs.

```
Transport mode:  [New: same original IP header][Encrypted: original payload]
Tunnel mode:      [New outer IP header][Encrypted: entire original IP packet incl. its header]
```

--> **IKE (Internet Key Exchange)** is the protocol IPsec uses to negotiate the actual encryption keys and algorithms before the tunnel carries any real traffic -- IKEv1 (older, two phases: authenticate peers, then negotiate the actual IPsec security association) and IKEv2 (modern, faster, better NAT traversal and mobility support -- e.g. a phone can keep the same IPsec session while switching from Wi-Fi to cellular).

## SSL/TLS VPNs

--> Instead of operating at the IP layer like IPsec, an SSL/TLS VPN tunnels traffic inside a standard TLS connection (the same protocol securing HTTPS) -- often delivered through a browser (a "clientless" web portal reverse-proxying specific internal apps) or a lightweight client establishing a full-tunnel TLS session.
--> Advantage over IPsec: TLS traffic looks like ordinary HTTPS on port 443, so it traverses restrictive firewalls/NATs far more easily than IPsec (which historically struggled behind NAT and is often blocked outright by strict corporate/ISP firewalls).

## WireGuard

--> A newer VPN protocol built around a deliberately small, modern, and auditable codebase (a few thousand lines vs IPsec's much larger surface) using a fixed, opinionated set of state-of-the-art cryptographic primitives (Curve25519, ChaCha20, Poly1305, BLAKE2) rather than IPsec/IKE's negotiable menu of algorithms -- fewer choices means fewer misconfiguration and downgrade-attack possibilities.
--> Operates over UDP with a very lean handshake, generally giving noticeably better throughput and connection re-establishment speed (e.g. after a mobile device changes IP address) than IPsec or OpenVPN.

## Site-to-Site vs Remote-Access VPNs

--> **Site-to-site** -- connects two entire NETWORKS (e.g. a branch office's LAN to company HQ's LAN) through gateway devices at each end -- individual employee devices don't run any VPN software at all; the tunnel is transparent infrastructure between the two sites.
--> **Remote-access** -- connects a single INDIVIDUAL device (an employee's laptop) into a remote network -- requires client software/configuration on that specific device, and is what "connect to the corporate VPN before working from home" refers to.

## Split Tunneling

--> By default, a remote-access VPN can be configured as "full tunnel" -- ALL of the device's traffic, including ordinary web browsing, is routed through the encrypted tunnel and out through the corporate network's internet connection.
--> **Split tunneling** instead routes only TRAFFIC DESTINED FOR THE CORPORATE NETWORK through the tunnel, letting everything else (Netflix, general web browsing) go directly out the device's own local internet connection -- better performance and less load on corporate infrastructure, but a security trade-off: general internet traffic on that device is no longer protected/monitored by the corporate network's security controls, and a compromised "split" device sitting on both the open internet and the VPN tunnel simultaneously is a more attractive pivot point for an attacker than a fully-tunneled one.

# Spanning Tree Protocol (STP) -- Preventing Layer 2 Loops

--> Ethernet switches flood frames with an unknown destination out every port, and broadcast/multicast frames ALWAYS go out every port -- if a network has a redundant physical link forming a loop between switches, those frames circle forever, duplicating endlessly and consuming all available bandwidth within seconds (a "broadcast storm"). Redundant links are desirable for fault tolerance, but a loop is fatal, so something has to disable the redundant path until it's actually needed.
--> **Root bridge election** -- every switch in the network sends BPDUs (Bridge Protocol Data Units) advertising a "bridge ID" (priority + MAC address); the switch with the lowest bridge ID becomes the "root bridge" -- the logical center of the tree that every other switch calculates its shortest path toward.
--> Every OTHER switch then determines, for each of its ports, whether that port is on the best (lowest-cost) path back to the root -- ports that ARE part of the best path become "forwarding" ports; redundant ports that would create a loop are put into "blocking" state, where they still listen for BPDUs but do not forward any traffic.
--> If the active link/switch fails, STP recalculates and transitions a previously-blocked port into forwarding to restore connectivity -- classic STP takes tens of seconds to do this (a real operational drawback), which is why Rapid STP (RSTP, 802.1w) exists and converges in a few seconds instead by pre-negotiating alternate/backup port roles.

```
        [Root Bridge]
         /         \
    (forwarding) (forwarding)
       /               \
  [Switch A]------[Switch B]
       (this direct link between A and B is REDUNDANT -- one side is put into
        BLOCKING state so no loop forms, but stands ready if the path to root fails)
```

--> Security relevance: an attacker introducing a rogue switch that claims a very low bridge ID can win root bridge election and pull traffic through their device (an L2 analog of BGP hijacking) -- "BPDU Guard" and "Root Guard" are switch features that shut down or ignore BPDUs on ports where a root bridge election attempt is not expected (e.g. access ports facing end-user devices, which should never legitimately be trying to become the root).

# Firewalls and ACLs as Network Devices

--> **ACL (Access Control List)** -- the simplest network filtering mechanism, a router/switch rule list matching packets by source/destination IP, port, and protocol, allowing or denying each -- stateless: each packet is evaluated in isolation with no memory of prior packets, so a return-traffic rule generally has to be added explicitly in the opposite direction.
--> **Stateless firewall** -- functions much like an ACL: filters based on the header fields of each individual packet, with no awareness of which connection a packet belongs to.
--> **Stateful firewall** -- tracks active connections in a state table (source/dest IP+port, protocol, current TCP state) -- once an outbound connection is permitted, the firewall automatically allows the matching RETURN traffic without a separate explicit rule, and can reject packets that don't fit any known connection's expected state (e.g. an ACK arriving with no matching prior SYN). This is the default assumption behind almost all modern perimeter firewalls.
--> **Packet-filtering firewall** (stateless or stateful, as above) inspects only headers, never the actual payload/application content.
--> **Proxy firewall (application-layer gateway)** -- terminates the client's connection itself and opens a SEPARATE connection to the real destination on the client's behalf, actually parsing the application-layer protocol (e.g. understanding HTTP requests, not just "TCP port 80 traffic") -- this lets it enforce much finer content-aware rules (block specific URLs, strip malicious script content) at the cost of higher latency and resource use than simply forwarding packets, since every connection is fully proxied rather than passed through.

# Cross-References

--> This file builds directly on `02 TCP-IP, Ports and IP Addressing.md` (NAT, the VPN definition this file expands on) and `01 Networking Fundamentals and the OSI Model.md` (broadcast domains, which VLANs subdivide, and the network devices list, which the firewall/ACL section refines).
--> VLAN hopping and BGP hijacking both illustrate a recurring theme across this track: a protocol built on IMPLICIT TRUST between infrastructure components (a switch trusting a DTP request, a router trusting a BGP peer's advertisement) is exploitable specifically because it was never designed to authenticate the party on the other end -- the same lesson as ARP's lack of authentication, covered when packet capture/sniffing tools are used to observe LAN traffic in `06 Packet Capture Fundamentals with Wireshark.md`.
