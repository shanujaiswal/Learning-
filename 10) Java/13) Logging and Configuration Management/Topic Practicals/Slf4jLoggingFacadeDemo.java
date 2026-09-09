/*
 * Slf4jLoggingFacadeDemo.java
 *
 * ILLUSTRATIVE ONLY -- this file is NOT compilable/runnable as-is. SLF4J
 * (org.slf4j.Logger / LoggerFactory) is a pure facade: it ships no logging
 * engine of its own. To actually run this you need, on the classpath:
 *
 *     1. slf4j-api          (the facade itself, e.g. slf4j-api-2.0.13.jar)
 *     2. EXACTLY ONE binding, e.g.:
 *          - logback-classic (also pulls in logback-core + slf4j-api transitively), OR
 *          - slf4j-simple    (a tiny, dependency-free binding good for quick demos)
 *
 * Example with Maven:
 *     <dependency>
 *         <groupId>org.slf4j</groupId>
 *         <artifactId>slf4j-api</artifactId>
 *         <version>2.0.13</version>
 *     </dependency>
 *     <dependency>
 *         <groupId>org.slf4j</groupId>
 *         <artifactId>slf4j-simple</artifactId>
 *         <version>2.0.13</version>
 *     </dependency>
 *
 * Or, to compile/run by hand with two jars already downloaded next to this file:
 *     javac -cp "slf4j-api-2.0.13.jar;slf4j-simple-2.0.13.jar" Slf4jLoggingFacadeDemo.java
 *     java  -cp ".;slf4j-api-2.0.13.jar;slf4j-simple-2.0.13.jar" Slf4jLoggingFacadeDemo
 * (use ':' instead of ';' as the classpath separator on Linux/macOS)
 *
 * Demonstrates:
 *     1. Getting a Logger via LoggerFactory.getLogger(SomeClass.class) -- note this
 *        takes the Class object directly, unlike JUL's getLogger(name.getName())
 *     2. Parameterized logging with {} placeholders (and why it beats string concatenation)
 *     3. Passing a Throwable as the trailing argument to get a full stack trace logged
 *     4. MDC (Mapped Diagnostic Context) -- MDC.put/.get/.remove/.clear, and why it must
 *        always be cleared in a finally block (thread-pool reuse gotcha)
 *     5. What happens with ZERO bindings on the classpath (a one-time stderr warning,
 *        then all log calls silently become no-ops) vs. what happens with MULTIPLE
 *        bindings (a warning, then an unpredictable pick -- must be fixed by excluding
 *        the unwanted one from the dependency tree)
 *
 * Covers Theory chapter:
 *     10) Java/13) Logging and Configuration Management/Theory/02 SLF4J as a Logging Facade.md
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class Slf4jLoggingFacadeDemo {

    // One Logger per class, obtained via LoggerFactory.getLogger(Class) -- the
    // idiomatic SLF4J pattern used almost everywhere. Compare against JUL's
    // Logger.getLogger(SomeClass.class.getName()), which needs the extra .getName().
    private static final Logger logger = LoggerFactory.getLogger(Slf4jLoggingFacadeDemo.class);

    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }

    public static void main(String[] args) {

        // ---------------------------------------------------------------
        // 1) Basic logging through the facade -- exact output format/destination
        // depends entirely on whichever binding is on the classpath (Logback,
        // slf4j-simple, Log4j2-via-binding, etc.) -- SLF4J itself has no opinion.
        // ---------------------------------------------------------------
        printSection("1) Basic SLF4J logging (format/destination depends on the active binding)");
        logger.info("Application starting up");
        logger.warn("This is a warning-level message");
        logger.error("This is an error-level message");
        logger.debug("This DEBUG message may or may not print, depending on the binding's configured level");

        // ---------------------------------------------------------------
        // 2) Parameterized ({}) logging -- the single biggest practical reason
        // SLF4J beats naive string concatenation.
        // ---------------------------------------------------------------
        printSection("2) Parameterized logging with {} placeholders");
        String orderId = "ORD-9001";
        int quantity = 3;

        // BAD -- string concatenation happens EVERY time this line executes,
        // even if debug logging is currently disabled and the result discarded.
        // logger.debug("Processing order " + orderId + " with quantity " + quantity);

        // GOOD -- SLF4J only substitutes placeholders (and calls toString() on
        // each argument) IF the debug level is actually enabled for this logger.
        logger.debug("Processing order {} with quantity {}", orderId, quantity);
        logger.info("Placing order {} with quantity {}", orderId, quantity);
        // Expected substitution: "Placing order ORD-9001 with quantity 3"

        // ---------------------------------------------------------------
        // 3) Logging an exception -- a trailing Throwable argument is automatically
        // treated as the exception to attach (full stack trace), separate from {}.
        // ---------------------------------------------------------------
        printSection("3) Logging an exception with its stack trace");
        try {
            throw new IllegalStateException("Simulated failure while processing order " + orderId);
        } catch (IllegalStateException e) {
            // Exactly one {} placeholder, one non-throwable argument (orderId), and
            // the Throwable passed LAST, unpaired with any placeholder -- the safest
            // pattern to avoid SLF4J's varargs-detection gotcha mentioned in the theory.
            logger.error("Failed to process order {}", orderId, e);
        }

        // ---------------------------------------------------------------
        // 4) MDC -- Mapped Diagnostic Context: per-thread key/value context attached
        // to every log line emitted from that thread, without threading an ID through
        // every single log call manually.
        // ---------------------------------------------------------------
        printSection("4) MDC (Mapped Diagnostic Context) basics");
        simulateHandleRequest("REQ-1234", "user-42");

        // ---------------------------------------------------------------
        // 5) What zero/multiple bindings look like in practice (described, not
        // reproduced live, since that would require deliberately misconfiguring
        // the classpath) -- see the comment block below.
        // ---------------------------------------------------------------
        printSection("5) Binding-related warnings you may see at startup (illustrative only)");
        System.out.println("Zero bindings on the classpath produces (once, to stderr):");
        System.out.println("    SLF4J(W): No SLF4J providers were found.");
        System.out.println("    SLF4J(W): Defaulting to no-operation (NOP) logger implementation");
        System.out.println("    --> log calls compile and run fine, but nothing is ever written anywhere.");
        System.out.println();
        System.out.println("Multiple bindings on the classpath (e.g. logback-classic AND");
        System.out.println("log4j-slf4j2-impl both present via transitive dependencies) produces:");
        System.out.println("    SLF4J(W): Class path contains multiple SLF4J providers.");
        System.out.println("    --> SLF4J picks one (order not guaranteed) -- the fix is always to");
        System.out.println("        EXCLUDE the unwanted binding from the dependency tree, not to ignore it.");

        printSection("All SLF4J facade demos completed (assuming a binding was present).");
    }

    /**
     * Simulates handling one server request -- illustrates the idiomatic MDC
     * put/use/clear pattern. In a real server framework, a servlet filter or
     * interceptor typically does the MDC.put(...) at request entry and the
     * MDC.clear() in a finally block at request exit, so every log line emitted
     * anywhere during that request (on that same thread) automatically carries
     * the requestId/userId context -- e.g. via a pattern containing %X{requestId}.
     */
    private static void simulateHandleRequest(String requestId, String userId) {
        MDC.put("requestId", requestId);
        MDC.put("userId", userId);
        try {
            logger.info("Handling request");
            // Any nested method call's log lines on THIS thread also see the MDC values.
            processOrderWithinRequest();
            System.out.println("MDC value for requestId right now: " + MDC.get("requestId"));
        } finally {
            // CRITICAL: always clear MDC when the request/task ends. MDC is backed by
            // a ThreadLocal -- in a thread-pooled server, forgetting this means the
            // NEXT request that reuses this same pooled thread would incorrectly
            // inherit THIS request's requestId/userId values.
            MDC.clear();
        }
        System.out.println("MDC value for requestId AFTER clear(): " + MDC.get("requestId") + "  (expected: null)");
    }

    private static void processOrderWithinRequest() {
        // This log line, emitted from a completely different method, still carries
        // whatever MDC values are currently set on THIS thread -- that's the whole point.
        logger.info("Processing order within the current request context");
    }
}
