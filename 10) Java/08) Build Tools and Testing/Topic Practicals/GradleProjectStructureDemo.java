/*
 * GradleProjectStructureDemo.java
 *
 * Covers Theory chapter:
 *     08) Build Tools and Testing/Theory/02 Gradle Fundamentals.md
 *
 * IMPORTANT: This file demonstrates the JAVA CODE side of a Gradle project (what would live under
 * src/main/java and src/test/java) alongside COMMENTED build.gradle / build.gradle.kts snippets
 * showing the build config that would drive it. It is a single, self-contained .java file for study
 * purposes -- it will NOT compile/run as-is inside a real Gradle project (a real project splits
 * PriceCalculator / PriceCalculatorTest into separate files, and build.gradle is its own top-level
 * file, not Java code). Read this alongside Theory file 02 for the full explanation of each concept.
 *
 * ---------------------------------------------------------------------------------------------
 * WHAT A REAL GRADLE PROJECT'S build.gradle (Groovy DSL) WOULD LOOK LIKE FOR THIS CODE:
 *
 * plugins {
 *     id 'java'
 * }
 *
 * repositories {
 *     mavenCentral()
 * }
 *
 * dependencies {
 *     testImplementation 'org.junit.jupiter:junit-jupiter:5.10.2'
 * }
 *
 * test {
 *     useJUnitPlatform()
 * }
 *
 * ---------------------------------------------------------------------------------------------
 * EQUIVALENT build.gradle.kts (Kotlin DSL):
 *
 * plugins {
 *     java
 * }
 *
 * repositories {
 *     mavenCentral()
 * }
 *
 * dependencies {
 *     testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
 * }
 *
 * tasks.test {
 *     useJUnitPlatform()
 * }
 *
 * Tasks in action for this project:
 *     ./gradlew compileJava   -> compiles PriceCalculator below into build/classes/java/main
 *     ./gradlew test          -> also runs PriceCalculatorTest via the JUnit Platform
 *     ./gradlew build          -> compile + test + assemble build/libs/*.jar
 *     ./gradlew tasks          -> lists every available task in this project
 * ---------------------------------------------------------------------------------------------
 */

import java.util.List;

// =====================================================================================
// This class represents what would live at: src/main/java/com/example/app/PriceCalculator.java
// =====================================================================================
class PriceCalculator {

    private static final double TAX_RATE = 0.08;

    double subtotal(List<Double> itemPrices) {
        double total = 0.0;
        for (double price : itemPrices) {
            if (price < 0) {
                throw new IllegalArgumentException("price cannot be negative: " + price);
            }
            total += price;
        }
        return total;
    }

    double totalWithTax(List<Double> itemPrices) {
        return round2(subtotal(itemPrices) * (1 + TAX_RATE));
    }

    double applyDiscount(double amount, double discountPercent) {
        if (discountPercent < 0 || discountPercent > 100) {
            throw new IllegalArgumentException("discountPercent must be between 0 and 100");
        }
        return round2(amount * (1 - discountPercent / 100.0));
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}

// =====================================================================================
// This class represents what would live at: src/test/java/com/example/app/PriceCalculatorTest.java
// Uses JUnit 5 syntax (see Theory file 03) -- included here to show how "./gradlew test" would
// exercise this class. Requires the junit-jupiter dependency shown in the build.gradle snippet above.
// =====================================================================================
/*
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PriceCalculatorTest {

    private final PriceCalculator calculator = new PriceCalculator();

    @Test
    void subtotalSumsAllPrices() {
        assertEquals(30.0, calculator.subtotal(List.of(10.0, 10.0, 10.0)));
    }

    @Test
    void subtotalRejectsNegativePrice() {
        assertThrows(IllegalArgumentException.class,
            () -> calculator.subtotal(List.of(10.0, -5.0)));
    }

    @Test
    void totalWithTaxAppliesTaxRate() {
        // 100 * 1.08 = 108.00
        assertEquals(108.0, calculator.totalWithTax(List.of(100.0)), 0.001);
    }

    @ParameterizedTest
    @CsvSource({
        "100.0, 10, 90.0",
        "50.0, 50, 25.0",
        "200.0, 0, 200.0"
    })
    void applyDiscountComputesCorrectAmount(double amount, double discountPercent, double expected) {
        assertEquals(expected, calculator.applyDiscount(amount, discountPercent), 0.001);
    }

    @Test
    void applyDiscountRejectsOutOfRangePercent() {
        assertThrows(IllegalArgumentException.class, () -> calculator.applyDiscount(100.0, 150));
    }
}
*/

// =====================================================================================
// Runnable entry point so this single file can still be compiled/run directly with plain javac/java
// for study purposes, independent of any Gradle project -- exercises PriceCalculator manually,
// standing in for what the (commented-out) JUnit test class above would verify automatically.
// =====================================================================================
public class GradleProjectStructureDemo {
    public static void main(String[] args) {
        PriceCalculator calculator = new PriceCalculator();

        List<Double> cart = List.of(19.99, 5.50, 12.00);
        System.out.printf("Subtotal: %.2f%n", calculator.subtotal(cart));
        System.out.printf("Total with tax: %.2f%n", calculator.totalWithTax(cart));
        System.out.printf("100.0 with 20%% discount: %.2f%n", calculator.applyDiscount(100.0, 20));

        try {
            calculator.applyDiscount(100.0, 150);
        } catch (IllegalArgumentException e) {
            System.out.println("Expected failure caught: " + e.getMessage());
        }

        System.out.println("\nIn a real Gradle project:");
        System.out.println("  ./gradlew compileJava -> compiles this class");
        System.out.println("  ./gradlew test        -> also runs PriceCalculatorTest (see commented block above)");
        System.out.println("  ./gradlew build         -> compile + test + assemble build/libs/*.jar");
        System.out.println("  ./gradlew tasks          -> lists every available task, incl. custom ones");
    }
}
