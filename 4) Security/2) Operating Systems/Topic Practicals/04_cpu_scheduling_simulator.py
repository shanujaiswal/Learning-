"""
04 - CPU Scheduling Simulator
================================
Theory chapter: "04 CPU Scheduling Algorithms.md"

Simulates three classic CPU scheduling algorithms over the SAME small set of
fake processes (arrival time + burst/CPU time), printing:
  - a text Gantt chart (which process ran during which time slice), and
  - the average waiting time,
so the algorithms can be compared directly.

Algorithms implemented:
  1. FCFS (First-Come, First-Served)          -- non-preemptive
  2. SJF  (Shortest Job First, non-preemptive) -- picks shortest burst among
                                                    processes that have arrived
  3. Round Robin (preemptive, time quantum = 2)

Waiting time definition used here (standard):
    waiting_time = turnaround_time - burst_time
    turnaround_time = completion_time - arrival_time
"""

from dataclasses import dataclass, field
from copy import deepcopy


@dataclass
class Proc:
    name: str
    arrival: int
    burst: int
    remaining: int = field(default=None)

    def __post_init__(self):
        if self.remaining is None:
            self.remaining = self.burst


# The same fake workload, reused (via deepcopy) for every algorithm.
BASE_PROCESSES = [
    Proc("P1", arrival=0, burst=5),
    Proc("P2", arrival=1, burst=3),
    Proc("P3", arrival=2, burst=8),
    Proc("P4", arrival=3, burst=6),
]


def print_gantt(timeline):
    """timeline: list of (proc_name, start, end) tuples in time order."""
    bar = "|"
    ticks = "0"
    for name, start, end in timeline:
        width = max(end - start, 1)
        bar += f" {name} " + ("-" * (width * 2 - len(name) - 2) if width * 2 > len(name) + 2 else "") + "|"
        ticks += f"{'':>{ (width*2+2) - len(str(end)) }}{end}"
    print(bar)
    print(ticks)


def report(name, timeline, completion, processes):
    print(f"\n=== {name} ===")
    print_gantt(timeline)
    total_wait = 0
    print(f"{'Proc':<6}{'Arrival':<9}{'Burst':<7}{'Completion':<12}{'Turnaround':<12}{'Waiting':<8}")
    for p in processes:
        turnaround = completion[p.name] - p.arrival
        waiting = turnaround - p.burst
        total_wait += waiting
        print(f"{p.name:<6}{p.arrival:<9}{p.burst:<7}{completion[p.name]:<12}{turnaround:<12}{waiting:<8}")
    avg_wait = total_wait / len(processes)
    print(f"Average waiting time: {avg_wait:.2f}")
    return avg_wait


def fcfs(processes):
    procs = sorted(deepcopy(processes), key=lambda p: p.arrival)
    time_now = 0
    timeline = []
    completion = {}
    for p in procs:
        start = max(time_now, p.arrival)
        end = start + p.burst
        timeline.append((p.name, start, end))
        completion[p.name] = end
        time_now = end
    return report("FCFS (First-Come, First-Served)", timeline, completion, processes)


def sjf_non_preemptive(processes):
    procs = deepcopy(processes)
    remaining = {p.name: p for p in procs}
    time_now = 0
    timeline = []
    completion = {}
    while remaining:
        available = [p for p in remaining.values() if p.arrival <= time_now]
        if not available:
            # CPU idle until next arrival
            time_now = min(p.arrival for p in remaining.values())
            continue
        chosen = min(available, key=lambda p: (p.burst, p.arrival))
        start = time_now
        end = start + chosen.burst
        timeline.append((chosen.name, start, end))
        completion[chosen.name] = end
        time_now = end
        del remaining[chosen.name]
    return report("SJF (Shortest Job First, non-preemptive)", timeline, completion, processes)


def round_robin(processes, quantum=2):
    procs = deepcopy(processes)
    procs_by_name = {p.name: p for p in procs}
    arrival_order = sorted(procs, key=lambda p: p.arrival)
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
        timeline.append((p.name, start, end))
        p.remaining -= run_for
        time_now = end
        admit_arrivals(time_now)
        if p.remaining > 0:
            queue.append(p)
        else:
            completion[p.name] = end
        if not queue and not_yet_arrived:
            time_now = not_yet_arrived[0].arrival
            admit_arrivals(time_now)

    return report(f"Round Robin (quantum={quantum})", timeline, completion, procs_by_name.values())


if __name__ == "__main__":
    print("Workload (same for every algorithm):")
    for p in BASE_PROCESSES:
        print(f"  {p.name}: arrival={p.arrival}, burst={p.burst}")

    avg_fcfs = fcfs(BASE_PROCESSES)
    avg_sjf = sjf_non_preemptive(BASE_PROCESSES)
    avg_rr = round_robin(BASE_PROCESSES, quantum=2)

    print("\n=== Comparison of average waiting time ===")
    print(f"FCFS:          {avg_fcfs:.2f}")
    print(f"SJF:           {avg_sjf:.2f}")
    print(f"Round Robin:   {avg_rr:.2f}")
