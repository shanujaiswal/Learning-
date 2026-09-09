/**
 * JstlExpressionLanguageDemo.java
 *
 * Demonstrates, with heavy comments explaining WHAT would happen at runtime
 * inside a real servlet container:
 *     1. A controller servlet setting up model data and forwarding to a JSP view
 *        (the Model 2 / MVC pattern, servlet-as-controller, JSP-as-view)
 *     2. JSTL core tags (c:forEach, c:if, c:choose/c:when/c:otherwise, c:out,
 *        c:set/c:remove, c:url) shown as commented .jsp-code blocks
 *     3. Expression Language (EL) syntax -- ${...}, operators, scope resolution,
 *        the `empty` operator, and EL implicit objects (param, header, cookie, ...)
 *     4. JSTL formatting tags (fmt:formatNumber, fmt:formatDate, fmt:message)
 *     5. The full MVC request flow diagram and why views live under WEB-INF/
 *
 * Covers Theory chapter:
 *     10) Java/12) Servlets and JSP/Theory/04 JSP Standard Tag Library and Expression Language.md
 *
 * IMPORTANT -- this file will NOT compile or run standalone with plain `javac`/`java`.
 * It requires:
 *     1. The Jakarta Servlet API AND the JSTL implementation (e.g. `jakarta.servlet.jsp.jstl`
 *        / `jakarta.servlet.jsp.jstl-api` plus an implementation jar such as Glassfish's,
 *        or whatever your container bundles) on the classpath.
 *     2. A running servlet container with JSP + JSTL support (e.g. Apache Tomcat with the
 *        JSTL jars added to WEB-INF/lib) to actually translate/compile/deploy and invoke
 *        the JSP referenced below -- there is no `main()` method here because the .jsp
 *        file is compiled and driven entirely by the CONTAINER's JSP engine, not by you.
 *
 * How you would actually run this, end to end:
 *   1. Create a standard Maven "war" packaged project:
 *        mvn archetype:generate -DarchetypeArtifactId=maven-archetype-webapp
 *   2. Add to pom.xml (provided scope for the servlet API; JSTL itself is typically a
 *      regular compile-scope dependency bundled into the WAR, unlike the servlet API):
 *        <dependency>
 *            <groupId>jakarta.servlet</groupId>
 *            <artifactId>jakarta.servlet-api</artifactId>
 *            <version>6.0.0</version>
 *            <scope>provided</scope>
 *        </dependency>
 *        <dependency>
 *            <groupId>jakarta.servlet.jsp.jstl</groupId>
 *            <artifactId>jakarta.servlet.jsp.jstl-api</artifactId>
 *            <version>3.0.0</version>
 *        </dependency>
 *        <dependency>
 *            <groupId>org.glassfish.web</groupId>
 *            <artifactId>jakarta.servlet.jsp.jstl</artifactId>
 *            <version>3.0.1</version>
 *        </dependency>
 *   3. Place the controller servlet class below under src/main/java/com/example/,
 *      and create a REAL productList.jsp file (using the syntax shown in the
 *      commented .jsp blocks below) under src/main/webapp/WEB-INF/views/.
 *   4. Build a WAR: mvn package
 *   5. Drop the resulting .war into Tomcat's webapps/ directory.
 *   6. Visit http://localhost:8080/<app-name>/products?category=electronics
 *      in a browser to trigger doGet -> forward -> JSTL/EL rendering.
 */

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


// ---------------------------------------------------------------------------
// 0) A trivial domain object used by the controller + the commented JSP below.
// ---------------------------------------------------------------------------
class ProductItem {
    private final String name;
    private final double price;

    ProductItem(String name, double price) {
        this.name = name;
        this.price = price;
    }

    public String getName() { return name; }     // EL's ${p.name} relies on this getter convention
    public double getPrice() { return price; }    // EL's ${p.price} calls this via getPrice()
}

/** Trivial stand-in for a real service/DAO layer -- kept out of the JSP entirely (MVC). */
class ProductService {
    List<ProductItem> findByCategory(String category) {
        List<ProductItem> results = new ArrayList<>();
        if ("electronics".equals(category)) {
            results.add(new ProductItem("USB Cable", 5.99));
            results.add(new ProductItem("Wireless Mouse", 129.50));
        } else {
            results.add(new ProductItem("Notebook", 2.49));
        }
        return results;
    }
}


// ---------------------------------------------------------------------------
// 1) The controller servlet -- ALL logic lives here (parsing params, calling
//    the "Model" service layer, choosing a view). The JSP it forwards to does
//    presentation ONLY, using JSTL + EL -- no business logic, no raw scriptlets.
// ---------------------------------------------------------------------------

/*
 * MVC REQUEST FLOW (Theory 04), reproduced here as a comment diagram:
 *
 *                      +-----------------------+
 *   HTTP Request --->  |  Servlet (Controller)  |
 *                      +-----------------------+
 *                               |
 *                      1. Parse request params
 *                      2. Call service/business logic (the "Model")
 *                      3. Put result data into request attributes
 *                      4. Forward to a JSP (never redirect for this step --
 *                         forwarding preserves the request attributes just set)
 *                               |
 *                               v
 *                      +-----------------------+
 *                      |     JSP (View)         |
 *                      |  JSTL + EL only --      |
 *                      |  no business logic      |
 *                      +-----------------------+
 *                               |
 *                               v
 *                         HTML Response
 */
@WebServlet("/products")
public class JstlExpressionLanguageDemo extends HttpServlet {

    private final ProductService productService = new ProductService(); // the "Model" layer

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String category = req.getParameter("category");

        // Business logic happens HERE, in the controller -- NOT in the JSP.
        List<ProductItem> products = productService.findByCategory(category);

        req.setAttribute("products", products);
        req.setAttribute("category", category);

        // Placing the view under WEB-INF/ means it is NOT directly web-accessible by URL --
        // the container blocks direct requests to anything under WEB-INF/ -- it can ONLY be
        // reached via this server-side forward(), so clients can never bypass the controller
        // and hit a "raw" view with no data ever set up.
        RequestDispatcher dispatcher = req.getRequestDispatcher("/WEB-INF/views/productList.jsp");
        dispatcher.forward(req, resp);
    }
}


/*
 * =============================================================================
 * THE ACTUAL productList.jsp FILE this servlet forwards to would look roughly
 * like the following. It is NOT valid Java and cannot live as executable code
 * in this .java file -- shown here as a documentation block so the JSTL/EL
 * syntax from Theory 04 is captured alongside its matching controller servlet.
 *
 * File: src/main/webapp/WEB-INF/views/productList.jsp
 * =============================================================================
 *
 * <%@ taglib prefix="c" uri="jakarta.tags.core" %>
 * <%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
 * <%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
 * <%-- (Older javax.servlet-era codebases use http://java.sun.com/jsp/jstl/core etc. instead --
 *      functionally identical, just the pre-Jakarta-rename namespace.) --%>
 *
 * <html>
 * <body>
 *
 * <h1>Products in ${category}</h1>
 * <%-- ^ EL: ${category} reads the "category" REQUEST attribute the servlet set via
 *      setAttribute() above -- EL's default scope search order is page, then request,
 *      then session, then application, using the FIRST match found. To be explicit and
 *      avoid ambiguity: ${requestScope.category}. --%>
 *
 * <p>You have ${fn:length(products)} product(s).</p>
 * <%-- ^ fn: JSTL functions library -- EL utility functions like fn:length, fn:toUpperCase,
 *      fn:contains -- used here instead of a scriptlet calling products.size(). --%>
 *
 * <c:choose>
 *     <%-- c:choose / c:when / c:otherwise -- the if/else-if/else equivalent --%>
 *     <c:when test="${empty products}">
 *         <%-- the `empty` operator -- true for null, empty String, empty Collection/Map/array --
 *              the idiomatic EL way to guard a loop in one check, avoiding both a
 *              NullPointerException-style issue AND an unnecessary empty <ul></ul> render. --%>
 *         <p>No products found.</p>
 *     </c:when>
 *     <c:otherwise>
 *         <table>
 *         <c:forEach var="p" items="${products}" varStatus="status">
 *             <%-- c:forEach -- loops over a Collection/array/Map. varStatus exposes loop
 *                  metadata (index 0-based, count 1-based, first/last booleans, current item)
 *                  without a manually maintained counter -- exactly the bookkeeping that used
 *                  to require a scriptlet. --%>
 *             <tr class="${status.index % 2 == 0 ? 'even' : 'odd'}">
 *                 <%-- ^ EL ternary operator, and arithmetic/modulo operators (+ - * / % or div/mod) --%>
 *                 <td>${status.count}</td>
 *                 <td><c:out value="${p.name}" /></td>
 *                 <%-- ^ c:out HTML-escapes its value by default (XSS protection) -- unlike
 *                      plain ${p.name} output, which does NOT escape. Use c:out (or an
 *                      equivalent escaping mechanism) for any value that could contain
 *                      user-supplied content, e.g. <script> tags submitted by a user. --%>
 *                 <td><fmt:formatNumber value="${p.price}" type="currency" /></td>
 *                 <%-- ^ fmt:formatNumber -- locale-aware currency formatting, e.g. "$129.50" --%>
 *
 *                 <c:if test="${p.price > 100}">
 *                     <%-- c:if -- single-branch conditional, no else. Comparison operators:
 *                          >, <, >=, <=, ==, != or their word forms gt, lt, ge, le, eq, ne. --%>
 *                     <td>(premium)</td>
 *                 </c:if>
 *             </tr>
 *         </c:forEach>
 *         </table>
 *     </c:otherwise>
 * </c:choose>
 *
 * <%-- c:set / c:remove -- declaring and removing scoped variables from within a JSP,
 *      without a scriptlet --%>
 * <c:set var="taxRate" value="0.08" scope="page" />
 * <p>Estimated tax rate: <fmt:formatNumber value="${taxRate}" type="percent" /></p>
 * <c:remove var="taxRate" />
 *
 * <%-- c:url -- builds a context-relative URL, handling session-URL-rewriting automatically --%>
 * <a href="<c:url value='/products'><c:param name='category' value='electronics' /></c:url>">
 *     Electronics
 * </a>
 *
 * <%-- EL implicit objects -- distinct from the JSP implicit objects (Theory 03), mostly maps
 *      giving scriptlet-free access to request data: --%>
 * <p>Searching for: ${param.query}</p>
 * <%-- ^ param      -- Map<String,String> of request parameters, equivalent to
 *                       request.getParameter("query") in Java --%>
 * <p>Your browser: ${header['User-Agent']}</p>
 * <%-- ^ header     -- Map<String,String> of request headers --%>
 * <%-- Other EL implicit objects: paramValues (String[] per param), cookie (Cookie per name,
 *      e.g. ${cookie.theme.value}), initParam (context init params), pageScope/requestScope/
 *      sessionScope/applicationScope (explicit scope maps), pageContext itself. --%>
 *
 * <fmt:bundle basename="messages">
 *     <fmt:message key="welcome.greeting" />
 *     <%-- ^ fmt:message -- internationalization: looks up a key in a resource bundle --%>
 * </fmt:bundle>
 *
 * </body>
 * </html>
 * =============================================================================
 */


/*
 * =============================================================================
 * JSTL TAG LIBRARY OVERVIEW (Theory 04) -- kept here as a quick-reference
 * comment block:
 *
 *   c    Core          Conditionals, loops, variable scoping, URL building --
 *                      by far the most used
 *   fmt  Formatting/i18n  Number/date formatting, locale-aware messages, resource bundles
 *   fn   Functions      EL utility functions -- fn:length, fn:toUpperCase, fn:contains
 *   sql  SQL            Direct DB access from JSP -- almost universally discouraged
 *                        today, mixing persistence into the view defeats MVC entirely;
 *                        mentioned only for legacy-code recognition -- NEVER use in new code
 *   x    XML            XPath-based XML processing within JSP -- rare in modern apps
 *
 * Common gotchas:
 *   - Plain ${...} output is NOT HTML-escaped; <c:out> IS -- use c:out for anything
 *     that could contain user-supplied content, to avoid XSS.
 *   - EL's scope-search order (page -> request -> session -> application) can silently
 *     pick up a STALE session-scoped attribute shadowing a fresh request-scoped one with
 *     the same name -- be explicit (${requestScope.x}) whenever that ambiguity is possible.
 *   - Forgetting the <%@ taglib %> directive for a prefix used in the page causes a
 *     translation-time error -- easy to forget when copy-pasting JSP snippets.
 * =============================================================================
 */
