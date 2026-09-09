# Multi-User Linux File Permission & Access Control Audit Report

Generated: 2026-08-17 05:13 UTC  
Entries scanned: 78  
Total findings: 4

## Severity Breakdown

| Severity | Count |
|----------|-------|
| 🔴 CRITICAL | 2 |
| 🟠 HIGH | 2 |
| 🟡 MEDIUM | 0 |
| 🟢 LOW | 0 |

## Issue Type Breakdown

| Issue Type | Count |
|------------|-------|
| WORLD_WRITABLE | 1 |
| UNAUTHORIZED_SUID_SGID | 1 |
| PERMISSION_POLICY_VIOLATION | 1 |
| GROUP_PRIVILEGE_OVERLAP | 1 |

## Prioritized Findings (most severe first)

### 1. 🔴 [CRITICAL] `/etc/cron.d/backup-job`

- **Issue type:** WORLD_WRITABLE
- **Kind:** File
- **Owner:group:** `root:root`
- **Current mode:** `0666`
- **Finding:** System Config file is world-writable, group-writable (-rw-rw-rw-), violating: System configuration must never be group- or world-writable.
- **Fix:**

  ```bash
    chmod 0644 /etc/cron.d/backup-job
  ```

### 2. 🔴 [CRITICAL] `/usr/local/bin/legacy-report-tool`

- **Issue type:** UNAUTHORIZED_SUID_SGID
- **Kind:** File
- **Owner:group:** `dave:engineering`
- **Current mode:** `4755`
- **Finding:** SUID bit set on '/usr/local/bin/legacy-report-tool' (owner=dave) but this binary is NOT in the approved allowlist -- it would run with dave's privileges for any user who executes it. Classic privilege-escalation vector.
- **Fix:**

  ```bash
    chmod 0755 /usr/local/bin/legacy-report-tool
  ```

### 3. 🟠 [HIGH] `/home/carol`

- **Issue type:** PERMISSION_POLICY_VIOLATION
- **Kind:** Directory
- **Owner:group:** `carol:carol`
- **Current mode:** `0755`
- **Finding:** User Home directory is world-readable, world-executable (drwxr-xr-x), violating: Home directories are private: the directory itself blocks all 'other' access.
- **Fix:**

  ```bash
    chmod 0750 /home/carol
  ```

### 4. 🟠 [HIGH] `/srv/shared/finance/bonus_plan.xlsx`

- **Issue type:** GROUP_PRIVILEGE_OVERLAP
- **Kind:** File
- **Owner:group:** `bob:interns`
- **Current mode:** `0660`
- **Finding:** '/srv/shared/finance/bonus_plan.xlsx' is owned by 'bob' but group-writable by 'interns', an unrelated group with no legitimate claim on this resource (expected group: 'finance'). Any member of 'interns' can modify data belonging to 'finance'.
- **Fix:**

  ```bash
    chown bob:finance /srv/shared/finance/bonus_plan.xlsx
  ```

## All Remediation Commands (copy/paste block)

```bash
chmod 0644 /etc/cron.d/backup-job
chmod 0755 /usr/local/bin/legacy-report-tool
chmod 0750 /home/carol
chown bob:finance /srv/shared/finance/bonus_plan.xlsx
```
