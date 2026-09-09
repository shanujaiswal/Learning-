/**
 * HttpRequestHandlingSessionDemo.java
 *
 * Demonstrates, with heavy comments explaining WHAT would happen at runtime
 * inside a real servlet container:
 *     1. doGet/doPost/doPut/doDelete overrides and the safe/idempotent conventions
 *     2. Reading HttpServletRequest parameters, headers, and other request data
 *     3. Request attributes vs parameters, and forward() vs sendRedirect()
 *     4. Writing HttpServletResponse (status, headers, body)
 *     5. HttpSession usage -- setAttribute/getAttribute/invalidate, session scope
 *     6. Cookie creation and reading, including HttpOnly/Secure flags
 *
 * Covers Theory chapter:
 *     10) Java/12) Servlets and JSP/Theory/02 HTTP Request Handling and Session Management.md
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
 *   6. Visit http://localhost:8080/<app-name>/products in a browser to trigger doGet,
 *      or POST a form to /products to trigger doPost, etc.
 */

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Map;


// ---------------------------------------------------------------------------
// 1) doGet/doPost/doPut/doDelete -- matching HTTP verbs to their conventional
//    semantics (safe/idempotent), plus reading request data and writing a response.
// ---------------------------------------------------------------------------

/**
 * ProductServlet illustrates all four common doXxx overrides on one resource,
 * and shows the full breadth of HttpServletRequest reading / HttpServletResponse
 * writing described in Theory 02.
 */
@WebServlet("/products")
public class HttpRequestHandlingSessionDemo extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        // GET -- safe (no side effects) and idempotent -- used for retrieving/displaying data.

        // ---- Reading request PARAMETERS (query string ?id=42&color=red&color=blue) ----
        String productId = req.getParameter("id");             // one value, or null if absent
        String[] colors = req.getParameterValues("color");      // multiple values for the same key
        Enumeration<String> paramNames = req.getParameterNames(); // every parameter name present
        Map<String, String[]> allParams = req.getParameterMap();  // everything, unmodifiable map

        // GOTCHA: getParameter returns null, not "", for a missing parameter -- always null-check
        // before calling .trim()/.equals()/etc., or risk a NullPointerException.
        if (productId == null) {
            productId = "unknown";
        }

        // ---- Reading other parts of the request ----
        String method = req.getMethod();                  // "GET"
        String uri = req.getRequestURI();                   // "/app/products"
        String query = req.getQueryString();                 // raw, unparsed query string
        String userAgent = req.getHeader("User-Agent");       // a specific header, or null
        Enumeration<String> headerNames = req.getHeaderNames(); // every header name present
        String remoteAddr = req.getRemoteAddr();              // client's IP address

        System.out.println("[ProductServlet] " + method + " " + uri + "?" + query
                + " from " + remoteAddr + " (User-Agent: " + userAgent + ")");

        // ---- Request ATTRIBUTES vs PARAMETERS -- a critical distinction ----
        // Parameters: always String/String[], sent BY THE CLIENT, read-only from server code.
        // Attributes: any Object, set BY SERVER-SIDE CODE, used to hand data to a forwarded view.
        req.setAttribute("resolvedProductId", productId);
        req.setAttribute("requestedColors", colors);

        // forward() -- server-side handoff; browser is unaware, URL bar unchanged, ONE round trip,
        // and attributes set above ARE visible to whatever we forward to (a JSP in a real app;
        // here we simulate the "view" inline via another servlet path for illustration).
        RequestDispatcher dispatcher = req.getRequestDispatcher("/WEB-INF/views/productDetail.jsp");
        // dispatcher.forward(req, resp); // would hand off to a JSP -- commented out, no real JSP here

        // ---- Writing the HttpServletResponse ----
        resp.setStatus(HttpServletResponse.SC_OK);           // 200 -- default if never set explicitly
        resp.setContentType("text/html");                     // sets the Content-Type header
        resp.setCharacterEncoding("UTF-8");                    // important for correct multi-byte text
        resp.setHeader("X-Custom-Header", "demo-value");        // arbitrary custom header
        resp.getWriter().write("<h1>Product " + productId + "</h1>");
        // NOTE: never call both getWriter() and getOutputStream() on the same response --
        // that throws IllegalStateException (the response commits to ONE output mode).
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        // POST -- used for creating a resource / submitting a form / any request with side
        // effects. NOT idempotent by convention, and NOT safe to bookmark/refresh blindly.

        // Set character encoding BEFORE reading any parameters, or the container may already
        // have parsed them with the wrong (often ISO-8859-1) default encoding -- unfixable after
        // the fact if the client submitted non-ASCII characters.
        req.setCharacterEncoding("UTF-8");

        String name = req.getParameter("name");
        System.out.println("[ProductServlet] doPost: creating product '" + name + "'");
        // ... save to a database here in a real application ...

        // POST-redirect-GET (PRG) pattern -- after a successful state-changing POST, redirect
        // rather than directly writing HTML. This makes the browser issue a fresh GET to the
        // redirect target, so a page refresh re-runs the harmless GET instead of resubmitting
        // the POST (avoiding duplicate submissions on refresh).
        resp.sendRedirect("confirmation.html");
        // Contrast with forward(): sendRedirect is a 302 + Location header, TWO round trips,
        // no automatic data sharing (new request, original request attributes are lost), and
        // can target ANY url including a different domain. forward() is one round trip,
        // server-side only, same request/response objects carried through, and can only
        // target resources within this same web application.
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        // PUT -- conventionally idempotent: replaces a resource entirely. Calling it N times
        // with the same body should leave the same end state as calling it once.
        req.setCharacterEncoding("UTF-8");

        // Reading the raw request body as text (e.g. a JSON payload for a REST-style PUT)
        BufferedReader bodyReader = req.getReader();
        StringBuilder body = new StringBuilder();
        String line;
        while ((line = bodyReader.readLine()) != null) {
            body.append(line);
        }
        System.out.println("[ProductServlet] doPut: replacing resource with body: " + body);

        resp.setContentType("application/json");
        resp.getWriter().write("{\"status\":\"replaced\"}");
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        // DELETE -- conventionally idempotent: deleting an already-deleted resource is still
        // "deleted" afterward, so repeating it is harmless.
        String productId = req.getParameter("id");
        System.out.println("[ProductServlet] doDelete: removing product " + productId);
        resp.setStatus(HttpServletResponse.SC_NO_CONTENT); // 204 -- no body needed
    }
}


// ---------------------------------------------------------------------------
// 2) HttpSession -- setAttribute/getAttribute/invalidate, session scope,
//    and the JSESSIONID cookie mechanism underneath it.
// ---------------------------------------------------------------------------

@WebServlet("/login")
class LoginServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String username = req.getParameter("username");

        // req.getSession() gets the existing session OR creates a new one. The FIRST time this
        // is called for a given client, the container generates a unique session ID, creates an
        // in-memory HttpSession keyed by that ID, and sends it back as a Set-Cookie: JSESSIONID=...
        // response header. Every SUBSEQUENT request automatically resends that cookie, and the
        // container hands your servlet the SAME HttpSession object.
        HttpSession session = req.getSession();

        // Session fixation defense: regenerate the session ID immediately upon successful
        // authentication, so an attacker who planted a known pre-login session ID on the
        // victim's browser cannot hijack the post-login session.
        req.changeSessionId();

        session.setAttribute("username", username);   // store arbitrary data, scoped to this user
        session.setAttribute("cartItemCount", 0);
        session.setMaxInactiveInterval(30 * 60);        // session times out after 30 min of inactivity

        System.out.println("[LoginServlet] logged in '" + username + "', session id="
                + session.getId());

        resp.sendRedirect("welcome");
    }
}

@WebServlet("/welcome")
class WelcomeServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        // getSession(false) returns null instead of creating a new session as a side effect --
        // use this whenever you only want to CHECK for an existing session.
        HttpSession session = req.getSession(false);

        resp.setContentType("text/plain");
        if (session == null || session.getAttribute("username") == null) {
            resp.getWriter().write("Not logged in.");
            return;
        }

        String username = (String) session.getAttribute("username");
        // Session scope persists across MANY requests from the same client, unlike request
        // attributes (Theory 02), which only live for one request (plus any forwards within it).
        resp.getWriter().write("Welcome back, " + username + "! (session id=" + session.getId() + ")");
    }
}

@WebServlet("/logout")
class LogoutServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        HttpSession session = req.getSession(false);
        if (session != null) {
            session.invalidate();   // explicitly end the session -- e.g. on logout
            System.out.println("[LogoutServlet] session invalidated");
        }
        resp.sendRedirect("login.html");
    }
}


// ---------------------------------------------------------------------------
// 3) Cookies -- creation, reading, and the HttpOnly/Secure security essentials.
// ---------------------------------------------------------------------------

@WebServlet("/preferences")
class PreferencesServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String theme = req.getParameter("theme"); // e.g. "dark"

        // Creating and sending a cookie to the client
        Cookie prefCookie = new Cookie("theme", theme);
        prefCookie.setMaxAge(60 * 60 * 24 * 30);  // 30 days, in seconds; omit/negative = session-only
        prefCookie.setPath("/");                   // which paths on this domain get the cookie resent
        prefCookie.setHttpOnly(true);               // JS cannot read this cookie -- mitigates XSS token theft
        prefCookie.setSecure(true);                  // only sent over HTTPS -- essential for sensitive cookies
        resp.addCookie(prefCookie);

        resp.setContentType("text/plain");
        resp.getWriter().write("Preference saved: theme=" + theme);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        // Reading cookies sent by the client. GOTCHA: req.getCookies() can return null (not an
        // empty array) if the client sent no cookies at all -- always null-check before iterating.
        Cookie[] cookies = req.getCookies();
        String theme = "light"; // default
        if (cookies != null) {
            for (Cookie c : cookies) {
                if ("theme".equals(c.getName())) {
                    theme = c.getValue();
                }
            }
        }
        resp.setContentType("text/plain");
        resp.getWriter().write("Current theme preference: " + theme);
        // Cookies vs HttpSession: use HttpSession for anything server-side/sensitive (you don't
        // want the client able to read or tamper with it); use a plain cookie for small,
        // non-sensitive, client-visible preferences like this theme value.
    }
}
