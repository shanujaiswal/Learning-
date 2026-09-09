/*
 * CodeQualityAndCiBasicsDemo.java
 *
 * Covers Theory chapter:
 *     08) Build Tools and Testing/Theory/05 Code Quality and CI Basics for Java.md
 *
 * IMPORTANT: Unlike files 01-04, this topic isn't a build tool or test framework with its own API to
 * call -- it's a set of PRACTICES (static analysis, coverage, CI pipelines) that act ON code you
 * already wrote. So this file demonstrates the idea directly in Java: a small class written with
 * deliberately poor style/quality (commented out, annotated with WHAT tool would flag each issue and
 * WHY), immediately followed by a "fixed", clean version of the exact same behavior. Below that,
 * Checkstyle/SpotBugs/SonarQube/JaCoCo configuration snippets and a full CI pipeline are reproduced
 * as commented XML/YAML-like pseudocode, matching how files 01-02 embedded commented pom.xml/
 * build.gradle blocks alongside runnable Java. Every commented block here is illustrative only --
 * it is not meant to compile (it isn't even Java in the CI-pipeline case) -- only the "fixed" class
 * and main() below are real, runnable code. See Theory file 05 for the full tool-by-tool reference.
 * ---------------------------------------------------------------------------------------------
 */

// =====================================================================================
// BEFORE: a small class written with several intentional quality issues, commented out.
// Each issue is annotated with which tool would typically catch it and why it matters.
// This represents what might live at: src/main/java/com/example/app/discountcalc.java (sic --
// see the naming issue flagged below) BEFORE any static analysis/CI gate was applied to it.
// =====================================================================================
/*
import java.io.FileReader;   // SpotBugs: unused import if never actually used below -- dead weight

class discountcalc {                              // Checkstyle: class names must be PascalCase
                                                            // ("discountcalc" fails a TypeName rule)

    public double d;                                        // Checkstyle: non-descriptive field name;
                                                              // also public mutable field breaks encapsulation

    public double calc(double p,double pct) {               // Checkstyle: parameter names too short/unclear
                                                              // ("p", "pct" instead of "price", "percent");
                                                              // also missing space after comma (formatting)
        double r = p - (p * pct / 100);                      // Checkstyle: unclear local variable name "r"
        this.d = r;
        return r;
    }

    public double calcNoCheck(double p, double pct) {
        // SpotBugs: no validation at all -- a negative pct silently INCREASES the price instead of
        // discounting it, and a pct > 100 silently returns a negative price. Neither is caught here,
        // unlike the fixed version below which throws IllegalArgumentException for both.
        return p - (p * pct / 100);
    }

    public void leaky() throws Exception {
        // SpotBugs: classic resource leak -- FileReader is opened but never closed in a finally block
        // or try-with-resources, so a real run of this method would leak a file handle on every call,
        // and worse, leaks it even more certainly if reading throws partway through.
        FileReader reader = new FileReader("prices.txt");
        reader.read();
        // reader.close() is missing entirely
    }

    public boolean equals(Object o) {
        // SpotBugs: overrides equals() WITHOUT also overriding hashCode() -- violates the equals/
        // hashCode contract (equal objects must have equal hashcodes), which silently breaks behavior
        // in any HashMap/HashSet/HashSet-backed collection that stores instances of this class.
        if (o == null) return false;
        return this.d == ((discountcalc) o).d;   // also: unsafe cast with no instanceof check first
    }
}
*/

// =====================================================================================
// AFTER: the same behavior, "fixed" -- clean naming, input validation, resource safety, and a
// correct equals/hashCode pair. This represents what would live at:
//   src/main/java/com/example/app/DiscountCalculator.java
// after the issues above were addressed (whether manually, or because a CI quality gate blocked
// the original version from merging until they were fixed).
// =====================================================================================
class DiscountCalculator {

    private double lastResult;

    double applyDiscount(double price, double discountPercent) {
        if (discountPercent < 0 || discountPercent > 100) {
            // Fixes the SpotBugs-flagged "no validation" issue above -- fails loudly and immediately
            // instead of silently producing a nonsensical result.
            throw new IllegalArgumentException("discountPercent must be between 0 and 100");
        }
        double result = price - (price * discountPercent / 100);
        this.lastResult = result;
        return result;
    }

    double getLastResult() {
        return lastResult;
    }

    // Demonstrates the resource-safety fix for the SpotBugs-flagged leak above: try-with-resources
    // guarantees the reader is closed even if an exception is thrown mid-read, with no explicit
    // finally block required.
    long countCharactersInFile(java.io.Reader source) throws java.io.IOException {
        long count = 0;
        try (java.io.Reader reader = source) {
            while (reader.read() != -1) {
                count++;
            }
        }
        return count;
    }

    @Override
    public boolean equals(Object o) {
        // Correct equals(): null-safe, class-safe (instanceof also handles null), and paired with
        // hashCode() below so this class behaves correctly inside HashMap/HashSet.
        if (this == o) {
            return true;
        }
        if (!(o instanceof DiscountCalculator)) {
            return false;
        }
        DiscountCalculator other = (DiscountCalculator) o;
        return Double.compare(this.lastResult, other.lastResult) == 0;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(lastResult);
    }
}

// =====================================================================================
// Static analysis + coverage tool configuration, reproduced as commented pseudocode (matching how
// 01/02 embedded commented pom.xml/build.gradle blocks). None of this is meant to compile -- it
// documents what a real project's build file would contain to enforce the "fixed" standard above.
// =====================================================================================
/*
    ---------------------------------------------------------------------------
    CHECKSTYLE -- coding standards and style (source-level, e.g. naming, imports, line length)
    ---------------------------------------------------------------------------
    <!-- Maven: pom.xml -->
    <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-checkstyle-plugin</artifactId>
        <version>3.4.0</version>
        <configuration>
            <configLocation>checkstyle.xml</configLocation>
            <failOnViolation>true</failOnViolation>
        </configuration>
        <executions>
            <execution>
                <goals><goal>check</goal></goals>
                <phase>verify</phase>
            </execution>
        </executions>
    </plugin>

    <!-- checkstyle.xml -- would have flagged "discountcalc", "p", "pct", "r" above -->
    <module name="Checker">
        <module name="TreeWalker">
            <module name="UnusedImports"/>
            <module name="LineLength"><property name="max" value="120"/></module>
            <module name="TypeName"/>     <!-- enforces PascalCase class names -->
            <module name="MethodName">
                <property name="format" value="^[a-z][a-zA-Z0-9]*$"/>
            </module>
        </module>
    </module>

    ---------------------------------------------------------------------------
    SPOTBUGS -- bytecode-level bug pattern detection (e.g. the leak + equals/hashCode issues above)
    ---------------------------------------------------------------------------
    <!-- Maven: pom.xml -->
    <plugin>
        <groupId>com.github.spotbugs</groupId>
        <artifactId>spotbugs-maven-plugin</artifactId>
        <version>4.8.6.2</version>
        <executions>
            <execution>
                <goals><goal>check</goal></goals>
                <phase>verify</phase>
            </execution>
        </executions>
    </plugin>
    <!-- Checkstyle checks how the code LOOKS; SpotBugs checks how the code likely BEHAVES --
         both are commonly run together since they catch different classes of issues. -->

    ---------------------------------------------------------------------------
    SONARQUBE -- comprehensive, server-based quality platform with a pass/fail "Quality Gate"
    ---------------------------------------------------------------------------
    # mvn verify sonar:sonar -Dsonar.projectKey=my-app -Dsonar.host.url=https://sonarcloud.io
    #
    # Quality Gate example conditions:
    #   - 0 new critical bugs
    #   - coverage on NEW code >= 80%
    #   - 0 new security hotspots
    # SonarQube deliberately focuses on "new code" vs "overall code" -- a legacy codebase may have
    # thousands of pre-existing minor issues that aren't realistic to fix all at once; the gate
    # instead blocks introducing NEW problems in the code just changed.

    ---------------------------------------------------------------------------
    JACOCO -- code coverage (line + branch), instruments bytecode at test-run time
    ---------------------------------------------------------------------------
    <!-- Maven: pom.xml -->
    <plugin>
        <groupId>org.jacoco</groupId>
        <artifactId>jacoco-maven-plugin</artifactId>
        <version>0.8.12</version>
        <executions>
            <execution>
                <goals><goal>prepare-agent</goal></goals>
            </execution>
            <execution>
                <id>report</id>
                <phase>verify</phase>
                <goals><goal>report</goal></goals>
            </execution>
            <execution>
                <id>check</id>
                <goals><goal>check</goal></goals>
                <configuration>
                    <rules>
                        <rule>
                            <element>BUNDLE</element>
                            <limits>
                                <limit>
                                    <counter>LINE</counter>
                                    <value>COVEREDRATIO</value>
                                    <minimum>0.80</minimum>   <!-- fail build if line coverage < 80% -->
                                </limit>
                            </limits>
                        </rule>
                    </rules>
                </configuration>
            </execution>
        </executions>
    </plugin>

    # target/site/jacoco/index.html would show, per class:
    #
    # Element               Missed Instr.   Cov.   Missed Branches   Cov.
    # ------------------------------------------------------------------
    # DiscountCalculator      6 of 60       90%     1 of 4            75%
    #
    # NOTE: line coverage vs branch coverage -- applyDiscount()'s single "if" above can show 100%
    # line coverage while only ever testing the valid-input branch; branch coverage specifically
    # catches whether BOTH the throw path and the normal-return path were exercised by tests.
    # Coverage is a FLOOR, not a goal -- 100% coverage with weak assertions is still a weak suite.
*/

// =====================================================================================
// A minimal CI pipeline, illustrated as commented YAML-like pseudocode (GitHub Actions style),
// matching how a real project would wire compile -> test -> static analysis -> coverage -> package
// into one automated sequence run on every push/pull-request. Not valid Java; illustrative only.
// =====================================================================================
/*
    # .github/workflows/ci.yml
    name: CI
    on:
      push:
        branches: [main]
      pull_request:

    jobs:
      build:
        runs-on: ubuntu-latest
        steps:
          - uses: actions/checkout@v4

          - name: Set up JDK 17                     # pin the JDK version explicitly -- an unpinned
            uses: actions/setup-java@v4               # "latest" runner JDK can silently change behavior
            with:
              java-version: '17'
              distribution: 'temurin'
              cache: 'maven'                          # caches ~/.m2 between runs -- meaningful speed lever

          - name: Compile                             # cheapest check first -- fail fast on trivial errors
            run: mvn -B compile

          - name: Run unit tests                      # JUnit 5 (+ Mockito) -- see Theory/Practicals 03-04
            run: mvn -B test

          - name: Run static analysis                 # Checkstyle + SpotBugs -- style + bug patterns
            run: mvn -B checkstyle:check spotbugs:check

          - name: Check coverage threshold             # JaCoCo -- fails build if under configured minimum
            run: mvn -B verify

          - name: Package artifact                    # only after everything above passed
            run: mvn -B package

          - name: Upload coverage report
            uses: actions/upload-artifact@v4
            with:
              name: jacoco-report
              path: target/site/jacoco/

    # Why "mvn verify" (not "mvn test") matters in CI: verify runs through the phase where
    # Checkstyle/SpotBugs/JaCoCo's check goal are typically bound -- "mvn test" alone would run
    # unit tests but skip those later quality gates entirely.
    #
    # Ordering rationale (fail fast, ordered by cost):
    #   1. compile               (seconds)     -- syntax/type errors
    #   2. unit tests             (seconds-min) -- behavioral regressions
    #   3. static analysis        (seconds-min) -- style + bug patterns
    #   4. coverage check         (part of verify)
    #   5. package                (only if everything above passed)
    #   6. (optional) integration tests / deploy -- slowest, often a separate pipeline stage
*/

// =====================================================================================
// Runnable entry point so this single file can still be compiled/run directly with plain javac/java
// for study purposes -- exercises the "fixed" DiscountCalculator above (input validation, resource
// safety, equals/hashCode), standing in for what real Checkstyle/SpotBugs/JaCoCo + CI would enforce
// automatically against the codebase this class lives in.
// =====================================================================================
public class CodeQualityAndCiBasicsDemo {
    public static void main(String[] args) throws Exception {
        DiscountCalculator calculator = new DiscountCalculator();

        double result = calculator.applyDiscount(100.0, 20.0);
        System.out.printf("100.0 with 20%% discount: %.2f%n", result);
        System.out.println("getLastResult() reflects it: " + calculator.getLastResult());

        try {
            calculator.applyDiscount(100.0, 150.0);
        } catch (IllegalArgumentException e) {
            System.out.println("Expected validation failure caught: " + e.getMessage());
        }

        try {
            calculator.applyDiscount(100.0, -10.0);
        } catch (IllegalArgumentException e) {
            System.out.println("Expected validation failure caught: " + e.getMessage());
        }

        // Exercise the try-with-resources fix -- reads from an in-memory StringReader instead of
        // a real file, but demonstrates the same resource-safety pattern as the fixed version above.
        long charCount = calculator.countCharactersInFile(new java.io.StringReader("hello ci"));
        System.out.println("Characters read safely via try-with-resources: " + charCount);

        // Exercise the correct equals/hashCode pair -- two calculators with the same lastResult
        // are equal, and (correctly) hash the same, so they behave consistently in a HashSet.
        DiscountCalculator other = new DiscountCalculator();
        other.applyDiscount(100.0, 20.0);
        System.out.println("calculator.equals(other) with same result: " + calculator.equals(other));
        System.out.println("hashCode match: " + (calculator.hashCode() == other.hashCode()));

        java.util.Set<DiscountCalculator> seen = new java.util.HashSet<>();
        seen.add(calculator);
        seen.add(other);
        System.out.println("HashSet size after adding two 'equal' calculators (expect 1): " + seen.size());

        System.out.println("\nIn a real project with a CI pipeline configured:");
        System.out.println("  mvn checkstyle:check -> would have flagged the BEFORE class's naming issues");
        System.out.println("  mvn spotbugs:check    -> would have flagged the resource leak + equals/hashCode");
        System.out.println("  mvn verify (+ JaCoCo)  -> fails the build if coverage drops below threshold");
        System.out.println("  GitHub Actions workflow above -> runs all of this on every push/PR automatically");
    }
}
