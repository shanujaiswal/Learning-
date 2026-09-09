/**
 * FiltersListenersBestPracticesDemo.java
 *
 * Demonstrates, with heavy comments explaining WHAT would happen at runtime
 * inside a real servlet container:
 *     1. The Filter interface contract (init/doFilter/destroy) and the
 *        "wrap around" execution model (code before/after chain.doFilter)
 *     2. A filter chain -- character encoding filter + authentication filter,
 *        including deliberately short-circuiting the chain
 *     3. ServletContextListener -- application-wide startup/shutdown hooks
 *        (contextInitialized/contextDestroyed)
 *     4. HttpSessionListener -- reacting to session creation/destruction
 *     5. Thread-safety in servlets/filters -- instance variable pitfalls,
 *        demonstrated via a commented bad-vs-good example plus a live one
 *
 * Covers Theory chapter:
 *     10) Java/12) Servlets and JSP/Theory/05 Filters Listeners and Servlet Best Practices.md
 *
 * IMPORTANT -- this file will NOT compile or run standalone with plain `javac`/`java`.
 * It requires:
 *     1. The Jakarta Servlet API on the classpath (e.g. `jakarta.servlet-api` as a
 *        PROVIDED dependency -- the container supplies the real implementation at
 *        runtime, so you never bundle it yourself in the deployed WAR).
 *     2. A running servlet container (e.g. Apache Tomcat, Jetty) to actually deploy
 *        and invoke these filters/listeners/servlets -- there is no `main()` method
 *        here because they are instantiated and driven entirely by the CONTAINER.
 *
 * How you would actually run this, end to end:
 *   1. Create a standard Maven "war" packaged project:
 *        mvn archetype:generate -DarchetypeArtifactId=maven-archetype-webapp
 *   2. Add to pom.xml (provided scope -- Tomcat supplies the real jar at runtime):
 *        <dependency>
 *            <groupId>jakarta.servlet</groupId>
 *            <artifactId>jakarta.servlet-api</artifactId>
 *            <version>6.0.0</version>
 *            <scope>provided</scope>
 *        </dependency>
 *   3. Place each class below (one public top-level class per file, per normal
 *      Java rules -- this single file bundles several for readability only,
 *      you'd split it when actually deploying) under src/main/java/com/example/.
 *   4. Build a WAR: mvn package
 *   5. Drop the resulting .war into Tomcat's webapps/ directory (or run
 *      `mvn tomcat7:run` / `cargo:run` with a suitable plugin configured).
 *   6. Visit http://localhost:8080/<app-name>/api/anything to see the logging
 *      filter fire, or /admin/anything to see the auth filter redirect an
 *      unauthenticated request -- watch the console for listener/filter output.
 */

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.annotation.WebListener;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;


// ---------------------------------------------------------------------------
// 1) A Filter -- doFilter with chain.doFilter, the "wrap around" execution model.
//    Code BEFORE chain.doFilter(...) runs on the way IN (before the servlet);
//    code AFTER it runs on the way OUT (after the servlet has produced its
//    response) -- which is why this one filter can measure total duration.
// ---------------------------------------------------------------------------

/**
 * LoggingFilter applies to every URL under /api/, logging method+URI on the
 * way in and total duration on the way out.
 *
 *     Request --> Filter A (before) --> Filter B (before) --> Servlet -->
 *                 Filter B (after) --> Filter A (after) --> Response
 *                 (filters nest like layers of an onion -- one "wrapped" execution)
 */
@WebFilter("/api/*")
public class FiltersListenersBestPracticesDemo implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // Called once, at startup, mirroring a servlet's init().
        System.out.println("[LoggingFilter] init() -- filter starting up");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        long start = System.currentTimeMillis();

        // Filters work with the generic ServletRequest/ServletResponse types -- cast when
        // HTTP-specific methods (getMethod, getRequestURI, etc.) are actually needed.
        HttpServletRequest httpReq = (HttpServletRequest) request;
        System.out.println("[LoggingFilter] Incoming: " + httpReq.getMethod() + " " + httpReq.getRequestURI());

        // MUST be called to continue the chain -- if doFilter() is never invoked, the chain
        // stops here and the target servlet (and any later filters) never run at all. This is
        // the single most common filter bug: forgetting this line (or returning early without
        // it) causes every matching request to hang or get no response, with no obvious error.
        chain.doFilter(request, response);

        // Everything from here down runs on the way BACK OUT, once the servlet (and any
        // filters after this one in the chain) have already finished.
        long duration = System.currentTimeMillis() - start;
        System.out.println("[LoggingFilter] Completed in " + duration + "ms");
    }

    @Override
    public void destroy() {
        System.out.println("[LoggingFilter] destroy() -- filter shutting down");
    }
}


// ---------------------------------------------------------------------------
// 2) A filter chain -- multiple filters applying in sequence, including one
//    that DELIBERATELY does NOT call chain.doFilter(...) to short-circuit.
// ---------------------------------------------------------------------------

/**
 * Must run before anything else touches request parameters -- ordering with
 * @WebFilter annotations is NOT deterministic across containers; when true
 * ordering matters (as it does here), declare it explicitly via web.xml
 * <filter-mapping> entries instead, or use a framework's registration API.
 */
@WebFilter(urlPatterns = "/*", filterName = "characterEncodingFilter")
class CharacterEncodingFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        request.setCharacterEncoding("UTF-8"); // must run FIRST, before parameters are read downstream
        chain.doFilter(request, response);
    }
}

/**
 * Guards everything under /admin/*. Deliberately does NOT call
 * chain.doFilter(...) when the user is unauthenticated -- a legitimate,
 * common pattern for short-circuiting the chain. The important rule: a
 * COMPLETE response (here, a redirect) must already be written before
 * returning without calling doFilter(), since nothing downstream will run
 * to do it instead.
 */
@WebFilter(urlPatterns = "/admin/*", filterName = "authenticationFilter")
class AuthenticationFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpReq = (HttpServletRequest) request;
        HttpServletResponse httpResp = (HttpServletResponse) response;

        if (httpReq.getSession(false) == null || httpReq.getSession().getAttribute("user") == null) {
            httpResp.sendRedirect("/login");
            return;   // chain.doFilter() NOT called -- the servlet (and any later filters)
                       // never run for an unauthenticated request
        }
        chain.doFilter(request, response);   // authenticated -- let the request continue
    }
}


// ---------------------------------------------------------------------------
// 3) A ServletContextListener -- application-wide startup/shutdown, guaranteed
//    to run before ANY servlet handles ANY request (unlike a single servlet's
//    init(), which is scoped only to that one servlet).
// ---------------------------------------------------------------------------

@WebListener
class AppStartupListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        // Called ONCE, when the whole web application starts up -- BEFORE any servlet's
        // init() runs. Ideal for truly application-wide setup: connection pools, caches,
        // background schedulers. NOTE: expensive work here blocks application startup
        // entirely -- the app isn't considered "up" until this method returns.
        System.out.println("[AppStartupListener] Application starting -- initializing shared resources");
        Object pool = createConnectionPool();
        sce.getServletContext().setAttribute("dataSource", pool);
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        // Called ONCE, when the application is shutting down/undeploying -- the natural
        // place to release what contextInitialized acquired.
        System.out.println("[AppStartupListener] Application shutting down -- releasing shared resources");
        Object pool = sce.getServletContext().getAttribute("dataSource");
        closeConnectionPool(pool);
    }

    private Object createConnectionPool() {
        // In a real app: return a javax.sql.DataSource / HikariCP pool / etc.
        return new Object();
    }

    private void closeConnectionPool(Object pool) {
        // In a real app: pool.close() or equivalent.
    }
}


// ---------------------------------------------------------------------------
// 4) An HttpSessionListener -- reacting to session creation/destruction,
//    e.g. maintaining a live "currently online users" counter.
// ---------------------------------------------------------------------------

@WebListener
class SessionTrackingListener implements HttpSessionListener {
    private static final AtomicInteger activeSessions = new AtomicInteger(0);

    @Override
    public void sessionCreated(HttpSessionEvent se) {
        int count = activeSessions.incrementAndGet();
        System.out.println("[SessionTrackingListener] Session created -- active sessions now: " + count);
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent se) {
        int count = activeSessions.decrementAndGet();
        System.out.println("[SessionTrackingListener] Session destroyed (timeout or invalidate()) -- "
                + "active sessions now: " + count);
    }
}

/*
 * Overview of other common listener types (Theory 05):
 *   ServletContextListener          - application startup/shutdown
 *   HttpSessionListener             - session created/destroyed (shown above)
 *   ServletRequestListener          - request started/finished (e.g. correlation IDs)
 *   ServletContextAttributeListener - an attribute added/removed/replaced on the ServletContext
 *   HttpSessionAttributeListener    - an attribute added/removed/replaced on an HttpSession
 */


// ---------------------------------------------------------------------------
// 5) Thread-safety in servlets -- instance variable pitfalls. The container
//    creates ONE servlet instance and handles MANY concurrent requests
//    against it via a thread pool, so mutable instance fields are shared,
//    unsynchronized state across every concurrent request.
// ---------------------------------------------------------------------------

/*
 * BAD -- classic race condition (shown as a comment; do not use):
 *
 * public class CounterServlet extends HttpServlet {
 *     private int hitCount = 0;   // INSTANCE field -- one copy, shared by every concurrent thread
 *
 *     protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
 *         hitCount++;   // read-modify-write is NOT atomic -- two threads can both read the same
 *                       // value before either writes back the incremented result, LOSING an update
 *         resp.getWriter().write("Hits: " + hitCount);
 *     }
 * }
 *
 * Thread A: reads hitCount (5)
 * Thread B: reads hitCount (5)          <-- both threads see the SAME stale value
 * Thread A: writes hitCount = 6
 * Thread B: writes hitCount = 6          <-- B's write overwrites A's -- one increment is LOST
 * Expected: 7.  Actual: 6.
 */

/** GOOD -- Fix 1 (best): AtomicInteger performs read-modify-write atomically, no lost updates. */
@WebServlet("/safe-counter-v2")
class SafeCounterServletV2 extends HttpServlet {
    private final AtomicInteger hitCount = new AtomicInteger(0);

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        int current = hitCount.incrementAndGet();   // atomic -- no explicit lock needed
        resp.getWriter().write("Hits: " + current);
    }
}

/** GOOD -- Fix 2 (works, but serializes access -- hurts concurrency/throughput). */
@WebServlet("/synchronized-counter")
class SynchronizedCounterServlet extends HttpServlet {
    private int hitCount = 0;
    private final Object lock = new Object();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        int current;
        synchronized (lock) {
            current = ++hitCount;
        }
        resp.getWriter().write("Hits: " + current);
    }
}

/** GOOD -- Fix 3 (best default posture): no shared mutable instance state at all -- keep
 *  per-request data in LOCAL variables, which are inherently thread-safe (each thread has
 *  its own stack). Also shows what IS safe as an instance field: an immutable value read
 *  once in init() and never mutated afterward. */
@WebServlet("/order-lookup")
class OrderLookupServlet extends HttpServlet {

    // Safe: assigned ONCE in init(), only ever READ afterward -- effectively immutable,
    // no different from a hand-written servlet's safe read-only fields (Theory 01).
    private String serviceRegion;

    @Override
    public void init() {
        this.serviceRegion = "us-east"; // in a real app, from a config/init-param
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String userId = req.getParameter("userId");   // LOCAL variable -- safe, each thread has its own
        String orderId = "ORDER-" + userId + "-" + serviceRegion; // pretend lookup
        resp.getWriter().write("Latest order: " + orderId);
    }
}

/*
 * Note: HttpSession and request attributes are NOT subject to this problem -- each client
 * gets its OWN HttpSession, and each request gets its OWN HttpServletRequest, so storing
 * per-user or per-request data there is inherently safe from cross-thread interference
 * between DIFFERENT users' requests.
 *
 * Best practices summary (Theory 05):
 *   - Use filters for cross-cutting concerns (logging, auth, encoding, compression).
 *   - Always call chain.doFilter(...) unless deliberately short-circuiting with a
 *     complete response already written.
 *   - Use ServletContextListener for application-wide setup/teardown, not an arbitrary
 *     servlet's init()/destroy().
 *   - Never store mutable, per-request state in a servlet/filter instance field.
 *   - Raw Filter/Listener APIs remain relevant even in a Spring app, for behavior the
 *     framework doesn't already provide out of the box.
 */
