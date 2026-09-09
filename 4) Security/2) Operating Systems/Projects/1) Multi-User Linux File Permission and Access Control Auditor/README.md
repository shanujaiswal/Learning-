# Multi-User Linux File Permission and Access Control Auditor

## Real-World Scenario

You're a sysadmin (or the security engineer covering "systems hardening")
responsible for a shared Linux server with dozens of user accounts, several
team-shared directories, and the usual pile of system config and binaries.
Over months of ad-hoc `chmod -R`, quick "just make it work" fixes, and one
legacy internal tool nobody remembers installing, the permission posture has
quietly drifted away from policy. Nobody notices until it's exploited.

This is exactly the job of tools like **Lynis**, **OpenSCAP**, or a
CIS-benchmark scan script: walk the filesystem, compare what's actually
there against what *should* be there, and hand back a prioritized list of
fixes instead of a wall of raw `ls -l` output.

This project simulates that server's permission metadata entirely in
memory (no real filesystem access, fully cross-platform, fully
reproducible) and audits it for four realistic classes of misconfiguration:

1. **World-writable sensitive files** -- e.g. a root-owned cron job that
   somehow ended up `0666`, letting *any* local user rewrite what root's
   cron will execute next.
2. **Unapproved SUID/SGID binaries** -- a binary that runs with its
   owner's privileges (per the SUID mechanism) but isn't on the
   allowlist of tools that are supposed to do that -- the textbook
   Linux privilege-escalation vector (`find / -perm -4000`, just run
   defensively).
3. **Group-write privilege overlap** -- a file owned by one user but
   group-writable by a group that has no legitimate business with it
   (payroll data writable by the interns group).
4. **Directory intent vs. reality mismatches** -- a "private" home
   directory that is actually world-readable/traversable, silently
   undermining every file-level permission inside it.

## Architecture

| Module | Role | Real-World Equivalent |
|---|---|---|
| `filesystem_simulator.py` | Generates a deterministic (seeded) synthetic filesystem: paths, owner, group, octal mode, SUID/SGID bits, plus 4 deliberately injected misconfigurations | The disk itself / what `os.stat()` and `os.lstat()` would report on a live server |
| `permission_rules.py` | The audit policy: SUID/SGID allowlist, per-path-category permission ceilings (system config / binary / user home / shared-group / log), expected-group-per-shared-directory map | A CIS Benchmark profile, a Lynis policy file, or an OpenSCAP XCCDF ruleset |
| `auditor.py` | The scanning engine: walks every entry, applies every rule, emits structured `Finding` records with severity | The scanning core of Lynis / OpenSCAP / a custom `find`+`stat` hardening script |
| `remediation_report.py` | Renders findings into a prioritized Markdown report with exact `chmod`/`chown` commands | The remediation appendix of a compliance scan report |
| `main.py` | Orchestrates the full run: build -> scan (streaming findings) -> summarize -> report | The CLI entrypoint you'd actually run, e.g. `lynis audit system` |

## Run It

Requires only the Python standard library (developed/tested on Python 3.10+
for the `X | None` type hints and `match`-free structural style used
throughout).

```bash
cd "3) Security/2) Operating Systems/Projects/1) Multi-User Linux File Permission and Access Control Auditor"
python main.py
```

This will:
1. Build the simulated filesystem (78 entries, fixed seed `1337`).
2. Print each finding as it's discovered, tagged with severity.
3. Print a severity/summary breakdown.
4. Write `permission_audit_report.md` next to the scripts.

You can also run any module standalone for a narrower view:

```bash
python filesystem_simulator.py   # dump the first entries of the synthetic tree
python auditor.py                # run just the scan + a quick summary
python remediation_report.py     # scan + write the report only
```

## Verified Result (actual output from `python main.py`)

```
==============================================================================
Multi-User Linux File Permission and Access Control Auditor
==============================================================================

Simulated filesystem loaded: 78 entries.

Scanning for misconfigurations...

  [!! CRITICAL] WORLD_WRITABLE             /etc/cron.d/backup-job
  [!! CRITICAL] UNAUTHORIZED_SUID_SGID     /usr/local/bin/legacy-report-tool
  [!  HIGH    ] PERMISSION_POLICY_VIOLATION /home/carol
  [!  HIGH    ] GROUP_PRIVILEGE_OVERLAP    /srv/shared/finance/bonus_plan.xlsx

------------------------------------------------------------------------------
SUMMARY
------------------------------------------------------------------------------
Entries scanned : 78
Total findings  : 4
  CRITICAL : 2
  HIGH     : 2
  MEDIUM   : 0
  LOW      : 0

Generating remediation report...
Report written to: ...\permission_audit_report.md

Most severe finding: [CRITICAL] WORLD_WRITABLE on '/etc/cron.d/backup-job'
```

All **4 deliberately injected misconfigurations** were found, and nothing
else was flagged (the ~70 healthy baseline entries produced zero false
positives):

| # | Injected bug | Detected as | Recommended fix |
|---|---|---|---|
| 1 | `/etc/cron.d/backup-job` at `0666` | `WORLD_WRITABLE` (CRITICAL) | `chmod 0644 /etc/cron.d/backup-job` |
| 2 | `/usr/local/bin/legacy-report-tool`, SUID set, not allowlisted | `UNAUTHORIZED_SUID_SGID` (CRITICAL) | `chmod 0755 /usr/local/bin/legacy-report-tool` |
| 3 | `/home/carol` at `0755` (world-readable+traversable) | `PERMISSION_POLICY_VIOLATION` (HIGH) | `chmod 0750 /home/carol` |
| 4 | `/srv/shared/finance/bonus_plan.xlsx` group `interns` instead of `finance` | `GROUP_PRIVILEGE_OVERLAP` (HIGH) | `chown bob:finance /srv/shared/finance/bonus_plan.xlsx` |

The generated `permission_audit_report.md` contains all four findings with
full descriptions, plus a copy/paste block of every remediation command.

## Things to Try Changing

- **Add a 5th injected bug** in `filesystem_simulator.py`, e.g. an
  `/etc/shadow`-equivalent file made world-readable (`0644` instead of a
  restrictive mode) -- extend `permission_rules.py`'s `SYSTEM_CONFIG`
  policy to also forbid `S_IROTH` on truly sensitive files, and confirm
  the auditor catches it.
- **Tighten the SUID allowlist** in `permission_rules.py` by removing
  `/usr/bin/wall` and re-running -- watch a previously "legitimate"
  binary become a new finding, showing how policy changes ripple through
  without touching the scanning engine at all.
- **Add a new path category** (e.g. `/opt/vendor` third-party software)
  with its own `PermissionPolicy` in `permission_rules.py`, and a matching
  branch in `classify()` -- demonstrates how the rules/engine separation
  scales to new parts of a real filesystem tree.
- **Make the noise realistic at scale**: bump the `range(20)` loop in
  `filesystem_simulator.py` to `range(2000)` to simulate a much larger
  org server, and confirm the auditor still surfaces exactly the same 4
  real findings out of thousands of entries (signal vs. noise at scale).
- **Add a severity for stale SGID directories**: extend `auditor.py` with
  a check for directories where the SGID bit is set outside
  `/srv/shared/*` (SGID on a directory makes new files inherit the
  directory's group -- legitimate for team dirs, suspicious elsewhere).
