/*
 * LayeredVsCleanArchitectureDemo.java
 *
 * Demonstrates:
 *   1. Traditional Layered (N-Tier) Architecture -- Controller -> Service -> Repository,
 *      each layer calling only the layer directly beneath it (top-to-bottom dependency).
 *   2. Clean Architecture -- concentric rings (Entity, Use Case, Interface Adapter) where
 *      the Dependency Rule is enforced: the use-case ring OWNS the repository interface,
 *      and the outer adapter ring implements it -- dependencies point INWARD, not downward.
 *   3. The Dependency Rule made concrete: swapping the Clean Architecture repository
 *      implementation (in-memory vs "database") requires ZERO changes to the use case
 *      or entity, because they never depended on the concrete implementation at all.
 *
 * Class/package names below are prefixed (Layered_ / Clean_) to stand in for what would,
 * in a real multi-module project, be separate Java packages (e.g. layered.controller,
 * clean.usecase, clean.adapter) -- this keeps the whole contrast runnable in one file.
 *
 * Covers Theory chapter:
 *   10) Java/02) Object-Oriented Programming in Java/Theory/09 Layered and Clean Architecture Fundamentals.md
 *
 * Compile:  javac 01_LayeredVsCleanArchitectureDemo.java
 * Run:      java LayeredVsCleanArchitectureDemo
 */

import java.util.HashMap;
import java.util.Map;

// =============================================================================
// PART 1: Traditional Layered (N-Tier) Architecture
// =============================================================================
// Dependency direction: Controller -> Service -> Repository (top calls down, always).
// Notice the Service (business rules) directly depends on a CONCRETE repository type
// below it -- this is the "breaks down" case the theory file warns about: the most
// important code (business rules) is coupled to the least stable code (persistence).
// =============================================================================

/** Layered persistence layer -- knows about "storage", nothing about HTTP or business rules. */
class Layered_OrderRepository {
    private final Map<Long, Layered_Order> table = new HashMap<>();
    private long nextId = 1;

    Layered_Order save(Layered_Order order) {
        order.id = nextId++;
        table.put(order.id, order);
        System.out.println("  [Layered_OrderRepository] INSERT order id=" + order.id);
        return order;
    }

    Layered_Order findById(long id) {
        return table.get(id);
    }
}

/** Layered business layer -- calls DOWN into the repository layer directly. */
class Layered_OrderService {
    private final Layered_OrderRepository repository;   // concrete dependency, pointing DOWNWARD

    Layered_OrderService(Layered_OrderRepository repository) {
        this.repository = repository;
    }

    Layered_Order placeOrder(String customerId, double amount) {
        if (amount <= 0) throw new IllegalArgumentException("Order amount must be positive");
        Layered_Order order = new Layered_Order(customerId, amount);
        return repository.save(order);
    }
}

/** Layered presentation layer -- calls DOWN into the service layer. */
class Layered_OrderController {
    private final Layered_OrderService service;

    Layered_OrderController(Layered_OrderService service) {
        this.service = service;
    }

    String handlePlaceOrder(String customerId, double amount) {   // stands in for a REST handler
        Layered_Order order = service.placeOrder(customerId, amount);
        return "{\"orderId\":" + order.id + ",\"customerId\":\"" + order.customerId + "\"}";
    }
}

class Layered_Order {
    long id;
    final String customerId;
    final double amount;

    Layered_Order(String customerId, double amount) {
        this.customerId = customerId;
        this.amount = amount;
    }
}

// =============================================================================
// PART 2: Clean Architecture -- Entities, Use Case, Interface Adapters
// =============================================================================
// Dependency direction: EVERYTHING points INWARD toward the Entity/Use Case ring.
// The use-case ring DEFINES the repository interface (Clean_OrderRepositoryPort);
// the outer adapter ring merely IMPLEMENTS it. Source-code arrows point inward even
// though the runtime call sequence (controller -> use case -> repository) looks the
// same as the layered example above -- that is the entire point of the Dependency Rule.
// =============================================================================

// --- Entity ring (innermost): enterprise-wide business rule, zero framework knowledge. ---
final class Clean_Money {
    private final double amount;

    Clean_Money(double amount) {
        if (amount < 0) throw new IllegalArgumentException("Money cannot be negative");
        this.amount = amount;
    }

    double amount() { return amount; }
}

// --- Use Case ring: orchestrates entities, OWNS the repository abstraction it needs. ---
// This interface is defined here, INSIDE the use-case ring -- not inside the adapter ring.
interface Clean_OrderRepositoryPort {
    Clean_PlacedOrder save(Clean_PlacedOrder order);
}

final class Clean_PlacedOrder {
    long id;   // assigned by the repository implementation, not by the use case
    final String customerId;
    final Clean_Money total;

    Clean_PlacedOrder(String customerId, Clean_Money total) {
        this.customerId = customerId;
        this.total = total;
    }
}

/**
 * Use case -- depends ONLY on the Clean_OrderRepositoryPort abstraction (which it owns)
 * and the Clean_Money entity. It has never heard of HTTP, JDBC, or any concrete adapter.
 */
class Clean_PlaceOrderUseCase {
    private final Clean_OrderRepositoryPort repository;   // abstraction, owned by THIS ring

    Clean_PlaceOrderUseCase(Clean_OrderRepositoryPort repository) {
        this.repository = repository;
    }

    Clean_PlacedOrder execute(String customerId, double amount) {
        Clean_Money total = new Clean_Money(amount);        // Entity enforces its own invariant
        Clean_PlacedOrder order = new Clean_PlacedOrder(customerId, total);
        return repository.save(order);
    }
}

// --- Interface Adapters ring: implements the use-case-owned port, lives OUTSIDE it. ---

/** Secondary adapter #1 -- an in-memory "database". Swappable with zero use-case changes. */
class Clean_InMemoryOrderRepositoryAdapter implements Clean_OrderRepositoryPort {
    private final Map<Long, Clean_PlacedOrder> table = new HashMap<>();
    private long nextId = 1;

    @Override
    public Clean_PlacedOrder save(Clean_PlacedOrder order) {
        order.id = nextId++;
        table.put(order.id, order);
        System.out.println("  [Clean_InMemoryOrderRepositoryAdapter] stored order id=" + order.id);
        return order;
    }
}

/** Secondary adapter #2 -- stands in for a "real database" adapter (e.g. JDBC-backed). */
class Clean_DatabaseOrderRepositoryAdapter implements Clean_OrderRepositoryPort {
    private long nextId = 100;   // pretend IDs come from a different sequence than the in-memory adapter

    @Override
    public Clean_PlacedOrder save(Clean_PlacedOrder order) {
        order.id = nextId++;
        System.out.println("  [Clean_DatabaseOrderRepositoryAdapter] INSERT INTO orders (customer_id, total) VALUES ("
                + order.customerId + ", " + order.total.amount() + ")  -- simulated SQL, id=" + order.id);
        return order;
    }
}

/** Primary adapter -- translates an HTTP-shaped request into a call on the use case. */
class Clean_OrderController {
    private final Clean_PlaceOrderUseCase useCase;

    Clean_OrderController(Clean_PlaceOrderUseCase useCase) {
        this.useCase = useCase;
    }

    String handlePost(String customerId, double amount) {
        Clean_PlacedOrder order = useCase.execute(customerId, amount);
        return "{\"orderId\":" + order.id + ",\"customerId\":\"" + order.customerId + "\"}";
    }
}

// =============================================================================
// Main class
// =============================================================================

public class LayeredVsCleanArchitectureDemo {

    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(78));
        System.out.println(title);
        System.out.println("=".repeat(78));
    }

    // -------------------------------------------------------------------------
    // Demo 1: Traditional Layered Architecture -- top-to-bottom dependency
    // -------------------------------------------------------------------------
    private static void demoLayered() {
        printSection("1) Traditional Layered Architecture -- Controller -> Service -> Repository");

        Layered_OrderRepository repository = new Layered_OrderRepository();
        Layered_OrderService service = new Layered_OrderService(repository);
        Layered_OrderController controller = new Layered_OrderController(service);

        String response = controller.handlePlaceOrder("cust-1", 250.0);
        System.out.println("  Response: " + response);

        assert repository.findById(1L) != null : "order should have been persisted";

        System.out.println("  NOTE: Layered_OrderService imports Layered_OrderRepository directly --");
        System.out.println("        business rules are coupled to a concrete persistence class.");
    }

    // -------------------------------------------------------------------------
    // Demo 2: Clean Architecture -- Dependency Rule, dependencies point INWARD
    // -------------------------------------------------------------------------
    private static void demoCleanWithInMemoryAdapter() {
        printSection("2) Clean Architecture -- wired with the IN-MEMORY secondary adapter");

        Clean_OrderRepositoryPort repository = new Clean_InMemoryOrderRepositoryAdapter();
        Clean_PlaceOrderUseCase useCase = new Clean_PlaceOrderUseCase(repository);
        Clean_OrderController controller = new Clean_OrderController(useCase);

        String response = controller.handlePost("cust-42", 499.0);
        System.out.println("  Response: " + response);

        assert response.contains("cust-42");
    }

    // -------------------------------------------------------------------------
    // Demo 3: Swap the adapter -- ZERO changes to the use case or entity
    // -------------------------------------------------------------------------
    private static void demoCleanSwapAdapter() {
        printSection("3) Dependency Rule proof -- swap to the DATABASE adapter, use case untouched");

        // The only line that changes between this demo and the previous one is the
        // concrete adapter picked here in the composition root. Clean_PlaceOrderUseCase,
        // Clean_OrderController and Clean_Money are reused completely unmodified.
        Clean_OrderRepositoryPort repository = new Clean_DatabaseOrderRepositoryAdapter();
        Clean_PlaceOrderUseCase useCase = new Clean_PlaceOrderUseCase(repository);   // same class as before
        Clean_OrderController controller = new Clean_OrderController(useCase);       // same class as before

        String response = controller.handlePost("cust-42", 750.0);
        System.out.println("  Response: " + response);

        assert response.contains("cust-42");

        System.out.println("  NOTE: Clean_PlaceOrderUseCase never imports either adapter class --");
        System.out.println("        it only ever depends on Clean_OrderRepositoryPort (its own interface).");
    }

    // -------------------------------------------------------------------------
    // Demo 4: Entity invariant is enforced regardless of which adapter is used
    // -------------------------------------------------------------------------
    private static void demoEntityInvariant() {
        printSection("4) Entity invariant -- Clean_Money rejects negative amounts, in EVERY wiring");

        Clean_OrderRepositoryPort repository = new Clean_InMemoryOrderRepositoryAdapter();
        Clean_PlaceOrderUseCase useCase = new Clean_PlaceOrderUseCase(repository);

        try {
            useCase.execute("cust-99", -10.0);
        } catch (IllegalArgumentException e) {
            System.out.println("  Caught expected exception from the Entity ring: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        demoLayered();
        demoCleanWithInMemoryAdapter();
        demoCleanSwapAdapter();
        demoEntityInvariant();

        System.out.println();
        System.out.println("All Layered vs Clean Architecture demos completed.");
    }
}
