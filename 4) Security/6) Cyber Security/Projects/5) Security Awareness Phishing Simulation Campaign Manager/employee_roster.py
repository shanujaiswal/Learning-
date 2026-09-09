"""
employee_roster.py

Generates a synthetic company roster for a simulated (never real) phishing
awareness campaign. Every employee is fictional; nothing here touches a real
mailbox, real identity, or real HR system.

Each employee is given a hidden "susceptibility" trait (0.0 - 1.0). This is
an INTERNAL simulation variable only, used to make simulated click/report
behavior realistic (e.g. non-technical departments trend more susceptible
than IT). It is deliberately never surfaced in campaign_report.py's
management-facing output -- the report only ever shows aggregate,
department-level behavioral stats (click/report/ignore counts), never a
per-person "risk score" profiling field. That separation mirrors real
awareness-training platforms, which track behavioral history for training
purposes but must not turn it into an individual surveillance/scoring tool.

Run this file directly to print the roster for a quick sanity check.
"""

import random
from dataclasses import dataclass, field

SEED = 1337

FIRST_NAMES = [
    "Alex", "Jordan", "Taylor", "Morgan", "Casey", "Riley", "Jamie", "Cameron",
    "Drew", "Skyler", "Avery", "Peyton", "Quinn", "Reese", "Dana", "Rowan",
    "Sam", "Emerson", "Harper", "Kendall", "Logan", "Parker", "Sage", "Elliot",
]

LAST_NAMES = [
    "Nguyen", "Smith", "Patel", "Garcia", "Kim", "Johnson", "Brown", "Davis",
    "Martinez", "Lopez", "Chen", "Anderson", "Taylor", "Thomas", "Moore",
    "Jackson", "White", "Harris", "Clark", "Lewis", "Young", "Allen", "King",
]

# Department -> (base susceptibility mean, std-dev) used only to *seed* each
# employee's hidden trait. Reflects the real-world pattern noted in the
# awareness-training theory notes: non-technical, high email-volume roles
# (Sales, HR, Finance) trend more susceptible than IT/security staff who
# handle phishing tells daily.
DEPARTMENTS = {
    "Finance": (0.55, 0.15),
    "Sales": (0.60, 0.15),
    "HR": (0.50, 0.15),
    "IT": (0.20, 0.10),
    "Operations": (0.45, 0.15),
}

ROSTER_SIZE = 60


@dataclass
class Employee:
    employee_id: str
    name: str
    department: str
    role: str
    # Hidden simulation-only trait: probability weight feeding the campaign
    # simulator's click/report/ignore roll. NEVER printed in the management
    # report -- see campaign_report.py.
    _susceptibility: float = field(repr=False, default=0.5)
    trained: bool = False  # whether they've completed remedial training

    def clamp_susceptibility(self) -> None:
        self._susceptibility = min(0.95, max(0.02, self._susceptibility))


def _role_for_department(dept: str, rng: random.Random) -> str:
    roles = {
        "Finance": ["Accountant", "Financial Analyst", "AP Clerk", "Controller"],
        "Sales": ["Account Executive", "Sales Rep", "BDR", "Sales Manager"],
        "HR": ["HR Generalist", "Recruiter", "HR Manager", "Payroll Admin"],
        "IT": ["Sysadmin", "Helpdesk Tech", "Security Analyst", "Developer"],
        "Operations": ["Ops Coordinator", "Logistics Analyst", "Ops Manager", "Facilities"],
    }
    return rng.choice(roles[dept])


def build_roster(seed: int = SEED, size: int = ROSTER_SIZE) -> list[Employee]:
    """Builds the fixed, reproducible synthetic roster."""
    rng = random.Random(seed)
    dept_names = list(DEPARTMENTS.keys())

    roster: list[Employee] = []
    used_names: set[str] = set()

    for i in range(size):
        dept = dept_names[i % len(dept_names)]
        mean, std = DEPARTMENTS[dept]

        # pick a unique-ish full name
        for _ in range(50):
            full_name = f"{rng.choice(FIRST_NAMES)} {rng.choice(LAST_NAMES)}"
            if full_name not in used_names:
                used_names.add(full_name)
                break

        susceptibility = rng.gauss(mean, std)
        emp = Employee(
            employee_id=f"EMP{i + 1:04d}",
            name=full_name,
            department=dept,
            role=_role_for_department(dept, rng),
            _susceptibility=susceptibility,
        )
        emp.clamp_susceptibility()
        roster.append(emp)

    return roster


if __name__ == "__main__":
    roster = build_roster()
    print(f"Synthetic roster: {len(roster)} employees across {len(DEPARTMENTS)} departments\n")
    for emp in roster[:10]:
        print(f"{emp.employee_id}  {emp.name:<20} {emp.department:<12} {emp.role}")
    print("...")
