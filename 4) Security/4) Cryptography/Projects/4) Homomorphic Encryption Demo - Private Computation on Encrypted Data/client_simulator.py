"""
client_simulator.py

Simulates several independent clients (e.g. hospitals reporting a patient
reading, or departments reporting a payroll total). Each client:

    1. Holds ONE private integer that it never shows to anyone else --
       not other clients, not the cloud, not even the key authority.
    2. Receives only the PUBLIC key from the KeyAuthority.
    3. Encrypts its own private number locally, and hands only the
       resulting ciphertext to the cloud aggregator.

Because Paillier encryption is randomized (a fresh random blinding factor r
is used every time), two clients holding the same secret value would still
produce different-looking ciphertexts -- the cloud can't even tell which
clients' values match.
"""

from __future__ import annotations

from paillier_cryptosystem import PaillierPublicKey, encrypt


class Client:
    """A single participant contributing one private numeric value."""

    def __init__(self, name: str, private_value: int, public_key: PaillierPublicKey):
        self.name = name
        self._private_value = private_value  # never exposed outside this object
        self._public_key = public_key

    def encrypt_private_value(self) -> int:
        """Encrypt this client's secret value under the shared public key.

        Returns only the ciphertext -- the plaintext `_private_value` never
        leaves this method's closure over `self`.
        """
        ciphertext = encrypt(self._public_key, self._private_value)
        print(f"[{self.name}] Encrypted my private value locally. "
              f"Only the ciphertext leaves my machine.")
        return ciphertext

    def reveal_for_verification_only(self) -> int:
        """Used ONLY by main.py at the very end, purely so the demo can
        compute the 'ground truth' real sum to compare against the
        decrypted homomorphic result. In a real deployment this method
        would not exist -- the whole point is that nobody ever needs to
        call it."""
        return self._private_value


def build_clients(public_key: PaillierPublicKey, values: dict[str, int]) -> list[Client]:
    """Convenience factory: build one Client per (name, private_value) pair."""
    return [Client(name, value, public_key) for name, value in values.items()]
