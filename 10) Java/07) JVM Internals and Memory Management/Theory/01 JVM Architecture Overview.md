# Why JVM Internals Matter

--> Java code doesn't run directly on hardware -- it compiles to BYTECODE, a platform-independent instruction set, which the Java Virtual Machine (JVM) then loads, verifies, and executes -- this is the entire basis of "write once, run anywhere."
--> Understanding the JVM isn't academic trivia -- it directly explains why `OutOfMemoryError` happens, why the first few seconds of a Java program are slower than the rest (JIT warm-up), why `-Xmx` and other flags exist, and why garbage collection pauses show up in production latency graphs. Every later file in this module (GC, leaks, class loading, tuning) is really just a deeper look at one piece of the architecture covered here.

# The Big Picture -- From `.java` to Running Program

```text
MySource.java  --javac-->  MySource.class (bytecode)  --JVM loads-->  Class Loader Subsystem
                                                                              |
                                                                              v
                                                                     Runtime Data Areas
                                                                    (Heap, Stacks, Metaspace, ...)
                                                                              |
                                                                              v
                                                                       Execution Engine
                                                                 (Interpreter + JIT Compiler + GC)
                                                                              |
                                                                              v
                                                                    Native OS / Hardware
```

--> `javac` (the Java compiler) turns source code into `.class` files containing BYTECODE -- a compact, platform-neutral instruction set (think of it as an intermediate language, not machine code).
--> The JVM is the program that actually reads and executes that bytecode. "The JVM" is really shorthand for three cooperating subsystems: the **Class Loader Subsystem**, the **Runtime Data Areas** (memory), and the **Execution Engine**. This file walks through all three.
--> **JVM vs JRE vs JDK** -- the JVM is just the execution engine; the JRE (JVM + core class libraries like `java.lang`, `java.util`) is what's needed to RUN Java programs; the JDK (JRE + compiler, debugger, tools like `jps`/`jstack`) is what's needed to DEVELOP them. You need the JDK to build, but only a JRE/JVM to run a compiled application.

# 1) The Class Loader Subsystem

--> Before any code can execute, the JVM has to get the `.class` file's bytecode into memory, verify it's safe, and prepare it for use. This happens in three phases: **Loading**, **Linking**, and **Initialization**.

```text
Loading  ---->  Linking (Verify -> Prepare -> Resolve)  ---->  Initialization
```

## Loading

--> Reads the `.class` file's binary data (from disk, a JAR, or the network) and creates a corresponding `java.lang.Class` object in memory, which becomes the class's runtime representation -- every `.getClass()` call returns one of these.
--> Loading is delegated across a **hierarchy of class loaders**, each responsible for a different part of the classpath:

```text
Bootstrap ClassLoader   -- loads core JDK classes (java.lang.*, java.util.*) from the JDK's own modules -- written in native code, has no Java-visible parent
        |
        v (parent)
Platform ClassLoader    -- loads JDK-supplied but non-core classes (used to be "Extension" pre-Java 9)
        |
        v (parent)
Application ClassLoader -- loads YOUR application's classes from the classpath/JAR -- this is what `MyClass.class.getClassLoader()` returns for normal user code
        |
        v (parent, optional)
Custom ClassLoaders     -- user-defined, e.g. for plugin systems, hot-reloading, or isolating dependency versions
```

--> **Delegation model** -- by default, a class loader asks its PARENT to load a class first, and only tries loading it itself if the parent can't find it. This is called **parent-first delegation**, and it's the reason a malicious or accidental class named `java.lang.String` in your own code can never silently replace the real `java.lang.String` -- the bootstrap loader always wins that race. Covered in depth with working custom loader code in File 04.

## Linking

--> **Verify** -- the bytecode verifier checks that the `.class` file is structurally valid and doesn't violate JVM safety rules (no illegal type casts, no stack over/underflows, no jumping into the middle of another method). This is what makes it safe to load bytecode from untrusted sources -- a hand-crafted or corrupted `.class` file gets rejected with a `VerifyError` rather than crashing the JVM or corrupting memory.
--> **Prepare** -- allocates memory for the class's static fields and sets them to their default values (`0`, `null`, `false`) -- NOT their declared initial values yet.
--> **Resolve** -- (can happen lazily) converts symbolic references in the constant pool (e.g. the textual name `"java/util/ArrayList"`) into direct references (actual memory addresses/pointers) by loading whatever other classes are referenced.

## Initialization

--> Runs the class's static initializers and static field assignments, IN THE ORDER they appear in the source, top to bottom. This is when `static { ... }` blocks execute and `static int x = 5;` actually becomes `5` (Prepare had already zeroed it to `0`).
--> **Initialization is lazy and triggered on first active use** -- typically the first time the class is instantiated, one of its static methods/fields is accessed, or a subclass is initialized. Merely referencing a class in code (e.g. as a parameter type) does not trigger initialization.
--> **Thread safety guarantee** -- the JVM guarantees class initialization happens exactly once and is synchronized across threads, which is why the "initialization-on-demand holder" idiom is a common thread-safe lazy-singleton pattern in Java (a static nested class is only initialized when first accessed, and the JVM's own locking makes it thread-safe for free, no explicit `synchronized` needed).

# 2) Runtime Data Areas -- Where the JVM Keeps Its Memory

--> Once code is loaded, the JVM needs memory to run it. That memory is divided into distinct regions, each with different lifetimes, sharing rules, and failure modes.

```text
                         JVM Process Memory
        +----------------------------------------------------------+
        |  SHARED across all threads                                |
        |  +----------------+   +--------------------------------+  |
        |  |     Heap       |   |   Metaspace (was PermGen)       |  |
        |  | (objects live  |   |  class metadata, method bytecode|  |
        |  |  here)         |   |  static fields' definitions     |  |
        |  +----------------+   +--------------------------------+  |
        |                                                            |
        |  PER-THREAD (one copy of each, per running thread)         |
        |  +--------------+  +----------------+  +----------------+ |
        |  | JVM Stack    |  | PC Register     |  | Native Method  | |
        |  | (frames:     |  | (address of the |  | Stack (for JNI/| |
        |  | locals, refs,|  |  current bytecode|  | native calls)  | |
        |  | partial      |  |  instruction)    |  |                | |
        |  | results)     |  |                  |  |                | |
        |  +--------------+  +----------------+  +----------------+ |
        +----------------------------------------------------------+
```

## Heap

--> Where every object created with `new` (or via reflection, autoboxing, string concatenation, etc.) actually lives. SHARED across all threads -- this is the memory region garbage collection manages, and the subject of File 02 in full detail.
--> Divided internally into generations (Young/Old) for GC efficiency purposes -- covered fully in File 02.
--> Sized with `-Xms` (initial heap size) and `-Xmx` (max heap size). Running out of heap space that can't be reclaimed produces `java.lang.OutOfMemoryError: Java heap space`.

## JVM Stack (per thread)

--> Each thread gets its OWN stack, created when the thread starts. Every method call pushes a new **stack frame** onto it, containing that method's local variables, the operand stack for intermediate computation, and a reference back to the calling frame.
--> Frames are popped when the method returns. This is why local variables (primitives and object REFERENCES, not the objects themselves) disappear when a method exits -- but any object they pointed to survives on the heap if something else still references it.
--> Deep or infinite recursion exhausts this per-thread memory, throwing `java.lang.StackOverflowError` -- NOT an `OutOfMemoryError`, because it's a different memory region with a different failure mode. Sized with `-Xss`.

## PC (Program Counter) Register

--> Each thread also has its own PC register, holding the address of the JVM instruction currently executing. Lets the JVM resume a thread exactly where it left off after a context switch. Trivially small, never a practical concern for tuning.

## Native Method Stack

--> Used when Java code calls into native (non-Java) code via JNI (Java Native Interface) -- e.g. calls that eventually reach OS-level or C-library code. Most application code never interacts with this directly, but library internals (I/O, some `java.lang.Math` functions, reflection under the hood) do.

## Metaspace (formerly PermGen)

--> Stores CLASS-level metadata: the structure of loaded classes, method bytecode, the runtime constant pool, static field definitions. NOT the same as the heap -- this describes classes themselves, not the objects instantiated from them.
--> **History note** -- before Java 8, this lived in a fixed-size region called PermGen (Permanent Generation), which was a notoriously common source of `OutOfMemoryError: PermGen space` in applications that loaded lots of classes dynamically (app servers redeploying web apps was the classic offender). Java 8 replaced it with Metaspace, which lives in NATIVE memory (outside the heap) and by default grows dynamically, though it can still be capped with `-XX:MaxMetaspaceSize` and still throws `OutOfMemoryError: Metaspace` if that cap is hit.
--> A classloader leak (File 03) that keeps creating classes without ever releasing their loader is the classic modern cause of Metaspace exhaustion.

# 3) The Execution Engine

--> This is the part that actually RUNS the bytecode. It's not one simple thing -- it's a layered system trading off startup speed against peak throughput.

## Interpreter

--> Reads bytecode instructions one at a time and executes them directly, translating each to the equivalent machine operations on the fly. Starts running immediately -- no compilation delay -- but is slow per-instruction because it re-decodes the same bytecode every single time it's encountered, even in a hot loop executed millions of times.

## JIT (Just-In-Time) Compiler

--> The JVM tracks how often each method is called ("hotness"). Methods that get called frequently enough get compiled directly to native machine code at RUNTIME, after which every future call runs the fast compiled version instead of being re-interpreted -- this is why long-running Java programs get FASTER the longer they run ("warm-up").
--> **Tiered compilation** (default since Java 8, controlled by `-XX:+TieredCompilation`) uses two JIT compilers in stages:

```text
Tier 0 -- Interpreter               -- runs everything initially, collects profiling data
Tier 1-3 -- C1 (Client compiler)    -- compiles quickly, less optimized, good for fast warm-up
Tier 4 -- C2 (Server compiler)      -- compiles slowly but produces highly optimized code, kicks in for the hottest methods
```

--> **Key JIT optimizations**:
  - **Inlining** -- replacing a method call with the method's body directly, eliminating call overhead -- especially powerful for small getters/setters and lambda bodies.
  - **Escape analysis** -- if the JIT proves an object never "escapes" the method it's created in (no reference leaves via return value, field assignment, etc.), it can allocate that object on the STACK instead of the heap, or eliminate the allocation entirely -- reducing GC pressure without any code change.
  - **Loop unrolling / vectorization** -- restructuring loops to reduce branch overhead or use SIMD hardware instructions.
  - **Deoptimization** -- if an assumption the JIT optimized for turns out wrong later (e.g. a class that was assumed "never subclassed" gets subclassed via dynamic loading), the JVM can fall back from compiled code to the interpreter and re-optimize -- this is rare but explains occasional unexpected latency spikes in long-running services.

## Garbage Collector

--> Technically part of the execution engine's responsibilities (it runs as part of the managed runtime), but large enough a topic to get its own file in full (File 02).

# Bytecode Basics

--> Bytecode is stack-based (not register-based like real CPUs) -- most instructions pop operands off an per-frame "operand stack," operate on them, and push the result back.

```java
int a = 1;
int b = 2;
int c = a + b;
```

```text
// Roughly compiles to (viewable via `javac -c` / `javap -c`):
iconst_1        // push int constant 1
istore_1        // pop into local variable slot 1 (a)
iconst_2        // push int constant 2
istore_2        // pop into local variable slot 2 (b)
iload_1         // push local variable 1 (a) onto the stack
iload_2         // push local variable 2 (b) onto the stack
iadd            // pop two ints, add them, push result
istore_3        // pop into local variable slot 3 (c)
```

--> You can inspect real bytecode yourself with `javap -c MyClass.class` -- genuinely useful for understanding what a lambda, a `switch` on strings, or autoboxing actually compiles down to, and for confirming JIT/escape-analysis-relevant assumptions.
--> **Constant pool** -- each `.class` file has a table of constants (string literals, class/method/field references) that bytecode instructions refer to by index rather than embedding directly -- part of what makes `.class` files compact and portable.

# Gotchas and Best Practices

--> **`ClassNotFoundException` vs `NoClassDefFoundError`** -- the first is thrown when code EXPLICITLY tries to load a class by name (e.g. `Class.forName(...)`) and fails; the second is thrown when a class was successfully compiled against and available at compile time, but is missing at RUNTIME when the JVM tries to link/use it (classic cause: a JAR present at build time but missing from the runtime classpath).
--> **Static initializer exceptions are sticky** -- if a `static { }` block throws, the JVM wraps it in `ExceptionInInitializerError` and marks the class as permanently unusable for the rest of that JVM run -- every subsequent attempt to use it throws `NoClassDefFoundError`, even though the original problem was different. Keep static initializers simple and defensive.
--> **Don't fight the JIT prematurely** -- manually "optimizing" code to help the JIT (e.g. avoiding small methods to reduce "call overhead") usually backfires, since the JIT is specifically good at inlining and optimizing small, simple methods. Write clear code first; profile before micro-optimizing.
--> **Warm-up matters for benchmarks** -- naively timing a method's first call and its 10,000th call in the same JVM run will show very different numbers purely due to interpretation vs JIT compilation, unrelated to any code change. Proper microbenchmarking tools (like JMH) account for this explicitly.
