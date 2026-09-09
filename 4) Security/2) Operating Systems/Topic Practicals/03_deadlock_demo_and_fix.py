"""
03 - Deadlock Demo and Fix
============================
Theory chapter: "05 Deadlocks.md"

Classic deadlock: two threads each need two locks, but acquire them in
OPPOSITE order:
    Thread A: acquire lock_1, then lock_2
    Thread B: acquire lock_2, then lock_1

If A grabs lock_1 and B grabs lock_2 at (roughly) the same time, A then
blocks waiting for lock_2 (held by B) and B blocks waiting for lock_1 (held
by A). Neither can proceed -> deadlock. This matches all four Coffman
conditions from the Theory chapter: mutual exclusion, hold-and-wait, no
preemption, and circular wait.

To demo this WITHOUT actually hanging forever, we use
`lock.acquire(timeout=...)` so a stuck thread gives up after a few seconds
and reports the deadlock instead of freezing the process.

Then we show the fix: both threads acquire the two locks in the SAME global
order (lock_1 before lock_2), which breaks the circular-wait condition and
makes deadlock impossible for this pair of locks.
"""

import threading
import time

ACQUIRE_TIMEOUT = 3.0  # seconds; long enough for the deadlock to genuinely form


def deadlock_demo():
    print("--- Deadlock demo (opposite lock-acquisition order) ---")
    lock_1 = threading.Lock()
    lock_2 = threading.Lock()

    # Barrier lets us line the two threads up so both grab their first lock
    # before either tries for the second -- guarantees the deadlock forms
    # instead of leaving it to chance.
    barrier = threading.Barrier(2)
    outcomes = {}

    def worker_a():
        with lock_1:
            print("Thread-A: acquired lock_1, now waiting for lock_2...")
            barrier.wait()
            got = lock_2.acquire(timeout=ACQUIRE_TIMEOUT)
            if got:
                print("Thread-A: acquired lock_2 (no deadlock this run)")
                lock_2.release()
                outcomes["A"] = "success"
            else:
                print(f"Thread-A: TIMED OUT after {ACQUIRE_TIMEOUT}s waiting "
                      "for lock_2 -> deadlock detected")
                outcomes["A"] = "deadlock"

    def worker_b():
        with lock_2:
            print("Thread-B: acquired lock_2, now waiting for lock_1...")
            barrier.wait()
            got = lock_1.acquire(timeout=ACQUIRE_TIMEOUT)
            if got:
                print("Thread-B: acquired lock_1 (no deadlock this run)")
                lock_1.release()
                outcomes["B"] = "success"
            else:
                print(f"Thread-B: TIMED OUT after {ACQUIRE_TIMEOUT}s waiting "
                      "for lock_1 -> deadlock detected")
                outcomes["B"] = "deadlock"

    ta = threading.Thread(target=worker_a)
    tb = threading.Thread(target=worker_b)
    start = time.time()
    ta.start()
    tb.start()
    ta.join()
    tb.join()
    elapsed = time.time() - start

    print(f"Elapsed: {elapsed:.1f}s -- outcomes: {outcomes}")
    if "deadlock" in outcomes.values():
        print("RESULT: genuine deadlock reproduced and detected via timeout "
              "(instead of the process hanging forever).\n")
    else:
        print("RESULT: both threads happened to succeed this run -- "
              "the barrier normally forces the deadlock; try again.\n")


def fixed_demo():
    print("--- Fixed version (consistent global lock order) ---")
    lock_1 = threading.Lock()
    lock_2 = threading.Lock()
    barrier = threading.Barrier(2)
    outcomes = {}

    # Both threads now agree on a single global order: always lock_1 first,
    # then lock_2. This removes the circular-wait condition, so deadlock
    # between these two locks is no longer possible.
    def worker_a():
        with lock_1:
            barrier.wait()
            with lock_2:
                print("Thread-A: acquired lock_1 then lock_2 -- success")
                outcomes["A"] = "success"

    def worker_b():
        barrier.wait()
        with lock_1:
            with lock_2:
                print("Thread-B: acquired lock_1 then lock_2 -- success")
                outcomes["B"] = "success"

    ta = threading.Thread(target=worker_a)
    tb = threading.Thread(target=worker_b)
    start = time.time()
    ta.start()
    tb.start()
    ta.join()
    tb.join()
    elapsed = time.time() - start

    print(f"Elapsed: {elapsed:.2f}s -- outcomes: {outcomes}")
    assert outcomes == {"A": "success", "B": "success"}
    print("RESULT: no deadlock -- consistent lock ordering broke the "
          "circular-wait condition required for deadlock.")


if __name__ == "__main__":
    deadlock_demo()
    fixed_demo()
