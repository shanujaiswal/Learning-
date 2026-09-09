/**
 * 02_entity_relationships_demo.java
 *
 * Demonstrates, with illustrative JPA/Hibernate entity code:
 *     1. A UNIDIRECTIONAL @ManyToOne mapping (Product -> Supplier, one-way navigation)
 *     2. A BIDIRECTIONAL @OneToMany / @ManyToOne mapping (Category <-> Product) with a
 *        synchronization helper method keeping both in-memory sides consistent
 *     3. The full cascade type spectrum, applied deliberately (Order -> OrderLine)
 *     4. orphanRemoval = true vs plain cascade = REMOVE, and why they solve different problems
 *     5. An @Embeddable composite key (OrderLineId) used as an @EmbeddedId, plus a plain
 *        (non-key) @Embeddable value object (Address) with @AttributeOverride
 *
 * Covers Theory chapter:
 *     15) Hibernate and Advanced JPA/Theory/02 Entity Relationships In Depth.md
 *
 * IMPORTANT -- this file is illustrative, NOT a standalone runnable program.
 * It requires a real Hibernate/JPA + database setup to compile/run:
 *     - hibernate-core (or a Spring Boot Data JPA starter) on the classpath
 *     - a JDBC driver for your target database
 *     - a running database, with ddl-auto=update (dev only) or a real migration
 *       (see Theory 06) creating the corresponding tables
 *
 * Run: drop these classes into a real Hibernate/Spring Boot project's source tree
 * alongside a configured SessionFactory/EntityManagerFactory, then exercise the
 * service methods below from a real entry point (main(), a test, or a controller).
 */

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.*;

// ---------------------------------------------------------------------------
// 1) UNIDIRECTIONAL @ManyToOne -- Supplier has NO "products" field at all.
//    The database foreign key (supplier_id on "products") still exists; only
//    Java-side navigation is one-way (product.getSupplier(), never the reverse).
//    This is Hibernate's recommended default whenever the inverse direction
//    isn't genuinely needed in real code.
// ---------------------------------------------------------------------------

@Entity
@Table(name = "suppliers")
class Supplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    // Deliberately NO "products" collection here -- to find a Supplier's Products,
    // query for them instead: SELECT p FROM Product p WHERE p.supplier.id = :supplierId
    // Adding a bidirectional collection "just in case" would add synchronization
    // burden for no real, exercised benefit (see Theory 02's guidance).

    protected Supplier() { }

    public Supplier(String name) {
        this.name = name;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
}

// ---------------------------------------------------------------------------
// 2) BIDIRECTIONAL @OneToMany / @ManyToOne -- Category <-> Product.
//    Category.products is the INVERSE (mappedBy) side -- purely a Java-side
//    navigation convenience, contributes NO foreign key column itself.
//    Product.category is the OWNING side -- carries @JoinColumn, and is the
//    ONLY side Hibernate consults when deciding what SQL to issue for the FK.
// ---------------------------------------------------------------------------

@Entity
@Table(name = "categories")
class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String name;

    // Inverse side -- cascade = ALL + orphanRemoval = true is appropriate ONLY
    // because a Product in this illustrative model has no independent existence
    // outside its one owning Category (see cascade discussion below).
    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Product> products = new ArrayList<>();

    protected Category() { }

    public Category(String name) {
        this.name = name;
    }

    // Synchronization helper -- keeps BOTH sides of the bidirectional
    // relationship consistent in memory in ONE call. Without this, setting
    // only product.setCategory(this) would leave this.products stale (not
    // containing the new product) until the Category is reloaded from the DB.
    public void addProduct(Product product) {
        products.add(product);
        product.setCategory(this);           // sets the OWNING side too -- this is what actually matters for the FK
    }

    // orphanRemoval = true means calling this actually DELETES the product's
    // row at flush time (not just detaches it from the list) -- appropriate
    // here because a Product is considered fully owned by its Category.
    public void removeProduct(Product product) {
        products.remove(product);
        product.setCategory(null);
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public List<Product> getProducts() { return products; }
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

    // OWNING side of the bidirectional Category<->Product relationship --
    // carries @JoinColumn, i.e. the actual "category_id" foreign key column.
    // Only setting THIS side (never Category.products directly) has any
    // effect on the database.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    // UNIDIRECTIONAL @ManyToOne to Supplier -- see class comment above.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    protected Product() { }

    public Product(String name, BigDecimal price) {
        this.name = name;
        this.price = price;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public BigDecimal getPrice() { return price; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public Supplier getSupplier() { return supplier; }
    public void setSupplier(Supplier supplier) { this.supplier = supplier; }
}

// ---------------------------------------------------------------------------
// 3) Full cascade-type spectrum -- Order -> OrderLine. cascade = ALL is
//    deliberately appropriate here (an OrderLine has NO meaning outside its
//    owning Order), unlike, say, cascading REMOVE from Product onto a shared
//    Category (which Theory 02 explicitly warns against).
// ---------------------------------------------------------------------------

@Entity
@Table(name = "orders")
class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String customerName;

    // cascade = ALL -- PERSIST, MERGE, REMOVE, REFRESH, DETACH all propagate
    // from Order to its OrderLines. orphanRemoval = true additionally deletes
    // an OrderLine the moment it's unlinked from this list, even if the Order
    // itself survives (see orphanRemoval vs cascade=REMOVE demo below).
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderLine> lines = new ArrayList<>();

    protected Order() { }

    public Order(String customerName) {
        this.customerName = customerName;
    }

    public void addLine(OrderLine line) {
        lines.add(line);
        line.setOrder(this);
    }

    public Long getId() { return id; }
    public List<OrderLine> getLines() { return lines; }
}

@Entity
@Table(name = "order_lines")
class OrderLine {

    // @EmbeddedId -- a genuinely COMPOSITE primary key (orderId + lineNumber),
    // used here purely to illustrate @Embeddable/@EmbeddedId; a real OrderLine
    // could equally use a simple surrogate @Id. See OrderLineId below.
    @EmbeddedId
    private OrderLineId id;

    // The OWNING side of this relationship, but note: because the FK columns
    // (order_id) are part of the @EmbeddedId itself, they're mapped via
    // @MapsId rather than a separate @JoinColumn field.
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("orderId")
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(nullable = false, length = 200)
    private String description;

    @Column(nullable = false)
    private BigDecimal amount;

    protected OrderLine() { }

    public OrderLine(Order order, int lineNumber, String description, BigDecimal amount) {
        this.order = order;
        this.id = new OrderLineId(order.getId(), lineNumber);
        this.description = description;
        this.amount = amount;
    }

    public OrderLineId getId() { return id; }
    public Order getOrder() { return order; }
    public void setOrder(Order order) { this.order = order; }
    public String getDescription() { return description; }
    public BigDecimal getAmount() { return amount; }
}

// ---------------------------------------------------------------------------
// 4) @Embeddable composite key -- must implement equals()/hashCode() by
//    VALUE (not reference), because JPA relies on value equality to manage
//    identity for embedded/composite IDs (Set membership, Map keys, etc.).
// ---------------------------------------------------------------------------

@Embeddable
class OrderLineId implements Serializable {

    private Long orderId;
    private Integer lineNumber;

    protected OrderLineId() { }                    // required no-args constructor for JPA

    public OrderLineId(Long orderId, Integer lineNumber) {
        this.orderId = orderId;
        this.lineNumber = lineNumber;
    }

    public Long getOrderId() { return orderId; }
    public Integer getLineNumber() { return lineNumber; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OrderLineId)) return false;
        OrderLineId that = (OrderLineId) o;
        return Objects.equals(orderId, that.orderId) && Objects.equals(lineNumber, that.lineNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderId, lineNumber);
    }
}

// ---------------------------------------------------------------------------
// 5) A plain (non-key) @Embeddable value object -- Address, embedded twice
//    into Customer via @AttributeOverride so both embeddings don't collide
//    on the same column names.
// ---------------------------------------------------------------------------

@Embeddable
class Address {
    private String street;
    private String city;
    private String zipCode;

    protected Address() { }

    public Address(String street, String city, String zipCode) {
        this.street = street;
        this.city = city;
        this.zipCode = zipCode;
    }

    public String getStreet() { return street; }
    public String getCity() { return city; }
    public String getZipCode() { return zipCode; }
}

@Entity
@Table(name = "customers")
class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    // Default column names (street, city, zip_code) -- no collision since
    // this is the only Address field so far.
    @Embedded
    private Address homeAddress;

    // Same @Embeddable type used a SECOND time on this entity -- @AttributeOverride
    // is REQUIRED here, otherwise both embeddings would try to map to the same
    // "street"/"city"/"zip_code" columns and collide.
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "street", column = @Column(name = "billing_street")),
        @AttributeOverride(name = "city", column = @Column(name = "billing_city")),
        @AttributeOverride(name = "zipCode", column = @Column(name = "billing_zip_code"))
    })
    private Address billingAddress;

    protected Customer() { }

    public Customer(String name, Address homeAddress, Address billingAddress) {
        this.name = name;
        this.homeAddress = homeAddress;
        this.billingAddress = billingAddress;
    }

    public Long getId() { return id; }
    public Address getHomeAddress() { return homeAddress; }
    public Address getBillingAddress() { return billingAddress; }
}

// ---------------------------------------------------------------------------
// Service layer -- demonstrates the synchronization helper, cascade behavior,
// and orphanRemoval vs cascade=REMOVE side by side.
// ---------------------------------------------------------------------------

class CatalogRelationshipService {

    private final EntityManager entityManager;

    public CatalogRelationshipService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    // Demonstrates the bidirectional synchronization helper -- calling code
    // NEVER sets category/product sides individually; it always goes through
    // addProduct(), which keeps both sides consistent in memory in one call.
    public void createCategoryWithProducts() {
        Category electronics = new Category("Electronics");

        Product laptop = new Product("Laptop", new BigDecimal("1299.99"));
        Product mouse = new Product("Wireless Mouse", new BigDecimal("29.99"));

        electronics.addProduct(laptop);   // sets laptop.category = electronics AND adds to electronics.products
        electronics.addProduct(mouse);

        // cascade = ALL on Category.products means persisting the Category alone
        // is enough -- both Products are cascaded-persisted in the same call.
        entityManager.persist(electronics);
    }

    // Demonstrates cascade = ALL creating a whole Order + OrderLines in one
    // persist() call, and orphanRemoval actually deleting a line the moment
    // it's removed from the parent's collection -- even though the Order
    // itself is never deleted.
    public void createOrderThenRemoveOneLine(Long orderId) {
        Order order = new Order("Jane Doe");
        order.addLine(new OrderLine(order, 1, "Widget", new BigDecimal("9.99")));
        order.addLine(new OrderLine(order, 2, "Gadget", new BigDecimal("19.99")));
        entityManager.persist(order);      // cascades PERSIST to both OrderLines

        // Later, in a separate unit of work: removing line 0 from the list
        // triggers orphanRemoval -- Hibernate issues a DELETE for that specific
        // OrderLine row at flush, WITHOUT deleting the Order itself.
        Order managedOrder = entityManager.find(Order.class, order.getId());
        managedOrder.getLines().remove(0);
        // No explicit remove() call needed here -- orphanRemoval = true on
        // Order.lines detects the unlinked child and deletes it at flush time.
    }

    // Contrast: cascade = REMOVE triggers only when the PARENT itself is
    // explicitly removed -- deletes ALL children together with the parent.
    public void deleteEntireOrder(Long orderId) {
        Order order = entityManager.find(Order.class, orderId);
        if (order != null) {
            entityManager.remove(order);   // cascade = ALL (includes REMOVE) -- deletes Order AND every OrderLine
        }
    }
}

/*
 * NOTE on annotations used above:
 * This file uses plain JPA (jakarta.persistence.*) annotations throughout, portable to
 * any JPA provider (Hibernate, EclipseLink). It requires a real EntityManagerFactory/
 * EntityManager (or Spring Data JPA repositories wrapping the same), a JDBC driver, and
 * a database schema matching these entities (via ddl-auto=update for local dev, or a
 * real Flyway/Liquibase migration per Theory 06) to actually persist and query data.
 */
