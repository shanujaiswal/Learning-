/*
 * OptionalNullSafetyDemo.java
 *
 * Demonstrates:
 *     1. Optional.of vs Optional.ofNullable vs Optional.empty
 *     2. isPresent/isEmpty, ifPresent, ifPresentOrElse
 *     3. orElse vs orElseGet -- proving orElse's argument is evaluated eagerly
 *     4. orElseThrow (default and custom exception)
 *     5. map / filter / flatMap chains replacing nested null checks
 *     6. Optional.stream() flattening a list of Optionals
 *     7. Anti-pattern illustrations: unsafe get(), Optional as a field (bad) vs
 *        Optional only at the accessor boundary (good)
 *
 * Covers Theory chapter:
 *     06) Java 8+ Modern Features/Theory/04 Optional and Null Safety.md
 *
 * Compile & run:
 *     javac 04_optional_null_safety.java
 *     java OptionalNullSafetyDemo
 */

import java.util.*;
import java.util.stream.*;

public class OptionalNullSafetyDemo {

    record Address(String city) {}
    record Person(String name, Address address) {}   // address may legitimately be null internally

    // GOOD pattern: Optional used only as an accessor return type, not as a field type
    static final class UserAccount {
        private String middleName;   // nullable internally -- NOT wrapped in Optional as a field

        UserAccount(String middleName) {
            this.middleName = middleName;
        }

        Optional<String> getMiddleName() {
            return Optional.ofNullable(middleName);
        }
    }

    public static void main(String[] args) {
        printSection("1) Creating Optionals: of / ofNullable / empty");
        creatingOptionals();

        printSection("2) isPresent/isEmpty, ifPresent, ifPresentOrElse");
        checkingAndConsuming();

        printSection("3) orElse vs orElseGet -- eager vs lazy default");
        orElseVsOrElseGet();

        printSection("4) orElseThrow -- default and custom exception");
        orElseThrowDemo();

        printSection("5) map/filter/flatMap chains replacing nested null checks");
        chainingDemo();

        printSection("6) Optional.stream() flattening a list of Optionals");
        optionalStreamDemo();

        printSection("7) Anti-patterns: unsafe get() vs safe access, field-vs-accessor Optional");
        antiPatternsDemo();

        System.out.println("\nAll Optional / null-safety demos completed.");
    }

    private static void creatingOptionals() {
        Optional<String> present = Optional.of("hello");
        Optional<String> empty = Optional.empty();
        Optional<String> maybeNull = Optional.ofNullable(null);

        System.out.println("Optional.of(\"hello\")     -> " + present);
        System.out.println("Optional.empty()          -> " + empty);
        System.out.println("Optional.ofNullable(null) -> " + maybeNull);

        try {
            Optional.of(null);
        } catch (NullPointerException e) {
            System.out.println("Optional.of(null) throws immediately (fail-fast): " + e.getClass().getSimpleName());
        }
    }

    private static void checkingAndConsuming() {
        Optional<String> opt = Optional.of("value");
        Optional<String> empty = Optional.empty();

        System.out.println("opt.isPresent()   = " + opt.isPresent());
        System.out.println("empty.isEmpty()   = " + empty.isEmpty());

        opt.ifPresent(v -> System.out.println("ifPresent fired with: " + v));
        empty.ifPresent(v -> System.out.println("this should never print"));

        opt.ifPresentOrElse(
                v -> System.out.println("ifPresentOrElse (present branch): " + v),
                () -> System.out.println("this should never print"));
        empty.ifPresentOrElse(
                v -> System.out.println("this should never print"),
                () -> System.out.println("ifPresentOrElse (empty branch) fired"));
    }

    private static int expensiveDefaultCallCount = 0;

    private static String expensiveDefault() {
        expensiveDefaultCallCount++;
        System.out.println("   >>> expensiveDefault() actually executed! (call #" + expensiveDefaultCallCount + ")");
        return "computed-default";
    }

    private static void orElseVsOrElseGet() {
        Optional<String> present = Optional.of("present-value");

        System.out.println("Calling present.orElse(expensiveDefault()) -- watch for the eager call below:");
        String r1 = present.orElse(expensiveDefault());   // expensiveDefault() runs EVEN THOUGH present has a value
        System.out.println("Result: " + r1 + "   (expensiveDefaultCallCount=" + expensiveDefaultCallCount + ")");

        System.out.println("\nCalling present.orElseGet(() -> expensiveDefault()) -- lazy, should NOT execute:");
        String r2 = present.orElseGet(OptionalNullSafetyDemo::expensiveDefault);
        System.out.println("Result: " + r2 + "   (expensiveDefaultCallCount=" + expensiveDefaultCallCount + ", unchanged)");

        Optional<String> empty = Optional.empty();
        System.out.println("\nCalling empty.orElseGet(() -> expensiveDefault()) -- should execute now:");
        String r3 = empty.orElseGet(OptionalNullSafetyDemo::expensiveDefault);
        System.out.println("Result: " + r3 + "   (expensiveDefaultCallCount=" + expensiveDefaultCallCount + ", incremented)");
    }

    private static void orElseThrowDemo() {
        Optional<String> present = Optional.of("ok");
        System.out.println("present.orElseThrow() = " + present.orElseThrow());

        Optional<String> empty = Optional.empty();
        try {
            empty.orElseThrow();
        } catch (NoSuchElementException e) {
            System.out.println("Default orElseThrow() -> NoSuchElementException: " + e.getMessage());
        }

        try {
            empty.orElseThrow(() -> new IllegalStateException("name is required but was missing"));
        } catch (IllegalStateException e) {
            System.out.println("Custom orElseThrow() -> IllegalStateException: " + e.getMessage());
        }
    }

    private static void chainingDemo() {
        Person withAddress = new Person("Alice", new Address("NYC"));
        Person withoutAddress = new Person("Bob", null);

        String city1 = Optional.ofNullable(withAddress)
                .map(Person::address)
                .map(Address::city)
                .map(String::toUpperCase)
                .orElse("UNKNOWN");
        String city2 = Optional.ofNullable(withoutAddress)
                .map(Person::address)
                .map(Address::city)
                .map(String::toUpperCase)
                .orElse("UNKNOWN");

        System.out.println("Chained lookup (has address):    " + city1);
        System.out.println("Chained lookup (no address):     " + city2);

        // filter demo
        Optional<String> name = Optional.of("  alice  ");
        Optional<String> filteredShort = name.map(String::trim).filter(s -> s.length() > 10);
        Optional<String> filteredLong = name.map(String::trim).filter(s -> s.length() >= 5);
        System.out.println("filter (fails, too short): " + filteredShort);
        System.out.println("filter (passes):           " + filteredLong);
    }

    private static void optionalStreamDemo() {
        List<Optional<String>> optionals = List.of(Optional.of("a"), Optional.empty(), Optional.of("b"), Optional.empty());

        List<String> presentValues = optionals.stream()
                .flatMap(Optional::stream)
                .collect(Collectors.toList());
        System.out.println("Flattened present values from List<Optional<String>>: " + presentValues);
    }

    private static void antiPatternsDemo() {
        Optional<String> empty = Optional.empty();
        try {
            empty.get();   // unsafe -- anti-pattern
        } catch (NoSuchElementException e) {
            System.out.println("Calling .get() on empty Optional (anti-pattern) threw: " + e.getClass().getSimpleName());
        }
        System.out.println("Safer alternative -- empty.orElse(\"fallback\") = " + empty.orElse("fallback"));

        UserAccount withMiddleName = new UserAccount("Marie");
        UserAccount withoutMiddleName = new UserAccount(null);
        System.out.println("getMiddleName() (present): " + withMiddleName.getMiddleName());
        System.out.println("getMiddleName() (absent):  " + withoutMiddleName.getMiddleName());
        System.out.println("Note: UserAccount stores middleName as a plain nullable String field internally,");
        System.out.println("      and only wraps it in Optional at the accessor boundary -- not as the field type itself.");
    }

    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }
}
