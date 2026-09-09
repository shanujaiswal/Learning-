/**
 * InventoryServiceApplication.java
 *
 * A minimal, complete Spring Boot application class -- the actual build TARGET
 * that the companion Dockerfile in this same folder packages into a container
 * image (COPY target/inventory-service-1.0.0.jar app.jar) and that the
 * companion docker-compose.yml runs as the "inventory-service" container,
 * wired to a Postgres container alongside it.
 *
 * Covers Theory chapter:
 *     17) Microservices and Spring Cloud/Theory/06 Containerization and Orchestration Basics for Microservices.md
 *
 * IMPORTANT -- this file is illustrative, NOT a standalone runnable program AS-IS.
 * It requires a real Spring Boot project scaffold to actually compile/run:
 *     - spring-boot-starter-web        (for the REST endpoint below)
 *     - spring-boot-starter-actuator   (so /actuator/health/readiness and
 *       /actuator/health/liveness exist, matching the readinessProbe/livenessProbe
 *       paths referenced in the Theory chapter's Kubernetes YAML)
 *     - a pom.xml/build.gradle producing an executable fat JAR named to match
 *       the Dockerfile's COPY line (inventory-service-1.0.0.jar)
 *
 * Build + containerize (after wiring this into a real Maven/Gradle project's
 * src/main/java/... and running `mvn package` to actually produce the JAR):
 *     mvn package
 *     docker build -t inventory-service:1.0.0 .
 *     docker run -p 8080:8080 inventory-service:1.0.0
 * or, for the whole local stack (this service + its database):
 *     docker-compose up --build
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
 * Dockerfile's ENTRYPOINT) actually launches inside the container.
 */
@SpringBootApplication
public class InventoryServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }
}

/**
 * A deliberately tiny REST endpoint -- just enough for the container to have
 * something observable to curl once it's running (docker-compose up, then
 * curl http://localhost:8080/api/inventory/SKU-1001), matching the "how this
 * all fits together" flow described at the end of the Theory chapter.
 *
 * Actuator's own health endpoints (/actuator/health/readiness,
 * /actuator/health/liveness -- enabled via spring-boot-starter-actuator plus
 * Kubernetes-probe-aware health groups, not shown as code since they require
 * no custom code beyond the dependency + config) are what a real
 * readinessProbe/livenessProbe in a Kubernetes Deployment would target;
 * this controller is separate, illustrative business-logic-shaped content.
 */
@RestController
class InventoryController {

    // Fake in-memory "database" -- purely so this file is self-contained;
    // docker-compose.yml in this same folder wires a REAL Postgres container
    // alongside this service, which a real implementation would use instead
    // via Spring Data JPA (SPRING_DATASOURCE_URL etc., set as container env vars).
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
