# Why "It Compiled and It Works Locally" Isn't Enough for a Distributed System

--> Every network call between microservices (chapter 03) is a NEW opportunity for failure that simply doesn't exist inside a monolith's in-process method calls: the downstream service could be down, slow, overloaded, or returning errors -- and worse, these failures can CASCADE. If Service A calls Service B which calls Service C, and C becomes slow, requests pile up waiting on C, which makes B slow, which makes A slow, which can eventually exhaust A's thread pool and take A down too -- even though A's own code has no bug at all. **Resilience patterns** exist specifically to stop failures from cascading and to let a system degrade gracefully instead of catastrophically.
--> **Resilience4j** (`spring-cloud-starter-circuitbreaker-resilience4j` alongside `resilience4j-spring-boot3`) is the modern, lightweight resilience library for the Spring ecosystem -- successor to Netflix Hystrix, which is now in maintenance/end-of-life status. This chapter covers its four core patterns: Circuit Breaker, Retry, Timeout, and Bulkhead.

# Circuit Breaker -- Stop Calling a Service That's Already Failing

--> A **circuit breaker** wraps a call to a potentially-unreliable dependency and, after enough failures, "trips open" and stops even ATTEMPTING the call for a while -- failing FAST (immediately, via a fallback) instead of letting every caller wait out a slow timeout against a service that's clearly already unhealthy. The name is a direct analogy to an electrical circuit breaker: it trips to protect the rest of the system from a fault, rather than letting the fault propagate.

## The Three States

```text
                 failure rate exceeds threshold
      CLOSED  ------------------------------------->  OPEN
   (calls flow through            |                (calls fail FAST,
    normally, failures             |                 fallback used,
    are counted)                   |                 real endpoint
        ^                          |                 NOT called at all)
        |                          |
        |                   after wait-duration-in-open-state elapses
        |                          |
        |                          v
        +-------------------  HALF_OPEN
       enough trial calls    (lets a LIMITED number of
        succeed                trial calls through to test
                                if the dependency recovered)
                                      |
                          if trial calls still fail -->  back to OPEN
```

| State | Behavior |
|---|---|
| `CLOSED` | Normal operation -- calls pass through to the real dependency; failures are tracked in a rolling window |
| `OPEN` | Calls are NOT attempted at all -- they fail immediately (fast) and the configured fallback runs instead; protects the dependency from further load while it's unhealthy and protects the caller from wasting time/threads on calls likely to fail |
| `HALF_OPEN` | After a wait period, a small number of TRIAL calls are let through to test whether the dependency has recovered -- if they succeed, the breaker closes again; if they still fail, it reopens |

## Configuring and Annotating a Circuit Breaker

```yaml
# application.yml
resilience4j:
  circuitbreaker:
    instances:
      inventoryService:                              # a named instance -- referenced by name in code below
        sliding-window-type: COUNT_BASED               # or TIME_BASED -- how the failure rate is measured
        sliding-window-size: 10                         # last 10 calls considered
        failure-rate-threshold: 50                      # trips OPEN if >= 50% of those calls failed
        wait-duration-in-open-state: 10s                # how long to stay OPEN before trying HALF_OPEN
        permitted-number-of-calls-in-half-open-state: 3  # how many trial calls allowed in HALF_OPEN
        minimum-number-of-calls: 5                       # don't evaluate failure rate until at least 5 calls happened
        slow-call-duration-threshold: 2s                 # calls slower than this count as "slow" (can also trip the breaker)
        slow-call-rate-threshold: 50                     # >= 50% slow calls can also trip it, independent of outright failures
```

```java
@Service
public class OrderService {

    private final InventoryClient inventoryClient;      // e.g. a Feign client from chapter 03

    public OrderService(InventoryClient inventoryClient) {
        this.inventoryClient = inventoryClient;
    }

    // "inventoryService" here MUST match the instance name configured in application.yml above.
    // fallbackMethod names a method in THIS class with a matching signature + an extra
    // Throwable parameter at the end -- Resilience4j calls it whenever the breaker is
    // OPEN, or the wrapped call throws an exception the breaker is configured to count.
    @CircuitBreaker(name = "inventoryService", fallbackMethod = "getStockLevelFallback")
    public StockLevel getStockLevel(String productId) {
        return inventoryClient.getStockLevel(productId);
    }

    // Fallback signature: same return type + same leading parameters as the
    // original method, PLUS one extra parameter for the triggering exception.
    private StockLevel getStockLevelFallback(String productId, Throwable t) {
        // A sensible degraded response -- NOT necessarily an error to the end user.
        // Here: assume out-of-stock rather than failing the whole order flow outright.
        return StockLevel.unknown(productId);
    }
}
```

--> **Deep Dive -- what counts as a "failure" is configurable** -- by default, any exception thrown by the wrapped method counts toward the failure rate, but Resilience4j lets you explicitly configure `record-exceptions` (which exception types count as failures) and `ignore-exceptions` (which don't -- e.g. a `ProductNotFoundException` for a genuinely-missing product is a normal business outcome, NOT a sign the Inventory Service itself is unhealthy, so it shouldn't count toward tripping the breaker).

# Retry -- Automatically Re-Attempt Transient Failures

--> A **retry** automatically re-attempts a failed call some number of times before giving up -- appropriate for TRANSIENT failures (a brief network blip, a momentary spike in latency) but dangerous when misapplied (retrying a call that's genuinely going to keep failing just adds load to an already-struggling dependency, and retrying a NON-idempotent operation, like `POST /charge-card`, risks duplicate side effects -- see chapter 01's idempotency discussion).

```yaml
resilience4j:
  retry:
    instances:
      inventoryService:
        max-attempts: 3                                # original attempt + up to 2 retries = 3 total
        wait-duration: 500ms                            # base wait between attempts
        enable-exponential-backoff: true                # each retry waits longer than the last (500ms, 1000ms, ...)
        exponential-backoff-multiplier: 2
        retry-exceptions:
          - java.io.IOException                         # only retry on these specific exception types
          - org.springframework.web.client.ResourceAccessException
        ignore-exceptions:
          - com.example.ProductNotFoundException          # never retry a "this product genuinely doesn't exist" response
```

```java
@Service
public class OrderService {

    // @Retry and @CircuitBreaker commonly stack on the SAME method -- order matters:
    // Resilience4j applies them in the order listed as annotations (top to bottom acts
    // as outer-to-inner), and the conventional stacking is CircuitBreaker OUTERMOST,
    // Retry innermost, so the breaker sees the AGGREGATE outcome after retries are
    // exhausted, not each individual retry attempt as a separate "failure."
    @CircuitBreaker(name = "inventoryService", fallbackMethod = "getStockLevelFallback")
    @Retry(name = "inventoryService")
    public StockLevel getStockLevel(String productId) {
        return inventoryClient.getStockLevel(productId);
    }

    private StockLevel getStockLevelFallback(String productId, Throwable t) {
        return StockLevel.unknown(productId);
    }
}
```

--> **Exponential backoff matters** -- retrying immediately, three times in rapid succession, against a dependency that's struggling under load just adds MORE load right when it can least handle it. Exponential backoff (each retry waiting longer than the last) gives the dependency breathing room to recover between attempts.
--> **Never blindly retry non-idempotent operations** -- `retry-exceptions`/`ignore-exceptions` should be scoped deliberately, and for genuinely non-idempotent calls (e.g. "charge this card," "send this email"), retrying automatically is only safe if the OPERATION ITSELF is idempotent at the business level (e.g. it carries an idempotency key the downstream service deduplicates on) -- not just because the HTTP client makes retrying mechanically easy.

# Timeout -- Never Wait Forever

--> A **timeout** bounds how long a call is allowed to take before it's treated as a failure, regardless of whether the dependency would have eventually responded. Without a timeout, a hung dependency can hold a calling thread indefinitely, which (at enough concurrency) exhausts the caller's thread pool -- the exact cascading-failure scenario this whole chapter exists to prevent.

```yaml
resilience4j:
  timelimiter:
    instances:
      inventoryService:
        timeout-duration: 2s                    # the call MUST complete within 2 seconds or it's treated as a failure
        cancel-running-future: true              # attempt to cancel the underlying call once it times out
```

```java
// TimeLimiter is most naturally used with an async/CompletableFuture-returning method --
// it wraps a Supplier<CompletableFuture<T>> and enforces the timeout around it.
@Service
public class OrderService {

    @TimeLimiter(name = "inventoryService")
    @CircuitBreaker(name = "inventoryService", fallbackMethod = "getStockLevelFallbackAsync")
    public CompletableFuture<StockLevel> getStockLevelAsync(String productId) {
        return CompletableFuture.supplyAsync(() -> inventoryClient.getStockLevel(productId));
    }

    private CompletableFuture<StockLevel> getStockLevelFallbackAsync(String productId, Throwable t) {
        return CompletableFuture.completedFuture(StockLevel.unknown(productId));
    }
}
```

--> **A timeout should always be SHORTER than the caller's own upstream timeout budget** -- if a gateway or another service is willing to wait 5 seconds for THIS service's response, and this service's own downstream calls have a 10-second timeout, the whole chain can never actually respect the 5-second budget. Timeouts should be set thinking about the FULL call chain's latency budget, not each hop in isolation.

# Bulkhead -- Isolate Resource Pools So One Dependency Can't Starve Everything Else

--> The name comes from ship design -- a bulkhead is a wall that partitions a ship's hull into separate compartments, so a hole in ONE compartment doesn't sink the whole ship. Applied to software: a **bulkhead** limits how many CONCURRENT calls can be in-flight to a particular dependency, so a slow/overwhelmed dependency can only ever consume ITS OWN allotted slice of resources (threads or concurrent-call permits), not exhaust resources shared by calls to OTHER, unrelated dependencies.

```yaml
resilience4j:
  bulkhead:
    instances:
      inventoryService:
        max-concurrent-calls: 10           # at most 10 concurrent calls to this dependency at once
        max-wait-duration: 0                # how long a NEW call waits for a free slot before failing fast (0 = fail immediately if full)
  thread-pool-bulkhead:                     # a variant backed by a genuinely separate thread pool, for true isolation
    instances:
      inventoryService:
        max-thread-pool-size: 10
        core-thread-pool-size: 5
        queue-capacity: 20
```

```java
@Service
public class OrderService {

    @Bulkhead(name = "inventoryService", fallbackMethod = "getStockLevelFallback")
    @CircuitBreaker(name = "inventoryService", fallbackMethod = "getStockLevelFallback")
    public StockLevel getStockLevel(String productId) {
        return inventoryClient.getStockLevel(productId);
    }

    private StockLevel getStockLevelFallback(String productId, Throwable t) {
        return StockLevel.unknown(productId);
    }
}
```

--> **Why this matters even WITH a circuit breaker** -- a circuit breaker protects against a dependency that's already failing outright, but a bulkhead protects against a dependency that's merely SLOW (not yet failing enough to trip the breaker) from monopolizing shared resources (like a common thread pool) that OTHER, healthy dependencies' calls also need. Without a bulkhead, one slow dependency can starve calls to a completely unrelated, perfectly healthy dependency simply because they're competing for the same limited thread pool.

# Stacking Patterns Together -- Order Matters

--> It's normal for a single method to be wrapped in MULTIPLE Resilience4j annotations at once. The conventional stacking order (outermost to innermost) is:

```text
@CircuitBreaker   (outermost -- sees the FINAL outcome after retries/bulkhead/timeout are all resolved)
  @RateLimiter    (if used -- caps overall call rate regardless of outcome)
    @Retry        (retries the call some number of times on transient failure)
      @Bulkhead   (limits concurrent in-flight calls)
        @TimeLimiter  (innermost -- bounds each individual attempt's duration)
          [the actual call]
```

```java
// A fully-stacked example -- realistic for a genuinely important downstream dependency.
@CircuitBreaker(name = "inventoryService", fallbackMethod = "getStockLevelFallback")
@Retry(name = "inventoryService")
@Bulkhead(name = "inventoryService")
@TimeLimiter(name = "inventoryService")
public CompletableFuture<StockLevel> getStockLevel(String productId) {
    return CompletableFuture.supplyAsync(() -> inventoryClient.getStockLevel(productId));
}
```

--> **Not every call needs every pattern** -- a low-stakes, rarely-called, non-critical-path dependency might reasonably get just a timeout and nothing else; a critical, high-traffic, frequently-called dependency in the hot path of checkout probably deserves the full stack. Match the amount of resilience machinery to the actual criticality and traffic pattern of each dependency, rather than applying the maximal stack everywhere out of habit (which adds real complexity and config surface for every instance).

# Common Gotchas

--> **Fallback methods with a mismatched signature** -- Resilience4j resolves the fallback method by REFLECTION at startup based on matching parameter types (with one added `Throwable` parameter) and return type; a mismatch fails SILENTLY at startup in some setups or throws a confusing wiring error -- always double-check the fallback signature mirrors the original method plus the trailing `Throwable`.
--> **Retrying non-idempotent operations blindly** -- covered above; this is the single most dangerous resilience misconfiguration, since it can cause real duplicate side effects (double charges, duplicate orders) rather than just impacting availability.
--> **No timeout combined with a circuit breaker configured on failure COUNT alone** -- if slow calls aren't also configured to count via `slow-call-duration-threshold`/`slow-call-rate-threshold`, a dependency that's merely SLOW (not outright erroring) may never trip the breaker at all, and every caller keeps waiting out the full (long) response time.
--> **Treating expected business exceptions as circuit-breaker failures** -- a `ProductNotFoundException` for a real 404 is normal business behavior, not a sign the Inventory Service itself is unhealthy; without `ignore-exceptions` configured, a spike in genuinely-missing-product lookups could needlessly trip the breaker for everyone.
--> **Applying `@CircuitBreaker` (or any of these annotations) to a `private` method, or calling the annotated method from WITHIN the same class** -- like most Spring AOP-based annotations, these rely on a proxy wrapping the bean; calling an annotated method via `this.someMethod()` from inside the same class bypasses the proxy entirely and the resilience behavior silently never applies. The annotated method must be called from OUTSIDE the class (through the injected Spring-managed bean) for the proxy to intercept it.
--> **Ignoring the difference between OPEN-state fast failures and genuine timeouts** -- monitoring/alerting should distinguish "the breaker is open and fast-failing" (a systemic issue, likely worth paging someone) from "occasional retries succeeding on transient blips" (usually fine, often not worth alerting on) -- lumping them together in dashboards hides real signal.

# Best Practices Summary

--> Reach for Resilience4j, not Hystrix, for new Spring Cloud resilience work -- Hystrix is in maintenance/end-of-life status.
--> Configure explicit timeouts on every outbound call -- never rely on an unbounded wait, and size timeouts against the FULL call chain's latency budget, not just one hop.
--> Use exponential backoff for retries, and never retry non-idempotent operations unless the operation itself carries an idempotency guarantee.
--> Use `ignore-exceptions`/`record-exceptions` deliberately so normal business-logic exceptions (like "not found") don't count toward tripping a circuit breaker meant to detect genuine dependency unhealthiness.
--> Add bulkheads around dependencies that share infrastructure (thread pools) with other, unrelated calls, so one slow dependency can't starve everything else.
--> Always provide a sensible fallback -- decide deliberately whether "degraded but functional" (return cached/default data) or "fail the whole operation" is the right behavior for each specific call, rather than defaulting to one or the other everywhere.
--> Only stack as many patterns (circuit breaker + retry + bulkhead + timeout) as a given dependency's actual criticality and traffic justify -- not every call needs the full stack.
--> Remember these annotations rely on Spring AOP proxies -- self-invocation from within the same class bypasses them entirely.
