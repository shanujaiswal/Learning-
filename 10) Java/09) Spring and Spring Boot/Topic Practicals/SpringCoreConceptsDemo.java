/**
 * SpringCoreConceptsDemo.java
 *
 * Demonstrates, with runtime console logging:
 *     1. Constructor injection (preferred style -- immutable, required dependency)
 *     2. Setter injection (mutable, suited to optional dependencies)
 *     3. Field injection via @Autowired (concise, but discouraged for real code)
 *     4. A plain @Component bean discovered via component scanning
 *     5. A @Configuration class with @Bean factory methods (for a "third-party" style class)
 *     6. Full bean lifecycle: @PostConstruct / @PreDestroy callbacks, in order
 *     7. Wiring + lifecycle observed end-to-end via AnnotationConfigApplicationContext
 *
 * Covers Theory chapter:
 *     10) Java/09) Spring and Spring Boot/Theory/01 Spring Framework Core Concepts.md
 *
 * IMPORTANT -- this file will NOT compile or run as a plain .java file with `javac`/`java`.
 * It requires Spring on the classpath (at minimum the `spring-context` dependency, which
 * transitively pulls in spring-core/spring-beans, plus `jakarta.annotation-api` for
 * @PostConstruct/@PreDestroy). It is illustrative code meant to be copy-pasted/adapted
 * into an actual Spring (Framework or Boot) project -- e.g. split into separate files
 * under src/main/java/... in a Maven/Gradle project -- not compiled standalone.
 *
 * Run (once dropped into a real Spring project), pick ONE depending on your project type:
 *
 *   Plain Maven + spring-context (no Spring Boot):
 *     mvn dependency:get -Dartifact=org.springframework:spring-context:6.1.0
 *     mvn compile exec:java -Dexec.mainClass="com.example.SpringCoreConceptsDemo"
 *
 *   Spring Boot project (recommended way to actually try this out):
 *     1. Generate a project at https://start.spring.io with the "Spring Web" or
 *        just plain "Spring Boot Starter" dependency (which includes spring-context).
 *     2. Paste the classes below into src/main/java/com/example/... (one top-level
 *        class per file, per normal Java file-naming rules).
 *     3. Replace the main() demo below with a CommandLineRunner bean, or just call
 *        runDemo() from your @SpringBootApplication's main() method.
 *     4. mvn spring-boot:run   (or)   ./gradlew bootRun
 *
 * Minimal Maven dependency (pom.xml) if wiring this up manually without Boot:
 *     <dependency>
 *         <groupId>org.springframework</groupId>
 *         <artifactId>spring-context</artifactId>
 *         <version>6.1.0</version>
 *     </dependency>
 */

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;


// ---------------------------------------------------------------------------
// Dependency interfaces + two competing implementations
// (illustrates the "ambiguous bean" scenario and how @Primary/@Qualifier fix it)
// ---------------------------------------------------------------------------

interface PaymentGateway {
    String charge(double amount);
}

@Component
@Primary   // designates this as the default when PaymentGateway is ambiguous
class StripePaymentGateway implements PaymentGateway {
    @Override
    public String charge(double amount) {
        return "Stripe charged $" + amount;
    }
}

@Component
class PayPalPaymentGateway implements PaymentGateway {
    @Override
    public String charge(double amount) {
        return "PayPal charged $" + amount;
    }
}

// A @Repository is semantically a @Component (data-access stereotype) --
// picked up automatically by component scanning just like @Component would be.
@Repository
class InMemoryOrderRepository {
    private int orderCount = 0;

    public int saveOrder() {
        return ++orderCount;   // pretend persistence -- just increments a counter
    }
}


// ---------------------------------------------------------------------------
// 1) CONSTRUCTOR INJECTION -- the preferred style
// ---------------------------------------------------------------------------

/**
 * OrderService demonstrates constructor injection: both dependencies are
 * `final`, supplied as constructor parameters, and the object simply CANNOT
 * exist in a half-wired state -- if a dependency is missing, construction
 * itself fails, immediately and loudly, rather than surfacing later as a
 * NullPointerException when the field is finally used.
 *
 * Since Spring 4.3+, @Autowired on the constructor is OPTIONAL when there is
 * only a single constructor -- Spring infers it should be used for injection.
 * It's shown here explicitly for clarity.
 */
@Component
class OrderService {
    private final PaymentGateway paymentGateway;      // resolved via @Primary (StripePaymentGateway)
    private final InMemoryOrderRepository orderRepository;

    @Autowired // optional here since there's only one constructor, kept for clarity
    public OrderService(PaymentGateway paymentGateway, InMemoryOrderRepository orderRepository) {
        this.paymentGateway = paymentGateway;
        this.orderRepository = orderRepository;
        System.out.println("[OrderService] constructed via CONSTRUCTOR injection -- dependencies guaranteed non-null");
    }

    public void placeOrder(double amount) {
        int orderId = orderRepository.saveOrder();
        String result = paymentGateway.charge(amount);
        System.out.println("[OrderService] order #" + orderId + " placed -- " + result);
    }
}


// ---------------------------------------------------------------------------
// 2) SETTER INJECTION -- suited to optional dependencies
// ---------------------------------------------------------------------------

/**
 * NotificationService demonstrates setter injection. The class is constructed
 * with a no-arg constructor first; the dependency is supplied afterward via
 * a setter. Unlike constructor injection, the field can't be `final`, and
 * there's a window where the object exists but the dependency is still null
 * -- appropriate here because sending a notification is treated as optional
 * ("best effort"), not something the object cannot function without.
 */
@Component
class NotificationService {
    private PaymentGateway paymentGateway; // not final -- reassignable, may stay null if optional

    @Autowired(required = false) // explicitly optional -- app should still work if unset
    public void setPaymentGateway(PaymentGateway paymentGateway) {
        this.paymentGateway = paymentGateway;
        System.out.println("[NotificationService] dependency supplied via SETTER injection");
    }

    public void notifyPaymentReceived(double amount) {
        if (paymentGateway == null) {
            System.out.println("[NotificationService] no gateway configured -- skipping receipt lookup");
            return;
        }
        System.out.println("[NotificationService] sending confirmation for $" + amount);
    }
}


// ---------------------------------------------------------------------------
// 3) FIELD INJECTION -- concise, but generally discouraged in real code
// ---------------------------------------------------------------------------

/**
 * AuditLogger demonstrates field injection: @Autowired directly on a private
 * field, with no constructor or setter at all. Spring uses reflection to
 * assign the field after construction. This is the LEAST recommended style
 * for production code -- shown here for completeness / contrast only:
 *   - the field can't be final
 *   - the class can't be instantiated (with its dependency) outside a
 *     Spring container without reflection hacks (e.g. ReflectionTestUtils)
 *   - a class's true dependencies are hidden inside the body instead of
 *     being visible in its constructor signature
 */
@Component
class AuditLogger {
    @Autowired
    @Qualifier("payPalPaymentGateway") // disambiguates which PaymentGateway bean to use here
    private PaymentGateway paymentGateway;

    public void logAudit(String action) {
        System.out.println("[AuditLogger] (FIELD-injected gateway=" + paymentGateway.getClass().getSimpleName()
                + ") audit: " + action);
    }
}


// ---------------------------------------------------------------------------
// 4) BEAN LIFECYCLE -- @PostConstruct / @PreDestroy callbacks
// ---------------------------------------------------------------------------

/**
 * ConnectionPoolManager demonstrates the full observable lifecycle:
 *   1. constructor runs (instantiation)
 *   2. setter/field injection runs (dependency population) -- none needed here
 *   3. @PostConstruct runs (initialization -- safe to assume all deps are set)
 *   4. ... bean lives and is used by the application ...
 *   5. @PreDestroy runs (destruction -- only guaranteed for SINGLETON beans,
 *      and only if the container shuts down gracefully, e.g. via
 *      context.close() or a registered JVM shutdown hook)
 */
@Component
class ConnectionPoolManager {

    public ConnectionPoolManager() {
        System.out.println("[ConnectionPoolManager] 1. constructor -- object instantiated");
    }

    @PostConstruct
    public void initializePool() {
        // Guaranteed to run AFTER all dependencies are injected -- the right
        // place to "start" a bean: open connections, warm caches, validate config.
        System.out.println("[ConnectionPoolManager] 2. @PostConstruct -- pool warmed up, ready for use");
    }

    public void borrowConnection() {
        System.out.println("[ConnectionPoolManager] 3. connection borrowed from pool");
    }

    @PreDestroy
    public void closePool() {
        // Runs just before the container discards this bean (e.g. context.close())
        // -- the right place to release resources: close connections, flush buffers.
        System.out.println("[ConnectionPoolManager] 4. @PreDestroy -- pool draining and closing connections");
    }
}


// ---------------------------------------------------------------------------
// 5) @Configuration + @Bean -- Java-config style, typically used for
//    third-party classes you don't own and can't annotate with @Component
// ---------------------------------------------------------------------------

/**
 * Pretend this represents a third-party class you can't put @Component on
 * (e.g. it lives in a library JAR). @Bean factory methods are how you
 * register such classes as Spring-managed beans anyway.
 */
class ThirdPartyMetricsClient {
    private final String endpoint;

    public ThirdPartyMetricsClient(String endpoint) {
        this.endpoint = endpoint;
    }

    public void record(String metric) {
        System.out.println("[ThirdPartyMetricsClient] recorded '" + metric + "' -> " + endpoint);
    }
}

@Configuration
@ComponentScan(basePackageClasses = SpringCoreConceptsDemo.class) // picks up all @Component classes above
class AppConfig {

    // @Bean methods are how you wire classes you don't own/can't annotate.
    // initMethod/destroyMethod mirror @PostConstruct/@PreDestroy for classes
    // that can't use those annotations either (e.g. no annotation support).
    @Bean(initMethod = "connect", destroyMethod = "disconnect")
    public ThirdPartyMetricsClientWrapper metricsClient() {
        return new ThirdPartyMetricsClientWrapper(new ThirdPartyMetricsClient("https://metrics.example.com"));
    }
}

/**
 * Thin wrapper purely to demonstrate @Bean(initMethod=..., destroyMethod=...)
 * as an alternative to @PostConstruct/@PreDestroy for classes you can't annotate.
 */
class ThirdPartyMetricsClientWrapper {
    private final ThirdPartyMetricsClient client;

    public ThirdPartyMetricsClientWrapper(ThirdPartyMetricsClient client) {
        this.client = client;
    }

    public void connect() {
        System.out.println("[ThirdPartyMetricsClientWrapper] initMethod 'connect' -- connection established");
    }

    public void record(String metric) {
        client.record(metric);
    }

    public void disconnect() {
        System.out.println("[ThirdPartyMetricsClientWrapper] destroyMethod 'disconnect' -- connection closed");
    }
}


// ---------------------------------------------------------------------------
// 6) Demo entry point -- wires everything up via AnnotationConfigApplicationContext
//    and shows the full lifecycle end-to-end, in order.
// ---------------------------------------------------------------------------

public class SpringCoreConceptsDemo {

    public static void main(String[] args) {
        System.out.println("=== Starting IoC container (AnnotationConfigApplicationContext) ===");

        // This single line triggers the entire process described in the theory
        // chapter's "How ApplicationContext Resolves and Wires Beans" section:
        //   1. load bean definitions (via @ComponentScan inside AppConfig)
        //   2. determine creation order from the dependency graph
        //   3. instantiate + inject every singleton eagerly
        //   4. run each bean through @PostConstruct
        //   5. context is ready
        AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(AppConfig.class);

        System.out.println("\n=== Container ready -- retrieving and using beans ===");

        OrderService orderService = context.getBean(OrderService.class);
        orderService.placeOrder(49.99);

        NotificationService notificationService = context.getBean(NotificationService.class);
        notificationService.notifyPaymentReceived(49.99);

        AuditLogger auditLogger = context.getBean(AuditLogger.class);
        auditLogger.logAudit("order-placed");

        ConnectionPoolManager poolManager = context.getBean(ConnectionPoolManager.class);
        poolManager.borrowConnection();

        ThirdPartyMetricsClientWrapper metrics = context.getBean(ThirdPartyMetricsClientWrapper.class);
        metrics.record("orders.placed");

        System.out.println("\n=== Closing container -- observe destruction callbacks fire ===");
        // registerShutdownHook()/close() is what actually triggers @PreDestroy --
        // Spring Boot does this for you automatically; in plain Spring it's manual.
        context.close();

        System.out.println("\n=== Demo complete ===");
    }
}
