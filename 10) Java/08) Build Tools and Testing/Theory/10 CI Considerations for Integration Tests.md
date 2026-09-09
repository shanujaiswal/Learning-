# Running Testcontainers in CI

--> A Testcontainers-based test suite needs a Docker daemon reachable from wherever the test JVM runs. Locally, that's just Docker Desktop/Docker Engine already installed. In CI, the runner itself needs Docker made available to it -- how that happens depends on the CI platform.

| CI platform | Docker availability |
|---|---|
| GitHub Actions (`ubuntu-latest` hosted runners) | Docker pre-installed and running by default -- Testcontainers usually works with zero extra config |
| GitLab CI | Depends on the executor -- `docker` or `docker+machine` executors need Docker-in-Docker (DinD) configured explicitly; `shell` executors on a Docker-capable host work directly |
| Jenkins | Depends entirely on the agent -- a Docker-capable agent/node is required; not automatic |
| CircleCI | Requires selecting a `machine` executor (which provides a real Docker daemon) rather than the default `docker` executor (which runs your job INSIDE a container, without its own daemon) |
| Self-hosted runners | Whatever the host provides -- verify `docker info` succeeds before assuming Testcontainers will work |

```yaml
# GitHub Actions -- Testcontainers typically just works on hosted Ubuntu runners
name: CI
on: [push]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - run: mvn verify   # Testcontainers finds the host's Docker daemon automatically
```

--> **Always verify Docker availability as a first, fast-failing step** when debugging a broken CI job -- Testcontainers' error messages when it can't reach a daemon at all are usually clear ("Could not find a valid Docker environment"), but it's easy to burn CI minutes chasing a red herring in test code when the real problem is infrastructure.

# Docker-in-Docker (DinD)

--> **Docker-in-Docker** refers to running a Docker daemon INSIDE a container, so that a CI job which itself runs inside a container can still launch further containers (the ones Testcontainers needs) alongside itself.
--> Two common shapes:

```text
Docker-in-Docker (true nested daemon)
  CI job container
    -> runs its own separate Docker daemon inside itself
    -> Testcontainers talks to THIS inner daemon
    -> generally requires the job container to run --privileged (security trade-off)

Docker-outside-of-Docker (DooD) / socket mounting
  CI job container
    -> has the HOST's Docker socket (/var/run/docker.sock) bind-mounted in
    -> Testcontainers talks to the host's daemon directly
    -> containers it starts are SIBLINGS of the job container, not nested inside it
    -> no --privileged needed, but the job container can affect/see the host's other containers
```

```yaml
# GitLab CI example: docker-outside-of-docker via socket mount (the more common, lighter approach)
test:
  image: maven:3.9-eclipse-temurin-21
  variables:
    DOCKER_HOST: "unix:///var/run/docker.sock"
  services: []   # not using DinD service; relying on socket availability on the runner
  script:
    - mvn verify
```

--> **Socket-mounting (DooD) is generally preferred over true nested DinD** where the CI platform allows it -- it avoids running privileged containers, starts faster (no inner daemon to boot), and is what GitHub Actions' hosted runners effectively provide out of the box. True DinD is sometimes unavoidable on stricter, multi-tenant CI platforms that won't grant socket access for isolation reasons.
--> **`TESTCONTAINERS_HOST_OVERRIDE`** and related environment variables exist for CI setups where the Docker daemon Testcontainers talks to is not on the same network-visible host as the test JVM (e.g. some remote-Docker or Testcontainers Cloud setups) -- check the Testcontainers docs for the specific CI platform when the default auto-detection doesn't work.

# Test Data Isolation Strategies

--> Running integration tests in CI often means MULTIPLE test classes (or even parallel CI jobs) sharing infrastructure, which raises the same data-isolation questions as local development, with less room to debug interactively when it goes wrong.

| Strategy | Isolation level | Speed cost |
|---|---|---|
| Fresh container per test class/method | Perfect -- no possibility of cross-test leakage | High -- container startup repeated many times |
| Singleton container + `@Transactional` rollback | High -- each test's writes are undone | Low -- one container, cheap per-test rollback |
| Singleton container + `@Sql` cleanup scripts | Medium -- depends on scripts being complete | Low-medium |
| Singleton container, no cleanup (rely on unique test data) | Low -- easy to get wrong as the suite grows | Lowest, but riskiest |
| Separate schema/database per test class within one container | High, without per-class container startup cost | Medium -- schema setup cost instead |

--> **Parallel test execution** (e.g. Maven Surefire/Gradle running test classes concurrently to save wall-clock time) multiplies isolation risk -- two test classes sharing one singleton container and hitting the same tables AT THE SAME TIME can interfere even with per-test rollback, since rollback only protects one test's OWN transaction, not against concurrent reads/writes from a different test's in-flight transaction. Either avoid parallelizing integration tests that share state, or give each parallel worker its own container/schema.
--> **A pragmatic default**: singleton container (started once for the whole CI job) + `@Transactional` rollback for the majority of tests, reserving fresh-container-per-class or explicit schema separation for the specific tests that can't use transactional rollback (e.g. tests that must commit, or that test cross-transaction behavior).

# Balancing Test Suite Speed vs. Coverage

--> Integration tests are inherently slower than unit tests, and CI time is a real, felt cost -- slow feedback loops push developers toward skipping local verification and relying on CI to eventually complain, which delays feedback rather than shortening it.
--> **Tactics for keeping an integration suite fast without cutting coverage that matters:**

--> **Singleton container pattern** (previous file) -- pay container startup cost once per CI job, not once per test class.
--> **Prefer the narrowest slice that proves the thing** -- a `@DataJpaTest` against a real Testcontainers Postgres proves query correctness far more cheaply than a full `@SpringBootTest` when the web layer isn't what's under test.
--> **Split the pipeline into fast and slow stages** -- run unit tests (and maybe fast slice tests) on every push for quick feedback; gate merges on the full integration suite, possibly only running it on PRs to the main branch or before deploy rather than on every intermediate commit.
--> **Tag/group slow tests explicitly** (JUnit 5 `@Tag("integration")`) so `mvn test` (fast, unit-only, run constantly) and `mvn verify` (includes integration tests, run less frequently or in a separate CI stage) can be triggered independently.

```java
import org.junit.jupiter.api.Tag;

@Tag("integration")
@SpringBootTest
class OrderServiceIntegrationTest {
    // Excluded from a fast `mvn test` run configured to skip this tag,
    // included in a slower `mvn verify` / dedicated CI stage that doesn't.
}
```

```xml
<!-- Maven Surefire (unit only) vs Failsafe (integration, separate phase) is the classic split -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-failsafe-plugin</artifactId>
    <executions>
        <execution>
            <goals>
                <goal>integration-test</goal>
                <goal>verify</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

--> **Cache Docker image layers between CI runs** where the platform supports it (most hosted runners already cache the base OS image layers; some platforms let you cache the specific `postgres:16-alpine` etc. layers too) -- this mostly eliminates the "first pull is slow" cost from every CI run, not just the first one.
--> **Run integration tests in parallel across CI jobs/shards** (splitting test classes across multiple parallel CI runners) when the suite grows large enough that even an optimized single-job run is a bottleneck -- combine with per-shard container isolation to avoid the cross-job interference described above.

# Flaky Test Mitigation

--> Integration tests are more flake-prone than unit tests by nature -- they depend on timing, network-adjacent behavior (even loopback), and external processes booting up, all sources of nondeterminism a pure-logic unit test doesn't have.

--> **Container readiness races** -- a container reporting "started" before the service inside it is actually ready to accept connections. Mitigate with an accurate `waitingFor(...)` strategy (see the Testcontainers Fundamentals file) rather than a fixed `Thread.sleep()`, which is both slower than necessary on fast runs and still capable of failing on a slow one.
--> **Shared mutable state between tests** -- singleton containers plus incomplete cleanup is the classic cause of "passes alone, fails in the full suite" or "passes locally, fails in CI" symptoms. Diagnose by running the suspect test in isolation vs. as part of the full suite; a difference in outcome points straight at leaked state.
--> **Resource contention in CI** -- CI runners are often more resource-constrained (CPU/memory) than a developer's machine, so a timeout tuned against local runs can be too tight in CI. Prefer wait strategies and client timeouts with reasonable headroom over externally-imposed hardcoded ones.
--> **Test order dependence** -- a test that only passes when run after another (because it silently relies on data the other test happened to leave behind) is a latent bug that parallel or reordered CI execution will eventually expose. Fix by making each test independently set up its own required state (via `@Sql`, explicit repository calls, or fixtures) rather than depending on suite ordering.
--> **Non-deterministic assertions** -- asserting on exact timestamps, generated IDs, or ordering that the system doesn't actually guarantee (e.g. asserting row order from a query with no `ORDER BY`) causes intermittent, confusing failures unrelated to the code being tested. Assert on what's actually guaranteed; sort explicitly or use order-independent assertions (e.g. `containsExactlyInAnyOrder`) when order isn't part of the contract.

# Common Gotchas and Best Practices

--> **A missing Docker daemon in CI fails EVERY Testcontainers-based test at once** -- if a whole suite goes red simultaneously with connection-style errors, suspect infrastructure (daemon availability, DinD/socket config) before suspecting the tests themselves.
--> **Don't let "it's flaky, just re-run it" become policy.** A CI job configured to auto-retry flaky tests can mask a genuine, intermittent race condition (in the code OR the test) for a long time -- treat flakiness as a bug to root-cause, using isolated reruns and logs, not a fact of life to route around indefinitely.
--> **Keep the fast/slow split enforced, not just suggested** -- if "run integration tests separately" is a convention nobody's pipeline actually enforces, unit and integration tests silently blur together and the fast feedback loop erodes over time.
--> **Pin image versions in CI the same way as locally** (`postgres:16-alpine`, not `latest`) -- CI environments pull fresh images more often than a developer's cached local Docker, so an unpinned tag is more likely to introduce a surprise version change exactly where you can least afford new flakiness.
--> **Resource limits matter on shared CI runners** -- running many containers in parallel (multiple Testcontainers-based jobs on one constrained runner) can starve them of memory/CPU and produce failures that look like application bugs but are really environment exhaustion; monitor CI runner resource usage when integration suites start failing intermittently under load.
