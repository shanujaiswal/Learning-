# CI/CD Platforms Beyond AWS-Native and GitHub Actions

--> AWS's own CodePipeline/CodeBuild (covered in the AWS CI/CD file) and GitHub Actions (covered in depth in file 02 of this folder) are two options among many -- Jenkins and GitLab CI/CD remain extremely common, especially in self-hosted/on-prem or pre-cloud-native organizations.
--> All of these tools solve the same core problem -- automatically build, test, and deploy code on every change -- they differ mainly in HOW pipelines are defined, WHERE they run, and how much operational overhead they push onto you.

# Jenkins -- The Original Extensible CI Server

--> Jenkins is a self-hosted, open-source automation server -- one of the oldest and most widely deployed CI tools, known for being infinitely extensible via plugins rather than for being easy to operate.
--> Unlike GitHub Actions or GitLab CI/CD (SaaS-first, config lives in the repo), Jenkins traditionally required a dedicated server you install, patch, and scale yourself -- a real operational cost that newer tools avoid.

## Pipeline-as-Code -- the Jenkinsfile

--> Modern Jenkins usage defines pipelines as code in a `Jenkinsfile` checked into the repo (replacing the old practice of clicking through a web UI to configure jobs) -- this brings the same code-review/version-control benefits that `.gitlab-ci.yml` and GitHub Actions workflow files provide natively.

```groovy
// Jenkinsfile -- Declarative Pipeline syntax
pipeline {
    agent any                     // Run on any available agent/executor

    stages {
        stage('Build') {
            steps {
                sh 'npm install'
                sh 'npm run build'
            }
        }
        stage('Test') {
            steps {
                sh 'npm test'
            }
        }
        stage('Deploy') {
            when {
                branch 'main'      // Only deploy from the main branch
            }
            steps {
                sh './deploy.sh'
            }
        }
    }

    post {
        failure {
            mail to: 'team@example.com', subject: 'Build failed', body: 'Check Jenkins.'
        }
    }
}
```

--> Declarative Pipeline (shown above) -- a structured, opinionated syntax, easier to read and lint. Scripted Pipeline -- raw Groovy code, more flexible but harder to maintain; declarative is the recommended default for new pipelines.

## Plugins -- Jenkins's Defining Feature (and Liability)

--> Jenkins's plugin ecosystem (thousands of plugins) is why it can integrate with almost anything -- Git, Docker, Kubernetes, Slack, every cloud provider, every artifact repository.
--> The tradeoff -- plugin version incompatibilities, security vulnerabilities in unmaintained plugins, and upgrade fragility are the most common source of Jenkins operational pain, which is a major reason SaaS-native platforms (GitHub Actions, GitLab CI/CD, CircleCI) have gained ground for greenfield projects.

## Agents and Executors -- Where the Work Actually Runs

--> Controller (formerly "master") -- the central Jenkins server that schedules jobs, serves the web UI, and stores configuration -- should NOT run heavy build workloads itself.
--> Agent (formerly "slave") -- a separate machine (or container/pod) that connects to the controller and actually executes pipeline steps -- Jenkins scales horizontally by adding more agents.
--> Executor -- a slot on an agent capable of running one build at a time; an agent can have multiple executors to run several jobs concurrently.
--> Kubernetes plugin -- dynamically provisions agents as short-lived pods in a Kubernetes cluster, spinning one up per build and tearing it down afterward -- avoids maintaining a fixed pool of always-on agent VMs, conceptually similar to GitHub Actions' hosted runners spinning up fresh per job.

```groovy
// Requesting a Kubernetes-provisioned agent instead of a static one
pipeline {
    agent {
        kubernetes {
            yaml '''
            spec:
              containers:
              - name: node
                image: node:20
            '''
        }
    }
    stages {
        stage('Build') { steps { sh 'npm ci' } }
    }
}
```

# GitLab CI/CD

--> GitLab CI/CD is built directly into GitLab (source control + CI/CD + issue tracking in one product) -- pipelines are defined by a single `.gitlab-ci.yml` file at the repo root, auto-detected and run on every push, no separate server setup required from the user's perspective.

```yaml
# .gitlab-ci.yml
stages:
  - build
  - test
  - deploy

build_job:
  stage: build
  script:
    - npm install
    - npm run build
  artifacts:
    paths:
      - dist/

test_job:
  stage: test
  script:
    - npm test

deploy_job:
  stage: deploy
  script:
    - ./deploy.sh
  only:
    - main          # Only run this job on the main branch
```

--> Stages run in order (`build` → `test` → `deploy`); jobs within the same stage run in parallel by default.
--> `artifacts` -- files produced by one job (a build output, a test report) that get passed forward to later stages -- the same underlying need GitHub Actions solves with its own `actions/upload-artifact`.

## Runners -- GitLab's Equivalent of Jenkins Agents / GH Actions Runners

--> A GitLab Runner is the agent process that actually executes job scripts -- GitLab.com provides shared runners for free/paid tiers, or you can register self-hosted runners (on your own VMs, Kubernetes cluster, or bare metal) for more control, custom hardware, or to keep builds inside a private network.
--> This mirrors the same self-hosted-vs-managed tradeoff covered for GitHub Actions runners in file 02 -- shared/hosted runners for convenience, self-hosted runners when you need specific dependencies, GPUs, network access to internal systems, or lower cost at high volume.

# CircleCI and Travis CI -- Brief Notes

--> CircleCI -- a SaaS CI/CD platform (config in `.circleci/config.yml`) historically popular for its fast Docker-layer caching and clean UI; conceptually very similar to GitHub Actions/GitLab CI/CD -- YAML-defined jobs, hosted or self-hosted ("runner") execution, a marketplace of reusable config ("Orbs," CircleCI's equivalent of GitHub Actions/reusable workflows).
--> Travis CI -- one of the earliest hosted CI services, especially popular in the open-source world for public GitHub repos; its relevance has declined significantly as GitHub Actions (native to GitHub, free for public repos) absorbed most of that use case.
--> The broader trend across all of these tools -- CI/CD platforms converge on the same shape (YAML pipeline definition checked into the repo, stages/jobs, hosted or self-hosted runners, a marketplace/registry of reusable steps) -- the meaningful differences are ecosystem integration, pricing, and operational ownership rather than fundamentally different capabilities.
