### Concurrent Port Scanning with asyncio and Threading

--> `01 Networking Basics for Python Security Scripts.md` built a single-threaded, sequential port scanner and explicitly deferred making it fast -- scanning a /24 network sequentially at even a 1-second timeout per port would take hours. This file delivers that concurrency, and covers the general multithreading/multiprocessing patterns behind it.

## Ethical note

--> Everything below inherits the same restriction as the single-threaded scanner in `01` -- only scan hosts/networks you own or are explicitly authorized to test (lab VMs, `scanme.nmap.org`). Making a scanner faster does not make it more legal to point at someone else's infrastructure; if anything, a fast scanner is more likely to trip an IDS/IPS and draw attention.

## Why threading helps here specifically

--> Port scanning is I/O-bound, not CPU-bound -- almost all of the time in `scan_port()` is spent WAITING on the network (a `connect()` call blocking until a TCP handshake completes, fails, or times out), not doing actual computation. This is exactly the case where Python's Global Interpreter Lock (GIL) is not a bottleneck -- a thread blocked on socket I/O releases the GIL, letting other threads run -- so `threading` gives a genuine, large speedup for this workload, unlike CPU-bound work where the GIL would prevent threads from running Python bytecode truly in parallel.

## Threaded scanner using a ThreadPoolExecutor

```python
import socket
import errno
from concurrent.futures import ThreadPoolExecutor, as_completed

def scan_port(host, port, timeout=1.0):
    """Same logic as the single-threaded version in file 01 -- unchanged."""
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.settimeout(timeout)
    try:
        result = sock.connect_ex((host, port))
        return port, ("open" if result == 0 else "closed")
    except socket.timeout:
        return port, "filtered"
    except socket.error:
        return port, "error"
    finally:
        sock.close()

def scan_range_threaded(host, start_port, end_port, max_workers=200):
    ports = range(start_port, end_port + 1)
    open_ports = []

    # max_workers controls how many sockets are in flight simultaneously -- too high
    # can exhaust local ephemeral ports or trip rate-limiting/IDS on the target
    with ThreadPoolExecutor(max_workers=max_workers) as pool:
        futures = {pool.submit(scan_port, host, p): p for p in ports}
        for future in as_completed(futures):
            port, status = future.result()
            if status == "open":
                print(f"    {port}/tcp  open")
                open_ports.append(port)
    return sorted(open_ports)

if __name__ == "__main__":
    found = scan_range_threaded("scanme.nmap.org", 1, 1000, max_workers=200)
    print(f"[+] Open ports: {found}")
```

--> `as_completed()` yields futures as they finish, not in submission order -- ideal here, since you want to print/collect each result the moment it's ready rather than waiting for port 1's result before you're allowed to see port 999's.
--> Scanning 1000 ports this way typically finishes in well under a second against a responsive host, versus 1000 seconds worst-case sequentially if every port were filtered and each one had to hit the full timeout.

## The asyncio version -- a single thread, cooperative concurrency

--> `asyncio` achieves similar concurrency without OS threads at all -- a single thread runs an event loop that switches between many pending coroutines whenever one of them is awaiting I/O, rather than the OS scheduler switching between separate threads. For pure socket I/O at very high connection counts, this avoids per-thread memory overhead and context-switching cost that a thread pool of, say, 5000 workers would incur.

```python
import asyncio

async def scan_port_async(host, port, timeout=1.0):
    """asyncio equivalent: open_connection() replaces the blocking connect()."""
    try:
        conn = asyncio.open_connection(host, port)
        reader, writer = await asyncio.wait_for(conn, timeout=timeout)
        writer.close()
        await writer.wait_closed()
        return port, "open"
    except asyncio.TimeoutError:
        return port, "filtered"
    except (ConnectionRefusedError, OSError):
        return port, "closed"

async def scan_range_async(host, start_port, end_port, concurrency=500):
    semaphore = asyncio.Semaphore(concurrency)   # caps how many connection attempts are in flight

    async def bounded_scan(port):
        async with semaphore:
            return await scan_port_async(host, port)

    tasks = [bounded_scan(p) for p in range(start_port, end_port + 1)]
    results = await asyncio.gather(*tasks)

    open_ports = sorted(port for port, status in results if status == "open")
    return open_ports

if __name__ == "__main__":
    found = asyncio.run(scan_range_async("scanme.nmap.org", 1, 1000, concurrency=500))
    print(f"[+] Open ports: {found}")
```

--> The `asyncio.Semaphore` here plays exactly the same role as `max_workers` did in the ThreadPoolExecutor version -- without it, `asyncio.gather()` would fire off ALL 1000 connection attempts at once, which can overwhelm local resources or look like a SYN flood to the target's IDS rather than a normal scan.
--> `asyncio.wait_for()` wraps the coroutine with a timeout the same way `socket.settimeout()` did for the blocking version -- asyncio has no implicit per-operation timeout, so forgetting this wrapper means a single unresponsive port can hang that one task indefinitely (though other tasks keep progressing, since it's only that one coroutine that's stuck waiting).

## Threading vs multiprocessing vs asyncio -- choosing the right tool

--> **Threading (`threading` / `concurrent.futures.ThreadPoolExecutor`)** -- best for I/O-bound work (network calls, file I/O, subprocess calls) where threads spend most of their time blocked waiting, not computing -- the GIL is released during that wait, so real overlap happens. Simple mental model (regular blocking-style code, just spread across workers), but each thread carries real OS-level overhead, limiting practical scale to roughly hundreds-to-low-thousands of concurrent workers.
--> **Multiprocessing (`multiprocessing` / `concurrent.futures.ProcessPoolExecutor`)** -- necessary for CPU-BOUND work (hashing millions of candidate passwords, parsing large amounts of data, running a computationally heavy fuzzing mutation loop) -- each process has its OWN Python interpreter and GIL, so this is the only one of the three that achieves genuine parallel execution of Python bytecode on multiple cores. Higher overhead than threads (each process is a full separate interpreter, and data passed between processes must be pickled/serialized rather than simply shared in memory).
--> **asyncio** -- best when you need VERY HIGH concurrency (thousands to tens of thousands of simultaneous I/O operations) specifically for I/O-bound work, since coroutines are far lighter-weight than OS threads -- the trade-off is that every I/O call in the chain must be written in `async`/`await` style (or wrapped via `run_in_executor()` if you must call a blocking library), so it doesn't mix transparently with ordinary blocking code the way threading does.

```python
# CPU-bound example where multiprocessing, not threading, is the right choice:
# hashing a large candidate password list -- this is pure computation, no I/O waiting involved
import hashlib
from concurrent.futures import ProcessPoolExecutor

def hash_candidate(password):
    return password, hashlib.sha256(password.encode()).hexdigest()

def hash_wordlist_parallel(candidates):
    # ProcessPoolExecutor spreads the CPU-bound hashing work across actual OS processes/cores --
    # a ThreadPoolExecutor here would NOT speed this up meaningfully, since the GIL blocks
    # true parallel execution of CPU-bound Python bytecode across threads
    with ProcessPoolExecutor() as pool:
        return dict(pool.map(hash_candidate, candidates))
```

--> A useful rule of thumb: "waiting on the network or disk -> threading or asyncio; crunching numbers/hashes/parsing in Python itself -> multiprocessing." Mixing them is common in real tooling too -- e.g. a scanner using asyncio for the network I/O, handing any CPU-heavy post-processing of results (like decoding/parsing thousands of banner responses) off to a process pool.

## Cross-references

--> Builds directly on the single-threaded scanner and `socket` fundamentals in `01 Networking Basics for Python Security Scripts.md`. The GIL-release-during-I/O-wait behavior here is the Python-specific instance of the general race-condition/concurrency material in the Operating Systems track's `07 Race Conditions and Classic Synchronization Problems.md` -- a `ThreadPoolExecutor` still shares state (e.g. the `open_ports` list) across threads, so appending to a plain list from multiple worker callbacks without a lock is technically still a race in principle, though CPython's GIL happens to make simple list `append()` calls atomic enough in practice that it rarely bites here specifically.
