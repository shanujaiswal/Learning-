"""
02 - Race Condition and Lock
==============================
Theory chapters: "01 Processes, Threads and the OS Kernel.md" and
                  "05 Deadlocks.md" (concurrency-safety background)

A race condition happens when multiple threads read-modify-write a shared
variable without synchronization, and the OS scheduler interleaves their
instructions in a way that loses updates.

`counter += 1` is NOT atomic in Python. It compiles to roughly:
    tmp = counter      # read
    tmp = tmp + 1      # modify
    counter = tmp       # write
If two threads interleave between the read and the write, one increment can
be silently lost.

This script:
  1. Runs many threads incrementing a shared counter WITHOUT a lock and
     shows the final total is usually WRONG (less than expected).
  2. Runs the exact same workload WITH a threading.Lock and shows the final
     total is always CORRECT.
"""

import threading

NUM_THREADS = 8
INCREMENTS_PER_THREAD = 100_000
EXPECTED_TOTAL = NUM_THREADS * INCREMENTS_PER_THREAD


class UnsafeCounter:
    def __init__(self):
        self.value = 0

    def increment_many(self, times):
        for _ in range(times):
            # Deliberately unsynchronized read-modify-write.
            current = self.value
            current += 1
            self.value = current


class SafeCounter:
    def __init__(self):
        self.value = 0
        self.lock = threading.Lock()

    def increment_many(self, times):
        for _ in range(times):
            with self.lock:
                current = self.value
                current += 1
                self.value = current


def run_unsafe():
    print("--- WITHOUT a lock (race condition) ---")
    counter = UnsafeCounter()
    threads = [
        threading.Thread(target=counter.increment_many, args=(INCREMENTS_PER_THREAD,))
        for _ in range(NUM_THREADS)
    ]
    for t in threads:
        t.start()
    for t in threads:
        t.join()

    print(f"Expected total: {EXPECTED_TOTAL}")
    print(f"Actual total:   {counter.value}")
    if counter.value != EXPECTED_TOTAL:
        lost = EXPECTED_TOTAL - counter.value
        print(f"BUG CONFIRMED: {lost} increments were lost to interleaving.")
    else:
        print("(This run happened not to lose any increments -- race "
              "conditions are timing-dependent; re-run if this happens.)")


def run_safe():
    print("\n--- WITH a threading.Lock (fixed) ---")
    counter = SafeCounter()
    threads = [
        threading.Thread(target=counter.increment_many, args=(INCREMENTS_PER_THREAD,))
        for _ in range(NUM_THREADS)
    ]
    for t in threads:
        t.start()
    for t in threads:
        t.join()

    print(f"Expected total: {EXPECTED_TOTAL}")
    print(f"Actual total:   {counter.value}")
    assert counter.value == EXPECTED_TOTAL, "Lock should guarantee correctness!"
    print("CONFIRMED: the lock serializes access to the shared counter, so "
          "no increments are ever lost, no matter how threads interleave.")


if __name__ == "__main__":
    run_unsafe()
    run_safe()
