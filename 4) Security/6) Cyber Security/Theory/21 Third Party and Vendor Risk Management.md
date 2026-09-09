# Why Your Security Posture Includes Everyone You Trust

--> An organization can have excellent internal security controls and still be breached through a VENDOR -- a third-party software library, a cloud service provider, a contractor with system access, or a supplier with a network connection into your environment. The Target breach (via an HVAC vendor's stolen credentials) and the SolarWinds/Log4Shell supply-chain incidents referenced in the DevSecOps file are exactly this risk category, at massive scale.

# The Vendor Risk Assessment Lifecycle

--> **Due diligence before onboarding** -- assessing a prospective vendor's security posture BEFORE granting them any access or integrating their software, typically via security questionnaires, requesting compliance certifications (SOC 2, ISO 27001, referenced in the GRC file), and sometimes the right to audit.
--> **Contractual security requirements** -- data protection clauses, breach notification timelines, and security standards written explicitly into the vendor contract, not left as an informal assumption.
--> **Ongoing monitoring** -- a vendor's risk profile isn't fixed at onboarding -- periodic reassessment, monitoring for the vendor's own breach disclosures, and tracking whether their compliance certifications remain current.
--> **Offboarding** -- revoking a vendor's access completely and promptly when the relationship ends, mirroring the employee offboarding hygiene covered in the Insider Threat file, since a forgotten, still-active vendor integration is functionally the same risk as a forgotten employee account.

# Tiering Vendors by Risk Level

--> Not every vendor warrants the same scrutiny -- a vendor with direct access to production systems or sensitive customer data (a payment processor, a cloud infrastructure provider) is a fundamentally different risk than a vendor providing an offline service with no system access at all (an office supplies vendor).
--> Risk tiering lets a security team focus limited due-diligence effort where it actually matters, rather than applying the same exhaustive questionnaire to every vendor relationship regardless of actual exposure.

# Software Supply Chain Risk -- A Specific, Growing Category

--> Every third-party library/dependency a codebase relies on (npm packages, Python packages, referenced throughout the Full Stack notes) is itself a vendor relationship, whether treated that way or not -- the DevSecOps file's coverage of SBOMs and SCA (Software Composition Analysis) scanning is the TECHNICAL control most directly addressing this specific sub-category of vendor risk.
--> The SolarWinds attack specifically compromised a trusted vendor's build pipeline, distributing malicious code through what looked like a completely legitimate, signed software update -- illustrating that even a vendor's OWN internal security failure becomes every one of their customers' problem simultaneously.

# Fourth-Party Risk

--> A vendor often relies on ITS OWN vendors/subprocessors (e.g. your SaaS vendor hosts their service on a cloud provider, uses a separate payment processor) -- this "fourth-party" risk is genuinely harder to assess directly, since you have no direct contractual relationship with them, and typically relies on your direct vendor's own due diligence being sound.

# Right-Sizing Vendor Risk Programs

--> A vendor risk program that's too heavy-handed slows business operations and gets routinely bypassed under deadline pressure; one that's too light misses genuinely high-risk relationships -- the risk-tiering approach above, combined with the quantitative risk framing (ALE) covered in the GRC file, helps calibrate effort proportionally rather than treating vendor risk management as an all-or-nothing checkbox exercise.

# Connecting Back to Incident Response

--> A vendor-caused incident still requires YOUR organization's incident response process (covered in that file) to activate -- vendor contracts should explicitly define breach notification timelines specifically so your incident response clock starts as early as possible, rather than learning about a vendor's breach weeks after the fact through public disclosure.
