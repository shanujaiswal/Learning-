/**
 * EurekaDiscoveryClientApplication.java
 *
 * Demonstrates, with illustrative Spring Boot code:
 *     1. A Eureka CLIENT registration setup -- the @SpringBootApplication entry point
 *        of a regular microservice that registers itself with a Eureka server on startup.
 *     2. A Spring Cloud Gateway route configuration expressed as a programmatic
 *        RouteLocator @Bean (the Java alternative to declaring routes in YAML).
 *     3. A simple GlobalFilter -- a cross-cutting concern (auth header check) that
 *        applies to EVERY route passing through the gateway, not just one.
 *
 * Covers Theory chapter:
 *     17) Microservices and Spring Cloud/Theory/02 Service Discovery and API Gateway.md
 *
 * IMPORTANT -- this file is illustrative, NOT a standalone runnable program.
 * It requires real Spring Cloud infrastructure and dependencies to actually run:
 *     - spring-cloud-starter-netflix-eureka-client   (for the registering service)
 *     - spring-cloud-starter-netflix-eureka-server   (for a separate Eureka registry
 *       process -- NOT included here; this file only shows the CLIENT side)
 *     - spring-cloud-starter-gateway                 (for Spring Cloud Gateway)
 *     - a running Eureka server (typically on port 8761) for the client to register with
 *     - corresponding application.yml entries (shown in comments below) since Eureka/
 *       Gateway configuration is normally split between Java config and YAML
 *
 * None of this infrastructure exists in this repository -- these classes are meant to
 * be copied into real, separate Spring Boot projects (one for the client service, one
 * for the gateway) to see them run end-to-end.
 */

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

// =============================================================================
// PART A -- EUREKA CLIENT REGISTRATION
//
// A regular microservice (e.g. "inventory-service") that registers itself with
// a Eureka server on startup. Beyond the dependency + a matching application.yml,
// NO extra Java code is strictly required -- @EnableDiscoveryClient is shown here
// explicitly for clarity, though in modern Spring Cloud it is often auto-configured
// once the eureka-client dependency is on the classpath.
// =============================================================================

/*
 * application.yml this class expects to find alongside it (NOT a real file here --
 * shown as a comment purely for illustration of what makes registration actually work):
 *
 * spring:
 *   application:
 *     name: inventory-service        # <-- the discoverable service ID other services
 *                                     #     and the gateway's routes will look this service
 *                                     #     up by; must stay consistent across environments.
 * eureka:
 *   client:
 *     service-url:
 *       defaultZone: http://localhost:8761/eureka/
 *   instance:
 *     prefer-ip-address: true        # register by IP -- more reliable inside containers
 */
@SpringBootApplication
@EnableDiscoveryClient                 // makes registration-with-Eureka explicit and visible in code
public class EurekaDiscoveryClientApplication {
    public static void main(String[] args) {
        SpringApplication.run(EurekaDiscoveryClientApplication.class, args);
    }
}

// =============================================================================
// PART B -- SPRING CLOUD GATEWAY: PROGRAMMATIC ROUTES
//
// The Java alternative to declaring routes in application.yml. Useful when
// routing logic needs to be conditional/dynamic (e.g. built from a database or
// feature flag) rather than static YAML. This class would live in the SEPARATE
// gateway application/process, not inside inventory-service above.
// =============================================================================

@Configuration
class GatewayRoutesConfig {

    /**
     * Defines three routes, each forwarding to a different backend service by its
     * DISCOVERY-RESOLVED name via "lb://" (load-balance across whatever healthy
     * instances of that service are currently registered in Eureka) -- never a
     * literal hardcoded host:port, which would defeat the point of combining the
     * gateway with service discovery.
     */
    @Bean
    public RouteLocator customRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                // Route 1: /api/orders/** -> order-service, tagging every forwarded
                // request with a header identifying it came through the gateway.
                .route("order-service-route", r -> r
                        .path("/api/orders/**")
                        .filters(f -> f.addRequestHeader("X-Gateway-Source", "spring-cloud-gateway"))
                        .uri("lb://order-service"))

                // Route 2: /api/inventory/** -> inventory-service, stripping zero path
                // segments (kept explicit here to mirror the YAML equivalent in the Theory file).
                .route("inventory-service-route", r -> r
                        .path("/api/inventory/**")
                        .filters(f -> f.stripPrefix(0))
                        .uri("lb://inventory-service"))

                // Route 3: /api/payments/** -> payment-service, restricted to GET/POST only --
                // any other HTTP method on this path simply won't match this route.
                .route("payment-service-route", r -> r
                        .path("/api/payments/**")
                        .and().method("GET", "POST")
                        .uri("lb://payment-service"))

                .build();
    }
}

// =============================================================================
// PART C -- A SIMPLE GLOBALFILTER
//
// Applies to EVERY route above, regardless of which one matched -- the natural
// place for cross-cutting concerns (auth, logging) that shouldn't be repeated
// per-route. This class would also live in the gateway's own project.
// =============================================================================

/**
 * Rejects any request that doesn't carry an Authorization header BEFORE it ever
 * reaches a backend service. A real implementation would validate a JWT/token's
 * signature and expiry here, not just check for the header's mere presence --
 * simplified deliberately for illustration.
 */
@Component
class AuthHeaderCheckFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");

        if (authHeader == null || authHeader.isBlank()) {
            // Short-circuits the chain -- the backend service is NEVER called at all
            // for an unauthenticated request; the gateway fails fast on its own.
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        // Header present -- let the request continue through the rest of the
        // filter chain (any per-route filters) and on to the matched backend service.
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -1;   // lower values run earlier -- this check runs before most other filters
    }
}
