/*
 * Topic01_G1Demo.java
 *
 * NOTE ON FILENAME: the public class is named "Topic01_G1Demo" (Java identifiers can't
 * start with a digit) while the FILE keeps the "01_..." numeric prefix used throughout
 * this repo for ordering.
 *
 * Compile: javac 01_G1Demo.java
 * Run (default collector on your JDK -- G1 on Java 9+ unless overridden):
 *          java Topic01_G1Demo
 *
 * Run EXPLICITLY selecting G1 with a tight pause goal and GC logging (recommended):
 *          java -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -Xlog:gc*:file=gc-g1.log:time,uptime,level,tags Topic01_G1Demo
 *
 * Run with a smaller region size and IHOP tuned lower, to make marking cycles trigger
 * more often and more visibly in the log against this program's allocation workload:
 *          java -XX:+UseG1GC -XX:G1HeapRegionSize=1m -XX:InitiatingHeapOccupancyPercent=30 ^
 *               -Xmx256m -Xlog:gc*:file=gc-g1-tuned.log:time,uptime,level,tags Topic01_G1Demo
 *
 * What you should see in the log with the above flags (see Theory File 01):
 *   - "Pause Young (Normal) (G1 Evacuation Pause)" entries as Eden fills
 *   - Eventually "Pause Initial Mark", "Concurrent Mark", "Pause Remark", "Pause Cleanup"
 *     as Old-gen occupancy crosses the IHOP threshold
 *   - "Pause Young (Mixed) (G1 Evacuation Pause)" entries afterward, reclaiming the
 *     Old regions identified as garbage-heavy during Cleanup
 *   - Possibly "Humongous Allocation" lines from the humongous-object workload below
 *
 * Covers Theory chapter:
 *   07) JVM Internals and Memory Management/Theory/06 G1 Garbage Collector Deep Dive.md
 *
 * IMPORTANT CAVEAT: which collector actually RUNS is decided entirely by JVM flags at
 * launch time -- no amount of code inside main() can force G1 on if the JVM was started
 * with, say, -XX:+UseSerialGC. What THIS program CAN do, and does below, is (a) genuinely
 * inspect and report which collector is active via java.lang.management, and (b) run an
 * allocation-heavy workload shaped to be interesting to watch under G1 specifically
 * (short-lived churn, tenured survivors, and a humongous-object pattern).
 */

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.List;

public class Topic01_G1Demo {

    public static void main(String[] args) throws Exception {
        printSection("0) Active GC Configuration (java.lang.management)");
        reportActiveGarbageCollectors();

        printSection("1) Allocation-Heavy Workload -- Young-Gen Churn (G1 Evacuation Pauses)");
        runYoungGenChurnWorkload();

        printSection("2) Allocation-Heavy Workload -- Tenured Survivors (feeds Old-gen / Mixed GC)");
        runTenuringWorkload();

        printSection("3) Allocation-Heavy Workload -- Humongous Objects (> 50% of a G1 region)");
        runHumongousAllocationWorkload();

        printSection("4) Active GC Configuration -- AFTER the workload (compare counts/times)");
        reportActiveGarbageCollectors();

        System.out.println("\nAll G1 demo workloads completed.");
        System.out.println("Re-run with -XX:+UseG1GC -Xlog:gc* (see flags in the header comment) to");
        System.out.println("actually observe G1's Initial Mark / Concurrent Mark / Remark / Cleanup /");
        System.out.println("Mixed GC cycle described in Theory File 01.");
    }

    // -------------------------------------------------------------------
    // 0) / 4) Inspect which GC is actually active using java.lang.management.
    //    This part is genuinely runnable and useful regardless of which
    //    collector the JVM was launched with -- it reports real, live data.
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
     * The bean NAMES themselves tell you which collector family is active --
     * this is the standard, reliable way to detect the running GC at runtime
     * without parsing command-line flags yourself.
     *   G1:         "G1 Young Generation", "G1 Old Generation"
     *   ZGC:        "ZGC" (or "ZGC Cycles" / "ZGC Pauses" on generational ZGC, JDK 21+)
     *   Shenandoah: "Shenandoah Cycles" / "Shenandoah Pauses"
     *   Parallel:   "PS Scavenge", "PS MarkSweep" (or "Parallel Scavenge"/"Parallel MarkSweep")
     *   Serial:     "Copy", "MarkSweepCompact"
     */
    private static String identifyCollectorFamily(List<GarbageCollectorMXBean> gcBeans) {
        StringBuilder names = new StringBuilder();
        for (GarbageCollectorMXBean bean : gcBeans) {
            names.append(bean.getName()).append(' ');
        }
        String joined = names.toString();

        if (joined.contains("G1")) {
            return "==> Active collector family: G1 (Garbage-First) -- matches this demo's Theory chapter.";
        } else if (joined.contains("ZGC")) {
            return "==> Active collector family: ZGC -- launch with -XX:+UseG1GC to see G1 behavior instead.";
        } else if (joined.contains("Shenandoah")) {
            return "==> Active collector family: Shenandoah -- launch with -XX:+UseG1GC to see G1 behavior instead.";
        } else if (joined.contains("PS ") || joined.contains("Parallel")) {
            return "==> Active collector family: Parallel GC -- launch with -XX:+UseG1GC to see G1 behavior instead.";
        } else if (joined.contains("Copy") || joined.contains("MarkSweepCompact")) {
            return "==> Active collector family: Serial GC -- launch with -XX:+UseG1GC to see G1 behavior instead.";
        }
        return "==> Active collector family: unrecognized bean names (" + joined.trim() + ")";
    }

    // -------------------------------------------------------------------
    // 1) Young-gen churn: lots of short-lived objects, sized to force
    //    repeated Eden-fill evacuation pauses under G1.
    // -------------------------------------------------------------------
    private static void runYoungGenChurnWorkload() {
        System.out.println("Allocating short-lived byte[] chunks in batches to fill Eden repeatedly.");
        System.out.println("Under G1 this drives 'Pause Young (Normal) (G1 Evacuation Pause)' events.\n");

        long totalAllocated = 0;
        for (int batch = 1; batch <= 20; batch++) {
            List<byte[]> churn = new ArrayList<>();
            for (int i = 0; i < 2000; i++) {
                byte[] chunk = new byte[4 * 1024]; // 4KB, well under humongous threshold
                chunk[0] = (byte) i;
                churn.add(chunk);
                totalAllocated += chunk.length;
            }
            // churn goes out of scope at the end of each batch iteration -- everything in
            // it becomes garbage almost immediately, the classic generational-hypothesis
            // pattern G1's Young-Only collections are built around.
            churn.clear();
        }
        System.out.printf("Allocated ~%.1fMB total in short-lived batches.%n", totalAllocated / 1e6);
    }

    // -------------------------------------------------------------------
    // 2) Tenuring workload: keep a growing list of objects ALIVE across
    //    many allocation rounds so some get promoted to Old regions,
    //    which is what eventually triggers G1's concurrent marking cycle
    //    (once Old-gen occupancy crosses InitiatingHeapOccupancyPercent)
    //    and, later, Mixed GCs.
    // -------------------------------------------------------------------
    private static void runTenuringWorkload() {
        System.out.println("Keeping a fraction of allocated objects alive across rounds so they age");
        System.out.println("past the tenuring threshold and get promoted into Old regions -- this is");
        System.out.println("what eventually triggers G1's concurrent marking cycle (IHOP) and later");
        System.out.println("Mixed GCs, per Theory File 01.\n");

        List<int[]> survivors = new ArrayList<>();
        for (int round = 1; round <= 15; round++) {
            for (int i = 0; i < 5000; i++) {
                int[] shortLived = new int[64];
                shortLived[0] = i;
                // most of these die immediately (not retained) -- only every 50th
                // array is kept, simulating a realistic mix of garbage and survivors
                if (i % 50 == 0) {
                    survivors.add(shortLived);
                }
            }
            if (round % 5 == 0) {
                System.out.printf("Round %d: retained %,d survivor arrays so far.%n", round, survivors.size());
            }
        }
        System.out.printf("Finished tenuring workload with %,d objects still strongly reachable.%n",
                survivors.size());
        // survivors is a local variable that goes out of scope when this method returns,
        // so all of it becomes eligible for collection afterward too -- but while this
        // method runs, it represents realistic long-lived data pressuring Old-gen.
    }

    // -------------------------------------------------------------------
    // 3) Humongous allocation workload: allocate objects deliberately
    //    sized larger than 50% of a typical G1 region, to demonstrate the
    //    humongous-object allocation path described in Theory File 01.
    //    Run with a small -XX:G1HeapRegionSize (e.g. 1m) to make ordinary
    //    ~600KB arrays qualify as humongous and show up as such in the log.
    // -------------------------------------------------------------------
    private static void runHumongousAllocationWorkload() {
        System.out.println("Allocating large byte[] arrays sized to exceed 50% of a small G1 region");
        System.out.println("(run with -XX:G1HeapRegionSize=1m to make this obviously humongous).\n");

        long totalAllocated = 0;
        for (int i = 0; i < 30; i++) {
            byte[] humongousCandidate = new byte[700 * 1024]; // 700KB > 50% of a 1MB region
            humongousCandidate[0] = 1;
            humongousCandidate[humongousCandidate.length - 1] = 1;
            totalAllocated += humongousCandidate.length;
            // deliberately not retained -- becomes garbage immediately, so repeated runs
            // of this loop also demonstrate humongous-region RECLAMATION, not just allocation
        }
        System.out.printf("Allocated ~%.1fMB across humongous-sized arrays.%n", totalAllocated / 1e6);
    }

    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }
}
