# Why External Data Formats Matter

--> Real programs rarely operate purely on in-memory objects -- they read configuration files, accept user input, exchange data with web APIs, and process exported spreadsheets. This file covers three practical angles on that: how the Java ecosystem typically handles JSON (via third-party libraries, since the JDK has no built-in JSON API), how to parse a simpler format like CSV by hand, and how `Scanner` handles ad hoc text/console input parsing.

# JSON in Java -- No Built-In Support, and Why That's OK

--> Unlike some languages, the Java Standard Library (JDK) has **no built-in JSON parser**. JSON handling in Java is done via well-established third-party libraries, the two dominant ones being **Jackson** and **Gson**. Both convert between JSON text and Java objects ("serialization"/"deserialization," reusing the same terminology as `java.io.Serializable` even though the mechanism is unrelated).

| | Jackson | Gson |
|---|---|---|
| Maintainer | FasterXML (community) | Google |
| Typical use | Enterprise apps, Spring Boot's default | Android apps, simpler standalone use |
| Core classes | `ObjectMapper` | `Gson` |
| Annotations | `@JsonProperty`, `@JsonIgnore`, etc. | `@SerializedName`, `@Expose`, etc. |
| Performance | Generally faster for large payloads | Simpler API, slightly more overhead |
| Streaming API | Yes (`JsonParser`/`JsonGenerator`) | Yes (`JsonReader`/`JsonWriter`) |

--> **Conceptual shape of both libraries** -- despite different APIs, the mental model is identical: a mapper/converter object reads a JSON string and produces a Java object (deserialization), or takes a Java object and produces a JSON string (serialization), typically driven by reflection over the object's fields, matching JSON keys to Java field/property names.

```java
// Conceptual illustration of Jackson usage (requires the com.fasterxml.jackson.core dependency,
// NOT part of the JDK -- shown here for reference, not run as part of the practicals in this series)
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
```

```java
// Conceptual illustration of Gson usage (requires the com.google.code.gson dependency)
/*
import com.google.gson.Gson;

public class GsonExample {
    public static void main(String[] args) {
        Gson gson = new Gson();
        Person p = new Person();
        p.name = "Ada Lovelace";
        p.age = 36;

        String json = gson.toJson(p);                    // Java object -> JSON
        Person restored = gson.fromJson(json, Person.class);  // JSON -> Java object
    }
}
*/
```

--> **Why no JDK built-in?** -- historically, JSON became dominant well after Java's core I/O APIs were designed, and by the time it was ubiquitous, the ecosystem already had mature, widely-adopted libraries; adding a JDK-native JSON API risked either being redundant or fragmenting the ecosystem further. This mirrors the JDK's general philosophy of keeping the core small and relying on the library ecosystem (Maven Central) for many common concerns.
--> **What this Topic Practicals file actually demonstrates** -- since Jackson/Gson require external dependencies not available in a plain `javac`/`java` environment, the runnable practical instead builds a minimal, EDUCATIONAL hand-rolled JSON-object-to-string writer for a simple flat structure, to illustrate the underlying mechanics (escaping, structure) that libraries like Jackson automate for you at scale.

# Parsing CSV Manually

--> CSV (Comma-Separated Values) is a common lightweight tabular format. For SIMPLE, well-formed CSV (no embedded commas or quoted fields), manual parsing with `String.split(",")` is often good enough. For anything with embedded commas, quoted fields, or escaped quotes, a proper CSV library (e.g. Apache Commons CSV, OpenCSV) is strongly preferred over hand-rolled parsing -- the format has more edge cases than it looks.

```java
import java.util.*;

public class SimpleCsvParsing {
    public static void main(String[] args) {
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

        for (Map<String, String> record : records) {
            System.out.println(record);
        }
    }
}
```

--> **Where naive `split(",")` breaks** -- a field like `"Smith, John"` (a quoted field containing a comma) splits incorrectly into two fields with naive splitting, because the parser doesn't understand CSV quoting rules. Correctly handling this requires tracking whether the parser is currently "inside quotes" character by character, and handling escaped quotes (`""` representing a literal `"` inside a quoted field) -- exactly the kind of edge-case handling a real CSV library already solves.

```java
// A slightly more robust hand-rolled parser that DOES respect simple double-quoted fields
public class QuotedCsvLineParser {
    static List<String> parseLine(String line) {
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
}
```

# The `Scanner` Class

--> `java.util.Scanner` parses primitive types and strings from an input source (console `System.in`, a `String`, or a file) using configurable delimiters (whitespace by default). It's the most common tool for reading interactive console input in introductory Java, and is also handy for quick parsing of loosely structured text.

```java
import java.util.Scanner;

public class ScannerBasicsDemo {
    public static void main(String[] args) {
        // Scanning a String source directly (no actual console interaction needed to demonstrate)
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
}
```

--> **The classic `nextInt()` + `nextLine()` trap** -- `nextInt()`/`nextDouble()`/etc. consume only the NUMBER token, leaving the trailing newline character in the buffer. A subsequent `nextLine()` call then immediately returns that leftover EMPTY string instead of the next actual line of input, silently skipping a line. This is one of the most commonly hit `Scanner` bugs by beginners.

```java
import java.util.Scanner;

public class ScannerNextIntTrap {
    public static void main(String[] args) {
        String simulatedInput = "42\nHello World";
        Scanner scanner = new Scanner(simulatedInput);

        int number = scanner.nextInt();
        String line = scanner.nextLine();   // BUG: returns "" (the leftover end of the "42" line), not "Hello World"
        System.out.println("number=" + number + ", line='" + line + "'");

        // Fix: consume the leftover newline explicitly before reading the next real line
        Scanner fixedScanner = new Scanner(simulatedInput);
        int number2 = fixedScanner.nextInt();
        fixedScanner.nextLine();             // discard the leftover newline
        String properLine = fixedScanner.nextLine();
        System.out.println("number2=" + number2 + ", properLine='" + properLine + "'");
    }
}
```

--> **Custom delimiters** -- `Scanner` can be reconfigured to split on something other than whitespace, which makes it usable as a lightweight, if limited, CSV-style parser too.

```java
import java.util.Scanner;

public class ScannerCustomDelimiterDemo {
    public static void main(String[] args) {
        Scanner scanner = new Scanner("apple,banana,cherry");
        scanner.useDelimiter(",");
        while (scanner.hasNext()) {
            System.out.println(scanner.next());
        }
        scanner.close();
    }
}
```

--> **Checking before consuming (`hasNextX()`)** -- calling `nextInt()` when the next token isn't actually a valid integer throws `InputMismatchException`. Defensive parsing checks `hasNextInt()` (or the appropriate `hasNextX()`) first.

```java
import java.util.Scanner;

public class ScannerDefensiveParsingDemo {
    public static void main(String[] args) {
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
}
```

# Common Gotchas

--> **Never call `System.in`'s `Scanner.close()` and then try to read again** -- closing a `Scanner` wrapping `System.in` closes the underlying standard input stream too; attempting further reads (even with a brand new `Scanner`) throws `NoSuchElementException`/`IllegalStateException` for the remainder of the program's life.
--> **Assuming JSON/CSV parsing is "just string splitting"** -- both formats have real specifications with escaping rules; hand-rolled parsers that ignore quoting/escaping work on clean sample data and then break in production on the first field containing a comma, quote, or embedded newline.
--> **Not closing `Scanner` instances wrapping files** -- a `Scanner` over a `FileReader`/file `Path` holds an open file handle just like any other stream; wrap it in try-with-resources.
--> **Locale-sensitive parsing surprises** -- `Scanner.nextDouble()` (and similar) are LOCALE-AWARE by default, meaning in some locales `1.234` might be interpreted as `1234` (using `.` as a thousands separator instead of a decimal point). For predictable parsing, consider `scanner.useLocale(Locale.US)` or parse manually with `Double.parseDouble()`, which is always locale-independent.

# Best Practices Summary

--> For real JSON handling in production code, use Jackson or Gson (via Maven/Gradle dependency) rather than hand-rolling a parser -- JSON has enough edge cases (nested structures, escaping, numeric formats) that a dedicated library is almost always the right call.
--> For real CSV handling with any chance of quoted/escaped fields, use a proper library (Apache Commons CSV, OpenCSV) rather than `String.split(",")`.
--> Use `Scanner` for simple console input and quick-and-dirty text parsing; for high-volume or performance-sensitive file parsing, prefer `BufferedReader`/`Files.lines()` (covered in the I/O theory files), which are faster since they skip `Scanner`'s regex-based tokenization overhead.
--> Always guard `nextInt()`/`nextDouble()`-style calls with the corresponding `hasNextX()` check when input isn't guaranteed well-formed.
--> Remember the `nextInt()` + `nextLine()` newline-leftover trap -- insert an extra `nextLine()` to consume the leftover newline when mixing token-based and line-based reads.
--> Be explicit about locale when parsing numbers from text to avoid environment-dependent parsing bugs.
