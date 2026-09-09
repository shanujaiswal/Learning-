"""
department_requirements.py
---------------------------
The planning INPUT for this project: one company site, one allocated parent
block, and a list of departments each needing a differently-sized subnet.

In a real job this list comes from a requirements-gathering conversation with
each department lead ("how many desks / APs / servers do you have, plus
growth room for the next 2-3 years?") -- not just today's headcount. That's
why every number below already has growth headroom baked in, e.g. Engineering
has ~120 people today but is budgeted for 200 host addresses.

This module has no logic, just data -- it's what a real IPAM tool would let
you type into a "new site" form.
"""

# The single block the org was allocated for this new site (e.g. handed down
# from a corporate 10.0.0.0/8 supernet by whoever manages global IP space).
PARENT_BLOCK = "10.20.0.0/16"

# (department_name, required_host_addresses)
# Ordered largest-to-smallest here for readability, but ipam_planner.py does
# NOT depend on this order -- vlsm_allocate() sorts internally and hands
# back results in whatever order the list is given, so departments can be
# added to this list in any order (e.g. appended as new asks come in).
DEPARTMENT_REQUIREMENTS = [
    ("Engineering", 200),   # ~120 engineers today + growth headroom
    ("Sales", 90),          # ~60 sales staff + growth headroom
    ("Servers/DMZ", 40),    # public-facing + internal servers, load balancers
    ("Guest WiFi", 60),     # visitor devices, deliberately isolated from LAN
    ("Voice/VoIP", 20),     # desk phones, growth headroom
    ("Printers/IoT", 10),   # office printers, badge readers, smart TVs
]
