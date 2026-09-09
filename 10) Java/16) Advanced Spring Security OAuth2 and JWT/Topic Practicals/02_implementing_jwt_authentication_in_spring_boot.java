/**
 * 02_implementing_jwt_authentication_in_spring_boot.java
 *
 * Demonstrates, with illustrative Spring Security + jjwt code:
 *     1. A JwtService class showing REAL HMAC (HS256) and RSA (RS256) token
 *        generation/signing shapes, plus claim extraction and validation --
 *        the actual API calls a real `jjwt` (io.jsonwebtoken) dependency
 *        would provide, following on from the UnsupportedOperationException
 *        skeleton style used in the more basic Spring Security demo.
 *     2. A JwtAuthenticationFilter wired into the Spring Security filter
 *        chain, distinguishing expired-token failures from other JWT
 *        exceptions per the theory's "don't swallow all failures the same
 *        way" guidance.
 *     3. A full refresh-token flow WITH ROTATION -- server-side storage of a
 *        hashed refresh token, reuse detection, and whole-token-family
 *        revocation on suspected theft.
 *
 * Covers Theory chapter:
 *     16) Advanced Spring Security OAuth2 and JWT/Theory/02 Implementing JWT Authentication in Spring Boot.md
 *
 * IMPORTANT -- this file is illustrative, NOT a standalone runnable program.
 * It requires a real Spring Boot project on the classpath to compile/run, specifically:
 *     - spring-boot-starter-security     (Spring Security core + filter chain)
 *     - spring-boot-starter-web          (Spring MVC, for the controller)
 *     - spring-boot-starter-data-jpa     (for the RefreshToken entity/repository)
 *     - a JWT library: io.jsonwebtoken:jjwt-api, jjwt-impl, jjwt-jackson (jjwt 0.12.x API shown)
 *
 * Run (in a real Spring Boot project, after wiring this into src/main/java/... and adding
 * a @SpringBootApplication class with a main() method):
 *     mvn spring-boot:run
 *   or
 *     ./gradlew bootRun
 *
 * Example requests once the app is running on the default port (8080):
 *     curl -X POST http://localhost:8080/api/auth/login \
 *          -H "Content-Type: application/json" \
 *          -d "{\"username\":\"alice\",\"password\":\"secret\"}"
 *     curl -X POST http://localhost:8080/api/auth/refresh \
 *          -H "Content-Type: application/json" \
 *          -d "{\"refreshToken\":\"<raw-refresh-token-from-login-response>\"}"
 */

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// =============================================================================
// SECTION 1 -- JWT signing/validation: HMAC (symmetric) variant
// =============================================================================
// This is the REAL implementation shape (using jjwt 0.12.x's builder API) that
// replaces the UnsupportedOperationException skeleton from the more basic
// Spring Security demo. HMAC fits a single service that both issues and
// validates its own tokens -- see Section 1b for RSA, used when multiple
// independent resource servers must verify tokens they don't issue.

@Component
class JwtService {

    // NEVER hardcode a real secret -- load it from an environment variable or
    // a secrets manager (Vault, AWS Secrets Manager, etc.). Injected here via
    // @Value purely to show WHERE it should come from; the property itself
    // must resolve to a base64-encoded value at least 256 bits (32 bytes) long,
    // since Keys.hmacShaKeyFor() rejects anything shorter for HS256 with a
    // WeakKeyException -- a deliberate guardrail against brute-forceable secrets.
    private final SecretKey signingKey;
    private static final long ACCESS_TOKEN_VALIDITY_MS = 15 * 60 * 1000;   // 15 minutes -- short-lived on purpose

    JwtService(@Value("${app.jwt.base64-secret}") String base64Secret) {
        this.signingKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(base64Secret));
    }

    /** Builds and HS256-signs a JWT for the given username + roles. */
    public String generateAccessToken(String username, List<String> roles) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + ACCESS_TOKEN_VALIDITY_MS);

        return Jwts.builder()
                .subject(username)                  // "sub" claim -- who this token represents
                .claim("roles", roles)              // custom claim -- kept small and non-secret per the theory's guidance
                .claim("jti", UUID.randomUUID().toString())  // unique token ID -- enables a denylist/replay-detection pattern
                .issuedAt(now)                       // "iat" claim
                .expiration(expiry)                  // "exp" claim -- enforced automatically on parse
                .signWith(signingKey)                // HS256 by default for a SecretKey
                .compact();                          // produces the final "header.payload.signature" string
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        return (List<String>) parseClaims(token).get("roles");
    }

    public String extractJti(String token) {
        return parseClaims(token).get("jti", String.class);
    }

    /**
     * Parses and verifies the token, PINNING the expected algorithm (HS256,
     * implied by verifying with an HMAC SecretKey) rather than trusting
     * whatever algorithm the token's own header claims -- this is exactly
     * the mitigation the theory describes against "alg:none" and
     * RS256/HS256 algorithm-confusion attacks. Throws a JwtException
     * subclass on bad signature, malformed token, or expiry.
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** Returns true only if the token's signature is valid AND it has not expired. */
    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }
}

// =============================================================================
// SECTION 1b -- JWT signing/validation: RSA (asymmetric) variant
// =============================================================================
// Used when MULTIPLE independent resource servers must validate tokens issued
// by ONE central authorization server, without being trusted to issue tokens
// themselves -- only the PUBLIC key is distributed (often via a JWKS
// endpoint), so a compromised resource server can verify but never forge.

class RsaJwtSigningIllustration {

    /** Illustrates key generation -- in production, a key pair is generated
     *  ONCE, the private key stays exclusively on the Authorization Server
     *  (loaded from a keystore/secrets manager), and the public key is either
     *  distributed directly or published at a JWKS endpoint for resource
     *  servers to fetch automatically (see Topic Practicals file 03). */
    static KeyPair generateRsaKeyPairIllustration() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048); // 2048+ bits, per the theory's best-practice minimum
        return generator.generateKeyPair();
    }

    /** Only the AUTHORIZATION SERVER, holding the private key, can sign. */
    static String signWithPrivateKey(String subject, PrivateKey privateKey) {
        return Jwts.builder()
                .subject(subject)
                .issuedAt(new Date())
                .signWith(privateKey)   // RS256 implied when signing with an RSA PrivateKey
                .compact();
    }

    /** A RESOURCE SERVER, holding only the public key, can verify but never forge. */
    static Claims verifyWithPublicKey(String token, PublicKey publicKey) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}

// =============================================================================
// SECTION 2 -- JwtAuthenticationFilter, wired into the Spring Security chain
// =============================================================================
// Distinguishes an EXPIRED token (client should try a silent refresh) from
// any OTHER JwtException (signature tampering, malformed token -- treat as
// fully invalid, clear the SecurityContext, no refresh attempt makes sense).

@Component
class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring("Bearer ".length());
            try {
                if (jwtService.isTokenValid(token)) {
                    String username = jwtService.extractUsername(token);
                    List<SimpleGrantedAuthority> authorities = jwtService.extractRoles(token).stream()
                            .map(SimpleGrantedAuthority::new)
                            .toList();

                    Authentication authentication =
                            new UsernamePasswordAuthenticationToken(username, null, authorities);
                    ((UsernamePasswordAuthenticationToken) authentication)
                            .setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // Stateless: rebuilt from scratch, purely from this request's token, every time.
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (ExpiredJwtException ex) {
                // Distinguishing expiry from other failures lets the client know to attempt
                // POST /api/auth/refresh instead of forcing a full re-login immediately.
                response.setHeader("X-Token-Expired", "true");
                SecurityContextHolder.clearContext();
            } catch (JwtException ex) {
                // Bad signature, malformed token, unsupported algorithm, etc. -- treat as
                // fully invalid. Never fall back to trusting the token's own claimed algorithm.
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);   // always continue -- permitAll() endpoints must still work with no token
    }
}

// =============================================================================
// SECTION 3 -- Refresh tokens WITH ROTATION and reuse (theft) detection
// =============================================================================
// Access tokens are validated purely by signature (no DB lookup -- the whole
// point of JWT statelessness). Refresh tokens are long-lived enough that
// being able to REVOKE them on demand is worth reintroducing a small amount
// of server-side state for -- so they're stored server-side, HASHED (never
// raw), and rotated on every use.

@Entity
class RefreshToken {
    @Id
    @GeneratedValue
    private Long id;
    private String tokenHash;         // SHA-256 of the actual token value -- never store it raw
    private String username;
    private Instant expiresAt;
    private boolean revoked;
    private Long replacedByTokenId;   // links rotated tokens into a "family" for reuse detection

    // getters/setters omitted for brevity -- illustrative entity shape only
    public Long getId() { return id; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public boolean isRevoked() { return revoked; }
    public void setRevoked(boolean revoked) { this.revoked = revoked; }
}

@Repository
interface RefreshTokenRepository {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    RefreshToken save(RefreshToken token);
    /** Revokes every token descended from the same original login -- the
     *  "revoke the whole family" response to a detected reuse/theft signal. */
    void revokeFamily(Long tokenId);
}

record TokenPair(String accessToken, String refreshToken) { }
record RefreshRequest(String refreshToken) { }
record LoginRequest(String username, String password) { }

@Service
class RefreshTokenService {

    private final RefreshTokenRepository repository;
    private final JwtService jwtService;
    private static final long REFRESH_TOKEN_VALIDITY_DAYS = 7;

    RefreshTokenService(RefreshTokenRepository repository, JwtService jwtService) {
        this.repository = repository;
        this.jwtService = jwtService;
    }

    /** Issues a brand-new (access, refresh) pair after a successful login, with no prior token to rotate. */
    public TokenPair issueInitialPair(String username, List<String> roles) {
        String accessToken = jwtService.generateAccessToken(username, roles);
        String rawRefreshToken = issueAndPersistNewRefreshToken(username);
        return new TokenPair(accessToken, rawRefreshToken);
    }

    /**
     * Handles POST /api/auth/refresh. Looks up the presented refresh token by
     * its HASH, and either:
     *   - not found / already revoked / expired -> treat as a possible theft
     *     signal, revoke the ENTIRE token family, force full re-login
     *   - valid -> issue a fresh pair and IMMEDIATELY invalidate the old
     *     refresh token (rotation) so it can never be used again
     */
    public TokenPair refresh(String presentedRawToken) {
        String hash = sha256(presentedRawToken);
        RefreshToken stored = repository.findByTokenHash(hash)
                .orElseThrow(() -> new BadCredentialsException("Unknown refresh token"));

        if (stored.isRevoked() || stored.getExpiresAt().isBefore(Instant.now())) {
            // Reuse of an already-rotated (or expired) token is a strong signal
            // something is wrong -- a legitimate client would never present a
            // token it already rotated away. Contain the blast radius by
            // revoking every token descended from the same original login,
            // not just this one.
            repository.revokeFamily(stored.getId());
            throw new BadCredentialsException("Refresh token reuse detected -- entire session family revoked");
        }

        stored.setRevoked(true);   // rotation: this token is now permanently spent
        repository.save(stored);

        String newAccessToken = jwtService.generateAccessToken(stored.getUsername(), List.of("USER"));
        String newRawRefreshToken = issueAndPersistNewRefreshToken(stored.getUsername());
        return new TokenPair(newAccessToken, newRawRefreshToken);
    }

    private String issueAndPersistNewRefreshToken(String username) {
        // A cryptographically random opaque value -- NOT a JWT itself, just a
        // random token whose hash we can look up later. 32 random bytes,
        // base64url-encoded, mirrors the entropy used for PKCE's code_verifier
        // in Topic Practicals file 01.
        byte[] randomBytes = new byte[32];
        new java.security.SecureRandom().nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        RefreshToken entity = new RefreshToken();
        entity.setTokenHash(sha256(rawToken));
        entity.setUsername(username);
        entity.setExpiresAt(Instant.now().plusSeconds(REFRESH_TOKEN_VALIDITY_DAYS * 24 * 60 * 60));
        entity.setRevoked(false);
        repository.save(entity);

        return rawToken; // the RAW value is returned to the client ONCE -- only the hash is ever persisted
    }

    private String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes("UTF-8"));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Unexpected: SHA-256/UTF-8 unavailable", e);
        }
    }
}

@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final RefreshTokenService refreshTokenService;

    AuthController(RefreshTokenService refreshTokenService) {
        this.refreshTokenService = refreshTokenService;
    }

    // POST /api/auth/login -- illustrative only; a real implementation verifies
    // credentials against a UserDetailsService-backed repository first (as in
    // the basic Spring Security demo) before ever issuing tokens.
    @PostMapping("/login")
    public TokenPair login(@RequestBody LoginRequest request) {
        boolean credentialsValid = true; // placeholder -- real check omitted, see basic Spring Security demo
        if (!credentialsValid) {
            throw new BadCredentialsException("Invalid username or password");
        }
        return refreshTokenService.issueInitialPair(request.username(), List.of("USER"));
    }

    // POST /api/auth/refresh -- the refresh token normally arrives via an
    // HttpOnly, Secure, SameSite cookie rather than a JSON body in a real
    // deployment (see Topic Practicals file 05) -- a request body is shown
    // here purely to keep this illustration self-contained.
    @PostMapping("/refresh")
    public TokenPair refresh(@RequestBody RefreshRequest request) {
        return refreshTokenService.refresh(request.refreshToken());
    }
}

/*
 * NOTE on the annotations/classes used above:
 * This snippet uses real Spring Security, Spring Data JPA, and jjwt 0.12.x
 * types exactly as they'd appear in a real Spring Boot project. It requires
 * spring-boot-starter-security, spring-boot-starter-web, spring-boot-starter-data-jpa,
 * and io.jsonwebtoken:jjwt-api/-impl/-jackson on the classpath, plus a real
 * @SpringBootApplication entry point and a configured `app.jwt.base64-secret`
 * property (injected from an environment variable/secrets manager, never a
 * literal in application.yml) to actually compile and run this end-to-end.
 */
