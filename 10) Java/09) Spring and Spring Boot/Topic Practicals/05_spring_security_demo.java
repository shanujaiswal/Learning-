/**
 * 05_spring_security_demo.java
 *
 * Demonstrates, with illustrative Spring Security code:
 *     1. A SecurityConfig class declaring a SecurityFilterChain bean (modern,
 *        non-WebSecurityConfigurerAdapter style) -- stateless session policy,
 *        URL-based authorization rules ordered specific -> general, CSRF
 *        disabled for a token-based API, and a BCryptPasswordEncoder bean
 *     2. A minimal JwtUtil skeleton -- token generation, parsing/validation,
 *        and claim extraction (illustrative, NOT production-hardened crypto)
 *     3. A custom JwtAuthenticationFilter slotted into the filter chain
 *     4. A secured @RestController demonstrating @PreAuthorize method-level
 *        authorization alongside the filter chain's coarser URL rules
 *
 * Covers Theory chapter:
 *     10) Java/09) Spring and Spring Boot/Theory/05 Spring Security Basics.md
 *
 * IMPORTANT -- this file is illustrative, NOT a standalone runnable program.
 * It requires a real Spring Boot project on the classpath to compile/run, specifically:
 *     - spring-boot-starter-security     (Spring Security core + filter chain)
 *     - spring-boot-starter-web          (Spring MVC, for the controller)
 *     - a JWT library, e.g. io.jsonwebtoken:jjwt-api/-impl/-jackson (jjwt) or
 *       nimbus-jose-jwt -- JwtUtil below shows the SHAPE of such a class,
 *       not a working implementation, since it depends on that library's API.
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
 *     curl -X GET  http://localhost:8080/api/admin/reports \
 *          -H "Authorization: Bearer <jwt-from-login-response>"
 */

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.filter.OncePerRequestFilter;

import java.util.Collections;
import java.util.List;
import java.util.Map;

// ---------------------------------------------------------------------------
// 1) Security configuration -- modern style: a SecurityFilterChain @Bean
//    inside a @Configuration class. Replaces the deprecated
//    WebSecurityConfigurerAdapter subclassing approach seen in older material.
// ---------------------------------------------------------------------------

@org.springframework.context.annotation.Configuration
@EnableWebSecurity
@EnableMethodSecurity                      // required for @PreAuthorize below to have any effect at all
class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @org.springframework.context.annotation.Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // CSRF protects session-cookie-based apps from cross-site request forgery.
            // This API is stateless/token-based (no ambient session cookie), so the
            // attack CSRF defends against doesn't apply here -- disabling it is
            // conventional for this style of API, NOT a general "always disable" rule.
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session
                    .sessionCreationPolicy(SessionCreationPolicy.STATELESS))   // no HttpSession -- re-authenticate every request
            .authorizeHttpRequests(auth -> auth
                    // Rules are evaluated top to bottom -- FIRST match wins, so specific
                    // rules must come before broader ones (a broad rule declared first
                    // would shadow everything below it).
                    .requestMatchers("/api/auth/**").permitAll()                      // login/register -- no token required yet
                    .requestMatchers(HttpMethod.GET, "/api/public/**").permitAll()     // open read-only endpoints
                    .requestMatchers("/api/admin/**").hasRole("ADMIN")                // coarse, URL-pattern-based gate
                    .anyRequest().authenticated()                                     // everything else needs a valid JWT
            )
            // Slot our custom JWT filter in BEFORE Spring's default username/password
            // filter, since we're not doing form login at all -- every request's
            // identity comes from the Authorization header instead.
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @org.springframework.context.annotation.Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt: a deliberately slow, salted hash -- never store or compare
        // plaintext passwords, and never use a fast general-purpose hash (like
        // plain SHA-256) for passwords either.
        return new BCryptPasswordEncoder();
    }
}

// ---------------------------------------------------------------------------
// 2) JWT utility skeleton -- shows the SHAPE of token generation/validation.
//    Illustrative only: a real implementation needs a real JWT library
//    (e.g. jjwt) to actually build/parse/verify the base64url-encoded,
//    signed token. The secret key below is a placeholder -- a real project
//    loads it from a secret store / environment variable, never hardcodes it.
// ---------------------------------------------------------------------------

@Component
class JwtUtil {

    // NEVER hardcode a real secret like this in production code -- load from
    // an environment variable or a secrets manager. Shown as a literal here
    // purely so the illustrative shape of the class is self-contained.
    private static final String SECRET_KEY_PLACEHOLDER = "replace-with-a-real-256-bit-secret-from-config";
    private static final long ACCESS_TOKEN_VALIDITY_MS = 15 * 60 * 1000;   // 15 minutes -- short-lived on purpose

    /**
     * Builds and signs a JWT for the given username + roles.
     * Real implementation (with a library like jjwt) would look roughly like:
     *
     *     return Jwts.builder()
     *             .setSubject(username)
     *             .claim("roles", roles)
     *             .setIssuedAt(new Date())
     *             .setExpiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_VALIDITY_MS))
     *             .signWith(secretKey, SignatureAlgorithm.HS256)
     *             .compact();
     */
    public String generateToken(String username, List<String> roles) {
        throw new UnsupportedOperationException(
                "Illustrative skeleton only -- wire in a real JWT library (e.g. jjwt) to implement this.");
    }

    /** Verifies the signature and expiry, then extracts the subject (username). Throws on any invalid/expired token. */
    public String extractUsername(String token) {
        throw new UnsupportedOperationException("Illustrative skeleton only -- see generateToken() note above.");
    }

    /** Extracts the "roles" claim from an already-validated token. */
    public List<String> extractRoles(String token) {
        throw new UnsupportedOperationException("Illustrative skeleton only -- see generateToken() note above.");
    }

    /** Returns true only if the token's signature is valid AND it has not expired. */
    public boolean isTokenValid(String token) {
        throw new UnsupportedOperationException("Illustrative skeleton only -- see generateToken() note above.");
    }
}

// ---------------------------------------------------------------------------
// 3) Custom filter -- runs once per request, BEFORE the authorization
//    decision is made. Reads the Authorization header, validates the JWT,
//    and (if valid) populates the SecurityContext so the rest of the chain
//    and the eventual controller method see an authenticated principal --
//    all without any server-side session, per the stateless auth flow.
// ---------------------------------------------------------------------------

@Component
class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, java.io.IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring("Bearer ".length());

            try {
                if (jwtUtil.isTokenValid(token)) {
                    String username = jwtUtil.extractUsername(token);
                    List<String> roles = jwtUtil.extractRoles(token);

                    List<SimpleGrantedAuthority> authorities = roles.stream()
                            .map(SimpleGrantedAuthority::new)
                            .toList();

                    Authentication authentication =
                            new UsernamePasswordAuthenticationToken(username, null, authorities);

                    // This is the crucial step for a STATELESS API -- nothing was
                    // remembered from a previous request; the SecurityContext is
                    // rebuilt from scratch, purely from this request's token, every
                    // single time.
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (RuntimeException ex) {
                // Invalid/expired/tampered token -- leave the SecurityContext empty.
                // The downstream authorization filter will then reject the request
                // with 401 for any endpoint that requires authentication.
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);   // always continue the chain -- permitAll() endpoints must still work with no token
    }
}

// ---------------------------------------------------------------------------
// 4) A minimal auth service + login endpoint -- issues a JWT after verifying
//    credentials. In a real project, credential verification would look up
//    the user via a UserDetailsService-backed repository and compare with
//    the PasswordEncoder bean, exactly as covered in the Theory file.
// ---------------------------------------------------------------------------

@Service
class AuthService {

    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    public AuthService(JwtUtil jwtUtil, PasswordEncoder passwordEncoder) {
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
    }

    public String login(String username, String rawPassword) {
        // Illustrative only -- a real implementation loads the stored BCrypt
        // hash for "username" from a UserRepository and compares with
        // passwordEncoder.matches(rawPassword, storedHash), throwing a
        // BadCredentialsException (-> 401) on mismatch, before ever issuing a token.
        boolean credentialsValid = passwordEncoder.matches(rawPassword, "$2a$10$examplePlaceholderHash");
        if (!credentialsValid) {
            throw new IllegalArgumentException("Invalid username or password");
        }
        return jwtUtil.generateToken(username, List.of("USER"));
    }
}

record LoginRequest(String username, String password) { }

@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // POST /api/auth/login -- permitAll() per the SecurityFilterChain rules above,
    // since a client can't have a token yet at this point.
    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody LoginRequest request) {
        String token = authService.login(request.username(), request.password());
        return ResponseEntity.ok(Map.of("accessToken", token, "tokenType", "Bearer"));
    }
}

// ---------------------------------------------------------------------------
// 5) A secured controller -- combines the filter chain's coarse URL rule
//    (/api/admin/** requires ROLE_ADMIN) with a fine-grained @PreAuthorize
//    check at the method level for a rule the URL pattern alone can't express.
// ---------------------------------------------------------------------------

@RestController
@RequestMapping("/api/admin")
class AdminController {

    // Reachable only if the SecurityFilterChain's "/api/admin/**" -> hasRole("ADMIN")
    // rule already let the request through -- @PreAuthorize here is redundant with
    // that URL rule but shown for illustration of the annotation's syntax.
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/reports")
    public ResponseEntity<List<String>> getReports() {
        return ResponseEntity.ok(List.of("quarterly-sales", "inventory-audit"));
    }

    // A rule the URL pattern genuinely CANNOT express -- "the resource owner,
    // OR an admin" depends on the actual authenticated principal and the
    // requested id, not just the URL shape. This is exactly when @PreAuthorize
    // earns its place alongside coarser filter-chain rules.
    @PreAuthorize("#userId == authentication.name or hasRole('ADMIN')")
    @GetMapping("/users/{userId}/audit-log")
    public ResponseEntity<List<String>> getUserAuditLog(@PathVariable String userId) {
        return ResponseEntity.ok(List.of("login@2026-08-30T10:00:00Z", "password-change@2026-08-30T10:05:00Z"));
    }
}

/*
 * NOTE on the annotations/classes used above:
 * This snippet uses real Spring Security types (SecurityFilterChain,
 * OncePerRequestFilter, @PreAuthorize, BCryptPasswordEncoder, etc.) exactly
 * as they'd appear in a real Spring Boot project, which requires
 * spring-boot-starter-security on the classpath, component scanning, and a
 * @SpringBootApplication entry point to wire everything together. JwtUtil's
 * methods deliberately throw UnsupportedOperationException because actually
 * signing/parsing a JWT requires a real JWT library's API (e.g. jjwt) which
 * isn't assumed to be on the classpath here -- wire one in and replace those
 * bodies to see this run end-to-end in a real project.
 */
