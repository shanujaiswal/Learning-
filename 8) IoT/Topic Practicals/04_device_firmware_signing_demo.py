"""
04 - Device Firmware Signing / Verification Demo (secure OTA concepts)
=========================================================================

Companion practical for:
    Theory/05 IoT Security Fundamentals.md ("Secure boot", "Firmware
    signing", "Unique per-device credentials/certificates")

Concept
-------
The security theory notes describe two related defenses against a
compromised OTA (over-the-air) update channel or physical flash access:

    - **Firmware signing**: the vendor signs every firmware image with
      their PRIVATE key before releasing it. The signature is shipped
      alongside (or appended to) the firmware image.
    - **Secure boot / OTA verification**: the device holds only the
      vendor's PUBLIC key (baked in at manufacture) and verifies every
      firmware image's signature against it before flashing/executing.
      A device that cannot verify a valid signature refuses the image.

This means an attacker who can intercept or tamper with an OTA update in
transit -- or push a malicious image to the update server -- cannot get a
device to accept it, because they don't have the vendor's private key
and any modification to the firmware bytes invalidates the signature.

This script demonstrates the full life cycle with real cryptography
(Ed25519 digital signatures, via the `cryptography` library):

    1. Vendor keypair generation (private key stays with the vendor,
       public key is what ships baked into every device).
    2. "Build" a firmware image (just bytes, standing in for a real
       compiled binary).
    3. Vendor signs the firmware with the PRIVATE key.
    4. Device verifies the signature against the PUBLIC key -- succeeds,
       because the image is genuine and untampered.
    5. An attacker tampers with a single byte of the firmware (simulating
       a MITM on the OTA channel or a compromised update server).
    6. Device verification of the tampered image -- correctly FAILS and
       is rejected, exactly the outcome secure boot/OTA verification is
       designed to guarantee.

Why Ed25519 rather than RSA here: it's a modern elliptic-curve signature
scheme with small keys/signatures (32/64 bytes) and fast verification,
which matters a lot on a constrained microcontroller that has to verify
a signature using very little RAM/CPU -- a realistic choice for real
embedded secure-boot implementations, not just a demo convenience.

Run:
    pip install cryptography
    python 04_device_firmware_signing_demo.py
"""

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives.asymmetric.ed25519 import (
    Ed25519PrivateKey,
    Ed25519PublicKey,
)
from cryptography.hazmat.primitives import serialization
import hashlib


def generate_vendor_keypair():
    """Simulates the vendor's one-time key ceremony.

    The private key never leaves the vendor's signing infrastructure.
    The public key is what gets baked into every device's bootloader at
    manufacture time (this is the "trusted public key" from the theory
    notes) -- distributing it doesn't help an attacker forge signatures.
    """
    private_key = Ed25519PrivateKey.generate()
    public_key = private_key.public_key()
    return private_key, public_key


def build_firmware_image(version="1.0.0"):
    """Stand-in for a real compiled firmware binary.

    In reality this would be an actual flashable binary (bytes read from
    a .bin file); here we just fabricate deterministic-looking bytes so
    the demo has something concrete to sign, hash, and tamper with.
    """
    header = f"FIRMWARE v{version} | build=demo | ".encode()
    # Fabricate a plausible "code" payload.
    payload = bytes((i * 37 + 11) % 256 for i in range(512))
    return header + payload


def sign_firmware(private_key, firmware_bytes):
    """Vendor-side: produce a signature over the firmware image.

    Ed25519 signs the message directly (it has its own internal hashing),
    so no separate digest step is required -- unlike classic RSA
    signature schemes that typically sign a hash of the message.
    """
    return private_key.sign(firmware_bytes)


def verify_firmware(public_key, firmware_bytes, signature):
    """Device-side: verify a firmware image against its signature.

    Returns True if the signature is valid for exactly these bytes under
    the trusted public key, False otherwise. Mirrors the check a real
    secure bootloader performs before it will execute (or even flash) an
    incoming image.
    """
    try:
        public_key.verify(signature, firmware_bytes)
        return True
    except InvalidSignature:
        return False


def sha256_hex(data):
    return hashlib.sha256(data).hexdigest()


def main():
    print("=" * 78)
    print("FIRMWARE SIGNING / SECURE OTA VERIFICATION DEMO (Ed25519)")
    print("=" * 78)

    # -----------------------------------------------------------------
    # Step 1: Vendor key ceremony
    # -----------------------------------------------------------------
    private_key, public_key = generate_vendor_keypair()
    public_bytes = public_key.public_bytes(
        encoding=serialization.Encoding.Raw,
        format=serialization.PublicFormat.Raw,
    )
    print("\n[Vendor] Generated Ed25519 keypair.")
    print(f"[Vendor] Public key (baked into every device at manufacture): "
          f"{public_bytes.hex()}")
    print("[Vendor] Private key stays on vendor signing infrastructure "
          "(never shown/shipped).")

    # -----------------------------------------------------------------
    # Step 2: Build a firmware image
    # -----------------------------------------------------------------
    firmware = build_firmware_image(version="2.4.1")
    print(f"\n[Vendor] Built firmware image: {len(firmware)} bytes, "
          f"sha256={sha256_hex(firmware)[:16]}...")

    # -----------------------------------------------------------------
    # Step 3: Vendor signs the image
    # -----------------------------------------------------------------
    signature = sign_firmware(private_key, firmware)
    print(f"[Vendor] Signed firmware. Signature ({len(signature)} bytes): "
          f"{signature.hex()[:32]}...")
    print("[Vendor] Ships firmware.bin + signature.sig to the OTA update server.")

    # -----------------------------------------------------------------
    # Step 4: Device verifies the genuine, untampered image
    # -----------------------------------------------------------------
    print("\n" + "-" * 78)
    print("SCENARIO A: Device receives the genuine OTA image")
    print("-" * 78)
    ok = verify_firmware(public_key, firmware, signature)
    print(f"[Device] Signature verification: {'PASS' if ok else 'FAIL'}")
    print(f"[Device] {'Proceeding to flash and boot new firmware.' if ok else 'Rejecting update -- refusing to flash.'}")
    assert ok, "Genuine firmware unexpectedly failed verification!"

    # -----------------------------------------------------------------
    # Step 5: Attacker tampers with the firmware in transit
    # -----------------------------------------------------------------
    print("\n" + "-" * 78)
    print("SCENARIO B: Attacker tampers with the firmware "
          "(MITM on OTA channel, or compromised update server)")
    print("-" * 78)
    tampered = bytearray(firmware)
    flip_index = len(tampered) // 2
    original_byte = tampered[flip_index]
    tampered[flip_index] ^= 0xFF  # flip every bit of one byte -- a tiny, targeted change
    tampered = bytes(tampered)

    print(f"[Attacker] Flipped 1 byte at offset {flip_index}: "
          f"0x{original_byte:02x} -> 0x{tampered[flip_index]:02x}")
    print(f"[Attacker] Tampered image sha256={sha256_hex(tampered)[:16]}... "
          f"(original was {sha256_hex(firmware)[:16]}...)")
    print("[Attacker] Ships tampered image alongside the ORIGINAL signature "
          "(attacker has no private key, so cannot produce a valid new one).")

    # -----------------------------------------------------------------
    # Step 6: Device verification of the tampered image must fail
    # -----------------------------------------------------------------
    ok_tampered = verify_firmware(public_key, tampered, signature)
    print(f"\n[Device] Signature verification: {'PASS' if ok_tampered else 'FAIL'}")
    print(f"[Device] {'Proceeding to flash and boot new firmware.' if ok_tampered else 'Rejecting update -- refusing to flash. Device stays on current firmware.'}")
    assert not ok_tampered, "Tampered firmware incorrectly passed verification!"

    # -----------------------------------------------------------------
    # Bonus: even a correctly-signed-but-wrong-image fails (attacker
    # cannot just replay an old valid signature over new malicious bytes)
    # -----------------------------------------------------------------
    print("\n" + "-" * 78)
    print("SCENARIO C: Attacker substitutes an entirely different "
          "(unsigned) malicious image")
    print("-" * 78)
    malicious_firmware = build_firmware_image(version="MALICIOUS-9.9.9")
    ok_malicious = verify_firmware(public_key, malicious_firmware, signature)
    print(f"[Device] Signature verification: {'PASS' if ok_malicious else 'FAIL'}")
    print(f"[Device] {'Proceeding to flash and boot new firmware.' if ok_malicious else 'Rejecting update -- refusing to flash.'}")
    assert not ok_malicious, "Malicious firmware incorrectly passed verification!"

    print("\n" + "=" * 78)
    print("TAKEAWAY")
    print("=" * 78)
    print(
        "The device only trusts the vendor's PUBLIC key baked in at\n"
        "manufacture. Any firmware bytes that don't exactly match what the\n"
        "vendor's PRIVATE key signed -- whether from a single flipped bit or\n"
        "a wholesale malicious substitution -- fail verification and are\n"
        "rejected before ever being flashed or executed. This is what secure\n"
        "boot + signed OTA updates buy you: an attacker who compromises the\n"
        "update server or the network path in transit still cannot get\n"
        "malicious code to run on the device, per Theory/05 IoT Security\n"
        "Fundamentals.md."
    )


if __name__ == "__main__":
    main()
