"""
04_ab_test_analysis.py

Demonstrates "A/B Testing and Experimentation" end to end:

    1. Generate synthetic control vs treatment conversion data (a fake
       "new checkout button" experiment).
    2. Run a two-proportion z-test AND a chi-square test of independence
       (scipy.stats) to check statistical significance.
    3. Compute effect size (absolute lift, relative lift, Cohen's h).
    4. Print a clear significant? / effect size / recommendation summary.

Run:
    python 04_ab_test_analysis.py
"""

import numpy as np
from scipy import stats

ALPHA = 0.05  # standard significance threshold
RNG_SEED = 42


def generate_ab_data(
    n_control: int = 4000,
    n_treatment: int = 4000,
    control_rate: float = 0.10,
    treatment_rate: float = 0.115,
    seed: int = RNG_SEED,
) -> tuple[int, int, int, int]:
    """Simulate a conversion experiment: each visitor converts (1) or not (0).

    Returns (control_conversions, control_n, treatment_conversions, treatment_n).
    """
    rng = np.random.default_rng(seed)
    control = rng.binomial(1, control_rate, n_control)
    treatment = rng.binomial(1, treatment_rate, n_treatment)
    return control.sum(), n_control, treatment.sum(), n_treatment


def two_proportion_z_test(x1: int, n1: int, x2: int, n2: int) -> tuple[float, float]:
    """Two-proportion z-test (pooled variance under H0: p1 == p2).

    x1, n1 = conversions/total for group 1 (control)
    x2, n2 = conversions/total for group 2 (treatment)
    Returns (z_statistic, two_sided_p_value).
    """
    p1, p2 = x1 / n1, x2 / n2
    p_pool = (x1 + x2) / (n1 + n2)
    se = (p_pool * (1 - p_pool) * (1 / n1 + 1 / n2)) ** 0.5
    z_stat = (p2 - p1) / se
    p_value = 2 * (1 - stats.norm.cdf(abs(z_stat)))
    return z_stat, p_value


def chi_square_test(x1: int, n1: int, x2: int, n2: int) -> tuple[float, float]:
    """Chi-square test of independence on the 2x2 contingency table:

                converted   not_converted
        control     x1         n1 - x1
        treatment   x2         n2 - x2
    """
    table = np.array([[x1, n1 - x1], [x2, n2 - x2]])
    chi2, p_value, _dof, _expected = stats.chi2_contingency(table, correction=False)
    return chi2, p_value


def cohens_h(p1: float, p2: float) -> float:
    """Cohen's h: an effect size for the difference between two proportions.

    Uses the arcsine transformation so the effect size is comparable across
    different baseline rates. Rule of thumb: 0.2 small, 0.5 medium, 0.8 large.
    """
    phi1 = 2 * np.arcsin(np.sqrt(p1))
    phi2 = 2 * np.arcsin(np.sqrt(p2))
    return phi2 - phi1


def main() -> None:
    print("=" * 70)
    print("A/B TEST: does the new checkout button increase conversion rate?")
    print("=" * 70)

    control_x, control_n, treatment_x, treatment_n = generate_ab_data()
    p_control = control_x / control_n
    p_treatment = treatment_x / treatment_n

    print(f"\nControl:   {control_x}/{control_n} converted  (rate = {p_control:.4f})")
    print(f"Treatment: {treatment_x}/{treatment_n} converted  (rate = {p_treatment:.4f})")

    # --- Significance testing ---
    print("\n" + "-" * 70)
    print("Two-proportion z-test")
    print("-" * 70)
    z_stat, p_value_z = two_proportion_z_test(control_x, control_n, treatment_x, treatment_n)
    print(f"z-statistic: {z_stat:.4f}  |  p-value: {p_value_z:.6f}")

    print("\n" + "-" * 70)
    print("Chi-square test of independence (cross-check)")
    print("-" * 70)
    chi2_stat, p_value_chi2 = chi_square_test(control_x, control_n, treatment_x, treatment_n)
    print(f"chi2-statistic: {chi2_stat:.4f}  |  p-value: {p_value_chi2:.6f}")
    print(
        "\nNote: for a 2x2 table, chi-square is mathematically equivalent to the "
        "two-sided z-test (chi2 approx equals z^2), so the two p-values should "
        "closely agree - this is a useful sanity check on the result."
    )

    # --- Effect size ---
    print("\n" + "-" * 70)
    print("Effect size")
    print("-" * 70)
    absolute_lift = p_treatment - p_control
    relative_lift = (absolute_lift / p_control) * 100 if p_control > 0 else float("nan")
    h = cohens_h(p_control, p_treatment)
    h_magnitude = (
        "negligible" if abs(h) < 0.2 else
        "small" if abs(h) < 0.5 else
        "medium" if abs(h) < 0.8 else
        "large"
    )
    print(f"Absolute lift:  {absolute_lift:+.4f}  ({absolute_lift * 100:+.2f} percentage points)")
    print(f"Relative lift:  {relative_lift:+.2f}%")
    print(f"Cohen's h:      {h:+.4f}  ({h_magnitude} effect)")

    # --- Summary / recommendation ---
    print("\n" + "=" * 70)
    print("SUMMARY")
    print("=" * 70)
    is_significant = p_value_z < ALPHA
    print(f"Significant?      {'YES' if is_significant else 'NO'}  (alpha = {ALPHA}, p = {p_value_z:.6f})")
    print(f"Effect size:      {relative_lift:+.2f}% relative lift, Cohen's h = {h:+.4f} ({h_magnitude})")

    if is_significant and absolute_lift > 0:
        print(
            "Recommendation:  Ship the treatment (new checkout button). The "
            "conversion lift is both statistically significant and directionally "
            "positive - unlikely to be due to random chance."
        )
    elif is_significant and absolute_lift < 0:
        print(
            "Recommendation:  Do NOT ship the treatment. It significantly "
            "DECREASES conversion rate compared to control."
        )
    else:
        print(
            "Recommendation:  Do not ship yet. The observed difference is not "
            "statistically significant - either run the test longer to collect "
            "more data, or conclude there is no meaningful effect."
        )


if __name__ == "__main__":
    main()
