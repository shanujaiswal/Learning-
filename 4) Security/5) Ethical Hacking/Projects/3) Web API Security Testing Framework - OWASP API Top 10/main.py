"""
main.py -- Web API Security Testing Framework Orchestrator (OWASP API Top 10)
================================================================================

LEGAL / ETHICAL SCOPE
----------------------
Starts and tests ONLY a local Flask app on 127.0.0.1:5000 (vulnerable_api.py)
that this project ships. Do not repurpose any of these scripts against a
system you do not own or are not explicitly authorized to test.

WHAT THIS DOES
----------------
1. Starts vulnerable_api.py in a background thread and waits for /api/health
   to respond.
2. Runs all four tester scripts against it in turn:
     - bola_tester.py            (API1:2023 - BOLA/IDOR)
     - function_auth_tester.py   (API5:2023 - BFLA)
     - data_exposure_tester.py   (API3:2023 - Excessive Data Exposure)
     - rate_limit_tester.py      (API4:2023 - Lack of Rate Limiting)
3. Prints each confirmed finding.
4. Generates api_security_report.md via report.py from the real, live
   findings collected above.

RUN
----
    python main.py
"""

from __future__ import annotations

import sys
import threading
import time

import requests

import vulnerable_api
from bola_tester import check_bola
from data_exposure_tester import check_data_exposure
from function_auth_tester import check_bfla
from rate_limit_tester import check_rate_limiting
from report import generate_report

TARGET = "http://127.0.0.1:5000"


def _start_server() -> threading.Thread:
    thread = threading.Thread(
        target=lambda: vulnerable_api.app.run(host="127.0.0.1", port=5000, debug=False, use_reloader=False),
        daemon=True,
    )
    thread.start()
    return thread


def _wait_for_health(timeout: float = 10.0) -> bool:
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            resp = requests.get(f"{TARGET}/api/health", timeout=0.5)
            if resp.status_code == 200:
                return True
        except requests.exceptions.RequestException:
            pass
        time.sleep(0.2)
    return False


def main() -> int:
    print("=" * 78)
    print("Web API Security Testing Framework -- OWASP API Security Top 10")
    print("=" * 78)
    print()

    print("[*] Starting vulnerable_api.py in a background thread...")
    _start_server()

    if not _wait_for_health():
        print("[!] API did not come up in time. Aborting.")
        return 1
    print(f"[*] API is up at {TARGET}\n")

    findings = []

    print("-" * 78)
    findings.append(check_bola())

    print("-" * 78)
    findings.append(check_bfla())

    print("-" * 78)
    findings.append(check_data_exposure())

    print("-" * 78)
    findings.append(check_rate_limiting())

    print("=" * 78)
    print("SUMMARY OF CONFIRMED FINDINGS")
    print("=" * 78)
    confirmed = [f for f in findings if f["confirmed"]]
    for f in confirmed:
        print(f"  [CONFIRMED] {f['id']}: {f['title']}")
    if not confirmed:
        print("  No findings were confirmed in this run.")
    print()
    print(f"[*] {len(confirmed)}/{len(findings)} OWASP API Top 10 issues confirmed against this lab target.")
    print()

    report_path = generate_report(findings, target=TARGET)
    print(f"[*] Report written to: {report_path}")

    return 0 if len(confirmed) == len(findings) else 2


if __name__ == "__main__":
    sys.exit(main())
