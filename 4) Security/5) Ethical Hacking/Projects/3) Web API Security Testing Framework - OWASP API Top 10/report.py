"""
report.py -- API Security Report Generator
=============================================

Compiles the 4 structured findings produced by bola_tester.py,
function_auth_tester.py, data_exposure_tester.py and rate_limit_tester.py
into a single scored `api_security_report.md`, styled like a real API
pentest/bug-bounty report: severity per OWASP API Security Top 10 (2023)
category, evidence, and remediation.

Not meant to be run standalone in the usual case -- main.py calls
generate_report() with the live findings it collected. Running this file
directly regenerates the report using canned example findings (all
confirmed), useful for previewing the report format without starting the
API.
"""

from __future__ import annotations

import os
from datetime import datetime

# id -> (OWASP category label, severity, remediation)
CATALOG = {
    "API1:2023-BOLA": (
        "API1:2023 - Broken Object Level Authorization",
        "Critical",
        "Every object-fetching endpoint must independently verify server-side that "
        "the authenticated caller's id equals the requested resource's owner id (or "
        "that the caller holds an explicit admin/shared-access grant) before "
        "returning data -- e.g. `if caller['id'] != user_id and caller['role'] != "
        "'admin': return 403`. Never rely on IDs being 'hard to guess'.",
    ),
    "API5:2023-BFLA": (
        "API5:2023 - Broken Function Level Authorization",
        "Critical",
        "Enforce role/permission checks server-side on every privileged endpoint "
        "(e.g. `if caller['role'] != 'admin': return 403`), independent of whatever "
        "the client UI shows or hides. A hidden button is not an access control.",
    ),
    "API3:2023-EXCESSIVE-DATA-EXPOSURE": (
        "API3:2023 - Broken Object Property Level Authorization (Excessive Data Exposure)",
        "High",
        "Define an explicit allow-list output schema/DTO per endpoint (e.g. a "
        "dataclass, pydantic model, or marshmallow schema) and serialize only the "
        "fields the client legitimately needs. Never return the raw internal "
        "record and trust the client to ignore extra fields such as password "
        "hashes, internal notes, or PII.",
    ),
    "API4:2023-RATE-LIMITING": (
        "API4:2023 - Unrestricted Resource Consumption (Lack of Rate Limiting)",
        "High",
        "Apply per-account and per-IP rate limiting (e.g. Flask-Limiter, or an API "
        "gateway/WAF rule) such as '5 attempts per minute' on authentication and "
        "OTP-style endpoints. Return HTTP 429 with a Retry-After header once "
        "exceeded, and add exponential backoff / temporary lockout after repeated "
        "failures.",
    ),
}

REPORT_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "api_security_report.md")


def _score(findings: list[dict]) -> tuple[int, int]:
    """Very small scoring model: confirmed Critical=25pts, High=15pts off a
    100-point baseline, floored at 0. Purely illustrative, not a real CVSS
    calculation."""
    score = 100
    for f in findings:
        if not f["confirmed"]:
            continue
        _, severity, _ = CATALOG[f["id"]]
        score -= 25 if severity == "Critical" else 15
    return max(score, 0), 100


def generate_report(findings: list[dict], target: str = "http://127.0.0.1:5000") -> str:
    confirmed = [f for f in findings if f["confirmed"]]
    score, max_score = _score(findings)

    lines = []
    lines.append("# API Security Report -- OWASP API Security Top 10 Assessment")
    lines.append("")
    lines.append(f"- **Target:** `{target}` (local lab: vulnerable_api.py)")
    lines.append(f"- **Generated:** {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    lines.append(f"- **Findings confirmed:** {len(confirmed)} / {len(findings)}")
    lines.append(f"- **Security score:** {score} / {max_score}  " + ("(needs immediate remediation)" if score < 60 else ""))
    lines.append("")
    lines.append("## Summary")
    lines.append("")
    lines.append(
        "This assessment exercised a local JSON REST API (`vulnerable_api.py`) "
        "against four OWASP API Security Top 10 (2023) issue classes using "
        "automated, evidence-producing test scripts. Each finding below was "
        "independently confirmed by making the actual HTTP request an attacker "
        "would make and inspecting the real response -- not inferred from source "
        "code alone."
    )
    lines.append("")
    lines.append("| # | OWASP Category | Severity | Status |")
    lines.append("|---|----------------|----------|--------|")
    for i, f in enumerate(findings, start=1):
        category, severity, _ = CATALOG[f["id"]]
        status = "CONFIRMED VULNERABLE" if f["confirmed"] else "Not confirmed"
        lines.append(f"| {i} | {category} | {severity} | {status} |")
    lines.append("")
    lines.append("## Detailed Findings")
    lines.append("")

    for i, f in enumerate(findings, start=1):
        category, severity, remediation = CATALOG[f["id"]]
        lines.append(f"### {i}. {category}")
        lines.append("")
        lines.append(f"- **Endpoint / Title:** {f['title']}")
        lines.append(f"- **Severity:** {severity}")
        lines.append(f"- **Status:** {'CONFIRMED VULNERABLE' if f['confirmed'] else 'Not confirmed in this run'}")
        lines.append(f"- **Evidence:** {f['evidence']}")
        lines.append(f"- **Remediation:** {remediation}")
        lines.append("")

    lines.append("## Methodology")
    lines.append("")
    lines.append(
        "- `bola_tester.py` -- authenticated as user A, requested user B's object "
        "by id substitution using user A's own valid token.\n"
        "- `function_auth_tester.py` -- authenticated as a non-admin user, called "
        "the admin-only endpoint directly with that user's token.\n"
        "- `data_exposure_tester.py` -- made a normal, legitimate client request "
        "and inspected the raw JSON body for sensitive field names never "
        "rendered by the UI.\n"
        "- `rate_limit_tester.py` -- sent a bounded burst of rapid login attempts "
        "and checked whether any were throttled (HTTP 429)."
    )
    lines.append("")
    lines.append(
        "*This report was generated automatically by `report.py` against a "
        "deliberately vulnerable local lab target. It is a training artifact, "
        "not a real security assessment of any production system.*"
    )

    content = "\n".join(lines) + "\n"
    with open(REPORT_PATH, "w", encoding="utf-8") as fh:
        fh.write(content)

    return REPORT_PATH


if __name__ == "__main__":
    # Preview using canned example findings (all confirmed) -- lets you see
    # the report format without starting the API. main.py uses real findings.
    example_findings = [
        {
            "id": "API1:2023-BOLA",
            "title": "Broken Object Level Authorization (BOLA/IDOR) on GET /api/users/<id>",
            "confirmed": True,
            "evidence": "Example evidence -- run main.py for a live-verified report.",
        },
        {
            "id": "API5:2023-BFLA",
            "title": "Broken Function Level Authorization on GET /api/admin/users",
            "confirmed": True,
            "evidence": "Example evidence -- run main.py for a live-verified report.",
        },
        {
            "id": "API3:2023-EXCESSIVE-DATA-EXPOSURE",
            "title": "Excessive Data Exposure on GET /api/users/<id>/profile",
            "confirmed": True,
            "evidence": "Example evidence -- run main.py for a live-verified report.",
        },
        {
            "id": "API4:2023-RATE-LIMITING",
            "title": "Lack of Rate Limiting on POST /api/login",
            "confirmed": True,
            "evidence": "Example evidence -- run main.py for a live-verified report.",
        },
    ]
    path = generate_report(example_findings)
    print(f"[*] Preview report written to {path}")
