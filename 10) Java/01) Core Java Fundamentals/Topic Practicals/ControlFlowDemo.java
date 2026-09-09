/*
 * ControlFlowDemo.java
 *
 * Demonstrates:
 *     1. if / else if / else chains, including nesting
 *     2. Classic switch with an intentional fall-through bug, then the corrected version
 *     3. Modern switch expressions (Java 14+) with arrow syntax, multi-label cases, and yield
 *     4. for, while, and do-while loops
 *     5. Enhanced for-each loop over an array
 *     6. break and continue inside a loop
 *     7. Labeled loops with a labeled break to escape nested loops
 *     8. Pattern matching for switch (Java 21+) -- clearly marked as requiring Java 21+
 *
 * Covers Theory chapter:
 *     10) Java/01) Core Java Fundamentals/Theory/03 Control Flow Statements.md
 *
 * Compile: javac 03_control_flow_demo.java
 * Run:     java ControlFlowDemo
 *
 * Note: sections 3 and 8 use switch expressions / pattern matching for switch.
 *       Switch expressions with arrow syntax and yield require Java 14+.
 *       Pattern matching for switch (case Integer i ->, guarded "when" patterns,
 *       "case null") requires Java 21+. Everything else in this file is
 *       compatible with much older Java versions.
 */
public class ControlFlowDemo {

    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }

    public static void main(String[] args) {
        demoIfElse();
        demoClassicSwitchFallThroughBug();
        demoClassicSwitchCorrected();
        demoModernSwitchExpression();
        demoForLoop();
        demoWhileLoop();
        demoDoWhileLoop();
        demoForEach();
        demoBreakAndContinue();
        demoLabeledLoop();
        demoPatternMatchingSwitch();
        System.out.println("\nAll control flow demos completed.");
    }

    // ---------------------------------------------------------------------------
    // 1) if / else if / else -- including nesting
    // ---------------------------------------------------------------------------
    private static void demoIfElse() {
        printSection("1) if / else if / else chains (with nesting)");

        int score = 72;
        // Expected output: "Grade: C" -- 72 fails ">= 90" and ">= 80" but passes ">= 70"
        if (score >= 90) {
            System.out.println("Grade: A");
        } else if (score >= 80) {
            System.out.println("Grade: B");
        } else if (score >= 70) {
            System.out.println("Grade: C");
        } else {
            System.out.println("Grade: F");
        }

        // Nested if -- the inner condition only makes sense given the outer one is true
        int age = 25;
        boolean hasLicense = true;
        // Expected output: "Can drive."
        if (age >= 18) {
            if (hasLicense) {
                System.out.println("Can drive.");
            } else {
                System.out.println("Old enough, but needs a license.");
            }
        } else {
            System.out.println("Too young to drive.");
        }
    }

    // ---------------------------------------------------------------------------
    // 2) Classic switch -- intentional fall-through bug demonstration
    // ---------------------------------------------------------------------------
    private static void demoClassicSwitchFallThroughBug() {
        printSection("2) Classic switch -- ACCIDENTAL fall-through bug (missing break)");

        int day = 2;
        // BUG: no break statements below -- execution falls through every case
        // it enters instead of stopping after the matching one.
        // Expected (buggy) output for day = 2:
        //   Tuesday
        //   Wednesday
        //   Some other day
        // Only "Tuesday" was actually intended, but control fell through
        // case 2 -> case 3 -> default because none of them had a break.
        switch (day) {
            case 1:
                System.out.println("Monday");
            case 2:
                System.out.println("Tuesday");
            case 3:
                System.out.println("Wednesday");
            default:
                System.out.println("Some other day");
        }
    }

    private static void demoClassicSwitchCorrected() {
        printSection("2b) Classic switch -- CORRECTED with explicit break on every case");

        int day = 2;
        // Expected output for day = 2:  Tuesday   (and nothing else)
        switch (day) {
            case 1:
                System.out.println("Monday");
                break;
            case 2:
                System.out.println("Tuesday");
                break;
            case 3:
                System.out.println("Wednesday");
                break;
            default:
                System.out.println("Some other day");
                break; // optional on the last branch, but kept for consistency/safety
        }

        // Intentional fall-through is still a legitimate pattern -- grouping several
        // labels to share one body, using comma-free stacked case labels.
        int month = 4; // April
        int daysInMonth;
        switch (month) {
            case 1: case 3: case 5: case 7: case 8: case 10: case 12:
                daysInMonth = 31;
                break;
            case 4: case 6: case 9: case 11:
                daysInMonth = 30;
                break;
            case 2:
                daysInMonth = 28; // ignoring leap years for simplicity
                break;
            default:
                daysInMonth = -1;
                break;
        }
        System.out.println("April has " + daysInMonth + " days."); // Expected: 30
    }

    // ---------------------------------------------------------------------------
    // 3) Modern switch expression (Java 14+) -- arrow syntax, multi-label, yield
    // ---------------------------------------------------------------------------
    private static void demoModernSwitchExpression() {
        printSection("3) Modern switch expression -- arrow syntax, multi-label case, yield");

        int day = 6;
        // Expected output: "Weekend" -- multi-label case "6, 7" matches day = 6.
        // No break needed and no fall-through risk: each arrow branch is self-contained.
        String name = switch (day) {
            case 1 -> "Monday";
            case 2 -> "Tuesday";
            case 3 -> "Wednesday";
            case 4 -> "Thursday";
            case 5 -> "Friday";
            case 6, 7 -> "Weekend";
            default -> "Unknown";
        };
        System.out.println("Day name: " + name);

        int score = 72;
        // Expected output:
        //   Borderline C range
        //   Grade: C
        // The "case 7" branch is a block, so it uses yield to produce the value
        // instead of an inline expression.
        String grade = switch (score / 10) {
            case 10, 9 -> "A";
            case 8 -> "B";
            case 7 -> {
                System.out.println("Borderline C range");
                yield "C";
            }
            default -> "F";
        };
        System.out.println("Grade: " + grade);
    }

    // ---------------------------------------------------------------------------
    // 4) for loop
    // ---------------------------------------------------------------------------
    private static void demoForLoop() {
        printSection("4) for loop -- known iteration count");

        // Expected output: Iteration 0 .. Iteration 4
        for (int i = 0; i < 5; i++) {
            System.out.println("Iteration " + i);
        }
    }

    // ---------------------------------------------------------------------------
    // 5) while loop
    // ---------------------------------------------------------------------------
    private static void demoWhileLoop() {
        printSection("5) while loop -- continuation depends on evolving state, not a counter");

        int n = 27;
        int steps = 0;
        // Collatz conjecture step count -- number of iterations is not known up front.
        while (n != 1) {
            n = (n % 2 == 0) ? n / 2 : 3 * n + 1;
            steps++;
        }
        System.out.println("Collatz steps for 27: " + steps); // Expected: 111
    }

    // ---------------------------------------------------------------------------
    // 6) do-while loop
    // ---------------------------------------------------------------------------
    private static void demoDoWhileLoop() {
        printSection("6) do-while loop -- body guaranteed to run at least once");

        int attempts = 0;
        int input;
        // Simulates repeatedly prompting until a positive number is "entered" --
        // the body must run at least once to produce a value to check at all.
        do {
            attempts++;
            input = attempts < 3 ? -1 : 42; // stand-in for a real input read
            System.out.println("Attempt " + attempts + ": got " + input);
        } while (input <= 0);
        // Expected output:
        //   Attempt 1: got -1
        //   Attempt 2: got -1
        //   Attempt 3: got 42
        System.out.println("Accepted input: " + input);
    }

    // ---------------------------------------------------------------------------
    // 7) Enhanced for-each loop over an array
    // ---------------------------------------------------------------------------
    private static void demoForEach() {
        printSection("7) Enhanced for-each loop over an array");

        int[] numbers = {10, 20, 30, 40};
        // Expected output: 10, 20, 30, 40 (one per line)
        for (int n : numbers) {
            System.out.println(n);
        }

        java.util.List<String> names = java.util.List.of("Ann", "Bo", "Cid");
        // Expected output: Ann, Bo, Cid (one per line)
        for (String name : names) {
            System.out.println(name);
        }
    }

    // ---------------------------------------------------------------------------
    // 8) break and continue
    // ---------------------------------------------------------------------------
    private static void demoBreakAndContinue() {
        printSection("8) break and continue inside a loop");

        // Expected output: 1, 3, 5 (odd numbers below 7; loop stops entirely at 7)
        for (int i = 1; i <= 10; i++) {
            if (i == 7) {
                break; // stop the loop entirely once i reaches 7
            }
            if (i % 2 == 0) {
                continue; // skip printing even numbers, but keep looping
            }
            System.out.println(i);
        }
    }

    // ---------------------------------------------------------------------------
    // 9) Labeled loop with labeled break
    // ---------------------------------------------------------------------------
    private static void demoLabeledLoop() {
        printSection("9) Labeled loop -- labeled break escapes both nested loops");

        // Without a label, a plain "break" here would only exit the INNER loop,
        // requiring an extra flag variable to also stop the outer loop.
        // "break outer" exits both loops immediately, in one step.
        // Expected output: 0,0  0,1  0,2  1,0  -- then the loop is fully exited
        // as soon as i == 1 && j == 1 is reached.
        outer:
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (i == 1 && j == 1) {
                    break outer;
                }
                System.out.println(i + "," + j);
            }
        }
    }

    // ---------------------------------------------------------------------------
    // 10) Pattern matching for switch (Java 21+)
    // ---------------------------------------------------------------------------
    private static void demoPatternMatchingSwitch() {
        printSection("10) Pattern matching for switch (REQUIRES JAVA 21+)");

        // The describe() helper below uses type patterns (case Integer i ->),
        // a guarded pattern (case Integer i when i < 0 ->), and case null --
        // all part of pattern matching for switch, finalized in Java 21.
        // If compiling on an older JDK, this method and its calls should be
        // removed or the whole file should be compiled with --release 21 or later.
        System.out.println(describe(-5));      // Expected: "negative integer: -5"
        System.out.println(describe(42));       // Expected: "integer: 42"
        System.out.println(describe("hello"));  // Expected: "string of length 5"
        System.out.println(describe(null));     // Expected: "it's null"
        System.out.println(describe(3.14));     // Expected: "something else: 3.14"
    }

    // Requires Java 21+ (pattern matching for switch)
    private static String describe(Object obj) {
        return switch (obj) {
            case Integer i when i < 0 -> "negative integer: " + i;
            case Integer i            -> "integer: " + i;
            case String s             -> "string of length " + s.length();
            case null                 -> "it's null";
            default                   -> "something else: " + obj;
        };
    }
}
