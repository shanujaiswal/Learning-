# The doXxx Methods -- Handling Specific HTTP Verbs

--> As covered in Theory 01, `HttpServlet.service()` inspects the incoming request's HTTP method and dispatches to a matching `doXxx` method. This chapter goes deep on what you actually DO inside those methods: reading request data and writing a response.

```java
public class ProductServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        // GET -- should be safe (no side effects) and idempotent (repeatable
        // with the same result) -- used for retrieving/displaying data.
        String productId = req.getParameter("id");
        resp.setContentType("text/html");
        resp.getWriter().write("<h1>Product " + productId + "</h1>");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        // POST -- used for creating a resource / submitting a form / any
        // request with side effects. NOT idempotent by convention (calling
        // it twice may create two resources) and NOT safe to bookmark/refresh
        // blindly (the "confirm form resubmission" browser warning exists
        // exactly because of this).
        String name = req.getParameter("name");
        // ... save to a database ...
        resp.sendRedirect("confirmation.html"); // POST-redirect-GET pattern -- see below
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        // PUT -- conventionally idempotent: replaces a resource entirely.
        // Calling it N times with the same body should leave the same end state
        // as calling it once. Common in REST APIs for "update the whole resource".
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        // DELETE -- conventionally idempotent: deleting an already-deleted
        // resource is still "deleted" afterward, so repeating it is harmless.
        resp.setStatus(HttpServletResponse.SC_NO_CONTENT); // 204
    }
}
```

--> **The POST-redirect-GET (PRG) pattern** -- after a successful `doPost` that changes server state (e.g. saving a form), respond with `resp.sendRedirect(...)` rather than directly writing HTML. This makes the browser issue a fresh `GET` to the redirect target, so a page refresh re-runs the harmless `GET` instead of resubmitting the `POST` (which would otherwise risk duplicate form submissions/double charges/etc.).
--> **Safe vs idempotent, precisely** -- "safe" means the method causes no server-side side effects (read-only); "idempotent" means repeating the exact same request N times has the same effect as doing it once. `GET`/`HEAD`/`OPTIONS` are safe (and therefore idempotent too, vacuously). `PUT`/`DELETE` are idempotent but NOT safe (they do change state, just repeatably so). `POST`/`PATCH` are neither, by convention -- though nothing in the servlet API physically stops you from writing a `doGet` that deletes data; these are conventions the whole web (browsers, proxies, caches) relies on you following.

# HttpServletRequest

--> `HttpServletRequest` (which your servlet receives as a method parameter, already constructed by the container from the raw incoming HTTP request) is the read side of the request/response pair -- everything the client sent is accessible through it.

## Reading Request Parameters

--> "Parameters" in servlet terminology specifically means: URL query string values (`?name=value&other=value2`) AND, for a `POST` with `Content-Type: application/x-www-form-urlencoded` or `multipart/form-data`, form field values -- the servlet API merges both sources into the same parameter API so your code doesn't need to care which one it came from.

```java
protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
    String single = req.getParameter("name");           // one value, or null if absent
    String[] multi = req.getParameterValues("color");    // multiple values for the same key (e.g. checkboxes)
    Enumeration<String> allNames = req.getParameterNames(); // every parameter name present
    Map<String, String[]> all = req.getParameterMap();    // everything, as an unmodifiable map
}
```

--> **Gotcha -- `getParameter` returns `null`, not an empty string, for a missing parameter** -- always null-check before calling `.trim()`/`.equals()`/etc. on the result, or you get a `NullPointerException`.
--> **Reading other parts of the request:**

```java
protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
    String method = req.getMethod();                 // "GET"
    String uri = req.getRequestURI();                 // "/app/products"
    String query = req.getQueryString();               // "id=42&sort=asc" (raw, unparsed)
    String header = req.getHeader("User-Agent");        // a specific header, or null
    Enumeration<String> headers = req.getHeaderNames();  // every header name present
    String contentType = req.getContentType();          // e.g. "application/json"
    String remoteAddr = req.getRemoteAddr();            // client's IP address
    java.io.BufferedReader body = req.getReader();       // raw request body as text (e.g. for JSON APIs)
}
```

## Request Attributes vs Parameters -- a Critical Distinction

--> These two concepts are easy to confuse but serve entirely different purposes.

| | Parameters | Attributes |
|---|---|---|
| Type | Always `String` or `String[]` | Any `Object` |
| Source | Sent BY THE CLIENT (query string / form body) | Set BY SERVER-SIDE CODE |
| Mutable from server code? | No -- read-only, reflects what the client sent | Yes -- `setAttribute`/`getAttribute`/`removeAttribute` |
| Typical use | Reading form/query input | Passing data from a servlet to a JSP view via `RequestDispatcher.forward()` |

```java
protected void doGet(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {
    List<Product> products = productService.findAll(); // some server-side object, not a String
    req.setAttribute("products", products);            // stash it on the request
    // Forward to a JSP, which can then read "products" via ${products} in EL (Theory 04)
    req.getRequestDispatcher("/WEB-INF/views/productList.jsp").forward(req, resp);
}
```

--> Request attributes only live for the duration of a single request (including any internal forwards within that same request) -- they do NOT persist across separate HTTP requests. For that, you need a session (below).

# HttpServletResponse

--> `HttpServletResponse` is the write side -- your servlet's job is ultimately to populate this object (headers, status code, body) before the container flushes it back to the client.

```java
protected void doGet(HttpServletRequest req, HttpServletResponse resp)
        throws IOException {
    resp.setStatus(HttpServletResponse.SC_OK);              // 200 -- default if never set explicitly
    resp.setContentType("application/json");                 // sets the Content-Type header
    resp.setCharacterEncoding("UTF-8");                       // important for correct multi-byte text
    resp.setHeader("X-Custom-Header", "some-value");          // arbitrary custom header

    java.io.PrintWriter writer = resp.getWriter();             // for TEXT output
    writer.write("{\"status\":\"ok\"}");

    // OR, for binary output (images, files, etc.) -- use ONE OR THE OTHER,
    // never both getWriter() and getOutputStream() on the same response:
    // java.io.OutputStream out = resp.getOutputStream();
}
```

--> **Gotcha -- calling both `getWriter()` and `getOutputStream()` on the same response** throws `IllegalStateException` -- the response commits to ONE output mode (character or byte) per request, decided by whichever method you call first.
--> **`resp.sendRedirect(url)` vs `RequestDispatcher.forward(req, resp)`** -- these look similar but behave very differently, and mixing them up is one of the most common servlet interview questions.

| | `sendRedirect` | `forward` |
|---|---|---|
| Round trip | Server sends `302 Found` + `Location` header; browser makes a NEW request | Entirely server-side; browser is unaware, URL bar unchanged |
| Round trips | 2 (original request, then the new one to the redirect target) | 1 |
| Data sharing | None automatic -- new request, no access to original request attributes | Same `HttpServletRequest`/`HttpServletResponse` objects carried through -- attributes set before the forward ARE visible |
| Can target | Any URL, including a different domain entirely | Only resources within the same web application |
| Typical use | After a `POST` that changes state (PRG pattern), or redirecting to an external site | Handing off to a JSP to render a view, while keeping request-scoped data |

```java
// sendRedirect -- browser makes a fresh GET to /confirmation, request attributes are lost
resp.sendRedirect("confirmation.html");

// forward -- server-side handoff, request attributes set above ARE visible in the JSP
req.setAttribute("message", "Saved successfully");
req.getRequestDispatcher("/WEB-INF/views/result.jsp").forward(req, resp);
```

# Session Management with HttpSession

--> HTTP itself is **stateless** -- the server has no built-in memory of a client between one request and the next. A "session" is a server-side mechanism to correlate multiple requests from the SAME client over time (e.g. "is this user logged in?"), built on top of stateless HTTP using a token exchanged via a cookie or URL parameter.

```java
protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
    HttpSession session = req.getSession();      // gets the existing session, OR creates a new one
    // HttpSession session = req.getSession(false); // returns null instead of creating one -- use
                                                       // this to check "is there already a session?"
                                                       // without accidentally creating one as a side effect

    session.setAttribute("username", "alice");    // store arbitrary data, scoped to this user's session
    session.setAttribute("cartItemCount", 3);

    String username = (String) session.getAttribute("username"); // read it back later, any request
    session.setMaxInactiveInterval(30 * 60);       // session times out after 30 min of inactivity
    session.invalidate();                           // explicitly end the session (e.g. on logout)
}
```

--> **How it actually works under the hood** -- the FIRST time `req.getSession()` is called for a given client, the container generates a unique session ID, creates an in-memory `HttpSession` object keyed by that ID, and sends it back to the client as a cookie (named `JSESSIONID` by default). On every SUBSEQUENT request, the browser automatically resends that cookie, the container looks up the ID in its session store, and hands your servlet the SAME `HttpSession` object -- that's the entire mechanism that makes a stateless protocol feel "stateful" to the application.

```text
Request 1 (login): no JSESSIONID cookie sent
    --> container creates HttpSession, generates ID "ABC123"
    --> response includes: Set-Cookie: JSESSIONID=ABC123

Request 2 (view cart): browser automatically sends Cookie: JSESSIONID=ABC123
    --> container looks up session "ABC123" -> finds the SAME HttpSession object
    --> session.getAttribute("username") returns "alice", set in request 1
```

--> **Session scope vs request scope** -- data in `HttpSession` persists across MANY requests from the same client until the session times out or is invalidated; data in request attributes (previous section) only lives for one request (plus any forwards within it). Choosing the wrong scope is a common bug: storing per-request data in the session leaks memory and can leak between logically separate operations; storing per-user state (login identity, shopping cart) in request attributes loses it the instant the request ends.

# Cookies

--> A **cookie** is a small piece of data the server asks the browser to store and automatically resend with future requests to the same domain/path. Sessions are typically implemented ON TOP of a cookie (`JSESSIONID`), but you can also create and read cookies directly for your own purposes (remembering preferences, "remember me" tokens, etc.).

```java
protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
    // Creating and sending a cookie to the client
    Cookie prefCookie = new Cookie("theme", "dark");
    prefCookie.setMaxAge(60 * 60 * 24 * 30);   // 30 days, in seconds; omit/negative = session-only cookie
    prefCookie.setPath("/");                    // which paths on this domain get the cookie resent
    prefCookie.setHttpOnly(true);                // JavaScript CANNOT read this cookie -- mitigates XSS token theft
    prefCookie.setSecure(true);                  // only sent over HTTPS -- essential for anything sensitive
    resp.addCookie(prefCookie);

    // Reading cookies sent by the client
    Cookie[] cookies = req.getCookies();          // may be null if the client sent none at all!
    if (cookies != null) {
        for (Cookie c : cookies) {
            if ("theme".equals(c.getName())) {
                String theme = c.getValue();       // "dark"
            }
        }
    }
}
```

--> **Gotcha -- `req.getCookies()` can return `null`**, not an empty array, when the client sends no cookies at all -- always null-check before iterating.
--> **`setHttpOnly(true)` and `setSecure(true)` are security essentials**, not optional extras, for any cookie carrying sensitive data (session IDs, auth tokens) -- `HttpOnly` blocks client-side JavaScript from reading the cookie (closing off a common XSS attack vector: stealing session cookies via injected script), and `Secure` ensures the cookie is never sent in plaintext over an unencrypted HTTP connection.
--> **Cookies vs `HttpSession` -- when to use which** -- use `HttpSession` for anything that should live server-side (you don't want the client able to read or tamper with it, and you don't want to burden every request with a large payload); use a plain cookie for small, non-sensitive, client-visible preferences (UI theme, a "don't show this again" flag) where server-side session storage would be overkill.

# Common Gotchas

--> **Character encoding not set before reading parameters** -- if the client submits a form with non-ASCII characters and `req.setCharacterEncoding(...)` isn't called BEFORE the first `getParameter()` call, the container may have already parsed the parameters with the wrong (often default ISO-8859-1) encoding, producing mojibake that can't be fixed after the fact. Call `req.setCharacterEncoding("UTF-8")` as the very first line of your method when encoding matters.
--> **Response already committed** -- once any part of the response (typically enough body bytes to fill the output buffer) has been physically sent to the client, calling `resp.sendRedirect(...)` or `resp.setStatus(...)` afterward throws `IllegalStateException: response already committed` -- decide on your response's status/headers BEFORE writing significant body content.
--> **Session fixation** -- if a session ID is established before a user authenticates and then reused unchanged after login, an attacker who can plant a known session ID on a victim's browser can hijack their post-login session. The standard mitigation is to call `req.changeSessionId()` (or invalidate and create a fresh session) immediately upon successful authentication, which most modern frameworks (Spring Security included) do automatically.
--> **Storing non-serializable objects in `HttpSession`** -- if the container is configured to persist sessions across restarts, or replicate them across a cluster, every attribute must be `Serializable` -- storing a raw JDBC `Connection` or similar non-serializable object in the session will fail (or silently break clustering) in that setup.

# Best Practices Summary

--> **Match HTTP verbs to their semantic conventions** -- `GET` for safe reads, `POST` for creates/side effects, `PUT`/`DELETE` for idempotent updates/removals -- browsers, proxies, and caches all assume you're following these conventions.
--> **Use the POST-redirect-GET pattern** after any state-changing `POST` to prevent duplicate-submission-on-refresh.
--> **Use `forward()` to hand off to a view (JSP) within the same app**, keeping request-scoped data; use `sendRedirect()` when the browser genuinely needs a new URL/round trip.
--> **Set character encoding explicitly and early**, before reading any parameters or writing any response body.
--> **Mark sensitive cookies `HttpOnly` and `Secure`**, always.
--> **Prefer `getSession(false)`** when you only want to check for an existing session without creating one as a side effect.
--> **Regenerate the session ID on login** (session fixation defense) if you're not already using a framework (like Spring Security) that does it for you.
