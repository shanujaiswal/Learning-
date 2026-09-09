### Zero Trust Architecture

--> This chapter covers a security MODEL/PHILOSOPHY rather than a single tool — it changes how identity, network, and access control decisions are made across everything covered in earlier chapters (firewalls, VPNs, least privilege).
--> Core principle, stated in three words: "never trust, always verify." No user, device, or system is trusted by default, EVER — regardless of whether it's sitting inside the corporate network or outside it.

## The Old Model: Perimeter Security ("Castle and Moat")

--> Traditional network security treats the corporate network boundary as a hard perimeter — a "moat" — defended by firewalls, VPN gateways, and IDS/IPS at the edge. Once a user or device authenticates and gets INSIDE that perimeter (e.g., connects to the VPN, or is physically plugged into the office network), it is implicitly trusted to reach almost everything else on the internal network with comparatively little further checking.
--> This is the "castle and moat" mental model: build a strong wall around the whole castle, trust everyone already standing inside it.

Why this model breaks down in the real world:

1. Lateral movement after a single breach
   --> If a perimeter is the only real checkpoint, then ONE compromised laptop or ONE stolen VPN credential gives an attacker broad internal access — they're "inside the moat" and can often move sideways to servers, file shares, and domain controllers with little additional friction. This is exactly the Lateral Movement tactic from the ATT&CK framework in Chapter 10, and it's devastatingly effective specifically because perimeter models grant so much implicit internal trust.

2. Insider threats and compromised credentials
   --> Perimeter security has almost nothing meaningful to say about a legitimate, authenticated employee (or an attacker holding their stolen valid credentials) misusing access they're technically allowed to have. Trust, once granted at the door, mostly persists.

3. Cloud, remote work, and BYOD dissolve the perimeter itself
   --> When employees work from home, access SaaS apps directly over the public internet, and use personal devices, there IS no clean physical/network perimeter left to defend. Assets are scattered across corporate data centers, multiple cloud providers, and home networks simultaneously — the castle's walls have effectively been torn down by the modern way work happens.

4. Flat internal networks
   --> Many corporate networks, once you're inside the perimeter, are largely "flat" — a compromised marketing department workstation can often reach the finance server directly because nothing internally re-checks or restricts that path.

## Core Zero Trust Principles

--> Zero Trust Architecture (ZTA), formalized notably in NIST SP 800-207, replaces "trust by location" with "trust computed continuously and dynamically, per-request, based on multiple signals, regardless of network location."

The foundational rules that fall out of "never trust, always verify":

1. Verify explicitly, every time
   --> Every access request is authenticated and authorized based on ALL available signals (user identity, device health, location, data sensitivity, behavioral anomaly) — not once at login, but continuously and per-resource. Being "on the VPN" grants zero implicit trust by itself.

2. Least-privilege access
   --> Every identity (human or machine/service account) gets the absolute minimum access required to do its specific job, for the minimum time necessary, and nothing more. This is the same least-privilege principle from Chapter 1, but Zero Trust operationalizes it far more granularly and continuously than classic RBAC ever did — often as Just-In-Time (JIT) access that's granted temporarily for a specific task and automatically revoked afterward, rather than standing access that persists indefinitely.

3. Assume breach
   --> Design the entire architecture as if an attacker is ALREADY inside the network somewhere. This directly mirrors the "assume breach" mindset behind threat hunting in Chapter 10 — segmentation and continuous verification exist specifically so that even a successful initial compromise gets contained to the smallest possible blast radius instead of unlocking the whole environment.

## The Five Pillars of Zero Trust

--> Zero Trust is usually broken down (per CISA's Zero Trust Maturity Model and similar frameworks) into pillars that each need their own continuous verification, tied together by cross-cutting visibility/analytics and automation/orchestration.

1. Identity
   --> Every human and machine identity must be strongly, continuously verified — not just at initial login. Concretely: enforce MFA everywhere (not just for admins), use short-lived tokens instead of long-lived passwords/API keys wherever possible, apply risk-based/adaptive authentication (a login from a new country or an impossible-travel pattern — logging in from London then Tokyo eight minutes later — triggers an automatic step-up challenge or outright block, even mid-session).
   --> Identity becomes the new perimeter in Zero Trust thinking — it's the primary control point, replacing the old network-edge firewall as "the thing that decides who gets in."

2. Device
   --> The requesting DEVICE's health and posture is verified alongside the user's identity. A valid username/password/MFA combo from a device that is unpatched, missing endpoint security (tying back to Chapter 10's EDR), jailbroken, or simply unknown/unmanaged should NOT be granted the same access as a known, compliant, corporate-managed device — even for the exact same user.
   --> Example: a Conditional Access policy (common in Microsoft Entra ID / Azure AD) that requires the device to be domain-joined AND have EDR reporting "healthy" AND be running a current OS patch level before it can access the finance SharePoint site, regardless of whether the user's password and MFA were both correct.

3. Network (Micro-segmentation)
   --> Instead of one flat internal network behind a single perimeter firewall, the network is divided into many small, isolated segments/zones, each with its OWN access policy enforced between them — so that even a compromised host in one segment cannot freely reach hosts in another segment without passing its own explicit check.
   --> Example: the finance database server sits in its own micro-segment that ONLY accepts connections from the specific finance application server, on the specific port the app needs, and from nowhere else — not from the marketing subnet, not from a random employee laptop, not even from other servers in the same data center rack. A compromised marketing workstation (Lateral Movement, again tying to Chapter 10's ATT&CK tactics) simply cannot reach it at the network layer at all, regardless of what credentials the attacker holds.
   --> Software-Defined Perimeter (SDP) and service meshes (e.g., Istio) are common technical mechanisms for enforcing micro-segmentation at the application/network layer respectively — this also connects directly to Kubernetes network policies covered in the container security chapter.

4. Application / Workload
   --> Trust is verified per-application and per-workload, not just per-network-hop. Every request between two services/applications is authenticated (often via mutual TLS — both sides present certificates, not just the server) and authorized, whether that request originates from a human user's browser or from one backend microservice calling another.
   --> This matters enormously in cloud/microservices architectures where dozens of services talk to each other constantly — Zero Trust says every single one of those internal calls should ALSO be verified, not just the initial user-facing login.

5. Data
   --> Data itself is classified by sensitivity (public, internal, confidential, restricted) and protected with controls that travel WITH the data regardless of where it ends up — encryption at rest and in transit, Data Loss Prevention (DLP) policies, and access controls enforced at the point the data is actually opened/used, not just at the point it's stored.
   --> Example: a confidential spreadsheet is encrypted with rights-management such that even if it's accidentally emailed outside the company or downloaded to a personal USB drive, the recipient still can't open it without a valid, continuously re-checked authorization token — the protection travels with the file itself rather than relying on network location to keep it contained.

## ZTNA vs Traditional VPN

--> This is one of the most concrete, practical changes Zero Trust brings, and a very common interview topic.

Traditional VPN (site-to-site or remote-access):
--> Grants the connecting device/user broad NETWORK-LEVEL access to an entire subnet/segment once connected — conceptually, the VPN just extends the "castle" out to wherever the user physically is, then drops them inside the same moat as everyone else.
--> Once connected, the user's device can typically REACH (at the network/routing level) many internal systems even if it has no actual business need for most of them — access to any specific SERVER is often controlled downstream (if at all) by that server's own permissions, but the network PATH to reach it and attempt access is already wide open.
--> This is exactly the lateral-movement risk described earlier: a compromised VPN credential or an infected remote laptop becomes a launchpad with unnecessarily broad network reach.

ZTNA (Zero Trust Network Access):
--> Grants access to individual, specific APPLICATIONS or services only, never broad network-level reach — the user (or their device) never gets an IP route to the internal network segment at all. Each access request is brokered per-application through a control plane that continuously checks identity + device posture + context before allowing (and continuing to allow) that one specific connection.
--> Access is typically brokered through a cloud-based control plane/proxy rather than a direct network tunnel — the user connects OUT to the ZTNA broker, the broker separately connects to the internal app, and the two sessions are stitched together; the user's device is never actually placed onto the internal network's IP space.
--> Practical effect: a user granted ZTNA access to "the internal HR web app" can reach exactly that HR web app and nothing else — they cannot ping the file server, cannot port-scan the internal network, cannot see that other internal systems even exist, because they were never granted a network path to them in the first place. If their device is compromised, the attacker inherits only that same narrow, application-specific access — not a route into the whole internal network.
--> Common ZTNA products referenced in the industry: Zscaler Private Access, Cloudflare Access, Palo Alto Prisma Access.

--> Summary of the difference in one line: VPN answers "is this device allowed on the network," ZTNA answers "is this specific identity, from this specific healthy device, right now, allowed to reach this one specific application" — and re-answers that question continuously rather than once at connection time.

## Implementing Zero Trust Incrementally

--> Zero Trust is a destination/philosophy, not a single product you buy and flip on — mature organizations get there through a multi-year, incremental roadmap, not a rip-and-replace overhaul. A realistic, practical rollout order:

1. Inventory and visibility first
   --> You cannot enforce least-privilege access to assets you don't know exist. Start by building a complete inventory of identities (human + service accounts), devices, applications, and data flows. This is unglamorous but foundational — most Zero Trust programs stall here because organizations underestimate how much they DON'T know about their own environment.

2. Strong identity foundation
   --> Enforce MFA everywhere (starting with admin/privileged accounts, then all users), move toward Single Sign-On (SSO) so identity is centralized in one place rather than scattered across dozens of separate app logins, and begin retiring standing, long-lived credentials in favor of short-lived, JIT-granted access for privileged tasks.

3. Device posture checks
   --> Roll out Conditional Access-style policies (device must be managed/healthy/patched) initially in "audit/report-only" mode to see what would break, then gradually move to actual enforcement once the org understands the impact.

4. Segment the network
   --> Start micro-segmentation with the highest-value assets first (the crown-jewel database, the domain controllers) rather than trying to segment the entire flat network at once — pick a small, high-value blast-radius reduction and prove the model works before expanding it everywhere.

5. Pilot ZTNA for a subset of remote access
   --> Rather than ripping out the corporate VPN overnight, pilot ZTNA for one specific application or one specific user group (e.g., contractors, who traditionally get FAR too much VPN-based network access relative to their actual need) and expand from there as confidence and tooling maturity grow.

6. Extend to applications and data
   --> Only once identity, device, and network foundations are solid does it become practical to layer in mutual-TLS between internal services and DLP/data-classification controls — these pillars depend on the earlier ones already being in place to be enforceable at all.

--> Realistic caveat worth remembering for both study and interviews: very few real organizations are "fully Zero Trust" in a pure sense — it's a maturity spectrum (CISA's model explicitly defines Traditional → Initial → Advanced → Optimal stages), and most orgs are still somewhere in the middle of that spectrum, incrementally reducing implicit trust rather than having eliminated it entirely.
