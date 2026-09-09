"""
recovery_simulator.py

The core measurement engine of the tabletop exercise. Given the incident
scenario and the critical systems registry, computes for every affected
system:

  - actual RPO achieved  = incident time - last known-good backup time
  - actual RTO achieved  = sum of this system's own recovery steps, PLUS
                           any wait time forced by a dependency chain (a
                           system that depends on another cannot be
                           considered fully recovered until that other
                           system's own recovery -- including ITS
                           dependency chain -- has completed)

...and compares both against the system's documented targets, producing a
pass/fail verdict plus a root-cause classification for any breach.
"""

from dataclasses import dataclass
from datetime import datetime, timedelta
from typing import Dict, List, Optional

from critical_systems_registry import CriticalSystem, all_systems, get_system

# Root-cause labels used in both the console timeline and the report.
CAUSE_STALE_BACKUP = "Stale/infrequent backup (RPO breach)"
CAUSE_SLOW_PROCEDURE = "Recovery procedure too slow for RTO target"
CAUSE_DEPENDENCY_DELAY = "Dependency-chain wait time (RTO breach)"


@dataclass
class RecoveryResult:
    system_id: str
    display_name: str

    # RPO
    rpo_target_minutes: int
    actual_rpo_minutes: float
    rpo_pass: bool

    # RTO
    rto_target_minutes: int
    own_recovery_minutes: int
    dependency_wait_minutes: float
    actual_rto_minutes: float
    rto_pass: bool

    # Timeline (for console / report readability)
    recovery_start_time: datetime
    recovery_complete_time: datetime

    depends_on: Optional[str]
    root_causes: List[str]


def _minutes_between(later: datetime, earlier: datetime) -> float:
    return (later - earlier).total_seconds() / 60.0


def _resolve_recovery_complete_time(
    system_id: str,
    incident_time: datetime,
    memo: Dict[str, datetime],
) -> datetime:
    """Recursively resolve the wall-clock time at which `system_id` is FULLY
    recovered, accounting for its dependency chain. All systems start their
    OWN recovery work at incident time in parallel (isolate-and-restore
    begins immediately for everyone), but a system with a dependency cannot
    be considered complete until whichever is later of:
        (a) its own recovery steps finishing, or
        (b) the system it depends on finishing (including THAT system's
            own dependency chain)
    This mirrors reality: teams work each system's restore in parallel, but
    a dependent system still can't go live until what it depends on is up.
    """
    if system_id in memo:
        return memo[system_id]

    system = get_system(system_id)
    own_complete = incident_time + timedelta(minutes=system.own_recovery_minutes)

    if system.depends_on is None:
        complete_time = own_complete
    else:
        dependency_complete = _resolve_recovery_complete_time(
            system.depends_on, incident_time, memo
        )
        complete_time = max(own_complete, dependency_complete)

    memo[system_id] = complete_time
    return complete_time


def simulate_system(
    system_id: str,
    incident_time: datetime,
    memo: Optional[Dict[str, datetime]] = None,
) -> RecoveryResult:
    """Compute the full RTO/RPO verdict for one system."""
    if memo is None:
        memo = {}

    system = get_system(system_id)

    # --- RPO: real data-loss window vs target ---------------------------
    actual_rpo_minutes = _minutes_between(incident_time, system.last_backup_time)
    rpo_pass = actual_rpo_minutes <= system.rpo_target_minutes

    # --- RTO: real recovery time vs target, including dependency wait ---
    recovery_complete_time = _resolve_recovery_complete_time(
        system_id, incident_time, memo
    )
    recovery_start_time = incident_time
    actual_rto_minutes = _minutes_between(recovery_complete_time, incident_time)

    own_recovery_minutes = system.own_recovery_minutes
    if system.depends_on is not None:
        dependency_complete = memo[system.depends_on]
        own_complete = incident_time + timedelta(minutes=own_recovery_minutes)
        # Wait time actually imposed by the dependency chain beyond this
        # system's own work (0 if the dependency finishes before we would
        # have finished our own steps anyway).
        dependency_wait_minutes = max(
            0.0, _minutes_between(dependency_complete, own_complete)
        )
    else:
        dependency_wait_minutes = 0.0

    rto_pass = actual_rto_minutes <= system.rto_target_minutes

    # --- Root cause classification for any breach ------------------------
    root_causes: List[str] = []
    if not rpo_pass:
        root_causes.append(CAUSE_STALE_BACKUP)
    if not rto_pass:
        if dependency_wait_minutes > 0:
            root_causes.append(CAUSE_DEPENDENCY_DELAY)
        else:
            root_causes.append(CAUSE_SLOW_PROCEDURE)

    return RecoveryResult(
        system_id=system.system_id,
        display_name=system.display_name,
        rpo_target_minutes=system.rpo_target_minutes,
        actual_rpo_minutes=actual_rpo_minutes,
        rpo_pass=rpo_pass,
        rto_target_minutes=system.rto_target_minutes,
        own_recovery_minutes=own_recovery_minutes,
        dependency_wait_minutes=dependency_wait_minutes,
        actual_rto_minutes=actual_rto_minutes,
        rto_pass=rto_pass,
        recovery_start_time=recovery_start_time,
        recovery_complete_time=recovery_complete_time,
        depends_on=system.depends_on,
        root_causes=root_causes,
    )


def simulate_all(
    system_ids: List[str], incident_time: datetime
) -> Dict[str, RecoveryResult]:
    """Simulate every system, sharing one memo so dependency lookups resolve
    consistently regardless of iteration order."""
    memo: Dict[str, datetime] = {}
    results: Dict[str, RecoveryResult] = {}
    for system_id in system_ids:
        results[system_id] = simulate_system(system_id, incident_time, memo)
    return results
