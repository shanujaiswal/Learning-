# The Problem SOAR Solves -- Alert Fatigue

--> A SIEM (covered in the Incident Response file) generates alerts, but a busy security team can face hundreds or thousands per day -- manually investigating and responding to each one is unsustainable, and analysts inevitably start missing genuine threats buried in the noise ("alert fatigue").
--> SOAR (Security Orchestration, Automation and Response) sits on top of a SIEM and other security tools, automating the REPETITIVE parts of investigation and response, so human analysts focus their limited time on the judgment calls that actually need it.

# Orchestration -- Connecting Disparate Tools Together

--> "Orchestration" means coordinating actions across MULTIPLE separate security tools (the SIEM, the firewall, the EDR platform, the ticketing system, threat intelligence feeds) as one coherent workflow, rather than an analyst manually logging into five different consoles for every single incident.

# Automation -- Playbooks for Repetitive Response Steps

--> A Playbook defines an automated sequence of steps to run when a specific type of alert fires -- e.g. for a "suspicious login from a new country" alert: automatically check the user's recent activity, query a threat intelligence feed (connecting to the CTI file) on the source IP's reputation, and if the IP is known-malicious, automatically disable the account and open a ticket -- all without a human needing to manually perform each of those individual steps.

```
Trigger: SIEM alert -- "Impossible travel" login detected
   |
   v
1. Automatically query threat intel feed for source IP reputation
2. Automatically pull the user's last 10 login events for context
3. IF source IP is flagged malicious:
       --> Automatically disable the user account
       --> Automatically create a high-priority ticket with all gathered context attached
   ELSE:
       --> Create a lower-priority ticket for analyst review, WITH the context already gathered
```

--> Even when a playbook doesn't fully automate the RESPONSE (disabling an account might deliberately require human approval for a sensitive action), it still automates the tedious INVESTIGATION/context-gathering, so by the time an analyst looks at it, the relevant information is already assembled rather than requiring 20 minutes of manual lookup first.

# Response -- Consistent, Fast Action

--> SOAR platforms can take response actions directly (isolating an endpoint via EDR integration, blocking an IP at the firewall, disabling an Active Directory account) far faster than a human could manually execute the same steps across multiple systems -- critical during an active incident where dwell time directly correlates with damage.

# Case Management

--> SOAR platforms typically also handle case/ticket management for security incidents specifically -- tracking an incident's full timeline, every automated action taken, and analyst notes in one place, integrating with (or replacing) generic IT ticketing tools for security-specific workflows.

# SOAR vs SIEM -- How They Relate

--> SIEM's job is DETECTION -- collecting and correlating logs to identify that something suspicious happened.
--> SOAR's job is RESPONSE -- taking the SIEM's alert and automating the investigation/action that follows, closing the loop between "we detected something" and "we did something about it."
--> Modern security operations centers (SOCs) typically run both together, often from the same vendor, as an integrated detection-and-response pipeline rather than two disconnected tools.

# Common SOAR Platforms

--> Splunk SOAR, Palo Alto Cortex XSOAR, Microsoft Sentinel's built-in automation -- each providing a playbook-building interface (often visual/drag-and-drop) for defining these automated workflows without requiring every analyst to be a skilled programmer.

# Why SOAR Matters for Incident Response Speed

--> Mean Time to Respond (MTTR) is a key incident response metric (connecting back to the PICERL lifecycle covered in the Incident Response file) -- SOAR's core value proposition is directly reducing MTTR by removing manual, repetitive delay from the response process, which matters enormously given that attacker dwell time (how long they operate undetected/unresponded-to) is one of the strongest predictors of breach severity.
