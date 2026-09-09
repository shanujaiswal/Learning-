# Why Containers Fit Microservices So Naturally

--> A microservices architecture (chapter 01) produces many small, independently deployable services -- each needing its own runtime environment, its own dependencies, its own process isolation from every other service. Running all of them as bare JAR files on shared VMs quickly becomes an operational mess: "works on my machine" version mismatches, port collisions, dependency conflicts between services sharing one host's JVM installation. **Containers** solve this by packaging a service together with its ENTIRE runtime environment (JVM, OS libraries, config) into one portable, isolated unit that runs identically on a developer's laptop, a CI runner, and a production server.

# Dockerizing a Spring Boot Application

--> A **Dockerfile** is a recipe describing how to build a container image -- a step-by-step set of instructions Docker follows to produce a reusable, versioned artifact containing the application and everything it needs to run.

## A Basic Dockerfile

```dockerfile
# Dockerfile
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy the already-built fat JAR (produced by mvn package / gradle bootJar) into the image
COPY target/inventory-service-1.0.0.jar app.jar

# Document which port the app listens on -- informational, doesn't actually publish it
EXPOSE 8080

# The command that runs when a container is started FROM this image
ENTRYPOINT ["java", "-jar", "app.jar"]
```

--> **`eclipse-temurin:21-jre-alpine`** -- a minimal base image containing ONLY a JRE (not a full JDK, since the image just needs to RUN compiled code, not compile it) on Alpine Linux (a small, security-focused distribution) -- keeping the final image size down, which matters for pull speed and attack surface.
--> **Build and run it locally:**

```text
docker build -t inventory-service:1.0.0 .
docker run -p 8080:8080 inventory-service:1.0.0
#              ^host:container -- maps port 8080 on the host machine to port 8080 inside the container
```

## Multi-Stage Builds -- Building AND Running in One Dockerfile

--> A **multi-stage build** uses one stage (with a full JDK + build tool) to COMPILE the application, then copies only the resulting JAR into a second, much leaner runtime stage -- so the final image never contains the JDK, Maven/Gradle cache, or source code, only the compiled artifact.

```dockerfile
# Dockerfile -- multi-stage
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /build/target/inventory-service-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

--> **Why this matters** -- a single-stage Dockerfile that installs a JDK and Maven just to build, then ships all of that INSIDE the final runtime image, needlessly bloats image size and attack surface; multi-stage builds are the standard pattern for compiled languages in Docker specifically to avoid this.

# docker-compose for Local Multi-Service Development

--> A single microservice rarely runs alone even locally -- it typically needs a database, maybe a message broker, maybe other services it calls. **docker-compose** describes a whole local stack of containers, their configuration, and how they network together, in one YAML file, started with a single command.

```yaml
# docker-compose.yml
version: "3.9"

services:
  inventory-service:
    build: .                                    # build from the local Dockerfile
    ports:
      - "8080:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://inventory-db:5432/inventory
      SPRING_DATASOURCE_USERNAME: inventory_user
      SPRING_DATASOURCE_PASSWORD: changeme
    depends_on:
      - inventory-db

  inventory-db:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: inventory
      POSTGRES_USER: inventory_user
      POSTGRES_PASSWORD: changeme
    ports:
      - "5432:5432"
    volumes:
      - inventory-db-data:/var/lib/postgresql/data   # persists data across container restarts

  redis-cache:
    image: redis:7-alpine
    ports:
      - "6379:6379"

volumes:
  inventory-db-data:
```

```text
docker-compose up --build       # builds inventory-service's image and starts every service in the stack
docker-compose down             # stops and removes containers (add -v to also drop named volumes)
docker-compose logs -f inventory-service     # tail logs for just one service
```

--> **`inventory-db` as a hostname** -- inside the compose-created network, `inventory-service` can reach the database simply via the hostname `inventory-db` (the service's own name in the compose file) -- Docker Compose's built-in DNS resolves service names to the right container automatically, no manual IP management needed.
--> **`depends_on` only controls START ORDER, not readiness** -- `depends_on: [inventory-db]` ensures the database container STARTS before the app container, but does NOT wait for Postgres to actually finish initializing and accept connections -- a genuinely fast-starting app can still fail its first connection attempt. Production-grade compose setups add a `healthcheck` block and `depends_on: condition: service_healthy` to wait for genuine readiness, not just process start.

# Kubernetes Concepts -- Just Enough to Understand Java Microservices in Production

--> **Kubernetes (K8s)** is a container ORCHESTRATION platform -- where docker-compose is fine for one developer's local machine, Kubernetes manages containers across a CLUSTER of many machines, handling scheduling, scaling, self-healing, and networking at production scale. A full Kubernetes course is out of scope here, but every Java backend developer benefits from knowing these core building blocks.

```text
                              Kubernetes Cluster
   +----------------------------------------------------------------+
   |   Deployment: inventory-service (desired replicas: 3)          |
   |     +-----------+   +-----------+   +-----------+              |
   |     |   Pod 1    |   |   Pod 2    |   |   Pod 3    |              |
   |     | container: |   | container: |   | container: |              |
   |     | inventory- |   | inventory- |   | inventory- |              |
   |     |  service   |   |  service   |   |  service   |              |
   |     +-----------+   +-----------+   +-----------+              |
   |            ^               ^               ^                    |
   |            +---------------+---------------+                    |
   |                            |                                     |
   |                    Service: inventory-service                     |
   |                (stable internal DNS name + load balancing         |
   |                 across whichever Pods are currently healthy)      |
   +----------------------------------------------------------------+
```

| Concept | What it is |
|---|---|
| **Pod** | The smallest deployable unit in Kubernetes -- usually wraps exactly ONE container (your Spring Boot app's container), though a Pod can hold multiple tightly-coupled containers. Pods are ephemeral -- Kubernetes can kill and recreate them at any time (a node failing, a rolling deploy, autoscaling). |
| **Deployment** | Declares "I want N replicas of this Pod running at all times" -- Kubernetes continuously reconciles reality toward that desired state, restarting failed Pods and enabling rolling updates (replacing old-version Pods with new-version ones a few at a time, with zero downtime if configured correctly). |
| **Service** | A stable network identity (a DNS name + virtual IP) that load-balances traffic across whichever Pods currently match a label selector -- solves the exact "Pods come and go, addresses change constantly" problem that service discovery (chapter 02) solves in a Eureka-based setup; Kubernetes' Service IS a form of native, server-side service discovery. |
| **ConfigMap / Secret** | Externalized configuration and sensitive values, injected into Pods as environment variables or mounted files -- conceptually similar in purpose to Spring Cloud Config Server (chapter 05), but managed natively by the cluster. |
| **Ingress** | Routes external HTTP(S) traffic into the cluster to the right Service based on hostname/path -- plays a role similar to an API Gateway (chapter 02) at the cluster's edge. |
| **Namespace** | A logical partition within a cluster (e.g. separating `staging` and `production` workloads on the same physical cluster). |

## A Minimal Deployment + Service YAML

```yaml
# deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: inventory-service
spec:
  replicas: 3
  selector:
    matchLabels:
      app: inventory-service
  template:
    metadata:
      labels:
        app: inventory-service
    spec:
      containers:
        - name: inventory-service
          image: myregistry.example.com/inventory-service:1.0.0
          ports:
            - containerPort: 8080
          env:
            - name: SPRING_DATASOURCE_URL
              valueFrom:
                configMapKeyRef:
                  name: inventory-config
                  key: datasource-url
          readinessProbe:                       # Kubernetes only routes traffic to this Pod once this passes
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 10
          livenessProbe:                        # Kubernetes restarts the Pod if this starts failing
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 30
---
# service.yaml
apiVersion: v1
kind: Service
metadata:
  name: inventory-service
spec:
  selector:
    app: inventory-service       # routes to any Pod carrying this label -- ties back to the Deployment's template labels
  ports:
    - port: 80
      targetPort: 8080
```

--> **`readinessProbe` vs `livenessProbe` -- a genuinely important distinction**: a **readiness** probe failing tells Kubernetes "don't send this Pod traffic yet" (e.g. still warming up a cache, or its database connection isn't ready) WITHOUT killing it; a **liveness** probe failing tells Kubernetes "this Pod is stuck/deadlocked, kill and restart it." Spring Boot Actuator's `/actuator/health/readiness` and `/actuator/health/liveness` endpoints (enabled via the `spring-boot-starter-actuator` dependency plus Kubernetes-probe-aware health groups) map directly onto these two checks.

# How This All Fits Together

```text
Developer writes Java code
        |
        v
Multi-stage Dockerfile builds a container image
        |
        v
docker-compose (local dev) OR CI pipeline builds/pushes the image to a registry
        |
        v
Kubernetes Deployment pulls the image, runs N replica Pods
        |
        v
Kubernetes Service load-balances traffic across healthy Pods (readinessProbe-gated)
        |
        v
Ingress (or an API Gateway, chapter 02) routes external traffic to the right Service
```

# Common Gotchas

--> **Shipping a JDK (not just a JRE) in the final runtime image** -- unnecessarily bloats image size and attack surface; use multi-stage builds so only the compiled JAR and a JRE make it into the final image.
--> **Assuming `depends_on` in docker-compose waits for genuine readiness** -- it only waits for the dependency's container process to START, not for a database to finish initializing; add explicit healthchecks for real readiness-gating.
--> **No `readinessProbe` configured in Kubernetes** -- without one, Kubernetes may route live traffic to a Pod that's still starting up (JVM warming, cache not populated, DB connection pool not established yet), causing a burst of errors right after every deploy/scale event.
--> **Confusing liveness and readiness probes** -- a liveness probe that's too aggressive (short timeout, low failure threshold) can cause Kubernetes to repeatedly kill and restart a Pod that's just temporarily slow (e.g. under GC pause or heavy load), making a slow-but-recoverable situation into a crash-loop.
--> **Hardcoding configuration (URLs, credentials) directly into a Docker image** -- defeats the purpose of building one image and deploying it identically across dev/staging/production; externalize via environment variables, ConfigMaps/Secrets, or a config server instead.
--> **Not setting explicit JVM memory flags inside a container** -- older JVMs could misdetect available memory inside a cgroup-limited container and use host-wide memory figures instead of the container's actual limit, leading to OOM-kills; modern JDKs (10+) are container-aware by default, but it's still worth explicitly setting `-XX:MaxRAMPercentage` when a container's memory limit is tightly constrained.

# Best Practices Summary

--> Use multi-stage Dockerfiles so the final runtime image contains only a JRE and the compiled application, never build tools or source code.
--> Use docker-compose for local multi-service development, with explicit healthchecks (not just `depends_on`) when genuine startup-order dependencies matter.
--> Expose Spring Boot Actuator's health endpoints and wire them into Kubernetes' `readinessProbe`/`livenessProbe` so the platform can make correct traffic-routing and restart decisions automatically.
--> Externalize all environment-specific configuration (via env vars, ConfigMaps, Secrets, or a config server) -- never bake environment-specific values into the container image itself.
--> Treat a Kubernetes Service as the cluster-native equivalent of service discovery, and an Ingress as the cluster-native equivalent of an API Gateway's edge-routing role, when reasoning about how the concepts in earlier chapters map onto a Kubernetes deployment.
--> Version container images explicitly (e.g. `1.0.0`, a git SHA) rather than relying on `latest`, so deployments are reproducible and rollbacks are possible.
