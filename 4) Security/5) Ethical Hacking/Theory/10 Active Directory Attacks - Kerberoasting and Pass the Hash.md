### Active Directory Attacks - Kerberoasting and Pass the Hash

--> LEGAL/ETHICAL REMINDER: everything below is for authorized environments only - your own lab (e.g. a self-hosted AD lab, GOAD, HackTheBox/TryHackMe AD boxes) or engagements with signed written permission. Attacking a real company's domain controller without authorization is a serious crime (Computer Misuse Act, CFAA, equivalents). Always have written scope/rules of engagement before touching a real domain.

--> This note assumes you already understand basic Windows networking and Metasploit usage. It builds directly on that toward Active Directory (AD), the identity/access backbone of almost every enterprise network.

## Active Directory Structure Basics

--> Active Directory is Microsoft's directory service for centrally managing users, computers, and permissions across a Windows network.

1. Domain - a logical grouping of objects (users, computers, groups) that share a common directory database and security policy. Example: `corp.local`.
2. Forest - one or more domains that share a common schema and trust relationships. The top-level container.
3. Domain Controller (DC) - a server running AD DS (Active Directory Domain Services) that holds the domain database (`NTDS.dit`), handles authentication, and enforces Group Policy. Compromising a DC usually means full domain compromise.
4. Organizational Unit (OU) - a container used to organize users/computers/groups for applying Group Policy Objects (GPOs) and delegating administration.
5. Users and Groups - identities and their group memberships. Group membership is how permissions are actually granted (e.g. `Domain Admins`, `Enterprise Admins`).
6. SYSVOL and NTDS.dit - SYSVOL holds GPOs/scripts replicated between DCs; `NTDS.dit` is the actual AD database (contains password hashes of every domain account) — this is the ultimate target of a domain compromise (via DCSync or copying the file after gaining DA).

--> Trust relationships let users in one domain authenticate to resources in another. Attacks often abuse trusts to pivot between domains in a forest.

```text
Forest: corp.local
 ├── Domain: corp.local
 │     ├── OU: Sales
 │     │     └── Users: alice, bob
 │     ├── OU: IT
 │     │     └── Users: svc_sql (service account, has an SPN)
 │     └── Domain Controllers OU
 │           └── DC01 (holds NTDS.dit)
 └── Group: Domain Admins (full control of the domain)
```

## Kerberos Authentication Flow

--> Kerberos is the default authentication protocol in AD (NTLM is the legacy fallback). Understanding its message flow is required to understand every AD attack below.

--> Key components:
- KDC (Key Distribution Center) - runs on every DC, has two services: the Authentication Service (AS) and the Ticket Granting Service (TGS).
- TGT (Ticket Granting Ticket) - proves you authenticated; used to request further tickets without re-sending your password.
- TGS ticket / service ticket - a ticket for a *specific* service, encrypted with that service account's key.
- SPN (Service Principal Name) - identifies a service instance, e.g. `MSSQLSvc/sql01.corp.local:1433`, bound to the account running that service.

--> Step by step flow:

1. AS-REQ - client sends a request to the KDC's AS, including a timestamp encrypted with a key derived from the user's password (pre-authentication).
2. KDC validates the pre-auth timestamp by decrypting it with the account's known password hash. If valid, it proves the client knows the password.
3. AS-REP - the KDC replies with a TGT (encrypted with the `krbtgt` account's hash — the KDC's own service account) plus a session key (encrypted with the user's key).
4. Client now holds a TGT it can present for up to (by default) 10 hours without re-entering credentials.
5. TGS-REQ - when the client wants to access a service (e.g. a SQL server), it sends the TGT plus the SPN of the target service to the TGS.
6. KDC checks the TGT is valid (decrypts using `krbtgt` hash), then issues a service ticket **encrypted with the target service account's password hash** (NTLM hash of that account, derived via RC4 or AES depending on config).
7. TGS-REP - client receives the service ticket.
8. AP-REQ - client presents the service ticket directly to the target service (e.g. the SQL server). The service decrypts it using its own password hash to validate the client's identity — no contact with the DC needed for this last step.

```text
Client                     KDC (AS)                    KDC (TGS)                Service
  |--- AS-REQ (encrypted timestamp) ------->|
  |<-- AS-REP (TGT + session key) ----------|
  |
  |--- TGS-REQ (TGT + SPN) ----------------------------->|
  |<-- TGS-REP (service ticket, enc w/ svc hash) --------|
  |
  |--- AP-REQ (service ticket) ------------------------------------------->|
  |<-- authenticated access -----------------------------------------------|
```

--> The critical fact that enables Kerberoasting: **the service ticket is encrypted with the target service account's own password hash**, and the KDC will hand that ticket to *any* authenticated domain user who asks for it — no special privilege required. That means any valid domain credential (even a low-privilege one) can request tickets for any SPN and attempt to crack them offline.

## Kerberoasting

--> Kerberoasting abuses the fact that any domain user can request a TGS ticket for any service with a registered SPN, and that ticket is encrypted with the service account's password hash.

--> Attack logic:
1. Enumerate accounts with an SPN set (typically service accounts running SQL Server, IIS APP pools, etc. — human admins rarely need SPNs, so SPN accounts are juicy targets, and they're often configured with weak/old passwords that never expire).
2. Request a TGS service ticket for each SPN using your own (even unprivileged) domain credentials.
3. Extract the encrypted portion of the ticket (the part encrypted with the service account's hash) and take it offline.
4. Crack it offline with hashcat/John — no lockout policy applies because you're not talking to the DC anymore, you're brute-forcing locally.
5. If cracked, you now have that service account's plaintext password — often with additional access (e.g. local admin on a SQL box, or itself a privileged account).

```bash
# Enumerate SPNs with Impacket (from a Linux attack box, with valid creds)
GetUserSPNs.py corp.local/alice:'Password123' -dc-ip 10.10.10.5 -request
# -request flag also pulls the crackable ticket hash (format: $krb5tgs$23$*...)

# Or with PowerView (from a Windows host)
Get-DomainUser -SPN | Select samaccountname,serviceprincipalname

# Save the extracted hash and crack offline
hashcat -m 13100 spn_hashes.txt rockyou.txt   # mode 13100 = Kerberos 5 TGS-REP etype 23
john --format=krb5tgs --wordlist=rockyou.txt spn_hashes.txt
```

--> Mitigations: use long random passwords (25+ chars) for service accounts, use Group Managed Service Accounts (gMSA) which rotate passwords automatically, enforce AES-only Kerberos encryption (RC4 tickets are far weaker to crack), and monitor for abnormal volumes of TGS-REQ events (Event ID 4769).

## AS-REProasting

--> AS-REProasting targets accounts that have **"Do not require Kerberos preauthentication"** enabled (a misconfiguration, or sometimes legacy/compat setting).

--> Recall step 2 of the Kerberos flow: normally the KDC requires you to prove you know the password *before* issuing a TGT (the encrypted timestamp). If pre-auth is disabled for an account, an attacker can send an AS-REQ for that username with **no proof of knowledge at all**, and the KDC will happily reply with an AS-REP that contains data encrypted with that account's password hash. That's now crackable offline exactly like a Kerberoast hash — and unlike Kerberoasting, you don't even need valid domain credentials to start (just valid usernames), because AS-REQ doesn't require prior authentication when pre-auth is off.

```bash
# Enumerate accounts without pre-auth and grab AS-REP hashes with Impacket
GetNPUsers.py corp.local/ -usersfile users.txt -no-pass -dc-ip 10.10.10.5

# Crack the AS-REP hash ($krb5asrep$23$...)
hashcat -m 18200 asrep_hashes.txt rockyou.txt
```

--> Mitigation: leave pre-authentication enabled (it's off by default in modern AD; only disabled deliberately or via legacy misconfig) and audit for accounts with the `DONT_REQ_PREAUTH` UAC flag set.

## Pass-the-Hash (PtH)

--> Pass-the-Hash exploits NTLM authentication, which does not require the plaintext password — only the NTLM hash. If you dump an NTLM hash (e.g. from `SAM`, `lsass.exe` memory, or NTDS.dit), you can authenticate as that user/computer **without ever cracking the hash**, by passing it directly to the authentication protocol.

--> Why it works: NTLM auth is a challenge-response scheme where the client proves knowledge of the password by computing a response derived from the NTLM hash — not the plaintext. If you already have the hash, you can compute that response yourself; cracking is unnecessary.

```bash
# Pass-the-Hash with Impacket to get a shell via SMB
psexec.py -hashes :<ntlm_hash> Administrator@10.10.10.5

# Or with CrackMapExec to spray a hash across many hosts
crackmapexec smb 10.10.10.0/24 -u Administrator -H <ntlm_hash>

# Mimikatz (on a compromised Windows host, local admin) — pass-the-hash to spawn a process as another user
sekurlsa::pth /user:Administrator /domain:corp.local /ntlm:<ntlm_hash> /run:cmd.exe
```

--> Impact: PtH is why lateral movement in a flat network is so dangerous — one local admin hash reused across many machines (a common sysadmin habit) lets an attacker pivot host to host with a single dumped hash.

--> Mitigation: enable Credential Guard, restrict local admin reuse (LAPS — unique random local admin password per machine), enforce the "Protected Users" group and Restricted Admin mode for RDP, disable NTLM where possible in favor of Kerberos-only.

## Pass-the-Ticket (PtT)

--> Pass-the-Ticket is the Kerberos analog of PtH: instead of an NTLM hash, you steal a Kerberos ticket (TGT or service ticket) from memory and inject it into your own session to impersonate that identity, without ever knowing the password or hash.

```bash
# Dump tickets from LSASS memory with Mimikatz
sekurlsa::tickets /export

# Inject a stolen TGT (.kirbi file) into the current logon session
kerberos::ptt stolen_ticket.kirbi

# Rubeus equivalent (dump + pass in one tool)
Rubeus.exe dump /service:krbtgt
Rubeus.exe ptt /ticket:base64ticketblob
```

--> Because the ticket itself carries the authorization data, this is extremely powerful when the stolen ticket belongs to a Domain Admin who happened to be logged into the box you compromised.

## Golden Ticket and Silver Ticket

--> These are forged-ticket attacks that go a level further than stealing an existing ticket — they *manufacture* a valid one from scratch, provided you already have the right key material (meaning: this is post-domain-compromise persistence, not an initial-access technique).

1. Golden Ticket - forge a TGT by using the **`krbtgt` account's password hash** (the KDC's own key, obtained via a DCSync or NTDS.dit dump after compromising a DC). Because the TGT is encrypted with `krbtgt`'s hash and the KDC trusts anything encrypted with it, you can mint a TGT for *any* user (including one that doesn't exist) with *any* group memberships (e.g. Domain Admins), valid for up to 10 years by default. This is the strongest AD persistence technique — it survives password resets of individual accounts (only rotating `krbtgt`'s password twice, invalidating existing tickets, fixes it).
2. Silver Ticket - forge a *service* ticket directly using a specific **service account's** password hash (not `krbtgt`). This is more limited in scope (only grants access to that one service) but doesn't touch the KDC at all in the process (the ticket is presented straight to the service via AP-REQ), which can be stealthier since there's no TGS-REQ event logged on the DC.

```bash
# Golden Ticket with Mimikatz (needs krbtgt hash + domain SID, obtained after DA compromise)
lsadump::dcsync /domain:corp.local /user:krbtgt
kerberos::golden /user:fakeadmin /domain:corp.local /sid:S-1-5-21-... /krbtgt:<krbtgt_ntlm_hash> /ptt

# Rubeus equivalent
Rubeus.exe golden /user:fakeadmin /domain:corp.local /sid:S-1-5-21-... /krbtgt:<hash> /ptt
```

--> Detection/mitigation: monitor for TGTs with abnormal lifetimes or for usernames that don't exist in AD, rotate the `krbtgt` password twice on suspicion of compromise, use Microsoft Defender for Identity / similar to flag forged-ticket anomalies.

## Tooling Overview (Conceptual)

--> You don't need to memorize every flag — understand what each tool is *for*:

- Mimikatz - the classic Windows post-exploitation credential tool. Dumps plaintext passwords/hashes/tickets from LSASS memory, performs Pass-the-Hash, Pass-the-Ticket, DCSync, and Golden/Silver Ticket forging. Requires local admin (or SYSTEM) on the target host.
- Rubeus - a more modern, Kerberos-focused C# tool (part of the .NET/GhostPack tooling ecosystem). Specializes in requesting, forging, renewing, and injecting Kerberos tickets (Kerberoasting, AS-REProasting, Golden/Silver Tickets, ticket renewal attacks). Often preferred over Mimikatz for Kerberos-specific work because it's more actively maintained and easier to run in memory (less noisy to some AVs).
- Impacket - a Python library/toolset for working with Windows network protocols from Linux. Includes `GetUserSPNs.py` (Kerberoasting), `GetNPUsers.py` (AS-REProasting), `secretsdump.py` (dump hashes from SAM/NTDS.dit/LSA secrets, including remote DCSync), `psexec.py`/`wmiexec.py`/`smbexec.py` (remote command execution via PtH), and more. This is the go-to toolkit when attacking AD from a non-Windows box.
- CrackMapExec / NetExec - a "Swiss army knife" for spraying credentials/hashes across many hosts on a network at once, enumerating shares, and running commands — very useful once you have one valid credential and want to see where else it works.
- BloodHound - not an exploitation tool but a graph-based AD enumeration/visualization tool: it maps users, groups, ACLs, and trust relationships to reveal attack paths (e.g. "this low-priv user is 3 hops from Domain Admin via these three misconfigurations"). Almost always the first tool run after any initial AD foothold in a real engagement.

--> Putting it together: a typical AD attack chain in a lab is initial foothold to BloodHound enumeration to Kerberoasting/AS-REProasting for extra creds to Pass-the-Hash/Pass-the-Ticket for lateral movement to compromise a DC to DCSync/Golden Ticket for full persistence. Practicing each stage individually on a lab domain builds the intuition to chain them.
