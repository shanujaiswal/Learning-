"""
handshake_simulator.py
=======================
Simulates capturing a WPA2 4-way handshake for a target network.

IMPORTANT (lab scope): this module does NOT touch any wireless hardware, does
NOT put a network interface into monitor mode, and does NOT send/receive any
real 802.11 frames. It models only the *data* a real capture tool
(airodump-ng, hcxdumptool, Wireshark) would place in a .cap/.hccapx/.hc22000
file after genuinely capturing a handshake over the air:

    - the network's SSID
    - the AP's and client's MAC addresses
    - the ANonce (from message 1) and SNonce (from message 2)
    - a MIC value (from message 2), computed with the REAL target passphrase

An attacker who captured a real handshake would have exactly this data and
nothing more -- they do NOT have the passphrase itself (that's what they're
trying to recover). This module plays the role of "the network", generating
that captured data using a real passphrase the dictionary attack (in
dictionary_attack.py) does not get to see directly -- it only gets the
CapturedHandshake object below and must recover the passphrase from it.
"""

from __future__ import annotations

import os
from dataclasses import dataclass

from wpa2_crypto import HandshakeMaterial, mic_for_passphrase


@dataclass(frozen=True)
class CapturedHandshake:
    """Everything a real airodump-ng/hcxdumptool capture file would contain
    for one completed 4-way handshake -- and nothing an attacker wouldn't
    actually have. Notably: no passphrase, no PMK, no PTK, no KCK.
    """

    ssid: str
    ap_mac: bytes
    client_mac: bytes
    anonce: bytes
    snonce: bytes
    replay_counter: int
    captured_mic: bytes

    def material(self) -> HandshakeMaterial:
        """Repackage the public capture fields for the crypto module -- this
        is exactly what an attacker's own dictionary-attack code would build
        from a parsed capture file, so it can recompute a candidate MIC."""
        return HandshakeMaterial(
            ssid=self.ssid,
            ap_mac=self.ap_mac,
            client_mac=self.client_mac,
            anonce=self.anonce,
            snonce=self.snonce,
            replay_counter=self.replay_counter,
        )


def _random_mac() -> bytes:
    """A locally-administered, unicast random MAC (for simulation only)."""
    first_byte = (os.urandom(1)[0] & 0b11111100) | 0b00000010
    return bytes([first_byte]) + os.urandom(5)


def _random_nonce() -> bytes:
    """A real ANonce/SNonce is 32 bytes of randomness generated per handshake."""
    return os.urandom(32)


def simulate_capture(
    ssid: str,
    passphrase: str,
    ap_mac: bytes | None = None,
    client_mac: bytes | None = None,
) -> CapturedHandshake:
    """Simulate capturing one 4-way handshake for (ssid, passphrase).

    In real life: an auditor puts a card in monitor mode near the target AP,
    waits for (or forces, via a deauth of their own authorized test client)
    a client to complete its handshake, and captures messages 1-4 with
    airodump-ng/hcxdumptool. Here, we simply generate the same fields that
    capture would contain, using the real passphrase to compute the MIC
    the "client" would have sent in message 2.
    """
    ap_mac = ap_mac if ap_mac is not None else _random_mac()
    client_mac = client_mac if client_mac is not None else _random_mac()
    anonce = _random_nonce()
    snonce = _random_nonce()
    replay_counter = 1

    material = HandshakeMaterial(
        ssid=ssid,
        ap_mac=ap_mac,
        client_mac=client_mac,
        anonce=anonce,
        snonce=snonce,
        replay_counter=replay_counter,
    )
    # The "client" (which genuinely knows the passphrase) computes the real
    # MIC using the full PSK -> PMK -> PTK -> MIC chain. This is the value
    # that ends up in the captured handshake file.
    captured_mic = mic_for_passphrase(passphrase, material)

    return CapturedHandshake(
        ssid=ssid,
        ap_mac=ap_mac,
        client_mac=client_mac,
        anonce=anonce,
        snonce=snonce,
        replay_counter=replay_counter,
        captured_mic=captured_mic,
    )


def describe(handshake: CapturedHandshake) -> str:
    """A human-readable summary, similar to what `aircrack-ng handshake.cap`
    or a hcxpcapngtool conversion log would print about a captured file."""
    return (
        f"Captured 4-way handshake\n"
        f"  SSID           : {handshake.ssid}\n"
        f"  AP MAC (BSSID) : {handshake.ap_mac.hex(':')}\n"
        f"  Client MAC     : {handshake.client_mac.hex(':')}\n"
        f"  ANonce         : {handshake.anonce.hex()}\n"
        f"  SNonce         : {handshake.snonce.hex()}\n"
        f"  Replay counter : {handshake.replay_counter}\n"
        f"  Captured MIC   : {handshake.captured_mic.hex()}"
    )
