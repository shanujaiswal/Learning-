# What Happens When a Computer Powers On

--> Before any OS concept covered in this folder (processes, memory management, file systems) can exist, the machine has to get from "just received power" to "kernel is running and ready" -- the boot process is that sequence.

# The Boot Sequence, Step by Step

--> **1. Power-On Self-Test (POST)** -- firmware checks that essential hardware (CPU, RAM, basic devices) is present and functioning before proceeding at all.
--> **2. Firmware (BIOS or UEFI)** -- the low-level firmware built into the motherboard takes over, responsible for finding a bootable device and starting the boot process.
--> **3. Bootloader** -- a small program (GRUB on most Linux systems, Windows Boot Manager on Windows) loaded by the firmware -- its job is to locate and load the actual OS kernel into memory, and hand off execution to it.
--> **4. Kernel Initialization** -- the kernel (covered in the Processes/Threads file) initializes core subsystems -- memory management, process scheduling, device drivers -- and mounts the root file system.
--> **5. Init System** -- the very first user-space process (PID 1 on Linux -- `systemd` on most modern distros) starts every other system service and eventually reaches a usable login/desktop state.

# BIOS vs UEFI

--> BIOS (Basic Input/Output System) -- the older standard, uses the Master Boot Record (MBR) partitioning scheme, limited to booting from disks up to 2TB, and runs in a restrictive 16-bit mode during boot.
--> UEFI (Unified Extensible Firmware Interface) -- the modern replacement, supports GPT (GUID Partition Table, no practical size limit), boots faster, and enables Secure Boot.

# Secure Boot -- A Security-Relevant Boot Feature

--> Secure Boot (a UEFI feature) cryptographically verifies that each stage of the boot process (firmware → bootloader → kernel) is signed by a trusted authority BEFORE allowing it to run -- directly connects to the digital signature concepts covered in the Cryptography track.
--> This specifically defends against bootkits/rootkits -- malware that infects the boot process itself to run before the OS (and any OS-level antivirus/EDR, covered in the Cyber Security track's Endpoint Security file) even loads, making it otherwise extremely difficult to detect from within a running, already-compromised OS.

# Device Drivers -- Software That Talks to Hardware

--> A device driver is kernel-level (or sometimes user-level) software that lets the OS communicate with a specific piece of hardware (a graphics card, a network adapter, a USB device) through a device-specific protocol, exposing a CONSISTENT interface to the rest of the OS and applications regardless of exactly which hardware model is installed.
--> Drivers commonly run in kernel mode (covered in the Processes/Threads file) for performance -- but this also means a buggy or malicious driver has significant power, since it runs with the same privilege level as the kernel itself, not the restricted privilege of ordinary user-mode applications.

# Why Drivers Are a Security-Relevant Topic

--> A vulnerable driver is a genuine privilege-escalation vector -- exploiting a bug in a kernel-mode driver can grant an attacker kernel-level code execution, directly connecting to the Windows/Linux Privilege Escalation content in the Ethical Hacking track (some real-world privesc techniques specifically target vulnerable, poorly-audited third-party drivers rather than the OS kernel itself).
--> Driver signing requirements (Windows requires kernel drivers to be digitally signed by a trusted authority on modern systems) exist for exactly this reason -- to prevent unsigned, potentially malicious code from loading with kernel-level privilege in the first place.

# Safe Mode -- Booting With Minimal Drivers

--> Safe Mode boots the OS with only essential drivers loaded, skipping most third-party drivers and startup programs -- a standard troubleshooting technique, and also occasionally a relevant step in malware removal/incident response, since it can prevent certain malware (which may rely on loading alongside normal startup) from running at all.
