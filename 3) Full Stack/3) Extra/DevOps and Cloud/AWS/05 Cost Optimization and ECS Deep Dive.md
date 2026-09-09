# ECS in Depth -- Task Definitions and Services

--> Task Definition -- the blueprint for a container (or group of tightly-coupled containers) -- specifies image, CPU/memory, environment variables, port mappings, and IAM role -- conceptually similar to a Kubernetes Pod spec.
--> Task -- a running instance of a Task Definition -- similar to a Kubernetes Pod.
--> ECS Service -- keeps a specified number of Tasks running, replacing failed ones and integrating with a Load Balancer -- similar to a Kubernetes Deployment + Service combined.

```json
{
  "family": "my-app",
  "containerDefinitions": [
    {
      "name": "my-app",
      "image": "<account>.dkr.ecr.us-east-1.amazonaws.com/my-app:latest",
      "portMappings": [{ "containerPort": 3000 }],
      "memory": 512,
      "cpu": 256
    }
  ],
  "requiresCompatibilities": ["FARGATE"],
  "networkMode": "awsvpc"
}
```

# Service Discovery Between ECS Services

--> AWS Cloud Map -- provides internal DNS-based service discovery for ECS services, so one service can reach another by a stable name (`billing.internal`) instead of tracking changing task IPs -- the ECS equivalent of Kubernetes' built-in Service DNS.

# Fargate Spot -- Cheaper Compute for Fault-Tolerant Workloads

--> Fargate Spot runs tasks on spare AWS capacity at up to ~70% discount vs standard Fargate pricing -- AWS can reclaim that capacity with only ~2 minutes' notice.
--> Appropriate for: batch jobs, CI runners, stateless workers that can tolerate being interrupted and retried -- NOT appropriate for a primary user-facing API without redundancy across both Spot and standard capacity.

# Reserved Instances, Savings Plans and Spot Instances (EC2)

--> On-Demand -- pay full price per hour, no commitment -- most expensive, most flexible.
--> Reserved Instances (RI) -- commit to a specific instance type/region for 1 or 3 years for a significant discount (up to ~70%) -- best for steady, predictable baseline load.
--> Savings Plans -- similar discount model to RIs but more flexible (commit to a $/hour spend rather than a specific instance type) -- easier to apply across a mixed/evolving fleet.
--> Spot Instances -- bid on unused EC2 capacity at up to ~90% discount -- can be reclaimed by AWS with 2 minutes' notice, so only for interruption-tolerant workloads (batch processing, stateless horizontally-scaled workers, CI).
--> Typical production cost strategy -- Reserved/Savings Plans for the predictable baseline, On-Demand or Auto Scaling for handling variable peak load, Spot for anything interruption-tolerant.

# Cost Allocation and Visibility

--> Cost Allocation Tags -- tag every resource (Project, Environment, Team) so Cost Explorer can break down spend by tag -- without tagging discipline, a growing bill becomes very hard to attribute or optimize.
--> AWS Cost Explorer -- visualizes spend trends over time, filterable by service/tag/account -- the starting point for any cost investigation.
--> AWS Budgets -- set a spend threshold per project/tag and get alerted (or even automatically act) before it's exceeded, going further than the basic CloudWatch billing alarms.
--> Trusted Advisor -- flags concrete cost-saving opportunities automatically (idle load balancers, underutilized EC2 instances, unattached EBS volumes still being billed).

# Right-Sizing and Common Waste

--> The single most common AWS cost mistake -- over-provisioned EC2/RDS instances chosen "to be safe" and never revisited after launch, even as actual CPU/memory usage stays consistently low.
--> Unattached EBS volumes and old snapshots left behind after an instance is terminated continue to be billed indefinitely until manually cleaned up.
--> NAT Gateways bill per-hour AND per-GB processed -- a frequently underestimated line item in VPC-heavy architectures; consolidating NAT usage or using VPC endpoints for AWS service traffic (S3, DynamoDB) avoids routing that traffic through a NAT Gateway unnecessarily.
