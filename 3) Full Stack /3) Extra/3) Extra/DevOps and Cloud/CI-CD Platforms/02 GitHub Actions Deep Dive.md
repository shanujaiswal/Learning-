# Why GitHub Actions Gets Its Own Deep Dive

--> GitHub Actions has come up only in passing elsewhere (as a point of comparison for AWS CodePipeline and Jenkins) -- but it's the most widely used CI/CD platform for GitHub-hosted projects specifically because it's built directly into GitHub, config lives in the repo, and it triggers on virtually any repo event with no external service to wire up.
--> Same core idea as GitLab CI/CD's `.gitlab-ci.yml` (previous file) -- YAML workflow files, jobs made of steps, hosted or self-hosted runners -- but with its own syntax and its defining feature: the Actions Marketplace.

# Workflow YAML Syntax

--> Workflow files live in `.github/workflows/*.yml` -- each file is one workflow, and a repo can have many, each triggered independently.

```yaml
# .github/workflows/ci.yml
name: CI

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  build-and-test:
    runs-on: ubuntu-latest        # A GitHub-hosted runner

    steps:
      - name: Checkout code
        uses: actions/checkout@v4        # A reusable "Action" from the marketplace

      - name: Set up Node
        uses: actions/setup-node@v4
        with:
          node-version: '20'

      - name: Install dependencies
        run: npm ci

      - name: Run tests
        run: npm test

      - name: Build
        run: npm run build
```

--> `on` -- defines the trigger(s): `push`, `pull_request`, `schedule` (cron), `workflow_dispatch` (manual trigger button), or events from other workflows.
--> `jobs` -- a workflow is made of one or more jobs; by default jobs run in parallel unless one declares `needs: [other_job]` to run after it.
--> `steps` -- each step is either `run` (a raw shell command) or `uses` (invokes a reusable Action) -- most real workflows are a mix of both.
--> `runs-on` -- selects the runner's OS/environment (`ubuntu-latest`, `windows-latest`, `macos-latest`, or a self-hosted label).

# The Actions Marketplace

--> An "Action" is a packaged, reusable unit of workflow logic -- published by GitHub, third parties, or your own org -- referenced with `uses: owner/repo@version` (e.g. `actions/checkout@v4`).
--> This is conceptually the same idea as CircleCI's "Orbs" (mentioned in file 01) or a Terraform module -- don't hand-write logic that's already a well-maintained, versioned, shared component.
--> Pinning to a version -- `@v4` tracks a major version tag (receives non-breaking updates); pinning to a full commit SHA is the more security-conscious choice for third-party actions, since a tag can be moved to point at different code but a SHA cannot.

```yaml
      - uses: docker/build-push-action@v5     # Marketplace action: build & push a Docker image
        with:
          push: true
          tags: myorg/my-app:${{ github.sha }}
```

# Matrix Builds -- Running the Same Job Across Many Configurations

--> A matrix build runs the same job multiple times, once per combination of variables -- the standard way to test a library across multiple language versions, OSes, or dependency versions without duplicating the job definition.

```yaml
jobs:
  test:
    strategy:
      matrix:
        node-version: [18, 20, 22]
        os: [ubuntu-latest, windows-latest]
    runs-on: ${{ matrix.os }}
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: ${{ matrix.node-version }}
      - run: npm ci && npm test
```

--> The example above runs 3 × 2 = 6 jobs in parallel (every combination of `node-version` and `os`) -- `fail-fast: false` (a strategy option) keeps the other jobs running even if one combination fails, useful when you want the full compatibility picture rather than stopping at the first failure.

# Self-Hosted Runners

--> GitHub-hosted runners (`runs-on: ubuntu-latest`) are ephemeral VMs GitHub provisions, runs your job on, and destroys -- zero maintenance, but limited hardware, no access to private/internal networks, and metered pricing on private repos.
--> Self-hosted runners -- your own machine (VM, on-prem server, or a pod in your Kubernetes cluster) registered to your repo/org, matched by label instead of `ubuntu-latest`.

```yaml
jobs:
  deploy:
    runs-on: [self-hosted, linux, gpu]   # Matches a runner registered with these labels
    steps:
      - run: ./deploy-to-internal-network.sh
```

--> Reasons to self-host -- need GPU hardware, need to reach resources inside a private VPC without exposing them publicly, want to avoid per-minute billing at high volume, or need a specific pre-installed toolchain.
--> Cost/tradeoff -- you take on the same patching/scaling/security burden that made Jenkins agents (previous file) operationally heavy -- self-hosted runners are not "free," they're a shift of cost from GitHub's billing to your own infrastructure and ops time.

# Reusable Workflows

--> A reusable workflow is an entire workflow file called from another workflow with `uses`, parameterized by inputs -- the way to avoid copy-pasting the same multi-job pipeline across many repos, similar in spirit to a Terraform module (covered in the Terraform file) or a Helm chart (covered in the Kubernetes Helm file).

```yaml
# .github/workflows/reusable-deploy.yml
on:
  workflow_call:
    inputs:
      environment:
        required: true
        type: string

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - run: ./deploy.sh --env ${{ inputs.environment }}
```

```yaml
# .github/workflows/ci.yml -- calling the reusable workflow
jobs:
  deploy-staging:
    uses: my-org/my-repo/.github/workflows/reusable-deploy.yml@main
    with:
      environment: staging
```

--> A "composite action" is the smaller-grained equivalent -- reusable steps within a single job, rather than a whole reusable job/workflow.

# Caching Dependencies

--> Cache -- persists files (a `node_modules` folder, a Python venv, a Docker layer cache) between workflow runs, keyed by a hash of the lockfile, so unchanged dependencies don't get re-downloaded/re-built every run.

```yaml
      - uses: actions/cache@v4
        with:
          path: ~/.npm
          key: npm-${{ hashFiles('package-lock.json') }}
          restore-keys: |
            npm-
```

--> `setup-node`, `setup-python`, etc. actions often have built-in caching via a simple `cache: 'npm'` input -- reach for that before hand-rolling `actions/cache` unless you need more control.
--> A cache miss (key not found) falls back to `restore-keys` prefixes, restoring the closest previous cache rather than starting from nothing -- balances build speed against exact correctness.

# Artifacts -- Passing Files Between Jobs

--> Unlike a cache (best-effort, for speed), an Artifact is the durable, intentional output of a job -- test reports, build binaries, coverage output -- available to download from the workflow run's UI, or passed to a later job in the same workflow.

```yaml
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - run: npm run build
      - uses: actions/upload-artifact@v4
        with:
          name: dist-output
          path: dist/

  deploy:
    needs: build
    runs-on: ubuntu-latest
    steps:
      - uses: actions/download-artifact@v4
        with:
          name: dist-output
      - run: ./deploy.sh
```

--> This is the same underlying need GitLab CI/CD's `artifacts:` keyword serves (previous file) and what CodeBuild's `artifacts` block handles in AWS's pipeline -- every CI/CD platform needs a way to carry a build's output forward without rebuilding it in every subsequent job/stage.
