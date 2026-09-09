# Authentication vs Authorization -- Two Different Questions

--> These two words get used loosely in everyday conversation but mean precisely different things in security, and almost every Spring Security concept sorts cleanly into one bucket or the other.
--> **Authentication ("who are you?")** -- verifying an identity claim. Logging in with a username/password, presenting a valid JWT, or supplying an API key are all authentication mechanisms -- the end result is the system deciding "this request genuinely comes from user X" (or rejecting it if it can't verify that).
--> **Authorization ("what are you allowed to do?")** -- once identity is established, deciding whether THIS identity is permitted to perform THIS specific action on THIS specific resource. A logged-in user is authenticated, but might still be forbidden from deleting another user's data, or from accessing an admin-only endpoint.
--> **Why the distinction matters in practice** -- they fail with DIFFERENT HTTP status codes and different fixes: a failed authentication is `401 Unauthorized` (you don't know who I am, or you reject my claimed identity -- the fix is to log in / supply valid credentials), a failed authorization is `403 Forbidden` (I know exactly who you are, and the answer is still no -- no amount of re-authenticating fixes this, the identity simply lacks permission). Spring Security models both as distinct concerns with distinct configuration, and mixing them up in code (returning 401 for a permission problem, or 403 for a missing/invalid token) confuses API clients and monitoring alike.

| | Authentication | Authorization |
|---|---|---|
| Question answered | Who are you? | What can you do? |
| Failure status | `401 Unauthorized` | `403 Forbidden` |
| Spring Security concept | `AuthenticationManager`, `UserDetailsService` | `SecurityFilterChain` rules, `@PreAuthorize`, roles/authorities |
| Example | Username + password login, JWT signature check | "only ADMIN can delete users" |

# The Security Filter Chain -- How a Request Actually Gets Checked

--> Spring Security works by inserting a CHAIN OF SERVLET FILTERS in front of your application's normal request handling -- every incoming HTTP request passes through this chain BEFORE it ever reaches a `@RestController` method. Each filter in the chain has one job (parse a token, check a session, enforce CSRF, etc.), and the request only reaches your controller if it survives every filter that would reject it.

```text
Incoming HTTP request
        |
        v
 SecurityContextPersistenceFilter  -- loads/creates the SecurityContext for this request
        |
        v
 UsernamePasswordAuthenticationFilter / your custom JWT filter  -- attempts to authenticate
        |                                                          the request (form login, or
        |                                                          "read the Authorization header,
        |                                                          validate the token")
        v
 ExceptionTranslationFilter  -- catches AuthenticationException/AccessDeniedException thrown
        |                       further down the chain and converts them into 401/403 responses
        v
 FilterSecurityInterceptor / AuthorizationFilter  -- makes the actual ALLOW/DENY decision for
        |                                             this specific URL, based on configured rules
        v
 Your @RestController method  (only reached if authorized)
```

--> **`SecurityContext` / `SecurityContextHolder`** -- once a request is authenticated, Spring Security stores the resulting `Authentication` object (who the user is, their granted authorities/roles) in a `SecurityContext`, accessible anywhere in that request's processing via `SecurityContextHolder.getContext().getAuthentication()`. By default this is thread-local and tied to one request -- which is exactly why STATELESS APIs (JWT-based, covered below) need to re-establish it on every single incoming request, since nothing is remembered between requests server-side.
--> **This filter-chain model is why Spring Security configuration feels different from typical Spring code** -- you're not writing `if` statements inside your controllers to check permissions; you're declaratively configuring which filters run, in what order, and what rules the authorization filter applies to which URL patterns. The `SecurityFilterChain` bean (next section) is where that declarative configuration lives.

# Configuring Security -- SecurityFilterChain

--> Modern Spring Security (5.7+) configures the filter chain by declaring a `SecurityFilterChain` `@Bean` inside a `@Configuration` class -- this REPLACES the older `WebSecurityConfigurerAdapter` subclassing approach, which is now deprecated. If you see `extends WebSecurityConfigurerAdapter` in a tutorial or an older codebase, treat it as legacy style.

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())                         // see CSRF note below -- fine for stateless JWT APIs
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))   // no HttpSession -- every request re-authenticates
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**", "/api/public/**").permitAll()   // no authentication required
                .requestMatchers("/api/admin/**").hasRole("ADMIN")              // requires ROLE_ADMIN authority
                .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll() // GETs open, writes protected
                .anyRequest().authenticated()                                   // everything else needs a valid identity
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)  // custom JWT filter slotted into the chain
            .build();

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();          // NEVER store or compare plaintext passwords
    }
}
```

--> **Rule ordering matters -- most specific first** -- `authorizeHttpRequests` rules are evaluated TOP TO BOTTOM, and the FIRST matching rule wins. Putting a broad `anyRequest().authenticated()` before a more specific `permitAll()` rule for a public endpoint would incorrectly lock that endpoint down, because the broad rule matches first and the more specific one below it never gets consulted.
--> **`hasRole("ADMIN")` vs `hasAuthority("ROLE_ADMIN")`** -- `hasRole` is sugar that automatically prepends the `"ROLE_"` prefix, so `hasRole("ADMIN")` and `hasAuthority("ROLE_ADMIN")` check the exact same thing. A common gotcha is writing `hasRole("ROLE_ADMIN")`, which actually checks for an authority literally named `"ROLE_ROLE_ADMIN"` and silently never matches.
--> **CSRF (Cross-Site Request Forgery) protection** -- enabled by default in Spring Security, designed for traditional session-cookie-based browser apps (where a malicious site could otherwise trick a logged-in user's browser into submitting an unwanted request using their existing session cookie). **For a stateless, token-based (JWT) REST API with no server-side session and no cookie-based auth, CSRF protection is not applicable and is conventionally disabled** -- the attack it prevents doesn't apply when there's no ambient session cookie for a foreign page to piggyback on. It should stay ENABLED for any app still using session/cookie-based authentication.

# UserDetailsService and the Authentication Flow

--> **`UserDetailsService`** is the interface Spring Security calls to look up a user's credentials and authorities during authentication -- you implement `loadUserByUsername(String username)`, typically backed by a Spring Data JPA repository in a real app.

```java
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("No user: " + username));

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getHashedPassword())        // already BCrypt-hashed -- never plaintext
                .authorities(user.getRoles().toArray(new String[0]))
                .build();
    }
}
```

--> **Password hashing -- `PasswordEncoder`** -- passwords are NEVER stored or compared as plaintext. `BCryptPasswordEncoder` is the standard choice: it's a deliberately slow, salted hashing algorithm designed to resist brute-force attacks even if the password hash database leaks. Registering a `PasswordEncoder` bean (as shown above) is what Spring Security uses both when a new password is set (`encoder.encode(rawPassword)`) and when a login attempt is checked (`encoder.matches(rawPassword, storedHash)`).
--> **`@PreAuthorize` -- method-level authorization** -- an alternative (or complement) to URL-pattern rules in `SecurityFilterChain`, letting you express authorization rules directly on a SERVICE or CONTROLLER method using a Spring Expression Language (SpEL) condition. Requires `@EnableMethodSecurity` on a configuration class.

```java
@Configuration
@EnableMethodSecurity                 // turns on @PreAuthorize / @PostAuthorize / @Secured
public class MethodSecurityConfig { }

@Service
public class ProductAdminService {

    @PreAuthorize("hasRole('ADMIN')")                              // simple role check
    public void deleteProduct(Long id) { ... }

    @PreAuthorize("#userId == authentication.principal.id or hasRole('ADMIN')")  // owner OR admin
    public void updateProfile(Long userId, ProfileUpdateRequest request) { ... }
}
```

--> **`SecurityFilterChain` URL rules vs `@PreAuthorize` method rules -- when to use which** -- URL-based rules are simple, centralized in one place, and best for coarse-grained "this whole path prefix requires this role" decisions. `@PreAuthorize` is better for fine-grained, data-dependent rules ("only the owner of THIS specific resource, or an admin") that can't be expressed by URL pattern alone, since it has access to the actual method arguments and the authenticated principal via SpEL. Many real projects use BOTH -- coarse gatekeeping at the filter-chain level, fine-grained checks at the method level.

# JWT (JSON Web Token) Overview

--> **The core problem JWT solves** -- traditional session-based auth requires the SERVER to remember something (a session ID mapped to user data, stored in memory or a shared session store) between requests. That's fine for a single server, but becomes awkward once you have multiple server instances behind a load balancer (which one has the session? do they all need to share a session store?) or want a truly STATELESS API. JWT moves the "remembered" data INTO the token itself, cryptographically signed so the server can trust it without looking anything up.

## JWT Structure -- Three Base64URL Segments Joined by Dots

```text
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhbGljZSIsInJvbGVzIjpbIlVTRVIiXSwiZXhwIjoxNzk5MDAwMDAwfQ.4f8a...signature

  [ HEADER ]                    .  [ PAYLOAD / CLAIMS ]                      . [ SIGNATURE ]
  base64url({                      base64url({                                HMAC-SHA256( base64url(header)
    "alg":"HS256",                   "sub":"alice",                                       + "." +
    "typ":"JWT"                      "roles":["USER"],                             base64url(payload),
  })                                 "exp":1799000000                               secretKey )
                                   })
```

--> **Header** -- states the signing algorithm (`HS256` = HMAC-SHA256 with a shared secret, `RS256` = RSA public/private key pair) and token type.
--> **Payload (claims)** -- the actual DATA the token carries: `sub` (subject -- typically the username/user id), `exp` (expiration, Unix timestamp), `iat` (issued-at), plus any custom claims (roles, permissions, tenant id). **Critically -- the payload is only BASE64-ENCODED, not encrypted.** Anyone who intercepts a JWT can decode and READ its claims trivially (it's not secret data protection) -- what the signature guarantees is that the claims haven't been TAMPERED WITH, not that they're hidden. Never put secrets (passwords, credit card numbers) in a JWT payload.
--> **Signature** -- computed over the header and payload using a secret key (HMAC) or a private key (RSA/ECDSA). The server verifies this signature on every incoming request using the same secret (or the corresponding public key) -- if a client tampers with the payload (e.g. changing `"roles":["USER"]` to `"roles":["ADMIN"]`), the signature no longer matches and verification fails.

## Stateless Auth Flow

```text
1. POST /api/auth/login  {username, password}
        |
        v
   Server verifies credentials against the database (UserDetailsService + PasswordEncoder)
        |
        v
   Server generates a JWT, signs it with its secret key, returns it to the client
        |
        v
2. Client stores the JWT (memory, secure storage -- NOT localStorage if XSS is a concern)
        |
        v
3. Every subsequent request:  Authorization: Bearer <jwt>
        |
        v
   Server's JWT filter: extract token -> verify signature -> check expiry -> extract claims
        |                -> build an Authentication object -> set it on SecurityContextHolder
        v
   Request proceeds through authorization checks and into the controller, AS IF the user had
   just logged in on THIS request -- nothing about the user was remembered between requests.
```

--> **"Stateless" is the key word** -- the server holds NO session data between requests; every request is independently, fully re-authenticated purely from the token it carries. This is exactly why `SessionCreationPolicy.STATELESS` is set in the filter chain config above -- it tells Spring Security "don't bother creating or reading an `HttpSession` at all, everything needed is in the token."
--> **Expiration and refresh tokens** -- a JWT's `exp` claim is typically short-lived (minutes to a couple hours) to limit the damage window if a token is stolen. A separate, longer-lived REFRESH TOKEN (often stored more securely, sometimes as an HttpOnly cookie) is used to obtain a new short-lived access token without forcing the user to log in again with credentials -- this refresh flow is a common production addition not shown in the skeleton below.
--> **Logout with stateless JWT is inherently awkward** -- since the server remembers nothing, there's no session to "destroy" server-side. Common approaches: keep tokens short-lived so a stolen/leaked one expires quickly regardless; maintain a server-side DENYLIST of revoked token IDs (`jti` claim) checked on each request (which reintroduces a bit of server-side state, trading pure statelessness for the ability to truly revoke); or simply have the client discard the token (works for "user clicked logout," but does nothing if the token was already stolen).

# Common Gotchas

--> **Confusing 401 and 403** -- 401 means the request's identity couldn't be established or verified at all (missing/invalid/expired token); 403 means the identity IS known and valid, but lacks permission for this specific action. Returning the wrong one misleads API clients about what fix is actually needed.
--> **`hasRole("ROLE_X")` double-prefixing** -- `hasRole` already adds `"ROLE_"` internally; passing an authority string that already starts with `ROLE_` produces a check against `"ROLE_ROLE_X"` that silently never matches anything.
--> **Storing plaintext or reversibly-encrypted passwords** -- always hash with `BCryptPasswordEncoder` (or a similarly designed slow hash); never store passwords in a form that lets anyone with database access read them back out directly.
--> **Disabling CSRF protection on a session/cookie-based app "because a JWT tutorial said so"** -- CSRF disabling is appropriate for stateless, token-based auth with no ambient session cookie; a cookie/session-based app still needs it.
--> **Treating JWT payload contents as confidential** -- the payload is base64-encoded, not encrypted; anyone holding the token can read every claim in it. Never place secrets or sensitive PII directly in a JWT payload.
--> **Overly broad `authorizeHttpRequests` rule ordering** -- a broad rule declared before a narrower, more specific one can shadow it, since the first match wins; always order rules from most specific to most general.
--> **No expiration, or excessively long-lived tokens** -- a stolen token with a huge `exp` window remains valid (and unrevokable without a denylist) for as long as it hasn't expired; keep access tokens short-lived and use a refresh-token flow for longevity.
--> **Forgetting `@EnableMethodSecurity`** -- `@PreAuthorize`/`@PostAuthorize`/`@Secured` annotations are silently ignored (no error, they just never fire) unless method security is explicitly enabled on a configuration class.

# Best Practices Summary

--> Keep authentication (identity) and authorization (permission) conceptually and code-wise separate -- 401 for the former's failures, 403 for the latter's.
--> Configure security via a `SecurityFilterChain` `@Bean` -- treat `WebSecurityConfigurerAdapter` subclassing as legacy/deprecated style if encountered in older material.
--> Order `authorizeHttpRequests` rules from most specific to most general -- the first matching rule wins.
--> Always hash passwords with `BCryptPasswordEncoder` (or equivalent) -- never store or compare plaintext.
--> Use `SessionCreationPolicy.STATELESS` + a JWT filter for token-based APIs; disable CSRF only in that stateless context, not for cookie/session-based apps.
--> Keep JWTs short-lived, never put secrets in the payload, and pair short-lived access tokens with a longer-lived refresh token flow for usability.
--> Combine coarse URL-based rules (`SecurityFilterChain`) with fine-grained `@PreAuthorize` method rules where a decision depends on the specific resource/owner, not just the URL shape.
--> Remember `@PreAuthorize` needs `@EnableMethodSecurity` to do anything at all.
