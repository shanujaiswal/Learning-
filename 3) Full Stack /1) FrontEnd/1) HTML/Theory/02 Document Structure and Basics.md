# HTML Document Structure

--> HTML (HyperText Markup Language) describes the structure of a web page using elements (tags).
--> Every HTML document starts with `<!DOCTYPE html>` — tells the browser to render the page in standards mode (HTML5).
--> The root element is `<html>`, which wraps `<head>` and `<body>`.

```html
<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="UTF-8">
    <title>Page Title</title>
  </head>
  <body>
    <!-- visible content goes here -->
  </body>
</html>
```

# The head Section

--> `<head>` contains metadata — information about the page that isn't shown directly.
--> `<meta charset="UTF-8">` sets the character encoding (supports almost all characters/symbols).
--> Always declare `<meta charset="UTF-8">` as the very first thing inside `<head>` — before `<title>` or other head content — otherwise the browser may mis-render special characters (encoding pitfall).
--> `<meta name="viewport" content="width=device-width, initial-scale=1.0">` makes the page responsive on mobile devices.
--> `<title>` sets the text shown in the browser tab.
--> `<link>` connects external resources like stylesheets (`rel="stylesheet"`).
--> `<style>` allows internal CSS directly inside the document.
--> `<script>` embeds or links JavaScript.

# Elements and Tags

--> An HTML element usually consists of a start tag, content, and an end tag: `<tagname>content</tagname>`.
--> Some elements are self-closing / empty (no content, no end tag) — e.g. `<br>`, `<img>`, `<input>`, `<hr>`.
--> Tags are not case sensitive, but lowercase is the convention/standard.

# Nested Elements

--> When an HTML element is used inside another element it is called a nested element.
--> Example: the `<html>` tag contains `<head>` and `<body>` tags, forming a nested structure.
--> Elements must be properly nested — an inner tag must close before its outer tag closes.

# Comments

--> `<!-- comment text -->` — not rendered on the page, used for notes/documentation in the code.

# Whitespace and Formatting

--> HTML collapses multiple spaces/line breaks in the source into a single space when displayed.
--> Shift + Alt + F — code beautifier / auto-formats code in most editors (VS Code).

# Common Shortcuts / Symbols

--> `&lt;` → `<`
--> `&gt;` → `>`
--> `&amp;` → `&`
--> `&nbsp;` → non-breaking space
--> `!` (in Emmet) → generates a full HTML boilerplate
--> `../` → used in paths to go up one directory level to reach a file's location

# The main Element

--> `<main>` represents the main/dominant content of the `<body>` of a document.
--> There should only be one visible `<main>` per page.

# Empty Elements

--> Elements with no content that do not print anything are called empty elements.
--> They do not have an ending/closing tag (e.g. `<br>`, `<img>`, `<input>`, `<meta>`, `<link>`).

# lang Attribute Variants

--> `lang="en"` — generic English content.
--> `lang="en-US"` / `lang="en-GB"` — region-specific variant (affects spellcheck, date formats, screen reader pronunciation).
--> Set on `<html>` for the whole page, or on a specific element to override for a section in a different language.

# Document Outline / Validation Tools

--> The W3C Markup Validation Service (validator.w3.org) checks HTML for errors and standards compliance — good habit to run pages through it.

# Quirks Mode vs Standards Mode

--> Standards Mode — the browser renders the page strictly according to the modern HTML/CSS specifications. Triggered by a correct `<!DOCTYPE html>` declaration at the very top of the document.
--> Quirks Mode — a legacy rendering mode that mimics old, non-standard browser behavior (from pre-HTML5 days) for backward compatibility with very old pages.
--> A missing or malformed `<!DOCTYPE html>` (or an old-style doctype) can silently drop the page into Quirks Mode, causing unexpected box-model/CSS sizing bugs.
--> Always include the simple HTML5 doctype (`<!DOCTYPE html>`) to guarantee Standards Mode.

# The base Element

--> `<base href="url" target="_blank">` — sets a default base URL and/or default target for ALL relative links and `<a>`/`<img>`/`<form>` references on the page.
--> Must be placed inside `<head>`, and only ONE `<base>` element is allowed per document.
--> Useful when a page's relative links should all resolve against a different root URL than the page's own location.

```html
<head>
  <base href="https://example.com/assets/">
  <!-- <img src="logo.png"> now resolves to https://example.com/assets/logo.png -->
</head>
```

# The noscript Element

--> `<noscript>` — defines content to display ONLY if the browser has JavaScript disabled or does not support it.
--> Commonly used to show a fallback message telling the user to enable JavaScript.

```html
<noscript>
  <p>Please enable JavaScript to use this website.</p>
</noscript>
```

# Block-Level vs Inline Elements

--> Block-level elements always start on a new line and take up the full available width by default (e.g. `<div>`, `<p>`, `<h1>`–`<h6>`, `<ul>`, `<section>`, `<article>`, `<form>`).
--> Inline elements do not start on a new line and only take up as much width as their content needs (e.g. `<span>`, `<a>`, `<strong>`, `<em>`, `<img>`, `<label>`).
--> Block-level elements can contain inline elements and (usually) other block-level elements; inline elements should generally only contain other inline elements or text.
--> This is the default rendering behavior — CSS `display` property (`block`, `inline`, `inline-block`, etc.) can override it for any element.

# How the Internet Works (Client-Server Model)

--> Before writing HTML that a browser will actually load over a network, it helps to know what happens between typing a URL and seeing a page.
--> Client-Server Model — the browser (client) sends a request for a resource; a server receives it, processes it, and sends back a response (usually HTML/CSS/JS/data). The client renders whatever comes back.
--> DNS (Domain Name System) — translates a human-readable domain name (e.g. `example.com`) into the numeric IP address of the server that actually hosts the site; happens automatically before the browser can even connect.
--> HTTP (HyperText Transfer Protocol) — the protocol/rules the browser and server use to communicate (request methods like GET/POST, status codes like 200/404/500, headers).
--> HTTPS — HTTP encrypted with TLS/SSL; encrypts data in transit so it can't be read or tampered with by anyone intercepting the connection. Modern browsers mark plain HTTP sites as "Not Secure."
--> Basic request/response flow: Browser looks up the domain via DNS -> opens a connection to the server's IP -> sends an HTTP(S) request -> server sends back an HTTP(S) response (status code + headers + body, e.g. an HTML document) -> browser parses and renders it.
