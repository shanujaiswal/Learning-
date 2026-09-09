/*
 * JavaOverviewDemo.java
 *
 * Demonstrates:
 *     1. Hello World, in a couple of variants (println vs print vs printf)
 *     2. Reading JVM/System properties via System.getProperty(...)
 *     3. How the JVM reports heap memory via Runtime.getRuntime()
 *     4. Reading command-line arguments passed into main(String[] args)
 *     5. The class-name-must-match-filename rule, and public static void main
 *        as the JVM's designated entry point
 *
 * Covers Theory chapter:
 *     10) Java/01) Core Java Fundamentals/Theory/01 Java Language Overview and Setup.md
 *
 * Compile: javac 01_java_overview_demo.java
 * Run:     java JavaOverviewDemo
 * Run with args (to see section 4 populated): java JavaOverviewDemo alpha beta gamma
 *
 * Note on filenames: the source file above is named to match this study repo's
 * numbered-practicals convention, but the PUBLIC class inside it is named
 * "JavaOverviewDemo" -- per the filename rule explained in the theory file,
 * javac actually requires the public class's name to match the .java filename
 * exactly, so on disk this file is compiled as "JavaOverviewDemo.java" in
 * practice; keep that class name when you copy/compile this file locally.
 */

public class JavaOverviewDemo {

    // -------------------------------------------------------------------
    // Small helper -- mirrors the "print_section" convention used in the
    // reference Python practicals, just to keep console output organized
    // into clearly labeled, skimmable blocks.
    // -------------------------------------------------------------------
    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }

    public static void main(String[] args) {

        // ---------------------------------------------------------------
        // 1) Hello World variants
        // ---------------------------------------------------------------
        // println/print/printf are the three most common ways to write to
        // standard output. println adds a trailing newline; print does not;
        // printf supports C-style format specifiers (%s, %d, etc).
        printSection("1) Hello World variants");
        System.out.println("Hello, World!");           // Expected: Hello, World!  (with newline)
        System.out.print("Hello");                       // Expected: "Hello" printed with NO newline...
        System.out.println(", World! (via print + println)"); // ...so this continues on the same line
        System.out.printf("Hello, %s! You are running Java %s.%n", "World", "safely");
        // Expected: Hello, World! You are running Java safely.

        // ---------------------------------------------------------------
        // 2) System properties -- the JVM exposes a bundle of key/value
        // configuration and environment info via System.getProperty(key).
        // These are set by the JVM at startup, not by the OS environment
        // directly (that's System.getenv() instead, a different mechanism).
        // ---------------------------------------------------------------
        printSection("2) System properties (java.version, os.name, etc.)");
        // Expected output resembles (exact values depend on your machine):
        //   Java version : 21.0.2
        //   Java vendor  : Eclipse Adoptium
        //   Java home    : C:\Program Files\Eclipse Adoptium\jdk-21...
        //   OS name      : Windows 11
        //   OS arch      : amd64
        //   OS version   : 10.0
        //   User name    : <your OS username>
        //   User dir     : <the directory java was launched from>
        //   File separator: \  (or / on Unix-like systems)
        System.out.println("Java version  : " + System.getProperty("java.version"));
        System.out.println("Java vendor   : " + System.getProperty("java.vendor"));
        System.out.println("Java home     : " + System.getProperty("java.home"));
        System.out.println("OS name       : " + System.getProperty("os.name"));
        System.out.println("OS arch       : " + System.getProperty("os.arch"));
        System.out.println("OS version    : " + System.getProperty("os.version"));
        System.out.println("User name     : " + System.getProperty("user.name"));
        System.out.println("User dir      : " + System.getProperty("user.dir"));
        System.out.println("File separator: " + System.getProperty("file.separator"));
        // If a requested property key doesn't exist, getProperty returns null
        // rather than throwing -- worth demonstrating explicitly here:
        System.out.println("Unknown key   : " + System.getProperty("this.key.does.not.exist"));
        // Expected: Unknown key   : null

        // ---------------------------------------------------------------
        // 3) JVM memory reporting via Runtime.getRuntime()
        // ---------------------------------------------------------------
        // Runtime gives a live handle onto the JVM instance this program is
        // running inside of. totalMemory() is the heap size currently
        // allocated FROM the OS by the JVM (grows as needed up to maxMemory).
        // freeMemory() is how much of that already-allocated heap is unused
        // right now. Neither number is "total RAM on the machine" -- both
        // are scoped to THIS JVM process's heap only.
        printSection("3) JVM memory reporting (Runtime.getRuntime())");
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long maxMemory = runtime.maxMemory();          // upper bound the heap is allowed to grow to
        long usedMemory = totalMemory - freeMemory;
        // Expected: three positive byte counts, converted to MB below for
        // readability -- exact values vary by machine and JVM defaults, but
        // typically total/used will be tens of MB for a program this small,
        // and maxMemory will usually be a sizable fraction of system RAM.
        System.out.println("Total heap (allocated): " + toMb(totalMemory) + " MB");
        System.out.println("Free heap (unused)    : " + toMb(freeMemory) + " MB");
        System.out.println("Used heap (total-free): " + toMb(usedMemory) + " MB");
        System.out.println("Max heap (ceiling)    : " + toMb(maxMemory) + " MB");
        System.out.println("Available processors  : " + runtime.availableProcessors());
        // Allocating a modestly large array demonstrates freeMemory() shrink
        // after allocation -- illustrating that the heap is real, tracked
        // memory, not just a static reported number.
        int[] scratch = new int[1_000_000];             // ~4MB (int = 4 bytes each)
        scratch[0] = 1;                                   // touch it so it isn't optimized away
        long freeMemoryAfterAlloc = runtime.freeMemory();
        System.out.println("Free heap after allocating a 1,000,000-int array: "
                + toMb(freeMemoryAfterAlloc) + " MB  (expect this to be LOWER than before)");

        // ---------------------------------------------------------------
        // 4) Command-line arguments
        // ---------------------------------------------------------------
        // args[] is populated from whatever tokens follow "JavaOverviewDemo"
        // on the "java" command line -- it is never null, only possibly
        // empty, per the theory file's explanation of the main() signature.
        printSection("4) Command-line arguments (String[] args)");
        if (args.length == 0) {
            // Expected when run as: java JavaOverviewDemo
            System.out.println("No arguments were passed.");
            System.out.println("Try running: java JavaOverviewDemo alpha beta gamma");
        } else {
            // Expected when run as: java JavaOverviewDemo alpha beta gamma
            //   Received 3 argument(s):
            //     args[0] = alpha
            //     args[1] = beta
            //     args[2] = gamma
            System.out.println("Received " + args.length + " argument(s):");
            for (int i = 0; i < args.length; i++) {
                System.out.println("  args[" + i + "] = " + args[i]);
            }
        }

        // ---------------------------------------------------------------
        // 5) Entry point recap
        // ---------------------------------------------------------------
        // A quick reminder printed at the end, tying back to the theory
        // file's line-by-line breakdown of "public static void main":
        //   public  -> the JVM (an external caller) can see and invoke it
        //   static  -> callable with no object instance needed
        //   void    -> returns nothing to the JVM
        //   main    -> the exact method name the JVM looks for by convention
        printSection("5) Recap");
        System.out.println("This whole program ran because the JVM located and invoked");
        System.out.println("'public static void main(String[] args)' in class JavaOverviewDemo.");
        System.out.println("\nAll Java overview demos completed.");
    }

    // Small formatting helper -- converts a raw byte count into MB with two
    // decimal places, purely to keep the memory-reporting output readable.
    private static double toMb(long bytes) {
        double mb = bytes / (1024.0 * 1024.0);
        return Math.round(mb * 100.0) / 100.0;
    }
}
