### Threshold Signatures and Threshold ECDSA

--> A single signing key is a single point of compromise — steal it once, sign anything forever. Threshold signatures split a private key across `N` parties such that any `T` of them can jointly produce a valid signature, but any group SMALLER than `T` learns nothing usable, not even a partial key that helps them guess.
--> The critical, easy-to-miss distinction from plain Shamir secret sharing of a static key (the MPC territory covered in note 16): with simple secret sharing, reconstructing a signature by combining shares typically means reconstructing the FULL private key at some point, even if only briefly in one party's memory. **True threshold signing never reconstructs the full private key anywhere, at any point — not during setup, not during signing.** Each party only ever computes and exchanges partial values that are individually useless; the valid signature emerges from the protocol's math without the key ever existing in one place.
--> This "never assembled, not even momentarily" property is what makes threshold signing strictly harder to build than threshold secret sharing, and also strictly more valuable operationally — there is no instant in the protocol's execution where compromising one machine's memory gets you the whole key.

## Why Threshold-Schnorr/EdDSA Is Comparatively Easy

--> Schnorr signatures (and EdDSA, note 08's deterministic-nonce cousin) have a signing equation that is **linear/additive** in the private key and the nonce: roughly, `s = k + c*d` (challenge `c`, nonce `k`, private key `d`, mod the group order).
--> Because addition and multiplication-by-a-public-scalar distribute cleanly across secret shares, each party can compute their own partial `s_i` using only their private key SHARE and a share of the nonce, and the final signature is just the SUM of the partial `s_i` values: `s = s_1 + s_2 + ... `. No party ever needs to see anyone else's share of `d` or `k` to make this work — the linearity does all the work.
--> This is why threshold-Schnorr protocols (e.g. FROST) are relatively simple and were practical years before usable threshold-ECDSA existed.

## Why Threshold-ECDSA Was Historically Much Harder

--> ECDSA's signing equation is `s = k⁻¹ (H(m) + r*d) mod n`, where `k⁻¹` is the **modular inverse** of the random nonce `k` (note 08 covers this nonce and why reusing it is catastrophic for plain single-key ECDSA).
--> That inverse `k⁻¹` is the problem: **modular inversion is not a linear operation**. You cannot compute the inverse of a sum-of-shares by summing the inverses of the individual shares — `(k_1 + k_2)⁻¹ != k_1⁻¹ + k_2⁻¹`. Schnorr's clean "just add the partial signatures" trick simply does not exist for ECDSA's equation.
--> This meant that, for a long time, doing ECDSA in a threshold way required actually secure MULTI-PARTY COMPUTATION of a genuinely nonlinear function jointly, rather than a few local additions each party can do independently — a much harder cryptographic engineering problem, and the reason threshold-ECDSA research lagged years behind threshold-Schnorr despite ECDSA being the far more widely deployed signature scheme (Bitcoin, Ethereum's legacy accounts, TLS certificates).

## How Modern Protocols Solved It (GG18/GG20-style)

--> The GG18/GG20 family of protocols (named for their authors, Gennaro-Goldfeder) made practical threshold-ECDSA work by combining two tools already covered elsewhere in this track:
--> 1. **Paillier encryption** — an additively homomorphic encryption scheme: given `Enc(a)` and `Enc(b)`, anyone can compute `Enc(a+b)` WITHOUT decrypting either value. This lets parties compute on encrypted shares of the nonce and key without any single party ever seeing another's plaintext share — exactly the kind of "compute without revealing inputs" guarantee note 16's MPC chapter builds up to, applied specifically to the arithmetic ECDSA's inversion step needs.
--> 2. **Zero-knowledge proofs** (note 17) — at each step, a party proves properties of its encrypted contributions (e.g. "this ciphertext really does encrypt a value consistent with my earlier commitment") WITHOUT revealing the underlying value, so malicious/deviating parties can be caught rather than silently corrupting the joint computation.
--> Together, these let `T` parties jointly walk through the arithmetic that produces a valid ECDSA `(r, s)` pair, never assembling the private key `d` or even the full nonce `k` at any single point, while still being able to detect (and in newer **identifiable-abort** variants, pin the blame on) a misbehaving participant instead of just failing silently.
--> Newer protocol generations trade some of GG18/GG20's heavier Paillier-based machinery for lighter-weight approaches, but the same two-ingredient shape — homomorphic-style arithmetic on shares plus ZK proofs of correct behavior — is the recurring pattern across the family.

## 2-of-3 Threshold Signing Round (Conceptual)

```
Parties: A, B, C  (any 2 of 3 can sign; no 1 party ever holds the full key)
Signing request: "sign transaction TX" — parties A and C are chosen to sign

  A                          C                        (B stays offline)
  |-- commit to nonce share ------> |
  |<------- commit to nonce share --|
  |-- ZK proof: commitment well-formed -->|
  |<---------- ZK proof: commitment well-formed --|
  |-- Paillier-encrypted partial ---->|   MtA (multiplicative-to-additive)
  |    computation exchange           |   conversion sub-protocol handles
  |<--- Paillier-encrypted partial ---|   the k⁻¹ nonlinearity without
  |    computation exchange           |   ever decrypting anyone's share
  |-- partial signature s_A -------->|
  |<-------------- partial signature s_C --|
  |                                    |
  |   Both combine s_A, s_C into final (r, s) --
  |   a single valid ECDSA signature, verifiable
  |   against the ONE public key, as if signed
  |   by a normal single-key holder.
  |   Private key d: never existed in one place.
```

--> Note what never appears anywhere in that exchange: the full private key `d`, and the full nonce `k`. Only commitments, ZK proofs, and Paillier-encrypted partial values cross the wire — this is the entire point.

## Real-World Deployment

--> **Multi-party custody for cryptocurrency wallets/exchanges** — threshold-ECDSA lets a custodian split signing authority across geographically/organizationally separate parties (e.g. different data centers, or a customer + an exchange + an insurance/recovery party) so that no single compromised machine, insider, or data center breach can move funds alone. See note 26 for the broader wallet key-management landscape this sits inside (hot/cold separation, HSMs, seed backup strategies).
--> **MPC-based signing services as an alternative to on-chain multisig** — instead of deploying a smart-contract multisig (which is visible on-chain, adds transaction size/gas cost, and reveals the M-of-N policy publicly), threshold-ECDSA produces a signature that looks, on-chain, exactly like an ordinary single-key signature. The "multiple parties had to cooperate" fact lives entirely off-chain in the signing protocol, not in the blockchain's data structure.
--> This off-chain-invisible property is a deliberate tradeoff: it saves on-chain footprint and hides the custody policy from public view, at the cost of losing the auditability an on-chain multisig gives for free (anyone can inspect a multisig contract's policy; nobody can inspect a threshold-MPC signing policy from the signature alone).

## Comparison: Single-Key HSM vs On-Chain Multisig vs Threshold-MPC

| Property | Single-key HSM | On-chain multisig | Threshold-MPC signing |
|---|---|---|---|
| Single point of compromise | Yes — one HSM, one key | No — requires compromising M of N signers | No — requires compromising T of N parties |
| On-chain footprint / cost | Minimal (looks like normal tx) | Larger tx size, visible policy, extra gas | Minimal (looks like normal tx) |
| Policy auditability from chain data alone | N/A (single signer, no policy) | Fully visible — anyone can inspect the contract | Not visible — policy lives off-chain in the protocol |
| Signing latency | Fast (local operation) | Slower (on-chain coordination, block confirmation for setup changes) | Slower than single-key (multi-round network protocol), faster than on-chain governance changes |
| Cross-chain/asset portability | Depends on HSM integration per chain | Requires multisig contract support per chain | Works anywhere plain ECDSA signatures are accepted — chain-agnostic |
| Recovery if a party is lost | Full loss if HSM/key destroyed (unless backed up) | Reconfigure contract with remaining signers | Re-share/re-key protocol among remaining parties (if T still reachable) |

## Pitfalls

--> 1. **Confusing threshold signing with simple secret sharing** — if any step in a "threshold" scheme ever reconstructs the full private key in one place (even transiently, even in a HSM enclave used only for that instant), it has quietly given up the property that makes threshold signing worth the complexity in the first place.
--> 2. **Assuming threshold protocols are free of nonce-reuse-class risk** — the MtA (multiplicative-to-additive) sub-protocols handling ECDSA's `k⁻¹` nonlinearity have their own history of subtle implementation bugs (parameter range checks, missing ZK proof verification) that leaked key material despite the key never being "reconstructed" in the naive sense — protocol correctness here is genuinely harder to get right than it looks.
--> 3. **Treating "identifiable abort" as prevention** — newer protocol variants can tell you WHICH party misbehaved after the fact, which is valuable for accountability, but does not by itself stop a malicious party from disrupting a signing round; it converts silent failure into attributable failure, not into guaranteed success.
--> 4. **Off-chain policy invisibility as a governance risk** — because a threshold-MPC signature is indistinguishable on-chain from a single-key signature, organizations relying on it need strong OFF-chain controls and auditing (who the T-of-N parties actually are, how shares are provisioned/rotated) since the blockchain itself will never surface a misconfigured or compromised threshold policy the way an on-chain multisig contract would.
