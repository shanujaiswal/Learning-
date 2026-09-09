/**
 * 03_lazy_eager_n_plus_1_demo.java
 *
 * Demonstrates, with illustrative Spring Data JPA / Hibernate code:
 *     1. FetchType.LAZY vs FetchType.EAGER entities side by side (Product.category
 *        LAZY, Product.warranty EAGER) and how a LAZY field is really a Hibernate proxy
 *     2. A LazyInitializationException scenario -- accessing a lazy field AFTER the
 *        Session/transaction has already closed (commented walk-through of why)
 *     3. The N+1 query problem illustrated directly (a loop over findAll() results
 *        triggering one query per row)
 *     4. Fix #1 -- JOIN FETCH in JPQL, collapsing N+1 into a single query
 *     5. Fix #2 -- @EntityGraph, both a named graph and an ad-hoc attributePaths graph
 *
 * Covers Theory chapter:
 *     15) Hibernate and Advanced JPA/Theory/03 Lazy vs Eager Loading and the N+1 Problem.md
 *
 * IMPORTANT -- this file is illustrative, NOT a standalone runnable program.
 * It requires a real Spring Boot project on the classpath to compile/run:
 *     - spring-boot-starter-data-jpa (JPA/Hibernate, Spring Data repositories)
 *     - a JDBC driver for your target database
 *     - spring.jpa.show-sql=true (+ format_sql=true) to actually OBSERVE the query
 *       counts described in the comments below
 *
 * Run (in a real Spring Boot project, after wiring this into src/main/java/...):
 *     mvn spring-boot:run
 *   or
 *     ./gradlew bootRun
 */

import jakarta.persistence.*;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

// ---------------------------------------------------------------------------
// 1) Entities -- Category is the LAZY association target; Warranty is
//    deliberately mapped EAGER on the SAME Product entity purely to contrast
//    the two side by side. In real projects, default EVERY @ManyToOne/@OneToOne
//    to LAZY explicitly (see Theory 03's Best Practices) -- EAGER is shown here
//    only for illustration, not as a recommendation.
// ---------------------------------------------------------------------------

@Entity
@Table(name = "categories")
class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String name;

    protected Category() { }

    public Category(String name) { this.name = name; }

    public Long getId() { return id; }
    public String getName() { return name; }
}

@Entity
@Table(name = "warranties")
class Warranty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer months;

    protected Warranty() { }

    public Warranty(Integer months) { this.months = months; }

    public Long getId() { return id; }
    public Integer getMonths() { return months; }
}

// A @NamedEntityGraph declared on the entity -- reusable across MULTIPLE query
// methods (see ProductRepository.findByPriceGreaterThanEqual below), unlike a
// hand-written JOIN FETCH which only applies to the one query it's written on.
@Entity
@Table(name = "products")
@NamedEntityGraph(
    name = "Product.withCategory",
    attributeNodes = @NamedAttributeNode("category")
)
class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private BigDecimal price;

    // LAZY -- populated with a Hibernate PROXY object on load, not null and not
    // the real Category yet. The proxy knows category_id (already loaded with
    // Product's own row) but has NOT issued "SELECT * FROM categories WHERE id=?"
    // until a real field/method on it is accessed (e.g. category.getName()).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    // EAGER -- shown here ONLY to contrast with the LAZY field above. Every
    // query against Product will ALSO immediately join/fetch Warranty, whether
    // any particular caller needs it or not -- exactly the "silent, compounding
    // cost" Theory 03 warns never to reach for as a LazyInitializationException fix.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "warranty_id")
    private Warranty warranty;

    protected Product() { }

    public Product(String name, BigDecimal price, Category category, Warranty warranty) {
        this.name = name;
        this.price = price;
        this.category = category;
        this.warranty = warranty;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public BigDecimal getPrice() { return price; }
    public Category getCategory() { return category; }
    public Warranty getWarranty() { return warranty; }
}

// ---------------------------------------------------------------------------
// 2) Repository -- derived methods (N+1-prone by default), a JOIN FETCH fix,
//    and two flavors of @EntityGraph fix.
// ---------------------------------------------------------------------------

interface ProductRepository extends JpaRepository<Product, Long> {

    // Plain derived query -- returns Products with LAZY Category proxies still
    // uninitialized. Looping over the result and touching .getCategory().getName()
    // is exactly the N+1 setup demonstrated below.
    List<Product> findAllByOrderByNameAsc();

    // FIX #1 -- JOIN FETCH in JPQL. Pulls Category in the SAME query as Product
    // via a real SQL JOIN, for THIS query only -- does not change Product's
    // globally mapped fetch type.
    @Query("SELECT p FROM Product p JOIN FETCH p.category WHERE p.price >= :minPrice")
    List<Product> findExpensiveProductsWithCategory(@Param("minPrice") BigDecimal minPrice);

    // FIX #2a -- @EntityGraph referencing the @NamedEntityGraph declared on
    // Product above. Attaches to a plain DERIVED method -- no @Query needed at all.
    @EntityGraph(value = "Product.withCategory")
    List<Product> findByPriceGreaterThanEqual(BigDecimal minPrice);

    // FIX #2b -- ad-hoc entity graph, listing attribute paths directly without
    // needing a @NamedEntityGraph declared on the entity. Useful for a one-off
    // fetch shape that doesn't warrant a reusable named graph.
    @EntityGraph(attributePaths = {"category", "warranty"})
    Optional<Product> findWithDetailsById(Long id);
}

interface CategoryRepository extends JpaRepository<Category, Long> { }

// ---------------------------------------------------------------------------
// 3) Service layer -- shows the N+1 problem happening, then both fixes
//    eliminating it, plus the LazyInitializationException scenario.
// ---------------------------------------------------------------------------

@Service
class ProductReportingService {

    private final ProductRepository productRepository;

    public ProductReportingService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    // -------------------------------------------------------------------
    // THE N+1 PROBLEM, illustrated directly.
    // -------------------------------------------------------------------
    @Transactional(readOnly = true)
    public void printAllCategoryNames_NPlusOneProblem() {
        List<Product> products = productRepository.findAllByOrderByNameAsc();
        // Query #1 -- "SELECT * FROM products ORDER BY name ASC". Say this
        // returns 500 rows.

        for (Product product : products) {
            // Each iteration below triggers the LAZY Category proxy's backing
            // query the FIRST time a real field on it is touched:
            //     SELECT * FROM categories WHERE id = ?
            // For 500 products, that's up to 500 MORE queries (1 per row, many
            // likely redundant since products commonly share categories) --
            // 1 + 500 = 501 total queries instead of one well-joined query.
            System.out.println(product.getName() + " -> " + product.getCategory().getName());
        }
    }

    // -------------------------------------------------------------------
    // FIX #1 -- JOIN FETCH collapses the whole thing into ONE query total.
    // -------------------------------------------------------------------
    @Transactional(readOnly = true)
    public void printExpensiveProductCategories_JoinFetchFixed(BigDecimal minPrice) {
        List<Product> products = productRepository.findExpensiveProductsWithCategory(minPrice);
        // Single SELECT ... JOIN ... -- category data is ALREADY populated here,
        // no further queries fire in the loop below.
        for (Product product : products) {
            System.out.println(product.getName() + " -> " + product.getCategory().getName());
        }
    }

    // -------------------------------------------------------------------
    // FIX #2 -- @EntityGraph, reusable across different derived query methods
    // without hand-writing JPQL for each one.
    // -------------------------------------------------------------------
    @Transactional(readOnly = true)
    public void printProductsAboveThreshold_EntityGraphFixed(BigDecimal minPrice) {
        List<Product> products = productRepository.findByPriceGreaterThanEqual(minPrice);
        // The @NamedEntityGraph("Product.withCategory") ensures "category" is
        // eagerly fetched for THIS query -- again, no per-row follow-up query.
        for (Product product : products) {
            System.out.println(product.getName() + " -> " + product.getCategory().getName());
        }
    }

    // -------------------------------------------------------------------
    // THE FIX THAT MATTERS MOST -- fetch what you need INSIDE the
    // transactional boundary and return a DTO/plain value, so no lazy field
    // is ever touched after the Session has closed.
    // -------------------------------------------------------------------
    @Transactional(readOnly = true)
    public String getCategoryNameForProduct(Long productId) {
        Product product = productRepository.findById(productId).orElseThrow();
        // Accessed HERE, while the Session/EntityManager backing this
        // @Transactional method is still open -- perfectly safe.
        return product.getCategory().getName();
        // Session closes the moment this method returns (transaction boundary ends here).
    }

    /*
     * -------------------------------------------------------------------
     * LazyInitializationException SCENARIO -- commented out because it is
     * a deliberately BROKEN example, shown for illustration only. If this
     * method existed and were called from a layer OUTSIDE any @Transactional
     * boundary (e.g. a web controller calling a plain getter after the
     * service method above already returned), it would throw at runtime:
     *
     *     org.hibernate.LazyInitializationException:
     *     failed to lazily initialize a collection/proxy... no Session
     *
     * try {
     *     Product product = productRepository.findById(productId).orElseThrow();
     *     return product;   // returned OUTSIDE any @Transactional method -- Session closes here
     * } // ... later, in a DIFFERENT, non-transactional layer:
     * String categoryName = product.getCategory().getName();
     * // <-- BOOM: the Category proxy needs an ACTIVE Session to run its
     * // backing SELECT when triggered, but that Session has already closed.
     * // Hibernate throws instead of silently failing.
     *
     * WHY it happens mechanically: product.getCategory() returns the proxy
     * that was already sitting in the "category" field (cheap, no query --
     * the proxy already knows its ID). It is ONLY the .getName() call that
     * tries to fire the proxy's backing query, and by then there is no
     * Session left to run it through.
     *
     * The three real fixes, in order of preference (see Theory 03):
     *   1. Fetch what's needed INSIDE the transactional method (as done in
     *      getCategoryNameForProduct() above) -- the best fix.
     *   2. JOIN FETCH / @EntityGraph for that specific query (as done above).
     *   3. Widen @Transactional to cover the calling code too (Open Session
     *      In View) -- blunter, generally discouraged; most teams explicitly
     *      set spring.jpa.open-in-view=false instead of relying on this.
     *
     * NEVER "fix" this by switching Category to FetchType.EAGER -- that
     * trades a loud, visible exception for a silent, compounding N+1-style
     * cost on every query against Product, everywhere, forever.
     * -------------------------------------------------------------------
     */
}

/*
 * NOTE on annotations used above:
 * This snippet uses real JPA (jakarta.persistence.*) and Spring Data JPA
 * (org.springframework.data.jpa.repository.*) annotations exactly as they'd appear
 * in a real Spring Boot project. Observing the actual query counts described in the
 * comments requires spring.jpa.show-sql=true (and format_sql=true) in
 * application.properties, plus a populated database -- both intentionally omitted
 * here since this file is illustrative only (see header).
 */
