"""
acl_rule_engine.py

An ordered Access Control List (ACL) rule engine -- the kind of thing that lives
inside a software-defined firewall / cloud Security Group (AWS SG, Azure NSG)
or a router ACL (Cisco `access-list`).

Core idea, and the single most important thing to get right about ANY firewall
ACL: rules are evaluated TOP TO BOTTOM, in priority order, and the FIRST rule
that matches a flow decides its fate -- first-match-wins. Every rule after that
match is never even consulted for that flow. If NO rule matches, the implicit
"deny all" at the very bottom of every real ACL/firewall kicks in.

Only the Python standard library is used (`ipaddress` for CIDR containment
and membership tests -- no third-party packet or network libraries).
"""

from __future__ import annotations

import ipaddress
from dataclasses import dataclass
from typing import Optional, Union

IPNetwork = Union[ipaddress.IPv4Network, ipaddress.IPv6Network]

ANY = "any"  # wildcard for protocol/port matching, mirrors Cisco ACL "any" / AWS SG "All"


def _to_network(cidr: str) -> IPNetwork:
    """Parse a CIDR string ('10.10.0.0/24') into an ipaddress network object."""
    return ipaddress.ip_network(cidr, strict=False)


@dataclass(frozen=True)
class ACLRule:
    """
    One line of a firewall/router ACL.

    priority   -- lower number = evaluated earlier (like a Cisco ACL's implicit
                  sequence number, or an AWS Security Group rule's position).
    name       -- human-readable label, shown in reports/logs.
    src_cidr   -- source network the rule applies to, e.g. "10.10.0.0/24".
    dst_cidr   -- destination network the rule applies to.
    protocol   -- "tcp", "udp", "icmp", or ANY ("any").
    port       -- destination port (int), or ANY ("any") for all ports.
    action     -- "allow" or "deny".
    """

    priority: int
    name: str
    src_cidr: str
    dst_cidr: str
    protocol: str
    port: Union[int, str]
    action: str

    def __post_init__(self) -> None:
        if self.action not in ("allow", "deny"):
            raise ValueError(f"Rule {self.name!r}: action must be 'allow' or 'deny'")
        if self.protocol not in ("tcp", "udp", "icmp", ANY):
            raise ValueError(f"Rule {self.name!r}: unsupported protocol {self.protocol!r}")

    @property
    def src_network(self) -> IPNetwork:
        return _to_network(self.src_cidr)

    @property
    def dst_network(self) -> IPNetwork:
        return _to_network(self.dst_cidr)

    def matches(self, flow: "Flow") -> bool:
        """Return True if this single rule's match criteria cover the given flow."""
        if ipaddress.ip_address(flow.src_ip) not in self.src_network:
            return False
        if ipaddress.ip_address(flow.dst_ip) not in self.dst_network:
            return False
        if self.protocol != ANY and self.protocol != flow.protocol:
            return False
        if self.port != ANY and self.port != flow.port:
            return False
        return True

    def describe(self) -> str:
        port_part = "any port" if self.port == ANY else f"port {self.port}"
        proto_part = "any proto" if self.protocol == ANY else self.protocol
        return (
            f"[{self.priority:>3}] {self.name}: {self.action.upper()} "
            f"{self.src_cidr} -> {self.dst_cidr} ({proto_part}/{port_part})"
        )


IMPLICIT_DENY = ACLRule(
    priority=10_000,
    name="implicit-deny-all",
    src_cidr="0.0.0.0/0",
    dst_cidr="0.0.0.0/0",
    protocol=ANY,
    port=ANY,
    action="deny",
)


@dataclass(frozen=True)
class Flow:
    """A single simulated traffic flow being tested against the ACL."""

    src_ip: str
    dst_ip: str
    protocol: str
    port: int
    description: str = ""

    def __post_init__(self) -> None:
        # Validate eagerly so a malformed generated flow fails fast and loud.
        ipaddress.ip_address(self.src_ip)
        ipaddress.ip_address(self.dst_ip)


@dataclass(frozen=True)
class EvaluationResult:
    action: str
    matched_rule: ACLRule
    flow: Flow

    @property
    def allowed(self) -> bool:
        return self.action == "allow"


def sorted_rules(rules: list[ACLRule]) -> list[ACLRule]:
    """Return rules ordered by ascending priority (evaluation order)."""
    return sorted(rules, key=lambda r: r.priority)


def evaluate(rules: list[ACLRule], flow: Flow) -> EvaluationResult:
    """
    First-match-wins evaluation, exactly like a router ACL or cloud Security
    Group: walk the rule list in priority order and return the action of the
    FIRST rule whose criteria match the flow. If nothing matches, fall back
    to the implicit deny-all.
    """
    for rule in sorted_rules(rules):
        if rule.matches(flow):
            return EvaluationResult(action=rule.action, matched_rule=rule, flow=flow)
    return EvaluationResult(action=IMPLICIT_DENY.action, matched_rule=IMPLICIT_DENY, flow=flow)


# ---------------------------------------------------------------------------
# The "production" ACL for the segmented office network used by main.py's
# traffic-simulation pass. VLAN layout:
#   Guest VLAN  -> 10.10.0.0/24  (untrusted, visitor devices)
#   Corp VLAN   -> 10.20.0.0/24  (employee workstations)
#   Server VLAN -> 10.30.0.0/24  (internal application/DB/SSH-managed servers)
# ---------------------------------------------------------------------------
OFFICE_ACL: list[ACLRule] = [
    ACLRule(10, "corp-to-server-https", "10.20.0.0/24", "10.30.0.0/24", "tcp", 443, "allow"),
    ACLRule(20, "corp-to-server-ssh", "10.20.0.0/24", "10.30.0.0/24", "tcp", 22, "allow"),
    ACLRule(30, "corp-to-server-mysql", "10.20.0.0/24", "10.30.0.0/24", "tcp", 3306, "allow"),
    ACLRule(40, "guest-to-internet-web", "10.10.0.0/24", "0.0.0.0/0", "tcp", 443, "allow"),
    ACLRule(50, "guest-to-internet-http", "10.10.0.0/24", "0.0.0.0/0", "tcp", 80, "allow"),
    ACLRule(60, "block-guest-to-server", "10.10.0.0/24", "10.30.0.0/24", ANY, ANY, "deny"),
    ACLRule(70, "block-guest-to-corp", "10.10.0.0/24", "10.20.0.0/24", ANY, ANY, "deny"),
    ACLRule(80, "corp-intra-vlan", "10.20.0.0/24", "10.20.0.0/24", ANY, ANY, "allow"),
]


# ---------------------------------------------------------------------------
# A deliberately MISCONFIGURED ACL -- used only by the shadow-rule analysis
# pass in main.py. Rule 20 below can never fire: rule 10 already allows ALL
# traffic from the entire Corp VLAN to the entire Server VLAN on any
# protocol/port, so the narrower "ssh only" rule placed after it is dead code.
# This is a very common real-world misconfiguration: someone adds a broad
# "temporary" allow-all rule during troubleshooting, forgets to remove it, and
# every more specific rule below it silently stops doing anything.
# ---------------------------------------------------------------------------
MISCONFIGURED_ACL: list[ACLRule] = [
    ACLRule(10, "corp-to-server-ALLOW-ALL", "10.20.0.0/24", "10.30.0.0/24", ANY, ANY, "allow"),
    ACLRule(20, "corp-to-server-ssh-only", "10.20.0.0/24", "10.30.0.0/24", "tcp", 22, "allow"),
    ACLRule(30, "corp-to-server-mysql-only", "10.20.0.128/28", "10.30.0.0/24", "tcp", 3306, "allow"),
    ACLRule(40, "block-guest-to-server", "10.10.0.0/24", "10.30.0.0/24", ANY, ANY, "deny"),
    ACLRule(50, "guest-to-internet-web", "10.10.0.0/24", "0.0.0.0/0", "tcp", 443, "allow"),
    ACLRule(60, "guest-single-host-web", "10.10.0.5/32", "0.0.0.0/0", "tcp", 443, "allow"),
]
