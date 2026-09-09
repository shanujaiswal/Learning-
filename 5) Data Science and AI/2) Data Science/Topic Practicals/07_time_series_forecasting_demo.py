"""
07 - Time Series Forecasting Demo
=====================================
Companion script for: "Time Series Forecasting".

Covers:
  - Generating a synthetic daily time series with trend + seasonality + noise.
  - Decomposing it into trend/seasonal/residual components with
    `statsmodels.tsa.seasonal.seasonal_decompose`.
  - Testing stationarity with the Augmented Dickey-Fuller (ADF) test.
  - Fitting a simple ARIMA model and producing a forecast plot.
"""

import warnings
from pathlib import Path

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
from statsmodels.tsa.arima.model import ARIMA
from statsmodels.tsa.seasonal import seasonal_decompose
from statsmodels.tsa.stattools import adfuller

warnings.filterwarnings("ignore")  # silence statsmodels convergence/frequency chatter

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


# ---------------------------------------------------------------------------
# STEP 1 -- Generate a synthetic time series: trend + seasonality + noise
# ---------------------------------------------------------------------------

section("STEP 1: Generate synthetic time series (trend + seasonality + noise)")

rng = np.random.default_rng(seed=5)
n_days = 365 * 2  # two years of daily data
dates = pd.date_range("2024-01-01", periods=n_days, freq="D")

t = np.arange(n_days)
trend = 0.05 * t                                          # slow linear upward trend
seasonality = 10 * np.sin(2 * np.pi * t / 365.25)          # yearly seasonal cycle
weekly = 2 * np.sin(2 * np.pi * t / 7)                     # weekly wiggle
noise = rng.normal(scale=2.5, size=n_days)

values = 50 + trend + seasonality + weekly + noise
series = pd.Series(values, index=dates, name="value")

print(f"Generated {n_days} daily observations from {dates[0].date()} "
      f"to {dates[-1].date()}.")
print(series.head(8))
print(f"\nSeries mean: {series.mean():.2f}, std: {series.std():.2f}")


# ---------------------------------------------------------------------------
# STEP 2 -- Seasonal decomposition
# ---------------------------------------------------------------------------

section("STEP 2: Seasonal decomposition (additive, period=365)")

decomposition = seasonal_decompose(series, model="additive", period=365)

print("Decomposition produced trend, seasonal, and residual components.")
print(f"Trend (first non-NaN values):\n{decomposition.trend.dropna().head(3)}")
print(f"\nSeasonal component (first 5 values):\n"
      f"{decomposition.seasonal.head(5)}")
print(f"\nResidual std (unexplained noise): "
      f"{decomposition.resid.dropna().std():.3f}")

fig = decomposition.plot()
fig.set_size_inches(9, 7)
for ax in fig.axes:
    ax.grid(True, color=GRID, linewidth=0.6)
fig.tight_layout()
fig.savefig(OUT_DIR / "07_decomposition.png", dpi=150)
plt.close(fig)
print("\nSaved 07_decomposition.png")


# ---------------------------------------------------------------------------
# STEP 3 -- Stationarity test: Augmented Dickey-Fuller (ADF)
# ---------------------------------------------------------------------------

section("STEP 3: Augmented Dickey-Fuller (ADF) stationarity test")

# H0: the series has a unit root (i.e. it is NON-stationary).
# H1: the series is stationary.
adf_stat, p_value, used_lag, n_obs, crit_values, _ = adfuller(series)

print(f"ADF statistic = {adf_stat:.4f}")
print(f"p-value       = {p_value:.4f}")
print(f"Used lag      = {used_lag}")
print("Critical values:")
for key, val in crit_values.items():
    print(f"  {key}: {val:.4f}")

alpha = 0.05
if p_value < alpha:
    print(f"\nConclusion: p-value ({p_value:.4f}) < alpha ({alpha}) -> "
          f"REJECT H0. The raw series looks stationary.")
else:
    print(f"\nConclusion: p-value ({p_value:.4f}) >= alpha ({alpha}) -> "
          f"FAIL TO REJECT H0. The raw series is NON-stationary "
          f"(expected, since it has trend + seasonality).")

# Differencing is the classic fix for non-stationarity (also what ARIMA's
# "I" -- integration -- term does internally).
diff_series = series.diff().dropna()
adf_stat_diff, p_value_diff, *_ = adfuller(diff_series)
print(f"\nAfter first-differencing: ADF p-value = {p_value_diff:.6f} "
      f"({'stationary' if p_value_diff < alpha else 'still non-stationary'})")


# ---------------------------------------------------------------------------
# STEP 4 -- Fit ARIMA and forecast
# ---------------------------------------------------------------------------

section("STEP 4: Fit ARIMA(2,1,2) model and forecast 30 days ahead")

# Keep the model small/fast: fit on the series with weekly-frequency numeric
# index handled internally by statsmodels via the DatetimeIndex.
model = ARIMA(series, order=(2, 1, 2))
fitted_model = model.fit()

print(fitted_model.summary().tables[1])  # coefficient table only, keep it short

n_forecast = 30
forecast_result = fitted_model.get_forecast(steps=n_forecast)
forecast_mean = forecast_result.predicted_mean
conf_int = forecast_result.conf_int(alpha=0.05)

print(f"\nForecast for next {n_forecast} days (first 5):")
print(forecast_mean.head(5).round(3))

# ---------------------------------------------------------------------------
# CHART -- History (last 120 days) + forecast with confidence interval
# ---------------------------------------------------------------------------

section("CHART: History + ARIMA forecast -> 07_forecast.png")

history_window = series.iloc[-120:]

fig, ax = plt.subplots(figsize=(10, 5))
ax.plot(history_window.index, history_window.values, color=PRIMARY,
        linewidth=1.5, label="Observed (last 120 days)")
ax.plot(forecast_mean.index, forecast_mean.values, color=ACCENT,
        linewidth=2.2, label=f"ARIMA(2,1,2) forecast ({n_forecast} days)")
ax.fill_between(forecast_mean.index, conf_int.iloc[:, 0], conf_int.iloc[:, 1],
                color=ACCENT, alpha=0.2, label="95% confidence interval")
ax.axvline(series.index[-1], color=MUTED, linestyle="--", linewidth=1)
ax.set_title("Time Series Forecast with ARIMA", fontsize=13, fontweight="bold")
ax.set_xlabel("Date")
ax.set_ylabel("Value")
ax.legend(frameon=False, loc="upper left")
fig.tight_layout()
fig.savefig(OUT_DIR / "07_forecast.png", dpi=150)
plt.close(fig)
print("Saved 07_forecast.png")

section("Done. 2 PNG charts written to this folder (decomposition, forecast).")
