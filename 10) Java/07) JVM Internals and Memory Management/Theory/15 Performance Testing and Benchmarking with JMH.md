# Why Microbenchmarking Java Is Genuinely Hard

--> It's tempting to "benchmark" a small piece of code by wrapping it in a loop, timing with `System.nanoTime()` before and after, and calling it done. On the JVM, this naive approach is reliably WRONG, in specific, well-understood ways, because of exactly the machinery covered in File 05 -- the JIT doesn't run your code the same way twice in a row.
--> **JMH (Java Microbenchmark Harness)** is the OpenJDK team's own tool, built specifically to sidestep these pitfalls -- written by the same engineers who build the JIT compiler, precisely because they needed a trustworthy way to measure JIT-compiled code correctly. It's the de facto standard for any serious Java microbenchmark and ships as a Maven/Gradle dependency plus an annotation processor/build plugin that generates the actual benchmark-running code.

# Pitfall 1: JIT Warmup

--> As covered in File 05, code starts interpreted and only reaches peak (C2, Tier 4) performance after enough invocations. A naive "time this loop of 1000 calls" benchmark spends much of its measured time in SLOWER interpreted or C1-compiled code, and the measurement is really "how fast is warmup," not "how fast is this code at steady state" -- the number one source of misleading ad-hoc Java benchmarks.
--> **JMH's fix**: explicit, configurable **warmup iterations** that run and are DISCARDED before any measurement begins, specifically to let the JIT reach steady state first.

```java
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
```

# Pitfall 2: Dead Code Elimination

--> If a benchmark computes a result and never uses it, the JIT is fully entitled to notice the result is unused and simply DELETE the entire computation -- correctly, per the language semantics (nothing observable changes), but disastrously for a benchmark that was trying to measure that exact computation's cost.

```java
// BROKEN: result is never used -- the JIT can (and often will) optimize the whole call away
for (int i = 0; i < 1000000; i++) {
    expensiveComputation(x);   // no-op as far as the JIT can tell -- may vanish entirely
}
```

--> **JMH's fix**: a benchmark method should RETURN its result (JMH consumes it automatically), or, when a method produces multiple values or side-effecting work, accept a **`Blackhole`** parameter and explicitly `consume()` each value.

```java
@Benchmark
public int measureAdd() {
    return expensiveComputation(x);   // returned value = "used" as far as the JIT must assume
}

@Benchmark
public void measureMultiple(Blackhole bh) {
    bh.consume(expensiveComputation(x));
    bh.consume(anotherComputation(x));
}
```

--> **`Blackhole` isn't just "print the value" or "assign to a field"** -- naive alternatives like storing to a `public` field can still, in principle, be optimized away if the JIT can prove nothing ever reads that field back, or can introduce their OWN measurement noise (a volatile write has real cost). `Blackhole.consume()` is implemented specifically to reliably prevent dead-code elimination with minimal, well-understood overhead of its own, which is exactly the kind of subtle correctness detail that makes rolling your own benchmark harness risky.

# Pitfall 3: Constant Folding

--> If the JIT can prove an input never varies, it can pre-compute the "computation" at compile time and just use the constant answer -- again, correct per language semantics, but not what a benchmark intended to measure.

```java
// BROKEN: x is a compile-time-visible constant -- the JIT may fold the whole computation away
private static final int x = 42;

@Benchmark
public int measure() {
    return expensiveComputation(x);
}
```

--> **JMH's fix**: benchmark inputs should come from **`@State`** fields, which JMH deliberately keeps the JIT from treating as compile-time constants (by construction -- they're read through an object the JIT can't fully constant-fold across benchmark invocations), so the computation being measured can't be silently precomputed away.

# Core JMH Annotations

## `@Benchmark`

--> Marks a method as an actual benchmark to run. JMH's annotation processor generates real, separately-compiled benchmark-runner classes from these at build time -- benchmarks are NOT run by reflection at runtime, specifically so the generated harness code itself is subject to normal JIT compilation and doesn't introduce reflection overhead into the measurement.

## `@State`

--> Marks a class holding benchmark input/fixture data, with a required **scope**:

```java
@State(Scope.Thread)   // one instance per benchmark thread (the default and most common)
public class BenchmarkState {
    int x = 42;
    List<Integer> data;

    @Setup(Level.Trial)
    public void setup() {
        data = generateTestData();
    }
}

@Benchmark
public int measure(BenchmarkState state) {
    return expensiveComputation(state.x);
}
```

| Scope | Meaning |
|---|---|
| `Scope.Thread` | One instance per benchmark thread -- default, use for most single-threaded benchmarks |
| `Scope.Benchmark` | One shared instance across all threads -- for measuring contended/shared-state scenarios |
| `Scope.Group` | One instance per thread group, for `@Group`-based producer/consumer-style benchmarks |

## `@Setup` and `@TearDown`

--> Run fixture setup/cleanup code, at a chosen **level**, OUTSIDE the measured region -- critical, since anything expensive done inside a `@Benchmark` method itself (like generating test data) would pollute the measurement.

| Level | When it runs |
|---|---|
| `Level.Trial` | Once before/after an entire benchmark run (all iterations) -- most common, for expensive one-time fixture setup |
| `Level.Iteration` | Once before/after each measurement iteration |
| `Level.Invocation` | Before/after EVERY single benchmark call -- rarely appropriate, since its own overhead can dominate a fast benchmark; JMH's own docs warn against it except for specific cases |

## `@Warmup` and `@Measurement`

--> As shown above -- control how many iterations of each kind run and for how long. Typical defaults are modest (a handful of iterations, one second each); real investigations often explicitly widen these (more iterations, longer each) especially for benchmarks with longer or less predictable warmup behavior.

## `@BenchmarkMode` and `@OutputTimeUnit`

```java
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class MyBenchmark { ... }
```

| Mode | Measures |
|---|---|
| `Throughput` | Operations completed per unit time -- higher is better |
| `AverageTime` | Average time per operation -- lower is better |
| `SampleTime` | Distribution of individual operation times (including percentiles) -- lower is better |
| `SingleShotTime` | Time for a single invocation with no warmup -- specifically for measuring COLD/first-call performance (the opposite of what the other modes intentionally avoid measuring) |
| `All` | Runs all of the above |

## `@Fork`

```java
@Fork(value = 3, warmups = 1)
```

--> Runs the ENTIRE benchmark in a fresh, separate JVM process, `value` times, and averages/reports across forks -- because JIT compilation decisions, and even things like memory layout, can vary somewhat between JVM runs; a single-process run risks reporting one process's particular (possibly atypical) JIT/GC behavior as if it were the general answer. `warmups` discards entire fork(s) as JVM-level warmup, on top of per-fork iteration warmup.

## A Complete Example

```java
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3)
public class StringConcatBenchmark {

    private String a = "hello";
    private String b = "world";

    @Benchmark
    public String plusOperator() {
        return a + b;
    }

    @Benchmark
    public String stringBuilder() {
        return new StringBuilder(a).append(b).toString();
    }
}
```

```text
public static void main(String[] args) throws Exception {
    org.openjdk.jmh.Main.main(args);   // or run via the jmh-maven-plugin / gradle jmh plugin
}
```

# Reading JMH Results Correctly

```text
Benchmark                             Mode  Cnt    Score    Error  Units
StringConcatBenchmark.plusOperator    avgt   15    8.421 ±  0.213  ns/op
StringConcatBenchmark.stringBuilder   avgt   15    9.847 ±  0.531  ns/op
```

--> **`Cnt`** -- total measurement iterations across all forks (5 iterations × 3 forks = 15 here) -- always sanity-check this matches the configured `@Fork`/`@Measurement` settings.
--> **`Score`** -- the reported mean (for `AverageTime`/`Throughput` modes).
--> **`Error`** -- half the 99.9% confidence interval by default -- **the single most-ignored column in casual JMH reading**. `8.421 ± 0.213` means the true value is estimated to lie in roughly `[8.208, 8.634]` at that confidence level; if two benchmarks' score ranges OVERLAP once error is accounted for, they are NOT reliably distinguishable from this run, regardless of which mean number happens to look bigger.
--> In the example above, `8.421 ± 0.213` (plusOperator, range ~8.2-8.63) and `9.847 ± 0.531` (stringBuilder, range ~9.3-10.4) don't overlap, so this run DOES support "plusOperator was faster here" -- but a result like `8.421 ± 0.900` vs `8.700 ± 0.850` would NOT support a confident conclusion either way, despite one mean being numerically larger.
--> **A high `Error` relative to `Score`** usually means: not enough iterations/forks, a genuinely noisy environment (other processes competing for CPU, thermal throttling, a shared/virtualized CI runner), or a benchmark whose real-world variance is just high -- the fix is more forks/iterations and a quieter measurement environment before trusting the number, not ignoring the error bar.

# Gotchas and Best Practices

--> **Never benchmark in a shared/noisy environment and trust small differences.** A laptop running a browser and Slack in the background, or a shared CI runner, introduces enough scheduling noise to swamp genuinely small (single-digit-percent) effects; reserve strong conclusions for a quiet, dedicated machine, and treat noisy-environment results as directional at best.
--> **Isolate what you actually want to measure.** A common mistake is benchmarking a method that does I/O, unpredictable-timing work, or heavy allocation alongside the thing actually under test -- the JIT/GC-related noise from the unrelated part can dominate the number, hiding the real signal.
--> **Beware benchmarking something the JIT will treat completely differently at realistic call-site polymorphism than in the tight, monomorphic loop JMH runs.** Per File 05, inlining and escape-analysis decisions are call-site-shape-dependent -- a benchmark that calls ONE implementation of an interface a million times in a tight loop can get inlining/monomorphic-dispatch benefits that the SAME code never gets in the real application, where that call site sees many implementations. A microbenchmark answers "how fast is this code in isolation, under JMH's specific calling pattern" -- not automatically "how fast is this code in my actual application."
--> **Don't skip `@Fork`.** A single-JVM benchmark risk conflating one process's specific (possibly lucky or unlucky) JIT/GC behavior with the general answer; multiple forks and averaging across them is what makes a result trustworthy rather than anecdotal.
--> **Match warmup length to the code's actual warmup behavior**, not a fixed default -- code with unusually complex polymorphism, large methods near inlining thresholds, or heavy allocation patterns can take longer than the default 5x1s warmup to reach true steady state; if results seem to still be trending across measurement iterations (visible in per-iteration JMH output, not just the final averaged summary), extend warmup rather than trusting an average that's still including warmup-phase behavior.
--> **A microbenchmark is a precision instrument for a narrow question, not a substitute for real load testing.** JMH answers "which of these two snippets is faster, isolated, under JIT-favorable conditions" -- it does not answer "will my service meet its p99 latency SLA under production traffic," which depends on GC behavior under real allocation rates, real thread contention, real call-site polymorphism, and real I/O -- questions that belong to load/integration testing and to the production-facing tools in Files 01-03, not to JMH.
--> **Profiler integration exists and is underused** -- JMH supports `-prof` options (e.g. `-prof gc` to report allocation/GC stats per benchmark, `-prof async` if async-profiler is available) that attach lightweight profiling DURING the benchmark run itself, directly answering "is this benchmark's difference actually about allocation/GC pressure" rather than requiring a separate profiling pass afterward.
