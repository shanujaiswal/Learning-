# Two Different Ways to "Wrap" a Request, and Why Both Exist

--> Both a **Servlet `Filter`** and a Spring **`HandlerInterceptor`** let you run code BEFORE and/or AFTER a request is handled -- logging, auth checks, timing, header manipulation -- without touching the controller method itself. They look similar on the surface, but they live at genuinely different layers of the stack, which is exactly why Spring provides both instead of just one.
--> This file builds directly on file 01's request lifecycle -- `HandlerInterceptor` is one of the concrete participants inside the `DispatcherServlet` flow described there (specifically, part of the `HandlerExecutionChain` that `HandlerMapping` returns), so understanding where it sits requires that mental model.

# Filter vs HandlerInterceptor -- Where Each One Lives

| Aspect | Servlet `Filter` | Spring `HandlerInterceptor` |
|---|---|---|
| Defined by | Servlet API (`jakarta.servlet.Filter`) -- container-level, not Spring-specific | Spring MVC (`org.springframework.web.servlet.HandlerInterceptor`) |
| Runs relative to `DispatcherServlet` | WRAPS the entire servlet, including `DispatcherServlet` itself -- runs before request routing even begins | Runs INSIDE `DispatcherServlet`'s own processing, after a handler has already been matched |
| Knows about the matched handler? | No -- has no idea which controller method (if any) will eventually run | Yes -- receives the actual `HandlerMethod` object, can inspect annotations on it, etc. |
| Can it access Spring beans / `ApplicationContext`? | Only indirectly (e.g. by looking it up manually); not natively Spring-aware | Yes -- it's a Spring-managed concept, trivially injects other beans |
| Typical use cases | CORS headers, request/response wrapping (e.g. reading the body twice), authentication that must apply to non-Spring-MVC resources too (static files, other servlets), character encoding setup | Logging tied to WHICH controller/method handled the request, authorization checks that need annotation metadata (e.g. a custom `@RequireRole`), per-handler timing |
| Configuration | Registered with the servlet container (`FilterRegistrationBean`, or `@Component` + `@WebFilter` scanning) | Registered specifically with Spring MVC via `WebMvcConfigurer.addInterceptors` |
| Applies to | EVERY request reaching the servlet container, including requests that never match any `@RequestMapping` | Only requests that flow through `DispatcherServlet` AND matched some handler mapping |

--> **The mental shortcut**: a `Filter` sits at the SERVLET CONTAINER boundary -- outside and unaware of Spring MVC entirely (Tomcat would call it even for a static file with no Spring involvement at all). A `HandlerInterceptor` sits INSIDE `DispatcherServlet`'s own request handling, specifically wrapped around the handler that `HandlerMapping` already resolved -- so it's the right tool whenever your cross-cutting logic needs to know or care WHICH controller method is about to run.

# HandlerInterceptor -- The Three Lifecycle Hooks

```java
public class RequestTimingInterceptor implements HandlerInterceptor {

    private static final String START_TIME_ATTR = "startTime";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis());
        return true;   // true = continue to the handler; false = short-circuit, handler never runs
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response,
                            Object handler, ModelAndView modelAndView) {
        // Runs AFTER the handler method returns successfully, BEFORE the view is rendered --
        // for a @RestController this is essentially "after the method returned, before serialization
        // fully completes." Skipped entirely if the handler threw an exception.
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                 Object handler, Exception ex) {
        // Runs AFTER the full response is complete -- ALWAYS, whether the handler succeeded or threw.
        // This is where cleanup and "did it succeed or fail" logging belongs.
        long startTime = (long) request.getAttribute(START_TIME_ATTR);
        long duration = System.currentTimeMillis() - startTime;
        System.out.printf("%s %s -> %dms (exception: %s)%n",
                request.getMethod(), request.getRequestURI(), duration, ex);
    }
}
```

--> **`preHandle` returning `false` is a deliberate short-circuit** -- the handler method (and every LATER interceptor's `preHandle`) never runs, and it's YOUR responsibility inside `preHandle` to have already written a complete response (e.g. `response.setStatus(401)` + a body) before returning `false`, since nothing downstream will do it for you. This is the classic pattern for an interceptor-based authentication check.
--> **`postHandle` is easy to misuse** -- it does NOT run if the handler threw an exception, and for a `@RestController`, the response body may already be substantially written by the time `postHandle` runs (since `@ResponseBody` serialization can happen as part of return-value handling, which for some converter/streaming configurations occurs before `postHandle`). In modern REST-API-first codebases, `afterCompletion` is used far more often than `postHandle` precisely because it fires unconditionally and is the reliable place for logging/cleanup/metrics.
--> **Registering an interceptor** -- via `WebMvcConfigurer`, with optional path inclusion/exclusion:

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RequestTimingInterceptor())
                .addPathPatterns("/api/**")           // only apply to these paths
                .excludePathPatterns("/api/health");  // but skip this one
    }
}
```

# Filters -- The Servlet-Level Alternative

```java
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {   // Spring's convenience base class
                                                                      // guarantees exactly one execution per request
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String correlationId = Optional.ofNullable(request.getHeader("X-Correlation-Id"))
                .orElse(UUID.randomUUID().toString());
        response.setHeader("X-Correlation-Id", correlationId);
        MDC.put("correlationId", correlationId);   // e.g. thread-local for logging frameworks
        try {
            filterChain.doFilter(request, response);   // MUST call this to continue the chain
        } finally {
            MDC.remove("correlationId");
        }
    }
}
```

--> **`OncePerRequestFilter`** is Spring's own base class (not raw Servlet API) that guards against a filter running more than once for the same request -- a real risk with certain forwarding/include scenarios in raw servlet filter chains. Prefer it over implementing `Filter` directly for anything Spring-aware.
--> A plain `@Component`-annotated `OncePerRequestFilter` is auto-registered by Spring Boot for ALL requests; use `FilterRegistrationBean` explicitly when you need fine control over URL patterns or ordering relative to other filters.
--> **Ordering matters and is a common source of bugs** -- multiple filters run in a defined order (`@Order` or `FilterRegistrationBean.setOrder`), and Spring Security's own filter chain (if present) is itself implemented as ONE filter early in this chain -- meaning custom filters that need an authenticated principal must be ordered AFTER Spring Security's filter, while filters like the correlation-ID example above are typically fine running first.

# Exception Handling -- From a Thrown Exception to a Shaped Response

--> Referring back to file 01's lifecycle: when a controller method (or an interceptor, or an argument resolver like Bean Validation) throws, `DispatcherServlet` does NOT let the exception propagate raw to the servlet container -- it hands it to the chain of registered `HandlerExceptionResolver` beans, each given a chance to convert the exception into a response. `ExceptionHandlerExceptionResolver` is the one that powers `@ExceptionHandler`/`@ControllerAdvice`, and it's registered by default the moment `spring-boot-starter-web` is present.

# @ExceptionHandler -- Local, Then Global

--> **Local scope** -- an `@ExceptionHandler` method declared INSIDE a `@RestController` only handles exceptions thrown by methods in that SAME controller class:

```java
@RestController
@RequestMapping("/api/products")
public class ProductController {

    @GetMapping("/{id}")
    public ProductResponse getOne(@PathVariable Long id) {
        return productService.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    @ExceptionHandler(ProductNotFoundException.class)   // only catches exceptions from THIS class
    public ResponseEntity<ErrorResponse> handleNotFound(ProductNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("PRODUCT_NOT_FOUND", ex.getMessage()));
    }
}
```

--> **Global scope via `@RestControllerAdvice`** -- (already introduced briefly in the REST APIs chapter) is where real projects centralize this, so it's worth going deeper on the mechanics here rather than repeating the basics:

```java
@RestControllerAdvice   // = @ControllerAdvice + @ResponseBody, applies to EVERY @RestController by default
public class GlobalExceptionHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ProductNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("PRODUCT_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_FAILED", detail));
    }

    @ExceptionHandler(Exception.class)   // catch-all -- keep this narrow in what it hides
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        return ResponseEntity.internalServerError()
                .body(new ErrorResponse("INTERNAL_ERROR", "Something went wrong"));
    }
}
```

--> **Scoping a `@ControllerAdvice` to a subset of controllers** -- by default it applies GLOBALLY, but `@ControllerAdvice(basePackages = "com.example.api")`, `@ControllerAdvice(assignableTypes = AdminController.class)`, or annotation-based targeting narrows it -- useful in larger apps with genuinely different API surfaces (e.g. a public API vs an internal admin API) that need different error-shaping conventions.
--> **Resolution order when both local and global handlers could match**: Spring prefers the handler declared IN THE SAME CONTROLLER CLASS as the one that threw, over any `@ControllerAdvice` handler, even a more specific one globally -- and among candidates at the same scope, the most specific exception TYPE wins (a handler for `ProductNotFoundException` beats one for `RuntimeException` beats one for `Exception`), regardless of declaration order in the source file.

# ResponseEntityExceptionHandler -- Extending Spring's Own Built-In Handling

--> Spring MVC itself already handles a long list of internal exceptions with sensible defaults (`MethodArgumentNotValidException`, `HttpMessageNotReadableException` for malformed JSON, `HttpRequestMethodNotSupportedException` for a `405`, `NoHandlerFoundException` for a `404`, etc.) via a built-in resolver. `ResponseEntityExceptionHandler` is an abstract base class you can extend in your OWN `@ControllerAdvice` to override just the specific ones you want to customize, while still getting Spring's handling for everything else you don't override:

```java
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    // Overrides Spring's built-in handling for this ONE exception type
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField, FieldError::getDefaultMessage,
                        (a, b) -> a));
        return ResponseEntity.status(status).body(Map.of("errors", errors));
    }

    // A custom application exception, handled the normal @ExceptionHandler way in the same class
    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<Object> handleNotFound(ProductNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }
}
```

--> **Why extend it instead of writing every handler from scratch** -- `ResponseEntityExceptionHandler` already correctly handles roughly two dozen framework-level exception types with matching HTTP status codes (this mapping represents real, easy-to-get-wrong knowledge -- e.g. knowing `HttpMediaTypeNotSupportedException` should be a `415` not a `400`). Extending it means you inherit all of that correctness and only override the handful whose RESPONSE SHAPE you want to customize for your API's error contract, rather than reimplementing status-code judgment calls for exceptions you may not even remember exist.

# Designing a Consistent Error Response Shape

--> A recurring real-world requirement: every error response across the whole API -- regardless of which exception caused it -- should have the SAME JSON shape, so client code can parse errors uniformly instead of special-casing each endpoint.

```java
public record ErrorResponse(
        String code,          // machine-readable, stable identifier -- e.g. "PRODUCT_NOT_FOUND"
        String message,       // human-readable detail, safe to show/log
        Instant timestamp,    // when the error occurred -- useful for correlating with server logs
        String path           // which endpoint produced it
) {
    public static ErrorResponse of(String code, String message, String path) {
        return new ErrorResponse(code, message, Instant.now(), path);
    }
}
```

--> **Never leak internals into the error body** -- a raw exception's `.getMessage()` or (worse) its stack trace can accidentally include SQL fragments, internal class names, or file paths if propagated unfiltered into a generic `Exception` handler's response. The generic catch-all handler should almost always return a FIXED, generic message ("Something went wrong") to the client while logging the real exception (with stack trace) server-side via a logger -- the client-facing message and the server-side log message are deliberately DIFFERENT levels of detail.

# Common Gotchas

--> **Confusing `Filter` and `HandlerInterceptor` responsibilities** -- reaching for a `Filter` when you need annotation-aware, per-handler logic (it can't see the matched `HandlerMethod`), or reaching for a `HandlerInterceptor` when you need something to run for EVERY request including ones that never match a Spring MVC handler (e.g. protecting static resources) -- interceptors simply never run for unmatched requests.
--> **Forgetting `preHandle` must fully write the response before returning `false`** -- returning `false` without setting a status code and body results in a response that just... stops, often manifesting as an empty `200 OK` to the client, which is worse than an explicit `401`/`403`.
--> **Relying on `postHandle` for logic that must always run** -- it's skipped on exceptions; use `afterCompletion` for anything that needs an unconditional guarantee (metrics, resource cleanup, audit logging of both success and failure).
--> **A catch-all `@ExceptionHandler(Exception.class)` masking bugs as generic 500s** -- if it's the ONLY handler registered, legitimate `400`-class problems (bad input) get reported to clients identically to real server bugs, and monitoring built around status-code classes becomes useless. Always register the specific, expected exception types first.
--> **Multiple `@ControllerAdvice` classes with overlapping exception types** -- if two separate `@ControllerAdvice` beans both declare an `@ExceptionHandler(SomeException.class)`, which one wins is determined by `@Order`/`Ordered` on the advice classes, NOT declaration order or alphabetical class name -- an unordered pair produces behavior that can be surprising and is worth explicitly resolving with `@Order` if it ever occurs.
--> **Filters running before Spring Security has authenticated the request** -- a custom filter that assumes `SecurityContextHolder` already has a principal needs an explicit order placing it AFTER Spring Security's filter chain, otherwise it sees an anonymous/empty security context even for legitimately authenticated requests.

# Best Practices Summary

--> Use a `Filter` for cross-cutting concerns that must apply to the raw request/response regardless of Spring MVC routing (CORS, correlation IDs, encoding); use `HandlerInterceptor` when the logic needs to know about the matched handler or its annotations.
--> Prefer `afterCompletion` over `postHandle` for logging/metrics/cleanup that must run whether or not the handler succeeded.
--> Centralize error shaping in one (or a small, deliberately scoped set of) `@RestControllerAdvice` class, ordering handler methods conceptually from most specific exception to least.
--> Extend `ResponseEntityExceptionHandler` rather than reimplementing status-code decisions for framework-level exceptions Spring MVC already understands well.
--> Design one consistent `ErrorResponse` shape for the whole API, and never let raw exception messages or stack traces leak to the client through the generic fallback handler -- log detail server-side, return a generic message client-side.
--> Be deliberate about filter and controller-advice ORDERING (`@Order`) the moment more than one of either exists -- don't assume declaration order or rely on default ordering being "obviously correct."
