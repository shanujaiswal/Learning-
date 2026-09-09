### Identity and Access Management (IAM), SSO, and OAuth

--> Authentication answers "who are you?" (proving identity — a password, a fingerprint, a hardware key). Authorization answers "what are you allowed to do?" (permissions/roles, checked AFTER identity is already confirmed). A login screen is authentication; the fact that an "Employee" role can't open the "Admin Payroll" page is authorization. Almost every access-control bug in the real world is a confusion between these two — a system correctly identifies a user but then fails to check whether that specific user should be allowed to see a specific resource (this exact bug class is called IDOR — Insecure Direct Object Reference — covered in the AppSec chapter).
--> Identity and Access Management (IAM) is the umbrella discipline covering how an organization creates, authenticates, authorizes, and eventually de-provisions user identities across all its systems. At enterprise scale (thousands of employees, hundreds of apps), IAM is what stops "who has access to what" from becoming an unanswerable question.

## Single Sign-On (SSO)

--> SSO lets a user authenticate ONCE with a central Identity Provider (IdP) and then access many different, independent applications (Service Providers / SPs) without logging in again to each one separately.
--> Without SSO: an employee has 40 separate passwords for Salesforce, Slack, AWS, the internal wiki, the expense system, etc. Each one is a separate attack surface, a separate password to phish, and a separate account to remember to disable when the employee leaves.
--> With SSO: the employee logs into one IdP (e.g., Okta, Azure AD / Entra ID, Ping Identity). That IdP then vouches for their identity to every other connected app. Login happens once per session; access everywhere else is automatic.

Why SSO matters at scale, beyond convenience:
1. Centralized deprovisioning
   --> When an employee is fired, disabling their ONE IdP account instantly cuts off access to every connected application at once. Without SSO, IT has to remember to manually disable 40 separate accounts — and inevitably misses some, leaving "ghost accounts" that are a classic post-employment breach vector.
2. Centralized MFA enforcement
   --> MFA is configured once at the IdP level and automatically applies to every downstream app, instead of hoping each of the 40 apps implemented MFA correctly on their own.
3. Centralized audit logging
   --> One place to see "who logged into what, and when" across the whole company, instead of piecing together logs from 40 different systems during an investigation.
4. Reduced password fatigue -> fewer weak/reused passwords
   --> Users forced to remember fewer passwords are less likely to reuse the same weak password everywhere, which is one of the biggest real-world breach root causes (credential stuffing).

## SAML (Security Assertion Markup Language)

--> SAML is an older (early-2000s), XML-based standard for exchanging authentication and authorization data between an Identity Provider (IdP) and a Service Provider (SP). It's still extremely common in large enterprises (Okta, ADFS, PingFederate all speak SAML) even though OAuth/OIDC (below) are the modern default for consumer-facing apps.

--> Two key roles:
--> Identity Provider (IdP) — the system that actually authenticates the user and holds their identity (e.g., Okta, Azure AD).
--> Service Provider (SP) — the application the user wants to access (e.g., a SaaS tool like Salesforce), which trusts the IdP's word instead of managing its own password database.

==> SAML Assertion Flow (SP-initiated, the common case)
```
1. User -> tries to open https://saas-app.com/dashboard  (the SP)
2. SP    -> sees no valid session, redirects browser to the IdP's SSO URL
            with a SAML AuthnRequest
3. IdP   -> prompts the user to log in (if not already logged in) and
            checks their credentials/MFA
4. IdP   -> generates a signed XML "SAML Assertion" containing:
              - the user's identity (e.g., email, username)
              - attributes (e.g., department, role, group membership)
              - a digital signature (so the SP can verify it wasn't forged)
              - a short validity window (prevents replay of an old assertion)
5. IdP   -> browser POSTs this signed assertion back to the SP's
            Assertion Consumer Service (ACS) URL
6. SP    -> verifies the signature against the IdP's known public
            certificate, trusts the assertion, creates a local session
            for the user, and grants access to the dashboard
```
--> Critical security detail: the SP never sees the user's password. The SP only ever sees a signed assertion vouching for identity — this is the entire point of federated identity: the app you're logging into never has to store or handle credentials at all.
--> Common real-world SAML misconfiguration: an SP that doesn't properly validate the assertion's digital signature (or accepts assertions signed by an untrusted/attacker-controlled certificate) — this allows an attacker to forge a fake assertion and log in as anyone. This exact class of bug has been found in real IdP/SP implementations (e.g., historical "XML Signature Wrapping" attacks against SAML libraries).

## OAuth 2.0

--> OAuth 2.0 is NOT an authentication protocol — it's an AUTHORIZATION protocol. It answers "can this app access this specific resource on my behalf?" not "who is this user?" (that distinction matters — see the OIDC section below). The single most common real-world confusion in security interviews is calling OAuth an authentication mechanism; it isn't one by itself.
--> Classic real-world example: "Sign in with Google" isn't really OAuth's original purpose — its original purpose is more like "Allow this photo-printing app to access your Google Photos without ever giving it your Google password."

--> Four roles in OAuth:
--> Resource Owner — the user who owns the data (you).
--> Client — the third-party application requesting access (the photo-printing app).
--> Authorization Server — issues tokens after the user approves access (Google's auth server).
--> Resource Server — the API that actually holds the protected data and accepts the token (the Google Photos API).

==> Authorization Code Flow — Worked Example
--> This is the most secure and most common OAuth flow, used by any app with a backend server that can keep a secret. Concrete example: "PrintMyPics" (the Client) wants to access a user's Google Photos (the Resource Server), authorized via Google's Authorization Server.

```
Step 1 — User clicks "Connect Google Photos" inside PrintMyPics
   Browser is redirected to Google's authorization endpoint:

   GET https://accounts.google.com/o/oauth2/v2/auth?
       client_id=printmypics-12345
       &redirect_uri=https://printmypics.com/oauth/callback
       &response_type=code
       &scope=photos.readonly
       &state=xyz789random

Step 2 — Google shows the user a consent screen:
   "PrintMyPics wants to: View your Google Photos. Allow?"
   The user is authenticating to GOOGLE here, never to PrintMyPics.

Step 3 — User clicks "Allow". Google redirects back to PrintMyPics:

   GET https://printmypics.com/oauth/callback?
       code=AUTH_CODE_ABC123
       &state=xyz789random

   --> The "state" parameter is checked here to confirm it matches what
       was sent in Step 1 -- this defends against CSRF attacks where an
       attacker tricks a victim into completing an OAuth flow the
       attacker initiated.

Step 4 — PrintMyPics' BACKEND (not the browser) exchanges the short-lived
   code for an access token, authenticating itself with its own client
   secret:

   POST https://oauth2.googleapis.com/token
   Content-Type: application/x-www-form-urlencoded

   client_id=printmypics-12345
   &client_secret=SUPER_SECRET_VALUE   # never exposed to the browser
   &code=AUTH_CODE_ABC123
   &grant_type=authorization_code
   &redirect_uri=https://printmypics.com/oauth/callback

Step 5 — Google's Authorization Server responds:

   {
     "access_token": "ya29.a0AfH6...",
     "expires_in": 3600,
     "refresh_token": "1//0gG8...",
     "scope": "photos.readonly",
     "token_type": "Bearer"
   }

Step 6 — PrintMyPics now calls the Resource Server directly, using the
   access token as a Bearer credential:

   GET https://photoslibrary.googleapis.com/v1/mediaItems
   Authorization: Bearer ya29.a0AfH6...

   --> Google's Photos API checks the token's validity and scope, and
       returns only what "photos.readonly" permits. It never sees the
       user's actual Google password at any point in this flow.
```
--> Why the code is exchanged in two steps instead of returning the access token directly: the intermediate "code" travels through the browser's URL bar (visible in browser history, referrer headers, proxy logs), so it is treated as low-value and short-lived. The actual high-value access token is exchanged over a direct server-to-server call, authenticated with a client_secret the browser never sees. This two-step design is the core reason the Authorization Code flow is considered secure for server-side apps.
--> Access tokens are short-lived (often 1 hour) by design — if one leaks, the exposure window is small. The refresh_token is long-lived and used by the backend to silently get new access tokens without bothering the user to log in again.
--> `scope` is critical for least privilege — an app should request the narrowest scope possible (`photos.readonly`, not full account access). Users should be suspicious of any third-party app OAuth consent screen requesting broad, unrelated permissions.

==> Other OAuth Flows (brief)
--> Implicit Flow — an older, now-discouraged flow that returned the access token directly in the browser URL fragment with no code exchange step; deprecated because tokens end up exposed in browser history/logs. Modern guidance (OAuth 2.1) removes it entirely in favor of Authorization Code + PKCE.
--> PKCE (Proof Key for Code Exchange) — an extension added on top of the Authorization Code flow for public clients that can't safely hold a client_secret (mobile apps, single-page apps). The client generates a random "code_verifier," sends its hash ("code_challenge") in Step 1, and must present the original verifier in Step 4 — this proves the app exchanging the code is the SAME app that started the flow, defending against code-interception attacks.
--> Client Credentials Flow — used for machine-to-machine (M2M) auth with no human user involved at all (e.g., a backend service authenticating to another backend service using only its own client_id/client_secret).

## OpenID Connect (OIDC) — Authentication Built on Top of OAuth

--> OIDC is a thin identity layer built ON TOP of OAuth 2.0 that adds the missing piece: actual user AUTHENTICATION.
--> The key technical difference: alongside the OAuth `access_token`, OIDC also issues an `id_token` — a signed JSON Web Token (JWT) that specifically asserts "this user's identity was verified," containing claims like `sub` (subject/user ID), `email`, `name`, and `iss` (issuer).
--> In practice: `access_token` = "here's a key to go fetch data from an API." `id_token` = "here is cryptographic proof of who just logged in." "Sign in with Google" buttons on websites are technically OIDC, not raw OAuth — the site needs to know WHO you are, not just get permission to call an API on your behalf.
--> Decoded example id_token payload:
```json
{
  "iss": "https://accounts.google.com",
  "sub": "110169484474386276334",
  "email": "vanisha@example.com",
  "email_verified": true,
  "aud": "printmypics-12345",
  "exp": 1893456000,
  "iat": 1893452400
}
```
--> Rule of thumb for interviews: OAuth = authorization ("can this app act on my behalf"), OIDC = authentication ("who is this user"), and OIDC is literally implemented using OAuth's flows under the hood.

## Multi-Factor Authentication (MFA) Methods — Deep Dive

--> MFA requires two or more independent proof categories: something you know (password), something you have (phone, hardware key), something you are (fingerprint/biometric). Requiring two factors from the SAME category (two passwords) is not real MFA.

1. TOTP (Time-based One-Time Password)
   --> An app (Google Authenticator, Authy, a password manager) and the server both share a secret key established at enrollment (usually via a QR code). Both sides independently compute a 6-digit code from that shared secret combined with the current 30-second time window, using the HMAC-based OTP algorithm.
   --> Because both sides compute the code independently from a shared secret + synchronized clock, no network round-trip is required to generate a code — this is why it works even with the phone in airplane mode.
   --> Weakness: still phishable — a fake login page can prompt the user for their TOTP code in real time and relay it to the real site within the 30-second validity window (a real-time "adversary-in-the-middle" phishing kit, e.g., Evilginx-style attacks).

2. Push Notifications
   --> The login attempt sends a push notification to a registered mobile app ("Approve this login? Yes/No"), often showing context like location/device.
   --> Convenient, but vulnerable to "MFA fatigue" / "MFA bombing" attacks — an attacker who already has the password spams dozens of push approval requests until an annoyed or confused user finally taps "Approve" just to make the notifications stop. This exact technique was used in real high-profile breaches (e.g., the 2022 Uber breach).
   --> Mitigation: "number matching" push MFA, where the user must type a specific number shown on the login screen into the push prompt, which prevents mindless tap-approval.

3. Hardware Tokens / FIDO2 / WebAuthn
   --> A physical device (YubiKey, Titan Security Key) or platform authenticator (Windows Hello, Touch ID/Face ID via a device's secure enclave) that performs public-key cryptographic challenge-response authentication.
   --> How it works at a high level: at registration, the device generates a unique public/private key pair per site. The private key never leaves the device/secure hardware. The site stores only the public key. At login, the site sends a random challenge; the device signs it with the private key; the site verifies the signature with the stored public key.
   --> This is the only mainstream MFA method that is cryptographically phishing-resistant — a fake lookalike site literally cannot obtain a valid signature because FIDO2/WebAuthn binds the credential to the exact origin (domain) it was registered for. Even if a user is fooled into visiting a phishing site, the browser/authenticator itself refuses to complete the handshake with the wrong origin.
   --> This is why FIDO2/WebAuthn (the standards behind "passkeys") is the current gold standard being pushed industry-wide, including by Google, Microsoft, and Apple.

4. SMS OTP — and why it's the weakest option
   --> A one-time code sent via text message to the user's registered phone number.
   --> Weakness 1 — SIM swapping: an attacker socially engineers (or bribes an insider at) a mobile carrier into porting the victim's phone number to an attacker-controlled SIM, after which all SMS codes go straight to the attacker.
   --> Weakness 2 — SS7 protocol attacks: the SS7 signaling protocol used by telecom networks has known weaknesses that let a sufficiently resourced attacker intercept SMS messages in transit at the network level.
   --> Weakness 3 — no cryptographic binding to a specific login session/origin, unlike FIDO2 — a phished code can simply be relayed by the attacker to the real site in real time.
   --> Weakness 4 — SMS itself is not encrypted end-to-end and can, in some cases, be viewed by anyone with physical access to a locked screen showing a notification preview.
   --> NIST SP 800-63B has, since 2016, specifically discouraged SMS as an authenticator for anything security-sensitive, precisely because of these known weaknesses — yet it remains extremely widespread because it requires zero app installation and works on any phone.

## Just-In-Time (JIT) Access and Privileged Access Management (PAM)

--> Standing privileged access — a user who has admin rights 24/7/365 whether they're using them or not — is a massive standing attack surface. If that account's credentials are ever compromised, the attacker inherits full-time admin rights immediately.
--> Just-In-Time (JIT) Access flips this: users have NO standing privileged access by default. When they actually need elevated access (e.g., to patch a production database at 2 AM), they submit a request, it's approved (manually or via automated policy), and privileged access is granted for a narrow time window (e.g., 1 hour) before it automatically expires and reverts to a normal, unprivileged account.
--> Privileged Access Management (PAM) is the broader discipline/toolset (CyberArk, HashiCorp Vault, BeyondTrust) that implements this: it vaults/rotates privileged credentials, brokers JIT elevation requests, and records/audits every privileged session (often with full session recording/keystroke logging for the highest-risk accounts like domain admins).
--> Core PAM capabilities:
--> Credential vaulting — privileged passwords/keys are stored in an encrypted vault, never known directly by the human user; the PAM tool injects them at time of use.
--> Automatic rotation — privileged credentials are automatically rotated after each use (or on a schedule), so even a leaked credential has a very short useful life.
--> Session recording — every privileged session is logged/recorded for audit and forensic purposes.
--> Approval workflows — elevation requests can require manager/security-team sign-off before being granted, adding a human check on the riskiest actions.
--> Why this matters for the overall theory set: this is least privilege (Chapter 1/3's core principle) applied specifically and rigorously to the accounts that would cause the most damage if compromised — domain admins, cloud root accounts, database superusers.

## Tying It Together

--> Authentication proves identity; authorization decides what that identity can do — almost every access-control vulnerability traces back to conflating the two.
--> SSO centralizes authentication across many apps via an IdP, using SAML (older, XML/enterprise) or OIDC (modern, JSON/web-native) as the underlying protocol.
--> OAuth 2.0 is authorization delegation ("let this app act on my behalf"), and OIDC layers real authentication on top of it via the signed id_token.
--> MFA strength runs on a real spectrum: SMS OTP (weakest, phishable, SIM-swappable) < push notifications (better, but MFA-fatigue-able) < TOTP (good, still phishable in real time) < FIDO2/WebAuthn hardware/passkeys (best, cryptographically phishing-resistant).
--> JIT access and PAM apply least privilege specifically to the highest-value privileged accounts, converting "always-on admin rights" into "admin rights only for the exact minute they're needed, fully audited."
