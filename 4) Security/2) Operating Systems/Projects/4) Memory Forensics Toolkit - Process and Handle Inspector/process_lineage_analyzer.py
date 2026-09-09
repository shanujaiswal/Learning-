"""
process_lineage_analyzer.py

Parent/child (PPID) lineage validator.

Real-world equivalent: Volatility's `pstree` plugin (and the manual
"pstree anomaly hunting" a forensic analyst does by eyeballing its
output) -- building the process tree from PID/PPID pairs and looking for
processes whose claimed parent doesn't check out. In a real incident
this shows up as:

  - A process's PPID pointing at a PID that no longer exists in the
    process list at all (the true parent already exited, or the PPID was
    forged outright) -- "process hollowing" / "PPID spoofing" (e.g.
    malware launched via CreateProcess with an explicitly forged
    lpAttributeList parent handle, so `svchost.exe` appears to have been
    spawned by `services.exe` when it actually wasn't).
  - A process claiming itself as its own parent (a PID/PPID cycle),
    which cannot legitimately happen in a real process tree.

This module validates every PID/PPID edge in the snapshot against the
set of PIDs that actually exist in that same snapshot.
"""

from __future__ import annotations

from dataclasses import dataclass

# PID 0 conventionally means "no parent" (the root of the tree, e.g. the
# kernel/System Idle Process) and is never itself a real process here.
ROOT_PPID = 0


@dataclass
class LineageFinding:
    pid: int
    process_name: str
    claimed_ppid: int
    reason: str

    def describe(self) -> str:
        return (
            f"[PROCESS LINEAGE] PID {self.pid} ({self.process_name}): "
            f"claimed parent PID {self.claimed_ppid} -> {self.reason}"
        )


def analyze_lineage(snapshot: dict) -> list[LineageFinding]:
    """Validate every process's PPID against the snapshot's real PID set."""
    processes = snapshot["processes"]
    real_pids = set(processes.keys())
    findings: list[LineageFinding] = []

    for pid, process in processes.items():
        ppid = process.ppid

        if ppid == pid:
            findings.append(LineageFinding(
                pid=pid,
                process_name=process.name,
                claimed_ppid=ppid,
                reason="process claims itself as its own parent (PID/PPID cycle)",
            ))
            continue

        if ppid == ROOT_PPID:
            continue  # legitimate root of the tree

        if ppid not in real_pids:
            findings.append(LineageFinding(
                pid=pid,
                process_name=process.name,
                claimed_ppid=ppid,
                reason="parent PID does not correspond to any process in the "
                       "snapshot (orphaned/spoofed parent)",
            ))

    return findings


if __name__ == "__main__":
    from memory_snapshot_generator import generate_snapshot

    snap = generate_snapshot()
    results = analyze_lineage(snap)
    if not results:
        print("No process-lineage anomalies found.")
    for f in results:
        print(f.describe())
