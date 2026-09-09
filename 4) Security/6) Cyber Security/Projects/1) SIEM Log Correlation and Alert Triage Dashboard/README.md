# SIEM Log Correlation & Alert Triage Dashboard

## Real-world scenario

A SOC analyst starts a shift and opens the SIEM. Overnight, the firewall,
the IDS/IPS sensor, and the authentication server have all been logging
independently, at high volume, mostly boring traffic. The analyst does not
read raw logs one by one -- the SIEM has already run **correlation
searches** (Splunk Enterprise Security) / **analytics rules** (Microsoft
Sentinel) / **rules & building blocks** (IBM QRadar) that stitch related
events together and turn them into a short, ranked list of **notable
events / incidents** the analyst actually triages.

This project simulates exactly that pipeline for one 4-hour shift, end to
end, with **rule-based, fully explainable logic -- no machine learning**.
Every point of every severity score can be traced back to one plain-English
rule ("5 failed logins in 23 seconds", "HIGH-severity IDS signature
match"), the same way a real correlation search's logic is auditable by a
SOC lead.

This is intentionally simple and distinct from the AI-Powered SOC capstone
elsewhere in this repo -- there is no model here, just deterministic
if/then detection logic, the way most SIEM correlation content actually
works in production.

## Architecture

| Module | Role | Real-world equivalent |
|---|---|---|
| `log_sources.py` | Generates one shift of synthetic multi-source logs (firewall, IDS, auth) with a fixed seed: background noise plus four deliberately seeded, coherent incidents. | The raw syslog/CEF/JSON feeds a SIEM ingests from a firewall, an IDS/IPS sensor, and an auth server. |
| `correlation_rules.py` | Stage 1: groups raw events by source IP within a sliding time window. Stage 2: runs five independent, human-readable detection rules against each group (signature match, firewall block, brute-force frequency, success-after-failure, multi-source corroboration). | Splunk ES correlation searches / Sentinel analytics rules -- the logic that turns a stream of log lines into candidate incidents. |
| `alert_triage_engine.py` | Sums the weights of whichever rules fired into a severity score, maps it onto CRITICAL/HIGH/MEDIUM/LOW bands, attaches a recommended action, and ranks alerts by priority (severity desc, then earliest-first). | The "notable event" layer in Splunk ES / the "incident" object Sentinel creates once a rule fires -- structured, scored, ranked. |
| `dashboard.py` | Renders the analyst-facing console view: the priority-sorted alert queue, severity/source breakdown counts, and a mean-time-to-triage (MTTT) stat from a synthetic analyst-review-time model. | The "Notable Events" / "Incidents" queue screen an analyst actually works a shift from in Splunk ES / Sentinel / QRadar. |
| `main.py` | Runs the full pipeline end to end, prints the live dashboard, and saves a matplotlib PNG summary. | The always-on SIEM pipeline (ingest -> correlate -> triage -> analyst screen) compressed into one reproducible run. |

## Run it

```bash
python main.py
```

Requires only the Python standard library plus `matplotlib` (already
installed). No network access, no external services, no ML libraries.

You can also run any module standalone for a quicker look at just its
stage, e.g. `python log_sources.py`, `python dashboard.py`.

## Verified result

Actual output from `python main.py` (fixed seed, fully reproducible):

```
Ingesting logs from firewall / IDS / auth sources for one 4-hour shift...
  -> 298 raw log events ingested.

Running correlation rules (grouping window = 180s)...
  -> 44 alert(s) produced after correlation + triage.

====================================================================================================
ALERT QUEUE  (sorted by priority: severity desc, then earliest-first)
====================================================================================================
#   ALERT ID   SEV       SCORE  SRC IP           SOURCES          FIRST SEEN           EVIDENCE
----------------------------------------------------------------------------------------------------
1   ALT-0007   CRITICAL  120    203.0.113.77     auth,firewall,ids 2026-08-18 08:35:01  5 rule(s)/13 event(s)
    -> MEDIUM-severity IDS signature match: ET SCAN Possible Nmap User-Agent Detected; 5 firewall BLOCK
       event(s) (port-scan-like probing), ports=21,23,3306,445,8443; 5 failed logins within 31s
       (threshold=5/120s); Accepted login for 'root' followed a failed-login streak from the same
       source IP -- likely compromise; Corroborated across all 3 sources: auth, firewall, ids
    -> Recommended action: Escalate to Tier 2/3 immediately -- begin containment (isolate host / block IP).

2   ALT-0022   HIGH      55     198.51.100.66    firewall,ids     2026-08-18 09:35:00  3 rule(s)/3 event(s)
    -> HIGH-severity IDS signature match: ET WEB_SERVER Possible SQL Injection UNION SELECT, ET
       WEB_SERVER SQL Injection Attempt; 1 firewall BLOCK event(s) (blocked connection), ports=443;
       Corroborated across 2 sources: firewall, ids
    -> Recommended action: Investigate as priority within this shift -- confirm scope before next
       tier escalation.

3   ALT-0030   HIGH      45     10.0.0.44        firewall,ids     2026-08-18 10:30:00  2 rule(s)/6 event(s)
    -> HIGH-severity IDS signature match: ET POLICY Suspicious Outbound to Known Bad IP, ET TROJAN
       Possible C2 Beacon Detected; Corroborated across 2 sources: firewall, ids
    -> Recommended action: Investigate as priority within this shift -- confirm scope before next
       tier escalation.

4   ALT-0040   MEDIUM    25     203.0.113.201    auth             2026-08-18 11:25:04  1 rule(s)/7 event(s)
    -> 5 failed logins within 23s (threshold=5/120s)
    -> Recommended action: Review in normal analyst queue -- confirm false-positive vs. benign
       vs. needs escalation.

5   ALT-0038   MEDIUM    20     10.0.0.33        auth,firewall,ids 2026-08-18 11:22:38  1 rule(s)/3 event(s)
    -> Corroborated across all 3 sources: auth, firewall, ids
    -> Recommended action: Review in normal analyst queue -- confirm false-positive vs. benign
       vs. needs escalation.

... (39 more LOW-severity alerts -- mostly coincidental 2-source corroboration
     between routine firewall ALLOWs and ordinary auth traffic sharing a
     time window; kept in the queue at the bottom for situational awareness) ...

====================================================================================================
BREAKDOWN
====================================================================================================
By severity:
  CRITICAL    1  #-----------------------------
  HIGH        2  ##----------------------------
  MEDIUM      2  ##----------------------------
  LOW        39  ##############################
By source (alerts touching each log source):
  firewall   41  ##############################
  ids        15  ###########-------------------
  auth       33  ########################------

====================================================================================================
SIMULATED TRIAGE-TIME STAT (synthetic analyst-review-time model)
====================================================================================================
  Alerts triaged this shift : 44
  Mean time to triage (MTTT): 1m 11s
  Total analyst time spent  : 51m 55s
  Slowest alert to triage   : ALT-0007 (7m 00s)
  Fastest alert to triage   : ALT-0010 (0m 45s)

Saved shift summary chart to: siem_dashboard_result.png
```

All four incidents deliberately seeded in `log_sources.py` were found and
triaged sensibly, exactly as intended:

1. **Scan -> brute force -> compromise** from `203.0.113.77` -> correctly
   ranked #1, **CRITICAL** (score 120) -- every rule fired, including the
   "Accepted login after a failed-login streak" rule that flags an actual
   compromise, not just an attempt.
2. **SQL injection web attack** from `198.51.100.66` -> **HIGH** (score 55).
3. **C2 beacon from an infected host** (`10.0.0.44` -> `45.33.12.9`) ->
   **HIGH** (score 45).
4. **Brute force that never succeeds** from `203.0.113.201` -> correctly
   triaged lower than the successful compromise, at **MEDIUM** (score 25).

The remaining 39 LOW alerts are background noise that happened to
corroborate across two sources within the 180s grouping window (e.g. a
routine firewall ALLOW and an ordinary login from the same internal IP) --
a realistic reminder that even simple correlation rules produce a long
tail of low-fidelity noise a real analyst has to skim past, which is why
they're ranked last and marked "no immediate action required."

`siem_dashboard_result.png` (generated by `main.py`) contains three panels:
alerts by severity, alerts by log source, and simulated per-alert triage
time colored by severity -- visually confirming the CRITICAL alert both
scores highest and takes the longest to triage (7m 00s vs. a ~1 minute mean).

## Things to try changing

- **Reduce LOW-severity noise**: raise the weight/threshold in
  `rule_multi_source_correlation` in `correlation_rules.py` (e.g. require
  3 sources, not 2, before it fires) and re-run to see the LOW-severity
  tail shrink.
- **Tighten or loosen the brute-force rule**: change
  `BRUTE_FORCE_THRESHOLD` / `BRUTE_FORCE_WINDOW` in `correlation_rules.py`
  and watch `ALT-0040` (bruteforce-only) move between MEDIUM and HIGH.
- **Add a new IDS signature**: add an entry to `IDS_SIGNATURES` in
  `log_sources.py` and reference it from a new `_incident_*` builder to
  see a fifth incident appear and get triaged automatically.
- **Change the grouping window**: pass a different `window` to
  `triage()` in `main.py` (e.g. 60s instead of 180s) and see incidents
  split into more, smaller alerts, or noise alerts disappear entirely.
- **Tune the triage-time model**: adjust `BASE_REVIEW_SECONDS`,
  `SECONDS_PER_EVENT`, or `SECONDS_PER_RULE_HIT` in `dashboard.py` to see
  how MTTT reacts to a "faster" or "slower" analyst.
- **Add a new severity band**: insert a fifth band (e.g. "INFO") in
  `SEVERITY_BANDS` in `alert_triage_engine.py` and give it its own
  recommended action and chart color.
