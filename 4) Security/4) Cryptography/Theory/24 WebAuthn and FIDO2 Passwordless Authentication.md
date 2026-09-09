### WebAuthn and FIDO2 Passwordless Authentication

--> Passwords fail in three predictable ways: users reuse them across sites (one breach compromises many accounts), they can be phished by a lookalike site that just asks for them, and SMS/OTP "second factors" can be intercepted or relayed by a real-time phishing proxy sitting between the user and the real site.
--> WebAuthn/FIDO2's core idea: replace "prove you know a shared secret" with "prove you hold a private key, bound to a specific origin, that never leaves a hardware or OS-backed authenticator." Note 04 covers why storing password *hashes* well still doesn't stop phishing or reuse — WebAuthn is a different family of fix, removing the shared secret from the picture entirely.
--> The private key is generated and stored inside the authenticator — a USB/NFC/Bluetooth security key, or a platform authenticator like Touch ID, Windows Hello, or a phone's biometric unlock — typically backed by the same secure-enclave/TEE hardware isolation covered in note 22. The key is designed to be non-extractable: even the OS and the browser never see it in plaintext.

## Actors

--> 1. **Relying Party (RP)** — the website/service (identified by its origin, e.g. `https://bank.com`) that wants to authenticate the user.
--> 2. **Authenticator** — the hardware/platform component that generates and holds private keys and performs signing. Never talks to the network directly.
--> 3. **Client** — the browser (or OS), which mediates between the RP's JavaScript and the authenticator via the WebAuthn API, and critically, is the party that tells the authenticator what the ACTUAL origin of the page is (the authenticator trusts the client on this, which is why browser/OS integrity matters).

## Registration Ceremony

--> The RP's server sends a random challenge to the browser. JavaScript calls `navigator.credentials.create()` with that challenge plus RP identity (origin) and desired parameters. The browser passes this to the authenticator, prompting the user (touch the key, scan a fingerprint, etc.).
--> The authenticator generates a **new keypair scoped to that RP's origin** (a different keypair per site — this is what makes cross-site correlation and phishing structurally hard), signs an attestation over the challenge and origin, and returns the public key plus a **credential ID** (an opaque handle used later to ask for that specific credential again).
--> The server stores the public key and credential ID against the user's account — no secret is stored, symmetrically to how note 04 recommends never storing a raw password: here there's no password-equivalent to store AT ALL, just a public key.

```js
// Simplified registration (browser-side) — real code has more options
// (timeout, authenticatorSelection, excludeCredentials, etc.)
const options = await fetch('/webauthn/register/challenge').then(r => r.json());

const credential = await navigator.credentials.create({
  publicKey: {
    challenge: base64ToBuffer(options.challenge),   // random, server-generated, single-use
    rp: { name: "Bank Example", id: "bank.com" },    // origin the key gets bound to
    user: {
      id: base64ToBuffer(options.userId),
      name: "vanisha@warpx.ai",
      displayName: "Vanisha",
    },
    pubKeyCredParams: [{ alg: -7, type: "public-key" }],  // -7 = ES256 (ECDSA P-256, note 08)
    authenticatorSelection: { userVerification: "required" },
  },
});

// Send the new public key + credential ID to the server to store
await fetch('/webauthn/register/verify', {
  method: 'POST',
  body: JSON.stringify({
    id: credential.id,
    rawId: bufferToBase64(credential.rawId),
    response: {
      attestationObject: bufferToBase64(credential.response.attestationObject),
      clientDataJSON: bufferToBase64(credential.response.clientDataJSON),
    },
  }),
});
```

## Authentication Ceremony

--> The server sends a fresh random challenge. `navigator.credentials.get()` asks the authenticator to sign it — the authenticator locates the matching credential for the CURRENT page's origin, has the user re-verify presence (touch/biometric), and produces a signature over the challenge plus a `clientDataJSON` blob that includes the origin the browser observed.
--> The server verifies the signature against the stored public key for that credential ID, checks the challenge matches what it issued, and checks the origin embedded in `clientDataJSON` matches the expected RP origin — this last check is what makes phishing structurally fail, covered next.

```js
// Simplified authentication (browser-side)
const options = await fetch('/webauthn/login/challenge').then(r => r.json());

const assertion = await navigator.credentials.get({
  publicKey: {
    challenge: base64ToBuffer(options.challenge),
    allowCredentials: [{ id: base64ToBuffer(options.credentialId), type: "public-key" }],
    userVerification: "required",
  },
});

// Server verifies: signature over (clientDataJSON || authenticatorData)
// using the STORED public key, then checks clientDataJSON.origin ===
// "https://bank.com" and clientDataJSON.challenge === the one it issued.
await fetch('/webauthn/login/verify', {
  method: 'POST',
  body: JSON.stringify({
    id: assertion.id,
    response: {
      authenticatorData: bufferToBase64(assertion.response.authenticatorData),
      clientDataJSON: bufferToBase64(assertion.response.clientDataJSON),
      signature: bufferToBase64(assertion.response.signature),
    },
  }),
});
```

## Why Origin Binding Defeats Phishing

--> A phishing page at `bank-secure-login.com` can show the user a convincing fake login form and even relay whatever the user types straight to the real bank — this works fine against passwords, and it ALSO works against SMS OTPs, because a real-time phishing proxy (e.g. an "evilginx"-style reverse proxy) just forwards the OTP the victim receives to the real site within its short validity window.
--> WebAuthn breaks this specific relay attack structurally: the browser itself — not the phishing site, not the user — tells the authenticator what origin the page is actually running on. The authenticator will only produce a valid signature for the credential matching that ACTUAL origin. A phishing page cannot get a signature "for bank.com" no matter how convincing it looks, because it does not control what origin string the browser reports.
--> There is no "type the code into the fake page" step to intercept, because the whole point of the ceremony is that the cryptographic proof is generated and origin-scoped entirely inside the browser/authenticator boundary — the phishing site never even receives something worth relaying.

## Attestation

--> During registration, the authenticator can optionally provide **attestation** — a signed statement, verifiable via a manufacturer certificate chain, about what kind of authenticator produced the key (make/model, certification level). This lets an RP with strict requirements (e.g. a bank wanting FIPS-certified hardware keys only) verify device provenance before accepting a credential.
--> Most consumer-facing services set attestation to "none" or ignore it, both for privacy (attestation can fingerprint hardware) and because for most threat models simply having ANY WebAuthn-compliant authenticator is enough — verifying the exact make/model is an enterprise/high-assurance concern, not a baseline requirement.

## Discoverable Credentials and Passkeys

--> A **discoverable credential** (the technical basis for the "passkey" branding) stores enough information on the authenticator itself that the user doesn't need to type a username first — the browser can prompt "sign in with a passkey" and let the authenticator surface which account it has a credential for.
--> Passkeys are commonly **synced** across a user's devices via the platform account (iCloud Keychain, Google Password Manager, Windows/Microsoft account) rather than pinned to one physical device. This is what makes "passwordless" practical at consumer scale — losing your phone doesn't lock you out — but it reintroduces a **key custody tradeoff** that pure hardware-key FIDO2 deliberately avoided: the private key's non-extractability now depends on the platform vendor's sync/encryption design (typically end-to-end encrypted key material, but still a bigger trust surface than "the key physically cannot leave this one dongle").
--> Hardware security keys (single-device, non-syncing) remain the higher-assurance option precisely because there is no cloud-sync attack surface at all — a tradeoff enterprises and high-value targets often still choose deliberately over the convenience of synced passkeys.

## Threat Comparison

| Threat | Password | SMS/OTP | WebAuthn/Passkey |
|---|---|---|---|
| Phishing (credential entered on fake site) | Fully vulnerable | Vulnerable — proxy relays code in real time | Structurally immune — signature is origin-bound |
| Replay of captured secret | Vulnerable until changed | Single-use code, but proxy relay happens before it expires | Signature is challenge-bound and single-use by construction |
| Server-side database breach | Stolen hashes crackable (note 04) | Phone number leak, not the OTP itself | Only public keys stored — nothing useful to an attacker |
| Device loss | N/A | SIM-swap risk | Hardware key: locked out unless backup registered; synced passkey: recoverable via platform account |

## Pitfalls

--> 1. **Treating attestation as identity verification** — attestation proves device TYPE, not that the legitimate account owner is the one using it; it is not a substitute for proper account recovery flows.
--> 2. **No backup credential registered** — losing your only hardware authenticator with no second key or passkey enrolled is a full lockout; RPs should require at least two registered authenticators.
--> 3. **Downgrading to a weaker fallback method** — an RP that lets a user "fall back to SMS" if WebAuthn fails reintroduces exactly the phishing surface WebAuthn was meant to close; fallback paths need their own hardening, not silent equivalence.
--> 4. **Confusing "passwordless" with "phishing-proof" for synced passkeys** — synced passkeys are phishing-resistant via origin binding, but the sync mechanism shifts some trust onto the platform account's own security (see note 22's discussion of hardware-backed key isolation for why single-device hardware keys avoid this tradeoff entirely).
