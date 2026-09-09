# From `Future` to `CompletableFuture` -- What Was Missing

--> File 04's `Future<V>` has a fundamental limitation: the ONLY way to get its result is to call the blocking `get()` -- there's no way to say "run this callback WHEN the result is ready," no way to chain a follow-up computation onto it, and no built-in way to combine multiple futures together. `CompletableFuture<V>` (Java 8+) implements both `Future<V>` AND `CompletionStage<V>`, adding a rich set of composable, non-blocking chaining methods on top.

```text
Future<V>:                              CompletableFuture<V>:
- future.get() -- BLOCKS to read        - .thenApply(fn) -- chain a transform, non-blocking
- no chaining                           - .thenCompose(fn) -- chain another async step
- no combining multiple futures         - .thenCombine(other, fn) -- combine two futures' results
- no callback on completion             - .thenAccept/.thenRun -- react without blocking
- exceptions only surface at get()      - .exceptionally/.handle -- inline exception recovery
```

# Creating a `CompletableFuture`

```java
import java.util.concurrent.CompletableFuture;

// Run a task asynchronously with no result (like Runnable)
CompletableFuture<Void> voidFuture = CompletableFuture.runAsync(() ->
        System.out.println("running on " + Thread.currentThread().getName()));

// Run a task asynchronously that produces a result (like Callable/Supplier)
CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() -> {
    return 21 * 2;
});

// Already-completed future -- useful as a starting point in tests or fallback chains
CompletableFuture<Integer> done = CompletableFuture.completedFuture(42);
```

--> **Default executor:** without an explicit `Executor` argument, `supplyAsync`/`runAsync` (and the `*Async` chaining variants) run on the JVM-wide common `ForkJoinPool` (see below) -- fine for short, non-blocking work, but a poor choice for blocking I/O since it starves the shared pool used elsewhere in the JVM.

```java
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

ExecutorService ioPool = Executors.newFixedThreadPool(8);
CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> callSlowService(), ioPool);
//                                                                                          ^^^^^^
//                                                                 explicit executor -- best practice
//                                                                 for anything blocking or I/O-bound
```

# Chaining -- `thenApply`, `thenCompose`, `thenCombine`, `thenAccept`/`thenRun`

--> **`thenApply(Function<T,R>)`** -- transforms the result once it's available, like `.map()` on a stream; returns a new `CompletableFuture<R>`.

```java
CompletableFuture<Integer> base = CompletableFuture.supplyAsync(() -> 10);
CompletableFuture<Integer> doubled = base.thenApply(x -> x * 2);        // 20
CompletableFuture<String> asText = doubled.thenApply(x -> "value=" + x); // "value=20"
```

--> **`thenCompose(Function<T, CompletableFuture<R>>)`** -- for when the next step is ITSELF asynchronous (returns another `CompletableFuture`); flattens the nesting instead of producing a `CompletableFuture<CompletableFuture<R>>`. This is the async analogue of `flatMap`.

```java
CompletableFuture<User> userFuture = fetchUserAsync(id);           // CompletableFuture<User>
CompletableFuture<Order> orderFuture = userFuture.thenCompose(user ->
        fetchLatestOrderAsync(user.getId()));                       // avoids nested futures
```

--> **Gotcha -- `thenApply` vs `thenCompose`:** using `thenApply` where the mapping function itself returns a `CompletableFuture` gives you a `CompletableFuture<CompletableFuture<R>>` -- structurally valid but almost never what you want; use `thenCompose` whenever the next step is async.

--> **`thenCombine(CompletableFuture<U>, BiFunction<T,U,R>)`** -- combines the results of TWO independent futures once BOTH complete.

```java
CompletableFuture<Integer> priceFuture = fetchPriceAsync(itemId);
CompletableFuture<Double> taxRateFuture = fetchTaxRateAsync(region);

CompletableFuture<Double> total = priceFuture.thenCombine(taxRateFuture,
        (price, taxRate) -> price * (1 + taxRate));
```

--> **`thenAccept(Consumer<T>)`** -- consumes the result for a side effect, returns `CompletableFuture<Void>` (no further value). **`thenRun(Runnable)`** -- runs a follow-up action that doesn't need the result at all.

```java
base.thenAccept(x -> System.out.println("got: " + x));
base.thenRun(() -> System.out.println("finished, don't care about the value"));
```

--> **The `*Async` suffix on any chaining method** (`thenApplyAsync`, `thenComposeAsync`, ...) forces that stage to run on the common pool (or an explicit executor argument) instead of potentially running on whichever thread completed the PREVIOUS stage -- useful to keep long/blocking follow-up work off of latency-sensitive threads.

```java
base.thenApplyAsync(x -> slowTransform(x), ioPool);   // explicit executor for a slow step
```

# Combining Many Futures -- `allOf` and `anyOf`

```java
CompletableFuture<Integer> f1 = CompletableFuture.supplyAsync(() -> 1);
CompletableFuture<Integer> f2 = CompletableFuture.supplyAsync(() -> 2);
CompletableFuture<Integer> f3 = CompletableFuture.supplyAsync(() -> 3);

CompletableFuture<Void> all = CompletableFuture.allOf(f1, f2, f3);   // completes when ALL finish
all.join();                                                          // block until all done (unchecked)

CompletableFuture<Object> any = CompletableFuture.anyOf(f1, f2, f3); // completes when ANY ONE finishes first
```

--> **`allOf` returns `CompletableFuture<Void>`**, not a list of results -- you must still call `.join()`/`.get()` on each ORIGINAL future afterward to read individual results (a common idiom is to collect the futures into a `List`, `allOf` on them, then `.thenApply(v -> list.stream().map(CompletableFuture::join).toList())`).
--> **`join()` vs `get()`:** `join()` behaves like `get()` but throws an UNCHECKED `CompletionException` instead of a checked `ExecutionException`, which is why it's preferred inside lambdas (e.g. inside a stream's `.map()`) where checked exceptions are awkward to declare.

# Exception Handling -- `exceptionally`, `handle`, `whenComplete`

--> An exception thrown anywhere in a chain SHORT-CIRCUITS subsequent `thenApply`/`thenCompose`/etc. stages -- they're simply skipped, and the failure propagates until something handles it.

```java
CompletableFuture<Integer> risky = CompletableFuture.supplyAsync(() -> {
    if (Math.random() < 0.5) throw new RuntimeException("failed!");
    return 42;
});

// exceptionally -- recover with a fallback value, only invoked if an exception occurred
CompletableFuture<Integer> recovered = risky.exceptionally(ex -> {
    System.out.println("recovering from: " + ex.getMessage());
    return -1;
});

// handle -- ALWAYS runs, receives (result, exception) -- exactly one of the two is non-null
CompletableFuture<Integer> handled = risky.handle((result, ex) -> {
    if (ex != null) return -1;
    return result * 2;
});

// whenComplete -- observe (result, exception) for a side effect (e.g. logging), does NOT recover --
// re-throws the same exception if one occurred, and doesn't change the completed value
risky.whenComplete((result, ex) -> {
    if (ex != null) System.out.println("logged failure: " + ex);
    else System.out.println("logged success: " + result);
});
```

--> **`exceptionally` vs `handle` vs `whenComplete`:**

```text
exceptionally(Function<Throwable,T>)  -- runs ONLY on failure, produces a recovery value
handle(BiFunction<T,Throwable,R>)     -- ALWAYS runs, can inspect+recover from either outcome
whenComplete(BiConsumer<T,Throwable>) -- ALWAYS runs, side-effect only, doesn't change the outcome
```

--> **Gotcha:** forgetting to attach ANY exception handler means a failure just sits inside the `CompletableFuture` silently until something calls `get()`/`join()` on it (which then throws) -- unlike an uncaught exception on a plain thread, a failed `CompletableFuture` that's never joined can fail completely silently.

# `ForkJoinPool` -- Divide-and-Conquer Parallelism

--> `ForkJoinPool` is a specialized `ExecutorService` designed for tasks that recursively split into smaller subtasks ("fork") and then combine results ("join") -- classic divide-and-conquer. Its key feature is **work-stealing**: each worker thread has its own deque of subtasks, and an idle worker "steals" work from the TAIL of a busy worker's deque instead of sitting idle, which keeps all cores busy even when subtasks finish at very different times.

```java
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.ForkJoinPool;

class SumTask extends RecursiveTask<Long> {
    private final int[] arr; private final int start, end;
    SumTask(int[] arr, int start, int end) { this.arr = arr; this.start = start; this.end = end; }

    @Override
    protected Long compute() {
        if (end - start <= 1000) {                 // small enough -- compute directly
            long sum = 0;
            for (int i = start; i < end; i++) sum += arr[i];
            return sum;
        }
        int mid = (start + end) / 2;
        SumTask left = new SumTask(arr, start, mid);
        SumTask right = new SumTask(arr, mid, end);
        left.fork();                                // schedule left half asynchronously
        long rightResult = right.compute();          // compute right half on THIS thread
        long leftResult = left.join();                // wait for the forked half
        return leftResult + rightResult;
    }
}

ForkJoinPool pool = new ForkJoinPool();      // defaults to parallelism = availableProcessors()
long total = pool.invoke(new SumTask(bigArray, 0, bigArray.length));
```

--> **`RecursiveTask<V>`** returns a result; **`RecursiveAction`** is the void-returning counterpart (same fork/join shape, `compute()` returns nothing).
--> **The common pool:** `ForkJoinPool.commonPool()` is a JVM-wide, lazily-initialized shared pool used as the default executor for `CompletableFuture`'s `*Async` methods AND for parallel streams (below) -- sized by default to `availableProcessors() - 1`.
--> **Best practice -- threshold sizing:** splitting all the way down to trivially small units adds pure overhead (task object creation, scheduling); pick a "do it directly" threshold (like the `<= 1000` above) that balances parallelism against per-task overhead.
--> **Gotcha -- blocking calls on common-pool threads:** since `CompletableFuture` and parallel streams share the SAME common pool by default, a blocking I/O call inside one can starve unrelated parallel-stream or `CompletableFuture` work elsewhere in the same JVM -- use a dedicated executor for blocking work rather than relying on the common pool.

# Parallel Streams -- Concurrency Notes

--> `.parallelStream()` (or `.stream().parallel()`) splits a stream's elements across the common `ForkJoinPool` and processes them concurrently, using the same fork/join work-stealing machinery under the hood.

```java
long sum = IntStream.rangeClosed(1, 10_000_000)
        .parallel()
        .mapToLong(i -> (long) i * i)
        .sum();
```

--> **Not automatically a win:** parallel streams add coordination/splitting overhead, so they only pay off for LARGE data sets with CPU-bound per-element work; for small collections or I/O-bound operations per element, sequential streams are often faster AND avoid tying up the shared common pool.
--> **Shared pool danger, again:** by default `.parallelStream()` also runs on `ForkJoinPool.commonPool()` -- the same pool as `CompletableFuture`'s async default -- so heavy parallel-stream usage and heavy `CompletableFuture` usage elsewhere in the same JVM can contend with and starve each other. A custom parallelism can be forced by submitting the stream operation into a separately-sized `ForkJoinPool` via `pool.submit(() -> ...).get()`.
--> **Side effects and ordering:** the lambda passed to a parallel stream's operations should be stateless and non-interfering (no shared mutable state without synchronization -- same rule as file 02's mutable shared state warnings); operations like `forEach` on a parallel stream do NOT guarantee encounter order, unlike `forEachOrdered` (which forces order at the cost of losing some parallelism benefit).

# Common Gotchas and Best Practices Recap

--> **Use `thenCompose`, not `thenApply`, when the next step is itself asynchronous** -- otherwise you get an awkward nested `CompletableFuture<CompletableFuture<R>>`.
--> **Supply an explicit `Executor` for blocking/slow work** -- the default common pool is shared JVM-wide with parallel streams and other `CompletableFuture` chains, and blocking it starves unrelated work.
--> **A failed `CompletableFuture` fails silently until observed** -- always attach `exceptionally`/`handle`, or eventually call `get()`/`join()`, or a failure can go unnoticed entirely.
--> **`allOf` gives you a `Void` future, not results** -- collect and `join()` the original futures individually to get their values.
--> **`join()` is the unchecked-exception sibling of `get()`** -- prefer it inside lambdas/streams where checked exceptions are awkward.
--> **`ForkJoinPool` work-stealing shines for divide-and-conquer, CPU-bound recursive work** -- pick a sensible "small enough, don't split further" threshold to avoid excess overhead.
--> **Parallel streams are not a free win** -- validate with real measurements on real data sizes; small collections or I/O-heavy per-element work often regress under `.parallel()`.
