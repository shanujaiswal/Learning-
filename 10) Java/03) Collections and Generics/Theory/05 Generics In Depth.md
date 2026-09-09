# Why Generics Exist

--> Before Java 5, containers stored `Object`, forcing casts everywhere and pushing type errors from compile-time to RUNTIME (`ClassCastException`). **Generics** let classes, interfaces, and methods be parameterized by TYPE, so the compiler enforces type correctness and casts become unnecessary.
--> Three goals generics achieve simultaneously: (1) **type safety** -- catch type mismatches at compile time, (2) **elimination of casts** -- code reads cleaner and never risks a runtime `ClassCastException` from a bad cast, (3) **generic algorithms** -- write one method that works correctly across many types (`Collections.sort()` works on `List<T>` for any `Comparable T`).

```java
List rawList = new ArrayList();
rawList.add("hello");
String s = (String) rawList.get(0);   // manual cast required, can fail at runtime

List<String> typedList = new ArrayList<>();
typedList.add("hello");
String s2 = typedList.get(0);          // no cast needed -- compiler already knows the type
```

# Generic Classes

--> A generic class declares one or more TYPE PARAMETERS in angle brackets after the class name -- conventionally single uppercase letters: `T` (Type), `E` (Element), `K`/`V` (Key/Value), `R` (Return), `N` (Number).

```java
class Box<T> {
    private T content;

    public void set(T content) { this.content = content; }
    public T get() { return content; }
}

Box<String> stringBox = new Box<>();
stringBox.set("hello");
String value = stringBox.get();     // no cast needed

Box<Integer> intBox = new Box<>();
intBox.set(42);
// intBox.set("wrong type");        // COMPILE ERROR -- caught immediately
```

--> Multiple type parameters are common too, e.g. `Map<K, V>`:

```java
class Pair<K, V> {
    private final K key;
    private final V value;
    public Pair(K key, V value) { this.key = key; this.value = value; }
    public K getKey()   { return key; }
    public V getValue() { return value; }

    @Override
    public String toString() { return "(" + key + ", " + value + ")"; }
}

Pair<String, Integer> entry = new Pair<>("age", 30);
System.out.println(entry);   // (age, 30)
```

# Generic Methods

--> A method can be generic independently of whether its class is generic -- the type parameter is declared BEFORE the return type, and is typically inferred from the argument at the call site.

```java
class Utils {
    // <T> here declares the method's own type parameter, separate from any class-level one
    static <T> T firstElement(List<T> list) {
        return list.get(0);
    }

    static <T> void swap(T[] array, int i, int j) {
        T temp = array[i];
        array[i] = array[j];
        array[j] = temp;
    }

    // Multiple type parameters in a method
    static <K, V> Pair<K, V> makePair(K key, V value) {
        return new Pair<>(key, value);
    }
}

String first = Utils.firstElement(List.of("a", "b", "c"));   // T inferred as String
Integer firstNum = Utils.firstElement(List.of(1, 2, 3));      // T inferred as Integer

Pair<String, Integer> p = Utils.<String, Integer>makePair("x", 1);  // explicit type witness (rarely needed)
Pair<String, Integer> p2 = Utils.makePair("x", 1);                   // usually inference suffices
```

# Bounded Type Parameters

--> A bound RESTRICTS what types can be substituted for a type parameter, using `extends` (for classes AND interfaces alike, in generics `extends` means "is a subtype of" or "implements") -- this also gives the compiler knowledge of the bound's methods, so they can be called directly.

```java
// Only types that implement Comparable<T> can be used -- lets us safely call compareTo()
class MaxFinder<T extends Comparable<T>> {
    static <T extends Comparable<T>> T max(List<T> list) {
        T max = list.get(0);
        for (T item : list) {
            if (item.compareTo(max) > 0) {    // compareTo() is only guaranteed to exist because of the bound
                max = item;
            }
        }
        return max;
    }
}

System.out.println(MaxFinder.max(List.of(3, 7, 2, 9, 4)));   // 9

// Multiple bounds: T must satisfy ALL of them (at most one can be a class, must be listed first)
static <T extends Number & Comparable<T>> T maxNumeric(List<T> list) {
    T max = list.get(0);
    for (T item : list) {
        if (item.compareTo(max) > 0) max = item;
    }
    return max;
}
```

# Wildcards -- `?`, `? extends T`, `? super T`

--> A wildcard `?` represents an UNKNOWN type, used when you don't need to name the type parameter, typically in method PARAMETERS (rarely useful as a return type or field type).

```text
List<?>            -- unbounded wildcard: a list of SOME unknown type, read-only-ish
List<? extends T>   -- upper-bounded wildcard: a list of T or any SUBTYPE of T ("producer" -- safe to READ as T)
List<? super T>     -- lower-bounded wildcard: a list of T or any SUPERTYPE of T ("consumer" -- safe to WRITE T into)
```

--> **The PECS mnemonic -- "Producer Extends, Consumer Super"** -- if a structure only PRODUCES values you read out of it, use `? extends T`; if it only CONSUMES values you put into it, use `? super T`. If it does both, don't use a wildcard at all -- use the exact type `T`.

```java
// Producer -- reading Number-compatible values OUT of the list, so "? extends Number"
static double sumAll(List<? extends Number> list) {
    double sum = 0;
    for (Number n : list) {       // safe: guaranteed to be AT LEAST a Number
        sum += n.doubleValue();
    }
    return sum;
    // list.add(5);   // COMPILE ERROR -- can't safely add: the list might actually be List<Integer>,
                       // and the compiler can't verify a generic "Number" fits into it
}

sumAll(List.of(1, 2, 3));          // List<Integer> accepted
sumAll(List.of(1.5, 2.5));         // List<Double> accepted

// Consumer -- writing Integer values INTO the list, so "? super Integer"
static void addNumbers(List<? super Integer> list) {
    list.add(1);      // safe: guaranteed the list can hold at least Integer (or a supertype)
    list.add(2);
    // Integer x = list.get(0);   // only safe to read as Object, NOT Integer -- the list could be List<Number> or List<Object>
}

List<Number> numbers = new ArrayList<>();
addNumbers(numbers);    // List<Number> accepted -- Number is a supertype of Integer
```

--> **Why you can't add to `List<? extends T>`:** the compiler doesn't know the EXACT type -- if `list` is declared `List<? extends Number>`, it could actually be pointing at a `List<Integer>` at runtime. Allowing `list.add(3.14)` (a `Double`) would corrupt that `List<Integer>` with a `Double` in it, silently breaking type safety -- so the compiler forbids ANY `add()` except `add(null)`.
--> **Unbounded `List<?>`** is used when the method genuinely doesn't care about the element type at all -- e.g. `printSize(List<?> list)` just calls `list.size()`, which needs no type information.

```java
static void printAll(List<?> list) {
    for (Object o : list) {          // elements can only be read as Object -- type is truly unknown
        System.out.println(o);
    }
}
```

# Type Erasure -- What Actually Happens at Runtime

--> Java generics are implemented via **type erasure**: generic type information exists ONLY at compile time, for the compiler's own type-checking. At runtime, ALL generic type parameters are ERASED -- `List<String>` and `List<Integer>` are literally the SAME class object at runtime (`List.class`), and the JVM has no idea what `T` was.

```java
List<String> strings = new ArrayList<>();
List<Integer> ints = new ArrayList<>();
System.out.println(strings.getClass() == ints.getClass());   // true! both are just ArrayList.class at runtime

// Compiler-inserted casts make this work transparently:
// Source:  String s = strings.get(0);
// Bytecode (conceptually): String s = (String) strings.get(0);  -- the cast IS there, just invisible to you
```

--> **What actually happens to the type parameter `T`:** it's replaced with its bound (`Object` if unbounded, or the leftmost bound if bounded, e.g. `T extends Comparable<T>` erases `T` to `Comparable`). The compiler then inserts casts wherever the erased type is used in a way that needs the real type.

```java
class Box<T> {
    private T value;                  // erases to: private Object value;
    public T get() { return value; }  // erases to: public Object get() { return value; }
                                        // call sites get an inserted cast: (T) box.get()

    public void set(T value) { this.value = value; }
}

class NumberBox<T extends Number> {
    private T value;                   // erases to: private Number value;  (bound, not Object)
}
```

--> **Consequences of erasure (the "generic gotchas"):**

```java
// 1) Cannot create an instance of a type parameter
class Factory<T> {
    T create() {
        // return new T();       // COMPILE ERROR -- the JVM doesn't know what T is at runtime
        return null;
    }
}

// 2) Cannot create a generic array directly
class Container<T> {
    // T[] array = new T[10];    // COMPILE ERROR
    @SuppressWarnings("unchecked")
    T[] array = (T[]) new Object[10];   // workaround -- unchecked cast, use with care
}

// 3) Cannot use instanceof with a parameterized type
List<String> list = new ArrayList<>();
// if (list instanceof List<String>) {}   // COMPILE ERROR -- erased at runtime, type unknown
if (list instanceof List<?>) {}            // OK -- unbounded wildcard is fine, no specific type checked

// 4) Cannot overload methods that erase to the same signature
class Overload {
    void process(List<String> list) {}
    // void process(List<Integer> list) {}   // COMPILE ERROR -- both erase to process(List)
}

// 5) Static context cannot reference a class's type parameter
class Holder<T> {
    // static T instance;              // COMPILE ERROR -- static members are shared across ALL
                                         // parameterizations (Holder<String>, Holder<Integer>, ...),
                                         // but T is only known per-instance, so it's meaningless here
}

// 6) Cannot catch a generic exception type or throw/catch parameterized checked exceptions this way
// class MyException<T> extends Exception {}   // COMPILE ERROR -- not allowed at all
```

--> **Why erasure was chosen (historical context):** Java 5 needed generics to be BINARY-COMPATIBLE with pre-generics bytecode and libraries compiled before Java 5 -- erasure lets `List<String>` compile down to the exact same bytecode shape as the old raw `List`, so old `.class` files and new generic code can interoperate. The trade-off is exactly the list of gotchas above.

# Generic Gotchas -- Deeper Notes

--> **Raw types still compile (with warnings) for backward compatibility** -- `List list = new ArrayList();` is legal but loses ALL type safety; the compiler emits an "unchecked" warning. Always use the parameterized form.
--> **Generic array creation via varargs (`heap pollution`)** -- passing a generic varargs array can silently corrupt the heap with wrong types; the compiler warns with `[unchecked]` and the `@SafeVarargs` annotation exists to suppress this warning specifically for methods verified not to misuse the array.
--> **Bridge methods** -- when a generic class is overridden with a more specific type, the compiler generates a hidden "bridge method" to preserve polymorphism after erasure; this is invisible in source but shows up if you inspect bytecode/reflection (`getClass().getMethods()` shows extra synthetic methods).
--> **Wildcards cannot be used at generic class/method DECLARATION sites** -- `class Box<? extends T>` is illegal; wildcards are only valid where a type is USED (a parameter type, variable type), not where it's declared.

# Best Practices

--> Prefer generic methods over generic classes when only one method needs the type parameter -- keeps the API smaller.
--> Follow PECS: `? extends T` for read-only/producer parameters, `? super T` for write-only/consumer parameters, plain `T` when both reading and writing are needed.
--> Avoid raw types entirely in new code -- always parameterize, even with `<?>` if the type is genuinely unknown/irrelevant.
--> Use bounded type parameters (`<T extends Comparable<T>>`) whenever the generic code needs to call methods specific to a family of types, rather than casting or reflecting.
--> Suppress `@SuppressWarnings("unchecked")` narrowly (smallest possible scope) and only after manually verifying the cast is actually safe.
