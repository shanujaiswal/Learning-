# Bug Bounty Recon-to-Report Automation Toolkit

## Real-world scenario

A bug bounty hunter picks up an authorized program -- **AcmeCorp Public
Bug Bounty** -- with a published scope: a broad `*.acmecorp.com` wildcard,
plus an explicit list of exclusions the program carves back out (a
third-party-hosted marketing blog, a third-party status page, an acquired
subsidiary's HR portal, and an internal-only staging network). This is
the realistic shape of almost every real program's scope page.

The hunter's actual workflow, end to end, looks like:

1. **Recon** -- enumerate every subdomain you can find for the target
   (passive sources: crt.sh, Subfinder, Amass). This *always* turns up
   assets regardless of whether the program wants them tested --
   certificate transparency logs don't know or care about a program's
   exclusion list.
2. **Scope compliance** -- before sending a single request, filter that
   raw candidate list against the program's published rules. **This step
   is not optional and not just good practice** -- Theory note 16 is
   explicit that testing an out-of-scope asset is a real, serious
   program-policy violation even when it isn't a crime: it gets
   researchers banned from the platform and forfeits payment even for a
   genuinely valid bug found elsewhere in scope. `scope_filter.py` is the
   load-bearing control in this entire pipeline for exactly that reason.
3. **Lightweight probing** -- run a handful of safe, high-signal,
   non-destructive checks against the survivors only (exposed
   `.git`/`.env`, verbose debug/stack-trace pages, missing security
   headers) -- never an aggressive scanner, never DoS-style load testing.
4. **Report writing** -- every confirmed finding becomes a standalone,
   submission-ready report in the format a real triage team expects:
   Title, Severity, Affected Asset, Steps to Reproduce, Impact, Suggested
   Fix. A poorly written report gets a genuinely valid bug closed as
   "needs more info" or ignored outright -- write-up quality is a real
   part of getting paid, not a formality.

This toolkit automates that entire workflow, offline and deterministically
(no real DNS/network calls, fixed random seed, a local simulated HTTP
response model standing in for live probes), and then **proves** the scope
step actually held: `main.py` asserts that no host touched by any probe was
ever a member of the excluded set.

This project is intentionally about the *process* (recon -> filter ->
probe -> report), not a deep dive into any one vulnerability class -- see
the other Ethical Hacking projects for vuln-class-specific work.

## Architecture

| Module | Role | Real-world equivalent |
|---|---|---|
| `program_scope.py` | Encodes the program's published in-scope wildcard patterns and out-of-scope exclusions as data, with scope-evaluation logic (exclusions win over wildcards) | The program's policy/scope page on HackerOne/Bugcrowd/Intigriti |
| `subdomain_enumerator.py` | Simulates passive subdomain discovery (fixed seed, no network calls), deliberately mixing in out-of-scope hosts | Subfinder / Amass (passive) / crt.sh certificate-transparency sweep |
| `scope_filter.py` | Strictly filters every candidate against `program_scope.py` before any testing happens, logging every decision | A disciplined hunter manually checking every host against the scope page -- automated, so nothing is ever skipped |
| `vulnerability_probes.py` | Runs 3 low-risk/high-signal checks (exposed sensitive path, debug/stack-trace leak, missing security header) against in-scope survivors only, with an independent in-module scope re-check as defense in depth | Manual/lightweight checks a hunter runs by hand or with `curl`/Burp Repeater, deliberately *not* an aggressive automated scanner (many programs forbid those) |
| `report_writer.py` | Renders each confirmed `Finding` into a submission-ready Markdown report plus one combined summary | Filling out a HackerOne/Bugcrowd submission form using the program's report template |
| `main.py` | Orchestrates the full pipeline and asserts zero out-of-scope hosts were ever probed | The hunter's own end-to-end session discipline, made mechanically verifiable |

## Run it

```bash
python main.py
```

No dependencies beyond the Python standard library -- everything is
simulated in-process (no Flask server, no real network egress).

## Verified result (actual output from a real run)

```
[1/4] Enumerating candidate subdomains (simulated, fixed seed)...
      Discovered 13 candidate hosts (7 legitimate + 5 out-of-scope + 1 unrelated lookalike domain)

[2/4] Filtering candidates against published scope rules...
  [IN-SCOPE]  cdn.acmecorp.com, www.acmecorp.com, api.acmecorp.com, portal.acmecorp.com,
              shop.acmecorp.com, dev.acmecorp.com, mail.acmecorp.com   (7 hosts)
  [EXCLUDED PER SCOPE] vpn.internal.acmecorp.com, blog.acmecorp.com, build.internal.acmecorp.com,
              acmecorp-notreal.net, status.acmecorp.com, partner-hr.acmecorp.com   (6 hosts)

[3/4] Running vulnerability probes against in-scope hosts only...
      api.acmecorp.com:    1 finding  -- [Medium] verbose stack trace / debug mode enabled
      portal.acmecorp.com: 1 finding  -- [High]   exposed /.env (leaked DB/SMTP credentials)
      shop.acmecorp.com:   1 finding  -- [Low]    missing Content-Security-Policy header
      dev.acmecorp.com:    3 findings -- [High] exposed /.git/config, [Medium] stack trace,
                                          [Low] missing CSP header
      cdn / www / mail.acmecorp.com: clean, no findings

      COMPLIANCE CHECK PASSED: 7 host(s) probed, 0 out-of-scope hosts touched
      (excluded set has 6 hosts).

[4/4] Writing submission-ready reports for 6 confirmed finding(s)...
      -> bug_bounty_reports/01-debug_stack_trace-api-acmecorp-com.md
      -> bug_bounty_reports/02-exposed_sensitive_path-portal-acmecorp-com.md
      -> bug_bounty_reports/03-missing_security_header-shop-acmecorp-com.md
      -> bug_bounty_reports/04-exposed_sensitive_path-dev-acmecorp-com.md
      -> bug_bounty_reports/05-debug_stack_trace-dev-acmecorp-com.md
      -> bug_bounty_reports/06-missing_security_header-dev-acmecorp-com.md
      -> bug_bounty_reports/submission_summary.md

Total confirmed findings: 6
Total hosts discovered:   13
Total in-scope hosts:     7
Total excluded per scope: 6
Out-of-scope hosts touched by any probe: 0 (must be 0)
```

The `assert` statements in `main.py` (zero overlap between probed hosts and
the excluded set, and every probed host is a subset of the in-scope list)
both passed on this run -- the pipeline never sent a single simulated
request to `blog.acmecorp.com`, `status.acmecorp.com`,
`partner-hr.acmecorp.com`, either `*.internal.acmecorp.com` host, or the
unrelated lookalike domain, even though all six were discovered during
enumeration right alongside legitimate targets.

## Things to try changing

- Add a new out-of-scope exclusion pattern to `program_scope.py` (e.g. a
  second acquired subsidiary) and a matching candidate host to
  `subdomain_enumerator.py` -- confirm it gets caught and excluded without
  touching any other module.
- Add a 4th probe to `vulnerability_probes.py` (e.g. checking for an
  exposed `/admin` panel with default credentials, or a permissive CORS
  header) and a matching fixture entry, then re-run and confirm a new
  report appears in `bug_bounty_reports/`.
- Deliberately try to probe an out-of-scope host directly (e.g. call
  `vulnerability_probes.run_all_probes("blog.acmecorp.com")` from a
  Python shell) and observe the `PermissionError` the in-module scope
  guard raises -- this is the defense-in-depth check on top of
  `scope_filter.py`.
- Change `subdomain_enumerator.SEED` to a different integer and re-run --
  the discovery order changes but the scope-filtering and findings stay
  identical, since the underlying candidate pool and the fixture model in
  `vulnerability_probes.py` are unchanged.
- Swap the in-process fixture model in `vulnerability_probes.py` for a
  real local Flask test target (start a tiny Flask app with a
  deliberately misconfigured `/`, `/.env` route, and debug mode on,
  then use `requests.get()` against `http://127.0.0.1:5000/...`) to see
  the exact same detection logic work against real HTTP responses instead
  of simulated ones.
