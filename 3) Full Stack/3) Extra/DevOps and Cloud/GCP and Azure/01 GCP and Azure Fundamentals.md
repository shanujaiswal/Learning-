# Why Learn Beyond AWS

--> AWS is the largest cloud provider (covered in depth elsewhere in this folder), but Google Cloud Platform (GCP) and Microsoft Azure are the #2 and #3 players -- many companies use one of these instead of, or alongside, AWS. The underlying CONCEPTS transfer almost entirely; mostly it's service names and specific tooling that differ.

# Service Equivalency Table

| Category | AWS | GCP | Azure |
|---|---|---|---|
| Virtual machines | EC2 | Compute Engine | Virtual Machines |
| Object storage | S3 | Cloud Storage | Blob Storage |
| Serverless functions | Lambda | Cloud Functions | Azure Functions |
| Managed Kubernetes | EKS | GKE | AKS |
| Relational database | RDS | Cloud SQL | Azure SQL Database |
| NoSQL database | DynamoDB | Firestore / Bigtable | Cosmos DB |
| Identity & access | IAM | IAM | Entra ID (formerly Azure AD) |
| CDN | CloudFront | Cloud CDN | Azure CDN |
| Container registry | ECR | Artifact Registry | Azure Container Registry |
| Infra as Code (native) | CloudFormation | Deployment Manager | ARM Templates / Bicep |

--> Terraform (covered in its own file) works identically across all three -- one real advantage of investing in Terraform over each provider's own IaC tool.

# Google Cloud Platform (GCP) -- Standout Strengths

--> GKE (Google Kubernetes Engine) -- widely regarded as the most mature, polished managed Kubernetes offering, unsurprising given Google originally created Kubernetes internally (as Borg) before open-sourcing the project.
--> BigQuery -- a serverless data warehouse built for extremely fast SQL analytics over massive datasets, a common reason companies pick GCP specifically for data/analytics workloads.
--> Strong global network infrastructure -- Google's own private backbone network often gives GCP an edge in cross-region latency compared to relying on the public internet between regions.

```bash
gcloud compute instances create my-vm --zone=us-central1-a --machine-type=e2-medium
gsutil cp file.txt gs://my-bucket/file.txt
```

# Microsoft Azure -- Standout Strengths

--> Deepest integration with Microsoft's existing enterprise ecosystem -- Active Directory (now Entra ID), Office 365, Windows Server -- a major reason large enterprises already invested in Microsoft tooling often default to Azure.
--> Azure DevOps -- a fully integrated CI/CD, work-tracking, and repo platform, an all-in-one alternative to piecing together GitHub Actions + a separate project management tool.
--> Hybrid cloud strength (Azure Arc, Azure Stack) -- strong tooling specifically for organizations running a mix of on-premises datacenters and cloud infrastructure together.

```bash
az vm create --resource-group MyGroup --name MyVM --image UbuntuLTS
az storage blob upload --container-name mycontainer --file file.txt --name file.txt
```

# Choosing Between Them (When You Actually Have a Choice)

--> In practice, the choice is often made FOR you by what a company already uses, rather than a green-field technical decision -- but as general tendencies: AWS has the largest ecosystem/maturity and hiring pool, GCP tends to appeal to data/ML-heavy and Kubernetes-native workloads, Azure tends to appeal to organizations already deep in the Microsoft enterprise stack.
--> Multi-cloud (deliberately using more than one provider) adds real operational complexity (different IAM models, different networking primitives) and is usually only justified by a specific requirement (avoiding vendor lock-in contractually, or a specific service one provider does uniquely well) rather than adopted by default.
