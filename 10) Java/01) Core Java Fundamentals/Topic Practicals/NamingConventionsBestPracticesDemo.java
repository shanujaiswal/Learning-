/*
 * NamingConventionsBestPracticesDemo.java
 *
 * Demonstrates:
 *     1. Well-named constants (static final, UPPER_SNAKE_CASE) replacing magic
 *        numbers -- a before/after comparison via two small methods that
 *        produce the identical result, only one of them readable
 *     2. A properly formatted Javadoc comment (@param / @return) on a helper method
 *     3. The == vs .equals() pitfall for String/object comparison, with
 *        printed output showing the surprising case
 *     4. The floating point equality pitfall -- comparing doubles with ==
 *        vs using an epsilon threshold
 *     5. A well-named vs poorly-named variable comparison (poor version shown
 *        only in comments, since Java won't let two variables share a name)
 *
 * Covers Theory chapter:
 *     10) Java/01) Core Java Fundamentals/Theory/07 Java Naming Conventions and Best Practices.md
 *
 * Compile: javac 07_naming_conventions_best_practices_demo.java
 * Run:     java NamingConventionsBestPracticesDemo
 */

public class NamingConventionsBestPracticesDemo {

    // -------------------------------------------------------------------
    // Well-named constants -- UPPER_SNAKE_CASE, declared once, used by name
    // -------------------------------------------------------------------
    private static final int LATE_FEE_THRESHOLD_DAYS = 30;
    private static final double LATE_FEE_PERCENT = 5.0;
    private static final double EPSILON = 1e-9; // tolerance for floating point comparisons

    public static void main(String[] args) {

        printSectionHeader("1) Magic numbers vs named constants");
        demoMagicNumbersVsConstants();

        printSectionHeader("2) Javadoc-documented helper method in action");
        demoJavadocHelperMethod();

        printSectionHeader("3) == vs .equals() pitfall for String/object comparison");
        demoEqualsVsDoubleEquals();

        printSectionHeader("4) Floating point equality pitfall");
        demoFloatingPointEquality();

        printSectionHeader("5) Well-named vs poorly-named variables");
        demoVariableNaming();

        System.out.println("\nAll naming convention and best practice demos completed.");
    }

    // -------------------------------------------------------------------
    // Helper -- prints a section divider so each demo's output is easy to
    // scan in the console, mirroring the section-header style used across
    // this repo's other practical files.
    // -------------------------------------------------------------------
    private static void printSectionHeader(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }

    // ===================================================================
    // 1) Magic numbers vs named constants
    // ===================================================================

    /**
     * Calculates a late fee using unexplained literal numbers scattered
     * directly in the expression -- this is the "BEFORE" version.
     * A reader has no way to know what 30 or 0.05 mean without guessing,
     * and if the business rule changes, every occurrence must be hunted down.
     */
    private static double calculateLateFeeWithMagicNumbers(int daysLate, double balance) {
        if (daysLate <= 30) {                 // <-- what is 30? no idea from reading this alone
            return 0.0;
        }
        return balance * 0.05;                // <-- what is 0.05? a rate? a cap? unclear
    }

    /**
     * Calculates the exact same late fee, but expressed using named
     * constants -- this is the "AFTER" version. The intent (a 30-day grace
     * period, a 5% penalty rate) is now readable directly from the code,
     * and changing the business rule means editing one constant declaration
     * instead of searching the whole codebase for the literal value.
     */
    private static double calculateLateFeeWithNamedConstants(int daysLate, double balance) {
        if (daysLate <= LATE_FEE_THRESHOLD_DAYS) {
            return 0.0;
        }
        return balance * (LATE_FEE_PERCENT / 100.0);
    }

    private static void demoMagicNumbersVsConstants() {
        int daysLate = 45;
        double balance = 1000.0;

        double feeFromMagicNumbers = calculateLateFeeWithMagicNumbers(daysLate, balance);
        double feeFromNamedConstants = calculateLateFeeWithNamedConstants(daysLate, balance);

        System.out.println("Magic-number version fee:    " + feeFromMagicNumbers);
        System.out.println("Named-constant version fee:  " + feeFromNamedConstants);
        // Expected output: both print 50.0 -- identical RESULT, but only the
        // named-constant version tells you WHY that result is correct just
        // by reading the code, without needing a comment to explain 30 or 0.05.
        System.out.println("Same result, but only one version explains itself: "
                + (feeFromMagicNumbers == feeFromNamedConstants));
    }

    // ===================================================================
    // 2) Javadoc comment basics
    // ===================================================================

    /**
     * Calculates the discounted total price of an order.
     *
     * @param subtotal the pre-discount total; must be non-negative
     * @param discountPercent the discount to apply, expressed as 0-100
     * @return the discounted total, rounded to two decimal places
     * @throws IllegalArgumentException if subtotal is negative or discountPercent is outside 0-100
     */
    private static double calculateDiscountedTotal(double subtotal, double discountPercent) {
        if (subtotal < 0 || discountPercent < 0 || discountPercent > 100) {
            throw new IllegalArgumentException("Invalid subtotal or discount percentage");
        }
        return Math.round(subtotal * (1 - discountPercent / 100.0) * 100.0) / 100.0;
    }

    private static void demoJavadocHelperMethod() {
        double total = calculateDiscountedTotal(200.0, 15.0);
        System.out.println("Discounted total for $200.00 at 15% off: " + total);
        // Expected output: 170.0 -- 200 minus 15% (30) is 170.
        System.out.println("(See the Javadoc comment directly above calculateDiscountedTotal"
                + " in the source -- @param/@return document the contract for callers.)");
    }

    // ===================================================================
    // 3) == vs .equals() for String/object comparison
    // ===================================================================

    private static void demoEqualsVsDoubleEquals() {
        // Two Strings built from literals are interned by the JVM and often
        // share the same object -- so == can look like it "works" here.
        String literalOne = "hello";
        String literalTwo = "hello";
        System.out.println("literalOne == literalTwo:        " + (literalOne == literalTwo));
        // Expected output: true -- both literals point at the same interned String object.
        // This is exactly what makes the pitfall dangerous: it looks reliable until it isn't.

        // A String built with `new` is deliberately a DIFFERENT object in memory,
        // even though its CONTENT is identical.
        String constructedCopy = new String("hello");
        System.out.println("literalOne == constructedCopy:   " + (literalOne == constructedCopy));
        // Expected output: false -- different objects in memory, even though the
        // characters are identical. This is the surprising case beginners hit.

        System.out.println("literalOne.equals(constructedCopy): " + literalOne.equals(constructedCopy));
        // Expected output: true -- .equals() compares CONTENT, which is what
        // you almost always actually want when comparing Strings.

        System.out.println("Lesson: always use .equals() for String/object content comparison, "
                + "never == (== is for comparing references, or primitives).");
    }

    // ===================================================================
    // 4) Floating point equality pitfall
    // ===================================================================

    private static void demoFloatingPointEquality() {
        double a = 0.1 + 0.2;
        double b = 0.3;

        System.out.println("0.1 + 0.2 == 0.3 using ==:       " + (a == b));
        // Expected output: false -- binary floating point cannot represent
        // 0.1, 0.2, or 0.3 exactly, so the sum accumulates tiny rounding
        // error and the raw == comparison fails even though the values are
        // "mathematically" equal.
        System.out.println("Actual value of 0.1 + 0.2:       " + a);

        boolean approximatelyEqual = Math.abs(a - b) < EPSILON;
        System.out.println("0.1 + 0.2 == 0.3 using epsilon:  " + approximatelyEqual);
        // Expected output: true -- comparing within a small tolerance (EPSILON)
        // is the correct way to compare floating point values for "equality."

        System.out.println("Lesson: never compare double/float with ==; "
                + "use Math.abs(a - b) < EPSILON instead.");
    }

    // ===================================================================
    // 5) Well-named vs poorly-named variables
    // ===================================================================

    private static void demoVariableNaming() {
        // POOR NAMING (shown only in comments -- Java won't allow a second
        // variable with the same name doing the same job in this scope):
        //
        //     int x = 1000;      // no idea what this represents
        //     int y = 5;         // no idea what this represents
        //     int z = x * y / 100;   // impossible to tell what "z" means either
        //
        // GOOD NAMING -- the real, compiled version below. Every name explains
        // its own purpose; no comment is required to understand what's going on.
        int accountBalance = 1000;
        int interestRatePercent = 5;
        int interestEarned = accountBalance * interestRatePercent / 100;

        System.out.println("accountBalance:      " + accountBalance);
        System.out.println("interestRatePercent: " + interestRatePercent);
        System.out.println("interestEarned:      " + interestEarned);
        // Expected output: 50 -- 5% of 1000. The point isn't the arithmetic,
        // it's that the well-named version is understandable at a glance,
        // while the commented-out poorly-named version (x, y, z) requires
        // guessing or hunting for context elsewhere in the code.
    }
}
