/*
 * InheritanceDemo.java
 *
 * Demonstrates:
 *   1. `extends` and the IS-A relationship, with a Vehicle -> Car / Motorcycle hierarchy
 *   2. Constructor chaining via super(...) and the exact order of constructor execution
 *   3. Method overriding with @Override, and calling super.method() to extend behavior
 *   4. Field hiding vs method overriding -- the classic gotcha, shown explicitly side by side
 *   5. The `final` keyword on a class and on a method
 *
 * Covers Theory chapter:
 *   10) Java/02) Object-Oriented Programming in Java/Theory/03 Inheritance.md
 *
 * Compile:  javac 03_InheritanceDemo.java
 * Run:      java InheritanceDemo
 *
 * (File name matches the public class name: InheritanceDemo)
 */

import java.util.List;

public class InheritanceDemo {

    // -----------------------------------------------------------------------
    // Helper: section divider, mirrors the style used across the Python demos
    // -----------------------------------------------------------------------
    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }

    // -----------------------------------------------------------------------
    // 1) extends / IS-A hierarchy: Vehicle -> Car, Motorcycle
    //    Also demonstrates constructor chaining order and super(...) usage.
    // -----------------------------------------------------------------------

    static class Vehicle {
        String make;
        String model;
        int wheelCount;

        Vehicle(String make, String model, int wheelCount) {
            // No explicit super() here -- the compiler inserts an implicit,
            // no-arg super() call to Object() automatically, since Vehicle
            // extends nothing explicitly (so it implicitly extends Object).
            this.make = make;
            this.model = model;
            this.wheelCount = wheelCount;
            System.out.println("  [1] Vehicle(...) constructor running for " + make + " " + model);
        }

        void describe() {
            System.out.println(make + " " + model + " (" + wheelCount + " wheels)");
        }

        // Overridable (non-final) on purpose, to contrast with the final method below.
        void startEngine() {
            System.out.println(make + " " + model + " engine starting (generic Vehicle behavior).");
        }

        // final -- no subclass may ever override this. See section 5.
        final void displayVin(String vin) {
            System.out.println("VIN lookup is identical for every vehicle: " + vin);
        }
    }

    static class Car extends Vehicle {
        int doorCount;

        Car(String make, String model, int doorCount) {
            super(make, model, 4);              // must be the first statement -- chains to Vehicle's constructor
            this.doorCount = doorCount;
            System.out.println("  [2] Car(...) constructor running for " + make + " " + model);
        }

        @Override
        void describe() {
            super.describe();                    // extend, don't replace -- reuse Vehicle's describe() first
            System.out.println("  -> It's a car with " + doorCount + " doors.");
        }

        @Override
        void startEngine() {
            System.out.println(make + " " + model + " engine starting with a quiet electronic ignition.");
        }
    }

    // final class -- SportsCar cannot be subclassed further. Demonstrated in section 5.
    static final class SportsCar extends Car {
        int topSpeedMph;

        SportsCar(String make, String model, int topSpeedMph) {
            super(make, model, 2);
            this.topSpeedMph = topSpeedMph;
            System.out.println("  [3] SportsCar(...) constructor running for " + make + " " + model);
        }

        @Override
        void startEngine() {
            super.startEngine();                  // still extend Car's version rather than fully replacing it
            System.out.println("  -> Roaring off toward " + topSpeedMph + " mph.");
        }
    }

    static class Motorcycle extends Vehicle {
        boolean hasSidecar;

        Motorcycle(String make, String model, boolean hasSidecar) {
            super(make, model, hasSidecar ? 3 : 2);
            this.hasSidecar = hasSidecar;
            System.out.println("  [2] Motorcycle(...) constructor running for " + make + " " + model);
        }

        @Override
        void startEngine() {
            System.out.println(make + " " + model + " engine starting with a loud roar.");
        }
    }

    static void demoConstructorChainingAndOverriding() {
        printSection("1) extends / IS-A hierarchy + constructor chaining order");

        System.out.println("Constructing a Car (Vehicle -> Car), watch the printed order:");
        Car car = new Car("Toyota", "Corolla", 4);
        System.out.println("Construction finished. Order was Vehicle first, then Car -- top of the");
        System.out.println("hierarchy always finishes initializing before the subclass layer runs.");

        System.out.println();
        System.out.println("Constructing a SportsCar (Vehicle -> Car -> SportsCar), three levels deep:");
        SportsCar ferrari = new SportsCar("Ferrari", "488", 205);

        System.out.println();
        printSection("2) Method overriding + super.method() ('extend, don't replace')");
        car.describe();          // Car's overridden describe(), which itself calls super.describe()
        car.startEngine();        // Car's own override, no super call this time -- full replacement
        ferrari.startEngine();    // SportsCar's override DOES call super.startEngine() -- see Car's message appear first

        System.out.println();
        System.out.println("Polymorphic dispatch through Vehicle-typed references:");
        List<Vehicle> vehicles = List.of(
                car,
                ferrari,
                new Motorcycle("Harley-Davidson", "Sportster", false)
        );
        for (Vehicle v : vehicles) {
            v.describe();
            v.startEngine();      // each call runs the ACTUAL object's override, not Vehicle's -- dynamic dispatch
        }
    }

    // -----------------------------------------------------------------------
    // 2) Field hiding vs method overriding -- the classic gotcha, shown explicitly
    // -----------------------------------------------------------------------

    static class Animal {
        String category = "Animal";          // will be HIDDEN (not overridden) by Dog below

        void identify() {                     // will be OVERRIDDEN by Dog below
            System.out.println("I am a(n) " + category + " (via Animal.identify)");
        }
    }

    static class Dog extends Animal {
        String category = "Dog";              // hides Animal.category -- this is NOT polymorphic

        @Override
        void identify() {                      // genuinely overrides Animal.identify -- IS polymorphic
            System.out.println("I am specifically a " + category + " (via Dog.identify)");
        }
    }

    static void demoFieldHidingVsMethodOverriding() {
        printSection("3) Field hiding vs method overriding -- the classic gotcha");

        Animal a = new Dog();     // reference type Animal, actual object type Dog

        System.out.println("Reference declared as: Animal   |   Actual object is: Dog");
        System.out.println();

        // FIELD access -- resolved at COMPILE TIME by the reference's declared type (Animal).
        System.out.println("a.category            => " + a.category + "   (field access -- uses REFERENCE type Animal, NOT polymorphic)");

        // METHOD call -- resolved at RUNTIME by the object's actual type (Dog). Dynamic dispatch.
        System.out.print("a.identify()          => ");
        a.identify();              // prints "I am specifically a Dog" -- Dog's category, via Dog's overridden method

        // Casting to Dog exposes Dog's own hidden field directly.
        Dog d = (Dog) a;
        System.out.println("((Dog) a).category    => " + d.category + "   (only visible after casting the reference type)");

        System.out.println();
        System.out.println("Conclusion: field access followed the REFERENCE type (Animal -> \"Animal\"),");
        System.out.println("but the method call followed the OBJECT's real type (Dog -> Dog's category).");
        System.out.println("This asymmetry is exactly why same-named fields across a hierarchy are a trap.");
    }

    // -----------------------------------------------------------------------
    // 3) final keyword demo -- class-level and method-level
    // -----------------------------------------------------------------------

    static void demoFinalKeyword() {
        printSection("4) final keyword -- class and method");

        Car car = new Car("Honda", "Civic", 4);
        car.displayVin("1HGCM82633A004352");   // final method -- guaranteed identical behavior on every Vehicle subtype

        System.out.println();
        System.out.println("SportsCar is declared 'final class' -- attempting");
        System.out.println("'class Supercar extends SportsCar { }' would be a COMPILE ERROR.");
        System.out.println("Vehicle.displayVin(...) is declared 'final' -- no subclass (Car, SportsCar,");
        System.out.println("Motorcycle) is permitted to override it; every vehicle looks it up identically.");
    }

    // -----------------------------------------------------------------------
    // main -- run each demo in turn
    // -----------------------------------------------------------------------
    public static void main(String[] args) {
        demoConstructorChainingAndOverriding();
        demoFieldHidingVsMethodOverriding();
        demoFinalKeyword();

        System.out.println();
        System.out.println("All Inheritance demos completed.");
    }
}
