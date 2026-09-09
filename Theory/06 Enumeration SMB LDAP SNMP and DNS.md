### Enumeration - SMB, LDAP, SNMP and DNS

--> ⚠️ LEGAL / ETHICAL REMINDER: Everything below is active interaction with a target's services — only run it against machines you own or have explicit written permission to test (TryHackMe/HackTheBox lab machines, Metasploitable2/3, your own AD lab, or an authorized client's explicitly in-scope hosts). Null-session and default-credential enumeration is exactly the kind of "low and slow" activity real IDS/SIEM rules are tuned to catch — treat it with the same seriousness as exploitation, not as "just scanning".

--> Enumeration is the process of actively querying a discovered service to extract detailed, actionable information about it — usernames, shares, groups, domain structure, running processes, software versions — rather than just knowing "port X is open".
--> It sits between Scanning (note 03) and Gaining Access (notes 04/05) in the methodology: nmap tells you WHAT service is listening, enumeration tells you enough about HOW it's configured and WHO uses it to actually plan an attack (a password to try, a share to read, a username to brute-force against).
--> The reason it's a distinct phase and not just "more scanning": each of these protocols (SMB, LDAP, SNMP, DNS) has its OWN query language and its own tool ecosystem — nmap's NSE scripts give you a taste (`-sC`), but dedicated enumeration tools go far deeper.

## Why Enumeration Matters So Much in Internal/AD Assessments

--> On external web engagements, OWASP-style vulnerabilities (note 04) dominate. On internal networks and Active Directory environments, enumeration IS most of the engagement — AD is enormously information-rich (every user, group, computer, trust relationship, and permission is queryable by design, because that's literally what a directory service is for).
--> A very large fraction of real-world AD compromises follow this exact chain: anonymous/null enumeration reveals a username list → password spraying (note 07) against that list finds one weak/reused password → that low-priv foothold is enough to enumerate further (now authenticated) → privilege escalation or lateral movement follows.

## SMB Enumeration

--> SMB (Server Message Block, ports 139/445) is Windows' native file/printer-sharing protocol; Samba is the Linux/Unix implementation. It's arguably the single richest enumeration target on an internal Windows network — shares, users, groups, OS version, password policy, and sometimes even domain admin group membership can all be pulled from it, sometimes without ANY credentials at all.

==> Null Sessions
--> A "null session" is an anonymous, unauthenticated connection to SMB (`username=""`, `password=""`). Older Windows versions and misconfigured modern ones allow a surprising amount of enumeration this way. This is the very first thing to check on any box with 445 open.

```bash
smbclient -L //192.168.56.101/ -N       # -L lists shares, -N = no password (null session)
```
```text
        Sharename       Type      Comment
        ---------       ----      -------
        print$          Disk      Printer Drivers
        tmp             Disk      oh noes!
        opt             Disk
        IPC$            IPC       IPC Service (Metasploitable...)
        ADMIN$          IPC       IPC Service (Metasploitable...)
```
--> Look for: any share besides the default administrative ones (`ADMIN$`, `C$`, `IPC$`, `print$`) — a share like `tmp` or a company-named share is worth immediately trying to mount.

```bash
smbclient //192.168.56.101/tmp -N       # connect to a specific share anonymously
smb: \> ls                               # list files once connected
smb: \> get secret.txt                   # download a file of interest
smb: \> mget *                           # download everything in the current directory
```

==> enum4linux / enum4linux-ng
--> `enum4linux` (and its actively maintained rewrite `enum4linux-ng`) is the classic all-in-one SMB/Samba enumeration wrapper — it automates dozens of individual `rpcclient`/`net`/`smbclient` queries into one run.
```bash
enum4linux -a 192.168.56.101            # -a = "all" enumeration checks (the one you'll use 95% of the time)
```
--> What to scan the output for:
1. Workgroup/domain name and OS version banner (confirms exact target build).
2. `Users on 192.168.56.101` — a full username list. Even without passwords, this is gold for later password spraying (note 07) — you've turned "unknown target" into "known set of valid account names".
3. Password policy (`Minimum password length`, `Lockout threshold`) — directly informs whether brute force is even viable or will lock out accounts after N attempts.
4. Share list with permissions — same info as `smbclient -L` but combined with per-share READ/WRITE access checks.
5. Group memberships, especially anyone in `Domain Admins`.

```bash
enum4linux-ng -A 192.168.56.101 -oY results.yaml   # newer tool, structured YAML output, easier to parse/automate
```

==> smbmap
--> `smbmap` focuses specifically on mapping out SHARE PERMISSIONS quickly, including recursive directory listing — often faster to read than enum4linux's wall of text when shares are what you care about.
```bash
smbmap -H 192.168.56.101 -u '' -p ''     # anonymous/null session share listing
smbmap -H 192.168.56.101 -u user -p pass  # authenticated, once you have creds
```
```text
[+] IP: 192.168.56.101:445     Name: 192.168.56.101
    Disk                    Permissions     Comment
    ----                    -----------     -------
    print$                  NO ACCESS
    tmp                     READ, WRITE     oh noes!
    opt                     READ ONLY
```
--> `READ, WRITE` is the finding you want — a writable share is a candidate for dropping a malicious file (a `.lnk`, a macro doc, a webshell if the share backs a web root) that gets executed by another user or process.
```bash
smbmap -H 192.168.56.101 -u '' -p '' -R tmp     # recursively list a specific share's directory tree
smbmap -H 192.168.56.101 -u '' -p '' --download 'tmp\secret.txt'   # pull a specific file
```

==> rpcclient — Manual Deep Dive
--> When automated tools are inconclusive, `rpcclient` gives raw access to the underlying MSRPC calls enum4linux wraps.
```bash
rpcclient -U "" -N 192.168.56.101       # connect with null session
rpcclient $> enumdomusers               # list domain users directly
rpcclient $> queryuser 0x3e8             # query details for a specific RID (user identifier, here 1000 in hex)
rpcclient $> enumdomgroups               # list domain groups
rpcclient $> querygroup 0x200             # e.g. query "Domain Admins" (RID 512 = 0x200)
```
--> RIDs (Relative Identifiers) are predictable — well-known RIDs like 500 (built-in Administrator) and 512 (Domain Admins group) let you enumerate a specific account of interest even if the general listing is blocked, via "RID cycling" (`querydispinfo` / brute-forcing sequential RIDs).

## LDAP Enumeration (Active Directory)

--> LDAP (Lightweight Directory Access Protocol, ports 389/636 for LDAPS) is the query protocol Active Directory is built on. Every user, group, computer object, organizational unit, and (critically) many security-relevant attributes live in the LDAP directory tree and are queryable with the right filter — sometimes anonymously.

==> ldapsearch Basics
```bash
ldapsearch -x -H ldap://192.168.56.102 -s base namingcontexts
# -x = simple authentication, -s base = search only the base entry, "namingcontexts" attribute reveals the domain's Distinguished Name (DN)
```
```text
namingcontexts: DC=corp,DC=local
```
--> That `DC=corp,DC=local` is the domain's base DN — every subsequent query is anchored under it.

```bash
# Anonymous bind - many misconfigured DCs still allow this
ldapsearch -x -H ldap://192.168.56.102 -b "DC=corp,DC=local" -D "" -w ""
```
--> `-b` sets the search base, `-D ""` / `-w ""` = anonymous bind (empty distinguished name and password). If this returns results without a real "invalid credentials" error, anonymous LDAP read is enabled — a serious misconfiguration.

==> Authenticated Queries With a Valid Foothold Account
--> Once you have ANY valid domain credential (even a low-priv one from password spraying), LDAP queries become far more productive.
```bash
ldapsearch -x -H ldap://192.168.56.102 -D "corp\jsmith" -w 'Password123!' \
  -b "DC=corp,DC=local" "(objectClass=user)" sAMAccountName
```
--> Common filters worth memorizing:
1. `(objectClass=user)` — every user object.
2. `(objectClass=computer)` — every domain-joined machine (great for building a target list).
3. `(objectClass=group)` — every group, to map privilege structure.
4. `(&(objectCategory=person)(objectClass=user)(adminCount=1))` — accounts that are or have ever been privileged (adminCount gets set to 1 and often stays that way even after removal from a privileged group — a nice "who used to be an admin" trail).
5. `(userAccountControl:1.2.840.113556.1.4.803:=2)` — disabled accounts (bitwise filter on the UAC flag).
6. `servicePrincipalName=*` — accounts with an SPN set, i.e. service accounts — these are the targets for "Kerberoasting" (requesting their Kerberos service ticket, which is encrypted with the account's password hash, then cracking it offline — an advanced AD attack worth knowing exists even before you study it in depth).

--> In practice most people reach for a friendlier wrapper over raw `ldapsearch` once inside an AD lab:
```bash
# ldapdomaindump - produces clean HTML/JSON reports of users, groups, computers, policy
ldapdomaindump -u 'corp.local\jsmith' -p 'Password123!' 192.168.56.102

# windapsearch - convenience wrapper for common AD-specific LDAP queries
python3 windapsearch.py -d corp.local -u jsmith -p 'Password123!' --da    # --da = enumerate Domain Admins directly
```
--> What to look for in any LDAP dump: password policy (min length, lockout threshold — same reasoning as SMB), which OUs (Organizational Units) exist (reveals org structure, e.g. `OU=Finance`, `OU=ServiceAccounts`), any user object with a `description` field containing a plaintext password (embarrassingly common), and Kerberoastable service accounts.

## SNMP Enumeration

--> SNMP (Simple Network Management Protocol, UDP port 161) is used to monitor and manage network devices (routers, switches, printers, sometimes servers). It authenticates with a "community string" instead of a username/password, and shockingly often still uses the vendor DEFAULT strings.
--> Default community strings to always try: `public` (read-only, by far the most common default) and `private` (read-write — if this works, you may be able to reconfigure the device, not just read from it).

```bash
# Confirm SNMP is even open/responding first (recall note 03's UDP scan)
sudo nmap -sU -p161 --open 192.168.56.101

snmpwalk -c public -v1 192.168.56.101              # walk the entire MIB tree with community string "public", SNMPv1
snmpwalk -c public -v2c 192.168.56.101              # try v2c as well - some devices only respond to one version
```
```text
SNMPv2-MIB::sysDescr.0 = STRING: Linux metasploitable 2.6.24-16-server ...
SNMPv2-MIB::sysContact.0 = STRING: admin@example.com
SNMPv2-MIB::sysName.0 = STRING: metasploitable
IF-MIB::ifDescr.1 = STRING: lo
HOST-RESOURCES-MIB::hrSWRunName.1 = STRING: "init"
HOST-RESOURCES-MIB::hrSWRunName.2 = STRING: "sshd"
```
--> What to mine from the walk: `sysDescr` (exact OS/kernel version — feeds version-based CVE lookup), `hrSWRunName` entries (every running process — reveals installed software, backup agents, AV product, custom scripts), and on network gear specifically, `ifDescr`/routing tables (reveals internal network topology beyond what you can see from your current vantage point).

```bash
# Targeted queries instead of a full walk, once you know the OID you want
snmpget -c public -v2c 192.168.56.101 1.3.6.1.2.1.1.1.0     # sysDescr specifically
snmpwalk -c public -v2c 192.168.56.101 1.3.6.1.2.1.25.4.2.1.2   # hrSWRunName subtree - running processes only

# Brute-forcing the community string itself if "public"/"private" fail
onesixtyone -c community_strings.txt 192.168.56.101
```
--> On Windows targets specifically, SNMP (if enabled via the legacy SNMP service) can leak an enormous amount via the Windows-specific MIB extensions — installed software list, share names, even local usernames — `snmp-check` is a good purpose-built tool for that:
```bash
snmp-check 192.168.56.101 -c public
```

## DNS Enumeration

--> DNS enumeration goes beyond note 03's basic `dig`/`nslookup` lookups — the goal here is to map an ENTIRE domain's subdomain/host footprint, since internal hostnames often reveal architecture (`vpn-`, `dev-`, `db-`, `backup-` prefixes are a map of what to target next).

==> Zone Transfers (AXFR) — the Best-Case Finding
```bash
dig axfr @ns1.example.com example.com
```
--> If the name server is misconfigured to allow transfers to anyone (should only be allowed to designated secondary DNS servers), this ONE command dumps every record in the zone — every subdomain, every internal IP, every mail/service record — in one shot. Always the very first thing to try.

==> dnsrecon — Automated Multi-Technique Enumeration
```bash
dnsrecon -d example.com -t axfr        # specifically attempt zone transfer against all NS records for the domain
dnsrecon -d example.com -t std          # standard enumeration: A/AAAA/MX/NS/TXT/SOA records
dnsrecon -d example.com -D subdomains.txt -t brt   # brute-force subdomains against a wordlist ("brt" = brute)
```
--> `dnsrecon` automatically tries zone transfer against EVERY name server it finds for the domain, tries common subdomain brute-forcing, and even attempts Google/Bing scraping for additional passive results — good default "run this first" tool.

==> dnsenum — Similar Coverage, Different Output Style
```bash
dnsenum --enum example.com
dnsenum -f subdomains-top1000.txt example.com   # supply your own wordlist for brute-force subdomain guessing
```

==> fierce — Lightweight, Good for Quick Internal Recon
```bash
fierce --domain example.com
```
--> `fierce` is deliberately simple/fast — good for a quick first pass before committing to `dnsrecon`'s heavier multi-technique run.

==> Reverse DNS and PTR Sweeps
```bash
dig -x 192.168.56.101                              # reverse lookup - IP to hostname
for ip in 192.168.56.{1..254}; do dig +short -x $ip; done   # sweep a whole subnet for PTR records
```
--> Reverse DNS across an internal subnet is an easy way to build a hostname-to-IP map without touching every host individually — hostnames like `DC01`, `SQL-PROD-02`, `FILESRV01` immediately tell you which box is the domain controller, which is the database, etc.

## Putting It Together — Enumeration Feeding the Next Phase

--> The output of this entire phase should be a concrete list, not vague impressions:
1. A username list (from SMB `enumdomusers`, LDAP `sAMAccountName` dumps, or SNMP process owners) → feeds password spraying (note 07).
2. A password policy (lockout threshold, min length) → tells you HOW to spray safely without locking accounts out.
3. A share/directory map with permission levels → tells you exactly where to look for sensitive files or where to drop a payload.
4. A hostname/subdomain/IP map (DNS + reverse DNS) → tells you what else exists on the network worth scanning next (loop back to note 03 against newly discovered hosts).
5. Exact software/OS versions (SNMP `sysDescr`, SMB OS banner) → feeds `searchsploit`/CVE lookup exactly as in note 03's nmap version-detection workflow.

--> This closes the gap between note 03 (Scanning) and notes 07-09 (Password Attacks, Linux/Windows Privilege Escalation) — enumeration is what turns "these ports are open" into "here is a specific, testable attack plan".
