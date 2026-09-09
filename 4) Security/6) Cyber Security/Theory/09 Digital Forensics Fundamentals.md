### Digital Forensics Fundamentals

--> Incident Response (Chapter 5) answers "how do we contain and recover from this?" Digital forensics answers a different, more exacting question: "exactly what happened, in what order, and can we prove it — potentially to a level that would hold up in a courtroom?" Every action taken during forensics has to survive not just technical scrutiny but legal scrutiny, which is why this discipline is far more procedure-obsessed than ordinary IT troubleshooting. A forensics investigation done sloppily doesn't just produce a wrong conclusion — it can make otherwise-solid evidence legally unusable.

## The Digital Forensics Process

--> The process is typically broken into six phases, and — critically — the ORDER matters, because getting it wrong can destroy the very evidence you're trying to preserve.

1. Identify
   --> Recognize that a forensic investigation is needed, and scope what potential evidence sources exist (which machines, which accounts, which logs, which cloud services touched the incident).
   --> Example: after a SIEM alert flags unusual outbound traffic from a finance workstation, the investigator identifies that the workstation itself, the domain controller's logs, and the firewall's logs are all potential evidence sources.

2. Preserve
   --> Take immediate steps to prevent evidence from being altered, deleted, or lost — this often means the very FIRST action is to stop normal IT activity that would otherwise happen automatically (log rotation deleting old logs, a scheduled disk cleanup job, a well-meaning sysadmin "just restarting the server to fix it").
   --> Example: immediately disabling automatic log rotation/retention policies on the affected systems and placing a "legal hold" notice so nobody deletes anything relevant, even accidentally.

3. Collect
   --> Actually acquire the evidence, using methods that don't alter the original data (covered in detail below — disk imaging, memory dumps, log exports) and in the correct order of volatility (also below).
   --> Example: capturing a full memory dump of the live, running finance workstation BEFORE powering it off, then creating a bit-for-bit disk image afterward.

4. Analyze
   --> Examine the collected evidence to reconstruct what happened: timeline of events, what the attacker did, what data (if any) was accessed/exfiltrated, how they got in initially (root cause).
   --> Example: using a memory-analysis tool to discover a malicious process was injected into a legitimate `explorer.exe`, then correlating its network connections against firewall logs to identify the command-and-control server it was talking to.

5. Document
   --> Meticulously record every single action taken during the investigation — what was collected, how, by whom, when, and every finding, with enough detail that another qualified investigator could review and independently verify the conclusions.
   --> This isn't optional paperwork — undocumented findings are, in a legal context, often treated as if they didn't happen at all.

6. Present
   --> Communicate the findings to their intended audience — this could be a technical report for the internal security team, an executive summary for leadership, or formal sworn testimony/an expert report for a courtroom, each requiring a different level of technical detail and framing.

--> A useful way to remember this whole flow: you can't skip straight to Analyze — everything found during Analyze is only as trustworthy as the Collect step that produced it, and Collect is only defensible if Preserve happened correctly and immediately.

## Chain of Custody

--> Chain of custody is the chronological, documented record of who has handled a piece of evidence, when, and what they did with it, from the moment it was collected until it's presented (in court, to a client, to law enforcement).
--> Why it matters legally: if a chain of custody has a gap — say, a hard drive sat in an unlocked drawer for two days with no record of who had access — opposing counsel in a legal proceeding can argue the evidence may have been tampered with during that gap, potentially getting it thrown out entirely, regardless of whether tampering actually occurred. The BURDEN is on the investigator to prove an unbroken chain, not on the other side to prove it was broken.
--> A typical chain-of-custody log records, for every single transfer of evidence:
```
Item: Dell Latitude laptop, Serial# XJ4K291
Collected by: J. Alvarez, 2026-08-06 14:32 UTC
Collected from: Finance workstation, Room 302
Storage: Sealed evidence bag #E-0091, placed in evidence locker #4

Transfer log:
2026-08-06 14:32 - Collected by J. Alvarez -> sealed in evidence bag
2026-08-06 15:10 - Transferred to K. Osei (forensic analyst) for imaging
                    -> signature + hash of bag seal verified intact
2026-08-06 17:45 - Returned to evidence locker #4 by K. Osei
                    -> disk image hash: SHA256 a1b2c3...
```
--> The core discipline this reflects: evidence should be sealed, stored, and every single handoff logged with a signature, so that at any point in the future, anyone can answer "who had this, and could they have altered it?" with a clean, gap-free paper trail.

## Order of Volatility

--> Different types of evidence disappear at wildly different speeds. Order of volatility ranks evidence sources from MOST fragile (collect first, before it's gone forever) to LEAST fragile (can wait a bit without being lost) — a foundational principle that dictates the actual sequence of the Collect phase.
```
MOST VOLATILE (collect first)
  1. CPU registers, cache             -- gone in nanoseconds, changes constantly
  2. RAM (running processes, network connections, encryption keys in memory)
                                        -- lost completely the instant power is cut
  3. Network state (active connections, routing tables, ARP cache)
                                        -- changes/expires within seconds to minutes
  4. Running processes / temporary filesystem data
  5. Disk (files on the hard drive)   -- persists after shutdown, but changes with
                                        every write/boot
  6. Remote logging / backups        -- may exist independently elsewhere, often
                                        the least urgent to grab first
LEAST VOLATILE (can wait)
```
--> The critical, easy-to-get-backwards lesson this order teaches: RAM must be captured BEFORE disk, and definitely before powering the machine off. A common, damaging mistake made by inexperienced first responders is to immediately unplug/shut down a compromised machine "to be safe" — this instantly and irreversibly destroys everything that was only in memory: decrypted data, active malware that only exists in RAM (fileless malware), active network connections, and encryption keys that might have unlocked an otherwise-encrypted disk.
--> Correct instinct instead: isolate the machine from the network (unplug the network cable, not the power cable) to stop further damage/communication, THEN begin evidence collection starting from the most volatile source downward.

## Disk Imaging

--> A disk image is a bit-for-bit, forensically exact copy of an entire storage device — not just a copy of the visible files, but every sector, including deleted files not yet overwritten, slack space, and unallocated space that might still hold recoverable fragments of old data.
--> Why not just copy the files normally? A normal file copy only grabs what the live filesystem currently shows you — it misses deleted files, misses file metadata like original creation timestamps, and (worse) the very act of booting/using the original system to do the copying risks altering timestamps and other metadata on the evidence itself.

==> Write Blockers
--> A write blocker is a hardware device (or software equivalent) placed between the original evidence drive and the investigator's analysis machine that physically/logically allows READ commands through but blocks ALL write commands from ever reaching the original drive.
--> This guarantees the original evidence is never modified during imaging — even a single accidentally-written byte (e.g., the analysis OS automatically updating a "last accessed" timestamp just from being plugged in) can be used to challenge the evidence's integrity in a legal proceeding. A write blocker removes that entire risk category.

==> Hash Verification
--> Before and after imaging, a cryptographic hash (commonly SHA-256, historically MD5) is calculated of the original drive and of the resulting image file.
```
Original drive hash:  SHA256: 4f3c9a1e...  (calculated once, immediately)
Image file hash:      SHA256: 4f3c9a1e...  (calculated after imaging completes)

--> Hashes match exactly => the image is a mathematically verified,
    bit-for-bit exact duplicate of the original -- this fact alone is
    what allows an investigator to work on the COPY rather than
    risking the original, while still being able to prove in court
    that the copy is faithful to the original.
```
--> All analysis work happens on the verified image, never on the original drive — the original goes straight into evidence storage (chain of custody, above) and typically is never touched again unless a fresh verification copy is needed.
--> Common imaging tools: FTK Imager, dd/dcfldd (Linux command-line, produces raw bit-for-bit images), Guymager — all designed around this exact write-blocked, hash-verified workflow.

## Memory Forensics

--> Memory forensics is the analysis of a captured RAM dump to reconstruct what was actively happening on a system at the moment it was captured — critical because a growing share of modern malware is "fileless" (it never writes itself to disk at all, existing only in memory, specifically to evade traditional disk-based antivirus scanning and to leave nothing behind after a reboot).
--> Volatility (and Volatility 3, its modern rewrite) is the most widely used open-source memory forensics framework. It parses the raw structure of a memory dump against known operating system data structures to extract meaningful, structured information from what otherwise looks like an undifferentiated blob of bytes.

What Volatility can extract from a RAM dump:
1. Running processes (and process trees)
   --> `pslist` / `pstree` plugins reconstruct the full list of processes that were running at capture time, including parent-child relationships — revealing, for example, that `winword.exe` unexpectedly spawned `powershell.exe`, a classic malicious macro execution pattern.
2. Network connections
   --> `netscan` reveals active and recently-closed TCP/UDP connections at the time of capture, including remote IPs/ports — this is often how an investigator finds the actual command-and-control server address, even if it's long gone from disk-based logs by the time the investigation starts.
3. Injected code / malicious code hiding inside legitimate processes
   --> `malfind` scans process memory for signs of code injection (memory regions with executable permissions that don't correspond to a legitimately loaded file on disk) — this is exactly how fileless malware hiding inside `explorer.exe` or `svchost.exe` gets caught, since it leaves no file on disk to find but DOES leave an anomalous memory region.
4. Loaded DLLs and command history
   --> `dlllist`, `cmdline` plugins reveal what modules a process had loaded and the exact command-line arguments it was launched with — often directly revealing an attacker's exact commands (e.g., a PowerShell one-liner with a base64-encoded payload).
5. Registry hives and password hashes held in memory
   --> Some credentials/hashes that would never be found on disk in plaintext can still be extracted from memory (e.g., cached credentials, or artifacts left by credential-dumping tools like Mimikatz, which itself operates almost entirely in memory).

--> Example Volatility usage (conceptual command-line pattern):
```bash
# List running processes at time of capture
vol -f memory.dmp windows.pslist

# Search for injected/hidden code in process memory
vol -f memory.dmp windows.malfind

# List network connections active at capture time
vol -f memory.dmp windows.netscan
```

## Case Study: Investigating a Compromised Host

--> Scenario: A SOC alert (Chapter 5 style) flags unusual outbound traffic at 2 AM from `FIN-WKS-07`, a finance department workstation that should never be active at that hour.

```
1. Identify:
   The alert itself is the trigger. The investigator confirms this is
   a real anomaly (not a scheduled backup job) and scopes the affected
   system as FIN-WKS-07 plus the user account logged into it.

2. Preserve:
   The investigator immediately disables log retention/rotation
   policies on FIN-WKS-07's endpoint agent and the firewall so
   nothing relevant ages out mid-investigation. The machine is
   isolated from the network (network cable unplugged / EDR
   network-isolation feature triggered) -- crucially, power is left
   ON.

3. Collect (in order of volatility):
   a. A full RAM capture is taken first, using a trusted forensic
      tool run from external/write-protected media (never installing
      new software onto the live evidence machine, which would alter
      it).
   b. Active network connection state and ARP cache are logged.
   c. Only after memory is safely captured is the machine powered
      down and a full disk image taken through a write blocker, with
      hashes recorded before and after imaging.
   d. Relevant firewall and domain controller logs covering the
      suspicious time window are exported and hashed as well.
   Every single one of these steps is logged with timestamp and
   handler name for the chain of custody.

4. Analyze:
   - windows.pslist on the RAM dump shows a suspicious child process:
     winword.exe -> powershell.exe -> a base64-encoded one-liner
     visible via the cmdline plugin.
   - windows.netscan shows that PowerShell process held an active
     connection to an external IP that matches nothing on any
     approved-vendor list.
   - windows.malfind confirms an injected, unsigned code region
     inside the powershell.exe process -- consistent with a malicious
     macro-delivered payload that never wrote itself to disk as a
     standalone file.
   - Disk image analysis confirms the user opened an email attachment
     ("Q3_Invoice.docm") at 01:57 AM, matching the process tree's
     start time almost exactly -- establishing likely initial access
     via a phishing attachment with a malicious macro.
   - Firewall logs confirm outbound traffic to the same external IP
     found via netscan, continuing for roughly 40 minutes before the
     SIEM alert fired.

5. Document:
   A full report is written: initial access vector (phishing
   attachment), execution chain (Word macro -> PowerShell -> in-memory
   payload), C2 IP address and duration of the connection, what (if
   anything) was accessed on the finance share during that 40-minute
   window, and every collection/analysis step with timestamps and
   hashes to support chain of custody.

6. Present:
   A technical findings report goes to the security team (root cause,
   IOCs to block, detection rule improvements for the SIEM). An
   executive summary goes to leadership (business impact, whether
   customer/financial data was actually exposed). If the incident
   later involves law enforcement or a legal/insurance claim, the
   same documented, hash-verified, chain-of-custody-tracked evidence
   is what makes the investigator's conclusions actually defensible.
```
--> Notice how directly this case study threads together everything from this chapter AND from Chapter 5: the SIEM alert (Chapter 5's Identification), network isolation (Chapter 5's Containment) — but this time done in the specific order of volatility, with a write-blocked, hash-verified disk image and a documented chain of custody, so that the same investigation that stops the bleeding operationally could also stand up to legal/regulatory scrutiny afterward.

## Tying It Together

--> Digital forensics exists to answer "what exactly happened, and can we prove it," which is a stricter bar than incident response's "how do we stop and recover from this."
--> Chain of custody is what makes evidence legally trustworthy — an unbroken, signed record of every hand that ever touched it, because a single unexplained gap can get otherwise-solid evidence excluded entirely.
--> Order of volatility dictates that RAM is always captured before disk, and network cable (not power) is what gets pulled first — get this backwards and you can permanently destroy the most valuable evidence before you ever get to look at it.
--> Disk imaging (write blocker + hash verification) lets an investigator work freely on a provably identical copy while the original evidence stays untouched and safely stored.
--> Memory forensics (Volatility and similar tools) is what catches modern fileless malware specifically because it exists only in RAM and leaves nothing for disk-based tools to ever find.
--> This chapter closes the loop with Chapter 5: SIEM/SOC detects and IR contains an incident in real time; forensics is the rigorous, evidence-grade reconstruction of exactly what happened, done right alongside (never instead of) that real-time response.
