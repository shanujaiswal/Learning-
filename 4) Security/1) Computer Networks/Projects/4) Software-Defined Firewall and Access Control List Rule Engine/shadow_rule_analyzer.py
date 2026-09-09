"""
shadow_rule_analyzer.py

Static analysis of an ACL rule list to detect "rule shadowing" -- a rule that
can NEVER fire because an earlier, broader rule in the list already matches
every flow the later rule would have matched. This is one of the most common
real-world firewall misconfigurations (the kind of finding a commercial
firewall-audit tool like AlgoSec or Tufin exists specifically to surface):
someone bolts on a broad "temporary" allow-all rule near the top of the ACL,
forgets to remove it, and every more specific/intentional rule below it
silently becomes dead code -- first-match-wins means it is never even
consulted again.

No traffic is generated here -- this is purely a structural analysis of the
rule list itself (a "config lint", not a simulation).
"""

from __future__ import annotations

import ipaddress
from dataclasses import dataclass

from acl_rule_engine import ACLRule, ANY, sorted_rules


@dataclass(frozen=True)
class ShadowFinding:
    shadowed_rule: ACLRule   # the rule that can never fire
    shadowing_rule: ACLRule  # the earlier, broader rule that always matches first

    def describe(self) -> str:
        return (
            f"Rule [{self.shadowed_rule.priority}] {self.shadowed_rule.name!r} "
            f"is fully SHADOWED by earlier rule [{self.shadowing_rule.priority}] "
            f"{self.shadowing_rule.name!r} -- it can never fire."
        )


def _network_covers(broader: ipaddress._BaseNetwork, narrower: ipaddress._BaseNetwork) -> bool:
    """True if every address in `narrower` is also inside `broader` (or they're equal)."""
    if broader.version != narrower.version:
        return False
    return narrower == broader or narrower.subnet_of(broader)


def _rule_covers(earlier: ACLRule, later: ACLRule) -> bool:
    """
    True if `earlier` fully covers `later`'s match space, i.e. EVERY flow that
    would match `later` is guaranteed to already match `earlier` first.
    That requires earlier's source range, destination range, protocol and port
    to each be equal to or broader than later's.
    """
    if not _network_covers(earlier.src_network, later.src_network):
        return False
    if not _network_covers(earlier.dst_network, later.dst_network):
        return False
    if earlier.protocol != ANY and earlier.protocol != later.protocol:
        return False
    if earlier.port != ANY and earlier.port != later.port:
        return False
    return True


def find_shadowed_rules(rules: list[ACLRule]) -> list[ShadowFinding]:
    """
    Walk the ACL in evaluation order. For each rule, check every rule that
    fires strictly BEFORE it -- if any earlier rule's match space fully
    covers this rule's match space, this rule is unreachable (shadowed).
    We report the FIRST (highest-priority / earliest-evaluated) rule that
    shadows it, since that is the one which actually intercepts the traffic.
    """
    ordered = sorted_rules(rules)
    findings: list[ShadowFinding] = []

    for i, later in enumerate(ordered):
        for earlier in ordered[:i]:
            if _rule_covers(earlier, later):
                findings.append(ShadowFinding(shadowed_rule=later, shadowing_rule=earlier))
                break  # first shadowing rule found is the one that matters
    return findings


def print_report(rules: list[ACLRule], findings: list[ShadowFinding]) -> None:
    print(f"Analyzed {len(rules)} rules in evaluation order:")
    for rule in sorted_rules(rules):
        print(f"    {rule.describe()}")

    print()
    if not findings:
        print("No shadowed rules detected -- every rule is reachable.")
        return

    print(f"{len(findings)} shadowed (unreachable) rule(s) detected:")
    for finding in findings:
        print(f"    - {finding.describe()}")
