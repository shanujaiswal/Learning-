"""
module_integrity_checker.py

Reflective-DLL-injection detector.

Real-world equivalent: Volatility's `ldrmodules` plugin cross-references
three different Windows loader data structures (PEB lists vs VAD-derived
mappings) to find DLLs that are mapped into a process's memory but are
missing from the "normal" loader bookkeeping -- the classic sign of a
reflectively-loaded (never touched LoadLibrary/disk) module.

Here we simulate the equivalent check the simple way, appropriate for a
synthetic snapshot: every module a process has loaded is checked against
a known-good "on disk" software inventory. A module is flagged if:

  - it has no path at all (path is None), or
  - its reported path isn't present in the known-good disk inventory.

Either case means "this code is running in memory with nothing on disk
to back it up" -- exactly what reflective DLL injection produces.
"""

from __future__ import annotations

from dataclasses import dataclass


@dataclass
class ModuleFinding:
    pid: int
    process_name: str
    module_name: str
    reported_path: str
    reason: str

    def describe(self) -> str:
        return (
            f"[MODULE INTEGRITY] PID {self.pid} ({self.process_name}): "
            f"module '{self.module_name}' reported path = {self.reported_path!r} "
            f"-> {self.reason}"
        )


def check_module_integrity(snapshot: dict) -> list[ModuleFinding]:
    """Flag any loaded module that has no matching entry on disk.

    Returns a list of ModuleFinding, one per suspicious module.
    """
    inventory = set(snapshot["disk_module_inventory"])
    findings: list[ModuleFinding] = []

    for pid, process in snapshot["processes"].items():
        for module in process.modules:
            if module.path is None:
                findings.append(ModuleFinding(
                    pid=pid,
                    process_name=process.name,
                    module_name=module.name,
                    reported_path="<none - memory only>",
                    reason="module has NO backing file on disk "
                           "(likely reflectively injected)",
                ))
            elif module.path not in inventory:
                findings.append(ModuleFinding(
                    pid=pid,
                    process_name=process.name,
                    module_name=module.name,
                    reported_path=module.path,
                    reason="module path is not in the known-good disk inventory",
                ))

    return findings


if __name__ == "__main__":
    from memory_snapshot_generator import generate_snapshot

    snap = generate_snapshot()
    results = check_module_integrity(snap)
    if not results:
        print("No module-integrity anomalies found.")
    for f in results:
        print(f.describe())
