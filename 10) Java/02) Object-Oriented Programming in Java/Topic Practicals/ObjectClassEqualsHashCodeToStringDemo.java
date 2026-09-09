/*
 * ObjectClassEqualsHashCodeToStringDemo.java
 *
 * Demonstrates:
 *   1. Default Object behavior -- reference-equality equals(), identity hashCode(),
 *      and the unhelpful ClassName@hexHashCode toString().
 *   2. A properly overridden equals()/hashCode()/toString() triple (Point), following
 *      the this==o / null+getClass() / field-comparison pattern, built with Objects.equals/hash.
 *   3. The equals() contract -- reflexive, symmetric, transitive, consistent, non-null --
 *      each clause demonstrated explicitly.
 *   4. A BROKEN example that overrides equals() but not hashCode(), showing HashSet.contains
 *      silently failing, then the FIXED version once hashCode() is derived from the same fields.
 *   5. getClass() vs instanceof in equals() -- a concrete symmetry violation using instanceof
 *      across a subclass, contrasted with the getClass() strategy that avoids it.
 *   6. Comparable<T> -- natural ordering via compareTo(), used by Collections.sort().
 *   7. Comparator<T> -- external orderings via comparing()/thenComparing()/reversed(),
 *      including a lambda-based comparator and multi-key sorting.
 *
 * Covers Theory chapter:
 *   10) Java/02) Object-Oriented Programming in Java/Theory/07 Object Class Equals HashCode and toString.md
 *
 * Compile:  javac 07_ObjectClassEqualsHashCodeToStringDemo.java
 * Run:      java ObjectClassEqualsHashCodeToStringDemo
 */

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

// ---------------------------------------------------------------------------
// 1) A plain class with NO overrides -- relies entirely on Object's defaults
// ---------------------------------------------------------------------------

class RawPoint {
    int x, y;
    RawPoint(int x, int y) { this.x = x; this.y = y; }
    // equals(), hashCode(), toString() all inherited from Object -- identity based
}

// ---------------------------------------------------------------------------
// 2) A properly overridden equals()/hashCode()/toString() triple
// ---------------------------------------------------------------------------

class Point {
    private final int x;
    private final int y;

    Point(int x, int y) { this.x = x; this.y = y; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;                                  // fast path -- same reference
        if (o == null || getClass() != o.getClass()) return false;    // null-check + exact-type check
        Point other = (Point) o;                                       // safe cast -- type verified above
        return x == other.x && y == other.y;                           // field-by-field comparison
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);                                    // built from the SAME fields as equals()
    }

    @Override
    public String toString() {
        return "Point(x=" + x + ", y=" + y + ")";
    }

    int getX() { return x; }
    int getY() { return y; }
}

// ---------------------------------------------------------------------------
// 5) getClass() vs instanceof -- symmetry violation demo classes
// ---------------------------------------------------------------------------

class InstanceofPoint {
    int x, y;
    InstanceofPoint(int x, int y) { this.x = x; this.y = y; }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof InstanceofPoint)) return false;   // permissive -- matches subclasses too
        InstanceofPoint p = (InstanceofPoint) o;
        return x == p.x && y == p.y;                          // note: doesn't check p's exact runtime class
    }

    @Override
    public int hashCode() { return Objects.hash(x, y); }
}

class InstanceofColoredPoint extends InstanceofPoint {
    String color;
    InstanceofColoredPoint(int x, int y, String color) {
        super(x, y);
        this.color = color;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof InstanceofColoredPoint)) return false;   // stricter than the superclass!
        InstanceofColoredPoint cp = (InstanceofColoredPoint) o;
        return super.equals(cp) && Objects.equals(color, cp.color);
    }

    @Override
    public int hashCode() { return Objects.hash(super.hashCode(), color); }
}

// ---------------------------------------------------------------------------
// 4) BROKEN pair -- equals() overridden, hashCode() forgotten -- then FIXED
// ---------------------------------------------------------------------------

class BrokenPoint {
    int x, y;
    BrokenPoint(int x, int y) { this.x = x; this.y = y; }

    @Override
    public boolean equals(Object o) {                 // overridden -- compares by value
        if (!(o instanceof BrokenPoint)) return false;
        BrokenPoint p = (BrokenPoint) o;
        return x == p.x && y == p.y;
    }
    // hashCode() intentionally NOT overridden -- still inherited from Object (identity-based!)
}

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
        return Objects.hash(x, y);                     // derived from the SAME fields as equals()
    }
}

// ---------------------------------------------------------------------------
// 6) Comparable<T> -- natural ordering
// ---------------------------------------------------------------------------

class Employee implements Comparable<Employee> {
    private final String name;
    private final double salary;

    Employee(String name, double salary) { this.name = name; this.salary = salary; }

    @Override
    public int compareTo(Employee other) {
        return Double.compare(this.salary, other.salary);   // ascending by salary -- natural ordering
    }

    String getName() { return name; }
    double getSalary() { return salary; }

    @Override
    public String toString() { return name + "($" + salary + ")"; }
}

// ---------------------------------------------------------------------------
// Main class
// ---------------------------------------------------------------------------

public class ObjectClassEqualsHashCodeToStringDemo {

    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }

    // -----------------------------------------------------------------------
    // Demo 1: default Object behavior -- reference equality, identity hashCode
    // -----------------------------------------------------------------------
    private static void demoDefaultBehavior() {
        printSection("1) Default Object behavior -- identity-based equals/hashCode/toString");

        RawPoint p1 = new RawPoint(1, 2);
        RawPoint p2 = new RawPoint(1, 2);          // same field values, DIFFERENT object

        System.out.println("p1.equals(p2) = " + p1.equals(p2));   // false -- default equals is just p1 == p2
        System.out.println("p1 == p2      = " + (p1 == p2));       // false -- different heap addresses
        System.out.println("p1.toString() = " + p1);                // RawPoint@hexHashCode
        System.out.println("p1.hashCode() != p2.hashCode() (usually): "
                + (p1.hashCode() != p2.hashCode()));
        assert !p1.equals(p2);
    }

    // -----------------------------------------------------------------------
    // Demo 2: properly overridden equals/hashCode/toString
    // -----------------------------------------------------------------------
    private static void demoProperOverrides() {
        printSection("2) Properly overridden equals()/hashCode()/toString() -- Point");

        Point a = new Point(3, 4);
        Point b = new Point(3, 4);                  // equal by VALUE, different object
        Point c = new Point(9, 9);

        System.out.println("a = " + a);
        System.out.println("b = " + b);
        System.out.println("a.equals(b) = " + a.equals(b) + "   (a == b) = " + (a == b));
        System.out.println("a.equals(c) = " + a.equals(c));
        System.out.println("a.hashCode() == b.hashCode(): " + (a.hashCode() == b.hashCode()));
        assert a.equals(b) && a.hashCode() == b.hashCode();
        assert !a.equals(c);

        // equals(null) must return false, never throw
        System.out.println("a.equals(null) = " + a.equals(null));
        assert !a.equals(null);

        // equals against an unrelated type must return false, not throw ClassCastException
        System.out.println("a.equals(\"3,4\") = " + a.equals("3,4"));
        assert !a.equals("3,4");
    }

    // -----------------------------------------------------------------------
    // Demo 3: the equals() contract, clause by clause
    // -----------------------------------------------------------------------
    private static void demoEqualsContract() {
        printSection("3) The equals() contract -- reflexive, symmetric, transitive, consistent, non-null");

        Point x = new Point(1, 1);
        Point y = new Point(1, 1);
        Point z = new Point(1, 1);

        // Reflexive -- x.equals(x) must always be true
        System.out.println("Reflexive:  x.equals(x) = " + x.equals(x));
        assert x.equals(x);

        // Symmetric -- x.equals(y) == y.equals(x)
        System.out.println("Symmetric:  x.equals(y) = " + x.equals(y) + "   y.equals(x) = " + y.equals(x));
        assert x.equals(y) == y.equals(x);

        // Transitive -- x.equals(y) && y.equals(z) => x.equals(z)
        System.out.println("Transitive: x.equals(y) && y.equals(z) => x.equals(z) = "
                + (x.equals(y) && y.equals(z) && x.equals(z)));
        assert x.equals(y) && y.equals(z) && x.equals(z);

        // Consistent -- repeated calls return the same result
        boolean first = x.equals(y);
        boolean second = x.equals(y);
        System.out.println("Consistent: repeated x.equals(y) -> " + first + ", " + second);
        assert first == second;

        // Non-null -- x.equals(null) must be false, never throw
        System.out.println("Non-null:   x.equals(null) = " + x.equals(null));
        assert !x.equals(null);
    }

    // -----------------------------------------------------------------------
    // Demo 4: BROKEN equals/hashCode pair vs the FIXED pair
    // -----------------------------------------------------------------------
    private static void demoBrokenVsFixedHashCode() {
        printSection("4) equals() without hashCode() -- HashSet.contains() silently breaks");

        Set<BrokenPoint> brokenSet = new HashSet<>();
        brokenSet.add(new BrokenPoint(3, 4));
        BrokenPoint brokenLookup = new BrokenPoint(3, 4);

        System.out.println("brokenLookup.equals(stored) = "
                + brokenLookup.equals(brokenSet.iterator().next()));       // true -- equals() says "equal"
        System.out.println("brokenSet.contains(brokenLookup) = "
                + brokenSet.contains(brokenLookup));                        // FALSE -- wrong bucket!
        assert !brokenSet.contains(brokenLookup);                             // demonstrates the bug

        Set<FixedPoint> fixedSet = new HashSet<>();
        fixedSet.add(new FixedPoint(3, 4));
        FixedPoint fixedLookup = new FixedPoint(3, 4);

        System.out.println("fixedSet.contains(fixedLookup) = " + fixedSet.contains(fixedLookup));  // true
        assert fixedSet.contains(fixedLookup);
    }

    // -----------------------------------------------------------------------
    // Demo 5: getClass() vs instanceof -- a concrete symmetry violation
    // -----------------------------------------------------------------------
    private static void demoGetClassVsInstanceof() {
        printSection("5) getClass() vs instanceof in equals() -- symmetry violation with instanceof");

        InstanceofPoint p = new InstanceofPoint(1, 1);
        InstanceofColoredPoint cp = new InstanceofColoredPoint(1, 1, "red");

        boolean pEqualsCp = p.equals(cp);   // true -- InstanceofPoint.equals only looks at x, y; cp IS a Point
        boolean cpEqualsP = cp.equals(p);   // false -- InstanceofColoredPoint requires instanceof ColoredPoint

        System.out.println("p.equals(cp)  = " + pEqualsCp);
        System.out.println("cp.equals(p)  = " + cpEqualsP);
        System.out.println("Symmetry broken (p.equals(cp) != cp.equals(p)): " + (pEqualsCp != cpEqualsP));
        assert pEqualsCp != cpEqualsP;   // the contract violation, demonstrated concretely

        // Point (from demo 2) uses getClass() -- comparing across different types is simply false,
        // which mechanically preserves symmetry no matter what subclass might exist.
        Point strictA = new Point(1, 1);
        System.out.println("Point uses getClass(): strictA.equals(cp) = " + strictA.equals(cp) + " (always false, no symmetry risk)");
    }

    // -----------------------------------------------------------------------
    // Demo 6: Comparable<T> -- natural ordering
    // -----------------------------------------------------------------------
    private static void demoComparable() {
        printSection("6) Comparable<T> -- Employee's natural ordering (by salary)");

        List<Employee> staff = new ArrayList<>(List.of(
                new Employee("Alice", 75000),
                new Employee("Bob", 60000),
                new Employee("Carl", 90000)));

        Collections.sort(staff);            // uses Employee.compareTo() -- ascending salary
        System.out.println("Sorted by natural ordering (salary asc): " + staff);

        assert staff.get(0).getName().equals("Bob");
        assert staff.get(2).getName().equals("Carl");
    }

    // -----------------------------------------------------------------------
    // Demo 7: Comparator<T> -- external, pluggable, chainable orderings
    // -----------------------------------------------------------------------
    private static void demoComparator() {
        printSection("7) Comparator<T> -- external orderings, chaining, reversed()");

        List<Employee> staff = new ArrayList<>(List.of(
                new Employee("Alice", 60000),
                new Employee("Bob", 60000),
                new Employee("Carl", 90000)));

        // Lambda-based comparator -- alphabetical by name
        staff.sort((a, b) -> a.getName().compareTo(b.getName()));
        System.out.println("Sorted by name (lambda):        " + staff);

        // Comparator.comparing(...) factory -- salary ascending
        Comparator<Employee> bySalary = Comparator.comparingDouble(Employee::getSalary);
        staff.sort(bySalary);
        System.out.println("Sorted by salary (comparing):   " + staff);

        // thenComparing -- break salary ties using name
        Comparator<Employee> bySalaryThenName =
                Comparator.comparingDouble(Employee::getSalary).thenComparing(Employee::getName);
        staff.sort(bySalaryThenName);
        System.out.println("Sorted by salary, then name:    " + staff);
        assert staff.get(0).getName().equals("Alice");   // Alice ties Bob on salary, wins alphabetically
        assert staff.get(1).getName().equals("Bob");

        // reversed() -- flip direction without rewriting the comparator
        staff.sort(bySalary.reversed());
        System.out.println("Sorted by salary descending:    " + staff);
        assert staff.get(0).getName().equals("Carl");
    }

    public static void main(String[] args) {
        demoDefaultBehavior();
        demoProperOverrides();
        demoEqualsContract();
        demoBrokenVsFixedHashCode();
        demoGetClassVsInstanceof();
        demoComparable();
        demoComparator();

        System.out.println();
        System.out.println("All Object Class / equals-hashCode-toString demos completed.");
    }
}
