/*
 * 06_completablefuture_modern_concurrency_demo.java
 *
 * Demonstrates:
 *   1. Creating CompletableFutures -- supplyAsync/runAsync, an explicit executor, completedFuture
 *   2. Chaining -- thenApply vs thenCompose, thenCombine, thenAccept/thenRun
 *   3. Combining many futures -- allOf (collect results) and anyOf (first to finish)
 *   4. Exception handling -- exceptionally, handle, whenComplete
 *   5. ForkJoinPool -- a RecursiveTask doing divide-and-conquer parallel sum, with work-stealing
 *   6. Parallel streams -- a quick concurrency/timing comparison against a sequential stream
 *
 * Covers Theory chapter:
 *   05) Multithreading and Concurrency/Theory/06 CompletableFuture and Modern Concurrency.md
 *
 * Compile: javac 06_completablefuture_modern_concurrency_demo.java
 * Run:     java CompletableFutureModernConcurrencyDemo
 */

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

class CompletableFutureModernConcurrencyDemo {

    public static void main(String[] args) throws Exception {
        demoCreatingFutures();
        demoChaining();
        demoCombiningManyFutures();
        demoExceptionHandling();
        demoForkJoinPool();
        demoParallelStreams();
        System.out.println("\nAll CompletableFuture and Modern Concurrency demos completed.");
    }

    private static void printSection(String title) {
        System.out.println("\n" + "=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }

    // -------------------------------------------------------------------
    // 1) Creating CompletableFutures
    // -------------------------------------------------------------------

    private static void demoCreatingFutures() throws Exception {
        printSection("1) Creating CompletableFutures -- supplyAsync/runAsync/completedFuture");

        CompletableFuture<Void> ranAsync = CompletableFuture.runAsync(() ->
                System.out.println("runAsync task on " + Thread.currentThread().getName()));
        ranAsync.get();   // wait for it so console output stays ordered for the demo

        CompletableFuture<Integer> supplied = CompletableFuture.supplyAsync(() -> {
            System.out.println("supplyAsync computing on " + Thread.currentThread().getName());
            return 21 * 2;
        });
        System.out.println("supplyAsync result: " + supplied.get());

        // Explicit executor -- best practice for blocking/slow work so it doesn't starve the common pool
        ExecutorService ioPool = Executors.newFixedThreadPool(2);
        CompletableFuture<String> withExecutor = CompletableFuture.supplyAsync(() -> {
            sleep(50);
            return "result-from-dedicated-pool";
        }, ioPool);
        System.out.println("supplyAsync(with explicit executor) result: " + withExecutor.get());
        ioPool.shutdown();

        CompletableFuture<Integer> already = CompletableFuture.completedFuture(42);
        System.out.println("completedFuture value (no async work at all): " + already.get());
    }

    // -------------------------------------------------------------------
    // 2) Chaining -- thenApply, thenCompose, thenCombine, thenAccept/thenRun
    // -------------------------------------------------------------------

    private static void demoChaining() throws Exception {
        printSection("2) Chaining -- thenApply vs thenCompose, thenCombine, thenAccept/thenRun");

        // thenApply -- synchronous-style transform chained onto the result
        CompletableFuture<Integer> base = CompletableFuture.supplyAsync(() -> 10);
        CompletableFuture<String> transformed = base
                .thenApply(x -> x * 2)                 // 20
                .thenApply(x -> "value=" + x);         // "value=20"
        System.out.println("thenApply chain result: " + transformed.get());

        // thenCompose -- next step is ITSELF asynchronous; flattens instead of nesting futures
        CompletableFuture<Integer> userId = CompletableFuture.supplyAsync(() -> 7);
        CompletableFuture<String> userOrderSummary = userId.thenCompose(id ->
                fetchOrderSummaryAsync(id));            // returns CompletableFuture<String>, not nested
        System.out.println("thenCompose chain result: " + userOrderSummary.get());

        // thenCombine -- merge results of two INDEPENDENT futures once both complete
        CompletableFuture<Integer> priceFuture = CompletableFuture.supplyAsync(() -> 100);
        CompletableFuture<Double> taxRateFuture = CompletableFuture.supplyAsync(() -> 0.08);
        CompletableFuture<Double> total = priceFuture.thenCombine(taxRateFuture,
                (price, taxRate) -> price * (1 + taxRate));
        System.out.println("thenCombine result (price * (1+tax)): " + total.get());

        // thenAccept / thenRun -- react without producing a further value
        CompletableFuture<Void> accepted = base.thenAccept(x -> System.out.println("thenAccept saw: " + x));
        accepted.get();
        CompletableFuture<Void> ran = base.thenRun(() -> System.out.println("thenRun -- ignores the value entirely"));
        ran.get();
    }

    private static CompletableFuture<String> fetchOrderSummaryAsync(int userId) {
        return CompletableFuture.supplyAsync(() -> {
            sleep(20);
            return "order-summary-for-user-" + userId;
        });
    }

    // -------------------------------------------------------------------
    // 3) Combining many futures -- allOf and anyOf
    // -------------------------------------------------------------------

    private static void demoCombiningManyFutures() throws Exception {
        printSection("3) allOf (collect all results) and anyOf (first to finish)");

        List<CompletableFuture<Integer>> futures = List.of(
                CompletableFuture.supplyAsync(() -> { sleep(30); return 1; }),
                CompletableFuture.supplyAsync(() -> { sleep(10); return 2; }),
                CompletableFuture.supplyAsync(() -> { sleep(20); return 3; })
        );

        // anyOf must race against FRESH, still-pending futures -- reusing already-joined ones from allOf
        // below would just replay their cached (instant) results in whatever order the array happens to give.
        List<CompletableFuture<Integer>> raceFutures = List.of(
                CompletableFuture.supplyAsync(() -> { sleep(30); return 1; }),
                CompletableFuture.supplyAsync(() -> { sleep(10); return 2; }),
                CompletableFuture.supplyAsync(() -> { sleep(20); return 3; })
        );
        CompletableFuture<Object> any = CompletableFuture.anyOf(raceFutures.toArray(new CompletableFuture[0]));
        System.out.println("anyOf -- first future to complete produced: " + any.get()
                + " (usually 2, since it sleeps shortest at 10ms -- but common-pool thread startup jitter"
                + " on such short sleeps means this isn't 100% guaranteed every run)");

        CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        // allOf itself yields Void -- join() the ORIGINAL futures individually to get their results
        CompletableFuture<List<Integer>> allResults = all.thenApply(v ->
                futures.stream().map(CompletableFuture::join).collect(java.util.stream.Collectors.toList()));
        System.out.println("allOf -- collected results in original order: " + allResults.get());
    }

    // -------------------------------------------------------------------
    // 4) Exception handling -- exceptionally, handle, whenComplete
    // -------------------------------------------------------------------

    private static void demoExceptionHandling() throws Exception {
        printSection("4) Exception handling -- exceptionally / handle / whenComplete");

        CompletableFuture<Integer> failing = CompletableFuture.supplyAsync(() -> {
            throw new RuntimeException("simulated failure");
        });

        // exceptionally -- runs ONLY on failure, supplies a fallback value
        CompletableFuture<Integer> recovered = failing.exceptionally(ex -> {
            System.out.println("exceptionally: recovering from '" + ex.getMessage() + "'");
            return -1;
        });
        System.out.println("exceptionally result: " + recovered.get());

        // handle -- ALWAYS runs, sees (result, exception), exactly one is non-null
        CompletableFuture<Integer> ok = CompletableFuture.supplyAsync(() -> 50);
        CompletableFuture<Integer> handledOk = ok.handle((result, ex) -> {
            if (ex != null) return -1;
            return result * 2;
        });
        CompletableFuture<Integer> failingAgain = CompletableFuture.supplyAsync(() -> {
            throw new IllegalStateException("boom");
        });
        CompletableFuture<Integer> handledFail = failingAgain.handle((result, ex) -> {
            if (ex != null) {
                System.out.println("handle: saw exception, recovering with -1");
                return -1;
            }
            return result * 2;
        });
        System.out.println("handle on success path: " + handledOk.get());
        System.out.println("handle on failure path: " + handledFail.get());

        // whenComplete -- side-effect only observer, does NOT recover; rethrows via get()
        CompletableFuture<Integer> observed = CompletableFuture.<Integer>supplyAsync(() -> {
            throw new RuntimeException("still fails");
        }).whenComplete((result, ex) -> {
            if (ex != null) System.out.println("whenComplete: logged failure -- " + ex.getCause());
            else System.out.println("whenComplete: logged success -- " + result);
        });
        try {
            observed.get();
        } catch (ExecutionException e) {
            System.out.println("whenComplete did not recover -- get() still threw: " + e.getCause());
        }
    }

    // -------------------------------------------------------------------
    // 5) ForkJoinPool -- divide-and-conquer with RecursiveTask
    // -------------------------------------------------------------------

    private static void demoForkJoinPool() {
        printSection("5) ForkJoinPool -- RecursiveTask divide-and-conquer parallel sum");

        int size = 2_000_000;
        int[] data = new int[size];
        for (int i = 0; i < size; i++) data[i] = 1;   // sum should equal 'size'

        ForkJoinPool pool = new ForkJoinPool();        // defaults to parallelism = availableProcessors()
        long start = System.nanoTime();
        long sum = pool.invoke(new SumTask(data, 0, data.length));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        System.out.println("ForkJoinPool parallel sum of " + size + " ones = " + sum
                + " (expected " + size + "), took " + elapsedMs + "ms, parallelism="
                + pool.getParallelism());
        pool.shutdown();
    }

    /** Classic fork/join divide-and-conquer sum: splits the array in half until small enough to sum directly. */
    private static class SumTask extends RecursiveTask<Long> {
        private static final int THRESHOLD = 10_000;   // "small enough -- just compute directly" cutoff
        private final int[] arr;
        private final int start, end;

        SumTask(int[] arr, int start, int end) {
            this.arr = arr;
            this.start = start;
            this.end = end;
        }

        @Override
        protected Long compute() {
            int length = end - start;
            if (length <= THRESHOLD) {
                long sum = 0;
                for (int i = start; i < end; i++) sum += arr[i];
                return sum;
            }
            int mid = start + length / 2;
            SumTask left = new SumTask(arr, start, mid);
            SumTask right = new SumTask(arr, mid, end);
            left.fork();                      // schedule left half asynchronously (may be work-stolen)
            long rightResult = right.compute(); // compute right half on this thread
            long leftResult = left.join();       // wait for the forked left half
            return leftResult + rightResult;
        }
    }

    // -------------------------------------------------------------------
    // 6) Parallel streams -- quick concurrency/timing comparison
    // -------------------------------------------------------------------

    private static void demoParallelStreams() {
        printSection("6) Parallel streams -- sequential vs parallel timing comparison");

        int upperBound = 20_000_000;

        long startSeq = System.nanoTime();
        long sumSeq = IntStream.rangeClosed(1, upperBound)
                .mapToLong(i -> (long) i)
                .sum();
        long elapsedSeqMs = (System.nanoTime() - startSeq) / 1_000_000;

        long startPar = System.nanoTime();
        long sumPar = IntStream.rangeClosed(1, upperBound)
                .parallel()
                .mapToLong(i -> (long) i)
                .sum();
        long elapsedParMs = (System.nanoTime() - startPar) / 1_000_000;

        System.out.println("sequential sum=" + sumSeq + " took " + elapsedSeqMs + "ms");
        System.out.println("parallel   sum=" + sumPar + " took " + elapsedParMs + "ms"
                + " (uses ForkJoinPool.commonPool(), parallelism="
                + ForkJoinPool.commonPool().getParallelism() + ")");
        System.out.println("note: parallel is not guaranteed faster for every workload/machine -- "
                + "always measure on the real data size before assuming it's a win");

        // Demonstrate that forEach on a parallel stream does NOT guarantee encounter order
        AtomicInteger seen = new AtomicInteger(0);
        System.out.print("parallel forEach order (order not guaranteed): ");
        IntStream.rangeClosed(1, 8).parallel().forEach(i -> seen.incrementAndGet());
        System.out.println("(count processed: " + seen.get() + ", printed order intentionally omitted "
                + "since it varies run to run)");

        System.out.print("forEachOrdered forces encounter order: ");
        IntStream.rangeClosed(1, 8).parallel().forEachOrdered(i -> System.out.print(i + " "));
        System.out.println();
    }

    // -------------------------------------------------------------------
    // helper
    // -------------------------------------------------------------------

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
