# The Problem Testcontainers Solves

--> Before Testcontainers, integration-testing a database interaction usually meant one of two compromises: an in-memory substitute like **H2** running in "Postgres compatibility mode," or **mocking** the repository/data-access layer entirely. Both trade away exactly the thing an integration test exists to verify.
--> **H2/in-memory substitutes** approximate another database's SQL dialect but never perfectly -- Postgres-specific features (`JSONB`, array columns, `ON CONFLICT`, window functions, specific error codes on constraint violations, case-sensitive identifiers) either don't exist in H2 or behave subtly differently. A test can pass against H2 and fail against real Postgres in production -- the exact "false confidence" an integration test is supposed to prevent.
--> **Mocking the repository** (e.g. `Mockito.mock(OrderRepository.class)`) tests your service's orchestration logic but proves nothing about whether your actual queries, mappings, or constraints are correct -- it's really still a unit test wearing an integration test's name.
--> **Testcontainers** is a Java library that spins up real, disposable Docker containers -- a real PostgreSQL server, a real Kafka broker, a real Redis instance -- for the duration of a test run, then tears them down. You get the exact production technology, in a throwaway, isolated instance, no shared test database to coordinate or accidentally pollute.

| Approach | Real engine? | Setup cost | Fidelity to production | Requires Docker |
|---|---|---|---|---|
| Mocked repository | No | None | Low -- tests your code's assumptions only | No |
| In-memory DB (H2) | No (compatibility mode) | Low | Medium -- dialect gaps remain | No |
| Shared test DB/server | Yes | Ongoing (someone maintains it) | High, but tests can interfere with each other | No |
| Testcontainers | Yes | Docker + container startup time per run | High, and isolated per test run | Yes |

# Adding Testcontainers to a Project

```xml
<!-- Maven: pom.xml -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <version>1.19.7</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>1.19.7</version>
    <scope>test</scope>
</dependency>
<!-- One module per technology you need, e.g.: -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <version>1.19.7</version>
    <scope>test</scope>
</dependency>
```

```groovy
// Gradle: build.gradle
dependencies {
    testImplementation 'org.testcontainers:testcontainers:1.19.7'
    testImplementation 'org.testcontainers:junit-jupiter:1.19.7'
    testImplementation 'org.testcontainers:postgresql:1.19.7'
}
```

--> **Docker (or a compatible runtime) must be available wherever these tests run** -- locally on the developer's machine, and in CI. This is Testcontainers' main practical requirement and its main friction point; see the CI Considerations file for how pipelines handle this.

# `@Testcontainers` and `@Container`

```java
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers   // JUnit 5 extension: manages the lifecycle of any @Container fields automatically
class OrderRepositoryContainerTest {

    @Container   // instance field -> a NEW container is started before EACH test method, stopped after
    PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");

    @Test
    void containerIsRunningAndReachable() {
        assertThat(postgres.isRunning()).isTrue();
        // postgres.getJdbcUrl(), getUsername(), getPassword() expose real, dynamically-assigned
        // connection details -- the host port is chosen at random to avoid collisions between runs.
    }
}
```

--> **`@Testcontainers`** is the JUnit 5 extension that hooks container start/stop into the test lifecycle -- without it, `@Container` fields are just ordinary fields that Testcontainers has no reason to manage.
--> **`@Container` field scope controls lifecycle**: an **instance field** gets a fresh container per test method (maximum isolation, maximum overhead); a **`static` field** is shared across all test methods in the class, started once before the first test and stopped after the last (much faster, but tests can leak state into each other through it unless you clean up explicitly, e.g. in `@AfterEach`).

```java
@Testcontainers
class OrderRepositorySharedContainerTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    // static -> ONE container instance shared across all @Test methods in this class

    @AfterEach
    void cleanUpRows() {
        // Since the container (and its data) persists between tests, explicitly reset state
        // if tests must not see each other's data -- e.g. TRUNCATE tables here, or wrap tests
        // in @Transactional as shown in the REST/DB testing file.
    }
}
```

# `GenericContainer` -- Running Anything

--> Not every technology has a purpose-built module. `GenericContainer` runs ANY Docker image, letting you configure ports, environment variables, startup wait conditions, and volumes manually.

```java
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

@Container
static GenericContainer<?> wiremock = new GenericContainer<>(
        DockerImageName.parse("wiremock/wiremock:3.5.4"))
    .withExposedPorts(8080)
    .withEnv("WIREMOCK_OPTIONS", "--verbose")
    .waitingFor(Wait.forHttp("/__admin/mappings").forStatusCode(200));

// After startup, map the container's internal port to whatever host port Docker assigned:
String baseUrl = "http://" + wiremock.getHost() + ":" + wiremock.getMappedPort(8080);
```

--> **`withExposedPorts(8080)`** declares which port INSIDE the container to expose -- Docker then maps it to a random free port on the host, retrieved via `getMappedPort(8080)`. Never hardcode the host-side port; it changes on every run and across parallel test executions.
--> **`waitingFor(...)`** defines the READINESS check Testcontainers polls before considering the container "started." Without an appropriate wait strategy, a test can start issuing requests to a container whose process is running but whose application inside hasn't finished booting yet -- a classic source of flaky first-request failures. Common strategies: `Wait.forListeningPort()` (default for many modules), `Wait.forHttp(path)`, `Wait.forLogMessage(regex, times)`.

# Purpose-Built Modules

--> Testcontainers ships pre-configured container classes for popular technologies -- they wrap `GenericContainer` with sensible defaults (correct wait strategy, standard ports, convenience accessors), so you rarely need `GenericContainer` for anything mainstream.

```java
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.containers.GenericContainer;

// PostgreSQL
@Container
static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
    .withDatabaseName("orders")
    .withUsername("app")
    .withPassword("secret");
// Accessors: getJdbcUrl(), getUsername(), getPassword(), getDriverClassName()

// Kafka
@Container
static ConfluentKafkaContainer kafka =
    new ConfluentKafkaContainer("confluentinc/cp-kafka:7.6.1");
// Accessor: getBootstrapServers()

// Redis has no first-party module; GenericContainer is the common approach
@Container
static GenericContainer<?> redis = new GenericContainer<>(
        DockerImageName.parse("redis:7-alpine"))
    .withExposedPorts(6379);
// Connection: redis.getHost() + ":" + redis.getMappedPort(6379)
```

--> Available modules span most mainstream infrastructure: relational DBs (`postgresql`, `mysql`, `mariadb`, `mssqlserver`, `oracle-xe`), messaging (`kafka`, `rabbitmq`, `pulsar`), NoSQL (`mongodb`, `cassandra`, `elasticsearch`), caching (no first-party Redis module as of recent Testcontainers versions -- `GenericContainer` is standard), and general-purpose tools (`localstack` for AWS service emulation, `nginx`, `toxiproxy` for network-fault simulation).
--> Check the Testcontainers module list before hand-rolling a `GenericContainer` setup for something mainstream -- purpose-built modules save real boilerplate and encode known-good wait strategies.

# Container Lifecycle in Detail

```text
Test class starts
  -> Testcontainers extension activates (@Testcontainers)
  -> For each @Container field: container.start() called
       - Docker pulls the image if not cached locally (slow on first run/CI, cached after)
       - Container process starts
       - Wait strategy polls until ready (or times out and fails the test with a clear error)
  -> Test method(s) run against the now-ready container
  -> container.stop() called (instance field: per test; static field: once, after all tests in class)
       - Container and its ephemeral state are destroyed -- nothing persists to the next run
```

--> **Startup cost is real** -- pulling an image (first time) and booting a database engine typically costs hundreds of milliseconds to a few seconds. This is the direct trade-off against H2/mocks, and exactly why lifecycle scope (per-test vs. per-class vs. singleton-across-classes, covered in the next file) matters for keeping a Testcontainers-based suite fast.
--> **Ryuk** is a companion container Testcontainers starts automatically to garbage-collect leftover containers if a JVM crashes mid-test-run without a clean shutdown -- it's what prevents orphaned containers from accumulating on a dev machine or CI runner. It can be disabled (`TESTCONTAINERS_RYUK_DISABLED=true`) but that's rarely advisable outside constrained CI sandboxes that already handle cleanup themselves.

# Common Gotchas and Best Practices

--> **Pin image tags, don't use `latest`.** `postgres:16-alpine` is reproducible; `postgres:latest` silently changes behavior whenever the tag is repointed upstream, which can break tests with no corresponding code change.
--> **Prefer purpose-built modules over `GenericContainer`** when one exists -- they encode a correct wait strategy so you don't have to discover the hard way that "container started" and "database ready to accept connections" are different moments.
--> **Never hardcode ports.** Always read `getMappedPort(...)`/`getJdbcUrl()` etc. at runtime -- Testcontainers deliberately randomizes host ports so parallel test runs (including CI running multiple jobs concurrently) don't collide.
--> **Static vs. instance `@Container` is a correctness/speed trade-off, not a style choice.** Instance fields give full isolation but multiply startup cost by test count; static fields are fast but require you to reason about (and usually clean up) shared state between tests.
--> **Docker must be running wherever the tests execute.** A missing Docker daemon fails fast with a clear connection error locally, but in CI this needs explicit provisioning -- covered in the CI Considerations file.
--> **First run is always slower** (image pull) -- don't judge Testcontainers startup cost from a cold cache; subsequent runs reuse the locally cached image.
