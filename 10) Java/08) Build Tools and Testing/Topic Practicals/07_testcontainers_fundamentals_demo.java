/**
 * 07_testcontainers_fundamentals_demo.java
 *
 * Demonstrates, with heavily commented illustrative test code, the core
 * Testcontainers building blocks covered in the fundamentals theory chapter:
 *     1. @Testcontainers + @Container with PostgreSQLContainer -- the basic shape
 *     2. GenericContainer with a custom image, exposed port, and wait strategy
 *     3. Container lifecycle contrast -- instance field (per-test) vs. static field (per-class)
 *     4. Comments on Ryuk, image pinning, and mapped-port safety
 *
 * Covers Theory chapter:
 *     08) Build Tools and Testing/Theory/07 Testcontainers Fundamentals.md
 *
 * IMPORTANT -- this file will NOT compile or run as a plain .java file with `javac`/`java`.
 * It requires:
 *   - A real Docker (or compatible) daemon reachable wherever the test JVM runs -- NOT
 *     available in this environment, which is exactly why this file is illustrative only.
 *   - Testcontainers core + junit-jupiter + postgresql modules on the test classpath.
 *   - JUnit 5 + AssertJ (from `spring-boot-starter-test`, or standalone JUnit 5 + AssertJ
 *     if used outside a Spring Boot project -- nothing here actually requires Spring Boot,
 *     Testcontainers alone is a plain JUnit 5 extension).
 *
 * Minimal Maven dependencies (pom.xml) to make ALL sections below compile:
 *     <dependency>
 *         <groupId>org.testcontainers</groupId>
 *         <artifactId>testcontainers</artifactId>
 *         <version>1.19.7</version>
 *         <scope>test</scope>
 *     </dependency>
 *     <dependency>
 *         <groupId>org.testcontainers</groupId>
 *         <artifactId>junit-jupiter</artifactId>
 *         <version>1.19.7</version>
 *         <scope>test</scope>
 *     </dependency>
 *     <dependency>
 *         <groupId>org.testcontainers</groupId>
 *         <artifactId>postgresql</artifactId>
 *         <version>1.19.7</version>
 *         <scope>test</scope>
 *     </dependency>
 *     <dependency>
 *         <groupId>org.junit.jupiter</groupId>
 *         <artifactId>junit-jupiter</artifactId>
 *         <scope>test</scope>
 *     </dependency>
 *     <dependency>
 *         <groupId>org.assertj</groupId>
 *         <artifactId>assertj-core</artifactId>
 *         <scope>test</scope>
 *     </dependency>
 *
 * Run (once dropped into a real project with Docker available):
 *     mvn test -Dtest=PostgresContainerBasicsTest
 *     mvn test -Dtest=GenericContainerWiremockTest
 *     mvn test -Dtest=InstanceVsStaticContainerLifecycleTest
 */

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;


// =============================================================================
// 1) @Testcontainers + @Container -- the basic shape, using the purpose-built
//    PostgreSQLContainer module
// =============================================================================

/**
 * @Testcontainers is the JUnit 5 extension that hooks container start/stop into
 * the test lifecycle -- without it, the @Container field below would just be an
 * ordinary field Testcontainers has no reason to manage.
 *
 * This class uses an INSTANCE field -- a fresh Postgres container is started
 * before EACH @Test method and stopped after it. Maximum isolation (no test can
 * ever see another test's data), maximum overhead (every test pays full
 * container-startup cost). Contrast with section 3 below.
 */
@Testcontainers
class PostgresContainerBasicsTest {

    @Container
    PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine") // pinned tag, never "latest"
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @Test
    void containerIsRunningAndReachableWithRealConnectionDetails() throws Exception {
        assertThat(postgres.isRunning()).isTrue();

        // getJdbcUrl()/getUsername()/getPassword() expose the REAL, dynamically
        // assigned connection details -- the host port is chosen at random by
        // Docker to avoid collisions between parallel runs. NEVER hardcode a port.
        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement statement = connection.createStatement()) {

            statement.execute("CREATE TABLE demo (id INT PRIMARY KEY, name VARCHAR(50))");
            statement.execute("INSERT INTO demo VALUES (1, 'hello')");

            var resultSet = statement.executeQuery("SELECT name FROM demo WHERE id = 1");
            resultSet.next();
            assertThat(resultSet.getString("name")).isEqualTo("hello");
            // This is a REAL Postgres engine -- real SQL dialect, real constraint
            // enforcement, none of H2's "compatibility mode" approximation gaps.
        }
    }
}


// =============================================================================
// 2) GenericContainer -- running an image with no purpose-built Testcontainers
//    module, with an explicit exposed port and readiness (wait) strategy
// =============================================================================

/**
 * Not every technology has a dedicated container class. GenericContainer runs
 * ANY Docker image, letting you configure ports, environment variables, and a
 * wait strategy by hand. Here: a WireMock instance used to stub an external
 * HTTP dependency the code under test calls.
 */
@Testcontainers
class GenericContainerWiremockTest {

    @Container
    static GenericContainer<?> wiremock = new GenericContainer<>(
            DockerImageName.parse("wiremock/wiremock:3.5.4"))
            .withExposedPorts(8080) // the PORT INSIDE the container -- Docker maps it to a random host port
            .withEnv("WIREMOCK_OPTIONS", "--verbose")
            // waitingFor(...) is the readiness check Testcontainers polls before
            // considering the container "started." Without this, a test could
            // start issuing requests before WireMock has finished booting inside
            // the container -- a classic source of flaky first-request failures.
            .waitingFor(Wait.forHttp("/__admin/mappings").forStatusCode(200));

    @Test
    void wiremockAdminApiIsReachableOnceWaitStrategySucceeds() {
        assertThat(wiremock.isRunning()).isTrue();

        // Never hardcode "8080" as the host-side port -- always resolve it at
        // runtime via getMappedPort(), since Docker assigns it randomly.
        String baseUrl = "http://" + wiremock.getHost() + ":" + wiremock.getMappedPort(8080);

        assertThat(baseUrl).startsWith("http://");
        // A real HTTP client (e.g. RestTemplate/WebClient/HttpClient) would then
        // call baseUrl + "/__admin/mappings" to register stubs, or the app under
        // test would be configured to call this container instead of the real
        // external service -- omitted here to keep the example focused on the
        // container-configuration mechanics themselves.
    }
}


// =============================================================================
// 3) Container lifecycle contrast -- instance field vs. static field
// =============================================================================

/**
 * Demonstrates BOTH scoping strategies side by side, matching the theory
 * file's explicit contrast. Field scope is a correctness/speed TRADE-OFF, not
 * a style choice:
 *   - instance field -> new container per @Test method (isolation, high cost)
 *   - static field    -> one container shared by the whole class (speed, needs
 *                         explicit cleanup to avoid tests leaking state into
 *                         each other)
 */
@Testcontainers
class InstanceVsStaticContainerLifecycleTest {

    // INSTANCE FIELD: a brand-new container is started before EVERY test method
    // in this class and destroyed after it. Two @Test methods below never see
    // the same container instance -- fully isolated, but startup cost is paid twice.
    @Container
    PostgreSQLContainer<?> perTestPostgres = new PostgreSQLContainer<>("postgres:16-alpine");

    // STATIC FIELD: ONE container instance, shared across every @Test method in
    // this class -- started once before the first test, stopped once after the
    // last. Much faster for a class with many tests, but data written by one
    // test method is still there for the next unless explicitly cleaned up.
    @Container
    static PostgreSQLContainer<?> sharedPostgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @AfterEach
    void cleanUpSharedContainerState() {
        // Because sharedPostgres persists across test methods, explicit cleanup
        // is this test's responsibility -- e.g. TRUNCATE tables here, or (as
        // shown in file 04 of this folder) wrap tests in @Transactional so each
        // one's writes roll back automatically instead.
    }

    @Test
    void perTestContainer_isFreshEveryTime() {
        assertThat(perTestPostgres.isRunning()).isTrue();
    }

    @Test
    void sharedContainer_isTheSameInstanceAcrossTestMethods() {
        assertThat(sharedPostgres.isRunning()).isTrue();
        // If this test and another both ran against sharedPostgres and inserted
        // rows without cleanup, each would see the other's leftover data -- the
        // exact hazard @AfterEach above (or @Transactional rollback) exists to guard against.
    }
}


/*
 * =============================================================================
 * COMMENTARY -- notes that don't belong inside any single test class above
 * =============================================================================
 *
 * Ryuk: Testcontainers automatically starts a companion "Ryuk" container
 * alongside your test containers. Its job is to garbage-collect any leftover
 * containers if the JVM crashes mid-run without a clean shutdown -- this is
 * what prevents orphaned Postgres/WireMock containers from silently
 * accumulating on a dev machine or CI runner over time. It CAN be disabled via
 * TESTCONTAINERS_RYUK_DISABLED=true, but that's rarely a good idea outside
 * constrained CI sandboxes that already guarantee their own cleanup.
 *
 * Image pinning: every container above uses a specific tag (postgres:16-alpine,
 * wiremock:3.5.4) rather than "latest". An unpinned tag can silently change
 * behavior whenever upstream repoints it -- breaking tests with no
 * corresponding code change on your side.
 *
 * Purpose-built modules vs. GenericContainer: PostgreSQLContainer in section 1
 * already knows the correct wait strategy and exposes convenience accessors
 * (getJdbcUrl(), etc.) -- GenericContainer in section 2 is the fallback for
 * anything without a dedicated module, and requires you to get the wait
 * strategy right yourself (a wrong/missing one is a classic source of flaky
 * "container started but app inside wasn't ready" failures).
 * =============================================================================
 */
