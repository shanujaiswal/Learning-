### DCSync Mechanics

--> LEGAL/ETHICAL REMINDER: everything below is for authorized environments only - your own lab (e.g. a self-hosted AD lab, GOAD, HackTheBox/TryHackMe AD boxes) or engagements with signed written permission. Running DCSync against a real domain without authorization is a serious crime (Computer Misuse Act, CFAA, equivalents). Always have written scope/rules of engagement before touching a real domain.

--> This note assumes you understand basic AD structure and the krbtgt/Golden Ticket material from note 10. DCSync deserves its own note because its mechanics are genuinely different from every other credential-theft technique covered so far - it needs no code execution on a domain controller at all.

## The Protocol Being Abused - MS-DRSR

--> Domain controllers legitimately replicate the directory database between each other using MS-DRSR (Directory Replication Service Remote Protocol), an RPC-based protocol. When DC02 needs the latest changes DC01 has, it literally asks DC01 to "replicate" the relevant objects to it - including password-hash attributes, because DCs need each other's copy of every account's hash to authenticate users regardless of which DC they hit.
--> DCSync abuses this by having an ATTACKER-CONTROLLED machine - not a real DC at all - issue that same replication request to a real DC and simply receive the answer. The DC has no built-in way to verify "is the requester actually a DC," it only checks whether the requesting security principal holds the right AD PERMISSIONS to ask for replication data.

## The Permissions That Actually Matter

--> Two extended rights on the domain object control this:
1. `DS-Replication-Get-Changes` (GUID `1131f6aa-9c07-11d1-f79f-00c04fc2dcd2`)
2. `DS-Replication-Get-Changes-All` (GUID `1131f6ad-9c07-11d1-f79f-00c04fc2dcd2`)
--> Normally these are held only by Domain Admins, Enterprise Admins, and the domain controllers' own computer accounts (which need them to replicate with each other). But they are ORDINARY AD PERMISSIONS that can be delegated to ANY security principal - and they occasionally get misdelegated by accident, e.g. a group created for some legitimate directory-sync tool integration (a password-sync appliance, an identity-governance product) is granted both rights so the tool can pull hash data, and that group is broader or more compromisable than anyone realized.
--> If an attacker compromises ANY account holding both rights, they can DCSync from literally any machine on the network with line-of-sight to a DC - no local admin, no code execution, no presence on a DC required at all.

## What You Actually Get

--> A DCSync request can target a specific account (commonly `krbtgt`, to enable Golden Ticket forging per note 10) or be used to pull every account's NTLM hash domain-wide.

```bash
# Mimikatz - DCSync the krbtgt account specifically (the classic Golden Ticket setup)
lsadump::dcsync /domain:corp.local /user:krbtgt

# Impacket secretsdump.py via the DRSUAPI method - dumps ALL domain account
# hashes by replaying MS-DRSR requests, no code execution on the DC needed
secretsdump.py -just-dc corp.local/alice:'Password123'@10.10.10.5
```

--> Contrast this explicitly with dumping `NTDS.dit` directly (either by copying the file after taking a volume shadow copy, or via `ntdsutil`) - that route requires actual DA-level access ON a domain controller (local admin on the DC itself, or equivalent), a much higher bar to clear than "holds one specific pair of AD rights." DCSync collapses that requirement down to a permissions check, which is exactly why it's treated as its own distinct technique rather than a footnote under credential dumping (note 35 covers the LSASS/DPAPI/browser-credential side of dumping instead).

## Why This Is Both a Devastating Escalation and a Stealthy One

--> Escalation angle: "has DS-Replication-Get-Changes(-All) on the domain object" sounds like a narrow, specific thing to hold - but it is functionally equivalent to owning every credential in the domain including `krbtgt`, which means it's equivalent to full domain compromise and Golden Ticket persistence (note 10) in one step. BloodHound renders this explicitly as a `GetChangesAll`/`GetChanges` edge on any principal that holds it, and any such edge to a non-Tier-0-worthy account is treated as a critical finding.
--> Stealth angle: from the DC's perspective, a DCSync request looks exactly like normal inter-DC replication traffic - there is no "user logged on" event the way there is for interactive or network logons. Detecting abuse specifically requires watching for replication requests whose SOURCE is NOT a real domain controller's IP/computer account, which most environments don't baseline for by default.

## Detection

--> 1. Enable SACL auditing on the domain object specifically for the `DS-Replication-Get-Changes` and `DS-Replication-Get-Changes-All` extended rights, generating Event ID 4662 whenever they're exercised.
--> 2. Filter 4662 events for that GUID pair where the source computer account is NOT one of the domain's actual DCs - a real DC exercising this right is expected traffic; anything else is a strong DCSync indicator.
--> 3. Use BloodHound's `GetChangesAll`/`GetChanges` edges proactively during assessments (and defensively during audits) to find any non-Tier-0 principal that holds these rights before an attacker does.
--> 4. Monitor for `secretsdump.py`/Mimikatz `lsadump::dcsync` command-line signatures via EDR, though this only catches unsophisticated/noisy usage - the permission-based detection above is the reliable control.

## Mitigation

--> Regularly audit domain-object ACLs for `DS-Replication-Get-Changes*` grants and remove any that aren't Domain Admins/Enterprise Admins/DCs themselves. Treat any custom group or service account holding these rights as Tier-0 sensitive - protect it (and its owners' credentials) with the same rigor as a Domain Admin account, since compromising it IS compromising a Domain Admin in every practical sense.

--> Putting it together: DCSync is usually the technique that CONVERTS a delegation abuse (note 33) or a misdelegated-permissions finding into full domain compromise - once you have DCSync-equivalent rights on any account, pulling `krbtgt`'s hash and minting a Golden Ticket (note 10) is a two-command finish line.
