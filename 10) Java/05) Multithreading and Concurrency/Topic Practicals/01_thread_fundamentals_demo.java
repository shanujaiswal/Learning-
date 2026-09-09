/*
 * 01_thread_fundamentals_demo.java
 *
 * Demonstrates:
 *   1. Creating threads by extending Thread vs implementing Runnable vs lambda
 *   2. start() vs run() -- the classic beginner trap
 *   3. Thread lifecycle states (NEW, RUNNABLE, TIMED_WAITING, TERMINATED)
 *   4. join() -- waiting for a worker thread to finish
 *   5. sleep() and cooperative interruption via interrupt()
 *   6. Daemon vs user threads
 *   7. Thread naming, identity, and priority
 *
 * Covers Theory chapter:
 *   05) Multithreading and Concurrency/Theory/01 Thread Fundamentals.md
 *
 * Compile: javac 01_thread_fundamentals_demo.java
 * Run:     java ThreadFundamentalsDemo
 */

class ThreadFundamentalsDemo {

    public static void main(String[] args) throws InterruptedException {
        demoCreatingThreads();
        demoStartVsRun();
        demoLifecycleStates();
        demoJoin();
        demoSleepAndInterrupt();
        demoDaemonThread();
        demoNamingAndPriority();
        System.out.println("\nAll Thread Fundamentals demos completed.");
    }

    private static void printSection(String title) {
        System.out.println("\n" + "=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }

    // -------------------------------------------------------------------
    // 1) Three ways to create threads
    // -------------------------------------------------------------------

    // Approach A: extending Thread directly
    static class GreeterThread extends Thread {
        @Override
        public void run() {
            System.out.println("[extends Thread] Hello from " + Thread.currentThread().getName());
        }
    }

    // Approach B: implementing Runnable (preferred -- decouples task from execution)
    static class GreeterTask implements Runnable {
        @Override
        public void run() {
            System.out.println("[implements Runnable] Hello from " + Thread.currentThread().getName());
        }
    }

    private static void demoCreatingThreads() throws InterruptedException {
        printSection("1) Creating threads -- extends Thread vs Runnable vs lambda");

        Thread t1 = new GreeterThread();
        t1.start();
        t1.join();

        Thread t2 = new Thread(new GreeterTask());
        t2.start();
        t2.join();

        // Approach C: Runnable is a functional interface -- lambda shorthand (Java 8+)
        Thread t3 = new Thread(() ->
                System.out.println("[lambda Runnable] Hello from " + Thread.currentThread().getName()));
        t3.start();
        t3.join();
    }

    // -------------------------------------------------------------------
    // 2) start() vs run() -- run() does NOT create a new thread
    // -------------------------------------------------------------------

    private static void demoStartVsRun() throws InterruptedException {
        printSection("2) start() vs run()");

        Thread t = new Thread(() ->
                System.out.println("Executing on thread: " + Thread.currentThread().getName()));

        System.out.println("Calling run() directly (WRONG for concurrency):");
        t.run();   // runs synchronously on "main" -- no new thread involved

        Thread t2 = new Thread(() ->
                System.out.println("Executing on thread: " + Thread.currentThread().getName()));
        System.out.println("Calling start() (RIGHT):");
        t2.start();  // schedules a genuinely new thread (name will be Thread-N)
        t2.join();

        // Gotcha: start() can only be called once
        try {
            t2.start();
        } catch (IllegalThreadStateException e) {
            System.out.println("Caught expected exception calling start() twice: " + e);
        }
    }

    // -------------------------------------------------------------------
    // 3) Observing lifecycle states
    // -------------------------------------------------------------------

    private static void demoLifecycleStates() throws InterruptedException {
        printSection("3) Thread lifecycle states");

        Thread t = new Thread(() -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        System.out.println("Before start(): " + t.getState());        // NEW
        t.start();
        Thread.sleep(50);                                              // give it time to reach sleep()
        System.out.println("After start(), mid-sleep: " + t.getState()); // TIMED_WAITING
        t.join();
        System.out.println("After join(): " + t.getState());          // TERMINATED
    }

    // -------------------------------------------------------------------
    // 4) join() -- waiting for a worker to finish before continuing
    // -------------------------------------------------------------------

    private static void demoJoin() throws InterruptedException {
        printSection("4) join() -- main thread waits for worker completion");

        Thread worker = new Thread(() -> {
            for (int i = 1; i <= 3; i++) {
                System.out.println("worker step " + i);
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        worker.start();
        worker.join();   // blocks main here until worker finishes all 3 steps
        System.out.println("worker finished -- safe to continue on main");
    }

    // -------------------------------------------------------------------
    // 5) sleep() and cooperative interruption
    // -------------------------------------------------------------------

    private static void demoSleepAndInterrupt() throws InterruptedException {
        printSection("5) sleep() and interrupt()");

        Thread worker = new Thread(() -> {
            int iterations = 0;
            // Cooperative cancellation: check the interrupted flag periodically.
            while (!Thread.currentThread().isInterrupted()) {
                iterations++;
                if (iterations > 1_000_000) break; // safety valve for demo purposes
            }
            System.out.println("worker noticed interruption after ~" + iterations + " spins, exiting cleanly");
        });
        worker.start();
        Thread.sleep(20);          // let it spin briefly
        worker.interrupt();        // politely request stop -- sets the interrupted flag
        worker.join();

        // Demonstrate InterruptedException path (interrupting a thread blocked in sleep())
        Thread sleeper = new Thread(() -> {
            try {
                System.out.println("sleeper going to sleep for 5s...");
                Thread.sleep(5000);
                System.out.println("sleeper woke up normally (should not happen in this demo)");
            } catch (InterruptedException e) {
                // Best practice: restore the interrupt status instead of swallowing it
                Thread.currentThread().interrupt();
                System.out.println("sleeper was interrupted early, InterruptedException caught, flag restored");
            }
        });
        sleeper.start();
        Thread.sleep(100);
        sleeper.interrupt();       // wakes sleeper up early via InterruptedException
        sleeper.join();
    }

    // -------------------------------------------------------------------
    // 6) Daemon vs user threads
    // -------------------------------------------------------------------

    private static void demoDaemonThread() throws InterruptedException {
        printSection("6) Daemon vs user threads");

        Thread daemon = new Thread(() -> {
            int tick = 0;
            while (true) {
                tick++;
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return;
                }
            }
        });
        daemon.setDaemon(true);     // MUST be set before start()
        System.out.println("Is daemon before start? " + daemon.isDaemon());
        daemon.start();
        System.out.println("Daemon thread started -- JVM will NOT wait for it to finish on its own.");
        System.out.println("(In this demo we let main() finish normally; the daemon is abandoned at JVM exit.)");

        // Gotcha demonstration: setDaemon() after start() throws
        try {
            daemon.setDaemon(false);
        } catch (IllegalThreadStateException e) {
            System.out.println("Caught expected exception setting daemon flag after start(): " + e);
        }
    }

    // -------------------------------------------------------------------
    // 7) Naming, identity, and priority
    // -------------------------------------------------------------------

    private static void demoNamingAndPriority() throws InterruptedException {
        printSection("7) Thread naming, identity, and priority");

        Thread t = new Thread(() -> {
            Thread current = Thread.currentThread();
            System.out.println("Name: " + current.getName()
                    + ", ID: " + current.getId()
                    + ", Priority: " + current.getPriority());
        }, "worker-1");

        t.setPriority(Thread.MAX_PRIORITY);  // hint only -- not a scheduling guarantee
        t.start();
        t.join();

        System.out.println("Default priority constant: " + Thread.NORM_PRIORITY);
        System.out.println("Main thread name: " + Thread.currentThread().getName());
    }
}
