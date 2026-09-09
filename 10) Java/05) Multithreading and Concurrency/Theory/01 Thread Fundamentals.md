# Why Multithreading Matters

--> Modern CPUs have multiple cores, but a single-threaded program only ever uses one of them at a time -- multithreading is how a Java program does more than one thing "at once," either by genuinely running on separate cores (parallelism) or by rapidly switching between tasks on one core (concurrency) so that a slow I/O-bound task doesn't block everything else.
--> A **process** is an independent running program with its own memory space (e.g. the JVM itself). A **thread** is a lightweight unit of execution WITHIN a process -- all threads in a Java program share the same heap memory, the same static variables, and the same open files, but each thread has its OWN call stack, program counter, and local variables. This shared-memory model is what makes multithreading powerful (fast communication between threads) and dangerous (race conditions if that shared memory isn't managed carefully).

```text
Process (JVM)
 ├── Heap (shared by all threads: objects, static fields)
 ├── Thread 1 -- own stack, own local variables, own PC
 ├── Thread 2 -- own stack, own local variables, own PC
 └── Thread 3 -- own stack, own local variables, own PC
```

--> Every Java program already has at least one thread: the **main thread**, which runs your `main()` method. Everything discussed in this file is about creating and managing ADDITIONAL threads alongside it.

# Creating Threads -- Two Classic Approaches

--> Java gives you two traditional ways to define the code a thread should run: extending `Thread` directly, or implementing the `Runnable` interface and handing it to a `Thread`. Both end with a call to `.start()`.

## Approach 1 -- Extending `Thread`

```java
class GreeterThread extends Thread {
    @Override
    public void run() {                      // the code this thread will execute
        System.out.println("Hello from " + Thread.currentThread().getName());
    }
}

// usage
GreeterThread t = new GreeterThread();
t.start();          // starts a NEW thread, which then calls run()
```

--> Extending `Thread` is straightforward but **burns your one shot at single inheritance** in Java -- a class extending `Thread` can't extend anything else, which is a real limitation in a language without multiple inheritance of classes.

## Approach 2 -- Implementing `Runnable` (Preferred)

```java
class GreeterTask implements Runnable {
    @Override
    public void run() {
        System.out.println("Hello from " + Thread.currentThread().getName());
    }
}

// usage
Runnable task = new GreeterTask();
Thread t = new Thread(task);
t.start();
```

--> **Why `Runnable` is generally preferred:**
  1. It separates "what work should be done" (`Runnable`) from "how it gets executed" (`Thread`), following the principle of favoring composition over inheritance.
  2. Your task class remains free to extend some other class if needed.
  3. A `Runnable` can be reused and handed to thread pools (the `Executor` framework, covered in file 04) instead of always spawning a brand-new `Thread`.
--> Since `Runnable` is a **functional interface** (single abstract method `run()`), Java 8+ lets you skip the class entirely and use a lambda:

```java
Thread t = new Thread(() -> System.out.println("Hello from " + Thread.currentThread().getName()));
t.start();
```

## Approach 3 -- `Callable` (returns a value, throws checked exceptions)

--> `Runnable.run()` returns nothing and cannot throw checked exceptions. `Callable<V>` is the modern alternative used with the Executor framework (covered fully in file 04) whose `call()` method returns a value and CAN throw checked exceptions:

```java
import java.util.concurrent.Callable;

Callable<Integer> task = () -> {
    return 42;                 // can also throw checked exceptions
};
```

--> `Callable` cannot be passed to a raw `Thread` constructor -- it's designed to be submitted to an `ExecutorService`, which wraps its result in a `Future`. This is why `Callable` is introduced here conceptually but used properly in file 04.

# `start()` vs `run()` -- The Single Most Common Beginner Mistake

--> Calling `run()` directly does NOT create a new thread -- it just calls the method like any ordinary method, executing on the CURRENT thread (usually `main`). Calling `start()` is what actually asks the JVM to allocate a new call stack and schedule a new thread of execution, which then internally invokes your `run()` method on that new thread.

```java
Thread t = new Thread(() -> System.out.println(Thread.currentThread().getName()));

t.run();     // WRONG if you wanted concurrency -- prints "main", runs synchronously, no new thread
t.start();   // RIGHT -- prints something like "Thread-0", runs on a genuinely new thread
```

--> **Gotcha:** `start()` can only be called ONCE per `Thread` object. Calling it a second time throws `IllegalThreadStateException` -- a `Thread` object is a single-use ticket to run its `run()` method once on a new thread, not a reusable handle.

# The Thread Lifecycle

--> A thread moves through a well-defined set of states, exposed via `Thread.getState()` (returns a `Thread.State` enum).

```text
        new Thread(...)
              |
              v
           NEW  --------------------- (never started, or already terminated)
              |
           start()
              v
         RUNNABLE  <-------------------------+
        /    |    \                          |
       /     |     \                         |
  (waiting  (blocked   (scheduled by OS,     |
   for lock) on I/O)    actually running)    |
      |          \                           |
      v           \                          |
   BLOCKED         \                         |
      |             \                        |
  (lock acquired)     \                      |
      +----------------+---------------------+
              |
   wait() / join() / sleep() called
              v
     WAITING / TIMED_WAITING
              |
   notified / timeout / interrupted
              v
         RUNNABLE (loop continues above)
              |
       run() method returns
              v
         TERMINATED
```

--> **`NEW`** -- the `Thread` object has been constructed but `start()` has not been called yet.
--> **`RUNNABLE`** -- the thread is eligible to run; it might actually be executing on a CPU core right now, or it might just be waiting for the OS scheduler to give it a turn. Java doesn't distinguish "running" from "ready to run" as separate states -- both are `RUNNABLE`.
--> **`BLOCKED`** -- the thread is waiting to acquire a `synchronized` lock that another thread currently holds (see file 02).
--> **`WAITING`** -- the thread is waiting indefinitely for another thread to do something, via `Object.wait()` (no timeout), `Thread.join()` (no timeout), or `LockSupport.park()`.
--> **`TIMED_WAITING`** -- like `WAITING`, but with a timeout: `Thread.sleep(ms)`, `Object.wait(ms)`, `Thread.join(ms)`.
--> **`TERMINATED`** -- the thread has finished executing `run()` (either normally or via an uncaught exception) and cannot be restarted.

```java
Thread t = new Thread(() -> {
    try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
});
System.out.println(t.getState());   // NEW
t.start();
System.out.println(t.getState());   // RUNNABLE (or TIMED_WAITING if it already hit sleep)
t.join();
System.out.println(t.getState());   // TERMINATED
```

# `join()` -- Waiting for a Thread to Finish

--> `join()` blocks the CALLING thread until the target thread terminates -- it is the standard way to make one thread wait for another to complete before proceeding, and is the closest analogue to synchronous "wait for this task to finish."

```java
Thread worker = new Thread(() -> {
    for (int i = 0; i < 3; i++) System.out.println("working " + i);
});
worker.start();
worker.join();                 // main thread blocks here until worker finishes
System.out.println("worker is done, safe to continue");
```

--> `join(long millis)` waits at most that long, then returns anyway (the thread may still be alive afterward -- always safe to check with `isAlive()` if it matters). `join()` throws the checked `InterruptedException`, so it must be wrapped in try/catch or declared thrown.

# `sleep()` -- Pausing the Current Thread

--> `Thread.sleep(millis)` is a STATIC method that pauses the CURRENTLY EXECUTING thread for at least the given duration -- it does NOT release any locks the thread holds (a critical difference from `wait()`, covered in file 03), and it does not guarantee the exact wake-up time, only a lower bound (the OS scheduler decides exactly when to resume it).

```java
System.out.println("before sleep");
Thread.sleep(1000);           // pauses THIS thread, whichever one is executing this line
System.out.println("after sleep");
```

--> **Gotcha:** because `sleep()` is static, `someOtherThread.sleep(1000)` is misleading syntax -- it still pauses the CURRENT thread, not `someOtherThread`. Most linters/IDEs warn about calling static methods via an instance reference for exactly this reason.

# Interrupting Threads

--> Java has no safe way to forcibly kill a thread (the old `Thread.stop()` is deprecated and dangerous, because it can leave shared objects in a half-updated, inconsistent state). Instead, Java uses a **cooperative interruption** model: calling `thread.interrupt()` merely sets an internal "interrupted" flag and, if the thread is currently blocked in `sleep()`, `wait()`, or `join()`, wakes it up early by throwing `InterruptedException` there.

```java
Thread worker = new Thread(() -> {
    while (!Thread.currentThread().isInterrupted()) {
        // do work in small chunks, checking the flag periodically
    }
    System.out.println("worker noticed the interrupt and is exiting cleanly");
});
worker.start();
Thread.sleep(100);
worker.interrupt();          // politely asks the worker to stop; worker must cooperate
```

--> **Gotcha -- swallowing `InterruptedException`:** catching `InterruptedException` and doing nothing (or just logging it) silently discards the interruption signal, which can make a program impossible to shut down cleanly. The standard fix, if you can't propagate the checked exception, is to restore the flag:

```java
try {
    Thread.sleep(1000);
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();   // restore the interrupt status for outer code to see
}
```

# Daemon Threads vs User Threads

--> By default, threads are **user threads** -- the JVM will not exit until every user thread has finished. **Daemon threads** are background "service" threads that do NOT keep the JVM alive -- once all user threads finish, the JVM exits immediately, abandoning any still-running daemon threads mid-execution (no cleanup, no finally blocks guaranteed to run).

```java
Thread background = new Thread(() -> {
    while (true) { /* some background housekeeping, e.g. cache eviction */ }
});
background.setDaemon(true);      // MUST be called BEFORE start()
background.start();
// JVM can exit even though this thread never terminates on its own
```

--> **Common real use:** the JVM's own garbage collector thread is a daemon thread. Application code often uses daemon threads for things like periodic cache cleanup or metrics reporting -- work that's fine to simply abandon on shutdown.
--> **Gotcha:** `setDaemon(true)` throws `IllegalThreadStateException` if called AFTER the thread has already started.

# Thread Naming and Identity

--> Every thread has a name (auto-generated like `Thread-0`, `Thread-1`, ... if not set explicitly) and a unique ID, both useful for debugging and logging in concurrent code where interleaved output is otherwise hard to trace back to a specific thread.

```java
Thread t = new Thread(() -> System.out.println("hi"), "worker-1");   // name set in constructor
t.setName("renamed-worker");                                          // or set/changed later
System.out.println(Thread.currentThread().getName());                // read the current thread's name
System.out.println(t.getId());                                       // unique numeric ID (deprecated in favor of threadId() in Java 19+)
```

# Thread Priority

--> Every thread has a priority between `Thread.MIN_PRIORITY` (1) and `Thread.MAX_PRIORITY` (10), with `Thread.NORM_PRIORITY` (5) as the default. Priority is only a HINT to the OS scheduler about relative importance -- it does NOT guarantee execution order, and its actual effect is highly platform-dependent (some OSes largely ignore it).

```java
Thread t = new Thread(() -> System.out.println("high priority task"));
t.setPriority(Thread.MAX_PRIORITY);     // hint only -- not a guarantee
t.start();
```

--> **Best practice:** do not rely on thread priority for correctness or for enforcing execution ORDER -- use proper synchronization tools (locks, latches, executors) instead. Priority is, at best, a performance tuning hint for CPU-bound background vs foreground work, never a substitute for real coordination.

# Common Gotchas and Best Practices Recap

--> **Prefer `Runnable`/`Callable` + `Thread` (or better, an `Executor`, file 04) over subclassing `Thread`** -- keeps task logic decoupled from execution mechanics and preserves inheritance flexibility.
--> **Never call `run()` expecting concurrency** -- always call `start()`.
--> **A `Thread` object is single-use** -- once `start()`ed and finished, it cannot be restarted; construct a new `Thread` instead.
--> **Uncaught exceptions in `run()` don't crash the whole JVM** -- they terminate just that thread and print a stack trace via the thread's `UncaughtExceptionHandler` (customizable via `setUncaughtExceptionHandler`), while every other thread keeps running.
--> **Creating raw threads for short-lived tasks is expensive and doesn't scale** -- OS thread creation has real overhead (memory for the stack, kernel bookkeeping); this is exactly the problem the `ExecutorService` thread pool abstraction (file 04) solves by reusing a fixed pool of threads across many tasks.
--> **Prefer `Thread.sleep` in small increments with an interruption check inside loops** for long-running background work, so the thread can be told to stop promptly rather than only checking once every few seconds.
