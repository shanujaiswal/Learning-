/**
 * InventoryServiceApplication.java
 *
 * A minimal, complete Spring Boot application class representing the exact
 * build TARGET that this folder's companion AWS deployment artifacts
 * (buildspec.yml, Procfile, .ebextensions/environment.config) all assume:
 *   - buildspec.yml   -> AWS CodeBuild compiles this into a JAR and (for the
 *                         ECS/Fargate path) builds+pushes a Docker image of it
 *   - Procfile        -> tells Elastic Beanstalk's Java SE platform how to
 *                         launch the packaged JAR
 *   - .ebextensions/   -> customizes the EB environment this app runs inside
 *     environment.config
 *
 * Covers Theory chapter:
 *     20) Cloud Deployment for Java Applications/Theory/01 Deploying Java
 *     Applications on AWS.md
 *
 * IMPORTANT -- this file is illustrative, NOT a standalone runnable program
 * AS-IS. It requires a real Spring Boot project scaffold to actually compile:
 *     - spring-boot-starter-web        (for the REST endpoint below)
 *     - spring-boot-starter-actuator   (so /actuator/health exists -- the
 *       same endpoint the Theory chapter's ECS Task Definition healthCheck
 *       and CodeBuild smoke-test would target)
 *     - a pom.xml producing an executable fat JAR named to match this
 *       folder's buildspec.yml/Procfile (inventory-service-1.0.0.jar)
 *
 * Build + deploy paths this class feeds (see the Theory chapter for the full
 * comparison of when to reach for which):
 *     mvn clean package                        # -> target/inventory-service-1.0.0.jar
 *     eb deploy                                 # Elastic Beanstalk (see Procfile, .ebextensions/)
 *     docker build/push + register a Task Def   # ECS/Fargate (see comments at bottom)
 *     mvn package + shade + upload              # Lambda (see comments at bottom)
 */

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The application's single entry point -- what "java -jar app.jar" (the
 * command line this folder's Procfile hands to Elastic Beanstalk, and what a
 * Fargate Task's container ENTRYPOINT would also run) actually launches.
 */
@SpringBootApplication
public class InventoryServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }
}

/**
 * A deliberately tiny REST endpoint -- just enough for a deployed instance
 * (on EB, ECS/Fargate, or behind API Gateway via Lambda) to have something
 * observable to curl, matching the "how this fits together" flow described
 * throughout Theory chapter 01.
 *
 * Actuator's /actuator/health (enabled via spring-boot-starter-actuator, no
 * custom code required beyond the dependency) is what the ECS Task
 * Definition's healthCheck command and a CodeBuild/EB deployment's smoke
 * test would target -- deliberately not reimplemented here since it needs no
 * custom code, only the starter dependency.
 */
@RestController
class InventoryController {

    // Fake in-memory "database" -- purely so this file is self-contained.
    // A real deployment would instead use Spring Data JPA against RDS
    // (spring.datasource.url etc., injected as environment variables/secrets
    // exactly as shown in the Theory chapter's .ebextensions and ECS Task
    // Definition "secrets" block -- never hardcoded here).
    private final Map<String, Integer> stock = new ConcurrentHashMap<>(Map.of(
            "SKU-1001", 50,
            "SKU-1002", 0
    ));

    @GetMapping("/api/inventory/{productId}")
    public Map<String, Object> getStock(@PathVariable String productId) {
        int available = stock.getOrDefault(productId, 0);
        return Map.of("productId", productId, "available", available);
    }
}

/*
 * ---------------------------------------------------------------------------
 * EXAMPLE AWS CLI COMMANDS -- ECS/FARGATE TASK DEFINITION (illustrative only)
 * ---------------------------------------------------------------------------
 * These commands are NOT executed by this file; they document the CLI flow
 * described in Theory chapter 01's "ECS and Fargate" section, assuming the
 * JSON Task Definition shown there is saved locally as task-def.json.
 *
 *   # 1. Authenticate Docker to ECR and push the built image
 *   aws ecr get-login-password --region us-east-1 \
 *     | docker login --username AWS --password-stdin 123456789.dkr.ecr.us-east-1.amazonaws.com
 *   docker build -t inventory-service:1.0.0 .
 *   docker tag inventory-service:1.0.0 123456789.dkr.ecr.us-east-1.amazonaws.com/inventory-service:1.0.0
 *   docker push 123456789.dkr.ecr.us-east-1.amazonaws.com/inventory-service:1.0.0
 *
 *   # 2. Register a new Task Definition revision from the JSON in the Theory chapter
 *   aws ecs register-task-definition --cli-input-json file://task-def.json
 *
 *   # 3. Create (first time) or update (subsequent deploys) the ECS Service
 *   #    to run 3 copies of the new Task Definition revision on Fargate
 *   aws ecs create-service \
 *     --cluster inventory-cluster \
 *     --service-name inventory-service \
 *     --task-definition inventory-service \
 *     --desired-count 3 \
 *     --launch-type FARGATE \
 *     --network-configuration "awsvpcConfiguration={subnets=[subnet-abc123],securityGroups=[sg-abc123],assignPublicIp=ENABLED}"
 *
 *   aws ecs update-service --cluster inventory-cluster --service inventory-service \
 *     --task-definition inventory-service --force-new-deployment
 *
 * ---------------------------------------------------------------------------
 * EXAMPLE AWS CLI COMMANDS -- LAMBDA DEPLOYMENT (illustrative only)
 * ---------------------------------------------------------------------------
 * Mirrors Theory chapter 01's "AWS Lambda with Java" section -- packaging a
 * shaded JAR (see the maven-shade-plugin snippet there) as a Lambda function.
 *
 *   # 1. Build the shaded ("fat") JAR containing the handler + all dependencies
 *   mvn clean package
 *
 *   # 2. Create the function the first time (Java 21 runtime, handler class
 *   #    referenced by fully-qualified-name::handleRequest)
 *   aws lambda create-function \
 *     --function-name inventory-lookup \
 *     --runtime java21 \
 *     --handler com.example.inventory.InventoryLookupHandler::handleRequest \
 *     --role arn:aws:iam::123456789:role/lambda-inventory-role \
 *     --zip-file fileb://target/inventory-lambda-1.0.0.jar \
 *     --memory-size 512 \
 *     --timeout 10
 *
 *   # 3. Update the function code on subsequent deploys
 *   aws lambda update-function-code \
 *     --function-name inventory-lookup \
 *     --zip-file fileb://target/inventory-lambda-1.0.0.jar
 *
 *   # 4. Enable SnapStart (the Theory chapter's single biggest Java cold-start
 *   #    mitigation) -- free to enable, applies on the next publish
 *   aws lambda update-function-configuration \
 *     --function-name inventory-lookup \
 *     --snap-start ApplyOn=PublishedVersions
 *   aws lambda publish-version --function-name inventory-lookup
 * ---------------------------------------------------------------------------
 */
