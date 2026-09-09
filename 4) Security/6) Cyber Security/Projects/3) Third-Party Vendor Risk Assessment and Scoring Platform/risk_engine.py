"""
risk_engine.py

Combines the questionnaire-derived security-posture risk score with the
data-sensitivity multiplier into a single overall risk score and tier per
vendor, and flags vendors needing enhanced due diligence / a corrective
action plan before renewal.

Real-world equivalent: a real TPRM (Third-Party Risk Management) platform's
risk-scoring model (e.g. OneTrust, BitSight, SecurityScorecard) -- the part
that turns "questionnaire answers" + "what this vendor touches" into a
single number a risk committee can act on.
"""

from dataclasses import dataclass
from enum import Enum

from data_sensitivity_model import DataAccessScope, DataType, sensitivity_multiplier, sensitivity_label
from questionnaire_scorer import PostureScoreBreakdown, QuestionnaireResponse, score_questionnaire
from vendor_registry import Vendor

# Data types that count as "regulated, high-impact" for the breach override rule.
_HIGH_IMPACT_DATA_TYPES = {DataType.PCI, DataType.PHI, DataType.PCI_PHI}


class RiskTier(Enum):
    LOW = "Low"
    MEDIUM = "Medium"
    HIGH = "High"
    CRITICAL = "Critical"


# Overall risk score = posture_risk_score (0-100) x sensitivity_multiplier
# (roughly 0.3 - 2.9). These thresholds were tuned against the sample vendor
# registry so that tiers line up with intuition:
#   - a strong vendor holding only anonymized/no data lands Low
#   - a strong vendor holding highly regulated bulk data still lands Medium/High
#     purely because of what they hold (the "same questionnaire, different
#     tier" proof in main.py)
#   - a weak vendor (missing controls, recent breach) holding regulated data
#     lands Critical
_TIER_THRESHOLDS = (
    (140, RiskTier.CRITICAL),
    (80, RiskTier.HIGH),
    (35, RiskTier.MEDIUM),
)


def _tier_from_score(overall_score: float) -> RiskTier:
    for threshold, tier in _TIER_THRESHOLDS:
        if overall_score >= threshold:
            return tier
    return RiskTier.LOW


@dataclass(frozen=True)
class VendorAssessment:
    vendor: Vendor
    posture_breakdown: PostureScoreBreakdown
    sensitivity_multiplier: float
    sensitivity_label: str
    overall_risk_score: float
    tier: RiskTier
    needs_enhanced_due_diligence: bool
    due_diligence_reasons: list


def _breach_override_reason(questionnaire: QuestionnaireResponse, data_scope: DataAccessScope):
    """
    A vendor with a disclosed breach in the last 2 years, who ALSO handles
    high-impact regulated data (PCI/PHI), must get enhanced due diligence
    regardless of how good their other questionnaire answers look -- a past
    breach touching that class of data is disqualifying on its own.
    """
    if questionnaire.breach_in_last_2_years and data_scope.data_type in _HIGH_IMPACT_DATA_TYPES:
        return (f"Disclosed breach in last 2 years while handling "
                f"{data_scope.data_type.value.upper()} data -- treated as automatic "
                f"enhanced-due-diligence trigger regardless of other answers.")
    return None


def assess_vendor(vendor: Vendor) -> VendorAssessment:
    """Run the full combined assessment for a single vendor."""
    posture_breakdown = score_questionnaire(vendor.questionnaire)
    multiplier = sensitivity_multiplier(vendor.data_scope)
    label = sensitivity_label(multiplier)

    overall_score = round(posture_breakdown.posture_risk_score * multiplier, 1)
    tier = _tier_from_score(overall_score)

    reasons = []
    if tier in (RiskTier.CRITICAL, RiskTier.HIGH):
        reasons.append(f"Overall risk tier is {tier.value} (score {overall_score}).")

    breach_reason = _breach_override_reason(vendor.questionnaire, vendor.data_scope)
    if breach_reason:
        reasons.append(breach_reason)

    return VendorAssessment(
        vendor=vendor,
        posture_breakdown=posture_breakdown,
        sensitivity_multiplier=multiplier,
        sensitivity_label=label,
        overall_risk_score=overall_score,
        tier=tier,
        needs_enhanced_due_diligence=bool(reasons),
        due_diligence_reasons=reasons,
    )


def assess_all(vendors) -> list:
    return [assess_vendor(v) for v in vendors]
