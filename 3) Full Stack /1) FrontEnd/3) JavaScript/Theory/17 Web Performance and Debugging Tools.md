# Web Performance Basics

--> Loading performance is usually described with these key metrics (Core Web Vitals + related):
--> LCP (Largest Contentful Paint) -- time until the largest visible element (hero image, heading) renders -- good is under 2.5s
--> FCP (First Contentful Paint) -- time until the first piece of content (text/image) renders
--> TTFB (Time To First Byte) -- how long the server takes to respond to the initial request
--> CLS (Cumulative Layout Shift) -- measures unexpected layout movement (e.g. images without dimensions pushing content down) -- good is under 0.1
--> INP (Interaction to Next Paint) -- replaced FID (First Input Delay) as the responsiveness metric -- time from a user interaction (click/tap) to the next paint

# Reducing Load Time

--> Minify and bundle CSS/JS -- fewer, smaller files
--> Code-splitting -- load only the JS needed for the current page/route, defer the rest (React.lazy + Suspense, dynamic import())
--> Compress assets -- gzip/Brotli on the server, modern image formats (WebP/AVIF)
--> Lazy-load images/iframes below the fold -- <img loading="lazy">
--> Use a CDN to serve static assets closer to the user
--> Preload critical resources -- <link rel="preload"> for fonts/critical CSS, <link rel="preconnect"> for early DNS/TLS to third-party origins
--> Avoid render-blocking resources -- defer/async on <script>, avoid @import chains in CSS

# Rendering Performance

--> Browsers render in phases -- Style (compute CSS) -> Layout (compute geometry/position) -> Paint (fill in pixels) -> Composite (layer them together)
--> Changing layout-affecting properties (width, top, display) triggers Layout + Paint -- expensive
--> Changing transform/opacity only triggers Composite -- cheap, runs on the GPU -- prefer these for animations
--> Avoid "layout thrashing" -- reading a layout property (offsetHeight) right after writing a style forces the browser to recalculate synchronously; batch reads and writes separately

# Debouncing and Throttling

--> Debounce -- delays running a function until after a pause in events (e.g. wait 300ms after the user stops typing before firing a search request)
--> Throttle -- runs a function at most once every N milliseconds regardless of how many events fire (e.g. limit a scroll handler to once every 100ms)

```javascript
function debounce(fn, delay) {
  let timer;
  return (...args) => {
    clearTimeout(timer);
    timer = setTimeout(() => fn(...args), delay);
  };
}

function throttle(fn, limit) {
  let waiting = false;
  return (...args) => {
    if (waiting) return;
    fn(...args);
    waiting = true;
    setTimeout(() => (waiting = false), limit);
  };
}
```

---

# Browser DevTools -- Network Tab

--> Shows every request the page makes -- status code, size, timing waterfall (DNS, connect, TTFB, download)
--> "Disable cache" checkbox -- forces fresh requests during testing, since browsers cache aggressively by default
--> Throttling dropdown -- simulate Slow 3G/Fast 3G to test on poor connections
--> Filter by type (XHR/Fetch, JS, CSS, Img) to isolate what's slow

# Browser DevTools -- Performance Tab

--> Records a timeline of everything the browser does while interacting with the page -- scripting, rendering, painting
--> Flame chart shows the call stack over time -- wide blocks mean a function is taking a long time (main-thread blocking)
--> Look for long yellow (scripting) blocks and layout thrashing (purple) to find jank
--> "Bottom-Up" / "Call Tree" views help identify which specific function is the actual bottleneck vs which one just triggered it

# Browser DevTools -- Memory Tab

--> Heap snapshot -- captures all objects currently in memory, useful for finding what's taking up space
--> Comparing two snapshots taken over time reveals a memory leak -- objects that should have been garbage collected but keep growing in count
--> Common leak causes -- detached DOM nodes (removed from the page but still referenced by JS), forgotten setInterval/event listeners, closures holding references longer than needed

# Console and Debugger

--> console.table() -- prints arrays/objects as a readable table
--> console.time("label") / console.timeEnd("label") -- measures elapsed time between two points
--> debugger; statement -- pauses execution at that line when DevTools is open, same as a manual breakpoint
--> Conditional breakpoints -- right-click a line number in Sources tab, add a condition so it only pauses when that expression is true
--> Network request breakpoints -- pause execution whenever a request matching a URL pattern fires

# Lighthouse and Web Vitals Tooling

--> Lighthouse (built into Chrome DevTools, also a CLI/CI tool) -- audits Performance, Accessibility, SEO, Best Practices, gives a 0-100 score with specific fix suggestions
--> PageSpeed Insights -- runs Lighthouse plus real-world (CrUX) field data from actual users
--> web-vitals JS library -- reports Core Web Vitals directly from real users in production (Real User Monitoring, vs Lighthouse's lab data)

# Deep Dive -- requestAnimationFrame and requestIdleCallback

--> `setTimeout`/`setInterval` (covered in the Events and DOM file) are NOT synchronized with the browser's actual rendering cycle -- a timer can fire in the middle of a frame, causing visual updates to feel slightly out of sync. `requestAnimationFrame(callback)` specifically schedules a callback to run right BEFORE the browser's next repaint, making it the correct tool for any JavaScript-driven visual animation.

```javascript
function animate() {
  element.style.transform = `translateX(${position}px)`;
  position += 2;
  if (position < 500) requestAnimationFrame(animate);   // Schedule the NEXT frame's update
}
requestAnimationFrame(animate);
```

--> `requestIdleCallback(callback)` schedules a callback to run only when the browser has genuine spare idle time during the current frame, specifically for non-urgent background work (analytics logging, pre-fetching data likely to be needed soon) that shouldn't compete with more time-critical rendering/interaction work for the main thread.

# Deep Dive -- Moving Heavy Work Off the Main Thread

--> Directly connecting to the Web Workers file -- any CPU-intensive computation (parsing a huge dataset, image processing, complex calculations) that runs on the main thread blocks rendering and user interaction entirely for its duration, directly causing poor INP scores (mentioned above). Moving such work into a Web Worker keeps the main thread free to handle rendering and user input responsively, which is precisely the performance justification for reaching for Web Workers rather than just accepting a slow, blocking computation.
