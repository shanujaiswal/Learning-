### Cyber Security Fundamentals and the CIA Triad

--> Cyber security is the practice of protecting systems, networks, and data from unauthorized access, damage, or disruption.
--> Everything in defensive security eventually traces back to protecting three properties of information: Confidentiality, Integrity, and Availability. This is called the CIA Triad.
--> Every attack you will ever study is, at its core, an attempt to break one (or more) of these three properties.

## The CIA Triad

==> Confidentiality
--> Confidentiality means only authorized people/systems can read or view the data. Unauthorized people should not be able to see it, even if they intercept it.
--> It is enforced using encryption, access control, authentication, and permissions.

Example of Confidentiality being violated:
--> An attacker sniffs unencrypted Wi-Fi traffic in a coffee shop and reads another customer's login credentials being sent over HTTP.
--> A hospital employee looks up a celebrity's medical records out of curiosity, without having a job-related reason to access them (this is an insider confidentiality breach, no "hacking" required).
--> A misconfigured AWS S3 bucket is left public, exposing millions of customer records to anyone with the URL.

==> Integrity
--> Integrity means the data has not been altered or tampered with, either in storage or in transit. If integrity is intact, what you receive/read is exactly what was originally sent/stored.
--> It is enforced using hashing (MD5, SHA-256), digital signatures, checksums, and version control.

Example of Integrity being violated:
--> An attacker performs a Man-in-the-Middle (MITM) attack and changes the destination bank account number in a wire transfer request while it is in transit.
--> A student hacks into a school database and changes their failing grade to a passing one.
--> Malware silently modifies system files or injects malicious code into a legitimate software update (this is called a supply-chain attack).

==> Availability
--> Availability means authorized users can access the data/system whenever they need to, without disruption.
--> It is enforced using redundancy, backups, load balancing, DDoS protection, and disaster recovery plans.

Example of Availability being violated:
--> A DDoS (Distributed Denial of Service) attack floods a company's web server with traffic until it crashes, and legitimate customers can't check out on the shopping site.
--> Ransomware encrypts every file on a hospital's servers, and doctors can't access patient records until a ransom is paid or backups are restored.
--> A backhoe accidentally cuts a fiber line, taking a data center offline (availability threats aren't always malicious — natural disasters and accidents count too).

--> Note: some frameworks extend CIA into the "Parkerian Hexad" by adding Authenticity, Possession/Control, and Utility, but CIA is the foundational model you must know cold.

## Threat vs Vulnerability vs Risk vs Exploit vs Attack Vector

--> These five words get used interchangeably by beginners, but in real security work (and in interviews) they mean very different things. Getting this right is a rite of passage.

1. Vulnerability – A weakness or flaw in a system that could be exploited.
   --> Example: A web application is running an outdated version of Apache Struts with a known unpatched remote code execution bug (this exact flaw caused the 2017 Equifax breach).

2. Threat – Anything that could exploit a vulnerability to cause harm. A threat is the "who/what" — the potential danger itself.
   --> Example: A cybercriminal group that scans the internet for servers running that vulnerable Apache Struts version.

3. Risk – The likelihood of a threat exploiting a vulnerability, combined with the potential impact/damage if it happens. Risk = Threat x Vulnerability x Impact (conceptually).
   --> Example: "There is a HIGH risk that our unpatched customer database server gets breached, because it holds 100 million records and the exploit is publicly available."

4. Exploit – The actual tool, code, or technique used to take advantage of a vulnerability.
   --> Example: A Python script/Metasploit module that sends a crafted HTTP request to trigger the Apache Struts bug and get a shell on the server.

5. Attack Vector – The path or method an attacker uses to gain access to a system in the first place (how they get in).
   --> Example: A phishing email with a malicious attachment, an exposed RDP port, a vulnerable public-facing web form, a USB drive left in a parking lot, a weak Wi-Fi password.

--> Putting it all together with one story:
--> "Our HR portal has an unpatched vulnerability. Ransomware gangs (threat actors) are actively targeting HR portals like ours (threat). They typically get in via phishing emails sent to HR staff (attack vector), and once in, they use a specific exploit to escalate privileges. Given how sensitive our employee data is, the risk of a breach is critical."

## Defense in Depth

--> Defense in depth is the strategy of layering multiple, different security controls so that if one layer fails, another layer is still there to stop or slow the attacker.
--> The idea comes from castle defense — a moat, a wall, guards, a locked gate, and an inner keep. No single layer is perfect, so you stack them.

Typical layers in a real network (outside-in):
1. Perimeter security – Firewalls, DDoS protection, edge routers.
2. Network security – Segmentation, VLANs, IDS/IPS, VPNs.
3. Endpoint security – Antivirus/EDR, host-based firewalls, patching.
4. Application security – Input validation, secure coding, WAFs (Web Application Firewalls).
5. Data security – Encryption at rest and in transit, DLP (Data Loss Prevention).
6. Identity security – MFA, least privilege, strong password policies.
7. Physical security – Locked server rooms, badge access, CCTV.
8. Policies and people – Security awareness training, incident response plans.

--> Why it matters: if an attacker bypasses your firewall (layer 1), your IDS might still catch them (layer 2). If they get past that too, endpoint antivirus might block their malware (layer 3). No single control is trusted to work 100% of the time.
--> This is the opposite of "security by a single silver bullet" thinking — no product alone, no matter how expensive, makes you "secure." Defense in depth assumes failure is normal and plans for it.

## Cyber Security (Defense) vs Ethical Hacking (Offense)

--> These are two sides of the same coin, often called Blue Team vs Red Team.

==> Cyber Security / Blue Team (Defensive)
--> Focused on protecting systems: building defenses, monitoring, detecting attacks, and responding to incidents.
--> Roles: SOC Analyst, Incident Responder, Security Engineer, Threat Hunter.
--> Mindset: "How do I detect and stop this attack, and how do I make sure it never happens again?"
--> Day-to-day: watching SIEM alerts, patching servers, writing detection rules, reviewing logs, running tabletop incident-response drills.

==> Ethical Hacking / Red Team (Offensive)
--> Focused on legally and proactively attacking systems (with permission) to find vulnerabilities before real attackers do.
--> Roles: Penetration Tester, Red Teamer, Bug Bounty Hunter.
--> Mindset: "How would a real attacker break into this system, and what would they do once inside?"
--> Day-to-day: reconnaissance, scanning, exploitation, writing pentest reports with remediation advice.

--> Key distinction: Ethical hackers operate under a signed contract/scope of work (called "Rules of Engagement") — hacking the same system without that authorization is illegal, even with identical techniques. The techniques used by both sides overlap heavily; the authorization and the goal (attack to report vs attack to steal) is what separates a pentester from a criminal.
--> There's also Purple Team, which is not a separate job so much as a collaboration practice — red and blue teams working together, sharing attack techniques and detection gaps in real time, to make both teams (and the organization) better.

## Why This Chapter Matters

--> Every course, certification, and job description in security assumes you already know CIA, threat/vulnerability/risk/exploit, defense in depth, and red vs blue. These are the "alphabet" you'll use to read everything else — firewalls, malware, incident response — so this vocabulary needs to be automatic before moving on.
