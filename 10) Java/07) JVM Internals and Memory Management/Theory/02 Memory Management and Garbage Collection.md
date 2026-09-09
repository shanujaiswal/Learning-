# Why Garbage Collection Matters

--> In languages like C, the programmer manually `malloc`s and `free`s memory -- forget a `free` and you leak memory forever; free too early or twice and you corrupt memory or crash. Java's Garbage Collector (GC) automates reclaiming memory for objects that are no longer reachable, eliminating that entire class of manual bugs.
--> That automation isn't free, though -- GC has to find "garbage" objects, which usually means pausing or slowing down the application at unpredictable moments. Understanding HOW the GC works is what lets you reason about latency spikes, tune JVM flags sensibly, and diagnose why a service that "runs fine most of the time" occasionally freezes for a few hundred milliseconds.

# What Makes an Object "Garbage"

--> An object is eligible for collection when it's **unreachable** -- there is no chain of references from a GC ROOT (active thread stacks, static fields, JNI references, currently-executing method local variables) that can reach it anymore.

```java
Object a = new Object();   // reachable -- local variable 'a' is a GC root reference
a = null;                  // the original object is now unreachable (assuming nothing else references it)
                            // -- eligible for collection, but NOT immediately collected;
                            //    it just becomes a CANDIDATE the next time GC runs
```

--> **Reachability, not reference counting** -- Java does NOT use simple reference counting (unlike e.g. Python's primary mechanism). Reachability-based GC correctly handles CYCLES -- two objects that reference each other but that nothing else references are both still garbage, and the JVM's GC identifies that correctly by tracing from roots, whereas naive reference counting would leak them forever (each has a count of 1, pointing at the other).

# The Generational Hypothesis

--> Empirically, most objects die YOUNG -- a huge fraction of allocated objects (temporary variables, short-lived request-scoped data) become garbage almost immediately, while a small fraction survive for the life of the program (caches, singletons, connection pools). This observation, called the **generational hypothesis**, is the foundation of how the JVM heap is structured.

```text
                              HEAP
   +---------------------------------------------------------------+
   |                  YOUNG GENERATION                              |
   |  +-------------------+   +------------+   +------------+       |
   |  |       Eden         |  |  Survivor 0 |  | Survivor 1 |       |
   |  | (new objects born  |  |    (S0)     |  |    (S1)    |       |
   |  |  here)              |  |             |  |            |       |
   |  +-------------------+   +------------+   +------------+       |
   |                                                                  |
   +---------------------------------------------------------------+
   |                    OLD GENERATION (Tenured)                     |
   |         long-lived objects promoted from Young Gen               |
   +---------------------------------------------------------------+

   (Metaspace lives OUTSIDE the heap entirely -- see File 01)
```

## The Young Generation and Minor GC

--> **Eden** -- almost all new objects are allocated here first. Very cheap allocation (typically just a pointer bump).
--> When Eden fills up, a **Minor GC** runs: every object in Eden is checked for reachability. Survivors are copied into one of the two **Survivor spaces** (S0/S1 -- only one is "active"/in-use at any time, the other stays empty as a copy destination); dead objects are simply left behind and Eden is wiped clean in one shot.
--> Each time an object survives a Minor GC, its **age** counter increments. Minor GCs alternate which survivor space is the destination copy target (this copying scheme is why it's sometimes called a "copying collector" for the young generation) -- objects that live long enough (age passes a threshold, tunable via `-XX:MaxTenuringThreshold`) get **promoted** to the Old Generation.
--> Minor GCs are frequent but FAST -- Eden is relatively small, and most objects in it are already dead, so there's little live data to copy.

## The Old Generation and Major/Full GC

--> Holds objects that have survived enough Minor GCs to be promoted, plus any objects allocated directly there if they're large enough to skip Eden (`-XX:PretenureSizeThreshold` on some collectors).
--> Cleaning the Old Generation (a **Major GC**, sometimes conflated with **Full GC**, which typically also includes Metaspace) is much more expensive -- it's a larger region with a higher proportion of LIVE data, so more work is needed to identify survivors and, for compacting collectors, to move them.
--> **This is the pause that actually shows up as a latency spike in production** -- Minor GCs are usually sub-millisecond to a few milliseconds; Major/Full GCs can be tens to hundreds of milliseconds or worse depending on heap size and collector.

# Stop-The-World (STW) Pauses

--> Most GC algorithms need the object graph to hold still while they trace it -- otherwise the application could mutate references mid-scan and the GC would miscount reachability. To guarantee this, the JVM pauses ALL application threads at a "safepoint" for at least part of the GC cycle -- this is a **Stop-The-World** pause.
--> The entire modern history of GC algorithm design is largely a story of shrinking STW pause time -- from "pause for the whole collection" (Serial/Parallel) to "pause only for small, bounded phases while doing most work concurrently" (G1, ZGC, Shenandoah).
--> **STW pauses are why GC tuning is a latency problem, not just a throughput problem** -- a batch job that runs once a day mostly cares about total throughput (how much GC overhead reduces overall work done); a web service or trading system cares acutely about the MAXIMUM pause any single request could observe.

# GC Algorithms

## Serial GC (`-XX:+UseSerialGC`)

--> Uses a single thread for both Minor and Major GC, and stops the world completely for the whole collection.
--> Simplest, lowest overhead for small heaps, but pauses scale with heap size -- unsuitable for large heaps or latency-sensitive multi-core servers.
--> Still the sensible default for small/short-lived processes (e.g. small CLI tools, tiny containers) where minimizing memory/CPU footprint matters more than pause time, since it avoids the coordination overhead of multi-threaded collection.

## Parallel GC (`-XX:+UseParallelGC`) -- the historical Java 8 default

--> Uses MULTIPLE threads to perform GC work faster, but still stops the world for the full collection -- it parallelizes the pause, it doesn't eliminate it.
--> Optimizes for **throughput**: total time NOT spent in GC, across the life of the program. Good fit for batch processing, offline data pipelines -- anything where occasional longer pauses are acceptable in exchange for maximum CPU efficiency between pauses.
--> Tunable throughput/pause-time goals via `-XX:GCTimeRatio` and `-XX:MaxGCPauseMillis` (best-effort hint, not a hard guarantee).

## G1 GC (Garbage-First) (`-XX:+UseG1GC`) -- default since Java 9

--> Divides the heap into many small, equal-sized **regions** (not just one contiguous Eden/Old split) -- each region can independently be Eden, Survivor, or Old at different times.

```text
Traditional:  [ ----- Young ----- ][ --------- Old --------- ]
G1:           [E][O][E][S][O][O][E][O][S][E][O][O][E][S][O] ... (many small regions, mixed roles)
```

--> Concentrates collection effort on the regions with the MOST garbage first (hence "Garbage-First") -- tracks live-data statistics per region and prioritizes collecting the regions that will free the most memory for the least work.
--> Aims to meet a configurable **pause time goal** (`-XX:MaxGCPauseMillis=200` by default) by choosing how many regions to collect in each cycle -- a soft target, not a hard guarantee, but far more predictable than Parallel GC for mixed workloads.
--> Good general-purpose default for most modern server applications with moderate-to-large heaps that want a balance of throughput and pause predictability without manual tuning.

## ZGC (`-XX:+UseZGC`) and Shenandoah -- low-latency collectors

--> Designed for extremely large heaps (multi-GB to TB range) with STW pause targets in the SINGLE-DIGIT MILLISECONDS, regardless of heap size, by doing almost all work (marking, relocating/compacting) CONCURRENTLY with the running application, using colored pointers / load barriers (ZGC) or forwarding pointers (Shenandoah) to let the app keep running correctly while objects are being moved underneath it.
--> Trade-off: higher CPU/memory overhead than G1 for the concurrent bookkeeping, and historically ZGC didn't compact/generational-split as aggressively (Generational ZGC, default-ish by Java 21+, closed much of that gap by applying the generational hypothesis to ZGC too).
--> Use when pause time is the dominant concern and the heap is large -- e.g. large in-memory caches, latency-sensitive services with big working sets. Overkill (and often lower throughput) for small heaps or batch jobs.

## Quick Comparison

```text
Collector    Threads      Pause Style              Best For
Serial       1            Full STW                 Small heaps, single-core, tiny footprints
Parallel     Many         Full STW (parallelized)   Max throughput, batch/offline jobs
G1           Many         Mostly-concurrent,        General purpose default; balanced
                           short STW phases          throughput + predictable pauses
ZGC/Shen.    Many         Concurrent, ms-scale STW  Huge heaps, hard low-latency requirements
```

# GC Tuning Flags -- Practical Reference

```text
-Xms<size>                      Initial heap size (e.g. -Xms512m)
-Xmx<size>                      Maximum heap size (e.g. -Xmx4g)
-Xmn<size>                      Young generation size (Parallel/CMS-era flag; sizes Eden+Survivors)
-XX:+UseG1GC                    Select G1 collector
-XX:+UseZGC                     Select ZGC
-XX:+UseParallelGC              Select Parallel collector
-XX:+UseSerialGC                Select Serial collector
-XX:MaxGCPauseMillis=<ms>       Soft pause-time goal (G1/others) -- a target, not a guarantee
-XX:NewRatio=<n>                Old:Young size ratio (e.g. 2 means Old is 2x Young)
-XX:SurvivorRatio=<n>           Eden:Survivor size ratio within Young gen
-XX:MaxTenuringThreshold=<n>    How many Minor GC survivals before promotion to Old
-XX:+PrintGCDetails             (older JVMs) verbose GC logging
-Xlog:gc*                       (Java 9+ unified logging) verbose GC logging
-XX:+HeapDumpOnOutOfMemoryError Auto-dump the heap when OOM occurs (covered in File 03)
-XX:MaxMetaspaceSize=<size>     Cap Metaspace growth (class metadata, off-heap)
```

--> **`-Xms` == `-Xmx` is a common production recommendation** -- setting the initial and max heap size equal avoids the JVM spending time repeatedly resizing the heap at runtime as demand grows, at the cost of committing that memory upfront even if unused.
--> **Sizing the heap too large can backfire** -- a bigger heap means Major/Full GC has more live data to potentially scan and compact, which can mean LONGER pauses when they do happen, even though they happen less often. Sizing is a genuine trade-off, not "bigger is always better."

# Reference Types and GC Interaction

--> Beyond normal ("strong") references, `java.lang.ref` provides reference types that behave differently under GC pressure -- useful for building caches and avoiding certain leak patterns:

```text
Strong reference     -- normal `Object o = new Object();` -- NEVER collected while reachable.
SoftReference<T>      -- collected only when the JVM is genuinely low on memory (good for memory-sensitive caches).
WeakReference<T>      -- collected at the NEXT GC cycle if no strong references remain (used by WeakHashMap,
                          and to avoid listener/callback leaks -- see File 03).
PhantomReference<T>   -- `.get()` always returns null; used only to get a notification AFTER an object has
                          been finalized/collected, for cleanup scheduling (modern code uses `Cleaner` instead
                          of the deprecated `finalize()`).
```

# Gotchas and Best Practices

--> **`System.gc()` is a suggestion, not a command** -- it hints to the JVM that now might be a good time for a Full GC, but the JVM is free to ignore it entirely. Calling it manually in application code is almost always a code smell (masking a real leak, or a misguided attempt at "cleanup") and can actively hurt performance by forcing unnecessary Full GC pauses. `-XX:+DisableExplicitGC` exists specifically to let ops teams neutralize careless `System.gc()` calls in third-party code.
--> **GC pause time and GC throughput are usually in tension** -- optimizing hard for one often costs the other; pick the collector and tuning goal that matches what the application actually needs (a batch ETL job and a trading system have very different priorities), rather than chasing both simultaneously.
--> **Don't tune blindly** -- always tune against real GC logs (`-Xlog:gc*`) and a realistic workload/heap size, not synthetic microbenchmarks or guesses. File 05 covers monitoring GC behavior in production in more depth.
--> **A high allocation rate matters more than most people expect** -- even short-lived objects aren't "free" just because Minor GC is fast; extremely high allocation rates (e.g. excessive autoboxing, unnecessary intermediate object creation in hot loops) increase Minor GC frequency and can still show up as a measurable throughput cost.
--> **Promotion failure / premature promotion** -- if the Young generation is too small relative to the allocation rate, objects get promoted to Old before they'd naturally die, filling up the Old generation faster and triggering more frequent, expensive Major GCs. This is a common root cause when GC tuning is needed at all.
