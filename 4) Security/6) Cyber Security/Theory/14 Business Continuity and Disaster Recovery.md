### Business Continuity and Disaster Recovery

--> Chapter 5 covered the Incident Response lifecycle for a security incident specifically. This chapter covers the broader discipline of keeping (or getting) the BUSINESS running through ANY major disruption — a security incident (ransomware), but also fires, floods, hardware failure, power outages, or a data center going offline — and is a core, frequently tested topic on security certifications (CISSP, Security+) precisely because it sits at the intersection of technical controls and business/organizational planning.

## BCP vs DRP

--> These two terms are related and often confused, but they answer different questions and operate at different scopes.

--> BCP (Business Continuity Planning) is the broader, organization-wide plan for keeping CRITICAL BUSINESS FUNCTIONS running (or resuming them within an acceptable time) during and after a disruptive event — covering people, processes, facilities, communications, and alternate ways of working, not just IT systems. Example scope: if the main office building becomes unusable, where do employees work from, how does payroll still get processed, how does customer support keep answering the phone, who is authorized to make key decisions if the CEO is unreachable.
--> DRP (Disaster Recovery Planning) is the narrower, IT/technical-systems-focused SUBSET of business continuity — specifically, how to restore IT infrastructure, applications, and data after a disaster, so that the systems the business functions actually run ON are back online.
--> Relationship: DRP is a component/enabler of the larger BCP. The BCP defines WHICH business functions matter most and how fast they must be restored; the DRP is the specific technical execution plan for restoring the underlying IT systems those functions depend on, to meet the targets the BCP defines.
--> Concrete example distinguishing the two: after a ransomware attack encrypts the company's file servers, the DRP dictates the exact technical steps to restore those servers from backup and get email/file-sharing systems back online. The BCP, running in parallel, dictates how the business keeps SERVING CUSTOMERS while that recovery is underway — e.g., staff temporarily reverting to a documented manual/paper-based order-taking process, or a bank of previously-briefed customer service staff citing a pre-approved holding statement — so revenue-generating and customer-facing operations don't fully halt just because the IT systems are still down.

## RTO and RPO

--> These two metrics are the quantitative heart of both BCP and DRP — every backup strategy, DR site tier, and recovery procedure ultimately exists to hit specific RTO/RPO TARGETS that the business has decided it can tolerate for a given system.

--> RTO (Recovery Time Objective): the MAXIMUM acceptable amount of TIME a system/process can be down before the business impact becomes unacceptable. It answers: "how long can we tolerate this being OFFLINE?"
--> RPO (Recovery Point Objective): the MAXIMUM acceptable amount of DATA (measured in time) the business can tolerate losing, i.e., how far back in time the restored data is allowed to be from the moment of failure. It answers: "how much recent data can we afford to lose?"

Worked numeric example:

--> Suppose a company's order-processing database has an RTO of 4 hours and an RPO of 1 hour.
--> A ransomware attack encrypts the database server at 10:00 AM. It is fully detected, contained, and a clean restore is completed and verified by 1:30 PM.
   --> RTO check: total downtime was 3.5 hours (10:00 AM → 1:30 PM), which is UNDER the 4-hour RTO target — the recovery TIME objective was met.
--> The most recent backup available to restore from was taken at 9:15 AM (45 minutes before the attack).
   --> RPO check: data loss equals the gap between the last good backup (9:15 AM) and the moment of failure (10:00 AM) = 45 minutes, which is UNDER the 1-hour RPO target — the data loss objective was also met. Every order placed between 9:15 AM and 10:00 AM (45 minutes of transactions) is permanently lost and must be manually reconstructed or re-entered by staff/customers, but this amount of loss was within the pre-agreed, acceptable tolerance.
--> If instead the last usable backup had been taken at 6:00 AM (a 4-hour gap to the 10:00 AM failure), the RPO of 1 hour would have been MISSED even though the RTO was still met — this is the critical point students often miss: RTO and RPO are independent axes, and a recovery can succeed on one while failing the other. A fast recovery (good RTO) with a stale backup (bad RPO) is still a real failure against the org's stated tolerance.
--> Practical consequence of this example: to reliably hit a 1-hour RPO, backups (or at minimum transaction log shipping/continuous replication) must run AT LEAST every hour — a nightly-only backup schedule could never meet a 1-hour RPO no matter how fast the restore process itself is, because the GAP between backups is what defines the RPO ceiling, independent of restore speed.

## Backup Strategies

--> RPO targets are achieved through backup FREQUENCY and design; RTO targets are achieved through backup ACCESSIBILITY/restore speed and DR site readiness (covered next). Backup strategy decisions directly determine which RPO/RTO numbers are even achievable.

### The 3-2-1 Rule

--> A widely taught baseline backup rule: keep at least **3** total copies of your data, on at least **2** different types of storage media, with at least **1** copy stored OFFSITE (physically or logically separate from the primary systems/location).
--> Why each number matters: 3 copies protects against a single copy being corrupted/deleted/encrypted without leaving you with zero fallback. 2 different media types protects against a failure mode specific to one storage technology (e.g., all disks in the same RAID array/SAN failing together) taking out every copy at once. 1 offsite copy specifically protects against site-wide disasters (fire, flood, a ransomware worm that encrypts everything reachable on the local network, including locally-attached backup drives) — this is the single most commonly violated part of the rule in real-world ransomware incidents, where attackers deliberately seek out and encrypt/delete ANY backups they can reach on the same network before triggering the main encryption payload, specifically to remove the victim's ability to simply restore and ignore the ransom demand.
--> A modern extension sometimes taught as "3-2-1-1-0": adds a second "1" for at least one IMMUTABLE or air-gapped copy (cannot be modified/deleted even by an attacker holding valid admin credentials, for a defined retention window) and a "0" meaning zero errors after regularly, actually VERIFYING backups restore correctly — a backup that has never been test-restored is not a verified backup, it's an assumption.

### Full, Incremental, and Differential Backups

--> Full backup: copies ALL selected data, every time, regardless of what has or hasn't changed since the last backup. Simplest to restore from (only one backup set needed) but the slowest to run and the most storage-intensive if done frequently.
--> Incremental backup: copies only the data that has changed since the LAST backup of ANY type (full or incremental). Fast to run and storage-efficient, but restoring requires the last full backup PLUS every single incremental backup made since then, applied in the correct order.
--> Differential backup: copies all data that has changed since the LAST FULL backup specifically (not since the last differential). Restoring only ever needs the last full backup plus the single most recent differential — simpler and faster to restore than incremental chains, at the cost of each differential backup growing larger over the week as more changes accumulate since the last full.

Worked example schedule — a common real-world pattern: full backup every Sunday, incremental every other day of the week, restoring on a Thursday after a Wednesday-night failure:

```
Sun: FULL        (100 GB — everything)
Mon: INCREMENTAL (5 GB  — changed since Sunday's full)
Tue: INCREMENTAL (4 GB  — changed since Monday's incremental)
Wed: INCREMENTAL (6 GB  — changed since Tuesday's incremental)   <- failure occurs Wed night
```
--> Restore path required: Sunday's FULL, then Monday's INCREMENTAL, then Tuesday's INCREMENTAL, then Wednesday's INCREMENTAL, applied strictly in that order. If even ONE incremental in that chain is missing or corrupted, every incremental after it becomes unusable too — this is incremental's core restore-time risk.

--> The equivalent week using DIFFERENTIAL instead of incremental:
```
Sun: FULL         (100 GB — everything)
Mon: DIFFERENTIAL (5 GB  — changed since Sunday's full)
Tue: DIFFERENTIAL (9 GB  — changed since Sunday's full, growing)
Wed: DIFFERENTIAL (15 GB — changed since Sunday's full, growing further)  <- failure occurs Wed night
```
--> Restore path required: only Sunday's FULL plus Wednesday's DIFFERENTIAL — just two backup sets regardless of which day of the week the failure happened on, at the cost of Wednesday's differential being noticeably larger than an equivalent incremental would have been, since it re-captures every change back to Sunday each time rather than only since the previous day.
--> Practical trade-off summary: incremental = smaller/faster backups, slower and more fragile restores (long dependency chain). Differential = larger/slower backups as the week progresses, faster and simpler restores (always just two sets). Many real backup schedules combine both concepts with periodic synthetic fulls to keep both backup time AND restore complexity bounded.

## Hot, Warm, and Cold Sites

--> A DR SITE is an alternate location/infrastructure the organization can fail over to when the primary site/systems are unavailable. The three tiers trade off cost against how quickly they can achieve a target RTO.

--> Hot site: a fully equipped, continuously running, near-real-time-synchronized duplicate of the production environment, ready to take over within minutes with little to no data loss. Achieves the shortest possible RTO (and typically the shortest RPO too, given continuous replication) but is by far the most expensive to maintain, since it's effectively running and being kept in sync as a full parallel production environment 24/7 even while doing nothing.
--> Warm site: a partially equipped, ready-ish environment (hardware/infrastructure exists and is periodically updated/patched, but data is only synced on a scheduled basis — e.g., nightly — rather than continuously) that requires some additional setup/activation time and accepts somewhat more data loss (a larger RPO gap) than a hot site, in exchange for meaningfully lower ongoing cost.
--> Cold site: essentially bare infrastructure/space (power, cooling, network connectivity exist) with NO pre-installed systems or synced data ready to go — hardware must be procured/configured and data restored from backups from scratch during an actual disaster. Cheapest to maintain on an ongoing basis, but has by far the longest RTO (often measured in days, not hours) since almost everything has to be built and restored during the event itself, under pressure, rather than beforehand.
--> Choosing between these three is fundamentally an economic decision made in direct response to the RTO/RPO targets defined earlier in this chapter: a system with a genuinely aggressive 15-minute RTO structurally REQUIRES a hot site (nothing cheaper can physically achieve that speed); a system whose RTO tolerance is measured in days can reasonably use a much cheaper cold site instead — spending hot-site money on a system that could tolerate a cold site's timeline is simply wasted budget, and the reverse (cold-site infrastructure for a system that needs a 15-minute RTO) is a plan that will provably fail its own stated objective the moment it's actually tested.
--> Cloud computing has significantly blurred these tiers in modern practice — infrastructure-as-code and cloud provider multi-region replication let organizations approximate "hot site" recovery speed at something closer to "warm site" ongoing cost, by only paying for standby compute capacity when it's actually spun up during a failover rather than keeping a full duplicate data center running idle year-round.

## Tabletop Exercises and DR Drills

--> A written BCP/DRP document that has never actually been TESTED is, in practice, closer to a work of hopeful fiction than a reliable plan — untested assumptions about who's available, whether backups actually restore cleanly, and whether documented steps still match how systems actually work today routinely turn out to be wrong the moment they're needed for real, and discovering that during an actual disaster is the worst possible time to learn it.
--> A tabletop exercise is a structured, DISCUSSION-BASED walkthrough of a simulated disaster scenario — participants talk through what they would do at each stage, without actually executing any real technical failover — used to validate the PLAN and people's understanding of their roles at comparatively low cost/disruption. A full-scale DR drill goes further and actually executes some or all of the technical recovery steps for real (e.g., genuinely failing over to the warm site and confirming the application actually comes up and serves real traffic correctly) — more disruptive/costly to run, but the only way to truly validate that the technical recovery process actually works as documented, not just that it sounds plausible on paper.

Example tabletop scenario walkthrough — "Ransomware Encrypts the Primary File Server," run as a facilitated discussion with IT, security, legal, communications, and executive stakeholders in the room:

1. Facilitator presents the scenario: "It's 6 AM Monday. The primary file server and its directly-attached backup drive are both fully encrypted by ransomware. The attacker has left a ransom note demanding payment in 48 hours. Go."
2. IT lead is asked: "What's our actual RTO/RPO for this specific system, and can we realistically hit it right now, given what you know about our current backup state?" — this immediately tests whether the DOCUMENTED targets match CURRENT reality, since infrastructure and backup configurations drift over time and plans are rarely updated to match.
3. Someone on the call points out that the offsite backup copy (the "1" in 3-2-1) hasn't actually been TEST-RESTORED in eight months — the exercise has just surfaced a real, previously invisible gap ("0 errors, verified" was silently not being upheld) entirely through a discussion, at zero cost to production, rather than discovering that same gap for real during an actual live ransomware incident with the clock already running.
4. Legal/communications stakeholders are asked: "Who is authorized to decide whether we pay the ransom, and who talks to customers/press if this leaks, and what do we actually say?" — surfacing that no such authority or pre-approved communications template currently exists, which is exactly the kind of BCP-level (not purely technical DRP) gap tabletop exercises exist to catch.
5. Facilitator introduces a complication mid-exercise: "The one employee who knows how to operate the backup restoration tool is on vacation, unreachable." — deliberately stress-testing single points of failure in the PEOPLE side of the plan, not just the technology side.
6. After the exercise, every gap surfaced (untested backup, undefined ransom-payment authority, single-person dependency on backup operations) becomes a formal action item with an owner and a deadline — directly mirroring the "Lessons Learned" phase of the IR lifecycle from Chapter 5, applied here proactively, before a real incident, rather than reactively after one.

--> Cadence matters: mature organizations run tabletop exercises at least annually (often more frequently for their highest-criticality systems) and rotate through different disaster scenarios (ransomware, data center fire, key-person unavailability, cloud provider regional outage) rather than repeatedly rehearsing the same single scenario, since different disaster types tend to stress entirely different parts of the plan.
