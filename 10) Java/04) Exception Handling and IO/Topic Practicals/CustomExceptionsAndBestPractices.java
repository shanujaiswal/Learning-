/*
 * CustomExceptionsAndBestPractices.java
 *
 * Demonstrates:
 *   1. Creating checked and unchecked custom exceptions
 *   2. Exception chaining (preserving root cause via getCause())
 *   3. try-with-resources with a built-in AutoCloseable and a custom one
 *   4. Multiple resources closed in reverse order
 *   5. Multi-catch (catch (TypeA | TypeB e))
 *   6. Suppressed exceptions from try-with-resources
 *   7. Anti-pattern illustration (commented) vs the fixed version
 *
 * Covers Theory chapter:
 *   10) Java/04) Exception Handling and IO/Theory/02 Custom Exceptions and Best Practices.md
 *
 * Compile: javac 02_custom_exceptions_and_best_practices.java
 * Run:     java CustomExceptionsAndBestPractices
 */

import java.io.IOException;
import java.sql.SQLException;

public class CustomExceptionsAndBestPractices {

    // ---------------------------------------------------------------
    // Custom checked exception -- callers are compiler-forced to handle it
    // ---------------------------------------------------------------
    static class InsufficientFundsException extends Exception {
        private final double shortfall;

        public InsufficientFundsException(String message, double shortfall) {
            super(message);
            this.shortfall = shortfall;
        }

        public double getShortfall() {
            return shortfall;
        }
    }

    // ---------------------------------------------------------------
    // Custom unchecked exception -- represents a programming/logic error
    // ---------------------------------------------------------------
    static class InvalidAccountStateException extends RuntimeException {
        public InvalidAccountStateException(String message) {
            super(message);
        }
    }

    // ---------------------------------------------------------------
    // Custom exception with full constructor overload set (best practice)
    // ---------------------------------------------------------------
    static class OrderProcessingException extends RuntimeException {
        public OrderProcessingException() { super(); }
        public OrderProcessingException(String message) { super(message); }
        public OrderProcessingException(String message, Throwable cause) { super(message, cause); }
        public OrderProcessingException(Throwable cause) { super(cause); }
    }

    // A minimal bank account used to demonstrate the custom checked exception
    static class BankAccount {
        private double balance;

        BankAccount(double initialBalance) {
            this.balance = initialBalance;
        }

        void withdraw(double amount) throws InsufficientFundsException {
            if (amount > balance) {
                throw new InsufficientFundsException(
                        "Cannot withdraw " + amount + ", balance is only " + balance,
                        amount - balance);
            }
            if (amount < 0) {
                throw new InvalidAccountStateException("Withdrawal amount cannot be negative: " + amount);
            }
            balance -= amount;
        }

        double getBalance() {
            return balance;
        }
    }

    // A custom AutoCloseable resource for the try-with-resources demo
    static class ManagedResource implements AutoCloseable {
        private final String name;
        private final boolean failOnClose;

        ManagedResource(String name) {
            this(name, false);
        }

        ManagedResource(String name, boolean failOnClose) {
            this.name = name;
            this.failOnClose = failOnClose;
            System.out.println("  Opening " + name);
        }

        void use() {
            System.out.println("  Using " + name);
        }

        void useAndFail() {
            throw new IllegalStateException("Failure while using " + name);
        }

        @Override
        public void close() throws Exception {
            System.out.println("  Closing " + name);
            if (failOnClose) {
                throw new Exception("Failure while closing " + name);
            }
        }
    }

    public static void main(String[] args) {
        printSection("1) Custom checked exception with extra data");
        demoCustomCheckedException();

        printSection("2) Custom unchecked exception");
        demoCustomUncheckedException();

        printSection("3) Exception chaining -- preserving root cause");
        demoExceptionChaining();

        printSection("4) try-with-resources -- automatic close, reverse order");
        demoTryWithResources();

        printSection("5) try-with-resources -- suppressed exceptions");
        demoSuppressedExceptions();

        printSection("6) Multi-catch -- one handler, several exception types");
        demoMultiCatch();

        printSection("7) Anti-pattern vs fixed version");
        demoAntiPatternVsFixed();

        System.out.println("\nAll custom exception / best practice demos completed.");
    }

    // -------------------------------------------------------------------
    static void demoCustomCheckedException() {
        BankAccount account = new BankAccount(100.0);
        try {
            account.withdraw(250.0);
        } catch (InsufficientFundsException e) {
            System.out.println("Caught: " + e.getMessage());
            System.out.println("Shortfall was: " + e.getShortfall());
        }
        System.out.println("Balance remains: " + account.getBalance());
    }

    // -------------------------------------------------------------------
    static void demoCustomUncheckedException() {
        BankAccount account = new BankAccount(100.0);
        try {
            account.withdraw(-10.0);   // triggers the unchecked InvalidAccountStateException
        } catch (InvalidAccountStateException e) {
            System.out.println("Caught unchecked custom exception: " + e.getMessage());
        } catch (InsufficientFundsException e) {
            // unreachable in this call, but required since withdraw() declares it
            System.out.println("Unexpected: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------
    static void saveOrderToDatabase() throws SQLException {
        throw new SQLException("connection timed out");   // simulated low-level failure
    }

    static void processOrder() {
        try {
            saveOrderToDatabase();
        } catch (SQLException e) {
            // Wrap in a domain-specific exception, but PRESERVE the original as the cause
            throw new OrderProcessingException("Failed to persist order #12345", e);
        }
    }

    static void demoExceptionChaining() {
        try {
            processOrder();
        } catch (OrderProcessingException e) {
            System.out.println("Top-level message: " + e.getMessage());
            System.out.println("Root cause type:    " + e.getCause().getClass().getSimpleName());
            System.out.println("Root cause message: " + e.getCause().getMessage());
        }
    }

    // -------------------------------------------------------------------
    static void demoTryWithResources() {
        System.out.println("Two resources, opened A then B:");
        try (ManagedResource a = new ManagedResource("A");
             ManagedResource b = new ManagedResource("B")) {
            a.use();
            b.use();
        } catch (Exception e) {
            System.out.println("  Unexpected: " + e.getMessage());
        }
        System.out.println("(Notice B closes before A -- reverse of declaration order)");
    }

    // -------------------------------------------------------------------
    static void demoSuppressedExceptions() {
        // The try block throws AND close() throws -- the try's exception wins, close()'s is "suppressed"
        try (ManagedResource r = new ManagedResource("FlakyResource", true)) {
            r.useAndFail();   // throws IllegalStateException
        } catch (Exception e) {
            System.out.println("Primary exception: " + e.getMessage());
            for (Throwable suppressed : e.getSuppressed()) {
                System.out.println("Suppressed exception: " + suppressed.getMessage());
            }
        }
    }

    // -------------------------------------------------------------------
    static void demoMultiCatch() {
        String[] inputs = {"abc", "0"};
        for (String input : inputs) {
            try {
                int value = Integer.parseInt(input);   // may throw NumberFormatException
                System.out.println("100 / " + value + " = " + (100 / value));   // may throw ArithmeticException
            } catch (NumberFormatException | ArithmeticException e) {
                // ONE handler for two unrelated exception types
                System.out.println("Bad input '" + input + "' -> " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
    }

    // -------------------------------------------------------------------
    static void demoAntiPatternVsFixed() {
        System.out.println("Anti-pattern (commented out, shown for reference only):");
        System.out.println("  try { risky(); } catch (Exception e) { /* swallowed, no trace */ }");

        System.out.println("\nFixed version -- specific catch, logged, cause preserved if rethrown:");
        try {
            riskyOperation();
        } catch (IOException e) {
            // At minimum: log something meaningful instead of silently discarding
            System.out.println("  Logged failure: " + e.getMessage() + " (cause preserved if this were rethrown)");
        }
    }

    static void riskyOperation() throws IOException {
        throw new IOException("disk unavailable");
    }

    static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }
}
