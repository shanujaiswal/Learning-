"""
ueba_detector.py

Scores a single NEW day of a user's activity against THEIR OWN personal
baseline (produced by behavioral_baseline_model.compute_user_baseline) and
flags statistically significant PERSONALIZED deviations.

Three independent signals are checked, each against the user's own
distribution -- never a global, one-size-fits-all rule:

    1. Login-hour deviation   -- circular z-score (handles hour wraparound,
                                   e.g. 23:50 and 00:10 are 20 minutes apart,
                                   not ~24 hours apart) against the user's own
                                   login_hour_mean/std.
    2. Volume deviation       -- z-score of download_mb against the user's own
                                   volume_mean/std.
    3. Access-pattern deviation -- set difference between systems touched
                                   today and the user's own "usual_systems"
                                   pool learned during the baseline period.

A day is flagged if ANY signal crosses its personalized threshold. Because
every threshold is evaluated against that user's own mean/std (or own usual-
systems set), a night-shift admin's routine 2:30am login produces a login-hour
z-score near 0 -- while that same 2:30am login would be a huge z-score for a
strict 9-5 analyst. This is the core proof of "personalized", not "generic".
"""

from dataclasses import dataclass, field

import numpy as np

from behavioral_baseline_model import UserBaseline

# Personalized anomaly threshold: how many of *this user's own* standard
# deviations away counts as statistically significant. 3.0 sigma is the
# classic "this basically never happens by chance" statistical cutoff.
Z_THRESHOLD = 3.0


@dataclass
class ScoreResult:
    user_id: str
    day: int
    login_hour_z: float
    volume_z: float
    unexpected_systems: set = field(default_factory=set)
    flags: list = field(default_factory=list)
    label: str = ""
    scenario: str = ""

    @property
    def is_flagged(self) -> bool:
        return len(self.flags) > 0


def _circular_hour_distance(hour: float, mean_hour: float) -> float:
    """
    Smallest distance (in hours) between `hour` and `mean_hour` on a 24-hour
    clock, so an admin whose mean login hour is 2.5am and who logs in at
    23:50 (which is "close" to 2.5am going the other way around midnight)
    isn't scored as if they were 21+ hours off from their own normal.
    """
    diff = abs(hour - mean_hour) % 24
    return min(diff, 24 - diff)


def score_day(baseline: UserBaseline, day_record: dict) -> ScoreResult:
    """
    Score one new day of activity for a user against THEIR OWN baseline.

    Parameters
    ----------
    baseline : UserBaseline
        This user's personal baseline (own mean/std/usual-systems -- never
        another user's, and never a global/organization-wide statistic).
    day_record : dict
        {"day", "login_hour", "download_mb", "systems_accessed", ...}
        (extra keys such as "scenario"/"label" are carried through if present)

    Returns
    -------
    ScoreResult
    """
    login_hour = day_record["login_hour"]
    download_mb = day_record["download_mb"]
    systems_today = set(day_record.get("systems_accessed", []))

    # --- Signal 1: login-hour deviation (personalized, circular) ---
    hour_dist = _circular_hour_distance(login_hour, baseline.login_hour_mean)
    login_hour_z = hour_dist / baseline.login_hour_std

    # --- Signal 2: download-volume deviation (personalized) ---
    volume_z = (download_mb - baseline.volume_mean) / baseline.volume_std

    # --- Signal 3: access-pattern deviation (personalized set-difference) ---
    unexpected_systems = systems_today - baseline.usual_systems

    flags = []
    if login_hour_z > Z_THRESHOLD:
        flags.append(
            f"OFF-HOURS LOGIN: logged in at {login_hour:.2f}h, "
            f"{hour_dist:.2f}h from personal mean {baseline.login_hour_mean:.2f}h "
            f"(z={login_hour_z:.2f}, personal std={baseline.login_hour_std:.2f}h)"
        )
    if volume_z > Z_THRESHOLD:
        flags.append(
            f"VOLUME SPIKE: downloaded {download_mb:.1f} MB vs personal mean "
            f"{baseline.volume_mean:.1f} MB (z={volume_z:.2f}, "
            f"personal std={baseline.volume_std:.1f} MB)"
        )
    if unexpected_systems:
        flags.append(
            f"ACCESS-PATTERN DEVIATION: touched system(s) never seen in this "
            f"user's baseline -> {sorted(unexpected_systems)} "
            f"(usual pool={sorted(baseline.usual_systems)})"
        )

    return ScoreResult(
        user_id=baseline.user_id,
        day=day_record["day"],
        login_hour_z=login_hour_z,
        volume_z=volume_z,
        unexpected_systems=unexpected_systems,
        flags=flags,
        label=day_record.get("label", ""),
        scenario=day_record.get("scenario", ""),
    )


def score_all_users(baselines: dict, scenario_days: dict) -> dict:
    """user_id -> ScoreResult, scoring each user's own scenario day against
    their own baseline only."""
    return {
        user_id: score_day(baselines[user_id], day_record)
        for user_id, day_record in scenario_days.items()
    }


if __name__ == "__main__":
    from user_behavior_baseline_generator import generate_baseline_history
    from behavioral_baseline_model import compute_all_baselines
    from anomaly_scenarios import build_scenario_days

    history = generate_baseline_history()
    baselines = compute_all_baselines(history)
    scenarios = build_scenario_days()

    results = score_all_users(baselines, scenarios)
    for user_id, result in results.items():
        status = "FLAGGED" if result.is_flagged else "not flagged"
        print(f"\n{user_id} [{status}] -- {result.label}")
        for f in result.flags:
            print(f"    - {f}")
