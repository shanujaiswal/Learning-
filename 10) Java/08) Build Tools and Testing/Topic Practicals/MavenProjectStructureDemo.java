/*
 * MavenProjectStructureDemo.java
 *
 * Covers Theory chapter:
 *     08) Build Tools and Testing/Theory/01 Maven Fundamentals.md
 *
 * IMPORTANT: This file demonstrates the JAVA CODE side of a Maven project (what would live under
 * src/main/java and src/test/java) alongside COMMENTED pom.xml snippets showing the build config
 * that would drive it. It is a single, self-contained .java file for study purposes -- it will NOT
 * compile/run as-is inside a real Maven project (a real project splits App / AppTest into separate
 * files under src/main/java and src/test/java, and pom.xml is its own top-level file, not Java code).
 * Read this alongside Theory file 01 for the full explanation of each concept referenced here.
 *
 * ---------------------------------------------------------------------------------------------
 * WHAT A REAL MAVEN PROJECT'S pom.xml WOULD LOOK LIKE FOR THIS CODE (see Theory 01 for full detail):
 *
 * <project ...>
 *     <modelVersion>4.0.0</modelVersion>
 *     <groupId>com.example</groupId>
 *     <artifactId>build-tools-demo</artifactId>
 *     <version>1.0.0-SNAPSHOT</version>
 *     <packaging>jar</packaging>
 *
 *     <properties>
 *         <maven.compiler.release>17</maven.compiler.release>
 *         <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
 *     </properties>
 *
 *     <dependencies>
 *         <dependency>
 *             <groupId>org.junit.jupiter</groupId>
 *             <artifactId>junit-jupiter</artifactId>
 *             <version>5.10.2</version>
 *             <scope>test</scope>
 *         </dependency>
 *     </dependencies>
 *
 *     <build>
 *         <plugins>
 *             <plugin>
 *                 <groupId>org.apache.maven.plugins</groupId>
 *                 <artifactId>maven-surefire-plugin</artifactId>
 *                 <version>3.2.5</version>
 *             </plugin>
 *         </plugins>
 *     </build>
 * </project>
 *
 * Build lifecycle in action for this project:
 *     mvn compile        -> compiles InventoryManager below into target/classes
 *     mvn test           -> also runs InventoryManagerTest via Surefire
 *     mvn package         -> also bundles everything into target/build-tools-demo-1.0.0-SNAPSHOT.jar
 *     mvn clean install   -> wipes target/, rebuilds everything, installs the jar into ~/.m2/repository
 * ---------------------------------------------------------------------------------------------
 */

import java.util.HashMap;
import java.util.Map;

// =====================================================================================
// This class represents what would live at: src/main/java/com/example/app/InventoryManager.java
// =====================================================================================
class InventoryManager {

    // In-memory stand-in for what a real app might back with a database -- kept simple
    // so this demo has no external dependency beyond the JDK itself.
    private final Map<String, Integer> stockBySku = new HashMap<>();

    void addStock(String sku, int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("quantity cannot be negative");
        }
        stockBySku.merge(sku, quantity, Integer::sum);
    }

    boolean hasStock(String sku, int quantity) {
        return stockBySku.getOrDefault(sku, 0) >= quantity;
    }

    void reduceStock(String sku, int quantity) {
        if (!hasStock(sku, quantity)) {
            throw new IllegalStateException("insufficient stock for SKU: " + sku);
        }
        stockBySku.merge(sku, -quantity, Integer::sum);
    }

    int getStockLevel(String sku) {
        return stockBySku.getOrDefault(sku, 0);
    }
}

// =====================================================================================
// This class represents what would live at: src/test/java/com/example/app/InventoryManagerTest.java
// Uses JUnit 5 syntax (see Theory file 03 for the full JUnit 5 reference) -- included here to show
// how "mvn test" (the 'test' phase of the Maven build lifecycle) would exercise this class.
// Requires the junit-jupiter dependency shown in the pom.xml snippet above to actually compile/run.
// =====================================================================================
/*
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InventoryManagerTest {

    private InventoryManager inventory;

    @BeforeEach
    void setUp() {
        inventory = new InventoryManager();          // fresh instance before every test
        inventory.addStock("SKU-1", 10);
    }

    @Test
    void addStockIncreasesLevel() {
        inventory.addStock("SKU-1", 5);
        assertEquals(15, inventory.getStockLevel("SKU-1"));
    }

    @Test
    void reduceStockDecreasesLevel() {
        inventory.reduceStock("SKU-1", 4);
        assertEquals(6, inventory.getStockLevel("SKU-1"));
    }

    @Test
    void reduceStockBeyondAvailableThrows() {
        assertThrows(IllegalStateException.class, () -> inventory.reduceStock("SKU-1", 999));
    }

    @Test
    void addingNegativeQuantityThrows() {
        assertThrows(IllegalArgumentException.class, () -> inventory.addStock("SKU-1", -1));
    }
}
*/

// =====================================================================================
// Runnable entry point so this single file can still be compiled/run directly with plain javac/java
// for study purposes, independent of any Maven project -- exercises InventoryManager manually,
// standing in for what the (commented-out) JUnit test class above would verify automatically.
// =====================================================================================
public class MavenProjectStructureDemo {
    public static void main(String[] args) {
        InventoryManager inventory = new InventoryManager();

        inventory.addStock("SKU-1", 10);
        System.out.println("Stock after adding 10: " + inventory.getStockLevel("SKU-1"));

        inventory.reduceStock("SKU-1", 4);
        System.out.println("Stock after reducing 4: " + inventory.getStockLevel("SKU-1"));

        System.out.println("Has stock for 5 more? " + inventory.hasStock("SKU-1", 5));
        System.out.println("Has stock for 100 more? " + inventory.hasStock("SKU-1", 100));

        try {
            inventory.reduceStock("SKU-1", 999);
        } catch (IllegalStateException e) {
            System.out.println("Expected failure caught: " + e.getMessage());
        }

        System.out.println("\nIn a real Maven project:");
        System.out.println("  mvn compile        -> compiles this class");
        System.out.println("  mvn test           -> also runs InventoryManagerTest (see commented block above)");
        System.out.println("  mvn package         -> bundles into target/*.jar");
        System.out.println("  mvn dependency:tree -> shows resolved dependency graph, incl. transitive deps");
    }
}
