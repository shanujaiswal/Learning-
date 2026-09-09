# What Is Git

--> Git is a distributed version control system (DVCS) -- tracks changes to files over time, enables collaboration, and lets you revert to any previous state.
--> Distributed means every clone of a repository is a full copy with complete history -- unlike older centralized systems (SVN), there's no single point of failure and most operations work offline.
--> Created by Linus Torvalds in 2005 for Linux kernel development.

# How Git Stores Data

--> Git stores data as a series of SNAPSHOTS, not file-based diffs -- every commit is a complete picture of all tracked files at that moment.
--> Three core object types (in the .git folder's object database):
--> Blob -- raw content of a single file, identified by the hash of its content (not its filename).
--> Tree -- represents a directory; lists blobs/sub-trees with names and permissions.
--> Commit -- a snapshot pointer: references one tree, plus author/message/timestamp and a pointer to its parent commit(s).
--> Content-addressable storage -- every object is identified by the SHA-1 hash of its own content. Identical content is stored only once; changing one character produces a completely different hash, making history tamper-evident.

# The Three States / Areas

1. Working Directory -- your actual files on disk, where you make edits.
2. Staging Area (Index) -- a holding area for changes you've marked as ready to commit.
3. Repository (.git directory) -- where Git permanently stores committed history as objects.

--> Basic workflow: edit files (Working Directory) -> git add (Staging Area) -> git commit (Repository) -> git push (Remote).

# Installing and Configuring Git

```bash
# Install
sudo apt-get install git      # Ubuntu/Debian
brew install git               # macOS

# Configure identity (required before committing)
git config --global user.name "My Name"
git config --global user.email "email@example.com"
git config --global core.editor "code --wait"  # Set VS Code as default editor
git config --list                               # View all settings
```

# Initializing and Cloning

```bash
git init                                    # Create a new repo in the current folder (one-time per project)
git clone https://github.com/user/repo.git  # Copy an existing remote repo locally
git clone -b branch-name <url>              # Clone a specific branch only
```

--> git init creates a hidden .git folder -- this holds the entire object database and history; deleting it removes all Git tracking (but not the files themselves).

# Checking Status

```bash
git status     # Full status -- untracked, modified, staged files
git status -s  # Short format
```

--> File states: untracked (new, never added), modified (changed since last commit), staged (added, ready to commit), unmodified (matches last commit).
--> Short status flags: `??` untracked, `A` added to stage, `M` modified, `D` deleted.

# Staging and Committing

```bash
git add filename.txt      # Stage a specific file
git add .                  # Stage all changes in current directory and below
git add -A                 # Stage all changes in the entire repo
git add -u                 # Stage only modified/deleted files (not new ones)

git commit -m "Add login feature"       # Commit staged changes with a message
git commit -a -m "Fix bug"               # Stage all tracked modified files AND commit, skipping git add
git commit --amend -m "New message"      # Rewrite the most recent commit's message (or add more changes to it)
```

# Viewing History

```bash
git log                              # Full commit history
git log --oneline                     # Compact, one line per commit
git log --graph --oneline --all       # Visual branch/merge graph
git diff                              # Unstaged changes (working dir vs staging)
git diff --staged                     # Staged changes (staging vs last commit)
git diff branch1 branch2              # Compare two branches
```

# .gitignore

--> Tells Git which files/folders to NEVER track -- they won't appear in git status and can't accidentally be committed, even with `git add .`.

```
node_modules/
dist/
.env
.env.local
*.log
.DS_Store
```

--> Only affects UNTRACKED files -- if a file was already committed before being ignored, remove it from tracking explicitly: `git rm --cached filename`.
--> gitignore.io and GitHub's official gitignore templates provide ready-made files for specific languages/frameworks.

# HEAD and Commit References

--> HEAD is a pointer to whichever commit is currently checked out -- "you are here" in history.
--> Normally HEAD points to a branch name (e.g. main), which itself points to a commit -- HEAD moves automatically as you commit.
--> Detached HEAD -- happens when HEAD points directly to a commit hash instead of a branch (e.g. after checking out an old commit). Commits made here can be lost unless a new branch is created there first (`git checkout -b new-branch`).
--> HEAD~1 -- one commit before HEAD; HEAD~2 -- two commits before, etc. -- used to reference relative history positions.

## Deep Dive -- git stash -- Temporarily Shelving Changes

--> `git stash` saves uncommitted changes (both staged and unstaged) aside, restoring a clean working directory, WITHOUT needing to commit half-finished work just to switch tasks.

```bash
git stash                        # Save current changes, revert working directory to match the last commit
git stash list                    # View all stashed change-sets
git stash pop                      # Reapply the most recent stash AND remove it from the stash list
git stash apply                     # Reapply the most recent stash but KEEP it in the list (for reapplying elsewhere too)
git stash drop                       # Discard a stash without applying it
```

--> The classic use case -- you're mid-way through a feature when an urgent bug fix request comes in -- `git stash` lets you cleanly switch to `main`, fix the bug, then switch back and `git stash pop` to resume exactly where you left off, without a messy half-finished commit cluttering history.

## Deep Dive -- git reflog -- The Safety Net for "I Think I Just Lost My Work"

--> `git reflog` shows a log of every place `HEAD` has pointed to recently -- every commit, checkout, reset, and rebase -- even ones no longer reachable from any branch. This makes it the single most important recovery tool for undoing a mistake that seems to have permanently deleted work.

```bash
git reflog
# a1b2c3d HEAD@{0}: commit: Add feature
# e4f5g6h HEAD@{1}: reset: moving to HEAD~3   <- an accidental hard reset that seemed to lose commits
# i7j8k9l HEAD@{2}: commit: Fix bug

git reset --hard e4f5g6h   # Or checkout the commit hash directly -- recovers the "lost" work,
                              # since it was never actually deleted, just no longer pointed to by any branch
```

--> Git rarely truly deletes anything immediately -- an unreachable commit remains in the object database (referenced in the "How Git Stores Data" section above) until Git's garbage collection eventually cleans it up (typically after 30+ days by default) -- `reflog` is precisely how you find and recover something that still exists but is no longer reachable through normal branch history, turning most "I accidentally deleted my work" panics into a recoverable situation.
