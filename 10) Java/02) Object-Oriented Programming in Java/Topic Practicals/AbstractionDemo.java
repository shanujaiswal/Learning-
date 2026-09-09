/*
 * AbstractionDemo.java
 *
 * Demonstrates:
 *   1. An abstract class (AbstractShape) with a constructor, a field, an abstract
 *      method, and a concrete method shared by all subclasses.
 *   2. Concrete subclasses (Circle, Rectangle) implementing the abstract contract.
 *   3. An interface (Describable) with a default method, a static method, and a
 *      private helper method (Java 9+).
 *   4. The diamond problem -- two interfaces (Left, Right) providing conflicting
 *      default methods, resolved explicitly in the implementing class via
 *      Interface.super.method().
 *   5. "Class always wins over interface default" resolution order.
 *   6. A functional interface (Calculator) used with a lambda expression.
 *
 * Covers Theory chapter:
 *   10) Java/02) Object-Oriented Programming in Java/Theory/05 Abstraction Abstract Classes and Interfaces.md
 *
 * Compile:  javac 05_AbstractionDemo.java
 * Run:      java AbstractionDemo
 */

// ---------------------------------------------------------------------------
// 1) Abstract class with constructor, field, abstract method, concrete method
// ---------------------------------------------------------------------------

abstract class AbstractShape {
    private final String name;                      // shared state, inherited by every subclass

    protected AbstractShape(String name) {           // constructor -- runs via super() from subclasses
        this.name = name;
    }

    abstract double area();                          // no body -- every concrete subclass MUST implement this

    void describe() {                                // concrete method -- shared as-is, calls the abstract one
        System.out.printf("%-10s area = %.2f%n", name, area());
    }

    String getName() {
        return name;
    }
}

class Circle extends AbstractShape {
    private final double radius;

    Circle(double radius) {
        super("Circle");                             // must call the abstract class's constructor
        this.radius = radius;
    }

    @Override
    double area() {
        return Math.PI * radius * radius;
    }
}

class Rectangle extends AbstractShape {
    private final double width;
    private final double height;

    Rectangle(double width, double height) {
        super("Rectangle");
        this.width = width;
        this.height = height;
    }

    @Override
    double area() {
        return width * height;
    }
}

// ---------------------------------------------------------------------------
// 3) Interface with default method, static method, and private helper (9+)
// ---------------------------------------------------------------------------

interface Describable {
    String label();                                  // abstract -- must be implemented

    default void printDescription() {                // default -- has a body, inherited automatically
        log("printing description");
        System.out.println("Description: " + label());
    }

    default void printShout() {
        log("printing shout");
        System.out.println("DESCRIPTION: " + label().toUpperCase());
    }

    static Describable of(String text) {              // static -- called as Describable.of(...), not inherited
        return () -> text;                             // lambda implementing the single abstract method label()
    }

    private void log(String action) {                 // private helper (Java 9+) -- shared by default methods,
        System.out.println("[Describable] " + action); // never visible outside the interface
    }
}

// ---------------------------------------------------------------------------
// 4) The diamond problem -- conflicting default methods, resolved explicitly
// ---------------------------------------------------------------------------

interface Left {
    default void greet() {
        System.out.println("Hello from Left");
    }
}

interface Right {
    default void greet() {
        System.out.println("Hello from Right");
    }
}

class Both implements Left, Right {
    @Override
    public void greet() {                              // explicit override REQUIRED -- compiler refuses to guess
        Left.super.greet();                              // explicitly invoke Left's version
        Right.super.greet();                             // explicitly invoke Right's version
        System.out.println("Hello from Both (merged resolution)");
    }
}

// ---------------------------------------------------------------------------
// 5) "Class always wins over interface default" resolution order
// ---------------------------------------------------------------------------

class Animal {
    void makeSound() {
        System.out.println("Some generic animal sound (from superclass Animal)");
    }
}

interface SoundMaker {
    default void makeSound() {
        System.out.println("Sound from interface SoundMaker (should be shadowed)");
    }
}

class Dog extends Animal implements SoundMaker {
    // No override needed here -- Animal.makeSound() automatically wins over
    // SoundMaker's default, since a superclass's concrete method always beats
    // an interface default. This is what keeps adding a default method to an
    // interface from silently changing behavior of classes with their own logic.
}

// ---------------------------------------------------------------------------
// 6) A functional interface used with a lambda
// ---------------------------------------------------------------------------

@FunctionalInterface
interface Calculator {
    int compute(int a, int b);                        // exactly one abstract method -- qualifies as functional
}

// ---------------------------------------------------------------------------
// Main class -- runs each demo in turn
// ---------------------------------------------------------------------------

public class AbstractionDemo {

    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }

    private static void demoAbstractClass() {
        printSection("1) Abstract class -- shared constructor/state + abstract method");
        AbstractShape[] shapes = {
                new Circle(3.0),
                new Rectangle(4.0, 5.0)
        };
        for (AbstractShape shape : shapes) {
            shape.describe();                          // concrete method, inherited, calls each subclass's area()
        }
        assert shapes[0].getName().equals("Circle");
    }

    private static void demoInterfaceDefaultStaticPrivate() {
        printSection("2) Interface -- default method, static factory, private helper");
        Describable item = Describable.of("a compact widget");   // static method used as a factory
        item.printDescription();                                 // default method, uses private log() internally
        item.printShout();                                        // second default method, shares the same helper
    }

    private static void demoDiamondProblem() {
        printSection("3) Diamond problem -- conflicting defaults resolved explicitly");
        Both both = new Both();
        both.greet();                                   // prints Left's, Right's, and Both's own lines
    }

    private static void demoClassWinsOverInterface() {
        printSection("4) Resolution order -- superclass method wins over interface default");
        Dog dog = new Dog();
        dog.makeSound();                                 // Animal's version wins, SoundMaker's default is ignored
    }

    private static void demoFunctionalInterfaceLambda() {
        printSection("5) Functional interface + lambda expressions");
        Calculator add = (a, b) -> a + b;                 // lambda implementing Calculator.compute
        Calculator multiply = (a, b) -> a * b;
        Calculator subtract = (a, b) -> a - b;

        System.out.println("add.compute(2, 3)      = " + add.compute(2, 3));
        System.out.println("multiply.compute(2, 3)  = " + multiply.compute(2, 3));
        System.out.println("subtract.compute(2, 3)  = " + subtract.compute(2, 3));

        assert add.compute(2, 3) == 5;
        assert multiply.compute(2, 3) == 6;
        assert subtract.compute(2, 3) == -1;
    }

    public static void main(String[] args) {
        demoAbstractClass();
        demoInterfaceDefaultStaticPrivate();
        demoDiamondProblem();
        demoClassWinsOverInterface();
        demoFunctionalInterfaceLambda();

        System.out.println();
        System.out.println("All Abstraction demos completed.");
    }
}
