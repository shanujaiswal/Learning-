/*
 * LogbackConfigurationDemo.java
 *
 * ILLUSTRATIVE ONLY -- this file is NOT compilable/runnable as-is. It uses the
 * SLF4J API (org.slf4j.Logger), and for that logging to actually be captured,
 * formatted, and routed anywhere, it needs Logback -- SLF4J's "native"
 * implementation -- on the classpath, which requires:
 *
 *     1. logback-classic  (transitively pulls in logback-core + slf4j-api)
 *
 * Example with Maven:
 *     <dependency>
 *         <groupId>ch.qos.logback</groupId>
 *         <artifactId>logback-classic</artifactId>
 *         <version>1.5.6</version>
 *     </dependency>
 *
 * Or, to compile/run by hand with the jars already downloaded next to this file:
 *     javac -cp "logback-classic-1.5.6.jar;logback-core-1.5.6.jar;slf4j-api-2.0.13.jar" LogbackConfigurationDemo.java
 *     java  -cp ".;logback-classic-1.5.6.jar;logback-core-1.5.6.jar;slf4j-api-2.0.13.jar" LogbackConfigurationDemo
 * (use ':' instead of ';' as the classpath separator on Linux/macOS)
 *
 * ALSO requires a logback.xml (shown below as a comment block, since it's a
 * separate XML file, not Java) placed at the CLASSPATH ROOT, e.g.
 * src/main/resources/logback.xml in a Maven/Gradle project -- Logback finds it
 * there automatically at startup, no manual wiring or system property needed.
 *
 * Covers Theory chapter:
 *     10) Java/13) Logging and Configuration Management/Theory/03 Logback Configuration and Usage.md
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * =====================================================================
 * ILLUSTRATIVE logback.xml -- this is what the above class's logging
 * behavior would actually be shaped by. Save this as its own file named
 * exactly "logback.xml" on the classpath root; it is NOT parsed by javac,
 * it's shown here purely so this demo is self-contained to read.
 * =====================================================================
 *
 * <?xml version="1.0" encoding="UTF-8"?>
 * <configuration>
 *
 *     <!-- Console appender -- human-readable output for local development -->
 *     <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
 *         <encoder>
 *             <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
 *         </encoder>
 *     </appender>
 *
 *     <!-- Rolling file appender -- writes to disk, rotates daily AND by size,
 *          keeps at most 30 days of history, caps total disk usage at 1GB -->
 *     <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
 *         <file>logs/app.log</file>
 *         <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
 *             <fileNamePattern>logs/app.%d{yyyy-MM-dd}.%i.log.gz</fileNamePattern>
 *             <maxFileSize>10MB</maxFileSize>
 *             <maxHistory>30</maxHistory>
 *             <totalSizeCap>1GB</totalSizeCap>
 *         </rollingPolicy>
 *         <encoder>
 *             <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} [%X{requestId}] - %msg%n</pattern>
 *         </encoder>
 *     </appender>
 *
 *     <!-- Optional: wrap the file appender so its I/O happens on a background
 *          thread instead of blocking the calling application thread -->
 *     <appender name="ASYNC_FILE" class="ch.qos.logback.classic.AsyncAppender">
 *         <queueSize>512</queueSize>
 *         <discardingThreshold>0</discardingThreshold> <!-- 0 = never drop events under pressure -->
 *         <includeCallerData>false</includeCallerData>  <!-- true is expensive; leave off by default -->
 *         <appender-ref ref="FILE" />
 *     </appender>
 *
 *     <!-- Root logger -- the default level + appenders for anything not overridden below -->
 *     <root level="INFO">
 *         <appender-ref ref="CONSOLE" />
 *         <appender-ref ref="ASYNC_FILE" />
 *     </root>
 *
 *     <!-- Per-package override -- see this one package's DEBUG detail without
 *          turning DEBUG on globally (which would flood the log with noise) -->
 *     <logger name="com.example.orders" level="DEBUG" />
 *
 *     <!-- Quiet down an extremely chatty third-party library -->
 *     <logger name="org.hibernate.SQL" level="WARN" />
 *
 * </configuration>
 *
 * =====================================================================
 */

public class LogbackConfigurationDemo {

    // Note: this is a plain org.slf4j.Logger -- the application code never
    // imports anything from ch.qos.logback directly. Logback is wired in purely
    // by being present on the classpath plus the logback.xml file above; that's
    // the whole point of SLF4J's facade/binding split covered in the previous chapter.
    private static final Logger logger = LoggerFactory.getLogger(LogbackConfigurationDemo.class);

    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }

    public static void main(String[] args) {

        // ---------------------------------------------------------------
        // 1) Root-level logging -- governed by <root level="INFO"> in the
        // logback.xml shown above: appears on BOTH the CONSOLE appender and the
        // ASYNC_FILE-wrapped rolling FILE appender, in the pattern each specifies.
        // ---------------------------------------------------------------
        printSection("1) Root-level logging (INFO threshold, per logback.xml's <root>)");
        logger.info("Application starting up");
        logger.warn("Disk usage nearing configured threshold");
        logger.error("Unhandled condition encountered during startup checks");
        logger.debug("This DEBUG line does NOT appear -- root's level is INFO, not DEBUG");

        // ---------------------------------------------------------------
        // 2) Per-package override -- a logger named "com.example.orders.*" (or
        // exactly that name) would print DEBUG per the <logger name="com.example.orders"
        // level="DEBUG" /> override, while THIS class's logger (whatever package
        // it's actually in) still follows root's INFO threshold unless it happens
        // to match that override too.
        // ---------------------------------------------------------------
        printSection("2) Per-package level overrides (illustrated, not directly reproducible here)");
        System.out.println("A logger named com.example.orders.OrderService would show DEBUG output");
        System.out.println("(because of the <logger name=\"com.example.orders\" level=\"DEBUG\" /> override),");
        System.out.println("while com.example.billing.InvoiceService would still follow root's INFO level.");

        // ---------------------------------------------------------------
        // 3) Parameterized logging + exception logging -- identical SLF4J API
        // usage as the previous chapter; Logback is simply what's executing
        // underneath now, per the binding on the classpath.
        // ---------------------------------------------------------------
        printSection("3) Parameterized logging and exception logging (same SLF4J API as always)");
        String orderId = "ORD-9001";
        logger.info("Placing order {} with quantity {}", orderId, 3);
        try {
            throw new IllegalStateException("Simulated failure while processing order " + orderId);
        } catch (IllegalStateException e) {
            logger.error("Failed to process order {}", orderId, e);
        }

        // ---------------------------------------------------------------
        // 4) What the rolling file appender is doing behind the scenes
        // ---------------------------------------------------------------
        printSection("4) RollingFileAppender behavior (per the config above)");
        System.out.println("Every INFO+ line above is ALSO written to logs/app.log (via ASYNC_FILE -> FILE).");
        System.out.println("That file rotates when it exceeds 10MB OR at the next day boundary (whichever");
        System.out.println("comes first), compressing old files to logs/app.<date>.<index>.log.gz, retaining");
        System.out.println("at most 30 days of history and never exceeding 1GB of total rotated log size.");

        printSection("All Logback configuration demos completed (assuming logback-classic + logback.xml present).");
    }
}
