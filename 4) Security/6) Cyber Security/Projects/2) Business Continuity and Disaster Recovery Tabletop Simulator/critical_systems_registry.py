"""
critical_systems_registry.py

Defines the business-critical systems in scope for the BC/DR tabletop
exercise. Each system carries:

  - rto_target_minutes : max acceptable DOWNTIME the business will tolerate
  - rpo_target_minutes : max acceptable DATA LOSS WINDOW the business will tolerate
  - backup_frequency_minutes : how often backups are SUPPOSED to run
  - last_backup_time   : timestamp of the last KNOWN-GOOD backup (drift from
                         the intended frequency is exactly how stale-backup
                         RPO breaches happen in real incidents)
  - recovery_steps     : ordered list of (step_name, estimated_minutes) that
                         make up the documented recovery procedure
  - depends_on         : id of another system that must be fully recovered
                         first (a real dependency chain, e.g. a customer
                         portal cannot be considered "up" until the order
                         database behind it is back), or None

This module holds only static data -- no simulation logic lives here.
"""

from dataclasses import dataclass, field
from datetime import datetime
from typing import List, Optional, Tuple


@dataclass
class CriticalSystem:
    system_id: str
    display_name: str
    rto_target_minutes: int
    rpo_target_minutes: int
    backup_frequency_minutes: int
    last_backup_time: datetime
    recovery_steps: List[Tuple[str, int]] = field(default_factory=list)
    depends_on: Optional[str] = None

    @property
    def own_recovery_minutes(self) -> int:
        """Sum of this system's own recovery-step estimates (excludes any
        dependency-chain wait time -- that is computed by the simulator,
        since it requires knowledge of OTHER systems' timelines)."""
        return sum(minutes for _step, minutes in self.recovery_steps)


# ---------------------------------------------------------------------------
# The registry: four representative business-critical systems.
#
# order-database   -> clean baseline: meets both RTO and RPO
# customer-portal  -> demonstrates the dependency-chain mechanic: its own
#                     recovery steps are fast, and the wait forced by its
#                     dependency on order-database pushes its actual RTO up
#                     to 150 min -- still inside its 180-min target in this
#                     run (a PASS), but only because its target already has
#                     headroom for the upstream wait. Tighten either target
#                     to see this flip to a BREACH.
# payment-gateway  -> meets RPO (near-real-time replication), but BREACHES
#                     RTO because the documented recovery procedure is
#                     simply too slow for its aggressive 60-minute target
# email-system     -> meets RTO easily, but BREACHES RPO because its backup
#                     schedule is only nightly (a stale-backup gap) against
#                     a 60-minute RPO target that a daily backup can never
#                     satisfy, independent of how fast the restore itself is
# ---------------------------------------------------------------------------

SYSTEMS = {
    "order-database": CriticalSystem(
        system_id="order-database",
        display_name="Order Processing Database",
        rto_target_minutes=240,       # 4 hours
        rpo_target_minutes=60,        # 1 hour
        backup_frequency_minutes=30,  # transaction log shipping every 30 min
        last_backup_time=datetime(2026, 8, 18, 1, 45, 0),
        recovery_steps=[
            ("Isolate infected host from network", 15),
            ("Restore database from last known-good backup", 90),
            ("Verify data integrity / run consistency checks", 30),
            ("Bring database service back online", 15),
        ],
        depends_on=None,
    ),
    "customer-portal": CriticalSystem(
        system_id="customer-portal",
        display_name="Customer Web Portal",
        rto_target_minutes=180,       # 3 hours
        rpo_target_minutes=60,        # 1 hour
        backup_frequency_minutes=60,  # hourly config/app-state backup
        last_backup_time=datetime(2026, 8, 18, 1, 30, 0),
        recovery_steps=[
            ("Isolate portal web/app servers", 10),
            ("Redeploy application servers from clean image", 20),
            ("Reconnect portal to order database", 10),
        ],
        depends_on="order-database",  # cannot serve real traffic until this is up
    ),
    "payment-gateway": CriticalSystem(
        system_id="payment-gateway",
        display_name="Payment Gateway",
        rto_target_minutes=60,        # aggressive: PCI / revenue-critical
        rpo_target_minutes=15,        # 15 minutes
        backup_frequency_minutes=15,  # near-synchronous replication
        last_backup_time=datetime(2026, 8, 18, 1, 50, 0),
        recovery_steps=[
            ("Isolate payment processing nodes", 20),
            ("Fail over to secondary DR site", 45),
            ("Validate in-flight transactions / reconcile ledger", 30),
        ],
        depends_on=None,
    ),
    "email-system": CriticalSystem(
        system_id="email-system",
        display_name="Corporate Email System",
        rto_target_minutes=120,          # 2 hours
        rpo_target_minutes=60,           # 1 hour
        backup_frequency_minutes=1440,   # documented as hourly, but actually only nightly
        last_backup_time=datetime(2026, 8, 17, 22, 0, 0),
        recovery_steps=[
            ("Isolate mail server from network", 10),
            ("Restore mail server from last nightly backup", 45),
            ("Restore individual mailboxes", 30),
        ],
        depends_on=None,
    ),
}


def get_system(system_id: str) -> CriticalSystem:
    return SYSTEMS[system_id]


def all_systems() -> List[CriticalSystem]:
    return list(SYSTEMS.values())
