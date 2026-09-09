# Why ZGC Exists

--> ZGC (Z Garbage Collector, `-XX:+UseZGC`) was designed with one primary goal: keep STW pause times below roughly a millisecond, no matter how large the heap is -- from a few hundred MB up to multiple TERABYTES. That's a fundamentally different design target than G1, which scales pause time WITH heap and live-set size even though it bounds it via a soft goal.
--> Introduced experimentally in JDK 11, production-ready by JDK 15, and significantly reworked with **Generational ZGC** becoming the default ZGC mode in JDK 21+. If pause time is the dominant requirement and the previous generation's G1 tuning can't get you low enough, ZGC is the next step up.
--> The core trick that makes near-total concurrency possible: instead of doing marking/relocation work during a STW pause, ZGC does almost ALL of it concurrently with the application, using clever pointer encoding and a barrier the application itself cooperates with on every load -- rather than relying on the GC alone to track everything via write barriers like G1's SATB scheme.

# Colored Pointers

--> ZGC's central technique. On 64-bit systems, object references are 64 bits wide, but an actual memory address only needs 42-48 bits. ZGC repurposes some of the UNUSED high-order bits of every reference as **metadata bits directly embedded in the pointer itself** -- hence "colored" pointers.

```text
A colored pointer (conceptually):

  63........48 47....44 43......................0
  [ unused ]   [colors] [       object address     ]

  Color bits track (varies by JDK version, roughly):
    Finalizable  -- reachable only through a finalizer
    Remapped     -- pointer already points at the object's CURRENT location
    Marked0/1    -- alternating marking-cycle bits (which GC cycle marked this)
```

--> Because the color bits live INSIDE the reference itself, the GC can tell a huge amount about an object -- whether it's been marked in the current cycle, whether it's been relocated already -- just by inspecting the pointer value, WITHOUT touching the object or any separate side-table. This is what allows marking and relocation bookkeeping to happen without stopping every thread to synchronize on shared external metadata.
--> Different reference values (different color bits) can point at the SAME underlying object across its lifetime -- a "stale" colored pointer (from before a relocation) and a "fresh" one (after) both decode to a valid address, but the load barrier (below) is what reconciles a stale one into the fresh one on next access.
--> **Trade-off**: reserving bits for colors limits addressable heap size on a given colored-pointer scheme, and it means ZGC needs specific OS/CPU support for the multi-mapping trick that backs this (see Concurrent Everything below). It's also part of why ZGC historically had higher constant-factor memory/CPU overhead than G1.

# Load Barriers

--> A **load barrier** is a small piece of code the JIT inserts on every read of an object reference from the heap (a "load" of a reference-typed field, essentially every `obj.field` dereference where `field` is a reference). ZGC's load barrier checks the color bits of the pointer just loaded.
--> If the color bits indicate the pointer is "good" (already remapped to the object's current location, already marked if a marking cycle is active), the barrier does nothing extra -- the fast path is just a few extra instructions, cheap enough to be barely measurable.
--> If the color bits indicate the pointer is "bad" (stale -- pointing at a pre-relocation address, or not yet marked during an active marking cycle), the barrier triggers a **slow path**: it resolves the object's actual current location, fixes up the pointer's color bits, and importantly, HEALS the reference in memory it was just loaded from (self-healing pointers) -- so the next time anything reads that same field, it gets the already-fixed, "good" pointer and takes the fast path.

```text
Application thread reads obj.field:
        |
        v
   [ load barrier check color bits ]
        |
   good?  ------ yes -----> return pointer, continue (fast path, ~free)
        |
        no
        |
        v
   slow path: resolve real location, fix color bits,
   "heal" (rewrite) the field in-place with the corrected pointer
        |
        v
   return corrected pointer, continue
```

--> This is the mechanism that lets ZGC RELOCATE (move/compact) live objects WHILE the application keeps running and keeps reading references to them -- something G1 can only do during a STW evacuation pause. The application effectively helps the GC "fix up" pointers lazily, on demand, spread out over the whole concurrent phase instead of all at once in a pause.
--> **Only reference LOADS need the barrier** (not stores) in ZGC's design -- this asymmetry is deliberate, since loads vastly outnumber stores in most workloads' hot paths, and keeping the barrier logic in the (cheap, common) load path rather than needing heavier bookkeeping on every store is part of what keeps overhead low.

# Why ZGC Achieves Sub-Millisecond Pauses

--> Nearly every phase that would traditionally require a STW pause is redesigned to run concurrently instead, using colored pointers + load barriers as the enabling mechanism:

```text
ZGC cycle phases (heavily simplified):

1. Pause Mark Start        (STW, sub-ms) -- flip marking color, snapshot roots
2. Concurrent Mark          (Concurrent)  -- trace live objects, app runs normally
3. Pause Mark End           (STW, sub-ms) -- finalize marking, handle edge cases
4. Concurrent Process        (Concurrent)  -- non-strong (weak/soft/phantom) refs,
   Non-Strong References                   class unloading prep
5. Concurrent Relocate      (Concurrent)  -- COPY live objects to new locations;
                                             load barriers heal stale pointers
                                             as the app touches them
6. Pause Relocate Start     (STW, sub-ms) -- flip remap color, snapshot roots
   (only in some cycle variants)
```

--> The STW phases that remain (`Pause Mark Start`, `Pause Mark End`, and a brief relocate-start pause) do a fixed, small, ROOT-SET-sized amount of work -- scanning thread stacks and other roots -- which does NOT grow with heap size or live-set size. That's the structural reason ZGC's pause time stays flat and sub-millisecond even as the heap scales into the terabytes, unlike G1 where STW evacuation pause time scales with how many regions/how much live data is being copied in that pause.
--> **Concurrent relocation is the hard part other collectors avoid doing concurrently** -- moving an object while other threads might be reading/writing it mid-move is inherently tricky; ZGC's colored-pointer + self-healing-barrier scheme is specifically what makes this safe without a pause, because any thread that loads a stale reference gets redirected to the correct, current location transparently.

# Concurrent Everything

--> ZGC is described as doing marking, reference processing, relocation, AND (for Generational ZGC) even remembered-set-style tracking almost entirely concurrently -- the STW portions are deliberately kept to fixed, tiny root-scanning pauses only.
--> This requires OS-level support: ZGC relies on **multi-mapping** -- the same physical memory can be mapped at multiple different virtual addresses simultaneously, which is how it can present the "same" object at what looks like different addresses to satisfy the colored-pointer scheme without extra copies. This is why ZGC has specific OS/platform requirements (mainstream on 64-bit Linux, Windows, macOS in modern JDKs, but historically lagged Linux support).
--> **Concurrent class unloading and stack processing** round out the "everything" -- even class-metadata cleanup, which many older collectors handle only during a Full GC, is largely concurrent in ZGC.

# Generational ZGC (JDK 21+ default)

--> Original (non-generational) ZGC treated the whole heap as one undifferentiated space -- every concurrent cycle re-marked and potentially relocated EVERY live object, regardless of age. That ignores the generational hypothesis entirely and means ZGC did strictly more marking/relocation work per cycle than a generational collector needs to, especially wasteful given how many objects die young.
--> **Generational ZGC** (`-XX:+UseZGC` with generational mode; became the default ZGC behavior in JDK 21, and the ONLY mode from JDK 23 onward as the non-generational mode was deprecated/removed) splits the heap into a young and old generation, same hypothesis as every other JVM collector, but keeps ZGC's core concurrent-everything, colored-pointer, sub-millisecond-pause machinery for BOTH generations.
--> Effect: young-generation cycles (which happen far more often) now only need to trace and relocate the much smaller set of young objects, dramatically reducing the total CPU/memory bandwidth ZGC spends per unit of garbage collected, closing much of the throughput gap that used to exist between ZGC and G1 -- while keeping the same sub-millisecond pause guarantee.
--> If you're on JDK 21+, enabling ZGC gives you generational ZGC by default -- there's no real reason to reach for the old single-generation mode.

# Heap Size Considerations

--> ZGC's pause times are essentially heap-size-INDEPENDENT (unlike G1, where bigger live sets can mean longer, though still bounded, pauses) -- this is the entire point of the design, and it's ZGC's headline advantage on very large heaps.
--> That said, ZGC is NOT free on small heaps -- the concurrent bookkeeping (extra CPU threads doing marking/relocation work alongside the app, load barrier overhead on every reference read, multi-mapping memory overhead) has a real constant cost that's easier to justify when the heap and workload are large enough to amortize it. For a small heap that's already fast under G1 (or even Parallel), ZGC's overhead can make it a net throughput loss for no practical pause-time benefit.
--> ZGC generally wants MORE headroom than G1 for the same live-data-set, because concurrent relocation needs somewhere to copy objects to while the application keeps allocating simultaneously -- undersized heaps can cause ZGC to slow down the application deliberately (allocation stalls) to avoid running out of memory mid-cycle, which shows up as latency even without a classic "GC pause."
--> Rule of thumb: ZGC shines from a few GB up through multi-TB heaps where predictable sub-ms pauses matter more than squeezing maximum throughput or minimum memory footprint out of a small heap.

# Key Tuning Flags

```text
-XX:+UseZGC                       Select ZGC
-Xmx<size>                        Max heap -- size generously; ZGC wants room to
                                   relocate concurrently without stalling allocation
-XX:ConcGCThreads=<n>             Number of concurrent GC worker threads (raise on
                                   high-core-count machines under heavy allocation)
-XX:ZAllocationSpikeTolerance=<n> How much allocation-rate spike headroom ZGC plans for
-XX:SoftMaxHeapSize=<size>        A soft cap below -Xmx ZGC tries to stay under
                                   (lets it use more heap only when truly needed)
-Xlog:gc*                         Unified GC logging -- ZGC log format differs
                                   from G1's; look for phase timings and allocation
                                   stall counts specifically
```

--> **`-Xmx` sizing matters more for ZGC than for most other collectors** -- because pause time doesn't scale with heap size, there's little downside to sizing generously; the real risk of an undersized heap with ZGC is allocation stalls, not longer pauses.

# Gotchas and Best Practices

--> **ZGC trades some throughput and memory/CPU overhead for pause-time consistency** -- don't reach for it by default; it earns its keep specifically when a measured G1 pause profile (from real GC logs) is breaching a hard latency requirement that G1 tuning can't fix.
--> **Watch for allocation stalls in the logs, not just pause counts** -- with ZGC, an undersized heap manifests as the application being deliberately throttled to let concurrent relocation catch up, which is a different failure mode than a classic STW pause spike but has the same user-facing symptom (latency).
--> **Confirm generational mode on JDK 21+** -- if migrating from a pinned older-JDK single-generation ZGC configuration, revisit any flags that assumed non-generational behavior; generational ZGC's tuning surface (young-gen sizing especially) behaves more like G1's.
--> **Platform support and JDK version matter** -- ZGC's feature set (generational mode, full concurrent class unloading, multi-mapping support) has evolved significantly release over release; always check what's actually available/default in the specific JDK version being deployed rather than assuming.
--> **Don't compare ZGC and G1 pause numbers out of context** -- G1's 200ms goal and ZGC's sub-ms pauses aren't measuring the same thing end-to-end; always evaluate actual application-observed latency (including any allocation-stall time) under realistic load, not just the STW pause metric in isolation.
