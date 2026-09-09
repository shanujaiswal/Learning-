# Why "Java Doesn't Have Memory Leaks" Is a Myth

--> Because Java has automatic garbage collection, it's tempting to think memory leaks are a C/C++ problem that doesn't apply here. In reality, Java absolutely CAN leak memory -- just not by forgetting to `free()`. A **Java memory leak** is an object that is technically still REACHABLE (so the GC correctly refuses to collect it, because collecting it would be unsafe/incorrect) but is no longer actually NEEDED by the application. The GC has no way to know intent -- it only knows reachability, not usefulness.
--> These leaks are often slower and sneakier than native leaks -- an application can run fine for days or weeks, with heap usage creeping up slightly after every GC cycle, until it finally exhausts the heap and throws `OutOfMemoryError`, often in production, often at the worst time.

# Common Memory Leak Patterns

## 1) Unbounded Caches

--> A `HashMap` used as a cache that's never evicted or bounded grows forever as new keys are added -- each entry is a real, reachable, "in-use-looking" object, so nothing about it is obviously wrong.

```java
// LEAK: this map only grows -- entries are never removed
private static final Map<String, byte[]> cache = new HashMap<>();

void handleRequest(String userId, byte[] data) {
    cache.put(userId, data);   // grows forever if userIds keep changing
}
```

--> **Fix** -- use a bounded cache with an eviction policy: `LinkedHashMap` in LRU mode with a fixed capacity, or better, a dedicated caching library (Caffeine, Guava `CacheBuilder`) that supports size limits, TTL expiry, and weak/soft-referenced values.

## 2) Listener / Callback Registration Without Deregistration

--> Registering a listener/observer/callback with a long-lived publisher, but never unregistering it, keeps the LISTENER (and everything it references) alive for as long as the PUBLISHER lives -- even if the listener's own "owner" is logically done and would otherwise be garbage.

```java
// LEAK: publisher outlives the subscriber's intended lifetime, keeping it pinned in memory
publisher.addListener(myShortLivedComponent);
// ... myShortLivedComponent is done being used, but was never removed:
// publisher.removeListener(myShortLivedComponent);   <-- missing
```

--> **Fix** -- always pair registration with deregistration (often in a `close()`/`dispose()`/`@PreDestroy` method), or have the publisher hold listeners via `WeakReference` so they can be collected even if deregistration is forgotten.

## 3) Inner Class / Anonymous Class Holding an Implicit Outer Reference

--> A non-static inner class (and anonymous classes) implicitly hold a reference to their enclosing instance -- if an instance of the inner class outlives the intended lifetime and is reachable from somewhere long-lived, it drags the ENTIRE outer object along with it.

```java
class MyActivity {
    // BAD: anonymous Runnable implicitly holds a reference to MyActivity
    Runnable leaky = new Runnable() {
        public void run() { /* ... */ }
    };
}
```

--> **Fix** -- use a `static` nested class (which does NOT hold an outer reference) plus an explicit `WeakReference` to the outer object if it's occasionally needed. This exact pattern is a famous, well-documented source of leaks in Android `Activity`/`Handler` code.

## 4) `ThreadLocal` Not Cleaned Up (Especially in Thread Pools)

--> `ThreadLocal` values are stored in a map owned by the THREAD itself. In a thread pool, threads are reused indefinitely -- if a `ThreadLocal` is set but never `.remove()`d, its value survives long after the logical "task" that set it has finished, for the entire remaining lifetime of that pooled thread.

```java
private static final ThreadLocal<ExpensiveContext> ctx = new ThreadLocal<>();

void handleRequest() {
    ctx.set(new ExpensiveContext());
    try {
        // ... use ctx.get() ...
    } finally {
        ctx.remove();     // REQUIRED in pooled-thread environments -- otherwise it leaks per pooled thread
    }
}
```

## 5) Unclosed Resources (Streams, Connections)

--> Not a heap leak in the traditional sense, but the same category of problem -- native/off-heap or externally-limited resources (file handles, DB connections, sockets) that are opened but never closed will exhaust their own limit even though the JVM heap looks fine.
--> **Fix** -- always use try-with-resources (`AutoCloseable`) rather than manual `close()` calls that can be skipped by an exception.

## 6) Classloader Leaks

--> If a custom classloader (e.g. an app server redeploying a web app) is kept reachable by something outside its own scope (a thread it started that's still running, a static reference held by a shared/system classloader, a `ThreadLocal` on a thread pool thread outside the app), then EVERY class it loaded -- and the Metaspace memory for all of them -- stays alive too, even after the "old" deployment was supposed to be unloaded. Classic cause of Metaspace growth over repeated redeploys.

# `OutOfMemoryError` Types -- Reading the Message

--> `OutOfMemoryError` is not one error -- the message after the colon tells you WHICH resource was exhausted, and that should directly drive your diagnosis approach.

```text
java.lang.OutOfMemoryError: Java heap space
    --> The heap itself is full and GC couldn't free enough. Classic leak or genuinely undersized heap.

java.lang.OutOfMemoryError: GC overhead limit exceeded
    --> The JVM is spending >98% of CPU time doing GC and reclaiming <2% of the heap each time --
        it gives up rather than let the app appear to hang while doing near-useless repeated collection.

java.lang.OutOfMemoryError: Metaspace
    --> Class metadata region is full -- usually a classloader leak (see above) or -XX:MaxMetaspaceSize set too low.

java.lang.OutOfMemoryError: Unable to create new native thread
    --> The OS refused to create another OS thread for the JVM -- usually too many threads created
        (a thread-per-request leak, or an unbounded thread pool), or an OS-level thread/process limit (ulimit).

java.lang.OutOfMemoryError: Requested array size exceeds VM limit
    --> Code tried to allocate an array larger than the JVM/platform allows -- usually a bug computing a size
        (e.g. a negative-turned-huge value from integer overflow) rather than a true capacity need.

java.lang.StackOverflowError
    --> Technically an Error, not "OutOfMemoryError", and a DIFFERENT memory region (thread stack, not heap) --
        infinite or too-deep recursion. See File 01's Runtime Data Areas section.
```

# Profiling and Diagnostic Tools

## `jconsole` -- Live GUI Monitoring

--> Ships with the JDK. Connects to a running JVM (local or remote via JMX) and shows live graphs of heap usage, thread count, loaded class count, and CPU usage over time. Good first stop for "is memory climbing over time" style questions, and can trigger a manual GC or heap dump from the GUI.

## VisualVM -- Deeper Profiling GUI

--> A more full-featured profiler (separate download since it was decoupled from the JDK) -- adds CPU/memory sampling profilers, thread dump visualization, and heap dump analysis (object counts, retained size, reference chains) in one tool. The natural next step after `jconsole` shows a problem exists but you need to find WHERE.

## `jstack` -- Thread Dumps

--> `jstack <pid>` prints the current stack trace of every thread in the target JVM at that instant -- invaluable for diagnosing deadlocks, thread contention, or threads stuck waiting on something (e.g. a leaking connection pool where every thread is blocked waiting for a connection that never gets returned).
--> Look for threads in `BLOCKED` state waiting on the same lock (deadlock candidate), or many threads stuck in the same stack frame (a bottleneck or exhausted resource).

## `jmap` -- Heap Dumps and Histograms

```text
jmap -histo <pid>              -- live histogram of object counts/bytes per class, printed to console
jmap -dump:live,format=b,file=heap.hprof <pid>   -- full heap dump to a file for offline analysis
```

--> `-histo` is a fast, low-overhead way to check "what class is eating my heap" without a full dump -- often enough to spot an obviously-wrong count (e.g. millions of instances of a class that should have dozens).
--> A full heap dump (`.hprof` file) can be opened in VisualVM, Eclipse MAT (Memory Analyzer Tool), or IntelliJ's built-in profiler to trace exact reference chains keeping specific objects alive -- the "who's holding onto this and why" question that a histogram alone can't answer.

## `-XX:+HeapDumpOnOutOfMemoryError`

--> Production-critical flag -- automatically writes a heap dump the MOMENT an `OutOfMemoryError` is thrown, capturing the exact state that caused the crash, rather than requiring you to reproduce it live (which may be impossible for an intermittent production-only leak). Pair with `-XX:HeapDumpPath=/path/to/dumps` to control where it's written.

## `jstat` -- GC Statistics Over Time

--> `jstat -gcutil <pid> 1000` prints GC statistics (percentage full per generation, GC counts/times) every 1000ms -- useful for watching generational behavior and GC frequency live without the overhead of a full GUI tool.

# A Basic Leak-Hunting Workflow

```text
1. Notice symptom       -- heap usage graph trends upward across multiple GC cycles (not just sawtooth,
                            but the LOW points after each GC keep rising) -- via jconsole/VisualVM/metrics.
2. Narrow with histogram -- jmap -histo <pid>, sorted by bytes -- which class(es) have suspiciously
                            high or ever-growing instance counts?
3. Confirm with heap dump -- jmap -dump or -XX:+HeapDumpOnOutOfMemoryError, then open in
                             VisualVM/Eclipse MAT, and use "Path to GC Roots" on the suspect objects
                             to see exactly what's holding them reachable.
4. Fix the root cause    -- bound the cache / deregister the listener / clear the ThreadLocal /
                             use a static nested class -- whichever pattern from above applies.
5. Verify                -- re-run under load, confirm the post-GC low-water-mark in heap usage
                             stays flat over time instead of climbing.
```

# Gotchas and Best Practices

--> **A sawtooth heap graph is NORMAL, not a leak** -- heap usage rising between GCs and dropping after each one is exactly how generational GC is supposed to look. The red flag is the FLOOR of that sawtooth trending upward over many cycles, not the sawtooth shape itself.
--> **Don't assume the biggest object in a histogram is the leak** -- large legitimate caches, connection pools, or session stores can dominate a histogram without being wrong. Compare against expected/baseline counts, and look at growth over time rather than a single snapshot.
--> **Heap dumps can be huge and slow to capture** -- taking one on a large production heap can itself cause a multi-second pause (it typically forces a Full GC first to get a clean live-object snapshot) -- use judiciously in production, and prefer `-XX:+HeapDumpOnOutOfMemoryError` (automatic, only fires once) over manual dumps on a live struggling system.
--> **Profilers have overhead** -- a profiler attached with deep object-allocation tracking can itself slow the application significantly and even shift GC behavior; prefer lightweight sampling profilers for first-pass investigation, and reserve heavier instrumentation for focused, short investigations.
--> **Reproduce with realistic load** -- many leaks only manifest under sustained load or specific request patterns (e.g. a leak tied to a rarely-hit code path); a quick smoke test may show a perfectly flat heap while the leak is very real under production traffic patterns.
