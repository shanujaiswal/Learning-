"""
01 - Processes vs Threads
==========================
Theory chapter: "01 Processes, Threads and the OS Kernel.md"

Key idea to make concrete:
- A PROCESS has its own address space and its own PID (process id).
- A THREAD lives inside a process and shares that process's address space
  (memory, file descriptors, ...) with every other thread in the process.
  All threads of one process report the SAME PID via os.getpid(), but each
  has its own thread-id.

This script spawns the *same* CPU-light task:
  1. Directly in the main process/thread (baseline).
  2. In several separate child PROCESSES (multiprocessing.Process).
  3. In several separate THREADS (threading.Thread) inside this one process.

and prints os.getpid() (and threading.get_ident() for threads) from inside
each worker so the distinction is visible in real numbers, not theory.
"""

import os
import threading
import multiprocessing
import time


def cpu_light_task(label, results=None):
    """A small amount of real work: sum of squares. Cheap on purpose --
    this demo is about identifying WHO ran the code, not benchmarking."""
    total = sum(i * i for i in range(200_000))
    pid = os.getpid()
    tid = threading.get_ident()
    msg = f"[{label}] pid={pid} thread_id={tid} partial_sum={total}"
    print(msg)
    if results is not None:
        results.append(msg)


def demo_processes(n=3):
    print("\n--- Using multiprocessing.Process ---")
    print(f"Main process pid = {os.getpid()}")
    procs = []
    for i in range(n):
        p = multiprocessing.Process(target=cpu_light_task, args=(f"process-worker-{i}",))
        procs.append(p)
        p.start()
    for p in procs:
        p.join()
    print("Observation: each worker printed a DIFFERENT pid than the main "
          "process and than each other -- every Process gets its own "
          "address space and its own PID from the OS kernel.")


def demo_threads(n=3):
    print("\n--- Using threading.Thread ---")
    print(f"Main process pid = {os.getpid()} (this will repeat for every thread)")
    threads = []
    for i in range(n):
        t = threading.Thread(target=cpu_light_task, args=(f"thread-worker-{i}",))
        threads.append(t)
        t.start()
    for t in threads:
        t.join()
    print("Observation: every thread printed the SAME pid (they all live "
          "inside this one process and share its address space) but a "
          "DIFFERENT thread_id -- that is the process-vs-thread distinction.")


if __name__ == "__main__":
    print("=== Baseline: running in the main thread of the main process ===")
    cpu_light_task("main")

    demo_processes()
    demo_threads()

    print("\nSummary:")
    print("- multiprocessing.Process -> distinct PIDs (real OS processes, own memory space)")
    print("- threading.Thread        -> one shared PID, distinct thread ids (shared memory space)")
