# What Spring Boot Is, and the Problem It Solves

--> Plain Spring (the "core" Spring Framework -- IoC container, dependency injection, `ApplicationContext`, etc.) is a set of powerful but low-level building blocks. Using it directly means YOU write and wire everything: XML or `@Configuration` classes declaring every bean, a `DispatcherServlet` registered by hand in `web.xml`, a `DataSource` bean configured with driver class names and pool settings, a `ViewResolver`, transaction managers, and so on -- before you write a single line of actual business logic.
--> Spring Boot is NOT a replacement for Spring -- it's an opinionated layer ON TOP of Spring (and the wider Spring ecosystem: Spring MVC, Spring Data, Spring Security) that eliminates that setup tax. It ships sensible defaults, auto-detects what's on your classpath, and configures beans for you -- so a working REST API can start from a single class and a handful of annotations instead of pages of XML.
--> **Two concrete problems it solves:**

```text
1. BOILERPLATE CONFIGURATION
   Plain Spring:  manually declare DispatcherServlet, ViewResolver, DataSource,
                  EntityManagerFactory, TransactionManager, JSON converters...
   Spring Boot:   add spring-boot-starter-web to the classpath -> all of the above
                  is auto-configured with production-sane defaults, overridable
                  via a handful of properties.

2. DEPENDENCY VERSION MANAGEMENT ("dependency hell")
   Plain Spring:  YOU pick a version for Spring Core, Spring MVC, Jackson, Hibernate,
                  Tomcat, SLF4J... and YOU make sure they're all mutually compatible.
   Spring Boot:   a single "starter" parent/BOM pins a tested, compatible version set
                  for the ENTIRE ecosystem -- you declare artifacts with no version
                  number and trust the BOM to pick one that works together.
```

--> The mental model: Spring Boot's job is to get you to a RUNNING, PRODUCTION-GRADE application as fast as possible, with an escape hatch to override any default the moment you need to. It never removes your ability to drop down to plain Spring configuration -- it just means you don't have to start there.
--> **Spring Boot is still Spring underneath.** Every bean in a Spring Boot app lives in the exact same `ApplicationContext` you'd get from plain Spring -- dependency injection, bean scopes, `@Autowired`, AOP, all identical. Nothing about the programming model changes; only how much of the WIRING you have to do by hand changes.

# @SpringBootApplication -- Anatomy of the Entry-Point Annotation

--> Nearly every Spring Boot app starts with a single class like this:

```java
@SpringBootApplication
public class MyApp {
    public static void main(String[] args) {
        SpringApplication.run(MyApp.class, args);
    }
}
```

--> `@SpringBootApplication` is a META-ANNOTATION -- it's not magic on its own, it's just shorthand that bundles together THREE separate annotations, each doing a distinct job. Writing `@SpringBootApplication` is functionally equivalent to stacking all three explicitly:

```java
@Configuration
@EnableAutoConfiguration
@ComponentScan(basePackages = "com.example.myapp")
public class MyApp { ... }
```

```text
@SpringBootApplication
   |
   +-- @Configuration            "this class itself can declare @Bean methods"
   |
   +-- @EnableAutoConfiguration  "let Spring Boot guess and register beans
   |                              based on what's on the classpath"
   |
   +-- @ComponentScan            "scan this package (and sub-packages) for
                                  @Component/@Service/@Repository/@Controller"
```

--> **`@Configuration`** -- marks the class itself as a source of bean definitions. It means you COULD add `@Bean`-annotated methods directly inside this class and they'd be registered in the `ApplicationContext`, same as any other configuration class. It's what makes the entry-point class a legitimate participant in the Spring container's configuration model, not just a launcher with a `main` method bolted on.
--> **`@EnableAutoConfiguration`** -- the actual "Boot magic" switch. It tells Spring Boot: inspect the classpath, inspect existing bean definitions, and auto-register a curated set of beans that make sense for what it finds. This is covered in depth in the next section.
--> **`@ComponentScan`** -- tells Spring where to look for classes annotated `@Component` (and its specializations `@Service`, `@Repository`, `@Controller`, `@RestController`) so they get turned into beans automatically. **Critically, with no arguments it scans the package of the annotated class AND ALL SUB-PACKAGES -- nothing above or beside it.** This single fact is the root cause of one of the most common Spring Boot bugs (see Gotchas below).

--> **Deep Dive -- why package placement of the main class matters so much**
--> Because `@ComponentScan`'s default is "this package and below," the conventional Spring Boot project structure puts the `@SpringBootApplication` class in the ROOT package (e.g. `com.example.myapp`), with every controller, service, and repository nested in sub-packages beneath it (`com.example.myapp.controller`, `com.example.myapp.service`, ...). If you instead put the main class in `com.example.myapp.app` and your controllers in `com.example.myapp.web`, component scanning will silently never find them -- no error, just beans that don't exist, usually surfacing as a 404 on an endpoint you're sure you wrote correctly.

# Auto-Configuration -- How Spring Boot Guesses What Beans You Need

--> Auto-configuration is a set of pre-written `@Configuration` classes, shipped inside Spring Boot's own JARs (and inside starter JARs), each wrapped in `@Conditional` checks so it only ACTIVATES when it makes sense for your specific classpath and existing beans. Adding `spring-boot-starter-web` doesn't just add Tomcat and Spring MVC as dependencies -- it also brings along `WebMvcAutoConfiguration`, `DispatcherServletAutoConfiguration`, `HttpMessageConvertersAutoConfiguration`, and dozens of others that light up because Boot detected the right classes on the classpath.

```text
mvn dependency adds spring-boot-starter-web to classpath
        |
        v
DispatcherServlet.class is now loadable
        |
        v
DispatcherServletAutoConfiguration's @ConditionalOnClass(DispatcherServlet.class)
evaluates TRUE
        |
        v
Spring Boot registers a DispatcherServlet bean with sane defaults,
UNLESS you already defined your own DispatcherServlet bean
```

--> **The `@Conditional` family -- the mechanism that makes this safe and non-invasive:**

| Annotation | Activates the bean when... |
|---|---|
| `@ConditionalOnClass(X.class)` | class `X` is present on the classpath (e.g. only configure `DataSource` support if a JDBC driver class exists) |
| `@ConditionalOnMissingClass` | a given class is ABSENT from the classpath |
| `@ConditionalOnBean(X.class)` | a bean of type `X` already exists in the context |
| `@ConditionalOnMissingBean(X.class)` | NO bean of type `X` exists yet -- this is the big one: it's how auto-configuration politely backs off the moment YOU define your own bean of that type |
| `@ConditionalOnProperty(name=..., havingValue=...)` | a specific `application.properties`/`.yml` key is set to a specific value |
| `@ConditionalOnWebApplication` | the app is a web application (servlet-based or reactive) |
| `@ConditionalOnExpression` | a SpEL expression evaluates to true -- for conditions too complex for the other annotations |

--> **`@ConditionalOnMissingBean` is the single most important one to internalize** -- it's the reason auto-configuration NEVER fights you. If you declare your own `@Bean DataSource dataSource() {...}`, Spring Boot's own `DataSourceAutoConfiguration` sees a `DataSource` bean already exists and simply does not register its own. Auto-configuration is best understood as "smart defaults that get out of the way the instant you provide something more specific," not as a rigid framework you have to work around.

--> **The registration mechanism -- how Boot even knows which auto-configuration classes exist:**
--> **Spring Boot 2.x** used a file at `META-INF/spring.factories` inside each JAR, listing auto-configuration classes under the key `org.springframework.boot.autoconfigure.EnableAutoConfiguration`:

```properties
# META-INF/spring.factories (Spring Boot 2.x style)
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration,\
org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,\
org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
```

--> **Spring Boot 3.x** replaced this with a dedicated, simpler file: `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`, which is just a plain newline-separated list of fully-qualified class names (no key, no properties-file escaping, faster to parse):

```text
# META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration
org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
```

--> Either way, `@EnableAutoConfiguration` triggers Spring Boot to read this file (from every JAR on the classpath that has one, including third-party starters you didn't write), collect the full list of candidate configuration classes, and then evaluate each one's `@Conditional` annotations to decide which actually get applied.

--> **Seeing exactly what got auto-configured -- the debug flag.** This is the single most useful troubleshooting tool for "why isn't my bean showing up" or "why is this feature enabled when I didn't ask for it":

```properties
# application.properties
debug=true
```

```text
# Or as a command-line/JVM arg, without touching the properties file:
java -jar myapp.jar --debug
```

--> With `debug=true`, startup logs print a full **CONDITIONS EVALUATION REPORT**, split into:

```text
============================
CONDITIONS EVALUATION REPORT
============================

Positive matches:
-----------------
   DispatcherServletAutoConfiguration matched:
      - @ConditionalOnClass found required class 'jakarta.servlet.Filter' (OnClassCondition)

Negative matches:
-----------------
   DataSourceAutoConfiguration#dataSource:
      Did not match:
         - @ConditionalOnMissingBean (types: javax.sql.DataSource; SearchStrategy: all) found bean 'myDataSource' (OnBeanCondition)
```

--> Reading this report answers "is auto-configuration X active, and if not, exactly which condition failed" -- vastly faster than guessing or stepping through auto-configuration source with a debugger.

# Starters -- Curated Dependency Bundles

--> A "starter" is just a Maven/Gradle artifact with NO CODE of its own -- it's a `pom.xml` (or equivalent) whose only real content is a list of `<dependency>` entries pulling in everything typically needed for one purpose. Adding one line to your build file replaces what would otherwise be a dozen individually-chosen, individually-versioned dependencies.

```xml
<!-- Adding this ONE dependency transitively pulls in Spring MVC, an embedded
     Tomcat server, Jackson for JSON, validation support, and more -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

| Starter | Brings in | Typical use |
|---|---|---|
| `spring-boot-starter-web` | Spring MVC, embedded Tomcat, Jackson (JSON), validation | REST APIs / traditional web apps |
| `spring-boot-starter-data-jpa` | Spring Data JPA, Hibernate, JDBC support | Talking to a relational DB via JPA repositories |
| `spring-boot-starter-security` | Spring Security core + web security filters | Authentication/authorization |
| `spring-boot-starter-test` | JUnit 5, Mockito, AssertJ, Spring Test, JSONPath | Unit and integration testing (test-scoped by default) |
| `spring-boot-starter-validation` | Hibernate Validator (Bean Validation / JSR-380) | `@Valid`, `@NotNull`, etc. on request bodies |
| `spring-boot-starter-actuator` | Production-ready endpoints (`/health`, `/metrics`, ...) | Monitoring/ops |
| `spring-boot-starter-thymeleaf` | Thymeleaf templating engine + Spring integration | Server-rendered HTML views |

--> **Notice there's no `<version>` tag on the starter dependency above.** That's not an oversight -- it's the second half of what Spring Boot solves. Version numbers are resolved through a **Bill of Materials (BOM)**, declared once, usually via the `spring-boot-starter-parent` POM (or `spring-boot-dependencies` BOM imported into `dependencyManagement` if you can't use the parent POM, e.g. because you already have a different parent):

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.2.5</version>
</parent>
```

--> That single `<version>3.2.5</version>` on the PARENT is the only Spring Boot-related version number you ever set. It pulls in a `dependencyManagement` block (hundreds of entries) that pins exact, mutually-tested versions for every artifact Spring Boot knows about -- Jackson, SLF4J, Hibernate, Tomcat, JUnit, all of it. Every starter you then declare inherits its transitive dependency versions from that BOM, so `spring-boot-starter-web` and `spring-boot-starter-data-jpa` are GUARANTEED to pull in mutually compatible versions of shared dependencies like Jackson, rather than each independently deciding on a version and potentially colliding.
--> **This is dependency management, not dependency injection** -- easy to conflate the terms, but they're unrelated concepts that happen to share the word "dependency."

# application.properties vs application.yml

--> Both live in `src/main/resources/` and configure the exact same underlying property keys -- Spring Boot binds both formats to the same internal `Environment` abstraction, so which one you use is purely a syntax/readability choice. YAML tends to win for deeply nested config (profiles, lists) because it avoids repeating a long dotted prefix on every line; properties files win for simplicity and unambiguous grep-ability.

```properties
# application.properties
server.port=8081
spring.datasource.url=jdbc:mysql://localhost:3306/mydb
spring.datasource.username=root
spring.datasource.password=secret
spring.jpa.hibernate.ddl-auto=update
logging.level.root=INFO
logging.level.org.springframework.web=DEBUG
logging.level.com.example.myapp=TRACE
```

```yaml
# application.yml -- identical settings, nested form
server:
  port: 8081

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/mydb
    username: root
    password: secret
  jpa:
    hibernate:
      ddl-auto: update

logging:
  level:
    root: INFO
    org.springframework.web: DEBUG
    com.example.myapp: TRACE
```

--> **Common properties worth memorizing:**

| Property | Purpose |
|---|---|
| `server.port` | HTTP port the embedded server listens on (default `8080`) |
| `server.servlet.context-path` | URL prefix for the whole app, e.g. `/api` |
| `spring.datasource.url` / `.username` / `.password` / `.driver-class-name` | JDBC connection details |
| `spring.jpa.hibernate.ddl-auto` | schema handling: `none`, `validate`, `update`, `create`, `create-drop` |
| `spring.jpa.show-sql` | logs generated SQL statements |
| `logging.level.<package>` | per-package log level override |
| `spring.application.name` | logical app name, shows up in logs/Actuator/service discovery |
| `management.endpoints.web.exposure.include` | which Actuator endpoints are exposed over HTTP |

--> **Profile-specific files** -- Spring Boot lets you split config per-environment using the naming pattern `application-{profile}.properties` (or `.yml`): `application-dev.yml`, `application-prod.yml`, `application-test.yml`. The BASE `application.yml` always loads; the profile-specific file loads ON TOP of it (its values win on conflict) once that profile is activated:

```properties
# application.properties (base -- always loaded)
spring.profiles.active=dev
```

```yaml
# application-dev.yml -- only loaded when the "dev" profile is active
spring:
  datasource:
    url: jdbc:h2:mem:devdb
logging:
  level:
    com.example.myapp: DEBUG
```

```yaml
# application-prod.yml -- only loaded when the "prod" profile is active
spring:
  datasource:
    url: jdbc:mysql://prod-db:3306/mydb
logging:
  level:
    com.example.myapp: WARN
```

--> A single `application.yml` can ALSO hold multiple profiles in one file using `---` document separators (YAML multi-document), an alternative to separate files that some teams prefer for keeping related config visually together:

```yaml
spring:
  profiles:
    active: dev
---
spring:
  config:
    activate:
      on-profile: dev
server:
  port: 8081
---
spring:
  config:
    activate:
      on-profile: prod
server:
  port: 80
```

--> **Property precedence order (highest wins) -- abbreviated to the ones you'll actually hit day to day:**

```text
1. Command-line arguments              (--server.port=9090)
2. JVM system properties                (-Dserver.port=9090)
3. OS environment variables             (SERVER_PORT=9090)
4. Profile-specific application-{profile}.yml/.properties (outside the packaged jar, then inside)
5. Base application.yml/.properties     (outside the packaged jar, then inside)
6. @PropertySource-annotated files
7. Default properties set in code       (SpringApplication.setDefaultProperties)
```

--> The practical takeaway: anything you set as a command-line flag or environment variable at deploy time OVERRIDES whatever's baked into the packaged JAR's properties file -- this is exactly how the same JAR artifact gets deployed to dev, staging, and prod with different config without rebuilding it, and it's why containerized deployments (Docker/Kubernetes) almost always inject config via environment variables rather than baking environment-specific property files into the image.

# Typical Spring Boot Project Structure

```text
my-app/
├── pom.xml                                  <- Maven build file (or build.gradle for Gradle)
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/example/myapp/
│   │   │       ├── MyAppApplication.java    <- @SpringBootApplication entry point, ROOT package
│   │   │       ├── controller/
│   │   │       │   └── UserController.java
│   │   │       ├── service/
│   │   │       │   └── UserService.java
│   │   │       ├── repository/
│   │   │       │   └── UserRepository.java
│   │   │       └── model/
│   │   │           └── User.java
│   │   └── resources/
│   │       ├── application.yml              <- base config
│   │       ├── application-dev.yml          <- dev-profile overrides
│   │       ├── application-prod.yml         <- prod-profile overrides
│   │       ├── static/                      <- served as-is at "/" (JS, CSS, images)
│   │       └── templates/                   <- server-rendered views (Thymeleaf, etc.)
│   └── test/
│       └── java/
│           └── com/example/myapp/
│               └── MyAppApplicationTests.java
└── target/                                  <- compiled classes + the packaged jar (build output)
```

--> **`static/` vs `templates/`** -- `static/` is for files served exactly as-is (a request for `/logo.png` maps directly to `src/main/resources/static/logo.png`); `templates/` is for server-side rendered view templates (Thymeleaf, FreeMarker) that get PROCESSED (variables substituted, loops evaluated) before being sent to the client. A pure REST API backend typically uses neither -- it returns JSON directly from `@RestController` methods.
--> **The main class stays in the root package** for the component-scanning reason covered above -- everything else is organized by LAYER (`controller`/`service`/`repository`) or, in larger apps, by FEATURE/domain module, but either way it must live at or below the root package.

# Embedded Servers -- No More WAR Files

--> Traditional Java web apps were packaged as a **WAR (Web Application Archive)** and deployed INTO a separately-installed servlet container (Tomcat, JBoss, WebLogic) that you provisioned and managed on the target server ahead of time -- the server's lifecycle was independent of the app's.
--> Spring Boot flips this around: the servlet container (Tomcat by default, swappable for Jetty or Undertow) is EMBEDDED as a library dependency INSIDE your application's own JAR. Running the JAR starts the app AND its own private web server in the same JVM process -- there's no separate server installation to manage, configure, or keep in version-sync with your app.

```xml
<!-- Swapping Tomcat for Jetty: exclude Boot's default, add Jetty's starter -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
    <exclusions>
        <exclusion>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-tomcat</artifactId>
        </exclusion>
    </exclusions>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jetty</artifactId>
</dependency>
```

| Aspect | WAR + external container | Spring Boot embedded server |
|---|---|---|
| Deployment artifact | `.war`, deployed into a running container | self-contained executable `.jar` |
| Server lifecycle | managed separately, shared across multiple apps | bundled with, and owned by, your app |
| "It works on my machine" risk | container version mismatches between environments | server version is pinned in your build, travels with the app |
| Typical run command | copy WAR into `webapps/`, container picks it up | `java -jar app.jar` |
| Multiple apps per server | common (shared Tomcat instance, multiple WARs) | one embedded server per app instance (matches microservices/containers well) |

--> This is a major reason Spring Boot fits so naturally with Docker/Kubernetes-style deployment -- "one process, one app, one port" is exactly the embedded-server model, whereas the WAR-in-shared-container model actively fights containerization (why run a whole external Tomcat process just to host one app inside a container that's already providing isolation?).
--> **You can still produce a WAR if you truly need to** (e.g. deploying into a legacy, mandated app server) -- Spring Boot supports it via `SpringBootServletInitializer`, but it's very much the exception path, not the default.

# Running a Spring Boot Application

```text
# 1. Via Maven plugin -- best for active development, supports devtools live-reload
mvn spring-boot:run

# 2. Via Gradle equivalent
gradlew bootRun

# 3. Build a fat/executable jar, then run it directly with the JVM -- how you'd
#    actually ship and run it in production; the jar embeds all dependencies
#    AND the embedded server, so it needs nothing but a JVM to run
mvn clean package
java -jar target/my-app-0.0.1-SNAPSHOT.jar

# 4. From an IDE (IntelliJ/Eclipse/VS Code) -- right-click the class with
#    main() -> Run, or use the IDE's Spring Boot run configuration; functionally
#    identical to invoking main() directly, with debugger attach for free
```

--> **Passing config at launch, without touching any file** (ties back to the property-precedence order above):

```text
java -jar app.jar --server.port=9090 --spring.profiles.active=prod
```

# Common Gotchas

--> **Component scan package mismatch.** Covered above but worth repeating as the #1 "why is my bean/controller missing" cause: `@ComponentScan` (bundled inside `@SpringBootApplication`) only scans the package the main class lives in, plus sub-packages. A controller in a sibling or parent package is invisible to the container -- no error at startup, just a 404 or a missing bean at runtime. Fix: move the main class to the true root package, or add an explicit `@ComponentScan(basePackages = {...})`.
--> **Missing starter -> `NoSuchBeanDefinitionException` at runtime, not compile time.** If you `@Autowired` a `DataSource` or a `JpaRepository` but forgot to add `spring-boot-starter-data-jpa`, the code COMPILES FINE (the types exist if you have the plain `spring-orm`/`spring-jdbc` jars, or the interface is your own) but the auto-configuration that would have created the bean never activates because its `@ConditionalOnClass` check fails -- you only find out at startup (or worse, lazily, at first use) with a `NoSuchBeanDefinitionException` or `UnsatisfiedDependencyException`. Always check the auto-configuration report (`debug=true`) when a bean you expect isn't there.
--> **Property name typos are silently ignored, not rejected.** `application.properties`/`.yml` are just loosely-typed key-value maps as far as Spring Boot's relaxed binding is concerned -- `server.prot=8081` (typo) does NOT fail startup, it just does nothing, and your app quietly starts on the default port 8080 while you stare at a config file that "should" have worked. Mitigate with `@ConfigurationProperties`-bound classes plus `spring-boot-configuration-processor`, which gives IDE autocomplete and (with strict binding) can fail startup on genuinely unknown keys.
--> **YAML indentation errors** are the single most common YAML-specific bug -- YAML is whitespace-significant, and mixing tabs and spaces, or misaligning a nested key by one space, either throws a `ScannerException`/`ParserException` at startup or, more insidiously, silently nests a property under the WRONG parent, so it binds to a different (often unused) property path and again just does nothing:

```yaml
# WRONG -- "port" is nested one level too deep, effectively becomes
# server.servlet.port instead of server.port; server.port falls back to default
server:
  servlet:
  port: 8081

# RIGHT
server:
  port: 8081
```

--> **YAML also silently coerces bareword values** -- `enabled: no` or `version: 3.0` in YAML are NOT strings, they're parsed as boolean `false` and a floating point number respectively (the classic "Norway problem": a YAML value of literal `no` becomes boolean `false`). If you actually need the literal string, quote it: `enabled: "no"`.

# Best Practices Summary

--> **Keep the main class at the true root package** -- resolves component-scan ambiguity before it ever becomes a bug.
--> **Prefer starters over hand-picking individual dependencies** -- let the BOM manage versions; only drop to explicit versions when you have a specific, understood reason to override.
--> **Use profile-specific files (`application-{profile}.yml`) for environment differences**, and keep secrets (passwords, API keys) out of any file that's committed to source control -- inject them via environment variables or a secrets manager at deploy time, leaning on the property-precedence order to let them override file-based defaults.
--> **Turn on `debug=true` early when something "should be there and isn't"** -- the conditions evaluation report answers auto-configuration questions faster than searching source code or guessing.
--> **Prefer YAML for config with real nesting or multiple profiles in one file; prefer `.properties` for small, flat configs** where the extra structure of YAML buys nothing.
--> **Bind configuration to typed `@ConfigurationProperties` classes rather than scattering `@Value("${...}")` everywhere** once a feature has more than two or three related properties -- centralizes validation, gets IDE autocomplete via the annotation processor, and catches typos that a plain `@Value` would silently swallow.
--> **Treat auto-configuration as a default, not a constraint** -- defining your own bean of a given type is the sanctioned way to override Boot's behavior (thanks to `@ConditionalOnMissingBean`), so reach for that before fighting the framework or disabling auto-configuration wholesale with `@SpringBootApplication(exclude = ...)`.
