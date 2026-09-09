/*
 * HexagonalArchitectureDemo.java
 *
 * Demonstrates:
 *   1. An Application Core (domain + use case) that defines both a PRIMARY (driving) port
 *      and SECONDARY (driven) ports -- the core never depends on any adapter.
 *   2. TWO primary (driving) adapters -- a CLI-style adapter and a "test harness" adapter --
 *      both calling INTO the exact same primary port with zero changes to the core.
 *   3. TWO secondary (driven) adapters -- an in-memory repository and a "slow/external"
 *      repository -- both satisfying the exact same secondary port, swappable freely.
 *   4. Swapping adapters (both primary and secondary) without touching the application core,
 *      which is the central practical payoff of Hexagonal Architecture / Ports & Adapters.
 *
 * Covers Theory chapter:
 *   10) Java/02) Object-Oriented Programming in Java/Theory/10 Hexagonal Architecture Ports and Adapters.md
 *
 * Compile:  javac 02_HexagonalArchitectureDemo.java
 * Run:      java HexagonalArchitectureDemo
 */

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

// =============================================================================
// APPLICATION CORE -- domain model, use cases, and the ports they offer/need.
// Nothing in this section imports anything from an adapter class below.
// =============================================================================

/** Domain object living inside the hexagon. */
class Order {
    private final String id;
    private final String customerId;
    private final long amountCents;

    Order(String id, String customerId, long amountCents) {
        this.id = id;
        this.customerId = customerId;
        this.amountCents = amountCents;
    }

    String id() { return id; }
    String customerId() { return customerId; }
    long amountCents() { return amountCents; }
}

/** Simple confirmation value returned across the primary port boundary. */
record OrderConfirmation(String orderId, boolean paymentCharged) { }

// --- PRIMARY (driving) port: describes what the application OFFERS to the outside world. ---
interface PlaceOrderPort {
    OrderConfirmation placeOrder(String customerId, long amountCents);
}

// --- SECONDARY (driven) ports: describe what the application NEEDS from its environment. ---
interface OrderRepositoryPort {
    void save(Order order);
    Optional<Order> findById(String orderId);
}

interface PaymentGatewayPort {
    boolean charge(String customerId, long amountCents);
}

/**
 * The application core's "brain" for this one use case. It implements the PRIMARY port
 * and depends only on the SECONDARY ports (interfaces it does not implement itself) --
 * it knows nothing about HTTP, a CLI, a database driver, or a payment provider's SDK.
 */
class PlaceOrderService implements PlaceOrderPort {
    private final OrderRepositoryPort repository;
    private final PaymentGatewayPort paymentGateway;
    private int nextOrderNumber = 1;

    PlaceOrderService(OrderRepositoryPort repository, PaymentGatewayPort paymentGateway) {
        this.repository = repository;
        this.paymentGateway = paymentGateway;
    }

    @Override
    public OrderConfirmation placeOrder(String customerId, long amountCents) {
        if (amountCents <= 0) throw new IllegalArgumentException("Order amount must be positive");

        boolean charged = paymentGateway.charge(customerId, amountCents);
        if (!charged) throw new IllegalStateException("Payment declined for customer " + customerId);

        String orderId = "ORD-" + (nextOrderNumber++);
        Order order = new Order(orderId, customerId, amountCents);
        repository.save(order);

        return new OrderConfirmation(orderId, true);
    }
}

// =============================================================================
// PRIMARY (DRIVING) ADAPTERS -- sit outside the hexagon, call INTO PlaceOrderPort.
// Two completely different triggers reach the exact same core logic.
// =============================================================================

/** Primary adapter #1 -- stands in for a CLI command translating args into a port call. */
class OrderCliAdapter {
    private final PlaceOrderPort placeOrderPort;

    OrderCliAdapter(PlaceOrderPort placeOrderPort) {
        this.placeOrderPort = placeOrderPort;
    }

    void run(String customerId, long amountCents) {
        System.out.println("  [OrderCliAdapter] parsing CLI args: customerId=" + customerId + " amountCents=" + amountCents);
        OrderConfirmation confirmation = placeOrderPort.placeOrder(customerId, amountCents);
        System.out.println("  [OrderCliAdapter] Order placed: " + confirmation.orderId());
    }
}

/** Primary adapter #2 -- stands in for an HTTP controller translating a request into a port call. */
class OrderHttpAdapter {
    private final PlaceOrderPort placeOrderPort;

    OrderHttpAdapter(PlaceOrderPort placeOrderPort) {
        this.placeOrderPort = placeOrderPort;
    }

    String handlePost(String customerId, long amountCents) {   // stands in for a @PostMapping handler
        OrderConfirmation confirmation = placeOrderPort.placeOrder(customerId, amountCents);
        return "{\"orderId\":\"" + confirmation.orderId() + "\",\"charged\":" + confirmation.paymentCharged() + "}";
    }
}

// =============================================================================
// SECONDARY (DRIVEN) ADAPTERS -- sit outside the hexagon, are CALLED BY the core
// through a secondary port. Two interchangeable implementations of each port.
// =============================================================================

/** Secondary adapter -- in-memory repository, ideal for fast tests, no database required. */
class InMemoryOrderRepositoryAdapter implements OrderRepositoryPort {
    private final Map<String, Order> store = new HashMap<>();

    @Override
    public void save(Order order) {
        store.put(order.id(), order);
        System.out.println("  [InMemoryOrderRepositoryAdapter] stored " + order.id());
    }

    @Override
    public Optional<Order> findById(String orderId) {
        return Optional.ofNullable(store.get(orderId));
    }
}

/** Secondary adapter -- stands in for a slower "real" persistence technology. */
class ExternalOrderRepositoryAdapter implements OrderRepositoryPort {
    private final Map<String, Order> remoteSimulation = new HashMap<>();

    @Override
    public void save(Order order) {
        remoteSimulation.put(order.id(), order);
        System.out.println("  [ExternalOrderRepositoryAdapter] simulated network write for " + order.id());
    }

    @Override
    public Optional<Order> findById(String orderId) {
        return Optional.ofNullable(remoteSimulation.get(orderId));
    }
}

/** Secondary adapter -- a payment gateway that always approves (useful for demos/tests). */
class AlwaysApprovePaymentAdapter implements PaymentGatewayPort {
    @Override
    public boolean charge(String customerId, long amountCents) {
        System.out.println("  [AlwaysApprovePaymentAdapter] approving charge of " + amountCents + " cents for " + customerId);
        return true;
    }
}

/** Secondary adapter -- a payment gateway that declines any charge over a fixed limit. */
class LimitedPaymentAdapter implements PaymentGatewayPort {
    private final long limitCents;

    LimitedPaymentAdapter(long limitCents) {
        this.limitCents = limitCents;
    }

    @Override
    public boolean charge(String customerId, long amountCents) {
        boolean approved = amountCents <= limitCents;
        System.out.println("  [LimitedPaymentAdapter] " + (approved ? "approved" : "DECLINED")
                + " charge of " + amountCents + " cents (limit " + limitCents + ") for " + customerId);
        return approved;
    }
}

// =============================================================================
// Main class -- the composition root: the only place that knows every concrete adapter.
// =============================================================================

public class HexagonalArchitectureDemo {

    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(78));
        System.out.println(title);
        System.out.println("=".repeat(78));
    }

    // -------------------------------------------------------------------------
    // Demo 1: Core wired with in-memory repository + always-approve payment gateway,
    // driven by the CLI primary adapter.
    // -------------------------------------------------------------------------
    private static void demoCliWithInMemoryAdapters() {
        printSection("1) CLI primary adapter -> core -> in-memory repository + always-approve payment");

        OrderRepositoryPort repository = new InMemoryOrderRepositoryAdapter();
        PaymentGatewayPort paymentGateway = new AlwaysApprovePaymentAdapter();
        PlaceOrderPort placeOrderPort = new PlaceOrderService(repository, paymentGateway);

        OrderCliAdapter cli = new OrderCliAdapter(placeOrderPort);
        cli.run("cust-1", 5000);

        assert repository.findById("ORD-1").isPresent();
    }

    // -------------------------------------------------------------------------
    // Demo 2: SAME core class (PlaceOrderService), but driven through the HTTP adapter
    // instead of the CLI adapter -- proves primary adapters are freely swappable.
    // -------------------------------------------------------------------------
    private static void demoHttpWithSameCore() {
        printSection("2) HTTP primary adapter -> the SAME core -> zero core changes");

        OrderRepositoryPort repository = new InMemoryOrderRepositoryAdapter();
        PaymentGatewayPort paymentGateway = new AlwaysApprovePaymentAdapter();
        PlaceOrderPort placeOrderPort = new PlaceOrderService(repository, paymentGateway);

        OrderHttpAdapter http = new OrderHttpAdapter(placeOrderPort);
        String response = http.handlePost("cust-2", 3000);
        System.out.println("  HTTP response: " + response);

        assert response.contains("ORD-1");   // fresh service instance -> its own order-numbering sequence
    }

    // -------------------------------------------------------------------------
    // Demo 3: Swap the SECONDARY adapters (repository + payment gateway) while
    // keeping the SAME primary adapter and the SAME core class.
    // -------------------------------------------------------------------------
    private static void demoSwapSecondaryAdapters() {
        printSection("3) Swap secondary adapters -- external repository + spending-limited payment gateway");

        OrderRepositoryPort repository = new ExternalOrderRepositoryAdapter();      // swapped
        PaymentGatewayPort paymentGateway = new LimitedPaymentAdapter(10_000);        // swapped
        PlaceOrderPort placeOrderPort = new PlaceOrderService(repository, paymentGateway);   // same core class

        OrderCliAdapter cli = new OrderCliAdapter(placeOrderPort);   // same primary adapter class
        cli.run("cust-3", 7500);    // under the limit -> approved

        try {
            cli.run("cust-3", 25_000);   // over the limit -> declined, core throws
        } catch (IllegalStateException e) {
            System.out.println("  Caught expected exception: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Demo 4: The core rejects invalid input regardless of which adapters surround it --
    // the invariant lives in ONE place (the core), not duplicated per adapter.
    // -------------------------------------------------------------------------
    private static void demoCoreInvariantHoldsForEveryAdapterCombination() {
        printSection("4) Core invariant (amount must be positive) holds no matter which adapters are used");

        PlaceOrderPort placeOrderPort = new PlaceOrderService(
                new InMemoryOrderRepositoryAdapter(), new AlwaysApprovePaymentAdapter());

        try {
            placeOrderPort.placeOrder("cust-4", -100);
        } catch (IllegalArgumentException e) {
            System.out.println("  Caught expected exception from the core: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        demoCliWithInMemoryAdapters();
        demoHttpWithSameCore();
        demoSwapSecondaryAdapters();
        demoCoreInvariantHoldsForEveryAdapterCombination();

        System.out.println();
        System.out.println("All Hexagonal Architecture (Ports & Adapters) demos completed.");
    }
}
