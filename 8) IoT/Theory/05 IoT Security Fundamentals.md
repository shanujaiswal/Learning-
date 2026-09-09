# Why IoT Devices Are a Uniquely Attractive Target

--> IoT devices combine several weaknesses that rarely coexist in a normal server or laptop:

--> **Weak or default credentials** -- many devices ship with a hardcoded or default username/password (`admin`/`admin`), sometimes not even changeable, sometimes changeable but never actually changed by the end user or installer.

--> **Rarely patched** -- unlike a phone or laptop, most IoT devices have no visible update UI, no user habit of checking for updates, and often no OTA mechanism at all (Chapter 4) -- so known vulnerabilities can remain exploitable on deployed devices for years.

--> **Physically accessible** -- a device bolted to a pole, sitting in a public building's ceiling, or in a customer's home is reachable by anyone with a screwdriver, unlike a server locked in a data center. Physical access often means direct access to a UART/JTAG debug port, exposing firmware and secrets.

--> **Massive scale** -- there are vastly more IoT devices than traditional servers, and a huge share run near-identical firmware. A single vulnerability can be automated against millions of near-identical, poorly monitored targets at once -- something rarely true of bespoke enterprise servers.

# Mirai -- The Canonical Case Study

--> The **Mirai botnet** (2016) is the textbook example of what happens when all of the above weaknesses line up. Mirai scanned the internet for IoT devices (mostly IP cameras and home routers) with Telnet open, and tried a small hardcoded list of default vendor username/password pairs. Devices that matched were infected and turned into bots. At its peak, Mirai commanded hundreds of thousands of devices and was used to launch some of the largest DDoS attacks recorded at the time, including one that took down Dyn's DNS infrastructure and knocked a large swath of the internet (Twitter, Reddit, Netflix, and others) offline for hours.

--> The lessons generalize far beyond Mirai specifically: **default credentials at scale are a single exploit that compromises an entire fleet at once**, and unmonitored, unpatched embedded devices make an unusually reliable and durable botnet substrate, since owners rarely notice their doorbell camera is participating in a DDoS.

--> For the offensive side of testing exactly these weaknesses -- scanning for exposed services, brute-forcing default credentials, extracting and analyzing firmware -- see `3) Security/5) Ethical Hacking/Theory/23 IoT and Embedded Device Security Testing.md`, which already covers this vault's attacker's-eye view of IoT devices in depth. This chapter is its defensive/architectural counterpart: how to design and deploy IoT systems so that testing methodology finds as little as possible.

# Defensive Architecture

--> **Secure boot** -- the device's bootloader cryptographically verifies the firmware image's signature before executing it, at every boot. This blocks an attacker (even one with physical flash access) from simply swapping in malicious firmware, since it will fail signature verification and refuse to run.

--> **Firmware signing** -- every firmware image, especially OTA update images (Chapter 4), is signed by the vendor's private key, and the device only accepts updates whose signature verifies against a trusted public key baked in at manufacture. Without this, the OTA channel that exists to patch vulnerabilities becomes the easiest way to push malware to an entire fleet.

--> **Network segmentation** -- IoT devices go on their own VLAN or subnet, isolated from the main corporate/home network, with firewall rules allowing only the specific traffic the device actually needs (e.g., outbound MQTT to one broker IP, nothing else). This means a compromised smart plug or camera cannot pivot to reach laptops, servers, or file shares on the main network -- it's contained to a segment that has little else worth reaching. This directly reuses the OSI/network fundamentals from `3) Security/1) Computer Networks` and general segmentation practice from broader network security material.

--> **Minimal attack surface** -- disable every service, port, and debug interface not strictly required in production: Telnet, unused web admin panels, exposed UART headers, default SSH access. Every open service is one more thing that can carry a default credential or an unpatched bug; a device that exposes nothing beyond the one protocol it actually needs (MQTT to a specific broker, say) has a correspondingly smaller set of things to go wrong.

--> **Unique per-device credentials/certificates** -- as covered in Chapter 4's device identity discussion, each device should authenticate to the cloud platform with its own certificate, not a value shared across the entire product line. A shared secret baked into every unit means extracting it from one device (via physical access) compromises every device that shares it.

# A Brief Note on Physical/Electrical Safety

--> Working hands-on with IoT hardware -- wiring sensors, relays, or mains-adjacent actuators -- carries real electrical risk that pure software work doesn't. Relays and anything switching mains voltage (lamps, appliances) should be treated with real caution: use pre-built, properly rated relay modules rather than improvised wiring, double-check polarity and voltage ratings before powering a new sensor (many are 3.3V-only and are damaged by the 5V some boards output), and never assume a device is safe to open while still plugged in.

# Deep Dive -- Security Debt That Outlives the Product

--> A subtlety that doesn't come up with typical web applications: an IoT device sold today may still be physically running its original firmware, unpatched, a decade from now -- long after the vendor has stopped supporting it or even exists. This is different in kind from a SaaS vulnerability, which the vendor can usually just fix server-side for everyone at once. Because of this, the defensive controls in this chapter matter most not at launch but years into a device's field life, when it's most likely to be running known-vulnerable firmware with nobody left to patch it -- which is exactly the scenario network segmentation and minimal attack surface are meant to contain even after everything else has failed.
