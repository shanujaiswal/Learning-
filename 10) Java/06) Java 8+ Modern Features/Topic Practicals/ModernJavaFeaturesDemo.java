/*
 * ModernJavaFeaturesDemo.java
 *
 * Demonstrates:
 *     1. var -- local variable type inference (Java 10)
 *     2. Records -- concise immutable data carriers, compact constructors (Java 16)
 *     3. Sealed interfaces/classes with a permits list (Java 17)
 *     4. Pattern matching for instanceof (Java 16)
 *     5. Pattern matching for switch + record patterns + case null (Java 21)
 *     6. Text blocks (Java 15)
 *     7. Virtual threads (Java 21)
 *
 * Covers Theory chapter:
 *     06) Java 8+ Modern Features/Theory/06 Modern Java Features Java 9-21.md
 *
 * Compile & run (requires JDK 21+):
 *     javac --release 21 06_modern_java_features_9_to_21.java
 *     java ModernJavaFeaturesDemo
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ModernJavaFeaturesDemo {

    // ---- Records (Java 16) ----
    record Point(int x, int y) { }

    record Range(int min, int max) {
        Range {   // compact constructor -- validates canonical constructor's implicit parameters
            if (min > max) {
                throw new IllegalArgumentException("min must not exceed max");
            }
        }
    }

    record Circle(double radius) {
        static final double PI_APPROX = 3.14159;

        double area() {
            return PI_APPROX * radius * radius;
        }

        static Circle unitCircle() {
            return new Circle(1.0);
        }
    }

    // ---- Sealed hierarchy (Java 17) used later for pattern-matching switch (Java 21) ----
    sealed interface Shape permits ShapeCircle, ShapeSquare, ShapeTriangle { }
    record ShapeCircle(double radius) implements Shape { }
    record ShapeSquare(double side) implements Shape { }
    record ShapeTriangle(double base, double height) implements Shape { }

    public static void main(String[] args) throws Exception {
        printSection("1) var -- local variable type inference (Java 10)");
        varDemo();

        printSection("2) Records -- concise immutable data carriers (Java 16)");
        recordsDemo();

        printSection("3) Sealed interfaces/classes with permits (Java 17)");
        sealedDemo();

        printSection("4) Pattern matching for instanceof (Java 16)");
        instanceofPatternDemo();

        printSection("5) Pattern matching for switch + record patterns + case null (Java 21)");
        switchPatternDemo();

        printSection("6) Text blocks (Java 15)");
        textBlocksDemo();

        printSection("7) Virtual threads (Java 21)");
        virtualThreadsDemo();

        System.out.println("\nAll Modern Java Features (9-21) demos completed.");
    }

    private static void varDemo() {
        var name = "Alice";                          // inferred as String
        var count = 42;                              // inferred as int
        var list = new ArrayList<String>();           // inferred as ArrayList<String>
        var map = new HashMap<String, List<Integer>>();  // avoids repeating a long generic type

        list.add("first");
        list.add("second");
        map.put("evens", List.of(2, 4, 6));

        System.out.println("name  (String)                = " + name);
        System.out.println("count (int)                   = " + count);
        System.out.println("list  (ArrayList<String>)     = " + list);
        System.out.println("map   (HashMap<String,List>)  = " + map);

        for (var i = 0; i < 3; i++) {
            System.out.println("  var in for-loop, i = " + i);
        }

        // var is fixed at compile time -- this would NOT compile if uncommented:
        // name = 5;   // error: incompatible types: int cannot be converted to String
        System.out.println("(var is static typing -- 'name = 5' would be a compile error, not shown here)");
    }

    private static void recordsDemo() {
        Point p1 = new Point(3, 4);
        Point p2 = new Point(3, 4);
        Point p3 = new Point(5, 6);

        System.out.println("p1.x() = " + p1.x() + ", p1.y() = " + p1.y());
        System.out.println("p1 toString()   -> " + p1);
        System.out.println("p1.equals(p2)   -> " + p1.equals(p2) + "  (same components)");
        System.out.println("p1.equals(p3)   -> " + p1.equals(p3) + "  (different components)");
        System.out.println("p1.hashCode()==p2.hashCode() -> " + (p1.hashCode() == p2.hashCode()));

        // Compact constructor validation
        Range valid = new Range(1, 10);
        System.out.println("Valid range: " + valid);
        try {
            new Range(10, 1);
        } catch (IllegalArgumentException e) {
            System.out.println("Range(10, 1) rejected by compact constructor: " + e.getMessage());
        }

        // Additional methods + static factory on a record
        Circle unit = Circle.unitCircle();
        System.out.println("Circle.unitCircle() = " + unit + ", area = " + unit.area());
    }

    private static void sealedDemo() {
        List<Shape> shapes = List.of(
                new ShapeCircle(2.0),
                new ShapeSquare(3.0),
                new ShapeTriangle(4.0, 5.0));

        for (Shape shape : shapes) {
            System.out.println(shape + " -> area = " + areaOf(shape));
        }
        System.out.println("Shape is sealed and permits only: ShapeCircle, ShapeSquare, ShapeTriangle");
        System.out.println("(the compiler enforces this closed set -- enabling exhaustive switches below)");
    }

    // Exhaustive switch over a sealed type -- no default needed (Java 21 pattern matching for switch)
    private static double areaOf(Shape shape) {
        return switch (shape) {
            case ShapeCircle c -> Math.PI * c.radius() * c.radius();
            case ShapeSquare s -> s.side() * s.side();
            case ShapeTriangle t -> 0.5 * t.base() * t.height();
        };
    }

    private static void instanceofPatternDemo() {
        Object[] objects = { "hello", 42, 3.14, List.of(1, 2, 3) };

        for (Object obj : objects) {
            // Pattern matching for instanceof -- cast + declaration happen inline
            if (obj instanceof String s) {
                System.out.println("String pattern matched: length = " + s.length() + ", value = \"" + s + "\"");
            } else if (obj instanceof Integer i) {
                System.out.println("Integer pattern matched: doubled = " + (i * 2));
            } else if (obj instanceof Double d) {
                System.out.println("Double pattern matched: value = " + d);
            } else {
                System.out.println("No specific pattern matched for: " + obj);
            }
        }

        // Flow-typing with a negated check and early continue
        Object maybeString = "flow-typed";
        describeIfString(maybeString);
        describeIfString(99);
    }

    private static void describeIfString(Object obj) {
        if (!(obj instanceof String s)) {
            System.out.println("Not a String: " + obj);
            return;
        }
        // s is in scope here thanks to flow typing, even though the pattern was in a negated check
        System.out.println("Confirmed String via flow typing, uppercase = " + s.toUpperCase());
    }

    private static void switchPatternDemo() {
        Object[] inputs = {
                new Point(0, 0),
                new Point(5, 5),
                new Point(3, 7),
                null,
                "not a point"
        };

        for (Object obj : inputs) {
            System.out.println(describe(obj));
        }
    }

    // Record patterns + when guards + case null, all Java 21
    private static String describe(Object obj) {
        return switch (obj) {
            case Point(int x, int y) when x == 0 && y == 0 -> "origin";
            case Point(int x, int y) when x == y -> "on the diagonal (" + x + "," + y + ")";
            case Point(int x, int y) -> "point at (" + x + ", " + y + ")";
            case null -> "null value (matched explicitly via case null)";
            default -> "not a point: " + obj;
        };
    }

    private static void textBlocksDemo() {
        String jsonOld = "{\n" +
                "  \"name\": \"Alice\",\n" +
                "  \"age\": 30\n" +
                "}";

        String jsonNew = """
                {
                  "name": "Alice",
                  "age": 30
                }
                """;

        System.out.println("Old-style concatenated JSON:");
        System.out.println(jsonOld);
        System.out.println("\nText block JSON (equivalent, far more readable):");
        System.out.println(jsonNew);
        System.out.println("Contents equal (ignoring the text block's extra trailing newline)? "
                + jsonNew.strip().equals(jsonOld.strip()));

        String lineContinuation = """
                line one \
                continues here without a newline in between""";
        System.out.println("\nLine continuation with trailing backslash:");
        System.out.println(lineContinuation);

        String trailingSpacePreserved = """
                exactly one trailing space:\s
                end""";
        System.out.println("\nText block with \\s forcing a preserved trailing space:");
        System.out.println("[" + trailingSpacePreserved + "]");
    }

    private static void virtualThreadsDemo() throws InterruptedException {
        AtomicInteger completedCount = new AtomicInteger(0);
        int taskCount = 1000;

        System.out.println("Submitting " + taskCount + " short-lived tasks to a virtual-thread-per-task executor...");

        try (ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < taskCount; i++) {
                final int taskId = i;
                virtualExecutor.submit(() -> {
                    // Simulated lightweight blocking work -- cheap on virtual threads
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                    completedCount.incrementAndGet();
                    if (taskId == 0) {
                        System.out.println("Sample task 0 ran on: " + Thread.currentThread()
                                + " (isVirtual=" + Thread.currentThread().isVirtual() + ")");
                    }
                });
            }
            virtualExecutor.shutdown();
            virtualExecutor.awaitTermination(30, TimeUnit.SECONDS);
        }

        System.out.println("Completed tasks: " + completedCount.get() + " / " + taskCount);

        // Directly creating and starting a single named virtual thread
        Thread vt = Thread.ofVirtual().name("worker-1").start(() ->
                System.out.println("Explicit virtual thread running, isVirtual = " + Thread.currentThread().isVirtual()));
        vt.join();
    }

    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }
}
