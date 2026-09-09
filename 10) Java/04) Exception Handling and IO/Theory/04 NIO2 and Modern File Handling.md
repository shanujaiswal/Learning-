# Why NIO.2 Exists

--> Java 7 introduced `java.nio.file` (informally "NIO.2," distinct from the original `java.nio` buffer/channel API from Java 1.4) specifically to fix long-standing pain points in `java.io.File`: silent `boolean` failures instead of exceptions, no symbolic link support, no filesystem metadata beyond the basics, and no efficient way to walk large directory trees. NIO.2 is now the RECOMMENDED way to do file I/O in modern Java for anything beyond the simplest cases.
--> The core building blocks are **`Path`** (replaces `File` as the representation of a filesystem location) and **`Files`** (a utility class of static methods that DO the actual work -- reading, writing, copying, moving, walking, watching).

# `Path` and `Paths` / `Path.of`

--> A `Path` represents a location in a filesystem -- like `File`, it can point to something that doesn't exist yet. Unlike `File`, `Path` is an INTERFACE, is immutable, and is far richer in the operations it exposes directly.

```java
import java.nio.file.Path;
import java.nio.file.Paths;

public class PathBasicsDemo {
    public static void main(String[] args) {
        // Two equivalent ways to create a Path -- Path.of() is preferred since Java 11 (Paths.get() is older, still valid)
        Path p1 = Paths.get("data", "reports", "2024", "summary.txt");
        Path p2 = Path.of("data/reports/2024/summary.txt");

        System.out.println("File name: " + p1.getFileName());
        System.out.println("Parent: " + p1.getParent());
        System.out.println("Root: " + p1.getRoot());
        System.out.println("Number of path elements: " + p1.getNameCount());
        System.out.println("Element 0: " + p1.getName(0));
        System.out.println("Is absolute? " + p1.isAbsolute());
        System.out.println("Absolute form: " + p1.toAbsolutePath());

        // Combining paths
        Path base = Path.of("/home/user");
        Path combined = base.resolve("documents/notes.txt");
        System.out.println("Resolved: " + combined);

        // Normalizing away redundant ".." and "." segments
        Path messy = Path.of("/a/b/../c/./d");
        System.out.println("Normalized: " + messy.normalize());   // /a/c/d

        // Relativizing -- expressing one path relative to another
        Path from = Path.of("/a/b");
        Path to = Path.of("/a/b/c/d");
        System.out.println("Relative: " + from.relativize(to));   // c/d
    }
}
```

--> **`Path` vs `File` interop** -- legacy code using `File` can convert either direction: `file.toPath()` and `path.toFile()`, so `Path`-based code can still call into older `File`-based APIs when needed.

# Reading and Writing Files -- the Modern Way

--> The `Files` class provides one-liner convenience methods for the most common operations, eliminating most of the manual stream-wiring boilerplate that `java.io` required.

```java
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.io.IOException;

public class ModernReadWriteDemo {
    public static void main(String[] args) throws IOException {
        Path path = Path.of("nio-demo.txt");

        // --- Writing ---
        Files.writeString(path, "Hello from NIO.2!\n", StandardCharsets.UTF_8);          // Java 11+, overwrites by default
        Files.write(path, List.of("Line A", "Line B", "Line C"),
                    StandardOpenOption.APPEND);                                          // append a list of lines

        // --- Reading ---
        String wholeFile = Files.readString(path, StandardCharsets.UTF_8);               // Java 11+, whole file as one String
        System.out.println("--- readString() ---\n" + wholeFile);

        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);           // whole file as List<String>
        System.out.println("--- readAllLines() -- " + lines.size() + " lines ---");
        lines.forEach(System.out::println);

        byte[] rawBytes = Files.readAllBytes(path);                                       // whole file as byte[]
        System.out.println("--- Total bytes: " + rawBytes.length + " ---");

        // --- Streaming lines lazily (better for large files -- doesn't load everything into memory) ---
        try (var lineStream = Files.lines(path, StandardCharsets.UTF_8)) {
            long lineCount = lineStream.count();
            System.out.println("Line count via stream: " + lineCount);
        }

        Files.deleteIfExists(path);
    }
}
```

--> **`readAllLines`/`readString`/`readAllBytes` load the ENTIRE file into memory** -- perfectly fine for config files and moderate text files, but a poor choice for huge files (multi-GB logs). `Files.lines()` returns a lazily-evaluated `Stream<String>` that reads incrementally and MUST be closed (hence try-with-resources -- `Stream` implements `AutoCloseable` specifically because of cases like this).

# File and Directory Operations

```java
import java.nio.file.*;
import java.io.IOException;
import java.util.stream.Stream;

public class FileOperationsDemo {
    public static void main(String[] args) throws IOException {
        Path dir = Path.of("nio-sandbox");
        Files.createDirectories(dir);   // like mkdirs() but THROWS IOException on failure instead of returning false

        Path fileA = dir.resolve("a.txt");
        Path fileB = dir.resolve("b.txt");
        Files.writeString(fileA, "content A");

        // Existence and metadata checks
        System.out.println("Exists: " + Files.exists(fileA));
        System.out.println("Is regular file: " + Files.isRegularFile(fileA));
        System.out.println("Is directory: " + Files.isDirectory(dir));
        System.out.println("Size: " + Files.size(fileA) + " bytes");
        System.out.println("Last modified: " + Files.getLastModifiedTime(fileA));

        // Copy and move
        Files.copy(fileA, fileB, StandardCopyOption.REPLACE_EXISTING);
        Path renamed = dir.resolve("a-renamed.txt");
        Files.move(fileA, renamed, StandardCopyOption.ATOMIC_MOVE);

        // Listing directory contents -- Files.list() returns a lazy Stream, must be closed
        try (Stream<Path> entries = Files.list(dir)) {
            entries.forEach(System.out::println);
        }

        // Recursively walking an entire directory tree
        try (Stream<Path> walk = Files.walk(dir)) {
            long totalFiles = walk.filter(Files::isRegularFile).count();
            System.out.println("Total regular files under tree: " + totalFiles);
        }

        // Cleanup -- must delete files before their parent directory
        Files.deleteIfExists(renamed);
        Files.deleteIfExists(fileB);
        Files.deleteIfExists(dir);
    }
}
```

--> **`Files` methods throw checked `IOException` on real failure**, rather than returning misleading booleans like much of `java.io.File` does -- `Files.createDirectories()` either succeeds or throws, it never silently does nothing.
--> **`Files.list()` and `Files.walk()` are LAZY streams backed by an open directory handle** -- they must be closed (try-with-resources) or the underlying OS resource leaks. This is a common gotcha: forgetting the try-with-resources on `Files.walk()` works fine in small test programs but leaks handles in long-running processes.

# Symbolic Links and File Attributes

--> NIO.2 added first-class support for symbolic links and rich, filesystem-specific attribute views that `java.io.File` never exposed.

```java
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.io.IOException;

public class AttributesAndLinksDemo {
    public static void main(String[] args) throws IOException {
        Path target = Path.of("link-target.txt");
        Files.writeString(target, "I am the real file.");

        Path link = Path.of("link-to-target.txt");
        try {
            Files.createSymbolicLink(link, target.toAbsolutePath());
            System.out.println("Is symbolic link: " + Files.isSymbolicLink(link));
            System.out.println("Link resolves to: " + Files.readSymbolicLink(link));
        } catch (UnsupportedOperationException | IOException e) {
            // Symlink creation can require elevated privileges on Windows -- handle gracefully
            System.out.println("Symlink creation not permitted in this environment: " + e.getMessage());
        }

        // Rich POSIX/basic file attributes in one round-trip (cheaper than separate calls)
        BasicFileAttributes attrs = Files.readAttributes(target, BasicFileAttributes.class);
        System.out.println("Creation time: " + attrs.creationTime());
        System.out.println("Size: " + attrs.size());
        System.out.println("Is directory: " + attrs.isDirectory());
        System.out.println("Is regular file: " + attrs.isRegularFile());

        Files.deleteIfExists(link);
        Files.deleteIfExists(target);
    }
}
```

# Watching a Directory for Changes (`WatchService`)

--> `WatchService` lets a program subscribe to filesystem change EVENTS (create/modify/delete) for a directory, instead of manually polling `Files.list()` in a loop. Useful for things like hot-reloading config files, or processing files as they're dropped into an "inbox" folder.

```java
import java.nio.file.*;
import java.io.IOException;

public class WatchServiceDemo {
    public static void main(String[] args) throws IOException, InterruptedException {
        Path watchedDir = Files.createTempDirectory("watch-demo");

        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            watchedDir.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);

            // Trigger a filesystem event from a separate thread so main can poll for it
            Thread producer = new Thread(() -> {
                try {
                    Thread.sleep(200);
                    Files.writeString(watchedDir.resolve("new-file.txt"), "hello watcher");
                } catch (Exception ignored) { }
            });
            producer.start();

            // poll() with a timeout -- unlike take(), it does not block forever if no event ever arrives
            WatchKey key = watchService.poll(2, java.util.concurrent.TimeUnit.SECONDS);
            if (key != null) {
                for (WatchEvent<?> event : key.pollEvents()) {
                    System.out.println("Event kind: " + event.kind() + "  context: " + event.context());
                }
                key.reset();   // IMPORTANT -- must reset the key to keep receiving further events on it
            } else {
                System.out.println("No filesystem event observed within timeout.");
            }
            producer.join();
        } finally {
            // cleanup
            try (var entries = Files.list(watchedDir)) {
                for (Path p : entries.toList()) {
                    Files.deleteIfExists(p);
                }
            }
            Files.deleteIfExists(watchedDir);
        }
    }
}
```

--> **`take()` vs `poll()`** -- `WatchService.take()` blocks indefinitely until an event occurs; `poll()` (with or without a timeout) returns immediately (or after the timeout) with `null` if nothing has happened yet. Real applications typically run the watch loop on a dedicated background thread.
--> **`key.reset()` is easy to forget** -- after processing a `WatchKey`'s events, it MUST be reset, or that key stops receiving further events entirely, silently.

# `Files.walkFileTree` and `FileVisitor` for Fine-Grained Tree Operations

--> For cases needing more control than `Files.walk()`'s flat stream (e.g. skipping entire subtrees, distinguishing pre-visit vs post-visit of a directory, handling visit failures per-file), `Files.walkFileTree` with a `SimpleFileVisitor` gives full control over traversal.

```java
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.io.IOException;

public class FileVisitorDemo {
    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("visitor-demo");
        Files.createDirectories(root.resolve("sub"));
        Files.writeString(root.resolve("top.txt"), "top level");
        Files.writeString(root.resolve("sub/nested.txt"), "nested level");

        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                System.out.println("Entering directory: " + dir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                System.out.println("Visiting file: " + file + " (" + attrs.size() + " bytes)");
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                System.out.println("Failed to visit: " + file + " -- " + exc.getMessage());
                return FileVisitResult.CONTINUE;   // keep going despite this one failure
            }
        });

        // Recursive delete -- walk the tree bottom-up, deleting files then their now-empty directories
        Files.walk(root)
             .sorted(java.util.Comparator.reverseOrder())   // deepest paths first
             .forEach(p -> {
                 try { Files.deleteIfExists(p); } catch (IOException ignored) { }
             });
    }
}
```

# Common Gotchas

--> **Forgetting to close `Files.list()`/`Files.walk()` streams** -- these hold an open directory handle; not closing them (via try-with-resources) leaks OS resources over time in long-running programs.
--> **`Files.move`/`Files.copy` overwrite behavior** -- by default, both FAIL with `FileAlreadyExistsException` if the destination exists; you must explicitly pass `StandardCopyOption.REPLACE_EXISTING` to overwrite.
--> **Deleting a non-empty directory** -- `Files.delete()`/`deleteIfExists()` on a directory that still has contents throws `DirectoryNotEmptyException`; recursive deletion requires walking the tree and deleting deepest-first (as shown above).
--> **`WatchKey.reset()` omission** -- silently stops future events for that key with no exception raised, which can look like "the watcher just stopped working" during debugging.
--> **Relative paths depend on the JVM's working directory** -- `Path.of("data.txt")` resolves relative to wherever the JVM process was launched FROM, which is not always what a developer expects, especially when running from an IDE vs command line.

# `Files` vs `java.io.File` -- When to Use Which

| Scenario | Prefer |
|---|---|
| New code, general file I/O | `java.nio.file.Files` / `Path` |
| Need detailed error information | `Files` (throws specific `IOException` subtypes) |
| Symbolic link handling | `Files` (native support) |
| Watching for filesystem changes | `Files` (`WatchService`) |
| Interop with an old API expecting `java.io.File` | `Path.toFile()` bridges the two |
| Legacy codebase already using `File` throughout | Stay consistent unless doing a larger refactor |

# Best Practices Summary

--> Default to `java.nio.file.Path` and `Files` for all new file-handling code -- richer functionality, clearer exceptions, better resource semantics.
--> Always close streaming `Files` methods (`list`, `walk`, `lines`) via try-with-resources.
--> Use `Files.createDirectories()` over manual `mkdir()`/`mkdirs()` -- it throws on real failure instead of returning a silent `boolean`.
--> Be explicit about copy/move overwrite behavior with `StandardCopyOption`.
--> For directory watching, always call `key.reset()` after processing events, and run the watch loop off the main thread in real applications.
--> Use `Files.readString`/`Files.writeString` for simple whole-file text I/O (Java 11+); reserve `Files.lines()` for files too large to comfortably hold in memory.
