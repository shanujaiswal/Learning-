"""
questionnaire_scorer.py

Scores a vendor's raw security-questionnaire answers into a single
"security posture risk score" (0-100, HIGHER = WORSE security hygiene).

Deliberately knows NOTHING about what data the vendor can access -- this is
the "how good is this vendor at security" half of the assessment, kept
completely independent from the "how bad would it be if they got breached"
half (data_sensitivity_model.py). risk_engine.py is the only place the two
are combined.

Real-world equivalent: the scoring rubric behind a vendor security
questionnaire (e.g. SIG Lite / CAIQ) before it gets weighted by data
criticality.
"""

from dataclasses import dataclass


@dataclass(frozen=True)
class QuestionnaireResponse:
    """Raw answers a vendor gives in a security self-assessment questionnaire."""
    encryption_at_rest: bool          # does the vendor encrypt data at rest?
    mfa_enforced: bool                # is MFA enforced for all admin/user access?
    has_soc2: bool                    # does the vendor hold a current SOC 2 (or ISO 27001) report?
    breach_in_last_2_years: bool      # any disclosed security breach in the last 24 months?
    subprocessor_count: int           # number of fourth-party subprocessors they rely on


# Risk points added when a control is MISSING (or a breach occurred).
# Weighted so that the two "prevent unauthorized access" controls
# (encryption + MFA) dominate, since their absence is the most direct path
# to a mass-exposure incident.
_NO_ENCRYPTION_PENALTY = 30
_NO_MFA_PENALTY = 25
_NO_SOC2_PENALTY = 20
_RECENT_BREACH_PENALTY = 20

# Subprocessor sprawl penalty: every subprocessor beyond a "reasonable" baseline
# of 3 adds fourth-party risk (see theory file), capped so it can't alone push
# an otherwise-good vendor into a bad score.
_SUBPROCESSOR_FREE_ALLOWANCE = 3
_SUBPROCESSOR_PENALTY_PER_EXTRA = 2
_SUBPROCESSOR_PENALTY_CAP = 15

MAX_POSTURE_RISK = 100


@dataclass(frozen=True)
class PostureScoreBreakdown:
    """Itemized contribution of each questionnaire answer to the final score."""
    encryption_penalty: int
    mfa_penalty: int
    soc2_penalty: int
    breach_penalty: int
    subprocessor_penalty: int
    raw_total: int
    posture_risk_score: int  # raw_total capped at MAX_POSTURE_RISK

    def as_lines(self) -> list:
        return [
            f"  encryption-at-rest missing : {self.encryption_penalty:+d}",
            f"  MFA not enforced           : {self.mfa_penalty:+d}",
            f"  no current SOC 2 report    : {self.soc2_penalty:+d}",
            f"  breach in last 2 years     : {self.breach_penalty:+d}",
            f"  subprocessor sprawl        : {self.subprocessor_penalty:+d}",
            f"  ------------------------------------",
            f"  posture risk score (0-100): {self.posture_risk_score}"
            + ("  (capped)" if self.raw_total > MAX_POSTURE_RISK else ""),
        ]


def score_questionnaire(response: QuestionnaireResponse) -> PostureScoreBreakdown:
    """
    Convert raw questionnaire answers into a posture RISK score.

    0   = perfect security hygiene (encryption + MFA + SOC2, no breach, lean subprocessor list)
    100 = worst-case security hygiene across every measured control
    """
    encryption_penalty = 0 if response.encryption_at_rest else _NO_ENCRYPTION_PENALTY
    mfa_penalty = 0 if response.mfa_enforced else _NO_MFA_PENALTY
    soc2_penalty = 0 if response.has_soc2 else _NO_SOC2_PENALTY
    breach_penalty = _RECENT_BREACH_PENALTY if response.breach_in_last_2_years else 0

    extra_subprocessors = max(0, response.subprocessor_count - _SUBPROCESSOR_FREE_ALLOWANCE)
    subprocessor_penalty = min(
        _SUBPROCESSOR_PENALTY_CAP,
        extra_subprocessors * _SUBPROCESSOR_PENALTY_PER_EXTRA,
    )

    raw_total = (
        encryption_penalty + mfa_penalty + soc2_penalty + breach_penalty + subprocessor_penalty
    )
    posture_risk_score = min(MAX_POSTURE_RISK, raw_total)

    return PostureScoreBreakdown(
        encryption_penalty=encryption_penalty,
        mfa_penalty=mfa_penalty,
        soc2_penalty=soc2_penalty,
        breach_penalty=breach_penalty,
        subprocessor_penalty=subprocessor_penalty,
        raw_total=raw_total,
        posture_risk_score=posture_risk_score,
    )
