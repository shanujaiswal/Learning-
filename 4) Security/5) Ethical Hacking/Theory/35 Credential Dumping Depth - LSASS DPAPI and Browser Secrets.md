### Credential Dumping Depth - LSASS, DPAPI, and Browser Secrets

--> LEGAL/ETHICAL REMINDER: everything below is for authorized environments only - your own lab (e.g. a self-hosted AD lab, GOAD, HackTheBox/TryHackMe AD boxes) or engagements with signed written permission. Dumping credentials from a real host without authorization is a serious crime (Computer Misuse Act, CFAA, equivalents). Always have written scope/rules of engagement before touching a real system.

--> This note assumes you know note 10's brief Mimikatz/Pass-the-Hash coverage and note 09's Windows privilege escalation content. It goes deeper into HOW three major categories of local credential material actually get extracted.

## LSASS Memory - What's Really In There

--> `lsass.exe` (Local Security Authority Subsystem Service) is where Windows caches the credential material needed to support Single Sign-On - so a user doesn't have to re-enter a password every time they access a new resource. Depending on configuration and logon type, this can include: NTLM hashes of logged-on accounts, cached Kerberos tickets (TGTs and service tickets), and - only if the legacy `wdigest` provider is enabled (default was ON through Windows 8.1/Server 2012, off by default since, but plenty of environments never explicitly disable it) - REVERSIBLY ENCRYPTED PLAINTEXT passwords, because WDigest authentication requires the plaintext to compute its challenge response.
--> `sekurlsa::logonpasswords` (Mimikatz) reads this directly out of live LSASS memory via `OpenProcess`/`ReadProcessMemory` calls - which is exactly the API pattern modern EDR products hook and alert on heavily, making a direct live Mimikatz run against LSASS one of the noisiest, most readily-detected actions in AD post-exploitation today.

```powershell
# Stealthier alternative: dump the LSASS process to a file first, using a
# built-in Windows mechanism rather than an attacker tool directly touching
# LSASS with a flagged API call pattern
rundll32.exe C:\windows\System32\comsvcs.dll, MiniDump <lsass_pid> C:\temp\lsass.dmp full

# Then parse the dump file OFFLINE, away from the live process entirely
mimikatz.exe "sekurlsa::minidump C:\temp\lsass.dmp" "sekurlsa::logonpasswords" exit
```

--> Why the dump-then-parse-offline approach evades some detections: many EDR hooks specifically watch `OpenProcess`/`ReadProcessMemory` calls made AGAINST lsass.exe by unusual processes. `comsvcs.dll`'s `MiniDump` export is a legitimate, signed Windows component (originally intended for COM+ crash dumps) being repurposed - the process handle/memory-read pattern still occurs, but coming from a well-known signed DLL invoked via `rundll32` looks more like normal system activity to some detection logic, and crucially the actual credential PARSING happens later, offline, against a static file with no live LSASS interaction at all at that stage.
--> Credential Guard is Microsoft's structural fix: it moves the most sensitive secrets (the NTLM hash cache, Kerberos TGT decryption keys) into a VBS (Virtualization-Based Security) isolated container - a mini hypervisor-protected environment that even a SYSTEM-level process on the host OS cannot read into, because the secrets never live in the "normal" LSASS address space at all. Where Credential Guard is enabled and the account isn't excluded, `sekurlsa::logonpasswords`-style dumping simply doesn't recover those specific secrets even with full SYSTEM access.

## DPAPI - The Secret Behind "Windows Just Remembers It"

--> DPAPI (Data Protection API) is the underlying encryption mechanism behind a huge range of "Windows just remembers this for me" features: saved RDP credentials, saved Wi-Fi keys, EFS (Encrypting File System) keys, and - historically, before Chromium moved to a newer scheme - saved browser passwords (see the Browser Secrets section below, which still relies on DPAPI as its base layer).
--> Mechanically: DPAPI derives an encryption key from the user's LOGON PASSWORD (via a "master key" stored under `%APPDATA%\Microsoft\Protect\<user SID>\`), meaning secrets protected this way are, by design, decryptable only in the context of that specific user being logged on (or holding their password/NTLM hash) - which is exactly the property that makes it attractive for storing saved credentials safely at rest without a separate password prompt every time.
--> The abuse chain has two forms: (a) with the user's actual logon PASSWORD (or an unlocked live session as that user), you can decrypt their own DPAPI blobs directly; (b) more powerfully, every domain has a DOMAIN BACKUP KEY held by Domain Admins, which can decrypt ANY user's master key domain-wide, REGARDLESS of their current password - meaning a Domain Admin (or an attacker who has reached DA) can retroactively decrypt every DPAPI-protected secret for every user in the domain, even ones whose passwords have since changed.

```bash
# Mimikatz dpapi:: module - decrypt a specific user's masterkey using the
# domain backup key (requires DA-equivalent access to obtain the backup key first)
lsadump::backupkeys /system:dc01.corp.local /export

# Then decrypt a target user's DPAPI blob offline with the exported backup key
dpapi::masterkey /in:"C:\Users\jsmith\AppData\Roaming\Microsoft\Protect\S-1-5-21-...\<guid>" /pvk:backupkey.pvk

# SharpDPAPI automates the enumerate-then-decrypt workflow for a whole host
SharpDPAPI.exe machinetriage
```

## Browser-Stored Credentials

--> Chromium-based browsers (Chrome, Edge) store saved website logins in a local SQLite database (`Login Data`, found under the browser's profile directory) where the actual password VALUES are encrypted - and on Windows, that encryption is DPAPI, tied to the logged-on user exactly as described above. This means an attacker with local code execution in the context of the logged-on user can decrypt every saved browser password WITHOUT touching LSASS at all - it's purely a DPAPI operation against a SQLite file, using the same domain-backup-key escalation path if the user's own session/password isn't directly available.
--> Firefox uses its own separate encryption scheme (not DPAPI) with an optional master password, but the same general principle applies: local user-context access is usually sufficient to recover saved logins unless a strong separate master password protects the profile.
--> Practically, this makes "dump the browser's saved passwords" a near-zero-effort, very-high-value step on any host where you've landed as the logged-on user - it frequently yields plaintext credentials to other systems (webmail, internal portals, cloud consoles) that never touch AD authentication at all.

## Mitigations

--> 1. Enable Credential Guard on all supported endpoints - the single most impactful structural fix against LSASS-based credential theft.
--> 2. Disable the WDigest provider explicitly (`UseLogonCredential` registry value set to 0) so no reversible plaintext ever lands in LSASS memory in the first place.
--> 3. Deploy LAPS (Local Administrator Password Solution) so local admin credentials are unique per machine and rotated - limiting the blast radius of any single host's credential dump.
--> 4. Discourage/disable browser-native password storage in enterprise environments via GPO/policy, pushing users toward a managed password manager instead.
--> 5. Monitor for LSASS access from non-standard processes (Sysmon Event ID 10 with `TargetImage` = lsass.exe, filtering for GrantedAccess values and calling processes outside the expected allowlist) and for `comsvcs.dll`/`rundll32` MiniDump invocations specifically, since that combination is a well-known signature at this point despite being "living off the land."

--> Putting it together: LSASS, DPAPI, and browser-secret extraction are usually run as a matched set immediately after landing interactively (or via a beacon) on any new host during lateral movement (note 13) - each targets a different storage mechanism, but all three convert "I have code execution as this user" into "I now hold credentials for OTHER systems," which is what actually drives lateral movement forward rather than the initial foothold itself.
