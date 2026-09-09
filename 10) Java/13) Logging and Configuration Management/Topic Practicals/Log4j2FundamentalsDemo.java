/*
 * Log4j2FundamentalsDemo.java
 *
 * ILLUSTRATIVE ONLY -- this file is NOT compilable/runnable as-is. It uses
 * Log4j2's own NATIVE API (org.apache.logging.log4j.LogManager / Logger), which
 * requires, on the classpath:
 *
 *     1. log4j-api   (the API surface)
 *     2. log4j-core  (the actual engine -- without this, nothing gets logged,
 *                     mirroring SLF4J's own facade/binding split, but INSIDE
 *                     the Log4j2 project itself)
 *
 * Example with Maven:
 *     <dependency>
 *         <groupId>org.apache.logging.log4j</groupId>
 *         <artifactId>log4j-api</artifactId>
 *         <version>2.23.1</version>
 *     </dependency>
 *     <dependency>
 *         <groupId>org.apache.logging.log4j</groupId>
 *         <artifactId>log4j-core</artifactId>
 *         <version>2.23.1</version>
 *     </dependency>
 *
 * Or, to compile/run by hand with the jars already downloaded next to this file:
 *     javac -cp "log4j-api-2.23.1.jar;log4j-core-2.23.1.jar" Log4j2FundamentalsDemo.java
 *     java  -cp ".;log4j-api-2.23.1.jar;log4j-core-2.23.1.jar" Log4j2FundamentalsDemo
 * (use ':' instead of ';' as the classpath separator on Linux/macOS)
 *
 * IMPORTANT -- never put log4j-core on the classpath alongside logback-classic
 * or a Log4j2 SLF4J binding (log4j-slf4j2-impl) at the same time as ANOTHER
 * SLF4J binding; only one SLF4J provider should ever be active (see the SLF4J
 * chapter's "multiple bindings" gotcha).
 *
 * ALSO requires a log4j2.xml (shown below as a comment block, since it's a
 * separate config file, not Java) on the classpath root -- auto-detected by
 * filename, same mechanism as Logback's logback.xml.
 *
 * Covers Theory chapter:
 *     10) Java/13) Logging and Configuration Management/Theory/04 Log4j2 Fundamentals.md
 */

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/*
 * =====================================================================
 * ILLUSTRATIVE log4j2.xml -- what this class's logging behavior would
 * actually be shaped by. Save as "log4j2.xml" on the classpath root.
 * Log4j2 also supports .json/.yaml/.properties for the same content --
 * XML is by far the most common in practice, so that's shown here.
 * =====================================================================
 *
 * <?xml version="1.0" encoding="UTF-8"?>
 * <Configuration status="WARN" monitorInterval="30">
 *     <!-- status="WARN" -- controls Log4j2's OWN internal diagnostic chatter
 *          about its startup/config parsing, separate from application output.
 *          monitorInterval="30" -- re-checks this file every 30s and hot-reloads
 *          it without an application restart. -->
 *
 *     <Appenders>
 *         <Console name="Console" target="SYSTEM_OUT">
 *             <PatternLayout pattern="%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n" />
 *         </Console>
 *
 *         <RollingFile name="RollingFile"
 *                       fileName="logs/app.log"
 *                       filePattern="logs/app-%d{yyyy-MM-dd}-%i.log.gz">
 *             <PatternLayout pattern="%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n" />
 *             <Policies>
 *                 <TimeBasedTriggeringPolicy />
 *                 <SizeBasedTriggeringPolicy size="10MB" />
 *             </Policies>
 *             <DefaultRolloverStrategy max="30" />
 *         </RollingFile>
 *     </Appenders>
 *
 *     <Loggers>
 *         <Logger name="com.example.orders" level="DEBUG" additivity="false">
 *             <AppenderRef ref="Console" />
 *             <AppenderRef ref="RollingFile" />
 *         </Logger>
 *
 *         <Logger name="org.hibernate.SQL" level="WARN" />
 *
 *         <Root level="INFO">
 *             <AppenderRef ref="Console" />
 *             <AppenderRef ref="RollingFile" />
 *         </Root>
 *     </Loggers>
 * </Configuration>
 *
 * =====================================================================
 * ILLUSTRATIVE ASYNC LOGGING CONFIG -- fully async, Disruptor-backed loggers
 * (the higher-throughput of Log4j2's two async approaches). Requires the
 * additional "com.lmax:disruptor" dependency, plus telling Log4j2 (via a
 * system property, NOT XML) to use the async context selector for ALL loggers:
 *
 *   -Dlog4j2.contextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector
 *
 * "Mixed" mode -- make ONE logger async while everything else stays synchronous,
 * expressed directly in log4j2.xml via <AsyncLogger> instead of a global switch:
 *
 * <Loggers>
 *     <AsyncLogger name="com.example.highvolume" level="INFO" additivity="false">
 *         <AppenderRef ref="RollingFile" />
 *     </AsyncLogger>
 *     <Root level="INFO">
 *         <AppenderRef ref="Console" />
 *     </Root>
 * </Loggers>
 *
 * Gotcha: enabling Async Loggers WITHOUT adding the disruptor dependency does
 * NOT fail loudly -- Log4j2 silently falls back to synchronous behavior with
 * just a startup warning, which is easy to miss if you expect the throughput
 * benefit and don't see it.
 * =====================================================================
 */

public class Log4j2FundamentalsDemo {

    // Native Log4j2 API: LogManager.getLogger(Class) -- deliberately mirrors
    // SLF4J's LoggerFactory.getLogger(Class) closely, both in signature and in
    // method names (info/debug/warn/error) and {} placeholder style, by design.
    private static final Logger logger = LogManager.getLogger(Log4j2FundamentalsDemo.class);

    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }

    public static void main(String[] args) {

        // ---------------------------------------------------------------
        // 1) Basic logging -- governed by <Root level="INFO"> in the log4j2.xml
        // shown above, appearing on both the Console and RollingFile appenders.
        // ---------------------------------------------------------------
        printSection("1) Root-level logging (INFO threshold, per log4j2.xml's <Root>)");
        logger.info("Application starting up");
        logger.warn("Disk usage nearing configured threshold");
        logger.error("Unhandled condition encountered during startup checks");
        logger.debug("This DEBUG line does NOT appear -- root's level is INFO, not DEBUG");

        // ---------------------------------------------------------------
        // 2) Parameterized ({}) logging -- same placeholder style as SLF4J
        // ---------------------------------------------------------------
        printSection("2) Parameterized logging with {} placeholders (same style as SLF4J)");
        String orderId = "ORD-9001";
        logger.info("Placing order {} with quantity {}", orderId, 3);

        // ---------------------------------------------------------------
        // 3) Exception logging -- trailing Throwable argument attaches the full
        // stack trace, same convention as SLF4J.
        // ---------------------------------------------------------------
        printSection("3) Logging an exception with its stack trace");
        try {
            throw new IllegalStateException("Simulated failure while processing order " + orderId);
        } catch (IllegalStateException e) {
            logger.error("Failed to process order {}", orderId, e);
        }

        // ---------------------------------------------------------------
        // 4) Per-package override + async logging, described (illustrated via the
        // comment block above, not directly reproducible without the actual config
        // files and disruptor dependency present).
        // ---------------------------------------------------------------
        printSection("4) Per-package overrides and async logging (illustrated above)");
        System.out.println("A logger named com.example.orders.OrderService would print DEBUG and route");
        System.out.println("ONLY to its own appenders (additivity=\"false\" stops bubbling to Root),");
        System.out.println("per the <Logger name=\"com.example.orders\" level=\"DEBUG\" additivity=\"false\">");
        System.out.println("entry in the illustrative log4j2.xml above.");
        System.out.println();
        System.out.println("With the disruptor dependency + AsyncLoggerContextSelector system property set,");
        System.out.println("or an <AsyncLogger> entry for one specific logger (\"mixed\" mode), the actual");
        System.out.println("logging call becomes non-blocking, backed by the LMAX Disruptor lock-free queue --");
        System.out.println("meaningfully higher throughput than Logback's AsyncAppender approach in most benchmarks.");

        printSection("All Log4j2 fundamentals demos completed (assuming log4j-api + log4j-core present).");
    }
}
