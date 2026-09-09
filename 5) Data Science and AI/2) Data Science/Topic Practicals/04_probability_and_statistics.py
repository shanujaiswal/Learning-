"""
04 - Probability & Statistics for Data Science
=================================================
Companion script for: "Probability/Statistics for DS".

Covers:
  - Simulating a binomial distribution (discrete) with NumPy and comparing
    the empirical frequencies against the theoretical PMF.
  - Simulating a normal distribution (continuous) with NumPy.
  - Visualizing both distributions in a single saved PNG.
  - Running a real two-sample t-test with `scipy.stats` to check whether
    two groups (e.g. control vs. treatment) differ significantly, reporting
    the p-value and a plain-English conclusion.
"""

from pathlib import Path

import matplotlib.pyplot as plt
import numpy as np
from scipy import stats

OUT_DIR = Path(__file__).parent
SEP = "=" * 70

INK = "#1a1a1a"
MUTED = "#6b6b6b"
PRIMARY = "#2b6cb0"
ACCENT = "#c05621"
GRID = "#d9d9d9"


def section(title: str) -> None:
    print(f"\n{SEP}\n{title}\n{SEP}")


plt.rcParams.update({
    "figure.facecolor": "white",
    "axes.facecolor": "white",
    "axes.edgecolor": MUTED,
    "axes.labelcolor": INK,
    "text.color": INK,
    "xtick.color": INK,
    "ytick.color": INK,
    "axes.grid": True,
    "grid.color": GRID,
    "grid.linewidth": 0.6,
    "axes.spines.top": False,
    "axes.spines.right": False,
    "font.size": 11,
})

rng = np.random.default_rng(seed=11)


# ---------------------------------------------------------------------------
# PART 1 -- Binomial distribution: simulate coin flips
# ---------------------------------------------------------------------------

section("PART 1: Binomial distribution (simulated coin flips)")

n_trials = 20        # flips per experiment
p_heads = 0.5         # fair coin
n_experiments = 10_000

binom_samples = rng.binomial(n=n_trials, p=p_heads, size=n_experiments)
print(f"Simulated {n_experiments} experiments of {n_trials} flips each "
      f"(p={p_heads}).")
print(f"Empirical mean heads: {binom_samples.mean():.3f} "
      f"(theoretical mean = n*p = {n_trials * p_heads:.3f})")
print(f"Empirical variance: {binom_samples.var():.3f} "
      f"(theoretical variance = n*p*(1-p) = "
      f"{n_trials * p_heads * (1 - p_heads):.3f})")

# Empirical PMF (normalized histogram) vs. theoretical PMF
values, counts = np.unique(binom_samples, return_counts=True)
empirical_pmf = counts / n_experiments
theoretical_pmf = stats.binom.pmf(values, n_trials, p_heads)
print("\nk | empirical P(k) | theoretical P(k)")
for k, ep, tp in zip(values, empirical_pmf, theoretical_pmf):
    print(f"{k:2d} | {ep:.4f}          | {tp:.4f}")


# ---------------------------------------------------------------------------
# PART 2 -- Normal distribution: simulate a continuous measurement
# ---------------------------------------------------------------------------

section("PART 2: Normal distribution (simulated continuous measurement)")

mu, sigma = 68.0, 3.0   # e.g. adult height in inches
normal_samples = rng.normal(loc=mu, scale=sigma, size=n_experiments)
print(f"Simulated {n_experiments} draws from N(mu={mu}, sigma={sigma}).")
print(f"Sample mean: {normal_samples.mean():.3f}, "
      f"sample std: {normal_samples.std(ddof=1):.3f}")

# Empirical rule check (68-95-99.7)
within_1sigma = np.mean(np.abs(normal_samples - mu) <= sigma)
within_2sigma = np.mean(np.abs(normal_samples - mu) <= 2 * sigma)
within_3sigma = np.mean(np.abs(normal_samples - mu) <= 3 * sigma)
print(f"Fraction within 1 sigma: {within_1sigma:.3f} (expected ~0.683)")
print(f"Fraction within 2 sigma: {within_2sigma:.3f} (expected ~0.954)")
print(f"Fraction within 3 sigma: {within_3sigma:.3f} (expected ~0.997)")


# ---------------------------------------------------------------------------
# CHART -- Both distributions side by side
# ---------------------------------------------------------------------------

section("CHART: Binomial + Normal distributions -> 04_distributions.png")

fig, axes = plt.subplots(1, 2, figsize=(11, 4.5))

ax = axes[0]
ax.bar(values, empirical_pmf, color=PRIMARY, alpha=0.75, label="Empirical")
ax.plot(values, theoretical_pmf, "o-", color=ACCENT, linewidth=2,
        label="Theoretical PMF")
ax.set_title(f"Binomial(n={n_trials}, p={p_heads})", fontsize=12,
             fontweight="bold")
ax.set_xlabel("Number of heads (k)")
ax.set_ylabel("P(X = k)")
ax.legend(frameon=False)

ax = axes[1]
ax.hist(normal_samples, bins=40, density=True, color=PRIMARY, alpha=0.75,
        edgecolor="white", linewidth=0.5, label="Simulated samples")
x_line = np.linspace(normal_samples.min(), normal_samples.max(), 200)
ax.plot(x_line, stats.norm.pdf(x_line, mu, sigma), color=ACCENT, linewidth=2.5,
        label="Theoretical PDF")
ax.set_title(f"Normal(mu={mu}, sigma={sigma})", fontsize=12, fontweight="bold")
ax.set_xlabel("Value")
ax.set_ylabel("Density")
ax.legend(frameon=False)

fig.tight_layout()
fig.savefig(OUT_DIR / "04_distributions.png", dpi=150)
plt.close(fig)
print("Saved 04_distributions.png")


# ---------------------------------------------------------------------------
# PART 3 -- Real hypothesis test: two-sample t-test
# ---------------------------------------------------------------------------

section("PART 3: Hypothesis test — independent two-sample t-test")

# Scenario: does a new "treatment" (e.g. a website design) increase average
# time-on-page compared to a "control" group?
control = rng.normal(loc=42.0, scale=8.0, size=60)      # seconds
treatment = rng.normal(loc=46.5, scale=8.0, size=60)     # seconds, slightly higher

print(f"Control group   : n={len(control)}, mean={control.mean():.2f}, "
      f"std={control.std(ddof=1):.2f}")
print(f"Treatment group : n={len(treatment)}, mean={treatment.mean():.2f}, "
      f"std={treatment.std(ddof=1):.2f}")

# H0: mean(control) == mean(treatment)
# H1: mean(control) != mean(treatment)
alpha = 0.05
t_stat, p_value = stats.ttest_ind(control, treatment, equal_var=True)

print(f"\nH0: no difference in mean time-on-page between groups")
print(f"H1: there IS a difference in mean time-on-page")
print(f"t-statistic = {t_stat:.4f}")
print(f"p-value     = {p_value:.4f}")
print(f"alpha       = {alpha}")

if p_value < alpha:
    print(f"\nConclusion: p-value ({p_value:.4f}) < alpha ({alpha}) -> "
          f"REJECT H0. The difference in means is statistically significant.")
else:
    print(f"\nConclusion: p-value ({p_value:.4f}) >= alpha ({alpha}) -> "
          f"FAIL TO REJECT H0. No statistically significant difference found.")

section("Done. 1 PNG chart written to this folder; t-test result printed above.")
