"""
03 - Exploratory Data Analysis & Visualization
=================================================
Companion script for: "EDA/Visualization".

Generates a small synthetic dataset (house sizes vs. prices, with a
handful of injected outliers), computes summary statistics, and produces
three real matplotlib charts saved as PNG files in this same folder:
  1. A histogram (distribution of house prices).
  2. A scatter plot with a fitted linear trend line (size vs. price).
  3. A box plot (outlier detection for price).
"""

from pathlib import Path

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd

OUT_DIR = Path(__file__).parent
SEP = "=" * 70

# A small, consistent, colorblind-friendly palette used across all charts.
INK = "#1a1a1a"          # text / axis lines
MUTED = "#6b6b6b"         # secondary text / grid
PRIMARY = "#2b6cb0"       # main data hue (blue)
ACCENT = "#c05621"        # trend line / highlight (orange, distinct from PRIMARY)
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


# ---------------------------------------------------------------------------
# STEP 1 -- Generate a small synthetic dataset
# ---------------------------------------------------------------------------

section("STEP 1: Generate synthetic 'housing' dataset")

rng = np.random.default_rng(seed=7)
n = 120

size_sqft = rng.normal(loc=1500, scale=350, size=n).clip(500, None)
# Price roughly linear in size plus noise: price ~ 120 * size + noise
price = 120 * size_sqft + rng.normal(loc=0, scale=45000, size=n) + 20000

# Inject a handful of deliberate outliers (e.g. luxury homes / data errors)
outlier_idx = rng.choice(n, size=4, replace=False)
price[outlier_idx] *= rng.uniform(1.8, 2.4, size=4)

df = pd.DataFrame({"size_sqft": size_sqft.round(0),
                    "price": price.round(0)})
print(df.head(10))
print(f"\n... ({len(df)} rows total)")


# ---------------------------------------------------------------------------
# STEP 2 -- Summary statistics
# ---------------------------------------------------------------------------

section("STEP 2: Summary statistics")

print(df.describe().round(2))

q1, q3 = df["price"].quantile([0.25, 0.75])
iqr = q3 - q1
lower_fence = q1 - 1.5 * iqr
upper_fence = q3 + 1.5 * iqr
suspected_outliers = df[(df["price"] < lower_fence) | (df["price"] > upper_fence)]
print(f"\nIQR-based outlier fences for price: [{lower_fence:,.0f}, {upper_fence:,.0f}]")
print(f"Suspected outliers ({len(suspected_outliers)} rows):\n", suspected_outliers)


# ---------------------------------------------------------------------------
# CHART 1 -- Histogram of price distribution
# ---------------------------------------------------------------------------

section("CHART 1: Histogram of house prices -> 03_histogram.png")

fig, ax = plt.subplots(figsize=(7, 4.5))
ax.hist(df["price"], bins=20, color=PRIMARY, edgecolor="white", linewidth=0.8)
ax.set_title("Distribution of House Prices", fontsize=13, fontweight="bold")
ax.set_xlabel("Price ($)")
ax.set_ylabel("Number of houses")
ax.axvline(df["price"].mean(), color=ACCENT, linewidth=2,
           linestyle="--", label=f"Mean = ${df['price'].mean():,.0f}")
ax.legend(frameon=False)
fig.tight_layout()
fig.savefig(OUT_DIR / "03_histogram.png", dpi=150)
plt.close(fig)
print("Saved 03_histogram.png")


# ---------------------------------------------------------------------------
# CHART 2 -- Scatter plot with fitted trend line
# ---------------------------------------------------------------------------

section("CHART 2: Scatter (size vs price) with trend line -> 03_scatter_trend.png")

# Fit a simple linear trend (degree-1 polynomial) for visualization.
slope, intercept = np.polyfit(df["size_sqft"], df["price"], deg=1)
x_line = np.linspace(df["size_sqft"].min(), df["size_sqft"].max(), 100)
y_line = slope * x_line + intercept

fig, ax = plt.subplots(figsize=(7, 4.5))
ax.scatter(df["size_sqft"], df["price"], color=PRIMARY, alpha=0.65,
           s=28, edgecolor="white", linewidth=0.5, label="Houses")
ax.plot(x_line, y_line, color=ACCENT, linewidth=2.5,
        label=f"Trend: price = {slope:,.0f}*size + {intercept:,.0f}")
ax.set_title("House Size vs. Price", fontsize=13, fontweight="bold")
ax.set_xlabel("Size (sqft)")
ax.set_ylabel("Price ($)")
ax.legend(frameon=False, loc="upper left")
fig.tight_layout()
fig.savefig(OUT_DIR / "03_scatter_trend.png", dpi=150)
plt.close(fig)
print("Saved 03_scatter_trend.png")
print(f"Fitted trend: price ≈ {slope:,.1f} * size_sqft + {intercept:,.1f}")


# ---------------------------------------------------------------------------
# CHART 3 -- Box plot for outlier detection
# ---------------------------------------------------------------------------

section("CHART 3: Box plot of price -> 03_boxplot.png")

fig, ax = plt.subplots(figsize=(5, 5))
box = ax.boxplot(df["price"], vert=True, widths=0.4, patch_artist=True,
                  boxprops=dict(facecolor=PRIMARY, alpha=0.35, edgecolor=PRIMARY),
                  medianprops=dict(color=ACCENT, linewidth=2),
                  whiskerprops=dict(color=MUTED),
                  capprops=dict(color=MUTED),
                  flierprops=dict(marker="o", markerfacecolor=ACCENT,
                                   markeredgecolor=ACCENT, markersize=6, alpha=0.8))
ax.set_title("Price Distribution — Outlier Detection", fontsize=13, fontweight="bold")
ax.set_ylabel("Price ($)")
ax.set_xticks([1])
ax.set_xticklabels(["House prices"])
fig.tight_layout()
fig.savefig(OUT_DIR / "03_boxplot.png", dpi=150)
plt.close(fig)
print("Saved 03_boxplot.png")
print(f"Points beyond whiskers (matplotlib fliers) are the same rows flagged "
      f"by the IQR fence check above ({len(suspected_outliers)} rows).")

section("Done. 3 PNG charts written to this folder.")
