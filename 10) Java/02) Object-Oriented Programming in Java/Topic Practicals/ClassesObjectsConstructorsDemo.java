/*
 * ClassesObjectsConstructorsDemo.java
 *
 * Demonstrates:
 *     1. Class anatomy -- fields, methods, constructors, static vs instance initializer blocks
 *     2. Objects vs classes vs references -- identity vs equality, shared references
 *     3. The `this` keyword -- field disambiguation, this(...) chaining, passing the current
 *        instance, and method chaining (fluent API)
 *     4. Constructor overloading with this(...) chaining, worked through a BankAccount example
 *     5. Order of initialization -- static blocks -> instance blocks -> constructor body,
 *        and across an inheritance hierarchy
 *     6. The overridable-method-in-constructor gotcha
 *
 * Covers Theory chapter:
 *     10) Java/02) Object-Oriented Programming in Java/Theory/01 Classes Objects and Constructors.md
 *
 * Compile: javac 01_ClassesObjectsConstructorsDemo.java
 * Run:     java ClassesObjectsConstructorsDemo
 */

import java.util.ArrayList;
import java.util.List;

public class ClassesObjectsConstructorsDemo {

    // -------------------------------------------------------------------
    // Small print helper, mirrors the section-banner style used elsewhere
    // -------------------------------------------------------------------
    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }

    public static void main(String[] args) {
        demoObjectsVsReferences();
        demoThisFieldDisambiguation();
        demoConstructorChainingAndOverloading();
        demoBankAccountWorkedExample();
        demoStaticVsInstanceInitializerOrder();
        demoInitializationOrderAcrossInheritance();
        demoMethodChainingWithThis();
        demoOverridableMethodInConstructorGotcha();
        System.out.println("\nAll Classes/Objects/Constructors demos completed.");
    }

    // ---------------------------------------------------------------------------
    // 1) Objects vs classes vs references -- identity vs shared references
    // ---------------------------------------------------------------------------
    static class Dog {
        String name;
        Dog(String name) { this.name = name; }
    }

    private static void demoObjectsVsReferences() {
        printSection("1) Objects vs Classes vs References");

        Dog d1 = new Dog("Rex");
        Dog d3 = new Dog("Rex");   // a SEPARATE object, even though the field values match

        // Reference/identity comparison -- true only if both variables point at the SAME object
        System.out.println("d1 == d3 (different objects, same field values): " + (d1 == d3));
        assert !(d1 == d3) : "d1 and d3 must be distinct objects";

        Dog d2 = d1;   // d2 now holds the SAME address as d1 -- no new object created
        System.out.println("d1 == d2 (d2 = d1, same object): " + (d1 == d2));
        assert d1 == d2;

        d2.name = "Fido";   // mutating through d2 is visible through d1 -- they share one object
        System.out.println("d1.name after mutating through d2: " + d1.name);
        assert d1.name.equals("Fido");

        System.out.println("Class of d1: " + d1.getClass().getSimpleName());
    }

    // ---------------------------------------------------------------------------
    // 2) `this` for field disambiguation
    // ---------------------------------------------------------------------------
    static class Person {
        private String name;
        private int age;

        Person(String name, int age) {
            // Parameters deliberately shadow the fields -- `this.` is required to reach the field.
            this.name = name;
            this.age = age;
        }

        void setName(String name) {
            this.name = name;   // without `this`, this would be a no-op self-assignment
        }

        // Demonstrates the classic silent-bug version, for illustration only (not actually called
        // in a way that corrupts state below -- kept private and unused by the demo flow).
        void brokenSetAge(int age) {
            age = age;   // BUG: assigns the parameter to itself; the field is untouched
        }

        @Override
        public String toString() {
            return "Person{name='" + name + "', age=" + age + "}";
        }
    }

    private static void demoThisFieldDisambiguation() {
        printSection("2) `this` for Field Disambiguation");

        Person p = new Person("Alice", 30);
        System.out.println("Constructed: " + p);

        p.setName("Alicia");
        System.out.println("After setName (uses this.name = name): " + p);
        assert p.toString().contains("Alicia");

        int ageBefore = p.age;
        p.brokenSetAge(99);
        System.out.println("After brokenSetAge(99) -- age unchanged because `this` was omitted: " + p);
        assert p.age == ageBefore : "age must be untouched -- brokenSetAge never reaches the field";
    }

    // ---------------------------------------------------------------------------
    // 3) Constructor chaining with this(...) and constructor overloading
    // ---------------------------------------------------------------------------
    static class Pizza {
        private final String size;
        private final boolean hasCheese;

        Pizza(String size, boolean hasCheese) {
            this.size = size;
            this.hasCheese = hasCheese;
            System.out.println("  -> Pizza(String, boolean) body running: size=" + size + ", cheese=" + hasCheese);
        }

        Pizza(String size) {
            this(size, true);   // this(...) MUST be the first statement -- chains to the 2-arg ctor
            System.out.println("  -> Pizza(String) body running (after delegation)");
        }

        Pizza() {
            this("medium");     // chains to the 1-arg ctor, which chains to the 2-arg ctor
            System.out.println("  -> Pizza() body running (after delegation)");
        }

        @Override
        public String toString() {
            return "Pizza{size='" + size + "', hasCheese=" + hasCheese + "}";
        }
    }

    private static void demoConstructorChainingAndOverloading() {
        printSection("3) Constructor Chaining via this(...) and Overloading");

        System.out.println("Constructing new Pizza():");
        Pizza p = new Pizza();
        System.out.println("Result: " + p);
        // Chain order printed above should be: 2-arg body, then 1-arg body, then 0-arg body --
        // this(...) delegation runs to completion BEFORE the delegating constructor's own body.
        assert p.toString().contains("medium") && p.toString().contains("true");
    }

    // ---------------------------------------------------------------------------
    // 4) Worked example -- BankAccount with multiple overloaded constructors
    // ---------------------------------------------------------------------------
    static class BankAccount {
        private static int nextAccountNumber = 1000;   // static -- shared across all accounts

        private final int accountNumber;   // assigned once, never changes -- read-only after construction
        private String owner;
        private double balance;

        // "Master" constructor -- every other overload eventually delegates here.
        BankAccount(String owner, double openingBalance) {
            if (owner == null || owner.isBlank()) {
                throw new IllegalArgumentException("owner cannot be blank");
            }
            if (openingBalance < 0) {
                throw new IllegalArgumentException("openingBalance cannot be negative");
            }
            this.accountNumber = nextAccountNumber++;
            this.owner = owner;
            this.balance = openingBalance;
        }

        // Overload: no opening balance supplied -- defaults to 0.0
        BankAccount(String owner) {
            this(owner, 0.0);
        }

        // Overload: anonymous account, default balance -- passes the current instance nowhere here,
        // but demonstrates chaining through two levels down to the master constructor.
        BankAccount() {
            this("Unnamed Owner");
        }

        void deposit(double amount) {
            if (amount <= 0) throw new IllegalArgumentException("deposit must be positive");
            this.balance += amount;
        }

        void withdraw(double amount) {
            if (amount <= 0) throw new IllegalArgumentException("withdrawal must be positive");
            if (amount > this.balance) throw new IllegalStateException("insufficient funds");
            this.balance -= amount;
        }

        double getBalance() { return balance; }
        int getAccountNumber() { return accountNumber; }

        @Override
        public String toString() {
            return "BankAccount#" + accountNumber + "{owner='" + owner + "', balance=" + balance + "}";
        }
    }

    private static void demoBankAccountWorkedExample() {
        printSection("4) Worked Example -- BankAccount with Overloaded Constructors");

        BankAccount full = new BankAccount("Priya", 500.0);
        BankAccount ownerOnly = new BankAccount("Ravi");     // chains to (owner, 0.0)
        BankAccount anonymous = new BankAccount();            // chains to ("Unnamed Owner") -> (owner, 0.0)

        System.out.println(full);
        System.out.println(ownerOnly);
        System.out.println(anonymous);

        // Each account gets a distinct, incrementing account number from the shared static counter.
        assert full.getAccountNumber() != ownerOnly.getAccountNumber();
        assert ownerOnly.getAccountNumber() != anonymous.getAccountNumber();

        full.deposit(250.0);
        full.withdraw(100.0);
        System.out.println("After deposit(250) and withdraw(100): " + full);
        assert full.getBalance() == 650.0;

        try {
            full.withdraw(10_000.0);
        } catch (IllegalStateException e) {
            System.out.println("Caught expected exception on overdraw: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------------------
    // 5) Static vs instance initializer blocks -- observing execution order
    // ---------------------------------------------------------------------------
    static class InitOrderDemo {
        static int classLoadCount = 0;   // bumped once by the static block

        static {
            classLoadCount++;
            System.out.println("  [static block] InitOrderDemo class loaded (count=" + classLoadCount + ")");
        }

        int instanceId;

        {
            // Instance initializer block -- runs on EVERY `new`, before the constructor body,
            // after field defaults are applied.
            System.out.println("  [instance block] running before constructor body");
            instanceId = assignId();
        }

        private static int idCounter = 0;
        private static int assignId() { return ++idCounter; }

        InitOrderDemo() {
            System.out.println("  [constructor body] InitOrderDemo() running, instanceId=" + instanceId);
        }
    }

    private static void demoStaticVsInstanceInitializerOrder() {
        printSection("5) Static vs Instance Initializer Block Order");

        System.out.println("Creating first InitOrderDemo:");
        InitOrderDemo a = new InitOrderDemo();
        System.out.println("Creating second InitOrderDemo:");
        InitOrderDemo b = new InitOrderDemo();

        // The static block ran exactly once, even though two objects were created.
        System.out.println("classLoadCount after 2 objects: " + InitOrderDemo.classLoadCount);
        assert InitOrderDemo.classLoadCount == 1 : "static block must run only once per class load";

        // The instance block ran once per object, giving each a distinct instanceId.
        System.out.println("a.instanceId=" + a.instanceId + ", b.instanceId=" + b.instanceId);
        assert a.instanceId != b.instanceId;
    }

    // ---------------------------------------------------------------------------
    // 6) Order of initialization across an inheritance hierarchy
    // ---------------------------------------------------------------------------
    static class Animal {
        static { System.out.println("  1. Animal static block"); }
        { System.out.println("  3. Animal instance block"); }
        Animal() {
            System.out.println("  4. Animal constructor");
        }
    }

    static class AnimalDog extends Animal {
        static { System.out.println("  2. AnimalDog static block"); }
        { System.out.println("  5. AnimalDog instance block"); }
        AnimalDog() {
            // No explicit super() call needed -- the compiler inserts an implicit super() as the
            // first statement, which is why Animal's setup (steps 3-4) always precedes Dog's (5-6).
            System.out.println("  6. AnimalDog constructor");
        }
    }

    private static void demoInitializationOrderAcrossInheritance() {
        printSection("6) Initialization Order Across Inheritance (superclass-first)");

        System.out.println("Constructing first AnimalDog:");
        new AnimalDog();

        System.out.println("Constructing second AnimalDog (static blocks do NOT repeat):");
        new AnimalDog();
        // Expected output ordering for EACH construction: 1, 2 (only once, first time ever),
        // then 3, 4, 5, 6 every single time a new AnimalDog is built.
    }

    // ---------------------------------------------------------------------------
    // 7) Method chaining (fluent API) via `return this;`
    // ---------------------------------------------------------------------------
    static class StringJoinerLite {
        private final List<String> parts = new ArrayList<>();

        StringJoinerLite add(String s) {
            parts.add(s);
            return this;   // hands back the current object so the caller can chain another call
        }

        String build() { return String.join("", parts); }
    }

    private static void demoMethodChainingWithThis() {
        printSection("7) Method Chaining via return this;");

        String result = new StringJoinerLite().add("a").add("b").add("c").build();
        System.out.println("Chained result: " + result);
        assert result.equals("abc");
    }

    // ---------------------------------------------------------------------------
    // 8) Gotcha -- calling an overridable method from a constructor
    // ---------------------------------------------------------------------------
    static class Base {
        Base() {
            System.out.println("  Base() constructor calling init()...");
            init();   // DANGEROUS: init() may be overridden by a subclass whose fields aren't set yet
        }
        void init() {
            System.out.println("  Base.init() default implementation");
        }
    }

    static class Derived extends Base {
        private String label = "ready";   // assigned AFTER Base()'s constructor body finishes

        Derived() {
            super();   // implicit anyway, shown explicitly here for clarity
            System.out.println("  Derived() constructor body: label=" + label);
        }

        @Override
        void init() {
            // At the moment Base() calls init(), Derived's field initializers have NOT run yet --
            // `label` is still null here, not "ready", even though the field initializer looks
            // like it runs "immediately."
            System.out.println("  Derived.init() override: label=" + label + " (expected null here!)");
        }
    }

    private static void demoOverridableMethodInConstructorGotcha() {
        printSection("8) Gotcha -- Overridable Method Called From a Constructor");

        Derived d = new Derived();
        System.out.println("Final state after full construction: label=" + d.label);
        // By the time construction fully completes, the field DOES hold "ready" -- the gotcha is
        // only that init(), called mid-construction from Base(), observed the field too early.
        assert d.label.equals("ready");
    }
}
