# API Security Report -- OWASP API Security Top 10 Assessment

- **Target:** `http://127.0.0.1:5000` (local lab: vulnerable_api.py)
- **Generated:** 2026-08-17 11:00:35
- **Findings confirmed:** 4 / 4
- **Security score:** 20 / 100  (needs immediate remediation)

## Summary

This assessment exercised a local JSON REST API (`vulnerable_api.py`) against four OWASP API Security Top 10 (2023) issue classes using automated, evidence-producing test scripts. Each finding below was independently confirmed by making the actual HTTP request an attacker would make and inspecting the real response -- not inferred from source code alone.

| # | OWASP Category | Severity | Status |
|---|----------------|----------|--------|
| 1 | API1:2023 - Broken Object Level Authorization | Critical | CONFIRMED VULNERABLE |
| 2 | API5:2023 - Broken Function Level Authorization | Critical | CONFIRMED VULNERABLE |
| 3 | API3:2023 - Broken Object Property Level Authorization (Excessive Data Exposure) | High | CONFIRMED VULNERABLE |
| 4 | API4:2023 - Unrestricted Resource Consumption (Lack of Rate Limiting) | High | CONFIRMED VULNERABLE |

## Detailed Findings

### 1. API1:2023 - Broken Object Level Authorization

- **Endpoint / Title:** Broken Object Level Authorization (BOLA/IDOR) on GET /api/users/<id>
- **Severity:** Critical
- **Status:** CONFIRMED VULNERABLE
- **Evidence:** Authenticated as 'alice' (own object id != 2), requested GET /api/users/2 with alice's token and received HTTP 200 with {'bio': 'PRs a 5k every month, ask me how.', 'email': 'bob@example.local', 'id': 2, 'username': 'bob'} -- another user's private object, when this should have been HTTP 403 Forbidden.
- **Remediation:** Every object-fetching endpoint must independently verify server-side that the authenticated caller's id equals the requested resource's owner id (or that the caller holds an explicit admin/shared-access grant) before returning data -- e.g. `if caller['id'] != user_id and caller['role'] != 'admin': return 403`. Never rely on IDs being 'hard to guess'.

### 2. API5:2023 - Broken Function Level Authorization

- **Endpoint / Title:** Broken Function Level Authorization on GET /api/admin/users
- **Severity:** Critical
- **Status:** CONFIRMED VULNERABLE
- **Evidence:** Authenticated as 'bob' with role='user' (non-admin), called GET /api/admin/users and received HTTP 200 with {'users': [{'id': 1, 'role': 'user', 'username': 'alice'}, {'id': 2, 'role': 'user', 'username': 'bob'}, {'id': 3, 'role': 'admin', 'username': 'admin'}]} -- the admin-only endpoint has no server-side role check, so this should have been HTTP 403.
- **Remediation:** Enforce role/permission checks server-side on every privileged endpoint (e.g. `if caller['role'] != 'admin': return 403`), independent of whatever the client UI shows or hides. A hidden button is not an access control.

### 3. API3:2023 - Broken Object Property Level Authorization (Excessive Data Exposure)

- **Endpoint / Title:** Excessive Data Exposure on GET /api/users/<id>/profile
- **Severity:** High
- **Status:** CONFIRMED VULNERABLE
- **Evidence:** GET /api/users/1/profile (own profile, legitimate request) returned sensitive fields never rendered by the client UI: ['password', 'password_hash', 'ssn_last4', 'internal_notes']. Example values: {'password': 'alicepw123', 'password_hash': 'a2ffdea4c2b348af09ef095df9bc615916d651dfdfce192e147f3cde18405e63', 'ssn_last4': '4321', 'internal_notes': 'Flagged by support 2026-02: chargeback dispute, see ticket #8841.'}.
- **Remediation:** Define an explicit allow-list output schema/DTO per endpoint (e.g. a dataclass, pydantic model, or marshmallow schema) and serialize only the fields the client legitimately needs. Never return the raw internal record and trust the client to ignore extra fields such as password hashes, internal notes, or PII.

### 4. API4:2023 - Unrestricted Resource Consumption (Lack of Rate Limiting)

- **Endpoint / Title:** Lack of Rate Limiting on POST /api/login
- **Severity:** High
- **Status:** CONFIRMED VULNERABLE
- **Evidence:** Sent 40 POST /api/login requests in 0.21s (193.7 req/s) against the same username with wrong passwords each time. All 40 came back HTTP 401 (processed normally) with zero HTTP 429 responses and no server-side slowdown -- the endpoint applies no rate limiting, lockout, or backoff of any kind, so an attacker script could continue brute-forcing indefinitely.
- **Remediation:** Apply per-account and per-IP rate limiting (e.g. Flask-Limiter, or an API gateway/WAF rule) such as '5 attempts per minute' on authentication and OTP-style endpoints. Return HTTP 429 with a Retry-After header once exceeded, and add exponential backoff / temporary lockout after repeated failures.

## Methodology

- `bola_tester.py` -- authenticated as user A, requested user B's object by id substitution using user A's own valid token.
- `function_auth_tester.py` -- authenticated as a non-admin user, called the admin-only endpoint directly with that user's token.
- `data_exposure_tester.py` -- made a normal, legitimate client request and inspected the raw JSON body for sensitive field names never rendered by the UI.
- `rate_limit_tester.py` -- sent a bounded burst of rapid login attempts and checked whether any were throttled (HTTP 429).

*This report was generated automatically by `report.py` against a deliberately vulnerable local lab target. It is a training artifact, not a real security assessment of any production system.*
