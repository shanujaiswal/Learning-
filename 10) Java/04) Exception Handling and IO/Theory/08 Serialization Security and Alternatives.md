# Serialization Security and Alternatives

--> The previous two files covered HOW Java serialization works, in default and custom forms. This file covers WHY it is now widely regarded as one of the more dangerous corners of the standard library when untrusted data is involved, the concrete mechanism behind that danger, and the alternatives the industry has largely moved to.
--> The short version, stated up front: **never call `ObjectInputStream.readObject()` on data from an untrusted or unauthenticated source.** Everything in this file explains why that rule exists and what to do instead.

# Why Deserialization of Untrusted Data Is Dangerous

--> The core problem is a mismatch between WHAT `readObject()` does and WHAT developers intuitively expect it to do. Intuitively, it feels like "parsing data into a known shape," similar to parsing JSON into a DTO. In reality, `ObjectInputStream.readObject()`:

1. Reads a fully-qualified CLASS NAME directly from the untrusted byte stream.
2. Loads that class via the classloader (if not already loaded).
3. Allocates an instance WITHOUT calling any constructor of that class (as covered in file 1).
4. Populates its fields directly from stream data.
5. **Then invokes that object's `readObject()` method, if it defines one -- and this is attacker-influenced code execution happening automatically, before your application logic ever sees the resulting object.**

--> The attacker controls step 1 (which class to instantiate) and step 5 (which of that class's own deserialization-time code runs) as long as SOME class reachable on the application's classpath has interesting behavior in its `readObject()`, static initializers, `finalize()`/`Object.finalize` (legacy), or other methods invoked incidentally during or shortly after deserialization. The attacker does not need to control the vulnerable class -- they only need it to already exist somewhere in your dependency tree.

# Gadget Chains -- The Core Concept

--> A **gadget** is any class, already present on the application's classpath (in your code OR a third-party library), whose deserialization-time behavior (constructor logic that runs after allocation is bypassed doesn't count, but `readObject()`, `hashCode()`, `equals()`, `compareTo()`, `finalize()`, etc. invoked as a SIDE EFFECT of deserialization or of immediately-following framework code DOES count) does something exploitable, even in a small, seemingly-harmless way.
--> A **gadget chain** links several such gadgets together: object A's `readObject()` calls a method on a field, which happens to be an instance of class B, whose method does something with a field of class C, and so on -- until the chain reaches something genuinely dangerous, like invoking `Runtime.exec()`, reading/writing arbitrary files, or triggering reflection to call an arbitrary method. Security researchers have published (and cataloged, e.g., via the `ysoserial` tool) gadget chains built ENTIRELY from classes shipped in extremely common libraries -- Apache Commons Collections, Spring, Groovy, and others -- meaning many real-world applications were exploitable purely because of dependencies they already had, with no vulnerable code of their own.
--> **Why this is worse than a typical injection bug**: a SQL injection vulnerability requires a flaw in YOUR query-building code. A gadget-chain deserialization vulnerability can exist purely because a vulnerable class is REACHABLE on the classpath -- it doesn't require any flaw in code you wrote. Simply calling `readObject()` on attacker-controlled bytes, anywhere in the application, with a vulnerable gadget chain anywhere on the classpath, can be enough. This is why the mitigation focus shifted from "write safer code" to "stop deserializing untrusted data at all" or "restrict which classes can even be instantiated."

```java
// DANGER -- illustrative only, do NOT run against untrusted input.
// If `incomingBytes` came from a network socket, an uploaded file, an HTTP request body,
// or any other source outside your own trust boundary, this single line is a potential
// remote-code-execution vector, regardless of what MyExpectedClass looks like.
try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(incomingBytes))) {
    Object obj = in.readObject();   // attacker chose the class; its readObject() already ran by now
}
```

--> **Real-world impact** -- Java deserialization gadget chains were behind a wave of serious CVEs in the mid-to-late 2010s across major frameworks and products (including remote code execution vulnerabilities in application servers, middleware, and enterprise software that accepted serialized Java objects over the network). This history is the primary reason serialization is treated with such caution today, and why many security guidelines (including OWASP's) list "insecure deserialization" as its own named risk category.

# Mitigations When You Must Use Java Serialization

--> If untrusted deserialization genuinely cannot be avoided (e.g., legacy system constraints), these are the standard mitigations, from strongest to weakest:

| Mitigation | How it helps |
|---|---|
| **`ObjectInputFilter`** (Java 9+, `java.io.ObjectInputFilter`) | Lets you allow-list exactly which classes `ObjectInputStream` is permitted to instantiate, by name/pattern, and reject everything else BEFORE the dangerous instantiation happens |
| Avoid Java serialization entirely for network/file input | Use a data format with no code-execution surface (below) |
| Keep dependencies minimal and updated | Fewer libraries on the classpath = fewer potential gadgets; patched libraries remove known gadget classes |
| Run with a restrictive `SecurityManager` | Historically used to sandbox what deserialized code could do (Note: `SecurityManager` was deprecated for removal starting Java 17, and removed in Java 24 -- no longer a viable mitigation on current JDKs) |
| Never expose a raw `ObjectInputStream` endpoint on a network-facing service | Structural prevention -- don't create the vulnerable surface in the first place |

```java
import java.io.*;

public class SafeDeserializationDemo {
    public static void main(String[] args) throws Exception {
        // ObjectInputFilter (Java 9+): allow ONLY specific, known-safe classes.
        ObjectInputFilter filter = ObjectInputFilter.Config.createFilter(
            "com.example.model.Employee;com.example.model.Department;!*"
            // "!*" at the end means: reject anything not explicitly allowed above
        );

        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream("data.ser"))) {
            in.setObjectInputFilter(filter);
            Object obj = in.readObject();   // throws InvalidClassException if a disallowed class appears
        }
    }
}
```

--> **`ObjectInputFilter` is the single most effective built-in mitigation** if you must keep using `ObjectInputStream` -- it moves the trust decision ("which classes am I willing to instantiate from this stream?") to an explicit allow-list under your control, rather than implicitly trusting anything reachable on the classpath. It can be set per-stream (`setObjectInputFilter`) or JVM-wide (`jdk.serialFilter` system property / security property).
--> **Allow-listing beats block-listing** -- a deny-list of "known bad" gadget classes is a losing, reactive game (new gadget chains are discovered continuously); an allow-list of "known good, expected" classes is finite and closed by construction.

# Why Java Serialization Is Considered Legacy for Cross-Boundary Data

--> Beyond the security dimension, Java's native serialization has structural downsides for any data that crosses a trust, language, or version boundary:

| Limitation | Detail |
|---|---|
| **Java-only** | The byte format is Java-specific; no straightforward interop with services written in other languages |
| **Tightly coupled to class structure** | `serialVersionUID`/field-shape mismatches break compatibility (file 1) -- brittle across independent deploys |
| **Opaque, non-human-readable** | Binary format; can't be inspected, diffed, or debugged by eye the way JSON/XML can |
| **No schema/contract separate from code** | The "schema" IS the Java class; no independent interface definition to version or document |
| **Security surface** | As detailed above -- the single biggest reason to avoid it for untrusted input |

--> **Where Java serialization still legitimately shows up**: same-JVM-version internal caching, some RPC frameworks operating entirely within a single trusted deployment (with `ObjectInputFilter` in place), and legacy systems built around it long before alternatives were standard. New code, especially anything touching a network boundary, a browser, another service, or another language, should default AWAY from it.

# Alternative: JSON (Jackson / Gson)

--> JSON serialization libraries convert objects to/from a text-based, human-readable, language-agnostic format. Unlike `ObjectInputStream.readObject()`, a JSON deserializer like Jackson's `ObjectMapper` builds objects by calling constructors/setters against a KNOWN target type YOU specify -- it never lets the input stream dictate which arbitrary class gets instantiated.

```java
import com.fasterxml.jackson.databind.ObjectMapper;

class Employee {
    public String name;
    public int age;

    public Employee() { }   // Jackson typically needs a no-arg constructor (or configured alternative)
    public Employee(String name, int age) { this.name = name; this.age = age; }
}

public class JacksonDemo {
    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        Employee original = new Employee("Ada Lovelace", 30);
        String json = mapper.writeValueAsString(original);
        System.out.println(json);   // {"name":"Ada Lovelace","age":30} -- readable, inspectable

        // The caller decides the TARGET TYPE -- the input can't smuggle in an arbitrary class.
        Employee restored = mapper.readValue(json, Employee.class);
        System.out.println(restored.name + ", " + restored.age);
    }
}
```

--> **Why this is structurally safer** -- with Jackson/Gson, `mapper.readValue(json, Employee.class)` tells the library exactly what type to build; the JSON text has no mechanism to say "actually, instantiate `java.rmi.server.UnicastRemoteObject` instead" the way a Java-serialized stream's embedded class name can. (Jackson DOES support polymorphic typing via `@JsonTypeInfo`, which reintroduces a narrower version of the same class-selection risk if misconfigured with an open/unbounded type hierarchy -- so even here, restricting allowed types matters.)
--> **Gson** (Google's library) offers a similar API surface (`Gson.toJson`/`fromJson`) with a lighter-weight, more convention-based feel; Jackson is more feature-rich (annotations, modules for Java time types, streaming API, broader ecosystem integration with Spring, etc.) and is the more common default in larger applications.

# Alternative: Protocol Buffers (protobuf)

--> Protocol Buffers is a binary serialization format developed by Google, built around an explicit, language-neutral SCHEMA file (`.proto`) that is compiled into generated code for many languages (Java, Python, Go, C++, etc.).

```protobuf
// employee.proto
syntax = "proto3";

message Employee {
  string name = 1;
  int32 age = 2;
}
```

--> A code generator produces a Java class (`Employee`) with builder-style construction and `toByteArray()`/`parseFrom(byte[])` methods. Because the wire format is defined by the `.proto` schema rather than reflected from a live Java class, protobuf messages are:
--> **Strongly schema-versioned by design** -- fields are numbered, and the format has explicit, well-documented rules for what changes are backward/forward compatible (adding a new optional field is always safe; reusing a field number is not).
--> **Compact and fast** -- a well-known, mature binary format, generally smaller on the wire and faster to encode/decode than JSON text, which matters for high-throughput internal service-to-service communication.
--> **Cross-language by construction** -- the same `.proto` file generates matching code for services written in entirely different languages, making it a natural fit for polyglot microservice architectures (and it underlies gRPC).
--> **Not human-readable** without the schema and tooling (unlike JSON), and it introduces a build-time code-generation step that JSON libraries typically don't require.

# Alternative: Apache Avro

--> Avro is another schema-based binary serialization format, notably popular in the big-data/streaming ecosystem (Kafka, Hadoop). Its key differentiator from protobuf is that the schema (in JSON form) is typically stored/transmitted ALONGSIDE the data (or in a schema registry) rather than compiled permanently into generated code, which makes it well suited to systems where schemas evolve frequently and data is processed generically without needing a matching compiled class for every producer.

# Comparison Table

| | Java Serialization | JSON (Jackson/Gson) | Protocol Buffers | Apache Avro |
|---|---|---|---|---|
| Format | Binary, Java-specific | Text (human-readable) | Binary, schema-defined | Binary, schema-defined (schema often external) |
| Cross-language | No | Yes | Yes | Yes |
| Schema separate from code | No (schema = class) | Loose/optional (POJOs, or schema-less) | Yes (`.proto` file) | Yes (Avro schema, often in a registry) |
| Security for untrusted input | Poor -- avoid | Good, if target type is fixed and polymorphism is restricted | Good -- no arbitrary class instantiation from wire data | Good -- same reasoning |
| Human debuggability | Very poor | Excellent | Poor (needs schema + tooling) | Poor (needs schema + tooling) |
| Typical use today | Legacy, same-JVM internal caching only | REST APIs, config, general-purpose interchange | High-throughput internal RPC (esp. with gRPC), polyglot systems | Streaming/big-data pipelines (Kafka, Hadoop ecosystems) |
| Versioning story | Fragile (`serialVersionUID`) | Flexible but implicit/manual | Strong, explicit, well-documented rules | Strong, schema-evolution-first design |

# Common Gotchas

--> **Assuming "it's internal, so it's trusted"** -- internal services still cross process/network boundaries, and an internal service can itself be compromised or receive attacker-influenced input indirectly; "trusted" boundaries have a way of eroding over time as systems integrate.
--> **Adding `ObjectInputFilter` too late** -- it must be set on the `ObjectInputStream` BEFORE `readObject()` is called; setting it after is a no-op for that read.
--> **Jackson polymorphic deserialization misconfigured with `@JsonTypeInfo` and an unbounded base type** -- can reintroduce a narrower form of the same "attacker picks the class" problem; always restrict to a known, closed set of subtypes (`@JsonSubTypes`) rather than allowing arbitrary class-name-driven polymorphism.
--> **Treating protobuf/Avro as a drop-in replacement with zero migration cost** -- both require schema authorship and a build-time (protobuf) or registry-based (Avro) workflow that plain Java objects/JSON don't need; the safety and performance gains come with real tooling overhead.
--> **Forgetting that fixing the security problem doesn't remove the OTHER downsides of Java serialization** -- even with `ObjectInputFilter` correctly locked down, you still have an opaque, Java-only, structurally-brittle format; filtering makes it SAFER, not necessarily a good long-term choice for new cross-boundary data.

# Best Practices Summary

--> Never call `ObjectInputStream.readObject()` on data from an untrusted or unauthenticated source without an `ObjectInputFilter` allow-list in place.
--> Prefer allow-listing (`ObjectInputFilter`) over any attempt at deny-listing known-bad gadget classes.
--> For new code involving network APIs, config, or general interchange, default to JSON (Jackson/Gson) -- readable, tooling-friendly, and structurally safer against arbitrary class instantiation.
--> For high-throughput, cross-language, or strongly-versioned internal service communication, prefer Protocol Buffers (often paired with gRPC).
--> For streaming/big-data pipelines with frequently evolving schemas, consider Avro.
--> Reserve Java's native serialization for narrow, same-JVM-version, fully-trusted internal use cases only -- and even there, keep dependencies minimal to shrink the available gadget surface.
--> Keep abreast of `SecurityManager`'s removal (Java 24) if any legacy mitigation strategy depended on it -- it is no longer available as a sandboxing layer on current JDKs.
