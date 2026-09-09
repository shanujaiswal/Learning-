"""
05_causal_inference_intro.py

Demonstrates "Causal Inference Beyond A/B Testing" using the classic
Difference-in-Differences (DiD) technique.

Scenario: a company rolls out a loyalty program to customers in one region
(treatment) but not another (control), and we have average weekly spend
BEFORE and AFTER the rollout for both groups. Because we can't randomize who
gets the loyalty program (unlike a true A/B test), DiD lets us estimate the
program's causal effect by netting out any trend that would have happened
anyway.

    DiD estimate = (treatment_after - treatment_before)
                 - (control_after   - control_before)

The control group's before/after change proxies for what WOULD have happened
to the treatment group without the intervention (the "counterfactual"),
under the key assumption of parallel trends.

Run:
    python 05_causal_inference_intro.py
"""

import numpy as np
from scipy import stats

RNG_SEED = 7
ALPHA = 0.05


def generate_did_data(
    n_per_group: int = 500,
    control_before_mean: float = 50.0,
    treatment_before_mean: float = 48.0,
    common_trend: float = 3.0,   # both groups would drift up by this much anyway
    true_treatment_effect: float = 6.0,  # the actual causal effect we're trying to recover
    noise_sd: float = 8.0,
    seed: int = RNG_SEED,
) -> dict[str, np.ndarray]:
    """Simulate weekly spend for 4 cells: control/treatment x before/after."""
    rng = np.random.default_rng(seed)

    control_before = rng.normal(control_before_mean, noise_sd, n_per_group)
    control_after = rng.normal(control_before_mean + common_trend, noise_sd, n_per_group)

    treatment_before = rng.normal(treatment_before_mean, noise_sd, n_per_group)
    treatment_after = rng.normal(
        treatment_before_mean + common_trend + true_treatment_effect, noise_sd, n_per_group
    )

    return {
        "control_before": control_before,
        "control_after": control_after,
        "treatment_before": treatment_before,
        "treatment_after": treatment_after,
    }


def difference_in_differences(data: dict[str, np.ndarray]) -> dict[str, float]:
    """Compute the DiD estimate and its four component means."""
    means = {k: v.mean() for k, v in data.items()}

    control_change = means["control_after"] - means["control_before"]
    treatment_change = means["treatment_after"] - means["treatment_before"]
    did_estimate = treatment_change - control_change

    return {
        **means,
        "control_change": control_change,
        "treatment_change": treatment_change,
        "did_estimate": did_estimate,
    }


def did_significance_test(data: dict[str, np.ndarray]) -> tuple[float, float]:
    """Test whether the DiD estimate is significantly different from zero.

    Equivalent to the interaction term in the regression:
        spend ~ treatment_group + after_period + treatment_group:after_period
    Implemented here directly as a t-test on the (after - before) differences
    of each unit's group, comparing treatment's per-unit change to control's.
    Since our synthetic groups are independent samples (not paired individuals),
    we approximate with an independent two-sample t-test on the "change"
    distributions built by pairing before/after values index-wise.
    """
    control_diff = data["control_after"] - data["control_before"]
    treatment_diff = data["treatment_after"] - data["treatment_before"]
    t_stat, p_value = stats.ttest_ind(treatment_diff, control_diff, equal_var=False)
    return t_stat, p_value


def main() -> None:
    print("=" * 70)
    print("CAUSAL INFERENCE: Difference-in-Differences (loyalty program rollout)")
    print("=" * 70)

    data = generate_did_data()
    result = difference_in_differences(data)

    print("\nAverage weekly spend by group and period:")
    print(f"  Control   - before: ${result['control_before']:.2f}   after: ${result['control_after']:.2f}")
    print(f"  Treatment - before: ${result['treatment_before']:.2f}   after: ${result['treatment_after']:.2f}")

    print("\nChange over time within each group:")
    print(f"  Control change:   {result['control_change']:+.2f}  (the 'business as usual' trend)")
    print(f"  Treatment change: {result['treatment_change']:+.2f}  (trend + any treatment effect)")

    print("\n" + "-" * 70)
    print("Difference-in-Differences estimate")
    print("-" * 70)
    print(
        f"DiD = treatment_change - control_change "
        f"= {result['treatment_change']:.2f} - {result['control_change']:.2f} "
        f"= {result['did_estimate']:+.2f}"
    )
    print(
        f"\nInterpretation: after removing the underlying trend that affected "
        f"BOTH groups (estimated from the control group), the loyalty program "
        f"is estimated to have caused an average increase of "
        f"${result['did_estimate']:.2f} per customer per week - this is the "
        f"causal effect, not just a before/after correlation."
    )

    t_stat, p_value = did_significance_test(data)
    print("\n" + "-" * 70)
    print("Significance of the DiD estimate")
    print("-" * 70)
    print(f"t-statistic: {t_stat:.4f}  |  p-value: {p_value:.6f}")
    if p_value < ALPHA:
        print(
            f"Since p={p_value:.6f} < {ALPHA}, the estimated causal effect is "
            f"statistically significant - unlikely to be random noise."
        )
    else:
        print(
            f"Since p={p_value:.6f} >= {ALPHA}, we cannot confidently distinguish "
            f"this estimate from zero effect."
        )

    print(
        "\nKey assumption (parallel trends): this DiD estimate is only valid if "
        "the control group's before/after change is a good proxy for what the "
        "treatment group would have done WITHOUT the loyalty program. If the two "
        "regions were already diverging for unrelated reasons, the estimate "
        "would be biased - this is the main threat to causal validity beyond "
        "a randomized A/B test."
    )


if __name__ == "__main__":
    main()
