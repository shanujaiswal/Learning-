"""
user_behavior_baseline_generator.py

Generates a synthetic "normal observation period" of per-user behavioral
history for a UEBA (User and Entity Behavior Analytics) system.

Each user gets a DISTINCT personal baseline:
    - login-hour distribution (mean/std of the hour-of-day they typically log in)
    - daily download volume distribution (mean/std MB downloaded per day)
    - a "usual systems" set (the pool of systems/files they normally touch)
    - daily distinct-systems-accessed count (how many of that pool they touch per day)

This models the fact that "normal" varies a LOT person to person: a night-shift
admin logs in at 2am every day as a matter of routine, while a 9-5 analyst
logging in at 2am would be a glaring anomaly. The baseline generator bakes in
that person-to-person variety on purpose, fixed seed for reproducibility.
"""

import numpy as np

RANDOM_SEED = 42
OBSERVATION_DAYS = 60  # length of the "normal" baseline observation window

# All systems/file-shares that exist in the organization. Each user's
# role-based access only ever touches a subset of these during the baseline
# period ("usual systems"); anything outside that subset during scoring is a
# potential access-pattern anomaly.
ALL_SYSTEMS = [
    "hr_portal", "payroll_db", "customer_crm", "finance_ledger",
    "source_code_repo", "engineering_wiki", "sales_pipeline_db",
    "legal_contracts_share", "exec_board_docs", "network_admin_console",
    "backup_server", "email_archive", "vpn_gateway_logs", "shared_drive_marketing",
    "product_roadmap_docs",
]

# Each user profile: (mean login hour, std login hour, mean MB/day, std MB/day,
# usual systems pool, mean distinct systems/day, std distinct systems/day)
USER_PROFILES = {
    # Standard 9-5 analyst: tight login window, modest downloads, small system set.
    "alice_finance": dict(
        login_hour_mean=9.5, login_hour_std=0.8,
        volume_mean=120.0, volume_std=25.0,
        usual_systems=["finance_ledger", "hr_portal", "email_archive"],
        systems_per_day_mean=2.0, systems_per_day_std=0.6,
    ),
    # Strict 9-5 employee about to resign (data-exfil scenario target).
    "bob_sales": dict(
        login_hour_mean=9.0, login_hour_std=0.6,
        volume_mean=90.0, volume_std=18.0,
        usual_systems=["sales_pipeline_db", "customer_crm", "email_archive"],
        systems_per_day_mean=2.2, systems_per_day_std=0.5,
    ),
    # Night-shift sysadmin: genuinely, routinely logs in around 2-3am.
    # This is the "must NOT be flagged" personalization proof case.
    "carla_nightadmin": dict(
        login_hour_mean=2.5, login_hour_std=1.0,
        volume_mean=200.0, volume_std=40.0,
        usual_systems=["network_admin_console", "backup_server", "vpn_gateway_logs"],
        systems_per_day_mean=3.0, systems_per_day_std=0.7,
    ),
    # Regular engineer, moderate hours, moderate downloads, tight system set
    # (access-pattern-deviation scenario target).
    "dave_engineering": dict(
        login_hour_mean=10.0, login_hour_std=1.2,
        volume_mean=150.0, volume_std=30.0,
        usual_systems=["source_code_repo", "engineering_wiki", "product_roadmap_docs"],
        systems_per_day_mean=2.5, systems_per_day_std=0.6,
    ),
    # Another ordinary 9-5 baseline user (control/comparison; never targeted
    # by an injected scenario) to prove the detector doesn't cry wolf on
    # everyone once thresholds are personalized.
    "erin_hr": dict(
        login_hour_mean=8.7, login_hour_std=0.5,
        volume_mean=70.0, volume_std=15.0,
        usual_systems=["hr_portal", "payroll_db", "email_archive"],
        systems_per_day_mean=1.8, systems_per_day_std=0.4,
    ),
}


def generate_baseline_history(seed: int = RANDOM_SEED, n_days: int = OBSERVATION_DAYS):
    """
    Build n_days of normal behavioral history per user.

    Returns
    -------
    dict[str, list[dict]]
        user_id -> list of daily records:
            {"day": int, "login_hour": float, "download_mb": float,
             "systems_accessed": list[str]}
    """
    rng = np.random.default_rng(seed)
    history = {}

    for user_id, profile in USER_PROFILES.items():
        records = []
        usual_systems = profile["usual_systems"]

        for day in range(n_days):
            # Login hour: wrap into [0, 24) so a night-shift mean near 2am
            # doesn't produce nonsensical negative hours.
            login_hour = rng.normal(profile["login_hour_mean"], profile["login_hour_std"])
            login_hour = float(np.clip(login_hour % 24, 0, 23.99))

            # Download volume in MB, floored at a small positive amount.
            download_mb = rng.normal(profile["volume_mean"], profile["volume_std"])
            download_mb = float(max(5.0, download_mb))

            # How many distinct systems from their usual pool they touch today.
            n_systems = rng.normal(profile["systems_per_day_mean"], profile["systems_per_day_std"])
            n_systems = int(np.clip(round(n_systems), 1, len(usual_systems)))
            systems_today = list(rng.choice(usual_systems, size=n_systems, replace=False))

            records.append({
                "day": day,
                "login_hour": round(login_hour, 2),
                "download_mb": round(download_mb, 2),
                "systems_accessed": systems_today,
            })

        history[user_id] = records

    return history


if __name__ == "__main__":
    history = generate_baseline_history()
    for user_id, records in history.items():
        hours = [r["login_hour"] for r in records]
        vols = [r["download_mb"] for r in records]
        print(f"{user_id}: {len(records)} days | "
              f"login_hour mean={np.mean(hours):.2f} std={np.std(hours):.2f} | "
              f"volume mean={np.mean(vols):.2f} std={np.std(vols):.2f}")
