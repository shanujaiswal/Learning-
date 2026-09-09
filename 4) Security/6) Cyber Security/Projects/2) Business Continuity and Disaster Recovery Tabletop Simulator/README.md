# Business Continuity & Disaster Recovery (BC/DR) Tabletop Simulator

A stdlib-only Python simulator for a BC/DR tabletop exercise: it takes a fixed
ransomware incident, a registry of business-critical systems (with their
documented RTO/RPO targets, real backup schedules, and real recovery
procedures), and computes whether each system would *actually* meet its
recovery commitments -- including the knock-on delay caused by systems that
depend on other systems. It ends by writing a Markdown "lessons learned"
after-action report, exactly like a real BC/DR exercise produces.

No hardware abstraction, no ROS2, no robotics framework of any kind is used
or needed here -- this is a pure data/logic simulation over plain Python
dataclasses and datetime arithmetic.

## Real-world scenario

At 02:00, ransomware detonates across the production network (overnight,
when attackers know fewest staff are watching -- a deliberate, realistic
choice) and encrypts four business-critical systems at once:

- **Order Processing Database** -- the system of record for all orders.
- **Customer Web Portal** -- depends on the order database being back up
  before it can serve real traffic (a real dependency chain).
- **Payment Gateway** -- PCI/revenue-critical, with an aggressive 60-minute
  RTO target and near-synchronous replication for RPO.
- **Corporate Email System** -- documented as backed up "hourly" but, as
  actually configured, only backed up nightly -- the classic gap between a
  paper policy and what's really running.

The exercise doesn't guess at these numbers -- it walks each system through
its own documented recovery steps (isolate, restore, verify, bring back
online, etc.), measures the real elapsed time and real data-loss window
against the *actual* last-known-good backup timestamp, and folds in
dependency-chain wait time where one system can't be called "recovered"
until an upstream system is. It then checks all of that against the
committed RTO (Recovery Time Objective -- max tolerable downtime) and RPO
(Recovery Point Objective -- max tolerable data loss window), and produces a
post-incident after-action report with root causes and recommended fixes.

## Architecture

| Module | Role | Real-world equivalent |
|---|---|---|
| `critical_systems_registry.py` | Static data: each system's RTO/RPO targets, backup frequency, last-known-good backup time, ordered recovery-step estimates, and dependency link | The BC/DR plan's system inventory / asset register that a business continuity manager maintains |
| `incident_scenario.py` | Fixed incident definition: start time, narrative, which systems are declared "affected" | The tabletop exercise's facilitator script / injected scenario brief |
| `recovery_simulator.py` | Core measurement engine: computes actual RPO (incident time minus last backup) and actual RTO (own recovery steps plus any dependency-chain wait, recursively resolved), then classifies the root cause of any breach | A real DR tabletop exercise's RTO/RPO measurement and root-cause analysis step |
| `tabletop_report.py` | Compiles per-system pass/fail verdicts, root causes, and recommended fixes into a Markdown report | A post-incident BC/DR after-action report circulated to leadership |
| `main.py` | Orchestrates the run: prints the scenario, simulates every affected system, prints a summary, writes the report to disk | The facilitator running the live tabletop session end to end |

## Run it

```bash
cd "2) Business Continuity and Disaster Recovery Tabletop Simulator"
python main.py
```

No dependencies beyond the Python standard library (dataclasses, datetime,
typing) -- no `pip install` required.

## Verified result (actual output from running `main.py`)

```
Systems exercised:            4
Systems breaching commitment: 2 / 4
  RPO breaches: 1 -> ['Corporate Email System']
  RTO breaches: 1 -> ['Payment Gateway']
    - Payment Gateway: Recovery procedure too slow for RTO target
    - Corporate Email System: Stale/infrequent backup (RPO breach)
```

Per-system detail actually observed:

| System | RPO target / actual | RPO verdict | RTO target / actual | RTO verdict | Why |
|---|---|---|---|---|---|
| Order Processing Database | 60 / 15 min | PASS | 240 / 150 min | PASS | Clean baseline -- frequent log shipping, no dependency, recovery well inside target. |
| Customer Web Portal | 60 / 30 min | PASS | 180 / 150 min | PASS | Own recovery steps take only 40 min, but it must wait on `order-database` finishing first, adding 110 min of dependency-chain wait -- pushing actual RTO to 150 min. It still clears its 180-min target in this run, but only because that target has headroom for the upstream wait; tighten it and this flips to a breach. |
| Payment Gateway | 15 / 10 min | PASS | 60 / 95 min | **BREACH** | RPO is fine (10-minute-old replication). But the documented recovery procedure (isolate 20 min + failover 45 min + reconcile ledger 30 min = 95 min) simply cannot fit inside the aggressive 60-minute RTO target -- a **slow-recovery-procedure** breach, unrelated to backups. |
| Corporate Email System | 60 / 240 min | **BREACH** | 120 / 85 min | PASS | Recovery itself is fast (85 min). But the last known-good backup was 240 minutes before the incident (nightly backup, not the hourly the policy assumed) against a 60-minute RPO target -- a **stale-backup** breach, unrelated to how fast the restore runs. |

This demonstrates all three distinct breach mechanisms the simulator is
built to surface:

1. **Stale backup vs RPO target** (email system) -- fast restore doesn't
   help if too much data was already lost before the last backup ran.
2. **Slow recovery procedure vs RTO target** (payment gateway) -- fresh data
   doesn't help if the documented steps take longer than the business will
   tolerate being down.
3. **Dependency-chain delay** (customer portal) -- a system can do
   everything right on its own and still be forced to wait on an upstream
   system's recovery before it's genuinely usable.

## Things to try changing

- **Fix the email system's stale-backup RPO breach**: in
  `critical_systems_registry.py`, change `email-system`'s
  `backup_frequency_minutes` from `1440` (nightly) to something like `30` or
  `60`, and move `last_backup_time` closer to `INCIDENT_START` (e.g.
  `datetime(2026, 8, 18, 1, 30, 0)`, 30 minutes before). Re-run `main.py` --
  the RPO breach for the Corporate Email System disappears.
- **Fix the payment gateway's slow-procedure RTO breach**: shorten the
  `"Fail over to secondary DR site"` step (e.g. from 45 to 20 minutes, to
  simulate a scripted/automated failover instead of a manual one) so total
  recovery time drops under 60 minutes.
- **Push the customer portal's dependency delay into an actual breach**:
  tighten `customer-portal`'s `rto_target_minutes` from 180 to something
  like 140 (still more than its own 40-minute recovery, but less than the
  150-minute dependency-inclusive total) and re-run -- watch the root cause
  become `Dependency-chain wait time (RTO breach)`.
- **Add a new affected system**: add an entry to `SYSTEMS` in
  `critical_systems_registry.py` and its `system_id` to
  `AFFECTED_SYSTEM_IDS` in `incident_scenario.py` -- no other code changes
  are needed; the simulator and report generator both iterate the registry
  generically.
