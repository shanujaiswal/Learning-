### TLS, Diffie-Hellman, and Key Exchange

--> Symmetric encryption (AES) is fast but needs both sides to already share a secret key. Asymmetric encryption (RSA) solves key sharing but is too slow for bulk data. This file covers exactly how real systems like HTTPS bridge that gap — and it starts with a problem that seems almost paradoxical.

## The Key Exchange Problem

--> Imagine you (a browser) want to talk securely with a bank's server for the very first time. You've never met before, there is no shared secret, and the entire conversation happens over a network that attackers can passively read AND actively tamper with (a "man in the middle").
--> You cannot just symmetric-encrypt your first message, because that requires a key the other side must already have — and you have no way to safely SEND them that key over the same insecure channel, since anyone eavesdropping would just read the key too.
--> This is not a "we haven't found a clever enough trick yet" problem — it's a structural chicken-and-egg problem: to establish a secure channel you seem to need a secure channel first.
--> Diffie-Hellman (1976) was the breakthrough that solved this: two parties can PUBLICLY exchange some values, in full view of an eavesdropper, and still each independently compute the SAME shared secret at the end — while an eavesdropper who saw every exchanged value cannot feasibly compute that same secret.

## Diffie-Hellman Key Exchange — Worked Example with Small Numbers

--> The trick relies on modular exponentiation being easy to compute forward, but hard to reverse (the "discrete logarithm problem") — similar in spirit to RSA's factoring difficulty, but a different underlying hard math problem.
--> Both parties publicly agree on two numbers: a prime `p` and a base/generator `g`. These do NOT need to be secret.

--> Step by step, using small numbers only for illustration (real DH uses numbers hundreds of digits long):

```python
# Publicly agreed values (anyone, including an eavesdropper, can see these)
p = 23   # a prime modulus
g = 5    # a generator

# Alice picks a PRIVATE random number, never sent to anyone
alice_private = 6

# Bob picks his own PRIVATE random number, never sent to anyone
bob_private = 15

# Each computes a PUBLIC value and sends it openly over the network
alice_public = pow(g, alice_private, p)   # g^alice_private mod p
bob_public = pow(g, bob_private, p)       # g^bob_private mod p

print("Alice sends publicly:", alice_public)   # 5^6 mod 23  = 8
print("Bob sends publicly:  ", bob_public)     # 5^15 mod 23 = 19

# Alice combines Bob's PUBLIC value with her OWN PRIVATE number
alice_shared_secret = pow(bob_public, alice_private, p)    # 19^6 mod 23

# Bob combines Alice's PUBLIC value with his OWN PRIVATE number
bob_shared_secret = pow(alice_public, bob_private, p)      # 8^15 mod 23

print("Alice computed secret:", alice_shared_secret)   # 2
print("Bob computed secret:  ", bob_shared_secret)     # 2

print(alice_shared_secret == bob_shared_secret)   # True - both landed on the SAME secret, 2
# an eavesdropper saw p=23, g=5, alice_public=8, bob_public=19 - all public -
# but cannot feasibly recover alice_private=6 or bob_private=15 from those (the discrete log problem),
# and therefore cannot compute the shared secret either
```

--> This shared secret (in real systems, a large random-looking number) is then fed into a Key Derivation Function to produce the actual AES session key both sides will use for the rest of the conversation.
--> Modern TLS almost always uses Elliptic Curve Diffie-Hellman (ECDHE) instead of classic modular-exponentiation DH — same core idea, but built on elliptic curve math, giving equivalent security with much smaller numbers/keys and faster computation. The "E" in ECDHE stands for "Ephemeral" — a brand new key pair is generated for every single connection, which is what gives TLS 1.3 forward secrecy (a compromise of the server's long-term private key later can't be used to decrypt past recorded sessions).

## How TLS/HTTPS Combines Asymmetric and Symmetric Encryption

--> HTTPS never uses RSA/asymmetric crypto to encrypt the actual webpage data — it's far too slow for large amounts of traffic. Instead, TLS uses a "hybrid" approach:

1. Handshake phase (asymmetric) – The client and server use asymmetric techniques (certificate verification via RSA/ECDSA signatures, key agreement via ECDHE) to authenticate the server and agree on a shared symmetric session key.
2. Bulk data phase (symmetric) – Once that session key exists, ALL actual application data (the HTML, images, API responses) is encrypted using fast symmetric encryption — almost always AES-GCM or ChaCha20-Poly1305 (see file 03).

--> This is exactly the "hybrid encryption" pattern also mentioned in file 05 for RSA: use slow asymmetric crypto only for the small, one-time job of agreeing on a key; use fast symmetric crypto for everything after that.

## Certificates and Certificate Authorities (CAs)

--> Diffie-Hellman alone solves EAVESDROPPING, but not IMPERSONATION — nothing so far stops a man-in-the-middle from running their OWN Diffie-Hellman exchange with you while pretending to be the bank, then relaying/tampering with everything to the real bank. You'd compute a "shared secret" alright, just with the wrong party.
--> A TLS certificate solves this by binding a public key to an identity (a domain name like `bank.com`), and that binding is itself digitally signed (see file 05) by a Certificate Authority (CA) — a trusted third party like DigiCert or Let's Encrypt.
--> Your browser ships with a built-in list of trusted CA public keys (the "root store"). When the bank's server presents its certificate during the handshake, your browser verifies the CA's SIGNATURE on that certificate using the CA's already-trusted public key. If the signature checks out, the browser now trusts that the public key inside the certificate genuinely belongs to `bank.com`.
--> This creates a "chain of trust": Root CA signs an Intermediate CA's certificate, the Intermediate CA signs the bank's actual server certificate. As long as you trust the root, and each signature in the chain verifies correctly, you transitively trust the leaf certificate.
--> Without this step, ECDHE key exchange would still work perfectly — you just could never be sure WHO you securely agreed a key with, which is precisely the gap CAs and certificates close.

## TLS 1.2 vs TLS 1.3 Handshakes — Conceptual Difference

--> TLS 1.2 handshake (simplified): client and server exchange multiple round trips negotiating a large menu of cipher suite options, then perform the key exchange (which could even be plain RSA key transport in older configs, offering no forward secrecy), THEN start encrypting application data. Typically 2 full round trips before any data flows.
1. Client Hello (proposes cipher suites) --> Server Hello + certificate --> key exchange messages --> Finished --> (now) encrypted application data.

--> TLS 1.3 (2018) simplified and hardened this significantly:
1. Removed all the legacy weak/insecure cipher suite options (no more RSA key transport, no more CBC-mode-with-separate-MAC, no more weak hash functions) — the negotiation menu is drastically smaller and every remaining option is modern and safe.
2. Made ECDHE (with forward secrecy) mandatory for essentially all connections — plain static RSA key exchange is gone.
3. Cut the handshake down to effectively ONE round trip: the client guesses the server's preferred key-exchange parameters and sends its ECDHE public value in the very first message alongside the Client Hello, letting encrypted application data flow almost immediately ("1-RTT handshake"), with an even faster optional "0-RTT" resumption mode for returning clients.

--> Net effect: TLS 1.3 connections establish noticeably faster (fewer round trips = lower latency, which matters a lot on mobile networks) AND are more secure by construction, because insecure legacy options were deleted from the protocol entirely rather than merely discouraged.
