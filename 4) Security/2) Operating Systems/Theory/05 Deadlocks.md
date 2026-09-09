# What a Deadlock Is

--> A deadlock occurs when two or more processes are each waiting for a resource held by ANOTHER process in the same waiting group -- none of them can ever proceed, since each is stuck waiting on someone else who is, in turn, waiting on them.

```
Process A holds Resource 1, waiting for Resource 2
Process B holds Resource 2, waiting for Resource 1
--> Neither can ever proceed. Permanently stuck.
```

# The Four Necessary Conditions (Coffman Conditions)

--> A deadlock can only occur if ALL FOUR of these hold simultaneously -- preventing any ONE of them is enough to prevent deadlock entirely:
--> **Mutual Exclusion** -- at least one resource is held in a non-shareable way (only one process can use it at a time).
--> **Hold and Wait** -- a process holding a resource is simultaneously waiting to acquire another.
--> **No Preemption** -- a resource can't be forcibly taken away from a process holding it; it must be released voluntarily.
--> **Circular Wait** -- a cycle exists where each process is waiting for a resource held by the next process in the cycle.

# Deadlock Prevention Strategies

--> Break Mutual Exclusion where possible -- use resources that support concurrent access instead of exclusive locks, when the use case allows it.
--> Break Hold and Wait -- require a process to request ALL the resources it will need up front, before starting, rather than acquiring them incrementally while already holding others.
--> Allow Preemption -- let the OS/runtime forcibly reclaim a resource from a process if needed (with rollback), rather than requiring voluntary release.
--> Break Circular Wait -- the most common practical fix: impose a strict, GLOBAL ORDER on how resources may be acquired (e.g. "always acquire Lock A before Lock B, never the reverse") -- if every process obeys the same ordering, a circular wait becomes structurally impossible.

```python
# Deadlock-prone: two threads may acquire locks in opposite order
def transfer_a_to_b():
    with lock_a:
        with lock_b:
            move_funds()

def transfer_b_to_a():
    with lock_b:      # Opposite order from the function above -- classic deadlock risk
        with lock_a:
            move_funds()

# Fixed: always acquire locks in the same globally agreed-upon order
def transfer_a_to_b():
    with lock_a:
        with lock_b:
            move_funds()

def transfer_b_to_a():
    with lock_a:      # Same order as above, regardless of transfer direction
        with lock_b:
            move_funds()
```

# Deadlock Detection and Recovery

--> Rather than preventing deadlocks upfront, some systems instead let them potentially occur, periodically checking for cycles in a resource-allocation graph, and recovering by forcibly terminating or rolling back one of the deadlocked processes if one is detected.
--> This trades a small chance of a deadlock actually occurring (and needing recovery) against the sometimes-significant performance cost of strict prevention -- a legitimate trade-off depending on how costly a rare deadlock actually is for that specific system.

# Deadlock vs Starvation vs Race Condition -- Related but Distinct

--> Deadlock -- processes are stuck waiting on each other, permanently, with no progress possible at all.
--> Starvation -- a process is repeatedly denied a resource it needs (often due to scheduling priority, covered in the previous file), but the SYSTEM as a whole is still making progress -- just not for that one unlucky process.
--> Race Condition -- covered in the Concurrency file in the Python Backend notes -- the outcome of concurrent operations depends on unpredictable timing, producing an incorrect result, without necessarily involving any waiting/blocking at all.

# Real-World Relevance

--> Database transaction deadlocks (two transactions each waiting on a row lock the other holds -- directly connects to the Transactions and ACID file in the Database notes) are a common, concrete example most backend developers will eventually encounter -- most database engines detect this automatically and abort one of the transactions, requiring the application to retry it.
