# Event-Driven Architecture -- Reacting to What Happened

--> Building directly on the asynchronous communication pattern introduced in the previous file -- Event-Driven Architecture (EDA) structures an ENTIRE system around producing and reacting to events ("OrderPlaced," "PaymentFailed," "UserRegistered") rather than services directly calling each other's specific APIs. A service doesn't need to know WHO will react to an event it publishes, or how many consumers exist -- it simply announces "this happened," and anything interested can react.

```
Traditional (direct calls):
  Order Service --> directly calls --> Inventory Service
  Order Service --> directly calls --> Email Service
  Order Service --> directly calls --> Analytics Service
  (Order Service must know about, and be updated whenever, every single consumer changes)

Event-Driven:
  Order Service --> publishes "OrderPlaced" event --> (doesn't know or care who's listening)
       Inventory Service listens and reacts independently
       Email Service listens and reacts independently
       Analytics Service listens and reacts independently
       (A NEW consumer can be added later with ZERO changes to the Order Service at all)
```

# Event Sourcing -- Storing Changes, Not Just Current State

--> Most applications (and every example throughout the Database/SQL files) store CURRENT state -- an `orders` table has one row per order, and updating it OVERWRITES the previous values, discarding history. Event Sourcing takes a fundamentally different approach -- instead of storing current state, you store the complete, ordered SEQUENCE of events that led to that state, and current state is derived by replaying those events.

```
Traditional state storage:
  orders table: { id: 123, status: "shipped", total: 99.99 }
  (How it got to "shipped" and whether the total was ever changed is lost)

Event Sourcing:
  Event log for order 123:
    1. OrderCreated    { total: 89.99 }
    2. ItemAdded         { item: "extra widget", price: 10.00 }
    3. OrderPaid          { amount: 99.99 }
    4. OrderShipped        { carrier: "FedEx" }

  Current state is COMPUTED by replaying all events in order -- and the full history is
  never lost, since every event remains permanently in the log.
```

--> **Why this matters** -- a complete, permanent audit trail comes essentially for free (directly connecting to the audit-logging and digital forensics concepts covered in the Cyber Security track's Digital Forensics file) -- you can always answer "how did we get here" and even "what would the state have looked like at any specific point in the past" by replaying events only up to that point.
--> **The real cost** -- reconstructing current state by replaying potentially thousands of events for every read is far slower than just reading a current-state row directly -- in practice, systems using Event Sourcing periodically save "snapshots" of computed state to avoid replaying the entire history every single time, and this added complexity is exactly why Event Sourcing is a specialized technique, not a universal default.

# CQRS -- Command Query Responsibility Segregation

--> CQRS separates the model used for WRITING data (Commands -- "place this order," "update this user") from the model used for READING data (Queries -- "show me this user's order history") -- rather than using the SAME model/schema for both, which is what virtually every application covered elsewhere in this Full Stack track does by default.

```
Traditional (single model for both reads and writes):
  One "Order" model/table used for both creating orders AND displaying order history

CQRS (separated models):
  Write Model:  Optimized for validating and processing a new order correctly
  Read Model:    A separate, denormalized structure OPTIMIZED specifically for
                    fast dashboard queries (e.g. pre-joined, pre-aggregated data)
                    -- kept in sync with the write model, often asynchronously via events
```

--> **Why separate them at all** -- write operations need strong consistency and validation (has this customer paid, is this item in stock); read operations for a dashboard often need to be FAST and can tolerate being a few seconds stale -- optimizing one combined model for both needs is often a genuine compromise on both fronts, and CQRS lets each side be optimized independently for its actual different requirements.
--> **CQRS and Event Sourcing are frequently combined** -- events published by the write side (as commands are processed) are exactly what update the read-side's denormalized views -- directly connecting back to the Event-Driven Architecture pattern above, and to the database Materialized View concept referenced in the Database Advanced notes, which is conceptually very similar to a CQRS read model.
--> **The real cost, again** -- CQRS introduces genuine architectural complexity (two models to maintain, keeping them in sync, potential brief inconsistency between them) that's entirely unnecessary for most applications -- like Event Sourcing, it's a specialized tool for specific situations (complex domains with very different read/write access patterns, or high-scale systems where read and write load need to scale completely independently) rather than a default approach.

# Domain-Driven Design (DDD) -- Modeling the Actual Business

--> DDD is a broader design philosophy (predating and often used ALONGSIDE the patterns above) focused on building software whose structure directly mirrors the real business domain it serves, developed in close collaboration with actual domain experts (not just engineers guessing at business rules).

## Ubiquitous Language

--> DDD insists that the EXACT SAME terminology be used by business stakeholders, in documentation, AND in the actual code -- if the business calls something a "Shipment," the code should have a `Shipment` class/table, not an `Order` table with a `shipping_status` field standing in for a concept that actually deserves to be its own explicit thing. This sounds like a small naming detail, but it directly prevents the extremely common, costly problem of code and business understanding SLOWLY DRIFTING APART as a system evolves and new developers join.

## Bounded Contexts -- Where Microservices Boundaries Actually Come From

--> A Bounded Context is a boundary within which a specific model and its ubiquitous language apply consistently -- the SAME word can genuinely mean different things in different contexts, and DDD makes this explicit rather than forcing one unified (and inevitably compromised) definition everywhere.

```
"Customer" in the Sales Bounded Context:
  Focused on: purchase history, loyalty tier, contact preferences

"Customer" in the Support Bounded Context:
  Focused on: open tickets, support history, satisfaction scores

Same real-world person, DELIBERATELY different models, because each context
only cares about, and should only be complicated by, the aspects relevant to it.
```

--> This is precisely where genuinely well-reasoned microservice boundaries come from in practice -- rather than arbitrarily splitting a system by technical layer or guesswork, DDD's Bounded Contexts identify NATURAL seams in the business domain itself, and each Bounded Context often maps directly onto one microservice (connecting directly back to the Monolith vs Microservices file), giving a principled answer to "how should we actually split this system up" instead of an arbitrary one.

## Aggregates and Entities

--> An Entity is an object with a distinct, persistent identity that matters (a specific `Order` with ID 123, tracked and referenced by that identity even as its other attributes change over time).
--> An Aggregate is a cluster of related entities/objects treated as a single consistency unit, with one designated "Aggregate Root" acting as the only entry point external code is allowed to interact with -- e.g. an `Order` Aggregate Root might internally contain multiple `OrderLineItem` entities, but external code always goes THROUGH the `Order` to modify a line item, rather than modifying `OrderLineItem` objects directly and independently -- this is what keeps the whole cluster's business rules (e.g. "an order's total must always match the sum of its line items") consistently enforced in exactly one place.

# When to Actually Reach for These Patterns

--> All three patterns covered in this file (EDA, CQRS, DDD) share the SAME underlying lesson as the Monolith vs Microservices file's conclusion -- each solves a REAL problem, but each also introduces real complexity that is absolutely not worth paying for a simple CRUD application. DDD's Bounded Context thinking is valuable at almost any scale simply as a way of thinking clearly about a domain; Event Sourcing and CQRS earn their operational cost specifically once a domain's complexity, audit requirements, or read/write scaling needs genuinely demand them -- introducing them prematurely, before that real need exists, is one of the most common, well-documented ways software projects accumulate unnecessary complexity and end up harder to maintain than the simpler alternative would have been.
