# Why JFR Exists

--> Traditional profilers (sampling or instrumenting) are typically things you turn ON when you already suspect a problem, run for a short window, then turn off -- because their overhead is too high to leave running in production all the time. **JFR (JDK Flight Recorder)** was built with a different goal from day one: overhead low enough (typically well under 1-2%) to run **continuously in production**, so that when something goes wrong, you already have a recording covering the incident, instead of needing to reproduce it live.
--> JFR started as a commercial-only JRockit/Oracle JDK feature, was open-sourced and merged into OpenJDK in JDK 11 (JEP 328), and is now a standard, free, built-in part of every OpenJDK-based JVM from 11 onward (backported to 8u262+ in some distributions). No extra download, no agent to attach -- it's compiled into the JVM itself.
--> The companion analysis GUI, **JDK Mission Control (JMC)**, is a separate download (no longer bundled with the JDK) -- JFR is the recorder built into the JVM, JMC is the desktop app you open `.jfr` files in.

# Event-Based Profiling -- The Core Model

--> JFR's fundamental unit is the **event**, not a periodic stack sample or an instrumented method call. Internally, JVM subsystems (the GC, the JIT compiler, the class loader, thread scheduler, I/O, allocation paths) are already instrumented with lightweight "fire an event" hooks that were added specifically for JFR -- when a GC pause happens, a class loads, an exception is thrown, a thread blocks on a lock past a threshold, etc., the responsible JVM subsystem emits a structured event with relevant fields, a timestamp, and (for many event types) a stack trace.
--> This is fundamentally different from a sampling profiler that wakes up every N milliseconds and asks "what's on the stack right now regardless of what's happening" -- JFR events are emitted BY the code path that matters, at the moment it matters, so rare-but-expensive events (a 200ms GC pause, a single very slow I/O call) are captured precisely rather than needing to get lucky with a sample.
--> JFR does still include a **sampling-based** event for CPU/method profiling specifically (`jdk.ExecutionSample`), since "what's hot on the CPU generally" is inherently a statistical question -- but everything else (GC, compilation, allocation, locking, I/O, exceptions) is captured as true, deterministic events, not samples.

# Event Categories

| Category | Example events | Typical use |
|---|---|---|
| GC | `GarbageCollection`, `OldGarbageCollection`, `AllocationInsideTLAB`, `AllocationOutsideTLAB` | Pause times, per-collector behavior, allocation hotspots |
| Threading / Locking | `ExecutionSample`, `ThreadPark`, `JavaMonitorEnter`, `ThreadSleep` | CPU hotspots, lock contention, blocking time |
| JIT / Compilation | `Compilation`, `Deoptimization`, `CompilerInlining` | Slow-to-warm-up methods, deopt storms (see File 05) |
| Class loading | `ClassLoad`, `ClassLoaderStatistics` | Classloader leaks, excessive dynamic class generation |
| I/O | `SocketRead`, `SocketWrite`, `FileRead`, `FileWrite` | Slow network/disk calls |
| Exceptions | `JavaExceptionThrow`, `JavaErrorThrow` | Hidden/swallowed exception storms hurting performance |
| Application-defined | Custom events via `jdk.jfr.Event` | Business-specific timing (e.g. "order processing duration") |

--> Events carry a **threshold** where relevant -- e.g. `JavaMonitorEnter` (lock contention) only fires when a thread waits longer than a configurable duration, so ordinary uncontended locking (the overwhelming majority of lock operations in a healthy app) produces zero overhead and zero noise.

# Recording Levels: "Profile" vs "Default"

--> JFR ships two built-in configurations, trading detail for overhead:

| Config | Overhead | What's on |
|---|---|---|
| `default` | ~0.1-1% | Coarser sampling intervals, fewer stack traces, meant to run always-on in production |
| `profile` | ~1-3% | Finer sampling (e.g. more frequent `ExecutionSample`), more stack traces, meant for a deliberate short diagnostic session |

--> Both are still dramatically cheaper than most instrumenting profilers, which is the entire point -- `profile` is "turn up the detail for an hour," not "only usable outside production."

# Starting a Recording

## At JVM Startup (flags)

```text
# Time-bounded recording, written to disk automatically
-XX:StartFlightRecording=duration=60s,filename=myapp.jfr

# Continuous, always-on recording (ring-buffer style, capped size/duration)
-XX:StartFlightRecording=disk=true,maxsize=250MB,maxage=1h,filename=recording.jfr,settings=default

# Combine with the unlock flag on some older JDK versions (not needed on JDK 11+ OpenJDK)
-XX:+UnlockCommercialFeatures -XX:+FlightRecorder     # legacy Oracle JDK 8 only
```

## Attaching to an Already-Running JVM (`jcmd`)

```text
# Start a recording on a live process, capped, dumped to disk on stop
jcmd <pid> JFR.start name=diag duration=120s filename=diag.jfr settings=profile

# Check what's currently recording
jcmd <pid> JFR.check

# Dump the current buffer without stopping the recording (great for continuous recordings)
jcmd <pid> JFR.dump name=diag filename=snapshot.jfr

# Stop a named recording
jcmd <pid> JFR.stop name=diag
```

--> **`jcmd` is the preferred way to start ad-hoc recordings on production** -- no restart needed, which matters enormously for an incident that's already happening on a JVM you can't afford to bounce.
--> **`settings=profile` vs `settings=default`** maps directly to the table above -- pass a custom `.jfc` settings file (editable/exportable from JMC's template manager) for fine-grained control over which events are on and at what threshold.
--> **`disk=true` + `maxsize`/`maxage`** is what makes "always-on in production" practical -- JFR keeps a rolling window in memory and/or on disk and discards older data past the cap, so a recording left running for weeks doesn't grow unbounded; when an incident happens, `JFR.dump` pulls the recent window that already captured it.

# Analyzing Recordings in JDK Mission Control (JMC)

--> Open a `.jfr` file in JMC (`File > Open File`) to get the **Automated Analysis** report first -- JMC runs a built-in rule engine over the recording and surfaces likely issues (e.g. "high GC pause ratio," "lock contention detected," "excessive exceptions") ranked by severity, before you've looked at a single raw event.
--> **General tab** -- overview of recording duration, JVM version, and startup flags actually in effect (useful for confirming what config a production JVM is really running).
--> **Memory tab** -- heap/pool usage over time, GC pause timeline, allocation-by-class breakdown (which classes are generating the most allocation pressure, often the real lever on GC pause frequency).
--> **Code tab** -- the "hot methods" view built from `ExecutionSample` events, aggregated into both a flat hot-method list and a call-tree/flame-graph-style view; also shows JIT compilation activity and (crucially) exception statistics -- a class throwing exceptions in a hot loop is a classic hidden performance killer that CPU-only profilers can miss if the exception path itself is fast per-call but happens constantly.
--> **Threads tab** -- a per-thread timeline showing running/waiting/blocked state over the recording window, plus a lock-contention view -- for finding which specific lock is the bottleneck and which threads are waiting on it, without needing repeated `jstack` snapshots.
--> **I/O tab** -- socket/file read/write events with duration, for spotting slow external calls hiding inside otherwise "fast" application code.

# Custom Application Events

--> JFR isn't limited to built-in JVM events -- application code can define and emit its own events using the `jdk.jfr` API (built into the JDK, no extra dependency), and they show up in JMC right alongside GC/thread/IO events on the same unified timeline.

```java
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Description;

@Label("Order Processed")
@Description("Time taken to fully process a customer order")
class OrderProcessedEvent extends Event {
    @Label("Order ID")
    String orderId;

    @Label("Item Count")
    int itemCount;
}

void processOrder(Order order) {
    OrderProcessedEvent event = new OrderProcessedEvent();
    event.begin();
    try {
        // ... actual processing ...
        event.orderId = order.getId();
        event.itemCount = order.getItems().size();
    } finally {
        event.commit();   // no-op with near-zero cost if this event type isn't enabled in the current recording
    }
}
```

--> This is the mechanism that makes JFR genuinely useful for correlating BUSINESS-level events ("order processing took 4 seconds") against SYSTEM-level events ("...because a Full GC ran during that window") on one shared timeline -- something a generic profiler with no domain knowledge can't do.
--> **Disabled events cost almost nothing.** `event.commit()` on a disabled event type is a cheap check-and-return, which is why it's safe to leave custom JFR instrumentation compiled into production code permanently rather than stripping it out.

# JFR vs Other Tools -- When to Reach for It

| Situation | Better fit |
|---|---|
| "Something's wrong right now in production, need history" | JFR continuous recording + `JFR.dump` |
| "Need exact GC pause causes and allocation hotspots" | JFR (built-in GC/allocation events) |
| "Need a quick live look at heap/thread graphs, interactively" | JConsole/VisualVM (JMX-based, see Files 01 and 03) |
| "Need line-level CPU profiling with async-profiler-grade stack accuracy" | async-profiler (see File 03) -- JFR's `ExecutionSample` is good but async-profiler can be more precise for pure CPU work |
| "Need to correlate a business event with system behavior on one timeline" | JFR custom events |

# Gotchas and Best Practices

--> **JFR requires `-XX:+FlightRecorder` unlocking on some old Oracle JDK 8 builds** (it was a licensed commercial feature there) -- irrelevant on any modern OpenJDK-based distribution (11+, and most 8 builds from major vendors today), where it's on by default and free, but worth knowing if working with an old, unpatched Oracle JDK 8.
--> **`ExecutionSample`'s sampling interval means very short-lived hot methods can be under-sampled** -- for extremely fine-grained CPU profiling of a narrow hot loop, a dedicated sampling profiler with a shorter interval (async-profiler) can resolve detail JFR's default settings would smooth over; bump to `profile` settings or a custom `.jfc` first before reaching for another tool.
--> **A `.jfr` file's size is bounded by your `maxsize`/`maxage`/`duration` settings, not by how much happened** -- if a recording is capped too small for a slow-building issue (e.g. a leak that takes 6 hours to become visible), the ring buffer will have discarded the earliest, most-informative data by the time anyone looks; size continuous recordings generously against expected time-to-notice.
--> **Old-object tracking (`OldObjectSample`) has to be explicitly enabled and carries more overhead than the rest of `default`** -- it's what lets JMC show "objects that have survived a long time and are still referenced," directly useful for memory-leak hunting, but isn't in the low-overhead default set for good reason.
--> **JMC's automated analysis is a starting point, not a verdict** -- its rules flag statistically unusual patterns; always cross-check a flagged issue against the actual timeline/call-tree data rather than treating a rule's severity label as ground truth.
--> **Recordings are portable** -- a `.jfr` captured on a production server can be copied off and analyzed on a laptop with no live connection to the process required, unlike JMX-based live tools; this makes JFR the natural default for "capture now, analyze later, possibly by someone else entirely."
