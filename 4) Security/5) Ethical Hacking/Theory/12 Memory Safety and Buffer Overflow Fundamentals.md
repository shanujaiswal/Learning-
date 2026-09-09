### Memory Safety and Buffer Overflow Fundamentals

--> LEGAL/ETHICAL REMINDER: this note explains a conceptual memory-corruption bug class and the mitigations built to stop it. The legal way to practice this hands-on is CTF "pwn" challenges (picoCTF, HackTheBox, TryHackMe, dedicated binary-exploitation ranges) and your own compiled test binaries — never against production software or systems you don't own/aren't authorized to test.

--> Goal of this note: understand how stack memory is laid out, why writing past a fixed-size buffer corrupts adjacent memory, why that historically allowed control-flow hijacking, and — most importantly — why modern languages and compilers evolved specific mitigations to make this bug class far harder to exploit today.

## Why Memory Safety Is a Foundational Security Topic

--> A huge fraction of historical critical vulnerabilities (in operating systems, browsers, network daemons, embedded firmware) trace back to a single root cause: a program written in a memory-unsafe language (C, C++) accessing memory outside the bounds the programmer intended. Buffer overflows are the classic, most-taught example of this root cause, which is why they remain foundational even though modern mitigations have made straightforward exploitation much harder than it was in the 1990s-2000s.

## Stack Memory Layout Basics

--> When a program calls a function, the CPU and calling convention set up a "stack frame" for that function on the call stack — a region of memory that grows and shrinks as functions are called and return.

--> A simplified x86-family stack frame, growing from high addresses toward low addresses (the stack grows "downward" in memory on most common architectures):

```text
Higher memory addresses
 +------------------------+
 |  Caller's stack frame  |
 +------------------------+
 |  Return address        |  <- where execution resumes after this function returns
 +------------------------+
 |  Saved base pointer    |  <- caller's frame pointer, saved so it can be restored
 +------------------------+
 |  Local variable: buf[] |  <- a fixed-size local buffer, e.g. char buf[64]
 +------------------------+
 |  Other local variables |
 +------------------------+
Lower memory addresses (stack grows this direction on most platforms)
```

1. Return address - the memory address the CPU jumps back to once the current function finishes. This is the single most valuable piece of data on the stack from an attacker's perspective, because control over it means control over what code executes next.
2. Saved base pointer (frame pointer) - lets the function correctly reference its own locals and restore the caller's frame on return.
3. Local buffer - a fixed-size chunk of memory reserved for a local variable like `char buf[64]`. Crucially, the buffer's size is fixed at compile time, but many unsafe operations that fill it (older C string/copy functions) don't inherently check that the data being written actually fits.

--> The key structural fact: on many calling conventions, local buffers sit *below* (at lower addresses than) the saved return address on the stack. That physical adjacency is precisely what makes a buffer overflow dangerous — writing past the end of the buffer means writing directly toward, and potentially over, the return address and other critical control-flow data.

## Why Overflowing a Fixed-Size Buffer Is Dangerous

--> A buffer overflow happens when code writes more data into a fixed-size buffer than the buffer was allocated to hold, and the language/function doing the writing does not enforce a bound. In memory-unsafe languages, there's nothing stopping the write from simply continuing past the buffer's end into whatever memory happens to sit next to it.

--> Illustrative, simplified C-like pseudocode (deliberately unsafe, for teaching purposes only — never write real code this way):

```c
// Illustrative pseudocode: an unsafe fixed-size buffer copy
void handle_input(char *user_supplied_data) {
    char buf[64];              // fixed-size local buffer, 64 bytes
    strcpy(buf, user_supplied_data);  // copies with NO bounds checking whatsoever -
                                       // if user_supplied_data is longer than 64 bytes,
                                       // strcpy keeps writing past the end of buf
    // ... use buf ...
}
```

--> What "overflowing into adjacent memory" conceptually means: if `user_supplied_data` is, say, 200 bytes long, `strcpy` will happily write all 200 bytes starting at `buf`'s address — the first 64 bytes land inside `buf` as intended, but the remaining ~136 bytes land in whatever memory comes right after `buf` on the stack: potentially other local variables, the saved base pointer, and ultimately the saved return address. Because the write has no concept of "stop at the buffer's boundary," it simply overwrites whatever bytes are physically next, regardless of what those bytes represent.

```text
Before overflow:
[  buf (64 bytes)  ][ saved base ptr ][ return address ]

After writing 200 bytes of attacker-controlled data starting at buf:
[  attacker data (64 bytes fills buf) ][ overwritten! ][ overwritten with attacker bytes! ]
```

## Why This Historically Allowed Control-Flow Hijacking

--> If an attacker can control exactly what bytes land at the return-address location, and the CPU has no way to distinguish "a legitimate return address" from "whatever bytes happen to be sitting there," then when the function returns, the CPU jumps to whatever address the attacker placed — not the caller's real code. Classic exploitation redirected execution to attacker-supplied shellcode (either injected directly into the overflowing buffer itself, or, once direct code injection was mitigated, chained together from existing executable code already in the program via techniques like return-oriented programming). This is the essence of "control-flow hijacking": corrupting data that the CPU implicitly trusts to determine what instructions run next.

--> This is why the historical severity of buffer overflows was so high: a single unchecked `strcpy`, `gets`, `sprintf`, or similar unbounded-copy call could be the difference between "harmless crash" and "arbitrary remote code execution," entirely depending on what an attacker managed to place at the overwritten return address.

## Modern Mitigations

--> Decades of exploitation experience against this exact bug class drove the development of several layered defenses. None of them make memory-unsafe code *correct*, but together they make turning a raw buffer overflow into working code execution dramatically harder.

1. Stack canaries (stack protector) - the compiler inserts a random, secret value ("canary") between local buffers and the saved return address/base pointer. Before a function returns, it checks whether the canary is still intact. A naive linear buffer overflow that overwrites the return address will, by necessity, also overwrite the canary in between — and the corrupted canary is detected before the corrupted return address is ever used, causing the program to abort safely instead of jumping to attacker-controlled code.
2. ASLR (Address Space Layout Randomization) - randomizes the base addresses of the stack, heap, and loaded libraries on each run. Even if an attacker corrupts a return address, without knowing the actual runtime address of any useful code to jump to (their own injected shellcode, or an existing library function), a raw overflow becomes far less reliable to exploit — the attacker is essentially guessing addresses in a much larger, randomized space.
3. DEP/NX (Data Execution Prevention / No-eXecute) - marks memory regions like the stack and heap as non-executable at the hardware/MMU level. Even if an attacker successfully injects shellcode into a buffer and redirects execution there, the CPU refuses to execute instructions from a page marked non-executable. This is precisely why exploitation techniques shifted toward return-oriented programming (chaining together small snippets of *already-executable, legitimate* code already present in the binary/libraries) rather than injecting fresh shellcode.
4. Stack protector compiler flags - modern compilers (`-fstack-protector`, `-fstack-protector-all`/`-strong` in GCC/Clang, `/GS` in MSVC) automatically insert canaries and reorder stack variables (placing buffers closer to the canary/return address, and pointers/other sensitive variables further away) specifically to make the most dangerous overflow patterns easier to detect and less useful even when undetected.
5. Memory-safe language design - the most structural fix of all is not detecting overflows after the fact but making them impossible to write in the first place. Languages like Rust enforce bounds-checked array/slice access and ownership rules at compile time (and where dynamic checks are needed, at runtime with a controlled panic rather than silent memory corruption); Go performs runtime bounds checking on slice/array access by default. Choosing a memory-safe language for new systems-level code eliminates this entire bug class structurally, rather than relying on mitigations layered around an inherently unsafe operation.

```text
Illustrative layered-defense stack frame (conceptual, not to scale):

[ buf (64 bytes) ][ STACK CANARY (random) ][ saved base ptr ][ return address ]
        ^ overflow here                 ^ must overwrite this
                                            to reach the return address -
                                            and doing so is detected before
                                            the corrupted return address is used
```

--> Why combining mitigations matters: each one closes a different part of the exploitation chain. Canaries catch the *overwrite* of the return address itself. ASLR makes it hard to know *where* to redirect execution to. DEP/NX makes it impossible to simply *execute injected bytes* even if you do control where execution jumps. An attacker historically needed to defeat all of these simultaneously (e.g. via an information leak to defeat ASLR, combined with ROP to defeat DEP/NX) to achieve reliable code execution from a raw stack overflow — which is exactly why modern binary exploitation research is so much more involved than "overflow a buffer, jump to shellcode," and why it remains an active, skill-intensive discipline studied through CTF "pwn" challenges rather than something trivially automatable.

## Why CTF "Pwn" Challenges Are the Legal Practice Venue

--> Binary exploitation ("pwn") CTF challenges are purpose-built, intentionally vulnerable binaries distributed for legal practice — you compile or are given a binary with deliberate memory-safety bugs (sometimes with specific mitigations deliberately disabled for teaching purposes, e.g. compiled with `-fno-stack-protector` or without ASLR, to isolate one concept at a time) and practice identifying and exploiting the bug in an isolated, authorized environment. Platforms like picoCTF, HackTheBox, and TryHackMe host structured pwn tracks specifically for this purpose, progressing from simple stack overflows with no mitigations up through binaries with the full modern mitigation stack enabled, which is the closest legal analog to real-world exploitation difficulty.

## Terminology Recap

1. Stack frame - the region of stack memory allocated for a single function call, containing its locals, saved registers, and the return address.
2. Buffer overflow - writing more data into a fixed-size buffer than it can hold, corrupting adjacent memory.
3. Control-flow hijacking - corrupting data the CPU trusts to decide what executes next (classically, the return address), redirecting execution to attacker-influenced code.
4. Stack canary - a random sentinel value placed to detect stack-buffer corruption before a corrupted return address is used.
5. ASLR - randomizing memory layout at runtime so an attacker can't reliably predict useful addresses.
6. DEP/NX - marking data memory regions non-executable so injected bytes can't simply be run as code.
7. ROP (return-oriented programming) - a technique for achieving code execution despite DEP/NX, by chaining together small pieces of already-executable code instead of injecting new code.
8. Memory-safe language - a language whose design (bounds checking, ownership rules) makes this bug class structurally unreachable rather than merely harder to exploit.

--> The throughline: buffer overflows are the textbook example of what happens when a language trusts the programmer to manually enforce a boundary that the hardware itself does not enforce. Modern defense-in-depth (canaries, ASLR, DEP/NX) manages the symptom; memory-safe language design addresses the actual root cause, which is why so much new systems programming has moved toward languages like Rust and Go over the last decade.
