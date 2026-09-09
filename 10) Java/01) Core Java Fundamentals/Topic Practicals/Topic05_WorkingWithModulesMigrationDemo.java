/*
 * Topic05_WorkingWithModulesMigrationDemo.java
 *
 * NOTE ON FILENAME: the public class is named "Topic05_WorkingWithModulesMigrationDemo" (Java
 * identifiers can't start with a digit) while the FILE keeps the "05_..." numeric prefix used
 * throughout this repo for ordering.
 *
 * Compile: javac 05_WorkingWithModulesMigrationDemo.java
 * Run:     java Topic05_WorkingWithModulesMigrationDemo
 *
 * IMPORTANT CAVEAT: this Theory chapter is primarily about TOOLING and the modules ECOSYSTEM
 * (module path vs classpath, automatic modules, jdeps, jlink, --add-opens/--add-exports,
 * incremental migration strategy) -- none of which can be meaningfully exercised inside a
 * single compiled .java file. This file is therefore MOSTLY illustrative/commented content
 * (command-line examples as string literals / comments, not executed), PLUS one genuinely
 * runnable snippet at the end using Class.getModule()/Module.isNamed() to inspect this very
 * program's own module status at runtime.
 *
 * Covers Theory chapter:
 *   01) Core Java Fundamentals/Theory/15 Working with Modules Migration and Practical Considerations.md
 */

public class Topic05_WorkingWithModulesMigrationDemo {

    public static void main(String[] args) {
        showClasspathVsModulePathExamples();
        showAutomaticModuleNamingRules();
        showJdepsCommandExamples();
        showAddOpensAddExportsExamples();
        showJlinkExample();
        showIncrementalMigrationWalkthrough();
        demoNamedVsUnnamedModuleAtRuntime(); // the one genuinely runnable part
        System.out.println("\nAll module-migration demos completed.");
    }

    // -------------------------------------------------------------------
    // 1) Classpath vs module path -- illustrative command-line examples
    // -------------------------------------------------------------------
    private static void showClasspathVsModulePathExamples() {
        printSection("1) Classpath vs module path (illustrative -- shell commands, not executed)");

        System.out.println("# Classpath (pre-JPMS, still fully supported -- no boundary enforcement):");
        System.out.println("java -cp app.jar;lib/commons.jar;lib/gson.jar com.example.Main");
        System.out.println();
        System.out.println("# Module path (JPMS-aware -- requires/exports/opens enforced):");
        System.out.println("java --module-path app.jar;lib/commons.jar;lib/gson.jar \\");
        System.out.println("     --module com.example.app/com.example.Main");
        System.out.println();
        System.out.println("Key difference (Theory file 05): missing dependency on the classpath surfaces as");
        System.out.println("NoClassDefFoundError potentially deep into execution; on the module path it fails");
        System.out.println("FAST at launch with a clear java.lang.module.FindException naming exactly what's");
        System.out.println("missing. Both can be mixed in a single 'java' invocation -- this is what makes");
        System.out.println("INCREMENTAL migration possible.");
    }

    // -------------------------------------------------------------------
    // 2) Automatic module naming rules -- illustrative
    // -------------------------------------------------------------------
    private static void showAutomaticModuleNamingRules() {
        printSection("2) Automatic module naming rules (illustrative)");

        System.out.println("A plain JAR with NO module-info.class, placed on the MODULE PATH (not the");
        System.out.println("classpath), becomes an 'automatic module'. Its name is derived as follows:");
        System.out.println();
        System.out.println("  a) If META-INF/MANIFEST.MF declares 'Automatic-Module-Name: <name>', that name");
        System.out.println("     is used -- STABLE, the recommended approach many libraries adopt ahead of");
        System.out.println("     ever shipping a real module-info.java.");
        System.out.println("  b) Otherwise, the JVM DERIVES a name from the JAR's filename (stripping the");
        System.out.println("     version suffix, converting invalid characters) -- FRAGILE, since renaming");
        System.out.println("     the JAR silently changes the derived name and breaks any 'requires' that");
        System.out.println("     referenced the old one.");
        System.out.println();
        System.out.println("  # Example: JAR filename 'gson-2.10.1.jar', no manifest entry");
        System.out.println("  # Derived automatic module name: gson");
        System.out.println();
        System.out.println("  module com.example.app {");
        System.out.println("      requires gson;               // automatic module, name derived/declared");
        System.out.println("      requires com.example.core;   // a genuine, fully modularized dependency");
        System.out.println("  }");
        System.out.println();
        System.out.println("An automatic module automatically exports EVERY package it contains and reads");
        System.out.println("every other module in the graph -- 'trust everything, expose everything', a");
        System.out.println("deliberately loose bridge between the unnamed module and a properly authored one.");
    }

    // -------------------------------------------------------------------
    // 3) jdeps command examples -- illustrative
    // -------------------------------------------------------------------
    private static void showJdepsCommandExamples() {
        printSection("3) jdeps command examples (illustrative -- shell commands, not executed)");

        System.out.println("# Summarize which modules/packages app.jar actually depends on:");
        System.out.println("jdeps --module-path lib -summary app.jar");
        System.out.println();
        System.out.println("# Attempt to scaffold a module-info.java for a legacy, un-modularized JAR:");
        System.out.println("jdeps --generate-module-info out-dir lib/some-legacy-library.jar");
        System.out.println();
        System.out.println("jdeps is bundled with the JDK and analyzes compiled .class/JAR bytecode --");
        System.out.println("far more reliable than guessing 'requires' clauses by hand.");
    }

    // -------------------------------------------------------------------
    // 4) --add-opens / --add-exports examples -- illustrative
    // -------------------------------------------------------------------
    private static void showAddOpensAddExportsExamples() {
        printSection("4) --add-opens / --add-exports flags (illustrative -- shell commands, not executed)");

        System.out.println("Used when you CANNOT modify a module's descriptor (it's part of the JDK itself,");
        System.out.println("or a third-party JAR) but still need broader reflective/compile-time access than");
        System.out.println("it grants by default:");
        System.out.println();
        System.out.println("java --add-opens java.base/java.lang=ALL-UNNAMED \\");
        System.out.println("     --add-opens java.base/java.util=ALL-UNNAMED \\");
        System.out.println("     -jar legacy-app-that-needs-jdk-internals.jar");
        System.out.println();
        System.out.println("# --add-exports forces a package to be EXPORTED (compile-time visible) to a");
        System.out.println("# target module/ALL-UNNAMED, without granting deep reflection:");
        System.out.println("java --add-exports java.base/sun.security.x509=ALL-UNNAMED -jar app.jar");
        System.out.println();
        System.out.println("These are a pragmatic, temporary bridge -- NOT a permanent substitute for a");
        System.out.println("properly configured module-info.java where you DO control the code. Flags");
        System.out.println("sprinkled across launch scripts/CI config accumulate as technical debt.");
    }

    // -------------------------------------------------------------------
    // 5) jlink example -- illustrative
    // -------------------------------------------------------------------
    private static void showJlinkExample() {
        printSection("5) jlink -- custom trimmed runtime image (illustrative)");

        System.out.println("Once an application AND all its dependencies are proper named (or at least");
        System.out.println("automatic) modules, jlink can assemble a CUSTOM, minimal JRE:");
        System.out.println();
        System.out.println("jlink --module-path $JAVA_HOME/jmods:app-mods \\");
        System.out.println("      --add-modules com.example.app \\");
        System.out.println("      --output custom-runtime \\");
        System.out.println("      --strip-debug --no-header-files --no-man-pages");
        System.out.println();
        System.out.println("Produces a smaller runtime image (skips unused JDK modules like java.desktop or");
        System.out.println("java.sql) -- well suited to container-dense deployments. This is one of the");
        System.out.println("concrete payoffs of full modularization, beyond encapsulation alone.");
    }

    // -------------------------------------------------------------------
    // 6) Incremental migration steps -- commented walkthrough
    // -------------------------------------------------------------------
    private static void showIncrementalMigrationWalkthrough() {
        printSection("6) Migration steps -- commented walkthrough (bottom-up, incremental)");

        System.out.println("Step 1: Start with LEAF dependencies -- modularize (or rely on automatic-module");
        System.out.println("        treatment of) the libraries with the FEWEST dependencies of their own");
        System.out.println("        first, working up toward the application's own top-level code.");
        System.out.println();
        System.out.println("Step 2: Use the unnamed module as a safety net during transition -- keep still-");
        System.out.println("        unmodularized parts of YOUR OWN codebase on the classpath while");
        System.out.println("        modularizing others, via mixed classpath+module-path invocations.");
        System.out.println();
        System.out.println("Step 3: Lean on automatic modules for third-party JARs you don't control -- you");
        System.out.println("        typically can't add a module-info.java to someone else's JAR yourself.");
        System.out.println();
        System.out.println("Step 4: Use jdeps to discover REAL dependencies before hand-writing module-info.java");
        System.out.println("        (see section 3 above) -- far more reliable than guessing.");
        System.out.println();
        System.out.println("Step 5: Only write module-info.java for your OWN application/library modules LAST,");
        System.out.println("        once their dependencies are resolvable as named or automatic modules.");
        System.out.println();
        System.out.println("Common blockers along the way (Theory file 05): SPLIT PACKAGES (same package name");
        System.out.println("supplied by two different modules -- forbidden, common in old api/impl JAR pairs),");
        System.out.println("and REFLECTION-HEAVY FRAMEWORKS (ORMs, DI containers, mocking libraries) throwing");
        System.out.println("InaccessibleObjectException until the right packages are 'opens'ed.");
    }

    // -------------------------------------------------------------------
    // 7) GENUINELY RUNNABLE: inspect this program's own module at runtime.
    // -------------------------------------------------------------------
    private static void demoNamedVsUnnamedModuleAtRuntime() {
        printSection("7) Named vs unnamed module at runtime (genuinely runnable)");

        Class<?> thisClass = Topic05_WorkingWithModulesMigrationDemo.class;
        Module thisModule = thisClass.getModule();

        System.out.println("Class.getModule() for this class: " + thisModule);
        System.out.println("Module.isNamed(): " + thisModule.isNamed());
        System.out.println("Module.getName(): " + thisModule.getName());

        // A JDK platform class, for contrast -- always lives in a NAMED module (java.base),
        // regardless of how THIS demo class itself was launched.
        Module javaBaseModule = String.class.getModule();
        System.out.println();
        System.out.println("For contrast, java.lang.String.class.getModule(): " + javaBaseModule);
        System.out.println("java.lang.String's module isNamed(): " + javaBaseModule.isNamed());
        System.out.println("java.lang.String's module getName(): " + javaBaseModule.getName());

        System.out.println();
        if (thisModule.isNamed()) {
            System.out.println("This demo class is running from the MODULE PATH as part of a named module --");
            System.out.println("JPMS boundary enforcement (requires/exports/opens) fully applies to it.");
        } else {
            System.out.println("This demo class is running from the CLASSPATH (plain javac/java, no");
            System.out.println("module-info.java on the module path), so getModule() returns the UNNAMED");
            System.out.println("module -- isNamed() is false and getName() is null. Per Theory file 05, the");
            System.out.println("unnamed module reads every other module and exports everything it has to");
            System.out.println("every other module, which is exactly why classpath-only code keeps working");
            System.out.println("completely unmodified on every JDK version since Java 9.");
        }
        System.out.println();
        System.out.println("java.base, by contrast, is ALWAYS a named module (isNamed() = true, getName() =");
        System.out.println("\"java.base\") regardless of how the caller class itself was launched -- the JDK");
        System.out.println("itself has been fully modularized since Java 9 (Theory file 04).");
    }

    // -------------------------------------------------------------------
    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }
}
