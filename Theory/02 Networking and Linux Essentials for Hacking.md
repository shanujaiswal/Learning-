### Networking and Linux Essentials for Hacking

--> You cannot hack what you don't understand. Almost every attack is really "networking + Linux knowledge applied with malicious/testing intent."
--> This note is the toolbox you need before touching nmap, Metasploit, or any web attack tool.

## The OSI Model (Quick Recap)

--> The OSI model splits networking into 7 layers. As a hacker, you mostly live in layers 3, 4, and 7.

1. Physical – actual cables, radio waves, electrical signals.
2. Data Link – MAC addresses, switches, ARP (Address Resolution Protocol) works here. ARP spoofing attacks happen at this layer.
3. Network – IP addresses, routing. Ping (ICMP) and IP live here.
4. Transport – TCP and UDP live here. Ports, handshakes, reliability.
5. Session – manages connections/sessions between apps.
6. Presentation – encryption/encoding (SSL/TLS technically straddles here and layer 4).
7. Application – the actual protocols apps use: HTTP, FTP, SSH, DNS, SMTP.

--> Memory trick: "**P**lease **D**o **N**ot **T**hrow **S**ausage **P**izza **A**way" (Physical, Data Link, Network, Transport, Session, Presentation, Application).

## TCP/IP Model (What's Actually Used In Practice)

--> The real internet uses a simpler 4-layer model that maps onto OSI:

1. Network Access (OSI layers 1-2) – Ethernet, WiFi, MAC addresses.
2. Internet (OSI layer 3) – IP addressing and routing.
3. Transport (OSI layer 4) – TCP/UDP.
4. Application (OSI layers 5-7) – HTTP, DNS, SSH, etc.

## IP Addressing Basics

--> An IPv4 address is 32 bits, written as 4 numbers 0-255 separated by dots, e.g. `192.168.1.10`.
--> IP addresses are split into a Network portion and a Host portion. The subnet mask (or CIDR notation) tells you where that split is.

--> CIDR notation example: `192.168.1.0/24`
- `/24` means the first 24 bits are the network portion, leaving 8 bits (2^8 = 256 addresses, 254 usable) for hosts.
- `192.168.1.0` = network address (unusable for a host).
- `192.168.1.255` = broadcast address (unusable for a host).
- `192.168.1.1` – `192.168.1.254` = usable host addresses.

--> Common CIDR sizes to memorize:

| CIDR | Subnet Mask | Usable Hosts |
|---|---|---|
| /24 | 255.255.255.0 | 254 |
| /16 | 255.255.0.0 | 65,534 |
| /8 | 255.0.0.0 | 16,777,214 |
| /30 | 255.255.255.252 | 2 (common for point-to-point links) |

--> Private (non-routable on the internet) IP ranges — you WILL see these in every home lab:
- `10.0.0.0/8`
- `172.16.0.0/12`
- `192.168.0.0/16`

## TCP vs UDP

--> Both are Transport-layer (layer 4) protocols that carry data over IP, but they behave very differently — this matters a lot when scanning and when choosing attack tools.

==> TCP (Transmission Control Protocol)
--> Connection-oriented — establishes a connection before sending data via the "3-way handshake":
1. Client sends `SYN` (synchronize).
2. Server replies `SYN-ACK` (synchronize-acknowledge).
3. Client replies `ACK` (acknowledge). Connection established.
--> Reliable — guarantees delivery, retransmits lost packets, keeps packets in order.
--> Used by: HTTP/HTTPS, SSH, FTP, SMTP — anything where you can't afford to lose data.

==> UDP (User Datagram Protocol)
--> Connectionless — just fires packets ("datagrams") without checking if they arrived.
--> Unreliable but fast — no handshake, no retransmission, no ordering guarantee.
--> Used by: DNS lookups, DHCP, video streaming, VoIP, online gaming — speed matters more than perfect delivery.

--> Why this matters for hacking: TCP scans (`-sS`, `-sT`) are fast and reliable because of the handshake response. UDP scans (`-sU`) are much slower and less reliable because a closed UDP port often just... doesn't respond at all (nmap has to wait for a timeout or an ICMP "port unreachable" message).

## Common Ports and Services (Memorize This Table)

| Port | Protocol | Service | Why it matters to a pentester |
|---|---|---|---|
| 21 | TCP | FTP | File transfer — often allows anonymous login, cleartext creds |
| 22 | TCP | SSH | Remote shell — brute-forceable, check for weak/default creds |
| 23 | TCP | Telnet | Remote shell, but UNENCRYPTED — creds sniffable in plaintext |
| 25 | TCP | SMTP | Mail sending — can leak valid usernames via VRFY command |
| 53 | TCP/UDP | DNS | Name resolution — zone transfers can leak internal hostnames |
| 80 | TCP | HTTP | Web server — unencrypted, main target for web app attacks |
| 110 | TCP | POP3 | Mail retrieval — often cleartext |
| 139/445 | TCP | SMB (NetBIOS/direct) | Windows file sharing — huge attack surface (EternalBlue, null sessions) |
| 143 | TCP | IMAP | Mail retrieval |
| 443 | TCP | HTTPS | Web server, encrypted — but app-layer bugs still apply |
| 445 | TCP | SMB | See above — one of the most exploited ports in history |
| 3306 | TCP | MySQL | Database — check for weak root passwords, remote access enabled |
| 3389 | TCP | RDP | Windows Remote Desktop — brute-forceable, BlueKeep-style bugs |
| 8080 | TCP | HTTP (alt) | Common alt web port, proxies, admin panels |

--> Rule of thumb during scanning: any of these open on a machine you're authorized to test is worth manually poking at with the matching client tool (e.g. `ftp <ip>`, `smbclient -L <ip>`).

## Essential Linux Commands for a Pentesting Workflow

--> Kali Linux (the standard pentesting distro) is just Debian Linux with security tools pre-installed. You need to be fluent in the terminal.

==> Navigation & File Viewing
```bash
ls -la              # list all files (including hidden, starting with .) with details (permissions, size, owner)
cd /path/to/dir      # change directory
pwd                  # print current directory (where am I?)
cat file.txt         # print a file's full contents to screen
less file.txt        # view a file page by page (better for long files, q to quit)
```

==> Searching Text (grep)
```bash
grep "password" config.php          # find lines containing "password" in a file
grep -i "password" config.php       # case-insensitive search
grep -r "API_KEY" /var/www/         # recursively search every file under a directory
grep -rn "TODO" .                   # recursive + show line numbers, search current dir
```
--> `grep` is one of the most-used tools in recon — searching source code dumps, config files, and command output for secrets, keywords, or patterns.

==> Permissions (chmod)
--> Linux permissions are three sets of 3 bits: read(4)/write(2)/execute(1) for Owner, Group, Others.
```bash
chmod 755 script.sh     # owner: rwx (7), group: r-x (5), others: r-x (5) — typical for a script
chmod +x exploit.py     # add execute permission (needed before you can run `./exploit.py`)
chmod 600 id_rsa         # owner-only read/write — required for SSH private keys or ssh refuses to use them
```
--> `755` breakdown: 7 = 4+2+1 (rwx), 5 = 4+0+1 (r-x). Memorize this arithmetic — it comes up constantly in privilege escalation (finding SUID files, writable configs).

==> Process & Network Inspection
```bash
ps aux                       # list all running processes, who owns them, CPU/mem usage
ps aux | grep apache         # find a specific process by name
netstat -tulnp                # (older) list listening TCP(t)/UDP(u) ports, numeric(n), with process(p)
ss -tulnp                     # (modern replacement for netstat) same idea, faster
```
--> On a compromised box, `ps aux` and `ss -tulnp` are the first things you check: what's running, and what ports is THIS machine itself listening on (maybe there's an internal-only service you can now reach).

==> Finding Files
```bash
find / -name "*.conf" 2>/dev/null        # find all .conf files from root, hide permission-denied errors
find / -perm -4000 2>/dev/null           # find all SUID binaries (classic privilege escalation hunting)
find / -writable -type d 2>/dev/null     # find all world-writable directories
```
--> `find / -perm -4000` is one of THE most important privilege-escalation recon commands — SUID binaries run with the file owner's permissions (often root), and misconfigured ones are a common way to escalate from a low-priv shell to root.

## Basic Bash Scripting for Automation

--> Pentesting involves a lot of repetitive tasks (scanning many IPs, testing many usernames). Bash scripting automates this.

```bash
#!/bin/bash
# simple-scan.sh — ping every host in a /24 range and report which ones are alive

for i in $(seq 1 254); do
    ip="192.168.56.$i"
    ping -c 1 -W 1 "$ip" > /dev/null 2>&1     # send 1 ping, wait max 1 second, discard output
    if [ $? -eq 0 ]; then                      # $? = exit code of last command, 0 = success
        echo "$ip is UP"
    fi
done
```
```bash
chmod +x simple-scan.sh    # make it executable
./simple-scan.sh           # run it
```

--> Key Bash concepts used above:
- `$(seq 1 254)` – command substitution, generates numbers 1 to 254.
- `> /dev/null 2>&1` – redirect both normal output (`1`) and error output (`2`) to the void, so the script only prints what we explicitly `echo`.
- `$?` – special variable holding the exit code of the previous command (0 = success, non-zero = failure). `ping` returns 0 if it got a reply.
- `for ... in ...; do ... done` – a loop; standard Bash loop syntax.

--> Another common pattern — looping over a wordlist file for brute-forcing (conceptual, actual brute-forcing tools like Hydra do this more efficiently, but understanding the loop matters):
```bash
#!/bin/bash
# try each password in a wordlist against an SSH login (educational — use hydra/medusa in real labs)
while read -r pass; do
    echo "Trying password: $pass"
    # sshpass -p "$pass" ssh user@target "echo success" 2>/dev/null && echo "FOUND: $pass"
done < wordlist.txt
```
--> `while read -r pass; do ... done < wordlist.txt` — reads a file line by line into the variable `pass`. `-r` prevents backslashes from being interpreted, which matters for wordlists.

--> With this networking + Linux foundation in place, move on to note 03 for active reconnaissance and nmap.
