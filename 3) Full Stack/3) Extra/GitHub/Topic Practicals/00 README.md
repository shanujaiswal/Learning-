# GitHub Practical -- Index

--> Companion, hands-on walkthroughs for the three GitHub Theory chapters. These are real, runnable Bash scripts (and one real GitHub Actions workflow) meant to be read top-to-bottom and either executed directly or copy-pasted command-by-command into your own terminal.

## IMPORTANT -- Run These Inside a Scratch Directory

--> These scripts create REAL commits, branches, and merge conflicts. Never run them inside a real project. First create an empty scratch directory:

```bash
mkdir git-practice && cd git-practice
```

Then run each script from inside that folder (or open it and copy commands one at a time).

## Files

1. **`01-core-workflow-walkthrough.sh`** -- Matches Theory chapter 1 (Git Fundamentals and Core Workflow). Initializes a repo, walks through the Working Directory -> Staging Area -> Repository flow, with `git status`/`git diff`/`git log` checked at every step.
2. **`02-branching-merging-conflict-resolution.sh`** -- Matches Theory chapter 2 (Branching, Merging, Collaboration). Creates two branches that edit the same line of the same file, merges one cleanly, then deliberately walks through a real merge conflict on the other -- showing the exact conflict markers and how to resolve them -- plus a `git stash` example mid-workflow.
3. **`03-github-cicd-workflow-example.yml`** -- Matches Theory chapter 3 (GitHub Platform, Collaboration, CI/CD). A real GitHub Actions workflow, as it would live at `.github/workflows/ci.yml`, that installs Node dependencies, runs tests on every push/PR, and deploys only on merge to `main`.
4. **`03-github-cicd-workflow-example.md`** -- Section-by-section explanation of the YAML file above.

## Suggested Order

Run/read `01` first, then `02`. `03`'s `.yml` isn't something you "run" locally -- read it alongside its `.md` companion, and if you want to see it work for real, commit it into an actual GitHub repo at `.github/workflows/ci.yml` and push.
