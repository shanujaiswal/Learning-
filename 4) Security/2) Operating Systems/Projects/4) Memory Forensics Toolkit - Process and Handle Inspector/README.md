# Memory Forensics Toolkit -- Process and Handle Inspector

A fully offline, self-contained simulation of a memory-forensics triage
workflow: given a synthetic "memory snapshot" (a structured, in-memory
Python stand-in for what a tool like Volatility would reconstruct from a
real memory dump), find the signs of process injection and hidden
malware without ever touching a real process, a real memory dump, or a
third-party forensics framework.

## Real-world scenario

An incident responder is handed a memory image from a potentially
compromised Windows host. They don't have time to eyeball gigabytes of
raw memory by hand, so they run a set of automated checks -- the same
category of checks Volatility plugins perform -- looking for the
tell-tale fingerprints malware leaves in memory even when it never
touches disk:

- **Reflective DLL injection** -- a module is mapped into a process's
  address space and running code, but there is no corresponding file on
  disk. Normal `LoadLibrary()` calls always load from a real file;
  reflective loaders manually map a DLL image straight from a memory
  buffer, so `ldrmodules`-style checks find it in the loader's in-memory
  bookkeeping but not backed by anything on the filesystem.
- **Process hollowing / PPID spoofing** -- a process reports a parent
  PID that doesn't correspond to any process actually present in the
  snapshot. Attackers forge the parent process at creation time (e.g. via
  `UpdateProcThreadAttribute` with a spoofed parent handle) specifically
  to make `svchost.exe` or similar look like it was launched by a
  legitimate service host, when the real lineage is a lie or the true
  parent has already exited.
- **RW -> RWX shellcode staging** -- a memory region was allocated
  read-write (to stage a payload byte-for-byte), then flipped to
  read-write-execute (or execute-only). Legitimate application code has
  essentially no reason to make a private memory region executable at
  runtime; this transition is the fingerprint of classic
  allocate-write-execute shellcode injection.
- **Credential-dumping / LSASS-handle-grab** -- a process holds an
  abnormally large number of open handles to `lsass.exe`, several with
  memory-read rights (`PROCESS_VM_READ`). This is exactly how tools like
  Mimikatz operate: get a handle to LSASS, then walk its memory to
  extract cached credentials -- no disk artifact required.

This project builds a small, deterministic snapshot with one process
exhibiting each of these four patterns among a handful of ordinary,
clean processes, then runs independent analyzer modules against it and
renders the process tree with the flagged processes highlighted.

## Architecture

| Module | Role | Real-world equivalent |
|---|---|---|
| `memory_snapshot_generator.py` | Builds the synthetic snapshot (processes, loaded modules, memory regions, open handles) with a fixed seed and four planted anomalies. | The raw memory dump itself, or Volatility's `pslist`/`dlllist`/`vadinfo`/`handles` output that a real analyst would start from. |
| `module_integrity_checker.py` | Flags loaded modules whose reported path isn't in the known-good "on disk" inventory (or has no path at all). | Volatility's `ldrmodules` plugin (loader-list vs VAD cross-check for unbacked modules). |
| `process_lineage_analyzer.py` | Validates every process's PPID against the set of PIDs that actually exist in the snapshot; flags orphaned/spoofed parents and self-parenting cycles. | Volatility's `pstree` plugin and the anomaly-hunting an analyst does over its output. |
| `memory_region_analyzer.py` | Flags memory regions with a RW -> RWX protection transition, and processes with abnormal (or memory-read-capable) handle counts to a sensitive target process (LSASS). | Volatility's `malfind` plugin (private executable regions = shellcode) and its `handles` plugin filtered to Process-type handles on `lsass.exe`. |
| `main.py` | Runs all three analyzers, prints a consolidated forensic report, and renders/saves the highlighted process-tree PNG. | A SOC analyst's/DFIR playbook that chains multiple Volatility plugins and summarizes the verdict per process. |

## Run it

```bash
cd "3) Security/2) Operating Systems/Projects/4) Memory Forensics Toolkit - Process and Handle Inspector"
python main.py
```

Requires only the Python standard library plus `matplotlib` (already
installed). No admin privileges, no real process access, no network --
everything operates on the synthetic snapshot generated in-process.

Each analyzer can also be run standalone for a focused view, e.g.:

```bash
python module_integrity_checker.py
python process_lineage_analyzer.py
python memory_region_analyzer.py
python memory_snapshot_generator.py   # dumps the raw snapshot as JSON
```

## Verified result (actual output)

Ran on 2026-08-17 with `python main.py`:

```
==============================================================================
MEMORY FORENSICS TOOLKIT -- PROCESS AND HANDLE INSPECTOR
==============================================================================
Snapshot seed        : 1337
Processes in snapshot: 11
Total findings        : 4

------------------------------------------------------------------------------
MODULE INTEGRITY (reflective DLL / injected module)
------------------------------------------------------------------------------
  [MODULE INTEGRITY] PID 700 (svchost.exe): module 'evil_reflective.dll' reported path = '<none - memory only>' -> module has NO backing file on disk (likely reflectively injected)

------------------------------------------------------------------------------
PROCESS LINEAGE (spoofed / orphaned parent)
------------------------------------------------------------------------------
  [PROCESS LINEAGE] PID 800 (svchost.exe): claimed parent PID 9999 -> parent PID does not correspond to any process in the snapshot (orphaned/spoofed parent)

------------------------------------------------------------------------------
MEMORY REGIONS (RW -> RWX shellcode pattern)
------------------------------------------------------------------------------
  [MEMORY REGION] PID 900 (chrome.exe): region at 0x02000000 went RW -> RWX (RW-then-executable = classic shellcode-injection pattern)

------------------------------------------------------------------------------
HANDLE ABUSE (credential-dumping / LSASS-handle-grab)
------------------------------------------------------------------------------
  [HANDLE ABUSE] PID 1000 (taskhostw.exe) holds 18 handle(s) to lsass.exe (PID 500), memory-read rights = True -> possible credential-dumping behavior (LSASS-handle-grab pattern)

==============================================================================
PER-PROCESS VERDICT
==============================================================================
  PID     4 System         ppid=0      -> clean
  PID   100 explorer.exe   ppid=4      -> clean
  PID   200 svchost.exe    ppid=100    -> clean
  PID   300 chrome.exe     ppid=100    -> clean
  PID   400 winlogon.exe   ppid=4      -> clean
  PID   500 lsass.exe      ppid=400    -> clean
  PID   600 notepad.exe    ppid=100    -> clean
  PID   700 svchost.exe    ppid=100    -> SUSPICIOUS -> REFLECTIVE_DLL  [expected: REFLECTIVE_DLL, MATCH]
  PID   800 svchost.exe    ppid=9999   -> SUSPICIOUS -> SPOOFED_PARENT  [expected: SPOOFED_PARENT, MATCH]
  PID   900 chrome.exe     ppid=100    -> SUSPICIOUS -> RWX_SHELLCODE  [expected: RWX_SHELLCODE, MATCH]
  PID  1000 taskhostw.exe  ppid=100    -> SUSPICIOUS -> HANDLE_HOARDING  [expected: HANDLE_HOARDING, MATCH]
==============================================================================

Process tree image saved to: memory_forensics_result.png
```

All four planted indicators (`REFLECTIVE_DLL`, `SPOOFED_PARENT`,
`RWX_SHELLCODE`, `HANDLE_HOARDING`) were detected and matched exactly
against the analyzers, with zero false positives on the seven clean
baseline processes. `memory_forensics_result.png` renders the process
tree with the four flagged nodes colour-coded by anomaly type and PID
800's node floating disconnected from the tree (since its claimed parent
PID 9999 doesn't exist), which is itself a visual tell of the spoofed
lineage.

## Things to try changing

- **Raise `HANDLE_COUNT_THRESHOLD`** in `memory_region_analyzer.py` and
  watch PID 1000 stop triggering purely on count -- but it will still
  trigger because of the `PROCESS_VM_READ` access right check, showing
  why real detections should combine multiple signals rather than a
  single threshold.
- **Add a module to `DISK_MODULE_INVENTORY`** in
  `memory_snapshot_generator.py` that matches an injected module's path
  exactly, and see the reflective-DLL finding disappear -- a reminder
  that a known-good inventory is only as strong as its coverage (this is
  why real tools also check in-memory PE headers/entropy, not just
  paths).
- **Change PID 800's `ppid`** from `9999` to a real PID (e.g. `100`) and
  confirm the lineage analyzer goes quiet -- then set it to `800` itself
  (self-parenting) and confirm the cycle-detection branch fires instead.
- **Add a second RW->RWX region** to a currently-clean process (e.g.
  `notepad.exe`) and confirm it gets picked up and correctly re-colored
  in the rendered tree.
- **Add a third "moderately suspicious" process** with exactly
  `HANDLE_COUNT_THRESHOLD + 1` benign (non-memory-read) handles to LSASS,
  to see the count-based branch of `analyze_handle_abuse` fire
  independently of the access-rights branch.
