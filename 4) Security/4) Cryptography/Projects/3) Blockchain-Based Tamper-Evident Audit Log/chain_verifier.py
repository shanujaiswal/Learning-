"""
chain_verifier.py

Independently walks an AuditChain end-to-end and checks, for every block:

    1. HASH INTEGRITY  -- does a fresh recompute of the block's own contents
                           (compute_hash()) match the hash stored in the block
                           at creation time? If not, the block's data was
                           mutated after the fact.

    2. LINK INTEGRITY   -- does this block's previous_hash actually equal the
                           prior block's REAL (recomputed) hash? Because block
                           N+1's previous_hash was fixed at creation time to
                           block N's hash-at-that-time, ANY change to block N's
                           stored hash (detected by check 1, or propagating
                           from a still-earlier tampered block) breaks this
                           link for block N+1 and, transitively, for every
                           subsequent block -- the hash-chain property that
                           gives the whole log its tamper-evidence.

    3. SIGNATURE INTEGRITY -- does the block's signature validly verify,
                           under the signing authority's public key, against
                           the block's *currently stored* block_hash? An
                           attacker who edits data but leaves the old
                           signature in place will fail this check too,
                           since the signature was only ever valid for the
                           original hash.

The verifier reports the FIRST block where anything is wrong, and lists
every later block whose link is consequently invalid (because their
previous_hash no longer matches reality).
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import List

from audit_chain import AuditChain, GENESIS_PREVIOUS_HASH
from signing_authority import verify_signature


@dataclass
class BlockCheck:
    index: int
    hash_ok: bool          # stored block_hash == fresh compute_hash()
    link_ok: bool          # stored previous_hash == prior block's real hash
    signature_ok: bool     # signature verifies against stored block_hash

    @property
    def ok(self) -> bool:
        return self.hash_ok and self.link_ok and self.signature_ok


@dataclass
class VerificationReport:
    checks: List[BlockCheck]
    chain_length: int

    @property
    def is_valid(self) -> bool:
        return all(c.ok for c in self.checks)

    @property
    def first_broken_index(self) -> int | None:
        for c in self.checks:
            if not c.ok:
                return c.index
        return None

    @property
    def broken_indices(self) -> List[int]:
        return [c.index for c in self.checks if not c.ok]

    def summary(self) -> str:
        if self.is_valid:
            return f"VALID -- all {self.chain_length} blocks passed hash, link, and signature checks."

        first = self.first_broken_index
        downstream = [i for i in self.broken_indices if i != first]
        lines = [
            f"TAMPERING DETECTED -- chain is INVALID.",
            f"  First broken block : #{first}",
            f"  Downstream blocks whose hash-link is now invalid: "
            f"{downstream if downstream else '(none)'}",
            f"  Total invalid blocks: {len(self.broken_indices)} of {self.chain_length}",
        ]
        return "\n".join(lines)


def verify_chain(chain: AuditChain) -> VerificationReport:
    """
    Walk the entire chain and produce a VerificationReport.

    Each block is checked independently against reality (its own recomputed
    hash, the prior block's real recomputed hash, and its own stored
    signature) -- there is no "trust" carried over from a previous block's
    check, which is precisely why a single historical tamper is visible at
    every downstream block, not just the one that was edited.
    """
    checks: List[BlockCheck] = []
    previous_real_hash = GENESIS_PREVIOUS_HASH

    for block in chain:
        fresh_hash = block.compute_hash()
        hash_ok = fresh_hash == block.block_hash

        link_ok = block.previous_hash == previous_real_hash

        signature_ok = verify_signature(
            chain.signing_authority.public_key, block.block_hash, block.signature
        )

        checks.append(
            BlockCheck(
                index=block.index,
                hash_ok=hash_ok,
                link_ok=link_ok,
                signature_ok=signature_ok,
            )
        )

        # The "real" hash that the NEXT block's previous_hash must match is
        # whatever this block's contents actually hash to right now -- not
        # necessarily the (possibly tampered/stale) stored block_hash. This
        # is what makes a single historical edit break every later link.
        previous_real_hash = fresh_hash

    return VerificationReport(checks=checks, chain_length=len(checks))
