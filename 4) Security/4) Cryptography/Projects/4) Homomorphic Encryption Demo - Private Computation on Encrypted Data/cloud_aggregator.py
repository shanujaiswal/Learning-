"""
cloud_aggregator.py

The CloudAggregator plays the role of an untrusted (or "honest-but-curious")
cloud analytics service -- e.g. AWS/Azure/GCP hosting a privacy-preserving
statistics pipeline for a hospital consortium or a payroll analytics vendor.

It:
    - receives ONLY ciphertexts from clients (never plaintext),
    - homomorphically combines them into a single ciphertext representing
      the SUM of all clients' secret values, by repeated modular
      multiplication (Paillier's additive homomorphism) -- it never calls
      decrypt() because it never has the private key,
    - is handed only the PUBLIC key, which is mathematically insufficient
      to recover any plaintext (that's the entire point of public-key
      cryptography's hard problem here -- the composite residuosity
      assumption),
    - is explicitly shown, in `attempt_to_snoop`, trying every trick it
      actually has access to (looking at raw ciphertext ints, trying to
      "decrypt" with only n and g, brute-forcing against the public key
      alone) and getting nothing useful.
"""

from __future__ import annotations

from paillier_cryptosystem import PaillierPublicKey, homomorphic_add


class CloudAggregator:
    """An untrusted compute node that only ever sees ciphertexts + the
    public key -- never plaintext, never the private key."""

    def __init__(self, public_key: PaillierPublicKey):
        self.public_key = public_key
        self._received_ciphertexts: list[tuple[str, int]] = []

    def receive_ciphertext(self, client_name: str, ciphertext: int) -> None:
        """Accept a ciphertext from a client. This is ALL the cloud ever
        gets from any client -- a single large integer that looks like
        random noise."""
        self._received_ciphertexts.append((client_name, ciphertext))
        print(f"[CloudAggregator] Received an opaque ciphertext from {client_name}: "
              f"{str(ciphertext)[:40]}... (showing first 40 digits of "
              f"{len(str(ciphertext))} total)")

    def homomorphically_sum(self) -> int:
        """Combine every received ciphertext into ONE ciphertext that
        decrypts to the sum of all the original plaintexts -- using only
        modular multiplication mod n^2. No decryption key is used or
        needed at any point in this method."""
        if not self._received_ciphertexts:
            raise ValueError("No ciphertexts received yet.")

        names = [name for name, _ in self._received_ciphertexts]
        print(f"[CloudAggregator] Homomorphically combining ciphertexts from: "
              f"{', '.join(names)} -- via ciphertext multiplication mod n^2. "
              f"No plaintext is ever seen or needed.")

        running_total_ciphertext = self._received_ciphertexts[0][1]
        for _, ciphertext in self._received_ciphertexts[1:]:
            running_total_ciphertext = homomorphic_add(
                self.public_key, running_total_ciphertext, ciphertext
            )
        return running_total_ciphertext

    def attempt_to_snoop(self) -> None:
        """Demonstrate, honestly, everything the cloud COULD try with what
        it actually has (public key + ciphertexts) -- and that none of it
        recovers a plaintext. This is the "server is shown to be unable to
        recover any individual plaintext" requirement, made concrete.
        """
        print("\n[CloudAggregator] --- Attempting to snoop on client data ---")
        print("[CloudAggregator] I only hold: the public key (n, g) and a pile "
              "of ciphertext integers. I do NOT hold: p, q, lambda, or mu.")

        n = self.public_key.n
        for name, ciphertext in self._received_ciphertexts:
            # Attempt 1: naive "maybe it's just a big number, try mod n?"
            naive_guess = ciphertext % n
            print(f"[CloudAggregator]   Trying naive ciphertext-mod-n on {name}'s "
                  f"data -> {str(naive_guess)[:20]}... "
                  f"(meaningless: this is NOT the plaintext, just noise shaped by r^n)")

            # Attempt 2: try to brute-force small plaintexts by re-encrypting
            # guesses and matching (this is the only "attack" available without
            # the private key: an exhaustive/discrete-log style search).
            # We only try a tiny handful of guesses to prove the point --
            # even this toy brute force can never succeed because Paillier
            # encryption is randomized: re-encrypting the SAME guess value
            # produces a DIFFERENT ciphertext every time (different random
            # blinding factor r), so direct ciphertext matching is structurally
            # impossible even for a guess that happens to be correct.
            from paillier_cryptosystem import encrypt as _encrypt
            trial_guess = 42
            trial_ciphertext = _encrypt(self.public_key, trial_guess)
            matches = trial_ciphertext == ciphertext
            print(f"[CloudAggregator]   Re-encrypting a guess (e.g. {trial_guess}) and "
                  f"comparing to {name}'s ciphertext -> match={matches} "
                  f"(randomized encryption means even a correct guess wouldn't match "
                  f"bit-for-bit; brute force here has zero structural chance of working)")

        print("[CloudAggregator] Conclusion: without lambda and mu (the private key, "
              "held only by the KeyAuthority), and without factoring n = p * q "
              "(computationally infeasible at this key size), the cloud cannot "
              "recover ANY individual client's plaintext value.")
        print("[CloudAggregator] --- End snooping attempt: 0 plaintexts recovered ---\n")

    def get_all_raw_ciphertexts(self) -> list[tuple[str, int]]:
        """Expose exactly what the cloud has stored, for outside inspection
        (used by main.py to prove none of these values equal the real
        plaintexts)."""
        return list(self._received_ciphertexts)
