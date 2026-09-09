# Overview -- Java's Faster Release Cadence

--> Since Java 9 (2017), the JDK moved to a **six-month release cadence**, with certain versions (11, 17, 21) designated **LTS (Long-Term Support)**. This chapter surveys the most impactful language features introduced from Java 9 through 21: local-variable type inference (`var`), records, sealed classes, pattern matching for `switch`, text blocks, and the virtual threads introduced as a preview-then-final feature culminating in Java 21.
--> These features share a common theme: **reducing boilerplate and making illegal states harder to represent**, continuing the direction started by lambdas and Streams in Java 8.

# `var` -- Local Variable Type Inference (Java 10)

```java
var name = "Alice";                 // inferred as String
var count = 42;                     // inferred as int
var list = new ArrayList<String>(); // inferred as ArrayList<String>
var map = new HashMap<String, List<Integer>>();  // avoids repeating a long generic type twice

for (var i = 0; i < 10; i++) { }    // fine in loops
```

--> **`var` is compile-time type inference, NOT dynamic typing.** The compiler determines the exact static type at compile time from the initializer expression, and that type is then fixed forever -- `var x = "hello"; x = 5;` does not compile, exactly as if `String x = "hello";` had been written. There is zero runtime cost or flexibility gained; `var` is purely a source-code convenience that removes redundant type declarations.
--> **Restrictions:** `var` can only be used for local variables with an initializer, `for` loop variables, and try-with-resources variables -- NOT for fields, method parameters, return types, or as a bare declaration without an initializer (`var x;` does not compile, since there's nothing to infer from). It also cannot be initialized to `null` directly (`var x = null;` fails -- the compiler cannot infer a type from `null` alone).

```java
// AVOID -- reduces readability when the type isn't obvious from the right-hand side
var result = process(a, b, c);   // what type IS result? Reader must check process()'s signature

// PREFER var when the type is already obvious from the right side
var users = new ArrayList<User>();      // obviously ArrayList<User>
var reader = new BufferedReader(new FileReader("f.txt"));  // obviously BufferedReader
```

--> **Best practice:** use `var` when it improves readability by removing redundancy (especially with long generic types or obvious constructor calls), but avoid it when the inferred type would be unclear to a reader glancing at the line -- `var` is a readability tool, not a rule to apply everywhere.

# Records (Java 16, preview since 14) -- Concise Immutable Data Carriers

```java
record Point(int x, int y) { }

Point p1 = new Point(3, 4);
System.out.println(p1.x());        // 3  -- accessor, NOT getX()
System.out.println(p1.y());        // 4
System.out.println(p1);            // Point[x=3, y=4]  -- auto-generated toString()

Point p2 = new Point(3, 4);
System.out.println(p1.equals(p2)); // true -- auto-generated equals() compares all components
System.out.println(p1.hashCode() == p2.hashCode());  // true -- auto-generated hashCode()
```

--> A `record` declaration automatically generates: a canonical constructor, private final fields for each component, public accessor methods named exactly after the component (`x()`, not `getX()`), plus `equals()`, `hashCode()`, and `toString()` -- all consistent and based on every declared component. This eliminates the enormous boilerplate that hand-written immutable value classes used to require.
--> **Records are implicitly `final`** (cannot be extended) and all components are implicitly `private final` -- a record is fundamentally a transparent, immutable data carrier, not meant for classic OOP inheritance hierarchies.

```java
// Compact constructor -- validation without repeating the parameter list
record Range(int min, int max) {
    Range {   // no parameter list here -- refers to the canonical constructor's implicit parameters
        if (min > max) {
            throw new IllegalArgumentException("min must not exceed max");
        }
    }
}

// Records can have additional methods, static fields, and static factory methods
record Circle(double radius) {
    static final double PI_APPROX = 3.14159;

    double area() {
        return PI_APPROX * radius * radius;
    }

    static Circle unitCircle() {
        return new Circle(1.0);
    }
}
```

--> Records can implement interfaces (e.g. `record Point(int x, int y) implements Comparable<Point>`), but cannot extend another class (they implicitly extend `Record`) and cannot declare additional instance fields beyond their components -- keeping them strictly to their "data carrier" role.

# Sealed Classes and Interfaces (Java 17)

```java
sealed interface Shape permits Circle, Square, Triangle { }

record Circle(double radius) implements Shape { }
record Square(double side) implements Shape { }
final class Triangle implements Shape {
    final double base, height;
    Triangle(double base, double height) { this.base = base; this.height = height; }
}
```

--> `sealed` restricts which classes/interfaces may implement or extend a type -- the `permits` clause is an exhaustive, closed list, checked by the compiler. Every permitted subtype must itself be declared `final`, `sealed` (with its own further-restricted permits list), or `non-sealed` (reopening extensibility for that one branch only).
--> **Why this matters:** sealed types let the compiler know the COMPLETE set of possible subtypes at compile time -- this is what makes exhaustive pattern matching in `switch` (below) possible without a `default` branch, because the compiler can verify every case is covered.

```java
// If a permitted subclass is in the same file, the permits clause can sometimes be omitted
// (the compiler infers it from all direct subtypes declared in that same compilation unit)
sealed interface Vehicle { }
record Car() implements Vehicle { }
record Bike() implements Vehicle { }
```

# Pattern Matching for `instanceof` (Java 16)

```java
Object obj = "hello";

// Old style
if (obj instanceof String) {
    String s = (String) obj;      // manual, redundant cast
    System.out.println(s.length());
}

// Pattern matching -- the cast and declaration happen inline
if (obj instanceof String s) {
    System.out.println(s.length());   // s is already a String here, no cast needed
}
```

--> The pattern variable (`s`) is only in scope where the compiler can prove `obj instanceof String` is true -- including, thanks to flow typing, in code paths guarded by a negated check followed by an early return (`if (!(obj instanceof String s)) return; // s usable below`).

# Pattern Matching for `switch` (Java 21)

```java
sealed interface Shape permits Circle, Square, Triangle { }
record Circle(double radius) implements Shape { }
record Square(double side) implements Shape { }
record Triangle(double base, double height) implements Shape { }

static double area(Shape shape) {
    return switch (shape) {
        case Circle c -> Math.PI * c.radius() * c.radius();
        case Square s -> s.side() * s.side();
        case Triangle t -> 0.5 * t.base() * t.height();
        // NO default needed -- the compiler proves this switch is EXHAUSTIVE
        // because Shape is sealed and every permitted subtype has a case
    };
}
```

--> Because `Shape` is `sealed` with exactly three permitted implementations, the compiler can verify all cases are handled and does not require (or allow, without a warning) a redundant `default` -- if a new subtype is ever added to the `permits` list, every exhaustive `switch` over that sealed type will FAIL TO COMPILE until a new case is added, turning a class of runtime bugs into compile-time errors.

```java
// Pattern matching with record deconstruction (record patterns, Java 21)
record Point(int x, int y) { }

static String describe(Object obj) {
    return switch (obj) {
        case Point(int x, int y) when x == 0 && y == 0 -> "origin";
        case Point(int x, int y) when x == y -> "on the diagonal";
        case Point(int x, int y) -> "point at (" + x + ", " + y + ")";
        case null -> "null value";                 // switch can now match null directly (Java 21)
        default -> "not a point";
    };
}
```

--> **Record patterns** let a `switch` (or `instanceof`) destructure a record directly into its components in one step, and `when` clauses attach additional boolean guard conditions to a case -- both are Java 21 finalized features that make working with sealed hierarchies of records extremely expressive.
--> **`case null ->`** is new in Java 21 -- switch statements/expressions previously threw `NullPointerException` immediately on a `null` selector; now `null` can be matched explicitly as its own case, and if it isn't handled, the old NPE-throwing behavior is preserved for compatibility.

# Text Blocks (Java 15)

```java
// Old style -- string concatenation, easy to get whitespace/escaping wrong
String jsonOld = "{\n" +
        "  \"name\": \"Alice\",\n" +
        "  \"age\": 30\n" +
        "}";

// Text block -- triple-quote delimiters, preserves formatting naturally
String jsonNew = """
        {
          "name": "Alice",
          "age": 30
        }
        """;
```

--> A text block starts with `"""` followed immediately by a line terminator (content cannot start on the same line as the opening delimiter) and ends with a matching `"""`. The compiler strips a computed amount of **incidental leading whitespace** based on the indentation of the closing delimiter -- moving the closing `"""` left or right changes how much leading whitespace survives in every line, which is a common source of confusion until it "clicks."
--> Text blocks still support escape sequences (`\n`, `\"`, `\\`) and the special `\` line-continuation (suppresses the following newline) and `\s` (forces a trailing space that would otherwise be stripped).

```java
String noNewlineAtEnd = """
        line one \
        continues here""";   // trailing backslash suppresses the newline, joining the lines

String trailingSpacePreserved = """
        exactly one trailing space:\s
        """;
```

--> Text blocks are ideal for embedded JSON, SQL, HTML snippets, and any multi-line literal that previously required painful concatenation or escaping.

# Virtual Threads (Java 21, preview since 19)

```java
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

// Traditional platform threads -- each backed 1:1 by an OS thread, expensive to create in bulk
try (ExecutorService platformExecutor = Executors.newFixedThreadPool(200)) {
    // limited to a few hundred/thousand concurrent platform threads before resource exhaustion
}

// Virtual threads -- lightweight, JVM-managed, millions can exist concurrently
try (ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
    for (int i = 0; i < 100_000; i++) {
        int taskId = i;
        virtualExecutor.submit(() -> {
            // blocking calls (I/O, sleep) here do NOT block an OS thread --
            // the virtual thread is "unmounted" from its carrier thread while blocked
            System.out.println("Task " + taskId + " running on " + Thread.currentThread());
        });
    }
}   // executor.close() (implicit via try-with-resources) waits for submitted tasks
```

--> **Virtual threads (Project Loom) are lightweight threads managed by the JVM rather than the OS** -- creating a virtual thread is extremely cheap (no OS-level thread allocation), and millions can be created where only thousands of platform threads would be feasible. They are designed to make the simple, blocking, "one thread per request/task" programming style scale to very high concurrency, as an alternative to complex asynchronous/reactive code.
--> A virtual thread runs on top of a small pool of **carrier threads** (real platform threads) -- when a virtual thread performs a blocking operation (like blocking I/O), the JVM "unmounts" it from its carrier thread, freeing that carrier to run other virtual threads, and re-mounts the original virtual thread onto some carrier once the blocking operation completes. This is transparent to the code -- ordinary blocking calls (`Thread.sleep`, blocking I/O) work as expected, just without tying up an OS thread the whole time.

```java
Thread vt = Thread.ofVirtual().name("worker-1").start(() -> {
    System.out.println("Running in a virtual thread: " + Thread.currentThread().isVirtual());
});
vt.join();
```

--> **Gotcha:** virtual threads are NOT a general performance boost for CPU-bound work -- they help specifically with **I/O-bound, high-concurrency, blocking-style code** (many concurrent network calls, database queries, etc.). CPU-bound tasks are still limited by the number of actual CPU cores regardless of how many virtual threads are used.
--> **Gotcha:** code that relies on `synchronized` blocks around long blocking operations can "pin" a virtual thread to its carrier thread (preventing the unmount optimization) -- in performance-sensitive contexts, prefer `java.util.concurrent.locks.ReentrantLock` over `synchronized` for blocks that wrap blocking I/O calls under virtual threads.

# Quick Feature-to-Version Map

| Feature | Introduced (Final) | Preview started |
|---|---|---|
| `var` local type inference | Java 10 | -- |
| Text blocks | Java 15 | Java 13 |
| Pattern matching for `instanceof` | Java 16 | Java 14 |
| Records | Java 16 | Java 14 |
| Sealed classes/interfaces | Java 17 | Java 15 |
| Pattern matching for `switch`, record patterns | Java 21 | Java 17 |
| Virtual threads | Java 21 | Java 19 |

# Best Practices Summary

--> Use `var` to cut redundant type noise when the type is obvious from context; avoid it when it would obscure the type from a reader.
--> Prefer `record` over hand-written immutable POJOs for simple data carriers -- it eliminates boilerplate and guarantees consistent `equals`/`hashCode`/`toString`.
--> Model closed sets of related types (a `Shape`, a `Result` success/failure, an AST node hierarchy) with `sealed` types so the compiler can enforce exhaustiveness in `switch` expressions.
--> Prefer pattern-matching `switch` expressions over long `if/else instanceof` chains when working with a sealed hierarchy -- they're more concise and compiler-checked.
--> Use text blocks for any embedded multi-line literal (SQL, JSON, HTML) instead of string concatenation.
--> Reach for virtual threads when writing highly concurrent, I/O-bound, blocking-style code (e.g. one virtual thread per incoming request) -- not as a blanket replacement for platform threads in CPU-bound workloads.
