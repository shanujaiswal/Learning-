# Why This Matters

--> Files 01-03 covered annotations read at RUNTIME via reflection. This file covers a completely different consumer of annotations: the JAVA COMPILER ITSELF, during compilation, via the **Annotation Processing** API (`javax.annotation.processing`). This is how Lombok generates getters/setters, how Dagger and MapStruct generate wiring/mapping code, and how tools like Google's AutoValue produce entire classes -- all BEFORE your code ever runs, often with ZERO runtime reflection cost.
--> The key conceptual distinction to hold onto throughout this file:

```text
Runtime reflection (Files 01-03)     -- reads RUNTIME-retention annotations, AFTER compilation, while the
                                         program is executing; can inspect but generally can't add new source
Compile-time processing (this file)  -- reads annotations (any retention) DURING compilation, as a compiler
                                         plugin; can generate entirely NEW .java source files that get compiled
                                         alongside the original code
```

# How javac's Annotation Processing Works

```text
1. javac parses your source files into an abstract syntax tree (AST) -- but does NOT compile to bytecode yet.
2. javac discovers registered annotation processors (via META-INF/services or, more commonly today,
   auto-detected through the @AutoService/@SupportedAnnotationTypes conventions and build tool config).
3. Each processor's process() method is called, given the set of annotations it declared interest in and
   access to the AST via the Element/TypeElement API (a compile-time analog of Class/Method/Field).
4. A processor may generate NEW .java source files (via the Filer API).
5. javac performs ANOTHER round of compilation -- this time INCLUDING the newly generated sources -- and
   repeats steps 2-4 until no processor generates anything new ("processing rounds").
6. Once no new sources are generated, javac compiles everything (original + all generated) to bytecode.
```

--> This round-based design is why generated code can itself be annotated and trigger FURTHER generation -- and also why a buggy processor that keeps generating slightly different output each round can cause infinite-loop-like slow builds.

# The `AbstractProcessor` Skeleton

```java
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import java.util.Set;

@SupportedAnnotationTypes("com.example.GenerateBuilder")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class BuilderProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(GenerateBuilder.class)) {
            if (element.getKind() != ElementKind.CLASS) {
                processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR, "@GenerateBuilder only applies to classes", element);
                continue;
            }
            generateBuilderClassFor((TypeElement) element);
        }
        return true;   // true = "this processor claims these annotations, don't let others process them too"
    }

    private void generateBuilderClassFor(TypeElement type) {
        // Uses processingEnv.getFiler() to write a new .java source file -- shown in detail below.
    }
}
```

--> `AbstractProcessor` (from `javax.annotation.processing`) is the conventional base class -- it provides `processingEnv` (access to the `Filer`, `Messager`, `Elements`, and `Types` utilities) and reduces the interface `Processor` down to just implementing `process()`.

## `@SupportedAnnotationTypes` and `@SupportedSourceVersion`

```java
@SupportedAnnotationTypes({"com.example.GenerateBuilder", "com.example.Immutable"})   // can list several
@SupportedSourceVersion(SourceVersion.RELEASE_17)
```

--> `@SupportedAnnotationTypes` tells the compiler which annotations this processor wants to be notified about (supports wildcards like `"com.example.*"`); `@SupportedSourceVersion` declares the newest Java language version the processor understands, mostly used by javac to emit a warning if run under a NEWER compiler than the processor claims support for. (Since Java 9, these can alternatively be expressed by overriding `getSupportedAnnotationTypes()`/`getSupportedSourceVersion()` methods instead of annotations, which allows computing them dynamically.)

## Registering a Processor

```text
META-INF/services/javax.annotation.processing.Processor
    com.example.BuilderProcessor        <- fully-qualified processor class name, one per line
```

--> javac discovers processors via the standard Java `ServiceLoader` mechanism -- a plain text file listing implementation class names. In modern build tooling this file is usually GENERATED automatically by Google's `@AutoService` annotation (itself a small annotation processor!) rather than hand-written, and Maven/Gradle wire annotation processor JARs onto the compiler's processor path (`annotationProcessorPaths` in Maven, the `annotationProcessor` configuration in Gradle) separately from ordinary compile dependencies.

# The `Element`/`TypeElement` Model -- Compile-Time "Reflection"

--> Annotation processing operates on the AST via `javax.lang.model.element`, a DIFFERENT, PARALLEL API to `java.lang.reflect` -- deliberately so, because at processing time the classes being examined haven't been COMPILED yet, so there's no `Class` object to reflect on; there's only a syntactic representation of the not-yet-compiled code.

| Reflection (runtime, `java.lang.reflect`) | Annotation processing (compile-time, `javax.lang.model`) |
|---|---|
| `Class<?>` | `TypeElement` |
| `Method` | `ExecutableElement` |
| `Field` | `VariableElement` |
| `Constructor<?>` | `ExecutableElement` (kind `CONSTRUCTOR`) |
| Operates on loaded, running bytecode | Operates on parsed-but-not-yet-compiled source |

```java
TypeElement classElement = (TypeElement) element;
for (Element enclosed : classElement.getEnclosedElements()) {
    if (enclosed.getKind() == ElementKind.FIELD) {
        VariableElement field = (VariableElement) enclosed;
        String fieldName = field.getSimpleName().toString();
        TypeMirror fieldType = field.asType();     // TypeMirror -- compile-time analog of Class<?>
    }
}
```

# Generating Code with `Filer`

```java
private void generateBuilderClassFor(TypeElement type) {
    String className = type.getSimpleName() + "Builder";
    String packageName = processingEnv.getElementUtils().getPackageOf(type).toString();

    try (Writer writer = processingEnv.getFiler()
            .createSourceFile(packageName + "." + className)
            .openWriter()) {
        writer.write("package " + packageName + ";\n\n");
        writer.write("public class " + className + " {\n");
        for (Element field : type.getEnclosedElements()) {
            if (field.getKind() == ElementKind.FIELD) {
                String name = field.getSimpleName().toString();
                String type2 = field.asType().toString();
                writer.write("    private " + type2 + " " + name + ";\n");
                writer.write("    public " + className + " " + name + "(" + type2 + " v) { this." + name
                        + " = v; return this; }\n");
            }
        }
        writer.write("}\n");
    } catch (java.io.IOException e) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Failed to write builder: " + e);
    }
}
```

--> `Filer.createSourceFile(name)` returns a `JavaFileObject` you write plain Java source text INTO -- yes, hand-assembled as strings (real-world processors usually use a template/code-generation library like JavaPoet to avoid manual string concatenation, but the underlying mechanism is exactly this). The generated `.java` file is automatically picked up and compiled in the NEXT processing round.

# How Real Frameworks Use This

```text
Lombok       -- technically abuses the API (manipulates the AST/compiler internals directly rather than only
                generating new files) to inject generated getters/setters/constructors/builders into the
                EXISTING class rather than a new file -- this is why Lombok needs special IDE plugins and is
                sometimes flagged as "unsupported" compiler behavior; it goes beyond the intended Filer-only
                contract.
MapStruct    -- reads @Mapper-annotated interfaces describing a source/target DTO pair, and GENERATES a full
                implementation class with plain field-by-field assignment code -- avoiding runtime reflection
                entirely, so mapping is as fast as hand-written code, fully debuggable, and errors (missing
                field mappings) surface as COMPILE warnings/errors rather than runtime surprises.
Dagger       -- reads @Inject/@Component/@Module annotations and generates the entire dependency injection
                wiring as plain Java classes (e.g. DaggerMyComponent) -- in contrast to Spring, which does
                equivalent wiring via RUNTIME reflection every startup, Dagger's generated code has near-zero
                runtime overhead since all the "reflection-like" analysis happened once, at compile time.
AutoValue    -- reads @AutoValue-annotated abstract classes and generates immutable value-class
                implementations (equals/hashCode/toString/constructors) as a real generated subclass.
```

--> The recurring theme: compile-time generation trades a more complex TOOLING setup (an extra processor dependency, generated-sources directories your IDE needs to know about) for eliminating runtime reflection cost and moving error detection from "crashes in production" to "fails to compile" -- a very favorable trade for library authors, which is why this pattern has become dominant in performance-sensitive or startup-time-sensitive frameworks (notably contrasted with Spring's traditionally reflection-heavy runtime approach, and part of why newer efforts like Spring's AOT/GraalVM native-image support lean on similar compile-time generation).

# Gotchas and Best Practices

--> **Generated sources live in a build-tool-specific directory** (`target/generated-sources/annotations` in Maven, `build/generated/sources/annotationProcessor` in Gradle) -- these must be marked as source roots for the IDE to resolve references to generated classes without showing false "cannot find symbol" errors; a very common new-project setup issue.
--> **A processor can only ADD new files, never edit existing ones** (with Lombok's AST-manipulation approach being the deliberate, fragile exception) -- if a use case needs modifying an existing class's bytecode rather than generating a companion class, annotation processing is the wrong tool; bytecode manipulation libraries (ASM, ByteBuddy) or javaagents operate at a different stage entirely.
--> **`process()` returning `true` "claims" the annotation types**, preventing other processors from also seeing elements annotated with them -- return `false` if multiple processors legitimately need to share interest in the same annotation.
--> **Processing rounds can create subtle ordering bugs** -- code that assumes a generated class from another processor already exists in the SAME round will fail; that class only becomes available in the NEXT round once its source has actually been generated and reparsed.
--> **Compile times grow with processor complexity** -- annotation processing runs as part of every build, so processors doing expensive analysis (or that trigger many rounds) directly and measurably slow down `javac`; this is one practical reason some projects choose runtime reflection or minimal processing despite the performance upside of generated code.
--> **This is a genuinely separate skillset from runtime reflection** -- `javax.lang.model.element.Element` and `java.lang.reflect.Member` are unrelated types despite conceptually mirroring each other; code written against one API cannot be reused against the other, and it's easy to reach for the wrong import when starting out.
