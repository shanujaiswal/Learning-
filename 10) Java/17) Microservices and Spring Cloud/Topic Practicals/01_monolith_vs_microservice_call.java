/**
 * 01_monolith_vs_microservice_call.java
 *
 * Demonstrates, with illustrative Spring Boot code:
 *     1. The SAME business operation ("does this order have enough stock?") implemented
 *        two ways: (a) a monolith-style in-process module call, (b) a microservice-style
 *        REST call over the network -- side by side, so the contrast in chapter 01 is
 *        visible in actual code, not just prose.
 *     2. A simple domain-boundary example splitting Order / Inventory / Payment into
 *        separate conceptual services, each owning its own data and a one-sentence
 *        responsibility (per the "bounded context" guidance in the Theory chapter).
 *
 * Covers Theory chapter:
 *     17) Microservices and Spring Cloud/Theory/01 Microservices Fundamentals.md
 *
 * IMPORTANT -- this file is illustrative, NOT a standalone runnable program.
 * The "microservice" half requires real, separately-deployed Spring Boot services and
 * a working HTTP client (RestTemplate/WebClient/Feign -- see chapter 03's practical file)
 * to actually run. Specifically it assumes:
 *     - spring-boot-starter-web                          (for RestTemplate / REST controllers)
 *     - a real "inventory-service" process listening on its own port (or resolvable via
 *       service discovery, chapter 02) -- there is NO such service actually running here.
 *
 * This file compiles as a mental model / teaching artifact, not as a working demo --
 * drop the relevant classes into real, separate Spring Boot projects to see it run.
 */

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

// =============================================================================
// PART A -- THE MONOLITH WAY
//
// In a monolith, "Order" and "Inventory" are just two packages/modules in the
// SAME running process. Checking stock is a plain Java method call: no network,
// no serialization, no partial failure mode beyond a normal exception. Both
// modules typically share ONE database and can be wrapped in a single
// @Transactional boundary.
// =============================================================================

/**
 * Monolith-style "Inventory" module -- just a regular Spring bean living in the
 * same application as OrderModule below. No HTTP involved at all.
 */
@Service
class InventoryModule {

    // Stands in for a real database-backed repository -- irrelevant to the point
    // being illustrated, which is HOW OrderModule reaches this data, not how it's stored.
    private final Map<String, Integer> stockByProductId = new HashMap<>(Map.of(
            "SKU-1001", 50,
            "SKU-1002", 0
    ));

    /** A plain in-process method -- returns instantly, can throw only "normal" Java exceptions. */
    public int getAvailableStock(String productId) {
        return stockByProductId.getOrDefault(productId, 0);
    }

    public void reserveStock(String productId, int quantity) {
        int current = getAvailableStock(productId);
        if (current < quantity) {
            throw new IllegalStateException("Insufficient stock for " + productId);
        }
        stockByProductId.put(productId, current - quantity);
    }
}

/**
 * Monolith-style "Order" module -- calls InventoryModule directly as a normal
 * injected Java object. This is a single method call: no network round trip,
 * no timeout to configure, no partial-failure handling beyond a try/catch,
 * and (in a real app) both modules typically share one @Transactional
 * database transaction so the whole operation is atomic.
 */
@Service
class OrderModuleMonolithStyle {

    private final InventoryModule inventoryModule;   // in-process dependency, injected by Spring

    public OrderModuleMonolithStyle(InventoryModule inventoryModule) {
        this.inventoryModule = inventoryModule;
    }

    /** Placing an order in a monolith: one direct call, no network involved. */
    public String placeOrder(String productId, int quantity) {
        int available = inventoryModule.getAvailableStock(productId);   // <-- plain method call
        if (available < quantity) {
            return "REJECTED: insufficient stock for " + productId;
        }
        inventoryModule.reserveStock(productId, quantity);               // <-- plain method call, same transaction
        return "CONFIRMED: order placed for " + quantity + " x " + productId;
    }
}

// =============================================================================
// PART B -- THE MICROSERVICE WAY
//
// The SAME operation, but Order and Inventory are now two SEPARATE deployed
// services with their own databases, talking over HTTP. This introduces:
//     - a network hop (latency, and a NEW failure mode: the network call itself
//       can fail even when both services' code is perfectly correct)
//     - serialization/deserialization (DTOs, JSON) instead of passing a live object
//     - no shared transaction -- if the order-confirmation step below failed
//       AFTER inventory was reserved, that's now a partial-failure/consistency
//       problem requiring a saga/compensating-transaction (see chapter 01's
//       Saga Pattern section), NOT a database rollback.
// =============================================================================

/** DTO representing the JSON body returned by the (separately deployed) Inventory Service. */
class StockLevelResponse {
    private String productId;
    private int available;

    public StockLevelResponse() { }   // needed for Jackson deserialization

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public int getAvailable() { return available; }
    public void setAvailable(int available) { this.available = available; }
}

/** DTO sent as the JSON request body for a "reserve stock" call to the Inventory Service. */
class ReserveStockRequest {
    private int quantity;

    public ReserveStockRequest() { }
    public ReserveStockRequest(int quantity) { this.quantity = quantity; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}

/**
 * Microservice-style Order Service -- calls the Inventory Service, a completely
 * separate deployed process, over plain HTTP via RestTemplate (chapter 03 covers
 * RestTemplate/WebClient/Feign in depth; RestTemplate is used here only because
 * it's the simplest to read inline for this specific monolith-vs-microservice contrast).
 *
 * NOTE: "http://inventory-service" below is a DISCOVERY-RESOLVED name (chapter 02),
 * not a literal hostname -- in a real deployment this requires either a
 * @LoadBalanced RestTemplate bean wired to Eureka, or an equivalent mechanism.
 * Neither actually exists in this illustrative file.
 */
@Service
class OrderServiceMicroserviceStyle {

    private final RestTemplate restTemplate;

    public OrderServiceMicroserviceStyle(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public String placeOrder(String productId, int quantity) {
        // 1) A network call replaces the in-process method call from Part A.
        //    This can now fail in ways a monolith call never could: DNS/discovery
        //    failure, connection refused, timeout, the remote service returning
        //    a 5xx, or the JSON body failing to deserialize.
        String stockUrl = "http://inventory-service/api/inventory/" + productId;
        StockLevelResponse stock = restTemplate.getForObject(stockUrl, StockLevelResponse.class);

        if (stock == null || stock.getAvailable() < quantity) {
            return "REJECTED: insufficient stock for " + productId;
        }

        // 2) A SECOND network call to reserve stock. Between step 1 and step 2,
        //    stock could have changed (another order reserved it first) -- a race
        //    condition that simply cannot happen inside one shared-transaction
        //    monolith call, and must be handled explicitly here (e.g. the
        //    Inventory Service itself re-checks availability atomically on its side).
        String reserveUrl = "http://inventory-service/api/inventory/" + productId + "/reserve";
        restTemplate.postForObject(reserveUrl, new ReserveStockRequest(quantity), StockLevelResponse.class);

        // 3) There is NO shared database transaction wrapping steps 1-2 and
        //    "confirm the order" below. If this process crashed between reserving
        //    stock and confirming the order, Inventory would be left with stock
        //    reserved against an order that never got confirmed -- exactly the
        //    kind of inconsistency the Saga Pattern (chapter 01) exists to resolve.
        return "CONFIRMED: order placed for " + quantity + " x " + productId;
    }
}

// =============================================================================
// PART C -- DOMAIN-BOUNDARY EXAMPLE: Order / Inventory / Payment as separate
// conceptual services, each describable in ONE sentence without an "and"
// (per the Theory chapter's "signals you've drawn a boundary well" guidance).
// =============================================================================

/**
 * Conceptually: "Order Service owns order lifecycle -- create, cancel, order status."
 * In a real split, this class would live in its OWN deployed application, with its
 * OWN database, calling Inventory/Payment only over the network (Part B style),
 * never sharing their tables directly.
 */
class OrderServiceBoundary {
    // Owns: order id, status (PENDING/CONFIRMED/CANCELLED), line items, timestamps.
    // Does NOT own: stock levels (Inventory's job) or card/payment details (Payment's job).
    private String orderId;
    private String status;

    public OrderServiceBoundary(String orderId) {
        this.orderId = orderId;
        this.status = "PENDING";
    }

    public void confirm() { this.status = "CONFIRMED"; }
    public void cancel() { this.status = "CANCELLED"; }
    public String getStatus() { return status; }
}

/**
 * Conceptually: "Inventory Service owns stock levels and reservations."
 * Would NEVER expose or depend on Order's internal status field, and Order
 * would never directly query Inventory's stock table -- only via its REST API.
 */
class InventoryServiceBoundary {
    private final Map<String, Integer> stock = new HashMap<>();

    public void setStock(String productId, int quantity) { stock.put(productId, quantity); }
    public int getStock(String productId) { return stock.getOrDefault(productId, 0); }
}

/**
 * Conceptually: "Payment Service owns charging, refunds, payment methods."
 * Deliberately knows NOTHING about products or stock -- only amounts and payment
 * references. Mixing payment logic into Order or Inventory would be a boundary smell.
 */
class PaymentServiceBoundary {
    public String charge(String orderId, BigDecimal amount) {
        // Illustrative only -- a real implementation would call out to a payment
        // processor and return a transaction reference, handle declines, etc.
        return "PAYMENT-REF-" + orderId + "-" + amount;
    }
}
