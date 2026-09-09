/*
 * FileIoWithJavaIo.java
 *
 * Demonstrates, using files written to the system temp directory (auto-cleaned at the end
 * so this program is safe to run repeatedly without leaving artifacts behind):
 *   1. java.io.File -- path reference vs actual disk existence, mkdir vs mkdirs
 *   2. Byte streams vs character streams
 *   3. Buffered streams (BufferedReader/BufferedWriter) for line-based I/O
 *   4. Copying a file byte-by-byte with try-with-resources
 *   5. Serialization / deserialization, including `transient` fields
 *
 * Covers Theory chapter:
 *   10) Java/04) Exception Handling and IO/Theory/03 File IO with java.io.md
 *
 * Compile: javac 03_file_io_with_java_io.java
 * Run:     java FileIoWithJavaIo
 */

import java.io.*;
import java.nio.file.Files;

public class FileIoWithJavaIo {

    // A small Serializable class used in the serialization demo
    static class Employee implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String name;
        private final int age;
        private final transient String sessionToken;   // transient -- excluded from serialization

        Employee(String name, int age, String sessionToken) {
            this.name = name;
            this.age = age;
            this.sessionToken = sessionToken;
        }

        @Override
        public String toString() {
            return "Employee{name='" + name + "', age=" + age + ", sessionToken=" + sessionToken + "}";
        }
    }

    public static void main(String[] args) throws IOException {
        // Use a dedicated temp working directory so the whole demo is self-contained and safely re-runnable
        File workDir = Files.createTempDirectory("java-io-demo").toFile();
        System.out.println("Working directory for this run: " + workDir.getAbsolutePath());

        try {
            printSection("1) java.io.File -- reference vs reality, mkdir vs mkdirs");
            demoFileClass(workDir);

            printSection("2) Byte streams vs character streams");
            demoByteVsCharacterStreams(workDir);

            printSection("3) Buffered streams for line-based I/O");
            demoBufferedStreams(workDir);

            printSection("4) Copying a file with try-with-resources");
            demoFileCopy(workDir);

            printSection("5) Serialization and deserialization");
            demoSerialization(workDir);

            System.out.println("\nAll java.io demos completed.");
        } finally {
            deleteRecursively(workDir);
            System.out.println("Cleaned up temp directory: " + workDir.getAbsolutePath());
        }
    }

    // -------------------------------------------------------------------
    static void demoFileClass(File workDir) throws IOException {
        File file = new File(workDir, "example.txt");
        System.out.println("Exists before creation? " + file.exists());   // false -- File object is just a reference

        boolean created = file.createNewFile();
        System.out.println("createNewFile() returned: " + created);
        System.out.println("Exists after creation?  " + file.exists());
        System.out.println("Is file: " + file.isFile() + "  Is directory: " + file.isDirectory());
        System.out.println("Length in bytes: " + file.length());

        // mkdir() vs mkdirs()
        File singleDir = new File(workDir, "single-dir");
        System.out.println("mkdir() on existing-parent dir: " + singleDir.mkdir());

        File deepDirWithoutParents = new File(workDir, "missing-parent/child/grandchild");
        System.out.println("mkdir() when parents are missing (expected false): " + deepDirWithoutParents.mkdir());
        System.out.println("mkdirs() when parents are missing (expected true): " + deepDirWithoutParents.mkdirs());
    }

    // -------------------------------------------------------------------
    static void demoByteVsCharacterStreams(File workDir) throws IOException {
        File file = new File(workDir, "stream-demo.txt");

        // Character stream write -- text-aware
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("Hello, streams!\nSecond line.");
        }

        // Character stream read
        try (FileReader reader = new FileReader(file)) {
            StringBuilder sb = new StringBuilder();
            int c;
            while ((c = reader.read()) != -1) {
                sb.append((char) c);
            }
            System.out.println("Character stream content:\n" + sb);
        }

        // Byte stream read -- raw bytes, no text interpretation
        try (FileInputStream in = new FileInputStream(file)) {
            int byteCount = 0;
            while (in.read() != -1) {
                byteCount++;
            }
            System.out.println("Total raw bytes read: " + byteCount);
        }
    }

    // -------------------------------------------------------------------
    static void demoBufferedStreams(File workDir) throws IOException {
        File file = new File(workDir, "buffered-demo.txt");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            for (int i = 1; i <= 5; i++) {
                writer.write("Line " + i);
                writer.newLine();   // platform-correct line separator
            }
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                System.out.println("  [" + lineNumber + "] " + line);
            }
        }
    }

    // -------------------------------------------------------------------
    static void demoFileCopy(File workDir) throws IOException {
        File source = new File(workDir, "source.txt");
        File dest = new File(workDir, "dest.txt");

        try (FileWriter fw = new FileWriter(source)) {
            fw.write("Content to be copied byte by byte.");
        }

        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(dest)) {
            int b;
            int copiedBytes = 0;
            while ((b = in.read()) != -1) {
                out.write(b);
                copiedBytes++;
            }
            System.out.println("Copied " + copiedBytes + " bytes from " + source.getName() + " to " + dest.getName());
        }

        // verify
        try (BufferedReader reader = new BufferedReader(new FileReader(dest))) {
            System.out.println("Destination content: " + reader.readLine());
        }
    }

    // -------------------------------------------------------------------
    static void demoSerialization(File workDir) throws IOException {
        File file = new File(workDir, "employee.ser");
        Employee original = new Employee("Ada Lovelace", 30, "secret-session-abc123");
        System.out.println("Original:  " + original);

        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(file))) {
            out.writeObject(original);
        }
        System.out.println("Serialized to: " + file.getName() + " (" + file.length() + " bytes)");

        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(file))) {
            Employee restored = (Employee) in.readObject();
            System.out.println("Restored:  " + restored);
            System.out.println("(Notice sessionToken came back null -- `transient` fields are never serialized)");
        } catch (ClassNotFoundException e) {
            System.out.println("Class not found during deserialization: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------
    static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }

    static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }
}
