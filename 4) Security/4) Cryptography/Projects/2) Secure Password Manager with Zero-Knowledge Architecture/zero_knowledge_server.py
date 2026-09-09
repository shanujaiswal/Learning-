"""
zero_knowledge_server.py
=========================

Simulates the remote "cloud sync" server (the role played by Bitwarden's or
1Password's servers). This module models the server's storage and the
server's LACK of capability -- it holds:

  - the per-user random salt (not secret, needed to re-derive the key later)
  - the encrypted vault blob (nonce + AES-GCM ciphertext+tag)

It deliberately exposes NO decrypt method, NO key parameter anywhere in its
API, and never receives the master password or derived key at all. This
mirrors real zero-knowledge architectures: even a fully compromised server (or
a subpoena, or a rogue employee with full DB access) yields only random-
looking ciphertext.

`prove_cannot_read()` is a lightweight, auditable demonstration of that: it
takes only what the server has on disk and tries to interpret it as UTF-8
JSON -- exactly what a curious server operator staring at the database would
try first -- and shows that this fails.
"""

from __future__ import annotations

import json
from dataclasses import dataclass, field

from vault_crypto import EncryptedBlob


@dataclass
class ServerRecord:
    """What the server persists for one user account. Note: no password
    field, no key field -- structurally incapable of holding secrets."""
    username: str
    salt: bytes
    blob: EncryptedBlob


class ZeroKnowledgeServer:
    """A simulated backend. Stands in for Bitwarden/1Password's cloud sync
    service, S3 bucket, or database row for a given account.

    Every method here only ever touches ciphertext, salts, and metadata.
    There is intentionally no `decrypt(...)`, no `login(password)`, no
    `reset_password(...)` that could reveal plaintext -- because the real
    server can't do any of those either.
    """

    def __init__(self) -> None:
        self._records: dict[str, ServerRecord] = {}

    def store_vault(self, username: str, salt: bytes, blob: EncryptedBlob) -> None:
        """Upload/replace the encrypted vault for `username`.

        This is the ONLY write path into server storage, and it accepts only
        already-encrypted material -- there is no code path by which a
        plaintext password entry could reach this method.
        """
        self._records[username] = ServerRecord(username=username, salt=salt, blob=blob)
        print(f"[server] stored encrypted vault for '{username}' "
              f"({len(blob.ciphertext)} bytes ciphertext, "
              f"{len(salt)}-byte salt).")

    def fetch_vault(self, username: str) -> ServerRecord:
        """Return the stored salt + encrypted blob for the client to decrypt
        LOCALLY. The server never sees the result of that decryption."""
        if username not in self._records:
            raise KeyError(f"No vault found for user '{username}'")
        print(f"[server] sent encrypted vault for '{username}' to client "
              f"(server has no way to read its contents).")
        return self._records[username]

    def prove_cannot_read(self, username: str) -> str:
        """Demonstrate, using only server-side data, that the server cannot
        recover the vault contents.

        Returns a human-readable report string. This deliberately mimics what
        a server operator/attacker with full database access would try:
        1. Grab the raw stored bytes.
        2. Try to decode them as UTF-8 text.
        3. Try to parse that text as JSON (the vault's real plaintext format).
        Both of these must fail for a properly encrypted blob.
        """
        record = self._records[username]
        raw = record.blob.ciphertext
        lines = [
            f"Attempting to read vault for '{username}' using ONLY server-side data...",
            f"  Raw ciphertext (hex, first 32 bytes): {raw[:32].hex()}...",
        ]

        # Attempt 1: naive UTF-8 decode, like reading any plaintext file.
        try:
            raw.decode("utf-8")
            lines.append("  UTF-8 decode:  SUCCEEDED (unexpected!)")
        except UnicodeDecodeError as e:
            lines.append(f"  UTF-8 decode:  FAILED ({e})")

        # Attempt 2: try to parse as JSON, the vault's actual plaintext shape.
        try:
            json.loads(raw)
            lines.append("  JSON parse:    SUCCEEDED (unexpected!)")
        except Exception as e:  # json.JSONDecodeError or the UnicodeDecodeError above
            lines.append(f"  JSON parse:    FAILED ({type(e).__name__}: {e})")

        lines.append(
            "  Conclusion: without the master password (never sent to or "
            "stored by this server), the ciphertext is indistinguishable "
            "from random noise. The server cannot recover the vault."
        )
        return "\n".join(lines)


if __name__ == "__main__":
    from key_derivation import derive_key, generate_salt
    from vault_crypto import encrypt_vault

    salt = generate_salt()
    key = derive_key("correct horse battery staple", salt)
    blob = encrypt_vault(
        [{"site": "example.com", "username": "alice", "password": "hunter2"}], key
    )

    server = ZeroKnowledgeServer()
    server.store_vault("alice", salt, blob)
    print(server.prove_cannot_read("alice"))
