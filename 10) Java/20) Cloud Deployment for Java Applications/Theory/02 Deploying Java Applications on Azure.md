# Azure's Java Deployment Landscape

--> Microsoft has invested heavily in first-class Java support on Azure -- partly because a large share of Azure's enterprise customer base runs Java/Spring workloads, and partly through its ongoing collaboration with the Spring team (Microsoft co-maintains Spring Cloud Azure and employs Spring committers). The result is that Java on Azure feels less like "a JVM crammed onto someone else's cloud" and more like a genuinely supported first-class citizen, with tooling (Maven/Gradle plugins, the Azure Toolkit for IntelliJ) built specifically for Java developers rather than adapted from generic tooling.

```text
                    Less operational control, more Azure-managed
   AKS (Kubernetes)  -->  App Service  -->  Azure Spring Apps  -->  Azure Functions
   (you manage           (Azure manages     (Azure manages Spring   (Azure manages
    Kubernetes            the VM/platform,   AND the platform,       everything;
    yourself)              you manage app)   config server, etc.)    you manage
                                                                       functions)
```

# Azure App Service for Java

--> **App Service** is Azure's PaaS offering for web applications -- broadly the same category as AWS Elastic Beanstalk -- you deploy a JAR/WAR or a container image, and Azure handles the underlying VM, OS patching, load balancing, and (optionally) auto-scaling, while exposing configuration knobs for JVM options, custom domains, and deployment slots.

## Deploying a Spring Boot JAR

```text
# Package the app normally
mvn clean package

# Using the Azure CLI
az webapp up --name inventory-service --resource-group inventory-rg \
             --runtime "JAVA:21-java21" --sku B1
```

--> Maven and Gradle also have dedicated **Azure Web App plugins** that let you deploy directly from the build tool without hand-writing CLI scripts -- common in real projects because it keeps deployment configuration versioned alongside the code.

```xml
<!-- pom.xml -->
<plugin>
  <groupId>com.microsoft.azure</groupId>
  <artifactId>azure-webapp-maven-plugin</artifactId>
  <version>2.14.0</version>
  <configuration>
    <resourceGroup>inventory-rg</resourceGroup>
    <appName>inventory-service</appName>
    <runtime>
      <os>Linux</os>
      <javaVersion>Java 21</javaVersion>
      <webContainer>Java SE</webContainer>
    </runtime>
    <appSettings>
      <property>
        <name>SPRING_DATASOURCE_URL</name>
        <value>${azure.db.jdbc.url}</value>
      </property>
    </appSettings>
  </configuration>
</plugin>
```

```text
mvn azure-webapp:deploy
```

--> **App Settings vs Connection Strings** -- App Service exposes two separate mechanisms for externalized config: **Application Settings** (arbitrary key-value pairs, injected as environment variables into the app process -- Spring Boot picks these up automatically since Spring maps environment variables to properties) and **Connection Strings** (a dedicated, slightly more structured store specifically for database/service connection strings, which Azure encrypts at rest and can integrate with Key Vault references).
--> **Deployment Slots** -- App Service lets you run a separate, fully-provisioned "staging" slot alongside "production" with its own URL, deploy a new version to staging, smoke-test it, then **swap** slots -- an atomic, near-zero-downtime cutover (and just as importantly, an instant rollback by swapping back if something's wrong). This is a genuinely distinctive App Service feature without a precise one-line equivalent in EB or Elastic Beanstalk's rolling-deployment model.
--> **Java SE vs Tomcat runtime stacks** -- App Service can run a Spring Boot app either as a standalone runnable JAR under "Java SE" (the embedded Tomcat/Undertow inside Spring Boot handles HTTP, App Service just runs `java -jar`) or as a WAR deployed onto an App Service-managed Tomcat/JBoss instance -- for a typical Spring Boot app built with `spring-boot-maven-plugin`, Java SE is the natural fit since the app is already self-contained.

# Azure Spring Apps -- A Managed Spring-Native Platform

--> **Azure Spring Apps** (formerly "Azure Spring Cloud") is a fully managed platform built SPECIFICALLY for running Spring Boot microservices -- distinct from App Service in that it understands Spring idioms natively: it runs a managed **Config Server** (equivalent to Spring Cloud Config, chapter 05 of the Microservices module) and a managed **Eureka-compatible service registry** for you, without you standing up either service yourself.

```text
+------------------------------------------------------------------+
|                     Azure Spring Apps instance                    |
|                                                                    |
|  +-------------------+   +-------------------+                    |
|  | Managed Config     |   | Managed Service    |                    |
|  | Server (git-backed)|   | Registry (Eureka)  |                    |
|  +---------+----------+   +---------+----------+                    |
|            |                        |                               |
|            v                        v                               |
|  +-------------------+   +-------------------+                    |
|  |  inventory-service  |   |  order-service      |                    |
|  |  (Spring Boot app,  |   |  (Spring Boot app,  |                    |
|  |   your JAR)         |   |   your JAR)         |                    |
|  +-------------------+   +-------------------+                    |
+------------------------------------------------------------------+
```

```text
# Deploy an already-built Spring Boot JAR straight into a managed app
az spring app deploy --name inventory-service \
                      --service inventory-spring-apps \
                      --resource-group inventory-rg \
                      --artifact-path target/inventory-service-1.0.0.jar
```

--> **Why this matters if you're already using Spring Cloud** -- a team that has adopted Spring Cloud Config Server and Eureka (as covered in the Microservices module) can move to Azure Spring Apps and largely DROP the self-hosted config server and registry, letting Azure operate the equivalent managed infrastructure instead, while keeping the same `@EnableDiscoveryClient`-style application code mostly unchanged.
--> **Blue-green deployments and built-in monitoring** -- Azure Spring Apps bundles Application Insights integration (distributed tracing, metrics) and blue-green deployment support out of the box, tailored to Spring's actuator/metrics conventions specifically, rather than being a generic platform feature bolted on afterward.
--> **When it fits** -- teams already committed to the Spring Cloud microservices pattern who want the operational burden of the config server/registry lifted off them, without switching to Kubernetes-native service discovery.

# Azure Kubernetes Service (AKS) -- Brief Mention

--> **AKS** is Azure's managed Kubernetes offering -- Azure operates the control plane; you define node pools and deploy the SAME Kubernetes Deployment/Service/Ingress YAML manifests covered in chapter 06, unmodified, since Kubernetes' API surface is identical across clouds. AKS is the right choice when a team specifically wants Kubernetes-level portability and control (custom operators, complex multi-tenant namespacing, existing Kubernetes tooling investment) rather than App Service or Azure Spring Apps' more opinionated, lower-ceremony platforms.
--> **AKS vs Azure Spring Apps for a Spring microservices fleet** -- Azure Spring Apps is simpler if the workload is exclusively Spring Boot services and you're fine with its managed conventions; AKS is the better fit for a genuinely polyglot environment (Java alongside Go/Python/Node services) or when portability to another Kubernetes-based cloud is a real future requirement.

# Azure Functions with Java

--> **Azure Functions** is Azure's serverless/Functions-as-a-Service offering, directly comparable to AWS Lambda -- code runs only in response to a trigger (HTTP request, a message on a Service Bus queue, a Cosmos DB change feed event, a timer) and billing is per-execution.

## A Minimal Java Azure Function

```java
// InventoryLookupFunction.java
public class InventoryLookupFunction {

    @FunctionName("inventoryLookup")
    public HttpResponseMessage run(
            @HttpTrigger(name = "req", methods = {HttpMethod.GET},
                         route = "inventory/{sku}", authLevel = AuthorizationLevel.FUNCTION)
            HttpRequestMessage<Optional<String>> request,
            @BindingName("sku") String sku,
            final ExecutionContext context) {

        InventoryItem item = new InventoryRepository().findBySku(sku);
        return request.createResponseBuilder(HttpStatus.OK)
                       .header("Content-Type", "application/json")
                       .body(item)
                       .build();
    }
}
```

```xml
<!-- pom.xml -- the Azure Functions Maven plugin scaffolds and deploys -->
<plugin>
  <groupId>com.microsoft.azure</groupId>
  <artifactId>azure-functions-maven-plugin</artifactId>
  <version>1.34.0</version>
</plugin>
```

```text
mvn azure-functions:deploy
```

--> **Hosting plans -- this is where cold starts are decided, not left to chance** -- Azure Functions offers three distinct plans with very different cold-start/cost tradeoffs:

| Plan | Behavior | Cold starts |
|---|---|---|
| **Consumption** | True pay-per-execution, scales to zero when idle | Yes -- same JVM-startup cold start concern as Lambda |
| **Premium** | Keeps a configurable number of "pre-warmed" instances always ready, still scales elastically beyond that | Effectively eliminated for the pre-warmed baseline |
| **Dedicated (App Service Plan)** | Runs on VMs you're already paying for continuously (e.g. alongside other App Service apps) | None -- it's always running, just like App Service |

--> **Java-specific cold start mitigation** -- the same principles from the AWS Lambda discussion apply: minimize dependencies pulled into the function's classpath, avoid a full Spring Boot context if a plain function will do (Azure Functions Java doesn't require Spring at all -- the `@FunctionName`-annotated method IS the entire deployable unit), and use the Premium plan for latency-sensitive, user-facing HTTP triggers.
--> **Durable Functions** -- an extension for orchestrating long-running, multi-step stateful workflows across multiple function calls (a saga-like pattern) -- relevant if a Java Azure Functions workload needs to coordinate several steps with retries and checkpointing, rather than a single stateless invocation.

# Common Gotchas

--> **Deploying a WAR to App Service's Java SE stack (or vice versa)** -- the runtime stack (Java SE vs Tomcat vs JBoss) must match the artifact type; a Spring Boot fat JAR with an embedded server belongs on "Java SE," not on the Tomcat stack (which would try to also run its own Tomcat underneath/around the embedded one).
--> **Choosing the Consumption plan for a latency-sensitive Java HTTP API** -- Java cold starts on Consumption can run into multiple seconds; either budget for Premium plan pre-warmed instances or reconsider whether Functions is the right compute choice for that specific endpoint.
--> **Not using Key Vault references for secrets in App Settings** -- App Settings can hold a literal secret value OR a `@Microsoft.KeyVault(SecretUri=...)` reference that resolves at runtime from Azure Key Vault -- always prefer the latter so secrets aren't sitting in plaintext in the App Service configuration blade.
--> **Assuming Azure Spring Apps' managed Config Server behaves identically to a self-hosted one in every respect** -- it's compatible with the same git-backed config repository pattern, but has Azure-specific setup steps (linking the repo through the Azure CLI/portal) rather than a `spring-cloud-config-server` dependency and your own `application.yml`.
--> **Ignoring Deployment Slots and deploying straight to production** -- App Service's slot-swap mechanism exists specifically to catch bad deployments before they receive live traffic; skipping it forfeits both the safety net and the near-instant rollback capability.

# Best Practices Summary

--> Use App Service as the default for a straightforward Spring Boot JAR/WAR or container deployment when you want a managed platform without adopting Kubernetes.
--> Reach for Azure Spring Apps specifically when the workload already leans on Spring Cloud Config/Eureka-style patterns and you want Azure to operate that infrastructure for you.
--> Choose AKS when genuine Kubernetes portability or polyglot orchestration is required, reusing the same manifests from chapter 06 unmodified.
--> For Azure Functions in Java, pick the hosting plan deliberately based on latency sensitivity (Consumption for tolerant/background work, Premium or Dedicated for latency-sensitive synchronous APIs) rather than defaulting to Consumption everywhere.
--> Use Deployment Slots for App Service releases and Key Vault references for all secrets, rather than deploying directly to production or storing secrets as plain App Settings values.
--> Keep JVM memory and startup tuning explicit (JVM options in App Service's configuration, minimal dependencies in Functions) rather than relying on defaults tuned for generic workloads.
