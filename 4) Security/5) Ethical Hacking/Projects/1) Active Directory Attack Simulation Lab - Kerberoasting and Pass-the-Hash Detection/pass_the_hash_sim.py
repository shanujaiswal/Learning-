"""
pass_the_hash_sim.py

Simulates the Pass-the-Hash (PtH) technique as an ABSTRACT sequence of
labeled data-structure operations. No real NTLM protocol, no real
network authentication, and no real hash algorithm is implemented.

Core idea being modeled: an attacker who has obtained an account's
password HASH (never the plaintext -- e.g. simulating a dump from
`lsass.exe` memory or the SAM database) can authenticate to other
machines by presenting that hash directly to a simulated
`authenticate_with_hash()` function. Cracking the hash into a
plaintext password is never required -- that's precisely what makes
PtH dangerous and exactly why hash reuse across many hosts is a
detectable lateral-movement pattern.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timedelta

from ad_directory_simulator import ADDomain


@dataclass
class SimulatedNTLMAuthEvent:
    """An abstract stand-in for a real Windows Security Event ID 4624
    (An account was successfully logged on) with LogonType=3 (network)
    and AuthenticationPackageName=NTLM -- the real-world log signature
    a Pass-the-Hash authentication leaves behind.
    """
    timestamp: datetime
    account: str
    target_host: str
    auth_package: str          # always "NTLM" for this simulation
    simulated_hash_label: str  # abstract label, never a real hash value
    success: bool


def obtain_simulated_hash(account: str) -> str:
    """Simulate an attacker having already dumped an account's hash.

    In a real attack this would be `lsass.exe` memory (Mimikatz
    `sekurlsa::logonpasswords`) or a SAM/NTDS.dit dump
    (`secretsdump.py`). Here we just synthesize an opaque label --
    never a real password, never a real hash algorithm -- to
    represent "the attacker now holds this account's hash".
    """
    return f"SIM-NTLM-HASH::{account}"


def authenticate_with_hash(
    account: str,
    simulated_hash: str,
    target_host: str,
    when: datetime,
) -> SimulatedNTLMAuthEvent:
    """Simulate authenticating to one target host using only the hash.

    Real equivalent: `psexec.py -hashes :<ntlm_hash> Administrator@host`
    or `crackmapexec smb <host> -u Administrator -H <ntlm_hash>`. The
    key point this function preserves: the plaintext password is never
    used, referenced, or required anywhere in this call.
    """
    return SimulatedNTLMAuthEvent(
        timestamp=when,
        account=account,
        target_host=target_host,
        auth_package="NTLM",
        simulated_hash_label=simulated_hash,
        success=True,
    )


def simulate_pass_the_hash(
    domain: ADDomain,
    stolen_account: str,
    start_time: datetime,
    target_hosts: list[str] | None = None,
    seconds_between_hops: float = 5.0,
) -> list[SimulatedNTLMAuthEvent]:
    """Run the full simulated PtH lateral-movement sweep.

    The attacker reuses ONE stolen hash to authenticate to many
    machine accounts in the domain in a short window -- the classic
    "spray a hash across the network" lateral-movement pattern (real
    equivalent: `crackmapexec smb 10.10.10.0/24 -H <hash>`). This
    one-hash-many-hosts-in-a-short-window pattern is exactly what
    detection_engine.py's Pass-the-Hash detector looks for.
    """
    simulated_hash = obtain_simulated_hash(stolen_account)

    if target_hosts is None:
        target_hosts = [m.hostname for m in domain.machines]

    events: list[SimulatedNTLMAuthEvent] = []
    for i, host in enumerate(target_hosts):
        when = start_time + timedelta(seconds=i * seconds_between_hops)
        events.append(
            authenticate_with_hash(stolen_account, simulated_hash, host, when)
        )

    return events


if __name__ == "__main__":
    from ad_directory_simulator import build_lab_domain

    domain = build_lab_domain()
    events = simulate_pass_the_hash(
        domain, stolen_account="svc_backup", start_time=datetime.now()
    )

    print(
        f"Simulated PtH: 'svc_backup' hash reused across "
        f"{len(events)} distinct hosts:"
    )
    for e in events:
        print(
            f"  {e.timestamp:%H:%M:%S}  {e.account} -> {e.target_host}  "
            f"auth_package={e.auth_package}  success={e.success}"
        )
