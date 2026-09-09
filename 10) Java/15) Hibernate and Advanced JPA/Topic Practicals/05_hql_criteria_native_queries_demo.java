/**
 * 05_hql_criteria_native_queries_demo.java
 *
 * Demonstrates, with illustrative Spring Data JPA / Hibernate code:
 *     1. HQL/JPQL queries via @Query -- aggregate + GROUP BY/HAVING, a constructor
 *        expression projecting into a DTO, and an explicit JOIN
 *     2. The JPA Criteria API -- a programmatically built query whose predicates are
 *        added only for filters actually supplied at runtime
 *     3. A native SQL query (nativeQuery = true), including the countQuery required
 *        for native + pagination
 *     4. A Specification-based dynamic query -- Spring Data's composable, reusable
 *        wrapper around the same Criteria API machinery
 *
 * Covers Theory chapter:
 *     15) Hibernate and Advanced JPA/Theory/05 HQL Criteria API and Native Queries.md
 *
 * IMPORTANT -- this file is illustrative, NOT a standalone runnable program.
 * It requires a real Spring Boot project on the classpath to compile/run:
 *     - spring-boot-starter-data-jpa
 *     - a JDBC driver for your target database
 *     - (optional, for full compile-time type safety) hibernate-jpamodelgen annotation
 *       processor generating Product_/Category_ metamodel classes -- this file uses
 *       plain string attribute names instead, to avoid depending on generated code
 *
 * Run (in a real Spring Boot project, after wiring this into src/main/java/...):
 *     mvn spring-boot:run
 *   or
 *     ./gradlew bootRun
 */

import jakarta.persistence.*;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

// ---------------------------------------------------------------------------
// Entities -- Product/Category, kept simple since this file's focus is the
// QUERYING layer, not the mapping.
// ---------------------------------------------------------------------------

@Entity
@Table(name = "categories")
class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false)
    private boolean active;

    protected Category() { }

    public Category(String name, boolean active) {
        this.name = name;
        this.active = active;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public boolean isActive() { return active; }
}

@Entity
@Table(name = "products")
class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private boolean discontinued;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    protected Product() { }

    public Product(String name, BigDecimal price, Category category) {
        this.name = name;
        this.price = price;
        this.category = category;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public BigDecimal getPrice() { return price; }
    public boolean isDiscontinued() { return discontinued; }
    public Category getCategory() { return category; }
}

// A plain DTO for a JPQL constructor expression -- avoids loading full Product
// entities (and their lazy Category proxies) when only a few fields are needed.
class ProductSummary {
    private final Long id;
    private final String name;
    private final BigDecimal price;

    // Constructor signature must match the JPQL "SELECT new ...ProductSummary(...)"
    // argument list exactly, in order and type.
    public ProductSummary(Long id, String name, BigDecimal price) {
        this.id = id;
        this.name = name;
        this.price = price;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public BigDecimal getPrice() { return price; }
}

// ---------------------------------------------------------------------------
// 1) Repository -- HQL/JPQL via @Query: aggregate + GROUP BY/HAVING, a
//    constructor-expression DTO projection, an explicit JOIN, a bulk
//    @Modifying DELETE, plus native SQL queries (with the required countQuery
//    for pagination).
// ---------------------------------------------------------------------------

interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    // Aggregate functions + GROUP BY/HAVING, exactly like SQL but over
    // entity/field names. Returns Object[] rows: [categoryName, count, avgPrice].
    @Query("SELECT p.category.name, COUNT(p), AVG(p.price) " +
           "FROM Product p GROUP BY p.category.name HAVING COUNT(p) > 5")
    List<Object[]> categorySummary();

    // Constructor expression -- projects DIRECTLY into a DTO, skipping full
    // entity (and lazy proxy) hydration entirely for this read-only view.
    @Query("SELECT new ProductSummary(p.id, p.name, p.price) " +
           "FROM Product p WHERE p.category.id = :categoryId")
    List<ProductSummary> summariesByCategory(@Param("categoryId") Long categoryId);

    // Explicit JOIN -- needed here because we filter on the joined Category
    // ("c.active = true") separately from Product's own fields, not just
    // dot-navigate into it.
    @Query("SELECT p FROM Product p JOIN p.category c WHERE c.name = :categoryName AND c.active = true")
    List<Product> findActiveInCategory(@Param("categoryName") String categoryName);

    // Bulk DELETE -- operates directly against the database, bypassing the
    // persistence context. @Modifying is REQUIRED alongside @Query for any
    // UPDATE/DELETE JPQL statement.
    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM Product p WHERE p.category.id = :categoryId AND p.discontinued = true")
    int purgeDiscontinued(@Param("categoryId") Long categoryId);

    // -------------------------------------------------------------------
    // Native SQL -- real table/column names, Hibernate just executes and
    // maps the ResultSet back. Reserved for genuinely vendor-specific needs.
    // -------------------------------------------------------------------

    @Query(value = "SELECT * FROM products WHERE price > :minPrice ORDER BY id DESC LIMIT 10",
           nativeQuery = true)
    List<Product> findTopExpensiveNative(@Param("minPrice") BigDecimal minPrice);

    // Native query + pagination REQUIRES an explicit countQuery -- Hibernate
    // cannot auto-derive a COUNT(*) equivalent from an arbitrary native SQL
    // string the way it can for JPQL. Omitting this silently breaks/throws.
    @Query(value = "SELECT * FROM products WHERE category_id = :catId",
           countQuery = "SELECT COUNT(*) FROM products WHERE category_id = :catId",
           nativeQuery = true)
    Page<Product> findByCategoryNative(@Param("catId") Long catId, Pageable pageable);
}

interface CategoryRepository extends JpaRepository<Category, Long> { }

// ---------------------------------------------------------------------------
// 2) JPA Criteria API -- built as a tree of Java objects rather than a
//    string, so predicates are added/omitted PROGRAMMATICALLY based on which
//    filters were actually supplied at runtime. Uses plain string attribute
//    names here (get("category")) rather than a generated metamodel, for
//    simplicity -- see the header note on hibernate-jpamodelgen for the
//    fully type-safe alternative.
// ---------------------------------------------------------------------------

@Repository
class ProductCriteriaSearchRepository {

    private final EntityManager entityManager;

    public ProductCriteriaSearchRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public List<Product> searchProducts(String categoryName, BigDecimal minPrice, BigDecimal maxPrice) {

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Product> query = cb.createQuery(Product.class);
        Root<Product> product = query.from(Product.class);          // "FROM Product p" equivalent

        List<Predicate> predicates = new ArrayList<>();

        if (categoryName != null) {
            predicates.add(cb.equal(product.get("category").get("name"), categoryName));
        }
        if (minPrice != null) {
            predicates.add(cb.greaterThanOrEqualTo(product.get("price"), minPrice));
        }
        if (maxPrice != null) {
            predicates.add(cb.lessThanOrEqualTo(product.get("price"), maxPrice));
        }

        query.select(product).where(cb.and(predicates.toArray(new Predicate[0])));
        // Only the filters that were actually non-null end up in the generated
        // SQL's WHERE clause -- impossible to express as cleanly with one
        // static JPQL string.

        return entityManager.createQuery(query).getResultList();
    }
}

// ---------------------------------------------------------------------------
// 3) Specifications -- Spring Data's higher-level wrapper around the exact
//    same Criteria API machinery, composed as small, reusable, individually
//    testable predicate-building units.
// ---------------------------------------------------------------------------

class ProductSpecifications {

    static Specification<Product> hasCategory(String categoryName) {
        // Returning null means "no predicate contributed" -- Spring Data
        // silently omits it when combining specifications, which is exactly
        // what makes optional filters composable this cleanly.
        return (root, query, cb) ->
            categoryName == null ? null : cb.equal(root.get("category").get("name"), categoryName);
    }

    static Specification<Product> priceAtLeast(BigDecimal minPrice) {
        return (root, query, cb) ->
            minPrice == null ? null : cb.greaterThanOrEqualTo(root.get("price"), minPrice);
    }

    static Specification<Product> priceAtMost(BigDecimal maxPrice) {
        return (root, query, cb) ->
            maxPrice == null ? null : cb.lessThanOrEqualTo(root.get("price"), maxPrice);
    }

    static Specification<Product> notDiscontinued() {
        return (root, query, cb) -> cb.isFalse(root.get("discontinued"));
    }
}

// ---------------------------------------------------------------------------
// 4) Service layer -- ties all three approaches (HQL, Criteria, Specification)
//    plus native SQL together behind one query-facing API.
// ---------------------------------------------------------------------------

@Service
class ProductQueryService {

    private final ProductRepository productRepository;
    private final ProductCriteriaSearchRepository criteriaSearchRepository;

    public ProductQueryService(ProductRepository productRepository,
                                ProductCriteriaSearchRepository criteriaSearchRepository) {
        this.productRepository = productRepository;
        this.criteriaSearchRepository = criteriaSearchRepository;
    }

    // Static, known-at-compile-time shape -- plain JPQL via @Query is simplest here.
    @Transactional(readOnly = true)
    public List<ProductSummary> getSummariesByCategory(Long categoryId) {
        return productRepository.summariesByCategory(categoryId);
    }

    // Query's structure is fixed but genuinely needs an explicit join filter.
    @Transactional(readOnly = true)
    public List<Product> getActiveInCategory(String categoryName) {
        return productRepository.findActiveInCategory(categoryName);
    }

    // Vendor-specific pagination example via native SQL + required countQuery.
    @Transactional(readOnly = true)
    public Page<Product> getByCategoryNative(Long categoryId, Pageable pageable) {
        return productRepository.findByCategoryNative(categoryId, pageable);
    }

    // Dynamic filter shape -- delegates to the hand-rolled Criteria API method.
    @Transactional(readOnly = true)
    public List<Product> searchViaCriteria(String categoryName, BigDecimal minPrice, BigDecimal maxPrice) {
        return criteriaSearchRepository.searchProducts(categoryName, minPrice, maxPrice);
    }

    // The SAME dynamic-filter use case, expressed instead via composable
    // Specifications -- preferred over hand-rolled CriteriaBuilder code in a
    // Spring Data project, since each predicate-building method here is
    // independently reusable and testable across different search endpoints.
    @Transactional(readOnly = true)
    public List<Product> searchViaSpecification(String categoryName, BigDecimal minPrice, BigDecimal maxPrice) {
        Specification<Product> spec = Specification
                .where(ProductSpecifications.hasCategory(categoryName))
                .and(ProductSpecifications.priceAtLeast(minPrice))
                .and(ProductSpecifications.priceAtMost(maxPrice))
                .and(ProductSpecifications.notDiscontinued());

        return productRepository.findAll(spec);
    }
}

/*
 * NOTE on annotations used above:
 * This snippet uses real JPA (jakarta.persistence.*, jakarta.persistence.criteria.*)
 * and Spring Data JPA (org.springframework.data.jpa.repository.*,
 * org.springframework.data.jpa.domain.Specification) APIs exactly as they'd appear in
 * a real Spring Boot project. Running any of these queries requires a configured
 * EntityManagerFactory/DataSource and a populated database matching the entities --
 * intentionally omitted here since this file is illustrative only (see header).
 */
