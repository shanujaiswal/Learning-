/*
 * MethodReferencesStreamsDemo.java
 *
 * Demonstrates:
 *     1. All four kinds of method references (static, bound instance, unbound
 *        instance, constructor)
 *     2. Various ways to create a Stream (collections, arrays, of, generate,
 *        iterate, IntStream ranges)
 *     3. Intermediate vs terminal operations, and stream laziness
 *     4. Short-circuiting an infinite stream with limit()
 *     5. Streams being single-use (IllegalStateException on reuse)
 *
 * Covers Theory chapter:
 *     06) Java 8+ Modern Features/Theory/02 Method References and Streams Introduction.md
 *
 * Compile & run:
 *     javac 02_method_references_streams_intro.java
 *     java MethodReferencesStreamsDemo
 */

import java.util.*;
import java.util.function.*;
import java.util.stream.*;

public class MethodReferencesStreamsDemo {

    public static void main(String[] args) {
        printSection("1) Four kinds of method references");
        methodReferenceKinds();

        printSection("2) Creating streams in various ways");
        creatingStreams();

        printSection("3) Intermediate vs terminal operations (laziness)");
        intermediateVsTerminal();

        printSection("4) Short-circuiting an infinite stream");
        shortCircuitInfiniteStream();

        printSection("5) Streams are single-use");
        singleUseStream();

        System.out.println("\nAll method reference / stream intro demos completed.");
    }

    // -----------------------------------------------------------------
    // 1) Method reference kinds
    // -----------------------------------------------------------------
    private static void methodReferenceKinds() {
        // Kind 1: static method reference
        Function<String, Integer> parseRef = Integer::parseInt;
        System.out.println("Static ref  Integer::parseInt applied to \"42\"  = " + parseRef.apply("42"));

        BiFunction<Integer, Integer, Integer> maxRef = Math::max;
        System.out.println("Static ref  Math::max applied to (3,9)         = " + maxRef.apply(3, 9));

        // Kind 2: bound instance method reference (bound to a specific existing object)
        String greeting = "Hello, World!";
        Supplier<String> upperRef = greeting::toUpperCase;
        System.out.println("Bound ref   greeting::toUpperCase              = " + upperRef.get());

        PrintStream out = System.out;
        Consumer<String> printRef = out::println;
        System.out.print("Bound ref   out::println                       -> ");
        printRef.accept("printed via bound method reference");

        // Kind 3: unbound instance method reference (receiver supplied at call time)
        Function<String, Integer> lengthRef = String::length;
        System.out.println("Unbound ref String::length applied to \"hello\" = " + lengthRef.apply("hello"));

        BiFunction<String, String, Boolean> equalsIgnoreCaseRef = String::equalsIgnoreCase;
        System.out.println("Unbound ref String::equalsIgnoreCase(\"Hi\",\"hi\") = "
                + equalsIgnoreCaseRef.apply("Hi", "hi"));

        // Kind 4: constructor reference
        Supplier<ArrayList<String>> listMaker = ArrayList::new;
        List<String> freshList = listMaker.get();
        freshList.add("built via constructor reference");
        System.out.println("Constructor ref ArrayList::new -> " + freshList);

        Function<String, StringBuilder> sbMaker = StringBuilder::new;
        StringBuilder sb = sbMaker.apply("seeded content");
        System.out.println("Constructor ref StringBuilder::new(\"seeded content\") -> " + sb);

        IntFunction<int[]> arrayMaker = int[]::new;
        int[] freshArray = arrayMaker.apply(5);
        System.out.println("Constructor ref int[]::new(5) -> array length = " + freshArray.length);
    }

    // -----------------------------------------------------------------
    // 2) Creating streams
    // -----------------------------------------------------------------
    private static void creatingStreams() {
        List<Integer> nums = List.of(1, 2, 3, 4, 5);
        System.out.println("From collection: " + nums.stream().collect(Collectors.toList()));

        Stream<String> s2 = Stream.of("a", "b", "c");
        System.out.println("Stream.of:       " + s2.collect(Collectors.toList()));

        int[] arr = {10, 20, 30};
        System.out.println("Arrays.stream:   " + Arrays.toString(Arrays.stream(arr).toArray()));

        System.out.println("Stream.empty count: " + Stream.empty().count());

        List<Integer> generated = Stream.generate(() -> 7).limit(3).collect(Collectors.toList());
        System.out.println("Stream.generate(() -> 7).limit(3): " + generated);

        List<Integer> iterated = Stream.iterate(1, n -> n * 2).limit(5).collect(Collectors.toList());
        System.out.println("Stream.iterate(1, n -> n*2).limit(5): " + iterated);

        // Java 9+ bounded iterate (predicate-based, no limit() needed)
        List<Integer> boundedIterate = Stream.iterate(1, n -> n < 100, n -> n * 2)
                .collect(Collectors.toList());
        System.out.println("Stream.iterate(1, n<100, n->n*2):    " + boundedIterate);

        System.out.println("IntStream.range(0,5):        " + Arrays.toString(IntStream.range(0, 5).toArray()));
        System.out.println("IntStream.rangeClosed(0,5):  " + Arrays.toString(IntStream.rangeClosed(0, 5).toArray()));

        List<Integer> chars = "hello".chars().boxed().collect(Collectors.toList());
        System.out.println("\"hello\".chars() (char codes): " + chars);
    }

    // -----------------------------------------------------------------
    // 3) Intermediate vs terminal, laziness
    // -----------------------------------------------------------------
    private static void intermediateVsTerminal() {
        List<String> names = List.of("Alice", "Bob", "Charlie", "Dave", "Eve");

        System.out.println("Building pipeline (filter + map) -- nothing executes yet...");
        Stream<String> pipeline = names.stream()
                .filter(n -> {
                    System.out.println("   filtering: " + n);
                    return n.length() > 3;
                })
                .map(n -> {
                    System.out.println("   mapping:   " + n);
                    return n.toUpperCase();
                });
        System.out.println("Pipeline object created -- notice NO filter/map prints appeared above this line.");

        System.out.println("Now calling terminal op collect(toList())...");
        List<String> result = pipeline.collect(Collectors.toList());
        System.out.println("Result: " + result);
    }

    // -----------------------------------------------------------------
    // 4) Short-circuiting an infinite stream
    // -----------------------------------------------------------------
    private static void shortCircuitInfiniteStream() {
        System.out.println("Stream.iterate(1, n -> n+1) is infinite -- but limit(3) after filter stops early:");
        List<Integer> firstThreeEven = Stream.iterate(1, n -> n + 1)
                .peek(n -> System.out.println("   generated: " + n))
                .filter(n -> n % 2 == 0)
                .limit(3)
                .collect(Collectors.toList());
        System.out.println("First three even numbers found: " + firstThreeEven);
        System.out.println("Notice generation stopped at 6, not continuing to infinity.");
    }

    // -----------------------------------------------------------------
    // 5) Single-use streams
    // -----------------------------------------------------------------
    private static void singleUseStream() {
        Stream<String> stream = Stream.of("x", "y", "z");
        long count = stream.count();   // terminal op -- consumes the stream
        System.out.println("First terminal op (count): " + count);

        try {
            stream.forEach(System.out::println);   // reusing a consumed stream
        } catch (IllegalStateException e) {
            System.out.println("Reusing consumed stream threw as expected: " + e.getMessage());
        }
    }

    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }
}
