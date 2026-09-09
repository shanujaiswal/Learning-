/**
 * 04_resilience_patterns_stacked.java
 *
 * Demonstrates, with illustrative Spring Boot code, a single service method
 * stacked with FOUR Resilience4j annotations at once, matching the "Stacking
 * Patterns Together" section of the Theory chapter:
 *     @CircuitBreaker  (outermost -- sees the final outcome after everything else resolves)
 *     @Retry           (retries a transient failure some number of times)
 *     @Bulkhead        (limits concurrent in-flight calls to this dependency)
 *     @TimeLimiter     (innermost -- bounds each individual attempt's duration)
 * plus matching fallback methods for each annotation that declares one.
 *
 * Covers Theory chapter:
 *     17) Microservices and Spring Cloud/Theory/04 Resilience Patterns Circuit Breaker and Retry.md
 *
 * IMPORTANT -- this file is illustrative, NOT a standalone runnable program.
 * It requires real Resilience4j + Spring Cloud dependencies and matching
 * application.yml configuration to actually run:
 *     - spring-cloud-starter-circuitbreaker-resilience4j
 *     - resilience4j-spring-boot3
 *     - a real "inventory-service" (or equivalent) client to wrap -- a Feign
 *       client stand-in is used below (see chapter 03's practical file for how
 *       such a client is actually built)
 *     - the resilience4j.* configuration block shown in comments below, which
 *       must exist in a real application.yml for these annotations to have any
 *       effect at all (the annotations alone, without matching named config,
 *       just use Resilience4j's defaults, which are rarely what you want)
 *
 * None of this infrastructure exists in this repository -- drop this class into
 * a real Spring Boot project with the dependencies above to see it run.
 */

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/*
 * application.yml this class expects to find alongside it (NOT a real file here --
 * shown as a comment purely for illustration of what makes these annotations
 * actually behave the way the Theory chapter describes):
 *
 * resilience4j:
 *   circuitbreaker:
 *     instances:
 *       inventoryService:
 *         sliding-window-type: COUNT_BASED
 *         sliding-window-size: 10
 *         failure-rate-threshold: 50
 *         wait-duration-in-open-state: 10s
 *         permitted-number-of-calls-in-half-open-state: 3
 *         minimum-number-of-calls: 5
 *         slow-call-duration-threshold: 2s
 *         slow-call-rate-threshold: 50
 *   retry:
 *     instances:
 *       inventoryService:
 *         max-attempts: 3
 *         wait-duration: 500ms
 *         enable-exponential-backoff: true
 *         exponential-backoff-multiplier: 2
 *         retry-exceptions:
 *           - java.io.IOException
 *         ignore-exceptions:
 *           - com.example.ProductNotFoundException
 *   bulkhead:
 *     instances:
 *       inventoryService:
 *         max-concurrent-calls: 10
 *         max-wait-duration: 0
 *   timelimiter:
 *     instances:
 *       inventoryService:
 *         timeout-duration: 2s
 *         cancel-running-future: true
 */

/** A minimal stand-in for a real remote client (e.g. a Feign interface from chapter 03). */
interface InventoryClientStub {
    StockLevelStub getStockLevel(String productId);
}

class StockLevelStub {
    private final String productId;
    private final int available;

    StockLevelStub(String productId, int available) {
        this.productId = productId;
        this.available = available;
    }

    static StockLevelStub unknown(String productId) {
        return new StockLevelStub(productId, -1);   // -1 signals "could not be determined"
    }

    public String getProductId() { return productId; }
    public int getAvailable() { return available; }
}

@Service
class OrderServiceResilientStack {

    private final InventoryClientStub inventoryClient;

    public OrderServiceResilientStack(InventoryClientStub inventoryClient) {
        this.inventoryClient = inventoryClient;
    }

    // -------------------------------------------------------------------
    // The fully-stacked method -- realistic for a genuinely important
    // downstream dependency in a hot path (e.g. checkout). Order matters:
    // CircuitBreaker sees the AGGREGATE outcome after Retry/Bulkhead/
    // TimeLimiter are all resolved, not each individual retry attempt.
    //
    // NOTE (from the Theory chapter's gotchas): this method must be called
    // from OUTSIDE this class (through the Spring-managed proxy) for any of
    // these annotations to take effect at all -- calling it via
    // `this.getStockLevel(...)` from within OrderServiceResilientStack itself
    // would silently bypass every one of these behaviors.
    // -------------------------------------------------------------------

    @CircuitBreaker(name = "inventoryService", fallbackMethod = "getStockLevelFallback")
    @Retry(name = "inventoryService")
    @Bulkhead(name = "inventoryService")
    @TimeLimiter(name = "inventoryService")
    public CompletableFuture<StockLevelStub> getStockLevel(String productId) {
        // TimeLimiter requires an async, CompletableFuture-returning method to wrap --
        // the actual (blocking) client call is pushed onto a separate thread here.
        return CompletableFuture.supplyAsync(() -> inventoryClient.getStockLevel(productId));
    }

    /**
     * Fallback for the fully-stacked method above. Resilience4j resolves this by
     * REFLECTION at startup, matching on: same leading parameter types as the
     * original method (String productId), same return type (CompletableFuture<StockLevelStub>),
     * plus one EXTRA trailing Throwable parameter. A mismatched signature here
     * fails wiring, per the Theory chapter's gotchas -- this is worth double-checking
     * carefully any time the original method's signature changes.
     *
     * This single fallback is reused for ALL FOUR annotations above since they share
     * the same "name" (inventoryService) and Resilience4j falls through to it whenever
     * the circuit is OPEN, retries are exhausted, the bulkhead is full, or the time
     * limit is exceeded.
     */
    private CompletableFuture<StockLevelStub> getStockLevelFallback(String productId, Throwable t) {
        // A degraded-but-functional response, deliberately NOT propagating the failure
        // to the caller -- treats "can't determine current stock" as assume-out-of-stock
        // rather than failing the whole order flow outright. Whether this or a hard
        // failure is the right call is a business decision made per-dependency (see
        // the Theory chapter's "Best Practices Summary").
        return CompletableFuture.completedFuture(StockLevelStub.unknown(productId));
    }

    // -------------------------------------------------------------------
    // A SIMPLER, non-async variant showing @CircuitBreaker + @Retry alone
    // (no Bulkhead/TimeLimiter) -- included because not every dependency
    // justifies the full stack; a lower-traffic, less-critical call might
    // reasonably only need these two (per the Theory chapter's
    // "not every call needs every pattern" guidance).
    // -------------------------------------------------------------------

    @CircuitBreaker(name = "inventoryService", fallbackMethod = "getStockLevelSimpleFallback")
    @Retry(name = "inventoryService")
    public StockLevelStub getStockLevelSimple(String productId) {
        return inventoryClient.getStockLevel(productId);
    }

    private StockLevelStub getStockLevelSimpleFallback(String productId, Throwable t) {
        return StockLevelStub.unknown(productId);
    }
}
