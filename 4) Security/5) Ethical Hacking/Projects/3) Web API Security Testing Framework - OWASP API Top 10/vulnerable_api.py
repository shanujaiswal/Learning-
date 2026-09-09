"""
vulnerable_api.py -- Deliberately Vulnerable JSON REST API (OWASP API Top 10 Lab)
==================================================================================

LEGAL / ETHICAL SCOPE
----------------------
This Flask app is a local, offline lab target. It binds to 127.0.0.1 only and
must never be exposed to a real network. Every other script in this project
talks ONLY to this app. Do not repurpose any of this code against a system you
do not own or are not explicitly authorized to test.

WHAT THIS IS
-------------
A small "SocialFit" style JSON REST API backing an imaginary fitness/social
mobile app -- the kind of backend note 15 (Mobile App and API Security
Testing) describes as "a thin UI shell over a REST API doing all the real
work". It intentionally reproduces four OWASP API Security Top 10 (2023)
issues so the tester scripts in this project have something real to confirm:

  API1:2023 - Broken Object Level Authorization (BOLA / IDOR)
      GET /api/users/<id>            -- any logged-in user can fetch ANY
                                         user's object just by changing <id>
                                         in the URL. No ownership check.

  API5:2023 - Broken Function Level Authorization (BFLA)
      GET /api/admin/users           -- an admin-only endpoint that never
                                         actually checks the caller's role
                                         server-side; any authenticated user
                                         (or even the client "hiding" the
                                         admin button) can call it directly.

  API3:2023 - Broken Object Property Level Authorization / Excessive Data
              Exposure
      GET /api/users/<id>/profile    -- returns the FULL internal user
                                         record, including password_hash,
                                         internal_notes and ssn_last4, when
                                         the mobile client only ever
                                         displays username/bio/avatar.

  API4:2023 - Unrestricted Resource Consumption (Lack of Rate Limiting)
      POST /api/login                -- no throttling of any kind, so a
                                         script can brute-force credentials
                                         or hammer OTP-style endpoints
                                         indefinitely.

None of this is hidden behind clever logic -- each vulnerability is a plain,
commented gap in the handler so it reads as a teaching artifact, not an
obfuscated puzzle. A `# FIX:` comment next to each one describes the real
server-side control that would close it.

ENDPOINTS
----------
  POST /api/login              {"username", "password"} -> {"token"}
  GET  /api/users/<id>          Authorization: Bearer <token>   [BOLA]
  GET  /api/users/<id>/profile  Authorization: Bearer <token>   [Excessive Data Exposure]
  GET  /api/admin/users         Authorization: Bearer <token>   [BFLA]
  GET  /api/health              liveness probe used by main.py to know the
                                 server is up before testers start hammering it

RUN
----
    python vulnerable_api.py
(main.py normally starts this for you in a background thread.)
"""

from __future__ import annotations

import hashlib
import time

from flask import Flask, jsonify, request

app = Flask(__name__)

# ----------------------------------------------------------------------------
# "Database" -- an in-memory user table. Passwords are hashed at rest (good),
# but that good practice is undone below by an endpoint that ships the hash
# straight to the client anyway (bad) -- a realistic combination: teams often
# get ONE control right (hashing at rest) while missing the DIFFERENT control
# that actually matters here (never exposing that hash over the wire at all).
# ----------------------------------------------------------------------------


def _hash(password: str) -> str:
    return hashlib.sha256(password.encode()).hexdigest()


USERS = {
    1: {
        "id": 1,
        "username": "alice",
        "password": "alicepw123",
        "password_hash": _hash("alicepw123"),
        "role": "user",
        "bio": "Marathon training, week 6.",
        "avatar_url": "https://example.local/avatars/alice.png",
        "email": "alice@example.local",
        "ssn_last4": "4321",
        "internal_notes": "Flagged by support 2026-02: chargeback dispute, see ticket #8841.",
    },
    2: {
        "id": 2,
        "username": "bob",
        "password": "bobsecretpw",
        "password_hash": _hash("bobsecretpw"),
        "role": "user",
        "bio": "PRs a 5k every month, ask me how.",
        "avatar_url": "https://example.local/avatars/bob.png",
        "email": "bob@example.local",
        "ssn_last4": "9911",
        "internal_notes": "VIP tier customer, do not suspend without manager approval.",
    },
    3: {
        "id": 3,
        "username": "admin",
        "password": "sup3r-admin-pw",
        "password_hash": _hash("sup3r-admin-pw"),
        "role": "admin",
        "bio": "SocialFit platform administrator.",
        "avatar_url": "https://example.local/avatars/admin.png",
        "email": "admin@example.local",
        "ssn_last4": "0000",
        "internal_notes": "Root operator account. Rotate credentials quarterly.",
    },
}

# token -> user_id. Tokens are opaque random-looking strings, NOT JWTs, since
# this lab is about authorization logic bugs, not token-format attacks (note
# 15's JWT section covers that separate topic).
SESSIONS: dict[str, int] = {}

# Every login attempt is recorded here so rate_limit_tester.py's confirmation
# is based on server-observed truth, not just "the client didn't get blocked".
LOGIN_ATTEMPTS: list[dict] = []


def _issue_token(user_id: int) -> str:
    token = hashlib.sha256(f"{user_id}-{time.time_ns()}".encode()).hexdigest()
    SESSIONS[token] = user_id
    return token


def _authenticated_user():
    """Resolve the caller's user record from a Bearer token, or None."""
    auth = request.headers.get("Authorization", "")
    if not auth.startswith("Bearer "):
        return None
    token = auth.removeprefix("Bearer ").strip()
    user_id = SESSIONS.get(token)
    if user_id is None:
        return None
    return USERS.get(user_id)


@app.get("/api/health")
def health():
    return jsonify({"status": "ok"})


@app.post("/api/login")
def login():
    # VULNERABLE (API4:2023 - Unrestricted Resource Consumption): no rate
    # limiting, no lockout, no delay, no CAPTCHA -- every request is served
    # as fast as the server can, regardless of how many failed attempts came
    # from the same client seconds ago. A script can brute-force a password
    # or hammer an OTP endpoint shaped like this one indefinitely.
    #
    # FIX: apply a per-account and per-IP rate limit (e.g. Flask-Limiter,
    # or an API gateway/WAF rule) such as "5 attempts per minute", return
    # HTTP 429 with a Retry-After header once exceeded, and add exponential
    # backoff / temporary account lockout after repeated failures.
    body = request.get_json(silent=True) or {}
    username = body.get("username", "")
    password = body.get("password", "")

    LOGIN_ATTEMPTS.append({"username": username, "ts": time.time()})

    user = next((u for u in USERS.values() if u["username"] == username), None)
    if user is None or user["password"] != password:
        return jsonify({"error": "invalid credentials"}), 401

    token = _issue_token(user["id"])
    return jsonify({"token": token, "user_id": user["id"], "role": user["role"]})


@app.get("/api/users/<int:user_id>")
def get_user(user_id: int):
    # VULNERABLE (API1:2023 - Broken Object Level Authorization / BOLA): the
    # handler checks that SOME valid token was presented, but never checks
    # that the token's owner is actually allowed to see THIS object. Any
    # logged-in user (alice) can request bob's or admin's record just by
    # changing the id in the URL -- the textbook BOLA scenario from note 15's
    # "GET /api/v2/users/8843/workouts" example.
    #
    # FIX: after resolving the caller, require
    #     if caller["id"] != user_id and caller["role"] != "admin": return 403
    # before returning any data.
    caller = _authenticated_user()
    if caller is None:
        return jsonify({"error": "authentication required"}), 401

    target = USERS.get(user_id)
    if target is None:
        return jsonify({"error": "not found"}), 404

    # Returns the object regardless of whether caller["id"] == user_id.
    return jsonify(
        {
            "id": target["id"],
            "username": target["username"],
            "email": target["email"],
            "bio": target["bio"],
        }
    )


@app.get("/api/users/<int:user_id>/profile")
def get_user_profile(user_id: int):
    # VULNERABLE (API3:2023 - Excessive Data Exposure): the mobile client's
    # profile screen only ever renders username/bio/avatar, but this handler
    # is a lazy `return jsonify(full_record)` that ships the ENTIRE internal
    # user object -- including password_hash, ssn_last4 and internal_notes --
    # trusting the client to just "not display" the extra fields. A caller
    # inspecting the raw HTTP response (curl, Burp, or this project's
    # data_exposure_tester.py) sees everything regardless of what the app UI
    # shows.
    #
    # FIX: define an explicit output schema / DTO (e.g. a dataclass or
    # marshmallow schema with only the allowed public fields) and serialize
    # THAT, never the raw internal record -- "allow-list what goes out",
    # never "the client will just ignore extra fields".
    caller = _authenticated_user()
    if caller is None:
        return jsonify({"error": "authentication required"}), 401

    target = USERS.get(user_id)
    if target is None:
        return jsonify({"error": "not found"}), 404

    return jsonify(target)  # <-- ships every field, including secrets


@app.get("/api/admin/users")
def admin_list_users():
    # VULNERABLE (API5:2023 - Broken Function Level Authorization / BFLA):
    # this admin-only endpoint only checks "is there a valid token at all",
    # exactly like note 15's M3 mobile finding ("client-side-only auth
    # checks that can be bypassed by directly calling the backend API") --
    # the mobile/web client simply hides the "Admin" button for non-admin
    # roles, but the SERVER never independently verifies caller["role"] here,
    # so any authenticated regular user can call it directly and get the
    # full user listing.
    #
    # FIX: require
    #     if caller["role"] != "admin": return 403
    # server-side, independent of whatever the client UI shows or hides.
    caller = _authenticated_user()
    if caller is None:
        return jsonify({"error": "authentication required"}), 401

    # No role check here at all -- BFLA.
    return jsonify(
        {"users": [{"id": u["id"], "username": u["username"], "role": u["role"]} for u in USERS.values()]}
    )


if __name__ == "__main__":
    print("[*] Starting vulnerable_api.py on http://127.0.0.1:5000 (lab use only)")
    app.run(host="127.0.0.1", port=5000, debug=False)
