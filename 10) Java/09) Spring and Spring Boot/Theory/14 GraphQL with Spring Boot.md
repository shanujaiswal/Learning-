# Why a Dedicated Starter -- spring-boot-starter-graphql

--> **`spring-boot-starter-graphql`** is Spring's official starter for building GraphQL servers, built on top of the standalone **Spring for GraphQL** project (which itself wraps `graphql-java`, the reference GraphQL execution engine for the JVM). It follows the exact same philosophy as `spring-boot-starter-web` for REST: pull in one dependency, get auto-configuration, and express your API surface through ANNOTATIONS on Spring-managed beans rather than hand-wiring the execution engine yourself.
--> Adding it to a Spring Boot project auto-configures an HTTP endpoint (`POST /graphql` by default), wires up `graphql-java`'s execution engine, and gives you a `GraphQlSource` bean that loads your schema. It also transitively brings in `spring-boot-starter-web` (or WebFlux, if you're on the reactive stack) since GraphQL still travels over HTTP underneath.

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-graphql</artifactId>
</dependency>
```

--> **Where the schema lives** -- by convention, Spring for GraphQL looks for `.graphqls` (or `.gqls`) SDL files under `src/main/resources/graphql/`. You write the schema by hand, in plain SDL, exactly as shown in the previous file -- Spring does NOT generate the schema from your Java classes (this is a deliberate "schema-first" design, in contrast to some "code-first" GraphQL frameworks in other ecosystems).

```text
src/main/resources/graphql/schema.graphqls
```

```graphql
type Query {
    product(id: ID!): Product
    products(category: String): [Product!]!
}

type Mutation {
    createProduct(input: ProductInput!): Product!
}

type Product {
    id: ID!
    name: String!
    price: Float!
    category: String
    reviews: [Review!]!
}

type Review {
    id: ID!
    rating: Int!
    comment: String
}

input ProductInput {
    name: String!
    price: Float!
    category: String
}
```

--> **`input` types** -- GraphQL distinguishes OUTPUT types (`type`, what the server returns) from INPUT types (`input`, what the client sends as an argument). `ProductInput` above can only be used as an argument, never as a return type -- this separation mirrors the REST convention of separate Request/Response DTOs covered in the Spring REST chapter, except GraphQL enforces it at the SCHEMA level rather than by convention.

# @QueryMapping, @MutationMapping, @SubscriptionMapping -- Wiring Root Fields

--> These annotations mark a Spring bean's method as the resolver for a ROOT-level field in `Query`, `Mutation`, or `Subscription` respectively. They're the GraphQL equivalent of `@GetMapping`/`@PostMapping` in Spring MVC -- the mechanism that connects schema fields to actual Java code.

```java
@Controller   // a plain @Controller, NOT @RestController -- GraphQL controllers don't return HTTP response bodies directly
public class ProductGraphQlController {

    private final ProductService productService;

    public ProductGraphQlController(ProductService productService) {
        this.productService = productService;
    }

    @QueryMapping   // matches "product(id: ID!): Product" in the Query type
    public Product product(@Argument Long id) {
        return productService.findById(id);
    }

    @QueryMapping   // matches "products(category: String): [Product!]!"
    public List<Product> products(@Argument String category) {
        return productService.search(category);
    }

    @MutationMapping   // matches "createProduct(input: ProductInput!): Product!"
    public Product createProduct(@Argument ProductInput input) {
        return productService.create(input);
    }
}
```

--> **Method name matches the schema field name by default** -- `@QueryMapping public Product product(...)` matches the `product` field in `type Query` because the METHOD NAME matches the FIELD NAME, exactly like `@PathVariable` matching by parameter name in Spring MVC. If they differ, specify explicitly: `@QueryMapping("product")`.
--> **`@Argument`** extracts a named GraphQL argument and binds it to a method parameter, converting types automatically (GraphQL `ID`/`Int`/`String` -> Java `Long`/`Integer`/`String`, and a GraphQL `input` type -> a matching Java class via reflection, the same convention-driven binding Jackson uses for `@RequestBody` in REST). Like `@PathVariable`, you can bind by matching parameter name or specify it explicitly: `@Argument("id") Long productId`.
--> **No `ResponseEntity` equivalent** -- GraphQL doesn't have per-field HTTP status codes; a resolver either returns a value (possibly `null`, if the schema allows it) or throws an exception, which the framework converts into an entry in the response's `"errors"` array. There's no "return 404" concept at the field level the way there is in REST.

# @SchemaMapping -- Resolving Fields on Non-Root Types

--> `@QueryMapping`/`@MutationMapping` only cover ROOT fields (fields directly on `Query`/`Mutation`/`Subscription`). Every OTHER field in the schema -- like `Product.reviews` -- needs its own resolver too, unless a default (trivial, property-matching) resolver already covers it. **`@SchemaMapping`** is the general-purpose annotation for wiring a resolver to ANY field on ANY type in the schema.

```java
@Controller
public class ProductGraphQlController {

    private final ReviewService reviewService;

    // Resolves Product.reviews -- called once per Product object returned by the "products" query
    @SchemaMapping(typeName = "Product", field = "reviews")
    public List<Review> reviews(Product product) {   // the parent Product is injected automatically
        return reviewService.findByProductId(product.getId());
    }
}
```

--> **The parent object is injected as a method parameter automatically** -- when resolving `Product.reviews`, Spring for GraphQL passes the ALREADY-RESOLVED `Product` instance (the one returned by the `product`/`products` query resolver) into the `@SchemaMapping` method, so it knows which product's reviews to fetch.
--> **`typeName` can be omitted when it matches the parameter's type** -- `@SchemaMapping(field = "reviews")` alone is often enough if the method's parent-object parameter type is unambiguous, but being explicit with `typeName` is common practice for readability, especially once a schema has several types with similarly-named fields.
--> **When you DON'T need `@SchemaMapping` at all** -- if `Product.name` in the schema matches a `Product.getName()` Java getter, Spring for GraphQL resolves it automatically via a default `PropertyDataFetcher` -- no annotated method required. You only write `@SchemaMapping` methods for fields that need actual FETCH LOGIC (a database call, a computed value, a call to another service).

# DataFetcher -- The Underlying Concept

--> Under the hood, EVERY resolver -- whether it's a default property-matching one or a method you annotated with `@QueryMapping`/`@SchemaMapping` -- is registered with `graphql-java` as a **`DataFetcher`**: a functional interface with one method, `Object get(DataFetchingEnvironment environment)`, responsible for producing one field's value.
--> Spring for GraphQL's annotations (`@QueryMapping`, `@SchemaMapping`, etc.) are a convenience layer that AUTO-REGISTERS your annotated methods as `DataFetcher`s behind the scenes -- you rarely need to implement `DataFetcher` directly in a typical Spring Boot GraphQL app, but understanding that it's the underlying abstraction explains WHY resolvers are per-field, why `DataFetchingEnvironment` gives access to arguments/context/the parent object, and how third-party libraries (like a DataLoader integration) plug into the same mechanism.

```java
// What @SchemaMapping generates for you under the hood, conceptually:
DataFetcher<List<Review>> reviewsFetcher = environment -> {
    Product product = environment.getSource();   // the parent object
    return reviewService.findByProductId(product.getId());
};
```

--> **`DataFetchingEnvironment`** is the `graphql-java` equivalent of a Spring MVC `HttpServletRequest` -- it carries the field's arguments, the parent ("source") object, the GraphQL context, and the full query's `DataLoaderRegistry` (see below). Spring for GraphQL's `@Argument` and automatic parent-object injection are convenience wrappers around pulling data out of this environment manually.

# The N+1 Problem in GraphQL

--> This is the single most important PERFORMANCE gotcha in any non-trivial GraphQL server. Consider a query listing 50 products, each with its reviews:

```graphql
query {
    products(category: "electronics") {   # 1 query: fetch 50 products
        name
        reviews {                          # resolver called ONCE PER PRODUCT
            rating
        }
    }
}
```

--> If `Product.reviews`'s resolver does a naive `reviewRepository.findByProductId(product.getId())` per product, that's **1 query to fetch the products + 50 separate queries to fetch each product's reviews = 51 total database round trips** for one GraphQL request -- this is the N+1 problem, and it's structurally BUILT IN to how GraphQL resolves nested fields (one resolver call per parent item), unlike a REST endpoint where a developer naturally writes one JOIN-based query for the whole response by hand.
--> **Why this is worse in GraphQL than in typical REST/JPA code** -- REST's N+1 problem (covered in the Spring Data JPA material) is something a developer can accidentally trigger through lazy loading, but GraphQL's field-by-field, per-parent-item resolver model makes it the DEFAULT behavior for any non-trivial nested schema, not an edge case.

# DataLoader -- Batching and Caching Nested Fetches

--> **DataLoader** is the standard pattern (originating from Facebook's JavaScript `dataloader` library, ported to Java as `java-dataloader`, and integrated into `graphql-java`/Spring for GraphQL) for solving the N+1 problem. The core idea: instead of each resolver call immediately hitting the database, it registers "I need the reviews for product 7" into a BATCH, and the DataLoader waits until all resolvers for the CURRENT LEVEL of the query have registered their requests, then fires ONE batched call for everything at once.

```java
// A BatchLoader: takes a LIST of keys, returns a matching LIST of results, one DB call total
public class ReviewsByProductBatchLoader implements MappedBatchLoader<Long, List<Review>> {

    private final ReviewRepository reviewRepository;

    public ReviewsByProductBatchLoader(ReviewRepository reviewRepository) {
        this.reviewRepository = reviewRepository;
    }

    @Override
    public CompletionStage<Map<Long, List<Review>>> load(Set<Long> productIds) {
        // ONE query instead of N -- e.g. "SELECT * FROM reviews WHERE product_id IN (...)"
        List<Review> allReviews = reviewRepository.findByProductIdIn(productIds);
        Map<Long, List<Review>> grouped = allReviews.stream()
                .collect(Collectors.groupingBy(Review::getProductId));
        return CompletableFuture.completedFuture(grouped);
    }
}
```

```java
@Configuration
public class DataLoaderConfig {

    @Bean
    public BatchLoaderRegistry.RegistrationSpec<Long, List<Review>> reviewsLoader(
            BatchLoaderRegistry registry, ReviewRepository reviewRepository) {
        return registry.forName("reviewsByProduct")
                .registerMappedBatchLoader((productIds, env) -> {
                    List<Review> allReviews = reviewRepository.findByProductIdIn(productIds);
                    Map<Long, List<Review>> grouped = allReviews.stream()
                            .collect(Collectors.groupingBy(Review::getProductId));
                    return Mono.just(grouped);
                });
    }
}
```

```java
@Controller
public class ProductGraphQlController {

    @SchemaMapping(typeName = "Product", field = "reviews")
    public CompletableFuture<List<Review>> reviews(Product product, DataLoader<Long, List<Review>> loader) {
        // Registers this product's id for the CURRENT batch instead of fetching immediately.
        // graphql-java automatically dispatches all queued keys together at the end of the batch window.
        return loader.load(product.getId());
    }
}
```

--> **What "batching" buys you** -- instead of 50 separate `SELECT ... WHERE product_id = ?` calls, the DataLoader collects all 50 product IDs that resolvers asked for DURING THAT QUERY's execution, then issues ONE `SELECT ... WHERE product_id IN (7, 8, 9, ..., 56)` call, and hands each resolver back its own slice of the combined result -- turning 51 round trips into 2.
--> **DataLoader also CACHES within a single request** -- if two different resolvers in the SAME query happen to ask for the same key (e.g. two different fields both need `User` id 5), the DataLoader only fetches it once and reuses the cached result for the second ask. This cache is scoped to ONE request/execution -- it is NOT a long-lived, cross-request cache, and a fresh `DataLoaderRegistry` is created per GraphQL request by Spring for GraphQL's auto-configuration.
--> **Spring for GraphQL wires this for you** via `BatchLoaderRegistry`, auto-configured as a bean -- you register named batch loaders once at startup, and any `@SchemaMapping`/`@QueryMapping` method can request the matching `DataLoader<K, V>` as a method parameter, exactly as shown above.

# Testing GraphQL Endpoints -- GraphQlTester

--> Spring for GraphQL provides `GraphQlTester`, a fluent testing client analogous to `MockMvc`/`WebTestClient` for REST, letting you send a query document and assert on the JSON response path-by-path without spinning up a real HTTP server.

```java
@GraphQlTest(ProductGraphQlController.class)
class ProductGraphQlControllerTests {

    @Autowired
    private GraphQlTester graphQlTester;

    @Test
    void fetchesProductByid() {
        graphQlTester.document("""
                query {
                    product(id: "1") { name price }
                }
                """)
                .execute()
                .path("product.name").entity(String.class).isEqualTo("Keyboard");
    }
}
```

# Common Gotchas

--> **Forgetting the schema file location/naming** -- Spring for GraphQL auto-detects `.graphqls`/`.gqls` files under `src/main/resources/graphql/` by default; a schema placed elsewhere without adjusting `spring.graphql.schema.locations` simply won't be picked up, and the app fails to start with a clear "no schema found" style error.
--> **Writing `@SchemaMapping` resolvers that do direct DB calls with no batching** -- this is the #1 way GraphQL APIs quietly become slow in production; it works fine in development with a handful of test rows and falls over under a real dataset. Treat DataLoader as a DEFAULT, not an optimization to bolt on later, for any field returning a list of related entities.
--> **Mismatched method/field names silently failing** -- unlike `@PathVariable` (which errors loudly with a clear message on a name mismatch), a `@QueryMapping`/`@SchemaMapping` method whose name doesn't match the schema field can fail to wire up with a less obvious startup validation error -- always double-check `@QueryMapping("exactFieldName")` explicitly when in doubt.
--> **Confusing `type` and `input`** -- trying to use an `input` type as a return type (or vice-versa) is a schema-level type error caught at STARTUP (schema validation), not silently ignored -- a useful safety net, but it means schema mistakes surface as app-boot failures rather than runtime GraphQL errors.
--> **Assuming HTTP status codes mean anything for GraphQL errors** -- a malformed query argument or a resolver exception typically still returns HTTP `200 OK` with an `"errors"` array in the body; don't rely on status-code-based error handling in client code the way you would for REST.
--> **DataLoader cache leaking across requests** -- because the `DataLoaderRegistry` is (correctly) created fresh per request by Spring for GraphQL's defaults, don't try to manually share/reuse a `DataLoader` instance across requests for a "global cache" -- that's a correctness bug waiting to serve stale data to a different user's query.

# Best Practices Summary

--> Keep the SDL schema as the single source of truth, written by hand -- treat it with the same rigor as a REST API's OpenAPI spec.
--> Use `@QueryMapping`/`@MutationMapping` only for ROOT fields; use `@SchemaMapping` for everything else, and lean on default property-matching resolvers when no real logic is needed.
--> Treat every list-of-related-entities field as a DataLoader candidate from day one -- don't wait for an N+1 problem to show up in production metrics.
--> Keep `@SchemaMapping`/`@QueryMapping` methods thin -- delegate to a `@Service` layer, exactly like the Controller -> Service -> Repository layering used for REST controllers.
--> Use `GraphQlTester` for controller-level tests instead of standing up a full HTTP client -- faster feedback, same idea as `MockMvc` for REST.
--> Remember mutations execute sequentially and queries can execute in parallel -- design resolver side effects (or lack thereof) accordingly.
