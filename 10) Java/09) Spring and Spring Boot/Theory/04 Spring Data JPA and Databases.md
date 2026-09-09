# Why Spring Data JPA Exists -- The Problem It Solves

--> Talking to a relational database from plain Java means either hand-writing JDBC (`Connection`, `PreparedStatement`, `ResultSet`, manual column-to-field mapping, manual connection pooling) or writing an ORM's boilerplate by hand (mapping classes, session management, query objects). **JPA (Jakarta Persistence API)** is a SPECIFICATION -- a set of interfaces and annotations describing how Java objects map to database tables -- and **Hibernate** is the most common IMPLEMENTATION of that specification that Spring Boot wires in by default via `spring-boot-starter-data-jpa`.
--> **Spring Data JPA** sits one layer above raw JPA/Hibernate -- it generates REPOSITORY implementations for you at runtime from an INTERFACE you declare, so you write zero SQL and zero boilerplate CRUD code for the common cases. You still get full JPA underneath (entities, the persistence context, JPQL) whenever you need more control.
--> **The layering, end to end:**

```text
Your code            -->  ProductRepository extends JpaRepository<Product, Long>
Spring Data JPA       -->  generates a proxy implementation of that interface at startup
JPA (specification)   -->  EntityManager, @Entity, @Id, JPQL -- the standard contract
Hibernate (impl)       -->  translates JPQL/criteria into real SQL, manages the persistence context
JDBC                  -->  the actual wire protocol talking to the database driver
Database (MySQL/Postgres/H2/...)
```

--> **Starter dependency** -- `spring-boot-starter-data-jpa` pulls in Hibernate, Spring Data JPA, and the JPA API itself. You additionally need a JDBC driver for whichever database you're targeting (`h2` for an in-memory dev database, `postgresql`, `mysql-connector-j`, etc.) and typically configure the connection in `application.properties`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/mydb
spring.datasource.username=app_user
spring.datasource.password=secret
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
```

--> **`spring.jpa.hibernate.ddl-auto`** controls whether Hibernate manages your schema automatically -- `none` (do nothing, you manage schema yourself/via migrations), `validate` (check entities match existing schema, fail if not), `update` (add missing tables/columns, never removes anything -- convenient in dev, risky in prod), `create`/`create-drop` (wipe and recreate on every startup -- tests and throwaway demos only). **Production systems almost universally use `validate` or `none`, with real schema migrations handled by Flyway or Liquibase** -- letting Hibernate auto-alter a live production schema is a well-known source of data-loss incidents.

# @Entity, @Id, @GeneratedValue, @Column -- Mapping a Class to a Table

--> **`@Entity`** marks a plain Java class as something JPA should persist -- Hibernate creates (or expects) a corresponding database table, by default named after the class (configurable). An entity class has a few hard requirements: a no-args constructor (JPA instantiates it via reflection), a field marked `@Id`, and fields/getters that aren't `final` (JPA needs to set values after construction).
--> **`@Id`** marks the PRIMARY KEY field. **`@GeneratedValue`** tells JPA/the database how to auto-generate that key's value rather than the application assigning it manually.

```java
@Entity
@Table(name = "products")                 // optional -- omit to default to the class name ("Product" -> "product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)   // DB auto-increment column (MySQL AUTO_INCREMENT, Postgres SERIAL/IDENTITY)
    private Long id;

    @Column(name = "product_name", nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private BigDecimal price;

    private String category;                              // no @Column needed -- defaults: column name = field name, nullable = true

    protected Product() { }                                 // no-args constructor required by JPA

    public Product(String name, BigDecimal price, String category) {
        this.name = name;
        this.price = price;
        this.category = category;
    }

    // getters + setters omitted for brevity
}
```

--> **`GenerationType` strategies:**

| Strategy | How it works | Typical fit |
|---|---|---|
| `IDENTITY` | Delegates to the database's auto-increment column | MySQL, simplest, but disables JDBC batch inserts in older Hibernate versions |
| `SEQUENCE` | Uses a database SEQUENCE object, fetched (often in batches) before insert | PostgreSQL, Oracle -- plays nicely with batching |
| `TABLE` | Simulates a sequence with a separate table row | Portable across any database, but slowest -- rarely used today |
| `AUTO` | Lets Hibernate pick based on the underlying database dialect | Fine for prototypes; be explicit in real projects |

--> **`@Column` attributes worth knowing** -- `name` (override the derived column name), `nullable` (adds a `NOT NULL` constraint when Hibernate generates DDL), `length` (`VARCHAR(n)`, default 255), `unique` (adds a unique constraint), `updatable`/`insertable` (exclude a field from UPDATE/INSERT statements -- useful for a `createdAt` you only ever want set once).
--> **Deep Dive -- `@Column` is metadata for DDL generation AND query building, not validation** -- `nullable = false` makes Hibernate emit `NOT NULL` when it generates the table, and it participates in the JPQL Hibernate builds, but it does NOT stop your Java code from trying to `save()` a null value -- that still fails at the DATABASE constraint level (a `DataIntegrityViolationException`), not gracefully in Java. Bean Validation (`@NotNull` from `jakarta.validation.constraints`) is the tool for validating BEFORE that point, and the two annotation sets are commonly combined on the same field.

```text
@Id               -- marks the primary key field
@GeneratedValue   -- how the primary key value is produced (IDENTITY / SEQUENCE / TABLE / AUTO)
@Column           -- customizes the mapped column (name, nullable, length, unique)
@Table            -- customizes the mapped table (name, schema, uniqueConstraints)
@Transient        -- excludes a field from persistence entirely (computed/derived fields)
@Enumerated       -- maps a Java enum field (STRING stores the enum name, ORDINAL stores its index -- always prefer STRING, ORDINAL breaks silently if enum order ever changes)
@Temporal         -- (legacy java.util.Date/Calendar only) maps DATE/TIME/TIMESTAMP granularity; not needed for java.time types, which map automatically
```

# Repository Interfaces -- CrudRepository, JpaRepository, and Derived Queries

--> You don't write a repository IMPLEMENTATION at all for the common case -- you declare an INTERFACE extending one of Spring Data's base interfaces, and Spring generates a working implementation (a JDK dynamic proxy) at application startup, wired in as a bean ready to `@Autowired`/constructor-inject wherever needed.

```java
public interface ProductRepository extends JpaRepository<Product, Long> {
    // Long here is the type of the @Id field -- Product's primary key type.
}
```

--> **The repository interface hierarchy** (each extends the one before it, adding capability):

| Interface | Adds | Notable methods |
|---|---|---|
| `Repository<T, ID>` | Marker interface, no methods | -- (rarely extended directly) |
| `CrudRepository<T, ID>` | Basic CRUD | `save`, `saveAll`, `findById`, `findAll`, `existsById`, `deleteById`, `delete`, `count` |
| `PagingAndSortingRepository<T, ID>` | Pagination + sorting | `findAll(Pageable)`, `findAll(Sort)` |
| `JpaRepository<T, ID>` | JPA-specific extras | `flush`, `saveAndFlush`, `deleteAllInBatch`, batch-friendly `findAll()` returning `List` instead of `Iterable` |

--> **In practice, almost every project extends `JpaRepository`** -- it includes everything `CrudRepository` and `PagingAndSortingRepository` offer plus JPA-specific conveniences, and the marginal cost of the extra methods it exposes is essentially zero.
--> **Derived query methods** -- Spring Data JPA parses the METHOD NAME itself and generates the corresponding query, no implementation or annotation required, as long as the name follows its keyword convention:

```java
public interface ProductRepository extends JpaRepository<Product, Long> {

    // SELECT p FROM Product p WHERE p.name = ?1
    Optional<Product> findByName(String name);

    // SELECT p FROM Product p WHERE p.category = ?1 ORDER BY p.price DESC
    List<Product> findByCategoryOrderByPriceDesc(String category);

    // SELECT p FROM Product p WHERE p.price BETWEEN ?1 AND ?2
    List<Product> findByPriceBetween(BigDecimal min, BigDecimal max);

    // SELECT COUNT(p) > 0 FROM Product p WHERE p.name = ?1  (returns boolean, not the entity)
    boolean existsByName(String name);

    // DELETE FROM Product p WHERE p.category = ?1
    long deleteByCategory(String category);

    // SELECT p FROM Product p WHERE p.name LIKE %?1%  (contains, case-sensitive)
    List<Product> findByNameContaining(String fragment);

    // Case-insensitive variant
    List<Product> findByNameContainingIgnoreCase(String fragment);
}
```

--> **Common derived-query keywords** -- `findBy`, `existsBy`, `countBy`, `deleteBy`/`removeBy` as the prefix; `And`/`Or` to combine conditions; `OrderBy...Asc`/`Desc` for sorting; `GreaterThan`/`LessThan`/`Between`/`Like`/`Containing`/`StartingWith`/`EndingWith`/`IgnoreCase`/`In`/`IsNull`/`IsNotNull` as condition modifiers; a leading `First`/`Top` + a number (e.g. `findTop5ByOrderByPriceDesc`) to limit results.
--> **When derived query names get too long/unreadable** (a name like `findByCategoryAndPriceGreaterThanAndNameContainingIgnoreCase` is technically valid but painful), switch to `@Query` (next section) instead of fighting the naming convention further.

# JPQL Basics -- @Query and Named Parameters

--> **JPQL (Jakarta Persistence Query Language)** looks like SQL but queries JAVA ENTITY names and FIELD names, not table/column names -- Hibernate translates it into real SQL for the underlying database at execution time. This is what keeps JPQL portable across different database vendors, unlike hand-written native SQL which can rely on vendor-specific syntax.

```java
public interface ProductRepository extends JpaRepository<Product, Long> {

    // JPQL -- "Product" and "p.category" refer to the ENTITY CLASS and its FIELD, not the table/column.
    @Query("SELECT p FROM Product p WHERE p.category = :category AND p.price <= :maxPrice")
    List<Product> searchByCategoryAndMaxPrice(
            @Param("category") String category,
            @Param("maxPrice") BigDecimal maxPrice);

    // Positional parameters (?1, ?2, ...) work too, but named parameters (:name) are far more readable
    // and don't break if you reorder the method's parameter list.
    @Query("SELECT p FROM Product p WHERE p.name = ?1")
    Optional<Product> findExactName(String name);

    // Native SQL escape hatch -- nativeQuery = true switches JPQL off entirely; here you DO use
    // real table/column names, and you give up database portability in exchange for full SQL power
    // (vendor-specific functions, complex joins/window functions JPQL can't express).
    @Query(value = "SELECT * FROM products WHERE price > :minPrice ORDER BY created_at DESC LIMIT 10",
           nativeQuery = true)
    List<Product> findTopExpensiveNative(@Param("minPrice") BigDecimal minPrice);

    // @Modifying is REQUIRED alongside @Query for UPDATE/DELETE JPQL -- without it Spring Data
    // assumes every @Query is a SELECT and throws at runtime.
    @Modifying
    @Query("UPDATE Product p SET p.price = p.price * :multiplier WHERE p.category = :category")
    int bulkAdjustPrice(@Param("category") String category, @Param("multiplier") BigDecimal multiplier);
}
```

--> **`@Modifying` gotchas** -- an `UPDATE`/`DELETE` JPQL query bypasses the persistence context entirely (it goes straight to the database), meaning any already-loaded entity in memory does NOT automatically reflect the change unless you also set `@Modifying(clearAutomatically = true)` to clear the persistence context afterward, or refetch. This is a common source of "I updated it but my in-memory object still shows the old value" confusion.
--> **JPQL vs native SQL, when to reach for which** -- prefer JPQL (or derived query methods) by default for portability and because it operates on entity graphs (it understands `@ManyToOne`/`@OneToMany` navigation via dot-path, e.g. `p.category.name`). Drop to native SQL only when you need a database-specific feature JPQL genuinely cannot express (window functions, full-text search operators, vendor hints).

# Relationships -- @OneToMany, @ManyToOne, @ManyToMany, @OneToOne

--> Relational databases model relationships with FOREIGN KEYS; JPA models them as OBJECT REFERENCES between entities, annotated to describe cardinality and ownership. Getting the OWNING SIDE right is the single most important concept here.

## @ManyToOne / @OneToMany (the most common pairing)

--> Many `Product` rows can belong to one `Category` row -- this is a classic many-to-one / one-to-many pair, always modeled TOGETHER as two sides of the same relationship.

```java
@Entity
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    // The "one" side -- mappedBy points to the field name on Product that owns the relationship.
    // mappedBy means "I am NOT the owning side -- don't create a foreign key column here."
    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Product> products = new ArrayList<>();
}

@Entity
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    // The "many" side -- THIS is the owning side: JPA puts a "category_id" foreign key
    // column directly on the products table. FetchType.LAZY is the correct default for
    // @ManyToOne in real projects (see Fetch Types below).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;
}
```

--> **`mappedBy` is the key thing to internalize** -- exactly ONE side of a bidirectional relationship owns the foreign key column, and that owning side is the one WITHOUT `mappedBy`. The non-owning side declares `mappedBy = "<field name on the owning entity>"`, purely for Java's convenience of navigating the relationship in both directions -- it creates no additional column or table by itself.
--> **`cascade`** controls whether operations on the parent (`Category`) automatically propagate to children (`Product`) -- `CascadeType.ALL` means saving/deleting a `Category` also saves/deletes its `Product`s. Common values: `PERSIST`, `MERGE`, `REMOVE`, `REFRESH`, `DETACH`, or `ALL` for all of them. **Be deliberate here** -- cascading `REMOVE` from a "category" onto its "products" might be exactly what you want, or it might silently delete inventory nobody intended to delete.
--> **`orphanRemoval = true`** -- if a `Product` is removed from the `category.getProducts()` list (even without an explicit delete call), Hibernate deletes that `Product` row entirely on the next flush. Only meaningful on the "one" side of a `@OneToMany`, and only for genuinely OWNED child entities (a product truly belongs to one category and has no independent existence) -- inappropriate when the child could reasonably be reassigned or exist standalone.

## @ManyToMany

--> Many `Product`s can each have many `Tag`s, and vice versa -- requires a JOIN TABLE in the database (no foreign key fits on either side alone).

```java
@Entity
public class Product {
    // ...
    @ManyToMany
    @JoinTable(
        name = "product_tags",                                    // the join table's name
        joinColumns = @JoinColumn(name = "product_id"),           // FK column pointing back to Product
        inverseJoinColumns = @JoinColumn(name = "tag_id"))        // FK column pointing to Tag
    private Set<Tag> tags = new HashSet<>();
}

@Entity
public class Tag {
    // ...
    @ManyToMany(mappedBy = "tags")            // non-owning side -- no @JoinTable repeated here
    private Set<Product> products = new HashSet<>();
}
```

--> **Prefer `Set` over `List` for `@ManyToMany`/`@OneToMany` collections** -- Hibernate's handling of `List` duplicates/reordering across a join table can trigger unnecessary DELETE+INSERT churn; `Set` semantics match "a collection of distinct related entities" more naturally for most relationship modeling.
--> **When the join table needs its OWN extra columns** (e.g. `product_tags` also needs a `tagged_at` timestamp), `@ManyToMany` can no longer express it -- model the join table as its OWN `@Entity` with two `@ManyToOne` relationships instead (effectively two `@OneToMany`/`@ManyToOne` pairs sandwiching a real entity).

## @OneToOne

--> One `User` has exactly one `UserProfile` -- less common than the other two, but shows up for "extension table" patterns (splitting a wide table into a core table + an optional/rarely-accessed detail table).

```java
@Entity
public class User {
    @Id @GeneratedValue private Long id;
    private String username;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
    private UserProfile profile;
}

@Entity
public class UserProfile {
    @Id @GeneratedValue private Long id;
    private String bio;

    @OneToOne
    @JoinColumn(name = "user_id", unique = true)   // owning side -- unique enforces true one-to-one, not one-to-many
    private User user;
}
```

--> **Gotcha -- forgetting `unique = true` on the owning `@JoinColumn`** silently allows the database to accept what is really a one-to-MANY relationship (multiple `UserProfile` rows pointing at the same `user_id`), even though your Java code and mental model assume strict one-to-one.

## Fetch Types -- LAZY vs EAGER

| Relationship | JPA spec default | Recommended in practice |
|---|---|---|
| `@ManyToOne` | `EAGER` | **Override to `LAZY`** |
| `@OneToOne` | `EAGER` | **Override to `LAZY`** |
| `@OneToMany` | `LAZY` | Leave as `LAZY` |
| `@ManyToMany` | `LAZY` | Leave as `LAZY` |

--> **Almost every real project overrides `@ManyToOne`/`@OneToOne` to `LAZY` explicitly** -- the JPA spec's `EAGER` defaults for these two are widely considered a historical mistake, because eagerly loading a related entity on every single query (even ones that never touch it) is a silent, easy-to-miss performance cost that compounds as the entity graph grows. Always write `fetch = FetchType.LAZY` explicitly rather than relying on defaults.
--> **`LazyInitializationException`** -- accessing a lazy relationship AFTER the persistence context/session that loaded the parent entity has closed (e.g. in the web layer, after the `@Transactional` service method has already returned) throws this. Fixes: fetch what you need inside the transactional method (map to a DTO there, before returning), use a JOIN FETCH JPQL query to eagerly pull the association for that specific query only, or (as a last, blunter resort) `@Transactional` scoped wider -- never solved correctly by just switching to `EAGER` everywhere, which just trades one performance problem for another (see N+1 below).
--> **The N+1 query problem** -- fetching a list of N `Product`s, then lazily accessing `product.getCategory()` inside a loop, fires 1 query for the products plus N additional queries (one per product) instead of one combined query -- a classic, easy-to-miss performance bug. Fixed with a JPQL `JOIN FETCH`:

```java
@Query("SELECT p FROM Product p JOIN FETCH p.category WHERE p.price > :minPrice")
List<Product> findExpensiveProductsWithCategory(@Param("minPrice") BigDecimal minPrice);
```

# Transactions -- @Transactional

--> A **transaction** groups multiple database operations into one ALL-OR-NOTHING unit -- either every operation succeeds and is COMMITTED, or if anything fails, everything is ROLLED BACK as if none of it happened. This matters the moment a single business operation touches more than one row/table (e.g. "transfer funds" = debit one account AND credit another -- both must succeed together or neither should apply).
--> **`@Transactional`** (from `org.springframework.transaction.annotation.Transactional`) tells Spring to wrap a method call in a transaction automatically -- no manual `begin()`/`commit()`/`rollback()` calls. It's almost always placed on **Service layer methods**, not repository or controller methods -- the service method typically represents one complete business operation, which is the right unit of atomicity.

```java
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final InventoryRepository inventoryRepository;

    public OrderService(OrderRepository orderRepository, InventoryRepository inventoryRepository) {
        this.orderRepository = orderRepository;
        this.inventoryRepository = inventoryRepository;
    }

    @Transactional
    public Order placeOrder(OrderRequest request) {
        Inventory inventory = inventoryRepository.findByProductId(request.getProductId())
                .orElseThrow(() -> new ProductNotFoundException(request.getProductId()));

        if (inventory.getQuantity() < request.getQuantity()) {
            throw new InsufficientStockException(request.getProductId());   // triggers a full rollback
        }

        inventory.setQuantity(inventory.getQuantity() - request.getQuantity());
        inventoryRepository.save(inventory);                                 // step 1

        Order order = new Order(request.getProductId(), request.getQuantity());
        return orderRepository.save(order);                                  // step 2 -- both commit together, or neither does
    }
}
```

--> **Default rollback behavior -- the single most common `@Transactional` gotcha** -- by default, Spring only rolls back on UNCHECKED exceptions (`RuntimeException` and its subclasses, plus `Error`). A CHECKED exception (one extending `Exception` directly, not `RuntimeException`) does NOT trigger a rollback unless you say so explicitly:

```java
@Transactional(rollbackFor = Exception.class)     // force rollback on checked exceptions too
public void riskyOperation() throws SomeCheckedException { ... }
```

--> **`readOnly = true`** -- for methods that only read data, `@Transactional(readOnly = true)` lets Hibernate apply optimizations (skipping dirty-checking flushes) and can hint the database driver toward a read replica in some setups. A good habit on every pure-query service method, even though correctness doesn't strictly require it.
--> **Propagation** -- controls how a `@Transactional` method behaves when called from ANOTHER method that's already inside a transaction. `REQUIRED` (the default) joins the existing transaction if one is active, or starts a new one if not. `REQUIRES_NEW` always suspends any existing transaction and starts a fresh, independent one (useful for "always log this audit record even if the outer operation rolls back"). Other values (`NESTED`, `MANDATORY`, `SUPPORTS`, `NOT_SUPPORTED`, `NEVER`) exist but are rarely needed outside specific edge cases.
--> **Deep Dive -- why `@Transactional` on a `private` method or a self-invoked method silently does nothing** -- Spring implements `@Transactional` via a PROXY wrapping the bean (by default, a JDK dynamic proxy or CGLIB subclass). The proxy intercepts calls arriving FROM OUTSIDE the bean. A method calling another method on `this` (self-invocation, e.g. `this.otherTransactionalMethod()`) bypasses the proxy entirely and goes straight to the real object -- the transactional behavior is silently skipped. This is one of the most common "why isn't my `@Transactional` doing anything" bugs in real Spring codebases. Fix: move the transactional method to a different bean and call it through THAT bean's injected reference, so the call genuinely passes through the proxy.
--> **`@Transactional` also can't be private** -- Spring's proxy-based AOP needs to override the method, so it must be `public` (or at least non-private and non-final on CGLIB proxies) for the annotation to take effect at all.

# Common Gotchas

--> **Forgetting a no-args constructor on an `@Entity`** -- JPA instantiates entities via reflection and requires one (can be `protected`, doesn't need to be `public`), even if you also define a convenience all-args constructor for your own code to use.
--> **`ddl-auto=update` (or worse, `create`) left on in production** -- fine for local dev against a throwaway database, dangerous against a real one; use proper migrations (Flyway/Liquibase) with `validate` or `none` once there's real data to protect.
--> **Leaving `@ManyToOne`/`@OneToOne` at their `EAGER` spec default** -- always write `fetch = FetchType.LAZY` explicitly rather than relying on JPA's historical (arguably mistaken) defaults.
--> **Accessing a lazy relationship outside an active transaction/session** -- `LazyInitializationException` at the web layer; fetch what's needed inside the `@Transactional` service method, or use `JOIN FETCH`.
--> **The N+1 query problem from looping over lazy associations** -- one query becomes N+1 queries; fix with `JOIN FETCH` or a purpose-built query, not by switching everything to `EAGER`.
--> **Calling a `@Transactional` method via `this.` from within the same bean** -- the proxy is bypassed, no transaction actually starts; the fix is calling through another bean, not adding more annotations to the same method.
--> **Assuming `@Transactional` rolls back on ANY exception** -- it only rolls back on unchecked exceptions by default; use `rollbackFor` for checked exceptions that should also roll back.
--> **`@ManyToMany` with a `List` instead of a `Set`**, and needing extra columns on a join table -- both push you toward either switching to `Set` or promoting the join table to its own `@Entity`.

# Best Practices Summary

--> Extend `JpaRepository` for new repositories by default -- it's a strict superset of `CrudRepository`/`PagingAndSortingRepository` with no real downside.
--> Prefer derived query methods for simple lookups; switch to `@Query`/JPQL once the derived method name becomes unreadable; reach for native SQL only for genuinely vendor-specific needs.
--> Always specify `fetch = FetchType.LAZY` explicitly on `@ManyToOne`/`@OneToOne` -- don't rely on JPA's `EAGER` defaults for those two.
--> Put `@Transactional` on Service-layer methods representing one whole business operation -- not on repository methods (already transactional per-call) or controller methods (wrong layer for a business-operation boundary).
--> Use `@Transactional(readOnly = true)` on pure-query service methods, and `rollbackFor = Exception.class` wherever a checked exception should also trigger a rollback.
--> Never call a `@Transactional` method via `this.` from inside the same class and expect the transaction to apply -- route the call through another bean.
--> Use real schema migrations (Flyway/Liquibase) with `ddl-auto=validate`/`none` once a project has real data to protect -- reserve `update`/`create-drop` for local dev and tests.
--> Map entity relationships deliberately -- know which side owns the foreign key (`mappedBy` marks the non-owning side), and be careful with `cascade`/`orphanRemoval` on anything that isn't a genuinely owned child.
