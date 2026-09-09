### Blind, Ring, and Group Signatures

--> Ordinary digital signatures (see note 05) prove "this exact private key holder signed this exact message" — full accountability, zero privacy. A whole family of signature variants exists specifically to weaken that link on purpose: hide the message from the signer, hide the signer's identity from the verifier, or hide it conditionally with an escape hatch. These three primitives — blind, ring, and group signatures — are the classic answers to "prove something was authorized without proving who or what."
--> They solve different privacy problems and are not interchangeable: blind signatures hide the MESSAGE from the SIGNER at signing time; ring and group signatures hide the SIGNER's identity from the VERIFIER at verification time.

## Blind Signatures

--> A blind signature lets a signer produce a valid signature on a message WITHOUT ever seeing its content. The requester "blinds" the message before sending it to the signer, the signer signs the blinded value, and the requester "unblinds" the result — producing a signature that verifies correctly against the ORIGINAL, unblinded message.
--> Classic motivating use case — **Chaum's digital cash (1982)**: a bank needs to sign a "coin" so it's provably issued by the bank, but if the bank sees the coin's serial number at withdrawal time, it can later link that serial number back to the specific customer who withdrew it when the coin is spent, destroying the anonymity of cash. Blinding breaks that link: the bank signs a blinded coin, debits the customer's account, and has mathematically no way to correlate the blinded value it signed with the unblinded coin that shows up at a merchant later.
--> Modern relevance: **Privacy Pass** style tokens (used to let a browser prove "a human already solved a CAPTCHA" to a different site without that site learning WHICH CAPTCHA-solving session it was) and several anonymous credential schemes use blind signatures or their modern descendants (e.g. blind BBS+, algebraic MACs) for the same core reason — issuance and redemption/use must be unlinkable.

### RSA Blind Signature Construction (Conceptual)

--> Recall from note 05: RSA signing is `s = m^d mod n` (informally: encrypt the hash with the private exponent `d`), and verification checks `s^e mod n == m`.
--> The blinding trick exploits RSA's multiplicative structure: `(m * r^e)^d mod n = m^d * r^(e*d) mod n = m^d * r mod n` (since `r^(e*d) mod n = r`, because `e` and `d` are inverses mod the group order). The random factor `r` passes straight through the signing operation and can be divided back out afterward.

```
Blind signature flow (RSA-based), conceptually:

1. Requester picks a random blinding factor r (coprime to n).
2. Requester computes the blinded message:
       m' = m * r^e mod n
   (r is raised to the PUBLIC exponent e — same e the signer's
   public key uses — so it can later be "undone" via r's inverse)
3. Requester sends m' to the signer. The signer has NO IDEA what m
   actually is — m' looks like random noise without knowing r.
4. Signer signs the blinded value with their private key d:
       s' = (m')^d mod n
5. Requester unblinds:
       s = s' * r^-1 mod n
   This works out to s = m^d mod n — a completely valid, ordinary
   RSA signature on the ORIGINAL message m, that the signer never saw.
6. Anyone (including the signer, later) can verify s against m using
   the signer's normal public key — verification is indistinguishable
   from a signature on a message the signer saw directly.
```

```python
# Illustrative only — real implementations use a proper crypto library
# (e.g. python-blindrsa) with correct padding; this shows the bare math.

def blind_sign_demo(n, e, d, message_int):
    import random, math

    # 1. requester: pick blinding factor r coprime to n
    while True:
        r = random.randrange(2, n - 1)
        if math.gcd(r, n) == 1:
            break

    # 2. requester blinds the message using the signer's PUBLIC exponent
    blinded = (message_int * pow(r, e, n)) % n

    # 3/4. signer signs the blinded value — never sees `message_int`
    blind_sig = pow(blinded, d, n)

    # 5. requester unblinds using r's modular inverse
    r_inv = pow(r, -1, n)
    signature = (blind_sig * r_inv) % n

    # 6. verification is ordinary RSA signature verification
    assert pow(signature, e, n) == message_int % n
    return signature
```

--> Why this matters practically: the signer's participation is auditable ("the bank did sign SOME coin for this withdrawal") without being traceable ("the bank can tell WHICH spent coin came from THIS withdrawal"). That's the whole privacy property in one sentence.
--> Padding matters in real deployments — naive textbook RSA blinding (as sketched above) is also the classic RSA signature-forgery multiplicative trick, so production blind-signature schemes use blinded variants of proper padding (e.g. RSA-PSS-based blind signing per RFC 9474) rather than raw exponentiation.

## Ring Signatures

--> A ring signature lets ANY member of a defined group ("ring") sign a message such that a verifier can confirm "one of these N public keys signed this" — but cannot determine WHICH one.
--> No group manager, no setup ceremony, no registration authority needed: a signer picks an arbitrary set of OTHER people's public keys (they don't even need permission or participation from the other members — just their public keys), mixes their own key into that ring, and produces a signature that verifies against the whole ring.
--> Real-world use case — **Monero's privacy model**: when spending an output, Monero constructs a ring of several plausible "decoy" outputs alongside the real one being spent, and a ring signature proves "one of these outputs was validly spent" without revealing which one. Combined with stealth addresses and confidential (blinded) amounts, this is what gives Monero transaction-level sender ambiguity that plain Bitcoin-style transactions lack (Bitcoin transactions are pseudonymous but fully traceable on-chain, see note 19).
--> Anonymity here is unconditional and irrevocable by design — there is no mechanism, even in principle, for anyone (including the ring's own members) to later determine who actually signed. That is the key contrast with group signatures below.

## Group Signatures

--> A group signature gives the same surface-level property as a ring signature ("someone in this group signed this, you don't know who") but adds a **group manager** role that holds a special key capable of "opening" a signature — revoking the anonymity and identifying the true signer when necessary.
--> Setup is required and heavier: a trusted group manager runs an enrollment process, issuing each member a unique signing credential derived from a group public key. Verifiers only ever see the single, fixed group public key — individual member keys are never exposed — but the manager retains an opening/tracing trapdoor.
--> This makes anonymity CONDITIONAL and REVOCABLE rather than absolute: fine for scenarios where privacy from ordinary verifiers is desired, but full anonymity from all parties (including an authority) is NOT the goal — e.g. an auditor or regulator needs a break-glass path.
--> Motivating use case — **anonymous attestation**: a device wants to prove "I am a genuine, unrevoked member of this hardware family" to a remote verifier without revealing its individual device identity (which would let the verifier build a tracking profile across every attestation it receives). This is conceptually the same problem **Direct Anonymous Attestation (DAA)** solves for TPM-based remote attestation (see note 22 on TEEs/attestation) — DAA is essentially a specialized group-signature scheme where the "group" is every TPM of a given manufacturer/model, and revocation lets a manufacturer blacklist a specific compromised device's credential without deanonymizing every honest device that attested.
--> A similar structure appears in "anonymous corporate attestation" style designs — an employee proves "I am an authorized employee of this company" to an external system without revealing which employee, while the company itself (as group manager) retains the ability to identify the individual if fraud is later discovered.

## Comparison Table

| Property | Blind Signature | Ring Signature | Group Signature |
|---|---|---|---|
| What's hidden | The message content, from the signer | The signer's identity, from the verifier | The signer's identity, from the verifier |
| Anonymity scope | N/A (signer knows their own key; message is what's blinded) | Anonymous among the chosen ring | Anonymous among the enrolled group |
| Revocable / traceable? | No — unblinding is one-way, no one can trace the original request from the blinded value alone | No — anonymity is unconditional and permanent by construction | Yes — group manager holds an opening/tracing key |
| Setup / authority required | Signer just needs a normal keypair; no group setup | None — signer picks any ring of public keys on the fly | Yes — a group manager runs enrollment and issuance |
| Who can add/remove members | N/A | Anyone signing can include any public keys they like | Only the group manager enrolls/revokes members |
| Real-world example | Chaum's digital cash; Privacy Pass anonymous tokens | Monero transaction signing | Direct Anonymous Attestation (TPM), anonymous corporate credentials |

## Pitfalls

--> 1. **Confusing ring signatures with group signatures** — the visible verifier-facing property ("someone in a set signed this") looks identical, but the presence of a manager who CAN deanonymize is the entire distinguishing feature and has major implications for the actual privacy guarantee being offered — always ask "can anyone trace this later, under any circumstances?"
--> 2. **Naive/unpadded RSA blind signing** — as noted above, textbook RSA blinding without proper padding conventions is directly related to known RSA malleability/forgery attacks; use a standardized construction (RFC 9474 blind RSA, or a vetted library) rather than the toy math shown here.
--> 3. **Ring size as an anonymity budget** — a ring signature's anonymity set is only as large as the ring itself; a ring of 2 provides far weaker privacy than a ring of 16, and if the "decoy" keys in the ring are ever shown to be implausible spenders (e.g. provably-unspent outputs in Monero's early history before mandatory ring signatures), the anonymity set effectively shrinks — this is exactly the kind of statistical/structural weakness class covered in note 10.
--> 4. **Assuming blind signatures give unforgeability guarantees beyond "the signer authorized SOME message"** — a malicious requester can still get a blind signature over content the signer would never have knowingly approved (that's the whole point), so blind signatures are appropriate only where "I don't need to know what I'm signing, just that exactly one signature = one authorization" already holds (e.g. one blind coin per one debited withdrawal) — not a general substitute for content-aware signing.
--> Next logical step: these anonymity-preserving signature schemes are frequently built on the same zero-knowledge proof machinery (proving a statement is true without revealing why) covered in note 17 — ring and group signatures are, at their core, specialized non-interactive zero-knowledge proofs of "I know a valid credential for one member of this set."
