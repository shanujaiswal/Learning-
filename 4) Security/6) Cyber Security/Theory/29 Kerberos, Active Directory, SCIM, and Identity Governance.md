### Kerberos, Active Directory, SCIM, and Identity Governance

--> Chapter 6 covered SSO, SAML, OAuth/OIDC, MFA, and PAM/JIT from the perspective of modern, mostly cloud/web-native identity. This chapter goes one layer deeper in two directions: BACKWARD, into the on-premises directory/ticketing mechanics (Kerberos, Active Directory, LDAP) that still run most large enterprises' internal authentication underneath all that modern SSO, and FORWARD, into the governance/provisioning machinery (SCIM, IGA, CIEM) that manages identity and entitlements at scale once an organization has thousands of users across dozens of systems.

## LDAP — The Directory Underneath Everything

--> LDAP (Lightweight Directory Access Protocol) is a protocol for reading and writing entries in a DIRECTORY — a specialized, hierarchical database optimized for fast lookups of "who/what" information (users, groups, computers, organizational units) rather than the transactional read/write workloads a relational database is built for.
--> Directory data is organized as a tree (the Directory Information Tree, DIT), with each entry identified by a Distinguished Name (DN) describing its exact path down that tree — conceptually similar to a filesystem path, but for identity objects:
```
DN: CN=Jane Doe,OU=Finance,OU=Employees,DC=acmecorp,DC=com

  CN = Common Name        (the specific object -- "Jane Doe")
  OU = Organizational Unit (a container/grouping, like a folder --
                             "Finance", nested inside "Employees")
  DC = Domain Component    (the domain itself, split into labels --
                             "acmecorp" then "com")
```
--> Every user/group/computer object in the directory carries attributes (email, phone, group memberships, account status) that applications query via LDAP rather than each maintaining their own separate user database — this is precisely what lets a single directory be the SOURCE OF TRUTH that dozens of internal applications authenticate/authorize against, the on-premises ancestor of the IdP concept from Chapter 6's SSO section.
--> LDAP binds (the operation of actually authenticating against the directory) can be anonymous, simple (username + password sent, ideally only over LDAPS — LDAP over TLS — never plaintext), or SASL (a more flexible, pluggable authentication mechanism) — a genuinely common, still-seen real-world misconfiguration is an application configured to bind over plain unencrypted LDAP, sending credentials across the network in the clear.

## Active Directory — Microsoft's LDAP-Based Directory Service

--> Active Directory (AD) is Microsoft's directory service — it implements LDAP (among other protocols) as its query interface, but adds a huge amount of Windows-specific structure on top: Group Policy (centrally pushed configuration/security settings to every joined machine), Domain Controllers (DCs — the servers that actually host the directory and answer authentication requests), and Kerberos (below) as its default authentication protocol rather than simple LDAP binds.
--> Core objects: Users, Groups (Security Groups control access; Distribution Groups are just email lists), Organizational Units (OUs — containers used to apply Group Policy and delegate administrative control over a subset of the directory), and Computer objects (every domain-joined Windows machine itself has a directory entry and its own credential).
--> Domain, Tree, Forest — AD's structural hierarchy: a Domain is a single administrative/security boundary (e.g., `acmecorp.com`); a Tree is a set of domains sharing a contiguous namespace (`acmecorp.com`, `emea.acmecorp.com`); a Forest is the outermost boundary, a collection of one or more trees that trust each other and share a common schema/configuration — the Forest is generally the REAL security boundary in AD, not the Domain, a frequently-tested distinction because Domain Admins in one domain can, depending on trust configuration, still reach resources elsewhere in the same Forest.
--> Why AD still matters so much in 2026 despite the cloud-native shift: the vast majority of large enterprises still run substantial on-premises Windows infrastructure (file servers, legacy line-of-business apps, printers) that AD/Kerberos authenticates, and Azure AD/Entra ID (mentioned in Chapter 6) is frequently deployed in a HYBRID configuration, synchronizing identities from an on-prem AD forest into the cloud IdP rather than replacing it outright — meaning an AD compromise very often still cascades directly into the cloud identity layer too.

## Kerberos — Ticket-Based Authentication, Conceptually

--> Kerberos is a network authentication protocol (used as AD's default auth mechanism) built around a core design goal: a user should never have to send their password across the network to every single server they want to access — instead, they authenticate ONCE to a trusted third party, and get cryptographically signed, time-limited TICKETS that vouch for their identity to everything else. This is the exact same underlying philosophy as SAML/OIDC's signed-assertion model from Chapter 6, just implemented earlier (1980s MIT origin) and for a different (LAN/domain) context, using symmetric encryption tickets rather than XML/JWT.
--> Three roles: the Client (the user's workstation), the KDC (Key Distribution Center — the trusted third party, which on an AD network runs directly on every Domain Controller), and the Application Server (the actual resource being accessed — a file share, a database, an internal web app).
--> The KDC itself has two logical components: the Authentication Server (AS), which verifies the initial login, and the Ticket Granting Server (TGS), which issues tickets for specific services — in AD both run together on the DC, but they perform conceptually distinct steps below.

==> Ticket-Granting Mechanics, Step by Step
```
1. AS-REQ / AS-REP (initial login):
   Client -> KDC (Authentication Server):
     "I am jane.doe, here is proof (timestamp encrypted with my
      password-derived key)"
   KDC verifies the proof, then replies with a Ticket Granting Ticket
   (TGT) -- a ticket, encrypted with a key only the KDC itself knows,
   that essentially says "the KDC vouches that this is jane.doe, valid
   until [expiry]." The client CANNOT read the TGT's contents, only
   present it.
   --> Critically: the user's actual password is never sent over the
       network -- only a value derived from it, used briefly to prove
       possession of the password, and the TGT itself is what gets
       used from this point forward.

2. TGS-REQ / TGS-REP (requesting access to a specific service):
   Client -> KDC (Ticket Granting Server):
     "Here is my TGT (proving I already authenticated). I want to
      access FileServer01."
   KDC verifies the TGT, then issues a Service Ticket -- encrypted
   with a key shared between the KDC and THAT SPECIFIC application
   server, containing the client's identity and (in AD) their group
   memberships.

3. AP-REQ (accessing the actual resource):
   Client -> Application Server (FileServer01):
     "Here is my Service Ticket for you specifically."
   FileServer01 decrypts it using the key it shares with the KDC,
   confirms it's genuinely from the KDC and hasn't expired, and
   grants access -- without FileServer01 ever needing to contact the
   KDC directly at this final step, and without the client's password
   ever having touched this server at all.
```
--> Why this design matters defensively: because the TGT/Service Tickets are time-limited and the password itself never crosses the network after initial login, Kerberos meaningfully reduces credential-exposure risk compared to older protocols (like NTLM) that pass password-derived hashes around more directly and repeatedly.
--> Real-world attacks against this exact mechanism (worth knowing conceptually, not to execute): "Golden Ticket" attacks forge a TGT from a stolen KDC master key (the `krbtgt` account's key), granting an attacker the ability to impersonate ANY user indefinitely; "Kerberoasting" requests Service Tickets for accounts with weak passwords and cracks the ticket's encryption offline at leisure, since the ticket itself is encrypted with a key derived from that service account's password — both attacks are specifically why "Golden Ticket" and "Kerberoasting" appear constantly in AD-security and red-team material, and both are direct, practical consequences of the exact ticket mechanics described above.

## SCIM — Automated Provisioning Alongside SAML/OIDC

--> Chapter 6 covered SAML and OIDC as protocols for AUTHENTICATION federation (proving who a user is at login time). SCIM (System for Cross-domain Identity Management) solves an adjacent but different problem: PROVISIONING — automatically creating, updating, and deleting user accounts in downstream applications as employees join, change roles, or leave, using a standardized REST/JSON API rather than each app maintaining its own manual account-management process.
--> Without SCIM: an IT admin manually creates a new hire's account in Slack, Salesforce, the internal wiki, and 20 other apps by hand, and — far more dangerously — manually remembers to DISABLE all 20 accounts the day someone is terminated. Without SCIM, offboarding relies entirely on human memory and process discipline, which is exactly the kind of gap that produces lingering "ghost accounts" flagged in Chapter 6.
--> With SCIM: the IdP (Okta, Azure AD/Entra ID) pushes a standardized SCIM request to every connected application the moment an HR system marks someone as hired/changed/terminated, and the downstream app auto-creates/updates/disables the account within minutes, with zero manual per-app steps.
```
Example SCIM request (IdP -> downstream SaaS app, on employee termination):

  PATCH /scim/v2/Users/2819c223-7f76-453a-919d-413861904646
  Content-Type: application/scim+json

  {
    "schemas": ["urn:ietf:params:scim:api:messages:2.0:PatchOp"],
    "Operations": [
      { "op": "replace", "path": "active", "value": false }
    ]
  }
```
--> How SCIM and SAML/OIDC actually fit together in a real deployment: SAML/OIDC handle the LOGIN moment ("let this already-provisioned user into this app right now"), while SCIM handles the LIFECYCLE moment ("make sure this user's account exists/is current/is disabled in this app in the first place") — a mature enterprise IdP integration runs both simultaneously for the same application: SCIM keeps the account itself in sync, SAML/OIDC lets the user actually log into it.

## Identity Governance and Administration (IGA)

--> IGA is the governance layer sitting above day-to-day provisioning (SCIM) and authentication (SAML/OIDC/Kerberos) — it answers "is everyone's access still CORRECT and can we PROVE that to an auditor," rather than just "can this specific login succeed right now."

==> Joiner-Mover-Leaver (JML) Lifecycle
--> The formal model IGA platforms (SailPoint, Saviynt, Microsoft Entra ID Governance) are built around, mapping identity lifecycle events to access changes:
1. Joiner — a new employee/contractor starts; access is provisioned based on their role (often via SCIM under the hood) — ideally following least privilege (Chapters 1/3/6) from day one, not "give them whatever the last person in that seat had," which silently accumulates unnecessary access over time.
2. Mover — an existing employee changes role/department (Finance -> Engineering, promoted to a manager); this is the step organizations most often get WRONG in practice — access from the OLD role is frequently left in place ("just in case," or simply forgotten) while NEW access is added, so the person accumulates permissions from every role they've ever held rather than only their current one. This accumulation pattern is specifically called privilege creep, and it's one of the most common audit findings in real IGA reviews.
3. Leaver — the person exits the organization; access must be fully revoked, ideally immediately upon their last day (or, for a for-cause termination, immediately upon the decision, not the announcement) — directly the "centralized deprovisioning" value of SSO from Chapter 6, but IGA additionally tracks and PROVES that every single downstream system was actually deprovisioned, not just the central IdP account.

==> Access Certification Campaigns
--> A periodic (typically quarterly), formal review process where the actual owners of a resource (a manager, a system owner) are required to explicitly re-attest that every person currently holding access to that resource still genuinely needs it — rather than assuming access, once granted, remains correct forever.
```
Example access certification campaign flow:
1. IGA platform generates a review: "Manager Sarah Chen, please certify
   these 14 people's access to the Finance Reporting database."
2. Sarah reviews each entry: for each user, she clicks Approve (still
   needed) or Revoke (no longer needed/unrecognized).
3. Any entry left un-reviewed past a deadline is auto-escalated to
   Sarah's own manager, and/or auto-revoked, depending on policy.
4. Every decision -- and who made it, and when -- is logged as
   auditable evidence.
```
--> This is precisely the kind of evidence a SOC 2 Type II auditor (Chapter 27) samples directly — "show me your last three quarterly access certification campaigns for this system, and show me that flagged/revoked access was actually removed" is close to verbatim standard audit-test language, making access certification one of the most directly audit-visible IGA activities.
--> Segregation of Duties (SoD) checks are frequently layered into certification campaigns — flagging combinations of access that should never coexist on the same person (e.g., the ability to both CREATE a vendor in the finance system AND APPROVE payments to vendors, which would let one person commit fraud with no second set of eyes) — a control concept borrowed directly from traditional financial-audit practice and applied to IT access.

## CIEM — Cloud Infrastructure Entitlement Management

--> Chapter 7 covered cloud IAM basics (AWS/Azure roles and policies) at a conceptual level. CIEM is the specialized discipline/tooling category that exists specifically because cloud entitlements have become too numerous, too granular, and too dynamically-generated for traditional IGA tools (built around comparatively simpler, more static on-prem/app roles) to meaningfully govern.
--> The core problem CIEM addresses: a single cloud IAM role can easily be granted hundreds of individual fine-grained permissions across dozens of services, and in practice the vast majority go completely unused — CIEM platforms continuously analyze ACTUAL usage (via cloud audit logs, e.g., AWS CloutTrail) against GRANTED permissions to surface the gap, a metric often summarized as the Permission Gap: "this role has 340 granted permissions; only 12 have ever actually been used in the last 90 days."
--> This directly operationalizes least privilege at cloud scale — rather than a human trying to hand-craft a minimal IAM policy up front (extremely hard to get right for a complex cloud role) or manually auditing usage after the fact (extremely hard to do at cloud scale by hand), CIEM tooling can recommend or automatically generate a RIGHT-SIZED policy based on observed real usage, then continuously monitor for entitlement drift as the environment changes.
--> CIEM also specifically hunts for high-risk entitlement PATTERNS that are easy to create by accident in cloud IAM but are rarely intentional: unused privileged roles sitting dormant (a standing-access risk directly analogous to the JIT/PAM problem from Chapter 6, but for cloud service roles rather than human admin accounts), overly permissive wildcard policies (`"Action": "*"`, `"Resource": "*"`), and toxic combinations across MULTIPLE cloud accounts/subscriptions that, individually, look fine but together create an unintended privilege-escalation path (e.g., a role in Account A that can assume a role in Account B that, in turn, has admin rights back in Account A).
--> How CIEM fits with the rest of this chapter's tools: SCIM/IGA govern HUMAN identity lifecycle and access across largely enterprise SaaS/on-prem apps; CIEM governs MACHINE/cloud-resource entitlements (service roles, workload identities, cross-account trust) at a scale and granularity human-centric IGA tooling was never designed to reach — increasingly, mature enterprises run both side by side, sometimes as a single unified platform, because a compromised human identity with excessive IGA-governed access and an over-permissioned cloud service role represent the same underlying "unreviewed entitlement" risk in two different domains.

## Tying It Together

--> LDAP is the underlying directory protocol; Active Directory is Microsoft's LDAP-based implementation with Kerberos as its default authentication protocol, and the Forest — not the Domain — is AD's real security boundary.
--> Kerberos avoids ever sending a password across the network after initial login by using a trusted KDC to issue short-lived, cryptographically protected tickets (TGT -> Service Ticket -> resource access) — the same signed-assertion philosophy as SAML/OIDC from Chapter 6, applied at the LAN/domain layer, with Golden Ticket and Kerberoasting attacks being direct, well-documented abuses of that exact ticket mechanism.
--> SCIM automates the provisioning/deprovisioning LIFECYCLE side of identity (create/update/disable accounts across systems) as the natural complement to SAML/OIDC's authentication side from Chapter 6.
--> IGA governs whether all that provisioned access is still CORRECT over time via the Joiner-Mover-Leaver lifecycle and periodic access certification campaigns, with privilege creep (especially at the Mover stage) as the most common real-world failure mode and audit finding.
--> CIEM extends that same "is this access still actually needed" governance question into the cloud's much larger, more dynamic, more machine-oriented entitlement surface — together, this chapter's tools plus Chapter 6's SSO/OAuth/PAM content form the complete practical IAM stack an enterprise actually runs, from the wire-level ticket exchange up through the quarterly audit review.
