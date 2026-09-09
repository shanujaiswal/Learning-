### Cryptocurrency Wallet Key Management - BIP32 39 44

--> A "wallet" doesn't store coins. Coins (or rather, unspent transaction outputs / account balances) live on a public, replicated ledger forever. A wallet stores **private keys** — the only thing that lets you produce a valid ECDSA signature (see note 08, secp256k1) authorizing a transaction that moves value associated with the corresponding public key/address.
--> Lose the private key, lose the ability to sign — the balance is still on the ledger, permanently unreachable. Leak the private key, and anyone else can sign in your place. There is no password reset, no "forgot my key" flow, no bank to call. This single fact drives every design choice covered below.
--> Bitcoin/Ethereum wallets almost universally use secp256k1 ECDSA (as covered in note 08) for signing. Everything in this note is about how the private keys that feed into that signing operation are generated, organized, and backed up — the wallet layer sits entirely on top of the ECC math you already know.

## BIP-39 — Mnemonic Seed Phrases

--> BIP-39 solves a usability problem: a raw 256-bit private key (`4f3a9c...` as 64 hex chars) is nearly impossible for a human to write down, read back, and re-type correctly without error. BIP-39 converts random entropy into a sequence of common English words that humans can transcribe reliably.
--> The process, step by step:
--> 1. Generate `128`-`256` bits of true random entropy (via a CSPRNG — see note 12) in multiples of 32 bits. 128 bits -> 12-word phrase, 256 bits -> 24-word phrase (the common security-conscious default).
--> 2. Append a **checksum**: take `SHA-256(entropy)`, and append its first `entropy_length / 32` bits to the entropy. This checksum lets wallet software detect a typo'd or corrupted word during recovery — a wrong word is very likely to fail the checksum check.
--> 3. Split the (entropy + checksum) bit string into 11-bit groups. Each 11-bit group is an index (0-2047) into the standardized **BIP-39 wordlist** of exactly 2048 words. Each group becomes one word of the mnemonic.
--> 4. The result — e.g. `witch collapse practice feed shame open despair creek road again ice least` — is the seed phrase you write down.

```
entropy (128-256 bits) --SHA256--> checksum bits appended
        |
        v
split into 11-bit chunks --> index into 2048-word list --> mnemonic words
```

--> Deriving the actual binary seed from the mnemonic is where note 07's PBKDF2 comes in directly: `seed = PBKDF2-HMAC-SHA512(password = mnemonic, salt = "mnemonic" + passphrase, iterations = 2048, dklen = 64 bytes)`.
--> The optional user-supplied **passphrase** (sometimes called the "25th word") is NOT stored anywhere and is not part of the wordlist — it's mixed directly into the PBKDF2 salt. Forgetting it means the resulting seed is unrecoverable even with the correct 24 words, which is either a powerful plausible-deniability / hidden-wallet feature or a catastrophic footgun depending on how carefully it's backed up.
--> Why PBKDF2 here specifically (and not just SHA-512 once): the mnemonic's entropy is already high (128-256 bits from a CSPRNG), so this isn't really defending against brute force the way password hashing is (see note 07's KDF distinction) — the fixed 2048-iteration count is mostly a deliberate, standardized work factor baked into the BIP-39 spec so all wallet implementations derive the identical 64-byte seed from the same mnemonic, and reference implementations agreed on when the standard was written.

## BIP-32 — Hierarchical Deterministic (HD) Wallets

--> Before BIP-32, wallets generated and stored a pool of independent, unrelated private keys — each one needing its own separate backup. BIP-32 makes an entire tree of keys deterministically derivable from a single 64-byte seed, so ONE backup (the seed / mnemonic) recovers every key that was ever derived from it, past or future.
--> The seed produces a **master key pair** (`m`) plus a **master chain code** — a 256-bit private key and a separate 256-bit value that's mixed into every child derivation as extra entropy so that knowing a child key alone tells you nothing about sibling keys.
--> Child Key Derivation (CKD) function, informally: `(child_key, child_chain_code) = HMAC-SHA512(parent_chain_code, parent_key_material || index)`, split into two 256-bit halves — the left half is combined with the parent private key (mod curve order n) to get the child private key, the right half becomes the child chain code.
--> Because it's deterministic, the same `(parent_key, chain_code, index)` always regenerates the same child — this is "hierarchical deterministic": an entire tree of key pairs, reproducible from the root seed alone, with no need to store intermediate keys.

## Hardened vs Non-Hardened Derivation

--> **Non-hardened** derivation uses the parent's *public* key (plus chain code and index) as HMAC input. This allows a neat trick: a "watch-only" wallet holding only the parent's public key + chain code can derive all non-hardened child public keys (to generate receive addresses, watch balances) WITHOUT ever holding a private key.
--> The catch: with non-hardened derivation, if an attacker obtains ONE child private key AND the parent's public key (or extended public key, `xpub`) — both individually non-secret-feeling pieces of information that get shared more casually (an `xpub` is often handed to a watch-only server or accounting tool) — they can algebraically invert the CKD formula and recover the **parent private key**. From the parent private key, every other child key in that entire subtree (siblings, cousins, everything below the parent) is trivially re-derivable.
--> **Hardened** derivation breaks this by using the parent's *private* key (never the public key) as HMAC input, indexed with values `>= 2^31` (written with a trailing `'`, e.g. `44'`). Since deriving a hardened child requires the parent private key, an attacker with a leaked child private key plus the parent's public key gains nothing — the math simply can't be run backward without the parent private key they don't have.
--> The tradeoff: a hardened child's PUBLIC key cannot be derived from the parent's public key alone — you need the parent private key to derive the child's key pair (public included) at all. This is precisely why watch-only setups sit below the hardened boundary, and every level at or above the "account" level in BIP-44 (`m/44'/coin'/account'`) is mandated hardened — a single leaked address-level private key must never be able to compromise the account's — let alone the whole wallet's — master tree.

## BIP-44 — The Standard Derivation Path

--> BIP-44 imposes a fixed, agreed-upon path structure on top of BIP-32 so any BIP-39/32/44-compliant wallet can reconstruct the exact same accounts and addresses from the same seed, regardless of which software generated them:

```
m / 44' / coin_type' / account' / change / address_index
```

| Level | Meaning | Hardened? |
|---|---|---|
| `44'` | Fixed purpose constant — "this is a BIP-44 path" | Yes |
| `coin_type'` | Which cryptocurrency (`0'` = Bitcoin, `60'` = Ethereum, per SLIP-44 registry) | Yes |
| `account'` | User-chosen account number (`0'`, `1'`, ...) — lets one seed cleanly separate "savings", "spending", "business" | Yes |
| `change` | `0` = receiving addresses, `1` = internal/change addresses (UTXO-model change outputs) | No |
| `address_index` | Sequential address number within that account/branch (`0`, `1`, `2`, ...) | No |

--> Practical upshot: `m/44'/0'/0'/0/0` is "Bitcoin, account 0, receive address 0" and `m/44'/60'/0'/0/0` is "Ethereum, account 0, address 0" — both derived deterministically from the SAME seed phrase. One 24-word backup therefore backs an effectively unlimited number of accounts, coins, and addresses, forever reproducible on any compliant wallet software.
--> This is why restoring a wallet on new hardware is "type in 24 words" rather than "restore from a backup file" — the words ARE the backup; everything else is recomputed from them.

## Worked Example — Mnemonic to Master Key to Child Key

```python
# Illustrative pseudocode following the hdwallet / bip32utils style APIs.
# (In a real project: `pip install hdwallet` or `pip install bip32utils`.)

from hdwallet import HDWallet
from hdwallet.symbols import BTC

# --- 1. Generate or import a BIP-39 mnemonic ---
# hdwallet.mnemonic delegates to the standard wordlist + checksum process
# described above; in practice you'd use hdwallet's HDWallet.generate_mnemonic()
mnemonic = "witch collapse practice feed shame open despair creek road again ice least"

hdwallet = HDWallet(symbol=BTC)
hdwallet.from_mnemonic(mnemonic=mnemonic, language="english", passphrase="")

# --- 2. Mnemonic -> 64-byte seed via PBKDF2-HMAC-SHA512 (see note 07) ---
print("seed:", hdwallet.seed())
# e.g. seed: 5eb00bbddcf069084889a8ab9155568165f5c453ccb85e70811aaed6f6da5fc19a5ac40b389cd370d086206dec8aa6c43daea6690f20ad3d8d48b2d2ce9e38

# --- 3. Seed -> BIP-32 master key + master chain code ---
print("master xprv:", hdwallet.root_xprivate_key())
print("master xpub:", hdwallet.root_xpublic_key())

# --- 4. Derive a specific BIP-44 child: m/44'/0'/0'/0/0 ---
hdwallet.from_path(path="m/44'/0'/0'/0/0")

print("path:", hdwallet.path())            # m/44'/0'/0'/0/0
print("private key:", hdwallet.private_key())
print("public key:", hdwallet.public_key())
print("address:", hdwallet.p2pkh_address())

# --- 5. A watch-only setup: derive addresses from the ACCOUNT xpub alone ---
# Everything from "account" downward (change, address_index) is NON-hardened,
# so an accounting/monitoring service holding only this xpub can enumerate
# every receive address without ever touching a private key.
account_xpub = hdwallet.root_xpublic_key()  # in practice: the account-level xpub, not root
```

## Custody Models

| Model | Where the private key lives | Convenience | Attack surface |
|---|---|---|---|
| Hot wallet | Software on an internet-connected device (phone/browser extension) | High — instant signing | Malware, browser exploits, clipboard hijackers, phishing |
| Cold / hardware wallet | Dedicated offline device (Ledger/Trezor-style secure element) | Medium — plug in to sign | Supply-chain/firmware tampering, physical theft, evil-maid access |
| Paper / metal backup | Seed phrase written/etched, stored offline, never typed into a device | Lowest (recovery-only) | Physical theft, fire/water damage, discovery by anyone who finds it |

--> The seed phrase is the **single point of total compromise** for every model above. Unlike a traditional credential covered in note 21 (key rotation, revocation, short-lived certs), a BIP-39/32 seed has no rotation mechanism baked into the standard — whoever holds the 12/24 words can derive and control every past and future key in the entire tree, permanently, the instant they have it. Rotating "away" from a compromised seed means generating an entirely new seed and manually moving all funds to it before the attacker does — a race, not a fix.
--> This is fundamentally different from rotating a leaked TLS private key (revoke the cert, issue a new one, old key becomes worthless) — there is no certificate authority or revocation list for a wallet seed. The ledger itself doesn't know or care that a key was "supposed to" be retired.

## Common Real-World Attack Vectors

--> 1. **Clipboard hijacking malware** — malware that watches the clipboard for anything shaped like a cryptocurrency address and silently swaps in the attacker's address before the user pastes it into a "send" field. The user signs a real, validly-formed transaction — just to the wrong destination — and nothing about the signature or the chain looks anomalous.
--> 2. **Fake/tampered hardware wallet firmware** — a hardware wallet whose firmware has been modified (via supply-chain interdiction or a malicious "update") to leak the seed or generate a predictable one, defeating the entire point of keeping the key in a dedicated offline secure element.
--> 3. **Seed phrase phishing via fake "import your wallet" prompts** — fake wallet apps, browser extensions, or support-chat scripts that ask the victim to "restore" or "verify" their wallet by typing in their 12/24-word phrase, which is then exfiltrated. No legitimate wallet ever needs your seed phrase to "verify" anything — it's needed only to derive keys, i.e. to take control.
--> 4. **Physical theft of a written-down phrase** — a phrase stored on paper in a drawer or safe is only as secure as physical access to that location; unlike a digital secret, it can be stolen without leaving any trace of the private key material being "accessed" (no logs, no alerts).

## Institutional Alternative — Threshold Custody

--> A single seed phrase is a single point of failure by design, which is acceptable for individual retail custody but unacceptable at institutional scale (exchanges, custodians) where one compromised phrase means all customer funds. Note 25 covers threshold-ECDSA / multi-party computation custody, where no single party ever holds a complete private key or seed — signing requires cooperation among a threshold of independent key shares, so no single leak, insider, or seed phrase compromise is sufficient to move funds.

## Pitfalls

--> 1. **Storing the seed phrase digitally in plaintext** (photo on phone, note-taking app, cloud drive) — turns a physical-security problem into a "any malware or account-compromise on that device/service" problem, which is a far larger attack surface.
--> 2. **Reusing a passphrase-less seed across "hot" and "cold" contexts** — typing the same recovery phrase into a hot wallet app for convenience defeats the isolation a hardware wallet was bought to provide.
--> 3. **Trusting an unverified derivation path** — some wallets default to non-BIP-44-compliant paths for certain coins; restoring the same words into a different wallet without matching the exact path can show a "zero balance" for funds that are actually still there under a different derivation path.
--> 4. **Skipping hardened derivation above the account level** in custom tooling — exposing an `xpub` for convenience (e.g. to a bookkeeping tool) at a level that isn't hardened above it risks the parent-key-recovery attack described above if any descendant private key ever leaks.
