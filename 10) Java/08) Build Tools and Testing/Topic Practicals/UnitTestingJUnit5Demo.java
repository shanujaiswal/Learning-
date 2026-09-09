/*
 * UnitTestingJUnit5Demo.java
 *
 * Covers Theory chapter:
 *     08) Build Tools and Testing/Theory/03 Unit Testing with JUnit 5.md
 *
 * IMPORTANT: This file demonstrates a class under test (Calculator) alongside a full JUnit 5 test
 * class (CalculatorTest) written with real JUnit 5 imports and annotations -- @Test, @BeforeEach,
 * @AfterEach, @BeforeAll/@AfterAll, assertions, assertThrows, and @ParameterizedTest/@ValueSource /
 * @CsvSource. The test class is kept COMMENTED OUT because JUnit 5 (org.junit.jupiter:junit-jupiter)
 * is not an actual dependency available in this environment -- there is no pom.xml/build.gradle here
 * to pull it in, so it will NOT compile with plain javac. It is included in full, with real import
 * statements, purely for study purposes; see Theory file 03 for the full annotation/assertion reference.
 *
 * A real project would split this into:
 *   src/main/java/com/example/app/Calculator.java
 *   src/test/java/com/example/app/CalculatorTest.java
 * with junit-jupiter added as a test-scoped dependency (see Theory 03's pom.xml/build.gradle snippets).
 *
 * To make this SINGLE FILE still compile/run standalone with plain javac/java (no build tool, no
 * JUnit on the classpath), a small hand-rolled MiniAssert helper class is provided further below,
 * and the runnable main() exercises Calculator through that instead -- standing in for what the
 * commented-out real JUnit 5 test class would verify automatically under "mvn test" / "gradlew test".
 * ---------------------------------------------------------------------------------------------
 */

import java.util.List;

// =====================================================================================
// This class represents what would live at: src/main/java/com/example/app/Calculator.java
// =====================================================================================
class Calculator {

    double add(double a, double b) {
        return a + b;
    }

    double subtract(double a, double b) {
        return a - b;
    }

    double multiply(double a, double b) {
        return a * b;
    }

    double divide(double a, double b) {
        if (b == 0) {
            throw new ArithmeticException("/ by zero");
        }
        return a / b;
    }

    // Deliberately simple "is prime" helper used below to demonstrate @ParameterizedTest
    // against a range of int inputs (see CalculatorTest.allKnownPrimesReturnTrue).
    boolean isPrime(int n) {
        if (n < 2) {
            return false;
        }
        for (int i = 2; i * i <= n; i++) {
            if (n % i == 0) {
                return false;
            }
        }
        return true;
    }
}

// =====================================================================================
// This class represents what would live at: src/test/java/com/example/app/CalculatorTest.java
//
// Requires the junit-jupiter dependency (see Theory 03) to actually compile/run:
//   <dependency>
//       <groupId>org.junit.jupiter</groupId>
//       <artifactId>junit-jupiter</artifactId>
//       <version>5.10.2</version>
//       <scope>test</scope>
//   </dependency>
//
// Run via:  mvn test           (Maven, using maven-surefire-plugin)
//           ./gradlew test     (Gradle, with test { useJUnitPlatform() } configured)
// =====================================================================================
/*
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Calculator behavior")
class CalculatorTest {

    private Calculator calculator;

    @BeforeAll
    static void setupOnce() {
        // Runs ONCE before all tests in this class -- must be static, since it runs before any
        // instance of CalculatorTest exists (JUnit creates a new instance per @Test method).
        System.out.println("Starting CalculatorTest suite...");
    }

    @BeforeEach
    void setUp() {
        // Fresh Calculator before every single test -- guarantees no state leaks between tests.
        calculator = new Calculator();
    }

    @AfterEach
    void tearDown() {
        // Runs after every test, even if it failed. Nothing to release here since Calculator
        // holds no external resources, but this is where you'd close files/connections/etc.
        calculator = null;
    }

    @AfterAll
    static void tearDownOnce() {
        // Runs ONCE after all tests in this class -- must be static, same reasoning as @BeforeAll.
        System.out.println("Finished CalculatorTest suite.");
    }

    // --------------------------------------------------------------------------------
    // Basic assertions
    // --------------------------------------------------------------------------------

    @Test
    @DisplayName("add() sums two numbers")
    void addSumsTwoNumbers() {
        assertEquals(5.0, calculator.add(2.0, 3.0));
        assertEquals(5.0, calculator.add(2.0, 3.0), "2 + 3 should equal 5");   // optional message
    }

    @Test
    void subtractComputesDifference() {
        assertEquals(1.0, calculator.subtract(4.0, 3.0));
        assertNotEquals(0.0, calculator.subtract(4.0, 3.0));
    }

    @Test
    void multiplyComputesProduct() {
        assertEquals(12.0, calculator.multiply(3.0, 4.0));
        assertTrue(calculator.multiply(3.0, 4.0) > 0);
        assertFalse(calculator.multiply(-3.0, 4.0) > 0);
    }

    @Test
    void divideHandlesFloatingPointWithDelta() {
        // Floating point equality needs an explicit delta/tolerance -- 0.1 + 0.2 != 0.3 exactly
        // in binary floating point, so a raw assertEquals(0.3, ...) without a delta would fail.
        assertEquals(0.3333, calculator.divide(1.0, 3.0), 0.0001);
    }

    // --------------------------------------------------------------------------------
    // Exception testing with assertThrows
    // --------------------------------------------------------------------------------

    @Test
    @DisplayName("divide by zero throws ArithmeticException")
    void divideByZeroThrows() {
        // The throwing code MUST be wrapped in a lambda (Executable) -- calling it directly
        // would just propagate the exception and fail the test with an unhandled exception.
        ArithmeticException ex = assertThrows(
            ArithmeticException.class,
            () -> calculator.divide(10.0, 0.0)
        );
        assertEquals("/ by zero", ex.getMessage());
    }

    @Test
    void divideByNonZeroDoesNotThrow() {
        // Documents intent explicitly: this call must NOT throw.
        assertDoesNotThrow(() -> calculator.divide(10.0, 2.0));
    }

    // --------------------------------------------------------------------------------
    // Grouped assertions with assertAll -- runs every assertion even if earlier ones fail,
    // and reports ALL failures together (vs. separate calls, where the first failure stops
    // the test immediately).
    // --------------------------------------------------------------------------------

    @Test
    void assertAllChecksMultiplePropertiesTogether() {
        assertAll("basic arithmetic",
            () -> assertEquals(5.0, calculator.add(2.0, 3.0)),
            () -> assertEquals(1.0, calculator.subtract(4.0, 3.0)),
            () -> assertEquals(12.0, calculator.multiply(3.0, 4.0))
        );
    }

    // --------------------------------------------------------------------------------
    // Parameterized tests
    // --------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 5, 7, 11, 13})
    @DisplayName("known primes return true")
    void allKnownPrimesReturnTrue(int number) {
        assertTrue(calculator.isPrime(number));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 4, 6, 8, 9, 10})
    void knownNonPrimesReturnFalse(int number) {
        assertFalse(calculator.isPrime(number));
    }

    @ParameterizedTest
    @CsvSource({
        "2.0, 3.0, 5.0",
        "0.0, 0.0, 0.0",
        "-2.0, 2.0, 0.0",
        "10.5, 0.5, 11.0"
    })
    void addReturnsCorrectSumForVariousInputs(double a, double b, double expectedSum) {
        assertEquals(expectedSum, calculator.add(a, b), 0.0001);
    }
}
*/

// =====================================================================================
// Minimal, hand-rolled assertion helper so this file can compile/run standalone with plain
// javac/java, without JUnit on the classpath -- NOT a replacement for JUnit 5, just a stand-in
// that lets the demo below exercise Calculator and print pass/fail results for study purposes.
// =====================================================================================
class MiniAssert {

    static int passed = 0;
    static int failed = 0;

    static void assertEquals(double expected, double actual, double delta, String label) {
        if (Math.abs(expected - actual) <= delta) {
            passed++;
            System.out.println("  [PASS] " + label);
        } else {
            failed++;
            System.out.println("  [FAIL] " + label + " -- expected " + expected + " but was " + actual);
        }
    }

    static void assertTrue(boolean condition, String label) {
        if (condition) {
            passed++;
            System.out.println("  [PASS] " + label);
        } else {
            failed++;
            System.out.println("  [FAIL] " + label);
        }
    }

    static void assertThrowsArithmetic(Runnable action, String label) {
        try {
            action.run();
            failed++;
            System.out.println("  [FAIL] " + label + " -- expected ArithmeticException but none was thrown");
        } catch (ArithmeticException e) {
            passed++;
            System.out.println("  [PASS] " + label + " (caught: " + e.getMessage() + ")");
        }
    }

    static void summary() {
        System.out.println("\nResults: " + passed + " passed, " + failed + " failed");
    }
}

// =====================================================================================
// Runnable entry point so this single file can still be compiled/run directly with plain javac/java
// for study purposes, independent of any build tool or JUnit on the classpath -- exercises Calculator
// manually via MiniAssert above, standing in for what the (commented-out) real JUnit 5 CalculatorTest
// class would verify automatically under "mvn test" / "./gradlew test".
// =====================================================================================
public class UnitTestingJUnit5Demo {
    public static void main(String[] args) {
        Calculator calculator = new Calculator();

        System.out.println("Running Calculator checks (MiniAssert stand-in for JUnit 5)...\n");

        MiniAssert.assertEquals(5.0, calculator.add(2.0, 3.0), 0.0001, "add(2, 3) == 5");
        MiniAssert.assertEquals(1.0, calculator.subtract(4.0, 3.0), 0.0001, "subtract(4, 3) == 1");
        MiniAssert.assertEquals(12.0, calculator.multiply(3.0, 4.0), 0.0001, "multiply(3, 4) == 12");
        MiniAssert.assertEquals(0.3333, calculator.divide(1.0, 3.0), 0.0001, "divide(1, 3) ~= 0.3333");

        MiniAssert.assertThrowsArithmetic(
            () -> calculator.divide(10.0, 0.0),
            "divide(10, 0) throws ArithmeticException"
        );

        for (int prime : List.of(2, 3, 5, 7, 11, 13)) {
            MiniAssert.assertTrue(calculator.isPrime(prime), "isPrime(" + prime + ") == true");
        }
        for (int nonPrime : List.of(1, 4, 6, 8, 9, 10)) {
            MiniAssert.assertTrue(!calculator.isPrime(nonPrime), "isPrime(" + nonPrime + ") == false");
        }

        MiniAssert.summary();

        System.out.println("\nIn a real project with JUnit 5 on the classpath:");
        System.out.println("  mvn test        -> runs CalculatorTest (see commented block above) via Surefire");
        System.out.println("  ./gradlew test  -> runs CalculatorTest via the JUnit Platform");
        System.out.println("  @ParameterizedTest + @ValueSource/@CsvSource replace the manual loops above");
        System.out.println("  assertThrows(...) replaces the manual try/catch used in MiniAssert above");
    }
}
