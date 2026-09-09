# Java Serialization Fundamentals

--> **Serialization** is the process of converting an object's in-memory state into a linear byte stream that can be persisted to disk, sent over a network, or stored in a cache -- and **deserialization** is the reverse process, reconstructing an equivalent object graph from that byte stream. Java has built this in since 1.1 via the `java.io.Serializable` marker interface and the `ObjectOutputStream`/`ObjectInputStream` classes.
--> The previous Theory file (`03 File IO with java.io.md`) introduced serialization briefly as one use of `java.io` streams. This file goes much deeper -- the exact mechanics of the default serialization algorithm, every rule around `serialVersionUID` and `transient`, and the traps that catch developers who assume serialization "just works" like a generic object copier.
--> Mental model: think of serialization not as "saving an object" but as **saving a class's non-transient, non-static field values**, tagged with just enough class metadata (fully qualified class name + `serialVersionUID`) for the JVM to find the right class again and re-populate a new instance of it later. It does NOT save behavior (methods/bytecode) -- only data.

# The `Serializable` Marker Interface

--> `java.io.Serializable` is a **marker interface** -- it declares zero methods. Implementing it doesn't add any behavior directly; it's purely a flag that the JVM's serialization machinery checks via `instanceof` before allowing an object to be written out.

```java
import java.io.Serializable;

public class Point implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int x;
    private final int y;

    public Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public String toString() {
        return "Point(" + x + ", " + y + ")";
    }
}
```

--> **Why a marker interface instead of a method contract?** The language designers wanted opt-in serialization without forcing every class to implement boilerplate methods -- the JVM's reflection-based default mechanism handles the field-by-field work automatically once the marker is present. Compare this to `Externalizable` (covered in the next Theory file), which DOES require implementing methods for full manual control.
--> **Attempting to serialize a non-`Serializable` object throws `NotSerializableException` at RUNTIME**, not a compile-time error -- the compiler has no way to know at compile time whether `ObjectOutputStream.writeObject(Object)` will be called with a serializable instance, since the parameter type is just `Object`. This is a very common surprise: code compiles fine, then blows up the first time it actually runs against real data.
--> **The entire object graph must be serializable.** If `Point` had a field `private SomeHelper helper;` where `SomeHelper` does NOT implement `Serializable`, attempting to serialize a `Point` throws `NotSerializableException: SomeHelper` -- unless that field is marked `transient` (see below). Serialization is transitive: every reachable, non-transient object must itself be `Serializable`.

# `ObjectOutputStream` and `ObjectInputStream`

--> These two classes are the workhorses of Java serialization. `ObjectOutputStream` wraps an underlying `OutputStream` (typically a `FileOutputStream` or `ByteArrayOutputStream`) and adds `writeObject(Object)`. `ObjectInputStream` wraps an `InputStream` and adds `readObject()`.

```java
import java.io.*;
import java.util.ArrayList;
import java.util.List;

class Employee implements Serializable {
    private static final long serialVersionUID = 1L;

    private String name;
    private int age;
    private double salary;

    public Employee(String name, int age, double salary) {
        this.name = name;
        this.age = age;
        this.salary = salary;
    }

    @Override
    public String toString() {
        return "Employee{name='" + name + "', age=" + age + ", salary=" + salary + "}";
    }
}

public class ObjectStreamDemo {
    public static void main(String[] args) throws IOException, ClassNotFoundException {
        List<Employee> team = new ArrayList<>();
        team.add(new Employee("Ada Lovelace", 30, 95000.0));
        team.add(new Employee("Alan Turing", 34, 105000.0));

        String path = "team.ser";

        // --- Serialize: write the whole list in one call ---
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(path))) {
            out.writeObject(team);   // List is Serializable (ArrayList implements it); writes the graph
        }

        // --- Deserialize: reconstruct the list and every Employee inside it ---
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(path))) {
            @SuppressWarnings("unchecked")
            List<Employee> restored = (List<Employee>) in.readObject();   // unchecked cast -- type erasure
            restored.forEach(System.out::println);
        }

        new File(path).delete();
    }
}
```

--> **`readObject()` returns `Object`** -- you must cast to the expected type, and because of generic type erasure the compiler cannot verify that cast for parameterized types like `List<Employee>`, producing an unchecked-cast warning. There is no way around this with the classic API; it's one of the ergonomic costs of a reflection-based, type-erased serialization mechanism.
--> **`ClassNotFoundException`** -- `readObject()` declares this checked exception because deserialization looks up the class by fully-qualified name via the classloader at read time. If that class isn't on the classpath of the JVM doing the deserializing (e.g., you serialized with a private `Employee` class not shipped to the reading application), this is thrown instead of `NotSerializableException`.
--> **Multiple objects, one stream** -- you can call `writeObject()` several times on the same stream to write multiple objects sequentially, then call `readObject()` the same number of times, in the same order, to read them back. The stream has no built-in "how many objects are there" marker; the reader must know the expected shape.
--> **Object graphs and shared references are preserved** -- if two fields in the object graph point to the SAME instance, Java's serialization detects this (via an internal handle table) and reconstructs a single shared instance on the read side too, rather than two separate copies. This also correctly handles circular references (A references B, B references A back) without infinite recursion.

# How Default Serialization Actually Works

--> When a class implements `Serializable` and does NOT define custom `writeObject`/`readObject` methods (covered in the next Theory file), the JVM uses **default serialization**, driven entirely by reflection:

1. Write the class descriptor: fully-qualified class name, `serialVersionUID`, and a description of the field types/names.
2. Walk the class hierarchy from `Object` down to the actual class, and for each level, write out every **non-static, non-transient** field's current value, in a JVM-determined order.
3. For fields whose type is itself an object, RECURSE -- serialize that object's fields the same way (transitively).

--> On deserialization, the reverse happens -- but critically, **no constructor of the serializable class is called** for a normal `Serializable` type (this is unlike a plain "new object + copy fields" operation). Instead, the JVM allocates a raw, uninitialized instance directly (bypassing constructors entirely, roughly the way `Unsafe.allocateInstance` operates internally) and populates its fields directly via reflection. This is why final fields CAN still be set during deserialization despite `final` normally requiring assignment in a constructor.
--> **Constructor of the first non-serializable superclass DOES run.** If your `Serializable` class extends a non-`Serializable` superclass, that superclass's no-arg constructor IS invoked during deserialization (to properly initialize whatever state that superclass needs), but any constructor in the `Serializable` class itself and its `Serializable` ancestors is skipped. A non-serializable superclass without an accessible no-arg constructor makes the subclass fail to deserialize with `InvalidClassException`.

```java
class Base {
    protected String label;
    Base() {
        label = "default-from-Base-ctor";
        System.out.println("Base constructor ran");
    }
}

class Derived extends Base implements Serializable {
    private static final long serialVersionUID = 1L;
    private int value;
    Derived(int value) {
        this.value = value;
        this.label = "explicitly set";
    }
}
// Deserializing a Derived instance: Base() DOES run (prints the message),
// but Derived(int) does NOT run -- fields are set directly by reflection.
```

# `static` Fields Are Never Serialized

--> Serialization operates on INSTANCE state. `static` fields belong to the class, not any particular instance, so they are always skipped -- there's no need for `transient` on a `static` field, though adding it is harmless and sometimes used for documentation clarity.

```java
class Counter implements Serializable {
    private static final long serialVersionUID = 1L;
    static int instancesCreated = 0;   // never serialized -- belongs to the class
    private int id;

    Counter() {
        id = ++instancesCreated;
    }
}
```

# The `transient` Keyword

--> `transient` marks an instance field to be EXCLUDED from the default serialization process. On deserialization, a `transient` field is left at its type's default value: `null` for objects, `0`/`0.0` for numeric primitives, `false` for `boolean`.

```java
import java.io.*;

class UserSession implements Serializable {
    private static final long serialVersionUID = 1L;

    private String username;
    private transient String authToken;     // sensitive -- must never hit disk
    private transient Thread workerThread;   // not serializable anyway (Thread isn't Serializable)

    UserSession(String username, String authToken) {
        this.username = username;
        this.authToken = authToken;
    }

    @Override
    public String toString() {
        return "UserSession{username='" + username + "', authToken=" + authToken + "}";
    }
}
```

--> **Common reasons to mark a field `transient`:**
| Reason | Example |
|---|---|
| Security -- avoid persisting secrets | passwords, auth tokens, API keys, session IDs |
| Field type isn't `Serializable` | `Thread`, `Socket`, database `Connection`, file handles |
| Cheap to recompute -- no need to persist | cached hash codes, derived/computed totals |
| Not meaningful across JVM runs | in-memory-only handles, listeners, thread pools |
--> **`transient` fields must be re-initialized manually if needed** -- typically inside a custom `readObject()` method (next Theory file), or by lazily recomputing the value the next time it's accessed after deserialization. Forgetting this is a very common bug: code assumes a field is always populated, then throws `NullPointerException` only on objects that came from deserialization.

# `serialVersionUID` in Depth

--> `serialVersionUID` is a `private static final long` field that acts as a **version fingerprint** for a class's serialized form. It is written into the byte stream when serializing, and checked against the CURRENT class's `serialVersionUID` when deserializing.

```java
class Config implements Serializable {
    private static final long serialVersionUID = 3L;   // bumped manually after a breaking field change
    private String environment;
    private int retryCount;
    // private boolean debugMode;  <-- removed in this version; UID bumped deliberately
}
```

--> **If you don't declare it explicitly**, the JVM auto-generates one at RUNTIME by hashing structural details of the class (fields, methods, interfaces, etc.) using an algorithm defined in the Java Object Serialization Specification. This auto-generated value is **highly sensitive to compiler and class changes** -- adding a method, changing field order in some toolchains, or even switching compilers can silently produce a different UID.
--> **Consequence of relying on the auto-generated UID**: a class serialized by one build and deserialized by a slightly different build (even a logically-compatible change like adding an unrelated method) can fail with:
```
java.io.InvalidClassException: com.example.Config; local class incompatible:
stream classdesc serialVersionUID = 4890321498104821, local class serialVersionUID = 1234567890123456
```
--> **Best practice: ALWAYS declare `serialVersionUID` explicitly**, even just as `1L`. This decouples version compatibility from incidental class changes and puts YOU in control of exactly when a version bump should be treated as a breaking change vs. a compatible evolution. Every modern IDE and static analyzer (IntelliJ, Eclipse, SonarQube, `serial` javac lint) warns when a `Serializable` class omits it.
--> **What counts as a compatible change (default UID checking aside)?** Adding new fields is generally safe with default serialization -- old streams simply leave the new field at its default value on read. Removing fields, changing a field's type, or changing a class from `Serializable` to non-`Serializable` (or vice versa) are the kinds of changes that typically warrant a UID bump, because the shapes no longer line up.

| Scenario | Effect at deserialization time |
|---|---|
| UIDs match, field added since serialization | New field gets its default value; deserialization succeeds |
| UIDs match, field removed since serialization | Old field's value in the stream is simply discarded; succeeds |
| UIDs match, field type changed | `InvalidClassException` -- type mismatch is always fatal regardless of UID |
| UIDs DON'T match | `InvalidClassException` immediately, even if the fields would otherwise be compatible |

# Serializing Arrays and Common Collections

--> Arrays are serializable automatically (the array type itself implements `Serializable` implicitly) as long as their element type is serializable. Most `java.util` collection implementations (`ArrayList`, `HashMap`, `HashSet`, `LinkedList`, etc.) implement `Serializable` already -- but the objects placed INSIDE them still need to be serializable too.

```java
import java.io.*;
import java.util.*;

public class CollectionSerializationDemo {
    public static void main(String[] args) throws IOException, ClassNotFoundException {
        Map<String, int[]> scoresByStudent = new HashMap<>();
        scoresByStudent.put("Ada", new int[]{95, 88, 91});
        scoresByStudent.put("Alan", new int[]{80, 76, 99});

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(buffer)) {
            out.writeObject(scoresByStudent);
        }

        byte[] serializedBytes = buffer.toByteArray();
        System.out.println("Serialized size: " + serializedBytes.length + " bytes");

        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(serializedBytes))) {
            @SuppressWarnings("unchecked")
            Map<String, int[]> restored = (Map<String, int[]>) in.readObject();
            restored.forEach((name, scores) -> System.out.println(name + ": " + Arrays.toString(scores)));
        }
    }
}
```

--> **`HashMap`/`HashSet` and hash-order caveats** -- these serialize the underlying bucket structure conceptually as key/value writes, but iteration order after deserialization is governed by the SAME hashing rules as any freshly populated hash-based collection -- it is not guaranteed to match insertion order or the order before serialization. Never rely on `HashMap`/`HashSet` iteration order surviving a serialize/deserialize round trip; use `LinkedHashMap`/`LinkedHashSet`/`TreeMap` if order matters.
--> **`ByteArrayOutputStream`/`ByteArrayInputStream`** are a common pairing with `ObjectOutputStream`/`ObjectInputStream` when you want the serialized bytes in memory (e.g., to store in a cache, send over a socket, or inspect size) rather than immediately writing to a file.

# Common Gotchas

--> **Forgetting `implements Serializable` on a nested/inner class field** -- a perfectly reasonable-looking class fails at runtime with `NotSerializableException` naming the offending nested type, not the outer class.
--> **Non-static inner classes implicitly hold a reference to their enclosing instance** -- if you make a non-static inner class `Serializable`, its hidden `this$0` reference to the outer instance is ALSO serialized, which means the outer class must be `Serializable` too (or you get `NotSerializableException` on the outer class, which is confusing since you never explicitly referenced it). Prefer `static` nested classes for anything you intend to serialize independently.
--> **Relying on the default auto-generated `serialVersionUID`** -- makes your serialized data brittle across recompiles/JDK/compiler version changes. Always declare it explicitly.
--> **Assuming deserialization calls your constructor** -- it does not (for plain `Serializable` types), so any invariant-establishing logic in a constructor (validation, computing derived fields) is silently skipped on the deserialization path unless you use custom `readObject()` (next Theory file).
--> **Forgetting that `transient` fields come back `null`/`0`/`false`** -- code that assumes a field is always populated after construction can break specifically (and often only in production, only for objects loaded from persisted/cached state) for deserialized instances.
--> **Serializing large object graphs unintentionally** -- if class `A` holds a reference to a huge cache, logger, or the whole application context, and `A` is `Serializable` without marking that reference `transient`, serializing one small `A` object can attempt to drag the ENTIRE reachable graph along with it, causing huge output, `StackOverflowError` on deeply nested graphs, or accidental `NotSerializableException` deep in unrelated code.
--> **Serialization is JVM/version sensitive for CLASS FILES, not just data** -- serialized bytes reference a class by name, not by embedding its bytecode; the reading JVM must have a structurally-compatible version of that exact class available on its classpath.

# Best Practices Summary

--> Always declare `private static final long serialVersionUID` explicitly on every `Serializable` class, and bump it deliberately on breaking changes.
--> Mark sensitive fields (credentials, tokens) and non-serializable/non-persistable fields (threads, sockets, connections, caches) as `transient`.
--> Prefer `static` nested classes over non-static inner classes for anything you plan to serialize, to avoid dragging in an implicit outer-instance reference.
--> Remember default serialization skips constructors -- don't put essential invariant-establishing logic only in a constructor if instances may also be created via deserialization.
--> Treat every `Serializable` class as part of your public API surface once instances have been persisted anywhere -- field removal/type changes are effectively breaking changes for old data.
--> For anything beyond simple same-JVM-version caching or internal RPC, prefer explicit data formats (JSON, Protocol Buffers) over Java's native serialization -- covered in depth in `03 Serialization Security and Alternatives.md`.
