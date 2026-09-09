# Bug Bounty Report #04

**Title:** Exposed Git repository metadata at /.git/config on dev.acmecorp.com

**Severity:** High (approximate CVSS v3.1 base score range: 7.0 - 8.9)

**Affected Asset:** `dev.acmecorp.com` -- https://dev.acmecorp.com/.git/config

**Check Type:** `exposed_sensitive_path`

**Program:** AcmeCorp Public Bug Bounty

**Date Reported:** 2026-08-18

---

## Summary

Exposed Git repository metadata at /.git/config on dev.acmecorp.com. Confirmed via a low-risk, non-destructive probe against
an in-scope asset, as verified by this program's published scope rules
(see `program_scope.py`).

## Steps to Reproduce

1. Send an unauthenticated GET request to https://dev.acmecorp.com/.git/config
2. Observe a 200 OK response instead of a 404, with the raw file contents returned in the body (see Proof of Concept / evidence).

## Proof of Concept / Evidence

```
GET https://dev.acmecorp.com/.git/config -> HTTP 200
[core]
	repositoryformatversion = 0
[remote "origin"]
	url = git@github.com:acmecorp/webapp-internal.git

```

## Impact

The response body discloses the internal git remote URL and repository layout for dev.acmecorp.com. An attacker can use this to reconstruct source code history via `git-dumper`-style tooling and search it for additional hardcoded secrets or logic flaws.

## Suggested Fix

Remove /.git/config from the publicly served web root entirely (it should never be deployed outside the server's local filesystem), add a web-server rule (nginx `location ~ /\.(git|env) { deny all; }` or equivalent) to block access to dotfiles by default, and rotate any credentials that were exposed.

---
*Generated automatically by the Bug Bounty Recon-to-Report Automation Toolkit.
All steps above were executed only against assets confirmed in-scope by
`scope_filter.py` prior to testing.*
