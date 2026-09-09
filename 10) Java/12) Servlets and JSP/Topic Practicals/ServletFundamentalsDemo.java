/**
 * ServletFundamentalsDemo.java
 *
 * Demonstrates, with heavy comments explaining WHAT would happen at runtime
 * inside a real servlet container:
 *     1. The Servlet interface contract (init/service/destroy)
 *     2. GenericServlet vs HttpServlet
 *     3. HttpServlet's doGet/doPost/doPut/doDelete dispatch mechanism
 *     4. The full servlet lifecycle, instrumented with console logging
 *     5. Configuring a servlet via @WebServlet annotation (modern style)
 *     6. The "one instance, many threads" gotcha, illustrated directly
 *
 * Covers Theory chapter:
 *     10) Java/12) Servlets and JSP/Theory/01 Servlet Fundamentals and Lifecycle.md
 *
 * IMPORTANT -- this file will NOT compile or run standalone with plain `javac`/`java`.
 * It requires:
 *     1. The Jakarta Servlet API on the classpath (e.g. `jakarta.servlet-api` as a
 *        PROVIDED dependency -- the container supplies the real implementation at
 *        runtime, so you never bundle it yourself in the deployed WAR).
 *     2. A running servlet container (e.g. Apache Tomcat, Jetty) to actually deploy
 *        and invoke these servlets -- there is no `main()` method here because
 *        servlets are instantiated and driven entirely by the CONTAINER, not by you.
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
 *   6. Visit http://localhost:8080/<app-name>/hello in a browser to trigger doGet.
 */

import jakarta.servlet.GenericServlet;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebInitParam;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;


// ---------------------------------------------------------------------------
// 1) GenericServlet -- protocol-agnostic base class. Almost never extended
//    directly in real web apps, shown here purely for contrast with HttpServlet.
// ---------------------------------------------------------------------------

/**
 * A minimal, protocol-agnostic servlet. Note that GenericServlet forces YOU
 * to implement the single abstract service() method yourself, with no help
 * distinguishing GET from POST -- you'd have to inspect the request manually
 * (and even that requires casting to HttpServletRequest, since the plain
 * ServletRequest/ServletResponse types here carry no HTTP-specific methods).
 */
class RawGenericServletExample extends GenericServlet {

    @Override
    public void service(ServletRequest req, ServletResponse res)
            throws ServletException, IOException {
        // GenericServlet gives us NO automatic method dispatch -- if we cared
        // about GET vs POST here, we'd have to cast and check ourselves:
        if (req instanceof HttpServletRequest httpReq) {
            System.out.println("[RawGenericServletExample] handling raw " + httpReq.getMethod()
                    + " request -- no automatic dispatch, we did the check manually");
        }
        res.setContentType("text/plain");
        res.getWriter().write("Handled by GenericServlet.service() directly");
    }
}


// ---------------------------------------------------------------------------
// 2) HttpServlet with @WebServlet annotation -- the modern, standard approach
// ---------------------------------------------------------------------------

/**
 * HelloServlet demonstrates:
 *   - @WebServlet annotation-based configuration (no web.xml needed)
 *   - init-params read via getServletConfig().getInitParameter(...)
 *   - overriding the specific doGet/doPost methods (never service() itself)
 *   - the full lifecycle: init() once, doGet()/doPost() many times, destroy() once
 */
@WebServlet(
    name = "helloServlet",
    urlPatterns = {"/hello"},
    loadOnStartup = 1,                 // eagerly instantiated + init()'d at container startup,
                                         // rather than lazily on the first request
    initParams = {
        @WebInitParam(name = "greeting", value = "Hello")
    }
)
public class ServletFundamentalsDemo extends HttpServlet {

    private String greeting;   // set ONCE in init(), read-only afterward -- safe to share across threads

    // ---- LIFECYCLE PHASE 1: init() -- called EXACTLY ONCE, before any request ----
    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);   // CRITICAL -- without this, GenericServlet never stores `config`,
                                // and getServletConfig()/getInitParameter() would misbehave afterward.
                                // (Overriding the no-arg init() instead avoids this pitfall entirely --
                                // shown as an alternative in initializePool() below.)
        this.greeting = config.getInitParameter("greeting"); // "Hello", from @WebInitParam above
        System.out.println("[ServletFundamentalsDemo] init() -- servlet starting up, greeting='" + greeting + "'");
    }

    // ---- LIFECYCLE PHASE 2: service() -- HttpServlet implements this FOR us,
    //      dispatching to doGet/doPost/doPut/doDelete based on the HTTP method.
    //      We never override service() itself -- only the doXxx methods below. ----

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("text/plain");
        resp.getWriter().write(greeting + ", world! (handled by doGet)");
        System.out.println("[ServletFundamentalsDemo] doGet() handled a request on thread: "
                + Thread.currentThread().getName());
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("text/plain");
        resp.getWriter().write(greeting + ", world! (handled by doPost)");
        // Note: if a client sends PUT/DELETE to this servlet, HttpServlet's
        // INHERITED default doPut/doDelete implementations respond with
        // 405 Method Not Allowed automatically -- we never wrote that logic ourselves.
    }

    // ---- LIFECYCLE PHASE 3: destroy() -- called EXACTLY ONCE, at shutdown ----
    @Override
    public void destroy() {
        System.out.println("[ServletFundamentalsDemo] destroy() -- servlet shutting down, cleaning up");
    }
}


// ---------------------------------------------------------------------------
// 3) Thread-safety gotcha preview (fully explored in Theory/Practical 05) --
//    shown briefly here because it's a direct, immediate consequence of the
//    "one shared instance, many threads" lifecycle fact from Theory 01.
// ---------------------------------------------------------------------------

@WebServlet("/unsafe-counter")
class UnsafeCounterServlet extends HttpServlet {

    // DANGER: instance field, shared by every concurrent request/thread.
    // Two overlapping requests can interleave the read-increment-write
    // sequence of `hitCount++` and LOSE an update -- a classic race condition.
    private int hitCount = 0;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        hitCount++;   // NOT thread-safe -- see UnsafeCounterServlet's sibling below for the fix
        resp.getWriter().write("Unsafe hit count: " + hitCount
                + " (this number can be WRONG under concurrent load)");
    }
}

@WebServlet("/safe-counter")
class SafeCounterServlet extends HttpServlet {

    // FIX: AtomicInteger performs the read-modify-write as one atomic operation --
    // no lost updates possible, no explicit synchronized block needed either.
    private final AtomicInteger hitCount = new AtomicInteger(0);

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        int current = hitCount.incrementAndGet();
        resp.getWriter().write("Safe hit count: " + current + " (always correct under concurrent load)");
    }
}
