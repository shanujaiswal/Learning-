/**
 * 06_test_pyramid_demo.java
 *
 * Demonstrates, with heavily commented illustrative test code, the levels of the
 * test pyramid and how Spring Boot's test-slice annotations map onto them:
 *     1. A plain unit test -- pure logic, everything mocked, no Spring context at all
 *     2. @WebMvcTest -- controller-layer slice, MockMvc + @MockBean service
 *     3. @DataJpaTest -- repository-layer slice, real JPA + an embedded/real DB
 *     4. @SpringBootTest -- full application context, the top of the "how real is it" scale
 *     5. Comments contrasting cost/speed/realism trade-offs across all four
 *
 * Covers Theory chapter:
 *     08) Build Tools and Testing/Theory/06 Integration Testing Strategy and the Test Pyramid.md
 *
 * IMPORTANT -- this file will NOT compile or run as a plain .java file with `javac`/`java`.
 * It requires a real Spring Boot project on the classpath, specifically:
 *   - `spring-boot-starter-test` (JUnit 5, Mockito, AssertJ, MockMvc, @DataJpaTest, and friends)
 *     for sections 2-4.
 *   - `spring-boot-starter-web` + `spring-boot-starter-data-jpa` (+ an H2 runtime dependency for
 *     the @DataJpaTest slice, or a Testcontainers Postgres setup as shown in files 02-04 of this
 *     folder) for the OrderController/OrderRepository/Order classes these tests assume exist.
 *   - Section 1 (the plain unit test) is the ONE exception -- it needs only JUnit 5 + Mockito,
 *     no Spring at all, which is exactly the point being illustrated.
 * It is illustrative code meant to be copy-pasted/adapted into an actual Spring Boot project --
 * e.g. split into separate files under src/test/java/... in a Maven/Gradle project -- not
 * compiled standalone.
 *
 * Minimal Maven dependencies (pom.xml) to make ALL sections below compile:
 *     <dependency>
 *         <groupId>org.springframework.boot</groupId>
 *         <artifactId>spring-boot-starter-web</artifactId>
 *     </dependency>
 *     <dependency>
 *         <groupId>org.springframework.boot</groupId>
 *         <artifactId>spring-boot-starter-data-jpa</artifactId>
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
 * Run (once dropped into a real Spring Boot project):
 *     mvn test                              (runs the whole suite)
 *     mvn test -Dtest=OrderPricingUnitTest   (runs just the pure unit test, section 1)
 */

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.MediaType;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


// =============================================================================
// Tiny production-side classes these tests assume exist elsewhere in the app.
// In a real project these live under src/main/java, NOT alongside the tests --
// included here only so the test classes below are self-contained and readable
// without cross-referencing another file.
// =============================================================================

@Entity
class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String status;
    private int quantity;
    private double unitPrice;

    public Order() {
    }

    public Order(Long id, String status, int quantity, double unitPrice) {
        this.id = id;
        this.status = status;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public Long getId() {
        return id;
    }

    public String getStatus() {
        return status;
    }

    public int getQuantity() {
        return quantity;
    }

    public double getUnitPrice() {
        return unitPrice;
    }
}

interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByStatus(String status);
}

interface OrderService {
    Optional<Order> findById(Long id);
}

@RestController
class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/orders/{id}")
    public Order getOrder(@PathVariable Long id) {
        return orderService.findById(id).orElse(null);
    }
}

// A tiny piece of PURE business logic, deliberately free of any Spring/DB/HTTP
// dependency -- exactly the shape of class the base of the pyramid should be
// made of. It takes primitives/POJOs in, returns a primitive/POJO out.
class OrderPricingCalculator {

    double calculateTotal(int quantity, double unitPrice, double discountRate) {
        if (quantity < 0) {
            throw new IllegalArgumentException("quantity cannot be negative");
        }
        double subtotal = quantity * unitPrice;
        return subtotal - (subtotal * discountRate);
    }
}


// =============================================================================
// 1) PLAIN UNIT TEST -- base of the pyramid: milliseconds, isolated, no Spring
// =============================================================================

/**
 * No Spring annotations anywhere in this class -- no @SpringBootTest, no test
 * slice, not even an ApplicationContext. This is the cheapest, fastest test
 * style there is: pure JUnit 5 + Mockito against plain Java objects.
 *
 * Contrast with the tests below: this class boots NOTHING. It runs in single-digit
 * milliseconds and its failure signal points at exactly one method. Compare that to
 * @SpringBootTest in section 4, which can take seconds just to start the context
 * before a single assertion runs.
 */
class OrderPricingUnitTest {

    private final OrderPricingCalculator calculator = new OrderPricingCalculator();

    @Test
    void calculateTotal_appliesDiscountCorrectly() {
        double total = calculator.calculateTotal(3, 10.0, 0.10);

        assertThat(total).isEqualTo(27.0); // (3 * 10.0) - 10% = 27.0
    }

    @Test
    void calculateTotal_rejectsNegativeQuantity() {
        assertThat(
                org.junit.jupiter.api.Assertions.assertThrows(
                        IllegalArgumentException.class,
                        () -> calculator.calculateTotal(-1, 10.0, 0.0))
        ).hasMessageContaining("negative");
    }

    /**
     * A classic "unit test with a mock" -- this is verifying ORCHESTRATION
     * (did my code call its collaborator correctly?), not boundary correctness.
     * Per the theory file's checklist: "does the service call repository.save()
     * exactly once" is exactly this kind of question -- a mock proves it far
     * more cheaply than a real database ever could. No Spring context needed
     * even though a "service" and "repository" concept is involved here.
     */
    @Test
    void plainMockitoMock_verifiesOrchestrationWithoutSpringOrADatabase() {
        OrderRepository mockRepo = mock(OrderRepository.class); // a plain Mockito mock, NOT a @MockBean
        when(mockRepo.findByStatus("PENDING"))
                .thenReturn(List.of(new Order(1L, "PENDING", 2, 9.99)));

        List<Order> pending = mockRepo.findByStatus("PENDING");

        assertThat(pending).hasSize(1);
        verify(mockRepo).findByStatus("PENDING"); // proves the call happened, not that the DB query is correct
    }
}


// =============================================================================
// 2) @WebMvcTest -- controller-layer slice: fast, MockMvc, service layer mocked
// =============================================================================

/**
 * Loads only MVC infrastructure plus the named controller -- OrderService is
 * NOT scanned into this context, so it's supplied via @MockBean. Faster than
 * @SpringBootTest (no full context, no DB, no real server) but more real than
 * the pure unit test above: this exercises the actual DispatcherServlet,
 * argument resolution, and JSON serialization pipeline.
 */
@WebMvcTest(OrderController.class)
class OrderControllerSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService; // real service NOT loaded; this replaces it in the sliced context

    @Test
    void getOrder_returns200AndJsonBody() throws Exception {
        when(orderService.findById(1L)).thenReturn(Optional.of(new Order(1L, "PENDING", 2, 9.99)));

        mockMvc.perform(get("/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
        // Real HTTP-shaped dispatch happened here, but no real socket, no real
        // database -- this proves the controller/JSON layer, nothing below it.
    }
}


// =============================================================================
// 3) @DataJpaTest -- repository-layer slice: real JPA, real (here: embedded) DB
// =============================================================================

/**
 * Loads the JPA slice only -- @Entity classes, Spring Data repositories, the
 * transaction manager -- and rolls back after each test automatically.
 *
 * NOTE the gotcha from the theory file: by DEFAULT @DataJpaTest substitutes an
 * embedded database (e.g. H2) even if the real app uses Postgres. That's fine
 * for illustrating the annotation here, but it's exactly the "false confidence"
 * gap Testcontainers exists to close -- see files 02-04 in this folder for how
 * @AutoConfigureTestDatabase(replace = Replace.NONE) plus a real Postgres
 * container fixes this for production-realistic query tests.
 */
@DataJpaTest
class OrderRepositorySliceTest {

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void findByStatus_returnsOnlyMatchingOrders() {
        orderRepository.save(new Order(null, "PENDING", 1, 5.0));
        orderRepository.save(new Order(null, "SHIPPED", 1, 5.0));

        assertThat(orderRepository.findByStatus("PENDING")).hasSize(1);
        // No manual cleanup needed -- @DataJpaTest wraps each test in a
        // transaction that is rolled back automatically when the method returns.
    }
}


// =============================================================================
// 4) @SpringBootTest -- full context: the most realistic, most expensive level
// =============================================================================

/**
 * Boots the ENTIRE application context -- every @Component/@Service/@Repository/
 * @Configuration, exactly as production would via component scan. This is the
 * top of the "how real is it" scale for a Spring-level test (still short of a
 * true E2E test against a deployed, networked process -- see the theory file's
 * pyramid diagram). Typically costs 1-5+ seconds of context startup, though
 * Spring caches and reuses an identical context across test classes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderServiceApplicationIntegrationTest {

    @org.springframework.boot.test.web.server.LocalServerPort
    private int port;

    @Test
    void applicationContextStartsSuccessfully() {
        // The mere fact that this test method runs at all proves every bean in
        // the context wired up correctly -- a whole category of "works in every
        // unit test, breaks at runtime" misconfiguration bugs would fail HERE,
        // not in any of the narrower slices above.
        assertThat(port).isGreaterThan(0);
    }
}


/*
 * =============================================================================
 * COMMENTARY -- Pyramid trade-offs across the four levels demonstrated above
 * =============================================================================
 *
 * | Section | Style                | What's real                  | Approx speed | Failure signal points at |
 * |---------|----------------------|-------------------------------|--------------|----------------------------|
 * | 1       | Plain unit test      | Nothing external               | Milliseconds | One class/method exactly   |
 * | 2       | @WebMvcTest           | MVC dispatch, JSON, validation | Tens of ms   | Controller/JSON layer      |
 * | 3       | @DataJpaTest          | JPA, transactions, (embedded) DB | ~100ms-1s  | Repository/query layer     |
 * | 4       | @SpringBootTest       | Entire application context     | 1-5+ seconds | "Somewhere in the wiring"  |
 *
 * Choosing the narrowest slice that still proves what you care about keeps the
 * suite fast -- reserve @SpringBootTest for the cases that genuinely need the
 * whole chain (see file 04 in this folder for a full HTTP-to-database example),
 * and keep the base of the pyramid (section 1's style) as wide as possible by
 * extracting pure logic out of classes that would otherwise force a slower test.
 * =============================================================================
 */
