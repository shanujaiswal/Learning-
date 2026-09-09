"""
ad_directory_simulator.py

A purely in-memory, abstract model of an Active Directory domain.

IMPORTANT: This is a data-structure simulation for security education.
There is no real Kerberos protocol, no real network traffic, no real
password hashing algorithm implementation, and no connection to any
real machine or domain. "Password strength" and "hashes" below are
simplified stand-ins used only to make the detection-logic lessons
concrete.

Objects modeled:
    - UserAccount      : a normal human domain user (no SPN).
    - ServiceAccount    : an account with a Service Principal Name (SPN)
                          set, i.e. the kind of account that runs a
                          Windows service (SQL Server, IIS app pool, ...).
                          This is the class of account Kerberoasting
                          targets.
    - MachineAccount    : a domain-joined computer account (a valid
                          Kerberoasting/PtH lateral-movement target host).
"""

from __future__ import annotations

import math
from dataclasses import dataclass, field


def estimate_password_entropy_bits(password: str) -> float:
    """Rough, simplified entropy estimate: length * log2(charset size).

    This is NOT a cryptographic strength model -- it is a simulated
    stand-in used purely to decide, for lab purposes, whether an
    abstract "offline crack" would realistically succeed within a
    reasonable simulated time budget.
    """
    if not password:
        return 0.0

    charset_size = 0
    if any(c.islower() for c in password):
        charset_size += 26
    if any(c.isupper() for c in password):
        charset_size += 26
    if any(c.isdigit() for c in password):
        charset_size += 10
    if any(not c.isalnum() for c in password):
        charset_size += 32

    charset_size = max(charset_size, 1)
    return len(password) * math.log2(charset_size)


@dataclass
class UserAccount:
    """A normal human domain account -- no SPN, not a Kerberoasting target."""
    username: str
    display_name: str
    simulated_password: str  # never a "real" password, purely a lab label


@dataclass
class ServiceAccount:
    """A domain account with a Service Principal Name (SPN) registered.

    Real-world equivalent: an account like `svc_sql` running MSSQL, or
    `svc_web` running an IIS application pool -- exactly the kind of
    account real Kerberoasting attacks (MITRE ATT&CK T1558.003) target,
    because the KDC will hand out a service ticket encrypted with this
    account's key to *any* authenticated domain user who asks, with no
    extra privilege required.
    """
    username: str
    spn: str
    simulated_password: str
    description: str = ""

    @property
    def entropy_bits(self) -> float:
        return estimate_password_entropy_bits(self.simulated_password)


@dataclass
class MachineAccount:
    """A domain-joined computer account -- a lateral-movement target host."""
    hostname: str
    role: str = "workstation"


@dataclass
class ADDomain:
    """The whole simulated domain: users, SPN service accounts, machines."""
    name: str
    users: list[UserAccount] = field(default_factory=list)
    service_accounts: list[ServiceAccount] = field(default_factory=list)
    machines: list[MachineAccount] = field(default_factory=list)

    def find_service_account(self, username: str) -> ServiceAccount | None:
        for sa in self.service_accounts:
            if sa.username == username:
                return sa
        return None


def build_lab_domain() -> ADDomain:
    """Construct a small, fixed sample domain: corp.local.

    Mix of SPN service accounts with intentionally weak simulated
    passwords (short / dictionary-style, low simulated entropy) and
    ones with strong simulated passwords (long, high simulated
    entropy, gMSA-style) so the crack-feasibility check in
    kerberoasting_sim.py has something meaningful to distinguish.
    """
    domain = ADDomain(name="corp.local")

    domain.users = [
        UserAccount("alice", "Alice Chen", "Sunshine2024!Rand#88x"),
        UserAccount("bob", "Bob Martins", "Tr33House_92kQ!"),
        UserAccount("carol", "Carol Ito", "Zx9#mPq2_LongEnough!"),
    ]

    # Service accounts (SPN set) -- the Kerberoasting target pool.
    domain.service_accounts = [
        # --- Weak / crackable (short, dictionary-style passwords) ---
        ServiceAccount(
            username="svc_sql",
            spn="MSSQLSvc/sql01.corp.local:1433",
            simulated_password="Summer2023",
            description="SQL Server service account (legacy, never rotated)",
        ),
        ServiceAccount(
            username="svc_web",
            spn="HTTP/web01.corp.local",
            simulated_password="Password1",
            description="IIS application pool identity",
        ),
        ServiceAccount(
            username="svc_backup",
            spn="HOST/backup01.corp.local",
            simulated_password="backup123",
            description="Backup agent service account",
        ),
        # --- Strong / not realistically crackable ---
        ServiceAccount(
            username="svc_gmsa_reporting",
            spn="MSSQLSvc/reportsrv.corp.local:1433",
            simulated_password="Qx7#vL2$mZp9!eR4_wK8Tn0@Yf",
            description="Reporting service (gMSA-style, auto-rotated)",
        ),
        ServiceAccount(
            username="svc_app_gateway",
            spn="HTTP/gateway.corp.local",
            simulated_password="9fT!kD2_qW7pXo5&rL1sMv6#Zc3",
            description="API gateway service account, 25+ char random password",
        ),
    ]

    domain.machines = [
        MachineAccount("sql01.corp.local", role="database server"),
        MachineAccount("web01.corp.local", role="web server"),
        MachineAccount("backup01.corp.local", role="backup server"),
        MachineAccount("fileserver01.corp.local", role="file server"),
        MachineAccount("hr-ws07.corp.local", role="workstation"),
        MachineAccount("fin-ws12.corp.local", role="workstation"),
        MachineAccount("dev-ws03.corp.local", role="workstation"),
        MachineAccount("dc01.corp.local", role="domain controller"),
    ]

    return domain


if __name__ == "__main__":
    d = build_lab_domain()
    print(f"Simulated domain: {d.name}")
    print(f"  Users: {[u.username for u in d.users]}")
    print(f"  SPN service accounts: {[s.username for s in d.service_accounts]}")
    print(f"  Machines: {[m.hostname for m in d.machines]}")
