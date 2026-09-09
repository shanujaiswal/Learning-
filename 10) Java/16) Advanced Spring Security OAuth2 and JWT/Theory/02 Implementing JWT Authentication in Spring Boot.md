# Beyond the Basics -- What This File Adds

--> Theory file 05 in the Spring and Spring Boot section ("Spring Security Basics") already introduced JWT structure and the stateless auth flow at a conceptual level, with a `JwtUtil` skeleton whose methods deliberately threw `UnsupportedOperationException`. THIS file goes the rest of the way -- actual signing algorithms (HMAC vs RSA, and when to choose which), a REAL working token-generation/validation implementation using the `jjwt` library, a properly wired `JwtAuthenticationFilter`, and a full refresh-token flow with rotation. If you haven't read that file yet, it's worth doing so first for the conceptual foundation (JWT's three segments, why statelessness matters, `SecurityContextHolder`) -- this file assumes that background and builds directly on top of it.

# Signing Algorithms -- HMAC (Symmetric) vs RSA (Asymmetric)

--> A JWT's signature is what makes it TAMPER-EVIDENT (not confidential -- the payload is still just base64-encoded, never encrypted). Which algorithm you sign with determines who CAN verify a token, and that choice has real architectural consequences once more than one service needs to check tokens.

| | HMAC (HS256 / HS384 / HS512) | RSA (RS256 / RS384 / RS512) |
|---|---|---|
| Key material | ONE shared secret, used for both signing and verifying | A PRIVATE key (signs) and a separate PUBLIC key (verifies) |
| Who can verify | Anyone holding the shared secret -- which means anyone who can verify can ALSO forge a valid signature | Anyone holding the public key -- but the public key alone cannot be used to forge a signature |
| Typical fit | A single service (or a tightly-controlled cluster of instances of the SAME service) that both issues and validates its own tokens | Multiple independent services need to VALIDATE tokens issued by ONE central authorization server, without being trusted to ISSUE tokens themselves |
| Key distribution | The secret must be distributed to every verifier -- and every verifier could then also forge tokens, which is a real risk in a multi-service system | Only the public key needs distributing (often auto-published at a JWKS endpoint, see below) -- the private key never leaves the issuing authority |
| Performance | Faster to compute | Slower, but rarely the bottleneck in practice |

--> **The core architectural question is "who needs to verify, and can they be trusted to also issue?"** -- in a monolith or a single auth service validating its own tokens, HMAC's shared secret is simple and sufficient. The moment MULTIPLE independent resource servers (Theory file 03) need to validate tokens issued by a central authorization server, HMAC becomes awkward: distributing the shared secret to every resource server means every one of those services could now also mint forged tokens for ANY user, which defeats the trust boundary between "issues tokens" and "merely checks them." RSA (or ECDSA) sidesteps this entirely -- resource servers get only the PUBLIC key, sufficient to verify but useless for forging.
--> **JWKS (JSON Web Key Set)** -- in RSA/asymmetric setups, the authorization server typically PUBLISHES its current public key(s) at a well-known HTTPS endpoint (conventionally `/.well-known/jwks.json`), letting resource servers fetch and cache the verification key automatically instead of it being manually distributed and rotated -- this is exactly the mechanism `spring-boot-starter-oauth2-resource-server` uses under the hood (Theory file 03) when you configure an `issuer-uri`.

# Generating a Real Token -- `jjwt` Library

--> The following uses `io.jsonwebtoken:jjwt-api`, `jjwt-impl`, and `jjwt-jackson` (the most common JWT library in the Spring ecosystem) to replace the `UnsupportedOperationException` skeleton from the basics file with an ACTUAL working implementation.

```java
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import java.util.Date;

public class JwtService {

    // A real 256-bit (32-byte) key -- loaded from config/secrets manager,
    // NEVER hardcoded. Keys.hmacShaKeyFor requires >= 256 bits for HS256.
    private final SecretKey signingKey;
    private static final long ACCESS_TOKEN_VALIDITY_MS = 15 * 60 * 1000;      // 15 minutes

    public JwtService(String base64Secret) {
        this.signingKey = Keys.hmacShaKeyFor(java.util.Base64.getDecoder().decode(base64Secret));
    }

    public String generateAccessToken(String username, java.util.List<String> roles) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + ACCESS_TOKEN_VALIDITY_MS);

        return Jwts.builder()
                .subject(username)                 // "sub" claim -- who this token represents
                .claim("roles", roles)             // custom claim -- application-specific data
                .issuedAt(now)                     // "iat" claim
                .expiration(expiry)                 // "exp" claim -- enforced automatically on parse
                .signWith(signingKey)               // HS256 by default for a SecretKey
                .compact();                         // produces the final "header.payload.signature" string
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    @SuppressWarnings("unchecked")
    public java.util.List<String> extractRoles(String token) {
        return (java.util.List<String>) parseClaims(token).get("roles");
    }

    /** Parses and verifies; throws (JwtException subclasses) on bad signature, malformed token, or expiry. */
    private io.jsonwebtoken.Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);          // throws ExpiredJwtException, SignatureException, MalformedJwtException, etc.
            return true;
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException ex) {
            return false;
        }
    }
}
```

--> **Why `parseClaims` throwing is the RIGHT design, not a bug to swallow silently** -- `ExpiredJwtException`, `SignatureException`, and `MalformedJwtException` are all DIFFERENT failure reasons a real system may want to log or handle differently (an expired token might trigger a client-side refresh attempt; a bad signature might indicate tampering worth alerting on). `isTokenValid` collapses them to a boolean for the common case, but the underlying exception type is still available to catch separately where that distinction matters.
--> **`Keys.hmacShaKeyFor` requires a sufficiently long key** -- HS256 needs at least a 256-bit (32-byte) key; passing a short, guessable string throws a `WeakKeyException`. This is a deliberate guardrail against exactly the "secret" being weak enough to brute-force.

## RSA Signing -- the Asymmetric Variant

```java
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;

// Typically generated once and loaded from a keystore/secrets manager in production --
// shown generated in-process here purely to illustrate the key SHAPE involved.
KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
generator.initialize(2048);
KeyPair keyPair = generator.generateKeyPair();
PrivateKey privateKey = keyPair.getPrivate();   // stays on the Authorization Server ONLY
PublicKey publicKey = keyPair.getPublic();      // distributed to Resource Servers (or published via JWKS)

String token = Jwts.builder()
        .subject("alice")
        .signWith(privateKey)              // signing requires the PRIVATE key
        .compact();

// A resource server, holding only the PUBLIC key, can verify but never sign:
io.jsonwebtoken.Claims claims = Jwts.parser()
        .verifyWith(publicKey)
        .build()
        .parseSignedClaims(token)
        .getPayload();
```

# The Validation Filter -- Wiring It Into Spring Security

--> This is the same conceptual filter as the basics file's `JwtAuthenticationFilter`, now calling into a REAL `JwtService` instead of throwing `UnsupportedOperationException`, plus handling the specific exception types thrown by `jjwt`.

```java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                if (jwtService.isTokenValid(token)) {
                    String username = jwtService.extractUsername(token);
                    var authorities = jwtService.extractRoles(token).stream()
                            .map(SimpleGrantedAuthority::new)
                            .toList();
                    var authentication = new UsernamePasswordAuthenticationToken(username, null, authorities);
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (io.jsonwebtoken.ExpiredJwtException ex) {
                // Distinguishing expiry from other failures lets clients tell "please refresh"
                // apart from "this token is fundamentally invalid" if the response body says so.
                response.setHeader("X-Token-Expired", "true");
            } catch (io.jsonwebtoken.JwtException ex) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
```

# Refresh Tokens -- Full Flow With Rotation

--> The basics file mentioned refresh tokens conceptually; here is the actual flow, including ROTATION -- a security-hardening technique where each refresh token can only be used ONCE.

```text
1. Login succeeds -> server issues BOTH:
       - a short-lived ACCESS TOKEN (e.g. 15 min), sent in the response body
       - a longer-lived REFRESH TOKEN (e.g. 7 days), often stored server-side
         (hashed, keyed by a random ID) AND handed to the client as an
         HttpOnly, Secure, SameSite cookie (never localStorage -- see Theory file 05)
        |
        v
2. Access token expires. Client calls POST /api/auth/refresh, presenting
   ONLY the refresh token (via its HttpOnly cookie -- no JS access needed).
        |
        v
3. Server looks up the refresh token server-side:
       - not found / already used / expired  -> reject entirely, force full re-login
       - valid                                -> issue a NEW access token AND
                                                  a NEW refresh token, then
                                                  IMMEDIATELY invalidate the old
                                                  refresh token (rotation)
        |
        v
4. Client now holds a fresh pair; repeat from step 2 whenever the access
   token next expires.
```

--> **Why rotate refresh tokens instead of reusing the same one for its whole lifetime?** -- rotation turns refresh-token theft into a DETECTABLE event. If an attacker steals a refresh token and uses it, the legitimate client's NEXT refresh attempt (using what is now a stale, already-rotated token) fails -- which is a strong signal something is wrong, and a well-built system can respond by revoking the entire token family (every descendant token derived from that original login) rather than just the one token, containing an attacker who got a head start.
--> **Storing refresh tokens server-side (a database table, keyed by a random opaque ID, storing a HASH of the token not the raw value)** -- unlike access tokens, which are validated purely by signature with no database lookup (that's the whole point of JWT statelessness), refresh tokens are long-lived enough that being able to REVOKE them on demand (logout, detected compromise, admin action) is worth reintroducing a small amount of server-side state for. This is the same "denylist" tradeoff the basics file mentioned for logout, applied specifically and deliberately to the long-lived token rather than the short-lived one.

```java
@Entity
public class RefreshToken {
    @Id @GeneratedValue
    private Long id;
    private String tokenHash;       // SHA-256 of the actual token value -- never store it raw
    private String username;
    private Instant expiresAt;
    private boolean revoked;
    private Long replacedByTokenId; // supports the "revoke whole family on reuse" detection above
}

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository repository;
    private final JwtService jwtService;

    public RefreshTokenService(RefreshTokenRepository repository, JwtService jwtService) {
        this.repository = repository;
        this.jwtService = jwtService;
    }

    public TokenPair refresh(String presentedRawToken) {
        String hash = sha256(presentedRawToken);
        RefreshToken stored = repository.findByTokenHash(hash)
                .orElseThrow(() -> new BadCredentialsException("Unknown refresh token"));

        if (stored.isRevoked() || stored.getExpiresAt().isBefore(Instant.now())) {
            // Reuse of an already-rotated/expired token: treat as a possible theft signal
            // and revoke the ENTIRE token family the compromised token belongs to.
            repository.revokeFamily(stored.getId());
            throw new BadCredentialsException("Refresh token reuse detected -- session revoked");
        }

        stored.setRevoked(true);   // rotation: this token is now permanently spent
        repository.save(stored);

        String newAccessToken = jwtService.generateAccessToken(stored.getUsername(), List.of("USER"));
        String newRawRefreshToken = issueAndPersistNewRefreshToken(stored.getUsername(), stored.getId());
        return new TokenPair(newAccessToken, newRawRefreshToken);
    }

    private String sha256(String raw) { /* MessageDigest.getInstance("SHA-256") ... */ return ""; }
    private String issueAndPersistNewRefreshToken(String username, Long previousId) { return ""; }
}

record TokenPair(String accessToken, String refreshToken) { }
```

# Storing Claims -- What Belongs in a Token vs What Doesn't

--> **Good fits for JWT claims** -- `sub` (subject/username), `roles`/`authorities` (so authorization checks don't need a database round trip), `iat`/`exp`, a tenant or organization ID in multi-tenant systems, a `jti` (unique token ID, useful for the denylist pattern mentioned in the basics file).
--> **Bad fits for JWT claims** -- anything that changes frequently and must always be CURRENT (a user's roles that were just revoked by an admin won't be reflected until the token naturally expires -- this is an inherent staleness window with stateless tokens), anything secret (passwords, tokens for OTHER systems, PII beyond what's needed), and anything large (tokens are sent on EVERY request as an HTTP header -- bloating them with a full user profile wastes bandwidth on every single call).
--> **The staleness-vs-statelessness tradeoff is fundamental, not a bug to "fix" carelessly** -- the entire performance benefit of JWT (no database lookup to authenticate a request) comes from trusting the token's claims at face value until expiry. Shortening access-token lifetime narrows the staleness window but increases refresh traffic; a denylist re-adds a lookup (partially defeating statelessness) but allows immediate revocation. There is no configuration that gives you both zero staleness AND zero server-side state -- pick the tradeoff deliberately based on how sensitive a delayed revocation would be for your system.

# Common Gotchas

--> **Using a weak or short HMAC secret** -- `jjwt`'s `Keys.hmacShaKeyFor` will reject keys under 256 bits for HS256, but hand-rolled implementations may not enforce this, leaving the signature brute-forceable.
--> **Distributing an HMAC shared secret to multiple independent services "to save time"** -- any holder of the secret can forge tokens for any user; switch to RSA/JWKS once more than one trust boundary needs to verify tokens.
--> **Reusing the same refresh token indefinitely without rotation** -- a stolen refresh token then remains usable by an attacker for its entire (typically long) lifetime with no way to detect the compromise.
--> **Storing refresh tokens in plaintext** -- store a hash, exactly as you would a password, so a leaked database doesn't hand out usable refresh tokens directly.
--> **Putting frequently-changing authorization data in a long-lived token** -- a revoked role or permission won't take effect until the access token naturally expires; keep access tokens short-lived if this staleness window matters for your system.
--> **Swallowing all JWT parse exceptions the same way** -- collapsing `ExpiredJwtException` and `SignatureException` into one generic failure loses information a client or monitoring system could act on differently.

# Best Practices Summary

--> Use HMAC when one service issues and validates its own tokens; switch to RSA (with JWKS publication) once multiple independent resource servers must validate tokens they don't issue.
--> Generate real signing keys via a secure random source with adequate length (256+ bits for HMAC, 2048+ bits for RSA) and load them from a secrets manager, never hardcoded.
--> Keep access tokens short-lived (minutes); pair with a longer-lived, server-side-tracked, ROTATING refresh token.
--> Detect refresh-token reuse and revoke the entire token family when it happens, not just the one reused token.
--> Store refresh tokens hashed, never raw, in whatever server-side store backs them.
--> Keep claims small, non-secret, and limited to what authorization actually needs; treat staleness-vs-statelessness as a deliberate design tradeoff, not an oversight to eliminate.
--> Distinguish expired-token failures from tampered/malformed-token failures in both logging and client-facing behavior.
