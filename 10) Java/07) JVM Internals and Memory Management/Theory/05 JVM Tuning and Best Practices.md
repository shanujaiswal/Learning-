# Why Tuning Is Its Own Skill

--> Everything in Files 01-04 (architecture, GC, leaks, class loading) is the mental model. This file is the PRACTICAL playbook -- what flags actually matter, how to size things sensibly, what to monitor in production, and critically, WHEN tuning is even worth doing at all versus leaving JVM defaults alone.
--> The single biggest mistake in JVM tuning is doing it reflexively (copy-pasting flags from a blog post) instead of empirically (measuring the actual problem first). This file leans heavily on that principle throughout.

# The Most Impactful JVM Flags -- A Practical Reference

```text
MEMORY SIZING
-Xms<size>                       Initial heap size                 e.g. -Xms2g
-Xmx<size>                       Maximum heap size                 e.g. -Xmx2g
-Xss<size>                       Per-thread stack size              e.g. -Xss512k
-XX:MaxMetaspaceSize=<size>      Cap on Metaspace (class metadata) e.g. -XX:MaxMetaspaceSize=256m
-XX:MaxDirectMemorySize=<size>   Cap on direct (off-heap) ByteBuffers

GC SELECTION AND TUNING
-XX:+UseG1GC / -XX:+UseZGC / -XX:+UseParallelGC / -XX:+UseSerialGC
-XX:MaxGCPauseMillis=<ms>        Soft pause-time goal (G1 and others)
-XX:+ParallelRefProcEnabled     Parallelize reference (Soft/Weak/Phantom) processing during GC

DIAGNOSTICS AND SAFETY NETS
-XX:+HeapDumpOnOutOfMemoryError  Auto heap dump on OOM (see File 03)
-XX:HeapDumpPath=<path>          Where to write that dump
-XX:+ExitOnOutOfMemoryError      Kill the JVM immediately on OOM instead of limping along corrupted
                                  (useful under an orchestrator like Kubernetes that will restart the pod)
-XX:+CrashOnOutOfMemoryError    Similar, produces a native crash file too
-Xlog:gc*:file=gc.log:time,uptime  Unified GC logging to a file (Java 9+)
-XX:+PrintCommandLineFlags       Print the effective flags (including JVM-computed defaults) at startup

CONTAINER AWARENESS (default-on since Java 10+)
-XX:+UseContainerSupport         JVM reads cgroup limits instead of host machine's full resources
                                  (critical when running in Docker/Kubernetes -- see Gotchas below)

MISC
-server                          (legacy, mostly implicit now) use the server JIT tier / defaults
-XX:+TieredCompilation          Enable tiered JIT (default on) -- see File 01
```

# Sizing the Heap

--> **Start from measured working set, not guesses.** Run the application under a realistic load, watch actual heap usage (via `jstat`, `jconsole`, or an APM tool) across multiple GC cycles, and size `-Xmx` with meaningful headroom above the observed post-GC "floor" -- not the peak, and not an arbitrary round number picked without data.
--> **`-Xms` equal to `-Xmx` is a common production default** -- avoids the overhead and latency variability of the JVM incrementally growing the heap under load; the memory is reserved/committed upfront in exchange for predictability.
--> **Bigger isn't automatically better** -- as covered in File 02, a larger heap can mean longer (though less frequent) Major/Full GC pauses. The right size balances "big enough to avoid excessive GC frequency" against "small enough that a Major GC pause stays within acceptable latency."
--> **Containers need extra care** -- in Docker/Kubernetes, `-Xmx` (or the JVM's auto-computed default, typically 25% of visible memory) must stay comfortably under the container's memory LIMIT, accounting for Metaspace, thread stacks, and other off-heap/native memory -- not just the heap. An OOM-killed container (killed by the OS/orchestrator, distinct from a Java `OutOfMemoryError`) is a classic sign the total JVM footprint, not just `-Xmx`, exceeded the container limit.

# Sizing the Stack

--> `-Xss` controls PER-THREAD stack size (default is typically 512KB-1MB depending on platform). Too small risks `StackOverflowError` on legitimately deep (but correct) call chains, especially with heavy recursion or deep framework call stacks; too large wastes memory per thread and, at large thread counts, can be a meaningful chunk of total memory (thousands of threads x a few MB each adds up fast).
--> A very deep, unbounded `StackOverflowError` in production is usually a genuine bug (unintended infinite/runaway recursion) rather than a sizing problem -- increasing `-Xss` to "fix" it often just delays the crash rather than addressing the root cause. Investigate the recursion first.

# Monitoring in Production

--> **What to actually track continuously** (via JMX/Micrometer/Prometheus exporters, not just ad hoc tool attachment):

```text
- Heap usage (used/committed/max) per generation, sampled over time -- watch the post-GC floor trend
- GC pause count and duration (both Minor and Major/Full) -- p50/p95/p99, not just averages
- GC throughput -- % of wall-clock time spent in GC (rising % over time is an early warning sign)
- Thread count -- a slowly climbing thread count often means a leak in a pool or unclosed executor
- Class count / Metaspace usage -- climbing steadily hints at a classloader leak (File 03)
- Application-level latency alongside GC metrics -- correlating a latency spike with a GC log
  timestamp is often the fastest way to confirm (or rule out) GC as the cause of a production issue
```

--> **JMX (Java Management Extensions)** exposes most of this programmatically -- `jconsole`/VisualVM are just JMX clients; production setups typically instead run a metrics agent (Micrometer + Prometheus, or a vendor APM agent) that scrapes the same JMX MBeans continuously and feeds dashboards/alerts, since attaching `jconsole` by hand to a production server isn't a sustainable monitoring strategy.
--> **GC logs are cheap insurance** -- `-Xlog:gc*:file=gc.log:time,uptime` has negligible overhead and should essentially always be enabled in production; when an incident happens, having the actual GC log for that time window is far more useful than trying to reproduce the issue after the fact.

# When to Actually Worry About GC

--> **Don't tune preemptively.** If the application meets its latency/throughput SLOs with default settings (which, since Java 9, means G1 with sensible auto-computed defaults), leave it alone -- tuning adds complexity and risk for no measured benefit.
--> **Signals that DO warrant investigation**:

```text
- p99/p999 latency shows periodic spikes that correlate with GC log timestamps
- GC throughput (time spent in GC / total time) climbs above roughly 5-10% sustained
- Full/Major GC frequency increases over the life of a long-running process (often -> a leak, not
  just "needs more heap" -- see File 03's leak-hunting workflow before reflexively raising -Xmx)
- OutOfMemoryErrors of any kind in logs (even ones the app appears to "recover" from)
- Steadily climbing baseline memory/thread/class counts on a dashboard, independent of load
```

--> **When a leak vs. a sizing problem look similar** -- both show heap usage trending upward. The distinguishing test: does usage eventually PLATEAU under STEADY, repeated load (a sizing/GC-tuning problem, fixable with heap size or collector choice) or does it grow WITHOUT BOUND indefinitely (a leak, fixable only by finding and removing the retaining reference per File 03)? Always run this test before changing GC flags in response to rising memory.

# A Sensible Default Tuning Checklist

```text
1. Pick a collector matching the workload -- G1 (default) for most services; ZGC for huge
   heaps/hard latency SLAs; Parallel for pure-throughput batch jobs; Serial only for tiny/CLI processes.
2. Set -Xms = -Xmx based on MEASURED working set with headroom, not a guess.
3. Enable -Xlog:gc* to a file, always -- cheap, and invaluable during incidents.
4. Enable -XX:+HeapDumpOnOutOfMemoryError with a writable -XX:HeapDumpPath -- a free safety net.
5. In containers, verify -XX:+UseContainerSupport is respecting the actual limit
   (check with -XX:+PrintFlagsFinal | grep -i maxheapsize inside the container) and leave headroom
   above -Xmx for Metaspace/threads/native memory within the container's hard memory limit.
6. Set a sensible -XX:MaxGCPauseMillis if using G1 and default pause behavior doesn't meet SLOs --
   but treat it as a starting point to measure against, not a guaranteed outcome.
7. Monitor continuously (JMX/metrics), not just when something already looks wrong.
8. Re-tune only in response to a measured, specific problem -- and re-measure after each change
   to confirm it actually helped, one flag at a time.
```

# Gotchas and Best Practices

--> **Flags interact -- change one at a time.** Changing GC algorithm AND heap size AND pause-time goal simultaneously makes it impossible to attribute a result to any specific change. Isolate variables during tuning, the same as any other experiment.
--> **JVM defaults are genuinely good now.** Modern JVMs (Java 11+) auto-detect available CPUs/memory (including container limits) and pick reasonable defaults -- the days of every production Java app needing a long hand-tuned flag list are largely over for typical workloads. Reach for manual tuning when you have a specific, measured problem, not as a default first step.
--> **`-XX:+PrintFlagsFinal`** (combined with `-version`, e.g. `java -XX:+PrintFlagsFinal -version | grep -i UseG1GC`) shows what the JVM actually resolved every flag to, including ones you didn't set explicitly -- an easy way to confirm what's really in effect, rather than assuming.
--> **Don't confuse container OOM-kill with Java `OutOfMemoryError`** -- the former is the OS/orchestrator killing the whole process for exceeding its cgroup memory limit (no Java stack trace, just an exit code / `OOMKilled` status); the latter is the JVM itself throwing a catchable/loggable Java exception. They require different fixes (container limit or total JVM footprint, vs. heap size or an actual leak).
--> **Warm-up-sensitive services need special attention** -- a service that autoscales aggressively (new instances spun up under load) pays the JIT warm-up cost (File 01) repeatedly on fresh instances right when load is highest; consider techniques like Class Data Sharing (`-Xshare`), AppCDS, or (for the most latency-sensitive cases) ahead-of-time/native-image compilation to reduce cold-start impact.
