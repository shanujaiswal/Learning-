### Threat Modeling - STRIDE, DREAD, and Attack Trees

--> SCOPE/DISCIPLINE NOTE (adapted from the usual legal/ethical reminder): threat modeling is a planning and design discipline, not an active-exploitation technique — there's nothing here to "authorize" against a live target the way there is with note 04's payloads or note 10's ticket forging. It's the structured thinking you do BEFORE and ALONGSIDE hands-on testing (note 01's methodology) to decide what's actually worth attacking, defending, or reporting (note 16's bug bounty/reporting mindset). Applying it well still requires the same professional judgment as any other security work — done carelessly, it produces a false sense of security instead of one.

--> This note assumes you're comfortable with the overall pentest methodology in note 01 and the general idea that vulnerabilities have varying real-world impact (echoed throughout note 16's severity/reporting content). It builds toward a proactive, design-time counterpart to that reactive, find-the-bug work.

## Why Threat Modeling Exists

--> Every other note in this track is fundamentally reactive: a system already exists, and you're finding what's already broken in it. Threat modeling flips the order — you enumerate what COULD go wrong in a system's design before it's fully built (or continuously, as it evolves), so security effort gets spent where the actual risk is, instead of wherever a scanner happened to point or wherever testing time ran out.

--> It's a complement to hands-on testing, not a replacement for it — a threat model tells you WHERE to look and WHY it matters; note 01-style reconnaissance/exploitation is still how you confirm whether a modeled threat is actually exploitable in practice. Teams that only threat-model and never test ship confident-sounding documents with unverified assumptions; teams that only test and never threat-model tend to miss entire categories of risk that a five-minute design conversation would have caught.

## The General Threat-Modeling Process

1. Define scope and build a Data Flow Diagram (DFD) - the actual artifact you threat-model against. It shows processes (the boxes that do work), data stores (databases, files, caches), external entities (users, third-party APIs), data flows (arrows between them), and — critically — trust boundaries (lines marking where data crosses from one trust level to another, e.g. "internet" to "DMZ" to "internal network").
2. Identify threats per DFD element - walk every process, data store, data flow, and external entity and ask what could go wrong there specifically. STRIDE (below) is the standard categorization scheme used at this step.
3. Rate and prioritize - not every identified threat deserves equal investment; some risk-scoring approach (DREAD, or more modern alternatives) ranks them.
4. Mitigate - design or implement a control for each threat that clears the priority bar — a code change, an architectural change, a compensating control, or a documented accepted risk.
5. Validate - confirm the mitigation actually closes the threat, ideally via the same hands-on testing skillset used everywhere else in this track.

```text
[External Entity: User] --(1. login request)--> [Process: Auth Service] --(2. query)--> [Data Store: User DB]
        ^                                              |
        |                                     (3. session token)
        +----------------------------------------------+
   ===== trust boundary (internet -> internal network) crosses flow (1) =====
```

--> The DFD above is deliberately minimal — a real one for even a small feature has more processes and flows, but the point is the same: threats get identified AGAINST specific elements and specific flows crossing specific trust boundaries, not against "the system" in the abstract.

## STRIDE — Threat Categorization Per DFD Element

--> STRIDE (Microsoft-originated) gives six threat categories to check against each DFD element — it turns "what could go wrong here" from an open-ended brainstorm into a checklist.

1. **Spoofing** - an attacker impersonates another user, process, or system. Applies to external entities and processes. Conceptually the same problem as note 10's AD identity spoofing (Pass-the-Hash/Pass-the-Ticket are, at their core, spoofing a legitimate identity) — the mitigation family is authentication: strong credential checks, mutual TLS between services, signed tokens.
2. **Tampering** - unauthorized modification of data in transit or at rest. Applies to data flows and data stores. The mitigation family is integrity: HMACs/digital signatures on data, TLS for flows crossing trust boundaries, write-access controls on stores.
3. **Repudiation** - a user denies having performed an action, and the system has no way to prove otherwise. Applies mainly to processes. This is why audit logging and non-repudiation controls matter — connects directly to note 04's logging/monitoring section (Insufficient Logging and Monitoring is, from a threat-modeling lens, exactly a repudiation threat left unmitigated): if there's no reliable log of who did what, a malicious insider or a compromised account can act and later plausibly deny it.
4. **Information Disclosure** - exposure of data to a party not authorized to see it. Applies to data flows, data stores, and processes. The mitigation family is confidentiality: encryption at rest/in transit, access control checks, minimizing what's returned in API responses (note 04's Sensitive Data Exposure category, viewed from design time instead of after the fact).
5. **Denial of Service** - degrading or denying availability of a process or flow. Rate limiting, resource quotas, redundancy/failover design.
6. **Elevation of Privilege** - gaining capabilities beyond what was granted. Applies mainly to processes. This is the design-time umbrella over essentially the entire privilege-escalation content of this track — Linux privesc (note 08), Windows privesc (note 09), and container/K8s privilege abuse (note 33 territory) are all real-world instances of an Elevation of Privilege threat that a design-time review could have flagged (e.g. "this process runs as root when it only needs to bind a low port" is an EoP threat identifiable before a single line of exploit code exists).

--> Worked mini-example — a login feature's DFD has: [User] --(credentials)--> [Auth Process] --(query)--> [User DB], with a trust boundary between User and Auth Process (internet-facing).

| DFD Element | STRIDE Category Checked | Threat Identified |
|---|---|---|
| User -> Auth Process flow (crosses trust boundary) | Spoofing | Attacker submits stolen/guessed credentials, impersonating the real user |
| User -> Auth Process flow | Tampering | Credentials sent over plain HTTP could be modified/replaced in transit |
| Auth Process | Repudiation | No log of failed/successful login attempts — a compromised account's actions can't be attributed later |
| Auth Process -> User DB flow | Information Disclosure | Verbose DB error on query failure leaks schema/user-existence info |
| Auth Process | Denial of Service | No rate limiting — attacker can lock out or overwhelm the login endpoint |
| Auth Process | Elevation of Privilege | A successful low-priv login somehow returns an admin-scoped session token due to a role-assignment bug |

--> Not every category applies to every element — the value of STRIDE is that it forces you to at least CONSIDER each of the six against each element, rather than only thinking of the threat categories that come naturally to whoever's doing the review.

## DREAD — Quantitative-ish Risk Scoring (Largely Superseded)

--> DREAD scores an identified threat across five factors, each typically rated 1–10, then averages them into a single priority number:

1. **Damage** - how bad is the impact if exploited?
2. **Reproducibility** - how easily/reliably can the attack be repeated?
3. **Exploitability** - how much skill/effort does the attack require?
4. **Affected users** - what proportion of users/systems are impacted?
5. **Discoverability** - how easily would an attacker find this threat?

```text
Threat: SQLi in login form
Damage: 9   Reproducibility: 8   Exploitability: 7   Affected users: 10   Discoverability: 6
DREAD score = (9+8+7+10+6) / 5 = 8.0   -> high priority
```

--> Why it fell out of favor: DREAD's numbers FEEL objective but aren't — different reviewers scoring the exact same threat routinely produce wildly different numbers, because "how exploitable is this" or "how discoverable" are judgment calls with no shared rubric behind the 1–10 scale. Microsoft itself, where DREAD originated, moved away from it for this reason — inconsistent scoring across teams made priority numbers effectively meaningless for cross-team comparison, and the false precision of an average like "8.0" invited over-trusting a number that was really five different people's gut feelings averaged together. Industry has largely moved toward more structured, less falsely-precise approaches (bug-bounty-style severity tiers as in note 16, or CVSS-style vectors with defined sub-criteria) that at least make the INPUTS to a rating explicit and comparable, even if judgment is still involved.

## Attack Trees — Complementary Hierarchical Modeling

--> Where STRIDE is applied per DFD element (bottom-up, systematic coverage), an Attack Tree starts from the attacker's ultimate GOAL at the root and works down — top-down, goal-driven, and naturally visual.

1. Root node - the attacker's ultimate objective, e.g. "Compromise the domain."
2. Child nodes - the distinct ways to achieve the parent goal, connected by OR logic (any one child alone suffices) or AND logic (all children together are required) between siblings.
3. Leaf nodes - concrete, executable techniques — the same level of specificity as the actual attack chains covered throughout this track.

```text
GOAL: Compromise the Domain
|
+--(OR)--> Phish a user for credentials
|             |
|             +--(AND)--> Craft convincing phishing email
|             +--(AND)--> Bypass/evade email security controls
|
+--(OR)--> Exploit an internet-exposed service
|             |
|             +--(OR)--> Unpatched CVE on a public-facing app (note 03/04 recon+exploitation)
|             +--(OR)--> Exposed RDP/VPN with weak/reused credentials
|
+--(OR)--> Abuse a misconfigured ADCS certificate template
              |
              +--(AND)--> Enumerate ADCS templates (see note 32)
              +--(AND)--> Request a cert impersonating a privileged account
```

--> This is exactly the attack-path/kill-chain thinking already implicit throughout this whole study track — a real engagement's chain (initial foothold to enumeration to privesc to lateral movement to domain compromise, as walked through in note 10's closing summary) IS a single path down some attack tree drawn for the goal "compromise the domain." Drawing the tree explicitly, before testing, helps prioritize which branch to spend limited engagement time on — the branch with the fewest AND-conditions and lowest per-step difficulty is usually the attacker's (and therefore the tester's) path of least resistance, and defenders should harden branches in that same priority order.

## Comparison Table

| | STRIDE | DREAD | Attack Trees |
|---|---|---|---|
| Question answered | What KINDS of things can go wrong at this element? | How BAD/urgent is this specific threat, roughly? | What are the DISTINCT PATHS to the attacker's goal? |
| Direction | Bottom-up, per DFD element | Applied to an already-identified threat | Top-down, from attacker's goal |
| When to use | During DFD-based design review, systematic coverage | Rough triage (largely superseded — prefer structured severity/CVSS in practice) | Communicating/visualizing attack paths, prioritizing defenses by path difficulty |
| Output format | Categorized threat list per element | A single priority number per threat | A hierarchical AND/OR diagram |

--> Threat modeling doesn't replace anything else in this track — it's the lens that decides, before you ever fire a payload, which of the techniques in notes 01 through 42 are actually worth pointing at a given system, and it's the same lens a defender uses to decide where to harden first. Cross-reference note 01 for how this slots into overall methodology, note 10 for the AD-specific spoofing/EoP examples, note 16 for how prioritized findings get written up, and note 32 for the ADCS-specific attack-tree branch above.
