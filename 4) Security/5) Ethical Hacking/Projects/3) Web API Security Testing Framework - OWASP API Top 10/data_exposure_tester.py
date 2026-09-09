"""
data_exposure_tester.py -- API3:2023 Excessive Data Exposure Checker
======================================================================

LEGAL / ETHICAL SCOPE
----------------------
Talks ONLY to http://127.0.0.1:5000 (vulnerable_api.py, run by you). Do not
point this at any other host.

WHAT THIS DEMONSTRATES
------------------------
Logs in normally and calls the ordinary, client-facing GET
/api/users/<id>/profile endpoint -- exactly the request the mobile app's own
profile screen would make for the logged-in user's own profile -- then
inspects the RAW JSON response body for sensitive field names that the UI
never displays and the client never needed (password_hash, plaintext
password, ssn_last4, internal_notes). Finding any of them confirms Excessive
Data Exposure: the server is trusting the client to just "not show" extra
fields instead of never sending them in the first place.
"""

from __future__ import annotations

import requests

TARGET = "http://127.0.0.1:5000"  # local lab target ONLY -- do not change

# Field names a normal profile-screen response should NEVER contain. This is
# the analogue of grepping decompiled mobile client code for secrets in note
# 15, applied instead to a live API response body.
SENSITIVE_FIELDS = ["password", "password_hash", "ssn_last4", "internal_notes"]


def _login(username: str, password: str) -> tuple[str, int]:
    resp = requests.post(f"{TARGET}/api/login", json={"username": username, "password": password})
    resp.raise_for_status()
    body = resp.json()
    return body["token"], body["user_id"]


def check_data_exposure(user=("alice", "alicepw123")) -> dict:
    print("[*] Excessive Data Exposure check -- API3:2023")
    print(f"    Logging in as {user[0]} and requesting their OWN profile")
    print("    (a completely normal, legitimate client request)...")
    token, user_id = _login(*user)

    resp = requests.get(
        f"{TARGET}/api/users/{user_id}/profile",
        headers={"Authorization": f"Bearer {token}"},
    )
    resp.raise_for_status()
    data = resp.json()

    exposed = [f for f in SENSITIVE_FIELDS if f in data]

    finding = {
        "id": "API3:2023-EXCESSIVE-DATA-EXPOSURE",
        "title": "Excessive Data Exposure on GET /api/users/<id>/profile",
        "confirmed": False,
        "evidence": "",
    }

    print(f"    Raw response fields: {sorted(data.keys())}")

    if exposed:
        finding["confirmed"] = True
        finding["evidence"] = (
            f"GET /api/users/{user_id}/profile (own profile, legitimate request) returned "
            f"sensitive fields never rendered by the client UI: {exposed}. Example values: "
            f"{ {k: data[k] for k in exposed} }."
        )
        print(f"    [!] VULNERABLE: response leaks sensitive fields the client never needed: {exposed}")
    else:
        finding["evidence"] = "No sensitive field names found in the response body."
        print("    [+] Not vulnerable here: response only contains expected public fields.")

    print()
    return finding


if __name__ == "__main__":
    result = check_data_exposure()
    print(result)
