/**
 * 01_oauth2_fundamentals_and_grant_types.java
 *
 * Demonstrates, with illustrative code and comments:
 *     1. The four OAuth2 roles (Resource Owner, Client, Authorization Server,
 *        Resource Server) modeled conceptually as plain classes/comments --
 *        OAuth2 itself is a protocol/choreography, not a library API, so this
 *        section is deliberately conceptual rather than a working call.
 *     2. An illustrative walkthrough of the Authorization Code flow -- the
 *        redirect to /authorize, the callback with an authorization code,
 *        and the backend's server-to-server exchange of that code for tokens.
 *     3. A GENUINELY RUNNABLE PKCE (Proof Key for Code Exchange) example --
 *        generating a code_verifier and deriving its code_challenge using
 *        only java.security.SecureRandom and java.security.MessageDigest,
 *        no external OAuth2/HTTP libraries required.
 *
 * Covers Theory chapter:
 *     16) Advanced Spring Security OAuth2 and JWT/Theory/01 OAuth2 Fundamentals and Grant Types.md
 *
 * IMPORTANT -- this file is illustrative, NOT a standalone runnable program AS A WHOLE.
 * Section 1 and Section 2 are conceptual illustrations of a protocol choreography
 * that involves a real browser, a real Authorization Server, and real HTTP calls --
 * they cannot be "run" as Java code in isolation, and their methods either return
 * canned illustrative values or throw UnsupportedOperationException to make that
 * explicit. Section 3 (PKCE generation) is the exception: it uses only the JDK's
 * own java.security APIs and its logic DOES compile and run standalone. To try
 * it yourself, copy Section 3 (PkceGenerator) plus a small main() into a file
 * named to match a public class, e.g. PkceDemo.java, then:
 *
 *     javac PkceDemo.java
 *     java PkceDemo
 *
 * (This file itself follows this Topic Practicals series' convention of using
 * package-private, non-public top-level classes -- see the sibling example at
 * "09) Spring and Spring Boot/Topic Practicals/05_spring_security_demo.java" --
 * so it is not compiled as a single standalone public-class file.)
 *
 * A full end-to-end OAuth2 flow additionally requires:
 *     - A real Authorization Server (Keycloak, Auth0, Okta, or Spring's own
 *       spring-security-oauth2-authorization-server) to actually issue codes/tokens
 *     - An HTTP client to perform the real redirects and the /token exchange call
 *     - spring-boot-starter-oauth2-client if wiring this into a Spring Boot app
 *       (see Topic Practicals file 03 for that side of things)
 */

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// =============================================================================
// SECTION 1 -- The Four OAuth2 Roles, Modeled Conceptually
// =============================================================================
// OAuth2 is NOT a library you import -- it's a protocol describing how four
// parties interact. These classes exist purely to pin down, in code comments,
// what each role IS and is NOT responsible for. Nothing here performs real I/O.

/**
 * The end user. Owns the protected data (e.g. their Google Contacts) and is
 * the only party who can grant or deny consent for a Client to access it.
 * The Resource Owner authenticates DIRECTLY with the Authorization Server --
 * the Client never sees their password.
 */
class ResourceOwner {
    private final String username;

    ResourceOwner(String username) {
        this.username = username;
    }

    /** Illustrative only -- in reality this happens on the Authorization
     *  Server's own hosted login/consent page, in the user's browser, not
     *  inside the Client's code at all. */
    boolean approveConsent(List<String> requestedScopes) {
        System.out.println(username + " is asked to approve scopes: " + requestedScopes);
        return true; // the user clicks "Allow"
    }
}

/**
 * The third-party application wanting access to the protected resource.
 * Never sees the Resource Owner's actual password -- it only ever receives
 * a narrowly-scoped, revocable TOKEN.
 */
class OAuth2Client {
    final String clientId;
    final String clientSecret;      // confidential clients only -- public clients (SPA/mobile) have none
    final String redirectUri;

    OAuth2Client(String clientId, String clientSecret, String redirectUri) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
    }
}

/**
 * Authenticates the Resource Owner, obtains their consent, and issues tokens.
 * Real-world examples: Keycloak, Auth0, Okta, Google, or a self-hosted
 * Spring Authorization Server. Owns the /authorize and /token endpoints.
 */
class AuthorizationServer {

    /** Step 3 of the Authorization Code flow (see Section 2) -- illustrative only. */
    String issueAuthorizationCode(OAuth2Client client, ResourceOwner owner, List<String> scopes) {
        if (!owner.approveConsent(scopes)) {
            throw new IllegalStateException("Resource owner denied consent");
        }
        // A real Authorization Server generates a short-lived, single-use,
        // cryptographically random code and associates it server-side with
        // the client, the granted scopes, and (if PKCE was used) the
        // code_challenge presented in step 1.
        return "auth-code-" + UUID.randomUUID();
    }

    /** Step 5 of the Authorization Code flow -- illustrative only; a real
     *  implementation validates the code, the client secret/PKCE verifier,
     *  and returns real signed JWTs (see Topic Practicals file 02). */
    Map<String, String> exchangeCodeForTokens(String authorizationCode, String clientSecretOrVerifier) {
        throw new UnsupportedOperationException(
                "Illustrative only -- a real Authorization Server validates the code + secret/verifier " +
                "server-side and returns real access_token/refresh_token JSON from its /token endpoint.");
    }
}

/**
 * The API that actually holds the protected data. Accepts a bearer token on
 * incoming requests and serves data only if the token is valid AND
 * sufficiently scoped for the requested operation.
 */
class ResourceServer {

    /** Illustrative only -- a real Resource Server validates the token's
     *  signature, expiry, issuer, and audience before this ever runs (see
     *  Topic Practicals file 03's oauth2ResourceServer() configuration). */
    String serveProtectedResource(String bearerToken, String requiredScope) {
        throw new UnsupportedOperationException(
                "Illustrative only -- see Topic Practicals file 03 for a real, working " +
                "resource-server token-validation configuration.");
    }
}

// =============================================================================
// SECTION 2 -- Authorization Code Flow, Illustrated Step by Step
// =============================================================================
// This models the redirect-based choreography from Theory file 01. It cannot
// really "run" here -- steps 1-3 involve an actual browser and an actual
// Authorization Server's hosted login page -- so each step is represented as
// a small illustrative method with comments explaining what really happens
// over the wire at that point.

class AuthorizationCodeFlowIllustration {

    /**
     * Step 1 -- the CLIENT (its backend, before redirecting the browser)
     * builds the /authorize URL. In a real app this is a browser redirect
     * (HTTP 302), not a Java method call -- shown here as a String-building
     * illustration of exactly what parameters that redirect must carry.
     */
    static String buildAuthorizeRedirectUrl(OAuth2Client client, List<String> scopes,
                                             String state, String codeChallenge) {
        String scopeParam = String.join("%20", scopes); // space-separated, URL-encoded
        return "https://auth-server.example.com/authorize"
                + "?response_type=code"
                + "&client_id=" + client.clientId
                + "&redirect_uri=" + client.redirectUri
                + "&scope=" + scopeParam
                + "&state=" + state                              // CSRF defense for the flow itself -- must be verified on callback
                + "&code_challenge=" + codeChallenge              // PKCE -- see Section 3
                + "&code_challenge_method=S256";
        // The browser is redirected to this URL. The user logs in and
        // consents DIRECTLY on the Authorization Server's own page --
        // the Client's code never runs during that part at all.
    }

    /**
     * Step 3 -- the Authorization Server redirects the browser BACK to the
     * Client's redirect_uri with an authorization code and the original
     * "state" value. This method illustrates the CLIENT'S callback handler
     * receiving that redirect (e.g. a @GetMapping("/callback") in Spring).
     */
    static void handleCallback(String returnedState, String expectedState, String authorizationCode) {
        if (!returnedState.equals(expectedState)) {
            // Without this check, an attacker could trick a victim's browser
            // into completing a flow the attacker initiated -- the whole
            // point of the "state" parameter (Theory file 01).
            throw new SecurityException("state mismatch -- possible CSRF on the OAuth2 flow, aborting");
        }
        System.out.println("Received authorization code: " + authorizationCode
                + " -- now exchanging it server-to-server for tokens (step 4/5)");
    }

    /**
     * Step 4/5 -- the CLIENT'S BACKEND (never the browser) exchanges the code
     * for tokens by calling the Authorization Server's /token endpoint
     * directly, presenting either its client_secret (confidential client) or
     * its PKCE code_verifier (public client) as proof it's the same client
     * that started the flow in step 1.
     */
    static Map<String, String> illustrativeTokenExchangeRequestBody(
            String authorizationCode, OAuth2Client client, String codeVerifier) {
        return Map.of(
                "grant_type", "authorization_code",
                "code", authorizationCode,
                "redirect_uri", client.redirectUri,
                "client_id", client.clientId,
                // A confidential client sends client_secret here instead of/alongside code_verifier.
                "code_verifier", codeVerifier
        );
        // A real client would POST this body to https://auth-server.example.com/token
        // and receive back {"access_token": "...", "refresh_token": "...", "expires_in": 900, ...}
    }
}

// =============================================================================
// SECTION 3 -- PKCE: Genuinely Runnable, No External Dependencies
// =============================================================================
// Unlike Sections 1 and 2, this part performs real cryptographic work using
// only java.security -- it is a faithful, standalone-runnable implementation
// of the code_verifier / code_challenge generation Theory file 01 describes.

class PkceGenerator {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    /**
     * Generates a cryptographically random code_verifier: 43-128 characters
     * from the unreserved URL-safe alphabet, per RFC 7636. We generate 32
     * random bytes (256 bits of entropy) and base64url-encode them, which
     * yields a 43-character string -- comfortably within the required range.
     */
    static String generateCodeVerifier() {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        return URL_ENCODER.encodeToString(randomBytes);
    }

    /**
     * Derives the code_challenge from a code_verifier using the S256 method:
     * code_challenge = BASE64URL( SHA256(code_verifier) ).
     * The verifier itself is NEVER sent in step 1 -- only this derived
     * challenge is, so an eavesdropper on the /authorize redirect gains
     * nothing usable to complete the token exchange later.
     */
    static String deriveCodeChallengeS256(String codeVerifier) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] digest = sha256.digest(codeVerifier.getBytes("US-ASCII"));
            return URL_ENCODER.encodeToString(digest);
        } catch (NoSuchAlgorithmException | java.io.UnsupportedEncodingException e) {
            // SHA-256 and US-ASCII are both guaranteed available on every JVM,
            // so this branch is unreachable in practice -- wrapped to keep the
            // public method signature checked-exception-free.
            throw new IllegalStateException("Unexpected: SHA-256/US-ASCII unavailable", e);
        }
    }

    /**
     * What the AUTHORIZATION SERVER does at token-exchange time (step 4/5):
     * independently re-derive the challenge from the verifier the client just
     * revealed, and check it matches the challenge it was given back in step 1.
     * If they match, this proves the token-exchange request came from the
     * SAME client instance that started the flow -- without either party ever
     * transmitting a long-lived secret.
     */
    static boolean serverSideVerification(String presentedCodeVerifier, String storedCodeChallenge) {
        String recomputedChallenge = deriveCodeChallengeS256(presentedCodeVerifier);
        return recomputedChallenge.equals(storedCodeChallenge);
    }
}

// =============================================================================
// Runnable entry point -- exercises Section 3 for real, and prints an
// illustrative trace of Sections 1 and 2 without invoking their
// UnsupportedOperationException-throwing methods.
// =============================================================================
class Oauth2FundamentalsAndGrantTypes {

    public static void main(String[] args) {
        System.out.println("=== Section 1/2: conceptual roles + Authorization Code flow (illustration only) ===");
        OAuth2Client client = new OAuth2Client("my-web-app", null, "https://app.example.com/callback");
        String state = UUID.randomUUID().toString();

        // --- PKCE happens FIRST, before step 1, so the challenge is ready to include in the redirect ---
        String codeVerifier = PkceGenerator.generateCodeVerifier();
        String codeChallenge = PkceGenerator.deriveCodeChallengeS256(codeVerifier);

        String authorizeUrl = AuthorizationCodeFlowIllustration.buildAuthorizeRedirectUrl(
                client, List.of("openid", "profile", "read:contacts"), state, codeChallenge);
        System.out.println("Step 1 -- browser would be redirected to:\n  " + authorizeUrl);

        // Steps 2-3 happen in the user's browser against a real Authorization Server;
        // we simulate the callback the Client's backend would receive afterward.
        String simulatedAuthCode = "auth-code-" + UUID.randomUUID();
        AuthorizationCodeFlowIllustration.handleCallback(state, state, simulatedAuthCode);

        Map<String, String> tokenRequestBody = AuthorizationCodeFlowIllustration
                .illustrativeTokenExchangeRequestBody(simulatedAuthCode, client, codeVerifier);
        System.out.println("Step 4 -- backend would POST this body to /token:\n  " + tokenRequestBody);

        System.out.println();
        System.out.println("=== Section 3: PKCE verifier/challenge -- genuinely computed above, now verified ===");
        System.out.println("code_verifier  = " + codeVerifier);
        System.out.println("code_challenge = " + codeChallenge);

        // Simulate the Authorization Server's own check at token-exchange time.
        boolean valid = PkceGenerator.serverSideVerification(codeVerifier, codeChallenge);
        System.out.println("Authorization Server's recomputed-challenge check passes: " + valid);

        // And what happens if an attacker tries to exchange the code with a DIFFERENT verifier
        // (e.g. because they intercepted only the code, not the original verifier):
        String attackerGuess = PkceGenerator.generateCodeVerifier(); // a different random value
        boolean attackerValid = PkceGenerator.serverSideVerification(attackerGuess, codeChallenge);
        System.out.println("An attacker's guessed verifier passes the same check: " + attackerValid
                + "  (this is exactly what PKCE prevents)");
    }
}

/*
 * NOTE on running this file:
 * All the logic above is plain JDK code with no external dependencies --
 * Sections 1 and 2 never actually perform network I/O (their "sensitive"
 * methods either print illustrative output or throw
 * UnsupportedOperationException to make clear they are NOT real
 * implementations of a live protocol exchange), while Section 3's PKCE code
 * is fully real, working cryptography using only
 * java.security.SecureRandom and java.security.MessageDigest -- copy it into
 * its own public-class file (as described in the header) to compile and run
 * it standalone and see real verifier/challenge values printed. A real
 * OAuth2 integration replaces Sections 1 and 2 with actual HTTP calls (a
 * browser redirect, a server-to-server POST to a real Authorization Server's
 * /token endpoint) -- see Topic Practicals file 03 for how Spring Security's
 * oauth2Client()/oauth2ResourceServer() wire this into a real Spring Boot
 * application declaratively.
 */
