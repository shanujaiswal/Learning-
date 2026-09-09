/**
 * KafkaFundamentalsDemo.java
 *
 * Illustrates:
 *     1. A raw KafkaProducer sending keyed records, with acks/durability config
 *     2. A raw KafkaConsumer poll() loop, manual offset commits, and
 *        auto.offset.reset behavior
 *     3. Spring Kafka's higher-level wrappers: a KafkaTemplate-based publisher
 *        service and a @KafkaListener-based consumer component
 *     4. Example application.properties for Spring Boot auto-configuration
 *
 * Covers Theory chapter:
 *     10) Java/18) Messaging and Caching/Theory/02 Apache Kafka Fundamentals
 *     for Java Developers.md
 *
 * IMPORTANT -- THIS FILE DOES NOT COMPILE OR RUN ON ITS OWN.
 *     It requires a running Kafka broker (or Kafka cluster) and the
 *     corresponding client dependencies on the classpath. It is illustrative
 *     only -- copy/paste and adapt the relevant sections into a real project.
 *
 * To actually run this (conceptually):
 *     1. Start Kafka locally, e.g. via Docker:
 *          docker run -d --name kafka -p 9092:9092 apache/kafka:latest
 *        (or docker-compose with Kafka + Zookeeper/KRaft per the official
 *        Kafka quickstart)
 *     2. Add dependencies (Maven):
 *          <dependency>
 *              <groupId>org.apache.kafka</groupId>
 *              <artifactId>kafka-clients</artifactId>
 *              <version>3.7.0</version>
 *          </dependency>
 *          <!-- For the Spring Kafka section further below: -->
 *          <dependency>
 *              <groupId>org.springframework.kafka</groupId>
 *              <artifactId>spring-kafka</artifactId>
 *          </dependency>
 *     3. Create the "orders" topic (or let auto-create handle it in dev):
 *          kafka-topics.sh --create --topic orders --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1
 */

package com.example.messaging.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.Duration;
import java.util.List;
import java.util.Properties;

public class KafkaFundamentalsDemo {

    public static void main(String[] args) {
        rawProducerExample();
        rawConsumerExample();
        // springKafkaProducerAndConsumerExample() -- see the Spring-annotated
        // classes further below; they require a Spring application context
        // and are shown separately since they are not invoked from a plain main().
    }

    // =========================================================================
    // 1) RAW KAFKA PRODUCER -- keyed records, acks=all for strongest durability
    // =========================================================================
    static void rawProducerExample() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        // acks=all waits for ALL in-sync replicas to acknowledge before the
        // send is considered successful -- the strongest durability setting,
        // at the cost of the highest latency (see the acks table in Theory 02).
        props.put(ProducerConfig.ACKS_CONFIG, "all");

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            // Using "order-123" as the KEY ensures every message for this
            // order ID is routed to the SAME partition (Kafka hashes the key),
            // which guarantees per-key ordering even though the topic as a
            // whole has no total ordering across all its partitions.
            ProducerRecord<String, String> record =
                    new ProducerRecord<>("orders", "order-123", "{\"amount\":49.99}");

            // send() is ASYNCHRONOUS -- it returns a Future<RecordMetadata>
            // immediately. The callback fires once the broker actually
            // acknowledges the write (or reports an error), not when send()
            // itself returns.
            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    // In real code: log and decide on retry/dead-letter handling.
                    System.err.println("Send failed: " + exception.getMessage());
                } else {
                    System.out.printf("Delivered to partition %d, offset %d%n",
                            metadata.partition(), metadata.offset());
                }
            });

            producer.flush(); // ensure the async send completes before this demo method returns
        }
    }

    // =========================================================================
    // 2) RAW KAFKA CONSUMER -- poll loop, manual commit for at-least-once semantics
    // =========================================================================
    static void rawConsumerExample() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "billing-service");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        // Disable auto-commit so offsets are only committed AFTER this
        // consumer's own processing genuinely finishes -- auto-commit would
        // otherwise mark a batch "done" purely on a timer, regardless of
        // whether processing actually completed.
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

        // "earliest" replays the whole retained log the FIRST time this
        // consumer group starts with no committed offset; the client default
        // ("latest") would silently skip everything already in the log.
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of("orders"));

            // In a real application this loop runs indefinitely on a
            // dedicated thread; here it's bounded for illustrative purposes.
            for (int iteration = 0; iteration < 3; iteration++) {
                // poll() is the heartbeat of a Kafka consumer -- it both
                // fetches new records AND signals "I'm still alive" to the
                // broker, which is why business logic between polls must stay
                // fast (see max.poll.interval.ms in Theory 02's Gotchas).
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));

                for (ConsumerRecord<String, String> record : records) {
                    System.out.printf("partition=%d offset=%d key=%s value=%s%n",
                            record.partition(), record.offset(), record.key(), record.value());
                    // ... process the record ...
                }

                // Commit only after the whole batch has been successfully
                // processed -- a crash between poll() and commitSync() causes
                // redelivery of this batch on restart (at-least-once), rather
                // than silently skipping unprocessed records.
                if (!records.isEmpty()) {
                    consumer.commitSync();
                }
            }
        }
    }

    // =========================================================================
    // 3) SPRING KAFKA -- KafkaTemplate producer + @KafkaListener consumer
    //    (illustrative; requires a Spring Boot application context with
    //    spring-kafka on the classpath to actually wire up and run)
    // =========================================================================

    /*
     * Producing side -- KafkaTemplate wraps a KafkaProducer internally and
     * exposes Spring-style send methods. Would normally live in its own
     * OrderEventPublisher.java file within a Spring Boot project.
     *
     * import org.springframework.kafka.core.KafkaTemplate;
     * import org.springframework.stereotype.Service;
     *
     * @Service
     * public class OrderEventPublisher {
     *
     *     private final KafkaTemplate<String, String> kafkaTemplate;
     *
     *     public OrderEventPublisher(KafkaTemplate<String, String> kafkaTemplate) {
     *         this.kafkaTemplate = kafkaTemplate;
     *     }
     *
     *     public void publishOrderPlaced(String orderId, String payloadJson) {
     *         // (topic, key, value) -- the key drives partition assignment,
     *         // exactly as with the raw ProducerRecord shown above.
     *         kafkaTemplate.send("orders", orderId, payloadJson);
     *     }
     * }
     */

    /*
     * Consuming side -- @KafkaListener replaces the manual poll() loop
     * entirely. Would normally live in its own OrderEventListener.java file.
     *
     * import org.apache.kafka.clients.consumer.ConsumerRecord;
     * import org.springframework.kafka.annotation.KafkaListener;
     * import org.springframework.stereotype.Component;
     *
     * @Component
     * public class OrderEventListener {
     *
     *     @KafkaListener(topics = "orders", groupId = "billing-service")
     *     public void onOrderPlaced(ConsumerRecord<String, String> record) {
     *         // Spring manages the underlying KafkaConsumer, poll loop, and
     *         // thread pool, and (by default) commits the offset
     *         // automatically after this method returns without throwing --
     *         // configurable via a ContainerFactory's AckMode for the same
     *         // "commit only after success" control commitSync() gave above.
     *         System.out.printf("received key=%s value=%s%n", record.key(), record.value());
     *     }
     * }
     */

    /*
     * application.properties -- Spring Boot auto-configures Kafka beans from these:
     *
     *     spring.kafka.bootstrap-servers=localhost:9092
     *     spring.kafka.consumer.group-id=billing-service
     *     spring.kafka.consumer.auto-offset-reset=earliest
     *     spring.kafka.consumer.enable-auto-commit=false
     *     spring.kafka.producer.acks=all
     */
}
