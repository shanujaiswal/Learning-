# What Is a Computer Network

--> A network is a set of devices connected together to exchange data -- the internet is simply the largest example, a network of networks.
--> Every security topic later in this folder (packet sniffing, privilege escalation over a network, wireless attacks, firewalls) assumes you know how data actually moves between two machines -- this is that foundation.

# Network Types -- LAN, MAN and WAN

--> LAN (Local Area Network) -- covers a small physical area (a home, office, single building) -- typically owned/managed by a single organization, high speed, low latency.
--> MAN (Metropolitan Area Network) -- spans a city or campus -- larger than a LAN, smaller than a WAN, e.g. connecting multiple office branches across a city.
--> WAN (Wide Area Network) -- spans a large geographic area (a country, or the entire globe) -- the internet itself is the largest WAN. Typically slower/higher-latency than a LAN since it relies on third-party infrastructure (ISPs, undersea cables, satellite links) rather than cabling you fully control.
--> PAN (Personal Area Network) -- very short range, around a single person/device -- Bluetooth, NFC.

# Network Topologies -- How Devices Are Physically/Logically Arranged

--> Topology describes the ARRANGEMENT of connections between devices -- it affects cost, fault tolerance, and how failures/attacks propagate.

| Topology | Layout | Strength | Weakness |
|---|---|---|---|
| Bus | All devices share one central cable | Cheap, simple to install | One cable break takes down the whole network; performance degrades as devices are added |
| Star | Every device connects to one central hub/switch | Easy to add/remove devices; one device failing doesn't affect others | Central hub/switch failing takes down the entire network -- a single point of failure |
| Ring | Each device connects to exactly two neighbors, forming a loop | Data can travel in a predictable, orderly path | One broken link can disrupt the entire ring (unless dual-ring redundancy is used) |
| Mesh | Every device connects directly to every (or many) other devices | Highly fault-tolerant -- many redundant paths | Expensive and complex to wire/maintain as the network grows |
| Hybrid | A combination of the above (e.g. multiple star networks linked by a bus) | Flexible, matches real-world mixed needs | More complex to design and troubleshoot |

--> Most real-world office/home networks are physically a **star topology** (devices cabled to a central switch/router) that then connects into the internet, which is itself best described as a **mesh** of interconnected networks with many redundant paths between any two points -- this redundancy is a deliberate design goal (the internet's origins trace back to wanting a network that survives partial destruction).
--> Security relevance -- a star topology's central switch is a natural chokepoint for both defense (place a firewall/IDS there to monitor all traffic) and attack (compromising or DoS-ing that single point disrupts everyone behind it).

# The OSI Model -- 7 Layers

--> A conceptual model describing how data moves from an application on one machine to an application on another, broken into 7 layers, each with a distinct job. Real-world protocols don't map perfectly 1:1, but it's the standard shared vocabulary for discussing networking.

| Layer | Name | Job | Examples |
|---|---|---|---|
| 7 | Application | What the user-facing program actually does | HTTP, DNS, FTP, SMTP |
| 6 | Presentation | Data format/encoding, encryption | TLS/SSL, JPEG, ASCII |
| 5 | Session | Establishes/manages a connection session | Session tokens, NetBIOS |
| 4 | Transport | Reliable/unreliable delivery, ports | TCP, UDP |
| 3 | Network | Logical addressing, routing between networks | IP, ICMP |
| 2 | Data Link | Physical addressing within one local network | Ethernet, MAC addresses, switches |
| 1 | Physical | Actual bits over a medium | Cables, radio waves, voltage |

--> Mnemonic: "**P**lease **D**o **N**ot **T**hrow **S**ausage **P**izza **A**way" (Physical, Data Link, Network, Transport, Session, Presentation, Application).
--> In practice, most real protocol stacks are described with the simpler 4-layer TCP/IP model (covered next file) -- OSI is mainly useful as a teaching/troubleshooting framework ("is this a Layer 2 switching problem or a Layer 3 routing problem?").

# Encapsulation -- How Data Actually Travels

--> As data moves DOWN the layers on the sending machine, each layer wraps the data from the layer above with its own header (and sometimes a footer) -- this is called encapsulation.
--> Application data → wrapped with a TCP header (becomes a "segment") → wrapped with an IP header (becomes a "packet") → wrapped with an Ethernet header (becomes a "frame") → sent as raw bits.
--> The receiving machine does the reverse (de-encapsulation), stripping each header as it moves UP the layers, until only the original application data remains.

```
[ Ethernet Header [ IP Header [ TCP Header [ HTTP Data ] ] ] ]
     Layer 2            Layer 3      Layer 4       Layer 7
```

# MAC Addresses vs IP Addresses

--> MAC (Media Access Control) address -- a unique physical hardware address burned into a network interface card, used for Layer 2 delivery WITHIN a single local network (like a house address on one street).
--> IP address -- a logical address used for Layer 3 delivery ACROSS different networks (like a full postal address usable anywhere in the world).
--> ARP (Address Resolution Protocol) -- the mechanism that maps an IP address to a MAC address within a local network ("who has 192.168.1.5? tell me your MAC address") -- ARP spoofing (impersonating another device's IP-to-MAC mapping) is a classic attack covered later in the Ethical Hacking track.

# Common Network Devices

--> Hub -- (largely obsolete) broadcasts every incoming signal to every port, no intelligence.
--> Switch -- operates at Layer 2, learns which MAC address is on which port, and forwards frames only to the correct port -- far more efficient than a hub.
--> Router -- operates at Layer 3, forwards packets BETWEEN different networks based on IP addresses -- what actually connects your home network to the internet.
--> Access Point -- bridges wireless (802.11/Wi-Fi) devices onto a wired network.

# Deep Dive -- Collision Domains and Broadcast Domains

--> A **Collision Domain** is a network segment where two devices transmitting AT THE SAME TIME would cause their signals to collide and corrupt each other -- relevant to older shared-medium technologies (a hub, or the Bus topology covered above), where every device on the segment competes for the same physical medium. Modern switches give EACH connected port its own separate collision domain, effectively eliminating this problem for wired Ethernet.
--> A **Broadcast Domain** is a larger boundary -- the set of devices that all receive a BROADCAST frame (a Layer 2 message sent to "everyone" on the local network, e.g. an ARP request asking "who has this IP?"). A switch does NOT separate broadcast domains -- broadcasts still reach every device connected to it. A router DOES separate broadcast domains -- broadcasts never cross from one router-connected network to another.

```
[Switch] -- forwards to ONE specific port (learned by MAC address) for normal traffic,
             but floods a BROADCAST frame to every connected device -- one broadcast domain

[Router] -- does NOT forward broadcasts between the networks on each of its interfaces --
             each side of a router is a SEPARATE broadcast domain
```

--> **Why this matters for security** -- ARP spoofing (referenced above) and other Layer 2 attacks are only effective WITHIN a single broadcast domain, since ARP requests/replies don't cross router boundaries -- this is precisely why VLANs (Virtual LANs, which create separate broadcast domains on the same physical switch hardware) are a standard network segmentation technique, directly connecting to the Zero Trust Architecture and Network Security files in the Cyber Security track, limiting how far a Layer 2 attack from one compromised device can actually reach.

# Deep Dive -- Full Duplex vs Half Duplex

--> **Half Duplex** -- a connection can send OR receive at any given moment, but not both simultaneously (like an old two-way radio -- "over" when you're done talking) -- a legacy characteristic of hub-based/shared-medium networks, where collision avoidance required taking turns.
--> **Full Duplex** -- a connection can send AND receive simultaneously, in both directions at once (like a phone call) -- the standard for all modern switched Ethernet connections, since a switch gives each device its own dedicated collision-domain-free link, eliminating the need to "take turns" at all.
