# What Kafka Is, and Why It's Different From a Traditional Broker

--> Apache Kafka is a **distributed event streaming platform** -- at its core, it's a durable, append-only, distributed COMMIT LOG that producers write to and consumers read from independently. This framing matters because Kafka behaves quite differently from a traditional message broker (like a JMS provider or RabbitMQ): messages are NOT removed from Kafka when consumed -- they simply remain on disk for a configured retention period, and any number of independent consumers can read (and re-read) the same data at their own pace.

```text
TRADITIONAL BROKER (e.g. RabbitMQ queue)          KAFKA
   Consumer reads message -> message REMOVED         Consumer reads message -> message STAYS
   from the queue (once acked)                       (an offset just advances for THAT consumer)

   - Message is "consumed" = gone                     - Message is "consumed" = just read; still
                                                         there for replay, other consumers, etc.
   - Hard to replay history                            - Trivial to replay: reset offset, re-read
```

--> This log-based design is WHY Kafka excels at high-throughput event streaming, event sourcing, and feeding multiple independent downstream systems (analytics, search indexing, audit logs) from the same stream of events, often at throughput traditional brokers can't match, at the cost of a somewhat more complex mental model than "send to queue, consumer drains it."

# Core Concepts: Topics, Partitions, and Offsets

--> A **topic** is a named stream of messages -- the closest analogy to a JMS `Topic` or a RabbitMQ exchange's logical destination, but backed by Kafka's log structure. Producers publish to a topic; consumers subscribe to (or assign themselves) a topic.

--> A topic is split into one or more **partitions** -- this is the actual unit of parallelism and ordering in Kafka. Each partition is an independent, strictly ORDERED, append-only log. Splitting a topic into partitions is what lets Kafka scale horizontally: different partitions can live on different brokers and be consumed by different consumer instances in parallel.

```text
Topic: "orders"  (split into 3 partitions)

Partition 0:  [msg0][msg1][msg2][msg3][msg4] ...   <- strictly ordered within THIS partition
Partition 1:  [msg0][msg1][msg2] ...                <- independently ordered
Partition 2:  [msg0][msg1][msg2][msg3] ...          <- independently ordered

  - Ordering is guaranteed WITHIN a partition, NEVER across partitions of the
    same topic.
  - A message's partition is chosen by: an explicit partition number, OR a
    hash of its KEY (same key always -> same partition, giving per-key
    ordering), OR round-robin if no key is given.
```

--> An **offset** is a simple, ever-increasing integer identifying a message's position within its partition (0, 1, 2, 3, ...). Kafka does not track "has this been consumed" globally -- instead, EACH CONSUMER GROUP tracks its own current offset per partition, meaning multiple independent consumer groups can read the exact same partition at completely different positions simultaneously, without interfering with each other.

```text
Partition 0:  [0][1][2][3][4][5][6][7][8]
                              ^
Consumer Group "billing"    committed offset = 6  (next read starts at 7)

                        ^
Consumer Group "analytics"  committed offset = 3  (next read starts at 4)

Both groups read the SAME partition independently -- Kafka just remembers
"where each group currently is."
```

--> **Why per-key ordering matters in practice:** if you need all events for a given entity (e.g. all events for `orderId=123`) to be processed in the order they happened, you publish them with `orderId` as the message KEY. Kafka's default partitioner hashes the key to consistently route every message with that key to the SAME partition -- and since a partition is strictly ordered, that guarantees ordering for that entity, even though the topic as a whole has no total ordering across all its partitions.

# Consumer Groups -- How Kafka Achieves Both Queue and Pub/Sub Semantics

--> A **consumer group** is a named set of consumer instances that COOPERATE to consume a topic -- Kafka guarantees that each partition is assigned to at most ONE consumer instance within a given group at any time, spreading the topic's partitions across the group's instances.

```text
Topic "orders" has 3 partitions. Consumer group "billing-service" has 3 instances:

  Partition 0  --->  billing-service instance A
  Partition 1  --->  billing-service instance B
  Partition 2  --->  billing-service instance C

  Each message is processed by exactly ONE instance -- this is the
  "point-to-point / competing consumers" pattern from the previous chapter.

If a 4th instance joins the SAME group, it gets NO partitions (only 3 exist) --
extra instances beyond the partition count sit idle, ready to take over if
another instance dies (this is why partition count is effectively your
ceiling on consumer parallelism for a topic).
```

```text
Meanwhile, a SEPARATE consumer group "analytics-service" subscribing to the
SAME topic "orders" gets its OWN full copy of every partition, completely
independent of "billing-service"'s progress:

  Partition 0  --->  analytics-service instance X
  Partition 1  --->  analytics-service instance X  (one instance can own multiple partitions)
  Partition 2  --->  analytics-service instance X

This is the "pub/sub" pattern -- multiple groups, each seeing everything.
```

--> **This is the mechanism referenced in the previous chapter:** Kafka doesn't have separate "queue" and "topic" primitives like JMS -- ONE topic primitive, combined with the consumer-group concept, naturally yields point-to-point behavior (many instances, one group, work is spread out) or pub/sub behavior (many groups, each gets everything), simply depending on how you organize consumers into groups.

--> **Rebalancing:** whenever a consumer instance joins or leaves a group (deploy, crash, scale-up), Kafka triggers a REBALANCE -- partitions are reassigned across the group's remaining/new instances. During a rebalance, consumption pauses briefly for the affected partitions. Frequent, disruptive rebalances (e.g. from consumers that are too slow and get kicked for missing a heartbeat) are a common real-world Kafka operational headache, tuned via `session.timeout.ms`, `max.poll.interval.ms`, and (in modern Kafka) cooperative/incremental rebalancing strategies that avoid a full stop-the-world reassignment.

# The Producer API -- Basics

```java
Properties props = new Properties();
props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
// acks=all -- wait for all in-sync replicas to acknowledge before considering
// the send successful; the strongest durability setting (see below)
props.put(ProducerConfig.ACKS_CONFIG, "all");

try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
    ProducerRecord<String, String> record =
            new ProducerRecord<>("orders", "order-123", "{\"amount\":49.99}");

    // send() is ASYNCHRONOUS -- returns a Future<RecordMetadata> immediately.
    // The callback fires once the broker actually acknowledges the write
    // (or reports an error), NOT when send() itself returns.
    producer.send(record, (metadata, exception) -> {
        if (exception != null) {
            // handle failure -- log, retry, dead-letter, etc.
        } else {
            System.out.printf("delivered to partition %d, offset %d%n",
                    metadata.partition(), metadata.offset());
        }
    });
}
```

--> **The `acks` setting -- the single most important producer durability knob:**

| `acks` value | Meaning | Durability | Latency |
|---|---|---|---|
| `0` | Producer doesn't wait for any acknowledgment at all | Weakest -- messages can be silently lost | Fastest |
| `1` | Waits for the partition LEADER broker to write the message | Message survives leader crash only if it was written before the crash; can still be lost if the leader dies before replicating | Medium |
| `all` (or `-1`) | Waits for ALL in-sync replicas to acknowledge | Strongest -- survives leader failure as long as at least one in-sync replica remains | Slowest |

# The Consumer API -- Basics

```java
Properties props = new Properties();
props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
props.put(ConsumerConfig.GROUP_ID_CONFIG, "billing-service");
props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
// Disable auto-commit so we control exactly when an offset is considered "done"
props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
    consumer.subscribe(List.of("orders"));

    while (true) {
        // poll() is the heartbeat of a Kafka consumer -- it both fetches new
        // records AND signals to the broker "I'm still alive," which is why
        // a consumer that blocks too long doing business logic between polls
        // can get kicked out of its group as presumed dead (see Rebalancing above).
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));

        for (ConsumerRecord<String, String> record : records) {
            System.out.printf("partition=%d offset=%d key=%s value=%s%n",
                    record.partition(), record.offset(), record.key(), record.value());
            // ... process the record ...
        }

        // Manually commit offsets only after successful processing of the
        // whole batch -- this is what gives at-least-once semantics: a crash
        // between poll() and commitSync() causes redelivery of this batch on
        // restart, rather than silently skipping unprocessed records.
        consumer.commitSync();
    }
}
```

--> **`enable.auto.commit=true` (the default) vs manual commits:** auto-commit periodically commits the latest offset returned by `poll()` on a timer, REGARDLESS of whether your processing logic actually finished successfully -- convenient, but it can silently mark a message "done" that your code never actually finished handling (if it crashes mid-batch). Manual commits (`commitSync()`/`commitAsync()`) after processing completes is the standard practice whenever losing a record on crash is unacceptable.

# Spring Kafka -- `@KafkaListener` and `KafkaTemplate`

--> Spring Kafka wraps the raw Kafka Java client in Spring's familiar annotation-driven style, the same way Spring Data wraps JDBC/JPA.

```java
// Producing -- KafkaTemplate wraps a KafkaProducer, exposes Spring-style send methods
@Service
public class OrderEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public OrderEventPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishOrderPlaced(String orderId, String payload) {
        // (topic, key, value) -- the key drives partition assignment, exactly
        // as with the raw ProducerRecord shown above
        kafkaTemplate.send("orders", orderId, payload);
    }
}
```

```java
// Consuming -- @KafkaListener replaces the manual poll() loop entirely
@Component
public class OrderEventListener {

    @KafkaListener(topics = "orders", groupId = "billing-service")
    public void onOrderPlaced(ConsumerRecord<String, String> record) {
        // Spring manages the underlying KafkaConsumer, the poll loop, thread
        // pool, and (by default) commits the offset automatically after this
        // method returns without throwing -- configurable via a
        // ContainerFactory's AckMode for the same "commit only after success"
        // control the manual commitSync() call gave above.
        System.out.printf("received key=%s value=%s%n", record.key(), record.value());
    }
}
```

```properties
# application.properties -- Spring Boot auto-configures Kafka beans from these
spring.kafka.bootstrap-servers=localhost:9092
spring.kafka.consumer.group-id=billing-service
spring.kafka.consumer.auto-offset-reset=earliest
spring.kafka.consumer.enable-auto-commit=false
spring.kafka.producer.acks=all
```

--> **`auto-offset-reset`** governs what a consumer does the FIRST time it starts with no previously committed offset for a partition (e.g. a brand new consumer group): `earliest` replays the entire retained log from the beginning; `latest` (the Kafka client default) only sees messages produced AFTER it started, silently skipping everything already in the log. This single property is a very common source of "why is my new consumer not seeing any of the existing messages" confusion.

# Common Gotchas

--> **Assuming global ordering across a topic.** Ordering is per-partition only. If message order matters across the whole topic, you either need a single partition (killing parallelism) or you need to ensure all order-sensitive messages share the same key (and therefore the same partition).
--> **`poll()` loop starved by slow processing -> unwanted rebalance.** If your per-record processing inside the poll loop takes too long, the consumer can miss its `max.poll.interval.ms` deadline and get evicted from the group as presumed dead, even though it's still alive and working -- just slow. Offload heavy work to a separate thread pool, or tune the interval, rather than doing it all inline before the next `poll()`.
--> **Confusing "committed" with "processed."** Auto-commit (or a careless manual `commitSync()` placed too early) can mark an offset committed before your business logic actually finished, silently losing at-least-once semantics on a crash.
--> **New consumer group with `auto-offset-reset=latest` (the client default) appearing to "lose" historical data** -- it isn't lost, the new group simply starts reading from "now," not from the beginning of the retained log.
--> **Treating Kafka like a traditional queue that empties out.** Retention is TIME/SIZE based (e.g. 7 days, or a max log size), not "until consumed" -- a topic with no consumers at all still fills up and eventually old segments age out per the retention policy, they don't wait forever for a consumer that never shows up.

# Best Practices Summary

--> **Choose a partition key deliberately** -- it's your ordering and parallelism lever simultaneously; picking `null` (round-robin) is fine for order-independent data, but any entity requiring in-order processing needs a stable key.
--> **Default `acks=all` for producers where losing a message matters**, accepting the latency cost; reserve `acks=1`/`0` for genuinely loss-tolerant, high-volume telemetry-style data.
--> **Disable auto-commit and commit only after successful processing** whenever "processed" must mean "actually processed," not just "handed to my poll loop."
--> **Size partition count for your target consumer parallelism up front** -- you can increase partitions later, but doing so reshuffles key-to-partition hashing and can break existing per-key ordering guarantees for keys already in flight.
--> **Set `auto-offset-reset` deliberately per consumer group's actual need** (`earliest` for "must not miss anything," `latest` for "only care about new events going forward") rather than leaving it as an accidental default.
--> **Keep per-record processing inside `@KafkaListener`/poll loops fast**, offloading slow work asynchronously, to avoid spurious rebalances from missed poll deadlines.
