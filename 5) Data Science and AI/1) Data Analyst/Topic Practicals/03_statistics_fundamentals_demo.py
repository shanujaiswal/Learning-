"""
03_statistics_fundamentals_demo.py

Demonstrates "Statistics Fundamentals for Analysts" using the sample sales
dataset:

    1. Central tendency & spread computed BY HAND, then cross-checked with
       numpy/scipy (mean, median, mode, variance, std).
    2. A real correlation between two columns (units vs revenue).
    3. A one-sample t-test (is average revenue per order different from a
       target/benchmark value?).
    4. A two-sample t-test (is average revenue different between two regions?).

Each test prints a plain-English interpretation of the p-value, not just the
number.

Run:
    python 03_statistics_fundamentals_demo.py
"""

from pathlib import Path
from statistics import mode

import numpy as np
import pandas as pd
from scipy import stats

HERE = Path(__file__).resolve().parent
CSV_PATH = HERE / "01_generate_sample_sales_data.csv"
ALPHA = 0.05  # standard significance threshold


def manual_mean(values: np.ndarray) -> float:
    return sum(values) / len(values)


def manual_variance(values: np.ndarray, sample: bool = True) -> float:
    n = len(values)
    m = manual_mean(values)
    ss = sum((x - m) ** 2 for x in values)
    denom = (n - 1) if sample else n
    return ss / denom


def manual_std(values: np.ndarray, sample: bool = True) -> float:
    return manual_variance(values, sample) ** 0.5


def manual_median(values: np.ndarray) -> float:
    s = sorted(values)
    n = len(s)
    mid = n // 2
    if n % 2 == 0:
        return (s[mid - 1] + s[mid]) / 2
    return s[mid]


def section_1_descriptive_stats(df: pd.DataFrame) -> None:
    print("=" * 70)
    print("SECTION 1: Descriptive statistics - manual vs numpy/scipy")
    print("=" * 70)

    revenue = df["revenue"].to_numpy()

    manual = {
        "mean": manual_mean(revenue),
        "median": manual_median(revenue),
        "variance (sample)": manual_variance(revenue, sample=True),
        "std (sample)": manual_std(revenue, sample=True),
    }
    library = {
        "mean": np.mean(revenue),
        "median": np.median(revenue),
        "variance (sample)": np.var(revenue, ddof=1),
        "std (sample)": np.std(revenue, ddof=1),
    }

    comparison = pd.DataFrame({"manual_calc": manual, "numpy_calc": library}).round(4)
    print(comparison)

    # Mode is easiest to compute on a rounded / binned or categorical field
    rounded_units = df["units"].dropna().round().astype(int)
    try:
        units_mode = mode(rounded_units)
    except Exception:
        units_mode = rounded_units.value_counts().idxmax()
    print(f"\nMode of 'units' (most frequent order size): {units_mode}")
    print("(Mode is most meaningful for discrete/categorical data like 'units', "
          "less so for continuous revenue figures with few exact repeats.)")


def section_2_correlation(df: pd.DataFrame) -> None:
    print("\n" + "=" * 70)
    print("SECTION 2: Correlation between units and revenue")
    print("=" * 70)

    clean = df.dropna(subset=["units", "revenue"])
    r, p_value = stats.pearsonr(clean["units"], clean["revenue"])

    print(f"Pearson correlation coefficient (r): {r:.4f}")
    print(f"p-value for the correlation: {p_value:.6f}")

    strength = (
        "very weak" if abs(r) < 0.2 else
        "weak" if abs(r) < 0.4 else
        "moderate" if abs(r) < 0.6 else
        "strong" if abs(r) < 0.8 else
        "very strong"
    )
    direction = "positive" if r > 0 else "negative"
    print(
        f"\nInterpretation: units and revenue have a {strength} {direction} "
        f"correlation (r={r:.2f}). This makes intuitive sense - selling more "
        f"units tends to produce more revenue, but price differences across "
        f"products/regions add noise, keeping the correlation from being "
        f"perfect (r=1.0)."
    )
    if p_value < ALPHA:
        print(f"Since p={p_value:.6f} < {ALPHA}, this correlation is unlikely "
              f"to be due to random chance alone.")
    else:
        print(f"Since p={p_value:.6f} >= {ALPHA}, we cannot rule out that this "
              f"correlation arose by chance.")


def section_3_one_sample_ttest(df: pd.DataFrame) -> None:
    print("\n" + "=" * 70)
    print("SECTION 3: One-sample t-test")
    print("=" * 70)

    benchmark = 400.0  # e.g. a company target for average revenue per order
    revenue = df["revenue"].to_numpy()

    t_stat, p_value = stats.ttest_1samp(revenue, popmean=benchmark)
    sample_mean = revenue.mean()

    print(f"Question: Is the average order revenue different from the "
          f"company benchmark of ${benchmark:.2f}?")
    print(f"Sample mean: ${sample_mean:.2f}  |  Benchmark: ${benchmark:.2f}")
    print(f"t-statistic: {t_stat:.4f}  |  p-value: {p_value:.6f}")

    if p_value < ALPHA:
        direction = "higher" if sample_mean > benchmark else "lower"
        print(
            f"\nInterpretation: p={p_value:.6f} < {ALPHA}, so the difference "
            f"is statistically significant. Average revenue per order is "
            f"significantly {direction} than the ${benchmark:.2f} benchmark - "
            f"this is unlikely to be random noise."
        )
    else:
        print(
            f"\nInterpretation: p={p_value:.6f} >= {ALPHA}, so we do NOT have "
            f"enough evidence to say average revenue differs from the "
            f"${benchmark:.2f} benchmark. The observed difference could "
            f"plausibly be due to random sampling variation."
        )


def section_4_two_sample_ttest(df: pd.DataFrame) -> None:
    print("\n" + "=" * 70)
    print("SECTION 4: Two-sample (independent) t-test")
    print("=" * 70)

    region_a, region_b = "North", "South"
    a = df.loc[df["region"] == region_a, "revenue"].to_numpy()
    b = df.loc[df["region"] == region_b, "revenue"].to_numpy()

    # Welch's t-test (equal_var=False) is the safer default: it does not
    # assume the two regions have identical variance.
    t_stat, p_value = stats.ttest_ind(a, b, equal_var=False)

    print(f"Question: Is average order revenue different between "
          f"{region_a} (n={len(a)}) and {region_b} (n={len(b)})?")
    print(f"{region_a} mean: ${a.mean():.2f}  |  {region_b} mean: ${b.mean():.2f}")
    print(f"t-statistic: {t_stat:.4f}  |  p-value: {p_value:.6f}")

    if p_value < ALPHA:
        higher = region_a if a.mean() > b.mean() else region_b
        print(
            f"\nInterpretation: p={p_value:.6f} < {ALPHA}, so the difference "
            f"in average revenue between {region_a} and {region_b} is "
            f"statistically significant. {higher} has the higher average, "
            f"and this gap is unlikely to be explained by chance alone."
        )
    else:
        print(
            f"\nInterpretation: p={p_value:.6f} >= {ALPHA}, so we do NOT have "
            f"enough evidence to say average revenue differs between "
            f"{region_a} and {region_b}. Any observed gap could plausibly be "
            f"random sampling variation rather than a true regional effect."
        )


def main() -> None:
    df = pd.read_csv(CSV_PATH, parse_dates=["date"])
    section_1_descriptive_stats(df)
    section_2_correlation(df)
    section_3_one_sample_ttest(df)
    section_4_two_sample_ttest(df)


if __name__ == "__main__":
    main()
