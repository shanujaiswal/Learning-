# The Wiring Problem

--> A raw Testcontainers test (as in the previous file) starts a real container, but Spring's `DataSource`/`KafkaTemplate`/etc. still need to be told the container's connection details -- and those details (host port especially) aren't known until the container has already started, which is AFTER Spring would normally read `application.properties`. Two mechanisms solve this: `@DynamicPropertySource` (works everywhere, more boilerplate) and `@ServiceConnection` (Spring Boot 3.1+, near-zero boilerplate for supported technologies).

# `@DynamicPropertySource`

```java
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class OrderServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("orders")
        .withUsername("app")
        .withPassword("secret");

    @DynamicPropertySource   // static method, runs AFTER containers start but BEFORE context refresh
    static void registerPostgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Values are suppliers (lambdas), not eagerly-evaluated strings -- Spring calls them
        // lazily, at the moment it actually needs the property, which is what makes the ordering work.
    }

    @Test
    void springContextUsesTheRealContainerDatabase() {
        // Any @Autowired DataSource/JdbcTemplate/JPA repository in this context now points
        // at the real, running Postgres container -- not application.properties' configured DB.
    }
}
```

--> **Why a `Supplier`, not a plain value**: `@DynamicPropertySource` methods run early, but the registry stores each property as a lazily-evaluated `Supplier<Object>`. This matters because the container's mapped port genuinely isn't known until `.start()` has completed -- passing method references (`postgres::getJdbcUrl`) rather than calling `postgres.getJdbcUrl()` immediately defers evaluation to the right moment.
--> **Must be `static`**, for the same reason `@BeforeAll` must be `static` -- it runs before the Spring `ApplicationContext` (and therefore any test instance wiring) exists, at the JUnit class level, once per class.
--> Works for ANY property, not just database connections -- Kafka bootstrap servers, Redis host/port, arbitrary feature-flag-style config -- making it the general-purpose mechanism when no more specific integration exists.

# `@ServiceConnection` (Spring Boot 3.1+)

--> For common technologies, Spring Boot 3.1+ can detect a running Testcontainers container and auto-configure the matching connection properties itself -- eliminating the manual `@DynamicPropertySource` boilerplate entirely for the common case.

```java
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class OrderServiceServiceConnectionTest {

    @Container
    @ServiceConnection   // that's it -- no @DynamicPropertySource method needed
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void contextLoadsAgainstRealDatabase() {
        // Spring Boot recognizes this is a PostgreSQLContainer and auto-configures
        // spring.datasource.* (url, username, password, driver) to match it.
    }
}
```

--> **How it works**: Spring Boot ships `ContainerConnectionDetailsFactory` implementations for supported container types, each of which knows how to translate that container's runtime state into the right `ConnectionDetails` bean (e.g. `JdbcConnectionDetails`, `KafkaConnectionDetails`, `RedisConnectionDetails`). `@ServiceConnection` on a `@Container` field triggers that translation automatically -- no property names to get right, no risk of a typo'd key silently falling back to a default.
--> **Supported out of the box** (as of Spring Boot 3.1+, expanding in later versions): PostgreSQL, MySQL, MariaDB, MongoDB, Redis, Neo4j, Kafka, RabbitMQ, Cassandra, Elasticsearch, ActiveMQ, Pulsar, OpenSearch, Zipkin, OTLP, and more. Each requires the corresponding `spring-boot-testcontainers` support plus that technology's Testcontainers module on the classpath.

```xml
<!-- Maven: required for @ServiceConnection -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
</dependency>
```

| | `@DynamicPropertySource` | `@ServiceConnection` |
|---|---|---|
| Spring Boot version | Any (Spring Framework feature) | 3.1+ |
| Boilerplate | Manual property mapping per property | None -- one annotation |
| Works for custom/unsupported containers | Yes, always | Only for supported container types |
| Multiple connections of the same type | Manual, full control over naming | Supported via bean qualifiers/name matching |

--> **Use `@ServiceConnection` by default** for anything it supports -- it's less code and immune to property-name typos. Fall back to `@DynamicPropertySource` for containers it doesn't recognize (a custom `GenericContainer`-based service, an internal tool, a technology not yet covered).

# The Singleton Container Pattern

--> The naive approach -- a fresh `static @Container` per test class -- means EVERY integration test class in the project pays full container startup cost independently. With dozens of test classes, that adds minutes to a suite that could otherwise run in seconds. The **singleton container pattern** starts ONE container for the entire test run (JVM), shared across all test classes.

```java
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withReuse(true);   // combine with the Testcontainers reuse feature for cross-run reuse too
        POSTGRES.start();
        // Deliberately NEVER calling POSTGRES.stop() here -- the JVM shutting down at the end
        // of the whole test run is what stops the container (Ryuk cleans it up regardless).
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}

class OrderServiceIntegrationTest extends AbstractIntegrationTest {
    // Inherits the already-started, already-registered container -- no per-class startup cost.
}

class CustomerServiceIntegrationTest extends AbstractIntegrationTest {
    // Same container instance as OrderServiceIntegrationTest (started once, in the static block).
}
```

--> **Mechanism**: starting the container in a `static` initializer block (rather than a `@Container`-managed field) means it starts exactly once, the first time the class is loaded, and is never explicitly stopped by test code -- it rides along until the JVM process ends. This works because Testcontainers' Ryuk companion container guarantees cleanup regardless.
--> **Base class inheritance** is the common way to share this across many test classes -- every integration test extends `AbstractIntegrationTest` and inherits the running container and its property registration, so the container starts once per test run (or even once per developer machine across runs, if `.withReuse(true)` and the opt-in reuse feature are enabled) instead of once per class.
--> This is a widely-adopted community pattern rather than a Testcontainers API feature per se -- there's no single "singleton mode" flag; it emerges from how `static` initialization and JVM lifecycle work.

--> **`.withReuse(true)`** enables an entirely separate opt-in feature: reusing the SAME container across separate test-run invocations (e.g. across multiple `mvn test` runs during local development), not just across classes within one run. It requires setting `testcontainers.reuse.enable=true` in `~/.testcontainers.properties` and is generally a LOCAL development convenience -- most teams disable or ignore it in CI, where a fresh, guaranteed-clean container per pipeline run is usually the safer default.

# Testcontainers Module Ecosystem Overview

--> Beyond the core JDBC/Kafka examples already covered, the ecosystem includes modules worth knowing about when they come up:

| Module | Use case |
|---|---|
| `localstack` | Emulates AWS services (S3, SQS, SNS, DynamoDB, Lambda...) locally for testing AWS-integrated code without real AWS calls |
| `toxiproxy` | Injects network faults (latency, connection resets, bandwidth limits) between your app and a container, for resilience/timeout testing |
| `mockserver` / `wiremock` | Stub external HTTP APIs your app calls, as a real running server rather than an in-process mock |
| `k3s` | Runs a lightweight Kubernetes cluster for testing Kubernetes-aware code |
| `vault` | HashiCorp Vault for testing secrets-management integrations |
| `neo4j`, `couchbase`, `clickhouse` | Purpose-built modules for less common but still supported data stores |
| Testcontainers Cloud | A hosted service that runs containers remotely instead of on the local/CI Docker daemon -- reduces local resource usage and can speed up CI, at the cost of external dependency and cost |

--> Modules follow a consistent design: a typed container class with sensible defaults and a correct wait strategy, plus (increasingly) first-party `@ServiceConnection` support in Spring Boot for the most common ones.

# Common Gotchas and Best Practices

--> **`@DynamicPropertySource` methods must be `static`** and are only valid inside a test class annotated for Spring context loading (e.g. alongside `@SpringBootTest`) -- a common early mistake is putting connection logic in an instance method or `@BeforeEach`, which runs too late (context is already built).
--> **`@ServiceConnection` requires the `spring-boot-testcontainers` dependency** in addition to the technology-specific Testcontainers module -- forgetting it produces confusing "unrecognized container type" behavior rather than a clear error in some Spring Boot versions.
--> **Don't call `.stop()` on a singleton-pattern container.** If any test class explicitly stops a container that others still expect running, every subsequent test class fails with an obscure connection-refused error -- the whole point of the singleton pattern is to let the JVM/Ryuk own shutdown.
--> **Reused containers accumulate state across tests unless you clean up.** The singleton pattern trades per-class isolation for speed -- pair it with per-test data cleanup (e.g. `@Sql` scripts, `@Transactional` rollback, or explicit `TRUNCATE`, covered in the next file) rather than relying on a fresh database per test.
--> **Local container reuse (`withReuse(true)`) is a developer-experience feature, not a CI feature** -- most CI runners start from a clean environment every run anyway, so there's nothing to reuse, and relying on it in CI risks masking state leakage that a genuinely fresh container would have caught.
