# Why Gradle Exists Alongside Maven

--> Gradle (2007) was built as a reaction to Maven's rigidity -- Maven's XML `pom.xml` is purely DECLARATIVE (you describe WHAT you want, not HOW), which is predictable but awkward the moment you need genuinely custom build logic (conditional steps, custom task graphs, dynamic configuration). Gradle uses a real programming language (Groovy or Kotlin) for its build scripts, so it's declarative for the common cases but fully programmable when you need it.
--> Gradle also introduced INCREMENTAL builds and aggressive CACHING as first-class concerns -- it tracks task inputs/outputs and skips work that's already up to date, which is why large projects (and especially Android projects, which adopted Gradle as their standard build tool) build noticeably faster on repeat builds than the equivalent Maven project.
--> Today: Maven is still extremely common in traditional enterprise/Spring shops; Gradle dominates Android development and is increasingly common in new/greenfield JVM projects, especially multi-module ones, for its speed and flexibility.

# Groovy DSL vs Kotlin DSL

--> Gradle build scripts can be written in two languages, both compiling down to the same underlying build model:
--> **`build.gradle`** (Groovy DSL) -- the original, more common in older projects and most online tutorials/StackOverflow answers. Groovy is dynamically typed, so syntax is terse but IDE autocomplete/type-checking is weaker.
--> **`build.gradle.kts`** (Kotlin DSL) -- statically typed, so it gets full IDE autocompletion and compile-time error checking on the build script itself. Newer projects increasingly default to this. Functionally equivalent to Groovy for almost everything a typical project needs.

**The same simple build file in both dialects:**

```groovy
// build.gradle (Groovy DSL)
plugins {
    id 'java'
    id 'application'
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation 'org.junit.jupiter:junit-jupiter:5.10.2'
    implementation 'com.google.guava:guava:33.2.1-jre'
}

application {
    mainClass = 'com.example.app.App'
}

test {
    useJUnitPlatform()
}
```

```kotlin
// build.gradle.kts (Kotlin DSL)
plugins {
    java
    application
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    implementation("com.google.guava:guava:33.2.1-jre")
}

application {
    mainClass.set("com.example.app.App")
}

tasks.test {
    useJUnitPlatform()
}
```

--> Notice the shape is identical: `plugins {}`, `repositories {}`, `dependencies {}` -- only syntax details (quotes, semicolons, `.set()` calls) differ. The rest of this file uses Groovy syntax for examples since it's still the more commonly encountered dialect, with Kotlin equivalents noted where the difference matters.

# Project Structure

--> Gradle follows the SAME `src/main/java`, `src/test/java` convention as Maven (via the `java` plugin), which is one reason migrating between the two tools is usually mechanical rather than a full restructure.

```text
my-app/
├── settings.gradle(.kts)     -- declares the project name and, for multi-project builds, which subprojects exist
├── build.gradle(.kts)        -- the main build configuration
├── gradle.properties          -- key-value properties (versions, JVM args for the Gradle daemon, etc.)
├── gradlew, gradlew.bat        -- the Gradle Wrapper -- runs a pinned Gradle version without a global install
├── gradle/wrapper/             -- wrapper jar + properties (which Gradle version to download/use)
├── src/
│   ├── main/java/...
│   ├── main/resources/...
│   ├── test/java/...
│   └── test/resources/...
└── build/                       -- build OUTPUT directory (Gradle's equivalent of Maven's target/, never commit this)
```

--> **The Gradle Wrapper (`gradlew`)** is the standard, recommended way to invoke Gradle -- it's a small script checked into version control that downloads and uses the EXACT Gradle version the project was built with, so every developer (and CI machine) builds with an identical Gradle version without needing a separate global install. Always run `./gradlew ...` (or `gradlew.bat` on Windows) rather than a bare `gradle` command in a real project.

# Tasks -- Gradle's Fundamental Unit of Work

--> Everything Gradle does is a TASK -- compiling is a task (`compileJava`), running tests is a task (`test`), building a jar is a task (`jar`). Unlike Maven's fixed lifecycle phases, tasks form an arbitrary DIRECTED GRAPH of dependencies that Gradle resolves before executing anything.

```bash
./gradlew tasks              # list all available tasks in this project
./gradlew build              # the "do everything" task: compile, test, and assemble the artifact
./gradlew test               # run tests only (and whatever it depends on -- compiling first)
./gradlew clean              # delete the build/ directory
./gradlew clean build        # common combo -- fresh, fully verified build
./gradlew run                # run the application (via the "application" plugin)
./gradlew test --tests "com.example.CalculatorTest"   # run one specific test class
```

**Defining a custom task:**

```groovy
// build.gradle
tasks.register('hello') {
    doLast {
        println 'Hello from a custom Gradle task!'
    }
}

tasks.register('printVersion') {
    group = 'Custom'
    description = 'Prints the current project version'
    doLast {
        println "Version: ${project.version}"
    }
}

// Making one task depend on another -- Gradle guarantees ordering
tasks.named('build') {
    dependsOn 'printVersion'
}
```

--> **`doLast` vs `doFirst`** -- actions added with `doLast {}` run at the end of the task's execution; `doFirst {}` runs at the start. A task can have multiple actions chained this way.
--> **Task inputs/outputs and the UP-TO-DATE check** -- Gradle tracks each task's declared inputs (source files, config values) and outputs (compiled classes, jars). If neither has changed since the last run, Gradle SKIPS re-running the task entirely and reports it `UP-TO-DATE` -- this incremental build behavior is Gradle's headline performance feature over Maven, which re-runs every phase's work on every invocation.

# Dependency Management

## Configurations (Gradle's Equivalent of Maven Scopes)

| Gradle configuration | Roughly equivalent Maven scope | Meaning |
|---|---|---|
| `implementation` | `compile` | Needed to compile & run; NOT exposed to consumers of this project as a library |
| `api` (needs `java-library` plugin) | `compile` (exposed) | Needed to compile & run, AND exposed transitively to consumers |
| `compileOnly` | `provided` | Needed to compile only, not bundled or available at runtime |
| `runtimeOnly` | `runtime` | Needed at runtime only, not needed to compile against |
| `testImplementation` | `test` | Needed to compile & run tests only |
| `testRuntimeOnly` | `test` (runtime) | Needed only when RUNNING tests, e.g. a JUnit engine implementation |

```groovy
dependencies {
    implementation 'com.google.guava:guava:33.2.1-jre'
    testImplementation 'org.junit.jupiter:junit-jupiter-api:5.10.2'
    testRuntimeOnly 'org.junit.jupiter:junit-jupiter-engine:5.10.2'
    compileOnly 'org.projectlombok:lombok:1.18.32'
    runtimeOnly 'com.mysql:mysql-connector-j:8.4.0'
}
```

--> **`implementation` vs `api` is Gradle's biggest improvement on Maven's flat "compile" scope**: if module `service` uses `implementation` for its dependency on library X, then module `web` (which depends on `service`) does NOT automatically get X on its compile classpath -- X is an internal implementation detail of `service`. This keeps build classpaths smaller and avoids leaking internal dependencies, which speeds up recompilation across a multi-module project because changing X doesn't force `web` to recompile too.

## Repositories

```groovy
repositories {
    mavenCentral()               // the default public repo, same as Maven's default
    google()                      // needed for Android dependencies
    maven { url 'https://repo.example.com/internal' }   // a custom/internal repo
    mavenLocal()                   // ~/.m2/repository -- same local cache Maven uses
}
```

## Version Catalogs (Modern Centralized Version Management)

--> A `gradle/libs.versions.toml` file centralizes dependency versions across a whole (potentially multi-module) project -- Gradle's answer to Maven's `dependencyManagement`, but usable in single-module projects too and with IDE autocomplete support.

```toml
# gradle/libs.versions.toml
[versions]
junit = "5.10.2"
guava = "33.2.1-jre"

[libraries]
junit-jupiter = { module = "org.junit.jupiter:junit-jupiter", version.ref = "junit" }
guava = { module = "com.google.guava:guava", version.ref = "guava" }
```

```groovy
// build.gradle -- referencing the catalog
dependencies {
    testImplementation libs.junit.jupiter
    implementation libs.guava
}
```

# Multi-Project Builds

```text
my-platform/
├── settings.gradle(.kts)      <-- declares which subprojects exist
├── build.gradle(.kts)          <-- root-level shared config (often minimal)
├── api/
│   └── build.gradle(.kts)
├── service/
│   └── build.gradle(.kts)
└── web/
    └── build.gradle(.kts)
```

```groovy
// settings.gradle
rootProject.name = 'my-platform'
include 'api', 'service', 'web'
```

```groovy
// service/build.gradle
dependencies {
    implementation project(':api')       // depends on the sibling "api" subproject
    testImplementation libs.junit.jupiter
}
```

```groovy
// root build.gradle -- apply shared config to every subproject
subprojects {
    apply plugin: 'java'
    repositories { mavenCentral() }
    tasks.withType(Test) {
        useJUnitPlatform()
    }
}
```

--> Running `./gradlew build` from the root builds every subproject, and Gradle automatically figures out the correct order from the `project(':api')`-style inter-module dependencies -- same idea as Maven's reactor build order, computed instead from the task/dependency graph.

# Maven vs Gradle -- Side-by-Side Comparison

| Aspect | Maven | Gradle |
|---|---|---|
| Configuration style | Declarative XML (`pom.xml`) | Programmable DSL (Groovy/Kotlin) |
| Build model | Fixed lifecycle of phases | Flexible directed graph of tasks |
| Performance | Re-runs each phase's work every time (older versions); newer Maven has some incremental support | Incremental by default + build cache + daemon process -- generally faster on repeat builds |
| Learning curve | Lower -- mostly filling in a known template | Higher -- real scripting language, more ways to do the same thing |
| Custom build logic | Awkward -- requires writing a custom plugin in Java | Natural -- just write Groovy/Kotlin code in the build script |
| Dependency scope granularity | Coarser (`compile`, `provided`, `runtime`, `test`) | Finer (`implementation` vs `api` split avoids classpath leakage) |
| Ecosystem convention | Extremely standardized, very common in enterprise/Spring shops | Standard for Android; increasingly common for new JVM projects |
| Multi-module builds | Reactor build order via `<modules>` | Task graph via `include` + `project(':x')` |
| Wrapper | `mvnw` exists but historically less universal | `gradlew` is the strongly recommended default from day one |

--> **Neither tool is objectively "better"** -- Maven's rigidity is a feature when you want every project to look the same and be easy for any Java developer to pick up; Gradle's flexibility is a feature when build requirements are complex or performance at scale matters. Most teams pick based on ecosystem fit (Android essentially mandates Gradle; a lot of existing enterprise Java tooling assumes Maven) rather than a from-scratch technical evaluation.

# Common Gotchas and Best Practices

--> **Always commit the Gradle Wrapper files** (`gradlew`, `gradlew.bat`, `gradle/wrapper/*`) -- this is what guarantees every contributor and CI runs the identical, pinned Gradle version. Never assume a global `gradle` install exists or matches the project's expected version.
--> **Prefer `implementation` over `api`** unless you specifically intend for a dependency to be part of your module's public API surface -- defaulting to `api` everywhere defeats the classpath-isolation benefit Gradle offers over Maven.
--> **Avoid dynamic versions** (`'guava:guava:+'` or `'guava:33.+'`) in real projects -- like Maven version ranges, these make builds non-reproducible since the resolved version can silently change between builds.
--> **The Gradle daemon** is a long-running background JVM process that keeps build state warm between invocations for speed -- it's on by default and generally transparent, but is worth knowing about when debugging odd "it worked a second ago" caching issues (`./gradlew --stop` kills all running daemons if you need a truly clean state).
--> **`build/` should never be committed**, same reasoning as Maven's `target/` -- add it to `.gitignore`.
--> **Use `./gradlew`, never a bare `gradle` command**, in any command you'd put in documentation, CI config, or scripts -- a bare `gradle` depends on whatever happens to be globally installed on that machine, defeating the reproducibility the wrapper provides.
