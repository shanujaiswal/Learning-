# From One Service to Many -- What Changes

--> Everything in the earlier files (Theory 01-04) largely assumed a single service handling one request end-to-end. Once a system splits into MICROSERVICES, new questions appear that don't exist in a monolith: how does Service A prove to Service B who's calling, on whose behalf, and how does that identity survive across three or four hops? This file covers that propagation problem, the CSRF/CORS configuration questions that resurface at each service's edge, and a working inventory of the vulnerability classes that show up disproportionately often in real token-based systems.

# CORS -- A Browser-Enforced Rule, Not a Server Security Feature

--> **What CORS actually is** -- Cross-Origin Resource Sharing is a BROWSER mechanism that restricts which origins (scheme + host + port) a page's JavaScript can successfully read responses from, when calling a DIFFERENT origin than the page itself was served from. This is fundamentally different from CSRF protection (below) -- CORS doesn't stop a request from being SENT, it stops the browser from letting the calling page's JavaScript READ the response, unless the server explicitly says it's fine via response headers.
--> **Why a microservices frontend needs this configured at all** -- a typical setup serves the frontend SPA from one origin (`https://app.example.com`) and the API from another (`https://api.example.com`), which are different origins even though they're "the same product" to a user. Without explicit CORS configuration, the browser blocks the SPA's JavaScript from reading the API's responses entirely.

```java
@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("https://app.example.com"));   // NEVER "*" if credentials are involved
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);          // required if cookies (e.g. refresh token cookie) cross origins

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}

// Wired into the filter chain:
// http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
```

--> **`allowedOrigins("*")` combined with `allowCredentials(true)` is actively rejected by browsers, and rightly so** -- a wildcard origin combined with credentialed requests (cookies, `Authorization` headers treated as credentials in some setups) would let ANY site's JavaScript make authenticated calls on a logged-in user's behalf. Spring Security enforces this combination as invalid configuration; list explicit allowed origins whenever credentials are involved.
--> **CORS says nothing about whether a request is AUTHORIZED** -- it only controls whether a BROWSER lets a page's script read the response. A non-browser client (curl, a mobile app, another backend service) is entirely unaffected by CORS -- it's not a security boundary against server-to-server calls at all, only a browser-JS-to-cross-origin-server boundary.

# CSRF Revisited -- Why It Resurfaces Even for "Stateless" Systems

--> The basics file established the core rule: CSRF protection defends session/cookie-based apps and is conventionally disabled for pure bearer-token (`Authorization: Bearer <jwt>`) APIs, because there's no ambient credential a foreign page could piggyback on. **The nuance worth adding here** -- the moment ANY part of your auth flow uses a COOKIE the browser sends automatically (the HttpOnly refresh-token cookie from Theory file 02's rotation flow, for instance), that specific endpoint (the refresh endpoint) re-enters cookie/session territory and CSRF-style concerns apply to IT specifically, even while the rest of the API stays pure bearer-token and CSRF-exempt.
--> **`SameSite` cookie attribute as the practical modern mitigation** -- setting the refresh-token cookie's `SameSite=Strict` (or `Lax`) tells the browser not to send that cookie at all on cross-site requests, which closes most of the classic CSRF attack surface for that one cookie without needing a separate CSRF token scheme layered on top.

```java
ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", rawRefreshToken)
        .httpOnly(true)      // not readable by JavaScript -- mitigates XSS token theft
        .secure(true)        // HTTPS only
        .sameSite("Strict")  // not sent on cross-site requests -- mitigates CSRF against this cookie specifically
        .path("/api/auth/refresh")
        .maxAge(Duration.ofDays(7))
        .build();
response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
```

# Token Propagation -- Carrying Identity Across Service-to-Service Calls

--> Once Service A needs to call Service B to fulfill a user's request, the question becomes: does B need to know WHO the original user was, or just that A is a legitimate caller?

## Pattern 1 -- Pass-Through (Propagate the Original User's Token)

```text
Browser --[Bearer user-token]--> Service A --[Bearer SAME user-token]--> Service B
```

--> Service A forwards the SAME access token it received onward to Service B. Simple, and Service B's own authorization logic (roles/ownership) applies exactly as if the user called it directly. **The catch** -- this requires the token's `aud` (audience) to legitimately cover both A and B, or B must be configured to accept tokens where it isn't the sole intended audience, which weakens the audience-validation guarantee from Theory file 03. It also means B directly trusts whatever roles/claims are in the ORIGINAL token, with no opportunity for A to narrow what it's asking B to do on the user's behalf.

```java
// A minimal example of pass-through propagation using a Feign/WebClient interceptor:
@Bean
public WebClient serviceBClient(WebClient.Builder builder) {
    return builder
            .filter((request, next) -> {
                String incomingToken = extractCurrentRequestBearerToken();  // from the CURRENT inbound request
                ClientRequest propagated = ClientRequest.from(request)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + incomingToken)
                        .build();
                return next.exchange(propagated);
            })
            .build();
}
```

## Pattern 2 -- Token Exchange (Mint a NEW, Narrower Token)

--> Service A exchanges the incoming user token for a NEW token, scoped specifically to what it needs Service B to do, via the OAuth2 Token Exchange extension (RFC 8693) or a custom internal minting step. This keeps each downstream service's trust boundary narrower -- B sees a token that says "A is acting on behalf of user X, limited to scope Y," not the user's full original token with its full original scope.
--> **This is the better fit as a system grows more services deep** -- pass-through propagation compounds risk with each hop (any compromised service in the chain gets the FULL original token), while token exchange keeps each hop's blast radius limited to what that specific hop actually needs.

## Pattern 3 -- Service Identity Only (Client Credentials, No User Context)

```text
Service A --[Bearer service-A's-own-client-credentials-token]--> Service B
```

--> When B's operation genuinely doesn't need to know which END USER triggered it (a background reconciliation job, an internal cache-invalidation call), A authenticates to B purely as ITSELF via Client Credentials (Theory file 01) -- no user context propagated at all. If B's authorization decision would have depended on the specific user, this pattern is the WRONG fit -- it silently discards exactly the information an ownership/permission check (Theory file 04) would need.

| Pattern | Use when | Risk if used wrong |
|---|---|---|
| Pass-through | Simple systems, few hops, B's checks genuinely need the full original user context | Compounds exposure with every hop; audience validation gets murky |
| Token exchange | Multi-hop systems where each service should see only what it needs | More moving parts (exchange endpoint, RFC 8693 support) to set up correctly |
| Service identity only | B's operation has no user-specific authorization decision to make at all | Using this when B DOES need user context silently breaks per-user authorization |

# Common Vulnerabilities in Token-Based Systems

## Token Storage on the Client

--> **`localStorage` is readable by ANY JavaScript running on the page** -- including injected script from a successful XSS attack. Storing an access (or worse, refresh) token there means an XSS vulnerability ANYWHERE on the page (a compromised third-party script, an unsanitized user-generated content field) can exfiltrate the token wholesale.
--> **HttpOnly cookies are the standard mitigation for the REFRESH token specifically** -- `HttpOnly` makes the cookie invisible to JavaScript entirely, so XSS can't read it directly (though a sufficiently capable XSS attack could still ABUSE the cookie by making authenticated requests through the victim's own browser, even without reading its value -- HttpOnly mitigates exfiltration, not all XSS impact).
--> **The short-lived ACCESS token is the more debated case** -- many systems keep it in memory only (a JS variable, never persisted to storage at all), accepting that a full page reload requires a silent refresh via the HttpOnly refresh cookie. This limits an XSS attacker to whatever is in memory at the moment of compromise rather than a token retrievable at leisure from persistent storage.

## Replay Attacks

--> **The core risk** -- a valid, unexpired token intercepted in transit (a compromised network, a logged request, a browser extension) can be REPLAYED by an attacker exactly as sent, and the server has no inherent way to tell "the legitimate client sent this" from "an attacker is replaying a captured copy" -- the token alone doesn't prove who's currently presenting it.
--> **Mitigations, layered rather than any single one being sufficient** -- HTTPS everywhere (closes off network interception as the initial capture vector), short access-token lifetimes (shrinks the exploitable window), a `jti` (JWT ID) claim checked against a short-lived used-token cache for especially sensitive one-time operations, and binding tokens to additional context (a `cnf` confirmation claim tying a token to a specific TLS client certificate, in the stricter "sender-constrained tokens" / mTLS-bound-token pattern) for high-security systems.
--> **Refresh-token rotation (Theory file 02) is itself a replay defense specifically for refresh tokens** -- reuse of an already-rotated refresh token is detectable and treated as a compromise signal, which a bare unrotated refresh token has no equivalent protection against.

## Secret Management

--> **Signing secrets/keys are the single highest-value target in the entire system** -- whoever holds an HMAC secret (or an RSA private key) can mint tokens claiming to be ANY user with ANY role, completely bypassing every authorization check downstream. Treat these with commensurately higher care than ordinary application config.
--> **Never commit secrets to source control, even in a "private" repo** -- git history persists, forks/clones proliferate, and access review of a repo is a different (usually broader) control than access review of a secrets manager.
--> **Use a real secrets manager (Vault, AWS Secrets Manager, environment variables injected at deploy time from a secure store) rather than plain config files**, rotate signing keys periodically (RSA key rotation is straightforward via JWKS publishing multiple valid keys during a rotation window; HMAC secret rotation requires coordinating every holder of the shared secret simultaneously, another point in favor of RSA in multi-service systems per Theory file 02).
--> **Different secrets per environment** -- a leaked staging/dev secret should never grant access to production; sharing one secret "for simplicity" across environments means a lower-security environment's compromise becomes a production compromise too.

## Other Frequently-Seen Pitfalls

--> **The JWT `"alg": "none"` attack** -- some poorly-implemented JWT libraries historically accepted a token whose header claimed `"alg":"none"`, meaning NO signature verification happens at all, and an attacker can then freely forge any claims. Modern libraries (jjwt included) reject this by default, but ANY custom or hand-rolled parsing logic must explicitly refuse to honor the algorithm named in the token's own header without first confirming it matches what the SERVER expects to be verifying with -- never let the token dictate its own verification algorithm.
--> **Algorithm confusion (RS256 vs HS256)** -- a related historical attack: if a server is configured to accept EITHER RS256 or HS256 dynamically based on the token's own header, an attacker who obtains the RSA PUBLIC key (which, remember, is intentionally public / widely distributed) can craft an HS256 token, using the public key itself AS the HMAC secret -- and a vulnerable server verifying "whatever algorithm the token claims" ends up accepting it as validly HMAC-signed. Mitigation: pin the expected algorithm explicitly server-side (most modern libraries require you to specify accepted algorithms rather than trusting the token's header blindly) rather than accepting whatever the token claims to be signed with.
--> **Missing or overly permissive audience/issuer validation** -- covered in Theory file 03; worth re-flagging here as one of the most common real-world resource-server misconfigurations in multi-service systems specifically, where MULTIPLE valid issuers/audiences can exist and it's easy to validate too loosely "to make the multi-service case work."

# Common Gotchas

--> **Treating CORS as a security boundary against non-browser clients** -- it constrains browser JavaScript only; it does nothing to stop a direct API call from curl, a script, or another service.
--> **Setting `allowedOrigins("*")` alongside `allowCredentials(true)`** -- an invalid, actively-rejected combination once credentials are involved; list explicit origins instead.
--> **Blanket-disabling CSRF across an entire app because "we use JWT," while still using a cookie somewhere (like a refresh token)** -- the specific cookie-based endpoint still needs CSRF-aware handling (SameSite, or a token scheme) even if the rest of the API is legitimately exempt.
--> **Pass-through propagating a full user token across many service hops without narrowing it** -- compounds the blast radius of any single compromised service in the chain.
--> **Storing tokens in `localStorage` "because it's simpler"** -- trades convenience for full exposure to any XSS on the page; prefer in-memory access tokens plus an HttpOnly refresh cookie.
--> **Accepting the algorithm a token's own header claims without pinning an expected algorithm server-side** -- opens the door to `alg:none` and algorithm-confusion attacks.
--> **Sharing one signing secret across dev/staging/production** -- collapses environment isolation entirely for the single most valuable secret in the system.

# Best Practices Summary

--> Configure CORS with explicit allowed origins (never `*` with credentials), and remember it's a browser-only boundary, not a general access control.
--> Keep CSRF disabled for pure bearer-token endpoints, but apply `SameSite`/CSRF-aware handling specifically to any endpoint that still relies on an ambient cookie.
--> Choose a token-propagation pattern deliberately (pass-through, token exchange, or service-identity-only) based on how many hops deep the system goes and whether downstream services genuinely need user-level context.
--> Prefer in-memory access tokens plus an HttpOnly, Secure, SameSite refresh cookie over persistent browser storage for anything durable.
--> Layer replay defenses -- HTTPS, short-lived tokens, refresh-token rotation, and (for high-security needs) sender-constrained/mTLS-bound tokens -- rather than relying on any single one.
--> Manage signing secrets via a real secrets manager, rotate them periodically, and never share one secret across environments.
--> Pin the expected signing algorithm explicitly server-side rather than trusting whatever algorithm a token's own header claims.
--> Validate both issuer AND audience on every resource server, especially once multiple valid issuers/audiences exist in a multi-service system.
