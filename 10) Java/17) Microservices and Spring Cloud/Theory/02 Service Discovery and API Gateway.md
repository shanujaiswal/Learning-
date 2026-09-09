# The Problem -- Who Knows Where Everyone Else Lives?

--> In a monolith, one module calling another is just a Java method call -- no addresses involved. In microservices, Service A calling Service B needs B's network address (host + port), but in any real deployment (containers, Kubernetes, auto-scaling groups, cloud VMs), instances of B come and go constantly: new instances start during a deploy, old ones are terminated, instances are added/removed as load scales, and IP addresses are rarely stable across restarts. Hardcoding `http://192.168.1.42:8080` into Service A's config is fragile the moment ANY of that changes -- which, in production, is constantly.
--> **Service Discovery** solves this: instead of hardcoding addresses, services register themselves (or are registered) with a central **Service Registry**, and callers ask the registry "give me a healthy instance of `inventory-service`" at call time, getting back a currently-valid address.

# Client-Side vs Server-Side Discovery

--> There are two architecturally different places the "which instance do I call" decision can be made.

## Client-Side Discovery

--> The CALLING service itself queries the registry directly, gets back a list of healthy instances, and picks one (often via client-side load balancing). Spring Cloud's Netflix Eureka + Spring Cloud LoadBalancer is the classic example of this style in the Spring ecosystem.

```text
Client-Side Discovery flow:

[Order Service] --1. "where is inventory-service?"--> [Eureka Registry]
       |                                                      |
       | <--2. list of healthy instances: [10.0.0.5:8080, 10.0.0.9:8080]
       |
       +--3. picks one (client-side load balancing) and calls it directly--> [Inventory Service instance]
```

--> **Pros** -- no extra network hop at call time beyond the registry lookup (which is usually cached locally and refreshed periodically, not queried on every single call), load-balancing logic lives with the caller so it can be smart about retries/failover per-call.
--> **Cons** -- every calling service needs a discovery-client LIBRARY for whatever language/stack it's written in -- painful in a polyglot environment (a Python service and a Java service both need their own working Eureka client).

## Server-Side Discovery

--> The caller talks to a single, well-known endpoint (a load balancer or gateway), and THAT component queries the registry and routes the request on the caller's behalf -- the caller doesn't need any discovery-client code at all, it just makes a normal HTTP call to a fixed address. Kubernetes' built-in Service abstraction (covered in chapter 06) and cloud load balancers are classic server-side discovery mechanisms; an API Gateway (covered below) commonly plays this role too.

```text
Server-Side Discovery flow:

[Order Service] --plain HTTP call to a fixed address--> [Load Balancer / Gateway]
                                                                |
                                                                +--queries registry, forwards to a healthy instance--> [Inventory Service instance]
```

--> **Pros** -- calling services stay simple (no discovery library needed), works naturally in polyglot environments, centralizes load-balancing/routing logic in one place.
--> **Cons** -- the load balancer/gateway itself is now a piece of infrastructure that must be highly available (a single point of failure if not itself replicated), and adds a network hop.
--> **In practice** -- teams running on Kubernetes typically lean on Kubernetes' own Service discovery (server-side, via `kube-proxy` and DNS) rather than adding Eureka on top; teams running Spring Cloud outside Kubernetes (VMs, or Kubernetes without wanting to depend on its native mechanisms) commonly use Eureka for client-side discovery instead. Both are valid; don't run both at once for the same purpose without a specific reason.

# Eureka -- Spring Cloud's Classic Service Registry

--> **Netflix Eureka** (wrapped as `spring-cloud-starter-netflix-eureka-server` / `-client`) is a service registry where instances REGISTER themselves on startup, send periodic **heartbeats** to prove they're still alive, and get automatically DEREGISTERED if heartbeats stop (the instance crashed or was shut down without a clean deregistration).

## Running a Eureka Server

```java
// EurekaServerApplication.java -- a dedicated Spring Boot app that IS the registry.
// Requires: spring-cloud-starter-netflix-eureka-server

@SpringBootApplication
@EnableEurekaServer                 // turns this app into a Eureka registry
public class EurekaServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
```

```yaml
# application.yml for the Eureka SERVER itself
server:
  port: 8761                              # Eureka's traditional default port

eureka:
  client:
    register-with-eureka: false           # the registry itself doesn't need to register with... itself
    fetch-registry: false
  server:
    enable-self-preservation: true        # protects against mass-deregistration during network blips (see gotchas)
```

## Registering a Service as a Eureka Client

```yaml
# application.yml for a REGULAR microservice (e.g. inventory-service) that registers WITH Eureka
spring:
  application:
    name: inventory-service              # this becomes the service's discoverable ID -- crucial, must be consistent

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
  instance:
    prefer-ip-address: true              # register by IP rather than hostname -- usually more reliable in containers
```

```java
// Nothing extra is needed on the client beyond the dependency
// (spring-cloud-starter-netflix-eureka-client) and the config above --
// Spring Boot auto-registers the app with Eureka on startup using
// spring.application.name as the service ID other services will look it up by.
@SpringBootApplication
public class InventoryServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }
}
```

--> **Deep Dive -- the Eureka "self-preservation mode" gotcha** -- Eureka assumes that if it suddenly stops receiving heartbeats from a LARGE fraction of registered instances at once, that's more likely a NETWORK PARTITION between Eureka and its clients than every one of those instances actually dying simultaneously -- so it stops aggressively evicting instances from the registry ("self-preservation mode") to avoid a false mass-deregistration during a network blip. This is usually the right call in production, but it's a common source of confusion in local/dev environments (where a service really did crash, yet stays listed as "UP" in the Eureka dashboard for a while) -- don't panic if a genuinely-dead local instance lingers in the registry for a bit under self-preservation.
--> Consul (HashiCorp) is a common alternative to Eureka, offering the same registry/heartbeat concept plus built-in key-value config storage and more sophisticated health-check definitions (HTTP check, TCP check, script check) -- conceptually interchangeable with Eureka for the "service discovery" role, and Spring Cloud has a `spring-cloud-starter-consul-discovery` starter mirroring the Eureka one.

# API Gateway -- the Single Front Door

--> An **API Gateway** sits in front of all your microservices and is the ONE entry point external clients (web apps, mobile apps, third parties) talk to -- it then routes each incoming request to the correct backend service. Clients never call `inventory-service` or `payment-service` directly; they call the gateway, and the gateway figures out (often via service discovery) where to actually send the request.

```text
                                    +----------------------+
                                    |     API Gateway       |
[Web Client]  ---HTTPS--->         |  (Spring Cloud Gateway)|
[Mobile App]  ---HTTPS--->         |------------------------|
                                    |  routes based on path  |
                                    +----------+-------------+
                                               |
                +------------------------------+------------------------------+
                v                              v                              v
      [Order Service]              [Inventory Service]             [Payment Service]
```

--> **Why not let clients call each service directly?**
  - --> **Cross-cutting concerns in one place** -- authentication/authorization, rate limiting, request logging, and CORS handling belong in ONE gateway layer instead of duplicated across every single service.
  - --> **Hides internal topology** -- clients don't need to know how many services exist, how they're split, or their internal addresses -- the gateway presents one stable external API surface even as the internal service boundaries evolve.
  - --> **Single place for TLS termination** -- HTTPS certificates are managed once, at the gateway, rather than on every backend service.
  - --> **Protocol translation** -- a gateway can expose REST/JSON externally while some backend services use gRPC or another protocol internally.
--> **Costs to be aware of** -- the gateway is now a critical piece of infrastructure (must be highly available, typically run as multiple replicated instances behind its own load balancer) and adds one extra network hop to every single request.

# Spring Cloud Gateway -- Basics

--> **Spring Cloud Gateway** is Spring's reactive (built on Project Reactor / Spring WebFlux, non-blocking under the hood) API gateway. Routes are defined either declaratively in YAML or programmatically as Java `RouteLocator` beans.

## Declarative Routes via YAML

```yaml
# application.yml for the gateway itself
spring:
  cloud:
    gateway:
      routes:
        - id: order-service-route                       # unique route name, for identification/logging
          uri: lb://order-service                        # "lb://" == load-balance across Eureka-registered instances
          predicates:
            - Path=/api/orders/**                         # matches any request path starting with /api/orders/
          filters:
            - StripPrefix=0                               # keep /api/orders as-is when forwarding (0 = strip nothing)

        - id: inventory-service-route
          uri: lb://inventory-service
          predicates:
            - Path=/api/inventory/**
          filters:
            - AddRequestHeader=X-Gateway-Source, spring-cloud-gateway   # tags every forwarded request

        - id: payment-service-route
          uri: lb://payment-service
          predicates:
            - Path=/api/payments/**
            - Method=GET,POST                             # only forward GET/POST -- other methods 404 at the gateway
```

--> **Predicates** decide WHETHER a route matches an incoming request (path, HTTP method, header presence, query param, time window, and more can all be predicates, and multiple predicates on one route are AND-ed together). **Filters** modify the request/response as it passes through a matched route (add/remove headers, rewrite the path, add retry behavior, strip a path prefix before forwarding).
--> **`lb://service-name`** is the key integration point with service discovery -- rather than a literal `http://host:port`, `lb://order-service` tells the gateway "look up `order-service` in the discovery registry (Eureka/Consul/Kubernetes Service) and load-balance across whatever healthy instances are currently registered."

## Programmatic Routes via a RouteLocator Bean

```java
// Equivalent to the YAML routes above, expressed as Java config --
// useful when route logic needs to be conditional/dynamic rather than static.
@Configuration
public class GatewayRoutesConfig {

    @Bean
    public RouteLocatorBuilder.Builder orderRoute(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("order-service-route", r -> r
                        .path("/api/orders/**")
                        .filters(f -> f.addRequestHeader("X-Gateway-Source", "spring-cloud-gateway"))
                        .uri("lb://order-service"));
    }
}
```

## Common Built-In Filters

| Filter | What it does |
|---|---|
| `StripPrefix=N` | Removes the first N path segments before forwarding (e.g. `/api/orders/5` -> `/orders/5` with `StripPrefix=1`) |
| `AddRequestHeader` | Adds a header to the outgoing (forwarded) request |
| `AddResponseHeader` | Adds a header to the response sent back to the original client |
| `RewritePath` | Regex-based path rewriting, more flexible than `StripPrefix` |
| `RequestRateLimiter` | Rate-limits requests (commonly backed by Redis) -- protects backend services from being overwhelmed |
| `CircuitBreaker` | Wraps the route with a Resilience4j circuit breaker (chapter 04) -- routes to a fallback URI on failure |
| `Retry` | Automatically retries a failed forwarded request a configured number of times |

--> **Global filters** (implementing `GlobalFilter`) apply to EVERY route rather than one specific route -- the natural place for cross-cutting concerns like authentication token validation or centralized request logging that shouldn't be repeated per-route.

```java
// A simple GlobalFilter -- runs for every request through the gateway, regardless of route.
@Component
public class AuthHeaderCheckFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (authHeader == null || authHeader.isBlank()) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();     // short-circuits -- never reaches the backend service
        }
        return chain.filter(exchange);                        // let the request continue through the chain
    }

    @Override
    public int getOrder() {
        return -1;                                            // lower values run earlier in the filter chain
    }
}
```

# Common Gotchas

--> **Forgetting `lb://` and hardcoding a host/port in a route's `uri`** -- defeats the entire point of combining the gateway with service discovery; if an instance's address changes, the hardcoded route silently breaks.
--> **Treating the gateway as a place for BUSINESS logic** -- a gateway should route, authenticate, rate-limit, and log; it should NOT contain domain rules ("is this order eligible for a discount") -- that belongs in the owning service, otherwise the gateway becomes a second distributed monolith hub.
--> **Not running the gateway itself as multiple replicated instances** -- since every single request flows through it, a single-instance gateway is a textbook single point of failure.
--> **Eureka self-preservation confusion in local dev** -- covered above; a genuinely-stopped instance can linger as "UP" in the registry for a while, which can make gateway routing look broken when it's actually the registry being cautious.
--> **Mismatched `spring.application.name` across environments** -- the discovery registry keys services by this name; a typo or inconsistency between what a service registers as and what a route's `lb://` URI references means "service not found" at the gateway, even though the service is running and healthy.

# Best Practices Summary

--> Use client-side discovery (Eureka + Spring Cloud LoadBalancer) for Spring-heavy environments outside Kubernetes; lean on Kubernetes' native Service discovery when already running there rather than layering Eureka on top.
--> Put a single API Gateway in front of all external-facing traffic; never expose individual microservices' addresses directly to external clients.
--> Keep cross-cutting concerns (auth, rate limiting, logging, CORS) in the gateway's global filters; keep business logic entirely out of the gateway.
--> Always route via `lb://service-name` (or your platform's discovery-aware scheme) rather than hardcoded addresses, so the gateway automatically follows instances as they scale up/down or move.
--> Run the gateway itself as multiple, independently healthy replicas -- it is a single point of failure otherwise, by construction, since all traffic passes through it.
--> Combine the gateway with the resilience patterns in chapter 04 (circuit breaker, retry) at the routing layer, not just inside individual services.
