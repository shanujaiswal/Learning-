"""
main.py

Runs both simulated AD attacks (Kerberoasting, Pass-the-Hash) against
the simulated AD domain, then runs the detection engine against the
resulting event logs, and prints a full report.

This is a fully self-contained, offline lab: no real Kerberos/NTLM
protocol, no real network traffic, no real password cracking, and no
connection to any real Active Directory environment.
"""

from __future__ import annotations

from datetime import datetime

from ad_directory_simulator import build_lab_domain
from kerberoasting_sim import simulate_kerberoasting
from pass_the_hash_sim import simulate_pass_the_hash
from detection_engine import detect_kerberoasting, detect_pass_the_hash


def line(char: str = "=", width: int = 78) -> str:
    return char * width


def print_header(title: str) -> None:
    print()
    print(line())
    print(title)
    print(line())


def main() -> None:
    domain = build_lab_domain()
    start_time = datetime(2026, 8, 17, 9, 0, 0)

    print_header("ACTIVE DIRECTORY ATTACK SIMULATION LAB")
    print("(Simulated domain, simulated attacks, simulated detection -- ")
    print(" no real Kerberos/NTLM traffic, no real cryptography, no real network.)")
    print(f"\nDomain: {domain.name}")
    print(f"  Users:            {[u.username for u in domain.users]}")
    print(f"  SPN service accts:{[s.username for s in domain.service_accounts]}")
    print(f"  Machine accounts: {[m.hostname for m in domain.machines]}")

    # ------------------------------------------------------------------
    # Attack 1: Kerberoasting
    # ------------------------------------------------------------------
    print_header("ATTACK SIMULATION 1: KERBEROASTING (T1558.003)")
    attacker = "alice"
    print(f"Attacker account (low-privilege, valid domain creds): '{attacker}'")
    print("Requesting a simulated TGS service ticket for every SPN account...")

    ticket_requests, crack_results = simulate_kerberoasting(
        domain, attacker_account=attacker, start_time=start_time,
        seconds_between_requests=2.0,
    )

    print(f"\n{len(ticket_requests)} simulated service-ticket requests logged:")
    for r in ticket_requests:
        print(f"  {r.timestamp:%H:%M:%S}  {r.simulated_ticket_id}  "
              f"requester={r.requesting_account:8s} SPN={r.target_spn}")

    print("\nSimulated offline-crack-feasibility check per SPN account:")
    crackable_accounts = []
    for res in crack_results:
        flag = "CRACKED " if res.crackable else "safe    "
        print(f"  [{flag}] {res.target_account:22s} {res.verdict}")
        if res.crackable:
            crackable_accounts.append(res.target_account)

    # ------------------------------------------------------------------
    # Attack 2: Pass-the-Hash
    # ------------------------------------------------------------------
    print_header("ATTACK SIMULATION 2: PASS-THE-HASH")
    stolen_account = "svc_backup"
    print(f"Attacker has obtained a simulated NTLM HASH (never a plaintext "
          f"password) for: '{stolen_account}'")
    print("Authenticating to every machine account using only the hash...")

    pth_events = simulate_pass_the_hash(
        domain, stolen_account=stolen_account, start_time=start_time,
        seconds_between_hops=5.0,
    )

    print(f"\n{len(pth_events)} simulated NTLM authentication events logged:")
    for e in pth_events:
        print(f"  {e.timestamp:%H:%M:%S}  {e.account} -> {e.target_host:24s} "
              f"auth={e.auth_package}  success={e.success}")

    # ------------------------------------------------------------------
    # Detection layer
    # ------------------------------------------------------------------
    print_header("DETECTION ENGINE: ANALYZING EVENT LOGS")

    kerb_alerts = detect_kerberoasting(ticket_requests)
    pth_alerts = detect_pass_the_hash(pth_events)

    print("Kerberoasting detector (abnormal distinct-SPN volume, one source, "
          "short window):")
    if kerb_alerts:
        for a in kerb_alerts:
            print(f"  [ALERT:{a.severity}] {a.rule_name}")
            print(f"    {a.details}")
    else:
        print("  No alerts.")

    print("\nPass-the-Hash detector (one hash reused across abnormally many "
          "distinct hosts, short window):")
    if pth_alerts:
        for a in pth_alerts:
            print(f"  [ALERT:{a.severity}] {a.rule_name}")
            print(f"    {a.details}")
    else:
        print("  No alerts.")

    # ------------------------------------------------------------------
    # Summary
    # ------------------------------------------------------------------
    print_header("SUMMARY")

    print("Kerberoasting results:")
    if crackable_accounts:
        print(f"  {len(crackable_accounts)} of {len(domain.service_accounts)} "
              f"SPN service accounts would realistically be CRACKED offline:")
        for acct in crackable_accounts:
            print(f"    - {acct}")
    else:
        print("  No SPN service accounts were crackable in this run.")

    print("\nDetection results:")
    print(f"  Kerberoasting sweep flagged:   {'YES' if kerb_alerts else 'no'}")
    print(f"  Pass-the-Hash lateral move flagged: {'YES' if pth_alerts else 'no'}")

    print("\nAD hardening lessons:")
    print(
        "  1. SPN account password strength: any account with a Service\n"
        "     Principal Name is a Kerberoasting target the moment ANY valid\n"
        "     domain credential exists -- no special privilege is needed to\n"
        "     request its service ticket. Short/dictionary-style passwords\n"
        "     (like 'Password1' or 'Summer2023' above) fall to offline\n"
        "     cracking in minutes once the ticket is extracted. Fix: use\n"
        "     25+ character random passwords for service accounts, or better,\n"
        "     Group Managed Service Accounts (gMSA) which rotate automatically\n"
        "     and are never human-typed, plus enforce AES-only Kerberos\n"
        "     encryption so RC4 tickets (much weaker to crack) aren't issued."
    )
    print(
        "  2. Hash-reuse-across-hosts is detectable: Pass-the-Hash requires\n"
        "     no cracking, which is what makes it dangerous -- but it leaves\n"
        "     a distinctive log signature. One account authenticating via\n"
        "     NTLM to many distinct hosts within a short window is not\n"
        "     normal human behavior; it's the signature of a hash being\n"
        "     sprayed network-wide (crackmapexec/psexec -hashes). Fix: unique\n"
        "     per-machine local admin passwords (LAPS) so one dumped hash\n"
        "     only ever works on one host, plus Credential Guard and\n"
        "     disabling NTLM where Kerberos-only is viable."
    )


if __name__ == "__main__":
    main()
