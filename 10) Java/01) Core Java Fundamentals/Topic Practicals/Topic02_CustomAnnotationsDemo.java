/*
 * Topic02_CustomAnnotationsDemo.java
 *
 * NOTE ON FILENAME: the public class is named "Topic02_CustomAnnotationsDemo" (Java
 * identifiers can't start with a digit) while the FILE keeps the "02_..." numeric prefix
 * used throughout this repo for ordering.
 *
 * Compile: javac 02_CustomAnnotationsDemo.java
 * Run:     java Topic02_CustomAnnotationsDemo
 *
 * Demonstrates:
 *   1. A MARKER annotation (@Reviewed) -- no elements, presence/absence IS the signal
 *   2. A SINGLE-VALUE annotation (@Label) -- one value() element, uses shorthand syntax
 *   3. A FULL (multi-element) annotation (@Scheduled) -- several named elements with defaults
 *   4. Meta-annotations in action: @Retention, @Target, @Documented, @Inherited
 *   5. @Repeatable -- applying the same annotation multiple times to one element
 *
 * Covers Theory chapter:
 *   01) Core Java Fundamentals/Theory/09 Creating Custom Annotations.md
 */

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

public class Topic02_CustomAnnotationsDemo {

    public static void main(String[] args) throws Exception {
        demoMarkerAnnotation();
        demoSingleValueAnnotation();
        demoFullAnnotation();
        demoInheritedMetaAnnotation();
        demoRepeatableAnnotation();
        System.out.println("\nAll custom-annotations demos completed.");
    }

    // -------------------------------------------------------------------
    // 1) Marker annotation -- no elements at all
    // -------------------------------------------------------------------
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Documented
    @interface Reviewed {
        // No elements -- presence or absence of @Reviewed IS the entire signal, exactly like
        // JUnit's @Test / @Before. Queried purely with isAnnotationPresent().
    }

    @Reviewed
    static class Invoice {
        @Reviewed
        void submit() {
            System.out.println("  (invoice submitted)");
        }

        void draft() {
            System.out.println("  (invoice drafted -- not reviewed yet)");
        }
    }

    private static void demoMarkerAnnotation() throws Exception {
        printSection("1) Marker Annotation -- @Reviewed");

        Class<Invoice> cls = Invoice.class;
        System.out.println("Invoice class isAnnotationPresent(Reviewed.class) = "
                + cls.isAnnotationPresent(Reviewed.class));

        for (Method m : cls.getDeclaredMethods()) {
            System.out.println("  " + m.getName() + "() reviewed? " + m.isAnnotationPresent(Reviewed.class));
        }
    }

    // -------------------------------------------------------------------
    // 2) Single-value annotation -- one value() element, shorthand syntax
    // -------------------------------------------------------------------
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface Label {
        String value(); // named exactly "value" -- enables the @Label("...") shorthand
    }

    @Label("BillingModule")
    static class BillingService {
    }

    private static void demoSingleValueAnnotation() {
        printSection("2) Single-Value Annotation -- @Label");

        Label label = BillingService.class.getAnnotation(Label.class);
        System.out.println("BillingService is labeled: " + (label != null ? label.value() : "null"));
        System.out.println("Applied via shorthand @Label(\"BillingModule\") instead of @Label(value = \"...\"),");
        System.out.println("legal ONLY because the sole element is named exactly \"value\".");
    }

    // -------------------------------------------------------------------
    // 3) Full (multi-element) annotation, with defaults
    // -------------------------------------------------------------------
    enum TimeUnitLike { MILLISECONDS, SECONDS, MINUTES }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Scheduled {
        String cron() default "";
        long fixedDelayMs() default -1;
        long initialDelayMs() default 0;
        TimeUnitLike unit() default TimeUnitLike.MILLISECONDS;
    }

    static class JobRunner {
        @Scheduled(cron = "0 0 * * * *")
        void hourlyJob() {
            System.out.println("  (hourly job ran)");
        }

        @Scheduled(fixedDelayMs = 5000, initialDelayMs = 1000)
        void pollJob() {
            System.out.println("  (poll job ran)");
        }

        void unscheduledJob() {
            System.out.println("  (this one has no @Scheduled at all)");
        }
    }

    private static void demoFullAnnotation() throws Exception {
        printSection("3) Full Multi-Element Annotation -- @Scheduled");

        for (Method m : JobRunner.class.getDeclaredMethods()) {
            Scheduled s = m.getAnnotation(Scheduled.class);
            if (s != null) {
                System.out.println(m.getName() + "() -> cron=\"" + s.cron() + "\", fixedDelayMs=" + s.fixedDelayMs()
                        + ", initialDelayMs=" + s.initialDelayMs() + ", unit=" + s.unit());
            } else {
                System.out.println(m.getName() + "() -> no @Scheduled");
            }
        }
        System.out.println("\nNote hourlyJob() only specified cron -- fixedDelayMs/initialDelayMs/unit all");
        System.out.println("fell back to their declared defaults (-1, 0, MILLISECONDS), the same");
        System.out.println("'convention over configuration' style used by Spring/JPA-style annotations.");
    }

    // -------------------------------------------------------------------
    // 4) @Inherited meta-annotation
    // -------------------------------------------------------------------
    @Inherited
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface Secured {
        String role() default "USER";
    }

    @Secured(role = "ADMIN")
    static class BaseController {
    }

    static class DerivedController extends BaseController {
        // No @Secured written here directly -- but @Inherited propagates it from BaseController.
    }

    private static void demoInheritedMetaAnnotation() {
        printSection("4) @Inherited Meta-Annotation");

        Secured onBase = BaseController.class.getAnnotation(Secured.class);
        Secured onDerived = DerivedController.class.getAnnotation(Secured.class);

        System.out.println("BaseController.getAnnotation(Secured.class)    = "
                + (onBase != null ? "role=" + onBase.role() : "null"));
        System.out.println("DerivedController.getAnnotation(Secured.class) = "
                + (onDerived != null ? "role=" + onDerived.role() : "null")
                + "  (inherited via extends, thanks to @Inherited)");

        System.out.println("\nCaveat from Theory File 02: @Inherited affects ONLY TYPE-level annotations");
        System.out.println("through 'extends' -- it has NO effect on method/field annotations, and does");
        System.out.println("NOT propagate through interface implementation.");
    }

    // -------------------------------------------------------------------
    // 5) @Repeatable
    // -------------------------------------------------------------------
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Schedules {
        Schedule[] value(); // the container -- holds an array of the repeated annotation
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Repeatable(Schedules.class) // points to its container annotation above
    @interface Schedule {
        String day();
    }

    static class ReportJobs {
        @Schedule(day = "Monday")
        @Schedule(day = "Friday")
        void weeklyReport() {
            System.out.println("  (weekly report ran)");
        }
    }

    private static void demoRepeatableAnnotation() throws Exception {
        printSection("5) @Repeatable Annotation -- @Schedule x2");

        Method m = ReportJobs.class.getDeclaredMethod("weeklyReport");

        // Plain getAnnotation() would return null here -- the compiler packed the two @Schedule
        // usages into an implicit @Schedules container, which getAnnotation(Schedule.class)
        // does NOT unwrap.
        Schedule single = m.getAnnotation(Schedule.class);
        System.out.println("getAnnotation(Schedule.class)         = " + single
                + "  (null -- doesn't unwrap the implicit container)");

        // getAnnotationsByType IS repeatable-aware and unwraps the container automatically.
        Schedule[] all = m.getAnnotationsByType(Schedule.class);
        System.out.println("getAnnotationsByType(Schedule.class)  = " + all.length + " found:");
        for (Schedule s : all) {
            System.out.println("  day = " + s.day());
        }

        // The implicit container is also directly queryable.
        Schedules container = m.getAnnotation(Schedules.class);
        System.out.println("getAnnotation(Schedules.class).value().length = " + container.value().length);
    }

    // -------------------------------------------------------------------
    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }
}
