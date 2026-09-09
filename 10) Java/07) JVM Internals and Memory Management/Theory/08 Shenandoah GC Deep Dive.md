# Why Shenandoah Exists

--> Shenandoah is Red Hat's low-pause-time garbage collector (`-XX:+UseShenandoahGC`), developed in parallel with (and as a rival design to) Oracle's ZGC, with the same headline goal: STW pause times that don't scale with heap size, achieved by moving marking AND compaction/relocation work off the STW critical path and into concurrent phases.
--> Available since JDK 12 (as an experimental feature in OpenJDK builds that include it -- notably it was NOT bundled in Oracle's own JDK builds for a long time, but is available in Red Hat's, AdoptOpenJDK/Eclipse Temurin, and other OpenJDK distributions), production-quality for several releases now.
--> Where ZGC solves "concurrent relocation without stopping the world" using colored pointers embedded in the reference itself plus a load barrier, Shenandoah solves the SAME problem using a different, older, more classical technique: an indirection pointer stored WITH each object, known as a **Brooks pointer** (also called a forwarding pointer).

# Brooks Pointers

--> The core idea, proposed by Rodney Brooks in 1984, long predating Shenandoah: give every object an extra word (the "Brooks pointer" / forwarding pointer) stored right before or alongside its header, which normally just points to ITSELF.

```text
Normal state (object not being relocated):

  [Brooks ptr] --> points to --> [ THIS OBJECT'S OWN HEADER/DATA ]
       |
       +-- object header, fields, etc. follow

During/after relocation (object has been copied elsewhere):

  OLD copy:
  [Brooks ptr] ------------------> [ NEW copy's header/data ]
       (stale memory, but forwarding
        pointer redirects any reader)

  NEW copy:
  [Brooks ptr] --> points to itself (the new copy is now "home")
       |
       +-- object header, fields, etc. (the actual live data)
```

--> Every access to an object goes through an indirection: read the Brooks pointer first, then follow it to find where the object's real data currently lives. In the common case (object hasn't moved, or has already been fully relocated and everyone's been redirected) this indirection resolves to the object itself and costs one extra pointer dereference -- small but nonzero, unlike ZGC's fast path which is closer to free when the color bits are already "good."
--> When Shenandoah relocates (evacuates) a live object during a concurrent phase, it copies the object to a new location and then updates the Brooks pointer in the OLD copy to point at the NEW copy. Any thread still holding a reference to the old location transparently gets redirected to the new location via that one extra indirection -- the application never sees a dangling or incorrect reference, and doesn't need to pause while every reference to that object across the whole heap gets rewritten.
--> Later, a subsequent phase updates the actual REFERENCES throughout the heap to point directly at the new location (so the indirection can eventually be dropped for that object) -- but critically, this reference-updating work is also done concurrently, spread out rather than requiring a synchronized STW pass.

# Shenandoah's Concurrent Cycle

```text
1. Init Mark              (STW, short) -- snapshot roots, start marking
2. Concurrent Marking      (Concurrent) -- trace live object graph, app runs
3. Final Mark              (STW, short) -- finish marking, select regions to evacuate,
                                            prepare for concurrent evacuation
4. Concurrent Evacuation   (Concurrent) -- COPY live objects out of selected regions
                                            into fresh regions; Brooks pointers keep
                                            concurrent readers correctly redirected
5. Init Update References  (STW, short) -- prepare for the reference-update pass
6. Concurrent Update        (Concurrent) -- walk the heap updating references to point
   References                             directly at evacuated objects' new locations
7. Final Update References (STW, short) -- finish updating roots, reclaim old regions
```

--> Like ZGC, the STW phases here are deliberately kept small and largely independent of live-set size (root scanning and a bit of coordination), while the expensive parts -- tracing the whole graph and copying/updating live objects -- happen concurrently with the application running.
--> **Concurrent evacuation is Shenandoah's signature capability**, same as ZGC's -- both collectors solve the historically hard problem of "how do you MOVE an object while other threads might read or write it mid-move" without pausing the world; they just use different indirection mechanisms to do it (embedded colored bits + load barrier vs. an extra forwarding-pointer word + read/write barrier checks).

# Comparison with ZGC's Approach

```text
                    ZGC                          Shenandoah
Indirection         Encoded in the reference      Extra "Brooks pointer" word
mechanism           itself (colored pointer        stored with each object
                    bits in unused address bits)

Barrier             Load barrier only (checks      Load AND store barriers
                    color bits on reference reads)  historically used (varies
                                                     by Shenandoah barrier mode/
                                                     JDK version -- newer modes
                                                     reduce barrier scope)

Per-object          None extra -- reuses spare      One extra word per object
memory overhead     bits in the 64-bit pointer      (the Brooks/forwarding pointer)

Heap addressing     Reserves pointer bits for       No special pointer encoding
                    color metadata (implications    needed -- more "conventional"
                    for max addressable heap,       memory layout
                    though not a practical limit
                    at today's heap sizes)

Generational        Default/only mode from          Generational mode has been
support             JDK 23+ (opt-in from 21)        in active development;
                                                     historically Shenandoah was
                                                     primarily single-generation,
                                                     generational support arriving
                                                     later than ZGC's

Platform/JDK        Built into OpenJDK/Oracle        Distributed via OpenJDK builds
distribution        JDK mainline                    that include it (Red Hat builds,
                                                     Eclipse Temurin, etc.) -- historically
                                                     excluded from some Oracle JDK builds

Origin              Oracle                          Red Hat
```

--> Neither approach is strictly "better" in the abstract -- they're different engineering answers to the same problem. In practice, both deliver similar sub-to-low-double-digit-millisecond pause characteristics; the practical decision between them usually comes down to JDK DISTRIBUTION availability (is Shenandoah even built into the JDK binary you're deploying?), platform support, and how mature generational support is in the JDK version you're targeting, more than a fundamental technical superiority of one indirection scheme over the other.
--> **Shenandoah's forwarding-pointer approach is conceptually closer to classical copying-collector forwarding pointers** (the same idea used briefly during any copying GC's evacuation, including G1's) -- Shenandoah's innovation is making that forwarding persist and remain concurrently-safe across an entire evacuation phase instead of only being valid momentarily during a STW copy.

# Concurrent Evacuation in Practice

--> During Concurrent Evacuation, Shenandoah selects the regions with the most reclaimable garbage (a "garbage-first"-flavored heuristic, similar in spirit to G1's region prioritization) and copies their live objects to fresh regions while the application keeps running.
--> Read AND write barriers cooperate to keep this safe: a read barrier ensures any thread following a reference to an object mid-evacuation gets redirected via the Brooks pointer to wherever the current valid copy lives; a write barrier ensures a MUTATION to an object that's being concurrently evacuated is correctly applied to (or synchronized with) the copy that will become authoritative, so no update is lost mid-copy.
--> Shenandoah has iterated through different barrier implementation strategies across releases (e.g. a simpler "traversal" mode in earlier versions vs. the more refined concurrent marking/evacuation split used in later ones) aimed at reducing the per-access overhead these barriers impose -- worth checking release notes for the specific JDK version in use, since Shenandoah's internals have evolved meaningfully release over release, more so than G1's core algorithm has.

# Key Tuning Flags

```text
-XX:+UseShenandoahGC                 Select Shenandoah (requires a JDK build that includes it)
-Xmx<size>                           Max heap -- like ZGC, size generously; concurrent
                                      evacuation needs headroom to copy into while the
                                      app keeps allocating
-XX:ShenandoahGCHeuristics=<mode>    Collection heuristic: adaptive (default, learns from
                                      history), static, compact (favors memory footprint
                                      over pause time), aggressive (mostly for testing)
-XX:ConcGCThreads=<n>                Concurrent worker thread count
-XX:ShenandoahGarbageThreshold=<n>   Garbage % threshold for a region to become an
                                      evacuation candidate
-Xlog:gc*                            Unified GC logging -- inspect phase timings the
                                      same way as G1/ZGC logs
```

# Gotchas and Best Practices

--> **Verify Shenandoah is actually present in your JDK distribution before planning around it** -- unlike G1 and (in modern JDKs) ZGC, Shenandoah's availability has historically been distribution-dependent; passing `-XX:+UseShenandoahGC` on a build that doesn't include it fails to start rather than silently falling back.
--> **Per-object Brooks-pointer overhead is small but real** -- for extremely allocation-heavy, small-object-heavy workloads, the extra word per object and the read/write barrier cost can matter more than it would under G1's write-barrier-only (SATB) scheme; as always, measure with real GC logs and real workload rather than assuming.
--> **Concurrent evacuation, like ZGC's, wants a heap with enough headroom** -- an undersized heap forces Shenandoah into more conservative, less concurrent behavior, or outright degrades into more frequent, less effective cycles; don't undersize a Shenandoah heap the way you might get away with for G1.
--> **The `adaptive` heuristic is a sensible default and rarely needs manual override** -- reach for `static`/`compact`/`aggressive` heuristics only for specific, measured reasons (e.g. `compact` explicitly trades some pause-time consistency for a smaller memory footprint, useful in memory-constrained containers).
--> **Generational Shenandoah maturity should be checked against the specific JDK version** -- if generational behavior is a hard requirement, confirm current support level rather than assuming parity with Generational ZGC, since the two projects' generational work has proceeded on different timelines.
--> **Choosing between ZGC and Shenandoah is rarely a "which is technically superior" question in practice** -- for most teams it comes down to which is available/well-tested in their JDK distribution of choice, existing operational familiarity, and confirming actual measured pause/throughput numbers against the SPECIFIC workload, since both target the same low-pause niche.
