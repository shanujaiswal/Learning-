# FinOps -- Cost Governance Beyond AWS-Specific Tooling

--> The AWS Cost Optimization file already covers AWS-native tools -- Cost Explorer, Budgets, Trusted Advisor, Reserved Instances/Savings Plans/Spot -- FinOps is the broader organizational DISCIPLINE those tools serve, and it's the same discipline whether you're on AWS, Azure, GCP, or (increasingly common) more than one at once.
--> FinOps treats cloud cost the way DevOps treats delivery -- a cross-functional practice (engineering, finance, and leadership collaborating continuously) rather than a monthly bill finance complains about after the fact with no engineering context to act on it.

## Unit Economics

--> Unit economics -- cost expressed per meaningful business unit (cost per customer, cost per API request, cost per order processed) rather than as a single opaque total cloud bill -- this is what actually lets an organization answer "is this feature/product profitable," not just "did our bill go up."
--> Without unit economics, a rising cloud bill is ambiguous -- it might mean healthy growth (more customers, proportionally more cost) or genuine waste (inefficiency growing faster than usage) -- unit cost trending flat or down while volume grows is the actual signal of healthy efficiency.

## Showback and Chargeback

--> Showback -- cost is broken down and reported back to the team/project responsible (via cost allocation tags, already mentioned in the AWS Cost Optimization file) so they can SEE their own spend, without any money actually moving between budgets.
--> Chargeback -- goes a step further and actually bills each team's/product's budget for its share of cloud spend -- creates a much stronger incentive to optimize, since cost now hits a team's own numbers directly, but requires much more mature, reliable cost-allocation tagging to be fair (misattributed shared infrastructure cost becomes a real political problem under chargeback in a way it isn't under mere showback).
--> Showback is the far more common starting point in practice -- chargeback's accounting overhead and the risk of tagging-attribution disputes make many organizations stop at showback, using it purely as an awareness/behavior-change tool rather than a formal internal billing system.

## Kubecost

--> Kubecost addresses a specific gap the AWS-native tools can't fill -- Cost Explorer/tags attribute cost by AWS resource, but a single EKS/GKE/AKS cluster typically runs many teams' workloads as pods sharing the same underlying nodes, and AWS billing has no visibility into how that shared node cost splits across pods/namespaces/deployments.
--> Kubecost allocates real cluster cost (compute, memory, storage, even cloud LoadBalancer cost) down to the namespace, deployment, or even individual pod level, by combining actual cloud billing data with in-cluster resource usage -- effectively bringing showback/chargeback granularity INTO Kubernetes, one layer below what cloud-provider billing tools can see on their own.

```bash
# Kubecost is commonly installed via its Helm chart (same packaging model covered in the Kubernetes Helm file)
helm install kubecost kubecost/cost-analyzer --namespace kubecost --create-namespace
```

--> A common finding once teams install Kubecost -- namespaces with generous resource REQUESTS (covered in the Kubernetes Probes and Resource Limits file) but much lower actual usage are silently paying for capacity nobody's using, the cluster-level version of the "over-provisioned EC2 instance never revisited" waste pattern already called out in the AWS Cost Optimization file.

# Multi-Cloud and Hybrid Strategy

--> Multi-cloud -- deliberately running workloads across more than one cloud provider (AWS + GCP, or AWS + Azure) rather than committing entirely to one -- distinct from simply having the ABILITY to (which Terraform's cloud-agnostic design, covered in the Terraform file, provides regardless of whether you actually use more than one provider).
--> Genuine reasons to go multi-cloud -- avoiding vendor lock-in for a specific critical capability, meeting a data-residency/regulatory requirement only satisfied by a specific provider's regional footprint, or a result of company mergers/acquisitions inheriting infrastructure on different clouds -- NOT simply "redundancy," since running the same workload identically on two clouds at once is usually a large multiplier of operational complexity for a failure mode (a whole cloud provider going down) that's already rare and already partially addressed by the multi-AZ/multi-region strategy covered in the AWS file.
--> Hybrid cloud -- a mix of on-premises/private data center infrastructure and public cloud, common for organizations with existing data center investment, strict data-residency needs, or workloads with steady, predictable baseline load (cheaper to own than rent long-term) supplemented by cloud for burst capacity.

## The 6 R's of Migration

--> A framework for classifying HOW to move a given workload to the cloud (or between clouds) -- useful because "just lift it and put it in the cloud" is only one of several legitimate strategies, and picking the wrong one for a given workload wastes significant migration effort.
--> Rehost ("lift and shift") -- move the workload as-is onto cloud infrastructure (e.g. an on-prem VM becomes an EC2 instance) with minimal changes -- fastest, but doesn't capture cloud-native benefits (autoscaling, managed services).
--> Replatform ("lift, tinker and shift") -- move with some optimization along the way, e.g. swapping a self-managed database for a managed RDS instance, without a full application rewrite.
--> Refactor/Re-architect -- redesign the application to be cloud-native (breaking a monolith into microservices on Kubernetes, adopting serverless) -- highest effort, but captures the most long-term benefit; usually reserved for a workload that genuinely justifies the investment.
--> Repurchase -- replace the workload entirely with a SaaS product (e.g. moving a self-hosted CRM to Salesforce) rather than migrating the old system at all.
--> Retain -- deliberately leave a workload where it is for now (too risky/low-value to migrate yet) -- a legitimate, explicit decision, not a failure to migrate.
--> Retire -- decommission a workload found to be unused/redundant during migration planning -- migration planning frequently surfaces genuinely dead systems nobody had gotten around to shutting down, which is itself a cost-optimization win before any migration work even happens.

## Azure Arc and Cross-Environment Management

--> Azure Arc extends Azure's management plane (policy enforcement, monitoring, RBAC) OVER infrastructure that isn't actually running in Azure -- on-prem servers, Kubernetes clusters on other clouds, even AWS/GCP resources -- registering them as Arc-enabled resources so they show up and can be governed alongside genuinely Azure-native resources in one control plane.
--> This addresses a real hybrid/multi-cloud pain point directly -- without a tool like Arc, an organization running infrastructure across on-prem + Azure + another cloud has to apply security policy, patching, and monitoring separately in each environment with separate tooling; AWS and GCP have their own rough equivalents (AWS Systems Manager's hybrid activation, Google's Anthos/GKE Enterprise) aimed at the same cross-environment management gap.

# Kubernetes Networking Depth

--> The Kubernetes Observability file covers what happens ON TOP of pod-to-pod networking (a service mesh's sidecar proxies) -- this section covers the networking layer underneath that a service mesh sits on top of.

## CNI Plugins -- Calico and Cilium

--> CNI (Container Network Interface) -- the plugin interface Kubernetes uses to actually wire up pod networking -- a cluster doesn't have real networking until a CNI plugin is installed; Kubernetes itself defines the API (Pods get IPs, can reach each other) but delegates the actual implementation to a CNI plugin.
--> Calico -- one of the most widely used CNI plugins, notable for enforcing `NetworkPolicy` (below) at layer 3/4 using standard Linux networking (iptables/eBPF depending on configuration) -- often the default choice for straightforward network policy enforcement needs.
--> Cilium -- a newer CNI plugin built on eBPF (running programs directly in the Linux kernel rather than relying on iptables rule chains) -- offers significantly better performance at scale plus deeper capability: layer 7 (HTTP/gRPC-aware) network policies, and built-in observability (Hubble) that can show a live service dependency map, overlapping somewhat with what a service mesh's sidecars provide but at the kernel/network layer instead of via a per-pod proxy.
--> Choosing between them in practice -- Calico for simpler L3/L4 policy needs with a longer operational track record; Cilium when you want L7-aware policy, better performance at high pod density, or the built-in Hubble observability without also adopting a full service mesh.

## The NetworkPolicy Resource

--> By default, Kubernetes pods can reach every other pod in the cluster with no restriction -- a `NetworkPolicy` resource is how you actually restrict that, functioning like a firewall rule scoped to pods matched by label selectors (the same label-selector pattern already used by Services and Deployments in the Kubernetes fundamentals file).
--> A `NetworkPolicy` resource does nothing on its own without a CNI plugin that implements policy enforcement (Calico or Cilium above, among others) -- some simpler CNI plugins don't enforce `NetworkPolicy` at all, silently leaving the cluster fully open despite policies being defined.

```yaml
# Only allow pods labeled "role: frontend" to reach this app's pods on port 3000
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-frontend-only
spec:
  podSelector:
    matchLabels:
      app: my-app
  policyTypes:
    - Ingress
  ingress:
    - from:
        - podSelector:
            matchLabels:
              role: frontend
      ports:
        - protocol: TCP
          port: 3000
```

--> Default-deny -- a common baseline hardening pattern is a `NetworkPolicy` that denies all ingress/egress traffic to a namespace by default, then explicitly allowing only the specific pod-to-pod connections actually required -- the network equivalent of the least-privilege IAM practice already covered in the AWS security file, applied to pod traffic instead of API permissions.

# Serverless Beyond Lambda

--> AWS Lambda is covered in the main AWS files as AWS's serverless compute offering -- every major cloud, plus the open-source Kubernetes ecosystem, has converged on the same core idea: run code in response to an event/request without provisioning or managing any server, billed only for actual execution time.
--> Google Cloud Functions / Azure Functions -- direct equivalents of Lambda on their respective clouds -- same event-driven trigger model (HTTP request, a message queue, a storage bucket write), same "you write a function, the platform handles scaling and the underlying compute" value proposition, with provider-specific trigger integrations (Azure Functions integrates natively with Azure Event Grid/Service Bus, the way Lambda integrates natively with SQS/SNS as covered in the AWS file).
--> Knative -- brings the serverless request-driven model ONTO Kubernetes itself -- deploy a container the normal Kubernetes way, and Knative Serving automatically scales it from (and back down to) zero replicas based on incoming request traffic, giving Lambda-style "pay only when handling requests" economics for a plain containerized app without needing a separate proprietary FaaS platform.

```yaml
# Knative Service -- looks like a lightweight Kubernetes Deployment, but scales to zero when idle
apiVersion: serving.knative.dev/v1
kind: Service
metadata:
  name: my-app
spec:
  template:
    spec:
      containers:
        - image: my-app:1.0
```

--> Serverless Framework -- a deployment/tooling layer (not a runtime itself) that defines serverless functions and their triggers as code (`serverless.yml`) and deploys them to Lambda, Google Cloud Functions, Azure Functions, or others from one consistent workflow -- conceptually similar to how Terraform provides one consistent workflow across many infrastructure providers (covered in the Terraform file), applied specifically to the serverless-function deployment use case rather than general infrastructure provisioning.
--> When serverless earns its complexity vs. a container on Fargate/ECS/a Kubernetes Deployment -- genuinely spiky/intermittent traffic where scale-to-zero billing matters, and simple, short-lived, stateless request/event handling; a steady-traffic service or one needing long-running connections/heavy startup cost is usually better served by the always-on container patterns covered throughout the Docker and Kubernetes files.
