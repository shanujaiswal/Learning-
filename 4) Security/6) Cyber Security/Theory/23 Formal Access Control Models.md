# Why Formal Models Exist Beyond "Just Use Least Privilege"

--> The IAM file and the Database Access Control file cover PRACTICAL, real-world access control (roles, permissions, GRANT/REVOKE) -- this file covers the FORMAL, academic MODELS that underpin how access control is reasoned about theoretically, particularly in high-assurance environments (military, government, healthcare) where access control decisions need to be provably correct, not just practically reasonable.

# Discretionary Access Control (DAC)

--> The OWNER of a resource decides who else can access it, and can grant/revoke that access at their own discretion -- this is the model underlying standard Linux file permissions (covered in the File Systems, Permissions and System Calls file) and Windows NTFS ACLs -- a file's owner can `chmod` it to grant others access, entirely at their own judgment, without any centralized policy dictating whether that's actually appropriate.
--> **The core weakness of DAC** -- because individual users control sharing decisions, there's no guarantee the CUMULATIVE effect of many individual sharing decisions across an organization matches any coherent overall security policy -- a well-meaning employee can inadvertently over-share sensitive data simply because DAC gives them that discretion, with no organizational-level check preventing it.

# Mandatory Access Control (MAC)

--> Access decisions are made by a CENTRAL AUTHORITY based on a fixed security policy, and individual users/resource-owners CANNOT override it, no matter what they personally decide -- the opposite philosophy from DAC's owner-discretion model.
--> Every subject (user/process) and every object (file/resource) is assigned a SECURITY LABEL (e.g. "Top Secret," "Secret," "Confidential," "Unclassified") and the system enforces access based purely on comparing these labels, regardless of any individual's personal decision to share or not share.

## The Bell-LaPadula Model -- Protecting Confidentiality

--> Developed for military/government use, Bell-LaPadula's entire purpose is preventing sensitive information from flowing to lower-clearance levels -- it enforces two core rules:
--> **Simple Security Property ("no read up")** -- a subject at a given clearance level CANNOT read data classified at a HIGHER level -- someone with "Secret" clearance cannot read "Top Secret" documents, no matter what.
--> **Star Property ("no write down")** -- a subject at a given clearance level CANNOT write data DOWN to a lower classification level -- someone with "Top Secret" clearance working on a Top Secret document cannot copy/paste its contents into an "Unclassified" document, even though they're technically ALLOWED to read the Top Secret content itself. This second rule specifically prevents a high-clearance insider from deliberately or accidentally LEAKING sensitive information down to a level where less-cleared people could access it.

```
Bell-LaPadula, visualized:

  TOP SECRET   <-- can READ everything below, but CANNOT WRITE down to lower levels
      |
   SECRET      <-- can READ Secret and below, CANNOT read Top Secret, CANNOT write down
      |
CONFIDENTIAL   <-- etc.
      |
 UNCLASSIFIED
```

--> Bell-LaPadula is entirely focused on CONFIDENTIALITY (preventing unauthorized disclosure) -- it says nothing about protecting data's INTEGRITY (preventing unauthorized, incorrect modification), which is precisely the gap the Biba Model was designed to fill.

## The Biba Model -- Protecting Integrity

--> Biba is essentially Bell-LaPadula's MIRROR IMAGE, applied to integrity instead of confidentiality -- its concern isn't "who can see this sensitive data" but "can this data be trusted as accurate and uncorrupted."
--> **Simple Integrity Property ("no read down")** -- a subject at a HIGH integrity level cannot read data from a LOWER integrity level -- a highly-trusted system process shouldn't ingest and rely on data originating from a less-trusted, potentially-corrupted or attacker-controlled source.
--> **Integrity Star Property ("no write up")** -- a subject at a LOW integrity level cannot write to a HIGHER integrity level -- untrusted or lower-privilege input shouldn't be able to directly modify highly-trusted system data or configuration.

```
Biba, visualized (the mirror of Bell-LaPadula):

  HIGH INTEGRITY   <-- cannot READ from lower levels (might be corrupted/untrustworthy),
      |                 CAN write down (a trusted process's clean output can safely flow to lower systems)
  MEDIUM INTEGRITY
      |
  LOW INTEGRITY    <-- cannot WRITE up to higher levels (an untrusted process shouldn't be able to
                        directly corrupt trusted system state)
```

--> **A genuinely useful real-world intuition for Biba** -- this is conceptually why user input should never be trusted to directly modify critical system configuration without going through validation (directly connecting to the Injection Attacks file's core lesson) -- Biba formalizes exactly that intuition as a rigorous access control model, applicable well beyond just web application input validation.

# Role-Based Access Control (RBAC)

--> Rather than assigning permissions directly to individual USERS (which becomes unmanageable at scale, as covered practically in the Database Access Control file's discussion of Roles), RBAC assigns permissions to ROLES, and users are assigned to one or more roles -- the model underlying most real-world enterprise IAM systems (covered in the IAM file) and most database permission systems in practice.

```
Roles: "Analyst" -> [read reports, read dashboards]
        "Manager" -> [read reports, read dashboards, approve budgets]
        "Admin"    -> [everything]

Users are assigned to roles, not given individual permissions directly:
  Alice -> Analyst
  Bob    -> Manager
```

--> **Role hierarchies** -- roles can inherit permissions from other roles (a "Manager" role automatically includes everything an "Analyst" role can do, plus more), reducing redundant permission assignment and keeping the overall permission structure easier to reason about and audit.

# Attribute-Based Access Control (ABAC)

--> A more flexible, fine-grained model than RBAC -- access decisions are based on evaluating ATTRIBUTES of the subject, the resource, and the environmental context, combined via policy rules, rather than a fixed role assignment alone.

```
Example ABAC policy:
  ALLOW access to a medical record IF:
    subject.role == "doctor"
    AND subject.department == resource.patient.department
    AND time.hour BETWEEN 8 AND 18
    AND subject.location == "hospital network"
```

--> This lets access decisions account for CONTEXT that a simple, static role assignment can't capture -- a doctor accessing a patient record during business hours from the hospital network is treated differently from the SAME doctor attempting the same access at 3 AM from an unrecognized external network, directly connecting to the risk-based, context-aware philosophy underlying Zero Trust Architecture (covered in its own file), which is essentially ABAC's principles applied at the network access-control layer.

# Why These Formal Models Still Matter in Practice

--> Most real-world systems (covered practically throughout the IAM, Database Access Control, and Cloud Security files) use RBAC or ABAC-influenced approaches day-to-day, not literal Bell-LaPadula/Biba implementations -- but understanding these formal models gives a PRINCIPLED vocabulary and mental framework for reasoning about WHY a given access control design is (or isn't) actually sound -- e.g. recognizing that a system letting low-trust user input directly modify high-trust configuration data is violating Biba's core principle, even if nobody on the team has ever heard the term "Biba Model," gives you language and a formal basis for identifying and explaining exactly what's wrong and why it matters.
