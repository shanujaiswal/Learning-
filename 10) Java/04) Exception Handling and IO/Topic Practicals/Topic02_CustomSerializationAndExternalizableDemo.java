/*
 * Topic02_CustomSerializationAndExternalizableDemo.java
 *
 * NOTE ON FILENAME: the public class is named "Topic02_CustomSerializationAndExternalizableDemo"
 * (Java identifiers can't start with a digit) while the FILE keeps the "02_..." numeric prefix
 * used throughout this repo for ordering.
 *
 * Compile: javac 02_CustomSerializationAndExternalizableDemo.java
 * Run:     java Topic02_CustomSerializationAndExternalizableDemo
 *
 * Demonstrates:
 *   1. Custom writeObject/readObject augmenting default serialization (encrypting a transient
 *      field manually while defaultWriteObject()/defaultReadObject() handle the rest).
 *   2. Externalizable -- full manual control, public no-arg constructor requirement, explicit
 *      field-order discipline.
 *   3. writeReplace/readResolve for singleton-safe serialization (a broken singleton fixed by
 *      readResolve, contrasted with a correctly-protected one).
 *
 * Covers Theory chapter:
 *   04) Exception Handling and IO/Theory/07 Custom Serialization and Externalizable.md
 */

import java.io.*;

public class Topic02_CustomSerializationAndExternalizableDemo {

    public static void main(String[] args) throws Exception {
        demoCustomWriteReadObject();
        demoExternalizable();
        demoReadResolveSingletonProtection();
        demoBrokenSingletonWithoutReadResolve();
        System.out.println("\nAll custom-serialization/Externalizable demos completed.");
    }

    // -------------------------------------------------------------------
    // Shared helpers
    // -------------------------------------------------------------------
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

    // -------------------------------------------------------------------
    // 1) Custom writeObject / readObject
    // -------------------------------------------------------------------
    static class BankAccount implements Serializable {
        private static final long serialVersionUID = 1L;

        private String accountHolder;
        private double balance;
        private transient String encryptedPin; // handled manually, not by default mechanism

        BankAccount(String accountHolder, double balance, String pin) {
            this.accountHolder = accountHolder;
            this.balance = balance;
            this.encryptedPin = encrypt(pin);
        }

        private static String encrypt(String raw) {
            return "ENC[" + raw + "]"; // stand-in for real encryption
        }

        private void writeObject(ObjectOutputStream out) throws IOException {
            out.defaultWriteObject();     // writes accountHolder, balance (non-transient fields)
            out.writeUTF(encryptedPin);   // manually write the transient field
        }

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();               // restores accountHolder, balance
            this.encryptedPin = in.readUTF();      // manually restore the transient field
            if (balance < 0) {
                // readObject is exactly where you'd re-validate invariants a constructor would
                // normally enforce, since deserialization bypasses the constructor entirely.
                throw new InvalidObjectException("balance must not be negative");
            }
        }

        @Override
        public String toString() {
            return "BankAccount{holder='" + accountHolder + "', balance=" + balance
                    + ", pin=" + encryptedPin + "}";
        }
    }

    private static void demoCustomWriteReadObject() throws Exception {
        printSection("1) Custom writeObject/readObject (augmenting default serialization)");

        BankAccount original = new BankAccount("Ada Lovelace", 1500.0, "4471");
        System.out.println("Original: " + original);

        byte[] bytes = serialize(original);
        BankAccount restored = (BankAccount) deserialize(bytes);
        System.out.println("Restored: " + restored);
        System.out.println("encryptedPin (transient) correctly round-tripped via manual write/read in");
        System.out.println("writeObject()/readObject(), even though it's marked transient.");
    }

    // -------------------------------------------------------------------
    // 2) Externalizable -- full manual control
    // -------------------------------------------------------------------
    static class NetworkPacket implements Externalizable {
        private int sequenceNumber;
        private String payload;
        private long timestamp;

        // Externalizable REQUIRES a public no-arg constructor -- the JVM calls it via reflection
        // BEFORE readExternal() runs. This exists ONLY for the serialization framework.
        public NetworkPacket() {
        }

        NetworkPacket(int sequenceNumber, String payload, long timestamp) {
            this.sequenceNumber = sequenceNumber;
            this.payload = payload;
            this.timestamp = timestamp;
        }

        @Override
        public void writeExternal(ObjectOutput out) throws IOException {
            out.writeInt(sequenceNumber);
            out.writeUTF(payload);
            out.writeLong(timestamp);
            // Nothing is automatic -- forgetting a field here silently drops it forever.
        }

        @Override
        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            sequenceNumber = in.readInt();
            payload = in.readUTF();
            timestamp = in.readLong();
            // Must read back in the EXACT SAME ORDER they were written.
        }

        @Override
        public String toString() {
            return "NetworkPacket{seq=" + sequenceNumber + ", payload='" + payload + "', ts=" + timestamp + "}";
        }
    }

    private static void demoExternalizable() throws Exception {
        printSection("2) Externalizable -- full manual control");

        NetworkPacket original = new NetworkPacket(42, "hello", 1_700_000_000_000L);
        System.out.println("Original: " + original);

        byte[] bytes = serialize(original);
        System.out.println("Serialized size: " + bytes.length + " bytes (no reflective field metadata --");
        System.out.println("just the class name/UID header plus exactly what writeExternal() wrote).");

        NetworkPacket restored = (NetworkPacket) deserialize(bytes);
        System.out.println("Restored: " + restored);
        System.out.println("Restored via: public no-arg constructor + readExternal() -- NOT low-level");
        System.out.println("field allocation like default Serializable deserialization uses.");
    }

    // -------------------------------------------------------------------
    // 3) readResolve -- protecting singleton semantics across deserialization
    // -------------------------------------------------------------------
    static class AppConfig implements Serializable {
        private static final long serialVersionUID = 1L;
        private static final AppConfig INSTANCE = new AppConfig();

        private String environment = "production";

        private AppConfig() {
        }

        static AppConfig getInstance() {
            return INSTANCE;
        }

        // Without this method, deserializing a stream containing an AppConfig would silently
        // produce a SECOND instance, distinct from INSTANCE, breaking the singleton guarantee.
        private Object readResolve() throws ObjectStreamException {
            return INSTANCE;
        }

        @Override
        public String toString() {
            return "AppConfig@" + Integer.toHexString(System.identityHashCode(this))
                    + "{environment='" + environment + "'}";
        }
    }

    private static void demoReadResolveSingletonProtection() throws Exception {
        printSection("3) readResolve -- singleton survives deserialization");

        AppConfig singleton = AppConfig.getInstance();
        System.out.println("Original singleton instance: " + singleton);

        byte[] bytes = serialize(singleton);
        AppConfig restored = (AppConfig) deserialize(bytes);
        System.out.println("Deserialized instance:       " + restored);

        System.out.println("Same instance (singleton preserved)? " + (singleton == restored));
        System.out.println("This works because readResolve() swaps in AppConfig.INSTANCE right after");
        System.out.println("the JVM's low-level deserialization machinery finishes populating a throwaway");
        System.out.println("object -- the caller only ever sees the canonical instance.");
    }

    // -------------------------------------------------------------------
    // Contrast: a singleton WITHOUT readResolve -- the guarantee silently breaks.
    // -------------------------------------------------------------------
    static class BrokenSingleton implements Serializable {
        private static final long serialVersionUID = 1L;
        private static final BrokenSingleton INSTANCE = new BrokenSingleton();

        private BrokenSingleton() {
        }

        static BrokenSingleton getInstance() {
            return INSTANCE;
        }
        // No readResolve() here on purpose -- this is the broken case.
    }

    private static void demoBrokenSingletonWithoutReadResolve() throws Exception {
        printSection("3b) Contrast -- singleton WITHOUT readResolve (guarantee breaks)");

        BrokenSingleton singleton = BrokenSingleton.getInstance();
        byte[] bytes = serialize(singleton);
        BrokenSingleton restored = (BrokenSingleton) deserialize(bytes);

        System.out.println("Same instance? " + (singleton == restored));
        System.out.println("Without readResolve(), deserialization allocates a brand-new instance via");
        System.out.println("low-level mechanisms, bypassing the private constructor entirely -- the");
        System.out.println("'only one instance ever exists' invariant is silently violated.");
    }

    // -------------------------------------------------------------------
    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }
}
