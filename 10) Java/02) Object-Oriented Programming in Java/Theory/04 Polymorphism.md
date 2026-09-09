# Why Polymorphism Matters

--> "Polymorphism" literally means "many forms" -- it's the ability for the SAME piece of code (a method call, a variable of a supertype) to behave DIFFERENTLY depending on the actual type involved. It's what lets a single `List<Shape> shapes` be filled with `Circle`, `Rectangle`, and `Triangle` objects, and a single loop calling `shape.area()` on each one automatically run the right calculation for each specific shape -- no `if (shape instanceof Circle) ... else if ...` chain required.
--> Java has two distinct kinds of polymorphism that behave very differently under the hood: COMPILE-TIME polymorphism (overloading) and RUNTIME polymorphism (overriding). Confusing which one applies in a given situation is the source of most polymorphism-related bugs.

# Compile-Time Polymorphism -- Method Overloading

--> Overloading means multiple methods in the same class share a name but differ in their parameter list -- which one gets called is decided ENTIRELY at compile time, by the compiler matching the arguments in the call against the available signatures. This is also called "static binding" or "early binding."

```java
class Printer {
    void print(int x) {
        System.out.println("int: " + x);
    }

    void print(double x) {
        System.out.println("double: " + x);
    }

    void print(String x) {
        System.out.println("String: " + x);
    }

    void print(int x, int y) {
        System.out.println("two ints: " + x + ", " + y);
    }
}
```

--> The compiler looks at the STATIC (declared) types of the arguments at the call site and picks the single best-matching overload -- this decision is baked into the compiled bytecode and never changes at runtime, unlike overriding.

## Overload Resolution Rules -- What "Best Match" Means

--> When several overloads could technically apply, Java picks among them using a strict, ordered set of phases -- it does NOT simply pick "the closest" in some vague sense, it works through phases and stops at the first one that finds exactly one applicable match.

```text
Phase 1 -- Exact match / widening primitive conversion only (no boxing, no varargs)
           int -> long -> float -> double  (widening chain, no autoboxing allowed yet)
Phase 2 -- Same as above, but autoboxing/unboxing is now allowed
           int -> Integer,  double -> Double, etc.
Phase 3 -- Same as above, but varargs methods are now allowed
           only considered as an absolute last resort
```

```java
class Overload {
    static void call(long x)     { System.out.println("long"); }
    static void call(Integer x)  { System.out.println("Integer"); }
    static void call(int... x)   { System.out.println("varargs"); }

    public static void main(String[] args) {
        call(5);          // prints "long" -- Phase 1 widening (int->long) beats Phase 2 boxing (int->Integer)
                           // and both beat Phase 3 varargs
    }
}
```

--> **Widening is preferred over boxing, and boxing is preferred over varargs.** This surprises a lot of people the first time -- intuitively `call(Integer x)` looks like the "closer" match for an `int` argument, but Java tries every possible match WITHOUT autoboxing first, and a widening primitive conversion (`int` to `long`) qualifies without boxing, so it wins.
--> **`null` resolves to the MOST SPECIFIC applicable reference type overload.** If `print(String x)` and `print(Object x)` both exist, `print(null)` calls `print(String)`, because `String` is more specific than `Object`. If two unrelated reference types both match equally and neither is more specific than the other, it's a compile-time AMBIGUITY error.

```java
class NullOverload {
    static void call(String x) { System.out.println("String version"); }
    static void call(Object x) { System.out.println("Object version"); }

    public static void main(String[] args) {
        call(null);         // "String version" -- String is more specific than Object
    }
}
```

# Runtime Polymorphism -- Method Overriding and Dynamic Dispatch

--> Overriding (covered in the Inheritance file) is where runtime polymorphism happens -- when a method is called on a REFERENCE, and that method has been overridden somewhere in the object's actual class hierarchy, the version that runs is chosen based on the object's ACTUAL runtime type, not the reference's declared (static) type. This is "dynamic binding" or "late binding," and it's the mechanism that makes polymorphic collections genuinely useful.

```java
class Animal {
    void makeSound() {
        System.out.println("Some generic sound");
    }
}

class Dog extends Animal {
    @Override
    void makeSound() {
        System.out.println("Woof");
    }
}

class Cat extends Animal {
    @Override
    void makeSound() {
        System.out.println("Meow");
    }
}

Animal a = new Dog();     // reference type is Animal, actual object type is Dog
a.makeSound();              // prints "Woof" -- decided by the OBJECT's type, not the reference's declared type
```

## How the JVM Actually Performs Dynamic Dispatch (Conceptually -- the "vtable" Idea)

--> Conceptually, each class the JVM loads has an associated table of method pointers -- often called a "virtual method table" or vtable, a concept borrowed from how many object-oriented language runtimes implement dynamic dispatch (the JVM's actual internal mechanism, using per-class method tables resolved through the constant pool, is more nuanced, but the vtable model is the right level of understanding for reasoning about behavior).
--> For each non-static, non-private, non-final method, the table holds the address of whichever implementation is "current" for that class -- if a subclass overrides a method, its ENTRY in that slot is replaced with the subclass's implementation; if it doesn't override the method, the slot still points at the inherited superclass implementation.
--> When code calls `a.makeSound()`, the JVM doesn't look at the compile-time type of `a` at all for WHICH implementation to run -- it follows the actual object's class's table, finds the `makeSound` slot, and jumps to whatever implementation is registered there for that object's real class. This lookup happens at every call, which is why it's called "dynamic" -- the decision genuinely happens while the program is running, based on the object actually sitting in memory, not on what the source code's variable declaration says.

```text
Animal's table:      makeSound -> Animal.makeSound
Dog's table:          makeSound -> Dog.makeSound        (overridden slot)
Cat's table:          makeSound -> Cat.makeSound        (overridden slot)

Animal a = new Dog();
a.makeSound()  -->  JVM checks a's ACTUAL class (Dog) -->  follows Dog's table  -->  runs Dog.makeSound
```

--> **This is exactly why `static`, `private`, and `final` methods are NOT dynamically dispatched** -- they can't be overridden at all (static methods are hidden, not overridden; private methods aren't inherited; final methods are locked), so there's no "slot" that could ever point anywhere other than the exact method being called, and the compiler can resolve the call directly rather than through a runtime lookup.

```java
class Parent {
    static void staticMethod() {
        System.out.println("Parent static");
    }
}

class Child extends Parent {
    static void staticMethod() {          // hides, does not override
        System.out.println("Child static");
    }
}

Parent p = new Child();
p.staticMethod();      // prints "Parent static" -- resolved at COMPILE TIME by the reference's declared type
Child.staticMethod();  // the correct, non-misleading way to call a static method
```

# Upcasting and Downcasting

--> Casting between a class and its supertype/subtype changes how a reference is TREATED -- it never changes the actual object in memory, only what the compiler will allow to be done through that particular reference.

## Upcasting -- Implicit and Safe

--> Assigning a subclass instance to a superclass-typed reference is upcasting -- it's always safe and happens IMPLICITLY, no cast operator needed, because every `Dog` genuinely IS an `Animal` (the whole point of the IS-A relationship from the Inheritance file).

```java
Animal a = new Dog();       // upcast -- implicit, always safe
```

--> After upcasting, the reference can only directly access members declared in the SUPERCLASS's type (`Animal`'s members) -- even though the underlying object is still fully a `Dog` with all its `Dog`-specific members intact in memory, `a.bark()` would be a COMPILE ERROR through an `Animal`-typed reference, because the compiler only checks what the DECLARED type promises exists.

## Downcasting -- Explicit and Risky

--> Going the other way -- treating a superclass-typed reference as its more specific subclass type -- requires an EXPLICIT cast, because the compiler cannot guarantee it's actually safe; it might compile, but throw `ClassCastException` at runtime if the object isn't actually of that subclass.

```java
Animal a = new Dog();
Dog d = (Dog) a;             // downcast -- explicit cast required, safe here because a really IS a Dog
d.bark();                     // now Dog-specific members are accessible again

Animal a2 = new Cat();
Dog d2 = (Dog) a2;           // compiles fine, but throws ClassCastException at RUNTIME -- a2 is actually a Cat
```

--> **The safe pattern -- check with `instanceof` before downcasting.**

```java
if (a instanceof Dog) {
    Dog d = (Dog) a;
    d.bark();
}
```

# The `instanceof` Operator

--> `instanceof` checks whether an object's ACTUAL runtime type is the given type, or a subtype of it, returning a `boolean` -- it's the standard way to guard a downcast so it never throws.

```java
Animal a = new Dog();
System.out.println(a instanceof Dog);       // true
System.out.println(a instanceof Animal);     // true -- Dog IS-A Animal, so this is also true
System.out.println(a instanceof Cat);        // false
System.out.println(a instanceof Object);     // true -- everything is an Object
```

--> `null instanceof AnyType` always evaluates to `false` -- there's no need to null-check separately before an `instanceof` test, it's already safe against `null`.

## Pattern Matching for `instanceof` (Java 16+)

--> Modern Java lets the `instanceof` check and the cast/variable-declaration happen in a single expression -- if the check succeeds, a new variable of the checked type is immediately available, already cast, scoped to wherever the compiler can prove the check was true.

```java
// Old style -- separate check, then separate explicit cast
if (a instanceof Dog) {
    Dog d = (Dog) a;
    d.bark();
}

// Pattern matching instanceof -- check and cast in one step
if (a instanceof Dog d) {
    d.bark();                 // d is already a Dog here, no separate cast line needed
}
```

--> The pattern variable's scope follows normal flow-typing rules -- it's usable anywhere the compiler can prove the `instanceof` check was true, which includes past an early `return`/`continue`/`break` in the negative branch.

```java
void describe(Animal a) {
    if (!(a instanceof Dog d)) {
        return;                  // if this returns, d was NOT a Dog
    }
    d.bark();                     // reachable only when a WAS a Dog -- d is valid here too
}
```

--> This removes an entire category of "forgot to cast" or "cast to the wrong type" bugs, and it's now the idiomatic style in modern Java codebases over the old two-step check-then-cast pattern.

# Polymorphic Arrays and Collections

--> Because of upcasting, an array or collection declared with a supertype element type can freely hold a mix of different subtype objects -- this is precisely the mechanism that makes "process a list of Shapes generically" possible.

```java
Animal[] animals = { new Dog(), new Cat(), new Dog() };
for (Animal a : animals) {
    a.makeSound();          // dynamic dispatch picks the right override for each actual object
}
```

## The Array Covariance Gotcha -- `ArrayStoreException`

--> Java arrays are COVARIANT -- a `Dog[]` can be assigned to an `Animal[]`-typed reference, because `Dog` IS-A `Animal`. This sounds convenient, but it opens a hole the compiler cannot fully check: the array's ACTUAL runtime element type is still `Dog[]`, even though the reference is typed `Animal[]`.

```java
Dog[] dogs = new Dog[3];
Animal[] animals = dogs;         // legal -- array covariance, compiles fine

animals[0] = new Cat();           // compiles fine too (Cat IS an Animal)... but throws ArrayStoreException at RUNTIME!
                                    // because the array is ACTUALLY a Dog[] underneath, and a Cat can't go in it
```

--> The compiler only checks against the DECLARED type (`Animal[]`), but the JVM performs an additional RUNTIME check on every array store specifically because of this hole, and throws `ArrayStoreException` the moment an incompatible element is actually written in. This runtime check is a real (small but nonzero) performance cost paid on every array element assignment, purely to cover for a gap array covariance introduces.
--> **Generic collections (`List<Animal>`) deliberately do NOT have this problem** -- generics are invariant by default (`List<Dog>` is not a `List<Animal>` at all, they're unrelated types to the compiler) and generic type information is erased at runtime anyway (type erasure, covered in the Generics file), so there's no equivalent runtime hole to guard against; the mismatched-type mistake that array covariance allows at runtime is instead caught by the compiler at COMPILE time for collections, which is strictly better. This is one of the concrete, practical reasons modern Java code favors `List<T>` over raw arrays for polymorphic collections whenever flexibility is needed.

# Fields Are NOT Polymorphic

--> This deserves repeating from the Inheritance file's gotcha section because it's central to understanding what polymorphism actually covers: ONLY methods (specifically, non-static, non-private, non-final methods) are dynamically dispatched. Fields are always resolved by the REFERENCE's declared (static) type, at compile time -- there is no such thing as "field overriding," only field HIDING.

```java
class Animal {
    String type = "Animal";
    void identify() {
        System.out.println("I am a(n) " + type);
    }
}

class Dog extends Animal {
    String type = "Dog";                 // hides Animal.type -- NOT polymorphic
    @Override
    void identify() {
        System.out.println("I am specifically a " + type);   // Dog.identify uses Dog.type -- overriding IS polymorphic
    }
}

Animal a = new Dog();
System.out.println(a.type);        // "Animal" -- field access resolved by reference type (Animal), NOT polymorphic
a.identify();                        // "I am specifically a Dog" -- method call IS polymorphic, dispatched by object type
```

--> The practical rule: never rely on field access through a supertype reference to reflect subclass-specific state -- if subclass-specific data needs to be visible polymorphically, expose it through an (overridable) method or a getter, never through a raw field with the same name reused down the hierarchy.

# Common Gotchas

--> **Overload resolution surprises with autoboxing, varargs, and `null`.** As shown above, Java tries widening BEFORE boxing, and boxing BEFORE varargs -- a call that "looks like" it should hit the `Integer` overload might actually hit a `long` overload instead, purely because widening was tried first and succeeded. When in doubt, be explicit about argument types (`(long) 5` or `Integer.valueOf(5)`) rather than relying on which overload the compiler happens to prefer.
--> **Overloading is resolved by the STATIC type of the argument, not its runtime type -- this can silently defeat what looks like polymorphism.**

```java
class Handler {
    void handle(Animal a) { System.out.println("handling an Animal"); }
    void handle(Dog d)     { System.out.println("handling a Dog"); }

    public static void main(String[] args) {
        Handler h = new Handler();
        Animal a = new Dog();          // static type Animal, runtime type Dog
        h.handle(a);                    // prints "handling an Animal" -- overload chosen by STATIC type, not runtime type!
    }
}
```

--> This is a genuinely common source of confusion -- overriding is chosen by runtime type, but overloading (which of several DIFFERENTLY-signatured methods gets called) is chosen by static/compile-time type, and the two rules can point at different answers for the exact same variable, depending on whether the difference between the candidate methods is an override relationship or an overload relationship.
--> **Downcasting without an `instanceof` guard is a runtime landmine.** It will compile every time (as long as the target type is somewhere in the same hierarchy as the source type), and often runs fine in testing if the "wrong" subtype never happens to show up -- then throws `ClassCastException` in production the first time it does. Always guard with `instanceof` (ideally the pattern-matching form) unless the code has some other airtight guarantee of the object's actual type.
--> **Array covariance's `ArrayStoreException` only shows up at the exact line that performs the bad write, not at the line that created the aliasing** -- `Animal[] animals = dogs;` compiles and runs fine by itself; the exception only fires later, at `animals[i] = someCat;`, which can make the root cause (the covariant assignment much earlier) less obvious when debugging. Preferring `List<T>` sidesteps this category of bug entirely, as covered above.
--> **`equals()` overloading vs overriding trap** -- writing `boolean equals(Dog other)` in a class does NOT override `Object`'s `equals(Object obj)` -- it's a different parameter type, so it's a new overload sitting alongside the inherited `equals(Object)`, and code that calls `equals()` through an `Object` or collection context (like `List.contains()`) will silently use the useless default `Object.equals()` instead. Always use `@Override` and match the exact `equals(Object obj)` signature -- this exact trap is explored in full in the `equals`/`hashCode`/`toString` deep-dive file.
