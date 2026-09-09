/*
 * Topic02_ZGCDemo.java
 *
 * NOTE ON FILENAME: the public class is named "Topic02_ZGCDemo" (Java identifiers can't
 * start with a digit) while the FILE keeps the "02_..." numeric prefix used throughout
 * this repo for ordering.
 *
 * Compile: javac 02_ZGCDemo.java
 * Run (default collector on your JDK):
 *          java Topic02_ZGCDemo
 *
 * Run EXPLICITLY selecting ZGC (requires a JDK build that includes it; ZGC has been
 * production-ready since JDK 15, experimental from JDK 11):
 *          java -XX:+UseZGC -Xlog:gc*:file=gc-zgc.log:time,uptime,level,tags Topic02_ZGCDemo
 *
 * On JDK 21+, ZGC is GENERATIONAL by default (young + old generations, same core
 * concurrent/colored-pointer machinery for both -- see Theory File 02). To be explicit
 * about generational mode on JDKs where it's still opt-in (21/22; it's the ONLY mode
 * from JDK 23 onward):
 *          java -XX:+UseZGC -XX:+ZGenerational -Xlog:gc*:file=gc-zgc-gen.log:time,uptime,level,tags Topic02_ZGCDemo
 *
 * Give ZGC generous heap headroom (it wants room to relocate concurrently without
 * stalling allocation -- see "Heap Size Considerations" in Theory File 02):
 *          java -XX:+UseZGC -Xms512m -Xmx512m -XX:SoftMaxHeapSize=400m ^
 *               -Xlog:gc*:file=gc-zgc-sized.log:time,uptime,level,tags Topic02_ZGCDemo
 *
 * What you should see in the log with ZGC (see Theory File 02):
 *   - Named phases: "Pause Mark Start", "Concurrent Mark", "Pause Mark End",
 *     "Concurrent Process Non-Strong References", "Concurrent Relocate"
 *   - The "Pause ..." lines should be sub-millisecond to low-single-digit ms even
 *     under heavy allocation -- that flat, heap-size-independent pause time is
 *     ZGC's headline property
 *   - Possible "Allocation Stall" entries if the heap is undersized for the
 *     allocation rate this workload produces -- that is the ZGC-specific failure
 *     mode to watch for instead of a classic long STW pause
 *
 * Covers Theory chapter:
 *   07) JVM Internals and Memory Management/Theory/07 ZGC Deep Dive.md
 *
 * IMPORTANT CAVEAT: which collector actually RUNS is decided entirely by JVM flags at
 * launch time. This program cannot force ZGC on from inside main() -- what it CAN do is
 * (a) genuinely inspect and report which collector is active via java.lang.management,
 * and (b) run an allocation-heavy workload shaped to make ZGC's concurrent relocation
 * visible in the logs when the right flags are used to launch it.
 */

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.List;

public class Topic02_ZGCDemo {

    public static void main(String[] args) throws Exception {
        printSection("0) Active GC Configuration (java.lang.management)");
        reportActiveGarbageCollectors();

        printSection("1) Allocation-Heavy Workload -- Sustained High-Rate Churn");
        runSustainedAllocationWorkload();

        printSection("2) Allocation-Heavy Workload -- Large Object Retention (relocation pressure)");
        runRetainedLargeObjectWorkload();

        printSection("3) Active GC Configuration -- AFTER the workload (compare counts/times)");
        reportActiveGarbageCollectors();

        System.out.println("\nAll ZGC demo workloads completed.");
        System.out.println("Re-run with -XX:+UseZGC -Xlog:gc* (see flags in the header comment) to");
        System.out.println("actually observe ZGC's Pause Mark Start / Concurrent Mark / Pause Mark End /");
        System.out.println("Concurrent Relocate cycle described in Theory File 02, and confirm the");
        System.out.println("'Pause ...' phases stay sub-millisecond regardless of heap occupancy.");
    }

    // -------------------------------------------------------------------
    // 0) / 3) Inspect which GC is actually active using java.lang.management.
    //    Genuinely runnable and useful no matter which collector is active.
    // -------------------------------------------------------------------
    private static void reportActiveGarbageCollectors() {
        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();

        System.out.println("Detected " + gcBeans.size() + " GarbageCollectorMXBean(s):");
        for (GarbageCollectorMXBean bean : gcBeans) {
            System.out.printf("  Name: %-28s  CollectionCount=%-8d  CollectionTime=%dms%n",
                    bean.getName(), bean.getCollectionCount(), bean.getCollectionTime());
            System.out.println("    Managed pools: " + String.join(", ", bean.getMemoryPoolNames()));
        }

        System.out.println();
        System.out.println(identifyCollectorFamily(gcBeans));

        MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memBean.getHeapMemoryUsage();
        System.out.printf("%nHeap: used=%.1fMB  committed=%.1fMB  max=%.1fMB%n",
                heap.getUsed() / 1e6, heap.getCommitted() / 1e6, heap.getMax() / 1e6);
    }

    /**
     * Bean names identify the active collector family at runtime:
     *   ZGC (non-generational, JDK 11-20): "ZGC"
     *   ZGC (generational, JDK 21+):       "ZGC Cycles" / "ZGC Pauses"
     *   G1:                                "G1 Young Generation", "G1 Old Generation"
     *   Shenandoah:                        "Shenandoah Cycles" / "Shenandoah Pauses"
     *   Parallel:                          "PS Scavenge", "PS MarkSweep"
     *   Serial:                            "Copy", "MarkSweepCompact"
     */
    private static String identifyCollectorFamily(List<GarbageCollectorMXBean> gcBeans) {
        StringBuilder names = new StringBuilder();
        for (GarbageCollectorMXBean bean : gcBeans) {
            names.append(bean.getName()).append(' ');
        }
        String joined = names.toString();

        if (joined.contains("ZGC")) {
            String mode = joined.contains("Cycles") || joined.contains("Pauses")
                    ? "generational ZGC (JDK 21+ bean naming)"
                    : "non-generational (or pre-21) ZGC";
            return "==> Active collector family: ZGC -- " + mode + ". Matches this demo's Theory chapter.";
        } else if (joined.contains("G1")) {
            return "==> Active collector family: G1 -- launch with -XX:+UseZGC to see ZGC behavior instead.";
        } else if (joined.contains("Shenandoah")) {
            return "==> Active collector family: Shenandoah -- launch with -XX:+UseZGC to see ZGC behavior instead.";
        } else if (joined.contains("PS ") || joined.contains("Parallel")) {
            return "==> Active collector family: Parallel GC -- launch with -XX:+UseZGC to see ZGC behavior instead.";
        } else if (joined.contains("Copy") || joined.contains("MarkSweepCompact")) {
            return "==> Active collector family: Serial GC -- launch with -XX:+UseZGC to see ZGC behavior instead.";
        }
        return "==> Active collector family: unrecognized bean names (" + joined.trim() + ")";
    }

    // -------------------------------------------------------------------
    // 1) Sustained high allocation-rate churn -- ZGC is designed so pause
    //    time stays flat regardless of how fast/large the heap fills, but
    //    an undersized heap under sustained pressure like this can surface
    //    ALLOCATION STALLS (the ZGC-specific latency failure mode) instead
    //    of long pauses. See Theory File 02, "Heap Size Considerations".
    // -------------------------------------------------------------------
    private static void runSustainedAllocationWorkload() {
        System.out.println("Allocating a high volume of short-lived objects at a sustained rate.");
        System.out.println("Under ZGC this exercises Concurrent Mark + Concurrent Relocate without");
        System.out.println("(ideally) any pause growing with heap occupancy.\n");

        long totalAllocated = 0;
        for (int batch = 1; batch <= 25; batch++) {
            List<byte[]> churn = new ArrayList<>();
            for (int i = 0; i < 3000; i++) {
                byte[] chunk = new byte[8 * 1024]; // 8KB short-lived chunks
                chunk[0] = (byte) i;
                churn.add(chunk);
                totalAllocated += chunk.length;
            }
            churn.clear(); // everything above becomes garbage almost immediately
        }
        System.out.printf("Allocated ~%.1fMB total in sustained short-lived churn.%n", totalAllocated / 1e6);
    }

    // -------------------------------------------------------------------
    // 2) Retained large objects -- keep enough medium/large objects alive
    //    simultaneously that ZGC's CONCURRENT RELOCATION has real live data
    //    to move around during a cycle (relocation is the phase where
    //    colored pointers + load barriers do their work, per Theory File 02).
    // -------------------------------------------------------------------
    private static void runRetainedLargeObjectWorkload() {
        System.out.println("Retaining a growing set of medium-sized objects across rounds so ZGC's");
        System.out.println("concurrent relocation phase has meaningful live data to copy and");
        System.out.println("self-heal references to via the load barrier, per Theory File 02.\n");

        List<double[]> retained = new ArrayList<>();
        for (int round = 1; round <= 10; round++) {
            for (int i = 0; i < 200; i++) {
                double[] mediumObject = new double[2000]; // ~16KB each
                mediumObject[0] = i;
                retained.add(mediumObject);
            }
            if (round % 2 == 0) {
                System.out.printf("Round %d: retaining %,d medium objects (~%.1fMB live).%n",
                        round, retained.size(), (retained.size() * 2000L * 8) / 1e6);
            }
        }
        System.out.printf("Finished with %,d objects strongly reachable for relocation to act on.%n",
                retained.size());
        // retained falls out of scope when the method returns, freeing everything --
        // but while it's alive it represents a realistic "live set the GC must move".
    }

    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }
}
