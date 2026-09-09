"""
04_subnet_calculator.py
-------------------------
Maps to Theory chapter: "TCP/IP, Ports, IP Addressing" (IP addressing / subnetting)

Takes a CIDR notation network (e.g. 192.168.1.0/24) and calculates:
  - Network address
  - Broadcast address
  - Usable host range (first and last usable host)
  - Number of usable hosts

Uses only Python's standard library `ipaddress` module -- no manual bit
math required, but we print enough detail to see WHY the answers are what
they are.

Run:
    python 04_subnet_calculator.py                # runs worked examples
    python 04_subnet_calculator.py 10.0.0.0/26    # calculate your own CIDR
"""

import ipaddress
import sys


def analyze_cidr(cidr: str) -> None:
    """Print a full breakdown of a CIDR-notation network."""
    network = ipaddress.ip_network(cidr, strict=False)

    print(f"\nCIDR input:          {cidr}")
    print(f"Network address:     {network.network_address}")
    print(f"Netmask:             {network.netmask}  (prefix length /{network.prefixlen})")
    print(f"Broadcast address:   {network.broadcast_address}")
    print(f"Total addresses:     {network.num_addresses}")

    hosts = list(network.hosts())  # excludes network + broadcast addresses
    if hosts:
        print(f"Usable host range:   {hosts[0]} - {hosts[-1]}")
        print(f"Number of usable hosts: {len(hosts)}")
    else:
        # This happens for /31 (point-to-point, RFC 3021) and /32 (single host)
        print("Usable host range:   n/a (block too small to have separate "
              "network/broadcast + usable hosts, e.g. /31 or /32)")
        print(f"Number of usable hosts: {network.num_addresses}")

    print(f"Is private (RFC1918): {network.is_private}")


def main():
    if len(sys.argv) == 2:
        analyze_cidr(sys.argv[1])
        return

    print("No CIDR argument given -- running worked examples:")
    print("=" * 60)

    examples = [
        "192.168.1.0/24",   # classic home/office subnet, 254 usable hosts
        "10.0.0.0/8",       # huge private range
        "172.16.5.0/28",    # small subnet, 14 usable hosts
        "203.0.113.0/30",   # tiny point-to-point-ish block, 2 usable hosts
    ]
    for cidr in examples:
        analyze_cidr(cidr)
        print("-" * 60)

    print("\nTip: run again with your own network, e.g.:")
    print("  python 04_subnet_calculator.py 192.168.50.0/26")


if __name__ == "__main__":
    main()
