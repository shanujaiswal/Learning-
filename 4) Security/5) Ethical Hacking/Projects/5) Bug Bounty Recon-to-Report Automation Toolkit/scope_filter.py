"""
scope_filter.py

Strictly filters a candidate host list against program_scope.py's
published rules, BEFORE any probing happens.

This module is the load-bearing compliance control of the whole
toolkit: Theory note 16 is explicit that testing an out-of-scope asset
is itself a program-policy violation (bans, forfeited payouts) even
when it isn't illegal. Automating "manual scope-compliance discipline"
here means a human forgetting to check one host out of forty candidates
can never happen -- every single host is evaluated, and every exclusion
decision is logged.
"""

from __future__ import annotations

from dataclasses import dataclass

import program_scope


@dataclass(frozen=True)
class FilterResult:
    in_scope_hosts: list[str]
    excluded_hosts: list[str]
    decisions: list[program_scope.ScopeDecision]


def filter_candidates(hosts: list[str], *, verbose: bool = True) -> FilterResult:
    """
    Evaluate every candidate host against the program's scope rules and
    split them into in-scope survivors vs. excluded hosts, logging each
    decision (this log is the evidence trail a real hunter would keep
    to show they respected scope).
    """
    in_scope: list[str] = []
    excluded: list[str] = []
    decisions: list[program_scope.ScopeDecision] = []

    for host in hosts:
        decision = program_scope.evaluate(host)
        decisions.append(decision)

        if decision.in_scope:
            in_scope.append(host)
            if verbose:
                print(f"  [IN-SCOPE]  {host:<28} -> {decision.reason}")
        else:
            excluded.append(host)
            if verbose:
                print(f"  [EXCLUDED PER SCOPE] {host:<20} -> {decision.reason}")

    return FilterResult(in_scope_hosts=in_scope, excluded_hosts=excluded, decisions=decisions)


if __name__ == "__main__":
    import subdomain_enumerator

    candidates = subdomain_enumerator.enumerate_subdomains()
    print(f"Scope-filtering {len(candidates)} candidate hosts against '{program_scope.PROGRAM_NAME}'...\n")
    result = filter_candidates(candidates)
    print(f"\n{len(result.in_scope_hosts)} in-scope survivors, {len(result.excluded_hosts)} excluded.")
