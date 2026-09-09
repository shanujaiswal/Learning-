# Why This Matters Beyond File 01

--> File 01 introduced the class loader hierarchy at a high level as part of the overall JVM architecture. This file goes deeper into TWO closely related, very practical topics: how to actually WRITE and reason about custom class loaders, and the **Reflection API** -- the mechanism that lets Java code inspect and manipulate classes, methods, and fields at RUNTIME, even ones it didn't know about at compile time.
--> These two topics are linked because reflection is frequently used TOGETHER with custom class loading -- frameworks (Spring, Hibernate, plugin systems, testing frameworks) routinely load classes dynamically and then use reflection to instantiate and wire them together without any compile-time reference to their concrete types.

# Class Loader Hierarchy -- Recap and Detail

```text
Bootstrap ClassLoader     (native code, loads java.base module classes -- appears as `null` in Java code)
        |
        v
Platform ClassLoader      (loads other JDK-supplied modules)
        |
        v
Application ClassLoader   (loads your application/classpath classes -- getSystemClassLoader())
        |
        v
Custom ClassLoader(s)     (your own subclasses of ClassLoader)
```

```java
System.out.println(String.class.getClassLoader());       // null -- loaded by the Bootstrap loader
System.out.println(MyClass.class.getClassLoader());       // typically the Application (system) class loader
System.out.println(ClassLoader.getSystemClassLoader());   // the Application class loader itself
```

## Parent-First Delegation, Precisely

--> When `loadClass(name)` is called on a class loader, the default behavior (`ClassLoader.loadClass`) is:

```text
1. Check if this class is already loaded (cached) by THIS loader -- if so, return it.
2. Delegate to the PARENT loader first -- ask it to load the class.
3. Only if the parent can't find it, attempt to load it yourself (findClass).
```

--> This guarantees core classes are loaded exactly once, by the trusted bootstrap loader, and can never be shadowed by an application-defined class of the same fully-qualified name -- an important security/consistency guarantee, sometimes called **class loader isolation**.
--> **Each class is uniquely identified by (fully-qualified name + defining class loader)** -- this has a subtle but important consequence: the SAME `.class` file loaded by two DIFFERENT class loaders produces two DISTINCT `Class` objects that are NOT equal, and an instance from one can't be cast to the other -- this is the root cause of confusing `ClassCastException: MyClass cannot be cast to MyClass` errors in app servers, plugin systems, or when a class is present on multiple classpath locations.

## When and Why to Write a Custom Class Loader

--> **Plugin systems** -- load plugin JARs at runtime, potentially isolating each plugin's classes/dependencies from each other (so Plugin A's Guava version doesn't collide with Plugin B's).
--> **Hot reloading** -- discard and reload a set of classes without restarting the JVM (common in dev-mode tooling) by discarding the whole class loader and creating a fresh one (individual classes can't be "reloaded" in place -- the whole loader that defined them has to be replaced).
--> **Loading classes from non-standard sources** -- database blobs, network locations, encrypted/obfuscated bytecode decrypted at load time.
--> **Application/framework isolation** -- app servers (classic Java EE/Tomcat) give each deployed web application its own class loader so multiple apps with conflicting dependency versions can coexist in one JVM.

## Writing a Custom Class Loader

--> Typically extend `ClassLoader` and override `findClass(String name)` -- NOT `loadClass` directly (unless you specifically want to break parent-first delegation, which is rare and risky) -- so the standard delegation flow still applies, and your logic only runs as the fallback once parents have failed to find it.

```java
public class ByteArrayClassLoader extends ClassLoader {
    private final Map<String, byte[]> classBytes;

    public ByteArrayClassLoader(Map<String, byte[]> classBytes, ClassLoader parent) {
        super(parent);                       // wire up the parent explicitly
        this.classBytes = classBytes;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] bytes = classBytes.get(name);
        if (bytes == null) {
            throw new ClassNotFoundException(name);
        }
        return defineClass(name, bytes, 0, bytes.length);   // turns raw bytecode into a Class object
    }
}
```

--> `defineClass(...)` is the actual JVM-native operation that performs Loading + Linking (Verify/Prepare/Resolve) for the given bytecode, protected because calling it with malicious/malformed bytes has real safety implications -- this is why `findClass` (not a fully public API) is the intended extension point.

# Reflection API

--> Reflection lets code examine and manipulate classes, fields, methods, and constructors at RUNTIME, even for types unknown at compile time -- the foundation of virtually every framework that does dependency injection, ORM, serialization, or annotation processing (Spring, Jackson, JUnit, Hibernate).

## Getting a `Class` Object

```java
Class<?> c1 = String.class;                       // compile-time, no instance needed
Class<?> c2 = someObject.getClass();               // from a runtime instance
Class<?> c3 = Class.forName("java.lang.String");   // by fully-qualified name, may throw ClassNotFoundException
```

## Inspecting Fields, Methods, and Constructors

```java
Class<?> clazz = MyClass.class;

Field[] fields = clazz.getDeclaredFields();          // ALL fields (any visibility), declared directly on this class
Method[] methods = clazz.getDeclaredMethods();        // ALL methods declared directly on this class
Constructor<?>[] ctors = clazz.getDeclaredConstructors();

// getFields()/getMethods() (no "Declared") return only PUBLIC members, but INCLUDE inherited ones --
// getDeclaredX() returns members of any visibility, but ONLY those declared directly on this class
// (not inherited). Mixing these up is a very common source of "why can't reflection see this field" bugs.
```

## Invoking Methods and Accessing Fields Reflectively

```java
Method m = clazz.getDeclaredMethod("computeTotal", int.class);
m.setAccessible(true);                    // required to call private/protected/package-private members
Object result = m.invoke(instanceOrNull, 42);   // instanceOrNull is null for a static method

Field f = clazz.getDeclaredField("balance");
f.setAccessible(true);
Object value = f.get(instance);
f.set(instance, 1000);                    // can even mutate `final` non-static fields in some cases (JVM-dependent, discouraged)
```

--> **`setAccessible(true)` bypasses Java's normal access control checks** (private/protected/package-private) -- extremely powerful, and extremely easy to misuse; frameworks use it legitimately (e.g. to inject dependencies into private fields), but it breaks encapsulation guarantees and can be blocked entirely in modularized environments (Java Platform Module System, `--add-opens` requirements) or under a `SecurityManager` (removed in newer JDKs, but still enforced by module boundaries).

## Creating Instances Reflectively

```java
// Via a specific constructor:
Constructor<MyClass> ctor = MyClass.class.getDeclaredConstructor(String.class);
ctor.setAccessible(true);
MyClass obj = ctor.newInstance("hello");

// Via the no-arg constructor (common in frameworks needing a "blank" instance to populate via reflection):
MyClass obj2 = MyClass.class.getDeclaredConstructor().newInstance();
```

## Reflective Array and Generic Type Inspection

```java
Object arr = Array.newInstance(String.class, 5);   // creates a String[5] without a compile-time array type
Array.set(arr, 0, "hi");

// Generic type info is normally erased at runtime (type erasure), but reflection can still recover
// DECLARED generic signatures (not runtime instance types) via getGenericSuperclass(), getGenericType(), etc.
// -- this is how libraries like Jackson figure out that a field is List<MyDto> and not just List.
```

# Annotations Basics

--> Annotations attach metadata to code (classes, methods, fields, parameters) without directly affecting program logic themselves -- their meaning comes entirely from tools/frameworks/reflection that READ them.

```java
@Retention(RetentionPolicy.RUNTIME)   // must be RUNTIME to be visible via reflection at all
@Target(ElementType.METHOD)           // restricts where this annotation can legally be applied
public @interface Benchmark {
    String label() default "";
}

public class Service {
    @Benchmark(label = "critical-path")
    public void process() { /* ... */ }
}
```

--> **`@Retention` policy determines annotation lifetime**:

```text
SOURCE   -- discarded by the compiler, never in the .class file (e.g. @Override, @SuppressWarnings)
CLASS    -- kept in the .class file but NOT loaded into the JVM at runtime (default; rarely used directly)
RUNTIME  -- kept in the .class file AND available via reflection at runtime -- REQUIRED for any annotation
            you intend to inspect with getAnnotation() at runtime, e.g. @Test in JUnit, @Autowired in Spring
```

## Reading Annotations Reflectively

```java
Method m = Service.class.getMethod("process");
if (m.isAnnotationPresent(Benchmark.class)) {
    Benchmark b = m.getAnnotation(Benchmark.class);
    System.out.println("Label: " + b.label());
}
```

--> This exact pattern -- scan classes/methods for a marker annotation, then act on it -- is precisely how JUnit finds `@Test` methods, how Spring wires `@Autowired` fields, and how Jackson maps `@JsonProperty` fields to JSON keys. Understanding it demystifies a large fraction of "magic" framework behavior.

# Gotchas and Best Practices

--> **Reflection has real performance overhead** -- reflective method invocation is significantly slower than a direct call (bypasses JIT inlining opportunities, involves extra access checks and boxing/unboxing for primitives) -- fine for one-time startup wiring (dependency injection at boot), risky in a hot per-request loop without caching the `Method`/`Field` objects (or better, using `MethodHandle`s, which the JVM can optimize much closer to a direct call).
--> **Reflection breaks compile-time safety** -- `clazz.getDeclaredMethod("computeTotall")` (typo) compiles fine and only fails at RUNTIME with `NoSuchMethodException` -- there's no compiler check on string-based member names. Keep reflective code narrow, well-tested, and prefer compile-time alternatives (interfaces, generics) whenever the set of types is actually known ahead of time.
--> **Module system restrictions (Java 9+)** -- the Java Platform Module System can prevent `setAccessible(true)` from working across module boundaries unless the target module explicitly `opens` that package, or the JVM is launched with `--add-opens module/package=ALL-UNNAMED` -- a very common real-world friction point when upgrading older reflection-heavy frameworks to modern JDKs.
--> **`Class.forName` vs `getClassLoader().loadClass`** -- `Class.forName(name)` by default also INITIALIZES the class (runs static initializers); the loader's `loadClass` alone does not. This distinction has bitten people relying on JDBC drivers self-registering via a static block triggered by `Class.forName(...)`.
--> **Custom class loaders and memory leaks** -- as covered in File 03, a custom class loader that stays reachable (via a lingering thread, static reference, or `ThreadLocal`) keeps every class it loaded -- and their Metaspace footprint -- alive indefinitely. Always ensure custom loaders (and anything they start) can actually become unreachable when their "session" ends.
