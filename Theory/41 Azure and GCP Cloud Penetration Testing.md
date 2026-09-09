### Azure and GCP Cloud Penetration Testing

--> LEGAL/ETHICAL REMINDER: everything below is for authorized environments only — your own subscription/project, a dedicated cloud lab (e.g. a throwaway Azure free-tier tenant, a GCP sandbox project), or engagements with signed written permission and the specific cloud provider's pentest policy acknowledged. Azure and GCP each require you to follow their published penetration testing rules even on an account you own — some actions (load testing, certain network attacks) still need prior notification. Attacking a tenant/project you don't own or lack authorization for is a serious crime and a guaranteed account suspension.

--> This note assumes you already understand the general cloud pentesting concepts covered in note 17 (Cloud Penetration Testing) — IAM misconfiguration as a category, the instance metadata service as an SSRF-to-credential-theft target, and cloud-specific enumeration tooling. Note 17 is AWS-heavy; this note is the Azure/Microsoft Entra ID and GCP-specific counterpart — same underlying ideas, different identity model and different metadata endpoint mechanics.

## Azure AD / Microsoft Entra ID as the Real Perimeter

--> Microsoft rebranded Azure AD to Microsoft Entra ID, but the login infrastructure and attack surface are unchanged — every tenant authenticates through the SAME shared endpoint, `https://login.microsoftonline.com`, regardless of which company owns the tenant. This is the single most important fact for offensive Azure work: there is no per-customer login server to discover or scan, there is one global endpoint that accepts authentication attempts for every tenant on Earth, distinguished only by the username/tenant ID in the request.

--> That shared endpoint is why Azure AD password spraying is such a favorite initial-access vector in real engagements and real breaches — you don't need to find the target's login page, it's always the same URL, and a valid corporate email address is often guessable or OSINT-able (`firstname.lastname@corp.com` conventions, LinkedIn, breach-data email lists).

## Password Spraying Azure AD

--> Password spraying = trying ONE (or a small handful of) common/seasonal password against MANY usernames, instead of many passwords against one user — this evades per-account lockout thresholds because no single account ever accumulates enough failed attempts to lock.

--> Azure AD Smart Lockout is the specific defense here: it tracks failed attempts per account (and distinguishes familiar vs unfamiliar sign-in locations/devices) and locks the account after a threshold (default around 10 failures) for a cooldown window. Smart Lockout counts failures PER ACCOUNT, not across the tenant — so the evasion math is straightforward:

1. Low attempts-per-account, high account-count ratio evades it: spraying `Summer2026!` against 5,000 usernames at 1 attempt per account per spray round stays well under any single account's lockout threshold, even though the tenant as a whole receives 5,000 authentication attempts.
2. Spread attempts over time (e.g. one password per account per 30 minutes, cycling through a small password list over days) to also stay under sign-in risk/velocity detections, not just the raw counter.
3. Target service accounts and shared mailboxes specifically — they're less likely to have MFA enrolled and are often exempt from Conditional Access policies that apply to normal users.

```bash
# MSOLSpray - sprays a single password against a list of usernames via the MSOL/Graph auth endpoint
MSOLSpray.ps1 -UserList users.txt -Password 'Summer2026!' -OutFile results.txt

# o365spray - also does username enumeration (see below) before spraying, and supports proxy rotation
python3 o365spray.py --spray -U users.txt -p 'Summer2026!' --domain corp.com

# TREVORspray - built specifically for distributed/rotating-source-IP spraying to defeat
# per-IP throttling/conditional access location policies, spreading requests across many egress IPs
trevorspray.py -U users.txt --password 'Summer2026!' --recon
```

--> Detection/mitigation: enable Azure AD Smart Lockout (on by default, but tune thresholds), enforce MFA/Conditional Access for ALL accounts including service accounts (use certificate-based auth instead of passwords for those), monitor Azure AD sign-in logs for a spike in failed logins from a small set of source IPs across many distinct usernames — that pattern is a spray, not a targeted brute-force.

## Pre-Auth Username and Tenant Enumeration

--> Before spraying, an attacker wants to know which of the guessed emails are actually valid accounts in the tenant, WITHOUT needing a correct password — and WITHOUT triggering any lockout, since these checks happen pre-authentication.

--> The `GetCredentialType` endpoint (`login.microsoftonline.com/common/GetCredentialType`) and the related `GetUserRealm` endpoint are designed to tell a legitimate client's browser how to render the login flow (e.g. redirect to a federated ADFS page vs a normal Azure AD password box) — but their RESPONSE BEHAVIOR leaks whether a username exists.

--> Concretely: querying `GetCredentialType` for a username that exists returns a JSON body with `IfExistsResult: 0` (and details about the auth method — managed vs federated); for a username that does NOT exist, it returns `IfExistsResult: 1` (or a distinct non-zero code). No password is sent at all in this request — it's a pure existence oracle.

```bash
# o365spray's enumeration mode uses exactly this endpoint difference
python3 o365spray.py --enum -U users.txt --domain corp.com

# Manual curl equivalent (conceptual) - POST a username, inspect IfExistsResult in the JSON response
curl -s -X POST https://login.microsoftonline.com/common/GetCredentialType \
  -H "Content-Type: application/json" \
  -d '{"Username":"alice@corp.com"}'
```

--> Mitigation: this is largely an accepted design tradeoff by Microsoft (the endpoint has to exist for federated login redirection to work), so the practical defense is downstream — assume usernames WILL be enumerated, and invest in MFA/Conditional Access/Smart Lockout so a valid username alone is worthless to an attacker.

## Service Principal and App Registration Abuse

--> An App Registration in Azure AD represents an application; a Service Principal is that application's identity within a specific tenant, and it can be granted Microsoft Graph API permissions (application permissions, not just delegated user-context ones) — e.g. `Application.ReadWrite.All`, `Directory.ReadWrite.All`, `RoleManagement.ReadWrite.Directory`.

--> The escalation pattern: if an attacker compromises credentials/a client secret for a service principal that holds `Application.ReadWrite.All`, they can:

1. Add a new credential (client secret or certificate) to a DIFFERENT, more privileged app registration they don't otherwise control — effectively stealing that app's identity.
2. Grant their own compromised app additional Graph API permissions/app roles, since `Application.ReadWrite.All` lets you modify app registrations including their permission grants.
3. Use the now more-privileged identity to read/write directory objects, reset user passwords, or add themselves as an owner of higher-value apps — escalating from "compromised low-value automation account" to "directory-wide control."

--> Conceptually this is the exact same pattern as note 10's AD privilege-escalation-via-object-permissions (a principal with write access to another principal's authorization data can grant itself more power) — just replayed inside the Azure AD/Microsoft Graph object graph instead of on-prem AD ACLs.

```bash
# ROADtools (roadrecon) - builds a local database of the AAD tenant's objects/permissions,
# similar in spirit to BloodHound's graph model but for Azure AD
roadrecon auth -u compromised_sp_id -p secret --tenant corp.onmicrosoft.com
roadrecon gather
roadrecon gui        # browse the graph: users, apps, service principals, role assignments

# AzureHound - collects Azure AD/AzureRM data in a BloodHound-compatible format
azurehound -u user@corp.com -p 'password' --tenant corp.onmicrosoft.com list > azure.json
# Ingest azure.json into BloodHound to visually trace "which compromised principal
# is N hops from Global Administrator via Graph API permission abuse"
```

--> Mitigation: apply least privilege to app registrations (avoid blanket `*.ReadWrite.All` grants), require admin consent workflows for high-risk permissions, regularly audit service principal credentials/owners, and monitor Azure AD audit logs for `Add service principal credentials` and `Update application` events from unexpected actors.

## Azure AD Connect (Hybrid Bridge) as an Attack Path

--> Azure AD Connect (the sync engine that keeps on-prem AD and Azure AD in sync for hybrid organizations) runs as a service on a domain-joined server and holds a sync account with powerful directory-write rights on BOTH sides — it needs to write password hashes/attributes into Azure AD and, depending on config, can have `WriteDACL`-equivalent rights on-prem too.

--> This makes the AD Connect server a high-value bridge target in either direction:

1. On-prem-to-cloud pivot: an attacker who compromises on-prem AD (e.g. via note 10/note 31 techniques) and finds the AD Connect server can extract the sync account's credentials (stored, encrypted but decryptable with local admin rights, in the AD Connect SQL/config database) and use them to write attributes into Azure AD — including, in misconfigured environments, resetting cloud-only or synced user passwords.
2. Cloud-to-on-prem pivot: conversely, compromising the AD Connect server's Azure AD-facing identity can, in specific configurations (e.g. Password Hash Sync exposing hash material, or PTA agent abuse), be leveraged back toward on-prem access.
3. This "AADConnect" attack path is specifically why AD Connect servers should be treated as Tier-0 assets (same protection level as domain controllers), not as a routine application server.

--> Mitigation: treat the AD Connect server as Tier-0 (dedicated hardened host, restricted admin access, no other roles installed on it), rotate the sync account credentials regularly, and monitor for `AZUREADSSOACC$` or sync-account activity outside expected sync windows.

## GCP IAM Structure

--> GCP IAM is structured as role bindings at three resource-hierarchy levels — Organization, Folder, and Project — with permissions inheriting downward (a binding at the org level applies to every folder/project beneath it, which is itself a common misconfiguration source: an overly broad org-level grant nobody remembers).

1. Primitive roles - the old, coarse `Owner` / `Editor` / `Viewer` roles, granting sweeping access across almost every GCP service. Still found in older projects and almost always over-privileged for the task at hand.
2. Predefined roles - Google-curated, service-scoped roles (e.g. `roles/compute.instanceAdmin.v1`) intended to approximate least privilege without requiring custom role authoring.
3. Custom roles - org/project-defined bundles of specific permissions, offering the tightest scoping but requiring deliberate maintenance (they don't auto-update as GCP adds new permissions to predefined roles).

--> A "binding" is the actual unit of grant: it ties a role to a member (user, group, service account) at a specific resource level. Enumerating bindings is the GCP equivalent of enumerating IAM policies in AWS.

```bash
# Enumerate IAM policy bindings on a project (who has what role, at project scope)
gcloud projects get-iam-policy PROJECT_ID --format=json

# Enumerate at the org/folder level too - inherited grants are often the real problem
gcloud organizations get-iam-policy ORG_ID
gcloud resource-manager folders get-iam-policy FOLDER_ID

# Check what a specific (possibly compromised) service account can actually do
gcloud iam service-accounts get-iam-policy SA_EMAIL
gcloud projects get-ancestors-iam-policy PROJECT_ID   # combined view including inherited bindings
```

## GCP Metadata Service — the Header Requirement Difference

--> Like AWS's Instance Metadata Service, GCP compute instances expose a local-only metadata endpoint that hands out the attached service account's temporary OAuth token — making it the same SSRF-to-credential-theft target described in note 17 for AWS.

--> The specific GCP endpoint is `http://metadata.google.internal/computeMetadata/v1/`, and the critical technical difference from AWS is: GCP's metadata service REQUIRES the header `Metadata-Flavor: Google` on every request, and refuses requests without it. AWS's original IMDSv1, by contrast, was historically header-less — any plain `GET` to `169.254.169.254/latest/meta-data/...` worked with no special header at all, which is exactly why naive SSRF payloads worked so reliably against AWS IMDSv1 and why Amazon later introduced IMDSv2's mandatory PUT-token step as a harder-to-blindly-exploit fix.

--> Practical consequence for a pentester: a blind/basic SSRF that can only control the URL (not add arbitrary headers) may fail against GCP's metadata service even though it would have worked against unprotected AWS IMDSv1 — you need an SSRF primitive that lets you inject/control request headers, not just the URL, to pull GCP metadata.

```
# GCP metadata request - the Metadata-Flavor header is mandatory
GET /computeMetadata/v1/instance/service-accounts/default/token
Host: metadata.google.internal
Metadata-Flavor: Google

# Without the header, GCP returns 400/403 - unlike AWS IMDSv1 which had no such gate
```

--> Mitigation: this header requirement is itself a partial mitigation Google built in from the start; further harden by disabling legacy metadata endpoints where unused, restricting service account scopes attached to instances, and preferring Workload Identity Federation over long-lived service account keys.

## Service Account Key Abuse and Over-Privileged Instance Service Accounts

--> The GCP parallel to AWS instance-profile abuse: a compute instance has a service account attached, and any process/attacker code running ON that instance can request that service account's token straight from the metadata service (as above) — no key file needed if the SA is attached to the instance.

--> Separately, GCP lets you EXPORT a service account as a downloadable JSON key file — a long-lived, unrotated credential that, if leaked (committed to a public repo, embedded in a container image, left in a CI pipeline log), grants standing access with no expiry until manually revoked. This is a materially worse exposure than an instance's attached-SA token, which is short-lived and tied to the instance's life.

```bash
# Enumerate service accounts and their attached-instance usage in a project
gcloud iam service-accounts list --project PROJECT_ID
gcloud compute instances describe INSTANCE_NAME --format="value(serviceAccounts)"

# If you've compromised an instance, pull its attached SA's token directly from metadata
curl -H "Metadata-Flavor: Google" \
  "http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token"
```

--> Mitigation: avoid exporting/downloading SA JSON keys at all where Workload Identity or attached-instance identity suffices, scope instance service accounts down from the sweeping default `compute` scope to only what the workload needs, and enable/monitor Cloud Audit Logs for `iam.serviceAccounts.getAccessToken` and key-creation events.

## Comparison Table

| Aspect | Azure AD / Entra ID | GCP IAM | AWS IAM (note 17) |
|---|---|---|---|
| Identity model | Tenant-wide directory; users/groups/service principals/app registrations | Project/folder/org hierarchy; users/groups/service accounts | Account-scoped users/roles/groups; cross-account via role assumption |
| Metadata endpoint | N/A (identity is directory-based, not per-instance metadata) | `metadata.google.internal/computeMetadata/v1/` — requires `Metadata-Flavor: Google` header | `169.254.169.254/latest/meta-data/` — IMDSv1 historically header-less; IMDSv2 requires a session token |
| Common real-world misconfig | Over-permissioned app registrations/service principals (`Application.ReadWrite.All`); no MFA on service accounts | Primitive `Owner`/`Editor` roles left on projects; inherited org-level over-grants | Broad managed policies (`AdministratorAccess`) attached to roles; `iam:PassRole` escalation |
| Enumeration tooling | ROADtools, AzureHound (BloodHound-style graph) | `gcloud` IAM policy commands, GCP-specific ScoutSuite modules | Pacu, ScoutSuite, `aws iam` CLI |

--> Putting it together: an Azure/GCP-focused cloud engagement follows the same skeleton as note 17's AWS-centric flow (enumerate identity, enumerate storage/compute, chase the metadata-service credential-theft path, escalate via IAM/permission-graph abuse) — the concrete endpoints and permission models differ, but "find the over-privileged identity and pull its credentials" is the constant. Cross-reference note 17 for the AWS baseline and note 10 for the on-prem AD identity-graph abuse pattern that Azure AD's Graph API permission escalation directly mirrors.
