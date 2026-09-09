# Why This Belongs in an Architecture Module

--> The previous four files were about arranging CODE well -- layers, ports/adapters, bounded contexts, aggregates. This file is about verifying that the TESTS covering that code are actually doing their job. A well-architected system with weak tests is fragile in a way that's easy to miss: the architecture makes CHANGE safe to attempt, but only genuinely trustworthy tests make change safe to SHIP. Mutation testing is the tool that measures whether a test suite is trustworthy, as opposed to merely present -- and a system with cleanly separated layers/ports (files 01-02) is exactly the kind of system where mutation testing pays off fastest, because the business logic worth mutation-testing has already been isolated from framework noise.

# The Problem: Code Coverage Lies

--> **Line/branch code coverage** measures whether a test suite EXECUTES a given line or branch at least once. It says nothing about whether the test suite would actually NOTICE if that line's logic were wrong. A test can achieve 100% coverage of a method while asserting nothing meaningful about its behavior.

```java
class DiscountCalculator {
    double applyDiscount(double price, double percentage) {
        if (percentage > 50) {                 // branch A
            return price * 0.5;                  // branch A body
        }
        return price * (1 - percentage / 100);   // branch B body
    }
}
```

```java
// This test achieves 100% LINE coverage and 100% BRANCH coverage of DiscountCalculator.
// It calls both branches. And yet it would pass even if applyDiscount always returned 0.
@Test
void appliesDiscount() {
    DiscountCalculator calc = new DiscountCalculator();
    calc.applyDiscount(100, 60);     // executes branch A -- but asserts NOTHING about the result
    calc.applyDiscount(100, 20);     // executes branch B -- but asserts NOTHING about the result
    // no assertEquals, no assertion at all -- coverage tools count this as "fully covered"
}
```

--> **Coverage answers "was this code RUN during testing?"** It cannot answer "would a bug in this code have been CAUGHT by testing?" -- those are very different questions, and teams that chase a coverage percentage as a quality target routinely end up with exactly the test above: technically full coverage, with close to zero actual protection against regressions. This gap is precisely what mutation testing is designed to expose.

# What Mutation Testing Actually Does

--> A mutation-testing tool takes the PRODUCTION code, deliberately introduces a small, single, syntactically-valid bug into it (a **mutant**) -- flipping `>` to `>=`, changing `+` to `-`, deleting a method call, negating a boolean condition -- and then RE-RUNS the existing test suite against that mutated code.

```text
For EACH mutant generated:

  1. Take the original code, apply ONE small change (the "mutation").
  2. Run the FULL existing test suite against the mutated code.
  3. Did at least one test FAIL?
       YES -> the mutant was "KILLED"    -- the test suite is doing its job here
       NO  -> the mutant "SURVIVED"      -- the test suite would NOT catch this real bug
```

```java
// Original:
if (percentage > 50) { return price * 0.5; }

// Mutant #1 -- relational operator flipped (> becomes >=):
if (percentage >= 50) { return price * 0.5; }

// Mutant #2 -- boundary shifted (50 becomes 51):
if (percentage > 51) { return price * 0.5; }

// Mutant #3 -- arithmetic operator flipped (0.5 becomes 1.5, i.e. * becomes something else):
if (percentage > 50) { return price * 1.5; }
```

--> **Applying this to the "100% coverage, zero assertions" test above**: every one of those three mutants SURVIVES, because the test never checks the RETURNED value -- it only calls the method. A coverage tool reports this suite as fully covering `DiscountCalculator`; a mutation-testing tool correctly reports it as providing essentially no protection. This is mutation testing's core value proposition: it tests the TESTS, not the production code directly.

```java
// A test that actually asserts on the result KILLS all three mutants above.
@Test
void appliesFiftyPercentDiscountAboveThreshold() {
    DiscountCalculator calc = new DiscountCalculator();
    assertEquals(50.0, calc.applyDiscount(100, 60));    // kills mutant #1 (>= vs >) and #3 (* 1.5 vs * 0.5)
}

@Test
void appliesPercentageDirectlyAtOrBelowThreshold() {
    DiscountCalculator calc = new DiscountCalculator();
    assertEquals(80.0, calc.applyDiscount(100, 20));
    assertEquals(50.0, calc.applyDiscount(100, 50));      // kills mutant #2 (boundary at exactly 50)
}
```

# PIT (Pitest): Mutation Testing on the JVM

--> **PIT** (pitest.org, Maven/Gradle artifact `org.pitest:pitest`) is the de facto standard mutation-testing tool for Java. It integrates with JUnit and Maven/Gradle, runs the project's existing test suite against a generated set of mutants, and reports which mutants were killed vs. survived, per class and per line.

```xml
<!-- Maven -- pom.xml -->
<plugin>
    <groupId>org.pitest</groupId>
    <artifactId>pitest-maven</artifactId>
    <version>1.15.8</version>
    <configuration>
        <targetClasses>
            <param>com.example.billing.*</param>     <!-- which production classes to mutate -->
        </targetClasses>
        <targetTests>
            <param>com.example.billing.*Test</param>    <!-- which tests are allowed to kill mutants -->
        </targetTests>
        <mutationThreshold>80</mutationThreshold>        <!-- build FAILS below this mutation score -->
    </configuration>
</plugin>
```

```groovy
// Gradle -- build.gradle, using the pitest-gradle plugin (info.solidsoft.pitest)
pitest {
    targetClasses = ['com.example.billing.*']
    targetTests = ['com.example.billing.*Test']
    mutationThreshold = 80
    junit5PluginVersion = '1.2.1'      // required for JUnit 5 projects
}
```

```text
# Running it
mvn org.pitest:pitest-maven:mutationCoverage
gradle pitest

# Typical console/HTML report line:
> com.example.billing.DiscountCalculator
    Line coverage:      100% (6/6)
    Mutation coverage:   50% (3/6)      <-- the number that actually matters
```

--> **Why scope `targetClasses` narrowly**: mutating and re-running tests against EVERY class in a large codebase is extremely slow -- each surviving-vs-killed check re-runs relevant tests once per mutant, and a class can generate dozens of mutants. Scoping to the business-critical packages (the ones this module's architecture patterns are meant to keep clean and well-isolated, e.g. a use-case ring or a core domain package) keeps a mutation-testing run fast enough to fit into a CI pipeline, while still checking the code where a missed bug matters most.

# Mutation Operators

--> A **mutation operator** is the specific RULE PIT uses to generate a mutant -- a category of small syntactic change. Different tools implement somewhat different operator sets; PIT's common ones:

| Operator | Example change |
|---|---|
| Conditionals Boundary | `>` ↔ `>=`, `<` ↔ `<=` |
| Negate Conditionals | `==` ↔ `!=`, `>` ↔ `<=` (full negation) |
| Math | `+` ↔ `-`, `*` ↔ `/` |
| Increments | `i++` ↔ `i--`, `+= 1` ↔ `-= 1` |
| Invert Negatives | `-x` ↔ `x` |
| Void Method Calls | removes a call to a void method entirely |
| Return Values | replaces a return value with a mutated one (e.g. non-null → null, `true` → `false`, `n` → `n+1`) |
| Constructor Calls | removes a constructor call, substituting `null` |
| Remove Conditionals | forces an `if` to always/never take its branch |

```java
// A single method can spawn several DIFFERENT mutants from several DIFFERENT operators:
boolean isEligible(int age, boolean hasConsent) {
    return age >= 18 && hasConsent;
}
// Conditionals Boundary mutant:   age > 18   && hasConsent
// Negate Conditionals mutant:     age >= 18  && !hasConsent
// Return Values mutant:           return false;   (ignoring the real computation entirely)
```

--> **Each surviving mutant is a concrete, specific gap** -- unlike a raw coverage percentage, a mutation report tells you exactly which line and which kind of bug your tests would fail to catch, which makes it directly actionable: write (or strengthen) one assertion, re-run, and watch that specific mutant flip from survived to killed.

# Reading and Interpreting the Mutation Score

```text
Mutation Score = (mutants KILLED / total mutants GENERATED) × 100%

           Coverage %   Mutation Score %      What it tells you
Suite A:      100%             50%       lines are executed, but assertions are weak/missing
Suite B:       70%             65%       untested code exists, AND some tested code is weakly asserted
Suite C:       85%             82%       most tested code is genuinely, meaningfully verified
```

--> **A mutation score is always upper-bounded by coverage** -- code that is never executed by any test can never kill any mutant on that line, so it's common (and correct practice) to look at coverage FIRST to find untested code, then mutation score to judge whether the code that IS tested is tested well.
--> **Not every surviving mutant indicates a real gap.** Some mutants are logically **equivalent** -- the mutated code behaves identically to the original for every possible input, so no test could ever kill it even in principle (e.g. mutating `i < list.size()` to `i <= list.size() - 1` in a case where both are always true together). PIT reports these as survived, but they represent a tooling limitation, not a testing gap; a small number of equivalent mutants is normal and expected, and chasing a mutation score of literal 100% is usually not a productive use of time for exactly this reason.
--> **A "good" mutation score is context-dependent, not a universal number.** Teams commonly target somewhere in the 70-90% range for genuinely important business logic, treating anything below that as a signal worth investigating; a target of 100% is rarely realistic once equivalent mutants are accounted for, and chasing the last few percent often means writing brittle, over-specific tests purely to satisfy the tool rather than to catch real bugs.

# When Mutation Testing Is Worth the CI Cost

--> Mutation testing is genuinely expensive: for each mutant, the relevant subset of the test suite reruns, so a class with a large test suite and many generated mutants can take minutes where a normal test run takes seconds. This cost has to be weighed deliberately, not applied uniformly everywhere.

```text
WORTH IT:
  - core/critical business logic (pricing, billing, risk calculation, access control)
  - code that changes rarely once correct, where a regression would be expensive
  - libraries/shared modules consumed by many other teams
  - exactly the kind of code Clean/Hexagonal architecture (files 01-02) isolates
    into a well-defined core, and the kind of logic DDD tactical patterns (file 04)
    concentrate inside aggregates and domain services

LESS WORTH IT (as a blanket, everywhere-in-CI practice):
  - thin adapters/controllers/DTOs with little logic of their own to get wrong
  - generated code, framework configuration, simple pass-through delegation
  - fast-moving prototype/experimental code not yet stabilized
  - running against the ENTIRE codebase on EVERY commit, given the runtime cost
```

--> **A common, pragmatic middle ground**: run mutation testing on a schedule (nightly, weekly) or as a separate, non-blocking CI job against just the business-critical packages, rather than gating every single commit/PR on it. Some teams instead scope it to only the files CHANGED in a given PR (PIT supports this via SCM-based mutation testing), keeping the check fast and directly relevant to what's actually being reviewed, without the cost of mutating the whole codebase every time.
--> **Mutation testing pairs especially well with the architecture patterns in this module**: because Clean/Hexagonal architecture deliberately isolates business rules from framework code (files 01-02), and DDD tactical patterns concentrate domain logic inside a small number of well-defined aggregates/services (file 04), the highest-value mutation-testing TARGET in a well-architected codebase is usually small, clearly identified, and easy to scope `targetClasses` around -- one more practical payoff of keeping business logic cleanly separated from its surrounding infrastructure.

# Common Gotchas and Best Practices

--> **Treating mutation score as a target to chase for its own sake.** Like code coverage before it, a mutation-score percentage can be gamed -- writing tests that merely execute more code paths without meaningfully asserting on them will raise line coverage but not mutation score; conversely, writing overly specific tests purely to kill an equivalent or low-value mutant produces brittle tests that break on harmless refactors. The score is a DIAGNOSTIC signal pointing at specific weak spots, not a KPI to maximize blindly.
--> **Running full mutation testing on every commit across the whole codebase.** This is usually too slow to be practical and trains a team to ignore or disable the check out of impatience; scope it to critical packages and/or run it less frequently than the main test suite.
--> **Ignoring equivalent mutants and being surprised the score never reaches 100%.** A small number of unkillable-in-principle mutants is expected in any nontrivial codebase; investigate SPECIFIC surviving mutants to judge whether each is equivalent or a genuine gap, rather than fixating on the aggregate percentage alone.
--> **Using mutation testing as a substitute for code coverage, rather than a complement to it.** Mutation score is bounded by coverage -- untested code generates mutants that trivially survive (nothing runs them at all), which looks identical in the report to "tested but weakly asserted" code unless coverage is also checked. Use coverage to find code with NO tests, and mutation testing to judge whether the tests that DO exist are any good.
--> **Applying it uniformly to thin, low-logic adapter code.** A controller that does nothing but deserialize a request and call a use case (file 01/02) has very little for a mutation to meaningfully break -- mutation-testing that code mostly generates noise. Reserve the expensive check for code with actual conditional/arithmetic/business logic worth verifying.
--> **Forgetting that a killed mutant still needs a MEANINGFUL assertion, not just any assertion.** A test that asserts `result != null` will kill a "return null" mutant but will happily let a "return the wrong non-null value" mutant survive -- PIT's Return Values operator specifically targets this gap, and it is a useful reminder to assert on the actual EXPECTED value, not merely on the absence of an obviously-wrong one.
