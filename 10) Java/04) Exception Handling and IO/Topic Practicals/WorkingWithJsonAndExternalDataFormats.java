/*
 * WorkingWithJsonAndExternalDataFormats.java
 *
 * Demonstrates:
 *   1. Why the JDK has no built-in JSON API -- Jackson/Gson conceptual illustration (comments only, no deps)
 *   2. A hand-rolled, educational JSON-object-to-string writer (escaping + structure basics)
 *   3. Manual CSV parsing with String.split(",") on simple, well-formed data
 *   4. A more robust hand-rolled CSV line parser that respects double-quoted fields
 *   5. Scanner basics -- tokenized reading from a String source
 *   6. The classic nextInt() + nextLine() leftover-newline trap, and its fix
 *   7. Scanner with a custom delimiter (lightweight CSV-style parsing)
 *   8. Defensive parsing with hasNextInt() / hasNext() before consuming
 *
 * Covers Theory chapter:
 *   10) Java/04) Exception Handling and IO/Theory/05 Working with JSON and External Data Formats in Java.md
 *
 * Compile: javac 05_working_with_json_and_external_data_formats.java
 * Run:     java WorkingWithJsonAndExternalDataFormats
 */

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class WorkingWithJsonAndExternalDataFormats {

    public static void main(String[] args) {
        printSection("1) JSON in Java -- no JDK built-in, Jackson/Gson conceptual shape");
        demoJsonLibraryConcept();

        printSection("2) Hand-rolled educational JSON writer (escaping + structure)");
        demoHandRolledJsonWriter();

        printSection("3) Manual CSV parsing with String.split(\",\") -- simple well-formed data");
        demoSimpleCsvParsing();

        printSection("4) Robust hand-rolled CSV line parser -- respects quoted fields");
        demoQuotedCsvLineParser();

        printSection("5) Scanner basics -- tokenized reading from a String source");
        demoScannerBasics();

        printSection("6) The nextInt() + nextLine() leftover-newline trap");
        demoScannerNextIntTrap();

        printSection("7) Scanner with a custom delimiter");
        demoScannerCustomDelimiter();

        printSection("8) Defensive parsing with hasNextInt() / hasNext()");
        demoScannerDefensiveParsing();

        System.out.println("\nAll JSON / external data format demos completed.");
    }

    // -------------------------------------------------------------------
    // 1) JSON in Java -- no JDK built-in support
    // -------------------------------------------------------------------
    // The JDK ships NO json parser/writer. Real projects reach for a third-party
    // library. The two dominant choices are Jackson (com.fasterxml.jackson.core)
    // and Gson (com.google.code.gson). Both are NOT part of the JDK, so they are
    // NOT compiled or run here -- only shown as commented-out illustrations of the
    // conceptual API shape, so this file stays runnable with plain javac/java.
    //
    // Conceptual illustration of Jackson usage (requires an external dependency):
    /*
    import com.fasterxml.jackson.databind.ObjectMapper;

    class Person {
        public String name;
        public int age;
    }

    public class JacksonExample {
        public static void main(String[] args) throws Exception {
            ObjectMapper mapper = new ObjectMapper();

            // Java object -> JSON string
            Person p = new Person();
            p.name = "Grace Hopper";
            p.age = 85;
            String json = mapper.writeValueAsString(p);
            System.out.println(json);   // {"name":"Grace Hopper","age":85}

            // JSON string -> Java object
            Person restored = mapper.readValue(json, Person.class);
            System.out.println(restored.name + " is " + restored.age);
        }
    }
    */
    //
    // Conceptual illustration of Gson usage (requires an external dependency):
    /*
    import com.google.gson.Gson;

    public class GsonExample {
        public static void main(String[] args) {
            Gson gson = new Gson();
            Person p = new Person();
            p.name = "Ada Lovelace";
            p.age = 36;

            String json = gson.toJson(p);                       // Java object -> JSON
            Person restored = gson.fromJson(json, Person.class); // JSON -> Java object
        }
    }
    */
    static void demoJsonLibraryConcept() {
        System.out.println("JDK has no built-in JSON API -- Jackson and Gson are the dominant third-party choices.");
        System.out.println();
        System.out.println("            | Jackson                       | Gson");
        System.out.println("------------|--------------------------------|-----------------------------");
        System.out.println("Maintainer  | FasterXML (community)          | Google");
        System.out.println("Typical use | Enterprise apps, Spring Boot    | Android, simple standalone use");
        System.out.println("Core class  | ObjectMapper                   | Gson");
        System.out.println("Annotations | @JsonProperty, @JsonIgnore      | @SerializedName, @Expose");
        System.out.println("Streaming   | JsonParser / JsonGenerator      | JsonReader / JsonWriter");
        System.out.println();
        System.out.println("Both share the same mental model: a mapper/converter object reads a JSON");
        System.out.println("string into a Java object (deserialization), or turns a Java object into a");
        System.out.println("JSON string (serialization), matching JSON keys to Java fields via reflection.");
        System.out.println("See the commented-out JacksonExample / GsonExample blocks in the source above.");
    }

    // -------------------------------------------------------------------
    // 2) Hand-rolled educational JSON writer -- illustrates the mechanics that
    //    Jackson/Gson automate: quoting keys/strings, escaping special characters,
    //    and joining key:value pairs into a flat JSON object structure.
    // -------------------------------------------------------------------
    static String toEducationalJson(Map<String, Object> fields) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append("\"").append(escapeJson(entry.getKey())).append("\":");
            Object value = entry.getValue();
            if (value instanceof String) {
                sb.append("\"").append(escapeJson((String) value)).append("\"");
            } else if (value instanceof Number || value instanceof Boolean) {
                sb.append(value);
            } else if (value == null) {
                sb.append("null");
            } else {
                // Fallback: treat anything else as a quoted string representation
                sb.append("\"").append(escapeJson(String.valueOf(value))).append("\"");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    // Escapes the handful of characters JSON strings require escaped -- a REAL
    // library also handles unicode escapes, control characters, etc. This is a
    // deliberately minimal illustration, not a spec-complete implementation.
    static String escapeJson(String raw) {
        StringBuilder escaped = new StringBuilder();
        for (char c : raw.toCharArray()) {
            switch (c) {
                case '"':  escaped.append("\\\""); break;
                case '\\': escaped.append("\\\\"); break;
                case '\n': escaped.append("\\n");  break;
                case '\t': escaped.append("\\t");  break;
                default:   escaped.append(c);
            }
        }
        return escaped.toString();
    }

    static void demoHandRolledJsonWriter() {
        Map<String, Object> person = new LinkedHashMap<>();
        person.put("name", "Grace Hopper");
        person.put("age", 85);
        person.put("active", true);
        person.put("bio", "Pioneer of \"compilers\"\nand COBOL.");

        String json = toEducationalJson(person);
        System.out.println("Hand-rolled JSON output:");
        System.out.println(json);
        System.out.println();
        System.out.println("Notice the escaped quotes and newline inside 'bio' -- this is exactly the kind");
        System.out.println("of escaping detail Jackson/Gson handle automatically (and far more robustly)");
        System.out.println("for arbitrary nested objects, arrays, numbers, and unicode content.");
    }

    // -------------------------------------------------------------------
    // 3) Manual CSV parsing -- naive String.split(",") on simple, well-formed data
    // -------------------------------------------------------------------
    static void demoSimpleCsvParsing() {
        String csvData =
                "name,age,department\n" +
                "Alice,30,Engineering\n" +
                "Bob,25,Marketing\n" +
                "Carol,35,Engineering";

        String[] lines = csvData.split("\n");
        String[] headers = lines[0].split(",");

        List<Map<String, String>> records = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            String[] fields = lines[i].split(",");
            Map<String, String> record = new LinkedHashMap<>();
            for (int col = 0; col < headers.length; col++) {
                record.put(headers[col], fields[col]);
            }
            records.add(record);
        }

        System.out.println("Parsed " + records.size() + " CSV records:");
        for (Map<String, String> record : records) {
            System.out.println("  " + record);
        }

        System.out.println();
        System.out.println("Naive split(\",\") breaks on a field like \"Smith, John\" (a quoted field");
        System.out.println("containing a comma) -- it would be incorrectly split into two fields.");
    }

    // -------------------------------------------------------------------
    // 4) Robust hand-rolled CSV line parser -- respects simple double-quoted fields
    // -------------------------------------------------------------------
    static List<String> parseQuotedCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;               // toggle quote state
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString());     // field boundary, only if NOT inside quotes
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());              // last field after the loop
        return fields;
    }

    static void demoQuotedCsvLineParser() {
        String naiveLine = "Smith, John,42,Engineering";
        System.out.println("Naive split of 'Smith, John,42,Engineering':");
        System.out.println("  -> " + List.of(naiveLine.split(",")) + " (WRONG -- name got split in two)");

        String quotedLine = "\"Smith, John\",42,Engineering";
        List<String> parsed = parseQuotedCsvLine(quotedLine);
        System.out.println();
        System.out.println("Quote-aware parse of '\"Smith, John\",42,Engineering':");
        System.out.println("  -> " + parsed + " (CORRECT -- comma inside quotes preserved)");
    }

    // -------------------------------------------------------------------
    // 5) Scanner basics -- reading from a String source (no real console needed)
    // -------------------------------------------------------------------
    static void demoScannerBasics() {
        String simulatedInput = "Alice 30 95.5\nBob 25 88.2";
        Scanner scanner = new Scanner(simulatedInput);

        while (scanner.hasNextLine()) {
            String name = scanner.next();          // reads next whitespace-delimited token
            int age = scanner.nextInt();
            double score = scanner.nextDouble();
            scanner.nextLine();                     // consume the rest of the line (the trailing newline)
            System.out.println(name + " is " + age + " years old, scored " + score);
        }
        scanner.close();
    }

    // -------------------------------------------------------------------
    // 6) The classic nextInt() + nextLine() trap
    // -------------------------------------------------------------------
    static void demoScannerNextIntTrap() {
        String simulatedInput = "42\nHello World";

        Scanner buggyScanner = new Scanner(simulatedInput);
        int number = buggyScanner.nextInt();
        String line = buggyScanner.nextLine();   // BUG: returns "" (leftover end of the "42" line)
        System.out.println("Buggy: number=" + number + ", line='" + line + "' (expected 'Hello World')");
        buggyScanner.close();

        // Fix: consume the leftover newline explicitly before reading the next real line
        Scanner fixedScanner = new Scanner(simulatedInput);
        int number2 = fixedScanner.nextInt();
        fixedScanner.nextLine();                 // discard the leftover newline
        String properLine = fixedScanner.nextLine();
        System.out.println("Fixed: number2=" + number2 + ", properLine='" + properLine + "'");
        fixedScanner.close();
    }

    // -------------------------------------------------------------------
    // 7) Scanner with a custom delimiter -- lightweight CSV-style parsing
    // -------------------------------------------------------------------
    static void demoScannerCustomDelimiter() {
        Scanner scanner = new Scanner("apple,banana,cherry");
        scanner.useDelimiter(",");
        System.out.println("Tokens split on ',':");
        while (scanner.hasNext()) {
            System.out.println("  " + scanner.next());
        }
        scanner.close();
    }

    // -------------------------------------------------------------------
    // 8) Defensive parsing with hasNextInt() / hasNext()
    // -------------------------------------------------------------------
    static void demoScannerDefensiveParsing() {
        Scanner scanner = new Scanner("25 notanumber 30");
        while (scanner.hasNext()) {
            if (scanner.hasNextInt()) {
                System.out.println("Valid int: " + scanner.nextInt());
            } else {
                System.out.println("Skipping non-integer token: " + scanner.next());
            }
        }
        scanner.close();
    }

    static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }
}
