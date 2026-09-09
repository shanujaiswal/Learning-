"""
chain_validator.py
-------------------
Validates a presented X.509 certificate the way a relying party (a browser, a
service mesh sidecar, an mTLS-enabled internal client, etc.) would:

  1. Signature check      -- does the CA's public key actually verify the
                              certificate's signature? (Rejects self-signed
                              impersonation certs that were never touched by
                              the real CA's private key.)
  2. Validity-period check -- is "now" within [notBefore, notAfter]? (Rejects
                              not-yet-valid and expired certificates.)
  3. Hostname/subject match -- does the requested hostname appear in the
                              cert's Subject Alternative Name (SAN)?
  4. Revocation check       -- has the CA revoked this certificate's serial
                              number since issuance? (Checked against
                              revocation_registry.RevocationRegistry.)

Every check is independent and short-circuits with a clear, human-readable
reason on failure -- mirroring "no partial trust" from real TLS validation:
any single failure means the whole chain is rejected.
"""

from __future__ import annotations

import datetime
from dataclasses import dataclass, field

from cryptography import x509
from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives.asymmetric.rsa import RSAPublicKey

from revocation_registry import RevocationRegistry


@dataclass
class ValidationResult:
    ok: bool
    hostname: str
    checks: dict[str, bool] = field(default_factory=dict)
    reasons: list[str] = field(default_factory=list)

    def add(self, name: str, passed: bool, detail: str) -> None:
        self.checks[name] = passed
        if not passed:
            self.reasons.append(detail)

    def summary(self) -> str:
        status = "ACCEPTED" if self.ok else "REJECTED"
        lines = [f"[{status}] certificate for '{self.hostname}'"]
        for name, passed in self.checks.items():
            mark = "PASS" if passed else "FAIL"
            lines.append(f"    - {name}: {mark}")
        if self.reasons:
            lines.append("    reason(s): " + "; ".join(self.reasons))
        return "\n".join(lines)


def _verify_signature(certificate: x509.Certificate, ca_public_key: RSAPublicKey) -> bool:
    """
    Cryptographically verify that `certificate` was signed by the private key
    matching `ca_public_key`. This is the step that catches impersonation:
    an attacker's self-signed cert was signed with THEIR key, not the CA's,
    so this raises InvalidSignature and we return False.
    """
    try:
        ca_public_key.verify(
            certificate.signature,
            certificate.tbs_certificate_bytes,
            padding.PKCS1v15(),
            certificate.signature_hash_algorithm,
        )
        return True
    except InvalidSignature:
        return False
    except Exception:
        # Any other verification-related failure (e.g. algorithm mismatch)
        # is also treated as "does not verify".
        return False


def _check_validity_period(certificate: x509.Certificate) -> tuple[bool, str]:
    now = datetime.datetime.now(datetime.timezone.utc)
    not_before = certificate.not_valid_before_utc
    not_after = certificate.not_valid_after_utc
    if now < not_before:
        return False, f"certificate is not yet valid (notBefore={not_before.isoformat()})"
    if now > not_after:
        return False, f"certificate expired on {not_after.isoformat()} (now={now.isoformat()})"
    return True, ""


def _check_hostname(certificate: x509.Certificate, expected_hostname: str) -> tuple[bool, str]:
    try:
        san_ext = certificate.extensions.get_extension_for_class(x509.SubjectAlternativeName)
        dns_names = san_ext.value.get_values_for_type(x509.DNSName)
    except x509.ExtensionNotFound:
        dns_names = []

    if expected_hostname in dns_names:
        return True, ""
    return False, f"hostname '{expected_hostname}' not found in SAN {dns_names}"


def validate_certificate(
    certificate: x509.Certificate,
    *,
    expected_hostname: str,
    ca_public_key: RSAPublicKey,
    revocation_registry: RevocationRegistry,
) -> ValidationResult:
    """
    Run the full battery of checks against a presented certificate and return
    a ValidationResult. `ok` is True only if every check passes -- there is
    no partial trust, matching real-world chain validation semantics.
    """
    result = ValidationResult(ok=True, hostname=expected_hostname)

    signature_ok = _verify_signature(certificate, ca_public_key)
    result.add(
        "signature verifies against CA public key",
        signature_ok,
        "certificate was NOT signed by the trusted CA (possible impersonation)"
        if not signature_ok else "",
    )

    validity_ok, validity_reason = _check_validity_period(certificate)
    result.add("validity period (not expired / not premature)", validity_ok, validity_reason)

    hostname_ok, hostname_reason = _check_hostname(certificate, expected_hostname)
    result.add("hostname matches Subject Alternative Name", hostname_ok, hostname_reason)

    is_revoked = revocation_registry.is_revoked(certificate.serial_number)
    revocation_ok = not is_revoked
    revocation_detail = ""
    if is_revoked:
        reason = revocation_registry.revocation_reason(certificate.serial_number)
        revocation_detail = f"certificate serial {certificate.serial_number} is REVOKED (reason: {reason})"
    result.add("not present on CRL (not revoked)", revocation_ok, revocation_detail)

    result.ok = all(result.checks.values())
    return result
