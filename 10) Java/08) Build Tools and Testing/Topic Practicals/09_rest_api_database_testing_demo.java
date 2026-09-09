/**
 * 09_rest_api_database_testing_demo.java
 *
 * Demonstrates, with heavily commented illustrative test code, three ways to
 * test the SAME REST endpoint plus test-data setup and isolation strategies:
 *     1. MockMvc -- simulated servlet dispatch, no real socket
 *     2. TestRestTemplate -- real HTTP over a real embedded server
 *     3. WebTestClient -- fluent client, real HTTP or in-process binding
 *     4. @Sql -- pre-loading known fixture rows before a test runs
 *     5. @Transactional -- automatic per-test rollback for isolation
 *
 * Covers Theory chapter:
 *     08) Build Tools and Testing/Theory/09 Integration Testing REST APIs and Databases.md
 *
 * IMPORTANT -- this file will NOT compile or run as a plain .java file with `javac`/`java`.
 * It requires:
 *   - A real Docker daemon reachable wherever the test JVM runs (the examples below build on
 *     the singleton-container AbstractIntegrationTest pattern from file 03 in this folder) --
 *     NOT available in this environment, which is exactly why this file is illustrative only.
 *   - `spring-boot-starter-test`, `spring-boot-starter-web`, `spring-boot-starter-webflux`
 *     (for WebTestClient -- it works against MVC apps too, but the dependency itself comes
 *     from the reactive stack), `spring-boot-starter-data-jpa`, `spring-boot-testcontainers`,
 *     and the Testcontainers `postgresql` module.
 *   - A `src/test/resources/test-data/orders-fixture.sql` file, shown inline below, for the
 *     @Sql example to have something real to load.
 *
 * Minimal Maven dependencies (pom.xml) to make ALL sections below compile:
 *     <dependency>
 *         <groupId>org.springframework.boot</groupId>
 *         <artifactId>spring-boot-starter-web</artifactId>
 *     </dependency>
 *     <dependency>
 *         <groupId>org.springframework.boot</groupId>
 *         <artifactId>spring-boot-starter-webflux</artifactId>
 *     </dependency>
 *     <dependency>
 *         <groupId>org.springframework.boot</groupId>
 *         <artifactId>spring-boot-starter-data-jpa</artifactId>
 *     </dependency>
 *     <dependency>
 *         <groupId>org.springframework.boot</groupId>
 *         <artifactId>spring-boot-starter-test</artifactId>
 *         <scope>test</scope>
 *     </dependency>
 *     <dependency>
 *         <groupId>org.springframework.boot</groupId>
 *         <artifactId>spring-boot-testcontainers</artifactId>
 *         <scope>test</scope>
 *     </dependency>
 *     <dependency>
 *         <groupId>org.testcontainers</groupId>
 *         <artifactId>postgresql</artifactId>
 *         <version>1.19.7</version>
 *         <scope>test</scope>
 *     </dependency>
 *
 * Run (once dropped into a real Spring Boot project with Docker available):
 *     mvn test -Dtest=OrderControllerMockMvcTest
 *     mvn test -Dtest=OrderControllerTestRestTemplateTest
 *     mvn test -Dtest=OrderControllerWebTestClientTest
 *     mvn test -Dtest=OrderQuerySqlFixtureTest
 *     mvn test -Dtest=OrderRepositoryTransactionalRollbackTest
 */

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


// =============================================================================
// Tiny production-side classes these tests assume exist elsewhere in the app.
// =============================================================================

@Entity
class WebOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long productId;
    private int quantity;
    private String status;

    public WebOrder() {
    }

    public WebOrder(Long id, Long productId, int quantity, String status) {
        this.id = id;
        this.productId = productId;
        this.quantity = quantity;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getStatus() {
        return status;
    }
}

interface WebOrderRepository extends JpaRepository<WebOrder, Long> {
    List<WebOrder> findByStatus(String status);
}

record OrderRequest(Long productId, int quantity) {
}

record OrderResponse(Long id, int quantity, String status) {
}

// A single shared base class (matching the singleton pattern from file 03)
// used by every test class below so they all reuse ONE Postgres container
// instead of each starting its own.
abstract class AbstractWebIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
}


// =============================================================================
// 1) MockMvc -- simulates the servlet request/response inside the same JVM,
//    no real socket, fastest of the three
// =============================================================================

/**
 * MockMvc dispatches through the REAL DispatcherServlet, filter chain,
 * @ControllerAdvice exception handling, and Jackson serialization -- just
 * without an actual TCP socket. Default choice for most controller-level
 * integration tests: fast, and covers nearly the whole HTTP pipeline.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class OrderControllerMockMvcTest extends AbstractWebIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createOrderReturns201WithLocationHeader() throws Exception {
        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\": 42, \"quantity\": 3}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }
}


// =============================================================================
// 2) TestRestTemplate -- real HTTP calls over a real embedded server
// =============================================================================

/**
 * Requires a REAL embedded server (RANDOM_PORT here) -- the request genuinely
 * travels over localhost TCP through the actual servlet container, not a
 * simulation. Highest-fidelity option for proving the app behaves as an
 * actually-deployed process would, at the cost of real server startup time.
 * Spring Boot auto-configures the TestRestTemplate bean, pre-pointed at the
 * randomly chosen port.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class OrderControllerTestRestTemplateTest extends AbstractWebIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void createOrderOverRealHttp() {
        var request = new OrderRequest(42L, 3);

        ResponseEntity<OrderResponse> response =
                restTemplate.postForEntity("/orders", request, OrderResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("PENDING");
    }
}


// =============================================================================
// 3) WebTestClient -- fluent assertions, can bind to a real server or in-process
// =============================================================================

/**
 * Originally built for WebFlux/reactive apps, but usable against traditional
 * Spring MVC apps too -- offers a fluent, chained assertion style and can
 * operate either bound to a real running server (as here, via RANDOM_PORT) or
 * bound directly to a controller in-process (MockMvc-like mode).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Testcontainers
class OrderControllerWebTestClientTest extends AbstractWebIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void createOrderReturnsExpectedBody() {
        webTestClient.post().uri("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new OrderRequest(42L, 3))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.status").isEqualTo("PENDING");
    }

    // Practical default across all three sections above: MockMvc for most
    // controller-level tests (fast, thorough HTTP-pipeline coverage);
    // TestRestTemplate/WebTestClient reserved for the smaller set of tests that
    // specifically need to prove server-level behavior (compression, real
    // connection timeouts) that only a genuinely bound socket can reveal.
}


// =============================================================================
// 4) @Sql -- pre-loading known fixture rows before a test method runs
// =============================================================================

/**
 * Some tests need specific rows to already exist BEFORE the test body runs --
 * e.g. proving "fetch a non-existent order returns 404" needs OTHER orders to
 * exist first, to prove the query is actually filtering rather than always
 * returning empty regardless of input.
 *
 * Companion fixture file this annotation loads, expected at
 * src/test/resources/test-data/orders-fixture.sql:
 *
 *     INSERT INTO web_order (id, product_id, quantity, status) VALUES
 *         (100, 42, 1, 'PENDING'),
 *         (101, 43, 2, 'SHIPPED'),
 *         (102, 44, 5, 'CANCELLED');
 */
@SpringBootTest
@Testcontainers
class OrderQuerySqlFixtureTest extends AbstractWebIntegrationTest {

    @Autowired
    private WebOrderRepository webOrderRepository;

    @Test
    @Sql("/test-data/orders-fixture.sql") // runs BEFORE this test method, default phase BEFORE_TEST_METHOD
    void findsOrdersByStatus_againstKnownFixtureRows() {
        // /test-data/orders-fixture.sql has already inserted the three known
        // rows above by the time this method body starts running.
        assertThat(webOrderRepository.findByStatus("PENDING")).hasSize(1);
        assertThat(webOrderRepository.findByStatus("CANCELLED")).hasSize(1);
    }

    @Test
    @Sql(scripts = "/test-data/orders-fixture.sql",
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    @Sql(scripts = "/test-data/cleanup.sql", // e.g. "DELETE FROM web_order;"
            executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void explicitSetupAndTeardownPhases() {
        // Two @Sql annotations (Java allows repeatable annotations here) --
        // one for setup, one for teardown, each with an explicit phase.
        assertThat(webOrderRepository.findByStatus("SHIPPED")).hasSize(1);
    }
}


// =============================================================================
// 5) @Transactional -- automatic per-test rollback for isolation
// =============================================================================

/**
 * Spring's TestContext framework wraps each @Transactional test method in a
 * real database transaction, then ROLLS IT BACK after the test completes
 * (pass or fail) instead of committing -- giving each test a clean slate
 * without manually truncating tables or restarting the container. This is the
 * SAME @Transactional annotation used in production, but the test-context
 * machinery specifically defaults it to always-rollback.
 */
@SpringBootTest
@Testcontainers
@Transactional // at class level here -- applies to every @Test method below
class OrderRepositoryTransactionalRollbackTest extends AbstractWebIntegrationTest {

    @Autowired
    private WebOrderRepository webOrderRepository;

    @Test
    void savingAnOrderIsVisibleWithinTheSameTest() {
        webOrderRepository.save(new WebOrder(null, 1L, 1, "PENDING"));

        assertThat(webOrderRepository.count()).isEqualTo(1);
        // At the end of this method, Spring's test framework issues a ROLLBACK
        // instead of a commit -- this row never actually persists past the test.
    }

    @Test
    void databaseIsCleanAgainHere() {
        // Runs against a database with NO leftover row from the previous test,
        // even though both tests share the same singleton-pattern container.
        assertThat(webOrderRepository.count()).isEqualTo(0);
    }

    // CRITICAL LIMITATION (from the theory file): rollback-based isolation only
    // protects effects that live INSIDE that one wrapper transaction. Code that
    // spawns a new thread, calls another service over HTTP, publishes to Kafka,
    // or opens its own separate transaction (REQUIRES_NEW) is NOT rolled back
    // by this wrapper -- those effects escape it entirely and need manual
    // cleanup (e.g. @AfterEach deletes) instead.
}
