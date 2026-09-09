"""
memory_region_analyzer.py

Two related detectors that both look at "unusual use of memory/handles"
rather than static file content:

1. RW -> RWX region flagging.
   Real-world equivalent: Volatility's `malfind` plugin, which walks a
   process's VAD (Virtual Address Descriptor) tree looking for private,
   executable memory regions that don't correspond to a mapped image
   file -- the textbook signature of shellcode injection (allocate RW,
   write the payload, then VirtualProtect it to RWX or just RX and jump
   into it). We simulate the "allocated RW, now RWX" transition directly
   since our snapshot tracks both an initial and a current protection
   per region.

2. Abnormal handle counts to a sensitive process.
   Real-world equivalent: Volatility's `handles` plugin filtered down to
   Process-type handles pointed at lsass.exe, which is exactly how an
   analyst spots a credential-dumping tool (e.g. Mimikatz-style LSASS
   scraping) before it ever touches disk -- the tool just needs a
   memory-read-capable handle to lsass.exe, held open long enough to
   walk its memory for cached credentials.
"""

from __future__ import annotations

from dataclasses import dataclass

# A benign process might legitimately hold a single low-privilege handle
# to lsass.exe (e.g. a service manager doing a status check). More than
# this, or any handle carrying memory-read rights, is treated as
# suspicious.
HANDLE_COUNT_THRESHOLD = 3
MEMORY_READ_MARKERS = ("PROCESS_VM_READ", "PROCESS_ALL_ACCESS")


@dataclass
class RegionFinding:
    pid: int
    process_name: str
    base_address: str
    initial_protection: str
    current_protection: str

    def describe(self) -> str:
        return (
            f"[MEMORY REGION] PID {self.pid} ({self.process_name}): "
            f"region at {self.base_address} went {self.initial_protection} -> "
            f"{self.current_protection} (RW-then-executable = classic "
            f"shellcode-injection pattern)"
        )


@dataclass
class HandleFinding:
    pid: int
    process_name: str
    target_name: str
    target_pid: int
    handle_count: int
    has_memory_read_rights: bool

    def describe(self) -> str:
        return (
            f"[HANDLE ABUSE] PID {self.pid} ({self.process_name}) holds "
            f"{self.handle_count} handle(s) to {self.target_name} (PID "
            f"{self.target_pid}), memory-read rights = "
            f"{self.has_memory_read_rights} -> possible credential-dumping "
            f"behavior (LSASS-handle-grab pattern)"
        )


def analyze_memory_regions(snapshot: dict) -> list[RegionFinding]:
    """Flag memory regions that were allocated RW and are now RWX."""
    findings: list[RegionFinding] = []

    for pid, process in snapshot["processes"].items():
        for region in process.regions:
            if region.is_rw_to_rwx():
                findings.append(RegionFinding(
                    pid=pid,
                    process_name=process.name,
                    base_address=region.base_address,
                    initial_protection=region.initial_protection,
                    current_protection=region.current_protection,
                ))

    return findings


def analyze_handle_abuse(snapshot: dict) -> list[HandleFinding]:
    """Flag processes with an abnormal number of handles to the sensitive
    target process (e.g. lsass.exe), or any handle to it that carries
    memory-read rights, regardless of count.
    """
    sensitive_name = snapshot["sensitive_process"]
    findings: list[HandleFinding] = []

    for pid, process in snapshot["processes"].items():
        matching = [h for h in process.handles if h.target_name == sensitive_name]
        if not matching:
            continue

        count = len(matching)
        has_read_rights = any(
            marker in h.access_rights for h in matching for marker in MEMORY_READ_MARKERS
        )

        if count > HANDLE_COUNT_THRESHOLD or has_read_rights:
            findings.append(HandleFinding(
                pid=pid,
                process_name=process.name,
                target_name=sensitive_name,
                target_pid=matching[0].target_pid,
                handle_count=count,
                has_memory_read_rights=has_read_rights,
            ))

    return findings


if __name__ == "__main__":
    from memory_snapshot_generator import generate_snapshot

    snap = generate_snapshot()

    print("-- RW -> RWX region findings --")
    region_results = analyze_memory_regions(snap)
    if not region_results:
        print("None found.")
    for f in region_results:
        print(f.describe())

    print("\n-- Handle abuse findings --")
    handle_results = analyze_handle_abuse(snap)
    if not handle_results:
        print("None found.")
    for f in handle_results:
        print(f.describe())
