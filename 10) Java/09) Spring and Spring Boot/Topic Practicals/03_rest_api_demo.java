/**
 * 03_rest_api_demo.java
 *
 * Demonstrates, with illustrative Spring Boot code:
 *     1. A @RestController with GET/POST/PUT/DELETE endpoints for a "Product" resource
 *     2. Request/response DTO classes using Bean Validation annotations (@NotBlank, @Min, @Size, @Email)
 *     3. @Valid triggering validation on an incoming @RequestBody
 *     4. ResponseEntity usage to return 200 / 201 / 204 / 404 / 409 explicitly
 *     5. A minimal Service layer the controller delegates to (Controller -> Service -> "Repository")
 *     6. An in-memory Map used as fake persistence, purely for illustration
 *     7. A @RestControllerAdvice global exception handler translating exceptions into clean JSON errors
 *
 * Covers Theory chapter:
 *     10) Java/09) Spring and Spring Boot/Theory/03 Building REST APIs with Spring Boot.md
 *
 * IMPORTANT -- this file is illustrative, NOT a standalone runnable program.
 * It requires a real Spring Boot project on the classpath to compile/run, specifically:
 *     - spring-boot-starter-web          (Spring MVC, embedded Tomcat, Jackson JSON support)
 *     - spring-boot-starter-validation   (Bean Validation / Hibernate Validator, for @Valid + @NotBlank etc.)
 *
 * Run (in a real Spring Boot project, after wiring this into src/main/java/... and adding
 * a @SpringBootApplication class with a main() method):
 *     mvn spring-boot:run
 *   or
 *     ./gradlew bootRun
 *
 * Example requests once the app is running on the default port (8080):
 *     curl -X GET  http://localhost:8080/api/products
 *     curl -X GET  http://localhost:8080/api/products/1
 *     curl -X POST http://localhost:8080/api/products \
 *          -H "Content-Type: application/json" \
 *          -d "{\"name\":\"Keyboard\",\"price\":49.99,\"category\":\"electronics\"}"
 *     curl -X PUT  http://localhost:8080/api/products/1 \
 *          -H "Content-Type: application/json" \
 *          -d "{\"name\":\"Mechanical Keyboard\",\"price\":79.99,\"category\":\"electronics\"}"
 *     curl -X DELETE http://localhost:8080/api/products/1
 */

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

// ---------------------------------------------------------------------------
// 1) Domain "entity" -- stands in for what would normally be a @Entity/JPA class.
//    Kept deliberately separate from the DTOs below -- never returned directly
//    from the controller (see the Theory file's "DTOs" section for why).
// ---------------------------------------------------------------------------

class Product {
    private Long id;
    private String name;
    private BigDecimal price;
    private String category;
    private Instant createdAt;

    public Product(Long id, String name, BigDecimal price, String category, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.category = category;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public Instant getCreatedAt() { return createdAt; }
}

// ---------------------------------------------------------------------------
// 2) Request DTO -- only fields the CLIENT is allowed to send.
//    No id, no createdAt -- those are server-assigned. Bean Validation
//    annotations declare the constraints; they do nothing on their own until
//    a handler method uses @Valid to trigger them.
// ---------------------------------------------------------------------------

class ProductRequest {

    @NotBlank(message = "Product name is required")
    @Size(max = 100, message = "Product name must be 100 characters or fewer")
    private String name;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Price cannot be negative")
    private BigDecimal price;

    @NotBlank(message = "Category is required")
    private String category;

    // Not every Product needs a contact email -- shown here just to demonstrate @Email.
    @Email(message = "Contact email must be a valid email address")
    private String contactEmail;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }
}

// ---------------------------------------------------------------------------
// 3) Response DTO -- includes server-generated fields (id, createdAt),
//    excludes anything internal-only. This is what actually gets serialized
//    to JSON and sent back to the client.
// ---------------------------------------------------------------------------

class ProductResponse {
    private final Long id;
    private final String name;
    private final BigDecimal price;
    private final String category;
    private final Instant createdAt;

    public ProductResponse(Long id, String name, BigDecimal price, String category, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.category = category;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public BigDecimal getPrice() { return price; }
    public String getCategory() { return category; }
    public Instant getCreatedAt() { return createdAt; }
}

// ---------------------------------------------------------------------------
// 4) Manual entity <-> DTO mapper. A real project with many entities might
//    replace this with MapStruct-generated mappers, but manual mapping is
//    explicit, easy to debug, and perfectly fine at this scale.
// ---------------------------------------------------------------------------

final class ProductMapper {
    private ProductMapper() { }

    static Product toEntity(Long id, ProductRequest dto) {
        return new Product(id, dto.getName(), dto.getPrice(), dto.getCategory(), Instant.now());
    }

    static ProductResponse toResponse(Product entity) {
        return new ProductResponse(
                entity.getId(), entity.getName(), entity.getPrice(),
                entity.getCategory(), entity.getCreatedAt());
    }
}

// ---------------------------------------------------------------------------
// 5) Custom exceptions -- specific, meaningful failure types instead of
//    letting generic RuntimeExceptions bubble up unhandled. The global
//    exception handler below maps each of these to the correct HTTP status.
// ---------------------------------------------------------------------------

class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(Long id) {
        super("Product not found with id: " + id);
    }
}

class DuplicateProductException extends RuntimeException {
    public DuplicateProductException(String name) {
        super("A product named '" + name + "' already exists");
    }
}

// ---------------------------------------------------------------------------
// 6) Service layer interface + implementation. The controller only ever
//    talks to this interface -- it knows nothing about how products are
//    actually stored (in-memory Map here, a real database via Spring Data
//    JPA in a real project). This is what keeps the service unit-testable
//    without spinning up a web server.
// ---------------------------------------------------------------------------

interface ProductService {
    List<ProductResponse> findAll();
    ProductResponse findById(Long id);
    ProductResponse create(ProductRequest request);
    ProductResponse update(Long id, ProductRequest request);
    void delete(Long id);
}

/**
 * In-memory implementation standing in for a real @Repository-backed service.
 * In a real project this would be @Service, injecting a Spring Data JPA
 * ProductRepository instead of holding a Map directly -- the Map here is
 * ONLY a stand-in so this file is self-contained and illustrative.
 */
@Service
class InMemoryProductService implements ProductService {

    // Fake "database" -- a real project would delegate this to a JpaRepository.
    private final Map<Long, Product> store = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong(1);

    @Override
    public List<ProductResponse> findAll() {
        List<ProductResponse> results = new ArrayList<>();
        for (Product product : store.values()) {
            results.add(ProductMapper.toResponse(product));
        }
        return results;
    }

    @Override
    public ProductResponse findById(Long id) {
        Product product = store.get(id);
        if (product == null) {
            throw new ProductNotFoundException(id);
        }
        return ProductMapper.toResponse(product);
    }

    @Override
    public ProductResponse create(ProductRequest request) {
        boolean nameTaken = store.values().stream()
                .anyMatch(p -> p.getName().equalsIgnoreCase(request.getName()));
        if (nameTaken) {
            throw new DuplicateProductException(request.getName());   // -> mapped to 409 Conflict
        }
        Long newId = idSequence.getAndIncrement();
        Product entity = ProductMapper.toEntity(newId, request);
        store.put(newId, entity);
        return ProductMapper.toResponse(entity);
    }

    @Override
    public ProductResponse update(Long id, ProductRequest request) {
        Product existing = store.get(id);
        if (existing == null) {
            throw new ProductNotFoundException(id);
        }
        existing.setName(request.getName());
        existing.setPrice(request.getPrice());
        existing.setCategory(request.getCategory());
        return ProductMapper.toResponse(existing);
    }

    @Override
    public void delete(Long id) {
        if (!store.containsKey(id)) {
            throw new ProductNotFoundException(id);
        }
        store.remove(id);
    }
}

// ---------------------------------------------------------------------------
// 7) The REST controller -- deliberately THIN. It parses the request
//    (@PathVariable, @RequestBody), delegates all real work to the Service,
//    and shapes the HTTP response (status code, Location header) via
//    ResponseEntity. No business logic, no persistence code, lives here.
// ---------------------------------------------------------------------------

@RestController                            // == @Controller + @ResponseBody on every method
@RequestMapping("/api/products")           // shared base path for every handler below
class ProductController {

    private final ProductService productService;

    // Constructor injection -- Spring wires the ProductService bean in automatically.
    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    // GET /api/products -- always 200 OK, body is a (possibly empty) list.
    @GetMapping
    public ResponseEntity<List<ProductResponse>> getAll() {
        return ResponseEntity.ok(productService.findAll());
    }

    // GET /api/products/{id} -- 200 if found; ProductNotFoundException (-> 404) if not.
    // Note @PathVariable matches "{id}" to the "id" parameter by name.
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getOne(@PathVariable Long id) {
        return ResponseEntity.ok(productService.findById(id));
    }

    // GET /api/products/search?category=electronics&minPrice=10 -- demonstrates
    // @RequestParam with optional params and a default value.
    @GetMapping("/search")
    public ResponseEntity<List<ProductResponse>> search(
            @RequestParam(required = false) String category,
            @RequestParam(name = "minPrice", defaultValue = "0") BigDecimal minPrice) {

        List<ProductResponse> filtered = new ArrayList<>();
        for (ProductResponse p : productService.findAll()) {
            boolean matchesCategory = (category == null) || category.equalsIgnoreCase(p.getCategory());
            boolean matchesPrice = p.getPrice().compareTo(minPrice) >= 0;
            if (matchesCategory && matchesPrice) {
                filtered.add(p);
            }
        }
        return ResponseEntity.ok(filtered);
    }

    // POST /api/products -- 201 Created + Location header pointing at the new resource.
    // @Valid triggers Bean Validation on the incoming ProductRequest BEFORE this
    // method body runs at all -- if validation fails, MethodArgumentNotValidException
    // is thrown and this method is never entered (see GlobalExceptionHandler below).
    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        ProductResponse created = productService.create(request);
        URI location = URI.create("/api/products/" + created.getId());
        return ResponseEntity.created(location).body(created);
    }

    // PUT /api/products/{id} -- full replace, 200 OK with the updated resource.
    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request) {
        ProductResponse updated = productService.update(id, request);
        return ResponseEntity.ok(updated);
    }

    // DELETE /api/products/{id} -- 204 No Content on success, nothing in the body.
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

// ---------------------------------------------------------------------------
// 8) Global exception handler -- centralizes error shaping for EVERY
//    controller in the app, instead of repeating try/catch in each handler
//    method. @RestControllerAdvice == @ControllerAdvice + @ResponseBody,
//    same relationship as @RestController has to @Controller.
// ---------------------------------------------------------------------------

@RestControllerAdvice
class GlobalExceptionHandler {

    // Triggered when @Valid finds Bean Validation constraint violations on a
    // @RequestBody DTO (e.g. blank name, negative price). Walks the field
    // errors and returns a clean {field: message} map instead of Spring's
    // default, much noisier error payload.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fieldError ->
                errors.put(fieldError.getField(), fieldError.getDefaultMessage()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);   // 400
    }

    // Specific -> handled before the generic Exception fallback below.
    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(ProductNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)                   // 404
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(DuplicateProductException.class)
    public ResponseEntity<Map<String, String>> handleDuplicate(DuplicateProductException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)                    // 409
                .body(Map.of("error", ex.getMessage()));
    }

    // Catch-all fallback -- only for genuinely unexpected failures. Kept
    // last/least-specific on purpose; specific handlers above take priority
    // for the exception types they cover regardless of declaration order.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneric(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)       // 500
                .body(Map.of("error", "An unexpected error occurred"));
    }
}

/*
 * NOTE on the @Service annotation used above:
 * This snippet uses `@Service` on InMemoryProductService as it would appear
 * in a real Spring Boot project (org.springframework.stereotype.Service),
 * which requires component scanning + a @SpringBootApplication entry point
 * to actually wire it into the ProductController's constructor. Since this
 * file is illustrative only (see header), that bootstrap class is
 * intentionally omitted -- drop these classes into a real Spring Boot
 * project's source tree to see it run end-to-end.
 */
