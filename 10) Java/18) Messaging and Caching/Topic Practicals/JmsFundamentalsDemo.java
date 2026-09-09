/**
 * JmsFundamentalsDemo.java
 *
 * Illustrates:
 *     1. The core JMS interfaces/vocabulary: ConnectionFactory, Connection,
 *        Session, Destination (Queue and Topic), MessageProducer, MessageConsumer
 *     2. A point-to-point (Queue) producer/consumer pair
 *     3. A publish/subscribe (Topic) producer/consumer pair, including a
 *        DURABLE subscription (so a subscriber can catch up on messages
 *        published while it was offline)
 *     4. Both consumer styles: blocking receive() and the async MessageListener
 *     5. Acknowledgment modes (AUTO_ACKNOWLEDGE, CLIENT_ACKNOWLEDGE,
 *        DUPS_OK_ACKNOWLEDGE) and a transacted session with commit()/rollback()
 *
 * Covers Theory chapter:
 *     10) Java/18) Messaging and Caching/Theory/01 Messaging Fundamentals and JMS.md
 *
 * IMPORTANT -- THIS FILE DOES NOT COMPILE OR RUN ON ITS OWN.
 *     JMS is a Java API SPECIFICATION, not a broker -- it requires a real JMS
 *     PROVIDER implementation on the classpath and running somewhere reachable
 *     over the network (e.g. Apache ActiveMQ "Classic" or ActiveMQ Artemis,
 *     or IBM MQ). The code below is written against the ActiveMQ Artemis/
 *     ActiveMQ "Classic" client (org.apache.activemq.ActiveMQConnectionFactory)
 *     purely as an illustrative, widely-recognized JMS provider -- swap in
 *     whichever provider's ConnectionFactory implementation your environment
 *     actually uses; the JMS interfaces above ConnectionFactory are identical
 *     regardless of provider.
 *
 * To actually run this (conceptually):
 *     1. Start a broker, e.g. ActiveMQ Classic:
 *          https://activemq.apache.org/components/classic/download/
 *          bin/activemq start                  (default broker URL: tcp://localhost:61616)
 *     2. Add the provider's JMS client dependency to your build, e.g. (Maven):
 *          <dependency>
 *              <groupId>org.apache.activemq</groupId>
 *              <artifactId>activemq-client</artifactId>
 *              <version>5.18.3</version>
 *          </dependency>
 *     3. Compile/run this class's main() against that classpath.
 */

package com.example.messaging.jms;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicSubscriber;

// NOTE: import org.apache.activemq.ActiveMQConnectionFactory; -- the concrete
// provider implementation of javax.jms.ConnectionFactory used below. Any other
// JMS-compliant provider's ConnectionFactory implementation is a drop-in swap.

public class JmsFundamentalsDemo {

    public static void main(String[] args) throws JMSException {
        pointToPointQueueExample();
        publishSubscribeTopicExample();
        durableSubscriptionExample();
        acknowledgmentModesExample();
        transactedSessionExample();
    }

    // =========================================================================
    // 1) POINT-TO-POINT (QUEUE) -- exactly one consumer instance gets each message
    // =========================================================================
    static void pointToPointQueueExample() throws JMSException {
        // ConnectionFactory is the JMS entry point -- analogous to a JDBC
        // DataSource. In a real provider this would be, e.g.:
        //     ConnectionFactory connectionFactory =
        //             new ActiveMQConnectionFactory("tcp://localhost:61616");
        ConnectionFactory connectionFactory = lookUpProviderConnectionFactory();

        try (Connection connection = connectionFactory.createConnection()) {
            // A JMS Connection is created in a STOPPED state -- no messages are
            // delivered to a MessageConsumer until start() is called. This is
            // a classic, easy-to-forget gotcha (see Theory chapter 01).
            connection.start();

            // A Session is a single-threaded context for producing/consuming
            // messages, and controls transaction/acknowledgment behavior.
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            // A Queue is a point-to-point Destination -- each message is
            // consumed by exactly ONE consumer, even with several competing
            // consumer instances reading the same queue.
            Queue ordersQueue = session.createQueue("orders");

            // --- Producer side ---
            MessageProducer producer = session.createProducer(ordersQueue);
            producer.setDeliveryMode(DeliveryMode.PERSISTENT); // survive broker restart

            TextMessage message = session.createTextMessage("{\"orderId\":\"123\",\"amount\":49.99}");
            producer.send(message);
            System.out.println("Sent order message to point-to-point queue 'orders'");

            // --- Consumer side (blocking receive() style) ---
            MessageConsumer consumer = session.createConsumer(ordersQueue);
            Message received = consumer.receive(5000); // blocks up to 5s for a message
            if (received instanceof TextMessage textMessage) {
                System.out.println("Received from queue: " + textMessage.getText());
            }

            producer.close();
            consumer.close();
            session.close();
        }
    }

    // =========================================================================
    // 2) PUBLISH/SUBSCRIBE (TOPIC) -- every subscriber gets its OWN copy
    // =========================================================================
    static void publishSubscribeTopicExample() throws JMSException {
        ConnectionFactory connectionFactory = lookUpProviderConnectionFactory();

        try (Connection connection = connectionFactory.createConnection()) {
            connection.start();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            // A Topic is a pub/sub Destination -- every active subscriber
            // receives its own independent copy of each message.
            Topic eventsTopic = session.createTopic("order-events");

            // --- Async (MessageListener) consumer style -- far more common in
            // real applications than the blocking receive() style above,
            // because it doesn't tie up a dedicated thread just to poll.
            MessageConsumer subscriberA = session.createConsumer(eventsTopic);
            subscriberA.setMessageListener(new MessageListener() {
                @Override
                public void onMessage(Message msg) {
                    try {
                        if (msg instanceof TextMessage textMessage) {
                            System.out.println("Subscriber A received: " + textMessage.getText());
                        }
                    } catch (JMSException e) {
                        // In real code: log and decide whether to
                        // acknowledge/rollback depending on ack mode.
                        e.printStackTrace();
                    }
                }
            });

            MessageConsumer subscriberB = session.createConsumer(eventsTopic);
            subscriberB.setMessageListener(msg -> {
                try {
                    if (msg instanceof TextMessage textMessage) {
                        System.out.println("Subscriber B received: " + textMessage.getText());
                    }
                } catch (JMSException e) {
                    e.printStackTrace();
                }
            });

            // --- Producer side -- publishes ONCE, both subscribers get a copy ---
            MessageProducer producer = session.createProducer(eventsTopic);
            producer.send(session.createTextMessage("OrderPlaced: orderId=123"));
            System.out.println("Published event to topic 'order-events' -- both subscribers will receive it");

            producer.close();
            subscriberA.close();
            subscriberB.close();
            session.close();
        }
    }

    // =========================================================================
    // 3) DURABLE TOPIC SUBSCRIPTION -- catches up on messages published while
    //    the subscriber was offline (an ordinary/non-durable subscriber would
    //    silently miss anything published before it started listening).
    // =========================================================================
    static void durableSubscriptionExample() throws JMSException {
        ConnectionFactory connectionFactory = lookUpProviderConnectionFactory();

        try (Connection connection = connectionFactory.createConnection()) {
            // Durable subscriptions require a stable, unique client ID so the
            // broker can re-associate a reconnecting subscriber with the
            // subscription it registered earlier.
            connection.setClientID("billing-service-instance-1");
            connection.start();

            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Topic eventsTopic = session.createTopic("order-events");

            // createDurableSubscriber (as opposed to createConsumer) tells the
            // broker to RETAIN messages published while this subscriber is
            // offline, and deliver them on reconnect under the same
            // subscription name ("billing-durable-sub").
            TopicSubscriber durableSubscriber =
                    session.createDurableSubscriber(eventsTopic, "billing-durable-sub");

            durableSubscriber.setMessageListener(msg -> {
                try {
                    if (msg instanceof TextMessage textMessage) {
                        System.out.println("Durable subscriber caught up on: " + textMessage.getText());
                    }
                } catch (JMSException e) {
                    e.printStackTrace();
                }
            });

            // ... time passes, subscriber may disconnect and reconnect later
            // with the SAME clientID + subscription name, and still receive
            // everything published in the meantime.

            durableSubscriber.close();
            session.close();
        }
    }

    // =========================================================================
    // 4) ACKNOWLEDGMENT MODES -- AUTO_ACKNOWLEDGE, CLIENT_ACKNOWLEDGE, DUPS_OK
    // =========================================================================
    static void acknowledgmentModesExample() throws JMSException {
        ConnectionFactory connectionFactory = lookUpProviderConnectionFactory();

        try (Connection connection = connectionFactory.createConnection()) {
            connection.start();

            // AUTO_ACKNOWLEDGE: simplest, but a crash mid-processing (after
            // receive()/listener invocation, before your logic finishes) can
            // lose the message -- there is no automatic redelivery.
            Session autoAckSession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            // CLIENT_ACKNOWLEDGE: your code must explicitly call
            // message.acknowledge() -- full control over exactly when a
            // message is considered "handled."
            Session clientAckSession = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
            Queue queue = clientAckSession.createQueue("orders");
            MessageConsumer clientAckConsumer = clientAckSession.createConsumer(queue);

            Message msg = clientAckConsumer.receive(1000);
            if (msg != null) {
                try {
                    processOrder(msg);
                    // Only acknowledge AFTER processing genuinely completes --
                    // if this line is never reached (crash/exception above),
                    // the broker will redeliver the message.
                    msg.acknowledge();
                } catch (Exception processingFailure) {
                    // Do NOT acknowledge -- message remains unacknowledged
                    // and will be redelivered.
                    System.err.println("Processing failed, message will be redelivered: "
                            + processingFailure.getMessage());
                }
            }

            // DUPS_OK_ACKNOWLEDGE: lazy acknowledgment for higher throughput,
            // at the cost of allowing OCCASIONAL duplicate delivery -- only
            // appropriate when the consumer is idempotent or duplicates are
            // tolerable.
            Session dupsOkSession = connection.createSession(false, Session.DUPS_OK_ACKNOWLEDGE);

            autoAckSession.close();
            clientAckConsumer.close();
            clientAckSession.close();
            dupsOkSession.close();
        }
    }

    // =========================================================================
    // 5) TRANSACTED SESSION -- groups receive + processing + acknowledgment
    //    into one transaction; a thrown exception triggers rollback(), which
    //    puts the message back for redelivery.
    // =========================================================================
    static void transactedSessionExample() throws JMSException {
        ConnectionFactory connectionFactory = lookUpProviderConnectionFactory();

        try (Connection connection = connectionFactory.createConnection()) {
            connection.start();

            // First argument `true` marks this session as TRANSACTED --
            // acknowledgment mode is then irrelevant (commit()/rollback()
            // control everything instead).
            Session transactedSession = connection.createSession(true, Session.SESSION_TRANSACTED);
            Queue queue = transactedSession.createQueue("orders");
            MessageConsumer consumer = transactedSession.createConsumer(queue);

            Message msg = consumer.receive(1000);
            if (msg != null) {
                try {
                    processOrder(msg);
                    // Commits the receive AND marks the message as consumed,
                    // atomically, as one unit.
                    transactedSession.commit();
                } catch (Exception processingFailure) {
                    // Rolling back puts the message BACK on the queue for
                    // redelivery -- as if this receive() never happened.
                    transactedSession.rollback();
                    System.err.println("Rolled back transaction, message will be redelivered");
                }
            }

            consumer.close();
            transactedSession.close();
        }
    }

    // -------------------------------------------------------------------------
    // Helper stubs -- illustrative only
    // -------------------------------------------------------------------------

    /**
     * In a real project this returns a concrete provider ConnectionFactory,
     * e.g.:
     *     return new org.apache.activemq.ActiveMQConnectionFactory("tcp://localhost:61616");
     * or a JNDI lookup in a Java EE / application-server-managed environment.
     * Left unimplemented here since this file has no provider dependency on
     * its classpath -- see the file-level disclaimer above.
     */
    static ConnectionFactory lookUpProviderConnectionFactory() {
        throw new UnsupportedOperationException(
                "Illustrative only -- wire up a real JMS provider's ConnectionFactory "
                        + "(e.g. ActiveMQConnectionFactory) pointed at a running broker to execute this code.");
    }

    static void processOrder(Message message) throws JMSException {
        if (message instanceof TextMessage textMessage) {
            System.out.println("Processing order payload: " + textMessage.getText());
        }
    }
}
