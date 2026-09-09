# Framing the Decision

--> By this point, three messaging technologies have been covered: JMS (an API specification, typically backed by ActiveMQ or IBM MQ in practice), RabbitMQ (an AMQP broker), and Kafka (a distributed log-based streaming platform). They are NOT interchangeable drop-in replacements for each other -- each optimizes for a different shape of problem, and picking based on "which one is most popular" rather than "which one matches my actual delivery/ordering/throughput needs" is a common, expensive architectural mistake that's painful to unwind once a system is built around the wrong one.

--> **The one-sentence mental model for each:**

```text
JMS       -- a STANDARD API for enterprise messaging; the actual behavior
             depends entirely on which broker implements it underneath
             (commonly ActiveMQ or IBM MQ in real deployments).

RabbitMQ  -- a flexible, general-purpose MESSAGE ROUTER; excellent at
             complex routing and per-message reliability for moderate
             throughput task/work distribution.

Kafka     -- a distributed, replayable, ordered EVENT LOG; excellent at
             very high-throughput streaming and feeding many independent
             downstream consumers from the same durable history.
```

# Detailed Comparison Table

| Dimension | JMS (typ. ActiveMQ) | RabbitMQ | Kafka |
|---|---|---|---|
| Core abstraction | `Queue` / `Topic` (API-level) | Exchange -> binding -> Queue | Topic -> partitions (append-only log) |
| Throughput | Moderate | Moderate-to-high (tens of thousands msg/s achievable, tuned) | Very high (can sustain millions of msg/s across a cluster) |
| Message ordering | Per-queue/per-topic-subscriber, broker-dependent | Per-queue, FIFO-ish (with caveats under redelivery/priority) | Strict, but ONLY per-partition -- no global topic ordering |
| Delivery guarantee | At-most-once / at-least-once (ack-mode dependent) | At-least-once (manual ack) or at-most-once (auto-ack) | At-least-once by default; exactly-once achievable with idempotent producers + transactional consumers |
| Message retention after consumption | Removed once acknowledged | Removed once acknowledged | Retained per a time/size policy REGARDLESS of consumption -- replayable |
| Routing flexibility | Basic (queue vs topic) | Very high -- direct/topic/fanout/headers exchanges, arbitrary binding patterns | Low -- routing is really just "which topic," partitioning via key hashing |
| Replay historical messages | Not supported (once consumed, gone) | Not supported (once acked, gone) | Native and cheap -- reset a consumer group's offset |
| Multiple independent consumer groups reading the same data | Possible via durable topic subscriptions, but clunkier | Possible by binding multiple queues to the same exchange | Native and effortless -- just create another consumer group |
| Typical use case | Legacy Java EE enterprise integration, transactional messaging within an existing JMS-centric stack | Task queues, RPC-style request/reply, complex routing between microservices, work distribution | Event sourcing, activity/audit streams, log aggregation, feeding multiple analytics/search/ML pipelines from one event stream |
| Operational complexity | Low-to-moderate (broker-dependent) | Moderate | Higher -- cluster coordination (via ZooKeeper historically, or KRaft in modern versions), partition rebalancing, disk-heavy retention |
| Protocol | Java API only (not a wire protocol) | AMQP 0-9-1 (open wire protocol, multi-language) | Custom Kafka wire protocol (well-supported client libraries in most languages) |
| Message size sweet spot | Small-to-medium | Small-to-medium | Small-to-medium, but large aggregate volumes across the whole cluster |
| Priority queues / TTL / dead-lettering | Supported, broker-dependent | Rich native support (priority queues, per-message TTL, DLX) | Not a native concept -- would be modeled at the application/consumer level |

# When to Use Which -- Decision Guidance

```text
Use JMS when:
  - You're integrating with an existing enterprise Java EE / legacy system
    already built around a JMS provider (ActiveMQ, IBM MQ).
  - You need vendor portability at the API level within the JMS ecosystem
    specifically (rare as a GREENFIELD reason to choose it today).

Use RabbitMQ when:
  - You need complex, flexible ROUTING logic (topic patterns, fanout
    broadcast, header-based routing) that a simple topic-per-stream model
    doesn't express well.
  - Your workload is closer to "distribute discrete TASKS/JOBS to workers"
    than "stream continuous EVENTS for multiple consumers to replay."
  - You want rich per-message features out of the box: priorities, TTLs,
    dead-lettering, request/reply (RPC) patterns.
  - Your throughput needs are moderate -- tens of thousands of messages per
    second is comfortably within RabbitMQ's wheelhouse; Kafka-scale
    (millions/sec sustained) is not typically the deciding factor.

Use Kafka when:
  - You need very high sustained throughput and/or want DURABLE, REPLAYABLE
    history of events (event sourcing, audit trails, "replay the last 3
    days of events into a rebuilt read model").
  - Multiple, independent downstream systems need to consume the SAME
    stream of events at their own pace (analytics, search indexing,
    notifications, billing -- all reading the same "OrderPlaced" stream).
  - You're building event-driven/streaming architectures, potentially
    layering stream-processing (Kafka Streams, ksqlDB) on top later.
  - Strict ordering is only required PER-KEY/PER-ENTITY, not globally
    across an entire topic (a very common, satisfiable requirement).
```

--> **A practical rule of thumb some teams use:** "if I need to ask a broker to REMEMBER something happened so a system built next month can react to it, that's Kafka; if I need to get a job done reliably right now, possibly with complex conditional routing, that's RabbitMQ; if I'm stuck inside an existing JMS-based enterprise stack, that's JMS." None of these are absolute laws -- plenty of production systems use RabbitMQ for event distribution or Kafka for simple task queues -- but they're a reasonable starting heuristic when the choice is otherwise open.

# It Is Common (and Fine) to Use More Than One

--> Real systems frequently combine technologies rather than picking exactly one broker for the whole architecture:

```text
Example: an e-commerce platform might use --
  - Kafka for the core "OrderPlaced", "PaymentProcessed", "ShipmentDispatched"
    event stream that analytics, fraud detection, and reporting all consume
    independently and can replay/backfill from.
  - RabbitMQ for internal task distribution -- e.g. "generate and email this
    invoice PDF," a discrete job that should go to exactly one worker, with
    retry/dead-lettering handled by RabbitMQ's native DLX support.
  - (Less common today, but plausible) JMS if a subsystem still integrates
    with a 15-year-old enterprise billing system that only speaks JMS/ActiveMQ.
```

--> The takeaway: don't treat "which message broker" as a single, permanent, org-wide decision -- treat it as a per-integration-point decision, made against the actual delivery/ordering/throughput/routing needs of THAT specific integration.

# Common Gotchas

--> **Choosing Kafka by default "because it's popular" for a simple task queue** -- Kafka's operational overhead (cluster management, partition planning, understanding consumer-group rebalancing) is often not worth paying when RabbitMQ's queue+ack model would have solved the actual problem more simply.
--> **Assuming Kafka gives global ordering** -- teams migrating from a strictly-ordered queue system to Kafka are sometimes surprised that ordering is per-partition only; this needs explicit partition-key design, not an assumption it "just works" the way a single FIFO queue did.
--> **Expecting RabbitMQ or JMS to support cheap historical replay** -- once a message is acknowledged and removed, it's gone; if "replay last week's events" is a real requirement, that's a strong signal toward Kafka (or an explicit separate event-store alongside the broker).
--> **Underestimating Kafka's operational learning curve** for a small team without prior distributed-systems/cluster-ops experience -- broker count, replication factor, partition count, and consumer-group tuning all have real consequences that a simpler broker doesn't force you to think about upfront.

# Best Practices Summary

--> **Match the technology to the SHAPE of the problem** (discrete tasks vs. replayable event streams vs. legacy API compatibility), not to general popularity or a single company-wide mandate.
--> **Design partition/routing keys deliberately in Kafka** to get the ordering guarantees you actually need, rather than assuming a topic behaves like a single ordered queue.
--> **Lean on RabbitMQ's native routing/TTL/DLX features** rather than reinventing them at the application layer when the problem is genuinely routing-shaped.
--> **Treat broker choice as a per-integration decision** in larger systems -- it's normal and often correct to run more than one messaging technology side by side, each where it fits best.
--> **Revisit the choice if requirements shift** -- a system that started as "simple task queue" (RabbitMQ-shaped) sometimes grows into "need replayable event history for five different downstream teams" (Kafka-shaped); recognize that inflection point rather than contorting the original tool to fit a fundamentally different requirement.
