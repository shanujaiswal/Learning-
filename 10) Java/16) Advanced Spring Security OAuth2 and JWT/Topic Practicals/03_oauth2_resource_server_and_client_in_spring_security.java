/**
 * 03_oauth2_resource_server_and_client_in_spring_security.java
 *
 * Demonstrates, with illustrative Spring Security code:
 *     1. A ResourceServerConfig using oauth2ResourceServer().jwt(...) --
 *        Spring's own battle-tested JWT validation machinery, replacing a
 *        hand-rolled JwtAuthenticationFilter entirely -- plus a custom
 *        JwtAuthenticationConverter mapping an identity provider's claim
 *        shape onto Spring Security authorities, and an audience validator.
 *     2. An OAuth2ClientConfig using oauth2Client()/oauth2Login() -- the
 *        declarative Authorization Code + OIDC login flow -- for a
 *        browser-facing app logging users in via an external identity provider.
 *     3. A controller reading JWT claims via @AuthenticationPrincipal Jwt,
 *        and a companion controller reading OIDC identity claims via
 *        @AuthenticationPrincipal OidcUser.
 *     4. Config-properties illustrations for Keycloak/Auth0/Okta-style
 *        issuer-uri setups, for both the resource-server and client sides.
 *
 * Covers Theory chapter:
 *     16) Advanced Spring Security OAuth2 and JWT/Theory/03 OAuth2 Resource Server and Client in Spring Security.md
 *
 * IMPORTANT -- this file is illustrative, NOT a standalone runnable program.
 * It requires a real Spring Boot project on the classpath to compile/run, specifically:
 *     - spring-boot-starter-oauth2-resource-server   (validates incoming bearer JWTs)
 *     - spring-boot-starter-oauth2-client             (initiates login flows via an IdP)
 *     - spring-boot-starter-web
 *     - a REAL identity provider reachable at the configured issuer-uri (Keycloak,
 *       Auth0, Okta, or a locally-run spring-security-oauth2-authorization-server /
 *       Testcontainers Keycloak image for local development, per the theory file)
 *
 * Run (in a real Spring Boot project, after wiring this into src/main/java/...,
 * supplying application.yml properties matching your identity provider, and
 * adding a @SpringBootApplication class with a main() method):
 *     mvn spring-boot:run
 *   or
 *     ./gradlew bootRun
 *
 * Example requests once the app is running on the default port (8080):
 *     curl -X GET http://localhost:8080/api/me \
 *          -H "Authorization: Bearer <jwt-issued-by-your-identity-provider>"
 *     (visiting http://localhost:8080/dashboard in a browser, unauthenticated,
 *      triggers the full oauth2Login() redirect to the identity provider)
 */

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

// =============================================================================
// SECTION 1 -- Resource Server configuration: validating INCOMING bearer tokens
// =============================================================================
// This plays the "Resource Server" role from the four-role model (Theory file
// 01). Adding spring-boot-starter-oauth2-resource-server plus an issuer-uri
// gets automatic JWKS fetching, key-rotation handling, and signature/expiry/
// issuer validation for free -- replacing the hand-rolled JwtAuthenticationFilter
// from Topic Practicals file 02 entirely.

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class ResourceServerConfig {

    // application.yml equivalent (Keycloak-style realm issuer):
    //
    //   spring:
    //     security:
    //       oauth2:
    //         resourceserver:
    //           jwt:
    //             issuer-uri: https://my-keycloak.example.com/realms/my-app
    //
    // Spring auto-discovers /.well-known/openid-configuration at this URL,
    // which points to the JWKS endpoint -- the PUBLIC key(s) needed to verify
    // RSA-signed tokens are fetched and cached automatically, with zero
    // manual key management on this service's part.

    @Bean
    public SecurityFilterChain resourceServerFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())          // stateless bearer-token API -- no ambient session cookie to protect
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/public/**").permitAll()
                    .anyRequest().authenticated()
            )
            // Replaces a hand-rolled JwtAuthenticationFilter entirely -- parses,
            // verifies (signature + iss + exp/nbf), and populates the
            // SecurityContext automatically for any request carrying a valid
            // "Authorization: Bearer <jwt>" header.
            .oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(jwt -> jwt
                            .jwtAuthenticationConverter(jwtAuthenticationConverter())
                            .decoder(audienceValidatingJwtDecoder())
                    )
            );

        return http.build();
    }

    /**
     * Maps claims inside the JWT onto Spring Security GrantedAuthority objects.
     * Without this, Spring only sees the token as authenticated, with NO
     * roles/authorities attached for @PreAuthorize or hasRole() to check.
     * Keycloak nests roles under "realm_access.roles" by default -- shown
     * here with a flatter "roles" claim name for simplicity; the exact
     * claim name/shape varies by identity provider (Theory file 03's core warning).
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthorityPrefix("ROLE_");
        authoritiesConverter.setAuthoritiesClaimName("roles");     // adjust to match your IdP's claim name/nesting

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }

    /**
     * issuer-uri alone validates signature, issuer, and expiry -- but NOT
     * that the token was actually intended for THIS resource server. A token
     * legitimately issued by the same authorization server for a DIFFERENT
     * API would otherwise pass. This closes that gap with an explicit
     * audience check, per the theory's "one of the most common real-world
     * misconfigurations" warning.
     */
    @Bean
    public JwtDecoder audienceValidatingJwtDecoder(
            @Value("${app.security.issuer-uri}") String issuerUri,
            @Value("${app.security.expected-audience}") String expectedAudience) {

        NimbusJwtDecoder decoder = (NimbusJwtDecoder) JwtDecoders.fromIssuerLocation(issuerUri);

        OAuth2TokenValidator<Jwt> audienceValidator = jwt ->
                jwt.getAudience().contains(expectedAudience)
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(
                                new OAuth2Error("invalid_token", "Required audience is missing", null));

        OAuth2TokenValidator<Jwt> defaultValidators = JwtValidators.createDefaultWithIssuer(issuerUri);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(defaultValidators, audienceValidator));
        return decoder;
    }

    // Overload used directly by the @Bean method above when properties are
    // injected via constructor-style @Value parameters -- shown separately
    // purely so both the "how" (this method) and "why" (comments above) are
    // easy to read independently.
    private JwtDecoder audienceValidatingJwtDecoder() {
        return audienceValidatingJwtDecoder(
                "https://my-keycloak.example.com/realms/my-app",
                "my-orders-api");
    }
}

// =============================================================================
// SECTION 2 -- OAuth2 Client configuration: logging in via an external IdP
// =============================================================================
// This plays the "Client" role from the four-role model. A typical web app is
// BOTH a Client (redirects users to log in via Keycloak/Auth0/Google) AND a
// Resource Server for its own API (validating those same tokens on subsequent
// calls) -- Section 1 and Section 2 commonly coexist in the same application.

@Configuration
@EnableWebSecurity
class OAuth2ClientConfig {

    // application.yml equivalent:
    //
    //   spring:
    //     security:
    //       oauth2:
    //         client:
    //           registration:
    //             keycloak:
    //               client-id: my-web-app
    //               client-secret: ${KEYCLOAK_CLIENT_SECRET}   # never hardcode -- inject from a secrets manager/env var
    //               authorization-grant-type: authorization_code
    //               redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
    //               scope: openid, profile, email
    //           provider:
    //             keycloak:
    //               issuer-uri: https://my-keycloak.example.com/realms/my-app
    //
    // (Auth0/Okta config follows the identical shape -- only the issuer-uri,
    // client-id, and client-secret values differ; the Spring-side config
    // shape is standardized because all three implement the same OIDC surface.)

    @Bean
    public SecurityFilterChain clientFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/", "/public/**").permitAll()
                    .anyRequest().authenticated()
            )
            // Enables the full Authorization Code + OIDC login flow: redirects
            // unauthenticated users to the identity provider's login page,
            // handles the /login/oauth2/code/{registrationId} callback, and
            // establishes a local principal from the resulting ID token --
            // all the choreography from Theory file 01's Authorization Code
            // walkthrough, wired up declaratively with zero custom code.
            .oauth2Login(oauth2 -> oauth2.defaultSuccessUrl("/dashboard"));

        return http.build();
    }
}

// =============================================================================
// SECTION 3a -- Reading JWT claims as a RESOURCE SERVER (@AuthenticationPrincipal Jwt)
// =============================================================================

@RestController
class ResourceServerClaimsController {

    /**
     * Because ResourceServerConfig wired up oauth2ResourceServer().jwt(...),
     * Spring Security automatically injects the validated, parsed token as a
     * Jwt principal here -- no manual header parsing, no manual signature
     * check, and no manual expiry check required in the controller at all.
     */
    @GetMapping("/api/whoami")
    public Map<String, Object> whoAmI(@AuthenticationPrincipal Jwt jwt) {
        return Map.of(
                "subject", jwt.getSubject(),                       // "sub" claim
                "issuer", jwt.getIssuer().toString(),               // "iss" claim -- which Authorization Server issued this
                "expiresAt", String.valueOf(jwt.getExpiresAt()),    // "exp" claim
                "roles", jwt.getClaimAsStringList("roles"),         // custom claim, per this IdP's shape
                "rawClaims", jwt.getClaims()                        // full claim map, for illustration/debugging only
        );
    }
}

// =============================================================================
// SECTION 3b -- Reading identity claims as a CLIENT after oauth2Login() (@AuthenticationPrincipal OidcUser)
// =============================================================================

@RestController
class OidcProfileController {

    /**
     * Populated after a successful oauth2Login() flow -- claims come
     * directly from the identity provider's ID TOKEN (Theory file 01's
     * OIDC section), not the access token. No local user-table lookup is
     * needed for this basic profile info; a real app would still typically
     * sync/create a local user record on first login for anything beyond
     * these standard OIDC claims.
     */
    @GetMapping("/api/me")
    public Map<String, Object> me(@AuthenticationPrincipal OidcUser principal) {
        return Map.of(
                "name", principal.getFullName(),
                "email", principal.getEmail(),
                "subject", principal.getSubject(),   // the IdP's stable user identifier ("sub" claim)
                "issuer", principal.getIssuer().toString()
        );
    }
}

/*
 * NOTE on the annotations/classes used above:
 * This snippet uses real Spring Security OAuth2 resource-server/client types
 * (SecurityFilterChain, JwtAuthenticationConverter, JwtDecoder, OidcUser, Jwt,
 * etc.) exactly as they'd appear in a real Spring Boot project. It requires
 * spring-boot-starter-oauth2-resource-server AND/OR spring-boot-starter-oauth2-client
 * on the classpath (per which role(s) the service plays), matching
 * application.yml properties pointed at a REAL identity provider (or a local
 * Testcontainers-managed Keycloak, per the theory file), and a
 * @SpringBootApplication entry point to compile and run this end-to-end.
 * The two audienceValidatingJwtDecoder() method overloads exist purely to
 * show both the @Value-driven @Bean wiring and its underlying logic clearly
 * side by side -- a real project would keep only the @Bean-annotated one.
 */
