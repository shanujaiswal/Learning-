# From Mechanics to Strategy -- How Teams Actually Use What Came Before

--> The previous files cover the raw MECHANICS of branching/merging (file 02) and the BASICS of the GitHub platform and CI/CD (file 03). This file covers the higher-level layer built on top of those mechanics -- named, battle-tested STRATEGIES for how a whole team structures its branches over the life of a project, deeper GitHub Actions capabilities that go beyond a single simple workflow, GitHub's built-in security tooling, and how a project actually manages and ships releases.

# Branching Strategies -- Named, Comparable Models

--> File 02 covers HOW to branch and merge; it deliberately doesn't prescribe WHEN a team should branch, or how long a branch should live before merging. Different, well-known strategies answer that differently, each trading off release control against integration speed.

## Git Flow -- Structured, Release-Oriented

--> A strategy built around several long-lived, purpose-specific branches, historically popular for software shipped in discrete, versioned releases (desktop software, mobile apps with app-store release cycles).

```
main        -- always reflects the current PRODUCTION release
develop      -- the integration branch, ahead of main, where features accumulate
feature/*     -- branched from develop, merged back into develop when done
release/*      -- branched from develop when preparing a release, for final
                  stabilization/bug-fixing before it goes out
hotfix/*        -- branched directly from main, for an urgent production fix,
                    merged into BOTH main and develop
```

```bash
git checkout -b feature/checkout-redesign develop   # start a feature off develop
# ... work, commit ...
git checkout develop
git merge --no-ff feature/checkout-redesign          # merge back into develop when done

git checkout -b release/2.4.0 develop                # cut a release branch to stabilize
# ... only bug fixes go here, no new features ...
git checkout main
git merge --no-ff release/2.4.0                       # ship it
git tag v2.4.0
git checkout develop
git merge --no-ff release/2.4.0                        # bring the stabilization fixes back too
```

--> **Genuine advantage** -- a very clear, explicit process for exactly what's in each release, and a dedicated space (the release branch) to stabilize before shipping without blocking new feature work continuing on develop.
--> **The real cost, and why it's fallen out of favor for most web/SaaS teams** -- long-lived branches (`develop` diverging from `main` for weeks) mean big, infrequent, higher-risk merges, and the whole model assumes discrete, versioned releases rather than the continuous deployment covered in the CI/CD file -- a genuine mismatch for a product deployed to production many times a day.

## GitHub Flow -- Simple, Continuous Deployment

--> A dramatically simpler model built around exactly ONE long-lived branch (`main`), with every change made on a short-lived feature branch, merged back via a Pull Request as soon as it's ready.

```
main         -- always deployable; every commit here can go to production
feature/*     -- short-lived, branched from main, merged back via PR once reviewed
                and CI-passing (directly the Branch Protection Rules from file 03),
                then typically deployed immediately
```

```bash
git checkout -b feature/add-search main
# ... work, commit, push, open a PR ...
# CI runs (file 03's GitHub Actions), review happens, PR merges into main
# main's CI/CD pipeline deploys the new main immediately
```

--> **Genuine advantage** -- simple enough to explain in one sentence, and matches how most modern web/SaaS teams actually deploy (continuously, many times a day) far better than Git Flow's release-branch ceremony.
--> **The real cost** -- assumes `main` genuinely stays deployable at all times, which REQUIRES strong CI (automated tests gating every merge, from file 03) and feature flags for anything not fully ready for real users yet -- without both of those in place, GitHub Flow's simplicity turns into "everyone breaks main constantly" instead.

## Trunk-Based Development -- The Extreme of Continuous Integration

--> Pushes GitHub Flow's philosophy further -- developers commit directly to `main` ("trunk") extremely frequently, often multiple times a day, with branches (if used at all) living for HOURS, not days, specifically to minimize how long any code exists un-integrated with everyone else's.

```
main (trunk) -- receives small, frequent commits/merges from every developer,
                multiple times per day; long-lived feature branches are actively
                avoided, since a long-lived branch is exactly what causes large,
                risky, hard-to-review merges later

Incomplete features ship to main behind a FEATURE FLAG:
  if (featureFlags.newCheckoutFlow) {
    renderNewCheckout();
  } else {
    renderOldCheckout();
  }
  -- the flag lets unfinished/risky code sit in main, deployed to production,
     WITHOUT being visible/active to real users until it's explicitly turned on --
     decoupling "merged and deployed" from "live for users" entirely.
```

--> **Genuine advantage** -- the smallest possible integration risk (each individual change is tiny, so conflicts and "merge hell" are rare by construction) and the fastest possible feedback loop -- directly the philosophy behind Continuous Integration as a practice, taken to its logical conclusion.
--> **The real cost** -- requires significant additional discipline and infrastructure (a feature-flagging system, very strong automated test coverage since there's no long-lived branch acting as a safety buffer, and genuine team discipline about only committing small, safe increments) that a smaller or less mature team may not have built yet.

## Choosing Among Them

--> **Git Flow** -- genuinely versioned software with discrete release cycles (desktop/mobile apps, libraries with semantic-versioned releases, referenced in file 02's tagging section). **GitHub Flow** -- most web/SaaS products deploying continuously, wanting simplicity over Git Flow's ceremony. **Trunk-Based Development** -- larger, more mature engineering organizations (Google, Meta-scale) with the CI maturity and feature-flag infrastructure to support extremely frequent direct integration, prioritizing integration speed above all else. The common thread across all three, and the reason none of them are "wrong" -- each is optimizing for a genuinely different constraint (release cadence discipline vs deployment simplicity vs integration speed), not a universal best practice.

# GitHub Actions in Depth

--> File 03 covers a single, self-contained workflow. Real-world Actions usage at any meaningful scale needs several capabilities that one simple `ci.yml` file doesn't demonstrate.

## Reusable Workflows

--> Rather than copy-pasting the same steps (checkout, setup, test, build) into every repository's workflow file, a reusable workflow is defined ONCE and CALLED from other workflows, with inputs, the same DRY discipline that motivates shared functions/libraries anywhere else in software.

```yaml
# .github/workflows/reusable-test.yml -- defines the reusable logic, callable by others
on:
  workflow_call:
    inputs:
      node-version:
        required: true
        type: string

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: { node-version: "${{ inputs.node-version }}" }
      - run: npm ci && npm test
```

```yaml
# .github/workflows/ci.yml -- calls the reusable workflow instead of duplicating its steps
jobs:
  run-tests:
    uses: ./.github/workflows/reusable-test.yml
    with:
      node-version: "20"
```

--> Especially valuable across MULTIPLE repositories in the same organization -- a shared reusable workflow, referenced by its full path (`org/shared-workflows/.github/workflows/reusable-test.yml@main`), lets an entire organization update its CI logic in ONE place and have every consuming repository pick up the change, rather than needing to update dozens of near-identical copy-pasted workflow files individually.

## Self-Hosted Runners

--> By default, a workflow's `runs-on: ubuntu-latest` executes on a GitHub-managed, ephemeral VM. A self-hosted runner instead runs on infrastructure YOU control (an on-prem machine, your own cloud VM/Kubernetes pod).

```yaml
jobs:
  build:
    runs-on: [self-hosted, linux, gpu]   # labels matching a specific registered runner
```

--> **Why reach for this** -- workloads needing specialized hardware GitHub's shared runners don't offer (GPU-based ML training/inference), needing access to internal-network resources a cloud-hosted shared runner can't reach (an on-prem database, an internal artifact registry), or simply needing to avoid GitHub-hosted runners' per-minute billing at very high CI volume. **The real cost** -- YOU now own patching, scaling, and securing that runner infrastructure, and a self-hosted runner executing code from a public repository's PRs is a genuine security risk (an attacker's PR could run arbitrary code on YOUR infrastructure) unless carefully restricted (e.g. requiring maintainer approval before running workflows on external PRs).

## Artifacts

--> A way to pass files BETWEEN jobs within the same workflow run, or to persist a workflow run's output (test reports, build binaries) for later download.

```yaml
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - run: npm run build
      - uses: actions/upload-artifact@v4
        with: { name: dist-files, path: dist/ }

  deploy:
    needs: build
    runs-on: ubuntu-latest
    steps:
      - uses: actions/download-artifact@v4
        with: { name: dist-files, path: dist/ }
      - run: ./deploy.sh
```

--> `needs: build` -- makes the `deploy` job wait for `build` to finish and explicitly depend on its output, since jobs otherwise run in PARALLEL by default -- artifacts are the mechanism that actually carries a built output from one job to a dependent later one, since separate jobs run on separate, independent VMs with no shared filesystem.

## Monorepo CI Patterns

--> A monorepo (multiple, often-independent apps/services in ONE repository) creates a real efficiency problem for CI -- rerunning the ENTIRE test/build suite for every single package on every single commit, even a one-line change to a completely unrelated package, wastes enormous CI time and money at any real scale.

```yaml
# Path filtering -- only run a specific package's workflow when ITS files actually changed
on:
  push:
    paths:
      - "packages/orders-service/**"

jobs:
  test-orders-service:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - run: cd packages/orders-service && npm ci && npm test
```

--> More sophisticated setups use a dedicated tool (Turborepo, Nx) that understands the actual DEPENDENCY GRAPH between packages, and additionally caches previous build/test results per-package -- so a change to package A only reruns A's tests AND any package that actually depends on A, while every genuinely-unaffected package's previous, still-valid result is reused instead of needlessly recomputed.

# GitHub Security Features

--> Beyond code review and branch protection (file 03), GitHub bundles several automated security scanning tools directly into the platform, each addressing a genuinely different category of risk.

## Dependabot -- Dependency Vulnerability Scanning and Updates

--> Continuously scans a repository's dependency manifests (`package.json`, `requirements.txt`, etc.) against known vulnerability databases, and automatically opens a PR bumping a vulnerable dependency to a patched version.

```yaml
# .github/dependabot.yml
version: 2
updates:
  - package-ecosystem: "npm"
    directory: "/"
    schedule: { interval: "weekly" }
```

--> Directly connects to supply-chain security concerns covered in the Cyber Security track -- most real-world breaches exploit a KNOWN, already-patched vulnerability in a dependency nobody got around to updating, not a novel zero-day; Dependabot's whole value is closing that "known fix exists, nobody applied it" gap automatically, continuously, without relying on a human remembering to periodically audit dependencies manually.

## CodeQL -- Static Code Scanning

--> Runs semantic, AST-level static analysis across the codebase's OWN source code (not its dependencies) to find genuine security vulnerabilities directly in first-party code -- SQL injection, hardcoded credentials, unsafe deserialization, and similar patterns covered conceptually in the Cyber Security track's secure-coding material.

```yaml
# .github/workflows/codeql.yml
on: [push, pull_request]
jobs:
  analyze:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: github/codeql-action/init@v3
        with: { languages: javascript }
      - uses: github/codeql-action/analyze@v3
```

--> Findings surface directly as annotations on the specific PR/line that introduced them, and (combined with Branch Protection Rules from file 03) can be configured to BLOCK a PR from merging until a flagged issue is addressed -- catching a vulnerability before it ever reaches `main`, rather than relying on it being caught in a later, separate security review.

## Secret Scanning

--> Scans every push (and, with "push protection" enabled, BLOCKS a push before it even completes) for patterns matching known credential formats -- AWS access keys, API tokens, private keys -- specifically to catch a developer accidentally committing a real secret.

```
Push protection blocks a push containing, e.g.:
  AWS_SECRET_ACCESS_KEY = "AKIAIOSFODNN7EXAMPLE..."

The push is REJECTED before the secret is ever recorded in the repository's
history at all -- a genuinely important distinction from finding it AFTER
the fact, since once a secret is ANYWHERE in git history (even a later-
reverted commit), it must be treated as compromised and rotated -- git
history is never truly "deleted" from anyone who already cloned/fetched it.
```

--> **Why push protection specifically matters over after-the-fact detection** -- a leaked secret detected only AFTER it's pushed still requires immediately rotating that credential everywhere it's used, since the exposure already happened the moment it left your machine; catching it BEFORE the push completes prevents the exposure from ever occurring in the first place.

# Release Management

## GitHub Releases

--> A Release wraps a git tag (file 02's `git tag` command) with human-readable release notes and optional attached binary artifacts, giving both humans and tooling (package managers, auto-updaters) a clear, stable, browsable point to reference.

```bash
gh release create v2.4.0 \
  --title "v2.4.0 -- Search and Checkout Redesign" \
  --notes "Adds full-text search. See CHANGELOG.md for details." \
  dist/app-v2.4.0.tar.gz
```

## Changesets -- Automating Version Bumps and Changelogs

--> Rather than a human manually deciding a version bump and writing a changelog entry at release time, Changesets has each PR author record their OWN intended change (and its semver impact -- from file 02's Semantic Versioning section) as a small file, committed alongside the PR itself.

```bash
npx changeset   # interactively asks: which packages changed, and is this
                # major/minor/patch (directly the semver categories from file 02)
# Writes a small markdown file into .changeset/, committed with the PR
```

```markdown
---
"orders-service": minor
---

Add support for partial refunds on a completed order.
```

--> When it's time to release, a single command consumes every accumulated changeset file, computes the correct combined version bump, generates a changelog automatically from each PR's own recorded description, and publishes -- particularly valuable in a monorepo (connecting back to the monorepo CI section above) with MULTIPLE independently-versioned packages, since each package's changesets are tracked and bumped separately rather than needing one person to remember every package's every change at release time.
