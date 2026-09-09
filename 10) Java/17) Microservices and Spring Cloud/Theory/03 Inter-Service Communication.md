# Three Ways to Make One Java Service Call Another Over HTTP

--> Once services are split (chapter 01) and can find each other (chapter 02), they need an actual mechanism, IN CODE, to make the HTTP call. Spring's ecosystem has effectively THREE generations of tooling for this, and while all three still work, they're not equally recommended for new code -- this chapter covers all three so you can both write new code with the right one AND recognize the older ones in an existing codebase.

| Tool | Style | Blocking? | Status |
|---|---|---|---|
| `RestTemplate` | Imperative, template-method | Blocking (synchronous thread-per-call) | In maintenance mode -- not deprecated for removal, but Spring itself recommends `WebClient` for new code |
| `WebClient` | Reactive, fluent/functional | Non-blocking by default (can be used blocking-style too) | Current recommendation for programmatic HTTP calls |
| Feign (`@FeignClient`) | Declarative -- define an interface, Spring generates the implementation | Blocking by default (though reactive variants exist) | Current recommendation for SERVICE-TO-SERVICE calls specifically, due to how little code it takes |

--> **The short version** -- Feign is what you reach for FIRST for typical service-to-service REST calls (it's the least code and reads the most like a natural Java method call), `WebClient` is what you reach for when you need more control (reactive composition, streaming, fine-grained per-request behavior) or you're not in a position to define a Feign interface, and `RestTemplate` is what you'll SEE in older/legacy code but shouldn't choose for anything new.

# RestTemplate -- The Original, Imperative Client

--> `RestTemplate` has been Spring's HTTP client since long before reactive programming was mainstream in the Java ecosystem -- every call BLOCKS the calling thread until the response arrives (or the call times out/errors), same mental model as any regular synchronous Java method call.

```java
@Service
public class InventoryClient {

    private final RestTemplate restTemplate;

    // In a real project, RestTemplate is usually exposed as a @Bean (often with a
    // configured connection/read timeout) rather than `new RestTemplate()` inline --
    // shown inline here for illustration.
    public InventoryClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public StockLevel getStockLevel(String productId) {
        String url = "http://inventory-service/api/inventory/" + productId;   // "inventory-service" resolved via discovery + a load-balanced RestTemplate
        return restTemplate.getForObject(url, StockLevel.class);              // BLOCKS this thread until the response arrives
    }

    public StockLevel reserveStock(String productId, int quantity) {
        String url = "http://inventory-service/api/inventory/" + productId + "/reserve";
        ReserveRequest request = new ReserveRequest(quantity);
        return restTemplate.postForObject(url, request, StockLevel.class);
    }
}
```

```java
// A load-balanced RestTemplate bean -- @LoadBalanced makes "inventory-service" in the
// URL above resolve via the discovery client (Eureka etc.) rather than being a literal hostname.
@Configuration
public class RestTemplateConfig {
    @Bean
    @LoadBalanced
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

--> **Why it's in maintenance mode** -- `RestTemplate` is fundamentally thread-blocking, which doesn't compose well with reactive stacks (Spring WebFlux) and ties up a thread-per-in-flight-call, which becomes a real resource constraint under high concurrency (many simultaneous outbound calls). It's not being REMOVED and plenty of production code still uses it correctly, but Spring's own documentation has pointed to `WebClient` as the forward-looking choice since Spring 5.

# WebClient -- The Reactive, Modern Client

--> `WebClient` (from `spring-webflux`) is built on Project Reactor's `Mono`/`Flux` reactive types -- calls are non-blocking by default, meaning the calling thread is freed up to do other work while waiting for the response, and the eventual result arrives via a callback-style reactive pipeline rather than a direct return value.

```java
@Service
public class InventoryClient {

    private final WebClient webClient;

    public InventoryClient(WebClient.Builder webClientBuilder) {
        // baseUrl often set to "http://inventory-service" and resolved via a
        // @LoadBalanced WebClient.Builder bean, mirroring RestTemplate's approach above.
        this.webClient = webClientBuilder.baseUrl("http://inventory-service").build();
    }

    // Fully reactive/non-blocking -- returns a Mono, the caller composes further reactive
    // operations onto it (map/flatMap/etc.) rather than getting a plain value back immediately.
    public Mono<StockLevel> getStockLevelReactive(String productId) {
        return webClient.get()
                .uri("/api/inventory/{id}", productId)
                .retrieve()                                          // triggers the call, starts handling the response
                .bodyToMono(StockLevel.class)                        // deserializes the JSON body into a StockLevel
                .timeout(Duration.ofSeconds(3));                     // per-call timeout, composed reactively
    }

    // WebClient CAN also be used in a blocking style when the surrounding code is
    // fully synchronous (e.g. a traditional Spring MVC controller, not WebFlux) --
    // .block() waits for the result, same end effect as RestTemplate, just via a different API.
    public StockLevel getStockLevelBlocking(String productId) {
        return webClient.get()
                .uri("/api/inventory/{id}", productId)
                .retrieve()
                .bodyToMono(StockLevel.class)
                .block(Duration.ofSeconds(3));                       // BLOCKS -- use only from non-reactive callers
    }

    public Mono<StockLevel> reserveStockReactive(String productId, int quantity) {
        ReserveRequest request = new ReserveRequest(quantity);
        return webClient.post()
                .uri("/api/inventory/{id}/reserve", productId)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(StockLevel.class);
    }
}
```

--> **`.retrieve()` vs `.exchangeToMono()`** -- `.retrieve()` is the simpler, common-case API: it automatically raises an exception (`WebClientResponseException`) for 4xx/5xx responses. `.exchangeToMono()` gives full access to the raw `ClientResponse` (status code, headers) BEFORE deciding how to handle it -- useful when you need custom handling based on status code rather than treating every non-2xx as an error.
--> **`.block()` is a deliberate escape hatch, not the default usage pattern** -- calling `.block()` inside a reactive (WebFlux) application defeats the entire point of using `WebClient` and can even deadlock the reactive event-loop threads in some configurations. `.block()` is fine when the calling code is genuinely a traditional blocking Spring MVC controller and you just want `WebClient`'s modern API/connection-pooling without restructuring the whole app reactively -- it is NOT fine mixed into otherwise-reactive WebFlux code.
--> **Deep Dive -- why non-blocking matters at scale** -- a blocking client (`RestTemplate`) ties up one OS thread per in-flight call for the ENTIRE duration of that call (connect + wait for response), so if a downstream service is slow, threads pile up waiting and the calling service can exhaust its thread pool under load, becoming unresponsive to NEW requests even for endpoints unrelated to the slow downstream call. A non-blocking client releases the thread while waiting, so far more concurrent in-flight calls can be handled with far fewer threads -- this is the core motivation behind Spring WebFlux and `WebClient` existing at all.

# Feign -- Declarative REST Clients

--> **OpenFeign** (`spring-cloud-starter-openfeign`) lets you define an HTTP client as a plain Java INTERFACE annotated with Spring MVC-style mapping annotations -- Spring generates a working implementation at startup via a dynamic proxy. No method bodies to write at all for the basic case; the interface IS the client.

```java
// The interface declares WHAT the remote endpoint looks like -- Spring Cloud OpenFeign
// generates the actual HTTP-calling implementation behind the scenes.
@FeignClient(name = "inventory-service")     // "inventory-service" resolved via the discovery client, same as lb:// in the gateway
public interface InventoryClient {

    @GetMapping("/api/inventory/{productId}")
    StockLevel getStockLevel(@PathVariable("productId") String productId);

    @PostMapping("/api/inventory/{productId}/reserve")
    StockLevel reserveStock(@PathVariable("productId") String productId, @RequestBody ReserveRequest request);
}
```

```java
// Using it is just... calling a method. No HTTP client boilerplate visible at the call site at all.
@Service
public class OrderService {

    private final InventoryClient inventoryClient;      // Spring injects the Feign-generated implementation

    public OrderService(InventoryClient inventoryClient) {
        this.inventoryClient = inventoryClient;
    }

    public void placeOrder(String productId, int quantity) {
        StockLevel stock = inventoryClient.getStockLevel(productId);   // looks like a local method call
        if (stock.getAvailable() < quantity) {
            throw new InsufficientStockException(productId);
        }
        inventoryClient.reserveStock(productId, new ReserveRequest(quantity));
        // ... continue placing the order
    }
}
```

```java
// Enabling Feign clients requires one annotation on a @Configuration or the main app class.
@SpringBootApplication
@EnableFeignClients                    // scans for @FeignClient interfaces and generates their implementations
public class OrderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
```

--> **Why Feign is the go-to for service-to-service calls specifically** -- the interface reads exactly like the REST controller it's calling (same mapping annotations, `@PathVariable`, `@RequestBody`), so there's a very short mental hop between "what does the Inventory Service's controller look like" and "what does my Feign client interface look like" -- often close to a copy/paste of the controller's method signatures. It also integrates directly with Resilience4j (fallback methods, chapter 04) and service discovery (`name = "inventory-service"` resolves the same way `lb://inventory-service` does at the gateway) with minimal extra wiring.
--> **Feign Configuration -- timeouts, logging, custom error decoding**:

```java
// Per-client configuration -- applies only to InventoryClient, not every Feign client in the app.
@Configuration
public class InventoryClientConfig {

    @Bean
    public Request.Options options() {
        // connectTimeout, readTimeout -- Feign has NO timeout by default in some versions,
        // so setting this explicitly is important (see gotchas below).
        return new Request.Options(2000, 5000);    // 2s connect timeout, 5s read timeout
    }

    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;                  // logs request method/URL/response status -- useful in dev
    }

    @Bean
    public ErrorDecoder errorDecoder() {
        return new CustomInventoryErrorDecoder();    // translate specific HTTP error codes into specific Java exceptions
    }
}

@FeignClient(name = "inventory-service", configuration = InventoryClientConfig.class)
public interface InventoryClient {
    // ...
}
```

```java
// Custom error decoder -- turns a 404 from Inventory Service into a specific,
// meaningful Java exception instead of Feign's generic FeignException.NotFound.
public class CustomInventoryErrorDecoder implements ErrorDecoder {
    private final ErrorDecoder defaultDecoder = new Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        if (response.status() == 404) {
            return new ProductNotFoundException("Product not found via inventory-service");
        }
        return defaultDecoder.decode(methodKey, response);   // fall back to Feign's default handling for everything else
    }
}
```

# Side-by-Side Comparison

```java
// The SAME call -- "get stock level for productId" -- across all three approaches:

// 1) RestTemplate -- imperative, blocking, verbose URL construction
StockLevel s1 = restTemplate.getForObject(
        "http://inventory-service/api/inventory/{id}", StockLevel.class, productId);

// 2) WebClient -- reactive-first, more setup, most control (timeouts, retries, streaming composed reactively)
StockLevel s2 = webClient.get()
        .uri("/api/inventory/{id}", productId)
        .retrieve()
        .bodyToMono(StockLevel.class)
        .block();

// 3) Feign -- declarative, reads exactly like calling a local method
StockLevel s3 = inventoryClient.getStockLevel(productId);
```

# Common Gotchas

--> **No timeout configured at all** -- both `RestTemplate` and Feign, in various versions/configurations, have historically had NO default timeout (or a very generous one), meaning a hung downstream service can hang the CALLING thread indefinitely. Always explicitly configure connect/read timeouts -- never rely on defaults.
--> **Mixing `.block()` into reactive (WebFlux) code paths** -- covered above; it can deadlock the reactive scheduler under some configurations and always defeats the non-blocking benefit `WebClient` exists to provide.
--> **Forgetting `@EnableFeignClients`** -- without it on the main application class (or a config class), `@FeignClient` interfaces are never scanned, and Spring throws `NoSuchBeanDefinitionException` when something tries to inject one.
--> **Not handling the "service not found" / "no instances available" case** -- whether via `RestTemplate` with `@LoadBalanced`, `WebClient`, or Feign, if the target service has ZERO healthy registered instances, the call fails immediately with a specific exception (not a generic timeout) -- this should be handled distinctly from "the service responded with an error," since it usually means a genuinely different problem (deployment issue, wrong service name) than a transient failure.
--> **Ignoring HTTP status codes on the response** -- `RestTemplate.getForObject` throws `RestClientException` subclasses on 4xx/5xx by default (doesn't silently return null), `WebClient.retrieve()` similarly throws by default, and Feign throws `FeignException` subclasses -- all THREE need explicit try/catch or exception-translation logic; none of them silently swallow errors, but it's easy to forget to catch/translate them into your OWN domain exceptions.
--> **Choosing `RestTemplate` for brand-new code without a specific reason** -- it still works, but there's no upside to picking it over `WebClient` or Feign for a new client in 2026-era Spring Cloud code; it mainly shows up when maintaining older services.

# Best Practices Summary

--> Default to Feign (`@FeignClient`) for straightforward service-to-service REST calls -- it's the least code, reads closest to a normal method call, and integrates cleanly with resilience patterns (chapter 04).
--> Reach for `WebClient` when you need reactive composition, streaming responses, or fine-grained per-call control that Feign's declarative style doesn't expose as easily.
--> Avoid introducing new `RestTemplate` usage in new code -- it's not broken, but it's not the forward-looking choice either.
--> Always configure explicit connect and read timeouts on whichever client you use -- never rely on "no timeout" or an overly generous default.
--> Handle "no healthy instances" as its own distinct failure case, separate from "the service responded with an error" or "the call timed out."
--> Keep Feign client interfaces' method signatures mirroring the target controller's mapping annotations closely -- it keeps the client trivially easy to verify against the real API.
--> Layer resilience (circuit breakers, retries, fallbacks -- chapter 04) on top of whichever client you choose; none of the three provide meaningful resilience on their own beyond a basic timeout.
