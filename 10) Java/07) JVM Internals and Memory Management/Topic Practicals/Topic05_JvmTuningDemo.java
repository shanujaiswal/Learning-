/*
 * Topic05_JvmTuningDemo.java
 *
 * NOTE ON FILENAME: the public class is named "Topic05_JvmTuningDemo" (Java identifiers
 * can't start with a digit) while the FILE keeps the "05_..." numeric prefix used throughout
 * this repo for ordering.
 *
 * Compile: javac 05_JvmTuningDemo.java
 * Run:     java Topic05_JvmTuningDemo
 * Run with some of the documented flags to see them take effect, e.g.:
 *          java -Xms256m -Xmx256m -XX:+UseG1GC -Xlog:gc* Topic05_JvmTuningDemo
 *          java -XX:+PrintFlagsFinal -version | findstr /I maxheapsize   (Windows)
 *
 * Demonstrates:
 *   1. Reading JVM info at runtime -- available processors, heap sizing, thread info
 *   2. Common JVM tuning flags, documented as comments with explanations (not settable
 *      from inside a running program -- flags are supplied at JVM launch)
 *   3. A best-practices checklist as comments, distilled from Theory File 05
 *
 * Covers Theory chapter:
 *   07) JVM Internals and Memory Management/Theory/05 JVM Tuning and Best Practices.md
 */

public class Topic05_JvmTuningDemo {

    public static void main(String[] args) throws Exception {
        demoRuntimeJvmInfo();
        printCommonJvmFlags();
        printMonitoringSignals();
        printBestPracticesChecklist();
        System.out.println("\nAll JVM tuning demos completed.");
    }

    // -------------------------------------------------------------------
    // 1) Reading JVM info at runtime
    // -------------------------------------------------------------------
    private static void demoRuntimeJvmInfo() {
        printSection("1) Reading JVM Info at Runtime");

        Runtime rt = Runtime.getRuntime();

        System.out.println("Available processors (rt.availableProcessors()): " + rt.availableProcessors());
        System.out.println("(Since Java 10+, with -XX:+UseContainerSupport default-on, this correctly");
        System.out.println("reflects a container's CPU quota/cgroup limit, not just the host machine's");
        System.out.println("full core count -- critical for sizing thread pools correctly in Docker/K8s.)");

        long max = rt.maxMemory();       // -Xmx ceiling, or the JVM-computed default if unset
        long total = rt.totalMemory();   // currently committed heap
        long free = rt.freeMemory();     // free space within the committed heap
        long used = total - free;

        System.out.println();
        System.out.printf("Max heap (-Xmx or computed default): %,10d bytes (%.1f MB)%n", max, max / 1e6);
        System.out.printf("Total committed heap:                %,10d bytes (%.1f MB)%n", total, total / 1e6);
        System.out.printf("Free within committed heap:           %,10d bytes (%.1f MB)%n", free, free / 1e6);
        System.out.printf("Used (approx):                        %,10d bytes (%.1f MB)%n", used, used / 1e6);

        System.out.println("\nOther runtime-observable info worth knowing about:");
        System.out.println("  Thread.activeCount()                 = " + Thread.activeCount()
                + "  (active threads in this thread's thread group -- an approximation, not exact)");
        System.out.println("  System.getProperty(\"java.version\")   = " + System.getProperty("java.version"));
        System.out.println("  System.getProperty(\"java.vm.name\")   = " + System.getProperty("java.vm.name"));
        System.out.println("  System.getProperty(\"os.arch\")        = " + System.getProperty("os.arch"));

        System.out.println("\nNote (Theory File 05): 'Start from measured working set, not guesses.' This is");
        System.out.println("exactly the kind of live snapshot you'd sample repeatedly under real load --");
        System.out.println("via jstat/jconsole/an APM agent in production -- before ever setting -Xmx by hand.");
    }

    // -------------------------------------------------------------------
    // 2) Common JVM flags -- documented as comments (these are launch-time flags;
    //    a running program cannot change them for itself, only read their effects above)
    // -------------------------------------------------------------------
    private static void printCommonJvmFlags() {
        printSection("2) Common JVM Tuning Flags (Documented, Not Settable at Runtime)");
        System.out.println("These are supplied on the `java` command line at JVM launch -- printed here as");
        System.out.println("reference documentation, matching Theory File 05's practical flag reference:\n");

        System.out.println("MEMORY SIZING");
        System.out.println("  -Xms<size>                      Initial heap size, e.g. -Xms2g");
        System.out.println("  -Xmx<size>                      Maximum heap size, e.g. -Xmx2g");
        System.out.println("  -Xss<size>                      Per-thread stack size, e.g. -Xss512k");
        System.out.println("                                  (too small -> spurious StackOverflowError on");
        System.out.println("                                   legitimately deep recursion; too large wastes");
        System.out.println("                                   memory per thread at high thread counts)");
        System.out.println("  -XX:MaxMetaspaceSize=<size>     Cap on Metaspace (class metadata), e.g. 256m");
        System.out.println("  -XX:MaxDirectMemorySize=<size>  Cap on direct (off-heap) ByteBuffers");

        System.out.println("\nGC SELECTION AND TUNING");
        System.out.println("  -XX:+UseG1GC                    G1 -- balanced default for most services (Java 9+ default)");
        System.out.println("  -XX:+UseZGC                     Ultra-low-pause collector for huge heaps / hard SLAs");
        System.out.println("  -XX:+UseParallelGC              Throughput-oriented; good for batch/offline jobs");
        System.out.println("  -XX:+UseSerialGC                Single-threaded GC; only for tiny/CLI processes");
        System.out.println("  -XX:MaxGCPauseMillis=<ms>       Soft pause-time GOAL (G1 and others) -- a target,");
        System.out.println("                                  not a guarantee");
        System.out.println("  -XX:+ParallelRefProcEnabled     Parallelize Soft/Weak/Phantom reference processing");

        System.out.println("\nDIAGNOSTICS AND SAFETY NETS");
        System.out.println("  -XX:+HeapDumpOnOutOfMemoryError Auto heap dump the instant a real OOM is thrown");
        System.out.println("  -XX:HeapDumpPath=<path>         Where that heap dump gets written");
        System.out.println("  -XX:+ExitOnOutOfMemoryError     Kill the JVM immediately on OOM instead of limping");
        System.out.println("                                  along corrupted (useful under Kubernetes, which");
        System.out.println("                                  will simply restart the pod)");
        System.out.println("  -Xlog:gc*:file=gc.log:time,uptime   Unified GC logging to a file (Java 9+) --");
        System.out.println("                                  negligible overhead, should essentially always be on");
        System.out.println("  -XX:+PrintCommandLineFlags       Prints the effective flags (incl. JVM-computed");
        System.out.println("                                  defaults) at startup");
        System.out.println("  -XX:+PrintFlagsFinal             Combined with -version, shows what the JVM actually");
        System.out.println("                                  resolved EVERY flag to -- confirms reality vs assumption");

        System.out.println("\nCONTAINER AWARENESS (default-on since Java 10+)");
        System.out.println("  -XX:+UseContainerSupport        JVM reads cgroup limits instead of the host");
        System.out.println("                                  machine's full resources -- critical in Docker/K8s;");
        System.out.println("                                  this program's own availableProcessors() above");
        System.out.println("                                  reflects exactly this behavior");

        System.out.println("\nMISC");
        System.out.println("  -XX:+TieredCompilation          Tiered JIT compilation (default on) -- see Theory");
        System.out.println("                                  File 01 and 01_JvmArchitectureDemo.java's JIT demo");
    }

    // -------------------------------------------------------------------
    // Monitoring signals worth tracking continuously in production (comments/print only)
    // -------------------------------------------------------------------
    private static void printMonitoringSignals() {
        printSection("3) What to Monitor in Production (Signals, Not Just Snapshots)");
        System.out.println("(Theory File 05 -- track these continuously via JMX/Micrometer/Prometheus, not");
        System.out.println("just by attaching a tool once when something already looks wrong):\n");

        System.out.println("  - Heap usage (used/committed/max) per generation, sampled over time --");
        System.out.println("    watch the POST-GC FLOOR trend, not just the peaks");
        System.out.println("  - GC pause count and duration (Minor AND Major/Full) -- p50/p95/p99, not averages");
        System.out.println("  - GC throughput -- % of wall-clock time spent in GC (a rising % is an early warning)");
        System.out.println("  - Thread count -- a slowly climbing count often means a leak in a pool or executor");
        System.out.println("  - Class count / Metaspace usage -- steady climb hints at a classloader leak");
        System.out.println("    (see 03_MemoryLeaksDemo.java and 04_ClassLoadingReflectionDemo.java)");
        System.out.println("  - Application latency alongside GC metrics -- correlating a latency spike with");
        System.out.println("    a GC log timestamp is often the fastest way to confirm/rule out GC as the cause");

        System.out.println("\nDistinguishing a LEAK from a SIZING problem (Theory File 05): does usage");
        System.out.println("eventually PLATEAU under steady, repeated load (sizing -- fixable with heap size");
        System.out.println("or collector choice) or does it grow WITHOUT BOUND indefinitely (a leak -- fixable");
        System.out.println("only by finding and removing the retaining reference)? Always run this test before");
        System.out.println("reflexively raising -Xmx in response to rising memory.");
    }

    // -------------------------------------------------------------------
    // Best-practices checklist, as comments (distilled from Theory File 05)
    // -------------------------------------------------------------------
    /*
     * A SENSIBLE DEFAULT TUNING CHECKLIST (Theory File 05):
     *
     * 1. Pick a collector matching the workload -- G1 (default) for most services; ZGC for
     *    huge heaps/hard latency SLAs; Parallel for pure-throughput batch jobs; Serial only
     *    for tiny/CLI processes.
     * 2. Set -Xms = -Xmx based on MEASURED working set with headroom, not a guess -- avoids
     *    the overhead/latency variability of the heap incrementally growing under load.
     * 3. Enable -Xlog:gc* to a file, ALWAYS -- cheap, and invaluable during incidents.
     * 4. Enable -XX:+HeapDumpOnOutOfMemoryError with a writable -XX:HeapDumpPath -- a free
     *    safety net for otherwise-unreproducible production leaks.
     * 5. In containers, verify -XX:+UseContainerSupport is respecting the actual limit
     *    (check with -XX:+PrintFlagsFinal | grep -i maxheapsize inside the container) and
     *    leave headroom above -Xmx for Metaspace/threads/native memory within the
     *    container's hard memory limit.
     * 6. Set a sensible -XX:MaxGCPauseMillis if using G1 and default pause behavior doesn't
     *    meet SLOs -- but treat it as a starting point to measure against, not a guarantee.
     * 7. Monitor continuously (JMX/metrics), not just when something already looks wrong.
     * 8. Re-tune only in response to a measured, specific problem -- change ONE flag at a
     *    time and re-measure after each change to confirm it actually helped.
     *
     * GOTCHAS AND BEST PRACTICES:
     * - Flags interact -- change one at a time, or you can't attribute a result to any
     *   specific change.
     * - JVM defaults are genuinely good now (Java 11+ auto-detects CPUs/memory, including
     *   container limits). Reach for manual tuning only for a specific, MEASURED problem.
     * - Don't confuse a container OOM-kill (OS/orchestrator kills the whole process for
     *   exceeding its cgroup limit -- no Java stack trace) with a Java OutOfMemoryError
     *   (the JVM itself throwing a catchable/loggable exception). Different root causes,
     *   different fixes.
     * - Warm-up-sensitive, aggressively-autoscaled services pay the JIT warm-up cost
     *   (Theory File 01) repeatedly on fresh instances, right when load is highest --
     *   consider Class Data Sharing (-Xshare), AppCDS, or ahead-of-time/native-image
     *   compilation for the most latency-sensitive cases.
     * - Don't tune preemptively: if the app meets its latency/throughput SLOs with default
     *   settings, leave it alone. Tuning adds complexity and risk for no measured benefit.
     */
    private static void printBestPracticesChecklist() {
        printSection("4) Best-Practices Checklist (See Source Comments Above for Full Detail)");
        System.out.println("1. Pick a collector matching the workload (G1 default / ZGC / Parallel / Serial).");
        System.out.println("2. Set -Xms = -Xmx from MEASURED working set with headroom, not a guess.");
        System.out.println("3. Always enable -Xlog:gc* to a file -- cheap insurance.");
        System.out.println("4. Always enable -XX:+HeapDumpOnOutOfMemoryError with -XX:HeapDumpPath.");
        System.out.println("5. In containers, verify -XX:+UseContainerSupport respects the real limit,");
        System.out.println("   and leave headroom above -Xmx for Metaspace/threads/native memory.");
        System.out.println("6. Set -XX:MaxGCPauseMillis as a measured goal, not a blind guarantee.");
        System.out.println("7. Monitor continuously (JMX/metrics) -- not only when something looks wrong.");
        System.out.println("8. Re-tune ONE flag at a time, only for a measured problem, and re-measure after.");
        System.out.println("\n(Full explanations for each item are in this file's source comments, directly");
        System.out.println("above printBestPracticesChecklist(), and in Theory File 05.)");
    }

    // -------------------------------------------------------------------
    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }
}
