# Why IoT/Embedded Devices Are a Distinct Attack Surface

--> Everything covered so far in this track largely assumes a general-purpose computer running a mainstream OS (Windows/Linux) -- IoT and embedded devices (routers, cameras, smart locks, industrial control systems) often run stripped-down firmware, custom/proprietary protocols, and get security updates rarely or never, making them a persistently soft target in real environments.

# Firmware Extraction and Analysis

--> Before analyzing a device's software, you first need to actually GET the firmware -- via a direct download from the manufacturer, extracting it from an update file, or physically dumping it from the device's flash memory chip using a hardware programmer.
--> `binwalk` scans a firmware image and identifies embedded file systems, compression signatures, and known file headers within it, then extracts them -- the standard first step in firmware analysis.

```bash
binwalk -e firmware.bin      # Extract embedded file systems from a firmware dump
firmware-mod-kit/extract-firmware.sh firmware.bin
```

--> Once extracted, the file system can be examined like any Linux root file system -- looking for hardcoded credentials in config files, exposed private keys, and the actual binaries/scripts the device runs (using the reverse-engineering-adjacent skills touched on in the Buffer Overflow file for analyzing compiled binaries).

# Common Hardware Interfaces for Direct Access

--> UART (serial console) -- many devices expose a UART header on the circuit board, often providing a root shell or bootloader access without any authentication when physically connected with a simple USB-to-serial adapter.
--> JTAG -- a hardware debugging interface that can allow reading/writing memory directly, bypassing software-level protections entirely, if not properly disabled/locked down by the manufacturer.
--> Identifying these interfaces on a physical board is a standard step in a hardware-focused IoT assessment, often just requiring a multimeter and careful visual inspection of exposed pin headers.

# Weak/Default Credentials -- Still the Most Common Real Finding

--> An enormous number of real-world IoT compromises (including large-scale botnets) exploit nothing more sophisticated than default or weak manufacturer credentials that were never changed -- directly connecting to the password attack concepts covered in the Password Attacks file, applied here against embedded device web/SSH/telnet interfaces rather than a typical server.
--> Mirai (the botnet referenced in the Cyber Security track) specifically propagated by scanning the internet for IoT devices still using factory-default telnet credentials -- a stark illustration of how much real-world IoT insecurity comes from this single, unglamorous root cause rather than sophisticated exploitation.

# Wireless Protocol Analysis Beyond Wi-Fi

--> Many IoT devices communicate over protocols other than standard Wi-Fi -- Zigbee, Z-Wave, Bluetooth Low Energy (BLE) -- each with its own security model and tooling (e.g. software-defined radio tools for Zigbee/Z-Wave, `gatttool`/BLE-specific frameworks for Bluetooth).
--> BLE devices in particular are commonly assessed for weak/absent pairing authentication, allowing an attacker within radio range to connect and interact with device functions without proper authorization.

# API and Mobile App as the Real Attack Surface

--> Many "smart" devices are actually controlled primarily through a companion mobile app talking to a cloud API -- the API/Mobile security testing techniques covered in that dedicated file are often MORE productive against a modern IoT product than attacking the physical device directly, since the cloud backend is where the more valuable, more traditionally-exploitable attack surface actually lives.

# Why This Matters at Scale

--> IoT devices are frequently deployed in bulk, identically configured, with long deployment lifespans and rare patching -- a single vulnerability class found in one device model can potentially compromise every identical unit deployed across an organization (or across the internet, in Mirai's case), making IoT security testing a meaningfully different risk calculation than a one-off server vulnerability.
