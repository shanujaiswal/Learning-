# What Terraform Is

--> Terraform (by HashiCorp) is an Infrastructure as Code tool -- you describe the infrastructure you WANT (in a declarative config file), and Terraform figures out how to make the real infrastructure match that description, creating/updating/destroying resources as needed.
--> Unlike AWS's own CloudFormation (mentioned in the AWS notes), Terraform is CLOUD-AGNOSTIC -- the same tool, workflow, and language work across AWS, Azure, GCP, and dozens of other providers, making it the common choice for multi-cloud setups or teams wanting to avoid vendor lock-in on their tooling.

# HCL -- HashiCorp Configuration Language

```hcl
# main.tf
provider "aws" {
  region = "us-east-1"
}

resource "aws_instance" "web_server" {
  ami           = "ami-0abcdef1234567890"
  instance_type = "t3.micro"

  tags = {
    Name = "MyWebServer"
  }
}

resource "aws_s3_bucket" "app_data" {
  bucket = "my-unique-app-data-bucket"
}
```

--> `resource "aws_instance" "web_server"` -- declares a resource of TYPE `aws_instance`, with the local Terraform name `web_server` (used for references within this config, not the actual AWS resource name).

# The Core Terraform Workflow

```bash
terraform init      # Downloads the provider plugins (AWS, Azure, etc.) needed for this config
terraform plan       # Shows EXACTLY what would change -- create/update/destroy -- WITHOUT applying it
terraform apply       # Actually applies those changes to real infrastructure
terraform destroy      # Tears down everything this config manages
```

--> `terraform plan` is the single most important safety habit -- reviewing precisely what will change (often in a PR, as part of code review) BEFORE anything is actually touched, catching mistakes before they hit real infrastructure.

# State -- Terraform's Memory of What It Manages

--> Terraform tracks everything it's created in a State file (`terraform.tfstate`) -- this is how it knows what currently exists and what needs to change to match your config, rather than re-scanning all real infrastructure on every run.
--> Remote state (stored in an S3 bucket, Terraform Cloud, etc., rather than a local file) is essential for any team -- without it, two people running Terraform from their own local state files will conflict/corrupt each other's understanding of the infrastructure.

```hcl
terraform {
  backend "s3" {
    bucket = "my-terraform-state-bucket"
    key    = "prod/terraform.tfstate"
    region = "us-east-1"
  }
}
```

# Variables and Outputs

```hcl
variable "instance_type" {
  description = "EC2 instance type"
  type        = string
  default     = "t3.micro"
}

resource "aws_instance" "web_server" {
  instance_type = var.instance_type
  ami           = "ami-0abcdef1234567890"
}

output "instance_public_ip" {
  value = aws_instance.web_server.public_ip
}
```

--> Variables let the same configuration be reused across environments (dev/staging/prod) just by supplying different values, rather than duplicating the whole config file per environment.

# Modules -- Reusable Infrastructure Blueprints

--> A Module is a reusable, packaged group of resources (e.g. "a standard VPC setup," "a standard web-server-plus-load-balancer setup") -- avoids copy-pasting the same resource blocks across every project that needs the same infrastructure pattern.

```hcl
module "vpc" {
  source     = "./modules/vpc"
  cidr_block = "10.0.0.0/16"
}
```

# Why This Matters -- Connecting Back to CI/CD

--> Terraform runs are typically wired into the CI/CD pipeline (covered in the AWS CI/CD file) as their own stage -- infrastructure changes go through the same review/plan/approve process as application code changes, rather than being made by hand through a cloud console.
