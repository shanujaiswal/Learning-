### Bug Bounty Methodology and Report Writing

--> ⚠️ LEGAL / ETHICAL REMINDER: Bug bounty hunting is authorized testing BY DEFINITION — but only strictly within the published scope and rules of the specific program. Testing an out-of-scope asset, using a prohibited technique (e.g. automated scanning against a program that explicitly forbids it, or DoS-style load testing), or disclosing a finding publicly before the program's disclosure window closes can get you legally exposed, banned from the platform, and disqualified from payment even for a genuinely valid bug. Read the scope and rules FIRST, every single time, even on a platform/program you've used before — scope changes.

--> This note assumes you already know the OWASP Top 10 (note 04), nmap recon basics (note 03), and Burp's tooling (note 14). The focus here is the METHODOLOGY layer that turns "I know how vulnerabilities work" into "I consistently find them on real, already-hardened public targets" — plus the professional skill of writing a report that actually gets triaged and paid instead of ignored.

## Bug Bounty vs Traditional Pentest — What Actually Differs

--> They test the same underlying vulnerability classes, but the engagement shape is different in ways that change strategy:

1. **Scope and duration**: a pentest has a fixed, negotiated scope and a fixed time window (e.g. "1 week, these 3 IP ranges"), and you get paid regardless of findings. A bug bounty scope is often broad and OPEN-ENDED (an entire `*.example.com` wildcard, ongoing indefinitely), and you're paid per validated finding — no bug, no pay.
2. **Competition**: on a pentest, you're the only tester (or one of a small contracted team) with no time pressure from other testers. On a bug bounty, hundreds of other researchers may be hitting the same target simultaneously — the same "obvious" bug you found at hour 2 may have already been reported by someone else an hour earlier (a "duplicate," which typically pays nothing or a reduced "bonus" at the program's discretion).
3. **Reporting quality bar**: a pentest report is one deliverable covering everything found, usually reviewed with the client directly. Each bug bounty submission is a STANDALONE report triaged by a separate security team (sometimes an outsourced triage service) who has never spoken to you and decides quickly whether to accept, request more info, or close as invalid/duplicate/informative — the quality of the WRITE-UP directly determines whether a real bug gets paid.
4. **Incentive shape**: pentesters are typically rewarded for BREADTH (systematically covering the whole scope, including "nothing found here" as a valid, billable outcome). Bounty hunters are effectively rewarded for DEPTH and speed on whichever bug pays — this pushes methodology toward fast, wide recon to find the LEAST-tested corners of a big scope, since heavily-trodden endpoints (the login page, the main search bar) have usually already been picked over by everyone else.

## Recon Methodology for Bug Bounty

--> Because scope is often huge (an entire company's `*.target.com`), recon is where bounty hunters spend a disproportionate amount of effort — the goal is to find ASSETS other hunters haven't bothered to look at yet (old subdomains, forgotten staging environments, internal tools accidentally exposed).

==> Subdomain enumeration
--> Passive enumeration (no direct traffic to the target, queries public data sources instead — certificate transparency logs, DNS aggregators, search engine indexes):
```bash
subfinder -d target.com -all -o subs_subfinder.txt
amass enum -passive -d target.com -o subs_amass.txt
```
--> Merge and dedupe results from multiple tools — no single tool has complete coverage of every data source, so overlapping different tools consistently surfaces subdomains any single one misses.
```bash
cat subs_subfinder.txt subs_amass.txt | sort -u > subs_all.txt
```
--> Then filter to LIVE hosts only, since a huge fraction of enumerated subdomains are stale DNS records pointing nowhere:
```bash
cat subs_all.txt | httpx -silent -status-code -title -o subs_live.txt
```
--> Active/brute-force enumeration (tries a wordlist of common subdomain names directly against the target's DNS) finds things passive sources miss, at the cost of generating actual DNS traffic against the target — check the program's rules on this specifically, some explicitly restrict active DNS brute-forcing:
```bash
amass enum -active -d target.com -brute -w subdomains_wordlist.txt -o subs_active.txt
```

==> Content discovery (directory/file/endpoint brute-forcing)
--> Once you have live hosts, the next layer is finding paths/endpoints that aren't linked anywhere in the visible UI — old API versions, admin panels, backup files, exposed config.
```bash
ffuf -u https://app.target.com/FUZZ -w /path/to/wordlist.txt -mc 200,204,301,302,307,401,403 -o ffuf_results.json
gobuster dir -u https://app.target.com -w /path/to/wordlist.txt -x php,bak,old,zip -o gobuster_results.txt
```
--> `-mc` (ffuf) filters to interesting status codes rather than drowning in noise; the `-x` extension list in gobuster catches forgotten backup/config files specifically (`config.php.bak`, `db.old`) that are frequently left behind by developers and never cleaned up. SecLists' `raft-*` and `common.txt` wordlists are the standard starting point for both tools.
--> Parameter discovery is a related, often-skipped step — tools like `arjun` or ffuf against a known endpoint with a parameter wordlist find hidden/undocumented GET/POST parameters that unlock functionality not exposed in the normal UI flow (a classic source of BOLA/IDOR bugs from note 15, since a hidden `debug=true` or `admin=1` param may bypass a UI-only restriction).

==> Technology fingerprinting
--> Knowing exactly what's running under the hood tells you which KNOWN CVEs to check for and which vulnerability classes are even plausible (no point trying PHP-specific tricks against a pure Node.js/Express backend).
```bash
whatweb https://app.target.com
```
--> Wappalyzer (browser extension or CLI) passively fingerprints frameworks, CMSs, analytics platforms, CDNs, and JS libraries by inspecting response headers, cookies, and page source patterns — cross-reference any identified version against public CVE databases (the same version-checking mindset from note 03's nmap `-sV` and note 04 item 9).
--> Also worth checking manually: `robots.txt` and `sitemap.xml` (frequently reveal paths not linked from the UI), JS source files pulled and grepped for API endpoint strings and hardcoded keys (same technique as note 15's APK secret-hunting, just applied to bundled `.js` files instead), and Wayback Machine (`web.archive.org`) for historical snapshots of pages/endpoints that may still be live but no longer linked.

## Reading and Respecting Scope and Rules of Engagement

--> Before running a single tool, read the program page fully — this is not optional and is the single most common reason valid-looking submissions get rejected or, worse, researchers get banned.
--> Things to check every time, even for a program you've tested before:
- **In-scope assets** — exact domains/subdomains/apps/API versions listed. A wildcard `*.target.com` scope does NOT automatically include acquired subsidiary companies with entirely different domains unless explicitly listed.
- **Out-of-scope exclusions** — programs frequently explicitly exclude specific subdomains (often a marketing CMS, a third-party-hosted blog, or a staging environment known to be unstable) even within an otherwise broad scope.
- **Prohibited techniques** — many programs forbid automated vulnerability scanners (Nessus/Burp's automated scanner in Pro), any form of DoS/load testing, social engineering against employees, and physical testing, unless explicitly stated otherwise.
- **Reward table / severity definitions** — understand how the program itself defines Critical/High/Medium/Low and what it typically pays for each, so your OWN severity assessment in the report is calibrated to their expectations, not just a generic CVSS number.
- **Safe harbor language** — confirms the program legally authorizes good-faith testing within scope; know what protection you actually have (and don't have) before testing.

## Vulnerability Report Template and Worked Example

--> A good report answers, in order: what is it, how bad is it, exactly how do I reproduce it myself in under 2 minutes, and what should the developer do about it. Triagers read dozens of reports a day — clarity and reproducibility beat flowery writing every time.

==> Template
```text
Title: [Vulnerability class] in [specific endpoint/feature] leading to [impact]

Severity: [Critical/High/Medium/Low] — CVSS v3.1: [vector string and score]

Summary:
  One or two sentences: what the bug is and why it matters.

Steps to Reproduce:
  1. ...
  2. ...
  3. ...
  (Include exact requests, parameters, or a short script — assume the reader
   has zero prior context on this specific endpoint.)

Impact:
  What can a real attacker actually DO with this? Be concrete and specific
  to THIS application, not generic ("XSS can steal cookies" is generic;
  "an attacker can hijack any logged-in user's session and issue transfers
  on their behalf via the exposed /transfer endpoint" is specific).

Proof of Concept:
  Screenshot(s), request/response pairs, or a short video — visual proof
  triagers can verify in seconds without re-deriving your steps.

Remediation:
  Concrete, specific fix suggestion — not just "sanitize input."
```

==> Worked example (BOLA finding, building on note 15's example)
```text
Title: Broken Object Level Authorization on GET /api/v2/users/{id}/workouts
       allows any authenticated user to read any other user's private
       workout and location history

Severity: High — CVSS v3.1: AV:N/AC:L/PR:L/UI:N/S:U/C:H/I:N/A:N (7.1)

Summary:
  The /api/v2/users/{id}/workouts endpoint validates that the caller has a
  valid session token but does not verify the token's owner matches the
  requested {id}, allowing any authenticated user to enumerate and read
  every other user's private workout history, including GPS location
  traces of individual runs/rides.

Steps to Reproduce:
  1. Register a normal account and log in via the mobile app or web client.
  2. Intercept the GET request to /api/v2/users/{own_id}/workouts using
     Burp Proxy; note the valid Bearer token in the Authorization header.
  3. In Burp Repeater, change {own_id} in the URL path to a different,
     arbitrary numeric ID (e.g. own_id - 1), keeping the same Authorization
     header and all other values unchanged.
  4. Send the request. Observe a 200 OK response containing the target
     user's full workout list, including GPS coordinates and timestamps,
     despite no relationship existing between the two accounts.

Impact:
  Any registered user (i.e. anyone who can create a free account) can
  enumerate sequential user IDs and harvest every user's exact daily
  movement patterns, home/work location (inferable from recurring workout
  start points), and activity schedule — a serious real-world physical
  safety and stalking risk, not just a data-confidentiality issue.

Proof of Concept:
  [Screenshot of Repeater request/response pair showing user 8843's data
   returned while authenticated as user 8842, with both IDs visible]

Remediation:
  On every request to this endpoint, verify server-side that the
  authenticated token's associated user_id matches the {id} path
  parameter (or that the caller has an explicit sharing/friend
  relationship granting access), returning 403 Forbidden otherwise.
  Apply the same check pattern to every other object-level endpoint in
  the API, as this is likely a systemic authorization-layer gap rather
  than a single-endpoint oversight.
```

--> Notice what makes this "good": the steps are copy-pasteable/exactly reproducible with no guessing, the impact ties the generic "BOLA" label to a CONCRETE consequence specific to a fitness app (location/stalking risk, not a generic "data exposure" line), and the remediation suggests checking OTHER endpoints too — triagers/engineering teams appreciate being pointed at the systemic root cause, not just the one instance you happened to find, and it often earns extra credibility/bonus consideration.

## Responsible Disclosure Principles

1. **Report privately first, always** — through the program's designated channel (platform submission form, `security@` email, `.well-known/security.txt`), never via public social media, GitHub issues, or a blog post as the first point of contact.
2. **Don't exceed what's needed to prove the bug** — for an IDOR/BOLA, reading ONE other (ideally your own second test account's) record is sufficient proof; there's no legitimate reason to mass-harvest thousands of real users' data "to be thorough."
3. **Stop and report immediately if you accidentally access something clearly beyond intended scope or highly sensitive** (e.g. a misconfigured bug turns out to expose full production database credentials) — don't keep exploring "just a little further"; document what you've already seen and disclose immediately.
4. **Respect the disclosure timeline** — most programs/platforms specify a window (often 90 days, sometimes program-specific) the vendor has to fix the issue before independent public disclosure is permitted, and many bounty programs prohibit public disclosure entirely without explicit written permission, indefinitely.
5. **Don't pivot into destructive testing** — proving a SQLi exists via a boolean-based blind check or a harmless `SELECT version()` extraction is proof enough; running `DROP TABLE` or exfiltrating an entire production user table is not "more proof," it's now causing real damage and may void any safe-harbor protection the program offered.
6. **One issue, one report** — bundling multiple unrelated vulnerabilities into a single report slows triage and can cause a valid secondary finding to get lost or dismissed alongside an invalid primary one.

## Platform Overview

--> Most bug bounty work today happens through triage platforms that sit between researchers and companies, rather than researchers emailing companies directly.

- **HackerOne** — one of the largest platforms; hosts programs ranging from major tech companies to government (the US DoD's "Hack the Pentagon" program has run on HackerOne) to small startups. Has both public programs (open to anyone) and private ones (invite-only, often for researchers with an established reputation/signal score).
- **Bugcrowd** — comparable scale and structure to HackerOne, with its own researcher reputation/points system ("Bugcrowd Priority Score" combining CVSS-style technical severity with the platform's own priority weighting) and its own managed triage team.
- **Intigriti** — Europe-based, strong presence among EU companies partly due to GDPR-driven security investment, generally considered to have fast, responsive triage teams and a reputation for good researcher communication.
- **Vendor-run/self-hosted programs** — many large companies (Google, Meta, Microsoft, GitHub) run their own bounty programs directly rather than exclusively through a third-party platform, sometimes IN ADDITION to a listing on one of the platforms above — always confirm which channel/program version is authoritative if you find the same target listed in multiple places, since scope/rules can differ between them.

--> Practical note for building a track record: reputation (accepted-report count, average severity, response quality) on these platforms compounds — private/invite-only programs (which tend to have less competition and better pay per bug, precisely because they're not open to everyone) are typically unlocked by a solid public-program track record first, which is part of why methodical, well-scoped, clearly-written reports on public programs matter beyond just the immediate payout.
