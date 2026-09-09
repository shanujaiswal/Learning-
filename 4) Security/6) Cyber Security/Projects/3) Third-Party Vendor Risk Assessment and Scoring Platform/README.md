# Third-Party Vendor Risk Assessment and Scoring Platform

## Real-World Scenario

A company's vendor-risk-management (VRM) team is renewing contracts with a
list of third-party vendors -- payroll processors, cloud hosts, invoicing
SaaS tools, health-records APIs, newsletter platforms, and more. Before
renewal, each vendor:

1. Fills out a **security self-assessment questionnaire** (encryption,
   MFA, SOC 2 certification, breach history, subprocessor sprawl) --
   similar to a SIG Lite / CAIQ questionnaire in a real GRC tool.
2. Has a **documented data-access scope** on file -- what category of data
   (none, anonymized, PII, PCI, PHI, or PCI+PHI) the integration actually
   touches, and roughly how many individuals' records that covers.

A naive process would score risk from the questionnaire alone. That's
wrong: a perfectly-behaved vendor holding SSNs and health records is a far
bigger blast-radius risk than an equally well-behaved vendor that only
ever sees anonymized aggregate counts. This platform combines **both**
signals into one overall risk score and tier, and flags which vendors need
**enhanced due diligence** before their contract is renewed -- exactly the
output a risk committee reviews in a real TPRM (Third-Party Risk
Management) tool.

## Architecture

| Module | Role | Real-world equivalent |
|---|---|---|
| `questionnaire_scorer.py` | Converts raw questionnaire answers (encryption, MFA, SOC 2, breach history, subprocessor count) into a 0-100 "security posture risk score", knowing nothing about what data the vendor touches. | The scoring rubric behind a SIG Lite / CAIQ security questionnaire. |
| `data_sensitivity_model.py` | Scores the *blast radius* of a vendor's data-access scope (data type x record volume) into a sensitivity multiplier, independent of the vendor's security hygiene. | A data-classification policy (Public/Internal/Confidential/Restricted) combined with a data-flow mapping exercise. |
| `vendor_registry.py` | The system of record: each vendor's identity, category, questionnaire response, and data-access scope. | A VRM intake system / GRC tool (OneTrust, ServiceNow VRM, Vanta) storing questionnaire responses alongside a data-flow map. |
| `risk_engine.py` | Combines posture score x sensitivity multiplier into one overall risk score and tier (Low/Medium/High/Critical), and applies a breach-override rule to flag vendors for enhanced due diligence. | A real TPRM platform's risk-scoring model (e.g. OneTrust, BitSight, SecurityScorecard) -- the part that turns "questionnaire answers" + "what this vendor touches" into a single number a risk committee can act on. |
| `main.py` | Runs the assessment across the full vendor registry, prints each vendor's score breakdown/tier plus a summary table, then a side-by-side proof that data sensitivity -- not the questionnaire alone -- changes the outcome. | The report a vendor risk committee reviews before a renewal/termination decision. |

## Run It

```bash
python main.py
```

No third-party dependencies -- Python standard library only.

## Verified Result (actual output)

Full per-vendor breakdown and summary table:

```
Vendor ID Name                               Score        Tier    EDD?
V001      PayStream Payroll Processing        44.0      Medium      no
V002      BrightLeaf Newsletter Co            20.0         Low      no
V003      ClearRoute Cloud Hosting             0.0         Low      no
V004      QuickBooks-Alike Invoicing Startup   160.0    Critical     YES
V005      MedSync Health Records API         138.7        High     YES
V006      OfficeSupplyDirect                  24.0         Low      no
V007      AnalyticsAggregate Insights         18.7         Low      no
```

Same-questionnaire, different-data-sensitivity proof (V001 vs. V002 -- both
answer `encryption_at_rest=True, mfa_enforced=True, has_soc2=False,
breach_in_last_2_years=False, subprocessor_count=3`, giving an **identical**
posture risk score of 20 for both vendors):

```
V001 (PayStream Payroll Processing) vs. V002 (BrightLeaf Newsletter Co)
------------------------------------------------------------------------
                                            V001                V002
Data access scope                        PCI_PHI                 PII
Record count                              42,000              42,000
Sensitivity multiplier                      x2.2                x1.0
Overall risk score                          44.0                20.0
RISK TIER                                 Medium                 Low

PROVEN: same questionnaire, but V001 = Medium vs V002 = Low.
Data sensitivity (what the vendor can actually access) genuinely changes
the risk outcome -- it is not just the questionnaire in disguise.
```

`main.py` `assert`s that `assessment_a.tier != assessment_b.tier` -- the
script exits cleanly (exit code 0) only if this holds, so a passing run is
itself proof the demonstration works.

Also worth noting from the full run: V004 (weak questionnaire + recent
breach + PCI data) and V005 (weak questionnaire + recent breach + PHI data)
both trip the `_breach_override_reason` rule in `risk_engine.py` and are
flagged `Enhanced due diligence: REQUIRED` regardless of their numeric tier
thresholds, landing Critical and High respectively.

## Things to Try Changing

- **Flip `V006`'s data scope** from `DataType.NONE` to `DataType.PHI` with a
  large record count and re-run -- watch a vendor with objectively poor
  questionnaire hygiene (no encryption, no MFA, no SOC 2) jump from Low to
  Critical purely because of what it would then be allowed to touch.
- **Tune `_TIER_THRESHOLDS`** in `risk_engine.py` to make tiers stricter or
  looser, and see how many vendors move between Medium/High/Critical.
- **Add a new `DataType`** (e.g. `SOURCE_CODE` or `TRADE_SECRETS`) in
  `data_sensitivity_model.py` with its own multiplier, and assign it to a
  new vendor in `vendor_registry.py`.
- **Add a second breach-override rule** in `risk_engine.py` -- e.g. any
  vendor with `subprocessor_count > 10` handling PHI also gets an automatic
  enhanced-due-diligence flag, independent of overall score.
- **Add a third "identical questionnaire" vendor** to the `main.py` proof
  (e.g. a version of V001 with `DataType.ANONYMIZED` instead) to show the
  full spread of tiers a single fixed questionnaire can produce depending
  purely on data-access scope.
