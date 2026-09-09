/*
 * ExceptionHandlingFundamentals.java
 *
 * Demonstrates:
 *   1. Basic try/catch/finally control flow and execution order
 *   2. The finally-return trap
 *   3. Checked vs unchecked exceptions (compiler enforcement difference)
 *   4. throw vs throws
 *   5. Multiple / ordered catch blocks
 *   6. Inspecting exception information (message, class, stack trace)
 *   7. The exception hierarchy in action (Error vs Exception vs RuntimeException)
 *
 * Covers Theory chapter:
 *   10) Java/04) Exception Handling and IO/Theory/01 Exception Handling Fundamentals.md
 *
 * Compile: javac 01_exception_handling_fundamentals.java
 * Run:     java ExceptionHandlingFundamentals
 */

import java.io.IOException;

public class ExceptionHandlingFundamentals {

    public static void main(String[] args) {
        printSection("1) Basic try/catch/finally execution order");
        demoBasicTryCatchFinally();

        printSection("2) The finally-return trap");
        demoFinallyReturnTrap();

        printSection("3) Checked vs unchecked exceptions");
        demoCheckedVsUnchecked();

        printSection("4) throw vs throws");
        demoThrowVsThrows();

        printSection("5) Multiple catch blocks, tried top to bottom");
        demoMultipleCatchBlocks();

        printSection("6) Inspecting exception information");
        demoExceptionInspection();

        printSection("7) Exception hierarchy -- catching by common supertype");
        demoExceptionHierarchy();

        System.out.println("\nAll exception fundamentals demos completed.");
    }

    // -------------------------------------------------------------------
    // 1) Basic try/catch/finally
    // -------------------------------------------------------------------
    static void demoBasicTryCatchFinally() {
        try {
            System.out.println("try: about to divide by zero");
            int result = 10 / 0;                       // throws ArithmeticException
            System.out.println("unreachable: " + result);
        } catch (ArithmeticException e) {
            System.out.println("catch: caught -> " + e.getMessage());
        } finally {
            System.out.println("finally: always runs, success or failure");
        }

        // finally also runs when NO exception occurs
        try {
            System.out.println("try: no exception this time");
        } finally {
            System.out.println("finally: still runs even without an exception");
        }
    }

    // -------------------------------------------------------------------
    // 2) finally-return trap -- demonstrates why returning from finally is a bad idea
    // -------------------------------------------------------------------
    static int trapReturn() {
        try {
            return 1;
        } finally {
            return 2;   // silently overrides the try's return value -- avoid this pattern in real code
        }
    }

    static void demoFinallyReturnTrap() {
        int value = trapReturn();
        System.out.println("trapReturn() returned: " + value + " (the try's 'return 1' was silently discarded)");
    }

    // -------------------------------------------------------------------
    // 3) Checked vs unchecked exceptions
    // -------------------------------------------------------------------
    // Checked: caller is FORCED by the compiler to handle or declare this
    static void readConfigFile() throws IOException {
        throw new IOException("simulated missing config file");
    }

    // Unchecked: compiler does not force anything -- represents a programmer/logic error
    static void accessInvalidIndex() {
        int[] arr = {1, 2, 3};
        System.out.println(arr[10]);   // ArrayIndexOutOfBoundsException, unchecked
    }

    static void demoCheckedVsUnchecked() {
        // Checked exception -- MUST be caught (or main would need to declare throws IOException)
        try {
            readConfigFile();
        } catch (IOException e) {
            System.out.println("Handled checked exception: " + e.getMessage());
        }

        // Unchecked exception -- no compiler enforcement, but still must be caught here to keep the program running
        try {
            accessInvalidIndex();
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println("Handled unchecked exception: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------
    // 4) throw vs throws
    // -------------------------------------------------------------------
    // `throws` in the signature -- declares the possibility of a checked exception
    static void validateAge(int age) {
        if (age < 0) {
            // `throw` -- actually raises one specific exception instance right now
            throw new IllegalArgumentException("Age cannot be negative: " + age);
        }
        System.out.println("Age " + age + " accepted.");
    }

    static void openResource(String name) throws IOException {
        if (name == null || name.isEmpty()) {
            throw new IOException("Resource name must not be empty");
        }
        System.out.println("Opened resource: " + name);
    }

    static void demoThrowVsThrows() {
        validateAge(25);
        try {
            validateAge(-3);
        } catch (IllegalArgumentException e) {
            System.out.println("Caught from throw: " + e.getMessage());
        }

        try {
            openResource("");
        } catch (IOException e) {
            System.out.println("Caught declared-via-throws exception: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------
    // 5) Multiple catch blocks -- specific before general
    // -------------------------------------------------------------------
    static void demoMultipleCatchBlocks() {
        String[] inputs = {"abc", "999"};
        int[] data = {10, 20, 30};

        for (String input : inputs) {
            try {
                int index = Integer.parseInt(input);
                System.out.println("data[" + index + "] = " + data[index]);
            } catch (NumberFormatException e) {
                System.out.println("Invalid number format for input '" + input + "': " + e.getMessage());
            } catch (ArrayIndexOutOfBoundsException e) {
                System.out.println("Index out of range for input '" + input + "': " + e.getMessage());
            } catch (Exception e) {
                // must be last -- broadest catch-all
                System.out.println("Unexpected exception: " + e);
            }
        }
    }

    // -------------------------------------------------------------------
    // 6) Inspecting exception information
    // -------------------------------------------------------------------
    static void demoExceptionInspection() {
        try {
            Object str = "not a number";
            Integer castAttempt = (Integer) str;   // ClassCastException
            System.out.println(castAttempt);
        } catch (ClassCastException e) {
            System.out.println("getMessage(): " + e.getMessage());
            System.out.println("toString():   " + e.toString());
            System.out.println("getClass():   " + e.getClass().getName());
            System.out.println("Stack trace element count: " + e.getStackTrace().length);
        }
    }

    // -------------------------------------------------------------------
    // 7) Exception hierarchy -- a supertype catch handles any subtype
    // -------------------------------------------------------------------
    static void demoExceptionHierarchy() {
        // RuntimeException is a supertype of ArithmeticException, NullPointerException, etc.
        RuntimeException[] failures = {
                new ArithmeticException("div by zero"),
                new NullPointerException("null reference"),
                new IllegalStateException("bad state")
        };

        for (RuntimeException failure : failures) {
            try {
                throw failure;
            } catch (RuntimeException e) {
                // ONE catch clause handles every RuntimeException subtype thrown above
                System.out.println("Caught via common supertype RuntimeException: "
                        + e.getClass().getSimpleName() + " -> " + e.getMessage());
            }
        }
    }

    static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }
}
