/**
 * 11_webflux_annotated_and_functional_demo.java
 *
 * Demonstrates, with illustrative Spring WebFlux code:
 *     1. An annotated @RestController returning Mono<T>/Flux<T> -- the SAME
 *        annotations as Spring MVC, with reactive return types as the only
 *        structural change.
 *     2. A Server-Sent Events (SSE) streaming endpoint via a never-completing Flux.
 *     3. A functional endpoint defined via RouterFunction/HandlerFunction --
 *        WebFlux's second, annotation-free way to define routes.
 *     4. WebClient usage for calling a downstream service reactively,
 *        including timeout/retry/error-status handling.
 *
 * Covers Theory chapter:
 *     10) Java/09) Spring and Spring Boot/Theory/11 Spring WebFlux Fundamentals.md
 *
 * IMPORTANT -- this file is illustrative, NOT a standalone runnable program.
 * It requires a real Spring Boot project on the classpath to compile/run, specifically:
 *     - spring-boot-starter-webflux   (Spring WebFlux, embedded Netty by default,
 *                                       DispatcherHandler auto-registration, WebClient,
 *                                       RouterFunction/HandlerFunction support)
 * Note: spring-boot-starter-webflux and spring-boot-starter-web are normally NOT
 * combined in the same application (see file 05's theory for why) -- pick one.
 *
 * Run (in a real Spring Boot project, after wiring this into src/main/java/... and adding
 * a @SpringBootApplication class with a main() method):
 *     mvn spring-boot:run
 *   or
 *     ./gradlew bootRun
 *
 * Example requests once the app is running on the default port (8080):
 *     curl -X GET http://localhost:8080/api/products/1
 *     curl -X GET http://localhost:8080/api/products
 *     curl -N -X GET http://localhost:8080/api/products/prices/stream        (SSE stream, -N disables buffering)
 *     curl -X GET http://localhost:8080/functional/products/1               (functional endpoint equivalent)
 *     curl -X POST http://localhost:8080/api/products \
 *          -H "Content-Type: application/json" \
 *          -d "{\"name\":\"Webcam\",\"price\":39.99}"
 */

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.web.reactive.function.server.RequestPredicates.accept;

// ---------------------------------------------------------------------------
// 1) Domain types shared by both the annotated controller and the
//    functional-endpoint style below, so the two can be compared directly.
// ---------------------------------------------------------------------------

record ProductRequest(String name, BigDecimal price) { }
record ProductResponse(Long id, String name, BigDecimal price) { }
record PriceUpdate(Long productId, BigDecimal newPrice, long timestampEpochMillis) { }

class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(Long id) {
        super("Product not found with id: " + id);
    }
}

// ---------------------------------------------------------------------------
// 2) A ReactiveCrudRepository-shaped stand-in. A real project would extend
//    Spring Data R2DBC's ReactiveCrudRepository interface directly (see file
//    05's practicals for that illustration) -- here it's a hand-written
//    Mono/Flux-returning service so this file has no external DB dependency.
// ---------------------------------------------------------------------------

@Service
class ReactiveProductRepository {

    private final Map<Long, ProductResponse> store = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong(1);

    ReactiveProductRepository() {
        store.put(1L, new ProductResponse(1L, "Keyboard", new BigDecimal("49.99")));
    }

    Mono<ProductResponse> findById(Long id) {
        return Mono.justOrEmpty(store.get(id));
    }

    Flux<ProductResponse> findAll() {
        return Flux.fromIterable(store.values());
    }

    Mono<ProductResponse> save(ProductRequest request) {
        Long id = idSequence.getAndIncrement();
        ProductResponse saved = new ProductResponse(id, request.name(), request.price());
        store.put(id, saved);
        return Mono.just(saved);
    }
}

// ---------------------------------------------------------------------------
// 3) Annotated @RestController -- identical annotations to Spring MVC; the
//    only structural change is Mono<T>/Flux<T> return types instead of
//    T/List<T> directly.
// ---------------------------------------------------------------------------

@RestController
@RequestMapping("/api/products")
class ReactiveProductController {

    private final ReactiveProductRepository productRepository;

    public ReactiveProductController(ReactiveProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @GetMapping("/{id}")
    public Mono<ProductResponse> getOne(@PathVariable Long id) {
        return productRepository.findById(id)
                .switchIfEmpty(Mono.error(new ProductNotFoundException(id)));
    }

    @GetMapping
    public Flux<ProductResponse> getAll() {
        return productRepository.findAll();
    }

    // Accepting the body as Mono<ProductRequest> (rather than the plain DTO)
    // lets us flatMap immediately without WebFlux needing to fully resolve
    // the body before invoking this method -- a subtle composability win in
    // fully reactive pipelines, per the theory file.
    @PostMapping
    public Mono<ResponseEntity<ProductResponse>> create(@RequestBody Mono<ProductRequest> requestMono) {
        return requestMono
                .flatMap(productRepository::save)
                .map(created -> ResponseEntity
                        .created(URI.create("/api/products/" + created.id()))
                        .body(created));
    }

    // Streaming endpoint -- Server-Sent Events. produces = TEXT_EVENT_STREAM_VALUE
    // is required for genuinely incremental streaming semantics rather than
    // the client buffering the whole response (per the theory file's gotcha).
    @GetMapping(value = "/prices/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<PriceUpdate> streamPriceUpdates() {
        // Flux.interval never completes on its own -- it emits a tick every
        // second, standing in for a real "priceService.priceUpdates()" feed.
        return Flux.interval(Duration.ofSeconds(1))
                .map(tick -> new PriceUpdate(1L, new BigDecimal("49.99").add(BigDecimal.valueOf(tick)),
                        System.currentTimeMillis()));
    }
}

// ---------------------------------------------------------------------------
// 4) Functional endpoints -- the SAME "/api/products/{id}"-shaped behavior,
//    expressed as explicit routing code instead of annotations, mounted
//    under a different base path ("/functional/...") purely so both styles
//    can coexist in this illustrative file without clashing.
// ---------------------------------------------------------------------------

@Component
class ProductHandler {   // NOT annotated -- a plain class whose methods match HandlerFunction's shape

    private final ReactiveProductRepository productRepository;

    public ProductHandler(ReactiveProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Mono<ServerResponse> getOne(ServerRequest request) {
        Long id = Long.valueOf(request.pathVariable("id"));
        return productRepository.findById(id)
                .flatMap(product -> ServerResponse.ok().bodyValue(product))
                .switchIfEmpty(ServerResponse.notFound().build());
    }

    public Mono<ServerResponse> getAll(ServerRequest request) {
        Flux<ProductResponse> products = productRepository.findAll();
        return ServerResponse.ok().body(products, ProductResponse.class);
    }

    public Mono<ServerResponse> create(ServerRequest request) {
        return request.bodyToMono(ProductRequest.class)
                .flatMap(productRepository::save)
                .flatMap(saved -> ServerResponse
                        .created(URI.create("/functional/products/" + saved.id()))
                        .bodyValue(saved));
    }
}

@Configuration
class ProductRoutes {

    @Bean
    public RouterFunction<ServerResponse> productRoutes(ProductHandler handler) {
        // The entire route table is one readable, testable Java method --
        // composable with ordinary Java control flow rather than annotation
        // processing/reflection scanning at startup.
        return RouterFunctions.route()
                .GET("/functional/products/{id}", handler::getOne)
                .GET("/functional/products", handler::getAll)
                .POST("/functional/products", accept(MediaType.APPLICATION_JSON), handler::create)
                .build();
    }
}

// ---------------------------------------------------------------------------
// 5) WebClient -- WebFlux's non-blocking HTTP client, used here to call a
//    hypothetical downstream pricing service. Every call returns a
//    Mono/Flux, composing naturally with the operators from file 03.
// ---------------------------------------------------------------------------

record PriceInfo(String sku, BigDecimal price) { }

class PricingClientException extends RuntimeException {
    public PricingClientException(String message) {
        super(message);
    }
}

@Service
class ExternalPricingClient {

    private final WebClient webClient;

    // WebClient.Builder is auto-configured by Spring Boot and injected here
    // as a pre-configured bean -- baseUrl and other per-service settings are
    // customized once, rather than constructing WebClient from scratch.
    @Autowired
    public ExternalPricingClient(WebClient.Builder builder) {
        this.webClient = builder.baseUrl("https://pricing.example.com").build();
    }

    public Mono<PriceInfo> getPrice(String sku) {
        return webClient.get()
                .uri("/prices/{sku}", sku)
                .retrieve()                                              // triggers the request, expects 2xx
                .onStatus(HttpStatusCode::is4xxClientError,
                        response -> Mono.error(new PricingClientException("Bad request for sku: " + sku)))
                .bodyToMono(PriceInfo.class)
                .timeout(Duration.ofSeconds(3))                          // reactive timeout, not a blocking one
                .retryWhen(Retry.backoff(2, Duration.ofMillis(200)));    // composable resilience, same Reactor operators as file 03
    }

    public Mono<PriceInfo> submitPriceUpdate(PriceInfo request) {
        return webClient.post()
                .uri("/prices/update")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(PriceInfo.class);
    }
}
