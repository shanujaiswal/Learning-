# OAuth2 -- The Problem It Actually Solves

--> **The problem BEFORE OAuth2** -- imagine a third-party app wants to read your Google Contacts. The naive approach: you hand the app your Google username and password, and it logs in "as you." This is catastrophic -- the app now has your FULL Google password, can do anything your account can do (read email, change settings, reset other passwords), you cannot limit what it accesses, and revoking access means changing your password (breaking every other app you'd given it to too).
--> **What OAuth2 provides instead** -- a way for a user to grant a third-party application LIMITED, REVOCABLE access to a specific resource, without ever handing that application the user's actual credentials. The application receives a narrowly-scoped, revocable TOKEN instead of a password -- it can prove "I'm allowed to read this user's contacts" without ever having been able to log in as that user directly.
--> **OAuth2 is an AUTHORIZATION framework, not an authentication protocol** -- this is the single most common conceptual confusion. OAuth2 answers "is this application allowed to access this resource on the user's behalf, and with what scope?" It does NOT, by itself, tell the application WHO the user is in any standardized way. (That identity layer is what OpenID Connect, OIDC, adds on top of OAuth2 -- covered briefly at the end of this file.) Treating a raw OAuth2 access token as proof of identity is a common and dangerous mistake.

# The Four Roles

--> OAuth2 defines the interaction between exactly four parties. Every grant type (flow) below is just a different choreography between the same four roles.

| Role | Who it usually is | Responsibility |
|---|---|---|
| **Resource Owner** | The end user | Owns the protected data and grants (or denies) consent for it to be accessed |
| **Client** | The third-party app (web app, mobile app, another backend service) | Wants access to the protected resource; never sees the resource owner's password |
| **Authorization Server** | Keycloak, Auth0, Okta, Google, your own Spring Authorization Server | Authenticates the resource owner, obtains consent, and issues tokens |
| **Resource Server** | The API that actually holds the protected data | Accepts a token on incoming requests and serves data if the token is valid and sufficiently scoped |

--> **A concrete walkthrough using the Google Contacts example** -- Resource Owner = you; Client = the third-party contacts-backup app; Authorization Server = Google's OAuth2 server (accounts.google.com); Resource Server = the Google Contacts API. The client never touches your Google password -- it redirects you TO Google's authorization server, you authenticate directly with Google and approve a specific scope ("read your contacts"), and Google hands the client a token scoped to exactly that.
--> **In many real systems, the Authorization Server and Resource Server are operated by different teams or even different companies** -- but in a typical microservices setup inside one organization, you'll often run your OWN authorization server (or a managed one like Keycloak) and your OWN resource servers (your internal APIs), with the same four-role model still applying internally.
--> **Access token vs refresh token** -- the AUTHORIZATION SERVER issues both. The ACCESS TOKEN is what the client presents to the resource server on every API call -- short-lived by design (minutes to an hour) to limit the damage window if it leaks. The REFRESH TOKEN is presented back to the authorization server (never to the resource server) to obtain a new access token without forcing the resource owner to re-authenticate -- typically longer-lived, and must be stored more carefully since it represents a much longer-lived capability.

# Grant Types (Flows) -- Different Choreographies for Different Situations

--> A "grant type" is simply the sequence of steps used to actually obtain a token. Which one applies depends on WHO is asking (a human sitting at a browser? a backend service with no user at all?) and HOW MUCH that client can be trusted to keep a secret.

## Authorization Code Flow -- the Default for User-Facing Apps

--> This is the flow to reach for whenever a HUMAN is present and needs to log in via a browser (a web app, a mobile app, an SPA). It is deliberately structured so the client never sees the resource owner's password, and (with PKCE, below) never even directly handles the raw authorization code without proof of possession.

```text
1. Client redirects the browser to the Authorization Server's /authorize endpoint,
   including: client_id, redirect_uri, requested scope, a random "state" value,
   and (for PKCE) a code_challenge.
        |
        v
2. Authorization Server shows a login form (if not already logged in) and a
   consent screen ("App X wants to: read your profile, read your contacts").
   The resource owner authenticates directly with the AUTHORIZATION SERVER --
   the client never sees these credentials.
        |
        v
3. Authorization Server redirects the browser back to the client's redirect_uri
   with a short-lived, single-use AUTHORIZATION CODE and the original "state" value.
        |
        v
4. Client's BACKEND (not the browser) exchanges this code for tokens by calling
   the Authorization Server's /token endpoint directly, server-to-server,
   presenting its client_id + client_secret (or PKCE code_verifier) as proof
   it's the same client that started the flow.
        |
        v
5. Authorization Server validates the code + secret/verifier and responds with
   an access token (and typically a refresh token).
        |
        v
6. Client calls the Resource Server's API with "Authorization: Bearer <access_token>".
```

--> **Why the extra "code" indirection instead of returning the token directly in step 3?** -- the redirect in step 3 happens via the browser's address bar, which is comparatively exposed (browser history, referrer headers, logs). A short-lived, single-use CODE that's only exchangeable by presenting a secret the browser never had access to limits what a leaked redirect URL can actually be used for -- the code alone, without the client secret/verifier, is useless.
--> **The `state` parameter defends against CSRF on the OAuth2 flow itself** -- the client generates a random value before step 1, and verifies the SAME value comes back in step 3. Without it, an attacker could trick a victim's browser into completing an authorization flow initiated by the attacker, potentially binding the victim's session to the attacker's account.

## Client Credentials Flow -- No User Involved At All

--> Used for pure SERVICE-TO-SERVICE authentication, where there is no human resource owner in the picture -- a backend job, a scheduled batch process, or one microservice calling another entirely on its own behalf (not on behalf of any particular user).

```text
1. Client calls the Authorization Server's /token endpoint directly,
   presenting ONLY its own client_id + client_secret (no user, no browser,
   no redirect at all):

       POST /token
       grant_type=client_credentials
       client_id=inventory-service
       client_secret=***
       scope=orders.read
        |
        v
2. Authorization Server verifies the client's own credentials and issues
   an access token representing "this SERVICE," not any particular user.
        |
        v
3. Client calls the Resource Server with that token, same as any other flow.
```

--> **This is the natural fit for internal microservice-to-microservice calls** (see Theory file 05 for the fuller token-propagation picture) -- there's no login screen because there's no person logging in; the "identity" the token represents is the calling SERVICE itself.
--> **No refresh token in this flow** -- since there's no user session to preserve, the client simply requests a brand-new access token again via the same client_credentials call whenever the old one expires. There's nothing to "refresh" on behalf of, because nothing about a user was ever established.

## Resource Owner Password Credentials (ROPC) -- Legacy, Avoid

--> The client collects the resource owner's username and password directly and exchanges them for a token in one call. This defeats the entire point of OAuth2 (the client DOES see the raw password) and is retained in the spec mainly for legacy migration scenarios. **The OAuth 2.1 draft formally removes this grant type.** Do not use it in new designs -- if a first-party client absolutely must collect credentials directly, that's ultimately still better served by a proper Authorization Code flow with the authorization server's own hosted login page.

## Implicit Flow -- Deprecated

--> An older flow designed for pure-browser JavaScript apps (no backend to safely hold a client secret) that returned the access token directly in the redirect URL fragment, skipping the authorization-code exchange step. **Deprecated by OAuth 2.1** -- the token ends up exposed in browser history and referrer data with no code-exchange indirection, and offers no refresh token. Modern guidance for SPAs is Authorization Code + PKCE instead (below), even though the SPA still can't keep a secret -- PKCE solves that problem without the Implicit flow's exposure.

## Grant Type Selection at a Glance

| Situation | Grant Type |
|---|---|
| Web app / mobile app / SPA where a human logs in via browser | Authorization Code (+ PKCE) |
| Public client that can't hold a secret (SPA, native mobile app) | Authorization Code + PKCE |
| Backend service acting on its own behalf, no user present | Client Credentials |
| Legacy first-party migration only | Resource Owner Password Credentials (avoid otherwise) |
| Browser-only app, older guidance | Implicit (deprecated -- use PKCE instead) |

# PKCE (Proof Key for Code Exchange) -- Securing Public Clients

--> **The problem PKCE solves** -- a "public client" (a mobile app, an SPA) CANNOT safely store a client_secret -- anything shipped in an app binary or browser bundle can be extracted by a sufficiently motivated attacker. Without a secret, the authorization-code exchange in step 4 above has nothing to prove "I'm the same client that started this flow" -- so if the authorization code were somehow intercepted (a malicious app registering the same redirect URI scheme on the device, for instance), an attacker could exchange it for a token themselves.
--> **How PKCE fixes this without a stored secret** -- the client generates a random, per-flow secret called a `code_verifier` (never sent anywhere yet), derives a `code_challenge` from it (a SHA-256 hash, base64url-encoded), and sends only the CHALLENGE in step 1's `/authorize` redirect. Later, in step 4's token exchange, the client reveals the original `code_verifier`. The authorization server independently hashes the verifier and checks it matches the challenge it received back in step 1 -- proving this token-exchange request came from the SAME client instance that started the flow, without either party ever transmitting a long-lived secret.

```text
Client generates: code_verifier = random 43-128 char string (kept locally, never sent yet)
Client computes:  code_challenge = BASE64URL( SHA256(code_verifier) )

Step 1 (/authorize):  ...&code_challenge=XYZ&code_challenge_method=S256
Step 4 (/token):       ...&code_verifier=<the original random string>

Authorization Server: SHA256(received code_verifier) == stored code_challenge ? proceed : reject
```

--> **PKCE is no longer "just for public clients"** -- OAuth 2.1 recommends using PKCE on the Authorization Code flow UNCONDITIONALLY, even for confidential (secret-holding) clients, as defense-in-depth against authorization-code interception regardless of client type. Treat "Authorization Code flow" and "Authorization Code + PKCE" as effectively the same recommendation going forward.

# Scopes -- Limiting What a Token Can Do

--> A SCOPE is a string (`read:contacts`, `orders.write`, `openid profile email`) the client requests during authorization, representing a specific slice of access. The resource owner's consent screen is built from the requested scopes ("App X wants to: read your contacts"), and the resulting access token is limited to exactly what was granted -- a resource server should reject (403) a request for an action the token's scopes don't cover, even if the token is otherwise perfectly valid and unexpired.
--> **Principle of least privilege applies directly here** -- request the narrowest scope that accomplishes the task. A client that only ever needs to read a user's profile should never request write access to their entire account, both because users are (rightly) more hesitant to consent to broad scopes, and because a leaked token then does less damage.

# OpenID Connect (OIDC) -- The Identity Layer OAuth2 Doesn't Provide

--> Since OAuth2 alone says nothing standardized about WHO the resource owner is, OIDC is a thin identity layer built ON TOP of OAuth2 that adds exactly that. Requesting the `openid` scope in an Authorization Code flow causes the authorization server to additionally return an **ID Token** -- a JWT (see Theory file 02) containing standardized identity claims (`sub`, `email`, `name`, `iss`, `aud`).
--> **ID token vs access token -- do not confuse these** -- the ACCESS TOKEN is for the RESOURCE SERVER ("here's proof this client can access this API with this scope"); the ID TOKEN is for the CLIENT ITSELF ("here's proof of who just logged in"). A client should never forward its ID token to a resource server as if it were an access token, and a resource server should never treat an access token as identity proof without OIDC's ID token semantics behind it.
--> **This is exactly the mechanism behind "Log in with Google / Keycloak / Auth0"** -- the identity-provider integration covered in Theory file 03 is, under the hood, an OAuth2 Authorization Code flow with the `openid` scope added, consuming the resulting ID token to establish the user's identity in your own application.

# Common Gotchas

--> **Treating an OAuth2 access token as an authentication credential** -- it proves "this client may access this resource with this scope," not "this is who the user is." Use OIDC's ID token (or an explicit token-introspection call) for identity, not a bare access token.
--> **Using ROPC or Implicit flow in new designs** -- both are legacy/deprecated; use Authorization Code + PKCE for anything involving a browser or mobile app, and Client Credentials for pure service-to-service calls.
--> **Skipping the `state` parameter** -- leaves the Authorization Code flow open to CSRF against the authorization step itself.
--> **Requesting overly broad scopes "just in case"** -- inflates both the consent friction for users and the blast radius if the resulting token leaks.
--> **Forgetting PKCE for public clients** -- an SPA or mobile app doing a bare Authorization Code exchange with no `code_verifier`/`code_challenge` has no defense against authorization-code interception.
--> **Confusing the four roles when the same organization runs more than one** -- running your own Keycloak (Authorization Server) alongside your own APIs (Resource Servers) doesn't collapse the roles into one; the same separation of concerns (and separate token validation logic) still applies internally.

# Best Practices Summary

--> Use Authorization Code + PKCE for anything a human logs into via a browser or mobile app -- unconditionally include PKCE, even for confidential clients.
--> Use Client Credentials for backend service-to-service calls with no human resource owner involved.
--> Avoid ROPC and Implicit flow entirely in new work; both are legacy patterns superseded by the above.
--> Always validate the `state` parameter on the Authorization Code flow's redirect back to the client.
--> Request the narrowest scope that accomplishes the client's actual need -- least privilege reduces both consent friction and leak impact.
--> Never conflate an OAuth2 access token with proof of identity -- use OIDC's ID token (or introspection) when the application actually needs to know who the user is.
--> Keep access tokens short-lived and refresh tokens longer-lived but more carefully stored, mirroring the JWT expiration guidance in Theory file 02.
