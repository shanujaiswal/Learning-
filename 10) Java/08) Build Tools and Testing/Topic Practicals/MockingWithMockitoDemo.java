/*
 * MockingWithMockitoDemo.java
 *
 * Covers Theory chapter:
 *     08) Build Tools and Testing/Theory/04 Mocking with Mockito.md
 *
 * IMPORTANT: This file demonstrates a service class (OrderService) with two collaborators
 * (InventoryRepository, PaymentGateway) alongside a full Mockito + JUnit 5 test class
 * (OrderServiceTest) written with real imports/annotations -- @ExtendWith(MockitoExtension.class),
 * @Mock, @InjectMocks, when(...).thenReturn(...), argument matchers, verify(...), and an
 * ArgumentCaptor example. The test class is kept COMMENTED OUT because neither JUnit 5 nor
 * Mockito (org.mockito:mockito-core / mockito-junit-jupiter) are actual dependencies available
 * in this environment -- there is no pom.xml/build.gradle here to pull them in, so it will NOT
 * compile with plain javac. It is included in full, with real import statements and syntax,
 * purely for study purposes; see Theory file 04 for the full Mockito reference.
 *
 * A real project would split this into:
 *   src/main/java/com/example/app/OrderService.java (+ InventoryRepository.java, PaymentGateway.java)
 *   src/test/java/com/example/app/OrderServiceTest.java
 * with mockito-core and mockito-junit-jupiter added as test-scoped dependencies:
 *
 *   <dependency>
 *       <groupId>org.mockito</groupId>
 *       <artifactId>mockito-core</artifactId>
 *       <version>5.12.0</version>
 *       <scope>test</scope>
 *   </dependency>
 *   <dependency>
 *       <groupId>org.mockito</groupId>
 *       <artifactId>mockito-junit-jupiter</artifactId>
 *       <version>5.12.0</version>
 *       <scope>test</scope>
 *   </dependency>
 *
 * To make this SINGLE FILE still compile/run standalone with plain javac/java (no build tool, no
 * Mockito on the classpath), hand-rolled FAKE implementations of the two collaborator interfaces
 * are provided further below (simple in-memory stand-ins, NOT real Mockito mocks), and the runnable
 * main() exercises OrderService through those instead -- standing in for what the commented-out
 * real Mockito test class would verify automatically via @Mock/@InjectMocks/verify(...).
 * ---------------------------------------------------------------------------------------------
 */

// =====================================================================================
// These interfaces + OrderService represent what would live at:
//   src/main/java/com/example/app/InventoryRepository.java
//   src/main/java/com/example/app/PaymentGateway.java
//   src/main/java/com/example/app/OrderService.java
// =====================================================================================
interface InventoryRepository {
    boolean hasStock(String sku, int quantity);
    void reduceStock(String sku, int quantity);
}

interface PaymentGateway {
    boolean charge(String cardToken, double amount);
}

// The class under test: it COLLABORATES with the two interfaces above rather than doing
// inventory/payment work itself. This is the seam that makes it mockable in tests -- a unit
// test can swap in fakes for both collaborators without touching any real DB or payment API.
class OrderService {

    private final InventoryRepository inventoryRepository;
    private final PaymentGateway paymentGateway;

    // Constructor injection -- this is also exactly the shape @InjectMocks looks for first
    // when wiring @Mock-annotated fields into a real OrderService instance under test.
    OrderService(InventoryRepository inventoryRepository, PaymentGateway paymentGateway) {
        this.inventoryRepository = inventoryRepository;
        this.paymentGateway = paymentGateway;
    }

    boolean placeOrder(String sku, int quantity, double amount, String cardToken) {
        if (!inventoryRepository.hasStock(sku, quantity)) {
            return false;
        }
        boolean charged = paymentGateway.charge(cardToken, amount);
        if (!charged) {
            return false;
        }
        inventoryRepository.reduceStock(sku, quantity);
        return true;
    }
}

// =====================================================================================
// This class represents what would live at: src/test/java/com/example/app/OrderServiceTest.java
//
// Requires mockito-core + mockito-junit-jupiter (see block comment above) to actually compile/run.
// Run via:  mvn test           (Maven, using maven-surefire-plugin)
//           ./gradlew test     (Gradle, with test { useJUnitPlatform() } configured)
// =====================================================================================
/*
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)   // enables @Mock/@InjectMocks processing for this test class
class OrderServiceTest {

    @Mock                              // Mockito creates a fake InventoryRepository automatically --
    private InventoryRepository inventoryRepository;   // every method returns defaults until stubbed

    @Mock
    private PaymentGateway paymentGateway;

    @InjectMocks                       // Mockito creates a REAL OrderService and injects the two mocks
    private OrderService orderService;  // above into its constructor automatically

    // ----------------------------------------------------------------------------
    // Stubbing behavior with when(...).thenReturn(...)
    // ----------------------------------------------------------------------------

    @Test
    void placingAnOrderChargesPaymentAndReducesStock() {
        // Arrange -- program both mocked collaborators
        when(inventoryRepository.hasStock("SKU-1", 2)).thenReturn(true);
        when(paymentGateway.charge(anyString(), eq(49.98))).thenReturn(true);

        // Act
        boolean success = orderService.placeOrder("SKU-1", 2, 49.98, "card-123");

        // Assert -- check the RESULT...
        assertTrue(success);

        // ...and VERIFY the interactions that matter to this behavior
        verify(inventoryRepository).reduceStock("SKU-1", 2);
        verify(paymentGateway).charge("card-123", 49.98);
    }

    @Test
    void orderFailsWhenInsufficientStock() {
        when(inventoryRepository.hasStock("SKU-1", 100)).thenReturn(false);

        boolean success = orderService.placeOrder("SKU-1", 100, 49.98, "card-123");

        assertFalse(success);
        // Payment must never be attempted if stock check fails -- verify it was NEVER called.
        verify(paymentGateway, never()).charge(anyString(), anyDouble());
        verify(inventoryRepository, never()).reduceStock(anyString(), anyInt());
    }

    @Test
    void orderFailsWhenPaymentDeclined() {
        when(inventoryRepository.hasStock("SKU-1", 2)).thenReturn(true);
        when(paymentGateway.charge(anyString(), anyDouble())).thenReturn(false);   // declined

        boolean success = orderService.placeOrder("SKU-1", 2, 49.98, "card-123");

        assertFalse(success);
        // Stock must never be reduced if payment failed.
        verify(inventoryRepository, never()).reduceStock(anyString(), anyInt());
    }

    // ----------------------------------------------------------------------------
    // Argument matchers -- once ANY matcher is used in a call, ALL args in that call
    // must use matchers (cannot mix a raw literal with a matcher in the same call).
    // ----------------------------------------------------------------------------

    @Test
    void matchersDemonstrateExactVsAnyMatching() {
        when(inventoryRepository.hasStock(eq("SKU-1"), eq(2))).thenReturn(true);   // exact match
        when(paymentGateway.charge(startsWith("card-"), gt(0.0))).thenReturn(true); // partial match

        boolean success = orderService.placeOrder("SKU-1", 2, 10.0, "card-999");

        assertTrue(success);
    }

    // ----------------------------------------------------------------------------
    // Verifying call counts
    // ----------------------------------------------------------------------------

    @Test
    void reduceStockCalledExactlyOncePerOrder() {
        when(inventoryRepository.hasStock("SKU-1", 1)).thenReturn(true);
        when(paymentGateway.charge(anyString(), anyDouble())).thenReturn(true);

        orderService.placeOrder("SKU-1", 1, 9.99, "card-123");

        verify(inventoryRepository, times(1)).reduceStock("SKU-1", 1);
        verify(inventoryRepository, atLeastOnce()).hasStock("SKU-1", 1);
    }

    // ----------------------------------------------------------------------------
    // ArgumentCaptor -- inspecting exactly what was passed to a mock, useful when a
    // matcher alone isn't enough (e.g. asserting on fields of a complex object).
    // ----------------------------------------------------------------------------

    @Captor
    private ArgumentCaptor<Double> amountCaptor;

    @Test
    void capturesTheExactAmountChargedToPaymentGateway() {
        when(inventoryRepository.hasStock("SKU-1", 3)).thenReturn(true);
        when(paymentGateway.charge(anyString(), anyDouble())).thenReturn(true);

        orderService.placeOrder("SKU-1", 3, 29.97, "card-123");

        verify(paymentGateway).charge(eq("card-123"), amountCaptor.capture());
        assertEquals(29.97, amountCaptor.getValue(), 0.001);
    }
}
*/

// =====================================================================================
// Hand-rolled FAKE implementations (NOT real Mockito mocks) so this file can compile/run
// standalone with plain javac/java, without Mockito on the classpath. Each fake records the
// calls made to it (a poor-man's substitute for Mockito's verify(...)) so main() below can
// print what was actually invoked -- standing in for what the commented-out real Mockito test
// class would verify automatically.
// =====================================================================================
class FakeInventoryRepository implements InventoryRepository {

    private final java.util.Map<String, Integer> stock = new java.util.HashMap<>();
    java.util.List<String> reduceStockCalls = new java.util.ArrayList<>();

    void seedStock(String sku, int quantity) {
        stock.put(sku, quantity);
    }

    @Override
    public boolean hasStock(String sku, int quantity) {
        return stock.getOrDefault(sku, 0) >= quantity;
    }

    @Override
    public void reduceStock(String sku, int quantity) {
        reduceStockCalls.add(sku + ":" + quantity);
        stock.merge(sku, -quantity, Integer::sum);
    }
}

class FakePaymentGateway implements PaymentGateway {

    // "Programmed" outcome, standing in for Mockito's when(...).thenReturn(...)
    private final boolean shouldSucceed;
    java.util.List<String> chargeCalls = new java.util.ArrayList<>();

    FakePaymentGateway(boolean shouldSucceed) {
        this.shouldSucceed = shouldSucceed;
    }

    @Override
    public boolean charge(String cardToken, double amount) {
        chargeCalls.add(cardToken + ":" + amount);
        return shouldSucceed;
    }
}

// =====================================================================================
// Runnable entry point so this single file can still be compiled/run directly with plain javac/java
// for study purposes, independent of any build tool or Mockito on the classpath -- exercises
// OrderService manually via the hand-rolled fakes above, standing in for what the (commented-out)
// real Mockito test class would verify automatically via @Mock/@InjectMocks/verify(...).
// =====================================================================================
public class MockingWithMockitoDemo {
    public static void main(String[] args) {

        // --- Scenario 1: successful order (stock available + payment succeeds) ---
        FakeInventoryRepository inventory1 = new FakeInventoryRepository();
        inventory1.seedStock("SKU-1", 10);
        FakePaymentGateway payment1 = new FakePaymentGateway(true);
        OrderService orderService1 = new OrderService(inventory1, payment1);

        boolean result1 = orderService1.placeOrder("SKU-1", 2, 49.98, "card-123");
        System.out.println("Scenario 1 (should succeed): " + result1);
        System.out.println("  charge() calls recorded: " + payment1.chargeCalls);
        System.out.println("  reduceStock() calls recorded: " + inventory1.reduceStockCalls);

        // --- Scenario 2: insufficient stock -- payment must never be attempted ---
        FakeInventoryRepository inventory2 = new FakeInventoryRepository();
        inventory2.seedStock("SKU-1", 1);   // only 1 in stock, ordering 100
        FakePaymentGateway payment2 = new FakePaymentGateway(true);
        OrderService orderService2 = new OrderService(inventory2, payment2);

        boolean result2 = orderService2.placeOrder("SKU-1", 100, 49.98, "card-123");
        System.out.println("\nScenario 2 (should fail, insufficient stock): " + result2);
        System.out.println("  charge() calls recorded (expect empty): " + payment2.chargeCalls);

        // --- Scenario 3: payment declined -- stock must never be reduced ---
        FakeInventoryRepository inventory3 = new FakeInventoryRepository();
        inventory3.seedStock("SKU-1", 10);
        FakePaymentGateway payment3 = new FakePaymentGateway(false);   // simulates a decline
        OrderService orderService3 = new OrderService(inventory3, payment3);

        boolean result3 = orderService3.placeOrder("SKU-1", 2, 49.98, "card-123");
        System.out.println("\nScenario 3 (should fail, payment declined): " + result3);
        System.out.println("  reduceStock() calls recorded (expect empty): " + inventory3.reduceStockCalls);

        System.out.println("\nIn a real project with Mockito + JUnit 5 on the classpath:");
        System.out.println("  @Mock/@InjectMocks replace the hand-rolled Fake* classes above");
        System.out.println("  when(...).thenReturn(...) replaces the FakePaymentGateway constructor flag");
        System.out.println("  verify(mock).method(args) replaces manually inspecting *Calls lists above");
        System.out.println("  ArgumentCaptor replaces reading the raw String entries out of chargeCalls");
    }
}
