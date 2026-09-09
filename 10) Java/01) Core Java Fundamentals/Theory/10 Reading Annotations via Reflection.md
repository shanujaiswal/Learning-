# Why This Matters

--> Files 01-02 covered defining and applying annotations. This file closes the loop: how code actually READS annotations at runtime and acts on them. This is precisely the mechanism behind JUnit discovering `@Test` methods, Spring wiring `@Autowired` fields, and Jackson mapping `@JsonProperty` to JSON keys -- the "magic" of annotation-driven frameworks is, in full, `isAnnotationPresent()` + `getAnnotation()` + reflective invocation, scaled up. This file also builds a small working test-runner to make that concrete.
--> Prerequisite: every annotation involved here must be `@Retention(RetentionPolicy.RUNTIME)` (File 02) -- none of the APIs below can see anything with weaker retention.

# The Core Reflective Annotation API

--> `AnnotatedElement` is the interface implemented by `Class`, `Method`, `Field`, `Constructor`, and `Parameter` -- anything annotations can attach to exposes the same annotation-reading methods, regardless of what kind of program element it is.

```java
public interface AnnotatedElement {
    boolean isAnnotationPresent(Class<? extends Annotation> annotationClass);
    <T extends Annotation> T getAnnotation(Class<T> annotationClass);
    Annotation[] getAnnotations();                 // including inherited (TYPE-level, @Inherited only)
    Annotation[] getDeclaredAnnotations();          // only directly present, ignoring @Inherited
    <T extends Annotation> T[] getAnnotationsByType(Class<T> annotationClass);   // repeatable-aware
}
```

## `isAnnotationPresent` and `getAnnotation`

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface Test { String name() default ""; }

class Calc {
    @Test(name = "addition works")
    public void testAdd() { }

    public void notATest() { }
}

Method m = Calc.class.getDeclaredMethod("testAdd");
if (m.isAnnotationPresent(Test.class)) {
    Test t = m.getAnnotation(Test.class);
    System.out.println(t.name());          // "addition works"
}

Method m2 = Calc.class.getDeclaredMethod("notATest");
System.out.println(m2.getAnnotation(Test.class));   // null -- absent, not an exception
```

--> `isAnnotationPresent` returns a boolean; `getAnnotation` returns the annotation instance (a compiler-generated dynamic PROXY implementing the annotation interface -- see File 05's dynamic proxy discussion, since this is the canonical real-world use of `Proxy`) or `null` if absent. Both are equally valid checks -- `isAnnotationPresent` reads slightly clearer when you don't need the annotation's data, only its presence.

## `getAnnotations()` vs `getDeclaredAnnotations()`

```text
getAnnotations()          -- all RUNTIME-retained annotations present, INCLUDING inherited ones (TYPE + @Inherited)
getDeclaredAnnotations()  -- only annotations written directly on THIS element, ignoring inheritance entirely
```

--> Mirrors the same `getX()` vs `getDeclaredX()` distinction from ordinary reflection (File 04 of the JVM Internals chapter) -- `getAnnotations()` is the "effective" view (what you'd conceptually see), `getDeclaredAnnotations()` is the "exactly what's written here" view. For anything other than `@Inherited` class-level annotations, the two are identical.

## `getAnnotationsByType` -- Repeatable Annotations

```java
Method m = WeeklyJob.class.getDeclaredMethod("run");

// Works whether @Schedule was applied once, multiple times (via the implicit container), or not at all:
Schedule[] schedules = m.getAnnotationsByType(Schedule.class);
for (Schedule s : schedules) {
    System.out.println(s.day());
}
```

--> `getAnnotation(Schedule.class)` would return `null` for a repeated annotation, because internally the compiler wrapped multiple `@Schedule` usages in an implicit `@Schedules` container (File 02) -- `getAnnotation` doesn't unwrap that. `getAnnotationsByType` is specifically designed to handle BOTH the repeated case (unwrapping the container automatically) and the single/absent case (returning a length-1 or length-0 array), making it the safe universal choice for any annotation marked `@Repeatable`, even when you don't know in advance whether it'll be used once or many times.

# Practical Use Case: A Minimal Test-Runner

--> This mirrors, in miniature, what JUnit actually does: scan a class for annotated methods, invoke each reflectively, and report pass/fail. Understanding this fully demystifies "how does JUnit find my `@Test` methods" -- the answer is exactly this, just more elaborate.

```java
import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface MyTest { }

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface MyBeforeEach { }

class CalculatorTests {
    private int state;

    @MyBeforeEach
    void setUp() { state = 0; }

    @MyTest
    void additionWorks() {
        state = 2 + 2;
        if (state != 4) throw new AssertionError("expected 4, got " + state);
    }

    @MyTest
    void divisionByZeroThrows() {
        try {
            int x = 1 / 0;
            throw new AssertionError("expected ArithmeticException");
        } catch (ArithmeticException expected) {
            // pass
        }
    }
}

public class MiniRunner {
    public static void main(String[] args) throws Exception {
        runTests(CalculatorTests.class);
    }

    static void runTests(Class<?> testClass) throws Exception {
        Object instance = testClass.getDeclaredConstructor().newInstance();

        List<Method> beforeEach = new ArrayList<>();
        List<Method> tests = new ArrayList<>();
        for (Method m : testClass.getDeclaredMethods()) {
            if (m.isAnnotationPresent(MyBeforeEach.class)) beforeEach.add(m);
            if (m.isAnnotationPresent(MyTest.class)) tests.add(m);
        }

        int passed = 0, failed = 0;
        for (Method test : tests) {
            try {
                for (Method setup : beforeEach) {
                    setup.setAccessible(true);
                    setup.invoke(instance);
                }
                test.setAccessible(true);
                test.invoke(instance);
                System.out.println("PASS: " + test.getName());
                passed++;
            } catch (InvocationTargetException e) {
                // the REAL exception thrown by the test is wrapped -- must unwrap via getCause()
                System.out.println("FAIL: " + test.getName() + " -- " + e.getCause());
                failed++;
            }
        }
        System.out.printf("%nResults: %d passed, %d failed%n", passed, failed);
    }
}
```

--> Key mechanics this exercises:

```text
1. getDeclaredMethods()          -- enumerate every method regardless of visibility
2. isAnnotationPresent()         -- filter down to only the ones the "framework" cares about
3. newInstance() via constructor -- create a fresh test instance (JUnit does this PER test method, for isolation)
4. setAccessible(true)           -- allow invoking package-private/private test methods
5. invoke()                      -- actually run the method
6. InvocationTargetException     -- ALWAYS wraps whatever the invoked method threw; unwrap with getCause()
   to see the real assertion failure/exception, not a generic reflection error
```

# Reading Element Values and Edge Cases

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface Route {
    String path();
    String[] methods() default {"GET"};
    Class<? extends Filter> filter() default NoOpFilter.class;
}

@Route(path = "/users", methods = {"GET", "POST"})
public void handleUsers() { }

Method m = Controller.class.getDeclaredMethod("handleUsers");
Route r = m.getAnnotation(Route.class);
System.out.println(r.path());                 // "/users"
System.out.println(Arrays.toString(r.methods())); // [GET, POST]
Class<? extends Filter> f = r.filter();         // NoOpFilter.class (the default, since none was specified)
```

--> Annotation instances returned by reflection behave like read-only value objects -- there are no setters, `equals()`/`hashCode()` are defined by the JLS to compare all element values, and `toString()` produces a readable `@Route(path=/users, methods=[GET, POST], filter=class NoOpFilter)`-style rendering.
--> Requesting a `Class<?>`-typed element loads that class if not already loaded -- a subtle side effect: annotation processing can trigger class loading just by reading an annotation's `Class` element.

# Scanning an Entire Class Hierarchy or Package

```java
// Include inherited methods too (getMethods() -- public only, but walks up the hierarchy):
for (Method m : SomeClass.class.getMethods()) {
    if (m.isAnnotationPresent(MyTest.class)) { /* ... */ }
}

// To also catch non-public annotated methods declared on superclasses, walk manually:
Class<?> current = SomeClass.class;
while (current != null && current != Object.class) {
    for (Method m : current.getDeclaredMethods()) {
        if (m.isAnnotationPresent(MyTest.class)) { /* ... */ }
    }
    current = current.getSuperclass();
}
```

--> Real frameworks (Spring component scanning, JUnit test discovery) also need to find ANNOTATED CLASSES across a whole classpath, not just members of one known class -- that requires classpath scanning (walking JAR/directory contents and loading each `.class` file to check), a heavier mechanism usually delegated to a library (`ClassGraph`, Spring's `ClassPathScanningCandidateComponentProvider`) rather than hand-rolled, since correctly handling JARs, modules, and nested classpaths is substantial work.

# Gotchas and Best Practices

--> **`getAnnotation()` returning `null` almost always means retention, not absence** -- before assuming "the annotation isn't there," double check the annotation's `@Retention` is `RUNTIME` (File 02's #1 gotcha resurfaces constantly here).
--> **`InvocationTargetException` swallows the real stack trace at a glance** -- always call `.getCause()` when a reflective `invoke()` fails; logging the `InvocationTargetException` itself just says "something in the invoked method threw," not what.
--> **`getAnnotationsByType` vs `getAnnotation` for repeatable annotations** -- using plain `getAnnotation()` on a `@Repeatable` annotation type that was actually applied more than once returns `null` (it only matches the implicit container type), a subtle bug; always use `getAnnotationsByType()` when an annotation is declared `@Repeatable`, even if you expect just one usage in practice.
--> **`getMethods()` (public + inherited) vs `getDeclaredMethods()` (all visibility, this class only)** -- framework-style annotation scanning usually needs a DELIBERATE choice between these (or a manual hierarchy walk); picking the wrong one silently misses annotated private methods or annotated inherited methods.
--> **Cache reflective lookups in hot paths** -- repeatedly calling `getDeclaredMethods()` and re-scanning for annotations on every request is wasteful; frameworks typically scan ONCE (at startup or class-load time) and cache the resulting `Method`/`Field` + annotation-data mapping, then only pay the (much cheaper) `invoke()` cost per call thereafter -- see File 05's performance discussion.
--> **Annotation proxies are not your custom class** -- `getAnnotation()` returns a JDK-generated dynamic proxy implementing your annotation interface, not literally an instance of some concrete class you wrote -- you can't cast it to anything but the annotation interface itself, and `instanceof SomeUnrelatedType` will always be false.
