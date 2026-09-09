/*
 * 05_concurrent_utilities_demo.java
 *
 * Demonstrates:
 *   1. CountDownLatch -- one-shot "start gate" + "wait for all workers to finish"
 *   2. CyclicBarrier -- multi-phase synchronization with a barrier action
 *   3. Semaphore -- bounding concurrent access to a limited resource, plus tryAcquire()
 *   4. ConcurrentHashMap -- atomic compound operations (putIfAbsent/computeIfAbsent/compute/merge)
 *   5. CopyOnWriteArrayList -- safe iteration while another thread mutates the list
 *   6. Atomic classes -- AtomicInteger, AtomicLong, AtomicReference and the lost-update problem
 *
 * Covers Theory chapter:
 *   05) Multithreading and Concurrency/Theory/05 java.util.concurrent Utilities.md
 *
 * Compile: javac 05_concurrent_utilities_demo.java
 * Run:     java ConcurrentUtilitiesDemo
 */

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

class ConcurrentUtilitiesDemo {

    public static void main(String[] args) throws Exception {
        demoCountDownLatch();
        demoCyclicBarrier();
        demoSemaphore();
        demoConcurrentHashMap();
        demoCopyOnWriteArrayList();
        demoAtomicClasses();
        System.out.println("\nAll java.util.concurrent utilities demos completed.");
    }

    private static void printSection(String title) {
        System.out.println("\n" + "=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }

    // -------------------------------------------------------------------
    // 1) CountDownLatch -- start gate + completion gate
    // -------------------------------------------------------------------

    private static void demoCountDownLatch() throws InterruptedException {
        printSection("1) CountDownLatch -- start gate and 'wait for all workers' gate");

        CountDownLatch startGate = new CountDownLatch(1);   // released once, lets all workers begin together
        CountDownLatch doneGate = new CountDownLatch(3);    // counts down once per finished worker

        for (int i = 1; i <= 3; i++) {
            final int workerId = i;
            new Thread(() -> {
                try {
                    startGate.await();                       // all 3 workers wait for the same signal
                    System.out.println("worker " + workerId + " started at " + System.currentTimeMillis());
                    Thread.sleep(50L * workerId);             // simulate varying amounts of work
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneGate.countDown();                     // always signal completion, even on failure
                }
            }, "worker-" + workerId).start();
        }

        System.out.println("main: releasing start gate for all workers at once");
        startGate.countDown();                                // release all 3 at (roughly) the same instant

        doneGate.await();                                     // block until all 3 have called countDown()
        System.out.println("main: all workers finished -- doneGate reached zero");
    }

    // -------------------------------------------------------------------
    // 2) CyclicBarrier -- reusable multi-phase synchronization
    // -------------------------------------------------------------------

    private static void demoCyclicBarrier() throws InterruptedException {
        printSection("2) CyclicBarrier -- multi-phase synchronization, reused across phases");

        final int parties = 3;
        CyclicBarrier barrier = new CyclicBarrier(parties, () ->
                System.out.println(">>> barrier action: all " + parties + " threads reached this phase <<<"));

        CountDownLatch allDone = new CountDownLatch(parties);

        for (int i = 1; i <= parties; i++) {
            final int id = i;
            new Thread(() -> {
                try {
                    for (int phase = 1; phase <= 2; phase++) {
                        Thread.sleep(30L * id);   // simulate phase work taking different amounts of time
                        System.out.println("thread " + id + " finished phase " + phase + ", waiting at barrier");
                        barrier.await();          // blocks until ALL parties reach the barrier this phase
                    }
                } catch (InterruptedException | BrokenBarrierException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    allDone.countDown();
                }
            }, "phase-worker-" + id).start();
        }

        allDone.await();
        System.out.println("main: all phase-workers completed both phases");
    }

    // -------------------------------------------------------------------
    // 3) Semaphore -- limiting concurrent access
    // -------------------------------------------------------------------

    private static void demoSemaphore() throws InterruptedException {
        printSection("3) Semaphore -- at most 2 threads may use the resource concurrently");

        Semaphore semaphore = new Semaphore(2);           // only 2 permits -- 2 concurrent users max
        AtomicInteger concurrentUsers = new AtomicInteger(0);
        AtomicInteger maxObservedConcurrency = new AtomicInteger(0);
        CountDownLatch done = new CountDownLatch(5);

        for (int i = 1; i <= 5; i++) {
            final int taskId = i;
            new Thread(() -> {
                try {
                    semaphore.acquire();                    // blocks if 2 permits are already taken
                    int current = concurrentUsers.incrementAndGet();
                    maxObservedConcurrency.updateAndGet(prev -> Math.max(prev, current));
                    System.out.println("task " + taskId + " acquired permit (concurrent users=" + current + ")");
                    Thread.sleep(100);                      // simulate using the limited resource
                    concurrentUsers.decrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    semaphore.release();                    // always release, even on failure
                    done.countDown();
                }
            }, "sem-task-" + taskId).start();
        }

        done.await();
        System.out.println("max concurrent permit holders observed: " + maxObservedConcurrency.get()
                + " (should never exceed 2)");

        // tryAcquire -- non-blocking attempt
        Semaphore single = new Semaphore(1);
        single.acquire();
        boolean gotIt = single.tryAcquire();
        System.out.println("tryAcquire() on an already-fully-held semaphore returned: " + gotIt + " (expected false)");
        single.release();
    }

    // -------------------------------------------------------------------
    // 4) ConcurrentHashMap -- atomic compound operations
    // -------------------------------------------------------------------

    private static void demoConcurrentHashMap() throws InterruptedException {
        printSection("4) ConcurrentHashMap -- putIfAbsent / computeIfAbsent / compute / merge");

        ConcurrentHashMap<String, Integer> wordCounts = new ConcurrentHashMap<>();
        String[] words = {"a", "b", "a", "c", "b", "a", "d", "c", "a"};

        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch latch = new CountDownLatch(words.length);

        for (String word : words) {
            executor.execute(() -> {
                // merge: atomic "add or initialize" -- no external synchronization needed
                wordCounts.merge(word, 1, Integer::sum);
                latch.countDown();
            });
        }
        latch.await();
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        System.out.println("word counts (concurrent merge): " + wordCounts);
        // expected: a=4, b=2, c=2, d=1

        Map<String, Integer> cache = new ConcurrentHashMap<>();
        cache.putIfAbsent("x", 10);
        cache.putIfAbsent("x", 999);                     // no-op -- "x" already present
        System.out.println("putIfAbsent kept original value for x: " + cache.get("x"));

        cache.computeIfAbsent("y", k -> {
            System.out.println("computing value for absent key: " + k);
            return 20;
        });
        cache.computeIfAbsent("y", k -> 999);             // no-op -- "y" already present, lambda not invoked
        System.out.println("computeIfAbsent value for y: " + cache.get("y"));

        cache.compute("x", (k, v) -> v == null ? 1 : v + 1);
        System.out.println("compute incremented x to: " + cache.get("x"));
    }

    // -------------------------------------------------------------------
    // 5) CopyOnWriteArrayList -- safe iteration under concurrent mutation
    // -------------------------------------------------------------------

    private static void demoCopyOnWriteArrayList() throws InterruptedException {
        printSection("5) CopyOnWriteArrayList -- iterator sees a fixed snapshot, never CME");

        CopyOnWriteArrayList<String> listeners = new CopyOnWriteArrayList<>();
        listeners.add("listenerA");
        listeners.add("listenerB");
        listeners.add("listenerC");

        Thread mutator = new Thread(() -> {
            try {
                Thread.sleep(10);                 // let iteration start first
                listeners.add("listenerD");        // mutating while another thread iterates -- safe here
                listeners.remove("listenerA");
                System.out.println("mutator: added listenerD, removed listenerA");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "mutator");

        mutator.start();

        System.out.println("iterating over snapshot taken at iterator-creation time:");
        int seen = 0;
        for (String listener : listeners) {       // iterator is a fixed snapshot -- no ConcurrentModificationException
            System.out.println("  saw: " + listener);
            Thread.sleep(20);                     // slow the loop so the mutator definitely runs concurrently
            seen++;
        }
        System.out.println("iteration completed safely, saw " + seen + " elements from the snapshot");

        mutator.join();
        System.out.println("list after mutation settles: " + listeners);
    }

    // -------------------------------------------------------------------
    // 6) Atomic classes -- AtomicInteger, AtomicLong, AtomicReference
    // -------------------------------------------------------------------

    private static void demoAtomicClasses() throws InterruptedException {
        printSection("6) Atomic classes -- AtomicInteger/AtomicLong/AtomicReference, CAS in action");

        // --- Plain int under contention: demonstrates the LOST UPDATE problem ---
        int[] plainCounter = {0};
        Runnable unsafeIncrement = () -> {
            for (int i = 0; i < 10_000; i++) {
                plainCounter[0]++;               // NOT atomic: read-modify-write, races across threads
            }
        };

        Thread t1 = new Thread(unsafeIncrement);
        Thread t2 = new Thread(unsafeIncrement);
        t1.start(); t2.start();
        t1.join(); t2.join();
        System.out.println("plain int after 2 threads x 10,000 increments each (expected 20000): "
                + plainCounter[0] + "  <- likely LESS than 20000 due to lost updates");

        // --- AtomicInteger: same workload, no lost updates ---
        AtomicInteger atomicCounter = new AtomicInteger(0);
        Runnable safeIncrement = () -> {
            for (int i = 0; i < 10_000; i++) {
                atomicCounter.incrementAndGet();  // atomic, CAS-based, no lock needed
            }
        };

        Thread t3 = new Thread(safeIncrement);
        Thread t4 = new Thread(safeIncrement);
        t3.start(); t4.start();
        t3.join(); t4.join();
        System.out.println("AtomicInteger after 2 threads x 10,000 increments each (expected 20000): "
                + atomicCounter.get() + "  <- always exactly 20000");

        // --- compareAndSet ---
        AtomicInteger cas = new AtomicInteger(5);
        boolean success1 = cas.compareAndSet(5, 100);   // succeeds: current value IS 5
        boolean success2 = cas.compareAndSet(5, 200);   // fails: current value is now 100, not 5
        System.out.println("compareAndSet(5->100): " + success1 + ", compareAndSet(5->200) after that: "
                + success2 + ", final value: " + cas.get());

        // --- AtomicLong ---
        AtomicLong totalBytes = new AtomicLong(0);
        totalBytes.addAndGet(1024);
        totalBytes.addAndGet(2048);
        System.out.println("AtomicLong running total: " + totalBytes.get());

        // --- AtomicReference ---
        AtomicReference<String> configRef = new AtomicReference<>("v1-config");
        boolean swapped = configRef.compareAndSet("v1-config", "v2-config");
        System.out.println("AtomicReference swapped v1->v2: " + swapped + ", current: " + configRef.get());

        configRef.updateAndGet(current -> current + "-patched");
        System.out.println("AtomicReference after updateAndGet: " + configRef.get());
    }
}
