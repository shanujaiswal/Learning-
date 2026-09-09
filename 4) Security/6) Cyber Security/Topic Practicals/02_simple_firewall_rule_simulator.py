"""
02 - Simple Firewall Rule Simulator
Chapter: 02 Network Security - Firewalls, VPNs and IDS/IPS

WHAT THIS DEMONSTRATES
-----------------------
A real stateless packet-filtering firewall (think early iptables / an ACL on
a router) evaluates every incoming packet against an ordered list of rules
and applies the action (ALLOW/DENY) of the *first* rule that matches. If no
rule matches, a "default policy" (usually DENY) applies.

This script builds a tiny in-memory version of exactly that:
    - `FirewallRule`   -- one rule: direction, protocol, src IP (or CIDR-ish
                            prefix / "any"), dest port (or "any"), action.
    - `Firewall`        -- holds an ordered rule list + default policy and
                            evaluates packets against it, top-to-bottom.
    - A list of simulated packets (a mix of allowed and blocked traffic:
      SSH from an office IP, HTTP/HTTPS, a blocked Telnet attempt, traffic
      from a known-bad IP, an ICMP ping, etc.)

Run:
    python 02_simple_firewall_rule_simulator.py
"""

from __future__ import annotations

import ipaddress
from dataclasses import dataclass
from typing import Optional


@dataclass
class Packet:
    src_ip: str
    dst_port: int
    protocol: str  # "TCP", "UDP", "ICMP"
    description: str = ""


@dataclass
class FirewallRule:
    action: str            # "ALLOW" or "DENY"
    protocol: str           # "TCP", "UDP", "ICMP", or "ANY"
    src_network: str        # e.g. "10.0.0.0/24", "203.0.113.77/32", or "any"
    dst_port: object         # int, or "any"
    description: str

    def matches(self, packet: Packet) -> bool:
        if self.protocol != "ANY" and self.protocol != packet.protocol:
            return False
        if self.dst_port != "any" and self.dst_port != packet.dst_port:
            return False
        if self.src_network != "any":
            network = ipaddress.ip_network(self.src_network, strict=False)
            if ipaddress.ip_address(packet.src_ip) not in network:
                return False
        return True


class Firewall:
    """Stateless, ordered rule-list packet filter (first match wins)."""

    def __init__(self, rules: list[FirewallRule], default_policy: str = "DENY"):
        self.rules = rules
        self.default_policy = default_policy

    def evaluate(self, packet: Packet) -> tuple[str, str]:
        """Returns (action, reason) for the given packet."""
        for rule in self.rules:
            if rule.matches(packet):
                return rule.action, f"matched rule: {rule.description}"
        return self.default_policy, "no rule matched -> default policy"


def build_sample_ruleset() -> Firewall:
    rules = [
        FirewallRule("ALLOW", "TCP", "10.0.0.0/24", 22,
                     "Allow SSH (22) from trusted office LAN 10.0.0.0/24"),
        FirewallRule("ALLOW", "TCP", "any", 443,
                     "Allow inbound HTTPS (443) from anywhere"),
        FirewallRule("ALLOW", "TCP", "any", 80,
                     "Allow inbound HTTP (80) from anywhere"),
        FirewallRule("DENY", "TCP", "203.0.113.77/32", "any",
                     "Block all traffic from known-malicious IP 203.0.113.77"),
        FirewallRule("DENY", "TCP", "any", 23,
                     "Block Telnet (23) - insecure, unencrypted protocol"),
        FirewallRule("ALLOW", "ICMP", "10.0.0.0/24", "any",
                     "Allow ICMP (ping) from internal LAN for diagnostics"),
        FirewallRule("DENY", "ICMP", "any", "any",
                     "Block ICMP from the internet (avoid ping sweeps)"),
    ]
    return Firewall(rules, default_policy="DENY")


def build_sample_packets() -> list[Packet]:
    return [
        Packet("10.0.0.15", 22, "TCP", "Office laptop SSH-ing into the server"),
        Packet("198.51.100.9", 443, "TCP", "Random internet client browsing HTTPS site"),
        Packet("198.51.100.9", 80, "TCP", "Random internet client browsing HTTP site"),
        Packet("203.0.113.77", 443, "TCP", "Known-bad IP trying HTTPS anyway"),
        Packet("198.51.100.20", 23, "TCP", "Someone attempting a Telnet connection"),
        Packet("10.0.0.44", 0, "ICMP", "Internal host pinging the server for diagnostics"),
        Packet("198.51.100.20", 0, "ICMP", "External host pinging the server (recon attempt)"),
        Packet("192.0.2.5", 3389, "TCP", "External client attempting RDP (no rule -> default policy)"),
    ]


def main() -> None:
    firewall = build_sample_ruleset()
    packets = build_sample_packets()

    print("Firewall ruleset (evaluated top-to-bottom, first match wins):")
    for i, rule in enumerate(firewall.rules, 1):
        print(f"  {i}. [{rule.action}] proto={rule.protocol} src={rule.src_network} "
              f"port={rule.dst_port} -- {rule.description}")
    print(f"  Default policy (no match): {firewall.default_policy}\n")

    print("Evaluating simulated packets:\n")
    for packet in packets:
        action, reason = firewall.evaluate(packet)
        tag = "ALLOWED" if action == "ALLOW" else "BLOCKED"
        print(f"[{tag}] {packet.src_ip} -> port {packet.dst_port}/{packet.protocol}")
        print(f"          {packet.description}")
        print(f"          Reason: {reason}\n")


if __name__ == "__main__":
    main()
