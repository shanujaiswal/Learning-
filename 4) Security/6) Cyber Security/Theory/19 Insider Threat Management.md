# Why Insider Threats Are Uniquely Hard

--> Every technical control covered elsewhere in this track (firewalls, IAM, EDR) is largely designed around keeping OUTSIDERS out -- an insider already has legitimate credentials, legitimate access, and often a good understanding of exactly where the valuable data lives and how monitoring works, making their activity far harder to distinguish from normal, authorized behavior.

# Three Categories of Insider Threat

--> **Malicious insider** -- an employee/contractor deliberately stealing data, sabotaging systems, or otherwise acting against the organization's interests, often motivated by financial gain, grievance, or recruitment by an external party.
--> **Negligent insider** -- causes harm unintentionally -- falling for phishing, misconfiguring a system, mishandling sensitive data without malicious intent -- statistically the most common category of insider incident by volume.
--> **Compromised insider** -- an attacker has stolen a legitimate employee's credentials and is now operating AS that insider -- from a detection standpoint, this looks identical to a malicious insider unless additional context (impossible travel, unusual access patterns) reveals the account itself has been hijacked.

# Behavioral Indicators Worth Monitoring

--> Unusual data access patterns -- an employee suddenly accessing far more files/records than their normal baseline, especially data outside their normal job function.
--> Access right before departure -- a well-documented pattern: bulk downloads or unusual access spikes in the days/weeks before a resignation or termination is announced.
--> Off-hours activity -- accessing sensitive systems at times inconsistent with that person's normal working pattern.
--> Attempts to disable or evade logging/monitoring -- a strong signal of deliberate, malicious intent rather than negligence, since a negligent insider has no reason to specifically evade monitoring.

# User and Entity Behavior Analytics (UEBA)

--> UEBA tools build a behavioral BASELINE for each user/system over time, then flag statistically significant deviations from that baseline -- rather than relying on fixed rules (which insiders, knowing the rules, can often stay just under), UEBA can catch "this doesn't look like how this person normally behaves," even without a specific predefined signature for what they're doing.
--> This connects directly to the DLP file's mention of behavioral anomaly detection -- UEBA is often the underlying analytics engine making that kind of pattern-based DLP alerting possible.

# The Principle of Least Privilege as Insider Risk Reduction

--> Reducing WHAT any single insider can access in the first place (least privilege, covered throughout the IAM and Database Access Control files) directly limits the maximum possible damage any one compromised or malicious account can do -- the single most foundational, always-applicable insider threat mitigation, independent of any detection tooling.

# Separation of Duties

--> Structuring processes so no single individual can complete a sensitive action entirely alone -- e.g. requiring one person to INITIATE a wire transfer and a different person to APPROVE it -- makes malicious insider action require COLLUSION between multiple people, a meaningfully higher bar than a single bad actor acting alone.

# Offboarding -- The Most Common Practical Gap

--> Prompt, complete access revocation when an employee departs (all systems, all shared credentials, all physical access) is a surprisingly common real-world gap -- former-employee accounts left active for weeks or months after departure are a recurring, avoidable source of real incidents, directly connecting to the database access-control hygiene ("revoke permissions promptly") covered in the SQL Access Control file.

# Balancing Insider Threat Programs With Trust and Culture

--> Overly invasive monitoring can damage employee trust and morale if implemented without transparency -- most mature insider threat programs are explicit and disclosed about WHAT is monitored and WHY (usually framed around protecting the organization and its employees/customers, not surveilling staff), rather than covert monitoring that, if discovered, itself becomes a trust and retention problem.
