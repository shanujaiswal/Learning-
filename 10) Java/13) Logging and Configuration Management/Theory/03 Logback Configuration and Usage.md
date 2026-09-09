# What Logback Is, and Where It Fits

--> **Logback** is a logging engine written by Ceki Gülcü (SLF4J's own author) as the spiritual successor to Log4j 1.x, designed from day one to be SLF4J's "native" implementation -- when an application depends on `slf4j-api` + `logback-classic`, SLF4J calls flow into Logback with essentially no adapter overhead, because Logback implements the SLF4J `Logger` interface directly rather than through a separate translation layer.
--> Logback ships as three modules:

| Module | Purpose |
|---|---|
| `logback-core` | Foundational classes (appenders, encoders, filters) shared by the other two modules |
| `logback-classic` | The actual logging engine most applications use -- implements the SLF4J API directly, includes `logback.xml` configuration support |
| `logback-access` | Integrates with servlet containers (Tomcat, Jetty) to log HTTP access logs in a similar structured way |

--> **Dependency (Maven)** -- adding `logback-classic` transitively pulls in both `logback-core` and `slf4j-api`:

```xml
<dependency>
    <groupId>ch.qos.logback</groupId>
    <artifactId>logback-classic</artifactId>
    <version>1.5.6</version>
</dependency>
```

--> With zero configuration file present, Logback falls back to a sane default (log everything at DEBUG and above to the console) -- but real applications virtually always supply a `logback.xml` (or `logback-spring.xml` in Spring Boot projects, covered in the Configuration Management chapter) to control format, destinations, and per-package levels explicitly.

# The Structure of `logback.xml`

--> Logback looks for `logback.xml` (or `logback-test.xml`, which takes priority if present, intended for test-scope configuration) on the classpath at startup automatically -- no system property or manual wiring needed, unlike JUL.
--> **The three core building blocks**, and how they compose:

```text
<configuration>
    |
    |--> <appender>   -- WHERE log output goes (console, file, rolling file, etc.)
    |        |
    |        |--> <encoder>/<pattern>  -- HOW each log line is formatted
    |
    |--> <root level="...">        -- the DEFAULT level + appenders for everything
    |        |--> <appender-ref>    -- attaches one or more appenders to root
    |
    |--> <logger name="..." level="..."> -- PER-PACKAGE/PER-CLASS overrides
             |--> <appender-ref>    -- optionally route this logger's output elsewhere too
</configuration>
```

--> **A complete, realistic example:**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>

    <!-- Console appender -- human-readable output for local development -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- Rolling file appender -- writes to disk, rotates daily and by size -->
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/app.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
            <fileNamePattern>logs/app.%d{yyyy-MM-dd}.%i.log.gz</fileNamePattern>
            <maxFileSize>10MB</maxFileSize>
            <maxHistory>30</maxHistory>
            <totalSizeCap>1GB</totalSizeCap>
        </rollingPolicy>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- Root logger -- the default for any logger not explicitly overridden below -->
    <root level="INFO">
        <appender-ref ref="CONSOLE" />
        <appender-ref ref="FILE" />
    </root>

    <!-- Per-package override -- see this one package's SQL/debug detail without -->
    <!-- turning on DEBUG globally (which would flood the log with noise)        -->
    <logger name="com.example.orders" level="DEBUG" />

    <!-- Quiet down an extremely chatty third-party library -->
    <logger name="org.hibernate.SQL" level="WARN" />

</configuration>
```

--> **Reading the hierarchy in this example**: `com.example.orders.OrderService`'s logger inherits from `com.example.orders` (explicitly set to DEBUG here), which is why its DEBUG-level calls now print, while a completely unrelated class like `com.example.billing.InvoiceService` still falls back to `root`'s `INFO` threshold, since nothing more specific was set for `com.example.billing`.
--> **`additivity`** -- by default, a `<logger>`'s own `<appender-ref>` entries are used IN ADDITION TO whatever root's appenders are (log events "bubble up"); setting `additivity="false"` on a `<logger>` stops that bubbling, so ONLY that logger's own explicitly listed appenders receive its events, not root's.

# Appenders in Depth

| Appender | Purpose |
|---|---|
| `ConsoleAppender` | Writes to stdout/stderr -- typical for local development and containerized apps (where stdout is captured by the container runtime) |
| `FileAppender` | Writes to a single, non-rotating file -- rarely used alone in production since the file grows forever |
| `RollingFileAppender` | Writes to a file that automatically rotates based on a `rollingPolicy` -- the standard production choice |
| `AsyncAppender` | Wraps another appender, moving the actual I/O work onto a background thread so the calling application thread isn't blocked -- see the async section below |
| Custom / third-party appenders | e.g. appenders that ship structured JSON to Logstash, Kafka, or a cloud log service, via additional libraries (`logstash-logback-encoder` is a common one) |

--> **Rolling policies -- the two common strategies**, often combined (as in the example above):

| Rolling policy class | Rotates based on |
|---|---|
| `TimeBasedRollingPolicy` | Calendar time -- e.g. once per day, per hour |
| `SizeAndTimeBasedRollingPolicy` | BOTH time AND a max size -- rotates daily, but also mid-day if the file exceeds `maxFileSize` first (the pattern above); the most commonly used policy in real production configs |
| `FixedWindowRollingPolicy` + a separate size trigger | Pure size-based rotation with a fixed number of numbered backup files |

--> **`maxHistory`** controls how many days/periods of ROTATED files are retained before old ones are deleted automatically; `totalSizeCap` puts an overall disk-space ceiling on the whole rotated set regardless of `maxHistory`, deleting the oldest files first once exceeded -- together these two settings are what prevents "logs slowly filling the disk" from ever becoming an operational incident.

# Pattern Layout Syntax

--> The `<pattern>` string inside an `<encoder>` is Logback's mini format-string language for what each log line looks like. The most commonly used conversion words:

| Conversion word | Meaning |
|---|---|
| `%d{yyyy-MM-dd HH:mm:ss.SSS}` | Timestamp, formatted per the given date pattern |
| `%thread` | Name of the thread that logged the event |
| `%level` or `%-5level` | Log level (the `-5` left-justifies/pads it to 5 characters, keeping columns aligned) |
| `%logger{36}` | Logger name (i.e. usually the class name), truncated/abbreviated to at most 36 characters |
| `%msg` or `%message` | The actual log message text |
| `%n` | Platform-specific line separator -- always use this instead of a literal `\n` |
| `%X{key}` | An MDC value for the given key (prints empty string if not set) |
| `%ex` or `%exception` | The exception's stack trace, if one was passed to the logging call (included automatically after `%msg%n` even without explicitly naming it, though naming it lets you control formatting/depth) |

--> **A production-friendly, single-line pattern** commonly used so log-shipping tools can reliably split output line-by-line: `%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} [%X{requestId}] - %msg%n`.

# Log Levels Per Package -- Why This Matters in Practice

--> The whole reason `<logger name="..." level="...">` exists is to let you dial verbosity up or down for ONE slice of the codebase (or a noisy third-party library) without a global, all-or-nothing switch:

```xml
<!-- See exactly what SQL Hibernate is generating, without every OTHER -->
<!-- library in the app also dumping DEBUG noise -->
<logger name="org.hibernate.SQL" level="DEBUG" />
<logger name="org.hibernate.type.descriptor.sql" level="TRACE" />

<!-- Silence an extremely chatty connection-pool library except real problems -->
<logger name="com.zaxxer.hikari" level="WARN" />

<!-- Your own team's code, turned up for active debugging of one module -->
<logger name="com.example.payments" level="DEBUG" />
```

--> This is precisely the mechanism that makes it practical to run production at `INFO` globally while still being able to temporarily raise ONE package to `DEBUG` (often at runtime, without a redeploy, via a JMX-exposed level change or a config reload, both supported by Logback) when actively chasing down a bug.

# Async Logging with AsyncAppender

--> By default, a log call executes synchronously ON the calling thread -- including whatever I/O the appender does (writing to disk, a network appender, etc.), which means logging can measurably slow down a hot code path under load.
--> `AsyncAppender` wraps another appender and moves the actual write onto a separate, dedicated thread, using an internal blocking queue -- the calling thread just enqueues the event and returns immediately.

```xml
<appender name="ASYNC_FILE" class="ch.qos.logback.classic.AsyncAppender">
    <queueSize>512</queueSize>
    <discardingThreshold>0</discardingThreshold>  <!-- 0 = never discard, even under pressure -->
    <includeCallerData>false</includeCallerData>   <!-- true is expensive; only enable if you need %line/%method -->
    <appender-ref ref="FILE" />
</appender>

<root level="INFO">
    <appender-ref ref="ASYNC_FILE" />
</root>
```

--> **The trade-off** -- async logging trades a small risk (events sitting in memory in the queue could be lost if the JVM crashes hard before they're flushed) for meaningfully better throughput on the application's hot path. `discardingThreshold` (a percentage of the queue considered "nearly full") controls whether lower-priority events (TRACE/DEBUG/INFO) get silently DROPPED under sustained pressure to protect the application from being slowed down by a logging backlog -- setting it to `0` disables that dropping behavior entirely, prioritizing not losing anything over throughput.
--> `includeCallerData` defaults to `false` for a reason -- capturing the exact calling line/method (`%line`, `%method` in patterns) requires walking the stack trace, which is measurably expensive; enabling it is usually a deliberate, temporary debugging choice, not a default-on production setting.

# Common Gotchas

--> **`logback.xml` not found / not picked up** -- it must be directly on the classpath root (e.g. `src/main/resources/logback.xml` in a Maven/Gradle project), not just somewhere in the project directory tree.
--> **Both `logback.xml` and `logback-test.xml` present** -- Logback prefers `logback-test.xml` if it's found on the classpath, which is intentional for having different test-time vs runtime configuration, but surprises people who forget it exists and wonder why their production config edits seem to have no effect during test runs.
--> **Forgetting `additivity="false"`** -- leads to the same log line appearing to be duplicated, because it's being emitted by BOTH the specific logger's appenders AND root's appenders via bubbling.
--> **Rotating file appender misconfigured with no `maxHistory`/`totalSizeCap`** -- rotated files accumulate forever, silently filling the disk over weeks/months in a long-running production service.
--> **Setting `includeCallerData=true` on an `AsyncAppender` "just in case"** -- a common accidental performance regression, since it defeats a good portion of the point of going async in the first place.
--> **Confusing Logback's own internal status messages** ("no context given" printed to console at startup) **with actual application output** -- these come from Logback's internal `StatusManager` and usually indicate a configuration file problem worth investigating, not a bug in your business logic.

# Best Practices Recap

--> Always supply an explicit `logback.xml` in real projects rather than relying on the bare-bones default configuration.
--> Use `RollingFileAppender` with BOTH a size trigger and a `maxHistory`/`totalSizeCap` in production -- never a plain, non-rotating `FileAppender`.
--> Keep `root`'s level conservative (INFO or WARN) and use per-package `<logger>` overrides for targeted verbosity, rather than flipping global DEBUG on and off.
--> Wrap I/O-heavy appenders (especially file and network appenders under load) in `AsyncAppender` once logging becomes a measurable part of request latency.
--> Include an MDC field (like a request ID) in the pattern for any server-side application, so log lines from concurrent requests remain traceable.
