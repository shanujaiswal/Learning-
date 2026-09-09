"""
key_hygiene_auditor.py

Scans the fleet-wide authorized_keys inventory (fleet_inventory.generate_authorized_keys_inventory)
for two real-world key-hygiene red flags:

1. Duplicate keys — the exact same public key material installed on more than one
   host/account combination. In practice this means one leaked/stolen private key
   (a laptop, a CI runner) compromises every account it was ever copied onto, and
   there is no way to revoke access to just one of them without regenerating and
   redistributing a new key everywhere.

2. Unlabeled keys — an authorized_keys entry with no identifying comment field, so
   nobody can tell who the key belongs to, whether the owner still needs access, or
   whether it's safe to remove during an offboarding/cleanup pass.

This mirrors the kind of pass a tool like `ssh-audit` or a manual key-inventory
review performs across a fleet, just scoped to these two checks.
"""

from collections import defaultdict
from dataclasses import dataclass


@dataclass
class DuplicateKeyFinding:
    key_material: str
    key_type: str
    locations: list[tuple]  # list of (host, user)

    @property
    def fingerprint_preview(self) -> str:
        return f"{self.key_type} {self.key_material[:12]}...{self.key_material[-6:]}"


@dataclass
class UnlabeledKeyFinding:
    host: str
    user: str
    key_type: str
    key_material: str

    @property
    def fingerprint_preview(self) -> str:
        return f"{self.key_type} {self.key_material[:12]}...{self.key_material[-6:]}"


def find_duplicate_keys(inventory: list[dict]) -> list[DuplicateKeyFinding]:
    """Group inventory entries by exact key material; flag any group used in >1 place."""
    by_key = defaultdict(list)
    key_type_of = {}

    for entry in inventory:
        by_key[entry["key_material"]].append((entry["host"], entry["user"]))
        key_type_of[entry["key_material"]] = entry["key_type"]

    findings = []
    for key_material, locations in by_key.items():
        if len(locations) > 1:
            findings.append(DuplicateKeyFinding(
                key_material=key_material,
                key_type=key_type_of[key_material],
                locations=sorted(locations),
            ))

    # Worst (most widely reused) first.
    findings.sort(key=lambda f: len(f.locations), reverse=True)
    return findings


def find_unlabeled_keys(inventory: list[dict]) -> list[UnlabeledKeyFinding]:
    """Flag any entry whose comment field is empty/whitespace — no owner identification."""
    findings = []
    for entry in inventory:
        comment = (entry.get("comment") or "").strip()
        if not comment:
            findings.append(UnlabeledKeyFinding(
                host=entry["host"],
                user=entry["user"],
                key_type=entry["key_type"],
                key_material=entry["key_material"],
            ))
    findings.sort(key=lambda f: (f.host, f.user))
    return findings


def audit_key_hygiene(inventory: list[dict]) -> dict:
    """Run both key-hygiene checks and return a summary dict."""
    return {
        "total_keys": len(inventory),
        "duplicates": find_duplicate_keys(inventory),
        "unlabeled": find_unlabeled_keys(inventory),
    }


if __name__ == "__main__":
    from fleet_inventory import generate_authorized_keys_inventory, generate_fleet

    fleet = generate_fleet()
    inventory = generate_authorized_keys_inventory(fleet)
    report = audit_key_hygiene(inventory)

    print(f"Scanned {report['total_keys']} authorized_keys entries across the fleet.\n")

    print(f"Duplicate keys found: {len(report['duplicates'])}")
    for finding in report["duplicates"]:
        print(f"  {finding.fingerprint_preview} reused on {len(finding.locations)} account(s):")
        for host, user in finding.locations:
            print(f"    - {user}@{host}")

    print(f"\nUnlabeled keys found: {len(report['unlabeled'])}")
    for finding in report["unlabeled"]:
        print(f"  {finding.user}@{finding.host} — {finding.fingerprint_preview} (no comment)")
