/**
 * RedisCacheConfig.java
 *
 * This file has TWO parts:
 *
 *   PART A (illustrative, requires external infrastructure):
 *     Spring Data Redis configuration -- a RedisCacheManager @Bean, JSON
 *     serialization of cached values, per-cache-name TTL overrides, and a
 *     RedisTemplate-based service for cases beyond simple @Cacheable
 *     (matches the "Redis -- Distributed Caching Across Multiple Instances"
 *     section of the Theory chapter). ALSO sketches the Caffeine
 *     CacheManager @Bean equivalent for side-by-side comparison.
 *
 *   PART B (standalone, ACTUALLY RUNS -- no external dependencies at all):
 *     A hand-rolled in-memory cache, InMemoryTtlLruCache<K, V>, implementing:
 *       - per-entry TTL (time-to-live) expiration
 *       - simple LRU (least-recently-used) eviction once a max size is exceeded
 *     plus a main() method that exercises both behaviors and prints the
 *     results, so this half of the file can be compiled and run directly
 *     with nothing but a plain JDK.
 *
 * Covers Theory chapter:
 *     10) Java/18) Messaging and Caching/Theory/06 Redis and Caffeine for
 *     Java Caching.md
 *
 * ---------------------------------------------------------------------------
 * HOW TO RUN PART B STANDALONE:
 * ---------------------------------------------------------------------------
 *     javac 06_redis_caffeine_java_caching_demo.java
 *     java InMemoryTtlLruCacheDemo
 *
 *     (PART A's classes are NOT part of the public entry point and are
 *     commented out / clearly marked as requiring a real Spring Boot project
 *     plus a running Redis server -- see the disclaimer directly above PART A.)
 */


// =============================================================================
// PART A -- SPRING DATA REDIS CONFIGURATION (ILLUSTRATIVE ONLY)
// =============================================================================
//
// IMPORTANT -- THE CODE IN THIS COMMENT BLOCK DOES NOT COMPILE OR RUN ON ITS
// OWN. It requires:
//   1. A real Spring Boot project with "spring-boot-starter-data-redis" on
//      the classpath (Maven):
//          <dependency>
//              <groupId>org.springframework.boot</groupId>
//              <artifactId>spring-boot-starter-data-redis</artifactId>
//          </dependency>
//   2. A running Redis server reachable from the app, e.g. via Docker:
//          docker run -d --name redis -p 6379:6379 redis:7
//   3. For the Caffeine comparison bean: "com.github.ben-manes.caffeine:caffeine"
//      on the classpath instead (mutually alternative to Redis, not combined,
//      unless deliberately building the two-tier pattern the Theory chapter
//      describes).
//
// Copy/paste and adapt the classes below into such a project's src/main/java
// tree (each class would normally live in its own .java file).
/*
package com.example.caching.redis;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.autoconfigure.cache.CaffeineCacheManager;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

// -----------------------------------------------------------------------
// Redis-backed CacheManager -- once this bean exists, application code
// written against @Cacheable/@CachePut/@CacheEvict (see Practical file 05)
// does NOT change at all; only this configuration changes. Values are
// serialized to JSON before being stored in Redis, since Redis itself only
// stores bytes/strings.
// -----------------------------------------------------------------------
@Configuration
@EnableCaching
public class RedisCacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                // Per-cache-name override -- "products" gets a longer TTL
                // than everything else's 10-minute default.
                .withCacheConfiguration("products", defaultConfig.entryTtl(Duration.ofMinutes(30)))
                .build();
    }
}

// -----------------------------------------------------------------------
// The Caffeine equivalent, shown side-by-side purely for comparison -- pick
// ONE of RedisCacheConfig or CaffeineCacheConfig as your actual
// @Primary/only CacheManager bean, not both simultaneously, unless
// deliberately building a two-tier Caffeine-in-front-of-Redis architecture.
// -----------------------------------------------------------------------
@Configuration
@EnableCaching
class CaffeineCacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("products", "users");
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(10_000)                       // evict least-valuable entries once 10,000 is exceeded
                .expireAfterWrite(Duration.ofMinutes(10))    // entries expire 10 min after being WRITTEN, not read
                .recordStats());                             // enables cache.stats() -- hit rate, eviction count, etc.
        return cacheManager;
    }
}

// -----------------------------------------------------------------------
// RedisTemplate usage for cases beyond simple @Cacheable -- e.g. manual
// per-key TTL, or session-token-style key/value storage that isn't a
// straightforward method-result cache.
// -----------------------------------------------------------------------
@Service
class SessionTokenService {

    private final RedisTemplate<String, String> redisTemplate;

    SessionTokenService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    void storeToken(String userId, String token) {
        redisTemplate.opsForValue().set("session:" + userId, token, Duration.ofHours(1));
    }

    Optional<String> getToken(String userId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get("session:" + userId));
    }
}
*/
//
// Equivalent application.yml:
//
//     spring:
//       cache:
//         type: redis
//         redis:
//           time-to-live: 600000        # 10 minutes, in milliseconds
//       data:
//         redis:
//           host: localhost
//           port: 6379


// =============================================================================
// PART B -- STANDALONE, ACTUALLY RUNS: hand-rolled in-memory cache with TTL
// and simple LRU eviction. No Spring, no Redis, no Caffeine -- just the JDK.
// =============================================================================
//
// This directly illustrates the Theory chapter's point that a raw
// ConcurrentHashMap used as a manual cache has NO eviction policy (grows
// forever) and no built-in TTL -- here we hand-roll BOTH, the way Caffeine
// does internally (albeit far more simply and without Caffeine's
// Window-TinyLFU sophistication -- this is intentionally a teaching-scale
// implementation, not a production replacement for a real caching library).

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A minimal, thread-safe, in-memory cache supporting:
 *   - a per-entry TTL (time-to-live): an entry is treated as expired (and
 *     removed on next access) once its TTL has elapsed since it was WRITTEN
 *     -- mirroring Caffeine's expireAfterWrite semantics from the Theory
 *     chapter, which is the SAFER default for data that can genuinely change
 *     over time (a maximum staleness window regardless of read frequency).
 *   - simple LRU (least-recently-used) eviction once a maximum entry count
 *     is exceeded, implemented via a LinkedHashMap in access-order mode
 *     (its removeEldestEntry hook is the textbook way to get LRU behavior
 *     "for free" out of the JDK collections).
 *
 * This class is intentionally simple -- e.g. it does not implement
 * Caffeine's Window TinyLFU algorithm, per-key async loading, or stampede
 * protection (see Theory chapter 06's "Cache Stampede" section) -- it exists
 * to make the MECHANICS of TTL + LRU concrete and inspectable, not to be a
 * production-grade replacement for Caffeine/Redis.
 */
class InMemoryTtlLruCache<K, V> {

    /** Wraps a cached value together with the timestamp it was written at. */
    private static final class Entry<V> {
        final V value;
        final long writtenAtNanos;

        Entry(V value, long writtenAtNanos) {
            this.value = value;
            this.writtenAtNanos = writtenAtNanos;
        }
    }

    private final int maximumSize;
    private final long ttlNanos;
    private final ReentrantLock lock = new ReentrantLock();

    // accessOrder=true turns this LinkedHashMap into an LRU structure: every
    // get() call re-links the accessed entry to the "most recently used" end,
    // and removeEldestEntry (overridden below) evicts the "least recently
    // used" entry once size() exceeds maximumSize.
    private final LinkedHashMap<K, Entry<V>> store;

    private long hitCount = 0;
    private long missCount = 0;
    private long evictionCount = 0;
    private long expirationCount = 0;

    InMemoryTtlLruCache(int maximumSize, long ttlMillis) {
        if (maximumSize <= 0) {
            throw new IllegalArgumentException("maximumSize must be positive");
        }
        if (ttlMillis <= 0) {
            throw new IllegalArgumentException("ttlMillis must be positive");
        }
        this.maximumSize = maximumSize;
        this.ttlNanos = ttlMillis * 1_000_000L;

        // initialCapacity/loadFactor values below are unremarkable defaults;
        // accessOrder=true is the one flag that actually matters for LRU.
        this.store = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, Entry<V>> eldest) {
                boolean shouldEvict = size() > InMemoryTtlLruCache.this.maximumSize;
                if (shouldEvict) {
                    InMemoryTtlLruCache.this.evictionCount++;
                }
                return shouldEvict;
            }
        };
    }

    /** Stores (or overwrites) a value, stamped with the current write time. */
    void put(K key, V value) {
        lock.lock();
        try {
            store.put(key, new Entry<>(value, System.nanoTime()));
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the cached value for key, or null if absent OR expired.
     * An expired entry is actively removed (lazy expiration) rather than
     * waiting for a background sweep -- simple, and sufficient for a
     * teaching-scale cache; a production library would typically also run
     * periodic maintenance to reclaim expired entries that are never
     * looked up again.
     */
    V get(K key) {
        lock.lock();
        try {
            Entry<V> entry = store.get(key); // this get() call is what advances LRU order
            if (entry == null) {
                missCount++;
                return null;
            }
            long ageNanos = System.nanoTime() - entry.writtenAtNanos;
            if (ageNanos > ttlNanos) {
                // Expired -- remove it now and report a miss, exactly as a
                // real TTL cache would (an expired entry is logically gone,
                // even if it hadn't been physically swept out yet).
                store.remove(key);
                expirationCount++;
                missCount++;
                return null;
            }
            hitCount++;
            return entry.value;
        } finally {
            lock.unlock();
        }
    }

    int size() {
        lock.lock();
        try {
            return store.size();
        } finally {
            lock.unlock();
        }
    }

    String statsSummary() {
        lock.lock();
        try {
            return String.format(
                    "hits=%d misses=%d evictions=%d expirations=%d currentSize=%d",
                    hitCount, missCount, evictionCount, expirationCount, store.size());
        } finally {
            lock.unlock();
        }
    }
}


/**
 * Standalone entry point -- exercises InMemoryTtlLruCache's TTL expiration
 * and LRU eviction behavior and prints observable results. Compile and run
 * with nothing but a plain JDK (this class is package-private, not public,
 * specifically so this .java file can keep its descriptive filename while
 * still compiling directly -- javac only requires the FILENAME to match a
 * PUBLIC top-level class, and this file intentionally has none):
 *
 *     javac "06_redis_caffeine_java_caching_demo.java"
 *     java InMemoryTtlLruCacheDemo
 */
class InMemoryTtlLruCacheDemo {

    public static void main(String[] args) throws InterruptedException {
        demonstrateTtlExpiration();
        System.out.println();
        demonstrateLruEviction();
    }

    // =========================================================================
    // Demonstrates TTL: an entry written now is readable immediately, but
    // becomes an effective cache miss once its TTL has elapsed.
    // =========================================================================
    static void demonstrateTtlExpiration() throws InterruptedException {
        System.out.println("--- TTL expiration demo ---");

        // maximumSize=10 (not the focus here), ttlMillis=200 -- a short TTL so
        // this demo doesn't need to sleep for long.
        InMemoryTtlLruCache<String, String> cache = new InMemoryTtlLruCache<>(10, 200);

        cache.put("exchange-rate:USD-EUR", "0.92");
        System.out.println("Immediately after put -> get() = " + cache.get("exchange-rate:USD-EUR")
                + "  (expect the cached value: a fresh HIT)");

        Thread.sleep(250); // sleep past the 200ms TTL

        System.out.println("After sleeping past TTL -> get() = " + cache.get("exchange-rate:USD-EUR")
                + "  (expect null: the entry expired, i.e. a MISS)");

        System.out.println("Stats: " + cache.statsSummary());
    }

    // =========================================================================
    // Demonstrates LRU eviction: with maximumSize=3, inserting a 4th distinct
    // key evicts whichever existing key was LEAST RECENTLY USED -- which,
    // because we deliberately re-access "a" right before inserting the 4th
    // key, is "b" (not "a", even though "a" was inserted first).
    // =========================================================================
    static void demonstrateLruEviction() {
        System.out.println("--- LRU eviction demo ---");

        // A long TTL (1 hour) so TTL expiration doesn't interfere with this
        // demo -- only LRU eviction is under test here.
        InMemoryTtlLruCache<String, Integer> cache = new InMemoryTtlLruCache<>(3, 3_600_000);

        cache.put("a", 1);
        cache.put("b", 2);
        cache.put("c", 3);
        System.out.println("After inserting a, b, c (maximumSize=3) -> size = " + cache.size());

        // Re-access "a" -- this marks "a" as MOST recently used, leaving "b"
        // as the LEAST recently used entry (since "c" was inserted after "a"
        // was originally written, and "a" was JUST touched again by this get()).
        Integer touchedA = cache.get("a");
        System.out.println("Touched key 'a' via get() -> " + touchedA + "  (marks 'a' as most-recently-used)");

        // Inserting a 4th distinct key exceeds maximumSize=3, triggering
        // eviction of the current least-recently-used entry -- expected to
        // be "b", since "a" was just re-accessed and "c" is newer than "b".
        cache.put("d", 4);
        System.out.println("Inserted 'd' -> triggers eviction since maximumSize=3 exceeded");

        System.out.println("get(\"a\") = " + cache.get("a") + "  (expect 1 -- survived, was recently touched)");
        System.out.println("get(\"b\") = " + cache.get("b") + "  (expect null -- evicted as least-recently-used)");
        System.out.println("get(\"c\") = " + cache.get("c") + "  (expect 3 -- survived)");
        System.out.println("get(\"d\") = " + cache.get("d") + "  (expect 4 -- just inserted)");

        System.out.println("Stats: " + cache.statsSummary());
    }
}
