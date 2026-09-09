"""
ip_utils.py
-----------
Maps to Theory chapter: "TCP/IP, Ports and IP Addressing" (Subnetting and CIDR notation)

Core IP/CIDR math used by the rest of this project:
  - parse a CIDR block and describe it (network, broadcast, usable range, host count)
  - work out the smallest prefix length that can hold N required hosts
  - VLSM (Variable Length Subnet Masking): carve a single parent block into
    differently-sized child subnets, one per required host count, WITHOUT
    wasting address space the way a "one-size-fits-all" fixed subnet mask would.

Everything here is built on the standard library `ipaddress` module -- no
manual bit-shifting needed, but the VLSM carving logic (sort-by-size, then
walk a cursor through the address space) is the actual algorithm a real
IPAM tool implements under the hood.

This module is imported by ipam_planner.py and conflict_detector.py -- it
has no side effects and prints nothing on its own.
"""

from __future__ import annotations

import ipaddress
import math
from dataclasses import dataclass


class InsufficientSpaceError(Exception):
    """Raised when a parent block cannot fit all the requested subnets."""


@dataclass
class SubnetInfo:
    """A fully-described subnet, ready to drop into an allocation table."""

    name: str
    network: ipaddress.IPv4Network
    required_hosts: int

    @property
    def cidr(self) -> str:
        return str(self.network)

    @property
    def netmask(self) -> str:
        return str(self.network.netmask)

    @property
    def broadcast(self) -> str:
        return str(self.network.broadcast_address)

    @property
    def usable_range(self) -> str:
        hosts = list(self.network.hosts())
        if not hosts:
            # /31 or /32 -- no separate network/broadcast usable range
            return f"{self.network.network_address} (point-to-point / single host)"
        return f"{hosts[0]} - {hosts[-1]}"

    @property
    def usable_host_count(self) -> int:
        hosts = list(self.network.hosts())
        return len(hosts) if hosts else self.network.num_addresses

    @property
    def utilization_pct(self) -> float:
        """How much of this subnet's usable capacity the requirement actually needs."""
        capacity = self.usable_host_count
        if capacity == 0:
            return 0.0
        return round((self.required_hosts / capacity) * 100, 1)

    def contains(self, other: ipaddress.IPv4Network) -> bool:
        return other.subnet_of(self.network) or self.network.subnet_of(other)

    def overlaps(self, other: ipaddress.IPv4Network) -> bool:
        return self.network.overlaps(other)


def describe_cidr(cidr: str) -> dict:
    """Return a plain-dict breakdown of a CIDR block (network/broadcast/usable/hosts)."""
    network = ipaddress.ip_network(cidr, strict=False)
    hosts = list(network.hosts())
    if hosts:
        usable_range = f"{hosts[0]} - {hosts[-1]}"
        usable_count = len(hosts)
    else:
        usable_range = f"{network.network_address} (no separate usable range)"
        usable_count = network.num_addresses

    return {
        "cidr": str(network),
        "network_address": str(network.network_address),
        "netmask": str(network.netmask),
        "prefixlen": network.prefixlen,
        "broadcast_address": str(network.broadcast_address),
        "total_addresses": network.num_addresses,
        "usable_range": usable_range,
        "usable_host_count": usable_count,
    }


def prefix_for_hosts(required_hosts: int) -> int:
    """
    Smallest IPv4 prefix length (largest possible netmask, e.g. /27) whose
    subnet has enough USABLE host addresses for `required_hosts` devices.

    Reserves 2 addresses (network + broadcast) per subnet, matching how a
    real switch/router VLAN subnet actually works -- except for /31 and /32
    which are special-cased by RFC 3021 / single-host blocks.
    """
    if required_hosts <= 0:
        raise ValueError("required_hosts must be a positive integer")
    if required_hosts == 1:
        return 32
    if required_hosts == 2:
        return 31  # RFC 3021 point-to-point link, no network/broadcast reserved

    needed_addresses = required_hosts + 2  # + network + broadcast
    host_bits = math.ceil(math.log2(needed_addresses))
    prefix = 32 - host_bits
    if prefix < 0:
        raise ValueError(f"{required_hosts} hosts cannot fit in any IPv4 block")
    return prefix


def vlsm_allocate(parent_cidr: str, requirements: list[tuple[str, int]]) -> list[SubnetInfo]:
    """
    Perform VLSM allocation of `requirements` (list of (name, required_hosts))
    out of a single `parent_cidr` block.

    Algorithm (the same one an engineer does by hand on paper, or an IPAM
    tool does automatically):
      1. Sort requirements LARGEST-first. This is the crux of VLSM -- carving
         the biggest subnets first guarantees every subnet lands on a correct
         power-of-two address boundary without manual alignment bookkeeping.
      2. Walk a cursor through the parent's address space. For each
         requirement, compute the smallest prefix that fits it, round the
         cursor UP to that block's natural alignment boundary, and carve it out.
      3. If the cursor runs past the parent's broadcast address, the parent
         block is too small for everything requested -> InsufficientSpaceError.

    Returns subnets in the ORIGINAL requirement order (not the sorted order),
    so the caller's department ordering is preserved in the output table.
    """
    parent = ipaddress.ip_network(parent_cidr, strict=False)

    # Sort by required host count, descending, but remember original position.
    indexed = list(enumerate(requirements))
    indexed.sort(key=lambda item: item[1][1], reverse=True)

    parent_start = int(parent.network_address)
    parent_end = int(parent.broadcast_address)
    cursor = parent_start

    results_by_index: dict[int, SubnetInfo] = {}

    for original_index, (name, hosts) in indexed:
        prefix = prefix_for_hosts(hosts)
        block_size = 2 ** (32 - prefix)

        # Align the cursor up to this block's natural boundary.
        remainder = cursor % block_size
        if remainder != 0:
            cursor += block_size - remainder

        block_last = cursor + block_size - 1
        if block_last > parent_end:
            raise InsufficientSpaceError(
                f"Cannot allocate {block_size} addresses (/{prefix}) for "
                f"'{name}' ({hosts} hosts needed) -- parent block "
                f"{parent} is exhausted after prior allocations."
            )

        subnet = ipaddress.ip_network((cursor, prefix), strict=True)
        results_by_index[original_index] = SubnetInfo(name=name, network=subnet, required_hosts=hosts)

        cursor += block_size

    # Restore the caller's original ordering.
    return [results_by_index[i] for i in range(len(requirements))]


def free_blocks(parent_cidr: str, allocated: list[ipaddress.IPv4Network]) -> list[ipaddress.IPv4Network]:
    """
    Given a parent block and a list of already-allocated child networks,
    return the remaining FREE address ranges as a list of largest-possible
    CIDR blocks (what a real IPAM tool shows as "available space").
    """
    parent = ipaddress.ip_network(parent_cidr, strict=False)
    allocated_sorted = sorted(allocated, key=lambda n: int(n.network_address))

    free: list[ipaddress.IPv4Network] = []
    cursor = int(parent.network_address)
    parent_end = int(parent.broadcast_address)

    for net in allocated_sorted:
        net_start = int(net.network_address)
        net_end = int(net.broadcast_address)
        if net_start > cursor:
            free.extend(_range_to_cidrs(cursor, net_start - 1))
        cursor = max(cursor, net_end + 1)

    if cursor <= parent_end:
        free.extend(_range_to_cidrs(cursor, parent_end))

    return free


def _range_to_cidrs(first_int: int, last_int: int) -> list[ipaddress.IPv4Network]:
    """Summarize an inclusive integer address range into minimal CIDR blocks."""
    first_ip = ipaddress.ip_address(first_int)
    last_ip = ipaddress.ip_address(last_int)
    return list(ipaddress.summarize_address_range(first_ip, last_ip))
