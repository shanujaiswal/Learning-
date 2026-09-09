# Routing -- Getting Packets Across Networks

--> A router forwards packets between networks using a routing table -- a list of "to reach this network, send via this next-hop." Each router only needs to know the next hop, not the entire path to the destination.
--> Default Gateway -- the router a device sends traffic to when the destination isn't on its own local network -- almost always your home router for a typical device.
--> Static routing -- manually configured routes, fine for small/simple networks. Dynamic routing protocols (OSPF, BGP) -- routers automatically exchange routing information and adapt to network changes; BGP specifically is the protocol that routes traffic between different organizations across the entire internet.
--> Traceroute -- a diagnostic tool showing every router hop a packet passes through to reach a destination, useful for troubleshooting slow or failing connections.

```bash
traceroute google.com     # Linux/Mac
tracert google.com        # Windows
```

# DNS -- Translating Names to IP Addresses

--> DNS (Domain Name System) exists because humans remember names (`google.com`) far better than IP addresses (`142.250.190.78`) -- DNS is the internet's phonebook.
--> Resolution flow: your device asks a Recursive Resolver (often your ISP's or `8.8.8.8`) → which asks a Root server → which points to a TLD server (handles `.com`, `.org`, etc.) → which points to the Authoritative Nameserver for that specific domain → which finally returns the actual IP.
--> Caching at every step (browser, OS, resolver) is why repeat lookups are fast and why DNS changes can take time to propagate globally (until every cache's TTL expires).

# Common DNS Record Types

--> A record -- maps a domain name to an IPv4 address. AAAA -- same, for IPv6.
--> CNAME -- aliases one domain name to another (e.g. `www.example.com` → `example.com`).
--> MX -- specifies which mail servers handle email for a domain.
--> TXT -- arbitrary text, commonly used for domain ownership verification and email security (SPF/DKIM/DMARC records).
--> NS -- specifies the authoritative nameservers for a domain.

```bash
nslookup example.com
dig example.com MX
```

--> DNS is also a common attack surface -- DNS spoofing/cache poisoning (feeding a resolver a fake IP for a domain) and DNS tunneling (smuggling data through DNS queries to bypass firewalls) are covered further in the Ethical Hacking and Cyber Security tracks.

# HTTP -- The Protocol the Web Runs On

--> HTTP is a request-response protocol: a client sends a request (method, URL, headers, optional body), the server sends back a response (status code, headers, body).
--> Common methods -- GET (retrieve data), POST (submit/create data), PUT (replace data), PATCH (partially update), DELETE (remove data).
--> Common status codes -- 200 OK, 301/302 Redirect, 400 Bad Request, 401 Unauthorized, 403 Forbidden, 404 Not Found, 500 Internal Server Error.
--> HTTP is stateless by design -- each request is independent; cookies/sessions/tokens (covered in the Full Stack backend notes) are the mechanism applications use to simulate a persistent "logged in" state on top of a stateless protocol.

# The Full Request Lifecycle -- Putting It Together

--> Typing `https://example.com` and hitting enter triggers, in order: (1) DNS resolution of `example.com` to an IP, (2) a TCP three-way handshake to that IP on port 443, (3) a TLS handshake to establish an encrypted channel (certificate exchange, key negotiation), (4) the actual HTTP request sent over that encrypted TCP connection, (5) the server's HTTP response, (6) the browser rendering the returned HTML/CSS/JS.
--> Every layer covered in this Networking folder (DNS, TCP, IP routing, encryption) is a real, distinct step in something as ordinary as loading a webpage -- and each step is also a distinct place where security can succeed or fail (DNS spoofing, TCP hijacking, TLS downgrade attacks, HTTP-level injection).
