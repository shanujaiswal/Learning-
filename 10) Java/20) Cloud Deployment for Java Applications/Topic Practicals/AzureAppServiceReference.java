/**
 * AzureAppServiceReference.java
 *
 * IMPORTANT -- this file is a COMMENT-ONLY reference, not compilable code.
 * It intentionally does not redeclare a second @SpringBootApplication class
 * in this folder (see 01_InventoryServiceApplication.java, which already
 * provides the full, illustrative Spring Boot application source for this
 * module's practicals) -- Java forbids two files in the same directory
 * declaring conflicting public top-level types with the same simple name
 * once actually compiled together, and there is nothing Azure-specific about
 * the application class itself: the SAME Spring Boot JAR built by `mvn
 * clean package` is what gets deployed to Elastic Beanstalk (file 01), App
 * Service (this file), or App Engine (file 03) -- only the deployment
 * mechanism differs, which is the actual point of these three chapters.
 *
 * Covers Theory chapter:
 *     20) Cloud Deployment for Java Applications/Theory/02 Deploying Java
 *     Applications on Azure.md
 *
 * ---------------------------------------------------------------------------
 * WHAT WOULD BE DEPLOYED (for reference -- see 01_InventoryServiceApplication.java
 * for the actual, complete source)
 * ---------------------------------------------------------------------------
 *
 *     @SpringBootApplication
 *     public class InventoryServiceApplication {
 *         public static void main(String[] args) {
 *             SpringApplication.run(InventoryServiceApplication.class, args);
 *         }
 *     }
 *
 *     @RestController
 *     class InventoryController {
 *         @GetMapping("/api/inventory/{productId}")
 *         public Map<String, Object> getStock(@PathVariable String productId) { ... }
 *     }
 *
 * ---------------------------------------------------------------------------
 * HOW THIS JAR REACHES AZURE APP SERVICE (per Theory chapter 02)
 * ---------------------------------------------------------------------------
 *   mvn clean package
 *   az webapp up --name inventory-service --resource-group inventory-rg \
 *                --runtime "JAVA:21-java21" --sku B1
 *
 * or, via the azure-webapp-maven-plugin declared in pom.xml:
 *   mvn azure-webapp:deploy
 *
 * The companion azure-pipelines.yml in this same folder automates exactly
 * this build-then-deploy flow as a real Azure DevOps CI/CD pipeline, and
 * 02_InventoryLookupFunction.java in this same folder illustrates the
 * separate, function-shaped alternative (Azure Functions) also covered in
 * that Theory chapter.
 */
