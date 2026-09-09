"""
05_diffie_hellman_key_exchange.py
------------------------------------
Demonstrates the "TLS / Diffie-Hellman" chapter with a REAL two-party
Elliptic-Curve Diffie-Hellman (ECDH) key exchange, simulated within a
single script by creating two independent key pairs ("Alice" and "Bob").

The core idea of Diffie-Hellman: two parties can each generate their own
private/public key pair, exchange ONLY the public keys over an
untrusted channel, and each independently combine their own private key
with the other party's public key to arrive at the SAME shared secret --
without ever transmitting that secret itself. This is what makes modern
TLS handshakes able to establish a secure session key even if an
eavesdropper sees every message exchanged.

We use ECDH on curve SECP384R1 (the elliptic-curve variant of classic
Diffie-Hellman -- faster and equally secure at much smaller key sizes
than classic finite-field DH). The raw shared secret is then run through
HKDF to derive a uniform, fixed-length symmetric key suitable for use
with AES-GCM (see script 02) -- this final derivation step is standard
practice and is exactly what TLS 1.3 does after its DH key exchange.

Install:
    pip install cryptography
"""

from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.kdf.hkdf import HKDF

CURVE = ec.SECP384R1()


def generate_ecdh_keypair():
    private_key = ec.generate_private_key(CURVE)
    public_key = private_key.public_key()
    return private_key, public_key


def derive_shared_key(private_key, peer_public_key, *, info: bytes = b"handshake data") -> bytes:
    """Combine our private key with the peer's public key to get the raw
    ECDH shared secret, then run it through HKDF-SHA256 to derive a
    32-byte symmetric key. Raw DH output should never be used directly
    as a key -- HKDF whitens it into a uniformly random key.
    """
    shared_secret = private_key.exchange(ec.ECDH(), peer_public_key)
    return HKDF(
        algorithm=hashes.SHA256(),
        length=32,
        salt=None,
        info=info,
    ).derive(shared_secret)


def main():
    print("=" * 70)
    print("ECDH KEY EXCHANGE (curve: SECP384R1)")
    print("=" * 70)

    # --- Alice's side ---
    alice_private, alice_public = generate_ecdh_keypair()
    print("Alice generated her ECDH key pair.")

    # --- Bob's side ---
    bob_private, bob_public = generate_ecdh_keypair()
    print("Bob generated his ECDH key pair.")

    print("\nAlice and Bob exchange ONLY their public keys over the (possibly")
    print("eavesdropped) network. Private keys never leave each party.\n")

    # --- Each side independently derives the shared secret ---
    alice_shared_key = derive_shared_key(alice_private, bob_public)
    bob_shared_key = derive_shared_key(bob_private, alice_public)

    print(f"Alice's derived shared key: {alice_shared_key.hex()}")
    print(f"Bob's derived shared key  : {bob_shared_key.hex()}")

    match = alice_shared_key == bob_shared_key
    print(f"\nKeys match: {match}")
    assert match, "Diffie-Hellman key exchange FAILED -- keys do not match!"
    print("SUCCESS: Alice and Bob independently derived the identical shared")
    print("secret without ever transmitting it -- this shared key can now be")
    print("used directly as an AES-256-GCM key (see script 02).")

    # --- Sanity check: an eavesdropper who only sees public keys cannot
    #     derive the same secret without a private key. ---
    print("\n" + "=" * 70)
    print("EAVESDROPPER CHECK")
    print("=" * 70)
    eve_private, eve_public = generate_ecdh_keypair()
    eve_guess = derive_shared_key(eve_private, bob_public)
    print(f"Eve (attacker) tries with her own key pair and Bob's public key:")
    print(f"Eve's derived key: {eve_guess.hex()}")
    print(f"Eve's key matches Alice/Bob's shared key: {eve_guess == alice_shared_key} "
          "(expected False)")
    assert eve_guess != alice_shared_key


if __name__ == "__main__":
    main()
