# The AWS-Native CI/CD Pipeline

--> AWS offers its own fully managed CI/CD services -- an alternative to running Jenkins yourself or using GitHub Actions, with tighter native integration into the rest of AWS (IAM roles instead of managing separate credentials, direct deploy targets).

# CodeCommit, CodeBuild, CodeDeploy, CodePipeline

--> CodeCommit -- a managed Git repository (largely superseded in practice by teams just using GitHub/GitLab and connecting them to CodePipeline instead).
--> CodeBuild -- runs your build/test steps (compile, run unit tests, build a Docker image) in a managed, pay-per-minute container -- the AWS equivalent of a GitHub Actions runner.
--> CodeDeploy -- automates deploying a built artifact to EC2, Lambda, or ECS, supporting blue/green and rolling deployment strategies out of the box.
--> CodePipeline -- the orchestrator that chains these (and third-party tools) into stages: Source → Build → Test → Deploy, triggering automatically on a new commit.

```yaml
# buildspec.yml -- CodeBuild's build definition, analogous to a GitHub Actions workflow file
version: 0.2
phases:
  install:
    commands:
      - npm install
  build:
    commands:
      - npm run build
      - docker build -t my-app .
  post_build:
    commands:
      - docker push <account>.dkr.ecr.us-east-1.amazonaws.com/my-app:$CODEBUILD_RESOLVED_SOURCE_VERSION
artifacts:
  files:
    - '**/*'
```

# Deployment Strategies

--> Rolling deployment -- gradually replaces old instances/tasks with new ones, similar to a Kubernetes rolling update -- some downtime risk if the new version has a bug, but simple and resource-efficient.
--> Blue/Green deployment -- stands up an entirely separate "green" environment with the new version alongside the running "blue" one, then switches traffic over (via the load balancer or Route 53) once healthy -- enables instant rollback (just switch traffic back) at the cost of running double the infrastructure briefly.
--> Canary deployment -- routes a small percentage of traffic to the new version first, gradually increasing it while watching error rates/metrics -- catches bad deploys with minimal user impact before a full rollout.

# Infrastructure as Code in the Pipeline

--> Pipelines typically don't just deploy application code -- they also apply infrastructure changes (via CloudFormation or Terraform) as a pipeline stage, so infra changes go through the same review/approval process as code changes.
--> `cdk deploy` (AWS CDK -- Cloud Development Kit) -- lets you define infrastructure in actual code (TypeScript/Python/etc.) instead of raw YAML/JSON templates, then synthesizes it down to CloudFormation under the hood -- increasingly preferred over hand-written CloudFormation for complex infrastructure.

# CI/CD Security Practices

--> Pipeline execution roles should follow least privilege -- a pipeline deploying to a dev environment shouldn't hold credentials capable of touching production.
--> Manual approval stages -- CodePipeline supports a required human approval step before a production deployment stage runs, a common compromise between full automation and deployment safety.
--> Secrets used during build/deploy (API keys, deploy credentials) should come from Secrets Manager/Parameter Store at runtime, never committed to the buildspec or repo.
