### SOC 2, NIST 800-53, FedRAMP, and CIS Controls

--> Chapter 8 covered ISO 27001 (the certifiable ISMS), NIST CSF (the five/six-function strategic language), and the big three regulatory frameworks (GDPR, HIPAA, PCI-DSS). Those cover "does this org have a security PROGRAM" and "is this org legally allowed to touch this specific data type." This chapter covers four more frameworks a working security/GRC professional runs into constantly, each answering a slightly different question: SOC 2 ("can I trust this SaaS vendor?"), NIST 800-53/RMF ("does this US federal system meet a prescribed, auditable control baseline?"), FedRAMP/StateRAMP ("has a cloud provider already proven that to the government, so agencies don't each have to re-check it?"), and CIS Controls ("if I could only implement a short, PRIORITIZED list of controls, which ones matter most first?").

## SOC 2 — Trusting a Vendor's Controls

--> SOC 2 (System and Organization Controls 2) is an attestation report — not a certification like ISO 27001 — produced by an independent CPA/audit firm under AICPA standards, that describes how well a service organization's controls address a chosen set of Trust Service Criteria. It is the single most commonly requested piece of paper in a B2B SaaS security-questionnaire process ("send us your latest SOC 2 report" is standard vendor-onboarding language.)
--> Key distinction from ISO 27001: ISO 27001 certification says "we have a working ISMS, verified against Annex A controls, by an ACCREDITED certification body" and results in a certificate. SOC 2 says "an independent AUDITOR examined our specific controls against these specific criteria and formed an OPINION," and results in a detailed narrative REPORT, not a certificate — there is no such thing as being "SOC 2 certified," only "SOC 2 attested" or "having a SOC 2 report," a common phrasing mistake even among practitioners.

==> The Five Trust Service Criteria (TSC)
--> An organization scopes its SOC 2 audit around one mandatory criterion plus as many of the optional four as are relevant to what it sells:
1. Security (mandatory in every SOC 2 report) — protection against unauthorized access, both physical and logical; this criterion alone overlaps heavily with everything in Chapters 1-13 of this note series (firewalls, MFA, least privilege, patching, IAM).
2. Availability — the system is available for operation and use as committed/agreed (uptime SLAs, redundancy, disaster recovery — connects directly to Chapter 14's BCDR content).
3. Processing Integrity — system processing is complete, valid, accurate, timely, and authorized (relevant for a fintech/billing platform where a bug that silently drops or duplicates transactions is itself a trust failure, even with zero "hack" involved).
4. Confidentiality — information designated as confidential is protected as committed (contract-level NDAs, data-handling agreements — distinct from Privacy below, which is about personal data specifically).
5. Privacy — personal information is collected, used, retained, and disposed of in accordance with the organization's privacy notice and applicable principles (overlaps with GDPR/CCPA obligations, but is assessed here as an internal control commitment rather than a legal filing).
--> A company selling infrastructure/DevOps tooling might only pursue Security + Availability; a healthtech billing platform might pursue all five — the criteria chosen become part of the report's scope statement, so "SOC 2 compliant" without specifying which criteria is an incomplete claim.

==> Type I vs Type II Reports
--> Type I — evaluates whether controls are suitably DESIGNED as of a single point in time (a snapshot: "as of March 1st, these controls existed and were designed appropriately"). Faster and cheaper to obtain, but proves nothing about whether the controls actually operated correctly day-to-day.
--> Type II — evaluates whether controls were suitably designed AND operated effectively over an observation period, typically 3-12 months (the auditor pulls evidence samples throughout that window — e.g., "show me 25 random access-review tickets from this quarter" — not just a one-time interview). This is what serious enterprise customers actually ask for, because it proves sustained operation, not a one-day snapshot that could be staged.
--> Common real-world pattern for an early-stage startup: get a Type I first (fast, unblocks initial enterprise sales conversations), then transition to a Type II covering a 6-12 month period once the controls have had time to actually run and generate evidence.

==> The Audit Process, Practically
```
1. Readiness / Gap Assessment
   -> Often via a compliance automation platform (Vanta, Drata, Secureframe)
      that continuously monitors cloud/IT systems and flags control gaps
      against the chosen TSC before the real audit even starts.
2. Remediation
   -> Fix flagged gaps: turn on MFA everywhere, formalize an access review
      cadence, write missing policies, enable log retention.
3. Observation Period (Type II only)
   -> The controls run for real, generating audit evidence automatically
      (ticket systems, access review sign-offs, vulnerability scan reports)
      throughout the window.
4. Fieldwork
   -> The independent CPA firm samples evidence, interviews control owners,
      and tests whether controls actually operated as described.
5. Report Issuance
   -> The auditor issues an opinion: unqualified (clean), qualified (some
      exceptions noted), adverse, or disclaimer of opinion (rare, serious).
   -> The finished report is NOT public -- it's shared under NDA with
      prospective customers during their own vendor due-diligence process,
      which is why "SOC 2 report" functions as a trust currency between
      companies rather than a public badge.
```

## NIST 800-53 and the Risk Management Framework (RMF)

--> NIST SP 800-53 ("Security and Privacy Controls for Information Systems and Organizations") is a comprehensive CATALOG of specific, granular security and privacy controls, organized into 20 control families (Access Control, Audit and Accountability, Incident Response, System and Communications Protection, and so on). Where NIST CSF (Chapter 8) is a strategic five-function LANGUAGE and ISO 27001's Annex A is a ~93-control list an organization self-selects from, 800-53 is the much deeper, mandatory-for-US-federal-systems technical control catalog that 800-53 CONTROLS actually implement — in practice, an organization often maps CSF functions down to specific 800-53 controls as the concrete "how."
--> Each control has a baseline-appropriate set of enhancements — e.g., control family AC (Access Control) includes AC-2 (Account Management), AC-3 (Access Enforcement), AC-6 (Least Privilege) — and controls are tagged into Low, Moderate, and High baselines (from FIPS 199's impact categorization) so a system's required control SET scales with how damaging a breach of that system would actually be.

==> The Risk Management Framework (RMF) — How 800-53 Actually Gets Applied
--> RMF is the NIST SP 800-37 process that takes the 800-53 control catalog and turns it into an actual, auditable Authorization to Operate (ATO) for a specific federal information system. Seven steps:
1. Prepare — organizational and system-level groundwork: identify stakeholders, risk tolerance, common controls that can be inherited from elsewhere.
2. Categorize — classify the system's impact level (Low/Moderate/High) per FIPS 199, based on the confidentiality/integrity/availability impact of a breach — this single step determines which 800-53 control baseline applies.
3. Select — choose the specific control baseline (and any tailoring/overlays) from 800-53 matching the categorization.
4. Implement — actually deploy the selected controls in the real system.
5. Assess — an independent assessor (similar in spirit to a SOC 2 auditor) tests whether the controls are implemented correctly and operating as intended, producing a Security Assessment Report (SAR).
6. Authorize — an Authorizing Official (a senior federal risk-owner) reviews the SAR and formally decides whether to grant an Authority to Operate (ATO) — explicitly accepting the residual risk on the organization's behalf. This is the RMF's version of "someone with real authority signed off," directly echoing the Governance leg of GRC from Chapter 8.
7. Monitor — continuous monitoring of the system post-authorization (vulnerability scans, control reassessment, configuration change tracking) so the ATO reflects ongoing reality rather than a stale point-in-time approval — an ATO is not "certified forever," it requires continuous evidence to stay valid.
--> Mnemonic: Prepare, Categorize, Select, Implement, Assess, Authorize, Monitor (P-C-S-I-A-A-M) — notice the same underlying PDCA-style loop from ISO 27001 (Chapter 8) reappears here at federal-system granularity: categorize/select is Plan, implement is Do, assess is Check, authorize+monitor closes the loop back into ongoing Act.

## FedRAMP and StateRAMP — RMF for Cloud Providers, Reused

--> FedRAMP (Federal Risk and Authorization Management Program) exists to solve a specific inefficiency: without it, every single US federal agency wanting to use, say, a particular cloud storage provider would have to independently run its own full RMF assessment of that same provider — massively duplicated effort across agencies assessing the identical underlying cloud service.
--> FedRAMP instead has a cloud service provider go through ONE rigorous 800-53-based assessment (via an accredited Third-Party Assessment Organization, a "3PAO"), aligned to the same Low/Moderate/High impact baselines as RMF, resulting in a FedRAMP Authorization that other federal agencies can then RE-USE ("authorize to use") without redoing the assessment from scratch — a form of "assess once, use many times."
--> StateRAMP is the same underlying idea applied at the US state/local government level (many state and local government bodies also need cloud assurance but sit outside FedRAMP's federal-only scope) — a cloud vendor with FedRAMP or StateRAMP authorization is signaling the exact same category of rigor SOC 2 signals for commercial B2B buyers, just aimed at government buyers and built directly on the 800-53/RMF backbone instead of AICPA's Trust Service Criteria.
--> Practical takeaway for reading a cloud vendor's compliance page: SOC 2 = "trust me for commercial B2B," FedRAMP/StateRAMP = "trust me to hold government data," and a large cloud provider (AWS, Azure, GCP — Chapter 7) typically holds all of the above simultaneously across different specific services/regions, because each addresses a different buyer's due-diligence requirement.

## CIS Controls and CIS Benchmarks — A Prioritized, Practical Framework

--> The CIS Controls (from the Center for Internet Security) take a deliberately different philosophy from everything above: rather than a comprehensive catalog an organization tailors (800-53) or a management-system PROCESS (ISO 27001), CIS Controls are an explicitly PRIORITIZED, ordered list of ~18 controls, designed around the idea that a resource-constrained organization should implement them roughly IN ORDER because the early ones stop the most common real-world attacks.
--> Selected examples from the 18 (in their prioritized order): CIS Control 1 (Inventory and Control of Enterprise Assets), 2 (Inventory and Control of Software Assets), 3 (Data Protection), 4 (Secure Configuration of Enterprise Assets and Software), 5 (Account Management), 6 (Access Control Management), 7 (Continuous Vulnerability Management), 8 (Audit Log Management) — notice Controls 1-2 are exactly the asset-inventory problem NIST CSF's "Identify" function calls out (Chapter 8): you cannot secure, patch, or monitor an asset you don't know exists.

==> Implementation Groups (IG1 / IG2 / IG3)
--> CIS explicitly acknowledges that not every organization has the same risk profile or resources, and splits the ~153 underlying Safeguards (the specific sub-actions inside the 18 Controls) into three cumulative Implementation Groups:
1. IG1 — "Essential Cyber Hygiene": the baseline every organization, regardless of size or sophistication, should implement — basic asset inventory, basic access control, basic patching, basic logging. Explicitly designed to stop the most common, opportunistic, non-targeted attacks with the LEAST specialized staff/tooling required. A small business with no dedicated security team is realistically expected to reach IG1, not IG3.
2. IG2 — builds on IG1, adding safeguards for organizations with more complex IT environments and a dedicated (even if small) IT/security function — more granular logging, more formal vulnerability management processes, more mature IAM.
3. IG3 — the full safeguard set, aimed at organizations facing sophisticated/targeted attacks and holding sensitive data at scale (mature enterprises, critical infrastructure) — includes safeguards like application allowlisting, deeper incident response capability, and red-team-style validation.
--> Why this matters practically, contrasted with ISO 27001/800-53: those frameworks tell you WHAT the complete target state looks like and let the organization figure out sequencing; CIS explicitly tells you the SEQUENCE itself — "if you can only do ten things this year, do these ten, in roughly this order" — which is why CIS Controls are frequently the first framework a small/mid-size organization actually implements, even if a later compliance requirement (SOC 2, ISO 27001) eventually demands more.

==> CIS Benchmarks — A Different, Complementary Thing
--> CIS Benchmarks are NOT the same artifact as CIS Controls, despite the shared source and easy-to-confuse naming: Benchmarks are highly specific, technical, checklist-style secure-CONFIGURATION guides for a particular product (a CIS Benchmark for Windows Server 2022, one for Ubuntu 22.04, one for a specific version of Docker or Kubernetes), specifying exact settings ("disable SMBv1," "set minimum password length to 14," "disable the guest account").
--> Relationship between the two: CIS Control 4 ("Secure Configuration of Enterprise Assets and Software") is the STRATEGIC control that says "harden your systems' configurations" — a CIS Benchmark is the literal, line-by-line technical CHECKLIST an engineer runs against a specific server/container image to actually satisfy that control in practice. Automated compliance-scanning tools (OpenSCAP, and many commercial vulnerability scanners) can check a live system's configuration directly against a CIS Benchmark and report a percentage-compliant score.

## Tying It Together

--> SOC 2 is an independent AUDITOR's attestation opinion (not a certificate) against chosen Trust Service Criteria, with Type II (evidence sampled over months) carrying far more real trust weight than Type I (a single-day snapshot) — it's the dominant vendor-trust currency in commercial B2B SaaS.
--> NIST 800-53 is the deep, mandatory US-federal control CATALOG that the seven-step Risk Management Framework (Categorize->Select->Implement->Assess->Authorize->Monitor) turns into an actual, continuously-monitored Authority to Operate for a specific system.
--> FedRAMP/StateRAMP apply that exact same 800-53/RMF backbone to cloud service providers ONCE, so many government agencies/states can reuse a single authorization instead of each re-assessing the same cloud service from scratch.
--> CIS Controls flip the framing from "here is the complete target state" to "here is the prioritized ORDER to build it in," with IG1/IG2/IG3 scaling that same ordered list to an organization's actual size and risk profile — and CIS Benchmarks are the separate, product-specific configuration checklists that make CIS Control 4 (and, in practice, patching/hardening work referenced back in Chapter 3) concretely actionable.
--> Across all four frameworks in this chapter plus Chapter 8's ISO 27001/NIST CSF/GDPR/HIPAA/PCI-DSS, the same underlying pattern keeps recurring: define a control set, prove you actually run it (not just that you wrote a policy saying you would), and have someone with real authority formally accept whatever risk remains.
