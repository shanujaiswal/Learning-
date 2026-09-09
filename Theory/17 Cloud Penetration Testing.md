# Why Cloud Pentesting Differs From Traditional Network Pentesting

--> There's no physical network perimeter to scan in the traditional sense (covered in the Reconnaissance/Nmap file) -- the attack surface is API calls, IAM permissions, and misconfigurations in a shared-responsibility model, rather than open ports on a firewall. This file assumes the AWS fundamentals covered in the Full Stack DevOps notes and the IAM/Cloud Security concepts covered in the Cyber Security track -- here, viewed from the OFFENSIVE side.

# Authorization Is Non-Negotiable Here More Than Anywhere Else

--> Cloud providers explicitly prohibit testing infrastructure you don't own or lack written authorization for -- AWS, Azure, and GCP each have specific penetration testing policies that must be followed (some services require prior notification even with account ownership). Violating this risks account suspension and potential legal consequences, on top of the general ethical/legal framework covered in the Fundamentals and Methodology file.

# IAM Misconfiguration -- The Most Common Real-World Finding

--> Overly permissive IAM policies (violating least privilege, covered in the AWS Security Hardening notes) are consistently the top cloud security finding in real assessments -- a role intended for one narrow purpose that's actually allowed to do far more.

```bash
# Enumerate what permissions a compromised/discovered credential actually has
aws iam get-user
aws iam list-attached-user-policies --user-name discovered-user
aws iam simulate-principal-policy --policy-source-arn <arn> --action-names s3:GetObject
```

--> Privilege escalation paths within IAM itself are a specific, well-documented category -- e.g. a user who can attach ANY policy to themselves, or pass a role with broader permissions than intended (`iam:PassRole` misuse), can escalate from limited access to full account control.

# S3 Bucket Enumeration and Misconfiguration

--> Publicly readable/writable S3 buckets (referenced as a classic mistake in the AWS notes) remain a common real-world finding -- an attacker enumerates likely bucket names (company name + common suffixes) and checks accessibility directly.

```bash
aws s3 ls s3://company-backups --no-sign-request     # Checking if a bucket allows anonymous access
```

# The Metadata Service -- SSRF's Cloud-Specific Payoff

--> The Instance Metadata Service (`169.254.169.254`) is reachable only from WITHIN a cloud instance, providing that instance's temporary IAM credentials -- an SSRF vulnerability (covered in the OWASP Top 10 file) in a web application running on that instance can be leveraged to reach the metadata service and steal those credentials, exactly the mechanism behind the real-world Capital One breach referenced in the Cyber Security Cloud Security file.

```
GET /latest/meta-data/iam/security-credentials/<role-name>
Host: 169.254.169.254
```

--> IMDSv2 (requiring a session token via a PUT request first) was introduced specifically to make this SSRF-to-credential-theft chain harder to exploit blindly -- checking whether a target enforces IMDSv2 or still allows the older, more exploitable IMDSv1 is a standard cloud pentest check.

# Enumerating Cloud Resources With Purpose-Built Tools

--> Pacu (an AWS-specific exploitation framework), ScoutSuite, and CloudSploit automate discovery of common cloud misconfigurations across IAM, storage, networking, and logging -- the cloud equivalent of running Nmap/enumeration tools against a traditional network target.

```bash
pacu
> run iam__enum_permissions
> run s3__bucket_finder
```

# Serverless-Specific Attack Surface

--> Lambda functions (covered in the AWS notes) with overly broad execution roles, hardcoded secrets in environment variables, or vulnerable dependencies represent a distinct attack surface from traditional EC2-based targets -- reviewing a function's IAM execution role is often more revealing than trying to find a traditional "server" to exploit.

# Reporting Cloud Findings

--> Cloud findings map directly onto the CVSS-style severity/report structure covered in the Bug Bounty Methodology file, but should explicitly reference the specific IAM policy, resource ARN, and exact misconfiguration -- vague findings ("cloud security could be improved") are far less actionable than "role X can escalate to AdministratorAccess via iam:PassRole on resource Y."
