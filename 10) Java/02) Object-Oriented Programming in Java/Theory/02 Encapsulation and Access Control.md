# Encapsulation -- What It Is and Why It Matters

--> ENCAPSULATION is the bundling of an object's internal STATE (fields) together with the BEHAVIOR (methods) that operates on that state, while restricting direct outside access to the state itself -- the object exposes a controlled, deliberate API and hides everything about HOW it maintains its internal correctness.
--> The core motivation is INVARIANT PROTECTION -- an object often has rules that must always hold true (a bank balance never negative, a percentage always between 0 and 100, a list of items never containing `null`). If any outside code can reach in and directly overwrite a field, NOTHING can guarantee those rules stay true. Routing all state changes through methods means the object itself is the single place responsible for enforcing its own correctness.
--> A second, equally important motivation is CHANGE ISOLATION -- if internal representation is fully private and only exposed through methods, the internal representation can be changed later (e.g. switching from storing a `List<Order>` to a `Map<Long, Order>`) without breaking any code outside the class, as long as the public method signatures stay the same. Public fields make that kind of refactor a breaking change for every caller.

```java
// Without encapsulation -- any code anywhere can violate the invariant
class BankAccountBroken {
    public double balance;   // public field -- no protection at all
}

BankAccountBroken acc = new BankAccountBroken();
acc.balance = -500;          // perfectly legal, and nonsensical -- nothing stops this

// With encapsulation -- the class itself enforces the rule
class BankAccountSafe {
    private double balance;

    public void deposit(double amount) {
        if (amount <= 0) throw new IllegalArgumentException("deposit must be positive");
        balance += amount;
    }

    public double getBalance() { return balance; }
}
```

--> **Gotcha -- encapsulation is not just "make fields private and add getters/setters for everything."** Blindly generating a getter and setter for every private field (an IDE can do this automatically) recreates the exact same problem as public fields -- any code can still set the field to anything through the setter. Genuine encapsulation means the setters (if they exist at all) VALIDATE, and some fields legitimately get no setter at all because they should never change after construction.

# Getters and Setters

--> Convention: a getter for a field named `balance` is `getBalance()` (or, for `boolean` fields specifically, `isBalance()`/`isActive()`/etc. by convention rather than `getActive()`); the matching setter is `setBalance(double balance)`. This `get`/`is`/`set` naming convention is called the JAVABEANS convention, and a huge amount of tooling (frameworks, serialization libraries, IDEs) relies on it being followed consistently.

```java
public class Product {
    private String name;
    private double price;
    private boolean discontinued;

    public String getName() { return name; }
    public void setName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name cannot be blank");
        }
        this.name = name;
    }

    public double getPrice() { return price; }
    public void setPrice(double price) {
        if (price < 0) throw new IllegalArgumentException("price cannot be negative");
        this.price = price;
    }

    public boolean isDiscontinued() { return discontinued; }   // boolean getter uses "is", not "get"
    public void setDiscontinued(boolean discontinued) { this.discontinued = discontinued; }
}
```

--> **Validation belongs in the setter (or constructor), not scattered at call sites.** Every path that can change `price` should go through `setPrice`, so the `price < 0` check only needs to exist in ONE place and is guaranteed to run no matter which code is doing the setting.
--> **Read-only properties** -- provide a getter with no matching setter, for state that should be visible but never changed after construction (an ID, a creation timestamp, a computed value). **Write-only properties** are rare but exist -- e.g. a `setPassword(String password)` that hashes and stores a digest, deliberately without any `getPassword()`, because the raw value should never be readable again once set.
--> **Not every field needs a getter or a setter at all.** A purely internal bookkeeping field (an internal cache, a dirty-flag, a lock object) that outside code never legitimately needs to read or write should simply have neither -- exposing it "just in case" is over-exposure, covered further below.
--> **Computed/derived getters are fine and common** -- a getter does not have to return a field directly; `getFullName()` returning `firstName + " " + lastName` (with no `fullName` field ever stored) is a perfectly ordinary getter, and often preferable to storing and having to keep a derived value in sync.

# Access Modifiers -- The Four Levels of Visibility

--> Java has exactly four access levels, and (unusually) only one of them has an actual keyword missing -- "package-private" is signified by writing NO modifier at all, which is why it's also called the "default" access level.

```text
private              -- visible only within the SAME class (including other instances of that class)
(no modifier)         -- visible within the SAME package only            ("package-private" / "default")
protected             -- visible within the SAME package, PLUS subclasses in other packages
public                -- visible everywhere, from any package
```

--> **Comparison table -- who can access a member with each modifier:**

| Modifier         | Same class | Same package (non-subclass) | Subclass, different package | Any other class (world) |
|------------------|:----------:|:----------------------------:|:----------------------------:|:------------------------:|
| `private`        | Yes        | No                            | No                            | No                        |
| *(default)*      | Yes        | Yes                           | No                            | No                        |
| `protected`      | Yes        | Yes                           | Yes                           | No                        |
| `public`         | Yes        | Yes                           | Yes                           | Yes                       |

--> **`private`** -- the strongest restriction, and the right DEFAULT choice for fields in almost all cases. Only code physically inside the same class body can reference a `private` member -- not even a subclass in the SAME file can touch it directly.
--> **package-private (default)** -- used for things meant to be shared among cooperating classes that live together in one package (e.g. helper classes an entire package's internal implementation relies on) but that should not be part of the public API surface other packages depend on.
--> **`protected`** -- primarily exists to support inheritance -- it says "subclasses, wherever they live, are allowed to see and use this," which is useful for fields/methods a subclass is expected to extend or override behavior around, while still keeping it hidden from unrelated code. Note it also grants package-level access as a side effect (same as default), which is occasionally a surprise.
--> **`public`** -- the class's actual API contract with the rest of the world. Every `public` member is effectively a promise: changing its signature or behavior later can break any external code depending on it, which is exactly why minimizing what's `public` (exposing only what genuinely needs to be called from outside) matters so much.

```java
package com.example.accounts;

public class Account {
    private double balance;              // only Account itself can touch this directly
    String accountType;                  // package-private -- other classes in com.example.accounts can read/write
    protected String ownerName;          // package peers AND subclasses (any package) can access
    public String accountNumber;         // anyone, anywhere -- (illustrative; a real class would encapsulate this too)
}
```

--> **Class-level access** -- a top-level class itself can only be declared `public` or package-private (default) -- `private` and `protected` are not legal on a top-level class declaration, only on its MEMBERS (fields, methods, constructors) or on NESTED classes, where all four levels are valid.
--> **Gotcha -- a `public` class does not make its members `public` automatically.** Each member's access is independent of the class's own access level; a `public class` can still have entirely `private` fields (this is in fact the normal, expected shape of a well-encapsulated public class).

# Immutability Patterns

--> An IMMUTABLE object is one whose observable state can never change after construction -- once built, every field stays fixed for the object's entire lifetime. `String`, the wrapper classes (`Integer`, `Double`, etc.), and `java.time` classes (`LocalDate`, `Instant`, etc.) are all immutable in the standard library.
--> **The standard recipe for an immutable class:**

```text
1. Make the class itself `final` (or otherwise prevent subclassing) -- so a subclass can't add mutable state or override methods to break the immutability contract.
2. Make every field `private` and `final`.
3. Provide no setters -- state is supplied once, entirely through the constructor.
4. If any field is a reference to a MUTABLE type (an array, a List, a Date, another mutable object),
   defensively COPY it on the way in (constructor) and on the way out (any getter that would return it),
   so nobody outside can hold a reference that lets them mutate your internal state.
```

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ImmutablePoint3D {          // final -- cannot be subclassed
    private final double x;
    private final double y;
    private final double z;
    private final List<String> tags;             // a mutable type held internally

    public ImmutablePoint3D(double x, double y, double z, List<String> tags) {
        this.x = x;
        this.y = y;
        this.z = z;
        // Defensive copy IN -- if we stored `tags` directly, the caller's own reference
        // could still mutate our "immutable" object after construction.
        this.tags = new ArrayList<>(tags);
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }

    public List<String> getTags() {
        // Defensive copy OUT (or an unmodifiable wrapper) -- otherwise the caller receives
        // a live reference to our internal list and can mutate it directly, e.g. tags.clear().
        return Collections.unmodifiableList(tags);
    }
}
```

--> **Why immutability is valuable** -- immutable objects are automatically thread-safe (no synchronization needed, since there's no mutable state to race on), safe to share freely and cache without defensive copying by the CALLER, and impossible to put into an invalid state after construction (if the constructor validates and the object never changes, it stays valid forever).
--> **The Builder Pattern -- a preview.** When an immutable class has many fields (especially many optional ones), a constructor with a dozen parameters becomes unreadable and error-prone (easy to swap two `String` arguments of the same type by position). The BUILDER PATTERN addresses this: a separate mutable `Builder` object accumulates configuration via chained setter-like methods (using the `this`-returning method-chaining technique from the Classes/Constructors file), then a final `build()` call constructs the actual immutable object in one shot.

```java
public final class Pizza {
    private final String size;
    private final boolean cheese;
    private final boolean pepperoni;

    private Pizza(Builder b) {           // private constructor -- only the Builder can call it
        this.size = b.size;
        this.cheese = b.cheese;
        this.pepperoni = b.pepperoni;
    }

    public static class Builder {
        private String size = "medium";   // sensible defaults
        private boolean cheese = false;
        private boolean pepperoni = false;

        public Builder size(String size) { this.size = size; return this; }         // fluent chaining
        public Builder cheese(boolean v) { this.cheese = v; return this; }
        public Builder pepperoni(boolean v) { this.pepperoni = v; return this; }

        public Pizza build() { return new Pizza(this); }
    }
}

// Usage: new Pizza.Builder().size("large").cheese(true).build();
```

--> The Builder pattern is covered in full depth (including validation strategies and the "telescoping constructor" problem it solves) in a later Design Patterns chapter -- this is only a preview to connect it conceptually to immutability and encapsulation.

# Encapsulation vs Immutability -- Not the Same Thing

--> **Encapsulation** is about CONTROLLING access to state -- state can still change, but only through the class's own sanctioned methods, which can validate and maintain invariants. `BankAccountSafe` above is well-encapsulated but very much mutable (`balance` legitimately changes over time via `deposit`/`withdraw`).
--> **Immutability** is a stronger, more specific guarantee -- state cannot change AT ALL after construction, by anyone, including the class's own methods (an immutable class's "mutator" methods, if any, return a brand NEW object rather than modifying the existing one -- e.g. `LocalDate.plusDays(1)` returns a new `LocalDate`, it does not modify the receiver).
--> A class can be encapsulated without being immutable (the common case -- most well-designed mutable classes), and in principle a class could be immutable without being well-encapsulated (e.g. `public final` fields with no setters -- technically nothing can change after construction, but it skips the validation/API-flexibility benefits encapsulation provides). In practice, immutable classes are almost always written with `private final` fields, so the two patterns are usually combined.

# Common Gotchas and Best Practices

--> **Returning a mutable reference from a getter ("leaky encapsulation").** This is the single most common way well-intentioned encapsulation quietly fails: a class has a `private List<String> items;` field with no public setter, and looks safe -- but if `getItems()` returns `items` directly, any caller can do `account.getItems().clear()` and mutate the internal list without ever going through a setter or any validation. The field being `private` protects the REFERENCE from being reassigned, but does nothing to protect the OBJECT it points to from being mutated through a returned alias.

```java
class LeakyRoster {
    private List<String> names = new ArrayList<>();
    public List<String> getNames() { return names; }   // LEAK -- returns the live internal list
}

LeakyRoster r = new LeakyRoster();
r.getNames().add("hacked");     // mutates r's internal state with zero validation, from outside

class SafeRoster {
    private List<String> names = new ArrayList<>();
    public List<String> getNames() {
        return List.copyOf(names);         // or Collections.unmodifiableList(names) -- caller can't mutate
    }
    public void addName(String n) { names.add(n); }   // the ONLY sanctioned way to change the list
}
```

--> **The same leak applies to arrays, `Date`, `StringBuilder`, and any other mutable type** -- arrays especially, since `array.clone()` or returning a copy is easy to forget, and there is no built-in "unmodifiable array" wrapper the way there is for collections.
--> **Over-exposing internal state "just in case it's useful later."** Every `public` getter/setter is part of the class's permanent API contract -- adding accessors speculatively, before there's an actual need, tends to accumulate into a class where callers reach in and manipulate internals directly instead of asking the object to do the work, which defeats the entire purpose of encapsulation. Prefer exposing BEHAVIOR ("tell the object what to do") over exposing raw STATE ("ask for the data and do it yourself") wherever reasonable -- this is sometimes summarized as "Tell, Don't Ask."
--> **Anemic getter/setter classes.** A class that is nothing but private fields with a public getter AND a public setter for every single one, and no other behavior, provides essentially the same lack of protection as making every field public -- any invariant spanning multiple fields (e.g. "endDate must be after startDate") cannot be enforced anywhere, because each field can be set independently with no coordination. This is sometimes called an ANEMIC DOMAIN MODEL. True encapsulation means the object's methods do meaningful validation and coordinate multi-field invariants, not just mechanically forward each field through matching accessor pairs.
--> **Constructors are the first line of encapsulation.** Validation belongs in the constructor just as much as in setters -- an object should never be observable in an invalid state, not even momentarily right after construction. If a class is immutable (no setters at all), the constructor is the ONLY enforcement point, making its validation especially critical.
--> **Package-private and protected are also encapsulation tools, not just `private` vs `public`.** Deliberately choosing package-private for a helper class or method that only cooperating classes in the same package should use is itself an encapsulation decision -- it hides implementation details from the rest of the codebase without going all the way to `private`, which would be too restrictive for legitimate same-package collaborators.
