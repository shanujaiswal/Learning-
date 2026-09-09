"""
incident_scenario.py

Defines the fixed ransomware incident used for this tabletop exercise:
a single incident-start timestamp, and the list of systems the facilitator
declares "affected" (i.e. in scope for this run of the exercise).

Nothing here is randomized -- a tabletop exercise works from a fixed,
agreed-upon scenario so results are reproducible and discussable.
"""

from datetime import datetime

from critical_systems_registry import all_systems

# The moment the ransomware payload actually detonated / was first observed
# encrypting production systems. 2 AM is deliberately chosen -- overnight
# incidents are common in real ransomware attacks (attackers time detonation
# for when the fewest staff are watching) and stress backup/on-call gaps.
INCIDENT_START = datetime(2026, 8, 18, 2, 0, 0)

INCIDENT_NAME = "Ransomware Encryption of Core Production Systems"

INCIDENT_NARRATIVE = (
    "At 02:00 on 2026-08-18, a ransomware payload detonated across the "
    "production network, encrypting the order database, customer portal "
    "application servers, payment gateway processing nodes, and the "
    "corporate email server. IT and security teams were paged immediately. "
    "This exercise walks through the recovery of each affected system "
    "against its documented RTO/RPO commitments, using the ACTUAL state "
    "of each system's last backup and recovery procedure -- not the "
    "aspirational, on-paper version of either."
)

# All four registered systems are considered affected by this scenario.
AFFECTED_SYSTEM_IDS = [system.system_id for system in all_systems()]
