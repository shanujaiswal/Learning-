# Method References -- Lambdas Pointing at Existing Code

--> A **method reference** is shorthand for a lambda that does nothing but call an already-existing method. Where a lambda like `s -> s.toUpperCase()` describes HOW to call the method, a method reference `String::toUpperCase` just points AT the method directly -- same behavior, less noise, and it reuses a name that already documents itself.
--> Method references only apply when the lambda body is a single call to one existing method or constructor with matching parameters -- anything more (extra logic, multiple statements) still needs a full lambda.

# The Four Kinds of Method References

```text
1. Static method reference        ClassName::staticMethod
2. Instance method on a           particularObject::instanceMethod
   particular object
3. Instance method on an          ClassName::instanceMethod
   arbitrary object (of a
   particular type)
4. Constructor reference          ClassName::new
```

## 1) Reference to a Static Method

```java
// Lambda form
Function<String, Integer> parseLambda = s -> Integer.parseInt(s);
// Method reference form -- Integer.parseInt is static
Function<String, Integer> parseRef = Integer::parseInt;

BiFunction<Integer, Integer, Integer> maxLambda = (a, b) -> Math.max(a, b);
BiFunction<Integer, Integer, Integer> maxRef = Math::max;
```

## 2) Reference to an Instance Method on a Particular (Already-Existing) Object

```java
String greeting = "Hello, World!";

// Lambda form -- captures the specific 'greeting' object
Supplier<String> upperLambda = () -> greeting.toUpperCase();
// Method reference form -- bound to the specific 'greeting' instance
Supplier<String> upperRef = greeting::toUpperCase;

PrintStream out = System.out;
Consumer<String> printLambda = s -> out.println(s);
Consumer<String> printRef = out::println;      // very common pattern: System.out::println
```

## 3) Reference to an Instance Method on an Arbitrary Object of a Particular Type

--> This is the kind most often confused with #2. Here, the object the method is called on is NOT fixed in advance -- it's supplied as the FIRST parameter when the functional interface is invoked.

```java
// Lambda form -- 's' (the argument received later) is the object the method is called on
Function<String, Integer> lengthLambda = s -> s.length();
// Method reference form -- String::length is NOT bound to any specific string yet
Function<String, Integer> lengthRef = String::length;

lengthRef.apply("hello");     // 5 -- "hello" becomes the implicit receiver of .length()

// With two parameters: the FIRST parameter becomes the receiver, the rest become arguments
BiFunction<String, String, Boolean> equalsLambda = (a, b) -> a.equalsIgnoreCase(b);
BiFunction<String, String, Boolean> equalsRef = String::equalsIgnoreCase;
equalsRef.apply("Hi", "hi");   // true -- equivalent to "Hi".equalsIgnoreCase("hi")
```

--> **How to tell #2 from #3 at a glance** -- if the part before `::` is a variable holding an already-created object (lowercase, an instance), it's kind #2 (bound). If the part before `::` is a TYPE NAME (capitalized, a class), it's kind #3 (unbound) -- the receiver comes from the functional interface's own first parameter at call time.

## 4) Reference to a Constructor

```java
Supplier<ArrayList<String>> listMaker = ArrayList::new;    // no-arg constructor
ArrayList<String> list = listMaker.get();

Function<String, StringBuilder> sbMaker = StringBuilder::new;   // constructor taking one String arg
StringBuilder sb = sbMaker.apply("initial content");

// Constructor references are extremely common when collecting a Stream into a custom object
Function<Integer, int[]> arrayMaker = int[]::new;   // array constructor reference
int[] arr = arrayMaker.apply(10);                     // creates new int[10]
```

# Method Reference Cheat Sheet

| Kind | Syntax | Equivalent Lambda | Example |
|---|---|---|---|
| 1. Static method | `Type::staticMethod` | `(args) -> Type.staticMethod(args)` | `Integer::parseInt` |
| 2. Bound instance method | `object::instanceMethod` | `(args) -> object.instanceMethod(args)` | `str::toUpperCase` |
| 3. Unbound instance method | `Type::instanceMethod` | `(obj, args) -> obj.instanceMethod(args)` | `String::length` |
| 4. Constructor | `Type::new` | `(args) -> new Type(args)` | `ArrayList::new` |

# Why Streams Were Introduced

--> Before Java 8, processing a collection meant writing an explicit loop that mixed together three concerns: iterating, filtering/transforming, and accumulating a result. The **Stream API** (`java.util.stream`) separates these concerns into a **declarative pipeline** -- you describe the sequence of transformations, and the Stream machinery handles the iteration.

```java
import java.util.*;
import java.util.stream.*;

List<String> names = List.of("Alice", "Bob", "Charlie", "Dave", "Eve");

// Imperative (pre-Java 8 style)
List<String> longNamesImperative = new ArrayList<>();
for (String name : names) {
    if (name.length() > 3) {
        longNamesImperative.add(name.toUpperCase());
    }
}

// Declarative (Stream pipeline)
List<String> longNamesStream = names.stream()
        .filter(name -> name.length() > 3)
        .map(String::toUpperCase)
        .collect(Collectors.toList());
```

--> **A Stream is not a data structure** -- it doesn't store elements. It's a pipeline description over a SOURCE of data (a collection, array, generator function, or I/O channel) that computes results on demand. This distinction matters: streams don't hold state, can't be indexed, and (importantly) can only be traversed **once**.

# Creating Streams

```java
import java.util.*;
import java.util.stream.*;

// From a collection
List<Integer> nums = List.of(1, 2, 3, 4, 5);
Stream<Integer> s1 = nums.stream();

// From individual values
Stream<String> s2 = Stream.of("a", "b", "c");

// From an array
int[] arr = {1, 2, 3};
IntStream s3 = Arrays.stream(arr);

// Empty stream
Stream<String> s4 = Stream.empty();

// Infinite stream -- generate (no pattern, needs limit())
Stream<Double> s5 = Stream.generate(Math::random).limit(3);

// Infinite stream -- iterate (seed + next function, needs limit())
Stream<Integer> s6 = Stream.iterate(1, n -> n * 2).limit(5);   // 1, 2, 4, 8, 16

// Java 9+ iterate with a predicate (bounded, no limit() needed)
Stream<Integer> s7 = Stream.iterate(1, n -> n < 100, n -> n * 2);  // 1, 2, 4, ..., 64

// Primitive streams -- avoid autoboxing for numeric work
IntStream s8 = IntStream.range(0, 5);         // 0,1,2,3,4 (exclusive end)
IntStream s9 = IntStream.rangeClosed(0, 5);   // 0,1,2,3,4,5 (inclusive end)

// From a String's characters
IntStream s10 = "hello".chars();

// From Files (I/O-backed stream, must be closed -- use try-with-resources)
// Stream<String> lines = Files.lines(Paths.get("file.txt"));
```

# Intermediate vs Terminal Operations

--> Stream operations fall into exactly two categories, and understanding the distinction is the key to understanding stream execution.

| | Intermediate Operations | Terminal Operations |
|---|---|---|
| Return type | Another `Stream` | A non-stream value (or `void`) |
| Execution | **Lazy** -- nothing happens when called | **Eager** -- triggers the entire pipeline to run |
| Chainable | Yes -- can chain many in a row | No -- ends the pipeline; only one per pipeline |
| Examples | `filter`, `map`, `sorted`, `distinct`, `limit`, `skip`, `peek`, `flatMap` | `collect`, `forEach`, `reduce`, `count`, `sum`, `min`, `max`, `anyMatch`, `toArray`, `findFirst` |

```java
List<String> names = List.of("Alice", "Bob", "Charlie", "Dave", "Eve");

Stream<String> pipeline = names.stream()
        .filter(n -> n.length() > 3)     // intermediate -- LAZY, just records the step
        .map(String::toUpperCase);        // intermediate -- LAZY, just records the step

// Nothing has actually executed yet! Filtering and mapping only happen once a
// terminal operation is called:
List<String> result = pipeline.collect(Collectors.toList());   // TERMINAL -- now it runs
```

--> **Laziness enables optimizations.** Because intermediate operations are lazy, the Stream framework can fuse multiple operations together and process each element through the ENTIRE pipeline before moving to the next element, rather than materializing an intermediate list after every step. This also means operations like `limit()` can short-circuit -- if only 3 results are needed, an infinite source stops producing after 3, without the JVM ever "knowing" the source was infinite in the first place.

```java
Stream.iterate(1, n -> n + 1)
        .peek(n -> System.out.println("generated: " + n))
        .filter(n -> n % 2 == 0)
        .limit(3)
        .forEach(n -> System.out.println("result: " + n));
// Notice generation stops as soon as 3 matches are found -- it does NOT
// generate all integers first and then filter/limit.
```

# Streams Are Single-Use

--> Once a terminal operation runs, the stream is **consumed** -- calling any operation on it again throws `IllegalStateException: stream has already been operated upon or closed`.

```java
Stream<String> stream = names.stream();
stream.forEach(System.out::println);
// stream.count();   // IllegalStateException -- this stream is exhausted
```

--> To run the "same" pipeline again, create a fresh stream from the source collection (`names.stream()` again) -- the collection itself is unaffected by streaming over it (streams never mutate their source).

# `Stream` vs Collection -- Key Differences

| | Collection | Stream |
|---|---|---|
| Stores elements | Yes | No -- computes on demand |
| Reusable | Yes | No -- single use |
| When is work done | Eagerly, as elements are added | Lazily, only when terminal op runs |
| Can be infinite | No | Yes (`Stream.generate`, `Stream.iterate`) |
| Mutates source | Depending on operation | Never |
| Iteration control | External (`for`, `Iterator`) | Internal (stream drives iteration itself) |

# Common Gotchas and Best Practices

--> **Gotcha -- forgetting a terminal operation.** A pipeline of only intermediate operations does NOTHING -- it's a common bug to build up `.filter().map().sorted()` and forget to call `.collect(...)` or `.forEach(...)`, silently producing no work at all (no error, just a `Stream` object sitting unused).
--> **Gotcha -- reusing a consumed stream.** Always create a new stream (`collection.stream()`) if you need to process the same source again.
--> **Gotcha -- method reference kind #3 confusion.** `String::length` looks like it "has no receiver," but the receiver is supplied as the first argument at call time -- don't confuse it with a static method reference.
--> **Best practice -- prefer method references over trivial lambdas.** `.map(s -> s.trim())` is more idiomatically written `.map(String::trim)`.
--> **Best practice -- avoid side effects in `map`/`filter`.** Stream operations should be free of side effects (no mutating external state) for predictable behavior, especially once parallel streams are involved (see the Stream API In Depth file). Use `peek()` only for debugging, never for essential logic.
--> **Best practice -- prefer primitive streams (`IntStream`, `LongStream`, `DoubleStream`) for numeric-heavy pipelines** to avoid autoboxing overhead, converting back with `.boxed()` only when a `Stream<Integer>` is genuinely needed (e.g. to collect into a `List<Integer>`).
