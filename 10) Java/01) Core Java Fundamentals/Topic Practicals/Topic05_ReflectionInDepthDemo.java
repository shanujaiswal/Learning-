/*
 * Topic05_ReflectionInDepthDemo.java
 *
 * NOTE ON FILENAME: the public class is named "Topic05_ReflectionInDepthDemo" (Java
 * identifiers can't start with a digit) while the FILE keeps the "05_..." numeric prefix
 * used throughout this repo for ordering.
 *
 * Compile: javac 05_ReflectionInDepthDemo.java
 * Run:     java Topic05_ReflectionInDepthDemo
 *
 * Demonstrates:
 *   1. Dynamic proxies via java.lang.reflect.Proxy -- a simple logging InvocationHandler
 *   2. A closer look at Method/Field/Constructor reflection, including setAccessible on a
 *      private field and a private method
 *   3. Modifier decoding (Modifier.isPublic/isStatic/... and Modifier.toString)
 *   4. A simple reflective-call-vs-direct-call performance comparison
 *
 * Covers Theory chapter:
 *   01) Core Java Fundamentals/Theory/12 Reflection In Depth.md
 */

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;

public class Topic05_ReflectionInDepthDemo {

    public static void main(String[] args) throws Exception {
        demoDynamicProxy();
        demoMethodFieldConstructorIntrospection();
        demoSetAccessibleOnPrivateMembers();
        demoReflectionVsDirectCallPerformance();
        System.out.println("\nAll reflection-in-depth demos completed.");
    }

    // -------------------------------------------------------------------
    // 1) Dynamic proxies -- java.lang.reflect.Proxy
    // -------------------------------------------------------------------
    interface Greeter {
        String greet(String name);
    }

    private static void demoDynamicProxy() {
        printSection("1) Dynamic Proxy via java.lang.reflect.Proxy");

        // The "real" implementation the proxy will eventually delegate to.
        Greeter real = name -> "Hello, " + name + "!";

        // The handler intercepts EVERY method call made on the proxy instance.
        // NOTE: `proxy` here is the proxy instance itself -- calling a method on `proxy`
        // from inside invoke() would recurse infinitely; always delegate to `real` instead.
        InvocationHandler loggingHandler = (proxy, method, methodArgs) -> {
            System.out.println("  [proxy] before calling: " + method.getName()
                    + Arrays_toString(methodArgs));
            Object result = method.invoke(real, methodArgs);
            System.out.println("  [proxy] after calling:  " + method.getName() + " -> " + result);
            return result;
        };

        Greeter proxied = (Greeter) Proxy.newProxyInstance(
                Greeter.class.getClassLoader(),
                new Class<?>[]{Greeter.class},
                loggingHandler);

        System.out.println("Calling proxied.greet(\"World\"):");
        String result = proxied.greet("World");
        System.out.println("Final result received by caller: " + result);

        System.out.println("\nDynamic proxies can only implement INTERFACES, never proxy a concrete class --");
        System.out.println("this is exactly the mechanism getAnnotation() (File 03) uses to hand back a");
        System.out.println("working annotation instance with no hand-written implementation class anywhere.");
    }

    private static String Arrays_toString(Object[] args) {
        if (args == null) return "()";
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(args[i]);
        }
        return sb.append(")").toString();
    }

    // -------------------------------------------------------------------
    // 2) A closer look at Method/Field/Constructor
    // -------------------------------------------------------------------
    static class Account {
        private String owner;
        private double balance;

        Account() {
            this("unnamed", 0.0);
        }

        Account(String owner, double balance) {
            this.owner = owner;
            this.balance = balance;
        }

        private double computeInterest(double rate) {
            return balance * rate;
        }

        public String publicDescribe(String prefix, int precision) throws IllegalStateException {
            return prefix + owner;
        }
    }

    private static void demoMethodFieldConstructorIntrospection() throws Exception {
        printSection("2) Method / Field / Constructor -- A Closer Look");

        Method describe = Account.class.getMethod("publicDescribe", String.class, int.class);
        System.out.println("Method: " + describe);
        System.out.println("  getReturnType()            = " + describe.getReturnType().getSimpleName());
        System.out.println("  getParameterTypes()        = " + java.util.Arrays.toString(describe.getParameterTypes()));
        System.out.println("  getExceptionTypes()        = " + java.util.Arrays.toString(describe.getExceptionTypes()));
        System.out.println("  isVarArgs()                = " + describe.isVarArgs());
        System.out.println("  isSynthetic()               = " + describe.isSynthetic());
        System.out.println("  Modifier.toString(mods)     = " + Modifier.toString(describe.getModifiers()));
        System.out.println("  Modifier.isPublic(mods)     = " + Modifier.isPublic(describe.getModifiers()));

        Field balanceField = Account.class.getDeclaredField("balance");
        System.out.println("\nField: " + balanceField);
        System.out.println("  getType()                   = " + balanceField.getType().getSimpleName());
        System.out.println("  Modifier.toString(mods)     = " + Modifier.toString(balanceField.getModifiers()));
        System.out.println("  Modifier.isPrivate(mods)    = " + Modifier.isPrivate(balanceField.getModifiers()));

        Constructor<Account> ctor = Account.class.getDeclaredConstructor(String.class, double.class);
        System.out.println("\nConstructor: " + ctor);
        System.out.println("  getParameterCount()          = " + ctor.getParameterCount());
    }

    // -------------------------------------------------------------------
    // 3) setAccessible on a private field and a private method
    // -------------------------------------------------------------------
    private static void demoSetAccessibleOnPrivateMembers() throws Exception {
        printSection("3) setAccessible on Private Field and Private Method");

        Account acct = new Account("Vanisha", 1000.0);

        // Reflectively read AND mutate a private field. setAccessible(true) here is a
        // deliberate, commented encapsulation bypass -- purely for this demo's illustration.
        Field balanceField = Account.class.getDeclaredField("balance");
        balanceField.setAccessible(true);
        System.out.println("Before: balance = " + balanceField.get(acct));
        balanceField.set(acct, 2500.0);
        System.out.println("After:  balance = " + balanceField.get(acct));

        // Reflectively invoke a private method.
        Method computeInterest = Account.class.getDeclaredMethod("computeInterest", double.class);
        computeInterest.setAccessible(true);
        Object interest = computeInterest.invoke(acct, 0.05);
        System.out.println("computeInterest(0.05) via reflection = " + interest);

        System.out.println("\nsetAccessible(true) works here because Account is in our own unnamed module /");
        System.out.println("classpath. In a modularized (JPMS) app, the SAME call against another named");
        System.out.println("module would throw InaccessibleObjectException unless that module `opens` the");
        System.out.println("package, or the JVM is launched with --add-opens.");
    }

    // -------------------------------------------------------------------
    // 4) Reflective call vs direct call -- a simple performance comparison
    // -------------------------------------------------------------------
    static class MathOps {
        int square(int x) {
            return x * x;
        }
    }

    private static void demoReflectionVsDirectCallPerformance() throws Exception {
        printSection("4) Reflective Call vs Direct Call -- Timing Comparison");

        MathOps ops = new MathOps();
        Method squareMethod = MathOps.class.getDeclaredMethod("square", int.class);
        squareMethod.setAccessible(true); // cached ONCE, outside the timed loop -- the correct practice

        final int iterations = 2_000_000;

        // Warm up the JIT for both paths so the comparison isn't dominated by interpreter cost.
        long warmupSum = 0;
        for (int i = 0; i < 50_000; i++) {
            warmupSum += ops.square(i);
            warmupSum += (Integer) squareMethod.invoke(ops, i);
        }

        long directStart = System.nanoTime();
        long directSum = 0;
        for (int i = 0; i < iterations; i++) {
            directSum += ops.square(i);
        }
        long directElapsedNanos = System.nanoTime() - directStart;

        long reflectiveStart = System.nanoTime();
        long reflectiveSum = 0;
        for (int i = 0; i < iterations; i++) {
            reflectiveSum += (Integer) squareMethod.invoke(ops, i);
        }
        long reflectiveElapsedNanos = System.nanoTime() - reflectiveStart;

        System.out.println("(warmup checksum, ignore: " + warmupSum + ")");
        System.out.println("Iterations           = " + iterations);
        System.out.println("Direct call total     = " + (directElapsedNanos / 1_000_000.0) + " ms  (sum=" + directSum + ")");
        System.out.println("Reflective call total = " + (reflectiveElapsedNanos / 1_000_000.0) + " ms  (sum=" + reflectiveSum + ")");
        if (directElapsedNanos > 0) {
            System.out.printf("Reflective call was roughly %.1fx slower than direct in this run.%n",
                    reflectiveElapsedNanos / (double) directElapsedNanos);
        }

        System.out.println("\nAs Theory File 05 notes: the lookup (getDeclaredMethod) is a one-off cost we");
        System.out.println("already paid OUTSIDE this loop; the remaining gap comes from per-call access");
        System.out.println("checks, argument boxing/unboxing into Object[], and lost JIT inlining. Absolute");
        System.out.println("numbers vary run to run and JVM to JVM -- the RELATIVE gap is the point, and it");
        System.out.println("narrows substantially once the loop is warmed up, which is why reflection at");
        System.out.println("one-time startup/wiring time is essentially never a real bottleneck.");
    }

    // -------------------------------------------------------------------
    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }
}
