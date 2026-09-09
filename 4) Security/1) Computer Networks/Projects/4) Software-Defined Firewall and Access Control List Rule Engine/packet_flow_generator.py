"""
packet_flow_generator.py

Synthesizes a batch of traffic "flows" -- the (src IP, dst IP, protocol, port)
5-tuple-minus-src-port that a real firewall/router logs and matches ACL rules
against (think NetFlow records, or the fields shown in a Cisco ASA/`iptables -L`
log line). No packets are actually sent over any socket -- this is a pure
data-simulation of what a NetFlow collector or firewall log would show for a
segmented office network, so the ACL engine has realistic input to evaluate.

VLAN layout simulated here:
    Guest VLAN  -> 10.10.0.0/24  (visitor devices, untrusted)
    Corp VLAN   -> 10.20.0.0/24  (employee workstations)
    Server VLAN -> 10.30.0.0/24  (internal app/DB/SSH-managed servers)
    Internet    -> represented by public IPs outside all three VLANs
"""

from __future__ import annotations

from acl_rule_engine import Flow

# A handful of representative hosts within each VLAN, used to build flows.
GUEST_HOSTS = ["10.10.0.11", "10.10.0.42", "10.10.0.77"]
CORP_HOSTS = ["10.20.0.15", "10.20.0.23", "10.20.0.101"]
SERVER_HOSTS = {
    "web": "10.30.0.10",
    "ssh_jumpbox": "10.30.0.20",
    "db": "10.30.0.30",
}
PUBLIC_INTERNET_HOSTS = ["93.184.216.34", "142.250.72.14"]  # arbitrary public IPs


def generate_flows() -> list[Flow]:
    """
    Build a batch of flows mixing:
      - legitimate, policy-permitted cross-VLAN traffic (should be ALLOWED)
      - traffic that policy explicitly forbids, e.g. Guest VLAN reaching into
        the Server VLAN, or Guest reaching Corp (should be BLOCKED)
    Returned in a fixed, deterministic order for reproducible demo output.
    """
    flows: list[Flow] = []

    # --- Legitimate Corp -> Server traffic (should be ALLOWED) ---
    flows.append(Flow(CORP_HOSTS[0], SERVER_HOSTS["web"], "tcp", 443,
                       "Corp workstation browsing internal HTTPS web app"))
    flows.append(Flow(CORP_HOSTS[1], SERVER_HOSTS["ssh_jumpbox"], "tcp", 22,
                       "Corp admin SSH'ing into the jump box"))
    flows.append(Flow(CORP_HOSTS[2], SERVER_HOSTS["db"], "tcp", 3306,
                       "Corp app server querying the internal MySQL DB"))

    # --- Legitimate Guest -> Internet traffic (should be ALLOWED) ---
    flows.append(Flow(GUEST_HOSTS[0], PUBLIC_INTERNET_HOSTS[0], "tcp", 443,
                       "Guest device browsing the public internet (HTTPS)"))
    flows.append(Flow(GUEST_HOSTS[1], PUBLIC_INTERNET_HOSTS[1], "tcp", 80,
                       "Guest device on plain HTTP"))

    # --- Legitimate Corp intra-VLAN traffic (should be ALLOWED) ---
    flows.append(Flow(CORP_HOSTS[0], CORP_HOSTS[2], "tcp", 445,
                       "Corp-to-corp file share (SMB) between two workstations"))

    # --- Policy-violating traffic that MUST be blocked ---
    flows.append(Flow(GUEST_HOSTS[0], SERVER_HOSTS["db"], "tcp", 3306,
                       "Guest device attempting to reach the internal DB directly"))
    flows.append(Flow(GUEST_HOSTS[1], SERVER_HOSTS["ssh_jumpbox"], "tcp", 22,
                       "Guest device attempting SSH into the internal jump box"))
    flows.append(Flow(GUEST_HOSTS[2], CORP_HOSTS[0], "tcp", 445,
                       "Guest device probing a Corp workstation's SMB port"))

    # --- Traffic with no matching rule at all -> exercises implicit deny ---
    flows.append(Flow(SERVER_HOSTS["db"], PUBLIC_INTERNET_HOSTS[0], "tcp", 9999,
                       "Server VLAN host making an unexpected outbound connection"))

    return flows
