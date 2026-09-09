/*
 * Topic03_ShenandoahDemo.java
 *
 * NOTE ON FILENAME: the public class is named "Topic03_ShenandoahDemo" (Java identifiers
 * can't start with a digit) while the FILE keeps the "03_..." numeric prefix used
 * throughout this repo for ordering.
 *
 * Compile: javac 03_ShenandoahDemo.java
 * Run (default collector on your JDK):
 *          java Topic03_ShenandoahDemo
 *
 * Run EXPLICITLY selecting Shenandoah (requires a JDK BUILD that includes it -- e.g.
 * Eclipse Temurin, Red Hat builds; historically NOT bundled in some Oracle JDK builds.
 * Passing this flag on a build without Shenandoah fails to START rather than falling
 * back silently -- see Theory File 03 "Gotchas"):
 *          java -XX:+UseShenandoahGC -Xlog:gc*:file=gc-shenandoah.log:time,uptime,level,tags Topic03_ShenandoahDemo
 *
 * Run with the adaptive heuristic made explicit, plus generous heap headroom (Shenandoah,
 * like ZGC, wants room for concurrent evacuation -- see Theory File 03):
 *          java -XX:+UseShenandoahGC -XX:ShenandoahGCHeuristics=adaptive -Xms512m -Xmx512m ^
 *               -Xlog:gc*:file=gc-shenandoah-adaptive.log:time,uptime,level,tags Topic03_ShenandoahDemo
 *
 * Run with the "compact" heuristic instead, which trades some pause-time consistency for
 * a smaller memory footprint -- useful to compare against adaptive in the logs:
 *          java -XX:+UseShenandoahGC -XX:ShenandoahGCHeuristics=compact -Xmx256m ^
 *               -Xlog:gc*:file=gc-shenandoah-compact.log:time,uptime,level,tags Topic03_ShenandoahDemo
 *
 * What you should see in the log with Shenandoah (see Theory File 03):
 *   - Named phases: "Init Mark", "Concurrent marking", "Final Mark",
 *     "Concurrent evacuation", "Init Update Refs", "Concurrent update references",
 *     "Final Update Refs"
 *   - The "Init ..." / "Final ..." STW phases should stay short and largely
 *     independent of live-set size, the same structural property ZGC has, just
 *     achieved via Brooks (forwarding) pointers instead of colored pointers
 *   - Region selection favoring the most garbage-heavy regions first, similar in
 *     spirit to G1's "garbage-first" heuristic
 *
 * Covers Theory chapter:
 *   07) JVM Internals and Memory Management/Theory/08 Shenandoah GC Deep Dive.md
 *
 * IMPORTANT CAVEAT: which collector actually RUNS is decided entirely by JVM flags at
 * launch time (and Shenandoah must be present in the JDK build at all). This program
 * cannot force Shenandoah on from inside main() -- what it CAN do is (a) genuinely
 * inspect and report which collector is active via java.lang.management, and (b) run
 * an allocation-heavy workload shaped to make concurrent evacuation visible in the logs
 * when the right flags/build are used to launch it.
 */

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.List;

public class Topic03_ShenandoahDemo {

    public static void main(String[] args) throws Exception {
        printSection("0) Active GC Configuration (java.lang.management)");
        reportActiveGarbageCollectors();

        printSection("1) Allocation-Heavy Workload -- Mixed Short-Lived Object Churn");
        runMixedChurnWorkload();

        printSection("2) Allocation-Heavy Workload -- Mutation-Under-Evacuation Pattern");
        runMutateWhileLiveWorkload();

        printSection("3) Active GC Configuration -- AFTER the workload (compare counts/times)");
        reportActiveGarbageCollectors();

        System.out.println("\nAll Shenandoah demo workloads completed.");
        System.out.println("Re-run with -XX:+UseShenandoahGC -Xlog:gc* (see flags in the header comment,");
        System.out.println("and confirm your JDK build actually includes Shenandoah) to observe the");
        System.out.println("Init Mark / Concurrent Marking / Final Mark / Concurrent Evacuation /");
        System.out.println("Update References cycle described in Theory File 03.");
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
     *   Shenandoah: "Shenandoah Cycles" / "Shenandoah Pauses"
     *   ZGC:        "ZGC" (or "ZGC Cycles" / "ZGC Pauses" on generational ZGC)
     *   G1:         "G1 Young Generation", "G1 Old Generation"
     *   Parallel:   "PS Scavenge", "PS MarkSweep"
     *   Serial:     "Copy", "MarkSweepCompact"
     * If -XX:+UseShenandoahGC is passed on a JDK build that lacks Shenandoah, the JVM
     * fails to start at all -- so if this program is running, and you passed that flag,
     * Shenandoah bean names ARE what you should expect to see here.
     */
    private static String identifyCollectorFamily(List<GarbageCollectorMXBean> gcBeans) {
        StringBuilder names = new StringBuilder();
        for (GarbageCollectorMXBean bean : gcBeans) {
            names.append(bean.getName()).append(' ');
        }
        String joined = names.toString();

        if (joined.contains("Shenandoah")) {
            return "==> Active collector family: Shenandoah -- matches this demo's Theory chapter.";
        } else if (joined.contains("ZGC")) {
            return "==> Active collector family: ZGC -- launch with -XX:+UseShenandoahGC to see Shenandoah instead.";
        } else if (joined.contains("G1")) {
            return "==> Active collector family: G1 -- launch with -XX:+UseShenandoahGC to see Shenandoah instead.";
        } else if (joined.contains("PS ") || joined.contains("Parallel")) {
            return "==> Active collector family: Parallel GC -- launch with -XX:+UseShenandoahGC to see Shenandoah instead.";
        } else if (joined.contains("Copy") || joined.contains("MarkSweepCompact")) {
            return "==> Active collector family: Serial GC -- launch with -XX:+UseShenandoahGC to see Shenandoah instead.";
        }
        return "==> Active collector family: unrecognized bean names (" + joined.trim() + ")";
    }

    // -------------------------------------------------------------------
    // 1) Mixed short-lived churn -- generic allocation pressure to trigger
    //    Shenandoah's concurrent marking + evacuation cycle repeatedly.
    // -------------------------------------------------------------------
    private static void runMixedChurnWorkload() {
        System.out.println("Allocating a mix of small object types in batches to generate steady");
        System.out.println("garbage and pressure Shenandoah's region selection (most-garbage-first,");
        System.out.println("similar in spirit to G1's heuristic -- see Theory File 03).\n");

        long totalAllocated = 0;
        for (int batch = 1; batch <= 20; batch++) {
            List<Object> churn = new ArrayList<>();
            for (int i = 0; i < 2000; i++) {
                if (i % 2 == 0) {
                    byte[] chunk = new byte[4 * 1024];
                    churn.add(chunk);
                    totalAllocated += chunk.length;
                } else {
                    String s = "shenandoah-demo-string-" + i + "-batch-" + batch;
                    churn.add(s);
                    totalAllocated += s.length() * 2L; // rough char footprint
                }
            }
            churn.clear(); // everything above becomes garbage almost immediately
        }
        System.out.printf("Allocated ~%.1fMB total in mixed short-lived churn.%n", totalAllocated / 1e6);
    }

    // -------------------------------------------------------------------
    // 2) Mutation-under-evacuation pattern -- repeatedly MUTATE fields on
    //    objects that are retained across the loop, while also allocating.
    //    This exercises the READ and WRITE barrier cooperation Shenandoah
    //    relies on to keep concurrent evacuation correct when the app
    //    mutates an object that's mid-copy -- see Theory File 03,
    //    "Concurrent Evacuation in Practice".
    // -------------------------------------------------------------------
    private static void runMutateWhileLiveWorkload() {
        System.out.println("Retaining objects across rounds AND repeatedly mutating their fields --");
        System.out.println("this is the pattern that exercises Shenandoah's read/write barrier");
        System.out.println("cooperation during concurrent evacuation, per Theory File 03.\n");

        List<int[]> mutable = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            mutable.add(new int[]{0});
        }

        for (int round = 1; round <= 200_000; round++) {
            // Mutate a pseudo-random retained object every round -- simulates an app
            // actively writing to long-lived objects while the GC may be evacuating them.
            int idx = round % mutable.size();
            mutable.get(idx)[0]++;

            // Also allocate some fresh short-lived garbage alongside the mutation traffic.
            if (round % 1000 == 0) {
                byte[] transientBuf = new byte[2 * 1024];
                transientBuf[0] = 1; // touch it so it's not trivially dead-code-eliminated
            }
        }

        long sum = 0;
        for (int[] arr : mutable) {
            sum += arr[0];
        }
        System.out.printf("Finished mutation workload -- %,d retained objects, mutation checksum=%,d.%n",
                mutable.size(), sum);
    }

    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }
}
