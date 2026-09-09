"""
filesystem_simulator.py
========================
Builds a synthetic Linux-style filesystem permission table -- no real disk
access. Every entry is a structured record describing exactly what
`os.stat()` + `os.lstat()` would tell a real auditing tool: path, owner,
group, octal mode (including the SUID/SGID/sticky bits), and whether the
entry is a directory.

Why simulate instead of scanning the real disk?
  - Fully offline/self-contained and reproducible (fixed seed).
  - Cross-platform: this project runs identically on Windows, macOS, Linux,
    since it never calls os.stat()/os.chmod() against a real POSIX volume.
  - Deterministic misconfigurations mean we can prove the auditor actually
    finds every planted bug -- important for testing a security tool.

The tree models a small multi-user org server:
    /etc/...                 -- system-wide config (root-owned, sensitive)
    /usr/bin, /usr/local/bin  -- binaries, some legitimately SUID/SGID
    /home/<user>/...          -- per-user home directories ("private" intent)
    /srv/shared/<team>/...    -- shared group-collaboration directories
    /var/log/...              -- log files

A FilesystemEntry uses the same bit layout as Python's `stat` module so the
auditor can reuse the real stdlib constants (stat.S_IWOTH, stat.S_ISUID, ...)
against simulated data exactly as it would against real os.stat() results.
"""

from __future__ import annotations

import random
import stat
from dataclasses import dataclass, field


SEED = 1337  # fixed seed -> same synthetic tree + same injected bugs every run


@dataclass
class FilesystemEntry:
    path: str
    owner: str
    group: str
    mode: int  # full mode bits, e.g. stat.S_IFREG | 0o644 | stat.S_ISUID
    is_dir: bool = False

    @property
    def perm_bits(self) -> int:
        """The rwx-for-owner/group/other portion only (low 9 bits)."""
        return self.mode & 0o777

    @property
    def is_suid(self) -> bool:
        return bool(self.mode & stat.S_ISUID)

    @property
    def is_sgid(self) -> bool:
        return bool(self.mode & stat.S_ISGID)

    @property
    def symbolic(self) -> str:
        """Render like `ls -l`, e.g. 'drwxr-xr-x' or '-rwsr-xr-x'."""
        kind = "d" if self.is_dir else "-"
        return kind + stat.filemode(self.mode)[1:]

    def octal_str(self) -> str:
        """4-digit octal including the special-bits digit, e.g. '4755'."""
        special = 0
        if self.mode & stat.S_ISUID:
            special |= 4
        if self.mode & stat.S_ISGID:
            special |= 2
        if self.mode & stat.S_ISVTX:
            special |= 1
        return f"{special}{self.perm_bits:03o}"


def _mk(path, owner, group, perm, is_dir=False, suid=False, sgid=False, sticky=False):
    mode = (stat.S_IFDIR if is_dir else stat.S_IFREG) | perm
    if suid:
        mode |= stat.S_ISUID
    if sgid:
        mode |= stat.S_ISGID
    if sticky:
        mode |= stat.S_ISVTX
    return FilesystemEntry(path=path, owner=owner, group=group, mode=mode, is_dir=is_dir)


USERS = ["alice", "bob", "carol", "dave", "erin", "frank"]
TEAMS = ["engineering", "finance", "interns"]


def build_filesystem() -> list[FilesystemEntry]:
    """Returns a deterministic list of FilesystemEntry records: a healthy
    baseline plus a fixed set of deliberately injected misconfigurations."""
    rng = random.Random(SEED)
    entries: list[FilesystemEntry] = []

    # ---- 1. System configuration (root-owned, should never be world-writable) --
    entries += [
        _mk("/etc", "root", "root", 0o755, is_dir=True),
        _mk("/etc/passwd", "root", "root", 0o644),
        _mk("/etc/shadow", "root", "shadow", 0o640),
        _mk("/etc/sudoers", "root", "root", 0o440),
        _mk("/etc/ssh/sshd_config", "root", "root", 0o644),
        _mk("/etc/hosts", "root", "root", 0o644),
        # --- INJECTED BUG #1: world-writable sensitive system file ---
        _mk("/etc/cron.d/backup-job", "root", "root", 0o666),
    ]

    # ---- 2. Binaries: a mix of legitimate SUID/SGID tools + rogue ones ----
    entries += [
        _mk("/usr/bin/passwd", "root", "root", 0o755, suid=True),   # legit, allowlisted
        _mk("/usr/bin/sudo", "root", "root", 0o755, suid=True),     # legit, allowlisted
        _mk("/usr/bin/mount", "root", "root", 0o755, suid=True),    # legit, allowlisted
        _mk("/usr/bin/wall", "root", "tty", 0o755, sgid=True),      # legit, allowlisted
        _mk("/usr/bin/ls", "root", "root", 0o755),
        _mk("/usr/bin/cat", "root", "root", 0o755),
        _mk("/usr/bin/bash", "root", "root", 0o755),
        # --- INJECTED BUG #2: unexpected/unapproved SUID binary ---
        _mk("/usr/local/bin/legacy-report-tool", "dave", "engineering", 0o755, suid=True),
    ]

    # ---- 3. Home directories: expected to be private (owner rwx only) ----
    for user in USERS:
        # --- INJECTED BUG #3: carol's "private" home dir is world-readable,
        #     e.g. from an over-broad `chmod -R 755 /home` some time ago. ---
        home_perm = 0o755 if user == "carol" else 0o750
        entries.append(_mk(f"/home/{user}", user, user, home_perm, is_dir=True))
        entries.append(_mk(f"/home/{user}/.bashrc", user, user, 0o644))
        entries.append(_mk(f"/home/{user}/.ssh", user, user, 0o700, is_dir=True))
        entries.append(_mk(f"/home/{user}/.ssh/id_rsa", user, user, 0o600))
        entries.append(_mk(f"/home/{user}/notes.txt", user, user, 0o644))

    # ---- 4. Shared/group collaboration directories ----
    for team in TEAMS:
        entries.append(_mk(f"/srv/shared/{team}", "root", team, 0o770, is_dir=True))
        entries.append(_mk(f"/srv/shared/{team}/README.md", "root", team, 0o660))

    entries.append(_mk("/srv/shared/engineering/deploy_keys.pem", "alice", "engineering", 0o640))
    entries.append(_mk("/srv/shared/finance/payroll_2026.csv", "bob", "finance", 0o640))

    # --- INJECTED BUG #4: file owned by one user, but group-writable by an
    #     unrelated group -- a classic privilege-overlap misconfiguration
    #     (finance payroll data group-writable by the interns group). ---
    entries.append(_mk("/srv/shared/finance/bonus_plan.xlsx", "bob", "interns", 0o660))

    # ---- 5. Logs (append-only in spirit; world-writable would be bad) ----
    entries += [
        _mk("/var/log", "root", "root", 0o755, is_dir=True),
        _mk("/var/log/auth.log", "root", "adm", 0o640),
        _mk("/var/log/syslog", "root", "adm", 0o640),
        _mk("/var/log/app", "root", "adm", 0o750, is_dir=True),
    ]

    # ---- 6. A pile of routine, healthy noise so the auditor has to sift
    #         signal from a realistically sized tree (not just the bugs). ----
    for i in range(20):
        owner = rng.choice(USERS)
        entries.append(_mk(f"/home/{owner}/project_{i}.py", owner, owner, 0o644))

    return entries


if __name__ == "__main__":
    fs = build_filesystem()
    print(f"Simulated filesystem: {len(fs)} entries (seed={SEED})\n")
    for e in fs[:15]:
        print(f"{e.symbolic}  {e.owner:<8} {e.group:<10} {e.octal_str()}  {e.path}")
    print("...")
