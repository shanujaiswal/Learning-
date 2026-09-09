# Why the JVM Doesn't Just Compile Everything Up Front

--> Java source compiles to bytecode (File 04), and bytecode is initially run by the JVM's **interpreter** -- executing each instruction one at a time, which is simple and starts instantly but is far slower than native machine code. The **JIT (Just-In-Time) compiler** watches the running program and compiles the HOT parts -- methods and loops actually being executed frequently -- into real machine code at runtime, then swaps execution over to that machine code transparently.
--> This "compile only what's hot, and only once you've seen it run" strategy exists because: (1) most methods in a typical application run rarely or once, so compiling everything up front would waste time and memory on code that barely matters to overall performance; and (2) the JIT can use ACTUAL runtime information (which branch is usually taken, which concrete type actually shows up at a polymorphic call site, whether an object provably never escapes a method) to generate better machine code than a purely static, ahead-of-time compiler could, which never sees real execution.
--> This is also why Java programs have a **warmup period** -- code runs relatively slowly (interpreted) at first, and gets faster over the first seconds-to-minutes of execution as the JIT identifies and compiles hot spots. This single fact underlies both File 06's benchmarking methodology and a lot of real "why is my app slow for the first few minutes after deploy" investigations.

# C1 vs C2 -- Two Different Compilers

--> The HotSpot JVM (the JVM implementation in virtually every mainstream JDK) actually ships TWO JIT compilers with different design goals, used together via tiered compilation.

| | C1 (Client Compiler) | C2 (Server Compiler) |
|---|---|---|
| Optimization depth | Lighter -- fast to compile, modest optimization | Heavy -- aggressive optimization, much more analysis |
| Compile time | Fast | Slow (relatively) |
| Generated code quality | Good, not best | Best -- the highest-quality machine code the JVM produces |
| Profiling instrumentation | Adds counters/type profiling data to inform future C2 compilation | Consumes that profiling data to make aggressive, speculative optimizations |
| Historically used for | The old `-client` flag / fast startup scenarios | The old `-server` flag / long-running throughput scenarios |
| Modern role | Tier 1-3 in tiered compilation | Tier 4 in tiered compilation |

--> **Historically** (pre-Java 8 defaults), you picked ONE or the other via `-client`/`-server` flags -- C1 for fast-starting desktop apps, C2 for long-running server throughput. **Tiered compilation** (default since Java 8) uses BOTH together as stages of an escalating pipeline, getting fast startup AND eventually peak throughput from the same run.

# Tiered Compilation -- The Escalation Pipeline

```text
Tier 0: Interpreter
    --> Every method starts here. Slow, but zero compile latency -- runs immediately.
        The interpreter also collects invocation counts and basic profiling data as it runs.

Tier 1: C1, no profiling
    --> For methods deemed simple enough that further profiling won't change how they'd be compiled.

Tier 2: C1, limited profiling
    --> Intermediate step, less common to observe directly.

Tier 3: C1, full profiling
    --> Most methods land here first once "hot enough" to leave the interpreter --
        compiled quickly by C1, AND instrumented to gather rich profiling data
        (branch frequencies, actual types seen at call sites, etc.) for C2's benefit.

Tier 4: C2, fully optimized
    --> Once a method is hot enough (invocation/back-edge counters cross a threshold)
        AND C1's profiling data is available, C2 recompiles it using that data to
        make aggressive, sometimes speculative, optimizations -- the fastest machine
        code the JVM will ever produce for that method.
```

--> A method's typical journey: interpreted (Tier 0) → quickly C1-compiled with profiling (Tier 3) once it's been called "enough" times → later C2-recompiled (Tier 4) once it's PROVEN hot enough to be worth C2's heavier optimization cost, using the profiling data gathered during its Tier 3 life.
--> This is precisely why the SAME method can be compiled multiple times over a program's life, and why `-XX:+PrintCompilation` output (below) can show the same method name appear more than once at different tier numbers -- that's expected, not a bug.
--> **`-XX:-TieredCompilation`** disables this staged approach and goes straight to C2-only (the old `-server`-only behavior) -- rarely useful except for isolating tiered-compilation-specific behavior during investigation; **`-client`/pure-C1-only** configurations are largely historical at this point (and pure client-compiler-only JVM builds aren't shipped by mainstream vendors anymore).

# Hot Spot Detection -- How "Hot" Is Decided

--> The JVM (hence the name "HotSpot") tracks two counters per method:
--> **Invocation counter** -- incremented on every call to the method.
--> **Back-edge counter** -- incremented on every loop iteration (a "back edge" is a jump backward to the top of a loop) -- this exists SEPARATELY from the invocation counter because a method called only once but containing a million-iteration loop is just as hot as a method called a million times, and needs to be caught even though it "was only called once."
--> When either counter crosses a threshold (tunable via `-XX:CompileThreshold` in non-tiered mode; tiered mode uses its own internal, more dynamic thresholds), the method (or, for the back-edge case, potentially just the loop, via **On-Stack Replacement**) becomes a compilation candidate.
--> **On-Stack Replacement (OSR)** is the mechanism that lets a currently-running, still-interpreting long loop get swapped over to compiled code MID-EXECUTION, without waiting for the method to return and be called again -- essential for things like a single very long-running `main` loop that would otherwise never benefit from JIT compilation within one program run.

# Inlining

--> **Inlining** replaces a method call with the callee's actual body, directly in the caller's compiled code -- eliminating call overhead (stack frame setup, argument passing) and, more importantly, opening the door to further optimization ACROSS what used to be a call boundary (e.g. a caller can now see and eliminate work the callee did that turns out to be unnecessary given the caller's specific inputs). Inlining is widely considered the single most impactful JIT optimization, because it enables so many other optimizations to see across what were previously opaque call boundaries.
--> **Key flags and thresholds**:

| Flag | Default (typical) | Meaning |
|---|---|---|
| `-XX:MaxInlineSize` | 35 bytecodes | Max size of a method to inline normally |
| `-XX:FreqInlineSize` | 325 bytecodes | Max size for a method inlined because it's called very frequently ("hot" methods get a larger size budget) |
| `-XX:MaxInlineLevel` | 9 | Max depth of nested inlining (inlining a method that itself inlines another, etc.) |
| `-XX:MaxRecursiveInlineLevel` | 1 | Limits inlining of recursive calls to avoid unbounded expansion |

--> **Monomorphic vs polymorphic call sites matter enormously.** A virtual/interface method call (`invokevirtual`/`invokeinterface`, File 04) could in principle dispatch to any overriding implementation, which looks like it should defeat inlining entirely -- but C2 uses **Class Hierarchy Analysis (CHA)** plus runtime type profiling to inline speculatively anyway: if a call site has only ever seen ONE concrete type in practice (monomorphic) or a small handful (bimorphic/polymorphic, handled via an inlined type-check-and-branch), C2 can inline the likely implementation and guard it with a cheap type check, falling back to a real virtual call only if that guard fails. A call site that sees MANY different concrete types (megamorphic) generally can't be usefully inlined this way and falls back to a real virtual dispatch every time -- one real-world reason why heavy use of many-implementation interfaces at a single hot call site can measurably underperform a more concrete or better-guarded design.
--> **Inlining and File 04 connect directly here** -- a method's real inlining eligibility is judged by its BYTECODE size, not its source line count, which is exactly why "this method looks tiny in source" can still miss the inlining threshold once autoboxing, hidden accessor calls, or iterator machinery are accounted for.

# Deoptimization

--> C2 sometimes makes **speculative** optimizations based on assumptions that were true so far but aren't GUARANTEED forever -- e.g. "this call site has only ever seen class `Dog`, so inline `Dog.speak()` directly," or "this branch has never been taken, so compile assuming it never will be, and don't even generate code for that path." These speculative optimizations are exactly what makes C2's output so fast, but they come with an escape hatch: if the assumption is ever violated at runtime (a `Cat` shows up at that call site; the "never taken" branch IS taken), the JVM must **deoptimize** -- discard the compiled code for that method, fall back to the interpreter for that specific execution, and (depending on the reason) potentially recompile a more conservative version later that no longer makes the now-invalidated assumption.
--> **Common deoptimization triggers**: a previously-monomorphic call site starts seeing a new type; an `instanceof`/cast that was assumed to always succeed/fail flips; a branch assumed "cold" (never/rarely taken) is finally taken; class loading changes the type hierarchy a CHA-based optimization depended on (e.g. a new subclass is loaded that invalidates "this method has no overriders" reasoning).
--> **Deoptimization itself is not inherently bad** -- it's a normal, designed-in safety valve, and a single occasional deopt is invisible in practice. The real problem case is a **"deopt storm"** or thrashing: a call site or branch that keeps flip-flopping between assumptions, causing repeated compile → deoptimize → recompile → deoptimize cycles, which burns CPU on compilation itself and keeps the code running interpreted (slow) far more than it should. This is directly visible in JFR's `Deoptimization` events (File 02) or `-XX:+PrintCompilation` combined with `-XX:+TraceDeoptimization`.
--> **Fix pattern for deopt thrashing**: usually a design issue -- a call site being fed too many different concrete types (breaking the monomorphic/bimorphic assumption C2 relies on) is the most common cause; consolidating implementations, or accepting that a genuinely megamorphic call site just won't inline well, resolves it more reliably than fighting the JIT with flags.

# Escape Analysis

--> **Escape analysis** is C2's determination of whether an object's reference can possibly "escape" the method (or thread) that created it -- get stored somewhere else, passed to another thread, returned to a caller who might do the same, etc. If C2 can PROVE an object never escapes, several powerful optimizations become available:
--> **Scalar replacement** -- if an object never escapes and its fields are only ever accessed directly, the JIT can skip allocating it as a heap object entirely and instead treat its fields as if they were separate local variables/registers. This eliminates both the allocation cost AND the GC pressure that object would otherwise have caused -- a genuinely non-escaping short-lived helper object (a common pattern: small immutable "tuple" or coordinate-like classes created and discarded inside a hot loop) can end up costing effectively nothing at runtime despite looking like a heap allocation in source.
--> **Stack allocation** (a related idea, though HotSpot primarily achieves the benefit via scalar replacement rather than literal stack allocation of the object) and **lock elision** -- if escape analysis proves an object is only ever visible to ONE thread, any `synchronized` block locking on that object can have its locking entirely removed (since no other thread could ever contend for that lock), eliminating real synchronization overhead for a lock that was structurally guaranteed to be uncontended.
--> Escape analysis is enabled by default (`-XX:+DoEscapeAnalysis`) in modern JDKs and works automatically -- there's no annotation to request it -- but it's fragile in a specific, useful-to-know way: escape analysis is per-compilation and speculative like other C2 optimizations, so a change that makes an object's escape status ambiguous (e.g. passing it to a call site the JIT can't fully analyze, or a megamorphic call) can silently lose this benefit, which is one of the reasons micro-benchmark results for "does allocating a small helper object here cost anything" can be surprisingly workload/call-site-shape-dependent rather than a fixed universal answer.

# Observing the JIT: `-XX:+PrintCompilation` and Friends

```text
java -XX:+PrintCompilation MyApp
```

```text
    123   1       3       java.lang.String::hashCode (60 bytes)
    145   2       4       com.myapp.OrderService::calculateTotal (85 bytes)
    198   3   n    0       java.lang.System::arraycopy (native)
    250   4       3   %   com.myapp.BatchProcessor::processAll (210 bytes)
    301   5       4       com.myapp.OrderService::calculateTotal (85 bytes)   made not entrant
```

--> Column meaning, left to right: **timestamp** (ms since JVM start) -- **compile ID** (a sequential counter) -- **tier/qualifiers** (`n` = native method wrapper, `%` = OSR compilation, `s` = synchronized, `!` = has exception handler) -- **tier number** (1-4, per the tiered pipeline above) -- **method name** -- **bytecode size**.
--> **`made not entrant`** appearing after a method's line means that compiled version has been DEOPTIMIZED (or superseded by a newer compilation) and the JVM will no longer enter it for new calls -- seeing the SAME method recompiled multiple times, especially interleaved with "made not entrant," is the visible signature of deopt activity worth investigating further.
--> Useful companion flags:

| Flag | Purpose |
|---|---|
| `-XX:+PrintInlining` | Shows what got inlined into what, and WHY something didn't inline (too big, callee not monomorphic enough, etc.) -- requires `-XX:+UnlockDiagnosticVMOptions` on some JDK versions |
| `-XX:+TraceDeoptimization` | Prints details every time a deoptimization happens and why |
| `-XX:+PrintCompilation -XX:+PrintTieredEvents` | More granular view of tier transitions |
| `-Xbatch` | Forces synchronous (non-background) compilation -- useful for making a small reproduction deterministic when investigating compilation itself, never for production |
| `-XX:CompileThreshold=N` | Non-tiered invocation-count threshold (rarely tuned directly in tiered mode) |

--> For most day-to-day investigation, JFR's `Compilation` and `Deoptimization` events (File 02) surface the same information on a proper timeline correlated with everything else, which is usually a more practical starting point than raw `PrintCompilation` log-scraping.

# Gotchas and Best Practices

--> **Never conclude "the JIT didn't optimize X" from source code alone** -- always verify with `-XX:+PrintCompilation`/`-XX:+PrintInlining`/JFR events; the JIT's actual decisions depend on runtime profiling data, real call-site polymorphism, and bytecode size, none of which are reliably guessable from source.
--> **Warmup is real and matters for benchmarking** (fully explored in File 06) -- any measurement taken before a hot method has reached its final compiled tier is measuring the interpreter or an intermediate C1 tier, not steady-state performance; this is the single most common methodology mistake in ad-hoc "let me just time this real quick" performance checks.
--> **Escape analysis and inlining benefits are call-site-shape-dependent, not universal properties of a class** -- the "same" small object-creating method can be scalar-replaced away at one call site and fully heap-allocated at another, depending on what else the JIT can prove there; don't generalize a benchmark result for one usage pattern to all usage patterns of the same code.
--> **Deopt storms are a real, diagnosable production performance problem**, distinct from ordinary GC or lock contention issues, and easy to miss if only looking at CPU/heap graphs -- a method that seems to have inexplicably variable performance, or where CPU time seems dominated by something not obviously "your code," is worth checking for `Deoptimization` events specifically.
--> **Tiered compilation thresholds are deliberately not something to routinely hand-tune** -- they're internally adaptive (informed by available CPU cores, current compile queue depth, etc.); reach for `-XX:TieredStopAtLevel=1` (C1-only, faster startup, lower peak throughput -- useful for short-lived CLI tools/serverless cold-start-sensitive workloads) or similar high-level flags rather than fiddling with raw counters, unless doing genuinely deep JIT-specific investigation.
--> **C2 compilation itself costs CPU** -- a burst of many methods becoming hot simultaneously (e.g. right after a deploy, or right after a cache warms and a previously-cold code path suddenly runs a lot) can cause a visible, temporary CPU spike from compiler threads themselves, separate from and in addition to the warmup slowness of the not-yet-compiled code -- both are "warmup cost," but they're different mechanisms worth distinguishing when explaining a post-deploy CPU/latency blip.
