"""
auditor.py
===========
The scanning engine. Walks the simulated filesystem (filesystem_simulator.py)
and applies the policy (permission_rules.py), emitting structured Finding
records -- exactly the kind of output a real tool like Lynis, OpenSCAP, or a
custom `find / -perm ...` + `stat` script would produce, just against
simulated stat() data instead of a live disk.

Four categories of checks, matching the scenario brief:
  1. World-writable sensitive files/dirs (policy-forbidden-bit violations,
     generalized to also catch world-*readable* "private" dirs).
  2. SUID/SGID binaries outside the approved allowlist.
  3. Group-write privilege overlap: a file group-writable by a group that
     has no legitimate business with that resource.
  4. Directory permissions inconsistent with access-control intent (the
     "private" home dir that's actually world-readable) -- captured by
     check #1's generalized forbidden-bits logic for USER_HOME.
"""

from __future__ import annotations

import stat
from dataclasses import dataclass, field
from enum import Enum

from filesystem_simulator import FilesystemEntry, build_filesystem
import permission_rules as rules


class Severity(Enum):
    CRITICAL = "CRITICAL"
    HIGH = "HIGH"
    MEDIUM = "MEDIUM"
    LOW = "LOW"

    @property
    def rank(self) -> int:
        return {"CRITICAL": 0, "HIGH": 1, "MEDIUM": 2, "LOW": 3}[self.value]


@dataclass
class Finding:
    path: str
    issue_type: str
    severity: Severity
    current_mode: str          # octal, e.g. "0666"
    owner: str
    group: str
    is_dir: bool
    description: str
    recommended_mode: str | None = None   # octal target mode, if a chmod fixes it
    recommended_owner: str | None = None  # target owner/group, if a chown fixes it
    recommended_group: str | None = None

    def fix_commands(self) -> list[str]:
        cmds = []
        target = "/" if self.path == "" else self.path
        if self.recommended_mode is not None:
            cmds.append(f"chmod {self.recommended_mode} {target}")
        if self.recommended_owner is not None or self.recommended_group is not None:
            owner = self.recommended_owner or self.owner
            group = self.recommended_group or self.group
            cmds.append(f"chown {owner}:{group} {target}")
        return cmds


_BIT_LABELS = [
    (stat.S_IWOTH, "world-writable"),
    (stat.S_IROTH, "world-readable"),
    (stat.S_IXOTH, "world-executable"),
    (stat.S_IWGRP, "group-writable"),
]


def _violation_description(violation_bits: int) -> str:
    labels = [label for bit, label in _BIT_LABELS if violation_bits & bit]
    return ", ".join(labels) if labels else "non-compliant permission bits"


class Auditor:
    def __init__(self, entries: list[FilesystemEntry] | None = None):
        self.entries = entries if entries is not None else build_filesystem()
        self.findings: list[Finding] = []

    # ---- individual checks -------------------------------------------------

    def _check_policy_violation(self, e: FilesystemEntry) -> Finding | None:
        category = rules.classify(e.path)
        policy = rules.POLICIES[category]
        forbidden = policy.forbidden_bits_dir if e.is_dir else policy.forbidden_bits_file
        violation = e.perm_bits & forbidden
        if not violation:
            return None

        recommended = policy.recommended_dir_mode if e.is_dir else policy.recommended_file_mode
        desc_bits = _violation_description(violation)

        # Severity: system config / world-writable is always the worst case.
        if violation & stat.S_IWOTH and category == rules.Category.SYSTEM_CONFIG:
            severity = Severity.CRITICAL
        elif violation & stat.S_IWOTH:
            severity = Severity.HIGH
        elif category == rules.Category.USER_HOME:
            severity = Severity.HIGH  # a "private" dir leaking read/exec access
        else:
            severity = Severity.MEDIUM

        kind_word = "directory" if e.is_dir else "file"
        issue_type = "WORLD_WRITABLE" if violation & stat.S_IWOTH else "PERMISSION_POLICY_VIOLATION"

        return Finding(
            path=e.path,
            issue_type=issue_type,
            severity=severity,
            current_mode=e.octal_str(),
            owner=e.owner,
            group=e.group,
            is_dir=e.is_dir,
            description=(
                f"{category.name.replace('_', ' ').title()} {kind_word} is {desc_bits} "
                f"({e.symbolic}), violating: {policy.description}"
            ),
            recommended_mode=f"{recommended:04o}",
        )

    def _check_suid_sgid(self, e: FilesystemEntry) -> Finding | None:
        if not (e.is_suid or e.is_sgid):
            return None
        if e.path in rules.APPROVED_SUID_SGID_BINARIES:
            return None

        bit_name = "SUID" if e.is_suid else "SGID"
        if e.is_suid and e.is_sgid:
            bit_name = "SUID+SGID"

        # Recommended fix: strip the special bit(s), keep the base permission.
        stripped_mode = e.mode & 0o7777 & ~stat.S_ISUID & ~stat.S_ISGID
        return Finding(
            path=e.path,
            issue_type="UNAUTHORIZED_SUID_SGID",
            severity=Severity.CRITICAL,
            current_mode=e.octal_str(),
            owner=e.owner,
            group=e.group,
            is_dir=e.is_dir,
            description=(
                f"{bit_name} bit set on '{e.path}' (owner={e.owner}) but this binary is "
                f"NOT in the approved allowlist -- it would run with {e.owner}'s privileges "
                f"for any user who executes it. Classic privilege-escalation vector."
            ),
            recommended_mode=f"{stripped_mode:04o}",
        )

    def _check_group_overlap(self, e: FilesystemEntry) -> Finding | None:
        expected_group = rules.expected_group_for_shared_path(e.path)
        if expected_group is None:
            return None
        if not (e.perm_bits & stat.S_IWGRP):
            return None  # not group-writable at all -> no overlap risk
        if e.group == expected_group:
            return None  # group matches the resource's legitimate team

        return Finding(
            path=e.path,
            issue_type="GROUP_PRIVILEGE_OVERLAP",
            severity=Severity.HIGH,
            current_mode=e.octal_str(),
            owner=e.owner,
            group=e.group,
            is_dir=e.is_dir,
            description=(
                f"'{e.path}' is owned by '{e.owner}' but group-writable by '{e.group}', "
                f"an unrelated group with no legitimate claim on this resource "
                f"(expected group: '{expected_group}'). Any member of '{e.group}' can "
                f"modify data belonging to '{expected_group}'."
            ),
            recommended_group=expected_group,
        )

    # ---- orchestration -------------------------------------------------

    def run(self, on_finding=None) -> list[Finding]:
        """Walk every entry, apply every check, collect findings.

        If `on_finding` is given, it is called immediately for each finding
        as it's discovered (used by main.py to stream progress output).
        """
        self.findings = []
        checks = (self._check_policy_violation, self._check_suid_sgid, self._check_group_overlap)
        for entry in self.entries:
            for check in checks:
                finding = check(entry)
                if finding is not None:
                    self.findings.append(finding)
                    if on_finding is not None:
                        on_finding(finding)

        self.findings.sort(key=lambda f: (f.severity.rank, f.path))
        return self.findings

    def summary(self) -> dict[str, int]:
        counts = {sev.value: 0 for sev in Severity}
        for f in self.findings:
            counts[f.severity.value] += 1
        counts["TOTAL"] = len(self.findings)
        counts["ENTRIES_SCANNED"] = len(self.entries)
        return counts


if __name__ == "__main__":
    auditor = Auditor()
    findings = auditor.run(on_finding=lambda f: print(f"[{f.severity.value}] {f.path}: {f.issue_type}"))
    print("\nSummary:", auditor.summary())
