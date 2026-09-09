"""
cis_ssh_benchmark.py

A small, CIS-Benchmark-style ruleset for `sshd_config` hardening. Each rule is a
self-contained function that takes a host record (as produced by
`fleet_inventory.generate_fleet`) and returns a `RuleResult`.

Loosely modeled on real guidance from the CIS Distribution Independent Linux
Benchmark ("Ensure SSH root login is disabled", "Ensure SSH PasswordAuthentication
is disabled", etc.) — trimmed down to the checks this project cares about, not a
full reproduction of the real CIS document.
"""

from dataclasses import dataclass, field

# Cipher suites considered acceptable under modern hardening guidance
# (AEAD ciphers only — no CBC-mode, no arcfour/RC4, no legacy 3DES/Blowfish/CAST).
APPROVED_CIPHERS = {
    "chacha20-poly1305@openssh.com",
    "aes256-gcm@openssh.com",
    "aes128-gcm@openssh.com",
}


@dataclass
class RuleResult:
    rule_id: str
    title: str
    severity: str          # "critical" | "high" | "medium"
    passed: bool
    detail: str
    remediation: list[str] = field(default_factory=list)


def rule_root_login_disabled(host: dict) -> RuleResult:
    value = host.get("PermitRootLogin", "yes")
    passed = value == "no"
    if passed:
        detail = "PermitRootLogin is 'no' — root cannot log in over SSH at all."
    else:
        detail = f"PermitRootLogin is '{value}' — root login is not fully disabled."
    return RuleResult(
        rule_id="CIS-5.2.8",
        title="Ensure SSH root login is disabled",
        severity="critical",
        passed=passed,
        detail=detail,
        remediation=["PermitRootLogin no"],
    )


def rule_password_auth_disabled(host: dict) -> RuleResult:
    value = host.get("PasswordAuthentication", "yes")
    passed = value == "no"
    detail = (
        "PasswordAuthentication is 'no' — key-only authentication enforced."
        if passed else
        f"PasswordAuthentication is '{value}' — passwords are accepted alongside/instead of keys."
    )
    return RuleResult(
        rule_id="CIS-5.2.10",
        title="Ensure SSH PasswordAuthentication is disabled",
        severity="critical",
        passed=passed,
        detail=detail,
        remediation=["PasswordAuthentication no"],
    )


def rule_no_weak_ciphers(host: dict) -> RuleResult:
    ciphers = host.get("Ciphers", [])
    weak = [c for c in ciphers if c not in APPROVED_CIPHERS]
    passed = len(weak) == 0 and len(ciphers) > 0
    if passed:
        detail = f"All {len(ciphers)} configured ciphers are on the approved AEAD list."
    elif not ciphers:
        detail = "No Ciphers directive configured — daemon default may include weak ciphers."
    else:
        detail = f"Weak/legacy cipher(s) configured: {', '.join(weak)}."
    return RuleResult(
        rule_id="CIS-5.2.13",
        title="Ensure only strong ciphers are used",
        severity="high",
        passed=passed,
        detail=detail,
        remediation=[f"Ciphers {','.join(sorted(APPROVED_CIPHERS))}"],
    )


def rule_protocol_2_only(host: dict) -> RuleResult:
    protocol = host.get("Protocol", 2)
    passed = protocol == 2
    detail = (
        "Protocol 2 in use." if passed else
        f"Protocol {protocol} configured — SSH-1 is cryptographically broken and must not be used."
    )
    return RuleResult(
        rule_id="CIS-5.2.2",
        title="Ensure SSH Protocol is not set to 1",
        severity="critical",
        passed=passed,
        detail=detail,
        remediation=["Protocol 2"],
    )


def rule_access_restricted(host: dict) -> RuleResult:
    allow_users = host.get("AllowUsers", [])
    allow_groups = host.get("AllowGroups", [])
    has_wildcard = "*" in allow_users or "*" in allow_groups
    has_restriction = (len(allow_users) > 0 or len(allow_groups) > 0) and not has_wildcard
    passed = has_restriction
    if passed:
        source = f"AllowUsers {' '.join(allow_users)}" if allow_users else f"AllowGroups {' '.join(allow_groups)}"
        detail = f"Login access is restricted via {source}."
    elif has_wildcard:
        detail = "AllowUsers/AllowGroups is set to a wildcard ('*') — equivalent to no restriction at all."
    else:
        detail = "No AllowUsers or AllowGroups configured — any account on the host can attempt SSH login."
    return RuleResult(
        rule_id="CIS-5.2.20",
        title="Ensure SSH access is limited via AllowUsers/AllowGroups",
        severity="medium",
        passed=passed,
        detail=detail,
        remediation=["AllowUsers <explicit list of usernames, no wildcards>"],
    )


# Order matters for report readability; severity weighting is applied in config_auditor.py.
BENCHMARK_RULES = [
    rule_root_login_disabled,
    rule_password_auth_disabled,
    rule_no_weak_ciphers,
    rule_protocol_2_only,
    rule_access_restricted,
]

SEVERITY_WEIGHT = {
    "critical": 3,
    "high": 2,
    "medium": 1,
}


def run_benchmark(host: dict) -> list[RuleResult]:
    """Run every benchmark rule against a single host record."""
    return [rule(host) for rule in BENCHMARK_RULES]
