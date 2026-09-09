# Why OS-Level Logging Is a Security Topic, Not Just an Ops Topic

--> Every prior file in this folder covered a mechanism the OS provides (scheduling, memory, file systems, boot). Logging/auditing subsystems are how the OS RECORDS that those mechanisms were used, by whom, and when -- without this, an intrusion, a privilege escalation, or a misconfigured process leaves no evidence at all, and incident response becomes guesswork. Understanding these subsystems is the foundation the "Cyber Security" and "Detection" tracks build their monitoring/SIEM discussions on top of.

# syslog -- The Unix/Linux Standard

--> syslog is both a message FORMAT and a transport convention for log messages, originally standardized so any program (not just the OS kernel) could send a structured log entry to a central logging daemon rather than each writing its own ad hoc log file.
--> Every syslog message carries a **facility** (a rough category of source -- `kern` for kernel messages, `auth`/`authpriv` for authentication events, `cron`, `mail`, `daemon`, `local0`-`local7` for custom application use) and a **severity level**, ranked from most to least critical: `emerg`, `alert`, `crit`, `err`, `warning`, `notice`, `info`, `debug`.
--> Modern Linux systems typically run `rsyslog` or `journald` (systemd's own binary logging system, referenced in the systemd Deep Dive of `01 Processes, Threads and the OS Kernel.md`) as the actual daemon collecting these messages -- `journald` stores structured, indexed binary logs queried with `journalctl`, while classic syslog implementations write plain text to files under `/var/log/` (`/var/log/auth.log`, `/var/log/syslog`, etc.).

```bash
# journald: view only authentication-related log entries from the last boot
journalctl -u ssh --since today

# classic syslog: the authentication log is exactly where failed SSH logins show up
grep "Failed password" /var/log/auth.log
```

--> **Remote syslog forwarding** -- because an attacker who fully compromises a host can simply delete or tamper with its local logs to erase evidence, security-conscious setups forward syslog messages OFF the host, in real time, to a centralized, separately-secured log server (or a SIEM ingesting via syslog) -- so even if the source host is later wiped or its local logs are destroyed, the forwarded copy already exists elsewhere, immune to changes made on the compromised machine after the fact.

# auditd -- Linux's Fine-Grained Audit Framework

--> syslog records what applications choose to REPORT; `auditd` operates at a fundamentally different level -- it hooks directly into the Linux kernel's audit subsystem to record specific SYSTEM CALLS (the same system call interface covered in `03 File Systems, Permissions and System Calls.md`) as they actually happen, regardless of whether the application involved does any logging of its own at all.
--> Rules are defined with `auditctl` (or persisted in `/etc/audit/rules.d/`), specifying exactly which syscalls, files, or event types to watch:

```bash
# Watch for any write/attribute-change access to a sensitive file, tagged for easy searching later
auditctl -w /etc/passwd -p wa -k passwd_changes

# Watch every invocation of the execve syscall (i.e. log every program execution system-wide)
auditctl -a always,exit -F arch=b64 -S execve -k process_execution
```

--> Recorded events land in `/var/log/audit/audit.log` and are queried with `ausearch` (e.g. `ausearch -k passwd_changes` to pull every event tagged with that key) or summarized with `aureport` -- this level of detail is exactly what compliance frameworks (PCI-DSS, HIPAA, and similar) typically mandate: not just "did something go wrong" but a durable, queryable record of who executed what, touched which files, and when, at the kernel level where an attacker cannot simply avoid triggering it by being quiet at the application layer.
--> Because `auditd` operates below the application layer, it can catch activity that a compromised or malicious application would otherwise be able to hide by simply not writing its own logs -- e.g. a webshell dropped by an attacker won't voluntarily write "I just executed a reverse shell" to any application log, but `auditd`'s `execve` watch will still see the actual process creation regardless.

# Windows Event Log

--> Windows' equivalent centralized logging facility, organized into distinct logs by category -- **Security** (logons/logoffs, privilege use, object access -- the closest analog to `auditd`'s coverage), **System** (driver/service/OS-level events), **Application** (individual programs' own logged events), plus additional logs for specific server roles.
--> Each entry has an **Event ID** -- a numeric code identifying exactly what kind of event occurred, which security analysts learn to recognize directly (Event ID 4624 = successful logon, 4625 = failed logon, 4720 = a new user account was created, 4732 = a member was added to a security-enabled local group) -- these specific IDs are exactly what SIEM detection rules for Windows environments are written against, since matching on the numeric Event ID is far more reliable than parsing free-text messages that can vary across Windows versions/languages.
--> Viewed interactively via `eventvwr.msc` (Event Viewer) or queried from the command line/scripts with PowerShell:

```powershell
# Pull the 20 most recent failed-logon events (Event ID 4625) from the Security log
Get-WinEvent -FilterHashtable @{LogName='Security'; Id=4625} -MaxEvents 20
```

--> What auditing actually GETS RECORDED is itself configurable via Windows' **Audit Policy** (`auditpol.exe`, or Group Policy) -- by default many detailed object-access/privilege-use categories are NOT logged out of the box, so a freshly installed Windows system can have surprisingly thin security-relevant logs until an administrator deliberately enables the categories a security program actually needs (a common, easily-missed hardening gap in real deployments).

# Common Threads Across All Three

--> **Log tampering is the attacker's natural next move.** Once a system is compromised, an attacker with sufficient privilege can typically clear or edit the Windows Security log, disable/stop `auditd`, or delete syslog files outright -- which is exactly why forwarding logs to a separate, independently-secured destination (a central syslog server, a SIEM, a Windows Event Forwarding collector) is treated as a baseline control rather than an optional extra: LOCAL logs on a host that might itself become compromised are not trustworthy evidence on their own once that host is under attacker control.
--> **Volume vs signal.** All three subsystems can generate enormous volumes of low-value routine noise if configured too broadly (auditing every single file read system-wide, for instance) -- the practical skill is scoping rules/policies to the events that actually matter for detection (privilege escalation, unexpected process execution, access to specific sensitive files/accounts) rather than logging indiscriminately and drowning genuine signal in noise nobody will ever review.
--> **Time synchronization matters more than it seems.** Correlating events ACROSS multiple hosts/logs (was this Windows logon on the domain controller caused by that Linux server's process at roughly the same moment) is only possible if all systems' clocks agree closely -- which is why NTP (Network Time Protocol) accuracy is treated as a security-relevant baseline requirement, not just a convenience, in any environment that expects to actually use its logs for incident investigation.

# Cross-References

--> This file connects to `01 Processes, Threads and the OS Kernel.md` (systemd/journald), `03 File Systems, Permissions and System Calls.md` (the system call layer `auditd` hooks into and the permissions governing who can read/tamper with log files themselves), and sets up the monitoring/SIEM material covered later in the Cyber Security track -- these OS-level logs are the raw material a SIEM ingests, correlates, and alerts on.
