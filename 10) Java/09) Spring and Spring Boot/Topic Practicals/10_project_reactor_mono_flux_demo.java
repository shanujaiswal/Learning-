/**
 * 10_project_reactor_mono_flux_demo.java
 *
 * Demonstrates, with illustrative Project Reactor code:
 *     1. Mono (0 or 1 item) and Flux (0 to N items) construction basics.
 *     2. The core operators used constantly in real pipelines: map, flatMap,
 *        filter, zip, onErrorResume, switchIfEmpty.
 *     3. Side-by-side imperative vs reactive methods solving the SAME problem,
 *        to make the structural difference (sequential blocking vs a
 *        declarative, concurrently-composable pipeline) concrete.
 *     4. Cold publisher behavior and the "nothing runs until subscribed" rule.
 *     5. Scheduler usage (subscribeOn / boundedElastic) for isolating an
 *        unavoidable blocking call.
 *
 * Covers Theory chapter:
 *     10) Java/09) Spring and Spring Boot/Theory/10 Reactive Programming Fundamentals and Project Reactor.md
 *
 * IMPORTANT -- this file is illustrative, NOT a standalone runnable program
 * as-is (no build file/classpath is set up here). It requires:
 *     - reactor-core                (Project Reactor's Mono/Flux/Schedulers/operators)
 *   and, if run inside a Spring Boot app rather than as a bare Java class:
 *     - spring-boot-starter-webflux (pulls in reactor-core transitively, plus
 *                                     WebFlux integration -- see file 04's practicals)
 *
 * To actually run pipelines built this way (e.g. in a throwaway main method
 * or a unit test), Reactor requires a terminal operation -- .subscribe(...)
 * in real code, or .block()/.blockLast() ONLY in tests/bootstrapping, per the
 * theory file's explicit warning never to call .block() in reactive
 * request-handling code.
 *
 * Maven dependency (if wiring this into a real project):
 *     <dependency>
 *         <groupId>io.projectreactor</groupId>
 *         <artifactId>reactor-core</artifactId>
 *     </dependency>
 */

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// ---------------------------------------------------------------------------
// 1) Minimal domain types used throughout this file's pipelines.
// ---------------------------------------------------------------------------

record Product(Long id, String name, BigDecimal price, boolean inStock) { }
record Reviews(Long productId, int count, double averageRating) { }
record ProductResponse(Long id, String name, BigDecimal price, int reviewCount, double averageRating) { }

class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(Long id) {
        super("Product not found with id: " + id);
    }
}

// ---------------------------------------------------------------------------
// 2) Fake "repository" and "review service" -- NOT real reactive drivers,
//    just Mono/Flux-returning stand-ins so the pipelines below have
//    something concrete to compose. A real app would get these Mono/Flux
//    return types from an actual reactive source (R2DBC, WebClient, etc.)
//    rather than wrapping a plain in-memory Map like this.
// ---------------------------------------------------------------------------

class ReactiveProductRepository {

    private final Map<Long, Product> store = new ConcurrentHashMap<>();

    ReactiveProductRepository() {
        store.put(1L, new Product(1L, "Keyboard", new BigDecimal("49.99"), true));
        store.put(2L, new Product(2L, "Monitor", new BigDecimal("199.99"), false));
        store.put(3L, new Product(3L, "Mouse", new BigDecimal("19.99"), true));
    }

    // Mono<T> -- at most one value. Mono.justOrEmpty handles the "might not
    // be found" case without throwing directly inside the pipeline builder.
    Mono<Product> findById(Long id) {
        return Mono.justOrEmpty(Optional.ofNullable(store.get(id)));
    }

    // Flux<T> -- zero to many values, built from the in-memory collection.
    Flux<Product> findAll() {
        return Flux.fromIterable(store.values());
    }

    // Imperative equivalent, included ONLY for the side-by-side comparison
    // in section 5 below -- a blocking lookup that returns a plain Optional.
    Optional<Product> findByIdBlocking(Long id) {
        return Optional.ofNullable(store.get(id));
    }
}

class ReactiveReviewService {
    // Simulates an async call to a separate reviews microservice.
    Mono<Reviews> getReviews(Long productId) {
        return Mono.just(new Reviews(productId, 128, 4.5));
    }

    // Blocking equivalent, included ONLY for the side-by-side comparison.
    Reviews getReviewsBlocking(Long productId) {
        return new Reviews(productId, 128, 4.5);
    }
}

// ---------------------------------------------------------------------------
// 3) Building blocks: Mono/Flux construction, and "nothing runs until
//    subscribed" made concrete with a doOnSubscribe side effect.
// ---------------------------------------------------------------------------

class ReactorBasicsDemo {

    void demonstrateLaziness() {
        Mono<String> pipeline = Mono.fromSupplier(() -> {
                    System.out.println("Building the value NOW"); // only prints on subscribe
                    return "hello";
                })
                .map(String::toUpperCase)
                .doOnSubscribe(subscription -> System.out.println("Someone subscribed -- pipeline starts running"));

        System.out.println("Pipeline object built -- nothing has executed yet, no output above this line");
        pipeline.subscribe(value -> System.out.println("Got: " + value));
        // Only NOW does "Building the value NOW" print -- subscribe() is what triggers execution.
    }

    void demonstrateColdPublisher(ReactiveProductRepository repository) {
        Mono<Product> cold = repository.findById(1L);
        // Each subscription independently re-runs the underlying work --
        // subscribing twice below conceptually triggers TWO independent
        // lookups, not one shared result (this store is cheap to re-query,
        // but a real cold Mono wrapping a database call would run the query twice).
        cold.subscribe(p -> System.out.println("Subscriber A saw: " + p));
        cold.subscribe(p -> System.out.println("Subscriber B saw: " + p));
    }
}

// ---------------------------------------------------------------------------
// 4) Core operators: map / flatMap / filter / zip / onErrorResume.
// ---------------------------------------------------------------------------

class ReactorOperatorsDemo {

    private final ReactiveProductRepository productRepository;
    private final ReactiveReviewService reviewService;

    ReactorOperatorsDemo(ReactiveProductRepository productRepository, ReactiveReviewService reviewService) {
        this.productRepository = productRepository;
        this.reviewService = reviewService;
    }

    // map -- synchronous, 1-to-1, PURE transformation. No async work involved,
    // so map is the right operator here (not flatMap).
    Mono<String> uppercasedName(Long id) {
        return productRepository.findById(id)
                .map(Product::name)
                .map(String::toUpperCase);
    }

    // flatMap -- the transformation function itself returns ANOTHER Mono,
    // i.e. we're chaining a second asynchronous operation (fetching reviews)
    // off the result of the first. Using map here would produce the
    // nonsensical, non-compiling Mono<Mono<ProductResponse>>.
    Mono<ProductResponse> getProductWithReviews(Long id) {
        return productRepository.findById(id)
                .switchIfEmpty(Mono.error(new ProductNotFoundException(id)))
                .flatMap(product -> reviewService.getReviews(id)
                        .map(reviews -> new ProductResponse(
                                product.id(), product.name(), product.price(),
                                reviews.count(), reviews.averageRating())));
    }

    // filter -- drop items not matching a predicate, e.g. only in-stock products.
    Flux<Product> inStockProducts() {
        return productRepository.findAll()
                .filter(Product::inStock);
    }

    // zip -- combine two INDEPENDENT publishers, running them concurrently
    // and waiting for BOTH before combining. This is the structural
    // concurrency win called out in the theory file: zip does not run the
    // two sources sequentially the way imperative code naturally would.
    Mono<ProductResponse> getProductWithReviewsZipped(Long id) {
        Mono<Product> productMono = productRepository.findById(id)
                .switchIfEmpty(Mono.error(new ProductNotFoundException(id)));
        Mono<Reviews> reviewsMono = reviewService.getReviews(id);

        return Mono.zip(productMono, reviewsMono)
                .map(tuple -> {
                    Product product = tuple.getT1();
                    Reviews reviews = tuple.getT2();
                    return new ProductResponse(
                            product.id(), product.name(), product.price(),
                            reviews.count(), reviews.averageRating());
                });
    }

    // onErrorResume -- reactive's try/catch: recover from an upstream error
    // with a fallback publisher instead of letting the error propagate.
    Mono<Product> getProductOrPlaceholder(Long id) {
        return productRepository.findById(id)
                .switchIfEmpty(Mono.error(new ProductNotFoundException(id)))
                .onErrorResume(ProductNotFoundException.class,
                        ex -> Mono.just(new Product(id, "Unknown Product", BigDecimal.ZERO, false)));
    }

    // Composable resilience -- timeout + retryWhen chain directly onto any
    // Mono/Flux, exactly as they would onto a WebClient call (see file 04's
    // practicals), because there is no separate "resilience API" to learn.
    Mono<Product> getProductWithTimeoutAndRetry(Long id) {
        return productRepository.findById(id)
                .switchIfEmpty(Mono.error(new ProductNotFoundException(id)))
                .timeout(Duration.ofSeconds(3))
                .retryWhen(Retry.backoff(2, Duration.ofMillis(200)));
    }
}

// ---------------------------------------------------------------------------
// 5) Imperative vs reactive -- the SAME logic, two styles, as adjacent
//    methods on the same class for direct comparison.
// ---------------------------------------------------------------------------

class ImperativeVsReactiveComparison {

    private final ReactiveProductRepository productRepository;
    private final ReactiveReviewService reviewService;

    ImperativeVsReactiveComparison(ReactiveProductRepository productRepository, ReactiveReviewService reviewService) {
        this.productRepository = productRepository;
        this.reviewService = reviewService;
    }

    // IMPERATIVE (blocking) -- Spring MVC style. Each line WAITS for the
    // previous one; the calling thread is occupied for the ENTIRE duration
    // of both the product lookup and the review lookup, strictly sequential.
    public ProductResponse getProductImperative(Long id) {
        Product product = productRepository.findByIdBlocking(id)
                .orElseThrow(() -> new ProductNotFoundException(id));   // thread blocks here (conceptually)
        Reviews reviews = reviewService.getReviewsBlocking(id);          // thread blocks here too, AFTER the first call finishes
        return new ProductResponse(product.id(), product.name(), product.price(),
                reviews.count(), reviews.averageRating());
    }

    // REACTIVE (non-blocking) -- WebFlux style. Describes a pipeline; the
    // calling thread is never occupied waiting. zipWith runs the product
    // lookup and the review lookup CONCURRENTLY, combining results only
    // when both have arrived -- a structural difference, not just syntax.
    public Mono<ProductResponse> getProductReactive(Long id) {
        return productRepository.findById(id)
                .switchIfEmpty(Mono.error(new ProductNotFoundException(id)))
                .zipWith(reviewService.getReviews(id))
                .map(tuple -> new ProductResponse(
                        tuple.getT1().id(), tuple.getT1().name(), tuple.getT1().price(),
                        tuple.getT2().count(), tuple.getT2().averageRating()));
    }
}

// ---------------------------------------------------------------------------
// 6) Schedulers -- isolating an unavoidable blocking call off the (in a real
//    WebFlux app) small event-loop thread pool, per the theory file's
//    guidance on subscribeOn + Schedulers.boundedElastic().
// ---------------------------------------------------------------------------

class LegacyBlockingReportGenerator {
    // Stands in for a legacy, genuinely blocking third-party SDK call that
    // cannot be replaced with a non-blocking equivalent.
    String generate() {
        try {
            Thread.sleep(50); // simulated slow blocking work -- illustrative only
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        return "report-content";
    }
}

class SchedulerDemo {

    private final LegacyBlockingReportGenerator legacyBlockingReportGenerator = new LegacyBlockingReportGenerator();

    Mono<String> generateReportWithoutBlockingEventLoop() {
        return Mono.fromCallable(legacyBlockingReportGenerator::generate)
                .subscribeOn(Schedulers.boundedElastic());   // isolates the blocking call away from event-loop threads
    }
}
