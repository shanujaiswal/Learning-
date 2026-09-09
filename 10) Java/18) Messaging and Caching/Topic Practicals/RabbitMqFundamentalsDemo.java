/**
 * RabbitMqFundamentalsDemo.java
 *
 * Illustrates:
 *     1. Raw RabbitMQ Java client: declaring a topic exchange, a durable
 *        queue, binding them with a routing-key pattern, and publishing
 *     2. Manual acknowledgment (basicAck/basicNack), prefetch (basicQos),
 *        and a dead-letter-exchange (DLX) configuration for poison messages
 *     3. Spring AMQP equivalents: @Configuration beans for Exchange/Queue/
 *        Binding, a RabbitTemplate-based producer, and a @RabbitListener consumer
 *     4. Example application.properties for Spring Boot auto-configuration
 *
 * Covers Theory chapter:
 *     10) Java/18) Messaging and Caching/Theory/03 RabbitMQ Fundamentals for
 *     Java Developers.md
 *
 * IMPORTANT -- THIS FILE DOES NOT COMPILE OR RUN ON ITS OWN.
 *     It requires a running RabbitMQ broker and the corresponding client
 *     dependencies on the classpath. It is illustrative only -- copy/paste
 *     and adapt the relevant sections into a real project.
 *
 * To actually run this (conceptually):
 *     1. Start RabbitMQ locally, e.g. via Docker (includes the management UI):
 *          docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3-management
 *     2. Add dependencies (Maven):
 *          <dependency>
 *              <groupId>com.rabbitmq</groupId>
 *              <artifactId>amqp-client</artifactId>
 *              <version>5.21.0</version>
 *          </dependency>
 *          <!-- For the Spring AMQP section further below: -->
 *          <dependency>
 *              <groupId>org.springframework.boot</groupId>
 *              <artifactId>spring-boot-starter-amqp</artifactId>
 *          </dependency>
 */

package com.example.messaging.rabbitmq;

import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.MessageProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

public class RabbitMqFundamentalsDemo {

    public static void main(String[] args) throws IOException, TimeoutException {
        declareTopologyAndPublish();
        consumeWithManualAcknowledgment();
        // springAmqpExample() -- see the Spring-annotated classes further
        // below; they require a Spring application context and are shown
        // separately since they are not invoked from a plain main().
    }

    // =========================================================================
    // 1) DECLARING EXCHANGE / QUEUE / BINDING, AND PUBLISHING (raw Java client)
    // =========================================================================
    static void declareTopologyAndPublish() throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        factory.setUsername("guest");
        factory.setPassword("guest");

        try (Connection connection = factory.newConnection();
             Channel channel = connection.createChannel()) {

            // Declare a TOPIC exchange -- producers never publish directly to
            // a queue, they publish to an exchange, which routes based on
            // bindings. `durable=true` means the exchange survives a broker restart.
            channel.exchangeDeclare("order-events", BuiltinExchangeType.TOPIC, true);

            // Dead-letter exchange (DLX) setup -- any message rejected without
            // requeue, or that expires via TTL, is republished here instead of
            // vanishing or looping forever as a "poison message."
            channel.exchangeDeclare("dlx-exchange", BuiltinExchangeType.FANOUT, true);
            channel.queueDeclare("dlx-queue", true, false, false, null);
            channel.queueBind("dlx-queue", "dlx-exchange", "");

            Map<String, Object> queueArgs = new HashMap<>();
            queueArgs.put("x-dead-letter-exchange", "dlx-exchange");
            queueArgs.put("x-message-ttl", 60000); // messages older than 60s are dead-lettered

            // Declare a durable queue (survives broker restart) with the DLX
            // argument attached.
            channel.queueDeclare("billing-queue", true, false, false, queueArgs);

            // Bind the queue to the exchange using a topic routing-key
            // PATTERN: "*" matches exactly one word, "#" matches zero or more
            // words. "order.*.created" matches "order.us.created" and
            // "order.eu.created" but not "order.created" (missing the region word).
            channel.queueBind("billing-queue", "order-events", "order.*.created");

            // Publish a message with a specific routing key and PERSISTENT
            // delivery mode. NOTE: durable queue + persistent message are two
            // SEPARATE settings that must BOTH be true for the message to
            // survive a broker restart.
            String message = "{\"orderId\":\"123\"}";
            channel.basicPublish("order-events", "order.us.created",
                    MessageProperties.PERSISTENT_TEXT_PLAIN,
                    message.getBytes(StandardCharsets.UTF_8));

            System.out.println("Published message with routing key 'order.us.created'");
        }
    }

    // =========================================================================
    // 2) CONSUMING WITH MANUAL ACKNOWLEDGMENT + PREFETCH
    // =========================================================================
    static void consumeWithManualAcknowledgment() throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        factory.setUsername("guest");
        factory.setPassword("guest");

        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        // Prefetch limits how many UNACKNOWLEDGED messages the broker will
        // hand this consumer at once -- without it, a fast producer and slow
        // consumer can let the broker push its entire backlog into the
        // consumer's memory at once. A small prefetch throttles delivery to
        // match actual processing rate.
        channel.basicQos(10);

        boolean autoAck = false; // manual ack -- the recommended default for anything important

        channel.basicConsume("billing-queue", autoAck, (consumerTag, delivery) -> {
            String body = new String(delivery.getBody(), StandardCharsets.UTF_8);
            try {
                processOrder(body);
                // Only ack AFTER successful processing -- tells the broker
                // "safe to remove this message now."
                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            } catch (Exception e) {
                // requeue=true puts the message back for redelivery;
                // requeue=false (combined with the DLX argument set up above)
                // routes it to the dead-letter exchange instead of retrying forever.
                try {
                    channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, true);
                } catch (IOException nackFailure) {
                    nackFailure.printStackTrace();
                }
            }
        }, consumerTag -> { /* consumer cancelled callback -- no-op here */ });

        // In a real application the channel/connection stay open for the
        // life of the process; left open here deliberately since consumption
        // is asynchronous/callback-driven.
    }

    static void processOrder(String payload) {
        System.out.println("Processing order payload: " + payload);
    }

    // =========================================================================
    // 3) SPRING AMQP -- @Configuration topology beans, RabbitTemplate producer,
    //    @RabbitListener consumer (illustrative; requires a Spring Boot
    //    application context with spring-boot-starter-amqp on the classpath)
    // =========================================================================

    /*
     * Declaring the exchange/queue/binding as Spring BEANS instead of
     * imperative channel.exchangeDeclare()/queueDeclare()/queueBind() calls.
     * Would normally live in its own RabbitConfig.java file.
     *
     * import org.springframework.amqp.core.Binding;
     * import org.springframework.amqp.core.BindingBuilder;
     * import org.springframework.amqp.core.Queue;
     * import org.springframework.amqp.core.TopicExchange;
     * import org.springframework.context.annotation.Bean;
     * import org.springframework.context.annotation.Configuration;
     *
     * @Configuration
     * public class RabbitConfig {
     *
     *     @Bean
     *     public TopicExchange orderEventsExchange() {
     *         return new TopicExchange("order-events", true, false);
     *     }
     *
     *     @Bean
     *     public Queue billingQueue() {
     *         return new Queue("billing-queue", true);
     *     }
     *
     *     @Bean
     *     public Binding billingBinding(Queue billingQueue, TopicExchange orderEventsExchange) {
     *         return BindingBuilder.bind(billingQueue)
     *                 .to(orderEventsExchange)
     *                 .with("order.*.created");
     *     }
     * }
     */

    /*
     * Producing side -- RabbitTemplate wraps a Channel and exposes
     * Spring-style convertAndSend methods. Would normally live in its own
     * OrderEventPublisher.java file.
     *
     * import org.springframework.amqp.rabbit.core.RabbitTemplate;
     * import org.springframework.stereotype.Service;
     *
     * @Service
     * public class OrderEventPublisher {
     *
     *     private final RabbitTemplate rabbitTemplate;
     *
     *     public OrderEventPublisher(RabbitTemplate rabbitTemplate) {
     *         this.rabbitTemplate = rabbitTemplate;
     *     }
     *
     *     public void publishOrderCreated(String region, String payload) {
     *         // (exchange, routingKey, payload) -- mirrors the raw
     *         // channel.basicPublish call above.
     *         rabbitTemplate.convertAndSend("order-events", "order." + region + ".created", payload);
     *     }
     * }
     */

    /*
     * Consuming side -- @RabbitListener replaces the manual basicConsume call
     * entirely. Would normally live in its own BillingListener.java file.
     *
     * import org.springframework.amqp.rabbit.annotation.RabbitListener;
     * import org.springframework.stereotype.Component;
     *
     * @Component
     * public class BillingListener {
     *
     *     @RabbitListener(queues = "billing-queue")
     *     public void onOrderCreated(String payload) {
     *         // By default, Spring AMQP uses AcknowledgeMode.AUTO at the
     *         // Spring level -- acks after the listener method returns
     *         // normally, and nacks-with-requeue if it throws. This is NOT
     *         // the same as raw autoAck=true; it still gives at-least-once
     *         // semantics unless explicitly configured otherwise.
     *         System.out.println("processing: " + payload);
     *     }
     * }
     */

    /*
     * application.properties:
     *
     *     spring.rabbitmq.host=localhost
     *     spring.rabbitmq.port=5672
     *     spring.rabbitmq.username=guest
     *     spring.rabbitmq.password=guest
     *     spring.rabbitmq.listener.simple.prefetch=10
     *     spring.rabbitmq.listener.simple.acknowledge-mode=auto
     */
}
