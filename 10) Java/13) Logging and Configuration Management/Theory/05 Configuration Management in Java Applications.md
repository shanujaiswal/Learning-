# Why Configuration Shouldn't Live in Code

--> Nearly every real application needs values that differ between environments -- a database URL, an API key, a thread-pool size, a feature flag, a log level -- and hardcoding any of these directly into Java source code creates several concrete problems: every environment-specific change requires a recompile and redeploy, secrets end up committed to version control where they're visible to everyone with repo access, and the same compiled artifact can't simply be promoted from dev to staging to production unchanged (defeating a core goal of reliable deployment pipelines: build once, deploy the same artifact everywhere, configured differently by environment).
--> **Externalized configuration** is the general practice of moving all of this environment-dependent data OUT of compiled code and into something read at startup (or even live-reloaded) -- files, environment variables, command-line arguments, or a dedicated configuration/secrets service -- so the exact same `.jar` can run correctly in every environment, each with its own configuration supplied from OUTSIDE the artifact.
--> **The general precedence idea** (formalized more specifically by Spring Boot later in this chapter, but true as a general pattern across most frameworks) -- configuration usually comes from multiple layered sources simultaneously, with a defined precedence order so more specific/environment-aware sources override more general/default ones: e.g. command-line arguments > environment variables > environment-specific file > base file > hardcoded defaults.

# Properties Files

--> Java's simplest, oldest configuration format is the `.properties` file -- flat key-value pairs, one per line, natively supported by `java.util.Properties` with zero extra dependencies.

```properties
# application.properties
app.name=Order Service
app.version=1.4.2
server.port=8080
db.url=jdbc:postgresql://localhost:5432/orders
db.username=app_user
db.pool.max-size=10
feature.new-checkout-flow.enabled=false
```

--> **Loading it in plain Java:**

```java
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

Properties props = new Properties();
try (InputStream in = Main.class.getClassLoader().getResourceAsStream("application.properties")) {
    if (in == null) {
        throw new IllegalStateException("application.properties not found on classpath");
    }
    props.load(in);
}
String dbUrl = props.getProperty("db.url");
int poolSize = Integer.parseInt(props.getProperty("db.pool.max-size", "5")); // "5" = fallback default
```

--> **Strengths**: dead simple, zero dependencies, universally understood, trivially diffable in version control.
--> **Limitations**: no native structure/nesting (everything is a flat string key, `db.pool.max-size` is just a naming CONVENTION, not real nesting), no native lists/arrays (usually faked with comma-separated values or numbered keys `item.0`, `item.1`), no comments-with-context beyond `#` lines, and no native support for types (everything is a `String` until your own code parses it).

# YAML Configuration

--> **YAML** ("YAML Ain't Markup Language") is a human-readable data-serialization format that supports genuine nesting, lists, and (loosely) typed scalars (numbers, booleans, strings) natively -- it has become the dominant configuration format in the Spring Boot ecosystem specifically because it expresses hierarchical configuration far more naturally than flat `.properties` keys.

```yaml
# application.yml
app:
  name: Order Service
  version: 1.4.2

server:
  port: 8080

db:
  url: jdbc:postgresql://localhost:5432/orders
  username: app_user
  pool:
    max-size: 10

feature:
  new-checkout-flow:
    enabled: false

allowed-origins:
  - https://app.example.com
  - https://admin.example.com
```

--> **YAML vs properties -- the same configuration, compared:**

| Aspect | `.properties` | `.yml` |
|---|---|---|
| Nesting | Flat keys with dot-naming convention only | Genuine nested structure |
| Lists | Awkward (`item.0=x`, `item.1=y`, or comma-joined string) | Native `- item` syntax |
| Readability at scale | Degrades with deep nesting-by-convention | Stays readable even several levels deep |
| Native JDK support | `java.util.Properties`, zero dependencies | Needs a YAML library (SnakeYAML, Jackson-YAML) -- though Spring Boot bundles this already |
| Whitespace sensitivity | Not significant | Indentation IS significant -- a common source of subtle bugs |
| Comments | `#` line comments | `#` line comments |

--> **The whitespace gotcha, worth calling out on its own** -- YAML uses indentation to express nesting, and MIXING tabs and spaces, or getting indentation levels inconsistent, silently changes the structure being parsed (or throws a parse error) -- unlike a brace-delimited format, there's no visual "closing" marker to catch a mismatch, so editors/linters that highlight YAML indentation are worth using.

# Externalized Configuration and Environment Variables

--> Beyond files, the two other primary sources of externalized configuration in Java applications are **environment variables** and **command-line arguments/system properties**, both read WITHOUT recompiling anything:

```java
// Environment variable -- typically used for secrets/host-specific values that
// shouldn't be committed to a config file checked into version control at all
String dbPassword = System.getenv("DB_PASSWORD");

// JVM system property -- set via -D flag on the java command line
// java -Dserver.port=9090 -jar app.jar
String port = System.getProperty("server.port", "8080"); // "8080" is the fallback default
```

--> **Why environment variables are the conventional home for secrets specifically** -- they're never accidentally committed to source control the way a checked-in properties file can be, they're natively supported by essentially every deployment platform (Docker, Kubernetes, cloud PaaS offerings, CI/CD systems) as a first-class mechanism for injecting values at container/process startup, and they naturally differ per-environment without needing separate files per environment.
--> **The Twelve-Factor App methodology** (a widely referenced set of best practices for building deployable, cloud-native applications) explicitly names "store config in the environment" as one of its core factors -- specifically calling out environment variables (not environment-named config FILES bundled inside the deployable artifact) as the correct place for anything that varies between deploys, precisely so the same build artifact is what gets promoted through every environment unchanged.

# Environment-Specific Profiles (The General Pattern)

--> A common pattern across many Java frameworks: maintain ONE base configuration file with shared defaults, plus SEPARATE, smaller override files per environment (dev, test, staging, production), and select which override applies via a single "active profile" setting at startup.

```text
application.yml            <- shared defaults, common to every environment
application-dev.yml        <- overrides for local development (e.g. verbose logging, local DB)
application-staging.yml    <- overrides for the staging environment
application-prod.yml       <- overrides for production (e.g. quieter logging, real DB credentials via env vars)
```

--> The active environment is typically selected via a single environment variable or system property read at startup (framework-specific mechanisms are covered below for Spring Boot specifically) -- the goal is that switching environments never means editing the base file, only choosing WHICH override file layers on top of it.

# Spring Boot's `application.yml` and Profiles

--> Spring Boot formalizes exactly the general pattern above into a first-class, well-documented mechanism, which is why it's worth covering concretely here even though this chapter isn't Spring-specific overall.
--> **Multi-document YAML with `---` separators** -- Spring Boot supports splitting profile-specific sections within a SINGLE `application.yml` file, using `---` as a document separator and `spring.config.activate.on-profile` to scope a section to one profile:

```yaml
# application.yml
spring:
  application:
    name: order-service

server:
  port: 8080

logging:
  level:
    root: INFO
    com.example.orders: INFO

---
spring:
  config:
    activate:
      on-profile: dev

server:
  port: 8081

logging:
  level:
    root: DEBUG
    com.example.orders: TRACE

db:
  url: jdbc:h2:mem:devdb

---
spring:
  config:
    activate:
      on-profile: prod

logging:
  level:
    root: WARN

db:
  url: ${DB_URL}          # pulled from an environment variable at runtime, not hardcoded
  username: ${DB_USERNAME}
  password: ${DB_PASSWORD}
```

--> **Equivalently, as separate files** (an alternative to the `---` multi-document style above, and generally preferred once profile-specific sections grow large): `application.yml` (base/shared) plus `application-dev.yml`, `application-prod.yml` (each containing ONLY that profile's overrides, no `on-profile` marker needed since the filename itself scopes it).
--> **Activating a profile** -- several equivalent ways, in practice usually just one of these per deployment:

```text
# As a JVM system property
java -Dspring.profiles.active=prod -jar app.jar

# As an environment variable (very common in containerized deployments)
SPRING_PROFILES_ACTIVE=prod java -jar app.jar

# In application.yml itself (rare -- usually only for a sensible local default)
spring:
  profiles:
    active: dev
```

--> **`${...}` placeholder resolution** -- any `${ENV_VAR_NAME}` (or `${ENV_VAR_NAME:defaultValue}` with a fallback) inside `application.yml` is resolved by Spring Boot's `Environment` abstraction against, in order, command-line args, JVM system properties, OS environment variables, and the properties/YAML files themselves -- this is exactly how the `db.url`/`username`/`password` values above stay OUT of the committed YAML file entirely while still being fully wired into the application's configuration.
--> **Spring Boot's actual configuration precedence** (highest wins; a heavily abbreviated version of the full documented order) roughly follows: command-line arguments > `SPRING_APPLICATION_JSON` env var > OS environment variables > profile-specific `application-{profile}.yml` > base `application.yml` > `@PropertySource` annotations > default values in code. The practical takeaway from this ordering: whatever is set via environment variable or command line at deploy time ALWAYS wins over whatever is baked into a checked-in YAML file, which is exactly the override behavior externalized configuration is meant to provide.

# Secrets Management Basics

--> Secrets (database passwords, API keys, signing keys, tokens) deserve handling distinct from ordinary configuration, because a leaked secret is a security incident, not just a misconfiguration.

| Practice | Why |
|---|---|
| Never commit real secrets to version control, even in a "private" repo | Git history retains old commits indefinitely; a repo's visibility can also change later |
| Prefer environment variables or a dedicated secrets manager over checked-in config files for secret VALUES | Keeps the secret out of the artifact and out of source control entirely |
| Use placeholder/reference syntax in checked-in config (`${DB_PASSWORD}`), never the literal value | The committed file documents WHAT is needed without containing the secret itself |
| Adopt a dedicated secrets manager for anything beyond small projects (HashiCorp Vault, AWS Secrets Manager, Azure Key Vault, Kubernetes Secrets) | Centralized rotation, access auditing, and fine-grained access control that plain env vars can't provide alone |
| Rotate secrets periodically, and immediately after any suspected leak | Limits the window an exposed credential remains usable |
| Keep example/template config files (`application.yml.example`) with placeholder values, separate from the real, gitignored file | Documents required configuration keys for new developers without exposing real values |
| Never log secret values, even at DEBUG level | Logs often end up in less access-controlled places than the original secret store |

--> **A minimal, practical setup for a small project without a dedicated secrets manager**: keep a `.env` file (or environment-specific real config file) listed in `.gitignore`, load it via environment variables at process/container startup, and commit only a `.env.example` template showing which keys are required with placeholder/dummy values.

# Common Gotchas

--> **Committing a real `application-prod.yml` with actual credentials** -- an extremely common real-world incident; always double-check `.gitignore` covers any file that might end up holding real secret values.
--> **YAML indentation errors changing meaning silently** -- covered above; validate YAML with a linter, especially before a production deploy.
--> **Forgetting a fallback/default when reading `System.getenv(...)`** -- an unset environment variable returns `null`, not an empty string or exception; unguarded use causes a `NullPointerException` far from the actual missing-configuration root cause. Always validate required configuration explicitly at startup (fail fast with a clear error) rather than letting a missing value surface as a confusing NPE deep in business logic.
--> **Mixing up which profile is actually active** -- especially in CI/CD pipelines where the active-profile environment variable might be set at a different layer (Dockerfile vs. Kubernetes manifest vs. CI job config) than expected; log the active profile explicitly at startup as a sanity check.
--> **Assuming `.properties`/`.yml` files are hot-reloaded automatically** -- by default in most setups they're read once at startup; live-reload requires an explicit mechanism (a config-refresh endpoint, Spring Cloud Config with `@RefreshScope`, or a file-watcher you build yourself) and shouldn't be assumed without checking.

# Best Practices Recap

--> Externalize anything that differs between environments -- never hardcode environment-specific values into compiled code.
--> Prefer YAML over flat properties files once configuration has any real nesting or lists, but stay disciplined about indentation.
--> Layer configuration as base-plus-profile-overrides, and make the "active profile" selection itself simple and explicit (one environment variable, checked at startup).
--> Treat secrets as a distinct category from ordinary configuration -- environment variables or a real secrets manager, never plain committed files, and never logged.
--> Fail fast and loudly at startup if required configuration is missing, rather than letting a missing value surface confusingly deep in the application later.
