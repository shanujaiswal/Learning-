# Framing the Decision

--> There is no universally "best" collector -- every GC design trades off some combination of THROUGHPUT (total CPU time NOT spent on GC), LATENCY/pause time (how long any single pause can block the application), and FOOTPRINT (memory/CPU overhead the collector itself consumes for its bookkeeping). Picking a collector means picking which of those three you're willing to sacrifice for the other two, given your actual heap size and workload.
--> The single most important habit in GC tuning: **decide based on measured GC logs from a realistic workload, not from defaults, folklore, or synthetic benchmarks.** Every flag and every collector choice below is a starting hypothesis to verify, not a guaranteed fix.

# Decision Table: Which Collector

```text
Collector    Heap Size          Pause Target        Throughput    Best Fit
-----------  -----------------  -------------------  ------------  ------------------------------
Serial       < ~256MB-512MB     N/A (pauses scale     Low          Tiny CLI tools, small containers,
                                 with heap, can be                  single-core envs, tests --
                                 long)                               minimal footprint matters most

Parallel     Small-Large        No goal --            HIGHEST       Batch jobs, offline ETL, data
             (any)              pauses scale with                   pipelines -- anything where
                                 heap, can be long                   total runtime matters, not
                                                                     any single pause

G1           Moderate-Large     Soft goal via         High         DEFAULT CHOICE for most server
             (~4GB - 100s GB)   -XX:MaxGCPauseMillis                apps -- balanced throughput +
                                 (default 200ms);                   predictable (not guaranteed)
                                 tens of ms realistic                pause bounds, minimal tuning
                                 for most workloads                  needed

ZGC          Large-Huge         Sub-millisecond,      Medium-High   Huge heaps + hard low-latency
             (few GB - multi-   heap-size-             (Generational  SLAs: trading systems, real-time
             TB)                independent            ZGC closes    bidding, large in-memory caches,
                                                        most of the   anything where G1's pause profile
                                                        gap to G1)    isn't good enough after tuning

Shenandoah   Large-Huge         Sub-to-low-double-    Medium-High   Same niche as ZGC -- choice
             (few GB - multi-   digit ms, heap-size-                 between the two is usually a
             TB)                independent                         JDK-distribution/availability
                                                                     question, not a technical one
```

--> **A simple decision flow:**

```text
Is this a short-lived CLI tool / tiny container / test process?
    YES --> Serial GC
    NO, continue...

Is the ONLY thing that matters total throughput (batch/offline, no
per-request latency SLA)?
    YES --> Parallel GC
    NO, continue...

Is heap large (100s of GB+) AND is there a hard sub-10ms (or lower)
pause requirement that survives G1 tuning attempts?
    YES --> ZGC or Shenandoah (pick based on JDK distribution /
             availability / team familiarity -- both solve the
             same problem)
    NO, continue...

Default --> G1. It's the JVM's own default since Java 9 for good
             reason: reasonable throughput, bounded (if not
             guaranteed sub-ms) pauses, minimal manual tuning.
```

--> **When in doubt, start with G1 (the default) and only move to ZGC/Shenandoah once real GC logs from production-like load prove G1's pause profile is genuinely insufficient** -- premature adoption of a low-latency collector on a workload that doesn't need it just pays its overhead for no benefit.

# Key Tuning Flags Per Collector -- Quick Reference

```text
COMMON (all collectors)
-Xms<size>                       Initial heap size
-Xmx<size>                       Max heap size (recommend -Xms == -Xmx in production)
-XX:MaxMetaspaceSize=<size>      Cap Metaspace growth
-Xlog:gc*                        Unified GC logging (Java 9+) -- always enable in prod

SERIAL
-XX:+UseSerialGC                 Select Serial GC
(minimal further tuning surface -- that's the point)

PARALLEL
-XX:+UseParallelGC               Select Parallel GC
-XX:ParallelGCThreads=<n>        Worker thread count for STW collection
-XX:GCTimeRatio=<n>              Target ratio of app time : GC time (throughput goal)
-XX:MaxGCPauseMillis=<ms>        Best-effort pause goal (secondary to throughput here)

G1
-XX:+UseG1GC                     Select G1 (default already on modern JDKs)
-XX:MaxGCPauseMillis=<ms>        Soft pause-time goal (default 200)
-XX:G1HeapRegionSize=<size>      Region size (1m-32m, power of 2)
-XX:InitiatingHeapOccupancyPercent=<n>  Old-gen occupancy that triggers concurrent
                                  marking cycle start (default 45; adaptive by default)
-XX:G1MixedGCCountTarget=<n>     Spread Old-gen reclamation across N Mixed GCs
-XX:ConcGCThreads / ParallelGCThreads   Concurrent vs STW-phase thread counts

ZGC
-XX:+UseZGC                      Select ZGC (generational by default on JDK 21+,
                                  the only mode from JDK 23+)
-XX:ConcGCThreads=<n>            Concurrent worker threads (raise for high allocation
                                  rate on many-core machines)
-XX:SoftMaxHeapSize=<size>       Soft cap below -Xmx
-XX:ZAllocationSpikeTolerance=<n>  Headroom planning for allocation-rate spikes

SHENANDOAH
-XX:+UseShenandoahGC             Select Shenandoah (requires a JDK build that includes it)
-XX:ShenandoahGCHeuristics=<mode>  adaptive (default) / static / compact / aggressive
-XX:ConcGCThreads=<n>             Concurrent worker threads
-XX:ShenandoahGarbageThreshold=<n>  Garbage % for a region to become evacuation candidate
```

# How to Read GC Logs

--> Enable unified logging with `-Xlog:gc*:file=gc.log:time,uptime,level,tags` (Java 9+) -- this is the ground truth for every tuning decision. Older `-XX:+PrintGCDetails` style flags still work on some JDKs but the unified format is richer and standardized across collectors.
--> **What to look for regardless of collector:**

```text
1. Pause TYPE     -- Minor/Young-only GC vs Mixed GC vs Full GC. Full GC entries
                      (or G1 "evacuation failure" / "to-space exhausted") are
                      the biggest red flag in any collector's log -- they mean
                      the concurrent/incremental scheme fell behind and had to
                      fall back to the expensive worst case.

2. Pause DURATION -- actual measured time, compared against your latency budget
                      or pause-time goal. A single outlier matters more than
                      the average for latency-sensitive services -- look at
                      p99/max, not mean.

3. Pause FREQUENCY -- how often GCs happen. Rising frequency over time with
                      shrinking effectiveness (less memory reclaimed per cycle)
                      often signals a real or growing memory leak, not just a
                      tuning problem.

4. Heap occupancy  -- Old-gen (or overall, for ZGC/Shenandoah) occupancy
   BEFORE/AFTER      trend across many cycles. If it climbs and never comes
                      back down over time, that's a leak signature.

5. Allocation rate -- (Derivable from Eden-fill frequency, or explicit in some
                      collector logs.) A workload with an unexpectedly high
                      allocation rate drives more frequent GCs even with zero
                      leaks -- distinguishing "allocates a lot" from "leaks"
                      is the first diagnostic split to make.

6. Concurrent cycle -- (G1/ZGC/Shenandoah) Are concurrent marking/evacuation
   phase timing        cycles completing well before the next one is needed?
                      If concurrent phases are barely finishing in time (or
                      not finishing before the next cycle triggers), that's
                      the leading indicator of an eventual fallback to a
                      full STW collection under sustained load.
```

--> **G1 log example shape** (unified logging, abbreviated):

```text
[gc,start   ] GC(42) Pause Young (Normal) (G1 Evacuation Pause)
[gc,heap    ] GC(42) Eden regions: 42->0(45)
[gc,heap    ] GC(42) Survivor regions: 6->6(6)
[gc,heap    ] GC(42) Old regions: 120->123
[gc          ] GC(42) Pause Young (Normal) (G1 Evacuation Pause) 512M->498M(1024M) 18.421ms
```

--> **ZGC/Shenandoah log example shape** -- look for named phases (`Pause Mark Start`, `Concurrent Mark`, `Pause Mark End`, `Concurrent Relocate` for ZGC; `Init Mark`, `Concurrent Marking`, `Final Mark`, `Concurrent Evacuation`, `Concurrent Update References` for Shenandoah) each with their own timing -- confirm the STW-labeled ones ("Pause ..." / "Init ..." / "Final ...") stay in the sub-to-low-double-digit millisecond range you expect, and that concurrent phases aren't running so long they overlap the next cycle's trigger point.

# Tools for GC Analysis

```text
Tool                        What it's for
---------------------------  -----------------------------------------------------------
-Xlog:gc* (built into JVM)   Raw source of truth -- always enable in any environment
                              where GC behavior matters, including production.

GCViewer (open source)       Parses GC logs (Serial/Parallel/CMS/G1; partial ZGC/
                              Shenandoah support depending on version) into pause-time
                              graphs, throughput %, and summary stats -- good first pass
                              for visualizing a log file.

GCEasy (web-based)           Upload a GC log, get an automated report: pause time
                              percentiles, throughput, object creation rate,
                              recommendations. Handy for a quick external sanity check.

JDK Mission Control (JMC)    Free, bundled-adjacent profiler from Oracle/OpenJDK --
                              consumes JFR (Java Flight Recorder) data, which includes
                              detailed GC events with much lower overhead than verbose
                              logging; better for live/production profiling than log
                              parsing alone.

JFR (Java Flight Recorder)   Built into the JDK (-XX:+FlightRecorder / -XX:StartFlightRecording).
                              Captures GC pause events, allocation profiling, and much
                              more, at low overhead suitable for always-on production use --
                              generally the recommended modern approach over pure text logs
                              for deep analysis, while keeping -Xlog:gc* on as a lightweight
                              always-there trail.

jstat -gcutil <pid>          Quick, no-log-file-needed live snapshot of generation
                              occupancy percentages and GC counts/times -- useful for a
                              fast manual check without setting up log parsing.

VisualVM                     GUI, includes a basic GC-activity visualizer plus heap/
                              thread inspection -- lighter-weight than JMC, decent for
                              local development investigation.
```

--> **Prefer JFR over verbose text logs for serious investigation** -- it's lower overhead (safe to leave on in production continuously), captures richer structured data (allocation stack traces, per-phase timings), and integrates directly with JMC's flame-graph and timeline views. Keep `-Xlog:gc*` on too, since it's cheap and gives an always-available baseline trail even without JMC set up.

# Gotchas and Best Practices

--> **Match the collector to the workload's actual latency requirement, not the biggest hammer available** -- ZGC/Shenandoah's concurrent-everything machinery has real constant-factor overhead; deploying them on a workload that G1 already serves within its latency budget is pure cost with no benefit.
--> **`-Xms` == `-Xmx` in production, for every collector** -- avoids runtime heap-resizing overhead and gives the collector (especially ZGC/Shenandoah, which want evacuation headroom) predictable space to work with from the start.
--> **A bigger heap is not automatically better for pause time** -- for G1/Parallel/Serial, more live data generally means more work per Major/Full GC pause. ZGC/Shenandoah largely decouple pause time from heap size, which is precisely their value proposition -- but they still want enough headroom to avoid allocation stalls.
--> **Full GC (or G1 evacuation failure) appearing in logs at all is a signal to act on**, not background noise -- for any of the concurrent/incremental collectors (G1, ZGC, Shenandoah), a fallback to full/stop-the-world behavior means the concurrent scheme couldn't keep pace with the application's allocation rate, and it's usually the single worst pause an application will experience.
--> **Correlate GC pause spikes with actual application-observed latency, not just the raw GC log number** -- for ZGC/Shenandoah specifically, also check for allocation stalls (the app being throttled to let concurrent work catch up), which cause user-facing latency without necessarily appearing as a classic long STW pause.
--> **Re-verify tuning choices after JDK upgrades** -- G1's IHOP defaults became adaptive, ZGC gained generational mode as its default, Shenandoah's barrier implementation has evolved -- a configuration hand-tuned against an older JDK's behavior may be suboptimal (or even counterproductive) after an upgrade; re-baseline against fresh GC logs rather than assuming old flags still make sense.
--> **Tuning is iterative, not one-shot** -- change one thing, re-measure against real GC logs under realistic load, and only then change the next thing. Simultaneously changing the collector AND several flags makes it impossible to attribute the resulting effect to any one change.
