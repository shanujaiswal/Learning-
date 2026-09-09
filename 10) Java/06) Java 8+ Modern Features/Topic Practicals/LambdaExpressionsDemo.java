/*
 * LambdaExpressionsDemo.java
 *
 * Demonstrates:
 *     1. Lambda syntax variations (full form, inferred types, single expression, block body)
 *     2. Custom @FunctionalInterface definitions
 *     3. Effectively final variable capture rules
 *     4. 'this' binding inside a lambda vs an anonymous class
 *     5. Built-in java.util.function interfaces: Function, BiFunction, Predicate,
 *        Consumer, Supplier, UnaryOperator, BinaryOperator
 *     6. Primitive specializations (IntPredicate, IntUnaryOperator) avoiding autoboxing
 *     7. Composition: andThen, compose, and, or, negate
 *
 * Covers Theory chapter:
 *     06) Java 8+ Modern Features/Theory/01 Lambda Expressions and Functional Interfaces.md
 *
 * Compile & run:
 *     javac 01_lambda_expressions_functional_interfaces.java
 *     java LambdaExpressionsDemo
 */

import java.util.function.*;
import java.util.concurrent.atomic.AtomicInteger;

public class LambdaExpressionsDemo {

    // -----------------------------------------------------------------
    // Custom functional interface
    // -----------------------------------------------------------------
    @FunctionalInterface
    interface Greeter {
        String greet(String name);

        default Greeter andThenExclaim() {
            return name -> greet(name) + "!";
        }
    }

    // Instance field used to demonstrate 'this' capture inside a lambda
    private int instanceCount = 0;

    private Runnable makeIncrementer() {
        // 'this' inside the lambda refers to the enclosing LambdaExpressionsDemo instance
        return () -> {
            this.instanceCount++;
            System.out.println("   instanceCount is now " + this.instanceCount
                    + " (via lambda, this.getClass()=" + this.getClass().getSimpleName() + ")");
        };
    }

    private Runnable makeIncrementerAnonymous() {
        // Anonymous class: 'this' refers to the anonymous class instance itself,
        // so the enclosing instance's field must be reached via LambdaExpressionsDemo.this
        return new Runnable() {
            @Override
            public void run() {
                LambdaExpressionsDemo.this.instanceCount++;
                System.out.println("   instanceCount is now " + LambdaExpressionsDemo.this.instanceCount
                        + " (via anonymous class, this.getClass()=" + this.getClass().getSimpleName() + ")");
            }
        };
    }

    public static void main(String[] args) {
        printSection("1) Lambda syntax variations");
        syntaxVariations();

        printSection("2) Custom functional interface + default method composition");
        customFunctionalInterface();

        printSection("3) Effectively final capture rules");
        effectivelyFinalCapture();

        printSection("4) 'this' inside lambda vs anonymous class");
        new LambdaExpressionsDemo().thisBindingDemo();

        printSection("5) Built-in java.util.function interfaces");
        builtInFunctionalInterfaces();

        printSection("6) Primitive specializations -- avoiding autoboxing");
        primitiveSpecializations();

        printSection("7) Composing functions and predicates");
        composition();

        System.out.println("\nAll lambda expression demos completed.");
    }

    // -----------------------------------------------------------------
    // 1) Syntax variations
    // -----------------------------------------------------------------
    private static void syntaxVariations() {
        // Full form: explicit parameter types, braces, explicit return
        Comparator2 byLengthFull = (String a, String b) -> {
            return Integer.compare(a.length(), b.length());
        };

        // Inferred parameter types
        Comparator2 byLengthInferred = (a, b) -> Integer.compare(a.length(), b.length());

        // Zero-argument lambda -- parentheses required
        Runnable noArgs = () -> System.out.println("   Runnable with no args executed");

        // Single argument -- parens optional
        Consumer<String> singleArgNoParens = s -> System.out.println("   received: " + s);

        // Multi-statement body -- braces + explicit return required
        Function<Integer, Integer> squarePlusOne = x -> {
            int squared = x * x;
            return squared + 1;
        };

        System.out.println("byLengthFull.compare(\"hi\",\"hey\") = " + byLengthFull.compare("hi", "hey"));
        System.out.println("byLengthInferred.compare(\"hi\",\"hey\") = " + byLengthInferred.compare("hi", "hey"));
        noArgs.run();
        singleArgNoParens.accept("payload");
        System.out.println("squarePlusOne.apply(4) = " + squarePlusOne.apply(4));
    }

    @FunctionalInterface
    interface Comparator2 {
        int compare(String a, String b);
    }

    // -----------------------------------------------------------------
    // 2) Custom functional interface with default method
    // -----------------------------------------------------------------
    private static void customFunctionalInterface() {
        Greeter plainGreeter = name -> "Hello, " + name;
        Greeter excitedGreeter = plainGreeter.andThenExclaim();

        System.out.println(plainGreeter.greet("Vanisha"));
        System.out.println(excitedGreeter.greet("Vanisha"));
    }

    // -----------------------------------------------------------------
    // 3) Effectively final capture rules
    // -----------------------------------------------------------------
    private static void effectivelyFinalCapture() {
        int counter = 0;                       // never reassigned -> effectively final
        Runnable printCounter = () -> System.out.println("   captured counter = " + counter);
        printCounter.run();

        // A plain local int cannot be mutated after being captured by a lambda; the
        // standard workaround is a mutable holder such as AtomicInteger, whose
        // REFERENCE never changes even though its internal state does.
        AtomicInteger mutableCounter = new AtomicInteger(0);
        Runnable incrementer = mutableCounter::incrementAndGet;
        incrementer.run();
        incrementer.run();
        incrementer.run();
        System.out.println("   AtomicInteger workaround, final value = " + mutableCounter.get());
    }

    // -----------------------------------------------------------------
    // 4) 'this' binding
    // -----------------------------------------------------------------
    private void thisBindingDemo() {
        Runnable lambdaIncrementer = makeIncrementer();
        Runnable anonIncrementer = makeIncrementerAnonymous();
        lambdaIncrementer.run();
        anonIncrementer.run();
    }

    // -----------------------------------------------------------------
    // 5) Built-in functional interfaces
    // -----------------------------------------------------------------
    private static void builtInFunctionalInterfaces() {
        Function<String, Integer> length = String::length;
        System.out.println("Function<String,Integer> length.apply(\"hello\") = " + length.apply("hello"));

        BiFunction<Integer, Integer, Integer> add = (a, b) -> a + b;
        System.out.println("BiFunction add.apply(3,4) = " + add.apply(3, 4));

        Predicate<Integer> isEven = n -> n % 2 == 0;
        System.out.println("Predicate isEven.test(10) = " + isEven.test(10));

        Consumer<String> print = System.out::println;
        System.out.print("Consumer print.accept -> ");
        print.accept("consumed value");

        Supplier<String> fixedValue = () -> "supplied-value";
        System.out.println("Supplier fixedValue.get() = " + fixedValue.get());

        UnaryOperator<Integer> doubleIt = n -> n * 2;
        System.out.println("UnaryOperator doubleIt.apply(21) = " + doubleIt.apply(21));

        BinaryOperator<Integer> max = Integer::max;
        System.out.println("BinaryOperator max.apply(3,9) = " + max.apply(3, 9));
    }

    // -----------------------------------------------------------------
    // 6) Primitive specializations
    // -----------------------------------------------------------------
    private static void primitiveSpecializations() {
        IntPredicate isPositive = n -> n > 0;               // no boxing to Integer
        System.out.println("IntPredicate isPositive.test(-5) = " + isPositive.test(-5));
        System.out.println("IntPredicate isPositive.test(5)  = " + isPositive.test(5));

        IntUnaryOperator square = n -> n * n;
        System.out.println("IntUnaryOperator square.applyAsInt(6) = " + square.applyAsInt(6));

        ToIntFunction<String> stringLength = String::length;
        System.out.println("ToIntFunction stringLength.applyAsInt(\"abcdef\") = "
                + stringLength.applyAsInt("abcdef"));
    }

    // -----------------------------------------------------------------
    // 7) Composition
    // -----------------------------------------------------------------
    private static void composition() {
        Function<Integer, Integer> times2 = x -> x * 2;
        Function<Integer, Integer> plus3 = x -> x + 3;

        Function<Integer, Integer> andThenResult = times2.andThen(plus3);   // (5*2)+3
        Function<Integer, Integer> composeResult = times2.compose(plus3);   // (5+3)*2
        System.out.println("times2.andThen(plus3).apply(5) = " + andThenResult.apply(5) + "  (expected 13)");
        System.out.println("times2.compose(plus3).apply(5) = " + composeResult.apply(5) + "  (expected 16)");

        Predicate<Integer> isPositive = n -> n > 0;
        Predicate<Integer> isEven = n -> n % 2 == 0;
        Predicate<Integer> positiveAndEven = isPositive.and(isEven);
        Predicate<Integer> positiveOrEven = isPositive.or(isEven);
        Predicate<Integer> notPositive = isPositive.negate();

        System.out.println("positiveAndEven.test(4)  = " + positiveAndEven.test(4));
        System.out.println("positiveAndEven.test(-4) = " + positiveAndEven.test(-4));
        System.out.println("positiveOrEven.test(-4)  = " + positiveOrEven.test(-4));
        System.out.println("notPositive.test(-4)     = " + notPositive.test(-4));

        Consumer<String> logUpper = s -> System.out.println("   UPPER: " + s.toUpperCase());
        Consumer<String> logLower = s -> System.out.println("   lower: " + s.toLowerCase());
        Consumer<String> both = logUpper.andThen(logLower);
        both.accept("Hello Composition");
    }

    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }
}
