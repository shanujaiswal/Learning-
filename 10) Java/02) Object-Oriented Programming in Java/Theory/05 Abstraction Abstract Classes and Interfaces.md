# What Abstraction Actually Means

--> Abstraction is about exposing WHAT something does while hiding HOW it does it -- a caller working with a `Shape` should be able to ask for its `area()` without knowing whether it's a circle computing `pi * r^2` or a rectangle computing `width * height`. This is a different concept from encapsulation (hiding internal STATE / data behind getters and access modifiers) even though the two are frequently taught together -- abstraction hides implementation COMPLEXITY and lets you program against a contract, encapsulation hides implementation DETAILS and protects data integrity.
--> Java gives you two dedicated language tools for abstraction: ABSTRACT CLASSES and INTERFACES. Both let you define a contract that concrete code must fulfill, but they differ sharply in what else they're allowed to carry along with that contract -- state, constructors, concrete behavior, and how many of them a single class can honor at once.
--> The practical payoff is DECOUPLING -- code written against an abstract type (`List<String> list = new ArrayList<>();`) doesn't care which concrete implementation it's handed, so the concrete class can be swapped, mocked in a test, or upgraded later without touching the calling code.

# Abstract Classes

--> An abstract class is declared with the `abstract` keyword and CANNOT be instantiated directly with `new` -- it exists purely to be extended. It can mix abstract methods (no body, must be implemented by subclasses) with regular concrete methods (full implementation, inherited as-is unless overridden).

```java
abstract class AbstractShape {
    private final String name;                 // abstract classes CAN hold state

    AbstractShape(String name) {                // and CAN have constructors --
        this.name = name;                       // called via super() from subclass constructors
    }

    abstract double area();                     // no body -- every concrete subclass MUST implement this

    void describe() {                           // concrete method -- shared, inherited as-is
        System.out.printf("%s has area %.2f%n", name, area());
    }
}

class Circle extends AbstractShape {
    private final double radius;
    Circle(double radius) {
        super("Circle");
        this.radius = radius;
    }
    @Override
    double area() { return Math.PI * radius * radius; }
}
```

--> **Constructors in abstract classes** -- an abstract class's constructor never runs on its own (since you can never `new AbstractShape(...)` directly), but it DOES run every time a concrete subclass is instantiated, via an implicit or explicit `super(...)` call. This is the normal place to initialize fields that every subclass needs, so subclasses don't each have to repeat that setup.
--> **Fields** -- abstract classes can declare instance fields (state) just like any other class, and those fields are inherited by subclasses. This is one of the biggest practical differences from interfaces, covered in the comparison table below.
--> **Mixing abstract and concrete methods freely** -- a class doesn't need to be "mostly abstract" to use `abstract` -- even a class with one abstract method and ten concrete ones is a valid abstract class, and remains impossible to instantiate directly as long as even ONE abstract method remains unimplemented.
--> **A subclass that doesn't implement every abstract method must itself be declared `abstract`** -- the compiler enforces this. Only when a class provides bodies for every inherited abstract method does it become concrete and instantiable.

```java
abstract class Base {
    abstract void step1();
    abstract void step2();
}

abstract class Partial extends Base {           // still abstract -- only implements step1
    @Override void step1() { System.out.println("step1 done"); }
    // step2() still missing -- Partial MUST stay abstract, or compilation fails
}

class Complete extends Partial {                // now concrete -- fills in the last gap
    @Override void step2() { System.out.println("step2 done"); }
}
```

--> **Gotcha -- forgetting to implement an abstract method** -- if a concrete (non-abstract) class extends an abstract class and misses even one abstract method, the compiler rejects it with "X is not abstract and does not override abstract method Y" -- this is a compile-time safety net, not a runtime surprise, which is one of abstraction's biggest practical wins over duck-typed languages.

# Interfaces

--> An interface defines a pure CONTRACT -- a set of method signatures that any implementing class agrees to provide. Historically (pre-Java 8) an interface could contain ONLY abstract method signatures and `public static final` constants -- no bodies, no state, no constructors.

```java
interface Drawable {
    void draw();                    // implicitly `public abstract` -- no need to write those modifiers
    int MAX_SIZE = 100;             // implicitly `public static final` -- a constant, not a mutable field
}
```

--> **All interface methods are implicitly `public`** -- you cannot make an interface method `private`, `protected`, or package-private (with the one exception of the `private` methods added in Java 9, discussed below, which exist purely as internal helpers, not part of the contract). Writing `public abstract void draw();` explicitly is legal but redundant -- idiomatic code omits both modifiers.
--> **All interface fields are implicitly `public static final`** -- meaning interfaces cannot hold per-instance state at all. Any field is a shared, immutable constant, not something each implementing object can have its own value for.
--> **A class implements an interface with the `implements` keyword**, and can implement MULTIPLE interfaces separated by commas -- this is how Java achieves a form of multiple inheritance (of type/contract, not of implementation) despite disallowing multiple class inheritance.

```java
class Circle implements Drawable, Comparable<Circle> {
    private final double radius;
    Circle(double radius) { this.radius = radius; }

    @Override public void draw() { System.out.println("Drawing circle r=" + radius); }
    @Override public int compareTo(Circle other) { return Double.compare(radius, other.radius); }
}
```

## Default Methods (Java 8+)

--> Java 8 introduced `default` methods -- interface methods WITH a body, that implementing classes inherit automatically and can optionally override. This was added specifically to let library authors add new methods to existing interfaces (like `List` or `Comparator`) without breaking every class that already implemented them -- without default methods, adding even one new abstract method to `java.util.List` would have broken every third-party `List` implementation in existence.

```java
interface Greeter {
    String name();                                   // abstract -- must be implemented

    default void greet() {                            // default -- has a body, inherited as-is
        System.out.println("Hello, " + name() + "!");
    }
}

class Person implements Greeter {
    private final String name;
    Person(String name) { this.name = name; }
    @Override public String name() { return name; }
    // greet() is inherited for free -- no need to implement it
}
```

## Static Methods in Interfaces (Java 8+)

--> Interfaces can also declare `static` methods -- utility methods logically related to the interface, callable only through the interface name itself (`Greeter.someStaticMethod()`), never through an instance and never inherited by implementing classes.

```java
interface MathOps {
    static int square(int x) { return x * x; }        // called as MathOps.square(5), NOT instance.square(5)
}
```

--> **Gotcha -- static interface methods are NOT inherited** -- if `MathOps` has `static int square(int x)` and `class Calculator implements MathOps`, you cannot call `calculator.square(5)` or `Calculator.square(5)` -- only `MathOps.square(5)` works. This trips people up because static methods on CLASSES are inherited by subclasses, but static methods on INTERFACES are not inherited by implementers -- a genuine asymmetry in the language.

## Private Methods in Interfaces (Java 9+)

--> Java 9 added `private` (and `private static`) methods to interfaces -- pure internal helpers used to avoid duplicating code between two or more `default` methods in the same interface. They have a body, are never part of the public contract, and are invisible to implementing classes entirely.

```java
interface Validator {
    boolean isValid(String input);

    default void validateOrThrow(String input) {
        log("validating: " + input);
        if (!isValid(input)) throw new IllegalArgumentException("invalid: " + input);
    }

    default void validateSilently(String input) {
        log("validating: " + input);
        // swallow instead of throwing
    }

    private void log(String msg) {                 // private helper -- shared by both default methods above
        System.out.println("[Validator] " + msg);   // not visible outside the interface at all
    }
}
```

--> **Why this matters** -- before Java 9, if two default methods needed the same few lines of shared logic, the only options were duplicating the code or exposing it as another `default` method (bloating the public contract with something that was never meant to be called externally). Private interface methods close that gap the same way private methods close it inside ordinary classes.

# Abstract Class vs Interface -- Deep Comparison

```text
Feature                    Abstract Class                      Interface
--------------------------------------------------------------------------------------------
Instantiation               Never directly                      Never directly
Instance fields (state)     Yes, any access modifier             No (only public static final constants)
Constructors                Yes -- run via super() from subclass  No -- interfaces have no constructors
Method bodies                Concrete methods freely allowed      default / static / private only (Java 8+)
Access modifiers on methods  public, protected, private, etc.     Always public (except private helpers, 9+)
Multiple inheritance         A class extends only ONE class       A class can implement MANY interfaces
Fields mutability            Can be mutable instance fields       Always public static final (constants)
Typical use case             Shared state + partial implementation "Can-do" contracts across unrelated classes
Keyword                       extends                             implements
```

--> **When to use an abstract class** -- when subclasses share meaningful COMMON STATE or common implementation logic, and there's a genuine "is-a" hierarchy (a `Dog` and `Cat` both genuinely ARE `Animal`s, and both need a `name` field and shared `eat()` logic). Abstract classes are the right tool when you're modeling a family of closely related types with shared internals.
--> **When to use an interface** -- when you're describing a CAPABILITY that unrelated classes might share, with no shared state or implementation baggage (a `Bird`, an `Airplane`, and a `Superhero` are entirely unrelated types, but all three could reasonably implement `Flyable`). Interfaces are also the only option when a class needs to honor multiple unrelated contracts simultaneously, since Java forbids extending more than one class.
--> **A common real design**: a small abstract base class providing shared state/logic, combined with one or more interfaces layered on top for additional capabilities -- e.g. `abstract class AbstractRepository<T> implements Closeable, Iterable<T>`. This gets the benefits of both: shared implementation from the single abstract superclass, and flexible multi-contract capability from interfaces.

# Functional Interfaces and a Lambda Preview

--> A FUNCTIONAL INTERFACE is any interface with EXACTLY ONE abstract method (default and static methods don't count toward this total, since they already have bodies). This single-abstract-method (SAM) shape is what allows Java to represent an implementation of that interface as a compact LAMBDA EXPRESSION instead of writing out a full anonymous class.

```java
@FunctionalInterface                 // optional but strongly recommended -- see gotcha below
interface Calculator {
    int compute(int a, int b);       // exactly one abstract method -- qualifies as functional
}

public class Demo {
    public static void main(String[] args) {
        Calculator add = (a, b) -> a + b;         // lambda -- shorthand for implementing compute()
        Calculator multiply = (a, b) -> a * b;
        System.out.println(add.compute(2, 3));       // 5
        System.out.println(multiply.compute(2, 3));   // 6
    }
}
```

--> **`@FunctionalInterface`** is an annotation that tells the compiler "this interface must have exactly one abstract method" -- if a second abstract method is later added by mistake, compilation FAILS immediately with a clear error, instead of silently breaking every lambda that used to implement this interface. It's the same idea as `@Override` -- optional, but a cheap insurance policy that catches mistakes early.
--> **Common built-in functional interfaces** (in `java.lang` and `java.util.function`) -- `Runnable` (`void run()`, no args, no return), `Comparator<T>` (`int compare(T a, T b)`), `Comparable<T>` (`int compareTo(T o)` -- technically single-method but conventionally not marked `@FunctionalInterface` since it's meant to be implemented by regular classes), `Supplier<T>` (`T get()`), `Consumer<T>` (`void accept(T t)`), `Function<T,R>` (`R apply(T t)`), `Predicate<T>` (`boolean test(T t)`). These are covered in depth in the Lambdas and Functional Interfaces chapter -- this is just enough to recognize the shape here.
--> Lambdas work for functional interfaces specifically BECAUSE there's only one method to implement -- the compiler can unambiguously match the lambda's parameter list and body to that single abstract method's signature. With two or more abstract methods, the compiler wouldn't know which one the lambda body is supposed to satisfy, which is exactly why the single-method rule exists.

# The Diamond Problem and How Java Resolves It

--> The classic "diamond problem" in multiple inheritance: if class `D` inherits from both `B` and `C`, and both `B` and `C` inherit a method from common ancestor `A` but override it differently, which version does `D` get? Java sidesteps this entirely for CLASSES by simply disallowing multiple class inheritance (`extends` accepts only one class). But `default` methods reopened a narrower version of the same problem for INTERFACES, since a class can implement multiple interfaces that each provide a conflicting default implementation of the same method.

```java
interface Left {
    default void greet() { System.out.println("Hello from Left"); }
}

interface Right {
    default void greet() { System.out.println("Hello from Right"); }
}

// class Both implements Left, Right { }        // COMPILE ERROR -- ambiguous greet(), must resolve explicitly

class Both implements Left, Right {
    @Override
    public void greet() {                        // explicit override REQUIRED to resolve the conflict
        Left.super.greet();                       // can explicitly choose Left's version...
        Right.super.greet();                      // ...and/or Right's version...
        System.out.println("Hello from Both");    // ...and/or provide entirely new behavior
    }
}
```

--> **Java's resolution rule** -- when two or more implemented interfaces provide conflicting `default` methods with the same signature, the class implementing both is FORCED to override the method explicitly and pick (or combine) a resolution -- the compiler refuses to guess, unlike the ambiguity that plagues C++'s multiple inheritance. This turns a silent, surprising runtime ambiguity into a loud, compile-time-enforced decision.
--> **The `Interface.super.method()` syntax** exists specifically for this situation -- it lets the overriding method still reach back and invoke one (or both) of the original default implementations, rather than being forced to reimplement everything from scratch.
--> **No conflict if only one interface provides a default** -- if `Left` has a `default greet()` but `Right` only declares `void greet();` (abstract, no body), there's no ambiguity, and the implementing class can simply inherit `Left`'s default without any explicit override required.

# Resolution Order -- Class Wins Over Interface

--> When a class extends an abstract (or concrete) class AND implements an interface, and BOTH provide behavior for the same method name/signature, Java has a strict, unambiguous priority: **the superclass's method always wins over any interface's default method** -- "class always wins." This rule exists precisely so that adding a `default` method to an interface can never silently change the behavior of a class that already inherited a concrete implementation from its superclass.

```java
class Animal {
    void makeSound() { System.out.println("Some generic animal sound"); }
}

interface SoundMaker {
    default void makeSound() { System.out.println("Sound from interface"); }
}

class Dog extends Animal implements SoundMaker {
    // no override needed -- Animal's makeSound() wins automatically, interface default is ignored
}

public class Test {
    public static void main(String[] args) {
        new Dog().makeSound();     // prints "Some generic animal sound" -- superclass wins, no ambiguity
    }
}
```

--> This "class wins" rule ONLY resolves class-vs-interface conflicts -- it does NOT resolve interface-vs-interface conflicts (two default methods from two different interfaces, neither of which is a superclass), which is exactly the diamond scenario above that still forces an explicit override.

# Common Gotchas Recap

--> **Forgetting to implement all abstract methods** -- whether from an abstract class or an interface, any unimplemented abstract method forces the implementing class to also be declared `abstract`, or the compiler rejects it outright.
--> **Assuming interface fields are mutable** -- `int MAX = 10;` inside an interface is implicitly `public static final`, so any attempt to reassign it (`MAX = 20;`) is a compile error, even though the field declaration doesn't visually scream "constant" the way it would in a class.
--> **Assuming static interface methods are inherited like static class methods** -- they are not; they must always be called through the interface name.
--> **Forgetting `@Override` when resolving a diamond conflict** -- the explicit override in the implementing class must match the exact signature, and omitting `@Override` (while not fatal) removes the compiler's ability to catch a typo'd signature that would otherwise silently create a brand new, unrelated method instead of overriding.
--> **Trying to instantiate an abstract class or interface directly** -- `new AbstractShape()` or `new Drawable()` is always a compile error; only `new` on a concrete class works, even via an anonymous class expression (`new Drawable() { ... }`), which is really creating an anonymous concrete subclass on the fly, not instantiating the interface itself.

# Deep Dive -- Why Interfaces Still Can't Hold Instance State

--> Even after Java 8 and 9 added default, static, and private methods to interfaces, instance fields were deliberately NOT added -- every field in an interface remains `public static final`. This is a deliberate design boundary, not an oversight: allowing per-instance mutable state in interfaces would blur interfaces and abstract classes into the same feature, and would reopen exactly the kind of "diamond" ambiguity Java avoided for classes -- if two interfaces each defined a mutable `int count` field and a class implemented both, which `count` would a shared method reference? Keeping interfaces state-free means every conflict interfaces can introduce is a BEHAVIOR conflict (resolvable via explicit override, as shown above), never a hidden DATA conflict.
--> This is also why default methods, despite having bodies, can only manipulate state through the ABSTRACT methods the implementing class defines (like the `Greeter` example calling `name()`) -- a default method has no field of its own to read or write, only the ability to call other methods on `this`.

# Deep Dive -- Sealed Interfaces (Java 17)

--> Java 17 introduced `sealed` interfaces and classes, which restrict which OTHER types are allowed to implement or extend them, using a `permits` clause -- this adds a middle ground between "any class can implement this" (a normal interface) and "nothing can extend this" (a `final` class).

```java
sealed interface Shape permits Circle, Square, Triangle { }

final class Circle implements Shape { }
final class Square implements Shape { }
non-sealed class Triangle implements Shape { }   // explicitly reopens extensibility for this one branch
```

--> This matters for abstraction because it lets a `switch` expression over a sealed interface's implementations be checked EXHAUSTIVELY by the compiler (no `default` branch required, since the compiler knows the complete, closed set of possible subtypes) -- a modern complement to traditional open-ended interfaces, useful when you deliberately want a fixed, known family of implementations rather than an open contract anyone can implement.
