# Explaining `03-github-cicd-workflow-example.yml` Section by Section

--> This walks through the workflow file exactly as it would live at `.github/workflows/ci.yml`, tying each part back to the CI/CD chapter of the Theory.

## File Location and Naming

--> Any `.yml` file placed under `.github/workflows/` in a repository is automatically picked up by GitHub Actions -- no separate registration step. The filename (`ci.yml`) is arbitrary; GitHub discovers workflows by folder location, not name.

## `name: CI/CD`

--> The label shown in the GitHub "Actions" tab for every run of this workflow. Purely cosmetic.

## `on:` -- Triggers

```yaml
on:
  push:
    branches: [main]
  pull_request:
    branches: [main]
```

--> This workflow runs in two situations: (1) any direct push to `main` (which, combined with Branch Protection Rules, usually only happens via a merged PR), and (2) any pull request that TARGETS `main`. This matches the Theory's point that a failing workflow on a PR can block merging when Branch Protection requires status checks to pass.

## `permissions:`

--> Explicitly scopes what the auto-generated `GITHUB_TOKEN` can do during the run. `contents: read` is the minimal safe default for a workflow that only builds/tests -- least-privilege practice, since GitHub Actions can otherwise use a token with broad default permissions.

## Job 1: `build-and-test`

```yaml
jobs:
  build-and-test:
    runs-on: ubuntu-latest
```

--> `runs-on` picks the virtual machine image the job executes on. `jobs` can contain multiple independent jobs -- here just one for CI, plus a second for deployment below.

### `strategy: matrix:`

```yaml
strategy:
  matrix:
    node-version: [18, 20]
```

--> Runs the ENTIRE job twice in parallel, once per Node version listed -- a common real-world pattern to confirm the code works across the Node versions you support, without duplicating all the steps.

### Steps

- **`actions/checkout@v4`** -- The first step in nearly every job; without it, the runner's VM has no copy of your repository at all.
- **`actions/setup-node@v4`** -- Installs the requested Node version onto the runner and enables npm's built-in caching (`cache: "npm"`) so `npm ci` is faster on subsequent runs.
- **`npm ci`** -- Deliberately used instead of `npm install`: it installs EXACTLY what's in `package-lock.json` and fails loudly if the lockfile is out of sync with `package.json` -- the reproducibility CI needs.
- **`npm run lint --if-present`** / **`npm run build --if-present`** -- `--if-present` skips the step gracefully if that script isn't defined in `package.json`, instead of failing the whole job.
- **`npm test`** -- Runs the test suite (e.g. Jest, per the Software Testing Practical folder) -- if any test fails, this step (and therefore the whole job) fails, which is what blocks a PR from merging under Branch Protection.

## Job 2: `deploy`

```yaml
deploy:
  needs: build-and-test
  if: github.ref == 'refs/heads/main' && github.event_name == 'push'
```

--> Two separate gates, both matching the chapter's "only deploys on merge to main" requirement:
- **`needs: build-and-test`** -- this job will not even start unless the `build-and-test` job (across BOTH matrix versions) succeeded first.
- **`if:`** -- even then, only runs when the event that triggered the workflow was a `push` (not a `pull_request`) AND the branch is `refs/heads/main`. A PR run, or a push to any other branch, never reaches this job.

### `environment: production`

--> Ties this job to a GitHub "Environment" (configured in Repository Settings), which can enforce additional protections such as required reviewers before deployment, and scopes environment-specific secrets.

### The deploy step and secrets

```yaml
env:
  DEPLOY_TOKEN: ${{ secrets.DEPLOY_TOKEN }}
```

--> `secrets.DEPLOY_TOKEN` pulls an encrypted value from Repository (or Environment) Settings > Secrets -- it is never written into the workflow file itself and is redacted from logs. The placeholder `run: echo "Deploying..."` stands in for whatever real deploy action or CLI command (`aws s3 sync`, `rsync`, a platform-specific GitHub Action, etc.) an actual project would use.

## Tying Back to the Theory

--> This single file demonstrates: workflow triggers (`on`), jobs and steps, the `actions/checkout` + `actions/setup-node` pattern, dependency installation and test execution as automated CI, and a conditional deployment job as CD -- exactly the progression the CI/CD chapter describes, plus the `needs`/`if`/`environment`/`secrets` mechanics that a bare theory example usually leaves out.
