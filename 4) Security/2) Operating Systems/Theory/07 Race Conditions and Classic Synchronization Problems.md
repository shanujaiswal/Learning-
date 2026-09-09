# What a Race Condition Actually Is

--> A race condition happens when two or more threads/processes access SHARED data concurrently, and the final result depends on the unpredictable ORDER in which their operations happen to interleave -- "unpredictable" because the OS scheduler (from `04 CPU Scheduling Algorithms.md`) can preempt a thread at literally any instruction, including in the middle of what looks like a single line of code.
--> The classic minimal example is an increment that looks atomic in source code but is not atomic at the machine level -- `counter += 1` actually compiles to three separate steps: READ counter into a register, ADD 1 to the register, WRITE the register back to counter. If a thread is preempted between the read and the write, another thread can read the SAME stale value, and one of the two increments is silently lost.

```
Initial: counter = 0

Thread A                          Thread B
--------                          --------
read counter -> 0
                                   read counter -> 0
add 1 -> 1
                                   add 1 -> 1
write counter <- 1
                                   write counter <- 1

Expected result after both threads increment once: counter = 2
Actual result because of the interleaving above:    counter = 1   <-- one increment LOST
```

--> This is not a hypothetical corner case -- it is the single most common bug category in concurrent programs, and the reason "just run it a thousand times in testing and it looked fine" is not a valid way to rule one out: a race condition can go unnoticed for years and then manifest only under specific timing/load conditions in production (or, from a security angle, be DELIBERATELY triggered by an attacker who can influence scheduling/timing, as in TOCTOU -- Time-Of-Check-To-Time-Of-Use -- vulnerabilities, where a check like "does this file exist and am I allowed to read it" and the later "actually read it" are separated in time, and an attacker swaps what the path points to in between).

# The Fix -- Mutual Exclusion

--> The general fix is ensuring the sequence of operations that must not be interleaved (the "critical section") runs as if it were ATOMIC -- only one thread inside it at a time -- typically enforced with a **mutex** (mutual exclusion lock): a thread must ACQUIRE the lock before entering the critical section and RELEASE it after, and any other thread trying to acquire it while held simply blocks until it's free.

```python
import threading

counter = 0
lock = threading.Lock()

def increment():
    global counter
    with lock:            # acquire on enter, release on exit -- even if an exception is raised inside
        counter += 1       # now genuinely atomic with respect to other threads holding the same lock

threads = [threading.Thread(target=increment) for _ in range(1000)]
for t in threads:
    t.start()
for t in threads:
    t.join()

print(counter)   # reliably 1000 -- without the lock, this is often LESS than 1000 due to lost updates
```

--> Locking correctly is its own discipline -- holding a lock too broadly kills concurrency (defeating the point of having multiple threads at all), holding it too narrowly reintroduces the race, and acquiring multiple locks in inconsistent order across different threads is exactly the deadlock scenario covered in `05 Deadlocks.md`. The three classic problems below are the standard vehicle for practicing exactly this trade-off.

# Producer-Consumer (Bounded Buffer)

--> One or more PRODUCER threads generate items and place them into a shared, fixed-size buffer; one or more CONSUMER threads remove items from that buffer and process them. Two hazards exist simultaneously: producers must not add to a FULL buffer, consumers must not remove from an EMPTY buffer, and the buffer's own item count/pointers are shared mutable state subject to the exact race condition above if two threads touch them at once.
--> The clean solution uses a mutex (protecting the buffer itself) PLUS two counting semaphores -- `empty` (how many empty slots remain) and `full` (how many filled slots exist) -- semaphores, unlike a plain mutex, can block a thread until a COUNT condition is satisfied, which is exactly "wait until there's room" / "wait until there's something to take."

```python
import threading

BUFFER_SIZE = 5
buffer = []

mutex = threading.Lock()             # protects the buffer's internal list itself
empty_slots = threading.Semaphore(BUFFER_SIZE)   # counts down as items are added
full_slots  = threading.Semaphore(0)             # counts up as items are added, down as consumed

def producer(item):
    empty_slots.acquire()    # block if the buffer is already full
    with mutex:
        buffer.append(item)
    full_slots.release()     # signal: one more item is now available to consume

def consumer():
    full_slots.acquire()     # block if the buffer is currently empty
    with mutex:
        item = buffer.pop(0)
    empty_slots.release()    # signal: one more empty slot is now available
    return item
```

--> The order matters -- acquiring the semaphore BEFORE the mutex (not after) is what actually avoids deadlock here: if a producer took the mutex first and then blocked waiting on `empty_slots` while HOLDING the mutex, a consumer that needs that same mutex to free up a slot could never get in, and both sides would be stuck forever.

# Readers-Writers

--> A shared resource (commonly modeled as a data structure or file) is read by many threads concurrently without any conflict -- reading doesn't modify anything, so multiple simultaneous readers are perfectly safe. But a WRITER modifying that resource must have EXCLUSIVE access -- no reader or other writer may touch it while a write is in progress, or a reader could observe a half-updated, inconsistent state (or two concurrent writers could corrupt it the same way the counter example did above).
--> The straightforward first-cut solution: track an active-reader count; the FIRST reader to arrive acquires the writer-exclusion lock (blocking any writer for the whole duration any readers are present), and the LAST reader to leave releases it.

```python
import threading

read_count = 0
read_count_lock = threading.Lock()   # protects the read_count variable itself
resource_lock = threading.Lock()     # the actual exclusive lock writers need, and the first/last reader toggles

def reader():
    global read_count
    with read_count_lock:
        read_count += 1
        if read_count == 1:
            resource_lock.acquire()      # first reader locks writers out entirely
    # ... perform the actual read here, concurrently with any other readers ...
    with read_count_lock:
        read_count -= 1
        if read_count == 0:
            resource_lock.release()      # last reader leaving lets a writer in

def writer():
    resource_lock.acquire()
    # ... perform the write, exclusively -- no readers or other writers active ...
    resource_lock.release()
```

--> This naive version has a known fairness flaw -- a steady stream of arriving readers can keep `read_count` above zero indefinitely, permanently starving any waiting writer (a concrete instance of the starvation concept from `04 CPU Scheduling Algorithms.md` and `05 Deadlocks.md`). Production-grade readers-writers locks (e.g. Python's `threading` has no built-in one, but libraries and most OS-level implementations do) typically add a fairness mechanism -- e.g. a waiting writer blocks any NEW readers from starting until it gets its turn, even though technically more readers could still safely proceed.

# Dining Philosophers

--> Five philosophers sit at a round table with five forks, one between each adjacent pair -- eating requires holding BOTH forks on either side of you, but there are only five forks for five philosophers, so forks must be shared and contested. This models the general problem of multiple threads each needing SEVERAL shared resources at once (not just one, which is what producer-consumer and readers-writers each cover), and it's the standard example for demonstrating exactly how deadlock arises and how to design around it.
--> **The naive, deadlock-prone version** -- every philosopher picks up their LEFT fork first, then their RIGHT fork. If all five do this at the exact same moment, every philosopher successfully grabs their left fork and then blocks forever waiting for their right fork (which is some other philosopher's already-taken left fork) -- all four Coffman conditions from `05 Deadlocks.md` (mutual exclusion, hold-and-wait, no preemption, circular wait) are satisfied simultaneously, and nobody ever eats again.

```python
import threading

NUM_PHILOSOPHERS = 5
forks = [threading.Lock() for _ in range(NUM_PHILOSOPHERS)]

def philosopher_naive(i):
    left, right = forks[i], forks[(i + 1) % NUM_PHILOSOPHERS]
    left.acquire()     # <-- if every philosopher does this at once, deadlock: everyone
    right.acquire()    #     holds their left fork and blocks forever on their right
    # ... eat ...
    right.release()
    left.release()
```

--> **The standard fix -- break the circular wait.** The simplest correct fix is having the philosophers acquire forks in a globally consistent order (e.g. always the LOWER-numbered fork before the higher-numbered one) rather than each always going "left then right" relative to their own seat -- this is exactly the "acquire locks in a fixed global order" deadlock-prevention strategy from `05 Deadlocks.md`, applied concretely.

```python
def philosopher_fixed(i):
    first_fork_id  = min(i, (i + 1) % NUM_PHILOSOPHERS)
    second_fork_id = max(i, (i + 1) % NUM_PHILOSOPHERS)

    forks[first_fork_id].acquire()    # everyone agrees on this same global ordering,
    forks[second_fork_id].acquire()   # so a circular wait can never form
    # ... eat ...
    forks[second_fork_id].release()
    forks[first_fork_id].release()
```

--> An alternative fix used in some textbook treatments: an arbitrator/waiter thread that only allows a philosopher to pick up forks if BOTH are simultaneously available, checked and granted atomically (via a single lock protecting the "who currently holds which fork" state) -- functionally a different implementation of the same principle: never let a thread hold one needed resource while indefinitely waiting on another that's part of a cycle.

# Why These Three Problems Specifically

--> Producer-consumer teaches waiting on a CONDITION (buffer has room / buffer has data) rather than just excluding concurrent access -- the semaphore-count pattern.
--> Readers-writers teaches that not all concurrent access is equally dangerous -- SOME operations (reads) can safely overlap, and forcing full exclusion for those would be needless, correctness-preserving-but-unnecessarily-slow over-caution.
--> Dining philosophers teaches multi-resource acquisition and the circular-wait root cause of deadlock directly, connecting concurrency primitives back to the deadlock theory in `05 Deadlocks.md`.
--> Together they cover the three fundamental shapes concurrency bugs take in real systems: lost updates from unprotected shared state (the plain race condition at the top of this file), resource-availability signaling, and multi-resource deadlock -- almost every production concurrency bug reduces to one of these three patterns, however deeply it's buried in a real codebase.
