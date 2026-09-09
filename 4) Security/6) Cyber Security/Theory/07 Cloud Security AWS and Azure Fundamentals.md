### Cloud Security — AWS and Azure Fundamentals

--> Moving infrastructure to the cloud does not remove the need for security — it changes WHO is responsible for WHICH parts of it. Most real-world cloud breaches (Capital One, countless exposed S3 buckets) are not failures of AWS/Azure's own infrastructure — they are failures of the CUSTOMER's half of the shared responsibility model. This chapter is about understanding exactly where that line is drawn, and the specific misconfigurations that keep landing companies in breach headlines.

## The Shared Responsibility Model

--> The cloud provider (AWS, Azure, GCP) and the customer each secure a different slice of the stack. The provider is always responsible for "security OF the cloud" (the physical data centers, host hardware, network infrastructure, hypervisor). The customer is always responsible for "security IN the cloud" (their data, their configurations, their access controls) — but exactly where the dividing line sits shifts depending on the service model.

==> IaaS (Infrastructure as a Service) — e.g., AWS EC2, Azure VMs
```
CUSTOMER responsible for:
  - Guest OS patching and hardening
  - Network/firewall configuration (Security Groups, NSGs)
  - Identity and access management (who can do what)
  - Application-level security
  - Data encryption (at rest and in transit)
-------------------------------------------------------
PROVIDER responsible for:
  - Physical data center security
  - Host hypervisor
  - Physical network infrastructure
  - Physical hardware decommissioning
```
--> IaaS gives the customer the most control — and therefore the most responsibility. You rent the equivalent of an empty apartment; you install and lock your own doors.

==> PaaS (Platform as a Service) — e.g., AWS Elastic Beanstalk, Azure App Service, RDS
```
CUSTOMER responsible for:
  - Application code security
  - Data and access control configuration
  - Identity and access management
-------------------------------------------------------
PROVIDER responsible for:
  - Underlying OS patching
  - Runtime environment
  - Everything from IaaS, plus the managed platform layer
```
--> The provider now manages the OS/runtime for you (e.g., RDS patches the database engine itself) — the customer's job shrinks to "write secure code and configure access correctly."

==> SaaS (Software as a Service) — e.g., Salesforce, Microsoft 365, Google Workspace
```
CUSTOMER responsible for:
  - User access management (who has an account, MFA enforcement)
  - Data classification and sharing settings
  - Endpoint security (the device the user is on)
-------------------------------------------------------
PROVIDER responsible for:
  - Literally everything else (app code, infrastructure, OS, network)
```
--> The customer's slice is smallest here — but it's NOT zero. A misconfigured "share externally" setting on a SaaS file-sharing tool, or an employee without MFA, is still 100% the customer's responsibility even though Microsoft/Google runs everything underneath.

--> The single most important takeaway from this model, said plainly: "the cloud provider will never save you from your own misconfiguration." AWS is not responsible for a public S3 bucket you left open — that is squarely on the "security IN the cloud" side of the line, always.

## AWS IAM — Roles, Policies, and Least Privilege

--> AWS IAM (Identity and Access Management) controls who (which user, service, or application) can do what (which actions) on which resources within an AWS account.
--> Core IAM concepts:
--> User — an identity for a human or a workload that needs long-term credentials.
--> Group — a collection of users that share the same permissions, managed together.
--> Role — a set of permissions that can be temporarily ASSUMED by a user, an AWS service, or an external identity — no long-term credentials attached, only short-lived tokens issued when the role is assumed. Roles are the modern best-practice way to grant access to EC2 instances/Lambda functions, because there's no static access key sitting on disk to leak.
--> Policy — a JSON document that explicitly defines what actions are allowed or denied on what resources.

==> Worked Example: A Least-Privilege S3 Policy
--> Bad practice (overly broad — never do this in production):
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": "s3:*",
      "Resource": "*"
    }
  ]
}
```
--> This grants EVERY S3 action (read, write, delete, change permissions) on EVERY bucket in the account. If the identity holding this policy is ever compromised, the attacker has full control of all stored data in S3, account-wide.

--> Least-privilege version — grants only what's actually needed, on only the specific bucket that's needed:
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "AllowReadOnlyOnSpecificBucket",
      "Effect": "Allow",
      "Action": [
        "s3:GetObject",
        "s3:ListBucket"
      ],
      "Resource": [
        "arn:aws:s3:::company-invoices-prod",
        "arn:aws:s3:::company-invoices-prod/*"
      ],
      "Condition": {
        "IpAddress": {
          "aws:SourceIp": "203.0.113.0/24"
        }
      }
    }
  ]
}
```
--> Why this is better, line by line:
--> `Action` is limited to `GetObject` and `ListBucket` (read-only) — no `PutObject`, `DeleteObject`, or `PutBucketPolicy`. Even if this identity is compromised, the attacker can only read, not tamper or destroy.
--> `Resource` names the EXACT bucket ARN, not `*` — access to any other bucket in the account is implicitly denied by default (IAM is deny-by-default; you must explicitly Allow).
--> The `Condition` block further restricts access to only work from a specific corporate IP range — a stolen credential used from outside that range simply won't work, even if otherwise valid.
--> This is the principle of least privilege (introduced generally in earlier chapters) applied concretely to IAM: grant the minimum action, on the minimum resource, under the minimum conditions necessary to do the job — nothing more "just in case."

==> IAM Best Practices Checklist
1. Never use the AWS account root user for day-to-day work — lock it away with MFA and only use it for the handful of actions that truly require it.
2. Prefer roles over long-lived access keys wherever possible (EC2 instance roles, Lambda execution roles) — short-lived, auto-rotated credentials beat static keys sitting in a config file.
3. Enforce MFA on all human IAM users, especially anyone with administrative permissions.
4. Regularly run IAM Access Analyzer / review unused permissions — permissions tend to accumulate ("permission creep") as people change roles over time and nobody removes the old grants.
5. Use separate AWS accounts per environment (dev/staging/prod) rather than one account with everything mixed together — this limits blast radius if one environment is compromised.

## Common Cloud Misconfigurations

1. Public S3 Buckets
   --> An S3 bucket left with public read (or worse, public write) access, exposing its contents to the entire internet with no authentication required.
   --> This single misconfiguration has caused an enormous number of real breaches — leaked customer databases, exposed backup files, leaked source code, leaked credentials-in-config-files — because it requires zero exploitation skill on the attacker's part; automated internet-wide scanners simply enumerate bucket names and check permissions.
   --> Defense: enable "S3 Block Public Access" at the account level (AWS now defaults new buckets to fully private), use bucket policies scoped tightly, and continuously scan for public buckets with a CSPM tool (below).

2. Overly Permissive Security Groups
   --> A Security Group (AWS's virtual firewall for EC2 instances) configured to allow inbound traffic from `0.0.0.0/0` (literally anywhere on the internet) on sensitive ports like SSH (22), RDP (3389), or a database port (3306, 5432).
   --> Example of the mistake:
   ```
   Type: SSH, Protocol: TCP, Port: 22, Source: 0.0.0.0/0
   ```
   --> This effectively invites automated brute-force scanners from the entire internet to hammer that port continuously. Real breaches have started from exactly this — an internet-exposed database port with a weak or default password, found within minutes by mass internet scanning tools like Shodan.
   --> Defense: restrict source ranges to known corporate IPs or a VPN CIDR block, or better, remove direct SSH/RDP exposure entirely in favor of a bastion host or AWS Systems Manager Session Manager (which requires no open inbound port at all).

3. Exposed Secrets in Code
   --> Hardcoding AWS access keys, database passwords, or API tokens directly in application source code — which then gets pushed to a (sometimes public) Git repository.
   --> This is one of the most common real-world initial-access vectors: automated bots continuously scrape GitHub/GitLab for accidentally-committed AWS keys (a leaked key can be found and abused within minutes of a push, in some documented cases).
   --> Example of the mistake:
   ```python
   # NEVER do this
   AWS_ACCESS_KEY_ID = "AKIAIOSFODNN7EXAMPLE"
   AWS_SECRET_ACCESS_KEY = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
   ```
   --> Defense: use a secrets manager (AWS Secrets Manager, HashiCorp Vault, Azure Key Vault) and inject secrets at runtime via environment variables or IAM roles — never commit them; use pre-commit hooks/git-secrets scanning to catch accidental commits before they leave a developer's machine; if a key ever IS leaked, rotate/revoke it immediately (assume it's already compromised the moment it's public).

## CSPM (Cloud Security Posture Management)

--> CSPM tools (AWS Security Hub, Microsoft Defender for Cloud, Wiz, Prisma Cloud) continuously scan an organization's cloud environment against security best-practice baselines (like CIS Benchmarks) and flag misconfigurations automatically — public buckets, overly permissive security groups, unencrypted storage, unused/over-privileged IAM roles, missing MFA — before an attacker finds them first.
--> Why this matters at cloud scale: a single AWS account can have thousands of resources across dozens of services, and configurations drift constantly as engineers make changes. Manually auditing this by hand is not feasible; CSPM automates continuous, ongoing checking instead of a one-time audit.
--> Typical CSPM workflow: continuous scan -> compare against a compliance/best-practice framework (CIS AWS Foundations Benchmark, for example) -> generate findings ranked by severity -> (in mature setups) auto-remediate low-risk findings automatically, or open a ticket for higher-risk ones requiring human review.
--> This is the cloud-native evolution of the vulnerability scanning concept from earlier chapters, applied specifically to configuration state rather than to software vulnerabilities/patches.

## Common Cloud Attack Vectors — SSRF and the Metadata Service

--> Server-Side Request Forgery (SSRF) is a web application vulnerability where an attacker tricks a server into making an HTTP request to a destination the attacker chose, rather than the destination the application intended.
--> This becomes catastrophic in the cloud specifically because of the EC2 Instance Metadata Service — every EC2 instance can query a special, non-internet-routable link-local address to retrieve information about itself, including, critically, the temporary IAM credentials attached to its instance role.

==> The Classic 169.254.169.254 Attack, Step by Step
```
1. A web application running on an EC2 instance has a feature that
   fetches a user-supplied URL (e.g., "import an image from this URL"
   or a PDF-generation service that fetches a webpage to render).

2. The application does NOT validate/restrict which URLs it's allowed
   to fetch (this missing validation IS the SSRF vulnerability).

3. Attacker submits, instead of a normal image URL:

   http://169.254.169.254/latest/meta-data/iam/security-credentials/

4. The vulnerable server, trusting its own outbound request, dutifully
   fetches this internal metadata URL and returns the response --
   which is the NAME of the IAM role attached to the instance, e.g.:

   my-app-ec2-role

5. Attacker then requests the credentials for that specific role:

   http://169.254.169.254/latest/meta-data/iam/security-credentials/my-app-ec2-role

6. The metadata service responds with the instance's actual temporary
   IAM credentials:

   {
     "AccessKeyId": "ASIAABCDEFGHIJKLMNOP",
     "SecretAccessKey": "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
     "Token": "IQoJb3JpZ2luX2VjEA...",
     "Expiration": "2026-08-06T18:00:00Z"
   }

7. The attacker now uses these stolen credentials directly from their
   OWN machine (not the victim server) to call the AWS API, inheriting
   whatever permissions that EC2 role was granted -- potentially
   reading every S3 bucket, spinning up resources, or pivoting further
   into the account, entirely from an SSRF bug that started as
   "the app fetches a URL."
```
--> This exact attack chain (SSRF -> metadata service -> stolen IAM creds -> S3 data exfiltration) was the real root cause of the 2019 Capital One breach, which exposed over 100 million customer records — it remains the textbook example taught industry-wide for exactly why SSRF is considered critical-severity in cloud environments, even though it might look "minor" in an on-prem-only threat model.
--> Defenses:
--> IMDSv2 (Instance Metadata Service version 2) — AWS's fix, which requires a session token obtained via a PUT request with a custom header before any metadata GET request will succeed. Because SSRF vulnerabilities typically only let an attacker control a simple GET request (not add custom headers or use PUT), enforcing IMDSv2-only on an instance blocks this entire attack class outright. This should be enabled account-wide as a hard default in any modern AWS environment.
--> Application-layer defense: strictly validate/allowlist any user-supplied URLs the server is allowed to fetch, and block requests to link-local/private IP ranges (169.254.0.0/16, 10.0.0.0/8, 127.0.0.1, etc.) at the application or network egress level.
--> Least privilege (again): even if credentials ARE stolen, a tightly scoped IAM role (per the worked policy example above) limits what the attacker can actually do with them — this is why least privilege is described as defense in depth, not just a "nice to have."

## Tying It Together

--> The shared responsibility model defines a hard line: the provider secures the cloud itself, the customer secures what they put IN it — and that customer-side line is where nearly every real cloud breach actually happens.
--> AWS IAM policies are the practical enforcement mechanism for least privilege in the cloud — narrow actions, narrow resources, narrow conditions, short-lived roles over static keys.
--> Public buckets, open security groups, and leaked secrets are the three misconfigurations responsible for a disproportionate share of real-world cloud breaches, precisely because none of them require any actual exploitation skill to find and abuse.
--> CSPM tooling exists because manual configuration auditing simply does not scale to modern cloud environments with thousands of resources changing daily.
--> SSRF against the metadata service is the canonical example of how a "minor" web bug becomes a full account compromise in the cloud specifically — the same bug in an on-prem-only world would be far less severe, which is exactly why cloud environments demand rethinking severity ratings for old, familiar vulnerability classes.
