### Hardware Security Modules and Cloud KMS

--> Every scheme covered so far (RSA in note 05, ECC in note 08, AES in note 03) assumes the private/secret key exists SOMEWHERE as bytes in memory at signing/decryption time. The question this note answers: where do those bytes actually live in a real production system, and how do you keep them from ever touching a general-purpose CPU's RAM in recoverable plaintext form?
--> A Hardware Security Module (HSM) is a dedicated, tamper-resistant physical device whose entire purpose is to generate, store, and use cryptographic keys such that the key material never leaves the device in plaintext — the device performs the operation (sign, decrypt, wrap) internally and returns only the result.

## Why an HSM Exists

--> A key stored as a file on a server's disk, even encrypted, is only as safe as the OS, the admin's laptop, the backup pipeline, and every process with root — a single privilege escalation anywhere in that chain can exfiltrate it. An HSM narrows the attack surface to the device itself.
--> 1. **Tamper resistance/evidence** — physical intrusion (drilling, voltage glitching, temperature/laser probing) triggers active zeroization of key material, sometimes within microseconds. Some modules physically self-destruct their memory contents on chassis breach.
--> 2. **No plaintext key export** — the API is designed so keys can be used but not read out. A well-configured HSM has no operation that returns raw private key bytes.
--> 3. **Isolated cryptographic execution** — the actual math (RSA sign, AES encrypt, ECDSA) happens on a separate, hardened processor, so a compromised host OS cannot single-step or memory-dump the operation.
--> 4. **Certified assurance** — FIPS 140-2/3 gives a government-recognized bar you can point to in an audit instead of trusting a vendor's marketing claims.

## FIPS 140-2/3 Levels

| Level | Guarantee | Typical use |
|---|---|---|
| Level 1 | Basic — approved algorithms, no physical security requirement | Software crypto modules, low-assurance embedded use |
| Level 2 | Level 1 + tamper-EVIDENCE (seals/coatings that show evidence of physical access) + role-based authentication | Entry-level dedicated appliances |
| Level 3 | Level 2 + tamper-RESISTANCE (active response — zeroizes keys on detected intrusion), identity-based auth, physical/logical separation of interfaces | Most production payment and CA-root HSMs (e.g. Thales Luna, AWS CloudHSM) |
| Level 4 | Level 3 + protection against environmental attacks (voltage/temperature manipulation), full envelope of physical protection | Highest-assurance deployments — national CA roots, some banking infrastructure |

--> FIPS 140-3 (2019 onward, superseding 140-2) aligns to the international ISO/IEC 19790 standard rather than defining its own criteria from scratch, but keeps the same four-level structure. Most vendor HSMs sold today are validated (or in the process of revalidation) under 140-3; existing 140-2 certificates remain valid for a transition period rather than being invalidated overnight.

## PKCS#11 — The Standard API

--> Applications don't talk to an HSM's internals directly — they talk through **PKCS#11** ("Cryptoki"), a vendor-neutral C API standardized by RSA Labs and later OASIS, so the same application code can target a Thales HSM, a SoftHSM test instance, or a smartcard without rewriting the crypto calls.
--> The mental model maps cleanly onto physical smartcard terminology:
--> 1. **Slot** — a logical or physical reader/socket. A single HSM appliance may expose many slots.
--> 2. **Token** — the actual cryptographic device present in a slot (the thing holding keys). Slot and token are often conflated in casual usage but the standard distinguishes them because a slot can be empty.
--> 3. **Session** — a connection an application opens to a token, either read-only or read/write, optionally authenticated as a specific user role.
--> 4. **Object** — keys, certificates, and data blobs stored on the token, each with a handle and a set of attributes (`CKA_EXTRACTABLE`, `CKA_SENSITIVE`, `CKA_SIGN`, etc.) that the HSM enforces at the hardware level — e.g. an object flagged `CKA_EXTRACTABLE=FALSE` and `CKA_SENSITIVE=TRUE` can be used for signing but genuinely cannot be read out via any PKCS#11 call.
--> The core function-call shape an application actually issues:
--> 1. `C_OpenSession` — establish a session with a slot.
--> 2. `C_Login` — authenticate (PIN, password, or smartcard) as a specific role (Security Officer vs normal User — see key ceremonies below).
--> 3. `C_GenerateKeyPair` / `C_GenerateKey` — ask the HSM to generate a keypair or symmetric key INSIDE the device; the private/secret handle never leaves.
--> 4. `C_SignInit` / `C_Sign` — hand the HSM a hash (or raw data) and a key handle, get back a signature. The private key bytes are never referenced by the caller — only a handle (an integer) is.
--> 5. `C_EncryptInit` / `C_Encrypt`, `C_DecryptInit` / `C_Decrypt` — same handle-based pattern for symmetric/asymmetric encryption.
--> 6. `C_WrapKey` / `C_UnwrapKey` — encrypt one key using another key that's also on the device, for secure key transport between HSMs or into/out of cold storage, again without either key appearing in host memory in plaintext.

```python
# Illustrative PKCS#11 usage via python-pkcs11 (wraps the underlying
# Cryptoki C library). The point of this example is what's ABSENT: at
# no point does private key material cross into a Python variable.
import pkcs11
from pkcs11 import KeyType, ObjectClass, Mechanism

lib = pkcs11.lib("/usr/lib/softhsm/libsofthsm2.so")   # vendor's Cryptoki .so/.dll
token = lib.get_token(token_label="MyHSMToken")

with token.open(user_pin="1234", rw=True) as session:
    # Key is generated INSIDE the device; `pubkey`/`privkey` here are
    # opaque handles, not the actual key bytes.
    pubkey, privkey = session.generate_keypair(
        KeyType.EC, 256, store=True, label="root-signing-key",
    )

    message_hash = b"\x9f" * 32  # SHA-256 digest computed by the caller
    # Signing happens on-device; only the signature crosses back out.
    signature = privkey.sign(message_hash, mechanism=Mechanism.ECDSA)

    assert pubkey.verify(message_hash, signature, mechanism=Mechanism.ECDSA)
```

--> This is why "the application talks to the HSM through PKCS#11" matters architecturally: it means an attacker who fully compromises the application host still only gets sign/encrypt *capability* through the open session, not the key itself — and that capability disappears the moment the session or PIN-authenticated login ends.

## Key Ceremonies

--> A **key ceremony** is a formal, scripted, witnessed procedure for generating or activating a high-value key (most commonly a root CA private key, as introduced in note 09's chain-of-trust discussion) in a way that no single individual could later be accused of having sole access to it.
--> **M-of-N control** — the key material (or the credentials needed to authorize its use) is split so that any M out of N designated custodians must be physically present and cooperate to authorize an operation; N-M people alone can do nothing. This is enforced either via HSM-native smartcard quorum (e.g. Thales/Entrust HSMs support an "M of N" operator card set — the HSM itself refuses to unlock without M distinct physical cards inserted and PINs entered) or via Shamir's Secret Sharing at the application layer.
--> A real-world root CA ceremony typically runs like this:
--> 1. Air-gapped machine and HSM, never previously and never subsequently connected to any network, brought out of sealed storage for the ceremony only.
--> 2. Multiple named participants with defined roles: ceremony administrator, M-of-N key custodians (each holding one smartcard/PIN), and independent **witnesses** (often an external auditor) who verify but hold no operational role.
--> 3. A pre-written, line-by-line **ceremony script** is followed exactly and read aloud — no ad-libbing commands, specifically to make the process auditable and repeatable if something goes wrong.
--> 4. The HSM generates the root keypair internally (`C_GenerateKeyPair`, never imported from outside); the private key attribute is set non-extractable at creation and never changes.
--> 5. The self-signed root certificate is produced, and the HSM's internal key-backup mechanism (itself M-of-N smartcard protected) creates a cloneable backup for disaster recovery — this is the one place the key ever exists outside the single device, and it's still never plaintext, only re-wrapped for another HSM of the same model/firmware.
--> 6. Every step, screen output, and card insertion is logged on video and in a signed transcript; witnesses countersign the transcript. This audit trail is what a browser root program (e.g. Mozilla's, as referenced when discussing CA trust in note 09) or a compliance auditor later reviews to trust that the root's integrity was never at risk of single-party compromise.
--> The HSM is then sealed, and the air-gapped machine is powered down and returned to a safe/vault, often not to be powered on again for months or years except for scheduled intermediate-signing ceremonies.

## Cloud KMS — HSM as a Service

--> Running your own HSM appliance is expensive and operationally heavy (rack space, firmware patching, quorum-card logistics). Cloud KMS services abstract this away: AWS KMS, Azure Key Vault (Managed HSM tier), and GCP Cloud KMS all run customer key operations on FIPS 140-2/3 validated HSMs on the provider's side, exposed through a plain API call instead of PKCS#11 sessions.
--> The provider still guarantees the master key never leaves the HSM boundary in plaintext — you get the PKCS#11 security property without operating physical hardware.

## Envelope Encryption — The Pattern Common to All Three

--> KMS APIs deliberately do NOT offer "encrypt this 4GB file" as an operation. Sending bulk data to a network API is slow, expensive (most KMS pricing is per-call and caps payload size — AWS KMS caps direct `Encrypt` calls at 4KB), and pointlessly routes gigabytes through a shared multi-tenant service for no security benefit.
--> Instead every cloud KMS implements **envelope encryption**:
--> 1. Ask the KMS to generate a **data key** — a fresh symmetric key (typically AES-256), generated using the KMS's CSPRNG (see note 12) inside the HSM boundary.
--> 2. The KMS returns TWO things: the data key in plaintext (for immediate local use) and the SAME data key encrypted ("wrapped") under your **KMS master key** (a Customer Master Key / CMK in AWS terms, a Key Vault key in Azure terms, a CryptoKey in GCP terms) — the master key itself never leaves the HSM, ever.
--> 3. Your application uses the plaintext data key locally to AES-GCM encrypt the actual bulk data, then immediately discards the plaintext data key from memory.
--> 4. You store the **ciphertext blob** — encrypted data + the encrypted (wrapped) data key + the AES-GCM nonce/tag — together. The wrapped data key travels with the ciphertext; there is nothing secret about storing it right next to the data it protects, because it's useless without a call back to the KMS to unwrap it.
--> 5. To decrypt later: send the small wrapped data key blob (not the bulk data) to the KMS's `Decrypt`/`UnwrapKey` call, get back the plaintext data key, then AES-GCM decrypt the bulk data locally.
--> This means the master key touches the HSM boundary only for tiny key-sized operations (generate/wrap/unwrap a 32-byte key), while the expensive bulk AES work happens locally at full CPU/AES-NI speed — the KMS never sees or handles your actual data.

```python
import boto3
import os
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

kms = boto3.client("kms", region_name="us-east-1")
KEY_ID = "arn:aws:kms:us-east-1:111122223333:key/root-data-key"

def envelope_encrypt(plaintext: bytes, aad: bytes = b"") -> dict:
    # Step 1+2: ask KMS for a fresh data key; it comes back BOTH ways —
    # plaintext for immediate use, and a ciphertext blob wrapped under
    # the KMS master key, which never leaves the HSM boundary.
    resp = kms.generate_data_key(KeyId=KEY_ID, KeySpec="AES_256")
    plaintext_data_key = resp["Plaintext"]          # 32 raw bytes, use then discard
    wrapped_data_key = resp["CiphertextBlob"]        # safe to store alongside the data

    # Step 3: bulk encryption happens LOCALLY — this is the whole point.
    # A multi-GB file never crosses the network to the KMS API.
    nonce = os.urandom(12)                           # see note 12 — CSPRNG-sourced nonce
    aesgcm = AESGCM(plaintext_data_key)
    ciphertext = aesgcm.encrypt(nonce, plaintext, aad)

    # Immediately drop the plaintext key reference — nothing else in this
    # process should hold a copy of it beyond this function's scope.
    del plaintext_data_key

    return {
        "wrapped_data_key": wrapped_data_key,   # stored, useless without KMS access
        "nonce": nonce,
        "ciphertext": ciphertext,               # includes the GCM auth tag
    }

def envelope_decrypt(blob: dict, aad: bytes = b"") -> bytes:
    # Step 5: send only the small wrapped key back to KMS, not the bulk data.
    resp = kms.decrypt(CiphertextBlob=blob["wrapped_data_key"], KeyId=KEY_ID)
    plaintext_data_key = resp["Plaintext"]

    aesgcm = AESGCM(plaintext_data_key)
    plaintext = aesgcm.decrypt(blob["nonce"], blob["ciphertext"], aad)
    del plaintext_data_key
    return plaintext
```

--> Note the AES-GCM nonce is generated locally with a CSPRNG (note 12) — the KMS call supplies the KEY, not the nonce, and reusing a nonce under the same key is exactly the AES-GCM catastrophic-failure mode described in note 03's pitfalls.

## AWS KMS vs Azure Key Vault vs GCP KMS

| Dimension | AWS KMS | Azure Key Vault (Managed HSM) | GCP Cloud KMS |
|---|---|---|---|
| HSM backing | FIPS 140-2 Level 3 validated HSMs (CloudHSM available for dedicated single-tenant HSM) | FIPS 140-2 Level 3 validated (Managed HSM tier); standard tier is software-backed multi-tenant | FIPS 140-2 Level 3 validated (Cloud HSM tier); software tier also available |
| Key policy model | Resource-based **key policy** JSON attached to the key itself, plus IAM policies — both must allow | Azure RBAC roles + (legacy) access policies scoped to the vault/key | Cloud IAM roles/bindings scoped at the key, key ring, or project level |
| Envelope key-gen call | `GenerateDataKey` / `GenerateDataKeyWithoutPlaintext` | `WrapKey` (you generate the data key locally, then ask Key Vault to wrap it — no native "generate + return both forms" call) | `encrypt` on a CryptoKey, or client-side `generateDataKey` pattern via Tink integration |
| Direct small-payload encrypt | `Encrypt`/`Decrypt` (4KB payload cap) | `Encrypt`/`Decrypt` operations on a Key Vault key | `encrypt`/`decrypt` on a CryptoKeyVersion (64KB cap for symmetric) |
| Multi-region / replication | Multi-Region Keys (explicit primary + replica keys, same key material) | Geo-replication tied to the vault, not per-key control | Key rings are regional or global; no automatic cross-region key replication |

## Pitfalls

--> 1. **Calling KMS `Encrypt` directly on bulk data** — hitting the payload size cap, paying per-call pricing for something that should be one local AES operation, and adding a network round-trip per megabyte. Always envelope-encrypt.
--> 2. **Caching the plaintext data key too long** — the data key should live in memory only as long as the immediate encrypt/decrypt operation; long-lived caching reintroduces the exact "key sitting in plaintext" risk the KMS was meant to eliminate. Cloud SDKs (AWS Encryption SDK, Google Tink) handle this correctly by default — hand-rolled envelope code often doesn't.
--> 3. **Treating the master key as a data key** — using the KMS master key directly for high-volume encryption, either hitting rate limits or, worse, requesting it be made "extractable" — most providers refuse this for HSM-backed keys, but software-backed tiers may allow exactly this footgun.
--> 4. **Skipping AAD (additional authenticated data)** — both KMS `Encrypt` calls and local AES-GCM support binding context (e.g. a record's object ID) into the auth tag; omitting it allows ciphertext to be silently swapped between two records that would otherwise decrypt "successfully."
--> 5. **No M-of-N equivalent for cloud key deletion** — most cloud KMS deletion is a single-admin-triggered action with only a waiting-period safety net (7–30 days), a much weaker control than a physical HSM ceremony's dual/multi-person authorization. Treat key deletion permissions with the same seriousness as a root CA ceremony custodian role.

--> Continue to note 21 for what happens to a key across its full lifecycle after it's generated — rotation, escrow, and eventual destruction — and to note 22 for how "compute without exposing plaintext" extends from key operations into general workload execution via Trusted Execution Environments.
