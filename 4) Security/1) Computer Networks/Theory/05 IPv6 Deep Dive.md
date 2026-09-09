# Why IPv6 Exists

--> IPv4 (covered earlier) has roughly 4.3 billion addresses -- exhausted years ago given the number of internet-connected devices today. IPv6 uses 128-bit addresses instead of IPv4's 32-bit, providing an astronomically larger address space (roughly 340 undecillion addresses) -- effectively solving address exhaustion permanently.

# IPv6 Address Format

--> Written as 8 groups of 4 hexadecimal digits, separated by colons: `2001:0db8:85a3:0000:0000:8a2e:0370:7334`.
--> Leading zeros in each group can be dropped, and ONE consecutive run of all-zero groups can be collapsed to `::` (only once per address, to keep it unambiguous).

```
2001:0db8:85a3:0000:0000:8a2e:0370:7334
2001:db8:85a3::8a2e:370:7334          (shortened form)

::1                                     (IPv6 loopback -- equivalent to IPv4's 127.0.0.1)
fe80::1                                  (a link-local address, shortened)
```

# No More NAT (Mostly)

--> With enough addresses for every device to have its own globally unique address, IPv6 was designed WITHOUT needing NAT (Network Address Translation, covered in the TCP/IP file) as a workaround for address scarcity.
--> Security implication -- IPv4's NAT incidentally acted as a rough firewall (devices behind NAT aren't directly reachable from the internet unless explicitly configured). With IPv6 potentially giving every device a public, routable address, a properly configured firewall becomes even more directly necessary rather than relying on NAT's side effect for implicit protection.

# Address Types

--> Global Unicast -- publicly routable, internet-reachable addresses (IPv6's equivalent of a public IPv4 address).
--> Link-Local (`fe80::/10`) -- only valid on the local network segment, auto-assigned to every interface, used for local operations like neighbor discovery -- never routed beyond the local link.
--> Unique Local (`fc00::/7`) -- IPv6's equivalent of IPv4's private address ranges (`192.168.x.x`, etc.) -- not globally routable, used for internal networking.
--> Multicast -- a single address can represent a GROUP of interfaces -- IPv6 uses multicast where IPv4 historically used broadcast (which IPv6 eliminates entirely), reducing unnecessary traffic to devices not interested in a given message.

# SLAAC -- Stateless Address Autoconfiguration

--> A device can automatically generate its own valid IPv6 address using information advertised by a local router, without needing a DHCP server to explicitly assign one (though DHCPv6 still exists as an alternative/complement for more centrally managed configuration).

# Neighbor Discovery Protocol (NDP)

--> IPv6's replacement for IPv4's ARP -- used for discovering other devices on the local link, finding routers, and detecting duplicate addresses.
--> Security note -- like ARP spoofing in IPv4, NDP has its own analogous spoofing risks (an attacker impersonating a router or another host on the local network) -- the same category of local-network trust issue, just in IPv6's specific protocol.

# Dual-Stack -- The Real-World Transition State

--> Most networks today run "dual-stack" -- both IPv4 and IPv6 simultaneously, since the transition away from IPv4 has been gradual and is still ongoing globally.
--> Security relevance -- a network/firewall configuration that thoroughly secures IPv4 traffic but neglects IPv6 rules leaves an entirely separate, often-overlooked attack surface open -- a well-known, common real-world misconfiguration.
