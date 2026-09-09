# Fetch Types Recap and Where This Chapter Goes Further

--> The Spring Data JPA chapter covers the basic LAZY/EAGER default table and a one-line mention of `JOIN FETCH` fixing N+1. This chapter goes deep on WHY `LazyInitializationException` happens mechanically, how to systematically DETECT N+1 (not just fix one instance of it), `@EntityGraph` as a declarative alternative to `JOIN FETCH`, and batch fetching as a third strategy.

# How Lazy Loading Actually Works -- Proxies

--> When you mark a relationship `FetchType.LAZY`, Hibernate does NOT leave the field null/unset -- it populates it with a **runtime-generated proxy object** (a dynamically generated subclass of the entity, or for collections, a Hibernate-specific collection wrapper like `PersistentBag`/`PersistentSet`). This proxy looks and behaves like the real entity for most purposes but holds no actual data yet, only the entity's ID and a reference back to the still-open Session.

```java
Product product = entityManager.find(Product.class, 1L);
// product.category is now a Hibernate PROXY object -- NOT null, NOT the real Category yet.
// It knows category's id (from the category_id FK column already loaded with Product),
// but has NOT executed "SELECT * FROM categories WHERE id = ?" yet.

Category category = product.getCategory();      // still just returns the proxy -- no query yet
String categoryName = category.getName();        // <-- THIS line triggers proxy initialization:
                                                   //     Hibernate now issues the SELECT, populates
                                                   //     the real fields, and the proxy delegates to them
```

--> **The proxy only fires its backing query the moment you actually access a real field/method on it** (other than the ID, which the proxy already knows without a query). This is the entire mechanism behind both the convenience of lazy loading AND every `LazyInitializationException` you will ever encounter.
--> **`LazyInitializationException` -- what is mechanically happening:** the proxy needs an ACTIVE Session to run its backing query when triggered. If that Session has already closed (the transaction ended, the `EntityManager` closed) by the time you access the lazy field, there is no Session left to run the query through, and Hibernate throws instead of silently failing.

```java
// Service layer -- @Transactional method, Session/EntityManager is open here
@Transactional(readOnly = true)
public Product getProduct(Long id) {
    return productRepository.findById(id).orElseThrow();
    // Session closes the moment this method returns (transaction boundary ends here)
}

// Controller layer -- Session is ALREADY CLOSED by the time this runs
@GetMapping("/products/{id}")
public ProductView getProductView(@PathVariable Long id) {
    Product product = productService.getProduct(id);
    String categoryName = product.getCategory().getName();   // <-- LazyInitializationException HERE
    // "failed to lazily initialize a collection/proxy... no Session"
}
```

--> **The three real fixes, in order of preference:**

| Fix | How | Trade-off |
|---|---|---|
| Fetch what you need INSIDE the transactional method | Access/map to a DTO before the method returns | Best -- keeps fetching decisions where the query is defined; no wider transaction needed |
| `JOIN FETCH` / `@EntityGraph` for that specific query | Eagerly pull the association for just this call | Good -- targeted, doesn't affect other queries against the same entity |
| Widen `@Transactional` to cover the view-rendering code too | Keep the Session open longer ("Open Session In View") | Blunter -- couples persistence lifetime to presentation layer, can hide N+1 problems, generally discouraged in modern Spring Boot (disabled by default via `spring.jpa.open-in-view=false` being the RECOMMENDED override) |

--> **Never "fix" this by switching the relationship to `EAGER`** -- that removes the exception by always loading the association immediately, everywhere, for every query against that entity, whether needed or not -- trading a loud, obvious exception for a silent, compounding performance cost (see N+1 below). An exception you can see and fix is preferable to a slow query you can't.
--> **`spring.jpa.open-in-view`** defaults to `true` in Spring Boot, which keeps the Hibernate Session open for the ENTIRE HTTP request (not just the `@Transactional` method), masking `LazyInitializationException` by accident -- convenient for prototypes, but it hides N+1 problems and couples the web tier to persistence lifetime. Most teams explicitly set `spring.jpa.open-in-view=false` and handle fetching deliberately instead.

# The N+1 Query Problem -- Detection and Fixes

--> **The problem, precisely:** fetching a list of N parent entities with one query, then accessing a LAZY association on each of them in a loop, issues 1 (for the parents) + N (one per parent, for the association) queries -- instead of a single query joining everything up front. It scales linearly with result size and is invisible in code review unless you specifically look for it.

```java
List<Product> products = productRepository.findAll();     // query #1 -- N products loaded

for (Product product : products) {
    System.out.println(product.getCategory().getName());  // triggers ONE query PER product
    // for 500 products -> 1 + 500 = 501 total queries, most of them redundant
    // (many products likely share the same category, re-fetched over and over)
}
```

--> **How to DETECT N+1 systematically** (not just notice one instance by luck):

| Technique | How |
|---|---|
| `spring.jpa.show-sql=true` + `spring.jpa.properties.hibernate.format_sql=true` | Manually eyeball repeated near-identical `SELECT` statements in logs during a single request |
| **Datasource-proxy / p6spy** | Third-party JDBC proxy libraries that log every query with a stack trace and count queries per request -- far more reliable than eyeballing logs |
| **Hibernate statistics** (`spring.jpa.properties.hibernate.generate_statistics=true`) | Hibernate tracks and can log query counts, cache hit ratios, etc. per session -- inspect via `SessionFactory.getStatistics()` |
| **Integration test asserting query count** | Libraries like `datasource-proxy` or manual `Statistics` assertions can fail a test if a code path issues more than the expected number of queries -- turns N+1 into a regression-tested invariant, not a hope |
| **APM/observability tools** (e.g. a query count widget in New Relic/Datadog) | Production-level detection when a specific endpoint's DB time spikes disproportionately to request volume |

--> **Fix #1 -- `JOIN FETCH` in JPQL** -- pulls the association in the SAME query as the parent, via a real SQL `JOIN`, for that specific query only (doesn't change the entity's mapped fetch type globally):

```java
@Query("SELECT p FROM Product p JOIN FETCH p.category WHERE p.price >= :minPrice")
List<Product> findExpensiveProductsWithCategory(@Param("minPrice") BigDecimal minPrice);
// Now ONE query total: a single SELECT with a JOIN, category data already populated,
// no proxy-triggered follow-up queries in the loop.
```

--> **Fix #2 -- `@EntityGraph`** -- a more declarative, reusable alternative to writing `JOIN FETCH` by hand in every query, especially useful when the SAME fetch shape is needed across several different query methods:

```java
@Entity
@NamedEntityGraph(
    name = "Product.withCategory",
    attributeNodes = @NamedAttributeNode("category")
)
class Product { /* ... */ }

interface ProductRepository extends JpaRepository<Product, Long> {

    @EntityGraph(value = "Product.withCategory")     // references the @NamedEntityGraph above
    List<Product> findByPriceGreaterThanEqual(BigDecimal minPrice);   // derived query -- no @Query needed at all

    // Ad-hoc entity graph -- doesn't need a @NamedEntityGraph declared on the entity,
    // just lists the attribute paths to eagerly fetch for THIS method only.
    @EntityGraph(attributePaths = {"category", "tags"})
    Optional<Product> findWithDetailsById(Long id);
}
```

--> **`JOIN FETCH` vs `@EntityGraph` -- when to pick which:**

| | `JOIN FETCH` (JPQL) | `@EntityGraph` |
|---|---|---|
| Works with derived query methods (no `@Query` needed) | No -- requires writing JPQL | **Yes** -- can attach to a plain derived method |
| Best for | One-off, hand-tuned complex queries | Reusable fetch shapes across multiple query methods |
| Readability for deep/multiple joins | Can get unwieldy with many `JOIN FETCH`es | Cleaner for "always fetch these N associations together" |

--> **Fix #3 -- Batch fetching (`@BatchSize` / `hibernate.default_batch_fetch_size`)** -- when a full `JOIN FETCH` isn't practical (e.g. would multiply rows unacceptably across several collections), Hibernate can fetch lazy associations in BATCHES instead of one-by-one: for 500 products with `@BatchSize(size = 20)` on `Category`, Hibernate issues `~25` queries (`SELECT ... WHERE category_id IN (?, ?, ..., ?20)`) instead of 500 single-row queries -- not as good as one query, but far better than N.

```java
@Entity
class Category {
    @Id @GeneratedValue private Long id;
    // ...
}

// Applied where Category is the "many" side being lazily fetched in batches:
@Entity
class Product {
    @ManyToOne(fetch = FetchType.LAZY)
    @BatchSize(size = 20)     // Hibernate-native annotation -- batches up to 20 proxy initializations per query
    private Category category;
}
```

--> **`@Fetch(FetchMode.SUBSELECT)`** (Hibernate-native, another N+1 mitigation) -- instead of batching by ID list, re-runs the ORIGINAL query's WHERE clause as a subselect to fetch ALL related rows for the whole result set in one extra query, regardless of how many distinct parent rows there are: effectively turns N+1 into exactly 2 queries total.

# `EntityGraph` Types -- `FETCH` vs `LOAD`

--> `@EntityGraph`'s `type` attribute controls how attributes NOT listed in the graph are treated:

| Type | Attributes IN the graph | Attributes NOT in the graph |
|---|---|---|
| `EntityGraphType.FETCH` (default) | Loaded eagerly | Forced to `LAZY`, regardless of their mapped default |
| `EntityGraphType.LOAD` | Loaded eagerly | Keep whatever their mapped fetch type already is |

```java
@EntityGraph(attributePaths = "category", type = EntityGraph.EntityGraphType.LOAD)
Optional<Product> findWithCategoryById(Long id);
// "category" is eagerly fetched; any OTHER lazy/eager association on Product keeps its
// own mapped default -- unlike FETCH mode, which would force everything else to LAZY.
```

# Common Gotchas

--> **Accessing a lazy field after the Session/transaction has closed** -- `LazyInitializationException`; fix by fetching what's needed inside the transactional boundary, not by switching to `EAGER`.
--> **Leaving `spring.jpa.open-in-view=true` (the default) and never noticing N+1 because the exception never fires** -- the query storm still happens, it's just hidden behind a Session that stays open for the whole request; explicitly disable it and handle fetching deliberately.
--> **Fixing ONE N+1 instance you happened to notice, without a systematic way to catch the next one** -- add query-count assertions to integration tests or turn on Hibernate statistics logging in a staging environment, rather than relying on spotting it by inspection.
--> **Using `JOIN FETCH` with multiple `List`-typed collections in one query** -- can trigger a Cartesian-product-style row explosion (each collection multiplies the result rows); fetch at most one collection eagerly per query, or fetch collections separately.
--> **Assuming `@EntityGraph` and `JOIN FETCH` are interchangeable in every case** -- `@EntityGraph` cannot express arbitrary JPQL conditions the way a hand-written `JOIN FETCH` query can; pick `JOIN FETCH` when you need a genuinely custom query shape.

# Best Practices Summary

--> Default every `@ManyToOne`/`@OneToOne` to `FetchType.LAZY` explicitly, and load associations deliberately, per query, rather than relying on `EAGER` or a wide-open Session.
--> Set `spring.jpa.open-in-view=false` and fetch what each use case needs inside its own `@Transactional` boundary or query.
--> Reach for `JOIN FETCH` for one-off custom queries, `@EntityGraph` for fetch shapes reused across several query methods, and `@BatchSize`/`FetchMode.SUBSELECT` when a full join isn't practical.
--> Build a habit of checking query counts (SQL logging, Hibernate statistics, or automated test assertions) for any new list-rendering code path that touches associations -- don't rely on spotting N+1 by inspection alone.
--> Never treat switching to `EAGER` as a fix for `LazyInitializationException` -- it trades a visible exception for an invisible, compounding performance cost.
