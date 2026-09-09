# Beyond the Basics -- What This Chapter Assumes and Adds

--> This file assumes you already know the basic shape of `@OneToMany`/`@ManyToOne`/`@ManyToMany`/`@OneToOne` and `mappedBy` (covered in `09) Spring and Spring Boot/Theory/04 Spring Data JPA and Databases.md`). Here we go deeper: WHY bidirectional mappings are dangerous if handled carelessly, the full cascade type table, orphan removal edge cases, and composite/embeddable keys -- none of which the Spring Data chapter covers.

# Unidirectional vs Bidirectional Mapping -- The Real Trade-off

--> A **unidirectional** relationship is navigable from only ONE side in Java -- e.g. `Product` has a `Category category` field, but `Category` has no `products` field/collection at all. The database foreign key still exists; only the JAVA-side navigation is one-way.
--> A **bidirectional** relationship is navigable from BOTH sides -- `Product.category` AND `Category.products` both exist, connected via `mappedBy`.

```java
// UNIDIRECTIONAL -- simplest possible mapping. Category has no idea Products exist.
@Entity
class Product {
    @Id @GeneratedValue private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;             // navigable: product.getCategory()
}

@Entity
class Category {
    @Id @GeneratedValue private Long id;
    private String name;
    // no "products" field at all -- to find a Category's Products, query for them:
    // SELECT p FROM Product p WHERE p.category.id = :categoryId
}
```

--> **Why prefer unidirectional when possible** -- it is simpler, has no synchronization burden (see below), and is Hibernate's officially recommended default whenever you don't clearly need to navigate FROM the "one"/inverse side in your actual code. Reach for bidirectional only when your application genuinely, repeatedly needs `category.getProducts()` navigation in real code paths -- not "just in case."
--> **The synchronization burden of bidirectional mappings** -- Hibernate does NOT automatically keep both sides of a bidirectional relationship in sync in memory. Setting only one side leaves the OTHER side's in-memory collection stale until the entity is reloaded from the database:

```java
Category electronics = categoryRepository.findByName("Electronics").orElseThrow();
Product laptop = new Product("Laptop", price);

laptop.setCategory(electronics);          // sets the owning side -- this alone determines the FK in the DB

// electronics.getProducts() does NOT yet contain "laptop" in memory! Only a reload
// (or a fresh query) would reflect it, because Hibernate only tracks Category.products
// as a mapping instruction for SQL generation -- it does not eagerly re-run the
// collection query just because the owning side changed.
```

--> **The standard fix -- a convenience "both sides at once" helper method**, conventionally placed on whichever side is more natural to call from:

```java
@Entity
class Category {
    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Product> products = new ArrayList<>();

    // Keeps BOTH sides of the relationship consistent in memory, in one call.
    public void addProduct(Product product) {
        products.add(product);
        product.setCategory(this);        // sets the owning side too
    }

    public void removeProduct(Product product) {
        products.remove(product);
        product.setCategory(null);
    }
}

// Calling code now only ever calls the helper, never sets one side manually:
electronics.addProduct(laptop);   // both electronics.products and laptop.category are now consistent in memory
```

# `mappedBy` Deep Dive -- Ownership, Not Just Syntax

--> **`mappedBy` marks the INVERSE (non-owning) side.** The rule that matters: exactly one side of any bidirectional relationship OWNS the foreign key column (or join table), and that is determined by which side does NOT have `mappedBy`.
--> **What "owning" actually controls** -- only the owning side's in-memory state is consulted when Hibernate decides what SQL to issue for the relationship. Changes made ONLY on the inverse (`mappedBy`) side, without also updating the owning side, are silently ignored by Hibernate at flush time -- not an error, just a no-op from the database's perspective.

```java
// WRONG -- this looks like it should work, but changes NOTHING in the database,
// because "products" on Category is the INVERSE (mappedBy) side.
Category electronics = categoryRepository.findById(1L).orElseThrow();
electronics.getProducts().add(someProduct);   // no FK update happens -- Hibernate ignores this at flush

// RIGHT -- must set the OWNING side (Product.category) for the FK to actually change.
someProduct.setCategory(electronics);
```

--> **Practical rule of thumb** -- for `@OneToMany`/`@ManyToOne`, the `@ManyToOne` side (which carries `@JoinColumn`, i.e. the actual foreign key column) is ALWAYS the owner; `@OneToMany` is ALWAYS the inverse side, always carries `mappedBy`. There's no ambiguity here because a foreign key column can only physically live in one table.
--> **For `@ManyToMany`**, either side COULD be designated the owner (arbitrary choice, since neither side has a "natural" FK) -- whichever side lacks `mappedBy` and declares the `@JoinTable` is the owner by convention/decision, not by any structural necessity.

# Cascade Types -- The Full Table and Why Each One Matters

--> `cascade` on a relationship annotation controls which JPA operations performed on the PARENT automatically propagate to the associated CHILD/children. Getting this wrong is a common source of either "why did my child rows silently vanish" or "why didn't saving the parent also save its children" bugs.

| CascadeType | Propagates when parent is... | Common use |
|---|---|---|
| `PERSIST` | `persist()`d (newly created) | Saving a new parent should also save its brand-new children in one call |
| `MERGE` | `merge()`d (detached re-attached) | Re-attaching a parent should also re-attach its children's state |
| `REMOVE` | `remove()`d (deleted) | Deleting a parent should also delete children that have no independent existence |
| `REFRESH` | `refresh()`d (reloaded from DB, discarding in-memory changes) | Reloading a parent should also reload its children from the DB |
| `DETACH` | `detach()`d (removed from persistence context without deleting) | Detaching a parent should also detach its children from the same context |
| `ALL` | Every one of the above | Convenience shorthand -- "this child's lifecycle is fully owned by the parent" |

```java
@Entity
class Order {
    @Id @GeneratedValue private Long id;

    // ALL is appropriate here -- an OrderLine has NO independent existence or
    // meaning outside its Order; deleting the Order should always delete its lines.
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderLine> lines = new ArrayList<>();
}
```

--> **The classic mistake -- reflexively slapping `cascade = ALL` on every relationship.** Consider `@ManyToOne` from `Product` to `Category`: cascading `REMOVE` from Product onto Category would mean deleting one product could delete an entire category (and, by further cascade, every OTHER product in it) -- almost certainly not intended, since a Category is a shared, independently-meaningful entity, not something owned exclusively by one Product. **Cascade only in the direction of genuine ownership** -- from the entity that "contains"/"owns" the other, never from a child back onto a shared parent.
--> **`CascadeType.REMOVE` vs `orphanRemoval` -- they solve different problems:**

| Mechanism | Triggers on | Scope |
|---|---|---|
| `cascade = CascadeType.REMOVE` | Explicitly calling `remove()`/`delete()` on the PARENT | Deletes all children when the parent itself is deleted |
| `orphanRemoval = true` | Removing a child from the parent's collection (or reassigning it to a different parent), WITHOUT deleting the parent | Deletes that specific child even though the parent still exists |

```java
Order order = orderRepository.findById(1L).orElseThrow();
order.getLines().remove(0);        // just removes it from the in-memory list

// With orphanRemoval = true: at flush, Hibernate detects that OrderLine is no
// longer referenced by any Order and issues a DELETE for that row -- even
// though "order" itself was never deleted.
//
// Without orphanRemoval (cascade = ALL only, no orphanRemoval): that OrderLine
// row survives in the database, now with a dangling/nulled foreign key
// (or an orphaned row, depending on mapping) -- rarely what anyone wants.
```

--> **`orphanRemoval` is only meaningful on the "one" side of `@OneToMany`/`@OneToOne`** and only makes sense for children with NO independent existence outside their one owning parent -- inappropriate for anything that could reasonably be reassigned (e.g. don't put `orphanRemoval` on `Category.products` if a Product should be reassignable to a different Category without being deleted).

# Composite Keys -- `@Embeddable` and `@EmbeddedId`

--> Most entities use a single surrogate key (`@Id @GeneratedValue Long id`) -- simple, stable, and recommended by default. Occasionally a table's natural primary key is genuinely COMPOSITE (made of more than one column), most commonly a join-table-turned-entity (see the `@ManyToMany` "extra columns" case) or a legacy schema you don't control.
--> **`@Embeddable` + `@EmbeddedId`** -- define the composite key as its own small class, marked `@Embeddable`, implementing `equals()`/`hashCode()` (JPA requires this for composite keys -- identity comparison must be by VALUE, not reference), then use it as the entity's `@Id` via `@EmbeddedId`.

```java
@Embeddable
class OrderLineId implements Serializable {
    private Long orderId;
    private Integer lineNumber;

    protected OrderLineId() { }

    public OrderLineId(Long orderId, Integer lineNumber) {
        this.orderId = orderId;
        this.lineNumber = lineNumber;
    }

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

@Entity
class OrderLine {
    @EmbeddedId
    private OrderLineId id;

    private String description;
    private BigDecimal amount;
}
```

--> **Alternative: `@IdClass`** -- functionally similar (a composite key made of multiple fields), but the ID fields are declared directly on the entity itself (duplicated in a separate class annotated `@IdClass` referencing the same field names) rather than wrapped in one embeddable object. `@EmbeddedId` is generally considered cleaner/more object-oriented (the composite key is a genuine value object you can pass around); `@IdClass` shows up more in legacy codebases.
--> **`@Embeddable` for non-key value objects too** -- the same annotation is used for ANY value object embedded into an entity's table, not just composite keys, e.g. an `Address` embedded directly into a `Customer`'s columns rather than as a separate joined table:

```java
@Embeddable
class Address {
    private String street;
    private String city;
    private String zipCode;
}

@Entity
class Customer {
    @Id @GeneratedValue private Long id;

    @Embedded                      // embeds Address's fields as columns directly on the "customers" table
    private Address homeAddress;   // -> columns: street, city, zip_code (or overridden via @AttributeOverride)
}
```

--> **`@AttributeOverride`** -- needed when an entity has TWO fields of the SAME `@Embeddable` type (e.g. `homeAddress` and `billingAddress` both of type `Address`) -- without it, both would try to map to the same column names and collide.

```java
@Entity
class Customer {
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "street", column = @Column(name = "billing_street")),
        @AttributeOverride(name = "city", column = @Column(name = "billing_city"))
    })
    private Address billingAddress;
}
```

# Common Gotchas

--> **Setting only the inverse (`mappedBy`) side of a bidirectional relationship and expecting the database to change** -- it silently does nothing; always set the owning side, ideally via a synchronization helper method that updates both.
--> **Reflexively using `cascade = ALL` everywhere** -- cascading `REMOVE` in the wrong direction (from a child onto a shared parent) can delete far more than intended; cascade only in the direction of true ownership.
--> **Confusing `cascade = REMOVE` with `orphanRemoval`** -- the former deletes children when the PARENT is deleted; the latter deletes a child the moment it's UNLINKED from its parent's collection, even if the parent survives.
--> **Forgetting `equals()`/`hashCode()` on an `@Embeddable` composite key** -- JPA relies on value equality to manage identity for embedded/composite IDs; without it, entity identity comparisons and collection behavior (`Set` membership, `Map` keys) break unpredictably.
--> **Choosing bidirectional "just in case" when the inverse side is never actually navigated in real code** -- adds synchronization burden and mapping complexity for no real benefit; default to unidirectional unless you have a concrete need.

# Best Practices Summary

--> Default to UNIDIRECTIONAL relationships; add the inverse side only when your application code genuinely navigates that direction repeatedly.
--> When bidirectional, always provide (and always use) a synchronization helper method that updates both sides together -- never set just one side directly from calling code.
--> Cascade only in the direction of genuine ownership (parent -> owned child), and audit every `CascadeType.ALL`/`REMOVE` for whether deleting the parent SHOULD really delete everything downstream.
--> Reach for `orphanRemoval = true` only on truly owned children with no independent existence -- never on entities that could reasonably be reassigned to a different parent.
--> Prefer a simple surrogate `@Id` by default; reach for `@Embeddable`/`@EmbeddedId` composite keys only when the natural key is genuinely composite (legacy schema, or a promoted join-table entity).
--> Always implement `equals()`/`hashCode()` on any `@Embeddable` used as (or as part of) an identifier.
