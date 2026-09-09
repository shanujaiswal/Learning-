/**
 * ChoosingMessagingTechnologyDemo.java
 *
 * Illustrates:
 *     A small, self-contained decision-helper: a MessagingRequirements value
 *     describing the shape of an integration problem, and a
 *     MessagingTechnologyAdvisor that recommends JMS, RabbitMQ, or Kafka
 *     based on the heuristics from the Theory chapter (throughput, need for
 *     replay, routing complexity, legacy JMS constraints, ordering scope).
 *
 *     This is a teaching aid, NOT a production decision engine -- real
 *     architectural choices should weigh far more context (team experience,
 *     existing infrastructure, operational maturity) than any simple
 *     if/else heuristic could capture. See the comparison table and
 *     "Common Gotchas" reproduced below for the full nuance.
 *
 * Covers Theory chapter:
 *     10) Java/18) Messaging and Caching/Theory/04 Choosing Between Kafka
 *     RabbitMQ and JMS.md
 *
 * IMPORTANT -- THIS FILE'S main() METHOD DOES COMPILE AND RUN STANDALONE.
 *     It uses only plain Java (no external broker/dependency needed) since
 *     it is a decision-helper/comparison illustration, not a messaging client.
 *     (Unlike files 01-03/06, there is no infrastructure requirement here.)
 *
 * ---------------------------------------------------------------------------
 * QUICK-REFERENCE SUMMARY OF THE THEORY CHAPTER'S COMPARISON TABLE
 * ---------------------------------------------------------------------------
 *
 *   Dimension                  | JMS (typ. ActiveMQ) | RabbitMQ            | Kafka
 *   ----------------------------|----------------------|----------------------|---------------------------
 *   Core abstraction            | Queue / Topic (API)  | Exchange->binding->Q | Topic->partitions (log)
 *   Throughput                  | Moderate             | Moderate-to-high     | Very high
 *   Ordering                    | Broker-dependent      | Per-queue, FIFO-ish  | Strict, but PER-PARTITION only
 *   Retention after consumption | Removed on ack        | Removed on ack       | Retained per policy -- replayable
 *   Routing flexibility         | Basic (queue/topic)   | Very high (4 exchange types) | Low (topic + key hashing)
 *   Replay historical messages  | Not supported         | Not supported        | Native and cheap
 *   Operational complexity      | Low-to-moderate       | Moderate             | Higher (cluster, partitions)
 *   Typical use case            | Legacy Java EE / enterprise integration | Task queues, RPC, complex routing | Event sourcing, audit streams, multi-consumer analytics
 *
 * ---------------------------------------------------------------------------
 * ONE-SENTENCE MENTAL MODEL FOR EACH (from the Theory chapter)
 * ---------------------------------------------------------------------------
 *   JMS       -- a STANDARD API for enterprise messaging; behavior depends
 *                entirely on the broker implementing it underneath.
 *   RabbitMQ  -- a flexible, general-purpose MESSAGE ROUTER; excellent at
 *                complex routing and per-message reliability for moderate
 *                throughput task/work distribution.
 *   Kafka     -- a distributed, replayable, ordered EVENT LOG; excellent at
 *                very high-throughput streaming and feeding many independent
 *                downstream consumers from the same durable history.
 *
 * ---------------------------------------------------------------------------
 * KEY TAKEAWAY (also from the Theory chapter)
 * ---------------------------------------------------------------------------
 *   It is common AND fine to use more than one messaging technology in the
 *   same system -- treat "which broker" as a PER-INTEGRATION decision (e.g.
 *   Kafka for a replayable event stream, RabbitMQ for task distribution with
 *   native DLX support, JMS only where a legacy system already requires it),
 *   not a single permanent org-wide mandate.
 */

package com.example.messaging.decision;

import java.util.ArrayList;
import java.util.List;

public class ChoosingMessagingTechnologyDemo {

    public static void main(String[] args) {
        // Scenario 1: high-throughput event stream feeding multiple independent
        // downstream consumers, needs replay -- should recommend Kafka.
        MessagingRequirements eventStreaming = new MessagingRequirements(
                /* estimatedMessagesPerSecond   */ 200_000,
                /* needsHistoricalReplay        */ true,
                /* needsComplexRouting          */ false,
                /* orderingScope                */ OrderingScope.PER_ENTITY_KEY,
                /* multipleIndependentConsumers */ true,
                /* mustIntegrateWithLegacyJms   */ false
        );

        // Scenario 2: discrete background jobs (e.g. "generate and email an
        // invoice PDF"), moderate throughput, wants native retry/DLX support
        // -- should recommend RabbitMQ.
        MessagingRequirements taskDistribution = new MessagingRequirements(
                /* estimatedMessagesPerSecond   */ 500,
                /* needsHistoricalReplay        */ false,
                /* needsComplexRouting          */ true,
                /* orderingScope                */ OrderingScope.NONE,
                /* multipleIndependentConsumers */ false,
                /* mustIntegrateWithLegacyJms   */ false
        );

        // Scenario 3: a subsystem must talk to a 15-year-old enterprise
        // billing system that only speaks JMS/ActiveMQ -- should recommend JMS
        // regardless of the other factors.
        MessagingRequirements legacyIntegration = new MessagingRequirements(
                /* estimatedMessagesPerSecond   */ 50,
                /* needsHistoricalReplay        */ false,
                /* needsComplexRouting          */ false,
                /* orderingScope                */ OrderingScope.NONE,
                /* multipleIndependentConsumers */ false,
                /* mustIntegrateWithLegacyJms   */ true
        );

        MessagingTechnologyAdvisor advisor = new MessagingTechnologyAdvisor();

        for (MessagingRequirements requirements : List.of(eventStreaming, taskDistribution, legacyIntegration)) {
            Recommendation recommendation = advisor.recommend(requirements);
            System.out.println("=================================================");
            System.out.println("Requirements: " + requirements);
            System.out.println("Recommended : " + recommendation.technology());
            System.out.println("Reasoning   :");
            for (String reason : recommendation.reasons()) {
                System.out.println("  - " + reason);
            }
        }
    }

    // =========================================================================
    // Domain model describing the SHAPE of an integration's needs -- deliberately
    // mirrors the "Detailed Comparison Table" dimensions from the Theory chapter.
    // =========================================================================
    enum OrderingScope {
        NONE,            // ordering doesn't matter at all
        PER_ENTITY_KEY,  // ordering matters only within a given entity/key (Kafka-friendly)
        GLOBAL_STRICT    // ordering must be strictly global across the whole stream
    }

    enum MessagingTechnology {
        JMS, RABBITMQ, KAFKA
    }

    record MessagingRequirements(
            long estimatedMessagesPerSecond,
            boolean needsHistoricalReplay,
            boolean needsComplexRouting,
            OrderingScope orderingScope,
            boolean multipleIndependentConsumers,
            boolean mustIntegrateWithLegacyJms
    ) {
    }

    record Recommendation(MessagingTechnology technology, List<String> reasons) {
    }

    // =========================================================================
    // The heuristic itself -- directly encodes the "When to Use Which" decision
    // guidance from the Theory chapter, checked in priority order.
    // =========================================================================
    static class MessagingTechnologyAdvisor {

        Recommendation recommend(MessagingRequirements req) {
            List<String> reasons = new ArrayList<>();

            // Rule 1: an existing legacy JMS-centric system is a hard constraint,
            // not a trade-off to weigh against throughput/routing -- if it's
            // true, JMS wins regardless of everything else (per Theory 04:
            // "if I'm stuck inside an existing JMS-based enterprise stack,
            // that's JMS").
            if (req.mustIntegrateWithLegacyJms()) {
                reasons.add("Must integrate with an existing JMS-centric legacy system "
                        + "(e.g. ActiveMQ/IBM MQ-backed enterprise billing) -- JMS is a hard constraint here.");
                return new Recommendation(MessagingTechnology.JMS, reasons);
            }

            // Rule 2: global strict ordering across an entire stream is NOT
            // something Kafka guarantees (only per-partition) -- if strict
            // global ordering is required, favor RabbitMQ's single-queue
            // FIFO-ish semantics over fighting Kafka's partitioning model.
            if (req.orderingScope() == OrderingScope.GLOBAL_STRICT) {
                reasons.add("Requires STRICT GLOBAL ordering across the entire stream -- "
                        + "Kafka only guarantees ordering per-partition, never topic-wide.");
                reasons.add("RabbitMQ's single-queue FIFO-ish delivery is a simpler fit "
                        + "than forcing a Kafka topic down to one partition (which kills parallelism).");
                return new Recommendation(MessagingTechnology.RABBITMQ, reasons);
            }

            // Rule 3: needing durable replay of history, OR very high sustained
            // throughput, OR multiple independent downstream consumer groups
            // reading the same stream -- all point at Kafka's log-based model.
            if (req.needsHistoricalReplay() || req.estimatedMessagesPerSecond() > 50_000
                    || req.multipleIndependentConsumers()) {
                if (req.needsHistoricalReplay()) {
                    reasons.add("Needs durable, replayable history -- Kafka retains messages per a "
                            + "time/size policy regardless of consumption, unlike RabbitMQ/JMS which "
                            + "remove messages once acknowledged.");
                }
                if (req.estimatedMessagesPerSecond() > 50_000) {
                    reasons.add("Estimated throughput (" + req.estimatedMessagesPerSecond()
                            + " msg/s) is in Kafka's sweet spot (can sustain millions/s across a cluster), "
                            + "beyond RabbitMQ's comfortable tens-of-thousands/s range.");
                }
                if (req.multipleIndependentConsumers()) {
                    reasons.add("Multiple independent downstream systems need to consume the SAME "
                            + "stream at their own pace -- native and effortless via separate Kafka consumer groups.");
                }
                if (req.orderingScope() == OrderingScope.PER_ENTITY_KEY) {
                    reasons.add("Ordering is only required per-entity/key, not globally -- a satisfiable "
                            + "requirement via a deliberate Kafka partition key, not a blocker.");
                }
                return new Recommendation(MessagingTechnology.KAFKA, reasons);
            }

            // Rule 4: default -- complex routing needs, or a moderate-throughput
            // discrete task/work-distribution shape, is RabbitMQ's wheelhouse.
            if (req.needsComplexRouting()) {
                reasons.add("Needs complex, flexible routing (topic patterns, fanout broadcast, "
                        + "header-based routing) -- RabbitMQ's exchange/binding model expresses this "
                        + "far more naturally than Kafka's 'just pick a topic' routing.");
            }
            reasons.add("Workload looks like discrete TASKS/JOBS to distribute to workers, at "
                    + "moderate throughput (" + req.estimatedMessagesPerSecond() + " msg/s) -- "
                    + "RabbitMQ's queue+ack model, prefetch tuning, and native dead-letter-exchange "
                    + "support fit this better than Kafka's operational overhead would justify.");
            return new Recommendation(MessagingTechnology.RABBITMQ, reasons);
        }
    }
}
