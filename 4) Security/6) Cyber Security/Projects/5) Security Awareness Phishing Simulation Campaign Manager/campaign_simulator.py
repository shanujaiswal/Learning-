"""
campaign_simulator.py

Runs one simulated (never real) phishing-awareness campaign: a single
template is "sent" to every employee on the roster, and each employee's
reaction -- clicked / reported / ignored -- is simulated using their hidden
susceptibility trait combined with the template's difficulty weight.

This plays the role a real phishing-simulation platform (KnowBe4, Proofpoint
Security Awareness Training, GoPhish, etc.) plays in an actual security
program: it "sends" the lure and records the behavioral outcome. Nothing
here contacts a mailbox or network -- it is a probabilistic model only.

Response model
--------------
For an employee with hidden susceptibility `s` (0..1) and a template with
difficulty `d` (0..1):

    click_p   = s * (0.3 + 0.7 * d)         -- harder/more convincing lures
                                                 raise click odds, scaled by
                                                 how susceptible the person is
    remaining = 1 - click_p
    report_p  = remaining * (1 - s) * 0.8   -- less-susceptible (more aware)
                                                 employees are more likely to
                                                 report rather than merely
                                                 ignore
    ignore_p  = remaining - report_p

A single random draw against these three buckets decides the outcome.
"""

from __future__ import annotations

import random
from dataclasses import dataclass
from datetime import datetime

from employee_roster import Employee
from phishing_templates import PhishingTemplate

RESPONSE_CLICKED = "clicked"
RESPONSE_REPORTED = "reported"
RESPONSE_IGNORED = "ignored"


@dataclass
class CampaignResult:
    campaign_name: str
    employee_id: str
    name: str
    department: str
    template_id: str
    response: str
    timestamp: str


def _click_probability(susceptibility: float, difficulty: float) -> float:
    p = susceptibility * (0.3 + 0.7 * difficulty)
    return min(0.97, max(0.01, p))


def simulate_response(
    employee: Employee, template: PhishingTemplate, rng: random.Random
) -> str:
    """Draws a single click/report/ignore outcome for one employee."""
    susc = employee._susceptibility
    click_p = _click_probability(susc, template.difficulty)
    remaining = 1.0 - click_p
    report_p = remaining * (1.0 - susc) * 0.8
    ignore_p = remaining - report_p

    roll = rng.random()
    if roll < click_p:
        return RESPONSE_CLICKED
    elif roll < click_p + report_p:
        return RESPONSE_REPORTED
    else:
        return RESPONSE_IGNORED


def run_campaign(
    roster: list[Employee],
    template: PhishingTemplate,
    campaign_name: str,
    seed: int,
) -> list[CampaignResult]:
    """
    "Sends" `template` to every employee in `roster` and simulates their
    response. Returns one CampaignResult per employee, in roster order.
    """
    rng = random.Random(seed)
    timestamp = datetime.now().isoformat(timespec="seconds")
    results: list[CampaignResult] = []

    for emp in roster:
        response = simulate_response(emp, template, rng)
        results.append(
            CampaignResult(
                campaign_name=campaign_name,
                employee_id=emp.employee_id,
                name=emp.name,
                department=emp.department,
                template_id=template.template_id,
                response=response,
                timestamp=timestamp,
            )
        )

    return results


def summarize(results: list[CampaignResult]) -> dict:
    """Aggregate click/report/ignore rates for a list of results."""
    total = len(results)
    clicked = sum(1 for r in results if r.response == RESPONSE_CLICKED)
    reported = sum(1 for r in results if r.response == RESPONSE_REPORTED)
    ignored = sum(1 for r in results if r.response == RESPONSE_IGNORED)
    return {
        "total": total,
        "clicked": clicked,
        "reported": reported,
        "ignored": ignored,
        "click_rate": clicked / total if total else 0.0,
        "report_rate": reported / total if total else 0.0,
        "ignore_rate": ignored / total if total else 0.0,
    }


if __name__ == "__main__":
    from employee_roster import build_roster
    from phishing_templates import get_template

    roster = build_roster()
    template = get_template("TPL-04")
    results = run_campaign(roster, template, "demo-campaign", seed=42)
    stats = summarize(results)

    print(f"Demo campaign using template {template.template_id} ({template.name})")
    print(f"  Sent to {stats['total']} employees\n")
    print(f"  Clicked : {stats['clicked']:>3}  ({stats['click_rate']:.1%})")
    print(f"  Reported: {stats['reported']:>3}  ({stats['report_rate']:.1%})")
    print(f"  Ignored : {stats['ignored']:>3}  ({stats['ignore_rate']:.1%})")
