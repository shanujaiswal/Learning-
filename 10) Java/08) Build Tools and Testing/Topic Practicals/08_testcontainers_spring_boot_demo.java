/**
 * 08_testcontainers_spring_boot_demo.java
 *
 * Demonstrates, with heavily commented illustrative test code, how to wire a
 * Testcontainers-managed container's connection details into a Spring
 * ApplicationContext:
 *     1. @DynamicPropertySource -- the manual, always-available wiring mechanism
 *     2. @ServiceConnection (Spring Boot 3.1+) -- the near-zero-boilerplate alternative
 *     3. The singleton container base-class pattern for fast, shared test infrastructure
 *
 * Covers Theory chapter:
 *     08) Build Tools and Testing/Theory/08 Testcontainers with Spring Boot.md
 *
 * IMPORTANT -- this file will NOT compile or run as a plain .java file with `javac`/`java`.
 * It requires:
 *   - A real Docker daemon reachable wherever the test JVM runs -- NOT available in this
 *     environment, which is exactly why this file is illustrative only.
 *   - `spring-boot-starter-test`, `spring-boot-testcontainers` (required specifically for
 *     @ServiceConnection), and the Testcontainers `postgresql` + `junit-jupiter` modules.
 *   - A real Spring Boot 3.1+ project for section 2's @ServiceConnection example; section 1's
 *     @DynamicPropertySource works on any Spring Framework version and needs no extra dependency
 *     beyond Testcontainers itself.
 *
 * Minimal Maven dependencies (pom.xml) to make ALL sections below compile:
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
 *
 * Run (once dropped into a real Spring Boot 3.1+ project with Docker available):
 *     mvn test -Dtest=OrderServiceDynamicPropertySourceTest
 *     mvn test -Dtest=OrderServiceServiceConnectionTest
 *     mvn test -Dtest=OrderServiceSingletonContainerTest
 */

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import static org.assertj.core.api.Assertions.assertThat;


// =============================================================================
// Tiny production-side classes these tests assume exist elsewhere in the app.
// =============================================================================

@Entity
class Customer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String email;

    public Customer() {
    }

    public Customer(Long id, String email) {
        this.id = id;
        this.email = email;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }
}

interface CustomerRepository extends JpaRepository<Customer, Long> {
}


// =============================================================================
// 1) @DynamicPropertySource -- manual wiring, works on any Spring Framework
//    version, for any technology
// =============================================================================

/**
 * A raw @Container field starts a real Postgres, but Spring's DataSource still
 * needs to be TOLD the container's connection details -- and the mapped host
 * port isn't known until AFTER the container has already started, which is
 * later than Spring would normally read application.properties.
 * @DynamicPropertySource bridges that gap.
 */
@SpringBootTest
@Testcontainers
class OrderServiceDynamicPropertySourceTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("orders")
            .withUsername("app")
            .withPassword("secret");

    /**
     * MUST be a static method -- it runs before the Spring ApplicationContext
     * (and therefore any test instance wiring) exists, at the JUnit class
     * level, exactly once. Note the method REFERENCES (postgres::getJdbcUrl)
     * rather than calling postgres.getJdbcUrl() immediately -- the registry
     * stores each property as a lazily-evaluated Supplier<Object>, deferring
     * evaluation to the moment Spring actually needs it, by which point the
     * container has genuinely finished starting and the real mapped port is known.
     */
    @DynamicPropertySource
    static void registerPostgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private CustomerRepository customerRepository;

    @Test
    void springContextUsesTheRealContainerDatabase() {
        Customer saved = customerRepository.save(new Customer(null, "ada@example.com"));

        assertThat(saved.getId()).isNotNull();
        // Any @Autowired DataSource/JdbcTemplate/JPA repository in this context
        // now points at the real running Postgres container, not whatever
        // application.properties configured by default.
    }
}


// =============================================================================
// 2) @ServiceConnection (Spring Boot 3.1+) -- near-zero-boilerplate alternative
// =============================================================================

/**
 * For common technologies, Spring Boot 3.1+ recognizes a running Testcontainers
 * container and auto-configures the matching connection properties itself --
 * eliminating the manual @DynamicPropertySource method entirely for the common
 * case. Requires the `spring-boot-testcontainers` dependency in addition to
 * the technology-specific Testcontainers module.
 */
@SpringBootTest
@Testcontainers
class OrderServiceServiceConnectionTest {

    @Container
    @ServiceConnection // that's it -- no @DynamicPropertySource method needed below
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private CustomerRepository customerRepository;

    @Test
    void contextLoadsAgainstRealDatabaseWithNoManualPropertyWiring() {
        // Spring Boot recognizes this is a PostgreSQLContainer and auto-configures
        // spring.datasource.* (url, username, password, driver) to match it --
        // no property names to get right, no risk of a typo'd key silently
        // falling back to an unrelated default.
        Customer saved = customerRepository.save(new Customer(null, "grace@example.com"));

        assertThat(saved.getId()).isNotNull();
    }

    // Use @ServiceConnection by default for anything it supports (PostgreSQL,
    // MySQL, MongoDB, Redis, Kafka, RabbitMQ, and more as of Spring Boot 3.1+).
    // Fall back to @DynamicPropertySource (section 1) for a custom
    // GenericContainer-based service or a technology it doesn't recognize.
}


// =============================================================================
// 3) Singleton container base-class pattern -- one container for the whole
//    test run, shared across every test class that extends this base
// =============================================================================

/**
 * The naive approach -- a fresh static @Container per test CLASS -- means
 * every integration test class pays full container startup cost
 * independently. With dozens of test classes that adds real minutes to a
 * suite that could otherwise run in seconds. Starting the container in a
 * STATIC INITIALIZER BLOCK (rather than letting @Container manage it) means
 * it starts exactly once, the first time this class is loaded by the JVM, and
 * is never explicitly stopped -- it rides along until the JVM process ends
 * (Ryuk cleans it up regardless).
 */
abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
                .withReuse(true); // opt-in: also allows reuse ACROSS separate local `mvn test` invocations
        POSTGRES.start();
        // Deliberately NEVER calling POSTGRES.stop() here -- JVM shutdown at
        // the end of the whole test run is what stops it. Calling .stop() from
        // any subclass would break every OTHER test class still relying on
        // this same running container.
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}

/**
 * Inherits the already-started, already-registered container from
 * AbstractIntegrationTest -- no per-class startup cost paid here.
 */
@SpringBootTest
class OrderServiceSingletonContainerTest extends AbstractIntegrationTest {

    @Autowired
    private CustomerRepository customerRepository;

    @Test
    void usesTheSharedSingletonContainer() {
        assertThat(customerRepository).isNotNull();
        // Because this container is shared across the WHOLE test run (every
        // class extending AbstractIntegrationTest reuses it), per-test data
        // isolation must come from elsewhere -- e.g. @Transactional rollback
        // or @Sql cleanup, both demonstrated in file 04 of this folder.
    }
}

/**
 * A second, independent test class extending the SAME base -- demonstrating
 * that it reuses the identical POSTGRES container instance rather than
 * starting a new one, which is the entire point of the pattern.
 */
@SpringBootTest
class CustomerServiceSingletonContainerTest extends AbstractIntegrationTest {

    @Autowired
    private CustomerRepository customerRepository;

    @Test
    void alsoUsesTheSameSharedContainerInstance() {
        // AbstractIntegrationTest.POSTGRES here is literally the same object
        // OrderServiceSingletonContainerTest used above -- started once, in
        // the static block, the first time either class was loaded.
        assertThat(AbstractIntegrationTest.POSTGRES.isRunning()).isTrue();
    }
}
