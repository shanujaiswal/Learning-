# A Promise Finally Delivered -- Distributed Transactions, Done Properly

--> The Monolith vs Microservices file flagged, but never actually explained, the core problem: once a business operation spans multiple services, each with its OWN database, you can no longer wrap the whole thing in one SQL transaction (the ACID guarantees covered in the Transactions and ACID file only apply WITHIN a single database). This file delivers that missing explanation in full -- the Saga pattern for coordinating a multi-step, multi-service operation, the Outbox pattern for making the individual steps reliable, and a framework (coupling/cohesion) for evaluating architectural decisions generally. None of this requires, and none of it covers, the GoF design patterns or SOLID principles -- those are a separate, code-level concern from the distributed-systems and system-structuring concerns covered here.

# The Problem, Concretely

```
Placing an order touches THREE separate services, each with its own database:
  1. Order Service     -- create the order record
  2. Inventory Service  -- decrement stock for the ordered item
  3. Payment Service     -- charge the customer's card

In a monolith, one SQL transaction wraps all three -- either ALL succeed,
or the transaction rolls back and NONE do. There is no equivalent single
transaction possible here -- three separate databases cannot participate
in one atomic commit without specialized (and, in practice, rarely used at
this scale) distributed-transaction protocols like two-phase commit, which
don't hold up well under microservices' independent-deployment and
availability requirements.
```

--> If Payment fails AFTER Inventory has already decremented stock, the system is left in an inconsistent intermediate state -- stock reduced for an order that was never actually paid for -- unless something explicitly UNDOES the Inventory step. This is exactly the problem the Saga pattern solves.

# The Saga Pattern

--> A Saga breaks one long business operation into a SEQUENCE of local transactions, one per service, each of which is a normal, ordinary ACID transaction WITHIN that one service's own database -- and, critically, defines an explicit COMPENSATING transaction for every step, to be run if a LATER step in the sequence fails, undoing that step's effect rather than relying on a rollback that spans services (which doesn't exist).

```
Saga steps:                          Compensating transaction if a later step fails:
  1. Order Service:    create order    -->  cancel order
  2. Inventory Service: reserve stock   -->  release reserved stock
  3. Payment Service:    charge card     -->  refund the charge

If step 3 (Payment) fails:
  Run step 2's compensation (release stock) and step 1's compensation
  (cancel order), IN REVERSE ORDER, to unwind everything that already
  succeeded -- ending in a consistent state, just reached differently
  than a single atomic rollback would have.
```

--> **This is fundamentally EVENTUAL, not immediate, consistency** -- for a brief window while a saga is still running, the system IS in an intermediate state (an order exists but hasn't been paid for yet) -- directly the same eventual-consistency trade-off the Monolith vs Microservices file names as the core cost of splitting a database per service, now shown in the specific mechanism that manages it.

## Choreography -- Each Service Reacts Independently

--> In a choreographed saga, there is no central coordinator -- each service listens for the PREVIOUS step's event, does its own local transaction, and publishes its OWN event for the next service to react to, using the event-driven communication covered in the Event-Driven Architecture file.

```
Order Service:      creates order  --> publishes "OrderCreated"
                                              |
Inventory Service:  listens for "OrderCreated", reserves stock
                                        --> publishes "StockReserved"
                                              |
Payment Service:    listens for "StockReserved", charges the card
                                        --> publishes "PaymentCompleted" (success)
                                                  or "PaymentFailed" (failure)

If "PaymentFailed" is published:
  Inventory Service listens for it directly, and runs ITS OWN
  compensation (release the reserved stock) in reaction.
  Order Service listens for it too, and cancels the order.
  -- Every service independently reacts to the failure event; nothing
     centrally orchestrates the unwind.
```

--> **Advantages** -- no single central component to build, deploy, or become a bottleneck; each service only needs to know the EVENTS it cares about, not the full end-to-end flow -- naturally fitting a system already built around Event-Driven Architecture.
--> **The real cost** -- as the number of steps grows, the actual END-TO-END flow of "what happens when someone places an order" exists NOWHERE as a single readable artifact -- it's implicitly scattered across every service's own event listeners, making the overall saga's logic genuinely hard to see, debug, or reason about as a whole, and easy to get subtly wrong (a missing listener for a failure event silently breaks the compensation chain with no obvious single place to notice it).

## Orchestration -- A Central Coordinator Drives the Flow

--> In an orchestrated saga, one dedicated component (a "Saga Orchestrator") explicitly calls each service IN SEQUENCE, tracks the saga's current state, and explicitly triggers compensating transactions in reverse order if any step fails -- the flow exists as one readable piece of code/config, not implicitly scattered across listeners.

```
Saga Orchestrator:
  1. Call Order Service:     create order        --> success, continue
  2. Call Inventory Service: reserve stock         --> success, continue
  3. Call Payment Service:    charge card           --> FAILS

  Orchestrator explicitly runs compensations, in reverse order:
  2'. Call Inventory Service: release reserved stock
  1'. Call Order Service:     cancel order

  Saga ends in a known, deliberately-tracked FAILED state --
  the orchestrator knows exactly how far the saga got and exactly
  what was already undone, at every point.
```

```javascript
// A simplified orchestrator, explicitly driving each step and its compensation
async function placeOrderSaga(orderRequest) {
  const order = await orderService.create(orderRequest);
  try {
    await inventoryService.reserveStock(order.items);
    try {
      await paymentService.charge(order.total);
    } catch (paymentError) {
      await inventoryService.releaseStock(order.items);   // compensate step 2
      throw paymentError;
    }
  } catch (err) {
    await orderService.cancel(order.id);                   // compensate step 1
    throw err;
  }
  return order;
}
```

--> **Advantages** -- the entire business flow is explicit and readable in ONE place, making it far easier to reason about, test, and modify than logic implicitly scattered across many services' independent event listeners; also makes tracking a specific saga's current progress/state straightforward, since the orchestrator itself IS that tracked state.
--> **The real cost** -- the orchestrator becomes a new, additional piece of infrastructure that must itself be built, deployed, and made reliable (what happens if the orchestrator crashes MID-saga?), and it necessarily knows about and couples to every participating service's specific API -- reintroducing some of the very coupling that splitting into independent services was meant to reduce in the first place, though this coupling is arguably more manageable, because it's now concentrated and visible in ONE place instead of scattered.

## Choosing Between Them

--> **Choreography** suits a small number of steps (2-3 services) in a system already heavily built around events, where the added visibility cost of a scattered flow is manageable.
--> **Orchestration** suits a saga with several steps, complex conditional branching, or where the business genuinely needs a clear, queryable "what state is THIS specific order's saga currently in" view -- the explicit coordinator naturally provides that tracking, whereas choreography would need to reconstruct it after the fact from scattered event logs.

# The Outbox Pattern -- Making a Single Step Reliable

--> The Saga pattern assumes each individual step reliably BOTH performs its local database change AND publishes its event -- but that pairing has a subtle failure mode of its own: what if a service commits its database transaction successfully, but then crashes (or the message broker is briefly unreachable) BEFORE it manages to actually publish the corresponding event? The database change happened; the rest of the saga never finds out.

```
The dual-write problem:
  1. INSERT INTO orders (...) VALUES (...)     -- database write succeeds, committed
  2. kafka.publish("OrderCreated", ...)         -- service crashes HERE, before publishing

Result: an order exists in the database that NO OTHER service ever learns about --
        the saga silently stalls, with no error raised anywhere.
```

--> The Outbox pattern solves this by writing the EVENT to be published into an "outbox" table, IN THE SAME LOCAL DATABASE TRANSACTION as the actual business data change -- since both writes are now part of ONE ordinary ACID transaction within a single database, they succeed or fail TOGETHER, with no window where one happens without the other.

```sql
-- Both inserted in ONE transaction -- atomic by ordinary ACID guarantees,
-- no distributed coordination needed, because both rows live in the SAME database.
BEGIN;
  INSERT INTO orders (id, status, total) VALUES (123, 'created', 99.99);
  INSERT INTO outbox (id, event_type, payload, published)
    VALUES (gen_random_uuid(), 'OrderCreated', '{"order_id": 123}', false);
COMMIT;
```

--> A separate process -- either a polling job that periodically reads unpublished outbox rows, or a Change Data Capture connector (e.g. Debezium reading the database's write-ahead log) -- picks up unpublished outbox rows and actually publishes them to Kafka/RabbitMQ (the Message Queues file), then marks them published.

```
Business transaction commits (order + outbox row, atomically)
        |
        v
Outbox poller/CDC connector reads unpublished rows
        |
        v
Publishes to Kafka/RabbitMQ  --> marks outbox row as published

If the publish step itself fails partway, the row simply stays
unpublished and gets retried on the next poll -- the ORIGINAL business
data was already safely committed regardless, so nothing is ever lost,
only possibly delayed or (with proper consumer-side idempotency,
covered in the API Design file's Idempotency Keys section) delivered
more than once.
```

--> **Why this matters specifically for Sagas** -- the Outbox pattern is what makes each individual saga STEP trustworthy enough to build a saga out of at all; without it, a choreographed saga's event chain can silently break at exactly the moment a service's local write succeeds but its corresponding publish doesn't, and an orchestrated saga's orchestrator can be told a step "succeeded" (the database write worked) while the downstream notification that should trigger the next step never actually goes out.

# Compensating Transactions -- The Detail That Makes Sagas Actually Work

--> A compensating transaction is not simply "the exact reverse of the forward operation" -- it's a deliberately-designed business operation in its own right, and needs its own explicit thought, because the world can have moved on since the original step ran.

```
Naive assumption: "reserve stock" undone by "un-reserve stock" is a trivial mirror image.

Reality: what if, between the reservation and the compensation running,
ANOTHER process already allocated that same stock elsewhere because it
appeared free? The compensation needs its OWN business logic (e.g.
release back into a specific pool, log the anomaly, or flag for manual
review) -- it is not guaranteed to be a clean, symmetrical undo.
```

--> **Compensations are not always fully undoable** -- charging a customer's card and later refunding it is a compensating transaction, but it is NOT identical to the charge never having happened (the customer sees two line items on their statement, a brief hold on funds, possibly a processing delay on the refund) -- a genuinely important, easy-to-miss distinction from the atomic rollback a single-database transaction provides, where a rolled-back transaction truly leaves zero trace. Saga-based eventual consistency accepts this weaker guarantee as the necessary cost of not needing a single shared database across independently-deployed services.

# Architectural Quality Attributes -- Coupling and Cohesion as an Evaluation Framework

--> Stepping back from any one specific pattern -- how do you actually judge whether an architectural decision (splitting a service, choosing choreography over orchestration, introducing a new shared library) is a GOOD one? Coupling and Cohesion give a concrete, reusable vocabulary for that judgment, independent of any specific pattern, and explicitly without reaching for GoF-style code-level patterns or SOLID's class-design rules, which operate one level of abstraction below what's discussed here.

## Coupling -- How Much One Part Depends on Another's Internals

--> Coupling measures how much a change in one component FORCES a change in another. Low coupling is generally desirable -- it's what lets one service, module, or team change independently without rippling changes through everything connected to it.

```
Tight coupling example:
  Service A directly queries Service B's DATABASE TABLES to read its data.
  --> If B ever changes its schema (renames a column, splits a table),
      A breaks too, even though A's own code didn't change at all.

Loose coupling example:
  Service A calls Service B's well-defined, versioned API to read that
  same data.
  --> B can freely change its INTERNAL schema/implementation as long as
      the API's contract (connecting to the OpenAPI/contract-testing
      concepts in the API Design file) stays the same -- A never notices.
```

--> This is exactly WHY "each service owns its own database" is a core microservices rule (from the Monolith vs Microservices file) rather than an arbitrary convention -- reaching directly into another service's database is one of the tightest, most damaging forms of coupling possible, because it exposes a service's internal implementation detail (its schema) as if it were a stable public contract.
--> Coupling isn't binary -- synchronous request/response coupling (Service A calls B and waits) is tighter than asynchronous event-based coupling (A publishes an event and doesn't know or care who reacts, from the Event-Driven Architecture file), which is looser still, because A doesn't even need B to exist or be available at the moment A acts.

## Cohesion -- How Well a Component's Own Pieces Belong Together

--> Cohesion measures how strongly the responsibilities WITHIN one component relate to each other and to one clear, single purpose. High cohesion is desirable -- it means a component does one coherent thing well, is easy to understand as a whole, and rarely needs to change for reasons unrelated to its actual purpose.

```
Low cohesion example:
  A single "Utils" service that handles email sending, PDF generation,
  currency conversion, AND user authentication -- four unrelated
  responsibilities crammed into one deployable unit for no reason
  beyond convenience.
  --> A change to currency conversion logic requires redeploying (and
      re-testing, and risking) the SAME service that handles authentication,
      even though the two have nothing to do with each other.

High cohesion example:
  A dedicated Notifications service that ONLY handles sending emails/SMS/
  push notifications -- every piece of its code relates to that one
  clear purpose, echoing the Domain-Driven Design file's Bounded Context
  idea of a service's boundary matching a real, coherent business capability.
```

--> This directly explains WHY Domain-Driven Design's Bounded Contexts (from the Event-Driven Architecture/CQRS/DDD file) make for good microservice boundaries in the first place -- a Bounded Context is, by definition, a cluster of closely-related concepts and responsibilities, which is exactly what high cohesion asks for; splitting a system along its NATURAL cohesive boundaries, rather than arbitrarily, is precisely what produces services that are both internally coherent and loosely coupled to each other.

## The Trade-off Framework in Practice

--> **The goal, stated plainly** -- LOW coupling BETWEEN components, HIGH cohesion WITHIN each component. These two properties are not in tension with each other (they're not opposite ends of one dial) -- a well-drawn boundary achieves both simultaneously, while a poorly-drawn one achieves neither.
--> **How to actually use this when evaluating a decision** -- ask two concrete questions of any proposed architectural change: "if X changes, how many OTHER things does this force to also change?" (a coupling question) and "does everything inside this one component actually belong together, for one clear reason?" (a cohesion question). A proposed service split that separates two responsibilities that always change TOGETHER for the same business reason is a low-cohesion mistake in the making (it splits what should stay one cohesive unit); a proposed shared library that many unrelated services all end up depending on for unrelated reasons is a coupling risk (a change to that one library now has a blast radius across every unrelated consumer).
--> **Applying it retroactively too** -- when an existing system feels painful to change (every small feature seems to require touching five different files/services, or one service seems to do far too much and get modified for entirely unrelated reasons), coupling and cohesion give the actual vocabulary for DIAGNOSING why, rather than just a vague feeling that "this codebase is messy" -- and, combined with the Saga/Outbox patterns above for handling the eventual-consistency consequences, form a genuinely complete toolkit for both DESIGNING and EVALUATING a distributed system's structure, independent of the code-level GoF/SOLID concerns handled elsewhere.
