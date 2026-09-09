# CPU Scheduler Security Impact Simulator

Fairness and Denial-of-Service via Scheduling Abuse

## Real-world scenario

The OS scheduler decides which of many ready processes gets the CPU next.
That decision is a security boundary, not just a performance knob: on a
shared machine (a multi-tenant server, a CI runner, a shell box with
multiple logged-in users), a **local, unprivileged process can deny service
to every other process on the box purely through how it behaves in the
scheduler's eyes** -- no exploit, no elevated privilege required.

Two classic abuse patterns are demonstrated here:

- **The convoy hog (FCFS abuse).** First-Come-First-Served has no concept
  of priority or fairness -- it just runs whoever arrived first, to
  completion, before touching anything else. A single hostile process that
  shows up early with a long burst (a runaway script, a fork bomb's first
  child, an unbounded loop) forces every legitimate process behind it to
  wait for the *entire* thing to finish. This is the textbook "convoy
  effect", except here it is deliberately triggered as a local DoS.

- **The priority flood (Priority-scheduling abuse).** Priority schedulers
  without aging always run the best-ready-priority process next, forever.
  A script that keeps spawning many cheap, best-priority helper processes
  (`nice`-abuse, or an attacker who can set `SCHED_FIFO`/high priority)
  keeps the ready queue permanently non-empty with "better" work, so any
  legitimate mid-priority process can be delayed indefinitely even though
  it individually needs very little CPU. This is priority starvation /
  priority inversion turned into a deliberate resource-exhaustion attack.

Real operating systems mitigate exactly this: Linux's CFS gives every task
a fair share of a "virtual runtime" instead of FCFS ordering; `nice`/`renice`
bound how much priority a process can claim; and classic priority schedulers
add **aging** (a waiting process's effective priority improves the longer it
waits) specifically to give a starvation *upper bound* guarantee. This
project reproduces both attacks in software and measures, with real numbers,
how much each mitigation actually helps.

## Architecture

| Module | Role | Real-world equivalent |
|---|---|---|
| `process_workload.py` | Generates one fixed, reproducible (seeded) mixed workload: legitimate jobs + a "convoy hog" + a "priority flood" of cheap hostile processes | A real mixed-tenant workload: normal user jobs plus a runaway/malicious process |
| `schedulers.py` | Implements FCFS, Round Robin (configurable quantum), and Priority scheduling (with/without aging) as pure functions over the workload | Linux CFS vs. an O(1)/static-priority scheduler design tradeoff; `nice`/aging vs. no aging |
| `fairness_analyzer.py` | Computes per-run wait-time statistics, Jain's Fairness Index, and a **relative** (per-run, outlier-based) starvation flag | The kind of fairness auditing a kernel scheduler benchmark (e.g. `hackbench`, `schedtool` studies) or an SRE fairness dashboard would do |
| `main.py` | Runs the identical hostile workload through all 4 policy configurations, prints the comparison, and renders `scheduler_fairness_result.png` | A scheduler policy A/B test / security regression test |

**Priority convention**: lower number = higher scheduling priority (matches
Unix `nice`, where `nice -n -5` is *more* favored than `nice -n 10`).

**Waiting time** (standard definition, valid for any of the three policies
here since no process ever blocks on I/O):

```
turnaround_time = completion_time - arrival_time
waiting_time    = turnaround_time - burst_time
```

**Starvation is defined relative to the run, not by a fixed cutoff.**
Total CPU demand in the workload is fixed -- no policy can make the *total*
waiting disappear, only decide who bears it. So a process is flagged
`STARVED` only if its own wait is more than **2x that run's own mean wait**
(and at least 10 time units) -- i.e. it was treated far worse than its
peers *in that same run*, which is what "unfair" actually means. This is
what correctly distinguishes "the whole system is under heavy load" (raises
everyone's wait together, nobody flagged) from "this policy is unfair"
(a few processes absorb queueing pain that should have been spread evenly).

## Run it

```bash
pip install numpy matplotlib
python main.py
```

Outputs a full per-policy report to the console (completion order, wait
times, starvation flags, Jain's index) plus `scheduler_fairness_result.png`.

To inspect the raw workload on its own:

```bash
python process_workload.py
```

## Verified result

Actual output from `python main.py` (seed = 42, workload = 10 legitimate
jobs + 1 convoy hog (burst 20) + 10 priority-flood processes (burst 1-2
each), 21 processes total, 85 total CPU-time units of demand):

| Policy | Jain's index (all) | Jain's index (legit only) | Mean wait (all) | Mean wait (legit) | Mean wait (hostile) | Max wait | Legit processes STARVED |
|---|---|---|---|---|---|---|---|
| FCFS | 0.873 | 0.908 | **40.48** | 40.20 | 40.73 | 62 | **0** |
| Priority, no aging | **0.455** | 0.901 | 17.43 | 29.70 | 6.27 | 65 | **3** (`L7`, `L8`, `L10`) |
| Priority, with aging | 0.698 | 0.822 | 32.33 | 38.40 | 26.82 | 65 | **0** |
| Round Robin (quantum=4) | 0.823 | 0.868 | 34.86 | 39.70 | 30.45 | 65 | **0** |

Two different, both-real security failure modes show up, and the metrics
correctly tell them apart:

- **FCFS: a "throughput DoS", not a selective one.** Nobody is flagged as
  a relative outlier (Jain's index looks deceptively okay, 0.873) because
  the single convoy hog delays *everyone* by roughly the same amount --
  but that "everyone" figure is the **worst mean wait of all four
  policies tested (40.48)**. The hog silently taxes the whole system's
  throughput. This is exactly why Jain's index alone is not enough --
  you need mean/max wait alongside it to catch a uniform-but-severe DoS.

- **Priority (no aging): a selective, targeted starvation.** Jain's index
  collapses to 0.455 -- the worst of any policy -- because the hostile
  flood processes finish almost immediately (mean hostile wait: **6.27**)
  while three specific legitimate jobs (`L7`, `L8`, `L10`, the
  latest-arriving ones) are starved, waiting 36-46 time units for work that
  needed only 4-6. This is the disproportionate-CPU-share signature the
  theory predicts: cheap, best-priority hostile processes flood the ready
  queue and starve unlucky legitimate ones outright.

- **Aging fixes the starvation it was designed to fix.** Turning on aging
  for the exact same Priority scheduler drops legit starvation from **3
  processes to 0**, at the cost of a *higher* mean wait (32.33 vs 17.43) --
  aging trades away the flood's free ride (hostile mean wait rises from
  6.27 to 26.82) for a starvation guarantee. Even the convoy hog itself
  eventually gets serviced under aging (whereas under FCFS or no-aging
  priority it's simply first-or-forgotten by construction) -- an honest
  side effect of the fact that real aging ages *every* waiting process,
  not just the "good" ones.

- **Round Robin prevents starvation without needing aging at all.** 0
  legitimate processes starved, Jain's index for legit processes (0.868)
  close to Priority-with-aging's (0.822) -- RR achieves a comparable
  fairness guarantee "for free", structurally, just by bounding every
  process's turn to one quantum. Its cost, as the theory predicts, is
  throughput/responsiveness for the process that's actually running
  (constant preemption) rather than any starvation risk.

## Things to try changing

- **Turn off aging** (`priority_scheduling(workload, aging=False)` in
  `main.py`) and watch the 3 starved legitimate processes reappear --
  confirms aging is doing real, measurable work, not just symbolic.
- **Shrink the Round Robin quantum** (e.g. `quantum=1`) -- fairness stays
  high, but total context-switch count (segments in `timeline`) explodes,
  illustrating the classic quantum-size tradeoff from the theory chapter.
- **Grow the quantum toward the hog's burst** (e.g. `quantum=20`) -- Round
  Robin should start degenerating back toward FCFS-like behaviour (the hog
  gets to run uninterrupted in a single turn), showing why "too long a
  quantum" reopens the same convoy-effect door RR is supposed to close.
- **Make the flood bigger** (`num_flood=40` in `process_workload.py`) --
  a Sybil-style abuse of "fair" schedulers: Round Robin is fair
  *per process*, so a large enough flood of forked hostile processes still
  grabs a disproportionate *total* CPU share relative to one legitimate
  process, even though no single flood process is individually favored.
- **Raise the convoy hog's burst** (e.g. `burst=100`) and watch FCFS's mean
  wait blow past every other policy's, while its Jain's index still looks
  artificially "fine" -- a good illustration of why fairness index and
  absolute wait time must always be read together.
- **Tighten the starvation detector** (`relative_multiplier=1.5` in
  `main.py`'s `analyze()` calls) to see how sensitive the starvation count
  is to the outlier threshold chosen.
