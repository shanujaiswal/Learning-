"""
conflict_detector.py
---------------------
Maps to Theory chapter: "TCP/IP, Ports and IP Addressing" (Subnetting and CIDR notation)

Real IPAM tools don't just allocate once and forget -- departments grow,
new departments get added, and sometimes someone manually types in a CIDR
that collides with something already handed out. This module answers three
questions a network engineer asks whenever a change request comes in:

  1. NEW DEPARTMENT REQUEST (host count only, e.g. "Marketing needs 25 hosts")
     -- is there a free block left in the parent big enough to fit it, and if
     so, what CIDR should it get?

  2. MANUAL/PROPOSED CIDR (someone already picked a block, e.g. from an old
     spreadsheet) -- does it OVERLAP any existing allocation?

  3. EXHAUSTION CHECK -- has the parent block run out of usable free space
     entirely for a request of a given size?

This mirrors exactly what clicking "Add Subnet" in Infoblox / phpIPAM does
behind the scenes: check remaining free space, refuse/flag collisions,
propose the next available block.
"""

from __future__ import annotations

import ipaddress
from dataclasses import dataclass

from ip_utils import SubnetInfo, free_blocks, prefix_for_hosts


@dataclass
class ConflictVerdict:
    ok: bool
    reason: str
    suggested_cidr: str | None = None

    def __str__(self) -> str:
        status = "OK" if self.ok else "REJECTED"
        line = f"[{status}] {self.reason}"
        if self.suggested_cidr:
            line += f" -> suggested block: {self.suggested_cidr}"
        return line


def check_new_department_by_size(
    parent_cidr: str,
    existing_allocations: list[SubnetInfo],
    department_name: str,
    required_hosts: int,
) -> ConflictVerdict:
    """
    Case 1: a new department shows up with only a host-count requirement
    (no CIDR picked yet). Find whether ANY free block left in the parent
    is large enough, and if so, propose the best (smallest-fit / least
    wasteful) one.
    """
    needed_prefix = prefix_for_hosts(required_hosts)
    needed_size = 2 ** (32 - needed_prefix)

    allocated_networks = [sub.network for sub in existing_allocations]
    free = free_blocks(parent_cidr, allocated_networks)

    # Look for the SMALLEST free block that is still big enough (least
    # wasteful fit), mirroring "best-fit" allocation in a real IPAM tool.
    candidates = [blk for blk in free if blk.num_addresses >= needed_size]
    if not candidates:
        largest_free = max((blk.num_addresses for blk in free), default=0)
        return ConflictVerdict(
            ok=False,
            reason=(
                f"'{department_name}' needs {required_hosts} hosts (requires a /{needed_prefix}, "
                f"{needed_size} addresses), but the largest free block left in {parent_cidr} "
                f"only has {largest_free} addresses. Parent block is EXHAUSTED for this request -- "
                f"allocate a new parent supernet or reclaim/shrink an existing department's block."
            ),
        )

    best = min(candidates, key=lambda blk: blk.num_addresses)
    # Carve out a right-sized subnet from the start of that free block.
    proposed = next(best.subnets(new_prefix=needed_prefix))

    return ConflictVerdict(
        ok=True,
        reason=(
            f"'{department_name}' needs {required_hosts} hosts (/{needed_prefix}). "
            f"Free block {best} has room."
        ),
        suggested_cidr=str(proposed),
    )


def check_manual_cidr_overlap(
    existing_allocations: list[SubnetInfo],
    department_name: str,
    proposed_cidr: str,
) -> ConflictVerdict:
    """
    Case 2: someone already picked a specific CIDR by hand (e.g. copying an
    old spreadsheet or a vendor's default). Check it against every existing
    allocation for an OVERLAP -- the classic IPAM mistake this whole project
    exists to prevent.
    """
    proposed = ipaddress.ip_network(proposed_cidr, strict=False)

    for sub in existing_allocations:
        if proposed.overlaps(sub.network):
            return ConflictVerdict(
                ok=False,
                reason=(
                    f"'{department_name}' proposed block {proposed} OVERLAPS the existing "
                    f"'{sub.name}' allocation ({sub.network}). This would cause duplicate/"
                    f"conflicting IP addresses on the network."
                ),
            )

    return ConflictVerdict(
        ok=True,
        reason=f"'{department_name}' proposed block {proposed} does not overlap any existing allocation.",
    )


def next_available_block(
    parent_cidr: str,
    existing_allocations: list[SubnetInfo],
    required_hosts: int,
) -> str | None:
    """Convenience helper: just the suggested CIDR string, or None if none fits."""
    verdict = check_new_department_by_size(parent_cidr, existing_allocations, "candidate", required_hosts)
    return verdict.suggested_cidr
