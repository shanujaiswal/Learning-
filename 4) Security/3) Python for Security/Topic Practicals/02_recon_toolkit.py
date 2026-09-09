"""
02_recon_toolkit.py

AUTHORIZED USE ONLY. Only run this script against a domain/host that YOU own or that you have
explicit, documented, written authorization to test. Sending requests to third-party websites
without permission can violate laws (e.g. the CFAA) and terms of service, even for "harmless"
reconnaissance like this. Replace TARGET_DOMAIN below before running.

Integrates Theory Ch.3 (Requests for Recon/Web Testing) and Ch.1 (DNS resolution basics) into one
coherent recon tool:

  1. Resolves the target domain to its IP address(es).
  2. Fetches the HTTP response headers and flags commonly-missing security headers.
  3. Probes a small set of common well-known/informational paths and reports their status.

This performs ONLY read-only, unauthenticated GET requests — no scanning, no exploitation, no
credential attempts.
"""

import socket
from urllib.parse import urljoin

import requests

# ---------------------------------------------------------------------------
# EDIT ME: replace with a domain you own / are authorized to test.
# example.com is used as a harmless, well-known placeholder that is generally
# safe for passive read-only demos, but you should still swap it for your own
# domain before treating this as a real assessment.
# ---------------------------------------------------------------------------
TARGET_DOMAIN = "example.com"

TIMEOUT_SECONDS = 8

# Headers that a reasonably-hardened site should generally be sending.
SECURITY_HEADERS_TO_CHECK = [
    "Strict-Transport-Security",
    "X-Frame-Options",
    "X-Content-Type-Options",
    "Content-Security-Policy",
    "Referrer-Policy",
    "Permissions-Policy",
]

# Common, non-intrusive informational paths worth checking. All of these are
# static-file lookups (GET) — nothing here attempts auth, injection, or fuzzing.
COMMON_PATHS = [
    "/robots.txt",
    "/.well-known/security.txt",
    "/sitemap.xml",
    "/favicon.ico",
]


def resolve_domain(domain: str) -> list[str]:
    """Resolve a domain to its IPv4/IPv6 addresses using standard-library socket (Ch.1)."""
    try:
        infos = socket.getaddrinfo(domain, None)
        addresses = sorted({info[4][0] for info in infos})
        return addresses
    except socket.gaierror as exc:
        print(f"[!] DNS resolution failed for {domain}: {exc}")
        return []


def check_security_headers(base_url: str) -> None:
    """Fetch the base URL and report on presence/absence of key security headers."""
    try:
        response = requests.get(base_url, timeout=TIMEOUT_SECONDS)
    except requests.RequestException as exc:
        print(f"[!] Could not fetch {base_url}: {exc}")
        return

    print(f"\n[+] GET {base_url} -> HTTP {response.status_code}")
    print("    Server header:", response.headers.get("Server", "<not disclosed>"))

    print("    Security header check:")
    for header in SECURITY_HEADERS_TO_CHECK:
        value = response.headers.get(header)
        if value:
            print(f"      [OK]      {header}: {value}")
        else:
            print(f"      [MISSING] {header}")


def check_common_paths(base_url: str) -> None:
    """GET a handful of common, non-sensitive well-known paths and report their status."""
    print("\n[+] Checking common paths:")
    for path in COMMON_PATHS:
        url = urljoin(base_url, path)
        try:
            response = requests.get(url, timeout=TIMEOUT_SECONDS, allow_redirects=True)
            print(f"    {path:<32} -> HTTP {response.status_code}")
        except requests.RequestException as exc:
            print(f"    {path:<32} -> request failed: {exc}")


def main() -> None:
    print(f"=== Recon: {TARGET_DOMAIN} (authorized targets only) ===")

    print("\n[+] DNS resolution:")
    addresses = resolve_domain(TARGET_DOMAIN)
    if addresses:
        for address in addresses:
            print(f"    {TARGET_DOMAIN} -> {address}")
    else:
        print("    No addresses resolved; continuing with HTTP checks anyway may fail.")

    base_url = f"https://{TARGET_DOMAIN}"
    check_security_headers(base_url)
    check_common_paths(base_url)

    print(
        "\nDone. Remember: this is passive, read-only recon of a domain you must be authorized to test."
    )


if __name__ == "__main__":
    main()
