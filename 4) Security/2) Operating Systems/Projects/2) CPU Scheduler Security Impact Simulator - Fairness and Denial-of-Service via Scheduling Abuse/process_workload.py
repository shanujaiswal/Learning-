"""
process_workload.py
====================
Generates a fixed, reproducible, mixed CPU workload used to demonstrate a
real local Denial-of-Service vector: scheduling abuse.

The workload mixes:

  1. LEGITIMATE jobs   -- ordinary short/medium tasks, priority = 5 (mid).

  2. CONVOY HOG        -- one hostile process that arrives FIRST (t=0) with a
                          very long burst. FCFS ignores priority entirely and
                          just runs whoever arrived first to completion, so
                          this single process alone starves every legitimate
                          job behind it (the classic "convoy effect" turned
                          into a deliberate local DoS).

  3. PRIORITY FLOOD    -- many small, cheap hostile processes, continuously
                          submitted with the numerically BEST priority.
                          Under priority scheduling without aging, as long as
                          *any* flood process is ready, it preempts/blocks
                          legitimate mid-priority work -- simulating a script
                          that keeps forking cheap, high-priority helper
                          tasks to hog the CPU (a "cheap process flood").

Priority convention (matches Unix `nice` and most textbook priority
schedulers): LOWER number = HIGHER scheduling priority (runs first).

Everything is derived from a fixed random seed, so every scheduler sees the
exact same workload -- any difference in outcome is due to the scheduling
POLICY, not luck.
"""

from dataclasses import dataclass, field
import numpy as np

SEED = 42

LEGIT_PRIORITY = 5
HOG_PRIORITY = 8      # worst priority -- shouldn't matter under a fair priority scheduler
FLOOD_PRIORITY = 1    # best priority -- always beats legit(5) and hog(8)


@dataclass
class Process:
    pid: str
    arrival: int
    burst: int
    priority: int
    hostile: bool = False
    remaining: int = field(default=None)

    def __post_init__(self):
        if self.remaining is None:
            self.remaining = self.burst

    def clone(self):
        """Fresh copy with `remaining` reset -- each scheduler must run on an untouched copy."""
        return Process(self.pid, self.arrival, self.burst, self.priority, self.hostile, self.burst)


def generate_workload(seed: int = SEED,
                       num_legit: int = 10,
                       num_flood: int = 10,
                       sim_window: int = 25):
    """Return a list[Process]: legitimate jobs + 1 convoy hog + a flood of cheap hostile jobs."""
    rng = np.random.default_rng(seed)
    processes = []

    # 1. Legitimate short/medium jobs, spread across the whole simulated window.
    for i in range(1, num_legit + 1):
        arrival = int(rng.integers(0, sim_window - 5))
        burst = int(rng.integers(3, 7))
        processes.append(Process(f"L{i}", arrival, burst, LEGIT_PRIORITY, hostile=False))

    # 2. The convoy hog: arrives first, a long burst, deliberately BAD priority
    #    (so a fair priority scheduler should ignore it) -- but FCFS cares
    #    only about arrival order, so this alone is enough to starve FCFS.
    processes.append(Process("HOG", arrival=0, burst=20, priority=HOG_PRIORITY, hostile=True))

    # 3. The priority flood: many cheap (burst 1-2), best-priority hostile
    #    processes arriving continuously through the whole window.
    for i in range(1, num_flood + 1):
        arrival = int(rng.integers(0, sim_window))
        burst = int(rng.integers(1, 3))
        processes.append(Process(f"F{i}", arrival, burst, FLOOD_PRIORITY, hostile=True))

    processes.sort(key=lambda p: (p.arrival, p.pid))
    return processes


def clone_workload(processes):
    """Return fresh, independent copies so multiple schedulers can run on the same workload."""
    return [p.clone() for p in processes]


def summarize(processes):
    legit = [p for p in processes if not p.hostile]
    hostile = [p for p in processes if p.hostile]
    print(f"Workload: {len(processes)} processes total "
          f"({len(legit)} legitimate, {len(hostile)} hostile)")
    print(f"  Legitimate total CPU demand: {sum(p.burst for p in legit)} time units")
    print(f"  Hostile total CPU demand:    {sum(p.burst for p in hostile)} time units "
          f"(1 convoy hog + {len(hostile) - 1} flood processes)")


if __name__ == "__main__":
    wl = generate_workload()
    summarize(wl)
    print()
    print(f"{'PID':<6}{'Arrival':<9}{'Burst':<7}{'Priority':<9}{'Type'}")
    for p in wl:
        tag = "HOSTILE" if p.hostile else "legit"
        print(f"{p.pid:<6}{p.arrival:<9}{p.burst:<7}{p.priority:<9}{tag}")
