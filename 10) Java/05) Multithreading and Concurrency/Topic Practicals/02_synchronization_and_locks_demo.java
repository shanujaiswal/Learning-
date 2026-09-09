/*
 * 02_synchronization_and_locks_demo.java
 *
 * Demonstrates:
 *   1. A race condition on an unsynchronized counter (lost updates)
 *   2. Fixing it with the synchronized keyword (method-level and block-level)
 *   3. A deadlock scenario, and the consistent-lock-ordering fix
 *   4. ReentrantLock with tryLock()/timeout and explicit unlock() in finally
 *   5. ReentrantReadWriteLock allowing concurrent reads but exclusive writes
 *
 * Covers Theory chapter:
 *   05) Multithreading and Concurrency/Theory/02 Synchronization and Locks.md
 *
 * Compile: javac 02_synchronization_and_locks_demo.java
 * Run:     java SynchronizationAndLocksDemo
 */

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

class SynchronizationAndLocksDemo {

    public static void main(String[] args) throws InterruptedException {
        demoRaceCondition();
        demoSynchronizedFix();
        demoDeadlockAvoidedByOrdering();
        demoReentrantLockWithTryLock();
        demoReadWriteLock();
        System.out.println("\nAll Synchronization and Locks demos completed.");
    }

    private static void printSection(String title) {
        System.out.println("\n" + "=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }

    // -------------------------------------------------------------------
    // 1) Race condition -- unsynchronized count++ loses updates
    // -------------------------------------------------------------------

    static class UnsafeCounter {
        private int count = 0;
        public void increment() { count++; }   // NOT atomic: read, add, write
        public int get() { return count; }
    }

    private static void demoRaceCondition() throws InterruptedException {
        printSection("1) Race condition on an unsynchronized counter");

        UnsafeCounter counter = new UnsafeCounter();
        int threads = 4;
        int incrementsPerThread = 50_000;
        Thread[] workers = new Thread[threads];

        for (int i = 0; i < threads; i++) {
            workers[i] = new Thread(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    counter.increment();
                }
            });
        }
        for (Thread t : workers) t.start();
        for (Thread t : workers) t.join();

        int expected = threads * incrementsPerThread;
        System.out.println("Expected: " + expected + "   Actual: " + counter.get()
                + (counter.get() != expected ? "   <-- lost updates due to race condition!" : "   (no race hit this run)"));
    }

    // -------------------------------------------------------------------
    // 2) synchronized fixes the race
    // -------------------------------------------------------------------

    static class SafeCounter {
        private int count = 0;
        public synchronized void increment() { count++; }   // atomic w.r.t. other synchronized calls
        public synchronized int get() { return count; }
    }

    private static void demoSynchronizedFix() throws InterruptedException {
        printSection("2) synchronized keyword fixes the race condition");

        SafeCounter counter = new SafeCounter();
        int threads = 4;
        int incrementsPerThread = 50_000;
        Thread[] workers = new Thread[threads];

        for (int i = 0; i < threads; i++) {
            workers[i] = new Thread(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    counter.increment();
                }
            });
        }
        for (Thread t : workers) t.start();
        for (Thread t : workers) t.join();

        int expected = threads * incrementsPerThread;
        System.out.println("Expected: " + expected + "   Actual: " + counter.get()
                + "   (synchronized guarantees this always matches)");

        // Also demonstrate a synchronized BLOCK on a dedicated private lock object
        Object lock = new Object();
        int[] blockCounter = {0};
        Runnable task = () -> {
            for (int j = 0; j < 10_000; j++) {
                synchronized (lock) {
                    blockCounter[0]++;
                }
            }
        };
        Thread a = new Thread(task);
        Thread b = new Thread(task);
        a.start(); b.start();
        a.join(); b.join();
        System.out.println("Block-level synchronized counter (expected 20000): " + blockCounter[0]);
    }

    // -------------------------------------------------------------------
    // 3) Deadlock avoided via consistent lock ordering
    // -------------------------------------------------------------------

    private static void demoDeadlockAvoidedByOrdering() throws InterruptedException {
        printSection("3) Deadlock avoidance via consistent lock ordering");

        final Object lockA = new Object();
        final Object lockB = new Object();

        // Both threads acquire lockA THEN lockB -- same global order -- no circular wait possible.
        Runnable taskUsingAThenB = () -> {
            synchronized (lockA) {
                sleepQuietly(20);
                synchronized (lockB) {
                    System.out.println(Thread.currentThread().getName() + " acquired lockA then lockB safely");
                }
            }
        };

        Thread t1 = new Thread(taskUsingAThenB, "worker-1");
        Thread t2 = new Thread(taskUsingAThenB, "worker-2");
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        System.out.println("Both threads completed -- consistent ordering (A before B, always) prevented deadlock.");
        System.out.println("(Contrast: if worker-2 acquired B then A instead, this could deadlock.)");
    }

    private static void sleepQuietly(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    // -------------------------------------------------------------------
    // 4) ReentrantLock -- explicit lock/unlock, tryLock with timeout
    // -------------------------------------------------------------------

    private static void demoReentrantLockWithTryLock() throws InterruptedException {
        printSection("4) ReentrantLock -- explicit locking and tryLock()");

        ReentrantLock lock = new ReentrantLock();
        int[] sharedValue = {0};

        Runnable incrementer = () -> {
            for (int i = 0; i < 10_000; i++) {
                lock.lock();
                try {
                    sharedValue[0]++;
                } finally {
                    lock.unlock();       // always release in finally, even if an exception occurred
                }
            }
        };

        Thread t1 = new Thread(incrementer);
        Thread t2 = new Thread(incrementer);
        t1.start(); t2.start();
        t1.join(); t2.join();
        System.out.println("ReentrantLock-protected total (expected 20000): " + sharedValue[0]);

        // tryLock() demo: hold the lock on one thread, attempt tryLock() with a timeout on another
        ReentrantLock demoLock = new ReentrantLock();
        Thread holder = new Thread(() -> {
            demoLock.lock();
            try {
                System.out.println("holder acquired the lock, sleeping 300ms while holding it...");
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                demoLock.unlock();
                System.out.println("holder released the lock");
            }
        });
        holder.start();
        Thread.sleep(50); // ensure holder grabs the lock first

        boolean acquired = demoLock.tryLock(100, TimeUnit.MILLISECONDS);
        if (acquired) {
            try {
                System.out.println("second thread acquired the lock (unexpected in this timing)");
            } finally {
                demoLock.unlock();
            }
        } else {
            System.out.println("tryLock(100ms) timed out -- lock was busy, avoided blocking forever");
        }
        holder.join();
    }

    // -------------------------------------------------------------------
    // 5) ReentrantReadWriteLock -- concurrent reads, exclusive writes
    // -------------------------------------------------------------------

    static class SharedCache {
        private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
        private String value = "initial";

        public String read(String readerName) {
            rwLock.readLock().lock();
            try {
                System.out.println(readerName + " reading value='" + value + "'");
                sleepQuietly(30); // simulate read work; other readers can overlap here
                return value;
            } finally {
                rwLock.readLock().unlock();
            }
        }

        public void write(String newValue) {
            rwLock.writeLock().lock();
            try {
                System.out.println(Thread.currentThread().getName() + " writing value='" + newValue + "' (exclusive)");
                sleepQuietly(30);
                value = newValue;
            } finally {
                rwLock.writeLock().unlock();
            }
        }
    }

    private static void demoReadWriteLock() throws InterruptedException {
        printSection("5) ReentrantReadWriteLock -- concurrent reads vs exclusive writes");

        SharedCache cache = new SharedCache();
        long start = System.currentTimeMillis();

        Thread r1 = new Thread(() -> cache.read("reader-1"));
        Thread r2 = new Thread(() -> cache.read("reader-2"));
        Thread r3 = new Thread(() -> cache.read("reader-3"));

        r1.start(); r2.start(); r3.start();
        r1.join(); r2.join(); r3.join();
        long readersElapsed = System.currentTimeMillis() - start;
        System.out.println("3 concurrent readers finished in ~" + readersElapsed
                + "ms (much less than 3x30ms if they truly overlapped)");

        Thread w1 = new Thread(() -> cache.write("updated-by-writer"), "writer-1");
        w1.start();
        w1.join();
        System.out.println("Final cached value: " + cache.read("reader-final"));
    }
}
