/**
 * AzureInventoryLookupFunction.java
 *
 * Illustrates:
 *     A minimal Java Azure Function using the HttpTrigger annotation style,
 *     directly mirroring the "A Minimal Java Azure Function" example from
 *     Theory chapter 02 -- an HTTP-triggered, function-as-a-service
 *     alternative to deploying the full Spring Boot app (file
 *     02_InventoryServiceApplication.java in this same folder) onto App
 *     Service.
 *
 * Covers Theory chapter:
 *     20) Cloud Deployment for Java Applications/Theory/02 Deploying Java
 *     Applications on Azure.md ("Azure Functions with Java")
 *
 * IMPORTANT -- THIS FILE IS ILLUSTRATIVE, NOT STANDALONE COMPILABLE/RUNNABLE
 * AS-IS. It requires:
 *     - the azure-functions-java-library dependency (for FunctionName,
 *       HttpTrigger, HttpRequestMessage, HttpResponseMessage, HttpStatus,
 *       AuthorizationLevel, HttpMethod, BindingName, ExecutionContext)
 *     - a real Maven project scaffolded by the Azure Functions Maven plugin
 *       (com.microsoft.azure:azure-functions-maven-plugin), which also
 *       generates the accompanying host.json/function.json Azure needs
 *     - a real InventoryRepository/InventoryItem (not defined here -- this
 *       file exists purely to show the trigger/binding shape, not a
 *       complete data layer)
 *
 * Deploy (once wired into a real Maven project via the plugin above):
 *     mvn azure-functions:deploy
 *
 * Unlike a Spring Boot app on App Service, this class alone (no Spring
 * context, no embedded server) IS the entire deployable unit -- exactly the
 * "avoid a full Spring Boot context if a plain function will do" cold-start
 * mitigation the Theory chapter calls out, since Azure Functions Java does
 * not require Spring at all.
 */

// import com.microsoft.azure.functions.*;
// import com.microsoft.azure.functions.annotation.*;

import java.util.Optional;

public class AzureInventoryLookupFunction {

    // @FunctionName("inventoryLookup")
    public /* HttpResponseMessage */ Object run(
            // @HttpTrigger(name = "req", methods = {HttpMethod.GET},
            //              route = "inventory/{sku}", authLevel = AuthorizationLevel.FUNCTION)
            /* HttpRequestMessage<Optional<String>> */ Object request,
            // @BindingName("sku")
            String sku,
            /* final ExecutionContext context */ Object context) {

        // InventoryItem item = new InventoryRepository().findBySku(sku);
        //
        // return request.createResponseBuilder(HttpStatus.OK)
        //                .header("Content-Type", "application/json")
        //                .body(item)
        //                .build();

        // Left unimplemented deliberately -- see the disclaimer above. This
        // method signature exists only to show the HttpTrigger/BindingName
        // annotation shape described in Theory chapter 02.
        throw new UnsupportedOperationException(
                "Illustrative only -- requires azure-functions-java-library and a real "
                        + "InventoryRepository to actually run. See Theory chapter 02's "
                        + "'A Minimal Java Azure Function' section for the fully-annotated version.");
    }
}
