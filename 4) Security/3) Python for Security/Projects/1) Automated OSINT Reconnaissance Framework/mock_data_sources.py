"""
mock_data_sources.py

Fully offline, simulated "public source" datasets for ONE fictional target
organization, "Nimbus Retail Group" (nimbusretail.example).

Nothing in this file makes a network call. Every function below is a stand-in
for a real OSINT source you'd normally query over the internet:

    - WHOIS registration lookup   -> normally `whois nimbusretail.example` or python-whois
    - DNS records dump            -> normally dnspython / `dig ANY`
    - Employee directory scrape   -> normally LinkedIn/company "About Us" scraping
    - Breach-corpus lookup        -> normally Have I Been Pwned's API
    - Tech-stack fingerprint      -> normally Wappalyzer / BuiltWith

The data is intentionally fixed (no randomness, no seed needed) so that every
run of this framework produces identical, reproducible output. This makes the
framework safe to demo, test, and grade without touching any real
organization, real person, or real API.

AUTHORIZED USE ONLY (in spirit): even though this is 100% fake data, treat the
pattern the same way you would treat real OSINT tooling -- only ever point the
real equivalent of these functions at a target you own or are authorized to
assess.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date


TARGET_DOMAIN = "nimbusretail.example"
TARGET_ORG_NAME = "Nimbus Retail Group"


# ---------------------------------------------------------------------------
# 1) Mock WHOIS record
# ---------------------------------------------------------------------------

def get_whois_record(domain: str) -> dict:
    """Return a simulated WHOIS registration record for the target domain.

    Real-world equivalent: `whois nimbusretail.example` or the python-whois /
    ipwhois libraries querying a registrar's WHOIS server.
    """
    if domain != TARGET_DOMAIN:
        return {}

    return {
        "domain_name": TARGET_DOMAIN,
        "registrar": "Example Registrar, LLC",
        "org": TARGET_ORG_NAME,
        "creation_date": date(2011, 3, 14),
        "expiration_date": date(2027, 3, 14),
        "name_servers": [
            "ns1.nimbusretail.example",
            "ns2.nimbusretail.example",
        ],
        "registrant_email": "domains@nimbusretail.example",
        "dnssec": "unsigned",
    }


# ---------------------------------------------------------------------------
# 2) Mock DNS records dump
# ---------------------------------------------------------------------------

def get_dns_records(domain: str) -> list[dict]:
    """Return a simulated DNS zone dump (as if from a DNS enumeration tool).

    Real-world equivalent: theHarvester / Amass / crt.sh certificate
    transparency search, or plain `dig ANY nimbusretail.example`.

    Deliberately includes one exposed "admin." subdomain -- a classic real
    risk signal (an internal admin panel that should never have public DNS,
    let alone be internet-reachable).
    """
    if domain != TARGET_DOMAIN:
        return []

    return [
        {"name": domain, "type": "A", "value": "203.0.113.10"},
        {"name": domain, "type": "MX", "value": "mail.nimbusretail.example"},
        {"name": f"www.{domain}", "type": "CNAME", "value": domain},
        {"name": f"mail.{domain}", "type": "A", "value": "203.0.113.11"},
        {"name": f"api.{domain}", "type": "A", "value": "203.0.113.12"},
        {"name": f"vpn.{domain}", "type": "A", "value": "203.0.113.13"},
        # Risk signal: internal admin panel exposed in public DNS.
        {"name": f"admin.{domain}", "type": "A", "value": "203.0.113.66"},
        {"name": f"staging.{domain}", "type": "A", "value": "203.0.113.14"},
    ]


# ---------------------------------------------------------------------------
# 3) Mock employee directory (LinkedIn/"About Us"-style scrape)
# ---------------------------------------------------------------------------

@dataclass(frozen=True)
class Employee:
    name: str
    title: str
    email: str


def get_employee_directory() -> list[Employee]:
    """Return a simulated employee directory scrape.

    Real-world equivalent: scraping a company's public "About Us"/"Team" page
    or LinkedIn "people at <company>" search results with BeautifulSoup, then
    deriving likely corporate email addresses from a known naming pattern
    (e.g. first.last@domain).
    """
    return [
        Employee("Alice Chen", "Chief Financial Officer", "alice.chen@nimbusretail.example"),
        Employee("Bilal Rahman", "IT Systems Administrator", "bilal.rahman@nimbusretail.example"),
        Employee("Carla Gomez", "Head of Marketing", "carla.gomez@nimbusretail.example"),
        Employee("David Okafor", "Senior Backend Engineer", "david.okafor@nimbusretail.example"),
        Employee("Elena Petrova", "HR Director", "elena.petrova@nimbusretail.example"),
        Employee("Farhan Ali", "DevOps Engineer", "farhan.ali@nimbusretail.example"),
    ]


# ---------------------------------------------------------------------------
# 4) Mock breach-corpus lookup table (Have I Been Pwned-style)
# ---------------------------------------------------------------------------

def get_breach_corpus() -> dict:
    """Return a simulated credential-exposure/breach-database lookup table.

    Real-world equivalent: the Have I Been Pwned "breached account" API, or a
    paid breach-intel feed. Keyed by email address; value describes which
    breach(es) the credential pair appeared in, plus whether a plaintext-ish
    password fragment was recovered alongside it.

    Only ONE employee below appears in the mock corpus -- intentionally
    representing a realistic low base-rate rather than "everyone is pwned".
    """
    return {
        "bilal.rahman@nimbusretail.example": {
            "breaches": ["CollectionLeak-2019", "MegaRetailerBreach-2021"],
            "exposed_password_hint": "Summer2019!",
            "first_seen": date(2019, 8, 2),
        },
        # Unrelated addresses included to show the corpus isn't org-specific.
        "jdoe@othercorp.example": {
            "breaches": ["CollectionLeak-2019"],
            "exposed_password_hint": "qwerty123",
            "first_seen": date(2019, 8, 2),
        },
        "test.user@example.com": {
            "breaches": ["OldForumDump-2016"],
            "exposed_password_hint": "letmein",
            "first_seen": date(2016, 5, 20),
        },
    }


# ---------------------------------------------------------------------------
# 5) Mock tech-stack fingerprint (Wappalyzer/BuiltWith-style)
# ---------------------------------------------------------------------------

def get_techstack_fingerprint(domain: str) -> list[dict]:
    """Return a simulated web-facing technology fingerprint for the domain.

    Real-world equivalent: Wappalyzer, BuiltWith, or manually fingerprinting
    via HTTP response headers / JS globals / meta generator tags.

    Includes one deliberately outdated, vulnerable component (an old jQuery
    build with known public CVEs) to serve as the third risk signal.
    """
    if domain != TARGET_DOMAIN:
        return []

    return [
        {"technology": "nginx", "version": "1.25.3", "category": "web-server"},
        {"technology": "PHP", "version": "8.2.10", "category": "language"},
        {"technology": "WordPress", "version": "6.5.2", "category": "cms"},
        # Risk signal: old jQuery with publicly known XSS CVEs.
        {"technology": "jQuery", "version": "1.12.4", "category": "javascript-library"},
        {"technology": "Cloudflare", "version": None, "category": "cdn"},
    ]
