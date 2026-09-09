### Wireless Network Security - WPA2 and Common Weaknesses

--> LEGAL/ETHICAL REMINDER: everything below is conceptual/educational. Capturing handshakes, auditing PSKs, or testing for rogue APs is only legal against networks you own or are explicitly authorized (in writing) to test — e.g. your own home lab AP, a TryHackMe/HackHTB wireless room, or a signed wireless penetration test engagement. Interfering with or auditing someone else's Wi-Fi without authorization is illegal in most jurisdictions.

--> Goal of this note: understand how 802.11 wireless security evolved, how the WPA2 handshake actually works, and why specific well-known weaknesses (WPS PIN design, KRACK, rogue APs) exist conceptually — as a foundation for defending wireless networks properly.

## 802.11 Basics

--> 802.11 is the IEEE standard family for Wi-Fi. A few terms recur throughout wireless security discussion:

1. SSID (Service Set Identifier) - the human-readable network name broadcast by an access point (e.g. `HomeWifi-5G`). Multiple physical APs can share the same SSID to form one logical network (e.g. mesh systems, enterprise APs).
2. BSSID - the MAC address of a specific physical radio/access point. Where SSID identifies "the network" as a concept, BSSID identifies "this specific box" — important because an attacker can broadcast the same SSID from a different BSSID to impersonate a network (see Evil Twin below).
3. Channel - a specific frequency range within a band (2.4GHz or 5GHz) that an AP transmits on. Multiple nearby APs on overlapping channels causes interference; security-wise, channel choice itself isn't a security control, but scanning tools use channel-hopping to discover all nearby APs during an audit.
4. Beacon frames - periodic broadcast frames an AP sends announcing its SSID, supported rates, and security capabilities. These are unauthenticated and unencrypted by design (a client needs to see them before any security handshake happens), which is part of why SSID/BSSID information is trivially visible to anyone nearby with a wireless card in monitor mode.

## Security Evolution: WEP to WPA3

--> Wireless security protocols evolved specifically because each predecessor had a fundamental, structural weakness — not just implementation bugs.

1. WEP (Wired Equivalent Privacy, 1997) - used RC4 stream cipher with a small (24-bit) initialization vector (IV) that gets reused frequently on a busy network. Reused IVs with a stream cipher leak enough information that passive traffic capture alone (no active attack needed) lets an attacker statistically recover the WEP key, often in minutes. WEP is considered completely broken and should never be used.
2. WPA (Wi-Fi Protected Access, 2003) - an interim fix introducing TKIP (Temporal Key Integrity Protocol), which added per-packet key mixing and message integrity checks on top of the same underlying RC4 cipher, as a stopgap while WPA2 was finalized (many WEP-era devices could be firmware-upgraded to WPA/TKIP without new hardware). TKIP fixed WEP's specific IV-reuse flaw but still had weaknesses (e.g. practical TKIP-specific packet-injection/decryption attacks) and is now itself deprecated.
3. WPA2 (2004) - replaced RC4/TKIP with AES in CCMP mode (a proper authenticated encryption scheme), a fundamentally stronger cryptographic foundation. WPA2-Personal (PSK, a shared passphrase) and WPA2-Enterprise (802.1X, individual per-user credentials via a RADIUS server) are the two deployment modes. WPA2 is still widely deployed today; its main remaining weaknesses aren't in the cipher itself but in the handshake and in weak passphrases (below).
4. WPA3 (2018) - replaces the PSK 4-way handshake's vulnerable points with SAE (Simultaneous Authentication of Equals, a variant of the Dragonfly key exchange), which provides forward secrecy (capturing today's handshake doesn't let you decrypt yesterday's or tomorrow's traffic even if you later learn the password) and resists offline dictionary attacks on captured handshake traffic far better than WPA2-PSK's design allows.

--> Why WEP/WPA are deprecated: their weaknesses are structural, not patchable. No configuration change fixes WEP's IV reuse problem or WPA/TKIP's underlying RC4 dependency — the only real fix is to stop using the protocol.

## The WPA2 4-Way Handshake, Step by Step

--> WPA2-Personal uses a pre-shared key (PSK) — a passphrase both the AP and client already know — but never sends that passphrase over the air. Instead, both sides prove they know it by deriving and exchanging session keys through a 4-message handshake.

1. Both AP and client already possess the PSK (the Wi-Fi password) and derive from it a PMK (Pairwise Master Key) using a key-derivation function (PBKDF2) seeded with the SSID — this is why the same passphrase produces a different PMK on networks with different SSIDs.
2. Message 1 (AP to client) - the AP sends an ANonce (a random nonce it generated).
3. Message 2 (client to AP) - the client generates its own SNonce, and from the PMK + ANonce + SNonce + both MAC addresses, derives a PTK (Pairwise Transient Key, the actual session key used to encrypt traffic). The client sends its SNonce back to the AP along with a MIC (Message Integrity Code) computed using the PTK — proving it correctly derived a key from the (assumed-correct) PMK.
4. Message 3 (AP to client) - the AP, having received the SNonce, can now independently derive the same PTK. It sends the GTK (Group Temporal Key, used for broadcast/multicast traffic) encrypted, plus its own MIC.
5. Message 4 (client to AP) - a final acknowledgment confirming installation of the keys. From this point on, unicast traffic is encrypted with the PTK and broadcast traffic with the GTK.

```text
AP                                          Client
 |--- Msg 1: ANonce ------------------------->|
 |<-- Msg 2: SNonce + MIC(PTK) ----------------|
 |--- Msg 3: GTK (encrypted) + MIC(PTK) ------>|
 |<-- Msg 4: ACK ------------------------------|
```

--> Why capturing this handshake matters for offline password auditing: the MIC values sent in messages 2 and 3 are computed using the PTK, which is itself derived from the PMK — which is derived from the passphrase. An attacker (or an admin auditing their own network) who captures all four messages can, entirely offline, try candidate passphrases: derive a candidate PMK, derive a candidate PTK, compute what the MIC *would* be, and compare it to the captured MIC. A match confirms the passphrase — with zero further contact with the AP, and no lockout policy applying, because (like Kerberoasting) the guessing happens entirely on the attacker's own hardware. This is precisely why passphrase strength matters so much for WPA2-Personal: an attacker only needs to capture one handshake (often forced via a deauthentication frame that kicks a client off so it reconnects and produces a fresh handshake) and can then brute-force offline indefinitely, at whatever speed their hardware (often GPU-accelerated) allows.

```bash
# Illustrative only - capturing/cracking your OWN authorized test network
# 1. Put a wireless card into monitor mode and capture a handshake (airodump-ng / similar)
# 2. Optionally send a deauth frame to your own test client to force a re-handshake
# 3. Take the captured handshake offline and audit passphrase strength
hashcat -m 22000 handshake.hc22000 rockyou.txt   # WPA-PBKDF2-PMKID+EAPOL mode
```

--> This is exactly why WPA3's SAE handshake was designed to resist this pattern: SAE is built so that an attacker who passively captures the exchange cannot mount an efficient offline dictionary attack against it the way WPA2-PSK's design allows — each guess attempt in SAE effectively requires new interaction with the real AP rather than pure offline computation.

## WPS PIN Design Weaknesses

--> WPS (Wi-Fi Protected Setup) was introduced to make home-router setup easier — press a button, or enter an 8-digit PIN, and the AP and device exchange credentials automatically without the user typing the actual Wi-Fi passphrase.

--> The structural flaw: many implementations split the 8-digit PIN verification into two independently-checked halves (roughly the first 4 digits, then the last 3-4, with the 8th digit being a checksum). Because each half is validated separately and the AP tells the client which half was wrong, this collapses what should be a ~10^8 (100 million) guess space into two much smaller sequential guess spaces (~10^4 each) — dramatically reducing the number of guesses needed for an offline-adjacent brute force against the PIN, entirely independent of how strong the actual Wi-Fi passphrase is.

--> This is exactly why security-conscious administrators disable WPS entirely: the feature exists purely for setup convenience, and its design flaw means it can undermine an otherwise-strong WPA2 passphrase, since gaining the WPS PIN typically yields the underlying Wi-Fi passphrase directly.

## Evil Twin / Rogue Access Points

--> An Evil Twin is a rogue access point configured to broadcast the same SSID (and sometimes spoof the same BSSID) as a legitimate network, in an attempt to trick client devices or users into connecting to the attacker's AP instead of the real one.

--> Why this works conceptually: client devices generally choose which AP to associate with based on SSID and signal strength, not cryptographic proof of "which physical AP is the real one" (this is exactly what WPA2/3's mutual authentication and 802.1X in Enterprise mode are meant to prevent, when properly configured with certificate validation). If a rogue AP has a stronger signal or the legitimate AP is temporarily jammed/deauthenticated, a device may associate with the rogue AP instead. For open or misconfigured networks (e.g. a captive-portal guest Wi-Fi with no real cryptographic client-to-AP authentication), a user can then be shown a fake login/captive portal page designed to harvest credentials — this is a social-engineering step layered on top of the wireless impersonation.

--> Enterprise networks using 802.1X with proper certificate validation (the client verifies the RADIUS server's certificate before sending credentials) are far more resistant to Evil Twin credential harvesting, because the client is cryptographically checking that it's really talking to the legitimate authentication infrastructure, not just matching on SSID.

## KRACK (Key Reinstallation Attack), Conceptually

--> KRACK (2017) was a vulnerability in the WPA2 4-way handshake's *implementation* across many vendors, not in the underlying AES-CCMP cipher itself.

--> The conceptual flaw: the handshake's message 3 can be retransmitted by the AP if it doesn't receive message 4 in time (normal, expected behavior for a lossy wireless link). But in many implementations, when the client received a retransmitted message 3, it would reinstall the *same* PTK/GTK it already had — and in doing so, reset the associated nonce/packet-counter state back to its initial value. Since these protocols rely on nonces (or counters) never repeating under the same key to remain secure, an attacker able to force this retransmission (by selectively blocking message 4 from reaching the AP) could trigger nonce reuse, which in stream-cipher-like modes leaks enough structure to decrypt or, in some cases, forge/inject packets.

--> Why this matters conceptually: it's a good example of a vulnerability that exists purely in *state-management logic* around an otherwise sound cryptographic handshake — the fix (patch clients/APs to not reset nonce state on retransmitted messages) didn't require replacing the cryptography, just correcting the implementation's handling of a specific message-replay edge case.

## Defenses

- Use WPA3 where supported, or WPA2 (AES/CCMP) as a minimum — never WEP or WPA/TKIP, since those are structurally broken rather than merely weak.
- Use a genuinely strong PSK for WPA2/WPA3-Personal - a long, high-entropy passphrase makes offline handshake-cracking computationally infeasible even after a handshake capture, closing the specific gap WPA2-PSK's design exposes.
- Disable WPS - given its structural PIN-verification flaw, WPS should be disabled on any network where security matters, or restricted to push-button-only mode if unavoidable.
- Prefer WPA2/WPA3-Enterprise (802.1X) for organizational networks - individual per-user credentials via RADIUS, combined with client-side certificate validation of the authentication server, meaningfully raises the bar against both credential-stuffing across users and Evil Twin-style impersonation.
- Keep firmware/drivers patched - KRACK-class vulnerabilities are implementation bugs; timely vendor patches on both APs and client devices closed the specific state-handling flaw involved.
- Monitor for rogue APs - wireless intrusion detection systems (WIDS) and periodic site surveys can flag unexpected BSSIDs broadcasting a legitimate SSID, or APs with unusual signal patterns, as an indicator of an Evil Twin nearby.
- Network segmentation and least privilege beyond the AP itself - even if a wireless network's confidentiality is somehow compromised, proper VLAN segmentation and internal access controls limit how much an attacker gains from that single foothold.

## Terminology Recap

1. SSID/BSSID - the broadcast network name versus the specific physical AP's MAC address.
2. PSK/PMK/PTK/GTK - the chain of key derivation from a shared passphrase down to the actual per-session and per-broadcast encryption keys.
3. 4-way handshake - the WPA2-Personal message exchange that proves both sides know the PSK and establishes session keys, without ever transmitting the passphrase itself.
4. SAE - WPA3's Dragonfly-based key exchange, designed to resist offline dictionary attacks against captured handshake traffic.
5. Evil Twin - a rogue AP impersonating a legitimate network's SSID to attract client connections.
6. KRACK - an implementation-level nonce-reuse flaw in WPA2 handshake state handling, fixed via patches rather than a cryptographic redesign.

--> The throughline: wireless security has repeatedly evolved by fixing a structural weakness in the *previous* generation's handshake or cipher (WEP's IV reuse, WPA2-PSK's offline-crackable handshake), while implementation-level bugs like KRACK and design shortcuts like WPS's split-PIN check show that even a sound protocol can be undermined by how it's actually built and deployed.
