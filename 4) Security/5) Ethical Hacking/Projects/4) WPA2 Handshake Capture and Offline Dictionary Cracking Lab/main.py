"""
main.py
=======
End-to-end demo of the WPA2 Handshake Capture and Offline Dictionary
Cracking Lab. Entirely simulated -- no wireless hardware, no monitor mode,
no real network traffic anywhere in this script.

Demonstrates, with assert-based verification (not just printed claims):

  1. A target network (SSID + real passphrase) has its 4-way handshake
     simulated/"captured" (handshake_simulator.py).
  2. An offline dictionary attack (dictionary_attack.py) recovers the
     correct passphrase purely by MIC matching -- zero contact with "the AP"
     during the attack itself.
  3. The recovered passphrase reproduces the captured MIC exactly (proving
     the crack is genuine, not coincidental).
  4. Deriving the PMK for the *same* passphrase but a *different* SSID
     produces a *different* PMK -- proving SSID acts as a cryptographic
     salt, so a precomputed table for one SSID does not transfer to another.
"""

from __future__ import annotations

from dictionary_attack import crack_handshake, load_wordlist
from handshake_simulator import describe, simulate_capture
from wpa2_crypto import derive_pmk

WORDLIST_PATH = "wordlist.txt"


def section(title: str) -> None:
    print()
    print("=" * 78)
    print(title)
    print("=" * 78)


def main() -> None:
    # ------------------------------------------------------------------
    # 1. Set up a target network with a REAL passphrase drawn from the
    #    wordlist (so the dictionary attack is guaranteed solvable, just
    #    like a real audit where the AP owner suspects a weak passphrase).
    # ------------------------------------------------------------------
    section("1. Target network setup")
    target_ssid = "HomeNetwork_5G"
    wordlist = load_wordlist(WORDLIST_PATH)
    target_passphrase = "sunshine12"
    assert target_passphrase in wordlist, "demo requires the target passphrase to be in the wordlist"
    print(f"Target SSID       : {target_ssid}")
    print(f"Target passphrase : {target_passphrase!r}  (kept secret from the attacker below)")
    print(f"Wordlist          : {WORDLIST_PATH} ({len(wordlist)} candidates)")

    # ------------------------------------------------------------------
    # 2. Simulate capturing the 4-way handshake for that network. This
    #    models exactly the data a real airodump-ng/hcxdumptool capture
    #    would contain -- SSID, both MACs, both nonces, and a MIC computed
    #    with the real passphrase. No passphrase/PMK/PTK is exposed here.
    # ------------------------------------------------------------------
    section("2. Simulated handshake capture")
    handshake = simulate_capture(target_ssid, target_passphrase)
    print(describe(handshake))

    # ------------------------------------------------------------------
    # 3. Run the offline dictionary attack against the captured handshake.
    #    The attacker code below only ever sees `handshake` (public capture
    #    data) and `wordlist` -- never `target_passphrase` directly.
    # ------------------------------------------------------------------
    section("3. Offline dictionary attack")
    result = crack_handshake(handshake, wordlist, verbose=True)

    assert result.cracked, "dictionary attack failed to recover the passphrase"
    assert result.passphrase == target_passphrase, "recovered passphrase does not match the real one"
    print()
    print(f"Cracked passphrase : {result.passphrase!r}")
    print(f"Candidates tried   : {result.candidates_tried}")
    print(f"Elapsed time       : {result.elapsed_seconds:.4f} s")
    print(f"Throughput         : {result.guesses_per_second:.1f} guesses/sec")
    print(
        "(PBKDF2-HMAC-SHA1 with 4096 rounds per guess is deliberately slow -- "
        "this is exactly why WPA2 cracking speed is measured in thousands, not "
        "billions, of guesses/sec on commodity hardware.)"
    )

    # ------------------------------------------------------------------
    # 4. Confirm the crack is genuine: recomputing the full chain with the
    #    recovered passphrase reproduces the captured MIC exactly.
    # ------------------------------------------------------------------
    section("4. Verifying the crack (MIC re-derivation)")
    from wpa2_crypto import mic_for_passphrase

    recomputed_mic = mic_for_passphrase(result.passphrase, handshake.material())
    assert recomputed_mic == handshake.captured_mic, "recomputed MIC does not match captured MIC"
    print(f"Captured MIC   : {handshake.captured_mic.hex()}")
    print(f"Recomputed MIC : {recomputed_mic.hex()}")
    print("MIC match confirmed -- passphrase recovery is cryptographically verified.")

    # ------------------------------------------------------------------
    # 5. SSID salting: same passphrase, different SSID -> different PMK.
    #    This is why a precomputed PMK table for one SSID (a classic
    #    optimization real tools like coWPAtty/genpmk exploit for common
    #    SSIDs) does not transfer to a network with a different SSID.
    # ------------------------------------------------------------------
    section("5. SSID salting demonstration")
    correct_ssid_pmk = derive_pmk(target_passphrase, target_ssid)
    wrong_ssid = "AttackerGuessedSSID"
    wrong_ssid_pmk = derive_pmk(target_passphrase, wrong_ssid)

    print(f"PMK with correct SSID ({target_ssid!r}) : {correct_ssid_pmk.hex()}")
    print(f"PMK with wrong SSID   ({wrong_ssid!r})   : {wrong_ssid_pmk.hex()}")

    assert correct_ssid_pmk != wrong_ssid_pmk, "PMK must differ when SSID differs (SSID acts as salt)"
    print("Confirmed: identical passphrase + different SSID => different PMK.")
    print("A precomputed PMK/rainbow table for one SSID does NOT transfer to another.")

    # Also show that deriving with the wrong SSID fails to reproduce the
    # captured MIC, i.e. an attacker who mistakenly assumes the wrong SSID
    # cannot crack the handshake even with the exact right passphrase.
    from wpa2_crypto import HandshakeMaterial

    wrong_ssid_material = HandshakeMaterial(
        ssid=wrong_ssid,
        ap_mac=handshake.ap_mac,
        client_mac=handshake.client_mac,
        anonce=handshake.anonce,
        snonce=handshake.snonce,
        replay_counter=handshake.replay_counter,
    )
    mic_with_wrong_ssid = mic_for_passphrase(target_passphrase, wrong_ssid_material)
    assert mic_with_wrong_ssid != handshake.captured_mic, (
        "MIC computed with the wrong SSID must NOT match the captured MIC "
        "even when using the correct passphrase"
    )
    print(
        "Confirmed: even the CORRECT passphrase fails to reproduce the captured "
        "MIC when derived against the wrong SSID."
    )

    section("All assertions passed -- lab demonstration complete")


if __name__ == "__main__":
    main()
