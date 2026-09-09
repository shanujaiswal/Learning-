"""
key_authority.py

The KeyAuthority is the ONLY entity in this entire demo that ever holds the
Paillier private key. In the real world this role is played by, e.g.:

    - a hospital's compliance/audit office collecting an aggregate medical
      statistic from several clinics without any clinic (or the cloud
      analytics vendor in between) ever seeing another clinic's raw values,
    - a payroll auditor computing a total-salary figure across departments
      without seeing any individual employee's salary,
    - a government statistics bureau computing an aggregate census/tax
      figure without any single citizen's data being exposed to the
      compute infrastructure in between.

It generates the Paillier keypair once, hands out the PUBLIC key freely to
every client and to the cloud aggregator, and keeps the PRIVATE key to
itself. Only it can call `decrypt_final_result`.
"""

from __future__ import annotations

from paillier_cryptosystem import (
    PaillierPrivateKey,
    PaillierPublicKey,
    decrypt,
    generate_keypair,
)


class KeyAuthority:
    """Generates and safeguards the Paillier keypair.

    `public_key` is meant to be read and distributed widely.
    `_private_key` is name-mangled/underscored to signal it must never leave
    this object -- nothing else in the codebase ever touches it directly.
    """

    def __init__(self, key_bit_length: int = 512):
        print(f"[KeyAuthority] Generating a {key_bit_length * 2}-bit Paillier keypair "
              f"({key_bit_length}-bit primes p, q)... this takes a few seconds.")
        self.public_key: PaillierPublicKey
        self._private_key: PaillierPrivateKey
        self.public_key, self._private_key = generate_keypair(key_bit_length)
        print("[KeyAuthority] Keypair ready. Public key (n, g) will now be distributed. "
              "Private key (lambda, mu) stays local and is never transmitted.")

    def get_public_key(self) -> PaillierPublicKey:
        """The only thing clients and the cloud are ever given."""
        return self.public_key

    def decrypt_final_result(self, ciphertext: int) -> int:
        """Decrypt the final aggregated ciphertext. This is the ONLY
        decryption call in the entire demo, and it happens exactly once,
        after the cloud has finished its homomorphic aggregation."""
        return decrypt(self._private_key, ciphertext)
