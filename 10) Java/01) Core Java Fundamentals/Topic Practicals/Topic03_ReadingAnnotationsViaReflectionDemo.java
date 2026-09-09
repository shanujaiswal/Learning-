/*
 * Topic03_ReadingAnnotationsViaReflectionDemo.java
 *
 * NOTE ON FILENAME: the public class is named "Topic03_ReadingAnnotationsViaReflectionDemo"
 * (Java identifiers can't start with a digit) while the FILE keeps the "03_..." numeric
 * prefix used throughout this repo for ordering.
 *
 * Compile: javac 03_ReadingAnnotationsViaReflectionDemo.java
 * Run:     java Topic03_ReadingAnnotationsViaReflectionDemo
 *
 * Demonstrates:
 *   1. A small, genuinely working test-runner-like framework (MiniRunner) that scans a class
 *      for @MyTest / @MyBeforeEach methods and invokes them, unwrapping InvocationTargetException
 *   2. isAnnotationPresent() / getAnnotation() basics
 *   3. getAnnotations() vs getDeclaredAnnotations()
 *   4. getAnnotationsByType() for a @Repeatable annotation
 *   5. Reading element values, including a Class<?>-typed default element
 *
 * Covers Theory chapter:
 *   01) Core Java Fundamentals/Theory/10 Reading Annotations via Reflection.md
 */

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Topic03_ReadingAnnotationsViaReflectionDemo {

    public static void main(String[] args) throws Exception {
        demoIsAnnotationPresentAndGetAnnotation();
        demoGetAnnotationsVsGetDeclaredAnnotations();
        demoRepeatableViaGetAnnotationsByType();
        demoReadingElementValuesIncludingClassElement();
        demoMiniTestRunner();
        System.out.println("\nAll reading-annotations-via-reflection demos completed.");
    }

    // -------------------------------------------------------------------
    // 1) isAnnotationPresent / getAnnotation basics
    // -------------------------------------------------------------------
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Test {
        String name() default "";
    }

    static class Calc {
        @Test(name = "addition works")
        public void testAdd() {
        }

        public void notATest() {
        }
    }

    private static void demoIsAnnotationPresentAndGetAnnotation() throws Exception {
        printSection("1) isAnnotationPresent / getAnnotation");

        Method m = Calc.class.getDeclaredMethod("testAdd");
        if (m.isAnnotationPresent(Test.class)) {
            Test t = m.getAnnotation(Test.class);
            System.out.println("testAdd() is a @Test named: \"" + t.name() + "\"");
        }

        Method m2 = Calc.class.getDeclaredMethod("notATest");
        System.out.println("notATest().getAnnotation(Test.class) = " + m2.getAnnotation(Test.class)
                + "  (null -- absent, not an exception)");
    }

    // -------------------------------------------------------------------
    // 2) getAnnotations() vs getDeclaredAnnotations()
    // -------------------------------------------------------------------
    @Inherited
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface Auditable {
    }

    @Auditable
    static class Base {
    }

    static class Derived extends Base {
    }

    private static void demoGetAnnotationsVsGetDeclaredAnnotations() {
        printSection("2) getAnnotations() vs getDeclaredAnnotations()");

        System.out.println("Derived.getAnnotations()         = " + Arrays.toString(Derived.class.getAnnotations())
                + "  (includes @Auditable, INHERITED from Base since it's @Inherited + TYPE-level)");
        System.out.println("Derived.getDeclaredAnnotations()  = "
                + Arrays.toString(Derived.class.getDeclaredAnnotations())
                + "  (empty -- nothing written DIRECTLY on Derived itself)");
    }

    // -------------------------------------------------------------------
    // 3) getAnnotationsByType -- repeatable annotations
    // -------------------------------------------------------------------
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Schedules {
        Schedule[] value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Repeatable(Schedules.class)
    @interface Schedule {
        String day();
    }

    static class WeeklyJob {
        @Schedule(day = "Monday")
        @Schedule(day = "Friday")
        void run() {
        }
    }

    private static void demoRepeatableViaGetAnnotationsByType() throws Exception {
        printSection("3) getAnnotationsByType() -- Repeatable Annotations");

        Method m = WeeklyJob.class.getDeclaredMethod("run");
        Schedule[] schedules = m.getAnnotationsByType(Schedule.class);
        System.out.println("Found " + schedules.length + " @Schedule usages (works whether applied once, many");
        System.out.println("times via the implicit container, or not at all):");
        for (Schedule s : schedules) {
            System.out.println("  day = " + s.day());
        }
    }

    // -------------------------------------------------------------------
    // 4) Reading element values, including a Class<?> element
    // -------------------------------------------------------------------
    interface Filter {
    }

    static class NoOpFilter implements Filter {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Route {
        String path();
        String[] methods() default {"GET"};
        Class<? extends Filter> filter() default NoOpFilter.class;
    }

    static class Controller {
        @Route(path = "/users", methods = {"GET", "POST"})
        public void handleUsers() {
        }
    }

    private static void demoReadingElementValuesIncludingClassElement() throws Exception {
        printSection("4) Reading Element Values, Including a Class<?> Element");

        Method m = Controller.class.getDeclaredMethod("handleUsers");
        Route r = m.getAnnotation(Route.class);
        System.out.println("path    = " + r.path());
        System.out.println("methods = " + Arrays.toString(r.methods()));
        System.out.println("filter  = " + r.filter().getSimpleName() + "  (default, since none was specified)");
        System.out.println("toString() form: " + r);
    }

    // -------------------------------------------------------------------
    // 5) A small, genuinely working test-runner-like framework
    // -------------------------------------------------------------------
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface MyTest {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface MyBeforeEach {
    }

    static class CalculatorTests {
        private int state;

        @MyBeforeEach
        void setUp() {
            state = 0;
        }

        @MyTest
        void additionWorks() {
            state = 2 + 2;
            if (state != 4) throw new AssertionError("expected 4, got " + state);
        }

        @MyTest
        void divisionByZeroThrows() {
            try {
                int x = 1 / 0;
                throw new AssertionError("expected ArithmeticException, got result " + x);
            } catch (ArithmeticException expected) {
                // pass
            }
        }

        @MyTest
        void deliberatelyFailingTest() {
            throw new AssertionError("this test is designed to fail, to show FAIL reporting");
        }

        void notATestHelper() {
            // Not annotated -- the runner must skip this.
        }
    }

    private static void demoMiniTestRunner() throws Exception {
        printSection("5) Mini Test-Runner Framework (scans @MyTest, invokes reflectively)");
        runTests(CalculatorTests.class);
    }

    /**
     * Mirrors, in miniature, what JUnit actually does: scan a class for annotated methods,
     * invoke each reflectively, and report pass/fail.
     */
    static void runTests(Class<?> testClass) throws Exception {
        Object instance = testClass.getDeclaredConstructor().newInstance();

        List<Method> beforeEach = new ArrayList<>();
        List<Method> tests = new ArrayList<>();
        for (Method m : testClass.getDeclaredMethods()) {
            if (m.isAnnotationPresent(MyBeforeEach.class)) beforeEach.add(m);
            if (m.isAnnotationPresent(MyTest.class)) tests.add(m);
        }

        int passed = 0, failed = 0;
        for (Method test : tests) {
            try {
                for (Method setup : beforeEach) {
                    setup.setAccessible(true);
                    setup.invoke(instance);
                }
                test.setAccessible(true);
                test.invoke(instance);
                System.out.println("PASS: " + test.getName());
                passed++;
            } catch (InvocationTargetException e) {
                // The REAL exception thrown by the test is wrapped -- unwrap via getCause()
                // to see the real assertion failure rather than a generic reflection error.
                System.out.println("FAIL: " + test.getName() + " -- " + e.getCause());
                failed++;
            }
        }
        System.out.printf("%nResults: %d passed, %d failed%n", passed, failed);
    }

    // -------------------------------------------------------------------
    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }
}
