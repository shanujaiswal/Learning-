/*
 * Topic02_GarbageCollectionDemo.java
 *
 * NOTE ON FILENAME: the public class is named "Topic02_GarbageCollectionDemo" (Java
 * identifiers can't start with a digit) while the FILE keeps the "02_..." numeric prefix
 * used throughout this repo for ordering.
 *
 * Compile: javac 02_GarbageCollectionDemo.java
 * Run:     java Topic02_GarbageCollectionDemo
 * Run with GC logging (recommended, Java 9+):
 *          java -Xlog:gc* Topic02_GarbageCollectionDemo
 *
 * Demonstrates:
 *   1. Young-generation churn -- allocating lots of short-lived objects and observing
 *      Runtime memory numbers rise and fall as GC reclaims them
 *   2. Object promotion intuition -- objects kept alive vs allowed to die
 *   3. Reachability vs System.gc() -- why nulling a reference doesn't mean "instantly freed"
 *   4. SoftReference vs WeakReference vs a strong reference, observed behaviorally
 *   5. A manual "sawtooth" heap usage pattern you could plot from the printed samples
 *
 * Covers Theory chapter:
 *   07) JVM Internals and Memory Management/Theory/02 Memory Management and Garbage Collection.md
 */

import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class Topic02_GarbageCollectionDemo {

    public static void main(String[] args) throws Exception {
        demoYoungGenChurn();
        demoReachabilityVsNulling();
        demoReferenceTypes();
        System.out.println("\nAll garbage collection demos completed.");
    }

    // -------------------------------------------------------------------
    // 1) Young-generation churn
    // -------------------------------------------------------------------
    private static void demoYoungGenChurn() {
        printSection("1) Young-Generation Churn -- Allocate-and-Discard Pattern");
        System.out.println("Allocating and discarding millions of short-lived objects in batches,");
        System.out.println("sampling Runtime memory before/after each batch.\n");
        System.out.println("(Most of these objects die in Eden almost immediately -- exactly the");
        System.out.println("'generational hypothesis' pattern described in Theory File 02.)\n");

        Runtime rt = Runtime.getRuntime();

        for (int batch = 1; batch <= 5; batch++) {
            long before = usedMemory(rt);

            // Allocate a large number of short-lived objects that immediately become garbage
            // once this loop iteration ends -- nothing outside the loop keeps a reference.
            long checksum = 0;
            for (int i = 0; i < 2_000_000; i++) {
                Integer boxed = i * 2;      // autoboxing allocates a new Integer object (usually, outside
                                              // the small-integer cache range) -- a classic "temporary object"
                checksum += boxed;
            }

            long after = usedMemory(rt);
            System.out.printf("Batch %d: used before=%6.1fMB  after=%6.1fMB  (checksum=%d, prevents dead-code elimination)%n",
                    batch, before / 1e6, after / 1e6, checksum);
        }

        System.out.println("\nUsed memory rising and falling across batches (rather than climbing");
        System.out.println("without bound) is the healthy 'sawtooth' pattern from Theory File 02 --");
        System.out.println("GC is reclaiming the temporary Integer objects between/during batches.");
    }

    // -------------------------------------------------------------------
    // 2) Reachability vs System.gc()
    // -------------------------------------------------------------------
    private static void demoReachabilityVsNulling() {
        printSection("2) Reachability -- Nulling a Reference Makes an Object ELIGIBLE, Not Freed");

        Object obj = new Object();
        System.out.println("Created an object, held by local variable 'obj'.");
        System.out.println("obj is reachable via a GC root (this method's stack frame).");

        obj = null;
        System.out.println("\nSet obj = null -- the original object is now UNREACHABLE (nothing else");
        System.out.println("references it), so it becomes a CANDIDATE for collection.");
        System.out.println("It is NOT necessarily collected at this exact instant, though -- GC runs");
        System.out.println("on its own schedule, not synchronously on every reference drop.");

        System.out.println("\nSystem.gc() is only a HINT the JVM is free to ignore (Theory File 02) --");
        System.out.println("calling it here to request (not guarantee) a collection pass:");
        System.gc();
        System.out.println("Requested. In production code, calling System.gc() explicitly is usually");
        System.out.println("a code smell -- see the Gotchas section in Theory File 02.");
    }

    // -------------------------------------------------------------------
    // 3) SoftReference vs WeakReference vs strong reference
    // -------------------------------------------------------------------
    private static void demoReferenceTypes() {
        printSection("3) Strong vs Soft vs Weak References");

        Object strongTarget = new Object();
        Object weakTarget = new Object();
        Object softTarget = new Object();

        WeakReference<Object> weakRef = new WeakReference<>(weakTarget);
        SoftReference<Object> softRef = new SoftReference<>(softTarget);

        System.out.println("Before dropping strong references:");
        System.out.println("  weakRef.get() != null : " + (weakRef.get() != null));
        System.out.println("  softRef.get() != null : " + (softRef.get() != null));

        // Drop the ONLY strong references to weakTarget and softTarget.
        // strongTarget is deliberately kept alive (referenced below) as a control/contrast.
        weakTarget = null;
        softTarget = null;

        System.out.println("\nDropped the strong references to the weak/soft targets. Requesting GC...");
        System.gc();
        sleepBriefly();

        System.out.println("\nAfter GC request:");
        System.out.println("  weakRef.get() != null : " + (weakRef.get() != null)
                + "  (WeakReferences are cleared at essentially the next GC cycle once unreachable)");
        System.out.println("  softRef.get() != null : " + (softRef.get() != null)
                + "  (SoftReferences typically survive UNLESS the JVM is genuinely low on memory --");
        System.out.println("                           so it's common/expected for this to still print 'true' here)");
        System.out.println("  strongTarget kept alive by a real reference the whole time: " + (strongTarget != null));

        System.out.println("\nThis is exactly why SoftReference suits memory-sensitive CACHES (evicted only");
        System.out.println("under real pressure) while WeakReference suits things like WeakHashMap keys or");
        System.out.println("listener maps where you want entries gone as soon as nothing else needs them.");
    }

    // -------------------------------------------------------------------
    private static long usedMemory(Runtime rt) {
        return rt.totalMemory() - rt.freeMemory();
    }

    private static void sleepBriefly() {
        try {
            Thread.sleep(100); // give the (concurrent parts of a) GC a moment to act before we re-check
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
