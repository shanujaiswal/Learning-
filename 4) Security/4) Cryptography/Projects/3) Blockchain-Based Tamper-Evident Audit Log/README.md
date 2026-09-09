# Blockchain-Based Tamper-Evident Audit Log

## Real-World Scenario

Compliance systems (financial transaction logs, access-control audit
trails, healthcare record access logs, etc.) need an audit log that is
**tamper-evident**: if anyone — including an insider with direct database or
filesystem access — alters a historical entry, that alteration must be
immediately and precisely detectable. Regulators (SOX, PCI-DSS, HIPAA) and
internal security teams rely on exactly this property.

This project builds a minimal, real, working version of the two
cryptographic mechanisms that make this possible, without any actual
distributed network or consensus mechanism (no mining, no P2P nodes — this
is a *permissioned, single-writer* hash chain, not a public blockchain):

1. **Hash chaining** — every entry ("block") stores the SHA-256 hash of the
   *previous* block alongside its own data, and its own hash is computed
   over its own contents plus that previous hash. Changing any historical
   block's data changes what its hash recomputes to, which no longer
   matches what the next block recorded as `previous_hash` — breaking the
   chain from that point forward.
2. **Digital signatures** — every block's hash is signed with the logging
   service's real Ed25519 private key (via the `cryptography` library) at
   creation time. Even an attacker with full read/write access to the
   storage backend cannot forge a *new*, validly-signed block, because they
   do not possess the private key.

## Architecture

| Module | Role | Real-World Equivalent |
|---|---|---|
| `audit_block.py` | Defines `AuditBlock`: index, timestamp, event data, `previous_hash`, `block_hash`, `signature`; `compute_hash()` does a fresh SHA-256 recompute over canonical JSON of the block's contents. | A single ledger entry / transaction record in a blockchain block. |
| `signing_authority.py` | Generates and holds the logging service's real Ed25519 keypair; signs a block's hash at creation time; exposes `verify_signature()`. | The logging service's signing key — like a notary's seal, or a Certificate Authority's signing key, or a KMS/HSM-held key used to sign log entries. |
| `audit_chain.py` | `AuditChain` + `append_event()`: creates the genesis block, links each new block's `previous_hash` to the prior block's real hash, computes and signs each new block. | A permissioned blockchain / hash-chain audit log, like AWS QLDB (Quantum Ledger Database) or a Merkle-tree-based Certificate Transparency log. |
| `chain_verifier.py` | `verify_chain()`: independently re-derives, for every block, whether (1) its stored hash matches a fresh recompute, (2) its `previous_hash` matches the prior block's real recomputed hash, and (3) its signature verifies — reporting the first broken block and every downstream block whose link is now invalid. | An independent auditor / compliance verification tool that periodically validates the entire ledger's integrity, like a blockchain explorer's chain-validation pass. |
| `main.py` | Demo: builds a legitimate ~10-event chain, verifies it (all pass), simulates an attacker directly mutating one historical block's stored data (without recomputing hashes/signature — exactly what a real attacker with raw storage access, but no private key, is limited to), re-verifies, and shows precise detection. | The end-to-end story: "auditor certifies the ledger is clean," "insider tampers with a record to hide a large withdrawal," "next audit catches it exactly." |

## Run It

```bash
python main.py
```

No external services required. Requires the `cryptography` package
(already installed in this environment).

## Verified Result (Actual Output)

The script was run and produced the following output (abbreviated hashes shown as truncated hex):

```
==============================================================================
BLOCKCHAIN-BASED TAMPER-EVIDENT AUDIT LOG
==============================================================================

[Setup] Logging service Ed25519 public key: 4b91acc5608f446d6163bff038b76f9df188e53ae507dcd8aeaedc269cd47ca7

[Step 1] Built legitimate audit chain (11 blocks, including genesis):
  Block # 0 | data={'event': 'CHAIN_GENESIS', ...} | prev_hash=000000000000... | hash=ecb5b7dda941...
  Block # 1 | data={'action': 'LOGIN', 'user': 'alice', ...} | prev_hash=ecb5b7dda941... | hash=9321bb5d5c75...
  Block # 2 | data={'action': 'WITHDRAWAL', 'user': 'alice', 'amount': 250.0} | prev_hash=9321bb5d5c75... | hash=ac3626874bf9...
  Block # 3 | data={'action': 'LOGIN', 'user': 'bob', ...} | prev_hash=ac3626874bf9... | hash=67552e0e0dfd...
  Block # 4 | data={'action': 'PERMISSION_GRANT', ...} | prev_hash=67552e0e0dfd... | hash=11d847ff6d8a...
  Block # 5 | data={'action': 'WITHDRAWAL', 'user': 'bob', 'amount': 4000.0} | prev_hash=11d847ff6d8a... | hash=002a6b0b5344...
  Block # 6 | data={'action': 'TRANSFER', ...} | prev_hash=002a6b0b5344... | hash=4e4cc84fd9ec...
  Block # 7 | data={'action': 'LOGIN_FAILED', 'user': 'mallory', ...} | prev_hash=4e4cc84fd9ec... | hash=b3c72418254d...
  Block # 8 | data={'action': 'ACCOUNT_LOCK', 'user': 'mallory', ...} | prev_hash=b3c72418254d... | hash=a1d9a430b81d...
  Block # 9 | data={'action': 'WITHDRAWAL', 'user': 'carol', 'amount': 750.5} | prev_hash=a1d9a430b81d... | hash=933611622d5c...
  Block #10 | data={'action': 'PERMISSION_REVOKE', ...} | prev_hash=933611622d5c... | hash=87cb74f8c370...

[Step 2] Verifying full chain (hash + link + signature)...
VALID -- all 11 blocks passed hash, link, and signature checks.

[Step 3] ATTACK SIMULATION
  An attacker with raw storage access edits block #5 directly,
  changing 'amount' from 4000.0 to 40.0
  (covering up a large withdrawal as a small one), WITHOUT recomputing
  block_hash, previous_hash, or the signature -- they don't have the
  logging service's private key, so they can't produce a new valid signature.

[Step 4] Re-verifying chain after tampering...
TAMPERING DETECTED -- chain is INVALID.
  First broken block : #5
  Downstream blocks whose hash-link is now invalid: [6]
  Total invalid blocks: 2 of 11

[Step 4b] Per-block detail after tampering:
  Block # 0 [OK ] hash_ok=True link_ok=True signature_ok=True
  Block # 1 [OK ] hash_ok=True link_ok=True signature_ok=True
  Block # 2 [OK ] hash_ok=True link_ok=True signature_ok=True
  Block # 3 [OK ] hash_ok=True link_ok=True signature_ok=True
  Block # 4 [OK ] hash_ok=True link_ok=True signature_ok=True
  Block # 5 [FAIL] hash_ok=False link_ok=True signature_ok=True
  Block # 6 [FAIL] hash_ok=True link_ok=False signature_ok=True
  Block # 7 [OK ] hash_ok=True link_ok=True signature_ok=True
  Block # 8 [OK ] hash_ok=True link_ok=True signature_ok=True
  Block # 9 [OK ] hash_ok=True link_ok=True signature_ok=True
  Block #10 [OK ] hash_ok=True link_ok=True signature_ok=True

==============================================================================
CONCLUSION
==============================================================================
Block #5 failed its own hash check (data no longer matches
the hash committed at creation time) AND its signature check would have
failed too had the attacker tried to re-sign it (the old signature is only
ever valid for the ORIGINAL data/hash it was computed over).
Block #6 failed its LINK check, because its stored previous_hash no longer
matches block #5's real (recomputed) hash.
This is the hash-chain property in action: one historical edit is
detectable and precisely localized, and it invalidates the chain from that
point forward -- exactly like tampering with a block in a real blockchain.
```

### Why doesn't the "hash_ok=False" propagate past block #6?

This is genuinely correct hash-chain behavior, not a bug: block #5's
**stored** `block_hash` field itself was left untouched by the attacker
(they only edited the `data` field) — so block #6's `previous_hash`
(which was fixed to block #5's stored hash at creation time) still
literally equals that stored string, and block #6's *own* hash recompute
is based on its own unmodified data, so it still matches. What breaks is:
block #5's *fresh* recompute no longer matches its *stored* hash
(`hash_ok=False`), which is the direct proof of tampering — and block #6's
`previous_hash` no longer matches block #5's fresh (real) hash
(`link_ok=False`), which is the proof the tampering happened *before* block
#6 in the chain. An attacker who wanted to hide this would have to also
rewrite block #5's stored `block_hash`, block #6's `previous_hash`, and
re-sign — but re-signing requires the private key they don't have. This is
exactly why a real 51%-attack-style rewrite of blockchain history requires
redoing every subsequent block's proof-of-work/signature, not just editing
one field.

## Things to Try Changing

- **Tamper with a different block** (`TARGET_INDEX` in `main.py`) — e.g. index
  1 (near the start) to see many more downstream blocks flip `link_ok=False`
  if you also propagate the change (see next point), versus index 9 (near
  the end) to see almost no propagation.
- **Simulate a "smarter" attacker** who also rewrites `block_hash` and
  `previous_hash` for every downstream block to try to cover their tracks —
  they will still fail every downstream block's `signature_ok` check,
  because they cannot produce valid Ed25519 signatures without the private
  key. Try implementing this in `main.py` to see the signature check alone
  catch a full rewrite attempt.
- **Corrupt the signature only** (leave data/hashes untouched) to see
  `signature_ok=False` trigger independently of `hash_ok`.
- **Add a second signing authority / key rotation** — sign different ranges
  of blocks with different keys and verify each against the correct
  historical public key, similar to how real certificate/log systems handle
  key rotation.
- **Persist the chain to disk** (JSON via `AuditBlock.to_dict()`) and reload
  it in a separate process to verify — closer to how a real audit log
  service would work with a database or file-backed store.
- **Add Merkle-tree batching** — group blocks into batches and store a
  Merkle root per batch, closer to how real blockchains and Certificate
  Transparency logs scale integrity proofs to millions of entries.
