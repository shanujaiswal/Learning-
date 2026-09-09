# Why SOLID and Design Patterns Matter

--> Everything in files 01-07 (encapsulation, inheritance, polymorphism, abstraction, interfaces, equals/hashCode) are the LANGUAGE MECHANICS of OOP -- the tools Java gives you. SOLID and design patterns are the next layer up: they are DESIGN GUIDANCE for how to actually arrange classes and interfaces using those mechanics so that code stays easy to extend, test, and maintain as it grows. Neither SOLID nor the classic Gang-of-Four (GoF) patterns are Java-specific -- they are language-agnostic OOP wisdom -- but every example below is idiomatic Java.
--> **SOLID** is an acronym for five principles (Robert C. Martin, "Uncle Bob") that describe properties of a WELL-STRUCTURED class/interface design. **Design patterns** are named, reusable SOLUTIONS to recurring design problems -- a shared vocabulary ("just use a Factory here") that lets developers communicate intent quickly. The two are complementary: patterns are frequently the concrete mechanism by which a SOLID principle gets satisfied (e.g. Strategy is essentially the Open/Closed Principle applied to behavior).

```text
S -- Single Responsibility Principle    -- a class should have ONE reason to change
O -- Open/Closed Principle               -- open for extension, closed for modification
L -- Liskov Substitution Principle       -- subtypes must be substitutable for their base types
I -- Interface Segregation Principle      -- many small interfaces beat one fat interface
D -- Dependency Inversion Principle        -- depend on abstractions, not concretions
```

# Single Responsibility Principle (SRP)

--> **"A class should have only one reason to change."** In practice this means a class should have one job / one axis of responsibility, not that a class must only have one method. When a class mixes unrelated concerns (e.g. business logic AND persistence AND formatting), a change to any one of those concerns forces a change to the class, and unrelated concerns risk breaking each other.

```java
// VIOLATION -- Invoice mixes business logic, persistence, AND presentation.
// Three unrelated reasons this class might need to change:
//   1. the tax calculation rules change (business logic)
//   2. the database schema/technology changes (persistence)
//   3. the printed report format changes (presentation)
class Invoice {
    private double amount;

    double calculateTotal() {                 // reason to change #1: business rules
        return amount * 1.18;                   // e.g. tax rate changes
    }

    void saveToDatabase() {                    // reason to change #2: persistence technology
        System.out.println("INSERT INTO invoices ...");
    }

    void printReport() {                        // reason to change #3: report formatting
        System.out.println("Invoice total: " + calculateTotal());
    }
}
```

```java
// FIXED -- each responsibility lives in its own class, each with exactly one reason to change.
class Invoice {
    private double amount;
    double getAmount() { return amount; }
    Invoice(double amount) { this.amount = amount; }
}

class InvoiceCalculator {                       // owns ONLY the business rule
    double calculateTotal(Invoice invoice) {
        return invoice.getAmount() * 1.18;
    }
}

class InvoiceRepository {                        // owns ONLY persistence
    void save(Invoice invoice) {
        System.out.println("INSERT INTO invoices ...");
    }
}

class InvoiceReportPrinter {                      // owns ONLY presentation
    void print(Invoice invoice, InvoiceCalculator calculator) {
        System.out.println("Invoice total: " + calculator.calculateTotal(invoice));
    }
}
```

--> **Why it matters**: smaller, single-purpose classes are easier to unit test in isolation (testing `InvoiceCalculator`'s tax math no longer requires a database), easier to reuse (the same `InvoiceCalculator` can be reused by a report printer AND an API layer), and changes to one concern (swap the persistence technology) cannot accidentally break an unrelated concern (the tax math).

# Open/Closed Principle (OCP)

--> **"Software entities should be open for extension, but closed for modification."** Once a class is written and tested, you should be able to add NEW behavior by ADDING new code (new subclasses, new implementations), not by reopening and editing the existing, already-working class. Editing working code to bolt on a new case is exactly how regressions get introduced into unrelated, previously-correct branches.

```java
// VIOLATION -- adding a new shape means editing calculateArea() and risking every
// existing branch (Circle, Rectangle) every time a new shape type is added.
class AreaCalculator {
    double calculateArea(Object shape) {
        if (shape instanceof Circle) {
            Circle c = (Circle) shape;
            return Math.PI * c.radius * c.radius;
        } else if (shape instanceof Rectangle) {
            Rectangle r = (Rectangle) shape;
            return r.width * r.height;
        }
        // Adding Triangle means coming back here and adding yet another else-if branch.
        throw new IllegalArgumentException("Unknown shape");
    }
}
class Circle { double radius; }
class Rectangle { double width, height; }
```

```java
// FIXED -- an abstraction (Shape) is closed for modification; NEW shapes extend it
// without ever touching AreaCalculator or any existing Shape subclass again.
interface Shape {
    double area();
}

class Circle implements Shape {
    private final double radius;
    Circle(double radius) { this.radius = radius; }
    @Override public double area() { return Math.PI * radius * radius; }
}

class Rectangle implements Shape {
    private final double width, height;
    Rectangle(double width, double height) { this.width = width; this.height = height; }
    @Override public double area() { return width * height; }
}

class Triangle implements Shape {                 // NEW shape -- zero changes to existing code
    private final double base, height;
    Triangle(double base, double height) { this.base = base; this.height = height; }
    @Override public double area() { return 0.5 * base * height; }
}

class AreaCalculator {
    double calculateArea(Shape shape) {             // never needs to change again
        return shape.area();
    }
}
```

--> **The mechanism is almost always polymorphism** -- an abstract class or interface defines a stable contract; new behavior arrives as a new implementation of that contract, and code written against the ABSTRACTION (not the concrete types) never needs to change to support it. This is precisely why files 04 (Polymorphism) and 05 (Abstraction) are prerequisites for applying OCP in practice.

# Liskov Substitution Principle (LSP)

--> **"Objects of a superclass should be replaceable with objects of a subclass without breaking the correctness of the program."** Named for Barbara Liskov. If code works correctly with a `Bird b`, it must continue to work correctly no matter WHICH `Bird` subclass `b` actually refers to at runtime -- a subclass must honor the BEHAVIORAL contract of its superclass, not just its method signatures.

```java
// VIOLATION -- the classic Square-extends-Rectangle trap.
class Rectangle {
    protected int width, height;
    void setWidth(int width) { this.width = width; }
    void setHeight(int height) { this.height = height; }
    int area() { return width * height; }
}

class Square extends Rectangle {
    @Override
    void setWidth(int width) {          // a Square must keep width == height,
        this.width = width;               // so it silently changes BOTH...
        this.height = width;
    }
    @Override
    void setHeight(int height) {         // ...breaking the Rectangle contract that
        this.width = height;               // width and height are independent!
        this.height = height;
    }
}

// Code written against Rectangle assumes width and height vary independently:
void resize(Rectangle r) {
    r.setWidth(5);
    r.setHeight(10);
    assert r.area() == 50;    // TRUE for a real Rectangle, FALSE for a Square (area() == 100)
}
// Passing a Square where a Rectangle is expected breaks a caller that was correct
// for every Rectangle -- Square is NOT substitutable for Rectangle. LSP violated.
```

```java
// FIXED -- don't force an IS-A relationship where the behavioral contract differs.
// Model the actual shared abstraction instead (both HAVE an area, neither must share mutators).
interface Quadrilateral {
    int area();
}

class Rectangle implements Quadrilateral {
    private final int width, height;
    Rectangle(int width, int height) { this.width = width; this.height = height; }
    @Override public int area() { return width * height; }
}

class Square implements Quadrilateral {
    private final int side;
    Square(int side) { this.side = side; }
    @Override public int area() { return side * side; }
}
// Neither class makes a behavioral promise it can't keep -- both are safely
// substitutable anywhere a Quadrilateral is expected.
```

--> **Practical signs of an LSP violation**: a subclass override that throws `UnsupportedOperationException` for a method the base class documents as always working; a subclass that weakens a postcondition (returns a less specific/valid result than the base class promises); a subclass that strengthens a precondition (rejects inputs the base class accepts). Any of these means client code written against the base type can no longer trust the contract once a subclass instance is substituted in.

# Interface Segregation Principle (ISP)

--> **"Clients should not be forced to depend on methods they do not use."** A single, large ("fat") interface forces every implementer to provide (or stub out) methods irrelevant to it. Prefer several small, focused interfaces over one all-encompassing one -- a class implements only the interfaces relevant to what it can actually do.

```java
// VIOLATION -- one fat interface forces EVERY printer to implement fax and scan,
// even a plain printer that physically cannot do either.
interface MultiFunctionDevice {
    void print(String document);
    void fax(String document);
    void scan(String document);
}

class OldPrinter implements MultiFunctionDevice {
    @Override public void print(String document) { System.out.println("Printing: " + document); }
    @Override public void fax(String document) {
        throw new UnsupportedOperationException("This printer cannot fax");   // forced, meaningless stub
    }
    @Override public void scan(String document) {
        throw new UnsupportedOperationException("This printer cannot scan");   // forced, meaningless stub
    }
}
```

```java
// FIXED -- split into small, role-specific interfaces; implement only what applies.
interface Printer { void print(String document); }
interface Fax { void fax(String document); }
interface Scanner { void scan(String document); }

class OldPrinter implements Printer {                 // only implements what it actually supports
    @Override public void print(String document) { System.out.println("Printing: " + document); }
}

class ModernAllInOnePrinter implements Printer, Fax, Scanner {   // opts into everything it supports
    @Override public void print(String document) { System.out.println("Printing: " + document); }
    @Override public void fax(String document) { System.out.println("Faxing: " + document); }
    @Override public void scan(String document) { System.out.println("Scanning: " + document); }
}
```

--> **Why it matters**: fat interfaces couple unrelated clients together -- a change to the `fax` method's signature now forces a recompile/rework of `OldPrinter`, which never even implemented `fax` meaningfully. Small interfaces also compose naturally (a class can implement several) and make the class's actual capabilities self-documenting from its `implements` clause alone.

# Dependency Inversion Principle (DIP)

--> **"High-level modules should not depend on low-level modules; both should depend on abstractions. Abstractions should not depend on details; details should depend on abstractions."** A high-level policy class (e.g. `NotificationService`) should not hard-code a dependency on one specific low-level implementation (e.g. `EmailSender`) -- it should depend on an INTERFACE, with the concrete implementation supplied from outside.

```java
// VIOLATION -- NotificationService (high-level policy) directly constructs and depends
// on EmailSender (a low-level, concrete detail). Adding SMS support means editing
// NotificationService itself, and it can never be tested without sending a real email.
class EmailSender {
    void send(String message) { System.out.println("Emailing: " + message); }
}

class NotificationService {
    private final EmailSender sender = new EmailSender();   // hard-coded concrete dependency
    void notifyUser(String message) {
        sender.send(message);
    }
}
```

```java
// FIXED -- both NotificationService and EmailSender depend on the MessageSender
// abstraction. The concrete sender is INJECTED (constructor injection here), so
// NotificationService never needs to change to support a new channel.
interface MessageSender {
    void send(String message);
}

class EmailSender implements MessageSender {
    @Override public void send(String message) { System.out.println("Emailing: " + message); }
}

class SmsSender implements MessageSender {               // NEW channel -- zero changes to NotificationService
    @Override public void send(String message) { System.out.println("Texting: " + message); }
}

class NotificationService {
    private final MessageSender sender;                    // depends on the ABSTRACTION only

    NotificationService(MessageSender sender) {            // dependency injected via constructor
        this.sender = sender;
    }

    void notifyUser(String message) {
        sender.send(message);
    }
}

// Usage -- the concrete choice is made by the CALLER, not baked into NotificationService:
NotificationService emailService = new NotificationService(new EmailSender());
NotificationService smsService = new NotificationService(new SmsSender());
emailService.notifyUser("Order shipped");
smsService.notifyUser("Order shipped");
```

--> **"Inversion" refers to the reversal of the naive dependency direction** -- naively, high-level code depends directly on low-level code; DIP inverts this so BOTH depend on an abstraction that sits between them, and the low-level detail is what conforms to the abstraction rather than the other way around. This is the principle underlying dependency-injection frameworks (Spring, etc.), but the principle itself needs no framework at all -- plain constructor injection, as shown above, already satisfies it.
--> **DIP and OCP work together** -- because `NotificationService` depends only on `MessageSender`, adding `SmsSender` (OCP: extension without modification) is possible ONLY because `NotificationService` never hard-coded a concrete dependency in the first place (DIP).

# Design Patterns -- Overview

--> A design pattern is a NAMED, battle-tested template for solving a recurring design problem -- not a finished piece of code to copy-paste, but a shape of solution to adapt to the specifics at hand. The original "Gang of Four" (GoF) book (Gamma, Helm, Johnson, Vlissides) grouped 23 patterns into three families; the five covered here span all three families and are the ones most commonly seen in everyday Java code.

```text
Creational   -- concerned with OBJECT CREATION mechanisms      (Singleton, Factory, Builder)
Structural   -- concerned with composing classes/objects        (not covered in depth here)
Behavioral   -- concerned with communication between objects      (Observer, Strategy)
```

# Singleton Pattern

--> **Intent**: ensure a class has exactly ONE instance across the entire application, and provide a single global access point to it. Common for things that are logically singular by nature -- a configuration manager, a connection pool, a logging facility.

```java
// Thread-safe, lazily-initialized Singleton using an enum -- the JVM guarantees
// enum instances are created exactly once, even under concurrent class loading,
// and it comes with serialization-safety for free (Effective Java's recommended approach).
enum AppConfig {
    INSTANCE;

    private String environment = "production";

    String getEnvironment() { return environment; }
    void setEnvironment(String environment) { this.environment = environment; }
}

// Usage -- there is no "new AppConfig()" available anywhere; INSTANCE is the only instance.
AppConfig.INSTANCE.setEnvironment("staging");
System.out.println(AppConfig.INSTANCE.getEnvironment());   // "staging"
```

```java
// Classic class-based form -- private constructor + static factory method,
// with double-checked locking for lazy, thread-safe initialization.
class ConnectionPool {
    private static volatile ConnectionPool instance;   // volatile -- visible across threads immediately

    private ConnectionPool() { }                        // private -- prevents external "new ConnectionPool()"

    static ConnectionPool getInstance() {
        if (instance == null) {                           // first check -- avoids locking on the common path
            synchronized (ConnectionPool.class) {
                if (instance == null) {                     // second check -- guards against a race
                    instance = new ConnectionPool();
                }
            }
        }
        return instance;
    }
}
```

--> **Caution**: Singleton is one of the most OVERUSED patterns -- it introduces global mutable state, which makes unit testing harder (tests can leak state into each other via the shared instance) and hides a class's dependencies (any code can silently reach out to the singleton rather than declaring it as a constructor parameter, which is itself in tension with the Dependency Inversion Principle above). Use it deliberately, not as a default.

# Factory Pattern

--> **Intent**: delegate the decision of WHICH concrete class to instantiate to a dedicated factory method/class, so calling code depends only on an abstraction and never calls `new ConcreteClass()` directly. This is frequently the concrete mechanism satisfying the Open/Closed Principle for object creation specifically.

```java
interface Notification {
    void notifyUser(String message);
}

class EmailNotification implements Notification {
    @Override public void notifyUser(String message) { System.out.println("Email: " + message); }
}

class SmsNotification implements Notification {
    @Override public void notifyUser(String message) { System.out.println("SMS: " + message); }
}

class PushNotification implements Notification {
    @Override public void notifyUser(String message) { System.out.println("Push: " + message); }
}

// Factory -- centralizes the "which concrete class?" decision in ONE place.
class NotificationFactory {
    static Notification create(String type) {
        return switch (type.toUpperCase()) {
            case "EMAIL" -> new EmailNotification();
            case "SMS" -> new SmsNotification();
            case "PUSH" -> new PushNotification();
            default -> throw new IllegalArgumentException("Unknown notification type: " + type);
        };
    }
}

// Calling code never mentions a concrete class -- only the Notification abstraction:
Notification n = NotificationFactory.create("SMS");
n.notifyUser("Your order has shipped");
```

--> **Why it matters**: without the factory, every call site that needs a `Notification` would need its own `if/else` or `switch` picking a concrete class -- scattering the same decision logic across the codebase and making it easy for different call sites to drift out of sync. Centralizing it in one factory means adding a new `Notification` type touches exactly one place.

# Builder Pattern

--> **Intent**: separate the CONSTRUCTION of a complex object (many optional parameters, some of which depend on validation or defaults) from its final representation, avoiding both a "telescoping constructor" (many overloaded constructors differing only in parameter count) and a bare setter-based approach that leaves an object mutable and possibly in an inconsistent partial state.

```java
// Telescoping constructor problem -- unreadable at the call site, and adding
// a new optional field means adding yet another overload.
class Pizza {
    Pizza(String size) { /* ... */ }
    Pizza(String size, boolean cheese) { /* ... */ }
    Pizza(String size, boolean cheese, boolean pepperoni) { /* ... */ }
    Pizza(String size, boolean cheese, boolean pepperoni, boolean mushrooms) { /* ... */ }
    // new Pizza("Large", true, false, true) -- what do these booleans even mean at the call site?
}
```

```java
// FIXED -- Builder with a fluent (chained) API and a fully immutable final Pizza.
class Pizza {
    private final String size;           // required
    private final boolean cheese;         // optional, defaulted
    private final boolean pepperoni;       // optional, defaulted
    private final boolean mushrooms;        // optional, defaulted

    private Pizza(Builder builder) {          // private -- only Builder can construct a Pizza
        this.size = builder.size;
        this.cheese = builder.cheese;
        this.pepperoni = builder.pepperoni;
        this.mushrooms = builder.mushrooms;
    }

    @Override
    public String toString() {
        return "Pizza[size=" + size + ", cheese=" + cheese
                + ", pepperoni=" + pepperoni + ", mushrooms=" + mushrooms + "]";
    }

    static class Builder {
        private final String size;             // required -- passed to Builder's own constructor
        private boolean cheese = false;          // optional fields default sensibly
        private boolean pepperoni = false;
        private boolean mushrooms = false;

        Builder(String size) { this.size = size; }   // required field enforced at Builder construction

        Builder withCheese() { this.cheese = true; return this; }         // each method returns 'this' --
        Builder withPepperoni() { this.pepperoni = true; return this; }    // enables fluent chaining
        Builder withMushrooms() { this.mushrooms = true; return this; }

        Pizza build() {                            // validation can happen here before construction
            return new Pizza(this);
        }
    }
}

// Usage -- reads almost like a sentence, and only the options actually wanted are mentioned:
Pizza order = new Pizza.Builder("Large")
        .withCheese()
        .withMushrooms()
        .build();
System.out.println(order);
```

--> **Why it matters**: the final `Pizza` is fully immutable (all fields `final`, set once via the private constructor) yet was assembled step-by-step with clear, self-documenting method names instead of positional booleans. `Builder` is also the pattern behind `StringBuilder` (chained `.append()` calls) and `java.time`'s various builders, and is a natural complement to records/constructors covered in file 01 when a class has many optional fields.

# Observer Pattern

--> **Intent**: define a one-to-many dependency between objects so that when one object (the SUBJECT/publisher) changes state, all its registered dependents (OBSERVERS/subscribers) are notified automatically, without the subject needing to know anything concrete about its observers beyond a shared interface. This is the foundation of event-driven systems, GUI listeners, and pub/sub messaging.

```java
import java.util.ArrayList;
import java.util.List;

interface Observer {
    void onPriceChanged(String stock, double newPrice);
}

// Subject -- maintains the list of observers and notifies them, but knows
// nothing about WHAT any given observer actually does with the notification.
class Stock {
    private final String symbol;
    private double price;
    private final List<Observer> observers = new ArrayList<>();

    Stock(String symbol, double price) { this.symbol = symbol; this.price = price; }

    void subscribe(Observer observer) { observers.add(observer); }
    void unsubscribe(Observer observer) { observers.remove(observer); }

    void setPrice(double newPrice) {
        this.price = newPrice;
        notifyObservers();                          // subject drives notification on state change
    }

    private void notifyObservers() {
        for (Observer observer : observers) {
            observer.onPriceChanged(symbol, price);    // each observer reacts independently
        }
    }
}

// Concrete observers -- each reacts to the same event completely differently.
class PriceLogger implements Observer {
    @Override public void onPriceChanged(String stock, double newPrice) {
        System.out.println("[LOG] " + stock + " is now $" + newPrice);
    }
}

class PriceAlert implements Observer {
    private final double threshold;
    PriceAlert(double threshold) { this.threshold = threshold; }
    @Override public void onPriceChanged(String stock, double newPrice) {
        if (newPrice > threshold) {
            System.out.println("[ALERT] " + stock + " exceeded $" + threshold + "!");
        }
    }
}

// Usage:
Stock apple = new Stock("AAPL", 150.0);
apple.subscribe(new PriceLogger());
apple.subscribe(new PriceAlert(180.0));
apple.setPrice(190.0);          // both observers fire; PriceAlert also prints an alert
```

--> **Java's built-in support**: `java.util.Observer`/`Observable` (deprecated since Java 9) were the original built-in version of this pattern; modern code either hand-rolls it as above or uses richer reactive/event libraries. GUI event listeners (`ActionListener`, `addActionListener`) are a ubiquitous real-world instance of this exact pattern -- a button (subject) notifies registered listeners (observers) when clicked.

# Strategy Pattern

--> **Intent**: define a family of interchangeable ALGORITHMS, encapsulate each one behind a common interface, and make them swappable at runtime -- the calling code is parameterized by WHICH strategy it holds, rather than branching internally on a type flag or enum to decide which algorithm to run. This is essentially the Open/Closed Principle applied specifically to swappable behavior.

```java
interface DiscountStrategy {
    double applyDiscount(double price);
}

class NoDiscount implements DiscountStrategy {
    @Override public double applyDiscount(double price) { return price; }
}

class PercentageDiscount implements DiscountStrategy {
    private final double percentage;
    PercentageDiscount(double percentage) { this.percentage = percentage; }
    @Override public double applyDiscount(double price) { return price * (1 - percentage / 100); }
}

class FlatDiscount implements DiscountStrategy {
    private final double amountOff;
    FlatDiscount(double amountOff) { this.amountOff = amountOff; }
    @Override public double applyDiscount(double price) { return Math.max(0, price - amountOff); }
}

// The context class holds a strategy and delegates to it -- it never branches
// on discount TYPE itself, and needs zero changes to support a brand-new strategy.
class ShoppingCart {
    private DiscountStrategy discountStrategy;

    ShoppingCart(DiscountStrategy discountStrategy) { this.discountStrategy = discountStrategy; }

    void setDiscountStrategy(DiscountStrategy discountStrategy) {   // swappable at runtime
        this.discountStrategy = discountStrategy;
    }

    double checkout(double subtotal) {
        return discountStrategy.applyDiscount(subtotal);
    }
}

// Usage:
ShoppingCart cart = new ShoppingCart(new PercentageDiscount(10));
System.out.println(cart.checkout(200.0));     // 180.0

cart.setDiscountStrategy(new FlatDiscount(15));   // swap strategy at runtime, no ShoppingCart changes
System.out.println(cart.checkout(200.0));         // 185.0
```

--> **Strategy vs. plain lambdas** -- since `DiscountStrategy` here has exactly one abstract method, it is a functional interface, so in modern Java a lambda can stand in for an entire concrete strategy class without a named implementing class at all: `cart.setDiscountStrategy(price -> price * 0.9);`. This is extremely common in real Java code -- `Comparator` (file 07) is itself a Strategy-pattern interface, and passing a lambda comparator to `list.sort(...)` is the Strategy pattern in its most everyday, lightweight form.

# How SOLID and These Patterns Reinforce Each Other

```text
Pattern      Primarily reinforces
Singleton     (orthogonal -- a creational convenience, not a direct SOLID mechanism;
                overuse can actually work AGAINST DIP by hiding dependencies)
Factory       OCP (new types added without touching call sites) + DIP (callers depend
                only on the created abstraction, never a concrete constructor)
Builder       SRP (construction logic separated from the object's own behavior)
Observer      OCP (new observer types added without changing the subject) + DIP
                (subject depends only on the Observer abstraction)
Strategy      OCP (new algorithms added without changing the context class) + DIP
                (context depends only on the Strategy abstraction, injected from outside)
```

--> **The throughline**: nearly every pattern above achieves its flexibility the same way -- by depending on an INTERFACE or ABSTRACT CLASS rather than a concrete implementation, and letting the concrete choice be supplied, swapped, or extended from outside the class that uses it. This single idea -- program to an interface, not an implementation -- is arguably the one lesson underlying all of SOLID and most of the GoF patterns, and it is only possible because of the polymorphism and abstraction mechanics covered in files 04 and 05.

# Common Gotchas and Best Practices

--> **Applying SOLID/patterns everywhere, on principle, regardless of actual need.** These are tools for managing CHANGE and complexity -- a tiny, stable utility class does not need to be split into five SRP-compliant classes, and a value that will only ever have one implementation does not need a Factory. Over-applying patterns produces "pattern soup" -- more indirection and files than the problem actually warrants, which is its own maintainability cost.
--> **Confusing Strategy with Factory** -- Factory answers "which OBJECT should I create?"; Strategy answers "which ALGORITHM should I run, given an object I already have?" They are frequently used together (a Factory can be used to construct the right Strategy for a given context), but they solve different problems.
--> **Singleton and global mutable state** -- covered above; prefer passing a single shared instance via constructor injection (Dependency Inversion) over reaching for `Singleton.INSTANCE` from deep inside unrelated classes, since the latter hides the dependency and complicates testing.
--> **LSP violations hiding inside seemingly natural inheritance** -- "a Square IS-A Rectangle" is true geometrically but false BEHAVIORALLY once mutIable setters are involved; always ask whether a subclass can honor every behavioral promise of its superclass, not just whether the IS-A relationship sounds correct in English.
--> **ISP violations creeping in over time** -- an interface that starts focused often accumulates unrelated methods as new features are bolted on; periodically ask whether every implementer of an interface actually uses every method on it, and split the interface once that stops being true.
--> **DIP without an actual need for multiple implementations** -- introducing an interface with exactly one implementation "just in case" adds indirection without current benefit; DIP earns its keep when there either already are multiple implementations, or swappability is a genuine near-term requirement (e.g. for testing with a mock/fake).
