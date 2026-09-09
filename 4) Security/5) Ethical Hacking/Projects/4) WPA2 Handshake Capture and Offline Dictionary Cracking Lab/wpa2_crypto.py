"""
wpa2_crypto.py
==============
REAL WPA2-PSK (802.11i) key-derivation primitives, implemented with the Python
standard library only (hashlib / hmac). Nothing here is a toy substitute --
these are the actual documented algorithms used by real WPA2 networks and by
real cracking tools (aircrack-ng, hashcat mode 22000, cowpatty, etc).

Chain of derivation (per IEEE 802.11i):

    passphrase + SSID  --[PBKDF2-HMAC-SHA1, 4096 iters, 256 bits]-->  PMK
    PMK + ANonce + SNonce + AP-MAC + STA-MAC  --[PRF-512 / HMAC-SHA1]-->  PTK
    PTK's first 16 bytes (KCK) + EAPOL frame  --[HMAC-SHA1, truncated]-->  MIC

Everything downstream of the passphrase is fully deterministic. That
determinism is exactly what makes an OFFLINE dictionary attack possible once
a handshake has been captured: no further contact with the AP is required to
test a candidate passphrase.

LAB NOTE: this project is a fully simulated, self-contained lab. No real
wireless hardware, monitor mode, or over-the-air capture/injection happens
anywhere in this codebase -- only the deterministic math a real capture
would let an auditor run offline.
"""

from __future__ import annotations

import hashlib
import hmac
from dataclasses import dataclass


# ---------------------------------------------------------------------------
# Step 1: PSK -> PMK  (Pairwise Master Key)
# ---------------------------------------------------------------------------
# Per IEEE 802.11i / RFC 2898 (PBKDF2), the WPA2-Personal PMK is:
#
#     PMK = PBKDF2-HMAC-SHA1(passphrase, SSID, iterations=4096, dklen=256 bits)
#
# The SSID is used as the PBKDF2 *salt*. This is the well-known reason the
# same passphrase produces a completely different PMK on a differently-named
# network -- the SSID "salts" the derivation. Real tools (aircrack-ng,
# hashcat -m 2500/22000) implement exactly this call.

PMK_ITERATIONS = 4096
PMK_LENGTH_BYTES = 32  # 256 bits


def derive_pmk(passphrase: str, ssid: str) -> bytes:
    """Derive the Pairwise Master Key from a passphrase and SSID.

    This is the *real* WPA2-Personal key-derivation function, straight out
    of stdlib hashlib.pbkdf2_hmac -- no simplification here.
    """
    if not (8 <= len(passphrase) <= 63):
        # The WPA2 spec restricts ASCII passphrases to 8-63 characters.
        raise ValueError("WPA2 passphrases must be 8-63 characters long")
    return hashlib.pbkdf2_hmac(
        "sha1",
        passphrase.encode("utf-8"),
        ssid.encode("utf-8"),
        PMK_ITERATIONS,
        dklen=PMK_LENGTH_BYTES,
    )


# ---------------------------------------------------------------------------
# Step 2: PMK -> PTK  (Pairwise Transient Key), via the 802.11i PRF
# ---------------------------------------------------------------------------
# The 802.11i "PRF" (pseudo-random function) family is built directly on
# HMAC-SHA1, expanded to an arbitrary output length by iterating a counter:
#
#     PRF(K, A, B, Len):
#         R = b""
#         for i in range(0, ceil(Len / 160)):
#             R += HMAC-SHA1(K, A || 0x00 || B || i)
#         return R[:Len bytes]
#
# For the pairwise key expansion specifically, the spec fixes:
#     A = "Pairwise key expansion"
#     B = Min(AA, SA) || Max(AA, SA) || Min(ANonce, SNonce) || Max(ANonce, SNonce)
#         (AA/SA = AP and STA MAC addresses; ordering by min/max so both
#          sides compute an identical B regardless of who is "AP" or "STA")
#
# PTK length is 512 bits (64 bytes) for a TKIP pairwise key (384 bits / 48
# bytes for pure CCMP/AES); we use 512 bits here, matching the classic
# reference derivation used by most teaching material and by aircrack-ng's
# own PTK-calculation code path. The first 16 bytes of the PTK are the KCK
# (Key Confirmation Key), which is what actually produces the MIC in
# messages 2 and 3 of the handshake -- that's the only slice this lab uses.

PAIRWISE_KEY_LABEL = b"Pairwise key expansion"
PTK_LENGTH_BYTES = 64  # 512 bits
KCK_LENGTH_BYTES = 16  # first 16 bytes of the PTK


def _prf(key: bytes, label: bytes, data: bytes, length_bytes: int) -> bytes:
    """The 802.11i PRF: HMAC-SHA1 iterated over a counter to expand output."""
    result = b""
    counter = 0
    while len(result) < length_bytes:
        block_input = label + b"\x00" + data + bytes([counter])
        result += hmac.new(key, block_input, hashlib.sha1).digest()
        counter += 1
    return result[:length_bytes]


def derive_ptk(
    pmk: bytes,
    ap_mac: bytes,
    client_mac: bytes,
    anonce: bytes,
    snonce: bytes,
) -> bytes:
    """Derive the Pairwise Transient Key from the PMK + handshake nonces/MACs.

    Both the AP and the client run this exact same computation independently
    during a real handshake -- that's how they arrive at a shared session
    key without ever transmitting it.
    """
    min_mac, max_mac = (ap_mac, client_mac) if ap_mac < client_mac else (client_mac, ap_mac)
    min_nonce, max_nonce = (anonce, snonce) if anonce < snonce else (snonce, anonce)
    b = min_mac + max_mac + min_nonce + max_nonce
    return _prf(pmk, PAIRWISE_KEY_LABEL, b, PTK_LENGTH_BYTES)


def extract_kck(ptk: bytes) -> bytes:
    """The Key Confirmation Key is the first 16 bytes of the PTK."""
    return ptk[:KCK_LENGTH_BYTES]


# ---------------------------------------------------------------------------
# Step 3: MIC (Message Integrity Code) over a simulated EAPOL frame
# ---------------------------------------------------------------------------
# In a real handshake, message 2 (client -> AP) carries an EAPOL-Key frame
# with the MIC field zeroed out, a MIC is computed over those bytes using
# the KCK, and the result is written into the MIC field before transmission.
# HMAC-MD5 is used for the older "WPA" cipher suite; HMAC-SHA1 (truncated to
# 16 bytes) is used for WPA2/AES-CCMP -- we implement the WPA2 case.
#
# This lab does not encode a byte-perfect real EAPOL frame (802.1X framing,
# key info bitfields, RSN IE, etc are out of scope for a crypto lab) -- it
# builds a *representative* EAPOL-Key payload containing exactly the fields
# a real frame's MIC would be computed over (SSID context, both MACs, both
# nonces, a replay counter), with the MIC field zeroed, matching the *real*
# "zero-then-HMAC" construction used by the actual protocol.


def build_eapol_frame(
    ssid: str,
    ap_mac: bytes,
    client_mac: bytes,
    anonce: bytes,
    snonce: bytes,
    replay_counter: int = 1,
) -> bytes:
    """Build a representative EAPOL-Key message-2 payload, MIC field zeroed.

    A real 802.1X EAPOL-Key frame has a fixed binary layout (descriptor
    type, key-info bitfield, key length, replay counter, nonce, key-IV, RSC,
    key-data, and a 16-byte MIC field). We model the fields that the MIC
    computation actually covers -- the MIC field itself is represented as
    16 zero bytes, exactly as the real protocol zeroes it before hashing.
    """
    mic_placeholder = b"\x00" * 16
    return (
        ssid.encode("utf-8")
        + ap_mac
        + client_mac
        + anonce
        + snonce
        + replay_counter.to_bytes(8, "big")
        + mic_placeholder
    )


def compute_mic(kck: bytes, eapol_frame: bytes) -> bytes:
    """HMAC-SHA1(KCK, eapol_frame), truncated to 16 bytes -- the real WPA2
    (AES/CCMP-suite) MIC construction used in 802.11i message 2/3."""
    return hmac.new(kck, eapol_frame, hashlib.sha1).digest()[:16]


def verify_mic(kck: bytes, eapol_frame: bytes, candidate_mic: bytes) -> bool:
    """Constant-time comparison of a computed MIC against a captured one."""
    expected = compute_mic(kck, eapol_frame)
    return hmac.compare_digest(expected, candidate_mic)


# ---------------------------------------------------------------------------
# Convenience: passphrase -> MIC in one call (what a cracker does per guess)
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class HandshakeMaterial:
    """Everything needed, alongside a passphrase guess, to compute a MIC."""

    ssid: str
    ap_mac: bytes
    client_mac: bytes
    anonce: bytes
    snonce: bytes
    replay_counter: int = 1


def mic_for_passphrase(passphrase: str, material: HandshakeMaterial) -> bytes:
    """Run the full PSK -> PMK -> PTK -> MIC chain for one candidate guess.

    This is the exact per-guess workload a real offline WPA2 cracker (e.g.
    aircrack-ng, hashcat -m 22000) performs for every wordlist entry -- the
    4096-round PBKDF2 dominates the cost, which is precisely why WPA2
    cracking throughput is measured in thousands, not billions, of
    guesses/sec on commodity hardware (versus unsalted fast hashes).
    """
    pmk = derive_pmk(passphrase, material.ssid)
    ptk = derive_ptk(pmk, material.ap_mac, material.client_mac, material.anonce, material.snonce)
    kck = extract_kck(ptk)
    frame = build_eapol_frame(
        material.ssid,
        material.ap_mac,
        material.client_mac,
        material.anonce,
        material.snonce,
        material.replay_counter,
    )
    return compute_mic(kck, frame)
