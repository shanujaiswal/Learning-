# Three Ways to Query -- and Why You Need All Three

--> The Spring Data JPA chapter briefly showed `@Query` with JPQL and a native SQL escape hatch. This chapter treats querying as a genuine decision space: **HQL/JPQL** for portable, entity-oriented static queries; the **JPA Criteria API** for programmatically BUILT queries whose shape isn't known until runtime; **native SQL** for vendor-specific power; and **Specifications** (Spring Data's higher-level wrapper around Criteria) for composable dynamic filtering without hand-rolling `CriteriaBuilder` calls everywhere.

```text
Query need                                        -->  Right tool
Static, known-at-compile-time query               -->  HQL/JPQL (@Query or derived method)
Query shape varies based on which filters a        -->  Criteria API (or Specifications, which wrap it)
  user actually supplied (dynamic WHERE clauses)
Needs a vendor-specific SQL feature JPQL can't      -->  Native SQL (nativeQuery = true)
  express (window functions, full-text search, ...)
```

# HQL / JPQL Syntax Beyond the Basics

--> HQL (Hibernate Query Language) and JPQL (its JPA-standardized subset) are functionally the same thing in modern Hibernate -- object-oriented query syntax operating on ENTITY and FIELD names, not table/column names, which Hibernate translates to real SQL at execution time.
--> **Beyond simple `WHERE`/`ORDER BY`, HQL supports:**

```java
// Aggregate functions + GROUP BY, exactly like SQL, but over entities/fields
@Query("SELECT p.category.name, COUNT(p), AVG(p.price) FROM Product p GROUP BY p.category.name HAVING COUNT(p) > 5")
List<Object[]> categorySummary();

// Constructor expressions -- project results DIRECTLY into a DTO, avoiding loading full
// entities (and their lazy proxies) when you only need a few fields. The DTO needs a
// matching constructor; the fully-qualified class name goes right in the JPQL string.
@Query("SELECT new com.example.dto.ProductSummary(p.id, p.name, p.price) FROM Product p WHERE p.category.id = :categoryId")
List<ProductSummary> summariesByCategory(@Param("categoryId") Long categoryId);

// Subqueries -- fully supported inside HQL, unlike some derived-query-method limitations
@Query("SELECT p FROM Product p WHERE p.price > (SELECT AVG(p2.price) FROM Product p2 WHERE p2.category = p.category)")
List<Product> aboveCategoryAveragePrice();

// Implicit vs explicit JOIN -- "p.category.name" is an IMPLICIT join (Hibernate joins
// automatically for you when you dot-navigate); an EXPLICIT join is needed when you
// also want to reference the joined entity itself, filter on it separately, or JOIN FETCH it.
@Query("SELECT p FROM Product p JOIN p.category c WHERE c.name = :categoryName AND c.active = true")
List<Product> findActiveInCategory(@Param("categoryName") String categoryName);

// Bulk UPDATE / DELETE -- operates directly against the database, bypassing the
// persistence context entirely (see the @Modifying gotcha in the Spring Data chapter).
@Modifying
@Query("DELETE FROM Product p WHERE p.category.id = :categoryId AND p.discontinued = true")
int purgeDiscontinued(@Param("categoryId") Long categoryId);
```

--> **HQL's key portability guarantee** -- because it references entity/field names rather than tables/columns, the SAME JPQL string produces correct (if dialect-flavored) SQL whether the underlying database is PostgreSQL, MySQL, or Oracle. This is the entire reason to prefer it over native SQL whenever it can express what you need.

# The JPA Criteria API -- Programmatic, Type-Safe Query Construction

--> HQL strings are static text -- fine when the query's SHAPE is known at compile time, but awkward the moment a query's structure needs to change based on RUNTIME conditions (e.g. "filter by category ONLY IF the user provided one, filter by price range ONLY IF both bounds were given"). Concatenating/interpolating JPQL strings by hand to handle this is fragile and a potential injection risk if done carelessly. The **Criteria API** builds the query as a tree of JAVA OBJECTS instead of a string, so conditions can be added/omitted programmatically, and (with the JPA metamodel) can be type-checked at compile time.

```java
public List<Product> searchProducts(String category, BigDecimal minPrice, BigDecimal maxPrice) {

    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Product> query = cb.createQuery(Product.class);
    Root<Product> product = query.from(Product.class);          // "FROM Product p" equivalent

    List<Predicate> predicates = new ArrayList<>();

    if (category != null) {
        predicates.add(cb.equal(product.get("category").get("name"), category));
    }
    if (minPrice != null) {
        predicates.add(cb.greaterThanOrEqualTo(product.get("price"), minPrice));
    }
    if (maxPrice != null) {
        predicates.add(cb.lessThanOrEqualTo(product.get("price"), maxPrice));
    }

    query.select(product).where(cb.and(predicates.toArray(new Predicate[0])));
    // Only the filters that were actually non-null end up in the generated SQL's WHERE clause --
    // impossible to express as cleanly with a single static JPQL string.

    return entityManager.createQuery(query).getResultList();
}
```

--> **`product.get("category")` -- the string-based weakness of the plain Criteria API** -- `get("category")` takes a raw STRING attribute name, which means a typo or a renamed field is only caught at RUNTIME, not compile time -- ironic for an API marketed as "type-safe." The JPA **metamodel** (generated classes like `Product_` via an annotation processor, e.g. Hibernate's `hibernate-jpamodelgen`) fixes this:

```java
// Generated by the annotation processor at build time: Product_.java, containing
// static, typed attribute references mirroring Product's fields.
predicates.add(cb.equal(product.get(Product_.category).get(Category_.name), category));
// Now a rename of Product.category or Category.name is a COMPILE error here, not a
// runtime string-mismatch bug discovered in production.
```

--> **When the Criteria API genuinely earns its complexity** -- dynamic, optional-filter search screens (the classic "advanced search" form with a dozen optional fields), building queries programmatically from generic/reusable filtering infrastructure, or any case where the query's structure is data-driven rather than fixed. **For everything else, plain JPQL/HQL is simpler to write, read, and maintain** -- don't reach for Criteria by default.

# Specifications -- Spring Data's Higher-Level Wrapper

--> Spring Data JPA's `Specification<T>` interface wraps the exact same underlying Criteria API machinery, but lets you compose small, REUSABLE, named predicate-building units instead of one large method building everything inline -- and lets a repository accept an assembled `Specification` directly.

```java
interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    // JpaSpecificationExecutor adds findAll(Specification<Product>), count(Specification<Product>), etc.
}

class ProductSpecifications {

    static Specification<Product> hasCategory(String categoryName) {
        return (root, query, cb) ->
            categoryName == null ? null : cb.equal(root.get("category").get("name"), categoryName);
        // returning null from a Specification means "no predicate contributed" -- Spring Data
        // silently omits it when combining specifications, which is exactly what makes
        // optional filters composable this cleanly.
    }

    static Specification<Product> priceAtLeast(BigDecimal minPrice) {
        return (root, query, cb) ->
            minPrice == null ? null : cb.greaterThanOrEqualTo(root.get("price"), minPrice);
    }

    static Specification<Product> priceAtMost(BigDecimal maxPrice) {
        return (root, query, cb) ->
            maxPrice == null ? null : cb.lessThanOrEqualTo(root.get("price"), maxPrice);
    }
}

// Calling code -- composes only the filters actually needed, reads almost like a fluent builder:
Specification<Product> spec = Specification
        .where(ProductSpecifications.hasCategory(category))
        .and(ProductSpecifications.priceAtLeast(minPrice))
        .and(ProductSpecifications.priceAtMost(maxPrice));

List<Product> results = productRepository.findAll(spec);
```

--> **Specifications vs raw Criteria API -- the practical difference:** Specifications are individually small, independently testable, and composable across DIFFERENT query methods/screens (e.g. `hasCategory()` can be reused in three different search endpoints); a hand-written Criteria method tends to bundle everything into one long, non-reusable block. **Prefer Specifications over raw `CriteriaBuilder` code in a Spring Data project** unless you need something Specifications' interface genuinely can't express.

# Native SQL Queries -- When and How

--> `nativeQuery = true` switches JPQL translation off entirely -- you write REAL SQL against REAL table/column names, and Hibernate just executes it and maps the `ResultSet` back (to an entity, if the columns line up, or to a projection/DTO otherwise).

```java
interface ProductRepository extends JpaRepository<Product, Long> {

    // Maps back to Product directly -- requires selecting exactly the columns Product expects,
    // aliased to match if column names differ from the entity's mapped names.
    @Query(value = "SELECT * FROM products WHERE price > :minPrice ORDER BY created_at DESC LIMIT 10",
           nativeQuery = true)
    List<Product> findTopExpensiveNative(@Param("minPrice") BigDecimal minPrice);

    // A vendor-specific feature JPQL genuinely cannot express -- e.g. PostgreSQL's
    // full-text search operators, or a window function like RANK()/ROW_NUMBER().
    @Query(value = """
        SELECT p.*, RANK() OVER (PARTITION BY p.category_id ORDER BY p.price DESC) AS price_rank
        FROM products p
        """, nativeQuery = true)
    List<Object[]> rankProductsWithinCategory();

    // Native query + pagination requires an explicit countQuery, since Hibernate can't
    // automatically derive a COUNT(*) equivalent from an arbitrary native SQL string
    // the way it can for JPQL.
    @Query(value = "SELECT * FROM products WHERE category_id = :catId",
           countQuery = "SELECT COUNT(*) FROM products WHERE category_id = :catId",
           nativeQuery = true)
    Page<Product> findByCategoryNative(@Param("catId") Long catId, Pageable pageable);
}
```

--> **The real trade-off of native SQL** -- full access to the database's specific dialect/features, at the cost of PORTABILITY (the query breaks silently or errors outright if you ever switch database vendors) and of losing JPQL's entity-graph-aware navigation (`p.category.name` style dot-paths). **Reach for it only when JPQL genuinely cannot express what's needed** -- it should be the exception, not the default.
--> **SQL injection risk is IDENTICAL to JDBC** -- native queries still use bound `:param`/`?1` placeholders (never string-concatenate user input into the query string), and Spring Data's parameter binding protects against injection exactly the same way `PreparedStatement` does under plain JDBC.

# Choosing Between the Three -- A Decision Table

| Situation | Use |
|---|---|
| Simple lookup expressible as a method name | Derived query method (not covered again here -- see Spring Data JPA chapter) |
| Static query, known shape, portable across DBs | HQL/JPQL via `@Query` |
| Query's WHERE clause structure varies by which filters were actually supplied at runtime | `Specification` (or raw Criteria API for cases Specifications can't express) |
| Needs a genuinely vendor-specific SQL feature | Native SQL (`nativeQuery = true`) |
| Only need a handful of fields, not full entities, for a read-only view | JPQL constructor expression (`SELECT new ...DTO(...)`) regardless of which of the above otherwise applies |

# Common Gotchas

--> **Reaching for the Criteria API for a query whose shape never actually varies** -- adds verbosity and indirection for no benefit; plain JPQL is simpler when the query's structure is fixed.
--> **Using `get("fieldName")` string literals in Criteria code without the generated metamodel** -- reintroduces exactly the stringly-typed, rename-unsafe fragility the Criteria API is supposed to avoid; generate and use the `_` metamodel classes for real type safety.
--> **Forgetting `countQuery` on a paginated native query** -- Spring Data cannot auto-derive a count for an arbitrary native SQL string the way it can for JPQL; pagination silently breaks or throws without it.
--> **String-concatenating user input into a native (or JPQL) query instead of using bound parameters** -- reintroduces SQL injection risk exactly as it would under raw JDBC; always use `:param`/`?1` placeholders.
--> **Returning `Specification` predicates that throw instead of returning `null` for "not applicable"** -- the documented convention is that a `null` predicate means "contribute nothing," which is what makes specifications cleanly composable for optional filters.
--> **Loading full entities via JPQL when only 2-3 fields are actually needed for a view** -- prefer a constructor expression (`SELECT new ...`) to avoid the overhead (and any lazy-loading proxy chains) of hydrating entities you don't need in full.

# Best Practices Summary

--> Default to JPQL (or derived query methods) for anything with a fixed, known-at-compile-time shape; it's the most readable and most portable option.
--> Reach for `Specification`s (not raw `CriteriaBuilder` code) whenever a query's filters are genuinely optional/dynamic at runtime -- compose small, individually testable predicate-building methods.
--> Generate and use the JPA metamodel (`Product_`, `Category_`, ...) with the Criteria API/Specifications for real compile-time safety, rather than raw string attribute names.
--> Treat native SQL as an intentional, occasional escape hatch for vendor-specific features JPQL cannot express -- never as a default querying style.
--> Always use bound parameters, never string concatenation, regardless of which of the three approaches you're using.
--> Use JPQL constructor expressions to project directly into DTOs whenever a view only needs a subset of an entity's fields.
