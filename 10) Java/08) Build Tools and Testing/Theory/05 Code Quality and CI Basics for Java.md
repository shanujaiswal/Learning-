# Why Automated Code Quality Checks Matter

--> Tests verify BEHAVIOR ("does this code do the right thing"), but say nothing about maintainability, consistency, or entire classes of bugs that never manifest as a failing test on the exact inputs you happened to try (like resource leaks, null-safety issues, or overly complex methods nobody can safely modify). Static analysis and coverage tooling close that gap by analyzing the code itself, not just its observed runtime behavior.
--> **Continuous Integration (CI)** ties all of this together -- every code change automatically triggers a build, the full test suite, and these quality checks, on a clean machine, before the change is allowed to merge. This catches "works on my machine" problems and enforces quality gates without relying on developers remembering to run everything manually.

# Static Analysis Tools

--> Static analysis examines source code or compiled bytecode WITHOUT running it, looking for patterns known to indicate bugs, security issues, or maintainability problems.

## Checkstyle -- Coding Standards and Style

--> Enforces STYLE and formatting conventions: naming conventions, import order, brace placement, line length, Javadoc presence -- things that don't affect correctness but affect consistency and readability across a codebase and its contributors.

```xml
<!-- Maven: pom.xml -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-checkstyle-plugin</artifactId>
    <version>3.4.0</version>
    <configuration>
        <configLocation>checkstyle.xml</configLocation>   <!-- your rules file, or a bundled one like google_checks.xml -->
        <failOnViolation>true</failOnViolation>
    </configuration>
    <executions>
        <execution>
            <goals><goal>check</goal></goals>
            <phase>verify</phase>
        </execution>
    </executions>
</plugin>
```

```xml
<!-- checkstyle.xml -- a tiny example rule set -->
<!DOCTYPE module PUBLIC "-//Checkstyle//DTD Checkstyle Configuration 1.3//EN"
    "https://checkstyle.org/dtds/configuration_1_3.dtd">
<module name="Checker">
    <module name="TreeWalker">
        <module name="UnusedImports"/>
        <module name="LineLength"><property name="max" value="120"/></module>
        <module name="MethodName">
            <property name="format" value="^[a-z][a-zA-Z0-9]*$"/>  <!-- enforce camelCase -->
        </module>
    </module>
</module>
```

## SpotBugs -- Bytecode-Level Bug Pattern Detection

--> Analyzes COMPILED `.class` bytecode (the successor to the older "FindBugs" project) looking for known bug PATTERNS: null pointer dereferences, infinite recursive loops, incorrect equals/hashCode implementations, resource leaks (unclosed streams), suspicious concurrency (e.g. calling `.start()` in a constructor).

```xml
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
```

--> **Checkstyle vs SpotBugs, in one line**: Checkstyle checks how the code LOOKS (source-level style); SpotBugs checks how the code likely BEHAVES (bytecode-level bug patterns) -- both are commonly run together since they catch entirely different classes of issues.

## SonarQube -- Comprehensive, Server-Based Quality Platform

--> A full platform (typically self-hosted or SonarCloud) combining static analysis, code coverage ingestion, duplication detection, and security vulnerability scanning ("SAST") into one dashboard, tracked over time across many projects.
--> Introduces the concept of a **Quality Gate** -- a set of pass/fail conditions (e.g. "0 new critical bugs," "coverage on new code ≥ 80%," "no new security hotspots") that a build must satisfy to be considered acceptable; CI pipelines commonly fail the build if the Quality Gate fails.
--> Distinguishes **"new code" vs "overall code"** metrics deliberately -- a large legacy codebase might have thousands of pre-existing minor issues that aren't realistic to fix all at once, so SonarQube's default gate focuses on not introducing NEW problems in the code just changed, which is a far more actionable, incremental target.

```yaml
# Typical invocation via the sonar-maven-plugin, run as part of a CI pipeline
# mvn verify sonar:sonar -Dsonar.projectKey=my-app -Dsonar.host.url=https://sonarcloud.io
```

# Code Coverage with JaCoCo

--> **Code coverage** measures WHICH lines/branches of code were actually executed while the test suite ran -- it answers "how much of my code did my tests even touch," which is a DIFFERENT question from "are my tests good" (100% coverage with weak assertions is still a weak test suite -- coverage measures exercised code, not verified correctness).
--> **JaCoCo** ("Java Code Coverage") is the standard coverage tool for the JVM -- it instruments bytecode at test-run time to track exactly which lines and branches executed, then produces an HTML/XML report.

```xml
<!-- Maven: pom.xml -->
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.12</version>
    <executions>
        <execution>
            <goals><goal>prepare-agent</goal></goals>   <!-- attaches the coverage instrumentation agent -->
        </execution>
        <execution>
            <id>report</id>
            <phase>verify</phase>
            <goals><goal>report</goal></goals>            <!-- generates target/site/jacoco/index.html -->
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
                                <minimum>0.80</minimum>    <!-- fail the build if line coverage < 80% -->
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

```groovy
// Gradle: build.gradle
plugins {
    id 'jacoco'
}
jacocoTestReport {
    dependsOn test                 // report must run AFTER tests execute
    reports {
        xml.required = true         // machine-readable, e.g. for SonarQube ingestion
        html.required = true         // human-readable report
    }
}
jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = 0.80
            }
        }
    }
}
```

```text
target/site/jacoco/index.html report shows, per class/package:

Element              Missed Instr.   Cov.   Missed Branches   Cov.
------------------------------------------------------------------
OrderService          12 of 140      91%     2 of 8            75%
InventoryRepository    0 of 60      100%     0 of 4           100%
```

--> **Line coverage vs branch coverage** -- line coverage just asks "did this line execute at least once"; branch coverage asks "was EVERY branch of every conditional (`if`/`else`, `switch` cases, ternary) exercised." A line like `if (x > 0) return a; else return b;` can show 100% line coverage while only ever testing the `true` branch -- branch coverage catches that gap, which is why it's generally the more meaningful metric.
--> **Coverage targets are a floor, not a goal.** Chasing "100% coverage" as an end in itself encourages low-value tests that execute code without meaningfully asserting on it, just to make the number go up. A more useful framing: coverage tells you what's DEFINITELY under-tested (0% is a real red flag); it can't tell you what's WELL tested.

# Basic CI Pipeline Concept for Java Projects

--> A CI pipeline is a sequence of automated steps triggered on every push/pull-request, run on a clean, disposable machine (a "runner"), so results don't depend on any one developer's local environment.

```text
Typical Java CI pipeline stages:

1. Checkout code
2. Set up JDK (specific version, e.g. 17)      -- pinned, not "whatever's installed"
3. Cache dependencies (~/.m2 or ~/.gradle)      -- avoids re-downloading the same jars every run
4. Compile                                       (mvn compile / ./gradlew compileJava)
5. Run unit tests                                (mvn test / ./gradlew test)
6. Run static analysis (Checkstyle/SpotBugs)     -- fail fast on style/bug-pattern violations
7. Generate + check coverage (JaCoCo)             -- fail if below threshold
8. Package artifact                              (mvn package / ./gradlew build)
9. (optional) Run integration tests              -- often a separate, slower stage
10. (optional) Publish artifact / deploy          -- only on specific branches, e.g. main
```

**Example: GitHub Actions workflow for a Maven project**

```yaml
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

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: 'maven'              # caches ~/.m2 between runs automatically

      - name: Build and run tests
        run: mvn -B clean verify      # -B = "batch mode", non-interactive output suited to CI logs

      - name: Upload coverage report
        uses: actions/upload-artifact@v4
        with:
          name: jacoco-report
          path: target/site/jacoco/
```

**Example: equivalent for a Gradle project**

```yaml
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: 'gradle'

      - name: Build and run tests
        run: ./gradlew build          # runs compile + test + jacocoTestCoverageVerification, etc.
```

--> **Why `mvn verify` (not `mvn test`) in CI** -- `verify` runs everything through the `verify` phase, which is where plugins like Checkstyle, SpotBugs, and JaCoCo's `check` goal are typically bound -- `mvn test` alone would run unit tests but skip those later quality gates entirely.
--> **Caching dependencies is a meaningful CI speed lever** -- without it, every single run re-downloads every dependency from Maven Central/etc from scratch, which on a large project can dominate total pipeline time far more than compiling or testing does.
--> **Fail fast, ordered by cost** -- cheap checks (compile, style) typically run before expensive ones (full test suite, integration tests) so a pipeline fails in seconds on a trivial style violation instead of waiting minutes for the full suite only to fail on something that could've been caught immediately.

# Putting It Together -- A Minimal Quality Gate Checklist

| Check | Tool | What it catches | Typical build stage |
|---|---|---|---|
| Compiles cleanly | `javac` via Maven/Gradle | Syntax/type errors | `compile` |
| Unit tests pass | JUnit 5 (+ Mockito) | Behavioral regressions | `test` |
| Style/consistency | Checkstyle | Naming, formatting, import order | `verify` |
| Bug patterns | SpotBugs | Null derefs, resource leaks, bad equals/hashCode | `verify` |
| Coverage threshold | JaCoCo | Under-tested code paths | `verify` |
| Aggregate quality trend | SonarQube | Duplication, complexity, security hotspots, gate over time | separate CI job / server |

# Common Gotchas and Best Practices

--> **Don't let static analysis rules run unchecked from day one on a legacy codebase** -- turning on strict Checkstyle/SpotBugs rules against years of existing code produces thousands of violations nobody will realistically fix at once, which tends to get the whole tool disabled in frustration. Start by gating only NEW code (SonarQube's new-code focus, or a suppressions/baseline file for Checkstyle/SpotBugs) and tighten gradually.
--> **Pin your JDK version explicitly in CI** (`java-version: '17'`) -- an unpinned "latest available" JDK on the runner can silently change behavior or break a build when the runner image updates, at a time completely disconnected from any actual code change.
--> **A green coverage number is not the same as a trustworthy test suite** -- always pair coverage tooling with actual code review of test quality; a test that calls a method but asserts nothing meaningful still counts as "covered."
--> **Keep CI fast** -- slow pipelines get skipped, ignored, or worked around under deadline pressure, which defeats the entire point. Parallelize independent stages (e.g. static analysis and unit tests can often run concurrently), and reserve genuinely slow steps (full integration tests, end-to-end suites) for a separate, less frequently triggered pipeline stage.
--> **Treat CI failures as build-blocking, not advisory** -- a quality gate that can be silently ignored or overridden without discussion tends to erode over time until it's effectively decorative.
