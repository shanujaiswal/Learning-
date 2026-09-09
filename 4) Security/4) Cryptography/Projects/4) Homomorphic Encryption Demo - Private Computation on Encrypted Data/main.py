"""
main.py

Runs the full story end-to-end:

    1. KeyAuthority generates a Paillier keypair and publishes the public key.
    2. Several Clients each encrypt their own private numeric value locally,
       using only the public key, and hand the ciphertext to the cloud.
    3. CloudAggregator (untrusted) homomorphically sums all ciphertexts
       WITHOUT ever decrypting anything, and is shown failing to snoop.
    4. KeyAuthority (the only private-key holder) decrypts the single
       aggregated ciphertext to reveal ONLY the final sum.
    5. We assert that the decrypted homomorphic sum exactly equals the real
       sum of the original plaintext values -- proving correctness, not
       just printing something that looks plausible.

Scenario used here: several clinics each report one private patient
measurement (e.g. a lab reading in mg/dL); a cloud analytics service
aggregates the total across all clinics without ever seeing an individual
patient's number; a compliance auditor (KeyAuthority) is the only one who
ever decrypts, and only the FINAL aggregate.
"""

from __future__ import annotations

from client_simulator import build_clients
from cloud_aggregator import CloudAggregator
from key_authority import KeyAuthority


def main() -> None:
    print("=" * 78)
    print("HOMOMORPHIC ENCRYPTION DEMO -- Paillier Cryptosystem")
    print("Scenario: cloud-aggregated clinical readings, computed without ever")
    print("decrypting any individual client's private value.")
    print("=" * 78)

    # ------------------------------------------------------------------
    # Step 1: Key authority generates the keypair and publishes only the
    # public half.
    # ------------------------------------------------------------------
    print("\n--- Step 1: Key Authority sets up the Paillier keypair ---")
    authority = KeyAuthority(key_bit_length=512)
    public_key = authority.get_public_key()

    # ------------------------------------------------------------------
    # Step 2: Several clients each hold one private number. Nobody but the
    # client itself ever sees its own plaintext value.
    # ------------------------------------------------------------------
    print("\n--- Step 2: Clients encrypt their own private values locally ---")
    private_readings = {
        "Clinic-A": 118,   # e.g. a private lab reading, mg/dL
        "Clinic-B": 95,
        "Clinic-C": 142,
        "Clinic-D": 87,
        "Clinic-E": 133,
    }
    clients = build_clients(public_key, private_readings)

    # ------------------------------------------------------------------
    # Step 3: The (untrusted) cloud receives ONLY ciphertexts.
    # ------------------------------------------------------------------
    print("\n--- Step 3: Cloud aggregator receives ciphertexts (never plaintext) ---")
    cloud = CloudAggregator(public_key)
    for client in clients:
        ciphertext = client.encrypt_private_value()
        cloud.receive_ciphertext(client.name, ciphertext)

    # ------------------------------------------------------------------
    # Step 4: The cloud tries to snoop on what it's holding, and fails.
    # ------------------------------------------------------------------
    cloud.attempt_to_snoop()

    # ------------------------------------------------------------------
    # Step 5: The cloud homomorphically sums all ciphertexts -- still no
    # decryption anywhere in this step.
    # ------------------------------------------------------------------
    print("--- Step 4: Cloud homomorphically aggregates ciphertexts into one ---")
    aggregated_ciphertext = cloud.homomorphically_sum()
    print(f"[CloudAggregator] Final aggregated ciphertext (first 40 digits of "
          f"{len(str(aggregated_ciphertext))}): {str(aggregated_ciphertext)[:40]}...")

    # ------------------------------------------------------------------
    # Step 6: ONLY the key authority decrypts, and only the final sum --
    # never any individual client's ciphertext.
    # ------------------------------------------------------------------
    print("\n--- Step 5: Key Authority decrypts ONLY the final aggregated result ---")
    decrypted_sum = authority.decrypt_final_result(aggregated_ciphertext)
    print(f"[KeyAuthority] Decrypted aggregate sum = {decrypted_sum}")

    # ------------------------------------------------------------------
    # Step 7: Verification -- prove (via assertion, not just printing) that
    # the homomorphically-computed, then decrypted, sum exactly matches the
    # real sum of the original plaintext values.
    # ------------------------------------------------------------------
    print("\n--- Step 6: Verification ---")
    real_sum = sum(client.reveal_for_verification_only() for client in clients)
    print(f"Real sum of original private values (known here only for verification "
          f"purposes; the cloud never had access to this): {real_sum}")
    print(f"Decrypted result of homomorphic aggregation:                          "
          f"{decrypted_sum}")

    assert decrypted_sum == real_sum, (
        f"MISMATCH: homomorphic result {decrypted_sum} != real sum {real_sum}"
    )
    print("\n*** VERIFIED: decrypted homomorphic sum EXACTLY matches the real "
          "plaintext sum. ***")

    # ------------------------------------------------------------------
    # Step 8: Prove the cloud's stored ciphertexts never equal any client's
    # real plaintext value (further confirming no leakage happened).
    # ------------------------------------------------------------------
    print("\n--- Step 7: Confirming the cloud's stored ciphertexts reveal nothing ---")
    raw = dict(cloud.get_all_raw_ciphertexts())
    for name, real_value in private_readings.items():
        ciphertext_value = raw[name]
        looks_like_plaintext = ciphertext_value == real_value
        print(f"  {name}: real value = {real_value:>4} | ciphertext (as int) has "
              f"{len(str(ciphertext_value))} digits | ciphertext == real value? "
              f"{looks_like_plaintext}")
        assert not looks_like_plaintext

    print("\nAll assertions passed. The cloud performed a real computation on "
          "real ciphertexts and never saw a single plaintext value, yet the "
          "final decrypted answer is exactly correct.")


if __name__ == "__main__":
    main()
