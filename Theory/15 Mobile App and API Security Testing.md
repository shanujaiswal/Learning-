### Mobile App and API Security Testing

--> ⚠️ LEGAL / ETHICAL REMINDER: Decompile, instrument, and intercept traffic only for apps you own the source of, deliberately vulnerable practice apps (OWASP MASTG's Crackmes/UnCrackable series, DVIA, InsecureBankv2), or targets explicitly in scope of an authorized engagement or bug bounty program. Reverse-engineering a real production app you don't have permission to test can violate both computer-misuse laws AND the app's terms of service/EULA independently — check both.

--> This continues from note 04's OWASP Top 10 (web) and note 05's Metasploit basics into two adjacent, increasingly important domains: mobile applications and the APIs that back them (and increasingly, back everything else — SPAs, IoT, B2B integrations).

## OWASP Mobile Top 10 — Overview

--> The OWASP Mobile Application Security (MAS) project maintains its own Top 10, distinct from the web one, because mobile apps have a different attack surface: the binary itself is in the attacker's hands, not just network traffic.

1. **M1 — Improper Credential Usage**: hardcoded credentials/API keys embedded in the app binary, insecure credential storage on-device.
2. **M2 — Inadequate Supply Chain Security**: vulnerable/malicious third-party SDKs and libraries bundled into the app.
3. **M3 — Insecure Authentication/Authorization**: weak session handling, client-side-only auth checks that can be bypassed by directly calling the backend API.
4. **M4 — Insufficient Input/Output Validation**: injection flaws reachable via deep links, intents, or malformed server responses.
5. **M5 — Insecure Communication**: no TLS, weak TLS config, or (see below) no certificate pinning.
6. **M6 — Inadequate Privacy Controls**: over-collection or insecure handling of PII.
7. **M7 — Insufficient Binary Protections**: no obfuscation/anti-tamper, making static analysis and modification trivial.
8. **M8 — Security Misconfiguration**: same spirit as the web OWASP item — debug flags left on, verbose logging, exported components with no permission checks.
9. **M9 — Insecure Data Storage**: secrets/tokens/PII in plaintext in local SQLite databases, shared preferences, or logs.
10. **M10 — Insufficient Cryptography**: weak/custom crypto instead of vetted platform APIs.

--> In practice, mobile testing splits into two phases: static analysis (pulling the app apart without running it) and dynamic analysis (watching it run and intercepting what it does). Both matter — static analysis finds hardcoded secrets and logic dead ends fast; dynamic analysis finds what actually happens on the wire and how server-side checks (or lack thereof) behave.

## APK Structure and Decompiling

--> An Android app ships as an `.apk` (Android Package) — really just a ZIP file containing compiled bytecode, resources, and manifest metadata.

```text
app.apk
├── AndroidManifest.xml     # declares permissions, components (activities/services/receivers), entry points
├── classes.dex             # compiled Java/Kotlin bytecode (Dalvik Executable format)
├── resources.arsc          # compiled resources (strings, layouts references)
├── res/                    # images, XML layouts, raw assets
├── assets/                 # arbitrary files bundled as-is — often where hardcoded config/secrets hide
└── lib/                    # native .so libraries (compiled C/C++, e.g. crypto or anti-tamper code)
```

==> apktool — unpacking resources and the manifest
```bash
apktool d app.apk -o app_decoded
```
--> This decodes `AndroidManifest.xml` back into readable XML and disassembles `classes.dex` into Smali (a human-readable intermediate assembly-like representation of Dalvik bytecode) — useful for reading permissions declared, exported components, and for PATCHING/rebuilding the app (e.g. to strip a root-detection check) via `apktool b`.

==> jadx — decompiling straight to readable Java
```bash
jadx -d app_decompiled app.apk
# or launch the GUI:
jadx-gui app.apk
```
--> jadx converts the DEX bytecode back into approximate, readable Java source (not always perfectly valid, but close enough to read logic) — this is the faster path for actually understanding app LOGIC (how a login flow works, how a license check works) rather than just its manifest.

--> Finding hardcoded secrets — once decompiled, this is straightforward `grep`/search work:
```bash
grep -riE "(api[_-]?key|secret|password|token|aws_access|firebase)" -r app_decompiled/ --include=*.java
grep -riE "https?://[a-z0-9.-]+" -r app_decompiled/ --include=*.java   # hardcoded endpoints, often staging/internal ones
```
--> Also check `assets/` and `res/values/strings.xml` directly — API keys, backend URLs, and even signing certs get left in there embarrassingly often. Static analysis of a single APK has turned up production database credentials, hardcoded admin backdoor passwords, and internal-only API endpoints more than once in real bug bounty writeups — this is a genuinely high-value, low-effort first step.

## Intercepting Mobile App Traffic

--> Setup is conceptually identical to note 14's Burp proxy setup, with one extra wrinkle: getting the DEVICE (physical or emulator) to trust and route through your proxy.
1. Point the device's Wi-Fi proxy settings (or use an emulator's proxy flag) at your Burp instance's IP:port.
2. Install Burp's CA cert onto the device — on modern Android (7+), apps by default only trust SYSTEM-installed CAs for their own network security config, so a user-installed CA cert (the easy path) is often silently ignored by the app even though the OS "shows" it as trusted for browsers.
3. That's `M5`/`M9`-adjacent hardening working as intended from the app's perspective — which is exactly why cert pinning bypass tooling exists.

==> Certificate pinning — what it is, and the bypass concept
--> Certificate/public-key pinning means the app hardcodes (pins) the expected server certificate or public key and refuses to trust ANY other certificate, even one signed by a CA the OS trusts — this defeats the "install Burp's CA cert" trick entirely, because Burp's dynamically generated per-site cert is a DIFFERENT public key than the one pinned in the app.
--> Bypass approach (conceptual, for authorized testing): use a dynamic instrumentation framework to hook into the app's running process and PATCH OUT the pinning check itself at runtime, rather than trying to forge a certificate.
- **Frida**: a dynamic instrumentation toolkit that injects a JavaScript engine into the target process, letting you hook and override any function call — including the specific `X509TrustManager.checkServerTrusted()` (Android) or `SSLPinningDelegate` (iOS) method that performs the pin comparison, forcing it to always return "trusted."
- **Objection**: a Frida-powered CLI wrapper purpose-built for mobile security testing that ships common recipes as one-liners, notably:
```bash
objection -g com.example.targetapp explore
# once in the objection shell:
android sslpinning disable
```
--> This single command attaches Frida, finds common pinning implementations (OkHttp's `CertificatePinner`, TrustManager overrides, etc.), and patches them at runtime — no APK rebuild needed, no need to even have the app's source. Once disabled, standard Burp proxy interception works exactly as it would on an unprotected app.

--> The deeper lesson: pinning raises the bar (you now need root/jailbreak + Frida instead of just installing a cert) but is NOT a substitute for real server-side authorization checks — everything downstream in the API Security section below still applies once you're past this layer, and often the pinning is the ONLY thing standing between "trivial to test" and "requires real reverse-engineering effort," which is exactly why bug bounty programs often explicitly call out whether pinning bypass is in scope.

---

## API Security Testing

--> Most modern apps (mobile AND single-page web apps) are thin UI shells over a REST/GraphQL API doing all the real work — which means the API itself, not the UI, is where the actual authorization and business logic live, and where the actual bugs are.

## Broken Object Level Authorization (BOLA)

--> BOLA (API Security Top 10's #1, and the API-specific evolution of the web IDOR concept from note 04) happens when an API endpoint fetches or modifies a resource by ID, but never verifies that the AUTHENTICATED caller is actually authorized to access THAT specific object — only that they're authenticated at all.

--> Worked example: a fitness-tracking app's API, intercepted via Burp per the setup above.
```http
GET /api/v2/users/8842/workouts HTTP/1.1
Host: api.fitnessapp.example.com
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```
--> `8842` is YOUR user ID, embedded because the mobile client fetched your own profile ID at login and uses it in subsequent calls. The server presumably checks "is this bearer token valid" — but does it also check "does this bearer token's owner actually equal user 8842"?
--> Test in Repeater: swap the ID and resend with your OWN valid token.
```http
GET /api/v2/users/8843/workouts HTTP/1.1
Host: api.fitnessapp.example.com
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```
--> If this returns `200 OK` with user 8843's private workout history (location data, heart rate, etc.) instead of a `403 Forbidden`, that's a confirmed BOLA — full unauthorized access to any other user's data by simply enumerating IDs. This exact bug class has affected numerous real fitness, dating, and social apps at scale, because sequential/GUID-but-still-fetchable IDs make enumeration trivial.
--> Fix: every object-fetching endpoint must independently verify server-side that `token.owner_id == requested_resource.owner_id` (or that the caller has an explicit grant/role permitting access to that specific object) — never rely on the ID simply being "hard to guess" (GUIDs help against blind guessing but do nothing against an attacker who legitimately obtained one ID and is testing adjacent ones, or against BOLA discovered via other users' shared links).

## Rate Limiting Bypass

--> Rate limiting caps how many requests a client can make in a time window — critical for preventing brute-force login/OTP attacks, scraping, and API abuse. Testing whether it's actually EFFECTIVE (not just present) is its own skill.

--> Common bypass techniques to test:
- **IP rotation** — if rate limiting keys off source IP only, requests routed through a rotating proxy pool or Tor circuits each look like a "new" client.
- **Header spoofing** — many rate limiters trust `X-Forwarded-For` or `X-Real-IP` at face value (because the app sits behind a load balancer that's supposed to set it) without validating it came from the actual trusted proxy; sending a fabricated `X-Forwarded-For: 1.2.3.4` that changes on every request can reset the counter.
```http
POST /api/login HTTP/1.1
Host: target.example.com
X-Forwarded-For: 203.0.113.7

username=victim&password=guess1
```
- **Endpoint/case variation** — some naive implementations key the limit off the exact literal path string; `/api/login`, `/API/login`, `/api/login/`, or `/api/login?` can sometimes bypass a limiter that isn't normalizing the path before checking.
- **Race conditions** — firing many requests in a very tight burst (using Intruder's "null" payload type with high concurrency, or a custom script) before the counter has a chance to increment/persist, particularly against limiters backed by an eventually-consistent store.
- **Account-scoped vs IP-scoped confusion** — if limiting is per-account but the attacker is credential-stuffing across MANY different accounts, an IP-only-blind limiter never triggers even though the aggregate request volume is huge.

--> Testing approach in Burp: send the target request to Intruder, set the payload position to something harmless/reused, and fire a burst of 50-100 requests while varying only the `X-Forwarded-For` header (or none at all as a control) — compare how many succeed before you see a `429 Too Many Requests`.

## JWT Attacks

--> JSON Web Tokens are a common bearer-token format for stateless API auth — a base64url-encoded header, payload (claims), and cryptographic signature, joined by dots: `header.payload.signature`.

==> Decoding and inspecting a JWT (no library needed for the read side)
```python
import base64, json

def decode_jwt_part(part: str) -> dict:
    # JWT base64url has no padding; pad it back out before decoding
    padded = part + "=" * (-len(part) % 4)
    return json.loads(base64.urlsafe_b64decode(padded))

token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyIjoiYWxpY2UiLCJyb2xlIjoidXNlciJ9.abc123signature"
header_b64, payload_b64, signature = token.split(".")

print(decode_jwt_part(header_b64))   # {'alg': 'HS256', 'typ': 'JWT'}
print(decode_jwt_part(payload_b64))  # {'user': 'alice', 'role': 'user'}
```
--> The header and payload are just base64 — NOT encrypted, only encoded. Anyone who intercepts a JWT can read every claim inside it instantly; the signature is what's supposed to prevent TAMPERING, not reading.

==> Attack 1 — `alg=none`
--> Some JWT libraries, if misconfigured or older, will accept a token whose header declares `"alg": "none"` and skip signature verification entirely, since "none" is technically a valid (if useless) algorithm in the JWS spec.
```python
import base64, json

def b64url_encode(data: dict) -> str:
    raw = json.dumps(data, separators=(",", ":")).encode()
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode()

header = {"alg": "none", "typ": "JWT"}
payload = {"user": "alice", "role": "admin"}   # escalate role, tamper freely

forged_token = f"{b64url_encode(header)}.{b64url_encode(payload)}."   # trailing dot, empty signature
print(forged_token)
```
--> If the server's verification code is something like "if alg is none, skip signature check" (a real historical bug class across multiple JWT libraries), this forged token with `role: admin` and NO valid signature at all gets accepted.

==> Attack 2 — weak/brute-forceable HMAC secret
--> HS256-signed JWTs use a single shared secret string for both signing and verification. If that secret is weak/short/guessable (a common default like `"secret"`, a leaked value from a public GitHub repo, or a low-entropy string), it can be brute-forced OFFLINE against a captured valid token — no rate limiting applies since it's happening entirely on the attacker's machine.
```bash
# hashcat mode 16500 targets JWT HS256 cracking directly
hashcat -a 0 -m 16500 captured_jwt.txt rockyou.txt
```
--> Once the secret is recovered, the attacker can sign ANY payload they want (e.g. `role: admin`, arbitrary `user` claim) with a perfectly valid signature — full impersonation of any account. `jwt_tool` (a dedicated Python CLI) automates both this attack and the `alg=none` one, plus several other JWT-specific checks (algorithm confusion between RS256/HS256, `kid` header injection/path traversal, `jku`/`x5u` header URL manipulation).

--> Fix, all JWT issues: use a well-maintained library configured to enforce ONE explicit expected algorithm (reject `none` and reject unexpected algorithm switches outright — never trust the `alg` header value from the token itself to decide how to verify it), use a cryptographically random secret of adequate length (32+ bytes for HMAC, or better, use asymmetric RS256/ES256 so the verifying party only ever needs the PUBLIC key), set short expiries (`exp` claim) and validate them server-side on every request, and never put highly sensitive data (raw PII, secrets) in the payload since it's always readable by anyone holding the token.
