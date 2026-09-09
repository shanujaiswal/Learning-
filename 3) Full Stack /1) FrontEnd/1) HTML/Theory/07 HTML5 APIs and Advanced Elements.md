# Semantic Structural Layout (Full Page Example)

```html
<body>
  <header>...</header>
  <nav>...</nav>
  <main>
    <section>...</section>
    <article>...</article>
    <aside>...</aside>
  </main>
  <footer>...</footer>
</body>
```

# Local Storage and Session Storage (HTML5 Web Storage API)

--> `localStorage` — stores data in the browser with no expiration date (persists across sessions).
--> `sessionStorage` — stores data only for the duration of the page session (cleared when the tab closes).
--> Accessed via JavaScript: `localStorage.setItem(key, value)`, `localStorage.getItem(key)`.
--> Data is stored as strings only — objects need `JSON.stringify()` / `JSON.parse()`.

# Canvas and SVG

--> `<canvas>` — a blank rectangular area for drawing graphics via JavaScript (bitmap-based).
--> `<svg>` — draws vector-based graphics directly in markup (scales without quality loss).

# Drag and Drop API

--> `draggable="true"` — makes an element draggable.
--> Events: `dragstart`, `dragover`, `drop` — handled via JavaScript to implement drag-and-drop interactions.

# Geolocation API

--> `navigator.geolocation.getCurrentPosition()` — retrieves the user's current location (requires permission).
--> Modern browsers require HTTPS (a secure context) for the Geolocation API to work — it will fail/be blocked on plain HTTP.

# Details and Summary (native accordion)

--> `<details>` — a disclosure widget that can be toggled open/closed.
--> `<summary>` — the always-visible heading for a `<details>` element; clicking it toggles the content.

```html
<details>
  <summary>Click to expand</summary>
  <p>Hidden content shown when opened.</p>
</details>
```

# Related APIs Often Paired with HTML5

--> History API (`history.pushState()`, `history.replaceState()`) — updates the URL/browser history without a full page reload, commonly used with HTML5 semantic pages for SPA-style navigation.
--> Fetch API (`fetch(url)`) — modern way to make HTTP requests from JavaScript, often used to populate HTML5 elements (e.g. filling a `<template>` or updating an `aria-live` region) with data.

# Dialog Element

--> `<dialog>` — a native modal/non-modal dialog box.
--> `.showModal()` / `.close()` — JavaScript methods to open/close it.

# Template Element

--> `<template>` — holds HTML markup that is not rendered immediately but can be cloned and inserted via JavaScript.

# Progress and Meter

--> `<progress value="70" max="100">` — shows a task's completion progress.
--> `<meter value="0.6">` — represents a scalar value within a known range (e.g. disk usage).

# Responsive Images

--> `<picture>` + `<source media="...">` + `<img>` — serves different images based on screen size/resolution.
--> `srcset` attribute on `<img>` — provides multiple image resolutions for the browser to choose from.

# Web Components (brief)

--> Custom Elements, Shadow DOM, and `<template>` together allow creating reusable, encapsulated custom HTML tags.

# Service Workers and PWA Basics

--> A Service Worker is a JavaScript file that runs separately from the page, in the background, acting as a network proxy between the browser and the internet.
--> Enables offline support (caching pages/assets so the app still works without a connection), push notifications, and background sync.
--> Registered from the page's main script:

```javascript
if ("serviceWorker" in navigator) {
  navigator.serviceWorker.register("/sw.js");
}
```

--> A Progressive Web App (PWA) combines a Service Worker + a Web App Manifest (`manifest.json`, linked via `<link rel="manifest" href="manifest.json">`) to let a website be "installed" on a device home screen and behave like a native app.

# IndexedDB

--> IndexedDB is a built-in browser database for storing large, structured amounts of data client-side (unlike `localStorage`, which only stores small string key-value pairs).
--> Supports indexes for fast queries, and works asynchronously so it doesn't block the page.
--> Typically accessed through a wrapper library (like `idb`) rather than the raw API directly, due to its verbose callback-based interface.

# WebSockets

--> WebSockets provide a persistent, full-duplex (two-way) connection between the browser and a server, unlike regular HTTP requests which are one-off request/response pairs.
--> Used for real-time features: live chat, live notifications, multiplayer games, collaborative editing.

```javascript
const socket = new WebSocket("wss://example.com/socket");
socket.onmessage = (event) => console.log("Received:", event.data);
socket.send("Hello server!");
```

# Web Workers

--> A Web Worker runs JavaScript in a background thread, separate from the page's main thread, so heavy computations don't freeze the UI.
--> Communicates with the main script via `postMessage()`/`onmessage` (data is copied between threads, not shared directly).

```javascript
// main.js
const worker = new Worker("worker.js");
worker.postMessage(40);
worker.onmessage = (e) => console.log("Result:", e.data);

// worker.js
onmessage = (e) => {
  const result = e.data * 2;
  postMessage(result);
};
```

# File API

--> The File API lets JavaScript read the contents of files selected via `<input type="file">` or dropped via drag-and-drop, directly in the browser (no server round-trip needed just to preview/read them).

```javascript
const input = document.querySelector('input[type="file"]');
input.addEventListener("change", () => {
  const file = input.files[0];
  const reader = new FileReader();
  reader.onload = () => console.log(reader.result);   // file contents
  reader.readAsText(file);
});
```

# Print Stylesheets

--> `<link rel="stylesheet" href="print.css" media="print">` — loads a separate stylesheet used ONLY when the page is printed (or exported to PDF via "Print"), letting you hide navigation/ads and adjust layout for paper.
--> Equivalently, a `@media print { ... }` block can be added inside a regular stylesheet.

# Canvas Deep Dive — A Raster Drawing Surface Controlled by JavaScript

--> `<canvas>` provides a blank, pixel-based drawing surface — it has NO built-in shapes of its own; everything visible is drawn imperatively via JavaScript using the Canvas 2D Rendering Context API.

```javascript
const canvas = document.getElementById("myCanvas");
const ctx = canvas.getContext("2d");

ctx.fillStyle = "blue";
ctx.fillRect(50, 50, 100, 80);        // Draw a filled rectangle

ctx.beginPath();
ctx.arc(200, 150, 40, 0, Math.PI * 2);   // Draw a circle
ctx.fillStyle = "red";
ctx.fill();
```

--> **The critical trade-off** — once something is drawn to a Canvas, the browser has NO memory of it as a distinct object; it's just colored pixels. There's no way to attach a click handler to just one shape — you must manually calculate whether click coordinates fall within that shape's bounds. This makes Canvas well-suited for graphics that don't need individual interactive elements: charts rendered once and updated wholesale, games with many rapidly-changing sprites, and image manipulation/filters. Canvas performance scales with the number of PIXELS drawn, largely independent of how many "shapes" make up the scene — excellent for scenes with thousands of small moving elements.

# SVG Deep Dive — Vector Graphics as Actual DOM Elements

--> Unlike Canvas, SVG elements ARE real DOM nodes — each `<circle>`, `<rect>`, or `<path>` can be selected with CSS, have event listeners attached, and be animated with standard CSS transitions, exactly like any other HTML element.

```html
<svg width="400" height="300">
  <circle cx="200" cy="150" r="40" fill="red" class="interactive-circle" />
</svg>
```

```css
.interactive-circle { transition: r 0.3s ease; }
.interactive-circle:hover { r: 50; }   /* Grows on hover using plain CSS — no JS animation loop needed */
```

--> **Why "scalable"** — SVG shapes are defined MATHEMATICALLY (a circle at coordinates X,Y with radius R) rather than as a fixed grid of pixels, so SVG graphics scale to any size with zero quality loss, unlike a raster image which becomes blurry when scaled up. This is precisely why icons/logos/simple illustrations are commonly delivered as SVG.
--> **`<path>`** can describe ANY arbitrary shape using a compact command syntax (`M`=moveto, `L`=lineto, `C`/`Q`=curves, `A`=arc, `Z`=closepath) — the foundation nearly every complex SVG illustration is built from; design tools generate these commands automatically on export.
--> **Choosing Canvas vs SVG** — choose SVG when elements need to be interactive/stylable with CSS, need lossless scaling, or need accessibility (SVG elements can carry ARIA labels; Canvas's pixel output is invisible to screen readers by default). Choose Canvas for a very large number of rapidly-changing elements, direct pixel manipulation, or when individual-element interactivity isn't needed.

# iframe Security Deep Dive

--> **Security implications** — a malicious site could embed YOUR site in a hidden/disguised iframe to trick users into clicking something they don't realize belongs to your site (clickjacking). The `X-Frame-Options`/`frame-ancestors` security headers exist specifically to let a site control whether/where it can be embedded in an iframe at all.
--> The `sandbox` attribute restricts what an embedded iframe's content is allowed to do — valuable when embedding UNTRUSTED third-party content.

```html
<iframe src="https://untrusted-widget.example.com" sandbox="allow-scripts"></iframe>
<!-- Only allows script execution — blocks forms, popups, and other risky capabilities by default -->
```

# Video and Audio — Format Fallback Strategy

```html
<video controls width="600">
  <source src="movie.mp4" type="video/mp4">
  <source src="movie.webm" type="video/webm">
  Your browser doesn't support video playback.
</video>
```

--> Multiple `<source>` elements let the browser pick whichever format IT actually supports — different browsers historically supported different video codecs, and providing several formats maximizes compatibility without needing browser-detection logic in JavaScript. The JavaScript `HTMLMediaElement` API (`.play()`, `.pause()`, `.currentTime`, events like `timeupdate`/`ended`) lets you build fully custom playback controls beyond the browser's default `controls` UI.
