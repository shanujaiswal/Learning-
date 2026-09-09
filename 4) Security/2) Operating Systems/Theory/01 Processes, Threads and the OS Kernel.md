# What an Operating System Actually Does

--> An OS manages a computer's hardware (CPU, memory, disk, devices) and exposes it to applications through a controlled, consistent interface -- applications never touch hardware directly.
--> Core responsibilities: process management, memory management, file system management, device management, and security/access control (permissions, users).
--> The Kernel -- the core of the OS with unrestricted hardware access, running at the highest privilege level. Everything an application does that touches hardware (reading a file, sending network data) ultimately goes through the kernel via a system call.

# User Mode vs Kernel Mode

--> Kernel mode -- full, unrestricted access to hardware and memory -- where the OS kernel and device drivers run.
--> User mode -- restricted -- where ordinary applications run, unable to directly access hardware or other processes' memory.
--> A System Call is the controlled doorway between the two -- when an application (in user mode) needs something only the kernel can do (open a file, allocate memory, send a network packet), it makes a system call, which switches the CPU to kernel mode to perform the privileged operation, then switches back.
--> This user/kernel separation is a foundational security boundary -- privilege escalation attacks (covered heavily in the Ethical Hacking track) are fundamentally about breaking out of a restricted user-mode context into kernel-level or admin-level control.

```
Application (user mode) --> system call (e.g. open(), read(), fork()) --> Kernel (kernel mode) --> Hardware
```

# Processes

--> A Process is a running instance of a program -- it has its own isolated memory space (other processes can't directly read/write it), its own set of resources (open file handles, environment variables), and a Process ID (PID).
--> Process states: New → Ready (waiting for CPU) → Running (actively executing) → Waiting/Blocked (waiting on I/O, e.g. disk or network) → Terminated.
--> Multitasking -- the CPU rapidly switches between multiple ready processes (a context switch), giving the illusion of true parallelism even on a single core.

```bash
ps aux            # List running processes (Linux)
kill -9 <pid>      # Forcefully terminate a process
top / htop         # Live view of process CPU/memory usage
```

# Threads

--> A Thread is a unit of execution WITHIN a process -- multiple threads in the same process share that process's memory space (unlike separate processes, which are isolated from each other).
--> Why threads exist -- creating a new thread is far cheaper than creating a new process (no need to duplicate memory space), and shared memory makes communication between threads trivial (though that same sharing is exactly what creates race conditions).
--> This directly connects to the Full Stack Python notes' Concurrency file (threading/multiprocessing/asyncio) -- that's this exact OS concept, applied at the application level.

# Context Switching

--> When the OS moves the CPU from running one process/thread to another, it must save the current one's complete state (register values, program counter, stack pointer) and load the next one's -- this is a context switch.
--> Context switches aren't free -- they cost CPU time themselves, which is why excessive switching (too many competing processes/threads) can actually reduce overall throughput ("thrashing").

# Interprocess Communication (IPC)

--> Since processes have isolated memory by default, they need explicit mechanisms to communicate: pipes (one-directional data stream, common for command chaining like `cmd1 | cmd2`), sockets (network-style communication, even between processes on the same machine), shared memory (a region both processes can access directly, fastest but requires careful synchronization), and signals (simple async notifications, like `SIGKILL` or `SIGTERM`).

# Deep Dive -- Namespaces and cgroups -- How Containers Actually Achieve Isolation

--> The Full Stack DevOps notes cover Docker/Kubernetes from a usage perspective -- this is the OS-level mechanism that makes container isolation possible AT ALL, and it's built entirely from ordinary Linux kernel features, not some separate "container technology."
--> **Namespaces** -- give a process a RESTRICTED, ISOLATED VIEW of a specific system resource, making it appear as though it's the only thing using that resource, even though it's actually sharing the same physical machine/kernel with other processes.

```
PID namespace     -- a containerized process sees itself as PID 1, unaware of any other processes on the host
Network namespace -- a container gets its own network interfaces, IP address, routing table, isolated from the host's
Mount namespace   -- a container sees its own isolated filesystem view, unaware of the host's real filesystem
UTS namespace     -- a container has its own hostname, independent of the host machine's actual hostname
User namespace    -- lets a process be "root" INSIDE the container while mapping to an unprivileged, non-root user on the actual host
```

--> **cgroups (Control Groups)** -- limit and account for how much of a physical resource (CPU, memory, disk I/O) a process or group of processes can actually consume -- this is precisely the underlying mechanism behind the Resource Requests and Limits covered in the Kubernetes Probes/Resource Limits file -- when Kubernetes sets a memory limit on a pod, it's configuring a cgroup, not inventing a new isolation concept.

```bash
# A simplified illustration of what a container runtime does under the hood using raw Linux tools
unshare --pid --net --mount --uts --fork /bin/bash   # Creates a new process with its own PID/network/mount/hostname namespaces
```

--> **Why this matters for security specifically** -- a container is NOT a virtual machine -- it shares the SAME kernel as the host and every other container on that machine, isolated only by namespaces and cgroups, both of which are kernel features that CAN have bugs. A kernel vulnerability that breaks namespace isolation is precisely what enables a "container escape" (directly connecting to the Container and Kubernetes Penetration Testing file in the Ethical Hacking track) -- fundamentally different, and generally considered a weaker isolation boundary, than a full VM's hardware-level virtualization, which doesn't share a kernel with its host at all.

# Deep Dive -- systemd -- The Modern Linux Init System

--> `systemd` (referenced in the Boot Process file as the first user-space process, PID 1) is responsible for starting every other system service in the correct dependency order during boot, and for supervising them afterward -- directly connecting to the systemd/service management commands covered practically in the Linux Terminal file.
--> A "unit file" describes a service's configuration -- what command starts it, what it depends on, whether it should restart automatically on crash.

```ini
# /etc/systemd/system/myapp.service
[Unit]
Description=My Node.js Application
After=network.target

[Service]
ExecStart=/usr/bin/node /app/server.js
Restart=always
User=appuser

[Install]
WantedBy=multi-user.target
```

--> **Security relevance** -- the `User=` directive above is a direct application of the Principle of Least Privilege (covered throughout the Cyber Security track) -- running a service under a dedicated, unprivileged `appuser` account rather than `root` means a compromise of that specific service doesn't automatically grant the attacker full root access to the entire system.
