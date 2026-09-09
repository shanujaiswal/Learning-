"""
06_concurrency_demo.py

Maps to Theory chapter:
    15 Concurrency Threading Multiprocessing and Asyncio.md

Demonstrates and times three approaches side by side:
    1. CPU-bound task run with multiprocessing.Pool (real speedup on multi-core CPUs)
    2. I/O-bound task run with threading
    3. The same I/O-bound task run with asyncio.gather

No external dependencies (uses only the standard library). Run directly:
    python "06_concurrency_demo.py"

Notes:
    - On Windows, multiprocessing requires the `if __name__ == "__main__":` guard
      (used below) because child processes re-import this module.
    - The CPU-bound speedup will vary depending on how many CPU cores are
      available on the machine running this script; on a single-core VM you
      may see little or no speedup due to process-creation overhead.
"""

from __future__ import annotations
import asyncio
import multiprocessing
import threading
import time


# ---------------------------------------------------------------------------
# 1. CPU-bound task: counting primes in a range (real CPU work, no I/O).
# ---------------------------------------------------------------------------
def count_primes(n: int) -> int:
    """Deliberately CPU-heavy: counts primes below n using trial division."""
    count = 0
    for candidate in range(2, n):
        is_prime = True
        for divisor in range(2, int(candidate ** 0.5) + 1):
            if candidate % divisor == 0:
                is_prime = False
                break
        if is_prime:
            count += 1
    return count


def run_cpu_bound_sequential(chunks: list[int]) -> tuple[list[int], float]:
    start = time.perf_counter()
    results = [count_primes(n) for n in chunks]
    elapsed = time.perf_counter() - start
    return results, elapsed


def run_cpu_bound_multiprocessing(chunks: list[int]) -> tuple[list[int], float]:
    start = time.perf_counter()
    with multiprocessing.Pool(processes=min(len(chunks), multiprocessing.cpu_count())) as pool:
        results = pool.map(count_primes, chunks)
    elapsed = time.perf_counter() - start
    return results, elapsed


# ---------------------------------------------------------------------------
# 2. I/O-bound task: simulate a slow network/disk call with a sleep.
# ---------------------------------------------------------------------------
def io_bound_task_sync(task_id: int, delay: float = 0.3) -> str:
    """Simulates blocking I/O (e.g. a network call) using time.sleep."""
    time.sleep(delay)
    return f"task-{task_id}-done"


def run_io_bound_threading(task_ids: list[int]) -> tuple[list[str], float]:
    results: list[str] = [""] * len(task_ids)

    def worker(index: int, task_id: int) -> None:
        results[index] = io_bound_task_sync(task_id)

    start = time.perf_counter()
    threads = [
        threading.Thread(target=worker, args=(i, task_id))
        for i, task_id in enumerate(task_ids)
    ]
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    elapsed = time.perf_counter() - start
    return results, elapsed


# ---------------------------------------------------------------------------
# 3. Same I/O-bound task, but with asyncio + asyncio.sleep + gather.
# ---------------------------------------------------------------------------
async def io_bound_task_async(task_id: int, delay: float = 0.3) -> str:
    await asyncio.sleep(delay)
    return f"task-{task_id}-done"


async def run_io_bound_asyncio(task_ids: list[int]) -> tuple[list[str], float]:
    start = time.perf_counter()
    results = await asyncio.gather(*(io_bound_task_async(task_id) for task_id in task_ids))
    elapsed = time.perf_counter() - start
    return list(results), elapsed


def demo_cpu_bound() -> None:
    print("=== CPU-bound: sequential vs multiprocessing.Pool ===")
    chunks = [400_000, 400_000, 400_000, 400_000]  # 4 equally-sized, sizable chunks of work

    seq_results, seq_time = run_cpu_bound_sequential(chunks)
    print(f"Sequential:      results={seq_results} time={seq_time:.3f}s")

    mp_results, mp_time = run_cpu_bound_multiprocessing(chunks)
    print(f"Multiprocessing: results={mp_results} time={mp_time:.3f}s")

    if mp_time > 0:
        speedup = seq_time / mp_time
        print(f"Speedup from multiprocessing: {speedup:.2f}x "
              f"(CPU count on this machine: {multiprocessing.cpu_count()})")


def demo_io_bound() -> None:
    print("\n=== I/O-bound: threading vs asyncio ===")
    task_ids = list(range(8))
    delay = 0.3

    seq_start = time.perf_counter()
    _ = [io_bound_task_sync(task_id, delay) for task_id in task_ids]
    seq_elapsed = time.perf_counter() - seq_start
    print(f"Sequential (blocking): time={seq_elapsed:.3f}s for {len(task_ids)} tasks")

    thread_results, thread_time = run_io_bound_threading(task_ids)
    print(f"Threading:              time={thread_time:.3f}s -> {thread_results}")

    async_results, async_time = asyncio.run(run_io_bound_asyncio(task_ids))
    print(f"Asyncio (gather):       time={async_time:.3f}s -> {async_results}")

    print(
        "\nObservation: threading and asyncio both run the "
        f"{len(task_ids)} x {delay}s I/O waits concurrently, finishing in roughly "
        f"~{delay}s instead of ~{len(task_ids) * delay:.1f}s sequential, "
        "while multiprocessing helps the CPU-bound section above by spreading "
        "real computation across multiple cores."
    )


def main() -> None:
    demo_cpu_bound()
    demo_io_bound()


if __name__ == "__main__":
    # Required on Windows/spawn platforms so child processes importing this
    # module don't re-trigger the whole script.
    main()
