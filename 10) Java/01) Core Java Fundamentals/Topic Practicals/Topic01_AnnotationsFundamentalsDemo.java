/*
 * Topic01_AnnotationsFundamentalsDemo.java
 *
 * NOTE ON FILENAME: the public class is named "Topic01_AnnotationsFundamentalsDemo" (Java
 * identifiers can't start with a digit) while the FILE keeps the "01_..." numeric prefix
 * used throughout this repo for ordering.
 *
 * Compile: javac 01_AnnotationsFundamentalsDemo.java
 * Run:     java Topic01_AnnotationsFundamentalsDemo
 *
 * Demonstrates:
 *   1. @Override -- compile-time safety net when overriding a superclass method
 *   2. @Deprecated -- marking discouraged APIs, with since/forRemoval elements
 *   3. @SuppressWarnings -- silencing a specific compiler warning category, scoped narrowly
 *   4. @FunctionalInterface -- enforcing the single-abstract-method contract for lambda targets
 *   5. Retention policy behavior differences (SOURCE vs CLASS vs RUNTIME), demonstrated by
 *      defining one annotation of each retention and showing which ones reflection can see
 *
 * Covers Theory chapter:
 *   01) Core Java Fundamentals/Theory/08 Annotations Fundamentals.md
 */

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class Topic01_AnnotationsFundamentalsDemo {

    public static void main(String[] args) {
        demoOverride();
        demoDeprecated();
        demoSuppressWarnings();
        demoFunctionalInterface();
        demoRetentionPolicyDifferences();
        System.out.println("\nAll annotations-fundamentals demos completed.");
    }

    // -------------------------------------------------------------------
    // 1) @Override
    // -------------------------------------------------------------------
    static class Animal {
        void makeSound() {
            System.out.println("  (generic animal noise)");
        }
    }

    static class Dog extends Animal {
        // @Override tells the COMPILER to verify this really overrides a superclass method.
        // Without it, a typo like "makeSond" would silently create an unrelated new method
        // instead of overriding -- the bug would only surface as confusing runtime behavior.
        @Override
        void makeSound() {
            System.out.println("  Woof!");
        }
    }

    private static void demoOverride() {
        printSection("1) @Override");
        Animal a = new Dog();
        System.out.println("Calling makeSound() on a Dog referenced as Animal (polymorphism):");
        a.makeSound();
        System.out.println("@Override has SOURCE retention -- it is purely a compile-time check and");
        System.out.println("leaves no trace in the .class file or at runtime.");
    }

    // -------------------------------------------------------------------
    // 2) @Deprecated
    // -------------------------------------------------------------------
    static class LegacyCalculator {
        /**
         * @deprecated Use {@link #add(int, int)} instead. Scheduled for removal in a future version.
         */
        @Deprecated(since = "2.5", forRemoval = true)
        int oldAdd(int x, int y) {
            return x + y;
        }

        int add(int x, int y) {
            return x + y;
        }
    }

    @SuppressWarnings("deprecation") // narrowly scoped: we deliberately call a deprecated method here to demo it
    private static void demoDeprecated() {
        printSection("2) @Deprecated");
        LegacyCalculator calc = new LegacyCalculator();
        System.out.println("Calling deprecated oldAdd(2, 3): " + calc.oldAdd(2, 3));
        System.out.println("(This call site would normally trigger a compiler warning at every call site;");
        System.out.println("the warning is suppressed here on purpose via @SuppressWarnings(\"deprecation\").)");
        System.out.println("@Deprecated doesn't stop code from compiling or running -- it's a warning, not");
        System.out.println("an error. Prefer the newer add(int, int): " + calc.add(2, 3));
    }

    // -------------------------------------------------------------------
    // 3) @SuppressWarnings
    // -------------------------------------------------------------------
    @SuppressWarnings("unchecked") // scoped to this single method, not the whole class
    private static void demoSuppressWarnings() {
        printSection("3) @SuppressWarnings");

        // An unchecked cast that the compiler cannot verify at compile time -- normally a warning.
        List<String> raw = new ArrayList();
        raw.add("hello");
        List<String> names = (List<String>) raw;
        System.out.println("Unchecked-cast list built without a compiler warning (suppressed): " + names);

        System.out.println("Scoping @SuppressWarnings to just this method (rather than the class) means any");
        System.out.println("OTHER unchecked-cast bug introduced elsewhere in this class would still warn.");
        System.out.println("@SuppressWarnings string values are NOT compiler-checked -- a typo like");
        System.out.println("\"unchcked\" would compile fine and silence nothing.");
    }

    // -------------------------------------------------------------------
    // 4) @FunctionalInterface
    // -------------------------------------------------------------------

    // Exactly one abstract method (a "SAM") -- default methods don't count against the limit.
    @FunctionalInterface
    interface Calculator {
        int calculate(int a, int b);

        default int calculateTwice(int a, int b) {
            return calculate(calculate(a, b), b);
        }
    }

    private static void demoFunctionalInterface() {
        printSection("4) @FunctionalInterface");

        Calculator addition = (a, b) -> a + b;
        System.out.println("addition.calculate(3, 4)      = " + addition.calculate(3, 4));
        System.out.println("addition.calculateTwice(3, 4) = " + addition.calculateTwice(3, 4)
                + "  (default method: calculate(calculate(3,4), 4) = calculate(7, 4))");

        System.out.println("\n@FunctionalInterface DOCUMENTS and ENFORCES the single-abstract-method contract:");
        System.out.println("if a second abstract method were added to Calculator by accident, the compiler");
        System.out.println("would reject it outright, rather than silently breaking every existing lambda.");
        System.out.println("Built-in examples: Runnable, Comparator<T>, Function, Predicate, Supplier, Consumer.");
    }

    // -------------------------------------------------------------------
    // 5) Retention policy differences, demonstrated via reflection
    // -------------------------------------------------------------------

    // Default retention if @Retention is omitted is CLASS -- present in the .class file, but
    // NOT loaded into runtime reflective metadata. Reflection will NOT be able to see this one.
    @interface ClassRetained {
    }

    // Explicit SOURCE retention -- discarded entirely by the compiler, never even reaches the
    // .class file. Reflection cannot see this one either (there's nothing left to see).
    @Retention(RetentionPolicy.SOURCE)
    @interface SourceRetained {
    }

    // RUNTIME retention -- loaded into the JVM's runtime metadata, so reflection CAN see it.
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface RuntimeRetained {
        String note() default "";
    }

    @ClassRetained
    @SourceRetained
    static class RetentionSample {
        @RuntimeRetained(note = "visible via reflection")
        void annotatedMethod() {
        }
    }

    private static void demoRetentionPolicyDifferences() throws SecurityException {
        printSection("5) Retention Policy Differences (checkable via reflection)");

        Class<RetentionSample> cls = RetentionSample.class;

        System.out.println("Class-level annotations reflection can see on RetentionSample:");
        System.out.println("  getAnnotations() = " + java.util.Arrays.toString(cls.getAnnotations()));
        System.out.println("  (Both @ClassRetained (CLASS retention, the default) and @SourceRetained");
        System.out.println("  (SOURCE retention) are INVISIBLE here -- neither survives into runtime metadata.");
        System.out.println("  This is the classic gotcha: getAnnotation() silently returns null/absent for");
        System.out.println("  anything weaker than RUNTIME retention -- no error, just a misleading 'not there'.)");

        try {
            Method m = cls.getDeclaredMethod("annotatedMethod");
            System.out.println("\nMethod-level check on annotatedMethod():");
            System.out.println("  isAnnotationPresent(RuntimeRetained.class) = "
                    + m.isAnnotationPresent(RuntimeRetained.class));
            RuntimeRetained rr = m.getAnnotation(RuntimeRetained.class);
            System.out.println("  getAnnotation(RuntimeRetained.class).note() = "
                    + (rr != null ? rr.note() : "null"));
            System.out.println("  Only @RuntimeRetained (RetentionPolicy.RUNTIME) is queryable -- exactly");
            System.out.println("  matching the retention table from Theory File 01.");
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    // -------------------------------------------------------------------
    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }
}
