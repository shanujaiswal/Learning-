"""
schedulers.py
=============
Three classic CPU scheduling algorithms, all operating on the SAME workload
(see process_workload.py) so their security/fairness behaviour can be
compared directly:

  1. fcfs()               -- First-Come, First-Served (non-preemptive)
  2. round_robin()        -- Round Robin with a configurable quantum (preemptive)
  3. priority_scheduling() -- Priority scheduling (preemptive, unit time-slices),
                              with an optional `aging` flag that gradually
                              improves a waiting process's effective priority
                              the longer it sits in the ready queue.

All three return the same shape of result so fairness_analyzer.py and main.py
don't need to know which algorithm produced it:

    {
        "name": str,
        "timeline": [(pid, start, end), ...],   # Gantt-chart-style execution log
        "stats": [
            {"pid", "arrival", "burst", "priority", "hostile",
             "completion", "turnaround", "wait"},
            ...
        ],
    }

Waiting time is computed the standard way (holds for any of the three
policies as long as processes never block on I/O, which this simulator does
not model):

    turnaround_time = completion_time - arrival_time
    waiting_time    = turnaround_time - burst_time
"""

from process_workload import clone_workload


def _build_result(name, timeline, completion, original_processes):
    stats = []
    for p in original_processes:
        c = completion[p.pid]
        turnaround = c - p.arrival
        wait = turnaround - p.burst
        stats.append({
            "pid": p.pid,
            "arrival": p.arrival,
            "burst": p.burst,
            "priority": p.priority,
            "hostile": p.hostile,
            "completion": c,
            "turnaround": turnaround,
            "wait": wait,
        })
    stats.sort(key=lambda s: s["completion"])
    return {"name": name, "timeline": timeline, "stats": stats}


# --------------------------------------------------------------------------
# 1. FCFS -- First-Come, First-Served (non-preemptive)
#
# SECURITY NOTE: FCFS has zero concept of priority or fairness -- it only
# cares about arrival order. A single hostile process that arrives early
# with a long burst (our "convoy hog") runs to completion before anything
# else gets a single tick of CPU, no matter how important the queued work
# is. This is the "convoy effect" weaponised into a local DoS.
# --------------------------------------------------------------------------
def fcfs(processes):
    procs = clone_workload(processes)
    procs.sort(key=lambda p: (p.arrival, p.pid))
    time_now = 0
    timeline = []
    completion = {}
    for p in procs:
        start = max(time_now, p.arrival)
        end = start + p.burst
        timeline.append((p.pid, start, end))
        completion[p.pid] = end
        time_now = end
    return _build_result("FCFS (First-Come, First-Served)", timeline, completion, procs)


# --------------------------------------------------------------------------
# 2. Round Robin -- preemptive, fixed quantum
#
# SECURITY NOTE: because every process gets an equal, bounded turn no matter
# its priority or how long it's been running, no single process (or small
# group) can monopolise the CPU indefinitely -- this is what makes RR
# resistant to the convoy-hog and priority-flood attacks above. Its
# remaining weakness: fairness is PER-PROCESS, not per-attacker -- a hostile
# actor who forks many cheap processes still gets one fair turn *per forked
# process*, so a large enough flood still grabs a disproportionate share of
# total CPU relative to a single legitimate process (a Sybil-style abuse of
# "fairness" itself).
# --------------------------------------------------------------------------
def round_robin(processes, quantum=4):
    procs = clone_workload(processes)
    arrival_order = sorted(procs, key=lambda p: (p.arrival, p.pid))
    time_now = 0
    queue = []
    completion = {}
    timeline = []
    not_yet_arrived = list(arrival_order)

    def admit_arrivals(up_to_time):
        while not_yet_arrived and not_yet_arrived[0].arrival <= up_to_time:
            queue.append(not_yet_arrived.pop(0))

    admit_arrivals(time_now)
    if not queue and not_yet_arrived:
        time_now = not_yet_arrived[0].arrival
        admit_arrivals(time_now)

    while queue:
        p = queue.pop(0)
        run_for = min(quantum, p.remaining)
        start = time_now
        end = start + run_for
        timeline.append((p.pid, start, end))
        p.remaining -= run_for
        time_now = end
        admit_arrivals(time_now)
        if p.remaining > 0:
            queue.append(p)
        else:
            completion[p.pid] = end
        if not queue and not_yet_arrived:
            time_now = not_yet_arrived[0].arrival
            admit_arrivals(time_now)

    return _build_result(f"Round Robin (quantum={quantum})", timeline, completion, procs)


# --------------------------------------------------------------------------
# 3. Priority Scheduling -- preemptive, 1-time-unit slices
#
# SECURITY NOTE: without aging, whichever ready process has the numerically
# lowest priority value always wins -- a flood of cheap, best-priority
# hostile processes that keeps the ready queue non-empty is therefore enough
# to lock out legitimate mid-priority work *indefinitely* (classic priority
# starvation / a form of priority inversion). Aging fixes this by gradually
# improving a process's EFFECTIVE priority the longer it waits, guaranteeing
# it eventually outranks the flood and gets scheduled.
# --------------------------------------------------------------------------
def priority_scheduling(processes, aging=False, aging_rate=1.0):
    procs = clone_workload(processes)
    by_pid = {p.pid: p for p in procs}
    not_yet_arrived = sorted(procs, key=lambda p: (p.arrival, p.pid))
    ready = []
    waited = {p.pid: 0 for p in procs}
    completion = {}
    timeline = []
    time_now = 0

    def admit_arrivals(up_to_time):
        while not_yet_arrived and not_yet_arrived[0].arrival <= up_to_time:
            ready.append(not_yet_arrived.pop(0))

    total_work = sum(p.burst for p in procs)
    admit_arrivals(time_now)
    safety_limit = total_work + max(p.arrival for p in procs) + 10

    running_pid = None
    seg_start = None

    while len(completion) < len(procs) and time_now <= safety_limit:
        if not ready:
            # CPU idle -- fast-forward to next arrival.
            if running_pid is not None:
                timeline.append((running_pid, seg_start, time_now))
                running_pid = None
            time_now = not_yet_arrived[0].arrival
            admit_arrivals(time_now)
            continue

        def eff_priority(p):
            base = p.priority - (aging_rate * waited[p.pid] if aging else 0)
            return (base, p.arrival, p.pid)

        chosen = min(ready, key=eff_priority)

        if chosen.pid != running_pid:
            if running_pid is not None:
                timeline.append((running_pid, seg_start, time_now))
            running_pid = chosen.pid
            seg_start = time_now

        chosen.remaining -= 1
        for p in ready:
            if p.pid != chosen.pid:
                waited[p.pid] += 1

        time_now += 1
        admit_arrivals(time_now)

        if chosen.remaining == 0:
            ready.remove(chosen)
            completion[chosen.pid] = time_now
            timeline.append((running_pid, seg_start, time_now))
            running_pid = None

    label = "Priority (with aging)" if aging else "Priority (no aging)"
    return _build_result(label, timeline, completion, list(by_pid.values()))
