# Where This Fits Against Terraform and CloudFormation

--> The Terraform file already covers HCL, state, `plan`/`apply`, and modules -- Terraform and AWS CloudFormation (mentioned there as the AWS-native alternative) are both DECLARATIVE, provisioning-focused IaC tools: you describe desired infrastructure, the tool computes and applies a diff.
--> Ansible and Pulumi cover two different gaps -- Ansible is push-based CONFIGURATION management (what runs ON servers that already exist) rather than provisioning; Pulumi is provisioning IaC like Terraform, but written in a general-purpose programming language instead of a declarative DSL. Both are commonly used ALONGSIDE Terraform, not strictly instead of it.

# Ansible -- Agentless, Push-Based Configuration Management

--> Configuration management -- once a server/VM exists, something still has to install packages, write config files, create users, and keep that configuration consistent over its lifetime -- that's Ansible's job, distinct from Terraform provisioning the VM in the first place.
--> Agentless -- unlike some older config management tools (Puppet, Chef) that require an installed agent daemon on every managed machine, Ansible just connects over SSH (or WinRM for Windows) and pushes changes -- nothing to install or keep updated on the target machines themselves.
--> Push-based -- Ansible actively connects out to targets and executes changes when you run it, the mirror image of GitOps's pull-based model (previous file) where an in-cluster agent pulls changes to itself instead.

## Playbooks -- Ansible's Declarative-ish YAML

```yaml
# playbook.yml
- name: Configure web servers
  hosts: webservers          # A group defined in the inventory file
  become: true                # Run tasks with sudo
  tasks:
    - name: Install nginx
      apt:
        name: nginx
        state: present

    - name: Copy nginx config
      copy:
        src: files/nginx.conf
        dest: /etc/nginx/nginx.conf

    - name: Ensure nginx is running
      service:
        name: nginx
        state: started
        enabled: true
```

```ini
# inventory.ini -- which machines "webservers" refers to
[webservers]
web1.example.com
web2.example.com
```

```bash
ansible-playbook -i inventory.ini playbook.yml
```

--> Idempotency -- like Terraform's `plan`/`apply` cycle, running the same playbook twice should produce the same end state without duplicating work -- Ansible's built-in modules (`apt`, `service`, `copy`) are written to check current state before acting, e.g. "install nginx" is a no-op if nginx is already installed at the requested version.
--> Roles -- a reusable, packaged group of tasks/templates/variables (e.g. "a standard web server role") -- Ansible's equivalent of a Terraform module (covered in the Terraform file) or a Helm chart (covered in the Kubernetes Helm file): don't hand-write the same setup for every project.
--> Where Ansible commonly sits in a real stack -- Terraform provisions the VM/network/security group, then Ansible (sometimes triggered as a `local-exec` provisioner, more often as a separate pipeline stage) configures the software running on that VM once it exists.

# Pulumi -- Infrastructure as Code in a Real Programming Language

--> Pulumi provisions cloud infrastructure the same way Terraform does (declarative resource graph, diffing against tracked state, `pulumi up`/`pulumi destroy`) but you write the configuration in an actual general-purpose language (TypeScript, Python, Go, C#) instead of HCL.

```typescript
// index.ts
import * as aws from "@pulumi/aws";

const bucket = new aws.s3.Bucket("app-data", {
    bucket: "my-unique-app-data-bucket",
});

const server = new aws.ec2.Instance("web-server", {
    ami: "ami-0abcdef1234567890",
    instanceType: "t3.micro",
    tags: { Name: "MyWebServer" },
});

export const bucketName = bucket.bucket;
```

```bash
pulumi up          # Equivalent to terraform plan + apply combined (shows diff, then confirms)
pulumi destroy      # Tear down everything this stack manages
```

--> Why choose Pulumi over HCL -- real loops/conditionals/functions/classes instead of HCL's more limited expression language, and the ability to share logic through ordinary code imports rather than the module system's more constrained interface -- appealing to teams that are primarily software engineers already comfortable in that language.
--> Why choose Terraform over Pulumi anyway -- HCL's simplicity is also a feature: it's harder to accidentally write infrastructure code with side effects, hidden control flow, or a bug class that only a general-purpose language allows; Terraform also has a larger, more mature provider ecosystem and is the more common choice/default expectation across the industry.
--> State model -- conceptually identical to Terraform's (a tracked file/backend recording what's been created) -- Pulumi Cloud (or a self-hosted backend) plays the same role Terraform Cloud/an S3 backend plays for Terraform state (covered in the Terraform file).

# Terraform Depth Gaps

## `terraform import` -- Adopting Existing, Unmanaged Infrastructure

--> Real infrastructure often already exists before Terraform is introduced (created by hand in the console, or by an older tool) -- `terraform import` brings an existing resource under Terraform's management by recording it into the state file, WITHOUT recreating it.

```bash
terraform import aws_instance.web_server i-0123456789abcdef0
```

--> Import only populates the STATE -- you must still hand-write a matching `resource` block in your `.tf` config yourself (or generate one with `terraform plan -generate-config-out=`, a newer convenience), otherwise the next `plan` will propose destroying the "unmanaged" resource it can't find configuration for.

## Workspaces -- Managing Multiple Environments from One Config

--> A Terraform workspace lets the SAME configuration manage multiple, independent instances of its state (e.g. dev/staging/prod) without duplicating `.tf` files -- each workspace has its own separate state within the same backend.

```bash
terraform workspace new staging
terraform workspace select staging
terraform apply             # Applies against staging's own state, using the same config files
```

--> Workspaces are a lighter-weight alternative to the more common pattern of separate directories per environment (`environments/dev/`, `environments/prod/`, each with their own state and possibly their own `.tfvars`) -- many teams prefer separate directories anyway, since workspaces make it easy to accidentally `apply` against the wrong environment if you forget which one is currently selected.

## `for_each` and `count` -- Avoiding Copy-Pasted Resource Blocks

--> `count` -- creates N copies of a resource, indexed numerically (`aws_instance.web_server[0]`, `[1]`, ...) -- simplest option when you just need N identical-ish resources.
--> `for_each` -- creates one resource per entry in a map or set, indexed by a stable KEY rather than a numeric position -- the key advantage is that removing an item from the middle of the collection doesn't reshuffle/recreate every resource after it the way `count`'s numeric indices can.

```hcl
variable "bucket_names" {
  type    = set(string)
  default = ["logs", "backups", "uploads"]
}

resource "aws_s3_bucket" "buckets" {
  for_each = var.bucket_names
  bucket   = "my-app-${each.value}"
}
```

--> Rule of thumb -- use `for_each` (keyed, stable) for any collection that might have items added/removed over time; reserve `count` for genuinely simple, rarely-changing repetition, or for conditionally creating a resource 0 or 1 times (`count = var.create_bucket ? 1 : 0`).

## State Locking -- Preventing Concurrent Apply Corruption

--> Remote state (covered in the Terraform file) solves teams sharing ONE state file -- state LOCKING solves two people running `apply` against that shared state AT THE SAME TIME, which without locking can corrupt the state file or produce conflicting infrastructure changes.
--> An S3 backend pairs with a DynamoDB table specifically for locking -- Terraform writes a lock record before starting an `apply` and any other run attempting to start sees the lock and waits/fails instead of proceeding concurrently.

```hcl
terraform {
  backend "s3" {
    bucket         = "my-terraform-state-bucket"
    key            = "prod/terraform.tfstate"
    region         = "us-east-1"
    dynamodb_table = "terraform-locks"     # Enables state locking
  }
}
```

## Terragrunt -- A Thin Wrapper for Multi-Environment Terraform

--> Terragrunt (a separate, third-party tool by Gruntwork) sits on top of Terraform to solve the DRY problem across many environments/modules -- generating backend config, remote state settings, and common variables from a single source rather than repeating them in every environment's directory.
--> Roughly analogous to how Helm layers reusable templating on top of raw Kubernetes YAML (covered in the Kubernetes Helm file) -- Terragrunt doesn't replace Terraform's execution model, it removes boilerplate around invoking it consistently across many environment directories.

## Policy-as-Code -- Sentinel and OPA

--> Policy-as-code enforces organizational rules on infrastructure changes automatically, as part of the pipeline, instead of relying on manual review to catch them -- e.g. "no S3 bucket may be created without encryption enabled," "no instance may be larger than X without an approval tag."
--> Sentinel -- HashiCorp's own policy-as-code framework, tightly integrated with Terraform Cloud/Enterprise -- policies run against a `terraform plan` and can block an `apply` that violates them.
--> OPA (Open Policy Agent) / Rego -- a general-purpose, cloud-agnostic policy engine (not Terraform-specific) that can evaluate policy against Terraform plans, Kubernetes admission requests, or many other JSON-shaped inputs using its own Rego query language -- the more common choice outside of Terraform Enterprise specifically, and often the same tool used for Kubernetes admission control (e.g. rejecting a Pod spec that requests `privileged: true`).

```rego
# Simplified OPA/Rego policy -- deny any S3 bucket resource without encryption configured
deny[msg] {
    resource := input.resource_changes[_]
    resource.type == "aws_s3_bucket"
    not resource.change.after.server_side_encryption_configuration
    msg := "S3 buckets must have encryption configured"
}
```

--> This is the same "shift security/compliance checks left, into the pipeline, as an enforced gate" idea already covered for SAST/DAST in the CI-CD Concepts file -- policy-as-code is that same philosophy applied specifically to infrastructure changes rather than application code.
