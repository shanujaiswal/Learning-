### Incident Response, SIEM, and Blue Team Basics

--> All the previous chapters covered how to prevent attacks. This chapter covers what happens when prevention fails anyway (and it eventually always does) — how a Blue Team detects, responds to, and learns from a real security incident.
--> This is the practical, day-to-day world of a SOC Analyst / Incident Responder job.

## The Incident Response (IR) Lifecycle

--> Incident Response is the structured process an organization follows to handle a security incident from start to finish, minimizing damage and recovery time and cost. The most widely taught model (from NIST/SANS) has six phases.

1. Preparation
   --> Everything done BEFORE an incident happens: writing an incident response plan, defining roles/responsibilities, setting up logging and monitoring tools, running tabletop exercises/drills, and making sure backups exist and are tested.
   --> Example: A company runs a yearly "ransomware simulation" drill so the IR team already knows exactly who to call and what to do at 2 AM on a Sunday, instead of figuring it out live during a real crisis.

2. Identification (Detection & Analysis)
   --> Determining whether a security incident has actually occurred — spotting the signs, confirming it's real (not a false positive), and understanding its scope.
   --> Example: A SOC analyst receives a SIEM alert for "50 failed login attempts followed by 1 success" on an admin account and confirms this is a real brute-force compromise, not a user who forgot their password.

3. Containment
   --> Limiting the damage and stopping the incident from spreading further, WITHOUT necessarily fixing the root cause yet. Speed matters more than elegance here.
   --> Short-term containment: isolating the infected machine from the network (disconnecting it, blocking its IP at the firewall) so malware can't spread further.
   --> Long-term containment: applying temporary fixes/patches to unaffected systems while a full remediation plan is prepared.
   --> Example: Immediately disabling the compromised admin account and disconnecting the infected server's network cable while the team investigates further.

4. Eradication
   --> Completely removing the threat from the environment — deleting malware, closing the vulnerability that was exploited, removing any backdoor accounts the attacker created.
   --> Example: Wiping and rebuilding the compromised server from a known-clean image, patching the vulnerability that allowed the initial breach, and resetting all credentials that may have been exposed.

5. Recovery
   --> Restoring affected systems back to normal, safe operation, and carefully monitoring them closely afterward to confirm the threat is truly gone and hasn't returned.
   --> Example: Bringing the rebuilt server back online, restoring data from clean backups, and watching its logs closely for the next few weeks for any sign of the attacker attempting to regain access.

6. Lessons Learned (Post-Incident Review)
   --> After the dust settles, the team documents what happened, what worked, what didn't, and updates policies/defenses/detection rules accordingly. This step is skipped far too often in the real world, and it's the one that actually prevents the SAME incident from happening again.
   --> Example: The team discovers the initial breach happened because the admin account had no MFA enabled — this becomes a formal action item: "Enforce MFA on all admin accounts within 30 days," plus a new SIEM detection rule is written to catch this exact brute-force pattern faster next time.

--> Mnemonic to remember the order: PICERL (Preparation, Identification, Containment, Eradication, Recovery, Lessons learned).

## SIEM (Security Information and Event Management)

--> A SIEM is a platform that collects log/event data from across an entire organization's systems (servers, firewalls, endpoints, applications) into one central place, then analyzes that data to detect suspicious activity and generate alerts.
--> Without a SIEM, an analyst would have to manually log into dozens/hundreds of separate systems to piece together what happened during an incident — a SIEM does this correlation automatically and in near real-time.

What a SIEM actually does, broken into its core functions:

==> Log Aggregation
--> Collects logs from many different sources (Windows Event Logs, Linux syslogs, firewall logs, application logs, cloud logs) and pulls them into a single, searchable repository.
--> Example: instead of separately checking the firewall, the web server, and the domain controller for suspicious activity, an analyst searches ONE SIEM dashboard that already has all three sources' logs in it.

==> Correlation
--> Connects related events from different sources/times that would look harmless individually but reveal an attack pattern when combined.
--> Example: A SIEM correlation rule notices that the same external IP address (a) failed to log into the VPN 20 times, then (b) succeeded once, then (c) that same account immediately accessed the file server and downloaded 500 files it had never touched before. Individually, none of these three log entries look alarming — correlated together, they clearly describe a compromised account being used for data theft.

==> Alerting
--> When a correlation rule or detection logic matches a defined suspicious pattern, the SIEM automatically raises an alert (dashboard notification, email, ticket) for a human analyst to triage and investigate.
--> Well-tuned alerting is a constant balancing act: alerts that are too broad create "alert fatigue" (analysts start ignoring alerts because there are too many false positives), while alerts that are too narrow miss real attacks.

--> Common SIEM/log tools mentioned in the industry:
--> Splunk – one of the most widely used commercial SIEM platforms, known for its powerful search language (SPL) and dashboards.
--> ELK Stack (Elasticsearch, Logstash, Kibana) – a popular open-source alternative used for log aggregation, search, and visualization.
--> Wazuh – a free, open-source SIEM/XDR platform that's very popular for beginners practicing at home because it's free and comes with pre-built detection rules.

## SOC (Security Operations Center)

--> A SOC is the team (and often the physical/virtual space) responsible for continuously monitoring, detecting, analyzing, and responding to security incidents across an organization — typically operating 24/7.
--> A SOC is usually structured in tiers:
--> Tier 1 Analyst – Front-line monitoring, triages incoming SIEM alerts, decides if something is a false positive or needs escalation. This is the most common entry-level SOC job.
--> Tier 2 Analyst – Handles escalated/confirmed incidents, performs deeper investigation, begins containment actions.
--> Tier 3 / Threat Hunter – Proactively searches for hidden/advanced threats that automated tools missed, handles the most complex incidents, and often helps tune detection rules to reduce false positives.
--> A SOC's core mission can be summarized as: see everything, understand what matters, and act fast enough to limit damage.

## Basic Log Analysis Workflow Example: Spotting a Brute-Force Attack in a Linux Auth Log

--> On a Linux system, authentication attempts (SSH logins, sudo usage, etc.) are recorded in `/var/log/auth.log` (Debian/Ubuntu) or `/var/log/secure` (RHEL/CentOS).
--> A brute-force attack is when an attacker repeatedly guesses usernames/passwords, trying to log in over and over until (hopefully, for them) one combination works.

What to look for in the log:

1. A high volume of "Failed password" entries in a short time window, especially from the same source IP.
```
Aug  6 02:14:01 server sshd[2211]: Failed password for root from 45.33.12.9 port 51422 ssh2
Aug  6 02:14:02 server sshd[2211]: Failed password for root from 45.33.12.9 port 51423 ssh2
Aug  6 02:14:03 server sshd[2211]: Failed password for admin from 45.33.12.9 port 51424 ssh2
Aug  6 02:14:04 server sshd[2211]: Failed password for admin from 45.33.12.9 port 51425 ssh2
```
   --> A pattern like this — dozens or hundreds of failed attempts within seconds from one IP, often trying multiple different usernames — is a classic automated brute-force signature.

2. Repeated attempts against common/default account names like `root`, `admin`, `test`, `oracle` (attackers often try well-known usernames rather than real employee names first).

3. A "Failed password" streak followed by an "Accepted password" or "Accepted publickey" entry from the SAME source IP — this is the critical, most important line to catch.
```
Aug  6 02:15:47 server sshd[2211]: Accepted password for admin from 45.33.12.9 port 51601 ssh2
```
   --> This single line means the brute-force attack SUCCEEDED. This is the moment an analyst must escalate immediately — from "someone is knocking on the door" to "someone is inside the house."

4. Logins occurring at unusual times (3 AM local time for an account that only ever logs in during business hours) or from unusual geographic locations (an account that always logs in from the same city suddenly logging in from a foreign country) — this is anomaly-based thinking applied manually.

5. Cross-referencing what that account did immediately AFTER logging in (checking command history, sudo logs, or other application logs) to determine if any real damage/access occurred, feeding directly into the Identification and Containment phases of the IR lifecycle covered above.

--> Basic defensive response to this exact scenario: block the offending IP at the firewall, force a password reset on the targeted account, enable/verify fail2ban (a tool that automatically bans IPs after a set number of failed attempts) is running, and check if MFA/SSH key-only authentication can be enforced going forward.

## Blue Team Practice Platforms

--> Reading theory is necessary but not sufficient — hands-on practice with realistic logs and simulated incidents is how the concepts in this chapter actually become second nature. A few widely recommended platforms for beginners:

--> LetsDefend – A hands-on platform built specifically around SOC Analyst training. It gives you a simulated SIEM dashboard with real alerts (phishing, malware, brute-force, etc.) that you must investigate and respond to, exactly like a real Tier 1 SOC job — genuinely one of the best beginner resources for this exact chapter's content.
--> TryHackMe SOC Level 1 Path – A structured, guided learning path covering SIEM fundamentals, log analysis, network security monitoring tools (Suricata, Zeek), and incident response, using guided rooms with step-by-step exercises rather than throwing you into the deep end.
--> Both platforms are specifically designed for total beginners transitioning into blue team / SOC roles, and both are commonly referenced in entry-level SOC job postings as relevant, credible practice experience.

## Tying the Whole Theory Set Together

--> Chapter 1 gave you the vocabulary (CIA triad, threat/vulnerability/risk).
--> Chapters 2 and 3 covered how systems and networks are defended (firewalls, patching, least privilege).
--> Chapter 4 covered how attackers actually get in (malware, social engineering).
--> This chapter closes the loop: what a real defensive team does when all of the above still fails and an incident happens anyway — detect it (SIEM/SOC), contain and fix it (IR lifecycle), and learn from it so it's harder for the same thing to happen twice.
