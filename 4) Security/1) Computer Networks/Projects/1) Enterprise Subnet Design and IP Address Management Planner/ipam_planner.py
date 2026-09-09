"""
ipam_planner.py
----------------
Maps to Theory chapter: "TCP/IP, Ports and IP Addressing" (Subnetting and CIDR notation)

Runs VLSM allocation across DEPARTMENT_REQUIREMENTS out of PARENT_BLOCK, and
prints an IPAM (IP Address Management) allocation table -- exactly the kind
of report a tool like Infoblox or phpIPAM generates for a network engineer:

    Department      CIDR              Usable Range                Hosts   Needed   Util%

This is the "single source of truth" a NOC/network team would keep on file
for this site -- every other module in this project (conflict_detector.py,
main.py) works FROM this table.
"""

from __future__ import annotations

import ipaddress

from department_requirements import DEPARTMENT_REQUIREMENTS, PARENT_BLOCK
from ip_utils import SubnetInfo, describe_cidr, free_blocks, vlsm_allocate


def build_allocation_table(
    parent_cidr: str = PARENT_BLOCK,
    requirements: list[tuple[str, int]] = DEPARTMENT_REQUIREMENTS,
) -> list[SubnetInfo]:
    """Run VLSM and return the allocation table (list of SubnetInfo)."""
    return vlsm_allocate(parent_cidr, requirements)


def print_allocation_table(allocations: list[SubnetInfo], parent_cidr: str = PARENT_BLOCK) -> None:
    parent_info = describe_cidr(parent_cidr)

    print("=" * 100)
    print(f"IPAM ALLOCATION PLAN -- parent block {parent_info['cidr']} "
          f"({parent_info['total_addresses']} total addresses)")
    print("=" * 100)

    header = f"{'Department':<16}{'CIDR':<18}{'Usable Range':<32}{'Capacity':>9}{'Needed':>8}{'Util %':>9}"
    print(header)
    print("-" * len(header))

    total_capacity = 0
    total_needed = 0
    for sub in allocations:
        print(
            f"{sub.name:<16}{sub.cidr:<18}{sub.usable_range:<32}"
            f"{sub.usable_host_count:>9}{sub.required_hosts:>8}{sub.utilization_pct:>8}%"
        )
        total_capacity += sub.usable_host_count
        total_needed += sub.required_hosts

    print("-" * len(header))
    allocated_addresses = sum(sub.network.num_addresses for sub in allocations)
    overall_util = round((total_needed / total_capacity) * 100, 1) if total_capacity else 0.0
    print(
        f"{'TOTAL':<16}{'':<18}{'':<32}{total_capacity:>9}{total_needed:>8}{overall_util:>8}%"
    )

    remaining = free_blocks(parent_cidr, [sub.network for sub in allocations])
    remaining_addresses = sum(n.num_addresses for n in remaining)
    print()
    print(f"Addresses allocated to departments: {allocated_addresses} "
          f"({round(allocated_addresses / parent_info['total_addresses'] * 100, 1)}% of parent block)")
    print(f"Addresses still free in {parent_cidr}: {remaining_addresses} "
          f"({round(remaining_addresses / parent_info['total_addresses'] * 100, 1)}% of parent block)")
    if remaining:
        free_list = ", ".join(str(n) for n in remaining[:6])
        more = f"  (+{len(remaining) - 6} more)" if len(remaining) > 6 else ""
        print(f"Free blocks available for future departments: {free_list}{more}")
    else:
        print("Free blocks available for future departments: NONE -- parent block is fully allocated")


def main() -> None:
    allocations = build_allocation_table()
    print_allocation_table(allocations)


if __name__ == "__main__":
    main()
