# What Log4j2 Is, and How It Relates to Log4j 1.x

--> **Log4j2** is Apache's ground-up rewrite of the original Log4j 1.x framework -- despite the similar name, it is a completely different codebase, not an incremental upgrade, built specifically to address Log4j 1.x's architectural limits (no async logging, weaker performance, and Log4j 1.x reaching official end-of-life) and to compete directly with Logback on features and speed.
--> Log4j2 can be used two ways: (1) as the underlying engine BEHIND the SLF4J facade (via the `log4j-slf4j2-impl` binding, following the same facade/binding model described in the SLF4J chapter), or (2) directly via its own native API (`org.apache.logging.log4j.Logger` / `LogManager`). Using it behind SLF4J is the more common and more portable choice for application code.
--> **A note on the infamous Log4Shell vulnerability (CVE-2021-44228, December 2021)** -- this was a critical remote-code-execution vulnerability in Log4j2's message-lookup substitution feature (`${jndi:...}` patterns being evaluated inside logged messages), NOT in Logback or Log4j 1.x. It's worth knowing about specifically because it drove a huge wave of Log4j2 upgrades industry-wide (fixed from version 2.15.0 onward, with further hardening in 2.17.x) and is a large part of why many teams are cautious about pinning Log4j2 versions and keeping them current.

# Dependencies (Maven)

--> **Using Log4j2 directly (native API):**

```xml
<dependency>
    <groupId>org.apache.logging.log4j</groupId>
    <artifactId>log4j-api</artifactId>
    <version>2.23.1</version>
</dependency>
<dependency>
    <groupId>org.apache.logging.log4j</groupId>
    <artifactId>log4j-core</artifactId>
    <version>2.23.1</version>
</dependency>
```

--> **Using Log4j2 as the engine behind SLF4J (the more common real-world setup):**

```xml
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-api</artifactId>
    <version>2.0.13</version>
</dependency>
<dependency>
    <groupId>org.apache.logging.log4j</groupId>
    <artifactId>log4j-slf4j2-impl</artifactId>
    <version>2.23.1</version>
</dependency>
<dependency>
    <groupId>org.apache.logging.log4j</groupId>
    <artifactId>log4j-core</artifactId>
    <version>2.23.1</version>
</dependency>
```

--> **Important**: do NOT put both `logback-classic` and a Log4j2 SLF4J binding on the classpath simultaneously -- exactly as covered in the SLF4J chapter's "multiple bindings" gotcha, only one binding should be present at a time.

# Configuration File Formats

--> Log4j2's biggest structural difference from Logback is format flexibility -- it supports **XML, JSON, YAML, and `.properties`** configuration, auto-detected by filename (`log4j2.xml`, `log4j2.json`, `log4j2.yaml`, `log4j2.properties`) found on the classpath. XML is by far the most common in practice, so that's the primary focus below.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration status="WARN">

    <Appenders>
        <Console name="Console" target="SYSTEM_OUT">
            <PatternLayout pattern="%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n" />
        </Console>

        <RollingFile name="RollingFile"
                      fileName="logs/app.log"
                      filePattern="logs/app-%d{yyyy-MM-dd}-%i.log.gz">
            <PatternLayout pattern="%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n" />
            <Policies>
                <TimeBasedTriggeringPolicy />
                <SizeBasedTriggeringPolicy size="10MB" />
            </Policies>
            <DefaultRolloverStrategy max="30" />
        </RollingFile>
    </Appenders>

    <Loggers>
        <Logger name="com.example.orders" level="DEBUG" additivity="false">
            <AppenderRef ref="Console" />
            <AppenderRef ref="RollingFile" />
        </Logger>

        <Logger name="org.hibernate.SQL" level="WARN" />

        <Root level="INFO">
            <AppenderRef ref="Console" />
            <AppenderRef ref="RollingFile" />
        </Root>
    </Loggers>

</Configuration>
```

--> **Structural comparison to `logback.xml`**: Log4j2 groups configuration under explicit `<Appenders>` and `<Loggers>` parent elements, and capitalizes element names (`<RollingFile>`, `<PatternLayout>`) -- functionally, the concepts (appenders, pattern layouts, per-logger levels, rolling policies, additivity) map almost one-to-one onto Logback's equivalents, just with different XML shapes and naming conventions.
--> **The `status` attribute on `<Configuration>`** -- controls Log4j2's OWN internal diagnostic logging (about its own startup/config parsing), separate entirely from your application's log output; setting it to `WARN` (as above) keeps Log4j2's internal chatter quiet unless something about the configuration itself is actually wrong.
--> **Automatic reconfiguration** -- Log4j2 supports a `monitorInterval` attribute on `<Configuration>` (e.g. `monitorInterval="30"`) that periodically re-checks the config file for changes and reloads it live, without restarting the application -- a feature Logback also supports (`scan="true" scanPeriod="30 seconds"` on its own `<configuration>` root element) but is worth calling out explicitly here since it's commonly used in Log4j2 configs.

# Native API Usage

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class OrderService {
    private static final Logger logger = LogManager.getLogger(OrderService.class);

    public void placeOrder(String orderId) {
        logger.info("Placing order {}", orderId);              // same {} placeholder style as SLF4J
        try {
            // ...
        } catch (Exception e) {
            logger.error("Failed to place order {}", orderId, e);
        }
    }
}
```

--> Notice the native Log4j2 API deliberately mirrors SLF4J's placeholder style (`{}`) and even its method names (`info`, `debug`, `warn`, `error`) closely -- this was a conscious design choice to keep the two APIs feeling nearly interchangeable.

# Async Logging in Log4j2

--> Async logging is one of Log4j2's headline advantages over both JUL and (until Logback's own `AsyncAppender` matured) traditional synchronous logging -- Log4j2 offers TWO distinct async approaches:

| Approach | How it works | Trade-off |
|---|---|---|
| **Async Appenders** | Same idea as Logback's `AsyncAppender` -- wrap a specific appender, only ITS I/O moves off the calling thread | Simpler to adopt selectively; other loggers/appenders stay synchronous |
| **Async Loggers** | The ENTIRE logging call becomes asynchronous, backed by the **LMAX Disruptor** library (a high-performance lock-free queue) | Much higher throughput; requires the `disruptor` dependency; can be enabled globally or per-logger ("mixed" mode) |

--> **Enabling fully async loggers** requires the Disruptor dependency and a system property (or a dedicated properties file) telling Log4j2 to use `AsyncLoggerContextSelector` for ALL loggers:

```xml
<dependency>
    <groupId>com.lmax</groupId>
    <artifactId>disruptor</artifactId>
    <version>3.4.4</version>
</dependency>
```

```text
-Dlog4j2.contextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector
```

--> **"Mixed" async/sync configuration** -- a `<AsyncLogger>` element can be used for a SPECIFIC logger to make just that one asynchronous, while `<Root>`/other `<Logger>` entries stay synchronous, giving finer-grained control than an all-or-nothing global switch:

```xml
<Loggers>
    <AsyncLogger name="com.example.highvolume" level="INFO" additivity="false">
        <AppenderRef ref="RollingFile" />
    </AsyncLogger>
    <Root level="INFO">
        <AppenderRef ref="Console" />
    </Root>
</Loggers>
```

--> **Why async logging matters at all** -- in a synchronous setup, every log call blocks the calling thread until the write (console flush, disk I/O, or network send) completes; under high load, this can become a measurable fraction of total request latency. Async logging (Disruptor-backed Async Loggers especially) is specifically engineered to minimize that cost, at the modest risk of losing the last few buffered events if the JVM terminates abruptly.

# Log4j2 vs Logback -- Comparison

| Aspect | Logback | Log4j2 |
|---|---|---|
| Relationship to SLF4J | Native implementation, built BY SLF4J's author | Used via a separate SLF4J binding jar, or its own native API |
| Configuration formats | XML (primarily); Groovy-based config also supported | XML, JSON, YAML, `.properties` |
| Async logging | `AsyncAppender` wraps an appender | Async Appenders (like Logback) AND fully async Disruptor-backed Async Loggers -- generally the higher-throughput option |
| Live config reload | `scan="true"` on `<configuration>` | `monitorInterval` attribute -- same idea |
| Maturity / ecosystem default | The most common default pick alongside SLF4J (e.g. Spring Boot's default) | Very strong alternative, particularly favored where raw async throughput matters most |
| Security track record | No equivalent incident at Log4j2's Log4Shell scale | Log4Shell (2021) -- patched, but a reason some teams remain cautious/vigilant about version pinning |
| Performance (synchronous case) | Competitive, generally very good | Competitive; historically benchmarked slightly ahead in some scenarios, especially with Async Loggers enabled |

--> **Practical takeaway on choosing between them** -- both are mature, actively maintained, fully capable production logging engines; Logback is the more common default (partly because of its tight SLF4J integration and being Spring Boot's out-of-the-box choice), while Log4j2 is often chosen specifically when async logging throughput is a priority, or when JSON/YAML configuration format flexibility is preferred. Neither choice is "wrong" for a typical application, and both are configured through conceptually identical building blocks: appenders, pattern layouts, per-package levels, and rolling policies.

# Common Gotchas

--> **Mixing Log4j 1.x and Log4j2 dependencies** -- these are entirely separate artifacts/packages (`org.apache.log4j` vs `org.apache.logging.log4j`); accidentally depending on both (often via an old transitive dependency) causes confusing classpath conflicts.
--> **Forgetting `log4j-core` alongside `log4j-api`** -- `log4j-api` alone is just the API surface; without `log4j-core` on the classpath, nothing actually gets logged (mirroring SLF4J's own facade/binding split, but INSIDE the Log4j2 project itself).
--> **Running an unpatched Log4j2 version** -- given the Log4Shell history, always keep Log4j2 on a current, patched version; treat any dependency scanner warning about it as high priority.
--> **Enabling Async Loggers without adding the `disruptor` dependency** -- Log4j2 silently falls back to synchronous behavior (with a startup warning) rather than failing loudly, which can be confusing if you expect the throughput benefit and don't see it.
--> **Assuming `log4j2.xml` auto-reload happens without `monitorInterval` set** -- like Logback's `scan`, this is opt-in, not automatic.

# Best Practices Recap

--> Prefer using Log4j2 through the SLF4J facade in application code (rather than its native API) unless you specifically need a Log4j2-only feature, to preserve the portability benefits described in the SLF4J chapter.
--> Keep Log4j2 on a current patched version at all times, given its security history.
--> Reach for Async Loggers (Disruptor-backed) specifically when logging throughput/latency has been measured to matter, not as a default -- the added dependency and configuration complexity should be justified by an actual need.
--> Structure `log4j2.xml` the same way you would `logback.xml` conceptually: conservative root level, targeted per-package overrides, rolling file policy with retention limits.
--> Never let both a Logback binding and a Log4j2 SLF4J binding coexist on the same classpath.
