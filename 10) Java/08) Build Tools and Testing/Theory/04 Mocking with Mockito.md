# Why Mocking Exists

--> A "unit" under test frequently COLLABORATES with other objects -- a service class calls a repository, which calls a database; an order processor calls a payment gateway, which calls a real external API. Testing the unit against the REAL collaborators makes the test slow, flaky (network/DB/environment dependent), and hard to control (how do you reliably make a real payment gateway return "declined" for a test?).
--> **Mockito** is the standard mocking framework for Java -- it creates fake, programmable stand-ins for real collaborator objects, so a unit test can control exactly what those collaborators return and verify exactly how they were called, entirely in memory, with no real I/O.
--> The class under test is REAL; only its collaborators are replaced. This keeps the test focused on the actual unit's logic while isolating it from everything the unit depends on.

# Test Doubles -- Vocabulary

--> "Mock" is used loosely in everyday conversation, but there's a useful, more precise vocabulary (from Martin Fowler's "Mocks Aren't Stubs") worth knowing:

| Term | What it does | Verified how |
|---|---|---|
| **Dummy** | A placeholder object passed around but never actually used (e.g. to satisfy a constructor parameter) | Never inspected at all |
| **Stub** | Returns pre-programmed answers to calls made during the test | Test checks the RESULT of the unit under test, not the stub itself |
| **Fake** | A working, simplified implementation (e.g. an in-memory `Map`-backed repository instead of a real DB) | Behaves like the real thing, just simpler |
| **Mock** | Pre-programmed with expectations about which calls it should receive | Test explicitly VERIFIES certain calls happened, e.g. "was `save()` called exactly once?" |
| **Spy** | Wraps a REAL object, letting most calls pass through while selectively overriding/tracking specific ones | Mix of real behavior + verification |

--> In practice, Mockito's `mock()` can act as either a stub OR a mock depending on whether the test only reads its programmed return value (stubbing) or explicitly asserts on which calls were made to it (mocking/verification) -- Mockito doesn't force you to pick one label, but understanding the distinction clarifies WHY you're using a test double a certain way in a given test.

# Adding Mockito to a Project

```xml
<!-- Maven: pom.xml -->
<dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-core</artifactId>
    <version>5.12.0</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-junit-jupiter</artifactId>   <!-- integrates Mockito with JUnit 5 annotations -->
    <version>5.12.0</version>
    <scope>test</scope>
</dependency>
```

```groovy
// Gradle: build.gradle
dependencies {
    testImplementation 'org.mockito:mockito-core:5.12.0'
    testImplementation 'org.mockito:mockito-junit-jupiter:5.12.0'
}
```

# Creating Mocks -- `@Mock`, `@InjectMocks`, and `mock()`

```java
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)   // enables @Mock/@InjectMocks processing for this test class
class OrderServiceTest {

    @Mock                              // Mockito creates a fake InventoryRepository automatically
    private InventoryRepository inventoryRepository;

    @Mock
    private PaymentGateway paymentGateway;

    @InjectMocks                       // Mockito creates a REAL OrderService and injects the two mocks above
    private OrderService orderService;  // into its constructor (or fields/setters) automatically

    @Test
    void placingAnOrderChargesPaymentAndReducesStock() {
        when(inventoryRepository.hasStock("SKU-1", 2)).thenReturn(true);
        when(paymentGateway.charge(anyString(), eq(49.98))).thenReturn(true);

        boolean success = orderService.placeOrder("SKU-1", 2, 49.98, "card-123");

        assertTrue(success);
        verify(inventoryRepository).reduceStock("SKU-1", 2);
        verify(paymentGateway).charge("card-123", 49.98);
    }
}
```

--> **`@Mock`** creates a bare fake of the given type -- every method returns a sensible default (null, 0, false, empty collection) until you program it with `when(...)`.
--> **`@InjectMocks`** creates a REAL instance of the class under test and tries to inject the `@Mock`-annotated fields into it (via constructor injection first, then setters, then field injection) -- this is the class you're actually testing, wired up with fakes for everything it depends on.
--> **Without the `MockitoExtension`**, the equivalent setup is manual: `InventoryRepository inventoryRepository = mock(InventoryRepository.class);` and manually constructing `OrderService` with the mocks passed in -- both approaches are common; `@Mock`/`@InjectMocks` mainly reduces boilerplate in larger test classes with many collaborators.

# Stubbing Behavior with `when(...).thenReturn(...)`

```java
import static org.mockito.Mockito.*;

class StubbingDemoTest {

    @Test
    void stubbingExamples() {
        InventoryRepository repo = mock(InventoryRepository.class);

        when(repo.hasStock("SKU-1", 5)).thenReturn(true);
        when(repo.hasStock("SKU-2", 5)).thenReturn(false);

        // Stubbing a sequence of different return values across successive calls
        when(repo.getStockLevel("SKU-1"))
            .thenReturn(10)
            .thenReturn(9)     // second call returns this
            .thenReturn(8);    // third and every subsequent call returns this (last value "sticks")

        // Stubbing an exception instead of a return value
        when(repo.getStockLevel("INVALID")).thenThrow(new IllegalArgumentException("unknown SKU"));

        // Stubbing with a dynamic Answer instead of a fixed value
        when(repo.getStockLevel(anyString())).thenAnswer(invocation -> {
            String sku = invocation.getArgument(0);
            return sku.startsWith("SKU-") ? 100 : 0;
        });

        // void methods use doThrow/doNothing/doAnswer instead of when(...) -- see below
        InventoryRepository voidRepo = mock(InventoryRepository.class);
        doThrow(new RuntimeException("DB down")).when(voidRepo).reduceStock("SKU-1", 5);
    }

    interface InventoryRepository {
        boolean hasStock(String sku, int quantity);
        int getStockLevel(String sku);
        void reduceStock(String sku, int quantity);
    }
}
```

--> **Why void methods need `doThrow`/`doAnswer`/`doNothing` instead of `when(...)`** -- `when(mock.voidMethod())` doesn't compile, because `when()` needs an actual return value to capture -- a void call returns nothing to pass to `when()`. The `do...().when(mock).voidMethod()` form flips the order specifically to work around this.

# Argument Matchers

--> Once you use ANY matcher (`any()`, `eq()`, `anyString()`, etc.) in a stubbing or verification call, ALL arguments in that call must use matchers -- you cannot mix a raw literal value with a matcher in the same call.

```java
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ArgumentMatchersDemoTest {

    @Test
    void matcherExamples() {
        PaymentGateway gateway = mock(PaymentGateway.class);

        when(gateway.charge(anyString(), anyDouble())).thenReturn(true);       // matches ANY arguments
        when(gateway.charge(eq("card-123"), eq(49.98))).thenReturn(true);       // matches EXACT values
        when(gateway.charge(startsWith("card-"), gt(0.0))).thenReturn(true);    // partial/conditional matching

        // WRONG -- mixes a matcher with a raw literal in the same call, throws InvalidUseOfMatchersException
        // when(gateway.charge(anyString(), 49.98)).thenReturn(true);

        // CORRECT -- once one arg uses a matcher, every arg must
        when(gateway.charge(anyString(), eq(49.98))).thenReturn(true);
    }

    interface PaymentGateway {
        boolean charge(String cardToken, double amount);
    }
}
```

| Matcher | Meaning |
|---|---|
| `any()` / `anyString()` / `anyInt()` / `anyList()` | Matches any value of that type, including sometimes null (check docs per-method) |
| `eq(value)` | Matches exactly this value -- needed to mix with other matchers in the same call |
| `isNull()` / `isNotNull()` | Matches null / non-null specifically |
| `argThat(predicate)` | Custom matching logic via a lambda predicate |
| `startsWith("x")`, `contains("x")`, `matches("regex")` | String-specific partial matchers |

# Verifying Interactions

```java
import static org.mockito.Mockito.*;

class VerificationDemoTest {

    @Test
    void verificationExamples() {
        InventoryRepository repo = mock(InventoryRepository.class);

        repo.reduceStock("SKU-1", 2);
        repo.reduceStock("SKU-1", 2);

        verify(repo, times(2)).reduceStock("SKU-1", 2);   // called exactly twice
        verify(repo, atLeastOnce()).reduceStock("SKU-1", 2);
        verify(repo, atLeast(1)).reduceStock("SKU-1", 2);
        verify(repo, atMost(5)).reduceStock("SKU-1", 2);
        verify(repo, never()).reduceStock("SKU-2", 1);     // was NEVER called with these args

        verifyNoMoreInteractions(repo);      // fails if repo had ANY calls not already verified above
        // verifyNoInteractions(otherMock);   // fails if otherMock was touched AT ALL
    }

    interface InventoryRepository {
        void reduceStock(String sku, int quantity);
    }
}
```

--> **`verify()` is about behavior/interactions**, distinct from `assertEquals`-style assertions which check RESULTS. Use `verify()` specifically when the thing worth confirming is "did my code call this collaborator correctly" -- e.g. confirming an email service's `send()` was actually invoked, since checking a return value alone wouldn't prove that.
--> **Don't over-verify.** Verifying every single mock interaction in every test makes tests brittle -- they break on harmless refactors that don't change observable behavior. Verify the interactions that matter to the behavior being tested, not incidental implementation details.

# Argument Captors -- Inspecting What Was Actually Passed

```java
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class ArgumentCaptorDemoTest {

    @Test
    void capturesTheActualArgumentPassedToTheMock() {
        EmailService emailService = mock(EmailService.class);
        NotificationSender sender = new NotificationSender(emailService);

        sender.notifyUser("ada@example.com", "Ada Lovelace");

        ArgumentCaptor<Email> captor = ArgumentCaptor.forClass(Email.class);
        verify(emailService).send(captor.capture());

        Email sentEmail = captor.getValue();
        assertEquals("ada@example.com", sentEmail.to());
        assertTrue(sentEmail.body().contains("Ada Lovelace"));
    }

    record Email(String to, String body) {}

    interface EmailService {
        void send(Email email);
    }

    static class NotificationSender {
        private final EmailService emailService;
        NotificationSender(EmailService emailService) { this.emailService = emailService; }
        void notifyUser(String address, String name) {
            emailService.send(new Email(address, "Hello, " + name + "!"));
        }
    }
}
```

--> Use an `ArgumentCaptor` when a matcher isn't enough -- specifically when you need to assert on the INTERNAL STRUCTURE of a complex object that was passed to a mock, rather than just confirming a call happened with roughly-matching arguments.

# Spies -- Partial Mocking of Real Objects

```java
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;

class SpyDemoTest {

    @Test
    void spyWrapsARealObject() {
        List<String> realList = new ArrayList<>();
        List<String> spyList = spy(realList);

        spyList.add("real call");           // actually executes on the real ArrayList
        assertEquals(1, spyList.size());     // reflects the real state -- "real call" is genuinely there

        // Selectively override just one method's behavior
        doReturn(999).when(spyList).size();
        assertEquals(999, spyList.size());   // now stubbed, even though the list itself still has 1 element

        verify(spyList).add("real call");     // spies support verification just like mocks
    }
}
```

--> **`spy()` vs `mock()`** -- `mock()` creates an entirely fake object where every method returns defaults unless stubbed; `spy()` wraps a REAL object where every method calls through to the real implementation UNLESS explicitly stubbed. Spies are useful for testing legacy code or third-party classes where you want mostly-real behavior with one or two methods intercepted.
--> **Use `doReturn().when()` rather than `when().thenReturn()` when stubbing a spy** -- `when(spyList.size())` would actually EXECUTE the real `size()` call first (since it's a real object) before Mockito even gets to stub it, which can have side effects; `doReturn()` avoids calling the real method at all.

# Common Testing Patterns

## Arrange-Act-Assert (AAA)

```java
@Test
void withdrawingMoreThanBalanceThrows() {
    // Arrange -- set up the object under test and its mocked collaborators
    var account = new BankAccount(100.0);

    // Act -- perform the single action being tested
    Executable action = () -> account.withdraw(150.0);

    // Assert -- verify the outcome
    assertThrows(InsufficientFundsException.class, action);
}
```

--> Structuring every test into these three clearly separated sections (even with a blank line or comment, as above) makes tests easy to scan and keeps each test focused on ONE behavior.

## Given-When-Then (Same Idea, BDD-Flavored Naming)

--> Functionally identical to Arrange-Act-Assert -- just renamed to read more like a specification, common when using `@DisplayName` or BDD-style tools. "Given an account with a $100 balance, when withdrawing $150, then it throws InsufficientFundsException."

# Common Gotchas and Best Practices

--> **Don't mock types you don't own carelessly** (e.g. `ArrayList`, JDK classes) -- mocking third-party or JDK internals often produces brittle tests that break when the library's internal call patterns change, even if its observable behavior doesn't. Prefer mocking interfaces YOUR code defines (`InventoryRepository`, `PaymentGateway`) as clean seams between units.
--> **Don't mock the class under test.** `@InjectMocks` should always be a REAL object -- mocking it would replace the very logic you're trying to test with a fake that does nothing.
--> **Avoid over-specifying stubs.** Stubbing methods that are never actually called by the test's `Act` step is dead configuration that clutters the test and can hide `UnnecessaryStubbingException` warnings (Mockito's "strict stubs" mode flags stubs that were never used, which is usually a sign the test doesn't need that stub, or worse, is testing the wrong thing).
--> **`@ExtendWith(MockitoExtension.class)` is required** for `@Mock`/`@InjectMocks` to actually get initialized under JUnit 5 -- forgetting it silently leaves those fields `null`, producing confusing `NullPointerException`s that have nothing obviously to do with a missing annotation.
--> **Mocks reset between tests automatically** when created fresh via `@Mock` (a new instance per test method, same as JUnit's own per-test isolation) -- don't manually try to "reset" a mock's stubbing state; just let each test set up what it needs in its own `Arrange` step.
--> **A test with heavy mocking of many collaborators is often a design smell**, not just a testing inconvenience -- if a class needs five mocks to test one method, it may be doing too much and could benefit from being split into smaller, more focused classes.
