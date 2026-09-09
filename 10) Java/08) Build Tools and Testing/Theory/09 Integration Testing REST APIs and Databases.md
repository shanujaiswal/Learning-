# Three Ways to Test a REST Endpoint

--> Spring offers three distinct HTTP-testing clients, each simulating a different amount of the real stack. Picking the right one is mostly about how much you need "actually over the network" to be true.

| Client | Real HTTP/network? | Needs a running server? | Typical pairing |
|---|---|---|---|
| `MockMvc` | No -- simulates the servlet request/response inside the same JVM | No | `@WebMvcTest` or `@SpringBootTest(webEnvironment = MOCK)` |
| `TestRestTemplate` | Yes -- real HTTP calls | Yes (`webEnvironment = RANDOM_PORT`/`DEFINED_PORT`) | `@SpringBootTest` with a real embedded server |
| `WebTestClient` | Yes (or simulated, it supports both modes) | Optional -- can bind directly to a controller OR do real HTTP | WebFlux apps, but also usable against MVC apps |

## `MockMvc`

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.junit.jupiter.api.Test;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class OrderControllerMockMvcTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void createOrderReturns201WithLocationHeader() throws Exception {
        mockMvc.perform(post("/orders")
                .contentType("application/json")
                .content("""
                    {"productId": 42, "quantity": 3}
                    """))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.status").value("PENDING"));
    }
}
```

--> `MockMvc` dispatches the request through the REAL `DispatcherServlet`, filter chain, `@ControllerAdvice` exception handling, and Jackson serialization -- everything except an actual TCP socket. It's the fastest of the three (no real server binding/startup) while still exercising nearly the whole HTTP-handling pipeline, which is why it's the default choice for most controller-level integration tests.

## `TestRestTemplate`

```java
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderControllerTestRestTemplateTest {

    @Autowired
    TestRestTemplate restTemplate;   // Spring Boot auto-configures this, pointed at the random port

    @Test
    void createOrderOverRealHttp() {
        var request = new OrderRequest(42L, 3);
        ResponseEntity<OrderResponse> response =
            restTemplate.postForEntity("/orders", request, OrderResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().status()).isEqualTo("PENDING");
    }
}
```

--> Requires a REAL embedded server (`RANDOM_PORT` or `DEFINED_PORT`) -- the request genuinely goes over localhost TCP, through the actual servlet container (Tomcat/Jetty/Undertow), not a simulation. This is the highest-fidelity option for verifying the app works as a deployed process would, at the cost of real server startup time.
--> Spring Boot auto-configures a `TestRestTemplate` bean whenever a real web environment is active, pre-configured with the right base port -- no manual wiring needed.

## `WebTestClient`

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.junit.jupiter.api.Test;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class OrderControllerWebTestClientTest {

    @Autowired
    WebTestClient webTestClient;

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
}
```

--> Originally built for WebFlux (reactive) apps, but usable against traditional Spring MVC apps too -- it offers a fluent, non-blocking-style assertion API and can operate in either "bind to real server" (like `TestRestTemplate`) or "bind to controller in-process" (like `MockMvc`) mode. Reach for it in reactive codebases, or when its fluent assertion style is preferred generally.

--> **Practical default**: `MockMvc` for most controller-level integration tests (fast, thorough coverage of the HTTP pipeline), `TestRestTemplate`/`WebTestClient` reserved for the smaller set of tests that specifically need to prove the app works as an actually-deployed, actually-networked process (e.g. verifying server-level config like compression, real connection timeouts).

# Testing the Full Request-Response Cycle with a Real Database

```java
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class OrderFullCycleIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mockMvc;

    @Test
    void creatingAnOrderPersistsItAndItCanBeFetchedBack() throws Exception {
        // 1. Real HTTP-shaped request through the real controller/service/repository chain...
        String location = mockMvc.perform(post("/orders")
                .contentType("application/json")
                .content("{\"productId\": 42, \"quantity\": 3}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getHeader("Location");

        // 2. ...whose effect is verified by reading it back -- proving the write actually
        //    reached the real database, not just that the controller returned 201.
        mockMvc.perform(get(location))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.quantity").value(3));
    }
}
```

--> This is the shape of a genuinely valuable integration test: it proves the ENTIRE chain -- HTTP deserialization, validation, service logic, repository SQL, transaction commit, and the read path -- works together against a real database engine, not just that each layer individually does its documented job in isolation.

# Setting Up Test Data with `@Sql`

--> Some tests need specific rows to already exist before the test body runs (e.g. "fetching a non-existent order returns 404" needs OTHER orders to exist to prove the query is actually filtering, not just always empty).

```java
import org.springframework.test.context.jdbc.Sql;
import org.junit.jupiter.api.Test;

class OrderQueryIntegrationTest extends AbstractIntegrationTest {

    @Test
    @Sql("/test-data/orders-fixture.sql")   // runs BEFORE the test method
    void findsOrdersByStatus() {
        // /test-data/orders-fixture.sql has already inserted known rows by this point
    }

    @Test
    @Sql(scripts = "/test-data/orders-fixture.sql",
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    @Sql(scripts = "/test-data/cleanup.sql",
         executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void explicitPhaseExample() {
        // Two @Sql annotations (Java allows repeatable annotations here) -- one for setup,
        // one for teardown, each with an explicit phase.
    }
}
```

```sql
-- src/test/resources/test-data/orders-fixture.sql
INSERT INTO orders (id, product_id, quantity, status) VALUES
    (100, 42, 1, 'PENDING'),
    (101, 43, 2, 'SHIPPED'),
    (102, 44, 5, 'CANCELLED');
```

--> **`@Sql` at the class level** applies the script(s) before EVERY test method in the class; **at the method level**, only for that one method (method-level takes precedence/merges over class-level depending on configuration). Default `executionPhase` is `BEFORE_TEST_METHOD`.
--> Prefer `@Sql` over building fixture rows through repository calls in `@BeforeEach` when the setup is genuinely data-fixture-shaped (many rows, easier to read as SQL) -- but repository-based setup remains simpler and more refactor-safe for a handful of rows, since it doesn't risk drifting out of sync with entity/schema changes the way a hand-written SQL file can.

# Transactional Rollback for Test Isolation

```java
import org.springframework.transaction.annotation.Transactional;
import org.junit.jupiter.api.Test;

@Transactional   // at class OR method level
class OrderRepositoryTransactionalTest extends AbstractIntegrationTest {

    @Test
    void savingAnOrderIsVisibleWithinTheSameTest() {
        orderRepository.save(new Order(null, "PENDING"));
        assertThat(orderRepository.count()).isEqualTo(1);
        // At the end of this test method, Spring's test framework automatically issues
        // a ROLLBACK instead of a commit -- this row never actually persists past the test.
    }

    @Test
    void databaseIsCleanAgainHere() {
        // Runs against a database with NO leftover row from the previous test,
        // even though both tests share the same (e.g. singleton-pattern) container.
        assertThat(orderRepository.count()).isEqualTo(0);
    }
}
```

--> **Mechanism**: Spring's `TestContext` framework wraps each `@Transactional` test method in a real database transaction, then ROLLS IT BACK after the test completes (pass or fail) instead of committing -- giving each test a clean slate without needing to truncate tables manually or restart the container.
--> This is the SAME `@Transactional` annotation used on production `@Service` methods, but the test-context machinery specifically defaults it to always-rollback, regardless of whether the test throws -- a deliberate difference from production `@Transactional` behavior (which commits on success).
--> **Critical limitation**: rollback-based isolation only works for effects that live INSIDE that one transaction. Code under test that spawns a new thread, calls another service over HTTP, publishes to a real Kafka topic, or itself opens a new/separate transaction (`REQUIRES_NEW`) will NOT be rolled back by the test's wrapper transaction -- those effects escape it entirely.
--> **`@Rollback(false)`** can override the default when a specific test genuinely needs to commit (rare -- usually only needed when asserting behavior that depends on a separate connection/transaction actually seeing the committed data).

# Choosing Between `@Sql`, `@Transactional`, and Manual Cleanup

| Strategy | Best for | Watch out for |
|---|---|---|
| `@Transactional` rollback | Most repository/service-level tests -- fast, automatic, no cleanup code | Doesn't roll back effects from async code, separate transactions, or other systems (Kafka, HTTP calls) |
| `@Sql` (setup script) | Tests needing specific pre-existing data, especially many rows or edge-case data shapes | Can drift out of sync with entity/schema changes if not kept up to date |
| Manual cleanup (`@AfterEach` + explicit deletes/`TRUNCATE`) | Tests that must commit real state (e.g. testing the commit itself, or cross-transaction visibility) | Easy to forget a table, silently leaking state into later tests |

# Common Gotchas and Best Practices

--> **`MockMvc` requests never touch a real socket** -- if the thing you're testing depends on something a real server provides (actual header size limits, real timeout behavior, response compression), `MockMvc` won't catch it; step up to `TestRestTemplate`/`WebTestClient` with a real embedded server for those cases.
--> **`@Transactional` test rollback silently hides bugs in code that genuinely needs `REQUIRES_NEW` or async behavior** -- if a test passes only because rollback erased evidence of a bug in cross-transaction code, that's a blind spot, not a success.
--> **`@Sql` script paths are classpath-relative** by default (`src/test/resources/...`) -- a wrong path fails at test-run time with a resource-not-found error, not a compile error, so a typo can slip through until CI runs it.
--> **Don't mix `@Sql` setup with `@Transactional` rollback carelessly** -- they compose fine (the `@Sql` insert happens inside the same rolled-back transaction), but forgetting that `@Sql`'s inserted rows also vanish at rollback can cause confusion when debugging "my fixture data isn't there" in a later, unrelated test run.
--> **Assert on the persisted result, not just the HTTP response**, when the goal is proving the write reached the database -- a 201 Created with a plausible-looking body can still mean the write silently failed if the test never reads it back (as shown in the full-cycle example above).
