# Why These Utilities Exist

--> Raw `synchronized`/`wait`/`notify` (file 02, 03) and the Executor framework (file 04) cover the basics, but many everyday coordination patterns -- "wait until N things finish," "let only K threads through at once," "a map that's safe under heavy concurrent read/write" -- would require careful hand-rolled locking every time. `java.util.concurrent` packages these patterns into tested, tuned building blocks so you rarely need to write your own low-level synchronization from scratch.
--> This file covers two families: **coordination utilities** (`CountDownLatch`, `CyclicBarrier`, `Semaphore`) that control WHEN/HOW MANY threads proceed, and **concurrent collections/atomics** (`ConcurrentHashMap`, `CopyOnWriteArrayList`, the `Atomic*` classes) that give thread-safe data without a single explicit `synchronized` block.

# `CountDownLatch` -- Wait for N Things to Happen, Once

--> A `CountDownLatch` is initialized with a count. One or more threads call `await()` and block; other threads call `countDown()` as they finish work. Once the count hits zero, ALL waiting threads are released -- permanently. **A latch cannot be reset or reused** once it reaches zero.

```java
import java.util.concurrent.CountDownLatch;

CountDownLatch latch = new CountDownLatch(3);   // wait for 3 workers

for (int i = 0; i < 3; i++) {
    new Thread(() -> {
        doWork();
        latch.countDown();      // decrement; never blocks, safe to call from any thread
    }).start();
}

latch.await();                  // blocks main thread until count reaches 0
System.out.println("all 3 workers finished");
```

--> Common shape: **"start gate"** -- have several worker threads all `await()` a single latch initialized to 1, then call `countDown()` once from a controller thread to release them all at the same instant (useful for simulating simultaneous load).
--> `await(timeout, unit)` returns a boolean instead of blocking forever, so you can bail out if workers are too slow.
--> **Gotcha:** if a worker throws before calling `countDown()`, the latch NEVER reaches zero and any thread waiting on it blocks forever -- always `countDown()` in a `finally` block.

```java
CountDownLatch startGate = new CountDownLatch(1);
CountDownLatch doneGate = new CountDownLatch(3);

for (int i = 0; i < 3; i++) {
    new Thread(() -> {
        try {
            startGate.await();      // all workers wait for the same starting signal
            doWork();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            doneGate.countDown();   // always signal completion, even on failure
        }
    }).start();
}
startGate.countDown();              // release all workers at once
doneGate.await();                   // wait for all to finish
```

# `CyclicBarrier` -- Wait for N Threads, Repeatedly

--> A `CyclicBarrier` also waits for N parties, but where `CountDownLatch` is a one-shot gate crossed by (potentially) DIFFERENT threads calling `countDown()`, a `CyclicBarrier` is crossed by the SAME N worker threads repeatedly, over multiple phases -- and it **automatically resets** once all parties arrive, ready for the next round.

```java
import java.util.concurrent.CyclicBarrier;

CyclicBarrier barrier = new CyclicBarrier(3, () ->
        System.out.println("all 3 reached the barrier -- running barrier action"));
        //                   ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
        //                   optional Runnable, run once per cycle by the LAST thread to arrive

for (int i = 0; i < 3; i++) {
    final int id = i;
    new Thread(() -> {
        for (int phase = 1; phase <= 2; phase++) {
            doPhaseOfWork(id, phase);
            try {
                barrier.await();       // block until all 3 threads reach this point THIS phase
            } catch (Exception e) {
                Thread.currentThread().interrupt();
            }
        }
    }).start();
}
```

--> Typical use case: **multi-phase parallel computation** where every worker must finish phase 1 before ANY worker starts phase 2 (e.g. parallel matrix operations, simulations with discrete time steps).
--> **`CountDownLatch` vs `CyclicBarrier` -- the core distinction:**

```text
CountDownLatch:                          CyclicBarrier:
- one-time use, cannot reset             - reusable across multiple "rounds"
- any thread can countDown()             - only the N parties themselves await()
- decouples "signalers" from "waiters"   - the SAME threads both do work AND wait
- typical: N workers, 1 waiter (or vice  - typical: N workers synchronizing with
  versa)                                   each other repeatedly
```

--> **Gotcha:** if any party's `await()` throws (e.g. is interrupted, or times out), the barrier is marked BROKEN and ALL other waiting/future threads immediately get a `BrokenBarrierException` -- a barrier is "all or nothing" per cycle, unlike a latch where individual failures don't propagate to others.

# `Semaphore` -- Limit Concurrent Access to N Permits

--> A `Semaphore` maintains a set of virtual "permits." `acquire()` takes a permit (blocking if none available); `release()` returns one. Unlike a lock, a semaphore is **not owned by a single thread** -- any thread can release a permit, even one it didn't acquire, and a semaphore can allow MORE than one thread through at once (permits > 1).

```java
import java.util.concurrent.Semaphore;

Semaphore semaphore = new Semaphore(3);   // only 3 threads may hold a permit at once

Runnable useResource = () -> {
    try {
        semaphore.acquire();               // blocks if all 3 permits are taken
        System.out.println(Thread.currentThread().getName() + " acquired permit");
        useLimitedResource();
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    } finally {
        semaphore.release();                // ALWAYS release, even on exception
    }
};
```

--> Classic use case: **bounding concurrent access to a limited resource pool** -- e.g. at most 3 simultaneous connections to a database, at most N simultaneous downloads.
--> **`tryAcquire()`** -- non-blocking attempt; returns `false` immediately if no permit is free instead of waiting. Overloads accept a timeout.
--> **Binary semaphore (permits=1)** can act like a lock, but critically **it has no notion of ownership** -- any thread may call `release()`, which is both a power (flexible signaling patterns) and a danger (nothing stops a bug from releasing twice or from the wrong thread, unlike `ReentrantLock` which enforces owner-only unlock, file 02).
--> **Fairness:** `new Semaphore(permits, true)` uses a fair ordering (FIFO) for waiting threads at some throughput cost; the default is unfair (better throughput, no starvation guarantee).

```java
Semaphore fair = new Semaphore(2, true);   // FIFO fairness -- avoids thread starvation under contention
```

# `ConcurrentHashMap` -- Thread-Safe Map Without Locking the Whole Thing

--> `HashMap` is not thread-safe; wrapping it with `Collections.synchronizedMap()` makes it safe but serializes EVERY access behind one lock, killing concurrency. `ConcurrentHashMap` instead uses fine-grained internal locking/CAS (compare-and-swap) so multiple threads can read AND write different parts of the map truly concurrently.

```java
import java.util.concurrent.ConcurrentHashMap;

ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();

map.put("a", 1);
map.get("a");                     // reads never block on writes to other keys

// atomic compound operations -- do the check-then-act in ONE call, no external synchronization needed
map.putIfAbsent("b", 2);                                  // only inserts if key absent
map.computeIfAbsent("c", k -> expensiveCompute(k));       // compute+insert atomically if absent
map.compute("a", (k, v) -> v == null ? 1 : v + 1);        // atomic update, works whether present or not
map.merge("a", 1, Integer::sum);                          // atomic "add or initialize"
```

--> **Never do this with a `ConcurrentHashMap` (or any map) under concurrency:**

```java
// BROKEN -- check-then-act race: two threads can both see "absent" and both insert
if (!map.containsKey("a")) {
    map.put("a", computeValue());
}
```

--> **Best practice:** prefer the atomic compound methods (`putIfAbsent`, `computeIfAbsent`, `compute`, `merge`) over manual "check, then act" sequences -- they close the race window entirely because the whole operation is atomic.
--> **Iteration:** iterators over a `ConcurrentHashMap` are *weakly consistent* -- they never throw `ConcurrentModificationException` and reflect SOME state of the map during iteration, but not necessarily a single fixed snapshot; a value added mid-iteration might or might not be seen.
--> **`null` is not allowed** as either a key or value (unlike `HashMap`) -- this is deliberate, since `get(key)` returning `null` would be ambiguous between "absent" and "mapped to null" in a concurrent setting where you can't safely follow up with a separate `containsKey` check.

# `CopyOnWriteArrayList` -- Thread-Safe List Optimized for Many Reads, Few Writes

--> Every mutation (`add`, `remove`, `set`) copies the ENTIRE underlying array and atomically swaps the reference; reads use whatever array snapshot was current when the read started and need NO locking at all.

```java
import java.util.concurrent.CopyOnWriteArrayList;

CopyOnWriteArrayList<String> list = new CopyOnWriteArrayList<>();
list.add("x");
list.add("y");

for (String s : list) {          // iterating is always safe, even if another thread mutates concurrently
    System.out.println(s);       // this iterator sees a fixed SNAPSHOT taken when iteration began
}
```

--> **Iterator never throws `ConcurrentModificationException`** -- it iterates a snapshot of the array taken at iterator-creation time, so concurrent adds/removes by other threads are simply invisible to an in-progress iteration.
--> **Cost model:** reads are cheap (no locking, no copying); every single write copies the whole array, so this is O(n) per write -- great for lists that are read constantly but rarely modified (e.g. a list of event listeners), TERRIBLE for write-heavy workloads.
--> **Common mistake:** using `CopyOnWriteArrayList` as a general-purpose thread-safe list for a workload with frequent writes -- the repeated full-array copies can dominate CPU time and generate heavy GC pressure. **Best practice:** reach for it specifically when reads vastly outnumber writes; otherwise consider `Collections.synchronizedList()` or a `ConcurrentHashMap`-backed structure.

# Atomic Classes -- Lock-Free Updates via CAS

--> `AtomicInteger`, `AtomicLong`, `AtomicReference<V>`, etc. wrap a single value and provide atomic read-modify-write operations WITHOUT using `synchronized` or explicit locks -- internally, they use the CPU's **compare-and-swap (CAS)** instruction: "update this value to X, but only if it's still equal to the value Y I last read."

```java
import java.util.concurrent.atomic.AtomicInteger;

AtomicInteger counter = new AtomicInteger(0);

counter.incrementAndGet();          // atomic ++counter, returns new value
counter.getAndIncrement();          // atomic counter++, returns OLD value
counter.addAndGet(5);               // atomic counter += 5, returns new value
counter.compareAndSet(6, 100);      // if current value == 6, set to 100; returns whether it succeeded
counter.updateAndGet(v -> v * 2);   // atomic update via a function
```

--> **Why this matters:** the naive `count++` on a plain `int` is NOT atomic (it's really read-modify-write, three separate steps that can interleave across threads) -- this is the classic race condition from file 02. `AtomicInteger.incrementAndGet()` does the same logical operation as a single indivisible hardware-backed step, with no lock needed.

```text
Plain int count++ under contention:      AtomicInteger.incrementAndGet():
  Thread A reads count=5                   CAS loop internally:
  Thread B reads count=5                     1. read current value
  Thread A writes count=6                    2. compute new value
  Thread B writes count=6   <- LOST UPDATE   3. CAS(old, new) -- retry step 1 if another
  (should have been 7)                          thread's CAS won the race first
```

--> **`AtomicReference<V>`** -- same CAS idea for object references, useful for lock-free updates to a shared reference (e.g. implementing a simple lock-free stack node, or atomically swapping an immutable config object).

```java
AtomicReference<String> ref = new AtomicReference<>("initial");
boolean swapped = ref.compareAndSet("initial", "updated");   // true; only succeeds if still "initial"
```

--> **When CAS "fails" it doesn't throw** -- `compareAndSet` just returns `false`, and the typical pattern is to retry in a loop until it succeeds (this is exactly what `incrementAndGet()` etc. do internally).
--> **`AtomicLong`** is the `long` counterpart; for VERY high-contention counters where you don't need the exact running value on every read (only an eventual total), `LongAdder` (also in `java.util.concurrent.atomic`) scales better by splitting the counter across multiple internal cells to reduce CAS contention, at the cost of a slightly more expensive `sum()` read.
--> **Gotcha:** atomics only make a SINGLE variable's updates atomic -- combining multiple atomic operations together is NOT automatically atomic as a whole (e.g. reading two `AtomicInteger`s and using both values still has a race between the two reads unless you use a different coordination mechanism).

# Common Gotchas and Best Practices Recap

--> **`CountDownLatch` is one-shot** -- once it hits zero it cannot be reused; reach for `CyclicBarrier` when the same threads need to resynchronize across multiple repeated phases.
--> **Always release what you acquire** -- `countDown()`, `release()`, and lock `unlock()` calls belong in a `finally` block so a thrown exception can't leave other threads blocked forever.
--> **A broken `CyclicBarrier` poisons the whole cycle** -- one thread's interruption/timeout immediately breaks the barrier for every other waiting party.
--> **Prefer atomic compound map operations** (`computeIfAbsent`, `merge`, etc.) over manual check-then-act on a `ConcurrentHashMap` to avoid races.
--> **`CopyOnWriteArrayList` is read-optimized, write-expensive** -- match it to read-heavy workloads only.
--> **Atomics guarantee atomicity per-variable, not across multiple variables** -- don't assume combining several atomic reads/writes is itself atomic.
--> **`Semaphore` has no ownership** -- any thread can `release()`, so bugs that double-release or release from the wrong place won't be caught the way a `ReentrantLock`'s owner-only unlock would catch them.
