#!/usr/bin/env bash
# =============================================================================
# 02 - BRANCHING, MERGING, AND CONFLICT RESOLUTION WALKTHROUGH
# Companion to: "02 Branching Merging and Collaboration.md"
#
# Two branches deliberately edit the SAME line of the SAME file.
# One merges in cleanly (fast-forward-ish setup), the other produces a REAL
# merge conflict that we resolve by hand, exactly as you would in practice.
# Also demonstrates 'git stash' mid-workflow.
#
# Run this INSIDE an empty scratch directory (ideally a fresh one, separate
# from script 01's):
#   mkdir git-practice-branching && cd git-practice-branching
#   bash /path/to/02-branching-merging-conflict-resolution.sh
# =============================================================================

set -e

echo "=============================================================="
echo "SETUP: init a repo with one file and one base commit"
echo "=============================================================="
git init
git config user.name "My Name" 2>/dev/null || true
git config user.email "email@example.com" 2>/dev/null || true

cat > pricing.txt << 'EOF'
PRODUCT: Widget
PRICE: 10
EOF
git add pricing.txt
git commit -m "feat: add initial pricing file"
git branch -M main

echo
echo "=============================================================="
echo "STEP 1: Create branch 'feature-discount' and edit the PRICE line"
echo "=============================================================="
git switch -c feature-discount
echo "-> 'git switch -c' both CREATES and SWITCHES to the new branch."

sed -i 's/PRICE: 10/PRICE: 8   # 20% discount applied/' pricing.txt
cat pricing.txt
git add pricing.txt
git commit -m "feat: apply 20% discount to widget price"

echo
echo "=============================================================="
echo "STEP 2: Back to main, create SECOND branch 'feature-tax' that edits"
echo "        the SAME line differently -- this is what sets up the conflict"
echo "=============================================================="
git switch main
git switch -c feature-tax

sed -i 's/PRICE: 10/PRICE: 11   # 10% tax applied/' pricing.txt
cat pricing.txt
git add pricing.txt
git commit -m "feat: apply 10% tax to widget price"

echo
echo "=============================================================="
echo "STEP 3: Merge feature-discount into main FIRST -- this one is clean"
echo "=============================================================="
git switch main
git log --oneline --all --graph
echo
git merge --no-ff feature-discount -m "merge: bring in feature-discount"
echo "-> Clean merge. main's PRICE line now says '8 # 20% discount applied'."
cat pricing.txt

echo
echo "=============================================================="
echo "STEP 4: Now merge feature-tax -- CONFLICT, because main and"
echo "        feature-tax both changed the same line since they diverged"
echo "=============================================================="
set +e   # allow this specific command to fail without killing the script
git merge feature-tax -m "merge: bring in feature-tax"
MERGE_EXIT=$?
set -e

if [ $MERGE_EXIT -ne 0 ]; then
  echo
  echo "-> As expected: Git could not auto-merge pricing.txt."
  git status
  echo
  echo "-> Open pricing.txt now. You will see conflict markers exactly like this:"
  echo
  cat pricing.txt
  echo
  echo "The real content between the markers means:"
  echo '  <<<<<<< HEAD                       -- start of "your current branch" version (main)'
  echo '  PRICE: 8   # 20% discount applied   -- what HEAD (main) currently has'
  echo '  =======                             -- divider'
  echo '  PRICE: 11   # 10% tax applied        -- what the incoming branch (feature-tax) has'
  echo '  >>>>>>> feature-tax                 -- end of "incoming branch" version'
  echo
  echo "=============================================================="
  echo "STEP 5: RESOLVE the conflict by hand -- decide the correct combined"
  echo "        value, remove ALL marker lines, then add + commit"
  echo "=============================================================="
  cat > pricing.txt << 'EOF'
PRODUCT: Widget
PRICE: 8.8   # 20% discount then 10% tax applied
EOF
  echo "-> Edited file with markers removed and the real resolution written:"
  cat pricing.txt

  git add pricing.txt
  echo "-> 'git add' on a conflicted file tells Git 'this conflict is resolved.'"
  git status
  echo "-> Notice pricing.txt now shows as staged, no longer listed as conflicted."

  git commit -m "merge: resolve pricing conflict between discount and tax branches"
  echo "-> Since we already had a merge message via -m, this commits the merge."
  echo "   (If you omit -m during a real conflict, Git opens your editor with a"
  echo "    pre-filled 'Merge branch...' message -- just save and close it.)"
else
  echo "-> Merge succeeded without conflict (unexpected for this setup, but continuing)."
fi

echo
git log --oneline --graph --all
echo "-> Final history shows both feature branches merged into main, including"
echo "   the merge commit that resolved the conflict."

echo
echo "=============================================================="
echo "REMINDER: if a conflict ever gets too messy to resolve, abort and"
echo "restart cleanly with:"
echo "  git merge --abort"
echo "=============================================================="

echo
echo "=============================================================="
echo "STEP 6: git stash -- shelve unfinished work mid-task"
echo "=============================================================="
echo "Scenario: you're mid-way through more pricing changes when an urgent"
echo "fix is needed elsewhere. You don't want to commit half-finished work."

echo "PRICE: 8.8   # 20% discount then 10% tax applied -- DRAFT: maybe change to 9?" > pricing.txt
git status
echo "-> pricing.txt is modified but NOT staged, and not ready to commit."

git stash
echo "-> Working directory is now clean again (matches last commit):"
git status
cat pricing.txt
echo "-> Notice the draft edit is gone from the file -- it's safely shelved, not lost."

git stash list
echo "-> The stash is saved in a list -- you could have multiple stashes."

echo
echo "Simulate the 'urgent fix' work happening here (skipped for brevity)..."
echo "Now resume the original task:"

git stash pop
echo "-> 'pop' re-applies the MOST RECENT stash AND removes it from the list."
cat pricing.txt
git stash list
echo "-> Stash list is empty again -- the draft change is back in your working"
echo "   directory exactly where you left it, ready to be finished and committed."

echo
echo "=============================================================="
echo "DONE. You've now exercised: divergent branches, a clean merge,"
echo "a REAL merge conflict with hand-resolution, and a stash/pop cycle."
echo "=============================================================="
