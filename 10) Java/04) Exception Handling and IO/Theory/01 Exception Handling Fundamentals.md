# Why Exception Handling Matters

--> Programs fail -- files go missing, network calls time out, users type garbage into input fields, arrays get indexed out of bounds. Exception handling is Java's structured mechanism for detecting these failures, separating "what to do when things go wrong" from "what to do when things go right," and doing so without littering every function with manual error-code checks like C does (`if (result == -1) { ... }`).
--> Java exceptions are OBJECTS -- when something goes wrong, the JVM (or your code) creates an exception object carrying information about the failure (message, type, stack trace) and hands control to whichever code up the call stack is prepared to handle that type of failure. This is fundamentally different from C-style error codes: the failure information travels WITH an object, and control flow jumps automatically instead of requiring the caller to check a return value at every single call site.

# The try / catch / finally Block

--> **`try`** -- wraps code that might throw an exception. If no exception occurs, every statement in the block runs normally and any `catch` blocks are skipped entirely.
--> **`catch`** -- catches and handles a specific exception type (or one of its supertypes) thrown from the `try` block. Multiple `catch` blocks can follow a single `try`, and Java tries them top-to-bottom, using the FIRST one whose type matches.
--> **`finally`** -- runs no matter what: whether the `try` succeeded, an exception was caught, an exception was NOT caught (still propagating), or the try/catch contained a `return`. It's the place to release resources (close files, connections, locks) that must be cleaned up regardless of outcome.

```java
import java.io.FileReader;
import java.io.IOException;

public class BasicTryCatch {
    public static void main(String[] args) {
        FileReader reader = null;
        try {
            reader = new FileReader("does-not-exist.txt");   // throws FileNotFoundException (a subclass of IOException)
            int firstChar = reader.read();
            System.out.println("First char: " + firstChar);
        } catch (IOException e) {
            System.out.println("Could not read file: " + e.getMessage());
        } finally {
            System.out.println("Cleanup runs regardless of success or failure.");
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    System.out.println("Failed to close reader: " + e.getMessage());
                }
            }
        }
    }
}
```

--> **Execution order guarantee** -- if the `try` block throws, the JVM searches its `catch` clauses top to bottom for the first type match, runs that handler, THEN runs `finally`. If nothing throws, `finally` still runs after the `try` block completes. If a `catch` block itself throws, `finally` STILL runs before that new exception propagates further.
--> **`finally` and `return` interaction (gotcha)** -- if both the `try`/`catch` and the `finally` block contain a `return`, the `finally` block's `return` WINS, silently discarding the other one. This is a well-known trap and considered bad practice -- never `return` from inside `finally`.

```java
public class FinallyReturnTrap {
    static int trap() {
        try {
            return 1;
        } finally {
            return 2;   // this silently overrides the try's return 1 -- avoid this pattern
        }
    }

    public static void main(String[] args) {
        System.out.println(trap());   // prints 2, not 1
    }
}
```

# The Exception Class Hierarchy

--> Every throwable in Java descends from `java.lang.Throwable`. Understanding this hierarchy tells you what you CAN catch, what you SHOULD catch, and what the compiler will FORCE you to acknowledge.

```text
Throwable
├── Error                          -- serious JVM-level problems, NOT meant to be caught by application code
│   ├── OutOfMemoryError
│   ├── StackOverflowError
│   └── NoClassDefFoundError
└── Exception                      -- problems application code IS meant to handle
    ├── IOException                -- CHECKED -- file/network/stream failures
    │   └── FileNotFoundException
    ├── SQLException                -- CHECKED -- database failures
    └── RuntimeException            -- UNCHECKED -- usually programmer errors
        ├── NullPointerException
        ├── ArrayIndexOutOfBoundsException
        ├── ClassCastException
        ├── ArithmeticException
        ├── IllegalArgumentException
        ├── IllegalStateException
        └── NumberFormatException
```

--> **`Throwable`** is the root of the entire hierarchy -- both `Error` and `Exception` extend it, and both CAN technically be caught with `catch (Throwable t)`, though catching `Throwable` directly is almost always wrong (see anti-patterns in the next file).
--> **`Error`** represents conditions a reasonable application should not try to catch -- `OutOfMemoryError` means the JVM is out of heap space; `StackOverflowError` means recursion (or mutual recursion) has exceeded the call stack limit. Catching these rarely helps because the JVM itself may be in an unstable state.
--> **`Exception`** is the branch application code is expected to interact with, and it splits into two fundamentally different categories that the Java compiler treats completely differently: **checked** and **unchecked**.

# Checked vs Unchecked Exceptions

| | Checked | Unchecked |
|---|---|---|
| Base class | `Exception` (excluding `RuntimeException`) | `RuntimeException` (and `Error`) |
| Compiler enforcement | MUST be caught or declared with `throws` | No compiler enforcement at all |
| Represents | Recoverable, expected external failures | Programming bugs / logic errors |
| Examples | `IOException`, `SQLException`, `InterruptedException` | `NullPointerException`, `ArithmeticException`, `IllegalArgumentException` |
| Typical response | Handle it, retry, or propagate deliberately | Fix the bug that caused it -- don't just catch and ignore |

--> **Checked exceptions** are exceptions the COMPILER forces you to deal with -- either catch them in a `try/catch`, or declare that your method might throw them via `throws`. The idea (from Java's original design) is that some failures are so foreseeable and recoverable (a file might not exist, a network call might time out) that the compiler should force every caller to consciously acknowledge the possibility.

```java
import java.io.IOException;

public class CheckedExceptionExample {
    // MUST either catch IOException here, or declare `throws IOException` -- the compiler will not compile otherwise
    static void readSomething() throws IOException {
        throw new IOException("simulated read failure");
    }

    public static void main(String[] args) {
        try {
            readSomething();
        } catch (IOException e) {
            System.out.println("Handled checked exception: " + e.getMessage());
        }
    }
}
```

--> **Unchecked exceptions** (all subclasses of `RuntimeException`) do NOT require a `try/catch` or a `throws` declaration -- the compiler is silent about them entirely. They typically represent bugs (dereferencing null, dividing by zero, bad array index) rather than expected external failures, so forcing every method up the call stack to declare them would be noise -- a `NullPointerException` can theoretically come from almost any line of code, so requiring `throws NullPointerException` everywhere would be meaningless.

```java
public class UncheckedExceptionExample {
    public static void main(String[] args) {
        int[] numbers = {1, 2, 3};
        System.out.println(numbers[5]);   // ArrayIndexOutOfBoundsException -- unchecked, compiles fine, fails at runtime
    }
}
```

--> **The debate this design causes** -- checked exceptions were controversial even within the Java community. Critics argue they encourage bad patterns (empty catch blocks just to satisfy the compiler, or `throws Exception` used as an escape hatch). Modern Java libraries (including much of the Streams API in Java 8+) lean unchecked for this reason. Still, understanding checked exceptions is mandatory because huge swaths of the standard library (`java.io`, JDBC, `java.nio.file`) use them heavily.

# `throw` vs `throws`

--> These look similar but do completely different jobs, and mixing them up is a common beginner mistake.

| Keyword | Purpose | Where it appears | Example |
|---|---|---|---|
| `throw` | Actually THROWS one specific exception instance, right now | Inside a method body, as a statement | `throw new IllegalArgumentException("bad input");` |
| `throws` | DECLARES that a method might throw certain checked exception types | In a method SIGNATURE | `void readFile() throws IOException { ... }` |

```java
import java.io.IOException;

public class ThrowVsThrows {

    // `throws` -- declares to callers "you must handle IOException if you call me"
    static void validateAge(int age) {
        if (age < 0) {
            // `throw` -- actually creates and throws the exception object, right here, right now
            throw new IllegalArgumentException("Age cannot be negative: " + age);
        }
        System.out.println("Age " + age + " is valid.");
    }

    static void openConfigFile() throws IOException {
        throw new IOException("config.txt missing");   // throw a checked exception, declared via throws
    }

    public static void main(String[] args) {
        validateAge(25);   // fine

        try {
            validateAge(-5);
        } catch (IllegalArgumentException e) {
            System.out.println("Caught: " + e.getMessage());
        }

        try {
            openConfigFile();
        } catch (IOException e) {
            System.out.println("Caught: " + e.getMessage());
        }
    }
}
```

--> **Memory aid** -- `throw` is a VERB, an action taken on ONE object, inside a method body. `throws` is an ADJECTIVE-like declaration, a list of exception TYPES, in a method signature. A method can have `throws A, B, C` (multiple types) but a single `throw` statement only ever throws one object at a time.

# Catching Multiple Exception Types

--> A single `try` can have several `catch` blocks, tried top-to-bottom. Java requires **more specific exception types to come before more general ones** -- putting `catch (Exception e)` before `catch (IOException e)` is a COMPILE ERROR, because the general catch would make the specific one unreachable.

```java
public class MultipleCatchBlocks {
    public static void main(String[] args) {
        int[] data = {10, 20, 30};
        String input = "abc";   // deliberately invalid for parsing

        try {
            int index = Integer.parseInt(input);   // throws NumberFormatException
            System.out.println(data[index]);
        } catch (NumberFormatException e) {
            System.out.println("Invalid number format: " + e.getMessage());
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println("Index out of range: " + e.getMessage());
        } catch (Exception e) {
            // general fallback -- MUST be last, or it won't compile
            System.out.println("Some other exception: " + e);
        }
    }
}
```

# What Information an Exception Carries

--> Every `Throwable` carries useful diagnostic information beyond just its type.

```java
public class ExceptionInspection {
    public static void main(String[] args) {
        try {
            int[] arr = new int[3];
            arr[10] = 1;
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println("getMessage(): " + e.getMessage());          // human-readable detail
            System.out.println("toString():   " + e.toString());            // class name + message
            System.out.println("getClass():   " + e.getClass().getName());  // exact runtime type
            System.out.println("--- stack trace ---");
            e.printStackTrace();                                            // full call chain where it happened
        }
    }
}
```

--> **The stack trace** is the ordered list of method calls active at the moment the exception was thrown (most recent call first) -- it is usually the single most valuable piece of debugging information Java gives you, showing exactly which line in which method triggered the failure and the full chain of callers that led there.

# Common Gotchas

--> **Catching `Exception` (or worse, `Throwable`) broadly** -- swallows bugs you didn't anticipate along with the ones you meant to catch, hiding real problems (like `NullPointerException` from a genuine bug) behind a generic handler meant for something else entirely.
--> **Empty catch blocks** -- `catch (IOException e) {}` silently discards the failure with zero trace it ever happened. This is one of the most notorious anti-patterns in Java code and makes debugging production issues extremely painful -- at minimum, log the exception.
--> **Using exceptions for normal control flow** -- exceptions involve capturing a stack trace, which has real performance cost. Using `try/catch` to implement something like "loop until `ArrayIndexOutOfBoundsException`" instead of just checking `i < arr.length` is both slower and confuses "expected control flow" with "exceptional failure."
--> **Forgetting that `finally` always runs** -- including when a `catch` block itself throws a new exception, or when the `try` block has a `return`/`break`/`continue`. Resource-cleanup logic placed anywhere else can be skipped; `finally` (or better, try-with-resources, covered in the next file) cannot.
--> **Order of `catch` blocks** -- unreachable catch blocks (general before specific) are a compile error for related types, but with UNRELATED exception types Java won't complain, yet the more likely/specific case should still usually be listed first for readability and correct handling.

# Best Practices Summary

--> Catch the MOST SPECIFIC exception type you can meaningfully handle -- avoid blanket `catch (Exception e)` unless you are at a true top-level boundary (e.g. a web request handler that must never crash the whole server).
--> Never leave a `catch` block empty -- at minimum log the exception (message + stack trace) so failures are visible.
--> Reserve checked exceptions for genuinely recoverable, expected conditions; reserve unchecked exceptions (or custom ones extending `RuntimeException`) for programmer errors and invariant violations.
--> Keep `try` blocks as SMALL as possible -- wrapping an entire method body in one giant `try` makes it unclear which line actually threw, and increases the chance of accidentally catching an exception you didn't intend to handle.
--> Prefer try-with-resources (next file) over manual `finally`-based cleanup wherever the resource implements `AutoCloseable`.
