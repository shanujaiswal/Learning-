# What Java Is, and the Philosophy Behind It

--> Java is a general-purpose, class-based, object-oriented programming language, first released by Sun Microsystems in 1995 (now owned and developed by Oracle) -- it was designed from the outset around one central promise: **Write Once, Run Anywhere (WORA)**.
--> The core idea behind WORA -- instead of compiling source code directly to machine code tied to one specific operating system and CPU (the way C or C++ traditionally does), Java compiles to an intermediate form called **bytecode**, which any machine running a **Java Virtual Machine (JVM)** can execute -- the same compiled `.class` file runs unmodified on Windows, Linux, and macOS, as long as each has a JVM installed.
--> Java is **statically typed** (types are checked at compile time, catching a large class of errors before the program ever runs) and **strongly typed** (you cannot silently treat a value as a different, incompatible type) -- this trades a bit of writing speed for a large gain in reliability and tooling support (autocomplete, refactoring, compile-time error checking) compared to dynamically typed languages.
--> Java is also **automatically memory-managed** -- programs don't manually allocate and free memory the way C does; the JVM's garbage collector reclaims memory for objects that are no longer reachable, removing an entire category of bugs (use-after-free, double-free, memory leaks from forgotten `free()` calls) at the cost of some runtime overhead, discussed briefly later in this file and in depth in a dedicated Memory Management chapter.

# JVM vs JRE vs JDK -- What Each One Actually Contains

--> These three acronyms are the single most common source of early confusion, because each one is a strict SUPERSET of the one before it -- understanding the containment relationship makes the rest click into place.

```text
JDK  (Java Development Kit)
 |
 |--> JRE  (Java Runtime Environment)
 |     |
 |     |--> JVM  (Java Virtual Machine)      -- executes bytecode
 |     |--> Core class libraries              -- java.lang, java.util, java.io, etc.
 |     |--> Supporting files                  -- property files, etc.
 |
 |--> Development tools
       |--> javac   -- the compiler (source .java -> bytecode .class)
       |--> java    -- the launcher (runs a compiled program, hosts the JVM)
       |--> javadoc -- generates HTML documentation from source comments
       |--> jar     -- packages compiled classes into a distributable .jar archive
       |--> jdb, jshell, and other diagnostic/interactive tools
```

| Component | Purpose | Who needs it |
|---|---|---|
| **JVM** | Executes bytecode; provides the runtime engine (class loading, memory management, JIT compilation) | Anyone running a Java program |
| **JRE** | JVM + the standard class libraries needed to RUN Java programs | End users who only need to run Java applications (largely superseded — see note below) |
| **JDK** | JRE + compiler and development tools needed to WRITE and BUILD Java programs | Developers |

--> **A practical note on modern Java** -- as of Java 11 onward, Oracle stopped shipping the JRE as a separate standalone download; today you install a JDK even if you only intend to run programs, and you can produce a slimmed-down custom runtime image with the `jlink` tool if you need a minimal deployment footprint. The JDK/JRE/JVM conceptual layering above still describes what each piece DOES, even though the JRE isn't distributed on its own anymore.
--> **Common beginner mix-up** -- "I installed Java but `javac` isn't found" almost always means a JRE-only install (or an incomplete PATH setup) was used instead of a full JDK -- `java` runs programs, but only the JDK's `javac` compiles them.

# The Compilation and Execution Model

--> Java uses a two-stage model: a portable COMPILE step, followed by a platform-specific EXECUTE step -- this split is exactly what makes WORA possible.

```text
 MyProgram.java  --[ javac ]-->  MyProgram.class  --[ java / JVM ]-->  Running program
 (source code,                  (bytecode,                            (JVM interprets
  human-readable)                 platform-independent)                 and/or JIT-compiles
                                                                          to native machine code)
```

--> **Step 1 -- Compilation (`javac`)**: the Java compiler reads your `.java` source file, checks it for syntax and type errors, and translates it into **bytecode** -- a compact, platform-neutral instruction set stored in a `.class` file. This is NOT machine code for any particular CPU; it's an instruction set designed specifically for the JVM to consume.
--> **Step 2 -- Execution (`java`)**: the `java` launcher starts a JVM instance, which loads the `.class` file's bytecode and runs it. The JVM can run bytecode two ways, and modern JVMs use both together:
  - **Interpretation** -- the JVM reads and executes bytecode instructions one at a time, immediately, with no preparation delay -- this is why a Java program can start running right away.
  - **Just-In-Time (JIT) compilation** -- the JVM watches which methods run frequently ("hot" code) and compiles THOSE specific methods down to native machine code on the fly, caching the result -- subsequent calls to that method run at near-native speed instead of being re-interpreted every time.
--> This interpret-then-JIT strategy is why Java historically has a short "warm-up" period where a program runs a bit slower before it reaches peak speed -- the JVM hasn't yet identified and compiled the hot paths.

# Bytecode and Platform Independence, Explained

--> Bytecode is the key abstraction that decouples "code that Java produces" from "code a specific CPU understands" -- `javac` only ever needs to know how to produce standard JVM bytecode, and it never needs to know anything about Windows vs Linux vs macOS, or x86 vs ARM.
--> All of the platform-specific work is pushed down into the JVM itself -- there is a DIFFERENT JVM binary for each operating system/architecture combination, and each one knows how to translate the SAME bytecode into the correct native instructions for its own machine.

```text
                     MyProgram.class (identical bytecode file)
                              |
        -----------------------------------------------
        |                     |                        |
   JVM for Windows       JVM for Linux            JVM for macOS
        |                     |                        |
  Windows machine code   Linux machine code      macOS machine code
```

--> **Why this matters practically** -- you compile once, and the resulting `.class` (or packaged `.jar`) file can be handed to any machine with a compatible JVM installed, with zero recompilation -- this is fundamentally different from a C program, where you typically need to recompile the source for each target platform.
--> **The trade-off** -- this portability isn't free; it's exactly why the JIT-compilation step exists at all -- a JVM has to do extra work at runtime to reach native-level performance, work that a directly-compiled C program never has to do because it was already compiled straight to machine code ahead of time.

# A Full "Hello World" Program, Line by Line

```java
public class HelloWorld {
    public static void main(String[] args) {
        System.out.println("Hello, World!");
    }
}
```

--> `public class HelloWorld` -- declares a class named `HelloWorld`. In Java, essentially ALL code lives inside a class -- there's no such thing as a loose top-level function floating outside any class (unlike Python or JavaScript). `public` means this class is visible from outside its own file/package.
--> **The filename rule** -- a `public` class MUST be saved in a file with the EXACT same name as the class, plus the `.java` extension -- this class must live in a file named `HelloWorld.java`, or the compiler rejects it. This is a Java-specific rule, not a general programming convention.
--> `public static void main(String[] args)` -- this exact signature is the designated ENTRY POINT the JVM looks for when you tell it to run a class:
  - `public` -- the JVM (external caller) must be able to see and call this method.
  - `static` -- means it belongs to the class itself rather than to any particular object instance, because the JVM calls it BEFORE any object of the class exists.
  - `void` -- `main` returns nothing back to the JVM.
  - `String[] args` -- an array holding any command-line arguments passed after the class name; empty (not null) if none were given.
--> `System.out.println("Hello, World!");` -- `System` is a built-in class in `java.lang` (automatically available, no import needed); `out` is a `PrintStream` field on `System` representing standard output; `println` writes the given text followed by a newline. Every statement in Java ends with a semicolon, and code blocks are delimited with `{ }`.

# Compiling and Running from the Command Line

```text
# Compile MyProgram.java into MyProgram.class (bytecode) in the same directory
javac MyProgram.java

# Run the compiled class -- note: no ".class" extension, and no ".java" either --
# you name the CLASS, and the JVM looks for MyProgram.class on the classpath
java MyProgram

# Passing command-line arguments -- these land in the main method's String[] args
java MyProgram arg1 arg2 arg3

# Compiling multiple files at once
javac FileOne.java FileTwo.java

# Compiling an entire directory of source files
javac *.java
```

--> **The classpath, briefly** -- the classpath is the list of locations (directories and/or `.jar` files) the JVM searches when it needs to find a compiled class. By default, running `java MyProgram` from a directory searches that current directory for `MyProgram.class` -- for larger projects with dependencies, you specify additional locations explicitly with `-cp` (or `-classpath`):
```text
java -cp .;lib/somelibrary.jar MyProgram      # Windows separator is ;
java -cp .:lib/somelibrary.jar MyProgram      # Unix/macOS separator is :
```
--> **`javac` vs `java` -- the confusion beginners hit most often** -- `javac` (the compiler) turns source into bytecode and produces NO output on the screen unless there's an error; `java` (the launcher) runs already-compiled bytecode and is what actually executes your `main` method and produces program output. Typing `java MyProgram.java` on very old JDKs would fail outright; modern JDKs (11+) do support running a single source file directly this way as a convenience for quick scripts, compiling it in-memory first, but this is a shortcut for simple cases, not the standard build workflow.

# Java Editions -- SE, EE/Jakarta EE, and ME

| Edition | Full name | Purpose |
|---|---|---|
| **Java SE** | Standard Edition | The core language, JVM, and standard libraries (collections, I/O, concurrency, etc.) -- the foundation every other edition builds on, and what this entire Core Java section focuses on |
| **Java EE / Jakarta EE** | Enterprise Edition (renamed Jakarta EE after Oracle transferred it to the Eclipse Foundation) | Adds APIs for large-scale server-side applications -- web servers, servlets, REST APIs, dependency injection, messaging -- built ON TOP of Java SE |
| **Java ME** | Micro Edition | A stripped-down subset of Java SE for resource-constrained embedded devices and older feature phones -- far less relevant today than it was in the 2000s, but still used in some embedded contexts |

--> **Why the "Jakarta EE" rename happened** -- Oracle donated the Java EE specification to the Eclipse Foundation in 2017, and for trademark reasons the project was renamed Jakarta EE going forward -- functionally it's the continuation of the same enterprise API family, just under new governance and a new name.
--> For everyday application-level Java learning (and for this entire study track), **Java SE is what you're using** -- Jakarta EE and frameworks like Spring build additional capability on top of the SE foundation covered here.

# Java Version History Highlights -- LTS vs Non-LTS

--> Since Java 9, Oracle moved to a much faster release cadence -- a new feature release every **six months** -- but not every release gets long-term support. **LTS (Long-Term Support)** releases are the ones organizations are expected to actually adopt and stay on for years; non-LTS releases are stepping stones that preview features and get replaced quickly.

| Version | Type | Notable highlights |
|---|---|---|
| Java 8 | LTS (2014) | Lambda expressions, the Streams API, `Optional`, default methods on interfaces -- widely considered the biggest single shift in how idiomatic Java code is written; still in heavy production use today |
| Java 11 | LTS (2018) | `var` for local type inference (from 10), new `HttpClient` API, string methods like `isBlank()`/`strip()`, removal of Java EE modules from the JDK |
| Java 17 | LTS (2021) | Sealed classes, pattern matching for `instanceof`, records (from 16) stabilized, strong encapsulation of JDK internals |
| Java 21 | LTS (2023) | Virtual threads (Project Loom) for lightweight concurrency, record patterns and pattern matching for `switch`, sequenced collections |

--> **Practical guidance** -- when starting a new project or learning the language today, targeting an LTS release (commonly 17 or 21) is the standard default, since LTS versions receive years of security patches and are what most production environments and job listings target -- non-LTS releases are worth knowing about for their previewed features, but rarely worth deploying to production directly.

# JVM Architecture at a High Level

--> The JVM isn't a single monolithic black box -- it's conventionally described as three cooperating subsystems. This is intentionally a brief overview here; deeper coverage belongs in a dedicated JVM Internals chapter later in this track.

```text
                          -----------------------------------
                          |            JVM                  |
                          |                                  |
  .class files  --------> |  1) Class Loader Subsystem       |
                          |     (loads, links, initializes    |
                          |      classes into memory)         |
                          |             |                     |
                          |             v                     |
                          |  2) Runtime Data Areas             |
                          |     - Method Area (class metadata) |
                          |     - Heap (all objects live here) |
                          |     - Stack (per-thread, method     |
                          |       calls & local variables)     |
                          |     - PC Registers, Native Stacks   |
                          |             |                     |
                          |             v                     |
                          |  3) Execution Engine                |
                          |     - Interpreter                  |
                          |     - JIT Compiler                 |
                          |     - Garbage Collector             |
                          -----------------------------------
```

--> **Class Loader Subsystem** -- finds a class's bytecode, verifies it's well-formed and safe, and prepares it in memory before it can be used -- this happens lazily, the first time a class is actually needed, not all at once at startup.
--> **Runtime Data Areas** -- where the JVM keeps everything while a program runs: the **heap** holds every object you create (shared across all threads); each thread gets its own **stack** for tracking method calls and local variables; the **method area** holds class-level metadata like method bytecode and static fields.
--> **Execution Engine** -- actually runs the bytecode, combining the interpreter and JIT compiler described earlier, and includes the **garbage collector**, which periodically scans the heap and reclaims memory occupied by objects nothing can reach anymore -- just enough context to know it exists; full GC algorithms and tuning are covered in a dedicated Memory Management / Garbage Collection chapter later in this track.

# Common Beginner Setup Gotchas

--> **`JAVA_HOME` not set, or pointing to the wrong install** -- many build tools (Maven, Gradle) and IDEs rely on the `JAVA_HOME` environment variable to locate the JDK -- if it's unset, missing, or pointing at a JRE-only install instead of a full JDK, builds fail with confusing errors that don't obviously mention `JAVA_HOME` at all.
--> **`PATH` not including the JDK's `bin` directory** -- even with `JAVA_HOME` set correctly, the shell won't find `javac`/`java` as bare commands unless the JDK's `bin` folder is also added to `PATH` -- this is the most common reason `java -version` works (because some other tool added just that) while `javac -version` says "command not found" (because a JRE-only path was added, not the full JDK's bin).
--> **Confusing `javac` and `java`** -- covered above, but worth repeating as the single most common early mistake: `javac` COMPILES (source -> bytecode, produces `.class` files, silent on success), `java` RUNS (bytecode -> executing program, produces your program's actual output).
--> **Multiple JDKs installed, wrong one active** -- especially common when different projects target different LTS versions -- `java -version` and `javac -version` should be checked together to confirm both point at the SAME installation, since a mismatched pair can compile with one version and silently attempt to run with another.
--> **Case sensitivity and filename mismatches** -- Java is case-sensitive, and (as covered above) a public class's filename must match the class name exactly, including case -- `helloworld.java` will not satisfy a class declared `public class HelloWorld`.

# Deep Dive -- Why "Compile Once, Run Anywhere" Still Has Edge Cases

--> WORA is a remarkably strong guarantee in practice, but it was never an absolute one, and understanding the exceptions deepens the understanding of what bytecode portability actually covers.
--> **Native code and platform-specific APIs** -- if a program calls out to native libraries via JNI (Java Native Interface), or depends on OS-specific file path conventions, environment variables, or line-ending assumptions, that program's BEHAVIOR can still differ across platforms even though the bytecode itself runs everywhere -- WORA guarantees the bytecode executes, not that every possible interaction with the surrounding OS is automatically portable.
--> **JVM version mismatches** -- bytecode compiled by a newer JDK targeting a newer class file version cannot run on an older JVM (you'll see an `UnsupportedClassVersionError`) -- portability across platforms is guaranteed for a GIVEN class file version, but forward compatibility (older JVM running newer bytecode) is not; backward compatibility (newer JVM running older bytecode) is generally very well maintained, which is itself a deliberate, heavily-tested design goal of the platform.
--> **Floating-point and locale-sensitive behavior** -- extremely rare in practice today, but historically some floating-point and locale/timezone-dependent operations had subtle platform-level differences -- modern JVMs have converged on `strictfp`-equivalent behavior by default since Java 17, closing most of this gap.
--> The practical takeaway: WORA is about the EXECUTION SEMANTICS of standard bytecode being platform-independent, which is a very strong and reliable guarantee for typical application code -- it was never a claim that literally everything a program could possibly do is automatically platform-agnostic.
