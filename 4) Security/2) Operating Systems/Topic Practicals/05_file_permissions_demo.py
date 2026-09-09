"""
05 - File Permissions Demo
=============================
Theory chapter: "03 File Systems, Permissions and System Calls.md"

Demonstrates the POSIX permission-bit model using `os.chmod` / `stat`:
  - creates a real file,
  - inspects its permission bits with os.stat(),
  - changes them with os.chmod(),
  - programmatically checks the read/write/execute bits for owner/group/other.

IMPORTANT cross-platform note:
  Windows does NOT use the POSIX rwx-for-owner/group/other bit model at the
  filesystem level -- it uses ACLs (Access Control Lists) with much richer,
  per-user/per-group permission entries, managed via `icacls` / the security
  tab, not chmod. Python's `os.chmod` on Windows only emulates a small
  subset of this: it can really only toggle the read-only attribute (clearing
  stat.S_IWRITE removes write access for everyone; write bits for
  group/other are not meaningfully separate the way they are on Linux/macOS).
  So on Windows, this script's chmod calls will "work" (no exception) but
  the resulting permission bits you read back will not distinguish
  owner/group/other the way a real POSIX filesystem would -- that distinction
  is only fully meaningful on Linux/macOS. The script prints which platform
  it detected so the difference is explicit rather than silently misleading.
"""

import os
import stat
import sys
import tempfile


def describe_permissions(path):
    mode = os.stat(path).st_mode
    print(f"Raw mode bits: {oct(mode)}")
    print(f"stat.filemode(): {stat.filemode(mode)}")

    checks = [
        ("Owner read", stat.S_IRUSR),
        ("Owner write", stat.S_IWUSR),
        ("Owner execute", stat.S_IXUSR),
        ("Group read", stat.S_IRGRP),
        ("Group write", stat.S_IWGRP),
        ("Group execute", stat.S_IXGRP),
        ("Other read", stat.S_IROTH),
        ("Other write", stat.S_IWOTH),
        ("Other execute", stat.S_IXOTH),
    ]
    for label, bit in checks:
        present = bool(mode & bit)
        print(f"  {label:<15}: {'yes' if present else 'no'}")

    # Also demonstrate the higher-level, OS-call-based checks that resolve
    # against the CURRENT process/user (these go through the real access()
    # system call under the hood on POSIX, and CreateFile-based checks on
    # Windows) rather than just decoding raw bits.
    print("os.access() checks for the current process:")
    print(f"  Readable? {os.access(path, os.R_OK)}")
    print(f"  Writable? {os.access(path, os.W_OK)}")
    print(f"  Executable? {os.access(path, os.X_OK)}")


def main():
    print(f"Detected platform: {sys.platform} "
          f"({'POSIX rwx model' if os.name == 'posix' else 'Windows ACL model (chmod emulated)'})")

    fd, path = tempfile.mkstemp(prefix="os_perm_demo_", suffix=".txt")
    with os.fdopen(fd, "w") as f:
        f.write("Hello, permissions!\n")

    try:
        print(f"\nCreated file: {path}")
        print("\n--- Initial permissions ---")
        describe_permissions(path)

        print("\n--- Setting permissions to 0644 (rw-r--r--) via os.chmod ---")
        os.chmod(path, 0o644)
        describe_permissions(path)

        print("\n--- Setting permissions to 0600 (rw-------, owner only) ---")
        os.chmod(path, 0o600)
        describe_permissions(path)

        print("\n--- Adding the execute bit for owner: 0700 (rwx------) ---")
        os.chmod(path, 0o700)
        describe_permissions(path)

        print("\n--- Making the file read-only for everyone: 0444 ---")
        os.chmod(path, 0o444)
        describe_permissions(path)
        print(f"Writable via os.access() after making read-only? "
              f"{os.access(path, os.W_OK)}")

        # Restore write permission so cleanup can delete the file.
        os.chmod(path, 0o600)
    finally:
        os.remove(path)
        print(f"\nCleaned up: removed {path}")


if __name__ == "__main__":
    main()
