/*
 * Topic06_JmhBenchmarkDemo.java
 *
 * NOTE ON FILENAME: the public class is named "Topic06_JmhBenchmarkDemo" (Java
 * identifiers can't start with a digit) while the FILE keeps the "06_..." numeric
 * prefix used throughout this repo for ordering.
 *
 * This file has TWO parts:
 *
 *   PART A (below, commented out): a REAL JMH benchmark class, written exactly as it
 *   would be in a genuine project, using @Benchmark/@State/@Warmup/@Measurement/@Fork.
 *   It is commented out because real JMH requires its annotation-processor + generated
 *   benchmark-runner classes on the classpath (a Maven/Gradle dependency: org.openjdk.jmh:
 *   jmh-core + jmh-generator-annprocess), which this JDK-only repo deliberately does not
 *   pull in. See the comment block for exact Maven/Gradle coordinates and run commands.
 *
 *   PART B (the actual compiled/runnable code in this file): a small HAND-ROLLED
 *   micro-benchmark harness that manually implements JMH's core ideas -- discarded
 *   warmup iterations, a Blackhole-style sink to defeat dead-code elimination, and
 *   multiple measured iterations with basic statistics -- specifically so you can see,
 *   by contrast, how much JMH does for you and why "just write your own timing loop"
 *   is risky (Theory File 06, "Why Microbenchmarking Java Is Genuinely Hard").
 *
 * Compile: javac 06_JmhBenchmarkDemo.java
 * Run:     java Topic06_JmhBenchmarkDemo
 *
 * Covers Theory chapter:
 *   07) JVM Internals and Memory Management/Theory/15 Performance Testing and Benchmarking with JMH.md
 */

import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;

public class Topic06_JmhBenchmarkDemo {

    public static void main(String[] args) {
        printSection("Part A: Real JMH Benchmark Code (see comment block below main())");
        System.out.println("A full, realistic JMH benchmark class is written as a comment further down in");
        System.out.println("this file (search for 'REAL JMH BENCHMARK'). It cannot run as part of this");
        System.out.println("plain `java`/`javac` demo -- it needs the JMH annotation processor and");
        System.out.println("generated runner classes from the jmh-core / jmh-generator-annprocess");
        System.out.println("dependencies. Build/run instructions are in that comment block.");

        printSection("Part B: Hand-Rolled Micro-Benchmark Harness (genuinely runs below)");
        Topic06_JmhBenchmarkDemo demo = new Topic06_JmhBenchmarkDemo();
        demo.runHandRolledHarness();

        System.out.println("\nAll JMH / hand-rolled benchmark demos completed.");
    }

    // =====================================================================
    // PART B: a small, genuinely runnable, hand-rolled harness that manually
    // demonstrates the same three core ideas JMH automates:
    //   1. Discarded WARMUP iterations before measuring (Theory File 06, Pitfall 1)
    //   2. A Blackhole-style sink to defeat dead-code elimination (Pitfall 2)
    //   3. Inputs read from instance state, not compile-time constants (Pitfall 3)
    // =====================================================================

    /** Stand-in for a JMH @State field -- read through an object so the JIT can't treat it
     *  as a compile-time constant the way a `static final` literal could be folded away. */
    private final String left = "hello-";
    private final String right = "world-" + ThreadLocalRandom.current().nextInt(1000);

    /**
     * A minimal hand-rolled stand-in for org.openjdk.jmh.infra.Blackhole. Real Blackhole
     * uses carefully tuned internal state/checks to reliably defeat dead-code elimination
     * with minimal overhead of its own (Theory File 06 explicitly warns rolling your own
     * is riskier than it looks) -- this version is illustrative, NOT a production-grade
     * substitute, which is exactly the point of contrasting it with real JMH below.
     */
    static final class SimpleBlackhole {
        // A volatile sink field: writes to it are real, observable side effects the JIT
        // cannot prove are unused, which is what (mostly) prevents the computation feeding
        // them from being eliminated entirely. Real Blackhole does more than this internally.
        private volatile long sink;

        void consume(long value) {
            sink = value;
        }

        void consume(Object value) {
            sink = System.identityHashCode(value);
        }
    }

    private void runHandRolledHarness() {
        final SimpleBlackhole blackhole = new SimpleBlackhole();
        final int warmupIterations = 5;
        final int measuredIterations = 5;
        final int opsPerIteration = 500_000;

        System.out.println("Comparing two ways of concatenating strings, hand-rolled JMH-style:");
        System.out.println("  plusOperator()   -- a + \"-\" + b");
        System.out.println("  stringBuilder()  -- new StringBuilder(a).append(b).toString()\n");

        // ---- Discarded warmup, mirroring @Warmup(iterations = N) ----
        System.out.println("Warming up (" + warmupIterations + " discarded iterations)...");
        for (int w = 0; w < warmupIterations; w++) {
            runRoundPlusOperator(opsPerIteration, blackhole);
            runRoundStringBuilder(opsPerIteration, blackhole);
        }
        System.out.println("Warmup done -- discarding those timings entirely, exactly like JMH's @Warmup.\n");

        // ---- Measured iterations, mirroring @Measurement(iterations = N) ----
        long[] plusNanos = new long[measuredIterations];
        long[] builderNanos = new long[measuredIterations];

        System.out.println("Measuring (" + measuredIterations + " iterations, " + opsPerIteration + " ops each)...");
        for (int m = 0; m < measuredIterations; m++) {
            plusNanos[m] = timeRound(() -> runRoundPlusOperator(opsPerIteration, blackhole));
            builderNanos[m] = timeRound(() -> runRoundStringBuilder(opsPerIteration, blackhole));
            System.out.printf("  Iteration %d:  plusOperator=%,10d ns   stringBuilder=%,10d ns%n",
                    m + 1, plusNanos[m], builderNanos[m]);
        }

        printSummary("plusOperator", plusNanos, opsPerIteration);
        printSummary("stringBuilder", builderNanos, opsPerIteration);

        System.out.println("\nBlackhole sink final value (proves the loops' results were 'used' and not");
        System.out.println("eliminated as dead code): " + blackhole.sink);

        System.out.println("\nWhat this hand-rolled harness does NOT give you, that real JMH does:");
        System.out.println("  - No separate JVM forks per benchmark (@Fork) -- a single process's JIT/GC");
        System.out.println("    quirks can bias every number here in a way multiple forks would average out.");
        System.out.println("  - No confidence-interval/error-bar statistics -- Theory File 06 stresses that");
        System.out.println("    the Error column is the most-ignored, most-important part of reading results;");
        System.out.println("    this harness only prints min/avg/max, no rigorous statistical treatment.");
        System.out.println("  - No protection against this harness method ITSELF being JIT-optimized in ways");
        System.out.println("    that distort measurement (a generated JMH runner is specifically structured");
        System.out.println("    to avoid measuring its own harness overhead).");
        System.out.println("  - No -prof gc/-prof async integration for correlating results with GC/allocation.");
    }

    private void runRoundPlusOperator(int ops, SimpleBlackhole bh) {
        for (int i = 0; i < ops; i++) {
            // Reads 'left'/'right' through instance fields (not compile-time constants),
            // mirroring @State -- the JIT cannot constant-fold this away across calls.
            String result = left + "-" + right + i;
            bh.consume(result); // stand-in for JMH auto-consuming a @Benchmark method's return value
        }
    }

    private void runRoundStringBuilder(int ops, SimpleBlackhole bh) {
        for (int i = 0; i < ops; i++) {
            String result = new StringBuilder(left).append('-').append(right).append(i).toString();
            bh.consume(result);
        }
    }

    @FunctionalInterface
    private interface TimedRound {
        void run();
    }

    private long timeRound(TimedRound round) {
        long start = System.nanoTime();
        round.run();
        return System.nanoTime() - start;
    }

    private void printSummary(String label, long[] nanos, int opsPerIteration) {
        long min = Long.MAX_VALUE, max = Long.MIN_VALUE, sum = 0;
        for (long n : nanos) {
            min = Math.min(min, n);
            max = Math.max(max, n);
            sum += n;
        }
        double avg = (double) sum / nanos.length;
        double avgNsPerOp = avg / opsPerIteration;
        System.out.printf("%n%-14s over %d iterations: min=%,10d ns  avg=%,12.0f ns  max=%,10d ns  (~%.2f ns/op)%n",
                label, nanos.length, min, avg, max, avgNsPerOp);
        System.out.println("(A real JMH run reports Score +/- Error in ns/op with a stated confidence level --");
        System.out.println("this min/avg/max is a much cruder stand-in; see Part A's comment for the real thing.)");
    }

    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }

    /*
     * =====================================================================
     * PART A -- REAL JMH BENCHMARK CODE (illustrative, requires JMH on the classpath)
     * =====================================================================
     *
     * Maven dependency (pom.xml):
     *
     *     <dependency>
     *         <groupId>org.openjdk.jmh</groupId>
     *         <artifactId>jmh-core</artifactId>
     *         <version>1.37</version>
     *     </dependency>
     *     <dependency>
     *         <groupId>org.openjdk.jmh</groupId>
     *         <artifactId>jmh-generator-annprocess</artifactId>
     *         <version>1.37</version>
     *         <scope>provided</scope>
     *     </dependency>
     *
     * Gradle (build.gradle, using the jmh plugin is the common alternative to raw deps):
     *
     *     plugins { id 'me.champeau.jmh' version '0.7.2' }
     *     dependencies { jmh 'org.openjdk.jmh:jmh-core:1.37' }
     *
     * Typical run (Maven shade/assembly producing a runnable benchmarks.jar):
     *     mvn clean package
     *     java -jar target/benchmarks.jar StringConcatBenchmark
     *
     * --------------------------------------------------------------------
     *
     * import org.openjdk.jmh.annotations.*;
     * import org.openjdk.jmh.infra.Blackhole;
     * import java.util.concurrent.TimeUnit;
     *
     * @State(Scope.Thread)
     * @BenchmarkMode(Mode.AverageTime)
     * @OutputTimeUnit(TimeUnit.NANOSECONDS)
     * @Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
     * @Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
     * @Fork(value = 3, warmups = 1)
     * public class StringConcatBenchmark {
     *
     *     private String a = "hello";
     *     private String b = "world";
     *
     *     @Setup(Level.Trial)
     *     public void setup() {
     *         // expensive one-time fixture setup goes here, OUTSIDE the measured region --
     *         // never do this inline inside a @Benchmark method itself.
     *     }
     *
     *     @Benchmark
     *     public String plusOperator() {
     *         return a + "-" + b;              // returned value = "used" as far as JMH/JIT
     *                                          // must assume -- defeats dead-code elimination
     *                                          // without needing an explicit Blackhole here
     *     }
     *
     *     @Benchmark
     *     public String stringBuilder() {
     *         return new StringBuilder(a).append('-').append(b).toString();
     *     }
     *
     *     @Benchmark
     *     public void multipleResults(Blackhole bh) {
     *         // When a benchmark method produces MULTIPLE values or does side-effecting
     *         // work with no single return value, consume each one explicitly instead.
     *         bh.consume(a + "-" + b);
     *         bh.consume(new StringBuilder(a).append('-').append(b).toString());
     *     }
     *
     *     public static void main(String[] args) throws Exception {
     *         org.openjdk.jmh.Main.main(args);
     *     }
     * }
     *
     * --------------------------------------------------------------------
     * Expected result shape (Theory File 06, "Reading JMH Results Correctly"):
     *
     *   Benchmark                             Mode  Cnt    Score    Error  Units
     *   StringConcatBenchmark.plusOperator    avgt   15    8.421 ±  0.213  ns/op
     *   StringConcatBenchmark.stringBuilder   avgt   15    9.847 ±  0.531  ns/op
     *
     * Cnt = 15 here because @Measurement(iterations=5) x @Fork(value=3) = 15 total
     * measured iterations. Score is the mean; Error is half the 99.9% confidence
     * interval -- ALWAYS check whether the two ranges (Score +/- Error) overlap before
     * concluding one benchmark is really faster; a non-overlapping pair (as above,
     * roughly [8.21,8.63] vs [9.32,10.38]) supports a real conclusion, an overlapping
     * pair does not, regardless of which mean looks numerically bigger.
     * =====================================================================
     */
}
