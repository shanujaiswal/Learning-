/**
 * 04_spring_data_jpa_demo.java
 *
 * Demonstrates, with illustrative Spring Data JPA code:
 *     1. @Entity classes (Category, Product) with @Id/@GeneratedValue/@Column
 *     2. A bidirectional @OneToMany / @ManyToOne relationship (Category <-> Product)
 *     3. A @ManyToMany relationship (Product <-> Tag) via a join table
 *     4. A Repository interface extending JpaRepository, with derived query methods
 *        AND a couple of @Query (JPQL) methods, including an @Modifying update
 *     5. A Service layer using @Transactional to group multi-step persistence
 *        operations into a single atomic unit
 *
 * Covers Theory chapter:
 *     10) Java/09) Spring and Spring Boot/Theory/04 Spring Data JPA and Databases.md
 *
 * IMPORTANT -- this file is illustrative, NOT a standalone runnable program.
 * It requires a real Spring Boot project on the classpath to compile/run, specifically:
 *     - spring-boot-starter-data-jpa   (JPA/Hibernate, Spring Data repositories)
 *     - a JDBC driver for your target database (e.g. h2 for local dev, postgresql, mysql-connector-j)
 *
 * Run (in a real Spring Boot project, after wiring this into src/main/java/... and adding
 * a @SpringBootApplication class with a main() method, plus datasource config in
 * application.properties):
 *     mvn spring-boot:run
 *   or
 *     ./gradlew bootRun
 *
 * Example application.properties for local dev against an in-memory H2 database:
 *     spring.datasource.url=jdbc:h2:mem:demo
 *     spring.jpa.hibernate.ddl-auto=update
 *     spring.jpa.show-sql=true
 */

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

// ---------------------------------------------------------------------------
// 1) Category entity -- the "one" side of a one-to-many relationship with
//    Product. mappedBy on the collection field marks this as the NON-OWNING
//    side -- no foreign key column is created on the "categories" table.
// ---------------------------------------------------------------------------

@Entity
@Table(name = "categories")
class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String name;

    // Non-owning side of the relationship -- "category" refers to the field
    // name on Product below. cascade = ALL + orphanRemoval means deleting a
    // Category (or removing a Product from this list) cascades appropriately --
    // a deliberate choice here since a Product is considered fully owned by
    // exactly one Category in this illustrative model.
    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Product> products = new ArrayList<>();

    protected Category() { }                       // required no-args constructor for JPA

    public Category(String name) {
        this.name = name;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public List<Product> getProducts() { return products; }
}

// ---------------------------------------------------------------------------
// 2) Tag entity -- the other side of a @ManyToMany with Product.
// ---------------------------------------------------------------------------

@Entity
@Table(name = "tags")
class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String label;

    // Non-owning side -- mappedBy points at Product.tags below. No @JoinTable
    // repeated here; Product already declares the join table's shape.
    @ManyToMany(mappedBy = "tags")
    private Set<Product> products = new HashSet<>();

    protected Tag() { }

    public Tag(String label) {
        this.label = label;
    }

    public Long getId() { return id; }
    public String getLabel() { return label; }
}

// ---------------------------------------------------------------------------
// 3) Product entity -- the "many" side of @ManyToOne with Category (the
//    OWNING side: JPA puts a "category_id" foreign key column directly on
//    the "products" table), and the owning side of @ManyToMany with Tag
//    (owns the "product_tags" join table).
// ---------------------------------------------------------------------------

@Entity
@Table(name = "products")
class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_name", nullable = false, length = 100)
    @NotBlank
    private String name;

    @Column(nullable = false)
    private BigDecimal price;

    // Owning side of @ManyToOne -- fetch = LAZY overrides JPA's EAGER default,
    // which real projects should always do explicitly (see Theory file's
    // "Fetch Types" section for why).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    // Owning side of @ManyToMany -- this @JoinTable declaration is what
    // actually creates the "product_tags" join table with both foreign keys.
    @ManyToMany
    @JoinTable(
        name = "product_tags",
        joinColumns = @JoinColumn(name = "product_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new HashSet<>();

    protected Product() { }                        // required no-args constructor for JPA

    public Product(String name, BigDecimal price, Category category) {
        this.name = name;
        this.price = price;
        this.category = category;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public Set<Tag> getTags() { return tags; }
}

// ---------------------------------------------------------------------------
// 4) Repository interfaces -- no implementation written by hand. Spring Data
//    JPA generates a proxy implementation at startup from just the method
//    signatures/annotations declared here.
// ---------------------------------------------------------------------------

interface CategoryRepository extends JpaRepository<Category, Long> {
    Optional<Category> findByName(String name);
}

interface ProductRepository extends JpaRepository<Product, Long> {

    // Derived query -- Spring Data parses the method name into JPQL automatically.
    // SELECT p FROM Product p WHERE p.category.name = ?1 ORDER BY p.price DESC
    List<Product> findByCategoryNameOrderByPriceDesc(String categoryName);

    // Derived query returning a boolean -- SELECT COUNT(p) > 0 FROM Product p WHERE p.name = ?1
    boolean existsByName(String name);

    // JPQL via @Query with named parameters -- used once the equivalent derived
    // method name would become unwieldy, or when a JOIN FETCH is needed to
    // avoid the N+1 query problem (eagerly pulling "category" for THIS query only).
    @Query("SELECT p FROM Product p JOIN FETCH p.category WHERE p.price >= :minPrice")
    List<Product> findExpensiveProductsWithCategory(@Param("minPrice") BigDecimal minPrice);

    // @Modifying is REQUIRED alongside @Query for an UPDATE/DELETE JPQL statement --
    // without it Spring Data assumes every @Query is a SELECT and throws at runtime.
    // clearAutomatically = true clears the persistence context afterward so any
    // already-loaded Product objects don't silently show stale in-memory prices.
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Product p SET p.price = p.price * :multiplier WHERE p.category.id = :categoryId")
    int bulkAdjustPriceForCategory(@Param("categoryId") Long categoryId,
                                    @Param("multiplier") BigDecimal multiplier);
}

interface TagRepository extends JpaRepository<Tag, Long> {
    Optional<Tag> findByLabel(String label);
}

// ---------------------------------------------------------------------------
// 5) Service layer -- business operations that touch MULTIPLE repository
//    calls are wrapped in @Transactional so they commit or roll back as one
//    atomic unit. Placed on the Service layer (not the Repository or
//    Controller layer) because a service method represents one complete
//    business operation -- the right unit of atomicity.
// ---------------------------------------------------------------------------

@Service
class ProductCatalogService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;

    // Constructor injection -- Spring wires all three repository beans in automatically.
    public ProductCatalogService(ProductRepository productRepository,
                                  CategoryRepository categoryRepository,
                                  TagRepository tagRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
    }

    // Multi-step write: looks up (or creates) a Category, creates a Product
    // under it, and attaches Tags -- all inside ONE transaction. If anything
    // after the first save() throws, the whole operation rolls back, so we
    // never end up with a half-created Product with no tags because of an
    // error partway through.
    @Transactional
    public Product createProductWithTags(String productName, BigDecimal price,
                                          String categoryName, Set<String> tagLabels) {

        Category category = categoryRepository.findByName(categoryName)
                .orElseGet(() -> categoryRepository.save(new Category(categoryName)));

        if (productRepository.existsByName(productName)) {
            throw new IllegalStateException("Product already exists: " + productName);
        }

        Product product = new Product(productName, price, category);

        for (String label : tagLabels) {
            Tag tag = tagRepository.findByLabel(label)
                    .orElseGet(() -> tagRepository.save(new Tag(label)));
            product.getTags().add(tag);
        }

        return productRepository.save(product);
    }

    // Pure read -- readOnly = true lets Hibernate skip dirty-checking flushes,
    // a small but free optimization for query-only service methods.
    @Transactional(readOnly = true)
    public List<Product> findExpensiveProducts(BigDecimal minPrice) {
        return productRepository.findExpensiveProductsWithCategory(minPrice);
    }

    // Bulk update via the @Modifying JPQL query above -- also wrapped in a
    // transaction, and rollbackFor is widened here to demonstrate forcing a
    // rollback on a checked exception too (Spring's default only rolls back
    // on unchecked exceptions).
    @Transactional(rollbackFor = Exception.class)
    public int applyDiscountToCategory(Long categoryId, BigDecimal multiplier) throws Exception {
        if (multiplier.compareTo(BigDecimal.ZERO) < 0) {
            throw new Exception("Multiplier cannot be negative");   // checked exception -- still rolls back here
        }
        return productRepository.bulkAdjustPriceForCategory(categoryId, multiplier);
    }
}

/*
 * NOTE on annotations used above:
 * This snippet uses real JPA (jakarta.persistence.*) and Spring Data JPA
 * (org.springframework.data.jpa.repository.*) annotations exactly as they'd
 * appear in a real Spring Boot project, which requires component scanning,
 * a configured DataSource, and a @SpringBootApplication entry point to
 * actually create the schema and wire these beans together. Since this file
 * is illustrative only (see header), that bootstrap class and the
 * application.properties datasource config are intentionally omitted --
 * drop these classes into a real Spring Boot project's source tree, add a
 * JDBC driver, and configure a datasource to see it run end-to-end.
 */
