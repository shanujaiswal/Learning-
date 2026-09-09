"""
kerberoasting_sim.py

Simulates the Kerberoasting technique (MITRE ATT&CK T1558.003) as an
ABSTRACT sequence of labeled data-structure operations:

  1. An attacker who holds *any* valid (even low-privilege) domain
     account requests a service ticket for every SPN account in the
     domain. This models the real-world fact that the KDC's TGS will
     hand a service ticket to any authenticated user who asks for one,
     with no special privilege required.
  2. Each simulated "ticket" is just a small dataclass -- there is no
     real Kerberos message, no real encryption, and no real network
     call involved.
  3. For each ticket, a simulated offline-crack-feasibility check is
     run against the target service account's simulated password
     entropy, standing in for what would really be an offline
     hashcat/John brute-force attempt against the encrypted portion of
     a real TGS-REP ticket.

Nothing here talks to a network, a real KDC, or performs any real
cryptography.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timedelta

from ad_directory_simulator import ADDomain, ServiceAccount

# Simulated offline-crack budget: below this simulated entropy, we
# consider a password "crackable" in a reasonable offline session
# (representing a fast GPU rig chewing through common patterns /
# small keyspaces). This is a lab heuristic, not a real crack-time
# model.
CRACK_FEASIBILITY_ENTROPY_THRESHOLD_BITS = 45.0

# A tiny simulated "wordlist" standing in for rockyou.txt + hashcat
# best64/OneRuleToRuleThemAll style mangling rules. Real offline
# cracking rarely brute-forces the full keyspace implied by raw
# entropy -- it tries a dictionary word with common
# capitalization/suffix/leetspeak mangling FIRST, which is why a
# password like "Summer2023" (word + year) is cracked in seconds even
# though its raw character-set entropy looks moderate. This list is a
# lab stand-in for that dictionary-attack reality, not a real
# password list.
SIMULATED_CRACKING_WORDLIST = {
    "password", "summer", "winter", "spring", "autumn", "welcome",
    "backup", "admin", "letmein", "qwerty", "dragon", "monkey",
    "football", "master", "shadow", "sunshine", "princess", "login",
}


def _base_word(password: str) -> str:
    """Strip common leading/trailing digits and symbols to recover the
    dictionary "base word" a mangling-rule attack would try first
    (e.g. "Summer2023" -> "summer", "Password1" -> "password").
    """
    word = password.lower()
    word = word.rstrip("0123456789!@#$%^&*_-")
    word = word.lstrip("0123456789!@#$%^&*_-")
    return word


def matches_dictionary_pattern(password: str) -> bool:
    """Simulated dictionary+rules crack check.

    Returns True if the password's base word appears in the lab's
    simulated wordlist -- representing what a real hashcat
    "best64"/rule-based dictionary attack would crack almost
    instantly, regardless of the password's raw character-set
    entropy.
    """
    return _base_word(password) in SIMULATED_CRACKING_WORDLIST


@dataclass
class SimulatedTicketRequest:
    """An abstract stand-in for a TGS-REQ / TGS-REP exchange.

    Fields mirror what a real Windows Security Event ID 4769
    (Kerberos Service Ticket Requested) would log, minus any real
    cryptographic material.
    """
    timestamp: datetime
    requesting_account: str   # the attacker's own valid domain account
    target_spn: str
    target_account: str
    simulated_ticket_id: str  # abstract label, never real ticket bytes


@dataclass
class CrackResult:
    target_account: str
    spn: str
    simulated_entropy_bits: float
    crackable: bool
    verdict: str


def request_service_ticket(
    requesting_account: str,
    service_account: ServiceAccount,
    when: datetime,
    request_index: int,
) -> SimulatedTicketRequest:
    """Simulate requesting a single TGS ticket for one SPN account.

    Real equivalent: `GetUserSPNs.py corp.local/alice:pass -request`
    against a real KDC. Here we just construct a labeled record.
    """
    return SimulatedTicketRequest(
        timestamp=when,
        requesting_account=requesting_account,
        target_spn=service_account.spn,
        target_account=service_account.username,
        simulated_ticket_id=f"SIM-TGS-{request_index:04d}",
    )


def assess_crack_feasibility(service_account: ServiceAccount) -> CrackResult:
    """Simulated offline-crack-feasibility check.

    Standing in for taking the encrypted portion of a real TGS-REP
    ticket offline and running hashcat/John against it. We never
    implement any real cryptography or crack any real hash -- we
    combine two simulated lab heuristics, mirroring how real offline
    cracking actually proceeds:

      1. Dictionary+rules pass (checked FIRST, as a real attacker
         would): does the password's base word match the simulated
         wordlist? If so it's crackable near-instantly, regardless of
         raw entropy.
      2. Brute-force feasibility fallback: if no dictionary hit,
         compare the simulated character-set entropy against a fixed
         lab threshold.
    """
    entropy = service_account.entropy_bits
    dictionary_hit = matches_dictionary_pattern(service_account.simulated_password)
    brute_force_feasible = entropy < CRACK_FEASIBILITY_ENTROPY_THRESHOLD_BITS
    crackable = dictionary_hit or brute_force_feasible

    if dictionary_hit:
        verdict = (
            f"CRACKABLE (base word matches simulated dictionary attack, "
            f"simulated entropy {entropy:.1f} bits) -- a real hashcat "
            "dictionary+rules pass (e.g. rockyou.txt + best64.rule) would "
            "crack this near-instantly regardless of raw entropy"
        )
    elif brute_force_feasible:
        verdict = (
            f"CRACKABLE (simulated entropy {entropy:.1f} bits < "
            f"{CRACK_FEASIBILITY_ENTROPY_THRESHOLD_BITS:.0f}-bit lab threshold) -- "
            "would fall to an offline brute-force attempt"
        )
    else:
        verdict = (
            f"NOT crackable in a reasonable offline session (no dictionary "
            f"match, simulated entropy {entropy:.1f} bits >= "
            f"{CRACK_FEASIBILITY_ENTROPY_THRESHOLD_BITS:.0f}-bit threshold)"
        )
    return CrackResult(
        target_account=service_account.username,
        spn=service_account.spn,
        simulated_entropy_bits=entropy,
        crackable=crackable,
        verdict=verdict,
    )


def simulate_kerberoasting(
    domain: ADDomain,
    attacker_account: str,
    start_time: datetime,
    seconds_between_requests: float = 2.0,
) -> tuple[list[SimulatedTicketRequest], list[CrackResult]]:
    """Run the full simulated Kerberoasting sweep.

    The attacker requests a service ticket for EVERY SPN account in
    the domain in rapid succession (a real attacker typically does
    exactly this -- request tickets for all discovered SPNs at once,
    since the KDC does not rate-limit or flag single requests). This
    burst-of-requests-from-one-source pattern is exactly what
    detection_engine.py's Kerberoasting detector looks for.

    Returns (ticket_requests, crack_results).
    """
    ticket_requests: list[SimulatedTicketRequest] = []
    for i, service_account in enumerate(domain.service_accounts):
        when = start_time + timedelta(seconds=i * seconds_between_requests)
        ticket_requests.append(
            request_service_ticket(attacker_account, service_account, when, i)
        )

    crack_results = [
        assess_crack_feasibility(sa) for sa in domain.service_accounts
    ]

    return ticket_requests, crack_results


if __name__ == "__main__":
    from ad_directory_simulator import build_lab_domain

    domain = build_lab_domain()
    requests, results = simulate_kerberoasting(
        domain, attacker_account="alice", start_time=datetime.now()
    )

    print(f"Requested {len(requests)} simulated service tickets as 'alice':")
    for r in requests:
        print(f"  {r.timestamp:%H:%M:%S}  {r.simulated_ticket_id}  SPN={r.target_spn}")

    print("\nSimulated offline-crack-feasibility results:")
    for res in results:
        flag = "CRACKED" if res.crackable else "safe"
        print(f"  [{flag:7s}] {res.target_account:20s} -- {res.verdict}")
