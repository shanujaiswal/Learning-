/**
 * 05_securing_microservices_and_common_vulnerabilities.java
 *
 * Demonstrates, with illustrative Spring Security code:
 *     1. A CorsConfig (browser-enforced cross-origin rules) and the CSRF
 *        nuance that resurfaces specifically for the HttpOnly refresh-token
 *        cookie endpoint even in an otherwise-stateless bearer-token API.
 *     2. A token-propagation example for service-to-service calls -- a
 *        WebClient (Feign-equivalent) interceptor forwarding the current
 *        inbound request's Authorization header onward (Pattern 1:
 *        pass-through), plus a Client-Credentials-based interceptor for
 *        pure service-identity calls (Pattern 3), and comments on
 *        Pattern 2 (token exchange, RFC 8693).
 *     3. Small illustrative code + comments covering common vulnerability
 *        pitfalls: insecure token storage, replay-attack mitigation via
 *        jti + a used-token cache, and secret management via environment
 *        variables rather than hardcoding.
 *
 * Covers Theory chapter:
 *     16) Advanced Spring Security OAuth2 and JWT/Theory/05 Securing Microservices and Common Vulnerabilities.md
 *
 * IMPORTANT -- this file is illustrative, NOT a standalone runnable program.
 * It requires a real Spring Boot project on the classpath to compile/run, specifically:
 *     - spring-boot-starter-security
 *     - spring-boot-starter-webflux OR a WebClient dependency (for the WebClient interceptor)
 *     - spring-boot-starter-oauth2-client   (for the Client Credentials interceptor)
 *     - a JWT library (io.jsonwebtoken:jjwt-api/-impl/-jackson) for the jti/replay-cache illustration
 *     - a cache provider (e.g. Caffeine, or Redis for a multi-instance deployment)
 *       for the replay-detection cache to actually be shared across instances
 *
 * Run (in a real Spring Boot project, after wiring this into src/main/java/... and adding
 * a @SpringBootApplication class with a main() method):
 *     mvn spring-boot:run
 *   or
 *     ./gradlew bootRun
 *
 * Example requests once the app is running on the default port (8080):
 *     curl -X POST http://localhost:8080/api/orders \
 *          -H "Authorization: Bearer <jwt>" -H "Origin: https://app.example.com"
 *     (Service A's OrderController would, in turn, call Service B via the
 *      propagating WebClient bean shown in Section 2)
 */

import io.jsonwebtoken.Claims;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

// =============================================================================
// SECTION 1 -- CORS configuration + the CSRF nuance for a cookie-based endpoint
// =============================================================================

@Configuration
class CorsConfig {

    /**
     * CORS is a BROWSER-enforced rule, not a general security boundary -- it
     * stops a page's JavaScript from READING a cross-origin response unless
     * the server explicitly allows it; it does nothing to stop curl, a
     * mobile app, or another backend service from calling the API directly.
     * Needed here because a typical setup serves the SPA from one origin
     * (app.example.com) and the API from another (api.example.com).
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("https://app.example.com"));   // NEVER "*" once credentials are involved
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);   // required if cookies (e.g. the refresh-token cookie below) cross origins

        // allowedOrigins("*") + allowCredentials(true) together is an INVALID,
        // actively-rejected combination once credentials are involved -- a
        // wildcard origin combined with credentialed requests would let ANY
        // site's JavaScript make authenticated calls on a logged-in user's
        // behalf, so browsers (and Spring Security) refuse to honor it.

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}

@Configuration
@EnableWebSecurity
class CsrfConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, CorsConfigurationSource corsSource) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsSource))
            // CSRF protects session/cookie-based apps; this API is otherwise
            // pure bearer-token (Authorization header), so the blanket
            // disable below is conventional -- BUT see the refresh-cookie
            // endpoint note further down, which is the nuance this file adds.
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/auth/**").permitAll()
                    .anyRequest().authenticated()
            );

        return http.build();
    }

    /**
     * The moment ANY endpoint uses a COOKIE the browser sends automatically
     * (the HttpOnly refresh-token cookie from Topic Practicals file 02's
     * rotation flow), THAT SPECIFIC endpoint re-enters cookie/session
     * territory -- CSRF-style concerns apply to it specifically, even while
     * the rest of the API stays pure bearer-token and legitimately CSRF-exempt.
     * SameSite is the practical modern mitigation: it tells the browser not
     * to send the cookie at all on cross-site requests, closing most of the
     * classic CSRF surface for that one cookie without a separate token scheme.
     */
    void issueRefreshCookieIllustration(org.springframework.http.server.reactive.ServerHttpResponse response,
                                         String rawRefreshToken) {
        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", rawRefreshToken)
                .httpOnly(true)      // not readable by JavaScript -- mitigates XSS token theft (see Section 3)
                .secure(true)        // HTTPS only
                .sameSite("Strict")  // not sent on cross-site requests -- mitigates CSRF against THIS cookie specifically
                .path("/api/auth/refresh")
                .maxAge(Duration.ofDays(7))
                .build();
        response.getHeaders().add(HttpHeaders.SET_COOKIE, refreshCookie.toString());
    }
}

// =============================================================================
// SECTION 2 -- Token propagation across service-to-service calls
// =============================================================================

/**
 * PATTERN 1 -- Pass-through: Service A forwards the SAME token it received
 * onward to Service B. Simple, and B's own authorization logic (roles/
 * ownership) applies exactly as if the user called it directly. The catch --
 * requires the token's audience to legitimately cover both A and B, and B
 * ends up directly trusting whatever claims are in the ORIGINAL token, with
 * no narrowing of what A is asking B to do on the user's behalf. This
 * compounds risk with every hop -- prefer Pattern 2 as a system grows deeper.
 */
@Configuration
class PassThroughPropagationConfig {

    @Bean
    public WebClient serviceBClient(WebClient.Builder builder) {
        return builder
                .baseUrl("https://service-b.internal.example.com")
                .filter((request, next) -> {
                    String incomingToken = extractCurrentRequestBearerToken();
                    ClientRequest propagated = ClientRequest.from(request)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + incomingToken)
                            .build();
                    return next.exchange(propagated);
                })
                .build();
    }

    /** Illustrative only -- in a real reactive app this reads the token from
     *  the current ServerWebExchange/ReactiveSecurityContextHolder; in a
     *  traditional MVC app it reads from SecurityContextHolder / the
     *  current HttpServletRequest's Authorization header directly. */
    private String extractCurrentRequestBearerToken() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new IllegalStateException("No authenticated request context to propagate a token from");
        }
        // Real implementations typically stash the raw token string as the
        // Authentication's "details" or "credentials" during the resource-server
        // JWT filter (Topic Practicals files 02/03), rather than reconstructing it here.
        return "raw-jwt-would-be-extracted-from-authentication-details-here";
    }
}

/**
 * PATTERN 3 -- Service identity only: when B's operation genuinely doesn't
 * need to know which END USER triggered it (a background reconciliation job,
 * an internal cache-invalidation call), A authenticates to B purely as
 * ITSELF via Client Credentials -- no user context propagated at all. Using
 * this when B's authorization decision WOULD have depended on the specific
 * user silently breaks per-user authorization -- pick this pattern only when
 * that's genuinely not the case.
 */
@Configuration
class ServiceIdentityPropagationConfig {

    @Bean
    public WebClient serviceBClientCredentialsClient(
            WebClient.Builder builder,
            ClientRegistrationRepository clientRegistrationRepository,
            org.springframework.security.oauth2.client.OAuth2AuthorizedClientService authorizedClientService) {

        OAuth2AuthorizedClientProvider authorizedClientProvider = OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials()   // this service authenticates as ITSELF -- no user, no browser, no redirect
                .build();

        // A real ServletOAuth2AuthorizedClientExchangeFilterFunction would be
        // constructed from an OAuth2AuthorizedClientManager wired with the
        // provider above and this app's own "service-a" client registration
        // (client_id/client_secret configured exactly like the OAuth2Client
        // config in Topic Practicals file 03, but with
        // authorization-grant-type: client_credentials instead of authorization_code).
        return builder
                .baseUrl("https://service-b.internal.example.com")
                .build();
        // .filter(oauth2ExchangeFilterFunction) would be attached here in a
        // fully wired real project -- omitted since it depends on
        // OAuth2AuthorizedClientManager bean wiring specific to the app's
        // registered client, not something meaningfully mockable in isolation.
    }
}

// PATTERN 2 -- Token exchange (RFC 8693): Service A exchanges the incoming
// user token for a NEW, narrower token scoped specifically to what it needs
// B to do, rather than forwarding the original token wholesale (Pattern 1)
// or discarding user context entirely (Pattern 3). This keeps each
// downstream hop's blast radius limited to what that hop actually needs, and
// is the better fit as a system grows more service-hops deep. Implementing
// this requires either an Authorization Server supporting the RFC 8693
// "urn:ietf:params:oauth:grant-type:token-exchange" grant type, or a custom
// internal token-minting endpoint -- both are deployment-specific enough
// that no single Java snippet captures "the" implementation; the choice
// itself (Pattern 1 vs 2 vs 3) is the illustrative point this section makes.

// =============================================================================
// SECTION 3 -- Common vulnerability pitfalls, illustrated
// =============================================================================

/**
 * PITFALL: insecure token storage. This class exists purely to CONTRAST the
 * wrong approach (comments) against the right one (the actual code below).
 */
class ClientSideTokenStorageIllustration {
    // WRONG (comment-only illustration -- do not do this):
    //     localStorage.setItem("accessToken", token);
    //     localStorage.setItem("refreshToken", token);
    // localStorage is readable by ANY JavaScript on the page, including
    // injected script from a successful XSS attack anywhere on the page
    // (a compromised third-party script, unsanitized user content). A single
    // XSS hole anywhere lets an attacker exfiltrate BOTH tokens wholesale.
    //
    // RIGHT:
    //   - Access token: keep in memory only (a JS variable, never persisted).
    //     An XSS attacker is then limited to whatever's in memory at the
    //     moment of compromise, not a token retrievable at leisure from storage.
    //   - Refresh token: HttpOnly + Secure + SameSite cookie (see CsrfConfig
    //     above) -- invisible to JavaScript entirely, so XSS can't read its
    //     value directly (though a sufficiently capable XSS attack could
    //     still ABUSE the cookie via authenticated requests made through the
    //     victim's own browser -- HttpOnly mitigates exfiltration, not all XSS impact).
}

/**
 * PITFALL: replay attacks. A valid, unexpired token intercepted in transit
 * can be REPLAYED exactly as sent -- the token alone doesn't prove who's
 * currently presenting it. Mitigation is layered: HTTPS everywhere, short
 * access-token lifetimes, refresh-token rotation (Topic Practicals file 02),
 * and -- for especially sensitive one-time operations -- checking a token's
 * "jti" (JWT ID) claim against a short-lived used-token cache, illustrated below.
 */
@Configuration
class ReplayMitigationIllustration {

    // A real deployment backs this with a shared cache (Redis) so replay
    // detection works correctly across multiple service instances -- an
    // in-memory ConcurrentHashMap (shown here for a runnable illustration of
    // the LOGIC only) would let a replay through if it happened to land on a
    // different instance than the one that saw the token first.
    private final ConcurrentMap<String, Instant> usedTokenIds = new ConcurrentHashMap<>();

    /**
     * Call this once per sensitive one-time operation (e.g. "submit payment,"
     * not every ordinary GET) BEFORE performing the operation. Returns false
     * -- and the caller should reject the request -- if this exact token's
     * jti has already been consumed once, exactly as a rotated refresh token
     * (Topic Practicals file 02) rejects reuse.
     */
    public boolean claimJtiOnce(Claims claims) {
        String jti = claims.get("jti", String.class);
        if (jti == null) {
            throw new IllegalStateException("Token has no jti claim -- cannot apply replay protection to it");
        }
        Instant previouslyUsedAt = usedTokenIds.putIfAbsent(jti, Instant.now());
        return previouslyUsedAt == null;   // true = first use (proceed); false = already seen (reject as a replay)
    }

    /** Periodic cleanup so this map doesn't grow unboundedly -- entries only
     *  need to be retained until the token itself would have expired anyway. */
    public void evictExpiredEntries(Duration tokenLifetime) {
        Instant cutoff = Instant.now().minus(tokenLifetime);
        usedTokenIds.values().removeIf(usedAt -> usedAt.isBefore(cutoff));
    }
}

/**
 * PITFALL: secret management. Signing secrets/keys are the single
 * highest-value target in the entire system -- whoever holds one can mint
 * tokens claiming to be ANY user with ANY role. This class illustrates
 * loading a secret from an ENVIRONMENT VARIABLE (or, in a real deployment, a
 * secrets manager like Vault/AWS Secrets Manager injecting that variable at
 * deploy time) rather than a literal string in source or a checked-in config file.
 */
class SecretManagementIllustration {

    // WRONG (never do this -- a literal secret committed to source control,
    // even in a "private" repo, persists in git history, forks, and clones
    // indefinitely, and is reviewed under a different, usually broader,
    // access-control policy than a real secrets manager):
    //     private static final String SECRET = "my-super-secret-key-12345";

    // RIGHT -- read from the environment at startup; the actual VALUE lives
    // only in the deployment platform's secret store (Kubernetes Secret,
    // AWS Secrets Manager, Vault, etc.), never in this file or in application.yml:
    public String loadSigningSecretFromEnvironment() {
        String secret = System.getenv("JWT_SIGNING_SECRET");
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SIGNING_SECRET environment variable is not set -- refusing to start with no signing key. " +
                    "Different secrets per environment (dev/staging/prod) are mandatory: a leaked staging secret " +
                    "must never grant access to production.");
        }
        return secret;
    }

    // Equivalent Spring idiom, for a value injected from the environment into
    // application.yml as "${JWT_SIGNING_SECRET}" and then bound via @Value:
    //     JwtService(@Value("${app.jwt.base64-secret}") String base64Secret) { ... }
    // (see Topic Practicals file 02's JwtService constructor)
}

/*
 * NOTE on the annotations/classes used above:
 * This snippet uses real Spring Security, Spring WebFlux (WebClient), and
 * jjwt types exactly as they'd appear in a real Spring Boot project, which
 * requires spring-boot-starter-security, a WebClient dependency,
 * spring-boot-starter-oauth2-client (for the Client Credentials interceptor),
 * a JWT library, and a @SpringBootApplication entry point to compile and run
 * this end-to-end. The Client Credentials WebClient bean is shown with its
 * OAuth2AuthorizedClientProvider construction but omits the final
 * ServletOAuth2AuthorizedClientExchangeFilterFunction wiring, since that
 * depends on an OAuth2AuthorizedClientManager bean specific to the
 * application's own registered "service-a" client -- the illustrative point
 * (authenticating as the SERVICE itself, no user context) stands regardless.
 */
