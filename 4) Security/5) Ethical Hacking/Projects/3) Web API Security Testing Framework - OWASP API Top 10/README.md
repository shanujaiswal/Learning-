# Web API Security Testing Framework -- OWASP API Security Top 10

A local, offline lab that pairs a deliberately vulnerable Flask JSON REST API
with a small automated testing framework that confirms four classic
**OWASP API Security Top 10 (2023)** issues and compiles the results into a
scored security report -- the kind of exercise a mobile/API pentester runs
against a backend they've been handed for testing (see note 15, *Mobile App
and API Security Testing*).

Distinct from this repo's other, web-app-focused OWASP projects: everything
here is a stateless JSON REST API (no HTML, no cookies, no browser at all --
just `Authorization: Bearer <token>` requests, exactly how a mobile client or
SPA talks to its backend).

## Real-world scenario

"SocialFit" is an imaginary fitness/social app. Its mobile client is a thin
UI shell over a JSON API -- login, view a profile, view another user's public
info. You've been asked to do an API-focused security pass on the backend
before it ships. Instead of clicking through the app, you talk to the API
directly (as any real attacker or bug-bounty tester would) and check whether
each request is authorized the way the client assumes it is:

- Can user A read user B's private object just by changing an ID in the URL?
- Does the "hidden" admin endpoint actually check who's calling it, or does
  it just trust that the app UI hides the button from regular users?
- Does a normal profile request leak fields (password hashes, internal notes)
  that the app screen never shows?
- Can the login endpoint be hammered with guesses without ever being slowed
  down or blocked?

All four turn out to be "yes" -- this project builds the API, then builds the
scripts that prove it.

## Architecture

| Module | Role | Real-world equivalent |
|---|---|---|
| `vulnerable_api.py` | Local Flask JSON REST API with 4 endpoints, each with one deliberate OWASP API Top 10 flaw | A staging/pre-prod backend a pentester has been handed to assess |
| `bola_tester.py` | Logs in as user A, requests user B's object by ID substitution using A's own token | A BOLA/IDOR checker like Burp Suite's API scanning / manual Repeater ID-swap test |
| `function_auth_tester.py` | Logs in as a non-admin user, calls the admin-only endpoint directly | Testing whether an "admin panel" is really just a hidden UI button vs. a real server-side role check |
| `data_exposure_tester.py` | Makes a normal client request, greps the raw JSON for sensitive field names | Inspecting raw HTTP responses in Burp/Postman instead of trusting what the app UI renders |
| `rate_limit_tester.py` | Sends a bounded burst of login attempts, checks for HTTP 429 throttling | An API abuse / credential-stuffing brute-force test |
| `report.py` | Compiles the 4 structured findings into a scored Markdown report | The write-up phase of a real pentest/bug-bounty report |
| `main.py` | Starts the API in a background thread, runs all 4 testers, prints findings, generates the report | The orchestrator/runner tying the whole engagement together |

## Run it

Requires `flask` and `requests` (stdlib otherwise):

```bash
python main.py
```

This starts `vulnerable_api.py` on `127.0.0.1:5000` in a background thread,
runs all four testers against it, prints each confirmed finding, and writes
`api_security_report.md` next to these scripts.

You can also run any tester standalone against an already-running API
(`python vulnerable_api.py` in one terminal, then e.g.
`python bola_tester.py` in another).

## Verified result

Actual output from `python main.py` (Flask access-log lines omitted for
brevity):

```
==============================================================================
Web API Security Testing Framework -- OWASP API Security Top 10
==============================================================================

[*] Starting vulnerable_api.py in a background thread...
[*] API is up at http://127.0.0.1:5000

------------------------------------------------------------------------------
[*] BOLA / IDOR check -- API1:2023 Broken Object Level Authorization
    Logging in as user A (alice) only...
    Got a valid token for alice. Now requesting user B's object
    (id=2, 'bob') using ALICE'S token, not bob's.
    [!] VULNERABLE: HTTP 200 returned bob's record while authenticated as alice: {'bio': 'PRs a 5k every month, ask me how.', 'email': 'bob@example.local', 'id': 2, 'username': 'bob'}

------------------------------------------------------------------------------
[*] BFLA check -- API5:2023 Broken Function Level Authorization
    Logging in as a REGULAR (non-admin) user: bob...
    Confirmed role='user' (not admin). Now calling the admin-only
    GET /api/admin/users endpoint directly with this non-admin token...
    [!] VULNERABLE: HTTP 200 returned admin data to a non-admin user: {'users': [{'id': 1, 'role': 'user', 'username': 'alice'}, {'id': 2, 'role': 'user', 'username': 'bob'}, {'id': 3, 'role': 'admin', 'username': 'admin'}]}

------------------------------------------------------------------------------
[*] Excessive Data Exposure check -- API3:2023
    Logging in as alice and requesting their OWN profile
    (a completely normal, legitimate client request)...
    Raw response fields: ['avatar_url', 'bio', 'email', 'id', 'internal_notes', 'password', 'password_hash', 'role', 'ssn_last4', 'username']
    [!] VULNERABLE: response leaks sensitive fields the client never needed: ['password', 'password_hash', 'ssn_last4', 'internal_notes']

------------------------------------------------------------------------------
[*] Rate Limiting check -- API4:2023 Unrestricted Resource Consumption
    Firing 40 rapid POST /api/login attempts against username
    'alice' with a WRONG password each time (bounded burst, lab-safe)...
    Sent 40 requests in 0.21s (193.7 req/s).
    Status code breakdown: 401(invalid creds)=40, 429(throttled)=0
    [!] VULNERABLE: zero requests were throttled -- no rate limiting present.

==============================================================================
SUMMARY OF CONFIRMED FINDINGS
==============================================================================
  [CONFIRMED] API1:2023-BOLA: Broken Object Level Authorization (BOLA/IDOR) on GET /api/users/<id>
  [CONFIRMED] API5:2023-BFLA: Broken Function Level Authorization on GET /api/admin/users
  [CONFIRMED] API3:2023-EXCESSIVE-DATA-EXPOSURE: Excessive Data Exposure on GET /api/users/<id>/profile
  [CONFIRMED] API4:2023-RATE-LIMITING: Lack of Rate Limiting on POST /api/login

[*] 4/4 OWASP API Top 10 issues confirmed against this lab target.

[*] Report written to: ...\api_security_report.md
```

`api_security_report.md` (generated in the same run) scores this API
**20 / 100** with all 4 findings listed as `CONFIRMED VULNERABLE` (2 Critical,
2 High), each with real evidence (actual HTTP status codes and response
bodies captured during the run) and a remediation.

## Things to try changing

- **Fix the BOLA endpoint**: in `vulnerable_api.py`'s `get_user()`, add
  `if caller["id"] != user_id and caller["role"] != "admin": return jsonify({"error": "forbidden"}), 403`
  before returning data, then rerun `main.py` -- `bola_tester.py`'s finding
  should flip to `confirmed: False` because the server now returns 403.
- **Fix the BFLA endpoint**: add the same kind of `if caller["role"] != "admin": return ..., 403`
  check to `admin_list_users()` and watch `function_auth_tester.py`'s finding
  disappear.
- **Fix the data exposure endpoint**: replace `return jsonify(target)` in
  `get_user_profile()` with an explicit allow-list, e.g.
  `return jsonify({"id": target["id"], "username": target["username"], "bio": target["bio"], "avatar_url": target["avatar_url"]})`,
  and watch `data_exposure_tester.py` report zero sensitive fields found.
- **Add rate limiting**: install `flask-limiter` and decorate `/api/login`
  with e.g. `@limiter.limit("5 per minute")`; rerun `rate_limit_tester.py`
  and you should start seeing HTTP 429 responses partway through the burst.
- **Raise `REQUEST_COUNT`** in `rate_limit_tester.py` to see the burst take
  longer but still show zero throttling on the unfixed API (keep it bounded --
  this project is intentionally not an unbounded flood tool).
- **Add a fifth user with a different role** (e.g. `"moderator"`) and extend
  `function_auth_tester.py` to check a second privileged endpoint you add,
  to see how the same BFLA pattern generalizes.
