# Submission Summary -- AcmeCorp Public Bug Bounty

**Date:** 2026-08-18
**Total confirmed findings:** 6

| # | Severity | Title | Affected Asset |
|---|----------|-------|-----------------|
| 01 | High | Exposed environment/config file at /.env on portal.acmecorp.com | `portal.acmecorp.com` |
| 02 | High | Exposed Git repository metadata at /.git/config on dev.acmecorp.com | `dev.acmecorp.com` |
| 03 | Medium | Verbose stack trace / debug mode enabled on api.acmecorp.com | `api.acmecorp.com` |
| 04 | Medium | Verbose stack trace / debug mode enabled on dev.acmecorp.com | `dev.acmecorp.com` |
| 05 | Low | Missing Content-Security-Policy header on shop.acmecorp.com | `shop.acmecorp.com` |
| 06 | Low | Missing Content-Security-Policy header on dev.acmecorp.com | `dev.acmecorp.com` |

All findings above were produced strictly against hosts that survived `scope_filter.py`'s evaluation of `program_scope.py`'s published scope rules. No out-of-scope asset (see the program's exclusion list) was probed at any point in this run -- enforced both by filtering candidates before probing and by each probe independently re-checking scope before sending a request.

Individual per-finding reports are in this same directory, one Markdown file each, named `<index>-<check-type>-<host>.md`.
