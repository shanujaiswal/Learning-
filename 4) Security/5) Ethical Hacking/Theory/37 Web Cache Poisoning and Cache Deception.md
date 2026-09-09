### Web Cache Poisoning and Cache Deception

--> ⚠️ LEGAL / ETHICAL REMINDER: Only test the techniques below against DVWA, PortSwigger Web Security Academy's cache poisoning/deception labs, TryHackMe/HackTheBox web boxes, or an authorized client app explicitly in scope. Poisoning a cache means every SUBSEQUENT visitor gets your payload, not just you — testing this against a real, unauthorized site does mass, indiscriminate damage to real users. Always use a unique cache-buster on any probe so you never poison a shared production cache while just checking for the bug.

--> A web cache — a CDN edge node, a reverse proxy cache like Varnish, or even a browser's own cache — exists to avoid re-generating the same response repeatedly. It stores a response keyed by some subset of the request, called the "cache key." By default the cache key is usually just the request METHOD and PATH (and sometimes a few whitelisted query parameters) — it is normally NOT every header and NOT every query parameter. Any later request that matches the same cache key gets served the STORED response directly from the cache, without ever reaching the origin server again.
--> The vulnerability: if some input that is NOT part of the cache key (an "unkeyed input" — a header like `X-Forwarded-Host`, `X-Forwarded-Scheme`, `X-Forwarded-Proto`, or a query parameter the cache doesn't include in its key) can still influence the CONTENT of the response, then whatever value an attacker sends for that unkeyed input gets baked into the cached response — and served, unmodified, to every other visitor who happens to request that same cache key until the entry expires or is purged.

## Cache poisoning — worked example

--> Suppose a page reflects the `X-Forwarded-Host` header (a header proxies normally add so the origin knows the original `Host` the client used) into a canonical `<link>` tag or into a generated absolute URL for a JS asset:
```html
<!-- Server-generated response body, before caching -->
<link rel="canonical" href="https://vulnerable-site.com/home">
<script src="https://vulnerable-site.com/static/analytics.js"></script>
```
--> A normal request just reflects the real host back. But the cache key here is only `GET /home` — it does NOT include the `X-Forwarded-Host` header. So the attacker sends this once:
```text
GET /home HTTP/1.1
Host: vulnerable-site.com
X-Forwarded-Host: attacker-controlled.com
```
--> The origin server, trusting the unkeyed header, generates:
```html
<link rel="canonical" href="https://attacker-controlled.com/home">
<script src="https://attacker-controlled.com/static/analytics.js"></script>
```
--> The cache stores THIS poisoned response under the key `GET /home` — with no awareness that `X-Forwarded-Host` was ever involved. From that point on, every ordinary visitor who requests `GET /home` (with a completely normal, unmodified request — no attacker interaction needed on their end at all) gets served the poisoned response straight from cache, loading `analytics.js` from the attacker's domain. The attacker's script now runs in every subsequent visitor's browser.
--> Contrast with ordinary reflected XSS (note 04): reflected XSS requires tricking EACH individual victim into clicking a specially crafted link. Cache poisoning requires only ONE request from the attacker — after that, the payload is stored and served automatically to everyone, turning a single request into a stored, mass-reaching attack that behaves like stored XSS but without ever having to get anything saved in a database.

## Detection methodology

1. Identify what's cacheable: inspect response headers for `Cache-Control` (e.g. `max-age=3600`, `public`), `Age` (seconds since the response was cached — a nonzero, incrementing `Age` proves you're hitting a cache, not the origin), and `X-Cache` (`HIT`/`MISS`, common on CDNs like Cloudflare/Fastly/Varnish).
2. Add a unique cache-buster to the PATH or a query param the cache is known to include in its key (e.g. `/home?cb=12345`), so your probing traffic never poisons the real, shared cache entry while you're still testing.
3. Probe candidate unkeyed inputs ONE AT A TIME — common ones: `X-Forwarded-Host`, `X-Forwarded-Scheme`, `X-Forwarded-Proto`, `X-Original-URL`, `X-HTTP-Method-Override`, non-cache-key query params. Send an obviously distinctive marker value and check if it's reflected in the response body/headers.
4. Confirm reflection first (does the marker show up at all), THEN confirm actual caching behavior separately: request the SAME cache-busted URL again without the injected header and see if the poisoned value is still served — if so, the cache stored it and is now serving it to a "clean" request, confirming the input truly is unkeyed and the poisoning is real (not just a reflection with no caching impact).

## Web Cache Deception — the inverse/sibling issue

--> Cache poisoning makes a cache store the WRONG content. Cache deception makes a cache store content that was never supposed to be cached AT ALL — typically a dynamic, personalized, sensitive response.
--> The classic technique abuses caches that decide "is this cacheable?" using the PATH's file extension rather than understanding the application's actual routing. Many caches assume anything ending in `.js`, `.css`, `.png`, etc. must be a static asset and cache it aggressively by default.
```text
GET /my-account/settings.js HTTP/1.1
Cookie: session=victim_session_token
```
--> If the application's router loosely matches path segments (e.g. treats anything under `/my-account/*` as the same account-settings handler, ignoring the fake trailing `.js`), it still returns the victim's real, personalized, sensitive account page — HTML full of their name, email, API keys, whatever the page shows — but now with a URL ending in `.js`. The cache, going purely off the extension, decides "this is a static JS file" and stores it under the key `/my-account/settings.js`.
--> Any OTHER user (including the attacker) can now request that exact same crafted URL:
```text
GET /my-account/settings.js HTTP/1.1
(no valid session needed — served straight from cache)
```
--> and receive the FIRST victim's cached, sensitive, personal page straight from the shared cache — no authentication required, because the cache serves it before the request ever reaches the application's auth checks again.
--> The attack chain in practice: attacker lures/tricks a logged-in victim into visiting `https://vulnerable-site.com/my-account/nonexistent.css` (e.g. via a link), the response gets cached under that path, and the attacker then simply requests the same path themselves to pull the victim's cached, sensitive data out of the shared cache.

## Mitigations

| Issue | Mitigation |
|---|---|
| Unkeyed input affects cached content (poisoning) | Configure the cache key to include every header/parameter that can influence response content, OR strip/ignore those headers at the edge before they reach the origin. |
| Sensitive/personalized responses get cached at all | Set `Cache-Control: private` or `Cache-Control: no-store` explicitly on any response containing session-specific or sensitive data — never rely on the cache's default extension/path heuristics to decide. |
| Cache deception via fake extensions | Configure strict path-based routing that does NOT silently ignore or wildcard-match extra trailing path segments — a request for `/my-account/settings.js` should 404 unless that literal resource exists, not silently fall through to the `/my-account` handler. |
| General hardening | Regularly audit `Vary` header usage so caches correctly differentiate responses that legitimately differ by header (e.g. `Accept-Encoding`, `Authorization`), rather than accidentally caching one user's response for all. |

--> Note 36 covers HTTP request smuggling, a related desync-class vector that can itself be used to deliver a cache-poisoning payload by exploiting front-end/back-end disagreement on request boundaries — the two attack classes are frequently chained together in real assessments; also cross-reference note 04 (OWASP Top 10, particularly Security Misconfiguration and Sensitive Data Exposure, which cache misconfigurations fall directly under).

--> With caching-layer attacks covered, note 38 shifts to a server-side code-execution surface hiding inside a completely different feature — template rendering — with Server-Side Template Injection.
