# Bug Bounty Report #02

**Title:** Exposed environment/config file at /.env on portal.acmecorp.com

**Severity:** High (approximate CVSS v3.1 base score range: 7.0 - 8.9)

**Affected Asset:** `portal.acmecorp.com` -- https://portal.acmecorp.com/.env

**Check Type:** `exposed_sensitive_path`

**Program:** AcmeCorp Public Bug Bounty

**Date Reported:** 2026-08-18

---

## Summary

Exposed environment/config file at /.env on portal.acmecorp.com. Confirmed via a low-risk, non-destructive probe against
an in-scope asset, as verified by this program's published scope rules
(see `program_scope.py`).

## Steps to Reproduce

1. Send an unauthenticated GET request to https://portal.acmecorp.com/.env
2. Observe a 200 OK response instead of a 404, with the raw file contents returned in the body (see Proof of Concept / evidence).

## Proof of Concept / Evidence

```
GET https://portal.acmecorp.com/.env -> HTTP 200
SESSION_SECRET=portalSessionKey2026
SMTP_PASSWORD=MailPass!42

```

## Impact

The response body discloses live credentials and secrets (database password, session/SMTP secrets, or cloud access keys) for portal.acmecorp.com. An attacker can use this to directly authenticate to backend services, read/write production data, or pivot into cloud infrastructure using the leaked keys.

## Suggested Fix

Remove /.env from the publicly served web root entirely (it should never be deployed outside the server's local filesystem), add a web-server rule (nginx `location ~ /\.(git|env) { deny all; }` or equivalent) to block access to dotfiles by default, and rotate any credentials that were exposed.

---
*Generated automatically by the Bug Bounty Recon-to-Report Automation Toolkit.
All steps above were executed only against assets confirmed in-scope by
`scope_filter.py` prior to testing.*
