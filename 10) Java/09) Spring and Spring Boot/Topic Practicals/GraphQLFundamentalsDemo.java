/**
 * GraphQLFundamentalsDemo.java
 *
 * Demonstrates, with illustrative (framework-free) Java code:
 *     1. A GraphQL SDL schema, shown as a comment block (Query/Mutation root types,
 *        object types, an input type, non-null "!" and list "[...]" notation)
 *     2. Plain Java "domain" classes standing in for what a GraphQL server would
 *        return as field values (Product, Review, User)
 *     3. Resolver-CONCEPT classes -- functional-interface-shaped stand-ins for what
 *        a real GraphQL engine (graphql-java) would call a "DataFetcher", showing
 *        how one resolver only knows how to fetch ITS OWN field from a parent object
 *     4. A tiny hand-rolled "engine" that walks a fixed query shape and calls the
 *        matching resolvers in the same nested/recursive pattern graphql-java uses,
 *        purely to make the resolver-per-field execution model concrete
 *     5. Why non-null ("!") fields are a runtime CONTRACT, illustrated by a resolver
 *        that would violate one if it returned null
 *
 * Covers Theory chapter:
 *     10) Java/09) Spring and Spring Boot/Theory/13 GraphQL Fundamentals.md
 *
 * IMPORTANT -- this file is illustrative, NOT a standalone GraphQL server.
 * Real GraphQL execution (schema parsing/validation, query parsing, introspection,
 * the "!"/"[...]" runtime enforcement, HTTP transport) requires an actual GraphQL
 * engine library on the classpath, most commonly:
 *     - graphql-java                    (the reference GraphQL execution engine for the JVM)
 *     - spring-boot-starter-graphql     (Spring's wrapper around graphql-java -- see file 02)
 *
 * This file compiles and runs standalone (plain JDK, no external deps) because it
 * only MIMICS the resolver/execution shape in plain Java -- it does not parse real
 * GraphQL query syntax or a real .graphqls schema file.
 *
 * Compile: javac 01_graphql_fundamentals_demo.java
 * Run:     java GraphQLFundamentalsDemo
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/*
 * ---------------------------------------------------------------------------
 * The SDL (Schema Definition Language) this demo is modeled on.
 * In a real GraphQL server this would live in a .graphqls file (see file 02
 * for exactly where Spring for GraphQL expects it), NOT inside a .java file --
 * it is reproduced here purely as documentation for the Java classes below.
 * ---------------------------------------------------------------------------
 *
 * type Query {
 *     product(id: ID!): Product
 *     products(category: String): [Product!]!
 * }
 *
 * type Mutation {
 *     createProduct(input: ProductInput!): Product!
 * }
 *
 * type Product {
 *     id: ID!
 *     name: String!
 *     price: Float!
 *     category: String
 *     inStock: Boolean!
 *     reviews: [Review!]!
 * }
 *
 * type Review {
 *     id: ID!
 *     rating: Int!
 *     comment: String
 *     author: User!
 * }
 *
 * type User {
 *     id: ID!
 *     username: String!
 * }
 *
 * input ProductInput {
 *     name: String!
 *     price: Float!
 *     category: String
 * }
 *
 * # Example client query this demo's "engine" mimics resolving:
 * # query {
 * #     product(id: "1") {
 * #         name
 * #         price
 * #         reviews {
 * #             rating
 * #             author { username }
 * #         }
 * #     }
 * # }
 */

// ---------------------------------------------------------------------------
// 1) Domain classes -- plain Java objects a resolver hands back as a field's
//    value. These mirror the SDL "type" declarations above field-for-field.
// ---------------------------------------------------------------------------

class User {
    private final String id;
    private final String username;

    User(String id, String username) {
        this.id = id;
        this.username = username;
    }

    public String getId() { return id; }
    public String getUsername() { return username; }   // default-resolver target: User.username
}

class Review {
    private final String id;
    private final int rating;
    private final String comment;
    private final String authorId;   // NOTE: only an id is stored -- author() below resolves the rest

    Review(String id, int rating, String comment, String authorId) {
        this.id = id;
        this.rating = rating;
        this.comment = comment;
        this.authorId = authorId;
    }

    public String getId() { return id; }
    public int getRating() { return rating; }           // default-resolver target: Review.rating
    public String getComment() { return comment; }      // default-resolver target: Review.comment (nullable)
    public String getAuthorId() { return authorId; }
}

class Product {
    private final String id;
    private final String name;
    private final double price;
    private final String category;   // nullable in the schema (no "!")
    private final boolean inStock;

    Product(String id, String name, double price, String category, boolean inStock) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.category = category;
        this.inStock = inStock;
    }

    public String getId() { return id; }
    public String getName() { return name; }             // default-resolver target: Product.name
    public double getPrice() { return price; }            // default-resolver target: Product.price
    public String getCategory() { return category; }      // default-resolver target: Product.category
    public boolean isInStock() { return inStock; }         // default-resolver target: Product.inStock
}

// Mirrors "input ProductInput" -- an INPUT type, only ever used as an argument,
// never returned as a result (see the Theory file's note on input vs type).
class ProductInput {
    final String name;
    final double price;
    final String category;

    ProductInput(String name, double price, String category) {
        this.name = name;
        this.price = price;
        this.category = category;
    }
}

// ---------------------------------------------------------------------------
// 2) Resolver-concept interfaces -- illustrative stand-ins for graphql-java's
//    real `DataFetcher<T>` (a functional interface: one method, produces one
//    field's value from a DataFetchingEnvironment). Here we simplify the
//    "environment" down to just the piece each resolver actually needs: an
//    argument, or the already-resolved parent object.
// ---------------------------------------------------------------------------

@FunctionalInterface
interface FieldResolver<TParent, TResult> {
    TResult resolve(TParent parent);
}

// ---------------------------------------------------------------------------
// 3) A tiny in-memory "data layer" -- stands in for what would really be
//    database/service calls behind each resolver.
// ---------------------------------------------------------------------------

final class FakeDatabase {
    private FakeDatabase() { }

    static final List<User> USERS = Arrays.asList(
            new User("1", "alice"),
            new User("2", "bob")
    );

    static final List<Product> PRODUCTS = Arrays.asList(
            new Product("1", "Mechanical Keyboard", 89.99, "electronics", true),
            new Product("2", "Standing Desk", 349.00, "furniture", false)
    );

    static final List<Review> REVIEWS = Arrays.asList(
            new Review("r1", 5, "Great switches", "1"),   // product 1, author alice
            new Review("r2", 4, "A bit loud", "2"),        // product 1, author bob
            new Review("r3", 3, "Sturdy but pricey", "1")  // product 2, author alice
    );

    static Product findProductById(String id) {
        return PRODUCTS.stream().filter(p -> p.getId().equals(id)).findFirst().orElse(null);
    }

    // Naive per-product lookup -- deliberately mirrors the N+1-prone resolver
    // pattern the Theory file warns about (fine here since there are only 3
    // rows; file 02's DataLoader demo shows the batched fix for real scale).
    static List<Review> findReviewsByProductId(String productId) {
        List<Review> result = new ArrayList<>();
        for (Review r : REVIEWS) {
            // In this tiny demo, reviews r1/r2 belong to product "1" and r3 to product "2"
            // -- wiring that association explicitly here since Review has no productId field
            // (kept out of the class above to keep the "author" resolver example focused).
            boolean belongsToProduct =
                    (productId.equals("1") && (r.getId().equals("r1") || r.getId().equals("r2")))
                 || (productId.equals("2") && r.getId().equals("r3"));
            if (belongsToProduct) {
                result.add(r);
            }
        }
        return result;
    }

    static User findUserById(String id) {
        return USERS.stream().filter(u -> u.getId().equals(id)).findFirst().orElse(null);
    }
}

// ---------------------------------------------------------------------------
// 4) Resolver-concept classes -- one class per NON-trivial field, exactly
//    like the Theory file's description of "you only write explicit resolver
//    code for fields that need real logic." Fields with a matching getter
//    (Product.name, Review.rating, User.username, ...) need no resolver class
//    here -- they're called out as "default resolver" in comments instead.
// ---------------------------------------------------------------------------

/** Root Query field resolver: product(id: ID!): Product */
class ProductQueryResolver implements FieldResolver<String, Product> {
    @Override
    public Product resolve(String id) {
        return FakeDatabase.findProductById(id);
    }
}

/** Field resolver: Product.reviews -- called once per Product, per the Theory file's execution model */
class ProductReviewsResolver implements FieldResolver<Product, List<Review>> {
    @Override
    public List<Review> resolve(Product product) {
        return FakeDatabase.findReviewsByProductId(product.getId());
    }
}

/** Field resolver: Review.author -- called once per Review; NON-NULL ("User!") in the schema */
class ReviewAuthorResolver implements FieldResolver<Review, User> {
    @Override
    public User resolve(Review review) {
        User author = FakeDatabase.findUserById(review.getAuthorId());
        if (author == null) {
            // The schema declares Review.author as "User!" (non-null). Per the Theory
            // file's "Common Gotchas" section, a resolver returning null here would be
            // a genuine schema-contract violation, not just an inconvenience -- a real
            // GraphQL engine would surface this as a runtime execution error. Since this
            // demo has no such engine to enforce it, we fail loudly ourselves instead.
            throw new IllegalStateException(
                    "Schema violation: Review.author is non-null (User!) but no User was found "
                            + "for authorId=" + review.getAuthorId());
        }
        return author;
    }
}

/** Root Mutation field resolver: createProduct(input: ProductInput!): Product! */
class CreateProductMutationResolver implements FieldResolver<ProductInput, Product> {
    @Override
    public Product resolve(ProductInput input) {
        // A real resolver would persist this via a @Service/repository, exactly like
        // the REST controller -> service -> repository layering covered elsewhere in
        // this study repo. Here we just fabricate a new Product to return.
        String newId = String.valueOf(FakeDatabase.PRODUCTS.size() + 1);
        return new Product(newId, input.name, input.price, input.category, true);
    }
}

// ---------------------------------------------------------------------------
// 5) Demo driver -- walks a FIXED query shape (equivalent to the SDL query
//    documented above) and calls resolvers in the same nested/recursive
//    order graphql-java would, to make the "resolver per field, parent
//    object passed down" execution model concrete without needing a real
//    query parser.
// ---------------------------------------------------------------------------

public class GraphQLFundamentalsDemo {

    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }

    public static void main(String[] args) {

        printSection("1) Resolving: query { product(id: \"1\") { name price reviews { rating author { username } } } }");

        // Root Query resolver -- "product(id: ID!): Product"
        ProductQueryResolver productResolver = new ProductQueryResolver();
        Product product = productResolver.resolve("1");
        // Expected: found, since FakeDatabase.PRODUCTS contains id "1"
        System.out.println("product.name  (default resolver) = " + product.getName());
        System.out.println("product.price (default resolver) = " + product.getPrice());

        // Field resolver -- "Product.reviews", called ONCE for this product, per the
        // Theory file's explanation of nested/recursive resolver execution.
        ProductReviewsResolver reviewsResolver = new ProductReviewsResolver();
        List<Review> reviews = reviewsResolver.resolve(product);
        System.out.println("product.reviews resolver returned " + reviews.size() + " review(s)");
        // Expected: 2 reviews (r1, r2) for product id "1"

        // Field resolver -- "Review.author", called ONCE PER REVIEW (this is exactly
        // the shape of resolver call that becomes an N+1 problem at scale -- see the
        // Theory file's "N+1 problem" section and file 02's DataLoader fix).
        ReviewAuthorResolver authorResolver = new ReviewAuthorResolver();
        for (Review review : reviews) {
            User author = authorResolver.resolve(review);
            // default resolver: User.username
            System.out.println("  review " + review.getId()
                    + " -> rating=" + review.getRating()
                    + ", author.username=" + author.getUsername());
        }
        // Expected:
        //   review r1 -> rating=5, author.username=alice
        //   review r2 -> rating=4, author.username=bob

        printSection("2) Resolving: query { products(category: null) { name inStock } }  (list root field)");
        // "products(category: String): [Product!]!" -- schema-guaranteed non-null list
        // of non-null Products. FakeDatabase.PRODUCTS never contains nulls, matching
        // that guarantee.
        for (Product p : FakeDatabase.PRODUCTS) {
            System.out.println("  " + p.getName() + " (inStock=" + p.isInStock() + ")");
        }
        // Expected: Mechanical Keyboard (inStock=true), Standing Desk (inStock=false)

        printSection("3) Resolving: mutation { createProduct(input: {...}) { id name price } }");
        // Root Mutation resolver -- "createProduct(input: ProductInput!): Product!"
        CreateProductMutationResolver createResolver = new CreateProductMutationResolver();
        ProductInput input = new ProductInput("USB Microphone", 59.99, "electronics");
        Product created = createResolver.resolve(input);
        System.out.println("Created product id=" + created.getId()
                + ", name=" + created.getName()
                + ", price=" + created.getPrice());
        // Expected: Created product id=3, name=USB Microphone, price=59.99

        printSection("4) Non-null (\"!\") enforcement -- what happens if a resolver breaks its contract");
        // Review.author is "User!" in the schema -- deliberately resolve a review with
        // an authorId that doesn't exist in FakeDatabase.USERS, to show the resolver
        // refusing to silently return null for a non-null field.
        Review reviewWithMissingAuthor = new Review("r99", 5, "Orphaned review", "does-not-exist");
        try {
            authorResolver.resolve(reviewWithMissingAuthor);
        } catch (IllegalStateException expected) {
            System.out.println("Caught expected contract violation: " + expected.getMessage());
        }
        // Expected: "Caught expected contract violation: Schema violation: Review.author
        // is non-null (User!) but no User was found for authorId=does-not-exist"

        printSection("5) Recap");
        System.out.println("Every field above was produced by its OWN small resolver function, called");
        System.out.println("with only its parent object -- exactly the per-field resolver model the");
        System.out.println("Theory file describes graphql-java using under the hood. A real server adds");
        System.out.println("query parsing, schema validation, introspection, and HTTP transport on top");
        System.out.println("of this same core idea -- see spring-boot-starter-graphql in file 02.");
        System.out.println("\nAll GraphQL fundamentals demos completed.");
    }
}
