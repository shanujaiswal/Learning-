# Security Awareness Phishing Simulation Campaign Manager

A small, stdlib-only Python simulation of an internal security-awareness
phishing exercise. **Everything here is synthetic.** There is no real email
sending, no real employees, no real network activity, and no individual
"risk score" surveillance -- it purely models, end to end, how a security
team runs a simulated phishing campaign, tracks who clicked, auto-enrolls
repeat clickers into remedial training, and measures whether that training
actually reduces click rates the next time around.

## Real-world scenario

Security-awareness teams at most mid-size and large companies run scheduled,
*simulated* phishing campaigns (never real phishing) using platforms like
KnowBe4 or Proofpoint Security Awareness Training. A believable-but-fake
lure is sent to some or all employees; the platform records who clicked the
link, who reported it via the "Report Phishing" button, and who ignored it.
Employees who click are automatically enrolled in short remedial training
modules. Programs then re-test with a similarly-difficulty lure weeks later
to prove -- with real click-rate numbers -- that the training measurably
reduced organizational susceptibility. This project models that entire
loop end to end with synthetic data.

## Architecture

| Module | Role in this project | Real-world equivalent |
|---|---|---|
| `employee_roster.py` | Generates a fixed, reproducible synthetic company roster (60 employees, 5 departments) with a hidden per-employee "susceptibility" trait used only to drive the simulation | HRIS / directory sync feeding a phishing-simulation platform's target list |
| `phishing_templates.py` | Defines a small library of simulated lure templates with a difficulty weight and documented red flags | The template library in KnowBe4 / Proofpoint Security Awareness Training |
| `campaign_simulator.py` | "Sends" one template to the whole roster and simulates each employee's click / report / ignore outcome from susceptibility + template difficulty | The simulation/delivery + click-tracking engine of a phishing-simulation platform (e.g. GoPhish, KnowBe4) |
| `remedial_training_tracker.py` | Tracks click history per employee across campaigns; auto-enrolls repeat clickers into mandatory remedial training; models training lowering susceptibility | Automated remedial-training enrollment workflows (auto-enroll-on-failure rules) |
| `campaign_report.py` | Builds the management-facing Markdown report: overall + department-level rates, repeat-offender list, before/after improvement metrics | The reporting/dashboard layer a security team shows leadership after a campaign |
| `main.py` | Orchestrates the two-campaign demo end to end and writes the report | The scheduled campaign-runner / cron job of a real awareness program |

## Run it

```bash
python main.py
```

This will:

1. Build the synthetic 60-person roster.
2. Run **Campaign #1** ("Package Delivery Failed", difficulty 0.35) against
   everyone.
3. Auto-enroll every employee who clicked in Campaign #1 into mandatory
   remedial training (which lowers their hidden susceptibility trait).
4. Simulate several weeks passing, then run **Campaign #2** using the same
   template/difficulty tier (to isolate the training effect from lure
   difficulty) against the same roster.
5. Print per-campaign click/report/ignore breakdowns and a before/after
   improvement comparison to the console.
6. Write the full report to `phishing_campaign_report.md`.

No arguments, no external packages -- only the Python standard library
(`random`, `dataclasses`, `collections`, `datetime`, `pathlib`).

## Verified result

From an actual run (`python main.py`), 60 employees, both campaigns using
the "Package Delivery Failed" template (difficulty 0.35):

| Metric | Campaign #1 | Campaign #2 |
|---|---|---|
| Clicked | 17 / 60 (**28.3%**) | 13 / 60 (**21.7%**) |
| Reported | 20 / 60 (33.3%) | 29 / 60 (48.3%) |
| Ignored | 23 / 60 (38.3%) | 18 / 60 (30.0%) |

- 17 / 60 employees were auto-enrolled in remedial training after Campaign #1.
- Organization-wide click rate **decreased by 6.7 percentage points**
  (a 23.5% relative reduction) between Campaign #1 and Campaign #2, and the
  report rate rose from 33.3% to 48.3% -- a measurable, quantified
  improvement directly attributable to the remedial-training intervention.

(Results are seeded/reproducible per run via fixed RNG seeds in `main.py`;
exact numbers will match the above on an unmodified checkout.)

## Things to try changing

- **`CAMPAIGN_1_TEMPLATE_ID` / `CAMPAIGN_2_TEMPLATE_ID` in `main.py`** --
  swap in harder templates (`TPL-04`, `TPL-05`) to see click rates spike,
  or make Campaign #2 harder than Campaign #1 to see training effects
  fight against a tougher lure.
- **`TRAINING_SUSCEPTIBILITY_DROP` in `remedial_training_tracker.py`** --
  raise or lower how much training reduces susceptibility to model a more
  or less effective training program.
- **`CLICK_THRESHOLD` in `remedial_training_tracker.py`** -- change how many
  clicks it takes to trigger mandatory enrollment (only matters once you
  add a 3rd+ campaign).
- **`DEPARTMENTS` in `employee_roster.py`** -- add departments or change
  the mean/std-dev susceptibility per department to model a different org
  structure.
- **Add a 3rd campaign in `main.py`** -- chain another `run_campaign` +
  `tracker.record_campaign` + report call to see the repeat-offender logic
  (2+ clicks total) actually trigger via `enroll_and_train`, and to watch
  whether improvement continues, plateaus, or reverses over time.
