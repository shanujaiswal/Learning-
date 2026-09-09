"""
04_rsa_signatures_and_encryption.py
--------------------------------------
Demonstrates the "RSA / Digital Signatures" chapter using the
`cryptography` library. Covers two distinct RSA use cases that are
often confused:

    1. DIGITAL SIGNATURES (authenticity + integrity, not secrecy)
       - Sign a message with the PRIVATE key.
       - Anyone with the PUBLIC key can verify the signature.
       - Tampering with the message after signing makes verification
         fail -- demonstrated explicitly below.
       - Uses RSASSA-PSS with SHA-256 (the modern recommended padding
         scheme for RSA signatures).

    2. ENCRYPTION (secrecy)
       - Encrypt a short message with the PUBLIC key.
       - Only the holder of the PRIVATE key can decrypt it.
       - Uses RSA-OAEP with SHA-256 (the modern recommended padding
         scheme for RSA encryption -- NEVER use raw/textbook RSA or the
         legacy PKCS#1 v1.5 encryption padding in new code).

Note: RSA encryption can only handle messages smaller than the key size
minus padding overhead (~190 bytes for a 2048-bit key with OAEP-SHA256).
In real systems RSA is used to encrypt a random AES key ("key
encapsulation"), and AES then encrypts the actual bulk data -- see
script 02 for the AES side of that pattern.

Install:
    pip install cryptography
"""

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa


def generate_keypair(key_size: int = 2048):
    private_key = rsa.generate_private_key(public_exponent=65537, key_size=key_size)
    public_key = private_key.public_key()
    return private_key, public_key


# ---------------------------------------------------------------------------
# Digital signatures
# ---------------------------------------------------------------------------

def sign_message(private_key, message: bytes) -> bytes:
    return private_key.sign(
        message,
        padding.PSS(mgf=padding.MGF1(hashes.SHA256()), salt_length=padding.PSS.MAX_LENGTH),
        hashes.SHA256(),
    )


def verify_signature(public_key, message: bytes, signature: bytes) -> bool:
    try:
        public_key.verify(
            signature,
            message,
            padding.PSS(mgf=padding.MGF1(hashes.SHA256()), salt_length=padding.PSS.MAX_LENGTH),
            hashes.SHA256(),
        )
        return True
    except InvalidSignature:
        return False


def demo_signatures(private_key, public_key):
    print("=" * 70)
    print("RSA DIGITAL SIGNATURES (RSASSA-PSS / SHA-256)")
    print("=" * 70)

    message = b"Transfer $100 from Alice to Bob. Authorized by Alice."
    signature = sign_message(private_key, message)
    print(f"Message  : {message.decode()}")
    print(f"Signature: {signature.hex()[:64]}... ({len(signature)} bytes)")

    ok = verify_signature(public_key, message, signature)
    print(f"\nVerify(original message)  -> {ok} (expected True)")
    assert ok is True

    tampered_message = b"Transfer $100 from Alice to Bob. Authorized by Alice!"
    tampered_ok = verify_signature(public_key, tampered_message, signature)
    print(f"Verify(tampered message)  -> {tampered_ok} (expected False)")
    assert tampered_ok is False

    print("\nRound-trip OK: valid signature verifies; tampered message is rejected.\n")


# ---------------------------------------------------------------------------
# Encryption (RSA-OAEP)
# ---------------------------------------------------------------------------

def rsa_oaep_encrypt(public_key, plaintext: bytes) -> bytes:
    return public_key.encrypt(
        plaintext,
        padding.OAEP(mgf=padding.MGF1(algorithm=hashes.SHA256()),
                     algorithm=hashes.SHA256(), label=None),
    )


def rsa_oaep_decrypt(private_key, ciphertext: bytes) -> bytes:
    return private_key.decrypt(
        ciphertext,
        padding.OAEP(mgf=padding.MGF1(algorithm=hashes.SHA256()),
                     algorithm=hashes.SHA256(), label=None),
    )


def demo_encryption(private_key, public_key):
    print("=" * 70)
    print("RSA ENCRYPTION (RSA-OAEP / SHA-256)")
    print("=" * 70)

    plaintext = b"The AES session key: 9f1c...(short secret payload)"
    ciphertext = rsa_oaep_encrypt(public_key, plaintext)
    print(f"Plaintext : {plaintext.decode()}")
    print(f"Ciphertext: {ciphertext.hex()[:64]}... ({len(ciphertext)} bytes)")

    recovered = rsa_oaep_decrypt(private_key, ciphertext)
    print(f"Decrypted : {recovered.decode()}")
    assert recovered == plaintext, "RSA-OAEP round-trip FAILED"
    print("\nRound-trip OK: decrypted plaintext matches the original.")


def main():
    private_key, public_key = generate_keypair()

    pub_pem = public_key.public_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PublicFormat.SubjectPublicKeyInfo,
    )
    print("Generated 2048-bit RSA keypair. Public key (PEM):")
    print(pub_pem.decode())

    demo_signatures(private_key, public_key)
    demo_encryption(private_key, public_key)


if __name__ == "__main__":
    main()
