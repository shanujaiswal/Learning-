# Bug Bounty Report #05

**Title:** Verbose stack trace / debug mode enabled on dev.acmecorp.com

**Severity:** Medium (approximate CVSS v3.1 base score range: 4.0 - 6.9)

**Affected Asset:** `dev.acmecorp.com` -- https://dev.acmecorp.com/trigger-error

**Check Type:** `debug_stack_trace`

**Program:** AcmeCorp Public Bug Bounty

**Date Reported:** 2026-08-18

---

## Summary

Verbose stack trace / debug mode enabled on dev.acmecorp.com. Confirmed via a low-risk, non-destructive probe against
an in-scope asset, as verified by this program's published scope rules
(see `program_scope.py`).

## Steps to Reproduce

1. Trigger an application error on https://dev.acmecorp.com (e.g. by requesting an endpoint with a malformed parameter, such as /trigger-error).
2. Observe the server returns a full stack trace / interpreter traceback in the response body instead of a generic error page.

## Proof of Concept / Evidence

```
GET https://dev.acmecorp.com/trigger-error -> HTTP 500
Traceback (most recent call last):
  File "app.py", line 88, in checkout
    total = price / discount
ZeroDivisionError: division by zero
Werkzeug debugger enabled (dev.acmecorp.com internal build 2026.1)

```

## Impact

The stack trace discloses internal implementation details -- file paths, framework/language version, class and method names, and (for the debugger-enabled case) potentially a remote code execution vector via an interactive debugger console. At minimum this materially assists an attacker in fingerprinting the stack and crafting more targeted exploits against known framework-version CVEs.

## Suggested Fix

Disable debug/development mode in the production and any internet-reachable non-production configuration (e.g. Flask/Werkzeug `debug=False`, Django `DEBUG=False`), and configure a generic branded 500 error page that logs the real traceback server-side only.

---
*Generated automatically by the Bug Bounty Recon-to-Report Automation Toolkit.
All steps above were executed only against assets confirmed in-scope by
`scope_filter.py` prior to testing.*
