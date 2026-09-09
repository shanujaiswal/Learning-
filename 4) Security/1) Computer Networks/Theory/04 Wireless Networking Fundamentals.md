# How Wi-Fi Actually Works

--> Wireless networking (802.11, the IEEE standard behind Wi-Fi) transmits data as radio waves instead of electrical signals over a cable -- which fundamentally changes the security model: a wired network requires physical access to a cable/port, while a wireless signal can be received by anyone within range, whether or not they're authorized to use the network.

# Wi-Fi Standards -- 802.11 Generations

--> Each generation (802.11b/g/n/ac/ax) improved speed and range, marketed today under simpler names: Wi-Fi 4 (802.11n), Wi-Fi 5 (802.11ac), Wi-Fi 6 (802.11ax).
--> Frequency bands -- 2.4GHz (longer range, more interference from other devices, slower) vs 5GHz (shorter range, less interference, faster) -- most modern routers broadcast both simultaneously, letting devices pick whichever suits their situation.

# Core Wireless Concepts

--> SSID (Service Set Identifier) -- the human-readable network name shown when you look for available Wi-Fi networks.
--> BSSID -- the actual MAC address of the specific access point broadcasting that SSID -- multiple physical access points can share one SSID (common in offices with many APs) but each has a distinct BSSID.
--> Channel -- a specific frequency sub-range within a band -- neighboring networks on overlapping channels interfere with each other, a common (non-security) cause of poor Wi-Fi performance.

# The Association Process -- Connecting to a Network

--> Scanning -- a device listens for (or actively probes for) beacon frames that access points broadcast, advertising their SSID/capabilities.
--> Authentication and Association -- the device and access point exchange frames to establish a connection, then the device is "associated" and can pass traffic.
--> 4-Way Handshake (for WPA2/WPA3) -- after association, a cryptographic handshake derives a unique session encryption key from the network's shared password, WITHOUT ever transmitting the password itself over the air -- this exact handshake is what the WPA2 attack content in the Ethical Hacking track targets (capturing this handshake to attempt an offline password-cracking attempt).

# Wireless Security Protocol Evolution

--> WEP (Wired Equivalent Privacy) -- the original, now completely broken standard -- crackable in minutes with widely available tools; should never be used today.
--> WPA (Wi-Fi Protected Access) -- an interim fix for WEP's flaws, itself since superseded.
--> WPA2 -- the long-standing modern standard, using AES encryption (covered in the Cryptography track) -- still widely deployed, though its 4-way handshake and PSK (Pre-Shared Key) mode have known weaknesses covered in the Ethical Hacking track's dedicated WPA2 file.
--> WPA3 -- the current standard, replacing WPA2's handshake with a stronger key exchange (SAE, resistant to offline dictionary attacks against a captured handshake) and mandating stronger encryption.

# Personal vs Enterprise Wi-Fi Security

--> WPA2/WPA3-Personal (PSK) -- everyone on the network shares the SAME pre-shared password -- simple to set up, but a single compromised/leaked password grants full network access, and there's no way to know WHICH person's credential was used for a given connection.
--> WPA2/WPA3-Enterprise -- uses 802.1X authentication against a RADIUS server, giving each user their own individual credentials -- access can be revoked per-person without changing a shared password, and connections are individually attributable -- the standard for any organization beyond a small office.

# Why This Matters Before Studying Wireless Attacks

--> The Ethical Hacking track's Wireless Network Security file assumes you understand the 4-way handshake, SSID/BSSID, and WPA2-Personal's shared-key model covered here -- attacks like handshake capture and rogue access point ("evil twin") attacks are direct exploitations of the mechanics described in this file.
