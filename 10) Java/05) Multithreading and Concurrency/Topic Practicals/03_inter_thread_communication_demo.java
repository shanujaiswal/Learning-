/*
 * 03_inter_thread_communication_demo.java
 *
 * Demonstrates:
 *   1. wait()/notifyAll() basics with a simple ready-flag signal
 *   2. A hand-rolled bounded-buffer producer-consumer using wait()/notifyAll()
 *   3. volatile fixing a visibility bug (flag not seen by another thread)
 *   4. volatile NOT fixing an atomicity bug (count++ still races)
 *
 * Covers Theory chapter:
 *   05) Multithreading and Concurrency/Theory/03 Inter-thread Communication.md
 *
 * Compile: javac 03_inter_thread_communication_demo.java
 * Run:     java InterThreadCommunicationDemo
 */

import java.util.LinkedList;
import java.util.Queue;

class InterThreadCommunicationDemo {

    public static void main(String[] args) throws InterruptedException {
        demoWaitNotifyBasics();
        demoProducerConsumer();
        demoVolatileVisibility();
        demoVolatileDoesNotGiveAtomicity();
        System.out.println("\nAll Inter-thread Communication demos completed.");
    }

    private static void printSection(String title) {
        System.out.println("\n" + "=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }

    // -------------------------------------------------------------------
    // 1) wait()/notifyAll() basics
    // -------------------------------------------------------------------

    static class SimpleSignal {
        private boolean ready = false;
        private final Object lock = new Object();

        public void awaitReady() throws InterruptedException {
            synchronized (lock) {
                while (!ready) {                 // ALWAYS re-check in a loop (spurious wakeup protection)
                    System.out.println(Thread.currentThread().getName() + " waiting for ready...");
                    lock.wait();
                }
                System.out.println(Thread.currentThread().getName() + " proceeding, ready=true");
            }
        }

        public void signalReady() {
            synchronized (lock) {
                ready = true;
                lock.notifyAll();                 // wake all waiters to re-check the condition
            }
        }
    }

    private static void demoWaitNotifyBasics() throws InterruptedException {
        printSection("1) wait()/notifyAll() basics");

        SimpleSignal signal = new SimpleSignal();
        Thread waiter = new Thread(() -> {
            try {
                signal.awaitReady();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "waiter");

        waiter.start();
        Thread.sleep(100);              // let the waiter reach wait() first
        System.out.println("main is about to signal ready");
        signal.signalReady();
        waiter.join();
    }

    // -------------------------------------------------------------------
    // 2) Producer-Consumer with a hand-rolled bounded buffer
    // -------------------------------------------------------------------

    static class BoundedBuffer<T> {
        private final Queue<T> queue = new LinkedList<>();
        private final int capacity;
        private final Object lock = new Object();

        BoundedBuffer(int capacity) { this.capacity = capacity; }

        void put(T item) throws InterruptedException {
            synchronized (lock) {
                while (queue.size() == capacity) {     // full -- wait for consumer to make room
                    lock.wait();
                }
                queue.add(item);
                System.out.println("produced: " + item + "  (buffer size=" + queue.size() + ")");
                lock.notifyAll();                        // wake any consumer waiting on "empty"
            }
        }

        T take() throws InterruptedException {
            synchronized (lock) {
                while (queue.isEmpty()) {                 // empty -- wait for producer to add something
                    lock.wait();
                }
                T item = queue.poll();
                System.out.println("consumed: " + item + "  (buffer size=" + queue.size() + ")");
                lock.notifyAll();                          // wake any producer waiting on "full"
                return item;
            }
        }
    }

    private static void demoProducerConsumer() throws InterruptedException {
        printSection("2) Producer-Consumer pattern with wait()/notifyAll()");

        BoundedBuffer<Integer> buffer = new BoundedBuffer<>(3);   // small capacity to force blocking

        Thread producer = new Thread(() -> {
            try {
                for (int i = 1; i <= 6; i++) {
                    buffer.put(i);
                    Thread.sleep(20);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "producer");

        Thread consumer = new Thread(() -> {
            try {
                for (int i = 1; i <= 6; i++) {
                    buffer.take();
                    Thread.sleep(50);    // consumer slower than producer -- buffer will fill up
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "consumer");

        producer.start();
        consumer.start();
        producer.join();
        consumer.join();
        System.out.println("Producer-consumer demo finished -- all 6 items produced and consumed.");
    }

    // -------------------------------------------------------------------
    // 3) volatile fixes a visibility bug
    // -------------------------------------------------------------------

    static class VisibilityFlag {
        private volatile boolean running = true;   // try removing volatile: the loop below may never stop

        void stop() { running = false; }

        void runUntilStopped() {
            long spins = 0;
            while (running) {
                spins++;
                if (spins > 2_000_000_000L) break;  // safety valve in case volatile is removed in an experiment
            }
            System.out.println("worker observed running=false and stopped after " + spins + " spins");
        }
    }

    private static void demoVolatileVisibility() throws InterruptedException {
        printSection("3) volatile fixes cross-thread visibility of a flag");

        VisibilityFlag flag = new VisibilityFlag();
        Thread worker = new Thread(flag::runUntilStopped, "flag-worker");
        worker.start();
        Thread.sleep(50);
        System.out.println("main calling stop()");
        flag.stop();
        worker.join(2000);
        System.out.println("worker terminated cleanly: " + !worker.isAlive());
    }

    // -------------------------------------------------------------------
    // 4) volatile does NOT provide atomicity for compound operations
    // -------------------------------------------------------------------

    static class BrokenVolatileCounter {
        private volatile int count = 0;
        void increment() { count++; }   // still read-modify-write -- volatile does not help here
        int get() { return count; }
    }

    private static void demoVolatileDoesNotGiveAtomicity() throws InterruptedException {
        printSection("4) volatile alone does NOT make count++ atomic");

        BrokenVolatileCounter counter = new BrokenVolatileCounter();
        int threads = 4;
        int incrementsPerThread = 50_000;
        Thread[] workers = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            workers[i] = new Thread(() -> {
                for (int j = 0; j < incrementsPerThread; j++) counter.increment();
            });
        }
        for (Thread t : workers) t.start();
        for (Thread t : workers) t.join();

        int expected = threads * incrementsPerThread;
        System.out.println("Expected: " + expected + "   Actual: " + counter.get()
                + (counter.get() != expected
                    ? "   <-- volatile did NOT prevent lost updates (needs synchronized/atomic instead)"
                    : "   (no race hit this particular run, but the bug is still there)"));
    }
}
