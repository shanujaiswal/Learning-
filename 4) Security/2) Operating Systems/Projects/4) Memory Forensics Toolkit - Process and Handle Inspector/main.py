"""
main.py

Orchestrates the full memory-forensics pass:

  1. Generate the synthetic snapshot (memory_snapshot_generator).
  2. Run the module-integrity, process-lineage, and memory-region /
     handle-abuse analyzers against it.
  3. Print a consolidated forensic report to the console.
  4. Render the process tree with matplotlib, highlighting every process
     that triggered at least one finding, and save it to
     memory_forensics_result.png.

Everything is offline / simulated -- there is no live-memory access, no
Volatility dependency, and no real process is touched.
"""

from __future__ import annotations

from collections import defaultdict

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.lines import Line2D

from memory_snapshot_generator import generate_snapshot
from module_integrity_checker import check_module_integrity
from process_lineage_analyzer import analyze_lineage
from memory_region_analyzer import analyze_memory_regions, analyze_handle_abuse

OUTPUT_IMAGE = "memory_forensics_result.png"


def build_report(snapshot: dict) -> dict:
    """Run every analyzer and group findings by PID."""
    module_findings = check_module_integrity(snapshot)
    lineage_findings = analyze_lineage(snapshot)
    region_findings = analyze_memory_regions(snapshot)
    handle_findings = analyze_handle_abuse(snapshot)

    findings_by_pid: dict[int, list[str]] = defaultdict(list)
    for f in module_findings:
        findings_by_pid[f.pid].append(("REFLECTIVE_DLL", f.describe()))
    for f in lineage_findings:
        findings_by_pid[f.pid].append(("SPOOFED_PARENT", f.describe()))
    for f in region_findings:
        findings_by_pid[f.pid].append(("RWX_SHELLCODE", f.describe()))
    for f in handle_findings:
        findings_by_pid[f.pid].append(("HANDLE_HOARDING", f.describe()))

    return {
        "module_findings": module_findings,
        "lineage_findings": lineage_findings,
        "region_findings": region_findings,
        "handle_findings": handle_findings,
        "findings_by_pid": findings_by_pid,
    }


def print_report(snapshot: dict, report: dict) -> None:
    total = (
        len(report["module_findings"])
        + len(report["lineage_findings"])
        + len(report["region_findings"])
        + len(report["handle_findings"])
    )

    print("=" * 78)
    print("MEMORY FORENSICS TOOLKIT -- PROCESS AND HANDLE INSPECTOR")
    print("=" * 78)
    print(f"Snapshot seed        : {snapshot['seed']}")
    print(f"Processes in snapshot: {len(snapshot['processes'])}")
    print(f"Total findings        : {total}")
    print()

    sections = [
        ("MODULE INTEGRITY (reflective DLL / injected module)", report["module_findings"]),
        ("PROCESS LINEAGE (spoofed / orphaned parent)", report["lineage_findings"]),
        ("MEMORY REGIONS (RW -> RWX shellcode pattern)", report["region_findings"]),
        ("HANDLE ABUSE (credential-dumping / LSASS-handle-grab)", report["handle_findings"]),
    ]

    for title, findings in sections:
        print("-" * 78)
        print(title)
        print("-" * 78)
        if not findings:
            print("  (clean -- no anomalies of this type found)")
        for f in findings:
            print(f"  {f.describe()}")
        print()

    # Per-process verdict summary.
    print("=" * 78)
    print("PER-PROCESS VERDICT")
    print("=" * 78)
    planted = snapshot["planted_anomalies"]
    for pid in sorted(snapshot["processes"]):
        process = snapshot["processes"][pid]
        tags = sorted({tag for tag, _ in report["findings_by_pid"].get(pid, [])})
        if tags:
            verdict = "SUSPICIOUS -> " + ", ".join(tags)
        else:
            verdict = "clean"
        expected = planted.get(pid)
        match = ""
        if expected:
            match = "  [expected: {}, {}]".format(
                expected, "MATCH" if expected in tags else "MISSED"
            )
        print(f"  PID {pid:>5} {process.name:<14} ppid={process.ppid:<6} -> {verdict}{match}")
    print("=" * 78)


def _layout_tree(snapshot: dict) -> dict:
    """Very small BFS layered layout: real PIDs. Any PPID that isn't a
    real process (spoofed/orphaned) is treated as a synthetic root so the
    node still renders, disconnected, instead of crashing the drawer.
    """
    processes = snapshot["processes"]
    real_pids = set(processes.keys())

    children: dict[int, list[int]] = defaultdict(list)
    roots: list[int] = []

    for pid, process in processes.items():
        if process.ppid == 0 or process.ppid not in real_pids:
            roots.append(pid)
        else:
            children[process.ppid].append(pid)

    positions: dict[int, tuple[float, float]] = {}
    depth_counts: dict[int, int] = defaultdict(int)

    def place(pid: int, depth: int):
        x = depth_counts[depth]
        depth_counts[depth] += 1
        positions[pid] = (x, -depth)  # placeholder x, fixed later per-depth
        for child in sorted(children.get(pid, [])):
            place(child, depth + 1)

    for root in sorted(roots):
        place(root, 0)

    # Re-space x coordinates evenly within each depth so siblings don't overlap.
    by_depth: dict[int, list[int]] = defaultdict(list)
    for pid, (_, negdepth) in positions.items():
        by_depth[-negdepth].append(pid)

    final_positions: dict[int, tuple[float, float]] = {}
    for depth, pids in by_depth.items():
        n = len(pids)
        for i, pid in enumerate(sorted(pids, key=lambda p: p)):
            x = (i - (n - 1) / 2.0)
            final_positions[pid] = (x, -depth)

    return final_positions


def render_process_tree(snapshot: dict, report: dict, output_path: str) -> None:
    processes = snapshot["processes"]
    positions = _layout_tree(snapshot)
    real_pids = set(processes.keys())

    fig, ax = plt.subplots(figsize=(13, 8))

    # Draw edges (parent -> child), skipping spoofed/missing parents.
    for pid, process in processes.items():
        if process.ppid in real_pids and process.ppid != pid:
            x1, y1 = positions[process.ppid]
            x2, y2 = positions[pid]
            ax.plot([x1, x2], [y1, y2], color="#999999", linewidth=1.2, zorder=1)

    tag_colors = {
        "REFLECTIVE_DLL": "#e67e22",
        "SPOOFED_PARENT": "#c0392b",
        "RWX_SHELLCODE": "#8e44ad",
        "HANDLE_HOARDING": "#2980b9",
    }
    clean_color = "#27ae60"

    for pid, process in processes.items():
        x, y = positions[pid]
        tags = sorted({tag for tag, _ in report["findings_by_pid"].get(pid, [])})

        if tags:
            color = tag_colors.get(tags[0], "#e74c3c")
            face = color
            edge = "black"
            size = 1400
        else:
            face = clean_color
            edge = "black"
            size = 1000

        ax.scatter([x], [y], s=size, c=face, edgecolors=edge, linewidths=1.3, zorder=3)

        label = f"{process.name}\nPID {pid}"
        if tags:
            label += "\n[" + ",".join(tags) + "]"
        ax.text(x, y - 0.28, label, ha="center", va="top", fontsize=7.5, zorder=4)

    ax.set_title(
        "Memory Forensics Toolkit -- Process Tree with Flagged Nodes Highlighted",
        fontsize=13,
        fontweight="bold",
    )
    ax.axis("off")
    ax.set_ylim(min(y for _, y in positions.values()) - 1.0, 1.0)

    legend_elements = [
        Line2D([0], [0], marker="o", color="w", label="Clean process",
               markerfacecolor=clean_color, markeredgecolor="black", markersize=12),
        Line2D([0], [0], marker="o", color="w", label="Reflective DLL (injected module)",
               markerfacecolor=tag_colors["REFLECTIVE_DLL"], markeredgecolor="black", markersize=12),
        Line2D([0], [0], marker="o", color="w", label="Spoofed / orphaned parent",
               markerfacecolor=tag_colors["SPOOFED_PARENT"], markeredgecolor="black", markersize=12),
        Line2D([0], [0], marker="o", color="w", label="RW -> RWX shellcode region",
               markerfacecolor=tag_colors["RWX_SHELLCODE"], markeredgecolor="black", markersize=12),
        Line2D([0], [0], marker="o", color="w", label="Abnormal LSASS handle count",
               markerfacecolor=tag_colors["HANDLE_HOARDING"], markeredgecolor="black", markersize=12),
    ]
    ax.legend(handles=legend_elements, loc="upper center", bbox_to_anchor=(0.5, 0.02),
              ncol=3, fontsize=8, frameon=False)

    fig.tight_layout()
    fig.savefig(output_path, dpi=150)
    plt.close(fig)


def main():
    snapshot = generate_snapshot()
    report = build_report(snapshot)
    print_report(snapshot, report)
    render_process_tree(snapshot, report, OUTPUT_IMAGE)
    print(f"\nProcess tree image saved to: {OUTPUT_IMAGE}")


if __name__ == "__main__":
    main()
