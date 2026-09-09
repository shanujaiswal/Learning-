/**
 * 08_dispatcher_servlet_lifecycle_demo.java
 *
 * Demonstrates, with illustrative Spring MVC code and heavy inline commentary:
 *     1. The Front Controller pattern -- ALL requests funnel through DispatcherServlet,
 *        never directly to a controller class.
 *     2. A concrete @RestController whose annotations are exactly what
 *        RequestMappingHandlerMapping reads at startup to build its routing table.
 *     3. A step-by-step, comment-driven walkthrough of what DispatcherServlet does
 *        internally for ONE concrete example request:
 *            GET /api/products/42
 *     4. Why ViewResolver is essentially dormant for a @RestController (contrasted
 *        with a traditional @Controller method that DOES return a view name, included
 *        purely for contrast -- not used by the REST flow below).
 *     5. HttpMessageConverter's role in turning the returned ProductResponse into JSON.
 *
 * Covers Theory chapter:
 *     10) Java/09) Spring and Spring Boot/Theory/08 Spring MVC Request Lifecycle Internals.md
 *
 * IMPORTANT -- this file is illustrative, NOT a standalone runnable program.
 * It requires a real Spring Boot project on the classpath to compile/run, specifically:
 *     - spring-boot-starter-web   (Spring MVC, embedded Tomcat, DispatcherServlet auto-registration,
 *                                   default HandlerMapping/HandlerAdapter/HttpMessageConverter beans)
 *
 * Run (in a real Spring Boot project, after wiring this into src/main/java/... and adding
 * a @SpringBootApplication class with a main() method):
 *     mvn spring-boot:run
 *   or
 *     ./gradlew bootRun
 *
 * Example request once the app is running on the default port (8080):
 *     curl -X GET http://localhost:8080/api/products/42
 */

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// ---------------------------------------------------------------------------
// 0) THE FRONT CONTROLLER, CONCEPTUALLY -- there is no Java code to write for
//    this part; it's entirely handled by Spring Boot auto-configuration. This
//    comment block stands in for the object that WOULD be configured by hand
//    in a raw (non-Boot) Spring MVC app:
//
//        // Roughly what DispatcherServletAutoConfiguration does for you:
//        DispatcherServlet dispatcherServlet = new DispatcherServlet(applicationContext);
//        ServletRegistrationBean<DispatcherServlet> registration =
//                new ServletRegistrationBean<>(dispatcherServlet, "/");   // mapped to EVERYTHING
//
//    Because spring-boot-starter-web is on the classpath, this registration
//    already exists the moment the application starts -- NONE of the classes
//    below are ever registered with the servlet container directly. They are
//    plain Spring beans that DispatcherServlet discovers via component scanning
//    and delegates to internally.
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// 1) Domain-ish response DTO -- what HttpMessageConverter (Jackson) will
//    serialize to JSON once the controller method returns.
// ---------------------------------------------------------------------------

class ProductResponse {
    private final Long id;
    private final String name;
    private final BigDecimal price;

    public ProductResponse(Long id, String name, BigDecimal price) {
        this.id = id;
        this.name = name;
        this.price = price;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public BigDecimal getPrice() { return price; }
}

class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(Long id) {
        super("Product not found with id: " + id);
    }
}

// ---------------------------------------------------------------------------
// 2) A minimal service standing in for a real repository-backed layer --
//    included only so the controller below has something concrete to call.
//    Not the focus of this file (see file 9's practicals for a fuller example).
// ---------------------------------------------------------------------------

@Service
class ProductLookupService {

    // Fake "database" pre-seeded with one row so GET /api/products/42 succeeds.
    private final Map<Long, ProductResponse> store = new ConcurrentHashMap<>();

    public ProductLookupService() {
        store.put(42L, new ProductResponse(42L, "Mechanical Keyboard", new BigDecimal("79.99")));
    }

    public ProductResponse findById(Long id) {
        ProductResponse found = store.get(id);
        if (found == null) {
            throw new ProductNotFoundException(id);
        }
        return found;
    }
}

// ---------------------------------------------------------------------------
// 3) The @RestController -- every annotation here is literally what
//    RequestMappingHandlerMapping scans at STARTUP (once, via reflection) to
//    build its URL -> Method routing table. Nothing below is evaluated per
//    request; the table is built once and consulted on every incoming request.
// ---------------------------------------------------------------------------

@RestController                       // @Controller + @ResponseBody on every method:
                                       // -> every return value skips ViewResolver entirely
                                       // and goes straight to an HttpMessageConverter instead.
@RequestMapping("/api/products")      // combined with @GetMapping below to form the full
                                       // pattern "/api/products/{id}" in the routing table.
class ProductLifecycleController {

    private final ProductLookupService productLookupService;

    // Constructor injection -- DispatcherServlet never constructs this class itself;
    // the ApplicationContext does, once, at startup, and DispatcherServlet's
    // HandlerAdapter simply calls methods on the already-existing bean per request.
    public ProductLifecycleController(ProductLookupService productLookupService) {
        this.productLookupService = productLookupService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getOne(@PathVariable Long id) {
        ProductResponse response = productLookupService.findById(id);
        return ResponseEntity.ok(response);
    }
}

// ---------------------------------------------------------------------------
// 4) CONTRAST ONLY -- a traditional @Controller (NOT @RestController) method
//    that DOES return a logical view name. This is the one case where
//    ViewResolver actually does something. Not part of the REST flow this
//    file is walking through -- included purely so the "ViewResolver barely
//    matters for REST APIs" point from the theory file has a concrete contrast.
//
//    @Controller                              // NOTE: no @ResponseBody here
//    class ProductPageController {
//        @GetMapping("/products/{id}/page")
//        public String showProductPage(@PathVariable Long id, Model model) {
//            model.addAttribute("productId", id);
//            return "product-detail";          // a STRING -> handed to ViewResolver,
//        }                                     // which resolves it to, e.g., a Thymeleaf
//    }                                         // template at templates/product-detail.html.
//
//    A @RestController method NEVER goes through this path -- its return
//    value is handed directly to an HttpMessageConverter instead (see step 5
//    of the walkthrough below).
// ---------------------------------------------------------------------------

/*
 * ===========================================================================
 * 5) THE FULL REQUEST LIFECYCLE, WALKED THROUGH FOR ONE CONCRETE REQUEST:
 *
 *         GET /api/products/42
 *
 * This is the same seven-step flow from the theory file, but grounded in the
 * exact classes declared above so each step has something real to point at.
 * ===========================================================================
 *
 * STEP 1 -- HTTP request arrives at the servlet container (embedded Tomcat).
 *     The raw HTTP request "GET /api/products/42 HTTP/1.1" hits Tomcat's
 *     connector. Tomcat has exactly ONE servlet mapped to "/" in this
 *     application: DispatcherServlet. Every request, regardless of path,
 *     is handed to it.
 *
 * STEP 2 -- Servlet container routes it to DispatcherServlet.
 *     DispatcherServlet.doGet(...) (internally doService/doDispatch) begins
 *     processing. It does NOT know yet which Java method, if any, should
 *     handle "/api/products/42".
 *
 * STEP 3 -- DispatcherServlet asks each registered HandlerMapping in turn.
 *     RequestMappingHandlerMapping is asked: "given GET /api/products/42, do
 *     you have a handler?" It consults the routing table built at startup
 *     from scanning ProductLifecycleController above:
 *         class-level:  @RequestMapping("/api/products")
 *         method-level: @GetMapping("/{id}")
 *         combined pattern: GET /api/products/{id}
 *     "/api/products/42" matches this pattern, with {id} captured as "42"
 *     (still a String at this point -- type conversion to Long happens later,
 *     in step 5a). The result is a HandlerExecutionChain wrapping the
 *     ProductLifecycleController#getOne(Long) HandlerMethod (plus any
 *     HandlerInterceptors registered for this path -- none in this minimal
 *     example; see file 02's practicals for that piece).
 *
 * STEP 4 -- DispatcherServlet asks each registered HandlerAdapter.
 *     RequestMappingHandlerAdapter is asked "can you invoke this kind of
 *     handler (an annotated controller method)?" -- yes. It is selected to
 *     perform the actual invocation in step 5.
 *
 * STEP 5 -- HandlerAdapter invokes the controller method.
 *     5a. Argument resolution: getOne(Long id) has one parameter, annotated
 *         @PathVariable. A HandlerMethodArgumentResolver for @PathVariable
 *         takes the captured String "42" from step 3 and converts it to a
 *         Long via Spring's ConversionService -- producing the actual
 *         argument id = 42L that gets passed into the method call.
 *     5b. The method is called: getOne(42L) executes body of
 *         ProductLifecycleController#getOne, which delegates to
 *         ProductLookupService#findById(42L). Since 42L IS pre-seeded in the
 *         demo store, this returns a ProductResponse (id=42, name="Mechanical
 *         Keyboard", price=79.99) wrapped in ResponseEntity.ok(...).
 *     5c. Return value handling: getOne returns ResponseEntity<ProductResponse>.
 *         A HandlerMethodReturnValueHandler recognizes ResponseEntity and:
 *           - unwraps the status code (200, from ResponseEntity.ok)
 *           - unwraps any headers (none set explicitly here)
 *           - hands the BODY (the ProductResponse object) to an
 *             HttpMessageConverter for serialization -- specifically
 *             MappingJackson2HttpMessageConverter, selected because the
 *             request's Accept header (or its absence, defaulting to
 *             "* / *") is compatible with "application/json", which is what
 *             Jackson produces. ViewResolver is NEVER consulted -- this is
 *             the exact "ViewResolver is dormant for REST" point from the
 *             theory file made concrete.
 *         Jackson serializes the ProductResponse to:
 *             {"id":42,"name":"Mechanical Keyboard","price":79.99}
 *
 * STEP 6 -- [Only relevant if something threw] Not applicable to this
 *     particular request since id=42 IS in the store, so
 *     ProductLookupService#findById returns normally rather than throwing
 *     ProductNotFoundException. Had the request instead been
 *     GET /api/products/999 (a non-existent id), findById would throw
 *     ProductNotFoundException, and DispatcherServlet would consult its
 *     HandlerExceptionResolver chain instead of proceeding to step 7 as
 *     described here -- ExceptionHandlerExceptionResolver would look for a
 *     matching @ExceptionHandler (none declared on this minimal controller,
 *     none in a @ControllerAdvice in this file), and finding none, Spring
 *     Boot's default fallback would produce a generic 500. File 02's
 *     practicals show the @ExceptionHandler/@ControllerAdvice machinery that
 *     would properly turn this into a clean 404 instead.
 *
 * STEP 7 -- DispatcherServlet finalizes the response.
 *     Status code 200, header "Content-Type: application/json" (set by the
 *     message converter), and the JSON body from step 5c are all now fully
 *     written to the HttpServletResponse. DispatcherServlet returns control
 *     to Tomcat, which flushes the bytes back over the socket to the client
 *     that issued "curl -X GET http://localhost:8080/api/products/42".
 *
 * The one-sentence version: HandlerMapping found getOne(Long), HandlerAdapter
 * resolved "42" into a Long and called it, and because nothing threw,
 * HandlerExceptionResolver was never needed -- the return value went straight
 * from ResponseEntity unwrapping to Jackson to bytes on the wire.
 * ===========================================================================
 */
