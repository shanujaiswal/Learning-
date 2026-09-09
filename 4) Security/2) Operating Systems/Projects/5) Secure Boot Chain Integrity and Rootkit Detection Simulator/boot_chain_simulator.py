"""
boot_chain_simulator.py

Models a simplified Secure Boot "chain of trust":

    firmware -> bootloader -> kernel -> drivers[]

Real UEFI Secure Boot works like this: each stage's binary is hashed, and
that hash is checked against a value that was signed by the trusted
authority the *previous* stage already vouches for. If stage N's measured
hash doesn't match what stage N-1 trusts, the chain of trust is broken and
boot MUST stop -- because anything after that point can no longer be
trusted (a compromised bootloader could hand off to *any* kernel it wants,
signed or not).

This module only builds the data + the "measure and verify one stage"
primitive. It intentionally does NOT decide what to do on failure --
that policy (halt vs. continue) lives in secure_boot_verifier.py, so the
same stage data can be run through both an "enabled" and "disabled" policy.

Everything here is pure stdlib (hashlib) and fully offline -- no real
firmware, disk, or OS APIs are touched. Binaries are simulated as short
byte strings standing in for "the actual firmware/bootloader/kernel image
bytes"; hashes are simulated as SHA-256 digests over those bytes.
"""

from __future__ import annotations

import hashlib
from dataclasses import dataclass, field
from typing import Optional


def measure(binary: bytes) -> str:
    """Simulate 'measuring' a boot component: a real TPM/UEFI implementation
    hashes the actual firmware/bootloader/kernel/driver image bytes. We do
    the same thing with SHA-256 over the simulated binary content."""
    return hashlib.sha256(binary).hexdigest()


@dataclass
class BootStage:
    """One link in the boot chain.

    name            -- human readable stage name (e.g. "Bootloader (GRUB)")
    binary          -- the simulated raw bytes of this stage's image
    expected_hash   -- the hash the PREVIOUS stage's trust store says this
                       stage must produce (i.e. "what was signed"). In real
                       Secure Boot this is a certificate/signature check;
                       we simplify it to an expected-digest comparison,
                       which is the same underlying integrity property.
    signed          -- whether this stage even carries a signature at all.
                       An unsigned stage always fails verification once
                       Secure Boot is enabled, no matter what its hash is.
    """

    name: str
    binary: bytes
    expected_hash: str
    signed: bool = True

    def measured_hash(self) -> str:
        return measure(self.binary)

    def verify(self) -> "StageResult":
        """Measure this stage and compare against what was expected/signed.
        Returns a StageResult -- pass/fail plus enough detail to explain why."""
        actual = self.measured_hash()
        if not self.signed:
            return StageResult(
                stage=self.name,
                ok=False,
                reason="stage carries no valid signature (unsigned)",
                expected_hash=self.expected_hash,
                actual_hash=actual,
            )
        if actual != self.expected_hash:
            return StageResult(
                stage=self.name,
                ok=False,
                reason="measured hash does not match signed/expected hash "
                       "(image was modified after signing -- tampering)",
                expected_hash=self.expected_hash,
                actual_hash=actual,
            )
        return StageResult(
            stage=self.name,
            ok=True,
            reason="measured hash matches signed/expected hash",
            expected_hash=self.expected_hash,
            actual_hash=actual,
        )


@dataclass
class StageResult:
    stage: str
    ok: bool
    reason: str
    expected_hash: str
    actual_hash: str

    def __str__(self) -> str:
        status = "PASS" if self.ok else "FAIL"
        return (
            f"[{status}] {self.stage}: {self.reason}\n"
            f"         expected: {self.expected_hash}\n"
            f"         measured: {self.actual_hash}"
        )


@dataclass
class Driver:
    """A kernel-mode driver considered for loading during boot / runtime.

    unsigned drivers are the classic rootkit-loading vector described in
    the theory file: Windows normally requires kernel drivers to be signed
    precisely to stop this."""

    name: str
    binary: bytes
    expected_hash: Optional[str] = None
    signed: bool = True
    hooks: list = field(default_factory=list)  # sensitive structures it patches, if any

    def measured_hash(self) -> str:
        return measure(self.binary)


def build_clean_chain() -> list[BootStage]:
    """A boot chain where every stage's hash matches what was signed --
    the 'nothing tampered with' baseline scenario."""
    firmware_bin = b"UEFI-FIRMWARE-v2.10-vendorXYZ"
    bootloader_bin = b"GRUB-BOOTLOADER-v2.06-signed-build"
    kernel_bin = b"LINUX-KERNEL-6.8.0-signed-build"

    return [
        BootStage("Firmware (UEFI)", firmware_bin, expected_hash=measure(firmware_bin)),
        BootStage("Bootloader (GRUB)", bootloader_bin, expected_hash=measure(bootloader_bin)),
        BootStage("Kernel (Linux 6.8.0)", kernel_bin, expected_hash=measure(kernel_bin)),
    ]


def build_tampered_chain() -> list[BootStage]:
    """Same chain as build_clean_chain(), except the bootloader stage's
    ACTUAL on-disk bytes have been modified by a bootkit after signing --
    but the 'expected_hash' still reflects what the ORIGINAL, legitimate
    bootloader hashed to (i.e. what firmware's trust store was told to
    expect). This models the real-world tampering scenario: an attacker
    overwrote the bootloader image on disk, but cannot forge a new
    signature/expected-hash entry trusted by firmware.
    """
    firmware_bin = b"UEFI-FIRMWARE-v2.10-vendorXYZ"

    legitimate_bootloader_bin = b"GRUB-BOOTLOADER-v2.06-signed-build"
    expected_bootloader_hash = measure(legitimate_bootloader_bin)  # what firmware trusts

    tampered_bootloader_bin = b"GRUB-BOOTLOADER-v2.06-signed-build" + b"\x00[BOOTKIT-PATCH]"

    kernel_bin = b"LINUX-KERNEL-6.8.0-signed-build"

    return [
        BootStage("Firmware (UEFI)", firmware_bin, expected_hash=measure(firmware_bin)),
        BootStage(
            "Bootloader (GRUB)",
            tampered_bootloader_bin,
            expected_hash=expected_bootloader_hash,  # signed value does NOT match tampered bytes
        ),
        # In a real Secure Boot chain this kernel stage would never even be
        # measured, because boot halts at the bootloader. It's included here
        # only so the chain "shape" is consistent; secure_boot_verifier.py
        # is what actually enforces the halt-on-failure policy.
        BootStage("Kernel (Linux 6.8.0)", kernel_bin, expected_hash=measure(kernel_bin)),
    ]


def build_rootkit_driver() -> Driver:
    """An unsigned kernel driver that, once loaded, hooks a sensitive
    kernel structure (here: the syscall table) -- the classic rootkit
    technique of intercepting syscalls to hide files/processes/network
    connections."""
    malicious_bin = b"UNSIGNED-DRIVER-sys_hide_v1-patches-syscall-table"
    return Driver(
        name="sys_hide.sys",
        binary=malicious_bin,
        expected_hash=None,   # never had a legitimate baseline entry
        signed=False,
        hooks=["syscall_table[__NR_getdents]", "syscall_table[__NR_kill]"],
    )


def build_legitimate_drivers() -> list[Driver]:
    """A small set of normal, signed drivers that make up the
    known-good baseline driver table."""
    drivers = []
    for dname, content in [
        ("nvidia_gpu.sys", b"SIGNED-DRIVER-nvidia-gpu-v552"),
        ("net_e1000.sys", b"SIGNED-DRIVER-intel-nic-v12"),
        ("storahci.sys", b"SIGNED-DRIVER-ahci-controller-v3"),
    ]:
        drivers.append(Driver(dname, content, expected_hash=measure(content), signed=True))
    return drivers
