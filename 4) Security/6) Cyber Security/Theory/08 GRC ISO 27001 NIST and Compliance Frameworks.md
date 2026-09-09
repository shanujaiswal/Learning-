### GRC, ISO 27001, NIST, and Compliance Frameworks

--> Every technical control covered so far in this note series (firewalls, MFA, least privilege, IR playbooks) eventually needs to be justified, documented, measured, and proven to an auditor, a regulator, or a board of directors. GRC is the discipline that connects "we do good security" to "we can PROVE we do good security, consistently, in a way outsiders can verify." This is often the least exciting-sounding chapter in a security curriculum and also, unglamorously, the one that determines whether a company can legally process credit cards, sell to hospitals, or operate in the EU at all.

## What GRC Means

--> GRC stands for Governance, Risk, and Compliance — three closely related but distinct disciplines that together form how an organization manages security (and broader operational) risk in a structured, accountable way.
--> Governance — the policies, structures, and decision-making processes that define HOW an organization manages security (who is accountable, what the security policy says, how decisions get escalated and approved). Governance answers "who decided this, and how do we know they had the authority to?"
--> Risk (Risk Management) — the process of identifying, assessing, and deciding how to handle threats to the organization (accept, mitigate, transfer, avoid — covered in depth below). Risk answers "what could go wrong, and how bad would it be?"
--> Compliance — demonstrating adherence to external laws, regulations, and internal policies (ISO 27001 certification, PCI-DSS attestation, GDPR compliance). Compliance answers "can we prove, to someone outside the organization, that we're actually doing what we say we do?"
--> These three feed each other in a loop: governance sets the policy -> risk management identifies what needs attention under that policy -> compliance proves (internally and externally) that the policy is actually being followed -> findings from compliance audits feed back into governance to update policy. GRC platforms (ServiceNow GRC, Vanta, Drata) exist specifically to manage this loop at scale instead of tracking it all in spreadsheets.

## ISO 27001 — Information Security Management System (ISMS)

--> ISO/IEC 27001 is the most widely recognized international standard for an Information Security Management System (ISMS) — not a checklist of specific technical controls, but a management SYSTEM/PROCESS for continuously identifying, managing, and reducing information security risk.
--> Getting "ISO 27001 certified" means an accredited external auditor has verified that an organization has a working ISMS in place, following the standard's requirements — it is a common prerequisite for enterprise B2B sales contracts ("we can't sign with you unless you're ISO 27001 certified").

==> The PDCA Cycle (Plan-Do-Check-Act)
--> ISO 27001 is built around continuous improvement via the PDCA cycle, not a one-time project:
1. Plan — establish the ISMS: define scope, conduct a risk assessment, decide which controls (from Annex A) are relevant, and write the Statement of Applicability (SoA) justifying inclusions/exclusions.
2. Do — implement the planned controls and processes in actual day-to-day operations (deploy the technical controls, train staff, roll out the policies).
3. Check — monitor and measure how well the ISMS is actually working: internal audits, management reviews, tracking whether incidents/near-misses reveal gaps.
4. Act — take corrective action on anything the "Check" phase revealed, and feed lessons back into the next "Plan" phase — this is what makes it a CYCLE rather than a one-and-done project; certification requires ongoing surveillance audits (typically annually) to confirm the cycle is still turning, not just a single point-in-time snapshot.

==> Annex A Control Categories (2022 revision structure)
--> Annex A lists the specific candidate controls an organization draws from (not all are mandatory — applicability is justified per-organization in the Statement of Applicability). The 2022 revision groups them into four themes:
1. Organizational controls (e.g., policies for information security, roles and responsibilities, supplier relationships, incident management procedures)
2. People controls (e.g., screening/background checks, terms of employment, security awareness training, disciplinary process for violations)
3. Physical controls (e.g., physical entry controls to buildings/data centers, equipment security, secure disposal of media, clear desk/clear screen policy)
4. Technological controls (e.g., access control, cryptography, network security, malware protection, secure development, logging and monitoring — this is where most of Chapters 1-5 of this note series live within the standard's structure)
--> The point of Annex A isn't memorizing every control number — it's understanding that ISO 27001 forces an organization to think about ALL FOUR categories together. A company with a great firewall (technological) but no background checks (people) and no locked server room (physical) still has a real, unaddressed gap.

## NIST Cybersecurity Framework (CSF) — The 5 Functions

--> The NIST CSF is a voluntary, widely-adopted (especially in the US) framework organizing cybersecurity activities into five core functions. Unlike ISO 27001 (which is a certifiable management system), NIST CSF is a common LANGUAGE/reference model for discussing and organizing a security program — many organizations map their existing program against it without seeking any formal "NIST certification" (which doesn't really exist as such for the CSF itself).

1. Identify
   --> Understand what needs protecting: asset inventory (what hardware/software/data does the organization even have?), business context, risk assessment, governance policies.
   --> Example: maintaining an up-to-date inventory of every server, cloud account, and sensitive data repository the company owns — you cannot protect what you don't know you have, and unknown/unmanaged "shadow IT" assets are a real, recurring root cause of breaches.

2. Protect
   --> The actual safeguards that reduce the likelihood/impact of an incident: access control, awareness training, data security (encryption), maintenance/patching, protective technology (firewalls, endpoint protection).
   --> This function is where the bulk of Chapters 2-3 of this note series conceptually live — firewalls, least privilege, patch management, MFA are all "Protect" function activities.

3. Detect
   --> Timely discovery of security events: continuous monitoring, anomaly/event detection, SIEM alerting, IDS/IPS — everything covered in Chapter 5 of this note series.
   --> Example: a SIEM correlation rule firing on an anomalous login pattern is a "Detect" function activity in NIST terms.

4. Respond
   --> Taking action once an incident is detected: response planning, communications (internal and to affected customers/regulators), analysis, mitigation, improvements — this maps very closely to the IR lifecycle's Containment/Eradication phases from Chapter 5.
   --> Example: activating the incident response plan, containing the compromised host, and notifying legal/PR as required.

5. Recover
   --> Restoring capabilities/services impaired by an incident, and improving resilience for next time: recovery planning, improvements based on lessons learned, communications during restoration.
   --> Example: restoring from clean backups and conducting the post-incident "lessons learned" review — maps closely to the Recovery/Lessons Learned phases from Chapter 5.
--> Mnemonic: IPDRR (Identify, Protect, Detect, Respond, Recover) — note this deliberately reads almost like an expanded version of PICERL from the IR chapter, because NIST CSF is describing the same underlying lifecycle at an organizational-strategy altitude rather than a single-incident altitude.
--> The 2024 CSF 2.0 revision added a sixth function, "Govern," sitting across all the others, explicitly tying the framework back into the GRC governance concept above (policy, roles, risk management strategy, oversight).

## Regulatory / Industry Compliance Frameworks — Brief Overview

--> These differ from ISO 27001/NIST CSF (which are general-purpose, voluntary-by-default frameworks) in that they are legally or contractually MANDATORY for specific industries/data types, often with real financial penalties for violations.

==> GDPR (General Data Protection Regulation)
--> Protects: the personal data of individuals located in the EU (names, emails, IP addresses, location data, biometric data — broadly defined).
--> Applies to: any organization processing EU residents' personal data, regardless of where the organization itself is headquartered — a US company with EU customers is still in scope.
--> Key requirements: lawful basis for processing data, data breach notification within 72 hours of discovery, the "right to be forgotten" (users can request deletion of their data), data minimization (only collect what's actually needed), and mandatory Data Protection Officers (DPOs) for certain organizations.
--> Penalties: up to €20 million or 4% of global annual revenue, whichever is higher — among the most severe compliance penalty structures in the world, which is why GDPR is treated as a board-level risk topic even at non-EU companies with EU customers.

==> HIPAA (Health Insurance Portability and Accountability Act)
--> Protects: PHI (Protected Health Information) — medical records, treatment history, health insurance information tied to an identifiable individual.
--> Applies to: "Covered Entities" (hospitals, doctors, health insurers) and their "Business Associates" (any third-party vendor that handles PHI on a covered entity's behalf, e.g., a cloud provider hosting a hospital's patient records).
--> Key requirements: administrative, physical, and technical safeguards for PHI (this three-category structure is deliberately similar in spirit to ISO 27001's control themes); mandatory breach notification; strict access controls and audit logging on who accessed which patient's records.
--> This is a US-specific law, but any company selling into US healthcare (including plenty of cloud/SaaS vendors) needs a signed Business Associate Agreement (BAA) and demonstrable HIPAA-compliant controls.

==> PCI-DSS (Payment Card Industry Data Security Standard)
--> Protects: cardholder data (card numbers, expiration dates, CVV, magnetic stripe/chip data).
--> Applies to: any organization that stores, processes, or transmits credit/debit card data — from a tiny online store using Stripe to a massive bank-issued card processor; the compliance LEVEL required scales with transaction volume.
--> Key requirements (from its 12 core requirements): build/maintain a secure network (firewalls), encrypt cardholder data in transit and at rest, restrict access on a need-to-know basis (least privilege again), regularly test security systems (vulnerability scans, penetration testing), maintain an information security policy.
--> Unlike GDPR/HIPAA (government laws with government enforcement), PCI-DSS is an industry-mandated standard created and enforced by the payment card brands (Visa, Mastercard, etc.) via contractual agreements with merchants and processors — non-compliance risks fines and, in serious cases, losing the ability to process card payments at all.

## Risk Assessment Methodology

--> Risk assessment is the structured process of identifying what could go wrong, estimating how likely it is and how bad it would be, and using that to prioritize which risks get addressed first — because no organization has infinite budget to fix every possible risk simultaneously.
--> Core formula underlying nearly all risk assessment: Risk = Likelihood x Impact.

==> Qualitative Risk Assessment
--> Uses descriptive/relative scales (Low/Medium/High, or a 1-5 numeric scale used descriptively rather than mathematically) rather than precise financial figures.
--> Faster, cheaper, doesn't require hard data — good for a first-pass triage across a large number of risks, or for organizations without mature loss-history data.
--> Weakness: subjective — two different risk assessors might rate the same risk differently, and "High" doesn't tell a CFO how many actual dollars are at stake.

==> Quantitative Risk Assessment
--> Assigns actual monetary/numeric values to likelihood and impact, most commonly via the Single Loss Expectancy / Annualized Loss Expectancy model:
```
SLE (Single Loss Expectancy) = Asset Value x Exposure Factor
   -- Exposure Factor = the % of the asset's value that would be lost
      in a single incident (e.g., a ransomware attack destroying 60%
      of a database's business value = Exposure Factor of 0.6)

ARO (Annualized Rate of Occurrence) = how many times per year this
   risk is expected to actually occur (e.g., 0.2 = expected once every
   5 years)

ALE (Annualized Loss Expectancy) = SLE x ARO
   -- This is the number that lets you directly compare a risk's cost
      against the cost of a proposed control, in the same currency.
```
--> Worked example: A company's customer database is valued at $2,000,000. A ransomware attack is estimated to destroy/corrupt 50% of its usable value (Exposure Factor 0.5) before recovery. Based on industry threat intelligence, this specific type of attack is estimated to occur once every 4 years (ARO = 0.25).
```
SLE = $2,000,000 x 0.5        = $1,000,000
ALE = $1,000,000 x 0.25       = $250,000 per year
```
--> If a proposed control (better backups, EDR, network segmentation) costs $80,000/year to implement and reduces the ARO to once every 10 years (ARO = 0.1), the new ALE becomes $100,000/year — a $150,000/year risk reduction for an $80,000/year cost, a clearly justifiable investment. This is exactly the kind of number a CFO/board actually wants to see instead of "High risk, please approve budget."
--> Weakness: requires reliable historical data/threat intelligence to produce meaningful ARO/impact figures — often difficult to obtain precisely for novel or rare risks, making the numbers somewhat approximate in practice even though they look precise.

==> Likelihood x Impact Risk Matrix (Worked Example)
--> A standard qualitative tool: plot each identified risk on a grid of Likelihood (rows) against Impact (columns), then prioritize by which quadrant it lands in.
```
                     IMPACT
              Low      Medium      High
            +--------+--------+--------+
   High     | Medium | High   | CRITICAL|
LIKELIHOOD  +--------+--------+--------+
   Medium   | Low    | Medium | High    |
            +--------+--------+--------+
   Low      | Low    | Low    | Medium  |
            +--------+--------+--------+
```
--> Example risks plotted on this matrix:
--> "Unpatched public-facing web server with a known critical CVE" -> High Likelihood (it's internet-facing and actively scanned) x High Impact (public server, customer-facing) = CRITICAL. Fix immediately.
--> "Insider accidentally emails an internal document to the wrong colleague" -> Medium Likelihood x Low Impact (non-sensitive internal doc) = Low. Acceptable, monitor.
--> "A specific, obscure zero-day in a legacy internal tool no one outside the company knows exists" -> Low Likelihood (nobody's found/targeted it) x High Impact (if it were exploited, full system compromise) = Medium. Address, but not with the same urgency as the CRITICAL item.
--> After scoring, an organization typically decides on one of four risk treatment strategies for each item: Mitigate (apply a control to reduce likelihood/impact — patch the server), Accept (the risk is low enough / cost of fixing outweighs the benefit — formally sign off and move on), Transfer (shift the financial impact elsewhere — buy cyber insurance), or Avoid (eliminate the risk entirely by not doing the risky activity — decommission the legacy tool instead of trying to secure it).

## Tying It Together

--> GRC is the organizational-level structure (Governance sets policy, Risk Management prioritizes what to fix, Compliance proves it's actually being done) that everything else in this note series eventually has to plug into to be taken seriously by auditors, regulators, and customers.
--> ISO 27001 is a certifiable management SYSTEM built on continuous PDCA improvement, covering organizational/people/physical/technological controls together — not a one-time technical checklist.
--> NIST CSF's five (now six, with Govern) functions describe the same detect-respond-recover lifecycle as Chapter 5's IR process, but framed at a strategic/organizational altitude rather than a single-incident altitude.
--> GDPR, HIPAA, and PCI-DSS are mandatory, legally/contractually enforced frameworks tied to SPECIFIC data types (EU personal data, health records, card data respectively) rather than general voluntary best practice.
--> Risk assessment — qualitative for speed/triage, quantitative (SLE x ARO = ALE) for defensible budget conversations — is the mechanism that turns "there are a thousand possible things that could go wrong" into a prioritized, fundable action list.
