# Why Logging Instead of System.out.println

--> Logging is the practice of recording structured, timestamped messages about what a running program is doing -- errors, warnings, key business events, diagnostic detail -- so that a human (or a monitoring system) can understand and troubleshoot the program's behavior AFTER the fact, often on a machine nobody is sitting in front of.
--> Every beginner starts with `System.out.println(...)` for this, and it works for tiny throwaway programs -- but it breaks down almost immediately once code moves toward anything real, for a set of very concrete reasons:

| Problem with `System.out.println` | What a real logging framework gives you instead |
|---|---|
| No severity levels -- everything looks the same | Levels (DEBUG, INFO, WARN, ERROR, ...) so output can be filtered by importance |
| Always writes to stdout -- can't redirect to a file, database, or remote log collector without extra plumbing | Configurable **appenders/handlers** -- console, file, rolling file, syslog, network socket, database, all at once if desired |
| Can't be turned off/on per class or package without touching code | Runtime-configurable, per-package/per-class log level thresholds |
| No timestamps, thread name, class name, or line context unless you type it manually every time | Automatic timestamp, thread, logger name, and (optionally) caller location on every line, via a configured **pattern** |
| Expensive string concatenation runs EVERY time, even if nobody will ever see the message | Lazy evaluation -- the message is only built if the level is actually enabled |
| No file rotation -- a long-running process writing to stdout redirected to a file grows forever | Built-in **rolling file appenders** -- rotate daily, by size, or both, with automatic old-file cleanup |
| Mixing debug noise with genuine errors makes production output unreadable | Clean separation -- ship INFO+ to production console, keep DEBUG available behind a flag for troubleshooting |
| Output ordering can interleave unpredictably across threads with no attribution | Structured output that (with MDC, covered in the SLF4J chapter) can tag which request/user/thread produced each line |

--> **The core mental shift**: `System.out.println` is "print a string somewhere" -- logging is "record an EVENT, at a SEVERITY, from a SOURCE, and let configuration (not code) decide where it ends up and whether it's even worth keeping." That separation of "what to log" (in code) from "where/how it's stored and filtered" (in configuration) is the single biggest reason logging frameworks exist.
--> A secondary but important reason: `System.err` (used for stack traces via `printStackTrace()`) has exactly the same problems as `System.out` -- it's still just an unstructured stream with no levels, no routing, and no configuration story.

# Log Levels -- The Universal Vocabulary

--> Nearly every logging framework in every language converges on a similar hierarchy of severity levels, ordered from least to most severe. Understanding this ordering is fundamental, because configuration almost always works as a THRESHOLD: "log this level and everything more severe than it, but suppress anything less severe."

| Level | Typical meaning | Example use |
|---|---|---|
| **TRACE** (finest) | Extremely fine-grained diagnostic detail, rarely enabled even in development | Logging every loop iteration, every method entry/exit |
| **DEBUG** | Detailed information useful while developing/diagnosing, too noisy for production by default | "Fetched 42 rows from query X", variable values at a decision point |
| **INFO** | Normal, expected application milestones worth recording in production | "Server started on port 8080", "Order #123 placed successfully" |
| **WARN** | Something unexpected happened, but the application can continue | "Config value missing, falling back to default", "Retrying failed request (2/3)" |
| **ERROR** | A real failure occurred; something did NOT work as intended | "Failed to save order to database", uncaught exception in a request handler |
| **FATAL** (some frameworks) | The application cannot continue and is about to (or should) terminate | Unrecoverable startup failure |

--> **The threshold behavior in practice** -- if a logger's configured level is `INFO`, then INFO, WARN, and ERROR messages are emitted, but DEBUG and TRACE calls are silently skipped (not evaluated at all, ideally -- see the lazy-evaluation gotcha below). Raising the threshold to `WARN` in production is a common way to cut log volume without touching any code.
--> **Practical guidance on picking a level** -- a good rule of thumb: DEBUG for "this would help me understand what the code did," INFO for "this is a notable business/lifecycle event a normal operator would want to see," WARN for "recoverable but suspicious," ERROR for "an operation failed and someone should look into it." Overusing INFO for everything defeats the entire purpose of having levels.

# java.util.logging (JUL) -- The Built-in Framework

--> `java.util.logging`, commonly abbreviated **JUL**, has shipped as part of the JDK itself since Java 1.4 (package `java.util.logging`) -- it requires **zero external dependencies**, which is precisely why it's the natural starting point for learning logging concepts and for small self-contained programs/demos.
--> JUL's core building blocks:

```text
Logger        -- the object your code calls (logger.info("..."), logger.warning("..."))
   |
   |--> has a Level threshold (OFF, SEVERE, WARNING, INFO, CONFIG, FINE, FINER, FINEST, ALL)
   |
   |--> has one or more Handlers attached (ConsoleHandler, FileHandler, custom handlers)
              |
              |--> each Handler has its OWN Level threshold too (can filter further)
              |--> each Handler has a Formatter (SimpleFormatter, XMLFormatter, custom)
```

--> **JUL's level names differ from the "universal" list above** -- this trips people up constantly:

| JUL level | Roughly equivalent to |
|---|---|
| `SEVERE` | ERROR |
| `WARNING` | WARN |
| `INFO` | INFO |
| `CONFIG` | (JUL-specific, config-related diagnostic info) |
| `FINE` | DEBUG |
| `FINER` | more detailed DEBUG |
| `FINEST` | TRACE |

--> **Getting a logger and using it:**

```java
import java.util.logging.Logger;
import java.util.logging.Level;

public class OrderService {
    // Convention: one Logger per class, named after the fully-qualified class name --
    // this is what lets you configure levels PER CLASS or PER PACKAGE later.
    private static final Logger logger = Logger.getLogger(OrderService.class.getName());

    public void placeOrder(String orderId) {
        logger.info("Placing order " + orderId);       // simple path
        logger.log(Level.FINE, "Debug detail about order {0}", orderId); // parameterized path
        try {
            // ... business logic ...
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to place order " + orderId, e); // logs message + stack trace
        }
    }
}
```

--> **Why `Logger.getLogger(ClassName.class.getName())` and not a hardcoded string** -- JUL (and every other framework) organizes loggers into a **hierarchical namespace** matching your package structure (`com.example.orders.OrderService` is a "child" of `com.example.orders`, which is a child of `com.example`). This hierarchy is exactly what lets you say "set everything under `com.example.orders` to FINE, but leave everything else at INFO" in configuration, without editing code.
--> **Attaching a handler and formatter manually:**

```java
import java.util.logging.*;

Logger logger = Logger.getLogger("com.example.app");
logger.setLevel(Level.FINE);
logger.setUseParentHandlers(false);   // don't also send to the default console handler

ConsoleHandler handler = new ConsoleHandler();
handler.setLevel(Level.FINE);
handler.setFormatter(new SimpleFormatter());
logger.addHandler(handler);
```

--> **`FileHandler` for writing to disk, with basic rotation:**

```java
FileHandler fileHandler = new FileHandler("app.log.%g", 1_000_000, 5, true);
// pattern "app.log.%g" -- %g is replaced with a generation number (0,1,2,...)
// 1_000_000            -- rotate once the file reaches ~1MB
// 5                     -- keep at most 5 rotated files (oldest gets overwritten)
// true                  -- append to existing file instead of overwriting on startup
fileHandler.setFormatter(new SimpleFormatter());
logger.addHandler(fileHandler);
```

# Configuring JUL via `logging.properties`

--> Rather than configuring handlers/levels in code (as above), JUL can be configured externally via a `.properties` file, which is what makes it a genuine "logging framework" rather than just a print-wrapper -- the JVM reads it via the system property `java.util.logging.config.file`.

```properties
# logging.properties
handlers = java.util.logging.ConsoleHandler, java.util.logging.FileHandler

.level = INFO

# Per-package/per-class level overrides -- this is the hierarchy in action
com.example.orders.level = FINE
com.example.legacy.level = SEVERE

java.util.logging.ConsoleHandler.level = ALL
java.util.logging.ConsoleHandler.formatter = java.util.logging.SimpleFormatter

java.util.logging.FileHandler.pattern = logs/app%u.log
java.util.logging.FileHandler.limit = 1000000
java.util.logging.FileHandler.count = 5
java.util.logging.FileHandler.formatter = java.util.logging.SimpleFormatter
```

```text
java -Djava.util.logging.config.file=logging.properties -cp . com.example.Main
```

--> **The effective level resolution rule** -- if `com.example.orders.OrderService`'s logger has no level explicitly set, JUL walks UP the hierarchy (`com.example.orders` -> `com.example` -> root `.level`) until it finds one that IS set, and uses that. This is exactly the mechanism that lets you leave most of the codebase alone and only override the one noisy package you're currently debugging.

# Limitations of java.util.logging

--> JUL is genuinely useful to learn the concepts (and it's what the runnable demo in this chapter's practicals file uses, since it needs zero external dependencies) -- but it is rarely chosen for real production applications, for concrete reasons:

| Limitation | Why it matters |
|---|---|
| Awkward, verbose configuration format (`.properties` with long dotted keys) compared to Logback's XML or Log4j2's XML/JSON/YAML | Harder to read/maintain at scale |
| Weaker pattern/layout customization than Logback/Log4j2 | Less control over exact log line format |
| No built-in async logging | Log calls can block the calling thread on I/O under load |
| Smaller ecosystem of appenders (no built-in "send to Kafka", "send to Elasticsearch", etc., without extra glue) | More manual integration work for centralized logging |
| Many libraries you depend on (Spring, Hibernate, Apache HttpClient, etc.) already log through **SLF4J**, not JUL directly | Mixing JUL app-code with SLF4J-based library code means TWO separate configuration stories unless you bridge them |
| Historically inconsistent adoption/perception in the Java ecosystem | Most real-world projects standardize on SLF4J + Logback (or Log4j2) instead |

--> This last point is the important one and sets up the next chapter: because so much of the Java ecosystem (Spring, Hibernate, Apache Commons, etc.) logs through the **SLF4J** facade rather than JUL directly, real applications adopt SLF4J as the API their OWN code calls too, and pick a single underlying implementation (typically Logback) for everything, JUL included, via a **bridge**. JUL isn't "wrong" -- it's simply the JDK's own answer to a problem the broader ecosystem solved differently and more thoroughly.

# Common Gotchas

--> **Forgetting `logger.setUseParentHandlers(false)`** -- by default, JUL's root logger already has a `ConsoleHandler` attached; if you add your OWN `ConsoleHandler` without disabling parent handler propagation, every message gets printed TWICE.
--> **Expensive message construction even when the level is disabled** -- `logger.fine("Result: " + expensiveCompute())` still evaluates `expensiveCompute()` even if FINE is disabled, because Java evaluates method arguments before the call happens. Guard expensive computation with `if (logger.isLoggable(Level.FINE))`, or prefer the parameterized `logger.log(Level.FINE, "Result: {0}", () -> expensiveCompute())` supplier form added in Java 8.
--> **Confusing JUL's `Level.INFO`/`WARNING`/`SEVERE` names with the "universal" INFO/WARN/ERROR names** used by SLF4J/Logback/Log4j2 -- they map conceptually but are literally different enum constants in different packages.
--> **Not setting a level on the `FileHandler`/`ConsoleHandler` while assuming the `Logger`'s level is the only gate** -- both the Logger AND each Handler have independent thresholds; a message must clear BOTH to actually be emitted.
--> **Relying only on `printStackTrace()` for exceptions** -- it writes to `System.err`, bypasses the entire logging configuration (levels, handlers, formatting, file destinations), and is very hard to correlate with the surrounding log context; always prefer `logger.log(Level.SEVERE, "message", exception)`, which prints the full stack trace THROUGH the logging framework.

# Best Practices Recap

--> Always log through a `Logger` obtained via `getLogger(SomeClass.class.getName())` -- never hardcode logger names as unrelated strings.
--> Choose the log level deliberately per message -- resist the temptation to log everything at INFO.
--> Never log sensitive data (passwords, full credit card numbers, auth tokens) at any level -- logs often end up in less-secured locations than the data's original source.
--> Prefer passing the exception object itself to the logging call (so the framework prints the full stack trace) rather than only logging `e.getMessage()`.
--> Treat logging configuration as something that changes per-environment (verbose in dev, quieter in production) WITHOUT needing a code change or redeploy -- this is the entire point of externalizing it to a properties/XML file, a theme this whole chapter builds toward.
