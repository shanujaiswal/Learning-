# The Testing Pyramid, Revisited for Spring

--> The classic testing pyramid -- lots of fast unit tests at the base, fewer integration tests in the middle, a handful of end-to-end tests at the top -- applies directly to Spring apps, but Spring adds a wrinkle: many "integration-shaped" tests only need a SLICE of the application context, not the whole thing. Understanding which layer a test belongs to is the difference between a test suite that runs in 4 seconds and one that takes 4 minutes.
--> **Unit tests (no Spring context at all)** -- plain JUnit + Mockito, testing a single class in isolation with its dependencies mocked out. No `ApplicationContext` is started, no beans are wired -- it's just Java objects. These are the fastest tests you can write (milliseconds each) and should make up the bulk of your suite.
--> **Slice tests (partial Spring context)** -- Spring Boot's `@...Test` slice annotations (`@WebMvcTest`, `@DataJpaTest`, etc.) boot only the beans relevant to one architectural layer -- e.g. `@WebMvcTest` wires up MVC infrastructure (controllers, `@ControllerAdvice`, converters) but NOT your `@Service` or `@Repository` beans, which you mock instead. Slower than a pure unit test (a mini context still has to start), but far faster than the full app.
--> **Full integration tests (`@SpringBootTest`)** -- boots the ENTIRE application context, exactly as it would run in production (all beans, all auto-configuration). Necessary for verifying that everything actually wires together correctly, but by far the slowest and heaviest test type -- context startup alone can take seconds, and that cost is paid per test class (or per context "shape" if Spring can't reuse a cached context between classes).

```text
        /\
       /  \      End-to-end / full-stack (few) -- @SpringBootTest, real server, maybe Testcontainers
      /----\
     /      \    Slice / integration tests (some) -- @WebMvcTest, @DataJpaTest
    /--------\
   /          \  Unit tests (many) -- plain JUnit + Mockito, no Spring context
  /____________\
```

--> **Why loading the full context for every test is wasteful** -- if you have 200 test methods and every single one is annotated `@SpringBootTest`, Spring will try to start the full context for each test CLASS. Spring does cache contexts between test classes when their configuration is identical (same annotations, same properties, same profiles), so you're not always paying the full cost -- but any variation (different `@MockBean`, different active profile, different property overrides) busts that cache and forces a fresh context boot. A test suite riddled with slightly-different `@SpringBootTest` configurations can end up starting the context dozens of times, turning a suite that should run in seconds into one that takes minutes. The fix is almost always "use a narrower slice, or a plain unit test, unless you specifically need the whole graph wired together."

# @SpringBootTest -- Full Context Integration Testing

--> `@SpringBootTest` is the "give me everything" annotation -- it finds your `@SpringBootApplication` class (or an explicit `classes = {...}`), boots the full `ApplicationContext` with all auto-configuration applied, and lets you `@Autowired` any bean in the app exactly as it would be wired at runtime. Use it when you genuinely need to verify cross-layer behavior -- e.g. "does a real HTTP request actually flow through security, controller, service, and repository correctly."

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderServiceIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void createOrder_persistsAndReturnsCreated() {
        OrderRequest request = new OrderRequest("sku-123", 2);
        ResponseEntity<OrderResponse> response =
                restTemplate.postForEntity("/api/orders", request, OrderResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getSku()).isEqualTo("sku-123");
    }
}
```

--> **`webEnvironment` options** -- this is the setting that decides whether (and how) a real servlet container gets started:

```text
MOCK           -- (default) loads a WebApplicationContext with a MOCK servlet environment --
                  no real server/port, requests go through MockMvc-style simulated dispatch.
                  Cheapest web-aware option; pairs naturally with @AutoConfigureMockMvc.
RANDOM_PORT     -- starts a REAL embedded server (Tomcat/Netty/etc.) on a randomly chosen free
                  port, and makes that port available for injection (e.g. into TestRestTemplate
                  or WebTestClient) -- true end-to-end HTTP calls over the network stack.
DEFINED_PORT    -- starts a real embedded server on the port configured in application
                  properties (or 8080 by default) -- rarely used in CI since a fixed port
                  risks collisions with parallel test runs; mostly useful for manual/local runs.
NONE            -- loads the ApplicationContext WITHOUT starting any servlet environment at
                  all -- for tests that only care about non-web beans (services, repositories,
                  business logic) but still want the full application wiring.
```

--> **Rule of thumb** -- `RANDOM_PORT` when you want a real network round trip (most realistic, but slowest and requires `TestRestTemplate`/`WebTestClient`); `MOCK` when you want web-layer behavior without the overhead of a real socket; `NONE` when the test isn't about the web layer at all but still needs the whole app wired.

# Test Slice Annotations -- Loading Only What You Need

--> Spring Boot ships a family of `@...Test` annotations that each auto-configure a NARROW, curated subset of auto-configuration relevant to one layer, and by default DISABLE full auto-configuration and component scanning outside that layer. This is the key performance and isolation lever in Spring testing.

## @WebMvcTest -- Controller Layer Only

--> Boots just the Spring MVC infrastructure -- `@Controller`/`@RestController` beans, `@ControllerAdvice`, `Converter`/`Filter`/`HandlerInterceptor` beans, Jackson message converters -- and auto-configures a `MockMvc` instance for you. It deliberately does NOT scan `@Service`, `@Repository`, or `@Component` beans, so anything your controller depends on must be mocked, typically with `@MockBean` (or `@MockitoBean` in newer Spring Boot/Spring Framework 6.2+ naming).

```java
@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean                         // registers a Mockito mock AS a bean in this sliced context
    private ProductService productService;

    @Test
    void getProduct_returnsJsonBody() throws Exception {
        when(productService.findById(1L))
                .thenReturn(new Product(1L, "Keyboard", 49.99));

        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.name").value("Keyboard"));
    }
}
```

--> **Why this is fast** -- you're not starting a database connection pool, not scanning your whole `@Service` layer, not touching security auto-configuration (unless you explicitly include it) -- just controller + MVC plumbing. This is usually 5-10x faster to start than `@SpringBootTest`.

## @DataJpaTest -- Repository Layer Only

--> Boots just the JPA-related infrastructure -- `@Entity` classes, Spring Data JPA repositories, the `EntityManager` -- and by default swaps in an **in-memory embedded database** (H2, if it's on the classpath) instead of your real production datasource. Crucially, `@DataJpaTest` also wraps EACH TEST METHOD in a transaction that is **rolled back automatically** at the end of the test, so tests don't leak data into each other even without manual cleanup.

```java
@DataJpaTest
class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private TestEntityManager entityManager;   // convenience helper for setting up test data

    @Test
    void findByNameContaining_returnsMatchingProducts() {
        entityManager.persist(new Product(null, "Mechanical Keyboard", 89.99));
        entityManager.persist(new Product(null, "Wireless Mouse", 29.99));

        List<Product> results = productRepository.findByNameContainingIgnoreCase("keyboard");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).contains("Keyboard");
    }
    // No manual cleanup needed -- the transaction wrapping this test method rolls back here.
}
```

--> **`@AutoConfigureTestDatabase`** -- if you want `@DataJpaTest` to use your REAL configured datasource instead of swapping in an embedded one (e.g. to test against actual Postgres-specific SQL), annotate with `@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)`.

## @MockBean / @MockitoBean -- Mocking Collaborators Inside a Sliced Context

--> `@MockBean` (classic) / `@MockitoBean` (the newer name introduced as Spring Framework's own mocking support matured, Spring Boot 3.4+) tells Spring: "create a Mockito mock of this type and put it INTO the application context as a bean," replacing whatever real bean would have been there. This is different from a plain Mockito `@Mock` -- a `@MockBean` mock is context-aware and gets injected wherever the real bean would have been autowired, including into other beans Spring wires up for you.
--> This is the mechanism that makes slice tests possible at all -- `@WebMvcTest` can't provide a real `ProductService` (it's out of scope for that slice), but the controller still needs SOMETHING implementing that interface to be injectable, so `@MockBean` fills the gap.

# MockMvc -- Simulating HTTP Requests Without a Real Server

--> `MockMvc` performs a request against Spring's `DispatcherServlet` machinery WITHOUT actually opening a network socket or starting a servlet container -- it's a simulated dispatch that exercises real routing, argument resolution, validation, and serialization, just without the network layer. This makes it both fast AND realistic for controller-layer testing.

```java
mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name": "Monitor", "price": 199.99}
                    """))
        .andExpect(status().isCreated())
        .andExpect(header().exists("Location"))
        .andExpect(jsonPath("$.name").value("Monitor"))
        .andExpect(jsonPath("$.price").value(199.99));
```

--> **`perform()`** -- executes a simulated request, built with static helpers like `get()`, `post()`, `put()`, `delete()` (from `MockMvcRequestBuilders`).
--> **`content()` / `contentType()`** -- attach a request body (typically a JSON string, or use an `ObjectMapper` to serialize a Java object) and declare its media type, mirroring what a real client would send.
--> **`andExpect()`** -- assert against the response using matchers: `status().isOk()`, `status().isCreated()`, `content().contentType(...)`, `jsonPath("$.field").value(...)`, `header().string(...)`. Multiple `andExpect()` calls chain naturally to assert several aspects of one response.
--> **`andDo(print())`** -- a debugging aid that dumps the full request/response (headers, body, status) to the console -- invaluable when a test fails and you can't tell why just from the assertion message.

# Mockito Basics, Recapped in Spring Context

--> Plain Mockito (`@Mock`, `@InjectMocks`) and Spring's context-aware mocking (`@MockBean`/`@MockitoBean`) solve the same underlying problem -- "give me a fake collaborator so I can test one class in isolation" -- but operate at different levels.

```text
@Mock + @InjectMocks           -- PURE MOCKITO, no Spring involved at all.
                                   @Mock creates a standalone mock object.
                                   @InjectMocks creates a real instance of the class under test
                                   and manually injects the @Mock fields into its constructor/setters.
                                   Needs @ExtendWith(MockitoExtension.class) on the test class.
                                   Fastest possible test -- no ApplicationContext, no reflection
                                   magic beyond field injection.

@MockBean / @MockitoBean       -- SPRING-AWARE. Registers the mock INTO a real (sliced or full)
                                   ApplicationContext, so it gets wired into whatever other beans
                                   Spring constructs. Requires a Spring test context to exist at
                                   all (@WebMvcTest, @DataJpaTest, @SpringBootTest, etc.).
```

```java
// Pure Mockito unit test -- no Spring context, fastest possible
@ExtendWith(MockitoExtension.class)
class OrderCalculatorTest {

    @Mock
    private TaxService taxService;

    @InjectMocks
    private OrderCalculator orderCalculator;   // real object, taxService field injected as the mock

    @Test
    void calculatesTotalIncludingTax() {
        when(taxService.rateFor("NY")).thenReturn(0.08);
        BigDecimal total = orderCalculator.calculateTotal(new BigDecimal("100"), "NY");
        assertThat(total).isEqualByComparingTo("108.00");
    }
}
```

--> **When to reach for which** -- if the class under test has NO Spring-managed dependencies you need wired through a context (it's just plain Java composition), prefer `@Mock`/`@InjectMocks` -- it's strictly faster and simpler. Reach for `@MockBean`/`@MockitoBean` only when you're already inside a Spring test slice (or full context) and need to neutralize one specific bean within it.

# Embedded Test Databases (H2) vs Testcontainers

--> **H2 in-memory** -- the default for `@DataJpaTest` when H2 is on the classpath. Extremely fast (no external process, pure in-JVM), zero setup, but it's not byte-for-byte the same database engine as your production Postgres/MySQL -- SQL dialect quirks, specific functions, or database-specific constraints can pass against H2 and fail against the real thing (or vice versa).
--> **Testcontainers** -- spins up a REAL instance of your production database (Postgres, MySQL, etc.) in a Docker container, scoped to the test run. Slower to start (pulling/starting a container has real overhead) but eliminates the "works on H2, breaks on Postgres" class of bug entirely, since you're testing against the actual engine you deploy with.

```java
// Brief illustrative shape -- not exhaustive
@SpringBootTest
@Testcontainers
class ProductRepositoryPostgresIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
```

--> **Practical guideline** -- use H2 liberally for fast day-to-day `@DataJpaTest` runs; reach for Testcontainers when you specifically need production-parity (complex native queries, DB-specific types, migration scripts you want verified against the real engine) or in a dedicated slower CI stage.

# Packaging -- The Executable "Fat Jar"

--> `spring-boot-maven-plugin` (added automatically by Spring Initializr) repackages your normal Maven build output into a self-contained, EXECUTABLE jar -- one you can run with `java -jar` with no external application server, no classpath wrangling, nothing else installed beyond a JRE.

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
        </plugin>
    </plugins>
</build>
```

```text
mvn clean package
    -> produces target/my-app-0.0.1-SNAPSHOT.jar

java -jar target/my-app-0.0.1-SNAPSHOT.jar
    -> runs the app directly, embedded server (Tomcat by default) starts inside the same JVM
```

--> **Regular jar vs Spring Boot executable jar** -- a plain `jar` built by Maven contains only YOUR compiled classes and resources; it explicitly does NOT bundle its dependencies, so running it with `java -jar` fails immediately with `NoClassDefFoundError` unless every dependency is separately on the classpath. A Spring Boot executable jar solves this by NESTING all dependency jars inside itself (under `BOOT-INF/lib/`), plus your own classes under `BOOT-INF/classes/`, plus a special bootstrap class (`JarLauncher`) that knows how to load classes out of those nested jars at runtime -- something the standard JVM classloader can't do with a naive "jar-in-a-jar" on its own.
--> This is also why a Spring Boot app doesn't need an external Tomcat/Jetty installation -- the embedded server library is just another dependency bundled inside the fat jar, and `SpringApplication.run()` starts it programmatically as part of your `main()` method.

```text
my-app.jar
├── META-INF/
├── org/springframework/boot/loader/     <- JarLauncher and friends, the bootstrap classloader
├── BOOT-INF/
│   ├── classes/                         <- your compiled application classes + resources
│   └── lib/                             <- every dependency jar, nested whole, unmodified
```

# Actuator -- Production Observability Out of the Box

--> `spring-boot-starter-actuator` adds a set of ready-made HTTP endpoints (and JMX equivalents) that expose the running application's internal health and metadata, without you writing any of that plumbing yourself. It's the standard way a Spring Boot app talks to monitoring/orchestration tooling (load balancers, Kubernetes probes, Prometheus scrapers, dashboards).

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

```text
/actuator/health    -- overall app health (UP/DOWN), and per-component health (DB, disk space,
                        custom HealthIndicators) -- what load balancers and k8s liveness/readiness
                        probes poll to decide whether to route traffic to this instance.
/actuator/info       -- arbitrary static metadata you choose to expose (build version, git commit,
                        custom properties under `info.*` in application.yml).
/actuator/metrics    -- runtime metrics (JVM memory, GC pauses, HTTP request timings, thread
                        counts, and any custom Micrometer metrics you register) -- the raw feed
                        that tools like Prometheus/Grafana scrape and chart.
/actuator/env        -- the full resolved environment (property sources, active profiles) --
                        extremely useful for debugging "why is this config value not what I
                        expect," but also sensitive, since it can leak secrets if left wide open.
```

--> **Endpoints are NOT all exposed by default** -- out of the box, only `/actuator/health` is exposed over HTTP; everything else must be explicitly opted in, because endpoints like `/env` or `/heapdump` can leak sensitive internals if left open to the world.

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, info, metrics, env      # or "*" for everything -- avoid in production
  endpoint:
    health:
      show-details: when-authorized               # or "always" -- controls detail visibility
```

--> **Securing actuator endpoints** -- in any real deployment, actuator endpoints beyond `/health` should sit behind authentication/authorization (commonly via Spring Security, restricting `/actuator/**` to an admin role or an internal network only) and ideally on a SEPARATE management port (`management.server.port`) so they're not even reachable on the same port as your public API.
--> **Why this matters for production** -- Actuator is the single fastest way to make a Spring Boot app observable without hand-rolling health checks or metrics exporters -- it's the backbone that Kubernetes probes, uptime monitors, and metrics dashboards plug into on day one.

# Dockerizing a Spring Boot App

--> A typical production Dockerfile for a Spring Boot app uses a **multi-stage build**: one stage that has the full JDK + Maven to COMPILE the app, and a second, much smaller stage that only has a JRE to RUN the already-built jar -- so the final image doesn't carry around the entire build toolchain.

```dockerfile
# ---- Stage 1: build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline              # cache dependency downloads as a separate layer
COPY src ./src
RUN mvn clean package -DskipTests

# ---- Stage 2: run ----
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

--> **Why multi-stage matters** -- a `maven:...` build image can easily be 500MB+ once Maven, the JDK, and all your build-time dependencies are counted, but almost none of that is needed to just RUN the finished jar. Copying only the final artifact into a slim JRE-only base image (`eclipse-temurin:21-jre-jammy`, `eclipse-temurin:21-jre-alpine`, etc.) routinely shrinks the final image from 500-800MB down to under 200MB.
--> **Layered jars for better Docker caching** -- Spring Boot's default fat jar is one big undifferentiated layer from Docker's point of view, so ANY code change invalidates the whole `COPY app.jar` layer and forces re-uploading the entire jar (dependencies included) on every deploy. Spring Boot supports splitting the jar into logical layers (dependencies, Spring Boot loader classes, application resources, application classes) so that Docker can cache the rarely-changing dependency layer separately from your frequently-changing application code:

```text
mvn spring-boot:build-image
    -> uses Cloud Native Buildpacks to build an OPTIMIZED, already-layered Docker image
       directly from the Maven build, no handwritten Dockerfile needed at all.

# OR, with a handwritten Dockerfile + layertools:
java -Djarmode=layertools -jar app.jar extract
    -> explodes the fat jar into: dependencies/, spring-boot-loader/, snapshot-dependencies/,
       application/ -- each COPYed as its own Docker layer, ordered least-to-most likely to change.
```

```dockerfile
# ---- Layered run stage (better caching) ----
FROM eclipse-temurin:21-jre-jammy AS builder
WORKDIR /app
COPY target/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=builder /app/dependencies/ ./
COPY --from=builder /app/spring-boot-loader/ ./
COPY --from=builder /app/snapshot-dependencies/ ./
COPY --from=builder /app/application/ ./
EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
```

--> **Basic build/run commands**:

```text
docker build -t my-app:latest .
docker run -p 8080:8080 my-app:latest
docker run -p 8080:8080 -e SPRING_PROFILES_ACTIVE=prod my-app:latest
```

--> **`EXPOSE 8080`** is documentation for humans and tooling (it doesn't actually publish the port) -- the `-p 8080:8080` flag on `docker run` is what actually maps the container's port to the host.

# Common Gotchas

--> **`@DataJpaTest` auto-rollback vs `@Transactional` service methods** -- `@DataJpaTest` wraps each test method in a transaction that ALWAYS rolls back at the end, regardless of what the code under test does, purely so tests don't pollute each other. This is different from a `@Transactional` annotation on a real service method, which commits normally in production and only rolls back on an unhandled exception (by default, unchecked exceptions) -- don't confuse "my repository test appears to not persist anything permanently" (expected, it's the test harness) with "my production service isn't committing" (a real bug, usually a missed exception or a self-invocation issue where `@Transactional` doesn't apply because the call bypassed the Spring proxy).
--> **Reaching for `@SpringBootTest` when a slice would do** -- a very common anti-pattern is writing every test as `@SpringBootTest` "to be safe," which drags in the full context (real datasource config, real security config, every bean) for tests that only actually exercise one controller or one repository method. This is the single biggest lever for a slow test suite -- audit for tests that could be `@WebMvcTest` or `@DataJpaTest` instead and aren't.
--> **Forgetting to expose actuator endpoints** -- adding `spring-boot-starter-actuator` alone gets you `/actuator/health` and nothing else over HTTP; hitting `/actuator/metrics` or `/actuator/env` and getting a 404 is almost always a missing `management.endpoints.web.exposure.include` entry, not a broken dependency.
--> **Bloated Docker images from skipping multi-stage builds** -- `FROM maven:3.9-eclipse-temurin-21` as your ONLY stage, with no second slim stage, ships the entire Maven + JDK toolchain in your production image -- multiply that across every microservice in a fleet and it becomes real storage and pull-time cost. Always split into a build stage and a runtime stage, and prefer a `-jre` (not `-jdk`) base for the runtime stage since production doesn't need a compiler.
--> **Testcontainers left running / port conflicts in CI** -- forgetting `@Testcontainers` lifecycle annotations or reusing containers incorrectly across test classes can cause flaky failures under parallel CI execution -- usually solved by letting Testcontainers manage container lifecycle automatically rather than manually starting/stopping containers.

# Deep Dive -- How Spring's Test Context Caching Actually Works

--> Starting an `ApplicationContext` is expensive, so the Spring TestContext framework caches contexts across test classes keyed by their exact configuration "signature" -- the set of configuration classes/annotations, active profiles, property sources, and `@MockBean`/`@MockitoBean` overrides used to build that context. If two test classes have IDENTICAL signatures, the SAME context instance is reused between them, skipping a second boot entirely.
--> The catch: this cache is extremely sensitive to signature differences. Two `@SpringBootTest` classes that look nearly identical but declare even one different `@MockBean`, or activate a different `@ActiveProfiles` value, or override one different property via `@TestPropertySource`, are treated as DIFFERENT signatures and each gets its own freshly-booted context. In a large test suite, this means small, well-intentioned per-test customizations (mocking one extra bean "just for this test") can silently multiply the number of context boots, and thus the total suite runtime, far more than the number of test classes would suggest.
--> **Practical takeaway** -- when writing many `@SpringBootTest` classes that are conceptually similar, try to consolidate shared configuration (common `@MockBean`s, common profiles) into a shared base class or a common test configuration, rather than letting each class drift into a slightly different signature that defeats context caching.

# Deep Dive -- Why the Fat Jar's Nested Structure Needs a Custom Classloader

--> A standard JVM classloader can load classes from a jar's own entries and from OTHER jars listed on the classpath, but it has no built-in concept of "a jar nested inside another jar" -- `BOOT-INF/lib/some-dependency.jar` sitting as raw bytes inside `my-app.jar` isn't something `java -cp my-app.jar` can navigate into on its own.
--> Spring Boot solves this with its own `JarLauncher` (bundled inside the fat jar itself, under `org/springframework/boot/loader/`) and a custom `LaunchedURLClassLoader` that knows how to read those nested jar entries directly as class sources, without ever exploding them onto disk first. This is why `java -jar my-app.jar` works transparently, while trying to reference a class from one of those nested dependency jars via a normal `-cp` classpath argument does not -- the nested-jar trick is entirely an invention of Spring Boot's loader machinery, not a general JVM feature.

# Best Practices Summary

```text
- Default to plain JUnit + Mockito (@Mock/@InjectMocks) for anything with no real Spring
  dependency to test -- fastest, simplest, no context startup cost at all.
- Reach for a slice annotation (@WebMvcTest, @DataJpaTest, etc.) before reaching for
  @SpringBootTest -- match the test's scope to the narrowest context that can prove it.
- Reserve @SpringBootTest for genuine cross-layer / end-to-end verification, and keep its
  configuration consistent across classes so Spring can cache and reuse the context.
- Use MockMvc for controller-layer HTTP testing instead of spinning up a real server unless
  you specifically need a real socket (webEnvironment = RANDOM_PORT / WebTestClient).
- Trust @DataJpaTest's automatic rollback for isolation -- don't hand-roll cleanup logic
  that duplicates what the transaction wrapper already does.
- Use H2 for fast everyday repository tests; use Testcontainers when production-parity with
  your real database engine actually matters for the behavior under test.
- Always package with spring-boot-maven-plugin so `java -jar` "just works" -- never ship a
  plain, non-executable jar as your deployable artifact.
- Explicitly opt in to the actuator endpoints you need via
  management.endpoints.web.exposure.include, and put anything beyond /health behind auth.
- Always multi-stage your Dockerfile: build with a full JDK/Maven image, run with a slim JRE
  image -- never ship your build toolchain in the production image.
- Prefer layered jars (spring-boot:build-image or layertools) for Docker builds you'll
  rebuild often -- it turns most deploys into "only re-upload the small application layer."
```
