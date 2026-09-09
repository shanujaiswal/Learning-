# The Filter Interface -- Cross-Cutting Concerns Around Requests

--> A **filter** is a component that intercepts a request BEFORE it reaches a servlet (and/or the response AFTER the servlet produces it), used for logic that applies across MANY servlets uniformly -- logging, authentication checks, compression, character encoding setup, CORS headers -- without duplicating that logic inside every individual servlet. This is exactly the same motivation as AOP in Spring (if you've covered that) or middleware in other web frameworks -- a way to apply cross-cutting logic without tangling it into business logic.

```java
public interface Filter {
    default void init(FilterConfig filterConfig) throws ServletException {}
    void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException;
    default void destroy() {}
}
```

--> **`init(FilterConfig)`** -- called once, at startup, mirroring a servlet's `init()`.
--> **`doFilter(request, response, chain)`** -- called for EVERY matching request. This is where the filter's actual logic lives, and it has a critical responsibility: it must explicitly call `chain.doFilter(request, response)` to pass control along to the next filter (or the target servlet, if this is the last filter) -- if it doesn't, the chain stops here and the servlet never runs at all.
--> **`destroy()`** -- called once, at shutdown.

## Writing a Filter

```java
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

@WebFilter("/api/*")   // applies to every URL under /api/
public class LoggingFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        long start = System.currentTimeMillis();
        HttpServletRequest httpReq = (HttpServletRequest) request; // filters work with the generic
                                                                     // ServletRequest/Response types --
                                                                     // cast when you need HTTP specifics
        System.out.println("Incoming: " + httpReq.getMethod() + " " + httpReq.getRequestURI());

        chain.doFilter(request, response);   // MUST be called to continue the chain --
                                               // everything AFTER this line runs on the way BACK OUT,
                                               // once the servlet (and any filters after this one) finish

        long duration = System.currentTimeMillis() - start;
        System.out.println("Completed in " + duration + "ms");
    }
}
```

--> **The "wrap around" execution model** -- this is the single most important mental model for filters: code BEFORE `chain.doFilter(...)` runs on the way IN (before the servlet), and code AFTER it runs on the way OUT (after the servlet has produced its response), which is why a logging filter can measure total request duration in one method.

```text
Request  --> Filter A (before) --> Filter B (before) --> Servlet --> Filter B (after) --> Filter A (after) --> Response
             |____________________________________________________________________________________________|
                            One "wrapped" execution -- filters nest like layers of an onion
```

## Filter Chains -- Multiple Filters, Ordered

--> Multiple filters can apply to the same URL, forming a **chain** -- each filter's `doFilter` calls the next, and the LAST filter in the chain calls the actual servlet.

```java
@WebFilter(urlPatterns = "/*", filterName = "characterEncodingFilter")
public class CharacterEncodingFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        request.setCharacterEncoding("UTF-8");   // must run FIRST, before parameters are read anywhere downstream
        chain.doFilter(request, response);
    }
}

@WebFilter(urlPatterns = "/admin/*", filterName = "authenticationFilter")
public class AuthenticationFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpReq = (HttpServletRequest) request;
        HttpServletResponse httpResp = (HttpServletResponse) response;
        if (httpReq.getSession(false) == null || httpReq.getSession().getAttribute("user") == null) {
            httpResp.sendRedirect("/login");
            return;   // NOT calling chain.doFilter() here -- deliberately halts the chain,
                       // the servlet (and any later filters) never run for an unauthenticated request
        }
        chain.doFilter(request, response);   // authenticated -- let the request continue
    }
}
```

--> **Ordering with annotations vs `web.xml`** -- `@WebFilter` does NOT let you specify a deterministic ordering relative to other filters (the container decides, often based on scan/class-loading order, which is NOT something you should rely on) -- when the ORDER of multiple filters genuinely matters (e.g. character encoding must be set before anything else touches parameters), declare filter ordering explicitly via `web.xml`'s `<filter-mapping>` entries, listed in the order you want them to run, or use a framework (Spring's `FilterRegistrationBean` with explicit `setOrder(...)`) that gives you control.
--> **Deliberately NOT calling `chain.doFilter(...)`** -- this is a legitimate, common pattern (shown above in `AuthenticationFilter`) for short-circuiting the chain, e.g. rejecting an unauthenticated request before it ever reaches the servlet -- just be sure to have ALREADY written a complete response (status code, redirect, error body) before returning, since nothing downstream will run to do it for you.

# Listeners -- Reacting to Application-Level Events

--> A **listener** is a class that reacts to lifecycle events in the servlet container -- application startup/shutdown, session creation/destruction, attribute changes -- without needing to be invoked from a servlet or filter directly; the container calls listener methods automatically when the corresponding event occurs.

## ServletContextListener -- Application Startup/Shutdown

```java
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

@WebListener
public class AppStartupListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        // Called ONCE, when the whole web application starts up --
        // BEFORE any servlet's init() runs. Ideal place for truly
        // application-wide setup: opening a connection pool, loading
        // a cache, starting a background scheduler.
        System.out.println("Application starting -- initializing shared resources");
        javax.sql.DataSource pool = createConnectionPool();
        sce.getServletContext().setAttribute("dataSource", pool);
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        // Called ONCE, when the application is shutting down/undeploying --
        // the natural place to release what contextInitialized acquired.
        System.out.println("Application shutting down -- releasing shared resources");
        javax.sql.DataSource pool = (javax.sql.DataSource) sce.getServletContext().getAttribute("dataSource");
        closeConnectionPool(pool);
    }

    private javax.sql.DataSource createConnectionPool() { /* ... */ return null; }
    private void closeConnectionPool(javax.sql.DataSource pool) { /* ... */ }
}
```

--> **Why use a listener instead of a servlet's `init()` with `load-on-startup`** -- a servlet's `init()` is scoped to THAT ONE SERVLET and only guaranteed to run before ITS first request; a `ServletContextListener` is scoped to the WHOLE APPLICATION and guaranteed to run before ANY servlet handles ANY request. For resources genuinely shared across multiple servlets (a connection pool, an application-wide cache), a listener is the architecturally correct place, not an arbitrary servlet's `init()`.

## HttpSessionListener -- Tracking Session Creation/Destruction

```java
@WebListener
public class SessionTrackingListener implements jakarta.servlet.http.HttpSessionListener {
    private static final java.util.concurrent.atomic.AtomicInteger activeSessions =
            new java.util.concurrent.atomic.AtomicInteger(0);

    @Override
    public void sessionCreated(jakarta.servlet.http.HttpSessionEvent se) {
        int count = activeSessions.incrementAndGet();
        System.out.println("Session created -- active sessions now: " + count);
    }

    @Override
    public void sessionDestroyed(jakarta.servlet.http.HttpSessionEvent se) {
        int count = activeSessions.decrementAndGet();
        System.out.println("Session destroyed (timeout or invalidate()) -- active sessions now: " + count);
    }
}
```

--> Useful for things like a live "currently online users" counter, auditing logins/logouts, or cleaning up per-session resources that a plain session-attribute approach can't hook into automatically.

## Overview of Common Listener Types

| Listener interface | Fires on |
|---|---|
| `ServletContextListener` | Application startup (`contextInitialized`) / shutdown (`contextDestroyed`) |
| `HttpSessionListener` | Session created / destroyed |
| `ServletRequestListener` | Request started / finished (request-scoped setup/teardown, e.g. request-scoped logging correlation IDs) |
| `ServletContextAttributeListener` | An attribute added/removed/replaced on the `ServletContext` |
| `HttpSessionAttributeListener` | An attribute added/removed/replaced on an `HttpSession` |

# Thread-Safety Concerns in Servlets -- Revisited in Depth

--> Theory 01 introduced the core fact: the container creates ONE servlet instance and handles MANY concurrent requests against it via a thread pool. This section is the deeper, consolidated treatment, because it is the single most consequential correctness issue in raw servlet programming.

```java
// UNSAFE -- classic race condition
public class CounterServlet extends HttpServlet {
    private int hitCount = 0;   // INSTANCE field -- one copy, shared by every concurrent thread

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        hitCount++;   // read-modify-write is NOT atomic -- two threads can both read the same
                       // value before either writes back the incremented result, LOSING an update
        resp.getWriter().write("Hits: " + hitCount);
    }
}
```

```text
Thread A: reads hitCount (5)
Thread B: reads hitCount (5)          <-- both threads see the SAME stale value
Thread A: writes hitCount = 6
Thread B: writes hitCount = 6          <-- B's write overwrites A's -- one increment is LOST
Expected: 7.  Actual: 6.
```

--> **Three correct fixes, in order of general preference:**

```java
// Fix 1 (best): avoid shared mutable state entirely -- keep counters/similar
// aggregate state in a database, or in a properly concurrent shared structure
// injected once, rather than a plain servlet instance field.
private final java.util.concurrent.atomic.AtomicInteger hitCount =
        new java.util.concurrent.atomic.AtomicInteger(0);

protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    int current = hitCount.incrementAndGet();   // atomic -- no lost updates, no explicit lock needed
    resp.getWriter().write("Hits: " + current);
}
```

```java
// Fix 2 (works, but serializes access -- hurts concurrency/throughput):
// synchronize the shared mutable section.
private int hitCount = 0;
private final Object lock = new Object();

protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    int current;
    synchronized (lock) {
        current = ++hitCount;
    }
    resp.getWriter().write("Hits: " + current);
}
```

```java
// Fix 3 (best default posture): don't have instance fields carrying per-request
// or mutable aggregate state AT ALL -- keep servlets stateless, and put anything
// per-request in a LOCAL VARIABLE inside doGet/doPost (which is inherently
// thread-safe, since each thread gets its own stack and local variables).
protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    String userId = req.getParameter("userId");   // LOCAL variable -- safe, each thread has its own
    Order order = orderService.findLatestOrder(userId);
    resp.getWriter().write("Latest order: " + order.getId());
}
```

--> **What IS safe to store as an instance field** -- immutable, effectively-read-only objects set up once in `init()` and never mutated afterward: a `DataSource`, a compiled regex `Pattern`, a configuration value loaded from init-params, a reference to a thread-safe service object. The danger is specifically MUTABLE state that changes per-request; a field that's assigned once in `init()` and only ever read afterward is perfectly safe to share.
--> **`HttpSession` and request attributes are NOT subject to this problem** -- each client gets its OWN `HttpSession`, and each request gets its OWN `HttpServletRequest`, so storing per-user or per-request data there is inherently safe from cross-thread interference between DIFFERENT users' requests (though a single session being hit by multiple concurrent tabs/requests from the SAME user is a separate, rarer concern).

# Why Spring MVC Replaced Raw Servlets for Most Applications

--> Having now seen the full raw servlet/JSP picture (Theory 01-05), it's worth being explicit about why virtually no new application today is built with hand-written servlets and JSPs as its primary architecture, and instead reaches for Spring MVC (or a similar framework) -- while still understanding that Spring MVC is BUILT ON TOP OF exactly what you've just learned, not a replacement for it at a lower level.

| Raw Servlets/JSP | Spring MVC |
|---|---|
| One servlet class (or a manually built dispatcher) per resource/URL pattern; you write the routing yourself | A single `DispatcherServlet` (itself an `HttpServlet`) handles ALL routing, mapped via `@GetMapping`/`@PostMapping` on plain `@Controller` classes |
| Manual `req.getParameter(...)` + manual type conversion + manual validation | Automatic data binding: method parameters annotated `@RequestParam`/`@PathVariable`/`@RequestBody` are converted and validated for you |
| Manual `req.setAttribute(...)` + manual `getRequestDispatcher().forward(...)` | Return a view name (or `@ResponseBody`/`ResponseEntity` for JSON) and Spring resolves the view / serializes the response |
| Thread-safety is entirely YOUR responsibility to reason about (this chapter) | Same underlying servlet threading model still applies, but Spring's own components (controllers, services) are typically written to be stateless by convention, and the framework doesn't nudge you toward instance-field mistakes the way raw servlet tutorials often do |
| No built-in dependency injection -- you `new` up services yourself, or hand-roll a factory/registry | Full IoC container (from the Spring/Spring Boot chapters) -- constructor-injected services, testable in isolation |
| JSON APIs require manual serialization (writing to `resp.getWriter()` yourself) | `@RestController` + Jackson (auto-configured) serializes/deserializes JSON for you automatically |
| web.xml or scattered `@WebServlet`/`@WebFilter` annotations across many classes | Centralized configuration (`@Configuration` classes, Spring Boot auto-configuration), consistent across the whole app |
| Testing requires a running container (or heavyweight mocking of `HttpServletRequest`/`HttpServletResponse`) | Spring MVC Test (`MockMvc`) lets you test controllers without starting a real server |

--> **The honest summary** -- raw servlets and JSP are not "wrong" or obsolete as a SPECIFICATION; they are the foundation Spring MVC, JSF, and virtually every other JVM web framework are built on. What changed is that hand-writing routing, parameter binding, view dispatch, and JSON serialization by hand, servlet by servlet, doesn't scale well to real application complexity -- Spring MVC (and Spring Boot's auto-configuration on top of it) automates exactly the repetitive, error-prone parts of servlet programming (routing tables, `web.xml`, manual parameter parsing, manual JSON handling) while still ultimately compiling down to, and running inside, a servlet container via `DispatcherServlet`.
--> **When you'd still touch raw servlets/filters/listeners directly even in a Spring app** -- writing a custom `Filter` for something Spring Security or Spring MVC doesn't already provide (a bespoke header-based auth scheme, a custom logging/correlation-ID filter), or a `ServletContextListener` for very low-level startup work that needs to happen even before Spring's own context initializes. Understanding the raw APIs from this chapter is directly what lets you drop down a level when a framework's abstractions don't cover your specific need.

# Common Gotchas

--> **Forgetting `chain.doFilter(...)`** -- the single most common filter bug: forget to call it (or return early without calling it, unintentionally) and every request matching that filter's URL pattern hangs or gets no response at all, with no obvious error pointing at the missing call.
--> **Assuming `@WebFilter`/`@WebListener` ordering is deterministic** -- it generally is NOT guaranteed across containers/versions; use `web.xml` explicit ordering (or a framework's registration API) whenever relative order actually matters.
--> **Doing expensive work in a listener's `contextInitialized`** without realizing it blocks application startup entirely (the app isn't considered "up" until this returns) -- fine for genuinely necessary setup, but a common source of surprisingly slow deployments if abused.
--> **Treating instance fields as "obviously fine because it's just a counter"** -- this remains the most common real-world servlet bug for developers new to the model; always ask "could two threads touch this concurrently?" before adding a mutable instance field to any servlet or filter.

# Best Practices Summary

--> **Use filters for cross-cutting concerns** (logging, auth checks, encoding setup, compression) that would otherwise be duplicated across many servlets.
--> **Always call `chain.doFilter(...)`** unless you are deliberately and correctly short-circuiting the chain with a complete response already written.
--> **Use `ServletContextListener` for application-wide setup/teardown** (connection pools, caches) rather than overloading an arbitrary servlet's `init()`/`destroy()`.
--> **Never store mutable, per-request state in a servlet/filter instance field** -- keep it local to the method, or use a properly concurrent structure (`AtomicInteger`, `ConcurrentHashMap`) when genuinely shared state is unavoidable.
--> **Recognize Spring MVC as an evolution of, not a replacement for, everything in this chapter** -- it automates routing/binding/serialization on top of the exact same servlet container contract.
--> **Reach for raw `Filter`/`Listener` APIs even in a Spring app** when you need behavior the framework doesn't already provide out of the box.
