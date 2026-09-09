/**
 * 14_graphql_spring_boot_demo.java
 *
 * Demonstrates, with illustrative Spring for GraphQL code:
 *     1. Where the SDL schema file lives in a Spring Boot project, and its contents
 *     2. A @Controller class using @QueryMapping / @MutationMapping for ROOT fields
 *     3. @SchemaMapping resolving a non-root field (Product.reviews) the naive,
 *        N+1-prone way -- one DB call per parent Product
 *     4. A BatchLoaderRegistry-based DataLoader that fixes that N+1 problem by
 *        batching all requested product ids into ONE query per request
 *     5. A @SchemaMapping method rewritten to use the DataLoader instead of a
 *        direct repository call
 *     6. A minimal Service + Repository layer the controller delegates to,
 *        mirroring the Controller -> Service -> Repository layering used for
 *        REST controllers elsewhere in this study repo
 *
 * Covers Theory chapter:
 *     10) Java/09) Spring and Spring Boot/Theory/14 GraphQL with Spring Boot.md
 *
 * IMPORTANT -- this file is illustrative, NOT a standalone runnable program.
 * It requires a real Spring Boot project on the classpath to compile/run, specifically:
 *     - spring-boot-starter-graphql   (Spring for GraphQL, wraps graphql-java, auto-configures
 *                                      the POST /graphql endpoint and schema loading)
 *     - spring-boot-starter-data-jpa  (or any persistence starter -- ReviewRepository below
 *                                      stands in for a real Spring Data repository)
 *
 * Run (in a real Spring Boot project, after wiring this into src/main/java/... and adding
 * a @SpringBootApplication class with a main() method, plus the schema file below saved to
 * src/main/resources/graphql/schema.graphqls):
 *     mvn spring-boot:run
 *   or
 *     ./gradlew bootRun
 *
 * Example request once the app is running on the default port (8080):
 *     curl -X POST http://localhost:8080/graphql \
 *          -H "Content-Type: application/json" \
 *          -d "{\"query\":\"query { products(category: \\\"electronics\\\") { name reviews { rating author { username } } } }\"}"
 */

// ---------------------------------------------------------------------------
// 0) The SDL schema this controller implements.
//    By convention Spring for GraphQL auto-detects *.graphqls/*.gqls files
//    under src/main/resources/graphql/ -- save this block there as
//    schema.graphqls (the Theory file's "Common Gotchas" section flags this
//    exact path as the #1 place a fresh setup goes wrong).
// ---------------------------------------------------------------------------
/*
 * src/main/resources/graphql/schema.graphqls
 * --------------------------------------------
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
 */

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.BatchLoaderRegistry;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

// ---------------------------------------------------------------------------
// 1) Domain classes -- mirror the schema's "type" declarations field-for-field,
//    exactly as they would in a real project's model/entity classes.
// ---------------------------------------------------------------------------

class Product {
    private Long id;
    private String name;
    private double price;
    private String category;

    public Product(Long id, String name, double price, String category) {
        this.id = id; this.name = name; this.price = price; this.category = category;
    }

    public Long getId() { return id; }
    public String getName() { return name; }               // default resolver: Product.name
    public double getPrice() { return price; }              // default resolver: Product.price
    public String getCategory() { return category; }        // default resolver: Product.category
}

class Review {
    private Long id;
    private int rating;
    private String comment;
    private Long productId;   // foreign key -- NOT part of the GraphQL schema, used only for the batch query
    private Long authorId;

    public Review(Long id, int rating, String comment, Long productId, Long authorId) {
        this.id = id; this.rating = rating; this.comment = comment;
        this.productId = productId; this.authorId = authorId;
    }

    public Long getId() { return id; }
    public int getRating() { return rating; }                // default resolver: Review.rating
    public String getComment() { return comment; }           // default resolver: Review.comment
    public Long getProductId() { return productId; }
    public Long getAuthorId() { return authorId; }
}

class User {
    private Long id;
    private String username;

    public User(Long id, String username) { this.id = id; this.username = username; }

    public Long getId() { return id; }
    public String getUsername() { return username; }         // default resolver: User.username
}

// Mirrors "input ProductInput" -- an INPUT type only ever bound from a client
// argument via @Argument, never returned as a query/mutation result.
class ProductInput {
    private String name;
    private double price;
    private String category;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}

// ---------------------------------------------------------------------------
// 2) Repository + Service layer -- the GraphQL controller stays thin and
//    delegates here, exactly like the Controller -> Service -> Repository
//    layering used for the REST controller in the Spring Boot practicals.
// ---------------------------------------------------------------------------

interface ReviewRepository {
    // A naive, ONE-product-at-a-time lookup -- what a resolver would call if
    // it fetched directly, without batching. This is the shape of call that
    // produces the N+1 problem when invoked once per parent Product.
    List<Review> findByProductId(Long productId);

    // The BATCHED equivalent -- accepts a whole SET of product ids and returns
    // every matching review in ONE query (e.g. "SELECT * FROM reviews WHERE
    // product_id IN (...)"). This is what the DataLoader below calls instead.
    List<Review> findByProductIdIn(Set<Long> productIds);
}

interface UserRepository {
    User findById(Long id);
}

@Service
class ProductService {
    // In a real project this delegates to a Spring Data JPA ProductRepository;
    // kept as a stand-in here so this file's focus stays on the GraphQL wiring.
    Product findById(Long id) { throw new UnsupportedOperationException("illustrative only"); }
    List<Product> search(String category) { throw new UnsupportedOperationException("illustrative only"); }
    Product create(ProductInput input) { throw new UnsupportedOperationException("illustrative only"); }
}

// ---------------------------------------------------------------------------
// 3) Root field resolvers -- @QueryMapping / @MutationMapping.
//    A plain @Controller, NOT @RestController -- per the Theory file, GraphQL
//    controllers don't return HTTP response bodies directly; the framework
//    serializes the returned object into the "data" field of the GraphQL
//    response envelope instead.
// ---------------------------------------------------------------------------

@Controller
class ProductGraphQlController {

    private final ProductService productService;
    private final ReviewRepository reviewRepository;   // used directly below ONLY to contrast naive vs batched
    private final UserRepository userRepository;

    public ProductGraphQlController(ProductService productService,
                                     ReviewRepository reviewRepository,
                                     UserRepository userRepository) {
        this.productService = productService;
        this.reviewRepository = reviewRepository;
        this.userRepository = userRepository;
    }

    // Matches "product(id: ID!): Product" -- method name "product" matches the
    // schema field name by default, so no explicit @QueryMapping("product") is needed.
    @QueryMapping
    public Product product(@Argument Long id) {
        return productService.findById(id);
    }

    // Matches "products(category: String): [Product!]!"
    @QueryMapping
    public List<Product> products(@Argument String category) {
        return productService.search(category);
    }

    // Matches "createProduct(input: ProductInput!): Product!"
    @MutationMapping
    public Product createProduct(@Argument ProductInput input) {
        return productService.create(input);
    }

    // ---- Resolving Product.reviews the NAIVE way -- kept here, commented out
    // of the actual @SchemaMapping wiring, purely to show what the Theory
    // file's "N+1 problem" section warns against. If products() above returns
    // 50 products, this method gets called 50 SEPARATE times -- 50 SEPARATE
    // "SELECT * FROM reviews WHERE product_id = ?" round trips.
    //
    // @SchemaMapping(typeName = "Product", field = "reviews")
    // public List<Review> reviewsNaive(Product product) {
    //     return reviewRepository.findByProductId(product.getId());   // <-- N+1 !
    // }
    //
    // The DataLoader-based version below (section 5) is the one actually wired
    // up via @SchemaMapping for this field in this file.

    // Matches "Review.author: User!" -- called once per Review, same N+1 shape
    // as reviews() above; a real project would give this its own DataLoader too
    // (omitted here to keep the demo focused on ONE complete DataLoader example).
    @SchemaMapping(typeName = "Review", field = "author")
    public User author(Review review) {
        return userRepository.findById(review.getAuthorId());
    }
}

// ---------------------------------------------------------------------------
// 4) DataLoader registration -- the N+1 fix. A BatchLoaderRegistry bean
//    registers a NAMED batch loader at startup; Spring for GraphQL creates a
//    fresh DataLoaderRegistry (and therefore a fresh per-request cache) for
//    EVERY GraphQL request automatically.
// ---------------------------------------------------------------------------

@Configuration
class DataLoaderConfig {

    @Bean
    public BatchLoaderRegistry.RegistrationSpec<Long, List<Review>> reviewsByProductLoader(
            BatchLoaderRegistry registry, ReviewRepository reviewRepository) {

        return registry.forName("reviewsByProduct")
                .registerMappedBatchLoader((productIds, env) -> {
                    // ONE query for the WHOLE batch of product ids collected during this
                    // level of the query's execution -- instead of N separate calls.
                    List<Review> allReviews = reviewRepository.findByProductIdIn(productIds);
                    Map<Long, List<Review>> grouped = allReviews.stream()
                            .collect(Collectors.groupingBy(Review::getProductId));
                    return CompletableFuture.completedFuture(grouped);
                });
    }
}

// ---------------------------------------------------------------------------
// 5) The BATCHED @SchemaMapping resolver -- this is the version actually
//    wired up for Product.reviews. Instead of querying immediately, it
//    registers this product's id into the CURRENT batch via loader.load(...);
//    graphql-java automatically dispatches every queued key together once all
//    sibling Product.reviews resolutions for this request have been requested.
// ---------------------------------------------------------------------------

@Controller
class ProductReviewsBatchedController {

    // DataLoader<K, V> is requested as a method parameter -- Spring for GraphQL
    // resolves it by the name registered above ("reviewsByProduct") based on
    // the generic type, exactly as described in the Theory file.
    @SchemaMapping(typeName = "Product", field = "reviews")
    public CompletableFuture<List<Review>> reviews(
            Product product,
            org.dataloader.DataLoader<Long, List<Review>> loader) {
        return loader.load(product.getId());
    }
}

/*
 * NOTE on why this file has TWO @Controller classes for the SAME schema type
 * (ProductGraphQlController and ProductReviewsBatchedController):
 * this is purely to let this single illustrative file show BOTH the naive,
 * N+1-prone approach (commented out above) and the DataLoader-based fix
 * side by side for comparison. In a real project you would have exactly ONE
 * @SchemaMapping(typeName = "Product", field = "reviews") method -- merge
 * ProductReviewsBatchedController's method directly into
 * ProductGraphQlController and delete the naive version entirely.
 *
 * NOTE on annotations used above:
 * This snippet uses Spring for GraphQL annotations (@Controller, @QueryMapping,
 * @MutationMapping, @SchemaMapping, @Argument) and Spring stereotypes (@Service,
 * @Configuration, @Bean) exactly as they appear in a real Spring Boot project,
 * which requires component scanning + a @SpringBootApplication entry point to
 * wire everything together. Since this file is illustrative only (see header),
 * that bootstrap class -- along with real ReviewRepository/UserRepository
 * implementations (e.g. Spring Data JPA interfaces) -- is intentionally
 * omitted. Drop these classes into a real Spring Boot project's source tree,
 * with spring-boot-starter-graphql on the classpath and the schema file saved
 * to src/main/resources/graphql/schema.graphqls, to see it run end-to-end.
 */
