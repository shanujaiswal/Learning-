"""
bola_tester.py -- API1:2023 Broken Object Level Authorization (BOLA/IDOR) Checker
==================================================================================

LEGAL / ETHICAL SCOPE
----------------------
Talks ONLY to http://127.0.0.1:5000 (vulnerable_api.py, run by you). Do not
point this at any other host.

WHAT THIS DEMONSTRATES
------------------------
Logs in as user A ("alice"), then uses ALICE'S OWN valid token to request
user B's ("bob's") object at GET /api/users/<id> by simply changing the id
in the URL -- exactly the note 15 "swap the ID and resend with your own
valid token" BOLA test. If the response is 200 with bob's data instead of a
403, that confirms the server never checks that the token owner matches the
requested object -- a Broken Object Level Authorization vulnerability.

Returns a dict result via check_bola() so main.py / report.py can consume a
structured finding instead of re-parsing printed text.
"""

from __future__ import annotations

import requests

TARGET = "http://127.0.0.1:5000"  # local lab target ONLY -- do not change


def _login(username: str, password: str) -> str:
    resp = requests.post(f"{TARGET}/api/login", json={"username": username, "password": password})
    resp.raise_for_status()
    return resp.json()["token"]


def check_bola(user_a=("alice", "alicepw123"), user_b_id: int = 2, user_b_username: str = "bob") -> dict:
    print("[*] BOLA / IDOR check -- API1:2023 Broken Object Level Authorization")
    print(f"    Logging in as user A ({user_a[0]}) only...")
    token_a = _login(*user_a)
    print(f"    Got a valid token for {user_a[0]}. Now requesting user B's object")
    print(f"    (id={user_b_id}, '{user_b_username}') using ALICE'S token, not bob's.")

    resp = requests.get(
        f"{TARGET}/api/users/{user_b_id}",
        headers={"Authorization": f"Bearer {token_a}"},
    )

    finding = {
        "id": "API1:2023-BOLA",
        "title": "Broken Object Level Authorization (BOLA/IDOR) on GET /api/users/<id>",
        "confirmed": False,
        "evidence": "",
    }

    if resp.status_code == 200:
        data = resp.json()
        leaked_correct_user = data.get("username") == user_b_username
        finding["confirmed"] = leaked_correct_user
        finding["evidence"] = (
            f"Authenticated as '{user_a[0]}' (own object id != {user_b_id}), requested "
            f"GET /api/users/{user_b_id} with alice's token and received HTTP 200 with "
            f"{data!r} -- another user's private object, when this should have been "
            f"HTTP 403 Forbidden."
        )
        print(f"    [!] VULNERABLE: HTTP 200 returned bob's record while authenticated as alice: {data}")
    else:
        finding["evidence"] = f"HTTP {resp.status_code} -- server correctly rejected cross-user access."
        print(f"    [+] Not vulnerable here: server responded HTTP {resp.status_code} (correct behavior).")

    print()
    return finding


if __name__ == "__main__":
    result = check_bola()
    print(result)
