# Why Build Custom Exceptions

--> The standard library's exceptions (`IllegalArgumentException`, `IOException`, etc.) are GENERIC -- they describe the mechanism of failure ("a null was passed," "a read failed") but not the DOMAIN meaning of that failure. A banking application that throws `IllegalStateException("balance too low")` forces every caller to inspect the message STRING to know what happened. A custom `InsufficientFundsException` makes the failure a first-class, catchable, self-documenting TYPE -- callers can `catch (InsufficientFundsException e)` specifically, and the exception can carry structured data (the shortfall amount, the account ID) instead of just a sentence.

# Creating a Custom Exception

--> Extend `Exception` for a CHECKED custom exception (callers are compiler-forced to handle it), or extend `RuntimeException` for an UNCHECKED one (callers are not forced, appropriate when the failure represents a programmer/logic error rather than an expected recoverable condition).

```java
// Checked custom exception -- callers must catch or declare it
class InsufficientFundsException extends Exception {
    private final double shortfall;

    public InsufficientFundsException(String message, double shortfall) {
        super(message);              // always forward the message to the parent constructor
        this.shortfall = shortfall;
    }

    public double getShortfall() {
        return shortfall;
    }
}

// Unchecked custom exception -- represents a programming/logic error, not forced by the compiler
class InvalidAccountStateException extends RuntimeException {
    public InvalidAccountStateException(String message) {
        super(message);
    }
}

public class BankAccount {
    private double balance;

    public BankAccount(double initialBalance) {
        this.balance = initialBalance;
    }

    public void withdraw(double amount) throws InsufficientFundsException {
        if (amount > balance) {
            double shortfall = amount - balance;
            throw new InsufficientFundsException(
                "Cannot withdraw " + amount + ", balance is only " + balance, shortfall);
        }
        balance -= amount;
    }

    public static void main(String[] args) {
        BankAccount account = new BankAccount(100.0);
        try {
            account.withdraw(250.0);
        } catch (InsufficientFundsException e) {
            System.out.println(e.getMessage());
            System.out.println("Shortfall was: " + e.getShortfall());
        }
    }
}
```

--> **Constructor conventions** -- it's standard practice to provide the same constructor overloads `Exception`/`Throwable` itself provides: no-arg, `(String message)`, `(String message, Throwable cause)`, and `(Throwable cause)`. This keeps your custom exception interoperable with every pattern the rest of the ecosystem expects (logging frameworks, chaining, etc.).

```java
class OrderProcessingException extends Exception {
    public OrderProcessingException() { super(); }
    public OrderProcessingException(String message) { super(message); }
    public OrderProcessingException(String message, Throwable cause) { super(message, cause); }
    public OrderProcessingException(Throwable cause) { super(cause); }
}
```

# Exception Chaining (Preserving the Root Cause)

--> When you catch one exception and want to throw a DIFFERENT, more domain-appropriate one, don't discard the original -- pass it as the **cause**. This preserves the full failure chain in the stack trace, which is often essential for real debugging (the low-level `SQLException` that triggered a higher-level `OrderProcessingException`, for instance).

```java
import java.sql.SQLException;

class OrderPersistenceException extends RuntimeException {
    public OrderPersistenceException(String message, Throwable cause) {
        super(message, cause);   // `cause` is preserved -- getCause() will return the original exception
    }
}

public class ExceptionChainingDemo {
    static void saveOrderToDatabase() throws SQLException {
        throw new SQLException("connection timed out");   // simulated low-level failure
    }

    static void processOrder() {
        try {
            saveOrderToDatabase();
        } catch (SQLException e) {
            // Wrap the low-level exception in a domain-specific one, but KEEP the original as the cause
            throw new OrderPersistenceException("Failed to persist order #12345", e);
        }
    }

    public static void main(String[] args) {
        try {
            processOrder();
        } catch (OrderPersistenceException e) {
            System.out.println("Top-level message: " + e.getMessage());
            System.out.println("Root cause: " + e.getCause());
            // printStackTrace() on `e` prints BOTH the wrapper exception's trace
            // AND, beneath a "Caused by:" line, the original SQLException's trace.
            e.printStackTrace();
        }
    }
}
```

--> **Why this matters** -- without chaining (`throw new OrderPersistenceException("failed")` with no cause), you lose the original stack trace entirely. In production, that's often the difference between diagnosing an issue in five minutes and spending hours reproducing it.

# try-with-resources

--> Introduced in Java 7, try-with-resources automatically closes any resource that implements `AutoCloseable` (which `Closeable` extends), eliminating the need for manual `finally`-block cleanup. Resources are closed in REVERSE order of declaration, and closing happens even if the `try` block throws.

```java
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class TryWithResourcesDemo {
    public static void main(String[] args) {
        // Old style -- verbose, easy to get wrong, especially with multiple resources
        BufferedReader oldReader = null;
        try {
            oldReader = new BufferedReader(new FileReader("data.txt"));
            System.out.println(oldReader.readLine());
        } catch (IOException e) {
            System.out.println("Old-style error: " + e.getMessage());
        } finally {
            if (oldReader != null) {
                try { oldReader.close(); } catch (IOException ignored) { }
            }
        }

        // Modern style -- resource declared in the try() parentheses, closed automatically
        try (BufferedReader reader = new BufferedReader(new FileReader("data.txt"))) {
            System.out.println(reader.readLine());
        } catch (IOException e) {
            System.out.println("New-style error: " + e.getMessage());
        }
        // reader.close() is called automatically here, no finally block needed

        // Multiple resources -- separated by semicolons, closed in REVERSE declaration order
        try (var in = new FileReader("source.txt");
             var out = new java.io.FileWriter("dest.txt")) {
            int c;
            while ((c = in.read()) != -1) {
                out.write(c);
            }
        } catch (IOException e) {
            System.out.println("Copy failed: " + e.getMessage());
        }
        // out.close() runs first, then in.close() -- reverse of declaration order
    }
}
```

--> **Any class can participate** -- simply implement `AutoCloseable` (one method: `void close() throws Exception`) and it becomes usable inside `try (...)`.

```java
class ManagedResource implements AutoCloseable {
    private final String name;
    ManagedResource(String name) {
        this.name = name;
        System.out.println("Opening " + name);
    }
    void use() {
        System.out.println("Using " + name);
    }
    @Override
    public void close() {
        System.out.println("Closing " + name);
    }
}

public class CustomAutoCloseableDemo {
    public static void main(String[] args) {
        try (ManagedResource a = new ManagedResource("A");
             ManagedResource b = new ManagedResource("B")) {
            a.use();
            b.use();
        }
        // Output order: Opening A, Opening B, Using A, Using B, Closing B, Closing A
    }
}
```

--> **Suppressed exceptions** -- if BOTH the `try` block AND the automatic `close()` call throw, the exception from `try` is the one propagated, and the `close()` exception is attached as a "suppressed" exception, retrievable via `getSuppressed()` -- no information is lost, unlike the old manual `finally` pattern where a `close()` exception could silently overwrite the original one.

# Multi-Catch (`catch (TypeA | TypeB e)`)

--> Introduced in Java 7 alongside try-with-resources, multi-catch lets one `catch` block handle SEVERAL unrelated exception types identically, avoiding duplicated handler code.

```java
public class MultiCatchDemo {
    public static void main(String[] args) {
        String[] inputs = {"abc", null};
        for (String input : inputs) {
            try {
                int value = Integer.parseInt(input);   // NumberFormatException if input is "abc"
                System.out.println(100 / value);        // ArithmeticException if value is 0; NPE handled by parseInt itself
            } catch (NumberFormatException | ArithmeticException e) {
                // ONE handler for two unrelated exception types
                System.out.println("Bad input '" + input + "': " + e.getClass().getSimpleName());
            } catch (NullPointerException e) {
                System.out.println("Input was null.");
            }
        }
    }
}
```

--> **Restriction** -- the types in a multi-catch must not be related by inheritance (you can't write `IOException | FileNotFoundException` since the latter is already a subtype of the former -- it's redundant and a compile error). The caught variable (`e` above) is also implicitly `final` and typed as the closest common supertype, so you can't reassign it or call subtype-specific methods without an explicit cast.

# Common Anti-Patterns to Avoid

--> **Swallowing exceptions silently**

```java
try {
    riskyOperation();
} catch (Exception e) {
    // BAD -- failure vanishes with no trace, no log, nothing
}
```

--> **Catching `Exception` or `Throwable` just to "make the error go away"** -- masks bugs (including `NullPointerException` from genuine defects) behind handling meant for something narrower, and makes root-causing production incidents far harder.

--> **Using exceptions for expected, common control flow** -- e.g. using a `NumberFormatException` to detect whether a string is numeric, when a purpose-built check (or `Character.isDigit` loop, or a regex) is cheaper and clearer.

```java
// BAD -- exceptions are relatively expensive (they capture a stack trace) and this abuses them for routine logic
static boolean isNumericBad(String s) {
    try {
        Integer.parseInt(s);
        return true;
    } catch (NumberFormatException e) {
        return false;
    }
}
```

--> **Losing the original cause when wrapping** -- `throw new MyException("something failed")` inside a `catch` block, without passing the caught exception as the cause, discards the original stack trace permanently.
--> **Declaring `throws Exception` on everything** -- this is a lazy way to satisfy the compiler for checked exceptions, but it defeats the entire PURPOSE of checked exceptions: callers can no longer tell from the signature which specific failures are possible, and are forced into equally broad catch blocks.
--> **Returning `null` from a `catch` block without documenting it** -- callers often assume a non-throwing method returns a valid, non-null object; silently returning `null` on failure just relocates the bug to a `NullPointerException` somewhere else, later, further from the actual cause.
--> **Overusing checked exceptions for things that are really programmer errors** -- e.g. a custom checked exception for "invalid array index passed to my method" adds ceremony for something that's really a bug, not a recoverable external condition; `IllegalArgumentException` (unchecked) is more appropriate.

# Best Practices Summary

--> Name custom exceptions ending in `Exception` (convention, not enforced) and make them clearly describe the domain failure (`InsufficientFundsException`, not `Error1Exception`).
--> Always provide the standard constructor overloads, and always forward `message`/`cause` to `super(...)`.
--> ALWAYS preserve the original exception as the `cause` when wrapping/rethrowing a different type.
--> Prefer try-with-resources over manual `finally` cleanup for anything implementing `AutoCloseable`.
--> Use multi-catch to deduplicate identical handling logic across unrelated exception types, rather than copy-pasting the same catch body.
--> Choose checked vs unchecked deliberately: checked for expected/recoverable external conditions the caller should be forced to consider, unchecked for programmer errors and invariant violations.
--> Never leave a catch block silently empty -- log at minimum, even in a prototype.
