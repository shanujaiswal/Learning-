# Why Architecture Patterns Matter

--> SOLID and GoF patterns (covered in the OOP module) govern how individual CLASSES and INTERFACES relate to each other. Architecture patterns operate one level up: they govern how entire LAYERS, MODULES, and SERVICES relate to each other -- where business logic physically lives, which parts of the codebase are allowed to depend on which other parts, and how a system stays maintainable when it has dozens of classes, several teams, and a lifespan measured in years rather than weeks. This file and the ones that follow it are about the SHAPE of a whole application, not the shape of a single class.
--> A recurring theme across every pattern in this module is the **dependency direction**. Small, class-level coupling problems (a class doing too much, a class depending on a concrete type it shouldn't) are usually a local fix. Architecture-level coupling problems (business logic depending on a specific database driver, a web framework leaking into domain code) are much more expensive to fix later, because they are woven through hundreds of files rather than one. Getting the dependency direction right EARLY is the central concern of this entire file.

# Traditional Layered (N-Tier) Architecture

--> The most common way developers organize a non-trivial application is into horizontal LAYERS, each responsible for one kind of concern, stacked so that each layer only calls the layer directly below it.

```text
┌─────────────────────────────────────┐
│   Presentation Layer                  │   -- controllers, REST endpoints, views
├─────────────────────────────────────┤
│   Business / Service Layer            │   -- business rules, orchestration, validation
├─────────────────────────────────────┤
│   Persistence / Data Access Layer      │   -- repositories, DAOs, ORM mappings
├─────────────────────────────────────┤
│   Database                             │   -- the actual data store
└─────────────────────────────────────┘
```

```java
// A typical layered Spring-style slice -- each class belongs to exactly one layer.

// -- Persistence layer: knows about JPA/JDBC, nothing about HTTP.
interface OrderRepository {
    Order findById(long id);
    void save(Order order);
}

class JpaOrderRepository implements OrderRepository {
    @Override public Order findById(long id) { /* SELECT ... */ return new Order(id); }
    @Override public void save(Order order) { /* INSERT/UPDATE ... */ }
}

// -- Business layer: knows about business rules, calls the repository, knows nothing about HTTP.
class OrderService {
    private final OrderRepository repository;
    OrderService(OrderRepository repository) { this.repository = repository; }

    Order placeOrder(long customerId, double amount) {
        if (amount <= 0) throw new IllegalArgumentException("Order amount must be positive");
        Order order = new Order(customerId, amount);
        repository.save(order);
        return order;
    }
}

// -- Presentation layer: knows about HTTP, delegates all real work downward to the service.
class OrderController {
    private final OrderService service;
    OrderController(OrderService service) { this.service = service; }

    Order handlePlaceOrder(long customerId, double amount) {   // stands in for a @PostMapping handler
        return service.placeOrder(customerId, amount);
    }
}

class Order {
    final long customerId; final double amount;
    Order(long customerId, double amount) { this.customerId = customerId; this.amount = amount; }
    Order(long id) { this.customerId = id; this.amount = 0; }
}
```

--> **The rule that makes this work**: a layer may only call DOWNWARD, into the layer immediately beneath it -- Presentation calls Business, Business calls Persistence, and never the reverse. This is easy to reason about, maps cleanly onto typical team structure (a "backend team" owns the middle layers, a "frontend/API team" owns Presentation), and is genuinely sufficient for a great many applications, especially smaller ones with a single, uncontested set of business rules.
--> **Where it breaks down**: the Business layer, which is supposed to hold the valuable, stable business logic, ends up depending DOWNWARD on the Persistence layer -- which means business logic is coupled to a specific database technology, an ORM, or a specific schema shape. Swapping the database, unit-testing business rules without spinning up a real database, or reusing the business logic behind a different kind of entrypoint (a message queue consumer instead of a REST controller) all become harder than they should be, because the most important code in the system depends on the least stable, most replaceable part of it.

# The Dependency Rule

--> This observation -- that the MOST important, MOST stable code (business rules) should never depend on the LEAST important, LEAST stable code (frameworks, databases, UI) -- is formalized as **the Dependency Rule**: *source code dependencies must point only inward, toward higher-level policy.* Nothing in an inner circle can know anything at all about an outer circle. This single rule is the organizing idea behind both Clean Architecture (this file) and Hexagonal Architecture (file 02) -- they differ mostly in vocabulary and exact circle count, not in the underlying rule.

```text
Naive layered dependency direction (the problem):

  Presentation ──depends on──> Business ──depends on──> Persistence
                                   ^
                         the MOST important code
                         depends on the LEAST stable code

Dependency Rule (the fix):

  Presentation ──depends on──> Business <──depends on── Persistence
                                   ^
                    everything points INWARD toward business rules;
                    Persistence depends on an ABSTRACTION business rules define
```

--> **The mechanism is Dependency Inversion (DIP), applied at the architecture level rather than the class level.** The business layer defines an interface it needs (e.g. `OrderRepository`), and the persistence layer provides a concrete implementation of that interface. The business layer still calls `OrderRepository`, but now the SOURCE CODE dependency arrow points the other way -- persistence depends on (implements) an abstraction owned by the business layer, rather than business code depending on a concrete persistence class. Nothing about the RUNTIME call sequence changes; only which code is allowed to `import` which other code changes.

# Clean Architecture's Concentric Circles

--> Robert C. Martin's Clean Architecture packages the Dependency Rule into four named concentric rings. Each ring may only depend on rings STRICTLY INSIDE it; nothing on the inside may ever import, reference, or know about anything on the outside.

```text
                ┌─────────────────────────────────────────┐
                │        Frameworks & Drivers                │  outermost
                │   (Spring, JPA, JDBC, React, HTTP, files)     │
                │   ┌───────────────────────────────────┐    │
                │   │     Interface Adapters                │    │
                │   │  (controllers, presenters, gateways,    │    │
                │   │   repository implementations, DTOs)      │    │
                │   │   ┌───────────────────────────┐    │    │
                │   │   │      Use Cases                │    │    │
                │   │   │ (application-specific        │    │    │
                │   │   │  business rules /            │    │    │
                │   │   │  interactors)                 │    │    │
                │   │   │   ┌───────────────────┐    │    │    │
                │   │   │   │    Entities           │    │    │    │
                │   │   │   │ (enterprise-wide       │    │    │    │
                │   │   │   │  business rules)        │    │    │    │
                │   │   │   └───────────────────┘    │    │    │
                │   │   └───────────────────────────┘    │    │
                │   └───────────────────────────────────┘    │
                └─────────────────────────────────────────┘
                                                            innermost

           All arrows point INWARD, toward Entities. Nothing inside
           ever imports anything from a ring drawn further outside it.
```

### Entities (innermost)

--> Encapsulate **enterprise-wide** business rules -- the rules that would still be true even if this particular application didn't exist (e.g. "an order's total can never be negative", "an interest rate calculation for a loan"). Entities are the most stable code in the system: the ones least likely to change when a screen is redesigned, a framework is upgraded, or a database is swapped. They have zero knowledge of use cases, frameworks, HTTP, or persistence.

```java
// Entity -- pure business rule, zero framework/persistence/HTTP knowledge.
final class Money {
    private final long cents;
    Money(long cents) {
        if (cents < 0) throw new IllegalArgumentException("Money cannot be negative");
        this.cents = cents;
    }
    Money add(Money other) { return new Money(this.cents + other.cents); }
    long cents() { return cents; }
}
```

### Use Cases (application business rules)

--> Encapsulate **application-specific** business rules -- the orchestration logic unique to THIS application (e.g. "when a customer places an order, check inventory, reserve stock, charge the card, then schedule shipping"). Use cases coordinate entities to accomplish a specific task, and they define the interfaces ("ports" -- see file 02) that outer rings must implement, such as a repository interface or a payment-gateway interface. Use cases know about entities; they know NOTHING about how data is persisted or how a request arrived.

```java
// Use case -- orchestrates entities to fulfil one specific application task.
// It DEFINES the OrderRepository interface; it does not know or care how it's implemented.
interface OrderRepository {
    void save(PlacedOrder order);
}

final class PlacedOrder {
    final String customerId; final Money total;
    PlacedOrder(String customerId, Money total) { this.customerId = customerId; this.total = total; }
}

class PlaceOrderUseCase {
    private final OrderRepository repository;   // an ABSTRACTION owned by this ring, not the outer ring
    PlaceOrderUseCase(OrderRepository repository) { this.repository = repository; }

    PlacedOrder execute(String customerId, long amountCents) {
        Money total = new Money(amountCents);     // uses the Entity to enforce its own invariant
        PlacedOrder order = new PlacedOrder(customerId, total);
        repository.save(order);
        return order;
    }
}
```

### Interface Adapters

--> Convert data between the format most convenient for use cases/entities and the format most convenient for external agencies (a web framework, a database driver). This ring contains controllers, presenters, DTOs/view-models, and the concrete implementations of the repository interfaces the use-case ring defined. Note the inversion: `JpaOrderRepository` lives OUT HERE, in the adapters ring, and implements the `OrderRepository` interface that lives IN the use-case ring.

```java
// Interface adapter -- implements the use-case-owned interface, but lives OUTSIDE it,
// and is the only piece of code here allowed to know about JPA/JDBC.
class JpaOrderRepository implements OrderRepository {
    @Override public void save(PlacedOrder order) {
        // JPA/JDBC-specific mapping and SQL would live here.
        System.out.println("INSERT INTO orders (customer_id, total_cents) VALUES ("
                + order.customerId + ", " + order.total.cents() + ")");
    }
}

// Interface adapter -- a controller that adapts an HTTP request into a use-case call,
// and adapts the use-case's result back into an HTTP-shaped response.
class OrderController {
    private final PlaceOrderUseCase useCase;
    OrderController(PlaceOrderUseCase useCase) { this.useCase = useCase; }

    String handlePost(String customerId, long amountCents) {   // stands in for a REST handler
        PlacedOrder order = useCase.execute(customerId, amountCents);
        return "{\"customerId\":\"" + order.customerId + "\",\"totalCents\":" + order.total.cents() + "}";
    }
}
```

### Frameworks & Drivers (outermost)

--> The messy, volatile details: Spring Boot itself, the JPA/Hibernate runtime, the actual JDBC driver, web server configuration, dependency-injection wiring, `main()`. This ring is deliberately kept as thin as possible -- mostly glue code and configuration -- because it is the ring most likely to change (framework upgrades, migrating from Spring to a different stack) and Clean Architecture's entire point is to make that change NOT ripple inward.

```java
// Frameworks & Drivers -- composition root. Only place that is allowed to know about
// every concrete class and wire them together; nothing else in the system does this.
class Main {
    public static void main(String[] args) {
        OrderRepository repository = new JpaOrderRepository();       // concrete adapter chosen HERE
        PlaceOrderUseCase useCase = new PlaceOrderUseCase(repository);
        OrderController controller = new OrderController(useCase);
        controller.handlePost("cust-42", 4999);
    }
}
```

# Why Dependencies Point Inward

--> **Business rules should be the most stable, most reusable, most testable part of any system** -- they are also, usually, the most expensive and important part to get right, since they encode the actual value the software delivers. Frameworks, databases, and UI technologies are comparatively interchangeable and change far more often over a system's lifetime (a new frontend framework every few years, a database migration, a REST-to-GraphQL rewrite). If business rules depend on those volatile details, every volatile change forces a ripple into the code that matters most. Pointing dependencies inward means the reverse: volatile details depend on stable rules, so volatile changes stay contained to the outer rings.
--> **Testability** falls out of this for free. `PlaceOrderUseCase` above can be fully unit-tested with an in-memory fake `OrderRepository` -- no database, no Spring context, no HTTP server needs to start. Only the thin adapters ring needs integration tests against real infrastructure.
--> **Independent deployability of concerns.** A team can swap `JpaOrderRepository` for a `MongoOrderRepository`, or replace REST controllers with a gRPC adapter, by writing a NEW adapter that implements the SAME use-case-owned interface -- zero changes to `PlaceOrderUseCase` or `Money`. This is Open/Closed at the architecture scale.
--> **Crossing a circle boundary always needs a translation.** Data that crosses an inward boundary (an HTTP request body reaching a use case) is converted into a simple, framework-agnostic shape at the boundary — plain Java objects, primitives, simple DTOs — never handing a framework-specific type (a `HttpServletRequest`, a JPA `@Entity` annotated class) across the line, or the inner ring silently gains a dependency on the outer ring's framework, undoing the whole point of drawing the line at all.

# Layered Architecture vs. Clean Architecture

| Aspect | Traditional Layered | Clean Architecture |
|---|---|---|
| Organizing principle | horizontal technical layers (Presentation/Business/Persistence) | concentric rings ordered by STABILITY, not technical role |
| Typical dependency direction | top-to-bottom (Presentation → Business → Persistence) | always INWARD (outer rings depend on inner rings) |
| Business logic's relationship to DB | often directly coupled to persistence layer | persistence depends on an interface business logic owns; DB is a swappable detail |
| Where interfaces for persistence live | usually alongside/inside the persistence layer | owned by the use-case ring; implemented by the outer adapters ring |
| Testing business logic | frequently needs a real/test database | pure unit tests with fakes; no framework or DB required |
| Learning curve / ceremony | low -- maps directly onto familiar MVC-ish thinking | higher -- more interfaces, more indirection, a real up-front cost |
| Best fit | small-to-medium apps, simple CRUD, short-lived projects | complex domain logic, long-lived systems, multiple delivery mechanisms |

# Common Gotchas and Best Practices

--> **Treating "layered" and "the Dependency Rule" as the same thing.** A codebase can be organized into layers/packages named `controller`, `service`, `repository` and STILL violate the Dependency Rule if the service layer imports JPA entity classes or throws `SQLException`. Package naming is not architecture; the direction of actual `import` statements is.
--> **Over-applying Clean Architecture's full four-ring ceremony to a small CRUD app.** A simple admin tool with no meaningful business rules gains little from four rings of indirection and mostly gains boilerplate. Clean Architecture earns its cost when business logic is genuinely complex, long-lived, or needs to be reused/tested independently of its delivery mechanism -- not by default on every project.
--> **Anemic use cases that just forward to a repository.** If a "use case" class does nothing but call one repository method and return the result, it may not be pulling its weight as a separate ring -- but be cautious collapsing this prematurely, since new business rules (validation, orchestration across multiple repositories) tend to arrive later and are far easier to add to an existing use-case class than to retrofit once controllers are calling repositories directly.
--> **Leaking framework types across the boundary.** Returning a JPA `@Entity`-annotated class straight out of a use case, or accepting a `HttpServletRequest` as a use-case parameter, silently reintroduces the exact coupling Clean Architecture is trying to eliminate -- convert at the boundary, every time, even when it feels like "just one more DTO".
--> **Forgetting the composition root.** Somewhere, concrete implementations DO have to be chosen and wired together -- that is unavoidable and fine. The discipline is confining that wiring to one clearly-identified place (a `main` method, a Spring `@Configuration` class) rather than letting `new ConcreteAdapter()` calls scatter through inner-ring code.
--> **Confusing "Entity" here with a JPA `@Entity`.** Clean Architecture's "Entity" ring means an enterprise business object; JPA's `@Entity` annotation means a persistence-mapped class. Conflating the two is exactly the kind of leak the pattern exists to prevent -- a Clean Architecture Entity should have no persistence annotations on it at all.
