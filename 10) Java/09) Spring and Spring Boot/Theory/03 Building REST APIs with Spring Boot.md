# Why REST APIs Need More Than Just "A Method That Returns Data"

--> A REST API isn't just "some Java methods reachable over HTTP" -- it's a contract: specific URLs map to specific resources, HTTP methods express INTENT (read vs create vs replace vs partially update vs delete), status codes communicate outcome without the client having to parse a message string, and the body format (almost always JSON) is negotiated and consistent. Spring Boot's web stack (Spring MVC under the hood) gives you annotations that map directly onto every piece of that contract.
--> Everything in this file builds on Spring's dependency injection and bean wiring (IoC container, `@Component`/`@Service`/`@Repository` stereotypes) covered in earlier chapters -- a `@RestController` is still just a Spring bean, it's simply annotated to also handle HTTP requests.

# @RestController vs @Controller (and @ResponseBody) -- What Changes

--> **`@Controller`** is the original Spring MVC annotation, built for returning VIEW NAMES (e.g. a Thymeleaf template like `"product-list"`) that Spring resolves to an HTML page -- the method return value is treated as a logical view name, not response data.
--> **`@ResponseBody`** flips that behavior on a per-method (or per-class) basis -- it tells Spring "don't treat this return value as a view name, serialize it directly into the HTTP response body" (via Jackson for JSON, by default). On a `@Controller`, you'd stick `@ResponseBody` on every single handler method that should return raw data instead of a view.
--> **`@RestController`** is a convenience meta-annotation: it's literally `@Controller` + `@ResponseBody` combined at the CLASS level, so every method in the class returns response data by default -- no need to repeat `@ResponseBody` on every method. This is why virtually every REST API controller in a Spring Boot app is annotated `@RestController`, not `@Controller`.

```java
// Old style -- @Controller + @ResponseBody on every method
@Controller
public class ProductController {
    @GetMapping("/products/{id}")
    @ResponseBody                          // without this, Spring tries to resolve "product" as a VIEW NAME and fails/404s
    public Product getProduct(@PathVariable Long id) {
        return productService.findById(id);
    }
}

// REST style -- @RestController does @ResponseBody for the whole class automatically
@RestController
public class ProductController {
    @GetMapping("/products/{id}")
    public Product getProduct(@PathVariable Long id) {
        return productService.findById(id);   // serialized straight to JSON, no extra annotation needed
    }
}
```

--> **Deep Dive -- what actually performs the serialization** -- when a handler method's return value needs to become an HTTP body, Spring MVC hands it to an `HttpMessageConverter`. For JSON, that's `MappingJackson2HttpMessageConverter`, backed by the Jackson library (`ObjectMapper`), auto-configured onto the classpath the moment you include `spring-boot-starter-web`. This converter is also what does the REVERSE job -- turning incoming JSON request bodies back into Java objects (covered under `@RequestBody` below). Mixing `@Controller` (view-based) and REST endpoints in the same class is legal but confusing -- most real projects keep them as fully separate classes.

# @RequestMapping and the Specialized Shorthand Annotations

--> **`@RequestMapping`** is the general-purpose, original annotation for mapping a URL (and optionally an HTTP method, headers, params, produced/consumed content types) to a handler method or an entire controller class. It's flexible but verbose when you have to specify the method every time.
--> The **shorthand annotations** (`@GetMapping`, `@PostMapping`, `@PutMapping`, `@PatchMapping`, `@DeleteMapping`) are each just `@RequestMapping` pre-locked to one HTTP method -- they exist purely for readability and are what you'll use 95% of the time in real code.

```java
// Verbose, general-purpose form
@RequestMapping(value = "/products", method = RequestMethod.GET)
public List<Product> getAllProducts() { ... }

// Equivalent, idiomatic shorthand
@GetMapping("/products")
public List<Product> getAllProducts() { ... }
```

| Annotation | HTTP Method | Typical Use | Idempotent? |
|---|---|---|---|
| `@GetMapping` | GET | Read one or many resources | Yes |
| `@PostMapping` | POST | Create a new resource | No |
| `@PutMapping` | PUT | Replace a resource entirely | Yes |
| `@PatchMapping` | PATCH | Partially update a resource | Not guaranteed, but usually treated as yes |
| `@DeleteMapping` | DELETE | Remove a resource | Yes |

--> **Idempotent** means "calling it once has the same end-state effect as calling it 10 times." `PUT /products/5` with the same body ten times always leaves product 5 in the same final state, so it's idempotent. `POST /products` ten times creates ten new products -- not idempotent. This distinction matters for retry logic: it's generally SAFE for a client (or a load balancer, or an HTTP library) to automatically retry a failed idempotent request, but automatically retrying a `POST` risks duplicate resource creation.
--> **Class-level + method-level combination** -- `@RequestMapping("/api/products")` on the class combines with `@GetMapping("/{id}")` on the method to form the full path `/api/products/{id}`. This is the standard way to avoid repeating the resource's base path on every single method.

```java
@RestController
@RequestMapping("/api/products")          // shared base path for every method below
public class ProductController {

    @GetMapping                            // GET /api/products
    public List<Product> getAll() { ... }

    @GetMapping("/{id}")                   // GET /api/products/{id}
    public Product getOne(@PathVariable Long id) { ... }

    @PostMapping                           // POST /api/products
    public Product create(@RequestBody Product product) { ... }

    @PutMapping("/{id}")                   // PUT /api/products/{id}
    public Product update(@PathVariable Long id, @RequestBody Product product) { ... }

    @DeleteMapping("/{id}")                // DELETE /api/products/{id}
    public void delete(@PathVariable Long id) { ... }
}
```

# Path Variables (@PathVariable) and Query Parameters (@RequestParam)

--> **`@PathVariable`** pulls a value out of the URL PATH itself -- it's for identifying a specific resource, e.g. the `{id}` in `/products/{id}`. By default Spring matches the `{name}` placeholder to a method parameter of the same name (Java's `-parameters` compiler flag needs to be on, which Spring Boot's default build setup already enables) -- you can also be explicit with `@PathVariable("id") Long productId` if the names differ.
--> **`@RequestParam`** pulls a value out of the URL's QUERY STRING (`?key=value&key2=value2`) -- it's for filtering, sorting, paging, and other OPTIONAL modifiers to a request, not for identifying a specific resource.

```java
// GET /api/products/42
@GetMapping("/{id}")
public Product getOne(@PathVariable Long id) {
    return productService.findById(id);
}

// GET /api/products?category=electronics&page=0&size=20&inStock=true
@GetMapping
public List<Product> search(
        @RequestParam(required = false) String category,          // optional, no default -- null if absent
        @RequestParam(defaultValue = "0") int page,                 // optional, defaults to 0 if absent
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(name = "inStock", required = false) Boolean inStock  // explicit name mapping
) {
    return productService.search(category, page, size, inStock);
}
```

--> **`required` vs `defaultValue`** -- `required = true` is the DEFAULT for `@RequestParam`, meaning a missing query param throws a `400 Bad Request` (`MissingServletRequestParameterException`) unless you either set `required = false` (parameter becomes `null` for objects, or you must use a wrapper type like `Integer`/`Boolean` instead of a primitive `int`/`boolean` since primitives can't be null) or provide a `defaultValue` (which implicitly makes it optional -- you don't need to also set `required = false`).
--> **Gotcha -- primitives can't be `null`** -- `@RequestParam(required = false) int page` will throw at startup or on a missing param, because there's no such thing as a null `int`. Either give it a `defaultValue`, or use the boxed type `Integer` and check for null in the method body.

# Request Body Binding (@RequestBody) and Automatic JSON Conversion

--> **`@RequestBody`** tells Spring "take the raw HTTP request body (typically JSON) and deserialize it into this Java object" -- it's the mirror image of what `@RestController`/`@ResponseBody` does on the way OUT. Without it, Spring has no idea a method parameter should be populated from the request body at all, and it'll try (and fail) to resolve it some other way.
--> **How the JSON <-> Java mapping actually happens** -- Jackson's `ObjectMapper` matches JSON keys to Java fields by NAME (using either the no-args constructor + setters, or an all-args constructor if using Java records/Lombok's `@AllArgsConstructor` with matching parameter names). You don't write any parsing code yourself -- it's entirely reflection + convention-driven, configured automatically the moment `spring-boot-starter-web` is on the classpath.

```java
public class ProductRequest {
    private String name;
    private BigDecimal price;
    private String category;
    // getters + setters (or use a Java record / Lombok @Data to avoid boilerplate)
}

@PostMapping
public Product create(@RequestBody ProductRequest request) {
    // Incoming JSON:  {"name": "Keyboard", "price": 49.99, "category": "electronics"}
    // Jackson matches "name" -> setName(), "price" -> setPrice(), "category" -> setCategory()
    return productService.create(request);
}
```

--> **Deep Dive -- customizing the JSON <-> Java field mapping** -- when the JSON key doesn't match the Java field name (common when the API contract uses `snake_case` or a different word entirely than your Java convention), `@JsonProperty("json_key_name")` on the field pins the exact mapping in both directions (serialization AND deserialization):

```java
public class ProductRequest {
    @JsonProperty("product_name")     // JSON body uses "product_name", Java field stays "name"
    private String name;

    @JsonProperty("unit_price")
    private BigDecimal price;
}
```

--> You can also configure a GLOBAL naming strategy (e.g. auto-convert `camelCase` Java fields to `snake_case` JSON) via `spring.jackson.property-naming-strategy=SNAKE_CASE` in `application.properties`, rather than annotating every field individually -- worth doing up front if your whole API needs to speak `snake_case` to match a broader company convention.

# ResponseEntity -- Controlling Status, Headers, and Body Explicitly

--> Returning a plain object (like `Product` or `List<Product>`) from a handler method always results in an HTTP `200 OK` on success -- Spring has no way to know you meant `201 Created` after a `POST`, or `404 Not Found` when a lookup comes up empty, unless you tell it. `ResponseEntity<T>` wraps the body together with an explicit status code and (optionally) custom headers, giving you full control over the response.

```java
@GetMapping("/{id}")
public ResponseEntity<Product> getOne(@PathVariable Long id) {
    return productService.findById(id)
            .map(ResponseEntity::ok)                       // 200 OK + body, if found
            .orElse(ResponseEntity.notFound().build());    // 404, no body, if empty Optional
}

@PostMapping
public ResponseEntity<Product> create(@Valid @RequestBody ProductRequest request) {
    Product created = productService.create(request);
    URI location = URI.create("/api/products/" + created.getId());
    return ResponseEntity
            .created(location)      // 201 Created + sets the "Location" header to the new resource's URL
            .body(created);
}

@DeleteMapping("/{id}")
public ResponseEntity<Void> delete(@PathVariable Long id) {
    productService.delete(id);
    return ResponseEntity.noContent().build();   // 204 No Content -- success, nothing to send back
}
```

--> **Plain object return vs `ResponseEntity`** -- returning a bare object is fine for the simple "always 200, always this shape" case, but the moment your endpoint has more than one possible OUTCOME (found vs not found, created vs conflict, etc.), `ResponseEntity` is the correct tool -- it lets the SAME method express different status codes on different code paths, which a bare return type structurally cannot do.
--> **`ResponseEntity.ok()`, `.created(uri)`, `.noContent()`, `.notFound()`, `.badRequest()`, `.status(HttpStatus.X)`** are all builder shortcuts -- `.status(HttpStatus.CONFLICT).body(errorPayload)` is the general escape hatch for any status not covered by a named shortcut. You can also chain `.header("X-Custom-Header", "value")` before `.body(...)` to attach arbitrary response headers.

# DTOs -- Why Not to Expose JPA Entities Directly in APIs

--> A **DTO (Data Transfer Object)** is a plain object shaped specifically for what crosses the API boundary -- it is DELIBERATELY separate from your JPA `@Entity` class, even when the fields look almost identical at first glance. Returning `@Entity` objects directly from a `@RestController` is a very common beginner mistake that works fine in a demo and then causes real problems the moment the app grows.
--> **Why not just return the entity?**
  - --> **Over-exposure** -- an entity often carries fields that should never leave the server: password hashes, internal audit columns, foreign-key-heavy relationship objects, soft-delete flags. A DTO is an explicit ALLOWLIST of exactly what's safe to send.
  - --> **Lazy-loading serialization crashes** -- JPA relationships (`@OneToMany`, `@ManyToOne` with `FetchType.LAZY`) are represented by proxy objects that only load their data when accessed WITHIN an active database session (a Hibernate `Session`/persistence context). By the time Jackson tries to serialize that entity into JSON (outside the transaction, in the web layer), the session is often already closed, throwing `LazyInitializationException` or, worse, silently triggering the N+1 query problem if the session happens to still be open. DTOs sidestep this entirely because they hold already-extracted plain values, not lazy proxies.
  - --> **Coupling your API contract to your database schema** -- if the entity IS the API response, then renaming a database column or restructuring a relationship becomes a BREAKING API CHANGE for every client, even though conceptually the API contract didn't need to change at all. A DTO layer decouples "how I store this" from "what I promise to clients."
  - --> **Different shapes for input vs output** -- a `POST` request body typically shouldn't include a server-generated `id`, `createdAt`, or `updatedAt` -- but a response DEFINITELY should include them. One combined class can't cleanly represent both without a pile of `null`-when-creating fields.
--> **Request DTO vs Response DTO pattern** -- separate classes for what comes IN vs what goes OUT, even for the "same" resource:

```java
// Request DTO -- only fields the CLIENT is allowed to set. No id, no timestamps.
public class ProductRequest {
    @NotBlank
    private String name;
    @NotNull @Min(0)
    private BigDecimal price;
    private String category;
    // getters/setters
}

// Response DTO -- includes server-generated fields, excludes anything internal-only.
public class ProductResponse {
    private Long id;
    private String name;
    private BigDecimal price;
    private String category;
    private Instant createdAt;
    // getters/setters (no setters needed if built via constructor only)
}
```

--> **Mapping between entity and DTO** -- somewhere, something has to copy fields from `Product` (entity) to `ProductResponse` (DTO) and from `ProductRequest` (DTO) to `Product` (entity). Two common approaches:
  - --> **Manual mapping** -- a constructor, static factory method, or dedicated `ProductMapper` class that does the field-by-field copying by hand. More verbose, but explicit, easy to debug, and zero magic -- often the better choice for smaller projects or when the mapping has real logic in it (e.g. combining two entity fields into one DTO field).
  - --> **MapStruct** -- an annotation-processor library that GENERATES the mapping code at compile time from an interface you declare (`@Mapper interface ProductMapper { ProductResponse toDto(Product entity); }`). It eliminates the boilerplate of manual mapping while still producing plain, debuggable, reflection-free Java code (unlike some older runtime-reflection mapping libraries) -- worth adopting once a project has enough entities/DTOs that hand-writing mappers becomes repetitive and error-prone.

```java
// Manual mapping example
public class ProductMapper {
    public static ProductResponse toResponse(Product entity) {
        ProductResponse dto = new ProductResponse();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setPrice(entity.getPrice());
        dto.setCategory(entity.getCategory());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }

    public static Product toEntity(ProductRequest dto) {
        Product entity = new Product();
        entity.setName(dto.getName());
        entity.setPrice(dto.getPrice());
        entity.setCategory(dto.getCategory());
        return entity;                     // id/createdAt are set by the DB / JPA, not here
    }
}
```

# Validation -- Bean Validation, @Valid, and Global Exception Handling

--> **Bean Validation** (JSR 380, implemented by Hibernate Validator, pulled in via `spring-boot-starter-validation`) lets you declare constraints as ANNOTATIONS directly on DTO fields, instead of writing manual `if` checks scattered through service methods.

```text
@NotNull      -- value must not be null (but CAN be empty string / empty collection)
@NotBlank     -- (String only) must not be null AND not just whitespace
@NotEmpty     -- must not be null AND not empty (works on String, Collection, Map, arrays)
@Size(min=, max=)  -- length/size bounds -- Strings, Collections, arrays
@Min / @Max   -- numeric lower/upper bound (inclusive)
@Email        -- must be a syntactically valid email format
@Positive / @PositiveOrZero / @Negative -- numeric sign constraints
@Past / @Future -- date/time must be before/after "now"
@Pattern(regexp = "...") -- must match a regular expression
```

```java
public class ProductRequest {
    @NotBlank(message = "Product name is required")
    private String name;

    @NotNull(message = "Price is required")
    @Min(value = 0, message = "Price cannot be negative")
    private BigDecimal price;

    @Size(max = 50, message = "Category name too long")
    private String category;

    @Email(message = "Contact email must be valid")
    private String contactEmail;
}
```

--> **`@Valid` on the controller method parameter** is what actually TRIGGERS validation -- the annotations on the DTO are inert metadata until something asks Spring to check them. Put `@Valid` immediately before `@RequestBody` in the handler method signature:

```java
@PostMapping
public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
    // If validation fails, this method body NEVER RUNS --
    // Spring throws MethodArgumentNotValidException before the handler is invoked.
    Product created = productService.create(request);
    return ResponseEntity.created(...).body(ProductMapper.toResponse(created));
}
```

--> **What happens when validation fails** -- Spring throws `MethodArgumentNotValidException`, which, if left UNHANDLED, results in a default Spring Boot error response: `400 Bad Request` with a generic JSON error body that's often not very client-friendly (it dumps internal detail, or is inconsistent across different failure types). This is where **`@ControllerAdvice` + `@ExceptionHandler`** comes in -- a GLOBAL exception handler that intercepts exceptions thrown by ANY controller in the app and converts them into a consistent, well-shaped error response.

```java
@RestControllerAdvice   // @ControllerAdvice + @ResponseBody combined, same relationship as @RestController
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());   // e.g. "price" -> "Price cannot be negative"
        }
        return ResponseEntity.badRequest().body(errors);
    }

    @ExceptionHandler(ProductNotFoundException.class)     // a custom exception you define
    public ResponseEntity<Map<String, String>> handleNotFound(ProductNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)                     // catch-all fallback for anything unexpected
    public ResponseEntity<Map<String, String>> handleGeneric(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Something went wrong"));
    }
}
```

--> **Why `@ControllerAdvice` beats try/catch in every controller method** -- without it, every single handler that can fail needs its own try/catch, duplicating the same error-shaping logic across dozens of methods. `@ControllerAdvice` centralizes it ONCE, applies it to every controller in the application by default, and keeps the controller methods themselves focused purely on the happy path.
--> **Deep Dive -- exception handler matching specificity** -- Spring picks the MOST SPECIFIC matching `@ExceptionHandler` for a thrown exception, walking up the exception's class hierarchy -- a `ProductNotFoundException` handler wins over a generic `Exception` handler if both are declared, so order the handlers from specific to general in your own head (declaration order in the file doesn't matter, specificity does).

# HTTP Status Code Conventions for REST

| Code | Meaning | Typical Business Scenario |
|---|---|---|
| `200 OK` | Success, response has a body | Successful GET, successful PUT/PATCH that returns the updated resource |
| `201 Created` | Success, new resource created | Successful POST -- pair with a `Location` header pointing to the new resource |
| `204 No Content` | Success, nothing to return | Successful DELETE, or a PUT/PATCH where you don't return the body |
| `400 Bad Request` | Client sent malformed or invalid data | Failed Bean Validation, malformed JSON, missing required query param |
| `401 Unauthorized` | Client isn't authenticated | Missing/invalid credentials (covered in the Security chapter) |
| `403 Forbidden` | Client is authenticated but not allowed | Authenticated user lacks permission for this action |
| `404 Not Found` | Resource doesn't exist | GET/PUT/DELETE on an id that isn't in the database |
| `409 Conflict` | Request conflicts with current state | Duplicate unique field (e.g. creating a product with a SKU that already exists), optimistic locking version mismatch |
| `500 Internal Server Error` | Unexpected server-side failure | Unhandled exception, bug, downstream dependency failure |

--> **The general principle** -- 2xx means "it worked," 4xx means "the CLIENT needs to fix something about the request," 5xx means "the SERVER failed regardless of what the client sent." Getting this right matters because clients (and monitoring/alerting systems) often behave DIFFERENTLY based on the status code class alone -- a 4xx might be silently handled/retried differently than a 5xx, which usually pages someone.
--> **Common mistake** -- returning `200 OK` with an error message embedded in the JSON body ("soft 200s"). This defeats the entire purpose of HTTP status codes and forces every client to parse the body just to know if the call succeeded -- always prefer the correct status code over a 200-with-an-error-flag.

# Content Negotiation Basics

--> **Content negotiation** is how client and server agree on the FORMAT of the response body -- driven primarily by the `Accept` request header (what formats the client is willing to receive) and, for requests with a body, the `Content-Type` header (what format the client is SENDING).
--> Spring Boot defaults to JSON for both directions the moment `spring-boot-starter-web` is on the classpath (via the Jackson message converter registered automatically) -- you don't have to configure anything to "turn on JSON." If a client sends `Accept: application/xml` and no XML converter is registered, Spring responds `406 Not Acceptable` rather than silently falling back to JSON.
--> You CAN restrict what a specific endpoint produces/consumes explicitly: `@GetMapping(value = "/products", produces = "application/json")` or `@PostMapping(consumes = "application/json")` -- mostly useful when an endpoint genuinely supports multiple formats and you want to be explicit, or when you want a clean 406/415 instead of accidental mismatched behavior. In the vast majority of everyday REST APIs, this is left at the default (JSON in, JSON out) and never touched.

# Worked Example -- Layered CRUD Flow (Controller -> Service -> Repository)

--> A well-structured Spring Boot REST endpoint is rarely "controller talks directly to the database" -- it flows through three layers, each with a distinct responsibility, which keeps the code testable and keeps HTTP concerns (status codes, DTOs) completely separate from business logic and persistence:

```text
HTTP request
     |
     v
@RestController   -- parses request (path vars, query params, @RequestBody), calls the Service,
     |                wraps the Service's result into a ResponseEntity with the right status code.
     |                Knows about DTOs and HTTP. Knows NOTHING about SQL or JPA.
     v
Service (@Service) -- business logic: validation beyond simple annotations, orchestrating
     |                multiple repository calls, enforcing business rules (e.g. "can't delete
     |                a product that has pending orders"), mapping entity <-> DTO.
     |                Knows nothing about HTTP status codes or the web layer.
     v
Repository (@Repository, e.g. Spring Data JPA) -- pure persistence: find/save/delete against
     |                the database. Knows nothing about business rules or HTTP.
     v
Database
```

```java
// Controller -- thin. Delegates everything, just shapes the HTTP response.
@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final ProductService productService;

    public ProductController(ProductService productService) {   // constructor injection
        this.productService = productService;
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        ProductResponse created = productService.create(request);
        return ResponseEntity.created(URI.create("/api/products/" + created.getId())).body(created);
    }
}

// Service -- business logic + entity/DTO mapping. No HTTP awareness at all.
@Service
public class ProductService {
    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public ProductResponse create(ProductRequest request) {
        if (productRepository.existsByName(request.getName())) {
            throw new DuplicateProductException("Product already exists: " + request.getName());
        }
        Product entity = ProductMapper.toEntity(request);
        Product saved = productRepository.save(entity);
        return ProductMapper.toResponse(saved);
    }
}

// Repository -- pure persistence, Spring Data JPA generates the implementation for you.
public interface ProductRepository extends JpaRepository<Product, Long> {
    boolean existsByName(String name);
}
```

--> **Why bother layering for something this small?** For a toy example it looks like ceremony, but the payoff shows up as the app grows: the Service layer is trivially UNIT TESTABLE without spinning up any web server or database (mock the repository), the Controller stays a thin, boring translation layer that rarely needs to change, and business rules live in exactly ONE place instead of being duplicated across multiple entry points (REST controller, a scheduled batch job, a CLI tool) that might all need to create a product.

# Common Gotchas

--> **Forgetting `@RequestBody`** -- without it, Spring tries to bind the incoming object from query parameters / form data instead of the JSON body, silently resulting in an object with all-null/default fields rather than an obvious error. If a POST/PUT endpoint's fields are always coming back empty, this is the first thing to check.
--> **Mismatched JSON field names vs Java field names** -- if the client sends `{"productName": "Keyboard"}` but the Java field is `name`, Jackson silently IGNORES the unmatched JSON key by default (rather than erroring) and leaves the Java field at its default value (`null`) -- use `@JsonProperty` to pin the exact expected name, or align naming conventions up front. Enabling `spring.jackson.deserialization.fail-on-unknown-properties=true` (or the reverse, aware it's not the default) can help catch mismatches loudly during development instead of silently.
--> **Returning entities directly, hitting lazy-loading serialization errors** -- covered in depth above under DTOs; the fix is always "map to a DTO before returning," never "just make the fetch type `EAGER`" (which just shifts the pain into an N+1 query problem instead).
--> **Not validating input** -- without `@Valid` + Bean Validation annotations, malformed data (negative prices, missing names, invalid emails) sails straight into the service layer and often the database, where it's much harder to trace back to "the API let this through" and much more likely to corrupt downstream logic or reports.
--> **Using primitive types with optional `@RequestParam`** -- `@RequestParam(required = false) int page` fails to compile-safety-check and blows up at request time; use `Integer` or supply `defaultValue`.
--> **Forgetting that `@PathVariable` name-matching needs parameter name info** -- if the project's compiler doesn't preserve parameter names (rare with Spring Boot's default Maven/Gradle setup, but possible with certain custom build configs), `@PathVariable Long id` fails to match `{id}` in the path unless given explicitly as `@PathVariable("id") Long id`. If unsure, just always specify the name explicitly -- it costs nothing and removes an entire class of subtle bugs.
--> **Swallowing exceptions into a bare 500** -- a catch-all `@ExceptionHandler(Exception.class)` returning `500` for EVERYTHING (including things that should be `400` or `404`) hides real problems from API consumers and from your own monitoring -- always handle specific, expected exceptions first, and reserve the generic 500 fallback for truly unexpected failures.

# Best Practices Summary

--> Use `@RestController` for JSON APIs, reserve `@Controller` for server-rendered view templates -- don't mix the two concerns in one class.
--> Use the specific shorthand mapping annotations (`@GetMapping`, `@PostMapping`, etc.) over generic `@RequestMapping` for readability.
--> `@PathVariable` identifies a specific resource; `@RequestParam` filters/modifies a collection request -- don't blur the two.
--> Always use DTOs at the API boundary -- never return `@Entity` objects directly, and never accept an entity as a `@RequestBody` type either.
--> Always pair `@Valid` with Bean Validation annotations on request DTOs -- don't rely on manual `if` checks for basic constraints.
--> Centralize error shaping in one `@RestControllerAdvice`, with specific `@ExceptionHandler` methods ordered from specific to general.
--> Return `ResponseEntity` whenever an endpoint has more than one possible outcome/status code -- reserve bare object returns for the simplest, single-outcome endpoints.
--> Follow HTTP status conventions precisely (201 on create, 204 on delete, 404 vs 400 vs 409 chosen deliberately) -- never smuggle errors through a 200.
--> Keep the Controller -> Service -> Repository layering even in small projects -- it costs little and pays off immediately once tests or a second entry point show up.
