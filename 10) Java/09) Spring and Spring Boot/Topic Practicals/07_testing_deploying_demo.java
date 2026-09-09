/**
 * 07_testing_deploying_demo.java
 *
 * Demonstrates, with heavily commented illustrative test code:
 *     1. @WebMvcTest -- controller-layer slice test using MockMvc + a @MockBean service
 *     2. @DataJpaTest -- repository-layer slice test against an in-memory DB, auto-rollback
 *     3. @SpringBootTest -- full-context integration test (RANDOM_PORT + TestRestTemplate)
 *     4. (companion, non-Java) an example multi-stage Dockerfile
 *     5. (companion, non-Java) example actuator-related application.yml properties
 *
 * Covers Theory chapter:
 *     10) Java/09) Spring and Spring Boot/Theory/07 Testing and Deploying Spring Boot Applications.md
 *
 * IMPORTANT -- this file will NOT compile or run as a plain .java file with `javac`/`java`.
 * It requires a real Spring Boot project on the classpath, specifically:
 *   - `spring-boot-starter-test` (brings JUnit 5, Mockito, AssertJ, MockMvc, @DataJpaTest,
 *     TestRestTemplate, and friends) for everything in sections 1-3.
 *   - `spring-boot-starter-actuator` for the actuator endpoints referenced in section 4/5.
 *   - `spring-boot-starter-web` + `spring-boot-starter-data-jpa` (+ an H2 runtime dependency)
 *     for the ProductController/ProductRepository/Product classes these tests assume exist.
 * It is illustrative code meant to be copy-pasted/adapted into an actual Spring Boot project
 * -- e.g. split into separate files under src/test/java/... in a Maven/Gradle project --
 * not compiled standalone.
 *
 * Run (once dropped into a real Spring Boot project), pick ONE depending on what you added:
 *
 *   Minimal Maven dependencies (pom.xml) to make ALL sections below compile:
 *     <dependency>
 *         <groupId>org.springframework.boot</groupId>
 *         <artifactId>spring-boot-starter-web</artifactId>
 *     </dependency>
 *     <dependency>
 *         <groupId>org.springframework.boot</groupId>
 *         <artifactId>spring-boot-starter-data-jpa</artifactId>
 *     </dependency>
 *     <dependency>
 *         <groupId>org.springframework.boot</groupId>
 *         <artifactId>spring-boot-starter-actuator</artifactId>
 *     </dependency>
 *     <dependency>
 *         <groupId>com.h2database</groupId>
 *         <artifactId>h2</artifactId>
 *         <scope>runtime</scope>
 *     </dependency>
 *     <dependency>
 *         <groupId>org.springframework.boot</groupId>
 *         <artifactId>spring-boot-starter-test</artifactId>
 *         <scope>test</scope>
 *     </dependency>
 *
 *   Once wired into a real project:
 *     1. Paste each numbered test class below into its own file under
 *        src/test/java/com/example/myapp/... (one top-level class per file, per normal
 *        Java file-naming rules), and paste the tiny production classes they depend on
 *        (Product, ProductController, ProductService, ProductRepository) under src/main/java.
 *     2. mvn test                       (runs the whole suite)
 *     3. mvn test -Dtest=ProductControllerTest   (runs just one class)
 *
 *   To try the Dockerfile / actuator config at the bottom of this file for real:
 *     1. Copy the Dockerfile block into a file literally named "Dockerfile" at your project root.
 *     2. Copy the application.yml block into src/main/resources/application.yml.
 *     3. mvn clean package
 *        docker build -t my-app:latest .
 *        docker run -p 8080:8080 my-app:latest
 *        curl http://localhost:8080/actuator/health
 */

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


// =============================================================================
// Tiny production-side classes these tests assume exist elsewhere in the app.
// In a real project these live under src/main/java, NOT alongside the tests --
// they're included here only so the test classes below are self-contained and
// readable without cross-referencing another file.
// =============================================================================

@Entity
class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private double price;

    public Product() {
    }

    public Product(Long id, String name, double price) {
        this.id = id;
        this.name = name;
        this.price = price;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public double getPrice() {
        return price;
    }
}

// A tiny Spring Data JPA repository -- just method-name-derived queries, no
// implementation needed, Spring Data generates it at runtime.
interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByNameContainingIgnoreCase(String fragment);
}

// A @Service the controller depends on -- deliberately NOT annotated with
// @Component/@Service here since it's only ever referenced as a @MockBean
// in section 1, but in the real app it would be a normal @Service class.
interface ProductService {
    Optional<Product> findById(Long id);
}

@RestController
class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/api/products/{id}")
    public ResponseEntity<Product> getProduct(@PathVariable Long id) {
        return productService.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}


// =============================================================================
// 1) @WebMvcTest -- controller layer ONLY, with MockMvc + a @MockBean service
// =============================================================================

/**
 * Boots just the MVC slice (ProductController + MVC infrastructure) -- the
 * ProductService bean is NOT scanned into this context at all, so it must be
 * supplied as a @MockBean or the controller would fail to construct (its
 * constructor requires a ProductService). No database, no @Service/@Repository
 * scanning, no full ApplicationContext -- this is the fastest of the three
 * "context-aware" test styles shown in this file.
 */
@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // Registers a Mockito mock AS A BEAN inside this sliced context -- Spring
    // wires it into ProductController exactly where the real ProductService
    // would have gone. This is different from a plain Mockito @Mock (see the
    // theory chapter's "Mockito Basics, Recapped in Spring Context" section) --
    // a @MockBean is context-aware and participates in Spring's dependency
    // injection, not just a standalone object you wire by hand.
    @MockBean
    private ProductService productService;

    @Test
    void getProduct_found_returns200WithJsonBody() throws Exception {
        // Arrange: stub the mocked collaborator's behavior via Mockito, same
        // syntax as any other Mockito mock.
        when(productService.findById(1L))
                .thenReturn(Optional.of(new Product(1L, "Keyboard", 49.99)));

        // Act + Assert: perform() simulates an HTTP GET against the
        // DispatcherServlet machinery WITHOUT opening a real network socket --
        // real routing/argument resolution/serialization still runs.
        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.name").value("Keyboard"))
                .andExpect(jsonPath("$.price").value(49.99));
    }

    @Test
    void getProduct_notFound_returns404() throws Exception {
        when(productService.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/products/999"))
                .andExpect(status().isNotFound());
        // Note: andDo(print()) could be chained onto either perform() call above
        // to dump the full request/response to the console for debugging --
        // omitted here to keep the assertion output focused.
    }
}


// =============================================================================
// 2) @DataJpaTest -- repository layer ONLY, in-memory DB, per-test rollback
// =============================================================================

/**
 * Boots just the JPA slice -- @Entity classes, Spring Data repositories, the
 * EntityManager -- and swaps in an embedded H2 database instead of any real
 * production datasource configured elsewhere in the app. Each test method
 * runs inside its own transaction that is AUTOMATICALLY ROLLED BACK at the
 * end, so test data never leaks between methods and no manual cleanup is
 * required -- this is different from a @Transactional service method in
 * production code, which commits normally and only rolls back on an
 * unhandled exception (see the theory chapter's gotchas section).
 */
@DataJpaTest
class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private TestEntityManager entityManager; // convenience helper for setting up test rows directly

    @Test
    void findByNameContainingIgnoreCase_returnsOnlyMatchingProducts() {
        // Arrange: persist directly via the TestEntityManager rather than the
        // repository under test, so the test doesn't accidentally depend on
        // the very method it's verifying.
        entityManager.persist(new Product(null, "Mechanical Keyboard", 89.99));
        entityManager.persist(new Product(null, "Wireless Mouse", 29.99));
        entityManager.flush();

        // Act
        List<Product> results = productRepository.findByNameContainingIgnoreCase("keyboard");

        // Assert
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).contains("Keyboard");
        // No manual DELETE/cleanup needed here -- @DataJpaTest's transaction
        // wrapper rolls this back the instant the test method returns.
    }

    @Test
    void save_assignsGeneratedId() {
        Product saved = productRepository.save(new Product(null, "Monitor", 199.99));

        assertThat(saved.getId()).isNotNull();
    }
}


// =============================================================================
// 3) @SpringBootTest -- full context, real embedded server, real HTTP round trip
// =============================================================================

/**
 * Boots the ENTIRE ApplicationContext -- every bean, all auto-configuration,
 * exactly as it would run in production -- and, because webEnvironment is set
 * to RANDOM_PORT, also starts a REAL embedded server (Tomcat by default) on a
 * randomly chosen free port. TestRestTemplate is auto-configured to talk to
 * that port, so this test makes a genuine network round trip through the
 * whole stack: real routing, real service layer, real repository, real
 * (H2, in this illustrative example) database.
 *
 * This is deliberately the heaviest and slowest of the three styles in this
 * file -- reach for it only when the thing under test genuinely spans
 * multiple layers, not as a default for every test (see the theory chapter's
 * "Common Gotchas" section on @SpringBootTest overuse).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductApiIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void getProduct_endToEnd_hitsRealServerAndRealRepository() {
        // Arrange: seed a row through the real repository bean (real
        // transaction semantics apply here, unlike @DataJpaTest's forced
        // rollback -- this test is responsible for its own data hygiene if
        // it needs isolation across methods).
        Product saved = productRepository.save(new Product(null, "USB Hub", 15.5));

        // Act: an actual HTTP GET over the network stack, through
        // DispatcherServlet, the real ProductService, and the real
        // ProductRepository/H2 database -- no mocks anywhere in this path.
        ResponseEntity<Product> response =
                restTemplate.getForEntity("/api/products/" + saved.getId(), Product.class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getName()).isEqualTo("USB Hub");
    }
}


/*
 * =============================================================================
 * COMPANION ARTIFACT -- Example multi-stage Dockerfile
 * (not Java -- save as a file literally named "Dockerfile" at the project root)
 * =============================================================================
 *
 * # ---- Stage 1: build ----
 * FROM maven:3.9-eclipse-temurin-21 AS build
 * WORKDIR /app
 * COPY pom.xml .
 * RUN mvn dependency:go-offline
 * COPY src ./src
 * RUN mvn clean package -DskipTests
 *
 * # ---- Stage 2: run ----
 * FROM eclipse-temurin:21-jre-jammy
 * WORKDIR /app
 * COPY --from=build /app/target/*.jar app.jar
 * EXPOSE 8080
 * ENTRYPOINT ["java", "-jar", "app.jar"]
 *
 * Build + run:
 *   docker build -t my-app:latest .
 *   docker run -p 8080:8080 my-app:latest
 *   docker run -p 8080:8080 -e SPRING_PROFILES_ACTIVE=prod my-app:latest
 *
 * Why multi-stage: the maven:... build image carries the full JDK + Maven
 * toolchain (500MB+), none of which is needed to just RUN the finished jar --
 * copying only the built artifact into a slim JRE-only base image routinely
 * shrinks the final image from 500-800MB down to under 200MB.
 * =============================================================================
 */


/*
 * =============================================================================
 * COMPANION ARTIFACT -- Example actuator-related application.yml properties
 * (not Java -- save/merge into src/main/resources/application.yml)
 * =============================================================================
 *
 * management:
 *   endpoints:
 *     web:
 *       exposure:
 *         include: health, info, metrics, env   # explicit opt-in -- only /health
 *                                                 # is exposed over HTTP by default;
 *                                                 # "*" exposes everything, avoid in prod
 *   endpoint:
 *     health:
 *       show-details: when-authorized            # or "always" -- controls how much detail
 *                                                 # the /actuator/health body reveals
 *   server:
 *     port: 9090                                  # optional: serve actuator on a SEPARATE
 *                                                 # port from the public API, so it's not
 *                                                 # reachable on the same port as normal traffic
 *
 * info:
 *   app:
 *     name: my-app
 *     version: 1.0.0                              # surfaced verbatim at /actuator/info
 *
 * With spring-boot-starter-actuator on the classpath and the block above applied:
 *   curl http://localhost:8080/actuator/health    -> {"status":"UP", ...}
 *   curl http://localhost:8080/actuator/info      -> {"app":{"name":"my-app","version":"1.0.0"}}
 *   curl http://localhost:8080/actuator/metrics   -> list of available metric names
 *   curl http://localhost:8080/actuator/env       -> full resolved environment (secure this!)
 * =============================================================================
 */
