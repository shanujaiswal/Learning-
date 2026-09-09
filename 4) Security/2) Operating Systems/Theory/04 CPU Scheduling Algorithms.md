# Why Scheduling Matters

--> With more ready processes/threads than CPU cores (the normal case), the OS must decide WHICH one gets to run next and for how long -- the Scheduler is the component making that decision, and different scheduling algorithms trade off fairness, responsiveness, and throughput differently.

# Key Scheduling Metrics

--> Throughput -- how many processes complete per unit of time.
--> Turnaround time -- total time from a process's arrival to its completion.
--> Waiting time -- how long a process sits in the ready queue before getting CPU time.
--> Response time -- how quickly a process gets its FIRST burst of CPU time after being submitted -- critical for interactive systems where a user is waiting.

# First-Come, First-Served (FCFS)

--> Processes run in the exact order they arrive, with no preemption -- simple to implement, but a single long-running process at the front of the queue makes every process behind it wait, even short ones ("convoy effect").

# Shortest Job First (SJF)

--> Always runs whichever ready process has the shortest expected burst time next -- provably minimizes average waiting time, but requires knowing burst times in advance (rarely knowable exactly in practice) and can starve long processes indefinitely if short ones keep arriving.

# Round Robin

--> Each process gets a fixed time slice ("quantum") -- if it doesn't finish, it's preempted and moved to the back of the ready queue, giving every process a fair, bounded turn.
--> The quantum size matters a lot -- too short, and the system wastes excessive time on context switching overhead; too long, and it starts behaving like FCFS, hurting responsiveness for interactive tasks.

```
Ready Queue: [P1, P2, P3]  quantum = 4ms

Time 0-4:  P1 runs (still has work left) --> moved to back
Time 4-8:  P2 runs (finishes) --> removed
Time 8-12: P3 runs (still has work left) --> moved to back
Time 12-16: P1 runs again ...
```

# Priority Scheduling

--> Each process is assigned a priority; the scheduler always picks the highest-priority ready process next.
--> Risk -- Priority Inversion / Starvation: a low-priority process can be indefinitely delayed if higher-priority processes keep arriving. Aging (gradually increasing a waiting process's priority the longer it waits) is the standard fix, guaranteeing eventual execution.

# Multilevel Feedback Queue -- What Real Operating Systems Actually Use

--> Modern OS schedulers (Linux's CFS -- Completely Fair Scheduler, Windows' scheduler) use more sophisticated hybrid approaches combining ideas from the above -- multiple priority queues, with processes moving between them based on observed behavior (a process that frequently yields for I/O gets treated as interactive and prioritized for responsiveness; a CPU-bound process gets a longer quantum but lower priority for fairness against interactive tasks).

# Why This Matters for Security and Performance Work

--> Understanding scheduling directly informs both performance tuning (why is my process not getting scheduled promptly -- check its priority/`nice` value) and certain security concerns -- resource-exhaustion/DoS attacks (covered in the Cyber Security track) often work precisely by monopolizing scheduling-relevant resources (spawning excessive processes/threads) to starve legitimate work of CPU time.

```bash
nice -n 10 ./low_priority_task.sh     # Linux: run with a lower scheduling priority
renice -n -5 -p 1234                    # Increase priority of an already-running process
```
