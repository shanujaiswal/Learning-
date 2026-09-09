# Why Bytecode Matters for Performance Work

--> Java source compiles to **bytecode**, not machine code -- the `javac` compiler's output is a `.class` file full of bytecode instructions for the JVM's own stack-based virtual machine, and it's the JIT compiler (File 05) that later turns hot bytecode into real machine code at runtime. Every performance discussion about inlining, escape analysis, or "why didn't the JIT optimize this" is ultimately a discussion about what bytecode the JIT is looking at.
--> You rarely need to hand-write or hand-optimize bytecode, but being able to READ it closes a specific and common gap: source code that "looks the same" (two ways of writing what seems like the same logic) can compile to meaningfully different bytecode, and profilers/JIT logs report in terms of methods and sometimes bytecode offsets, not source lines -- being able to map back to source requires knowing what you're looking at.

# The Class File Format -- A High-Level Map

--> A `.class` file is a binary format, but its structure is simple enough to describe as a sequence of sections:

```text
ClassFile {
    u4  magic;                  // 0xCAFEBABE -- the famous magic number, identifies it as a class file
    u2  minor_version;
    u2  major_version;          // determines minimum required JVM (e.g. 61 = Java 17, 65 = Java 21)
    u2  constant_pool_count;
    cp_info constant_pool[...]; // THE most important section for understanding -- see below
    u2  access_flags;           // public, final, abstract, interface, ...
    u2  this_class;             // index into constant pool: this class's name
    u2  super_class;            // index into constant pool: superclass name
    u2  interfaces_count;
    u2  interfaces[...];
    u2  fields_count;
    field_info fields[...];
    u2  methods_count;
    method_info methods[...];   // each method's bytecode lives here, in a "Code" attribute
    u2  attributes_count;
    attribute_info attributes[...];
}
```

--> **The Constant Pool** is the key structure to understand conceptually -- it's a table of literals and symbolic references (class names, method names, field names, string literals, numeric constants) that the rest of the class file refers to BY INDEX rather than embedding directly. When bytecode calls a method, it doesn't embed the method's name inline -- it references constant pool entry #N, which in turn describes the class, method name, and descriptor to invoke. This indirection is exactly what makes Java's dynamic linking model work: a class can be compiled against another class's public API and only resolve those symbolic references at class-loading/first-use time.
--> **Method descriptors** are a compact string encoding of a method's signature, e.g. a method `int add(String s, double d)` has descriptor `(Ljava/lang/String;D)I` -- `L...;` for object types, single letters for primitives (`I`=int, `D`=double, `J`=long, `Z`=boolean, `V`=void, etc.), parameters inside the parens, return type after. Recognizing this shorthand is necessary for reading raw bytecode or stack traces from tools that don't pretty-print it.

# Disassembling with `javap`

--> `javap` ships with every JDK -- it reads a compiled `.class` file and prints a human-readable view, at varying levels of detail.

```text
javap ClassName                    -- public members only, no bytecode (like a public API summary)
javap -p ClassName                 -- include private members too
javap -c ClassName                 -- disassemble: show actual bytecode instructions per method
javap -v ClassName                 -- verbose: bytecode + constant pool + stack map table + everything
javap -l ClassName                 -- include line-number and local-variable tables (needs -g compile)
```

--> **`javap` operates on compiled `.class` files, not `.java` source** -- run `javac Example.java` first (or use a build's `target`/`out` directory), then `javap` the resulting `.class`, or point it at a fully-qualified class name that's on the classpath.
--> Compiling with `javac -g` includes debugging info (local variable names, line number tables) -- without it, `javap -c` output still works but shows only numbered local variable slots (`0`, `1`, `2`) instead of the source names, and `-l` has nothing meaningful to show.

# Reading Bytecode -- A Worked Example

```java
public class Example {
    public int add(int a, int b) {
        return a + b;
    }
}
```

```text
$ javap -c Example

public int add(int, int);
  Code:
     0: iload_1
     1: iload_2
     2: iadd
     3: ireturn
```

--> **The JVM is a stack machine, not a register machine** -- almost every instruction pushes to or pops from an operand stack rather than naming registers. Reading bytecode means mentally tracking what's currently on that stack.
--> `iload_1` -- push local variable slot 1 (the `a` parameter; slot 0 is always `this` for an instance method) onto the operand stack, as an int.
--> `iload_2` -- push local variable slot 2 (`b`) onto the stack. Stack is now `[a, b]`.
--> `iadd` -- pop the top two ints, add them, push the result. Stack is now `[a+b]`.
--> `ireturn` -- pop the top int and return it from the method.
--> **The `i` prefix on most of these means "operates on int"** -- the JVM instruction set is largely typed per-instruction rather than generic: `iadd`/`ladd`/`fadd`/`dadd` for int/long/float/double addition, `iload`/`lload`/`fload`/`dload`/`aload` for loading each primitive type or a reference (`a` = reference/address), etc. This typing is part of why bytecode verification can catch type errors before code ever runs.

# A Few More Instructions Worth Recognizing

| Instruction (family) | Meaning |
|---|---|
| `aload_0` | Push local slot 0 (`this` in an instance method) |
| `getfield` / `putfield` | Read/write an instance field |
| `getstatic` / `putstatic` | Read/write a static field |
| `invokevirtual` | Call an instance method resolved via normal virtual dispatch (overridable) |
| `invokespecial` | Call a constructor, private method, or superclass method (non-virtual, resolved at compile time) |
| `invokestatic` | Call a static method |
| `invokeinterface` | Call a method through an interface reference |
| `invokedynamic` | Dynamically resolved call site -- underlies lambdas, method references, and string concatenation in modern Java |
| `new` | Allocate a new object (uninitialized -- a following `invokespecial` runs the constructor) |
| `athrow` | Throw the exception on top of the stack |
| `goto` | Unconditional jump -- backing loops |
| `if_icmpge`, `ifeq`, etc. | Conditional branches -- backing `if`/loop conditions |
| `checkcast` | Runtime type check (backs explicit casts and, notably, generic-type erasure checks) |

--> **`invokedynamic` is worth calling out specifically** -- it was added in Java 7 originally for dynamic languages on the JVM, but became central to modern Java itself: lambda expressions compile to an `invokedynamic` call site that lazily generates the actual implementation class at first invocation (via `LambdaMetafactory`), rather than `javac` eagerly generating a named inner class for every lambda. This is why decompiling a class with lambdas shows an `invokedynamic` instruction rather than a visible anonymous class, and it's part of why lambdas have a small one-time linkage cost on first use per call site, amortized away afterward.
--> **`invokevirtual` vs `invokespecial` vs `invokestatic`** matters for understanding what the JIT even CAN inline easily -- a call that's resolved non-virtually at compile time (`invokespecial`/`invokestatic`, or a `final`/effectively-monomorphic `invokevirtual`) is a much easier inlining target than a truly polymorphic `invokevirtual`/`invokeinterface` call site with multiple possible implementations, which ties directly into File 05's inlining discussion.

# A More Revealing Example -- Autoboxing Cost

```java
public int sumBoxed(List<Integer> nums) {
    int total = 0;
    for (Integer n : nums) {
        total += n;
    }
    return total;
}
```

--> Disassembling this shows an `invokevirtual` call to `Integer.intValue()` inside the loop body (unboxing `n` to add it to the primitive `total`) that isn't visible at all in the source -- the `+=` on a boxed `Integer` silently compiles to an explicit unbox-then-add. This is exactly the kind of thing that's invisible reading source but obvious reading bytecode, and explains real performance differences between `List<Integer>` and `int[]`-based loops that "look" like they should behave the same.
--> More generally: **string concatenation with `+`**, **enhanced-for loops over collections**, **try-with-resources**, and **switch on strings/enums** all desugar into meaningfully more bytecode than the source implies (an iterator's `hasNext()`/`next()` calls, a `StringBuilder` chain or `invokedynamic` call for concatenation on modern `javac`, a synthetic lookup table for enum switches) -- `javap -c` is the direct way to see exactly what a given language feature actually costs, rather than guessing.

# How This Helps Debug Performance Issues

--> **Confirming what the JIT sees.** JIT logs (`-XX:+PrintCompilation`, File 05) and profiler flame graphs report at the method level, occasionally with bytecode offsets for deopt reasons -- being able to open the class in `javap -c` and find that offset turns a cryptic log line into an exact instruction.
--> **Explaining a surprising inlining decision.** A method that "looks small" in source can compile to more bytecode than expected (autoboxing, hidden iterator calls, synthetic accessor methods for private field access from nested classes) and cross the JIT's default inlining size threshold (`-XX:MaxInlineSize`, ~35 bytecodes) as a result -- `javap -c` is how you'd actually verify the method's real bytecode size rather than guessing from source line count.
--> **Distinguishing "same behavior, different cost" alternatives.** When two idiomatic ways of writing something look equivalent (a `for` loop vs a stream pipeline, a boxed vs primitive collection, string concatenation via `+` vs `StringBuilder`), comparing their bytecode is a fast, JVM-version-independent way to see structurally what's different before reaching for a profiler to measure it live.
--> **Verifying build/annotation-processor output.** Frameworks that generate bytecode or rewrite classes at build/load time (Lombok, some AOP frameworks, older ORM proxy generation) can be sanity-checked with `javap` to confirm the generated class actually contains what's expected -- especially useful when tracking down a bug that only appears with the annotation processor enabled.

# Gotchas and Best Practices

--> **Bytecode instruction count is not the same as runtime cost.** A method with more bytecode isn't necessarily slower -- once JIT-compiled, the generated machine code is what actually runs, and the JIT can eliminate significant portions of "expensive-looking" bytecode entirely (dead code elimination, escape analysis avoiding allocation, loop unrolling). Bytecode tells you what the interpreter/JIT starts from, not the final runtime cost.
--> **`-g` at compile time changes debug info, not runtime behavior** -- always safe to add for a bytecode-reading session, never a performance concern in production (though release builds commonly compile with reduced debug info for smaller class files, this has negligible runtime performance impact of its own).
--> **Don't hand-edit bytecode as a normal workflow.** Tools exist for it (ASM, Javassist, ByteBuddy) and are legitimate for frameworks (mocking libraries, AOP, code coverage tools all do this) but it's a specialist, error-prone activity outside that context -- the goal of this material is READING bytecode to understand what's happening, not writing it by hand.
--> **`invokedynamic`-based lambdas mean a decompiled lambda body doesn't look like a normal named class** -- don't be surprised that searching for an obvious `LambdaExpression$1.class`-style file doesn't work the way it might have for pre-Java-8 anonymous classes; the actual implementation class is generated at runtime by `LambdaMetafactory`, not present as a `.class` file on disk at all.
--> **Major/minor version in the class file header is a fast compatibility check** -- `Unsupported class file major version N` at runtime means the class was compiled for a newer Java than the JVM running it; `javap -v` shows this version directly, which is a faster diagnosis than guessing from a stack trace alone.
