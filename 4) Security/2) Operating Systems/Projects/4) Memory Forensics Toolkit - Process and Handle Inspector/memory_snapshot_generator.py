"""
memory_snapshot_generator.py

Generates a synthetic "memory forensics snapshot" -- a structured, offline
stand-in for what a tool like Volatility would reconstruct from a real
memory dump (pslist, dlllist, handles, malfind).

No real memory is read. Everything here is plain Python data (dicts/
dataclasses) built with a fixed random seed so the output is 100%
reproducible. Four deliberate anomalies are planted so the analyzer
scripts have something real to catch:

  1. REFLECTIVE_DLL   - a process with a loaded module that has no
                         corresponding file on disk (classic reflective
                         DLL injection / process hollowing indicator).
  2. SPOOFED_PARENT   - a process whose reported parent PID does not
                         belong to any process actually present in the
                         snapshot (orphaned/spoofed parent, a process
                         hollowing / PPID-spoofing indicator).
  3. RWX_SHELLCODE    - a memory region that was allocated RW and later
                         changed to RWX (the textbook shellcode-injection
                         pattern: write the payload, then flip it
                         executable).
  4. HANDLE_HOARDING  - a process holding an abnormally large number of
                         open handles to a single sensitive process
                         (LSASS), mirroring how credential-dumping tools
                         such as Mimikatz grab PROCESS_VM_READ handles to
                         lsass.exe.

Run this file directly to pretty-print the raw snapshot.
"""

from __future__ import annotations

import random
from dataclasses import dataclass, field, asdict
from typing import Optional

SEED = 1337

# The "ground truth" list of files that legitimately exist on disk. This
# plays the role of a known-good software inventory / baseline hash list
# that a real investigator would build from a clean reference image.
DISK_MODULE_INVENTORY = {
    r"C:\Windows\System32\ntdll.dll",
    r"C:\Windows\System32\kernel32.dll",
    r"C:\Windows\System32\KernelBase.dll",
    r"C:\Windows\System32\user32.dll",
    r"C:\Windows\System32\gdi32.dll",
    r"C:\Windows\System32\advapi32.dll",
    r"C:\Windows\System32\msvcrt.dll",
    r"C:\Windows\System32\sechost.dll",
    r"C:\Windows\System32\rpcrt4.dll",
    r"C:\Windows\System32\combase.dll",
    r"C:\Windows\System32\shell32.dll",
    r"C:\Windows\System32\ws2_32.dll",
    r"C:\Windows\System32\crypt32.dll",
    r"C:\Windows\explorer.exe",
    r"C:\Windows\System32\svchost.exe",
    r"C:\Windows\System32\winlogon.exe",
    r"C:\Windows\System32\lsass.exe",
    r"C:\Windows\System32\services.exe",
    r"C:\Windows\System32\notepad.exe",
    r"C:\Program Files\Google\Chrome\Application\chrome.exe",
    r"C:\Program Files\Google\Chrome\Application\chrome_child.dll",
}

SENSITIVE_PROCESS_NAME = "lsass.exe"  # the crown jewel real credential-dump tools target


@dataclass
class Module:
    name: str
    path: Optional[str]          # None / a synthetic path == "not backed by a real file"
    base_address: str
    size_kb: int

    def looks_memory_only(self) -> bool:
        """True if this module has no legitimate on-disk backing file."""
        if self.path is None:
            return True
        if self.path not in DISK_MODULE_INVENTORY:
            return True
        return False


@dataclass
class MemoryRegion:
    base_address: str
    size_kb: int
    initial_protection: str      # protection at allocation time
    current_protection: str      # protection right now (at snapshot time)
    region_type: str             # PRIVATE, MAPPED, IMAGE

    def is_rw_to_rwx(self) -> bool:
        return self.initial_protection == "RW" and self.current_protection == "RWX"


@dataclass
class Handle:
    handle_id: int
    handle_type: str             # Process, File, Mutant, Key, Section, Thread ...
    target_name: str             # e.g. "lsass.exe" or a file path
    target_pid: Optional[int]
    access_rights: str


@dataclass
class Process:
    pid: int
    ppid: int
    name: str
    exe_path: str
    modules: list = field(default_factory=list)
    regions: list = field(default_factory=list)
    handles: list = field(default_factory=list)


def _baseline_modules(exe_name: str) -> list:
    """A believable, entirely-on-disk module list for a normal process."""
    common = [
        Module("ntdll.dll", r"C:\Windows\System32\ntdll.dll", "0x7ffe0000", 1800),
        Module("kernel32.dll", r"C:\Windows\System32\kernel32.dll", "0x7ffd0000", 700),
        Module("KernelBase.dll", r"C:\Windows\System32\KernelBase.dll", "0x7ffc0000", 2600),
        Module("msvcrt.dll", r"C:\Windows\System32\msvcrt.dll", "0x7ffb0000", 620),
    ]
    return common


def _baseline_regions() -> list:
    return [
        MemoryRegion("0x00400000", 64, "RX", "RX", "IMAGE"),     # .text of the exe itself
        MemoryRegion("0x00c00000", 256, "RW", "RW", "PRIVATE"),  # heap
        MemoryRegion("0x00e00000", 128, "RW", "RW", "PRIVATE"),  # stack
    ]


def _baseline_handles(rng: random.Random, own_pid: int) -> list:
    handles = []
    hid = 1
    for _ in range(rng.randint(2, 4)):
        handles.append(Handle(hid, "File", r"C:\Windows\System32\config\SOFTWARE", None, "READ"))
        hid += 1
    handles.append(Handle(hid, "Key", r"HKLM\Software\Microsoft\Windows", None, "READ"))
    return handles


def generate_snapshot() -> dict:
    """Build the full synthetic snapshot. Deterministic (fixed seed)."""
    rng = random.Random(SEED)

    processes: dict[int, Process] = {}

    def add(pid, ppid, name, exe_path):
        p = Process(pid, ppid, name, exe_path,
                     modules=list(_baseline_modules(name)),
                     regions=list(_baseline_regions()),
                     handles=_baseline_handles(rng, pid))
        processes[pid] = p
        return p

    # --- Clean baseline process tree -----------------------------------
    add(4, 0, "System", r"C:\Windows\System32\ntoskrnl.exe")
    add(100, 4, "explorer.exe", r"C:\Windows\explorer.exe")
    add(200, 100, "svchost.exe", r"C:\Windows\System32\svchost.exe")
    add(300, 100, "chrome.exe", r"C:\Program Files\Google\Chrome\Application\chrome.exe")
    add(400, 4, "winlogon.exe", r"C:\Windows\System32\winlogon.exe")
    add(500, 400, SENSITIVE_PROCESS_NAME, r"C:\Windows\System32\lsass.exe")  # sensitive target
    add(600, 100, "notepad.exe", r"C:\Windows\System32\notepad.exe")

    # --- Anomaly 1: REFLECTIVE_DLL ---------------------------------------
    # A normal-looking svchost child with an extra module that has no file
    # on disk -- e.g. injected via reflective DLL loading (no LoadLibrary
    # call ever touched the filesystem, so there's nothing to find there).
    p700 = add(700, 100, "svchost.exe", r"C:\Windows\System32\svchost.exe")
    p700.modules.append(
        Module("evil_reflective.dll", None, "0x10000000", 96)
    )

    # --- Anomaly 2: SPOOFED_PARENT ---------------------------------------
    # PPID claims 9999, but no process 9999 exists anywhere in this
    # snapshot -- classic PPID spoofing / process hollowing indicator
    # (e.g. a payload claiming svchost.exe was launched by services.exe
    # when the real launcher was something else that has since vanished).
    add(800, 9999, "svchost.exe", r"C:\Windows\System32\svchost.exe")

    # --- Anomaly 3: RWX_SHELLCODE -----------------------------------------
    # A region that was allocated read-write (to stage a payload) and was
    # then flipped to read-write-execute -- there is no legitimate reason
    # ordinary application code needs to do this at runtime.
    p900 = add(900, 100, "chrome.exe", r"C:\Program Files\Google\Chrome\Application\chrome.exe")
    p900.regions.append(
        MemoryRegion("0x02000000", 32, "RW", "RWX", "PRIVATE")
    )

    # --- Anomaly 4: HANDLE_HOARDING ----------------------------------------
    # A process opening a large number of handles to lsass.exe with
    # memory-read-capable access rights -- the signature move of
    # credential-dumping tools (Mimikatz-style LSASS scraping).
    p1000 = add(1000, 100, "taskhostw.exe", r"C:\Windows\System32\taskhostw.exe")
    next_hid = len(p1000.handles) + 1
    for i in range(18):
        p1000.handles.append(
            Handle(next_hid + i, "Process", SENSITIVE_PROCESS_NAME, 500,
                   "PROCESS_VM_READ|PROCESS_QUERY_INFORMATION")
        )

    # A normal process is allowed ONE benign handle to lsass (e.g. a
    # session manager querying it) so the analyzer has to actually use a
    # threshold, not just "handle count > 0".
    processes[200].handles.append(
        Handle(99, "Process", SENSITIVE_PROCESS_NAME, 500, "PROCESS_QUERY_LIMITED_INFORMATION")
    )

    snapshot = {
        "seed": SEED,
        "disk_module_inventory": sorted(DISK_MODULE_INVENTORY),
        "sensitive_process": SENSITIVE_PROCESS_NAME,
        "processes": processes,
        "planted_anomalies": {
            700: "REFLECTIVE_DLL",
            800: "SPOOFED_PARENT",
            900: "RWX_SHELLCODE",
            1000: "HANDLE_HOARDING",
        },
    }
    return snapshot


def snapshot_to_serializable(snapshot: dict) -> dict:
    """Convert dataclasses to plain dicts (for printing / json.dumps)."""
    out = dict(snapshot)
    out["processes"] = {
        pid: {
            **{k: v for k, v in asdict(p).items() if k not in ("modules", "regions", "handles")},
            "modules": [asdict(m) for m in p.modules],
            "regions": [asdict(r) for r in p.regions],
            "handles": [asdict(h) for h in p.handles],
        }
        for pid, p in snapshot["processes"].items()
    }
    return out


if __name__ == "__main__":
    import json
    snap = generate_snapshot()
    print(json.dumps(snapshot_to_serializable(snap), indent=2))
