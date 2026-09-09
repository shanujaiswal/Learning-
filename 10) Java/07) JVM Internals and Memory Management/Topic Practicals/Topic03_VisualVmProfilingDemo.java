/*
 * Topic03_VisualVmProfilingDemo.java
 *
 * NOTE ON FILENAME: the public class is named "Topic03_VisualVmProfilingDemo" (Java
 * identifiers can't start with a digit) while the FILE keeps the "03_..." numeric
 * prefix used throughout this repo for ordering.
 *
 * Compile: javac 03_VisualVmProfilingDemo.java
 * Run:     java Topic03_VisualVmProfilingDemo
 *
 * Demonstrates:
 *   1. A genuine, reproducible DEADLOCK between two threads, detected programmatically
 *      via ThreadMXBean.findDeadlockedThreads() -- the same mechanism behind JConsole's/
 *      VisualVM's "Detect Deadlock" button (Theory Files 01 and 03)
 *   2. A full thread dump (state + stack trace per thread), the same data `jstack`/
 *      VisualVM's Threads tab shows
 *   3. A heap-dump-worthy allocation workload -- retained vs transient allocations, so a
 *      heap dump taken mid-run (externally, via jmap/VisualVM/jcmd) would show a clear,
 *      explainable growth pattern if compared against a baseline dump
 *   4. Comments on VisualVM's Sampler/Profiler tabs and async-profiler usage (external
 *      tools -- not something this in-process demo can drive itself)
 *
 * Covers Theory chapter:
 *   07) JVM Internals and Memory Management/Theory/12 VisualVM and Profiling Tools Overview.md
 */

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class Topic03_VisualVmProfilingDemo {

    // Two locks, deliberately acquired in opposite order by two threads -- the textbook
    // deadlock signature described in Theory File 03: "each thread BLOCKED waiting for
    // a lock the OTHER one holds".
    private static final Object LOCK_A = new Object();
    private static final Object LOCK_B = new Object();

    public static void main(String[] args) throws Exception {
        printSection("1) Producing a Real Deadlock Between Two Threads");
        Thread t1 = startDeadlockThread("DeadlockThread-A", LOCK_A, LOCK_B);
        Thread t2 = startDeadlockThread("DeadlockThread-B", LOCK_B, LOCK_A);

        // Give both threads time to each grab their first lock and then block on the second.
        Thread.sleep(1000);

        printSection("2) Detecting the Deadlock via ThreadMXBean.findDeadlockedThreads()");
        detectAndReportDeadlock();

        printSection("3) Full Thread Dump (state + stack trace per thread)");
        printFullThreadDump();

        printSection("4) Heap-Dump-Worthy Allocation Workload");
        runAllocationWorkloadForHeapDump();

        printVisualVmAndAsyncProfilerNotes();

        System.out.println("\nNote: DeadlockThread-A and DeadlockThread-B are left permanently blocked --");
        System.out.println("that's an intrinsic property of a real deadlock (neither can ever proceed on");
        System.out.println("its own). This demo calls System.exit(0) below so the JVM doesn't hang forever.");
        System.out.println("All VisualVM/profiling demos completed.");
        System.exit(0);
    }

    // -------------------------------------------------------------------
    // 1) Produce a genuine deadlock
    // -------------------------------------------------------------------
    private static Thread startDeadlockThread(String name, Object firstLock, Object secondLock) {
        Thread t = new Thread(() -> {
            synchronized (firstLock) {
                System.out.println(Thread.currentThread().getName() + " acquired its first lock, "
                        + "now trying for the second (held by the other thread)...");
                try {
                    Thread.sleep(200); // widen the window so the other thread also grabs its first lock
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                synchronized (secondLock) {
                    // Unreachable in this demo -- a true deadlock never gets here.
                    System.out.println(Thread.currentThread().getName() + " acquired both locks (unexpected!)");
                }
            }
        }, name);
        t.setDaemon(true); // daemon so a stuck thread doesn't itself prevent JVM exit via System.exit
        t.start();
        return t;
    }

    // -------------------------------------------------------------------
    // 2) Detect it -- the exact API behind JConsole's/VisualVM's "Detect Deadlock" button
    // -------------------------------------------------------------------
    private static void detectAndReportDeadlock() {
        ThreadMXBean threadMxBean = ManagementFactory.getThreadMXBean();

        // findDeadlockedThreads() checks for cycles among threads BLOCKED on monitor locks
        // (synchronized). A related method, findMonitorDeadlockedThreads(), is an older,
        // narrower variant; findDeadlockedThreads() also covers java.util.concurrent locks.
        long[] deadlockedIds = threadMxBean.findDeadlockedThreads();

        if (deadlockedIds == null) {
            System.out.println("No deadlock detected (unexpected for this demo -- the sleep in main() may");
            System.out.println("need to be longer on a very slow machine).");
            return;
        }

        System.out.println("Found " + deadlockedIds.length + " deadlocked thread(s):");
        ThreadInfo[] infos = threadMxBean.getThreadInfo(deadlockedIds, true, true);
        for (ThreadInfo info : infos) {
            System.out.println("  Thread \"" + info.getThreadName() + "\" is " + info.getThreadState()
                    + " waiting to lock " + info.getLockName()
                    + " which is held by \"" + info.getLockOwnerName() + "\"");
        }
        System.out.println("\nThis mirrors jstack's/VisualVM's classic \"Found one Java-level deadlock\"");
        System.out.println("report -- two threads, each BLOCKED on a lock the other one holds.");
    }

    // -------------------------------------------------------------------
    // 3) Full thread dump -- same data jstack produces
    // -------------------------------------------------------------------
    private static void printFullThreadDump() {
        ThreadMXBean threadMxBean = ManagementFactory.getThreadMXBean();
        // getThreadInfo(ids, maxDepth) with a generous stack depth for ALL live threads --
        // dumpAllThreads(lockedMonitors, lockedSynchronizers) is the more complete variant,
        // matching what `jstack <pid>` prints.
        ThreadInfo[] allThreads = threadMxBean.dumpAllThreads(true, true);

        System.out.println("Live thread count: " + allThreads.length + "\n");
        for (ThreadInfo info : allThreads) {
            System.out.println("\"" + info.getThreadName() + "\" (id=" + info.getThreadId()
                    + ") state=" + info.getThreadState());
            StackTraceElement[] stack = info.getStackTrace();
            int framesToShow = Math.min(3, stack.length); // keep output readable -- jstack shows all
            for (int i = 0; i < framesToShow; i++) {
                System.out.println("    at " + stack[i]);
            }
            if (stack.length > framesToShow) {
                System.out.println("    ... (" + (stack.length - framesToShow) + " more frames)");
            }
            System.out.println();
        }
        System.out.println("Reading thread states (Theory File 03): RUNNABLE=executing/ready, BLOCKED=");
        System.out.println("waiting on a monitor another thread holds, WAITING/TIMED_WAITING=parked on");
        System.out.println("wait()/join()/park()/sleep, TERMINATED=finished.");
    }

    // -------------------------------------------------------------------
    // 4) Allocation workload shaped for heap dump analysis
    // -------------------------------------------------------------------
    private static void runAllocationWorkloadForHeapDump() throws InterruptedException {
        System.out.println("Allocating a RETAINED list (survives the whole method) plus a large amount of");
        System.out.println("TRANSIENT short-lived garbage -- a heap dump taken now (jmap/VisualVM/jcmd,");
        System.out.println("externally, against this process's PID) would show 'retained' as a genuine");
        System.out.println("live-object histogram entry, distinguishable from the discarded 'transient' churn.\n");

        List<byte[]> retained = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            retained.add(new byte[8 * 1024]); // ~4MB total, stays reachable via 'retained'
        }
        System.out.println("Retained ~" + (retained.size() * 8) + "KB across " + retained.size() + " byte[] instances.");

        long transientTotal = 0;
        for (int batch = 0; batch < 50; batch++) {
            List<String> churn = new ArrayList<>();
            for (int i = 0; i < 2000; i++) {
                churn.add("transient-string-" + batch + "-" + i);
                transientTotal++;
            }
            // churn goes out of scope every batch -- immediate garbage, unlike 'retained' above.
        }
        System.out.printf("Allocated and discarded %,d transient String instances across 50 batches.%n", transientTotal);

        System.out.println("\nWhile this process is running, try (Theory File 03 workflow):");
        System.out.println("  jmap -dump:live,format=b,file=before.hprof <pid>   (baseline)");
        System.out.println("  ... let more of this workload run ...");
        System.out.println("  jmap -dump:live,format=b,file=after.hprof <pid>    (second snapshot)");
        System.out.println("Then open both in VisualVM and use 'Compare to another heap dump' -- 'retained'");
        System.out.println("shows up as genuine net growth; the transient churn does not, since it's already");
        System.out.println("garbage by the time either dump is taken.");

        // Keep 'retained' alive a moment longer so an external tool has time to attach if desired.
        Thread.sleep(200);
        System.out.println("(retained.size() at end = " + retained.size() + " -- still reachable here.)");
    }

    // -------------------------------------------------------------------
    private static void printVisualVmAndAsyncProfilerNotes() {
        printSection("5) VisualVM Sampler/Profiler and async-profiler (Notes)");

        // VisualVM (separate download since JDK 9, visualvm.github.io) attaches to a local
        // or remote JVM process the same way jconsole does. Once attached to THIS process:
        //   Monitor tab    -- live CPU/heap/metaspace/class/thread graphs (JMX-based),
        //                      plus buttons for "Perform GC" and "Heap Dump" on demand.
        //   Threads tab    -- color-coded live timeline of every thread's state; the
        //                      deadlock produced above would show DeadlockThread-A/B
        //                      permanently BLOCKED (same red state) side by side.
        //   Sampler tab    -- low-overhead CPU/memory SAMPLING, safe for extended sessions;
        //                      start broad here first.
        //   Profiler tab   -- higher-overhead INSTRUMENTING profiler for a short, targeted
        //                      session once the Sampler has narrowed down a suspect area.
        System.out.println("VisualVM (attach to this process's PID while it runs, or right after a dump):");
        System.out.println("  Monitor tab  -- live CPU/heap/class/thread graphs; GC + heap dump buttons.");
        System.out.println("  Threads tab  -- color-coded timeline; the two deadlocked threads above would");
        System.out.println("                 show as permanently BLOCKED (same colour) side by side.");
        System.out.println("  Sampler tab  -- low-overhead sampling profiler; start broad here FIRST.");
        System.out.println("  Profiler tab -- higher-overhead instrumenting profiler; narrow, targeted follow-up.");

        // async-profiler (Linux-focused, CLI/agent) samples via AsyncGetCallTrace/perf_events
        // rather than only at JVM safepoints, so it captures time inside tight JIT-compiled
        // loops and native frames more accurately. Typical invocation against a running PID:
        //   ./profiler.sh -d 30 -f flamegraph.html <pid>          # 30s CPU profile -> flame graph
        //   ./profiler.sh -e alloc -d 30 -f alloc.html <pid>      # allocation profiling
        //   ./profiler.sh -e lock -d 30 -f locks.html <pid>       # lock-contention profiling
        System.out.println("\nasync-profiler (Linux, CLI/agent, de facto standard for prod CPU profiling):");
        System.out.println("  ./profiler.sh -d 30 -f flamegraph.html <pid>        (CPU, 30s, flame graph)");
        System.out.println("  ./profiler.sh -e alloc -d 30 -f alloc.html <pid>    (allocation profiling)");
        System.out.println("  ./profiler.sh -e lock  -d 30 -f locks.html <pid>    (lock-contention profiling)");
        System.out.println("Flame graphs: read WIDTH (share of sampled time), not depth, to find hotspots.");
    }

    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }
}
