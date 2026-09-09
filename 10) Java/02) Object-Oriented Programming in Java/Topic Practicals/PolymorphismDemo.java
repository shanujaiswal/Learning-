/*
 * PolymorphismDemo.java
 *
 * Demonstrates:
 *   1. Compile-time polymorphism: method overloading and overload resolution
 *      (int vs long vs Integer vs varargs, and the null / most-specific-type rule)
 *   2. Runtime polymorphism: a Shape hierarchy (Circle, Rectangle, Triangle)
 *      processed through a polymorphic List<Shape>, dynamic dispatch of area()/draw()
 *   3. Upcasting (implicit) and downcasting (explicit, guarded with instanceof)
 *   4. Pattern matching for instanceof (Java 16+)
 *   5. Fields are NOT polymorphic (field hiding) vs methods ARE polymorphic (overriding)
 *
 * Covers Theory chapter:
 *   10) Java/02) Object-Oriented Programming in Java/Theory/04 Polymorphism.md
 *
 * Compile:  javac 04_PolymorphismDemo.java
 * Run:      java PolymorphismDemo
 *
 * (File name matches the public class name: PolymorphismDemo)
 */

import java.util.ArrayList;
import java.util.List;

public class PolymorphismDemo {

    // -----------------------------------------------------------------------
    // Helper: section divider
    // -----------------------------------------------------------------------
    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }

    // -----------------------------------------------------------------------
    // 1) Compile-time polymorphism -- method overloading and resolution rules
    // -----------------------------------------------------------------------

    // Overload set used to show the widening-before-boxing-before-varargs rule.
    static void call(long x) {
        System.out.println("call(long) invoked with " + x + "  <-- widening (int->long), Phase 1, no boxing needed");
    }

    static void call(Integer x) {
        System.out.println("call(Integer) invoked with " + x + "  <-- would need autoboxing, Phase 2");
    }

    static void call(int... x) {
        System.out.println("call(int...) invoked with " + x.length + " args  <-- varargs, Phase 3, last resort");
    }

    // Overload set used to show the null / most-specific-reference-type rule.
    static void greet(String s) {
        System.out.println("greet(String) invoked  <-- String is more specific than Object");
    }

    static void greet(Object o) {
        System.out.println("greet(Object) invoked");
    }

    // A small overloaded "API" mirroring the theory file's Calculator example.
    static int add(int a, int b) {
        return a + b;
    }

    static double add(double a, double b) {
        return a + b;
    }

    static int add(int a, int b, int c) {
        return a + b + c;
    }

    static void demoOverloadResolution() {
        printSection("1) Compile-time polymorphism -- overload resolution");

        System.out.println("Overloaded add(): resolved purely by argument types/count, at compile time.");
        System.out.println("add(2, 3)        = " + add(2, 3) + "        (int, int overload)");
        System.out.println("add(2.5, 3.5)    = " + add(2.5, 3.5) + "    (double, double overload)");
        System.out.println("add(1, 2, 3)     = " + add(1, 2, 3) + "        (int, int, int overload)");

        System.out.println();
        System.out.println("Widening vs boxing vs varargs -- call(5) with an int literal:");
        call(5);   // Phase 1 (widening int->long) wins over Phase 2 (boxing to Integer) and Phase 3 (varargs)

        System.out.println();
        System.out.println("null resolves to the MOST SPECIFIC applicable reference-type overload:");
        greet(null);   // picks greet(String) over greet(Object), because String is more specific
    }

    // -----------------------------------------------------------------------
    // 2) Runtime polymorphism -- Shape hierarchy with dynamic dispatch
    // -----------------------------------------------------------------------

    static abstract class Shape {
        abstract double area();

        void draw() {
            System.out.printf("Drawing a generic shape with area %.2f%n", area());
        }
    }

    static class Circle extends Shape {
        double radius;

        Circle(double radius) {
            this.radius = radius;
        }

        @Override
        double area() {
            return Math.PI * radius * radius;
        }

        @Override
        void draw() {
            System.out.printf("Drawing a Circle (r=%.1f), area = %.2f%n", radius, area());
        }
    }

    static class Rectangle extends Shape {
        double width;
        double height;

        Rectangle(double width, double height) {
            this.width = width;
            this.height = height;
        }

        @Override
        double area() {
            return width * height;
        }

        @Override
        void draw() {
            System.out.printf("Drawing a Rectangle (%.1f x %.1f), area = %.2f%n", width, height, area());
        }
    }

    static class Triangle extends Shape {
        double base;
        double height;

        Triangle(double base, double height) {
            this.base = base;
            this.height = height;
        }

        @Override
        double area() {
            return 0.5 * base * height;
        }

        @Override
        void draw() {
            System.out.printf("Drawing a Triangle (base=%.1f, height=%.1f), area = %.2f%n", base, height, area());
        }
    }

    static void demoRuntimePolymorphism() {
        printSection("2) Runtime polymorphism -- polymorphic Shape list, dynamic dispatch");

        // Every element upcast implicitly to Shape when added -- the list itself is polymorphic.
        List<Shape> shapes = new ArrayList<>();
        shapes.add(new Circle(3.0));
        shapes.add(new Rectangle(4.0, 5.0));
        shapes.add(new Triangle(6.0, 2.0));

        double totalArea = 0.0;
        for (Shape s : shapes) {
            s.draw();                // dynamic dispatch -- runs each object's OWN override, chosen at runtime
            totalArea += s.area();    // same story -- area() resolves to the actual object's implementation
        }
        System.out.printf("Total area across all shapes: %.2f%n", totalArea);
    }

    // -----------------------------------------------------------------------
    // 3) Upcasting / downcasting + instanceof guard, and pattern matching instanceof
    // -----------------------------------------------------------------------

    static void demoCastingAndInstanceof() {
        printSection("3) Upcasting, downcasting, instanceof, and pattern matching instanceof");

        Shape s = new Circle(2.5);      // implicit UPCAST -- Circle assigned to a Shape-typed reference, always safe
        System.out.println("Upcast: Circle stored in a Shape reference -- s.area() = " + s.area());

        // Old-style downcast: explicit check, then explicit cast.
        if (s instanceof Circle) {
            Circle c = (Circle) s;                 // explicit DOWNCAST -- safe here because the instanceof check passed
            System.out.println("Old-style downcast succeeded, radius = " + c.radius);
        }

        // Modern pattern matching instanceof (Java 16+) -- check and cast combined into one expression.
        if (s instanceof Circle circle) {
            System.out.println("Pattern-matching instanceof: radius = " + circle.radius + ", area = " + circle.area());
        }

        // A downcast attempt guarded against the WRONG type -- shows the check correctly failing, no exception thrown.
        Shape rect = new Rectangle(3.0, 3.0);
        if (rect instanceof Circle badCast) {
            System.out.println("This should never print: " + badCast);
        } else {
            System.out.println("rect is not a Circle -- guarded instanceof correctly avoided a ClassCastException.");
        }

        // Demonstrating what an UNGUARDED downcast would do, caught safely here for demonstration purposes only.
        try {
            Circle willFail = (Circle) rect;         // compiles fine, but rect is actually a Rectangle
            System.out.println("Unreachable: " + willFail);
        } catch (ClassCastException e) {
            System.out.println("Unguarded downcast threw ClassCastException, as expected: " + e.getMessage());
        }

        // instanceof with a supertype and with Object -- both true, showing the IS-A chain.
        System.out.println("circle instanceof Shape  -> " + (s instanceof Shape));
        System.out.println("circle instanceof Object -> " + (s instanceof Object));
    }

    // -----------------------------------------------------------------------
    // 4) Fields are NOT polymorphic -- field hiding vs polymorphic methods, contrasted
    // -----------------------------------------------------------------------

    static class Base {
        String label = "Base";

        void printLabel() {
            System.out.println("Base.printLabel() sees label = " + label);
        }
    }

    static class Derived extends Base {
        String label = "Derived";       // HIDES Base.label -- this is NOT polymorphic

        @Override
        void printLabel() {              // genuinely OVERRIDES Base.printLabel() -- IS polymorphic
            System.out.println("Derived.printLabel() sees label = " + label);
        }
    }

    static void demoFieldsNotPolymorphic() {
        printSection("4) Fields are NOT polymorphic -- contrasted with polymorphic methods");

        Base b = new Derived();     // reference type Base, actual object type Derived

        // Field access -- resolved at COMPILE TIME using the REFERENCE's declared type (Base).
        System.out.println("b.label       => " + b.label + "   (field access uses declared type Base, NOT polymorphic)");

        // Method call -- resolved at RUNTIME using the OBJECT's actual type (Derived). Dynamic dispatch.
        System.out.print("b.printLabel()=> ");
        b.printLabel();               // prints "Derived.printLabel() sees label = Derived"

        Derived d = (Derived) b;      // downcast to see Derived's own hidden field directly
        System.out.println("((Derived) b).label => " + d.label + "   (only visible once cast to Derived)");

        System.out.println();
        System.out.println("Same object, two different answers for 'label' depending on HOW it's accessed:");
        System.out.println("  - through a Base reference:    \"" + b.label + "\"  (field, static binding)");
        System.out.println("  - through a Derived reference: \"" + d.label + "\"  (field, static binding)");
        System.out.println("  - via the overridden method:   picks Derived's field internally, dynamic dispatch");
    }

    // -----------------------------------------------------------------------
    // main -- run each demo in turn
    // -----------------------------------------------------------------------
    public static void main(String[] args) {
        demoOverloadResolution();
        demoRuntimePolymorphism();
        demoCastingAndInstanceof();
        demoFieldsNotPolymorphic();

        System.out.println();
        System.out.println("All Polymorphism demos completed.");
    }
}
