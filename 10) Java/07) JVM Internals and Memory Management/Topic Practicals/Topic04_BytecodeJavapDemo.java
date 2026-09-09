/*
 * Topic04_BytecodeJavapDemo.java
 *
 * NOTE ON FILENAME: the public class is named "Topic04_BytecodeJavapDemo" (Java
 * identifiers can't start with a digit) while the FILE keeps the "04_..." numeric
 * prefix used throughout this repo for ordering.
 *
 * Compile: javac 04_BytecodeJavapDemo.java
 * Run:     java Topic04_BytecodeJavapDemo
 *
 * Disassemble (the actual point of this file):
 *          javap -c Topic04_BytecodeJavapDemo
 *          javap -c -p Topic04_BytecodeJavapDemo      (include private methods too)
 *          javap -v Topic04_BytecodeJavapDemo          (verbose: + constant pool, stack map)
 *
 * This class deliberately includes several different method "shapes" so that
 * `javap -c` output is worth comparing against the reasoned-through expectations in the
 * comment blocks below each method -- run javap yourself and compare; exact instruction
 * offsets can vary slightly by javac version, but the INSTRUCTION SEQUENCE and overall
 * shape described here is stable and reflects true `javac`/JVM semantics.
 *
 * Covers Theory chapter:
 *   07) JVM Internals and Memory Management/Theory/13 Bytecode Basics and javap.md
 */

import java.util.ArrayList;
import java.util.List;

public class Topic04_BytecodeJavapDemo {

    public static void main(String[] args) {
        Topic04_BytecodeJavapDemo demo = new Topic04_BytecodeJavapDemo();

        System.out.println("add(3, 4)              = " + demo.add(3, 4));
        System.out.println("isPositive(5)           = " + demo.isPositive(5));
        System.out.println("staticGreeting()        = " + staticGreeting());
        System.out.println("sumBoxed([1,2,3])       = " + demo.sumBoxed(List.of(1, 2, 3)));
        System.out.println("concatenate(\"a\",\"b\")  = " + demo.concatenate("a", "b"));

        System.out.println("\nNow run:  javap -c Topic04_BytecodeJavapDemo");
        System.out.println("and compare the real output against the reasoned-through comment blocks");
        System.out.println("above each method below -- this is the point of this file, not the console");
        System.out.println("output you're reading right now.");
    }

    // =====================================================================
    // Method 1: instance method, plain int arithmetic -- the exact worked
    // example from Theory File 04.
    //
    // Source:
    //     public int add(int a, int b) {
    //         return a + b;
    //     }
    //
    // Expected `javap -c` output (reasoned through per Theory File 04's worked example;
    // instance method, so slot 0 = 'this', slot 1 = 'a', slot 2 = 'b'):
    //
    //   public int add(int, int);
    //     Code:
    //        0: iload_1        // push local slot 1 (a) -- slot 0 is 'this' for instance methods
    //        1: iload_2        // push local slot 2 (b)              stack: [a, b]
    //        2: iadd           // pop two ints, push their sum       stack: [a+b]
    //        3: ireturn        // pop int, return it
    //
    // The 'i' prefix (iload/iadd/ireturn) means "operates on int" -- the JVM instruction
    // set is typed per-instruction (iadd/ladd/fadd/dadd for int/long/float/double, etc.).
    // =====================================================================
    public int add(int a, int b) {
        return a + b;
    }

    // =====================================================================
    // Method 2: instance method with a conditional branch -- introduces
    // if_icmpXX-family instructions backing an `if`/boolean expression.
    //
    // Source:
    //     public boolean isPositive(int n) {
    //         return n > 0;
    //     }
    //
    // Expected `javap -c` output (reasoned through):
    //
    //   public boolean isPositive(int);
    //     Code:
    //        0: iload_1        // push local slot 1 (n)
    //        1: ifle     8     // if n <= 0 (i.e. NOT > 0), jump to offset 8
    //        4: iconst_1       // n > 0 was true: push int constant 1 (boolean true)
    //        5: goto      9    // skip over the "false" branch below
    //        8: iconst_0       // n > 0 was false: push int constant 0 (boolean false)
    //        9: ireturn        // return whatever's on top of the stack
    //
    // Booleans have NO dedicated JVM type -- they're represented as int 0/1 on the operand
    // stack and in local slots; iconst_0/iconst_1 plus ireturn is how `boolean`-returning
    // methods actually compile. `ifle` ("if less-or-equal to zero") is one of several
    // single-operand comparison-against-zero branches; two-operand comparisons between two
    // local values instead use the if_icmpXX family (if_icmpgt, if_icmpge, if_icmplt, ...).
    // =====================================================================
    public boolean isPositive(int n) {
        return n > 0;
    }

    // =====================================================================
    // Method 3: a STATIC method -- no implicit 'this', so local slot 0 is
    // whatever the first real parameter is (or unused, here there are none),
    // and invocation elsewhere uses invokestatic rather than invokevirtual.
    //
    // Source:
    //     public static String staticGreeting() {
    //         return "hello from JVM tools";
    //     }
    //
    // Expected `javap -c` output (reasoned through):
    //
    //   public static java.lang.String staticGreeting();
    //     Code:
    //        0: ldc           #N       // push a String constant from the constant pool
    //                                  // (the literal is NOT embedded inline -- only a
    //                                  // constant-pool INDEX is, per Theory File 04's
    //                                  // "Constant Pool" section)
    //        2: areturn                // return a reference ('a' prefix = address/reference)
    //
    // Note there is no 'aload_0' here at all -- a static method has no 'this', so slot 0
    // is free for the first real local variable/parameter (there are none in this method).
    // Any call SITE that invokes staticGreeting() elsewhere in this class compiles to
    // `invokestatic`, resolved at compile time -- unlike invokevirtual, this can never be
    // overridden, which is exactly why static calls are trivially easy inlining targets
    // for the JIT (Theory File 05).
    // =====================================================================
    public static String staticGreeting() {
        return "hello from JVM tools";
    }

    // =====================================================================
    // Method 4: autoboxing cost made visible -- the exact example from
    // Theory File 04's "A More Revealing Example" section.
    //
    // Source:
    //     public int sumBoxed(List<Integer> nums) {
    //         int total = 0;
    //         for (Integer n : nums) {
    //             total += n;
    //         }
    //         return total;
    //     }
    //
    // What `javap -c` reveals that the SOURCE does not show at all:
    //   - the enhanced-for loop desugars into explicit Iterator machinery:
    //       invokeinterface  java/util/List.iterator()
    //       ... loop head ...
    //       invokeinterface  java/util/Iterator.hasNext()
    //       ifeq  <exit>
    //       invokeinterface  java/util/Iterator.next()
    //       checkcast        java/lang/Integer      // generic-erasure check
    //   - `total += n` on a boxed Integer silently unboxes it before adding:
    //       invokevirtual    java/lang/Integer.intValue()
    //       iadd
    //   - the loop's back edge is a `goto` back to the hasNext() check.
    //
    // None of invokeinterface(hasNext/next), checkcast, or Integer.intValue() appear
    // anywhere in the SOURCE -- they're entirely compiler-inserted. This is precisely why
    // `List<Integer>` summation is measurably slower than an int[]-based loop despite
    // "looking" equivalent in source: every element pays an interface-dispatch iterator
    // call PLUS an unboxing invokevirtual call that a primitive int[] loop never needs.
    // Run `javap -c -p` on this class and find this method's Code block to see the real,
    // JDK-version-specific instruction sequence and offsets.
    // =====================================================================
    public int sumBoxed(List<Integer> nums) {
        int total = 0;
        for (Integer n : nums) {
            total += n;
        }
        return total;
    }

    // =====================================================================
    // Method 5: string concatenation with '+' -- on modern javac (9+), this
    // desugars to an invokedynamic call site (StringConcatFactory), NOT a
    // visible StringBuilder chain the way older javac versions used to emit.
    //
    // Source:
    //     public String concatenate(String a, String b) {
    //         return a + "-" + b;
    //     }
    //
    // Expected shape on a modern JDK's `javap -c` output (reasoned through; exact
    // constant-pool index and bootstrap-method details vary by JDK version -- this is
    // where running `javap -v` yourself to see the real BootstrapMethods table is worth
    // doing rather than guessing further):
    //
    //   public java.lang.String concatenate(java.lang.String, java.lang.String);
    //     Code:
    //        0: aload_1
    //        1: aload_2
    //        2: invokedynamic #N, 0   // InvokeDynamic #0:makeConcatWithConstants:(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
    //        7: areturn
    //
    // On JDK 8 and earlier, the same source instead compiled to an explicit, visible
    // `new StringBuilder()` + chained `.append(...)` + `.toString()` sequence -- several
    // more bytecode instructions than the single invokedynamic call site modern javac
    // emits. Either way, per Theory File 04: this is exactly the kind of "same source,
    // different real cost across versions" fact that's invisible without reading bytecode.
    // =====================================================================
    public String concatenate(String a, String b) {
        return a + "-" + b;
    }

    // =====================================================================
    // Method 6: a private method, included specifically to demonstrate that
    // `javap` (with no flags) shows PUBLIC members only -- run `javap` with
    // no flags on this class and this method will be ABSENT; add `-p` and it
    // appears. Also demonstrates invokespecial: any call to a private method
    // is resolved non-virtually at compile time (can't be overridden), which
    // per Theory File 04/05 makes it an easy inlining target for the JIT --
    // the same reasoning that applies to constructors and super calls.
    // =====================================================================
    private int square(int x) {
        return x * x;
    }

    /*
     * Method 7 (not written out as a runnable method here -- pure comment, since it
     * needs no source counterpart): a NEW object allocation shows up in bytecode as
     * TWO separate steps, unlike almost every other language's single "construct"
     * concept:
     *
     *     new Example        // 'new' allocates raw, UNINITIALIZED memory for the object
     *                        // and pushes a reference to it -- the constructor has NOT
     *                        // run yet at this point
     *     dup                // duplicate that reference (one copy is consumed by
     *                        // invokespecial below, one copy remains as the result)
     *     invokespecial Example.<init>()V   // runs the actual constructor body --
     *                                        // constructors are ALWAYS invokespecial,
     *                                        // never invokevirtual, since they can't be
     *                                        // polymorphically overridden
     *
     * This two-step "new, then invokespecial <init>" pattern is why bytecode-level tools
     * (ASM, Javassist, ByteBuddy -- mentioned in Theory File 04's gotchas) that generate
     * object-construction bytecode must emit both instructions in the right order, and
     * why a `new` instruction alone (without a matching invokespecial before the object
     * is used) leaves a genuinely unusable, half-constructed reference.
     */
}
