/*
 * StreamApiInDepthDemo.java
 *
 * Demonstrates:
 *     1. map / filter basics
 *     2. reduce (all three overloads)
 *     3. collect + Collectors: toList, toSet, joining, toMap (with merge function),
 *        counting, summingInt, averagingInt, summarizingInt
 *     4. groupingBy (plain, with downstream collector, multi-level)
 *     5. partitioningBy
 *     6. flatMap flattening nested lists and splitting sentences into words
 *     7. sorted / distinct / limit / skip / takeWhile / dropWhile
 *     8. anyMatch/allMatch/noneMatch, findFirst/findAny
 *     9. parallelStream with a safe collect() vs an unsafe shared-mutation forEach
 *
 * Covers Theory chapter:
 *     06) Java 8+ Modern Features/Theory/03 Stream API In Depth.md
 *
 * Compile & run:
 *     javac 03_stream_api_in_depth.java
 *     java StreamApiInDepthDemo
 */

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.*;

public class StreamApiInDepthDemo {

    record Employee(String department, String name, double salary) {}

    public static void main(String[] args) {
        printSection("1) map / filter");
        mapAndFilter();

        printSection("2) reduce -- three overloads");
        reduceOverloads();

        printSection("3) collect + Collectors basics");
        collectorsBasics();

        printSection("4) groupingBy");
        groupingByDemo();

        printSection("5) partitioningBy");
        partitioningByDemo();

        printSection("6) flatMap");
        flatMapDemo();

        printSection("7) sorted / distinct / limit / skip / takeWhile / dropWhile");
        otherIntermediateOps();

        printSection("8) anyMatch/allMatch/noneMatch, findFirst/findAny");
        matchingAndFinding();

        printSection("9) parallelStream -- safe vs unsafe accumulation");
        parallelStreamDemo();

        System.out.println("\nAll Stream API in-depth demos completed.");
    }

    private static void mapAndFilter() {
        List<String> names = List.of("alice", "bob", "charlie");

        List<Integer> lengths = names.stream().map(String::length).collect(Collectors.toList());
        System.out.println("Lengths: " + lengths);

        List<String> capitalized = names.stream()
                .map(s -> Character.toUpperCase(s.charAt(0)) + s.substring(1))
                .collect(Collectors.toList());
        System.out.println("Capitalized: " + capitalized);

        List<Integer> nums = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        List<Integer> evens = nums.stream().filter(n -> n % 2 == 0).collect(Collectors.toList());
        System.out.println("Evens: " + evens);
    }

    private static void reduceOverloads() {
        List<Integer> nums = List.of(1, 2, 3, 4, 5);

        Optional<Integer> sumNoIdentity = nums.stream().reduce((a, b) -> a + b);
        System.out.println("reduce (no identity): " + sumNoIdentity.get());

        int sumWithIdentity = nums.stream().reduce(0, (a, b) -> a + b);
        System.out.println("reduce (identity=0):  " + sumWithIdentity);

        int product = nums.stream().reduce(1, (a, b) -> a * b);
        System.out.println("reduce product (identity=1): " + product);

        int totalLength = Stream.of("a", "bb", "ccc")
                .reduce(0, (partial, str) -> partial + str.length(), Integer::sum);
        System.out.println("reduce (identity, accumulator, combiner) total chars: " + totalLength);
    }

    private static void collectorsBasics() {
        List<String> words = List.of("apple", "banana", "cherry", "date");

        System.out.println("toSet:    " + words.stream().collect(Collectors.toSet()).size() + " unique elements");
        System.out.println("joining:  " + words.stream().collect(Collectors.joining(", ", "[", "]")));
        System.out.println("counting: " + words.stream().collect(Collectors.counting()));
        System.out.println("sumChars: " + words.stream().collect(Collectors.summingInt(String::length)));
        System.out.printf("avgChars: %.2f%n", words.stream().collect(Collectors.averagingInt(String::length)));

        List<String> quickList = words.stream().toList();   // Java 16+ shorthand, unmodifiable
        System.out.println("toList() shorthand: " + quickList);
        try {
            quickList.add("elderberry");
        } catch (UnsupportedOperationException e) {
            System.out.println("Confirmed toList() result is unmodifiable: " + e.getClass().getSimpleName());
        }

        List<String> withDupes = List.of("apple", "apricot", "banana");
        Map<Character, String> firstLetterMap = withDupes.stream()
                .collect(Collectors.toMap(w -> w.charAt(0), w -> w, (existing, incoming) -> existing + "," + incoming));
        System.out.println("toMap with merge function: " + firstLetterMap);

        IntSummaryStatistics stats = words.stream().collect(Collectors.summarizingInt(String::length));
        System.out.println("summarizingInt -> min=" + stats.getMin() + " max=" + stats.getMax()
                + " avg=" + stats.getAverage() + " sum=" + stats.getSum());
    }

    private static void groupingByDemo() {
        List<String> items = List.of("apple", "banana", "avocado", "blueberry", "cherry");

        Map<Character, List<String>> byFirstLetter = items.stream()
                .collect(Collectors.groupingBy(s -> s.charAt(0)));
        System.out.println("groupingBy (plain):        " + byFirstLetter);

        Map<Character, Long> countByFirstLetter = items.stream()
                .collect(Collectors.groupingBy(s -> s.charAt(0), Collectors.counting()));
        System.out.println("groupingBy + counting:     " + countByFirstLetter);

        Map<Character, String> joinedByFirstLetter = items.stream()
                .collect(Collectors.groupingBy(s -> s.charAt(0), Collectors.joining(", ")));
        System.out.println("groupingBy + joining:      " + joinedByFirstLetter);

        List<Employee> employees = List.of(
                new Employee("Eng", "Alice", 95000),
                new Employee("Eng", "Bob", 88000),
                new Employee("Sales", "Charlie", 72000)
        );
        Map<String, Map<Boolean, List<Employee>>> nested = employees.stream()
                .collect(Collectors.groupingBy(Employee::department,
                        Collectors.groupingBy(e -> e.salary() > 80000)));
        System.out.println("multi-level groupingBy:    " + nested);
    }

    private static void partitioningByDemo() {
        List<Integer> nums = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        Map<Boolean, List<Integer>> evenOddPartition = nums.stream()
                .collect(Collectors.partitioningBy(n -> n % 2 == 0));
        System.out.println("partitioningBy even/odd: " + evenOddPartition);

        // Even a stream with no matches for one branch still produces both keys
        Map<Boolean, List<Integer>> allEvens = List.of(2, 4, 6).stream()
                .collect(Collectors.partitioningBy(n -> n % 2 == 0));
        System.out.println("partitioningBy (all even, false bucket still present): " + allEvens);
    }

    private static void flatMapDemo() {
        List<List<Integer>> nested = List.of(
                List.of(1, 2, 3),
                List.of(4, 5),
                List.of(6, 7, 8, 9)
        );
        List<Integer> flat = nested.stream().flatMap(List::stream).collect(Collectors.toList());
        System.out.println("Flattened nested lists: " + flat);

        List<String> sentences = List.of("hello world", "java streams", "flat map");
        List<String> words = sentences.stream()
                .flatMap(s -> Arrays.stream(s.split(" ")))
                .collect(Collectors.toList());
        System.out.println("Flattened words: " + words);
    }

    private static void otherIntermediateOps() {
        List<Integer> nums = List.of(5, 3, 8, 1, 9, 3, 5);

        System.out.println("sorted:        " + nums.stream().sorted().collect(Collectors.toList()));
        System.out.println("sorted (desc): " + nums.stream().sorted(Comparator.reverseOrder()).collect(Collectors.toList()));
        System.out.println("distinct:      " + nums.stream().distinct().collect(Collectors.toList()));
        System.out.println("limit(3):      " + nums.stream().limit(3).collect(Collectors.toList()));
        System.out.println("skip(2):       " + nums.stream().skip(2).collect(Collectors.toList()));

        List<Integer> ordered = List.of(1, 2, 3, 4, 1, 2);
        System.out.println("takeWhile(<4): " + ordered.stream().takeWhile(n -> n < 4).collect(Collectors.toList()));
        System.out.println("dropWhile(<4): " + ordered.stream().dropWhile(n -> n < 4).collect(Collectors.toList()));
    }

    private static void matchingAndFinding() {
        List<Integer> nums = List.of(1, 2, 3, 4, 5);

        System.out.println("anyMatch even:  " + nums.stream().anyMatch(n -> n % 2 == 0));
        System.out.println("allMatch even:  " + nums.stream().allMatch(n -> n % 2 == 0));
        System.out.println("noneMatch < 0:  " + nums.stream().noneMatch(n -> n < 0));
        System.out.println("findFirst:      " + nums.stream().findFirst().orElse(-1));
        System.out.println("findAny:        " + nums.stream().findAny().orElse(-1));
        System.out.println("min:            " + nums.stream().min(Comparator.naturalOrder()).orElse(-1));
        System.out.println("max:            " + nums.stream().max(Comparator.naturalOrder()).orElse(-1));
    }

    private static void parallelStreamDemo() {
        List<Integer> bigList = IntStream.rangeClosed(1, 1_000_000).boxed().collect(Collectors.toList());

        long start1 = System.nanoTime();
        long sequentialSum = bigList.stream().mapToLong(Integer::longValue).sum();
        long time1 = System.nanoTime() - start1;

        long start2 = System.nanoTime();
        long parallelSum = bigList.parallelStream().mapToLong(Integer::longValue).sum();
        long time2 = System.nanoTime() - start2;

        System.out.println("Sequential sum = " + sequentialSum + "  (" + (time1 / 1_000_000) + " ms)");
        System.out.println("Parallel sum   = " + parallelSum + "  (" + (time2 / 1_000_000) + " ms)");
        System.out.println("Both sums match: " + (sequentialSum == parallelSum));

        // Safe accumulation: collect() handles thread-safety internally
        List<Integer> safeResult = bigList.parallelStream()
                .filter(n -> n % 100000 == 0)
                .collect(Collectors.toList());
        System.out.println("Safe parallel collect() result: " + safeResult);

        // Demonstrate the pattern to AVOID: mutating a shared non-thread-safe counter.
        // AtomicInteger is used here specifically because it IS thread-safe --
        // a plain 'int' or ArrayList::add under parallelStream().forEach would be unsafe.
        AtomicInteger unsafeStyleButSafeCounter = new AtomicInteger(0);
        bigList.parallelStream().forEach(n -> unsafeStyleButSafeCounter.incrementAndGet());
        System.out.println("Thread-safe counter after parallel forEach: " + unsafeStyleButSafeCounter.get()
                + " (matches list size: " + (unsafeStyleButSafeCounter.get() == bigList.size()) + ")");
        System.out.println("Lesson: prefer collect()/reduce() over forEach()-with-shared-mutable-state,");
        System.out.println("        and if you must accumulate manually, use a thread-safe holder like AtomicInteger.");
    }

    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }
}
