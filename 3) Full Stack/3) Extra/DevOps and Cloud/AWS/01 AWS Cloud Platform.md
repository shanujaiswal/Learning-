# What Is AWS

--> Amazon Web Services (AWS) is a cloud computing platform offering on-demand infrastructure and services (compute, storage, databases, networking) -- rent resources by usage instead of buying/maintaining physical servers.
--> The largest cloud provider by market share -- most in-demand cloud skill for full-stack/backend roles alongside Azure and Google Cloud.
--> Pay-as-you-go pricing -- costs scale with actual usage (compute hours, storage GB, data transfer), which is powerful but requires active cost monitoring to avoid surprise bills.

# Core Compute -- EC2

--> EC2 (Elastic Compute Cloud) -- rentable virtual servers ("instances") -- the most fundamental building block, roughly equivalent to renting a VPS.
--> Instance types are named by family + size (e.g. t3.micro, m5.large) -- families are optimized for different workloads (t = general purpose burstable, c = compute-optimized, r = memory-optimized).
--> Key concepts: AMI (Amazon Machine Image -- the OS/software template an instance boots from), Security Groups (a virtual firewall controlling inbound/outbound traffic), Key Pairs (SSH key used to access the instance).

```bash
# Basic flow: launch an instance, then SSH into it
ssh -i my-key.pem ec2-user@<public-ip>
```

--> Auto Scaling Group -- automatically adds/removes EC2 instances based on demand (CPU load, request count), similar in spirit to Kubernetes' Horizontal Pod Autoscaler but at the VM level.
--> Elastic Load Balancer (ELB) -- distributes incoming traffic across multiple EC2 instances, providing redundancy and horizontal scaling.

# Storage -- S3

--> S3 (Simple Storage Service) -- object storage for files of any type/size (images, videos, backups, static website assets) -- not a traditional filesystem, but a flat key-value store organized into "buckets."
--> Extremely durable (designed for 99.999999999% durability) and commonly used for: static site hosting, file uploads (user avatars, documents), backups, and as a data lake for analytics.

```bash
aws s3 cp file.txt s3://my-bucket/file.txt   # Upload a file
aws s3 sync ./build s3://my-bucket             # Sync a whole folder (common for deploying a static frontend)
```

--> Bucket policies and ACLs control access -- a classic security mistake is leaving an S3 bucket publicly readable/writable when it shouldn't be; always review bucket permissions explicitly.
--> Storage classes (Standard, Infrequent Access, Glacier) trade retrieval speed for lower storage cost -- useful for archiving old data cheaply.

# Serverless Compute -- Lambda

--> Lambda runs code in response to events (an HTTP request, a file upload to S3, a scheduled timer) WITHOUT provisioning or managing any server -- you pay only for the actual execution time.
--> Ideal for: small API endpoints, background processing triggered by events, scheduled jobs -- less ideal for long-running or highly stateful workloads.

```javascript
// A basic Lambda handler (Node.js)
exports.handler = async (event) => {
  return {
    statusCode: 200,
    body: JSON.stringify({ message: "Hello from Lambda!" }),
  };
};
```

--> Cold starts -- a Lambda function that hasn't run recently takes slightly longer to respond the first time (spinning up its execution environment) -- a known trade-off of serverless compute.
--> API Gateway is commonly paired with Lambda to expose it as an HTTP API endpoint.

# Managed Databases -- RDS and DynamoDB

--> RDS (Relational Database Service) -- managed SQL databases (PostgreSQL, MySQL, etc.) -- AWS handles backups, patching, and replication, removing manual database administration overhead.
--> DynamoDB -- a fully managed NoSQL key-value/document database -- built for massive scale and single-digit-millisecond latency, but requires designing around its access patterns upfront (less flexible querying than SQL).

# Networking Basics -- VPC

--> VPC (Virtual Private Cloud) -- an isolated virtual network within AWS where your resources (EC2, RDS, etc.) live -- lets you control IP ranges, subnets, and routing, similar to designing a private network.
--> Public subnet -- has a route to the internet (for web servers, load balancers). Private subnet -- no direct internet route (for databases, internal services) -- accessed only from within the VPC for security.
--> Security Groups (instance-level firewall) and Network ACLs (subnet-level firewall) both control traffic, at different layers.

# Identity and Access Management -- IAM

--> IAM controls WHO can do WHAT within an AWS account -- Users (individual people/services), Groups (collections of users with shared permissions), Roles (a set of permissions assumable by a service or user without needing sign-in credentials).
--> Principle of Least Privilege -- grant only the specific permissions a user/service actually needs, nothing broader -- the single most important AWS security practice.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": "s3:GetObject",
      "Resource": "arn:aws:s3:::my-bucket/*"
    }
  ]
}
```

--> Never hard-code AWS credentials (access keys) directly in application code -- use environment variables, IAM Roles (for EC2/Lambda), or a secrets manager instead.

# Content Delivery -- CloudFront

--> CloudFront is AWS's CDN (Content Delivery Network) -- caches content (static assets, API responses) at edge locations worldwide, reducing latency for users far from your main server.
--> Commonly placed in front of S3 (for static sites) or an API/load balancer (to cache/accelerate dynamic content).

# Infrastructure as Code -- CloudFormation and Terraform

--> CloudFormation -- AWS's native Infrastructure as Code tool -- define infrastructure (EC2, S3, RDS, networking) in a JSON/YAML template, deploy/update/tear down as a single reproducible "stack."
--> Terraform (by HashiCorp) -- a cloud-agnostic alternative that works across AWS/Azure/GCP with a similar declarative approach -- often preferred for multi-cloud or when avoiding vendor lock-in.
--> Benefit over manual console clicking -- infrastructure changes are version-controlled, reviewable (via PR), and reproducible across environments (dev/staging/prod).

# Deploying a Simple Full-Stack App on AWS (Common Pattern)

--> Frontend (React build) -- S3 (static hosting) + CloudFront (CDN/HTTPS).
--> Backend (Node/Express API) -- Elastic Beanstalk (simplified deploy) or EC2/ECS (more control) or Lambda + API Gateway (serverless).
--> Database -- RDS (PostgreSQL/MySQL) for relational data, or DynamoDB for NoSQL.
--> Domain/HTTPS -- Route 53 (DNS) + ACM (free SSL certificates) tied into CloudFront/Load Balancer.

# AWS Free Tier and Cost Awareness

--> AWS Free Tier offers a limited amount of many services free for 12 months (new accounts) plus some services free indefinitely at low usage -- useful for learning/practicing without immediate cost.
--> Set up Billing Alarms (via CloudWatch) early -- it's easy to accidentally leave a resource running and incur unexpected charges; alerts catch this before it becomes a large bill.
