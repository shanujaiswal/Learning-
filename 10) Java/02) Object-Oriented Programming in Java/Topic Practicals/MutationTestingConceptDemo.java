/*
 * MutationTestingConceptDemo.java
 *
 * Demonstrates (by hand, since real PIT/pitest requires a Maven/Gradle plugin that
 * cannot run standalone with plain javac/java):
 *   1. A small class (DiscountCalculator) with a method that achieves 100% line
 *      coverage under a WEAK test -- the test calls every branch but asserts nothing
 *      meaningful, exactly the "coverage lies" scenario the theory file opens with.
 *   2. Three hand-written MUTANTS of that method (Conditionals Boundary, Negate
 *      Conditionals, and a Return-Values-style mutation) -- each a small, separate
 *      method standing in for what PIT would generate automatically.
 *   3. Running the SAME weak test logic against the original method and against each
 *      mutant, showing that the weak test cannot tell them apart (every mutant "survives").
 *   4. A STRONG test (one that asserts on actual expected values) run against the
 *      same original + mutants, showing it correctly "kills" every mutant -- the
 *      hands-on version of what a mutation score measures.
 *
 * Covers Theory chapter:
 *   10) Java/02) Object-Oriented Programming in Java/Theory/13 Mutation Testing for Code Quality.md
 *
 * Compile:  javac 05_MutationTestingConceptDemo.java
 * Run:      java MutationTestingConceptDemo
 */

import java.util.function.BiFunction;

// =============================================================================
// PRODUCTION CODE -- the method under test, and its subtly-undertested test.
// =============================================================================

/**
 * A small discounting rule: prices above the 50%-requested threshold get a flat
 * 60% discount (a deliberately steeper deal for big asks), everything else gets a
 * proportional discount based on the given percentage. The flat rate (60%) is
 * intentionally NOT equal to what the proportional formula would give at the
 * threshold (50%) -- that keeps every boundary mutation below genuinely killable
 * instead of landing on a coincidental equal-result point.
 */
class DiscountCalculator {
    double applyDiscount(double price, double percentage) {
        if (percentage > 50) {
            return price * 0.4;    // flat 60% discount, i.e. customer pays 40% of price
        }
        return price * (1 - percentage / 100);
    }
}

// =============================================================================
// HAND-WRITTEN MUTANTS -- each is ONE small syntactic change from the original,
// exactly mirroring the mutation operators PIT would apply automatically.
// =============================================================================

/** Mutant #1 -- Conditionals Boundary operator: ">" flipped to ">=". */
class DiscountCalculator_Mutant_ConditionalsBoundary {
    double applyDiscount(double price, double percentage) {
        if (percentage >= 50) {                 // MUTATED: was "percentage > 50"
            return price * 0.4;
        }
        return price * (1 - percentage / 100);
    }
}

/** Mutant #2 -- Negate Conditionals operator: ">" fully negated to "<=". */
class DiscountCalculator_Mutant_NegateConditionals {
    double applyDiscount(double price, double percentage) {
        if (percentage <= 50) {                 // MUTATED: was "percentage > 50" (fully negated)
            return price * 0.4;
        }
        return price * (1 - percentage / 100);
    }
}

/** Mutant #3 -- Math operator mutation: the flat-discount multiplier changed from 0.4 to 1.4. */
class DiscountCalculator_Mutant_MathOperator {
    double applyDiscount(double price, double percentage) {
        if (percentage > 50) {
            return price * 1.4;                   // MUTATED: was "price * 0.4"
        }
        return price * (1 - percentage / 100);
    }
}

// =============================================================================
// Main class -- runs both a WEAK and a STRONG test against the original method
// and against each mutant, using a shared functional interface so the exact same
// test LOGIC is reused across all four implementations (original + 3 mutants).
// =============================================================================

public class MutationTestingConceptDemo {

    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(78));
        System.out.println(title);
        System.out.println("=".repeat(78));
    }

    // -------------------------------------------------------------------------
    // The WEAK test -- achieves 100% line/branch coverage of applyDiscount, but
    // asserts NOTHING about the returned value. This mirrors the theory file's
    // "100% coverage, zero assertions" example exactly.
    // -------------------------------------------------------------------------
    // Returns true if the test "passes" (never throws) -- a weak test almost always
    // passes, regardless of whether the underlying logic is correct.
    private static boolean weakTest(BiFunction<Double, Double, Double> applyDiscount) {
        applyDiscount.apply(100.0, 60.0);   // executes the "> 50" branch -- but asserts NOTHING
        applyDiscount.apply(100.0, 20.0);   // executes the other branch -- but asserts NOTHING
        return true;                          // "passes" no matter what applyDiscount actually returns
    }

    // -------------------------------------------------------------------------
    // The STRONG test -- asserts on the ACTUAL expected values, including a
    // boundary case (percentage == 50 exactly), which is what actually catches bugs.
    // -------------------------------------------------------------------------
    // Returns true only if every assertion holds; false (rather than throwing) so we
    // can print a clean kill/survive report for every mutant in one pass.
    private static boolean strongTest(BiFunction<Double, Double, Double> applyDiscount) {
        try {
            check(40.0, applyDiscount.apply(100.0, 60.0), "60% requested -> flat 60% discount (pays 40%)");
            check(80.0, applyDiscount.apply(100.0, 20.0), "20% requested -> proportional discount");
            // At EXACTLY percentage == 50, the proportional formula gives price*(1-0.5) = price*0.5,
            // deliberately DIFFERENT from the flat rate (price*0.4) -- so this single assertion is
            // enough to distinguish ">" from ">=" (Conditionals Boundary) at the exact threshold,
            // unlike a discount rule where the two formulas happen to coincide at the boundary.
            check(50.0, applyDiscount.apply(100.0, 50.0), "exactly 50% (boundary) -> proportional (0.5), NOT flat (0.4)");
            return true;
        } catch (AssertionError e) {
            return false;
        }
    }

    private static void check(double expected, double actual, String description) {
        if (Math.abs(expected - actual) > 1e-9) {
            throw new AssertionError(description + " -- expected " + expected + " but was " + actual);
        }
    }

    // -------------------------------------------------------------------------
    // Demo 1: The weak test achieves "100% coverage" but tells original vs. mutants apart... never.
    // -------------------------------------------------------------------------
    private static void demoWeakTestMissesEveryMutant() {
        printSection("1) WEAK test (100% coverage, zero assertions) -- run against original + 3 mutants");

        DiscountCalculator original = new DiscountCalculator();
        DiscountCalculator_Mutant_ConditionalsBoundary mutant1 = new DiscountCalculator_Mutant_ConditionalsBoundary();
        DiscountCalculator_Mutant_NegateConditionals mutant2 = new DiscountCalculator_Mutant_NegateConditionals();
        DiscountCalculator_Mutant_MathOperator mutant3 = new DiscountCalculator_Mutant_MathOperator();

        boolean originalPassed = weakTest(original::applyDiscount);
        boolean mutant1Passed = weakTest(mutant1::applyDiscount);
        boolean mutant2Passed = weakTest(mutant2::applyDiscount);
        boolean mutant3Passed = weakTest(mutant3::applyDiscount);

        System.out.println("  original                              -> test passed: " + originalPassed);
        System.out.println("  mutant #1 (Conditionals Boundary)      -> test passed: " + mutant1Passed + "  (SURVIVED -- gap!)");
        System.out.println("  mutant #2 (Negate Conditionals)        -> test passed: " + mutant2Passed + "  (SURVIVED -- gap!)");
        System.out.println("  mutant #3 (Math operator 0.4 -> 1.4)   -> test passed: " + mutant3Passed + "  (SURVIVED -- gap!)");

        assert originalPassed && mutant1Passed && mutant2Passed && mutant3Passed
                : "the weak test should pass for ALL of these, proving it has zero discriminating power";

        int weakMutationScore = countKilled(false, false, false);
        System.out.println("  Weak-test mutation score: " + weakMutationScore + "/3 mutants killed (0%) despite 100% coverage.");
    }

    // -------------------------------------------------------------------------
    // Demo 2: The strong test kills every mutant -- proving it actually verifies behavior.
    // -------------------------------------------------------------------------
    private static void demoStrongTestKillsEveryMutant() {
        printSection("2) STRONG test (asserts actual expected values) -- run against original + 3 mutants");

        DiscountCalculator original = new DiscountCalculator();
        DiscountCalculator_Mutant_ConditionalsBoundary mutant1 = new DiscountCalculator_Mutant_ConditionalsBoundary();
        DiscountCalculator_Mutant_NegateConditionals mutant2 = new DiscountCalculator_Mutant_NegateConditionals();
        DiscountCalculator_Mutant_MathOperator mutant3 = new DiscountCalculator_Mutant_MathOperator();

        boolean originalPassed = strongTest(original::applyDiscount);
        boolean mutant1Passed = strongTest(mutant1::applyDiscount);
        boolean mutant2Passed = strongTest(mutant2::applyDiscount);
        boolean mutant3Passed = strongTest(mutant3::applyDiscount);

        System.out.println("  original                              -> test passed: " + originalPassed + "  (correct: must pass)");
        System.out.println("  mutant #1 (Conditionals Boundary)      -> test passed: " + mutant1Passed + "  (KILLED by boundary case, 50%)");
        System.out.println("  mutant #2 (Negate Conditionals)        -> test passed: " + mutant2Passed + "  (KILLED, wrong branch taken)");
        System.out.println("  mutant #3 (Math operator 0.4 -> 1.4)   -> test passed: " + mutant3Passed + "  (KILLED, wrong value returned)");

        assert originalPassed : "the strong test must pass against correct, unmutated production code";
        assert !mutant1Passed && !mutant2Passed && !mutant3Passed
                : "the strong test should FAIL (kill) every one of the three mutants";

        int strongMutationScore = countKilled(!mutant1Passed, !mutant2Passed, !mutant3Passed);
        System.out.println("  Strong-test mutation score: " + strongMutationScore + "/3 mutants killed (100%).");
    }

    // -------------------------------------------------------------------------
    // Demo 3: Side-by-side summary -- coverage was identical (100%) for both tests;
    // mutation score is what actually distinguished a weak suite from a strong one.
    // -------------------------------------------------------------------------
    private static void demoSideBySideSummary() {
        printSection("3) Summary -- identical 100% line coverage, wildly different mutation scores");

        System.out.printf("  %-12s %-15s %-18s%n", "Test suite", "Line coverage", "Mutation score");
        System.out.printf("  %-12s %-15s %-18s%n", "weakTest", "100% (3/3)", "0%  (0/3 killed)");
        System.out.printf("  %-12s %-15s %-18s%n", "strongTest", "100% (3/3)", "100% (3/3 killed)");
        System.out.println();
        System.out.println("  This is the exact gap real PIT/pitest reports measure automatically in a");
        System.out.println("  Maven/Gradle build -- here it was reproduced by hand: same coverage number,");
        System.out.println("  but only the strong test would actually catch a real regression in production.");
    }

    /** Small helper to make the "N/3 killed" arithmetic explicit and readable at each call site. */
    private static int countKilled(boolean m1Killed, boolean m2Killed, boolean m3Killed) {
        int count = 0;
        if (m1Killed) count++;
        if (m2Killed) count++;
        if (m3Killed) count++;
        return count;
    }

    public static void main(String[] args) {
        demoWeakTestMissesEveryMutant();
        demoStrongTestKillsEveryMutant();
        demoSideBySideSummary();

        System.out.println();
        System.out.println("All Mutation Testing concept demos completed.");
    }
}
