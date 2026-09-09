/*
 * Topic02_JfrCustomEventsDemo.java
 *
 * NOTE ON FILENAME: the public class is named "Topic02_JfrCustomEventsDemo" (Java
 * identifiers can't start with a digit) while the FILE keeps the "02_..." numeric
 * prefix used throughout this repo for ordering.
 *
 * Compile: javac 02_JfrCustomEventsDemo.java
 * Run (no recording flags -- events still fire, just aren't captured anywhere):
 *          java Topic02_JfrCustomEventsDemo
 *
 * Run WITH a recording so the custom events actually get written to a .jfr file
 * (recommended -- see Theory File 02, "Starting a Recording"):
 *          java -XX:StartFlightRecording=filename=demo.jfr,settings=profile Topic02_JfrCustomEventsDemo
 *
 * Then open demo.jfr in JDK Mission Control (JMC) and look for "Order Processed" and
 * "Batch Processed" events under the custom event tree -- they sit on the SAME unified
 * timeline as built-in GC/thread/compilation events, which is JFR's whole point.
 *
 * Attaching to an ALREADY-RUNNING process instead of a startup flag (jcmd):
 *          jcmd <pid> JFR.start name=diag duration=120s filename=diag.jfr settings=profile
 *          jcmd <pid> JFR.check
 *          jcmd <pid> JFR.dump name=diag filename=snapshot.jfr
 *          jcmd <pid> JFR.stop name=diag
 *
 * Demonstrates:
 *   1. Defining a custom JFR event by extending jdk.jfr.Event, with @Label/@Description
 *   2. event.begin()/event.commit() around a timed unit of work (duration captured automatically)
 *   3. A simple field-only event committed without begin() (an instantaneous event)
 *   4. Checking Event.isEnabled() to show the "disabled events cost almost nothing" claim
 *      from Theory File 02 is a real, checkable API, not just documentation
 *   5. Comments on -XX:StartFlightRecording and jcmd JFR.* commands (external tool usage)
 *
 * Covers Theory chapter:
 *   07) JVM Internals and Memory Management/Theory/11 Java Flight Recorder JFR and JDK Mission Control.md
 */

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;

import java.util.concurrent.ThreadLocalRandom;

public class Topic02_JfrCustomEventsDemo {

    public static void main(String[] args) throws Exception {
        printSection("1) Defining Custom JFR Events (jdk.jfr.Event)");
        System.out.println("Two custom event types defined below this method: OrderProcessedEvent");
        System.out.println("(duration-based, via begin()/commit()) and BatchProcessedEvent (instant,");
        System.out.println("field values only, committed with no begin()).");

        printSection("2) Checking Whether an Event Type Is Enabled");
        // isEnabled() reflects whether the CURRENT recording configuration (if any) has
        // this event type turned on. With no -XX:StartFlightRecording flag at all, JFR's
        // event subsystem is still present but no recording is active, so this is commonly
        // false -- and per Theory File 02, that's exactly when commit() is cheapest: a
        // quick check-and-return with no actual event construction/writing cost.
        OrderProcessedEvent probe = new OrderProcessedEvent();
        System.out.println("OrderProcessedEvent.isEnabled() = " + probe.isEnabled());
        System.out.println("(Run with -XX:StartFlightRecording=... to make this true and actually capture data.)");

        printSection("3) Emitting Duration-Based Custom Events (begin/commit)");
        for (int i = 1; i <= 5; i++) {
            processOrder("order-" + i, ThreadLocalRandom.current().nextInt(1, 10));
        }
        System.out.println("Committed 5 OrderProcessedEvent instances (visible in JMC if a recording was active).");

        printSection("4) Emitting Instant Field-Only Custom Events");
        for (int i = 1; i <= 3; i++) {
            recordBatchProcessed(100 * i, ThreadLocalRandom.current().nextInt(0, 5));
        }
        System.out.println("Committed 3 BatchProcessedEvent instances.");

        printSection("5) Why Disabled Events Are (Almost) Free");
        explainCheapDisabledEvents();

        printJcmdAndStartupFlagNotes();

        System.out.println("\nAll JFR custom-events demos completed.");
        System.out.println("If this run used -XX:StartFlightRecording=filename=demo.jfr, open demo.jfr in");
        System.out.println("JDK Mission Control now to see these events on the timeline.");
    }

    // -------------------------------------------------------------------
    // Custom event #1 -- duration-based (begin/commit), mirrors Theory File 02's
    // "Order Processed" example almost exactly.
    // -------------------------------------------------------------------
    @Name("com.study.jvmtools.OrderProcessed")
    @Label("Order Processed")
    @Description("Time taken to fully process a customer order")
    @Category({"Demo", "Orders"})
    public static class OrderProcessedEvent extends Event {
        @Label("Order ID")
        String orderId;

        @Label("Item Count")
        int itemCount;
    }

    private static void processOrder(String orderId, int itemCount) throws InterruptedException {
        OrderProcessedEvent event = new OrderProcessedEvent();
        event.begin(); // starts the event's internal timestamp/duration clock
        try {
            // Simulate real processing work with a small variable delay so different
            // orders show genuinely different durations when viewed in JMC.
            Thread.sleep(ThreadLocalRandom.current().nextInt(5, 20));
            event.orderId = orderId;
            event.itemCount = itemCount;
        } finally {
            // commit() is a cheap check-and-return if this event type isn't enabled in
            // the current recording (or if there's no recording at all) -- safe to leave
            // in production code permanently, per Theory File 02.
            event.commit();
        }
    }

    // -------------------------------------------------------------------
    // Custom event #2 -- instantaneous, field values only, no begin() call.
    // Useful for "this happened" events where duration isn't meaningful --
    // e.g. "a batch of N records finished, M of them failed".
    // -------------------------------------------------------------------
    @Name("com.study.jvmtools.BatchProcessed")
    @Label("Batch Processed")
    @Description("A batch finished processing; records the batch size and failure count")
    @Category({"Demo", "Batches"})
    public static class BatchProcessedEvent extends Event {
        @Label("Record Count")
        int recordCount;

        @Label("Failure Count")
        int failureCount;
    }

    private static void recordBatchProcessed(int recordCount, int failureCount) {
        BatchProcessedEvent event = new BatchProcessedEvent();
        // No begin() here -- when an event is committed without begin(), JFR timestamps
        // it at commit() time as an instant event rather than a duration event.
        event.recordCount = recordCount;
        event.failureCount = failureCount;
        event.commit();
    }

    // -------------------------------------------------------------------
    private static void explainCheapDisabledEvents() {
        System.out.println("jdk.jfr.Event.commit() on a DISABLED event type is documented (Theory File 02)");
        System.out.println("as a cheap check-and-return -- no stack trace capture, no serialization, no I/O.");
        System.out.println("This is why application code can leave custom JFR instrumentation compiled in");
        System.out.println("permanently, unlike a typical logging/metrics call that a team might feel");
        System.out.println("pressure to strip out of hot paths for fear of overhead.");
        System.out.println();
        System.out.println("Contrast with JMX polling (Theory File 01): JMX is pull-based, so a monitoring");
        System.out.println("agent's poll interval trades overhead against resolution directly. JFR events");
        System.out.println("are push-based and near-zero-cost when disabled, and full-fidelity (with a");
        System.out.println("threshold, where relevant) when enabled -- a materially different cost model.");
    }

    private static void printJcmdAndStartupFlagNotes() {
        printSection("6) Starting Recordings -- Startup Flags and jcmd (Notes)");

        // At JVM startup:
        //   -XX:StartFlightRecording=duration=60s,filename=myapp.jfr
        //   -XX:StartFlightRecording=disk=true,maxsize=250MB,maxage=1h,filename=recording.jfr,settings=default
        System.out.println("Startup flag (time-bounded, one-shot):");
        System.out.println("  -XX:StartFlightRecording=duration=60s,filename=myapp.jfr");
        System.out.println("Startup flag (continuous, ring-buffer style -- practical for always-on prod):");
        System.out.println("  -XX:StartFlightRecording=disk=true,maxsize=250MB,maxage=1h,filename=recording.jfr,settings=default");

        // Attaching to an already-running JVM without a restart:
        //   jcmd <pid> JFR.start name=diag duration=120s filename=diag.jfr settings=profile
        //   jcmd <pid> JFR.check
        //   jcmd <pid> JFR.dump name=diag filename=snapshot.jfr
        //   jcmd <pid> JFR.stop name=diag
        System.out.println("\njcmd against a LIVE process (no restart needed -- preferred for prod incidents):");
        System.out.println("  jcmd <pid> JFR.start name=diag duration=120s filename=diag.jfr settings=profile");
        System.out.println("  jcmd <pid> JFR.check");
        System.out.println("  jcmd <pid> JFR.dump name=diag filename=snapshot.jfr    (dumps without stopping)");
        System.out.println("  jcmd <pid> JFR.stop name=diag");

        System.out.println("\nsettings=default (~0.1-1% overhead, always-on friendly) vs settings=profile");
        System.out.println("(~1-3% overhead, finer sampling/more stack traces, deliberate short session) --");
        System.out.println("see Theory File 02, \"Recording Levels\".");
    }

    // -------------------------------------------------------------------
    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }
}
