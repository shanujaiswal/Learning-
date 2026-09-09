/**
 * 06_dependency_injection_demo.java
 *
 * Demonstrates, with illustrative Spring code:
 *     1. @Component / @Service / @Repository stereotypes on illustrative classes
 *     2. Constructor injection (preferred), field injection, and setter
 *        injection side by side, for direct comparison
 *     3. @Qualifier and @Primary resolving ambiguity between two beans of
 *        the same interface type
 *     4. @Configuration + @Bean for manually registering a third-party-style object
 *     5. @Profile beans ("dev" vs "prod" NotificationService implementations)
 *     6. @Value for single properties, and @ConfigurationProperties for a
 *        whole bound, validated group of related properties
 *
 * Covers Theory chapter:
 *     10) Java/09) Spring and Spring Boot/Theory/06 Dependency Injection and Annotations Deep Dive.md
 *
 * IMPORTANT -- this file is illustrative, NOT a standalone runnable program.
 * It requires a real Spring Boot project on the classpath to compile/run, specifically:
 *     - spring-boot-starter          (core Spring context, stereotype annotations, @Value/@ConfigurationProperties)
 *     - spring-boot-starter-validation  (for @Validated + Bean Validation annotations on MailProperties)
 *
 * Run (in a real Spring Boot project, after wiring this into src/main/java/... and adding
 * a @SpringBootApplication class with a main() method):
 *     mvn spring-boot:run
 *   or
 *     ./gradlew bootRun
 *
 * Example application.properties exercising the @Value / @ConfigurationProperties
 * beans below, and activating the "dev" profile:
 *     spring.profiles.active=dev
 *     app.mail.from-address=no-reply@example.com
 *     app.mail.retry-count=3
 *     app.mail.enabled=true
 */

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

// ---------------------------------------------------------------------------
// 1) @Repository -- persistence-layer stereotype. Beyond being a @Component,
//    @Repository also gets Spring's exception-translation behavior applied
//    (low-level persistence exceptions rethrown as Spring's DataAccessException
//    hierarchy) -- not relevant to this illustrative in-memory store, but the
//    annotation is still the correct one to reach for by architectural convention.
// ---------------------------------------------------------------------------

@Repository
class InMemoryUserStore {

    private final List<String> usernames = List.of("alice", "bob", "carol");

    public boolean exists(String username) {
        return usernames.contains(username);
    }
}

// ---------------------------------------------------------------------------
// 2) A generic @Component -- no specific layer, just "a Spring-managed bean."
// ---------------------------------------------------------------------------

@Component
class GreetingFormatter {
    public String format(String username) {
        return "Hello, " + username + "!";
    }
}

// ---------------------------------------------------------------------------
// 3) Three injection styles on three separate classes, purely for direct
//    side-by-side comparison. In real code you would pick ONE style
//    (constructor injection) consistently -- never mix all three like this.
// ---------------------------------------------------------------------------

/** Field injection -- shortest to write, weakest design (see Theory file for why). */
@Service
class FieldInjectedGreetingService {

    @Autowired
    private GreetingFormatter greetingFormatter;   // not final -- mutable for the object's whole lifetime
    @Autowired
    private InMemoryUserStore userStore;

    public String greet(String username) {
        if (!userStore.exists(username)) {
            throw new IllegalArgumentException("Unknown user: " + username);
        }
        return greetingFormatter.format(username);
    }
}

/** Setter injection -- allows reassignment after construction; rarely the right default. */
@Service
class SetterInjectedGreetingService {

    private GreetingFormatter greetingFormatter;
    private InMemoryUserStore userStore;

    @Autowired
    public void setGreetingFormatter(GreetingFormatter greetingFormatter) {
        this.greetingFormatter = greetingFormatter;
    }

    @Autowired
    public void setUserStore(InMemoryUserStore userStore) {
        this.userStore = userStore;
    }

    public String greet(String username) {
        if (!userStore.exists(username)) {
            throw new IllegalArgumentException("Unknown user: " + username);
        }
        return greetingFormatter.format(username);
    }
}

/**
 * Constructor injection -- the recommended default. Dependencies are final
 * (genuinely immutable), missing beans fail fast AT STARTUP rather than as a
 * later NullPointerException, and this class can be unit tested with plain
 * `new ConstructorInjectedGreetingService(fakeFormatter, fakeStore)` -- no
 * Spring test context required at all.
 */
@Service
class ConstructorInjectedGreetingService {

    private final GreetingFormatter greetingFormatter;
    private final InMemoryUserStore userStore;

    // @Autowired is optional here (Spring 4.3+ auto-detects a single constructor),
    // shown explicitly for clarity.
    @Autowired
    public ConstructorInjectedGreetingService(GreetingFormatter greetingFormatter,
                                               InMemoryUserStore userStore) {
        this.greetingFormatter = greetingFormatter;
        this.userStore = userStore;
    }

    public String greet(String username) {
        if (!userStore.exists(username)) {
            throw new IllegalArgumentException("Unknown user: " + username);
        }
        return greetingFormatter.format(username);
    }
}

// ---------------------------------------------------------------------------
// 4) @Qualifier and @Primary -- disambiguating two beans of the same
//    interface type. NotificationService has two implementations; the
//    consumer below picks one explicitly with @Qualifier, and EmailNotification
//    is separately marked @Primary as the implicit default for any OTHER
//    injection point that doesn't specify a qualifier.
// ---------------------------------------------------------------------------

interface NotificationService {
    void send(String to, String message);
}

@Service("emailNotification")
@Primary                                    // the default NotificationService wherever no @Qualifier is given
class EmailNotificationService implements NotificationService {
    @Override
    public void send(String to, String message) {
        System.out.println("[EMAIL to " + to + "] " + message);
    }
}

@Service("smsNotification")
class SmsNotificationService implements NotificationService {
    @Override
    public void send(String to, String message) {
        System.out.println("[SMS to " + to + "] " + message);
    }
}

@Service
class OrderNotifier {

    private final NotificationService urgentNotificationService;   // explicitly wants SMS for urgent alerts
    private final NotificationService defaultNotificationService;   // no @Qualifier -- resolves to @Primary (email)

    public OrderNotifier(@Qualifier("smsNotification") NotificationService urgentNotificationService,
                          NotificationService defaultNotificationService) {
        this.urgentNotificationService = urgentNotificationService;
        this.defaultNotificationService = defaultNotificationService;
    }

    public void notifyOrderShipped(String customerContact) {
        defaultNotificationService.send(customerContact, "Your order has shipped!");
    }

    public void notifyOrderUrgentIssue(String customerContact) {
        urgentNotificationService.send(customerContact, "Urgent: there is a problem with your order.");
    }
}

// ---------------------------------------------------------------------------
// 5) @Configuration + @Bean -- for objects you don't own the source of (a
//    stand-in "third-party" HttpClientConfig here) or whose construction
//    needs custom logic that a plain @Component annotation can't express.
// ---------------------------------------------------------------------------

class ThirdPartyHttpClient {
    private final Duration connectTimeout;
    private final Duration readTimeout;

    public ThirdPartyHttpClient(Duration connectTimeout, Duration readTimeout) {
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
    }

    public Duration getConnectTimeout() { return connectTimeout; }
    public Duration getReadTimeout() { return readTimeout; }
}

@Configuration
class HttpClientConfig {

    // We can't put @Component on ThirdPartyHttpClient -- imagine it's a class
    // from an external library we don't control the source of. @Bean is how
    // we still register a configured instance of it as a Spring-managed bean.
    @Bean
    public ThirdPartyHttpClient thirdPartyHttpClient() {
        return new ThirdPartyHttpClient(Duration.ofSeconds(5), Duration.ofSeconds(10));
    }
}

// ---------------------------------------------------------------------------
// 6) @Profile -- swapping bean implementations per environment without any
//    branching application code. "!prod" means "active in every profile
//    EXCEPT prod" -- a common pattern for dev/test-only fakes.
// ---------------------------------------------------------------------------

@Service
@Profile("!prod")                          // registered in "dev", "test", or no profile at all -- anything but "prod"
class FakePaymentGateway {
    public String charge(String customerId, java.math.BigDecimal amount) {
        System.out.println("[DEV] Pretending to charge " + customerId + " $" + amount);
        return "fake-transaction-id";
    }
}

@Service
@Profile("prod")                           // only registered when the "prod" profile is active
class RealPaymentGateway {
    public String charge(String customerId, java.math.BigDecimal amount) {
        // A real implementation would call an actual payment processor's API here.
        return "real-transaction-id";
    }
}

// ---------------------------------------------------------------------------
// 7) @Value -- injecting one or two standalone properties directly.
//    Fine for a couple of values; gets unwieldy for a whole related group
//    (see MailProperties below for the better-scaling alternative).
// ---------------------------------------------------------------------------

@Service
class SimpleGreetingBanner {

    @Value("${app.banner.text:Welcome!}")     // ":Welcome!" is the default used if the property is absent
    private String bannerText;

    @Value("${app.banner.max-length:80}")
    private int maxLength;

    public String render() {
        return bannerText.length() > maxLength
                ? bannerText.substring(0, maxLength)
                : bannerText;
    }
}

// ---------------------------------------------------------------------------
// 8) @ConfigurationProperties -- binding a whole GROUP of related properties
//    (all sharing the "app.mail" prefix) to one typed, validated class,
//    instead of scattering individual @Value fields. @Validated turns on
//    Bean Validation for the bound fields.
// ---------------------------------------------------------------------------

@ConfigurationProperties(prefix = "app.mail")
@Validated
class MailProperties {

    @NotBlank
    private String fromAddress;

    @Min(0)
    private int retryCount = 5;             // acts as the default when the property is absent

    private boolean enabled = true;

    public String getFromAddress() { return fromAddress; }
    public void setFromAddress(String fromAddress) { this.fromAddress = fromAddress; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}

@Configuration
@EnableConfigurationProperties(MailProperties.class)   // registers MailProperties itself as an injectable bean
class MailConfig { }

@Service
class MailPropertiesReportingService {

    private final MailProperties mailProperties;       // the whole related group, injected as one typed unit

    public MailPropertiesReportingService(MailProperties mailProperties) {
        this.mailProperties = mailProperties;
    }

    public String describeConfig() {
        return "Mail enabled=" + mailProperties.isEnabled()
                + ", from=" + mailProperties.getFromAddress()
                + ", retries=" + mailProperties.getRetryCount();
    }
}

/*
 * NOTE on the annotations used above:
 * This snippet uses real Spring Framework / Spring Boot annotations exactly
 * as they'd appear in a real project (org.springframework.stereotype.*,
 * org.springframework.beans.factory.annotation.*,
 * org.springframework.boot.context.properties.*), which requires component
 * scanning and a @SpringBootApplication entry point to actually build the
 * ApplicationContext and wire these beans together -- including resolving
 * the @Profile-gated beans against whichever profile is actually active.
 * Since this file is illustrative only (see header), that bootstrap class
 * is intentionally omitted -- drop these classes into a real Spring Boot
 * project's source tree, add the relevant application.properties entries,
 * and set spring.profiles.active to see it run end-to-end.
 */
