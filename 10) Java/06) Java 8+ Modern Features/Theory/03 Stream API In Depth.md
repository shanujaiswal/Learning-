# From Basics to Real Pipelines

--> The previous file introduced streams and the intermediate/terminal split. This file goes deep on the operations actually used to solve real problems: transforming data (`map`), filtering (`filter`), combining into a single value (`reduce`), and building rich result structures (`collect` + the `Collectors` utility class) -- plus flattening nested structures (`flatMap`) and running work across multiple threads (`parallelStream`).

# `map` -- Transforming Each Element

--> `map` applies a `Function` to every element, producing a new stream of (possibly different-typed) results, one-to-one.

```java
import java.util.*;
import java.util.stream.*;

List<String> names = List.of("alice", "bob", "charlie");

List<Integer> nameLengths = names.stream()
        .map(String::length)          // Stream<String> -> Stream<Integer>
        .collect(Collectors.toList());
System.out.println(nameLengths);       // [5, 3, 7]

List<String> capitalized = names.stream()
        .map(s -> Character.toUpperCase(s.charAt(0)) + s.substring(1))
        .collect(Collectors.toList());
System.out.println(capitalized);       // [Alice, Bob, Charlie]
```

# `filter` -- Keeping Elements That Match a Predicate

```java
List<Integer> nums = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

List<Integer> evens = nums.stream()
        .filter(n -> n % 2 == 0)
        .collect(Collectors.toList());
System.out.println(evens);   // [2, 4, 6, 8, 10]
```

--> `filter` never changes the element type -- it only decides which elements PASS THROUGH unchanged. Chaining multiple `filter` calls is equivalent to combining predicates with `&&`.

# `reduce` -- Combining a Stream Into a Single Value

--> `reduce` repeatedly applies a `BinaryOperator` to combine elements into one accumulated result. There are three overloads.

```java
List<Integer> nums = List.of(1, 2, 3, 4, 5);

// 1) No identity -- returns Optional<T> because an empty stream has no result
Optional<Integer> sum1 = nums.stream().reduce((a, b) -> a + b);
System.out.println(sum1.get());    // 15

// 2) With identity value -- returns T directly; identity is the result for an empty stream
int sum2 = nums.stream().reduce(0, (a, b) -> a + b);
System.out.println(sum2);          // 15

int product = nums.stream().reduce(1, (a, b) -> a * b);
System.out.println(product);       // 120

// 3) With identity, accumulator, and combiner -- needed for parallel streams
//    when the accumulator's result type differs from the stream's element type
int totalLength = Stream.of("a", "bb", "ccc")
        .reduce(0,
                (partialResult, str) -> partialResult + str.length(),  // accumulator
                (a, b) -> a + b);                                       // combiner (used when parallel)
System.out.println(totalLength);   // 6
```

--> **Why the combiner matters for parallel streams** -- when a stream is split across threads, each chunk produces its own partial result using the accumulator; the combiner then merges those partial results together. In a SEQUENTIAL stream, the combiner is never actually invoked -- but it must still be provided (and must be correct) for the code to be safely parallelizable later.

# `collect` and the `Collectors` Class

--> `collect` is a **mutable reduction** -- it accumulates stream elements into a result CONTAINER (a `List`, `Set`, `Map`, `String`, or custom object), rather than folding down to a single scalar like `reduce`. The `Collectors` utility class supplies ready-made strategies for nearly every common case.

```java
import java.util.stream.Collectors;

List<String> words = List.of("apple", "banana", "cherry", "date");

// Basic collectors
List<String> asList = words.stream().collect(Collectors.toList());
Set<String> asSet = words.stream().collect(Collectors.toSet());
String joined = words.stream().collect(Collectors.joining());              // "applebananacherrydate"
String joinedComma = words.stream().collect(Collectors.joining(", "));      // "apple, banana, cherry, date"
String joinedBracketed = words.stream().collect(Collectors.joining(", ", "[", "]"));  // "[apple, banana, cherry, date]"

// Counting, summing, averaging
long count = words.stream().collect(Collectors.counting());
int totalChars = words.stream().collect(Collectors.summingInt(String::length));
double avgLength = words.stream().collect(Collectors.averagingInt(String::length));

// Java 16+ shorthand for the most common case (toList() without Collectors prefix)
List<String> quickList = words.stream().toList();   // returns an UNMODIFIABLE list
```

## `toMap` -- Building a `Map` From a Stream

```java
Map<String, Integer> wordToLength = words.stream()
        .collect(Collectors.toMap(w -> w, String::length));
// {banana=6, date=4, apple=5, cherry=6}

// toMap throws IllegalStateException on duplicate keys unless a merge function is supplied
List<String> withDupes = List.of("apple", "apricot", "banana");
Map<Character, String> firstLetterMap = withDupes.stream()
        .collect(Collectors.toMap(
                w -> w.charAt(0),
                w -> w,
                (existing, incoming) -> existing + "," + incoming));   // merge function resolves collisions
// {a=apple,apricot, b=banana}
```

## `groupingBy` -- Splitting a Stream Into Groups

```java
List<String> items = List.of("apple", "banana", "avocado", "blueberry", "cherry");

Map<Character, List<String>> byFirstLetter = items.stream()
        .collect(Collectors.groupingBy(s -> s.charAt(0)));
// {a=[apple, avocado], b=[banana, blueberry], c=[cherry]}

// groupingBy with a DOWNSTREAM collector -- count per group instead of a List per group
Map<Character, Long> countByFirstLetter = items.stream()
        .collect(Collectors.groupingBy(s -> s.charAt(0), Collectors.counting()));
// {a=2, b=2, c=1}

// groupingBy with joining as downstream
Map<Character, String> joinedByFirstLetter = items.stream()
        .collect(Collectors.groupingBy(s -> s.charAt(0), Collectors.joining(", ")));
// {a=apple, avocado, b=banana, blueberry, c=cherry}

// Multi-level grouping (grouping the groups)
record Employee(String department, String name, double salary) {}
List<Employee> employees = List.of(
        new Employee("Eng", "Alice", 95000),
        new Employee("Eng", "Bob", 88000),
        new Employee("Sales", "Charlie", 72000)
);
Map<String, Map<Boolean, List<Employee>>> nested = employees.stream()
        .collect(Collectors.groupingBy(Employee::department,
                Collectors.groupingBy(e -> e.salary() > 80000)));
```

## `partitioningBy` -- A Special Case of Grouping Into Exactly Two Buckets

--> `partitioningBy` always produces a `Map<Boolean, List<T>>` with EXACTLY two keys (`true` and `false`), even if one bucket ends up empty -- unlike `groupingBy`, which only creates keys that actually occur.

```java
List<Integer> nums = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

Map<Boolean, List<Integer>> evenOddPartition = nums.stream()
        .collect(Collectors.partitioningBy(n -> n % 2 == 0));
// {false=[1, 3, 5, 7, 9], true=[2, 4, 6, 8, 10]}
```

--> **`groupingBy` vs `partitioningBy`** -- use `partitioningBy` specifically when the classifier is a boolean condition; it's marginally more efficient (backed by a specialized 2-slot map, not a general `HashMap`) and communicates intent ("split into two groups") more clearly than a boolean-keyed `groupingBy` would.

## Statistics Collectors

```java
IntSummaryStatistics stats = nums.stream()
        .collect(Collectors.summarizingInt(Integer::intValue));
System.out.println(stats.getMin() + " " + stats.getMax() + " " + stats.getAverage()
        + " " + stats.getSum() + " " + stats.getCount());
```

# `flatMap` -- Flattening Nested Structures

--> `map` produces one output PER input element -- `flatMap` produces a STREAM per input element and then flattens all those streams into one continuous stream. This is the tool for turning "a list of lists" (or any nested structure) into a single flat stream.

```java
List<List<Integer>> nested = List.of(
        List.of(1, 2, 3),
        List.of(4, 5),
        List.of(6, 7, 8, 9)
);

// map alone would give a Stream<Stream<Integer>> -- not useful directly
List<Integer> flat = nested.stream()
        .flatMap(List::stream)     // each inner List becomes a Stream, then all are concatenated
        .collect(Collectors.toList());
System.out.println(flat);          // [1, 2, 3, 4, 5, 6, 7, 8, 9]

// Classic use: splitting sentences into a flat stream of words
List<String> sentences = List.of("hello world", "java streams", "flat map");
List<String> words = sentences.stream()
        .flatMap(s -> Arrays.stream(s.split(" ")))
        .collect(Collectors.toList());
System.out.println(words);   // [hello, world, java, streams, flat, map]
```

--> **`map` vs `flatMap` rule of thumb** -- if the mapping function's return type is itself a `Stream`/`List`/`Optional` and you want a single flat result rather than a nested one, you need `flatMap`. `Optional` has its own `flatMap` for exactly this reason (see the Optional file) -- chaining `Optional`-returning methods with plain `map` would produce `Optional<Optional<T>>`.

# Other Useful Intermediate Operations

```java
List<Integer> nums = List.of(5, 3, 8, 1, 9, 3, 5);

List<Integer> sorted = nums.stream().sorted().collect(Collectors.toList());               // [1, 3, 3, 5, 5, 8, 9]
List<Integer> sortedDesc = nums.stream().sorted(Comparator.reverseOrder()).collect(Collectors.toList());
List<Integer> distinct = nums.stream().distinct().collect(Collectors.toList());             // [5, 3, 8, 1, 9]
List<Integer> limited = nums.stream().limit(3).collect(Collectors.toList());                // first 3
List<Integer> skipped = nums.stream().skip(2).collect(Collectors.toList());                 // drop first 2

// Java 9+: takeWhile / dropWhile -- stop/start based on a predicate (order matters, unlike filter)
List<Integer> ordered = List.of(1, 2, 3, 4, 1, 2);
List<Integer> taken = ordered.stream().takeWhile(n -> n < 4).collect(Collectors.toList());  // [1, 2, 3]
List<Integer> dropped = ordered.stream().dropWhile(n -> n < 4).collect(Collectors.toList()); // [4, 1, 2]
```

--> **`takeWhile`/`dropWhile` vs `filter`** -- `filter` inspects EVERY element independently; `takeWhile` stops at the FIRST element that fails the predicate (even if later elements would pass), and `dropWhile` starts including elements from the FIRST one that passes onward. This makes them fundamentally order-sensitive, unlike `filter`.

# Terminal Operations Beyond `collect`

```java
List<Integer> nums = List.of(1, 2, 3, 4, 5);

boolean anyEven = nums.stream().anyMatch(n -> n % 2 == 0);     // true
boolean allEven = nums.stream().allMatch(n -> n % 2 == 0);     // false
boolean noneNegative = nums.stream().noneMatch(n -> n < 0);    // true

Optional<Integer> first = nums.stream().findFirst();            // Optional[1]
Optional<Integer> any = nums.stream().findAny();                 // Optional[1] (sequential) or any element (parallel)

long count = nums.stream().count();
Optional<Integer> min = nums.stream().min(Comparator.naturalOrder());
Optional<Integer> max = nums.stream().max(Comparator.naturalOrder());

Integer[] arr = nums.stream().toArray(Integer[]::new);          // needs an array constructor reference

nums.stream().forEach(System.out::println);                     // side-effecting, no return value
```

--> **`findFirst` vs `findAny`** -- on a sequential stream they behave identically, but `findAny` is explicitly allowed to return ANY matching element for performance reasons once parallelized -- use `findFirst` when order-dependent determinism matters, `findAny` when it doesn't (and parallelism is in play).

# Parallel Streams

--> Any stream can be switched to parallel execution via `.parallel()` (on an existing stream) or `.parallelStream()` (directly from a `Collection`) -- the same pipeline of operations then runs across multiple threads from the JVM's common `ForkJoinPool`, splitting the source and combining partial results automatically.

```java
List<Integer> bigList = IntStream.rangeClosed(1, 10_000_000).boxed().collect(Collectors.toList());

long sequentialSum = bigList.stream()
        .mapToLong(Integer::longValue)
        .sum();

long parallelSum = bigList.parallelStream()
        .mapToLong(Integer::longValue)
        .sum();
```

--> **When parallel streams help** -- large datasets, CPU-bound (not I/O-bound) work per element, and operations that are easy to split and combine (sums, counts, simple transforms). The overhead of splitting work and coordinating threads can easily exceed the savings for small datasets or cheap per-element work -- benchmark before assuming parallel is faster.
--> **Gotcha -- shared mutable state.** Using `forEach` with a lambda that mutates a shared (non-thread-safe) collection or variable across a parallel stream is a classic race-condition bug. Streams are designed around stateless, side-effect-free lambdas specifically so parallelization is safe -- breaking that assumption breaks correctness silently (or with a `ConcurrentModificationException`).

```java
// WRONG -- race condition, non-thread-safe ArrayList mutated from multiple threads
List<Integer> results = new ArrayList<>();
bigList.parallelStream().forEach(results::add);   // may throw or silently lose elements

// RIGHT -- let collect() handle thread-safe accumulation internally
List<Integer> results2 = bigList.parallelStream().collect(Collectors.toList());
```

--> **Gotcha -- order sensitivity.** `forEach` on a parallel stream does NOT guarantee encounter order; use `forEachOrdered` if order matters (at the cost of losing some parallel benefit), or better, avoid depending on side-effect ordering altogether and use `collect`.
--> **Best practice -- default to sequential streams**; reach for `parallelStream()` only after profiling shows a genuine bottleneck on a sufficiently large, CPU-bound, side-effect-free pipeline.

# Summary Table -- Choosing the Right Terminal Operation

| Goal | Use |
|---|---|
| Build a `List`/`Set`/`Map` | `collect(Collectors.toList/toSet/toMap(...))` |
| Fold to a single combined value | `reduce(...)` |
| Group elements by a key | `collect(Collectors.groupingBy(...))` |
| Split into exactly two groups by boolean | `collect(Collectors.partitioningBy(...))` |
| Concatenate strings | `collect(Collectors.joining(...))` |
| Check existence of a match | `anyMatch` / `allMatch` / `noneMatch` |
| Get one element | `findFirst` / `findAny` |
| Just do something per element | `forEach` / `forEachOrdered` |
| Count / min / max / sum stats | `count`, `min`, `max`, `summarizingInt`, etc. |
