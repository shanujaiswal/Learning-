# What a Servlet Is and the Problem It Solves

--> A **servlet** is a Java class that runs inside a **servlet container** (also called a web container -- e.g. Apache Tomcat, Jetty, or the servlet engine embedded inside an application server like WildFly) and handles HTTP requests/responses on the server side. Before servlets existed, dynamic web content on the JVM was generated through CGI (Common Gateway Interface) scripts, where every incoming request spawned a brand-new OS process to handle it -- expensive, slow, and it didn't scale.
--> The problem servlets solve is **efficient, in-process handling of many concurrent HTTP requests using threads instead of processes**. A servlet container keeps a pool of servlet instances loaded in memory (typically ONE instance per servlet class, not one per request) and dispatches each incoming request to a **thread** that invokes methods on that shared instance -- dramatically cheaper than forking a process per request, and it lets the JVM's class loading, JIT compilation, and connection pooling amortize across requests instead of restarting fresh every time.
--> Servlets are part of the **Jakarta EE** specification (formerly **Java EE** / **J2EE** -- renamed after the project moved from Oracle to the Eclipse Foundation in 2019, which is also why the package name changed from `javax.servlet` to `jakarta.servlet` starting with Servlet spec 5.0 / Jakarta EE 9). The specification defines a set of interfaces and abstract classes; the actual HTTP handling logic (parsing headers, managing keep-alive connections, TLS termination, thread pooling) is implemented by the container, not by your code -- your servlet class just plugs into the container's request-handling pipeline.

```text
                     Servlet Container (e.g. Tomcat)
        +----------------------------------------------------+
Client  |   Thread Pool                                       |
Browser |   +--------+  +--------+  +--------+               |
  <---> |   |Thread 1|  |Thread 2|  |Thread 3|  ...           |
 HTTP   |   +---+----+  +---+----+  +---+----+               |
        |       |            |            |                   |
        |       v            v            v                   |
        |   +-----------------------------------------+       |
        |   |   ONE shared instance of MyServlet        |      |
        |   |   (created once, reused across requests)  |      |
        |   +-----------------------------------------+       |
        +----------------------------------------------------+
```

--> **Where Spring MVC fits in** -- if you've already studied the Spring/Spring Boot chapters, you've been using servlets the whole time without writing one yourself: Spring MVC's `DispatcherServlet` IS a servlet (it extends `HttpServlet` under the hood) that Spring Boot auto-registers and configures for you. Understanding raw servlets here is what demystifies what `DispatcherServlet` is actually doing -- routing requests, invoking your `@Controller` methods, and writing responses -- using exactly the same container contract described in this chapter.

# The Servlet Interface

--> Every servlet, no matter how it's written, ultimately implements the `jakarta.servlet.Servlet` interface. This is the root contract the container relies on -- it doesn't know or care about your class's specific type, it only knows it can call these five methods on anything implementing `Servlet`.

```java
public interface Servlet {
    void init(ServletConfig config) throws ServletException;
    void service(ServletRequest req, ServletResponse res)
            throws ServletException, IOException;
    void destroy();
    ServletConfig getServletConfig();
    String getServletInfo();
}
```

--> **`init(ServletConfig)`** -- called EXACTLY ONCE by the container, right after the servlet instance is created, before it can serve any request. Used for one-time setup: reading init parameters, opening a resource pool, loading configuration.
--> **`service(ServletRequest, ServletResponse)`** -- called ONCE PER REQUEST, on whatever thread the container assigns to handle that request. This is where request handling logic lives.
--> **`destroy()`** -- called EXACTLY ONCE, when the container is shutting down the servlet (app undeployed, container stopping) -- used for cleanup: closing resources opened in `init`.
--> **`getServletConfig()` / `getServletInfo()`** -- accessor methods; rarely overridden directly since the abstract base classes (below) implement them for you.

--> In practice, **nobody implements `Servlet` directly** -- you always extend one of the abstract base classes described next, which implement the boilerplate parts of this interface for you and expose a friendlier API.

# GenericServlet and HttpServlet

--> The Servlet API provides two abstract classes that implement `Servlet` and add layers of convenience -- each one narrowing the API to be more specific and easier to use for the common case (HTTP).

## GenericServlet

--> `GenericServlet` implements the `Servlet` interface (and `ServletConfig`, for convenience) and provides default, mostly-empty implementations of everything except `service()`, which remains abstract. It is **protocol-agnostic** -- it doesn't know anything about HTTP specifically, just the generic notion of a "request" and "response."
--> It stores the `ServletConfig` passed to `init()` automatically, so `getServletConfig()`, `getInitParameter(name)`, and `getServletContext()` all just work without you writing any storage code yourself, as long as you call `super.init(config)` if you override `init(ServletConfig)`.

```java
public abstract class MyGenericServlet extends GenericServlet {
    @Override
    public void service(ServletRequest req, ServletResponse res)
            throws ServletException, IOException {
        // You'd have to manually figure out whether this is a GET, POST, etc.
        // yourself -- GenericServlet gives you no HTTP-specific help at all.
    }
}
```

--> In practice, `GenericServlet` is almost never extended directly for web applications today -- it exists mostly for historical reasons (the Servlet API was originally designed to be protocol-neutral, imagining it might be used for non-HTTP protocols too) and as the base class that `HttpServlet` itself extends.

## HttpServlet

--> `HttpServlet` extends `GenericServlet` and adds **HTTP-specific** behavior -- this is the class virtually every real-world servlet extends. Its `service()` method is no longer abstract -- `HttpServlet` implements it FOR you: it inspects the incoming request's HTTP method (`GET`, `POST`, `PUT`, `DELETE`, `HEAD`, `OPTIONS`, `TRACE`) and dispatches to a correspondingly-named protected method.

```java
public class MyServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("text/plain");
        resp.getWriter().write("Hello from doGet!");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.getWriter().write("Hello from doPost!");
    }
    // You never override service() yourself -- HttpServlet's service() does
    // the method dispatch (GET -> doGet, POST -> doPost, etc.) for you.
}
```

--> **Dispatch table** -- this is the core mental model for how `HttpServlet` routes a request:

| Incoming HTTP method | HttpServlet dispatches to |
|---|---|
| `GET` | `doGet(req, resp)` |
| `POST` | `doPost(req, resp)` |
| `PUT` | `doPut(req, resp)` |
| `DELETE` | `doDelete(req, resp)` |
| `HEAD` | `doHead(req, resp)` (default impl calls `doGet` but discards the body, keeping only headers) |
| `OPTIONS` | `doOptions(req, resp)` (default impl auto-generates an `Allow` header listing supported methods) |
| `TRACE` | `doTrace(req, resp)` (default impl echoes the request back -- rarely overridden) |

--> **Gotcha -- overriding `service()` directly on an `HttpServlet`** -- technically legal (it's not `final`), but it throws away all the HTTP method dispatch logic `HttpServlet` gives you for free, forcing you to reimplement method-checking yourself. Virtually always override the specific `doXxx` methods instead, never `service()`, when extending `HttpServlet`.
--> **Gotcha -- default `doXxx` behavior when not overridden** -- if a client sends a `POST` to a servlet that only overrides `doGet`, the inherited `HttpServlet.doPost()` responds with HTTP `405 Method Not Allowed` automatically. This is a built-in safety net, not a silent no-op.

# The Servlet Lifecycle in Detail

--> Understanding lifecycle TIMING is one of the most important things to get right about servlets, because it explains thread-safety concerns (covered in depth in Theory 05) and where it's safe to do expensive setup work.

```text
1. CONTAINER STARTUP (or first request, if lazily loaded)
   Container reads deployment descriptor (web.xml) and/or scans for @WebServlet
   annotations, discovering which servlet classes exist and what URLs map to them.
        |
        v
2. INSTANTIATION
   Container calls the servlet class's no-arg constructor exactly ONCE
   (by default) -- creating a SINGLE shared instance for the whole application.
        |
        v
3. init(ServletConfig config)
   Called exactly ONCE, before the servlet handles its first request.
   Use this for one-time setup: read init-params, open a connection pool, etc.
        |
        v
4. service(request, response)  -->  doGet/doPost/doPut/doDelete/...
   Called ONCE PER REQUEST, potentially by many different threads CONCURRENTLY,
   for as long as the application is deployed. This is the hot path.
        |
        v
5. destroy()
   Called exactly ONCE, when the container is undeploying the app or shutting
   down -- release resources opened in init() here (close connections, stop
   background threads, flush buffers).
```

```java
public class LifecycleDemoServlet extends HttpServlet {
    private int requestCount = 0;   // INSTANCE field -- shared across ALL requests/threads!

    @Override
    public void init(ServletConfig config) throws ServletException {
        System.out.println("init() called ONCE -- servlet is starting up");
        // e.g. requestCount = 0; open a DB connection pool; read config
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        requestCount++;   // NOT thread-safe -- see Theory 05 for why this matters
        resp.getWriter().write("This servlet has handled " + requestCount + " requests");
    }

    @Override
    public void destroy() {
        System.out.println("destroy() called ONCE -- servlet is shutting down");
        // e.g. close the DB connection pool, flush any buffered data
    }
}
```

--> **Deep dive -- "one instance, many threads"** -- this is the single most important lifecycle fact for writing correct servlets. Because the container creates ONE instance of your servlet class and reuses it across every concurrent request (rather than creating a fresh instance per request, the way you might naively expect coming from, say, plain object-oriented design), any INSTANCE or STATIC field you declare is SHARED, mutable state accessed by multiple threads at once. `requestCount++` above is a classic race condition -- concurrent requests can interleave the read-increment-write sequence and lose updates. The fix (detailed in Theory 05) is to keep servlets stateless and put per-request data in local variables inside `doGet`/`doPost` instead of instance fields.
--> **`SingleThreadModel` (deprecated, avoid)** -- an old interface a servlet could implement to ask the container to guarantee only one thread executes its `service()` method at a time (either by serializing access or pooling multiple instances). It was deprecated in Servlet 2.4 and removed from the spec in later versions precisely because it encouraged sidestepping the real problem (mutable shared state) rather than fixing it -- correctly synchronizing or, better, avoiding shared mutable state is the recommended approach.

# Configuring Servlets: web.xml vs @WebServlet Annotations

--> A servlet class alone does nothing until the container is told **which URL(s) should route to it**. There are two ways to declare this mapping, and modern code strongly favors the second.

## web.xml (Deployment Descriptor) -- the traditional way

--> `web.xml` lives at `WEB-INF/web.xml` inside the deployed web application (a WAR file's structure) and is an XML file the container reads at startup to discover servlets, their URL mappings, init parameters, filters, listeners, session config, and more.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<web-app xmlns="https://jakarta.ee/xml/ns/jakartaee"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee
             https://jakarta.ee/xml/ns/jakartaee/web-app_5_0.xsd"
         version="5.0">

    <servlet>
        <servlet-name>myServlet</servlet-name>
        <servlet-class>com.example.MyServlet</servlet-class>
        <init-param>
            <param-name>maxResults</param-name>
            <param-value>50</param-value>
        </init-param>
        <load-on-startup>1</load-on-startup>
    </servlet>

    <servlet-mapping>
        <servlet-name>myServlet</servlet-name>
        <url-pattern>/hello</url-pattern>
    </servlet-mapping>

</web-app>
```

--> **`<load-on-startup>`** -- an integer controlling instantiation timing. Without it, a servlet is lazily instantiated on its FIRST incoming request (which makes that first request unusually slow). A non-negative value (e.g. `1`) tells the container to instantiate and call `init()` eagerly at application startup instead, with lower numbers loaded first when multiple servlets specify it -- important for servlets doing expensive setup you don't want a real user to wait through.
--> **`<init-param>`** -- read inside your servlet via `getServletConfig().getInitParameter("maxResults")` (or just `getInitParameter(...)` directly, since `GenericServlet` delegates it for convenience) -- a way to pass simple string configuration into a servlet without hardcoding it in Java.
--> Verbose and fully external to the Java code -- you WILL encounter `web.xml` in older/legacy enterprise codebases (and it's still valid today, even alongside annotations), but new code overwhelmingly favors annotations instead.

## @WebServlet Annotation -- the modern way

```java
import jakarta.servlet.annotation.WebInitParam;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet(
    name = "myServlet",
    urlPatterns = {"/hello"},
    loadOnStartup = 1,
    initParams = {
        @WebInitParam(name = "maxResults", value = "50")
    }
)
public class MyServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws java.io.IOException {
        String max = getServletConfig().getInitParameter("maxResults"); // "50"
        resp.getWriter().write("Hello, annotated servlet! maxResults=" + max);
    }
}
```

--> Introduced in Servlet 3.0 (2009) specifically to eliminate the need for `web.xml` for the common case -- the container scans compiled classes for `@WebServlet` at startup and registers them automatically, no XML required. The mapping lives right next to the class it configures, which is easier to keep in sync and easier to read.
--> `web.xml` and annotations CAN coexist in the same application -- `web.xml` entries take precedence for a servlet that's declared in BOTH places (this matters for overriding a third-party JAR's annotated servlet without touching its source), but for your own code, pick one style consistently and avoid mixing them for the same servlet.

```text
web.xml               --> fully external, verbose, still valid, useful for overriding
                           third-party/library servlet config without touching their code
@WebServlet annotation --> config lives next to the class, no XML file needed,
                           the dominant style for new code since Servlet 3.0
```

# Common Gotchas

--> **Confusing "one instance" with "one instance per request"** -- the container does NOT create a fresh servlet instance per request; it's one shared instance handling many concurrent requests via the thread pool. This is the root cause of most thread-safety bugs in naive servlet code (see Theory 05 for the full treatment).
--> **Forgetting `super.init(config)`** -- if you override the single-argument `init(ServletConfig)` (rather than the simpler no-arg `init()` -- see below) without calling `super.init(config)`, `GenericServlet`'s internal storage of the `ServletConfig` never happens, and later calls to `getServletConfig()`/`getInitParameter(...)` silently return unexpected results or throw `NullPointerException`. Prefer overriding the no-arg `init()` instead, which `GenericServlet` calls internally AFTER it has already stored the config -- it doesn't have this pitfall.
--> **404 vs 405 confusion** -- hitting an unmapped URL gives `404 Not Found` (no servlet matches this URL at all); hitting a mapped URL with an HTTP method the servlet doesn't override gives `405 Method Not Allowed` (the servlet exists, but this specific verb isn't handled) -- these mean very different things when debugging.
--> **Class reloading confusion during development** -- most containers (Tomcat included) support hot-reloading changed classes, but changes to `web.xml` itself, or structural changes like new annotations, sometimes require a full container restart, not just a redeploy -- if changes don't seem to take effect, restart the container before assuming your code is wrong.

# Best Practices Summary

--> **Always extend `HttpServlet`**, never implement `Servlet` or extend `GenericServlet` directly, for web applications -- you get HTTP method dispatch for free.
--> **Override the specific `doGet`/`doPost`/etc. methods**, never `service()` itself, on an `HttpServlet`.
--> **Prefer `@WebServlet` annotations** for new code; reach for `web.xml` only when you need to override third-party servlet configuration or maintain a legacy application.
--> **Never store per-request or per-user mutable state in instance fields** -- keep it local to the `doGet`/`doPost` method, or in `HttpSession`/request attributes as appropriate (Theory 02).
--> **Use `init()` and `destroy()` for expensive one-time setup/teardown** (connection pools, caches), not for anything that varies per request.
--> **Use `<load-on-startup>` (or `loadOnStartup` on `@WebServlet`)** for servlets with expensive initialization, so the cost is paid once at deployment rather than on some unlucky user's first request.
