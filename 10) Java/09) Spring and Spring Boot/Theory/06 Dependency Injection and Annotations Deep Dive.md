# Recap -- What Dependency Injection Actually Buys You

--> **Dependency Injection (DI)** is a specific application of the broader "Inversion of Control" principle: instead of a class CREATING the objects it depends on (`new ProductRepository()` inside `ProductService`), those dependencies are handed to it FROM THE OUTSIDE, by a container. Spring's IoC container is that container -- it scans for classes marked as "beans," works out how they depend on each other, constructs them in the right order, and wires the dependencies in automatically.
--> **Why this matters beyond "less `new` typing"** -- a class that receives its dependencies rather than constructing them is trivially TESTABLE (swap in a mock/fake for the test), trivially SWAPPABLE (a different implementation of an interface can be wired in without touching the dependent class's code), and has its true dependencies made EXPLICIT and visible (usually right there in the constructor signature) rather than buried inside method bodies.
--> This file goes one level deeper than earlier chapters' brief mentions -- covering exactly WHICH stereotype annotation to reach for, the three injection styles and why constructor injection is preferred, and how profiles/property injection let the same codebase behave differently across environments.

# Stereotype Annotations -- @Component, @Service, @Repository, and Why They're Different

--> All four annotations below (`@Component`, `@Service`, `@Repository`, `@Controller`/`@RestController`) do the EXACT SAME mechanical thing to the IoC container: they mark a class to be auto-detected during COMPONENT SCANNING and registered as a bean. `@Service`, `@Repository`, and `@Controller` are all, underneath, meta-annotated with `@Component` -- they ARE `@Component`, plus semantic meaning and (for `@Repository`) one real added behavior.

```java
@Component          // generic stereotype -- "this is a Spring-managed bean," no more specific meaning implied
public class EmailFormatter { ... }

@Service            // business/service-layer logic
public class ProductService { ... }

@Repository         // persistence-layer / data access logic
public class ProductRepository { ... }

@RestController     // web layer, REST endpoints (== @Controller + @ResponseBody, covered in ch. 03)
public class ProductController { ... }
```

| Annotation | Layer / Intent | Extra behavior beyond `@Component` |
|---|---|---|
| `@Component` | Generic -- anything not covered by a more specific stereotype | None |
| `@Service` | Business/orchestration logic | None (purely documentation/intent to readers and tools) |
| `@Repository` | Data access layer | **Exception translation** -- see below |
| `@Controller` / `@RestController` | Web layer | Enables request-mapping annotation processing |

--> **Why bother with more specific stereotypes if they're "just" `@Component` most of the time?** Two reasons: (1) **readability/intent** -- a new developer scanning package contents immediately understands a class's architectural role from its annotation, without reading its implementation; (2) **tooling** -- IDEs, architecture-linting tools (e.g. ArchUnit), and some Spring features use the specific stereotype to apply layer-appropriate behavior or enforce layering rules (e.g. "no `@Repository` class may be referenced directly from a `@Controller`").
--> **Deep Dive -- `@Repository`'s one real extra behavior: exception translation** -- a class annotated `@Repository` gets a `PersistenceExceptionTranslationPostProcessor` applied, which catches low-level, PERSISTENCE-TECHNOLOGY-SPECIFIC exceptions (a raw JDBC `SQLException`, a Hibernate-specific exception) and rethrows them as one of Spring's own unchecked `DataAccessException` subclasses. This means calling code can catch `DataAccessException` (or a specific subclass like `DataIntegrityViolationException`) without needing to know or care whether the underlying persistence technology is Hibernate, plain JDBC, or something else entirely -- genuinely useful decoupling, not just a naming convention.

# @Autowired -- Field, Constructor, and Setter Injection

--> **`@Autowired`** tells Spring "find a bean matching this dependency's type and inject it here" -- it can be placed on a field, a constructor, or a setter method. All three achieve the same end result (the dependency gets wired in) but differ significantly in testability, immutability, and how failures surface.

## Field Injection -- Simplest to Write, Weakest Design

```java
@Service
public class ProductService {
    @Autowired
    private ProductRepository productRepository;      // injected directly into the field

    @Autowired
    private EmailService emailService;
}
```

--> **Why this is generally discouraged despite being the shortest to type** -- the field can't be `final` (so it's mutable long after construction, even though it should conceptually never change), the class becomes impossible to instantiate normally in a plain unit test (`new ProductService()` gives you an object with `null` dependencies -- you're forced to use Spring's test context or reflection-based mocking frameworks just to set a private field), and the dependency requirement is HIDDEN inside the class body rather than visible in a constructor signature that documents "this is what I need to exist."

## Setter Injection -- Rarely the Right Default

```java
@Service
public class ProductService {
    private ProductRepository productRepository;

    @Autowired
    public void setProductRepository(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }
}
```

--> Allows the dependency to be reassigned after construction (useful for genuinely OPTIONAL dependencies that might be set/changed later, or reconfigured at runtime) but for the common case this is a downside, not a feature -- it means the object can exist in a half-constructed, dependency-less state, and nothing stops calling code from forgetting to call the setter at all.

## Constructor Injection -- The Recommended Default

```java
@Service
public class ProductService {
    private final ProductRepository productRepository;    // final -- genuinely immutable once constructed
    private final EmailService emailService;

    // @Autowired is OPTIONAL here since Spring 4.3+ -- if a class has exactly ONE constructor,
    // Spring uses it automatically for injection without needing the annotation at all.
    public ProductService(ProductRepository productRepository, EmailService emailService) {
        this.productRepository = productRepository;
        this.emailService = emailService;
    }
}
```

--> **Why this is the recommended default in virtually all current Spring guidance:**
  - --> **Immutability** -- dependencies are `final`, assigned exactly once, and can never be null-then-reassigned-later or accidentally left unset.
  - --> **Fails fast, at the right time** -- if a required dependency can't be satisfied, the application fails to even START UP (a clear, early, loud `NoSuchBeanDefinitionException` at boot), rather than failing later and confusingly with a `NullPointerException` the first time the unset field is actually used.
  - --> **Plain unit testability without a Spring context** -- `new ProductService(mockRepository, mockEmailService)` works in a plain JUnit test, no Spring test annotations, no reflection hacks, no need to spin up an `ApplicationContext` just to test business logic.
  - --> **Dependencies are visible and self-documenting** -- anyone reading the constructor signature immediately sees the complete list of what this class needs to function, without reading the whole class body.
  - --> **Naturally prevents circular dependencies from compiling silently** -- two classes needing each other via constructor injection fail at STARTUP with a clear circular-reference error, surfacing a real design problem immediately rather than it lurking, half-working, via field injection's more forgiving (lazier) wiring.

| Style | Immutable? | Fails fast at startup? | Plain-JUnit testable? | Recommended? |
|---|---|---|---|---|
| Field | No | No (NPE later, at first use) | Hard (needs reflection/Spring test context) | Avoid for required deps |
| Setter | No | No | Awkward (must remember to call setter) | Only for genuinely optional/reconfigurable deps |
| Constructor | Yes | Yes | Easy (`new` it directly) | **Default choice** |

# @Qualifier -- Disambiguating Multiple Beans of the Same Type

--> Autowiring by TYPE fails ambiguously the moment more than one bean of that type exists -- Spring throws `NoUniqueBeanDefinitionException` at startup, because it has no way to know which one you meant.

```java
public interface NotificationService {
    void send(String message);
}

@Service("emailNotification")
public class EmailNotificationService implements NotificationService { ... }

@Service("smsNotification")
public class SmsNotificationService implements NotificationService { ... }

@Service
public class OrderService {
    private final NotificationService notificationService;

    // Without @Qualifier, Spring can't decide between the two NotificationService beans -- ambiguous.
    public OrderService(@Qualifier("emailNotification") NotificationService notificationService) {
        this.notificationService = notificationService;
    }
}
```

--> **`@Qualifier`'s string argument matches the bean's NAME** -- by default a bean's name is its class name with a lowercased first letter (`emailNotificationService`), unless overridden explicitly as shown above (`@Service("emailNotification")`).
--> **`@Primary` -- the alternative to `@Qualifier` for a "usual default, occasionally overridden" scenario** -- annotate ONE of the candidate beans `@Primary` to make it the implicit default whenever no `@Qualifier` disambiguates; `@Qualifier` at an injection point still overrides `@Primary` when both are present.

```java
@Service
@Primary                              // the default NotificationService whenever nothing else is specified
public class EmailNotificationService implements NotificationService { ... }
```

--> **When to use which** -- `@Primary` fits "one implementation is the sensible default almost everywhere, a few call sites need the other one explicitly"; `@Qualifier` at every injection point fits "there's no natural default, every call site must be explicit about which one it wants."

# @Configuration and @Bean -- Manually Defining Beans

--> Not every bean can be a `@Component`-annotated class you wrote yourself -- third-party library classes (a `RestTemplate`, an `ObjectMapper`, a `DataSource`) don't have Spring annotations on them at all, and you can't add annotations to code you don't own. **`@Configuration` classes with `@Bean`-annotated methods** are how you register beans for exactly this situation -- the method body constructs the object explicitly, and Spring registers its return value as a bean, named after the method by default.

```java
@Configuration
public class AppConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
```

--> **`@Bean` method PARAMETERS are themselves autowired** -- in the `restTemplate` example above, `RestTemplateBuilder builder` is injected by Spring exactly like a constructor parameter would be; `@Bean` methods can depend on other beans (including other `@Bean`-defined ones) this way, and Spring resolves the correct construction order automatically.
--> **`@Component` (+ stereotypes) vs `@Bean` -- when to use which:**

| | `@Component` (class-level) | `@Bean` (method-level, inside `@Configuration`) |
|---|---|---|
| Use for | Classes YOU wrote and can annotate | Third-party classes, or when construction needs custom logic/branching |
| Discovery | Automatic component scanning | Explicit method call, explicit registration |
| Typical use | The bulk of your own services/repositories/controllers | `RestTemplate`, `ObjectMapper`, `DataSource`, any library object, or a bean whose creation depends on `@Value`/environment-specific branching |

# Property Injection -- @Value and @ConfigurationProperties

--> Externalizing configuration (URLs, timeouts, feature flags, API keys) OUT of Java code and INTO `application.properties`/`application.yml` (or environment variables, command-line args -- Spring's `Environment` abstraction merges all of these into one lookup) is what lets the same compiled JAR behave differently across dev/staging/prod without a rebuild.

## @Value -- Injecting a Single Property

```properties
# application.properties
app.mail.from-address=no-reply@example.com
app.mail.retry-count=3
```

```java
@Service
public class EmailService {

    @Value("${app.mail.from-address}")
    private String fromAddress;

    @Value("${app.mail.retry-count:5}")     // ":5" is a DEFAULT if the property is absent -- avoids a startup failure
    private int retryCount;
}
```

--> **`@Value` works fine for one or two standalone properties**, but scales poorly -- injecting a dozen related properties this way means a dozen separate `@Value` fields scattered through a class, no compile-time structure grouping them, and no validation beyond what you hand-write.

## @ConfigurationProperties -- Binding a Whole Group of Properties to One Class

```properties
# application.properties
app.mail.from-address=no-reply@example.com
app.mail.retry-count=3
app.mail.enabled=true
```

```java
@ConfigurationProperties(prefix = "app.mail")
@Validated                                       // enables Bean Validation on the bound fields
public class MailProperties {

    @NotBlank
    private String fromAddress;

    @Min(0)
    private int retryCount = 5;                  // acts as the default if the property is absent

    private boolean enabled = true;

    // getters + setters required for binding (or use a Java record / constructor binding in newer Spring versions)
}
```

```java
@Configuration
@EnableConfigurationProperties(MailProperties.class)   // registers MailProperties itself as a bean
public class MailConfig { }

@Service
public class EmailService {
    private final MailProperties mailProperties;        // inject the whole typed group, constructor injection as usual

    public EmailService(MailProperties mailProperties) {
        this.mailProperties = mailProperties;
    }
}
```

--> **`@ConfigurationProperties` vs `@Value` -- when to reach for which:**

| | `@Value` | `@ConfigurationProperties` |
|---|---|---|
| Best for | One or two standalone properties | A whole related GROUP of properties |
| Structure | Flat, scattered across fields/classes | One cohesive, typed class -- injectable as a single unit |
| Validation | Manual | Native Bean Validation support (`@Validated` + JSR 380 annotations) |
| Relaxed binding (`kebab-case`/`camelCase`/`snake_case` in properties -> Java field) | No, must match `${...}` key exactly | Yes, automatic |
| Type-safety for nested/list/map structures | Awkward (SpEL gymnastics) | Natural -- nested classes, `List<String>`, `Map<String,String>` bind directly |

# Spring Profiles -- @Profile

--> A **profile** is a named, environment-specific label -- `dev`, `test`, `staging`, `prod` are typical examples (no fixed set, name them whatever fits your project) -- that controls which beans get created and which properties get loaded, WITHOUT changing a single line of code between environments.

```java
@Service
@Profile("dev")                          // only registered as a bean when the "dev" profile is active
public class FakeEmailService implements EmailService {
    @Override
    public void send(String to, String subject, String body) {
        System.out.println("DEV MODE -- pretending to email " + to + ": " + subject);
    }
}

@Service
@Profile("prod")                         // only registered when "prod" is active
public class SmtpEmailService implements EmailService {
    @Override
    public void send(String to, String subject, String body) {
        // real SMTP call
    }
}
```

--> **Activating a profile** -- via `application.properties` (`spring.profiles.active=dev`), an environment variable (`SPRING_PROFILES_ACTIVE=prod`), or a JVM/command-line arg (`--spring.profiles.active=staging`) at launch. Multiple profiles can be active simultaneously (comma-separated), and beans/properties from all active profiles are merged.
--> **Profile-specific property files** -- `application-dev.properties`, `application-prod.properties` are loaded IN ADDITION to the base `application.properties` when that profile is active, with profile-specific values overriding the base file's values for the same key. This is the standard way to have, e.g., a real database URL in `application-prod.properties` and a local H2 URL in `application-dev.properties`, while shared settings live once in the base file.
--> **`@Profile` on a `@Configuration` class** applies to every `@Bean` method inside it at once -- useful for grouping an entire environment's worth of bean definitions (e.g. all of a "test doubles" configuration) in one place rather than annotating each bean individually.
--> **`@Profile("!prod")`** -- the `!` negation operator means "active in any profile EXCEPT prod" -- a common pattern for "test/dev-only" beans (like the `FakeEmailService` above) that should exist everywhere except the real production environment, without having to enumerate every non-prod profile name individually.

# Common Gotchas

--> **Defaulting to field injection out of habit** -- it compiles and runs fine in the simple case, but makes unit testing harder and hides real dependencies; prefer constructor injection as the default, reserving field injection (if ever) for truly optional wiring in test-only code.
--> **Ambiguous bean type with no `@Qualifier`/`@Primary`** -- `NoUniqueBeanDefinitionException` at startup the moment a second implementation of an interface is added; decide up front whether the situation calls for one `@Primary` default or explicit `@Qualifier` everywhere.
--> **`@Value` sprawl** -- a class accumulating many individual `@Value` fields for what is conceptually one configuration group is a sign to consolidate into a `@ConfigurationProperties` class instead.
--> **Forgetting `@EnableConfigurationProperties` (or a component-scanned `@Component` on the properties class itself)** -- a `@ConfigurationProperties` class with neither is never registered as a bean, and injecting it elsewhere fails with a "no such bean" error at startup.
--> **Typos in `@Profile` names, or forgetting to activate the intended profile** -- a bean annotated `@Profile("prod")` simply never exists in a run where no profile (or a differently-spelled one) is active -- this fails as a missing-bean error, not a warning, so it's usually caught quickly, but the fix is always "check what's actually active," not "add more beans."
--> **Mixing up `@Bean` and `@Component` roles** -- trying to `@Component`-annotate a class you don't own (impossible, you can't edit a third-party class's source) instead of reaching for a `@Bean` method; or, conversely, writing unnecessary `@Bean` methods for classes you DO own and could simply annotate directly, adding needless indirection.

# Best Practices Summary

--> Use `@Service`/`@Repository`/`@Controller`/`@RestController` over generic `@Component` whenever a more specific stereotype genuinely applies -- it documents architectural intent and, for `@Repository`, adds real exception-translation behavior.
--> Default to constructor injection for all required dependencies -- it's immutable, fails fast at startup, and is trivially unit-testable without a Spring context.
--> Reserve `@Autowired` on fields/setters for narrow cases (optional, reconfigurable dependencies) rather than as the everyday default.
--> Resolve multi-implementation ambiguity deliberately -- `@Primary` for "usual default, occasional override," `@Qualifier` everywhere when there's no natural default.
--> Reach for `@Bean` methods inside `@Configuration` classes for third-party objects and any bean whose construction needs custom logic; reach for `@Component` stereotypes for your own classes.
--> Group related configuration into a `@ConfigurationProperties` class rather than scattering many individual `@Value` fields; validate it with `@Validated` + Bean Validation annotations.
--> Use `@Profile` to swap environment-specific beans (fakes/stubs in dev, real integrations in prod) without branching application code, and keep profile-specific properties in `application-<profile>.properties` files.
