#!/usr/bin/env bash
# =============================================================================
# 01 - CORE WORKFLOW WALKTHROUGH
# Companion to: "01 Git Fundamentals and Core Workflow.md"
#
# Demonstrates the Working Directory -> Staging Area -> Repository flow.
# Run this INSIDE an empty scratch directory:
#   mkdir git-practice && cd git-practice
#   bash /path/to/01-core-workflow-walkthrough.sh
# Or open this file and copy/paste each block into your terminal one at a
# time, reading the "echo" explanations as you go.
# =============================================================================

set -e   # stop the script if any command fails, so you notice mistakes

echo "=============================================================="
echo "STEP 1: Configure identity (required before Git lets you commit)"
echo "=============================================================="
git config --global user.name "My Name" || true
git config --global user.email "email@example.com" || true
# ^ These are --global, so if you've already set them on this machine
#   these two lines are no-ops. Safe to run.

echo
echo "=============================================================="
echo "STEP 2: git init -- create a brand-new repository"
echo "=============================================================="
echo "This creates a hidden .git folder containing an (currently empty)"
echo "object database. Nothing is tracked yet."
git init

git status
echo "-> Notice: 'On branch main' / 'No commits yet' / nothing to commit."
echo "   The repo exists, but there is no history yet."

echo
echo "=============================================================="
echo "STEP 3: Working Directory -- create a file Git doesn't know about yet"
echo "=============================================================="
echo "# My Project" > README.md
echo "This is a NEW file. Git sees it exists on disk but has never tracked it."

git status
echo "-> Notice: README.md appears under 'Untracked files' (flag '??' in -s form):"
git status -s

echo
echo "=============================================================="
echo "STEP 4: Staging Area -- 'git add' promotes the file to staged"
echo "=============================================================="
echo "git add tells Git: 'I intend to include this file's CURRENT content"
echo "in my next commit.' It does NOT create a commit yet."
git add README.md

git status
echo "-> Notice: README.md now appears under 'Changes to be committed' ('A' -- added)."
git status -s

echo
echo "=============================================================="
echo "STEP 5: git diff --staged -- see exactly what will be committed"
echo "=============================================================="
echo "Plain 'git diff' would show nothing here (no unstaged changes)."
echo "'git diff --staged' compares the STAGING AREA against the last commit:"
git diff --staged

echo
echo "=============================================================="
echo "STEP 6: Repository -- git commit permanently records the snapshot"
echo "=============================================================="
git commit -m "docs: add initial README"

echo
git log --oneline
echo "-> Notice: a single commit now exists in the repository's history."

echo
echo "=============================================================="
echo "STEP 7: Modify a TRACKED file -- see the 'modified' state"
echo "=============================================================="
echo "" >> README.md
echo "## Section Two" >> README.md

git status
echo "-> Notice: README.md is now 'modified', not 'untracked' -- Git already"
echo "   knows this file; it's comparing the working directory against the"
echo "   last commit and sees a difference."
git status -s
echo "-> Short flag 'M' = modified."

echo
echo "=============================================================="
echo "STEP 8: git diff (unstaged) -- working directory vs staging area"
echo "=============================================================="
echo "Since nothing has been staged yet, staging area == last commit,"
echo "so this diff effectively shows working-directory changes vs last commit:"
git diff

echo
echo "=============================================================="
echo "STEP 9: Add a second, brand-new file, then stage EVERYTHING"
echo "=============================================================="
echo "console.log('hello');" > index.js

git status
echo "-> Now we have one 'modified' file (README.md) and one 'untracked' file (index.js)."

git add .
echo "-> 'git add .' staged both changes at once."
git status -s

echo
echo "=============================================================="
echo "STEP 10: Commit both staged changes together"
echo "=============================================================="
git commit -m "feat: add index.js and expand README"

git log --oneline
echo "-> Two commits now. This IS the core loop:"
echo "   edit (Working Dir) -> git add (Staging) -> git commit (Repository)"

echo
echo "=============================================================="
echo "STEP 11: Full history, and comparing two arbitrary commits"
echo "=============================================================="
git log --graph --oneline --all
echo
echo "Compare the very first commit against the current state of README.md:"
FIRST_COMMIT=$(git log --reverse --format=%H | head -n 1)
git diff "$FIRST_COMMIT" HEAD -- README.md

echo
echo "=============================================================="
echo "STEP 12: .gitignore -- stop Git from ever seeing certain files"
echo "=============================================================="
cat > .gitignore << 'EOF'
node_modules/
dist/
.env
*.log
EOF
mkdir -p node_modules
echo "fake-dependency" > node_modules/fake-dep.txt
echo "SECRET=123" > .env

git add .gitignore
git commit -m "chore: add .gitignore"

git status
echo "-> Notice: node_modules/ and .env do NOT show up as untracked at all --"
echo "   .gitignore is doing its job. (It only affects files not already tracked.)"

echo
echo "=============================================================="
echo "STEP 13: git commit --amend -- fixing the most recent commit"
echo "=============================================================="
echo "extra-ignore-entry/" >> .gitignore
git add .gitignore
git commit --amend -m "chore: add and refine .gitignore"
git log --oneline -3
echo "-> Notice: still the same NUMBER of commits -- amend replaced the last one"
echo "   instead of adding a new one. Never amend a commit already pushed/shared."

echo
echo "=============================================================="
echo "DONE. You've now exercised the full core workflow:"
echo "  init -> untracked -> staged -> committed -> modified -> re-staged"
echo "  -> re-committed -> .gitignore -> amend, checking status/diff/log"
echo "  at every step along the way."
echo "=============================================================="
