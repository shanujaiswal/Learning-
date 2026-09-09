# What ICS/SCADA Actually Are

--> Industrial Control Systems (ICS) are the computer systems that monitor and control physical industrial processes -- power grids, water treatment plants, oil pipelines, manufacturing lines. SCADA (Supervisory Control and Data Acquisition) is the specific category of ICS software providing centralized monitoring and control over these systems, often across a wide geographic area (an entire regional power grid, for example). This is a distinct security domain from the IoT and Embedded Device Security file's coverage -- while both involve physical, embedded hardware, ICS/SCADA specifically controls CRITICAL INFRASTRUCTURE, where a successful attack can cause genuine physical damage, injury, or death, not just data theft or service disruption.

# The ICS Architecture -- How These Systems Are Structured

```
Level 4-5: Enterprise IT Network (business systems, email, standard corporate IT)
              |
         [ Firewall/DMZ -- the critical boundary ]
              |
Level 3: Operations Management (historian databases, production scheduling)
              |
Level 2: Supervisory Control (SCADA/HMI -- Human-Machine Interface, operator dashboards)
              |
Level 1: Control Systems (PLCs -- Programmable Logic Controllers, RTUs -- Remote Terminal Units)
              |
Level 0: Physical Process (actual sensors, valves, motors, pumps, turbines)
```

--> This layered model (based on the Purdue Enterprise Reference Architecture, the standard reference framework for ICS network design) is central to ICS security -- the entire discipline revolves around properly SEGMENTING these levels so that a compromise of the enterprise IT network (Level 4-5, where standard cybersecurity attacks like phishing and malware, covered throughout the Cyber Security track, are most common) doesn't have a direct path down to the systems actually controlling physical equipment (Levels 0-2).

# PLCs -- Programmable Logic Controllers

--> A PLC is a ruggedized, purpose-built industrial computer that directly controls physical equipment based on programmed logic -- "if sensor reads pressure above X, close this valve" -- running continuously, often for years without a reboot, in harsh physical environments (extreme temperatures, vibration, dust) that standard IT hardware isn't designed to survive.
--> PLCs are programmed using specialized languages, most notably **Ladder Logic** -- a visual programming language deliberately designed to resemble electrical relay circuit diagrams, specifically so that electrical engineers (the traditional workforce operating industrial equipment, not software developers) could understand and modify control logic without needing traditional programming background.

# Why ICS/SCADA Security Is Fundamentally Different From IT Security

## Availability Trumps Confidentiality -- The Inverted CIA Triad Priority

--> The Cyber Security Fundamentals file's CIA Triad (Confidentiality, Integrity, Availability) applies here too, but the PRIORITY ORDER is effectively reversed compared to typical enterprise IT -- in ICS, AVAILABILITY is paramount (a power plant control system going offline can cause a regional blackout with immediate, severe real-world consequences), while in standard IT, confidentiality is often prioritized first (protecting sensitive data).
--> This inverted priority directly explains why standard IT security practices sometimes DON'T transfer cleanly -- a security patch requiring a system reboot might be routine in enterprise IT, but on a PLC controlling a continuously-running chemical process, an unplanned reboot could itself cause a dangerous process disruption, making patch management in ICS environments a genuinely harder, higher-stakes trade-off than in standard IT.

## Extremely Long Equipment Lifespans and Legacy Systems

--> ICS equipment is frequently deployed for 15-25+ years, far longer than typical IT hardware refresh cycles -- meaning a huge proportion of real-world ICS environments run genuinely outdated operating systems (Windows XP or even older embedded systems are still commonly found controlling real industrial equipment today) that no longer receive security patches at all, a stark contrast to the patch-compliance metrics covered in the Security Program Management file, which assume patching is at least generally possible.

## Insecure-by-Design Legacy Protocols

--> Many ICS communication protocols (Modbus, DNP3) were designed decades ago, in an era when these networks were assumed to be physically isolated and trusted -- most have NO built-in authentication or encryption at all, meaning anyone who can reach the network segment can send arbitrary control commands that the receiving PLC will simply execute without question.

```
Modbus (no authentication):
  A command to write a value to a specific "register" (which might directly control
  a physical valve's open/closed state) is accepted from ANY device that can reach
  the PLC on the network -- there's no username/password, no cryptographic signature,
  nothing verifying the command's legitimacy at the protocol level.
```

--> This is precisely why the Purdue Model's network SEGMENTATION (shown in the architecture diagram above) is the primary defensive control in ICS security -- since the protocols themselves largely can't authenticate commands, the defense has to come from preventing unauthorized access to the network in the first place, directly connecting to the Zero Trust Architecture and Network Security (Firewalls/VPNs/IDS-IPS) files, applied here with even higher stakes.

# Notable Real-World ICS Attacks

## Stuxnet -- The Landmark Case Study

--> Discovered in 2010, Stuxnet is widely considered the first publicly known cyberweapon specifically designed to cause physical damage -- it targeted Iranian uranium enrichment centrifuges by infecting Windows systems (using multiple genuine zero-day vulnerabilities) that programmed specific Siemens PLCs, then subtly altered the PLC's control logic to spin centrifuges at damaging speeds while simultaneously feeding FALSE "everything is normal" readings back to human operators watching the SCADA monitoring displays.
--> This attack directly illustrates several concepts covered elsewhere in this Security folder working together at unprecedented sophistication -- the initial infection used techniques from the Windows Privilege Escalation and Post-Exploitation files, propagated using methods echoing the Active Directory Attacks file's lateral movement concepts, and specifically targeted PLC ladder logic in a way requiring deep ICS-specific domain knowledge most conventional malware authors don't possess.

## Ukraine Power Grid Attacks (2015, 2016)

--> Attackers gained access to Ukrainian power distribution companies' networks (via conventional spear-phishing, connecting to the Social Engineering Execution file's techniques) and used that IT-network foothold to pivot into the ICS/SCADA systems controlling circuit breakers, remotely opening them to cause actual, physical blackouts affecting hundreds of thousands of people -- a direct, real-world demonstration of exactly the IT-to-OT (Operational Technology) pivot the Purdue Model's segmentation exists specifically to prevent.

# ICS-Specific Defensive Practices

--> **Network segmentation and unidirectional gateways** -- physically or logically enforcing that data can flow ONE WAY (from the control network up to monitoring/historian systems) while blocking any path for commands to flow the opposite direction from the IT network down into control systems -- a stronger guarantee than a standard firewall, since a unidirectional gateway (a "data diode") is physically incapable of passing traffic backward, not merely configured not to.
--> **ICS-aware intrusion detection** -- standard IT IDS/IPS tools (covered in the Network Security file) generally don't understand Modbus/DNP3 traffic content -- specialized ICS security monitoring tools are needed to recognize an anomalous or malicious COMMAND (e.g. a valve command sent at an unusual time from an unusual source) within otherwise-normal-looking industrial protocol traffic.
--> **Physical security as a genuine primary control** -- given how weak the protocols themselves are, physically securing access to control system hardware and network jacks (connecting to the Physical Security concepts covered in the Ethical Hacking track's own physical security file) remains a disproportionately important defensive layer in ICS environments compared to typical enterprise IT.

# Why This Matters as Its Own Specialized Field

--> ICS/SCADA security sits at the genuine intersection of cybersecurity and physical/industrial engineering -- a security professional working in this space needs to understand not just the attack/defense concepts covered throughout this Security folder, but also the real physical consequences and operational constraints (a plant that literally cannot be taken offline for a security patch without significant real-world cost or danger) that make this one of the highest-stakes, most specialized branches of the entire security field.
