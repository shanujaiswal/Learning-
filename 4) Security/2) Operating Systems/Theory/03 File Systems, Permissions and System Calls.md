# What a File System Does

--> A File System organizes how data is actually stored on and retrieved from a physical disk -- files, directories, metadata (size, timestamps, permissions), all mapped onto raw disk blocks.
--> Common file systems: NTFS (Windows), ext4 (most Linux distros), APFS (macOS) -- different formats, same underlying job.
--> Everything in Linux is conceptually treated as a file -- including devices (`/dev/sda`), running process info (`/proc/1234`), and even network sockets -- a design philosophy that makes many OS interactions consistent and scriptable.

# Linux File Permissions

--> Every file/directory has an owner (a user), a group, and permission bits for three categories: Owner, Group, Others -- each with Read (r), Write (w), Execute (x).

```bash
-rwxr-xr--  1 alice devs  1024 Jan 1 12:00 script.sh
# owner (alice): rwx    group (devs): r-x    others: r--
```

```bash
chmod 750 script.sh     # owner=rwx(7), group=r-x(5), others=---(0) -- numeric shorthand
chmod u+x script.sh     # add execute permission for the owner
chown alice:devs file   # change owner and group
```

--> The SUID bit -- when set on an executable, it runs with the OWNER's privileges (often root) rather than the privileges of the user who launched it -- exactly the mechanism abused in the Linux Privilege Escalation content in the Ethical Hacking track (a misconfigured SUID binary can be leveraged to execute commands as root).

```bash
chmod u+s /path/to/binary   # Set the SUID bit
find / -perm -4000 2>/dev/null   # Find all SUID binaries on a system -- a real privesc recon step
```

# Windows Permissions -- ACLs

--> Windows uses Access Control Lists (ACLs) rather than the simpler Linux rwx model -- each file/object has a list of specific users/groups and exactly which rights they have (read, write, execute, delete, modify permissions themselves), offering finer-grained control at the cost of more complexity.
--> Misconfigured ACLs (a normal user granted unintended write access to a service's executable or config) are a common Windows privilege-escalation vector, conceptually parallel to Linux SUID misconfigurations.

# System Calls -- The Interface Between Program and Kernel

--> Every meaningful action a program takes that touches the outside world (reading a file, opening a network connection, creating a process, allocating memory) is ultimately a system call into the kernel -- application code never does these things "directly."
--> Common Linux system calls: `open()`, `read()`, `write()`, `close()` (files), `fork()`/`exec()` (creating new processes), `socket()`/`connect()` (networking), `mmap()` (memory mapping).
--> `strace` -- traces every system call a running program makes -- an extremely useful tool for both debugging AND security analysis (seeing exactly what a suspicious binary actually does at the kernel-interaction level).

```bash
strace -f -e trace=open,read,write ./some_program
```

# Why This Matters for Security

--> Privilege escalation is, at its core, finding a way to make the kernel perform an action on your behalf that your current permission level shouldn't allow -- whether through a SUID/ACL misconfiguration (abusing an intended-but-overpermissioned mechanism) or a kernel exploit (breaking the enforcement mechanism itself).
--> File permission auditing (finding world-writable files, unnecessary SUID binaries, overly permissive ACLs) is a standard, foundational step in both offensive privilege-escalation enumeration and defensive system hardening.
