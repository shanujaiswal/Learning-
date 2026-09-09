# Why Memory Management Matters for Security

--> Nearly every classic exploitation technique in the Ethical Hacking track (buffer overflows, use-after-free, heap exploitation) is fundamentally an abuse of how memory is managed -- this file is the prerequisite for understanding WHY those attacks work at all.

# The Process Memory Layout

--> Every process gets its own virtual address space, conventionally divided into regions:

```
High addresses  +-------------------+
                 |       Stack        |  <-- function calls, local variables (grows downward)
                 |         |          |
                 |         v          |
                 |    (free space)    |
                 |         ^          |
                 |         |          |
                 |        Heap        |  <-- dynamically allocated memory (grows upward)
                 +-------------------+
                 |  BSS (uninitialized|
                 |    global vars)    |
                 +-------------------+
                 |  Data (initialized |
                 |    global vars)    |
                 +-------------------+
Low addresses    |    Text/Code       |  <-- the actual compiled program instructions
                 +-------------------+
```

--> Stack -- stores local variables and function call information (return addresses, parameters) -- automatically managed, grows/shrinks as functions are called/return. Stack Buffer Overflows (covered in Ethical Hacking) happen when writing past a stack-allocated buffer overwrites adjacent stack data, including a saved return address.
--> Heap -- dynamically allocated memory (`malloc` in C, `new` in many languages) that persists until explicitly freed -- manual memory management bugs here (use-after-free, double-free) are a major exploitation category.

# Virtual Memory -- The Illusion of Isolation

--> Each process believes it has access to a large, contiguous, private address space starting from address 0 -- this is virtual memory, an abstraction. The OS + CPU's Memory Management Unit (MMU) translate these virtual addresses to actual physical RAM addresses behind the scenes.
--> This is precisely what enforces process isolation -- Process A's virtual address `0x1000` and Process B's virtual address `0x1000` map to entirely different physical RAM locations; neither can accidentally (or, without a kernel exploit, maliciously) read the other's memory.
--> Paging -- physical memory is divided into fixed-size chunks (pages, commonly 4KB); a page table per process tracks which virtual pages map to which physical pages.

# Swapping / Paging to Disk

--> When physical RAM is full, the OS can move a page that isn't currently needed out to disk (the "swap" or "page file"), freeing RAM for active data -- and bring it back when needed.
--> This is why running out of RAM causes major slowdowns rather than an immediate crash -- the system is still functioning, but paging to/from disk is orders of magnitude slower than RAM access.

# Memory Protection

--> Each page has associated permissions -- readable, writable, executable -- enforced by hardware (the MMU), not just software convention.
--> DEP/NX (Data Execution Prevention / No-eXecute) -- marks memory regions like the stack as non-executable, so even if an attacker manages to inject shellcode into the stack via a buffer overflow, the CPU refuses to execute it as code -- a major mitigation against classic stack-based exploits.
--> ASLR (Address Space Layout Randomization) -- randomizes where the stack, heap, and libraries are loaded in memory on each run, making it far harder for an attacker to reliably predict a memory address to jump to (e.g. for a return-oriented-programming chain).
--> These two mitigations are exactly the ones referenced in the Ethical Hacking track's Memory Safety file as things a working exploit needs to bypass.

# Memory Leaks and Garbage Collection

--> Memory Leak -- allocated memory that's never freed and never used again -- in long-running processes (servers), this gradually consumes all available RAM until the system degrades or crashes.
--> Manual memory management (C/C++) -- the programmer explicitly allocates and frees memory; powerful but a direct source of use-after-free and double-free bugs.
--> Garbage Collection (Python, Java, JavaScript, Go) -- the runtime automatically reclaims memory no longer reachable by the program -- removes a whole category of manual-memory-management vulnerabilities, at the cost of some runtime overhead and less predictable pause timing.
