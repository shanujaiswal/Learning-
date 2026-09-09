/**
 * 12_webflux_vs_mvc_pitfalls_and_r2dbc_demo.java
 *
 * Demonstrates, with illustrative Spring WebFlux code:
 *     1. The single most damaging WebFlux mistake -- calling a blocking API
 *        from inside a reactive pipeline running on an event-loop thread --
 *        shown as a BAD example, then progressively fixed.
 *     2. The "better but still not ideal" fix (isolating the blocking call
 *        onto Schedulers.boundedElastic()), and the BEST fix (a genuinely
 *        non-blocking R2DBC driver, so there's no blocking call to isolate at all).
 *     3. Comments on debugging reactive stack traces -- the .checkpoint()
 *        operator, Hooks.onOperatorDebug(), and BlockHound, tied to a
 *        concrete pipeline.
 *     4. A brief R2DBC ReactiveCrudRepository interface illustration,
 *        including a derived query method, contrasted with the JPA
 *        equivalent it replaces.
 *
 * Covers Theory chapter:
 *     10) Java/09) Spring and Spring Boot/Theory/12 When to Choose WebFlux vs MVC.md
 *
 * IMPORTANT -- this file is illustrative, NOT a standalone runnable program.
 * It requires a real Spring Boot project on the classpath to compile/run, specifically:
 *     - spring-boot-starter-webflux        (Spring WebFlux, Reactor Netty)
 *     - spring-boot-starter-data-r2dbc     (Spring Data R2DBC, ReactiveCrudRepository)
 *     - an R2DBC driver for your database, e.g. r2dbc-postgresql / r2dbc-h2
 *     - (for the BAD/JPA-contrast examples only, NOT to be combined with
 *        R2DBC in a real app) spring-boot-starter-data-jpa -- shown purely
 *        for side-by-side illustration, never actually wired together here.
 *
 * Run (in a real Spring Boot project, after wiring the R2DBC-based pieces
 * into src/main/java/... and configuring spring.r2dbc.url/username/password):
 *     mvn spring-boot:run
 *   or
 *     ./gradlew bootRun
 *
 * Optional dev/staging-only debugging aids referenced below (never leave
 * these on in production -- real, measured overhead):
 *     Hooks.onOperatorDebug();                 // or -Dreactor.trace.assembly=true
 *     // reactor-tools' BlockHound, typically wired into integration tests
 */

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;

// ---------------------------------------------------------------------------
// 1) Minimal domain type shared across every example below.
// ---------------------------------------------------------------------------

record Product(Long id, String name, BigDecimal price, String category) { }

class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(Long id) {
        super("Product not found with id: " + id);
    }
}

// ---------------------------------------------------------------------------
// 2) A fake BLOCKING JPA-style repository -- stands in for a real
//    org.springframework.data.jpa.repository.JpaRepository. Included ONLY to
//    make the "wrong" and "better but still not ideal" examples below
//    concrete; a real project would never mix this with WebFlux (see the
//    theory file's "heavy reliance on JDBC/JPA" section).
// ---------------------------------------------------------------------------

interface BlockingJpaProductRepository {
    // In a real project: Product findById(Long id) via Spring Data JPA,
    // backed by a genuinely blocking JDBC driver underneath.
    Product findById(Long id);
}

// ---------------------------------------------------------------------------
// 3) THE MISTAKE, THE PARTIAL FIX, AND THE BEST FIX -- three methods on one
//    controller so they can be compared directly, per the theory file's
//    escalating "WRONG -> BETTER (but still not ideal) -> BEST" example.
// ---------------------------------------------------------------------------

@RestController
@RequestMapping("/api/pitfalls/products")
class BlockingPitfallController {

    private final BlockingJpaProductRepository jpaProductRepository;   // illustrative blocking dependency
    private final R2dbcProductRepository r2dbcProductRepository;       // illustrative non-blocking dependency (declared below)

    public BlockingPitfallController(BlockingJpaProductRepository jpaProductRepository,
                                      R2dbcProductRepository r2dbcProductRepository) {
        this.jpaProductRepository = jpaProductRepository;
        this.r2dbcProductRepository = r2dbcProductRepository;
    }

    // -----------------------------------------------------------------
    // WRONG -- calling a blocking JPA repository from a WebFlux controller.
    // Mono.fromCallable does NOT make jpaProductRepository.findById
    // non-blocking -- it only delays WHEN the blocking happens. If this
    // pipeline runs on an event-loop thread (WebFlux's default), the
    // blocking call stalls that thread, and because event-loop threads are
    // FEW and SHARED, it stalls every OTHER request relying on that same
    // thread too -- not just this one. Reactor Netty may throw
    // IllegalStateException here if .block() is called directly on an
    // event-loop thread, but a call like this one (no explicit .block(),
    // just an inline blocking call inside fromCallable) is NOT reliably
    // caught by that guard -- it can silently degrade throughput instead.
    // -----------------------------------------------------------------
    @GetMapping("/wrong/{id}")
    public Mono<Product> getOneWrong(@PathVariable Long id) {
        return Mono.fromCallable(() -> jpaProductRepository.findById(id))   // still BLOCKS the thread it runs on
                .switchIfEmpty(Mono.error(new ProductNotFoundException(id)));
    }

    // -----------------------------------------------------------------
    // BETTER (but still not ideal) -- explicitly isolate the blocking call
    // onto Schedulers.boundedElastic(), a pool specifically sized/intended
    // to absorb blocking work without starving the small event-loop pool.
    // Correct-ish, but at this point WebFlux has reintroduced
    // thread-per-request-like blocking for this one call, just on a
    // different pool -- worth asking whether WebFlux is buying anything here.
    // -----------------------------------------------------------------
    @GetMapping("/better/{id}")
    public Mono<Product> getOneBetter(@PathVariable Long id) {
        return Mono.fromCallable(() -> jpaProductRepository.findById(id))
                .subscribeOn(Schedulers.boundedElastic())   // now blocks a boundedElastic thread, not an event-loop one
                .switchIfEmpty(Mono.error(new ProductNotFoundException(id)))
                .checkpoint("BlockingPitfallController#getOneBetter id=" + id);   // see debugging section below
    }

    // -----------------------------------------------------------------
    // BEST -- use an actually non-blocking driver (R2DBC) so there is no
    // blocking call to isolate at all. Genuinely non-blocking end-to-end;
    // no Scheduler juggling needed, and no risk of stalling shared
    // event-loop threads.
    // -----------------------------------------------------------------
    @GetMapping("/best/{id}")
    public Mono<Product> getOneBest(@PathVariable Long id) {
        return r2dbcProductRepository.findById(id)
                .switchIfEmpty(Mono.error(new ProductNotFoundException(id)))
                .checkpoint("BlockingPitfallController#getOneBest id=" + id);
    }
}

// ---------------------------------------------------------------------------
// 4) Debugging reactive stack traces -- .checkpoint(), Hooks.onOperatorDebug(),
//    and BlockHound, illustrated as standalone notes tied to a pipeline.
// ---------------------------------------------------------------------------

class ReactiveDebuggingNotes {

    private final R2dbcProductRepository r2dbcProductRepository;

    ReactiveDebuggingNotes(R2dbcProductRepository r2dbcProductRepository) {
        this.r2dbcProductRepository = r2dbcProductRepository;
    }

    // .checkpoint("label") -- inserted at a meaningful point in a pipeline,
    // adds a labeled marker to the eventual assembly-time stack trace so an
    // error surfacing later points back to WHERE in YOUR pipeline it
    // happened, without the full cost of global operator debugging. Cheap
    // enough to leave in at meaningful boundaries even outside a debugging session.
    Mono<Product> findWithCheckpoint(Long id) {
        return r2dbcProductRepository.findById(id)
                .checkpoint("ReactiveDebuggingNotes#findWithCheckpoint id=" + id)
                .switchIfEmpty(Mono.error(new ProductNotFoundException(id)));
    }

    // Hooks.onOperatorDebug() (or -Dreactor.trace.assembly=true) -- globally
    // captures the assembly-time stack trace for EVERY operator in the
    // application, so error traces point back to where each pipeline was
    // BUILT in your source code, not just Reactor's own internal frames
    // (FluxMap, MonoFlatMap, scheduler internals, etc.). Meaningfully slows
    // the whole app down if left on -- a local debugging-session tool only,
    // typically called once near application startup when actively
    // diagnosing an opaque trace, never enabled unconditionally in production.
    static void enableGlobalOperatorDebuggingForThisDebuggingSessionOnly() {
        Hooks.onOperatorDebug();
    }

    // reactor-tools' BlockHound -- a Java agent that actively detects
    // blocking calls made from non-blocking (event-loop) threads AT RUNTIME,
    // catching exactly the getOneWrong()-style mistake above even in cases
    // Reactor Netty's own built-in .block() guard does not catch (e.g. a raw
    // Thread.sleep() or a blocking JDBC call with no explicit .block()).
    // Typically wired into integration tests rather than left running in
    // production, due to its own runtime overhead:
    //
    //     // in a test's @BeforeAll:
    //     BlockHound.install();
    //
    // With BlockHound installed, a test that exercises getOneWrong() above
    // would fail LOUDLY with a clear "blocking call detected" error instead
    // of silently degrading throughput only under real concurrent load.

    // The practical habit: read a reactive stack trace bottom-up, looking
    // for YOUR class names and checkpoint() labels first; treat the
    // surrounding Reactor-internal frames (FluxOnAssembly, FluxSubscribeOn,
    // etc.) as noise to skim past rather than read line-by-line.
}

// ---------------------------------------------------------------------------
// 5) R2DBC repository illustration -- Spring Data R2DBC's ReactiveCrudRepository
//    mirrors JpaRepository's shape, but every method returns Mono/Flux
//    instead of a plain value, and derived query methods still work using
//    the SAME naming convention as Spring Data JPA.
// ---------------------------------------------------------------------------

@Repository
interface R2dbcProductRepository extends ReactiveCrudRepository<Product, Long> {

    // Derived query method -- same naming convention as JpaRepository
    // (findByCategory), but returns Flux<Product> instead of List<Product>.
    Flux<Product> findByCategory(String category);

    // A custom query via @Query when a derived method name would be awkward
    // to express -- the reactive analog of JPA's @Query, still returning
    // Mono/Flux rather than a blocking result.
    @Query("SELECT * FROM product WHERE price >= :minPrice ORDER BY price ASC")
    Flux<Product> findAllPricedAtLeast(BigDecimal minPrice);

    Mono<Boolean> existsByName(String name);
}

/*
 * CONTRAST ONLY -- what the JPA equivalent of R2dbcProductRepository would
 * look like, included purely to make the theory file's "R2DBC is not a
 * drop-in replacement for JPA" point concrete. This interface is NEVER
 * actually wired into the same application as R2dbcProductRepository above --
 * a real project picks ONE data-access technology per datastore.
 *
 *     public interface JpaProductRepository extends JpaRepository<Product, Long> {
 *         List<Product> findByCategory(String category);   // returns a plain List, not Flux
 *         boolean existsByName(String name);                // returns a plain boolean, not Mono<Boolean>
 *         // Plus: lazy-loaded @OneToMany/@ManyToMany associations, a
 *         // first-level cache, and dirty-checking auto-flush via Hibernate's
 *         // persistence context -- NONE of which Spring Data R2DBC provides,
 *         // by deliberate design (see the theory file's R2DBC gaps section).
 *     }
 */
