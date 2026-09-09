# PWAs -- Recap and Why This File Goes Deeper

--> The React Native and Flutter Fundamentals file introduces PWAs briefly as the "cheapest to build" mobile approach -- a website that can be "installed" to a home screen. This file covers the actual mechanics that make that possible: the service worker, the manifest, offline caching strategies, background sync, and push notifications.
--> All of this builds directly on plain web technologies covered throughout the Full Stack track (JavaScript, the Fetch API, HTTP) -- a PWA is not a separate platform to learn, it's a set of browser APIs layered on top of an ordinary website.

# The Web App Manifest

--> `manifest.json` is a plain JSON file that tells the browser how the app should behave when "installed" -- its name, icons, colors, and display mode -- linked from the HTML `<head>`.

```html
<link rel="manifest" href="/manifest.json" />
```

```json
{
  "name": "My Task Tracker",
  "short_name": "Tasks",
  "start_url": "/",
  "display": "standalone",
  "background_color": "#ffffff",
  "theme_color": "#2563eb",
  "icons": [
    { "src": "/icons/icon-192.png", "sizes": "192x192", "type": "image/png" },
    { "src": "/icons/icon-512.png", "sizes": "512x512", "type": "image/png" }
  ]
}
```

--> `"display": "standalone"` is the key field making it FEEL like a native app rather than a browser tab -- it hides the browser's own address bar/URL chrome, presenting the app in its own window with just the OS status bar visible, the same visual effect as an installed native app.
--> `start_url` controls what page opens when launched from the home screen icon (often set to a specific onboarding-free entry point, distinct from whatever URL the user happened to be on when they installed it) and the icon sizes/`theme_color` control how the app appears in the OS app switcher, splash screen, and home screen.
--> A valid manifest plus a registered service worker (below) plus HTTPS are the baseline requirements browsers check before offering the "Add to Home Screen" / install prompt at all.

# Service Workers -- The Core Mechanism

--> A **service worker** is a JavaScript file that runs in its own background thread, SEPARATE from any page, sitting between the browser and the network -- it can intercept every network request the page makes and decide how to respond, which is what makes offline support and background behavior possible at all.
--> Registration happens from the page's normal JS, but the service worker file itself runs independently and can outlive the page being open (subject to the browser eventually terminating idle service workers to save resources).

```javascript
// In the main page's JS
if ("serviceWorker" in navigator) {
  navigator.serviceWorker.register("/sw.js")
    .then((reg) => console.log("Service worker registered", reg))
    .catch((err) => console.error("Registration failed", err));
}
```

```javascript
// sw.js -- the service worker file itself
const CACHE_NAME = "app-cache-v1";
const PRECACHE_URLS = ["/", "/index.html", "/styles.css", "/app.js"];

self.addEventListener("install", (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => cache.addAll(PRECACHE_URLS))
  );
});

self.addEventListener("fetch", (event) => {
  event.respondWith(
    caches.match(event.request).then((cached) => cached || fetch(event.request))
  );
});
```

--> The service worker's lifecycle -- `install` (fires once, the right moment to pre-cache core assets), `activate` (fires when a new version takes over, the right moment to clean up old caches), and `fetch` (fires on EVERY network request the page makes, letting the service worker intercept and respond however it chooses) -- are the three events almost every PWA's service worker hooks into.
--> Updating a service worker requires bumping something in the file itself (browsers detect a byte-for-byte change) -- and by default, an updated service worker waits until all open tabs of the old version are closed before fully taking over, a deliberate safety mechanism to avoid a page and its service worker disagreeing mid-session about what version is running.

# Offline-First Caching Strategies

--> Different resources warrant different caching strategies -- there's no single "correct" one, and real apps typically mix several depending on what's being requested.

--> **Cache-first** (shown in the fetch handler above) -- check the cache, only hit the network if nothing's cached. Best for static assets (CSS, JS bundles, fonts, icons) that rarely change and where instant load matters more than always having the absolute latest byte.
--> **Network-first** -- try the network, fall back to cache only if the request fails (offline, or the server is down). Best for content that should be as fresh as possible when online (a news feed, live pricing) but should still degrade gracefully offline rather than showing nothing.
```javascript
self.addEventListener("fetch", (event) => {
  event.respondWith(
    fetch(event.request)
      .then((response) => {
        const clone = response.clone();                          // Response bodies can only be read once
        caches.open(CACHE_NAME).then((cache) => cache.put(event.request, clone));
        return response;
      })
      .catch(() => caches.match(event.request))                   // Offline fallback
  );
});
```
--> **Stale-while-revalidate** -- serve the cached response IMMEDIATELY, then fetch a fresh copy in the background and update the cache for next time, directly analogous in spirit to the same-named strategy covered in the Data Fetching with React Query and SWR file, just implemented at the network-request layer instead of the React-state layer.
```javascript
self.addEventListener("fetch", (event) => {
  event.respondWith(
    caches.open(CACHE_NAME).then(async (cache) => {
      const cached = await cache.match(event.request);
      const networkFetch = fetch(event.request).then((response) => {
        cache.put(event.request, response.clone());
        return response;
      });
      return cached || networkFetch;                              // Return cached instantly if present, else wait on network
    })
  );
});
```
--> In practice, most production PWAs use **Workbox** (a Google-maintained library) rather than hand-writing every caching strategy -- it provides pre-built strategy implementations (`CacheFirst`, `NetworkFirst`, `StaleWhileRevalidate` as importable strategy classes) and integrates with build tools to auto-generate the precache manifest, similar in spirit to how most teams reach for React Query rather than hand-rolling `useEffect` fetching (covered in the Data Fetching file) once the problem is well-understood and solved-for generally.

# Background Sync

--> The **Background Sync API** lets a PWA defer an action (e.g. submitting a form, sending a chat message) until the device actually has connectivity again, rather than simply failing outright when a `fetch` is attempted while offline.

```javascript
// In the page -- register a sync request instead of fetching directly when offline
async function sendMessage(message) {
  await saveToIndexedDB(message);            // Store the pending action locally first
  const registration = await navigator.serviceWorker.ready;
  await registration.sync.register("send-messages");
}
```

```javascript
// sw.js -- runs automatically once connectivity returns, even if the page itself is closed
self.addEventListener("sync", (event) => {
  if (event.tag === "send-messages") {
    event.waitUntil(sendAllPendingMessages());   // Reads from IndexedDB, POSTs each, then clears them on success
  }
}
```

--> The critical property this provides -- the sync event can fire even after the user has closed the tab/app, as soon as the OS/browser detects the device is back online, which a normal in-page `fetch` retry loop cannot do since it requires the page to still be running.
--> Browser support for the full Background Sync API is inconsistent (notably absent in Safari/iOS as of recent versions) -- a common practical pattern is to attempt the Background Sync registration, and fall back to a simpler "retry on next app open" strategy (checking a local queue in IndexedDB on load) for browsers that lack it.

# Push Notifications

--> Push notifications require two cooperating pieces -- the **Push API** (receiving a message from a server even while the app/page isn't open, handled by the service worker) and the **Notifications API** (actually displaying a system notification to the user).

```javascript
// In the page -- request permission and subscribe
async function subscribeToPush() {
  const permission = await Notification.requestPermission();
  if (permission !== "granted") return;

  const registration = await navigator.serviceWorker.ready;
  const subscription = await registration.pushManager.subscribe({
    userVisibleOnly: true,
    applicationServerKey: VAPID_PUBLIC_KEY,     // Identifies your server to the push service
  });

  await fetch("/api/save-subscription", {        // Send the subscription object to your backend to store
    method: "POST",
    body: JSON.stringify(subscription),
  });
}
```

```javascript
// sw.js -- handles an incoming push message and displays a notification
self.addEventListener("push", (event) => {
  const data = event.data.json();
  event.waitUntil(
    self.registration.showNotification(data.title, {
      body: data.body,
      icon: "/icons/icon-192.png",
    })
  );
});

self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  event.waitUntil(clients.openWindow("/messages"));   // Deep-link into the app on tap
});
```

--> The backend side needs the subscription object (obtained above) stored per-user, and uses a library implementing the **Web Push protocol** (VAPID keys for authentication) to actually deliver a message to the browser vendor's push service, which then wakes the service worker via the `push` event -- this happens even if the app/tab isn't currently open, the same "runs independently of any open page" property that makes background sync possible.
--> iOS Safari only added Web Push support for installed PWAs relatively recently and with real limitations (the PWA generally needs to have been added to the home screen first) -- this remains one of the most significant practical gaps between what a PWA and a true native app (covered in the Native iOS and Android Development file) can reliably do for notifications, especially on iOS specifically.

# What PWAs Still Can't Fully Match

--> Despite everything above, PWAs remain meaningfully behind native apps (and to a lesser extent React Native/Flutter, covered in the React Native and Flutter Fundamentals file) for deep OS integration -- background location tracking, Bluetooth/NFC access, full-screen camera/AR capture, and being discoverable through the App Store/Play Store's own search and install flow are either unavailable, limited, or entirely platform-inconsistent for web-based apps.
--> The genuinely large win PWAs offer in exchange -- one codebase reusing existing web skills entirely, no app-store review process or release cadence (covered in the React Native Ecosystem, Testing, Release, and Flutter State Management file) to ship an update, and installability without requiring a store visit at all -- is exactly why PWAs remain the right choice specifically for content-focused or utility apps that don't need deep native device integration, rather than a universal replacement for native/cross-platform apps.
