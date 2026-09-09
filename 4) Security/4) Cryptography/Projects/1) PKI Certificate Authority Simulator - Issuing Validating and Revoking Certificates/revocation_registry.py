"""
revocation_registry.py
-----------------------
A minimal Certificate Revocation List (CRL) maintained by the CA.

Real-world equivalent: a CA's published CRL file (a signed list of revoked
serial numbers, refreshed periodically) or, more commonly today, a live
OCSP responder that answers "good / revoked / unknown" per-serial queries in
real time. This module models the CRL approach explicitly, as described in
the theory doc: the CA maintains a list of revoked serial numbers, and any
relying party (chain_validator.py here) checks a presented cert's serial
number against that list before trusting it.

We also generate a REAL, CA-signed x509.CertificateRevocationList object
(via `cryptography.x509.CertificateRevocationListBuilder`) so that the CRL
itself is a genuine, cryptographically signed artifact -- not a fake stand-in
data structure -- consistent with the project's "real X.509, not toys" rule.
"""

from __future__ import annotations

import datetime

from cryptography import x509
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric.rsa import RSAPrivateKey


class RevocationRegistry:
    """
    Tracks revoked certificate serial numbers on behalf of the CA and can
    produce a real, CA-signed CRL at any time.
    """

    def __init__(self, ca_name: x509.Name, ca_private_key: RSAPrivateKey):
        self._ca_name = ca_name
        self._ca_private_key = ca_private_key
        # serial_number -> (revocation_datetime, reason string for humans)
        self._revoked: dict[int, tuple[datetime.datetime, str]] = {}

    def revoke(self, serial_number: int, reason: str = "key compromise") -> None:
        """Mark a certificate's serial number as revoked, effective now."""
        self._revoked[serial_number] = (datetime.datetime.now(datetime.timezone.utc), reason)

    def is_revoked(self, serial_number: int) -> bool:
        return serial_number in self._revoked

    def revocation_reason(self, serial_number: int) -> str | None:
        entry = self._revoked.get(serial_number)
        return entry[1] if entry else None

    def revoked_serials(self) -> list[int]:
        return list(self._revoked.keys())

    def build_signed_crl(self) -> x509.CertificateRevocationList:
        """
        Build and CA-sign a real x509 CRL object containing every revoked
        serial number registered so far. This is exactly the artifact a real
        CA publishes at its CRL Distribution Point URL.
        """
        now = datetime.datetime.now(datetime.timezone.utc)
        builder = (
            x509.CertificateRevocationListBuilder()
            .issuer_name(self._ca_name)
            .last_update(now)
            .next_update(now + datetime.timedelta(days=7))
        )
        for serial, (revoked_at, _reason) in self._revoked.items():
            revoked_cert = (
                x509.RevokedCertificateBuilder()
                .serial_number(serial)
                .revocation_date(revoked_at)
                .build()
            )
            builder = builder.add_revoked_certificate(revoked_cert)

        return builder.sign(private_key=self._ca_private_key, algorithm=hashes.SHA256())
