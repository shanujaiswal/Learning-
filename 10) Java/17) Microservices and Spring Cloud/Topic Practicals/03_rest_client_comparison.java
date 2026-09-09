/**
 * 03_rest_client_comparison.java
 *
 * Demonstrates, with illustrative Spring Boot code, the SAME remote call --
 * "get the current stock level for a product from inventory-service" -- made
 * three different ways, matching the three generations of HTTP client tooling
 * covered in the Theory chapter:
 *     1. RestTemplate  -- imperative, blocking (in maintenance mode, shown for completeness)
 *     2. WebClient     -- reactive, non-blocking by default (current recommendation
 *                         for programmatic HTTP calls)
 *     3. OpenFeign     -- declarative interface-based client (current recommendation
 *                         specifically for service-to-service REST calls)
 *
 * Covers Theory chapter:
 *     17) Microservices and Spring Cloud/Theory/03 Inter-Service Communication.md
 *
 * IMPORTANT -- this file is illustrative, NOT a standalone runnable program.
 * It requires real Spring Cloud dependencies and a real running "inventory-service"
 * to actually execute:
 *     - spring-boot-starter-web       (RestTemplate)
 *     - spring-boot-starter-webflux   (WebClient)
 *     - spring-cloud-starter-openfeign (Feign)
 *     - a real, separately-deployed inventory-service reachable at the URLs/names
 *       used below (via service discovery, chapter 02, or a literal address in
 *       simpler setups) -- none of which exists in this repository.
 *
 * Drop the relevant classes into a real Spring Boot project (with the matching
 * starters on the classpath) to see any of the three approaches actually run.
 */

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

// =============================================================================
// SHARED DTOs -- used identically across all three client styles below, so the
// contrast is purely about the CALLING mechanism, not the data shapes.
// =============================================================================

class StockLevel {
    private String productId;
    private int available;

    public StockLevel() { }
    public StockLevel(String productId, int available) {
        this.productId = productId;
        this.available = available;
    }

    public static StockLevel unknown(String productId) {
        return new StockLevel(productId, -1);   // -1 signals "couldn't determine" to callers
    }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public int getAvailable() { return available; }
    public void setAvailable(int available) { this.available = available; }
}

class ReserveRequest {
    private int quantity;

    public ReserveRequest() { }
    public ReserveRequest(int quantity) { this.quantity = quantity; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}

// =============================================================================
// APPROACH 1 -- RestTemplate (imperative, blocking, in maintenance mode)
// =============================================================================

/**
 * A @LoadBalanced RestTemplate bean -- makes "inventory-service" in the URL below
 * resolve via the discovery client (Eureka etc., chapter 02) rather than being
 * treated as a literal hostname. Without @LoadBalanced, RestTemplate would try to
 * do a plain DNS lookup for "inventory-service" and fail outside a container/DNS
 * setup that happens to define that hostname.
 */
class RestTemplateConfigExample {
    // In a real project this method would be annotated @Bean inside a @Configuration
    // class -- shown here as a plain method to keep this file's structure simple.
    public RestTemplate loadBalancedRestTemplate() {
        return new RestTemplate();
    }
}

@Service
class InventoryClientRestTemplate {

    private final RestTemplate restTemplate;

    public InventoryClientRestTemplate(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /** BLOCKS the calling thread until inventory-service responds (or errors/times out). */
    public StockLevel getStockLevel(String productId) {
        String url = "http://inventory-service/api/inventory/{id}";
        return restTemplate.getForObject(url, StockLevel.class, productId);
    }

    public StockLevel reserveStock(String productId, int quantity) {
        String url = "http://inventory-service/api/inventory/{id}/reserve";
        return restTemplate.postForObject(url, new ReserveRequest(quantity), StockLevel.class, productId);
    }
}

// =============================================================================
// APPROACH 2 -- WebClient (reactive, non-blocking by default)
// =============================================================================

@Service
class InventoryClientWebClient {

    private final WebClient webClient;

    /**
     * In a real project, webClientBuilder would typically come from a
     * @LoadBalanced WebClient.Builder @Bean, mirroring RestTemplate's approach
     * above, so "inventory-service" resolves via service discovery here too.
     */
    public InventoryClientWebClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl("http://inventory-service").build();
    }

    /** Fully reactive -- returns a Mono immediately; the caller composes further operations onto it. */
    public Mono<StockLevel> getStockLevelReactive(String productId) {
        return webClient.get()
                .uri("/api/inventory/{id}", productId)
                .retrieve()                                    // triggers the call
                .bodyToMono(StockLevel.class)
                .timeout(Duration.ofSeconds(3));                // per-call timeout, composed reactively
    }

    /**
     * WebClient used in a BLOCKING style -- only appropriate when the surrounding
     * code is genuinely a traditional (non-WebFlux) Spring MVC controller; never
     * mix .block() into otherwise-reactive code paths (see Theory chapter gotchas).
     */
    public StockLevel getStockLevelBlocking(String productId) {
        return webClient.get()
                .uri("/api/inventory/{id}", productId)
                .retrieve()
                .bodyToMono(StockLevel.class)
                .block(Duration.ofSeconds(3));
    }

    public Mono<StockLevel> reserveStockReactive(String productId, int quantity) {
        return webClient.post()
                .uri("/api/inventory/{id}/reserve", productId)
                .bodyValue(new ReserveRequest(quantity))
                .retrieve()
                .bodyToMono(StockLevel.class);
    }
}

// =============================================================================
// APPROACH 3 -- OpenFeign (declarative -- current recommendation for
// service-to-service REST calls specifically)
// =============================================================================

/**
 * The interface declares WHAT the remote endpoint looks like; Spring Cloud
 * OpenFeign generates the actual HTTP-calling implementation via a dynamic
 * proxy at startup. "inventory-service" is resolved via the discovery client,
 * exactly like lb://inventory-service is at the gateway (chapter 02).
 */
@FeignClient(name = "inventory-service")
interface InventoryClientFeign {

    @GetMapping("/api/inventory/{productId}")
    StockLevel getStockLevel(@PathVariable("productId") String productId);

    @PostMapping("/api/inventory/{productId}/reserve")
    StockLevel reserveStock(@PathVariable("productId") String productId, @RequestBody ReserveRequest request);
}

/** Enabling Feign clients requires this annotation on the main app class (or a @Configuration class). */
class OrderServiceApplicationFeignEnabled {
    // In a real project:
    // @SpringBootApplication
    // @EnableFeignClients
    // public class OrderServiceApplication { public static void main(String[] a) { ... } }
    //
    // Shown as a comment rather than a real duplicate @SpringBootApplication class,
    // since only one such entry point is allowed per Spring Boot application.
}

/** Using the Feign client -- calling it looks exactly like calling a local method. */
@Service
class OrderServiceUsingFeign {

    private final InventoryClientFeign inventoryClient;

    public OrderServiceUsingFeign(InventoryClientFeign inventoryClient) {
        this.inventoryClient = inventoryClient;
    }

    public void placeOrder(String productId, int quantity) {
        StockLevel stock = inventoryClient.getStockLevel(productId);   // reads like a plain method call
        if (stock.getAvailable() < quantity) {
            throw new IllegalStateException("Insufficient stock for " + productId);
        }
        inventoryClient.reserveStock(productId, new ReserveRequest(quantity));
    }
}

// =============================================================================
// SIDE-BY-SIDE -- the SAME call, all three ways, for direct visual comparison.
// =============================================================================

class SideBySideComparison {
    void compare(RestTemplate restTemplate, WebClient webClient, InventoryClientFeign inventoryClient, String productId) {
        // 1) RestTemplate -- imperative, blocking
        StockLevel s1 = restTemplate.getForObject(
                "http://inventory-service/api/inventory/{id}", StockLevel.class, productId);

        // 2) WebClient -- reactive-first, blocking style shown here for a like-for-like comparison
        StockLevel s2 = webClient.get()
                .uri("/api/inventory/{id}", productId)
                .retrieve()
                .bodyToMono(StockLevel.class)
                .block();

        // 3) Feign -- declarative, reads exactly like calling a local method
        StockLevel s3 = inventoryClient.getStockLevel(productId);
    }
}
