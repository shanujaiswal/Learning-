# Operating Systems -- Practical

Runnable Python scripts that make the Theory chapters concrete. Each script is
self-contained (standard library only) -- run with `python <filename>`.

| # | File | Demonstrates | Theory chapter |
|---|------|---------------|-----------------|
| 1 | `01_processes_vs_threads.py` | Spawns the same CPU-light task via `multiprocessing.Process` and `threading.Thread`, printing real `os.getpid()` / thread-id values for each, to make the process-vs-thread distinction concrete (separate address space + PID vs. shared address space + one PID). | `01 Processes, Threads and the OS Kernel.md` |
| 2 | `02_race_condition_and_lock.py` | Multiple threads incrementing a shared counter without synchronization -> wrong final total due to interleaved read-modify-write; then the same code fixed with `threading.Lock` -> correct total. | `01 Processes, Threads and the OS Kernel.md` / `05 Deadlocks.md` (concurrency safety) |
| 3 | `03_deadlock_demo_and_fix.py` | Two threads acquiring two locks in opposite order -> real deadlock, detected via `lock.acquire(timeout=...)` instead of hanging forever; then the fixed version using a consistent global lock-acquisition order. | `05 Deadlocks.md` |
| 4 | `04_cpu_scheduling_simulator.py` | FCFS, Shortest Job First (non-preemptive), and Round Robin scheduling simulated over the same set of fake processes (arrival/burst times); prints a text Gantt chart and average waiting time per algorithm. | `04 CPU Scheduling Algorithms.md` |
| 5 | `05_file_permissions_demo.py` | Creates a file, inspects/changes permission bits with `os.chmod` / `stat`, and checks read/write/execute bits programmatically; notes on Windows ACL vs. POSIX rwx model differences. | `03 File Systems, Permissions and System Calls.md` |
| 6 | `06_memory_and_gc_demo.py` | `sys.getsizeof` on various objects, `gc` module stats, and a deliberate reference cycle that only the cyclic garbage collector can reclaim (`gc.collect()` object counts before/after). | `02 Memory Management and Virtual Memory.md` |

Not covered by a dedicated script: `06 Boot Process and Device Drivers.md` --
boot/BIOS/UEFI and kernel driver loading happen below the level a portable
Python script can observe or simulate meaningfully, so no practical file maps
to it.
