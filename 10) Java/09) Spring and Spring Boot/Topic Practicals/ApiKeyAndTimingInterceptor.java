/**
 * ApiKeyAndTimingInterceptor.java
 *
 * Demonstrates, with illustrative Spring MVC code:
 *     1. A HandlerInterceptor implementing all three lifecycle hooks
 *        (preHandle / postHandle / afterCompletion), including a
 *        preHandle-based short-circuit (authentication-style check).
 *     2. Registering that interceptor via WebMvcConfigurer, scoped to a
 *        specific set of paths.
 *     3. A @RestControllerAdvice global exception handler with local vs
 *        global @ExceptionHandler resolution illustrated side by side.
 *     4. Extending ResponseEntityExceptionHandler to override just ONE
 *        framework-level exception's response shape while inheriting
 *        Spring's own handling for everything else.
 *     5. A consistent custom ErrorResponse DTO shape used across every
 *        handler, deliberately never leaking raw exception internals to
 *        the client on the generic catch-all path.
 *
 * Covers Theory chapter:
 *     10) Java/09) Spring and Spring Boot/Theory/09 Interceptors and Exception Handling in Spring MVC.md
 *
 * IMPORTANT -- this file is illustrative, NOT a standalone runnable program.
 * It requires a real Spring Boot project on the classpath to compile/run, specifically:
 *     - spring-boot-starter-web   (Spring MVC, HandlerInterceptor, WebMvcConfigurer,
 *                                   @ControllerAdvice / @ExceptionHandler machinery)
 *
 * Run (in a real Spring Boot project, after wiring this into src/main/java/... and adding
 * a @SpringBootApplication class with a main() method):
 *     mvn spring-boot:run
 *   or
 *     ./gradlew bootRun
 *
 * Example requests once the app is running on the default port (8080):
 *     curl -X GET http://localhost:8080/api/orders/1
 *     curl -X GET http://localhost:8080/api/orders/999          -> 404, OrderNotFoundException
 *     curl -X GET http://localhost:8080/api/orders/1 -H "X-Api-Key: wrong"   -> 401, interceptor short-circuit
 */

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

// ---------------------------------------------------------------------------
// 1) Custom error response DTOs -- the SAME shape is used by every handler
//    below, local or global, so client code never has to special-case the
//    error payload per endpoint.
// ---------------------------------------------------------------------------

record ErrorResponse(
        String code,          // machine-readable, stable identifier -- e.g. "ORDER_NOT_FOUND"
        String message,       // human-readable detail, safe to show/log
        Instant timestamp,    // when the error occurred -- useful for correlating with server logs
        String path           // which endpoint produced it
) {
    public static ErrorResponse of(String code, String message, String path) {
        return new ErrorResponse(code, message, Instant.now(), path);
    }
}

// A second, more structured DTO used specifically for validation failures,
// where a flat message string isn't as useful as a field -> problem map.
record ValidationErrorResponse(
        String code,
        Map<String, String> fieldErrors,
        Instant timestamp,
        String path
) {
    public static ValidationErrorResponse of(Map<String, String> fieldErrors, String path) {
        return new ValidationErrorResponse("VALIDATION_FAILED", fieldErrors, Instant.now(), path);
    }
}

// ---------------------------------------------------------------------------
// 2) Domain exception types the handlers below translate into ErrorResponse.
// ---------------------------------------------------------------------------

class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(Long id) {
        super("Order not found with id: " + id);
    }
}

// ---------------------------------------------------------------------------
// 3) Minimal domain + service, just enough for the controller below to have
//    something real to call and something real to throw.
// ---------------------------------------------------------------------------

record OrderResponse(Long id, String status) { }

@Service
class OrderLookupService {
    private final Map<Long, OrderResponse> store = new ConcurrentHashMap<>();

    public OrderLookupService() {
        store.put(1L, new OrderResponse(1L, "SHIPPED"));
    }

    public OrderResponse findById(Long id) {
        OrderResponse found = store.get(id);
        if (found == null) {
            throw new OrderNotFoundException(id);
        }
        return found;
    }
}

// ---------------------------------------------------------------------------
// 4) HandlerInterceptor -- all three lifecycle hooks, including a deliberate
//    preHandle short-circuit to illustrate the authentication-style pattern.
// ---------------------------------------------------------------------------

public class ApiKeyAndTimingInterceptor implements HandlerInterceptor {

    private static final String START_TIME_ATTR = "startTime";
    private static final String REQUIRED_API_KEY = "demo-secret-key";   // illustrative only -- never hardcode secrets in real code

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Record start time for later duration logging in afterCompletion.
        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis());

        // Demonstrate that preHandle receives the ACTUAL matched handler --
        // something a Filter could never do, since a Filter runs before any
        // handler has been resolved at all.
        if (handler instanceof HandlerMethod handlerMethod) {
            System.out.printf("About to invoke: %s#%s%n",
                    handlerMethod.getBeanType().getSimpleName(),
                    handlerMethod.getMethod().getName());
        }

        String apiKey = request.getHeader("X-Api-Key");
        if (apiKey != null && !apiKey.equals(REQUIRED_API_KEY)) {
            // Deliberate short-circuit: the handler method (and any LATER
            // interceptor's preHandle) will never run. It is OUR responsibility
            // to fully write a response here -- nothing downstream will do it
            // for us if we just return false with no body written.
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            try {
                response.setContentType("application/json");
                response.getWriter().write("{\"code\":\"INVALID_API_KEY\",\"message\":\"Invalid X-Api-Key header\"}");
            } catch (Exception writeFailure) {
                // In real code, log this -- swallowing here only for illustrative brevity.
            }
            return false;   // false = short-circuit, handler never runs
        }

        return true;   // true = continue to the handler
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response,
                            Object handler, ModelAndView modelAndView) {
        // Runs AFTER the handler returns successfully, BEFORE the view is
        // rendered -- but is SKIPPED entirely if the handler threw. For a
        // @RestController, prefer afterCompletion (below) for anything that
        // must run unconditionally; postHandle is shown here only to
        // illustrate where it sits in the lifecycle.
        System.out.println("postHandle: handler completed without throwing");
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                 Object handler, Exception ex) {
        // Runs AFTER the full response is complete -- ALWAYS, whether the
        // handler succeeded or threw. This is the reliable place for
        // unconditional logging/cleanup/metrics.
        Object startTimeAttr = request.getAttribute(START_TIME_ATTR);
        if (startTimeAttr != null) {
            long duration = System.currentTimeMillis() - (long) startTimeAttr;
            System.out.printf("%s %s -> %dms (exception: %s)%n",
                    request.getMethod(), request.getRequestURI(), duration, ex);
        }
    }
}

// ---------------------------------------------------------------------------
// 5) Registering the interceptor -- scoped to /api/** only, via WebMvcConfigurer.
// ---------------------------------------------------------------------------

@Configuration
class WebConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ApiKeyAndTimingInterceptor())
                .addPathPatterns("/api/**")             // only applies to these paths
                .excludePathPatterns("/api/health");    // but skip health checks
    }
}

// ---------------------------------------------------------------------------
// 6) The controller -- demonstrates a LOCAL @ExceptionHandler that only
//    catches exceptions thrown by methods in THIS SAME class. If this class
//    did not declare handleNotFound below, the exception would fall through
//    to the GlobalExceptionHandler (@RestControllerAdvice) declared further
//    down instead -- local always wins over global when both could match.
// ---------------------------------------------------------------------------

@RestController
@RequestMapping("/api/orders")
class OrderController {

    private final OrderLookupService orderLookupService;

    public OrderController(OrderLookupService orderLookupService) {
        this.orderLookupService = orderLookupService;
    }

    @GetMapping("/{id}")
    public OrderResponse getOne(@PathVariable Long id) {
        return orderLookupService.findById(id);
    }

    // LOCAL scope -- only catches OrderNotFoundException thrown by methods in
    // OrderController itself. A structurally identical exception thrown from
    // some OTHER controller would instead fall through to whatever the
    // global @RestControllerAdvice declares for it (if anything).
    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFoundLocally(OrderNotFoundException ex, WebRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("ORDER_NOT_FOUND", ex.getMessage(), "/api/orders"));
    }
}

// ---------------------------------------------------------------------------
// 7) Global exception handler -- centralizes error shaping for every OTHER
//    controller in the app (any that do NOT declare their own local handler
//    for a given exception type). @RestControllerAdvice = @ControllerAdvice
//    + @ResponseBody, applied globally by default here (no basePackages/
//    assignableTypes narrowing -- see the theory file for that option).
// ---------------------------------------------------------------------------

@RestControllerAdvice
class GlobalExceptionHandler {

    // Handles Bean Validation failures uniformly across every controller
    // that doesn't override this locally, converting Spring's own (much
    // noisier) default payload into the flat field -> message map shape.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, WebRequest request) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        fieldError -> fieldError.getField(),
                        fieldError -> fieldError.getDefaultMessage(),
                        (first, second) -> first));
        return ResponseEntity.badRequest()
                .body(ValidationErrorResponse.of(fieldErrors, request.getDescription(false)));
    }

    // Catch-all fallback -- deliberately generic. Never leak the raw
    // exception message or stack trace to the client here; log the real
    // detail server-side (a real project would use a proper logger) and
    // return a FIXED, generic message instead.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, WebRequest request) {
        System.err.println("Unexpected exception: " + ex);   // stand-in for logger.error(..., ex)
        return ResponseEntity.internalServerError()
                .body(ErrorResponse.of("INTERNAL_ERROR", "Something went wrong", request.getDescription(false)));
    }
}

// ---------------------------------------------------------------------------
// 8) A SEPARATE @RestControllerAdvice extending ResponseEntityExceptionHandler
//    -- shown as its own class (in a real app you would typically only have
//    ONE mechanism per exception type; this is included purely to illustrate
//    the technique in isolation, per the theory file's "extending Spring's
//    own built-in handling" section). Overrides just ONE framework-level
//    exception's response shape while inheriting Spring's correct handling
//    (right HTTP status, etc.) for the ~two dozen others it doesn't override.
// ---------------------------------------------------------------------------

class ApiExceptionHandlerBase extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        fieldError -> fieldError.getField(),
                        fieldError -> fieldError.getDefaultMessage(),
                        (first, second) -> first));
        return ResponseEntity.status(status)
                .body(ValidationErrorResponse.of(errors, request.getDescription(false)));
        // Every OTHER exception type ResponseEntityExceptionHandler already
        // knows about (HttpMessageNotReadableException -> 400,
        // HttpRequestMethodNotSupportedException -> 405,
        // HttpMediaTypeNotSupportedException -> 415, etc.) is still handled
        // correctly by the inherited implementation, with zero code here.
    }
}
