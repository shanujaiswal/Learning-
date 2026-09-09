"""
report_writer.py

Turns confirmed Finding objects (from vulnerability_probes.py) into
submission-ready Markdown reports, one file per finding plus one
combined `submission_summary.md`, in the format real programs expect
(Theory note 16's template: Title / Severity / Summary / Steps to
Reproduce / Impact / Proof of Concept / Remediation).

This is the automated equivalent of typing out a HackerOne/Bugcrowd
report by hand -- consistent structure, nothing forgotten, and fast
enough to write immediately after the probe confirms the bug (before
memory of the exact request/response fades).
"""

from __future__ import annotations

import os
import re
from datetime import date

from vulnerability_probes import Finding

OUTPUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "bug_bounty_reports")

_SEVERITY_CVSS = {
    "Critical": "9.0 - 10.0",
    "High": "7.0 - 8.9",
    "Medium": "4.0 - 6.9",
    "Low": "0.1 - 3.9",
    "Info": "N/A",
}


def _slugify(text: str) -> str:
    text = text.lower()
    text = re.sub(r"[^a-z0-9]+", "-", text)
    return text.strip("-")[:80]


def render_report(finding: Finding, index: int) -> str:
    cvss_range = _SEVERITY_CVSS.get(finding.severity, "N/A")
    steps_md = "\n".join(f"{i}. {step}" for i, step in enumerate(finding.steps, start=1))

    return f"""# Bug Bounty Report #{index:02d}

**Title:** {finding.title}

**Severity:** {finding.severity} (approximate CVSS v3.1 base score range: {cvss_range})

**Affected Asset:** `{finding.host}` -- {finding.affected_url}

**Check Type:** `{finding.check}`

**Program:** AcmeCorp Public Bug Bounty

**Date Reported:** {date.today().isoformat()}

---

## Summary

{finding.title}. Confirmed via a low-risk, non-destructive probe against
an in-scope asset, as verified by this program's published scope rules
(see `program_scope.py`).

## Steps to Reproduce

{steps_md}

## Proof of Concept / Evidence

```
{finding.evidence}
```

## Impact

{finding.impact}

## Suggested Fix

{finding.fix}

---
*Generated automatically by the Bug Bounty Recon-to-Report Automation Toolkit.
All steps above were executed only against assets confirmed in-scope by
`scope_filter.py` prior to testing.*
"""


def write_reports(findings: list[Finding]) -> list[str]:
    """Write one Markdown file per finding, return the list of file paths written."""
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    written_paths: list[str] = []

    for i, finding in enumerate(findings, start=1):
        filename = f"{i:02d}-{finding.check}-{_slugify(finding.host)}.md"
        path = os.path.join(OUTPUT_DIR, filename)
        with open(path, "w", encoding="utf-8") as f:
            f.write(render_report(finding, i))
        written_paths.append(path)

    written_paths.append(write_summary(findings))
    return written_paths


def write_summary(findings: list[Finding]) -> str:
    """Write one combined submission_summary.md covering every confirmed finding."""
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    path = os.path.join(OUTPUT_DIR, "submission_summary.md")

    severity_order = {"Critical": 0, "High": 1, "Medium": 2, "Low": 3, "Info": 4}
    ordered = sorted(findings, key=lambda f: severity_order.get(f.severity, 99))

    lines = [
        "# Submission Summary -- AcmeCorp Public Bug Bounty",
        "",
        f"**Date:** {date.today().isoformat()}",
        f"**Total confirmed findings:** {len(findings)}",
        "",
        "| # | Severity | Title | Affected Asset |",
        "|---|----------|-------|-----------------|",
    ]
    for i, f in enumerate(ordered, start=1):
        lines.append(f"| {i:02d} | {f.severity} | {f.title} | `{f.host}` |")

    lines += [
        "",
        "All findings above were produced strictly against hosts that survived "
        "`scope_filter.py`'s evaluation of `program_scope.py`'s published scope rules. "
        "No out-of-scope asset (see the program's exclusion list) was probed at any point "
        "in this run -- enforced both by filtering candidates before probing and by each "
        "probe independently re-checking scope before sending a request.",
        "",
        "Individual per-finding reports are in this same directory, one Markdown file each, "
        "named `<index>-<check-type>-<host>.md`.",
    ]

    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")

    return path
