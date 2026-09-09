# Dockerfile
#
# Companion container-build recipe for 06_InventoryServiceApplication.java.
#
# Covers Theory chapter:
#   17) Microservices and Spring Cloud/Theory/06 Containerization and Orchestration Basics for Microservices.md
#
# IMPORTANT -- illustrative, requires real setup to build/run as-is:
#   - A real Maven project (pom.xml + src/main/java/...) containing
#     06_InventoryServiceApplication.java's classes, producing
#     target/inventory-service-1.0.0.jar via `mvn package`.
#   - This multi-stage build additionally expects that pom.xml + src/ to be
#     present in THIS build context if you use the build stage below; if you
#     only have the already-built JAR, use the simpler single-stage version
#     shown commented out at the bottom instead.
#
# Build + run (after a real `mvn package` has produced the JAR, or using the
# multi-stage build below to compile it inside Docker itself):
#   docker build -t inventory-service:1.0.0 .
#   docker run -p 8080:8080 inventory-service:1.0.0
#
# Multi-stage build -- Stage 1 compiles with a full JDK + Maven, Stage 2 ships
# only the compiled JAR on a minimal JRE, so the JDK/build tools/source code
# never end up in the final runtime image (see Theory chapter's "Why this
# matters" section on image size and attack surface).

# ---- Stage 1: build ----
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B package -DskipTests

# ---- Stage 2: runtime ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy only the compiled fat JAR from the build stage -- no JDK, no Maven
# cache, no source code makes it into this final image.
COPY --from=build /build/target/inventory-service-1.0.0.jar app.jar

# Documents which port the app listens on -- informational only, does not
# actually publish the port (that happens via `docker run -p` / compose `ports:`).
EXPOSE 8080

# Explicit container-aware JVM memory flag -- worth setting deliberately even
# though modern JDKs (10+) are container-aware by default, per the Theory
# chapter's gotcha about tightly memory-constrained containers.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]

# ---------------------------------------------------------------------------
# Simpler SINGLE-STAGE alternative (uncomment and delete the two stages above
# if the JAR is already built locally via `mvn package` BEFORE `docker build`,
# rather than compiling inside the Docker build itself):
#
# FROM eclipse-temurin:21-jre-alpine
# WORKDIR /app
# COPY target/inventory-service-1.0.0.jar app.jar
# EXPOSE 8080
# ENTRYPOINT ["java", "-jar", "app.jar"]
# ---------------------------------------------------------------------------
