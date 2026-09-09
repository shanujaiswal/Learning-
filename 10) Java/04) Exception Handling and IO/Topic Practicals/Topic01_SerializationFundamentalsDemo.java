/*
 * Topic01_SerializationFundamentalsDemo.java
 *
 * NOTE ON FILENAME: the public class is named "Topic01_SerializationFundamentalsDemo" (Java
 * identifiers can't start with a digit) while the FILE keeps the "01_..." numeric prefix used
 * throughout this repo for ordering.
 *
 * Compile: javac 01_SerializationFundamentalsDemo.java
 * Run:     java Topic01_SerializationFundamentalsDemo
 *
 * Demonstrates:
 *   1. A Serializable class round-tripped through a ByteArrayOutputStream/ByteArrayInputStream
 *      (no real file I/O needed -- serialized bytes live entirely in memory).
 *   2. transient field behavior -- excluded fields come back at their type's default value.
 *   3. Default serialization skipping constructors (final fields still get set via reflection).
 *   4. serialVersionUID mismatch causing InvalidClassException -- genuinely demonstrated in a
 *      single file by writing bytes tagged with one serialVersionUID, then patching the raw
 *      byte array in memory to swap in a DIFFERENT UID before handing it to a class loaded with
 *      the original UID, so ObjectInputStream detects a mismatch exactly like a real cross-build
 *      incompatibility would.
 *
 * Covers Theory chapter:
 *   04) Exception Handling and IO/Theory/06 Java Serialization Fundamentals.md
 */

import java.io.*;
import java.lang.reflect.Field;
import java.util.Arrays;

public class Topic01_SerializationFundamentalsDemo {

    public static void main(String[] args) throws Exception {
        demoBasicRoundTrip();
        demoTransientFieldBehavior();
        demoConstructorBypassOnDeserialization();
        demoSerialVersionUidMismatch();
        System.out.println("\nAll serialization-fundamentals demos completed.");
    }

    // -------------------------------------------------------------------
    // 1) Basic Serializable round trip via ByteArrayOutputStream/ByteArrayInputStream
    // -------------------------------------------------------------------
    static class Point implements Serializable {
        private static final long serialVersionUID = 1L;
        private final int x;
        private final int y;

        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public String toString() {
            return "Point(" + x + ", " + y + ")";
        }
    }

    private static void demoBasicRoundTrip() throws IOException, ClassNotFoundException {
        printSection("1) Basic Serializable round trip (in-memory bytes, no file I/O)");

        Point original = new Point(3, 7);

        // Serialize into an in-memory byte buffer instead of a real file.
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(buffer)) {
            out.writeObject(original);
        }
        byte[] bytes = buffer.toByteArray();
        System.out.println("Serialized " + original + " into " + bytes.length + " bytes.");

        // Deserialize back from those same bytes.
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            Point restored = (Point) in.readObject();
            System.out.println("Deserialized: " + restored);
            System.out.println("Same values? " + (original.toString().equals(restored.toString())));
            System.out.println("Different instances? " + (original != restored));
        }
    }

    // -------------------------------------------------------------------
    // 2) transient field behavior
    // -------------------------------------------------------------------
    static class UserSession implements Serializable {
        private static final long serialVersionUID = 1L;
        private String username;
        private transient String authToken;   // must never hit the byte stream

        UserSession(String username, String authToken) {
            this.username = username;
            this.authToken = authToken;
        }

        @Override
        public String toString() {
            return "UserSession{username='" + username + "', authToken=" + authToken + "}";
        }
    }

    private static byte[] serialize(Object o) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(buffer)) {
            out.writeObject(o);
        }
        return buffer.toByteArray();
    }

    private static Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return in.readObject();
        }
    }

    private static void demoTransientFieldBehavior() throws IOException, ClassNotFoundException {
        printSection("2) transient field behavior");

        UserSession original = new UserSession("ada", "secret-token-abc123");
        System.out.println("Before serialization: " + original);

        byte[] bytes = serialize(original);
        UserSession restored = (UserSession) deserialize(bytes);

        System.out.println("After round trip:      " + restored);
        System.out.println("authToken survived the round trip? " + (restored.authToken != null));
        System.out.println("(transient fields always come back null/0/false -- never re-populated");
        System.out.println("automatically; the caller must re-derive or re-fetch them manually.)");
    }

    // -------------------------------------------------------------------
    // 3) Default serialization bypasses constructors, but a non-Serializable
    //    superclass's no-arg constructor DOES run.
    // -------------------------------------------------------------------
    static class Base {
        protected String label;
        Base() {
            label = "default-from-Base-ctor";
            System.out.println("  Base() constructor ran (non-Serializable superclass)");
        }
    }

    static class Derived extends Base implements Serializable {
        private static final long serialVersionUID = 1L;
        private final int value; // final field, yet still gets set on deserialization

        Derived(int value) {
            System.out.println("  Derived(int) constructor ran (only during normal 'new')");
            this.value = value;
            this.label = "explicitly set at construction";
        }

        @Override
        public String toString() {
            return "Derived{value=" + value + ", label='" + label + "'}";
        }
    }

    private static void demoConstructorBypassOnDeserialization() throws IOException, ClassNotFoundException {
        printSection("3) Constructors are bypassed on deserialization (except non-Serializable ancestor)");

        System.out.println("Constructing original via 'new':");
        Derived original = new Derived(42);
        original.label = "mutated after construction";
        System.out.println("original = " + original);

        byte[] bytes = serialize(original);

        System.out.println("\nDeserializing (watch which constructor prints fire):");
        Derived restored = (Derived) deserialize(bytes);
        System.out.println("restored = " + restored);
        System.out.println("Notice: Derived(int) did NOT run again (no 'Derived(int) constructor ran' line above),");
        System.out.println("but Base() DID run. 'value' (declared directly in Derived, a Serializable class) is");
        System.out.println("correctly restored via reflection despite being final and despite no constructor");
        System.out.println("call. 'label' comes back as Base's default-from-ctor value, NOT the mutated value it");
        System.out.println("held at serialization time -- because 'label' is declared in Base, and Base is NOT");
        System.out.println("Serializable, so only Base's no-arg constructor (re-)establishes that state; it is");
        System.out.println("never written to or read from the byte stream at all.");
    }

    // -------------------------------------------------------------------
    // 4) serialVersionUID mismatch -> InvalidClassException
    // -------------------------------------------------------------------
    // A single class definition, loaded once by this JVM, with serialVersionUID = 100L.
    static class Config implements Serializable {
        private static final long serialVersionUID = 100L;
        private String environment;

        Config(String environment) {
            this.environment = environment;
        }

        @Override
        public String toString() {
            return "Config{environment='" + environment + "'}";
        }
    }

    /**
     * Genuinely demonstrates the UID-mismatch failure mode within a single file/JVM:
     * we serialize a real Config instance (UID = 100L baked into the stream), then
     * locate the 8-byte big-endian serialVersionUID field inside the raw serialized
     * bytes and overwrite it with a different value (999L) -- exactly simulating what
     * the bytes would look like had they been written by a DIFFERENT build of Config
     * with a different declared UID. Reading those tampered bytes back with the class
     * actually loaded here (UID = 100L) reproduces the real InvalidClassException a
     * cross-version deployment would hit.
     */
    private static void demoSerialVersionUidMismatch() throws Exception {
        printSection("4) serialVersionUID mismatch -> InvalidClassException");

        Config original = new Config("production");
        byte[] bytes = serialize(original);

        // Locate the 8-byte serialVersionUID in the class descriptor and patch it.
        long currentUid = getDeclaredSerialVersionUid(Config.class);
        byte[] uidBytes = longToBytes(currentUid);
        int index = indexOf(bytes, uidBytes);
        if (index < 0) {
            throw new IllegalStateException("Could not locate serialVersionUID bytes in the stream");
        }

        byte[] tampered = bytes.clone();
        byte[] fakeUidBytes = longToBytes(999L); // simulate "some other build" with UID = 999L
        System.arraycopy(fakeUidBytes, 0, tampered, index, 8);

        System.out.println("Original stream carries serialVersionUID = " + currentUid);
        System.out.println("Tampered stream now carries serialVersionUID = 999 (simulating a different build)");
        System.out.println("Local Config.class still declares serialVersionUID = " + currentUid);

        try {
            deserialize(tampered);
            System.out.println("UNEXPECTED: deserialization succeeded (should not happen)");
        } catch (InvalidClassException e) {
            System.out.println("\nCaught expected InvalidClassException:");
            System.out.println("  " + e.getMessage());
            System.out.println("This is exactly the failure a real class recompiled/rebuilt with a different");
            System.out.println("serialVersionUID would produce when reading old data -- always declare the UID");
            System.out.println("explicitly and bump it deliberately, not by accident.");
        }
    }

    private static long getDeclaredSerialVersionUid(Class<?> clazz) throws Exception {
        Field f = clazz.getDeclaredField("serialVersionUID");
        f.setAccessible(true);
        return f.getLong(null);
    }

    private static byte[] longToBytes(long value) {
        byte[] result = new byte[8];
        for (int i = 7; i >= 0; i--) {
            result[i] = (byte) (value & 0xFF);
            value >>= 8;
        }
        return result;
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    // -------------------------------------------------------------------
    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }
}
