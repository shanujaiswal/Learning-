"""
main.py
-------
Enterprise Subnet Design and IP Address Management (IPAM) Planner
====================================================================

SCENARIO
--------
A network engineer is designing IP addressing for a brand-new company site.
Head office handed down a single block, 10.20.0.0/16, for this site. Several
departments each need a differently-sized subnet: Engineering (largest),
Sales, Servers/DMZ, Guest WiFi, Voice/VoIP, and Printers/IoT (smallest).

Using a single fixed subnet mask for all of them (e.g. giving everyone a
/24) would either be too small for Engineering or waste thousands of
addresses on Printers/IoT. Instead we use VLSM (Variable Length Subnet
Masking) to size each subnet to what that department actually needs (plus
growth headroom), then produce the IPAM allocation table a NOC would keep on
file. Finally, we simulate two things that happen constantly in the real
world after the initial design is signed off:

  1. A new department shows up needing hosts -- does it fit in what's left?
  2. Someone hands you a CIDR from an old spreadsheet -- does it collide
     with something already allocated?

Run:
    python main.py
"""

from __future__ import annotations

from conflict_detector import check_manual_cidr_overlap, check_new_department_by_size
from department_requirements import DEPARTMENT_REQUIREMENTS, PARENT_BLOCK
from ipam_planner import build_allocation_table, print_allocation_table


def section(title: str) -> None:
    print()
    print(title)
    print("=" * len(title))


def main() -> None:
    # ------------------------------------------------------------------
    # STEP 1: VLSM allocation across all current departments
    # ------------------------------------------------------------------
    allocations = build_allocation_table(PARENT_BLOCK, DEPARTMENT_REQUIREMENTS)
    print_allocation_table(allocations, PARENT_BLOCK)

    # ------------------------------------------------------------------
    # STEP 2: New department request that FITS in remaining free space
    # ------------------------------------------------------------------
    section("SCENARIO A: New department 'Marketing' requests 45 host addresses")
    verdict_fits = check_new_department_by_size(
        PARENT_BLOCK, allocations, "Marketing", required_hosts=45
    )
    print(verdict_fits)

    # ------------------------------------------------------------------
    # STEP 3: New department request that is too large -- exhausted parent
    # ------------------------------------------------------------------
    section("SCENARIO B: New department 'BigDataCluster' requests 40,000 host addresses")
    verdict_exhausted = check_new_department_by_size(
        PARENT_BLOCK, allocations, "BigDataCluster", required_hosts=40000
    )
    print(verdict_exhausted)

    # ------------------------------------------------------------------
    # STEP 4: Someone manually proposes a CIDR from an old spreadsheet --
    # check it for overlap against the live allocation table.
    # ------------------------------------------------------------------
    section("SCENARIO C: Manual proposal -- 'Legacy-IT' wants to claim 10.20.0.128/25")
    verdict_overlap = check_manual_cidr_overlap(
        allocations, "Legacy-IT", proposed_cidr="10.20.0.128/25"
    )
    print(verdict_overlap)

    section("SCENARIO D: Manual proposal -- 'Backup-Site' wants to claim 10.20.10.0/24")
    verdict_clean = check_manual_cidr_overlap(
        allocations, "Backup-Site", proposed_cidr="10.20.10.0/24"
    )
    print(verdict_clean)


if __name__ == "__main__":
    main()
