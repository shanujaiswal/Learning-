/*
 * Topic01_JvmArchitectureDemo.java
 *
 * NOTE ON FILENAME: Java requires the public class name to exactly match the filename,
 * and Java identifiers cannot begin with a digit -- so the public class below is named
 * "Topic01_JvmArchitectureDemo" while the FILE keeps the "01_..." numeric prefix used
 * throughout this repo for ordering. Compile/run using the class name, not the raw file prefix:
 *
 * Compile: javac 01_JvmArchitectureDemo.java
 * Run:     java Topic01_JvmArchitectureDemo
 *
 * Demonstrates:
 *   1. Class loader hierarchy -- which loader loaded which class, and parent delegation
 *   2. Static initializer ordering and lazy initialization-on-first-use
 *   3. Runtime memory info (heap sizes) via java.lang.Runtime
 *   4. A simple JIT warm-up effect, timed empirically (interpreted vs JIT-compiled hot loop)
 *   5. Stack frames and recursion depth (leads into StackOverflowError conceptually --
 *      see 03_MemoryLeaksDemo.java for a controlled, explained trigger of it)
 *
 * Covers Theory chapter:
 *   07) JVM Internals and Memory Management/Theory/01 JVM Architecture Overview.md
 */

public class Topic01_JvmArchitectureDemo {

    public static void main(String[] args) throws Exception {
        demoClassLoaderHierarchy();
        demoStaticInitializationOrder();
        demoRuntimeMemoryInfo();
        demoJitWarmUp();
        demoStackFrameDepth();
        System.out.println("\nAll JVM architecture demos completed.");
    }

    // -------------------------------------------------------------------
    // 1) Class loader hierarchy
    // -------------------------------------------------------------------
    private static void demoClassLoaderHierarchy() {
        printSection("1) Class Loader Hierarchy");

        // java.lang.String is a core JDK class -> loaded by the Bootstrap loader,
        // which the JVM represents as `null` since it's implemented in native code
        // and has no Java-visible ClassLoader instance.
        ClassLoader stringLoader = String.class.getClassLoader();
        System.out.println("String.class.getClassLoader()      = " + stringLoader
                + "  (null means Bootstrap ClassLoader)");

        // This class (our own code) is loaded by the Application/System class loader.
        ClassLoader ourLoader = Topic01_JvmArchitectureDemo.class.getClassLoader();
        System.out.println("Our class's getClassLoader()        = " + ourLoader);

        System.out.println("ClassLoader.getSystemClassLoader()  = " + ClassLoader.getSystemClassLoader());

        // Walk the parent chain from our loader up to the bootstrap loader (null).
        System.out.println("\nParent delegation chain (child -> ... -> bootstrap):");
        ClassLoader current = ourLoader;
        int depth = 0;
        while (current != null) {
            System.out.println("  ".repeat(depth) + "-> " + current);
            current = current.getParent();
            depth++;
        }
        System.out.println("  ".repeat(depth) + "-> null (Bootstrap ClassLoader)");
    }

    // -------------------------------------------------------------------
    // 2) Static initializer ordering + lazy initialization-on-first-use
    // -------------------------------------------------------------------
    private static void demoStaticInitializationOrder() {
        printSection("2) Static Initialization Order (lazy, on first active use)");
        System.out.println("About to reference LazyInitialized.MESSAGE for the first time...");
        // This is the exact moment LazyInitialized gets Loaded -> Linked -> Initialized.
        // Nothing above this line triggered it -- merely compiling against the class
        // (or mentioning its name in a comment/type) does NOT initialize it.
        System.out.println("Value received: " + LazyInitialized.MESSAGE);
        System.out.println("(Notice the static block's print appeared ONLY just now, not earlier)");
    }

    /** Deliberately not touched until demoStaticInitializationOrder() runs, to prove laziness. */
    static class LazyInitialized {
        static {
            System.out.println("  [static initializer running for LazyInitialized -- this is the Initialization phase]");
        }
        static final String MESSAGE = computeMessage();

        private static String computeMessage() {
            return "Hello from a statically-initialized field";
        }
    }

    // -------------------------------------------------------------------
    // 3) Runtime memory info
    // -------------------------------------------------------------------
    private static void demoRuntimeMemoryInfo() {
        printSection("3) Runtime Memory Info (java.lang.Runtime)");
        Runtime rt = Runtime.getRuntime();

        long maxMemory = rt.maxMemory();         // -Xmx ceiling (or JVM-computed default)
        long totalMemory = rt.totalMemory();      // currently committed heap size
        long freeMemory = rt.freeMemory();        // free space within the currently committed heap
        long usedMemory = totalMemory - freeMemory;

        System.out.printf("Max heap (-Xmx or default):   %,10d bytes (%.1f MB)%n", maxMemory, maxMemory / 1e6);
        System.out.printf("Total committed heap:         %,10d bytes (%.1f MB)%n", totalMemory, totalMemory / 1e6);
        System.out.printf("Free within committed heap:   %,10d bytes (%.1f MB)%n", freeMemory, freeMemory / 1e6);
        System.out.printf("Currently used (approx):      %,10d bytes (%.1f MB)%n", usedMemory, usedMemory / 1e6);
        System.out.println("Available processors:         " + rt.availableProcessors());

        System.out.println("\nNote: totalMemory() can be LESS than maxMemory() -- the JVM grows the heap");
        System.out.println("toward the max as needed, it doesn't necessarily commit it all upfront");
        System.out.println("(unless -Xms == -Xmx, a common production tuning choice -- see Theory File 05).");
    }

    // -------------------------------------------------------------------
    // 4) JIT warm-up effect, timed empirically
    // -------------------------------------------------------------------
    private static void demoJitWarmUp() {
        printSection("4) JIT Warm-Up Effect (interpreted vs JIT-compiled)");
        System.out.println("Running the SAME hot loop repeatedly and timing each batch --");
        System.out.println("later batches should get faster as the JIT compiles the hot method.\n");

        final int iterationsPerBatch = 20_000_000;
        for (int batch = 1; batch <= 5; batch++) {
            long start = System.nanoTime();
            long result = hotLoopWork(iterationsPerBatch);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            System.out.printf("Batch %d: %6d ms  (result=%d, just to prevent dead-code elimination)%n",
                    batch, elapsedMs, result);
        }
        System.out.println("\nExpect later batches to trend faster than the first (JIT compiling this");
        System.out.println("method to native code after the JVM notices it's 'hot'). Exact numbers vary");
        System.out.println("by machine/JVM -- the TREND, not the absolute numbers, is the point.");
    }

    /** A deliberately simple, CPU-bound loop -- good JIT compilation/inlining target. */
    private static long hotLoopWork(int iterations) {
        long sum = 0;
        for (int i = 0; i < iterations; i++) {
            sum += (i * 31) ^ (i >>> 3);   // arbitrary arithmetic, just to keep the JIT busy
        }
        return sum;
    }

    // -------------------------------------------------------------------
    // 5) Stack frame depth
    // -------------------------------------------------------------------
    private static void demoStackFrameDepth() {
        printSection("5) Stack Frames and Recursion Depth");
        System.out.println("Each recursive call pushes a new JVM stack frame (locals + return address).");
        System.out.println("Recursing to a SAFE, bounded depth here (see Theory File 01's Runtime Data");
        System.out.println("Areas section, and 03_MemoryLeaksDemo.java, for what happens if unbounded):\n");

        int reachedDepth = recurse(0, 5000);
        System.out.println("Reached depth " + reachedDepth + " safely, then returned normally.");
        System.out.println("Each level's local variables disappeared as its frame was popped on return --");
        System.out.println("this is why local primitives/references don't outlive their method call.");
    }

    private static int recurse(int depth, int maxDepth) {
        if (depth >= maxDepth) {
            return depth;
        }
        return recurse(depth + 1, maxDepth);
    }

    // -------------------------------------------------------------------
    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }
}
