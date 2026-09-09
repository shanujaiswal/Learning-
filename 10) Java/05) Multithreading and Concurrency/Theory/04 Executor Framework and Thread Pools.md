# Why Not Just Create a `Thread` Per Task?

--> Creating a raw OS thread has real, measurable overhead -- allocating its stack (typically 512KB-1MB by default), registering it with the OS scheduler, and eventually tearing it down. Spawning a brand-new `Thread` for every incoming task (e.g. one per web request) works fine at small scale but breaks down badly under load: thousands of concurrent threads exhaust memory, and the OS scheduler spends more time context-switching between them than doing real work.
--> The **Executor framework** (`java.util.concurrent`) solves this by decoupling "submitting a task" from "how and when it actually runs." A pool of reusable worker threads picks up tasks from a queue as they arrive, so the cost of thread creation is paid once (at pool startup) rather than per task.

```text
Without a pool:                       With a pool:
Task1 -> new Thread -> run -> die     Task1 --\
Task2 -> new Thread -> run -> die     Task2 ---+--> [ task queue ] --> [ fixed pool of N worker threads ]
Task3 -> new Thread -> run -> die     Task3 --/                          (reused across many tasks)
   (expensive, unbounded growth)          (bounded, threads reused)
```

# `ExecutorService` -- The Core Abstraction

--> `ExecutorService` is an interface representing "something you can submit tasks to." It extends the simpler `Executor` interface (which just has `execute(Runnable)`) with lifecycle management and the ability to submit tasks that RETURN a result.

```java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

ExecutorService executor = Executors.newFixedThreadPool(4);

executor.execute(() -> System.out.println("fire-and-forget task"));   // no result, from Executor

executor.submit(() -> System.out.println("submitted task"));           // returns a Future<?>

executor.shutdown();     // stop accepting new tasks; let submitted ones finish
```

## Shutting Down an Executor -- Non-Optional

--> **An `ExecutorService`'s threads do NOT stop on their own** -- forgetting to shut one down is a classic resource leak that keeps the JVM alive indefinitely (its threads are non-daemon by default), since it holds live non-daemon threads.
--> **`shutdown()`** -- stops accepting new tasks, but lets already-submitted tasks (including ones still queued) finish normally.
--> **`shutdownNow()`** -- attempts to stop ALL actively executing tasks immediately (by interrupting them) and returns the list of tasks that were queued but never started; does not guarantee already-running tasks actually stop, since that still depends on them cooperating with interruption (file 01).
--> **`awaitTermination(timeout, unit)`** -- blocks until either all tasks finish after a shutdown request, or the timeout elapses; returns a boolean indicating which happened.

```java
executor.shutdown();
try {
    if (!executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
        executor.shutdownNow();     // force-cancel anything still running after the grace period
    }
} catch (InterruptedException e) {
    executor.shutdownNow();
    Thread.currentThread().interrupt();
}
```

--> **Best practice -- the standard shutdown idiom above** is worth memorizing: try a graceful `shutdown()` + `awaitTermination()` first, and fall back to `shutdownNow()` if tasks don't finish in a reasonable window.

# `Executors` Factory Methods

--> The `Executors` utility class provides convenient factory methods for common pool configurations, each backed by `ThreadPoolExecutor` (the actual configurable implementation) with different preset parameters.

```java
Executors.newFixedThreadPool(int n)      // exactly n threads, unbounded queue for excess tasks
Executors.newCachedThreadPool()          // grows as needed, reuses idle threads, shrinks after 60s idle
Executors.newSingleThreadExecutor()      // exactly 1 thread -- tasks run sequentially, in submission order
Executors.newScheduledThreadPool(int n)  // n threads, supports delayed/periodic task scheduling
Executors.newWorkStealingPool()          // ForkJoinPool-backed, parallelism = available processors by default
```

--> **`newFixedThreadPool(n)`** -- good default for CPU-bound or steady, predictable workloads. Its queue is UNBOUNDED, which means if tasks arrive faster than they can be processed, the queue grows without limit -- a real risk of `OutOfMemoryError` under sustained overload, since nothing pushes back on the caller.
--> **`newCachedThreadPool()`** -- great for many short-lived, bursty tasks (creates new threads on demand, reuses idle ones, kills threads idle for 60 seconds). Danger: with NO upper bound on thread count, a burst of enough tasks can create an unbounded number of threads and exhaust system resources.
--> **`newSingleThreadExecutor()`** -- guarantees tasks run one at a time, in the order submitted, useful when you need serialized execution without manual locking (e.g. a single writer thread for a log file).
--> **`newScheduledThreadPool(n)`** -- supports `schedule()` (run once after a delay), `scheduleAtFixedRate()`, and `scheduleWithFixedDelay()` for recurring tasks.

```java
import java.util.concurrent.*;

ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
scheduler.schedule(() -> System.out.println("runs once after 2s"), 2, TimeUnit.SECONDS);
scheduler.scheduleAtFixedRate(() -> System.out.println("tick"), 0, 1, TimeUnit.SECONDS);  // every 1s
scheduler.scheduleWithFixedDelay(() -> System.out.println("tock"), 0, 1, TimeUnit.SECONDS); // 1s AFTER each run finishes
```

--> **`scheduleAtFixedRate` vs `scheduleWithFixedDelay`:** fixed-rate tries to keep runs starting at a constant PERIOD regardless of how long each run takes (and can "catch up" by running back-to-back if a run overruns); fixed-delay always waits the given delay AFTER each run finishes before starting the next -- fixed-delay is usually safer for tasks whose duration can vary, since it can't pile up overlapping runs.

--> **Production caution:** the `Executors` convenience factories are handy for quick scripts and demos, but the unbounded queues/threads they use are a common source of production incidents. **Best practice for production code** is often to construct a `ThreadPoolExecutor` directly with an explicit BOUNDED queue and a defined rejection policy, so overload fails predictably instead of silently exhausting memory.

# `ThreadPoolExecutor` -- What's Actually Under the Hood

```java
import java.util.concurrent.*;

ThreadPoolExecutor pool = new ThreadPoolExecutor(
    2,                                  // corePoolSize -- threads kept alive even when idle
    4,                                  // maximumPoolSize -- max threads under load
    60L, TimeUnit.SECONDS,              // keepAliveTime -- how long extra threads stay idle before dying
    new ArrayBlockingQueue<>(100),      // work queue -- BOUNDED here, unlike the Executors defaults
    new ThreadPoolExecutor.CallerRunsPolicy()  // rejection policy when the queue is full and at max threads
);
```

--> **How a `ThreadPoolExecutor` decides what to do with a new task** (in this exact order):
  1. If fewer than `corePoolSize` threads exist, start a new thread for the task (even if other threads are idle).
  2. Otherwise, if the queue has room, enqueue the task.
  3. Otherwise, if fewer than `maximumPoolSize` threads exist, start a new (temporary) thread.
  4. Otherwise, the task is REJECTED, handled by the configured `RejectedExecutionHandler`.

```text
newFixedThreadPool(4) is actually:
    new ThreadPoolExecutor(4, 4, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>())
                                                              ^^^^^^^^^^^^^^^^^^^^^^^^^^^
                                                              UNBOUNDED -- rejection never triggers,
                                                              queue just grows indefinitely instead
```

--> **Rejection policies:** `AbortPolicy` (default -- throws `RejectedExecutionException`), `CallerRunsPolicy` (the calling thread itself runs the task, naturally throttling the caller), `DiscardPolicy` (silently drops the task), `DiscardOldestPolicy` (drops the oldest queued task to make room for the new one).

# `Future` and `Callable` -- Getting Results Back From Tasks

--> `Runnable.run()` returns nothing. `Callable<V>.call()` returns a value of type `V` and can throw checked exceptions -- submitting a `Callable` to an `ExecutorService` returns a `Future<V>`, a handle representing a result that may not exist YET.

```java
import java.util.concurrent.*;

ExecutorService executor = Executors.newFixedThreadPool(2);

Callable<Integer> task = () -> {
    Thread.sleep(500);
    return 21 * 2;
};

Future<Integer> future = executor.submit(task);

System.out.println("doing other work while the task runs in the background...");

Integer result = future.get();          // BLOCKS until the result is ready (or throws)
System.out.println("result: " + result);

executor.shutdown();
```

--> **`future.get()`** -- blocks indefinitely until the task completes, then returns its result, or re-throws the task's exception WRAPPED in `ExecutionException`. An overload `get(timeout, unit)` throws `TimeoutException` instead of blocking forever.
--> **`future.cancel(boolean mayInterruptIfRunning)`** -- attempts to cancel the task. If it hasn't started yet, it simply won't run. If it's already running, `mayInterruptIfRunning=true` calls `interrupt()` on the task's thread (which the task must cooperate with, per file 01) -- `false` lets it run to completion regardless.
--> **`future.isDone()` / `future.isCancelled()`** -- non-blocking status checks.

```java
Future<Integer> f = executor.submit(() -> { Thread.sleep(5000); return 1; });
boolean cancelled = f.cancel(true);         // interrupts the task if already running
System.out.println("cancel requested: " + cancelled + ", isCancelled: " + f.isCancelled());
```

## Submitting Many Tasks at Once -- `invokeAll` / `invokeAny`

```java
List<Callable<Integer>> tasks = List.of(() -> 1, () -> 2, () -> 3);

List<Future<Integer>> results = executor.invokeAll(tasks);   // waits for ALL to finish, in order

Integer firstDone = executor.invokeAny(tasks);                // returns the result of whichever finishes FIRST,
                                                                // cancels the rest
```

# Thread Pool Sizing -- How Many Threads Is "Right"?

--> There's no single correct pool size -- it depends on whether the work is CPU-bound or I/O-bound.

```text
CPU-bound tasks (heavy computation, little/no blocking):
    optimal threads ~= number of available CPU cores
    (more threads than cores just adds context-switching overhead with no throughput gain)

I/O-bound tasks (network calls, disk I/O, waiting on external systems):
    optimal threads can be MUCH higher than core count,
    because threads spend most of their time BLOCKED, not using the CPU

A commonly cited starting formula (from "Java Concurrency in Practice"):
    threads = number_of_cores * target_CPU_utilization * (1 + wait_time / compute_time)
```

```java
int cores = Runtime.getRuntime().availableProcessors();     // a practical starting point for CPU-bound sizing
ExecutorService cpuBoundPool = Executors.newFixedThreadPool(cores);
```

--> **Best practice:** treat any specific formula as a STARTING point, not gospel -- real sizing should be validated with load testing and monitoring (queue depth, task latency, thread utilization) against realistic workloads, since actual wait/compute ratios are rarely known precisely in advance.
--> **Common mistake:** using a single shared pool for both fast CPU-bound work and slow, blocking I/O-bound work -- a handful of slow I/O tasks can starve fast CPU tasks of worker threads. **Best practice:** use SEPARATE, appropriately-sized pools for meaningfully different workload types.

# Common Gotchas and Best Practices Recap

--> **Always shut down an `ExecutorService`** you created -- otherwise its threads keep the JVM alive forever. Use try-with-resources on `AutoCloseable` executors (Java 19+'s `ExecutorService` extends `AutoCloseable`) or the manual `shutdown()`/`awaitTermination()`/`shutdownNow()` idiom.
--> **Prefer bounded queues with an explicit rejection policy in production**, rather than the unbounded queues used by `Executors.newFixedThreadPool`/`newCachedThreadPool` defaults.
--> **An uncaught exception in a `Runnable` submitted via `execute()`** propagates to the pool's `UncaughtExceptionHandler` (often just logged) and the thread is replaced; an exception in a `Callable`/`submit()`ed task is instead captured and re-thrown from `future.get()` -- easy to silently miss if you never call `get()` at all.
--> **`invokeAny` cancels the losing tasks** -- don't rely on all submitted tasks in an `invokeAny` batch actually completing.
--> **Match pool type to workload** -- CPU-bound work wants a pool sized near core count; I/O-bound work can profitably use many more threads than cores.
--> **`ScheduledExecutorService`'s `scheduleAtFixedRate` can pile up overlapping executions** if a task regularly takes longer than its period -- prefer `scheduleWithFixedDelay` when task duration is variable or unpredictable.
