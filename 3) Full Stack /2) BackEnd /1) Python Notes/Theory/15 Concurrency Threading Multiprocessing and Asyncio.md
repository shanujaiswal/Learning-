## Threading

--> The threading module runs multiple threads (lightweight units of a process) that share the same memory space.
--> Due to the Global Interpreter Lock (GIL), only one thread executes Python bytecode at a time -- threading does NOT give true parallelism for CPU-bound work in standard CPython.
--> Threading IS useful for I/O-bound tasks (network requests, file reads, waiting on a database) -- while one thread waits on I/O, the GIL is released and another thread can run.

```python
import threading
import time

def download(name):
    print(f"{name} starting")
    time.sleep(2)  # simulates waiting on I/O
    print(f"{name} done")

threads = [threading.Thread(target=download, args=(f"file{i}",)) for i in range(3)]
for t in threads:
    t.start()
for t in threads:
    t.join()  # wait for all threads to finish before continuing
```

==> Race Conditions and Locks
--> When multiple threads read/write shared data at the same time, the result can depend on unpredictable timing -- a race condition.
--> threading.Lock() ensures only one thread can execute a critical section at a time.

```python
counter = 0
lock = threading.Lock()

def increment():
    global counter
    with lock:  # only one thread can be inside this block at a time
        counter += 1
```

---

## Multiprocessing

--> The multiprocessing module runs separate PROCESSES, each with its own Python interpreter and memory space -- this sidesteps the GIL entirely, giving true parallelism.
--> Best for CPU-bound tasks (heavy computation, data processing) where you want to use multiple CPU cores.
--> Communication between processes requires explicit tools (Queue, Pipe, shared memory) since they don't share memory by default.

```python
from multiprocessing import Process, Pool

def square(n):
    return n * n

if __name__ == "__main__":
    # Using Process directly
    p = Process(target=square, args=(5,))
    p.start()
    p.join()

    # Using a Pool to distribute work across CPU cores
    with Pool(processes=4) as pool:
        results = pool.map(square, [1, 2, 3, 4, 5])
        print(results)  # [1, 4, 9, 16, 25]
```

--> if __name__ == "__main__": guard is required on Windows/macOS (spawn start method) -- without it, child processes re-import and re-run the whole script, causing infinite process spawning.

==> Threading vs Multiprocessing
--> Threading -- lower overhead, shares memory, limited by GIL -- use for I/O-bound work.
--> Multiprocessing -- higher overhead (separate interpreters), true parallel CPU usage, no shared memory by default -- use for CPU-bound work.

---

## Asyncio

--> asyncio provides single-threaded concurrency using an event loop -- multiple tasks cooperatively take turns running, pausing at await points instead of using OS threads.
--> Best for I/O-bound work with MANY concurrent operations (thousands of network requests) -- much lower overhead than one thread per task.
--> async def defines a coroutine function; calling it returns a coroutine object (does not run immediately) -- must be awaited or scheduled on the event loop.

```python
import asyncio

async def fetch_data(name, delay):
    print(f"{name} starting")
    await asyncio.sleep(delay)  # non-blocking sleep -- yields control back to the event loop
    print(f"{name} done")
    return f"{name} result"

async def main():
    # Run multiple coroutines concurrently
    results = await asyncio.gather(
        fetch_data("A", 2),
        fetch_data("B", 1),
        fetch_data("C", 3),
    )
    print(results)

asyncio.run(main())  # entry point that creates and runs the event loop
```

==> Key asyncio Building Blocks
--> await -- pauses the current coroutine until the awaited thing completes, letting the event loop run other tasks meanwhile.
--> asyncio.gather(*coros) -- runs multiple coroutines concurrently and waits for all to finish, returning results in order.
--> asyncio.create_task(coro) -- schedules a coroutine to run in the background immediately, without blocking the current line.
--> asyncio.sleep(seconds) -- a non-blocking sleep (unlike time.sleep, which blocks the entire thread).

==> When to Use Which
--> Asyncio -- best for a huge number of I/O-bound tasks (many API calls) with minimal overhead, but requires the whole call chain to be async-aware (libraries must support it).
--> Threading -- works with ANY existing blocking code (no async rewrite needed), good for moderate I/O concurrency.
--> Multiprocessing -- only real option for genuine CPU-bound parallelism in standard CPython.

## Deep Dive -- The Classic asyncio Pitfall: Blocking Calls Inside a Coroutine

--> `asyncio`'s entire concurrency model relies on coroutines actually yielding control back to the event loop at `await` points -- calling a REGULAR blocking function (not `await`-aware) inside an `async def` doesn't magically make it non-blocking; it freezes the ENTIRE event loop, stalling every other concurrent task, not just the one that called it.

```python
import asyncio
import time

async def bad_task():
    time.sleep(2)   # BLOCKING -- freezes the whole event loop for 2 seconds, blocking ALL other tasks too
    return "done"

async def good_task():
    await asyncio.sleep(2)   # Non-blocking -- yields control back to the event loop while waiting
    return "done"
```

--> This exact mistake is extremely common when wrapping existing synchronous code (a blocking database driver, `requests.get()` instead of an async HTTP client) inside an `async def` without actually verifying it's non-blocking underneath.
--> **The fix for unavoidably-blocking code** -- run it in a separate thread via `loop.run_in_executor()`, which hands the blocking call off to a thread pool so the event loop itself stays free to run other coroutines while it waits.

```python
async def wrapped_blocking_call():
    loop = asyncio.get_event_loop()
    result = await loop.run_in_executor(None, time.sleep, 2)   # Runs in a thread pool, doesn't block the event loop
    return result
```

## Deep Dive -- The GIL's Future -- Free-Threaded Python

--> Python 3.13 introduced an experimental "free-threaded" build (PEP 703) that can run WITHOUT the GIL entirely -- a genuinely significant, long-anticipated change, since the threading limitation described at the top of this file (no true CPU parallelism via threads) has been a defining CPython characteristic for decades.
--> As of this writing, free-threaded Python is still experimental/opt-in, not yet the default -- most third-party C-extension libraries (NumPy, covered in the Data Science and AI folder, and many database drivers) need their own updates to be fully GIL-free-compatible, so multiprocessing remains the reliable, production-safe way to achieve genuine CPU parallelism for the foreseeable near term, even as the free-threaded build matures.
