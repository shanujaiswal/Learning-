# Automated OSINT Reconnaissance Framework

A fully offline, self-contained Python project that simulates the first phase
of an authorized security engagement: building an **OSINT profile** of a
target organization by combining several "public source" lookups into one
consolidated report that flags real risk signals.

Every "public source" in this project (WHOIS, DNS, employee directory, breach
database, tech-stack fingerprint) is a **local, fixed, mock dataset** in
`mock_data_sources.py`. No real domain, organization, person, or API is ever
touched, and no API keys are required. This makes the framework safe to run,
reproducible (identical output every run), and safe to grade or demo.

## Real-world scenario

Before starting an authorized penetration test or red-team engagement, a
security assessor typically spends time doing **passive OSINT reconnaissance**
on the target organization -- gathering information that is already public,
without touching the target's systems directly. The goal is to build an
attacker's-eye-view profile: what domains/subdomains exist, what technology
the target runs, who works there, and whether any of those employees'
credentials have already leaked in a prior breach.

This project simulates exactly that workflow end-to-end:

1. **WHOIS + DNS recon** -- who owns the domain, and what subdomains exist
   (including one that should never have been publicly exposed:
   `admin.nimbusretail.example`).
2. **Employee OSINT** -- who works there, and do any of their corporate email
   addresses show up in a breach-credential lookup (one employee,
   `bilal.rahman@nimbusretail.example`, does).
3. **Tech-stack fingerprinting** -- what software the target's public web
   presence runs, and whether any of it is a known-vulnerable version (the
   site is running jQuery 1.12.4, vulnerable to CVE-2020-11022/11023).
4. **Profile consolidation** -- all of the above merged into one Markdown
   report (`osint_profile_report.md`) with an overall risk rating.

In a real engagement, none of this data would be hardcoded -- it would come
from live queries against theHarvester/Amass, WHOIS servers, LinkedIn/company
pages, the Have I Been Pwned API, and Wappalyzer/BuiltWith. Here, every one of
those sources is replaced with a small local mock so the pipeline can be run
and inspected safely, with no network access or credentials of any kind.

## Architecture

| Module | Role in this project | Real-world equivalent |
|---|---|---|
| `mock_data_sources.py` | Fixed, offline "public source" datasets (WHOIS, DNS, employees, breach corpus, tech stack) for one fictional target org | The actual public data these tools would normally fetch over the network |
| `whois_and_dns_recon.py` | Queries mock WHOIS/DNS, flags sensitive-looking exposed subdomains (`admin.`, `staging.`, `vpn.`, etc.) | theHarvester / Amass (subdomain enumeration via certificate transparency + search engines), plus a plain WHOIS lookup |
| `employee_osint.py` | Cross-references the scraped employee list against the breach corpus by email | A "people at &lt;company&gt;" LinkedIn scrape combined with a breach-database check like Have I Been Pwned's API |
| `techstack_fingerprint.py` | Matches the site's tech-stack fingerprint against a small known-vulnerable-version table | Wappalyzer/BuiltWith fingerprinting cross-referenced against CVE/NVD data (same pattern as a dependency auditor, applied to web-facing tech) |
| `profile_builder.py` | Merges all three modules' findings into one structured profile, computes an overall risk rating, and renders a Markdown report | The "correlation" step every OSINT workflow ends with -- turning several sources into one cohesive assessment |
| `main.py` | Orchestrates the full pipeline, prints progress per source, writes and prints the final report | The top-level driver script/runbook an assessor would kick off at the start of an engagement |

## Run it

Requires only the Python standard library -- no `pip install` needed.

```bash
cd "Projects/1) Automated OSINT Reconnaissance Framework"
python main.py
```

This prints progress for each of the three recon steps, then writes the full
consolidated report to `osint_profile_report.md` in the same directory and
also prints it to the console.

Each module can also be run individually to see just its own output:

```bash
python whois_and_dns_recon.py
python employee_osint.py
python techstack_fingerprint.py
```

## Verified result (actual output)

Ran with `python main.py` against the mock target **Nimbus Retail Group**
(`nimbusretail.example`). All three intended risk signals were detected:

```
==============================================================================
Automated OSINT Reconnaissance Framework -- target: Nimbus Retail Group
==============================================================================
Target domain: nimbusretail.example
Mode: fully offline / simulated public sources (no real network calls)

==============================================================================
[1/3] WHOIS + DNS recon (mock theHarvester/Amass-style lookup)
==============================================================================
[+] WHOIS registrar: Example Registrar, LLC
[+] DNS records retrieved: 8
    [RISK] Exposed subdomain: vpn.nimbusretail.example (203.0.113.13)
    [RISK] Exposed subdomain: admin.nimbusretail.example (203.0.113.66)
    [RISK] Exposed subdomain: staging.nimbusretail.example (203.0.113.14)

==============================================================================
[2/3] Employee OSINT (mock LinkedIn scrape + breach-database check)
==============================================================================
[+] Employees discovered: 6
    [RISK] Bilal Rahman <bilal.rahman@nimbusretail.example> exposed in: CollectionLeak-2019, MegaRetailerBreach-2021

==============================================================================
[3/3] Tech-stack fingerprint (mock Wappalyzer-style scan)
==============================================================================
[+] Technologies detected: 5
    [RISK] jQuery 1.12.4 is outdated/vulnerable (CVE-2020-11022 / CVE-2020-11023)

==============================================================================
Consolidating OSINT profile + risk summary
==============================================================================
[+] Consolidated report written to: osint_profile_report.md
[+] Overall risk rating: HIGH
[+] Risk signals found: 3
    - 3 sensitive subdomain(s) exposed in public DNS
    - 1 employee credential(s) found in breach corpus
    - 1 outdated/vulnerable tech-stack component(s)
```

The full rendered report (also written to `osint_profile_report.md`) includes
the WHOIS table, complete DNS record dump, employee directory, breach
findings table, tech-stack fingerprint table, and vulnerable-component table
-- confirming all **3 risk signals** required by the brief were found:

1. **Exposed admin subdomain** -- `admin.nimbusretail.example` (plus `vpn.`
   and `staging.`, which the sensitive-keyword matcher also correctly flags).
2. **Breached employee credential** -- `bilal.rahman@nimbusretail.example`
   found in `CollectionLeak-2019` and `MegaRetailerBreach-2021`.
3. **Vulnerable tech-stack component** -- jQuery `1.12.4`, below the safe
   floor of `3.5.0` (CVE-2020-11022 / CVE-2020-11023).

Exit code was `0` on every run, and output is identical across repeated runs
since all mock data is fixed (no randomness).

## Things to try changing

- **Add a second exposed subdomain pattern** in `mock_data_sources.py` (e.g.
  `phpmyadmin.nimbusretail.example`) and confirm `whois_and_dns_recon.py`'s
  keyword matcher picks it up automatically.
- **Add a second breached employee** to `get_breach_corpus()` and watch the
  overall risk rating and `profile_builder.py`'s risk-signal count respond.
- **Tighten or loosen** `SENSITIVE_SUBDOMAIN_KEYWORDS` in
  `whois_and_dns_recon.py` to see how false positives/negatives shift (e.g.
  removing `"vpn"` stops it from being flagged even though it's still in DNS).
- **Add a new tech-stack entry + vulnerability rule** in
  `techstack_fingerprint.py`'s `KNOWN_VULNERABLE_VERSIONS` table (e.g. an old
  Apache or Drupal version) and confirm it shows up in the vulnerable
  components table.
- **Change the risk-rating thresholds** in `profile_builder.build_profile()`
  (currently 3+ signals = HIGH, 2 = MEDIUM-HIGH, 1 = MEDIUM, 0 = LOW) to match
  a different risk-scoring philosophy.
- **Swap the mock data source functions for real ones** (e.g. real
  `python-whois`, real Have I Been Pwned API calls with a key, real
  Wappalyzer output) to turn this from a training simulation into an actual
  recon tool -- remembering to only ever point it at a domain/org you own or
  are explicitly authorized to assess.
