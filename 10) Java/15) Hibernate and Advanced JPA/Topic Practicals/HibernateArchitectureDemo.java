/**
 * HibernateArchitectureDemo.java
 *
 * Demonstrates, with illustrative native-Hibernate code:
 *     1. A hibernate.cfg.xml-equivalent configuration (shown as a comment block below,
 *        since XML can't live inside a .java file) plus the Configuration/SessionFactory
 *        bootstrap sequence built from it
 *     2. SessionFactory (built ONCE, held for the app's lifetime) vs Session (cheap,
 *        one per unit of work) -- the core Hibernate runtime architecture
 *     3. Entity lifecycle states (transient -> managed -> detached -> removed) via
 *        persist()/merge()/detach()/remove()
 *     4. The first-level cache in action -- fetching the SAME entity twice in ONE
 *        Session returns the SAME object reference, with no second SELECT issued
 *
 * Covers Theory chapter:
 *     15) Hibernate and Advanced JPA/Theory/01 Hibernate Architecture and Core Concepts.md
 *
 * IMPORTANT -- this file is illustrative, NOT a standalone runnable program.
 * It requires a real Hibernate + JPA setup to compile/run, specifically:
 *     - hibernate-core on the classpath (native Hibernate, not just the jakarta.persistence API)
 *     - a JDBC driver for your target database (e.g. h2, postgresql, mysql-connector-j)
 *     - a real hibernate.cfg.xml (or equivalent properties) on the classpath, see below
 *     - a running database matching that configuration
 *
 * Run (in a real standalone Hibernate project, after adding the dependencies above and
 * placing hibernate.cfg.xml on the classpath, e.g. src/main/resources/hibernate.cfg.xml):
 *     mvn compile exec:java -Dexec.mainClass="HibernateArchitectureDemo"
 *   or, from an IDE, just run main() directly once the classpath/config is wired up.
 */

// ---------------------------------------------------------------------------
// hibernate.cfg.xml -- the classic standalone-Hibernate configuration file.
// This would live at src/main/resources/hibernate.cfg.xml in a real project;
// it is shown here as a comment block since XML cannot appear directly in a
// .java source file. Configuration.configure() (below) reads exactly this.
// ---------------------------------------------------------------------------
/*
<!DOCTYPE hibernate-configuration PUBLIC
        "-//Hibernate/Hibernate Configuration DTD 3.0//EN"
        "http://www.hibernate.org/dtd/hibernate-configuration-3.0.dtd">
<hibernate-configuration>
    <session-factory>
        <property name="hibernate.connection.driver_class">org.postgresql.Driver</property>
        <property name="hibernate.connection.url">jdbc:postgresql://localhost:5432/demo_db</property>
        <property name="hibernate.connection.username">app_user</property>
        <property name="hibernate.connection.password">secret</property>
        <property name="hibernate.dialect">org.hibernate.dialect.PostgreSQLDialect</property>

        <!-- Schema management: "validate" in any shared/production-like environment --
             see Theory 06 for why ddl-auto/hbm2ddl.auto should never manage schema in prod. -->
        <property name="hibernate.hbm2ddl.auto">validate</property>
        <property name="hibernate.show_sql">true</property>
        <property name="hibernate.format_sql">true</property>

        <property name="hibernate.connection.pool_size">10</property>

        <mapping class="Product"/>
        <mapping class="Category"/>
    </session-factory>
</hibernate-configuration>
*/

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;

import jakarta.persistence.*;
import java.math.BigDecimal;

// ---------------------------------------------------------------------------
// Entities -- plain JPA annotations, mapped exactly as declared in
// hibernate.cfg.xml's <mapping class="..."/> entries above.
// ---------------------------------------------------------------------------

@Entity
@Table(name = "categories")
class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String name;

    protected Category() { }                       // required no-args constructor for JPA/Hibernate

    public Category(String name) {
        this.name = name;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    protected Product() { }                        // required no-args constructor for JPA/Hibernate

    public Product(String name, BigDecimal price, Category category) {
        this.name = name;
        this.price = price;
        this.category = category;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public Category getCategory() { return category; }
}

// ---------------------------------------------------------------------------
// Main demo class -- name matches the file (HibernateArchitectureDemo), and
// walks through the SessionFactory/Session/persistence-context architecture
// end to end.
// ---------------------------------------------------------------------------

public class HibernateArchitectureDemo {

    public static void main(String[] args) {

        // ---------------------------------------------------------------
        // STEP 1 -- Configuration + SessionFactory. Configuration.configure()
        // reads hibernate.cfg.xml from the classpath (shown in the comment
        // block above). buildSessionFactory() is EXPENSIVE (parses all
        // entity mappings, builds SQL generation metadata) -- do this
        // exactly ONCE per application, at startup, and hold the resulting
        // SessionFactory for the entire application's lifetime.
        // ---------------------------------------------------------------
        Configuration configuration = new Configuration().configure();   // reads hibernate.cfg.xml
        SessionFactory sessionFactory = configuration.buildSessionFactory();

        try {
            demonstrateEntityLifecycle(sessionFactory);
            demonstrateFirstLevelCache(sessionFactory);
        } finally {
            // Close the SessionFactory only at application shutdown -- never per-request.
            sessionFactory.close();
        }
    }

    // -------------------------------------------------------------------
    // STEP 2 -- Session per unit of work, plus entity lifecycle states:
    // transient -> (persist) -> managed -> (session closes) -> detached
    // -> (merge) -> managed again, and finally removed.
    // -------------------------------------------------------------------
    private static void demonstrateEntityLifecycle(SessionFactory sessionFactory) {

        Category detachedCategory;

        // A Session is lightweight, cheap to create, and NOT thread-safe --
        // exactly one per unit of work (never share across threads).
        try (Session session = sessionFactory.openSession()) {
            Transaction tx = session.beginTransaction();
            try {
                // TRANSIENT -- a plain "new" object; Hibernate has never heard of it,
                // no database row exists yet.
                Category electronics = new Category("Electronics");

                // persist() moves it from Transient -> Managed. It now enters this
                // Session's persistence context and will be INSERTed at flush/commit.
                session.persist(electronics);

                Product laptop = new Product("Laptop", new BigDecimal("1299.99"), electronics);
                session.persist(laptop);               // also Transient -> Managed

                // Mutating a Managed entity -- Hibernate's dirty checking compares this
                // against the snapshot taken when it entered the persistence context,
                // and will issue an UPDATE for it at flush time (no explicit save needed).
                laptop.setPrice(new BigDecimal("1199.99"));

                tx.commit();   // flushes pending INSERT/UPDATE SQL, then commits

                detachedCategory = electronics;
            } catch (RuntimeException e) {
                tx.rollback();
                throw e;
            }
            // Session closes here (try-with-resources) -- every entity that was
            // Managed inside it is now DETACHED: still holds data in memory, but
            // Hibernate is no longer tracking changes to it.
        }

        // detachedCategory is DETACHED here -- modifying it directly has NO effect
        // on the database, because no Session is tracking it anymore.

        // merge() re-attaches a detached entity's STATE onto a fresh, newly-Managed
        // copy in a NEW Session -- note we use the RETURNED reference, never the
        // original detached object, going forward (the classic persist()-vs-merge()
        // gotcha called out in Theory 01).
        try (Session session2 = sessionFactory.openSession()) {
            Transaction tx2 = session2.beginTransaction();
            try {
                Category managedCopy = session2.merge(detachedCategory);
                // managedCopy is now Managed in session2's persistence context;
                // detachedCategory itself remains untracked and stale.
                System.out.println("Re-attached category name: " + managedCopy.getName());

                tx2.commit();
            } catch (RuntimeException e) {
                tx2.rollback();
                throw e;
            }
        }

        // REMOVED state demo -- load a fresh Product, mark it removed, flush deletes it.
        try (Session session3 = sessionFactory.openSession()) {
            Transaction tx3 = session3.beginTransaction();
            try {
                Product product = session3.get(Product.class, 1L);
                if (product != null) {
                    session3.remove(product);   // Managed -> Removed; DELETE issued at flush
                }
                tx3.commit();
            } catch (RuntimeException e) {
                tx3.rollback();
                throw e;
            }
        }
    }

    // -------------------------------------------------------------------
    // STEP 3 -- First-level cache demonstration. The persistence context
    // IS the first-level cache: always on, cannot be disabled, scoped to
    // exactly one Session. Fetching the same entity by ID twice within
    // ONE Session returns the SAME object reference, with only ONE SELECT
    // issued against the database.
    // -------------------------------------------------------------------
    private static void demonstrateFirstLevelCache(SessionFactory sessionFactory) {

        try (Session session = sessionFactory.openSession()) {
            Transaction tx = session.beginTransaction();
            try {
                // First fetch -- issues "SELECT * FROM products WHERE id = ?", and the
                // resulting Product enters this Session's persistence context.
                Product p1 = session.get(Product.class, 1L);

                // Second fetch of the SAME id, in the SAME Session -- Hibernate finds it
                // already in the first-level cache and returns it directly, WITHOUT
                // issuing a second SELECT.
                Product p2 = session.get(Product.class, 1L);

                // true -- literally the same object reference, not just equal data.
                // This is also exactly why dirty checking works: there is only ONE
                // in-memory instance to compare against its load-time snapshot.
                System.out.println("p1 == p2 (same object reference)? " + (p1 == p2));

                tx.commit();
            } catch (RuntimeException e) {
                tx.rollback();
                throw e;
            }
        }
        // The Session above is now closed -- its first-level cache is GONE. A brand
        // new Session opened next would issue a FRESH SELECT for id=1L and return a
        // DIFFERENT object reference (equal data, but not the same instance) -- there
        // is no cross-Session identity guarantee without a second-level cache (Theory 04).
    }
}

/*
 * NOTE on annotations/APIs used above:
 * This file mixes native Hibernate (org.hibernate.Session/SessionFactory/Transaction/
 * Configuration) with standard JPA annotations (jakarta.persistence.*) exactly as a
 * real standalone (non-Spring) Hibernate application would. In a Spring Boot app, an
 * EntityManagerFactory/EntityManager wraps this exact same machinery for you, and
 * @Transactional replaces the manual Transaction begin/commit/rollback dance shown
 * here -- but the underlying architecture (SessionFactory built once, Session per
 * unit of work, persistence context as first-level cache) is identical either way.
 * This bootstrap requires hibernate.cfg.xml on the classpath, a JDBC driver, and a
 * live database matching that configuration to actually run end-to-end.
 */
