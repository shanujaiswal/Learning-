# Why Java 8 Changed Everything

--> Before Java 8, expressing "a piece of behavior to pass around" meant writing an anonymous inner class -- several lines of ceremony (`new Comparator<String>() { public int compare(...) { ... } }`) just to pass one line of actual logic. Java 8 (2014) introduced **lambda expressions** and the **Stream API** specifically to let behavior be passed as a value, the way many other languages already allowed.
--> This shift is not cosmetic -- it changes how idiomatic Java code reads. Loops that manually build up results get replaced by declarative pipelines that describe WHAT should happen, not step-by-step HOW. This file covers the foundation that everything else in Java 8+ (streams, Optional, method references) is built on: **functional interfaces** and **lambda expressions**.

# Functional Interfaces -- The Foundation

--> A **functional interface** is an interface with **exactly one abstract method** (often abbreviated SAM -- Single Abstract Method). This single-method constraint is what makes it possible for a lambda expression to represent an instance of that interface -- the lambda's parameter list and body map directly onto that one method's signature and implementation.
--> A functional interface CAN have any number of `default` and `static` methods -- those don't count against the "single abstract method" rule, since they already have bodies and aren't left for the implementer to fill in.

```java
@FunctionalInterface
interface Greeter {
    String greet(String name);          // the single abstract method (SAM)

    default Greeter andThenExclaim() {   // default methods are allowed -- don't count
        return name -> greet(name) + "!";
    }
}
```

--> **`@FunctionalInterface`** is an optional but strongly recommended annotation -- it doesn't change runtime behavior, but it makes the COMPILER enforce the single-abstract-method rule, catching accidental additions of a second abstract method as a compile error instead of a subtle runtime surprise. Always annotate your own functional interfaces with it.

```java
@FunctionalInterface
interface Calculator {
    int calculate(int a, int b);
    // int otherMethod(int x);   <-- adding this would break the SAM rule
    //                                and @FunctionalInterface would flag it as a COMPILE ERROR
}
```

--> **Gotcha -- `Object`'s methods don't count.** An interface method that duplicates a method already on `Object` (like `toString()`, `equals(Object)`, or `hashCode()`) does NOT count as a second abstract method, because every implementing class inherits an implementation from `Object` regardless. This is why interfaces like `Comparator<T>` -- which technically declares `equals(Object)` alongside `compare(T, T)` -- are still valid functional interfaces.

# Lambda Expression Syntax

--> A lambda expression is a concise way to represent an instance of a functional interface -- `(parameters) -> body`. The compiler infers WHICH functional interface it implements from the **target type** (the variable type, method parameter type, or return type the lambda is assigned to).

```java
// Full form: explicit types, braces, explicit return
Comparator<String> byLength = (String a, String b) -> {
    return Integer.compare(a.length(), b.length());
};

// Inferred parameter types (compiler infers from Comparator<String>)
Comparator<String> byLength2 = (a, b) -> Integer.compare(a.length(), b.length());

// Single expression body -- no braces, no explicit return, no semicolon on the expression
Comparator<String> byLength3 = (a, b) -> Integer.compare(a.length(), b.length());

// Single parameter -- parentheses are optional
Runnable r = () -> System.out.println("no args needed for Runnable.run()");
Consumer<String> printer = s -> System.out.println(s);   // parens around single param optional

// Multiple statements require braces AND an explicit return (if the method returns a value)
Function<Integer, Integer> square = x -> {
    int result = x * x;
    return result;
};
```

--> **Syntax rules summarized:**

| Situation | Rule |
|---|---|
| Zero parameters | `() -> expr` -- parentheses required |
| One parameter | `x -> expr` or `(x) -> expr` -- parens optional |
| One parameter with explicit type | `(int x) -> expr` -- parens REQUIRED once a type is written |
| Multiple parameters | `(x, y) -> expr` -- parens required |
| Single expression body | No braces, no `return`, no semicolon needed (the expression's value IS the return value) |
| Multiple statements | `{ ... }` braces required, and `return` required if the interface method is non-void |
| Mixing typed and untyped params | NOT allowed -- `(int x, y)` is a compile error; either type all params or none |

# Effectively Final Variables -- Lambda Capture Rules

--> A lambda can reference local variables from its enclosing scope, but ONLY if those variables are **final or effectively final** (never reassigned after initialization, even though not literally marked `final`). This is a deliberate restriction, not an oversight.

```java
int counter = 0;
Runnable r = () -> System.out.println(counter);   // OK -- counter is effectively final (never reassigned)

int mutableCounter = 0;
// mutableCounter++;                                 // if this line existed anywhere,
// Runnable r2 = () -> System.out.println(mutableCounter);  // this would be a COMPILE ERROR
```

--> **Why this restriction exists** -- a lambda may outlive the method call that created it (e.g. it gets stored and invoked later, possibly on another thread). Java captures local variables **by value** (a snapshot), not by reference. Allowing reassignment after capture would create a mismatch between what the lambda "sees" and what the enclosing method thinks is true -- so the language simply forbids it at compile time, sidestepping an entire class of concurrency bugs.
--> **Workaround for mutable state** -- wrap the value in a single-element array, an `AtomicInteger`, or (better) restructure the code to return a value rather than mutate a captured variable.

```java
import java.util.concurrent.atomic.AtomicInteger;

AtomicInteger counter = new AtomicInteger(0);
Runnable increment = () -> counter.incrementAndGet();  // OK -- the reference itself never changes,
increment.run();                                          // only the object's internal state does
```

--> **Instance and static fields are exempt.** Fields (not local variables) can be freely reassigned inside a lambda, because they're stored on the heap, not the stack, and accessed through `this` (implicitly captured), not copied by value.

# Lambdas and `this`

--> Inside a lambda, `this` refers to the **enclosing instance**, NOT the lambda itself -- lambdas do not introduce their own `this` binding, unlike anonymous inner classes.

```java
class Counter {
    int count = 0;
    Runnable makeIncrementer() {
        return () -> {
            this.count++;          // 'this' is the Counter instance, exactly as if written outside the lambda
            System.out.println(this.getClass());  // prints Counter, not some anonymous lambda class
        };
    }
}
```

--> Contrast with an anonymous inner class, where `this` refers to the anonymous class instance itself, and `Counter.this` would be needed to reach the enclosing instance -- this is one of the genuine behavioral differences between lambdas and anonymous classes, not just a syntax simplification.

# Built-In Functional Interfaces (`java.util.function`)

--> Rather than writing a fresh functional interface for every use case, Java 8 ships a standard library of general-purpose ones in `java.util.function`. Learning these by name and shape is essential -- almost all Stream API and modern library code is expressed in terms of them.

| Interface | Abstract Method | Signature Shape | Purpose |
|---|---|---|---|
| `Function<T,R>` | `R apply(T t)` | T -> R | Transform a value into another value/type |
| `BiFunction<T,U,R>` | `R apply(T t, U u)` | (T,U) -> R | Transform two values into one |
| `Predicate<T>` | `boolean test(T t)` | T -> boolean | Test/filter a value |
| `BiPredicate<T,U>` | `boolean test(T t, U u)` | (T,U) -> boolean | Test two values |
| `Consumer<T>` | `void accept(T t)` | T -> void | Do something with a value, return nothing |
| `BiConsumer<T,U>` | `void accept(T t, U u)` | (T,U) -> void | Do something with two values |
| `Supplier<T>` | `T get()` | () -> T | Produce a value, taking no input |
| `UnaryOperator<T>` | `T apply(T t)` | T -> T | `Function<T,T>` specialization -- same input/output type |
| `BinaryOperator<T>` | `T apply(T t1, T t2)` | (T,T) -> T | `BiFunction<T,T,T>` specialization -- combine two of the same type |
| `Runnable` | `void run()` | () -> void | No input, no output (pre-existing, reused as functional interface) |
| `Callable<V>` | `V call()` | () -> V | No input, returns a value, can throw checked exceptions |

```java
import java.util.function.*;

Function<String, Integer> length = String::length;
System.out.println(length.apply("hello"));              // 5

BiFunction<Integer, Integer, Integer> add = (a, b) -> a + b;
System.out.println(add.apply(3, 4));                      // 7

Predicate<Integer> isEven = n -> n % 2 == 0;
System.out.println(isEven.test(10));                      // true

Consumer<String> print = System.out::println;
print.accept("consumed");                                  // consumed

Supplier<Double> randomValue = Math::random;
System.out.println(randomValue.get());                     // some random double

UnaryOperator<Integer> doubleIt = n -> n * 2;
System.out.println(doubleIt.apply(21));                    // 42

BinaryOperator<Integer> max = Integer::max;
System.out.println(max.apply(3, 9));                       // 9
```

# Primitive Specializations -- Avoiding Autoboxing Overhead

--> Generic functional interfaces like `Function<Integer, Integer>` force autoboxing (`int` -> `Integer` -> `int`), which allocates wrapper objects and adds overhead. For hot paths (e.g. Stream pipelines over large numeric datasets), Java provides **primitive-specialized** variants that avoid boxing entirely.

| Generic | Primitive Specializations |
|---|---|
| `Function<T,R>` | `IntFunction<R>`, `ToIntFunction<T>`, `IntToDoubleFunction`, `IntUnaryOperator`, `IntBinaryOperator`, and `Long`/`Double` equivalents |
| `Predicate<T>` | `IntPredicate`, `LongPredicate`, `DoublePredicate` |
| `Consumer<T>` | `IntConsumer`, `LongConsumer`, `DoubleConsumer` |
| `Supplier<T>` | `IntSupplier`, `LongSupplier`, `DoubleSupplier`, `BooleanSupplier` |

```java
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;

IntPredicate isPositive = n -> n > 0;      // no boxing to Integer -- works directly on int
System.out.println(isPositive.test(-5));    // false

IntUnaryOperator square = n -> n * n;
System.out.println(square.applyAsInt(6));   // 36
```

--> **Rule of thumb** -- use the primitive-specialized interfaces when working with large volumes of `int`/`long`/`double` in performance-sensitive code (this is exactly what `IntStream`, `LongStream`, and `DoubleStream`, covered in the next file, are built on). For everyday application code where the dataset is small, the generic versions are perfectly fine and more composable.

# Composing Functional Interfaces

--> Several built-in interfaces provide **default methods** for combining multiple instances into one, avoiding manually nested calls.

```java
import java.util.function.*;

// Function composition
Function<Integer, Integer> times2 = x -> x * 2;
Function<Integer, Integer> plus3 = x -> x + 3;

Function<Integer, Integer> combined1 = times2.andThen(plus3);  // apply times2 FIRST, then plus3
System.out.println(combined1.apply(5));   // (5*2)+3 = 13

Function<Integer, Integer> combined2 = times2.compose(plus3);  // apply plus3 FIRST, then times2
System.out.println(combined2.apply(5));   // (5+3)*2 = 16

// Predicate composition
Predicate<Integer> isPositive = n -> n > 0;
Predicate<Integer> isEven = n -> n % 2 == 0;

Predicate<Integer> positiveAndEven = isPositive.and(isEven);
Predicate<Integer> positiveOrEven = isPositive.or(isEven);
Predicate<Integer> isNegative = isPositive.negate();

System.out.println(positiveAndEven.test(4));    // true
System.out.println(positiveAndEven.test(-4));   // false

// Consumer composition -- runs both, in sequence
Consumer<String> logUpper = s -> System.out.println(s.toUpperCase());
Consumer<String> logLower = s -> System.out.println(s.toLowerCase());
Consumer<String> both = logUpper.andThen(logLower);
both.accept("Hello");   // prints HELLO then hello
```

--> **`andThen` vs `compose`** on `Function` are mirror images -- `f.andThen(g)` means "f, then g" (`g(f(x))`), while `f.compose(g)` means "g, then f" (`f(g(x))`). Mixing these up is a common source of subtle bugs -- always double check the order when chaining transformations.

# Anonymous Classes vs Lambdas -- What Actually Differs

| Aspect | Anonymous Inner Class | Lambda Expression |
|---|---|---|
| `this` | Refers to the anonymous class instance | Refers to the enclosing instance |
| Can implement | Any interface (any number of abstract methods) or extend any class | Only a functional interface (exactly one abstract method) |
| New scope? | Yes -- introduces a new class and scope | No -- shares the enclosing method's scope |
| Verbosity | High -- full class body syntax | Low -- just parameters and body |
| Bytecode | Compiled to a separate `.class` file | Compiled using `invokedynamic` + method handles, no separate `.class` file per lambda |
| Can be generic itself | Yes | No -- but can implement a generic functional interface |

--> **Performance note** -- lambdas are NOT implemented as hidden anonymous classes under the hood (a common misconception). The compiler emits an `invokedynamic` instruction, and the actual implementation class is generated lazily at runtime by `LambdaMetafactory`, which the JVM can optimize more aggressively than a statically compiled anonymous class, and which avoids bloating the number of `.class` files produced per lambda.

# Common Gotchas and Best Practices

--> **Gotcha -- lambdas can't throw checked exceptions unless the functional interface declares them.** `Function<T,R>`'s `apply` doesn't declare `throws IOException`, so a lambda assigned to it can't throw a checked `IOException` directly -- it must be caught internally or wrapped in an unchecked exception.

```java
Function<String, Integer> parse = s -> {
    try {
        return Integer.parseInt(s);
    } catch (NumberFormatException e) {   // unchecked -- fine, no need to declare it
        return 0;
    }
};
```

--> **Gotcha -- overload resolution ambiguity.** If a method is overloaded with both a functional interface parameter and another compatible type, or with two DIFFERENT functional interfaces that fit the same lambda shape (e.g. `Runnable` and `Callable<V>` both accept `() -> ...`), the compiler may report ambiguity requiring an explicit cast to disambiguate.
--> **Best practice -- keep lambda bodies short.** A lambda spanning many lines defeats the readability purpose of using one -- extract a named method and reference it (see the Method References file) once a lambda body grows past a few lines.
--> **Best practice -- prefer method references over lambdas that just call one existing method** (`s -> s.toUpperCase()` is better written as `String::toUpperCase`) -- covered fully in the next file.
--> **Best practice -- annotate custom functional interfaces with `@FunctionalInterface`** so accidental changes fail to compile rather than fail at some confusing call site later.
--> **Gotcha -- lambdas and serialization.** Lambdas ARE serializable if their target type is a `Serializable` functional interface, but this is fragile and implementation-dependent -- avoid relying on lambda serialization in production code; prefer serializing plain data and reconstructing behavior separately.
