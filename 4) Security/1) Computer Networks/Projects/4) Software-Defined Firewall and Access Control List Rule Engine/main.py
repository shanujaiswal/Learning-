"""
main.py

Entry point tying the three modules together:

  1. Generate a batch of simulated traffic flows across a segmented office
     network (Guest / Corp / Server VLANs).
  2. Evaluate every flow against the "production" ACL (first-match-wins,
     implicit deny fallback) and print the allow/deny verdict + the specific
     rule that decided it.
  3. Run the static shadow-rule analyzer against a deliberately misconfigured
     ACL and report which rule(s) are unreachable dead code, and why.

Run with:  python main.py
"""

from __future__ import annotations

from acl_rule_engine import OFFICE_ACL, MISCONFIGURED_ACL, evaluate
from packet_flow_generator import generate_flows
from shadow_rule_analyzer import find_shadowed_rules, print_report

SEPARATOR = "=" * 78


def section(title: str) -> None:
    print()
    print(SEPARATOR)
    print(title)
    print(SEPARATOR)


def run_traffic_simulation() -> None:
    section("PART 1 -- Traffic Simulation vs. the Production ACL")

    print("Active ACL (evaluated top to bottom, first match wins):")
    for rule in sorted(OFFICE_ACL, key=lambda r: r.priority):
        print(f"    {rule.describe()}")
    print("    [implicit deny-all -- fallback if nothing above matches]")

    flows = generate_flows()
    print(f"\nEvaluating {len(flows)} simulated flows:\n")

    allowed_count = 0
    denied_count = 0

    for flow in flows:
        result = evaluate(OFFICE_ACL, flow)
        verdict = "ALLOW" if result.allowed else "DENY "
        if result.allowed:
            allowed_count += 1
        else:
            denied_count += 1

        print(f"[{verdict}] {flow.src_ip:>15} -> {flow.dst_ip:<15} "
              f"{flow.protocol}/{flow.port:<5} | {flow.description}")
        print(f"         matched rule: {result.matched_rule.name} "
              f"(priority {result.matched_rule.priority})")

    print(f"\nSummary: {allowed_count} allowed, {denied_count} denied, "
          f"{len(flows)} total flows.")


def run_shadow_analysis() -> None:
    section("PART 2 -- Rule Shadowing Analysis on a Misconfigured ACL")

    print("This ACL was deliberately misconfigured with an overly broad rule")
    print("placed too early in the evaluation order:\n")

    findings = find_shadowed_rules(MISCONFIGURED_ACL)
    print_report(MISCONFIGURED_ACL, findings)

    section("Result")
    if findings:
        print(f"{len(findings)} misconfiguration(s) found -- these rules are dead "
              f"code and should be reordered, narrowed, or removed:")
        for f in findings:
            print(f"    * {f.shadowed_rule.name!r} shadowed by {f.shadowing_rule.name!r}")
    else:
        print("No shadowing detected.")


def main() -> None:
    run_traffic_simulation()
    run_shadow_analysis()
    print()
    print(SEPARATOR)
    print("Done.")
    print(SEPARATOR)


if __name__ == "__main__":
    main()
