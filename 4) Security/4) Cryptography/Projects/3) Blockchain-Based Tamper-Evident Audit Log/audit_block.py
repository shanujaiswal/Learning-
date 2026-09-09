"""
audit_block.py

Defines the AuditBlock -- the fundamental unit of the tamper-evident audit log.

Each block is conceptually identical to a block in a simplified blockchain:
    - it carries a payload (the audit event data),
    - it links to the previous block via a cryptographic hash (the "chain"),
    - it commits to its own contents via a SHA-256 hash,
    - it is digitally signed by the logging service's private key so that
      even someone with direct write access to the storage (a raw JSON file,
      a database row, etc.) cannot forge a *new* valid block without the key.

Altering any field of a block after the fact (index, timestamp, data, or
previous_hash) changes what compute_hash() returns, which will no longer
match the hash stored at creation time -- that mismatch is precisely what
chain_verifier.py detects.
"""

from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass, field
from typing import Any, Dict, Optional


@dataclass
class AuditBlock:
    """A single tamper-evident audit log entry ("block")."""

    index: int
    timestamp: str
    data: Dict[str, Any]                # the audit event payload, e.g. {"action": "WITHDRAWAL", ...}
    previous_hash: str                  # hash of the previous block (genesis uses "0" * 64)
    block_hash: str = field(default="") # SHA-256 hash committed at creation time
    signature: Optional[bytes] = None   # Ed25519 signature over block_hash, set at creation time

    def compute_hash(self) -> str:
        """
        Recompute the SHA-256 hash of this block's contents from scratch.

        This is deliberately independent of self.block_hash -- it is the
        "fresh recompute" the verifier compares against the stored hash to
        detect tampering. It hashes index + timestamp + data + previous_hash,
        using a canonical JSON encoding (sorted keys, no whitespace ambiguity)
        so the hash is deterministic regardless of dict insertion order.
        """
        payload = {
            "index": self.index,
            "timestamp": self.timestamp,
            "data": self.data,
            "previous_hash": self.previous_hash,
        }
        canonical = json.dumps(payload, sort_keys=True, separators=(",", ":"))
        return hashlib.sha256(canonical.encode("utf-8")).hexdigest()

    def to_dict(self) -> Dict[str, Any]:
        """Serialize the block, including its signature, for storage/printing."""
        return {
            "index": self.index,
            "timestamp": self.timestamp,
            "data": self.data,
            "previous_hash": self.previous_hash,
            "block_hash": self.block_hash,
            "signature": self.signature.hex() if self.signature else None,
        }
