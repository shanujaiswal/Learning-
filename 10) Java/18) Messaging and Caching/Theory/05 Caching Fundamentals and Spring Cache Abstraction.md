# Why Caching Exists

--> A cache is a small, FAST store sitting in front of a slower, more authoritative data source (a database, an external API, an expensive computation) -- it trades a bit of staleness risk for a large reduction in latency and load on the underlying source. The core insight caching exploits is that most real workloads have a small "hot" subset of data that's read far more often than the rest (the 80/20 rule, or "temporal locality" -- recently accessed data tends to be accessed again soon).

```text
WITHOUT A CACHE                          WITH A CACHE
   Request -> DB query (50ms) -> Response    Request -> Cache lookup (0.5ms) -> Response
   Every single request hits the DB          Only a CACHE MISS falls through to the DB
                                              (50ms), and the result is stored for next time
```

--> **Concrete problems caching solves:**

```text
1. LATENCY REDUCTION
   An in-memory cache lookup is typically 10-1000x faster than a database
   round-trip, and dramatically faster than a call to a third-party API.

2. LOAD REDUCTION ON THE SOURCE OF TRUTH
   Every cache hit is one query the database (or API, or expensive
   computation) never has to handle -- directly protects a struggling DB
   from being overwhelmed by repeated identical reads.

3. COST REDUCTION
   Many external APIs charge per call, or rate-limit aggressively --
   caching responses avoids redundant, billable/limited calls for data
   that hasn't actually changed.

4. RESILIENCE (secondary benefit, use with care)
   A cache can sometimes serve slightly-stale data even when the
   underlying source is temporarily unavailable -- valuable for read
   availability, dangerous if staleness silently masks a real outage.
```

--> **The trade-off, stated honestly:** caching introduces the possibility of serving STALE data -- data that no longer matches the true current state in the source of truth. Every caching strategy is fundamentally a negotiation between "how fast/cheap" and "how fresh," and getting that negotiation wrong (serving data that's too stale for the use case, or invalidating so aggressively the cache barely helps) is the single most common source of caching-related bugs.

# The Cache-Aside Pattern (a.k.a. Lazy Loading)

--> **Cache-aside** is the most common caching pattern in application code, and the one Spring's `@Cacheable` annotation directly automates. The application code -- not the cache itself -- is responsible for checking the cache first, falling back to the source on a miss, and populating the cache with what it found.

```text
READ PATH:
   1. Application asks the CACHE for key K.
   2. CACHE HIT  -> return cached value immediately. Source is never touched.
   3. CACHE MISS -> application queries the SOURCE OF TRUTH (e.g. database),
      stores the result in the cache under key K, then returns it.

        Request for key K
              |
              v
        +-----------+   hit    
        |   Cache   |--------> return cached value
        +-----------+
              | miss
              v
        +-----------+
        | Database  |
        +-----------+
              |
              v
        store result in cache under K, THEN return it
```

```text
WRITE PATH (the part that's easy to get wrong):
   The cache is NOT automatically kept in sync with writes -- cache-aside
   only defines the READ path. Writes must explicitly either:
     (a) INVALIDATE (evict) the corresponding cache entry so the next read
         is a miss and repopulates fresh, or
     (b) UPDATE the cache entry directly to the new value (write-through-ish,
         still initiated by application code, not automatic).
   Forgetting this is precisely how caches serve stale data after an update.
```

--> **Other caching patterns, briefly, for context (cache-aside is what Spring Cache abstracts over, but it helps to know these exist):**

| Pattern | How it works | Trade-off |
|---|---|---|
| Cache-aside (lazy loading) | App checks cache, falls back to source on miss, populates cache | Simple, only caches what's actually requested; first request for any key is always a miss ("cold start") |
| Write-through | Every write goes to the cache AND the source synchronously, together | Cache never stale after a write, but every write pays the cache-write latency too |
| Write-behind (write-back) | Write goes to the cache immediately; the source is updated asynchronously, later | Fastest writes, but a cache/process crash before the async flush can lose data |
| Read-through | The cache ITSELF knows how to load from the source on a miss (app just asks the cache, never touches the source directly) | Centralizes loading logic in the cache layer; conceptually close to cache-aside but the cache does the fetching, not the app |

# Spring's Cache Abstraction -- The Annotations

--> Spring Cache is a PROVIDER-AGNOSTIC abstraction -- the same `@Cacheable`/`@CachePut`/`@CacheEvict` annotations work whether the underlying cache is a simple in-memory `ConcurrentHashMap` (Spring's default `ConcurrentMapCacheManager`, fine for demos/tests), Caffeine (in-process, covered next chapter), Redis (distributed, also next chapter), or Ehcache. Swapping providers is (ideally) a configuration change, not a code change to your `@Service` classes.

```java
@Configuration
@EnableCaching   // activates Spring's caching annotation processing (AOP proxies
                  // intercept annotated method calls) -- required, easy to forget
public class CacheConfig {
    // With a real provider (Redis/Caffeine) this class also configures the
    // CacheManager bean -- see the next chapter for concrete examples.
}
```

--> **`@Cacheable` -- cache-aside, automated:**

```java
@Service
public class ProductService {

    @Cacheable(cacheNames = "products", key = "#productId")
    public Product findById(String productId) {
        // This method body ONLY RUNS on a cache miss -- Spring's AOP proxy
        // intercepts the call, checks the "products" cache for key
        // #productId FIRST, and only invokes this method if there's no
        // cached entry. The return value is then automatically stored
        // under that key for next time.
        System.out.println("Cache miss -- hitting the database for " + productId);
        return productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }
}
```

--> **`@CachePut` -- always runs the method, then updates the cache (write path):**

```java
@Service
public class ProductService {

    @CachePut(cacheNames = "products", key = "#product.id")
    public Product update(Product product) {
        // Unlike @Cacheable, this method body ALWAYS EXECUTES -- @CachePut
        // never short-circuits on a cache "hit," because the whole point
        // is to perform the write and then refresh what's cached, keeping
        // reads (via findById above) consistent with what was just saved.
        return productRepository.save(product);
    }
}
```

--> **`@CacheEvict` -- removes stale entries (the other half of the write path):**

```java
@Service
public class ProductService {

    @CacheEvict(cacheNames = "products", key = "#productId")
    public void delete(String productId) {
        productRepository.deleteById(productId);
        // After this method returns, the "products" cache entry for
        // #productId is removed -- the next findById(productId) call is
        // guaranteed to be a miss, re-querying (and finding it gone).
    }

    // allEntries=true clears the WHOLE cache region in one shot -- useful
    // for bulk operations where invalidating keys individually isn't
    // practical (e.g. a bulk import that touches thousands of products).
    @CacheEvict(cacheNames = "products", allEntries = true)
    public void bulkImport(List<Product> products) {
        productRepository.saveAll(products);
    }
}
```

--> **The SpEL `key` expression** -- `key = "#productId"` uses Spring Expression Language to reference a method parameter by name (requires `-parameters` compiler flag or explicit `@Cacheable(key = "#p0")` positional reference if parameter names aren't retained). Composite keys are expressed the same way: `key = "#product.id + '-' + #product.version"`, or by combining multiple parameters. Omitting `key` entirely derives a default key from ALL parameters via `SimpleKeyGenerator` -- fine for single-parameter methods, but easy to get subtly wrong for multi-parameter methods where you only want to key on one of them.

--> **`condition` and `unless` -- conditional caching:**

```java
@Cacheable(cacheNames = "products", key = "#productId",
           condition = "#productId != null",       // only attempt caching if true (evaluated BEFORE the call)
           unless = "#result == null")              // skip STORING the result if true (evaluated AFTER the call)
public Product findById(String productId) { ... }
```

--> `condition` decides whether caching applies to this invocation AT ALL (checked before the method runs); `unless` decides whether to skip caching the RESULT specifically (checked after the method runs, so it has access to `#result`) -- a common use is `unless = "#result == null"` to avoid permanently caching a "not found" `null` for a record that might be created moments later.

# Cache Providers -- Pluggability in Practice

--> Spring Boot auto-configures a `CacheManager` bean based on what's on the classpath, following the same auto-configuration philosophy covered in the Spring Boot chapter -- adding a dependency is often the only "code change" needed to switch providers.

| Property/Dependency | Resulting Provider |
|---|---|
| No cache library on classpath, `@EnableCaching` present | `ConcurrentMapCacheManager` -- simple in-memory `ConcurrentHashMap`-backed cache; no eviction policy, no TTL, fine for tests/demos only |
| `com.github.ben-manes.caffeine:caffeine` on classpath | `CaffeineCacheManager` -- high-performance in-process cache with configurable eviction/TTL (see next chapter) |
| `spring-boot-starter-data-redis` on classpath | `RedisCacheManager` -- distributed cache shared across multiple app instances (see next chapter) |
| `spring.cache.type=none` | Disables caching -- `@Cacheable` methods just run normally every time, useful for local dev/testing without standing up a cache provider |

```properties
# application.properties -- explicit provider selection (usually auto-detected,
# but can be pinned explicitly to avoid ambiguity if multiple are on the classpath)
spring.cache.type=redis
spring.cache.cache-names=products,users
spring.cache.redis.time-to-live=600000
```

--> **Why this pluggability matters in practice:** application code written against `@Cacheable`/`@CachePut`/`@CacheEvict` does not change AT ALL when moving from a local in-memory cache (development) to Caffeine (single-instance production) to Redis (multi-instance production needing a SHARED cache) -- only configuration/dependencies change. This is the same "program to an abstraction" benefit Spring Data provides over raw JDBC.

# Common Gotchas

--> **Forgetting `@EnableCaching`.** Without it, `@Cacheable`/`@CachePut`/`@CacheEvict` annotations are silently INERT -- no error, the methods just run every time as if uncached, because Spring never creates the AOP proxies that intercept the calls.
--> **Self-invocation bypassing the cache.** Like all Spring AOP-based annotations (`@Transactional` included), calling an `@Cacheable` method from ANOTHER method in the SAME class (`this.findById(id)`) bypasses the proxy entirely -- the call never goes through the interception logic, so caching silently does nothing. The call must come from OUTSIDE the class (through the injected Spring-managed bean) for the proxy to intervene.
--> **Caching mutable objects and mutating them after retrieval.** If `@Cacheable` returns a mutable object and calling code mutates it in place, that mutation corrupts the cached instance for every future reader of that key (for in-memory caches sharing the object reference) -- prefer immutable DTOs/records for cached values, or defensively copy.
--> **Forgetting the write-path (`@CachePut`/`@CacheEvict`) entirely**, leaving `@Cacheable` reads serving data that's gone stale the moment an update or delete happens elsewhere -- cache-aside's read path is automatic, but the write-side consistency is entirely the developer's responsibility.
--> **The default `ConcurrentMapCacheManager` has no eviction and no TTL** -- left running in production by accident (e.g. because a Redis/Caffeine dependency wasn't actually added), it grows unboundedly and never expires stale entries, a silent memory leak that "worked in the demo."
--> **Caching exceptions or `null` unintentionally.** By default, `@Cacheable` does NOT cache a thrown exception, but it CAN cache a returned `null` (unless guarded with `unless = "#result == null"`), which can wrongly "freeze" a not-found result even after the underlying record starts existing.

# Best Practices Summary

--> **Always pair `@Cacheable` read methods with explicit `@CachePut`/`@CacheEvict` on the corresponding write methods** -- treat cache invalidation as part of the write operation's contract, not an afterthought.
--> **Never call an `@Cacheable` method from inside the same class expecting caching to apply** -- route the call through the Spring-managed bean (inject the service into itself via a different bean, or restructure into a separate `@Service`).
--> **Cache immutable values (DTOs/records) rather than live mutable entities** wherever practical, to avoid silent cache corruption via in-place mutation.
--> **Set an explicit TTL once you move beyond the default in-memory cache** -- an eternally-cached entry is a slow-motion consistency bug waiting to surface.
--> **Use `unless = "#result == null"` (or similar) for lookup methods that can legitimately return "not found"**, so a temporarily-missing record doesn't get permanently frozen as "not found" in the cache.
--> **Treat the provider choice (in-memory / Caffeine / Redis) as a deployment concern, not an application-logic concern** -- keep `@Cacheable` usage provider-agnostic so switching from local dev caching to a shared distributed cache in production is a configuration change, covered concretely in the next chapter.
