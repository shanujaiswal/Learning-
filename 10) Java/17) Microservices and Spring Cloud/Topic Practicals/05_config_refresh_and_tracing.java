/**
 * 05_config_refresh_and_tracing.java
 *
 * Demonstrates, with illustrative Spring Boot code:
 *     1. A @RefreshScope config bean -- a feature-flag holder that gets REBUILT
 *        (not just re-read) whenever a POST /actuator/refresh call fires, without
 *        restarting the whole service.
 *     2. A GlobalFilter that is trace-aware -- reads the current trace/span
 *        context (as Micrometer Tracing would populate it) and logs it alongside
 *        every request passing through the gateway, so log lines can later be
 *        correlated across services by trace ID.
 *
 * Covers Theory chapter:
 *     17) Microservices and Spring Cloud/Theory/05 Centralized Configuration and Distributed Tracing.md
 *
 * IMPORTANT -- this file is illustrative, NOT a standalone runnable program.
 * It requires real Spring Cloud infrastructure and dependencies to actually run:
 *     - spring-cloud-config-client                 (to fetch config from a Config Server)
 *     - spring-boot-starter-actuator                (for /actuator/refresh to exist at all)
 *     - a running Spring Cloud Config Server, backed by a real Git repo holding
 *       this service's application.yml / *-service.yml files -- none of which
 *       exists here
 *     - micrometer-tracing-bridge-brave (or otelbridge) + a Zipkin/Jaeger backend,
 *       for the "current trace ID" in Part B to actually be populated by anything
 *       meaningful -- Tracer is used here only as an illustrative injection point
 *
 * Drop these classes into a real Spring Boot project (gateway + a regular
 * microservice) with the dependencies above to see them run end-to-end.
 */

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

// =============================================================================
// PART A -- @RefreshScope CONFIG BEAN
//
// Without @RefreshScope, a bean reading @Value-injected config is built ONCE at
// startup and keeps those original values forever, even after a config refresh
// event fires -- @RefreshScope makes Spring rebuild (not just re-read) this
// specific bean whenever a refresh is triggered, so it picks up new values from
// the Config Server without restarting the whole application.
// =============================================================================

/*
 * application.yml (or bootstrap.yml) this class expects alongside it, pulling
 * config from a Spring Cloud Config Server rather than its own local file --
 * shown as a comment purely for illustration, not a real file in this repo:
 *
 * spring:
 *   application:
 *     name: order-service
 *   config:
 *     import: "configserver:http://localhost:8888"
 *   cloud:
 *     config:
 *       profile: production
 *
 * management:
 *   endpoints:
 *     web:
 *       exposure:
 *         include: refresh          # exposes POST /actuator/refresh
 *
 * # Fetched from the Config Server's order-service.yml (or a shared application.yml):
 * feature:
 *   new-checkout-flow: false
 */

@RefreshScope                     // this bean is REBUILT (not just re-read) when a refresh event fires
@Component
class FeatureFlags {

    @Value("${feature.new-checkout-flow:false}")
    private boolean newCheckoutFlowEnabled;

    public boolean isNewCheckoutFlowEnabled() {
        return newCheckoutFlowEnabled;
    }
}

/**
 * A service consuming the refreshable flag -- notice it does NOT cache the
 * flag's value itself; it re-reads FeatureFlags on every call, so a runtime
 * refresh is visible immediately without this class needing any special logic.
 */
@Component
class CheckoutService {

    private final FeatureFlags featureFlags;

    public CheckoutService(FeatureFlags featureFlags) {
        this.featureFlags = featureFlags;
    }

    public String checkout(String orderId) {
        if (featureFlags.isNewCheckoutFlowEnabled()) {
            return "Processed " + orderId + " via NEW checkout flow";
        }
        return "Processed " + orderId + " via legacy checkout flow";
    }
}

/*
 * Triggering a refresh in a real deployment (NOT executed here):
 *     curl -X POST http://localhost:8080/actuator/refresh
 * or, at scale, via Spring Cloud Bus broadcasting one refresh event to every
 * subscribed instance at once, rather than calling /actuator/refresh per instance.
 */

// =============================================================================
// PART B -- TRACE-AWARE GLOBAL FILTER
//
// A GlobalFilter (same mechanism as chapter 02's AuthHeaderCheckFilter) that
// logs the current trace ID and span ID alongside every request passing
// through the gateway. In a real Micrometer-Tracing-instrumented app, this
// context is populated automatically for incoming HTTP requests -- reading it
// explicitly here is illustrative of HOW a log line gets tied back to a trace,
// not something you'd normally need to do by hand for the common case (most
// tracing correlation happens automatically via MDC, per the Theory chapter).
// =============================================================================

@Configuration
class TracingLoggingFilterConfig {

    /**
     * Registered as a @Bean here (rather than @Component directly on the class)
     * purely to make the Tracer dependency's injection point explicit for
     * illustration -- functionally equivalent to annotating the class itself.
     */
    @Bean
    public GlobalFilter traceAwareLoggingFilter(Tracer tracer) {
        return new TraceAwareLoggingFilter(tracer);
    }
}

class TraceAwareLoggingFilter implements GlobalFilter {

    private static final Logger log = LoggerFactory.getLogger(TraceAwareLoggingFilter.class);

    private final Tracer tracer;

    TraceAwareLoggingFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Reads whatever trace/span context Micrometer Tracing has already
        // established for this request (normally populated automatically by
        // the tracing instrumentation before this filter even runs).
        Span currentSpan = tracer.currentSpan();
        String traceId = (currentSpan != null) ? currentSpan.context().traceId() : "NO-TRACE-CONTEXT";
        String spanId = (currentSpan != null) ? currentSpan.context().spanId() : "NO-SPAN-CONTEXT";

        String path = exchange.getRequest().getURI().getPath();
        String method = exchange.getRequest().getMethod().name();

        // This log line, once shipped to a central log aggregator, can be
        // correlated with the SAME trace ID's log lines from every OTHER
        // service the request also touched (Order, Inventory, Payment, ...),
        // reconstructing the full request journey across process boundaries.
        log.info("[traceId={}, spanId={}] gateway received {} {}", traceId, spanId, method, path);

        return chain.filter(exchange)
                .doOnSuccess(unused ->
                        log.info("[traceId={}, spanId={}] gateway completed {} {} with status {}",
                                traceId, spanId, method, path, exchange.getResponse().getStatusCode()));
    }
}
