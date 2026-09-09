# The Problem `Optional` Solves

--> `null` in Java has always meant two very different things at once: "this value is legitimately absent" and "something went wrong / was never set." A method returning `String` gives no signal at the type level about whether `null` is a possible, meaningful result -- the caller has to know from documentation (or find out the hard way, via `NullPointerException`). Tony Hoare, who invented the null reference in 1965, famously called it his **"billion-dollar mistake."**
--> `java.util.Optional<T>` (Java 8) is a container type that makes absence **explicit in the type signature** -- a method returning `Optional<String>` tells callers, at compile time, "this might not have a value, and you must consciously handle that." It doesn't eliminate `null` from the language, but it gives a much better tool for the specific case of "a method may or may not have a result to return."

# Creating an `Optional`

```java
import java.util.Optional;

Optional<String> present = Optional.of("hello");        // throws NullPointerException immediately if the argument IS null
Optional<String> empty = Optional.empty();                // explicitly empty
Optional<String> maybeNull = Optional.ofNullable(getValueThatMightBeNull());  // wraps null safely into empty()
```

--> **`Optional.of` vs `Optional.ofNullable`** -- `of` is a deliberate assertion "I know this is not null" and fails LOUDLY and IMMEDIATELY if that assumption is wrong (fail-fast, easier to debug than a `NullPointerException` surfacing far from its true cause). `ofNullable` is for values that genuinely might be null and should become `Optional.empty()` in that case, without throwing.

# Checking and Retrieving Values

```java
Optional<String> opt = Optional.of("value");

if (opt.isPresent()) {           // imperative check, pre-Java 9 style
    System.out.println(opt.get());
}

opt.ifPresent(v -> System.out.println("Value: " + v));       // functional style, preferred

opt.ifPresentOrElse(                                            // Java 9+
        v -> System.out.println("Present: " + v),
        () -> System.out.println("Was empty")
);

boolean absent = opt.isEmpty();    // Java 11+ -- clearer than !isPresent()
```

--> **`.get()` is a trap if called blindly.** Calling `.get()` on an empty `Optional` throws `NoSuchElementException` -- using `.get()` without first checking `isPresent()` (or, better, avoiding `.get()` altogether) reintroduces the exact same "might blow up at runtime" problem `Optional` was meant to solve, just with a different exception type. **`.get()` should be rare in well-written code.**

# Safely Extracting a Value -- The Preferred Idioms

```java
Optional<String> opt = Optional.ofNullable(fetchNameFromDatabase());

String value1 = opt.orElse("default");                         // eager -- "default" is always CONSTRUCTED, even if opt is present
String value2 = opt.orElseGet(() -> computeExpensiveDefault());  // lazy -- supplier only runs if opt IS empty
String value3 = opt.orElseThrow();                               // Java 10+ -- throws NoSuchElementException if empty
String value4 = opt.orElseThrow(() -> new IllegalStateException("name required"));  // custom exception
```

--> **`orElse` vs `orElseGet` -- a genuine performance and correctness trap.** `orElse(x)` ALWAYS evaluates `x`, even when the `Optional` is present and `x` will be thrown away -- if `x` is an expensive call (e.g. a database query or object construction with side effects), that cost is paid unconditionally. `orElseGet(supplier)` only invokes the supplier when actually needed.

```java
// WRONG (subtle bug/perf issue) -- expensiveDefault() runs EVERY time, even when opt has a value
String result = opt.orElse(expensiveDefault());

// RIGHT -- the lambda only executes if opt is actually empty
String result2 = opt.orElseGet(() -> expensiveDefault());
```

# Transforming `Optional` Values -- `map`, `filter`, `flatMap`

```java
Optional<String> name = Optional.of("  alice  ");

Optional<String> trimmed = name.map(String::trim);                  // Optional["alice"]
Optional<Integer> length = name.map(String::trim).map(String::length);  // Optional[5]

Optional<String> filtered = name.map(String::trim).filter(s -> s.length() > 10);  // Optional.empty -- fails the predicate
```

--> If the wrapped value is null-transformed to null by `map`, the result correctly becomes `Optional.empty()` -- but if the mapping function ITSELF returns an `Optional` (e.g. another method that returns `Optional<T>`), plain `map` would produce a nested `Optional<Optional<T>>`, which is almost never what's wanted. Use `flatMap` in that case, exactly like the Stream API's `flatMap`.

```java
record Address(String city) {}
record Person(String name, Optional<Address> address) {}

Person person = new Person("Alice", Optional.of(new Address("NYC")));

// map would give Optional<Optional<String>> here -- wrong
Optional<String> cityWrong = person.address().map(a -> a.city() == null ? Optional.<String>empty() : Optional.of(a.city()));

// Better: keep the model simple -- Address::city returning a plain String, chained with flatMap on Person's address
Optional<String> city = person.address().map(Address::city);   // fine here since city() returns String, not Optional<String>
```

# Chaining Optional Pipelines -- Replacing Null-Check Chains

```java
// Old-style nested null checks
String cityUpperOld;
if (person != null) {
    Address addr = person.getAddress();
    if (addr != null && addr.getCity() != null) {
        cityUpperOld = addr.getCity().toUpperCase();
    } else {
        cityUpperOld = "UNKNOWN";
    }
} else {
    cityUpperOld = "UNKNOWN";
}

// Optional-chained equivalent -- flat, linear, no nested ifs
String cityUpper = Optional.ofNullable(person)
        .map(Person::getAddress)
        .map(Address::getCity)
        .map(String::toUpperCase)
        .orElse("UNKNOWN");
```

--> This is `Optional`'s strongest use case: a chain of "if this exists, get the next thing from it" navigations through a nested object graph, replacing a pyramid of `if (x != null)` checks with a single flat, readable pipeline that short-circuits to empty the moment any step is absent.

# `Optional` and Streams

```java
import java.util.stream.*;

List<Optional<String>> optionals = List.of(Optional.of("a"), Optional.empty(), Optional.of("b"));

// Java 9+ -- Optional.stream() turns a present value into a one-element Stream, empty into a zero-element Stream
List<String> presentValues = optionals.stream()
        .flatMap(Optional::stream)
        .collect(Collectors.toList());
System.out.println(presentValues);   // [a, b]
```

# Common Anti-Patterns -- What NOT to Do With `Optional`

--> **Anti-pattern 1: `Optional` as a field type.** `Optional` is not `Serializable` and was designed specifically as a RETURN TYPE for methods that may not have a result -- using it for class fields, constructor parameters, or method parameters adds indirection and boxing overhead without benefit, and often confuses tooling (Java Bean conventions, ORMs, serialization frameworks).

```java
// AVOID
class UserAvoid {
    private Optional<String> middleName;   // don't do this
}

// PREFER -- keep the field nullable, expose Optional only through the accessor if needed
class UserPrefer {
    private String middleName;             // may be null internally
    public Optional<String> getMiddleName() {
        return Optional.ofNullable(middleName);
    }
}
```

--> **Anti-pattern 2: Calling `.get()` without checking, or immediately after `isPresent()` in a way that's no better than a null check.**

```java
// AVOID -- barely better than a null check, and easy to forget the isPresent() guard elsewhere
if (opt.isPresent()) {
    System.out.println(opt.get());
}

// PREFER -- functional style makes the "only if present" logic impossible to skip accidentally
opt.ifPresent(System.out::println);
```

--> **Anti-pattern 3: Wrapping already-correct code in `Optional` just to "look modern."** If a value can never legitimately be absent, `Optional` adds ceremony and allocation for no benefit -- reserve it for genuinely optional results.
--> **Anti-pattern 4: Using `Optional` for collections.** An empty `List` already represents "no results" perfectly well -- never return `Optional<List<T>>`; return a plain (possibly empty) `List<T>` instead.

```java
// AVOID
Optional<List<String>> findMatchesAvoid(String query) { ... }

// PREFER -- an empty list already means "nothing found," no Optional needed
List<String> findMatchesPrefer(String query) { ... }   // return List.of() when nothing matches
```

--> **Anti-pattern 5: Using `Optional` in performance-critical hot loops.** Every `Optional` is a heap-allocated wrapper object -- in tight loops processing millions of primitives, this allocation overhead can matter; `OptionalInt`, `OptionalLong`, and `OptionalDouble` exist as primitive-specialized alternatives that avoid boxing the contained value (though they still allocate the wrapper itself).

```java
import java.util.OptionalInt;

OptionalInt maybeMax = IntStream.of(3, 7, 2).max();   // OptionalInt, not Optional<Integer>
int result = maybeMax.orElse(-1);
```

--> **Anti-pattern 6: Throwing away the "why" when converting to an exception.** `orElseThrow(() -> new CustomException("reason"))` should carry a MEANINGFUL message about what was missing and why -- a bare `orElseThrow()` (no-arg, Java 10+) throws a generic `NoSuchElementException` with no context, which is fine for quick prototyping but poor for production diagnostics.

# `Optional` Method Quick Reference

| Method | Purpose |
|---|---|
| `Optional.of(v)` | Wrap a known-non-null value; throws NPE if `v` is null |
| `Optional.ofNullable(v)` | Wrap a possibly-null value; becomes `empty()` if `v` is null |
| `Optional.empty()` | An explicitly empty Optional |
| `isPresent()` / `isEmpty()` | Boolean checks (prefer functional alternatives below when possible) |
| `get()` | Unsafe direct access -- throws if empty; avoid outside of guarded contexts |
| `orElse(x)` | Return value or `x`; `x` always evaluated eagerly |
| `orElseGet(supplier)` | Return value or lazily-computed supplier result |
| `orElseThrow()` / `orElseThrow(supplier)` | Return value or throw (default or custom exception) |
| `ifPresent(consumer)` | Run a consumer only if present |
| `ifPresentOrElse(consumer, runnable)` | Run one branch or the other (Java 9+) |
| `map(fn)` | Transform the contained value if present |
| `flatMap(fn)` | Transform with an `Optional`-returning function, avoiding nested Optionals |
| `filter(predicate)` | Keep the value only if it passes the predicate, else become empty |
| `stream()` | Convert to a 0- or 1-element Stream (Java 9+) |
| `or(supplier)` | Return this Optional, or another Optional from the supplier if empty (Java 9+) |

# Best Practices Summary

--> Use `Optional` as a **return type** for methods where "no result" is a normal, expected outcome -- not for every nullable value everywhere.
--> Never call `.get()` without a preceding presence check, and prefer avoiding it entirely via `map`/`orElse`/`ifPresent` chains.
--> Prefer `orElseGet` over `orElse` whenever the default is expensive to compute or has side effects.
--> Don't use `Optional` for fields, parameters, or collections.
--> `Optional` doesn't replace defensive coding everywhere -- for values that should genuinely never be null (invariants enforced by the type system and constructors), plain non-null values with validation at construction time remain the better tool.
