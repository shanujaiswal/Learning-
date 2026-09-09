"""
certificate_authority.py
-------------------------
A small, REAL Certificate Authority built entirely on the `cryptography` library's
x509 module. No toy/fake certificate structures anywhere in this project -- every
certificate produced here is a real, ASN.1-encoded, RSA-signed X.509 certificate
that any standard TLS stack (openssl, browsers, etc.) could parse and verify.

Responsibilities of this module:
  1. Generate a root CA keypair and a real self-signed root CA certificate.
  2. Issue leaf ("end-entity") certificates for internal hostnames, signed by the
     CA's private key, with real validity windows (notBefore / notAfter).
  3. Issue an already-expired certificate on demand, purely for demonstrating that
     chain validation correctly rejects expired certs.

This models what a private/internal PKI does in the real world -- comparable to
running your own instance of step-ca, HashiCorp Vault's PKI secrets engine, or an
internal Microsoft Active Directory Certificate Services (AD CS) deployment.
"""

from __future__ import annotations

import datetime
from dataclasses import dataclass

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.hazmat.primitives.asymmetric.rsa import RSAPrivateKey
from cryptography.x509.oid import NameOID

RSA_KEY_SIZE = 2048
ROOT_VALIDITY_DAYS = 3650   # 10 years, typical of a real root CA
LEAF_VALIDITY_DAYS = 90     # short-lived leaf certs, Let's-Encrypt-style hygiene


@dataclass
class IssuedCertificate:
    """A convenience bundle of a certificate and the private key that matches it."""
    certificate: x509.Certificate
    private_key: RSAPrivateKey

    @property
    def serial_number(self) -> int:
        return self.certificate.serial_number

    @property
    def common_name(self) -> str:
        return self.certificate.subject.get_attributes_for_oid(NameOID.COMMON_NAME)[0].value


def _generate_rsa_key() -> RSAPrivateKey:
    return rsa.generate_private_key(public_exponent=65537, key_size=RSA_KEY_SIZE)


def _build_name(common_name: str, org: str = "WarpX Internal Services") -> x509.Name:
    return x509.Name([
        x509.NameAttribute(NameOID.COUNTRY_NAME, "US"),
        x509.NameAttribute(NameOID.ORGANIZATION_NAME, org),
        x509.NameAttribute(NameOID.COMMON_NAME, common_name),
    ])


class CertificateAuthority:
    """
    A minimal, self-hosted internal CA.

    Real-world equivalent: the CA role played by step-ca / HashiCorp Vault PKI /
    an internal AD CS server -- something a company runs itself to issue certs
    for services that never touch the public internet (internal APIs, admin
    dashboards, service-to-service mTLS, etc.), instead of paying a public CA
    like DigiCert or relying on Let's Encrypt (which requires public DNS/HTTP
    reachability that internal-only hosts don't have).
    """

    def __init__(self, common_name: str = "WarpX Internal Root CA"):
        self.common_name = common_name
        self.private_key: RSAPrivateKey = _generate_rsa_key()
        self.name = _build_name(common_name, org="WarpX Internal Root CA Org")
        self.certificate: x509.Certificate = self._create_self_signed_root()
        # Track every serial number this CA has ever issued, so the revocation
        # registry (see revocation_registry.py) can be checked meaningfully.
        self._issued_serials: set[int] = {self.certificate.serial_number}

    # ------------------------------------------------------------------ #
    # Root CA creation
    # ------------------------------------------------------------------ #
    def _create_self_signed_root(self) -> x509.Certificate:
        now = datetime.datetime.now(datetime.timezone.utc)
        builder = (
            x509.CertificateBuilder()
            .subject_name(self.name)
            .issuer_name(self.name)  # self-signed: issuer == subject
            .public_key(self.private_key.public_key())
            .serial_number(x509.random_serial_number())
            .not_valid_before(now - datetime.timedelta(minutes=5))
            .not_valid_after(now + datetime.timedelta(days=ROOT_VALIDITY_DAYS))
            .add_extension(x509.BasicConstraints(ca=True, path_length=None), critical=True)
            .add_extension(
                x509.KeyUsage(
                    digital_signature=False, content_commitment=False, key_encipherment=False,
                    data_encipherment=False, key_agreement=False, key_cert_sign=True,
                    crl_sign=True, encipher_only=False, decipher_only=False,
                ),
                critical=True,
            )
            .add_extension(
                x509.SubjectKeyIdentifier.from_public_key(self.private_key.public_key()),
                critical=False,
            )
        )
        return builder.sign(self.private_key, hashes.SHA256())

    # ------------------------------------------------------------------ #
    # Leaf issuance
    # ------------------------------------------------------------------ #
    def issue_leaf_certificate(
        self,
        hostname: str,
        validity_days: int = LEAF_VALIDITY_DAYS,
        *,
        backdate_days: int | None = None,
        force_expired: bool = False,
    ) -> IssuedCertificate:
        """
        Issue a real leaf certificate signed by this CA's private key, for the
        given internal hostname (placed in both CN and SAN, matching modern
        hostname-validation practice).

        force_expired=True produces a certificate whose notAfter is already in
        the past -- used only to demonstrate that chain_validator.py correctly
        rejects expired certificates. A real CA would never willingly issue
        this; we do it here purely to create a realistic "bad" input.
        """
        leaf_key = _generate_rsa_key()
        leaf_name = _build_name(hostname, org="WarpX Internal Services")
        now = datetime.datetime.now(datetime.timezone.utc)

        if force_expired:
            not_before = now - datetime.timedelta(days=30)
            not_after = now - datetime.timedelta(days=1)   # expired yesterday
        else:
            not_before = now - datetime.timedelta(minutes=(backdate_days * 1440 if backdate_days else 5))
            not_after = now + datetime.timedelta(days=validity_days)

        serial = x509.random_serial_number()

        builder = (
            x509.CertificateBuilder()
            .subject_name(leaf_name)
            .issuer_name(self.name)                 # issued BY the CA
            .public_key(leaf_key.public_key())
            .serial_number(serial)
            .not_valid_before(not_before)
            .not_valid_after(not_after)
            .add_extension(x509.BasicConstraints(ca=False, path_length=None), critical=True)
            .add_extension(
                x509.SubjectAlternativeName([x509.DNSName(hostname)]),
                critical=False,
            )
            .add_extension(
                x509.KeyUsage(
                    digital_signature=True, content_commitment=False, key_encipherment=True,
                    data_encipherment=False, key_agreement=False, key_cert_sign=False,
                    crl_sign=False, encipher_only=False, decipher_only=False,
                ),
                critical=True,
            )
            .add_extension(
                x509.ExtendedKeyUsage([x509.oid.ExtendedKeyUsageOID.SERVER_AUTH]),
                critical=False,
            )
            .add_extension(
                x509.AuthorityKeyIdentifier.from_issuer_public_key(self.private_key.public_key()),
                critical=False,
            )
        )

        certificate = builder.sign(self.private_key, hashes.SHA256())
        self._issued_serials.add(serial)
        return IssuedCertificate(certificate=certificate, private_key=leaf_key)

    # ------------------------------------------------------------------ #
    # Utility
    # ------------------------------------------------------------------ #
    def public_key(self):
        return self.private_key.public_key()

    def was_issued_by_us(self, serial_number: int) -> bool:
        return serial_number in self._issued_serials


def create_self_signed_impersonation_certificate(hostname: str) -> IssuedCertificate:
    """
    Simulate an attacker: creates a certificate for `hostname` that is
    self-signed with a freshly generated, completely unrelated private key --
    i.e. NOT signed by the real CA at all. This is exactly the kind of
    certificate an attacker running a rogue/mitm server would present while
    claiming to be an internal host, and it must be rejected by
    chain_validator.py because its signature does not verify against the CA's
    public key.
    """
    attacker_key = _generate_rsa_key()
    name = _build_name(hostname, org="Totally Legit Attacker Inc")
    now = datetime.datetime.now(datetime.timezone.utc)

    builder = (
        x509.CertificateBuilder()
        .subject_name(name)
        .issuer_name(name)  # self-signed -- issuer == subject, no CA involved
        .public_key(attacker_key.public_key())
        .serial_number(x509.random_serial_number())
        .not_valid_before(now - datetime.timedelta(minutes=5))
        .not_valid_after(now + datetime.timedelta(days=365))
        .add_extension(x509.BasicConstraints(ca=False, path_length=None), critical=True)
        .add_extension(x509.SubjectAlternativeName([x509.DNSName(hostname)]), critical=False)
    )
    certificate = builder.sign(attacker_key, hashes.SHA256())
    return IssuedCertificate(certificate=certificate, private_key=attacker_key)


def to_pem(certificate: x509.Certificate) -> bytes:
    return certificate.public_bytes(serialization.Encoding.PEM)
