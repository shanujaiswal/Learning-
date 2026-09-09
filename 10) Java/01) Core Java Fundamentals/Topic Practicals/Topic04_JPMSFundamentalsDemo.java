/*
 * Topic04_JPMSFundamentalsDemo.java
 *
 * NOTE ON FILENAME: the public class is named "Topic04_JPMSFundamentalsDemo" (Java identifiers
 * can't start with a digit) while the FILE keeps the "04_..." numeric prefix used throughout
 * this repo for ordering.
 *
 * Compile: javac 04_JPMSFundamentalsDemo.java
 * Run:     java Topic04_JPMSFundamentalsDemo
 *
 * IMPORTANT CAVEAT: a real module-info.java demo needs a proper multi-module source layout
 * (one module directory per module, each with its own module-info.java) and a multi-module
 * compilation/launch (--module-source-path, --module-path, --module), which cannot be expressed
 * inside a single .java file compiled with plain `javac SomeFile.java`. This file therefore:
 *
 *   1. Shows illustrative/commented module-info.java syntax (requires/exports/opens/uses/provides)
 *      as it would look in a real multi-module project -- NOT compiled, just documentation-in-code.
 *   2. Provides a genuinely RUNNABLE demonstration of reflection-based access-restriction
 *      concepts that directly motivate JPMS's `opens` directive: setAccessible(true) behavior
 *      on private members, contrasted with what changes once a package is NOT opened to a
 *      caller module (explained in comments, since this file itself runs entirely in the
 *      UNNAMED module on the classpath, where JPMS's boundary checks do not engage -- see
 *      Theory file 05 for the unnamed module's "reads everything, exports everything" behavior).
 *
 * Covers Theory chapter:
 *   01) Core Java Fundamentals/Theory/14 The Java Platform Module System JPMS Fundamentals.md
 */

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class Topic04_JPMSFundamentalsDemo {

    public static void main(String[] args) throws Exception {
        showIllustrativeModuleInfoSyntax();
        demoSetAccessibleOnPrivateField();
        demoSetAccessibleOnPrivateMethod();
        explainHowThisWouldDifferUnderJPMS();
        System.out.println("\nAll JPMS-fundamentals demos completed.");
    }

    // -------------------------------------------------------------------
    // 1) Illustrative module-info.java syntax -- NOT compiled as part of this file.
    //    (module-info.java must live at the ROOT of a module's own source tree, one file per
    //    module; embedding a real one here is not possible in a single-file demo.)
    // -------------------------------------------------------------------
    private static void showIllustrativeModuleInfoSyntax() {
        printSection("1) Illustrative module-info.java syntax (documentation only, not compiled)");

        String example =
                "// src/com.example.orders/module-info.java\n" +
                "module com.example.orders {\n" +
                "    requires java.sql;                          // ordinary dependency\n" +
                "    requires transitive java.logging;             // propagates to consumers of this module\n" +
                "    requires static com.example.testkit;          // compile-time only, optional at runtime\n" +
                "    requires com.example.customers;               // another of our own modules\n" +
                "\n" +
                "    exports com.example.orders.api;                              // public API surface\n" +
                "    exports com.example.orders.dto to com.example.reporting;      // qualified export\n" +
                "\n" +
                "    opens com.example.orders.entity;               // deep reflection allowed (e.g. Hibernate)\n" +
                "    opens com.example.orders.dto to com.fasterxml.jackson.databind;  // qualified opens\n" +
                "\n" +
                "    uses com.example.spi.PaymentProcessor;          // consumes a service, doesn't care which impl\n" +
                "    provides com.example.spi.PaymentProcessor\n" +
                "            with com.example.orders.internal.DefaultPaymentProcessor;\n" +
                "}\n" +
                "\n" +
                "// Alternative: blanket-open every package in the module (common for application\n" +
                "// modules leaning heavily on reflection-based frameworks):\n" +
                "// open module com.example.orders {\n" +
                "//     requires java.sql;\n" +
                "//     exports com.example.orders.api;\n" +
                "// }";

        System.out.println(example);
        System.out.println();
        System.out.println("Key rules illustrated above (Theory file 04):");
        System.out.println("  - module-info.java sits at the ROOT of the module's source tree, not inside");
        System.out.println("    any package directory.");
        System.out.println("  - A package NOT listed in an 'exports' is completely inaccessible from outside");
        System.out.println("    the module at COMPILE TIME, even if every class in it is public.");
        System.out.println("  - 'opens' grants deep REFLECTIVE access (setAccessible(true) on private members)");
        System.out.println("    that 'exports' alone does NOT grant -- this is exactly what demos 2 and 3");
        System.out.println("    below explore in a runnable form.");
    }

    // -------------------------------------------------------------------
    // 2) Reflection into a PRIVATE field -- runnable demonstration of the mechanism JPMS's
    //    `opens` directive controls. In a real modular application, this exact call would throw
    //    InaccessibleObjectException unless the target's package were `opens`ed to the caller's
    //    module (or the whole module were `open`). Here, both classes live together in the
    //    UNNAMED module (this file is compiled/run on the plain classpath), so the unnamed
    //    module's "anything goes" behavior lets it succeed -- see explainHowThisWouldDifferUnderJPMS().
    // -------------------------------------------------------------------
    static class SecretHolder {
        private String secretValue = "classified-42";
    }

    private static void demoSetAccessibleOnPrivateField() throws Exception {
        printSection("2) setAccessible(true) on a private field (runnable)");

        SecretHolder holder = new SecretHolder();
        Class<?> clazz = SecretHolder.class;

        Field field = clazz.getDeclaredField("secretValue");
        System.out.println("Field is accessible before setAccessible? " + field.isAccessible());

        field.setAccessible(true); // in a modular app: needs the declaring package `opens`ed to caller
        System.out.println("Field is accessible after setAccessible(true)? " + field.isAccessible());

        String value = (String) field.get(holder);
        System.out.println("Reflectively read private field value: " + value);

        field.set(holder, "changed-via-reflection");
        System.out.println("Reflectively OVERWROTE private field: " + holder.secretValue);
    }

    // -------------------------------------------------------------------
    // 3) Reflection into a PRIVATE method -- same underlying mechanism, method form.
    // -------------------------------------------------------------------
    static class Calculator {
        private int addSecretly(int a, int b) {
            return a + b;
        }
    }

    private static void demoSetAccessibleOnPrivateMethod() throws Exception {
        printSection("3) setAccessible(true) on a private method (runnable)");

        Calculator calc = new Calculator();
        Method method = Calculator.class.getDeclaredMethod("addSecretly", int.class, int.class);

        System.out.println("Method modifiers: " + Modifier.toString(method.getModifiers()));
        method.setAccessible(true);

        Object result = method.invoke(calc, 3, 4);
        System.out.println("Invoked private method reflectively: addSecretly(3, 4) = " + result);
    }

    // -------------------------------------------------------------------
    // 4) Explain how this exact code would behave differently under real JPMS module boundaries.
    // -------------------------------------------------------------------
    private static void explainHowThisWouldDifferUnderJPMS() {
        printSection("4) How this would differ under real JPMS module boundaries");

        System.out.println("This class is loaded from the CLASSPATH (plain `javac`/`java`, no module-info.java");
        System.out.println("anywhere on the module path), so at runtime it lives in the UNNAMED module.");
        System.out.println("Per Theory file 05: 'the unnamed module reads every other module, and exports");
        System.out.println("everything it has to every other module' -- so setAccessible(true) above succeeds");
        System.out.println("unconditionally, exactly like pre-JPMS reflection always worked.");
        System.out.println();
        System.out.println("Contrast -- if SecretHolder instead lived in package com.example.internal inside a");
        System.out.println("REAL named module 'com.example.orders' declared like this:");
        System.out.println();
        System.out.println("    module com.example.orders {");
        System.out.println("        exports com.example.orders.api;   // internal package NOT exported/opened");
        System.out.println("    }");
        System.out.println();
        System.out.println("...then code in a DIFFERENT named module calling:");
        System.out.println("    Field field = SecretHolder.class.getDeclaredField(\"secretValue\");");
        System.out.println("    field.setAccessible(true);");
        System.out.println("would throw:");
        System.out.println("    java.lang.reflect.InaccessibleObjectException: Unable to make field private");
        System.out.println("    java.lang.String ...SecretHolder.secretValue accessible: module com.example.orders");
        System.out.println("    does not \"opens com.example.orders.internal\" to module <caller>");
        System.out.println();
        System.out.println("The FIX would be adding either:");
        System.out.println("    opens com.example.orders.internal;                       // open to everyone");
        System.out.println("or  opens com.example.orders.internal to <caller-module-name>; // qualified opens");
        System.out.println("to that module's module-info.java -- 'exports' alone would NOT be sufficient,");
        System.out.println("since exports only governs compile-time visibility/normal method calls, not deep");
        System.out.println("reflection into private members (see the exports-vs-opens table in Theory file 04).");
    }

    // -------------------------------------------------------------------
    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }
}
