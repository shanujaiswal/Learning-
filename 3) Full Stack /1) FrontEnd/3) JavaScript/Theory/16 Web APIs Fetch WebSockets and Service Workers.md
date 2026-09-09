# Fetch API In Depth

--> fetch(url, options) makes an HTTP request and returns a Promise that resolves to a Response object -- it does NOT reject on HTTP error status codes (404, 500), only on network failure
--> Must check response.ok (true for status 200-299) manually and throw yourself if you want 4xx/5xx to be treated as errors

```javascript
async function getUser(id) {
  const response = await fetch(`/api/users/${id}`);
  if (!response.ok) {
    throw new Error(`Request failed: ${response.status}`);
  }
  return response.json();
}
```

# Request Options

--> method -- "GET" (default), "POST", "PUT", "PATCH", "DELETE"
--> headers -- plain object or a Headers instance, e.g. { "Content-Type": "application/json", Authorization: `Bearer ${token}` }
--> body -- string (JSON.stringify(data)), FormData, Blob, or URLSearchParams -- not allowed with GET/HEAD
--> mode -- "cors", "no-cors", "same-origin" -- controls cross-origin request behavior
--> credentials -- "omit" (default cross-origin), "same-origin", "include" -- whether cookies are sent
--> cache -- "default", "no-store", "reload", "force-cache" -- controls HTTP caching behavior

```javascript
const response = await fetch("/api/users", {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ name: "Alice" }),
  credentials: "include",
});
```

# Response Object

--> response.status / response.statusText -- numeric code and its text
--> response.ok -- boolean shortcut for status in the 200-299 range
--> response.headers -- a Headers object, e.g. response.headers.get("Content-Type")
--> Body can only be consumed ONCE -- response.json(), response.text(), response.blob(), response.formData(), response.arrayBuffer()
--> response.clone() -- creates a duplicate of the response so the body can be read more than once (e.g. once for a cache, once for the app)

# AbortController with Fetch

--> new AbortController() creates a controller with a .signal that can cancel an in-flight fetch
--> Commonly used to cancel stale requests (e.g. search-as-you-type) or to implement request timeouts

```javascript
const controller = new AbortController();
const timeoutId = setTimeout(() => controller.abort(), 5000);

try {
  const response = await fetch("/api/data", { signal: controller.signal });
  clearTimeout(timeoutId);
  const data = await response.json();
} catch (err) {
  if (err.name === "AbortError") console.log("Request timed out or was cancelled");
}
```

# Uploading and Downloading Progress

--> fetch does NOT natively support upload progress -- for that, XMLHttpRequest (xhr.upload.onprogress) is still required
--> Download progress can be tracked by reading response.body as a ReadableStream and accumulating chunks manually

```javascript
const response = await fetch(url);
const reader = response.body.getReader();
const total = +response.headers.get("Content-Length");
let received = 0;

while (true) {
  const { done, value } = await reader.read();
  if (done) break;
  received += value.length;
  console.log(`${Math.round((received / total) * 100)}%`);
}
```

---

# WebSockets

--> WebSocket is a protocol that provides a persistent, full-duplex (two-way) connection between client and server over a single TCP connection -- unlike HTTP, either side can push messages at any time without a new request
--> Used for real-time features: chat apps, live notifications, collaborative editing, live price feeds, multiplayer games

# Connection Lifecycle

```javascript
const socket = new WebSocket("wss://example.com/socket");

socket.onopen = () => console.log("Connection opened");
socket.onmessage = (event) => console.log("Received:", event.data);
socket.onerror = (error) => console.log("Error:", error);
socket.onclose = (event) => console.log("Closed:", event.code, event.reason);

socket.send(JSON.stringify({ type: "greeting", text: "hello" }));
socket.close(1000, "Done");
```

--> readyState -- CONNECTING (0), OPEN (1), CLOSING (2), CLOSED (3)
--> Data sent/received is text or binary (Blob/ArrayBuffer) -- JSON is commonly used by stringifying/parsing manually, there is no built-in JSON support

# Reconnection Strategy

--> WebSockets do NOT auto-reconnect -- onclose fires on any disconnect (network drop, server restart) and reconnection logic must be written manually
--> Common pattern: exponential backoff so reconnect attempts don't hammer the server

```javascript
function connect(url, retries = 0) {
  const socket = new WebSocket(url);

  socket.onclose = () => {
    const delay = Math.min(1000 * 2 ** retries, 30000); // cap backoff at 30s
    setTimeout(() => connect(url, retries + 1), delay);
  };

  socket.onopen = () => { retries = 0; }; // reset backoff once reconnected
  return socket;
}
```

--> Heartbeats (periodic ping/pong messages) are often used to detect a dead connection faster than waiting for a TCP timeout

# WebSockets vs Alternatives

--> Server-Sent Events (SSE) -- one-way (server-to-client) only, simpler, auto-reconnects natively, uses plain HTTP -- good fit when the client never needs to push data back
--> Long polling -- client repeatedly requests, server holds the response until new data exists -- fallback for environments without WebSocket support
--> WebSockets -- best when both sides need to push data with low latency

---

# Service Workers

--> A Service Worker is a JavaScript file that runs in a separate background thread, between the web app and the network -- it has no access to the DOM but can intercept network requests, cache responses, and receive push notifications
--> Enables offline support, background sync, and push notifications -- foundational piece of Progressive Web Apps (PWAs)

# Registration

```javascript
// In the main app script
if ("serviceWorker" in navigator) {
  navigator.serviceWorker.register("/sw.js")
    .then((reg) => console.log("Service Worker registered", reg))
    .catch((err) => console.log("Registration failed", err));
}
```

--> Requires HTTPS (or localhost for development) -- browsers refuse to register service workers over plain HTTP for security reasons

# Lifecycle

--> install -- fires once when the service worker is first registered (or updated) -- typically used to pre-cache assets
--> activate -- fires after install, when the service worker takes control -- typically used to clean up old caches
--> Once active, the service worker intercepts fetch events for pages within its scope

```javascript
// sw.js
const CACHE_NAME = "app-cache-v1";
const ASSETS = ["/", "/index.html", "/styles.css", "/app.js"];

self.addEventListener("install", (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => cache.addAll(ASSETS))
  );
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((key) => key !== CACHE_NAME).map((key) => caches.delete(key)))
    )
  );
});
```

--> An updated service worker installs alongside the old one but stays "waiting" until all tabs using the old version are closed, unless self.skipWaiting() is called

# Caching Strategies (in the fetch event)

--> Cache First -- serve from cache if present, otherwise fetch from network (good for static assets like CSS/JS/images)
--> Network First -- try the network, fall back to cache on failure (good for frequently-updated data)
--> Stale-While-Revalidate -- serve from cache immediately AND fetch a fresh copy in the background to update the cache for next time

```javascript
self.addEventListener("fetch", (event) => {
  event.respondWith(
    caches.match(event.request).then((cached) => {
      return cached || fetch(event.request).then((response) => {
        return caches.open(CACHE_NAME).then((cache) => {
          cache.put(event.request, response.clone());
          return response;
        });
      });
    })
  );
});
```

# Offline Support and Background Sync

--> Because the service worker can respond entirely from cache, the app can keep working (or show a fallback page) with no network connection
--> Background Sync API -- lets the app defer an action (e.g. sending a queued form) until connectivity is restored, even if the page itself has been closed
--> Push API -- lets a server send a push message to the service worker, which can then display a notification via the Notifications API, even when the site is not open

# Deep Dive -- fetch() vs Axios

--> `fetch` is built into every modern browser (no dependency needed), but is intentionally minimal -- as shown above, it does NOT reject on HTTP error statuses, has no built-in request/response interceptors, no automatic JSON parsing, and no built-in timeout support (the `AbortController` timeout pattern shown above is the manual workaround).
--> Axios (a popular third-party library) wraps these gaps -- it rejects automatically on non-2xx status codes, auto-parses JSON responses, supports request/response interceptors (useful for globally attaching an auth token or handling 401 errors in one place, connecting to the JWT authentication concepts in the Node/Express Authentication file), and has built-in upload progress tracking (which raw `fetch` lacks entirely, as noted above).
--> In practice, `fetch` is entirely sufficient for simple projects or when avoiding a dependency matters; Axios (or a similar wrapper) tends to earn its place once a project needs consistent global error handling, request interceptors, or upload progress across many different API calls.

# Deep Dive -- Fetch Response Streaming Beyond Progress Tracking

--> Reading `response.body` as a `ReadableStream` (shown above for download progress) also enables processing LARGE responses incrementally, without waiting for the entire payload to download before starting work -- useful for progressively rendering a very large JSON array or text response as chunks arrive, rather than blocking on the full download first. This is the same streaming foundation that makes modern LLM API responses (token-by-token streaming output, referenced in the Generative AI file in the Data Science and AI folder) practical to display incrementally rather than waiting for a complete response.
