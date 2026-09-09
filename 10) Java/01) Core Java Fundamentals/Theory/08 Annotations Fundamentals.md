# Why This Matters

--> Annotations are metadata attached to Java code -- classes, methods, fields, parameters, even other annotations -- that carry NO direct effect on program logic by themselves. Their entire meaning comes from something ELSE reading them: the compiler, an annotation processor, or reflection at runtime. `@Override` doesn't make a method override anything; it tells the COMPILER to verify that it does.
--> This file covers the built-in annotations every Java developer uses daily, the syntax rules for using annotations, and the two axes that define an annotation's lifecycle: **retention** (how long it survives) and **target** (where it can legally appear). Files 02-05 build on this foundation to cover writing custom annotations, reading them via reflection, compile-time processing, and reflection in depth. Regex is a separate, unrelated topic covered in File 06 -- it shares this chapter only because both are commonly bundled under "language features that inspect/transform code or text."

# What Problem Do Annotations Solve?

--> Before annotations (pre-Java 5), metadata was expressed through side-channel mechanisms: naming conventions (`testXxx` methods in JUnit 3), marker interfaces (`Serializable`, `Cloneable` -- interfaces with no methods, existing only to be checked via `instanceof`), or external XML config files (early Spring, Hibernate mapping files).
--> Annotations let metadata live directly next to the code it describes, be type-checked by the compiler, be queried via reflection, and be processed at compile time to generate code -- replacing brittle string-based conventions and verbose external XML with a structured, in-code alternative.

```java
// Old way: marker interface, checked at runtime via instanceof
public class MyList implements Serializable { }

// Annotation way: structured metadata, can carry data, machine-checkable
@Entity
@Table(name = "users")
public class User { }
```

# Annotation Syntax Basics

```java
@Override
public String toString() { return "..."; }

@SuppressWarnings("unchecked")
public void cast() { List list = (List) new ArrayList<String>(); }

@Deprecated(since = "9", forRemoval = true)
public void legacyMethod() { }
```

--> Syntax rules:

```text
@AnnotationName                        -- marker form, no elements
@AnnotationName(value)                 -- single-element shorthand, ONLY works if the sole element is named "value"
@AnnotationName(key1 = val1, key2 = v2) -- full form, explicit element names required when there's more than one
```

--> Annotations can be stacked (multiple annotations on one target), and since Java 8 can also apply to type USES, not just declarations (`@NonNull String name` -- a "type annotation," discussed further in Target Types below).

# The Big Four Built-In Annotations

## `@Override`

```java
class Animal {
    void makeSound() { }
}
class Dog extends Animal {
    @Override
    void makeSound() { System.out.println("Woof"); }   // compiler VERIFIES this actually overrides a superclass method
}
```

--> **Purely a compile-time safety net** -- `SOURCE` retention, discarded entirely after compilation. Without it, a typo like `void makeSond()` silently creates an unrelated new method instead of overriding, and the bug surfaces only as confusing runtime behavior (the wrong method gets called via polymorphism). With `@Override`, the same typo is a compile error: "method does not override a method from its superclass."
--> Also valid when implementing an interface method (technically not "overriding" in the classical sense, but Java treats it the same way for `@Override` purposes since Java 6).

## `@Deprecated`

```java
/**
 * @deprecated Use {@link #newMethod()} instead. Scheduled for removal in 3.0.
 */
@Deprecated(since = "2.5", forRemoval = true)
public void oldMethod() { }
```

--> Marks an API element as discouraged for new use. Triggers a compiler warning at every CALL SITE (not just at the declaration). `since` and `forRemoval` (added in Java 9) are optional elements that let tooling distinguish "soft deprecated, keep working" from "will actually be deleted -- migrate now."
--> Pair `@Deprecated` (the annotation, machine-readable, RUNTIME retention so tools can query it) with the `@deprecated` Javadoc tag (human-readable explanation of WHY and what to use instead) -- the annotation alone gives no explanation, so IDEs display both together.

## `@SuppressWarnings`

```java
@SuppressWarnings("unchecked")
public <T> T[] toArray(List<T> list) {
    return (T[]) new Object[list.size()];   // unchecked cast warning silenced
}

@SuppressWarnings({"unchecked", "deprecation"})   // multiple values via array syntax
public void legacy() { }
```

--> Tells the compiler to stop emitting a specific category of warning for the annotated element (and everything nested inside it). Common values: `"unchecked"` (generic type safety can't be verified), `"deprecation"` (calling deprecated code), `"rawtypes"`, `"serial"`, `"unused"`.
--> **Scope it as narrowly as possible** -- suppressing at the method level is far better than at the class level, since a class-level suppression silences the warning for every member, potentially hiding a genuinely dangerous unchecked cast introduced later by someone else. `SOURCE` retention -- it's purely a compiler instruction, gone from the `.class` file.

## `@FunctionalInterface`

```java
@FunctionalInterface
public interface Calculator {
    int calculate(int a, int b);          // exactly one abstract method (a "SAM" -- Single Abstract Method)

    default int calculateTwice(int a, int b) {   // default methods don't count against the SAM limit
        return calculate(calculate(a, b), b);
    }
}
```

--> Documents AND enforces that an interface is intended to be used as a lambda/method-reference target. If a second abstract method is later added by accident, the compiler REJECTS the interface with an error, rather than silently breaking every lambda that implements it. Purely a compile-time guard rail (`SOURCE` retention) -- functional interfaces work as lambda targets with or without this annotation, but omitting it removes the safety net.
--> Built-in examples: `Runnable`, `Comparator<T>`, and the entire `java.util.function` package (`Function`, `Predicate`, `Supplier`, `Consumer`, etc.) are all annotated `@FunctionalInterface`.

# Retention Policies

--> `@Retention` (itself an annotation applied to annotation DEFINITIONS -- see File 02) controls how long an annotation survives past source code. This is the single most important thing to understand about any annotation before using it, because it determines what's even POSSIBLE to do with it.

| Policy | Survives compilation? | Loaded into JVM at runtime? | Queryable via reflection? | Typical use |
|---|---|---|---|---|
| `SOURCE` | No -- discarded by compiler | No | No | `@Override`, `@SuppressWarnings` -- compiler/tooling-only checks |
| `CLASS` | Yes -- in the `.class` file | No (not loaded by classloader into runtime metadata) | No | Bytecode-level tools (ASM, older annotation processors); rarely used directly by app developers -- this is the DEFAULT if `@Retention` is omitted |
| `RUNTIME` | Yes | Yes | **Yes** -- `getAnnotation()` etc. work | `@Test` (JUnit), `@Autowired` (Spring), any annotation you plan to read via reflection |

```text
SOURCE  ---compile--->  (discarded, never in .class)
CLASS   ---compile--->  .class file  ---load--->  (present in bytecode, NOT in runtime reflective metadata)
RUNTIME ---compile--->  .class file  ---load--->  JVM runtime metadata  ---reflection--->  queryable
```

--> **This is the #1 gotcha for beginners writing custom annotations**: forgetting `@Retention(RetentionPolicy.RUNTIME)` means `getAnnotation()` silently returns `null` at runtime -- no error, no warning, just a `null` that looks like "the annotation isn't there" when really it just wasn't KEPT around to be found. Covered in depth in File 02 and File 03.

# Target Types

--> `@Target` restricts WHERE an annotation is legally allowed to be applied -- attempting to use it elsewhere is a compile error, not a runtime issue.

| `ElementType` value | Applies to |
|---|---|
| `TYPE` | class, interface, enum, annotation, record |
| `FIELD` | fields (including enum constants) |
| `METHOD` | methods |
| `PARAMETER` | method/constructor parameters |
| `CONSTRUCTOR` | constructors |
| `LOCAL_VARIABLE` | local variables (rarely useful -- `SOURCE` retention only, since locals don't exist in reflective metadata) |
| `ANNOTATION_TYPE` | other annotations (meta-annotations, e.g. `@Retention` targets `ANNOTATION_TYPE`) |
| `PACKAGE` | `package-info.java` declarations |
| `TYPE_PARAMETER` | generic type parameters, e.g. `class Box<@Positive T>` (Java 8+) |
| `TYPE_USE` | any type usage/reference, e.g. `List<@NonNull String>`, casts, `new` expressions (Java 8+) |
| `MODULE` | `module-info.java` declarations (Java 9+) |

```java
@Target({ElementType.METHOD, ElementType.FIELD})   // legal on methods and fields ONLY
public @interface Auditable { }

@Auditable
class Foo {   // compile ERROR: TYPE not in the allowed target list
}
```

--> `TYPE_USE` (Java 8+) enabled a whole new category of tools -- "type annotations" like Checker Framework's `@NonNull`, `@Nullable` -- that annotate a type USE (a variable's declared type, a generic parameter, a cast) rather than just a declaration, allowing static analysis to catch null-safety and other type-level issues that ordinary declaration-only annotations couldn't express.
--> Omitting `@Target` entirely means the annotation is legal EVERYWHERE annotations can appear -- usually too permissive for a well-designed custom annotation; always specify the narrowest set that makes sense (see File 02).

# Gotchas and Best Practices

--> **`@SuppressWarnings` string values are NOT compiler-checked** -- `@SuppressWarnings("unchcked")` (typo) compiles fine and silences NOTHING, silently leaving the original warning active. There's no compile-time validation of these strings; double-check spelling.
--> **`RetentionPolicy.CLASS` is a common point of confusion** -- it sounds like it should mean "available in my class," but it actually means "present in the `.class` FILE but not loaded into runtime reflective metadata" -- functionally almost invisible to application code. If you can query it with reflection, you need `RUNTIME`, not `CLASS`.
--> **Annotations are not inherited by default** -- a subclass does NOT automatically carry a superclass's annotations unless the annotation itself is meta-annotated `@Inherited` (and even then, only for CLASS-level annotations, never fields/methods) -- see File 02.
--> **`@Deprecated` doesn't stop code from compiling or running** -- it's a warning, not an error; code can call deprecated APIs indefinitely (until `forRemoval = true` actually ships and the API is deleted in some future version). Don't rely on it as an enforcement mechanism.
--> **Always scope `@SuppressWarnings` as tightly as possible** -- prefer suppressing on the single statement/variable/method responsible, not the enclosing class, so unrelated future warnings in the same class aren't silently swallowed too.
