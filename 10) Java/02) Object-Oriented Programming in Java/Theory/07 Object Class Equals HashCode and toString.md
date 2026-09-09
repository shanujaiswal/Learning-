# Every Class Extends `Object`

--> Every class in Java, whether you write `extends` explicitly or not, implicitly extends `java.lang.Object` -- `class Dog {}` is exactly equivalent to `class Dog extends Object {}`. This is the ROOT of the entire class hierarchy -- there is no way to write a Java class that does NOT inherit from `Object`, either directly or transitively through some chain of superclasses.
--> Because every object IS-AN `Object`, every object automatically has the methods `Object` declares, whether or not the class author ever thinks about them: `equals(Object o)`, `hashCode()`, `toString()`, `getClass()`, `clone()`, `finalize()` (deprecated), and the threading-related `wait()`/`notify()`/`notifyAll()`. This file focuses on the three that come up constantly in everyday code -- `equals`, `hashCode`, and `toString` -- plus the closely related ordering interfaces `Comparable` and `Comparator`.
--> These three methods matter so much because Java's standard library leans on them EVERYWHERE -- collections (`HashMap`, `HashSet`, `ArrayList.contains`), debugging output (`System.out.println(obj)`, logging), and testing frameworks all silently call into `equals`, `hashCode`, or `toString` on your behalf. Getting them wrong doesn't usually cause a compile error -- it causes confusing runtime bugs, which is exactly why this topic deserves careful treatment.

```text
Object
  |
  +-- equals(Object o)      -- "are these two objects considered equal?"
  +-- hashCode()             -- "a numeric summary of this object, used by hash-based collections"
  +-- toString()              -- "a human-readable text representation"
  +-- getClass()              -- "the runtime class of this object" (used internally by equals, reflection)
  +-- clone(), finalize(), wait()/notify()/notifyAll()   -- covered elsewhere / rarely used directly
```

# The Default Behavior -- Reference Equality and `ClassName@hashcode`

--> If a class never overrides `equals`, `hashCode`, or `toString`, it inherits `Object`'s default implementations, and those defaults are all built around OBJECT IDENTITY (i.e. "is this literally the same object in memory"), not the object's field values.

```java
class Point {
    int x, y;
    Point(int x, int y) { this.x = x; this.y = y; }
}

public class Demo {
    public static void main(String[] args) {
        Point p1 = new Point(1, 2);
        Point p2 = new Point(1, 2);   // same field values, DIFFERENT object

        System.out.println(p1.equals(p2));   // false -- default equals is just "p1 == p2"
        System.out.println(p1 == p2);         // false -- different heap addresses
        System.out.println(p1);               // Point@1b6d3586  -- className@hexHashCode
    }
}
```

--> **Default `equals(Object o)`** is literally implemented as `return this == o;` -- pure reference/identity comparison. Two objects with identical field values are NOT equal unless `equals` has been overridden to compare fields instead.
--> **Default `hashCode()`** returns an implementation-specific integer, typically derived from the object's memory address or an internally tracked identity value -- it is consistent for a given object's lifetime but bears no relationship to field values.
--> **Default `toString()`** returns `getClass().getName() + "@" + Integer.toHexString(hashCode())` -- e.g. `com.example.Point@1b6d3586`. This is rarely useful for debugging or logging, which is why overriding `toString()` is one of the most common and highest-value overrides in everyday Java code.

# The `equals()` Contract

--> `equals()` is a method any class can override to define what "equal" means for its own objects -- but the override is not a free-for-all. The `Object.equals` Javadoc lays out a strict CONTRACT that every override must honor, because collections and library code rely on this contract holding for ANY object, not just the ones you personally wrote.

```text
For any non-null references x, y, z:

  Reflexive     -- x.equals(x) must always be true.
  Symmetric     -- x.equals(y) must equal y.equals(x) (both true or both false).
  Transitive    -- if x.equals(y) and y.equals(z), then x.equals(z) must also be true.
  Consistent    -- repeated calls to x.equals(y) return the same result,
                   as long as neither object's compared fields have changed.
  Non-null      -- x.equals(null) must always return false, never throw.
```

--> **Reflexive** -- almost never violated by accident, but worth stating: an object must always equal itself.
--> **Symmetric** -- the classic violation is comparing against a DIFFERENT type asymmetrically, e.g. a subclass `equals` that returns true when compared against its superclass, while the superclass's `equals` returns false when compared against the subclass. This breaks any code that might compare `a.equals(b)` in one place and `b.equals(a)` in another and expect the same answer.
--> **Transitive** -- famously hard to keep alongside symmetry once inheritance and additional fields get involved (a subclass adding a field that also participates in equality tends to break this) -- covered in the `getClass()` vs `instanceof` discussion below.
--> **Consistent** -- `equals` should be a PURE function of the object's compared fields -- it should never depend on unstable external state like network calls, current time, or random values. If the fields used in comparison don't change, the result must not change either.
--> **Non-null** -- `x.equals(null)` returning `false` (never throwing `NullPointerException`) is required. This is one of the easiest contract clauses to accidentally violate if a null-check is forgotten before dereferencing the argument.

# Overriding `equals()` Properly

--> The standard, idiomatic pattern for overriding `equals()` in modern Java:

```java
public class Point {
    private final int x;
    private final int y;

    public Point(int x, int y) { this.x = x; this.y = y; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;                       // fast path -- same reference, definitely equal
        if (o == null || getClass() != o.getClass()) return false;   // null-check + type-check combined
        Point other = (Point) o;                            // safe cast -- type already verified
        return x == other.x && y == other.y;                 // field-by-field comparison
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(x, y);
    }
}
```

--> **Step 1 -- reference check (`this == o`)**. Not required for correctness, but a cheap short-circuit: if it's literally the same object, it's trivially equal, and there's no need to do any field comparison at all.
--> **Step 2 -- null and type check**. `o == null` must return false (contract requirement). The type check ensures you're not comparing incompatible types -- e.g. a `Point` against a `String` should be `false`, not a `ClassCastException`.
--> **Step 3 -- safe cast and field comparison**. Once the type check passes, casting is guaranteed safe. Compare every field that logically participates in the object's notion of equality.
--> **Always override `@Override` explicitly** -- the annotation causes a compile error if the signature doesn't actually match `Object.equals(Object)` (e.g. accidentally writing `equals(Point o)`, which OVERLOADS rather than OVERRIDES and silently fails to be picked up by collections that call `equals(Object)` polymorphically). This is one of the most common and dangerous `equals` bugs -- the code compiles fine, but the override is never actually invoked by library code.

```java
// BUG -- this OVERLOADS equals, it does NOT override Object.equals(Object)
public boolean equals(Point o) {     // wrong parameter type -- should be Object
    return x == o.x && y == o.y;
}
// Without @Override, this compiles silently. HashMap/HashSet/ArrayList.contains
// all call equals(Object), so they never see this overload -- they fall back to
// the inherited Object.equals (reference equality), producing baffling bugs.
```

# `getClass()` vs `instanceof` in `equals()`

--> There are two common ways to write the type check inside `equals()`, and they behave differently with inheritance -- this is a genuinely debated design choice, not a simple right-or-wrong.

```java
// Option A -- getClass() comparison: strict, exact-type match
if (o == null || getClass() != o.getClass()) return false;

// Option B -- instanceof comparison: allows subclass instances to be compared
if (!(o instanceof Point)) return false;
```

--> **`getClass()` (strict)** -- only objects of the EXACT same runtime class can be equal. A `Point` and a `ColoredPoint extends Point` (even with identical x/y) are never equal to each other under this scheme. This preserves SYMMETRY and TRANSITIVITY automatically, even in the presence of subclassing, because the type check alone guarantees two objects being compared are always the exact same class.
--> **`instanceof` (permissive)** -- allows a subclass to be considered equal to a superclass instance (or another subclass) as long as `instanceof` matches. This is more flexible but is the classic source of transitivity/symmetry violations once a subclass adds new fields that also participate in equality -- see the concrete broken example below.

```java
class Point {
    int x, y;
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Point)) return false;
        Point p = (Point) o;
        return x == p.x && y == p.y;             // note: doesn't check p's exact class
    }
}

class ColoredPoint extends Point {
    String color;
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ColoredPoint)) return false;
        ColoredPoint cp = (ColoredPoint) o;
        return super.equals(cp) && color.equals(cp.color);
    }
}

// Symmetry violation:
Point p = new Point(1, 1);
ColoredPoint cp = new ColoredPoint(1, 1, "red");

p.equals(cp);    // true  -- Point.equals only looks at x, y, and cp IS a Point
cp.equals(p);    // false -- ColoredPoint.equals requires o instanceof ColoredPoint, p is not
// p.equals(cp) != cp.equals(p) -- contract broken!
```

--> **The pragmatic recommendation** -- prefer `getClass()` when a class is part of a hierarchy where subclasses might add fields (it mechanically guarantees the contract holds). Prefer `instanceof` only when the class is `final` (no subclassing possible, so the two approaches become equivalent) or when you deliberately want cross-hierarchy equality and have verified the contract holds for your specific case. Effective Java (Joshua Bloch)'s classic advice is actually stronger still: "favor composition over inheritance" specifically to sidestep this whole problem -- if `ColoredPoint` HAS-A `Point` field instead of extending `Point`, there's no equality contract tension to resolve at all.

# The `hashCode()` Contract

--> `hashCode()` returns an `int` "hash code" -- a numeric summary of an object, used internally by hash-based collections (`HashMap`, `HashSet`, `Hashtable`, `HashSet`-backed structures) to decide which internal BUCKET an object belongs in, so lookups can be near O(1) instead of scanning every element.

```text
For any object x, across any single execution of a program:

  Consistency        -- calling x.hashCode() multiple times returns the same int,
                         as long as no field used in equals() has changed.
  Equal objects       -- if x.equals(y) is true, then x.hashCode() MUST equal y.hashCode().
  Unequal objects      -- if x.equals(y) is false, their hashCodes MAY be equal or different
                          (a "hash collision" is legal and expected occasionally -- it is NOT
                          required that unequal objects have different hash codes).
```

--> **The critical rule: "equal objects MUST have equal hash codes."** This is a ONE-DIRECTIONAL requirement -- equal objects must hash the same, but objects with the same hash code are NOT required to be equal (that's just a collision, which every hash-based structure is designed to handle via chaining/probing). Violating this one-directional rule is what silently breaks hash-based collections.

# Why `equals()` and `hashCode()` Must Be Overridden Together

--> **The golden rule**: if you override `equals()`, you MUST also override `hashCode()` (and vice versa, though overriding `hashCode()` alone without `equals()` is less common and less dangerous). Overriding only one of the pair breaks the contract above and produces genuinely broken behavior in any hash-based collection.

```java
class BrokenPoint {
    int x, y;
    BrokenPoint(int x, int y) { this.x = x; this.y = y; }

    @Override
    public boolean equals(Object o) {           // overridden -- compares by value
        if (!(o instanceof BrokenPoint)) return false;
        BrokenPoint p = (BrokenPoint) o;
        return x == p.x && y == p.y;
    }
    // hashCode() NOT overridden -- still inherited from Object (identity-based!)
}

import java.util.HashSet;
import java.util.Set;

Set<BrokenPoint> set = new HashSet<>();
set.add(new BrokenPoint(3, 4));

BrokenPoint lookup = new BrokenPoint(3, 4);    // equals() says this IS equal to the one above
System.out.println(lookup.equals(set.iterator().next()));   // true

System.out.println(set.contains(lookup));      // FALSE! -- broken.
```

--> **Why this happens, mechanically**: `HashSet.contains(lookup)` does NOT scan every element calling `equals` on each one -- that would defeat the entire point of using a hash set. Instead it first calls `lookup.hashCode()` to compute which INTERNAL BUCKET to look in, and only calls `equals()` against the (usually few) objects already in that exact bucket. Since `hashCode()` is still the inherited identity-based default, `lookup` and the stored object have DIFFERENT hash codes (they're different objects in memory) even though `equals()` says they're equal -- so `contains` looks in the wrong bucket entirely and never even calls `equals()` against the right object. The set silently behaves as if the element isn't there, even though an "equal" element clearly is.
--> The exact same mechanism breaks `HashMap` key lookups (`map.get(key)` returning `null` for a key that's "equal" to one already inserted) and causes duplicate-looking entries to accumulate in a `HashSet` that should have deduplicated them.
--> **The fix is always the same** -- derive `hashCode()` from EXACTLY the same fields that `equals()` compares, so that "equal objects" mechanically produce "equal hash codes" by construction.

```java
class FixedPoint {
    int x, y;
    FixedPoint(int x, int y) { this.x = x; this.y = y; }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FixedPoint)) return false;
        FixedPoint p = (FixedPoint) o;
        return x == p.x && y == p.y;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(x, y);   // built from the SAME fields as equals()
    }
}
// Now set.contains(lookup) correctly returns true -- lookup hashes to the same
// bucket as the stored equal object, and equals() confirms the match once there.
```

# `toString()` -- Human-Readable Representation

--> Overriding `toString()` replaces the unhelpful `ClassName@hexHashCode` default with a readable summary -- it's automatically called by `System.out.println(obj)`, string concatenation (`"Point: " + p`), `String.valueOf(obj)`, and most logging frameworks, so overriding it once pays off everywhere an object might end up being printed or logged.

```java
public class Point {
    private final int x, y;
    Point(int x, int y) { this.x = x; this.y = y; }

    @Override
    public String toString() {
        return "Point(x=" + x + ", y=" + y + ")";
    }
}

Point p = new Point(3, 4);
System.out.println(p);              // Point(x=3, y=4) -- instead of Point@1b6d3586
System.out.println("Location: " + p);   // "Location: Point(x=3, y=4)" -- toString() called implicitly
```

--> **Best practice** -- keep `toString()` output concise and focused on identifying fields (avoid dumping every single field of a large object, and never include sensitive data like passwords or tokens, since `toString()` output routinely ends up in logs). Many IDEs and modern Java (`record` types, covered elsewhere) can auto-generate a sensible `toString()`, but for hand-written classes, a short, readable summary listing the class name and key field values is the standard convention.
--> **`toString()` is for humans/debugging, not for parsing** -- never write code that parses another class's `toString()` output back into data; there's no contract guaranteeing its format stays stable, unlike `equals`/`hashCode`, which do have a binding contract.

# `Objects.equals()` and `Objects.hash()` -- Utility Helpers

--> `java.util.Objects` (not to be confused with `java.lang.Object`) provides small static utility methods that make writing correct `equals`/`hashCode` overrides far less error-prone, especially around `null` handling.

```java
import java.util.Objects;

public class Person {
    private String name;      // could be null
    private int age;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Person p = (Person) o;
        return age == p.age && Objects.equals(name, p.name);
        // Objects.equals(a, b) handles null safely:
        // returns true if both null, false if only one is null,
        // otherwise delegates to a.equals(b) -- avoids manual null-checking.
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, age);
        // Objects.hash(...) builds an array of the given fields and computes
        // a combined hash roughly equivalent to Arrays.hashCode(new Object[]{name, age}).
        // Convenient and correct, though marginally slower than a hand-rolled combination
        // due to varargs array allocation -- rarely matters outside extremely hot paths.
    }
}
```

--> **`Objects.equals(a, b)`** -- null-safe equality check: `(a == b) || (a != null && a.equals(b))`. Use this for every reference-type field being compared inside `equals()` instead of calling `.equals()` directly on a field that might be `null`.
--> **`Objects.hash(fields...)`** -- convenience wrapper that boxes primitives, builds an `Object[]`, and delegates to `Arrays.hashCode`. For a hand-rolled equivalent without the varargs array allocation, the manual pattern (also what many IDEs auto-generate) is:

```java
@Override
public int hashCode() {
    int result = 17;                              // arbitrary nonzero seed
    result = 31 * result + Integer.hashCode(age);   // 31 -- odd prime, spreads bits well
    result = 31 * result + (name == null ? 0 : name.hashCode());
    return result;
}
```

# `Comparable<T>` -- Natural Ordering

--> `Comparable<T>` is an interface with a single method, `compareTo(T other)`, that defines an object's NATURAL ORDERING -- the default way instances of a class should be sorted when no other ordering is specified. Classes like `String`, `Integer`, and `LocalDate` all implement `Comparable`, which is exactly why `Collections.sort(listOfStrings)` and `Arrays.sort(intArray)` work without any extra configuration.

```java
public interface Comparable<T> {
    int compareTo(T o);
}
```

```text
Return value convention for compareTo(other):
  negative int   -- this object comes BEFORE other
  zero           -- this object is considered EQUAL in ordering to other
  positive int    -- this object comes AFTER other
```

```java
public class Employee implements Comparable<Employee> {
    private String name;
    private double salary;

    Employee(String name, double salary) { this.name = name; this.salary = salary; }

    @Override
    public int compareTo(Employee other) {
        return Double.compare(this.salary, other.salary);   // ascending by salary
        // Double.compare / Integer.compare are the idiomatic way to compare primitives --
        // avoid subtracting doubles/longs directly (see gotchas below).
    }

    @Override
    public String toString() { return name + "($" + salary + ")"; }
}

import java.util.*;
List<Employee> staff = new ArrayList<>(List.of(
    new Employee("Alice", 75000), new Employee("Bob", 60000)));
Collections.sort(staff);              // uses Employee's compareTo -- ascending salary
```

--> **Recommendation: keep `compareTo` consistent with `equals`.** The `Comparable` Javadoc strongly recommends (though does not strictly require) that `x.compareTo(y) == 0` should imply `x.equals(y) == true`, and vice versa. Violating this is legal but dangerous -- `TreeSet`/`TreeMap` (which order elements using `compareTo`, not `equals`/`hashCode`) will treat two objects as duplicates and silently discard one if `compareTo` returns `0` for them, even if `equals` would say they're different. This is a genuinely common source of "why did my TreeSet lose an element" bugs.

```java
// Inconsistent with equals -- dangerous in a TreeSet:
class Item implements Comparable<Item> {
    String name; double price;
    @Override public int compareTo(Item o) { return Double.compare(price, o.price); }
    // equals() (inherited or separately overridden) might compare name+price,
    // but compareTo only looks at price -- two DIFFERENT items with the same
    // price compare as "equal" (0) and a TreeSet<Item> will silently keep only one.
}
```

# `Comparator<T>` -- Custom, Pluggable Ordering

--> `Comparator<T>` is a separate interface for defining an ordering EXTERNALLY to the class being sorted -- useful when a class has no natural ordering of its own, when you need multiple different orderings for the same type, or when you don't own the class's source code and can't add `Comparable` to it.

```java
public interface Comparator<T> {
    int compare(T a, T b);   // same return-value convention as compareTo
}
```

```java
import java.util.*;

List<Employee> staff = new ArrayList<>(List.of(
    new Employee("Alice", 75000), new Employee("Bob", 60000), new Employee("Carl", 60000)));

// Old-school anonymous class:
staff.sort(new Comparator<Employee>() {
    @Override
    public int compare(Employee a, Employee b) {
        return a.getName().compareTo(b.getName());
    }
});

// Modern lambda -- identical behavior, far less boilerplate:
staff.sort((a, b) -> a.getName().compareTo(b.getName()));
```

--> **`Comparator.comparing(...)` factory methods** -- the modern, idiomatic way to build comparators without hand-writing `compare` bodies, using a method reference or lambda to extract the sort key.

```java
import java.util.Comparator;

Comparator<Employee> byName = Comparator.comparing(Employee::getName);
Comparator<Employee> bySalary = Comparator.comparingDouble(Employee::getSalary);
// comparingInt/comparingLong/comparingDouble avoid autoboxing overhead vs comparing(Employee::getSalaryAsDouble)
```

--> **Chaining with `thenComparing(...)`** -- breaks ties in the primary comparator using a secondary key, and can be chained further for tertiary keys and beyond. Essential for "sort by X, and for ties, sort by Y" requirements.

```java
Comparator<Employee> byNameThenSalary =
    Comparator.comparing(Employee::getName)
              .thenComparing(Employee::getSalary);

staff.sort(byNameThenSalary);
```

--> **`.reversed()`** -- flips any comparator's direction without rewriting it.

```java
Comparator<Employee> bySalaryDescending =
    Comparator.comparingDouble(Employee::getSalary).reversed();

// Or, sort ascending then reverse the whole comparator (equivalent for a total order):
staff.sort(Comparator.comparingDouble(Employee::getSalary).reversed());
```

--> **`nullsFirst` / `nullsLast`** -- wrap a comparator to define where `null` elements sort, since a plain comparator throws `NullPointerException` the moment it tries to compare against `null`.

```java
Comparator<String> safe = Comparator.nullsFirst(Comparator.naturalOrder());
```

# `Comparable` vs `Comparator` -- Side-by-Side

```text
                    Comparable<T>                    Comparator<T>
Defined             Inside the class itself            Externally, as a separate object
Method               compareTo(T other)                 compare(T a, T b)
Number allowed        Exactly one per class               As many as you want
Use case              The "default"/natural ordering       Alternate or ad-hoc orderings,
                                                             or ordering a class you can't modify
Typical call site       Collections.sort(list)              list.sort(comparator) /
                        Arrays.sort(array)                   Collections.sort(list, comparator)
```

--> **They compose** -- a class can implement `Comparable` for its natural ordering AND have separate `Comparator` constants available for alternate orderings, letting callers pick whichever fits: `staff.sort(null)` or `Collections.sort(staff)` uses natural ordering (`compareTo`), while `staff.sort(Employee.BY_SALARY)` uses an explicit comparator.

# Common Gotchas and Best Practices

--> **Mutating a field used in `hashCode()` after inserting the object into a `HashSet`/`HashMap` key position.** This is one of the most insidious hash-collection bugs -- it doesn't require forgetting to override `hashCode()` at all, just mutating a field that participates in it AFTER the object is already stored.

```java
class MutablePoint {
    int x, y;   // NOT final -- mutable
    MutablePoint(int x, int y) { this.x = x; this.y = y; }
    @Override public boolean equals(Object o) { /* compares x, y */ return true; }
    @Override public int hashCode() { return java.util.Objects.hash(x, y); }
}

Set<MutablePoint> set = new HashSet<>();
MutablePoint p = new MutablePoint(1, 1);
set.add(p);                 // stored in the bucket for hash(1, 1)

p.x = 99;                    // mutate AFTER insertion -- hashCode() now returns a different value

set.contains(p);             // FALSE -- looks in the bucket for hash(99, 1), but p physically
                              // still lives in the bucket for the OLD hash(1, 1)
set.remove(p);                // also fails for the same reason -- the object is "lost" in the set,
                              // unreachable by lookup even though iterating the set still finds it
```

--> **Best practice: prefer immutable fields (`final`) for anything used in `equals`/`hashCode`.** This is exactly why the constructor practicals for this topic (and the earlier Classes/Constructors file) lean on `final` fields set once at construction -- it makes this entire class of bug structurally impossible, since a field that can never change can never invalidate a previously computed hash bucket.
--> **Comparing floating-point fields with `==` inside `equals()`.** `double`/`float` equality via `==` is famously unreliable due to rounding (`0.1 + 0.2 == 0.3` is `false`), and `NaN != NaN` even for the literal same value. Prefer `Double.compare(a, b) == 0` (which also correctly treats `NaN` as equal to itself and orders `-0.0` before `0.0`, matching `Double.equals`'s documented behavior) over raw `==` when the field is a primitive floating-point type inside `equals()`.
--> **Forgetting `hashCode()` after overriding `equals()` (or vice versa)** -- covered in depth above; the two must always be overridden as a pair, derived from the same set of fields.
--> **Using `getClass()` inconsistently across subclasses**, or mixing `getClass()` in one direction of a comparison and `instanceof` in the other -- pick one strategy for a given hierarchy and apply it uniformly to avoid symmetry violations.
--> **Overloading `equals(SpecificType o)` instead of overriding `equals(Object o)`** -- always double check the parameter type is exactly `Object`, and always use `@Override` so the compiler catches signature mismatches immediately rather than silently compiling a dead overload.
--> **Including non-essential or frequently-changing fields in `equals`/`hashCode`** -- e.g. a `lastAccessedTimestamp` field that updates on every read would make an object's hash code (and thus its position in a `HashSet`) drift constantly, defeating the whole point of the collection. Only fields that define the object's logical identity should participate.

# Deep Dive -- Records as a Modern Shortcut

--> Java 16+ introduces `record` types, which automatically generate `equals()`, `hashCode()`, and `toString()` (along with a canonical constructor and accessor methods) based on the record's declared components, following exactly the correct patterns described in this file.

```java
public record Point(int x, int y) { }
// Automatically gets:
//   equals()   -- compares x and y field-by-field (getClass()-style exact-type check)
//   hashCode() -- combines x and y consistently with equals
//   toString() -- "Point[x=1, y=2]"
```

--> Records don't replace the need to understand the contract -- they simply remove the boilerplate for the extremely common case of a small, immutable data holder where every component participates in equality. Records are covered in more depth in a later file; they're mentioned here because they exist specifically to encode the `equals`/`hashCode`/`toString` best practices described above automatically.

# Deep Dive -- Why 31 Is the Traditional Hash Multiplier

--> The hand-rolled `hashCode()` pattern (`result = 31 * result + field.hashCode();`) traditionally uses the odd prime `31` as a multiplier. Two practical reasons: `31` is odd and prime, which helps distribute hash values more uniformly across buckets and reduces the chance of the multiplication cyclically losing information (an even multiplier would always clear the lowest bit); and `31 * i` can be replaced by the JVM/JIT with `(i << 5) - i`, a cheap bit-shift-and-subtract, which historically mattered more for performance than it does today but remains the conventional choice IDEs and `Objects.hash` implementations still follow.
