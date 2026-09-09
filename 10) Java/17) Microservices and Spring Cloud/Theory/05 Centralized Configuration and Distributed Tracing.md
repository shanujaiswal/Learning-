# Two New Problems That Only Show Up Once You Have Many Services

--> Chapters 01-04 covered how services find each other, talk to each other, and survive each other's failures. Two more problems only become painful once a system has grown to genuinely MANY services: (1) **configuration sprawl** -- the same database URL, feature flag, or API key duplicated across a dozen `application.yml` files, edited a dozen separate times, and inevitably drifting out of sync; and (2) **observability across boundaries** -- a single user request that flows through five services is now impossible to debug by just "reading the logs," because the logs are five separate files with no shared thread of correlation between them. This chapter covers **Spring Cloud Config Server** for the first problem and **distributed tracing** for the second.

# Centralized Configuration with Spring Cloud Config Server

--> **Spring Cloud Config Server** externalizes configuration OUT of each individual service's own `application.yml` and into a single, centrally-managed source (most commonly a Git repository) that every service fetches its config from at startup (and optionally refreshes at runtime).

```text
                        +---------------------------+
                        |   Git Repo (config-repo)   |
                        |  order-service.yml          |
                        |  inventory-service.yml       |
                        |  application.yml (shared)     |
                        +-------------+---------------+
                                      |
                                      v
                        +---------------------------+
                        |   Config Server            |
                        |  (spring-cloud-config-      |
                        |   server)                   |
                        +-------------+---------------+
                                      |
                    +-----------------+-----------------+
                    v                                    v
          [Order Service]                     [Inventory Service]
        fetches order-service.yml           fetches inventory-service.yml
        at startup via Config Client         at startup via Config Client
```

## Setting Up the Config Server

```java
// ConfigServerApplication.java -- a dedicated Spring Boot app that IS the config server.
// Requires: spring-cloud-config-server

@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
```

```yaml
# application.yml for the Config Server itself
server:
  port: 8888

spring:
  cloud:
    config:
      server:
        git:
          uri: https://github.com/example-org/config-repo    # the Git repo holding every service's config
          default-label: main                                  # branch to read from
          search-paths: '{application}'                        # optional: per-service subfolder convention
```

## Consuming Config as a Client Service

```yaml
# bootstrap.yml (or application.yml with spring.config.import in newer Spring Cloud versions)
# for a REGULAR microservice, e.g. inventory-service
spring:
  application:
    name: inventory-service          # used to fetch inventory-service.yml from the config repo
  config:
    import: "configserver:http://localhost:8888"
  cloud:
    config:
      profile: production            # fetches inventory-service-production.yml if it exists, layered over inventory-service.yml
```

--> **Config resolution order** mirrors Spring's normal profile-layering logic, just sourced from Git instead of the local classpath: a shared `application.yml` in the config repo applies to ALL services, `inventory-service.yml` applies only to that service, and `inventory-service-production.yml` layers profile-specific overrides on top -- more specific sources win over more general ones.
--> **Refreshing config without a restart** -- by default, config is fetched once at startup. Adding `spring-boot-starter-actuator` and annotating a bean `@RefreshScope` lets a `POST /actuator/refresh` call re-fetch config and rebuild that bean with the new values, without restarting the whole service. At larger scale, **Spring Cloud Bus** (backed by Kafka or RabbitMQ) broadcasts a single refresh trigger to ALL service instances at once, rather than needing to call `/actuator/refresh` on every instance individually.

```java
@RefreshScope                     // this bean is rebuilt (not just re-read) when a refresh event fires
@Component
public class FeatureFlags {

    @Value("${feature.new-checkout-flow:false}")
    private boolean newCheckoutFlowEnabled;

    public boolean isNewCheckoutFlowEnabled() {
        return newCheckoutFlowEnabled;
    }
}
```

--> **Secrets do NOT belong in the config repo in plaintext** -- even a private Git repo is the wrong place for raw database passwords or API keys, since git history retains everything forever and access to the repo is broader than access to the secret should be. Config Server supports encrypting values (`{cipher}...` prefixed values, decrypted server-side using a configured key or a Hardware Security Module), but the more common modern pattern is to keep genuinely sensitive secrets OUT of Config Server entirely, in a dedicated secrets manager (Vault, AWS Secrets Manager, Kubernetes Secrets) instead, and use Config Server only for non-secret application configuration.

# Distributed Tracing -- Following One Request Across Many Services

--> Inside a single service, a stack trace or a sequential log file is enough to reconstruct "what happened." The moment a request crosses a service boundary, that stops working -- Service A's logs and Service B's logs are two unrelated files with no way, by default, to tell that a specific log line in each came from the SAME originating user request. **Distributed tracing** solves this by attaching a shared identifier to a request as it flows through every service it touches, so every log line and every recorded operation can be tied back to the same end-to-end journey.

## Trace ID, Span ID, and the Trace Tree

```text
Trace ID: 4bf92f3577b34da6a3ce929d0e0e4736    (the SAME value across every service in this one request's journey)

  [Gateway]         Span: gateway-handle-request       (root span)
      |
      +--> [Order Service]   Span: create-order         (child span, references gateway span as parent)
              |
              +--> [Inventory Service]  Span: check-stock    (child of create-order)
              |
              +--> [Payment Service]    Span: charge-card     (child of create-order)
```

--> A **trace** is the entire end-to-end journey of one originating request; a **span** is one unit of work within that journey (typically one operation inside one service, or one outbound call) -- spans form a tree via parent/child relationships, and the SAME trace ID is attached to every span in the tree, letting a tracing backend reconstruct the full picture and render it as a timeline/waterfall diagram showing exactly which service or call was slow.

## Micrometer Tracing (formerly Spring Cloud Sleuth) + Zipkin

--> **Micrometer Tracing** is the modern Spring Boot 3+ mechanism for generating and propagating trace/span IDs automatically (Spring Cloud Sleuth is its predecessor, now superseded). It instruments common entry/exit points automatically -- incoming HTTP requests, outgoing `RestTemplate`/`WebClient`/Feign calls, `@KafkaListener` methods -- so most of the propagation happens with ZERO manual code, just the right dependencies and config.

```yaml
# application.yml -- shared across every service participating in tracing
management:
  tracing:
    sampling:
      probability: 1.0            # 1.0 = trace every request (fine for dev; lower in high-traffic production, e.g. 0.1)
  zipkin:
    tracing:
      endpoint: http://localhost:9411/api/v2/spans   # where finished spans are shipped for storage/visualization
```

```text
Every log line, once Micrometer Tracing is active, is automatically enriched with the
current trace and span IDs (via MDC -- see the Logging chapter), so a log aggregator
(Logback/Log4j2 output shipped to a central log store) can filter "show me every log
line from every service for THIS ONE trace ID":

2024-06-01 10:15:22 [order-service,4bf92f3577b34da6a3ce929d0e0e4736,00f067aa0ba902b7] INFO  OrderService - creating order for user 42
2024-06-01 10:15:22 [inventory-service,4bf92f3577b34da6a3ce929d0e0e4736,7a1d3f9c2b4e5678] INFO  InventoryService - checking stock for product 99
                                        ^-- SAME trace ID, different span ID, different service -- now correlatable
```

--> **Zipkin** (or Jaeger, a comparable alternative) is the backend that COLLECTS spans shipped from every instrumented service and renders them as a searchable, visual trace timeline -- showing exactly how long each hop took and where time was actually spent for a specific slow or failed request, which is otherwise close to impossible to reconstruct from scattered per-service logs alone.
--> **Correlation IDs beyond tracing IDs** -- some teams additionally propagate a business-level correlation ID (e.g. an order ID or a customer-facing request ID) alongside the tracing infrastructure's own trace ID, specifically so a support engineer or customer-facing log search can use an ID that means something business-wise, not just an opaque trace hash.
--> **Sampling matters at scale** -- tracing every single request (`probability: 1.0`) is fine in development and even viable in moderate-traffic production, but at very high request volumes, tracing (and shipping/storing) 100% of requests becomes its own significant overhead and storage cost; production systems commonly sample a smaller percentage (e.g. 1-10%) while still tracing 100% of ERRORS specifically, since those are the traces you most need when debugging.

# Common Gotchas

--> **Storing genuine secrets in the Config Server's Git-backed config repo in plaintext** -- covered above; use encryption or a dedicated secrets manager instead, never rely on "it's a private repo" as the only protection.
--> **Not using `@RefreshScope` on beans that read config expected to change at runtime** -- a `POST /actuator/refresh` re-fetches config values, but a bean built once at startup (without `@RefreshScope`) keeps its ORIGINAL values forever unless the whole service restarts.
--> **Calling `/actuator/refresh` on every instance manually instead of using Spring Cloud Bus** -- doesn't scale past a handful of instances; Bus broadcasts one refresh event to every subscribed instance at once via the shared message broker.
--> **Assuming tracing "just works" across an async boundary** -- trace context propagation across a `@KafkaListener` message or a manually-spawned thread requires the tracing library's async-aware instrumentation to be correctly wired; naive manual threading can silently lose the trace/span context, breaking the correlation the whole chapter is about.
--> **Tracing 100% of requests at high production traffic without considering cost** -- both the tracing overhead itself and the storage/query cost of the tracing backend scale with sampled volume; tune sampling deliberately rather than defaulting to "trace everything" indefinitely once traffic grows.

# Best Practices Summary

--> Externalize configuration that varies by environment (URLs, feature flags, non-secret settings) into Spring Cloud Config Server rather than duplicating `application.yml` values across every service's own repo.
--> Keep genuine secrets out of the config repo -- use encrypted values or, better, a dedicated secrets manager (Vault, cloud-native secret stores).
--> Use `@RefreshScope` plus Spring Cloud Bus for config that needs to change at runtime across many instances without a full restart.
--> Adopt Micrometer Tracing (or Sleuth on older Spring Cloud versions) with a backend like Zipkin as soon as a system has more than a couple of services calling each other -- retrofitting tracing after a production incident is much harder than having it from the start.
--> Tune trace sampling deliberately as traffic grows, and always trace error responses at a higher rate than successful ones.
--> Consider a business-meaningful correlation ID alongside tracing IDs when support/debugging workflows benefit from a human-readable identifier.
