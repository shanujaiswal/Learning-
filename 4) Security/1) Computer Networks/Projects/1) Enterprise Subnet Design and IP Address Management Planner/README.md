# Enterprise Subnet Design and IP Address Management (IPAM) Planner

## Real-world scenario

A network engineer is designing IP addressing for a brand-new company site.
Head office has handed down a single block, `10.20.0.0/16`, for this site.
Several departments each need a differently-sized subnet:

- **Engineering** (largest -- ~120 people today, budgeted to 200 host addresses for growth)
- **Sales** (~60 people, budgeted to 90)
- **Servers/DMZ** (public-facing + internal servers, load balancers -- 40)
- **Guest WiFi** (visitor devices, deliberately isolated from the corporate LAN -- 60)
- **Voice/VoIP** (desk phones -- 20)
- **Printers/IoT** (printers, badge readers, smart TVs -- 10)

Handing every department the same fixed subnet mask (e.g. a `/24` each) would
either be too small for Engineering or waste thousands of unused addresses on
Printers/IoT. This project uses **VLSM (Variable Length Subnet Masking)** to
size each subnet to what that department actually needs (with headroom), then
produces the **IPAM allocation table** a NOC/network team would keep on file
for the site -- exactly what tools like **Infoblox** or **phpIPAM** generate.

It then simulates the two things that happen constantly after the initial
design is signed off:

1. A **new department** shows up needing hosts -- does it fit in what's left,
   and if so, what block should it get?
2. Someone hands you a **CIDR from an old spreadsheet** -- does it collide
   with something already allocated?

This is a fuller, department-oriented planning tool -- not just a subnet
calculator. It builds on the same `ipaddress`-module foundation as
`Topic Practicals/04_subnet_calculator.py`, but adds VLSM carving across
multiple simultaneous requirements, an allocation table with utilization
tracking, and conflict/exhaustion detection for ongoing IPAM changes.

## Architecture

| Module | Role | Real-world equivalent |
|---|---|---|
| `ip_utils.py` | CIDR math: network/broadcast/usable range/host count, prefix-for-host-count, and the VLSM carving algorithm (sort largest-first, walk an aligned cursor through the parent block) | The bit-math engine underneath any IPAM tool's "auto-allocate" feature |
| `department_requirements.py` | The department list + host counts (with growth headroom) as planning input | The "new site request" form a network engineer fills in from department leads' requirements |
| `ipam_planner.py` | Runs VLSM across all departments from the parent block, prints the allocation table with utilization % and remaining free space | VLSM planning like a real network engineer does in an IPAM tool (Infoblox/phpIPAM) when standing up a new site |
| `conflict_detector.py` | Given the live allocation table plus a new request: checks fit-by-size (best-fit free block), overlap-by-manual-CIDR, and exhaustion | Clicking "Add Subnet" in Infoblox/phpIPAM -- the collision/capacity check that runs before a new allocation is committed |
| `main.py` | Runs the full scenario end-to-end and prints every step's verdict | The engineer's runbook: design the site, then handle the change requests that come in afterward |

## Run it

```
python main.py
```

No third-party dependencies -- only the standard library `ipaddress` module
(plus `math`, `dataclasses`, `itertools` internals). Requires Python 3.9+.

You can also run the planner or a single scenario module on its own:

```
python ipam_planner.py
```

## Verified result (actual output)

Ran with `python main.py` on Python 3 (stdlib only), output below is exactly
what the script prints:

```
====================================================================================================
IPAM ALLOCATION PLAN -- parent block 10.20.0.0/16 (65536 total addresses)
====================================================================================================
Department      CIDR              Usable Range                     Capacity  Needed   Util %
--------------------------------------------------------------------------------------------
Engineering     10.20.0.0/24      10.20.0.1 - 10.20.0.254               254     200    78.7%
Sales           10.20.1.0/25      10.20.1.1 - 10.20.1.126               126      90    71.4%
Servers/DMZ     10.20.1.192/26    10.20.1.193 - 10.20.1.254              62      40    64.5%
Guest WiFi      10.20.1.128/26    10.20.1.129 - 10.20.1.190              62      60    96.8%
Voice/VoIP      10.20.2.0/27      10.20.2.1 - 10.20.2.30                 30      20    66.7%
Printers/IoT    10.20.2.32/28     10.20.2.33 - 10.20.2.46                14      10    71.4%
--------------------------------------------------------------------------------------------
TOTAL                                                                   548     420    76.6%

Addresses allocated to departments: 560 (0.9% of parent block)
Addresses still free in 10.20.0.0/16: 64976 (99.1% of parent block)
Free blocks available for future departments: 10.20.2.48/28, 10.20.2.64/26, 10.20.2.128/25, 10.20.3.0/24, 10.20.4.0/22, 10.20.8.0/21  (+4 more)

SCENARIO A: New department 'Marketing' requests 45 host addresses
=================================================================
[OK] 'Marketing' needs 45 hosts (/26). Free block 10.20.2.64/26 has room. -> suggested block: 10.20.2.64/26

SCENARIO B: New department 'BigDataCluster' requests 40,000 host addresses
==========================================================================
[REJECTED] 'BigDataCluster' needs 40000 hosts (requires a /16, 65536 addresses), but the largest free block left in 10.20.0.0/16 only has 32768 addresses. Parent block is EXHAUSTED for this request -- allocate a new parent supernet or reclaim/shrink an existing department's block.

SCENARIO C: Manual proposal -- 'Legacy-IT' wants to claim 10.20.0.128/25
========================================================================
[REJECTED] 'Legacy-IT' proposed block 10.20.0.128/25 OVERLAPS the existing 'Engineering' allocation (10.20.0.0/24). This would cause duplicate/conflicting IP addresses on the network.

SCENARIO D: Manual proposal -- 'Backup-Site' wants to claim 10.20.10.0/24
=========================================================================
[OK] 'Backup-Site' proposed block 10.20.10.0/24 does not overlap any existing allocation.
```

**Math sanity-check** (done by hand, matches the program's output):

- Engineering needs 200 usable hosts -> needs 202 addresses -> `2^8 = 256` >= 202 -> `/24` (254 usable). Correct.
- Sales needs 90 -> 92 addresses -> `2^7 = 128` >= 92 -> `/25` (126 usable). Correct.
- Guest WiFi / Servers-DMZ both need `/26` (64 addresses, 62 usable) since 60 and 40 both need more than 32 but at most 62.
- Voice/VoIP needs 20 -> 22 addresses -> `2^5 = 32` -> `/27` (30 usable). Correct.
- Printers/IoT needs 10 -> 12 addresses -> `2^4 = 16` -> `/28` (14 usable). Correct.
- VLSM sorts largest-first (Engineering, Sales, Guest WiFi, Servers/DMZ, Voice, Printers) so every block lands on a naturally aligned boundary with zero gaps -- allocated addresses run contiguously from `10.20.0.0` to `10.20.2.47`, exactly 560 addresses (256+128+64+64+32+16), before free space picks back up at `10.20.2.48`.
- `10.20.0.128/25` is the second half of `10.20.0.0/24` (Engineering's block: `.0` - `.255`), so it correctly gets flagged as an overlap.
- `10.20.10.0/24` sits entirely inside the untouched remainder of the `/16`, so it correctly passes with no overlap.

## Things to try changing

- **Add a new department** to `department_requirements.py` (e.g. `("Marketing", 45)`) and re-run `main.py` -- watch it get its own row in the allocation table, sized and positioned automatically by VLSM.
- **Shrink the parent block** in `department_requirements.py` from `/16` to something tight like `/23` and re-run -- watch `InsufficientSpaceError` get raised because there isn't enough room for all six departments, simulating a site that was handed too small a block.
- **Change Scenario B's host count** in `main.py` to something that DOES fit (e.g. 5,000 instead of 40,000) and see the verdict flip from `REJECTED` to `OK` with a suggested CIDR.
- **Change Scenario C's proposed CIDR** to a block that partially (not fully) overlaps an existing allocation, e.g. `10.20.1.64/26` (straddles the Sales/Guest-WiFi boundary), and confirm the overlap check still catches it -- `ipaddress`'s `.overlaps()` catches partial overlaps too, not just exact/contained matches.
- **Feed `vlsm_allocate()` a requirement of exactly 1 or 2 hosts** (e.g. a point-to-point WAN link) and see it correctly return a `/32` or `/31` per RFC 3021, instead of wastefully carving out a `/30`.
