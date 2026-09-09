"""
program_scope.py

The published scope definition for a (fictional) bug bounty program:
"AcmeCorp Public Bug Bounty".

In real programs (HackerOne, Bugcrowd, Intigriti...) this information lives
on the program's policy page, not in code -- but every serious automation
pipeline encodes it as data FIRST, because every later step (enumeration,
filtering, probing, reporting) must be checked against it.

Reading and respecting this file is not a formality: testing an
out-of-scope asset is a policy violation even when it isn't a crime, and
programs will ban researchers and refuse payment for it -- see Theory
note 16, "Reading and Respecting Scope and Rules of Engagement".
"""

from __future__ import annotations

from dataclasses import dataclass, field
import fnmatch


PROGRAM_NAME = "AcmeCorp Public Bug Bounty"

# Wildcard patterns describing what IS allowed to be tested.
# fnmatch-style globs, matched against the full hostname.
IN_SCOPE_PATTERNS: list[str] = [
    "acmecorp.com",
    "*.acmecorp.com",
]

# Exact hosts or patterns that are EXPLICITLY carved out of the wildcard
# above and must NEVER be tested, even though they match an in-scope
# pattern. This is the realistic case the theory note calls out: a
# broad "*.acmecorp.com" scope does not include everything under that
# wildcard once the program publishes exclusions.
OUT_OF_SCOPE_PATTERNS: list[str] = [
    "blog.acmecorp.com",       # third-party-hosted marketing CMS (Ghost, out of AcmeCorp's control)
    "status.acmecorp.com",     # third-party status-page vendor (Statuspage.io), explicitly excluded
    "partner-hr.acmecorp.com", # acquired subsidiary HR portal, run by a different company entirely
    "*.internal.acmecorp.com", # internal-only staging network, program forbids any testing here
]

# Prohibited techniques per the program's rules of engagement (not
# enforced mechanically by this toolkit, but documented here because a
# real scope file always lists them -- see README for how this toolkit
# stays inside these rules by design, e.g. no active DNS brute forcing).
PROHIBITED_TECHNIQUES: list[str] = [
    "Automated vulnerability scanners (Nessus, Burp Pro active scan) against production",
    "Any DoS / load testing",
    "Active DNS brute forcing against out-of-scope subdomains",
    "Social engineering against AcmeCorp employees",
]


@dataclass(frozen=True)
class ScopeDecision:
    host: str
    in_scope: bool
    reason: str


def _matches_any(host: str, patterns: list[str]) -> str | None:
    """Return the pattern that matched `host`, or None."""
    host = host.lower().strip()
    for pattern in patterns:
        if fnmatch.fnmatch(host, pattern.lower()):
            return pattern
    return None


def evaluate(host: str) -> ScopeDecision:
    """
    Apply the program's scope rules to a single host, exclusions taking
    priority over the broad wildcard -- exactly how a careful human
    hunter should reason about it, just automated and made repeatable.
    """
    excluded_by = _matches_any(host, OUT_OF_SCOPE_PATTERNS)
    if excluded_by:
        return ScopeDecision(
            host=host,
            in_scope=False,
            reason=f"matches OUT-OF-SCOPE exclusion pattern '{excluded_by}'",
        )

    included_by = _matches_any(host, IN_SCOPE_PATTERNS)
    if included_by:
        return ScopeDecision(
            host=host,
            in_scope=True,
            reason=f"matches IN-SCOPE pattern '{included_by}'",
        )

    return ScopeDecision(
        host=host,
        in_scope=False,
        reason="does not match any published in-scope pattern",
    )
