# Why This Matters

--> File 01 covered using built-in annotations. This file covers DEFINING your own -- the `@interface` syntax, the meta-annotations that control an annotation's own behavior (`@Retention`, `@Target`, `@Documented`, `@Inherited`), and the three conventional "shapes" a custom annotation can take. This is the foundation every DI framework, ORM, and test framework builds on: `@Autowired`, `@Entity`, `@Test` are all just custom annotations defined exactly this way.

# The `@interface` Syntax

```java
public @interface Author {
    String name();
    String date() default "unknown";
}
```

--> `@interface` (not `interface` -- the `@` is part of the keyword) declares an annotation TYPE. Under the hood, the compiler generates something that behaves like a special interface: every annotation type implicitly extends `java.lang.annotation.Annotation`, and instances are created not with `new` but by writing `@Author(name = "Vanisha")` at a usage site.
--> Elements look like abstract methods (no body, `()` after the name) but are really more like typed, named fields:

```text
type element_name();                    -- required element (must be specified at every usage site)
type element_name() default someValue;  -- optional element (usage sites may omit it, falling back to the default)
```

--> **Allowed element types** are restricted -- primitives, `String`, `Class`, enums, other annotations, or an ARRAY of any of those. No arbitrary objects, no generics, no `List<T>` -- this restriction exists because annotation values must be resolvable as compile-time CONSTANTS, embeddable directly in the `.class` file's constant pool.

```java
public @interface Config {
    String[] tags() default {};                 // array element
    Class<?> handler() default Void.class;       // Class element
    Level level() default Level.INFO;             // enum element
    Nested nested() default @Nested;              // nested annotation element
}
```

# Elements With Default Values

```java
public @interface Retry {
    int attempts() default 3;
    long delayMs() default 1000L;
    String[] retryOn() default {"java.lang.Exception"};
}

@Retry                                     // uses all defaults: attempts=3, delayMs=1000, retryOn={"java.lang.Exception"}
public void callExternalService() { }

@Retry(attempts = 5, delayMs = 500)        // overrides two of three; retryOn still defaults
public void callFlakyService() { }
```

--> An element WITHOUT `default` is mandatory -- every usage site must supply it, or the code fails to compile. Design annotations so the common case needs zero or minimal configuration (sensible defaults), and only unusual cases need to override elements explicitly -- this is the same "convention over configuration" philosophy that made annotation-driven frameworks (Spring Boot, JPA) ergonomic.

## The `value()` Shorthand

```java
public @interface Label {
    String value();          // element named EXACTLY "value"
}

@Label("important")          // shorthand -- allowed ONLY because the sole/primary element is named "value"
public class Task { }

// Equivalent explicit form:
@Label(value = "important")
```

--> If an annotation has a `value()` element AND other elements, the shorthand only omits the name for `value()` -- every other element must still be named explicitly: `@Config(value = "x", timeout = 30)`.

# Meta-Annotations

--> "Meta-annotations" are annotations that annotate OTHER annotation definitions -- they configure how your custom annotation itself behaves. All four live in `java.lang.annotation`.

## `@Retention`

```java
@Retention(RetentionPolicy.RUNTIME)   // required if you intend to read this via reflection (File 03)
public @interface Test { }
```

--> Covered in depth in File 01. The single most consequential meta-annotation decision: **if you ever plan to call `getAnnotation()` at runtime, `RUNTIME` retention is non-negotiable** -- everything else (`SOURCE`, `CLASS`, or omitting `@Retention` entirely, which defaults to `CLASS`) makes the annotation invisible to reflection.

## `@Target`

```java
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Auditable { }
```

--> Restricts legal usage sites (see File 01's table). Omitting `@Target` allows the annotation anywhere -- almost always too broad. A well-designed annotation states its intended targets explicitly, both as documentation and as a compile-time guard rail against misuse.

## `@Documented`

```java
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiStable { }
```

--> Purely cosmetic for Javadoc generation -- when present, usages of `@ApiStable` show up IN the generated Javadoc HTML for the annotated element; without it, the annotation is applied but invisible in Javadoc output. Has zero effect on runtime behavior or reflection. Good practice for any annotation meant to be part of a PUBLIC API contract (like `@Deprecated` itself is `@Documented`).

## `@Inherited`

```java
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Secured { }

@Secured
class Base { }

class Derived extends Base { }   // Derived.class.getAnnotation(Secured.class) returns the inherited @Secured -- non-null!
```

--> Makes a CLASS-level annotation propagate from a superclass to its subclasses when queried via `getAnnotation()`/`isAnnotationPresent()`. Critical caveats:

```text
- Only affects ElementType.TYPE annotations (classes) -- has NO effect on method or field annotations, which are
  NEVER inherited regardless of @Inherited (a subclass overriding a method does not "inherit" the annotations
  on the superclass's version of that method).
- Only affects CLASS inheritance (extends), NOT interface implementation -- annotations on an interface are
  NOT inherited by implementing classes even with @Inherited present.
- Without @Inherited (the default), getAnnotation() on a subclass returns null even though the annotation
  is clearly present "conceptually" via the superclass -- a frequent source of confusion.
```

# Marker, Single-Value, and Full Annotations

--> Custom annotations conventionally take one of three shapes, in increasing order of complexity:

## 1. Marker Annotation -- No Elements

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Test { }        // no elements at all -- presence/absence IS the entire signal

@Test
public void shouldReturnTrue() { }
```

--> Queried purely with `isAnnotationPresent()` -- there's nothing else to extract. JUnit 4/5's `@Test`, `@Before`, `@After` are marker annotations (or close to it).

## 2. Single-Value Annotation -- One `value()` Element

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Table {
    String value();               // the table name, e.g. @Table("users")
}
```

--> Reads almost like a language keyword thanks to the shorthand syntax (`@Table("users")` instead of `@Table(value = "users")`). Best when the annotation conceptually represents ONE piece of data.

## 3. Full (Multi-Element) Annotation

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Scheduled {
    String cron() default "";
    long fixedDelayMs() default -1;
    long initialDelayMs() default 0;
    TimeUnit unit() default TimeUnit.MILLISECONDS;
}

@Scheduled(cron = "0 0 * * * *")
public void hourlyJob() { }

@Scheduled(fixedDelayMs = 5000, initialDelayMs = 1000)
public void pollJob() { }
```

--> Used when several independent, named pieces of configuration are needed -- Spring's `@RequestMapping`, JPA's `@Column`, and similar richly-configurable annotations follow this shape.

# Repeatable Annotations (Java 8+)

```java
import java.lang.annotation.Repeatable;

@Retention(RetentionPolicy.RUNTIME)
public @interface Schedules {                 // the CONTAINER annotation, holds an array
    Schedule[] value();
}

@Retention(RetentionPolicy.RUNTIME)
@Repeatable(Schedules.class)                   // points to its container
public @interface Schedule {
    String day();
}

@Schedule(day = "Monday")
@Schedule(day = "Friday")                      // same annotation applied twice -- only legal because of @Repeatable
public void weeklyReport() { }
```

--> Without `@Repeatable`, applying the same annotation type twice to one element is a compile error. The container (`Schedules` here) is a normal annotation you must also define, holding a `value()` array of the repeated type -- the compiler automatically packs multiple `@Schedule` usages into one implicit `@Schedules` behind the scenes. Reading these back via reflection uses `getAnnotationsByType()`, covered in File 03.

# Gotchas and Best Practices

--> **Forgetting `@Retention(RUNTIME)` is the single most common custom-annotation bug** -- the annotation compiles, applies cleanly, IDEs show it fine, and then `getAnnotation()` mysteriously returns `null` at runtime with no error anywhere. Always set retention deliberately, never rely on the `CLASS` default if reflection is involved.
--> **Annotation elements cannot have `null` as a default** -- `String name() default null;` is a compile error. Model "not set" with an empty string `""`, a sentinel enum value, or by using `Class<?> handler() default Void.class` (a common convention for "no handler class specified").
--> **Arrays as elements need `{}` even for a single value** -- `String[] tags() default {"a"};` requires braces at the definition, but a SINGLE-element array at a usage site can drop them: `@Config(tags = "solo")` is legal shorthand for `@Config(tags = {"solo"})`.
--> **`@Inherited` surprises people constantly** -- remember it applies ONLY to class-level (`TYPE`) annotations and ONLY through `extends`, never through method overrides or interface implementation. If method-level "inheritance" behavior is needed, you must walk the class hierarchy manually in your reflective code (see File 03).
--> **Keep annotation element types simple** -- resist the urge to accept a "richer" type; the compile-time-constant restriction (primitives/String/Class/enum/annotation/arrays thereof) is a hard JLS rule, not a library limitation, so there's no way around it.
--> **Name annotations as adjectives or nouns describing the target, not verbs** -- `@Cacheable`, `@Auditable`, `@Test`, `@Deprecated` read naturally at the usage site; verb-phrased names (`@DoCache`) read awkwardly and buck convention.
