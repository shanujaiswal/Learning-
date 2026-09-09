/*
 * Topic04_ClassLoadingReflectionDemo.java
 *
 * NOTE ON FILENAME: the public class is named "Topic04_ClassLoadingReflectionDemo" (Java
 * identifiers can't start with a digit) while the FILE keeps the "04_..." numeric prefix
 * used throughout this repo for ordering.
 *
 * Compile: javac 04_ClassLoadingReflectionDemo.java
 * Run:     java Topic04_ClassLoadingReflectionDemo
 *
 * Demonstrates:
 *   1. Class loader hierarchy -- printing getClassLoader() for a JDK class, our own class,
 *      and the system class loader, walking the parent-delegation chain
 *   2. Basic Reflection API usage: Class.forName, getDeclaredFields, getDeclaredMethods,
 *      getDeclaredConstructors, invoking a method reflectively, creating instances reflectively
 *   3. A simple custom @Retention(RUNTIME) annotation, read back via reflection
 *
 * Covers Theory chapter:
 *   07) JVM Internals and Memory Management/Theory/04 Class Loading and Reflection.md
 */

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class Topic04_ClassLoadingReflectionDemo {

    public static void main(String[] args) throws Exception {
        demoClassLoaderHierarchy();
        demoReflectionBasics();
        demoCustomAnnotationViaReflection();
        System.out.println("\nAll class loading / reflection demos completed.");
    }

    // -------------------------------------------------------------------
    // 1) Class loader hierarchy
    // -------------------------------------------------------------------
    private static void demoClassLoaderHierarchy() {
        printSection("1) Class Loader Hierarchy");

        // Core JDK class -> Bootstrap ClassLoader, which the JVM represents as `null`
        // because it's implemented in native code with no Java-visible ClassLoader instance.
        System.out.println("String.class.getClassLoader()      = " + String.class.getClassLoader()
                + "  (null means Bootstrap ClassLoader)");

        // Our own class -> normally the Application/System class loader.
        ClassLoader ourLoader = Topic04_ClassLoadingReflectionDemo.class.getClassLoader();
        System.out.println("Our class's getClassLoader()        = " + ourLoader);

        System.out.println("ClassLoader.getSystemClassLoader()  = " + ClassLoader.getSystemClassLoader());

        System.out.println("\nParent delegation chain (child -> ... -> bootstrap), per Theory File 04:");
        System.out.println("Bootstrap -> Platform -> Application -> (any custom loaders we wrote)\n");

        ClassLoader current = ourLoader;
        int depth = 0;
        while (current != null) {
            System.out.println("  ".repeat(depth) + "-> " + current);
            current = current.getParent();
            depth++;
        }
        System.out.println("  ".repeat(depth) + "-> null (Bootstrap ClassLoader)");

        System.out.println("\nRecall (Theory File 04): a class is uniquely identified by (fully-qualified");
        System.out.println("name + defining class loader) -- the SAME .class file loaded by two DIFFERENT");
        System.out.println("loaders yields two distinct, mutually-incompatible Class objects. This is the");
        System.out.println("root cause of the classic 'ClassCastException: X cannot be cast to X' bug in");
        System.out.println("app servers and plugin systems.");
    }

    // -------------------------------------------------------------------
    // 2) Reflection API basics
    // -------------------------------------------------------------------

    /** A plain target class we'll inspect and manipulate purely via reflection below. */
    public static class Widget {
        private String name;
        private int quantity;

        public Widget() {
            this("unnamed", 0);
        }

        public Widget(String name, int quantity) {
            this.name = name;
            this.quantity = quantity;
        }

        private int computeTotalValue(int unitPrice) {
            return quantity * unitPrice;
        }

        @Override
        public String toString() {
            return "Widget{name='" + name + "', quantity=" + quantity + "}";
        }
    }

    private static void demoReflectionBasics() throws Exception {
        printSection("2) Reflection API Basics");

        // --- Getting a Class object three ways ---
        System.out.println("Three ways to get a Class object (Theory File 04):");
        Class<?> c1 = Widget.class; // compile-time, no instance needed
        System.out.println("  Widget.class                              = " + c1);

        Widget sample = new Widget("bolt", 100);
        Class<?> c2 = sample.getClass(); // from a runtime instance
        System.out.println("  someInstance.getClass()                   = " + c2);

        // By fully-qualified name -- may throw ClassNotFoundException. Note Class.forName
        // ALSO initializes the class (runs static initializers), unlike loadClass alone.
        Class<?> c3 = Class.forName(
                "Topic04_ClassLoadingReflectionDemo$Widget");
        System.out.println("  Class.forName(\"...$Widget\")               = " + c3);
        System.out.println("  (all three refer to the same Class object: " + (c1 == c2 && c2 == c3) + ")");

        // --- Inspecting declared fields ---
        System.out.println("\nDeclared fields on Widget (getDeclaredFields -- ANY visibility, only THIS class):");
        Field[] fields = c1.getDeclaredFields();
        for (Field f : fields) {
            System.out.println("  " + f.getType().getSimpleName() + " " + f.getName());
        }

        // --- Inspecting declared methods ---
        System.out.println("\nDeclared methods on Widget (getDeclaredMethods -- ANY visibility, only THIS class):");
        Method[] methods = c1.getDeclaredMethods();
        for (Method m : methods) {
            System.out.println("  " + m);
        }

        // --- Inspecting declared constructors ---
        System.out.println("\nDeclared constructors on Widget:");
        Constructor<?>[] ctors = c1.getDeclaredConstructors();
        for (Constructor<?> ctor : ctors) {
            System.out.println("  " + ctor);
        }

        // --- Creating an instance reflectively via a specific constructor ---
        System.out.println("\nCreating a Widget reflectively via the (String, int) constructor:");
        @SuppressWarnings("unchecked")
        Constructor<Widget> paramCtor =
                (Constructor<Widget>) c1.getDeclaredConstructor(String.class, int.class);
        paramCtor.setAccessible(true);
        Widget reflectivelyBuilt = paramCtor.newInstance("gear", 25);
        System.out.println("  Built: " + reflectivelyBuilt);

        // --- Creating an instance via the no-arg constructor (common in frameworks) ---
        @SuppressWarnings("unchecked")
        Constructor<Widget> noArgCtor = (Constructor<Widget>) c1.getDeclaredConstructor();
        Widget blank = noArgCtor.newInstance();
        System.out.println("  Built via no-arg constructor: " + blank);

        // --- Reading/writing a private field reflectively ---
        System.out.println("\nReading and mutating the private 'quantity' field reflectively:");
        Field quantityField = c1.getDeclaredField("quantity");
        quantityField.setAccessible(true); // required for private members; bypasses normal access checks
        System.out.println("  Before: quantity = " + quantityField.getInt(reflectivelyBuilt));
        quantityField.setInt(reflectivelyBuilt, 999);
        System.out.println("  After:  quantity = " + quantityField.getInt(reflectivelyBuilt));
        System.out.println("  Widget now toString()s as: " + reflectivelyBuilt);

        // --- Invoking a private method reflectively ---
        System.out.println("\nInvoking the private computeTotalValue(int) method reflectively:");
        Method computeTotal = c1.getDeclaredMethod("computeTotalValue", int.class);
        computeTotal.setAccessible(true);
        Object result = computeTotal.invoke(reflectivelyBuilt, 10); // instance, then args
        System.out.println("  reflectivelyBuilt.computeTotalValue(10) = " + result);

        System.out.println("\nCaution (Theory File 04): setAccessible(true) bypasses normal access control");
        System.out.println("and can be BLOCKED in modularized environments (JPMS) unless the target module");
        System.out.println("`opens` that package, or the JVM is launched with --add-opens. Reflective calls");
        System.out.println("are also meaningfully slower than direct calls -- fine for one-time startup");
        System.out.println("wiring, risky uncached in a hot per-request loop.");
    }

    // -------------------------------------------------------------------
    // 3) Custom annotation + reading it via reflection
    // -------------------------------------------------------------------

    /**
     * A minimal custom annotation. RetentionPolicy.RUNTIME is REQUIRED for it to be visible
     * via reflection at all -- SOURCE and CLASS retention would make this demo see nothing.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Benchmark {
        String label() default "";
        int priority() default 0;
    }

    /** A small service with one annotated method and one plain, unannotated method. */
    public static class ReportService {
        @Benchmark(label = "critical-path", priority = 1)
        public void generateReport() {
            System.out.println("  (generateReport() ran)");
        }

        public void helperMethod() {
            System.out.println("  (helperMethod() ran -- not annotated)");
        }
    }

    private static void demoCustomAnnotationViaReflection() throws Exception {
        printSection("3) Custom Annotation Read via Reflection");

        System.out.println("Scanning ReportService's declared methods for @Benchmark, exactly the pattern");
        System.out.println("frameworks like JUnit (@Test) and Spring (@Autowired) use internally:\n");

        Class<?> serviceClass = ReportService.class;
        for (Method m : serviceClass.getDeclaredMethods()) {
            if (m.isAnnotationPresent(Benchmark.class)) {
                Benchmark b = m.getAnnotation(Benchmark.class);
                System.out.println("  " + m.getName() + "() is annotated @Benchmark(label=\"" + b.label()
                        + "\", priority=" + b.priority() + ")");

                // Demonstrate actually invoking the discovered, annotated method.
                Object instance = serviceClass.getDeclaredConstructor().newInstance();
                System.out.println("  Invoking it reflectively:");
                m.invoke(instance);
            } else {
                System.out.println("  " + m.getName() + "() has no @Benchmark annotation -- skipped");
            }
        }

        System.out.println("\nThis exact scan-then-act pattern is precisely how JUnit finds @Test methods,");
        System.out.println("how Spring wires @Autowired fields, and how Jackson maps @JsonProperty fields --");
        System.out.println("understanding it demystifies a large fraction of 'magic' framework behavior.");
    }

    // -------------------------------------------------------------------
    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }
}
