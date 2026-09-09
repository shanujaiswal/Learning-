### NTLM Relay and LLMNR/NBT-NS Poisoning

--> LEGAL/ETHICAL REMINDER: everything below is for authorized environments only - your own lab (e.g. a self-hosted AD lab, GOAD, HackTheBox/TryHackMe AD boxes) or engagements with signed written permission. Running Responder or a relay tool against a real production network without authorization is a serious crime (Computer Misuse Act, CFAA, equivalents). Always have written scope/rules of engagement before touching a real domain.

--> This note assumes you already understand the Kerberos/NTLM basics from note 10. It covers what is arguably the single most common real-world internal AD foothold technique - poisoning name-resolution broadcasts and relaying the credentials that fall out of it.

## Why Broadcast Name Resolution Exists At All

--> Windows resolves hostnames in a defined order: local hosts file, then DNS, then - if DNS fails to resolve a name - it falls back to broadcast/multicast protocols on the local segment: LLMNR (Link-Local Multicast Name Resolution, UDP 5355), NBT-NS (NetBIOS Name Service, UDP 137), and sometimes mDNS.
--> The fallback exists for legitimate reasons (ad-hoc peer discovery on a LAN with no DNS entry), but it has one fatal property for security: the fallback is a broadcast/multicast question with NO authentication on who is allowed to answer. ANY host on the same broadcast segment can respond "that's me" and the querying machine has no way to verify the claim.
--> Real trigger scenarios happen constantly on ordinary business networks without any attacker action: a user mistypes a mapped drive name (`\\fileservr\share` instead of `\\fileserver\share`), a stale GPO tries to reach a decommissioned print server, a browser tries to resolve an internal hostname that no longer has a DNS record. Every one of these failed DNS lookups becomes a broadcast query any listening attacker can answer.

## Responder - Poisoning the Broadcast

--> Responder is the standard tool for this: it binds to the relevant ports and answers EVERY LLMNR/NBT-NS/mDNS query it hears with "I am that host," then spins up fake SMB/HTTP/LDAP/etc. servers to receive the connection the victim now believes is legitimate.
--> Because the victim now believes it's talking to a real file/print/whatever server, it authenticates - and Windows SMB/HTTP clients authenticate using NTLM by default when talking to an unknown host that isn't Kerberos-capable from the client's point of view (no SPN registered for a fake hostname, so Kerberos can't be used, and the client falls back to NTLM). That NTLM authentication exchange is what Responder captures.

```bash
# Run Responder listening on the target interface, poisoning all supported protocols
responder -I eth0 -wrf

# Output looks roughly like:
# [SMB] NTLMv2-SSP Client   : 10.10.10.55
# [SMB] NTLMv2-SSP Username : CORP\jsmith
# [SMB] NTLMv2-SSP Hash     : jsmith::CORP:1122334455667788:...
```

--> What's actually captured is an NTLMv2 challenge/response, NOT the plaintext password and NOT the raw NTLM hash - it's a value derived from the hash plus a server challenge plus client data, which means it must either be cracked offline or relayed live; it can't be "passed" directly the way a raw NTLM hash can (contrast with Pass-the-Hash in note 10).

## Cracking vs Relaying

--> Two options once you have a captured NTLMv2 hash:
1. Crack it offline with hashcat/John (cross-reference note 07) - works only if the underlying password is weak/wordlist-guessable, no domain lockout risk since you're not talking to the DC.
2. Relay it live, without ever cracking it, straight into an authentication attempt against a DIFFERENT target service - NTLM Relay.

--> NTLM relay works because NTLM authentication is just a challenge-response handshake that doesn't bind itself to a specific destination server by default - if you can sit in the middle as soon as the victim starts authenticating to your poisoned name, you can immediately forward (relay) that exact handshake to a real target server before it expires, authenticating AS the victim against that target without ever seeing their password or cracking anything.

```bash
# Impacket's ntlmrelayx.py - listens for incoming NTLM auth (fed by Responder
# with LLMNR/NBT-NS poisoning disabled in Responder's own SMB/HTTP listeners
# so ntlmrelayx can bind those ports instead) and relays it onward
ntlmrelayx.py -tf targets.txt -smb2support

# Common relay targets and what you get:
#   -> another host's SMB (if that host has SMB signing NOT enforced) -> command exec
#   -> LDAP/LDAPS on a DC -> read/write directory objects as the relayed identity
#   -> the ADCS HTTP web enrollment endpoint -> ESC8 (see note 32)
```

--> SMB signing is the primary defense that breaks SMB relay: if the target enforces signing, every message must be cryptographically signed with a key derived from the session key established during authentication, and an attacker relaying the auth doesn't get to forge that signature for messages of their own choosing - so a signed-required target refuses the relayed session's follow-on requests. Signing is optional-but-not-required by default on workstations in many environments, which is exactly why they remain relay targets, while DCs enforce signing by default.
--> Relaying to LDAP/LDAPS is especially dangerous because LDAP has no signing-enforcement-by-default story as tight as SMB historically had (Microsoft only started defaulting to LDAP channel binding/signing enforcement more recently) - relaying a machine account's or user's authentication to LDAP lets an attacker write directory attributes as that identity, which chains directly into Resource-Based Constrained Delegation abuse (see note 33) - relay the authentication of a computer account, then use the LDAP write access it grants to configure `msDS-AllowedToActOnBehalfOfOtherIdentity` on itself, escalating from "captured one broadcast" to full delegation-based impersonation.

## mitm6 - The IPv6 Companion Attack

--> Most Windows networks still have IPv6 enabled by default even when nobody uses it, and Windows machines periodically ask "is there an IPv6 DHCP/DNS server on this network?" via DHCPv6 - mitm6 answers that query, becomes the victim's IPv6 DNS server, and can then direct WPAD/other lookups (or specifically target Windows Update / other well-known-hostname queries) toward the attacker, harvesting NTLM auth the same way, often combined directly with `ntlmrelayx.py` for a one-two chain (mitm6 to gain DNS control, ntlmrelayx to catch and relay whatever authenticates as a result).

```bash
mitm6 -d corp.local
# run alongside ntlmrelayx.py targeting LDAPS to add a rogue computer account
# via the relayed machine authentication triggered by mitm6's DNS control
```

## Mitigations

--> 1. Disable LLMNR and NBT-NS entirely via GPO where legacy NetBIOS name resolution isn't actually required (Computer Configuration -> Administrative Templates -> Network -> DNS Client -> Turn Off Multicast Name Resolution, plus disabling NetBIOS over TCP/IP on adapters).
--> 2. Enforce SMB signing on all hosts, not just DCs (`RequireSecuritySignature` GPO setting) - breaks SMB relay outright.
--> 3. Enforce LDAP signing and LDAP channel binding on DCs - closes the historically-weaker LDAP relay path.
--> 4. Disable NTLM authentication entirely where feasible (Restrict NTLM GPO settings, audit mode first) in favor of Kerberos-only - if NTLM can never be used, there's nothing to poison a fallback into.
--> 5. Disable IPv6 or at minimum block DHCPv6/rogue router advertisements if IPv6 is genuinely unused, closing the mitm6 avenue.

--> Putting it together: LLMNR/NBT-NS poisoning + NTLM relay is consistently the fastest realistic path from "plugged into the corporate LAN with no credentials" to "a foothold or even DA" in real internal engagements, which is why it's usually the very first thing run (alongside passive traffic capture) after gaining physical/VPN access to a target network - see note 32 for how relaying specifically into AD CS's web enrollment endpoint (ESC8) turns this into an even more direct escalation path, and note 33 for how the LDAP-write side chains into delegation abuse.
