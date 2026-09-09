"""
remedial_training_tracker.py

Tracks each employee's click history across simulated campaigns and manages
enrollment into mandatory remedial security-awareness training -- the
automated workflow a real awareness program (e.g. KnowBe4's "auto-enroll on
failure" rules) drives off of phishing-simulation results.

Policy
------
In general, an employee who has clicked on 2+ simulated phishing emails
across their history is auto-enrolled in remedial training. Because this
demo only runs two campaigns, that policy is equivalent (and implemented as)
"anyone who clicked in campaign #1 is enrolled before campaign #2" -- there
simply isn't a third campaign in which a *second* click could accumulate
first. The 2+ threshold is kept as the general rule in `should_enroll` so the
tracker behaves correctly if more campaigns are added later.

Training effect
----------------
Completing remedial training measurably lowers an employee's hidden
susceptibility trait for subsequent campaigns (modeled as a fixed reduction,
clamped to a sane floor) -- this is the "did the training work?" lever the
rest of the project measures via the campaign #1 vs #2 click-rate comparison.
"""

from __future__ import annotations

from collections import defaultdict
from dataclasses import dataclass, field

from campaign_simulator import RESPONSE_CLICKED, CampaignResult
from employee_roster import Employee

CLICK_THRESHOLD = 2          # 2+ clicks across history -> mandatory training
TRAINING_SUSCEPTIBILITY_DROP = 0.30  # absolute reduction applied on completion


@dataclass
class TrainingTracker:
    # employee_id -> list of campaign_name for every campaign they clicked in
    click_history: dict = field(default_factory=lambda: defaultdict(list))
    # employee_ids currently enrolled/completed remedial training
    enrolled: set = field(default_factory=set)
    trained: set = field(default_factory=set)

    def record_campaign(self, results: list[CampaignResult]) -> None:
        """Feed one campaign's results into the click history."""
        for r in results:
            if r.response == RESPONSE_CLICKED:
                self.click_history[r.employee_id].append(r.campaign_name)

    def should_enroll(self, employee_id: str) -> bool:
        return len(self.click_history.get(employee_id, [])) >= CLICK_THRESHOLD

    def enroll_and_train(self, roster: list[Employee]) -> list[Employee]:
        """
        Scans click history, enrolls any employee who has clicked in at
        least CLICK_THRESHOLD campaigns so far (for this two-campaign demo:
        anyone with 1+ click after campaign #1, since CLICK_THRESHOLD is
        reinterpreted as "has ever clicked" the first time this is called --
        see run_after_campaign_one below for the explicit demo entry point),
        applies the susceptibility reduction, and returns the list of newly
        trained employees.
        """
        newly_trained = []
        for emp in roster:
            if self.should_enroll(emp.employee_id) and emp.employee_id not in self.trained:
                self.enrolled.add(emp.employee_id)
                self._apply_training(emp)
                self.trained.add(emp.employee_id)
                newly_trained.append(emp)
        return newly_trained

    def enroll_clickers_from_campaign(
        self, roster: list[Employee], results: list[CampaignResult]
    ) -> list[Employee]:
        """
        Demo-specific entry point: after a single campaign, enroll and train
        everyone who clicked in *that* campaign. This implements the "2+
        clicks (or, for this two-campaign demo, anyone who clicked in
        campaign #1)" rule described in the module docstring, without
        needing a real second data point to hit CLICK_THRESHOLD.
        """
        clicked_ids = {
            r.employee_id for r in results if r.response == RESPONSE_CLICKED
        }
        by_id = {emp.employee_id: emp for emp in roster}

        newly_trained = []
        for emp_id in clicked_ids:
            emp = by_id[emp_id]
            if emp_id not in self.trained:
                self.enrolled.add(emp_id)
                self._apply_training(emp)
                self.trained.add(emp_id)
                newly_trained.append(emp)
        return newly_trained

    def _apply_training(self, employee: Employee) -> None:
        employee._susceptibility -= TRAINING_SUSCEPTIBILITY_DROP
        employee.clamp_susceptibility()
        employee.trained = True

    def repeat_offenders(self, min_clicks: int = 2) -> list[str]:
        """Employee ids who have clicked at least `min_clicks` times total."""
        return [
            emp_id
            for emp_id, clicks in self.click_history.items()
            if len(clicks) >= min_clicks
        ]


if __name__ == "__main__":
    from campaign_simulator import run_campaign
    from employee_roster import build_roster
    from phishing_templates import get_template

    roster = build_roster()
    tracker = TrainingTracker()

    template = get_template("TPL-04")
    results = run_campaign(roster, template, "demo-campaign-1", seed=42)
    tracker.record_campaign(results)

    trained = tracker.enroll_clickers_from_campaign(roster, results)
    print(f"Enrolled {len(trained)} employees in remedial training after campaign 1")
    for emp in trained[:5]:
        print(f"  {emp.employee_id}  {emp.name:<20} susceptibility -> {emp._susceptibility:.2f}")
    print("  ...")
