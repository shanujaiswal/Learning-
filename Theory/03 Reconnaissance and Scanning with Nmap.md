### Reconnaissance and Scanning with Nmap

--> ⚠️ LEGAL / ETHICAL REMINDER: Scanning a system — even just a port scan with no exploitation — without authorization is illegal in most jurisdictions (it's "unauthorized access" attempt under laws like India's IT Act Section 43, or the US CFAA). Only run anything in this note against systems you own or have written permission to test (TryHackMe/HackTheBox boxes, your own Metasploitable/DVWA VMs, or an authorized client's explicitly in-scope IP range).

--> Reconnaissance ("recon") is Phase 1 of the pentest methodology (see note 01) — gathering as much information as possible about a target before touching it aggressively.
--> Scanning is Phase 2 — actively probing the target to map out what's actually running.

## Passive vs Active Recon

==> Passive Recon
--> You gather information WITHOUT directly interacting with the target's systems. The target has no way of knowing you're looking.
--> Sources: public records, search engines, social media, code repositories, cached pages.
--> Zero risk of detection, zero risk of "breaking" anything, but limited depth.

==> Active Recon
--> You directly interact with the target's systems (visiting their website, pinging their server, port scanning).
--> The target COULD see this in firewall logs, IDS alerts, or web server access logs.
--> More detailed information, but leaves a trace — this is why real engagements agree in advance on what active recon is allowed (see Rules of Engagement, note 01).

## WHOIS Lookups

--> WHOIS is a protocol/database that stores registration information about a domain name: who registered it, registrar, creation/expiry date, and (increasingly, redacted for privacy) name servers and contact info.
```bash
whois example.com
```
--> Useful fields to look for in the output: `Registrant Organization`, `Name Server`, `Creation Date`, `Registrar`. This can reveal the hosting provider or reveal that a domain is about to expire (interesting for takeover attacks, though that's a separate advanced topic).

## DNS Lookups

--> DNS (Domain Name System) translates domain names to IP addresses, and stores several other useful record types.
```bash
nslookup example.com                  # basic: get the IP address of a domain
dig example.com                        # more detailed/flexible DNS query tool
dig example.com MX                     # find mail servers
dig example.com NS                     # find name servers
dig example.com ANY                    # try to get all record types (many servers now block this)
dig axfr @ns1.example.com example.com  # attempt a DNS zone transfer (usually blocked, but if allowed, leaks EVERY subdomain)
```
--> Record types to know: `A` (IPv4 address), `AAAA` (IPv6 address), `MX` (mail server), `NS` (name server), `TXT` (arbitrary text, often SPF/verification records), `CNAME` (alias to another domain).
--> A successful zone transfer (`AXFR`) is a classic misconfiguration finding — it dumps the entire DNS zone, revealing internal hostnames like `vpn.example.com`, `dev.example.com`, `staging.example.com` that weren't publicly linked anywhere.

## Google Dorking Basics

--> "Google dorking" (or "Google hacking") means using advanced search engine operators to find information that was accidentally indexed publicly.
--> ⚠️ Caution: dorking against a target only counts as authorized recon if that target is explicitly in your scope. Idly dorking random companies "just to see" is not authorized activity, even though you're only using Google.

```text
site:example.com filetype:pdf                  # find PDFs hosted on example.com
site:example.com inurl:admin                    # find URLs containing "admin" on that domain
site:example.com intitle:"index of"              # find exposed directory listings
"example.com" filetype:sql                       # find leaked SQL dump files mentioning the domain
site:pastebin.com "example.com" password         # find leaked credentials pasted publicly
```
--> Common operators: `site:` restricts to a domain, `filetype:` restricts to a file extension, `inurl:` searches the URL text, `intitle:` searches the page title, `intext:` searches page body text.
--> There's a public archive called the "Google Hacking Database" (GHDB, part of Exploit-DB) that catalogs known-useful dork strings — good to be aware it exists, worth browsing once you're comfortable with the basics.

## Nmap — The Core Scanning Tool

--> Nmap ("Network Mapper") is the industry-standard tool for host discovery and port scanning. Almost every pentest starts with nmap.

### Host Discovery

--> Before scanning ports, you often want to know which hosts in a range are even alive.
```bash
nmap -sn 192.168.56.0/24     # "ping scan" — discover live hosts in a /24, do NOT port scan them
nmap -sn 192.168.56.1-50     # scan a specific IP range instead of full CIDR
```
--> `-sn` = "no port scan", just host discovery (sends ICMP echo, TCP SYN to port 443, TCP ACK to port 80, and ICMP timestamp requests — it's smarter than a plain ping).

### Port Scan Types

--> The flag you choose changes HOW nmap probes each port, which changes speed, stealth, and required privileges.

1. `-sS` (TCP SYN scan / "half-open scan") – sends a SYN packet, if it gets SYN-ACK back the port is open, then nmap sends RST instead of completing the handshake. Fast, relatively stealthy (never completes a full connection), default scan type, requires root/admin privileges.
```bash
sudo nmap -sS 192.168.56.101
```

2. `-sT` (TCP Connect scan) – completes the full 3-way handshake (uses the OS's normal `connect()` system call). Slower, more "visible" (fully logged as a real connection by the target), but doesn't require root privileges.
```bash
nmap -sT 192.168.56.101
```

3. `-sU` (UDP scan) – scans UDP ports instead of TCP. Much slower because closed UDP ports often don't respond at all, so nmap has to wait for timeouts. Important for finding DNS(53), SNMP(161), DHCP services.
```bash
sudo nmap -sU --top-ports 20 192.168.56.101   # scanning all 65535 UDP ports is painfully slow, so limit to common ones
```

4. `-sV` (Version detection) – after finding open ports, nmap tries to determine the exact service AND version running (e.g. "Apache httpd 2.4.49" not just "port 80 open"). Critical — exact versions let you search for matching CVEs.
```bash
nmap -sV 192.168.56.101
```

5. `-sC` (Default script scan) – runs a safe default set of NSE (Nmap Scripting Engine) scripts against open ports (e.g. grabs banners, checks for anonymous FTP login, lists SMB shares).
```bash
nmap -sC 192.168.56.101
```

6. `-A` (Aggressive scan) – shortcut that combines `-sV` + `-sC` + `-O` (OS detection) + traceroute. Convenient but noisy and slower — great for lab practice, less ideal for a truly stealthy real engagement.
```bash
sudo nmap -A 192.168.56.101
```

7. `-O` (OS detection) – fingerprints the target's TCP/IP stack behavior to guess the operating system and version. Requires root, requires at least one open and one closed port to be reliable.
```bash
sudo nmap -O 192.168.56.101
```

--> Most common real-world combo you'll actually type constantly:
```bash
sudo nmap -sS -sV -sC -p- 192.168.56.101 -oN full_scan.txt
```
--> `-p-` means scan ALL 65535 ports (default nmap only scans the top 1000 most common ports — fine for a quick look, but a full scan can reveal services hiding on unusual ports).

### Timing and Stealth Flags

--> Nmap has 6 built-in timing templates, `-T0` (paranoid/slowest, tries to avoid IDS) through `-T5` (insane/fastest, very noisy).
```bash
nmap -T2 192.168.56.101     # "polite" — slower, less likely to overload the target or trip alarms
nmap -T4 192.168.56.101     # "aggressive" — commonly used on lab/CTF boxes where stealth doesn't matter, much faster
```
--> Other useful flags:
```bash
nmap -f 192.168.56.101              # fragment packets — attempt to evade simple packet-filtering firewalls/IDS
nmap -D RND:5 192.168.56.101         # decoy scan — spoof 5 random decoy source IPs to obscure which one is the real attacker
nmap --source-port 53 192.168.56.101 # spoof the source port (e.g. as DNS, port 53) to slip past dumb firewall rules
nmap -Pn 192.168.56.101              # skip host discovery entirely, assume host is up (useful if ICMP is blocked but the host is actually alive)
```

### Output Formats

--> Always save your scan output during a real engagement — you'll need it for the report, and re-running scans wastes time and generates extra noise.
```bash
nmap -sV 192.168.56.101 -oN scan.txt      # Normal format (human readable, what you see in the terminal)
nmap -sV 192.168.56.101 -oX scan.xml      # XML format (machine parseable, feeds into other tools)
nmap -sV 192.168.56.101 -oG scan.grep     # Grepable format (easy to pipe into grep/awk for quick filtering)
nmap -sV 192.168.56.101 -oA scan_all      # ALL three formats at once, using scan_all.nmap / .xml / .gnmap as filenames
```

### Interpreting Nmap Results — Planning the Next Step

--> A typical result looks like:
```text
PORT     STATE SERVICE     VERSION
21/tcp   open  ftp         vsftpd 2.3.4
22/tcp   open  ssh         OpenSSH 4.7p1 Debian 8ubuntu1
80/tcp   open  http        Apache httpd 2.2.8
139/tcp  open  netbios-ssn Samba smbd 3.X
445/tcp  open  netbios-ssn Samba smbd 3.X
3306/tcp open  mysql       MySQL 5.0.51a-3ubuntu5
```
--> How to read this and plan Phase 3 (Gaining Access, note 05):
1. Port state matters: `open` = reachable and a service is listening. `closed` = reachable but nothing listening. `filtered` = a firewall is silently dropping probes (can't tell if open or closed).
2. `vsftpd 2.3.4` is a famous example — this EXACT version has a known backdoor vulnerability (CVE-2011-2523), searchable directly in Metasploit (`search vsftpd`). Version numbers are gold — always cross-reference them against exploit databases (Exploit-DB, `searchsploit`, Metasploit's `search` command).
3. Port 445 (Samba) — check for anonymous/null session access, list shares (`smbclient -L //192.168.56.101/ -N`), check for the Samba version against known CVEs (e.g. the infamous "usermap_script" backdoor in some Samba 3.x versions).
4. Port 3306 (MySQL) exposed to the network at all is itself worth flagging — try connecting with common default/blank root credentials (only in an authorized lab, obviously).
5. Multiple old, unpatched services (Apache 2.2.8 from 2008, Samba 3.x) is a huge red flag pattern — this is literally the profile of Metasploitable2, the intentionally-vulnerable practice VM.

```bash
searchsploit vsftpd 2.3.4      # search the local Exploit-DB mirror for matching exploits by version string
```

--> Once nmap has told you WHAT is running and WHERE, you move from "Scanning" into "Gaining Access" — covered for web apps in note 04 and for Metasploit-based exploitation in note 05.
