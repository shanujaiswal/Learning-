# Why Packages Exist

--> A package is a namespace -- a way of grouping related classes and interfaces under a common name so that two classes with the same simple name (e.g. `List`) don't collide, as long as they live in different packages (`java.util.List` vs `java.awt.List` are entirely distinct types).
--> Without packages, every class in every library ever written would have to share one flat global namespace -- utterly unworkable once you're combining code from multiple authors, teams, and third-party libraries in a single project.
--> Beyond avoiding collisions, packages give LOGICAL ORGANIZATION -- related classes live together (`java.io` for I/O, `java.util` for collections/utilities, `java.net` for networking), which makes large codebases navigable and communicates intent about how classes relate to each other.
--> Packages also form the basis of one whole tier of access control (package-private / default access, covered later in this file) -- classes in the same package are treated as trusted collaborators that can see more of each other than unrelated classes can.

# The `package` Declaration and Directory Structure

--> A source file declares which package it belongs to with a `package` statement as the very first non-comment line in the file -- before any `import` statements or class declarations.

```java
package com.warpx.billing;

public class Invoice {
    // ...
}
```

--> **The package name MUST mirror the directory path exactly.** A class declared `package com.warpx.billing;` must physically live in a directory path `com/warpx/billing/` relative to the project's source root -- the compiler and JVM use this mapping to locate `.class` files on disk (or inside a JAR).

```text
project-root/
  src/
    com/
      warpx/
        billing/
          Invoice.java      <-- package com.warpx.billing;
          Payment.java      <-- package com.warpx.billing;
        billing/reports/
          InvoiceReport.java  <-- package com.warpx.billing.reports;
```

--> Note that `com.warpx.billing.reports` is a DIFFERENT package from `com.warpx.billing`, even though the name looks like a "sub-package" -- Java packages are not hierarchical in terms of access; a class in `com.warpx.billing.reports` gets no special access to package-private members of `com.warpx.billing`. The dotted naming is purely a folder-nesting and naming CONVENTION, not an inheritance-like relationship.
--> **Reverse-domain naming convention** -- packages are conventionally named starting with a reversed internet domain the organization controls (`com.warpx.*`, `org.apache.*`) specifically to keep package names globally unique across companies, the same collision-avoidance goal packages solve for classes, applied one level up.

# The Default (Unnamed) Package

--> A class with no `package` statement at all belongs to the "default package" (also called the unnamed package).
--> **Why it's discouraged in real projects:**
  - Classes in the default package CANNOT be imported by classes that live in a named package -- there is no name to import, so any class outside the default package simply cannot use them.
  - It defeats the entire point of namespacing -- every class in the default package shares one flat space, reintroducing the exact collision risk packages exist to prevent.
  - Most build tools and IDEs (Maven, Gradle) assume a package structure under `src/main/java` and behave awkwardly or emit warnings for unpackaged classes.
--> The default package is fine for a five-line scratch file you compile and run once to test an idea -- it should never appear in real production code.

# The `import` Statement

--> `import` lets you refer to a class by its SIMPLE name (`Invoice`) instead of typing the fully-qualified name (`com.warpx.billing.Invoice`) every time you use it. It is purely a compile-time convenience for the source file -- it does not load anything at runtime and does not affect the compiled bytecode's behavior.

```java
import com.warpx.billing.Invoice;      // single-type import -- imports exactly one class
import java.util.List;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) {
        List<Invoice> invoices = new ArrayList<>();
    }
}
```

--> **Wildcard import** (`import java.util.*;`) imports every public class directly inside that package, saving you from listing each one individually.

```java
import java.util.*;    // brings in List, ArrayList, Map, HashMap, Set, ... all at once
```

--> **Why wildcard imports are generally discouraged in real projects:**
  - They obscure exactly which classes a file actually depends on -- a reader (or a tool) can no longer tell at a glance where `Timer` came from if both `java.util.*` and `java.awt.*` are wildcard-imported, since both packages contain a class named `Timer`.
  - If two wildcard-imported packages both contain a class with the same simple name and the file actually uses that name, it's a compile ERROR ("reference is ambiguous") -- a single-type import never has this problem because it's explicit.
  - Most style guides (including Google's Java style guide) and most IDEs default to expanding wildcards into explicit single-type imports automatically, treating wildcards as something to avoid in checked-in code, not as a style choice with no downside.
  - Wildcard imports do NOT reach into sub-packages -- `import java.util.*;` does NOT import `java.util.concurrent.*` -- another common source of confusion.
--> **`java.lang` is imported automatically** into every Java source file -- that's why `String`, `Object`, `Integer`, `Math`, `System`, and `Exception` are usable without ever writing `import java.lang.*;`. Every other package, including `java.util` and `java.io`, must be explicitly imported.

# Fully-Qualified Names as an Alternative to `import`

--> You can always skip `import` entirely and just write the fully-qualified name at the point of use -- useful for a one-off usage, or specifically to disambiguate when two imported packages both have a class of the same simple name.

```java
public class Report {
    private java.util.Date generatedOn;   // no import needed -- fully-qualified inline

    public static void main(String[] args) {
        java.awt.List awtList = new java.awt.List();
        java.util.List<String> utilList = new java.util.ArrayList<>();
        // Both `List` types used in the same file without ambiguity, because each
        // usage is fully qualified instead of relying on a plain `import`.
    }
}
```

# `static import`

--> A regular `import` brings in a TYPE. A `static import` brings in a type's `static` MEMBERS (fields and methods) so they can be used unqualified, as if they were declared locally.

```java
import static java.lang.Math.*;    // static-imports every static member of Math

public class Circle {
    public static void main(String[] args) {
        double radius = 5.0;
        double area = PI * radius * radius;      // PI instead of Math.PI
        double diag = sqrt(pow(radius, 2) * 2);  // sqrt(...) / pow(...) instead of Math.sqrt / Math.pow
        System.out.println(area + " " + diag);
    }
}
```

--> **Single-member static import** is also allowed and is the more targeted, more readable option: `import static java.lang.Math.PI;` brings in only `PI`, leaving `Math.sqrt(...)` still written with its qualifier.
--> **Use case** -- static imports shine for math-heavy code (`PI`, `sqrt`, `pow`, `max`, `min`) and for test frameworks like JUnit, where `assertEquals(...)` and `assertTrue(...)` read far better unqualified than as `Assertions.assertEquals(...)` sprinkled through every test method.
--> **Caution about overuse** -- static-importing several different classes' members into one file can make code ambiguous to a human reader even when the compiler resolves it fine: seeing a bare `max(a, b)` doesn't tell you at a glance whether that's `Math.max`, `Collections.max`, or a custom static method from a third static-imported class. As a rule of thumb, static-import only well-known, unambiguous utility members (`Math`, `Objects.requireNonNull`, test-assertion libraries) -- and avoid wildcard-static-importing multiple unrelated classes into the same file.

# The Classpath

--> The classpath is the list of locations -- directories and/or JAR files -- that the JVM (`java`) and the compiler (`javac`) search when they need to locate a compiled `.class` file for a given fully-qualified class name.
--> When you write `import com.warpx.billing.Invoice;` or simply reference `Invoice`, the JVM doesn't know where `Invoice.class` physically sits until it walks the classpath entries looking for `com/warpx/billing/Invoice.class` under each one.

```text
java -cp out;lib/commons.jar;lib/gson.jar com.warpx.Main     (Windows -- ; separator)
java -cp out:lib/commons.jar:lib/gson.jar com.warpx.Main     (Linux/Mac -- : separator)
```

--> **Two ways to set it:**
  - The `CLASSPATH` environment variable -- a system-wide or session-wide default, applied whenever `-cp`/`-classpath` isn't explicitly given. Generally discouraged for real projects because it's an invisible, easy-to-forget global setting that silently changes behavior between machines.
  - The `-cp` (or `-classpath`) flag passed directly to `java`/`javac` -- the explicit, reproducible, and strongly preferred approach, since the classpath used is visible right there in the command that ran.
--> **`.` (the current directory)** is a common classpath entry meaning "also look for classes as loose `.class` files starting from wherever this command is being run from" -- necessary for quick compile-and-run workflows without a JAR, e.g. `javac Main.java && java -cp . Main`.
--> If a class truly cannot be found on the classpath at runtime, the JVM throws `NoClassDefFoundError` or `ClassNotFoundException` -- one of the most common beginner errors, and it is almost always a classpath misconfiguration rather than a code bug.

# Access Modifiers

--> Access modifiers control WHICH other code is allowed to see and use a class, field, method, or constructor. Java has four levels, and critically, "package-private" is a real, distinct level even though it has no keyword of its own -- it's simply what you get when you write NO modifier at all.

```java
public class Account {
    public String accountId;          // visible everywhere
    protected double balance;         // visible in package + subclasses anywhere
    double interestRate;              // no modifier -- "default"/package-private -- visible only in this package
    private String pin;               // visible only inside this exact class
}
```

## Full Access Modifier Visibility Table

| Modifier | Same Class | Same Package | Subclass (different package) | Different Package (non-subclass) |
|---|---|---|---|---|
| `private` | Yes | No | No | No |
| *(default / package-private, no keyword)* | Yes | Yes | No | No |
| `protected` | Yes | Yes | Yes | No |
| `public` | Yes | Yes | Yes | Yes |

--> **`private`** -- the tightest scope. Only code physically inside the same class body can access it (this includes other instances of the same class, and nested/inner classes of that class -- "same class" is per-class, not per-object).
--> **default/package-private** -- accessible to any class in the same package, regardless of inheritance -- this is what you get automatically if you omit a modifier, and it's exactly why unrelated helper classes are often deliberately left without a modifier: they're implementation details meant to be used only by collaborators in the same package.
--> **`protected`** -- everything package-private gives you, PLUS visibility to subclasses even if that subclass lives in a different package -- but with a subtlety: a subclass in a different package can only access the protected member through a reference typed as the SUBCLASS itself (or further down), not through an arbitrary reference typed as the superclass, and not to access another unrelated object's protected member.
--> **`public`** -- no restriction at all; accessible from any class, in any package, that can see the class itself.

## Access Modifiers for Top-Level Classes vs Members

--> A **top-level class** (a class not nested inside another) may only be declared `public` or default/package-private -- `private` and `protected` are not legal on a top-level class declaration, because "visible only within this class" or "visible to subclasses" are meaningless concepts for something that isn't a member of anything.
  - `public class Invoice { ... }` -- visible to any other package that imports it.
  - `class Invoice { ... }` (no modifier) -- visible only within its own package; commonly used for internal helper classes not meant to be part of a package's public API.
--> **Members** (fields, methods, constructors, and NESTED classes) may use all four levels, since a member always exists inside some enclosing class and the extra two levels (`private`, `protected`) make sense relative to that enclosing class and its subclasses.
--> A common real-world pattern: a `public` class exposing only `public` methods as its intended API, with `private` fields and `private` helper methods hidden as implementation detail -- this is ENCAPSULATION, and access modifiers are the language mechanism that enforces it rather than merely suggesting it.

# Standard Java Project Directory Structure

--> Even in Core Java without any build tool, it helps to know the convention that Maven and Gradle (the two dominant Java build tools) standardized on, because almost every real Java project you'll open follows it:

```text
my-project/
  src/
    main/
      java/
        com/warpx/billing/
          Invoice.java          <-- application source code
      resources/
        application.properties  <-- non-Java files bundled with the app (config, templates, etc.)
    test/
      java/
        com/warpx/billing/
          InvoiceTest.java      <-- test source code, mirrors the main package structure
      resources/
        test-data.json
  pom.xml                        (Maven)  /  build.gradle  (Gradle)
```

--> **`src/main/java`** holds actual application code; **`src/test/java`** holds test code, kept in a SEPARATE source tree (though usually mirroring the same package names) so that test classes are never accidentally bundled into the production artifact.
--> This structure is a convention, not a language rule -- `javac`/`java` know nothing about "main" or "test" -- but following it is what lets build tools auto-discover your source files without extra configuration, and what makes any Java project instantly navigable to another Java developer.

# JAR Files

--> A JAR (Java ARchive) is just a ZIP file, using the `.jar` extension, that bundles a whole tree of compiled `.class` files (preserving their package-derived directory structure) plus a manifest file describing metadata such as which class contains `main`.
--> JARs are how compiled Java code is packaged for distribution and reuse -- a library you `import` from (like a JSON parsing library) is almost always handed to you as a single `.jar` file, which you then add to your classpath (`-cp mylib.jar`) so its classes become resolvable.
--> `jar cf app.jar -C out .` creates a JAR from compiled classes; `java -jar app.jar` runs one directly, reading the "Main-Class" entry from its manifest to know which class's `main` method to invoke.

# Deep Dive -- Why `protected` Across Packages Is More Restrictive Than It Looks

--> It's tempting to read the table above as "protected = package-private + visible to any subclass anywhere," but the subclass-from-a-different-package case has a real restriction worth understanding precisely.
--> When a subclass `B` in package `q` extends class `A` in package `p`, `B` can access `A`'s protected members only on an object whose static type is `B` (or a further subclass of `B`) -- NOT on an arbitrary `A` reference, even though that reference might point to an actual `B` instance at runtime.

```java
// package p
public class A {
    protected int secret = 42;
}

// package q
public class B extends A {
    void show(A other, B self) {
        // System.out.println(other.secret);  // COMPILE ERROR -- `other` is typed as A, different package
        System.out.println(self.secret);      // OK -- `self` is typed as B, the subclass itself
        System.out.println(this.secret);      // OK -- inherited member, accessed through `this`
    }
}
```

--> The reasoning: `protected` grants access as a courtesy to your OWN subclass hierarchy, not a general license to poke at every `A` object that exists. If `other.secret` were allowed, `B` could reach into a completely unrelated `A` instance (say, one created by a third package that has nothing to do with `B`) purely because `B` happens to extend `A` -- that would leak far more access than "protected for subclassing" is meant to grant. Restricting access to references statically typed as the subclass (or below) closes that hole while still letting a subclass fully manage its own inherited state.
