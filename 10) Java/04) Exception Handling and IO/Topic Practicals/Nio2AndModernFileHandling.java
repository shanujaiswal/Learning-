/*
 * Nio2AndModernFileHandling.java
 *
 * Demonstrates, all within an auto-cleaned temp directory:
 *   1. Path / Path.of basics -- resolve, normalize, relativize
 *   2. Modern reading/writing via Files (writeString/readString/readAllLines/write with lines)
 *   3. Files.lines() as a lazily-evaluated stream
 *   4. Directory + file operations: createDirectories, copy, move, list, walk
 *   5. Reading BasicFileAttributes
 *   6. WatchService -- watching a directory for a create event
 *   7. Files.walkFileTree with a FileVisitor, plus a proper deepest-first recursive delete
 *
 * Covers Theory chapter:
 *   10) Java/04) Exception Handling and IO/Theory/04 NIO2 and Modern File Handling.md
 *
 * Compile: javac 04_nio2_and_modern_file_handling.java
 * Run:     java Nio2AndModernFileHandling
 */

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class Nio2AndModernFileHandling {

    public static void main(String[] args) throws IOException, InterruptedException {
        Path workDir = Files.createTempDirectory("nio2-demo");
        System.out.println("Working directory for this run: " + workDir.toAbsolutePath());

        try {
            printSection("1) Path basics -- resolve, normalize, relativize");
            demoPathBasics(workDir);

            printSection("2) Modern reading/writing via Files");
            demoModernReadWrite(workDir);

            printSection("3) Files.lines() as a lazy stream");
            demoLazyLineStream(workDir);

            printSection("4) Directory + file operations");
            demoFileOperations(workDir);

            printSection("5) BasicFileAttributes");
            demoFileAttributes(workDir);

            printSection("6) WatchService -- observing a directory create event");
            demoWatchService();

            printSection("7) Files.walkFileTree with a FileVisitor");
            demoFileVisitor();

            System.out.println("\nAll NIO.2 demos completed.");
        } finally {
            recursiveDelete(workDir);
            System.out.println("Cleaned up temp directory: " + workDir);
        }
    }

    // -------------------------------------------------------------------
    static void demoPathBasics(Path workDir) {
        Path p = Path.of("data", "reports", "2024", "summary.txt");
        System.out.println("Path: " + p);
        System.out.println("File name: " + p.getFileName());
        System.out.println("Parent: " + p.getParent());
        System.out.println("Element count: " + p.getNameCount());
        System.out.println("Is absolute? " + p.isAbsolute());

        Path combined = workDir.resolve("nested/leaf.txt");
        System.out.println("Resolved against workDir: " + combined);

        Path messy = Path.of("a/b/../c/./d");
        System.out.println("Normalized 'a/b/../c/./d': " + messy.normalize());

        Path from = Path.of("/a/b");
        Path to = Path.of("/a/b/c/d");
        System.out.println("Relativize /a/b -> /a/b/c/d: " + from.relativize(to));
    }

    // -------------------------------------------------------------------
    static void demoModernReadWrite(Path workDir) throws IOException {
        Path file = workDir.resolve("nio-demo.txt");

        Files.writeString(file, "Hello from NIO.2!\n", StandardCharsets.UTF_8);
        Files.write(file, List.of("Line A", "Line B", "Line C"), StandardOpenOption.APPEND);

        String whole = Files.readString(file, StandardCharsets.UTF_8);
        System.out.println("--- readString() ---\n" + whole);

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        System.out.println("--- readAllLines(): " + lines.size() + " lines ---");
        lines.forEach(l -> System.out.println("  " + l));

        byte[] rawBytes = Files.readAllBytes(file);
        System.out.println("Total bytes: " + rawBytes.length);
    }

    // -------------------------------------------------------------------
    static void demoLazyLineStream(Path workDir) throws IOException {
        Path file = workDir.resolve("lazy-lines.txt");
        Files.write(file, List.of("one", "two", "three", "four", "five"));

        try (Stream<String> lineStream = Files.lines(file, StandardCharsets.UTF_8)) {
            long count = lineStream.filter(line -> line.length() > 3).count();
            System.out.println("Lines longer than 3 characters: " + count);
        }
        // The stream (and its underlying file handle) is closed automatically here via try-with-resources
    }

    // -------------------------------------------------------------------
    static void demoFileOperations(Path workDir) throws IOException {
        Path sandbox = workDir.resolve("sandbox");
        Files.createDirectories(sandbox);   // throws IOException on real failure, unlike File.mkdirs()'s boolean

        Path fileA = sandbox.resolve("a.txt");
        Path fileB = sandbox.resolve("b.txt");
        Files.writeString(fileA, "content A");

        System.out.println("Exists: " + Files.exists(fileA));
        System.out.println("Size: " + Files.size(fileA) + " bytes");

        Files.copy(fileA, fileB, StandardCopyOption.REPLACE_EXISTING);
        Path renamed = sandbox.resolve("a-renamed.txt");
        Files.move(fileA, renamed, StandardCopyOption.REPLACE_EXISTING);

        System.out.println("Directory listing:");
        try (Stream<Path> entries = Files.list(sandbox)) {
            entries.forEach(p -> System.out.println("  " + p.getFileName()));
        }

        try (Stream<Path> walk = Files.walk(sandbox)) {
            long totalFiles = walk.filter(Files::isRegularFile).count();
            System.out.println("Total regular files under sandbox: " + totalFiles);
        }
    }

    // -------------------------------------------------------------------
    static void demoFileAttributes(Path workDir) throws IOException {
        Path file = workDir.resolve("attrs-demo.txt");
        Files.writeString(file, "attribute inspection target");

        BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
        System.out.println("Size: " + attrs.size());
        System.out.println("Is regular file: " + attrs.isRegularFile());
        System.out.println("Is directory: " + attrs.isDirectory());
        System.out.println("Creation time: " + attrs.creationTime());
    }

    // -------------------------------------------------------------------
    static void demoWatchService() throws IOException, InterruptedException {
        Path watchedDir = Files.createTempDirectory("watch-demo");
        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            watchedDir.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);

            Thread producer = new Thread(() -> {
                try {
                    Thread.sleep(200);
                    Files.writeString(watchedDir.resolve("new-file.txt"), "hello watcher");
                } catch (Exception ignored) {
                    // best-effort demo producer
                }
            });
            producer.start();

            WatchKey key = watchService.poll(3, TimeUnit.SECONDS);   // times out instead of blocking forever
            if (key != null) {
                for (WatchEvent<?> event : key.pollEvents()) {
                    System.out.println("Observed event: " + event.kind() + " on " + event.context());
                }
                key.reset();   // required to keep receiving future events on this key
            } else {
                System.out.println("No filesystem event observed within the timeout window.");
            }
            producer.join();
        } finally {
            recursiveDelete(watchedDir);
        }
    }

    // -------------------------------------------------------------------
    static void demoFileVisitor() throws IOException {
        Path root = Files.createTempDirectory("visitor-demo");
        try {
            Files.createDirectories(root.resolve("sub"));
            Files.writeString(root.resolve("top.txt"), "top level");
            Files.writeString(root.resolve("sub/nested.txt"), "nested level");

            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    System.out.println("Entering directory: " + dir.getFileName());
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    System.out.println("Visiting file: " + file.getFileName() + " (" + attrs.size() + " bytes)");
                    return FileVisitResult.CONTINUE;
                }
            });
        } finally {
            recursiveDelete(root);
        }
    }

    // -------------------------------------------------------------------
    // Deepest-first recursive delete -- files (and now-empty directories) must be
    // removed bottom-up, since Files.delete() refuses to delete a non-empty directory.
    static void recursiveDelete(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                        // best-effort cleanup
                    }
                });
        }
    }

    static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }
}
