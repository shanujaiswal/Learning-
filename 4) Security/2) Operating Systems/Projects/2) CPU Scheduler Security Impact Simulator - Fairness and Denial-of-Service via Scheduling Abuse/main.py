"""
main.py
=======
Runs the SAME hostile-mixed workload (process_workload.py) through four
scheduling-policy configurations:

    1. FCFS                     -- expected to be starved by the convoy hog
    2. Priority, no aging       -- expected to be starved by the priority flood
    3. Priority, WITH aging     -- expected to fix the starvation above
    4. Round Robin (quantum=4)  -- expected to stay fair throughout

...prints a per-policy report (completion order, wait times, starvation
flags, fairness index), and saves a bar chart comparing Jain's fairness
index and max wait time across all four policies to
`scheduler_fairness_result.png`.
"""

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

from process_workload import generate_workload, summarize
from schedulers import fcfs, round_robin, priority_scheduling
from fairness_analyzer import analyze, print_report

STARVATION_RELATIVE_MULTIPLIER = 2.0
STARVATION_ABSOLUTE_FLOOR = 10


def run_all(workload):
    runs = [
        fcfs(workload),
        priority_scheduling(workload, aging=False),
        priority_scheduling(workload, aging=True, aging_rate=1.0),
        round_robin(workload, quantum=4),
    ]

    results = []
    for run in runs:
        summary = analyze(run["stats"],
                           relative_multiplier=STARVATION_RELATIVE_MULTIPLIER,
                           absolute_floor=STARVATION_ABSOLUTE_FLOOR)
        print_report(run, summary)
        results.append((run["name"], summary))
    return results


def plot_comparison(results, out_path="scheduler_fairness_result.png"):
    names = [name for name, _ in results]
    jains = [summary["jains_index_all"] for _, summary in results]
    jains_legit = [summary["jains_index_legit"] for _, summary in results]
    max_waits = [summary["max_wait"] for _, summary in results]

    short_names = [n.replace(" (First-Come, First-Served)", "\n(FCFS)")
                     .replace("Priority (no aging)", "Priority\n(no aging)")
                     .replace("Priority (with aging)", "Priority\n(aging)")
                     .replace("Round Robin (quantum=4)", "Round Robin\n(q=4)")
                   for n in names]

    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(12, 5.5))

    x = range(len(names))
    width = 0.35
    ax1.bar([i - width / 2 for i in x], jains, width, label="Jain's index (all procs)", color="#3b82f6")
    ax1.bar([i + width / 2 for i in x], jains_legit, width, label="Jain's index (legit only)", color="#93c5fd")
    ax1.set_ylim(0, 1.05)
    ax1.set_ylabel("Jain's Fairness Index (1.0 = perfectly fair)")
    ax1.set_title("Fairness by scheduling policy")
    ax1.set_xticks(list(x))
    ax1.set_xticklabels(short_names)
    ax1.axhline(1.0, color="gray", linewidth=0.7, linestyle="--")
    ax1.legend(fontsize=8, loc="lower right")

    num_starved_legit = [summary["num_starved_legit"] for _, summary in results]
    bars = ax2.bar(x, max_waits, color=["#ef4444" if ns > 0 else "#22c55e" for ns in num_starved_legit])
    ax2.set_ylabel("Max wait time across all processes (time units)")
    ax2.set_title("Worst-case wait time by policy\n(red = at least one legit process flagged STARVED)")
    ax2.set_xticks(list(x))
    ax2.set_xticklabels(short_names)
    for b, mw in zip(bars, max_waits):
        ax2.text(b.get_x() + b.get_width() / 2, mw + 1, str(mw), ha="center", fontsize=9)

    fig.suptitle("CPU Scheduler Security Impact: Fairness & Starvation Under a Hostile Workload", fontsize=12)
    fig.tight_layout(rect=[0, 0, 1, 0.95])
    fig.savefig(out_path, dpi=150)
    print(f"\nSaved chart to {out_path}")


def main():
    workload = generate_workload()
    print("=" * 70)
    print("WORKLOAD (identical for every scheduling policy below)")
    print("=" * 70)
    summarize(workload)

    print("\n" + "=" * 70)
    print("RUNNING EACH SCHEDULING POLICY ON THE SAME HOSTILE WORKLOAD")
    print("=" * 70)
    results = run_all(workload)

    print("\n" + "=" * 70)
    print("CROSS-POLICY COMPARISON")
    print("=" * 70)
    print(f"{'Policy':<26}{'Jain (all)':<12}{'Jain (legit)':<14}{'Max wait':<10}{'#Starved legit'}")
    for name, summary in results:
        print(f"{name:<26}{summary['jains_index_all']:<12.3f}{summary['jains_index_legit']:<14.3f}"
              f"{summary['max_wait']:<10}{summary['num_starved_legit']}")

    plot_comparison(results)


if __name__ == "__main__":
    main()
