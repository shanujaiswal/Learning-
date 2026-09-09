# Beyond the Basics -- How Kafka Actually Behaves Under the Hood

--> The previous file introduces Kafka as a durable, replayable event log and shows the basic producer/consumer API. This file covers the mechanics that determine Kafka's actual real-world behavior under load and failure -- how partitioning and consumer groups genuinely distribute work, exactly what "reliable delivery" means and doesn't mean, what happens to a message that repeatedly fails processing, what ordering guarantees you can and can't rely on, and how a schema registry keeps producers and consumers from silently drifting apart -- all of which apply specifically to Kafka, not RabbitMQ or Celery, which have their own, different mechanics.

# Partitioning -- How a Topic Is Actually Split for Scale

--> A Kafka Topic (introduced in the previous file) is not one single log -- it's split into multiple PARTITIONS, each an independent, ordered, append-only log in its own right, and each partition physically lives on (and can be replicated across) specific broker nodes.

```
Topic "orders" split into 3 partitions:

Partition 0: [msg0][msg3][msg6][msg9] ...
Partition 1: [msg1][msg4][msg7]       ...
Partition 2: [msg2][msg5][msg8]       ...

Each partition is independently ordered and appended-to -- but there is
NO global ordering across partitions 0, 1, and 2 combined.
```

--> **Why partitions exist at all** -- a single log can only be read as fast as one consumer can read it; splitting a topic into multiple partitions lets MULTIPLE consumers (within the same consumer group, below) read DIFFERENT partitions in PARALLEL, which is the entire mechanism behind Kafka's high throughput claim from the previous file.
--> **How a message is assigned to a partition** -- by default, a message with a KEY is deterministically hashed to always land in the SAME partition (`hash(key) % num_partitions`); a message with no key is distributed round-robin (or, in recent Kafka versions, "sticky" batches for efficiency) across partitions.

```python
producer.send("orders", key=b"user_42", value=b"OrderPlaced:123")
# Every message with key "user_42" ALWAYS lands in the SAME partition --
# this is precisely what guarantees ordering for that one user's events,
# covered further in the Ordering Guarantees section below.
```

## Consumer Groups and Rebalancing

--> A Consumer Group (briefly named in the previous file's `group_id`) is a set of consumers cooperatively sharing the work of reading a topic -- Kafka guarantees each partition is consumed by AT MOST ONE consumer WITHIN a given group at any moment, which is exactly what makes parallel consumption safe without two consumers duplicating work on the same partition.

```
Topic "orders" -- 3 partitions, Consumer Group "analytics_service" with 3 consumers:

Partition 0 --> Consumer A
Partition 1 --> Consumer B
Partition 2 --> Consumer C
(Perfectly balanced -- each consumer handles exactly one partition)

If a 4th consumer joins the SAME group:
  It sits idle -- there are only 3 partitions, so a 4th consumer in the
  group has nothing left to be assigned; you cannot get more PARALLELISM
  within one group than you have partitions.
```

--> **Rebalancing** -- whenever a consumer joins or leaves a group (a new instance scales up, an existing one crashes or is redeployed), Kafka's group coordinator triggers a rebalance -- reassigning partitions among the group's CURRENT members so every partition still has exactly one owner.

```
Before: Consumer A owns partitions [0, 1], Consumer B owns [2]
Consumer A crashes
Rebalance triggers: Consumer B is reassigned to own [0, 1, 2] -- all partitions,
                     until a replacement consumer joins and another rebalance
                     redistributes again
```

--> **Why rebalancing is a real operational concern, not just a background detail** -- during a rebalance, consumption from the affected partitions PAUSES briefly while ownership is reassigned ("stop-the-world" rebalancing in older Kafka versions; newer "cooperative/incremental" rebalancing minimizes this by only reassigning the SPECIFIC partitions that actually need to move, rather than revoking every consumer's every partition and reassigning from scratch). Frequent rebalances (e.g. from a consumer that keeps crashing and rejoining, or one that's too slow and gets kicked for missing its heartbeat) create a real throughput cost, which is why production Kafka consumers tune `session.timeout.ms`/`max.poll.interval.ms` carefully rather than leaving defaults unexamined.
--> **The practical scaling rule this implies** -- to actually gain more parallel consumption capacity, you must increase the TOPIC's partition count (a decision usually made upfront or planned carefully, since it also changes key-to-partition assignment for keyed messages, described above) -- simply adding more consumer instances beyond the partition count buys you nothing, they just sit idle.

# Delivery Semantics -- What "Reliable" Actually Means

--> The previous file's RabbitMQ example shows `basic_ack` as "confirm successful processing," but doesn't unpack the actual guarantee that provides, or the genuinely different guarantees possible -- a distinction that matters enormously for anything touching money, inventory, or any other operation where duplicates or silent loss are unacceptable.

## At-Most-Once

```
Producer sends message --> Consumer receives it --> Consumer CRASHES before
                                                       finishing processing
Result: the message is LOST -- it is never retried, since nothing tracked
        that it wasn't successfully completed.
```

--> The weakest guarantee -- a message might be processed once, or might be silently dropped entirely if a failure happens at the wrong moment. Achieved by a consumer that commits its offset (marks a message as "done") BEFORE actually finishing processing it -- fast, but unsafe for anything that can't tolerate silent loss.

## At-Least-Once

```
Producer sends message --> Consumer processes it --> Consumer CRASHES
                              before committing its offset/ack
Result: on restart/rebalance, the message is REDELIVERED and processed
        AGAIN, since Kafka has no record that it was already handled --
        the message is never lost, but MAY be processed more than once.
```

--> The overwhelmingly common default in real Kafka deployments -- a consumer processes a message, THEN commits its offset (marking it as consumed) only after processing genuinely succeeds. If a crash happens between processing and committing, the same message is redelivered and processed AGAIN on recovery. **This directly demands idempotent consumer logic** -- connecting straight back to the API Design file's Idempotency Keys section -- a consumer charging a credit card on "OrderPlaced" must recognize and skip an order it's already charged, or a redelivered message duplicates the charge.

## Exactly-Once

```
The genuinely hard guarantee -- each message is processed, and its effects
committed, EXACTLY one time, with NO possibility of loss or duplication.
```

--> Kafka achieves this specifically for Kafka-to-Kafka pipelines (reading from one topic, producing to another, e.g. within Kafka Streams) via idempotent producers (each message carries a sequence number, so a broker can detect and discard an accidental retry-duplicate) combined with transactional writes spanning both the offset commit and the output message, so both happen atomically or neither does.
--> **The important, easy-to-miss caveat** -- true exactly-once semantics apply cleanly within Kafka's own ecosystem, but the moment a consumer's processing has a SIDE EFFECT outside Kafka (calling an external payment API, writing to a separate database), Kafka's internal exactly-once guarantee can't extend to that external system's behavior -- achieving effectively-exactly-once end-to-end still requires the consumer's OWN idempotency (checking "have I already done this external side effect for this specific message" before doing it again), the same discipline at-least-once processing already demands. In practice, most real systems build "effectively exactly-once" behavior on TOP of Kafka's at-least-once default, via idempotent consumer logic, rather than relying purely on Kafka's narrower internal exactly-once feature.

# Dead-Letter Queues -- Handling Messages That Keep Failing

--> What happens when a consumer genuinely CANNOT process a specific message no matter how many times it's retried (a malformed payload, a referenced record that was deleted, a bug triggered only by this one message's specific content)? Without a deliberate strategy, that one poison message can block an ENTIRE partition -- since a consumer must process messages from a partition in order, a message stuck failing forever prevents every message BEHIND it in that same partition from ever being reached at all.

```
Consumer reads message #501 --> processing throws an error --> retry
                              --> throws again --> retry (a few times, per policy)
                              --> still failing -->
                              Route message #501 to a separate "orders-dlq" topic
                              instead of retrying forever, and COMMIT past it,
                              letting message #502 onward proceed normally.
```

```python
def process_message(message):
    try:
        handle_order(message)
    except PermanentError as e:
        producer.send("orders-dlq", key=message.key, value=message.value)
        log.error(f"Sent to DLQ: {message.key}, reason: {e}")
    # Either way, the consumer's offset advances past this message --
    # the main partition is never permanently blocked by one bad message.
```

--> A Dead-Letter Queue is simply another Kafka topic, dedicated to holding messages that failed processing beyond some retry policy -- letting the main pipeline keep flowing while a human or a separate, dedicated reprocessing job investigates and (if the underlying issue gets fixed) replays the DLQ's messages back into the main flow later, rather than losing them or blocking everything behind them indefinitely.
--> **The design decision that matters most** -- distinguishing TRANSIENT failures (a downstream service briefly unavailable -- worth simply retrying with backoff) from PERMANENT failures (a malformed message that will never successfully process no matter how many retries) -- routing a merely transient failure straight to the DLQ prematurely means losing a message that would have succeeded on the next attempt, while retrying a permanently-broken message forever is exactly the partition-blocking problem the DLQ exists to prevent.

# Message Ordering Guarantees

--> Kafka's actual ordering guarantee is narrower than "messages come out in the order they went in" -- understanding exactly WHERE that guarantee applies (and where it doesn't) is essential for correctness.

```
GUARANTEED: strict ordering WITHIN a single partition.
  Partition 0: [OrderCreated][OrderPaid][OrderShipped] for order #123
  -- these three events, if all keyed to land in partition 0, are
  guaranteed to be read by their consumer in EXACTLY this order.

NOT guaranteed: ordering ACROSS different partitions.
  If order #123's events land in partition 0, and order #456's events
  land in partition 1, there is NO guarantee about the relative order
  a consumer sees events from #123 versus events from #456 -- they're
  simply on independent, unrelated logs.
```

--> **Why this is exactly why keying matters** -- from the Partitioning section above, all messages sharing the SAME key always land in the SAME partition -- keying every event for a given order by that order's ID (rather than leaving messages unkeyed, or keying by something else) is precisely what guarantees THAT order's own events are processed in the correct sequence, while accepting that events for a DIFFERENT, unrelated order have no defined relative ordering with respect to this one -- which is almost always the actually-correct requirement (you care that order #123's events process in order; you rarely care whether order #123's events are processed before or after unrelated order #456's).

# Schema Registry -- Keeping Producers and Consumers in Agreement

--> The previous file's examples send raw bytes/strings -- in practice, most production Kafka deployments enforce a defined SCHEMA for every message, using a format like Avro (or Protobuf), tracked centrally by a Schema Registry, specifically to prevent producers and consumers silently drifting out of agreement about a message's shape -- directly the same underlying problem the API Design file's Contract Testing section solves for synchronous REST APIs, now applied to asynchronous event streams instead.

```json
// An Avro schema for an "OrderPlaced" event, registered centrally
{
  "type": "record",
  "name": "OrderPlaced",
  "fields": [
    { "name": "orderId", "type": "string" },
    { "name": "total", "type": "double" },
    { "name": "status", "type": "string" }
  ]
}
```

```python
from confluent_kafka.avro import AvroProducer

producer = AvroProducer({
    "bootstrap.servers": "localhost:9092",
    "schema.registry.url": "http://localhost:8081",
})
producer.produce(topic="orders", value={"orderId": "123", "total": 99.99, "status": "created"},
                  value_schema=order_schema)
# The producer serializes according to the REGISTERED schema, and the registry
# rejects a produced message that doesn't conform to it, catching a malformed
# or drifted message BEFORE it ever reaches a consumer at all.
```

--> **Schema evolution rules** -- the registry enforces compatibility rules (commonly "backward compatible": a NEW schema version can still be read by consumers using the OLD schema, typically by requiring new fields to have a default value) when a producer tries to register an updated schema -- directly preventing the exact class of silent breaking change (a field renamed, a type changed) that the API Design file's OpenAPI/Contract Testing sections address for REST APIs, now enforced automatically at PRODUCE time for event streams instead of needing a separate consumer-side contract test to catch it after the fact.
--> **Why this matters more for Kafka specifically than for RabbitMQ/Celery** -- Kafka's retained, replayable log (from the previous file) means OLD messages, written under an OLD schema version, may be read by a consumer running NEW code months later during a replay -- schema evolution compatibility rules are what make that safe, ensuring a consumer can always correctly read both historical and current messages regardless of exactly when either the producer or consumer's code was last deployed.
