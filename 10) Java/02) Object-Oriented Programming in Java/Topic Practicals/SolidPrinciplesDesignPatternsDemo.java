/*
 * SolidPrinciplesDesignPatternsDemo.java
 *
 * Demonstrates:
 *   1. Single Responsibility Principle (SRP) -- Invoice split into calculator/repository/printer.
 *   2. Open/Closed Principle (OCP) -- Shape/AreaCalculator extended with a new shape, zero edits.
 *   3. Liskov Substitution Principle (LSP) -- Quadrilateral/Rectangle/Square avoiding the
 *      classic Square-extends-Rectangle trap.
 *   4. Interface Segregation Principle (ISP) -- Printer/Fax/Scanner instead of one fat interface.
 *   5. Dependency Inversion Principle (DIP) -- NotificationService depends on MessageSender,
 *      concrete sender injected via constructor.
 *   6. Singleton pattern -- enum-based, thread-safe, single instance.
 *   7. Factory pattern -- NotificationFactory centralizing "which concrete class?" decisions.
 *   8. Builder pattern -- Pizza.Builder, fluent chaining, immutable final product.
 *   9. Observer pattern -- Stock subject notifying PriceLogger/PriceAlert observers.
 *  10. Strategy pattern -- DiscountStrategy family, swappable at runtime, plus a lambda strategy.
 *
 * Covers Theory chapter:
 *   10) Java/02) Object-Oriented Programming in Java/Theory/08 SOLID Principles and OOP Design Patterns in Java.md
 *
 * Compile:  javac 08_SolidPrinciplesDesignPatternsDemo.java
 * Run:      java SolidPrinciplesDesignPatternsDemo
 */

import java.util.ArrayList;
import java.util.List;

// ---------------------------------------------------------------------------
// 1) Single Responsibility Principle -- each class has exactly one job
// ---------------------------------------------------------------------------

class Invoice {
    private final double amount;
    Invoice(double amount) { this.amount = amount; }
    double getAmount() { return amount; }
}

class InvoiceCalculator {                          // owns ONLY the business rule
    double calculateTotal(Invoice invoice) {
        return invoice.getAmount() * 1.18;            // e.g. tax rate
    }
}

class InvoiceRepository {                            // owns ONLY persistence
    void save(Invoice invoice) {
        System.out.println("  [DB] INSERT INTO invoices (amount) VALUES (" + invoice.getAmount() + ")");
    }
}

class InvoiceReportPrinter {                          // owns ONLY presentation
    void print(Invoice invoice, InvoiceCalculator calculator) {
        System.out.println("  [REPORT] Invoice total: " + calculator.calculateTotal(invoice));
    }
}

// ---------------------------------------------------------------------------
// 2) Open/Closed Principle -- Shape hierarchy open for extension
// ---------------------------------------------------------------------------

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

class Triangle implements Shape {                    // NEW shape -- zero changes to AreaCalculator
    private final double base, height;
    Triangle(double base, double height) { this.base = base; this.height = height; }
    @Override public double area() { return 0.5 * base * height; }
}

class AreaCalculator {
    double calculateArea(Shape shape) {                // never needs to change, no matter how many Shapes exist
        return shape.area();
    }
}

// ---------------------------------------------------------------------------
// 3) Liskov Substitution Principle -- Quadrilateral, not Square-extends-Rectangle
// ---------------------------------------------------------------------------

interface Quadrilateral {
    int area();
}

class LspRectangle implements Quadrilateral {
    private final int width, height;
    LspRectangle(int width, int height) { this.width = width; this.height = height; }
    @Override public int area() { return width * height; }
}

class LspSquare implements Quadrilateral {
    private final int side;
    LspSquare(int side) { this.side = side; }
    @Override public int area() { return side * side; }
}

// ---------------------------------------------------------------------------
// 4) Interface Segregation Principle -- small, role-specific interfaces
// ---------------------------------------------------------------------------

interface Printer { void print(String document); }
interface Fax { void fax(String document); }
interface Scanner { void scan(String document); }

class OldPrinter implements Printer {                  // only implements what it actually supports
    @Override public void print(String document) { System.out.println("  Printing: " + document); }
}

class ModernAllInOnePrinter implements Printer, Fax, Scanner {
    @Override public void print(String document) { System.out.println("  Printing: " + document); }
    @Override public void fax(String document) { System.out.println("  Faxing: " + document); }
    @Override public void scan(String document) { System.out.println("  Scanning: " + document); }
}

// ---------------------------------------------------------------------------
// 5) Dependency Inversion Principle -- depend on abstraction, inject the detail
// ---------------------------------------------------------------------------

interface MessageSender {
    void send(String message);
}

class EmailSender implements MessageSender {
    @Override public void send(String message) { System.out.println("  Emailing: " + message); }
}

class SmsSender implements MessageSender {              // NEW channel -- zero changes to NotificationService
    @Override public void send(String message) { System.out.println("  Texting: " + message); }
}

class NotificationService {
    private final MessageSender sender;                   // depends on the ABSTRACTION only

    NotificationService(MessageSender sender) {             // dependency injected via constructor
        this.sender = sender;
    }

    void notifyUser(String message) {
        sender.send(message);
    }
}

// ---------------------------------------------------------------------------
// 6) Singleton pattern -- enum-based, thread-safe by construction
// ---------------------------------------------------------------------------

enum AppConfig {
    INSTANCE;

    private String environment = "production";

    String getEnvironment() { return environment; }
    void setEnvironment(String environment) { this.environment = environment; }
}

// ---------------------------------------------------------------------------
// 7) Factory pattern -- centralizes "which concrete class?" decisions
// ---------------------------------------------------------------------------

interface Notification {
    void notifyUser(String message);
}

class EmailNotification implements Notification {
    @Override public void notifyUser(String message) { System.out.println("  Email: " + message); }
}

class SmsNotification implements Notification {
    @Override public void notifyUser(String message) { System.out.println("  SMS: " + message); }
}

class PushNotification implements Notification {
    @Override public void notifyUser(String message) { System.out.println("  Push: " + message); }
}

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

// ---------------------------------------------------------------------------
// 8) Builder pattern -- fluent construction, immutable final product
// ---------------------------------------------------------------------------

class Pizza {
    private final String size;
    private final boolean cheese;
    private final boolean pepperoni;
    private final boolean mushrooms;

    private Pizza(Builder builder) {               // private -- only Builder can construct a Pizza
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
        private final String size;                    // required
        private boolean cheese = false;                 // optional, defaulted
        private boolean pepperoni = false;
        private boolean mushrooms = false;

        Builder(String size) { this.size = size; }

        Builder withCheese() { this.cheese = true; return this; }
        Builder withPepperoni() { this.pepperoni = true; return this; }
        Builder withMushrooms() { this.mushrooms = true; return this; }

        Pizza build() { return new Pizza(this); }
    }
}

// ---------------------------------------------------------------------------
// 9) Observer pattern -- subject notifies registered observers automatically
// ---------------------------------------------------------------------------

interface Observer {
    void onPriceChanged(String stock, double newPrice);
}

class Stock {
    private final String symbol;
    private double price;
    private final List<Observer> observers = new ArrayList<>();

    Stock(String symbol, double price) { this.symbol = symbol; this.price = price; }

    void subscribe(Observer observer) { observers.add(observer); }
    void unsubscribe(Observer observer) { observers.remove(observer); }

    void setPrice(double newPrice) {
        this.price = newPrice;
        notifyObservers();
    }

    private void notifyObservers() {
        for (Observer observer : observers) {
            observer.onPriceChanged(symbol, price);
        }
    }
}

class PriceLogger implements Observer {
    @Override public void onPriceChanged(String stock, double newPrice) {
        System.out.println("  [LOG] " + stock + " is now $" + newPrice);
    }
}

class PriceAlert implements Observer {
    private final double threshold;
    PriceAlert(double threshold) { this.threshold = threshold; }
    @Override public void onPriceChanged(String stock, double newPrice) {
        if (newPrice > threshold) {
            System.out.println("  [ALERT] " + stock + " exceeded $" + threshold + "!");
        }
    }
}

// ---------------------------------------------------------------------------
// 10) Strategy pattern -- interchangeable algorithms, swappable at runtime
// ---------------------------------------------------------------------------

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

// ---------------------------------------------------------------------------
// Main class
// ---------------------------------------------------------------------------

public class SolidPrinciplesDesignPatternsDemo {

    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }

    // -----------------------------------------------------------------------
    // Demo 1: Single Responsibility Principle
    // -----------------------------------------------------------------------
    private static void demoSrp() {
        printSection("1) SRP -- Invoice split into calculator / repository / printer");

        Invoice invoice = new Invoice(1000.0);
        InvoiceCalculator calculator = new InvoiceCalculator();
        InvoiceRepository repository = new InvoiceRepository();
        InvoiceReportPrinter printer = new InvoiceReportPrinter();

        repository.save(invoice);
        printer.print(invoice, calculator);

        assert calculator.calculateTotal(invoice) == 1180.0;
    }

    // -----------------------------------------------------------------------
    // Demo 2: Open/Closed Principle
    // -----------------------------------------------------------------------
    private static void demoOcp() {
        printSection("2) OCP -- Shape hierarchy extended with Triangle, AreaCalculator unchanged");

        AreaCalculator calc = new AreaCalculator();
        Shape[] shapes = { new Circle(2), new Rectangle(3, 4), new Triangle(6, 5) };

        for (Shape shape : shapes) {
            System.out.printf("  %-30s area = %.2f%n", shape.getClass().getSimpleName(), calc.calculateArea(shape));
        }
        assert calc.calculateArea(new Rectangle(3, 4)) == 12.0;
    }

    // -----------------------------------------------------------------------
    // Demo 3: Liskov Substitution Principle
    // -----------------------------------------------------------------------
    private static void demoLsp() {
        printSection("3) LSP -- Quadrilateral abstraction avoids the Square-extends-Rectangle trap");

        List<Quadrilateral> shapes = List.of(new LspRectangle(4, 5), new LspSquare(4));
        for (Quadrilateral q : shapes) {
            System.out.println("  " + q.getClass().getSimpleName() + " area = " + q.area());
        }
        // Both are safely substitutable anywhere a Quadrilateral is expected --
        // neither class makes a behavioral promise (like independent width/height mutators) it can't keep.
        assert new LspRectangle(4, 5).area() == 20;
        assert new LspSquare(4).area() == 16;
    }

    // -----------------------------------------------------------------------
    // Demo 4: Interface Segregation Principle
    // -----------------------------------------------------------------------
    private static void demoIsp() {
        printSection("4) ISP -- Printer/Fax/Scanner instead of one fat MultiFunctionDevice interface");

        Printer basic = new OldPrinter();                 // only implements Printer -- no forced stubs
        basic.print("Resume.pdf");

        ModernAllInOnePrinter allInOne = new ModernAllInOnePrinter();
        allInOne.print("Contract.pdf");
        allInOne.fax("Contract.pdf");
        allInOne.scan("Contract.pdf");
    }

    // -----------------------------------------------------------------------
    // Demo 5: Dependency Inversion Principle
    // -----------------------------------------------------------------------
    private static void demoDip() {
        printSection("5) DIP -- NotificationService depends on MessageSender, not a concrete sender");

        NotificationService emailService = new NotificationService(new EmailSender());
        NotificationService smsService = new NotificationService(new SmsSender());   // NEW channel injected

        emailService.notifyUser("Order shipped");
        smsService.notifyUser("Order shipped");
    }

    // -----------------------------------------------------------------------
    // Demo 6: Singleton pattern
    // -----------------------------------------------------------------------
    private static void demoSingleton() {
        printSection("6) Singleton pattern -- enum-based AppConfig, exactly one instance");

        AppConfig.INSTANCE.setEnvironment("staging");
        System.out.println("  AppConfig.INSTANCE.getEnvironment() = " + AppConfig.INSTANCE.getEnvironment());

        // Accessed from "elsewhere" -- still the exact same instance and state:
        AppConfig sameInstance = AppConfig.INSTANCE;
        assert sameInstance == AppConfig.INSTANCE;
        assert sameInstance.getEnvironment().equals("staging");
    }

    // -----------------------------------------------------------------------
    // Demo 7: Factory pattern
    // -----------------------------------------------------------------------
    private static void demoFactory() {
        printSection("7) Factory pattern -- NotificationFactory picks the concrete class");

        Notification email = NotificationFactory.create("EMAIL");
        Notification sms = NotificationFactory.create("SMS");
        Notification push = NotificationFactory.create("PUSH");

        email.notifyUser("Welcome aboard!");
        sms.notifyUser("Your OTP is 4321");
        push.notifyUser("You have a new message");

        try {
            NotificationFactory.create("CARRIER_PIGEON");
        } catch (IllegalArgumentException e) {
            System.out.println("  Caught expected exception: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Demo 8: Builder pattern
    // -----------------------------------------------------------------------
    private static void demoBuilder() {
        printSection("8) Builder pattern -- Pizza.Builder, fluent chaining, immutable result");

        Pizza order = new Pizza.Builder("Large")
                .withCheese()
                .withMushrooms()
                .build();
        System.out.println("  " + order);

        Pizza plain = new Pizza.Builder("Small").build();
        System.out.println("  " + plain);
    }

    // -----------------------------------------------------------------------
    // Demo 9: Observer pattern
    // -----------------------------------------------------------------------
    private static void demoObserver() {
        printSection("9) Observer pattern -- Stock notifies PriceLogger and PriceAlert");

        Stock apple = new Stock("AAPL", 150.0);
        apple.subscribe(new PriceLogger());
        apple.subscribe(new PriceAlert(180.0));

        apple.setPrice(165.0);      // logged, no alert (below threshold)
        apple.setPrice(190.0);      // logged AND alerted (above threshold)
    }

    // -----------------------------------------------------------------------
    // Demo 10: Strategy pattern
    // -----------------------------------------------------------------------
    private static void demoStrategy() {
        printSection("10) Strategy pattern -- swappable DiscountStrategy, including a lambda strategy");

        ShoppingCart cart = new ShoppingCart(new PercentageDiscount(10));
        System.out.println("  10% off $200  = " + cart.checkout(200.0));
        assert cart.checkout(200.0) == 180.0;

        cart.setDiscountStrategy(new FlatDiscount(15));
        System.out.println("  $15 off $200  = " + cart.checkout(200.0));
        assert cart.checkout(200.0) == 185.0;

        cart.setDiscountStrategy(new NoDiscount());
        System.out.println("  No discount   = " + cart.checkout(200.0));
        assert cart.checkout(200.0) == 200.0;

        // A functional interface -- a lambda can stand in for an entire concrete strategy class:
        cart.setDiscountStrategy(price -> price * 0.5);
        System.out.println("  Lambda 50% off = " + cart.checkout(200.0));
        assert cart.checkout(200.0) == 100.0;
    }

    public static void main(String[] args) {
        demoSrp();
        demoOcp();
        demoLsp();
        demoIsp();
        demoDip();
        demoSingleton();
        demoFactory();
        demoBuilder();
        demoObserver();
        demoStrategy();

        System.out.println();
        System.out.println("All SOLID Principles / Design Patterns demos completed.");
    }
}
