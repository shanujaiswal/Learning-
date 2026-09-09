/*
 * Topic05_JitWarmupDemo.java
 *
 * NOTE ON FILENAME: the public class is named "Topic05_JitWarmupDemo" (Java identifiers
 * can't start with a digit) while the FILE keeps the "05_..." numeric prefix used
 * throughout this repo for ordering.
 *
 * Compile: javac 05_JitWarmupDemo.java
 * Run:     java Topic05_JitWarmupDemo
 *
 * Run WITH JIT compilation logging visible (see Theory File 05, "Observing the JIT"):
 *          java -XX:+PrintCompilation Topic05_JitWarmupDemo
 *
 * Run forcing C1-only (no tiered escalation to C2) to compare against the default:
 *          java -XX:TieredStopAtLevel=1 Topic05_JitWarmupDemo
 *
 * Run with tiered compilation disabled entirely (C2-only, old -server-only behavior):
 *          java -XX:-TieredCompilation Topic05_JitWarmupDemo
 *
 * Demonstrates:
 *   1. A hot loop timed across many successive rounds, showing the classic warmup curve:
 *      early rounds (interpreted / low tier) are visibly slower than later rounds (C2,
 *      Tier 4) -- the same effect Theory File 06 exists specifically to correct for
 *   2. On-Stack Replacement (OSR) context: a single very-long-running loop inside ONE
 *      method call, which can get compiled and swapped in MID-EXECUTION
 *   3. A simple megamorphic-vs-monomorphic call site comparison, illustrating (without
 *      needing -XX:+PrintInlining) that call-site shape affects steady-state throughput
 *   4. Comments on -XX:+PrintCompilation output format and how to read it
 *
 * IMPORTANT CAVEAT (same spirit as the G1Demo reference file): the JIT's actual tiering
 * decisions are internally adaptive and NOT something this program can force or fully
 * control -- what this file DOES do is run workloads genuinely shaped to make warmup
 * effects visible in wall-clock timing, and reports real, measured numbers every time.
 * Absolute numbers will vary by machine; the RELATIVE early-vs-late round pattern is the
 * point (Theory File 03/05's advice: trust relative comparisons over absolute numbers).
 *
 * Covers Theory chapter:
 *   07) JVM Internals and Memory Management/Theory/14 JIT Compilation Deep Dive.md
 */

public class Topic05_JitWarmupDemo {

    public static void main(String[] args) {
        printSection("1) Hot Loop Timing Across Many Rounds -- the Warmup Curve");
        demoWarmupCurve();

        printSection("2) On-Stack Replacement (OSR) Context -- One Very Long Loop");
        demoSingleLongLoopForOsr();

        printSection("3) Monomorphic vs Megamorphic Call Site (Illustrative)");
        demoCallSiteShapeEffect();

        printPrintCompilationNotes();

        System.out.println("\nAll JIT warmup demos completed.");
    }

    // -------------------------------------------------------------------
    // 1) Classic warmup curve: run the SAME hot computation across many
    // rounds, timing each round independently. Early rounds run mostly
    // interpreted (Tier 0) or C1-compiled (Tier 1/3); once the method has
    // been called enough times AND its back-edge/invocation counters cross
    // threshold, C2 (Tier 4) recompiles it using profiling data gathered
    // during its C1 life -- later rounds should be visibly faster.
    // -------------------------------------------------------------------
    private static void demoWarmupCurve() {
        final int roundsToShow = 10;
        final int iterationsPerRound = 2_000_000;

        System.out.println("Running " + roundsToShow + " rounds of a hot arithmetic loop, "
                + iterationsPerRound + " iterations each.");
        System.out.println("Watch the early rounds run slower than the later ones -- that gap IS warmup.\n");

        long[] roundNanos = new long[roundsToShow];
        for (int round = 0; round < roundsToShow; round++) {
            long start = System.nanoTime();
            long result = hotComputation(iterationsPerRound);
            long elapsed = System.nanoTime() - start;
            roundNanos[round] = elapsed;

            // Print the result too -- per Theory File 06's "dead code elimination" pitfall,
            // consuming the result (even just by printing it occasionally) is good practice
            // even outside a formal JMH benchmark, to reduce (not eliminate -- this isn't a
            // rigorous microbenchmark) the chance the JIT proves the loop's result is unused.
            System.out.printf("Round %2d: %,10d ns  (result=%d)%n", round + 1, elapsed, result);
        }

        long firstRoundNanos = roundNanos[0];
        long lastRoundNanos = roundNanos[roundsToShow - 1];
        double speedup = (double) firstRoundNanos / Math.max(1, lastRoundNanos);
        System.out.printf("%nFirst round: %,d ns   Last round: %,d ns   Observed speedup: %.2fx%n",
                firstRoundNanos, lastRoundNanos, speedup);
        System.out.println("(A speedup > 1x here is warmup in action: the SAME bytecode ran faster once");
        System.out.println("the JIT had a chance to compile it. Absolute values vary by machine and JDK;");
        System.out.println("the improving trend across rounds is the reliable, repeatable observation.)");
    }

    /**
     * A deliberately allocation-free, branch-light hot loop -- easy for C2 to optimize
     * heavily once compiled, and easy to reason about: pure integer arithmetic with no
     * external calls, so any speedup we measure is attributable to JIT compilation of
     * THIS loop, not GC pauses or I/O variance.
     */
    private static long hotComputation(int iterations) {
        long acc = 0;
        for (int i = 0; i < iterations; i++) {
            acc += (i * 31) ^ (i >>> 3);
        }
        return acc;
    }

    // -------------------------------------------------------------------
    // 2) On-Stack Replacement context: ONE method call containing a very
    // long-running loop. Per Theory File 05, OSR is what lets a currently-
    // interpreting long loop get swapped to compiled code MID-EXECUTION,
    // without waiting for the method to return and be called again -- this
    // matters because demoWarmupCurve() above calls hotComputation() many
    // TIMES (ordinary invocation-counter-driven compilation), whereas this
    // method calls it exactly ONCE with a huge iteration count, so if it
    // benefits from JIT compilation at all within this single call, OSR is
    // the mechanism responsible.
    // -------------------------------------------------------------------
    private static void demoSingleLongLoopForOsr() {
        final int hugeIterationCount = 200_000_000;
        System.out.println("Calling hotComputation() exactly ONCE with " + hugeIterationCount
                + " iterations.");
        System.out.println("If -XX:+PrintCompilation is enabled, look for a '%' qualifier next to this");
        System.out.println("method's compile log line -- that marks an On-Stack Replacement compilation,");
        System.out.println("specifically for a hot LOOP inside a method called only once (Theory File 05).\n");

        long start = System.nanoTime();
        long result = hotComputation(hugeIterationCount);
        long elapsed = System.nanoTime() - start;

        System.out.printf("Single long call: %,d ns for %,d iterations (result=%d)%n",
                elapsed, hugeIterationCount, result);
    }

    // -------------------------------------------------------------------
    // 3) Monomorphic vs megamorphic call site -- illustrative only (this
    // is NOT a rigorous JMH-grade measurement; see 06 for that distinction
    // done properly). Still genuinely runnable and shows a real, repeatable
    // relative difference on most JVMs: a call site that only ever sees ONE
    // concrete type can be inlined/speculated on by C2 far more easily than
    // one cycling through many implementations (Theory File 05's CHA +
    // monomorphic/megamorphic discussion).
    // -------------------------------------------------------------------
    private interface Op {
        int apply(int x);
    }

    private static final Op ADD_ONE = x -> x + 1;
    private static final Op DOUBLE = x -> x * 2;
    private static final Op NEGATE = x -> -x;
    private static final Op SQUARE = x -> x * x;
    private static final Op[] MANY_IMPLS = { ADD_ONE, DOUBLE, NEGATE, SQUARE };

    private static void demoCallSiteShapeEffect() {
        final int iterations = 20_000_000;

        // Monomorphic: this call site (inside monomorphicLoop) only ever invokes ADD_ONE.
        long monoStart = System.nanoTime();
        long monoResult = monomorphicLoop(iterations, ADD_ONE);
        long monoElapsed = System.nanoTime() - monoStart;

        // Megamorphic: this call site (inside megamorphicLoop) cycles through 4 different
        // implementations, well past the bimorphic case C2 can still guard cheaply.
        long megaStart = System.nanoTime();
        long megaResult = megamorphicLoop(iterations, MANY_IMPLS);
        long megaElapsed = System.nanoTime() - megaStart;

        System.out.printf("Monomorphic call site  (1 impl):  %,10d ns  (result=%d)%n", monoElapsed, monoResult);
        System.out.printf("Megamorphic call site (4 impls):  %,10d ns  (result=%d)%n", megaElapsed, megaResult);
        System.out.println("\nOn most JVMs the megamorphic version runs measurably slower per-call, since a");
        System.out.println("call site seeing many concrete types generally can't be inlined the way a");
        System.out.println("single-implementation call site can -- illustrative here, not a substitute for");
        System.out.println("a real JMH benchmark (see 06_JmhBenchmarkDemo.java) if this specific difference");
        System.out.println("ever mattered enough to need a trustworthy number.");
    }

    private static long monomorphicLoop(int iterations, Op op) {
        long acc = 0;
        for (int i = 0; i < iterations; i++) {
            acc += op.apply(i);
        }
        return acc;
    }

    private static long megamorphicLoop(int iterations, Op[] ops) {
        long acc = 0;
        for (int i = 0; i < iterations; i++) {
            // Cycling through 4 different Op implementations at the SAME call site --
            // this is what pushes it from monomorphic/bimorphic to megamorphic.
            acc += ops[i % ops.length].apply(i);
        }
        return acc;
    }

    // -------------------------------------------------------------------
    private static void printPrintCompilationNotes() {
        printSection("4) Reading -XX:+PrintCompilation Output (Notes)");

        // Example output line shape (Theory File 05):
        //     123   1       3       java.lang.String::hashCode (60 bytes)
        //     145   2       4       com.myapp.OrderService::calculateTotal (85 bytes)
        //     198   3   n    0       java.lang.System::arraycopy (native)
        //     250   4       3   %   com.myapp.BatchProcessor::processAll (210 bytes)
        //     301   5       4       com.myapp.OrderService::calculateTotal (85 bytes)   made not entrant
        System.out.println("Run this file with -XX:+PrintCompilation and look for lines shaped like:");
        System.out.println("  123   1       3       java.lang.String::hashCode (60 bytes)");
        System.out.println("  250   4       3   %   Topic05_JitWarmupDemo::hotComputation (NN bytes)");
        System.out.println("  301   5       4       Topic05_JitWarmupDemo::hotComputation (NN bytes)   made not entrant");
        System.out.println();
        System.out.println("Columns left to right: timestamp (ms since JVM start) -- compile ID (sequential)");
        System.out.println("-- qualifiers ('n'=native wrapper, '%'=OSR compile, 's'=synchronized, '!'=has");
        System.out.println("exception handler) -- tier number (1-4) -- method name -- bytecode size.");
        System.out.println();
        System.out.println("'made not entrant' means that compiled version was deoptimized or superseded --");
        System.out.println("seeing hotComputation() compiled more than once (e.g. Tier 3 then Tier 4) across");
        System.out.println("this run's many calls in demoWarmupCurve() is EXPECTED tiered-compilation");
        System.out.println("behavior, not a bug (Theory File 05).");
        System.out.println();
        System.out.println("Related flags: -XX:+PrintInlining (what got inlined and why/why not; needs");
        System.out.println("-XX:+UnlockDiagnosticVMOptions on some JDKs), -XX:+TraceDeoptimization (prints");
        System.out.println("every deopt and its reason), -Xbatch (forces synchronous compilation -- useful");
        System.out.println("for deterministic small repros, never for production).");
    }

    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }
}
