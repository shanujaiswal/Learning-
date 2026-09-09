# SRE as a Discipline, Not Just a Job Title

--> Site Reliability Engineering (SRE), a practice originating at Google, treats operations as a software engineering problem -- reliability targets are defined numerically and tracked like any other product requirement, rather than "keep it up" being a vague, unmeasured expectation.
--> The tooling covered in the previous file (Prometheus, Grafana, the ELK stack, Datadog/New Relic/Splunk) is what makes SRE practice possible at all -- SLIs (below) are measurements pulled directly from that observability data.

# SLIs, SLOs and SLAs

--> SLI (Service Level Indicator) -- an actual, measured metric of how the service is performing, e.g. "percentage of requests served in under 200ms," "percentage of requests that succeed (non-5xx)." This is a specific PromQL-style query (covered in the previous file) against real data, not an aspiration.
--> SLO (Service Level Objective) -- an internal target for that SLI, e.g. "99.9% of requests succeed, measured over a rolling 30 days." SLOs are how a team defines "reliable enough" concretely instead of arguing about it during an incident.
--> SLA (Service Level Agreement) -- an SLO that's been turned into an external, often contractual promise to customers, typically with financial penalties/credits if missed -- an SLA should always be set looser than the internal SLO, so there's margin to catch problems and improve before an externally-promised threshold is actually breached.

```promql
# The SLI query behind an "availability" SLO
sum(rate(http_requests_total{status!~"5.."}[30d]))
  /
sum(rate(http_requests_total[30d]))
```

## Error Budgets

--> An error budget is simply 100% minus the SLO, turned into a spendable allowance -- a 99.9% availability SLO means a 0.1% error budget, i.e. roughly 43 minutes of full downtime-equivalent per month before the SLO is breached.
--> Why this reframing matters -- it turns "should we ship this risky change" from a subjective argument into a data-driven question: if the error budget is nearly exhausted, the team should slow down, prioritize reliability work, and freeze risky releases; if there's plenty of budget left, shipping fast and taking calculated risks (a new feature, an experimental infra change) is explicitly justified rather than something to feel nervous about.
--> This is what gives feature-flag-driven, trunk-based release practice (covered in the CI-CD Concepts file) its safety net in practice -- error budgets are the quantified version of "how much risk can we actually afford to take on right now."

## Toil

--> Toil -- manual, repetitive, automatable operational work that scales linearly with service size and produces no lasting engineering value (manually restarting a crashed process, manually running the same deploy checklist every time, manually resizing a disk that's about to fill up).
--> Google's SRE practice caps the proportion of an SRE's time spent on toil (a commonly cited guideline is under 50%) specifically because unchecked toil crowds out the engineering work (automation, better tooling, architecture fixes) that would reduce future toil -- a team stuck doing 100% toil never gets time to fix the root causes creating that toil.
--> Nearly everything else covered in this DevOps and Cloud folder -- GitOps's self-healing reconciliation, autoscaling, CI/CD automation, Infrastructure as Code -- is, from an SRE lens, toil-elimination tooling: each one replaces a manual, repetitive human action with an automated system.

# Incident Response Process

--> Runbook -- a written, step-by-step procedure for responding to a specific, known failure mode ("database connection pool exhausted: check X, restart Y, escalate to Z if unresolved after 10 minutes") -- exists so the response to a known problem doesn't depend on which specific engineer happens to be on-call and how well they remember the last time it happened.
--> On-call rotation -- a schedule (commonly managed via a tool like PagerDuty or Opsgenie) determining who gets paged first when an alert fires (from Alertmanager, covered in the previous file, or a commercial platform's alerting) -- typically rotates weekly among a team, with a secondary/escalation on-call as backup if the primary doesn't acknowledge in time.

## Severity Levels

--> Incidents are typically classified by severity (commonly SEV1 through SEV4 or similar) to standardize how much urgency/response a given incident gets -- SEV1 (full outage, all customers affected, all-hands-on-deck immediate response) down to SEV4 (minor, contained, non-urgent, can be fixed during business hours).
--> Standardized severity levels are what make on-call sustainable -- without them, every alert tends to get treated as maximally urgent, which burns out an on-call rotation quickly; a clear severity rubric lets the on-call engineer correctly de-prioritize a low-severity issue until working hours.

## Postmortems and Blameless Retrospectives

--> A postmortem is a written record produced after any significant incident -- timeline of what happened, root cause, what actually stopped the bleeding, and concrete follow-up action items with owners and deadlines.
--> Blameless -- the postmortem process explicitly focuses on systemic/process causes ("the deploy pipeline let an unreviewed config change reach production," "there was no alert for this failure mode") rather than individual blame ("engineer X made a mistake") -- this isn't just a cultural nicety, it's what makes people willing to report and discuss incidents honestly instead of hiding or downplaying them out of fear.
--> Action items from a postmortem are the mechanism that actually turns an incident into future reliability improvement -- a postmortem that just documents what happened without concrete, tracked follow-up work tends to see the same failure mode repeat.

```markdown
# Postmortem template (abbreviated)
## Summary
Checkout service returned 5xx for 12 minutes due to a database connection pool exhaustion.

## Timeline
14:02 - Alert fired: HighErrorRate on checkout-service
14:05 - On-call acknowledged, began investigating connection pool metrics
14:14 - Root cause identified: a recent deploy increased pool size below the new expected load
14:14 - Rolled back deploy, service recovered

## Root Cause
Connection pool size was not adjusted alongside a traffic-pattern change in the prior release.

## Action Items
- [ ] Add an alert on connection pool saturation before exhaustion (Owner: A, Due: Aug 27)
- [ ] Add pool sizing to the pre-deploy checklist/runbook (Owner: B, Due: Aug 25)
```

# Chaos Engineering

--> Chaos engineering deliberately injects failure into a system (in a controlled way) to verify it actually degrades/recovers the way it's assumed to -- rather than discovering a resilience gap for the first time during a real, unplanned production incident.
--> Chaos Monkey -- Netflix's original tool (part of the broader "Simian Army"), which randomly terminates production instances during business hours -- forces every service to actually be built to survive instance failure, since a failure could be injected at any time, rather than resilience being an untested assumption.
--> Gremlin -- a commercial chaos engineering platform generalizing the same idea beyond just killing instances -- injecting network latency, CPU/memory pressure, DNS failures, or dependency outages on demand, with safety controls (blast radius limits, an easy abort) so experiments stay controlled rather than becoming an actual outage.
--> Fault injection -- the general technique underlying both tools: deliberately introduce a specific failure (kill a pod, add 500ms of network latency, block egress to a dependency) and observe whether the system's alerts, dashboards (from the previous file), and automated recovery (Kubernetes self-healing, autoscaling, retries) behave as designed.
--> Why this connects back to error budgets -- chaos experiments are themselves a controlled way to "spend" a small amount of error budget deliberately, in order to find and fix a resilience gap BEFORE it consumes a much larger amount of error budget unpredictably during a real incident.
--> Prerequisite for running chaos experiments responsibly -- solid observability (the previous file's Prometheus/Grafana/logging stack) and a practiced incident response process (this file) need to already exist; injecting failure into a system you can't observe or respond to just creates a real outage with extra steps.
