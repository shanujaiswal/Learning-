"""
main.py

End-to-end run of the statistical/rule-based UEBA insider-threat detector:

    1. Generate each user's 60-day "normal" baseline history
       (user_behavior_baseline_generator.py).
    2. Compute each user's PERSONAL baseline statistics
       (behavioral_baseline_model.py) -- mean/std of login hour, download
       volume, and their "usual systems" set. No trained ML model anywhere,
       just descriptive stats computed independently per user.
    3. Score the injected "new day" scenarios
       (anomaly_scenarios.py) against each user's OWN baseline
       (ueba_detector.py) -- never a global/organization-wide rule.
    4. Print each user's flags (or correct non-flagging of the night-shift
       control case) and an overall summary.
    5. Save a matplotlib PNG showing each flagged user's login-hour and
       download-volume deviation plotted against their own personal baseline
       distribution -- visual proof the threshold is personalized per user.
"""

import numpy as np
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

from user_behavior_baseline_generator import generate_baseline_history
from behavioral_baseline_model import compute_all_baselines
from anomaly_scenarios import build_scenario_days
from ueba_detector import score_all_users, Z_THRESHOLD

OUTPUT_PNG = "ueba_result.png"


def print_report(results: dict):
    print("=" * 78)
    print("UEBA INSIDER-THREAT DETECTOR -- PERSONALIZED BASELINE SCORING RESULTS")
    print("=" * 78)

    n_flagged = 0
    for user_id, r in results.items():
        if r.is_flagged:
            n_flagged += 1
            print(f"\n[FLAGGED]     {user_id}")
            print(f"              scenario: {r.scenario}")
            print(f"              {r.label}")
            for f in r.flags:
                print(f"              - {f}")
        else:
            marker = "[NOT FLAGGED - correct]" if r.scenario == "normal_nightshift_activity_control_case" \
                else "[not flagged]"
            print(f"\n{marker:14s} {user_id}")
            print(f"              scenario: {r.scenario}")
            print(f"              {r.label}")
            print(f"              login_hour_z={r.login_hour_z:.2f}, "
                  f"volume_z={r.volume_z:.2f}, unexpected_systems={sorted(r.unexpected_systems) or 'none'}"
                  f"  (threshold z={Z_THRESHOLD})")

    print("\n" + "-" * 78)
    print("SUMMARY")
    print("-" * 78)
    print(f"Users scored: {len(results)}")
    print(f"Flagged as anomalous: {n_flagged}")
    control = results.get("carla_nightadmin")
    if control is not None:
        verdict = "CORRECTLY NOT FLAGGED" if not control.is_flagged else "INCORRECTLY FLAGGED (bug!)"
        print(f"Night-shift control case (carla_nightadmin): {verdict} "
              f"(login_hour_z={control.login_hour_z:.2f} vs threshold {Z_THRESHOLD}) "
              "-- proves personalization, not a blanket off-hours rule.")
    print("=" * 78)


def plot_results(baselines: dict, results: dict, history: dict, out_path: str = OUTPUT_PNG):
    """
    For each FLAGGED user, plot their own baseline login-hour and
    download-volume distributions (from their 60-day normal history) as
    histograms, with a vertical line marking where their scenario day landed.
    This visually proves the flag is a deviation from THEIR OWN normal, not
    an absolute/global cutoff.
    """
    flagged_users = [u for u, r in results.items() if r.is_flagged]
    # Always include the night-shift control case too, to visually contrast
    # "same off-hours login, but NOT flagged because it matches her own norm".
    users_to_plot = flagged_users + (
        ["carla_nightadmin"] if "carla_nightadmin" not in flagged_users else []
    )

    n = len(users_to_plot)
    fig, axes = plt.subplots(n, 2, figsize=(11, 3.6 * n))
    if n == 1:
        axes = axes.reshape(1, 2)

    for row, user_id in enumerate(users_to_plot):
        baseline = baselines[user_id]
        result = results[user_id]
        hist = history[user_id]
        scenario_hour = None
        scenario_vol = None
        for uid, day in build_scenario_days().items():
            if uid == user_id:
                scenario_hour = day["login_hour"]
                scenario_vol = day["download_mb"]

        hours = [r["login_hour"] for r in hist]
        vols = [r["download_mb"] for r in hist]

        flagged = result.is_flagged
        color = "crimson" if flagged else "seagreen"
        tag = "FLAGGED" if flagged else "NOT FLAGGED (correct)"

        # --- login hour subplot ---
        ax = axes[row, 0]
        ax.hist(hours, bins=15, color="steelblue", alpha=0.7, label="60-day baseline")
        ax.axvline(scenario_hour, color=color, linewidth=2.5,
                   label=f"scenario day ({scenario_hour:.2f}h)")
        ax.set_title(f"{user_id} -- login hour [{tag}]\n"
                     f"z={result.login_hour_z:.2f} (own mean={baseline.login_hour_mean:.2f}h, "
                     f"own std={baseline.login_hour_std:.2f}h)")
        ax.set_xlabel("login hour of day")
        ax.set_ylabel("days in baseline")
        ax.legend(fontsize=8)

        # --- volume subplot ---
        ax2 = axes[row, 1]
        ax2.hist(vols, bins=15, color="darkorange", alpha=0.7, label="60-day baseline")
        ax2.axvline(scenario_vol, color=color, linewidth=2.5,
                    label=f"scenario day ({scenario_vol:.1f} MB)")
        ax2.set_title(f"{user_id} -- download volume [{tag}]\n"
                      f"z={result.volume_z:.2f} (own mean={baseline.volume_mean:.1f} MB, "
                      f"own std={baseline.volume_std:.1f} MB)")
        ax2.set_xlabel("MB downloaded / day")
        ax2.set_ylabel("days in baseline")
        ax2.legend(fontsize=8)

    fig.suptitle("UEBA: Each User's Own Baseline vs. Their Scenario Day\n"
                 "(personalized thresholds -- not a one-size-fits-all rule)",
                 fontsize=13, y=1.0)
    fig.tight_layout()
    fig.savefig(out_path, dpi=130, bbox_inches="tight")
    print(f"\nSaved plot: {out_path}")


def main():
    history = generate_baseline_history()
    baselines = compute_all_baselines(history)
    scenarios = build_scenario_days()
    results = score_all_users(baselines, scenarios)

    print_report(results)
    plot_results(baselines, results, history)


if __name__ == "__main__":
    main()
