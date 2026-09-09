/*
 * Topic01_JmxFundamentalsDemo.java
 *
 * NOTE ON FILENAME: the public class is named "Topic01_JmxFundamentalsDemo" (Java
 * identifiers can't start with a digit) while the FILE keeps the "01_..." numeric
 * prefix used throughout this repo for ordering.
 *
 * Compile: javac 01_JmxFundamentalsDemo.java
 * Run:     java Topic01_JmxFundamentalsDemo
 *
 * Demonstrates:
 *   1. ManagementFactory.getPlatformMBeanServer() -- the single per-JVM MBean registry
 *   2. Defining and registering a custom MXBean (CacheStatsMXBean / CacheStats), exactly
 *      following the "suffix MXBean is the contract" convention from Theory File 01
 *   3. Reading a custom MBean's attributes and invoking an operation through the
 *      MBeanServer's generic reflection-style API (getAttribute/invoke by ObjectName) --
 *      this is the same mechanism JConsole/VisualVM use under the hood, just without a GUI
 *   4. Reading a PLATFORM MXBean (ThreadMXBean) both directly and via the generic
 *      MBeanServer API, to show they are the exact same object either way
 *   5. Comments on JConsole usage and remote JMX connector flags (no code needed --
 *      those are JVM launch flags / external tool usage, not in-process API calls)
 *
 * Covers Theory chapter:
 *   07) JVM Internals and Memory Management/Theory/10 JMX Java Management Extensions Fundamentals.md
 */

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.atomic.AtomicLong;
import javax.management.Attribute;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;

public class Topic01_JmxFundamentalsDemo {

    public static void main(String[] args) throws Exception {
        printSection("0) The Platform MBean Server -- One Per JVM");
        MBeanServer mbs = explainPlatformMBeanServer();

        printSection("1) Defining and Registering a Custom MXBean");
        ObjectName cacheName = registerCustomMxBean(mbs);

        printSection("2) Reading Custom MBean Attributes via the Generic MBeanServer API");
        readCustomMxBeanGenerically(mbs, cacheName);

        printSection("3) Invoking an MBean Operation Generically (resetStats)");
        invokeOperationGenerically(mbs, cacheName);

        printSection("4) Platform MXBeans Are Ordinary MBeans Too (ThreadMXBean)");
        compareDirectVsGenericPlatformMxBeanAccess(mbs);

        printSection("5) Cleanup -- Always Unregister Short-Lived MBeans");
        mbs.unregisterMBean(cacheName);
        System.out.println("Unregistered " + cacheName + " -- if this were skipped, the MBean Server");
        System.out.println("would keep a strong reference to 'bean' forever (a leak pattern in itself).");

        printJConsoleAndRemoteNotes();

        System.out.println("\nAll JMX fundamentals demos completed.");
    }

    // -------------------------------------------------------------------
    // 0) ManagementFactory.getPlatformMBeanServer()
    // -------------------------------------------------------------------
    private static MBeanServer explainPlatformMBeanServer() {
        // There is exactly ONE platform MBean server per JVM. Calling this method twice
        // returns the SAME instance -- it's created lazily on first call and cached.
        // Every Platform MBean (Memory, Threading, ClassLoading, GarbageCollector, ...)
        // is already registered here before this line ever runs.
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        System.out.println("MBeanServer default domain: " + mbs.getDefaultDomain());
        System.out.println("MBeans currently registered: " + mbs.getMBeanCount());
        System.out.println("(This count includes every built-in Platform MBean: Memory, Threading,");
        System.out.println("ClassLoading, each GarbageCollector, OperatingSystem, Runtime, Compilation...)");
        return mbs;
    }

    // -------------------------------------------------------------------
    // 1) Custom MXBean -- interface suffix "MXBean" is the auto-detection contract
    // -------------------------------------------------------------------

    /** Management interface -- the "MXBean" suffix is mandatory for JMX to recognize this as an MXBean. */
    public interface CacheStatsMXBean {
        long getHitCount();
        long getMissCount();
        double getHitRatio();
        void resetStats(); // any non-getter/setter public method becomes an invokable "operation"
    }

    /** Implementation -- registered directly with the MBean server, no annotations needed. */
    public static class CacheStats implements CacheStatsMXBean {
        private final AtomicLong hits = new AtomicLong();
        private final AtomicLong misses = new AtomicLong();

        @Override
        public long getHitCount() { return hits.get(); }

        @Override
        public long getMissCount() { return misses.get(); }

        @Override
        public double getHitRatio() {
            long h = hits.get(), m = misses.get();
            return (h + m == 0) ? 0.0 : (double) h / (h + m);
        }

        @Override
        public void resetStats() {
            hits.set(0);
            misses.set(0);
        }

        void recordHit()  { hits.incrementAndGet(); }
        void recordMiss() { misses.incrementAndGet(); }
    }

    private static ObjectName registerCustomMxBean(MBeanServer mbs) throws Exception {
        // ObjectName structure: domain:key1=value1,key2=value2,...
        // The domain namespaces this bean away from java.lang/java.nio/etc.; key=value
        // pairs let a client filter/group multiple beans of the same "type".
        ObjectName name = new ObjectName("com.study.jvmtools:type=CacheStats,name=demoCache");
        CacheStats bean = new CacheStats();

        // Simulate some traffic before registering, just so the numbers aren't all zero.
        for (int i = 0; i < 7; i++) bean.recordHit();
        for (int i = 0; i < 3; i++) bean.recordMiss();

        mbs.registerMBean(bean, name);
        System.out.println("Registered custom MXBean at ObjectName: " + name);

        MBeanInfo info = mbs.getMBeanInfo(name);
        System.out.println("Attributes exposed: " + info.getAttributes().length
                + ", Operations exposed: " + info.getOperations().length);
        System.out.println("(getHitCount/getMissCount/getHitRatio -> 3 read-only attributes;");
        System.out.println(" resetStats() -> 1 operation, with a button in JConsole's MBeans tab.)");
        return name;
    }

    // -------------------------------------------------------------------
    // 2) Reading attributes through the generic MBeanServer API
    //    (this is exactly what JConsole/VisualVM's MBeans tab does under the hood)
    // -------------------------------------------------------------------
    private static void readCustomMxBeanGenerically(MBeanServer mbs, ObjectName name) throws Exception {
        // Note: no reference to the CacheStats class is needed here at all -- everything
        // is addressed purely by ObjectName + attribute name string, exactly like a
        // generic JMX client (JConsole, a remote monitoring agent) would do, with zero
        // compile-time dependency on our CacheStats/CacheStatsMXBean types.
        Object hitCount = mbs.getAttribute(name, "HitCount");
        Object missCount = mbs.getAttribute(name, "MissCount");
        Object hitRatio = mbs.getAttribute(name, "HitRatio");

        System.out.println("HitCount  = " + hitCount);
        System.out.println("MissCount = " + missCount);
        System.out.printf("HitRatio  = %.3f%n", (double) hitRatio);

        System.out.println("\n(Attribute names are derived from getter method names: getHitCount() ->");
        System.out.println("attribute \"HitCount\", following ordinary JavaBean conventions.)");
    }

    // -------------------------------------------------------------------
    // 3) Invoking an operation generically
    // -------------------------------------------------------------------
    private static void invokeOperationGenerically(MBeanServer mbs, ObjectName name) throws Exception {
        System.out.println("Before reset: HitCount=" + mbs.getAttribute(name, "HitCount"));

        // invoke(objectName, operationName, params, signature) -- params/signature are empty
        // here since resetStats() takes no arguments.
        mbs.invoke(name, "resetStats", new Object[0], new String[0]);

        System.out.println("After reset:  HitCount=" + mbs.getAttribute(name, "HitCount"));
        System.out.println("(This is exactly what clicking \"resetStats\" in JConsole's MBeans tab does --");
        System.out.println("fills in zero arguments and calls MBeanServer.invoke() the same way.)");
    }

    // -------------------------------------------------------------------
    // 4) Platform MXBeans are ordinary MBeans registered in the SAME server
    // -------------------------------------------------------------------
    private static void compareDirectVsGenericPlatformMxBeanAccess(MBeanServer mbs) throws Exception {
        // Direct, typed access -- the normal way application code reads platform data.
        ThreadMXBean threadsDirect = ManagementFactory.getThreadMXBean();
        System.out.println("Direct ThreadMXBean.getThreadCount()      = " + threadsDirect.getThreadCount());

        // Generic access to the SAME bean, via its well-known ObjectName -- proves it's
        // registered in the identical MBeanServer returned by getPlatformMBeanServer().
        ObjectName threadingName = new ObjectName("java.lang:type=Threading");
        Object threadCountGeneric = mbs.getAttribute(threadingName, "ThreadCount");
        System.out.println("Generic MBeanServer.getAttribute(...)     = " + threadCountGeneric);

        System.out.println("\nBoth calls return the same live value because ManagementFactory.getThreadMXBean()");
        System.out.println("and ManagementFactory.getPlatformMBeanServer() ultimately talk to the identical");
        System.out.println("underlying MBean -- java.lang:type=Threading -- just through two different APIs.");

        // Also demonstrate the deadlock-detection API mentioned in Theory File 01 --
        // genuinely runnable, returns null here since this demo has no deadlocked threads.
        long[] deadlocked = threadsDirect.findDeadlockedThreads();
        System.out.println("\nfindDeadlockedThreads() result: "
                + (deadlocked == null ? "null (no deadlock detected -- expected in this demo)"
                                       : deadlocked.length + " thread(s) deadlocked"));
        System.out.println("(See 03_VisualVmProfilingDemo.java for a demo that actually PRODUCES a deadlock");
        System.out.println("and prints the involved threads' stacks via this exact API.)");
    }

    // -------------------------------------------------------------------
    // 5) JConsole / remote JMX notes -- external tool usage, comments only
    // -------------------------------------------------------------------
    private static void printJConsoleAndRemoteNotes() {
        printSection("6) JConsole and Remote JMX Connections (Notes)");

        // jconsole -- bundled with the JDK, launched as just `jconsole` from a shell.
        // With no arguments it shows a picker of LOCAL JVM processes to attach to (via the
        // JVM attach API -- no special flags needed for same-machine attach). Try it against
        // this program's own PID while it's running (add a Thread.sleep in main if you want
        // time to attach): the CacheStats bean registered above appears live under the
        // "com.study.jvmtools" domain in the MBeans tab, fully interactive -- attributes
        // shown/refreshable, resetStats() invokable with a button, with ZERO extra tooling.
        System.out.println("jconsole <no args>  -- picks a LOCAL JVM to attach to (JVM attach API, no flags");
        System.out.println("                       needed). MBeans tab shows this demo's CacheStats bean live.");

        // Remote JMX requires explicitly opening an RMI connector at JVM startup:
        //   -Dcom.sun.management.jmxremote
        //   -Dcom.sun.management.jmxremote.port=9010
        //   -Dcom.sun.management.jmxremote.rmi.port=9010
        //   -Dcom.sun.management.jmxremote.authenticate=false   # DEV ONLY
        //   -Dcom.sun.management.jmxremote.ssl=false             # DEV ONLY
        //   -Djava.rmi.server.hostname=<reachable-host-or-ip>
        // Then connect from JConsole/VisualVM using "<host>:9010" as the connection string.
        System.out.println("Remote JMX flags (JVM startup, DEV ONLY without auth/ssl):");
        System.out.println("  -Dcom.sun.management.jmxremote");
        System.out.println("  -Dcom.sun.management.jmxremote.port=9010");
        System.out.println("  -Dcom.sun.management.jmxremote.rmi.port=9010");
        System.out.println("  -Dcom.sun.management.jmxremote.authenticate=false   (DEV ONLY)");
        System.out.println("  -Dcom.sun.management.jmxremote.ssl=false             (DEV ONLY)");
        System.out.println("  -Djava.rmi.server.hostname=<reachable-host-or-ip>");
        System.out.println("Then in JConsole/VisualVM: connect to \"<host>:9010\".");
        System.out.println("\nProduction JMX must use password.file/access.file + SSL, or be tunneled over");
        System.out.println("SSH -- an open, unauthenticated JMX port allows invokable operations, which can");
        System.out.println("be leveraged for remote code execution in the worst case (Theory File 01).");
    }

    // -------------------------------------------------------------------
    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }
}
