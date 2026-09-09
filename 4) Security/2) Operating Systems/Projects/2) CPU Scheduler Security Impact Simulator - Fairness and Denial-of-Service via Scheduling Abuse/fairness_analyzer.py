"""
fairness_analyzer.py
=====================
Given a scheduler run's per-process completion/wait log (as produced by
schedulers.py), compute the fairness/starvation metrics that matter for a
security assessment of a scheduling policy.

Jain's Fairness Index
----------------------
    J(x) = (sum(x_i))^2 / (n * sum(x_i^2))

J ranges from 1/n (worst -- one process takes everything) to 1.0 (perfectly
fair -- every process is treated identically). It is applied here to each
process's WAITING TIME: if every process waited the same number of time
units, J = 1 (fair). If a few processes are starved while others sail
through, the wait-time distribution becomes lopsided and J drops sharply.
This is the standard networking/OS definition of fairness, just applied to
"time spent waiting for CPU" instead of "bandwidth received".

Starvation flag
----------------
Total CPU demand in the workload is fixed -- no scheduling policy can make
the *total* amount of waiting across all processes disappear, it can only
decide who bears it. So an absolute wait-time cutoff would just measure
"how heavy is the workload", not "how unfairly is the wait distributed".

Instead, a process is flagged STARVED if its wait is a statistical outlier
*within that run*: it waited far longer than its peers did on average in
that same scheduling run --

    starved  <=>  wait > relative_multiplier * mean_wait_of_this_run
                  AND wait > absolute_floor

The absolute floor just avoids flagging trivial waits (e.g. "waited 3 units
when the mean was 1") as starvation. This definition correctly separates
"the workload is heavy" (raises everyone's wait together, no flags) from
"this scheduling policy is unfair" (a few processes carry the queueing pain
that should have been spread across everyone, so they blow past the run's
own average -- flagged).
"""

import statistics


def jains_fairness_index(values):
    """Jain's fairness index over a list of non-negative values (e.g. wait times)."""
    if not values:
        return 1.0
    # 0 wait is a perfectly valid (great) value -- do not treat as missing data.
    n = len(values)
    s = sum(values)
    ss = sum(v * v for v in values)
    if ss == 0:
        return 1.0  # everyone waited 0 -- perfectly fair
    return (s ** 2) / (n * ss)


def analyze(stats, relative_multiplier=2.0, absolute_floor=10):
    """
    stats: list of dicts (see schedulers._build_result) with keys
           pid, arrival, burst, priority, hostile, completion, turnaround, wait

    Mutates each stat dict in place to add a "starved" bool, and returns a
    summary dict of aggregate fairness metrics. A process is STARVED if its
    wait exceeds `relative_multiplier`x this run's own mean wait time (and
    exceeds `absolute_floor` time units, to avoid flagging noise when every
    wait in the run is tiny).
    """
    waits_for_mean = [s["wait"] for s in stats]
    run_mean_wait = statistics.mean(waits_for_mean) if waits_for_mean else 0.0
    threshold = max(relative_multiplier * run_mean_wait, absolute_floor)

    for s in stats:
        s["starved"] = s["wait"] > threshold
        s["starvation_threshold"] = threshold

    waits = [s["wait"] for s in stats]
    legit_waits = [s["wait"] for s in stats if not s["hostile"]]
    hostile_waits = [s["wait"] for s in stats if s["hostile"]]

    result = {
        "n": len(stats),
        "mean_wait": statistics.mean(waits) if waits else 0.0,
        "max_wait": max(waits) if waits else 0,
        "stdev_wait": statistics.pstdev(waits) if len(waits) > 1 else 0.0,
        "jains_index_all": jains_fairness_index(waits),
        "jains_index_legit": jains_fairness_index(legit_waits) if legit_waits else 1.0,
        "mean_wait_legit": statistics.mean(legit_waits) if legit_waits else 0.0,
        "mean_wait_hostile": statistics.mean(hostile_waits) if hostile_waits else 0.0,
        "num_starved": sum(1 for s in stats if s["starved"]),
        "num_starved_legit": sum(1 for s in stats if s["starved"] and not s["hostile"]),
        "starved_pids": [s["pid"] for s in stats if s["starved"]],
    }
    return result


def print_report(scheduler_result, analysis):
    name = scheduler_result["name"]
    stats = scheduler_result["stats"]
    print(f"\n=== {name} ===")
    print(f"{'PID':<6}{'Arr':<6}{'Burst':<7}{'Prio':<6}{'Type':<9}"
          f"{'Compl':<8}{'Wait':<7}{'Starved?'}")
    for s in stats:
        tag = "HOSTILE" if s["hostile"] else "legit"
        flag = "*** STARVED ***" if s["starved"] else ""
        print(f"{s['pid']:<6}{s['arrival']:<6}{s['burst']:<7}{s['priority']:<6}{tag:<9}"
              f"{s['completion']:<8}{s['wait']:<7}{flag}")

    print(f"\n  Mean wait (all):       {analysis['mean_wait']:.2f}")
    print(f"  Mean wait (legit):     {analysis['mean_wait_legit']:.2f}")
    print(f"  Mean wait (hostile):   {analysis['mean_wait_hostile']:.2f}")
    print(f"  Max wait:              {analysis['max_wait']}")
    print(f"  Stdev of wait:         {analysis['stdev_wait']:.2f}")
    print(f"  Jain's fairness index (all procs):   {analysis['jains_index_all']:.3f}")
    print(f"  Jain's fairness index (legit only):  {analysis['jains_index_legit']:.3f}")
    print(f"  Starved processes: {analysis['num_starved']} total, "
          f"{analysis['num_starved_legit']} of them legitimate "
          f"{analysis['starved_pids']}")
