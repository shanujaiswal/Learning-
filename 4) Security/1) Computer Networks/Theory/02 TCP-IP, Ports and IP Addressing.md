# The TCP/IP Model

--> The practical 4-layer model the real internet is actually built on (vs the theoretical 7-layer OSI model from the previous file): Application, Transport, Internet, and Link.
--> Application (maps to OSI 5-7): HTTP, DNS, SSH. Transport (OSI 4): TCP, UDP. Internet (OSI 3): IP. Link (OSI 1-2): Ethernet, Wi-Fi.

# TCP vs UDP

--> TCP (Transmission Control Protocol) -- connection-oriented, reliable -- establishes a connection first (the "three-way handshake"), guarantees packets arrive in order and retransmits lost ones. Used when correctness matters more than speed: web browsing (HTTP), file transfer, email.
--> UDP (User Datagram Protocol) -- connectionless, unreliable -- just fires packets with no handshake, no guaranteed order, no retransmission. Used when speed matters more than perfect delivery: video streaming, VoIP, DNS lookups, online gaming.

# The TCP Three-Way Handshake

```
Client                          Server
  |------- SYN --------------->|     (Client: "I want to connect, my starting sequence number is X")
  |<----- SYN-ACK -------------|     (Server: "Okay, acknowledged, here's my starting sequence number Y")
  |------- ACK --------------->|     (Client: "Acknowledged, connection established")
```

--> This handshake is exactly what a Nmap SYN scan (covered in the Ethical Hacking track) manipulates -- sending a SYN and analyzing the response (SYN-ACK vs RST) to determine if a port is open, WITHOUT completing the full handshake.
--> Connection teardown uses a similar FIN/ACK exchange to close cleanly.

# Ports -- Identifying Which Application

--> An IP address identifies a MACHINE; a port number identifies a specific APPLICATION/SERVICE running on that machine. A single server can run a website (port 80/443), SSH (port 22), and a database (port 3306) simultaneously -- ports keep their traffic separated.
--> Well-known ports (0-1023): 20/21 FTP, 22 SSH, 23 Telnet, 25 SMTP, 53 DNS, 80 HTTP, 443 HTTPS, 3306 MySQL, 5432 PostgreSQL, 3389 RDP.
--> Registered ports (1024-49151) and dynamic/ephemeral ports (49152-65535) -- the temporary high-numbered port your OS assigns to YOUR side of an outgoing connection.

# IP Addressing -- IPv4

--> An IPv4 address is 32 bits, written as four decimal numbers 0-255 separated by dots (e.g. `192.168.1.10`).
--> Split into a Network portion and a Host portion -- the subnet mask determines where that split happens.
--> Private (non-internet-routable) address ranges, reserved by RFC 1918: `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16` -- these are what your home router, corporate LANs, and cloud VPCs use internally.

# Subnetting and CIDR Notation

--> CIDR notation (`/24`, `/16`, etc.) -- the number after the slash is how many bits are the NETWORK portion; the rest are HOST bits.
--> `192.168.1.0/24` -- 24 network bits, 8 host bits → 256 possible addresses (2^8), 254 usable for hosts (first is the network address, last is the broadcast address).
--> Subnetting divides one large network into smaller ones -- useful for security (isolating a database subnet from a public-facing web subnet) and for efficient address allocation.

```
192.168.1.0/24  --> 192.168.1.1 through 192.168.1.254 usable, .0 = network, .255 = broadcast
192.168.1.0/25  --> splits it into two /25 subnets of 128 addresses each
```

# NAT -- Network Address Translation

--> NAT lets many devices on a private network (all with private IPs) share a single public IP address to reach the internet -- your router rewrites the source IP/port of outgoing packets and tracks the mapping to route responses back correctly.
--> This is also why an attacker generally can't directly reach a device sitting behind NAT without either port forwarding being configured or the device initiating the connection first.

# IPv6 -- Briefly

--> 128-bit addresses (vs IPv4's 32-bit) written in hex, e.g. `2001:0db8:85a3::8a2e:0370:7334` -- created because IPv4's ~4.3 billion addresses ran out.
--> No NAT needed in the traditional sense (enough addresses for every device to have a unique public one), though this shifts some security assumptions that IPv4/NAT setups implicitly relied on.

# Deep Dive -- Load Balancing Algorithms

--> A load balancer distributes incoming traffic across multiple backend servers -- directly connecting to the Elastic Load Balancer mentioned in the AWS Cloud Platform file and the Kubernetes Service concept in the Full Stack DevOps notes -- but HOW it chooses which server gets each request varies by algorithm, each with different trade-offs.
--> **Round Robin** -- cycles through servers in fixed order (1, 2, 3, 1, 2, 3...) -- simple, works well when all servers have similar capacity and all requests are roughly equal cost.
--> **Least Connections** -- routes each new request to whichever server currently has the FEWEST active connections -- better than Round Robin when requests take significantly different amounts of time to process, avoiding piling more work onto an already-busy server.
--> **IP Hash** -- computes a hash of the client's IP address to consistently route the SAME client to the SAME backend server every time -- useful for "session affinity" (also called "sticky sessions") when a server holds some client-specific state in memory, though this specific need is increasingly designed around instead (storing session state in Redis, covered in the Full Stack Extra notes, rather than relying on sticky routing).
--> **Weighted variants** -- Round Robin or Least Connections can be "weighted" to send proportionally more traffic to more powerful servers in a mixed-capacity fleet, rather than assuming every backend server is identical.
--> **Health checks** -- every load balancing algorithm should skip servers that are failing health checks (unresponsive, returning errors) -- directly connecting to the readiness probes covered in the Kubernetes Probes/Resource Limits file, which is exactly the mechanism a load balancer uses to know which backend instances are actually safe to route traffic to right now.

# Deep Dive -- Forward Proxy vs Reverse Proxy vs VPN

--> These three are frequently confused since all three sit "in the middle" of a connection, but they solve genuinely different problems.
--> **Forward Proxy** -- sits in front of CLIENTS, making requests on their behalf to external servers -- the external server sees the proxy's IP, not the actual client's. Used for content filtering (a school/company blocking certain websites for its users), anonymity, or bypassing geographic restrictions.
--> **Reverse Proxy** -- sits in front of SERVERS, receiving requests on their behalf and forwarding them to the appropriate backend -- the CLIENT thinks it's talking directly to the actual service, unaware a reverse proxy is routing/handling the request. Nginx acting as a reverse proxy in front of a Node.js app (referenced in the Full Stack Node/Express notes) is a classic example -- handling TLS termination, load balancing, and static file serving before the request ever reaches the application server.
--> **VPN (Virtual Private Network)** -- creates an ENCRYPTED TUNNEL between a device and a remote network, making the device appear to be ON that remote network entirely, for ALL its traffic (not just web browsing) -- a fundamentally different scope than a proxy, which typically only handles specific application traffic (like a browser's HTTP requests) rather than tunneling the device's entire network stack.

```
Forward Proxy:  Client --> Proxy --> Internet          (proxy represents the CLIENT to the outside world)
Reverse Proxy:  Internet --> Proxy --> Backend Servers   (proxy represents the SERVER to the outside world)
VPN:            Device <==encrypted tunnel==> Remote Network   (device becomes part of that remote network)
```

--> **Why this distinction matters for the Cyber Security track** -- the Network Security file's coverage of VPNs for secure remote access is solving a different problem than the reverse proxy patterns used for load balancing/TLS termination in a production web architecture -- conflating the two leads to misunderstanding what each technology actually protects against.
