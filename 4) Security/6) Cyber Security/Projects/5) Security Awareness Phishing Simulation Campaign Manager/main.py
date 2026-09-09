"""
main.py

Orchestrates the full simulated phishing-awareness exercise:

  1. Build the synthetic employee roster.
  2. Run Campaign #1 (an easier-to-catch lure) against the whole roster.
  3. Auto-enroll every employee who clicked in Campaign #1 into mandatory
     remedial training -- which measurably lowers their hidden
     susceptibility trait.
  4. Several (simulated) weeks later, run Campaign #2 (a harder, more
     convincing lure) against the same roster, now with trained employees
     less susceptible.
  5. Print per-campaign results and the before/after improvement comparison.
  6. Write the full management-facing report to phishing_campaign_report.md.

Everything here is synthetic simulation data. No real email is sent, no real
person is profiled -- see employee_roster.py's module docstring for the
privacy/scoring separation this project maintains.
"""

from __future__ import annotations

from campaign_report import build_report, write_report
from campaign_simulator import run_campaign, summarize
from employee_roster import build_roster
from phishing_templates import get_template
from remedial_training_tracker import TrainingTracker

CAMPAIGN_1_SEED = 42
CAMPAIGN_2_SEED = 99

CAMPAIGN_1_TEMPLATE_ID = "TPL-02"  # "Package Delivery Failed" -- moderate lure
# Campaign #2 reuses a template of the *same* difficulty tier so the
# click-rate comparison isolates the effect of remedial training rather than
# being confounded by a harder or easier lure. (Real awareness programs do
# the same: re-test with a comparable-difficulty lure to measure improvement.)
CAMPAIGN_2_TEMPLATE_ID = "TPL-02"  # "Package Delivery Failed" -- same lure, weeks later


def print_campaign_summary(title: str, stats: dict) -> None:
    print(f"\n{title}")
    print("-" * len(title))
    print(f"  Sent to  : {stats['total']}")
    print(f"  Clicked  : {stats['clicked']:>3}  ({stats['click_rate']:.1%})")
    print(f"  Reported : {stats['reported']:>3}  ({stats['report_rate']:.1%})")
    print(f"  Ignored  : {stats['ignored']:>3}  ({stats['ignore_rate']:.1%})")


def main() -> None:
    print("=" * 60)
    print(" SIMULATED SECURITY-AWARENESS PHISHING CAMPAIGN MANAGER")
    print(" (synthetic data only -- no real email is ever sent)")
    print("=" * 60)

    roster = build_roster()
    tracker = TrainingTracker()

    # ---- Campaign #1 -----------------------------------------------------
    template_1 = get_template(CAMPAIGN_1_TEMPLATE_ID)
    print(f"\nCampaign #1 launching: '{template_1.name}' "
          f"(difficulty={template_1.difficulty:.2f}) -> {len(roster)} employees")

    results_1 = run_campaign(roster, template_1, "campaign-1", seed=CAMPAIGN_1_SEED)
    tracker.record_campaign(results_1)
    stats_1 = summarize(results_1)
    print_campaign_summary("Campaign #1 Results", stats_1)

    # ---- Remedial training enrollment -------------------------------------
    newly_trained = tracker.enroll_clickers_from_campaign(roster, results_1)
    print(
        f"\nRemedial training: {len(newly_trained)} / {len(roster)} employees "
        f"auto-enrolled (everyone who clicked in Campaign #1)."
    )
    print("Simulating several weeks passing while training is completed...")

    # ---- Campaign #2 (weeks later, trained employees less susceptible) ---
    template_2 = get_template(CAMPAIGN_2_TEMPLATE_ID)
    print(f"\nCampaign #2 launching: '{template_2.name}' "
          f"(difficulty={template_2.difficulty:.2f}) -> {len(roster)} employees")

    results_2 = run_campaign(roster, template_2, "campaign-2", seed=CAMPAIGN_2_SEED)
    tracker.record_campaign(results_2)
    stats_2 = summarize(results_2)
    print_campaign_summary("Campaign #2 Results", stats_2)

    # ---- Improvement comparison -------------------------------------------
    delta = (stats_2["click_rate"] - stats_1["click_rate"]) * 100
    print("\nImprovement Comparison")
    print("-" * 23)
    print(f"  Campaign #1 click rate: {stats_1['click_rate']:.1%}")
    print(f"  Campaign #2 click rate: {stats_2['click_rate']:.1%}")
    print(f"  Change: {delta:+.1f} percentage points")
    if stats_2["click_rate"] < stats_1["click_rate"]:
        print("  -> Organization-wide susceptibility measurably IMPROVED.")
    else:
        print("  -> No improvement observed in this run.")

    # ---- Report -------------------------------------------------------------
    report_text = build_report(
        roster,
        tracker,
        campaign_1_results=results_1,
        campaign_1_template_name=template_1.name,
        campaign_2_results=results_2,
        campaign_2_template_name=template_2.name,
        trained_count=len(newly_trained),
    )
    path = write_report(report_text)
    print(f"\nFull management report written to: {path}")


if __name__ == "__main__":
    main()
