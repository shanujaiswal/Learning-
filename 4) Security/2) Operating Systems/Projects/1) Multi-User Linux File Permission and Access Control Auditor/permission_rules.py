"""
permission_rules.py
=====================
The audit *policy* -- deliberately separated from the *mechanism*
(auditor.py) so the rules can be reviewed, tightened, or handed to a
compliance team without touching scanning logic. This mirrors how real
tools like Lynis / OpenSCAP / CIS benchmark scripts ship a rules file
(YAML/XML) independent of the scanning engine.

Covers:
  - SUID/SGID allowlist: the only binaries permitted to run with elevated
    privileges (theory: "SUID bit -- runs with the OWNER's privileges").
  - Per-path-category safe-permission ceilings: system config, user home,
    shared/group directories, logs.
  - Helper to classify an arbitrary path into one of those categories.
"""

from __future__ import annotations

import stat
from dataclasses import dataclass
from enum import Enum, auto


# --------------------------------------------------------------------------
# 1. SUID / SGID allowlist
# --------------------------------------------------------------------------
# Any file found with the SUID or SGID bit set that is NOT in this set is
# flagged -- this is the exact "find / -perm -4000" recon step from the
# theory file, but applied defensively instead of offensively.
APPROVED_SUID_SGID_BINARIES = {
    "/usr/bin/passwd",
    "/usr/bin/sudo",
    "/usr/bin/mount",
    "/usr/bin/umount",
    "/usr/bin/su",
    "/usr/bin/wall",
    "/usr/bin/ping",
}


# --------------------------------------------------------------------------
# 2. Path categories
# --------------------------------------------------------------------------
class Category(Enum):
    SYSTEM_CONFIG = auto()
    BINARY = auto()
    USER_HOME = auto()
    SHARED_GROUP = auto()
    LOG = auto()
    OTHER = auto()


def classify(path: str) -> Category:
    """Categorize a path by its position in the tree -- a stand-in for the
    kind of path-prefix rules a real config-management/audit tool uses."""
    if path.startswith("/etc"):
        return Category.SYSTEM_CONFIG
    if path.startswith("/usr/bin") or path.startswith("/usr/local/bin") or path.startswith("/bin"):
        return Category.BINARY
    if path.startswith("/home/"):
        return Category.USER_HOME
    if path.startswith("/srv/shared"):
        return Category.SHARED_GROUP
    if path.startswith("/var/log"):
        return Category.LOG
    return Category.OTHER


# --------------------------------------------------------------------------
# 3. Safe-permission ceilings per category
# --------------------------------------------------------------------------
@dataclass(frozen=True)
class PermissionPolicy:
    """Describes the *maximum* permissiveness allowed for a category.

    Forbidden bits are expressed separately for files and directories
    because the same category can reasonably differ -- e.g. a private
    home *directory* must block all "other" access (rwx) so nobody can
    even list or traverse into it, while a dotfile inside it being
    world-*readable* is a normal, low-risk Linux default. Any bit set in
    an entry's mode that matches the relevant forbidden mask is a
    violation.
    """
    forbidden_bits_file: int     # bits that must never be set on regular files
    forbidden_bits_dir: int      # bits that must never be set on directories
    recommended_file_mode: int   # the mode we suggest restoring for files
    recommended_dir_mode: int    # the mode we suggest restoring for directories
    description: str


POLICIES: dict[Category, PermissionPolicy] = {
    Category.SYSTEM_CONFIG: PermissionPolicy(
        forbidden_bits_file=stat.S_IWOTH | stat.S_IWGRP,   # no group/other write, ever
        forbidden_bits_dir=stat.S_IWOTH | stat.S_IWGRP,
        recommended_file_mode=0o644,
        recommended_dir_mode=0o755,
        description="System configuration must never be group- or world-writable.",
    ),
    Category.BINARY: PermissionPolicy(
        forbidden_bits_file=stat.S_IWOTH | stat.S_IWGRP,
        forbidden_bits_dir=stat.S_IWOTH | stat.S_IWGRP,
        recommended_file_mode=0o755,
        recommended_dir_mode=0o755,
        description="Binaries must not be writable by group/other; SUID/SGID must be allowlisted.",
    ),
    Category.USER_HOME: PermissionPolicy(
        # Files: the classic Linux default (dotfiles/documents world-READABLE
        # is normal) -- only writes are dangerous.
        forbidden_bits_file=stat.S_IWOTH | stat.S_IWGRP,
        # Directory: a *private* home directory must block ALL "other"
        # access (r, w, AND x) so outsiders can't even list or traverse it,
        # plus no group-write.
        forbidden_bits_dir=stat.S_IROTH | stat.S_IWOTH | stat.S_IXOTH | stat.S_IWGRP,
        recommended_file_mode=0o640,
        recommended_dir_mode=0o750,
        description="Home directories are private: the directory itself blocks all 'other' access.",
    ),
    Category.SHARED_GROUP: PermissionPolicy(
        # Group collaboration dirs: group rw is fine and expected, but never
        # world-writable, and the *intended* group must match the resource.
        forbidden_bits_file=stat.S_IWOTH,
        forbidden_bits_dir=stat.S_IWOTH,
        recommended_file_mode=0o660,
        recommended_dir_mode=0o770,
        description="Shared/group directories allow group read-write, never world-write.",
    ),
    Category.LOG: PermissionPolicy(
        forbidden_bits_file=stat.S_IWOTH | stat.S_IWGRP,
        forbidden_bits_dir=stat.S_IWOTH | stat.S_IWGRP,
        recommended_file_mode=0o640,
        recommended_dir_mode=0o750,
        description="Logs must not be writable by group/other (tamper protection).",
    ),
    Category.OTHER: PermissionPolicy(
        forbidden_bits_file=stat.S_IWOTH,
        forbidden_bits_dir=stat.S_IWOTH,
        recommended_file_mode=0o644,
        recommended_dir_mode=0o755,
        description="Default baseline: never world-writable.",
    ),
}


# --------------------------------------------------------------------------
# 4. Ownership / group-overlap policy
# --------------------------------------------------------------------------
# Which groups are the *legitimate* collaborators for each shared directory
# tree. A file under /srv/shared/finance/... that is group-writable by any
# group NOT in this set for that team is a privilege-overlap finding.
SHARED_DIR_OWNING_GROUP = {
    "/srv/shared/engineering": "engineering",
    "/srv/shared/finance": "finance",
    "/srv/shared/interns": "interns",
}


def expected_group_for_shared_path(path: str) -> str | None:
    for prefix, group in SHARED_DIR_OWNING_GROUP.items():
        if path.startswith(prefix):
            return group
    return None
