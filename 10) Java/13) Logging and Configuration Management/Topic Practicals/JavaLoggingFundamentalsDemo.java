/*
 * JavaLoggingFundamentalsDemo.java
 *
 * Demonstrates, fully runnable with ZERO external dependencies (java.util.logging
 * ships in the JDK itself):
 *     1. Why System.out.println doesn't scale -- a quick contrast, printed for comparison
 *     2. Getting a Logger per class via Logger.getLogger(ClassName.class.getName())
 *     3. All the standard JUL levels, and which ones print by default
 *     4. Changing a logger's level programmatically and observing the effect
 *     5. Parameterized logging with {0}/{1} placeholders (JUL's equivalent of SLF4J's {})
 *     6. The "expensive message" gotcha, and guarding with isLoggable(...)
 *     7. Attaching a custom ConsoleHandler + Formatter, and disabling the default
 *        parent handler to avoid double-printed output
 *     8. Logging an exception WITH its stack trace via logger.log(Level, msg, throwable)
 *     9. A FileHandler writing rotated log files to disk (created under ./logs)
 *    10. The hierarchical logger-name -> level resolution walk (child inherits from parent)
 *
 * Covers Theory chapter:
 *     10) Java/13) Logging and Configuration Management/Theory/01 Logging Fundamentals and java.util.logging.md
 *
 * Compile: javac JavaLoggingFundamentalsDemo.java
 * Run:     java JavaLoggingFundamentalsDemo
 *
 * After running, check the ./logs directory next to wherever you ran `java` from --
 * it will contain a rotated file named demo0.log (or demo1.log, etc.) with the
 * FileHandler's output, in addition to everything printed to the console.
 */

import java.io.IOException;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class JavaLoggingFundamentalsDemo {

    // One Logger per class, named after the fully-qualified class name -- this is
    // the convention that makes per-package/per-class level configuration possible.
    private static final Logger logger =
            Logger.getLogger(JavaLoggingFundamentalsDemo.class.getName());

    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }

    public static void main(String[] args) throws IOException {

        // ---------------------------------------------------------------
        // 1) Why System.out.println doesn't scale -- quick illustration
        // ---------------------------------------------------------------
        printSection("1) System.out.println vs a real Logger");
        System.out.println("[println] Order 123 placed"); // no level, no timestamp, no source class
        // Compare against a proper logger call further below -- notice the automatic
        // timestamp, level, and calling class/method that come for free.

        // ---------------------------------------------------------------
        // 2) + 3) Standard JUL levels -- note the default root logger level is
        // INFO, so FINE/FINER/FINEST are silently skipped unless we raise the level.
        // ---------------------------------------------------------------
        printSection("2) & 3) Logger levels -- default behavior (root level = INFO)");
        logger.severe("SEVERE: something seriously failed");   // prints (>= INFO)
        logger.warning("WARNING: something looks suspicious"); // prints (>= INFO)
        logger.info("INFO: normal application milestone");     // prints (== INFO)
        logger.config("CONFIG: a configuration-related detail"); // does NOT print by default
        logger.fine("FINE: developer debug detail");            // does NOT print by default
        logger.finer("FINER: more detailed debug");             // does NOT print by default
        logger.finest("FINEST: extremely fine-grained trace");  // does NOT print by default
        System.out.println("(Expected: only SEVERE/WARNING/INFO lines appeared above this line)");

        // ---------------------------------------------------------------
        // 4) Raising the logger's level programmatically to see FINE appear
        // ---------------------------------------------------------------
        printSection("4) Raising level to FINE and re-attaching a matching handler");
        logger.setLevel(Level.FINE);
        // IMPORTANT: the Logger's own level is only HALF the gate -- the attached
        // Handler(s) also have their own threshold. The default console handler on
        // the root logger defaults to Level.INFO, so without adjusting a handler too,
        // FINE messages would still be swallowed at the handler stage. We attach our
        // own ConsoleHandler below (section 7) configured down to FINE explicitly.
        logger.setUseParentHandlers(false); // stop double-printing via the default root handler

        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(Level.FINE);
        consoleHandler.setFormatter(new SimpleFormatter());
        logger.addHandler(consoleHandler);

        logger.fine("FINE: now visible because both logger AND handler allow it");
        System.out.println("(Expected: the FINE line above DID print, unlike section 2/3)");

        // ---------------------------------------------------------------
        // 5) Parameterized logging -- {0}, {1} placeholders (java.text.MessageFormat style)
        // ---------------------------------------------------------------
        printSection("5) Parameterized logging with {0}/{1} placeholders");
        String orderId = "ORD-9001";
        int quantity = 3;
        logger.log(Level.INFO, "Placing order {0} with quantity {1}", new Object[] { orderId, quantity });
        // Expected: "Placing order ORD-9001 with quantity 3" (substitution happens
        // internally -- and only if the level actually passes the enabled check)

        // ---------------------------------------------------------------
        // 6) The "expensive message" gotcha -- guard with isLoggable(...)
        // ---------------------------------------------------------------
        printSection("6) Guarding expensive message construction with isLoggable(...)");
        logger.setLevel(Level.INFO); // drop back down so FINEST below is disabled again
        if (logger.isLoggable(Level.FINEST)) {
            // This branch will NOT execute now (FINEST is disabled), so
            // expensiveComputation() below never actually runs -- demonstrating the
            // pattern that avoids paying for message-building work nobody will see.
            String expensive = expensiveComputation();
            logger.finest("Expensive detail: " + expensive);
        }
        System.out.println("(Expected: expensiveComputation() was NOT called just now -- "
                + "see the counter below)");
        System.out.println("expensiveComputation() call count so far: " + expensiveCallCount);

        // ---------------------------------------------------------------
        // 8) Logging an exception with its stack trace
        // ---------------------------------------------------------------
        printSection("8) Logging an exception WITH its stack trace");
        try {
            throw new IllegalStateException("Simulated failure while processing order " + orderId);
        } catch (IllegalStateException e) {
            // Passing the Throwable as the third argument makes the framework print
            // the FULL stack trace as part of the structured log record -- this is
            // strictly better than e.printStackTrace(), which bypasses logging
            // configuration entirely and just writes raw text to System.err.
            logger.log(Level.SEVERE, "Failed to place order " + orderId, e);
        }

        // ---------------------------------------------------------------
        // 9) FileHandler -- writing rotated log files to disk
        // ---------------------------------------------------------------
        printSection("9) FileHandler writing to ./logs (rotating, up to 5 files, ~50KB each)");
        java.io.File logsDir = new java.io.File("logs");
        if (!logsDir.exists()) {
            logsDir.mkdirs();
        }
        FileHandler fileHandler = new FileHandler("logs/demo%g.log", 50_000, 5, true);
        fileHandler.setFormatter(new SimpleFormatter());
        fileHandler.setLevel(Level.ALL);
        logger.addHandler(fileHandler);
        logger.info("This INFO message is written to BOTH the console AND logs/demo0.log");
        fileHandler.flush();
        System.out.println("Check the ./logs directory next to this program for demo0.log");

        // ---------------------------------------------------------------
        // 10) Hierarchical logger-name -> level resolution, illustrated with a child logger
        // ---------------------------------------------------------------
        printSection("10) Hierarchical level resolution (child inherits from parent/root)");
        Logger childLogger = Logger.getLogger(
                JavaLoggingFundamentalsDemo.class.getName() + ".childComponent");
        // childLogger has no explicit level set -- java.util.logging does not expose
        // a public getEffectiveLevel() (unlike Logback/Log4j2's equivalents), so we
        // walk the parent chain manually here to show the SAME resolution mechanism
        // that isLoggable(...) uses internally: this class's logger (set to INFO
        // above) is the nearest ancestor with an explicit level, so that's what
        // effectively governs childLogger too.
        System.out.println("Parent logger level          : " + logger.getLevel());
        System.out.println("Child logger's OWN level      : " + childLogger.getLevel() + "  (null = inherited)");
        Logger walker = childLogger;
        Level effective = null;
        while (walker != null) {
            if (walker.getLevel() != null) {
                effective = walker.getLevel();
                break;
            }
            walker = walker.getParent();
        }
        System.out.println("Child logger EFFECTIVE level  : " + effective
                + "  (resolved by walking up the hierarchy to the nearest ancestor with a level set)");
        System.out.println("Child logger isLoggable(INFO) : " + childLogger.isLoggable(Level.INFO)
                + "  (isLoggable() performs this same walk internally)");

        printSection("All java.util.logging fundamentals demos completed.");
    }

    // Counts how many times expensiveComputation() actually runs, purely to prove
    // (in section 6 above) that it is NOT invoked when the guarding isLoggable check fails.
    private static int expensiveCallCount = 0;

    private static String expensiveComputation() {
        expensiveCallCount++;
        // Simulates a non-trivial computation someone might be tempted to inline
        // directly into a log statement's string concatenation.
        return "computed-value-" + (42 * expensiveCallCount);
    }
}
