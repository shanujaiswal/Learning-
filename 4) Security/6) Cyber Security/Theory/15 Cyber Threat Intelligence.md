# What Threat Intelligence Actually Is

--> Threat Intelligence (CTI) is processed, analyzed information about threats -- WHO is attacking (threat actors), HOW they operate (tactics/techniques), and WHAT to look for (indicators) -- turned into something a defender can actually act on, distinct from raw, unanalyzed data (a list of IPs means little without context on why they matter).

# The Three Levels of Threat Intelligence

--> **Strategic** -- high-level trends and risks for leadership/decision-makers (e.g. "ransomware targeting our industry sector has increased 40% this year") -- informs budget and policy decisions, not day-to-day defense.
--> **Tactical** -- describes adversary Tactics, Techniques, and Procedures (TTPs) -- how attackers actually operate, informing detection rule and defense design.
--> **Operational/Technical** -- specific, actionable Indicators of Compromise (IOCs) -- malicious IPs, file hashes, domains -- fed directly into security tools for immediate blocking/alerting.

# Indicators of Compromise (IOCs)

--> Concrete, observable artifacts suggesting a compromise has occurred or is occurring -- malicious file hashes (connecting to the Malware Analysis Automation file in the Python for Security track), C2 server IPs/domains (connecting to the Red Team C2 Frameworks file), suspicious registry keys, unusual outbound traffic patterns.
--> IOCs are fed into SIEM systems (covered in the Incident Response file) and firewalls/EDR (covered in the Endpoint Security file) as detection/blocking rules -- CTI is the SOURCE feeding those tools' rule sets, not a separate isolated activity.

# MITRE ATT&CK -- The Shared Framework

--> A structured, publicly maintained knowledge base cataloging real-world adversary Tactics and Techniques (Initial Access, Privilege Escalation, Lateral Movement, Exfiltration, etc.) -- previously referenced in the Endpoint Security file, this is the framework CTI analysts use to categorize and communicate observed adversary behavior in a shared, industry-standard vocabulary.
--> Mapping an incident's observed activity to specific ATT&CK technique IDs (e.g. T1078 "Valid Accounts") lets defenders compare notes across organizations and tools using the same reference language, rather than everyone describing the same technique in ad hoc terms.

# Threat Actor Profiling

--> Advanced Persistent Threats (APTs) -- typically nation-state-linked groups pursuing long-term espionage/sabotage objectives, given tracking designations (APT28, APT29) by security researchers based on observed patterns of TTPs, infrastructure, and targeting.
--> Attribution (determining WHO is behind an attack) is genuinely difficult and often only probabilistic -- CTI analysts build confidence through consistent patterns across multiple incidents (shared infrastructure, reused code, consistent TTPs) rather than any single definitive proof.

# Threat Intelligence Sharing

--> ISACs (Information Sharing and Analysis Centers) -- industry-specific groups (financial services, healthcare) where organizations share threat intelligence with peers facing similar adversaries, on the principle that a threat seen at one company today is often a threat every similar company will see soon.
--> STIX/TAXII -- standardized formats/protocols for structuring and automatically exchanging threat intelligence between organizations and tools, so IOCs/TTPs can be shared and ingested programmatically rather than manually re-typed between systems.

# Threat Hunting -- Using CTI Proactively

--> Rather than only waiting for an alert to fire, Threat Hunting uses CTI (a known APT group's specific TTPs, for example) to proactively search an environment's logs/systems for signs that same activity might already be present but undetected -- directly connecting to the threat-hunting concept already referenced in the Endpoint Security file, with CTI supplying the hypothesis to hunt for.

# Why CTI Ties the Whole Cyber Security Track Together

--> Incident Response (what happened), SIEM (what to alert on), Endpoint Security (what to block), and GRC risk assessment (what to prioritize) all consume threat intelligence as an input -- CTI is less a standalone technology and more the connective analytical layer feeding every other defensive discipline covered in this track with actual, current, real-world context.
