/**
 * 05_caching_fundamentals_spring_cache_demo.java
 *
 * Illustrates:
 *     1. @EnableCaching configuration (required -- without it, the caching
 *        annotations below are silently inert)
 *     2. @Cacheable -- automated cache-aside on the read path
 *     3. @CachePut -- always executes, then refreshes the cache (write path)
 *     4. @CacheEvict -- removes a single key, or clears an entire cache region
 *        (allEntries=true)
 *     5. SpEL key expressions, composite keys, and condition/unless for
 *        conditional caching
 *     6. The self-invocation gotcha (calling an @Cacheable method from
 *        within the same class bypasses the proxy) shown explicitly
 *
 * Covers Theory chapter:
 *     10) Java/18) Messaging and Caching/Theory/05 Caching Fundamentals and
 *     Spring Cache Abstraction.md
 *
 * IMPORTANT -- THIS FILE DOES NOT COMPILE OR RUN ON ITS OWN.
 *     It requires a real Spring Boot project (generated via
 *     https://start.spring.io) with at least "spring-boot-starter" on the
 *     classpath. No external cache provider (Redis/Caffeine) is required for
 *     THIS file specifically -- with @EnableCaching alone and no provider
 *     dependency added, Spring Boot falls back to the simple in-memory
 *     ConcurrentMapCacheManager (no eviction, no TTL -- fine for this
 *     illustration, NOT for production; see Theory chapter 06 for real
 *     providers with genuine eviction/TTL).
 *
 * To actually run this (conceptually):
 *     1. Add "spring-boot-starter" (or any Spring Boot starter) to a Maven/
 *        Gradle project.
 *     2. Copy/paste the classes below into that project's src/main/java tree
 *        (each class would normally live in its own .java file matching its
 *        class name).
 *     3. mvn spring-boot:run
 */

package com.example.caching.springcache;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

// =============================================================================
// 1) CACHE CONFIGURATION -- @EnableCaching activates AOP proxies that
//    intercept calls to @Cacheable/@CachePut/@CacheEvict-annotated methods.
//    FORGETTING THIS ANNOTATION is the #1 "why isn't my cache doing anything"
//    gotcha -- the annotations below would otherwise just be inert metadata.
// =============================================================================
@Configuration
@EnableCaching
class CacheConfig {

    // Explicitly declaring the (simple, no-eviction, no-TTL) in-memory
    // CacheManager here purely to name the cache regions used below --
    // "products" and "users". In a real production app this bean would be
    // replaced by a CaffeineCacheManager or RedisCacheManager (see Theory
    // chapter 06 and Practical file 06) rather than this demo-only default.
    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("products", "users");
    }
}


// =============================================================================
// 2) DOMAIN TYPE -- kept as an immutable record deliberately: Theory chapter
//    05's Best Practices explicitly warn against caching MUTABLE objects and
//    then mutating them after retrieval, which silently corrupts the cached
//    instance for every future reader of that key.
// =============================================================================
record Product(String id, String name, double price, int version) {
}


// A trivial stand-in for a real Spring Data repository / database call --
// pretends every lookup is expensive (e.g. a 50ms round-trip in a real DB).
interface ProductRepository {
    Optional<Product> findById(String productId);

    Product save(Product product);

    void deleteById(String productId);

    void saveAll(List<Product> products);
}


class ProductNotFoundException extends RuntimeException {
    ProductNotFoundException(String productId) {
        super("Product not found: " + productId);
    }
}


// =============================================================================
// 3) THE SERVICE -- @Cacheable / @CachePut / @CacheEvict in action
// =============================================================================
@Service
class ProductService {

    private final ProductRepository productRepository;

    ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    // --- READ PATH: @Cacheable -----------------------------------------------
    // This method body ONLY RUNS on a cache miss -- Spring's AOP proxy
    // intercepts the call, checks the "products" cache for key #productId
    // FIRST, and only invokes this method if there's no cached entry. The
    // return value is then automatically stored under that key for next time.
    //
    // unless = "#result == null" avoids permanently caching a "not found"
    // null for a record that might be created moments later (Theory 05's
    // "Caching exceptions or null unintentionally" gotcha).
    @Cacheable(cacheNames = "products", key = "#productId", unless = "#result == null")
    public Product findById(String productId) {
        System.out.println("Cache MISS -- hitting the database for " + productId);
        return productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }

    // Conditional caching: condition is evaluated BEFORE the method runs and
    // decides whether caching applies to this invocation AT ALL; unless is
    // evaluated AFTER (with access to #result) and decides whether to skip
    // STORING the result specifically.
    @Cacheable(cacheNames = "products", key = "#productId",
            condition = "#productId != null",
            unless = "#result == null")
    public Product findByIdConditional(String productId) {
        System.out.println("Cache MISS (conditional lookup) -- hitting the database for " + productId);
        return productRepository.findById(productId).orElse(null);
    }

    // --- WRITE PATH (1 of 2): @CachePut --------------------------------------
    // Unlike @Cacheable, this method body ALWAYS EXECUTES -- @CachePut never
    // short-circuits on a cache "hit," because the whole point is to perform
    // the write and then refresh what's cached, keeping subsequent
    // findById(...) reads consistent with what was just saved.
    //
    // The SpEL key expression here reads a field off the method PARAMETER
    // (#product.id) rather than referencing the parameter directly -- the
    // composite-key style mentioned in Theory 05 ("#product.id + '-' +
    // #product.version") would look like the commented-out variant below.
    @CachePut(cacheNames = "products", key = "#product.id")
    public Product update(Product product) {
        System.out.println("Always executes -- persisting update for " + product.id());
        return productRepository.save(product);
        // Composite-key alternative, if the cache needed to be versioned:
        //   @CachePut(cacheNames = "products", key = "#product.id + '-' + #product.version")
    }

    // --- WRITE PATH (2 of 2): @CacheEvict -- single key ----------------------
    @CacheEvict(cacheNames = "products", key = "#productId")
    public void delete(String productId) {
        productRepository.deleteById(productId);
        // After this method returns, the "products" cache entry for
        // #productId is removed -- the next findById(productId) call is
        // guaranteed to be a miss, re-querying (and finding it gone).
    }

    // --- WRITE PATH (2b): @CacheEvict -- allEntries=true clears the WHOLE
    // cache region in one shot, useful for bulk operations where invalidating
    // keys individually isn't practical.
    @CacheEvict(cacheNames = "products", allEntries = true)
    public void bulkImport(List<Product> products) {
        productRepository.saveAll(products);
    }

    // =========================================================================
    // 4) THE SELF-INVOCATION GOTCHA, SHOWN EXPLICITLY
    // =========================================================================
    // Calling findById(...) from HERE (this.findById(id), i.e. from another
    // method in the SAME class) bypasses the Spring AOP proxy entirely -- the
    // call never goes through the interception logic that checks the cache,
    // so caching silently does nothing, even though findById itself is
    // annotated @Cacheable. This method exists purely to demonstrate the
    // gotcha; callFindByIdThroughProxyInstead() below shows the correct fix.
    public Product brokenSelfInvocationExample(String productId) {
        // BUG: this is a plain Java method call on "this" -- no proxy
        // interception happens, so @Cacheable on findById is silently ignored
        // every time this method is called.
        return this.findById(productId);
    }

    // The fix in real code is almost always: inject ProductService into the
    // CALLER (a different bean) and call productService.findById(id) from
    // OUTSIDE this class, so the call passes through the Spring-managed
    // proxy. (Self-injecting a reference to one's own proxy is possible via
    // ApplicationContext lookups but is generally considered a design smell
    // preferably avoided by restructuring into two collaborating services.)
}
