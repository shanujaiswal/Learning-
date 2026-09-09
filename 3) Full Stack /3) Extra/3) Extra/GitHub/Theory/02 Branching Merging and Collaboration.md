# Branch Management

```bash
git branch                          # List local branches
git branch feature-name              # Create a branch (doesn't switch to it)
git checkout -b feature-name         # Create AND switch to a new branch
git switch -c feature-name           # Modern equivalent (Git 2.23+)
git switch branch-name               # Switch to an existing branch
git branch -d branch-name            # Delete a branch (only if merged)
git branch -D branch-name            # Force delete (even if unmerged)
git branch -a                        # List all branches, including remote
git branch -M new-name               # Rename the current branch
```

# Fetching and Pulling

```bash
git fetch origin        # Download remote changes WITHOUT merging -- safe, only updates origin/main
git fetch --all         # Fetch from all remotes
git pull origin main    # Fetch + merge in one step
git pull --rebase origin main  # Fetch + rebase instead of merge
```

--> `main` (your local branch) and `origin/main` (remote-tracking branch) are separate pointers -- origin/main only updates on fetch/pull, which is why fetch alone is always safe (never touches your working directory).

# Fast-Forward vs Three-Way Merge

--> Fast-Forward Merge -- happens when the branch being merged in is simply ahead, with no divergent commits on the current branch. Git just moves the branch pointer forward -- no merge commit, linear history.
--> Three-Way Merge -- happens when both branches have new commits since diverging. Git looks at both tips plus their common ancestor and creates a new merge commit with two parents.

```bash
git merge feature-branch            # Merge (fast-forwards if possible)
git merge --no-ff feature-branch    # Forces a merge commit even if fast-forward was possible -- keeps feature history visible
```

# Resolving Merge Conflicts

--> A conflict occurs when Git can't automatically reconcile changes to the same lines in both branches.

```bash
# After a conflicting merge:
# 1. Git marks conflicted files
# 2. Open files, look for conflict markers:
<<<<<<< HEAD
your current branch's version
=======
the incoming branch's version
>>>>>>> feature-branch
# 3. Edit to resolve, then:
git add resolved-file.txt
git commit
```

```bash
git merge --abort     # Cancel a conflicting merge and return to the pre-merge state
git mergetool         # Launch a visual merge tool
```

# Rebasing

```bash
git rebase main                  # Replay current branch's commits on top of main
git rebase -i HEAD~3              # Interactive rebase -- edit last 3 commits
```

--> Merge PRESERVES history exactly as it happened (both branches' commits stay, joined by a merge commit) -- truthful but can look messy.
--> Rebase REWRITES history -- replays your commits on top of the latest target branch, producing a clean linear history, but the original commits are replaced with new ones (different hashes).
--> The Golden Rule of Rebasing -- NEVER rebase commits already pushed/shared with others. Rebase is safe only for local, not-yet-shared commits (typically your own feature branch before opening a PR).

--> Interactive rebase operations: `pick` (keep), `reword` (edit message), `edit` (pause to amend), `squash` (combine into previous commit), `fixup` (like squash, discard message), `drop` (remove commit).

# Undoing Changes

```bash
git restore --staged file.txt    # Unstage a file
git restore file.txt              # Discard working directory changes
git reset --soft HEAD~1           # Undo last commit, keep changes staged
git reset HEAD~1                  # Undo last commit, changes become unstaged (--mixed, the default)
git reset --hard HEAD~1           # Undo last commit, DISCARD all changes -- only safe variant for local, unpushed commits
git revert HEAD                   # Create a NEW commit that undoes a previous commit -- safe for shared/pushed history
```

--> reset moves the branch pointer backward (rewriting history) -- only safe on local, unpushed commits. revert adds a new commit instead, so it never rewrites history -- the correct choice once commits are shared.

# Stashing

```bash
git stash                        # Temporarily shelve uncommitted changes
git stash list                    # View all stashes
git stash pop                     # Reapply and remove the most recent stash
git stash apply                   # Reapply without removing
git stash drop                    # Discard a stash
```

--> Useful when you need to quickly switch branches without committing half-finished work.

# Collaboration -- Forking Workflow

1. Fork the repository on GitHub (creates your own copy).
2. Clone your fork locally.
3. Add the original repo as "upstream": `git remote add upstream <original-url>`.
4. Create a feature branch, make changes, push to YOUR fork.
5. Open a Pull Request from your fork to the original repository.

```bash
git remote add upstream https://github.com/original-owner/repo.git
git fetch upstream
git checkout main
git merge upstream/main   # Sync your main branch with the original repo
```

# Cherry-Picking and Tags

```bash
git cherry-pick <commit-hash>   # Apply one specific commit from another branch
git tag v1.0.0                   # Lightweight tag
git tag -a v1.0.0 -m "Release"    # Annotated tag (recommended -- stores author/message/date)
git push origin --tags            # Push tags to remote
```

--> Semantic Versioning (MAJOR.MINOR.PATCH) -- MAJOR for breaking changes, MINOR for new backward-compatible features, PATCH for bug fixes. Git tags commonly mark these release points.
