# Custom Serialization and `Externalizable`

--> The previous Theory file covered DEFAULT serialization -- the JVM reflectively walking a class's fields. This file covers the mechanisms Java provides for taking manual control of that process: the special `writeObject`/`readObject` methods, the full-control `Externalizable` interface, and the `writeReplace`/`readResolve` hooks used for advanced patterns like enforcing singletons or version-bridging old data.
--> Mental model: default serialization asks "what does this object's memory layout look like, structurally?" Custom serialization lets you instead answer "what data does this object NEED to reconstruct itself, and how should that be read back?" -- which is a more deliberate, more maintainable contract, especially for classes with derived state, invariants, or non-serializable fields that need special handling.

# `writeObject` / `readObject` -- Customizing Default Serialization

--> A `Serializable` class can define two special PRIVATE methods with these EXACT signatures. The JVM's serialization machinery finds and calls them via reflection (not through any interface, since `Serializable` declares no methods) -- so the signature must match precisely, including `private`.

```java
private void writeObject(java.io.ObjectOutputStream out) throws IOException;
private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException;
```

--> Inside these methods, you typically call `out.defaultWriteObject()` / `in.defaultReadObject()` to still perform the NORMAL default serialization for all non-transient fields, and then explicitly read/write ADDITIONAL data before or after that call. This lets you augment default behavior instead of replacing it entirely.

```java
import java.io.*;

class BankAccount implements Serializable {
    private static final long serialVersionUID = 1L;

    private String accountHolder;
    private double balance;
    private transient String encryptedPin;   // handled manually below, not by default mechanism

    BankAccount(String accountHolder, double balance, String pin) {
        this.accountHolder = accountHolder;
        this.balance = balance;
        this.encryptedPin = encrypt(pin);
    }

    private String encrypt(String raw) {
        return "ENC[" + raw + "]";   // stand-in for real encryption
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();               // writes accountHolder, balance (non-transient fields)
        out.writeUTF(encryptedPin);              // manually write the transient field, already "encrypted"
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();                  // restores accountHolder, balance
        this.encryptedPin = in.readUTF();         // manually restore the transient field
    }

    @Override
    public String toString() {
        return "BankAccount{holder='" + accountHolder + "', balance=" + balance + ", pin=" + encryptedPin + "}";
    }
}

public class CustomSerializationDemo {
    public static void main(String[] args) throws IOException, ClassNotFoundException {
        BankAccount original = new BankAccount("Ada Lovelace", 1500.0, "4471");
        String path = "account.ser";

        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(path))) {
            out.writeObject(original);
        }
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(path))) {
            BankAccount restored = (BankAccount) in.readObject();
            System.out.println(restored);   // encryptedPin correctly restored via manual read
        }
        new File(path).delete();
    }
}
```

--> **Why `defaultWriteObject()`/`defaultReadObject()` matter** -- omitting the call to `defaultWriteObject()` inside `writeObject()` means NONE of the normal non-transient fields get written automatically; you'd have to write every single one manually. Most real-world custom serialization calls `defaultWriteObject()`/`defaultReadObject()` first, then layers extra reads/writes around it -- full manual control of every field is rare and usually a sign `Externalizable` (below) is the better fit.
--> **Common uses for `writeObject`/`readObject`:**
| Use case | Approach |
|---|---|
| Encrypting/decrypting sensitive fields | Mark `transient`, manually encrypt on write, decrypt on read |
| Validating invariants after deserialization | Add checks in `readObject()`; throw `InvalidObjectException` on failure |
| Re-initializing non-serializable resources | Recreate a `transient Logger`/`transient Connection` in `readObject()` |
| Compacting/compressing large fields | Custom binary encoding instead of relying on default field layout |
| Backward-compatible field migration | Detect missing stream fields via `ObjectInputStream.GetField`, supply defaults |
--> **`ObjectInputStream.GetField`/`ObjectOutputStream.PutField`** -- an even lower-level API for reading/writing fields by NAME rather than relying on `defaultReadObject()`/`defaultWriteObject()`, used when a class's field structure has changed between versions and you need to explicitly map old stream field names to new in-memory fields (e.g., a renamed field). This is advanced and relatively rare outside of frameworks maintaining strict backward compatibility.
--> **Validating in `readObject()` is a genuine security/correctness mechanism** -- since deserialization bypasses constructors, any invariant a constructor would normally enforce (e.g., "balance must not be negative") is NOT automatically re-checked for deserialized instances unless you add that check explicitly in `readObject()`. This matters even for trusted data (corrupted files, partial writes) and is critical for untrusted data (see the next Theory file on security).

# The `Externalizable` Interface -- Full Manual Control

--> `java.io.Externalizable` extends `Serializable` and adds two PUBLIC methods you must implement. Unlike `writeObject`/`readObject` (which augment default serialization), implementing `Externalizable` means default serialization is bypassed ENTIRELY -- you are responsible for writing and reading every single byte that matters.

```java
public interface Externalizable extends Serializable {
    void writeExternal(ObjectOutput out) throws IOException;
    void readExternal(ObjectInput in) throws IOException, ClassNotFoundException;
}
```

```java
import java.io.*;

class NetworkPacket implements Externalizable {
    private int sequenceNumber;
    private String payload;
    private long timestamp;

    // Externalizable REQUIRES a public no-arg constructor -- the JVM calls it via reflection
    // BEFORE readExternal() runs, since there is no default mechanism to allocate+populate at once.
    public NetworkPacket() {
    }

    public NetworkPacket(int sequenceNumber, String payload, long timestamp) {
        this.sequenceNumber = sequenceNumber;
        this.payload = payload;
        this.timestamp = timestamp;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeInt(sequenceNumber);
        out.writeUTF(payload);
        out.writeLong(timestamp);
        // Nothing is automatic here -- forgetting a field here means it is silently never persisted.
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        sequenceNumber = in.readInt();
        payload = in.readUTF();
        timestamp = in.readLong();
        // Must read fields back in the EXACT SAME ORDER they were written.
    }

    @Override
    public String toString() {
        return "NetworkPacket{seq=" + sequenceNumber + ", payload='" + payload + "', ts=" + timestamp + "}";
    }
}

public class ExternalizableDemo {
    public static void main(String[] args) throws IOException, ClassNotFoundException {
        NetworkPacket original = new NetworkPacket(42, "hello", System.currentTimeMillis());
        String path = "packet.ser";

        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(path))) {
            out.writeObject(original);   // same writeObject() call site as always -- delegates to writeExternal()
        }
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(path))) {
            NetworkPacket restored = (NetworkPacket) in.readObject();   // calls public NetworkPacket() then readExternal()
            System.out.println(restored);
        }
        new File(path).delete();
    }
}
```

--> **`Externalizable` REQUIRES a public no-arg constructor.** Unlike default `Serializable` deserialization (which bypasses constructors via low-level allocation), `Externalizable` deserialization DOES call a real constructor -- specifically the public no-arg one -- before invoking `readExternal()` to populate the freshly constructed instance. Omitting a public no-arg constructor causes `InvalidClassException` at deserialization time.
--> **No `serialVersionUID` protection by default** -- because `Externalizable` writes exactly what you tell it to, in exactly the order you specify, there is no automatic field-shape checking; version compatibility is entirely your responsibility (e.g., writing a format-version int as the first field and branching on it in `readExternal()`).

| | `Serializable` (default) | `Serializable` + custom `writeObject`/`readObject` | `Externalizable` |
|---|---|---|---|
| Control level | None -- fully reflective | Partial -- augments default | Full -- you write everything |
| Constructor called on deserialize | No (fields set via low-level allocation) | No (same as default) | Yes -- public no-arg constructor required |
| Performance | Moderate (reflection overhead) | Moderate | Fastest (no reflection for field access) |
| Boilerplate | None | Some | Most -- every field explicitly handled |
| Typical use | Simple data classes, prototyping | Fine-grained tweaks to an otherwise-default class | High-performance/high-control scenarios (RPC frameworks, custom wire formats) |

--> **Why `Externalizable` can be faster** -- default serialization uses reflection to inspect and access fields for every object in the graph, which carries real overhead at scale. `Externalizable`'s explicit `writeInt`/`writeUTF`/etc. calls compile to direct method calls with no reflection, which is why performance-sensitive frameworks (some RPC layers, caching systems) favor it for hot-path types.
--> **Field order discipline is entirely manual and entirely your bug to make** -- `writeExternal` and `readExternal` must agree on the exact sequence and types of fields; there is no compiler or runtime check that they match, only a runtime `EOFException`/garbled-data failure if they don't.

# Serialization and Inheritance Hierarchies

--> When a class hierarchy mixes `Serializable` and non-`Serializable` types, the RULES from the fundamentals file apply per-level: only `Serializable` classes participate in the field-write/read process; a non-`Serializable` ancestor's state is restored only via its normal (no-arg) constructor running once, at deserialization time, not by writing/reading its fields.

```java
import java.io.*;

abstract class Shape implements Serializable {
    private static final long serialVersionUID = 1L;
    protected String color;

    Shape(String color) {
        this.color = color;
    }

    abstract double area();
}

class Circle extends Shape {
    private static final long serialVersionUID = 1L;
    private double radius;

    Circle(String color, double radius) {
        super(color);
        this.radius = radius;
    }

    @Override
    double area() {
        return Math.PI * radius * radius;
    }

    @Override
    public String toString() {
        return "Circle{color='" + color + "', radius=" + radius + ", area=" + area() + "}";
    }
}
```

--> **Each level of a `Serializable` hierarchy needs its own `serialVersionUID`** -- it's a `private` field, so it is NOT inherited; every concrete AND abstract `Serializable` class in the hierarchy should declare its own explicitly, or each level falls back to its own auto-generated UID (with all the fragility that implies).
--> **If a subclass is `Serializable` but its superclass ISN'T**, the superclass must have an accessible no-arg constructor (as discussed in the fundamentals file), and any state the superclass owns is initialized ONLY by whatever that no-arg constructor does -- not by whatever state the object actually held when it was serialized. This is a classic bug source: a non-serializable base class holding meaningful state silently "resets" that state to whatever the no-arg constructor produces, on every deserialization.
--> **Polymorphic collections** (e.g., `List<Shape>` holding a mix of `Circle`, `Square`, etc.) serialize and deserialize correctly as long as every concrete class actually present in the collection is `Serializable` -- the exact runtime type of each element is recorded and used to reconstruct the correct subclass on read.

# `writeReplace` and `readResolve`

--> These two OPTIONAL, special-named methods let a class substitute a DIFFERENT object for itself during serialization (`writeReplace`) or deserialization (`readResolve`) -- powerful hooks for patterns like enforcing singletons, converting to/from a lightweight proxy representation, or interning shared instances.

```java
private Object writeReplace() throws ObjectStreamException;
private Object readResolve() throws ObjectStreamException;
```

### `readResolve` -- Enforcing Singleton Semantics Across Deserialization

--> A classic problem: even a proper singleton (private constructor, single static instance) can have that guarantee BROKEN by serialization, because deserialization allocates a brand-new instance via low-level mechanisms rather than calling the constructor. `readResolve()` fixes this by letting the class swap in the canonical instance right after deserialization completes.

```java
import java.io.*;

class AppConfig implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final AppConfig INSTANCE = new AppConfig();

    private String environment = "production";

    private AppConfig() {
    }

    public static AppConfig getInstance() {
        return INSTANCE;
    }

    // Without this method, deserializing a stream containing an AppConfig would silently
    // produce a SECOND instance, distinct from INSTANCE, breaking the singleton guarantee.
    private Object readResolve() throws ObjectStreamException {
        return INSTANCE;
    }
}
```

### `writeReplace` -- Substituting a Proxy Representation

--> `writeReplace()` runs BEFORE serialization and lets a class say "don't serialize me directly -- serialize this other object instead." A common pairing: an enum-like class serializes itself as a lightweight, stable proxy object that knows how to rebuild the real (possibly complex, possibly non-serializable-by-default) instance on the read side via that proxy's own `readResolve()`.

```java
import java.io.*;

class ImmutablePoint implements Serializable {
    private static final long serialVersionUID = 1L;
    private final transient int x;   // transient here because the proxy handles persistence instead
    private final transient int y;

    ImmutablePoint(int x, int y) {
        this.x = x;
        this.y = y;
    }

    private Object writeReplace() throws ObjectStreamException {
        return new SerializationProxy(x, y);
    }

    // If ImmutablePoint is ever deserialized directly (bypassing the proxy), reject it --
    // a common defensive pattern from Joshua Bloch's "Effective Java".
    private void readObject(ObjectInputStream in) throws InvalidObjectException {
        throw new InvalidObjectException("Use SerializationProxy instead");
    }

    private static class SerializationProxy implements Serializable {
        private static final long serialVersionUID = 1L;
        private final int x;
        private final int y;

        SerializationProxy(int x, int y) {
            this.x = x;
            this.y = y;
        }

        private Object readResolve() throws ObjectStreamException {
            return new ImmutablePoint(x, y);   // rebuild via the REAL constructor -- invariants re-run!
        }
    }
}
```

--> **Why the proxy pattern is considered a best practice for immutable/invariant-heavy classes** -- it routes deserialization through the actual constructor (`new ImmutablePoint(x, y)`), which means constructor validation logic runs again, unlike ordinary deserialization which bypasses constructors entirely. It also defends against a maliciously crafted byte stream trying to fabricate an `ImmutablePoint` with an illegal internal state (see the next Theory file for why this matters for untrusted data).
--> **`enum` types have `readResolve()`-like behavior built in automatically** -- the JVM guarantees that deserializing an enum constant always resolves to the existing constant of that name via `Enum.valueOf`, never allocates a new instance. This is one reason effective-Java guidance often prefers enum-based singletons over hand-written ones -- the singleton guarantee survives serialization for free.

# Common Gotchas

--> **Wrong method signature silently disables `writeObject`/`readObject`** -- if the signature doesn't match EXACTLY (`private void writeObject(ObjectOutputStream out) throws IOException`), the JVM simply won't find it via reflection and falls back to fully default serialization, with NO compiler error or warning that your intended customization was ignored.
--> **Forgetting `defaultWriteObject()`/`defaultReadObject()`** inside a custom `writeObject`/`readObject` pair means ordinary fields silently stop being serialized, even though the class still compiles and appears to "work" for fields you handle manually.
--> **`Externalizable` classes without a public no-arg constructor** fail with `InvalidClassException` only at deserialization time -- easy to miss in testing if you never actually round-trip an instance through bytes during development.
--> **Field order mismatch in `writeExternal`/`readExternal`** produces no compiler error, just garbled data or an `EOFException`/`ClassCastException` at read time, often far from the actual bug.
--> **`readResolve`/`writeReplace` must be effectively `private` (or at least not `public static`) and must match the exact zero-arg signature** -- `Object readResolve() throws ObjectStreamException` -- getting the signature wrong means it's silently never invoked, just like `writeObject`/`readObject`.
--> **Records and serialization** -- Java `record` types CAN implement `Serializable`, but they follow special rules: the canonical constructor IS invoked during deserialization (unlike ordinary classes), which means record component validation in a compact constructor DOES run automatically, giving records much of the proxy pattern's safety for free.

# Best Practices Summary

--> Use custom `writeObject`/`readObject` when you need to AUGMENT default serialization (encrypt a field, validate invariants, handle a transient resource) -- always call `defaultWriteObject()`/`defaultReadObject()` first unless you have a specific reason not to.
--> Use `Externalizable` only when you need maximum performance/control and are willing to own full responsibility for every field and every version-compatibility concern -- it is the less common choice for typical application code.
--> Always give `Externalizable` classes a public no-arg constructor; document clearly that it exists ONLY for the serialization framework, not for normal use.
--> Use `readResolve()` to protect singleton invariants, and the writeReplace/serialization-proxy pattern to protect immutability/validation invariants for classes where a forged byte stream could otherwise create an invalid instance.
--> Prefer `enum`-based singletons when possible -- the JVM's built-in enum deserialization semantics make the singleton guarantee automatic, with no `readResolve()` needed.
--> Remember that both custom `writeObject`/`readObject` and `Externalizable` still leave you fully responsible for the security concerns of accepting a byte stream from an untrusted source -- covered next.
