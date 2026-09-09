### Password Attacks - Hydra, John the Ripper and Hashcat

--> ⚠️ LEGAL / ETHICAL REMINDER: Brute forcing, password spraying, and hash cracking are among the noisiest, most easily-logged attacker activities there are — failed login floods and account lockouts are trivially visible to any monitoring. Only run anything below against systems you own or have explicit written permission to test, and agree in advance (Rules of Engagement, note 01) on lockout thresholds you must respect during a real authorized engagement.

--> A "password attack" covers any technique aimed at recovering a valid credential — either by guessing it against a live service (online), or by cracking a hash you've already obtained (offline). Which category you're in changes everything about strategy, speed, and risk.

## Online vs Offline Attacks

==> Online Attacks
--> You send guesses directly AT a live authentication service (SSH, FTP, a web login form, RDP, SMB). The service itself validates each guess.
--> Constraints: rate-limited by network round-trip time, easily triggers account lockout policies, easily logged/alerted on, and the target can simply block your IP.
--> Tooling: Hydra, Medusa, ncrack, Metasploit's `auxiliary/scanner/*/login` modules.

==> Offline Attacks
--> You already possess the password HASH (dumped from a SAM database, `/etc/shadow`, a stolen database, an NTDS.dit extract, a captured hash from a network protocol) and crack it locally without touching the target at all.
--> Constraints: only limited by your own compute power (CPU/GPU) and how good your wordlist/rule set is — no lockouts, no logging on the target's side, can run for days.
--> Tooling: John the Ripper, Hashcat.
--> Why this matters strategically: getting from "online, rate-limited, risky" to "offline, unlimited, silent" as fast as possible is a core theme of real engagements — e.g. dumping `/etc/shadow` via a privesc (note 08) turns a slow online SSH brute force into an unlimited offline Hashcat job.

## Wordlists

--> A wordlist (dictionary) is the single biggest factor in cracking/brute-forcing success — better wordlist beats more compute almost every time.

==> rockyou.txt
--> The most famous wordlist in the industry — ~14 million real passwords leaked from the 2009 RockYou breach. Ships with Kali (compressed) and is the default starting point for almost every lab/CTF box.
```bash
gunzip /usr/share/wordlists/rockyou.txt.gz     # Kali ships it gzipped to save disk space - unzip once before use
wc -l /usr/share/wordlists/rockyou.txt          # ~14,344,391 lines
```

==> SecLists
--> A much larger, curated collection of wordlists for every purpose — usernames, passwords, web content discovery, fuzzing payloads. `apt install seclists` on Kali, or clone from GitHub.
```bash
ls /usr/share/seclists/Passwords/                # browse categories: Common-Credentials, Leaked-Databases, etc.
cat /usr/share/seclists/Usernames/top-usernames-shortlist.txt
```

==> crunch — Generating Custom Wordlists
--> `crunch` generates wordlists from a character set and length pattern — useful when you know something about the password policy or format (e.g. "8 characters, starts with company name, ends in 2 digits").
```bash
crunch 8 8 0123456789 -o pins.txt                     # every possible 8-digit numeric PIN
crunch 6 8 abcdefghijklmnopqrstuvwxyz -o lower.txt      # all lowercase combos, length 6 to 8 (WARNING: huge file, do the math first)
crunch 8 8 -t Acme%%%% -o acme_passwords.txt            # pattern mode: "Acme" + 4 digits (%), e.g. Acme1234, Acme0007
```
--> `-t` pattern symbols: `@` = lowercase, `,` = uppercase, `%` = digit, `^` = symbol. Always estimate output SIZE first (`crunch 8 8 0123456789` alone is 100,000,000 lines) — `crunch` prints an estimate before generating if you just run it without `-o`.

==> Mutating Existing Wordlists
```bash
# Hashcat's rule engine can also generate mutations without cracking (see attack mode 3 below)
# Common cheap trick: append rockyou.txt + a target-specific custom list, then dedupe
cat rockyou.txt custom_company_terms.txt | sort -u > combined.txt
```

## Hydra — Online Brute Force

--> `hydra` is the standard tool for brute-forcing login credentials against a huge range of network protocols/services in parallel.

==> Basic Syntax
```bash
hydra -l <user> -P <passlist> <target> <service>       # single username, list of passwords
hydra -L <userlist> -P <passlist> <target> <service>    # list of usernames AND passwords - full cartesian product
hydra -l <user> -p <singlepass> <target> <service>       # single/single, mostly for testing the command works
```

==> SSH Example
```bash
hydra -l root -P /usr/share/wordlists/rockyou.txt ssh://192.168.56.101 -t 4
# -t 4 = 4 parallel connection threads. SSH is slow per-attempt (key exchange overhead) so don't go too high or you'll just get connection errors/false negatives.
```
```text
[22][ssh] host: 192.168.56.101   login: root   password: password123
```
--> That final line is the hit format — always `[port][service] host: ... login: ... password: ...`. Save output with `-o results.txt` on any real run.

==> FTP Example
```bash
hydra -L users.txt -P /usr/share/wordlists/rockyou.txt ftp://192.168.56.101
```

==> HTTP POST Form Example (the trickiest, most common real-world case)
```bash
hydra -l admin -P rockyou.txt 192.168.56.101 http-post-form \
  "/login.php:username=^USER^&password=^PASS^:Invalid username or password"
```
--> Breaking down the `http-post-form` string, colon-separated into 3 parts:
1. The path to POST to: `/login.php`.
2. The POST body template, with `^USER^`/`^PASS^` as placeholders Hydra substitutes on every attempt.
3. The FAILURE condition string — text that appears in the response ONLY on a failed login (e.g. "Invalid username or password"). Hydra treats any response NOT containing this string as a potential success. Getting this exact string right (inspect the login form's failure response first, e.g. with Burp/curl) is the single most common source of Hydra false negatives on web forms.
--> Alternative: prefix the failure string with `F=` explicitly, or use `S=` to instead match a SUCCESS string/redirect (often more reliable if the app redirects to `/dashboard` only on success):
```bash
hydra -l admin -P rockyou.txt 192.168.56.101 http-post-form \
  "/login.php:username=^USER^&password=^PASS^:S=Location: /dashboard"
```

==> Other Common Service Modules
```bash
hydra -l admin -P rockyou.txt rdp://192.168.56.101          # RDP
hydra -L users.txt -P rockyou.txt smb://192.168.56.101       # SMB
hydra -l sa -P rockyou.txt mssql://192.168.56.101             # MSSQL
hydra -l admin -P rockyou.txt 192.168.56.101 http-get -m /admin/    # HTTP basic auth on a specific path
```

==> Password Spraying vs Brute Forcing — a Critical Distinction
--> Brute force = MANY passwords against ONE (or few) username(s) — high volume against a single account, very likely to trigger THAT account's lockout policy.
--> Password spraying = ONE (or a small handful of) likely password(s) tried against MANY usernames — e.g. try `Password123!`, `Welcome1`, `CompanyName2024!` against every username from an enumeration dump (note 06). Stays under most per-account lockout thresholds because each individual account only sees one or two attempts, while still statistically finding the inevitable weak/reused password across a large user base.
```bash
# Spraying with Hydra: one password, many users, low thread count, spaced out
hydra -L usernames.txt -p 'Summer2024!' ssh://192.168.56.101 -t 1 -W 30
# -W 30 adds a wait between attempts - deliberately slow to stay under lockout thresholds and avoid alerting
```
--> Purpose-built spraying tools track lockout state properly across a whole run and are safer in real engagements: `kerbrute` (for AD/Kerberos specifically — can even enumerate valid usernames via pre-auth failure differences, without locking anything out), and CrackMapExec/NetExec's `--continue-on-success` spraying mode.

==> Account Lockout Considerations
--> Always check the enumerated password policy (note 06 — `enum4linux`/`ldapsearch` lockout threshold) BEFORE any online attack. A threshold of "5 bad attempts locks the account for 30 minutes" changes your entire approach.
--> Rules of thumb for real (authorized) engagements: stay at least 1-2 attempts under the threshold per account per lockout observation window, prefer spraying over brute forcing whenever a username list already exists, and always get explicit written sign-off before any activity that could lock out real users (this can cause a genuine business-impacting outage — a classic "oops" in inexperienced pentesting).

## John the Ripper — Offline Cracking

--> John ("JtR") is the classic offline password cracker, particularly strong at automatically detecting hash formats and has a mature, flexible rule engine for wordlist mutation.

==> Getting a Hash Into John's Format
```bash
# /etc/shadow hashes need unshadowing first (combine with /etc/passwd for full user context)
unshadow /etc/passwd /etc/shadow > combined.txt

# Windows SAM/SYSTEM hives dumped via secretsdump.py or similar need no extra step, just feed the hash directly
```

==> Basic Wordlist Attack
```bash
john --wordlist=/usr/share/wordlists/rockyou.txt combined.txt
john --show combined.txt          # display cracked passwords once the run finishes (or is interrupted)
```
```text
root:toor123
```

==> Format Detection
```bash
john --list=formats | less              # see every hash format John supports
john --format=raw-md5 hash.txt            # force a specific format if autodetection is wrong/ambiguous
```
--> John usually autodetects the format from hash structure (length, prefix like `$6$` for sha512crypt or `$2y$` for bcrypt), but hybrid/oddly-formatted hashes sometimes need `--format` forced explicitly.

==> Rules — Wordlist Mutation
--> A "rule" transforms each wordlist entry before trying it (append digits, capitalize first letter, leetspeak substitutions) — massively increases coverage from the same base wordlist, because real users mutate dictionary words predictably ("password" → "P@ssw0rd123!").
```bash
john --wordlist=rockyou.txt --rules combined.txt              # use John's built-in default rule set
john --wordlist=rockyou.txt --rules=Jumbo combined.txt          # a more aggressive, larger community rule set (in john.conf)
```

==> Incremental (Pure Brute Force) Mode
```bash
john --incremental combined.txt        # try every possible character combination, slowest, last resort
```

==> Zip/PDF/Office Document Password Cracking
```bash
zip2john protected.zip > zip.hash
john --wordlist=rockyou.txt zip.hash

pdf2john.pl protected.pdf > pdf.hash
office2john.py protected.docx > office.hash
```
--> John ships a whole family of `*2john` helper scripts that extract crackable hash material from specific file formats — always check for one before assuming a file type "can't be cracked with John".

## Hashcat — GPU-Accelerated Offline Cracking

--> Hashcat is the fastest cracker available because it's built ground-up for GPU acceleration (OpenCL/CUDA) — a modern GPU can be 50-100x+ faster than CPU-only John for many hash types, which matters enormously once you're past simple dictionary attacks into rule-based or mask attacks against large keyspaces.

==> Hash Modes (`-m`)
--> Hashcat needs an exact numeric mode identifying the hash algorithm/format — unlike John it does NOT reliably autodetect.
```bash
hashcat --example-hashes | less           # browse example hashes for every mode to identify yours by comparison
hashcat --help | grep -i md5               # search modes by keyword
```
--> Modes worth memorizing:
1. `0` — raw MD5.
2. `100` — raw SHA1.
3. `1000` — NTLM (Windows password hashes — hugely common target).
4. `1800` — sha512crypt (`$6$...`, modern Linux `/etc/shadow`).
5. `3200` — bcrypt (`$2a$`/`$2y$`, common in web app user tables).
6. `5600` — NetNTLMv2 (captured over the network, e.g. via Responder — relevant to AD attacks).
7. `13100` — Kerberos 5 TGS-REP etype 23 (the format Kerberoasting hashes come in).

==> Attack Modes (`-a`)
1. `-a 0` — Straight/dictionary attack: try each wordlist entry as-is (optionally with rules via `-r`).
```bash
hashcat -m 1000 -a 0 ntlm_hashes.txt rockyou.txt
```
2. `-a 3` — Mask/brute-force attack: define a character-set pattern per position instead of a full wordlist — efficient for known-format passwords (e.g. "always 8 chars, last 2 are digits").
```bash
hashcat -m 0 -a 3 hash.txt ?u?l?l?l?l?l?d?d
# ?u = uppercase, ?l = lowercase, ?d = digit, ?s = symbol - one custom charset token per expected character position
```
3. `-a 6` — Wordlist + mask (hybrid, mask appended to the end of each wordlist word): great for "dictionary word + suffix digits/symbols" patterns like corporate password policies often produce.
```bash
hashcat -m 1000 -a 6 hash.txt rockyou.txt ?d?d?d?d
# tries every rockyou word with every 4-digit suffix appended - e.g. "Summer" -> Summer0000...Summer9999
```
4. `-a 7` — Mask + wordlist (hybrid, mask prepended instead): same idea, reversed order.
```bash
hashcat -m 1000 -a 7 hash.txt ?d?d?d?d rockyou.txt
```

==> Rules in Hashcat
```bash
hashcat -m 0 -a 0 hash.txt rockyou.txt -r /usr/share/hashcat/rules/best64.rule
```
--> `best64.rule` is a well-known, small, high-yield ruleset (case toggling, appends, common leetspeak subs) — good default before reaching for something heavier like `rockyou-30000.rule`.

==> Practical Workflow
```bash
hashcat -m 1000 -a 0 ntlm.txt rockyou.txt -r best64.rule --potfile-disable
# --potfile-disable just avoids re-skipping already-cracked hashes across test runs during study/practice

hashcat --show ntlm.txt -m 1000       # show cracked results any time, even mid-run in another terminal
```
--> Cracked results are cached in a "potfile" (`~/.hashcat/hashcat.potfile` by default) — meaning if you crack the same hash again later (e.g. from a different dump of the same environment), Hashcat instantly recognizes it without recomputing.

==> GPU Acceleration Notes
--> Check available compute devices before a big job: `hashcat -I` lists detected GPUs/CPUs and their backend (CUDA/OpenCL/HIP).
--> `--benchmark` gives raw hashes/second per mode on your hardware — useful for estimating how long a given keyspace will realistically take before committing to a multi-day mask attack.
```bash
hashcat -I
hashcat -b -m 1000        # benchmark NTLM cracking speed specifically on this machine
```
--> Cloud/lab reality check: a laptop CPU cracking bcrypt (`-m 3200`, deliberately slow-by-design) might do a few hundred H/s, while a single decent GPU cracking NTLM (`-m 1000`, deliberately fast-by-design) can exceed tens of billions of H/s — algorithm choice matters as much as attacker hardware, which is exactly why slow hash functions (bcrypt/argon2/scrypt) are recommended for real applications.

## Choosing Between John and Hashcat

--> Rules of thumb rather than hard rules:
1. Hashcat for anything GPU-friendly and any large keyspace (mask attacks, hybrid attacks, NTLM/MD5/SHA1 at scale) — it's simply faster when a GPU is available.
2. John for quick CPU-only jobs, obscure/legacy formats it supports out of the box, and its convenient `*2john` file-format extraction helpers.
3. Many practitioners use John's helpers to EXTRACT the hash (`zip2john`, `ssh2john`, etc.) then feed that hash into Hashcat for the actual cracking — the two tools are complementary, not strictly competing.

--> This connects directly into note 06 (enumeration produces the username lists and hash dumps this note consumes) and note 08/09 (privilege escalation often ends with dumping `/etc/shadow` or the SAM/NTDS database, which is then cracked exactly as described here).
