# Two Providers, Two Very Different Jobs

--> Chapter 05 covered Spring's cache ABSTRACTION -- `@Cacheable`/`@CachePut`/`@CacheEvict` -- which stays the same regardless of the underlying provider. This chapter covers the two providers you'll actually reach for in real Spring Boot applications: **Caffeine**, an in-process (single-JVM) cache library, and **Redis**, a distributed (shared, out-of-process) cache/data store. Choosing between them is really a choice about WHERE the cached data needs to live relative to your application instances.

```text
                    Caffeine (in-process)                Redis (distributed)
                    ----------------------                --------------------
Lives inside:       The same JVM heap as your app          A separate process/server
Shared across:      Nothing -- each app instance has its   ALL app instances share the
                     OWN independent cache                  SAME cache
Speed:               Fastest possible (no network hop,       Slower than Caffeine (network
                     just a local hash map lookup)            round-trip), still far faster
                                                               than hitting the real database
Survives app         No -- cache is lost, cold start          Yes -- cache persists across
restart:              on every restart                        individual app restarts
Best fit:            Single-instance apps, or per-instance    Multi-instance apps needing a
                      caching where slight inconsistency       SHARED, consistent view of
                      between instances is acceptable           cached data
```

# Caffeine -- High-Performance In-Process Caching

--> **Caffeine** is a modern, high-performance Java caching library (the spiritual successor to Google Guava's cache) built around efficient eviction algorithms (Window TinyLFU, which outperforms simple LRU in most real workloads) and a simple, fluent builder API. Spring Boot auto-configures `CaffeineCacheManager` the moment `caffeine` is on the classpath alongside `@EnableCaching`.

```xml
<!-- pom.xml -->
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>
```

## Configuring Caffeine in Spring Boot

```java
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("products", "users");
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(10_000)                          // evict least-valuable entries once 10,000 is exceeded
                .expireAfterWrite(Duration.ofMinutes(10))       // entries expire 10 minutes after being WRITTEN
                .recordStats());                                // enables cache.stats() -- hit rate, eviction count, etc.
        return cacheManager;
    }
}
```

```properties
# Equivalent, simpler config purely via properties (no @Bean needed for basic cases)
spring.cache.type=caffeine
spring.cache.cache-names=products,users
spring.cache.caffeine.spec=maximumSize=10000,expireAfterWrite=10m
```

--> **`expireAfterWrite` vs `expireAfterAccess`** -- `expireAfterWrite` counts down from when an entry was last WRITTEN (inserted or updated), regardless of how often it's read; `expireAfterAccess` resets the countdown on every READ too, meaning a frequently-accessed entry can stay cached indefinitely even if the underlying data has actually gone stale. For data that can genuinely change over time, `expireAfterWrite` is usually the SAFER default -- it guarantees a maximum staleness window regardless of read frequency.
--> **Using Caffeine directly, without Spring's annotations** (for cases needing more control than `@Cacheable` offers, e.g. a manual loading cache with a defined loader function):

```java
LoadingCache<String, Product> productCache = Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterWrite(Duration.ofMinutes(10))
        .build(productId -> productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId)));
        // The lambda IS the "load on miss" logic -- get() below calls it
        // automatically on a miss and caches the result, atomically.

Product product = productCache.get("SKU-123");   // hit: instant; miss: runs the loader, caches, then returns
```

--> **Why Caffeine over the JDK's own `ConcurrentHashMap`** -- a raw `ConcurrentHashMap` used as a manual cache has NO eviction policy at all (grows forever, a classic memory leak) and no built-in TTL/expiration; Caffeine provides both, plus near-optimal hit rates via its eviction algorithm, with a battle-tested, heavily-benchmarked implementation instead of hand-rolled logic.

# Redis -- Distributed Caching Across Multiple Instances

--> **The problem Caffeine can't solve**: once an application runs as multiple instances behind a load balancer (the normal production shape), each instance has its OWN separate Caffeine cache -- instance A caching a product doesn't help instance B at all, and worse, if instance A's cache is invalidated after an update but instance B's isn't, different users can see DIFFERENT (inconsistent) cached data depending purely on which instance handled their request. **Redis**, an in-memory data store running as its own separate process/server, solves this by giving every application instance a single, SHARED cache to read and write.

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

```yaml
# application.yml
spring:
  cache:
    type: redis
    redis:
      time-to-live: 600000            # 10 minutes, in milliseconds -- default TTL for all Redis-backed caches
  data:
    redis:
      host: localhost
      port: 6379
```

```java
@Configuration
@EnableCaching
public class RedisCacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()));
                        // values are serialized to JSON before being stored in Redis --
                        // Redis itself only stores bytes/strings, so cached Java objects
                        // must be serialized going in and deserialized coming back out.

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withCacheConfiguration("products", defaultConfig.entryTtl(Duration.ofMinutes(30)))
                // per-cache-name overrides -- "products" gets a longer TTL than the default
                .build();
    }
}
```

--> **Once `RedisCacheManager` is configured, application code doesn't change at all** -- the exact same `@Cacheable(cacheNames = "products", key = "#productId")` from chapter 05 now reads/writes through Redis instead of Caffeine, transparently, which is the entire point of Spring's provider-agnostic cache abstraction.
--> **Serialization matters** -- because Redis stores bytes, every cached object must be serializable; `GenericJackson2JsonRedisSerializer` stores values as readable JSON (convenient for debugging via `redis-cli`, slightly larger on the wire than a binary format), while `JdkSerializationRedisSerializer` uses Java's built-in (binary, less human-readable, requires `Serializable`) serialization. JSON is the more common modern default for its debuggability and cross-language readability if other (non-Java) services ever need to inspect the same cache.
--> **Using `RedisTemplate` directly** for cases beyond simple `@Cacheable` (e.g. manual TTL per key, working with Redis's other data structures like lists/sets/sorted sets, not just simple key-value caching):

```java
@Service
public class SessionTokenService {

    private final RedisTemplate<String, String> redisTemplate;

    public SessionTokenService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void storeToken(String userId, String token) {
        redisTemplate.opsForValue().set("session:" + userId, token, Duration.ofHours(1));
    }

    public Optional<String> getToken(String userId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get("session:" + userId));
    }
}
```

# Eviction Policies -- What Happens When the Cache Is Full

| Policy | Behavior | Typical use |
|---|---|---|
| **TTL (Time-To-Live)** | Entry expires after a fixed duration, regardless of access pattern | Data with a known "freshness window" (e.g. an exchange rate valid for 5 minutes) |
| **LRU (Least Recently Used)** | When full, evicts the entry that hasn't been accessed in the LONGEST time | General-purpose caching where "recently used" predicts "will be used again soon" |
| **LFU (Least Frequently Used)** | When full, evicts the entry with the FEWEST total accesses | Workloads where some keys are consistently hot regardless of recency |
| **Window TinyLFU (Caffeine's default)** | A hybrid combining a small LRU-like "window" for newly-added entries with an LFU-based main cache -- empirically outperforms plain LRU/LFU on most real workloads | Caffeine's default; rarely needs manual tuning |
| **Size-based (`maximumSize`)** | Evicts (via whichever policy) once a maximum ENTRY COUNT is exceeded, independent of TTL | Bounding memory usage regardless of how fast entries would otherwise expire |

# Cache Stampede (Thundering Herd) -- A Concurrency Gotcha at Scale

--> A **cache stampede** (also called "thundering herd") happens when a popular cache entry expires, and MANY concurrent requests for that same key all experience a cache miss AT THE SAME TIME -- every one of them then hits the underlying database/API simultaneously to repopulate the same entry, momentarily multiplying load on the source exactly when the cache was supposed to be protecting it.

```text
Without stampede protection:
   Key "hot-product-42" expires at T=0
        |
        +--> Request 1 (miss) --> queries DB --> repopulates cache
        +--> Request 2 (miss) --> queries DB --> repopulates cache   (REDUNDANT --
        +--> Request 3 (miss) --> queries DB --> repopulates cache    same data,
        +--> ... (thousands more, all within milliseconds)            fetched N times)
```

--> **Mitigation strategies:**

```text
1. LOCK-BASED (mutex per key)
   Only ONE request per key is allowed to actually query the source on a
   miss; other concurrent requests for the SAME key either wait for the
   first to finish and reuse its result, or briefly serve stale data.
   Caffeine's LoadingCache.get() naturally coalesces concurrent misses for
   the SAME key into a single load -- this is built in, not something you
   have to hand-implement.

2. EARLY/PROBABILISTIC REFRESH
   Refresh a hot entry slightly BEFORE it actually expires (e.g. at 90% of
   its TTL, with some randomized jitter across different keys/instances)
   so it rarely reaches a true "miss" state under heavy concurrent load.

3. STALE-WHILE-REVALIDATE
   Serve the (slightly) stale cached value immediately while ONE background
   request refreshes it -- accepts a small staleness window in exchange for
   never blocking or stampeding on expiration.

4. JITTERED TTLs
   Adding a small random offset to each entry's TTL (rather than one exact,
   shared expiration instant) spreads out expirations over time instead of
   many keys/entries all expiring in the SAME instant -- particularly
   relevant when many entries were populated at roughly the same time
   (e.g. a cache warmed at application startup).
```

# Choosing Between Caffeine and Redis in Practice

| Situation | Recommended |
|---|---|
| Single-instance application, or per-instance cache is acceptable | Caffeine |
| Multiple instances need to see the SAME cached value consistently | Redis |
| Need the cache to survive an application restart | Redis (Caffeine's cache is lost on restart) |
| Absolute lowest possible latency, no network hop acceptable | Caffeine |
| Also need pub/sub, distributed locks, rate limiting, session storage (Redis's broader feature set beyond simple caching) | Redis |
| Want to minimize new infrastructure to operate/monitor | Caffeine (no separate server to run) |

--> **A common hybrid pattern (two-tier caching)**: a small, fast Caffeine cache in front of a larger, shared Redis cache -- a request checks Caffeine first (fastest possible, no network hop), falls back to Redis on a local miss (still much faster than the database), and only falls all the way through to the database on a miss at BOTH levels. This adds real complexity (two layers to keep consistent) and is worth it only once profiling shows the network hop to Redis is itself a meaningful bottleneck -- not a default starting architecture.

# Common Gotchas

--> **Choosing Caffeine for a multi-instance deployment without realizing each instance gets its own independent cache** -- leads to confusing inconsistency (different users see different cached data depending on which instance served them) that's easy to misdiagnose as a "random" bug.
--> **No TTL configured on Redis, relying only on `maximumSize`-equivalent memory limits** -- Redis's own `maxmemory-policy` eviction (not the same mechanism as an application-level TTL) can evict cache entries somewhat unpredictably under memory pressure if TTLs aren't set explicitly per entry.
--> **Ignoring serialization overhead/format when moving from Caffeine to Redis** -- objects that were simply held by reference in-process now must be serialized/deserialized on every cache read/write; large or deeply-nested objects can make this overhead noticeable, and a serialization format mismatch (e.g. changing a class's fields without considering already-cached serialized entries) can cause deserialization failures for entries cached under an old class shape.
--> **Not considering cache stampede for genuinely hot keys** -- a naive cache-aside implementation without stampede protection can cause a sudden load spike on the database exactly at the moment a popular entry expires, especially right after a deploy that clears/restarts caches.
--> **All entries expiring at the exact same instant** (e.g. a bulk cache warm-up at startup with one shared TTL) -- causes a synchronized mass-expiration and simultaneous mass cache-miss later; jittering TTLs avoids this.
--> **Forgetting that Redis is a SEPARATE process that itself needs monitoring, sizing, and (in production) high-availability configuration** -- unlike Caffeine (which lives inside your app's own JVM and needs no separate operational concern), Redis is now a piece of infrastructure that can itself become a single point of failure if not deployed with replication/failover in mind.

# Best Practices Summary

--> Default to Caffeine for single-instance or per-instance-acceptable caching -- it's simpler operationally (no separate server) and the fastest option available.
--> Move to Redis specifically when multiple application instances need a consistent, shared view of cached data, or when the cache needs to survive individual instance restarts.
--> Always set an explicit TTL, regardless of provider -- an unbounded cache is a slow-motion memory leak (Caffeine) or an unpredictable eviction surprise (Redis without per-key TTLs).
--> Prefer `expireAfterWrite` over `expireAfterAccess` for data that can genuinely go stale, since it guarantees a maximum staleness window regardless of read frequency.
--> Consider stampede protection (built-in coalescing via `LoadingCache`, early refresh, or jittered TTLs) for genuinely hot keys before they become a production incident.
--> Keep application code written against Spring's `@Cacheable`/`@CachePut`/`@CacheEvict` abstraction regardless of provider, so switching from Caffeine to Redis (or vice versa) stays a configuration change, not a code change.
--> Treat Redis as real infrastructure requiring its own monitoring, sizing, and high-availability planning once it's part of the production architecture.
