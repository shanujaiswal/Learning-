"""
data_sensitivity_model.py

Scores how sensitive/high-impact a vendor's DATA ACCESS SCOPE is -- i.e. not
"is this vendor secure", but "what happens to us if this vendor is breached".

This is the missing half of most naive vendor questionnaires: a vendor can
answer every security question perfectly and still be a huge blast-radius
risk if what they touch is SSNs and bank account numbers, versus a vendor
with a shaky questionnaire that only ever sees anonymized aggregate counts.

Real-world equivalent: a data classification policy (Public / Internal /
Confidential / Restricted) combined with a data-flow / data-mapping exercise
that tells you WHERE regulated data actually goes once it leaves your walls.
"""

from dataclasses import dataclass
from enum import Enum


class DataType(Enum):
    """Category of data the vendor can access, roughly ordered by breach impact."""
    NONE = "none"                    # no personal/regulated data at all
    ANONYMIZED = "anonymized"        # aggregated / de-identified data only
    PII = "pii"                      # personally identifiable info (names, emails, addresses)
    PCI = "pci"                      # payment card / bank account data
    PHI = "phi"                      # protected health information
    PCI_PHI = "pci_phi"              # both financial AND health data (e.g. payroll+benefits admin)


# Base impact multiplier per data type. This is the core "blast radius" weight:
# regulatory fines, breach notification cost, and reputational damage all scale
# with how sensitive the exposed data is, independent of vendor security hygiene.
_DATA_TYPE_MULTIPLIER = {
    DataType.NONE: 0.4,
    DataType.ANONYMIZED: 0.6,
    DataType.PII: 1.0,
    DataType.PCI: 1.6,
    DataType.PHI: 1.8,
    DataType.PCI_PHI: 2.2,
}


class VolumeTier(Enum):
    """How many individuals' records the vendor can reach."""
    NONE = "none"          # 0 records
    SMALL = "small"        # < 1,000 records
    MEDIUM = "medium"      # 1,000 - 50,000 records
    LARGE = "large"        # 50,000 - 1,000,000 records
    MASSIVE = "massive"    # > 1,000,000 records


# Volume amplifies impact but by a much smaller margin than data type --
# a PHI breach of 500 records is already catastrophic; a PII breach of
# 2,000,000 records is bad but each record alone is far less damaging.
_VOLUME_MULTIPLIER = {
    VolumeTier.NONE: 0.8,
    VolumeTier.SMALL: 0.9,
    VolumeTier.MEDIUM: 1.0,
    VolumeTier.LARGE: 1.15,
    VolumeTier.MASSIVE: 1.3,
}


def volume_tier_from_count(record_count: int) -> VolumeTier:
    """Bucket a raw record count into a VolumeTier."""
    if record_count <= 0:
        return VolumeTier.NONE
    if record_count < 1_000:
        return VolumeTier.SMALL
    if record_count < 50_000:
        return VolumeTier.MEDIUM
    if record_count < 1_000_000:
        return VolumeTier.LARGE
    return VolumeTier.MASSIVE


@dataclass(frozen=True)
class DataAccessScope:
    """What a vendor can actually see/touch, and how much of it."""
    data_type: DataType
    record_count: int

    @property
    def volume_tier(self) -> VolumeTier:
        return volume_tier_from_count(self.record_count)

    def describe(self) -> str:
        return (f"{self.data_type.value.upper()} data, "
                f"~{self.record_count:,} records ({self.volume_tier.value} volume)")


def sensitivity_multiplier(scope: DataAccessScope) -> float:
    """
    Combined data-sensitivity multiplier for a vendor's access scope.

    multiplier = data_type_weight * volume_weight

    This is the number risk_engine.py multiplies the questionnaire-derived
    security-posture risk score by. It is the ONLY thing that changes between
    two vendors who answer the same questionnaire identically but hold
    different data -- proving the tier isn't just "the questionnaire in
    disguise".
    """
    data_weight = _DATA_TYPE_MULTIPLIER[scope.data_type]
    volume_weight = _VOLUME_MULTIPLIER[scope.volume_tier]
    return round(data_weight * volume_weight, 3)


def sensitivity_label(multiplier: float) -> str:
    """Human-readable bucket for a given multiplier, used in report printouts."""
    if multiplier >= 2.0:
        return "Extreme (regulated financial + health data)"
    if multiplier >= 1.5:
        return "High (regulated financial or health data)"
    if multiplier >= 0.9:
        return "Moderate (identifiable personal data)"
    return "Low (anonymized or no personal data)"
