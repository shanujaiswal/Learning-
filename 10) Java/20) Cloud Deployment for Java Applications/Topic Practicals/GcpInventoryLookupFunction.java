/**
 * GcpInventoryLookupFunction.java
 *
 * Illustrates:
 *     A minimal Java Cloud Function using the HttpFunction interface style,
 *     directly mirroring the "A Minimal Java Cloud Function" example from
 *     Theory chapter 03 -- a single-purpose, event-triggered alternative to
 *     deploying the full Spring Boot app (file
 *     03_InventoryServiceApplication.java in this same folder) onto App
 *     Engine or Cloud Run.
 *
 * Covers Theory chapter:
 *     20) Cloud Deployment for Java Applications/Theory/03 Deploying Java
 *     Applications on GCP.md ("Cloud Functions with Java")
 *
 * IMPORTANT -- THIS FILE IS ILLUSTRATIVE, NOT STANDALONE COMPILABLE/RUNNABLE
 * AS-IS. It requires:
 *     - the com.google.cloud.functions:functions-framework-api dependency
 *       (for the HttpFunction, HttpRequest, HttpResponse types)
 *     - a real Maven project using the function-maven-plugin
 *       (com.google.cloud.functions:function-maven-plugin) to run/deploy it
 *     - a real InventoryRepository/InventoryItem/toJson (not defined here --
 *       this file exists purely to show the HttpFunction shape, not a
 *       complete data layer)
 *
 * Deploy (once wired into a real Maven project via the plugin above):
 *     gcloud functions deploy inventoryLookup \
 *       --gen2 --runtime=java21 --trigger-http \
 *       --entry-point=InventoryLookupFunction --memory=512MB
 *
 * Notably (per the Theory chapter): 2nd-generation Cloud Functions runs on
 * Cloud Run infrastructure under the hood -- which is why, once a "function"
 * like this grows more than one route or any real routing logic, the Theory
 * chapter recommends expressing it directly as a small Cloud Run service
 * (see 03_InventoryServiceApplication.java + app.yaml/cloudbuild.yaml in
 * this same folder) instead of contorting it into a single-entry-point
 * function.
 */

// import com.google.cloud.functions.HttpFunction;
// import com.google.cloud.functions.HttpRequest;
// import com.google.cloud.functions.HttpResponse;

public class GcpInventoryLookupFunction /* implements HttpFunction */ {

    // private final InventoryRepository repository = new InventoryRepository();

    // @Override
    public void service(/* HttpRequest request, HttpResponse response */
            Object request, Object response) throws Exception {

        // String sku = request.getFirstQueryParameter("sku").orElseThrow();
        // InventoryItem item = repository.findBySku(sku);
        //
        // response.setContentType("application/json");
        // response.getWriter().write(toJson(item));

        // Left unimplemented deliberately -- see the disclaimer above. This
        // method signature exists only to show the HttpFunction.service(...)
        // shape described in Theory chapter 03.
        throw new UnsupportedOperationException(
                "Illustrative only -- requires functions-framework-api and a real "
                        + "InventoryRepository to actually run. See Theory chapter 03's "
                        + "'A Minimal Java Cloud Function' section for the fully-typed version.");
    }
}
