"""
whois_and_dns_recon.py

Queries the mock WHOIS + DNS "public sources" for the target domain and flags
any sensitive-looking exposed subdomains.

Real-world equivalent: theHarvester / Amass for subdomain enumeration via
certificate transparency logs and search engines, plus a plain WHOIS lookup.

AUTHORIZED USE ONLY: this module operates purely on local, simulated data
(mock_data_sources.py) -- no real network requests are made. Point the real
equivalent tools only at domains you own or are authorized to test.
"""

from __future__ import annotations

from mock_data_sources import get_dns_records, get_whois_record

# Subdomain name fragments that commonly indicate a sensitive, internal-only
# service that should not be reachable/discoverable from the public internet.
SENSITIVE_SUBDOMAIN_KEYWORDS = [
    "admin",
    "staging",
    "dev",
    "test",
    "internal",
    "vpn",
    "backup",
    "db",
    "phpmyadmin",
]


def run_whois_lookup(domain: str) -> dict:
    """Fetch (mock) WHOIS data and return it as-is for reporting."""
    record = get_whois_record(domain)
    return record


def run_dns_enumeration(domain: str) -> list[dict]:
    """Fetch (mock) DNS records for the domain."""
    return get_dns_records(domain)


def flag_exposed_subdomains(dns_records: list[dict], domain: str) -> list[dict]:
    """Inspect DNS records and flag any subdomain whose name contains a
    sensitive keyword (admin, staging, vpn, etc.) -- these are prime targets
    for an attacker's initial foothold and should generally not be exposed in
    public DNS/reachable without additional access controls.
    """
    flagged = []
    for record in dns_records:
        name = record["name"]
        if name == domain:
            continue  # apex record, not a subdomain
        label = name[: -(len(domain) + 1)] if name.endswith(domain) else name
        for keyword in SENSITIVE_SUBDOMAIN_KEYWORDS:
            if keyword in label.lower():
                flagged.append(
                    {
                        "subdomain": name,
                        "record_type": record["type"],
                        "value": record["value"],
                        "matched_keyword": keyword,
                    }
                )
                break
    return flagged


def run(domain: str) -> dict:
    """Run the full WHOIS + DNS recon step and return a summary dict."""
    whois_record = run_whois_lookup(domain)
    dns_records = run_dns_enumeration(domain)
    exposed = flag_exposed_subdomains(dns_records, domain)

    return {
        "domain": domain,
        "whois": whois_record,
        "dns_records": dns_records,
        "exposed_subdomains": exposed,
    }


if __name__ == "__main__":
    from mock_data_sources import TARGET_DOMAIN

    result = run(TARGET_DOMAIN)

    print(f"=== WHOIS + DNS Recon: {TARGET_DOMAIN} ===")
    print("\n[+] WHOIS record:")
    for key, value in result["whois"].items():
        print(f"    {key}: {value}")

    print("\n[+] DNS records:")
    for record in result["dns_records"]:
        print(f"    {record['name']:<30} {record['type']:<6} {record['value']}")

    print("\n[+] Exposed sensitive subdomains:")
    if result["exposed_subdomains"]:
        for item in result["exposed_subdomains"]:
            print(
                f"    [RISK] {item['subdomain']} ({item['record_type']} -> {item['value']}) "
                f"matched keyword '{item['matched_keyword']}'"
            )
    else:
        print("    None found.")
