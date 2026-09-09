"""
03 - Model Monitoring / Data Drift Demo
==========================================
Chapter: MLOps Fundamentals / Model Deployment (post-deployment monitoring)

Once a model is deployed (see 02_fastapi_model_serving.py), the world can
keep changing while the model stays frozen -- this is "data drift" /
"concept drift". This script simulates production data for a feature that
gradually drifts away from its training distribution over a number of
"days", then measures the drift each day with two common statistical
techniques:

    1. Population Stability Index (PSI) -- a classic MLOps drift metric.
    2. Kolmogorov-Smirnov (KS) test -- via scipy.stats.ks_2samp.

An alert is printed once the drift metric crosses a chosen threshold,
mimicking what a monitoring dashboard / alerting pipeline would do.

Install:
    pip install numpy scipy pandas

Run:
    python 03_model_monitoring_drift_demo.py
"""

import numpy as np
import pandas as pd
from scipy import stats

RANDOM_SEED = 42
N_DAYS = 14
SAMPLES_PER_DAY = 500

# Training-time ("reference") distribution for the monitored feature.
TRAIN_MEAN = 50.0
TRAIN_STD = 10.0

# Drift thresholds commonly used in practice:
#   PSI < 0.1        -> no significant shift
#   0.1 <= PSI < 0.25 -> moderate shift, worth watching
#   PSI >= 0.25       -> significant shift, investigate/retrain
PSI_ALERT_THRESHOLD = 0.25
KS_PVALUE_ALERT_THRESHOLD = 0.01  # p-value below this => distributions differ significantly


def generate_reference_sample(rng, n=5000):
    """The distribution the model was trained on."""
    return rng.normal(loc=TRAIN_MEAN, scale=TRAIN_STD, size=n)


def generate_daily_production_sample(rng, day_index: int, n=SAMPLES_PER_DAY):
    """Simulate production traffic that gradually drifts over time.

    Both the mean and the variance creep away from the training
    distribution as `day_index` increases, simulating a slow real-world
    shift (e.g. a new user segment, a seasonal effect, an upstream data
    pipeline change).
    """
    drift_in_mean = day_index * 1.5       # mean creeps upward each day
    drift_in_std = day_index * 0.4        # spread widens each day
    mean = TRAIN_MEAN + drift_in_mean
    std = TRAIN_STD + drift_in_std
    return rng.normal(loc=mean, scale=std, size=n)


def compute_psi(reference: np.ndarray, current: np.ndarray, n_bins: int = 10) -> float:
    """Population Stability Index between a reference and current sample.

    Bins are defined on the reference distribution's quantiles so each
    reference bin starts with roughly equal weight; then we compare how
    the current sample's mass is distributed across those same bins.
    """
    bin_edges = np.quantile(reference, np.linspace(0, 1, n_bins + 1))
    bin_edges[0] = -np.inf
    bin_edges[-1] = np.inf

    ref_counts, _ = np.histogram(reference, bins=bin_edges)
    cur_counts, _ = np.histogram(current, bins=bin_edges)

    ref_pct = np.clip(ref_counts / len(reference), 1e-6, None)
    cur_pct = np.clip(cur_counts / len(current), 1e-6, None)

    psi = np.sum((cur_pct - ref_pct) * np.log(cur_pct / ref_pct))
    return float(psi)


def main():
    rng = np.random.default_rng(RANDOM_SEED)
    reference_sample = generate_reference_sample(rng)

    print(f"Reference (training) distribution: mean={TRAIN_MEAN}, std={TRAIN_STD}")
    print(f"Monitoring {N_DAYS} days of simulated production traffic...\n")

    results = []
    alert_fired = False

    for day in range(1, N_DAYS + 1):
        daily_sample = generate_daily_production_sample(rng, day)

        psi = compute_psi(reference_sample, daily_sample)
        ks_stat, ks_pvalue = stats.ks_2samp(reference_sample, daily_sample)

        drift_flagged = psi >= PSI_ALERT_THRESHOLD or ks_pvalue < KS_PVALUE_ALERT_THRESHOLD

        results.append(
            {
                "day": day,
                "sample_mean": float(np.mean(daily_sample)),
                "sample_std": float(np.std(daily_sample)),
                "psi": round(psi, 4),
                "ks_statistic": round(float(ks_stat), 4),
                "ks_pvalue": round(float(ks_pvalue), 6),
                "drift_flagged": drift_flagged,
            }
        )

        status = "OK"
        if drift_flagged:
            status = "*** DRIFT ALERT ***"
            if not alert_fired:
                print(
                    f"\n>>> First drift alert triggered on day {day}: "
                    f"PSI={psi:.4f}, KS p-value={ks_pvalue:.6f}\n"
                    f">>> Recommended action: investigate feature pipeline, "
                    f"consider retraining or recalibrating the model.\n"
                )
                alert_fired = True

        print(
            f"Day {day:>2}: mean={np.mean(daily_sample):6.2f} std={np.std(daily_sample):5.2f} "
            f"PSI={psi:6.4f} KS_stat={ks_stat:.4f} KS_pvalue={ks_pvalue:.6f}  [{status}]"
        )

    df = pd.DataFrame(results)
    print("\nFull drift-monitoring report:")
    print(df.to_string(index=False))


if __name__ == "__main__":
    main()
