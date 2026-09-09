# Secrets Manager and Parameter Store -- Never Hard-Code Credentials

--> Secrets Manager -- stores sensitive values (DB passwords, API keys) encrypted, with automatic rotation support (e.g. rotate an RDS password on a schedule without manual coordination with the app).
--> Systems Manager Parameter Store -- a cheaper, simpler alternative for configuration/secrets that don't need automatic rotation -- fine for most non-database secrets.
--> Application code fetches secrets at runtime via the AWS SDK/IAM role -- never bakes them into a Docker image, environment file committed to git, or a Lambda's plaintext environment variable.

```python
import boto3
client = boto3.client("secretsmanager")
secret = client.get_secret_value(SecretId="prod/db-password")["SecretString"]
```

# KMS -- Encryption Key Management

--> KMS (Key Management Service) -- creates and manages encryption keys used to encrypt data at rest across AWS (S3 buckets, RDS, EBS volumes, Secrets Manager itself) without you having to build or store key material yourself.
--> Customer Managed Keys (CMK) vs AWS Managed Keys -- CMKs give full control over key rotation/policy/access; AWS Managed Keys are simpler defaults with less configurability.
--> Envelope encryption -- KMS typically encrypts a smaller "data key" which then encrypts the actual bulk data -- this is why KMS scales to encrypting large amounts of data without every byte round-tripping through the KMS API.

# WAF -- Web Application Firewall

--> AWS WAF sits in front of CloudFront, an Application Load Balancer, or API Gateway, filtering HTTP requests against rules BEFORE they reach your application.
--> Managed Rule Groups -- pre-built rule sets for common threats (SQL injection patterns, known bad bot signatures, OWASP Top 10 patterns) -- maintained by AWS, no need to write detection logic yourself.
--> Rate-based rules -- automatically block/throttle an IP making an abnormally high number of requests, a first line of defense against basic DDoS/scraping/credential-stuffing attempts.

```json
{
  "Name": "RateLimitRule",
  "Statement": {
    "RateBasedStatement": {
      "Limit": 2000,
      "AggregateKeyType": "IP"
    }
  },
  "Action": { "Block": {} }
}
```

# Shield -- DDoS Protection

--> Shield Standard -- automatic, free protection against common network/transport-layer DDoS attacks, enabled by default for every AWS customer.
--> Shield Advanced -- paid tier adding protection against larger/more sophisticated attacks, 24/7 access to AWS's DDoS response team, and cost protection (credits if a DDoS attack spikes your bill via auto-scaling).

# GuardDuty -- Threat Detection

--> Continuously analyzes account activity (CloudTrail logs, VPC flow logs, DNS logs) for suspicious patterns -- compromised credentials being used from an unusual location, an EC2 instance suddenly communicating with a known malicious IP, unusual API call patterns consistent with reconnaissance.
--> Fully managed -- no infrastructure to run; findings appear as prioritized alerts rather than requiring you to build detection rules from raw logs yourself.

# Security Hub -- Centralized Posture Management

--> Aggregates findings from GuardDuty, Inspector (vulnerability scanning for EC2/containers), Macie (sensitive data discovery in S3), and WAF into one dashboard, scored against compliance standards (CIS AWS Foundations, PCI-DSS).
--> Useful for answering "what's our overall security posture right now" across an entire account/organization instead of checking each service's console individually.

# Baseline Hardening Checklist

--> Enable MFA on the root account and all IAM users with console access -- the root account itself should essentially never be used day-to-day.
--> Never use the root account's access keys for anything -- create IAM users/roles with least-privilege policies instead.
--> Enable CloudTrail (API call logging) in all regions, delivered to a locked-down S3 bucket -- the audit trail needed to investigate any incident after the fact.
--> Block public access on S3 buckets at the account level by default, only opening specific buckets deliberately when actually needed.
