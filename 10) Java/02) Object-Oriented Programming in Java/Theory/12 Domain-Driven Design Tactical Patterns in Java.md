# From Strategic to Tactical

--> File 03 covered WHERE domain logic belongs -- bounded contexts, ubiquitous language, subdomains. This file covers HOW to shape the actual Java code living inside one bounded context so it faithfully expresses business rules, protects its own invariants, and stays honest about what is a stable identity versus what is an interchangeable description. These are DDD's **tactical patterns**: entities, value objects, aggregates, repositories, domain events, and domain services. Unlike the GoF patterns from the OOP module, tactical DDD patterns are specifically about modeling a DOMAIN faithfully, not about general-purpose object-creation or behavioral flexibility -- though they lean heavily on the same OOP mechanics (encapsulation, immutability, polymorphism) covered there.

# Entities

--> An **entity** is an object defined by a persistent, continuous IDENTITY rather than by the current values of its attributes. Two entities with identical attribute values are still DIFFERENT entities if their identities differ; conversely, the SAME entity remains the same entity even as every one of its attributes changes over time. A `Customer` with `customerId = "C-42"` is the same customer whether their name is spelled correctly or was just fixed after a typo -- identity, not attribute equality, is what makes it "the same one".

```java
// Entity -- identity (customerId) is what equality/identity is based on, NOT the mutable attributes.
class Customer {
    private final CustomerId id;         // identity -- immutable once assigned, never changes
    private String name;                  // attribute -- can change without changing WHICH customer this is
    private Email email;                   // attribute -- can change too

    Customer(CustomerId id, String name, Email email) {
        this.id = id;
        this.name = name;
        this.email = email;
    }

    void changeEmail(Email newEmail) {      // behavior lives ON the entity -- not in a separate "setter service"
        this.email = newEmail;
    }

    CustomerId id() { return id; }

    @Override
    public boolean equals(Object o) {         // equality is by IDENTITY, not by attribute values
        if (this == o) return true;
        if (!(o instanceof Customer other)) return false;
        return id.equals(other.id);             // note: name/email are NOT part of equality
    }

    @Override
    public int hashCode() { return id.hashCode(); }
}
```

--> **Entities encapsulate behavior, not just data.** A common anti-pattern (the "anemic domain model") reduces entities to bags of getters/setters with all real logic pulled out into separate "service" classes -- this defeats the purpose of DDD's tactical patterns entirely, since business rules end up scattered across services rather than living next to the data they protect. `changeEmail` above lives ON `Customer`, not in a `CustomerService.changeEmail(customer, newEmail)` method, because validating and applying an email change IS the customer's own responsibility.

# Value Objects

--> A **value object** is defined entirely by its ATTRIBUTES, with no identity of its own -- two value objects with the same attribute values are, and should be treated as, completely interchangeable. Money, a date range, an address, an email address are typical value objects: two `Money` instances both representing "$50.00" are simply equal, full stop, with no separate notion of "which $50.00 this is". Value objects should be IMMUTABLE -- any "change" produces a new instance rather than mutating the existing one.

```java
// Value object -- equality is by VALUE (all fields), fully immutable, no identity of its own.
// A Java record is an excellent fit: it generates equals()/hashCode()/toString() by value automatically.
record Money(BigDecimal amount, Currency currency) {
    Money {                                     // compact constructor -- enforces the invariant on EVERY instance
        if (amount.signum() < 0) throw new IllegalArgumentException("Money cannot be negative");
        Objects.requireNonNull(currency, "currency is required");
    }

    Money add(Money other) {                     // "changing" money returns a NEW Money -- never mutates
        requireSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currency);
    }

    Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(this.amount.subtract(other.amount), this.currency);
    }

    private void requireSameCurrency(Money other) {
        if (!this.currency.equals(other.currency))
            throw new IllegalArgumentException("Currency mismatch: " + this.currency + " vs " + other.currency);
    }
}

// Usage -- two Money instances with the same amount/currency are simply EQUAL, no identity involved.
Money price = new Money(BigDecimal.valueOf(49.99), Currency.getInstance("USD"));
Money samePrice = new Money(BigDecimal.valueOf(49.99), Currency.getInstance("USD"));
System.out.println(price.equals(samePrice));   // true -- interchangeable, unlike two Customer entities
```

--> **Value objects are where invariants and small business rules cluster most naturally.** Wrapping a raw `String` email as an `Email` value object (rather than passing `String` everywhere) means the validation rule ("must contain an `@`") is enforced in exactly one place, at construction, and it becomes IMPOSSIBLE to hold an `Email` instance that doesn't satisfy the rule -- this is sometimes called "making illegal states unrepresentable".

```java
// A tiny value object dedicated to one concept -- prevents an invalid Email from ever existing.
record Email(String address) {
    Email {
        if (address == null || !address.contains("@"))
            throw new IllegalArgumentException("Invalid email address: " + address);
    }
}
```

# Entities vs. Value Objects

| Aspect | Entity | Value Object |
|---|---|---|
| Defined by | continuous identity | its attribute values |
| Equality | by identity field (e.g. an ID) | by all attribute values |
| Mutability | typically mutable (state changes over its lifetime) | always immutable |
| "Change" means | the same object's state is updated | a new instance replaces the old one |
| Java implementation | a class with an identity field and controlled mutator methods | ideally a `record`, or a class with only `final` fields and no setters |
| Example | `Customer`, `Order`, `Account` | `Money`, `Address`, `DateRange`, `Email` |

# Aggregates and Aggregate Roots

--> An **aggregate** is a cluster of entities and value objects that must be treated as ONE consistency boundary -- changes within the cluster must always leave the WHOLE cluster in a valid state, and nothing outside the aggregate should be able to reach into it and mutate an inner part directly, bypassing the rules that protect the cluster as a whole. Every aggregate has exactly one **aggregate root**, an entity that is the ONLY entry point external code is allowed to hold a reference to or call methods on; everything else inside the aggregate is reached only through the root.

```java
// Aggregate: Order (root) + OrderLine (internal entities), treated as ONE consistency unit.
// OrderLine is never exposed as a mutable reference to the outside -- only Order (the root) is.
class Order {                                     // AGGREGATE ROOT
    private final OrderId id;
    private final List<OrderLine> lines = new ArrayList<>();
    private OrderStatus status = OrderStatus.DRAFT;
    private static final int MAX_LINES = 50;         // an invariant the ROOT enforces for the WHOLE aggregate

    Order(OrderId id) { this.id = id; }

    void addLine(ProductId productId, int quantity, Money unitPrice) {
        if (status != OrderStatus.DRAFT)
            throw new IllegalStateException("Cannot modify an order that is no longer a draft");
        if (lines.size() >= MAX_LINES)
            throw new IllegalStateException("Order cannot exceed " + MAX_LINES + " lines");
        lines.add(new OrderLine(productId, quantity, unitPrice));      // internal entity created HERE, by the root
    }

    void submit() {
        if (lines.isEmpty()) throw new IllegalStateException("Cannot submit an order with no lines");
        this.status = OrderStatus.SUBMITTED;
    }

    Money total() {                                  // aggregate-wide computation belongs on the root
        return lines.stream()
                .map(OrderLine::lineTotal)
                .reduce(new Money(BigDecimal.ZERO, Currency.getInstance("USD")), Money::add);
    }

    List<OrderLine> lines() { return List.copyOf(lines); }   // read-only VIEW out -- never the live mutable list
}

class OrderLine {                                   // internal entity -- never referenced directly from outside the aggregate
    private final ProductId productId;
    private final int quantity;
    private final Money unitPrice;

    OrderLine(ProductId productId, int quantity, Money unitPrice) {
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be positive");
        this.productId = productId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    Money lineTotal() { return unitPrice.multipliedBy(quantity); }
}

enum OrderStatus { DRAFT, SUBMITTED, SHIPPED, CANCELLED }
```

--> **Why routing every change through the root matters**: if external code could grab an `OrderLine` directly and mutate its quantity, the `MAX_LINES` check and the `DRAFT`-only mutation rule on `Order` could be silently bypassed -- the aggregate's own invariants would no longer be reliably enforced. Funneling every mutation through `Order`'s methods is what makes those invariants actually trustworthy, not just documentation that callers might ignore.
--> **Aggregate boundaries should be kept SMALL.** A common mistake is designing one giant aggregate (e.g. `Customer` containing every order, every invoice, every support ticket the customer ever had) which then has to be loaded and locked in its entirety for even a tiny change, and which invites exactly the kind of god-object bloat DDD's strategic patterns (file 03) are meant to avoid. The rule of thumb: an aggregate should be exactly as large as its TRUE consistency requirement, and no larger -- reference OTHER aggregates by ID only (e.g. `Order` holds a `CustomerId`, not a live `Customer` object), never by direct object reference.

# Repositories

--> A **repository** provides an interface that looks like an in-memory collection of aggregate roots, hiding the actual persistence mechanism behind it entirely. This is the same repository-abstraction idea already introduced in files 01-02 (`OrderRepository`/`OrderRepositoryPort`), specialized here to a DDD-specific rule: a repository is defined **per aggregate root**, never per entity, and never per arbitrary table.

```java
// A repository per AGGREGATE ROOT -- Order only, never a separate OrderLineRepository,
// because OrderLine has no independent existence/identity outside its owning Order.
interface OrderRepository {
    void save(Order order);
    Optional<Order> findById(OrderId id);
    List<Order> findByCustomer(CustomerId customerId);
}
```

--> **Why per aggregate root, not per entity**: since `OrderLine` only ever exists inside an `Order` and is never independently retrieved, addressed, or mutated from outside, a repository for it would offer callers a way to bypass the aggregate's own consistency rules -- exactly the problem the aggregate boundary exists to prevent. One repository per aggregate root keeps persistence access shaped the same way the domain model's own consistency boundaries are shaped.

# Domain Events

--> A **domain event** is an immutable record of something significant that HAPPENED within the domain, expressed in the past tense using the ubiquitous language (file 03) -- `OrderSubmitted`, not `SubmitOrderRequest`. Domain events let other parts of the system (inside or outside the same bounded context) react to a state change without the originating aggregate needing to know anything about who's listening, decoupling the aggregate that caused the change from the code that reacts to it.

```java
// Domain event -- immutable, past-tense, carries exactly the facts other code needs to react.
record OrderSubmitted(OrderId orderId, CustomerId customerId, Money total, Instant occurredAt) { }

// The aggregate root RECORDS events as a side effect of a state-changing operation,
// but does not itself know or care who will eventually handle them.
class Order {
    private final List<Object> pendingEvents = new ArrayList<>();
    // ... id, lines, status fields as before ...

    void submit(CustomerId customerId) {
        if (lines.isEmpty()) throw new IllegalStateException("Cannot submit an order with no lines");
        this.status = OrderStatus.SUBMITTED;
        pendingEvents.add(new OrderSubmitted(id, customerId, total(), Instant.now()));   // recorded, not dispatched
    }

    List<Object> pullPendingEvents() {                 // application layer collects & publishes these after saving
        List<Object> events = List.copyOf(pendingEvents);
        pendingEvents.clear();
        return events;
    }
    // ... id, lines, total() as before ...
    private final OrderId id = null; private final List<OrderLine> lines = new ArrayList<>(); private OrderStatus status;
    Money total() { return new Money(BigDecimal.ZERO, Currency.getInstance("USD")); }
}
```

--> **Domain events are also the natural mechanism for context mapping's Published Language pattern (file 03)** -- an `OrderSubmitted` event, published onto a message queue, is exactly how one bounded context tells other bounded contexts something happened, without either context depending on the other's internal model directly.

# Domain Services

--> Most business logic belongs ON an entity or value object. A **domain service** exists for the smaller set of operations that don't naturally belong to any single entity -- typically because the logic meaningfully spans MULTIPLE aggregates, or because forcing it onto one aggregate would give that aggregate knowledge it shouldn't have (e.g. knowledge of a second aggregate's internals).

```java
// Domain service -- transferring money spans TWO Account aggregates; the operation
// doesn't naturally belong to either Account alone, so it lives in a dedicated domain service.
class MoneyTransferService {
    void transfer(Account from, Account to, Money amount) {
        from.withdraw(amount);     // each aggregate still enforces its OWN invariants internally
        to.deposit(amount);          // the service only ORCHESTRATES across the two aggregate boundaries
    }
}
```

--> **A domain service is not the same thing as an application-layer "service" class** (like `OrderService` in file 01's layered example, which mostly orchestrates a use case). A domain service holds genuine DOMAIN logic -- the rule that a transfer must debit one account and credit another atomically is a business rule, not application plumbing -- it is just domain logic that doesn't fit neatly inside one entity's boundary. Reach for a domain service only after confirming the logic truly doesn't belong to a single aggregate; misusing domain services as a dumping ground for logic that was just too much effort to place correctly is how anemic domain models creep back in.

# Tactical Patterns at a Glance

| Pattern | Defined by | Mutable? | Typical Java shape |
|---|---|---|---|
| Entity | continuous identity | usually yes | class with an ID field, controlled mutators |
| Value Object | its attribute values | no, always immutable | `record`, or a class with only `final` fields |
| Aggregate Root | the entity that guards a consistency boundary | yes, through its own methods only | class exposing only safe, invariant-checked operations |
| Repository | persistence abstraction for ONE aggregate root | n/a (interface) | interface owned by the domain/application layer |
| Domain Event | a fact that already happened | no, immutable, past-tense | `record`, published after a state change is committed |
| Domain Service | logic spanning multiple aggregates | n/a (typically stateless) | a class with no identity of its own, injected where needed |

# Common Gotchas and Best Practices

--> **The anemic domain model** -- entities reduced to getters/setters with all logic pulled into "service" classes. This is arguably the most common way DDD tactical patterns get half-adopted and lose most of their value; if `OrderService.addLineToOrder(order, line)` exists instead of `order.addLine(...)`, the invariant checks tend to migrate out of the aggregate and scatter across every caller, eventually diverging.
--> **Aggregates that are too large.** An aggregate spanning too much of the domain graph forces expensive loads, aggressive locking, and cross-team contention over a single class -- when in doubt, make the aggregate SMALLER and reference other aggregates by ID, accepting eventual consistency (often via a domain event) between them instead of forcing everything into one transactional boundary.
--> **Repositories per entity instead of per aggregate root.** Exposing a repository for an internal entity like `OrderLine` invites code to bypass the aggregate root's own invariant checks -- if something needs querying independently of its parent aggregate, that's usually a signal it should be modeled as its OWN aggregate root, not that it needs its own repository while still living inside another aggregate.
--> **Confusing a value object with a small entity.** The deciding question is always "does this need to be tracked as the SAME one over time, or is it fully interchangeable with any other instance holding equal values?" An `Address` used purely as a shipping destination is a value object; a `SavedAddress` a user names, edits, and reuses across multiple orders has its own identity and lifecycle, and is arguably an entity.
--> **Firing domain events before the state change is actually committed.** If `OrderSubmitted` is published and consumed by another service before the `Order`'s new status is durably saved, a crash between the publish and the save leaves downstream systems believing something happened that, from persistence's point of view, never did — collect events on the aggregate as shown above, and publish them only after the save that recorded the change has successfully completed.
--> **Reaching for a domain service too early.** If logic can be pushed onto a single entity/aggregate root without giving that aggregate inappropriate knowledge of another aggregate's internals, prefer that -- domain services are for genuine cross-aggregate operations, not a default home for logic that was merely inconvenient to place.
