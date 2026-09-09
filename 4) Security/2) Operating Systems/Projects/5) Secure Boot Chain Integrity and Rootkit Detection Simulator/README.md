# Secure Boot Chain Integrity and Rootkit Detection Simulator

A fully offline, stdlib-only Python simulator of a UEFI **Secure Boot chain
of trust** (firmware -> bootloader -> kernel -> drivers), demonstrating:

1. How Secure Boot catches a tampered bootloader (a bootkit) and halts the
   boot process immediately, before anything downstream ever runs.
2. What happens if Secure Boot is disabled/bypassed instead: the same
   tampering is let through, an unsigned rootkit driver loads and hooks the
   syscall table -- and only a **runtime integrity scan** run afterward
   catches it, by comparing the current driver table against a known-good
   baseline.

No real firmware, UEFI variables, disks, or kernel APIs are touched.
Boot stages and driver tables are modeled as plain Python data; hashes are
computed with `hashlib.sha256` over simulated "binary" byte strings.

## Real-world scenario

Modern PCs use UEFI Secure Boot to cryptographically verify every stage of
the boot process -- firmware verifies the bootloader's signature before
running it, the bootloader verifies the kernel's signature before handing
off to it, and (with driver signing enforcement) the kernel only loads
signed drivers. If any stage's measured hash doesn't match what was
signed, the chain of trust is broken and boot stops right there. This is
specifically designed to defeat **bootkits/rootkits**: malware that
infects the boot process itself so it runs *before* the OS (and any
OS-level antivirus/EDR) even loads, which otherwise makes it extremely
hard to detect from inside an already-compromised running system.

This project simulates exactly that defense, plus what happens when it's
turned off: an unsigned kernel driver loads and hooks the syscall table
(a classic technique for hiding files/processes/network connections from
normal enumeration -- think early-2000s rootkits like FU/FUTo, or the
reason Windows enforces kernel driver signing today). The catch-up
mechanism modeled here -- a runtime scanner diffing the current driver
table against a known-good baseline -- mirrors real tools like GMER,
TDSSKiller, or kernel integrity/EDR monitoring that look for exactly this
kind of tampering after the fact.

## Architecture

| Module | Role | Real-world equivalent |
|---|---|---|
| `boot_chain_simulator.py` | Models boot stages (firmware, bootloader, kernel) and drivers as data, each with a simulated SHA-256 "measurement" and an expected/signed hash; provides the per-stage `verify()` primitive. | The measurement step of a Trusted/Measured Boot -- hashing each boot component before deciding whether to trust it. |
| `secure_boot_verifier.py` | Walks the chain stage by stage. **Enabled** mode halts immediately at the first verification failure. **Disabled** mode measures every stage but loads regardless of the result. | UEFI Secure Boot's chain-of-trust enforcement (enabled) vs. Secure Boot turned off in firmware settings (disabled). |
| `driver_integrity_scanner.py` | Compares the currently-loaded driver table against a known-good baseline; flags unsigned/unexpected drivers, hash mismatches, and hooks of sensitive kernel structures (e.g. the syscall table). | A rootkit scanner like GMER/TDSSKiller, or runtime kernel integrity monitoring / EDR driver-table auditing. |
| `main.py` | Runs the enabled scenario (tampering caught, boot halted), the disabled scenario (tampering let through, rootkit driver loads), the runtime scan (rootkit caught after the fact), and prints a final comparison. | The end-to-end "before vs. after" story of what Secure Boot buys you, and what has to catch things when it's off. |

## Run it

```bash
python main.py
```

No dependencies beyond the Python standard library (`hashlib`, `dataclasses`).

## Verified result (actual output)

```
==============================================================================
SCENARIO A: Secure Boot ENABLED -- tampered bootloader
==============================================================================
--- Secure Boot: ENABLED ---
[PASS] Firmware (UEFI): measured hash matches signed/expected hash
         expected: 8e72a2fbd950549d231ac9637b0b5da939d3967b5b40a149c27566769c33aa68
         measured: 8e72a2fbd950549d231ac9637b0b5da939d3967b5b40a149c27566769c33aa68
[FAIL] Bootloader (GRUB): measured hash does not match signed/expected hash (image was modified after signing -- tampering)
         expected: ea35109c491ab886e3b1408a3f64253b6850f3f35409a89ec502ad57ccf89664
         measured: 00d2237a9eae4f5ec26b584e55a5e774fcdd3a6812c2f682746660bd0daaf5a4
         >>> HALT: chain of trust broken at 'Bootloader (GRUB)'. Boot stopped -- no further stages are measured or executed.
Result: BOOT HALTED at 'Bootloader (GRUB)'. System does not start.

==============================================================================
BASELINE: Secure Boot ENABLED -- clean, untampered chain
==============================================================================
--- Secure Boot: ENABLED ---
[PASS] Firmware (UEFI): measured hash matches signed/expected hash
         expected: 8e72a2fbd950549d231ac9637b0b5da939d3967b5b40a149c27566769c33aa68
         measured: 8e72a2fbd950549d231ac9637b0b5da939d3967b5b40a149c27566769c33aa68
[PASS] Bootloader (GRUB): measured hash matches signed/expected hash
         expected: ea35109c491ab886e3b1408a3f64253b6850f3f35409a89ec502ad57ccf89664
         measured: ea35109c491ab886e3b1408a3f64253b6850f3f35409a89ec502ad57ccf89664
[PASS] Kernel (Linux 6.8.0): measured hash matches signed/expected hash
         expected: c00e6ef1773a2dc1b114c52e2973237278f3d96de736b7fe87bac6f53b270ac8
         measured: c00e6ef1773a2dc1b114c52e2973237278f3d96de736b7fe87bac6f53b270ac8
Result: system booted -- every stage verified against its signed/expected hash.

==============================================================================
SCENARIO B: Secure Boot DISABLED -- same tampered bootloader
==============================================================================
--- Secure Boot: DISABLED ---
[PASS] Firmware (UEFI): measured hash matches signed/expected hash
         expected: 8e72a2fbd950549d231ac9637b0b5da939d3967b5b40a149c27566769c33aa68
         measured: 8e72a2fbd950549d231ac9637b0b5da939d3967b5b40a149c27566769c33aa68
[FAIL] Bootloader (GRUB): measured hash does not match signed/expected hash (image was modified after signing -- tampering)
         expected: ea35109c491ab886e3b1408a3f64253b6850f3f35409a89ec502ad57ccf89664
         measured: 00d2237a9eae4f5ec26b584e55a5e774fcdd3a6812c2f682746660bd0daaf5a4
[PASS] Kernel (Linux 6.8.0): measured hash matches signed/expected hash
         expected: c00e6ef1773a2dc1b114c52e2973237278f3d96de736b7fe87bac6f53b270ac8
         measured: c00e6ef1773a2dc1b114c52e2973237278f3d96de736b7fe87bac6f53b270ac8
Result: system booted anyway (Secure Boot disabled) despite verification failures at: Bootloader (GRUB).

Because Secure Boot is disabled, the compromised bootloader was allowed to hand off to the kernel, which in turn loaded an additional, unsigned kernel driver:

  loaded driver: nvidia_gpu.sys   [signed]
  loaded driver: net_e1000.sys    [signed]
  loaded driver: storahci.sys     [signed]
  loaded driver: sys_hide.sys     [UNSIGNED], hooks: ['syscall_table[__NR_getdents]', 'syscall_table[__NR_kill]']

==============================================================================
RUNTIME SCAN: driver_integrity_scanner vs. known-good baseline
==============================================================================
--- Runtime Driver Integrity Scan ---
[CRITICAL] sys_hide.sys: driver is not present in known-good baseline (unexpected/unknown driver loaded) and is UNSIGNED
[CRITICAL] sys_hide.sys: driver has no valid signature (unsigned kernel-mode code)
[CRITICAL] sys_hide.sys: driver hooks a sensitive kernel structure: syscall_table[__NR_getdents] (System Call Table (dispatch table for kernel syscalls)) -- classic rootkit hooking technique
[CRITICAL] sys_hide.sys: driver hooks a sensitive kernel structure: syscall_table[__NR_kill] (System Call Table (dispatch table for kernel syscalls)) -- classic rootkit hooking technique

Result: 4 finding(s), 4 CRITICAL. Rootkit indicators present.

==============================================================================
FINAL COMPARISON
==============================================================================
Secure Boot ENABLED  : boot halted at 'Bootloader (GRUB)' -- tampering caught IMMEDIATELY, before the kernel or any driver ever ran.
Secure Boot DISABLED : boot completed anyway (booted_fully=True); tampering was measured and reported but NOT enforced, letting an unsigned rootkit driver load and hook the syscall table.
Runtime scanner      : caught the rootkit driver AFTER the fact with 4 finding(s) (4 CRITICAL), but only because it happened to be run -- unlike Secure Boot, nothing forces this scan to happen before damage occurs.
```

## Things to try changing

- In `boot_chain_simulator.build_tampered_chain()`, tamper with the
  **kernel** stage instead of the bootloader, and confirm Secure Boot still
  catches it -- but now the bootloader stage shows as PASS first, since the
  chain only breaks at the kernel link.
- Add a second, downstream driver stage to the chain itself (not just the
  post-boot driver table) so Secure Boot's per-stage halting logic also
  applies to driver loading, not only firmware/bootloader/kernel.
- Add a second rootkit driver in `build_rootkit_driver()`-style that
  hooks a different sensitive structure (e.g. `IDT` or `interrupt_table`)
  and extend `SENSITIVE_STRUCTURES` in `driver_integrity_scanner.py` to
  recognize it.
- Make a driver "impersonate" a legitimate one: give it the same `name` as
  a baseline driver but different bytes, and watch the scanner report a
  baseline **hash mismatch** finding instead of an "unexpected driver"
  finding -- this models a rootkit that replaces a real driver file rather
  than adding a new one.
- Flip `signed=False` on the *kernel* stage in `build_clean_chain()` and
  run it through `run_secure_boot_enabled()` to see an otherwise-correct
  hash still fail verification, since an unsigned stage always fails
  regardless of its measured hash.
