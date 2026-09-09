# Wireless Network Security Auditor -- Rogue AP & Deauthentication Detector

A self-contained, offline blue-team tool that plays the role of a **WIDS/WIPS**
(Wireless Intrusion Detection/Prevention System) analyzing a captured log of an
office's Wi-Fi airspace. It detects the three classic wireless attack
signatures covered in `04 Wireless Networking Fundamentals.md`:

1. **Rogue / evil-twin AP** -- an unauthorized access point broadcasting the
   *same SSID* as the legitimate corporate network but from a *different,
   unknown BSSID* -- trying to lure clients into associating with it instead
   of the real AP.
2. **Deauthentication flood** -- a burst of 802.11 deauth management frames
   aimed at knocking clients off the real AP, the classic precursor used to
   herd victims onto an evil twin.
3. **Weak-security AP** -- an AP broadcasting `OPEN` or `WEP` where the
   corporate policy mandates `WPA2`/`WPA3`.

> **This is a simulation, not a sniffer.** No wireless hardware, monitor mode,
> or packet capture library is used. `airspace_log_generator.py` builds plain
> Python dict "log records" that *represent* beacon and deauth frames (BSSID,
> SSID, signal strength, encryption, timestamps) from a fixed random seed, so
> the whole scenario is 100% reproducible offline. This mirrors how a real
> analyst would work from an exported PCAP/Kismet log rather than live radio.

## Real-world scenario

A corporate security team runs Kismet (or a Cisco/Aruba WIDS sensor) that
passively listens to all 802.11 management traffic in the office and logs
every beacon and deauth frame it overhears, from *any* AP in range -- not
just the company's own hardware. An attacker sets up a laptop or Pineapple
device broadcasting the company's own SSID (`CorpNet-WiFi`) to trick employee
laptops into auto-connecting, then floods the real AP's clients with spoofed
deauth frames to force them to roam onto the fake AP, where their traffic
(and credentials) can be intercepted. Separately, IT may have quietly
misconfigured the guest AP to `OPEN` instead of `WPA2` during a hardware
swap -- a silent policy violation nobody would notice without an automated
audit. This tool is the automated audit: it ingests the captured frame log
and answers "what's wrong with our airspace right now?"

## Architecture

| Module | Role | Real-world equivalent |
|---|---|---|
| `known_ap_inventory.py` | Ground-truth enrollment: approved SSID -> BSSID list + required encryption per network | The WIDS's enrolled-AP database (Cisco Prime Infrastructure / Aruba AirWave AP inventory) |
| `airspace_log_generator.py` | Synthesizes a time-ordered log of beacon + deauth frames, with an injected evil twin, deauth flood, and misconfigured weak-encryption AP | A wireless sniffer in monitor mode / an exported Kismet or Wireshark 802.11 capture |
| `rogue_ap_detector.py` | Flags beacons whose SSID matches an approved network but whose BSSID doesn't, and beacons that violate the encryption policy | Kismet's rogue-AP alert / Cisco aWIPS "unauthorized AP" and "AP policy violation" signatures |
| `deauth_detector.py` | Sliding-window rate detector: flags bursts of deauth frames within a short time window | Kismet's "deauth flood" alert / Cisco aWIPS "management frame flood" signature |
| `main.py` | Orchestrates both detectors over the full log, prints a live alert feed + summary, renders the annotated timeline PNG | The WIDS console / SIEM dashboard an analyst watches |

## Run it

Requires Python 3 with `numpy` and `matplotlib` (stdlib otherwise).

```bash
python main.py
```

This regenerates the same fixed-seed airspace log every time, runs both
detectors, prints the alert feed and summary to the terminal, and writes
`wireless_audit_result.png` next to the scripts.

## Verified result (actual output)

```
==============================================================================
WIRELESS NETWORK SECURITY AUDITOR
Analyzing 588 captured 802.11 frames (522 beacons, 66 deauth frames)
==============================================================================

--- LIVE ALERT FEED ---------------------------------------------------------
[t=  0.00s] !! WEAK ENCRYPTION | AP AA:BB:CC:00:02:01 broadcasts SSID 'CorpNet-Guest' with encryption=OPEN, but policy requires at least WPA2.
[t= 45.00s] !! EVIL TWIN AP   | SSID 'CorpNet-WiFi' is broadcast by unrecognized BSSID DE:AD:BE:EF:13:37 (approved BSSIDs: ['AA:BB:CC:00:01:01', 'AA:BB:CC:00:01:02', 'AA:BB:CC:00:01:03']). Likely evil-twin AP impersonating the corporate network.
[t= 52.01s] !! DEAUTH FLOOD    | 60 deauth frames in 2.95s (>= 10 per 5s window). Apparent source(s): ['AA:BB:CC:00:01:01']. 7 distinct client(s) targeted: ['CL:IE:NT:00:00:01', 'CL:IE:NT:00:00:02', 'CL:IE:NT:00:00:03', 'CL:IE:NT:00:00:04', 'CL:IE:NT:00:00:05', 'CL:IE:NT:00:00:06', 'CL:IE:NT:00:00:07'].

--- SUMMARY -----------------------------------------------------------------
Rogue / evil-twin APs found : 1
    - BSSID DE:AD:BE:EF:13:37 impersonating SSID 'CorpNet-WiFi' (first seen t=45.00s)
Weak-encryption APs found   : 1
    - BSSID AA:BB:CC:00:02:01 SSID 'CorpNet-Guest' -> AP AA:BB:CC:00:02:01 broadcasts SSID 'CorpNet-Guest' with encryption=OPEN, but policy requires at least WPA2.
Deauth-flood bursts found   : 1
    - 52.01s - 54.96s: 60 frames
==============================================================================

Saved visual timeline to: wireless_audit_result.png
```

Note the deauth flood's spoofed source (`AA:BB:CC:00:01:01`) is the *real*
legitimate AP's BSSID -- attackers spoof the AP's own MAC as the deauth
frame's source address, since 802.11 deauth frames aren't authenticated, so
clients have no way to tell a spoofed deauth from a genuine one. Detecting
the *rate*, not the (unverifiable) source, is exactly why the burst detector
is rate-based rather than source-based.

`wireless_audit_result.png` renders two stacked panels: the top shows every
AP's beacon signal strength over time (the evil twin in red with X markers,
the weak-encryption guest AP in orange), with the deauth-flood window shaded
in red across both panels; the bottom is an event-plot of every raw deauth
frame, so the dense burst is visually obvious against the sparse background
noise.

## Things to try changing

- **Raise/lower the deauth burst threshold or window** in `main.py`'s call to
  `detect_deauth_bursts(deauth_events, window_seconds=..., threshold=...)` --
  too low and normal roaming noise gets flagged; too high and a real flood
  slips through. This is the same threshold-tuning tradeoff (false positives
  vs. false negatives) every real WIDS has to make.
- **Add a second evil twin** in `airspace_log_generator.py` with a different
  BSSID and see the alert feed/summary correctly report two distinct rogue
  APs instead of deduplicating them into one.
- **Make the evil twin also violate encryption policy** (e.g. broadcast
  `OPEN` instead of `WPA2`) and watch `rogue_ap_detector.py` raise *both* an
  `EVIL_TWIN` and a `WEAK_ENCRYPTION` alert for the same BSSID.
- **Spread the same deauth-frame count over a longer duration** (e.g. 60
  frames over 30 seconds instead of 3) so the burst no longer exceeds the
  rate threshold, demonstrating why rate (not raw count) is what matters.
- **Change `known_ap_inventory.py`'s required encryption to `WPA3`** for
  `CorpNet-WiFi` and watch every legitimate AP (still WPA2) suddenly get
  flagged as a policy violation -- showing how the whole audit is only as
  correct as the baseline it's compared against.
