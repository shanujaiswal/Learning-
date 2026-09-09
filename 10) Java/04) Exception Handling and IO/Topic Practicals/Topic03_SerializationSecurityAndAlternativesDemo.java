/*
 * Topic03_SerializationSecurityAndAlternativesDemo.java
 *
 * NOTE ON FILENAME: the public class is named "Topic03_SerializationSecurityAndAlternativesDemo"
 * (Java identifiers can't start with a digit) while the FILE keeps the "03_..." numeric prefix
 * used throughout this repo for ordering.
 *
 * Compile: javac 03_SerializationSecurityAndAlternativesDemo.java
 * Run:     java Topic03_SerializationSecurityAndAlternativesDemo
 *
 * Demonstrates:
 *   1. ObjectInputFilter (java.io.ObjectInputFilter, JDK 9+) -- a genuinely runnable allow-list
 *      filter that REJECTS an unexpected class during deserialization with InvalidClassException,
 *      and a second stream where the SAME class IS on the allow-list and succeeds.
 *   2. Illustrative/commented notes on why gadget chains are dangerous (no actual exploit code --
 *      that would require a vulnerable third-party gadget library and is deliberately NOT
 *      reproduced here; the comments explain the mechanism instead).
 *   3. A genuinely runnable, hand-rolled minimal JSON-like writer, contrasted with Java
 *      serialization, to illustrate why a text/schema-target-driven alternative is structurally
 *      safer against arbitrary class instantiation (no external JSON library used).
 *
 * Covers Theory chapter:
 *   04) Exception Handling and IO/Theory/08 Serialization Security and Alternatives.md
 */

import java.io.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class Topic03_SerializationSecurityAndAlternativesDemo {

    public static void main(String[] args) throws Exception {
        demoObjectInputFilterRejectsUnexpectedClass();
        demoObjectInputFilterAllowsExpectedClass();
        explainGadgetChainDanger();
        demoHandRolledJsonAlternative();
        System.out.println("\nAll serialization-security/alternatives demos completed.");
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

    // Two small, otherwise-unremarkable Serializable classes used only to demonstrate that
    // ObjectInputFilter can allow one by name/pattern while rejecting the other.
    static class Employee implements Serializable {
        private static final long serialVersionUID = 1L;
        private String name;

        Employee(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return "Employee{name='" + name + "'}";
        }
    }

    static class UnexpectedPayload implements Serializable {
        private static final long serialVersionUID = 1L;
        private String note = "I should never be allowed through the filter";

        @Override
        public String toString() {
            return "UnexpectedPayload{note='" + note + "'}";
        }
    }

    // -------------------------------------------------------------------
    // 1) ObjectInputFilter rejects a class NOT on the allow-list
    // -------------------------------------------------------------------
    private static void demoObjectInputFilterRejectsUnexpectedClass() throws Exception {
        printSection("1) ObjectInputFilter -- rejects a class NOT on the allow-list");

        // Simulate "attacker" bytes: a stream containing a class the receiver did not expect.
        byte[] unexpectedBytes = serialize(new UnexpectedPayload());

        // Allow-list ONLY this demo's Employee class (by its exact binary/nested name);
        // "!*" means "reject everything else not explicitly matched above".
        String pattern = Topic03_SerializationSecurityAndAlternativesDemo.class.getName() + "$Employee;!*";
        ObjectInputFilter filter = ObjectInputFilter.Config.createFilter(pattern);

        System.out.println("Allow-list pattern: " + pattern);
        System.out.println("Attempting to deserialize an UnexpectedPayload through that filter...");

        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(unexpectedBytes))) {
            in.setObjectInputFilter(filter); // MUST be set BEFORE readObject() -- setting it after is a no-op
            in.readObject();
            System.out.println("UNEXPECTED: deserialization succeeded (should have been rejected)");
        } catch (InvalidClassException e) {
            System.out.println("Caught expected InvalidClassException (filter REJECTED the class):");
            System.out.println("  " + e.getMessage());
            System.out.println("This is exactly how ObjectInputFilter stops a gadget-chain class from ever");
            System.out.println("being instantiated in the first place -- rejection happens before the");
            System.out.println("dangerous readObject()/constructor-adjacent logic of that class can run.");
        }
    }

    // -------------------------------------------------------------------
    // 2) ObjectInputFilter allows a class that IS on the allow-list
    // -------------------------------------------------------------------
    private static void demoObjectInputFilterAllowsExpectedClass() throws Exception {
        printSection("2) ObjectInputFilter -- allows a class that IS on the allow-list");

        byte[] expectedBytes = serialize(new Employee("Ada Lovelace"));

        String pattern = Topic03_SerializationSecurityAndAlternativesDemo.class.getName() + "$Employee;!*";
        ObjectInputFilter filter = ObjectInputFilter.Config.createFilter(pattern);

        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(expectedBytes))) {
            in.setObjectInputFilter(filter);
            Object restored = in.readObject();
            System.out.println("Deserialization SUCCEEDED for an allow-listed class: " + restored);
        }
        System.out.println("Same filter, same pattern -- the ONLY difference is whether the incoming");
        System.out.println("class name matches the allow-list. This is the 'allow-listing beats");
        System.out.println("block-listing' principle: a finite, closed set of expected classes.");
    }

    // -------------------------------------------------------------------
    // 3) Why gadget chains are dangerous -- illustrative notes only, NOT runnable exploit code
    // -------------------------------------------------------------------
    private static void explainGadgetChainDanger() {
        printSection("3) Why gadget chains are dangerous (illustrative notes -- no exploit code here)");

        System.out.println("No actual exploit is executed in this file -- reproducing a real gadget chain");
        System.out.println("would require a genuinely vulnerable third-party library on the classpath.");
        System.out.println("The mechanism, in short (see Theory file 03 for full detail):");
        System.out.println();
        System.out.println("  1. ObjectInputStream.readObject() reads a CLASS NAME directly from the");
        System.out.println("     untrusted byte stream -- the ATTACKER chooses which class gets loaded.");
        System.out.println("  2. The JVM allocates an instance of that class WITHOUT calling any of its");
        System.out.println("     constructors (see Theory file 01), then populates its fields from the");
        System.out.println("     stream, then invokes that object's own readObject()/readResolve()/etc.");
        System.out.println("     if it defines one -- attacker-influenced code runs automatically.");
        System.out.println("  3. A 'gadget' is any class already on the classpath (yours OR a dependency's)");
        System.out.println("     whose deserialization-time behavior does something exploitable in a small");
        System.out.println("     way. A 'gadget chain' links several gadgets together until the chain");
        System.out.println("     reaches something dangerous (Runtime.exec, arbitrary file I/O, reflection");
        System.out.println("     to call an arbitrary method).");
        System.out.println("  4. Tools like 'ysoserial' catalog gadget chains built ENTIRELY from classes");
        System.out.println("     shipped in extremely common libraries (Apache Commons Collections, Spring,");
        System.out.println("     Groovy) -- meaning an application can be exploitable purely because of");
        System.out.println("     dependencies it already has, with NO vulnerable code of its own.");
        System.out.println();
        System.out.println("  // DANGER -- illustrative only, deliberately NOT executed:");
        System.out.println("  // try (ObjectInputStream in = new ObjectInputStream(untrustedSocketStream)) {");
        System.out.println("  //     Object obj = in.readObject();  // attacker chose the class; its");
        System.out.println("  //                                     // readObject() already ran by now");
        System.out.println("  // }");
        System.out.println();
        System.out.println("This is exactly why demos 1 and 2 above matter: ObjectInputFilter closes off");
        System.out.println("step 1 by rejecting any class name not on an explicit allow-list, BEFORE step 2");
        System.out.println("(allocation) ever happens.");
    }

    // -------------------------------------------------------------------
    // 4) Hand-rolled minimal JSON-like writer -- illustrating the "alternative" concept
    // -------------------------------------------------------------------
    // A tiny, dependency-free JSON object writer. Real code should use Jackson/Gson (see Theory
    // file 03) -- this is purely to make the CONCEPT ("caller decides the target type; text has
    // no class-name-driven instantiation mechanism") tangible without pulling in a library.
    static final class SimpleJsonWriter {
        private final StringBuilder sb = new StringBuilder();
        private boolean first = true;

        SimpleJsonWriter() {
            sb.append('{');
        }

        SimpleJsonWriter field(String name, String value) {
            appendSeparator();
            sb.append(quote(name)).append(':').append(quote(value));
            return this;
        }

        SimpleJsonWriter field(String name, int value) {
            appendSeparator();
            sb.append(quote(name)).append(':').append(value);
            return this;
        }

        SimpleJsonWriter field(String name, boolean value) {
            appendSeparator();
            sb.append(quote(name)).append(':').append(value);
            return this;
        }

        private void appendSeparator() {
            if (!first) {
                sb.append(',');
            }
            first = false;
        }

        private static String quote(String s) {
            // Minimal escaping -- enough for this demo's plain ASCII field values, not a full
            // JSON-spec-compliant escaper (real code should use a proper JSON library).
            return '"' + s.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
        }

        String build() {
            return sb.toString() + '}';
        }
    }

    // A plain, non-Serializable POJO -- deliberately does NOT implement Serializable to reinforce
    // that the JSON alternative never needs Java's native serialization machinery at all.
    static class EmployeeDto {
        final String name;
        final int age;
        final boolean active;

        EmployeeDto(String name, int age, boolean active) {
            this.name = name;
            this.age = age;
            this.active = active;
        }
    }

    private static String toJson(EmployeeDto dto) {
        return new SimpleJsonWriter()
                .field("name", dto.name)
                .field("age", dto.age)
                .field("active", dto.active)
                .build();
    }

    /** Extremely small hand-rolled parser matching exactly the shape toJson() produces above. */
    private static EmployeeDto fromJson(String json) {
        Map<String, String> raw = new LinkedHashMap<>();
        String body = json.trim();
        body = body.substring(1, body.length() - 1); // strip outer { }
        for (String pair : body.split(",")) {
            int colon = pair.indexOf(':');
            String key = unquote(pair.substring(0, colon).trim());
            String value = pair.substring(colon + 1).trim();
            raw.put(key, value);
        }
        String name = unquote(raw.get("name"));
        int age = Integer.parseInt(raw.get("age"));
        boolean active = Boolean.parseBoolean(raw.get("active"));
        // The CALLER decides the target type is EmployeeDto -- nothing in the JSON text can
        // smuggle in an instruction to instantiate some other, unrelated class instead.
        return new EmployeeDto(name, age, active);
    }

    private static String unquote(String s) {
        s = s.trim();
        if (s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1);
        }
        return s.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static void demoHandRolledJsonAlternative() {
        printSection("4) Hand-rolled JSON-like alternative (no external library)");

        EmployeeDto original = new EmployeeDto("Alan Turing", 34, true);
        String json = toJson(original);
        System.out.println("Hand-written JSON: " + json);
        System.out.println("(Human-readable and inspectable -- contrast with Java serialization's opaque,");
        System.out.println("binary, class-name-embedding byte format.)");

        EmployeeDto restored = fromJson(json);
        System.out.println("Parsed back -> name=" + restored.name + ", age=" + restored.age
                + ", active=" + restored.active);
        System.out.println("fromJson() ALWAYS builds an EmployeeDto -- the text has no mechanism analogous");
        System.out.println("to ObjectInputStream's embedded class name to say 'instantiate something else'.");
        System.out.println("This is the structural safety property real JSON libraries (Jackson/Gson) share:");
        System.out.println("the caller fixes the target type; the input data cannot override that choice");
        System.out.println("(barring misconfigured open polymorphic typing -- see Theory file 03).");
    }

    // -------------------------------------------------------------------
    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }
}
