# What a Method Is and Why It Matters

--> A **method** is a named, reusable block of code that performs a task -- it's Java's unit of behavior, the same role a "function" plays in other languages. Every line of executable Java code lives inside SOME method (or constructor, or initializer block) -- there's no such thing as top-level statements floating outside a class, unlike Python or JavaScript.
--> Methods exist for the same reasons they exist everywhere: avoiding duplication (write the logic once, call it many times), giving behavior a meaningful name (`calculateTotalPrice(order)` reads better than the raw arithmetic inline), and creating a seam for testing and abstraction (callers only need to know WHAT a method does, not HOW).

# Anatomy of a Method Declaration

--> Every method declaration follows the same shape, and each piece has a distinct job:

```java
public static int add(int a, int b) throws ArithmeticException {
    return a + b;
}
```

```text
public          -- MODIFIERS: access control (public/private/protected/default) and other
static             keywords (static, final, abstract, synchronized...). Zero or more, in any
                    conventional order.
int             -- RETURN TYPE: the type of value the method hands back to its caller, or
                    `void` if it hands back nothing.
add             -- METHOD NAME: an identifier, conventionally camelCase, ideally a verb or
                    verb phrase describing the action (add, calculateTotal, isValid).
(int a, int b)  -- PARAMETER LIST: the ordered list of typed variables the method accepts,
                    each a (type, name) pair, comma-separated. Can be empty: ().
throws ...      -- THROWS CLAUSE: optional, declares checked exceptions this method might
                    propagate to its caller instead of handling internally.
{ ... }         -- METHOD BODY: the block of statements that runs when the method is called.
                    Abstract methods and interface method declarations omit the body entirely
                    (terminated with `;` instead) -- covered in the OOP topic.
```

--> **Modifiers** control who can call the method (`public`/`private`/`protected`/package-private) and how it behaves (`static` means it belongs to the class rather than an instance -- more on this at the end of this file, `final` means it can't be overridden by a subclass, `abstract` means it has no body and must be implemented elsewhere).

# The Method Signature -- What Actually Identifies a Method

--> This is the single most precise and most frequently misunderstood term in this file, because Java uses "signature" in an exact technical sense that determines what the compiler allows and what it doesn't.

```text
METHOD SIGNATURE = method name + parameter types (in order)

INCLUDED in the signature:
    - the method name
    - the number of parameters
    - the type of each parameter
    - the ORDER of parameter types

NOT included in the signature:
    - the return type
    - the parameter NAMES
    - the throws clause
    - modifiers (public, static, etc.)
```

--> **Why the return type is excluded -- and why that's not arbitrary.** Consider a call site: `add(3, 5);`. The compiler resolves WHICH `add` method to invoke purely by looking at the arguments being passed (`3, 5` -- two `int`s) -- it does this BEFORE it knows what the caller intends to do with the result, and in fact the caller might not use the result at all (a statement can call a method and discard its return value entirely). If two methods differed ONLY by return type -- `int add(int a, int b)` and `double add(int a, int b)` -- the compiler would have no information at the call site to decide between them, since the call `add(3, 5)` looks identical either way. This is precisely why Java (and most statically-typed languages) forbid overloading by return type alone -- it would make method resolution ambiguous or even impossible in cases where the return value is discarded.

```java
// This does NOT compile -- same name, same parameter list, different return type only:
public int compute(int x) { return x; }
public double compute(int x) { return x; }   // COMPILE ERROR: method compute(int) is already defined
```

--> **Why parameter names are excluded.** `add(int a, int b)` and `add(int x, int y)` are the EXACT SAME signature -- the names `a`/`b` vs `x`/`y` exist purely for readability inside the method body and in IDE tooltips; they play no role in distinguishing one method from another at a call site, because callers pass arguments positionally (or, since Java 8, sometimes referenced via reflection, but never by matching parameter names at a normal call site).

# `return` and `void` Methods

--> The `return` statement does two things at once: it immediately exits the method (no code after `return` in that execution path runs), and -- if the method's return type is not `void` -- it hands a value of that type back to the caller.

```java
public static boolean isEven(int n) {
    if (n % 2 == 0) {
        return true;      // exits here if n is even
    }
    return false;          // otherwise exits here
}

public static void logMessage(String msg) {   // void -- returns nothing
    System.out.println("[LOG] " + msg);
    return;                                     // optional here -- "return;" with no value
}                                                 // is legal in a void method, and falling off
                                                    // the end of the body implicitly returns too
```

--> A non-`void` method MUST return a value (or throw an exception) on every possible execution path -- the compiler performs "definite assignment"-style reachability analysis and refuses to compile a method where some path falls off the end without returning. A `void` method never needs an explicit `return` at all; the method simply ends when the closing brace is reached.

# Parameters vs Arguments -- Precise Terminology

--> These two words are often used interchangeably in casual speech, but they mean different things and it's worth being precise, because the distinction becomes important once pass-by-value is discussed below.

```text
PARAMETER  -- the variable declared in the METHOD DEFINITION -- a placeholder name and type.
ARGUMENT   -- the actual VALUE supplied at the CALL SITE -- what gets plugged into the parameter.
```

```java
public static void greet(String name) {   // "name" is the PARAMETER
    System.out.println("Hello, " + name);
}

greet("Vanisha");    // "Vanisha" is the ARGUMENT passed for the "name" parameter
```

# Varargs -- Variable-Length Argument Lists

--> Varargs (`...`) let a method accept ZERO or more arguments of a given type without the caller having to manually wrap them in an array first -- introduced in Java 5, and heavily used by the standard library (`String.format(String fmt, Object... args)`, `List.of(E... elements)`).

```java
public static int sum(int... numbers) {   // "numbers" behaves as an int[] inside the body
    int total = 0;
    for (int n : numbers) {
        total += n;
    }
    return total;
}

sum();              // legal -- numbers becomes an empty int[0]
sum(5);              // numbers becomes {5}
sum(1, 2, 3, 4, 5);  // numbers becomes {1, 2, 3, 4, 5}
sum(new int[]{1, 2}); // also legal -- an actual array can be passed directly, no wrapping needed
```

--> **The rules governing varargs:**

```text
1) A method can declare AT MOST ONE varargs parameter.
2) If present, the varargs parameter MUST be the LAST parameter in the list --
   the compiler needs every fixed parameter's position to be unambiguous before
   the "collect everything else" parameter begins.
3) Legal:    void log(String tag, Object... args)
   Illegal:  void log(Object... args, String tag)     // COMPILE ERROR
   Illegal:  void log(Object... a, Object... b)         // COMPILE ERROR -- two varargs
```

--> **How varargs desugars internally.** Varargs is pure syntactic sugar over arrays -- the compiler rewrites `int... numbers` into `int[] numbers` in the compiled bytecode, and rewrites every call site to wrap the loose arguments into a `new int[]{...}` array before the call. This is why `numbers.length`, `for (int n : numbers)`, and every other array operation work unmodified inside a varargs method body -- as far as the method body is concerned, it simply received an array.

# Pass-by-Value Semantics in Java

--> This is the most important -- and most persistently misunderstood -- semantic rule about how methods receive data in Java. State it precisely first, then unpack why it confuses people:

```text
JAVA IS ALWAYS PASS-BY-VALUE. THERE IS NO PASS-BY-REFERENCE IN JAVA. EVER.
```

--> For a primitive (`int`, `double`, `boolean`, ...), "the value" is obviously the primitive value itself -- a copy of the number is handed to the method.
--> For an object, "the value" being copied is the **reference** (essentially a pointer/address to where the object lives on the heap) -- NOT the object itself. Java never copies the object's fields when passing it to a method. This single fact is the source of nearly all beginner confusion on this topic, because it produces two behaviors that look contradictory unless you understand what's actually being copied:

```text
1) Reassigning the PARAMETER inside the method does NOT affect the caller's variable.
2) Mutating the OBJECT the reference points to DOES affect what the caller sees,
   because both the caller's reference and the method's copy of that reference
   point to the exact same object on the heap.
```

```java
public static void reassignPrimitive(int x) {
    x = 999;                          // only the local copy changes
}

public static void reassignReference(int[] arr) {
    arr = new int[]{999, 999};        // "arr" now points to a NEW array;
                                        // the caller's reference is untouched
}

public static void mutateThroughReference(int[] arr) {
    arr[0] = 999;                      // follows the SHARED reference and changes
                                        // the ONE array both caller and method see
}

public static void main(String[] args) {
    int num = 5;
    reassignPrimitive(num);
    System.out.println(num);            // 5 -- unaffected, primitive was copied

    int[] data = {1, 2, 3};
    reassignReference(data);
    System.out.println(data[0]);         // 1 -- unaffected, only the LOCAL copy of
                                            // the reference was repointed

    mutateThroughReference(data);
    System.out.println(data[0]);         // 999 -- affected! same object, mutated via
                                            // the shared reference
}
```

# Deep Dive -- Stack, Heap, and What "Copying a Reference" Actually Means

--> To fully dissolve the confusion, it helps to picture what memory looks like during `mutateThroughReference(data)` above. Java has two relevant memory regions: the **stack** (holds local variables and method call frames, including primitive values and reference VALUES) and the **heap** (holds the actual objects/arrays that references point to).

```text
STACK                                   HEAP
-----------------------------           --------------------------------
main() frame:
  data  --------ref A------------------> [ int[] object @ address A ]
                                            index 0: 1   (will become 999)
                                            index 1: 2
                                            index 2: 3

mutateThroughReference(int[] arr) frame:
  arr   --------ref A (COPY of A)------> (same object @ address A, shown above)
```

--> `data` in `main` and `arr` inside `mutateThroughReference` are TWO SEPARATE VARIABLES living in two separate stack frames -- but the VALUE stored in each of them is the same address, `A`. Copying a reference copies the address, not the thing it addresses -- exactly like photocopying a piece of paper that has someone's house address written on it: you now have two pieces of paper, but there's still only one house. Writing on the copied paper (`arr = new int[]{...}`, i.e. writing a DIFFERENT address onto it) doesn't change what's written on the original paper (`data` still holds address `A`). But walking to the house at that address and repainting it (`arr[0] = 999`, i.e. following the reference and mutating the object) is visible to anyone else holding a paper with that same address -- including `data` back in `main`.
--> This is why the common but IMPRECISE phrase "Java passes objects by reference" is wrong, and why the precise phrasing matters: Java passes the REFERENCE by value. If Java were truly pass-by-reference (like C++'s `&` parameters, or `ref` in C#), then `reassignReference(data)` WOULD be visible to the caller -- `data` itself would be repointed to the new array. It isn't. That single test -- does reassigning the parameter inside the method change what the caller's variable refers to? -- is the definitive way to prove Java is pass-by-value even for objects.
--> **Practical consequence:** if a method needs to hand back a NEW object/value to the caller (rather than mutate an existing one), it must do so via `return`, not by reassigning a parameter -- reassigning a parameter is invisible outside the method, full stop.

# Method Overloading

--> **Overloading** means defining multiple methods in the same class (or class hierarchy) that share the same NAME but have different SIGNATURES -- recall from above that "signature" means parameter count, types, and order, and explicitly EXCLUDES return type. Overloading is Java's mechanism for letting a single conceptual operation ("print", "add", "max") work naturally across different input shapes, resolved entirely at COMPILE TIME (this is "static" or "compile-time" polymorphism, as opposed to overriding, which is resolved at runtime -- covered in the OOP topic).

```java
public static void print(String s) { System.out.println("String: " + s); }
public static void print(int n)     { System.out.println("Int: " + n); }
public static void print(double d)  { System.out.println("Double: " + d); }
public static void print(int a, int b) { System.out.println("Two ints: " + a + ", " + b); }
```

--> **What makes a valid overload -- differ by at least one of:**

```text
1) NUMBER of parameters:      print(int a) vs print(int a, int b)
2) TYPE of parameters:        print(int a) vs print(double a)
3) ORDER of parameter types:  greet(String name, int age) vs greet(int age, String name)
```

--> **What does NOT make a valid overload (compile errors):**

```text
- Return type alone (shown earlier in the signature section).
- Parameter NAMES alone: print(int a) vs print(int b) -- identical signature, illegal.
- The `throws` clause alone.
- Modifiers alone (e.g. static vs non-static with otherwise identical signature) --
  this specific case is actually a compile error too: you cannot overload a method
  by making one version static and the other instance, if everything else matches.
```

# Overload Resolution -- The Order Java Tries

--> When a call like `foo(x)` could plausibly match more than one overload, the compiler picks the "most specific" applicable method using a strict, three-phase search, trying progressively looser matching rules and stopping at the first phase that finds exactly one applicable method:

```text
PHASE 1 -- Exact match (no conversion at all)
    Is there an overload whose parameter types exactly match the argument types?

PHASE 2 -- Widening primitive conversion (no boxing, no varargs)
    Can an argument be widened to fit a parameter without boxing?
    (byte -> short -> int -> long -> float -> double,  char -> int -> ...)

PHASE 3 -- Autoboxing / unboxing (no varargs)
    Can a primitive be boxed to its wrapper (int -> Integer) or vice versa
    to fit an overload?

PHASE 4 -- Varargs
    Only tried if nothing else matched -- treat a varargs parameter as the
    last resort "collect the rest" option.
```

```java
public static void choose(long x)     { System.out.println("long"); }
public static void choose(Integer x)   { System.out.println("Integer (boxed)"); }
public static void choose(int... x)    { System.out.println("varargs"); }

choose(5);   // prints "long" -- an int WIDENS to long (Phase 2) before it would ever
              // be autoboxed to Integer (Phase 3) or matched via varargs (Phase 4).
              // Widening is strictly preferred over boxing, and boxing is strictly
              // preferred over varargs.
```

--> **Why this order exists:** it's a deliberate design choice to make overload resolution predictable and to minimize surprises from autoboxing (introduced in Java 5, long after overloading already existed) -- Java prefers the conversion that is "closest" to what was actually written, treating autoboxing as more of a fallback convenience than a first-class conversion, and varargs as a last resort since it usually implies the compiler had to manufacture an array that wasn't explicitly there.

# Recursion Basics

--> A **recursive method** is one that calls itself, directly or indirectly, to solve a smaller instance of the same problem. Every correct recursive method needs two parts:

```text
BASE CASE      -- the simplest input(s), handled directly with no further recursive
                  call -- this is what stops the recursion from running forever.
RECURSIVE CASE -- reduces the problem toward the base case and calls itself on
                  that smaller/simpler version, then combines results if needed.
```

```java
public static long factorial(int n) {
    if (n <= 1) {                      // BASE CASE
        return 1;
    }
    return n * factorial(n - 1);        // RECURSIVE CASE -- shrinks toward the base case
}

public static long fibonacci(int n) {
    if (n <= 1) {                      // BASE CASE (covers both n=0 and n=1)
        return n;
    }
    return fibonacci(n - 1) + fibonacci(n - 2);   // RECURSIVE CASE -- two smaller calls
}
```

--> **Each recursive call adds a new frame to the call stack** -- a fresh block of stack memory holding that call's parameters, local variables, and return address. `factorial(5)` builds up 5 stacked frames before the base case is hit and they start unwinding back down, each frame multiplying in its pending `n *` as it returns. This is exactly the same call-stack-space idea introduced as O(n) auxiliary space in complexity analysis -- a recursive method that recurses `n` levels deep uses O(n) stack space even if it allocates no other data structure, and recursing too deep (commonly tens of thousands of frames, depending on JVM settings) throws a `StackOverflowError` before any other resource limit is hit.
--> Naive recursive Fibonacci is the classic example of recursion done inefficiently -- it makes roughly `2^n` calls because `fibonacci(n-2)` gets recomputed from scratch by both the `fibonacci(n-1)` branch and the direct `fibonacci(n-2)` branch, an O(2^n) time cost for what an iterative or memoized version solves in O(n). This ties directly back to the exponential-time row in the Big-O growth table.

# Static vs Instance Methods -- A Brief Preview

--> Every method so far in this file has been `static`, meaning it belongs to the CLASS itself and can be called without creating an object (`ClassName.methodName(...)`). An **instance method** (no `static` modifier) belongs to a particular OBJECT and can only be called on an instance (`someObject.methodName(...)`), because it implicitly operates on that object's own fields via the hidden `this` reference.
--> This distinction is foundational to Object-Oriented Programming and is covered in full depth in the OOP topic -- for now, the important takeaway is just that `static` methods are used for behavior that doesn't depend on any particular object's state (utility/helper methods, `main`, math functions), while instance methods are used for behavior that reads or modifies a specific object's data.
