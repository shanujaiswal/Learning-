# Two Different Spring Security Starters, Two Different Jobs

--> Spring Security ships TWO distinct OAuth2-related starters, and confusing which one a given service needs is a common early mistake. Both build directly on the four-role model from Theory file 01.

| Starter | This service plays the role of... | What it does |
|---|---|---|
| `spring-boot-starter-oauth2-resource-server` | **Resource Server** | Validates INCOMING bearer tokens on requests it receives -- checks signature, expiry, issuer, and audience before letting a request reach your controllers |
| `spring-boot-starter-oauth2-client` | **Client** | Initiates OAuth2/OIDC flows (typically Authorization Code) to obtain tokens FROM an authorization server, usually to let a human user log in via a third-party identity provider |

--> **A single application can be BOTH** -- a typical web app is a Client (it redirects users to log in via Keycloak/Auth0/Google, obtaining tokens on their behalf) AND simultaneously a Resource Server for its own API (validating those same tokens on subsequent API calls the browser makes back to it). Don't assume "OAuth2" always means only one of these roles.

# Resource Server Configuration -- Validating Incoming Tokens

--> The resource-server starter replaces a hand-rolled `JwtAuthenticationFilter` (the DIY approach shown in Theory file 02 and the basics file) with Spring Security's own, battle-tested JWT validation machinery -- point it at an identity provider and it handles signature verification, expiry, and claims-to-authorities mapping declaratively.

```yaml
# application.yml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          # The authorization server's issuer URL. Spring auto-discovers the
          # /.well-known/openid-configuration document at this URL, which in
          # turn points to the JWKS endpoint -- so the PUBLIC key(s) needed to
          # verify RSA-signed tokens are fetched and cached AUTOMATICALLY,
          # with zero manual key management on the resource server's part.
          issuer-uri: https://my-keycloak.example.com/realms/my-app
```

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class ResourceServerConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())                      // stateless resource server -- same reasoning as Theory 05 basics
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/public/**").permitAll()
                    .anyRequest().authenticated()
            )
            // THIS replaces the hand-rolled JwtAuthenticationFilter entirely --
            // Spring Security's own resource-server support parses, verifies,
            // and populates the SecurityContext automatically for any request
            // carrying a valid "Authorization: Bearer <jwt>" header.
            .oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            );

        return http.build();
    }

    /**
     * Maps claims inside the JWT (e.g. a custom "roles" or "realm_access.roles"
     * claim from Keycloak) onto Spring Security GrantedAuthority objects --
     * without this, Spring only sees the token as authenticated, with no
     * roles/authorities attached for @PreAuthorize or hasRole() to check.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        var authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthorityPrefix("ROLE_");
        authoritiesConverter.setAuthoritiesClaimName("roles");     // adjust to match your IdP's claim name

        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }
}
```

--> **What replacing the DIY filter with `issuer-uri` actually buys you** -- automatic JWKS fetching and periodic key rotation handling (no code to update when the identity provider rotates its signing key), standards-compliant validation of `iss` (issuer) and `exp`/`nbf` (time-based validity) out of the box, and one line of YAML instead of a hand-maintained `JwtService`. The tradeoff is less direct control -- if you need bespoke claim validation logic, you extend `OAuth2TokenValidator`, not just edit a filter's body directly.
--> **Manual, non-issuer-based configuration** -- when there's no OIDC discovery document available (or you deliberately want tighter control), you can configure the public key or JWKS URI directly instead of via `issuer-uri`:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: https://my-keycloak.example.com/realms/my-app/protocol/openid-connect/certs
          # or, for a single static RSA public key instead of a JWKS endpoint:
          # public-key-location: classpath:public-key.pem
```

--> **Custom validation -- checking the `aud` (audience) claim** -- `issuer-uri` alone validates signature and issuer/expiry, but NOT that the token was actually intended for THIS resource server (a token issued for a completely different API, by the same authorization server, would otherwise pass). Adding an audience check closes this gap:

```java
@Bean
public JwtDecoder jwtDecoder() {
    NimbusJwtDecoder decoder = JwtDecoders.fromIssuerLocation("https://my-keycloak.example.com/realms/my-app");

    OAuth2TokenValidator<Jwt> audienceValidator = jwt ->
            jwt.getAudience().contains("my-orders-api")
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(
                            new OAuth2Error("invalid_token", "Required audience is missing", null));

    OAuth2TokenValidator<Jwt> defaultValidators = JwtValidators.createDefaultWithIssuer(
            "https://my-keycloak.example.com/realms/my-app");

    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(defaultValidators, audienceValidator));
    return decoder;
}
```

# OAuth2 Client Configuration -- Logging In via an External Identity Provider

--> The client starter is what a browser-facing application uses to let users log in through Keycloak/Auth0/Okta/Google rather than maintaining its own username/password database at all.

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          keycloak:
            client-id: my-web-app
            client-secret: ${KEYCLOAK_CLIENT_SECRET}      # never hardcode -- inject from a secrets manager/env var
            authorization-grant-type: authorization_code
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
            scope: openid, profile, email
        provider:
          keycloak:
            issuer-uri: https://my-keycloak.example.com/realms/my-app
```

```java
@Configuration
@EnableWebSecurity
public class OAuth2ClientConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/", "/public/**").permitAll()
                    .anyRequest().authenticated()
            )
            // Enables the full Authorization Code + OIDC login flow: redirects
            // unauthenticated users to Keycloak's login page, handles the
            // callback, and establishes a local session/principal from the
            // resulting ID token -- all the choreography from Theory file 01's
            // Authorization Code walkthrough, wired up declaratively.
            .oauth2Login(oauth2 -> oauth2.defaultSuccessUrl("/dashboard"));

        return http.build();
    }
}
```

--> **What happens with zero extra code beyond this config** -- an unauthenticated request to a protected page gets redirected to Keycloak's hosted login form; after the user authenticates there and consents, Keycloak redirects back to `/login/oauth2/code/keycloak`; Spring Security's registered filter completes the authorization-code exchange, validates the resulting ID token, and builds an `OAuth2User`/`OidcUser` principal accessible in controllers via `@AuthenticationPrincipal`.

```java
@RestController
public class ProfileController {

    @GetMapping("/api/me")
    public Map<String, Object> me(@AuthenticationPrincipal OidcUser principal) {
        // Claims come directly from the identity provider's ID token --
        // no local user table lookup needed for basic profile info.
        return Map.of(
                "name", principal.getFullName(),
                "email", principal.getEmail(),
                "subject", principal.getSubject()
        );
    }
}
```

# Identity Provider Concepts -- Keycloak / Auth0 / Okta

--> All three (and most managed identity providers) play the AUTHORIZATION SERVER role from Theory file 01, and expose broadly the same standardized surface, which is exactly why the same Spring configuration shape (`issuer-uri`, client registration) works against any of them with only URLs/credentials changing.

| Concept | What it is | Where it shows up in Spring config |
|---|---|---|
| **Realm / Tenant** | An isolated namespace of users, clients, and roles within the identity provider (Keycloak calls it a "realm"; Auth0/Okta use "tenant"/"org") | Baked into the `issuer-uri` path |
| **Client registration** | The identity provider's record of YOUR app -- its client ID, secret, allowed redirect URIs, allowed grant types | `spring.security.oauth2.client.registration.*` |
| **Well-known/discovery document** | A standardized JSON document (`/.well-known/openid-configuration`) advertising the provider's token/authorize/JWKS endpoints | What `issuer-uri` auto-fetches under the hood |
| **JWKS endpoint** | Publishes the provider's current public signing key(s) | Auto-consumed via `issuer-uri`, or set explicitly via `jwk-set-uri` |
| **Realm/client roles → claims** | The provider embeds role/permission info in the token as claims (Keycloak nests these under `realm_access.roles`; the exact claim NAME and SHAPE varies by provider) | Requires a custom `JwtAuthenticationConverter` (shown above) tailored to that provider's specific claim structure |

--> **Why the exact claim structure varies and why that matters in code** -- OAuth2/OIDC standardizes the FLOWS and TOKEN FORMAT, but NOT every custom claim's name or nesting. Keycloak's roles typically live at `realm_access.roles` (an array) or `resource_access.<client-id>.roles`; other providers use flatter or differently-named claims entirely. This is precisely why `jwtAuthenticationConverter()` above is written as a customizable bean rather than something Spring can universally infer -- moving from one identity provider to another typically means adjusting exactly this claim-mapping logic, and little else in your security config.
--> **Local development without a live identity provider** -- Spring Security also supports `spring-security-oauth2-authorization-server` for standing up your OWN minimal authorization server for local testing/demos, and Testcontainers has ready-made Keycloak images for integration tests -- both let you exercise the full resource-server/client config above without depending on a live external provider.

# Common Gotchas

--> **Confusing the resource-server and client starters** -- adding `oauth2-client` to a pure backend API (which should just be validating incoming tokens as a resource server) pulls in login-flow machinery it doesn't need; adding only `oauth2-resource-server` to a browser-facing app that needs users to log in leaves it with no way to actually initiate that login.
--> **Relying only on `issuer-uri` and forgetting audience validation** -- a token legitimately issued by the same authorization server for a DIFFERENT API will otherwise pass validation on a resource server it was never intended for.
--> **Assuming every identity provider's role claim has the same name/shape** -- Keycloak, Auth0, and Okta each structure this differently; the `JwtAuthenticationConverter` must be tailored per provider, not copy-pasted assuming a universal `roles` claim.
--> **Hardcoding `client-secret` in `application.yml` checked into source control** -- always inject via environment variable or a secrets manager, exactly as with any other credential.
--> **Forgetting the redirect URI must be registered EXACTLY (scheme, host, path) on the identity provider's side** -- a mismatch causes the flow to fail at the callback step with an error the identity provider raises, not Spring.

# Best Practices Summary

--> Add `oauth2-resource-server` to services that validate incoming tokens; add `oauth2-client` to services that initiate login flows on behalf of a user -- add both only to an app that genuinely does both.
--> Prefer `issuer-uri`-based configuration over manually wiring JWKS/public keys -- it gets you automatic key rotation handling and discovery for free.
--> Always add an explicit audience check unless you're certain every token issued by your authorization server is intended for every one of your resource servers.
--> Write a provider-specific `JwtAuthenticationConverter` rather than assuming a universal claim shape for roles/authorities.
--> Keep client secrets and any identity-provider credentials out of source control, injected via environment/secrets manager.
--> Use a real identity provider (or a Testcontainers-managed one) in integration tests rather than mocking the OAuth2 machinery entirely, to catch claim-mapping and redirect-URI issues before production.
