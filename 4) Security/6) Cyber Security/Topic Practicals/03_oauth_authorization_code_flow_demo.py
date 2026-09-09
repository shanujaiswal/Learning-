"""
03 - OAuth 2.0 Authorization Code Flow Demo (real end-to-end HTTP)
Chapter: 06 Identity and Access Management (IAM), SSO and OAuth

WHAT THIS DEMONSTRATES
-----------------------
This spins up TWO real local Flask servers and drives an actual HTTP
Authorization Code flow between them -- not a diagram, not a mock function
call. It exercises the exact mechanics the Theory chapter describes:

    - Authorization Server ("auth_server", http://127.0.0.1:5001)
        GET  /authorize  -> validates client_id/redirect_uri, "logs the user
                             in" (auto-approved for this demo), issues a
                             short-lived one-time authorization CODE, and
                             302-redirects back to the client with
                             ?code=...&state=...
        POST /token      -> the CLIENT (server-to-server, not the browser)
                             exchanges the code + client_secret for an
                             ACCESS TOKEN. Codes are single-use and expire.
        GET  /userinfo   -> a protected resource; requires "Authorization:
                             Bearer <access_token>".

    - Client application ("client_app", http://127.0.0.1:5000)
        GET  /login      -> generates a random anti-CSRF `state` value,
                             stores it server-side, and redirects the
                             browser to the auth server's /authorize.
        GET  /callback   -> the auth server redirects back here with
                             ?code=...&state=.... The client verifies the
                             `state` matches what it generated (this is
                             what stops CSRF/code-injection attacks), then
                             exchanges the code for a token and calls
                             /userinfo to prove the token works.

The `requests.Session()` used in main() plays the role of the user's
browser: it follows the real HTTP 302 redirects between the two servers.

Dependencies:
    pip install flask requests

Run:
    python 03_oauth_authorization_code_flow_demo.py
"""

from __future__ import annotations

import secrets
import threading
import time
from datetime import datetime, timedelta

import requests
from flask import Flask, jsonify, redirect, request

AUTH_SERVER_HOST, AUTH_SERVER_PORT = "127.0.0.1", 5001
CLIENT_HOST, CLIENT_PORT = "127.0.0.1", 5000
AUTH_SERVER_BASE = f"http://{AUTH_SERVER_HOST}:{AUTH_SERVER_PORT}"
CLIENT_BASE = f"http://{CLIENT_HOST}:{CLIENT_PORT}"

# A single pre-registered OAuth client (normally set up once via a dev console).
REGISTERED_CLIENT_ID = "demo-client-123"
REGISTERED_CLIENT_SECRET = "s3cr3t-known-only-to-client-and-auth-server"
REGISTERED_REDIRECT_URI = f"{CLIENT_BASE}/callback"

CODE_TTL = timedelta(minutes=2)
TOKEN_TTL = timedelta(minutes=10)

# ---------------------------------------------------------------------------
# Authorization Server
# ---------------------------------------------------------------------------

auth_server = Flask("auth_server")

# In-memory "databases" for this demo. A real IdP would use a real DB.
_auth_codes: dict[str, dict] = {}     # code -> {client_id, redirect_uri, expires_at, used}
_access_tokens: dict[str, dict] = {}  # token -> {client_id, expires_at}
_registered_clients = {
    REGISTERED_CLIENT_ID: {
        "client_secret": REGISTERED_CLIENT_SECRET,
        "redirect_uri": REGISTERED_REDIRECT_URI,
    }
}
# Mock "logged in" user profile the auth server will vouch for.
_mock_user = {"sub": "user-42", "name": "Ada Lovelace", "email": "ada@example.com"}


@auth_server.get("/authorize")
def authorize():
    response_type = request.args.get("response_type")
    client_id = request.args.get("client_id")
    redirect_uri = request.args.get("redirect_uri")
    state = request.args.get("state")
    scope = request.args.get("scope", "")

    client = _registered_clients.get(client_id)
    if response_type != "code" or not client:
        return jsonify(error="invalid_request or unknown client_id"), 400
    if redirect_uri != client["redirect_uri"]:
        return jsonify(error="redirect_uri does not match registered value"), 400

    # In a real IdP this is where the user would see a login + consent screen.
    # We auto-approve here since this is a scripted demo, not an interactive one.
    print(f"[auth_server] /authorize: user auto-approves client "
          f"'{client_id}' for scope='{scope}'")

    code = secrets.token_urlsafe(24)
    _auth_codes[code] = {
        "client_id": client_id,
        "redirect_uri": redirect_uri,
        "expires_at": datetime.utcnow() + CODE_TTL,
        "used": False,
    }
    print(f"[auth_server] issued one-time authorization code={code[:12]}...")

    return redirect(f"{redirect_uri}?code={code}&state={state}")


@auth_server.post("/token")
def token():
    grant_type = request.form.get("grant_type")
    code = request.form.get("code")
    redirect_uri = request.form.get("redirect_uri")
    client_id = request.form.get("client_id")
    client_secret = request.form.get("client_secret")

    client = _registered_clients.get(client_id)
    if grant_type != "authorization_code" or not client:
        return jsonify(error="invalid_grant_type or unknown client"), 400
    if client_secret != client["client_secret"]:
        print("[auth_server] REJECTED /token: bad client_secret")
        return jsonify(error="invalid_client"), 401

    record = _auth_codes.get(code)
    if not record or record["used"]:
        print("[auth_server] REJECTED /token: code missing/already used")
        return jsonify(error="invalid_grant (code reused or unknown)"), 400
    if datetime.utcnow() > record["expires_at"]:
        print("[auth_server] REJECTED /token: code expired")
        return jsonify(error="invalid_grant (code expired)"), 400
    if record["client_id"] != client_id or record["redirect_uri"] != redirect_uri:
        print("[auth_server] REJECTED /token: client_id/redirect_uri mismatch")
        return jsonify(error="invalid_grant (client/redirect mismatch)"), 400

    record["used"] = True  # codes are single-use
    access_token = secrets.token_urlsafe(32)
    _access_tokens[access_token] = {
        "client_id": client_id,
        "expires_at": datetime.utcnow() + TOKEN_TTL,
    }
    print(f"[auth_server] code verified -> issuing access_token={access_token[:12]}...")

    return jsonify(
        access_token=access_token,
        token_type="Bearer",
        expires_in=int(TOKEN_TTL.total_seconds()),
    )


@auth_server.get("/userinfo")
def userinfo():
    auth_header = request.headers.get("Authorization", "")
    if not auth_header.startswith("Bearer "):
        return jsonify(error="missing bearer token"), 401
    access_token = auth_header.removeprefix("Bearer ")
    record = _access_tokens.get(access_token)
    if not record:
        return jsonify(error="invalid access token"), 401
    if datetime.utcnow() > record["expires_at"]:
        return jsonify(error="access token expired"), 401
    print(f"[auth_server] /userinfo: valid token, returning profile for "
          f"client '{record['client_id']}'")
    return jsonify(_mock_user)


# ---------------------------------------------------------------------------
# Client Application
# ---------------------------------------------------------------------------

client_app = Flask("client_app")

# Anti-CSRF state values this client has issued and is waiting to see echoed back.
_pending_states: set[str] = set()


@client_app.get("/login")
def login():
    state = secrets.token_urlsafe(16)
    _pending_states.add(state)
    print(f"[client_app] /login: generated anti-CSRF state={state[:12]}..., "
          f"redirecting browser to auth server")

    auth_url = (
        f"{AUTH_SERVER_BASE}/authorize"
        f"?response_type=code"
        f"&client_id={REGISTERED_CLIENT_ID}"
        f"&redirect_uri={REGISTERED_REDIRECT_URI}"
        f"&scope=profile"
        f"&state={state}"
    )
    return redirect(auth_url)


@client_app.get("/callback")
def callback():
    code = request.args.get("code")
    state = request.args.get("state")

    if state not in _pending_states:
        print("[client_app] REJECTED /callback: unknown/replayed state "
              "(possible CSRF) -- aborting flow")
        return jsonify(error="invalid state parameter"), 400
    _pending_states.discard(state)  # one-time use
    print(f"[client_app] /callback: state verified OK, received code={code[:12]}...")

    # Server-to-server exchange: code + client_secret -> access_token.
    # This request never goes through the user's browser.
    token_resp = requests.post(f"{AUTH_SERVER_BASE}/token", data={
        "grant_type": "authorization_code",
        "code": code,
        "redirect_uri": REGISTERED_REDIRECT_URI,
        "client_id": REGISTERED_CLIENT_ID,
        "client_secret": REGISTERED_CLIENT_SECRET,
    })
    if token_resp.status_code != 200:
        return jsonify(error="token exchange failed", detail=token_resp.json()), 400
    token_data = token_resp.json()
    access_token = token_data["access_token"]
    print(f"[client_app] token exchange OK -> access_token={access_token[:12]}...")

    # Use the access token to call the protected resource.
    profile_resp = requests.get(f"{AUTH_SERVER_BASE}/userinfo",
                                 headers={"Authorization": f"Bearer {access_token}"})
    profile = profile_resp.json()
    print(f"[client_app] /userinfo call OK -> {profile}")

    return jsonify(
        message="OAuth 2.0 Authorization Code flow completed successfully",
        state_verified=True,
        access_token=access_token,
        token_type=token_data["token_type"],
        expires_in=token_data["expires_in"],
        user_profile=profile,
    )


# ---------------------------------------------------------------------------
# Runner: start both servers, then simulate the browser walking the flow
# ---------------------------------------------------------------------------

def _run_flask_app(app: Flask, host: str, port: int) -> None:
    app.run(host=host, port=port, debug=False, use_reloader=False)


def main() -> None:
    print("Starting mock Authorization Server on "
          f"{AUTH_SERVER_BASE} and Client App on {CLIENT_BASE} ...\n")

    auth_thread = threading.Thread(
        target=_run_flask_app, args=(auth_server, AUTH_SERVER_HOST, AUTH_SERVER_PORT),
        daemon=True)
    client_thread = threading.Thread(
        target=_run_flask_app, args=(client_app, CLIENT_HOST, CLIENT_PORT),
        daemon=True)
    auth_thread.start()
    client_thread.start()

    # Give both dev servers a moment to bind their sockets.
    time.sleep(1.0)

    print("=" * 70)
    print("Simulating a user visiting the client app and clicking 'Log in'")
    print("(a requests.Session plays the role of the user's browser, "
          "following real HTTP redirects)")
    print("=" * 70 + "\n")

    browser = requests.Session()
    final_response = browser.get(f"{CLIENT_BASE}/login", allow_redirects=True)

    print("\nRedirect chain followed by the browser:")
    for hop in final_response.history:
        print(f"  {hop.status_code} {hop.url}")
    print(f"  {final_response.status_code} {final_response.url} (final)\n")

    print("Final response body from client_app's /callback:")
    print(final_response.json())


if __name__ == "__main__":
    main()
