# Why Build Tools Exist

--> Real Java projects are never "one file compiled with `javac`" -- they pull in dozens of third-party libraries (each with their OWN transitive dependencies), need to be compiled, tested, packaged into a `.jar`/`.war`, and often deployed -- doing all of this by hand with raw `javac`/`jar` commands does not scale past a toy project.
--> A build tool automates this entire lifecycle: fetching dependencies (and their dependencies' dependencies), compiling source, running tests, packaging artifacts, and plugging in extra steps (static analysis, code coverage, documentation generation) -- all driven by a single declarative configuration file instead of a pile of shell scripts.
--> **Maven** is the older, convention-heavy build tool for Java (2004) -- it standardized project structure and the build lifecycle across the entire Java ecosystem, which is precisely why almost every Java IDE, tutorial, and CI system understands a Maven project out of the box. **Gradle** (covered in the next file) is the newer, more flexible alternative built on the same core ideas.

# Convention Over Configuration -- The Standard Directory Layout

--> Maven's central philosophy: if everyone agrees on the same project layout, the build tool needs almost no configuration to find your source, tests, and resources -- you only configure the parts that DIFFER from the convention.

```text
my-app/
├── pom.xml                                 -- the project's build configuration ("Project Object Model")
├── src/
│   ├── main/
│   │   ├── java/                           -- application source code (package-per-directory)
│   │   │   └── com/example/app/App.java
│   │   └── resources/                      -- non-code files bundled into the jar (config, templates)
│   │       └── application.properties
│   └── test/
│       ├── java/                           -- test source code, mirrors main/java's package structure
│       │   └── com/example/app/AppTest.java
│       └── resources/                      -- test-only resources (test config, sample data files)
└── target/                                 -- build OUTPUT directory -- compiled classes, jar, reports (never commit this)
```

--> **Why this matters** -- because `src/main/java` is always `src/main/java`, Maven (and your IDE) never needs to be told where the source is. This is the opposite of tools like `make`, where every path must be spelled out explicitly.

# Anatomy of a pom.xml

--> The `pom.xml` ("Project Object Model") is an XML file at the project root that describes the project's identity, dependencies, and build configuration. Every Maven project has exactly one at its root (multi-module projects have one per module, described later).

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                              http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <!-- Coordinates: uniquely identify THIS artifact in any Maven repository -->
    <groupId>com.example</groupId>          <!-- reverse-domain namespace, like a Java package -->
    <artifactId>my-app</artifactId>         <!-- the project/module name -->
    <version>1.0.0-SNAPSHOT</version>       <!-- SNAPSHOT = still under development, mutable -->
    <packaging>jar</packaging>              <!-- jar (default), war, pom (for parent/aggregator modules) -->

    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <junit.version>5.10.2</junit.version>   <!-- custom property, reused below to avoid repetition -->
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>${junit.version}</version>  <!-- references the property above -->
            <scope>test</scope>                   <!-- only needed for compiling/running tests, not shipped -->
        </dependency>
        <dependency>
            <groupId>com.google.guava</groupId>
            <artifactId>guava</artifactId>
            <version>33.2.1-jre</version>
            <!-- no scope specified => defaults to "compile" => needed at compile time AND runtime -->
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>  <!-- runs unit tests during the "test" phase -->
                <version>3.2.5</version>
            </plugin>
        </plugins>
    </build>
</project>
```

--> **The three coordinates (`groupId:artifactId:version`, often abbreviated "GAV")** uniquely identify an artifact anywhere in the Maven universe -- this is exactly what you search for on [Maven Central](https://central.sonatype.com/) and exactly what other projects reference to depend on yours.
--> **`SNAPSHOT` versions** are mutable/in-development builds -- re-publishing the same SNAPSHOT version overwrites the previous one, and Maven will re-check for updates. **Release versions** (e.g. `1.0.0`, no `-SNAPSHOT` suffix) are immutable by convention -- once published, `1.0.0` should never change, so builds referencing it are reproducible forever.

# Dependency Management

## Dependency Scopes

| Scope | Available at compile time | Available at test time | Available at runtime | Packaged in final artifact | Typical use |
|---|---|---|---|---|---|
| `compile` (default) | Yes | Yes | Yes | Yes | Libraries your code directly calls, e.g. Guava |
| `provided` | Yes | Yes | No | No | Container-supplied APIs, e.g. Servlet API on Tomcat |
| `runtime` | No | Yes | Yes | Yes | JDBC drivers -- loaded via reflection, not compiled against |
| `test` | No | Yes | No | No | JUnit, Mockito -- only needed to compile/run tests |
| `system` | Yes | Yes | No | No | Local jar not in any repo (legacy, avoid) |

## Transitive Dependencies

--> If your project depends on library A, and A itself depends on library B, Maven automatically pulls in B too -- this is a TRANSITIVE dependency, and it's what makes modern dependency management tractable (you don't manually chase every dependency-of-a-dependency).

```text
my-app
 └── spring-web (direct dependency you declared)
      ├── spring-core        (transitive -- pulled in automatically)
      ├── spring-beans       (transitive)
      └── commons-logging    (transitive)
```

--> **Dependency conflicts** happen when two different dependency paths pull in DIFFERENT versions of the same library (the "diamond dependency" problem). Maven resolves this using "nearest wins" -- the version declared closest to your project in the dependency tree wins, with ties broken by declaration order in the `pom.xml`.
--> Run `mvn dependency:tree` to see the full resolved dependency graph, including which versions won and why -- this is the first command to reach for when you get a mysterious `NoSuchMethodError` or `ClassNotFoundException` at runtime (a classic symptom of the wrong version of a transitive dependency winning).
--> **Excluding a transitive dependency** that's causing a conflict or pulling in something unwanted:

```xml
<dependency>
    <groupId>org.example</groupId>
    <artifactId>some-library</artifactId>
    <version>2.0.0</version>
    <exclusions>
        <exclusion>
            <groupId>commons-logging</groupId>
            <artifactId>commons-logging</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

## The `dependencyManagement` Section

--> `dependencyManagement` declares versions CENTRALLY without actually adding the dependency to the build -- child modules (in a multi-module project) or the project itself can then declare the same dependency WITHOUT specifying a version, inheriting it from here. This is how large projects keep every module using consistent, compatible versions of shared libraries.

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-dependencies</artifactId>
            <version>3.3.2</version>
            <type>pom</type>
            <scope>import</scope>   <!-- "import" pulls in another pom's dependencyManagement wholesale -->
        </dependency>
    </dependencies>
</dependencyManagement>
```

## Where Dependencies Come From -- Repositories

--> **Maven Central** is the default, public repository almost every open-source Java library publishes to -- Maven checks here automatically, no configuration needed.
--> **Local repository** (`~/.m2/repository`) -- every dependency Maven ever downloads is cached here, so repeat builds (and other projects on the same machine needing the same dependency) don't re-download it.
--> **Private/internal repositories** (Nexus, Artifactory, GitHub Packages) -- companies host their own internal libraries here, and configure them as additional `<repositories>` in the `pom.xml` or in `~/.m2/settings.xml` (the latter is preferred for anything requiring credentials, since `settings.xml` is not committed to version control).

# The Build Lifecycle

--> Maven's build process is organized into LIFECYCLES, each made of an ordered sequence of PHASES. Running any phase automatically runs every phase before it in the sequence -- you never run "just testing" in isolation without also compiling first, because `test` depends on everything before it.

```text
validate  ->  compile  ->  test  ->  package  ->  verify  ->  install  ->  deploy
   |             |           |           |            |           |            |
  check       compile      run unit   bundle into   run          copy to    upload to
  project     src/main/    tests      a jar/war     integration  local      remote
  is correct  java                                  tests, QA    ~/.m2      repository
                                                      checks      repo       (shared)
```

| Phase | What it does |
|---|---|
| `validate` | Checks the project structure and `pom.xml` are correct before doing any real work |
| `compile` | Compiles `src/main/java` into `target/classes` |
| `test` | Runs unit tests (via Surefire plugin) against the compiled classes -- fails the build on a test failure |
| `package` | Bundles compiled code + resources into a distributable format (`target/my-app-1.0.0.jar`) |
| `verify` | Runs any additional checks on the packaged artifact (e.g. integration tests via Failsafe plugin) |
| `install` | Copies the packaged artifact into the LOCAL repo (`~/.m2/repository`) so other local projects can depend on it |
| `deploy` | Uploads the artifact to a REMOTE/shared repository, making it available to other developers/teams |

--> `mvn install` runs `validate -> compile -> test -> package -> verify -> install`, in that order, every time -- there's no way to "skip to install" without everything before it running first (short of explicitly skipping tests, see below).
--> There is also a separate CLEAN lifecycle (`mvn clean` deletes `target/`) and a SITE lifecycle (generates project documentation) -- these are independent of the default build lifecycle above.

```bash
mvn compile              # compile only
mvn test                 # compile + run tests
mvn package               # compile + test + bundle into jar/war
mvn install                # ...all the way through installing to ~/.m2
mvn clean install          # wipe target/ first, then do a full clean build -- the most common command in practice
mvn test -DskipTests=false -Dtest=CalculatorTest   # run only one specific test class
mvn package -DskipTests     # package WITHOUT running tests (fast, but risky -- use sparingly, e.g. local iteration)
```

--> **`-DskipTests` vs `-Dmaven.test.skip=true`** -- `-DskipTests` still COMPILES the tests but doesn't run them; `-Dmaven.test.skip=true` skips compiling them too. Prefer `-DskipTests` when you just want to speed up a local build without hiding a broken test compilation.

# Plugins and Goals

--> Almost everything Maven actually DOES is implemented by a PLUGIN -- even compiling is delegated to the `maven-compiler-plugin`. A plugin is a collection of GOALS (individual tasks, like `compiler:compile` or `surefire:test`), and phases in the lifecycle are just bound to specific goals by convention.
--> You can run a goal directly without going through a phase: `mvn dependency:tree`, `mvn compiler:compile`, `mvn org.apache.maven.plugins:maven-help-plugin:evaluate` -- useful for diagnostics without triggering a full build.

```xml
<build>
    <plugins>
        <!-- Explicitly pin the compiler plugin version + Java release for reproducible builds -->
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.13.0</version>
            <configuration>
                <release>17</release>
            </configuration>
        </plugin>

        <!-- Package everything (app + all dependencies) into one runnable "fat jar" -->
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-shade-plugin</artifactId>
            <version>3.5.3</version>
            <executions>
                <execution>
                    <phase>package</phase>
                    <goals><goal>shade</goal></goals>
                    <configuration>
                        <transformers>
                            <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                                <mainClass>com.example.app.App</mainClass>
                            </transformer>
                        </transformers>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

--> **Common plugins worth knowing by name**: `maven-compiler-plugin` (compiling), `maven-surefire-plugin` (unit tests), `maven-failsafe-plugin` (integration tests, phase `verify`), `maven-jar-plugin`/`maven-war-plugin` (packaging), `maven-shade-plugin`/`maven-assembly-plugin` (fat/uber jars bundling all dependencies), `jacoco-maven-plugin` (code coverage, covered in file 05), `spring-boot-maven-plugin` (runnable Spring Boot jars).

# Multi-Module Projects

--> A large application is often split into multiple Maven MODULES (e.g. `api`, `service`, `persistence`, `web`) that build together but can be developed, tested, and versioned somewhat independently -- each module is its own mini-project with its own `pom.xml`, all tied together by a PARENT pom.

```text
my-platform/                          (parent -- packaging "pom", no source of its own)
├── pom.xml                           <-- aggregator + shared config
├── api/
│   └── pom.xml                       <-- module, packaging "jar"
├── service/
│   └── pom.xml                       <-- depends on "api" module
└── web/
    └── pom.xml                       <-- depends on "service", packaging "war"
```

**Parent `pom.xml` (aggregator):**
```xml
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example</groupId>
    <artifactId>my-platform</artifactId>
    <version>1.0.0</version>
    <packaging>pom</packaging>          <!-- "pom" packaging => this module has no code, just organizes others -->

    <modules>
        <module>api</module>
        <module>service</module>
        <module>web</module>
    </modules>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
    </properties>

    <dependencyManagement>              <!-- centralizes versions for ALL child modules -->
        <dependencies>
            <dependency>
                <groupId>org.junit.jupiter</groupId>
                <artifactId>junit-jupiter</artifactId>
                <version>5.10.2</version>
                <scope>test</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
```

**Child `service/pom.xml`:**
```xml
<project>
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.example</groupId>
        <artifactId>my-platform</artifactId>
        <version>1.0.0</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>service</artifactId>    <!-- groupId + version inherited from parent, no need to repeat -->

    <dependencies>
        <dependency>
            <groupId>com.example</groupId>
            <artifactId>api</artifactId>       <!-- depends on the sibling "api" module -->
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <!-- no version needed -- inherited from parent's dependencyManagement -->
        </dependency>
    </dependencies>
</project>
```

--> Running `mvn install` from the PARENT directory builds every module in the correct dependency order automatically (Maven computes the "reactor build order" from the inter-module dependencies) -- you never need to manually figure out that `api` must build before `service`.
--> **`<parent>` vs `<dependencyManagement>` inheritance** -- a child inherits properties, plugin config, and dependency versions from its `<parent>`; that's DIFFERENT from a regular dependency, which just makes another artifact's classes available on the classpath.

# Common Gotchas and Best Practices

--> **Never commit `target/`** -- it's fully regenerable build output; add it to `.gitignore`.
--> **Pin plugin versions explicitly.** Omitting a plugin's `<version>` lets Maven pick "whatever is newest," which silently changes your build behavior over time and breaks reproducibility -- always specify exact versions for plugins (and consider a `pluginManagement` section in multi-module parents for consistency).
--> **Avoid version ranges** (`[1.0,2.0)`) for dependencies in most cases -- they make builds non-reproducible, since the resolved version can change between builds as new releases are published. Pin exact versions instead.
--> **`mvn dependency:tree` is your best diagnostic tool** for "why is this old/wrong version of a library on my classpath" -- always reach for it before manually guessing at exclusions.
--> **SNAPSHOT dependencies should never ship to production** -- they're mutable and can change underneath you; only release (non-SNAPSHOT) versions should appear in a production build's resolved dependency tree.
--> **`mvn clean install` vs `mvn install`** -- if `target/` has stale compiled classes from a previous run (e.g. after renaming/deleting a file), skipping `clean` can occasionally leave orphaned `.class` files around; when in doubt, especially after structural changes, do a `clean install`.
