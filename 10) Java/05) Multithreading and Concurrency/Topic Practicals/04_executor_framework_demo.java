/*
 * 04_executor_framework_demo.java
 *
 * Demonstrates:
 *   1. Basic ExecutorService usage: execute() vs submit(), and proper shutdown
 *   2. Callable + Future -- getting results back, get() with timeout, cancel()
 *   3. invokeAll() and invokeAny()
 *   4. ScheduledExecutorService -- one-shot delay and fixed-rate scheduling
 *   5. A ThreadPoolExecutor built directly with a bounded queue and rejection policy
 *
 * Covers Theory chapter:
 *   05) Multithreading and Concurrency/Theory/04 Executor Framework and Thread Pools.md
 *
 * Compile: javac 04_executor_framework_demo.java
 * Run:     java ExecutorFrameworkDemo
 */

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

class ExecutorFrameworkDemo {

    public static void main(String[] args) throws Exception {
        demoBasicExecutorUsage();
        demoCallableAndFuture();
        demoInvokeAllAndInvokeAny();
        demoScheduledExecutor();
        demoThreadPoolExecutorWithRejection();
        System.out.println("\nAll Executor Framework demos completed.");
    }

    private static void printSection(String title) {
        System.out.println("\n" + "=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }

    // -------------------------------------------------------------------
    // 1) Basic ExecutorService usage + proper shutdown idiom
    // -------------------------------------------------------------------

    private static void demoBasicExecutorUsage() throws InterruptedException {
        printSection("1) ExecutorService basics -- execute() vs submit(), shutdown idiom");

        ExecutorService executor = Executors.newFixedThreadPool(4);

        executor.execute(() ->
                System.out.println("fire-and-forget task on " + Thread.currentThread().getName()));

        Future<?> submitted = executor.submit(() ->
                System.out.println("submitted task on " + Thread.currentThread().getName()));

        // Standard graceful shutdown idiom
        shutdownAndAwait(executor);
        System.out.println("submitted task future isDone: " + submitted.isDone());
    }

    private static void shutdownAndAwait(ExecutorService executor) throws InterruptedException {
        executor.shutdown();                              // stop accepting new tasks
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                List<Runnable> dropped = executor.shutdownNow();
                System.out.println("Forced shutdown; tasks never started: " + dropped.size());
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // -------------------------------------------------------------------
    // 2) Callable + Future
    // -------------------------------------------------------------------

    private static void demoCallableAndFuture() throws Exception {
        printSection("2) Callable + Future -- results, timeout, and cancellation");

        ExecutorService executor = Executors.newFixedThreadPool(2);

        Callable<Integer> multiply = () -> {
            Thread.sleep(200);
            return 21 * 2;
        };
        Future<Integer> future = executor.submit(multiply);
        System.out.println("doing other work while task runs in background...");
        Integer result = future.get();                    // blocks until ready
        System.out.println("result via future.get(): " + result);

        // get() with a timeout
        Future<Integer> slow = executor.submit(() -> {
            Thread.sleep(2000);
            return 99;
        });
        try {
            slow.get(100, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            System.out.println("future.get(100ms) timed out as expected -- task still running in background");
        }
        slow.cancel(true);   // clean up: cancel the still-running task, interrupting it

        // Exception propagation via ExecutionException
        Future<Integer> failing = executor.submit(() -> {
            throw new IllegalStateException("boom");
        });
        try {
            failing.get();
        } catch (ExecutionException e) {
            System.out.println("Caught ExecutionException wrapping: " + e.getCause());
        }

        shutdownAndAwait(executor);
    }

    // -------------------------------------------------------------------
    // 3) invokeAll() and invokeAny()
    // -------------------------------------------------------------------

    private static void demoInvokeAllAndInvokeAny() throws InterruptedException {
        printSection("3) invokeAll() and invokeAny()");

        ExecutorService executor = Executors.newFixedThreadPool(3);

        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            final int n = i;
            tasks.add(() -> {
                Thread.sleep(50L * n);
                return n * n;
            });
        }

        List<Future<Integer>> all = executor.invokeAll(tasks);   // waits for ALL to finish
        System.out.print("invokeAll results: ");
        for (Future<Integer> f : all) {
            try {
                System.out.print(f.get() + " ");
            } catch (ExecutionException e) {
                System.out.print("ERR ");
            }
        }
        System.out.println();

        Integer fastest = null;
        try {
            fastest = executor.invokeAny(tasks);   // returns whichever finishes first, cancels the rest
        } catch (ExecutionException e) {
            System.out.println("invokeAny failed: " + e.getCause());
        }
        System.out.println("invokeAny result (first to finish, should be 1*1=1): " + fastest);

        shutdownAndAwait(executor);
    }

    // -------------------------------------------------------------------
    // 4) ScheduledExecutorService
    // -------------------------------------------------------------------

    private static void demoScheduledExecutor() throws InterruptedException {
        printSection("4) ScheduledExecutorService -- delayed and periodic tasks");

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

        scheduler.schedule(() -> System.out.println("one-shot task ran after ~300ms delay"),
                300, TimeUnit.MILLISECONDS);

        ScheduledFuture<?> periodic = scheduler.scheduleAtFixedRate(
                () -> System.out.println("periodic tick at " + System.currentTimeMillis()),
                0, 150, TimeUnit.MILLISECONDS);

        Thread.sleep(600);           // let a few ticks fire
        periodic.cancel(false);      // stop the periodic task; don't interrupt if currently running
        System.out.println("cancelled periodic task after ~600ms window");

        scheduler.shutdown();
        scheduler.awaitTermination(2, TimeUnit.SECONDS);
    }

    // -------------------------------------------------------------------
    // 5) ThreadPoolExecutor with bounded queue + rejection policy
    // -------------------------------------------------------------------

    private static void demoThreadPoolExecutorWithRejection() throws InterruptedException {
        printSection("5) ThreadPoolExecutor -- bounded queue and rejection policy");

        // corePoolSize=1, maxPoolSize=2, tiny queue capacity=2 -- easy to overload on purpose
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 2,
                1, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(2),
                new ThreadPoolExecutor.AbortPolicy()   // default policy: throws RejectedExecutionException
        );

        int accepted = 0, rejected = 0;
        for (int i = 1; i <= 8; i++) {
            final int taskId = i;
            try {
                pool.execute(() -> {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    System.out.println("task " + taskId + " ran on " + Thread.currentThread().getName());
                });
                accepted++;
            } catch (RejectedExecutionException e) {
                rejected++;
                System.out.println("task " + taskId + " REJECTED (pool + queue both full)");
            }
        }
        System.out.println("accepted=" + accepted + " rejected=" + rejected
                + "  (core=1, max=2, queueCapacity=2 -> at most 4 tasks can be in flight/queued at once)");

        shutdownAndAwait(pool);
    }
}
