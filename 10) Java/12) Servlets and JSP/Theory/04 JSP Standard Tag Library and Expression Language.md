# The Problem: Getting Java Logic Out of JSP Pages

--> Theory 03 ended on Model 2 (MVC): servlets as controllers, JSPs as views. But even a "view-only" JSP still needs SOME logic -- looping over a list, conditionally showing something, formatting a number -- and writing that with raw scriptlets (`<% %>`) still tangles Java syntax into what should be presentation markup, and scriptlet code isn't checked by any HTML-aware tool, doesn't get IDE support the way tags do, and is easy to write incorrectly (unclosed braces spanning multiple `<% %>` blocks are a classic JSP bug).
--> **JSTL (JSP Standard Tag Library)** and **EL (Expression Language)** together solve this: EL gives you a lightweight, HTML-attribute-friendly syntax for reading data (`${...}`), and JSTL gives you tag-based control structures (loops, conditionals, formatting) that LOOK like HTML/XML tags instead of Java code. Combined, a well-written modern JSP view can avoid scriptlets almost entirely.

```jsp
<%-- Before: scriptlet-heavy, Java tangled into markup --%>
<ul>
<%
    List<Product> products = (List<Product>) request.getAttribute("products");
    for (Product p : products) {
        if (p.getPrice() > 100) {
%>
    <li><%= p.getName() %> - $<%= p.getPrice() %> (premium)</li>
<%
        }
    }
%>
</ul>

<%-- After: JSTL + EL, reads like markup, no Java syntax at all --%>
<ul>
<c:forEach var="p" items="${products}">
    <c:if test="${p.price > 100}">
        <li>${p.name} - $${p.price} (premium)</li>
    </c:if>
</c:forEach>
</ul>
```

# Expression Language (EL)

--> EL is the `${...}` syntax used to READ data (from any of the four scopes covered in Theory 03: page/request/session/application) directly inside JSP markup, without any scriptlet Java code. It was introduced in JSP 2.0 specifically to reduce reliance on `<%= %>` expressions.

## Basic EL Syntax

```jsp
${expression}
```

```jsp
<p>Welcome, ${username}!</p>            <%-- reads an attribute named "username" from some scope --%>
<p>Total: ${order.total}</p>             <%-- calls order.getTotal() -- EL uses getter convention --%>
<p>First item: ${items[0]}</p>            <%-- array/List index access --%>
<p>Config value: ${settings['maxSize']}</p> <%-- Map access, or use dot notation: settings.maxSize --%>
```

--> **Scope resolution order** -- when you write `${username}` without specifying a scope, EL searches page scope, then request scope, then session scope, then application scope, in that order, and uses the FIRST match found. To be explicit and avoid ambiguity (e.g. two different scopes happen to have an attribute with the same name), you can qualify it: `${requestScope.username}`, `${sessionScope.username}`, `${applicationScope.username}`.
--> **Property access uses getter convention, not field names** -- `${order.total}` under the hood calls `order.getTotal()` (or `order.isTotal()` for a `boolean`); it works even though `total` isn't necessarily a public field, because EL relies on the JavaBean getter naming convention, not direct field access.

## EL Operators

```jsp
${user.age >= 18}              <%-- comparison: also eq, ne, lt, gt, le, ge as word alternatives --%>
${cartTotal > 0 && !isGuest}    <%-- logical: && and || work; and/or/not word forms also valid --%>
${price * quantity}             <%-- arithmetic: + - * / % (or div, mod) --%>
${user != null ? user.name : "Guest"}   <%-- ternary conditional --%>
${empty list}                    <%-- 'empty' operator -- true for null, empty String, empty
                                       Collection/Map/array -- extremely common in <c:if> guards --%>
```

--> **The `empty` operator deserves special attention** -- it's the idiomatic EL way to guard against null-or-empty in one check, commonly seen wrapping loops: `<c:if test="${not empty products}"> ... </c:if>` avoids both a `NullPointerException`-style issue AND an unnecessary empty `<ul></ul>` render.

## EL Implicit Objects

--> EL has its OWN set of implicit objects, distinct from (though overlapping in purpose with) the JSP implicit objects from Theory 03 -- most are maps that let you access request parameters, headers, cookies, and init parameters without Java code at all.

| EL implicit object | Purpose |
|---|---|
| `pageScope`, `requestScope`, `sessionScope`, `applicationScope` | Explicit scope maps (as shown above) |
| `param` | Map of request parameter names to their single `String` value -- `${param.username}` |
| `paramValues` | Map of request parameter names to `String[]` (for multi-valued parameters) |
| `header` | Map of request header names to values -- `${header['User-Agent']}` |
| `cookie` | Map of cookie names to `Cookie` objects -- `${cookie.theme.value}` |
| `initParam` | Map of context (application-wide) init parameters |
| `pageContext` | The `PageContext` object itself -- gives access to `request`, `session`, etc. as needed |

```jsp
<p>Searching for: ${param.query}</p>        <%-- equivalent to request.getParameter("query") in Java --%>
<p>Your browser: ${header['User-Agent']}</p>
```

# JSTL -- JSP Standard Tag Library

--> JSTL is a standard set of custom tags (installed as a library, referenced via the `taglib` directive from Theory 03) providing loop/conditional/formatting constructs as TAGS rather than Java scriptlets. It's organized into several tag libraries by purpose, of which the **core** library is by far the most commonly used.

```jsp
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
```

--> (Note: the `uri` values shown use the modern Jakarta EE 9+ namespace; older codebases on `javax.servlet` will instead use URIs like `http://java.sun.com/jsp/jstl/core` -- functionally identical, just the pre-rename namespace.)

## Core Tags (`c:` prefix)

```jsp
<%-- c:out -- writes a value, HTML-escaping it by default (XSS protection) --%>
<c:out value="${comment.text}" />
<%-- equivalent to ${comment.text}, but c:out escapes <script> etc. into &lt;script&gt; by default,
     whereas plain ${...} output does NOT escape -- an important security distinction (see gotchas) --%>

<%-- c:if -- single-branch conditional, no else --%>
<c:if test="${cart.itemCount > 0}">
    <p>You have ${cart.itemCount} items in your cart.</p>
</c:if>

<%-- c:choose / c:when / c:otherwise -- the if/else-if/else equivalent --%>
<c:choose>
    <c:when test="${user.role == 'ADMIN'}">
        <p>Welcome, administrator.</p>
    </c:when>
    <c:when test="${user.role == 'EDITOR'}">
        <p>Welcome, editor.</p>
    </c:when>
    <c:otherwise>
        <p>Welcome, guest.</p>
    </c:otherwise>
</c:choose>

<%-- c:forEach -- looping over a Collection, array, or Map --%>
<table>
<c:forEach var="product" items="${products}" varStatus="status">
    <tr class="${status.index % 2 == 0 ? 'even' : 'odd'}">
        <td>${status.count}</td>       <%-- status.count is 1-based; status.index is 0-based --%>
        <td>${product.name}</td>
        <td>${product.price}</td>
    </tr>
</c:forEach>
</table>

<%-- c:forEach with begin/end -- fixed-count loop, no collection needed --%>
<c:forEach var="i" begin="1" end="5">
    Page ${i}
</c:forEach>

<%-- c:set / c:remove -- declaring and removing scoped variables from within a JSP --%>
<c:set var="taxRate" value="0.08" scope="page" />
<c:remove var="taxRate" />

<%-- c:url -- builds a context-relative URL, handling session-URL-rewriting automatically if needed --%>
<a href="<c:url value='/products'><c:param name='category' value='electronics' /></c:url>">Electronics</a>
```

--> **`varStatus` deserves attention** -- it exposes loop metadata (`index` 0-based, `count` 1-based, `first`/`last` booleans, `current` the current item) without you needing a manually maintained counter variable, which is exactly the kind of bookkeeping that used to require a scriptlet.

## Formatting Tags (`fmt:` prefix)

```jsp
<%-- fmt:formatNumber -- locale-aware number/currency formatting --%>
<fmt:formatNumber value="${product.price}" type="currency" />        <%-- $19.99 --%>
<fmt:formatNumber value="${ratio}" type="percent" maxFractionDigits="1" />  <%-- 42.5% --%>

<%-- fmt:formatDate -- locale-aware date/time formatting --%>
<fmt:formatDate value="${order.createdAt}" pattern="yyyy-MM-dd HH:mm" />

<%-- fmt:message -- internationalization: looks up a key in a resource bundle --%>
<fmt:bundle basename="messages">
    <fmt:message key="welcome.greeting" />
</fmt:bundle>
```

## JSTL Tag Library Overview

| Prefix (conventional) | Library | Purpose |
|---|---|---|
| `c` | Core | Conditionals, loops, variable scoping, URL building -- by far the most used |
| `fmt` | Formatting/i18n | Number/date formatting, locale-aware messages, resource bundles |
| `fn` | Functions | EL utility functions -- `${fn:length(list)}`, `${fn:toUpperCase(str)}`, `${fn:contains(str, sub)}` |
| `sql` | SQL | Direct database access from JSP (`<sql:query>`) -- almost universally discouraged today, mixing persistence into the view layer defeats MVC entirely; mentioned only for legacy-code recognition |
| `x` | XML | XPath-based XML processing within JSP -- rare in modern applications |

```jsp
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<p>You have ${fn:length(products)} products.</p>
<p>${fn:toUpperCase(user.name)}</p>
```

# MVC Pattern with JSP as the View

--> Pulling Theory 01-04 together: a well-structured servlet+JSP application follows **Model 2 MVC**, where responsibilities are cleanly split across three kinds of components.

```text
                     +-------------------+
   HTTP Request ---> |  Servlet (Controller) |
                     +-------------------+
                              |
                     1. Parse request params
                     2. Call service/business logic (the "Model")
                     3. Put result data into request attributes
                     4. Forward to a JSP (never redirect for this step --
                        forwarding preserves the request attributes just set)
                              |
                              v
                     +-------------------+
                     |    JSP (View)      |
                     |  JSTL + EL only --  |
                     |  no business logic  |
                     +-------------------+
                              |
                              v
                        HTML Response
```

```java
// Controller -- a servlet. All logic lives here.
@WebServlet("/products")
public class ProductListServlet extends HttpServlet {
    private final ProductService productService = new ProductService(); // the "Model" layer

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String category = req.getParameter("category");
        List<Product> products = productService.findByCategory(category); // business logic, NOT in the JSP
        req.setAttribute("products", products);
        req.setAttribute("category", category);
        req.getRequestDispatcher("/WEB-INF/views/productList.jsp").forward(req, resp);
    }
}
```

```jsp
<%-- View -- productList.jsp. Presentation only, via JSTL + EL. --%>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<html>
<body>
<h1>Products in ${category}</h1>
<c:choose>
    <c:when test="${empty products}">
        <p>No products found.</p>
    </c:when>
    <c:otherwise>
        <ul>
        <c:forEach var="p" items="${products}">
            <li>${p.name} -- <fmt:formatNumber value="${p.price}" type="currency" /></li>
        </c:forEach>
        </ul>
    </c:otherwise>
</c:choose>
</body>
</html>
```

--> **Why JSPs are placed under `WEB-INF/`** -- resources under `WEB-INF/` are NOT directly web-accessible by URL (the container blocks direct requests to anything in that directory) -- they can ONLY be reached via a server-side `forward()`. Placing view JSPs there enforces that clients can never bypass the controller servlet and hit a "raw" view directly (e.g. requesting `productList.jsp` with no data ever set up), which would otherwise render a broken or empty page.
--> **Where this pattern historically led** -- this exact pattern (a "front controller" servlet or dispatching layer, routing to logic, then to a view) is precisely what Spring MVC's `DispatcherServlet` formalizes and automates: instead of hand-writing a servlet per resource and manually calling `getRequestDispatcher(...).forward(...)`, Spring MVC gives you `@Controller` classes, `@GetMapping`, and view resolution configured once, centrally. If you've done the Spring/Spring Boot chapters already, this should feel very familiar -- it's the same pattern, formalized and automated. Theory 05 discusses this evolution further.

# Common Gotchas

--> **`${...}` output vs `<c:out>` -- HTML escaping** -- by default, plain EL output (`${comment.text}`) does NOT HTML-escape its result, while `<c:out value="${comment.text}" />` DOES (escaping `<`, `>`, `&`, `"` into HTML entities). Rendering user-supplied content directly via `${...}` without escaping is a genuine XSS vulnerability if that content can contain `<script>` tags. Use `<c:out>` (or an escaping mechanism in whatever your container/JSP version defaults to) for any value that ultimately originated from user input.
--> **Confusing EL scope-search order with an explicit scope** -- `${username}` silently picks up whichever scope has it FIRST (page, then request, then session, then application) -- if a bug is caused by a STALE session-scoped attribute shadowing a fresh request-scoped one with the same name, it can be very confusing to debug without knowing this search order.
--> **Forgetting the `taglib` directive** -- using `<c:forEach>` etc. without the corresponding `<%@ taglib prefix="c" uri="..." %>` directive at the top of the file causes a translation-time error (the JSP engine doesn't recognize the tag) -- an easy thing to forget when copy-pasting JSP snippets between files.
--> **Mixing JSTL/EL with heavy scriptlets in the same page** -- technically legal, but if you're reaching for scriptlets alongside JSTL, it's often a sign the logic belongs in the controller servlet (or a helper/service class) instead of the view.

# Best Practices Summary

--> **Avoid scriptlets almost entirely** in views -- JSTL (`c:`, `fmt:`, `fn:`) plus EL cover the overwhelming majority of what a presentation-only view needs.
--> **Use `<c:out>` (or equivalent escaping)** for any value that could contain user-supplied content, to prevent XSS.
--> **Never use the `sql:` tag library** in real applications -- it violates MVC by putting persistence logic in the view; always query through a service/DAO layer in a servlet or Java class instead.
--> **Place view JSPs under `WEB-INF/`** so they can only be reached via a controller's `forward()`, never requested directly.
--> **Keep the controller servlet responsible for ALL logic** (parsing input, calling services, choosing a view) -- the JSP should only read data via EL/JSTL and render it.
--> **Be explicit about EL scope (`requestScope.x` vs `sessionScope.x`)** whenever a name could plausibly exist in more than one scope, to avoid subtle scope-shadowing bugs.
