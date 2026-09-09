# Why Threads Need to Communicate, Not Just Lock

--> `synchronized` and locks (file 02) solve MUTUAL EXCLUSION -- preventing two threads from touching shared state at the same time. But many real problems also need **coordination**: a thread that must WAIT until some condition becomes true (e.g. "wait until the queue has an item"), and another thread that makes that condition true and then NOTIFIES the waiter. Busy-checking a condition in a tight loop (`while (!ready) {}`) wastes CPU and doesn't scale -- `wait()`/`notify()` is Java's built-in mechanism for a thread to sleep efficiently until told otherwise.

# `wait()`, `notify()`, and `notifyAll()` -- The Basics

--> These three methods live on `Object` itself (every object has them), and can ONLY be called from within a block synchronized on that SAME object -- calling them without holding the object's monitor throws `IllegalMonitorStateException`.

```text
Object's monitor (intrinsic lock) has TWO roles here:
  1. Mutual exclusion  -- only one thread executes the synchronized block at a time
  2. Wait set           -- threads that called wait() sit here until notified
```

--> **`obj.wait()`** -- releases the lock on `obj` and suspends the current thread until another thread calls `notify()`/`notifyAll()` on the SAME object (or the wait times out, if a timed overload is used, or the thread is interrupted). Crucially, `wait()` is the ONE thing that releases the lock while blocked -- unlike `sleep()`, which holds all its locks the entire time.
--> **`obj.notify()`** -- wakes up ONE arbitrary thread waiting on `obj` (no guarantee which one). That thread does not resume immediately -- it must first RE-ACQUIRE the lock, which it can only do once the notifying thread releases it (typically by leaving the synchronized block).
--> **`obj.notifyAll()`** -- wakes up ALL threads waiting on `obj`; they then compete to re-acquire the lock one at a time. **This is almost always the safer default over `notify()`** -- with `notify()`, if the "wrong" thread happens to wake up for the current situation, it and every other legitimately waiting thread can be left hanging (a subtle, hard-to-debug form of missed signal).

```java
class SimpleSignal {
    private boolean ready = false;
    private final Object lock = new Object();

    public void awaitReady() throws InterruptedException {
        synchronized (lock) {
            while (!ready) {           // ALWAYS re-check the condition in a loop, never `if`
                lock.wait();
            }
            System.out.println("proceeding, ready is true");
        }
    }

    public void signalReady() {
        synchronized (lock) {
            ready = true;
            lock.notifyAll();          // wake up all waiters to re-check the condition
        }
    }
}
```

--> **Why `while`, never `if`, around `wait()`:** this guards against two real hazards. (1) **Spurious wakeups** -- the JVM is technically permitted to wake a waiting thread even without a matching `notify()` call, so the woken thread must re-verify the condition still holds. (2) **Multiple waiters with `notifyAll()`** -- several threads can wake up, but only some (or none) of them may find the condition still true by the time they each get the lock back (another thread might have consumed the resource first) -- each waiter must re-check for itself.

# The Producer-Consumer Pattern

--> The producer-consumer pattern is the canonical use case for `wait()`/`notify()`: one or more **producer** threads generate items and place them in a shared buffer; one or more **consumer** threads remove and process items from that buffer. Coordination is needed in both directions: producers must wait when the buffer is FULL, consumers must wait when the buffer is EMPTY.

```text
Producer(s) --> [ bounded buffer/queue ] --> Consumer(s)
                     ^              |
                     |              v
              full? producer    empty? consumer
              waits              waits
```

```java
import java.util.LinkedList;
import java.util.Queue;

class BoundedBuffer<T> {
    private final Queue<T> queue = new LinkedList<>();
    private final int capacity;
    private final Object lock = new Object();

    public BoundedBuffer(int capacity) { this.capacity = capacity; }

    public void put(T item) throws InterruptedException {
        synchronized (lock) {
            while (queue.size() == capacity) {      // full -- wait for a consumer to make room
                lock.wait();
            }
            queue.add(item);
            lock.notifyAll();                        // wake any consumer waiting on "empty"
        }
    }

    public T take() throws InterruptedException {
        synchronized (lock) {
            while (queue.isEmpty()) {                 // empty -- wait for a producer to add something
                lock.wait();
            }
            T item = queue.poll();
            lock.notifyAll();                          // wake any producer waiting on "full"
            return item;
        }
    }
}
```

--> **Why `notifyAll()` here specifically:** the same lock's wait set contains BOTH producers (waiting for "not full") and consumers (waiting for "not empty") in this simple design. `notify()` might wake a producer when a consumer just freed a slot but no new item was added -- the woken producer would find space fine, but if it woke the wrong TYPE of thread relative to what actually changed, useful capacity could go unused for a while. `notifyAll()` avoids that whole class of subtle bugs at a small performance cost, which is why it's the recommended default unless you've proven `notify()` is correct and worth the optimization for a specific hot path.
--> **In real code, prefer `BlockingQueue`** (`ArrayBlockingQueue`, `LinkedBlockingQueue` from `java.util.concurrent`, covered in file 05) over hand-rolling this pattern -- it implements exactly this bounded-buffer coordination internally, correctly and efficiently, so there's rarely a good reason to write raw `wait()`/`notify()` producer-consumer code outside of learning exercises.

# The `volatile` Keyword -- Visibility Without Mutual Exclusion

--> `synchronized` provides BOTH mutual exclusion and memory visibility. `volatile` provides ONLY visibility -- it guarantees that reads and writes to a `volatile` field go directly to main memory (not a CPU-core-local cache) and that all threads see the most recent write immediately, but it does NOT make compound operations (like `count++`) atomic, and it does NOT provide mutual exclusion.

```java
class Flag {
    private volatile boolean running = true;   // visibility guaranteed across threads

    public void stop() { running = false; }    // write is immediately visible to other threads

    public void run() {
        while (running) {                       // without volatile, this loop could see a STALE
            // do work                            cached "true" forever and never notice stop()
        }
        System.out.println("stopped");
    }
}
```

--> **`volatile` is correct here** because there's exactly ONE kind of update (a simple flag flip) and no compound read-modify-write dependency between threads.
--> **`volatile` is WRONG for this** because `count++` is read-modify-write, and `volatile` does nothing to prevent two threads from interleaving those three steps -- you'd still lose updates exactly like the unsynchronized example in file 02:

```java
class BrokenCounter {
    private volatile int count = 0;
    public void increment() { count++; }   // STILL a race condition despite volatile!
}
```

--> **Rule of thumb:** `volatile` is appropriate for a single flag or reference that is written by one thread (or in a way that individual writes don't depend on the previous value) and read by others. The moment an update depends on the CURRENT value (`x++`, `x = x + delta`, "add to a list"), you need real synchronization (`synchronized`, `Lock`, or an atomic class from file 05) instead.

# Memory Visibility -- The Deeper "Why"

--> Modern CPUs and the JVM aggressively optimize memory access: each CPU core may cache a variable's value locally, and the compiler/JIT may reorder instructions for performance, as long as single-threaded behavior is unaffected. Without an explicit visibility guarantee, a write by Thread A can sit in Thread A's CPU cache or store buffer indefinitely, invisible to Thread B, which might keep reading its own stale cached copy forever -- there is no guarantee it "eventually" becomes visible on its own.

```text
Thread A's core:  writes running = false  --> stored in A's local cache
Thread B's core:  reads running           --> still sees its OWN cached "true", never re-fetches
                                               (without a happens-before edge, this can persist "forever")
```

--> **The Java Memory Model (JMM)** formally defines when one thread's writes are guaranteed visible to another thread's reads, via the concept of **happens-before** relationships. Actions with no happens-before relationship between them may be seen in any order, or not seen at all, by a different thread.
--> **Sources of happens-before edges relevant here:**
  1. A `volatile` write happens-before every subsequent `volatile` read of the SAME field.
  2. Releasing a lock (leaving a `synchronized` block) happens-before a later thread acquiring that SAME lock.
  3. Starting a thread (`Thread.start()`) happens-before anything that thread does.
  4. Everything a thread does happens-before another thread successfully returns from `join()` on it.
--> This is precisely why `synchronized` blocks (file 02) fix BOTH the race condition (mutual exclusion) AND ensure the final value is visible to other threads afterward (visibility) -- two properties that are easy to conflate but are conceptually distinct, and `volatile` gives you only the second one.

# Common Gotchas and Best Practices Recap

--> **Always call `wait()` inside a `while` loop checking the condition, never a plain `if`** -- protects against spurious wakeups and multi-waiter races with `notifyAll()`.
--> **Prefer `notifyAll()` over `notify()`** unless you have a specific, well-understood reason and have proven `notify()` is safe for that exact scenario.
--> **`wait()`/`notify()`/`notifyAll()` must be called while holding the object's monitor** (i.e. inside a `synchronized` block on that same object), or you get `IllegalMonitorStateException`.
--> **`volatile` guarantees visibility, not atomicity** -- never use it alone for compound updates like counters; use atomic classes (file 05) or locks instead.
--> **In production code, prefer higher-level `java.util.concurrent` tools** (`BlockingQueue`, `CountDownLatch`, `Semaphore`, covered in file 05, and `Condition` objects paired with `ReentrantLock`) over hand-written `wait()`/`notify()` -- they're easier to get right, better tested, and communicate intent more clearly.
--> **`sleep()` does NOT release locks; `wait()` does** -- this is the key operational difference to remember when choosing between them inside synchronized code.
