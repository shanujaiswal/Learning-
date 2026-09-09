# Why Page Replacement Is Needed

--> `02 Memory Management and Virtual Memory.md` covered swapping/paging at a high level -- when physical RAM (frames) fills up and a process needs a page that isn't currently resident, the OS must evict (replace) some OTHER page currently in RAM to make room. WHICH page gets evicted matters enormously for performance: evict a page about to be needed again immediately, and you just cause another page fault a moment later; evict a genuinely unused one, and the system runs smoothly.
--> A "page fault" is what happens when a process accesses a page not currently in RAM -- the OS must pause the process, find/choose a frame (possibly evicting its current occupant to disk first), load the needed page from disk into that frame, then resume the process. This is orders of magnitude slower than a normal memory access (disk I/O vs RAM access), so minimizing the FREQUENCY of page faults is the entire goal of a good replacement algorithm.

# FIFO -- First-In, First-Out

--> The simplest possible policy -- evict whichever page has been in memory the LONGEST, regardless of whether it's been used recently. Implemented with just a queue: new pages join the back, evictions come from the front.
--> Cheap to implement, but ignores actual usage pattern entirely -- a page loaded early and used constantly ever since gets evicted just because it happens to be old, immediately causing a fault to bring it right back in.
--> **Belady's Anomaly** -- a genuinely counterintuitive property specific to FIFO: for some access patterns, INCREASING the number of available frames can actually INCREASE the number of page faults, which violates the intuition that more memory should never make things worse. (LRU and Optimal, below, never exhibit this anomaly.)

```
Reference string: 1 2 3 4 1 2 5 1 2 3 4 5     Frames available: 3

Frame contents over time (FIFO, oldest evicted first):
1  1,2  1,2,3  [fault:4 evicts 1]->2,3,4  [fault:1 evicts 2]->3,4,1  [fault:2 evicts 3]->4,1,2
[fault:5 evicts 4]->1,2,5  (1 hit, 2 hit)  [fault:3 evicts 1]->2,5,3  [fault:4 evicts 2]->5,3,4
[fault:5? already there -> hit]

Total faults in this run: 9 -- worked through step by step to show FIFO simply evicts by AGE,
with no regard for which page is about to be needed again.
```

# LRU -- Least Recently Used

--> Evicts whichever page has gone the LONGEST without being accessed -- the intuition being temporal locality: a page used recently is likely to be used again soon, so the safest thing to evict is the one that's been quietest.
--> Performs much better than FIFO in practice (real programs do exhibit temporal locality -- loops, repeated data structure access) and never suffers Belady's Anomaly, but exact LRU requires tracking the precise access ORDER of every resident page, which is expensive to maintain perfectly on every single memory access at OS scale -- true hardware-timestamp-per-access LRU is rarely implemented exactly; the Clock algorithm below is the practical approximation actually used.

```
Reference string: 1 2 3 4 1 2 5 1 2 3 4 5     Frames available: 3

Access 1: fault, load -> {1}                     resident, least-recent-first order: [1]
Access 2: fault, load -> {1,2}                   order: [1,2]
Access 3: fault, load -> {1,2,3}                 order: [1,2,3]
Access 4: fault, evict LRU=1  -> {2,3,4}         order: [2,3,4]
Access 1: fault, evict LRU=2  -> {3,4,1}         order: [3,4,1]
Access 2: fault, evict LRU=3  -> {4,1,2}         order: [4,1,2]
Access 5: fault, evict LRU=4  -> {1,2,5}         order: [1,2,5]
Access 1: HIT (already resident) -> reorder:     order: [2,5,1]
Access 2: HIT (already resident) -> reorder:     order: [5,1,2]
Access 3: fault, evict LRU=5  -> {1,2,3}         order: [1,2,3]
Access 4: fault, evict LRU=1  -> {2,3,4}         order: [2,3,4]
Access 5: fault, evict LRU=2  -> {3,4,5}

Every HIT moves that page to the "most recently used" end instead of triggering an eviction --
that reordering-on-hit is the entire mechanism, and it's what makes exact LRU bookkeeping
expensive enough that real kernels approximate it with Clock instead.
```

# Optimal (Belady's Algorithm)

--> Evicts whichever resident page will NOT be used again for the LONGEST time in the future -- provably the theoretical minimum possible number of page faults for any given reference string and frame count.
--> Impossible to actually implement in a real running system, because it requires knowing the FUTURE sequence of memory accesses in advance -- exactly the same "requires knowing the future" limitation that makes Shortest-Job-First scheduling in `04 CPU Scheduling Algorithms.md` impractical to run exactly. Its entire purpose is as a theoretical BASELINE -- other algorithms are evaluated by how close their fault count comes to Optimal's, not by beating it (nothing can beat it).

# Clock (Second-Chance) -- The Practical Approximation of LRU

--> Real operating systems don't track exact last-access order (too expensive); instead, each page has one hardware-maintained "reference bit," set to 1 automatically by the CPU/MMU whenever the page is accessed.
--> Pages are arranged conceptually in a circle with a single "clock hand" pointer. To pick a victim: examine the page the hand currently points to -- if its reference bit is 0, evict it; if it's 1, give it a "second chance" by clearing the bit to 0 and advancing the hand to the next page, repeating until it lands on a page with a 0 bit.

```
Circular list of resident pages, hand currently pointing at page C:

   A(ref=1) -> B(ref=0) -> [C(ref=1)] -> D(ref=1) -> back to A ...
                              ^ hand here

Step 1: C has ref=1 -> give second chance: clear C's bit to 0, advance hand to D
Step 2: D has ref=1 -> give second chance: clear D's bit to 0, advance hand to A
Step 3: A has ref=1 -> give second chance: clear A's bit to 0, advance hand to B
Step 4: B has ref=0 -> EVICT B, replace with the new incoming page, advance hand past it
```

--> This gets most of LRU's practical benefit (recently-touched pages survive; long-untouched ones get evicted) at a fraction of the bookkeeping cost, which is why it (or a close variant) is what real kernels actually run rather than exact LRU or the impossible Optimal.

# Disk Scheduling Algorithms

--> A separate but structurally similar problem: a spinning hard disk's read/write head can only be in one physical position at a time, and multiple pending I/O requests may ask for data at very different disk locations -- the ORDER those requests are serviced in significantly affects total seek time (how far and how often the head physically moves), which on mechanical drives dominates I/O latency. (On SSDs seek time is not a physical concern, but understanding this family of algorithms is still foundational, and many embedded/older systems and disk simulators in coursework still use it directly.)

## FCFS (First-Come, First-Served)

--> Services requests in the exact order they arrive, with no reordering -- simple and fair in arrival order, but can produce a lot of unnecessary back-and-forth head movement if requests happen to arrive in a scattered order across the disk (the direct disk-I/O analog of FCFS CPU scheduling's convoy effect from `04 CPU Scheduling Algorithms.md`).

## SSTF (Shortest Seek Time First)

--> Always services whichever PENDING request is physically closest to the head's current position -- minimizes seek distance for the immediate next move, generally giving much better average performance than FCFS.
--> Risk: **starvation** -- a request for a track far from the current cluster of activity can be repeatedly passed over in favor of closer ones arriving continuously nearby, waiting indefinitely (the same starvation failure mode Priority Scheduling and Readers-Writers exhibit in `04` and `07`).

## SCAN ("The Elevator Algorithm")

--> The head sweeps in ONE direction (say, toward higher track numbers), servicing every pending request it passes along the way, until it reaches the end of the disk -- then reverses direction and sweeps back, servicing requests on the way back too. Named for behaving exactly like a building elevator, which doesn't reverse mid-sweep just because someone on a floor it already passed pressed the button after it went by.
--> Avoids SSTF's starvation problem (every request eventually gets serviced as the sweep passes its location) while still keeping seek movement far more orderly than FCFS.

```
Disk tracks 0-199, head starts at 50, sweeping toward higher tracks,
pending requests at: 30, 90, 120, 160

SCAN order: 90, 120, 160 (continue to track 199, the physical end) then reverse -> 30
            (head visits every pending request exactly once per full sweep, in position order)
```

## C-SCAN (Circular SCAN)

--> A variant of SCAN that only services requests while moving in ONE direction; upon reaching the end of the disk, it jumps immediately back to the OTHER end WITHOUT servicing any requests during that return jump, then begins a fresh sweep in the same original direction again.
--> This gives more UNIFORM wait times than plain SCAN -- in plain SCAN, a request that just missed the sweep near the starting point has to wait for the entire round trip (out and back) before being serviced, whereas C-SCAN treats the disk as circular, so every region gets serviced at a consistent interval rather than the near-the-turnaround-point requests getting serviced twice as often as far-from-it ones.

```
Plain SCAN:   50 -> ... -> 199 -> ... -> 0 -> ... -> 50   (services requests on BOTH the outbound
                                                             and the return leg)
C-SCAN:       50 -> ... -> 199 -> [jump straight to 0, servicing nothing during the jump] -> ... -> 50
                                                             (services requests only going ONE way,
                                                              treating the disk as a circular track)
```

# Why This Still Matters on SSDs and in Security Contexts

--> Modern SSDs have no meaningful seek time, so pure SCAN/SSTF distance-minimization is largely moot for them directly -- but the underlying I/O SCHEDULING layer in the OS (deciding request ORDER and fairness, e.g. Linux's `mq-deadline` or `bfq` I/O schedulers) still exists and still has to balance throughput against starvation, just with different physical costs to optimize against.
--> Security relevance: I/O scheduling fairness (or its absence) is directly implicated in resource-exhaustion/DoS scenarios -- a process issuing a flood of disk requests can, depending on the scheduler and its fairness guarantees, degrade or starve other processes' I/O, the disk-level analog of the CPU-scheduling-based DoS concern raised in `04 CPU Scheduling Algorithms.md`.
