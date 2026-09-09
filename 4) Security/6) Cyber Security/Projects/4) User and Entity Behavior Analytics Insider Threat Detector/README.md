# User and Entity Behavior Analytics (UEBA) — Insider Threat Detector

A statistical/rule-based UEBA system that builds a **personal behavioral
baseline for every user** and flags **statistically significant deviations
from that specific user's own normal** — never a single organization-wide
rule. No trained ML model: just mean/std baselines, z-scores, and set
differences, computed independently per user. This is the same core
technique real UEBA products (Exabeam, Securonix, Microsoft Defender for
Identity) use for per-entity behavioral profiling before any advanced
scoring/ML layer is added on top.

## Real-world scenario

Every organization has employees whose "normal" looks nothing alike: a
9-to-5 financial analyst, a strict 9-to-5 sales rep, a night-shift sysadmin
who routinely logs in at 2am, and an engineer who only ever touches source
control and internal wikis. A **global** anomaly rule ("flag any login after
9pm", "flag any download over 500MB") either misses real insider threats
hiding inside someone else's normal range, or drowns analysts in false
positives by flagging the night-shift admin every single night.

Real UEBA deployments instead build **one baseline per user/entity** during
an observation window, then score every future day against *that specific
user's own* distribution. This project reproduces that approach end-to-end
with three insider-threat-relevant behaviors:

1. **Off-hours login** — a strict 9-5 employee suddenly logs in at 3am.
2. **Data-volume spike before resignation** — that same employee downloads
   ~50x their normal daily volume days before their resignation date — the
   textbook "bulk exfiltration before departure" pattern.
3. **Access-pattern deviation** — an engineer who has never in 60 days
   touched anything outside `source_code_repo` / `engineering_wiki` /
   `product_roadmap_docs` suddenly accesses `exec_board_docs`, `payroll_db`,
   and `legal_contracts_share` — systems entirely outside their role-based
   access pattern, even though the login hour and download volume look
   completely normal.
4. **The critical negative control** — a night-shift sysadmin logs in at her
   completely routine ~2:30am with her usual volume and usual systems. This
   day **must NOT be flagged**, proving the detector is personalized rather
   than a blanket "no logins after 9pm" rule that would otherwise flag her
   every single night of her working life.

## Architecture

| Module | Role | Real-world equivalent |
|---|---|---|
| `user_behavior_baseline_generator.py` | Synthesizes a 60-day "normal observation period" of per-user history (login hour, download volume, systems accessed), with genuinely distinct per-user profiles (including a night-shift profile) | The historical log ingestion window (SIEM/EDR/VPN/proxy logs) a real UEBA product replays before it trusts any baseline |
| `behavioral_baseline_model.py` | Computes each user's PERSONAL baseline: mean/std of login hour & volume, and a "usual systems" set, independently per user | A real UEBA product's per-entity baseline profile (e.g. Exabeam's/Securonix's per-user behavioral model) |
| `anomaly_scenarios.py` | Injects specific "new day" insider-threat scenarios (off-hours login + exfil before resignation, access-pattern deviation, and the night-shift negative control) as days to be scored after the baseline window | The suspicious activity a real insider-threat investigation is built around — resignation-adjacent bulk downloads, role-inconsistent access |
| `ueba_detector.py` | Scores one new day against ONE user's own baseline: circular z-score for login-hour deviation, z-score for volume deviation, set-difference for access-pattern deviation; flags if any signal crosses `Z_THRESHOLD` | Personalized anomaly scoring in a real insider-threat program — the core "is this normal *for them*" decision engine |
| `main.py` | Orchestrates the full pipeline, prints a flag report + summary, and saves `ueba_result.png` visualizing each user's scenario day against their own baseline distribution | The analyst-facing dashboard/alert triage view of a UEBA console |

## Run it

```bash
python main.py
```

Requires only `numpy` and `matplotlib` (stdlib otherwise). Produces console
output plus `ueba_result.png` in the project directory.

## Verified result

Actual output from `python main.py`:

```
==============================================================================
UEBA INSIDER-THREAT DETECTOR -- PERSONALIZED BASELINE SCORING RESULTS
==============================================================================

[FLAGGED]     bob_sales
              scenario: offhours_login_and_exfiltration_before_resignation
              3am login + ~50x normal download volume, 5 days before resignation date
              - OFF-HOURS LOGIN: logged in at 3.10h, 5.86h from personal mean 8.96h (z=8.53, personal std=0.69h)
              - VOLUME SPIKE: downloaded 4500.0 MB vs personal mean 91.9 MB (z=265.89, personal std=16.6 MB)

[FLAGGED]     dave_engineering
              scenario: access_pattern_deviation
              Normal hour/volume, but touched systems never seen in baseline (exec docs, payroll, legal) -- outside role-based access pattern
              - ACCESS-PATTERN DEVIATION: touched system(s) never seen in this user's baseline -> ['exec_board_docs', 'legal_contracts_share', 'payroll_db'] (usual pool=['engineering_wiki', 'product_roadmap_docs', 'source_code_repo'])

[NOT FLAGGED - correct] carla_nightadmin
              scenario: normal_nightshift_activity_control_case
              Routine ~2:30am login with normal volume/systems for a night-shift admin -- must NOT be flagged
              login_hour_z=0.03, volume_z=0.05, unexpected_systems=none  (threshold z=3.0)

------------------------------------------------------------------------------
SUMMARY
------------------------------------------------------------------------------
Users scored: 3
Flagged as anomalous: 2
Night-shift control case (carla_nightadmin): CORRECTLY NOT FLAGGED (login_hour_z=0.03 vs threshold 3.0) -- proves personalization, not a blanket off-hours rule.
==============================================================================

Saved plot: ueba_result.png
```

**Confirmed:**
- `bob_sales` — flagged on BOTH the off-hours login (z=8.53) and the volume
  spike (z=265.89) ahead of his resignation date.
- `dave_engineering` — flagged purely on access-pattern deviation (his login
  hour and volume z-scores are ~0, i.e. completely normal for him — only the
  never-before-seen systems trigger the flag).
- `carla_nightadmin` — **correctly NOT flagged.** Her ~2:30am login scores
  z=0.03 against her own baseline (mean 2.63h, std 0.88h), even though that
  exact same 2:30am login would have scored `z=(8.96-2.6)/0.69 ≈ 9.2` — a
  screaming anomaly — had it been scored against `bob_sales`'s baseline
  instead. This is the personalization proof: identical raw behavior, opposite
  verdict, depending entirely on whose baseline it's measured against.

`ueba_result.png` plots each flagged user's own 60-day login-hour and
download-volume histograms with their scenario day marked as a vertical line
(red = flagged, green = not flagged), so bob_sales's line visibly sits miles
outside his own distribution while carla_nightadmin's line sits right in the
middle of hers.

## Things to try changing

- **Lower/raise `Z_THRESHOLD`** in `ueba_detector.py` (e.g. 2.0 vs 4.0) and
  see how sensitivity trades off against false positives — try it against
  `erin_hr`, the untouched control user, to see the false-positive rate at
  each threshold.
- **Add a new user profile** in `user_behavior_baseline_generator.py` with an
  unusual but legitimate pattern (e.g. a weekend-only contractor) and inject
  a scenario day that's normal for them — confirm it's still not flagged.
- **Make the access-pattern signal graduated** instead of binary (e.g. flag
  only if `len(unexpected_systems) / len(usual_systems)` exceeds a ratio,
  rather than any single unexpected system).
- **Combine signals into one composite risk score** (e.g. weighted sum of
  the three z-scores) instead of "flag if ANY threshold is crossed", and
  compare which scenario days move up/down in a ranked risk list.
- **Shrink the baseline observation window** (`OBSERVATION_DAYS`) to see how
  few days of history are enough before z-scores stop being reliable
  (`MIN_LOGIN_HOUR_STD`/`MIN_VOLUME_STD` floors exist specifically to guard
  against a too-short or too-consistent history producing a hair-trigger
  baseline).
