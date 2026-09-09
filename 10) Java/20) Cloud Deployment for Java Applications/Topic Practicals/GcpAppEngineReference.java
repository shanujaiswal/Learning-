/**
 * GcpAppEngineReference.java
 *
 * IMPORTANT -- this file is a COMMENT-ONLY reference, not compilable code.
 * As with 02_InventoryServiceApplication.java, it deliberately does not
 * redeclare a second @SpringBootApplication class in this folder -- see
 * 01_InventoryServiceApplication.java for the full, illustrative Spring Boot
 * application source these practicals share. There is nothing GCP-specific
 * about the application class itself: the SAME Spring Boot JAR (or, for
 * Cloud Run/App Engine Flexible, the SAME Docker image built from it via
 * chapter 06's multi-stage Dockerfile pattern) is what gets deployed to App
 * Engine Standard, Cloud Run, or GKE -- only the deployment mechanism
 * differs, which is the actual point of chapters 01-03.
 *
 * Covers Theory chapter:
 *     20) Cloud Deployment for Java Applications/Theory/03 Deploying Java
 *     Applications on GCP.md
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
 * HOW THIS JAR/IMAGE REACHES GCP (per Theory chapter 03)
 * ---------------------------------------------------------------------------
 *   # App Engine Standard -- deploy source/JAR directly (see app.yaml in
 *   # this same folder):
 *   mvn clean package
 *   gcloud app deploy
 *
 *   # Cloud Run -- containerize first (same multi-stage Dockerfile pattern
 *   # as chapter 06), respecting the $PORT contract described in the Theory
 *   # chapter, then build via Cloud Build (see cloudbuild.yaml in this same
 *   # folder) and deploy:
 *   gcloud builds submit --config cloudbuild.yaml
 *   gcloud run deploy inventory-service --image gcr.io/inventory-project/inventory-service \
 *     --platform managed --region us-central1 --min-instances 0 --max-instances 10
 *
 * 03_InventoryLookupFunction.java in this same folder illustrates the
 * separate, function-shaped alternative (Cloud Functions, HttpFunction
 * interface style) also covered in that Theory chapter.
 */
