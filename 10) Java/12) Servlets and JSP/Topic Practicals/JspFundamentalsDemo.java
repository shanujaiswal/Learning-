/**
 * JspFundamentalsDemo.java
 *
 * Demonstrates, with heavy comments explaining WHAT would happen at runtime
 * inside a real servlet container:
 *     1. JSP's core fact: a .jsp file compiles INTO a servlet -- it is not a
 *        separate technology, just a more convenient way to author certain servlets
 *     2. The JSP lifecycle (translation -> compilation -> jspInit -> _jspService -> jspDestroy)
 *     3. A controller servlet forwarding to a JSP view (RequestDispatcher.forward)
 *     4. JSP scriptlet/expression/declaration syntax, shown as commented .jsp-code
 *        blocks (since a real .jsp file is not plain Java and cannot live in this .java file)
 *     5. JSP directives (page/include/taglib), also shown as commented .jsp blocks
 *     6. JSP implicit objects (request/response/session/application/out/...), explained via comments
 *
 * Covers Theory chapter:
 *     10) Java/12) Servlets and JSP/Theory/03 JSP Fundamentals.md
 *
 * IMPORTANT -- this file will NOT compile or run standalone with plain `javac`/`java`.
 * It requires:
 *     1. The Jakarta Servlet API (and, conceptually, a JSP engine such as Jasper,
 *        which ships bundled inside Tomcat) on the classpath -- PROVIDED scope,
 *        the container supplies the real implementation and the JSP compiler at
 *        runtime, so you never bundle either yourself in the deployed WAR.
 *     2. A running servlet container with JSP support (e.g. Apache Tomcat, Jetty)
 *        to actually translate/compile/deploy and invoke the JSP referenced below --
 *        there is no `main()` method here because the .jsp file is compiled and
 *        driven entirely by the CONTAINER's JSP engine, not by you.
 *
 * How you would actually run this, end to end:
 *   1. Create a standard Maven "war" packaged project:
 *        mvn archetype:generate -DarchetypeArtifactId=maven-archetype-webapp
 *   2. Add to pom.xml (provided scope -- Tomcat supplies the real jars at runtime):
 *        <dependency>
 *            <groupId>jakarta.servlet</groupId>
 *            <artifactId>jakarta.servlet-api</artifactId>
 *            <version>6.0.0</version>
 *            <scope>provided</scope>
 *        </dependency>
 *   3. Place the controller servlet class below under src/main/java/com/example/,
 *      and create a REAL productDetail.jsp file (using the syntax shown in the
 *      commented .jsp blocks below) under src/main/webapp/WEB-INF/views/.
 *   4. Build a WAR: mvn package
 *   5. Drop the resulting .war into Tomcat's webapps/ directory.
 *   6. Visit http://localhost:8080/<app-name>/productDetail in a browser -- the
 *      FIRST request triggers JSP translation + compilation (noticeably slower);
 *      subsequent requests reuse the already-compiled generated servlet class.
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
// 0) A trivial domain object used by the controller + the commented JSP below,
//    purely so the illustrative EL/scriptlet snippets have something concrete
//    to reference (p.getName(), p.getPrice(), etc.).
// ---------------------------------------------------------------------------
class Product {
    private final String name;
    private final double price;

    Product(String name, double price) {
        this.name = name;
        this.price = price;
    }

    public String getName() { return name; }
    public double getPrice() { return price; }
}


// ---------------------------------------------------------------------------
// 1) The controller servlet -- Model 2 MVC. All Java logic lives HERE, not in
//    the JSP. It prepares data as request attributes, then forwards to a JSP
//    which does presentation ONLY. This is the servlet half of the pairing;
//    the JSP half is shown entirely as commented code further down, since a
//    real .jsp file is not Java source and cannot compile inside this file.
// ---------------------------------------------------------------------------

/**
 * ---------------------------------------------------------------------------
 * CRITICAL FACT (Theory 03): JSP is NOT a separate technology from servlets --
 * it compiles INTO one. The very first time productDetail.jsp is requested (or
 * at deployment time, if precompiled), the container's JSP engine translates
 * it into an ordinary .java source file implementing HttpJspPage (which itself
 * extends HttpServlet), compiles that into a .class file, loads it, and
 * thereafter serves requests through that generated servlet exactly like any
 * other -- everything about the servlet lifecycle from Theory 01 applies
 * underneath a JSP too.
 *
 *     productDetail.jsp --[JSP engine translates]--> productDetail_jsp.java
 *                        --[javac compiles]--> productDetail_jsp.class
 * ---------------------------------------------------------------------------
 */
@WebServlet("/productDetail")
public class JspFundamentalsDemo extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        // 1. Parse request params (none needed here, kept simple for illustration)
        // 2. Call business/service logic (the "Model") -- normally a service/DAO layer
        List<Product> products = new ArrayList<>();
        products.add(new Product("Widget", 9.99));
        products.add(new Product("Gadget", 24.99));

        // 3. Put result data into request attributes -- visible to the JSP we forward to,
        //    because forward() carries the SAME HttpServletRequest object through, unlike
        //    sendRedirect() which would start a brand-new request with none of this data.
        req.setAttribute("products", products);
        req.setAttribute("pageTitle", "Product Catalog");

        // 4. Forward to a JSP to render the view. Placing the JSP under WEB-INF/ means it is
        //    NOT directly web-accessible by URL -- clients can only reach it via this forward(),
        //    never by requesting productDetail.jsp directly, which would otherwise render a
        //    broken page with no data ever set up.
        RequestDispatcher dispatcher = req.getRequestDispatcher("/WEB-INF/views/productDetail.jsp");
        dispatcher.forward(req, resp);
        // NOTE: nothing should be written to `resp` before this forward() call -- once the
        // response is committed, forward() would throw IllegalStateException.
    }
}


/*
 * =============================================================================
 * THE JSP LIFECYCLE (Theory 03) -- shown here as a comment block since it is
 * a conceptual/textual diagram, not executable Java:
 * =============================================================================
 *
 * 1. TRANSLATION (first request, or precompiled at build/deploy time)
 *    JSP engine parses the .jsp file and generates equivalent Java servlet
 *    source code (implementing HttpJspPage, which extends HttpServlet).
 *         |
 *         v
 * 2. COMPILATION
 *    The generated .java source is compiled into a .class file, exactly
 *    like any other servlet.
 *         |
 *         v
 * 3. INSTANTIATION + jspInit()
 *    Container instantiates the generated servlet class (once) and calls
 *    its jspInit() method (the JSP equivalent of a servlet's init()) --
 *    override with a <%! ... %> declaration if custom init logic is needed.
 *         |
 *         v
 * 4. _jspService(request, response)
 *    Called ONCE PER REQUEST -- the translated HTML + scriptlets + expressions
 *    actually execute here. You never write this method yourself -- the JSP
 *    engine generates it FOR you. Unlike HttpServlet, this ONE method handles
 *    ALL HTTP verbs by default -- JSPs aren't naturally split into doGet/doPost.
 *         |
 *         v
 * 5. jspDestroy()
 *    Called ONCE, on container shutdown/undeploy -- cleanup, like destroy().
 *
 * Why the FIRST request to a JSP is often noticeably slower: unless the
 * container precompiles JSPs at deployment time, translation + compilation
 * happens lazily on that first request, adding real latency. Production
 * deployments commonly precompile JSPs specifically to avoid this penalty.
 * =============================================================================
 */


/*
 * =============================================================================
 * THE ACTUAL productDetail.jsp FILE this servlet forwards to would look
 * roughly like the following. It is NOT valid Java and cannot live as
 * executable code in this .java file -- shown here as a documentation block
 * so the scriptlet/expression/declaration/directive/implicit-object syntax
 * from Theory 03 is captured alongside its matching controller servlet above.
 *
 * File: src/main/webapp/WEB-INF/views/productDetail.jsp
 * =============================================================================
 *
 * ---- JSP DIRECTIVES (<%@ ... %>) -- instructions to the JSP engine about
 *      how to translate THIS PAGE, not runtime behavior ----
 *
 * <%@ page contentType="text/html;charset=UTF-8" language="java"
 *          import="java.util.*"
 *          isErrorPage="false" errorPage="/error.jsp"
 *          session="true" %>
 * <%-- page directive: content type/charset, imports used by scriptlets/expressions
 *      below, error-page wiring, and whether a session is auto-created for this page --%>
 *
 * <%@ include file="header.jsp" %>
 * <%-- include directive: STATIC, translation-time merge of header.jsp's content into
 *      THIS page's source before compilation -- fast, but requires recompiling this
 *      page if header.jsp changes. (Contrast with the <jsp:include> ACTION, which is
 *      a runtime include re-invoked on every request -- not shown here, covered in
 *      Theory 03's gotchas.) --%>
 *
 * <%@ taglib prefix="c" uri="jakarta.tags.core" %>
 * <%-- taglib directive: declares a tag library (JSTL here) and binds it to a prefix
 *      used throughout the page -- fully explored in Theory 04 / JstlExpressionLanguageDemo.java --%>
 *
 * <html>
 * <body>
 *
 * <h1><%= request.getAttribute("pageTitle") %></h1>
 * <%-- ^ EXPRESSION <%= ... %>: a Java EXPRESSION (no semicolon, must evaluate to a
 *      value), converted to String and written directly into the response body.
 *      Translates to roughly: out.print( request.getAttribute("pageTitle") ); --%>
 *
 * <%
 *     // ^ SCRIPTLET <% ... %>: raw Java code copied nearly verbatim into the
 *     // generated _jspService() method body. Because it lands inside
 *     // _jspService(), it has automatic access to all JSP IMPLICIT OBJECTS
 *     // below (request, response, session, etc.) as if they were local
 *     // variables -- because in the generated code, they actually are.
 *     List<Product> products = (List<Product>) request.getAttribute("products");
 *     double total = 0;
 *     for (Product p : products) {
 *         total += p.getPrice();
 *     }
 * %>
 *
 * <ul>
 * <%
 *     for (Product p : products) {
 * %>
 *     <li><%= p.getName() %> - $<%= p.getPrice() %></li>
 * <%
 *     }
 * %>
 * </ul>
 * <p>Total: $<%= total %></p>
 *
 * <%!
 *     // ^ DECLARATION <%! ... %>: declares a MEMBER (field or method) of the
 *     // GENERATED SERVLET CLASS ITSELF, OUTSIDE of _jspService(). This is the
 *     // key difference from a scriptlet, and it has real consequences:
 *     //
 *     // GOTCHA: declarations create SHARED, MUTABLE INSTANCE STATE, exactly
 *     // like an instance field on a hand-written servlet (Theory 01/05) --
 *     // pageHitCount below is shared across EVERY CONCURRENT request to this
 *     // JSP and is NOT thread-safe, for exactly the same reason a raw
 *     // servlet's instance fields aren't. Easy trap: declarations look
 *     // almost identical to scriptlets syntactically (just one extra "!").
 *     private int pageHitCount = 0;
 *
 *     private String formatPrice(double price) {
 *         return String.format("$%.2f", price);
 *     }
 * %>
 * <p>This page has been hit <%= ++pageHitCount %> times (shared, unsafe counter -- illustrative only)</p>
 * <p>Formatted total: <%= formatPrice(total) %></p>
 *
 * <%--
 *     Summary of the three tag syntaxes and where each lands in the
 *     generated servlet class:
 *
 *     <%  ... %>   Scriptlet    --> goes INSIDE _jspService() -- runs per-request, local scope
 *     <%= ... %>   Expression   --> shorthand for out.print(...) inside _jspService()
 *     <%! ... %>   Declaration  --> goes OUTSIDE _jspService() -- becomes a field/method on
 *                                   the whole generated servlet class -- SHARED across requests!
 * --%>
 *
 * </body>
 * </html>
 * =============================================================================
 */


/*
 * =============================================================================
 * JSP IMPLICIT OBJECTS (Theory 03) -- because a JSP compiles into a servlet,
 * the JSP engine automatically declares these variables INSIDE the generated
 * _jspService() method -- usable directly in scriptlets/expressions with no
 * declaration or import needed, because the generated code already has them
 * in scope. Explained here via comments since they only exist inside a real
 * translated JSP, not in this standalone .java file:
 *
 *   request      HttpServletRequest  - the current request -- parameters, attributes, headers
 *   response     HttpServletResponse - the current response
 *   session      HttpSession         - the current session (unless page session="false")
 *   application  ServletContext      - application-wide shared object, one per deployed web app
 *   out          JspWriter           - the output stream <%= %> writes to
 *   config       ServletConfig       - this JSP's servlet configuration (init params, etc.)
 *   pageContext  PageContext         - unified access to all four scopes (page/request/session/application)
 *   page         Object (~= this)    - the generated servlet instance itself -- rarely used directly
 *   exception    Throwable           - only on pages marked isErrorPage="true" -- the exception
 *                                      that triggered the error forward
 *
 * The four scopes and how long each one's data lives:
 *   page        -- this one JSP's processing of this one request only -- not preserved across a forward
 *   request     -- one HTTP request, including any internal forwards within it (Theory 02)
 *   session     -- across many requests from the same client, until timeout/invalidation (Theory 02)
 *   application -- the entire lifetime of the deployed web application, shared by ALL users
 * =============================================================================
 */
