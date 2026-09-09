# GCP's Approach -- Container-First, With Serverless Everywhere

--> Google Cloud's Java story is shaped by one distinctive theme running through nearly every compute option: containers are the default unit of deployment, even in the "serverless" products. Where AWS Lambda and Azure Functions expect a language-specific handler function, GCP's flagship serverless product (**Cloud Run**) expects a Docker container that listens on a port -- meaning a lot of what you already know about Dockerizing a Spring Boot app from chapter 06 carries over almost unchanged.

```text
                    Less operational control, more GCP-managed
   GKE (Kubernetes)  -->  App Engine Flexible  -->  Cloud Run  -->  Cloud Functions
   (you manage           (GCP manages the VM,       (GCP manages    (GCP manages
    Kubernetes)           you manage a container)    everything,    everything;
                                                       container-     you manage
                                                       based)          functions)
```

# Google App Engine -- Standard vs Flexible for Java

--> **App Engine** is GCP's original PaaS, and it comes in two genuinely different environments for Java, not just two tiers of the same thing.

## App Engine Standard

--> Runs Java code inside a GCP-managed, sandboxed **Java 17/21 runtime** -- you deploy source/JAR, not a container, and the platform scales instances up and down (including to ZERO) automatically based on traffic, billing per request rather than for continuously running instances.

```yaml
# app.yaml -- App Engine Standard
runtime: java21
instance_class: F2

env_variables:
  SPRING_PROFILES_ACTIVE: "production"

automatic_scaling:
  min_instances: 0
  max_instances: 10
  target_cpu_utilization: 0.65
```

```text
mvn clean package
gcloud app deploy
```

--> **Sandboxing constraints** -- Standard's runtime restricts some things a full JVM app might otherwise assume freely available: no writing to the local filesystem outside `/tmp` (and even that is ephemeral, wiped between instance restarts), no arbitrary native code/JNI, and no long-lived background threads that outlive a request in older generations of the sandbox (loosened considerably in the second-generation Java 11+ runtimes, which behave much closer to a normal JVM than the original Java 8 sandbox did).
--> **Scale-to-zero** is the headline benefit -- an idle Standard service costs nothing beyond storage, at the cost of a cold start on the next request (a genuine, if usually smaller than Lambda's, JVM startup penalty).

## App Engine Flexible

--> Runs your app inside a **Docker container on a managed Compute Engine VM** -- effectively "App Engine's deployment experience, but you supply a Dockerfile" -- removing Standard's sandboxing restrictions (a real filesystem, background threads, native libraries all work normally) at the cost of NOT scaling to zero (Flexible always keeps at least one instance running) and slower scaling/deploy times, since it's provisioning VM-backed instances rather than lightweight sandboxed ones.

```yaml
# app.yaml -- App Engine Flexible
runtime: custom
env: flex

resources:
  cpu: 1
  memory_gb: 2

automatic_scaling:
  min_num_instances: 1
  max_num_instances: 5
```

--> **Standard vs Flexible, side by side:**

| Factor | App Engine Standard | App Engine Flexible |
|---|---|---|
| **Deployment unit** | JAR/source, GCP-managed runtime | Docker container |
| **Scales to zero?** | Yes | No (min 1 instance always running) |
| **Cold start** | Seconds | Not applicable (always warm), but slower to scale up |
| **Filesystem/native code** | Restricted | Unrestricted (full VM) |
| **Deploy speed** | Fast | Slower (builds/boots a VM-backed container) |
| **Best fit** | Bursty or low-traffic APIs where cost matters | Apps needing full OS access or custom runtime dependencies |

--> **In practice** -- Flexible has increasingly been superseded by Cloud Run for most new projects, since Cloud Run offers a comparable "bring your own container" model WITH scale-to-zero, which Flexible lacks; App Engine Standard remains relevant specifically for its GCP-managed-runtime simplicity when you don't want to write a Dockerfile at all.

# Cloud Run -- Containerized Java, Serverless Economics

--> **Cloud Run** runs any container that listens on a port (`PORT` environment variable, which Cloud Run sets and your app must respect) as a fully managed, auto-scaling, scale-to-zero service -- combining App Engine Flexible's "just bring a container" simplicity with App Engine Standard's scale-to-zero economics. This has become GCP's default recommendation for containerized Java workloads.

```dockerfile
# Dockerfile -- same multi-stage pattern from chapter 06, unchanged
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /build/target/inventory-service-1.0.0.jar app.jar
# Cloud Run injects $PORT at runtime -- the app must listen on it, not a hardcoded 8080
ENTRYPOINT exec java -jar app.jar --server.port=${PORT:-8080}
```

```text
gcloud builds submit --tag gcr.io/inventory-project/inventory-service
gcloud run deploy inventory-service \
  --image gcr.io/inventory-project/inventory-service \
  --platform managed \
  --region us-central1 \
  --min-instances 0 --max-instances 10 \
  --set-env-vars SPRING_PROFILES_ACTIVE=production \
  --set-secrets SPRING_DATASOURCE_PASSWORD=inventory-db-password:latest
```

--> **`$PORT` is not optional** -- unlike ECS/Fargate or Kubernetes where you declare a fixed `containerPort`, Cloud Run assigns the listening port dynamically via the `PORT` env var; a Spring Boot app must read `server.port` from it (`--server.port=${PORT}` or `SERVER_PORT` env var, which Spring Boot maps automatically) rather than hardcoding 8080 in `application.properties`.
--> **`--min-instances 0` vs `--min-instances 1`** -- setting a minimum of zero gets the full scale-to-zero cost benefit but reintroduces a cold start on the next request after idle; setting a minimum of one keeps a warm instance running continuously (costing money even when idle) specifically to avoid that -- the same fundamental tradeoff seen with Lambda's Provisioned Concurrency and Azure Functions' Premium plan.
--> **Concurrency per instance** -- unlike Lambda (one invocation per execution environment), a single Cloud Run instance can serve MULTIPLE concurrent requests (configurable, default 80) by relying on the JVM's own thread-per-request handling inside the container -- meaning a Cloud Run Java service behaves much more like a normal multi-threaded Spring Boot app under load than like a swarm of single-purpose Lambda invocations.
--> **Cloud Run jobs** -- a separate mode (`gcloud run jobs`) for run-to-completion batch/task workloads (data migrations, scheduled report generation) rather than request-serving services, filling a niche Lambda/Azure Functions cover via timer triggers.

# Google Kubernetes Engine (GKE) -- Brief Mention

--> **GKE** is Google's managed Kubernetes offering -- notably, GKE is where Kubernetes itself originated conceptually (Google open-sourced the ideas behind Borg, its internal cluster manager, as Kubernetes), so GKE tends to track upstream Kubernetes releases closely and offers **GKE Autopilot**, a mode where Google manages node provisioning/sizing entirely (you specify Pod resource requests, Google handles the underlying nodes), reducing a substantial chunk of the operational burden regular GKE (and self-managed Kubernetes generally) still carries. The Deployment/Service/Ingress YAML from chapter 06 applies unchanged.
--> **GKE vs Cloud Run for a Java microservices fleet** -- Cloud Run is simpler and covers the large majority of "stateless HTTP service" use cases with less operational overhead; GKE (especially Autopilot) is the better fit when you need Kubernetes-specific features Cloud Run doesn't expose (StatefulSets, custom operators, fine-grained networking policies, sidecar-heavy service mesh setups) or true multi-cloud Kubernetes portability.

# Cloud Functions with Java

--> **Cloud Functions** is GCP's function-as-a-service offering (2nd generation Cloud Functions is now built ON TOP OF Cloud Run under the hood, which is worth knowing since it explains why its scaling/concurrency behavior looks more Cloud-Run-like than classic FaaS).

```java
// InventoryLookupFunction.java
public class InventoryLookupFunction implements HttpFunction {

    private final InventoryRepository repository = new InventoryRepository();

    @Override
    public void service(HttpRequest request, HttpResponse response) throws Exception {
        String sku = request.getFirstQueryParameter("sku").orElseThrow();
        InventoryItem item = repository.findBySku(sku);

        response.setContentType("application/json");
        response.getWriter().write(toJson(item));
    }
}
```

```xml
<!-- pom.xml -->
<plugin>
  <groupId>com.google.cloud.functions</groupId>
  <artifactId>function-maven-plugin</artifactId>
  <version>0.11.0</version>
</plugin>
```

```text
gcloud functions deploy inventoryLookup \
  --gen2 \
  --runtime=java21 \
  --trigger-http \
  --entry-point=InventoryLookupFunction \
  --memory=512MB
```

--> **Same Java cold-start reality as Lambda/Azure Functions** -- JVM startup and classloading dominate cold-start latency; the mitigations are the same in spirit (minimize dependencies, avoid a full Spring context for a single function, configure a minimum instance count on 2nd-gen functions to keep instances warm, analogous to Lambda's Provisioned Concurrency).
--> **1st gen vs 2nd gen** -- 2nd generation Cloud Functions (built on Cloud Run + Eventarc) support longer request timeouts, larger instances, concurrency per instance, and more event source types than 1st generation; for new Java functions there's little reason to choose 1st gen today.
--> **When to just use Cloud Run instead** -- since 2nd-gen Cloud Functions runs on Cloud Run infrastructure anyway, a function with more than one HTTP route, or any real routing/middleware logic, is often better expressed directly as a small Cloud Run service (a thin Spring Boot app) rather than contorted into a single-entry-point function -- Cloud Functions earns its keep for genuinely single-purpose, event-triggered logic (a Pub/Sub message handler, a Cloud Storage upload trigger).

# Common Gotchas

--> **Hardcoding port 8080 instead of reading `$PORT` for Cloud Run** -- the container will fail its startup health check and never receive traffic if it doesn't bind to the port Cloud Run actually assigned.
--> **Choosing App Engine Flexible expecting scale-to-zero** -- Flexible always keeps at least one instance running (billed continuously); if scale-to-zero matters, Cloud Run is very likely the better modern choice for the same "bring a container" workflow.
--> **Deploying to App Engine Standard with code that needs real filesystem writes or native libraries** -- the sandboxed runtime will reject or silently fail such operations; either work within `/tmp` (ephemeral) and cloud storage, or move to Flexible/Cloud Run/GKE.
--> **Ignoring Cloud Run's per-instance concurrency setting** -- leaving it at defaults for a Java app with blocking I/O and a small thread pool can cause request queueing under load; tune `--concurrency` relative to the app's actual thread pool sizing.
--> **Treating 1st-gen Cloud Functions as the default for new Java functions** -- 2nd generation is strictly more capable and is the recommended starting point; 1st gen mainly exists for backward compatibility with older deployments.

# Best Practices Summary

--> Default to Cloud Run for new containerized Java workloads on GCP -- it combines App Engine Flexible's "just bring a Dockerfile" simplicity with real scale-to-zero economics.
--> Reserve App Engine Standard for cases where you specifically want to avoid writing a Dockerfile at all and can live within its sandboxed runtime's constraints.
--> Choose GKE (ideally Autopilot) only when Kubernetes-specific capabilities or multi-cloud portability are actual requirements, not a default starting point.
--> For Cloud Functions, use 2nd generation, keep functions genuinely single-purpose, and reach for Cloud Run instead once a "function" grows real routing or multiple endpoints.
--> Always read the listening port from `$PORT` in Cloud Run deployments (and equivalently respect each platform's expected runtime contract) rather than hardcoding values that work locally but break once deployed.
--> Apply the same cold-start mitigations across all GCP FaaS-style products (Cloud Functions, and Cloud Run scaled to zero) as covered for AWS Lambda -- minimal dependencies, lightweight frameworks where possible, and a deliberate minimum-instance setting when latency matters more than cost.
