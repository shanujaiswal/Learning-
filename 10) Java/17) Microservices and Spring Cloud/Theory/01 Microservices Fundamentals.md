# Why Break a Perfectly Working Application Into Pieces?

--> A **monolith** is a single deployable unit -- one WAR/JAR, one codebase, one build, one running process (even if scaled to multiple identical instances behind a load balancer) -- containing every module of the application (orders, inventory, payments, users) compiled and deployed together. A **microservices architecture** splits those same modules into independently deployable services, each with its OWN codebase, its OWN database (usually), its OWN deployment pipeline, and its OWN runtime process -- communicating over the network (HTTP/REST, messaging, gRPC) instead of via in-process Java method calls.
--> Neither is "correct" in the abstract -- this chapter is about the TRADEOFFS, because picking microservices for a small team building a low-traffic internal tool is a classic case of over-engineering, and staying monolithic past the point a large org needs independent team ownership is an equally real mistake in the other direction.

# Monolith vs Microservices -- The Tradeoffs, Honestly

| Dimension | Monolith | Microservices |
|---|---|---|
| Deployment | One deploy for the whole app -- simple pipeline, but a tiny change to one module requires redeploying everything | Each service deploys independently -- a bug fix in "shipping" doesn't touch "billing," but you now maintain N pipelines |
| Development speed (small team) | Fast -- one codebase, one IDE project, no network calls to fake in tests | Slower initially -- more moving parts, more infrastructure to stand up before you write business logic |
| Development speed (large org) | Slows down -- many teams committing to the same codebase, merge conflicts, one team's bug blocks another team's release | Scales better -- teams own their services end-to-end and release on their own schedule |
| Scaling | Scale the WHOLE app even if only one module (e.g. checkout) is under load -- wasteful | Scale just the hot service independently -- efficient, but requires infra (load balancers, orchestration) to do well |
| Technology choice | One language/framework/DB for everything | Each service can pick its own stack -- powerful, but a real cost in operational diversity (more things to monitor, patch, hire for) |
| Data consistency | Trivial -- one database, real ACID transactions across modules via a single `@Transactional` boundary | Hard -- each service's own database means cross-service consistency needs eventual consistency patterns (covered below) |
| Failure isolation | A memory leak or infinite loop in one module can take down the whole process | A crashing service can (if built resiliently) fail without taking the rest of the system down -- but ALSO introduces brand-new failure modes: network timeouts, partial failures, cascading retries |
| Testing | Easy to write true end-to-end integration tests -- everything's in one process | Harder -- testing a user flow that spans 4 services means either spinning all 4 up, using contract tests, or accepting weaker test guarantees |
| Operational complexity | Low -- one thing to monitor, one set of logs, one thing to keep patched | High -- distributed tracing, service discovery, per-service monitoring/alerting, more moving infrastructure pieces (covered in later chapters) |
| Debugging a production issue | Attach a debugger, read one log file, follow one call stack | Correlate logs ACROSS services, follow a request across process/network boundaries (distributed tracing becomes not-optional) |

--> **Deep Dive -- the "distributed monolith" anti-pattern** -- the worst outcome is NOT staying monolithic, it's splitting into microservices that are still tightly coupled underneath: services that must all deploy together because their APIs change in lockstep, services that share a single database (defeating the whole point of independent evolution), or services chattering over dozens of synchronous calls per user request. This gives you ALL the operational cost of microservices (network calls, more infra, more failure modes) with NONE of the benefit (independent deployability, failure isolation). If your services can't be deployed independently, you don't actually have microservices yet, regardless of how many separate git repos or JAR files exist.
--> **The honest recommendation for most teams** -- start with a well-structured, modular monolith (clear internal module boundaries, disciplined dependencies between packages) and split out microservices later, driven by REAL organizational or scaling pain (a specific module needs independent scaling, a specific team needs independent release cadence) rather than splitting upfront because "microservices are the modern way." Splitting too early adds massive complexity before you know where the real boundaries should even be.

# Finding Service Boundaries -- Domain-Driven Design, Lite

--> The hardest part of microservices isn't the Spring Cloud plumbing -- it's deciding WHERE to cut. Cut along the wrong lines (e.g. splitting by technical layer: a "database service," a "validation service") and you get a distributed monolith with chatty synchronous calls for every single business operation. **Domain-Driven Design (DDD)** offers a vocabulary for cutting along the RIGHT lines: business capability, not technical layer.
--> **Bounded Context** -- the central DDD idea relevant here: a bounded context is a boundary within which a particular business term has ONE consistent meaning and model. "Order" means something different to the Catalog service (a line item referencing a product) than it does to the Shipping service (a physical package with a tracking number and address) than it does to Billing (a set of charges against a payment method). Each of those meanings lives in ITS OWN bounded context -- which, in a microservices architecture, typically becomes its own service, with its own `Order` class shaped for exactly what that service needs, not one shared "God" `Order` entity everyone fights over.
--> **A good service boundary usually lines up with a business capability**, not a database table or a technical concern:

```text
GOOD boundaries (aligned to business capability):
    - Order Service       -- owns order lifecycle: create, cancel, order status
    - Inventory Service    -- owns stock levels, reservations
    - Payment Service      -- owns charging, refunds, payment methods
    - Shipping Service     -- owns fulfillment, tracking, carrier integration
    - Notification Service -- owns emails/SMS/push, triggered by events from the above

BAD boundaries (aligned to technical layer, not business capability):
    - "Database Service"       -- just a thin CRUD wrapper around a table, no real business logic
    - "Validation Service"     -- validation logic belongs INSIDE the service that owns the data
    - "Utility Service"        -- a grab-bag with no clear single responsibility
```

--> **Signals you've drawn a boundary well:**
  - --> The service can be described in ONE sentence of business language ("owns everything about processing a payment") without an "and" joining two unrelated responsibilities.
  - --> The service's data rarely (ideally never) needs to be joined, in a single query, with another service's data -- if you find yourself wanting a SQL `JOIN` across two services' tables, that's often a sign the boundary is wrong or the two "services" should really be one.
  - --> A single business change (e.g. "add gift-wrapping" as an option) mostly touches ONE service, not five.
--> **Signals you've drawn it badly:**
  - --> Every meaningful feature requires changing 3+ services in lockstep, deployed together -- this is the "distributed monolith" smell.
  - --> Two services constantly need each other's internal data via chatty synchronous calls just to do their own job.
  - --> You can't describe what a service "owns" without listing five unrelated nouns.
--> **Practical note** -- getting boundaries perfectly right on day one is rare even for experienced teams. DDD's real value is giving a shared vocabulary (bounded context, ubiquitous language, aggregate) so a team can reason about and later RESHAPE boundaries deliberately, rather than accidentally ending up with whatever split the org chart happened to produce.

# Communication Styles -- Synchronous vs Asynchronous

--> Once services are split, they need to talk to each other, and HOW they talk shapes the whole system's resilience and coupling characteristics.

## Synchronous Communication (Request/Response)

--> The calling service sends a request and BLOCKS (or at least the logical flow waits) until the called service responds -- typically REST over HTTP, or gRPC. This chapter's sibling chapter (03 -- Inter-Service Communication) covers the Java-level mechanics (`RestTemplate`, `WebClient`, Feign) in depth; this section is about the architectural tradeoff.
--> **Pros** -- simple mental model (it's just a function call over the network), immediate consistency (the caller KNOWS the result before proceeding), easy to reason about for request/response-shaped interactions (e.g. "look up this user's profile before rendering the page").
--> **Cons** -- **temporal coupling**: if the called service is down or slow, the CALLER is affected too -- a chain of synchronous calls (A calls B calls C) means C being slow makes A slow, and C being DOWN can make the whole chain fail unless resilience patterns (circuit breakers, timeouts -- chapter 04) are deliberately added. More services in the synchronous call chain means more cumulative latency and more compounding failure probability.

```text
Synchronous call chain -- a failure or slowdown in C ripples all the way back to A:

Client -> [Order Service] --(REST, blocking)--> [Inventory Service] --(REST, blocking)--> [Warehouse Service]
             A                                        B                                          C
```

## Asynchronous Communication (Messaging / Events)

--> The calling service publishes a MESSAGE or EVENT to a broker (e.g. Kafka, RabbitMQ) and moves on immediately -- it does NOT wait for (and often doesn't even know about) whichever service(s) eventually consume that message. The consuming service(s) process the message on their own schedule.
--> **Pros** -- **temporal decoupling**: the publisher doesn't care if a consumer is temporarily down (the message waits in the broker/queue until the consumer catches up), naturally supports one event fanning out to MULTIPLE independent consumers without the publisher knowing or caring how many, and smooths out load spikes (the queue acts as a buffer rather than every downstream service getting hit synchronously at once).
--> **Cons** -- harder to reason about (the flow isn't a simple linear call stack -- it's "publish and hope"), the caller can't get an immediate return value (no "here's the created order's ID" synchronously -- you either poll, get notified later, or restructure the flow), and it requires running/operating a message broker as new infrastructure.

```text
Asynchronous event flow -- Order Service doesn't wait on (or even know about) who's listening:

[Order Service] --publishes "OrderPlaced" event--> [Message Broker (Kafka/RabbitMQ)]
                                                              |
                                    +-------------------------+-------------------------+
                                    v                         v                         v
                          [Inventory Service]        [Notification Service]     [Analytics Service]
                          (reserves stock)            (sends confirmation email)  (logs the event)
```

--> **Choosing between them -- a rule of thumb**: use SYNCHRONOUS when the caller genuinely needs an immediate answer to proceed (e.g. "is this payment authorized, yes or no, right now") -- the user is waiting, and there's no meaningful way to defer the answer. Use ASYNCHRONOUS when the caller just needs to NOTIFY the rest of the system that something happened, and doesn't need to know the outcome immediately, or when one event should fan out to several independent consumers (e.g. "an order was placed" triggers inventory reservation, a confirmation email, and an analytics event, none of which the Order Service needs to wait on).
--> Real systems mix both -- a checkout flow might synchronously call Payment (needs an immediate authorize/decline decision) while ASYNCHRONOUSLY publishing "OrderPlaced" for inventory, shipping, and notifications to react to independently.

# The Hard Part -- Data Consistency and Distributed Transactions

--> In a monolith, "place an order, reserve inventory, and charge the customer" can be one `@Transactional` method wrapping three repository calls in a single database transaction -- if anything fails, the whole thing rolls back atomically, and the database guarantees it. Split those three responsibilities into three services with three separate databases, and that guarantee is GONE -- there is no single database transaction spanning a network call to another service's separate database.
--> **Why distributed transactions (2PC) are mostly avoided in practice** -- the classical answer, "Two-Phase Commit" (2PC), coordinates a commit across multiple databases, but it's rarely used in modern microservices because it requires all participants to be available and responsive during the ENTIRE transaction (blocking, reduces availability -- exactly what microservices are trying to avoid), doesn't scale well across network boundaries and heterogeneous datastores, and many popular datastores (most NoSQL stores, and even some usage patterns of Kafka) don't support the XA transaction protocol 2PC relies on at all.
--> **The practical alternative -- eventual consistency**, accepting that the system will be TEMPORARILY inconsistent (Order says "placed" for a moment before Inventory has confirmed reservation) but converges to a consistent state shortly after, via one of these patterns:

## The Saga Pattern

--> A **saga** breaks a business transaction that spans multiple services into a SEQUENCE of local transactions, each in one service, where each step publishes an event/message that triggers the next step. If a step fails partway through, previously completed steps are undone via explicit **compensating transactions** (not a database rollback -- an explicit "undo" business operation, e.g. "release the reserved inventory" rather than a magic rollback).

```text
Happy path (Order Saga):
    1. Order Service:      create order (status = PENDING)         -> publish "OrderCreated"
    2. Inventory Service:  reserve stock                             -> publish "StockReserved"
    3. Payment Service:    charge customer                           -> publish "PaymentCharged"
    4. Order Service:      mark order CONFIRMED                      (saga complete)

Failure path -- payment fails at step 3, so steps 1-2 must be compensated:
    3. Payment Service:    charge FAILS                               -> publish "PaymentFailed"
    2'. Inventory Service: compensating action -- RELEASE the reserved stock
    1'. Order Service:     compensating action -- mark order CANCELLED (not silently deleted)
```

--> **Two common saga coordination styles**:
  - --> **Choreography** -- no central coordinator; each service listens for the previous service's event and reacts (publishes its own event) on its own. Simple for a small number of steps, but the OVERALL flow isn't visible in any one place -- you have to mentally trace event subscriptions across services to understand the whole saga.
  - --> **Orchestration** -- a dedicated orchestrator service (or workflow engine) explicitly calls each step in sequence and explicitly triggers compensations on failure. The overall flow is visible in ONE place (the orchestrator's code/config), at the cost of that orchestrator becoming a more central, more critical piece of infrastructure.
--> **Deep Dive -- idempotency is non-negotiable in async systems** -- message brokers commonly guarantee "at-least-once" delivery, meaning a consumer MUST be prepared to receive and process the SAME message more than once without corrupting state (e.g. double-charging a customer, double-reserving stock). Every message handler that has a side effect needs either a natural idempotency check (e.g. "has this order ID already been charged?") or an explicit deduplication mechanism (tracking processed message IDs).

## Other Consistency Patterns Worth Knowing

--> **Outbox pattern** -- solves the "how do I atomically update my database AND publish an event" problem (writing to your own DB and publishing to a message broker are two separate systems, so a crash between the two leaves them inconsistent). The fix: write the event into an "outbox" table in the SAME local database transaction as the business data change, then a separate background process reads the outbox table and publishes those events to the broker, retrying safely since it's just re-reading already-committed rows.
--> **CQRS (Command Query Responsibility Segregation)** -- separates the model used to WRITE data from the model used to READ it, often letting each service maintain its own local, denormalized read-copy of data it needs from other services (kept in sync via the events those other services publish), rather than querying across the network for every read. Reduces synchronous coupling at the cost of eventual (not immediate) consistency on the read side.

# Common Gotchas

--> **Splitting by technical layer instead of business capability** -- a "Database Service" or "Validation Service" is a red flag; it isn't a bounded context, it's a technical concern smeared across a network call for no benefit.
--> **Sharing a database across services** -- the single most common way to accidentally build a "distributed monolith." If two services read/write the same tables, they can't evolve or deploy independently no matter how separate their codebases look, and a schema change becomes a cross-team coordination problem again.
--> **Assuming synchronous calls are "free"** -- every synchronous hop adds latency and a new failure mode; a request touching 6 services synchronously is fragile and slow compared to 1-2 hops plus asynchronous fan-out for the rest.
--> **Forgetting idempotency in event handlers** -- "at-least-once" delivery is the norm, not the exception, for most message brokers; a handler that isn't safe to run twice WILL eventually run twice in production.
--> **Reaching for 2PC/distributed transactions by default** -- it's rarely the right tool given its availability cost; reach for sagas + compensating transactions and embrace eventual consistency instead, reserving strict consistency for the few things that truly need it (usually kept inside a SINGLE service's database, not spread across several).
--> **Splitting into microservices before understanding the domain** -- premature splitting, before boundaries are well understood, tends to produce boundaries that need to be redrawn later, which is far more painful across separate deployed services than within one codebase's module structure.

# Best Practices Summary

--> Prefer a well-structured modular monolith until a REAL organizational or scaling pain justifies the operational cost of splitting -- don't split by default.
--> Draw service boundaries around business capabilities (bounded contexts), never around technical layers -- a service should be describable in one sentence without an "and."
--> Give each service its own database; never let two services share tables -- shared data is the #1 cause of a "distributed monolith."
--> Use synchronous calls where an immediate answer is truly needed; use asynchronous events/messaging for notifications and fan-out to multiple independent consumers.
--> Accept eventual consistency across service boundaries deliberately, using sagas with explicit compensating transactions rather than reaching for distributed transactions (2PC).
--> Design every message/event handler to be idempotent -- assume "at-least-once" delivery as the default, not the exception.
--> Use the outbox pattern when a service needs to atomically update its own data AND publish an event about that change.
