# BC/DR Tabletop Exercise -- Lessons Learned Report

**Scenario:** Ransomware Encryption of Core Production Systems
**Incident start:** 2026-08-18 02:00
**Systems exercised:** 4
**Systems breaching commitments:** 2 / 4

## Narrative

At 02:00 on 2026-08-18, a ransomware payload detonated across the production network, encrypting the order database, customer portal application servers, payment gateway processing nodes, and the corporate email server. IT and security teams were paged immediately. This exercise walks through the recovery of each affected system against its documented RTO/RPO commitments, using the ACTUAL state of each system's last backup and recovery procedure -- not the aspirational, on-paper version of either.

## Executive Summary

Of 4 business-critical systems exercised, **2** would have breached at least one BC/DR commitment in a real incident: **1** RPO breach(es) and **1** RTO breach(es).

## Per-System Results

| System | RPO Target | Actual RPO | RPO Verdict | RTO Target | Actual RTO | RTO Verdict |
|---|---|---|---|---|---|---|
| Order Processing Database | 60 min | 15 min | PASS | 240 min | 150 min | PASS |
| Customer Web Portal | 60 min | 30 min | PASS | 180 min | 150 min | PASS |
| Payment Gateway | 15 min | 10 min | PASS | 60 min | 95 min | **BREACH** |
| Corporate Email System | 60 min | 240 min | **BREACH** | 120 min | 85 min | PASS |

## Detail, Root Cause, and Recommended Fixes

### Order Processing Database (`order-database`)

- Last known-good backup: `2026-08-18 01:45` (backup frequency: every 30 min)
- Actual data-loss window (RPO): **15 min** vs target **60 min** -> PASS
- Own recovery-step time: 150 min
- Actual recovery time (RTO): **150 min** vs target **240 min** -> PASS
- Root cause: none -- this system met both commitments in this exercise.

### Customer Web Portal (`customer-portal`)

- Last known-good backup: `2026-08-18 01:30` (backup frequency: every 60 min)
- Actual data-loss window (RPO): **30 min** vs target **60 min** -> PASS
- Own recovery-step time: 40 min + dependency-chain wait: 110 min (waiting on `order-database`)
- Actual recovery time (RTO): **150 min** vs target **180 min** -> PASS
- Root cause: none -- this system met both commitments in this exercise.

### Payment Gateway (`payment-gateway`)

- Last known-good backup: `2026-08-18 01:50` (backup frequency: every 15 min)
- Actual data-loss window (RPO): **10 min** vs target **15 min** -> PASS
- Own recovery-step time: 95 min
- Actual recovery time (RTO): **95 min** vs target **60 min** -> **BREACH**
- **Root cause(s):** Recovery procedure too slow for RTO target
- **Recommended fix(es):**
  - Re-engineer or automate the slowest recovery steps (e.g. scripted failover instead of manual failover, pre-staged DR-site images) so the procedure's total estimated time fits under the RTO target, or renegotiate the RTO target with the business if the current cost of a faster procedure isn't justified.

### Corporate Email System (`email-system`)

- Last known-good backup: `2026-08-17 22:00` (backup frequency: every 1440 min)
- Actual data-loss window (RPO): **240 min** vs target **60 min** -> **BREACH**
- Own recovery-step time: 85 min
- Actual recovery time (RTO): **85 min** vs target **120 min** -> PASS
- **Root cause(s):** Stale/infrequent backup (RPO breach)
- **Recommended fix(es):**
  - Increase backup frequency so the worst-case gap between backups no longer exceeds the RPO target (e.g. move from nightly to hourly, or add continuous log shipping/replication). Also verify offsite/immutable copies are actually current -- a documented schedule that silently isn't being run is the same as having no backup at all.

## Action Items

- [ ] **Payment Gateway**: address Recovery procedure too slow for RTO target -- owner: TBD, deadline: TBD
- [ ] **Corporate Email System**: address Stale/infrequent backup (RPO breach) -- owner: TBD, deadline: TBD
