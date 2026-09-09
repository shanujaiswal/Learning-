## GitHub Overview

==> What is GitHub?
--> Web based hosting service for Git repositories that allow developers to store and manage their code using git.
--> Largest host of source code in the world
--> Acquired by Microsoft in 2018
--> Provides additional collaboration features beyond Git
--> we use to upload folder in git hub which is generally known as repository(repo)
--> changes made in github is called as commit.

==> Key GitHub Features
--> Repository hosting
--> Pull Requests for code review
--> Issues for tracking bugs and tasks
--> Projects for project management
--> Actions for CI/CD (Continuous Integration/Continuous Deployment)
--> Pages for website hosting
--> Discussions for community conversations
--> Security tools (Dependabot, CodeQL, etc.)
--> Extensive API for integrations

==> GitHub Terminology
--> Repository: A project containing your files and revision history
--> Fork: A personal copy of another user's repository
--> Pull Request: Proposed changes to a repository
--> Issue: Suggestions, tasks, or bug reports for a project
--> README: Documentation file that appears on the main page of a repository
--> GitHub Pages: Website hosting directly from a repository

> **Note:** `GitHub\Theory\01 Git Fundamentals and Core Workflow.md`, `02 Branching Merging and Collaboration.md`, and `03 GitHub Platform Collaboration and CI-CD.md` cover this same Git/GitHub ground in a more current, better-organized form. Treat this file (`GitHub_Note.Md`) as the older/duplicate version and prefer the `Theory` folder as the primary reference; this file is kept as-is for reference/history.

## Git(software)

--> Git is a distributed version control system (DVCS).
--> Version control System is a tool that helps to track changes in code.
--> It is popular, free & open source, fast and scalable
--> Track the history , collaborate.Works well for both small and large projects
--> Created by Linus Torvalds (the creator of Linux) in 2005 and maintained by Junio Hamano

# What does Git do?

--> Manage projects with Repositories(Repo)
--> Clone a project to work on a local copy
--> Control and track changes with Staging and Committing
--> Branch and Merge to allow for work on different parts and versions of a project
--> Pull the latest version of the project to a local copy
--> Push local updates to the main project
--> Tracks changes to files over time (version history)
--> Enables collaboration among multiple developers
--> Manages projects with repositories
--> Allows working on different features simultaneously via branches
--> Enables reverting to previous versions when needed
--> Facilitates code review and quality control

# Key Benefits

--> Full history of changes
--> Offline work capability
--> Branching and merging
--> Data integrity (SHA-1 hash)
--> Speed and performance
--> Supports non-linear development

# How Git Actually Stores Data (Theory)

--> Unlike older version control systems that store a list of file-based changes (diffs), Git stores data as a series of SNAPSHOTS of the entire project -- every commit is a complete picture of what all tracked files looked like at that moment, not just what changed.
--> Internally, Git builds this out of three core object types, all stored in the `.git` folder's object database: - Blob (Binary Large Object) -- the raw content of a single file, stored by its content, not its filename. - Tree -- represents a directory; lists blobs (files) and other trees (subdirectories) it contains, plus their names/permissions. - Commit -- a snapshot pointer: references one tree (the project's state), the commit's author/message/timestamp, and a pointer to its parent commit(s).
--> Content-Addressable Storage -- every object (blob/tree/commit) is identified by the SHA-1 hash of its own content, not by a filename or sequential number. This means: - If two files (or two commits) have IDENTICAL content, Git stores that content only ONCE internally, even if referenced from many places -- an automatic space-saving side effect. - Changing even one character anywhere in a file's content produces a completely different hash -- this is what makes Git's history tamper-evident: altering an old commit would change its hash, which would change every commit built on top of it.
--> This is also why Git operations (branching, diffing, checking integrity) are so fast -- most operations are just following pointers between these hashed objects rather than scanning/diffing file-by-file.

# What Is HEAD?

--> HEAD is simply a pointer to whichever commit you currently have checked out -- "you are here" in the repository's history.
--> Normally, HEAD points to a BRANCH NAME (e.g. `main`), and that branch name in turn points to a specific commit -- so HEAD moves automatically whenever you commit on that branch.
--> Detached HEAD state happens when HEAD points directly to a commit hash instead of a branch name (e.g. after `git checkout <commit-hash>`) -- you can look around and even make experimental commits, but they won't belong to any branch and can be lost once you check out something else, unless you first create a branch there (`git checkout -b new-branch-name`, see Troubleshooting below).
--> `HEAD~1` means "the commit one step before HEAD," `HEAD~2` means two steps before, and so on -- this is how commands like `git reset HEAD~1` refer to relative history positions.

# Remote-Tracking Branches (origin/main vs. main)

--> `main` (your LOCAL branch) and `origin/main` (a REMOTE-TRACKING branch) are two separate pointers, even though they usually point to the same commit.
--> `origin/main` is Git's local record of where the `main` branch was on the remote, LAST TIME you communicated with it -- it only updates when you run `git fetch` (or `git pull`, which fetches then merges).
--> This is why `git fetch` is "safe" -- it only updates `origin/main` (your local memory of the remote's state), never touches your actual working `main` branch or working directory until you explicitly `merge`/`rebase`/`pull`.
--> `git status` comparing "your branch is 2 commits ahead/behind origin/main" is comparing your local branch pointer against this remote-tracking pointer.

# Git Workflow Fundamentals

==> The Three States in Git:

1. Working Directory: Your local files where you make changes
2. Staging Area (Index): Intermediate area for changes ready to be committed
3. Repository (.git directory): Where Git stores the metadata and object database

==> Basic Git Workflow:

1. Modify files in the working directory
2. Stage changes using `git add`
3. Commit changes using `git commit`
4. Push changes to remote repository using `git push`

## Getting Started with Git

Installing Git:

==> For Ubuntu/Debian
sudo apt-get install git
==> For macOS (using Homebrew)
brew install git
==> For Windows
Download from https://git-scm.com/download/win

1. Ctrl + ` == Open terminal
2. git --version --> Tells the version
3. git log --> history of commits for the repository
4. git log --oneline --> View compact history
5. git log --graph --oneline --all --> View history with graph visualization

# Configuring Git

1. git config --global user.name "My Name" --> Set username globally
2. git config --global user.email "email@gmail.com" --> Set email globally
3. git config --global core.editor "code --wait" # For VS Code --> Set default editor
4. git config --list --> View all configurations

# SSH Key Setup for GitHub

--> SSH keys let you authenticate with GitHub over the network without typing your username/password (or a token) every time you push/pull.

1. ssh-keygen -t ed25519 -C "your_email@example.com" --> Generate a new SSH key pair (creates a private key and a `.pub` public key file, default location `~/.ssh/id_ed25519`).
2. eval "$(ssh-agent -s)" --> Start the ssh-agent in the background.
3. ssh-add ~/.ssh/id_ed25519 --> Add your private key to the ssh-agent.
4. cat ~/.ssh/id_ed25519.pub --> Print the public key so you can copy it.
5. Add the copied public key on GitHub under Settings -> SSH and GPG keys -> New SSH key.
6. ssh -T git@github.com --> Test the connection; a successful message confirms authentication works.
   --> Once set up, use the SSH remote URL (`git@github.com:username/repo.git`) instead of the HTTPS URL when cloning/adding a remote.

# Git Help

1. git command -help --> See all the available options for the specific command
2. git help --all - See all possible commands --> This will display a very long list of commands

--> If you find yourself stuck in the list view, SHIFT + G to jump the end of the list, then q to exit the view

# Clone

==> Clone -- Cloning a repository on our local machine  
==> git clone <- some link ->

1. git clone https://github.com/username/repo.git --> Clone a repository
2. git clone -b branch-name https://github.com/username/repo.git --> Clone a specific branch
3. git clone https://github.com/username/repo.git my-directory --> Clone to a specific directory

# Status

==> status - displays the state of the code
--> git status --> Check the current status

1. untracked
   new files that git doesnt yet tracked
2. modified
   changed
3. staged
   files is ready to be committed
4. unmodified
   unchanged

--> git status -s --> View short status

Short status flags are:
?? - Untracked files
A - Files added to stage
M - Modified files
D - Deleted files

# .gitignore

--> A `.gitignore` file tells Git which files/folders to NEVER track (e.g. dependencies, build output, secrets, OS/editor clutter) -- they won't show up in `git status`, can't be accidentally committed, and are excluded even from `git add .`.
--> Create it as a plain text file named `.gitignore` in the repo root, one pattern per line.

```
# Dependencies
node_modules/

# Build output
dist/
build/

# Environment/secrets
.env
.env.local

# OS/editor files
.DS_Store
.vscode/

# Logs
*.log
```

--> Patterns support wildcards (`*.log` ignores all `.log` files), directory matches (`node_modules/` ignores the whole folder), and negation (`!important.log` un-ignores a specific file that would otherwise match a broader pattern).
--> `.gitignore` only affects UNTRACKED files -- if a file was already committed before being added to `.gitignore`, it must be removed from tracking explicitly: `git rm --cached filename`.
--> gitignore.io / GitHub's `gitignore` template repo provide ready-made `.gitignore` files for specific languages/frameworks (Node, Python, React, etc.).

# Creating Git Folder

1. mkdir "FileName" --> mkdir makes a new directory.
2. cd "FileName" --> cd changes the current working directory. or
3. cd /path/to/directory --> Go to a specific directory using its full path
4. cd .. --> Go up one directory
5. cd ../.. --> Go back 2 directory
6. cd - --> Go to the previous directory (where you were before the last cd command):
7. cd or cd ~ --> Go to your home directory
8. ls --> to list the contents of a directory
9. ls -la --> to list all the hidden folder in directory
10. pwd --> Print current Working Directory.

# init

init - used to create a new git repo

1. git init --> Initialize a new local Git repository && it is one time per project.
2. git remote add origin <link> --> Add a remote repository named 'origin'
3. git remote -v --> Verify the remote repository URLs
4. git push origin main --> Push 'main' branch to remote 'origin'
5. git push -u origin main --> Push and set 'main' to track 'origin/main'

--> git init creates a .git folder
--> .git --> a hidden folder to keep history of all files & sub-folder

## Tracking Files:

Working Dire --> **git add** --> Staging Area --> **git commit** --> Repo --> ** Git Push** --> Git Hub

# Add

==> add - adds new or changed files in working directory to the git staging area

1. git add filename.txt --> Add a specific file to staging (works identically on Mac/Linux/Windows)
2. git add . --> Add all changed/new files in the current directory and below (works identically on Mac/Linux/Windows)
3. git add -A --> The shorthand command for git add --all is
4. git add file1.txt file2.txt --> Add multiple files
6. git add -u --> Add all modified and deleted files (not new ones)

# Commit

==> commit - it is the record of changes or a checkpoint

1. git commit -m "A Meaningful message to be given" --> Commit with a message
2. git commit -a -m "Fix bug in feature Y" --> Commit all modified tracked files directly (skips staging)

# Commit Message Conventions (Conventional Commits)

--> Conventional Commits is a widely-used standard for structuring commit messages so they're consistent and can be parsed by tooling (e.g. to auto-generate changelogs or determine version bumps).
--> Format: `type(optional-scope): short description`

--> Common types: - `feat` -- a new feature (e.g. `feat(auth): add login with Google`) - `fix` -- a bug fix (e.g. `fix(cart): correct total price calculation`) - `docs` -- documentation only changes - `style` -- formatting changes that don't affect code behavior (whitespace, semicolons) - `refactor` -- code change that neither fixes a bug nor adds a feature - `test` -- adding or correcting tests - `chore` -- maintenance tasks (updating dependencies, build config)

--> Example commit history using this convention:

```
feat(navbar): add responsive mobile menu
fix(api): handle null response from user endpoint
chore: upgrade react to v19
```

--> Benefit: anyone scanning `git log` can immediately tell what KIND of change each commit represents, without opening the diff.

# Branch Management

1. git branch --> List all local branches
2. git branch -M new-name --> Rename current branch to 'main'
3. git checkout <-branch name-> --> to navigate (or)
4. git switch branch-name
5. git branch feature-name --> Create a new branch but not switch to it — you'll still be on your current branch.
6. git checkout -b <-new branch name-> --> Create and switch to a new branch (or)
7. git switch -c <-feature-name-> --> Modern way to create and switch to a new branch (Git 2.23+)
8. git branch -d <-branch name> --> to delete branch
9. git branch -a --> List all branches including remote
10. git branch -D branch-name --> Force delete a branch (even if not merged)

# Fetching

1. git fetch origin --> Fetch updates from remote without merging
2. git fetch --all --> Fetch updates from all remotes

# Pulling

1. git pull origin main --> Pull updates from remote (fetch + merge)
2. git pull --rebase origin main --> Pull with rebase instead of merge

# Push command

==> push --> upload local repo content to remote repo

1. git push origin main --> Push changes to remote
2. git push -u origin main --> Push and set upstream (first push)
3. git push --force origin main --> Force push (use with caution)
4. git push --all origin --> Push all branches
5. git push --tags origin --> Push tags

# Pull Requests (PR)

- Created via GitHub interface or GitHub CLI
- Allow code review before merging
- Can be linked to issues
- Support comments, code suggestions, and approvals
- Can have required status checks (CI/CD, code review)

# Diff (Comparing Changes)

--> `git diff` shows exactly which lines changed, added (`+`), or removed (`-`) between two states -- the most direct way to review what you're about to commit/merge before doing it.

1. git diff --> Shows unstaged changes (working directory vs. staging area).
2. git diff --staged (or `--cached`) --> Shows staged changes (staging area vs. last commit) -- what would actually be committed.
3. git diff HEAD --> Shows all changes (staged + unstaged) since the last commit.
4. git diff branch1 branch2 --> Compares two branches.
5. git diff commit1 commit2 --> Compares two specific commits.
6. git diff -- filename.txt --> Limits the diff to a single file.
7. git diff --stat --> Shows a summary (files changed, lines added/removed) instead of the full line-by-line diff.

# Merging Code

==> Way 1
git diff <-branch name> --> to compare commits,branches,files and more
git merge <-branch name> --> to merge 2 branches

==> Way 2
Create a PR(Pull Request)
PR -- It lets you tells others about changes you've pushed to a branch in a repository on Github

git merge --no-ff feature-branch --> Merge with no fast-forward (creates a merge commit)

# Fast-Forward vs. Three-Way Merge (Theory)

--> Fast-Forward Merge -- happens when the branch being merged in is simply AHEAD of the current branch, with no new commits on the current branch since they diverged. Git just moves the current branch's pointer forward to match -- no new merge commit is created, and history stays perfectly linear.
--> Three-Way Merge -- happens when BOTH branches have new commits since they diverged (the common case in real collaboration). Git looks at three points -- the tip of each branch, plus their common ancestor commit -- and combines the changes from both into a new merge commit with TWO parents.
--> `git merge --no-ff` forces a merge commit to be created even when a fast-forward would have been possible -- some teams prefer this so the history clearly shows "a feature branch was merged here," instead of the commits blending in invisibly with a fast-forward.
--> This is exactly what the "Merge Commit" vs "Squash and Merge" vs "Rebase and Merge" options on a GitHub PR (covered further below) are choosing between.

# Resolving Merge Conflicts

==> An event that takes places when Git is unable to automatically resolve differences in code between two commits
==> Handling Merge Conflicts:

1. Git will mark files with conflicts
2. Open the files and look for conflict markers (`<<<<<<<`, `=======`, `>>>>>>>`)
3. Edit files to resolve conflicts
4. Add resolved files with `git add`
5. Complete the merge with `git commit`

# Rebasing

1. git rebase main --> Rebase current branch onto another branch
2. git rebase -i HEAD~3 --> Rebase last 3 commits Interactive rebase for editing commits

# Merge vs. Rebase -- Which One to Use (Theory)

--> Both merge and rebase integrate changes from one branch into another, but they produce very different history: - Merge preserves exactly what happened -- both branches' commits stay as they were, joined by a new merge commit. History is truthful but can look messy/non-linear with lots of parallel work. - Rebase REWRITES history -- it takes your branch's commits and replays them one-by-one on top of the latest version of the target branch, as if you'd started your work from there all along. Result: a clean, linear history with no merge commits, but the original commits are technically discarded and replaced with new ones (different hashes).
--> The Golden Rule of Rebasing -- NEVER rebase commits that have already been pushed/shared with others (e.g. a shared `main`/`develop` branch). Since rebase creates brand-new commits with different hashes, anyone else who already has the old commits will have a diverged, conflicting history -- rebase is safe only for LOCAL, not-yet-shared commits (typically your own feature branch before opening a PR).
--> Common practice: rebase your own feature branch onto `main` to keep it up to date and avoid an unnecessary merge commit later, but use merge (or a PR's merge button) once the branch is ready to be integrated into the shared branch.

# Undoing changes

1. Staged changes
   git reset <-file name >
   git reset
2. commited changes (for one commits)
   git reset HEAD~1
3. commited changes (for many commits)
   git reset <- commit hash ->
   git reset --hard <- commit hash ->

# Undoing Changes

1. git restore --staged filename.txt --> Unstage a file
2. git reset HEAD filename.txt --> or (older syntax)
3. git restore filename.txt --> Discard changes in working directory
4. git checkout -- filename.txt --> or (older syntax)

5. git reset --soft HEAD~1 --> Undo last commit (keeping changes staged)
6. git reset HEAD~1 --> Undo last commit (unstaged changes)
7. git reset --hard HEAD~1 --> Undo last commit (discard changes)
8. git reset --hard commit-hash --> Undo to a specific commit
9. git revert HEAD --> Create a new commit that undoes previous commit
10. git revert commit-hash

# reset --soft vs --mixed vs --hard (Theory)

--> All three variants move your current branch pointer to an earlier commit, but differ in what happens to the Staging Area and Working Directory -- this is the actual distinction behind the commands listed above: - `--soft` -- moves the branch pointer only. Your staging area AND working directory are untouched, so all the "undone" commit's changes reappear as already-staged changes, ready to be re-committed differently. - `--mixed` (the default if no flag is given, e.g. plain `git reset HEAD~1`) -- moves the branch pointer AND resets the staging area to match, but leaves the working directory untouched. Changes reappear as unstaged modifications. - `--hard` -- moves the branch pointer, resets the staging area, AND overwrites the working directory to match. All changes from the undone commit(s) are completely discarded -- this is the only variant that can permanently lose work if used carelessly.
--> `git revert` is fundamentally different from all three `reset` variants -- instead of moving the branch pointer backward, it creates a brand NEW commit that applies the inverse of a previous commit's changes. This is why `revert` is the safe choice for shared/pushed history (it doesn't rewrite anything), while `reset` is only safe for local, not-yet-pushed commits (same Golden Rule as rebasing above).

**Interactive Rebase (History Editing):**

# Rebase with interactive mode

git rebase -i HEAD~5 --> Edit last 5 commits

# Available operations in interactive rebase:

1. p, pick = use commit
2. r, reword = use commit, but edit the commit message
3. e, edit = use commit, but stop for amending
4. s, squash = use commit, but meld into previous commit
5. f, fixup = like "squash", but discard this commit's log message
6. d, drop = remove commit

## Remote Repository Operations

# Working with Remotes:

1. git remote -v --> List remote repositories
2. git remote add origin https://github.com/username/repo.git --> Add a remote repository
3. git remote set-url origin https://github.com/username/new-repo.git --> Change remote URL
4. git remote remove origin --> Remove a remote

## Collaboration and Code Review

# Fork

==> A fork is a new repository that shares code and visibility setting with the original "upstream" repository
==> Fork is a rough copy
==> Forking Workflow

1. Fork a repository on GitHub
2. Clone your fork to local machine
3. Add original repository as "upstream" remote
4. Create a feature branch
5. Make changes and push to your fork
6. Create a Pull Request to the original repository

# Setting Up Upstream

1. git remote add upstream https://github.com/original-owner/repo.git --> Add the original repository as "upstream"
2. git fetch upstream --> Retrieves the latest changes from the upstream (original) repository without merging them into your local branches.
3. git checkout main --> Switch to your local main branch
4. git merge upstream/main --> Sync your main branch with upstream

# Code Review Best Practices

--> Keep changes small and focused
--> Write clear PR descriptions
--> Respond to review feedback
--> Request reviews from relevant team members
--> Use GitHub's code suggestion feature

# Merge Strategies for Pull Requests (GitHub)

--> When merging a PR on GitHub, there are three options, each producing a different commit history:

1. Merge Commit -- keeps all individual commits from the feature branch, plus adds one new merge commit tying them together. Preserves full history but can clutter the log with many small WIP commits.
2. Squash and Merge -- combines ALL commits from the PR into a single new commit on the target branch. Keeps the main branch history clean/linear, at the cost of losing the feature branch's individual commit-by-commit history.
3. Rebase and Merge -- replays each commit from the feature branch individually onto the target branch (no merge commit at all). Keeps a linear history while preserving individual commits.
   --> Squash and Merge is the most common default for teams that want a clean `main` branch history, especially when feature branches have messy "WIP"/"fix typo" commits.

# Branch Protection Rules (GitHub)

--> Repository settings that enforce quality gates before code can reach an important branch (typically `main`/`production`).
--> Common rules: require a pull request before merging (no direct pushes), require a minimum number of approving reviews, require status checks (CI tests/linting) to pass, require branches to be up to date before merging, restrict who can push directly.
--> Set under Repository Settings -> Branches -> Branch protection rules -- a standard practice on any team repository to prevent accidental direct pushes or unreviewed code reaching production.

## Advanced Git Techniques

# Stashing Changes

1. git stash --> Stash current changes
2. git stash save "Work in progress on feature X" --> Stash with a message
3. git stash list --> List all stashes
4. git stash apply --> Apply most recent stash without removing it
5. git stash apply stash@{2} --> Apply specific stash
6. git stash pop --> Apply and remove most recent stash
7. git stash drop stash@{1} --> Remove specific stash
8. git stash clear --> Clear all stashes

# Tagging

1. git tag --> List all tags
2. git tag v1.0.0 --> Create a lightweight tag
3. git tag -a v1.0.0 -m "Release version 1.0.0" --> Create an annotated tag
4. git tag -a v1.0.0 -m "Release version 1.0.0" 9fceb02 --> Tag a specific commit
5. git push origin --tags --> Push tags to remote
6. git tag -d v1.0.0 --> Delete a tag
7. git push origin --delete v1.0.0 --> Delete from remote

# Semantic Versioning (SemVer) -- What Tags Like v1.0.0 Actually Mean

--> Semantic Versioning is the near-universal convention for version numbers, formatted as `MAJOR.MINOR.PATCH` (e.g. `2.4.1`), where each part signals a specific kind of change: - MAJOR -- incremented for breaking/incompatible changes (code using the old version may need updates to work with the new one). - MINOR -- incremented for new, backward-compatible functionality (existing code keeps working, new features are simply available). - PATCH -- incremented for backward-compatible bug fixes only, no new functionality.
--> Example progression: `1.0.0` (initial release) -> `1.1.0` (new feature added) -> `1.1.1` (bug fix) -> `2.0.0` (breaking change).
--> This is why `npm install`/`package.json` version ranges (`^1.2.0`, `~1.2.0`) work the way they do -- they trust that MINOR/PATCH bumps won't break your code, but treat a MAJOR bump as something that needs manual review before upgrading.
--> Pairing this with Git tags (`git tag -a v1.2.0 -m "..."`) gives every release a permanent, referenceable point in history matching its published version number.

# Cherry-Picking

1. git cherry-pick commit-hash --> Apply a specific commit from another branch
2. git cherry-pick commit1 commit2 commit3 --> Cherry-pick multiple commits
3. git cherry-pick -n commit-hash --> Cherry-pick without committing

# git blame

--> Shows who last modified each line of a file, and in which commit -- useful for understanding WHY a piece of code exists or tracking down who to ask about it.

1. git blame filename.txt --> Show line-by-line authorship for the whole file.
2. git blame -L 10,20 filename.txt --> Only show blame for lines 10-20.
3. git blame -w filename.txt --> Ignore whitespace-only changes when attributing lines (avoids blaming a reformatting commit for logic that didn't actually change).

# git bisect (Finding the Commit That Introduced a Bug)

--> A binary-search tool that helps find EXACTLY which commit introduced a bug, by repeatedly checking out commits between a known-good and known-bad point and asking you to test each one.

1. git bisect start --> Begin a bisect session.
2. git bisect bad --> Mark the current commit as broken (usually HEAD).
3. git bisect good commit-hash --> Mark a known working commit further back in history.
4. Git checks out a commit halfway between good and bad -- test it, then run `git bisect good` or `git bisect bad` depending on the result.
5. Repeat step 4 until Git identifies the exact first bad commit.
6. git bisect reset --> End the session and return to the original branch/commit.
   --> Much faster than manually checking out commits one by one, especially across a long history -- bisecting 1000 commits takes only ~10 steps (log2 1000).

# Submodules

--> A submodule lets you include another Git repository as a subdirectory inside your own repository, keeping it as a separate repo with its own history (e.g. a shared component library used by multiple projects).

1. git submodule add https://github.com/username/other-repo.git path/to/folder --> Add a submodule.
2. git clone --recurse-submodules <repo-url> --> Clone a repo AND automatically fetch its submodules' content too.
3. git submodule update --init --recursive --> Fetch submodule content after a normal clone (if `--recurse-submodules` wasn't used).
4. git submodule update --remote --> Pull the latest changes from the submodule's own remote.
   --> Submodules pin to a SPECIFIC commit of the nested repo (not just "the latest") -- the parent repo tracks which exact commit of the submodule it depends on, similar to a locked dependency version.

# Git Aliases

--> Shortcuts for frequently-used (often long) Git commands, configured once and reused forever.

```
git config --global alias.st status
git config --global alias.co checkout
git config --global alias.br branch
git config --global alias.cm "commit -m"
git config --global alias.lg "log --oneline --graph --all"
```

--> After setting these, `git st` works exactly like `git status`, `git lg` works like the full graph log command, etc. -- saves significant typing for commands used many times a day.

## Troubleshooting

Common Issues and Solutions

1. Merge Conflicts
   git merge --abort --> Abort a merge with conflicts
   git mergetool --> Use merge tool to resolve conflicts

2. Detached HEAD State
   git checkout -b new-branch-name --> Create a branch from detached HEAD
   git checkout main --> Return to main branch

3. Accidentally Committed to Wrong Branch
   git branch new-branch --> Create a new branch with current changes
   git reset --hard origin/main --> Reset current branch to before your changes
   git checkout new-branch --> Switch to the new branch

4. Fix Last Commit Message
   git commit --amend -m "New commit message"

5. Recover Deleted Branch
   git reflog --> Find the SHA of the deleted branch's tip
   git checkout -b recovered-branch commit-hash --> Create a new branch at that commit

6. Clean Working Directory
   git clean -f --> Remove untracked files
   git clean -fd --> Remove untracked files and directories
   git clean -n --> Preview what will be removed

## Git Best Practices

==> Commit Practices
--> Write clear, concise commit messages
--> Use present tense in commit messages ("Add feature" not "Added feature")
--> Keep commits focused on a single logical change
--> Commit often, perfect later, publish once

==> Branching Strategy
--> Use meaningful branch names (e.g., `feature/user-authentication`, `fix/login-bug`)
--> Follow a branching model like GitFlow or GitHub Flow
--> Keep branches short-lived
--> Regularly sync with the main branch

==> Workflow Tips
--> Pull before pushing to avoid conflicts
--> Use `.gitignore` to exclude unnecessary files
--> Review changes before committing with `git diff`
--> Use descriptive commit messages
--> Set up SSH keys for secure authentication

==> Git Hooks
--> Pre-commit hooks for linting and testing
--> Post-receive hooks for deployment
--> Commit-msg hooks for enforcing commit message format

## GitHub Actions (CI/CD Basics)

--> GitHub Actions runs automated workflows (tests, linting, builds, deployments) directly inside GitHub, triggered by repository events (push, pull request, schedule, manual trigger).
--> A workflow is defined in a YAML file placed at `.github/workflows/*.yml` in the repo.

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
      - uses: actions/checkout@v4 # Check out the repo's code
      - uses: actions/setup-node@v4
        with:
          node-version: 20
      - run: npm install
      - run: npm test
      - run: npm run build
```

--> `on` -- defines what triggers the workflow (here: every push or PR targeting `main`).
--> `jobs` -- one or more sets of steps that run (optionally in parallel, on separate virtual machines).
--> `steps` -- individual commands or reusable `uses:` actions (pre-built steps shared via the GitHub Actions marketplace, e.g. `actions/checkout`, `actions/setup-node`).
--> Combined with Branch Protection Rules (above), a failing workflow can block a PR from being merged until it passes -- the core of a CI (Continuous Integration) pipeline.

## GitHub CLI (gh)

--> The `gh` command-line tool lets you interact with GitHub (issues, PRs, repos) directly from the terminal instead of the web UI.

1. gh auth login --> Authenticate the CLI with your GitHub account.
2. gh repo clone username/repo --> Clone a repository.
3. gh pr create --title "Add login feature" --body "Implements OAuth login" --> Create a pull request without leaving the terminal.
4. gh pr list --> List open pull requests.
5. gh pr checkout 42 --> Check out PR #42 locally to review/test it.
6. gh issue create --title "Bug: button not clickable" --> Create a new issue.
7. gh repo view --web --> Open the current repository in the browser.
