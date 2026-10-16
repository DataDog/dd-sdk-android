#!/usr/bin/env bash
#
# Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
# This product includes software developed at Datadog (https://www.datadoghq.com/).
# Copyright 2016-Present Datadog, Inc.
#
# Brings a feature branch into `dogfooding` as a single squash commit, through a PR. This is
# the only way features should reach `dogfooding`: the feature's own commits never enter its
# history, so resets and syncs can't silently drop the feature later on.
#
# Usage: ./ci/scripts/dogfooding/feature.sh <branch> [--at <sha>] [--full]
#   <branch>    The feature branch to dogfood (its tip on origin, unless --at is given).
#   --at <sha>  Dogfood this commit instead of the branch tip, e.g. once the branch was deleted
#               after merging into develop. Use the full SHA if the commit isn't local.
#   --full      Ignore earlier dogfoods of the branch and squash the whole feature again,
#               e.g. after its dogfood commit was reverted in `dogfooding`.
#
# The first dogfood of a branch since the last reset squashes the whole feature. Later ones
# only bring the changes since the previous dogfood (tracked with a `Dogfood-Source` trailer
# on each squash commit), so updates, removed files and force-pushed rebases all apply
# correctly. The PR is opened from a `dogfood-<branch>-<sha>` branch and must be merged with
# a merge commit.
#
# Safe to run locally: the squash is done in a temporary worktree, so the checkout it's run
# from (current branch, uncommitted changes) is never touched. On a conflict the worktree is
# kept and the script prints how to finish by hand.
#
# NOTE: This script does not modify `dogfooding` directly — it opens a PR against it.
# A human must review and merge that PR (branch protection requires this).

set -euo pipefail

REPO="DataDog/dd-sdk-android"
DOGFOODING_BRANCH="dogfooding"
RESET_SUBJECT="Reset dogfooding to develop"

usage() {
  echo "Usage: $0 <branch> [--at <sha>] [--full]" >&2
  exit 1
}

BRANCH=""
AT=""
FULL=false
while [ $# -gt 0 ]; do
  case "$1" in
    --at) [ -n "${2:-}" ] || usage; AT="$2"; shift 2 ;;
    --full) FULL=true; shift ;;
    -*) echo "Unknown option: $1" >&2; usage ;;
    *) [ -z "$BRANCH" ] || usage; BRANCH="$1"; shift ;;
  esac
done
[ -n "$BRANCH" ] || usage

# Makes a commit available locally: uses it if it's already there (e.g. whoever force-pushed
# the branch still has the old tip), otherwise fetches it by SHA from origin.
ensure_commit() {
  git cat-file -e "$1^{commit}" 2>/dev/null && return 0
  git fetch --quiet origin "$1" 2>/dev/null && git cat-file -e "$1^{commit}" 2>/dev/null
}

echo "Fetching latest from origin..."
if [ "$(git rev-parse --is-shallow-repository)" = "true" ]; then
  # Unshallow so the checks below see the real history, not a truncated one.
  git fetch --unshallow origin
fi
git fetch origin "+refs/heads/develop:refs/remotes/origin/develop" \
  "+refs/heads/$DOGFOODING_BRANCH:refs/remotes/origin/$DOGFOODING_BRANCH"

if [ -n "$AT" ]; then
  if ! ensure_commit "$AT"; then
    echo "Can't find commit $AT locally or on origin. Pass the full SHA." >&2
    exit 1
  fi
  TARGET=$(git rev-parse "$AT^{commit}")
else
  if ! git fetch --quiet origin "+refs/heads/$BRANCH:refs/remotes/origin/$BRANCH" 2>/dev/null; then
    echo "Branch $BRANCH not found on origin. If it was deleted, pass the commit with --at <sha>." >&2
    exit 1
  fi
  TARGET=$(git rev-parse "refs/remotes/origin/$BRANCH")
fi
SHA12=${TARGET:0:12}
DOGFOOD_BRANCH="dogfood-$BRANCH-$SHA12"

# Everything merged into dogfooding since the last reset. Earlier dogfoods were discarded by
# that reset, so they don't count. The reset commit is found by the subject the reset script
# writes itself; --topo-order makes "newest" independent of commit dates.
LAST_RESET=$(git log -1 --topo-order --format=%H --grep="^$RESET_SUBJECT" \
  origin/develop.."origin/$DOGFOODING_BRANCH")
if [ -n "$LAST_RESET" ]; then
  SINCE_RESET=("$LAST_RESET..origin/$DOGFOODING_BRANCH" --not origin/develop)
else
  SINCE_RESET=("origin/develop..origin/$DOGFOODING_BRANCH")
fi

# Newest Dogfood-Source trailer for this branch since the last reset ("<branch>@<sha>", split
# on the last @ so branch names are compared exactly).
LAST_DOGFOOD=""
if [ "$FULL" = false ]; then
  trailers=$(git log --no-merges --topo-order --format='%(trailers:key=Dogfood-Source,valueonly)' "${SINCE_RESET[@]}")
  while IFS= read -r source; do
    [ -n "$source" ] || continue
    if [ "${source%@*}" = "$BRANCH" ]; then
      LAST_DOGFOOD="${source##*@}"
      break
    fi
  done <<< "$trailers"
fi

open_prs=$(gh pr list --repo "$REPO" --base "$DOGFOODING_BRANCH" --state open \
  --json number,headRefName,url --limit 200 -q '.[] | "\(.number)\t\(.headRefName)\t\(.url)"')

# 1. A PR for this exact commit is already open: nothing to do.
existing_pr=$(awk -F'\t' -v head="$DOGFOOD_BRANCH" '$2 == head { print $3; exit }' <<< "$open_prs")
if [ -n "$existing_pr" ]; then
  echo "A dogfood PR for $BRANCH at $SHA12 is already open: $existing_pr"
  exit 0
fi

# 2. This exact commit is already dogfooded.
if [ "$LAST_DOGFOOD" = "$TARGET" ]; then
  echo "$BRANCH at $SHA12 is already dogfooded. If its dogfood commit was reverted, re-run with --full."
  exit 0
fi

# 3. The branch exists without an open PR (its PR was closed, or a previous run failed before
# opening one). Never overwrite it: someone may have pushed to it.
if git ls-remote --exit-code --heads origin "refs/heads/$DOGFOOD_BRANCH" >/dev/null; then
  echo "origin/$DOGFOOD_BRANCH already exists but has no open PR (its PR was closed, or a previous run failed before opening one)." >&2
  echo "Inspect it, delete it with 'git push origin --delete $DOGFOOD_BRANCH', then re-run." >&2
  exit 1
fi

# 4. Another dogfood of this branch is still open: its delta would overlap with this one.
# 5. A reset is pending: a delta computed now would apply on content the reset removes.
while IFS=$'\t' read -r number head url; do
  [ -n "$number" ] || continue
  suffix="${head#"dogfood-$BRANCH-"}"
  if [ "$suffix" != "$head" ] && [[ "$suffix" =~ ^[0-9a-f]{12}$ ]]; then
    echo "Another dogfood PR for $BRANCH is still open: $url" >&2
    echo "Merge or close it (deleting its branch) first, then re-run." >&2
    exit 1
  fi
  if [[ "$head" == reset-dogfooding-* ]]; then
    echo "A reset of $DOGFOODING_BRANCH is pending (#$number): $url" >&2
    echo "Wait until it's merged, then re-run." >&2
    exit 1
  fi
done <<< "$open_prs"

# 6. The feature must not be based on develop commits that dogfooding doesn't have yet,
# otherwise the squash would bring those commits in too.
base=$(git merge-base "$TARGET" origin/develop || true)
if [ -z "$base" ] || ! git merge-base --is-ancestor "$base" "origin/$DOGFOODING_BRANCH"; then
  echo "$BRANCH at $SHA12 is based on develop commits that $DOGFOODING_BRANCH doesn't have yet." >&2
  echo "Run ./ci/scripts/dogfooding/sync.sh (or merge the open sync PR) first, then re-run." >&2
  exit 1
fi

if [ -n "$LAST_DOGFOOD" ] && ! ensure_commit "$LAST_DOGFOOD"; then
  echo "Can't find $LAST_DOGFOOD, the previously dogfooded tip of $BRANCH (it may have been garbage-collected after a force-push)." >&2
  echo "Your clone doesn't have it, usually because someone else force-pushed $BRANCH. Whoever still has it can re-run from their clone, or push it (git push origin $LAST_DOGFOOD:refs/heads/tmp-recover) so it can be fetched; then re-run." >&2
  exit 1
fi

# Source PR into develop, for the PR link and to notify its author.
source_pr=$(gh pr list --repo "$REPO" --head "$BRANCH" --base develop --state all \
  --json url,author,state -q '(map(select(.state == "OPEN")) + .)[0] // empty | "\(.url)\t\(.author.login)"' 2>/dev/null || true)
source_pr_url=$(cut -f1 <<< "$source_pr")
author=$(cut -s -f2 <<< "$source_pr")
[ -n "$author" ] || author=$(gh api user -q .login 2>/dev/null || true)
if [ -n "$author" ]; then author="@$author"; else author=$(git config user.name || echo unknown); fi

if [ -n "$LAST_DOGFOOD" ]; then
  scope="Changes in $BRANCH since its previous dogfood at ${LAST_DOGFOOD:0:12}, up to $SHA12."
else
  scope="Full squash of $BRANCH at $SHA12."
fi
TITLE="Dogfood $BRANCH @ $SHA12"

repo_dir=$(pwd)

# Do the squash in a separate worktree so this never checks out a branch (or touches
# uncommitted work) in whatever repo the script happens to be run from.
worktree_dir=$(mktemp -d)
trap 'cd "$repo_dir"; git worktree remove --force "$worktree_dir" >/dev/null 2>&1 || true' EXIT
git worktree add --quiet -b "$DOGFOOD_BRANCH" "$worktree_dir" "origin/$DOGFOODING_BRANCH"
# Only now do we know this run created $DOGFOOD_BRANCH - safe to delete it on exit.
trap 'cd "$repo_dir"; git worktree remove --force "$worktree_dir" >/dev/null 2>&1 || true; git branch -D "$DOGFOOD_BRANCH" >/dev/null 2>&1 || true' EXIT
cd "$worktree_dir"

# The commit message and PR body live in the git dir, not the worktree, so resolving a
# conflict with `git add -A` can't commit them.
git_dir=$(git rev-parse --absolute-git-dir)
msg_file="$git_dir/DOGFOOD_MSG"
body_file="$git_dir/DOGFOOD_PR_BODY"
{
  echo "$TITLE"
  echo ""
  echo "$scope"
  [ -z "$source_pr_url" ] || echo "Source PR: $source_pr_url"
  echo ""
  echo "Dogfood-Source: $BRANCH@$TARGET"
  echo "Dogfood-Author: $author"
} > "$msg_file"
{
  echo "Dogfoods \`$BRANCH\` at $SHA12 as a single squash commit, created by \`ci/scripts/dogfooding/feature.sh\`."
  echo ""
  echo "$scope"
  [ -z "$source_pr_url" ] || echo "Source PR: $source_pr_url"
  echo "Author: $author"
  echo ""
  echo "Merge this PR with a merge commit, never squash: the \`Dogfood-Source\` trailer on the squash commit is how the reset and sync scripts track this feature."
} > "$body_file"

if [ -n "$LAST_DOGFOOD" ]; then
  echo "Applying the changes in $BRANCH since ${LAST_DOGFOOD:0:12}..."
  # A throwaway commit whose diff is exactly "previous dogfood -> target", even if the
  # branch was rebased in between.
  delta=$(git commit-tree "$TARGET^{tree}" -p "$LAST_DOGFOOD" -m "dogfood delta")
  git cherry-pick --no-commit "$delta" || true
else
  echo "Squashing $BRANCH at $SHA12..."
  git merge --squash "$TARGET" || true
fi

conflicts=$(git diff --name-only --diff-filter=U)
if [ -n "$conflicts" ]; then
  echo "" >&2
  echo "Conflicts while applying $BRANCH onto $DOGFOODING_BRANCH:" >&2
  echo "$conflicts" | sed 's/^/  /' >&2
  trap - EXIT
  cat >&2 <<EOF

The worktree is kept so you can finish by hand:
  cd "$worktree_dir"
  # resolve the conflicts, then:
  git add <files>
  git commit --no-verify -F "$msg_file"
  git push origin "HEAD:refs/heads/$DOGFOOD_BRANCH"
  gh pr create --repo $REPO --base $DOGFOODING_BRANCH --head "$DOGFOOD_BRANCH" --title "$TITLE" --body-file "$body_file"
Then clean up:
  cd "$repo_dir" && git worktree remove --force "$worktree_dir" && git branch -D "$DOGFOOD_BRANCH"
EOF
  exit 1
fi

if git diff --cached --quiet; then
  echo "Nothing to dogfood: $DOGFOODING_BRANCH already has the changes of $BRANCH at $SHA12."
  exit 0
fi

# --no-verify: this commit only squashes code that is reviewed on the feature branch, and the
# repo's pre-commit hook (lint, tests) would take minutes on every dogfood.
git commit --quiet --no-verify -F "$msg_file"

echo "Pushing $DOGFOOD_BRANCH..."
git push origin "HEAD:refs/heads/$DOGFOOD_BRANCH"

echo "Opening PR against $DOGFOODING_BRANCH..."
gh pr create --repo "$REPO" --base "$DOGFOODING_BRANCH" --head "$DOGFOOD_BRANCH" \
  --title "$TITLE" --body-file "$body_file"

echo ""
echo "Done. Review and merge the PR above (with a merge commit) to dogfood $BRANCH."
