# From Clean Architecture to Hexagonal Architecture

--> File 01 established the Dependency Rule: business logic must never depend on frameworks, databases, or delivery mechanisms -- dependencies point inward, toward stable business rules. **Hexagonal Architecture** (Alistair Cockburn, also called **Ports and Adapters**) is an earlier, differently-shaped articulation of the exact same underlying rule. Where Clean Architecture draws four concentric rings ordered by stability, Hexagonal Architecture draws a single boundary -- inside vs. outside -- and organizes everything outside that boundary by DIRECTION of communication rather than by ring. The two are close enough in intent that many teams use the terms loosely interchangeably; this file makes the distinct vocabulary and mental model explicit.

```text
Clean Architecture:            four concentric rings, ordered by STABILITY
Hexagonal Architecture:        one inside/outside boundary, ports on the boundary,
                                adapters plugged into the ports, organized by DIRECTION
                                (who initiates the call: the outside world, or the app?)
```

# The Core Idea: The Application Is a Hexagon

--> Draw the application's core (domain model + business logic) as a shape in the middle -- traditionally a hexagon, though the number of sides carries no real meaning; it was chosen simply to leave room to draw several sides without implying a "top" or "bottom" the way a layered diagram does. Every side of the hexagon is a **PORT** -- an interface that defines a boundary of communication. Outside each port, an **ADAPTER** translates between the outside world's technology and the port's interface.

```text
                     ┌───────────────────────┐
      HTTP Adapter ──►│                          │
                     │                          │◄── Database Adapter
   CLI Adapter    ──►│      Application Core       │
                     │    (domain + use cases)      │◄── Email Adapter
  Test Adapter    ──►│                          │
                     │                          │◄── Payment Gateway Adapter
                     └───────────────────────┘
                    ▲                              ▲
              driving/primary                  driven/secondary
              adapters call INTO             adapters are called BY
              the application                 the application
```

--> **The core never depends on any adapter.** It defines PORTS (interfaces) it needs or offers, and adapters are written to satisfy or consume those ports from outside. This is the identical Dependency Rule from file 01, just drawn as one boundary instead of four rings -- and it is why a repository interface, in both patterns, is owned by the inside and implemented on the outside.

# Ports: The Boundary Interfaces

--> A **port** is simply an interface, owned by the application core, that describes one channel of communication across the boundary. There are two kinds, distinguished by WHO initiates the call.

### Primary (Driving) Ports

--> A primary port is called BY the outside world INTO the application -- it describes something the application OFFERS. It is the entry point for a use case. In practice this is usually the use-case interface itself (or the use-case class directly, if no interface is warranted).

```java
// Primary/driving port -- describes a capability the application offers to the outside world.
// Something OUTSIDE (a controller, a CLI, a test) calls INTO this.
interface PlaceOrderPort {
    OrderConfirmation placeOrder(String customerId, long amountCents);
}
```

### Secondary (Driven) Ports

--> A secondary port is called BY the application OUT to the world -- it describes something the application NEEDS from its environment (persistence, sending an email, calling a payment gateway). The application core defines the interface; something outside provides the implementation.

```java
// Secondary/driven port -- describes something the application NEEDS from the outside world.
// The application calls OUT to this; the implementation is supplied by an adapter.
interface OrderRepositoryPort {
    void save(Order order);
    Optional<Order> findById(String orderId);
}

interface PaymentGatewayPort {
    boolean charge(String customerId, long amountCents);
}
```

# Adapters: The Translators

--> An **adapter** is a piece of technology-specific code that plugs into a port. Adapters, like ports, split into the same two directions.

### Primary (Driving) Adapters

--> Sit OUTSIDE the hexagon and CALL INTO a primary port -- they translate an external trigger (an HTTP request, a CLI command, a scheduled job, a test) into a call on the application core. A REST controller, a message-queue consumer, and a JUnit test are all primary adapters for the SAME primary port.

```java
// Primary adapter -- translates an HTTP-shaped request into a call on the primary port.
// This class knows about HTTP; PlaceOrderPort knows nothing about HTTP.
class OrderHttpController {
    private final PlaceOrderPort placeOrderPort;
    OrderHttpController(PlaceOrderPort placeOrderPort) { this.placeOrderPort = placeOrderPort; }

    String handlePost(String customerId, long amountCents) {   // stands in for a @PostMapping method
        OrderConfirmation confirmation = placeOrderPort.placeOrder(customerId, amountCents);
        return "{\"orderId\":\"" + confirmation.orderId() + "\"}";
    }
}

// A SECOND primary adapter for the exact same port -- a CLI, with zero changes to the core.
class OrderCliAdapter {
    private final PlaceOrderPort placeOrderPort;
    OrderCliAdapter(PlaceOrderPort placeOrderPort) { this.placeOrderPort = placeOrderPort; }

    void run(String[] args) {
        OrderConfirmation confirmation = placeOrderPort.placeOrder(args[0], Long.parseLong(args[1]));
        System.out.println("Order placed: " + confirmation.orderId());
    }
}
```

### Secondary (Driven) Adapters

--> Sit OUTSIDE the hexagon and are CALLED BY the application core THROUGH a secondary port -- they implement the port interface using a specific technology (JPA, an HTTP client for a third-party API, an in-memory map for tests).

```java
// Secondary adapter -- implements the port using JPA/JDBC specifics.
class JpaOrderRepositoryAdapter implements OrderRepositoryPort {
    @Override public void save(Order order) { /* real JPA/JDBC persistence */ }
    @Override public Optional<Order> findById(String orderId) { /* real JPA/JDBC lookup */ return Optional.empty(); }
}

// A SECOND secondary adapter for the exact same port -- an in-memory fake for tests,
// with zero changes to the application core, and no test database required.
class InMemoryOrderRepositoryAdapter implements OrderRepositoryPort {
    private final Map<String, Order> store = new HashMap<>();
    @Override public void save(Order order) { store.put(order.id(), order); }
    @Override public Optional<Order> findById(String orderId) { return Optional.ofNullable(store.get(orderId)); }
}
```

# Putting It Together: A Concrete Java Example Structure

--> A typical package layout for a Hexagonal Architecture Java project keeps the core in its own package, isolated from every adapter, with ports as the only interfaces the core exposes outward.

```text
com.example.orders
├── domain/                              -- the hexagon's interior: entities, value objects, pure logic
│   ├── Order.java
│   └── OrderConfirmation.java
├── application/                          -- use cases + the ports they need/offer
│   ├── port/
│   │   ├── in/
│   │   │   └── PlaceOrderPort.java         -- PRIMARY port (driving)
│   │   └── out/
│   │       ├── OrderRepositoryPort.java     -- SECONDARY port (driven)
│   │       └── PaymentGatewayPort.java      -- SECONDARY port (driven)
│   └── PlaceOrderService.java              -- implements PlaceOrderPort, uses the "out" ports
├── adapter/
│   ├── in/
│   │   ├── web/OrderHttpController.java     -- PRIMARY adapter (calls INTO PlaceOrderPort)
│   │   └── cli/OrderCliAdapter.java          -- PRIMARY adapter (calls INTO PlaceOrderPort)
│   └── out/
│       ├── persistence/JpaOrderRepositoryAdapter.java   -- SECONDARY adapter (implements OrderRepositoryPort)
│       └── payment/StripePaymentAdapter.java             -- SECONDARY adapter (implements PaymentGatewayPort)
└── Main.java                              -- composition root: wires adapters to ports
```

```java
// application/PlaceOrderService.java -- implements the primary port, depends only on secondary ports.
// This class is the entire hexagon's "brain" for this use case, and knows NOTHING about HTTP, JPA, or Stripe.
class PlaceOrderService implements PlaceOrderPort {
    private final OrderRepositoryPort repository;
    private final PaymentGatewayPort paymentGateway;

    PlaceOrderService(OrderRepositoryPort repository, PaymentGatewayPort paymentGateway) {
        this.repository = repository;
        this.paymentGateway = paymentGateway;
    }

    @Override
    public OrderConfirmation placeOrder(String customerId, long amountCents) {
        boolean charged = paymentGateway.charge(customerId, amountCents);
        if (!charged) throw new IllegalStateException("Payment declined");
        Order order = new Order(customerId, amountCents);
        repository.save(order);
        return new OrderConfirmation(order.id());
    }
}
```

```java
// Main.java -- composition root: the ONLY place that knows every concrete adapter type.
class Main {
    public static void main(String[] args) {
        OrderRepositoryPort repository = new JpaOrderRepositoryAdapter();
        PaymentGatewayPort paymentGateway = new StripePaymentAdapter();
        PlaceOrderPort placeOrderPort = new PlaceOrderService(repository, paymentGateway);

        OrderHttpController controller = new OrderHttpController(placeOrderPort);   // primary adapter wired in
        controller.handlePost("cust-42", 4999);
    }
}
```

# Hexagonal Architecture vs. Clean Architecture

| Aspect | Hexagonal (Ports & Adapters) | Clean Architecture |
|---|---|---|
| Number of boundaries | one boundary (inside vs. outside) | four ordered rings |
| Organizing axis | direction of call (primary/driving vs. secondary/driven) | stability (entities more stable than frameworks) |
| Vocabulary | ports, adapters | entities, use cases, interface adapters, frameworks |
| Where "use case" logic lives | inside the hexagon, alongside domain logic | its own explicit middle ring, separate from entities |
| Visual metaphor | hexagon with plugs on every side | concentric circles |
| Underlying rule | identical Dependency Rule -- core never depends on adapters | identical Dependency Rule -- inner rings never depend on outer rings |
| Best known for emphasizing | swappable adapters, esp. swapping real infra for test doubles | the explicit separation of enterprise rules vs. application-specific rules |

--> **In practice, most teams blend the two.** It's entirely normal to hear a codebase described as "hexagonal" while its internal package structure also separates a `domain` ring from an `application` (use-case) ring the way Clean Architecture would. The vocabulary differs; the enforceable rule -- the core never imports an adapter -- is the same rule either way, and that rule is the part worth actually enforcing (e.g. with build-tool module boundaries or architecture tests), not the specific diagram shape chosen to explain it.

# Why This Pattern Is Popular for Testing

--> Because every dependency the core needs is expressed as a **secondary port interface**, swapping a real adapter for a test double requires zero changes to the core or to the primary adapters -- `InMemoryOrderRepositoryAdapter` above is a complete, fast, no-database substitute for `JpaOrderRepositoryAdapter`, usable in a unit test with nothing more than `new PlaceOrderService(new InMemoryOrderRepositoryAdapter(), fakePaymentGateway)`.
--> Symmetrically, because every ENTRY point is expressed as a **primary port interface**, a test can drive the application core directly through `PlaceOrderPort` without spinning up an HTTP server at all -- the test itself is just another primary adapter. This is the practical payoff most teams cite as the reason to adopt Hexagonal Architecture: fast, framework-free unit tests for business logic, with slower integration tests reserved for the adapters themselves.

# Common Gotchas and Best Practices

--> **Making ports too fine-grained or too coarse.** A port interface with one method per tiny operation multiplies boilerplate; a single giant port interface covering everything the application needs starts to resemble the "fat interface" ISP violation from the OOP module. Group a port around one cohesive capability (e.g. `OrderRepositoryPort` for order persistence), not around one method or around the entire application.
--> **Letting a secondary adapter leak its technology back through the port's method signatures.** If `OrderRepositoryPort.findById` returns a JPA-specific type, or `PaymentGatewayPort.charge` accepts a Stripe SDK object, the port has stopped being a real abstraction -- it has just become a thin, disguised pass-through to one specific technology, and swapping adapters is no longer actually possible without touching the core.
--> **Conflating "port" with "interface I happened to write".** Not every interface in a hexagonal codebase is a port -- an interface used purely for internal polymorphism within the domain (e.g. a `DiscountStrategy`, from the OOP module's Strategy pattern) is not a boundary crossing and does not belong in an `adapter`/`port` package structure. Reserve the term for interfaces that genuinely sit AT the inside/outside boundary.
--> **Forgetting that primary adapters also need to stay thin.** It's tempting to put validation, orchestration, or business rules directly in `OrderHttpController` because "it's right there" -- but that logic then only works when triggered via HTTP, defeating the purpose of having a swappable primary adapter (the CLI adapter would need to duplicate it). Primary adapters should do translation only: parse the request, call the port, format the response.
--> **Treating the hexagon shape as literal.** The number of sides on the diagram is not meaningful and there is no rule limiting an application to exactly six ports -- most real applications have more primary and secondary ports than fit neatly on a hexagon. The name is a historical artifact of the original write-up, not a constraint.
