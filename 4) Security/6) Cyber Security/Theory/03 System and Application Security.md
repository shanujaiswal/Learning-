### System and Application Security

--> Network security protects the "roads" data travels on. System and application security protects the "buildings" — the operating systems, servers, and software that actually store and process the data.
--> Most real-world breaches don't happen because of some genius zero-day exploit — they happen because of unpatched systems, weak access control, and sloppy code. This chapter covers those exact fundamentals.

## Authentication vs Authorization

--> These two words sound similar and are constantly confused, but they answer completely different questions.

--> Authentication (AuthN) answers: "Who are you?" — it is the process of verifying an identity, usually via something you know (password), something you have (phone/token), or something you are (fingerprint).
--> Authorization (AuthZ) answers: "What are you allowed to do?" — it is the process of checking what permissions/access a verified identity actually has.

--> Example: When you swipe a badge to enter an office building, the badge reader checking that the badge is valid and belongs to you = authentication. The system then checking that your specific badge is allowed into the server room (not just the lobby) = authorization.
--> In web apps: logging in with your username/password = authentication. The app then checking whether you (a regular user) are allowed to access the /admin panel = authorization. A broken authorization check is how regular users end up accessing admin features they shouldn't (a bug class called "Broken Access Control" — currently #1 on the OWASP Top 10).

==> MFA / 2FA (Multi-Factor / Two-Factor Authentication)
--> Authentication is based on three possible "factors":
1. Something you know – password, PIN, security question.
2. Something you have – phone (OTP app/SMS code), hardware token (YubiKey), smart card.
3. Something you are – fingerprint, face ID, retina scan (biometrics).

--> 2FA specifically means using exactly two of these factors together. MFA is the general term for using two or more.
--> Example: Logging into your email with a password (factor 1: something you know) and then entering a 6-digit code from Google Authenticator on your phone (factor 2: something you have) = 2FA.
--> Why it matters: even if an attacker steals/phishes/cracks your password, they still can't log in without also having your physical phone (or fingerprint). This is why MFA blocks the vast majority of account-takeover attacks even when passwords leak.
--> Note: using a password + a security question is NOT true 2FA, because both are "something you know" — it's not actually a second factor, just a second piece of knowledge.

## Principle of Least Privilege (PoLP)

--> Every user, process, and system should be given only the minimum level of access/permissions necessary to do its job — nothing more.
--> This directly limits the "blast radius" of any compromise. If an attacker takes over an account that only has read access to one folder, that's all they get — instead of full admin control over the entire network.

Examples of Least Privilege in practice:
--> A junior accountant's account can view invoices but cannot approve payments or access HR records.
--> A web application's database account can only SELECT/INSERT/UPDATE rows in the app's own tables — it does NOT have permission to DROP TABLE or access the operating system.
--> A web server process runs as a low-privilege user (like `www-data` on Linux) instead of `root`, so that if the web app is exploited, the attacker doesn't automatically get full control of the server.
--> Admin/root accounts are used only when absolutely necessary, not for daily work — this is why IT staff have a separate "regular" account for email/browsing and a separate "admin" account for administrative tasks.

--> Related concept: Just-In-Time (JIT) access — instead of giving someone standing admin rights 24/7, they request elevated access only when needed, for a limited time window, which is then automatically revoked.

## Patch Management

--> A "patch" is an update released by a vendor to fix a known bug or security vulnerability in software/an OS.
--> Patch management is the ongoing, disciplined process of identifying, testing, and applying these patches across all systems in an organization.

--> Why unpatched systems are the #1 exploited weakness:
--> Once a vulnerability is publicly disclosed (or a patch is released), attackers reverse-engineer the patch to figure out exactly what was broken, then write exploits targeting anyone who hasn't updated yet. This is called "patch diffing."
--> Organizations are often slow to patch because of fear of breaking production systems, lack of visibility into what's even running, or simple lack of resources — attackers rely on exactly this lag.
--> Famous real-world example: The 2017 WannaCry ransomware outbreak exploited a Windows SMB vulnerability (EternalBlue) for which Microsoft had already released a patch (MS17-010) a full month before the attack. Organizations that hadn't patched were devastated; those that had were untouched.
--> Another example: The 2017 Equifax breach (mentioned in the fundamentals chapter) happened because a known, patched Apache Struts vulnerability was left unpatched for months on a public-facing server, exposing 147 million people's data.

--> Good patch management practice:
1. Maintain an accurate inventory of all systems/software (you can't patch what you don't know exists).
2. Subscribe to vendor security bulletins / CVE feeds.
3. Test patches in a staging environment before rolling to production.
4. Prioritize patches by severity (CVSS score) and exploitability (is it already being exploited in the wild?).
5. Set a defined patching SLA (e.g., "critical vulnerabilities patched within 72 hours").

## OS Hardening Basics

--> "Hardening" means reducing a system's attack surface — removing or locking down anything that isn't strictly necessary, so there are fewer ways in for an attacker.

Core hardening practices:
--> Disable unused services and ports. If a server doesn't need FTP, Telnet, or a print spooler running, turn them off — every running service is a potential vulnerability waiting to be found.
--> Change insecure default configurations. Many devices/software ship with default admin credentials (admin/admin) or open management interfaces — these must be changed/restricted immediately after install.
--> Apply the principle of least privilege to services too — don't run services as root/SYSTEM if a lower-privilege account will do.
--> Enable host-based firewalls and only allow required inbound/outbound traffic.
--> Remove default/sample accounts and files that ship with an installation (many CMS platforms and IoT devices are compromised purely through unchanged default credentials).
--> Keep only necessary software installed — every extra installed application is more code that could contain a vulnerability.
--> Enable logging and time synchronization (NTP) — you can't investigate an incident later if there are no logs, and inconsistent clocks make correlating events across systems nearly impossible.

--> "Secure by default" is the modern design philosophy this all points toward: systems should ship locked-down, requiring the administrator to deliberately open things up, rather than shipping wide-open and requiring the administrator to lock things down (which people often forget to do).

## Secure Coding Basics

--> Most application-layer breaches trace back to a handful of recurring coding mistakes. Secure coding means writing software with these mistakes in mind from the start, not bolting on security afterward.

==> Input Validation
--> Never trust data coming from a user, a file upload, an API request, or any external source — always validate it against an expected format, type, length, and range before using it.
--> Example: If a "phone number" field is expected to be digits only, reject/sanitize anything containing letters, quotes, or script tags before it ever reaches your database or is rendered on a page.
--> Prefer allow-lists (defining exactly what IS allowed) over deny-lists (trying to block everything bad) — deny-lists are always incomplete because attackers find new bypasses.
--> Failing to validate input is the root cause behind SQL Injection (unvalidated input becomes part of a database query) and many other injection-style attacks.

==> Output Encoding
--> When data is displayed back to a user (e.g., in a web page), it must be encoded/escaped so that it is rendered as plain text/data, not executed as code by the browser.
--> Example: If a user's profile "bio" field contains `<script>alert('hacked')</script>`, output encoding converts the angle brackets into `&lt;script&gt;...&lt;/script&gt;` so the browser displays it as harmless text instead of running it as JavaScript.
--> Failing to encode output is the root cause of Cross-Site Scripting (XSS) — one of the most common web vulnerabilities.

==> Avoiding Hardcoded Secrets
--> Never write passwords, API keys, database credentials, or encryption keys directly into source code.
--> Why: source code frequently ends up in places you don't expect — pushed to a public GitHub repo, shared with contractors, decompiled from a mobile app — and once a secret is exposed, it must be treated as permanently compromised.
--> Instead, use environment variables, dedicated secrets managers (AWS Secrets Manager, HashiCorp Vault, Azure Key Vault), or encrypted config files that are excluded from version control (`.gitignore`).
--> Real-world consequence: countless breaches have started with attackers scanning public GitHub repos for accidentally committed AWS keys or database passwords using automated tools.

```python
# BAD - hardcoded secret directly in code
db_password = "SuperSecret123!"

# GOOD - loaded from environment, never committed to source control
import os
db_password = os.environ.get("DB_PASSWORD")
```

## Tying Into the OWASP Top 10

--> The OWASP Top 10 is a regularly updated, industry-standard list of the most critical web application security risks, maintained by the Open Web Application Security Project.
--> Nearly every item on that list is a direct consequence of skipping one of the basics above:
--> Broken Access Control → skipping proper authorization checks (not just authentication).
--> Cryptographic Failures → sensitive data stored/transmitted without proper encryption.
--> Injection (SQL Injection, Command Injection, etc.) → missing input validation.
--> Security Misconfiguration → skipped OS/application hardening, default credentials left in place.
--> Vulnerable and Outdated Components → poor patch management, using libraries with known CVEs.
--> Identification and Authentication Failures → weak password policies, no MFA.

--> The takeaway for a beginner: you don't need to memorize the OWASP Top 10 as an isolated list of trivia. If you deeply understand authentication/authorization, least privilege, patching, hardening, and secure coding basics, you already understand WHY almost every item on that list exists.
