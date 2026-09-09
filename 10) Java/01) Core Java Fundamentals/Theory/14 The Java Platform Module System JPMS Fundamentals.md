# The Java Platform Module System (JPMS) Fundamentals

--> The **Java Platform Module System** (JPMS, also called "Project Jigsaw," its development codename) was introduced in **Java 9** (2017) as the biggest structural change to the Java platform since generics in Java 5. It adds a new unit of code organization -- the **module** -- ABOVE the package level, with the JVM enforcing real, compiler- and runtime-checked boundaries between modules for the first time.
--> Mental model: before JPMS, Java had exactly two levels of code organization that mattered to the compiler/runtime -- **packages** (namespacing) and **JARs** (physical packaging, but with NO enforced boundary; anything `public` in any JAR on the classpath was accessible to anything else on the classpath). JPMS adds a THIRD level, the **module**, which is the first one where the JVM actually enforces "you may only see what I explicitly chose to expose to you."

# Why JPMS Was Created -- The Problems With "Classpath Java"

--> Three specific, longstanding pain points motivated JPMS:

| Problem | Pre-JPMS reality |
|---|---|
| **No true encapsulation across JARs** | Any `public` class in any JAR is accessible from any other JAR on the classpath -- there was no way to say "this package is public API, this other package is internal implementation detail" at the JAR level. Convention (`internal` package names, `@Deprecated`, documentation) was the only enforcement, and reflection could bypass even `private` access entirely. |
| **"JAR hell" / fragile classpath** | The classpath is a flat, unordered bag of JARs. Two JARs can each contain a class with the same fully-qualified name, and whichever is found first on the classpath silently wins -- with no error, just unpredictable, hard-to-debug behavior depending on JAR ordering. |
| **No reliable configuration** | Nothing checked, at startup, whether all of an application's actual dependencies were present and compatible. Missing a transitive dependency typically surfaced as a `NoClassDefFoundError` at runtime, potentially deep into execution, rather than a clear failure at startup. |
| **Monolithic JDK** | Before Java 9, the entire JDK (`rt.jar`) shipped as one giant, un-modularized blob -- you couldn't ship a slimmed-down runtime containing only the JDK pieces your application actually used. This mattered increasingly for containerized deployments where image size and startup footprint matter. |

--> JPMS addresses all four: it gives modules **strong encapsulation** (a package not explicitly exported is genuinely inaccessible, even via reflection by default), **reliable configuration** (missing or conflicting module dependencies are caught before the application even starts, with a clear error naming exactly what's wrong), and it let the **JDK itself be split into ~70+ modules** (`java.base`, `java.sql`, `java.xml`, etc.), enabling custom minimal runtimes via `jlink` (covered in the next Theory file).

# Module vs Package vs JAR -- Three Different Concepts

--> These three terms get conflated constantly by newcomers to JPMS. They solve different problems and exist at different levels:

| | Package | JAR | Module |
|---|---|---|---|
| What it is | A namespace for grouping related classes/interfaces | A physical ZIP-based archive format for distributing compiled code | A named, versioned unit declaring its own dependencies AND its own public API surface |
| Enforced by | Compiler (naming/import rules only) | Nothing -- purely a packaging/distribution mechanism | Both compiler AND JVM at runtime -- genuine access control |
| Declares dependencies? | No | No (a JAR's `MANIFEST.MF` can list a classpath, but nothing enforces it) | Yes -- `requires` directives, checked at compile time and launch time |
| Declares its public API? | No (every `public` class in a package is visible package-wide and beyond) | No | Yes -- `exports` directives; un-exported packages are NOT accessible outside the module, even for `public` classes |
| Physical vs logical | Logical only (source organization) | Physical (an actual file) | Can be BOTH -- a "modular JAR" is a single JAR file that also carries a `module-info.class`, making it a JAR that IS a module |

--> **Key relationship**: a module typically CONTAINS one or more packages, and is typically PACKAGED as one JAR (a "modular JAR"). So for a simple project, you might have: one module = one JAR = several packages = many classes. The module is the new outermost layer that adds real dependency declarations and real access control on top of what packages and JARs already provided.

# `module-info.java` -- The Module Descriptor

--> Every module is declared by a special source file named EXACTLY `module-info.java`, placed at the ROOT of a module's source tree (i.e., directly inside the source folder, not inside any package). It compiles to `module-info.class`, which the JVM reads at both compile time and launch time.

```java
// src/com.example.orders/module-info.java
module com.example.orders {
    requires java.sql;                          // this module needs java.sql's exported API
    requires com.example.customers;              // and needs another one of our own modules

    exports com.example.orders.api;               // this package IS the module's public surface
    exports com.example.orders.dto to com.example.reporting;   // qualified export -- visible ONLY to that module

    opens com.example.orders.entity;               // allows deep reflection into this package (e.g., for JPA/Hibernate)
}
```

--> **Module naming convention**: reverse-DNS style, matching the convention used for top-level packages (`com.example.orders`), to avoid collisions in the module graph the same way package naming avoids collisions among classes. Unlike package names, a module name is a SINGLE identifier with dots as visual separators -- it does not need to correspond to an actual package structure, though for clarity it usually does (a module's name often matches its main/root package).
--> **Where `module-info.java` lives**: at the root of the module's source directory. In a typical Maven/Gradle-style layout, that's directly under `src/main/java/`, sitting alongside (not inside) the top-level package directories.

```
src/main/java/
├── module-info.java                     <-- module descriptor, at the root
└── com/
    └── example/
        └── orders/
            ├── api/
            │   └── OrderService.java     <-- in an exported package: visible to other modules
            ├── dto/
            │   └── OrderDto.java         <-- in a qualified-exported package: visible only to com.example.reporting
            └── entity/
                └── Order.java             <-- in an opened package: reflectively accessible (e.g., Hibernate), not exported for compile-time use
```

# The Four Core Directives

### `requires` -- Declaring a Dependency

--> `requires <module-name>;` declares that THIS module needs another module's exported API to compile and run. The JVM checks, at both compile time and application launch, that every `requires`d module is actually present and resolvable -- if not, the application fails FAST with a clear `java.lang.module.FindException` naming the missing module, rather than a `NoClassDefFoundError` deep into execution.

```java
module com.example.orders {
    requires java.sql;                    // ordinary dependency
    requires transitive java.logging;      // "transitive" -- explained below
    requires static com.example.testkit;   // "static" -- compile-time-only dependency (optional at runtime)
}
```

--> **`requires transitive`** -- normally, if module A `requires` module B, and module C `requires` module A, module C does NOT automatically get access to B's exports -- C would need its own explicit `requires B`. Marking A's dependency on B as `requires transitive B` means anyone who `requires` A ALSO implicitly gets B's exports, without needing to declare it themselves. This is used when A's own public API exposes types FROM B (e.g., a method in A's exported API returns a type declared in B) -- forcing every consumer of A to separately `requires` B just to use A's API would be poor ergonomics, so `transitive` propagates that dependency automatically.
--> **`requires static`** -- a COMPILE-TIME-ONLY dependency; the module is required to compile against, but is optional at runtime (the application must not fail to launch just because it's absent). Common for annotation-processing libraries or optional integration points that are only needed if a particular optional feature is actually exercised.

### `exports` -- Declaring Public API

--> `exports <package>;` makes a package's `public` types (and their `public`/`protected` members) accessible to code OUTSIDE the module -- both at compile time (other modules can `import` and reference those types) and at runtime (reflection via the standard, respectful `Class`/`Method` APIs works normally).
--> **Any package NOT explicitly `exports`ed is completely inaccessible from outside the module** -- even if every class in it is `public`. This is the headline feature of JPMS: genuine, JVM-enforced encapsulation that simply did not exist pre-Java-9. Trying to `import com.example.orders.internal.Helper;` from another module, where `com.example.orders.internal` isn't exported, is a COMPILE ERROR (`package ... is not visible`), not just a style violation.

```java
module com.example.orders {
    exports com.example.orders.api;                              // fully public to any module that requires this one
    exports com.example.orders.internal.testutil to com.example.orders.test;   // qualified export -- visible ONLY to the named module(s)
    // com.example.orders.internal is NOT listed -- genuinely inaccessible from outside this module
}
```

--> **Qualified exports** (`exports <package> to <module1>, <module2>;`) let you expose a package to a SPECIFIC, named list of other modules (commonly a module's own test module, or a small number of tightly-coupled internal modules) without making it broadly public to everyone. This is a middle ground between "fully public" and "fully hidden" that plain packages/JARs never offered.

### `opens` -- Allowing Deep Reflection

--> `opens <package>;` grants REFLECTIVE access to a package's types at runtime, INCLUDING their `private` members via `setAccessible(true)` -- something `exports` alone does NOT permit. This exists specifically because many widely-used frameworks (Hibernate/JPA, Jackson, Spring, dependency-injection containers, testing frameworks like JUnit and Mockito) rely on deep reflection to instantiate objects, inject fields, and inspect private state, and that pattern predates JPMS by many years.

```java
module com.example.orders {
    exports com.example.orders.api;    // normal public API -- compile-time visible, reflection respects normal access rules

    opens com.example.orders.entity;    // JPA entities: Hibernate needs to reflectively set private fields, construct
                                          // via no-arg constructors, etc. -- exports alone would NOT allow this.

    opens com.example.orders.dto to com.fasterxml.jackson.databind;   // qualified opens -- reflection allowed only for Jackson
}
```

| | `exports` | `opens` |
|---|---|---|
| Compile-time access (import, reference types) | Yes | No (unless also exported) |
| Normal runtime access (calling `public` methods) | Yes | Yes |
| Deep reflection (`setAccessible(true)` on private members) | No | Yes |
| Typical use | A module's genuine public API | Packages frameworks need to reflect into (JPA entities, Jackson-mapped DTOs, JUnit test classes) |

--> **`open module`** -- prefixing the ENTIRE module declaration with `open` (`open module com.example.orders { ... }`) opens EVERY package in the module to reflection, as a blanket convenience. This is common for application modules (as opposed to library modules) where fine-grained per-package `opens` isn't worth the ceremony, and especially common as a pragmatic migration aid for codebases leaning heavily on frameworks that need broad reflective access.

```java
open module com.example.orders {
    requires java.sql;
    exports com.example.orders.api;
    // every package in this module is implicitly "opened" -- no need for individual `opens` lines
}
```

### `uses` and `provides ... with` -- Services (Brief Mention)

--> JPMS also formalizes the **`ServiceLoader`** pattern (which predates modules) with two directives: `uses <service-interface>;` declares that a module CONSUMES implementations of a service located at runtime, and `provides <service-interface> with <implementation>;` declares that a module SUPPLIES an implementation, discoverable by any module that `uses` that interface -- without either side needing a compile-time dependency on the other's concrete implementation class.

```java
module com.example.orders {
    uses com.example.spi.PaymentProcessor;                                // consumes some implementation, doesn't care which
}

module com.example.stripe.payments {
    provides com.example.spi.PaymentProcessor with com.example.stripe.payments.StripePaymentProcessor;
}
```

--> This is a genuinely useful plugin-style mechanism but is a comparatively advanced/niche corner of JPMS; most everyday module descriptors only ever use `requires`, `exports`, and occasionally `opens`.

# Strong Encapsulation -- The Headline Benefit

--> Before JPMS, `public` meant "accessible to literally anything on the classpath" -- there was no intermediate notion of "public within my library, but not to external consumers." Library authors resorted to conventions like naming a package `...internal` or `...impl` and hoping people wouldn't import from it, or relying entirely on documentation ("do not use this class directly").
--> JPMS makes that boundary REAL and ENFORCED, both at compile time (a non-exported package simply cannot be imported/referenced by another module -- compile error) and, crucially, at RUNTIME (reflection into a non-`open`ed package throws `InaccessibleObjectException`, even via `setAccessible(true)`, unless the caller module was granted access). This closed a long-standing loophole where reflection could always bypass `private`/package-private access regardless of documented intent.

```java
// Attempting reflection into a package that is neither exported nor opened:
import java.lang.reflect.Field;

public class ReflectionAttempt {
    public static void main(String[] args) throws Exception {
        Class<?> clazz = Class.forName("com.example.orders.internal.Helper");
        Field field = clazz.getDeclaredField("secretValue");
        field.setAccessible(true);   // throws InaccessibleObjectException if internal package isn't `opens`ed
        // java.lang.reflect.InaccessibleObjectException: Unable to make field ... accessible:
        // module com.example.orders does not "opens com.example.orders.internal" to module <caller>
    }
}
```

--> This is a genuine security and API-stability win for library authors: internal implementation details can now be changed freely between versions without breaking consumers who (deliberately or accidentally) depended on them, because those consumers were never able to reach them in the first place.

# Common Gotchas

--> **`module-info.java` must be at the SOURCE ROOT**, not inside any package directory -- putting it in the wrong place produces confusing compiler errors about it not being recognized as a module descriptor.
--> **Forgetting `exports` makes even `public` classes invisible** -- this is a constant early-JPMS surprise for developers used to "public means public"; JPMS adds a module-level gate in front of that visibility.
--> **`opens` is easy to forget for reflection-heavy frameworks** -- an application that runs fine on the classpath can fail with `InaccessibleObjectException` the moment it's run on the module path with JPA/Hibernate/Jackson/a DI container, because those frameworks' reflective access into entity/DTO packages was never explicitly granted.
--> **Confusing `exports` with `opens`** -- `exports` alone does NOT permit deep reflection; a package can be fully exported (compile-time public API) and still throw `InaccessibleObjectException` if a framework tries `setAccessible(true)` on a private field within it, unless it's also `opens`ed.
--> **Split packages** -- JPMS forbids the SAME package name from being provided by two different modules on the module path simultaneously; this breaks a (fairly common, pre-JPMS) pattern of one logical package spread across multiple JARs, and is a common migration blocker (see the next Theory file).
--> **Module name vs package name confusion** -- a module name doesn't have to match its root package name, but mismatched naming makes code much harder to reason about; convention strongly favors keeping them aligned.

# Best Practices Summary

--> Name modules using reverse-DNS convention, matching (or closely resembling) the module's root package name.
--> Keep `module-info.java` exports minimal and deliberate -- treat it as your library's real, enforced public API surface, not an afterthought.
--> Use qualified exports (`exports ... to ...`) for internal-but-shared packages (e.g., test modules) rather than exporting broadly just for convenience.
--> Use `opens` (or targeted qualified `opens`) specifically for packages that reflection-heavy frameworks need to reach into -- don't default to `open module` for libraries; it's more defensible for application-level modules with heavy framework reliance.
--> Reach for `requires transitive` when your module's own exported API exposes types from a dependency, so consumers aren't forced to redundantly declare that dependency themselves.
--> Treat `requires`/`exports`/`opens` mismatches (missing modules, inaccessible packages) as CONFIGURATION errors to fix at the descriptor level -- not something to work around by loosening encapsulation reflexively.
