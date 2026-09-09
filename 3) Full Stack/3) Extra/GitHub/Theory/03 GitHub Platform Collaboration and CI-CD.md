# What Is GitHub

--> GitHub is a web-based hosting service for Git repositories -- adds collaboration features on top of raw Git (pull requests, issues, project boards, Actions, Pages).
--> Largest source code host in the world; acquired by Microsoft in 2018.

# Key GitHub Terminology

--> Repository -- a project's files and full revision history.
--> Fork -- your personal copy of someone else's repository.
--> Pull Request (PR) -- proposed changes from one branch/fork, opened for review before merging.
--> Issue -- a tracked task, bug report, or feature request.
--> README -- the documentation file shown on a repo's main page.

# Pull Requests and Code Review

--> A PR lets others review changes before they're merged into the main codebase -- supports inline comments, suggested edits, and approvals.
--> Good PR practices: keep changes small and focused, write clear descriptions of WHAT changed and WHY, respond promptly to review feedback.

# Merge Strategies for Pull Requests

--> Merge Commit -- keeps all individual commits from the feature branch, plus a new merge commit tying them together. Preserves full history but can clutter the log.
--> Squash and Merge -- combines ALL commits from the PR into ONE commit on the target branch. Keeps main branch history clean, at the cost of losing individual commit granularity. Most common default for teams wanting a tidy main branch.
--> Rebase and Merge -- replays each commit individually onto the target branch, no merge commit at all -- linear history while preserving individual commits.

# Branch Protection Rules

--> Repository settings that enforce quality gates before code reaches an important branch (typically main/production).
--> Common rules: require a PR before merging (no direct pushes), require a minimum number of approvals, require status checks (CI/tests) to pass, require branches to be up to date before merging.
--> Set under Repository Settings -> Branches -> Branch protection rules.

# Commit Message Conventions (Conventional Commits)

--> A standard format: `type(optional-scope): short description` -- lets tooling auto-generate changelogs or determine version bumps.
--> Common types: `feat` (new feature), `fix` (bug fix), `docs` (documentation only), `style` (formatting, no logic change), `refactor` (neither fix nor feature), `test` (adding/fixing tests), `chore` (maintenance, dependency updates).

```
feat(auth): add login with Google
fix(cart): correct total price calculation
chore: upgrade react to v19
```

# GitHub Actions -- CI/CD Basics

--> GitHub Actions runs automated workflows (tests, linting, builds, deployments) triggered by repository events (push, PR, schedule, manual trigger).
--> A workflow is a YAML file at `.github/workflows/*.yml`.

```yaml
name: CI

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  build-and-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4       # Check out the repo's code
      - uses: actions/setup-node@v4
        with:
          node-version: 20
      - run: npm install
      - run: npm test
      - run: npm run build
```

--> `on` -- what triggers the workflow. `jobs` -- sets of steps (can run in parallel across VMs). `steps` -- individual commands or reusable `uses:` actions from the marketplace.
--> Combined with Branch Protection Rules, a failing workflow can block a PR from merging -- the core of Continuous Integration.

# GitHub CLI (gh)

--> Interact with GitHub directly from the terminal instead of the web UI.

```bash
gh auth login                                            # Authenticate
gh repo clone username/repo                               # Clone a repo
gh pr create --title "Add login" --body "Implements OAuth" # Create a PR
gh pr list                                                  # List open PRs
gh pr checkout 42                                            # Check out PR #42 locally
gh issue create --title "Bug: button not clickable"          # Create an issue
```

# SSH Key Setup

--> SSH keys let you authenticate with GitHub over the network without typing credentials every push/pull.

```bash
ssh-keygen -t ed25519 -C "your_email@example.com"  # Generate key pair
eval "$(ssh-agent -s)"                               # Start ssh-agent
ssh-add ~/.ssh/id_ed25519                             # Add key to agent
cat ~/.ssh/id_ed25519.pub                              # Copy public key -- add it on GitHub under Settings > SSH keys
ssh -T git@github.com                                   # Test the connection
```

--> Once set up, use the SSH remote URL (`git@github.com:username/repo.git`) instead of HTTPS.

# Common Troubleshooting

```bash
git merge --abort                        # Abort a conflicting merge
git reflog                                # Find lost commits (e.g. after a bad reset) by their SHA
git checkout -b recovered-branch <hash>   # Recover a deleted branch from its last known commit
git clean -fd                              # Remove untracked files and directories
git clean -n                               # Preview what git clean would remove, without deleting
```

--> Detached HEAD recovery -- `git checkout -b new-branch-name` immediately creates a real branch from your current position, preventing those commits from being lost when you switch away.
