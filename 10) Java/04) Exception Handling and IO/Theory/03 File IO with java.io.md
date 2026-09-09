# The java.io Package -- Java's Original I/O Model

--> `java.io` (present since Java 1.0) provides Java's original, STREAM-based model for reading and writing data -- files, network sockets, in-memory buffers, and more, all through a common abstraction of sequential streams of bytes or characters. It predates the more modern `java.nio.file` API (covered in the next file) by over a decade, but it remains widely used, especially for simple sequential file reading/writing and for its `Serializable` support.
--> The core mental model: a **stream** is a one-directional pipe of data, either flowing IN (`InputStream`/`Reader`) or OUT (`OutputStream`/`Writer`), that you read from or write to sequentially, one chunk at a time, until exhausted or closed.

# The `File` Class

--> `java.io.File` represents a PATH on the filesystem -- a file or directory location -- but importantly, creating a `File` object does NOT create anything on disk. It's just a reference to a path, which may or may not currently exist.

```java
import java.io.File;
import java.io.IOException;

public class FileClassDemo {
    public static void main(String[] args) throws IOException {
        File file = new File("example.txt");   // just an in-memory reference -- nothing on disk yet

        System.out.println("Exists? " + file.exists());
        System.out.println("Absolute path: " + file.getAbsolutePath());
        System.out.println("Name: " + file.getName());

        boolean created = file.createNewFile();   // actually creates the file on disk (if it didn't exist)
        System.out.println("Created new file: " + created);
        System.out.println("Exists now? " + file.exists());
        System.out.println("Is file? " + file.isFile());
        System.out.println("Is directory? " + file.isDirectory());
        System.out.println("Length in bytes: " + file.length());
        System.out.println("Can read? " + file.canRead() + "  Can write? " + file.canWrite());

        File dir = new File("example-dir");
        boolean dirCreated = dir.mkdir();          // create a single directory
        System.out.println("Directory created: " + dirCreated);

        File nestedDirs = new File("a/b/c");
        nestedDirs.mkdirs();                        // create all necessary parent directories too

        // Listing directory contents
        File currentDir = new File(".");
        String[] contents = currentDir.list();
        System.out.println("Entries in current directory: " + (contents != null ? contents.length : 0));

        // Cleanup
        file.delete();
        dir.delete();
    }
}
```

--> **`mkdir()` vs `mkdirs()`** -- `mkdir()` creates exactly one directory and FAILS (returns `false`) if any parent directory in the path doesn't already exist. `mkdirs()` creates every missing directory along the path. This trips people up constantly -- `new File("a/b/c").mkdir()` fails silently (returns `false`, throws nothing) if `a` and `a/b` don't already exist.
--> **`File` is considered legacy for anything beyond basic existence/path checks** -- modern code generally prefers `java.nio.file.Path` and `Files` (next Theory file) for actual file manipulation, since they provide much better error reporting (exceptions instead of silent `boolean` failures) and far more capability.

# Byte Streams vs Character Streams

--> This is the single most important distinction in `java.io`. Getting it wrong (e.g. reading a UTF-8 text file with a raw byte stream and manually splitting on bytes) is a classic source of encoding bugs with multi-byte characters.

| | Byte Streams | Character Streams |
|---|---|---|
| Base classes | `InputStream` / `OutputStream` | `Reader` / `Writer` |
| Unit of data | Raw 8-bit bytes | 16-bit Unicode characters (`char`) |
| Use for | Binary data -- images, audio, serialized objects, any non-text format | Text data -- source code, config files, CSV, JSON, any human-readable content |
| Common subclasses | `FileInputStream`, `FileOutputStream`, `BufferedInputStream` | `FileReader`, `FileWriter`, `BufferedReader`, `BufferedWriter` |
| Encoding awareness | None -- just raw bytes, no concept of "character" | Handles character encoding (UTF-8, etc.) conversion automatically |

```java
import java.io.*;

public class ByteVsCharacterStreams {
    public static void main(String[] args) throws IOException {
        String path = "stream-demo.txt";

        // --- Character stream write (text-aware) ---
        try (FileWriter writer = new FileWriter(path)) {
            writer.write("Hello, streams!\n");
            writer.write("Second line.");
        }

        // --- Character stream read ---
        try (FileReader reader = new FileReader(path)) {
            int c;
            StringBuilder sb = new StringBuilder();
            while ((c = reader.read()) != -1) {   // read() returns int; -1 signals end of stream
                sb.append((char) c);
            }
            System.out.println("Character stream read:\n" + sb);
        }

        // --- Byte stream read (raw bytes, no text interpretation) ---
        try (FileInputStream in = new FileInputStream(path)) {
            int b;
            int byteCount = 0;
            while ((b = in.read()) != -1) {
                byteCount++;
            }
            System.out.println("Total raw bytes in file: " + byteCount);
        }

        new File(path).delete();
    }
}
```

--> **Why `read()` returns `int`, not `char`/`byte`** -- both `Reader.read()` and `InputStream.read()` return `int` specifically so that `-1` (end of stream) can be distinguished from every valid `char`/`byte` value, all of which fit within the non-negative range of `int`. A raw `byte` or `char` return type couldn't represent an "end of stream" sentinel without colliding with a real data value.

# Buffered Streams -- Why They Matter

--> Reading or writing ONE byte/character at a time via `FileReader`/`FileInputStream` directly is extremely slow, because (depending on the underlying implementation) each individual `read()`/`write()` call can trigger a system call to the OS. `BufferedReader`/`BufferedWriter`/`BufferedInputStream`/`BufferedOutputStream` wrap another stream and batch data into an internal in-memory buffer, drastically reducing the number of actual I/O operations.

```java
import java.io.*;

public class BufferedStreamsDemo {
    public static void main(String[] args) throws IOException {
        String path = "buffered-demo.txt";

        // BufferedWriter wraps a FileWriter -- writes accumulate in memory, flushed in batches
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(path))) {
            for (int i = 1; i <= 5; i++) {
                writer.write("Line " + i);
                writer.newLine();   // platform-appropriate line separator
            }
        }

        // BufferedReader adds readLine() -- extremely common pattern for text file processing
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = reader.readLine()) != null) {   // readLine() returns null at end of stream
                System.out.println("Read: " + line);
            }
        }

        new File(path).delete();
    }
}
```

--> **Always wrap raw file streams in a buffered variant** for anything beyond trivial reads -- `new BufferedReader(new FileReader(path))` is close to a universal idiom in pre-NIO.2 Java code. The performance difference on any real-sized file is dramatic (often 10-100x fewer underlying system calls).
--> **`flush()`** -- buffered streams hold data in memory until the buffer fills, the stream is closed, or `flush()` is explicitly called. If a program crashes or exits abnormally before a buffered writer is closed/flushed, buffered-but-unwritten data can be lost -- another reason try-with-resources (guaranteed `close()`, which flushes) matters.

# Serialization -- Converting Objects to Byte Streams

--> Serialization converts a Java object's in-memory state into a byte stream (for saving to disk, sending over a network, etc.), and deserialization reverses the process, reconstructing an equivalent object. A class must implement the marker interface `java.io.Serializable` to participate -- it has no methods to implement, it simply signals "instances of this class are allowed to be serialized."

```java
import java.io.*;

class Employee implements Serializable {
    private static final long serialVersionUID = 1L;   // version marker, explained below

    private String name;
    private int age;
    private transient String temporarySessionToken;   // `transient` -- excluded from serialization

    public Employee(String name, int age, String temporarySessionToken) {
        this.name = name;
        this.age = age;
        this.temporarySessionToken = temporarySessionToken;
    }

    @Override
    public String toString() {
        return "Employee{name='" + name + "', age=" + age + ", token=" + temporarySessionToken + "}";
    }
}

public class SerializationDemo {
    public static void main(String[] args) throws IOException, ClassNotFoundException {
        Employee original = new Employee("Ada Lovelace", 30, "secret-session-abc123");
        String path = "employee.ser";

        // Serialize: object -> byte stream -> file
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(path))) {
            out.writeObject(original);
        }

        // Deserialize: file -> byte stream -> reconstructed object
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(path))) {
            Employee restored = (Employee) in.readObject();
            System.out.println("Restored: " + restored);
            // token will print as "null" -- `transient` fields are never serialized, so they come back as
            // the type's default value (null for objects, 0/false for primitives) on deserialization.
        }

        new File(path).delete();
    }
}
```

--> **`transient` keyword** -- marks a field to be SKIPPED during serialization (useful for sensitive data like session tokens/passwords, or for fields that are cheap to recompute and needn't be persisted, like a cached hash). Deserialized instances get that field's default value.
--> **`serialVersionUID`** -- a version identifier for the serialized form of a class. If you deserialize an object using a DIFFERENT class version than the one that serialized it (fields added/removed), and the `serialVersionUID`s don't match, Java throws `InvalidClassException`. Explicitly declaring it (rather than letting the JVM auto-generate one from the class structure) gives you control over exactly when that check should fail vs. silently accept the mismatch.
--> **All fields must be serializable too** -- if `Employee` had a field of a non-`Serializable` type, attempting to serialize it throws `NotSerializableException` at runtime, not compile time. Every referenced object in the graph must (transitively) also be `Serializable`, unless marked `transient`.
--> **Security and modern caveats** -- Java's built-in serialization has a well-documented history of security vulnerabilities (deserializing untrusted data can trigger arbitrary code execution via "gadget chains"), and it's now widely considered legacy for anything crossing a trust boundary. Modern systems generally prefer explicit formats like JSON (Jackson/Gson, covered in the JSON theory file) or Protocol Buffers for serialization that touches external input.

# Common Gotchas

--> **Forgetting to close streams** -- unclosed streams leak OS file handles; a long-running program that repeatedly opens files without closing them will eventually hit "too many open files." Always use try-with-resources.
--> **Mixing byte and character streams incorrectly** -- reading a UTF-8 text file with raw byte-by-byte logic and treating each byte as one character corrupts any multi-byte character (accented letters, emoji, non-Latin scripts).
--> **`mkdir()` silently failing** -- returns `false` instead of throwing when parent directories are missing; easy to miss if the return value isn't checked.
--> **Platform-dependent line separators** -- hardcoding `"\n"` in `write()` calls is not portable across Windows (`\r\n`) vs Unix (`\n`); `BufferedWriter.newLine()` or `System.lineSeparator()` handle this correctly.
--> **Not specifying character encoding explicitly** -- `new FileReader(path)` (pre-Java 11) uses the JVM's DEFAULT platform encoding, which varies by OS/locale -- code that works on one machine can silently misread text on another. Java 11+ added `FileReader(File, Charset)` constructors specifically to fix this; prefer explicitly specifying `StandardCharsets.UTF_8`.

# Best Practices Summary

--> Always wrap `FileReader`/`FileWriter`/`FileInputStream`/`FileOutputStream` in their buffered counterparts for anything beyond trivial single-value reads.
--> Always use try-with-resources for streams -- never rely on manual `close()` calls in `finally`.
--> Prefer character streams (`Reader`/`Writer`) for text, byte streams (`InputStream`/`OutputStream`) for binary data -- don't mix the mental models.
--> Explicitly specify character encoding (`StandardCharsets.UTF_8`) rather than relying on platform defaults.
--> Treat Java's native `Serializable` as legacy/internal-use-only; avoid it for data crossing trust or version boundaries -- prefer explicit formats like JSON for that.
--> For new code, prefer `java.nio.file.Files` (next Theory file) over raw `java.io.File`/streams where possible -- better exceptions, more capability, less boilerplate.
