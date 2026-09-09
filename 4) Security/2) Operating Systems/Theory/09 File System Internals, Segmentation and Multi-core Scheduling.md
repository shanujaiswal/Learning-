# Inodes -- What a File Actually Is Under the Hood

--> `03 File Systems, Permissions and System Calls.md` covered permissions and the system call interface; this file goes one level deeper into how a Linux/Unix file system actually STORES a file's identity and location.
--> A filename is NOT the file -- it's just a human-readable label pointing at an **inode** (index node), a fixed-size on-disk structure holding all of a file's METADATA: owner UID/GID, permission bits, size, timestamps (created/modified/accessed), and -- critically -- pointers to the actual data blocks on disk where the file's CONTENT lives. The inode itself does not contain the filename at all.
--> A directory, in this model, is really just a special file whose content is a list of (filename, inode number) pairs -- which is exactly why multiple filenames can point at the same underlying inode/content (hard links, below), and why deleting a filename doesn't necessarily delete the data.

```
Directory entry (in /home/alice/):        Inode table:                    Data blocks on disk:
  "notes.txt"  ->  inode #4821    ------>  inode #4821: owner=alice, ---->  [actual file bytes]
                                            perms=rw-r--r--, size=1024,
                                            block pointers -> [...]
```

# Hard Links vs Symbolic Links

--> **Hard link** -- a second directory entry pointing at the SAME inode number as an existing file -- the two "files" are indistinguishable at the filesystem level; there is no concept of one being the "original" and one being the "link." The inode tracks a reference/link count, and the actual data is only freed once that count drops to zero (i.e. every hard link to it has been removed) -- deleting one hard link never affects the others' content. Hard links cannot cross filesystem/partition boundaries (an inode number is only meaningful within its own filesystem) and cannot target directories on most Unix systems.
--> **Symbolic link (symlink)** -- a genuinely separate file whose content is simply a TEXT PATH pointing at another file/directory -- it has its own distinct inode, and following it is an extra indirection step the kernel performs automatically when the path is resolved. Symlinks CAN cross filesystems and CAN point at directories, but they can also go "dangling" (point at a path that no longer exists) since nothing keeps the target alive the way a hard link's reference count does.

```bash
ln original.txt hardlink.txt        # hardlink.txt shares original.txt's inode & reference count
ln -s original.txt symlink.txt      # symlink.txt is its own inode containing the TEXT "original.txt"

ls -li original.txt hardlink.txt symlink.txt
# original.txt and hardlink.txt show the SAME inode number; symlink.txt shows a DIFFERENT one
```

--> Security relevance: symlinks are the classic vector for TOCTOU (Time-Of-Check-To-Time-Of-Use) attacks introduced in `07 Race Conditions and Classic Synchronization Problems.md` -- a privileged process that checks a path's permissions and then later opens "the same path" can be tricked if an attacker swaps a symlink to point somewhere else in the gap between those two steps (`symlink race` attacks), which is why security-sensitive code uses APIs that resolve and open atomically (e.g. `O_NOFOLLOW`, or opening by already-held file descriptor) rather than check-then-open as two separate steps.

# Journaling File Systems

--> A file system operation that looks like one step (e.g. "write this file") is actually several separate on-disk updates (allocate data blocks, update the inode, update the directory entry) -- if power is lost or the system crashes midway through, the disk can be left in an inconsistent state (e.g. data blocks allocated but the inode not yet updated to reference them, or vice versa).
--> A **journal** is a dedicated on-disk log where the file system writes down what it's ABOUT TO DO before actually doing it -- after a crash, the system replays the journal to either complete or cleanly roll back whatever was in progress, restoring consistency in seconds rather than requiring a full disk scan (the old `fsck`-style consistency check, which on large disks could take a very long time).
--> This is directly a durability/integrity property, and comes in different strictness levels: journaling only METADATA (fast, but actual file DATA written during a crash could still be lost/corrupted even though the filesystem structure stays consistent) vs journaling data too (slower, since every write is logged twice, but stronger guarantees).

# ext4 vs NTFS -- Structural Comparison

--> **ext4** (the dominant Linux filesystem) uses the inode model described above, journals metadata by default, groups inodes and their data blocks into "block groups" to keep related data physically close on disk (reducing seek distance -- tying directly back to the disk scheduling discussion in `08 Page Replacement and Disk Scheduling Algorithms.md`), and uses "extents" (a single record describing a large CONTIGUOUS run of blocks) rather than ext2/3's older per-block pointer lists, which is far more efficient for large files.
--> **NTFS** (Windows' filesystem) has no separate "inode" terminology but the Master File Table (MFT) serves an equivalent role -- every file and directory gets an MFT record holding its metadata and either its actual small content directly (for very small files, "resident" data) or pointers to its data clusters elsewhere on disk. NTFS journals via a feature literally called the "Journal" ($LogFile), and separately maintains permissions as full ACLs (Access Control Lists, covered in `03`) rather than ext4's simpler owner/group/other + optional POSIX ACL extension model.
--> Both are journaling filesystems solving the same crash-consistency problem via broadly the same idea (log intentions before executing them), differing mainly in on-disk layout conventions and how richly permissions/metadata are expressed natively.

# Segmentation -- Paging's Alternative and Complement

--> `02 Memory Management and Virtual Memory.md` covered paging -- dividing memory into small, FIXED-size pages/frames. Segmentation is a different, older idea: dividing a process's memory into VARIABLE-size logical SEGMENTS that correspond to meaningful program divisions -- e.g. one segment for code, one for the stack, one for the heap -- rather than paging's uniform fixed-size chunks that carry no semantic meaning about what they contain.
--> Each segment is addressed by a (segment number, offset within that segment) pair rather than paging's uniform linear address space -- this maps naturally onto how programmers/compilers actually think about a program's memory (this variable belongs in the data segment, this belongs on the stack) but suffers from EXTERNAL FRAGMENTATION: because segments are variable-sized, as they're allocated and freed over time, memory ends up scattered with gaps too small to fit a new segment even though the TOTAL free space would be enough -- a problem fixed-size paging does not have, since any free frame fits any page by definition.
--> Real modern systems (x86-64, Linux, Windows) use paging as the PRIMARY mechanism and either don't use segmentation meaningfully at all (x86-64 largely deprecated true segmentation) or use a hybrid -- some x86 systems historically combined both: memory is divided into segments, and each segment is ITSELF further divided into pages, getting paging's fixed-size-allocation efficiency while retaining some of segmentation's logical structure (e.g. separate protection attributes for a code segment vs a data segment).

```
Pure paging:        one flat address space, cut into uniform fixed-size pages -- no semantic meaning
Pure segmentation:   [Code segment][Stack segment][Heap segment] -- variable sizes, external fragmentation risk
Segmentation+paging: [Code segment: pages][Stack segment: pages][Heap segment: pages]
                      (logical structure from segmentation, allocation efficiency from paging)
```

# Multi-core Scheduling and SMP

--> Everything in `04 CPU Scheduling Algorithms.md` assumed effectively one CPU deciding what runs next; a modern multi-core machine (SMP -- Symmetric Multiprocessing, where every core is treated as an equal, interchangeable peer by the OS) must additionally decide WHICH core a given thread runs on, not just when.
--> **Load balancing across cores** -- the scheduler tries to keep all cores similarly busy, migrating threads between cores when one is idle and another is overloaded -- but migration isn't free: it discards whatever useful data that thread's previous core had already loaded into its private CPU cache, and reloading that working set on the new core costs time (cache misses) before the thread runs at full speed again.
--> **CPU affinity** -- because of that migration cost, schedulers try to keep a thread on the SAME core it ran on recently ("soft affinity," a preference to reduce cache-reload cost) and applications/administrators can request "hard affinity" (pinning a thread/process to specific cores explicitly) for latency-sensitive workloads that can't tolerate the variability of being bounced between cores.

# NUMA -- Non-Uniform Memory Access

--> On larger multi-socket systems, RAM is physically split into regions, each attached "locally" to one CPU socket -- a core can access ITS OWN socket's local memory quickly, but accessing memory attached to a DIFFERENT socket has to cross an inter-socket interconnect, taking noticeably longer. This is "non-uniform" access latency, as opposed to a smaller single-socket system where all RAM is equally distant from the one CPU.

```
Socket 0: [Cores 0-7]  <--fast local access-->  [Memory bank A]
                                \
                                 \ ----- slower cross-socket interconnect ----- \
                                                                                  \
Socket 1: [Cores 8-15] <--fast local access-->  [Memory bank B]                  (path a core-0
                                                                                   thread takes to
                                                                                   reach bank B)
```

--> A NUMA-aware scheduler tries to keep a thread running on a core physically close to the memory it's actually using -- allocating a thread's memory on its OWN socket's local bank, and preferring to schedule/migrate it onto cores on that same socket, rather than letting the scheduler naively treat every core as equidistant from every memory bank the way plain SMP load balancing would. Getting this wrong (a thread on socket 1 constantly reading memory physically attached to socket 0) is a common, hard-to-diagnose real-world performance problem on database and virtualization hosts, where it manifests as inexplicably poor throughput despite plenty of idle CPU capacity being reported.

# Cross-References

--> This file directly extends `02 Memory Management and Virtual Memory.md` (segmentation as paging's alternative), `03 File Systems, Permissions and System Calls.md` (inodes/journaling underneath the permission model already covered there), `04 CPU Scheduling Algorithms.md` (multi-core scheduling as an extension of single-core scheduling theory), and the TOCTOU race-condition concept introduced in `07 Race Conditions and Classic Synchronization Problems.md`.
