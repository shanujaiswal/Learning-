/*
 * Topic04_AnnotationProcessingAtCompileTimeDemo.java
 *
 * NOTE ON FILENAME: the public class is named "Topic04_AnnotationProcessingAtCompileTimeDemo"
 * (Java identifiers can't start with a digit) while the FILE keeps the "04_..." numeric
 * prefix used throughout this repo for ordering.
 *
 * Compile: javac 04_AnnotationProcessingAtCompileTimeDemo.java
 * Run:     java Topic04_AnnotationProcessingAtCompileTimeDemo
 *
 * IMPORTANT CONCEPTUAL NOTE (read this first):
 *   A REAL annotation processor (an AbstractProcessor implementation, registered via
 *   META-INF/services and run by javac DURING compilation) requires a separate compilation
 *   round: the processor class must be compiled and put on javac's processor path BEFORE the
 *   sources it processes are compiled. That two-phase build cannot be demonstrated inside one
 *   single-file, self-contained .java demo compiled with a plain `javac ThisFile.java`.
 *
 *   So this file does two things instead:
 *     PART A: clearly-commented ILLUSTRATIVE code showing the AbstractProcessor skeleton,
 *             @SupportedAnnotationTypes/@SupportedSourceVersion, and the Filer-based code
 *             generation pattern -- written exactly as it would look in a real project, but
 *             NOT compiled/run as part of this demo (it is not invoked from main()).
 *     PART B: a genuinely RUNNABLE contrast -- introspecting annotations at RUNTIME via
 *             java.lang.reflect, so the compile-time vs runtime distinction is concrete
 *             rather than just described in prose.
 *
 * Covers Theory chapter:
 *   01) Core Java Fundamentals/Theory/11 Annotation Processing at Compile Time.md
 */

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;

public class Topic04_AnnotationProcessingAtCompileTimeDemo {

    public static void main(String[] args) throws Exception {
        explainCompileTimeVsRuntimeDistinction();
        demoRuntimeIntrospectionContrast();
        System.out.println("\nSee the source of this file for the illustrative (non-executed)");
        System.out.println("AbstractProcessor skeleton in the 'PART A' comment block below main().");
        System.out.println("\nAll annotation-processing demos completed.");
    }

    // -------------------------------------------------------------------
    private static void explainCompileTimeVsRuntimeDistinction() {
        printSection("Compile-Time Processing vs Runtime Reflection");
        System.out.println("Runtime reflection (Files 01-03)     -- reads RUNTIME-retention annotations,");
        System.out.println("                                         AFTER compilation, while the program");
        System.out.println("                                         is executing; can inspect but generally");
        System.out.println("                                         can't add new source.");
        System.out.println("Compile-time processing (this file)  -- reads annotations (any retention) DURING");
        System.out.println("                                         compilation, as a compiler plugin; can");
        System.out.println("                                         generate entirely NEW .java source files");
        System.out.println("                                         that get compiled alongside the original.");
        System.out.println();
        System.out.println("Real examples: Lombok (getters/setters), MapStruct (DTO mappers), Dagger (DI");
        System.out.println("wiring), AutoValue (immutable value classes) -- all generate plain .java source");
        System.out.println("files BEFORE your code ever runs, trading a more complex build setup for");
        System.out.println("eliminating runtime reflection cost entirely.");
    }

    // -------------------------------------------------------------------
    // PART B: a genuinely runnable RUNTIME introspection demo, as the contrast case.
    // -------------------------------------------------------------------

    /** A RUNTIME-retained annotation, marking a field that a (hypothetical) builder generator cares about. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface GenerateBuilderField {
        boolean required() default false;
    }

    static class Person {
        @GenerateBuilderField(required = true)
        private String name;

        @GenerateBuilderField
        private int age;

        // Deliberately unannotated -- should be skipped by the introspection below.
        private String internalNotes;
    }

    private static void demoRuntimeIntrospectionContrast() {
        printSection("Runtime Contrast: Introspecting @GenerateBuilderField via Reflection");

        System.out.println("If @GenerateBuilderField were instead consumed by a real annotation PROCESSOR");
        System.out.println("(PART A below), a BuilderProcessor would read it at COMPILE time via the");
        System.out.println("javax.lang.model.element API and literally WRITE a new PersonBuilder.java file");
        System.out.println("to disk -- no reflection involved at all, and zero runtime cost.");
        System.out.println();
        System.out.println("Here, instead, we do the RUNTIME equivalent: scan Person's fields with");
        System.out.println("java.lang.reflect and print what a builder generator WOULD have produced,");
        System.out.println("entirely at run time, on every execution:");
        System.out.println();

        StringBuilder pseudoBuilder = new StringBuilder("class PersonBuilder {\n");
        for (Field f : Person.class.getDeclaredFields()) {
            GenerateBuilderField ann = f.getAnnotation(GenerateBuilderField.class);
            if (ann != null) {
                System.out.println("  Field '" + f.getName() + "' (" + f.getType().getSimpleName()
                        + ") is marked @GenerateBuilderField(required=" + ann.required() + ")");
                pseudoBuilder.append("    private ").append(f.getType().getSimpleName()).append(' ')
                        .append(f.getName()).append(";\n");
                pseudoBuilder.append("    public PersonBuilder ").append(f.getName()).append('(')
                        .append(f.getType().getSimpleName()).append(" v) { this.").append(f.getName())
                        .append(" = v; return this; }\n");
            } else {
                System.out.println("  Field '" + f.getName() + "' is NOT annotated -- skipped");
            }
        }
        pseudoBuilder.append("}");

        System.out.println("\n--- 'Generated' (really just printed at runtime) builder shape ---");
        System.out.println(pseudoBuilder);
        System.out.println("-------------------------------------------------------------------");
        System.out.println("\nThe key difference: a REAL annotation processor would have produced this AS AN");
        System.out.println("ACTUAL PersonBuilder.class, compiled alongside Person.class, usable with zero");
        System.out.println("reflection at all -- what we just did instead re-derives the same information");
        System.out.println("via reflection, EVERY time this program runs, which is strictly more expensive");
        System.out.println("and only possible because @GenerateBuilderField uses RUNTIME retention.");
    }

    // -------------------------------------------------------------------
    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }
}

/*
 * =====================================================================================
 * PART A -- ILLUSTRATIVE ONLY: a real AbstractProcessor, NOT compiled/run by this demo.
 * =====================================================================================
 *
 * This block is deliberately kept as a comment, not real compiled code, because:
 *   - AbstractProcessor and the javax.annotation.processing / javax.lang.model APIs are
 *     meant to run AS PART OF a javac invocation processing OTHER source files, not as
 *     ordinary application code you invoke directly.
 *   - Demonstrating it "for real" requires two separate compilation steps (compile the
 *     processor, then compile target sources with the processor on javac's processor path,
 *     typically via a META-INF/services/javax.annotation.processing.Processor registration
 *     file) -- infrastructure a single self-contained .java file cannot provide.
 *
 * -------------------------------------------------------------------------------------
 *
 * // The annotation this processor looks for -- retention doesn't matter much for compile-time
 * // processing (unlike reflection, which REQUIRES RUNTIME retention), since the processor reads
 * // it from the not-yet-compiled AST, not from loaded runtime metadata.
 * public @interface GenerateBuilder { }
 *
 * import javax.annotation.processing.*;
 * import javax.lang.model.SourceVersion;
 * import javax.lang.model.element.*;
 * import java.io.Writer;
 * import java.util.Set;
 *
 * @SupportedAnnotationTypes("com.example.GenerateBuilder")
 * @SupportedSourceVersion(SourceVersion.RELEASE_17)
 * public class BuilderProcessor extends AbstractProcessor {
 *
 *     @Override
 *     public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
 *         for (Element element : roundEnv.getElementsAnnotatedWith(GenerateBuilder.class)) {
 *             if (element.getKind() != ElementKind.CLASS) {
 *                 processingEnv.getMessager().printMessage(
 *                     Diagnostic.Kind.ERROR, "@GenerateBuilder only applies to classes", element);
 *                 continue;
 *             }
 *             generateBuilderClassFor((TypeElement) element);
 *         }
 *         // true = "this processor claims @GenerateBuilder, don't let others process it too"
 *         return true;
 *     }
 *
 *     private void generateBuilderClassFor(TypeElement type) {
 *         String className = type.getSimpleName() + "Builder";
 *         String packageName = processingEnv.getElementUtils().getPackageOf(type).toString();
 *
 *         try (Writer writer = processingEnv.getFiler()
 *                 .createSourceFile(packageName + "." + className)
 *                 .openWriter()) {
 *             writer.write("package " + packageName + ";\n\n");
 *             writer.write("public class " + className + " {\n");
 *             for (Element field : type.getEnclosedElements()) {
 *                 if (field.getKind() == ElementKind.FIELD) {
 *                     String name = field.getSimpleName().toString();
 *                     String fieldType = field.asType().toString();
 *                     writer.write("    private " + fieldType + " " + name + ";\n");
 *                     writer.write("    public " + className + " " + name + "(" + fieldType + " v) { this."
 *                             + name + " = v; return this; }\n");
 *                 }
 *             }
 *             writer.write("}\n");
 *         } catch (java.io.IOException e) {
 *             processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Failed to write builder: " + e);
 *         }
 *     }
 * }
 *
 * // Registration file that lets javac's ServiceLoader-based discovery find the processor:
 * //   META-INF/services/javax.annotation.processing.Processor
 * //     com.example.BuilderProcessor
 * //
 * // Then compiled with, e.g.:
 * //   javac -processor com.example.BuilderProcessor Person.java
 * // which would produce a NEW PersonBuilder.java (via the Filer), compiled in the NEXT
 * // processing round, ending up as a real PersonBuilder.class alongside Person.class.
 * =====================================================================================
 */
