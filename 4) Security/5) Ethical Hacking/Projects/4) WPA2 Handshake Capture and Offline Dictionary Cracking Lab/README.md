# WPA2 Handshake Capture and Offline Dictionary Cracking Lab

A fully simulated, self-contained Python lab demonstrating the real,
well-documented WPA2-PSK weakness: once a 4-way handshake is captured, an
attacker can recover a weak passphrase entirely **offline**, with zero
further contact with the access point.

> **Scope note:** this project performs no real wireless capture. No network
> interface is put into monitor mode, no 802.11 frames are sent or received,
> and no real network is touched. Every "capture" is a deterministic
> simulation built from the same fields a real `airodump-ng`/`hcxdumptool`
> capture file would contain. See
> `../../Theory/11 Wireless Network Security - WPA2 and Common Weaknesses.md`
> for the underlying concepts (4-way handshake, PMK/PTK/MIC, why WPA3's SAE
> fixes this class of weakness).

## Real-world scenario

WPA2-Personal never transmits the Wi-Fi passphrase over the air. Instead,
both the AP and client derive a **Pairwise Master Key (PMK)** from the
passphrase and SSID, then a **Pairwise Transient Key (PTK)** from the PMK
plus per-session nonces and MAC addresses, and prove to each other they
derived the same key by exchanging **MIC** (Message Integrity Code) values
during a 4-message handshake.

Because that entire derivation chain is deterministic and public-algorithm
(PBKDF2-HMAC-SHA1, per IEEE 802.11i), anyone who has **captured** the four
handshake messages (SSID, both MAC addresses, both nonces, and a MIC) can
try candidate passphrases completely offline: derive a candidate PMK, derive
a candidate PTK, compute what the MIC *would* be, and compare it to the
captured value. A match cryptographically proves the passphrase — with no
lockout policy, no rate limiting, and no further packets sent to the real
AP, because all the guessing happens on the attacker's own hardware. This is
exactly what `aircrack-ng` and `hashcat -m 22000` do in practice, and it is
precisely why WPA2-Personal's security is only as strong as the passphrase
chosen (a genuinely random, high-entropy passphrase makes this offline
attack computationally infeasible) — and precisely the gap WPA3's SAE
handshake was designed to close (SAE requires live interaction with the AP
per guess, so it cannot be attacked this way).

**Citations for the algorithms implemented here:**
- IEEE 802.11i-2004 — defines the 4-way handshake, the PRF construction
  (HMAC-SHA1-based), and the PMK/PTK/MIC derivation.
- RFC 2898 (PKCS #5) — PBKDF2, the key-stretching function WPA2 uses to turn
  a passphrase + SSID into the PMK (4096 iterations, 256-bit output).
- Real-world tooling that implements this exact chain: `aircrack-ng`,
  `hashcat` (hash-mode 22000, "WPA-PBKDF2-PMKID+EAPOL"), `cowpatty`,
  `hcxdumptool`/`hcxpcapngtool`.

## Architecture

| Module | Role | Real-world equivalent |
|---|---|---|
| `wpa2_crypto.py` | Implements the actual 802.11i key-derivation chain: PBKDF2-HMAC-SHA1 (passphrase+SSID → PMK), the 802.11i PRF (PMK+nonces+MACs → PTK), and HMAC-SHA1 MIC computation/verification over a simulated EAPOL frame. | The exact PBKDF2/PRF key derivation used by real WPA2 networks and implemented internally by `aircrack-ng`/`hashcat` mode 22000. |
| `handshake_simulator.py` | Simulates capturing a 4-way handshake for a target network (SSID, AP/client MAC, ANonce/SNonce, MIC) — using the real target passphrase to compute the captured MIC, then hiding that passphrase from the "attacker" code path. | `airodump-ng` capturing a live handshake, or `hcxdumptool` capturing PMKID/EAPOL frames, into a `.cap`/`.hc22000` file. |
| `dictionary_attack.py` | Iterates a wordlist, derives PMK→PTK→MIC per candidate via `wpa2_crypto.py`, and checks for a MIC match against the captured handshake; reports the cracked passphrase, candidate count, elapsed time, and guesses/sec. | `aircrack-ng -w wordlist.txt capture.cap` or `hashcat -m 22000 handshake.hc22000 wordlist.txt` — offline WPA2 dictionary cracking. |
| `main.py` | Orchestrates the full demo end-to-end with assert-based verification: sets up a target network, simulates the capture, runs the dictionary attack, re-verifies the MIC match, and proves SSID salting by showing a wrong-SSID derivation fails. | A security auditor's full workflow: capture → offline crack → verify → document why passphrase/SSID choices matter. |
| `wordlist.txt` | A small illustrative wordlist (in the spirit of `rockyou.txt`, kept tiny so the demo runs in seconds) containing the target passphrase among 20 candidates. | A real password dictionary used with `aircrack-ng`/`hashcat`. |

## Run it

```bash
cd "3) Security/5) Ethical Hacking/Projects/4) WPA2 Handshake Capture and Offline Dictionary Cracking Lab"
python main.py
```

No third-party dependencies — everything uses Python's standard library
(`hashlib`, `hmac`, `dataclasses`, `time`, `os`).

## Verified result

Actual output from running `python main.py` in this environment:

```
Target SSID       : HomeNetwork_5G
Target passphrase : 'sunshine12'  (kept secret from the attacker below)
Wordlist          : wordlist.txt (20 candidates)
...
  [   6] trying 'sunshine12'             -> MIC e96b95dcfa9bfdbc1377eb3b195f98ad
  MATCH -- captured MIC reproduced by passphrase 'sunshine12'

Cracked passphrase : 'sunshine12'
Candidates tried   : 6
Elapsed time       : 0.0164 s
Throughput         : 365.9 guesses/sec

Captured MIC   : e96b95dcfa9bfdbc1377eb3b195f98ad
Recomputed MIC : e96b95dcfa9bfdbc1377eb3b195f98ad
MIC match confirmed -- passphrase recovery is cryptographically verified.

PMK with correct SSID ('HomeNetwork_5G')     : 5c89591b2969def201fccc75f72ba314654d0e76f7d54f139d1a833e43840180
PMK with wrong SSID   ('AttackerGuessedSSID') : 891b2a3d25805966b81ef4f6274a591d5f62b6744267b1f2df8c67f2fcf4fd75
Confirmed: identical passphrase + different SSID => different PMK.
A precomputed PMK/rainbow table for one SSID does NOT transfer to another.
Confirmed: even the CORRECT passphrase fails to reproduce the captured MIC when derived against the wrong SSID.

All assertions passed -- lab demonstration complete
```

The passphrase `sunshine12` was cracked on the 6th wordlist candidate at
roughly **366 guesses/sec** on the machine this was run on (single-threaded,
pure-Python `hashlib.pbkdf2_hmac`, no GPU/parallelism) — note the throughput
number will vary by hardware; run it yourself to measure your own machine's
speed. All `assert` statements in `main.py` passed, including the SSID-salting
check.

Note on throughput: 4096-round PBKDF2-HMAC-SHA1 is *deliberately* expensive
per guess — this is the real reason WPA2 handshake cracking runs at
thousands (not billions) of guesses/sec even with GPU acceleration, versus
an unsalted fast hash which can be tried billions of times per second. This
cost is the passphrase's real defense: a weak, dictionary-guessable
passphrase still falls quickly, but a high-entropy passphrase turns this
same offline attack computationally infeasible.

## Things to try changing

- **Swap in a strong random passphrase.** Change `target_passphrase` in
  `main.py` to a 20-character random string (e.g.
  `"kX9$mQ2!vL7#pR4&wZ8@"`) and rerun. The same 20-word `wordlist.txt` will
  **not** find it — `crack_handshake` will exhaust the wordlist and return
  `cracked=False`. This demonstrates concretely why passphrase strength is
  the actual mitigation for this weakness, not any change to the protocol
  itself.
- **Change the target SSID** and observe how the captured MIC (and every
  derived PMK/PTK) changes completely, even for the exact same passphrase —
  reinforcing the SSID-salting demonstration in step 5 of `main.py`.
- **Grow `wordlist.txt`** to thousands of entries and watch elapsed time
  scale roughly linearly, while guesses/sec stays roughly constant (bounded
  by the fixed 4096-round PBKDF2 cost per guess) — a rough model of why real
  wordlist attacks against strong passphrases take impractically long.
- **Try HMAC-MD5 for the MIC** (used by the legacy "WPA"/TKIP cipher suite
  instead of WPA2/CCMP's HMAC-SHA1) in `wpa2_crypto.compute_mic` and see
  that the dictionary attack still works identically — the MIC algorithm
  choice doesn't change the fundamental offline-crackability, only the
  cipher suite in use.
- **Simulate a deauth-forced re-handshake** by calling `simulate_capture`
  twice for the same network and noticing the ANonce/SNonce (and therefore
  the captured MIC) differ each time, while the *correct* passphrase still
  cracks both captures — showing that forcing a fresh handshake via a
  deauth frame (mentioned in the theory note) doesn't require knowing the
  passphrase, only observing a new handshake.
