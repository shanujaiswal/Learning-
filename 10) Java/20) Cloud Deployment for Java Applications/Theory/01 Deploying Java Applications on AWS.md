# Why AWS, and Which Service to Reach For

--> Amazon Web Services offers several genuinely different ways to run a Java application, ranging from "upload a JAR and AWS figures out the rest" (Elastic Beanstalk) to "you own every VM detail" (raw EC2) to "no server at all, pay per invocation" (Lambda). Chapter 06 of the Microservices module covered Docker and Kubernetes as PLATFORM-AGNOSTIC packaging and orchestration -- this chapter is about what happens once you point that container (or that plain JAR) at a specific cloud provider's managed services. The right choice depends on how much operational control you want versus how much you're willing to hand to AWS, and how your traffic pattern behaves (steady vs. spiky vs. near-zero).

```text
                     Less operational control, more AWS-managed
   EC2 (raw VMs)  -->  Elastic Beanstalk  -->  ECS/Fargate  -->  Lambda
   (you patch OS,      (AWS manages the      (you manage        (AWS manages
    manage scaling,     platform, you        containers, not     everything;
    everything)         manage the app)      servers)            you manage
                                                                   functions)
```

# AWS Elastic Beanstalk

--> **Elastic Beanstalk (EB)** is a Platform-as-a-Service (PaaS) layer on top of raw AWS resources (EC2, an Elastic Load Balancer, Auto Scaling groups, CloudWatch monitoring) -- you upload application code (a JAR/WAR, or a Docker image), and EB provisions and wires together everything needed to run and scale it, while still giving you access to the underlying EC2 instances if you need to customize them.

## Deploying a Spring Boot JAR to Elastic Beanstalk

--> EB has a native **Java platform** (called the "Corretto" platform, using Amazon's own OpenJDK distribution) that expects either a runnable JAR or a WAR dropped onto Tomcat.

```text
# Package the app the normal way
mvn clean package

# Using the EB CLI (eb) -- initializes and deploys in a few commands
eb init inventory-service --platform "Corretto 21" --region us-east-1
eb create inventory-service-env --instance-type t3.small
eb deploy
```

--> **`.ebextensions/`** -- a directory of YAML config files checked into the project root that let you customize the underlying environment beyond what EB's defaults provide -- setting environment variables, installing OS packages, configuring the reverse proxy (Nginx) in front of your JAR, adjusting JVM options.

```yaml
# .ebextensions/environment.config
option_settings:
  aws:elasticbeanstalk:application:environment:
    SPRING_PROFILES_ACTIVE: production
    SPRING_DATASOURCE_URL: jdbc:postgresql://inventory-db.abc123.us-east-1.rds.amazonaws.com:5432/inventory
  aws:elasticbeanstalk:container:java:
    Xmx: 512m
```

--> **What EB manages for you** -- load balancer provisioning, EC2 Auto Scaling (based on CPU or request count thresholds you configure), rolling deployments, health monitoring dashboards, and log aggregation -- all without writing any Terraform/CloudFormation yourself, at the cost of less fine-grained control than doing it manually.
--> **When EB fits** -- teams that want "just deploy my JAR" simplicity without adopting containers, and who are comfortable with EB's opinionated defaults; it has fallen somewhat out of fashion relative to ECS/Fargate for NEW projects, but remains common in existing AWS shops and is genuinely the fastest path from zero to a running, load-balanced, auto-scaled Spring Boot app.

# ECS and Fargate -- Running Containerized Java Apps Without Managing Servers

--> **Elastic Container Service (ECS)** is AWS's own container orchestration service (an alternative to Kubernetes/EKS, described below) -- it runs Docker containers as **Tasks**, grouped into **Services** that maintain a desired number of running Task copies, much like a Kubernetes Deployment does for Pods.
--> **Fargate** is a "serverless" LAUNCH TYPE for ECS -- instead of provisioning and patching EC2 instances yourself to host your containers (the "EC2 launch type"), Fargate lets AWS run each Task on infrastructure it manages entirely; you specify CPU/memory per Task and AWS handles the rest. This is the most common way to run containerized Java microservices on AWS today.

```text
                         ECS Cluster (Fargate launch type)
   +--------------------------------------------------------------+
   |   ECS Service: inventory-service (desired count: 3)          |
   |     +-----------+   +-----------+   +-----------+            |
   |     |  Task 1    |   |  Task 2    |   |  Task 3    |            |
   |     | container: |   | container: |   | container: |            |
   |     | inventory- |   | inventory- |   | inventory- |            |
   |     |  service   |   |  service   |   |  service   |            |
   |     +-----------+   +-----------+   +-----------+            |
   |            ^               ^               ^                  |
   |            +---------------+---------------+                  |
   |                            |                                   |
   |             Application Load Balancer (ALB)                    |
   |         (routes external traffic, health-checks Tasks)         |
   +--------------------------------------------------------------+
```

## A Minimal ECS Task Definition

```json
{
  "family": "inventory-service",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "512",
  "memory": "1024",
  "containerDefinitions": [
    {
      "name": "inventory-service",
      "image": "123456789.dkr.ecr.us-east-1.amazonaws.com/inventory-service:1.0.0",
      "portMappings": [{ "containerPort": 8080, "protocol": "tcp" }],
      "environment": [
        { "name": "SPRING_PROFILES_ACTIVE", "value": "production" }
      ],
      "secrets": [
        {
          "name": "SPRING_DATASOURCE_PASSWORD",
          "valueFrom": "arn:aws:secretsmanager:us-east-1:123456789:secret:inventory-db-password"
        }
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/inventory-service",
          "awslogs-region": "us-east-1",
          "awslogs-stream-prefix": "ecs"
        }
      },
      "healthCheck": {
        "command": ["CMD-SHELL", "curl -f http://localhost:8080/actuator/health || exit 1"],
        "interval": 30,
        "timeout": 5,
        "retries": 3
      }
    }
  ]
}
```

--> **`awsvpc` network mode** -- gives each Fargate Task its OWN elastic network interface with a private IP, rather than sharing the host's networking stack -- this is required for Fargate and means Tasks get real Security Group-level network isolation, similar in spirit to how each Kubernetes Pod gets its own IP.
--> **`secrets` vs `environment`** -- sensitive values (DB passwords, API keys) should be pulled from **AWS Secrets Manager** or **SSM Parameter Store** at container start via the `secrets` block, never hardcoded in `environment` or baked into the image -- the same externalize-configuration principle from chapter 06, just AWS-flavored.
--> **ECR (Elastic Container Registry)** -- AWS's private Docker registry; the typical CI/CD flow builds the image, tags it, `docker push`es to ECR, then updates the ECS Service to a new Task Definition revision referencing the new image tag, triggering a rolling deployment.
--> **ECS vs EKS** -- ECS is AWS's proprietary orchestrator (simpler, AWS-only concepts); **EKS (Elastic Kubernetes Service)** is AWS's managed Kubernetes offering, running the SAME Kubernetes API and YAML manifests covered in chapter 06, just with AWS managing the control plane. Choose EKS when you need Kubernetes portability across clouds or already have Kubernetes expertise/tooling; choose ECS/Fargate for a simpler, more AWS-native experience with less YAML.

# AWS Lambda with Java

--> **Lambda** runs your code only in response to an event (an HTTP request via API Gateway, an SQS message, an S3 upload, a scheduled trigger) and bills per invocation and execution time down to the millisecond -- there is no server, container, or JVM running (and costing money) when nothing is happening. This is a fundamentally different model from EB/ECS, which keep a JVM warm and running continuously.

## A Minimal Java Lambda Handler

```java
// InventoryLookupHandler.java
public class InventoryLookupHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final InventoryRepository repository = new InventoryRepository(); // see cold start notes below

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        String sku = input.getPathParameters().get("sku");
        InventoryItem item = repository.findBySku(sku);

        return new APIGatewayProxyResponseEvent()
                .withStatusCode(200)
                .withHeaders(Map.of("Content-Type", "application/json"))
                .withBody(toJson(item));
    }
}
```

```xml
<!-- pom.xml -- shading everything into one deployable JAR -->
<plugin>
  <groupId>org.apache.maven.shade.plugin</groupId>
  <artifactId>maven-shade-plugin</artifactId>
  <version>3.5.1</version>
  <executions>
    <execution>
      <phase>package</phase>
      <goals><goal>shade</goal></goals>
    </execution>
  </executions>
</plugin>
```

## Cold Starts -- The Central Java-on-Lambda Concern

--> A **cold start** is the extra latency incurred the FIRST time a Lambda function runs (or after it's been idle long enough for AWS to recycle the environment) -- AWS has to download the deployment package, start a new execution environment, and initialize your code (for Java: start a JVM, load classes, run static initializers, run Spring's dependency injection container if used) before the actual handler logic even begins. **Java is notably worse at this than Python/Node/Go** because JVM startup and class loading are inherently heavier than starting an interpreter.

| Mitigation | How it helps |
|---|---|
| **Provisioned Concurrency** | AWS keeps N execution environments pre-initialized and warm at all times, at an ongoing cost -- eliminates cold starts entirely for the provisioned capacity, at the price of paying for idle warm instances (partially defeating Lambda's pay-per-use appeal). |
| **Smaller deployment packages** | Fewer classes to load, less to unzip -- avoid pulling in a full Spring Boot application if a lighter framework will do. |
| **Avoid full Spring Boot; use Micronaut/Quarkus** | Both frameworks do dependency injection and configuration wiring at COMPILE time (via annotation processing) instead of Spring's runtime classpath scanning and reflection-based DI -- this alone can cut Java cold starts from 3-5 seconds down to a few hundred milliseconds. |
| **AWS Lambda SnapStart** | For Java specifically, SnapStart initializes your function once, then takes a snapshot of the initialized execution environment's memory/disk state (via Firecracker microVM snapshotting) and restores FROM that snapshot on subsequent cold starts -- skipping JVM startup and static initialization entirely. Free to enable, and often the single biggest win for a genuinely Spring-based Lambda. |
| **`spring-cloud-function-adapter-aws`** | If you must use Spring, this adapter lets you write a plain Java function bean instead of pulling in the full Spring Boot web stack, trimming a meaningful amount of what has to initialize. |
| **Keep functions warm with scheduled pings** | An older, cruder workaround (a CloudWatch Events rule invoking the function every few minutes) -- generally superseded by Provisioned Concurrency and SnapStart now. |

--> **When cold starts don't matter** -- background/async processing (SQS consumers, S3-triggered image resizing, scheduled batch jobs) where a few extra seconds of latency on an occasional cold invocation is invisible to any human; this is where Java on Lambda is genuinely comfortable without any special tuning.
--> **When cold starts matter a lot** -- synchronous, user-facing HTTP APIs via API Gateway where a user is waiting on the response; this is where SnapStart, Provisioned Concurrency, or simply choosing ECS/Fargate instead becomes a real architectural decision.

# RDS -- Managed Relational Databases for Java Apps

--> **RDS (Relational Database Service)** provisions, patches, backs up, and can multi-AZ-replicate a managed database instance (PostgreSQL, MySQL, MariaDB, Oracle, SQL Server, or AWS's own Aurora) -- you get a JDBC connection endpoint and never SSH into a database server yourself.

```properties
# application-production.properties
spring.datasource.url=jdbc:postgresql://inventory-db.abc123xyz.us-east-1.rds.amazonaws.com:5432/inventory
spring.datasource.username=inventory_app
spring.datasource.password=${DB_PASSWORD}
spring.jpa.hibernate.ddl-auto=validate
```

--> **Connection pooling matters MORE, not less, on RDS** -- RDS instances have a hard maximum connection count based on instance size; a Spring Boot app's default HikariCP pool (10 connections) multiplied across many container replicas or, worse, many Lambda concurrent executions (each potentially opening its own connections) can exhaust RDS's connection limit fast. **RDS Proxy** sits between your app and RDS as a connection pooler/multiplexer specifically to solve this for high-concurrency or Lambda-based access patterns, where a burst of concurrent Lambda invocations could otherwise open hundreds of short-lived connections simultaneously.
--> **IAM database authentication** -- instead of a static password, RDS can authenticate connections using short-lived AWS IAM-generated auth tokens, avoiding a long-lived credential sitting in a Secrets Manager entry or environment variable -- more setup, but a genuinely stronger security posture for production.
--> **Aurora vs standard RDS engines** -- Aurora is AWS's own MySQL/PostgreSQL-compatible engine, re-architected for the cloud with faster failover and storage that scales automatically; for a Java app, the JDBC driver and Spring Data usage are identical either way -- the difference is purely operational/performance, not code-level.

# Comparing the AWS Options

| Factor | Elastic Beanstalk | ECS/Fargate | Lambda |
|---|---|---|---|
| **Unit of deployment** | JAR/WAR or container image | Container image | Function JAR (handler + deps) |
| **Runs continuously?** | Yes | Yes | No -- only on invocation |
| **Cold start concern** | No (JVM stays warm) | No (JVM stays warm) | Yes -- central concern |
| **Scaling** | Auto Scaling group (EC2-based) | ECS Service auto scaling (Task count) | Automatic, per-request, near-instant |
| **Billing model** | Pay for EC2 instances running | Pay for vCPU/memory while Tasks run | Pay per invocation + duration (ms) |
| **Best fit** | Simple monoliths/APIs, teams new to AWS | Containerized microservices, steady-to-bursty traffic | Event-driven, sporadic, or highly bursty workloads |
| **Operational overhead** | Low (AWS-managed platform) | Medium (you own the container, not the host) | Lowest (no infrastructure at all) |

--> **A rough decision rule** -- if the workload needs to respond to HTTP requests with low, predictable latency around the clock, ECS/Fargate is usually the sweet spot for Java today. If it's genuinely event-driven and often idle, Lambda (with SnapStart) can be dramatically cheaper. Elastic Beanstalk remains reasonable when you want the absolute least AWS-specific ceremony and don't need container-level control.

# Common Gotchas

--> **Not setting `-Xmx`/container memory limits explicitly on ECS/Fargate** -- Fargate Tasks have a hard memory ceiling defined in the Task Definition; a JVM that doesn't respect that limit (or an old JVM that misreads cgroup limits) gets OOM-killed by Fargate rather than the JVM gracefully managing its own heap -- always set `-XX:MaxRAMPercentage` or an explicit `-Xmx` below the Task's memory limit.
--> **Assuming Lambda + Spring Boot will be fast without any tuning** -- a naive Spring Boot Lambda deployment can have multi-second cold starts; either enable SnapStart, switch to Micronaut/Quarkus, or accept that this workload is a poor fit for Lambda.
--> **Exhausting RDS connections from many Fargate Tasks or Lambda concurrent executions** -- each replica/invocation opening its own HikariCP pool adds up fast; size pools conservatively and consider RDS Proxy for high-fan-out access patterns.
--> **Hardcoding AWS credentials or secrets in `application.properties`** -- use IAM roles attached to the ECS Task/Lambda function (so the AWS SDK picks up credentials automatically, no static keys anywhere) plus Secrets Manager/SSM Parameter Store for actual secrets like DB passwords.
--> **Forgetting that Elastic Beanstalk's "managed platform" still runs on EC2 instances you're billed for continuously** -- unlike Lambda, EB doesn't scale to zero; an idle EB environment still costs money.

# Best Practices Summary

--> Choose ECS/Fargate as the default for containerized, always-on Java microservices on AWS; reach for Lambda specifically for event-driven or sporadic workloads, and Elastic Beanstalk when you want the least AWS-specific setup for a simple JAR/WAR deployment.
--> For Java on Lambda, enable SnapStart (or move to Micronaut/Quarkus) before assuming Lambda is "too slow for Java" -- the cold start problem is real but has mature mitigations.
--> Pull secrets from Secrets Manager or SSM Parameter Store at runtime rather than baking them into images or environment variable files, and prefer IAM roles over static access keys wherever the AWS SDK is involved.
--> Set explicit JVM memory flags relative to each compute environment's actual memory limit (ECS Task memory, Lambda's configured memory, or EB's EC2 instance size) rather than trusting JVM defaults.
--> Use RDS Proxy when many short-lived compute units (Fargate Tasks scaling out, or Lambda functions under bursty concurrency) would otherwise open too many simultaneous database connections.
--> Push all environment-specific values (DB URLs, feature flags, profile names) through environment variables/Secrets Manager rather than separate hardcoded builds per environment -- the same externalized-configuration principle applies regardless of which AWS compute service is running the app.
