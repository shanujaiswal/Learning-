/*
 * Topic04_GcTuningReporterDemo.java
 *
 * NOTE ON FILENAME: the public class is named "Topic04_GcTuningReporterDemo" (Java
 * identifiers can't start with a digit) while the FILE keeps the "04_..." numeric prefix
 * used throughout this repo for ordering.
 *
 * Compile: javac 04_GcTuningReporterDemo.java
 * Run (works with ANY collector -- this is a generic GC stats reporter utility):
 *          java Topic04_GcTuningReporterDemo
 *
 * Run under each collector to compare real output side by side (this file doesn't pick
 * a collector for you -- see the decision table printed below and in Theory File 04):
 *          java -XX:+UseSerialGC       Topic04_GcTuningReporterDemo
 *          java -XX:+UseParallelGC     Topic04_GcTuningReporterDemo
 *          java -XX:+UseG1GC           Topic04_GcTuningReporterDemo
 *          java -XX:+UseZGC            Topic04_GcTuningReporterDemo
 *          java -XX:+UseShenandoahGC   Topic04_GcTuningReporterDemo   (if your JDK build includes it)
 *
 * Run with unified GC logging enabled (the real ground truth per Theory File 04 -- this
 * program's printed report is a convenient live snapshot, but a log file is what you'd
 * actually feed to GCViewer/GCEasy/JMC for serious tuning work):
 *          java -Xlog:gc*:file=gc-report.log:time,uptime,level,tags Topic04_GcTuningReporterDemo
 *
 * Covers Theory chapter:
 *   07) JVM Internals and Memory Management/Theory/09 Choosing and Tuning a Garbage Collector.md
 *
 * WHAT THIS FILE ACTUALLY DOES (genuinely runnable, regardless of which collector is
 * configured):
 *   1. Enumerates every GarbageCollectorMXBean and reports its name, collection count,
 *      and cumulative collection time.
 *   2. Reads MemoryMXBean heap/non-heap usage before and after a deliberate allocation
 *      burst, so you can see real occupancy numbers change.
 *   3. Prints the collector-choice decision table and per-collector tuning-flag quick
 *      reference from Theory File 04, as reference material alongside the live data.
 *   4. Prints a short guide to reading `-Xlog:gc*` output, matching Theory File 04's
 *      "How to Read GC Logs" section.
 */

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.List;

public class Topic04_GcTuningReporterDemo {

    public static void main(String[] args) throws Exception {
        printSection("1) Full GC Report -- BEFORE allocation burst");
        printFullGcReport();

        printSection("2) Forced Allocation Burst (to generate real GC activity to report on)");
        runAllocationBurst();

        printSection("3) Full GC Report -- AFTER allocation burst (compare counts/times/heap)");
        printFullGcReport();

        printSection("4) Reference: Collector Decision Table (Theory File 04)");
        printDecisionTable();

        printSection("5) Reference: Per-Collector Tuning Flags Quick Reference");
        printTuningFlagsReference();

        printSection("6) Reference: How to Read GC Logs (-Xlog:gc*)");
        printGcLogReadingGuide();

        System.out.println("\nGC tuning reporter finished.");
    }

    // =====================================================================
    // 1) / 3) THE ACTUAL RUNNABLE UTILITY: a full GC stats report using
    //    GarbageCollectorMXBean + MemoryMXBean + MemoryPoolMXBean.
    // =====================================================================
    private static void printFullGcReport() {
        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        System.out.println("-- Garbage Collector Beans --");
        long totalCollections = 0;
        long totalTimeMs = 0;
        for (GarbageCollectorMXBean bean : gcBeans) {
            long count = bean.getCollectionCount();
            long time = bean.getCollectionTime();
            if (count >= 0) totalCollections += count;
            if (time >= 0) totalTimeMs += time;
            System.out.printf("  %-28s count=%-8d totalTime=%-8dms pools=[%s]%n",
                    bean.getName(), count, time, String.join(", ", bean.getMemoryPoolNames()));
        }
        System.out.printf("  TOTAL across all beans: collections=%d, time=%dms%n", totalCollections, totalTimeMs);

        System.out.println("\n-- Heap / Non-Heap Memory Usage --");
        MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
        printUsage("Heap    ", memBean.getHeapMemoryUsage());
        printUsage("NonHeap ", memBean.getNonHeapMemoryUsage());

        System.out.println("\n-- Individual Memory Pools --");
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            MemoryUsage usage = pool.getUsage();
            if (usage == null) continue; // some pools don't support usage snapshots
            System.out.printf("  %-30s type=%-9s used=%8.1fMB  max=%8.1fMB%n",
                    pool.getName(), pool.getType(), usage.getUsed() / 1e6,
                    usage.getMax() < 0 ? -1 : usage.getMax() / 1e6);
        }

        System.out.println("\n-- Active Collector Family (inferred from bean names) --");
        System.out.println("  " + identifyCollectorFamily(gcBeans));
    }

    private static void printUsage(String label, MemoryUsage usage) {
        System.out.printf("  %s: init=%.1fMB used=%.1fMB committed=%.1fMB max=%s%n",
                label, usage.getInit() / 1e6, usage.getUsed() / 1e6, usage.getCommitted() / 1e6,
                usage.getMax() < 0 ? "undefined" : String.format("%.1fMB", usage.getMax() / 1e6));
    }

    private static String identifyCollectorFamily(List<GarbageCollectorMXBean> gcBeans) {
        StringBuilder names = new StringBuilder();
        for (GarbageCollectorMXBean bean : gcBeans) {
            names.append(bean.getName()).append(' ');
        }
        String joined = names.toString();

        if (joined.contains("G1")) return "G1 (Garbage-First)";
        if (joined.contains("ZGC")) return "ZGC" + (joined.contains("Cycles") ? " (generational, JDK 21+)" : "");
        if (joined.contains("Shenandoah")) return "Shenandoah";
        if (joined.contains("PS ") || joined.contains("Parallel")) return "Parallel GC";
        if (joined.contains("Copy") || joined.contains("MarkSweepCompact")) return "Serial GC";
        return "Unrecognized (" + joined.trim() + ")";
    }

    // =====================================================================
    // 2) A forced allocation burst -- deliberately allocates and discards a
    //    large volume of objects of varying sizes so that whatever collector
    //    is active has real work to do, and the "AFTER" report above shows
    //    non-zero deltas in collection count/time versus the "BEFORE" report.
    // =====================================================================
    private static void runAllocationBurst() {
        System.out.println("Running a mixed allocation burst (small churn + retained mid-size objects)");
        System.out.println("so the next report reflects real GC activity, not just idle baseline.\n");

        long totalAllocated = 0;

        // Short-lived churn
        for (int batch = 1; batch <= 15; batch++) {
            List<byte[]> churn = new ArrayList<>();
            for (int i = 0; i < 3000; i++) {
                byte[] chunk = new byte[4 * 1024];
                churn.add(chunk);
                totalAllocated += chunk.length;
            }
            churn.clear();
        }

        // Some retained mid-size objects to pressure Old-gen / long-lived pools too
        List<double[]> retained = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            double[] arr = new double[1000];
            retained.add(arr);
            totalAllocated += arr.length * 8L;
        }

        System.out.printf("Allocation burst complete: ~%.1fMB allocated (%,d objects retained).%n",
                totalAllocated / 1e6, retained.size());
    }

    // =====================================================================
    // 4) Decision table from Theory File 04, "Decision Table: Which Collector"
    // =====================================================================
    private static void printDecisionTable() {
        System.out.println(String.join("\n",
            "Collector    Heap Size          Pause Target              Throughput    Best Fit",
            "-----------  -----------------  ------------------------  ------------  ------------------------------",
            "Serial       < ~256MB-512MB     N/A (scales w/ heap,       Low           Tiny CLI tools, small containers,",
            "                                can be long)                             single-core envs, tests",
            "",
            "Parallel     Small-Large (any)  No goal -- scales w/ heap  HIGHEST       Batch jobs, offline ETL, data",
            "                                                                         pipelines -- total runtime matters",
            "",
            "G1           Moderate-Large     Soft goal via              High          DEFAULT CHOICE for most server",
            "             (~4GB - 100s GB)   -XX:MaxGCPauseMillis                     apps -- balanced throughput +",
            "                                (default 200ms)                          predictable pause bounds",
            "",
            "ZGC          Large-Huge         Sub-millisecond,           Medium-High   Huge heaps + hard low-latency",
            "             (few GB - TB)      heap-size-independent      (Gen ZGC      SLAs: trading, real-time bidding,",
            "                                                           closes gap)   large in-memory caches",
            "",
            "Shenandoah   Large-Huge         Sub-to-low-double-digit    Medium-High   Same niche as ZGC -- choice is",
            "             (few GB - TB)      ms, heap-size-independent               usually a JDK-distribution/",
            "                                                                         availability question"
        ));

        System.out.println("\nSimple decision flow (Theory File 04):");
        System.out.println(String.join("\n",
            "  Short-lived CLI tool / tiny container / test?      --> Serial GC",
            "  ONLY total throughput matters (batch/offline)?     --> Parallel GC",
            "  Huge heap AND hard sub-10ms pause requirement",
            "    that survives G1 tuning attempts?                --> ZGC or Shenandoah",
            "                                                          (pick by JDK distribution/availability)",
            "  Default (most server apps)                         --> G1"
        ));
    }

    // =====================================================================
    // 5) Per-collector tuning flags, from Theory File 04
    // =====================================================================
    private static void printTuningFlagsReference() {
        System.out.println(String.join("\n",
            "COMMON (all collectors)",
            "  -Xms<size>                       Initial heap size",
            "  -Xmx<size>                        Max heap size (recommend -Xms == -Xmx in production)",
            "  -XX:MaxMetaspaceSize=<size>       Cap Metaspace growth",
            "  -Xlog:gc*                         Unified GC logging (Java 9+) -- always enable in prod",
            "",
            "SERIAL",
            "  -XX:+UseSerialGC                  Select Serial GC (minimal further tuning surface)",
            "",
            "PARALLEL",
            "  -XX:+UseParallelGC                Select Parallel GC",
            "  -XX:ParallelGCThreads=<n>         Worker thread count for STW collection",
            "  -XX:GCTimeRatio=<n>               Target ratio of app time : GC time",
            "  -XX:MaxGCPauseMillis=<ms>         Best-effort pause goal (secondary to throughput here)",
            "",
            "G1",
            "  -XX:+UseG1GC                      Select G1 (default already on modern JDKs)",
            "  -XX:MaxGCPauseMillis=<ms>         Soft pause-time goal (default 200)",
            "  -XX:G1HeapRegionSize=<size>       Region size (1m-32m, power of 2)",
            "  -XX:InitiatingHeapOccupancyPercent=<n>  Old-gen occupancy that triggers marking (default 45)",
            "  -XX:G1MixedGCCountTarget=<n>      Spread Old-gen reclamation across N Mixed GCs",
            "  -XX:ConcGCThreads / -XX:ParallelGCThreads  Concurrent vs STW-phase thread counts",
            "",
            "ZGC",
            "  -XX:+UseZGC                       Select ZGC (generational by default on JDK 21+,",
            "                                     the only mode from JDK 23+)",
            "  -XX:ConcGCThreads=<n>              Concurrent worker threads",
            "  -XX:SoftMaxHeapSize=<size>         Soft cap below -Xmx",
            "  -XX:ZAllocationSpikeTolerance=<n>  Headroom planning for allocation-rate spikes",
            "",
            "SHENANDOAH",
            "  -XX:+UseShenandoahGC               Select Shenandoah (requires a JDK build that includes it)",
            "  -XX:ShenandoahGCHeuristics=<mode>  adaptive (default) / static / compact / aggressive",
            "  -XX:ConcGCThreads=<n>               Concurrent worker threads",
            "  -XX:ShenandoahGarbageThreshold=<n>  Garbage % for a region to become evacuation candidate"
        ));
    }

    // =====================================================================
    // 6) How to read GC logs, from Theory File 04, "How to Read GC Logs"
    // =====================================================================
    private static void printGcLogReadingGuide() {
        System.out.println("Enable unified GC logging (Java 9+), the ground truth for tuning decisions:");
        System.out.println("  -Xlog:gc*:file=gc.log:time,uptime,level,tags");
        System.out.println();
        System.out.println("What to look for regardless of collector:");
        System.out.println(String.join("\n",
            "  1. Pause TYPE      -- Minor/Young-only vs Mixed vs Full GC. Full GC (or G1",
            "                        'evacuation failure'/'to-space exhausted') is the biggest",
            "                        red flag -- the concurrent/incremental scheme fell behind.",
            "  2. Pause DURATION  -- compare against your latency budget; watch p99/max, not mean.",
            "  3. Pause FREQUENCY -- rising frequency + shrinking effectiveness often signals a leak.",
            "  4. Heap occupancy  -- Old-gen (or overall for ZGC/Shenandoah) trend before/after;",
            "                        climbing and never coming back down is a leak signature.",
            "  5. Allocation rate -- distinguishes 'allocates a lot' from 'leaks'.",
            "  6. Concurrent cycle timing -- (G1/ZGC/Shenandoah) are concurrent phases finishing",
            "                        well before the next cycle is needed?"
        ));
        System.out.println();
        System.out.println("G1 log example shape:");
        System.out.println("  [gc,start ] GC(42) Pause Young (Normal) (G1 Evacuation Pause)");
        System.out.println("  [gc,heap  ] GC(42) Eden regions: 42->0(45)");
        System.out.println("  [gc       ] GC(42) Pause Young (Normal) (G1 Evacuation Pause) 512M->498M(1024M) 18.421ms");
        System.out.println();
        System.out.println("ZGC/Shenandoah log example shape -- look for named phases:");
        System.out.println("  ZGC:        Pause Mark Start, Concurrent Mark, Pause Mark End, Concurrent Relocate");
        System.out.println("  Shenandoah: Init Mark, Concurrent Marking, Final Mark, Concurrent Evacuation,");
        System.out.println("              Concurrent Update References");
        System.out.println();
        System.out.println("Prefer JFR (-XX:StartFlightRecording) over pure text logs for serious/production");
        System.out.println("investigation -- lower overhead, richer structured data -- but keep -Xlog:gc* on");
        System.out.println("too as a cheap, always-available baseline trail.");
    }

    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }
}
