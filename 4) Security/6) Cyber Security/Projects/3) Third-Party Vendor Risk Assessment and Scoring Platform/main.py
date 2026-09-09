"""
main.py

Entry point for the Third-Party Vendor Risk Assessment and Scoring Platform.

Runs the combined assessment (security-questionnaire posture x data-sensitivity
blast radius) across every vendor in the registry, prints each vendor's full
score breakdown and resulting risk tier, and then runs a specific side-by-side
demonstration proving that data sensitivity -- not just the questionnaire --
genuinely changes the outcome: two vendors (V001, V002) answer the exact same
security questionnaire, but because they hold different data, they land in
different risk tiers.

Real-world equivalent: the report a vendor risk committee reviews before
renewing or terminating a third-party contract.
"""

from vendor_registry import VENDORS, get_vendor
from risk_engine import assess_all, assess_vendor, RiskTier


def print_vendor_report(assessment) -> None:
    vendor = assessment.vendor
    print(f"\n{'=' * 72}")
    print(f"{vendor.vendor_id} -- {vendor.name}")
    print(f"Category         : {vendor.category}")
    print(f"Data access scope: {vendor.data_scope.describe()}")
    print(f"{'-' * 72}")
    print("Questionnaire posture score breakdown:")
    for line in assessment.posture_breakdown.as_lines():
        print(line)
    print(f"{'-' * 72}")
    print(f"Data sensitivity multiplier : x{assessment.sensitivity_multiplier}  "
          f"({assessment.sensitivity_label})")
    print(f"Overall risk score          : "
          f"{assessment.posture_breakdown.posture_risk_score} x "
          f"{assessment.sensitivity_multiplier} = {assessment.overall_risk_score}")
    print(f"RISK TIER                   : {assessment.tier.value}")
    if assessment.needs_enhanced_due_diligence:
        print("Enhanced due diligence      : REQUIRED")
        for reason in assessment.due_diligence_reasons:
            print(f"  - {reason}")
    else:
        print("Enhanced due diligence      : not required")


def print_full_registry_report() -> None:
    print("#" * 72)
    print("THIRD-PARTY VENDOR RISK ASSESSMENT -- FULL VENDOR REGISTRY")
    print("#" * 72)

    assessments = assess_all(VENDORS)
    for assessment in assessments:
        print_vendor_report(assessment)

    print(f"\n{'=' * 72}")
    print("SUMMARY")
    print(f"{'=' * 72}")
    print(f"{'Vendor ID':<10}{'Name':<32}{'Score':>8}{'Tier':>12}{'EDD?':>8}")
    for assessment in assessments:
        v = assessment.vendor
        print(
            f"{v.vendor_id:<10}{v.name:<32}{assessment.overall_risk_score:>8}"
            f"{assessment.tier.value:>12}"
            f"{'YES' if assessment.needs_enhanced_due_diligence else 'no':>8}"
        )


def print_same_questionnaire_different_tier_proof() -> None:
    """
    The core proof this platform exists to demonstrate: V001 and V002 answer
    the security questionnaire identically. If the assessment were secretly
    "just the questionnaire", they'd land in the same tier. They don't --
    because V001 (payroll: SSNs + bank accounts + health/benefits data) and
    V002 (newsletter platform: email addresses only) have identical hygiene
    but wildly different blast radius.
    """
    print(f"\n\n{'#' * 72}")
    print("SIDE-BY-SIDE PROOF: IDENTICAL QUESTIONNAIRE, DIFFERENT DATA ACCESS")
    print(f"{'#' * 72}")

    vendor_a = get_vendor("V001")
    vendor_b = get_vendor("V002")

    assert vendor_a.questionnaire == vendor_b.questionnaire, (
        "Demo precondition failed: V001 and V002 must have identical "
        "questionnaire answers to prove data sensitivity is what moves the tier."
    )

    assessment_a = assess_vendor(vendor_a)
    assessment_b = assess_vendor(vendor_b)

    print(f"\n{vendor_a.vendor_id} ({vendor_a.name}) vs. "
          f"{vendor_b.vendor_id} ({vendor_b.name})")
    print(f"{'-' * 72}")
    print("Questionnaire answers (IDENTICAL for both vendors):")
    q = vendor_a.questionnaire
    print(f"  encryption_at_rest      : {q.encryption_at_rest}")
    print(f"  mfa_enforced            : {q.mfa_enforced}")
    print(f"  has_soc2                : {q.has_soc2}")
    print(f"  breach_in_last_2_years  : {q.breach_in_last_2_years}")
    print(f"  subprocessor_count      : {q.subprocessor_count}")
    print(f"  -> posture risk score   : {assessment_a.posture_breakdown.posture_risk_score}"
          f" (identical for both, since the questionnaire is identical)")

    print(f"\n{'-' * 72}")
    print(f"{'':<28}{vendor_a.vendor_id:>20}{vendor_b.vendor_id:>20}")
    print(f"{'Data access scope':<28}"
          f"{vendor_a.data_scope.data_type.value.upper():>20}"
          f"{vendor_b.data_scope.data_type.value.upper():>20}")
    print(f"{'Record count':<28}"
          f"{vendor_a.data_scope.record_count:>20,}"
          f"{vendor_b.data_scope.record_count:>20,}")
    print(f"{'Sensitivity multiplier':<28}"
          f"{'x' + str(assessment_a.sensitivity_multiplier):>20}"
          f"{'x' + str(assessment_b.sensitivity_multiplier):>20}")
    print(f"{'Overall risk score':<28}"
          f"{assessment_a.overall_risk_score:>20}"
          f"{assessment_b.overall_risk_score:>20}")
    print(f"{'RISK TIER':<28}"
          f"{assessment_a.tier.value:>20}"
          f"{assessment_b.tier.value:>20}")

    print(f"\n{'-' * 72}")
    assert assessment_a.tier != assessment_b.tier, (
        f"Expected {vendor_a.vendor_id} and {vendor_b.vendor_id} to land in "
        f"different risk tiers despite identical questionnaires -- got "
        f"{assessment_a.tier.value} for both. Data sensitivity failed to "
        f"change the outcome."
    )
    print(
        f"PROVEN: same questionnaire, but {vendor_a.vendor_id} = "
        f"{assessment_a.tier.value} vs {vendor_b.vendor_id} = "
        f"{assessment_b.tier.value}."
    )
    print(
        "Data sensitivity (what the vendor can actually access) genuinely "
        "changes the risk outcome -- it is not just the questionnaire in disguise."
    )


def main() -> None:
    print_full_registry_report()
    print_same_questionnaire_different_tier_proof()


if __name__ == "__main__":
    main()
