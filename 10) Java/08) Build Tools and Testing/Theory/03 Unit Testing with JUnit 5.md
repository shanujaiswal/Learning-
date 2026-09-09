# Why Automated Testing Matters

--> Manually re-checking that your code still works after every change does not scale -- it's slow, inconsistent, and people skip it under deadline pressure. An automated test suite encodes "what correct behavior looks like" as executable code, so it can be re-run in seconds, on every change, forever, by both humans and CI machines.
--> **Unit tests** verify a single unit of behavior (typically one method or one small class) in ISOLATION from the rest of the system -- no real database, no real network calls, no real filesystem. This makes them fast (milliseconds each) and precise (a failure points at almost exactly the broken line).
--> **JUnit 5** (also called "JUnit Jupiter") is the current standard testing framework for Java, and the near-universal default for unit testing in the JVM ecosystem. It's a ground-up rewrite of JUnit 4, built around three sub-projects: **Jupiter** (the new programming model/API you actually write tests against), **Platform** (the engine that discovers and runs tests, including old JUnit 3/4 tests via a compatibility layer), and **Vintage** (that compatibility layer).

# Adding JUnit 5 to a Project

```xml
<!-- Maven: pom.xml -->
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>5.10.2</version>
    <scope>test</scope>
</dependency>

<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-surefire-plugin</artifactId>
            <version>3.2.5</version>   <!-- must be a version that understands JUnit 5's platform -->
        </plugin>
    </plugins>
</build>
```

```groovy
// Gradle: build.gradle
dependencies {
    testImplementation 'org.junit.jupiter:junit-jupiter:5.10.2'
}
test {
    useJUnitPlatform()    // tells Gradle's test task to run tests via the JUnit Platform
}
```

# Core Lifecycle Annotations

--> JUnit creates a NEW instance of the test class for EVERY test method by default -- this is deliberate, so tests don't accidentally share mutable state through instance fields and one test's leftover state can't silently affect another.

```java
import org.junit.jupiter.api.*;

class LifecycleDemoTest {

    @BeforeAll
    static void setupOnce() {
        // Runs ONCE before ALL test methods in this class -- must be static
        // (because it runs before any instance exists, given a new instance per test).
        // Typical use: starting a shared, expensive resource (e.g. an embedded test server).
    }

    @BeforeEach
    void setupEachTest() {
        // Runs before EVERY test method -- typical use: resetting/creating fresh test fixtures
        // so each test starts from a known, isolated state.
    }

    @Test
    void firstTest() {
        // The actual test -- any public method annotated @Test.
    }

    @Test
    void secondTest() {
        // Completely independent instance/state from firstTest() above.
    }

    @AfterEach
    void tearDownEachTest() {
        // Runs after EVERY test method, even if it failed -- typical use: closing resources.
    }

    @AfterAll
    static void tearDownOnce() {
        // Runs ONCE after ALL tests in this class -- must be static, same reasoning as @BeforeAll.
    }
}
```

```text
Execution order for a class with 2 @Test methods:

@BeforeAll  (once)
  @BeforeEach -> firstTest()  -> @AfterEach
  @BeforeEach -> secondTest() -> @AfterEach
@AfterAll   (once)
```

## Other Common Annotations

| Annotation | Purpose |
|---|---|
| `@Test` | Marks a method as a test case |
| `@DisplayName("...")` | Custom, human-readable name shown in test reports instead of the method name |
| `@Disabled("reason")` | Skips this test (compiles, but doesn't run) -- always include a reason |
| `@Nested` | Groups related tests in an inner class, useful for organizing tests around scenarios |
| `@Tag("slow")` | Labels a test for selective inclusion/exclusion, e.g. `mvn test -Dgroups=slow` |
| `@RepeatedTest(5)` | Runs the same test multiple times -- useful for flaky/timing-sensitive or randomized logic |
| `@Timeout(2)` | Fails the test if it takes longer than the given duration (seconds by default) |

```java
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Bank Account behavior")
class BankAccountTest {

    @Nested
    @DisplayName("when the account has a positive balance")
    class WithPositiveBalance {
        @Test
        @DisplayName("withdrawing less than the balance succeeds")
        void withdrawSucceeds() {
            // ...
        }
    }

    @Nested
    @DisplayName("when the account is empty")
    class WithZeroBalance {
        @Test
        @DisplayName("withdrawing anything throws")
        void withdrawThrows() {
            // ...
        }
    }

    @Test
    @Disabled("Flaky under CI load -- tracked in JIRA-1234")
    void temporarilyDisabledTest() {
        // ...
    }
}
```

# Assertions

--> All standard assertions live in `org.junit.jupiter.api.Assertions`, typically static-imported for readability (`assertEquals(...)` instead of `Assertions.assertEquals(...)`).

```java
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class AssertionsDemoTest {

    @Test
    void basicAssertions() {
        assertEquals(4, 2 + 2);                          // expected, actual
        assertEquals(4, 2 + 2, "2 + 2 should equal 4");   // optional failure message (3rd arg)
        assertNotEquals(5, 2 + 2);

        assertTrue(5 > 2);
        assertFalse(2 > 5);

        assertNull(null);
        assertNotNull("not null");

        assertSame("interned string literal used twice refers to the same object");
        // assertSame checks REFERENCE equality (==); assertEquals checks logical equality (.equals())

        String[] expected = {"a", "b", "c"};
        String[] actual = {"a", "b", "c"};
        assertArrayEquals(expected, actual);              // element-by-element comparison

        assertEquals(0.30000000000000004, 0.1 + 0.2);     // WRONG -- floating point precision issue
        assertEquals(0.3, 0.1 + 0.2, 0.0001);              // correct -- explicit delta/tolerance for doubles
    }

    @Test
    void groupedAssertions() {
        // assertAll runs EVERY assertion even if earlier ones fail, and reports ALL failures together
        // -- versus separate assertEquals calls, where the first failure stops the test immediately.
        var person = new Person("Ada", 36);
        assertAll("person properties",
            () -> assertEquals("Ada", person.name()),
            () -> assertEquals(36, person.age()),
            () -> assertTrue(person.age() > 0)
        );
    }

    record Person(String name, int age) {}
}
```

## Exception Testing with `assertThrows`

```java
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ExceptionTestingDemoTest {

    @Test
    void divideByZeroThrowsArithmeticException() {
        Calculator calc = new Calculator();

        // assertThrows returns the caught exception, so you can assert further on it
        ArithmeticException ex = assertThrows(
            ArithmeticException.class,
            () -> calc.divide(10, 0)
        );
        assertEquals("/ by zero", ex.getMessage());
    }

    @Test
    void assertDoesNotThrowExample() {
        Calculator calc = new Calculator();
        // Documents intent: this call must NOT throw. Fails with a clear message if it does,
        // rather than letting the test fail with a raw, less-obvious stack trace.
        assertDoesNotThrow(() -> calc.divide(10, 2));
    }

    static class Calculator {
        int divide(int a, int b) { return a / b; }
    }
}
```

--> **Key detail**: the code that might throw must be wrapped in a lambda (`Executable`) passed to `assertThrows` -- calling the throwing code directly would just let the exception propagate and fail the test with an unhandled exception, rather than being captured and asserted on.

# Parameterized Tests

--> Parameterized tests run the SAME test logic against multiple sets of input data, avoiding copy-pasted near-duplicate test methods that differ only in their input values.

```java
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;

class ParameterizedDemoTest {

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    void allNumbersArePositive(int number) {
        assertTrue(number > 0);
    }

    @ParameterizedTest
    @CsvSource({
        "1, 1, 2",
        "2, 3, 5",
        "10, -5, 5"
    })
    void addReturnsCorrectSum(int a, int b, int expectedSum) {
        assertEquals(expectedSum, a + b);
    }

    @ParameterizedTest
    @MethodSource("stringProvider")   // references the method below by name
    void stringsAreNotBlank(String input) {
        assertFalse(input.isBlank());
    }

    static Stream<String> stringProvider() {
        return Stream.of("apple", "banana", "cherry");
    }

    @ParameterizedTest
    @EnumSource(DayOfWeek.class)
    void everyEnumValueHasAName(DayOfWeek day) {
        assertNotNull(day.name());
    }

    @ParameterizedTest
    @NullAndEmptySource                  // adds null AND "" as extra test cases automatically
    @ValueSource(strings = {" ", "  ", "\t"})
    void blankStringsAreTreatedAsBlank(String input) {
        assertTrue(input == null || input.isBlank());
    }

    enum DayOfWeek { MON, TUE, WED, THU, FRI, SAT, SUN }
}
```

--> Requires an extra dependency: `org.junit.jupiter:junit-jupiter-params` (usually pulled in transitively by the `junit-jupiter` aggregator artifact used above).
--> **`@ValueSource`** -- a simple list of one type of literal (ints, strings, etc). **`@CsvSource`** -- multiple parameters per test case, as comma-separated values. **`@MethodSource`** -- delegates to a method returning a `Stream`/`Collection`, needed when test data is too complex for a simple literal list. **`@EnumSource`** -- runs once per enum constant.

# Assumptions -- Conditionally Running Tests

```java
import static org.junit.jupiter.api.Assumptions.*;
import org.junit.jupiter.api.Test;

class AssumptionsDemoTest {

    @Test
    void onlyRunsOnCI() {
        // If the assumption is false, the test is ABORTED (not failed) -- shown as "skipped"
        // in test reports, distinct from a genuine failure.
        assumeTrue("true".equals(System.getenv("CI")));
        // ... test logic that only makes sense in a CI environment
    }
}
```

--> **`assumeTrue`/`assumeFalse` vs `@Disabled`** -- `@Disabled` is a static, always-off switch decided ahead of time; assumptions are a RUNTIME decision, letting a test dynamically skip itself based on the current environment (OS, env vars, available resources) without being reported as a failure.

# Test Doubles Preview and Where Mockito Fits

--> A "unit" test that reaches into a real database or calls a real external API is no longer really testing in ISOLATION -- it's slow, flaky (network/environment dependent), and a failure could mean the unit is broken OR the database was down. The standard fix is replacing real collaborators with TEST DOUBLES (fakes, stubs, mocks) that simulate just enough behavior for the test -- this is the whole subject of the next file, Mocking with Mockito.

# Common Gotchas and Best Practices

--> **One logical assertion focus per test** -- a test named `withdrawSucceeds` should be about withdrawal succeeding, not also silently checking five unrelated things. Use `assertAll` when multiple assertions genuinely belong to the same single logical check (e.g. checking all fields of one resulting object).
--> **Test names should describe behavior, not implementation** -- prefer `throwsWhenWithdrawingMoreThanBalance` over `testWithdraw2`. `@DisplayName` can make this even more readable in test reports without cluttering the method name itself.
--> **Don't rely on test execution order.** Each `@Test` should be independent and pass regardless of what ran before it -- shared mutable state between tests (e.g. a static counter incremented by one test and read by another) is a common, hard-to-debug source of "tests pass alone but fail together."
--> **Prefer `assertEquals(expected, actual)` argument order consistently** -- getting this backwards doesn't break the test, but it produces a confusing failure message ("expected X but was Y" reads backwards), which slows down debugging failures later.
--> **Floating point equality needs a delta.** `assertEquals(0.3, 0.1 + 0.2)` fails due to binary floating-point representation -- always pass an explicit tolerance for `double`/`float` comparisons.
--> **`@BeforeEach` over shared instance state set up once** -- resist the temptation to initialize fixtures in a field initializer or constructor if it involves any real setup logic; `@BeforeEach` makes the reset-per-test behavior explicit and consistent, and is where JUnit expects it.
