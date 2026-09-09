# Why G1 Exists

--> Before G1, the JVM's main options were Serial, Parallel, and CMS (Concurrent Mark Sweep). CMS reduced pause times by doing marking concurrently, but it never compacted the Old Generation -- it just tracked free lists, which meant fragmentation could eventually force a fallback to a full, uncompacted STW collection (the dreaded "concurrent mode failure"). G1 was built from the ground up to fix that: concurrent marking AND compaction, with predictable, tunable pause times.
--> G1 (Garbage-First) has been the **default collector since Java 9**, replacing Parallel GC as the out-of-the-box choice. If you don't pass a `-XX:+Use...GC` flag on a modern JDK, you're running G1.
--> The core idea: instead of treating the heap as a small number of large, contiguous generations, break it into many small **regions**, track how much garbage is in each one, and always collect the regions with the most garbage first -- hence the name.

# Region-Based Heap Layout

--> G1 divides the entire heap into a large number (typically 2048, but scales with heap size) of equal-sized **regions**, each between 1MB and 32MB (power-of-two sized, chosen automatically based on `-Xmx`, or set explicitly with `-XX:G1HeapRegionSize`).
--> Unlike the traditional fixed Eden/Survivor/Old split, a region's ROLE is not fixed in advance -- any region can be Eden, Survivor, Old, or (for very large objects) Humongous at different points in time. The generational concept still exists logically (G1 is still a generational collector, honoring the generational hypothesis), it's just implemented as a flexible SET of regions rather than a fixed contiguous block.

```text
Traditional generational layout (Parallel/CMS):
   [ --------- Young --------- ][ ------------- Old ------------- ]
   (fixed contiguous blocks, sizes change only via explicit resize)

G1 layout:
   [E][O][E][S][O][O][E][O][H][H][S][E][O][O][E][S][O][ free ][ free ]
   (many small regions; role assigned/reassigned per-region over time)

   E = Eden     S = Survivor     O = Old     H = Humongous (part of a chain)
```

--> **Humongous regions** -- any object larger than 50% of a region size is classified "humongous" and allocated directly into one or more contiguous humongous regions, bypassing Eden entirely. A single humongous object spanning multiple regions is a common source of fragmentation and premature Old-gen pressure if the application allocates large arrays/buffers frequently -- worth watching for in GC logs (`Humongous Allocation` entries).
--> **Remembered Sets (RSets)** -- because regions are scattered non-contiguously and can be collected independently, G1 needs to know, for each region, which OTHER regions hold references INTO it (so it can find roots into a region without scanning the entire heap). Each region maintains a Remembered Set -- essentially a per-region "who points at me" index -- updated incrementally via write barriers as the application mutates references. This is what makes it possible to collect a handful of regions without stopping to scan the whole heap.
--> **Card Table** -- underlying the RSet mechanism, the heap is further divided into small fixed-size "cards" (512 bytes); a write barrier marks a card "dirty" whenever a reference inside it is modified, so GC only needs to rescan dirty cards rather than the whole region.

# The G1 Concurrent Marking Cycle

--> G1's headline feature is that most of the work needed to find garbage happens CONCURRENTLY with the running application -- only small, bounded phases actually stop the world. The full cycle looks like this:

```text
1. Initial Mark        (STW -- piggybacks on a normal Young GC pause)
        |
        v
2. Root Region Scanning (Concurrent -- scans survivor regions as roots)
        |
        v
3. Concurrent Marking   (Concurrent -- traces the whole object graph,
        |                app threads keep running via SATB write barriers)
        v
4. Remark               (STW -- short, finalizes marking, processes
        |                SATB buffers, reference processing)
        v
5. Cleanup              (mostly STW -- reclaims empty regions immediately,
        |                computes liveness stats per region for the
        v                next mixed collection cycle)
6. (later) Mixed Collections -- copying/compacting live objects out of
                                the regions selected as most profitable
```

--> **1. Initial Mark** -- a STW pause that marks all objects directly reachable from GC roots. This phase is deliberately piggybacked onto an already-scheduled Young GC, so it doesn't add a separate pause of its own in the common case.
--> **2. Root Region Scanning** -- concurrent scan of survivor regions (since they may contain references into the Old generation that the marking phase needs as starting points). Must complete before the next Young GC can start.
--> **3. Concurrent Marking** -- the big one: traces the entire live object graph across the whole heap, concurrently with application threads. Uses a **Snapshot-At-The-Beginning (SATB)** algorithm -- a write barrier records the PREVIOUS value of any reference field the application overwrites during this phase, so objects that were reachable at the start of marking are guaranteed not to be missed even if the app concurrently unlinks them. Per-region live-data statistics are accumulated during this pass.
--> **4. Remark** -- a short STW pause to finish off marking: drains any remaining SATB buffers, handles weak references (finalizing what's actually unreachable), and finalizes per-region liveness data. Usually much shorter than Initial Mark.
--> **5. Cleanup** -- identifies regions that are 100% garbage (no live objects at all) and reclaims them immediately without needing to evacuate anything -- essentially free memory recovery. Also sorts all Old regions by liveness ("Garbage-First" ordering) to build the priority list mixed collections will draw from next.
--> **Concurrent marking does NOT itself reclaim Old generation garbage** -- it only IDENTIFIES which regions are worth collecting and how much garbage they hold. Actual reclamation of Old-region garbage happens afterward, during Mixed Collections.

# Evacuation Pauses: Young-Only and Mixed Collections

--> G1's actual reclamation mechanism is **evacuation** -- live objects are copied (not swept-in-place) out of the regions being collected into fresh empty regions, and the source regions are then reclaimed wholesale. This is what gives G1 automatic compaction -- unlike CMS, there's no fragmentation buildup to eventually trigger a full GC.

```text
Young-Only GC (Minor-GC equivalent):
   Collects ONLY Eden + Survivor regions.
   Live objects copied to new Survivor regions (or promoted to Old
   if age threshold reached, or region-copying heuristics decide so).
   Runs whenever Eden fills up. STW, but short -- bounded by the
   pause time goal.

Mixed GC (runs periodically AFTER a completed marking cycle):
   Collects ALL Young regions PLUS a SELECTED SUBSET of Old regions
   -- specifically the Old regions with the most garbage, as
   identified during the Cleanup phase.
   STW, evacuates live data from chosen regions into fresh regions.
   Runs repeatedly (multiple Mixed GCs per marking cycle) until the
   candidate Old regions are worked through or the reclaimable-
   percentage threshold (-XX:G1MixedGCLiveThresholdPercent /
   -XX:G1HeapWastePercent) says it's no longer worth continuing.
```

--> **This is the "Garbage-First" payoff** -- rather than collecting the entire Old generation in one massive Full-GC-style pause (like Parallel GC does), G1 collects only the few Old regions that are mostly garbage, a handful at a time, spread across several Mixed GC pauses -- each individually short and bounded.
--> **G1 still has a Full GC as a fallback** -- if the application allocates faster than G1 can evacuate (concurrent marking or evacuation can't keep pace, "to-space exhausted" / evacuation failure), G1 falls back to a full, single-threaded-ish, STW compacting collection of the ENTIRE heap. This is by far the worst-case pause G1 can produce, and its presence in GC logs is the #1 sign that G1 needs more heap, more concurrent GC threads, or an adjusted pause goal.

# Pause Time Goals: `-XX:MaxGCPauseMillis`

--> G1's most distinctive tuning knob is that you specify a PAUSE TIME GOAL rather than sizing generations directly: `-XX:MaxGCPauseMillis=200` (the default) tells G1 "try to keep each STW pause under 200ms."
--> **It's a soft target, not a hard guarantee.** G1 uses it to decide, at the start of each collection pause, how MANY regions to include -- fewer regions per pause means shorter pauses but more frequent ones (and, for Mixed GCs, more collections needed to work through the Old-gen garbage backlog). G1 continuously measures actual pause durations and adapts region-count choices using this feedback loop.
--> Setting the goal too low relative to allocation rate and live-set size doesn't get you faster pauses for free -- it forces G1 into smaller, more frequent evacuations, which can mean it can't keep up with garbage production (more Full GC fallbacks) or reduces overall throughput as more CPU goes to GC bookkeeping relative to app work.
--> **Young generation sizing is DERIVED from the pause goal in G1**, not set directly (unlike `-Xmn` in Parallel GC) -- G1 dynamically resizes how many regions are Eden based on what it estimates it can evacuate within the pause goal. You CAN bound it manually with `-XX:G1NewSizePercent` / `-XX:G1MaxNewSizePercent`, but doing so fights G1's adaptive sizing and is rarely a good idea unless you have a very specific reason.

# Key Tuning Flags

```text
-XX:+UseG1GC                          Select G1 (default on modern JDKs, so usually unnecessary)
-XX:MaxGCPauseMillis=<ms>             Soft pause-time goal (default 200)
-XX:G1HeapRegionSize=<size>           Force a specific region size (1m-32m, power of 2)
-XX:G1NewSizePercent=<n>              Minimum % of heap for Young gen (default 5)
-XX:G1MaxNewSizePercent=<n>           Maximum % of heap for Young gen (default 60)
-XX:InitiatingHeapOccupancyPercent    (IHOP) Old-gen occupancy % that triggers a new
    =<n>                             concurrent marking cycle to start (default 45)
-XX:ConcGCThreads=<n>                 Threads used for concurrent marking work
-XX:ParallelGCThreads=<n>             Threads used during STW pause phases
-XX:G1MixedGCCountTarget=<n>          Target number of Mixed GCs to spread Old-gen
                                       reclamation across (default 8)
-XX:G1HeapWastePercent=<n>            Stop Mixed GCs once reclaimable garbage drops
                                       below this % (default 5) -- avoids diminishing returns
-XX:G1MixedGCLiveThresholdPercent=<n> Old regions above this liveness % are excluded
                                       from Mixed GC candidacy (not worth evacuating)
-Xlog:gc*                             Unified GC logging -- essential for real tuning
```

--> **`InitiatingHeapOccupancyPercent` (IHOP) is one of the most impactful and least obvious flags** -- it decides how early G1 starts the concurrent marking cycle, based on Old-gen occupancy after a Young GC. Set it too high and marking starts too late, risking evacuation failure/Full GC because the marking cycle didn't finish before Old-gen fills up; set it too low and you pay for more frequent marking cycles than necessary. Modern JDKs also support **Adaptive IHOP** (on by default) which learns from past cycles instead of using a fixed percentage -- usually better than hand-tuning this.

# When G1 Is the Right Default

--> G1 is the right choice for the vast majority of server-side Java applications: web services, microservices, application servers, most databases -- anything with heap sizes from roughly 4GB up to a few hundred GB, that wants a reasonable balance of throughput and pause predictability WITHOUT hand-tuning generation sizes.
--> Prefer G1 over Parallel GC when pause time predictability matters at all (basically always for anything user-facing) -- the throughput cost versus Parallel is usually small (single-digit percent) for a large reduction in worst-case pause.
--> Prefer G1 over ZGC/Shenandoah when: heap is small-to-moderate (well under ~32GB, where G1's STW phases are naturally short anyway), pause targets in the tens-of-milliseconds range are acceptable (not sub-millisecond), or you want the most battle-tested, default, "just works" option with the largest amount of production experience and tooling behind it.
--> Consider moving OFF G1 toward ZGC/Shenandoah when: heap is very large (100s of GB+), pause time requirements are in the single-digit-millisecond range regardless of heap size (trading systems, real-time bidding, anything with hard SLA pause budgets), or GC logs show G1 Mixed/Full GC pauses regularly breaching your latency budget even after IHOP/pause-goal tuning.

# Gotchas and Best Practices

--> **A low `MaxGCPauseMillis` is a request, not a lever you can crank arbitrarily** -- pushing it very low (e.g. 10ms) on a large heap with a high allocation rate typically just causes G1 to fall behind and fall back to Full GC, which is a MUCH worse pause than accepting a slightly higher steady-state goal. Tune it to something realistic for the heap size and allocation rate, verified against actual GC logs.
--> **Watch for "to-space exhausted" / evacuation failure in logs** -- this means G1 ran out of space to copy survivors into during a pause, and it's a strong signal to either increase heap size, increase `-XX:G1ReservePercent` (heap held back specifically as evacuation headroom, default 10%), or investigate why the live-data-set is larger than expected.
--> **Humongous object churn is an easy blind spot** -- an application that allocates many arrays/buffers just over the 50%-of-region-size threshold will hammer humongous-region allocation/reclamation and put unusual pressure on Old-gen occupancy (humongous objects are allocated directly as effectively-Old). Increasing `G1HeapRegionSize` can push more of those allocations back under the humongous threshold.
--> **Don't manually pin Young generation size in G1 unless you've measured a specific problem** -- it defeats G1's adaptive sizing, which is usually smarter than a fixed guess across varying load.
--> **Mixed GC count and heap waste percent trade off reclamation speed against pause frequency** -- if Old-gen garbage isn't being reclaimed fast enough (occupancy keeps climbing across many Mixed GCs), lowering `G1MixedGCCountTarget` or `G1HeapWastePercent` makes G1 more aggressive about continuing to collect Old regions, at the cost of more STW pauses in the short term.
