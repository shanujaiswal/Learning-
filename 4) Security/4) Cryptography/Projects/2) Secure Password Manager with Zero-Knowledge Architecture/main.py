"""
main.py
========

Runs the full end-to-end story of the zero-knowledge password manager:

  1. Create a new local vault with several saved site entries (including a
     deliberately weak password and a deliberately reused password).
  2. LOCK the vault: derive a key from the master password + a fresh salt,
     and encrypt the vault with AES-256-GCM.
  3. "Upload" the encrypted blob + salt to the simulated zero-knowledge
     server. The master password and derived key never leave this process.
  4. UNLOCK with the CORRECT master password: re-derive the key, decrypt,
     and print the real saved entries.
  5. Attempt to UNLOCK with a WRONG master password: show that this fails
     cleanly (a caught exception / clear error), never silently returning
     garbled-but-present data.
  6. Run the password health checker against the decrypted vault and print
     which entries are flagged as weak and/or reused.
  7. Call `prove_cannot_read()` on the server to demonstrate that the server,
     using only what it stored, cannot recover the vault contents.

Nowhere in this file is the master password written to disk, logged to a
persistent file, or sent anywhere except into `derive_key(...)`.
"""

from __future__ import annotations

from key_derivation import derive_key, generate_salt
from password_health_checker import print_health_report
from vault_crypto import VaultDecryptionError, decrypt_vault, encrypt_vault
from zero_knowledge_server import ZeroKnowledgeServer

USERNAME = "alice"
CORRECT_MASTER_PASSWORD = "correct horse battery staple 42!"
WRONG_MASTER_PASSWORD = "correct horse battery staple 43!"  # one character off


def section(title: str) -> None:
    print("\n" + "=" * 78)
    print(title)
    print("=" * 78)


def main() -> None:
    # -------------------------------------------------------------------
    # Step 1: Create the local vault (this is what would live in the
    # Bitwarden/1Password desktop or browser client, entirely on-device).
    # -------------------------------------------------------------------
    section("STEP 1: Create local vault with saved entries")
    vault_entries = [
        {"site": "github.com", "username": "alice.dev", "password": "K7$mQ2!vXzL9pR4w"},
        {"site": "email.provider.com", "username": "alice@example.com", "password": "Tr0ub4dor&3-uniq"},
        # Deliberately weak (in the common-password list):
        {"site": "old-forum.example.com", "username": "alice123", "password": "123456"},
        # Deliberately reused across two different sites:
        {"site": "shopping-site.example.com", "username": "alice", "password": "hunter2"},
        {"site": "streaming-service.example.com", "username": "alice_w", "password": "hunter2"},
    ]
    for e in vault_entries:
        print(f"  + {e['site']:32s} user={e['username']:20s} password={e['password']}")

    # -------------------------------------------------------------------
    # Step 2: LOCK the vault -- derive key from master password, encrypt.
    # -------------------------------------------------------------------
    section("STEP 2: Lock the vault (derive key + AES-256-GCM encrypt)")
    salt = generate_salt()
    print(f"  Generated per-user random salt: {salt.hex()}")
    print("  Deriving key from master password via PBKDF2-HMAC-SHA256 "
          "(600,000 iterations)...")
    encryption_key = derive_key(CORRECT_MASTER_PASSWORD, salt)
    print(f"  Derived 256-bit key (hex, for demo only): {encryption_key.hex()}")

    encrypted_blob = encrypt_vault(vault_entries, encryption_key)
    print(f"  Encrypted vault: nonce={encrypted_blob.nonce.hex()}")
    print(f"  Ciphertext (hex, first 60 chars): {encrypted_blob.ciphertext.hex()[:60]}...")
    print(f"  Ciphertext length: {len(encrypted_blob.ciphertext)} bytes "
          "(plaintext length + 16-byte GCM auth tag)")

    # The master password and derived key now go out of "active use" here --
    # in a real client they'd be dropped as soon as possible.
    del encryption_key

    # -------------------------------------------------------------------
    # Step 3: The "server" stores ONLY the salt + encrypted blob.
    # -------------------------------------------------------------------
    section("STEP 3: Upload encrypted blob to the zero-knowledge server")
    server = ZeroKnowledgeServer()
    server.store_vault(USERNAME, salt, encrypted_blob)

    # -------------------------------------------------------------------
    # Step 4: Unlock with the CORRECT master password.
    # -------------------------------------------------------------------
    section("STEP 4: Unlock vault with the CORRECT master password")
    record = server.fetch_vault(USERNAME)
    correct_key = derive_key(CORRECT_MASTER_PASSWORD, record.salt)
    decrypted_entries = decrypt_vault(record.blob, correct_key)
    print("  Decryption SUCCEEDED. Recovered entries:")
    for e in decrypted_entries:
        print(f"    - {e['site']:32s} user={e['username']:20s} password={e['password']}")
    del correct_key

    # -------------------------------------------------------------------
    # Step 5: Attempt to unlock with a WRONG master password.
    # -------------------------------------------------------------------
    section("STEP 5: Attempt to unlock with a WRONG master password")
    record = server.fetch_vault(USERNAME)
    wrong_key = derive_key(WRONG_MASTER_PASSWORD, record.salt)
    try:
        garbage = decrypt_vault(record.blob, wrong_key)
        print("  UNEXPECTED: decryption succeeded with the wrong password!")
        print(f"  Contents: {garbage}")
    except VaultDecryptionError as exc:
        print(f"  Decryption FAILED CLEANLY, as expected: {exc}")
        print("  No partial or garbled vault data was returned to the caller.")
    del wrong_key

    # -------------------------------------------------------------------
    # Step 6: Password health check on the decrypted vault.
    # -------------------------------------------------------------------
    section("STEP 6: Password health / breach-pattern check")
    print_health_report(decrypted_entries)

    # -------------------------------------------------------------------
    # Step 7: Prove the server could never read the vault.
    # -------------------------------------------------------------------
    section("STEP 7: Prove the server cannot read the vault")
    print(server.prove_cannot_read(USERNAME))

    section("DONE")
    print("Summary: correct master password unlocked the real vault; wrong\n"
          "master password failed cleanly via the AES-GCM auth tag; the\n"
          "server-held blob is confirmed to be unreadable ciphertext; and\n"
          "the health checker flagged the weak/reused saved passwords.")


if __name__ == "__main__":
    main()
