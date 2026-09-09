# What a WAF Actually Does (Recap From the Defensive Side)

--> A Web Application Firewall (covered defensively in the AWS Security Hardening file) inspects incoming HTTP requests and blocks ones matching known-malicious patterns -- SQL injection signatures, XSS payload patterns, path traversal attempts -- before they ever reach the application. From the offensive side, a WAF is simply another obstacle standing between a payload and the vulnerable code it's meant to trigger.

# Why WAF Bypass Is Possible at All

--> A WAF works primarily through PATTERN MATCHING (signatures, regex, sometimes machine learning) -- it's fundamentally trying to distinguish malicious input from legitimate input using heuristics, not by actually understanding the application's logic. Any gap between "what the WAF's pattern recognizes" and "what the underlying application/database/browser will actually interpret the same way" is a potential bypass.

# Case and Encoding Variation

--> Simple case manipulation can slip past naive, case-sensitive signatures.

```sql
-- Blocked:
' OR 1=1--
-- Sometimes bypasses a naive filter:
' oR 1=1--
' /*comment*/OR/*comment*/1=1--
```

--> URL encoding, double encoding, or Unicode encoding of characters can pass through a WAF that only checks the decoded/normalized form once, while the backend application decodes it AGAIN (or differently), revealing the malicious payload only after the WAF has already approved the request.

```
%27%20OR%201=1--          (single URL-encoded)
%2527%2520OR%25201=1--    (double URL-encoded -- may defeat a WAF checking only single-decoded input)
```

# SQL Injection-Specific Evasion (Building on the OWASP Top 10 File)

--> Inline comments to break up a signature's expected pattern without changing SQL's actual parsing.

```sql
UNI/**/ON SEL/**/ECT username, password FROM users
```

--> Alternative syntax achieving the same logical result the WAF's specific signature doesn't cover -- e.g. using `OR 'a'='a'` instead of the far more commonly-signature-matched `OR 1=1`.

# XSS-Specific Evasion (Building on the Burp Suite/Client-Side Attacks File)

--> Alternative event handlers and tag choices when common ones (`<script>`, `onerror`) are specifically filtered.

```html
<img src=x onerror=alert(1)>
<svg onload=alert(1)>
<a href="javascript:alert(1)">click</a>
```

--> Case variation and null-byte injection between characters have historically bypassed regex-based filters expecting an exact, contiguous match of a blocked tag/keyword.

# HTTP Parameter Pollution and Request Smuggling

--> Sending the SAME parameter multiple times in one request can cause the WAF and the backend application to each pick a DIFFERENT one of the duplicate values -- the WAF inspects and approves one value, while the vulnerable backend actually processes the other.

```
?id=1&id=' OR 1=1--
```

--> HTTP Request Smuggling (exploiting inconsistencies in how a WAF/proxy and the backend server each parse ambiguous `Content-Length`/`Transfer-Encoding` headers) can let a smuggled second request bypass WAF inspection entirely, since the WAF and backend disagree about where one request ends and the next begins.

# Why WAF Bypass Testing Matters (Framed Defensively)

--> A WAF should always be treated as ONE LAYER of defense, never the sole protection -- the actual fix for any of the above is patching the underlying vulnerability itself (parameterized queries, output encoding, covered in the OWASP Top 10 file), not tuning WAF rules indefinitely to chase each new evasion technique. Testing WAF bypass techniques during an authorized engagement demonstrates exactly this point to a client relying too heavily on WAF-only protection.
