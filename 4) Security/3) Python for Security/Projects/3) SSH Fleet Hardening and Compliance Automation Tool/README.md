# SSH Fleet Hardening and Compliance Automation Tool

## Real-world scenario

A security engineer is responsible for SSH hardening across a fleet of servers —
web tier, databases, a bastion host, CI runners, and more. Two things need
checking on a recurring basis:

1. **Config compliance** — does every host's `sshd_config` meet baseline hardening
   rules (root login disabled, password auth disabled, only strong ciphers, no
   Protocol 1, restricted login access)? This is exactly what a CIS Benchmark scan
   or a Lynis SSH audit checks, just automated and scored per host.
2. **Key hygiene** — across the fleet's `authorized_keys` files, is any single key
   reused across multiple accounts/hosts (one leaked laptop compromising three
   accounts at once), and are there keys installed with no comment identifying
   who they belong to (so nobody can tell if they're still needed)?

This project automates both checks across an entire simulated fleet, scores each
host 0-100, ranks the worst offenders, and generates exact remediation commands —
the kind of report a security team would hand to ops after a fleet-wide audit.

**Fully offline and self-contained.** Real fleet tooling (see Theory Ch.5,
"Automating SSH and Remote Tasks with Paramiko") would use `paramiko.SSHClient` to
connect to each host, pull its `/etc/ssh/sshd_config` and `~/.ssh/authorized_keys`,
and run the same checks against the real files. This project simulates that fleet
locally with a fixed random seed — same hosts, same injected violations, every
run — so it runs anywhere with no SSH server, no network, and no credentials.

## Architecture

| Module | Role | Real-world equivalent |
|---|---|---|
| `fleet_inventory.py` | Generates a synthetic fleet of `sshd_config` records and an `authorized_keys` inventory, fixed seed, with deliberately injected violations | The "collect config from every host" step a Paramiko fleet script performs over real SSH connections |
| `cis_ssh_benchmark.py` | Defines the CIS-style ruleset (root login, password auth, ciphers, protocol, access restriction) as independent, testable rule functions | A CIS Benchmark document / Lynis SSH hardening check |
| `config_auditor.py` | Applies the benchmark to every host, computes a severity-weighted 0-100 compliance score, ranks worst offenders | The scoring engine behind a compliance dashboard (e.g. Lynis hardening index, OpenSCAP score) |
| `key_hygiene_auditor.py` | Scans the fleet-wide key inventory for duplicate keys reused across accounts and keys with no identifying comment | A key-reuse / key-inventory audit like `ssh-audit`, or a manual access-review pass before an audit |
| `remediation_generator.py` | Converts findings into exact `sshd_config` line changes and `sed`/`systemctl` commands, writes `ssh_compliance_report.md` | The remediation playbook / runbook a security team hands to ops after a scan |
| `main.py` | Orchestrates the full run: generate fleet -> audit configs -> audit keys -> print summary -> write report | The top-level driver script / cron job that runs the nightly compliance sweep |

## Run it

```bash
python main.py
```

No dependencies beyond the Python standard library — no `pip install`, no real
SSH server, no network access required.

## Verified result (actual output)

Ran with the fixed seed (`FLEET_SEED = 1337`), fleet of 15 hosts / 51
`authorized_keys` entries. Full console output was captured; abbreviated here for
readability (per-host PASS/FAIL detail is shown in full for a `main.py` run):

```
SSH Fleet Hardening and Compliance Automation Tool
(Simulated fleet — no real SSH connections are made.)

==============================================================================
PHASE 1: sshd_config compliance audit (per host)
==============================================================================

[web-01.fleet.internal] score=100.0/100  -> COMPLIANT
    [PASS] CIS-5.2.8    Ensure SSH root login is disabled
    [PASS] CIS-5.2.10   Ensure SSH PasswordAuthentication is disabled
    [PASS] CIS-5.2.13   Ensure only strong ciphers are used
    [PASS] CIS-5.2.2    Ensure SSH Protocol is not set to 1
    [PASS] CIS-5.2.20   Ensure SSH access is limited via AllowUsers/AllowGroups

[db-02.fleet.internal] score= 75.0/100  -> VIOLATIONS FOUND
    [FAIL] CIS-5.2.8    Ensure SSH root login is disabled
           -> PermitRootLogin is 'yes' — root login is not fully disabled.
    ... (other rules PASS)

[cache-03.fleet.internal] score= 75.0/100  -> VIOLATIONS FOUND
    [FAIL] CIS-5.2.10   Ensure SSH PasswordAuthentication is disabled
           -> PasswordAuthentication is 'yes' — passwords are accepted alongside/instead of keys.

[queue-04.fleet.internal] score= 83.3/100  -> VIOLATIONS FOUND
    [FAIL] CIS-5.2.13   Ensure only strong ciphers are used
           -> Weak/legacy cipher(s) configured: aes256-cbc, 3des-cbc.

[app-05.fleet.internal] score= 75.0/100  -> VIOLATIONS FOUND
    [FAIL] CIS-5.2.2    Ensure SSH Protocol is not set to 1
           -> Protocol 1 configured — SSH-1 is cryptographically broken and must not be used.

[lb-06.fleet.internal] score= 91.7/100  -> VIOLATIONS FOUND
    [FAIL] CIS-5.2.20   Ensure SSH access is limited via AllowUsers/AllowGroups
           -> No AllowUsers or AllowGroups configured — any account on the host can attempt SSH login.

[bastion-07.fleet.internal] score=  0.0/100  -> VIOLATIONS FOUND
    [FAIL] CIS-5.2.8 / CIS-5.2.10 / CIS-5.2.13 / CIS-5.2.2 / CIS-5.2.20  (fails every rule)

[monitor-08.fleet.internal] score= 66.7/100  -> VIOLATIONS FOUND
    [FAIL] CIS-5.2.8    PermitRootLogin is 'prohibit-password' — not fully disabled.
    [FAIL] CIS-5.2.20   AllowUsers/AllowGroups is set to a wildcard ('*').

[build-09.fleet.internal] score= 83.3/100  -> VIOLATIONS FOUND
    [FAIL] CIS-5.2.13   Weak/legacy cipher(s) configured: cast128-cbc.

[vpn-10 / mail-11 / storage-12 / auth-13 / backup-14 / dns-15] score=100.0/100 -> COMPLIANT (all)

==============================================================================
PHASE 2: authorized_keys hygiene audit (fleet-wide)
==============================================================================
Scanned 51 authorized_keys entries across the fleet.

Duplicate keys reused across accounts: 1
  [DUPLICATE] ssh-rsa oK1H252MCZUA...jed6Ck
      -> svc-backup@queue-04.fleet.internal
      -> operator@vpn-10.fleet.internal
      -> deploy@web-01.fleet.internal

Unlabeled keys (no owner identification): 2
  [UNLABELED] admin@lb-06.fleet.internal — ecdsa-sha2-nistp256 JMCivtAiunTV...6gCKAr
  [UNLABELED] ec2-user@mail-11.fleet.internal — ssh-rsa n2G+1bD9bBGa...NqiBJ3

==============================================================================
PHASE 3: fleet-wide summary
==============================================================================
Hosts audited:           15
Fully compliant hosts:   7/15
Average compliance score: 83.3/100

Worst offenders:
  1. bastion-07.fleet.internal score=  0.0  failed=[CIS-5.2.8, CIS-5.2.10, CIS-5.2.13, CIS-5.2.2, CIS-5.2.20]
  2. monitor-08.fleet.internal score= 66.7  failed=[CIS-5.2.8, CIS-5.2.20]
  3. db-02.fleet.internal     score= 75.0  failed=[CIS-5.2.8]

Key hygiene: 1 duplicate key group(s), 2 unlabeled key(s).

==============================================================================
REPORT WRITTEN
==============================================================================
Full remediation report written to: ssh_compliance_report.md
```

All 8 injected violations were caught: root login left open (db-02), password auth
left on (cache-03), weak ciphers mixed in (queue-04, build-09), Protocol 1 (app-05),
no access restriction (lb-06), a host failing every single rule (bastion-07), a
"prohibit-password" + wildcard `AllowUsers *` combination that looks compliant at a
glance but isn't (monitor-08), one reused key across 3 accounts, and 2 keys with no
owner comment.

`ssh_compliance_report.md` is generated fresh on every run with the fleet's current
per-host findings, worst-offenders table, exact `sshd_config` line fixes, `sed`
commands to apply them, and remediation for both key-hygiene findings.

## Things to try changing

- **Add a rule** — e.g. `MaxAuthTries` too high, or `X11Forwarding yes` — as a new
  function in `cis_ssh_benchmark.py` and add it to `BENCHMARK_RULES`; it will
  automatically show up in every host's audit and the weighted score.
- **Change `FLEET_SEED`** in `fleet_inventory.py` to get a different (but still
  reproducible) mix of hosts and violations, or increase `HOST_ROLES` to simulate
  a larger fleet.
- **Tune `SEVERITY_WEIGHT`** in `cis_ssh_benchmark.py` to make certain failures
  cost more or less toward the final score — try making a wildcard `AllowUsers *`
  count as `critical` instead of `medium`.
- **Add a third key-hygiene check** — e.g. flag keys using `ssh-rsa` (legacy) when
  `ssh-ed25519` should be the standard for new keys — as a new function in
  `key_hygiene_auditor.py`.
- **Point it at a real fleet** — swap `fleet_inventory.generate_fleet()` for a
  function that uses `paramiko.SSHClient.exec_command("cat /etc/ssh/sshd_config")`
  against a real host list (see Theory Ch.5's worked "fleet" example) and parse the
  returned text into the same host-record shape; the benchmark, auditor, and
  report generator all work unchanged since they only depend on the record's
  key/value shape.
