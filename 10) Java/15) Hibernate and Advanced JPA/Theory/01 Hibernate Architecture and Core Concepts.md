# Hibernate vs Plain JDBC -- Why an ORM Exists At All

--> Plain **JDBC** means you hand-write every `Connection`, `PreparedStatement`, `ResultSet`, manually map each column to a Java field, manually manage connection pooling/lifecycles, and manually track which objects are "dirty" (changed) so you know what to `UPDATE`. This is not just verbose -- it is a maintenance liability: every new field means touching N different SQL strings and N different mapping blocks scattered across the codebase.
--> **Hibernate** is an ORM (Object-Relational Mapping) framework -- and specifically, the most widely used IMPLEMENTATION of the JPA (Jakarta Persistence API) specification. It sits between your Java objects and the database, translating object graph navigation and object state changes into SQL automatically.
--> **The relationship between JPA and Hibernate, precisely stated:**

```text
JPA         -->  a SPECIFICATION (interfaces: EntityManager, EntityManagerFactory, annotations: @Entity, @Id...)
Hibernate   -->  an IMPLEMENTATION of that specification, PLUS its own native API and extra features JPA doesn't define
```

--> You can code purely against `jakarta.persistence.*` interfaces and stay 100% portable to any JPA provider (Hibernate, EclipseLink, OpenJPA) -- or you can drop down to Hibernate's own native API (`org.hibernate.Session`, `org.hibernate.SessionFactory`, `org.hibernate.Criteria`) for Hibernate-specific features that go beyond the JPA spec (e.g. `@NaturalId`, Hibernate-specific caching regions, `@DynamicUpdate`, Hibernate's own `StatelessSession`). Most real projects rely on JPA annotations by default and reach for Hibernate-native APIs only when a specific feature genuinely requires it.
--> **What Hibernate does for you, concretely:**

| Manual JDBC responsibility | What Hibernate automates |
|---|---|
| Writing `INSERT`/`UPDATE`/`DELETE`/`SELECT` SQL by hand | Generates SQL from your entity mappings and query language (HQL/JPQL) |
| Mapping `ResultSet` columns to object fields manually | Automatic mapping via annotations (`@Column`, `@Id`, etc.) |
| Tracking which loaded objects changed since load time | Automatic "dirty checking" -- compares in-memory state to a snapshot at flush time |
| Managing a connection pool by hand | Delegated to a `DataSource`/connection pool (HikariCP, etc.), Hibernate just borrows/returns connections |
| Caching query results across repeated business operations | First-level cache (always on, per-Session) + optional second-level cache (see Theory 04) |
| Writing joins for object graph navigation (`order.getCustomer().getName()`) | Lazy/eager loading transparently issues the right SQL when you navigate the object graph |

--> **The cost of that convenience** -- Hibernate hides a LOT of SQL generation behind object navigation, which is exactly why understanding what's happening underneath (this whole chapter) matters: silent N+1 queries, unexpected `UPDATE` statements from dirty checking, and `LazyInitializationException` are all consequences of NOT understanding the machinery Hibernate is running for you.

# The Hibernate Architecture -- SessionFactory, Session, and the Persistence Context

--> Hibernate's runtime architecture centers on three layered concepts, each with a distinct lifetime and responsibility.

```text
Configuration               -->  reads hibernate.cfg.xml/properties, entity mappings -- built ONCE at startup
        |
        v
SessionFactory              -->  heavyweight, thread-safe, ONE per database -- built ONCE, held for the app's lifetime
        |
        v
Session (== EntityManager)  -->  lightweight, NOT thread-safe -- one per unit-of-work (per request/transaction)
        |
        v
Persistence Context         -->  the Session's in-memory "identity map" of managed entities
```

--> **`SessionFactory`** (Hibernate-native) is equivalent to JPA's `EntityManagerFactory` -- it is built ONCE per database at application startup (expensive to create: it parses all entity mappings, builds SQL generation metadata, initializes connection pool settings), and then held and reused for the entire application lifetime. Creating a new one per request would be a severe performance mistake. It IS thread-safe -- many threads share the same `SessionFactory` instance safely.
--> **`Session`** (Hibernate-native) is equivalent to JPA's `EntityManager` -- lightweight, cheap to create, and explicitly NOT thread-safe. The standard pattern is one `Session`/`EntityManager` per unit of work (typically: per HTTP request, or per `@Transactional` method in a Spring app, where Spring manages this for you transparently via `EntityManager` injection/proxies).
--> **The Persistence Context** is the Session's internal bookkeeping structure -- an "identity map" tracking every entity instance the Session has loaded, created, or is otherwise managing during its lifetime. This is the mechanism underlying the first-level cache (below) and dirty checking.
--> **In a Spring Boot / Spring Data JPA app you rarely touch `SessionFactory`/`Session` directly** -- Spring wraps this machinery: `EntityManager` is injected and its lifecycle tied to `@Transactional` boundaries automatically. Understanding the underlying `SessionFactory`/`Session` model still matters because every JPA `EntityManager` call you make (or Spring Data repository call under the hood) is delegating to exactly this machinery.

```java
// Raw Hibernate-native bootstrap (rarely written by hand in a Spring app --
// Spring Boot auto-configures the equivalent EntityManagerFactory for you --
// shown here purely to make the architecture concrete)
Configuration configuration = new Configuration().configure();     // reads hibernate.cfg.xml
SessionFactory sessionFactory = configuration.buildSessionFactory(); // ONCE, held for app lifetime

Session session = sessionFactory.openSession();                    // per unit of work
Transaction tx = session.beginTransaction();
try {
    Product product = session.get(Product.class, 1L);              // enters the persistence context
    product.setPrice(new BigDecimal("29.99"));                     // dirty-checked automatically
    tx.commit();                                                    // flush + commit -- UPDATE issued here
} catch (Exception e) {
    tx.rollback();
    throw e;
} finally {
    session.close();                                                // release the Session -- NOT the SessionFactory
}
```

# Entity States -- Transient, Managed (Persistent), Detached, Removed

--> Every entity instance is, at any moment, in exactly one of four lifecycle states relative to a given Session/persistence context. Understanding these states explains almost every "why didn't my change get saved" or "why did I get a duplicate row" surprise.

| State | Meaning | How you get there | How you leave |
|---|---|---|---|
| **Transient** | A plain `new SomeEntity()` -- Hibernate has never heard of it, no DB row exists yet | Just instantiate it with `new` | `persist()`/`save()` moves it to Managed |
| **Managed (Persistent)** | Tracked by the current persistence context -- changes are auto-detected (dirty checking) and flushed | `persist()`, `merge()`, or loaded via `find()`/a query | `flush()`+`commit()` keeps it Managed; `detach()`/session close moves it to Detached; `remove()` moves it to Removed |
| **Detached** | Was Managed once, but its Session has since closed (or it was explicitly detached) -- the object still holds data in memory, but Hibernate is no longer tracking changes to it | Session/transaction ends, or explicit `detach()` | `merge()` re-attaches it (as a NEW managed copy) to a fresh Session |
| **Removed** | Marked for deletion -- still in the persistence context until flush, then the row is deleted | `remove()` on a Managed entity | Flush issues the `DELETE`; after that, effectively gone |

--> **`persist()` vs `merge()` -- the single most misunderstood pair in JPA:**

```java
// persist() -- for a TRANSIENT (brand new) entity. Enters the persistence context directly.
// Calling persist() on an already-Managed entity is a harmless no-op.
// Calling persist() on a DETACHED entity throws (in strict JPA) or is undefined behavior.
Product p = new Product("Widget", price);
entityManager.persist(p);       // p is now Managed -- same object reference, same identity

// merge() -- for a DETACHED entity (e.g. one that came from a previous request, or was
// deserialized). Returns a DIFFERENT, newly-Managed object -- the original detached
// instance you passed in is NOT the one being tracked.
Product detachedProduct = ...;                       // e.g. came in from a web form, has an id
Product managedCopy = entityManager.merge(detachedProduct);   // <-- use THIS reference going forward
managedCopy.setPrice(newPrice);                       // correct -- managedCopy is tracked
detachedProduct.setPrice(newPrice);                   // WRONG -- has no effect, this object isn't tracked
```

--> **Gotcha: mutating the object you passed to `merge()` instead of its return value** -- a extremely common bug. `merge()` copies the detached entity's STATE onto a (possibly newly-loaded) managed instance and returns that managed instance; the argument you passed in remains detached and untracked.

# `hibernate.cfg.xml` and Configuration Properties

--> Hibernate can be configured via an XML file (`hibernate.cfg.xml`, classic/legacy style, still used in non-Spring standalone Hibernate apps) or via plain properties (`hibernate.properties`, or -- in a Spring Boot app -- `spring.jpa.*`/`spring.datasource.*` entries in `application.properties`/`application.yml`, which Spring Boot translates into Hibernate properties under the hood).

```xml
<!-- hibernate.cfg.xml -- classic standalone-Hibernate configuration style -->
<hibernate-configuration>
    <session-factory>
        <property name="hibernate.connection.driver_class">org.postgresql.Driver</property>
        <property name="hibernate.connection.url">jdbc:postgresql://localhost:5432/mydb</property>
        <property name="hibernate.connection.username">app_user</property>
        <property name="hibernate.connection.password">secret</property>
        <property name="hibernate.dialect">org.hibernate.dialect.PostgreSQLDialect</property>

        <property name="hibernate.hbm2ddl.auto">validate</property>   <!-- schema management: see below -->
        <property name="hibernate.show_sql">true</property>
        <property name="hibernate.format_sql">true</property>

        <!-- Connection pool sizing (a bare Hibernate app; Spring Boot uses HikariCP by default instead) -->
        <property name="hibernate.connection.pool_size">10</property>

        <mapping class="com.example.Product"/>
        <mapping class="com.example.Category"/>
    </session-factory>
</hibernate-configuration>
```

--> **The equivalent in a Spring Boot `application.properties`** -- Spring Boot auto-configures the `EntityManagerFactory`/`SessionFactory` for you from these, so you rarely write `hibernate.cfg.xml` in a Spring project at all:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/mydb
spring.datasource.username=app_user
spring.datasource.password=secret

spring.jpa.hibernate.ddl-auto=validate
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true

# Explicit prefix for ANY raw Hibernate property not otherwise exposed by Spring Boot:
# spring.jpa.properties.hibernate.<anything> passes straight through to Hibernate.
spring.jpa.properties.hibernate.jdbc.batch_size=25
```

--> **`hibernate.dialect`** tells Hibernate which SQL variant to generate (pagination syntax, identity/sequence handling, function names all differ across databases). Modern Hibernate versions (6.x+) can usually AUTO-DETECT the dialect from the JDBC connection metadata, but pinning it explicitly avoids surprises and speeds up startup slightly by skipping detection.
--> **`hibernate.hbm2ddl.auto` / `spring.jpa.hibernate.ddl-auto`** -- the schema-generation strategy: `none` (Hibernate never touches schema), `validate` (verify entities match existing schema, fail fast if not), `update` (add missing tables/columns, dev-only), `create`/`create-drop` (wipe and rebuild every startup, tests/demos only). **Production should use `validate` or `none`, with schema changes handled by Flyway/Liquibase** (see Theory 06) -- this is repeated deliberately because it is the single most consequential misconfiguration in real Hibernate deployments.

# The First-Level Cache -- Always On, Scoped to the Session

--> The **first-level cache** is the persistence context itself, viewed from a caching angle -- it is NOT optional, cannot be disabled, and its scope is exactly ONE `Session`/`EntityManager`. Within that scope, asking for the same entity by ID twice returns the SAME Java object reference without hitting the database the second time.

```java
@Transactional
public void demonstrateFirstLevelCache(EntityManager em) {
    Product p1 = em.find(Product.class, 1L);   // SELECT issued -- p1 now Managed, cached in this persistence context
    Product p2 = em.find(Product.class, 1L);   // NO SELECT issued -- returned straight from the first-level cache

    System.out.println(p1 == p2);   // true -- literally the same object reference, not just equal data
}
```

--> **Why this matters beyond a performance freebie** -- it guarantees OBJECT IDENTITY within a single persistence context: if you load the same row through two different code paths inside one transaction, you get the same object, so a change made through one reference is automatically visible through the other (they're the same object). This is also why dirty checking works: Hibernate compares each Managed entity's current field values against a snapshot taken when it entered the persistence context, and issues `UPDATE` statements only for entities that actually changed, at flush time.
--> **The first-level cache disappears with the Session** -- once the `EntityManager`/`Session` closes (transaction ends), that cache is gone; a new one starts fresh for the next unit of work. This is precisely why the SAME row loaded in two different `@Transactional` methods produces two DIFFERENT Java objects (equal data, different references) -- there is no cross-session identity guarantee, only cross-session second-level caching (Theory 04) can bridge that gap.
--> **`flush()` vs `commit()`** -- `flush()` synchronizes the persistence context's in-memory state to the database (issues pending `INSERT`/`UPDATE`/`DELETE` SQL) WITHOUT ending the transaction; `commit()` ends the transaction (implicitly flushing first if needed) and makes the changes permanent. Hibernate auto-flushes before executing a query that could be affected by pending changes, but you can also call `flush()` explicitly (e.g. to get a `@GeneratedValue` ID assigned before you've committed).

# Common Gotchas

--> **Creating a new `SessionFactory`/`EntityManagerFactory` per request** -- catastrophic performance mistake; it's meant to be built once and reused for the application's entire lifetime. (In Spring Boot this is handled correctly for you automatically -- just don't hand-roll your own alongside it.)
--> **Sharing a single `Session`/`EntityManager` across multiple threads** -- neither is thread-safe; each unit of work needs its own.
--> **Mutating the argument passed to `merge()` and expecting it to persist** -- only the RETURNED managed copy is tracked; the original detached object is not.
--> **Leaving `ddl-auto=update` (or `create`) on in production** -- fine for local dev, a real risk of data loss against a live database with real data.
--> **Assuming two entities loaded in different transactions are the same object** -- the first-level cache is per-Session; expect equal DATA but different object references across transaction boundaries.
--> **Forgetting that `flush()` is not `commit()`** -- flushing pushes SQL to the database but the transaction can still roll back afterward, undoing it.

# Best Practices Summary

--> Treat `SessionFactory`/`EntityManagerFactory` as an expensive, application-lifetime singleton; treat `Session`/`EntityManager` as cheap and scoped to one unit of work.
--> Let Spring Boot manage `EntityManager` lifecycle via `@Transactional` in real projects rather than hand-rolling `Session` open/close/commit logic.
--> Always use the object RETURNED by `merge()`, never the detached argument you passed to it.
--> Keep schema management out of Hibernate's hands in production (`ddl-auto=validate`/`none`); use Flyway/Liquibase for real migrations.
--> Remember the first-level cache is automatic and per-Session -- it explains both the "same reference" behavior within a transaction and the "different reference, same data" behavior across transactions.
--> Pin `hibernate.dialect` explicitly in configuration rather than relying purely on auto-detection, for predictability and slightly faster startup.
