# Active Directory Attack Simulation Lab -- Kerberoasting and Pass-the-Hash Detection

A self-contained, offline Python lab that models two classic Active
Directory attack techniques and a detection layer for both, entirely
as labeled in-memory data structures. **There is no real Kerberos or
NTLM protocol implementation here, no real network traffic, no real
cryptography, and no connection to any real domain or machine.**
Everything -- the domain, the accounts, the "tickets", the "hashes",
the "cracking" -- is a simulated stand-in built for the educational
purpose of understanding *why* these attacks work and *what a real
detection rule looks for*.

> Legal/ethical note: this project performs no actual attack against
> anything. It is a data-structure model intended to teach the logic
> behind Kerberoasting, Pass-the-Hash, and their detections, for use
> in authorized lab/study contexts only.

## Real-world scenario

In a real enterprise Active Directory domain:

- **Kerberoasting** (MITRE ATT&CK
  [T1558.003](https://attack.mitre.org/techniques/T1558/003/)): any
  authenticated domain user -- even a low-privilege one -- can ask the
  Key Distribution Center (KDC) for a service ticket (TGS) for *any*
  account that has a Service Principal Name (SPN) registered, such as
  a SQL Server or IIS service account. That ticket is encrypted with
  the target service account's own password hash. The KDC does not
  check whether the requester has any special right to that service
  before handing out the ticket. An attacker collects tickets for
  every SPN account they can find, takes them offline, and cracks the
  encrypted portion with hashcat/John -- no domain lockout policy
  applies because the KDC is never contacted again after the ticket
  is issued.
- **Pass-the-Hash (PtH)**: NTLM authentication is a challenge-response
  scheme that only requires the account's NTLM *hash*, not the
  plaintext password. An attacker who dumps a hash from `lsass.exe`
  memory, the SAM database, or NTDS.dit can authenticate as that
  account on other machines by presenting the hash directly --
  cracking it is never necessary. Because sysadmins often reuse a
  local admin password/hash across many machines, one dumped hash can
  let an attacker pivot host to host across the network (`psexec.py
  -hashes`, `crackmapexec -H <hash>`).
- Both techniques leave detectable log signatures: Kerberoasting shows
  up as a burst of TGS-REQ requests (Windows Event ID 4769) for many
  distinct SPNs from one account in a short window; Pass-the-Hash
  shows up as one account authenticating via NTLM (Event ID 4624,
  `AuthenticationPackageName=NTLM`) to an abnormal number of distinct
  hosts in a short window -- the real lateral-movement indicator that
  tools like Microsoft Defender for Identity are built to catch.

This lab models the attacker side (requesting tickets, checking
crackability, passing a hash) and the defender side (the two
detection rules) as separate, inspectable Python modules.

## Architecture

| Module | Role in this lab | Real-world equivalent |
|---|---|---|
| `ad_directory_simulator.py` | Builds an in-memory AD domain: human users, SPN-bearing service accounts (some weak, some strong simulated passwords), and machine accounts | A real Active Directory domain's `NTDS.dit` database / directory structure |
| `kerberoasting_sim.py` | Simulates an attacker requesting a TGS "ticket" for every SPN account, plus a simulated offline-crack-feasibility check per account | The real Kerberoasting technique, MITRE ATT&CK [T1558.003](https://attack.mitre.org/techniques/T1558/003/), performed with tools like Impacket's `GetUserSPNs.py` + `hashcat -m 13100` |
| `pass_the_hash_sim.py` | Simulates an attacker reusing one stolen account hash to authenticate to many machine accounts | The real Pass-the-Hash technique, MITRE ATT&CK [T1550.002](https://attack.mitre.org/techniques/T1550/002/), performed with `psexec.py -hashes` or `crackmapexec -H <hash>` |
| `detection_engine.py` | Two correlation rules: abnormal distinct-SPN request volume from one source (Kerberoasting), and one hash used across abnormally many distinct hosts (Pass-the-Hash lateral movement) | A SIEM's AD attack-detection content, e.g. Microsoft Defender for Identity / Sentinel analytics rules over Windows Security Event Log (4769, 4624) |
| `main.py` | Orchestrates: builds the domain, runs both attack simulations, runs both detectors, prints a full report and hardening lessons | A tabletop / purple-team exercise walking through attack -> log -> detection |

## Run it

Requires only the Python standard library (Python 3.10+ for the
`X | None` type hints).

```bash
cd "3) Security/5) Ethical Hacking/Projects/1) Active Directory Attack Simulation Lab - Kerberoasting and Pass-the-Hash Detection"
python main.py
```

Each module is also independently runnable for a smaller, focused demo:

```bash
python ad_directory_simulator.py   # just print the simulated domain
python kerberoasting_sim.py        # just run the Kerberoasting sim
python pass_the_hash_sim.py        # just run the Pass-the-Hash sim
```

## Verified result (actual output)

Run on 2026-08-17 with `python main.py`:

```
==============================================================================
ACTIVE DIRECTORY ATTACK SIMULATION LAB
==============================================================================
(Simulated domain, simulated attacks, simulated detection --
 no real Kerberos/NTLM traffic, no real cryptography, no real network.)

Domain: corp.local
  Users:            ['alice', 'bob', 'carol']
  SPN service accts:['svc_sql', 'svc_web', 'svc_backup', 'svc_gmsa_reporting', 'svc_app_gateway']
  Machine accounts: ['sql01.corp.local', 'web01.corp.local', 'backup01.corp.local', 'fileserver01.corp.local', 'hr-ws07.corp.local', 'fin-ws12.corp.local', 'dev-ws03.corp.local', 'dc01.corp.local']

==============================================================================
ATTACK SIMULATION 1: KERBEROASTING (T1558.003)
==============================================================================
Attacker account (low-privilege, valid domain creds): 'alice'
Requesting a simulated TGS service ticket for every SPN account...

5 simulated service-ticket requests logged:
  09:00:00  SIM-TGS-0000  requester=alice    SPN=MSSQLSvc/sql01.corp.local:1433
  09:00:02  SIM-TGS-0001  requester=alice    SPN=HTTP/web01.corp.local
  09:00:04  SIM-TGS-0002  requester=alice    SPN=HOST/backup01.corp.local
  09:00:06  SIM-TGS-0003  requester=alice    SPN=MSSQLSvc/reportsrv.corp.local:1433
  09:00:08  SIM-TGS-0004  requester=alice    SPN=HTTP/gateway.corp.local

Simulated offline-crack-feasibility check per SPN account:
  [CRACKED ] svc_sql                CRACKABLE (base word matches simulated dictionary attack, simulated entropy 59.5 bits) -- a real hashcat dictionary+rules pass (e.g. rockyou.txt + best64.rule) would crack this near-instantly regardless of raw entropy
  [CRACKED ] svc_web                CRACKABLE (base word matches simulated dictionary attack, simulated entropy 53.6 bits) -- a real hashcat dictionary+rules pass (e.g. rockyou.txt + best64.rule) would crack this near-instantly regardless of raw entropy
  [CRACKED ] svc_backup             CRACKABLE (base word matches simulated dictionary attack, simulated entropy 46.5 bits) -- a real hashcat dictionary+rules pass (e.g. rockyou.txt + best64.rule) would crack this near-instantly regardless of raw entropy
  [safe    ] svc_gmsa_reporting     NOT crackable in a reasonable offline session (no dictionary match, simulated entropy 170.4 bits >= 45-bit threshold)
  [safe    ] svc_app_gateway        NOT crackable in a reasonable offline session (no dictionary match, simulated entropy 177.0 bits >= 45-bit threshold)

==============================================================================
ATTACK SIMULATION 2: PASS-THE-HASH
==============================================================================
Attacker has obtained a simulated NTLM HASH (never a plaintext password) for: 'svc_backup'
Authenticating to every machine account using only the hash...

8 simulated NTLM authentication events logged:
  09:00:00  svc_backup -> sql01.corp.local         auth=NTLM  success=True
  09:00:05  svc_backup -> web01.corp.local         auth=NTLM  success=True
  09:00:10  svc_backup -> backup01.corp.local      auth=NTLM  success=True
  09:00:15  svc_backup -> fileserver01.corp.local  auth=NTLM  success=True
  09:00:20  svc_backup -> hr-ws07.corp.local       auth=NTLM  success=True
  09:00:25  svc_backup -> fin-ws12.corp.local      auth=NTLM  success=True
  09:00:30  svc_backup -> dev-ws03.corp.local      auth=NTLM  success=True
  09:00:35  svc_backup -> dc01.corp.local          auth=NTLM  success=True

==============================================================================
DETECTION ENGINE: ANALYZING EVENT LOGS
==============================================================================
Kerberoasting detector (abnormal distinct-SPN volume, one source, short window):
  [ALERT:HIGH] KERBEROASTING_ABNORMAL_TGS_VOLUME
    Account 'alice' requested service tickets for 5 distinct SPNs within 8s (threshold: >3). Real equivalent: Event ID 4769 burst -- classic Kerberoasting enumeration sweep.

Pass-the-Hash detector (one hash reused across abnormally many distinct hosts, short window):
  [ALERT:CRITICAL] PASS_THE_HASH_LATERAL_MOVEMENT
    Account 'svc_backup' authenticated via NTLM to 8 distinct hosts within 35s (threshold: >3). Real equivalent: one NTLM hash reused network-wide -- classic Pass-the-Hash lateral movement.

==============================================================================
SUMMARY
==============================================================================
Kerberoasting results:
  3 of 5 SPN service accounts would realistically be CRACKED offline:
    - svc_sql
    - svc_web
    - svc_backup

Detection results:
  Kerberoasting sweep flagged:   YES
  Pass-the-Hash lateral move flagged: YES

AD hardening lessons:
  1. SPN account password strength: any account with a Service
     Principal Name is a Kerberoasting target the moment ANY valid
     domain credential exists -- no special privilege is needed to
     request its service ticket. Short/dictionary-style passwords
     (like 'Password1' or 'Summer2023' above) fall to offline
     cracking in minutes once the ticket is extracted. Fix: use
     25+ character random passwords for service accounts, or better,
     Group Managed Service Accounts (gMSA) which rotate automatically
     and are never human-typed, plus enforce AES-only Kerberos
     encryption so RC4 tickets (much weaker to crack) aren't issued.
  2. Hash-reuse-across-hosts is detectable: Pass-the-Hash requires
     no cracking, which is what makes it dangerous -- but it leaves
     a distinctive log signature. One account authenticating via
     NTLM to many distinct hosts within a short window is not
     normal human behavior; it's the signature of a hash being
     sprayed network-wide (crackmapexec/psexec -hashes). Fix: unique
     per-machine local admin passwords (LAPS) so one dumped hash
     only ever works on one host, plus Credential Guard and
     disabling NTLM where Kerberos-only is viable.
```

Both attacks ran end to end, the weak SPN accounts (`svc_sql`,
`svc_web`, `svc_backup`) were correctly identified as crackable while
the strong/gMSA-style accounts were not, and both detection rules
fired on the simulated attacker activity.

## Things to try changing

- **Strengthen all SPN account passwords** in
  `ad_directory_simulator.py` (`build_lab_domain()`) to long random
  strings like the `svc_gmsa_reporting`/`svc_app_gateway` examples,
  then re-run `python main.py`. The crackability check should report
  **zero** crackable accounts (verified: setting every
  `ServiceAccount.simulated_password` to a 27-character random string
  makes `assess_crack_feasibility()` return `crackable=False` for all
  five accounts).
- **Lower `distinct_spn_threshold` in `detect_kerberoasting()`** (e.g.
  to `1`) to see how sensitive the detector's false-positive rate
  becomes -- a legitimate admin who touches two services in a session
  would now also get flagged.
- **Spread out the simulated ticket requests** in
  `simulate_kerberoasting()` (increase `seconds_between_requests` to,
  say, 120) so they fall outside the detector's 5-minute window, and
  observe the Kerberoasting alert disappear -- illustrating why
  window size is a critical, tunable part of a real detection rule.
- **Give the Pass-the-Hash attacker fewer target hosts** (pass
  `target_hosts=[...]` with 2-3 hostnames into
  `simulate_pass_the_hash()`) to see the lateral-movement alert stop
  firing once the distinct-host count drops below the threshold.
- **Add a new weak SPN account** with a password like `"Winter2024"`
  to `ad_directory_simulator.py` and confirm the dictionary-pattern
  check in `kerberoasting_sim.py` (`matches_dictionary_pattern`)
  correctly flags it via the simulated wordlist.
