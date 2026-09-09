# Enums -- Type-Safe Fixed Sets of Constants

--> An `enum` defines a fixed, known-at-compile-time set of named constants -- a day of the week, a card suit, an HTTP status category. Before enums existed (pre-Java 5), this was faked with `public static final int` constants, which had no type safety at all -- a method expecting a "day" constant would happily accept any random `int`, including ones that meant nothing. An `enum` closes that hole completely -- the compiler only accepts genuine members of that enum type.

```java
enum Day {
    MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY
}

Day today = Day.WEDNESDAY;
// Day today = 3;               // COMPILE ERROR -- an int is not a Day, unlike the old int-constant approach
```

--> **Every enum implicitly extends `java.lang.Enum`**, which is why enums cannot extend any other class (Java has only single class inheritance, and that one slot is already used) -- but enums CAN still implement interfaces, covered below.
--> **Enum constants are singletons** -- there is exactly ONE instance of `Day.MONDAY` for the entire running program, created once when the enum class is first loaded, which makes `==` comparison between enum constants both safe and idiomatic (unlike `String`, where `==` is a classic trap).

## Enums with Fields, Constructors, and Methods

--> Enum constants aren't limited to bare names -- each constant can carry its own data by calling a constructor, exactly like constructing an object, because under the hood each enum constant genuinely IS an object of that enum type.

```java
enum Planet {
    MERCURY(3.303e+23, 2.4397e6),
    VENUS(4.869e+24, 6.0518e6),
    EARTH(5.976e+24, 6.37814e6);

    private final double mass;              // kg
    private final double radius;             // meters

    Planet(double mass, double radius) {     // enum constructors are implicitly private -- never called with `new`
        this.mass = mass;
        this.radius = radius;
    }

    double surfaceGravity() {
        final double G = 6.67300E-11;
        return G * mass / (radius * radius);
    }
}

double g = Planet.EARTH.surfaceGravity();    // ~9.8
```

--> **Enum constructors are always implicitly `private`** (or at most package-private) -- you cannot write `public Planet(...)` and you can never call `new Planet(...)` from outside the enum, since the whole point is a fixed, closed set of instances created exactly once at class-loading time.
--> **The constant list must come first**, terminated by a semicolon, before any fields, constructors, or methods -- `MERCURY(...), VENUS(...), EARTH(...);` then the rest of the class body.

## Abstract Methods Per-Constant

--> An enum can declare an `abstract` method, and require EACH constant to supply its own implementation via a constant-specific class body -- this is one of the most powerful and underused features of Java enums, replacing what would otherwise be a `switch` statement scattered across the codebase with contained, per-constant behavior.

```java
enum Operation {
    ADD {
        @Override public int apply(int a, int b) { return a + b; }
    },
    SUBTRACT {
        @Override public int apply(int a, int b) { return a - b; }
    },
    MULTIPLY {
        @Override public int apply(int a, int b) { return a * b; }
    };

    public abstract int apply(int a, int b);   // every constant above supplies its own body
}

int sum = Operation.ADD.apply(3, 4);            // 7
```

--> **Why this beats a switch statement** -- adding a new `Operation` constant with a switch-based design means finding and updating every switch statement that handles `Operation` throughout the codebase (easy to forget one). With abstract-method-per-constant, forgetting to implement `apply()` for a new constant is a COMPILE ERROR, not a silent runtime gap.

## Built-In Enum Methods

```text
values()        -- static method, returns an array of every constant in declaration order
valueOf(String)  -- static method, parses a String into its matching constant (throws IllegalArgumentException if no match)
name()          -- returns the constant's exact declared name as a String (e.g. "MONDAY")
ordinal()       -- returns the constant's position in the declaration order, starting at 0
compareTo()     -- inherited from Enum, compares by ordinal position
```

```java
for (Day d : Day.values()) {                 // values() -- iterate every constant
    System.out.println(d.name() + " = " + d.ordinal());
}

Day parsed = Day.valueOf("FRIDAY");           // FRIDAY
// Day.valueOf("Friday");                     // throws IllegalArgumentException -- case-sensitive exact match
```

--> **Gotcha -- relying on `ordinal()` for business logic** -- `ordinal()` reflects declaration ORDER, which is fragile: inserting a new constant in the middle of the list silently shifts every ordinal after it. Never persist an `ordinal()` value to a database or use it for meaningful comparisons unless the declaration order is truly guaranteed permanent -- prefer an explicit field instead (like `Planet`'s `mass`/`radius` above) for anything that needs a stable, meaningful value.

## Enums Implementing Interfaces

--> Since enums cannot `extend` another class but CAN `implements` interfaces, this is the standard way to give a family of enum constants a shared contract usable polymorphically alongside non-enum types.

```java
interface Describable {
    String describe();
}

enum Status implements Describable {
    ACTIVE, INACTIVE, SUSPENDED;

    @Override public String describe() { return "Status: " + name(); }
}
```

## EnumMap and EnumSet

--> `EnumMap<K, V>` and `EnumSet<E>` (in `java.util`) are specialized collection implementations restricted to enum keys/elements -- internally backed by an array indexed by `ordinal()`, making them significantly faster and more memory-efficient than a general-purpose `HashMap`/`HashSet` for enum-keyed data, while also iterating in the enum's natural declaration order automatically.

```java
EnumMap<Day, String> schedule = new EnumMap<>(Day.class);
schedule.put(Day.MONDAY, "Standup meeting");

EnumSet<Day> weekend = EnumSet.of(Day.SATURDAY, Day.SUNDAY);
EnumSet<Day> weekdays = EnumSet.complementOf(weekend);   // everything NOT in weekend
```

--> These are worth reaching for whenever a `Map` or `Set` is keyed purely by enum constants -- there's essentially no downside versus `HashMap`/`HashSet` in that specific situation.

## Enums in Switch Statements

--> Enums pair especially well with `switch`, since the constant names alone (without the enum type prefix) are valid case labels, and modern Java's enhanced switch can be checked for exhaustiveness by the compiler.

```java
String describeDay(Day d) {
    return switch (d) {                            // enhanced switch expression (Java 14+)
        case SATURDAY, SUNDAY -> "Weekend";
        case MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY -> "Weekday";
    };
    // No `default` needed -- the compiler knows Day has exactly 7 constants, all covered above
}
```

--> **Gotcha -- adding a case for every constant in old-style switch statements** -- with the classic `switch (d) { case MONDAY: ... }` syntax (no arrow, no exhaustiveness checking), the compiler does NOT warn about missing cases unless a `default` is required by other rules -- silently falling through to no matching case (or an unintended `default`) is a classic maintenance bug when a new enum constant is added later. The modern arrow-based `switch` expression closes this gap by making the compiler enforce exhaustiveness.

# Records (Java 16+)

--> A `record` is a compact way to declare an IMMUTABLE data-carrier class -- the compiler automatically generates a canonical constructor, private final fields, public accessor methods (named after the field, not `getX()`), and correct `equals()`, `hashCode()`, and `toString()` implementations, all from one line.

```java
record Point(int x, int y) { }

Point p1 = new Point(3, 4);
System.out.println(p1.x());          // 3 -- accessor named x(), not getX()
System.out.println(p1.y());          // 4
System.out.println(p1);              // Point[x=3, y=4] -- auto-generated toString()

Point p2 = new Point(3, 4);
System.out.println(p1.equals(p2));    // true -- auto-generated equals() compares all components
System.out.println(p1 == p2);         // false -- still two distinct objects, just equal by value
```

--> **What the compiler generates for `record Point(int x, int y)`**:
```text
- A canonical constructor:   Point(int x, int y)
- Accessor methods:          int x()   and   int y()      (NOT getX()/getY() -- no "get" prefix)
- equals(Object o):          true if same record type AND all components are equal
- hashCode():                consistent with equals() -- combines all component hash codes
- toString():                "Point[x=3, y=4]" style, listing every component
- private final fields for every component -- records are immutable, no setters are generated at all
```

--> **Records are implicitly `final`** -- a record can never be extended, and cannot itself extend another class (it implicitly extends `java.lang.Record`) -- similar to the enum restriction, and for a similar reason: a record's entire identity is its fixed set of components, and allowing subclassing would undermine the guarantee that a record's `equals()`/`hashCode()`/`toString()` faithfully represent its complete state.
--> **Records CAN implement interfaces** -- this is how you give a record additional behavior or make it usable polymorphically, since it can't gain that through inheritance.

```java
interface HasArea {
    double area();
}

record Rectangle(double width, double height) implements HasArea {
    @Override public double area() { return width * height; }
}
```

## Compact Constructors -- Validation Without Repeating the Field List

--> A COMPACT constructor lets you add validation or normalization logic without re-declaring the parameter list -- you write just the body, and the implicit assignment of each parameter to its matching field still happens automatically afterward.

```java
record Range(int min, int max) {
    Range {                                    // compact constructor -- no parameter list repeated
        if (min > max) {
            throw new IllegalArgumentException("min (" + min + ") > max (" + max + ")");
        }
        // no explicit `this.min = min;` needed -- happens automatically after this block runs
    }
}

new Range(1, 10);      // fine
// new Range(10, 1);   // throws IllegalArgumentException -- compact constructor validated it
```

--> **Compact constructor vs canonical (full) constructor** -- you can ALSO write a full canonical constructor with an explicit parameter list and explicit `this.min = min;` assignments, which gives full control (including transforming values, not just validating them), but then you're responsible for assigning every field yourself. The compact form is preferred whenever you're only validating or lightly normalizing input, since it's shorter and the compiler still fills in the mechanical assignment step.

```java
record Range(int min, int max) {
    Range {                                     // compact -- can also NORMALIZE, not just validate
        if (min > max) {
            int tmp = min; min = max; max = tmp;  // reassigning the parameter is allowed here,
        }                                          // and flows into the auto-generated field assignment
    }
}
```

--> **Gotcha -- confusing compact and canonical constructors** -- a compact constructor has NO parentheses/parameter list (`Range { ... }`), while a canonical constructor repeats the full parameter list and must assign every field explicitly (`Range(int min, int max) { this.min = min; this.max = max; }`). Writing a canonical constructor that FORGETS to assign a field is a compile error (all fields must be definitely assigned), whereas a compact constructor can never have that specific bug, since the assignment is automatic.
--> Records can also declare additional (overloaded) constructors beyond the canonical one, but every additional constructor must ultimately delegate to the canonical constructor via `this(...)`, directly or indirectly.

## Records vs Classes -- When to Use Which

```text
Aspect                  Record                                  Regular Class
--------------------------------------------------------------------------------------------
Mutability               Always immutable (fields are final)      Mutable or immutable, your choice
Boilerplate               Auto-generated (ctor/accessors/equals)   All written by hand (or via IDE/Lombok)
Inheritance                Implicitly final, can't extend classes   Can extend and be extended freely
Best for                   Plain data carriers, DTOs, value types    Entities with identity, behavior, mutable state
Accessor naming             x() -- no "get" prefix                    getX() by convention (JavaBeans style)
```

--> Reach for a record whenever a type's entire purpose is to bundle a small handful of values together with no meaningful identity beyond those values (a coordinate pair, a money amount + currency, a query result row). Reach for a regular class when a type has genuine identity distinct from its current field values, needs mutable state, or needs to participate in a class hierarchy.

# Nested Classes -- The Four Kinds

--> Java allows classes to be declared inside other classes (or even inside methods). There are four distinct flavors, each with different rules about what they can access and how they're instantiated.

## Static Nested Classes

--> Declared with `static` inside an outer class -- behaves like a completely ordinary top-level class that simply happens to be namespaced inside another class. It has NO implicit reference to any outer instance, and can be instantiated without ever creating an instance of the outer class.

```java
class Outer {
    static class Node {                       // static nested class -- no link to any Outer instance
        int value;
        Node(int value) { this.value = value; }
    }
}

Outer.Node node = new Outer.Node(42);          // no Outer instance required at all
```

--> Static nested classes are the most common choice when a helper class logically belongs to (and is namespaced under) an outer class, but doesn't need access to that outer instance's state -- classic examples are `Map.Entry`, builder classes, and linked data-structure node classes.

## Non-Static Inner Classes

--> Declared WITHOUT `static` -- every instance of an inner class is implicitly tied to exactly one enclosing instance of the outer class, and can freely access that outer instance's fields and methods (even private ones), through an implicit reference conventionally written `Outer.this`.

```java
class Outer {
    private int value = 10;

    class Inner {                              // non-static -- carries an implicit reference to an Outer instance
        void show() {
            System.out.println("Outer's value = " + value);       // direct access to Outer's private field
            System.out.println("Explicit: " + Outer.this.value);   // same thing, written explicitly
        }
    }
}

Outer outer = new Outer();
Outer.Inner inner = outer.new Inner();          // must be created THROUGH an Outer instance -- note the syntax
inner.show();
```

--> **The `outer.new Inner()` syntax** is required specifically because a non-static inner class instance cannot exist without a bound outer instance -- there is no way to construct one "from nothing."
--> **Gotcha -- implicit outer reference and memory leaks** -- every non-static inner class instance holds a hidden strong reference to its enclosing outer instance, for as long as the inner instance is reachable. If an inner class instance is handed off somewhere long-lived (registered as an event listener, stored in a long-lived collection, etc.), it can keep the ENTIRE outer object alive far longer than intended, even if nothing else references the outer object directly -- a classic, hard-to-spot memory leak, especially notorious with inner classes used as GUI event listeners. Prefer a `static` nested class (or a static nested class holding an explicit, possibly weak, reference) whenever the inner class doesn't actually need outer-instance access.

## Local Classes

--> A class declared INSIDE a method body -- scoped entirely to that method, invisible outside it, and able to access the enclosing method's local variables (as long as those variables are effectively final -- never reassigned after initialization).

```java
class Outer {
    void process(int threshold) {
        class Filter {                          // local class -- scoped to this method only
            boolean passes(int value) {
                return value > threshold;         // captures the enclosing method's parameter
            }
        }
        Filter f = new Filter();
        System.out.println(f.passes(15));
    }
}
```

--> Local classes are relatively rare in modern code -- most of their historical use cases (implementing a small one-off interface inline) are now better served by lambdas (for functional interfaces) or anonymous classes (for everything else), covered next. They're still occasionally useful when you need a full class with multiple methods or its own fields, scoped tightly to one method.

## Anonymous Classes

--> A class with no name at all, declared and instantiated in a single expression -- typically used to provide a one-off implementation of an interface or a one-off subclass of an abstract/concrete class, inline, exactly where it's needed.

```java
interface ClickHandler {
    void onClick();
}

ClickHandler handler = new ClickHandler() {        // anonymous class -- implements ClickHandler inline
    @Override
    public void onClick() {
        System.out.println("Clicked!");
    }
};
handler.onClick();
```

--> An anonymous class can also extend a concrete class, overriding or adding behavior for just that one instance:

```java
AbstractShape unitSquare = new AbstractShape("AdHocSquare") {
    @Override
    double area() { return 1.0; }
};
```

--> **Anonymous classes vs lambdas** -- for interfaces with EXACTLY one abstract method (functional interfaces), a lambda is almost always preferred today -- shorter, and doesn't carry the anonymous class's own separate `this`. Anonymous classes remain necessary when implementing an interface with more than one abstract method, or when extending a concrete/abstract class (lambdas can only implement functional interfaces, never extend a class).
--> Like local classes, anonymous classes can capture effectively-final local variables from their enclosing scope.

## Comparison Table -- Which Nested Class to Use

```text
Kind                  Outer instance link?   Named?   Can extend a class?   Typical use
--------------------------------------------------------------------------------------------
Static nested class    No                     Yes      Yes                   Namespaced helper, no outer access needed
Inner class (non-static) Yes (implicit)        Yes      Yes                   Needs tight, ongoing access to outer state
Local class             Yes (if non-static ctx) Yes     Yes                   Multi-method one-off, scoped to one method
Anonymous class         Yes (if non-static ctx) No      Yes (or implements)   Single one-off implementation, inline
```

--> **Rule of thumb** -- default to a `static` nested class unless you specifically need access to the enclosing instance's state, in which case use a non-static inner class but stay alert to its lifetime/memory implications. Reach for a lambda first for single-method interface implementations; fall back to an anonymous class only when a lambda can't apply (multiple abstract methods, or extending a class); reach for a local class only in the rare case where a fully named, multi-method, method-scoped helper is genuinely clearer than the alternatives.

# Common Gotchas Recap

--> **Relying on `ordinal()` for meaningful values or persistence** -- fragile against reordering; use an explicit field instead.
--> **Inner class implicit outer reference causing memory leaks** -- long-lived references to non-static inner class instances keep their outer instance alive; prefer static nested classes when outer access isn't needed.
--> **Old-style switch on enums silently missing a case** -- adding a new enum constant doesn't force old-style `switch` statements to handle it; prefer the exhaustiveness-checked arrow-based `switch` expression.
--> **Confusing a record's compact constructor with its canonical constructor** -- a compact constructor has no parameter list and auto-assigns fields after the block runs; a canonical constructor repeats the parameter list and must assign every field explicitly.
--> **Assuming records can be mutable or extended** -- both are impossible by design; records are implicitly final and every component is a final field with no generated setters.
--> **Forgetting that `outer.new Inner()` is required for non-static inner classes** -- unlike a static nested class, you cannot write `new Inner()` from outside `Outer` without an existing `Outer` instance to bind to.

# Deep Dive -- Records and Pattern Matching (Java 21)

--> Java 21 finalized RECORD PATTERNS, letting a `switch` (or `instanceof`) DECONSTRUCT a record directly into its components as part of the match, instead of matching the record as a whole and then calling accessors manually.

```java
record Point(int x, int y) { }

static String classify(Object obj) {
    return switch (obj) {
        case Point(int x, int y) when x == 0 && y == 0 -> "Origin";
        case Point(int x, int y) when x == y -> "On the diagonal";
        case Point(int x, int y) -> "Point at (" + x + ", " + y + ")";
        default -> "Not a point";
    };
}
```

--> This pairs especially well with `sealed` interfaces (covered in the Abstraction chapter) -- a `sealed` hierarchy of `record` implementations gives the compiler enough information to exhaustively pattern-match over an entire closed family of data shapes, a style borrowed from functional languages' algebraic data types, now natively expressible in Java.
