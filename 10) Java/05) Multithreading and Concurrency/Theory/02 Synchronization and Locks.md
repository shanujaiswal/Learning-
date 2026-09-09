# Why Synchronization Is Needed -- Race Conditions

--> When multiple threads read and write the SAME shared mutable state without coordination, the final result can depend on the unpredictable ORDER in which their operations happen to interleave -- this is a **race condition**, and it's the central problem synchronization solves.
--> The classic example: `count++` looks like a single operation but is actually THREE separate steps -- read `count`, add 1, write it back. If two threads interleave those steps, an increment can be lost entirely.

```text
Thread A: read count (0)
Thread B: read count (0)
Thread A: add 1 -> 1
Thread B: add 1 -> 1
Thread A: write count = 1
Thread B: write count = 1      <-- one increment was completely lost; expected 2, got 1
```

```java
class Counter {
    private int count = 0;
    public void increment() { count++; }     // NOT atomic -- read-modify-write race
    public int get() { return count; }
}
```

--> Running two threads that each call `increment()` 100,000 times on a shared `Counter` will, more often than not, produce a final value LESS than 200,000 -- concrete, reproducible proof that "looks atomic" and "is atomic" are very different things.

# The `synchronized` Keyword -- Intrinsic Locks (Monitors)

--> Every Java object has an implicit lock associated with it, called its **monitor** or **intrinsic lock**. The `synchronized` keyword uses that lock to ensure only ONE thread at a time can execute a block of code guarded by the same lock -- any other thread trying to enter is BLOCKED until the lock is released.

## Synchronized Methods

```java
class Counter {
    private int count = 0;
    public synchronized void increment() {     // locks on "this" for the duration of the call
        count++;
    }
    public synchronized int get() {
        return count;
    }
}
```

--> A `synchronized` instance method locks on `this`. A `synchronized` STATIC method locks on the Class object (`Counter.class`) instead -- these are two DIFFERENT locks, so a synchronized instance method and a synchronized static method on the same class do NOT block each other.

## Synchronized Blocks -- Finer-Grained Locking

--> Locking an entire method can be wasteful if only a small part of it touches shared state. A `synchronized` block lets you lock on a SPECIFIC object for just the critical section, reducing how long other threads are blocked.

```java
class BankAccount {
    private double balance;
    private final Object lock = new Object();     // a dedicated, private lock object

    public void withdraw(double amount) {
        // ... work that doesn't need the lock, e.g. logging, validation ...
        synchronized (lock) {
            if (balance >= amount) {
                balance -= amount;
            }
        }
        // ... more work that doesn't need the lock ...
    }
}
```

--> **Best practice -- use a private, dedicated lock object** (`private final Object lock = new Object();`) rather than locking on `this` or a public field. Locking on `this` exposes your lock to any external code that might also synchronize on your object for unrelated reasons, potentially causing surprising contention or deadlocks you don't control.

## Key Properties of Intrinsic Locks

--> **Mutual exclusion** -- only one thread can hold a given lock at a time; every other thread wanting it blocks.
--> **Reentrancy** -- a thread that already holds a lock can acquire it AGAIN (e.g. calling another synchronized method on the same object from within a synchronized method) without deadlocking itself. The JVM tracks a hold count and only truly releases the lock when the count returns to zero.

```java
public synchronized void outer() {
    inner();          // fine -- same thread re-acquires the same lock it already holds
}
public synchronized void inner() {
    // ...
}
```

--> **Happens-before guarantee** -- releasing a lock establishes a happens-before relationship with any subsequent acquisition of that SAME lock by another thread, meaning all memory writes made before the release are guaranteed VISIBLE to the thread that acquires the lock afterward. This is what makes `synchronized` solve BOTH mutual exclusion AND memory visibility (see file 03 for the visibility half in isolation via `volatile`).
--> **Automatically released on exception** -- if the code inside a `synchronized` block throws, the lock is still released as control leaves the block, unlike manual locks (`ReentrantLock`, below) which require an explicit `finally`.

# Deadlock

--> **Deadlock** occurs when two or more threads are each waiting for a lock the OTHER holds, and neither can proceed -- a permanent standstill. The classic cause is two threads acquiring the SAME two locks in OPPOSITE order.

```java
Object lockA = new Object();
Object lockB = new Object();

// Thread 1
synchronized (lockA) {
    /* ... */
    synchronized (lockB) { /* ... */ }     // waits for lockB
}

// Thread 2 (running concurrently)
synchronized (lockB) {
    /* ... */
    synchronized (lockA) { /* ... */ }     // waits for lockA -- DEADLOCK if Thread 1 already holds lockA
}
```

```text
Thread 1 holds lockA, wants lockB  ---->  blocked, waiting on Thread 2
Thread 2 holds lockB, wants lockA  ---->  blocked, waiting on Thread 1
                    (circular wait -- neither can ever proceed)
```

--> **The standard fix -- consistent lock ordering.** If EVERY thread in the program always acquires locks in the same GLOBAL order (e.g. always lockA before lockB, decided by some fixed rule like object ID or hash code), circular waiting becomes impossible.
--> **Other deadlock-avoidance techniques:** using `tryLock()` with a timeout (see `ReentrantLock` below) to back off and retry instead of blocking forever, minimizing the scope and number of locks held simultaneously, and avoiding calling unknown/overridable code while holding a lock.

# Livelock

--> **Livelock** is like deadlock's more active cousin -- threads are NOT blocked, they're actively running, but they keep responding to each other in a way that prevents any of them from making real progress. A classic analogy: two people in a hallway who each step aside to let the other pass, but both step the SAME direction repeatedly, forever blocking each other despite constant motion.
--> In code, this often happens with overly polite retry/backoff logic: a thread detects potential contention and yields or retries, but the OTHER thread does the exact same thing in lockstep, so neither ever succeeds. Fixing it usually involves adding randomized backoff (so retries don't stay in lockstep) or a priority/ordering rule to break the symmetry.

# Starvation

--> **Starvation** occurs when a thread is perpetually denied access to a resource it needs, because other threads are repeatedly given priority instead -- it's still making "progress" as a system, just never for that one unlucky thread. Common causes: a thread with low priority being consistently passed over, or a `synchronized` block being so contended that some threads wait indefinitely due to unfair scheduling.
--> Standard intrinsic locks (`synchronized`) provide NO fairness guarantee -- the JVM does not promise a "first in line" ordering for threads waiting on a lock. `ReentrantLock` (below) can be configured for fairness to mitigate this, at some throughput cost.

# `ReentrantLock` -- Explicit Locking from `java.util.concurrent.locks`

--> `ReentrantLock` is a more flexible alternative to `synchronized`, offering capabilities intrinsic locks don't: **try-lock with timeout**, **interruptible lock acquisition**, and **fairness policies**. It must be acquired and released EXPLICITLY -- there's no automatic release, so the release MUST go in a `finally` block or the lock can leak forever if an exception is thrown.

```java
import java.util.concurrent.locks.ReentrantLock;

class Counter {
    private int count = 0;
    private final ReentrantLock lock = new ReentrantLock();

    public void increment() {
        lock.lock();
        try {
            count++;
        } finally {
            lock.unlock();     // MUST be in finally -- guarantees release even on exception
        }
    }
}
```

## `tryLock()` -- Non-Blocking / Timed Acquisition

```java
if (lock.tryLock()) {                              // returns immediately -- true if acquired
    try {
        // critical section
    } finally {
        lock.unlock();
    }
} else {
    // could not get the lock right now -- do something else instead of blocking
}

// or with a timeout:
if (lock.tryLock(500, java.util.concurrent.TimeUnit.MILLISECONDS)) {
    try { /* critical section */ } finally { lock.unlock(); }
}
```

--> `tryLock()` is the standard tool for AVOIDING deadlock proactively -- a thread that can't get a second lock within a timeout can release what it already holds and retry later, rather than waiting forever in a potential circular-wait situation.

## Fairness

```java
ReentrantLock fairLock = new ReentrantLock(true);   // fair mode -- FIFO ordering of waiting threads
```

--> A **fair** lock grants access to the longest-waiting thread first, avoiding starvation, but at a noticeable throughput cost (more overhead in managing the waiting queue). The default (`false`, unfair) is faster for most workloads and is what `synchronized` effectively behaves like.

## `synchronized` vs `ReentrantLock` -- When to Use Which

```text
synchronized                          ReentrantLock
------------------------------------  ------------------------------------
Simpler syntax, less error-prone      More verbose, must remember unlock()
Auto-released on exception            Must manually unlock() in finally
No tryLock / timeout support          tryLock(), tryLock(timeout) supported
No interruptible acquisition          lockInterruptibly() supported
No fairness option                    Optional fair mode
Cannot inspect lock state             isLocked(), getHoldCount(), etc.
```

--> **Best practice:** default to `synchronized` for straightforward mutual exclusion -- it's simpler and impossible to forget to release. Reach for `ReentrantLock` specifically when you need timeouts, interruptibility, fairness, or the ability to hold a lock across method boundaries in a way `synchronized`'s block-scoped structure can't express.

# `ReadWriteLock` -- Separate Read and Write Locks

--> Many shared data structures are read FAR more often than they're written. A plain mutex forces even concurrent READS to serialize, which is wasteful when reads don't conflict with each other. `ReadWriteLock` (implemented by `ReentrantReadWriteLock`) splits locking into two locks: a **read lock** that MULTIPLE threads can hold simultaneously (as long as no thread holds the write lock), and a **write lock** that is exclusive (blocks all readers and other writers).

```java
import java.util.concurrent.locks.ReentrantReadWriteLock;

class SharedCache {
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final java.util.Map<String, String> data = new java.util.HashMap<>();

    public String get(String key) {
        rwLock.readLock().lock();               // multiple readers allowed concurrently
        try {
            return data.get(key);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public void put(String key, String value) {
        rwLock.writeLock().lock();               // exclusive -- blocks readers and other writers
        try {
            data.put(key, value);
        } finally {
            rwLock.writeLock().unlock();
        }
    }
}
```

```text
Readers:  R1 ---[reading]---
          R2 ---[reading]---     <-- multiple readers run concurrently, no conflict
          R3 ---[reading]---

Writer:                      W1 ---[writing]---   <-- writer excludes ALL readers and other writers
```

--> **When it pays off:** read-heavy, write-light workloads (e.g. a configuration cache refreshed rarely but read constantly). **When it doesn't help:** write-heavy workloads, where the exclusive write lock dominates anyway and the added bookkeeping of `ReadWriteLock` is pure overhead compared to a plain `ReentrantLock`.
--> **Gotcha:** a thread holding only the read lock cannot upgrade directly to the write lock (attempting to acquire the write lock while holding the read lock will deadlock against other readers) -- it must release the read lock first, then acquire the write lock, accepting that another thread could modify the data in between.

# Best Practices Recap

--> **Always guard shared mutable state** -- if more than one thread can read AND at least one can write the same variable/object without synchronization, you have a bug waiting to happen, even if it "usually" seems to work.
--> **Keep critical sections small** -- lock only what needs protecting, for as short a time as possible, to minimize contention.
--> **Prefer immutable objects and thread-confinement where possible** -- the fastest lock is the one you never needed because nothing is actually shared or mutable.
--> **Always release locks in `finally`** when using explicit `Lock` objects.
--> **Establish a consistent lock-acquisition order across the whole codebase** to prevent deadlock.
--> **Never call unknown/overridable code while holding a lock** unless you're certain it won't try to acquire other locks, to reduce deadlock risk.
