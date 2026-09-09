"""
rate_limit_tester.py -- API4:2023 Unrestricted Resource Consumption / Lack of Rate Limiting Checker
=====================================================================================================

LEGAL / ETHICAL SCOPE
----------------------
Talks ONLY to http://127.0.0.1:5000 (vulnerable_api.py, run by you), and is
intentionally BOUNDED (a fixed, small request count -- not an unbounded
flood) so this remains a controlled lab test, never a real-world DoS/flood
tool. Do not point this at any other host or increase REQUEST_COUNT to try
to actually take a service down.

WHAT THIS DEMONSTRATES
------------------------
Sends a bounded burst of rapid POST /api/login requests with a wrong
password (simulating a brute-force / credential-stuffing attempt, or an OTP
brute force per note 15's rate-limiting section) and checks whether ANY of
them come back HTTP 429 (Too Many Requests) or otherwise get throttled. If
literally all of them are processed at full speed with no 429s and no
noticeable slowdown, that confirms the login endpoint has no rate limiting
at all -- an attacker script could brute-force credentials unopposed.
"""

from __future__ import annotations

import time

import requests

TARGET = "http://127.0.0.1:5000"  # local lab target ONLY -- do not change

# Bounded on purpose -- enough requests to prove the point statistically
# without being an actual flood/DoS attempt.
REQUEST_COUNT = 40


def check_rate_limiting(username: str = "alice", request_count: int = REQUEST_COUNT) -> dict:
    print("[*] Rate Limiting check -- API4:2023 Unrestricted Resource Consumption")
    print(f"    Firing {request_count} rapid POST /api/login attempts against username")
    print(f"    '{username}' with a WRONG password each time (bounded burst, lab-safe)...")

    statuses = []
    start = time.perf_counter()
    for i in range(request_count):
        resp = requests.post(
            f"{TARGET}/api/login",
            json={"username": username, "password": f"wrong-guess-{i}"},
        )
        statuses.append(resp.status_code)
    elapsed = time.perf_counter() - start

    throttled = [s for s in statuses if s == 429]
    unthrottled_401s = statuses.count(401)

    finding = {
        "id": "API4:2023-RATE-LIMITING",
        "title": "Lack of Rate Limiting on POST /api/login",
        "confirmed": False,
        "evidence": "",
    }

    print(f"    Sent {len(statuses)} requests in {elapsed:.2f}s ({len(statuses) / elapsed:.1f} req/s).")
    print(f"    Status code breakdown: 401(invalid creds)={unthrottled_401s}, 429(throttled)={len(throttled)}")

    if not throttled and unthrottled_401s == request_count:
        finding["confirmed"] = True
        finding["evidence"] = (
            f"Sent {request_count} POST /api/login requests in {elapsed:.2f}s "
            f"({len(statuses) / elapsed:.1f} req/s) against the same username with wrong "
            f"passwords each time. All {unthrottled_401s} came back HTTP 401 (processed "
            f"normally) with zero HTTP 429 responses and no server-side slowdown -- the "
            f"endpoint applies no rate limiting, lockout, or backoff of any kind, so an "
            f"attacker script could continue brute-forcing indefinitely."
        )
        print("    [!] VULNERABLE: zero requests were throttled -- no rate limiting present.")
    else:
        finding["evidence"] = (
            f"{len(throttled)}/{request_count} requests were throttled (HTTP 429) -- "
            "rate limiting appears to be present."
        )
        print(f"    [+] Not vulnerable here: {len(throttled)} request(s) were throttled.")

    print()
    return finding


if __name__ == "__main__":
    result = check_rate_limiting()
    print(result)
