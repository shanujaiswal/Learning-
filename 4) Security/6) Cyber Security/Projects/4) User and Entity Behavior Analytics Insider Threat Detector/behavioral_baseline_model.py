"""
behavioral_baseline_model.py

Computes each user's PERSONAL baseline statistics from their normal-period
history. This is the statistical/rule-based equivalent of what a commercial
UEBA product (e.g. Exabeam, Securonix, Microsoft Defender for Identity) does
under the hood when it builds a per-entity behavioral profile: no trained ML
model, just descriptive statistics (mean/std) plus a "usual set" membership
model, computed independently for every user.

Deliberately kept simple and transparent (baseline + z-score/deviation),
NOT a trained ML model -- this is a classic statistical UEBA approach, not
an AI-integrated one.
"""

from dataclasses import dataclass, field

import numpy as np


# Floor values so a user with an unusually consistent baseline (std ~ 0) does
# not produce a division-by-zero or a hair-trigger z-score later on.
MIN_LOGIN_HOUR_STD = 0.25
MIN_VOLUME_STD = 5.0


@dataclass
class UserBaseline:
    user_id: str
    login_hour_mean: float
    login_hour_std: float
    volume_mean: float
    volume_std: float
    systems_per_day_mean: float
    systems_per_day_std: float
    usual_systems: set = field(default_factory=set)
    n_days_observed: int = 0


def compute_user_baseline(user_id: str, history: list) -> UserBaseline:
    """
    Compute one user's personal baseline from their list of daily history
    records (as produced by user_behavior_baseline_generator.generate_baseline_history).
    """
    login_hours = np.array([r["login_hour"] for r in history])
    volumes = np.array([r["download_mb"] for r in history])
    systems_per_day = np.array([len(r["systems_accessed"]) for r in history])

    usual_systems = set()
    for r in history:
        usual_systems.update(str(s) for s in r["systems_accessed"])

    return UserBaseline(
        user_id=user_id,
        login_hour_mean=float(np.mean(login_hours)),
        login_hour_std=max(float(np.std(login_hours)), MIN_LOGIN_HOUR_STD),
        volume_mean=float(np.mean(volumes)),
        volume_std=max(float(np.std(volumes)), MIN_VOLUME_STD),
        systems_per_day_mean=float(np.mean(systems_per_day)),
        systems_per_day_std=max(float(np.std(systems_per_day)), 0.3),
        usual_systems=usual_systems,
        n_days_observed=len(history),
    )


def compute_all_baselines(history_by_user: dict) -> dict:
    """user_id -> full observation history (list of day records) => user_id -> UserBaseline"""
    return {
        user_id: compute_user_baseline(user_id, records)
        for user_id, records in history_by_user.items()
    }


if __name__ == "__main__":
    from user_behavior_baseline_generator import generate_baseline_history

    history = generate_baseline_history()
    baselines = compute_all_baselines(history)
    for user_id, b in baselines.items():
        print(
            f"{user_id}: login_hour={b.login_hour_mean:.2f}+/-{b.login_hour_std:.2f} | "
            f"volume={b.volume_mean:.1f}+/-{b.volume_std:.1f} MB | "
            f"usual_systems={sorted(b.usual_systems)}"
        )
