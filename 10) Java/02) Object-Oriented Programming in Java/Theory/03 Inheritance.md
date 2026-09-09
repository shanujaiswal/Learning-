# Why Inheritance Matters

--> Inheritance lets one class ACQUIRE the fields and methods of another, so common state and behavior are written ONCE in a general class and reused (and specialized) by more specific classes -- instead of copy-pasting the same `name`, `age`, `eat()` logic into `Dog`, `Cat`, and `Bird` separately, they all extend a single `Animal` class that already has it.
--> This is one of the four pillars of OOP (alongside encapsulation, polymorphism, and abstraction) and it's the mechanism that makes polymorphism (the next file) possible at all -- without a shared supertype, there's no common reference type to point at different subclass objects.

# The `extends` Keyword and the IS-A Relationship

--> A class becomes a subclass (child class, derived class) of another by using `extends` in its declaration -- the class it extends is the superclass (parent class, base class).

```java
class Animal {
    String name;

    void eat() {
        System.out.println(name + " is eating.");
    }
}

class Dog extends Animal {          // Dog IS-A Animal
    void bark() {
        System.out.println(name + " says Woof!");
    }
}
```

--> **The IS-A test** -- inheritance should only be used when the relationship genuinely reads as "X IS-A Y" -- a `Dog` IS-A `Animal`, a `Car` IS-A `Vehicle`. If the real relationship is "X HAS-A Y" (a `Car` HAS-A `Engine`), that calls for composition (one class holding a reference to another as a field) instead of inheritance -- forcing a HAS-A relationship into `extends` is one of the most common OOP design mistakes, because it exposes the whole parent's API on the child even when only a small piece of its behavior was actually wanted.
--> A `Dog` object automatically has everything `Animal` has -- its `name` field and its `eat()` method -- PLUS whatever `Dog` itself adds, like `bark()`. Nothing needs to be re-declared.
--> **Every class in Java implicitly extends `Object`** if it doesn't explicitly extend anything else -- there is no such thing as a class with zero superclass; `Object` sits at the root of every inheritance chain (more in the Object Class section below).

# Single Inheritance -- No Multiple Class Inheritance

--> Java allows a class to extend only ONE direct superclass -- `class C extends A, B { }` is a compile error. This is different from languages like C++ that permit a class to inherit from several base classes directly.
--> **Why Java forbids it -- the Diamond Problem.** If class `B` and class `C` both extend class `A` and override the same method differently, and class `D extends B, C`, then when code calls that method on a `D` object, it's AMBIGUOUS which version -- `B`'s or `C`'s -- should run. C++ allows this ambiguity and forces the programmer to resolve it manually (virtual inheritance, explicit scoping); Java's designers deliberately avoided the whole category of bug by simply not allowing multiple class inheritance in the first place.

```text
        A
       / \
      B   C          <-- both override foo()
       \ /
        D             <-- extends B, C -- which foo() wins? Ambiguous. Java disallows this shape entirely.
```

--> **The workaround -- interfaces.** A class can implement MULTIPLE interfaces (`class D implements X, Y, Z`), because historically interfaces only declared method signatures with no implementation, so there was nothing to conflict. Since Java 8 introduced `default` methods on interfaces (implementations can live in an interface too), Java added an explicit resolution rule instead of silent ambiguity: if a class implements two interfaces with clashing `default` methods, the class is FORCED to override the method itself and pick (or combine) a behavior -- it will not compile otherwise. Interfaces are covered in depth in a later file, but it's worth knowing now: "no multiple inheritance of class implementation, but yes to multiple inheritance of interface type" is the core Java design decision here.
--> Every class DOES still form a single, linear chain up to `Object` -- `Dog extends Animal extends Object` -- so a class can have many ancestors, just not many DIRECT parents at any one level.

# The `super` Keyword

--> `super` refers to "the superclass part" of the current object -- it's used in two distinct situations: calling a superclass CONSTRUCTOR, and accessing a superclass FIELD or METHOD that's been hidden or overridden.

## `super(...)` -- Calling the Superclass Constructor

```java
class Animal {
    String name;

    Animal(String name) {
        this.name = name;
        System.out.println("Animal constructor running for " + name);
    }
}

class Dog extends Animal {
    String breed;

    Dog(String name, String breed) {
        super(name);              // MUST be the first statement in the constructor
        this.breed = breed;
        System.out.println("Dog constructor running for " + name);
    }
}
```

--> `super(...)` calls the matching constructor in the immediate superclass, passing along whatever arguments it needs -- this is how a subclass ensures the inherited part of the object (fields declared in the parent) gets properly initialized before the subclass adds its own initialization on top.
--> **`super(...)` must be the FIRST statement in a constructor, if used at all.** This is a hard compiler rule -- not a style preference. It exists because the subclass's own fields and logic should never run before the inherited state they might depend on has been set up.
--> **If a constructor doesn't explicitly call `super(...)`, the compiler inserts an implicit, no-argument `super()` call as the first statement automatically.** This only compiles if the superclass actually HAS a no-argument constructor available (either explicitly written or the default one the compiler generates when a class declares no constructors at all). If the superclass only has parameterized constructors, every subclass constructor MUST explicitly call `super(...)` with matching arguments, or it's a compile error.

## `super.field` and `super.method()` -- Accessing the Superclass Directly

```java
class Animal {
    String sound = "generic animal sound";

    void makeSound() {
        System.out.println("The animal makes a sound: " + sound);
    }
}

class Dog extends Animal {
    String sound = "Woof";              // hides Animal's sound field

    @Override
    void makeSound() {
        super.makeSound();               // explicitly invoke Animal's version first
        System.out.println("The dog barks: " + sound);
        System.out.println("Superclass field via super.sound: " + super.sound);
    }
}
```

--> `super.method()` calls the SUPERCLASS's version of a method that the subclass has overridden -- useful for "extend, don't replace" behavior: the subclass adds its own logic but still wants the parent's original logic to run too, rather than duplicating it.
--> `super.field` accesses the superclass's field directly, which only matters when the subclass has declared a field with the SAME NAME (field hiding -- covered in the gotchas section, this is different from and much rarer than method overriding).

# Method Overriding

--> Overriding means a subclass provides its OWN implementation of a method that's already defined in its superclass, using the exact same signature -- when called on a subclass instance, the subclass's version runs instead of the superclass's, even through a superclass-typed reference (this is runtime polymorphism, covered fully in the next file).

```java
class Animal {
    void makeSound() {
        System.out.println("Some generic sound");
    }
}

class Cat extends Animal {
    @Override
    void makeSound() {                    // overrides Animal.makeSound()
        System.out.println("Meow");
    }
}
```

## The Rules for a Valid Override

--> **Same method name and same parameter list (signature).** If the parameter list differs at all, it's not an override -- it's a brand new overload (see the gotcha section below for how this bites people).
--> **`@Override` annotation** -- not required by the compiler, but should be used on every intended override without exception. It tells the compiler "I claim this method overrides a superclass method" -- if the signature doesn't actually match anything in the superclass (a typo in the name, a wrong parameter type), the compiler raises a COMPILE ERROR instead of silently letting a broken overload slip through. This turns a silent runtime logic bug into an immediate, loud compile-time failure.
--> **Covariant return types** -- the overriding method's return type must be the SAME type, OR a SUBTYPE of the original return type (not necessarily identical). This lets an override return a more specific type than its parent promised.

```java
class Animal {
    Animal reproduce() {
        return new Animal();
    }
}

class Dog extends Animal {
    @Override
    Dog reproduce() {                 // covariant return -- Dog is a subtype of Animal, this is legal
        return new Dog();
    }
}
```

--> **Access modifier can stay the same or become MORE permissive, never more restrictive.** A `protected` method in the superclass can be overridden as `protected` or `public`, but not as `private` or package-private -- narrowing access would break the promise that "any code that could call this on the superclass type can call it on the subclass type too" (this ties directly into the Liskov Substitution Principle -- a subclass must be usable anywhere its superclass is expected).

```text
Superclass access   -->  Allowed override access
public               -->  public only
protected            -->  protected or public
package-private      -->  package-private, protected, or public
private              -->  N/A -- private methods aren't inherited, can't be overridden at all
```

--> **Exception rules** -- an overriding method can throw FEWER or narrower checked exceptions than the superclass version, but never NEW or BROADER checked exceptions. It can throw any unchecked (runtime) exception freely, regardless of what the superclass declared.

```java
class Animal {
    void eat() throws java.io.IOException { }
}

class Dog extends Animal {
    @Override
    void eat() throws java.io.FileNotFoundException { }   // OK -- narrower checked exception (subtype of IOException)
    // void eat() throws Exception { }                     // COMPILE ERROR -- broader checked exception than parent allows
}
```

--> **Static methods cannot be overridden -- they're HIDDEN, not overridden.** A `static` method with the same signature in a subclass doesn't participate in dynamic dispatch at all; which version runs is decided at COMPILE TIME based on the reference's declared type, not the object's actual runtime type. This is a subtle and important distinction covered in more depth in the Polymorphism file.
--> **`final`, `private`, and constructor methods cannot be overridden** -- `final` explicitly forbids it (see the `final` keyword section below), `private` methods aren't visible/inherited outside their own class at all, and constructors are never inherited or overridden, only chained via `super(...)`.

# Overriding vs Overloading

--> These two terms sound similar but describe completely different mechanisms -- confusing them is one of the most common OOP mistakes for people new to the language.

```text
                    | Overriding                          | Overloading
--------------------|--------------------------------------|---------------------------------------
Relationship        | Between superclass and subclass       | Within the SAME class (or inherited into one)
Parameter list       | Must be IDENTICAL                     | Must DIFFER (type, count, or order)
Return type          | Same type or covariant subtype         | Can be anything, unrelated to overload resolution
Access modifier      | Same or wider, never narrower          | No restriction between overloads
Binding time         | Runtime (dynamic dispatch)             | Compile time (resolved by matching arguments)
Purpose              | Specialize inherited behavior          | Offer multiple ways to call similar-purpose logic
Annotation           | `@Override` (recommended, checked)     | No equivalent annotation
```

```java
class Calculator {
    int add(int a, int b) {              // overload 1
        return a + b;
    }

    double add(double a, double b) {      // overload 2 -- different parameter TYPES, same class
        return a + b;
    }

    int add(int a, int b, int c) {        // overload 3 -- different parameter COUNT
        return a + b + c;
    }
}
```

--> Overloading is resolved entirely by the compiler by looking at the arguments in the call and picking the best-matching signature -- it has NOTHING to do with inheritance and involves no runtime decision at all. Overload resolution details (widening, autoboxing, varargs preference order) are covered in the Polymorphism file, since they interact closely with runtime dispatch pitfalls.

# Constructor Chaining Across the Hierarchy

--> Every time a subclass object is constructed, ALL constructors up the inheritance chain run, in order from the TOPMOST superclass down to the actual class being instantiated -- this guarantees every layer of inherited state is initialized before the layer built on top of it runs its own logic.

```java
class Animal {
    Animal() {
        System.out.println("1: Animal()");
    }
}

class Mammal extends Animal {
    Mammal() {
        super();                          // implicit even if omitted
        System.out.println("2: Mammal()");
    }
}

class Dog extends Mammal {
    Dog() {
        super();
        System.out.println("3: Dog()");
    }
}

// new Dog() prints, in this exact order:
// 1: Animal()
// 2: Mammal()
// 3: Dog()
```

--> **Order of execution, precisely:** for `new Dog()` -- Java first resolves the full chain (`Dog -> Mammal -> Animal -> Object`), then executes constructor BODIES from the top down: `Object()` (empty, does nothing observable) runs first, then `Animal()`, then `Mammal()`, then finally `Dog()`'s own body. The subclass's fields are only initialized (and its constructor body only runs) AFTER the full superclass chain above it has finished.
--> This is exactly why `super(...)` must be the first statement -- it's not just a style rule, it enforces this top-down initialization order structurally, so there's no way to accidentally run subclass logic before the inherited state exists.

# The `Object` Class -- Brief Overview

--> Every class in Java, directly or indirectly, extends `Object` -- it's the root of the entire class hierarchy. This means every object, of every class ever written, automatically has a small set of methods available.

```text
equals(Object obj)   -- logical equality comparison (default: reference/identity equality, `==`)
hashCode()            -- an int used by hash-based collections (HashMap, HashSet) to bucket objects
toString()            -- a String representation (default: ClassName@hexHashCode)
getClass()             -- returns the Class object representing the object's actual runtime type
```

--> The DEFAULT implementations inherited straight from `Object` are rarely what's wanted for custom classes -- default `equals()` only returns true for the literal same object in memory, and default `toString()` produces an unhelpful string like `Dog@1b6d3586`. Overriding `equals()`, `hashCode()`, and `toString()` properly is common enough, and has enough of its own rules and gotchas (especially the `equals`/`hashCode` contract), that it gets a full dedicated Deep Dive in a later file (07) -- this file only introduces that they exist and where they come from.
--> `getClass()` is `final` -- it cannot be overridden, since it needs to reliably report the object's true runtime type no matter what.

# The `final` Keyword

--> `final` means "cannot be changed further" -- its exact meaning depends on what it's applied to.

```java
final class Utility {                  // cannot be extended AT ALL -- no subclasses permitted
    // ...
}

class Animal {
    final void breathe() {             // cannot be overridden by any subclass
        System.out.println("Breathing...");
    }
}

class Constants {
    final double PI_APPROX = 3.14159;  // cannot be reassigned after initialization
}
```

--> **`final` class** -- no class may extend it. Common for utility classes (like `Math`) and for classes designed to be used exactly as-is, where allowing subclasses could break invariants the class relies on (String is the most famous example -- it's `final` specifically so nobody can create a subclass that changes its immutability guarantees while still being usable anywhere a `String` is expected).
--> **`final` method** -- no subclass may override it. Used to lock down behavior that must stay consistent across every subclass, often for safety (a method that other logic depends on behaving exactly one way) or because the method is called from a constructor and overriding it would be dangerous (see the gotcha below).
--> **`final` field** -- can only be assigned once: either at declaration, or exactly once inside every constructor. After that, it's permanently fixed for the life of the object. This is a common building block for making objects immutable.
--> **`final` local variable / parameter** -- cannot be reassigned within the method once given a value; commonly used for values captured by a lambda or inner class, which require the captured variable to be effectively final.

# Abstract Classes -- A Preview

--> Sometimes a superclass should define shared structure but deliberately leave some methods with NO implementation at all, forcing every subclass to supply their own -- that's what `abstract` classes and `abstract` methods are for. An `abstract` class cannot be instantiated directly (`new Animal()` would be illegal if `Animal` were abstract), only its concrete subclasses can be.
--> This is covered in full in its own dedicated file, since it deserves proper treatment alongside interfaces -- it's mentioned here only so the concept isn't a total surprise: inheritance and abstraction work hand in hand, and "should this superclass be abstract?" is a design question that comes up constantly once real hierarchies are being built.

# Common Gotchas

--> **Field hiding vs method overriding -- fields do NOT participate in dynamic dispatch.** If a subclass declares a field with the same name as a superclass field, it HIDES the superclass field rather than overriding it -- which field is accessed is decided at COMPILE TIME based on the REFERENCE's declared type, not the object's actual runtime type. This is the single most surprising gotcha for people coming from languages where "everything is virtual."

```java
class Animal {
    String category = "Animal";
}

class Dog extends Animal {
    String category = "Dog";          // hides Animal.category, does NOT override it
}

Animal a = new Dog();
System.out.println(a.category);        // prints "Animal" -- decided by the reference TYPE (Animal), not the object
System.out.println(((Dog) a).category); // prints "Dog" -- only visible after casting the reference type
```

--> Compare this to method overriding, where `a.makeSound()` on the same reference WOULD call `Dog`'s version, because methods ARE dynamically dispatched based on the object's actual runtime type. This asymmetry -- fields resolve by reference type, methods resolve by object type -- is exactly why designing classes with same-named fields across a hierarchy is discouraged; it silently produces different answers depending on which type the code happens to be looking through.

--> **Calling an overridable method from a superclass constructor is dangerous.** Because constructor chaining runs superclass constructors BEFORE the subclass's own field initializers and constructor body, calling an overridden method from the superclass constructor invokes the SUBCLASS's override -- but at that point, the subclass's own fields haven't been initialized yet.

```java
class Animal {
    Animal() {
        describe();                      // calls the OVERRIDDEN version, even from here
    }

    void describe() {
        System.out.println("I am an animal");
    }
}

class Dog extends Animal {
    String breed = "Labrador";

    @Override
    void describe() {
        System.out.println("I am a " + breed);   // breed is still null here! Dog()'s field initializers haven't run yet
    }
}

new Dog();   // prints "I am a null" -- NOT "I am a Labrador", because Animal() runs before Dog's fields are set
```

--> The fix is straightforward once it's understood: avoid calling overridable (non-final, non-private, non-static) methods from a constructor entirely -- either make the method `final` (so there's no override to worry about), or move the logic into a separate initialization step called explicitly after full construction completes.

--> **Accidentally overloading instead of overriding.** A tiny mismatch in the parameter list -- wrong type, extra parameter, different order -- silently creates a brand new overload instead of overriding anything, and the superclass method remains completely untouched and still reachable through a superclass-typed reference. This bug is dangerous specifically BECAUSE the code still compiles and runs; it just quietly does the wrong thing.

```java
class Animal {
    void makeSound(String volume) {
        System.out.println("Generic sound at volume " + volume);
    }
}

class Dog extends Animal {
    void makeSound(int volume) {          // NOT an override -- different parameter TYPE (int vs String)
        System.out.println("Woof at volume " + volume);
    }
}
```

--> Without `@Override`, this compiles cleanly, and it's easy to believe `Dog`'s `makeSound` "replaces" `Animal`'s -- it doesn't; both now exist side by side as overloads. Always add `@Override` on every intended override; if the signature doesn't actually match, the compiler will refuse to compile and immediately reveal the mistake instead of leaving it as a silent logic bug waiting to be discovered.

--> **Forgetting that private methods are never inherited.** A `private` method in the superclass isn't visible to subclasses at all, so a same-named method in the subclass isn't overriding or even hiding anything -- it's simply a brand new, unrelated method that happens to share a name.

--> **Widening an inherited exception in an override is a compile error, not a runtime surprise** -- this one at least fails loudly and immediately, but it trips people up until the exception rule (narrower or same checked exceptions only) is internalized, especially since unchecked exceptions have no such restriction at all.
