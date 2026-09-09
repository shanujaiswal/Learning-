# Bug Bounty Report #03

**Title:** Missing Content-Security-Policy header on shop.acmecorp.com

**Severity:** Low (approximate CVSS v3.1 base score range: 0.1 - 3.9)

**Affected Asset:** `shop.acmecorp.com` -- https://shop.acmecorp.com/

**Check Type:** `missing_security_header`

**Program:** AcmeCorp Public Bug Bounty

**Date Reported:** 2026-08-18

---

## Summary

Missing Content-Security-Policy header on shop.acmecorp.com. Confirmed via a low-risk, non-destructive probe against
an in-scope asset, as verified by this program's published scope rules
(see `program_scope.py`).

## Steps to Reproduce

1. Send a GET request to https://shop.acmecorp.com/ and inspect the response headers (e.g. `curl -I https://shop.acmecorp.com/`).
2. Observe there is no Content-Security-Policy header present in the response.

## Proof of Concept / Evidence

```
GET https://shop.acmecorp.com/ -> response headers: {'X-Frame-Options': 'DENY', 'X-Content-Type-Options': 'nosniff', 'Strict-Transport-Security': 'max-age=63072000'}
```

## Impact

Without a CSP, the application has no defense-in-depth mitigation against reflected/stored XSS -- if any injection point is ever found on this host (now or in the future), an attacker's injected script runs with no browser-enforced restriction on script sources, inline execution, or data exfiltration targets.

## Suggested Fix

Add a restrictive Content-Security-Policy header (e.g. `default-src 'self'; script-src 'self'; object-src 'none'`) at the web server or application-framework level, starting in Report-Only mode to validate it doesn't break legitimate functionality before enforcing it.

---
*Generated automatically by the Bug Bounty Recon-to-Report Automation Toolkit.
All steps above were executed only against assets confirmed in-scope by
`scope_filter.py` prior to testing.*
