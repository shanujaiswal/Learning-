### Ethical Hacking Fundamentals

--> ⚠️ LEGAL / ETHICAL REMINDER: Everything in this note (and in this entire vault) is meant to be practiced ONLY on systems you own, or systems you have explicit written permission to test (TryHackMe, HackTheBox, OverTheWire, picoCTF, DVWA, Metasploitable, your own home lab VMs). Scanning, attacking, or even "just looking around" on a system you don't own or don't have permission for is illegal in almost every country (in India: IT Act 2000, Sections 43 & 66). No exceptions, no "I was just curious".

--> Ethical hacking is the practice of legally attempting to break into computers, networks, or applications to find security weaknesses before a malicious attacker does.
--> The goal is not to cause damage — the goal is to find and report vulnerabilities so they can be fixed.
--> Ethical hackers use the exact same tools and techniques as malicious hackers, but under a legal agreement (a "contract" or "scope") with the owner of the system.

## Why Ethical Hacking Exists

--> Companies hire ethical hackers (also called penetration testers or pentesters) because:

1. It's cheaper to pay someone to find a bug than to deal with a data breach later.
2. Compliance requirements (PCI-DSS, ISO 27001, SOC2) often legally require regular security testing.
3. Automated scanners miss logic flaws — a human attacker thinks creatively, a scanner does not.
4. It builds customer trust ("we get pentested every year" is a selling point).

## Hat Terminology

--> "Hat" color is slang for what kind of hacker someone is, based on legality and intent.

==> White Hat
--> Hacks with permission, follows the law, reports findings responsibly.
--> Example: a pentester hired by a company, or a bug bounty hunter reporting through HackerOne/Bugcrowd.

==> Black Hat
--> Hacks without permission, for personal gain, damage, or malice.
--> Example: someone who breaks into a bank's database to steal card numbers and sell them.

==> Grey Hat
--> Sits in between — hacks without explicit permission, but usually doesn't have malicious intent (e.g. finds a bug and reports it anyway, without being asked).
--> Still illegal in most cases, even if "well intentioned". Reporting without permission does not make it legal.

==> Other terms you'll see
--> Script Kiddie – someone who runs tools/exploits written by others without understanding how they work.
--> Hacktivist – hacks to push a political or social agenda (e.g. defacing a website to protest something).
--> Red Team – a team that simulates a real-world attacker against an organization, usually stealthily, to test detection + response.
--> Blue Team – the defenders; the team that monitors, detects, and responds to attacks (SOC analysts, incident responders).
--> Purple Team – red + blue working together to improve both attack and defense simultaneously.

## Legal Basics — Never Skip This

--> "Authorization" is the single most important word in this entire field. Without it, ethical hacking becomes a crime, no matter your intent.

1. Written Permission (Authorization) – you must have a signed document (contract, letter of engagement, or in the case of bug bounty programs, the program's published policy) that says you are allowed to test specific systems.
2. Scope – the exact list of what you are allowed to touch. Example: "Only test app.example.com and api.example.com. Do NOT test the mail server or any third-party vendor systems."
3. Rules of Engagement (RoE) – the "how": allowed testing hours, allowed techniques (is social engineering allowed? is DoS testing allowed?), emergency contact if something breaks, how findings will be reported.
4. Get-out-of-jail letter – a short authorization letter you can produce if law enforcement or a confused sysadmin questions your activity during an engagement.

--> Practical rule of thumb: "If it's not explicitly in scope, don't touch it." When in doubt, ask the client, don't assume.

--> Legal frameworks to be aware of (names only, not deep law study):
- India: Information Technology Act, 2000 (Sections 43, 66, 66C, 66D, 43A).
- USA: Computer Fraud and Abuse Act (CFAA).
- UK: Computer Misuse Act 1990.
- International: most countries have an equivalent "unauthorized access" law.

## The Standard Penetration Testing Methodology

--> A pentest is not "just running Metasploit". It follows a repeatable methodology so nothing important is missed and results are reproducible.

--> The 6 stages, in order:

1. Reconnaissance (Recon) — gathering information about the target without necessarily touching it directly.
2. Scanning — actively probing the target to find live hosts, open ports, running services, and versions.
3. Gaining Access (Exploitation) — using a found vulnerability to actually break in (e.g. exploiting a vulnerable service, SQL injection, weak password).
4. Maintaining Access — once in, setting up a way to get back in later without repeating the exploit (backdoors, persistence). In a lab this proves "impact"; in a real engagement it's done carefully and cleaned up.
5. Covering Tracks — a real attacker deletes logs to hide. Ethical hackers document this step conceptually (to show the client what a real attacker could hide) but generally do NOT actually delete client logs, since the client needs those logs for their own investigation.
6. Reporting — writing up what was found, how it was found (step by step, reproducible), the severity/impact (using CVSS scores), and remediation advice. This is arguably the most important deliverable — a pentest with no report has no value to the client.

--> Memory trick: **R**econ → **S**can → **G**ain → **M**aintain → **C**over → **R**eport ("Real Spies Gather Many Covert Reports").

==> Reconnaissance in detail
--> Split into two types:
1. Passive recon – gathering info without directly interacting with the target (WHOIS records, public DNS records, LinkedIn, job postings, GitHub leaks, Google dorking). The target has no way to know you're looking.
2. Active recon – directly interacting with the target (visiting the website, pinging it, port scanning). The target COULD see this in their logs.

==> Scanning in detail
--> Find out: which hosts are alive, which ports are open, what service+version is running on each port, what OS is likely running.
--> Main tool: `nmap` (covered in depth in note 03).

==> Gaining Access in detail
--> This is where you actually exploit a weakness:
- A vulnerable service version (e.g. an outdated FTP server with a known CVE).
- A web app vulnerability (SQLi, XSS, broken auth — covered in note 04).
- Weak/default credentials (admin:admin, root:toor).
- Misconfigurations (anonymous FTP login, open SMB shares, exposed .git folders).

==> Maintaining Access in detail
--> After gaining a foothold, attackers often want persistence:
- Creating a new user account.
- Installing a backdoor/reverse shell that reconnects on reboot.
- Adding an SSH key to `~/.ssh/authorized_keys`.
--> In a real engagement this is done carefully and documented, then fully removed at the end (cleanup).

==> Covering Tracks in detail
--> Real attackers do this to avoid detection:
- Clearing bash history (`history -c`), clearing log files (`/var/log/auth.log`, `/var/log/apache2/access.log`).
- Modifying timestamps (`timestomp` in Metasploit).
--> As an ethical hacker you explain this technique exists and demonstrate it is possible, but you don't sabotage the client's forensic evidence without explicit permission.

==> Reporting in detail
--> A good pentest report includes:
- Executive summary (for management, non-technical, business risk framing).
- Technical findings (for engineers): vulnerability name, CVSS score, affected asset, steps to reproduce, evidence (screenshots), remediation steps.
- Risk rating: Critical / High / Medium / Low / Informational.

## Common Pentest Types (Black-box vs White-box vs Grey-box)

1. Black-box testing – tester is given zero internal information, just like a real external attacker. Most realistic, slowest.
2. White-box testing – tester is given full information (source code, network diagrams, credentials). Fastest, most thorough coverage.
3. Grey-box testing – tester is given partial information (e.g. a normal user account, but no source code). Common middle ground used in most real engagements.

## Legal Practice Platforms (Where You're Allowed To Practice)

--> These platforms give you EXPLICIT permission to attack their machines because that's literally the product. This is where beginners should practice everything in this vault.

1. TryHackMe – browser-based VPN + guided rooms, very beginner friendly, has structured learning paths (e.g. "Complete Beginner", "Offensive Pentesting").
2. HackTheBox – more advanced, less hand-holding, retired + active machines, good once you've got fundamentals down.
3. OverTheWire: Bandit – a wargame accessed purely over SSH, designed to teach Linux command-line + basic security concepts level by level (bandit0 → bandit1 → ...). Zero web browser needed, pure terminal.
4. picoCTF – beginner-friendly Capture The Flag (CTF) platform aimed originally at high schoolers, great for learning CTF-style challenges (crypto, web, binary exploitation, forensics) in isolated categories.
5. DVWA (Damn Vulnerable Web Application) – a deliberately vulnerable PHP/MySQL web app you run yourself (locally, in a VM, or via Docker) to practice web attacks (SQLi, XSS, CSRF, file upload vulns) with adjustable difficulty (low/medium/high/impossible) so you can see how fixes change exploitability.
6. Metasploitable2 / Metasploitable3 – deliberately vulnerable Linux/Windows VMs (by Rapid7) built specifically to be exploited with Metasploit. The classic target for learning Metasploit basics (see note 05).

--> Setup pattern used across almost all of these: run the vulnerable machine in an isolated VM or Docker container on your own machine (or connect via the platform's VPN), and attack it from your own Kali Linux (or similar) attacker machine. Never expose these vulnerable machines to the public internet — they are intentionally broken.

## A Typical Home Lab Setup

--> A minimal legal home lab looks like this:
- Attacker machine: Kali Linux (VM) — comes pre-loaded with nmap, Metasploit, Burp Suite, etc.
- Target machine(s): Metasploitable2 (VM) and/or DVWA (Docker container).
- Both VMs on the same isolated virtual network (e.g. VirtualBox "Host-Only" or "Internal Network" adapter) so the vulnerable machine is never reachable from the real internet.

```bash
# Example: checking you can reach your lab target from Kali
ping -c 4 192.168.56.101      # ping the Metasploitable VM's IP (find via `ip a` inside that VM)
```

--> Once you can `ping` your target and it responds, you're ready to move to Scanning (note 03).
