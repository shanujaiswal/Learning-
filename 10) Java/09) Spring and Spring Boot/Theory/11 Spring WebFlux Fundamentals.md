# What WebFlux Actually Is

--> **Spring WebFlux** is Spring's REACTIVE, non-blocking web framework -- a parallel alternative to Spring MVC, not a replacement or an upgrade to it. Both live in the same Spring ecosystem, both can use `@RestController`-style annotations, and both ship as separate starters (`spring-boot-starter-web` for MVC, `spring-boot-starter-webflux` for WebFlux) -- you pick ONE per application (mixing them in the same app is possible but unusual and generally discouraged).
--> This file assumes file 03's Reactor concepts (`Mono`/`Flux`, backpressure, non-blocking I/O) as background -- WebFlux is essentially "Spring MVC's annotation model and HTTP abstractions, rebuilt on top of a fully non-blocking, Reactor-based foundation instead of the blocking Servlet API."

# Architectural Comparison -- MVC vs WebFlux Side by Side

| Aspect | Spring MVC | Spring WebFlux |
|---|---|---|
| Underlying I/O model | Servlet API -- blocking, thread-per-request | Reactive Streams -- non-blocking, event-loop |
| Default embedded server | Tomcat (Servlet container) | Netty (async, event-loop server) -- though Tomcat/Jetty/Undertow are usable in their non-blocking modes too |
| Front controller | `DispatcherServlet` | `DispatcherHandler` (the reactive analog -- same coordinating ROLE, different underlying contract) |
| Handler return types | Plain objects, `ResponseEntity<T>` | `Mono<T>`, `Flux<T>`, `Mono<ResponseEntity<T>>` |
| Programming models available | Annotated controllers only | BOTH annotated controllers (familiar `@RestController` style) AND functional endpoints (`RouterFunction`/`HandlerFunction`) |
| Thread model | Large pool (often 200), one thread occupied per in-flight request | Small, fixed pool (~ number of CPU cores), threads never block |
| Database access | JDBC (blocking) via Spring Data JPA | R2DBC (non-blocking) via Spring Data R2DBC -- see file 05 |
| HTTP client for calling other services | `RestTemplate` (blocking, legacy) or WebClient used blockingly | `WebClient` (non-blocking, reactive) -- the natural fit |
| Backpressure | Not applicable -- data isn't streamed this way | Native, built into the Reactive Streams foundation |
| Mental model | Sequential, step-by-step, easy to read top-to-bottom | Declarative pipelines of operators, requires the reactive mindset from file 03 |

--> **`DispatcherHandler` is genuinely the reactive sibling of `DispatcherServlet`** -- it plays the same front-controller ROLE described in file 01 (find a handler, invoke it, resolve exceptions), but its entire contract is built around `Mono`/`Flux` instead of directly writing to a blocking `HttpServletResponse`. The conceptual pieces from file 01 -- something that finds the right handler, something that invokes it, something that resolves exceptions -- all have reactive counterparts (`HandlerMapping`, `HandlerAdapter`, `WebExceptionHandler` in the reactive stack), even though the concrete classes differ.

# Annotated Controllers in WebFlux -- Familiar Syntax, Reactive Return Types

--> If you already know `@RestController`, WebFlux's annotated model requires almost no NEW syntax -- the annotations (`@GetMapping`, `@PathVariable`, `@RequestParam`, `@RequestBody`) are IDENTICAL. The only structural change is that handler methods return `Mono<T>`/`Flux<T>` instead of `T`/`List<T>` directly.

```java
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductRepository productRepository;   // a ReactiveCrudRepository, see file 05

    public ProductController(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @GetMapping("/{id}")
    public Mono<ProductResponse> getOne(@PathVariable Long id) {
        return productRepository.findById(id)
                .switchIfEmpty(Mono.error(new ProductNotFoundException(id)))
                .map(ProductMapper::toResponse);
    }

    @GetMapping
    public Flux<ProductResponse> getAll() {
        return productRepository.findAll()
                .map(ProductMapper::toResponse);
    }

    @PostMapping
    public Mono<ResponseEntity<ProductResponse>> create(@Valid @RequestBody Mono<ProductRequest> requestMono) {
        return requestMono
                .map(ProductMapper::toEntity)
                .flatMap(productRepository::save)
                .map(ProductMapper::toResponse)
                .map(created -> ResponseEntity
                        .created(URI.create("/api/products/" + created.id()))
                        .body(created));
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> delete(@PathVariable Long id) {
        return productRepository.deleteById(id)
                .thenReturn(ResponseEntity.noContent().<Void>build());
    }
}
```

--> **`@RequestBody Mono<ProductRequest>`** -- note the request body itself can be accepted as a `Mono`, not just the plain DTO type (both work; accepting `Mono<ProductRequest>` directly lets you `flatMap` immediately without WebFlux needing to resolve/subscribe to the body before invoking your method, which is a subtle efficiency/composability win in fully reactive pipelines).
--> **`@Valid` still works** -- Bean Validation integrates the same way as MVC; a validation failure surfaces as `WebExchangeBindException` (the reactive analog of `MethodArgumentNotValidException`), handled via `@ExceptionHandler`/`@ControllerAdvice` exactly as in file 02 -- the exception-handling MODEL (local handler, then `@RestControllerAdvice`, then Spring's built-ins) carries over to WebFlux essentially unchanged, just with reactive-flavored exception types.
--> **Streaming a `Flux` as Server-Sent Events** -- one of WebFlux's genuinely distinctive capabilities, trivial to express because `Flux` IS a stream over time:

```java
@GetMapping(value = "/prices/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<PriceUpdate> streamPrices() {
    return priceService.priceUpdates();   // a Flux that emits a new PriceUpdate whenever the price changes
}
```

# Functional Endpoints -- RouterFunction and HandlerFunction

--> WebFlux offers a SECOND, entirely different way to define endpoints -- not annotations on a class, but explicit Java code that builds up a routing table and handler functions programmatically. This is unique to WebFlux; Spring MVC has no equivalent (aside from the much more limited `RouterFunction`-adjacent `WebMvcConfigurer.addViewControllers` for trivial static redirects).

```java
@Component
public class ProductHandler {   // NOT annotated -- just a plain class with methods matching HandlerFunction's shape

    private final ProductRepository productRepository;

    public ProductHandler(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Mono<ServerResponse> getOne(ServerRequest request) {
        Long id = Long.valueOf(request.pathVariable("id"));
        return productRepository.findById(id)
                .flatMap(product -> ServerResponse.ok().bodyValue(ProductMapper.toResponse(product)))
                .switchIfEmpty(ServerResponse.notFound().build());
    }

    public Mono<ServerResponse> getAll(ServerRequest request) {
        Flux<ProductResponse> products = productRepository.findAll().map(ProductMapper::toResponse);
        return ServerResponse.ok().body(products, ProductResponse.class);
    }

    public Mono<ServerResponse> create(ServerRequest request) {
        return request.bodyToMono(ProductRequest.class)
                .map(ProductMapper::toEntity)
                .flatMap(productRepository::save)
                .flatMap(saved -> ServerResponse
                        .created(URI.create("/api/products/" + saved.getId()))
                        .bodyValue(ProductMapper.toResponse(saved)));
    }
}

@Configuration
public class ProductRoutes {

    @Bean
    public RouterFunction<ServerResponse> productRoutes(ProductHandler handler) {
        return RouterFunctions.route()
                .GET("/api/products/{id}", handler::getOne)
                .GET("/api/products", handler::getAll)
                .POST("/api/products", accept(MediaType.APPLICATION_JSON), handler::create)
                .build();
    }
}
```

--> **Why this style exists at all, given annotated controllers already work** -- functional endpoints make routing EXPLICIT, testable Java code rather than annotation metadata scanned via reflection at startup -- the entire route table is one readable method, composable with standard Java (conditionals, loops, extracted helper methods) rather than annotation processing magic. Some teams prefer it for exactly this explicitness; others find annotated controllers more familiar and readable at a glance. Both compile to the same underlying `HandlerMapping`/`HandlerAdapter`-equivalent machinery -- this is a STYLE choice, not a capability difference (a few advanced routing predicates are only cleanly expressible functionally, but this is a rare deciding factor in practice).
--> **`ServerRequest`/`ServerResponse`** are WebFlux's reactive abstractions over the incoming request and outgoing response -- conceptually parallel to `HttpServletRequest`/`HttpServletResponse` in the Servlet world, but with methods that return `Mono`/`Flux` (`request.bodyToMono(SomeType.class)`) rather than blocking reads.

# WebClient -- The Reactive HTTP Client

--> **`WebClient`** is WebFlux's non-blocking HTTP client, meant to replace `RestTemplate` (which is blocking, and as of recent Spring versions is in MAINTENANCE MODE -- not deprecated outright, but explicitly not receiving new features, with `WebClient` recommended even for MVC/blocking applications that just need a modern client). Every call returns a `Mono`/`Flux`, composing naturally into a WebFlux pipeline -- and it works perfectly well from a blocking Spring MVC app too (just call `.block()` at the boundary, since MVC's own thread model already tolerates blocking).

```java
@Service
public class ExternalPricingClient {

    private final WebClient webClient;

    public ExternalPricingClient(WebClient.Builder builder) {
        this.webClient = builder.baseUrl("https://pricing.example.com").build();
    }

    public Mono<PriceInfo> getPrice(String sku) {
        return webClient.get()
                .uri("/prices/{sku}", sku)
                .retrieve()                                  // triggers the request, expects 2xx
                .onStatus(HttpStatusCode::is4xxClientError,
                        response -> Mono.error(new PricingClientException("Bad request: " + sku)))
                .bodyToMono(PriceInfo.class)
                .timeout(Duration.ofSeconds(3))               // reactive timeout, not a blocking one
                .retryWhen(Retry.backoff(2, Duration.ofMillis(200)));
    }

    public Mono<PricingResult> submitPricingRequest(PricingRequest request) {
        return webClient.post()
                .uri("/prices/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(PricingResult.class);
    }
}
```

--> **`WebClient.Builder` as an injected, pre-configured bean** -- Spring Boot auto-configures a `WebClient.Builder` bean (with sensible codec defaults) that you inject and customize per-service (`baseUrl`, default headers, a custom `ExchangeFilterFunction` for logging/auth) rather than constructing `WebClient` from scratch each time -- this is the idiomatic pattern for calling multiple downstream services, each getting its own configured `WebClient` instance built from the shared builder.
--> **`retrieve()` vs `exchangeToMono()`** -- `retrieve()` is the simpler, common-case API (auto-throws `WebClientResponseException` on 4xx/5xx unless you intercept with `.onStatus(...)`); `exchangeToMono()`/`exchangeToFlux()` hands you the full `ClientResponse` for cases needing more control (e.g. different deserialization based on status code) -- start with `retrieve()` and only reach for `exchangeToMono` when you hit its limits.
--> **Composable resilience operators come for free from Reactor** -- `.timeout()`, `.retryWhen()`, `.onErrorResume()` chain directly onto a `WebClient` call exactly as they would onto any other `Mono`, because a `WebClient` call IS just a `Mono`/`Flux` like any other Reactor publisher -- there's no separate "HTTP client resilience API" to learn.

# Common Gotchas

--> **Mixing annotated controllers and functional endpoints without a clear reason** -- both work in the same app, but arbitrarily mixing styles per-endpoint with no consistent rule makes the codebase harder to navigate; pick one as the DEFAULT team convention and deviate deliberately, not habitually.
--> **Using `RestTemplate` inside a WebFlux app** -- it's blocking, and calling it from a WebFlux handler reintroduces the exact thread-blocking problem WebFlux exists to avoid, potentially blocking one of the small number of event-loop threads and starving unrelated requests. Always use `WebClient` in a WebFlux application.
--> **Forgetting `produces = TEXT_EVENT_STREAM_VALUE` for a streaming endpoint** -- returning a `Flux` without declaring the SSE media type often still "works" in the sense of returning data, but the client may buffer the whole response instead of processing it incrementally as a stream -- explicit `produces` matters for genuinely-streaming semantics.
--> **Assuming WebFlux's annotated-controller model behaves identically to MVC's in EVERY respect** -- most things transfer directly, but subtle differences exist (e.g. how request body validation errors surface, how multipart form data is handled) -- always verify exception types and edge-case behaviors against WebFlux's own documentation rather than assuming 1:1 parity with Spring MVC.
--> **Blindly wrapping existing blocking service/repository code in `Mono.fromCallable` and calling it "reactive"** -- this compiles and often "works," but doesn't deliver WebFlux's actual benefit (thread-per-request blocking simply moves onto `Schedulers.boundedElastic()`'s pool instead of Tomcat's, and you still need a bounded-elastic-sized pool) -- true benefit requires a genuinely non-blocking stack end-to-end (WebClient, R2DBC, reactive Mongo/Redis drivers), covered further in file 05.

# Best Practices Summary

--> Pick WebFlux deliberately, for a genuine reason (see file 05) -- not because it's newer; Spring MVC remains the right default for most applications.
--> Reuse familiar `@RestController` annotations for WebFlux's annotated model -- the main adjustment is return types (`Mono`/`Flux`) and thinking in pipelines, not new annotation syntax.
--> Reach for functional endpoints (`RouterFunction`) when explicit, testable routing code is valued over annotation-driven discovery -- otherwise annotated controllers are the more familiar default.
--> Always use `WebClient`, never `RestTemplate`, for outbound HTTP calls inside a WebFlux application -- and prefer `WebClient` over `RestTemplate` even in new MVC code, given `RestTemplate`'s maintenance-mode status.
--> Compose resilience (`timeout`, `retryWhen`, `onErrorResume`) directly onto `WebClient` calls using the same Reactor operators from file 03 -- there's no separate API surface to learn for this.
--> Keep the full pipeline non-blocking end-to-end (WebClient + R2DBC, not JDBC) to actually realize WebFlux's throughput benefits -- a reactive controller sitting on top of a blocking repository gains little.
