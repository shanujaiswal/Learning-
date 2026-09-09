# The Problem -- JavaScript Is Single-Threaded

--> All JavaScript on a page (except what's covered here) runs on ONE thread -- the same thread responsible for rendering the UI and responding to clicks/scrolls. A slow, CPU-heavy computation (parsing a huge dataset, image processing, complex calculations) blocks that thread entirely, freezing the whole page until it finishes.

# Web Workers -- Real Background Threads

--> A Web Worker runs JavaScript on a genuinely separate background thread, so heavy computation no longer blocks the main thread/UI.
--> Workers do NOT have access to the DOM directly -- they communicate with the main thread exclusively through message passing, never by sharing memory/objects directly (this is what keeps both threads safe from race conditions on shared state).

```javascript
// main.js
const worker = new Worker("worker.js");

worker.postMessage({ command: "calculate", data: largeArray });

worker.onmessage = (event) => {
  console.log("Result from worker:", event.data);
};

worker.onerror = (error) => {
  console.error("Worker error:", error.message);
};
```

```javascript
// worker.js
self.onmessage = (event) => {
  const { command, data } = event.data;

  if (command === "calculate") {
    const result = data.reduce((sum, n) => sum + n, 0);  // Heavy work, off the main thread
    self.postMessage(result);
  }
};
```

# When to Use a Web Worker

--> Good fits -- large data processing/parsing (CSV, JSON), image/video manipulation, complex calculations (cryptographic hashing client-side, physics simulations), anything CPU-bound that would otherwise cause the UI to visibly stutter or freeze.
--> Not needed for -- typical async operations like `fetch()` -- network I/O is already non-blocking by nature (handled by the browser, not the main JS thread), so a worker adds unnecessary complexity there. Workers solve CPU-bound blocking, not I/O-bound waiting.

# Terminating a Worker

```javascript
worker.terminate();   // From the main thread -- immediately stops the worker
self.close();          // From inside the worker itself
```

--> Workers aren't automatically cleaned up when no longer needed -- forgetting to terminate one that's done its job leaves it consuming memory/resources unnecessarily.

# Dedicated Workers vs Shared Workers vs Service Workers

--> Dedicated Worker (what's shown above) -- tied to a single script/tab that created it.
--> Shared Worker -- can be accessed by multiple browser tabs/windows from the same origin, useful for coordinating state across several open tabs of the same app.
--> Service Worker (covered separately in the Web APIs file) -- solves a different problem entirely: acts as a network proxy for offline support/caching/push notifications, not general-purpose background computation.

# Transferring Large Data Efficiently

--> By default, `postMessage` copies data between threads (structured clone) -- fine for most data, but potentially slow for very large buffers (e.g. large binary data).
--> Transferable Objects (like `ArrayBuffer`) can be TRANSFERRED (ownership moves to the worker, zero-copy) instead of copied, by passing them in the second argument to `postMessage`.

```javascript
worker.postMessage(largeArrayBuffer, [largeArrayBuffer]);   // Transferred, not copied -- main thread loses access to it
```
