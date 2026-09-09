"""
audit_chain.py

Builds and appends AuditBlocks into a hash-linked, digitally-signed chain --
the tamper-evident audit log itself.

Invariants maintained by append_event() for every block it creates:
    1. block.previous_hash == the actual block_hash of the current last block
       (or "0" * 64 for the genesis block).
    2. block.block_hash == block.compute_hash() at creation time.
    3. block.signature is a valid Ed25519 signature (via the SigningAuthority)
       over block.block_hash.

These three invariants are exactly what chain_verifier.py re-checks for
every block, every time the chain is verified.
"""

from __future__ import annotations

from datetime import datetime, timezone
from typing import Any, Dict, List

from audit_block import AuditBlock
from signing_authority import SigningAuthority

GENESIS_PREVIOUS_HASH = "0" * 64


class AuditChain:
    """An append-only, hash-linked, signed chain of audit log entries."""

    def __init__(self, signing_authority: SigningAuthority) -> None:
        self.signing_authority = signing_authority
        self.blocks: List[AuditBlock] = []

    def _create_genesis_block(self) -> AuditBlock:
        genesis_data = {"event": "CHAIN_GENESIS", "note": "Audit log initialized"}
        return self._build_block(index=0, data=genesis_data, previous_hash=GENESIS_PREVIOUS_HASH)

    def _build_block(self, index: int, data: Dict[str, Any], previous_hash: str) -> AuditBlock:
        timestamp = datetime.now(timezone.utc).isoformat()
        block = AuditBlock(
            index=index,
            timestamp=timestamp,
            data=data,
            previous_hash=previous_hash,
        )
        block.block_hash = block.compute_hash()
        block.signature = self.signing_authority.sign(block.block_hash)
        return block

    def append_event(self, data: Dict[str, Any]) -> AuditBlock:
        """
        Append a new audit event to the chain.

        Automatically:
          - assigns the next index,
          - links previous_hash to the current last block's hash
            (creating the genesis block first if the chain is empty),
          - computes this block's own hash,
          - signs that hash with the logging service's private key.
        """
        if not self.blocks:
            genesis = self._create_genesis_block()
            self.blocks.append(genesis)

        previous_block = self.blocks[-1]
        new_block = self._build_block(
            index=previous_block.index + 1,
            data=data,
            previous_hash=previous_block.block_hash,
        )
        self.blocks.append(new_block)
        return new_block

    def __len__(self) -> int:
        return len(self.blocks)

    def __iter__(self):
        return iter(self.blocks)
