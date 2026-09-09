"""
techstack_fingerprint.py

Matches the (mock) web-facing tech-stack fingerprint against a small
known-vulnerable-version table -- the same style as a dependency/CVE auditor,
just applied to externally observable web technology instead of a project's
installed packages.

Real-world equivalent: Wappalyzer/BuiltWith to fingerprint a site's stack,
cross-referenced against CVE databases (NVD) or vendor security advisories.

AUTHORIZED USE ONLY: operates purely on local, simulated data
(mock_data_sources.py) -- no real fingerprinting requests are made.
"""

from __future__ import annotations

from mock_data_sources import get_techstack_fingerprint

# A deliberately small, hand-curated table of technologies with a version
# ceiling below which known public CVEs apply. Real tooling would pull this
# from a live CVE/NVD feed instead of a hardcoded dict.
KNOWN_VULNERABLE_VERSIONS = {
    "jQuery": {
        "vulnerable_below": "3.5.0",
        "cve": "CVE-2020-11022 / CVE-2020-11023",
        "description": "Pre-3.5.0 jQuery is vulnerable to XSS via .html()/.append() "
        "when passing untrusted HTML containing <option> or style attributes.",
    },
    "WordPress": {
        "vulnerable_below": "6.4.0",
        "cve": "Various core/plugin CVEs",
        "description": "Older WordPress core releases have multiple publicly "
        "documented vulnerabilities; keeping core up to date is a baseline control.",
    },
    "PHP": {
        "vulnerable_below": "8.1.0",
        "cve": "Multiple EOL-branch CVEs",
        "description": "PHP branches before 8.1 are past or nearing end-of-life "
        "and no longer receive security patches.",
    },
}


def _version_tuple(version: str) -> tuple[int, ...]:
    """Parse a dotted version string ('1.12.4') into a comparable tuple."""
    parts = []
    for chunk in version.split("."):
        digits = "".join(ch for ch in chunk if ch.isdigit())
        parts.append(int(digits) if digits else 0)
    return tuple(parts)


def fingerprint_stack(domain: str) -> list[dict]:
    """Return the (mock) tech-stack fingerprint for the domain."""
    return get_techstack_fingerprint(domain)


def flag_vulnerable_components(fingerprint: list[dict]) -> list[dict]:
    """Compare each fingerprinted technology's version against the
    known-vulnerable-version table and flag anything below the safe floor.
    """
    findings = []

    for component in fingerprint:
        tech = component["technology"]
        version = component["version"]
        rule = KNOWN_VULNERABLE_VERSIONS.get(tech)

        if rule is None or version is None:
            continue

        if _version_tuple(version) < _version_tuple(rule["vulnerable_below"]):
            findings.append(
                {
                    "technology": tech,
                    "installed_version": version,
                    "safe_at_or_above": rule["vulnerable_below"],
                    "cve": rule["cve"],
                    "description": rule["description"],
                }
            )

    return findings


def run(domain: str) -> dict:
    """Run the full tech-stack fingerprint step and return a summary dict."""
    fingerprint = fingerprint_stack(domain)
    findings = flag_vulnerable_components(fingerprint)

    return {
        "domain": domain,
        "fingerprint": fingerprint,
        "vulnerable_components": findings,
    }


if __name__ == "__main__":
    from mock_data_sources import TARGET_DOMAIN

    result = run(TARGET_DOMAIN)

    print(f"=== Tech-Stack Fingerprint: {TARGET_DOMAIN} ===")
    print("\n[+] Detected technologies:")
    for component in result["fingerprint"]:
        version = component["version"] or "<not disclosed>"
        print(f"    {component['technology']:<12} v{version:<10} ({component['category']})")

    print("\n[+] Vulnerable component check:")
    if result["vulnerable_components"]:
        for finding in result["vulnerable_components"]:
            print(
                f"    [RISK] {finding['technology']} {finding['installed_version']} "
                f"is below the safe floor of {finding['safe_at_or_above']} -- {finding['cve']}"
            )
            print(f"           {finding['description']}")
    else:
        print("    No known-vulnerable versions detected.")
