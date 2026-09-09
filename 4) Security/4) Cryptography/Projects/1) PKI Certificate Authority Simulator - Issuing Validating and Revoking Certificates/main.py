"""
main.py
-------
Runs the full PKI story end-to-end using REAL X.509 certificates:

  1. Stand up an internal root CA (real self-signed X.509 root cert).
  2. Issue two legitimate leaf certificates for internal hostnames.
  3. Validate both -- expect ACCEPTED.
  4. Present a self-signed impersonation cert for one of the hostnames --
     expect REJECTED (signature does not verify against the CA).
  5. Present an already-expired certificate -- expect REJECTED (validity
     period check fails).
  6. Revoke one of the legitimate certs, then show it is now REJECTED via
     the CRL check, while the OTHER legitimate cert still validates fine.

Run with:  python main.py
"""

from __future__ import annotations

from certificate_authority import (
    CertificateAuthority,
    create_self_signed_impersonation_certificate,
)
from chain_validator import validate_certificate
from revocation_registry import RevocationRegistry


def banner(title: str) -> None:
    print()
    print("=" * 78)
    print(title)
    print("=" * 78)


def main() -> None:
    banner("STEP 1 -- Stand up the internal Certificate Authority")
    ca = CertificateAuthority(common_name="WarpX Internal Root CA")
    registry = RevocationRegistry(ca_name=ca.name, ca_private_key=ca.private_key)
    print(f"Root CA created: {ca.common_name}")
    print(f"Root CA serial number: {ca.certificate.serial_number}")
    print(f"Root CA signature algorithm: {ca.certificate.signature_algorithm_oid._name}")
    print(f"Root CA validity: {ca.certificate.not_valid_before_utc} -> {ca.certificate.not_valid_after_utc}")

    banner("STEP 2 -- Issue two legitimate leaf certificates for internal hosts")
    api_host = "api.internal.warpx.local"
    db_host = "db-admin.internal.warpx.local"

    api_cert_bundle = ca.issue_leaf_certificate(api_host)
    db_cert_bundle = ca.issue_leaf_certificate(db_host)

    print(f"Issued leaf cert for '{api_host}' (serial {api_cert_bundle.serial_number})")
    print(f"Issued leaf cert for '{db_host}' (serial {db_cert_bundle.serial_number})")

    banner("STEP 3 -- Validate both legitimate certificates (expect ACCEPTED)")
    result_api = validate_certificate(
        api_cert_bundle.certificate,
        expected_hostname=api_host,
        ca_public_key=ca.public_key(),
        revocation_registry=registry,
    )
    print(result_api.summary())
    assert result_api.ok, "Legitimate api cert should have validated successfully!"

    result_db = validate_certificate(
        db_cert_bundle.certificate,
        expected_hostname=db_host,
        ca_public_key=ca.public_key(),
        revocation_registry=registry,
    )
    print()
    print(result_db.summary())
    assert result_db.ok, "Legitimate db-admin cert should have validated successfully!"

    banner("STEP 4 -- Attacker presents a SELF-SIGNED impersonation cert (expect REJECTED)")
    impersonation_bundle = create_self_signed_impersonation_certificate(api_host)
    print(
        f"Attacker-generated cert for '{api_host}', self-signed with a rogue key "
        f"(serial {impersonation_bundle.serial_number})"
    )
    result_impersonation = validate_certificate(
        impersonation_bundle.certificate,
        expected_hostname=api_host,
        ca_public_key=ca.public_key(),
        revocation_registry=registry,
    )
    print(result_impersonation.summary())
    assert not result_impersonation.ok, "Impersonation cert must be rejected!"
    assert not result_impersonation.checks["signature verifies against CA public key"], (
        "Impersonation cert should fail signature verification specifically"
    )

    banner("STEP 5 -- Present an already-EXPIRED certificate (expect REJECTED)")
    expired_host = "legacy-reporting.internal.warpx.local"
    expired_bundle = ca.issue_leaf_certificate(expired_host, force_expired=True)
    print(
        f"Issued (deliberately) expired cert for '{expired_host}' "
        f"(serial {expired_bundle.serial_number}), notAfter={expired_bundle.certificate.not_valid_after_utc}"
    )
    result_expired = validate_certificate(
        expired_bundle.certificate,
        expected_hostname=expired_host,
        ca_public_key=ca.public_key(),
        revocation_registry=registry,
    )
    print(result_expired.summary())
    assert not result_expired.ok, "Expired cert must be rejected!"
    assert not result_expired.checks["validity period (not expired / not premature)"], (
        "Expired cert should fail the validity-period check specifically"
    )

    banner("STEP 6 -- Revoke the 'api' cert (simulated key compromise), re-check via CRL")
    print(f"Revoking serial {api_cert_bundle.serial_number} ({api_host}) -- reason: key compromise")
    registry.revoke(api_cert_bundle.serial_number, reason="key compromise")

    signed_crl = registry.build_signed_crl()
    print(
        f"CA published a new signed CRL with {len(list(signed_crl))} revoked entr"
        f"{'y' if len(list(signed_crl)) == 1 else 'ies'}: "
        f"{[rc.serial_number for rc in signed_crl]}"
    )

    print()
    print("Re-validating the now-REVOKED 'api' cert (expect REJECTED):")
    result_api_revoked = validate_certificate(
        api_cert_bundle.certificate,
        expected_hostname=api_host,
        ca_public_key=ca.public_key(),
        revocation_registry=registry,
    )
    print(result_api_revoked.summary())
    assert not result_api_revoked.ok, "Revoked cert must be rejected!"
    assert not result_api_revoked.checks["not present on CRL (not revoked)"], (
        "Revoked cert should fail the CRL check specifically"
    )

    print()
    print("Re-validating the STILL-LEGITIMATE 'db-admin' cert (expect ACCEPTED):")
    result_db_still_ok = validate_certificate(
        db_cert_bundle.certificate,
        expected_hostname=db_host,
        ca_public_key=ca.public_key(),
        revocation_registry=registry,
    )
    print(result_db_still_ok.summary())
    assert result_db_still_ok.ok, "Non-revoked db-admin cert must still validate!"

    banner("ALL SCENARIOS VERIFIED")
    print("1. Legitimate api cert:            ACCEPTED")
    print("2. Legitimate db-admin cert:       ACCEPTED")
    print("3. Self-signed impersonation cert: REJECTED (signature does not verify)")
    print("4. Expired cert:                   REJECTED (validity period check)")
    print("5. Revoked api cert:               REJECTED (CRL check)")
    print("6. db-admin cert after revocation: ACCEPTED (unaffected by unrelated revocation)")


if __name__ == "__main__":
    main()
