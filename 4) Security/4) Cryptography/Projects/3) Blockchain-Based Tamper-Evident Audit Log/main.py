"""
main.py

Demonstration:

  1. Build a legitimate audit chain of ~10 real compliance-style events
     (financial transactions / access-control events), each hash-linked to
     the previous block and signed by the logging service's Ed25519 key.

  2. Verify the whole chain -- expect every block to pass all three checks.

  3. Simulate an attacker who has gained raw write access to the underlying
     storage (e.g. direct DB/file access) and mutates ONE historical block's
     event data in place -- WITHOUT recomputing block_hash, previous_hash,
     or the signature, exactly as a real attacker without the signing
     private key would be forced to do (they cannot produce a new valid
     signature over their edited data).

  4. Re-verify the chain and show that the tampering is caught precisely:
     the exact block that was edited, and every downstream block whose
     hash-link is now broken as a consequence.
"""

from __future__ import annotations

from audit_chain import AuditChain
from chain_verifier import verify_chain
from signing_authority import SigningAuthority


def print_chain(chain: AuditChain) -> None:
    for block in chain:
        print(
            f"  Block #{block.index:>2} | data={block.data} | "
            f"prev_hash={block.previous_hash[:12]}... | "
            f"hash={block.block_hash[:12]}..."
        )


def main() -> None:
    print("=" * 78)
    print("BLOCKCHAIN-BASED TAMPER-EVIDENT AUDIT LOG")
    print("=" * 78)

    # --- Step 0: stand up the logging service's real Ed25519 identity ---
    authority = SigningAuthority()
    print(f"\n[Setup] Logging service Ed25519 public key: "
          f"{authority.public_key_bytes().hex()}")

    # --- Step 1: build a legitimate chain of ~10 compliance events ---
    chain = AuditChain(authority)

    events = [
        {"action": "LOGIN", "user": "alice", "ip": "10.0.0.5"},
        {"action": "WITHDRAWAL", "user": "alice", "account": "ACC-1001", "amount": 250.00},
        {"action": "LOGIN", "user": "bob", "ip": "10.0.0.9"},
        {"action": "PERMISSION_GRANT", "admin": "carol", "target_user": "bob", "role": "auditor"},
        {"action": "WITHDRAWAL", "user": "bob", "account": "ACC-2002", "amount": 4000.00},
        {"action": "TRANSFER", "user": "alice", "from_acc": "ACC-1001", "to_acc": "ACC-2002", "amount": 100.00},
        {"action": "LOGIN_FAILED", "user": "mallory", "ip": "203.0.113.7"},
        {"action": "ACCOUNT_LOCK", "user": "mallory", "reason": "3 failed logins"},
        {"action": "WITHDRAWAL", "user": "carol", "account": "ACC-3003", "amount": 750.50},
        {"action": "PERMISSION_REVOKE", "admin": "carol", "target_user": "bob", "role": "auditor"},
    ]

    for event in events:
        chain.append_event(event)

    print(f"\n[Step 1] Built legitimate audit chain "
          f"({len(chain)} blocks, including genesis):")
    print_chain(chain)

    # --- Step 2: verify the legitimate chain ---
    print("\n[Step 2] Verifying full chain (hash + link + signature)...")
    report = verify_chain(chain)
    print(report.summary())

    # --- Step 3: attacker mutates one historical block's data in place ---
    TARGET_INDEX = 5  # the WITHDRAWAL of $4000.00 by bob (index 5)
    tampered_block = chain.blocks[TARGET_INDEX]
    original_amount = tampered_block.data["amount"]
    tampered_amount = 40.00

    print(f"\n[Step 3] ATTACK SIMULATION")
    print(f"  An attacker with raw storage access edits block #{TARGET_INDEX} directly,")
    print(f"  changing 'amount' from {original_amount} to {tampered_amount}")
    print(f"  (covering up a large withdrawal as a small one), WITHOUT recomputing")
    print(f"  block_hash, previous_hash, or the signature -- they don't have the")
    print(f"  logging service's private key, so they can't produce a new valid signature.")

    tampered_block.data["amount"] = tampered_amount  # direct in-place mutation, like editing raw storage

    # --- Step 4: re-verify and show precise detection ---
    print("\n[Step 4] Re-verifying chain after tampering...")
    tampered_report = verify_chain(chain)
    print(tampered_report.summary())

    print("\n[Step 4b] Per-block detail after tampering:")
    for check in tampered_report.checks:
        status = "OK " if check.ok else "FAIL"
        print(
            f"  Block #{check.index:>2} [{status}] "
            f"hash_ok={check.hash_ok} link_ok={check.link_ok} "
            f"signature_ok={check.signature_ok}"
        )

    print("\n" + "=" * 78)
    print("CONCLUSION")
    print("=" * 78)
    print(
        f"Block #{TARGET_INDEX} failed its own hash check (data no longer matches\n"
        f"the hash committed at creation time) AND its signature check (the old\n"
        f"signature was only ever valid for the original data/hash).\n"
        f"Every block AFTER #{TARGET_INDEX} (blocks "
        f"{[i for i in tampered_report.broken_indices if i > TARGET_INDEX]}) "
        f"failed their LINK check,\n"
        f"because their stored previous_hash no longer matches block "
        f"#{TARGET_INDEX}'s real (recomputed) hash.\n"
        f"This is the hash-chain property in action: one historical edit is\n"
        f"detectable and precisely localized, and it invalidates every\n"
        f"subsequent link -- exactly like tampering with a block in a real\n"
        f"blockchain."
    )


if __name__ == "__main__":
    main()
