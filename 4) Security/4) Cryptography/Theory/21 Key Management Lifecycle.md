### Key Management Lifecycle

--> Generating a strong key (note 12's CSPRNG guarantees) and storing it in an HSM (note 20) is necessary but not sufficient — a key that is never rotated, never revoked, or never properly destroyed accumulates risk the longer it exists. Key management is the discipline of treating a key's entire lifetime, not just its creation, as a security-relevant surface.

## The Full Lifecycle

--> 1. **Generation** — created with a CSPRNG (note 12) or inside an HSM boundary (note 20), never derived from low-entropy or predictable input.
--> 2. **Distribution** — getting the key (or, for asymmetric keys, the public half) to everyone who legitimately needs it, without exposing it to anyone who doesn't — historically the hardest problem in cryptography, which is exactly what Diffie-Hellman (note 06) and PKI (note 09) exist to solve.
--> 3. **Storage** — at rest, a key must be protected at least as strongly as the data it protects — ideally inside an HSM/KMS (note 20) rather than as a file on disk, and never in application source code or version control.
--> 4. **Usage** — the key is invoked for its intended operation (sign, encrypt, decrypt) under access controls that log and constrain who/what can trigger that operation.
--> 5. **Rotation** — the key is retired from new use and replaced with a fresh one on a schedule, before cryptanalytic or operational risk accumulates past an acceptable threshold.
--> 6. **Revocation** — the key is explicitly marked untrustworthy BEFORE its scheduled expiry, typically because of suspected or confirmed compromise (directly analogous to certificate revocation via OCSP/CRL in note 09).
--> 7. **Archival** — a retired key is kept (usually offline, encrypted under a separate key-encryption-key) purely so that data encrypted under it historically can still be decrypted, without the key being usable for any NEW operation.
--> 8. **Destruction** — the key material is irreversibly erased once no legitimate need to decrypt old data or verify old signatures remains.

## NIST SP 800-57 Cryptoperiods

--> NIST SP 800-57 defines a **cryptoperiod** as the span of time a specific key is authorized for use, balancing the cost/disruption of rotating too often against the risk of a key being exposed to cryptanalysis, side-channel leakage, or operational compromise for too long.
--> Signing keys and encryption keys warrant DIFFERENT cryptoperiods because the risk they carry is different in kind:
--> 1. A **signing key's** risk grows with how much can be forged if it's ever recovered — but a signature made while the key was valid typically needs to remain verifiable long after (e.g. a code-signing signature from 2020 should still verify in 2026). This argues for a shorter key-USE period but the VERIFICATION capability (public key + old signatures) staying valid indefinitely.
--> 2. An **encryption key's** risk grows with how much ACCUMULATED ciphertext exists under it — the more data protected by one key, the more valuable that key is to an attacker and the more exposure if it leaks (a single compromised key exposing years of encrypted data is a much larger blast radius than exposing one signature-verification capability).

| Key type | Typical NIST 800-57 originator-usage period | Rationale |
|---|---|---|
| Asymmetric signature private key | 1–3 years | Limits how long a single stolen key can be used to forge NEW signatures; old valid signatures remain verifiable regardless of rotation |
| Symmetric data-encryption key (e.g. AES data key under envelope encryption, note 20) | Per-use to ~1 year, often much shorter for high-volume keys | Caps how much ciphertext accumulates under one key — high-throughput systems rotate far more often than the guideline minimum |
| Symmetric authentication (MAC) key | ~1–2 years | Similar forgery-window logic as signature keys |
| Key-encryption-key / KMS master key (note 20) | Multiple years, sometimes with an "unlimited decrypt, limited encrypt" pattern | Rotating the KEK doesn't require touching data — only re-wrapping data keys — so the cost of rotating it is low even at longer intervals |
| Root CA private key (note 09) | 10–25 years | Extremely high replacement cost (trust-store propagation across the internet) justifies extreme physical protection (note 20's ceremonies) over frequent rotation |

--> These are guideline maximums, not mandates — a system handling highly sensitive data, or one where the key is used at very high volume, should rotate well inside the guideline, not treat it as a target to reach.

## Rotation Without Downtime — Key Versioning

--> The naive approach — decrypt everything with the old key, re-encrypt everything with the new key, in one atomic cutover — is operationally infeasible for any dataset large enough to matter, and creates a dangerous window where old and new ciphertext coexist under ambiguous versioning.
--> The practical pattern is **key versioning**: every key has a version identifier, new ciphertext is tagged with the version of the key that produced it, and multiple key versions remain simultaneously valid for DECRYPTION while only the newest version is used for new ENCRYPTION.

```python
# Simplified key-versioning wrapper around envelope encryption (note 20).
# Rotation here means: start using v2 for new writes, keep v1 available
# for reads, and lazily re-wrap data keys to v2 in the background —
# no synchronous re-encryption of the underlying bulk ciphertext at all.

class VersionedKeyStore:
    def __init__(self):
        self.active_version = "v2"
        self.kek_by_version = {
            "v1": load_kek("v1"),   # still valid for decrypt
            "v2": load_kek("v2"),   # used for all NEW wraps
        }

    def wrap_data_key(self, plaintext_data_key: bytes) -> dict:
        kek = self.kek_by_version[self.active_version]
        return {
            "version": self.active_version,
            "wrapped": kek.wrap(plaintext_data_key),
        }

    def unwrap_data_key(self, blob: dict) -> bytes:
        # Any still-valid version can decrypt — this is what makes
        # rotation non-disruptive: old ciphertext keeps working
        # without a synchronous mass re-encryption pass.
        kek = self.kek_by_version[blob["version"]]
        return kek.unwrap(blob["wrapped"])

    def rewrap_if_stale(self, blob: dict) -> dict:
        # Opportunistic "re-wrap on read" — over time, as data is read
        # in the normal course of business, its data key gets migrated
        # to the current version with zero dedicated migration downtime.
        if blob["version"] == self.active_version:
            return blob
        plaintext_data_key = self.unwrap_data_key(blob)
        return self.wrap_data_key(plaintext_data_key)
```

--> Because only the small wrapped DATA key gets re-wrapped (kilobytes), not the bulk ciphertext (potentially terabytes), re-wrapping on rotation is cheap regardless of how much underlying data exists — this is the same envelope-encryption structure from note 20 doing double duty as a rotation mechanism.
--> Old key versions are retained until every piece of ciphertext ever wrapped under them has either expired (archival, see above) or been confirmed re-wrapped — deleting a version too early bricks any data still referencing it.

## Key Escrow

--> Key escrow means a trusted third party holds a copy of a key (or the means to reconstruct it) so that data can still be recovered if the original key holder loses access, dies, is fired, or is otherwise unavailable.
--> Legitimate enterprise use: full-disk encryption recovery keys (e.g. BitLocker/FileVault recovery keys held by an organization's IT department) so that an employee's lost password doesn't mean permanently lost company data — this is standard, uncontroversial, and usually policy-mandated.
--> The controversial form is **law-enforcement key escrow** — mandating that a government or third party hold a backdoor key to ALL encrypted communications, justified as enabling lawful-intercept access to otherwise-encrypted data.
--> **The Clipper Chip (1993, NSA/NIST)** is the canonical historical case: a US government-backed encryption chip for telephones that included a mandatory back door — every chip's session key was also escrowed (split across two federal agencies, each holding half) so law enforcement, with a warrant, could reconstruct any Clipper-encrypted call's key.
--> It collapsed within a few years: cryptographers (notably Matt Blaze) found protocol flaws that let the escrow mechanism itself be bypassed while still using the chip, and the broader industry/public rejected mandated backdoors — the same debate resurfaces essentially unchanged in every subsequent "exceptional access" proposal (e.g. the 2016 Apple–FBI dispute over unlocking an iPhone), because the fundamental tension is unresolved: any backdoor built for a "trusted" authorized party is a backdoor that can potentially be discovered and used by an unauthorized one, and its mere existence weakens the guarantee for every user, not just the target of a specific warrant.

## Split Knowledge and Dual Control

--> **Split knowledge** — no single person ever possesses the complete key value; it's divided into components such that no subset below a defined threshold reveals anything about the whole (the same Shamir's Secret Sharing structure introduced for MPC in note 16, applied here to a literal key rather than a computation input).
--> **Dual control** — no single person can complete a sensitive operation alone, even if that person happens to hold the full key; the process itself requires two or more people to act, independent of whether the key is split.
--> These are complementary, not identical: a system can have dual control without split knowledge (two people must both type in the SAME whole key/password) or split knowledge without dual control (one person could theoretically collect all the components alone if the process doesn't also enforce separation of duty).
--> This is exactly the principle underlying the **M-of-N key ceremony** described in note 20 — a root CA ceremony enforces BOTH split knowledge (each custodian's smartcard alone reveals nothing) and dual control (the ceremony script requires multiple named roles to act together, and no one role can unilaterally trigger key generation or export).
--> Contrast with plain KMS access control (also note 20): a single cloud admin with sufficiently broad IAM permissions can often generate, use, and delete a key alone — split knowledge/dual control has to be deliberately layered on top via organizational policy (separate approval workflows, break-glass procedures) since the KMS API itself doesn't enforce M-of-N by default the way a physical HSM quorum does.

## Key Destruction and Zeroization

--> "Deleting" a key file with a normal filesystem `rm`/`del` typically only removes the directory entry — the actual bytes remain on the physical media until overwritten by something else, recoverable with forensic tools until that happens. This is unacceptable for a cryptographic key, whose entire value is that it must become PERMANENTLY unusable on command.
--> **Zeroization** is the deliberate overwrite of key memory with zeros (or another fixed pattern) immediately after use and on destruction, both in RAM (so a memory dump or swap-to-disk doesn't leak it) and in persistent storage. FIPS 140-2/3 (note 20) mandates zeroization behavior for validated modules, including automatic zeroization on detected tamper.
--> **Cryptographic erasure** — for data-at-rest that was itself encrypted, you don't need to overwrite the (potentially huge) ciphertext at all: destroying only the small key that decrypts it renders the ciphertext permanently unrecoverable, since without the key it is computationally indistinguishable from random noise. This is why full-disk encryption makes "instant secure wipe" of an entire drive practical — reformatting a drive by re-encrypting is infeasible at scale, but zeroizing a 32-byte key is instant.
--> On an HSM/KMS specifically, "destroy this key" is itself usually a privileged, audited, sometimes M-of-N-gated operation precisely because it's irreversible — cloud KMS providers (note 20) enforce a mandatory waiting period (commonly 7–30 days) between a deletion request and actual destruction specifically to create a recoverable window against an accidental or malicious single-actor deletion request.

## Pitfalls

--> 1. **Treating rotation as "regenerate and hope"** — rotating a key without a versioning scheme (above) either breaks decryption of everything encrypted under the old key, or forces a risky synchronous mass re-encryption. Always version.
--> 2. **No revocation path distinct from expiry** — if the only way to invalidate a compromised key is to "wait for it to expire," you have no incident response capability; revocation must be checkable and enforced independently of the cryptoperiod (mirrors the OCSP/CRL discussion in note 09).
--> 3. **Confusing archival with active availability** — an archived key should be retrievable for legitimate historical decryption needs but NOT loaded into any live signing/encryption path; conflating the two reintroduces an old, weaker key into current operations.
--> 4. **Escrow without dual control** — implementing recovery/escrow (even for the legitimate enterprise case) as a single support engineer's unilateral access defeats the purpose; the same split-knowledge/dual-control discipline used for ceremonies should gate escrow retrieval.
--> 5. **Assuming `delete` is destruction** — for any key that ever existed on writable media outside an HSM, only explicit zeroization (or discarding the sole copy of a wrapping key, i.e. cryptographic erasure) gives an actual destruction guarantee.

--> Continue to note 22 for a different but related trust problem — protecting not just keys at rest, but the computation itself, from an untrusted host operating system or hypervisor, via Trusted Execution Environments.
