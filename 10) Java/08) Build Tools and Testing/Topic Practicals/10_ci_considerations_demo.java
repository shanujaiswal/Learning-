/**
 * 10_ci_considerations_demo.java
 *
 * Demonstrates, with heavily commented illustrative content, how integration
 * test suites are organized and run in CI:
 *     1. An example GitHub Actions CI pipeline with a Docker-capable runner (shown as
 *        a comment block, not real Java -- CI config is YAML, not compilable code)
 *     2. @Tag("fast") / @Tag("slow") test tagging for splitting unit vs. integration runs
 *     3. Maven Surefire/Failsafe configuration notes (as comments) for running the two
 *        tiers as separate build phases
 *     4. Flaky-test-mitigation notes as comments
 *     5. A small, genuinely runnable retry-annotation illustration (plain JUnit 5,
 *        no Docker/Spring needed) showing the MECHANICS of a retry extension, paired
 *        with a comment on why "just retry it" is not a substitute for root-causing flakiness
 *
 * Covers Theory chapter:
 *     08) Build Tools and Testing/Theory/10 CI Considerations for Integration Tests.md
 *
 * IMPORTANT -- most of this file is illustrative and will NOT compile/run standalone:
 *   - Sections 1 and 2's @Tag("integration")-style classes assume a real Spring Boot +
 *     Testcontainers project (Docker required) exactly like files 02-04 in this folder --
 *     NOT available in this environment.
 *   - Section 5 (the retry-annotation illustration) is the ONE exception: it's plain
 *     JUnit 5 + a custom extension, no Spring, no Docker, no external service -- it
 *     genuinely compiles and runs with just `junit-jupiter` on the classpath, to
 *     concretely show the retry MECHANISM discussed in the theory file's flaky-test section.
 *
 * Minimal Maven dependencies (pom.xml) to make section 5 compile and run standalone:
 *     <dependency>
 *         <groupId>org.junit.jupiter</groupId>
 *         <artifactId>junit-jupiter</artifactId>
 *         <version>5.10.2</version>
 *         <scope>test</scope>
 *     </dependency>
 *
 * Sections 1-4 additionally need everything files 02-04 in this folder need
 * (spring-boot-starter-test, spring-boot-testcontainers, Testcontainers postgresql
 * module, Docker) to actually run against a real pipeline.
 *
 * Run section 5 standalone (once dropped into any Maven project with junit-jupiter):
 *     mvn test -Dtest=RetryExtensionIllustrationTest
 */

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;


/*
 * =============================================================================
 * 1) ILLUSTRATIVE CI PIPELINE -- GitHub Actions, Docker-capable hosted runner
 * (not Java -- save as .github/workflows/ci.yml at the project root)
 * =============================================================================
 *
 * name: CI
 * on: [push, pull_request]
 *
 * jobs:
 *   # ---- Fast stage: unit tests + fast slice tests on every push ----
 *   fast-tests:
 *     runs-on: ubuntu-latest
 *     steps:
 *       - uses: actions/checkout@v4
 *       - uses: actions/setup-java@v4
 *         with:
 *           java-version: '21'
 *           distribution: 'temurin'
 *       - name: Run fast tests only (excludes @Tag("slow"))
 *         run: mvn test -Dgroups='!slow'
 *         # ubuntu-latest hosted runners have Docker pre-installed and running
 *         # by default, but this stage deliberately EXCLUDES slow/integration
 *         # tests so it stays fast enough to run on every single push.
 *
 *   # ---- Slow stage: full integration suite, gated on PRs to main only ----
 *   integration-tests:
 *     runs-on: ubuntu-latest
 *     if: github.event_name == 'pull_request'
 *     steps:
 *       - uses: actions/checkout@v4
 *       - uses: actions/setup-java@v4
 *         with:
 *           java-version: '21'
 *           distribution: 'temurin'
 *       - name: Verify Docker is available before trusting any test failure
 *         run: docker info
 *         # Always verify Docker availability as a first, fast-failing step --
 *         # Testcontainers' "Could not find a valid Docker environment" error
 *         # is clear, but it's easy to burn CI minutes chasing a red herring in
 *         # test code when the real problem is infrastructure.
 *       - name: Run the full suite, including Testcontainers-backed tests
 *         run: mvn verify
 *         # Testcontainers finds the host's Docker daemon automatically on
 *         # hosted GitHub Actions runners -- no explicit DinD/socket config needed.
 *
 * Why split into two jobs: unit tests (seconds) give fast feedback on every
 * push; the Testcontainers-backed integration suite (which pays real container
 * startup cost) only runs on PRs targeting main, keeping the common-case
 * feedback loop fast without skipping the slower, higher-fidelity suite entirely.
 * =============================================================================
 */


/*
 * =============================================================================
 * 2) MAVEN SUREFIRE (unit) vs. FAILSAFE (integration) -- separate build phases
 * (not Java -- merge into pom.xml)
 * =============================================================================
 *
 * <!-- Surefire runs during the `test` phase -- picks up *Test.java by default -->
 * <!-- Failsafe runs during `integration-test`/`verify` -- picks up *IT.java by default -->
 * <plugin>
 *     <groupId>org.apache.maven.plugins</groupId>
 *     <artifactId>maven-surefire-plugin</artifactId>
 *     <configuration>
 *         <excludedGroups>slow</excludedGroups>  <!-- excludes @Tag("slow") tests -->
 *     </configuration>
 * </plugin>
 * <plugin>
 *     <groupId>org.apache.maven.plugins</groupId>
 *     <artifactId>maven-failsafe-plugin</artifactId>
 *     <executions>
 *         <execution>
 *             <goals>
 *                 <goal>integration-test</goal>
 *                 <goal>verify</goal>
 *             </goals>
 *         </execution>
 *     </executions>
 * </plugin>
 *
 * `mvn test`   -> Surefire only -> fast unit (+ fast slice) tests, run constantly.
 * `mvn verify` -> Surefire AND Failsafe -> includes the slower Testcontainers-backed
 *                 integration suite, run less frequently / in a dedicated CI stage.
 * =============================================================================
 */


// =============================================================================
// 3) @Tag -- splitting fast vs. slow tests so `mvn test -Dgroups='!slow'` and
//    `mvn verify` can be triggered independently (illustrative -- assumes the
//    same Spring Boot + Testcontainers setup as files 02-04 in this folder)
// =============================================================================

/**
 * A fast, pure-logic unit test -- tagged "fast" (or left untagged, with "slow"
 * used as the opt-out group instead; either convention works as long as the
 * build config and the tags agree). No Docker, no Spring context.
 */
@Tag("fast")
class OrderPricingFastTest {

    @Test
    void discountAppliesCorrectly() {
        // Illustrative only -- see file 01 in this folder for the full,
        // genuinely-runnable version of this exact test style.
        assertTrue(3 * 10.0 * 0.9 == 27.0);
    }
}

/**
 * A Testcontainers-backed integration test -- tagged "slow" so a CI stage
 * configured with `-Dgroups='!slow'` (or Surefire's <excludedGroups>) skips
 * it entirely, while a dedicated `mvn verify` stage still picks it up.
 *
 * NOTE: the actual @SpringBootTest/@Testcontainers/@Container annotations and
 * PostgreSQLContainer setup are OMITTED here for brevity -- see files 02-04 in
 * this folder for the full, real shape of a class like this. This snippet
 * exists purely to show WHERE @Tag goes and how it composes with those annotations.
 */
@Tag("slow")
class OrderServiceIntegrationSlowTest {
    // @SpringBootTest
    // @Testcontainers
    // class body would contain @Container fields and @Test methods exactly as
    // in files 02-04 of this folder -- excluded here to avoid duplicating that
    // already-illustrated setup, and because it would require Docker to run.
}


// =============================================================================
// 4) Flaky test mitigation -- notes as comments (no runnable illustration
//    needed here; these are process/design guidance, not a code mechanism)
// =============================================================================

/*
 * - Container readiness races: a container reporting "started" before the
 *   service inside it can actually accept connections. Mitigate with an
 *   ACCURATE waitingFor(...) strategy (see file 02 in this folder) rather than
 *   a fixed Thread.sleep(), which is both slower than necessary on fast runs
 *   and still capable of failing on a genuinely slow one.
 *
 * - Shared mutable state between tests: a singleton container (file 03) plus
 *   incomplete cleanup is the classic cause of "passes alone, fails in the
 *   full suite" or "passes locally, fails in CI." Diagnose by running the
 *   suspect test in isolation vs. as part of the full suite -- a difference in
 *   outcome points straight at leaked state.
 *
 * - Resource contention in CI: CI runners are often more resource-constrained
 *   than a developer machine, so a timeout tuned against local runs can be too
 *   tight in CI. Prefer wait strategies and client timeouts with headroom over
 *   hardcoded values copied from a fast local machine.
 *
 * - Test order dependence: a test that only passes when run after another
 *   (because it silently relies on data that other test happened to leave
 *   behind) is a latent bug that parallel or reordered CI execution will
 *   eventually expose. Fix by making each test independently set up its own
 *   required state (@Sql, explicit repository calls, or fixtures -- file 04)
 *   rather than depending on suite ordering.
 *
 * - Non-deterministic assertions: asserting on exact timestamps, generated
 *   IDs, or ordering the system doesn't actually guarantee (e.g. row order
 *   from a query with no ORDER BY) causes intermittent failures unrelated to
 *   the code under test. Assert only on what's actually guaranteed; sort
 *   explicitly or use order-independent assertions when order isn't part of
 *   the contract.
 *
 * - "Just re-run it" is not a fix: a CI job configured to auto-retry flaky
 *   tests can mask a genuine, intermittent race condition (in the code OR the
 *   test) for a long time. Treat flakiness as a bug to root-cause using
 *   isolated reruns and logs, not a fact of life to route around indefinitely.
 *   The retry mechanism illustrated in section 5 below is shown for its
 *   MECHANICS, not as an endorsement of retry-as-policy.
 */


// =============================================================================
// 5) Retry-annotation-illustration -- a small, GENUINELY RUNNABLE JUnit 5
//    extension showing the mechanics of a "retry a flaky test N times" concept
// =============================================================================

/**
 * A custom annotation marking a test method as eligible for retry.
 * Real-world equivalent: junit-pioneer's @RetryingTest, or similar libraries --
 * this is a minimal hand-rolled illustration of the same idea, kept small
 * enough to be genuinely runnable with no Docker/Spring dependency at all.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface RetryOnFailure {
    int times() default 3;
}

/**
 * A minimal JUnit 5 extension implementing the retry mechanism: if the test
 * method throws, and it's annotated @RetryOnFailure, re-invoke the underlying
 * logic up to the configured number of times before letting the failure propagate.
 *
 * NOTE: a fully general JUnit 5 retry extension normally uses
 * TestTemplateInvocationContextProvider to re-run the whole test lifecycle.
 * This simplified version instead demonstrates the CONCEPT via a counter the
 * test body itself consults -- intentionally simple so the mechanics stay
 * readable, matching this file's goal of illustrating flaky-test-retry
 * mechanics rather than shipping a production-grade extension.
 */
class RetryOnFailureExtension implements TestExecutionExceptionHandler {

    @Override
    public void handleTestExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
        // In a full TestTemplateInvocationContextProvider-based implementation,
        // this is where the extension would re-invoke the test method itself.
        // Here, re-throwing simply demonstrates where that retry decision point
        // lives in the JUnit 5 extension model -- see the AtomicInteger-based
        // in-test-body retry loop below for the concrete, runnable behavior.
        throw throwable;
    }
}

/**
 * A genuinely runnable illustration: rather than relying on a full
 * TestTemplateInvocationContextProvider (more moving parts than this file
 * needs), this test demonstrates the retry CONCEPT directly with a small
 * retry loop and an attempt counter -- concretely showing "keep trying a
 * flaky operation up to N times before failing the test," which is the
 * mechanism any @RetryingTest-style annotation ultimately implements underneath.
 */
class RetryExtensionIllustrationTest {

    // Simulates a flaky operation that fails on its first two attempts, then
    // succeeds -- standing in for e.g. a network call or container-readiness
    // check that's occasionally slow to become available.
    private final AtomicInteger attempt = new AtomicInteger(0);

    private boolean flakyOperation() {
        int currentAttempt = attempt.incrementAndGet();
        return currentAttempt >= 3; // "succeeds" only from the 3rd attempt onward
    }

    @Test
    @RetryOnFailure(times = 3)
    void retriesUntilTheFlakyOperationSucceeds() {
        int maxAttempts = 3;
        AssertionError lastFailure = null;

        for (int i = 1; i <= maxAttempts; i++) {
            try {
                assertTrue(flakyOperation(), "operation not yet ready on attempt " + i);
                return; // succeeded -- test passes, no further retries needed
            } catch (AssertionError e) {
                lastFailure = e;
                // In a real retry extension, a short backoff might go here.
                // Deliberately omitted -- keeping this illustration synchronous
                // and fast rather than modeling realistic backoff timing.
            }
        }

        throw lastFailure; // exhausted all retries -- genuinely fail the test
    }

    // The comment block in section 4 above is the important caveat here: this
    // mechanism is shown to illustrate HOW retry logic works structurally, not
    // as a recommendation to reach for retries as a default flaky-test fix.
    // A test that only passes on attempt 3 because of a genuine race condition
    // in the code under test is a bug retry-wrapping merely hides, not resolves.
}
