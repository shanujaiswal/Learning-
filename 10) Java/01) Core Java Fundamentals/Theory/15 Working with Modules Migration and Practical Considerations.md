# Working with Modules -- Migration and Practical Considerations

--> The previous Theory file covered JPMS syntax and concepts in isolation. This file covers the messier, more practical reality: how EXISTING, pre-JPMS ("classpath") code interacts with the module system, how to migrate a real application toward modules incrementally, and why -- despite being available since 2017 -- full JPMS adoption remains optional and comparatively rare across the Java ecosystem even years later.

# The Module Path vs the Classpath

--> Java 9+ supports TWO separate ways of locating code at compile and run time, and they behave very differently: the **classpath** (the original, pre-JPMS mechanism -- flat, unordered, no module boundaries) and the **module path** (JPMS-aware -- resolves named modules, checks `requires`/`exports` and enforces encapsulation).

```
# Classpath (pre-JPMS, still fully supported):
java -cp app.jar;lib/commons.jar;lib/gson.jar com.example.Main

# Module path (JPMS):
java --module-path app.jar;lib/commons.jar;lib/gson.jar --module com.example.app/com.example.Main
```

| | Classpath (`-cp` / `-classpath`) | Module path (`--module-path` / `-p`) |
|---|---|---|
| Boundary enforcement | None -- everything sees everything | Full JPMS rules -- `requires`/`exports`/`opens` enforced |
| Duplicate class names across JARs | Silently resolved by JAR order (first found wins) | `module-info.class` conflicts / split packages cause hard errors at startup |
| Missing dependency | `NoClassDefFoundError`, potentially deep into execution | `FindException` at launch -- fails fast, names the missing module |
| Code without `module-info.java` | Works exactly as before -- default behavior | Placed in the **unnamed module** (see below) if put on the module path anyway |
| JARs without `module-info.class` on the module path | N/A | Become **automatic modules** (see below) |

--> **You can mix both** -- a single `java` invocation can specify both `-cp` and `--module-path`, and a build can have some artifacts fully modularized while others remain plain classpath JARs. This mixed-mode operation is precisely what makes INCREMENTAL migration possible rather than requiring an all-or-nothing rewrite.

# The Unnamed Module

--> Any type loaded from the CLASSPATH (not the module path) -- whether via `-cp` explicitly, or simply because no `module-info.java` exists in a traditional non-modular project -- lives in what JPMS calls the **unnamed module**.
--> **The unnamed module reads every other module, and exports everything it has to every other module** -- it's effectively the "anything goes" escape hatch that keeps classpath-only code and tools working unmodified under JPMS. This is precisely why a plain, non-modularized Java project compiles and runs exactly as it always did on Java 9+: with no `module-info.java` present, the ENTIRE application (and all its classpath dependencies) simply lives together in the unnamed module, and JPMS's boundary enforcement essentially doesn't engage.
--> **The catch**: named modules CANNOT `requires` the unnamed module (there's nothing stable to name -- its contents are whatever happens to be on the classpath at launch, which isn't a reliable dependency declaration). So a fully modularized module generally cannot depend on classpath-only code; the dependency has to flow the other direction, or the dependency needs to become at least an automatic module (below).

# Automatic Modules

--> When a plain JAR (with NO `module-info.class`) is placed on the MODULE PATH (not the classpath) alongside genuinely modularized code, the JVM treats it as an **automatic module** -- a pragmatic bridge that lets un-modularized JARs participate in the module graph without being rewritten.
--> **How an automatic module's name is derived**: if the JAR's manifest (`META-INF/MANIFEST.MF`) specifies an `Automatic-Module-Name` entry, that name is used (this is the RECOMMENDED, stable approach -- many popular libraries added this manifest entry specifically to give consumers a predictable module name ahead of ever shipping a real `module-info.java`). Otherwise, the JVM DERIVES a name from the JAR's filename (stripping the version suffix and converting invalid characters), which is fragile -- renaming the JAR file changes the derived module name, silently breaking any `requires` that referenced the old derived name.

```
# JAR filename: gson-2.10.1.jar, no Automatic-Module-Name manifest entry
# Derived automatic module name: gson
# (Fragile: this derivation depends entirely on the exact filename at build time)
```

--> **What an automatic module grants**: unlike the unnamed module, an automatic module CAN be `requires`d by name from a real named module. It automatically `exports` every package it contains (there's no `module-info.java` to say otherwise) and automatically `requires` every OTHER module in the graph (so it can freely use anything, exactly like classpath code always could) -- it's essentially "trust everything, expose everything," a deliberately loose middle ground between the unnamed module and a properly authored one.

```java
// A real, named module CAN depend on an automatic module by its derived/manifest-declared name:
module com.example.app {
    requires gson;   // "gson" here is an automatic module (gson-2.10.1.jar on the module path)
    requires com.example.core;   // a genuine, fully modularized dependency
}
```

| | Unnamed module | Automatic module | Named (explicit) module |
|---|---|---|---|
| Has `module-info.class`? | No | No | Yes |
| Where it comes from | Classpath | Plain JAR placed on the MODULE path | JAR built with a real `module-info.java` |
| Can be `requires`d by name | No | Yes | Yes |
| Exports | Everything, to everyone | Everything, to everyone | Only what's explicitly `exports`ed |
| Reads (can access) other modules | Everything | Everything | Only what's explicitly `requires`d |

# Migrating a Classpath Application to Modules

--> Oracle's own migration guidance (and the general community consensus) favors a **bottom-up, incremental** approach rather than modularizing an entire application and its dependency tree in one pass:

1. **Start with leaf dependencies** -- modularize (or rely on automatic-module treatment of) the libraries with the FEWEST dependencies of their own first, working up toward the application's own top-level code.
2. **Use the "unnamed module" as a safety net during transition** -- keep still-unmodularized parts of your OWN codebase on the classpath while modularizing others, using mixed classpath+module-path invocations.
3. **Lean on automatic modules for third-party JARs you don't control** -- you often can't add a `module-info.java` to someone else's JAR yourself (though tools like `jdeps --generate-module-info` can attempt to scaffold one), so automatic-module treatment is frequently the practical endpoint for external dependencies that haven't modularized themselves yet.
4. **Use `jdeps` to discover real dependencies before writing `module-info.java` by hand** -- `jdeps` (bundled with the JDK) analyzes compiled `.class`/JAR bytecode and reports exactly which packages/modules a piece of code actually depends on, which is far more reliable than guessing.

```
jdeps --module-path lib -summary app.jar
jdeps --generate-module-info out-dir lib/some-legacy-library.jar
```

5. **Only write `module-info.java` for your OWN application/library modules last**, once their dependencies are resolvable as named or automatic modules.

--> **The `jlink` payoff for fully modularized applications** -- once an application AND all its dependencies are proper named (or at least automatic) modules, `jlink` can assemble a CUSTOM, trimmed JRE containing only the JDK modules actually needed (skipping unused ones like `java.desktop` or `java.sql` if the app never uses them), producing a smaller runtime image well suited to container deployments. This is one of the concrete, tangible payoffs of full modularization, beyond the encapsulation benefits themselves.

```
jlink --module-path $JAVA_HOME/jmods:app-mods \
      --add-modules com.example.app \
      --output custom-runtime \
      --strip-debug --no-header-files --no-man-pages
```

# Common JPMS Gotchas

--> **Split packages** -- JPMS FORBIDS the same package name from being supplied by two different modules on the module path at once (e.g., two different JARs both contributing classes under `com.example.utils`), which was a legal (if messy) pattern under the plain classpath. This single rule breaks a surprising number of real-world dependency trees, especially older multi-JAR libraries that were split across an "api" JAR and an "impl" JAR sharing package names, and is one of the most commonly cited migration blockers.
--> **Automatic module names derived from filenames are unstable** -- if a JAR lacks an `Automatic-Module-Name` manifest entry, its derived module name depends on its EXACT filename at the time it's placed on the module path; a routine version bump or a build tool renaming the artifact can silently change the derived name and break `requires` declarations elsewhere. Always prefer libraries that declare `Automatic-Module-Name` explicitly, or pin to it deliberately.
--> **Reflection-heavy frameworks breaking on the module path** -- code that ran fine on the classpath (where reflection had no module boundaries to respect) can throw `InaccessibleObjectException` the moment it runs on the module path against a properly modularized target, because the needed package was never `opens`ed. This affects a huge swath of the ecosystem: ORMs, DI containers, serialization libraries, mocking frameworks -- and is a major reason full modularization has been slow, since it can require coordinated changes across many independently-maintained libraries.
--> **`--add-opens`/`--add-exports`/`--add-reads` command-line escape hatches** -- for cases where you cannot modify a module's descriptor (e.g., it's part of the JDK itself, or a third-party JAR) but still need broader access than it grants, the JVM accepts launch-time flags to force it open. This is a very common (if inelegant) real-world workaround -- major frameworks and even some JDK-internal tooling have shipped with documented `--add-opens` requirements over the years (a well-known example: various tools needing `--add-opens java.base/java.lang=ALL-UNNAMED` to keep working after internal JDK APIs became strongly encapsulated in Java 9+).

```
java --add-opens java.base/java.lang=ALL-UNNAMED \
     --add-opens java.base/java.util=ALL-UNNAMED \
     -jar legacy-app-that-needs-jdk-internals.jar
```

--> **Strong encapsulation of JDK internals broke old code that relied on `com.sun.*`/`sun.*` internal classes** -- pre-Java-9 code that reached into JDK-internal packages (something that was always technically unsupported, but widely done anyway, especially by performance libraries and some frameworks) could no longer do so by default once those internal packages stopped being exported/opened. This was a genuinely significant, ecosystem-wide migration pain point, and remains one of the most commonly cited reasons some organizations delayed JDK upgrades for years.
--> **Multi-release JARs and modules interacting awkwardly** -- JARs built to support multiple Java versions (`META-INF/versions/`) alongside a `module-info.class` have had various edge-case bugs and tooling friction across different build tool versions; worth extra testing if your build uses both features together.

# Why JPMS Adoption Has Been Slow / Remains Largely Optional

--> Despite being available since 2017, a large fraction of real-world Java projects -- including many actively maintained ones -- still run entirely on the classpath with no `module-info.java` at all, for several concrete reasons:

--> **JPMS is fully OPTIONAL by design** -- Oracle deliberately preserved complete classpath compatibility specifically so that Java 9+ wouldn't break the existing ecosystem outright. There is essentially never a hard requirement to modularize; classpath-based applications continue to compile and run on every subsequent JDK version without any changes required.
--> **The migration cost is real and often not clearly worth it for typical applications** -- the payoffs (strong encapsulation, `jlink` custom runtimes, reliable configuration) matter most for library authors with a wide audience, and for deployment scenarios where runtime image size genuinely matters (e.g., container-dense environments). A typical internal business application gains comparatively little from modularizing itself, while still paying the FULL migration cost (split-package conflicts, reflection breakage, dependency modularization status outside your control).
--> **Ecosystem-wide coordination problem** -- a project can only cleanly and fully modularize once ALL of its transitive dependencies are at least reasonably JPMS-friendly (automatic modules with stable names, ideally real modules). Given how deep dependency trees get in typical Java projects (especially with frameworks like Spring pulling in dozens of transitive JARs), waiting on the entire ecosystem to catch up has been a slow, long tail -- some widely used libraries took years to add even an `Automatic-Module-Name` manifest entry, let alone a full `module-info.java`.
--> **Framework-heavy applications lean hard on reflection**, and JPMS's strong encapsulation is fundamentally in tension with that style until every relevant package is correctly `opens`ed -- which shifts real, ongoing maintenance burden onto application authors for a benefit (encapsulation) that mostly matters for code THEY don't control needing to reach INTO their code, not the reverse.
--> **Popular build tools and frameworks made "just use the classpath" the default path of least resistance** -- Spring Boot, for instance, historically packaged applications as "fat/uber JARs" designed around classpath semantics, and full first-class JPMS support across the framework ecosystem has been gradual and, in many cases, still not the default configuration developers reach for.
--> **Net result** -- JPMS is genuinely valuable and fully supported, and IS the right tool for JDK maintainers (the JDK itself is fully modularized) and for library/framework authors who want real encapsulation guarantees and slim custom runtimes. But for a large share of ordinary application code, the classpath (with the unnamed module quietly doing its job under the hood) remains the practical default, and modularizing is a deliberate, scoped decision rather than an assumed step in every project's lifecycle.

# Common Gotchas (Practical/Tooling)

--> **IDE and build tool support varies** -- Maven/Gradle both support JPMS, but plugin ecosystems, multi-module builds, and test frameworks (needing to reflectively access test classes) can require extra configuration (e.g., a separate `module-info.java` under `src/test/java`, or keeping tests on the classpath while main code is modularized).
--> **Testing modularized code** -- many teams pragmatically keep TEST code running on the plain classpath (against the compiled module's JAR) rather than fully modularizing tests too, specifically to sidestep the reflection/`opens` friction that heavily reflective test frameworks (JUnit, Mockito) can otherwise hit.
--> **`--add-opens` flags sprinkled across launch scripts/CI config accumulate as technical debt** -- convenient in the moment, but they represent unresolved encapsulation gaps; worth periodically revisiting as dependencies eventually ship proper module descriptors.
--> **Confusing "runs on Java 9+" with "is modularized"** -- virtually all classpath-based code runs fine on modern JDKs (unnamed module handles it transparently); running on a modern JDK version says nothing about whether a project has actually adopted JPMS.

# Best Practices Summary

--> Prefer the module path and real `module-info.java` descriptors for NEW libraries/applications where encapsulation and/or `jlink` slim runtimes provide clear value; don't treat modularization as mandatory for every project.
--> Migrate incrementally, bottom-up, using `jdeps` to discover actual dependencies rather than guessing `requires` clauses by hand.
--> Prefer dependencies that declare `Automatic-Module-Name` explicitly over relying on filename-derived automatic module names.
--> Expect and plan for split-package conflicts and reflection/`opens` friction as the two most common real-world migration blockers.
--> Use `--add-opens`/`--add-exports` as a pragmatic, temporary bridge for dependencies you don't control -- not as a permanent substitute for a properly configured `module-info.java` where you DO control the code.
--> Recognize that staying on the classpath (unnamed module) is a legitimate, fully supported, and very common choice -- JPMS adoption should be a deliberate decision driven by a concrete need (strong encapsulation for a widely consumed library, or a custom minimal runtime), not a default assumption for every project.
