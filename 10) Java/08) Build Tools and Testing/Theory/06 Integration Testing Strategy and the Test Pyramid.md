# Why Integration Tests Exist

--> **Unit tests** verify one class or method in ISOLATION, with all real collaborators (database, HTTP clients, message brokers, filesystem) replaced by test doubles. They're fast and pinpoint failures precisely, but they prove nothing about whether the pieces actually fit together -- a repository's SQL might be syntactically valid but semantically wrong against a real schema, a JSON mapping might silently drop a field, a transaction boundary might not actually roll back.
--> **Integration tests** verify that two or more real components work correctly TOGETHER -- your Spring `@Service` talking to a real (or realistic) database, your REST controller handling a real HTTP request through the real serialization/validation/exception-handling pipeline, your Kafka consumer actually deserializing a message produced onto a real broker. The defining trait is that at least one boundary that a unit test would have mocked out is now real.
--> Neither category replaces the other. Mocking every collaborator in a unit test proves your code calls its dependencies the way you *think* they behave -- it can't catch cases where that assumption itself is wrong (e.g. you assumed a repository method returns `null` on "not found" but it actually throws). Integration tests close that gap at the cost of speed and setup complexity.

# The Test Pyramid

--> The test pyramid is a heuristic for how a healthy test suite's tests should be DISTRIBUTED across levels, shaped like a pyramid because each layer up trades quantity for realism.

```text
        /\
       /  \        End-to-End (E2E) tests
      /----\       - fewest in number
     /      \      - full system, real browser/client, real network
    /--------\     - slow (seconds-minutes each), brittle, expensive to maintain
   /          \
  /------------\   Integration tests
 /              \  - moderate number
/----------------\ - real DB/broker (often via Testcontainers), real Spring context
/                  \- slower than unit (100ms-few sec each), fewer moving parts than E2E
/--------------------\
/                      \ Unit tests
/------------------------\ - most numerous, base of the pyramid
                            - milliseconds each, isolated, cheap to write and maintain
```

| Level | What's real | Speed | Failure signal | Typical count |
|---|---|---|---|---|
| Unit | Nothing external -- pure logic | Milliseconds | Points at one class/method | Hundreds-thousands |
| Integration | DB, message broker, HTTP layer, Spring context | Tens of ms - a few seconds | Points at a boundary/wiring problem | Dozens-low hundreds |
| End-to-End | Everything -- deployed app, real network, sometimes a browser | Seconds-minutes | Points at "something in the whole flow" -- needs digging | Few, high-value paths only |

--> **Anti-pattern: the "ice cream cone"** -- an inverted pyramid where a project has few unit tests but a large, slow, flaky E2E suite (common when teams distrust unit tests or reach for E2E as the default "does it work" check). Symptoms: a 45-minute CI run, tests that fail for reasons unrelated to the code change (timing, environment, test ordering), and a team that starts ignoring red builds.
--> The pyramid shape isn't a strict quota to hit -- it's a reminder that cost rises steeply as you move up levels, so each layer should only carry the tests that level is uniquely suited to catch.

# When Integration Tests Earn Their Cost

--> Write an integration test when the RISK lives at a boundary a unit test can't see through:

--> **Database query correctness** -- a custom `@Query` with a JOIN, a native SQL query, pagination/sorting logic, or a query relying on specific DB behavior (e.g. Postgres `JSONB` operators, case-insensitive collation). Mocking the repository here tests nothing real; you need a real (or Testcontainers-backed) database engine.
--> **Transaction boundaries** -- does `@Transactional` actually roll back all writes on an exception? Does a multi-step service method commit atomically? This is invisible to a mocked repository, which has no concept of a transaction at all.
--> **Serialization/deserialization at a real boundary** -- does the controller correctly map an incoming JSON body to a DTO, apply `@Valid` constraints, and produce the expected JSON response (including error responses)? A unit test calling the controller method directly in Java skips the entire HTTP/Jackson/validation pipeline.
--> **Framework wiring** -- does Spring actually autowire the beans you expect, resolve `@ConfigurationProperties` correctly, and apply the security filter chain the way the config claims? Misconfiguration is a classic "works in every unit test, breaks at runtime" failure mode.
--> **Cross-service contracts** -- does your Kafka consumer correctly handle the message format your producer actually emits? Do you correctly handle the DB's actual constraint-violation exception type (which differs across drivers)?

--> Conversely, DON'T reach for an integration test when a unit test with a mock proves the same thing more cheaply -- e.g. "does the service call `repository.save()` exactly once" is a unit-test-with-mock question (verifying your own orchestration logic), not one requiring a real database.

# Spring Boot Test Support Overview

--> Spring Boot provides several annotations that load different SLICES of the application context, trading "how much is real" against "how fast does the test run." Choosing the narrowest slice that still exercises what you care about keeps the suite fast.

## `@SpringBootTest` -- The Full Context

```java
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.junit.jupiter.api.Test;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderServiceApplicationIntegrationTest {

    @LocalServerPort
    int port;   // the actual port Spring bound to, since RANDOM_PORT picks a free one

    @Test
    void applicationStartsAndRespondsToHealthCheck() {
        var restTemplate = new TestRestTemplate();
        var response = restTemplate.getForEntity(
            "http://localhost:" + port + "/actuator/health", String.class);
        // Full application context is up: real beans, real config, real (or Testcontainers) DB.
    }
}
```

--> `@SpringBootTest` boots the ENTIRE application context -- every `@Component`, `@Service`, `@Repository`, `@Configuration`, exactly as production would, following `@SpringBootApplication`'s component scan. This is the most realistic Spring-level test but also the slowest to start (typically 1-5+ seconds of context startup, though Spring caches and reuses the context across test classes with an identical configuration).
--> `webEnvironment` controls whether a real embedded server starts: `MOCK` (default -- no real server, use `MockMvc`), `RANDOM_PORT` / `DEFINED_PORT` (real embedded Tomcat/Netty, use `TestRestTemplate`/`WebTestClient` for real HTTP calls), or `NONE` (no web environment at all).

## Test Slices -- Loading Only Part of the Context

--> A "slice" annotation loads only the beans relevant to one architectural layer, auto-configuring just enough infrastructure for that layer and skipping the rest -- much faster than `@SpringBootTest`, while still being more real than a pure mock-based unit test.

| Slice annotation | Loads | Typical collaborators to mock |
|---|---|---|
| `@WebMvcTest(OrderController.class)` | MVC infrastructure, the named `@Controller`, filters, `@ControllerAdvice` | `@Service`/`@Repository` beans, via `@MockBean` |
| `@DataJpaTest` | JPA repositories, an embedded/configured DB, Hibernate, transaction manager | Nothing -- it's meant to hit a real DB layer |
| `@WebFluxTest(OrderHandler.class)` | WebFlux infrastructure for reactive controllers | Service layer, via `@MockBean` |
| `@JsonTest` | Jackson `ObjectMapper` and related JSON config only | Everything else |
| `@DataMongoTest` / `@DataRedisTest` | The respective data-store client/template, no web layer | N/A |

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
class OrderControllerSliceTest {

    @Autowired
    MockMvc mockMvc;              // simulates HTTP calls without a real server -- fast

    @MockBean
    OrderService orderService;    // real service is NOT loaded; this replaces it in the context

    @Test
    void getOrderReturns200AndBody() throws Exception {
        when(orderService.findById(1L)).thenReturn(new Order(1L, "PENDING"));

        mockMvc.perform(get("/orders/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING"));
    }
}

@DataJpaTest   // loads real repositories + a real/embedded DB, rolls back after each test by default
class OrderRepositoryTest {
    @Autowired
    OrderRepository orderRepository;

    @Test
    void findsOrdersByStatus() {
        orderRepository.save(new Order(null, "PENDING"));
        assertThat(orderRepository.findByStatus("PENDING")).hasSize(1);
    }
}
```

--> **`@DataJpaTest` defaults to an in-memory/embedded database (e.g. H2) unless reconfigured** -- this is exactly the "mismatch with production" problem Testcontainers exists to solve, covered in the next file. `@AutoConfigureTestDatabase(replace = Replace.NONE)` disables that auto-substitution so `@DataJpaTest` uses whatever `DataSource` your Testcontainers setup provides instead.
--> Slices are named per Spring Boot module (`spring-boot-test-autoconfigure`); check the Spring Boot docs for the full list (`@RestClientTest`, `@JooqTest`, etc.) when working with less common infrastructure.

# Choosing a Level -- A Practical Checklist

--> **Can a unit test with a mock prove this?** If yes, that's cheaper and faster -- use it, and save the integration test budget for boundary risk.
--> **Does correctness depend on a specific DB/broker's actual behavior** (query semantics, constraint enforcement, serialization format)? That's integration-test territory, and it needs a REAL engine, not an approximation -- see the Testcontainers files that follow.
--> **Does correctness depend on multiple services/deployed processes actually talking over the network** (auth handshakes, service discovery, real infra config)? That's E2E territory -- keep it to the highest-value user journeys, since each one is expensive to write and maintain.
--> **What's the blast radius of getting this wrong?** Payment processing and auth boundaries justify integration-test investment that a purely internal utility function does not.

# Common Gotchas and Best Practices

--> **Don't let "integration test" become a dumping ground for slow unit tests.** A test that's slow because it sleeps or does unnecessary I/O without actually crossing a real boundary is just a badly written unit test -- fix it, don't reclassify it.
--> **Match the DB engine to production.** An `@DataJpaTest` against H2 while production runs Postgres can pass locally and fail in production over dialect differences (case sensitivity, `JSONB`, sequence behavior) -- this exact gap motivates Testcontainers, covered next.
--> **Keep the base of the pyramid wide.** If integration tests are the only way you're catching bugs, that's usually a sign business logic isn't sufficiently separated from I/O concerns -- extract pure logic into unit-testable classes/methods where possible.
--> **Watch context-loading cost.** Spring caches application contexts between test classes ONLY when their configuration is identical (same `@SpringBootTest` properties, same active profiles, same mocked beans) -- subtly different configurations across test classes silently multiply context-startup time across the whole suite.
--> **Slice tests are not a replacement for `@SpringBootTest`, they're a complement** -- a `@WebMvcTest` proves the controller layer is correct assuming the service behaves as mocked; you still want some full-context (or Testcontainers-backed) tests proving the real chain from HTTP request through to database write actually works end-to-end within your application.
