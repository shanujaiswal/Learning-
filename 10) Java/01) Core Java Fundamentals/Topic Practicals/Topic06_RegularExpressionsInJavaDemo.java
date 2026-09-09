/*
 * Topic06_RegularExpressionsInJavaDemo.java
 *
 * NOTE ON FILENAME: the public class is named "Topic06_RegularExpressionsInJavaDemo" (Java
 * identifiers can't start with a digit) while the FILE keeps the "06_..." numeric prefix
 * used throughout this repo for ordering.
 *
 * Compile: javac 06_RegularExpressionsInJavaDemo.java
 * Run:     java Topic06_RegularExpressionsInJavaDemo
 *
 * Demonstrates:
 *   1. Pattern/Matcher basics -- compile once, matches() vs find() vs lookingAt()
 *   2. Numbered capturing groups and named groups ((?<name>...))
 *   3. String convenience methods backed by regex -- matches/replaceAll/replaceFirst/split
 *   4. Greedy vs lazy quantifier comparison
 *   5. Pattern flags (CASE_INSENSITIVE, MULTILINE) and Pattern.quote() for literal text
 *
 * Covers Theory chapter:
 *   01) Core Java Fundamentals/Theory/13 Regular Expressions in Java.md
 */

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Topic06_RegularExpressionsInJavaDemo {

    // Compiled ONCE as static final fields -- Pattern.compile is the expensive step, and a
    // compiled Pattern is safe to share/reuse across many inputs and across threads.
    private static final Pattern DIGITS = Pattern.compile("\\d+");
    private static final Pattern EMAIL = Pattern.compile("^[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}$");
    private static final Pattern DATE = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})");
    private static final Pattern NAMED_DATE = Pattern.compile("(?<year>\\d{4})-(?<month>\\d{2})-(?<day>\\d{2})");

    public static void main(String[] args) {
        demoPatternAndMatcherBasics();
        demoMatchesVsFindVsLookingAt();
        demoCapturingGroups();
        demoNamedGroups();
        demoStringRegexMethods();
        demoGreedyVsLazyQuantifiers();
        demoPatternFlagsAndQuote();
        System.out.println("\nAll regular-expressions demos completed.");
    }

    // -------------------------------------------------------------------
    // 1) Pattern/Matcher basics
    // -------------------------------------------------------------------
    private static void demoPatternAndMatcherBasics() {
        printSection("1) Pattern / Matcher Basics");

        Matcher matcher = DIGITS.matcher("Order 42, ship to zone 7");
        System.out.println("Finding all digit runs in \"Order 42, ship to zone 7\":");
        while (matcher.find()) {
            System.out.println("  found: \"" + matcher.group() + "\" at [" + matcher.start() + "," + matcher.end() + ")");
        }

        System.out.println("\nEmail validation via a pre-compiled Pattern:");
        System.out.println("  \"user@example.com\" valid? " + EMAIL.matcher("user@example.com").matches());
        System.out.println("  \"not-an-email\" valid?     " + EMAIL.matcher("not-an-email").matches());
    }

    // -------------------------------------------------------------------
    // 2) matches() vs find() vs lookingAt()
    // -------------------------------------------------------------------
    private static void demoMatchesVsFindVsLookingAt() {
        printSection("2) matches() vs find() vs lookingAt()");

        String[] inputs = {"42", "x42", "42x"};
        for (String input : inputs) {
            Matcher m = DIGITS.matcher(input);
            boolean matches = DIGITS.matcher(input).matches();
            boolean find = DIGITS.matcher(input).find();
            boolean lookingAt = DIGITS.matcher(input).lookingAt();
            System.out.printf("  input=\"%-4s\"  matches()=%-5b  find()=%-5b  lookingAt()=%-5b%n",
                    input, matches, find, lookingAt);
        }
        System.out.println("\nmatches() requires the WHOLE string to match; find() allows a substring match;");
        System.out.println("lookingAt() must match starting at position 0 but need not consume the whole input.");
    }

    // -------------------------------------------------------------------
    // 3) Capturing groups
    // -------------------------------------------------------------------
    private static void demoCapturingGroups() {
        printSection("3) Capturing Groups");

        Matcher m = DATE.matcher("Event on 2026-09-01 confirmed");
        if (m.find()) {
            System.out.println("group(0) [entire match] = " + m.group(0));
            System.out.println("group(1) [year]          = " + m.group(1));
            System.out.println("group(2) [month]         = " + m.group(2));
            System.out.println("group(3) [day]           = " + m.group(3));
        }
    }

    // -------------------------------------------------------------------
    // 4) Named groups
    // -------------------------------------------------------------------
    private static void demoNamedGroups() {
        printSection("4) Named Groups -- (?<name>...)");

        Matcher m = NAMED_DATE.matcher("2026-09-01");
        if (m.matches()) {
            System.out.println("group(\"year\")  = " + m.group("year"));
            System.out.println("group(\"month\") = " + m.group("month"));
            System.out.println("group(\"day\")   = " + m.group("day"));
        }
        System.out.println("\nNamed groups self-document (m.group(\"year\")) and survive someone inserting");
        System.out.println("a new group earlier in the pattern, which would silently renumber numeric indexes.");
    }

    // -------------------------------------------------------------------
    // 5) String methods backed by regex
    // -------------------------------------------------------------------
    private static void demoStringRegexMethods() {
        printSection("5) String Methods Backed by Regex");

        System.out.println("\"hello123world\".matches(\"[a-z]+\\\\d+[a-z]+\") = "
                + "hello123world".matches("[a-z]+\\d+[a-z]+"));

        System.out.println("\"a1b2c3\".replaceAll(\"\\\\d\", \"#\")  = " + "a1b2c3".replaceAll("\\d", "#"));
        System.out.println("\"a1b2c3\".replaceFirst(\"\\\\d\", \"#\") = " + "a1b2c3".replaceFirst("\\d", "#"));

        System.out.println("\"one,two,,three\".split(\",\")     = "
                + java.util.Arrays.toString("one,two,,three".split(",")));
        System.out.println("\"one,two,,three\".split(\",\", -1) = "
                + java.util.Arrays.toString("one,two,,three".split(",", -1)));

        // Classic pitfall: "." must be escaped when splitting on a literal dot.
        System.out.println("\n\"a.b.c\".split(\"\\\\.\")  = " + java.util.Arrays.toString("a.b.c".split("\\.")) + "  (correct)");
        System.out.println("\"a.b.c\".split(\".\")    = " + java.util.Arrays.toString("a.b.c".split(".")) + "  (WRONG -- unescaped '.' matches every char)");

        System.out.println("\nString.matches() always requires a FULL match, same as Matcher.matches():");
        System.out.println("  \"abc123\".matches(\"\\\\d+\") = " + "abc123".matches("\\d+")
                + "  (letters remain unmatched, so false -- use find() for partial matches)");
    }

    // -------------------------------------------------------------------
    // 6) Greedy vs lazy quantifiers
    // -------------------------------------------------------------------
    private static void demoGreedyVsLazyQuantifiers() {
        printSection("6) Greedy vs Lazy Quantifier Comparison");

        String html = "<b>bold</b>";

        Pattern greedy = Pattern.compile("<.+>");
        Matcher g = greedy.matcher(html);
        g.find();
        System.out.println("Input: " + html);
        System.out.println("Greedy  <.+>   -> \"" + g.group() + "\"  (consumes as MUCH as possible)");

        Pattern lazy = Pattern.compile("<.+?>");
        Matcher l = lazy.matcher(html);
        l.find();
        System.out.println("Lazy    <.+?>  -> \"" + l.group() + "\"  (consumes as LITTLE as possible)");

        System.out.println("\nParsing HTML/XML-like tags with a greedy '.+' instead of lazy '.+?' is a");
        System.out.println("classic beginner bug -- it grabs one giant span across multiple tags instead");
        System.out.println("of matching a single tag.");
    }

    // -------------------------------------------------------------------
    // 7) Pattern flags and Pattern.quote()
    // -------------------------------------------------------------------
    private static void demoPatternFlagsAndQuote() {
        printSection("7) Pattern Flags and Pattern.quote()");

        Pattern caseInsensitive = Pattern.compile("hello", Pattern.CASE_INSENSITIVE);
        System.out.println("CASE_INSENSITIVE: \"HELLO world\" contains \"hello\"? "
                + caseInsensitive.matcher("HELLO world").find());

        Pattern multiline = Pattern.compile("^line", Pattern.MULTILINE);
        Matcher ml = multiline.matcher("line1\nline2\nline3");
        int count = 0;
        while (ml.find()) count++;
        System.out.println("MULTILINE: number of lines starting with \"line\" = " + count
                + "  (^ matches at every line boundary, not just string start)");

        // Pattern.quote() treats arbitrary text as a LITERAL string, sidestepping the
        // "escape every metacharacter by hand" problem entirely.
        String dynamicToken = "a.b*c";
        String literalPattern = Pattern.quote(dynamicToken);
        boolean literalMatch = Pattern.compile(literalPattern).matcher("a.b*c").matches();
        System.out.println("\nPattern.quote(\"a.b*c\") treats it as literal text: matches \"a.b*c\" exactly? "
                + literalMatch);
        System.out.println("Without quoting, \".\" and \"*\" would be interpreted as regex metacharacters");
        System.out.println("instead of literal characters -- important when building a pattern from");
        System.out.println("untrusted/dynamic text that is NOT meant to be interpreted as regex.");
    }

    // -------------------------------------------------------------------
    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }
}
