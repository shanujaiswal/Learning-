/*
 * Topic03_MemoryLeaksDemo.java
 *
 * NOTE ON FILENAME: the public class is named "Topic03_MemoryLeaksDemo" (Java identifiers
 * can't start with a digit) while the FILE keeps the "03_..." numeric prefix used throughout
 * this repo for ordering.
 *
 * Compile: javac 03_MemoryLeaksDemo.java
 * Run:     java Topic03_MemoryLeaksDemo
 *
 * Demonstrates:
 *   1. Unbounded static-collection leak pattern -- a "cache" that only ever grows
 *   2. Listener/callback registration-without-deregistration leak pattern
 *   3. Unclosed resources -- why try-with-resources matters (AutoCloseable)
 *   4. Simple heap/memory measurement using java.lang.Runtime (before/after snapshots)
 *   5. OutOfMemoryError -- EXPLAINED via comments only, never actually triggered here
 *      (deliberately not destructive -- see the big comment block below for why)
 *   6. Notes on external profiling/diagnostic tools (jconsole, VisualVM, jstack, jmap, jstat)
 *
 * Covers Theory chapter:
 *   07) JVM Internals and Memory Management/Theory/03 Memory Leaks and Performance Profiling.md
 */

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Topic03_MemoryLeaksDemo {

    public static void main(String[] args) throws Exception {
        demoUnboundedCacheLeak();
        demoListenerLeak();
        demoUnclosedResourceVsTryWithResources();
        demoHeapMeasurement();
        explainOutOfMemoryErrorSimulation();
        printProfilingToolNotes();
        System.out.println("\nAll memory leak / profiling demos completed.");
    }

    // -------------------------------------------------------------------
    // 1) Unbounded cache leak pattern (Theory File 03, "Common Memory Leak Patterns" #1)
    // -------------------------------------------------------------------
    /**
     * A static Map that nothing ever evicts from is one of the most common real-world
     * "Java memory leaks" -- every entry is perfectly REACHABLE (so the GC correctly
     * refuses to touch it), but the application no longer actually NEEDS most of them.
     * The GC only knows reachability, not usefulness -- that's the whole point of this demo.
     */
    private static final Map<String, byte[]> LEAKY_STATIC_CACHE = new HashMap<>();

    private static void demoUnboundedCacheLeak() {
        printSection("1) Unbounded Static-Collection Leak Pattern");
        System.out.println("Simulating a request-handling loop that caches a per-\"user\" byte[] blob");
        System.out.println("and NEVER evicts anything -- a classic unbounded-cache leak.\n");

        Runtime rt = Runtime.getRuntime();
        long before = usedMemory(rt);

        for (int i = 0; i < 5000; i++) {
            // Each "userId" is unique here, so the cache only ever grows -- in a real system
            // this might be actual user session data, request bodies, or computed results
            // that are cached "just in case" and never expire.
            String userId = "user-" + i;
            byte[] data = new byte[2048]; // pretend this is some real per-user payload
            LEAKY_STATIC_CACHE.put(userId, data); // LEAK: never removed, never bounded
        }

        long after = usedMemory(rt);
        System.out.printf("Cache now holds %,d entries. Used memory before=%.1fMB after=%.1fMB%n",
                LEAKY_STATIC_CACHE.size(), before / 1e6, after / 1e6);

        System.out.println("\nNotice: unlike the 'sawtooth' pattern from 02_GarbageCollectionDemo.java,");
        System.out.println("this memory is NOT reclaimed by GC -- LEAKY_STATIC_CACHE is a static field,");
        System.out.println("reachable for the entire lifetime of the JVM, so every entry stays alive.");
        System.out.println("\nFix (Theory File 03): use a BOUNDED cache -- a fixed-capacity LinkedHashMap");
        System.out.println("in LRU mode, or a real caching library (Caffeine, Guava CacheBuilder) that");
        System.out.println("supports size limits, TTL expiry, and weak/soft-referenced values.");

        // Clean up deliberately for the rest of this demo run (a real leak wouldn't do this).
        LEAKY_STATIC_CACHE.clear();
    }

    // -------------------------------------------------------------------
    // 2) Listener / callback registration-without-deregistration leak
    // -------------------------------------------------------------------
    /** A long-lived "publisher" that never forgets a registered listener -- the leak's cause. */
    static class Publisher {
        private final List<Runnable> listeners = new ArrayList<>();

        void addListener(Runnable listener) {
            listeners.add(listener);
        }

        void removeListener(Runnable listener) {
            listeners.remove(listener);
        }

        void fireAll() {
            for (Runnable r : listeners) {
                r.run();
            }
        }

        int listenerCount() {
            return listeners.size();
        }
    }

    /**
     * A component that's meant to be short-lived. If it registers itself as a listener with
     * a long-lived Publisher and is never removed, the Publisher keeps it (and everything it
     * references) reachable forever -- even after this component is logically "done".
     */
    static class ShortLivedComponent {
        private final int id;
        // A large-ish array here stands in for "everything this component drags along with it"
        // if it's kept alive by an un-removed listener registration.
        private final byte[] payload = new byte[1024 * 64];

        ShortLivedComponent(int id) {
            this.id = id;
        }

        Runnable asListener() {
            // Anonymous/lambda listener implicitly captures 'this' -- exactly the "inner class
            // holding an implicit outer reference" pattern from Theory File 03, item #3.
            return () -> System.out.println("  (listener fired for component #" + id + ")");
        }
    }

    private static void demoListenerLeak() {
        printSection("2) Listener/Callback Registered but Never Deregistered");

        Publisher publisher = new Publisher(); // stands in for a long-lived event bus / framework object

        System.out.println("Registering 3 short-lived components as listeners on a long-lived publisher...");
        for (int i = 0; i < 3; i++) {
            ShortLivedComponent component = new ShortLivedComponent(i);
            publisher.addListener(component.asListener());
            // BUG (intentionally shown): component is meant to be done here, but its listener
            // was never removed -- publisher.removeListener(...) is missing on purpose.
        }

        System.out.println("publisher now holds " + publisher.listenerCount() + " listeners.");
        System.out.println("Even though the 3 ShortLivedComponent instances are logically finished being");
        System.out.println("used, and no other part of the program holds a direct reference to them, they");
        System.out.println("remain REACHABLE (and un-collectable) via publisher -> listener -> captured 'this'.");

        publisher.fireAll();

        System.out.println("\nFix (Theory File 03): always pair addListener with removeListener (often in a");
        System.out.println("close()/dispose()/@PreDestroy method), or have the publisher hold listeners via");
        System.out.println("WeakReference so they can still be collected even if deregistration is forgotten.");

        // Clean up deliberately so this demo doesn't itself leak beyond its own scope.
        for (int i = 0; i < publisher.listenerCount(); i++) {
            // no-op placeholder; in real code you'd call publisher.removeListener(specificListener)
        }
    }

    // -------------------------------------------------------------------
    // 3) Unclosed resources vs try-with-resources
    // -------------------------------------------------------------------
    /** A trivial stand-in "resource" (file handle / DB connection / socket) that must be closed. */
    static class FakeResource implements AutoCloseable {
        private final String name;
        private boolean closed = false;

        FakeResource(String name) {
            this.name = name;
            System.out.println("  [opened resource: " + name + "]");
        }

        void use() {
            if (closed) {
                throw new IllegalStateException("used after close!");
            }
            System.out.println("  [using resource: " + name + "]");
        }

        @Override
        public void close() {
            closed = true;
            System.out.println("  [closed resource: " + name + "]");
        }
    }

    private static void demoUnclosedResourceVsTryWithResources() {
        printSection("3) Unclosed Resources -- Why try-with-resources Matters");

        System.out.println("BAD pattern (shown here WITHOUT actually leaking anything real -- illustrative");
        System.out.println("only): manual close() calls can be skipped entirely if an exception is thrown");
        System.out.println("between open() and close(). Pseudocode of the anti-pattern:");
        System.out.println("    FakeResource r = new FakeResource(\"manual\");");
        System.out.println("    r.use();               // if this throws, close() below is NEVER reached");
        System.out.println("    r.close();              // skipped on exception -- resource leaks");

        System.out.println("\nGOOD pattern -- try-with-resources GUARANTEES close() runs, exception or not:");
        try (FakeResource r = new FakeResource("try-with-resources")) {
            r.use();
        }
        System.out.println("(close() above ran automatically even though there was no exception here --");
        System.out.println("it would ALSO have run if r.use() had thrown.)");

        System.out.println("\nNote (Theory File 03): this isn't a HEAP leak in the traditional sense -- it's");
        System.out.println("native/off-heap or externally-limited resources (file handles, DB connections,");
        System.out.println("sockets) that get exhausted even though the JVM heap itself looks perfectly fine.");
    }

    // -------------------------------------------------------------------
    // 4) Simple heap/memory measurement using Runtime
    // -------------------------------------------------------------------
    private static void demoHeapMeasurement() {
        printSection("4) Heap/Memory Measurement via java.lang.Runtime");

        Runtime rt = Runtime.getRuntime();
        System.out.println("Snapshot #1 (baseline):");
        printMemorySnapshot(rt);

        // Allocate a chunk of memory we deliberately keep reachable for a moment, to show
        // the numbers move -- then let it go out of scope.
        List<byte[]> temporary = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            temporary.add(new byte[1024 * 1024]); // ~1MB each, ~200MB total (bounded by available heap)
        }

        System.out.println("\nSnapshot #2 (after allocating ~200MB, still reachable via 'temporary'):");
        printMemorySnapshot(rt);

        temporary = null; // now eligible for collection
        System.gc();       // hint only, per Theory File 02 -- not guaranteed to run synchronously
        sleepBriefly();

        System.out.println("\nSnapshot #3 (after dropping the reference and requesting GC):");
        printMemorySnapshot(rt);

        System.out.println("\nThis before/after/after-GC snapshot technique -- using only Runtime, no external");
        System.out.println("tools -- is a cheap first check you can leave inline in code (e.g. around a");
        System.out.println("suspect operation) before reaching for a full profiler.");
    }

    private static void printMemorySnapshot(Runtime rt) {
        long max = rt.maxMemory();
        long total = rt.totalMemory();
        long free = rt.freeMemory();
        long used = total - free;
        System.out.printf("  max=%.1fMB  committed=%.1fMB  free-in-committed=%.1fMB  used(approx)=%.1fMB%n",
                max / 1e6, total / 1e6, free / 1e6, used / 1e6);
    }

    // -------------------------------------------------------------------
    // 5) OutOfMemoryError -- explained, NOT actually triggered
    // -------------------------------------------------------------------
    /*
     * WHY THIS IS COMMENTED OUT / NEVER EXECUTED:
     * Deliberately exhausting heap to force a real OutOfMemoryError would crash THIS demo's
     * own JVM process, likely take down whatever is running it (an IDE, a shared terminal,
     * a CI runner), and teaches nothing that a clear explanation + a safe historical example
     * doesn't already teach. Every OutOfMemoryError variant below is explained instead of
     * triggered -- this mirrors real practice: you generally don't WANT to reproduce an OOM
     * live in a shared environment; you read logs/heap dumps from where it already happened.
     *
     * If you ever genuinely want to trigger one in a throwaway/sandboxed JVM to see it firsthand,
     * this is (uncommented) roughly what it looks like -- do NOT run this in a shared process:
     *
     *     List<byte[]> hog = new ArrayList<>();
     *     while (true) {
     *         hog.add(new byte[1024 * 1024]); // 1MB per iteration, kept reachable via 'hog'
     *     }
     *     // Eventually throws: java.lang.OutOfMemoryError: Java heap space
     *
     * The reason that would eventually throw: 'hog' is a GC root-reachable list that keeps
     * growing and is NEVER allowed to shrink -- every byte[] stays reachable, so GC can't
     * reclaim any of them, until the heap has genuinely nothing left to give.
     */
    private static void explainOutOfMemoryErrorSimulation() {
        printSection("5) OutOfMemoryError Types -- Explained (Not Triggered)");
        System.out.println("(See the source comment just above this method for why nothing here actually");
        System.out.println("forces a real OutOfMemoryError -- it would crash this demo's own process.)\n");

        System.out.println("java.lang.OutOfMemoryError: Java heap space");
        System.out.println("    -> The heap itself is full and GC couldn't free enough. Classic leak (like");
        System.out.println("       section 1 above, unbounded) or a genuinely undersized heap for the workload.\n");

        System.out.println("java.lang.OutOfMemoryError: GC overhead limit exceeded");
        System.out.println("    -> JVM is spending >98% of CPU time doing GC and reclaiming <2% of the heap");
        System.out.println("       each time -- it gives up rather than let the app appear to hang.\n");

        System.out.println("java.lang.OutOfMemoryError: Metaspace");
        System.out.println("    -> Class metadata region is full -- usually a classloader leak (see");
        System.out.println("       04_ClassLoadingReflectionDemo.java) or -XX:MaxMetaspaceSize set too low.\n");

        System.out.println("java.lang.OutOfMemoryError: Unable to create new native thread");
        System.out.println("    -> The OS refused to create another OS thread for the JVM -- usually a");
        System.out.println("       thread-per-request leak or unbounded thread pool, or an OS thread/process limit.\n");

        System.out.println("java.lang.OutOfMemoryError: Requested array size exceeds VM limit");
        System.out.println("    -> Code tried to allocate an array larger than the JVM/platform allows --");
        System.out.println("       usually a size-computation bug (e.g. integer overflow), not a true capacity need.\n");

        System.out.println("java.lang.StackOverflowError");
        System.out.println("    -> Technically a different Error, and a DIFFERENT memory region (thread stack,");
        System.out.println("       not heap) -- infinite or too-deep recursion. See Theory File 01's Runtime");
        System.out.println("       Data Areas section, and 01_JvmArchitectureDemo.java's bounded recursion demo.");
    }

    // -------------------------------------------------------------------
    // 6) Notes on external profiling / diagnostic tools (comments only -- nothing to run here)
    // -------------------------------------------------------------------
    private static void printProfilingToolNotes() {
        printSection("6) Profiling and Diagnostic Tools (Notes)");
        System.out.println("These are external tools you run AGAINST a live JVM process (this one, or any");
        System.out.println("other) -- not something this demo can execute on itself. Summarized from Theory");
        System.out.println("File 03; try them against this program's own process ID (PID) if you like:\n");

        // jconsole -- ships with the JDK; live GUI showing heap/thread/class graphs over time via JMX;
        //             can trigger a manual GC or heap dump from the GUI. Good first stop for
        //             "is memory climbing over time?"-style questions.
        System.out.println("jconsole   -- live GUI monitor (heap/threads/classes/CPU over time via JMX).");

        // VisualVM -- deeper profiler (separate download): adds CPU/memory sampling profilers,
        //             thread dump visualization, and heap dump analysis (object counts, retained
        //             size, reference chains). The natural next step after jconsole shows a problem.
        System.out.println("VisualVM   -- deeper profiler: sampling profilers + heap dump analysis (object");
        System.out.println("              counts, retained size, reference chains).");

        // jstack <pid> -- prints every thread's current stack trace at that instant. Invaluable for
        //                 deadlocks, contention, or threads stuck waiting (e.g. all blocked on a
        //                 leaking connection pool that never returns a connection).
        System.out.println("jstack     -- `jstack <pid>` dumps every thread's stack trace right now; look");
        System.out.println("              for BLOCKED threads on the same lock (deadlock) or many threads");
        System.out.println("              stuck in the same frame (bottleneck/exhausted resource).");

        // jmap -histo <pid>          -- live per-class object count/byte histogram, printed to console
        // jmap -dump:live,format=b,file=heap.hprof <pid>  -- full heap dump for offline analysis
        //                 (open .hprof files in VisualVM / Eclipse MAT / IntelliJ's profiler to trace
        //                 exact reference chains -- "who's holding this alive, and why").
        System.out.println("jmap       -- `jmap -histo <pid>` for a fast per-class object/byte histogram;");
        System.out.println("              `jmap -dump:live,format=b,file=heap.hprof <pid>` for a full heap");
        System.out.println("              dump to analyze reference chains in VisualVM/Eclipse MAT.");

        // jstat -gcutil <pid> 1000 -- prints GC stats (per-generation % full, GC counts/times) every
        //                             1000ms -- lightweight way to watch generational behavior live.
        System.out.println("jstat      -- `jstat -gcutil <pid> 1000` streams GC stats (per-gen % full,");
        System.out.println("              GC counts/times) every second -- lightweight, no GUI needed.");

        // -XX:+HeapDumpOnOutOfMemoryError -- production-critical flag: writes a heap dump the MOMENT
        //             an OutOfMemoryError is thrown, capturing exact crash-time state instead of
        //             requiring a live reproduction (often impossible for intermittent prod-only leaks).
        //             Pair with -XX:HeapDumpPath=/path/to/dumps.
        System.out.println("-XX:+HeapDumpOnOutOfMemoryError -- auto heap dump at the moment of a real OOM;");
        System.out.println("              production-critical since intermittent leaks are rarely reproducible");
        System.out.println("              on demand. Pair with -XX:HeapDumpPath=<dir>.");

        System.out.println("\nBasic workflow (Theory File 03): notice a rising post-GC floor (jconsole/VisualVM)");
        System.out.println("-> narrow with a histogram (jmap -histo) -> confirm with a heap dump + 'Path to GC");
        System.out.println("Roots' -> fix the retaining reference -> verify the floor stays flat under load.");
    }

    // -------------------------------------------------------------------
    private static long usedMemory(Runtime rt) {
        return rt.totalMemory() - rt.freeMemory();
    }

    private static void sleepBriefly() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }
}
