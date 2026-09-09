# Running Containers on AWS -- ECS, Fargate and EKS

--> ECS (Elastic Container Service) -- AWS's own container orchestrator, simpler than Kubernetes, tightly integrated with other AWS services.
--> Fargate -- a serverless mode for ECS (or EKS) -- run containers WITHOUT provisioning or managing the underlying EC2 instances at all; you just specify CPU/memory per container and AWS handles the servers.
--> EKS (Elastic Kubernetes Service) -- managed Kubernetes on AWS -- AWS runs the control plane; you still design pods/deployments/services the standard Kubernetes way.
--> Rule of thumb -- ECS/Fargate for simpler container workloads already committed to AWS; EKS when you need standard Kubernetes portability or already have Kubernetes expertise.

# Messaging and Decoupling -- SQS and SNS

--> SQS (Simple Queue Service) -- a managed message queue -- one service places a message on the queue, another service (or many, competing) picks it up and processes it. Decouples producers from consumers and smooths out traffic spikes (messages wait in the queue instead of overwhelming a downstream service).
--> SNS (Simple Notification Service) -- publish/subscribe messaging -- one message published to a "topic" can be delivered to many subscribers at once (multiple SQS queues, Lambda functions, emails) simultaneously, unlike SQS's one-consumer-per-message model.
--> Common pattern -- SNS fans a single event out to multiple SQS queues, each consumed independently by a different service (e.g. "order placed" triggers separate queues for billing, inventory, and email notification).

```bash
aws sqs send-message --queue-url <url> --message-body "process order 123"
```

# Monitoring and Logging -- CloudWatch

--> CloudWatch Metrics -- tracks numeric data over time (CPU usage, request count, error rate) for most AWS services automatically; supports custom application metrics too.
--> CloudWatch Logs -- centralized log storage/search for applications running on EC2, Lambda, ECS, etc. -- avoids SSH-ing into individual servers to read log files.
--> CloudWatch Alarms -- triggers a notification (or auto-scaling action) when a metric crosses a threshold, e.g. "alert if average CPU > 80% for 5 minutes" or the billing alarms mentioned for cost control.

# Availability Zones, Regions and Multi-Region Design

--> Region -- a geographic area (e.g. `us-east-1`) containing multiple, physically separate data centers.
--> Availability Zone (AZ) -- an isolated data center within a region -- deploying across multiple AZs protects against a single data center failure (power outage, hardware fault) without needing a different region.
--> Multi-region -- deploying the same application in entirely separate geographic regions -- protects against a whole-region outage and reduces latency for globally distributed users, at the cost of significantly more complexity (data replication/consistency across regions).
--> Most applications should start multi-AZ (cheap insurance, AWS makes it easy) and only go multi-region when there's a specific latency or disaster-recovery requirement that justifies the added complexity.

# DNS and Elastic Beanstalk (Simplified Deployment)

--> Route 53 -- AWS's DNS service -- routes a domain name to your infrastructure (a Load Balancer, CloudFront, S3 bucket) and supports health-check-based failover between endpoints.
--> Elastic Beanstalk -- a "deploy your code, we'll handle the infrastructure" platform -- you upload application code/a container, and Beanstalk provisions the EC2 instances, load balancer, auto-scaling, and health monitoring behind the scenes. Trades fine-grained control for a much faster path to production than manually wiring EC2 + ELB + Auto Scaling yourself.
