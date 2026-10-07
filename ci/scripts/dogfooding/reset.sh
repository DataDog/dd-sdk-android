#!/usr/bin/env bash
#
# Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
# This product includes software developed at Datadog (https://www.datadoghq.com/).
# Copyright 2016-Present Datadog, Inc.
#
# Resets `dogfooding`'s content to exactly match `develop`, without force-pushing and
# without losing history: creates a merge commit whose tree matches `develop` exactly
# but whose parents are both the current `dogfooding` tip and `develop`'s tip (so future
# syncs won't try to reintroduce discarded work), then opens a PR for review.
#
# Usage: ./ci/scripts/dogfooding/reset.sh [--force]
#   --force  Skip the confirmation prompt.
#
# Safe to run locally: the reset is done in a temporary worktree, so the checkout it's run
# from (current branch, uncommitted changes) is never touched.
#
# The reset branch is named after the current `dogfooding` and `develop` tips. Re-running
# against the same tips prints the already-open PR instead of opening a new one; if that
# branch exists without an open PR, the script fails and asks you to delete it first. Exits
# without opening a PR if `dogfooding` already has `develop`'s exact content.
#
# Features dogfooded since the last reset (with dogfooding/feature.sh) that haven't graduated to
# `develop` yet are listed in the prompt and the PR body, mentioning their authors, with the
# command to dogfood them again once this reset is merged. Open dogfood PRs are listed too:
# they were computed against the content this reset removes, so they must be closed and
# re-created after it.
#
# NOTE: This script does not modify `dogfooding` directly — it opens a PR against it.
# A human must review and merge that PR (branch protection requires this).

set -euo pipefail

REPO="DataDog/dd-sdk-android"
DOGFOODING_BRANCH="dogfooding"
# Subject of the reset commit. The dogfood, reset and sync scripts find the last reset by it,
# so don't change it.
RESET_SUBJECT="Reset dogfooding to develop"
FORCE=false

for arg in "$@"; do
  case "$arg" in
    --force) FORCE=true ;;
    *) echo "Unknown argument: $arg" >&2; exit 1 ;;
  esac
done

# Makes a commit available locally: uses it if it's already there, otherwise fetches it by SHA
# from origin (it may only be reachable by SHA, e.g. a feature tip that was force-pushed).
ensure_commit() {
  git cat-file -e "$1^{commit}" 2>/dev/null && return 0
  git fetch --quiet origin "$1" 2>/dev/null && git cat-file -e "$1^{commit}" 2>/dev/null
}

echo "Fetching latest from origin..."
if [ "$(git rev-parse --is-shallow-repository)" = "true" ]; then
  # Unshallow so the discard list and the merge below see the real history of both branches.
  git fetch --unshallow origin
fi
git fetch origin "+refs/heads/develop:refs/remotes/origin/develop" \
  "+refs/heads/$DOGFOODING_BRANCH:refs/remotes/origin/$DOGFOODING_BRANCH"

# Already has develop's exact content (e.g. right after a reset was merged): nothing to reset.
if git diff --quiet origin/develop "origin/$DOGFOODING_BRANCH"; then
  echo "$DOGFOODING_BRANCH already matches develop, nothing to reset."
  exit 0
fi

# Named after both tips, so re-running against the same tips maps to the same branch/PR.
DOGFOODING_SHA=$(git rev-parse "origin/$DOGFOODING_BRANCH")
DEVELOP_SHA=$(git rev-parse origin/develop)
RESET_BRANCH="reset-dogfooding-${DOGFOODING_SHA:0:12}-${DEVELOP_SHA:0:12}"

open_prs=$(gh pr list --repo "$REPO" --base "$DOGFOODING_BRANCH" --state open \
  --json number,headRefName,url,author --limit 200 \
  -q '.[] | "\(.number)\t\(.headRefName)\t\(.url)\t\(.author.login)"')

# Already have a PR for these exact tips: nothing to do.
existing_pr=$(awk -F'\t' -v head="$RESET_BRANCH" '$2 == head { print $3; exit }' <<< "$open_prs")
if [ -n "$existing_pr" ]; then
  echo "A reset PR for the current $DOGFOODING_BRANCH and develop tips is already open: $existing_pr"
  exit 0
fi

# The branch exists without an open PR (its PR was closed, or a previous run failed before
# opening one). Never overwrite it: someone may have pushed to it.
if git ls-remote --exit-code --heads origin "refs/heads/$RESET_BRANCH" >/dev/null; then
  echo "origin/$RESET_BRANCH already exists but has no open PR (its PR was closed, or a previous run failed before opening one)." >&2
  echo "Inspect it, delete it with 'git push origin --delete $RESET_BRANCH', then re-run." >&2
  exit 1
fi

# Everything merged into dogfooding since the last reset; what came before was already
# discarded by that reset. The reset commit is found by the subject this script writes
# below; --topo-order makes "newest" independent of commit dates.
LAST_RESET=$(git log -1 --topo-order --format=%H --grep="^$RESET_SUBJECT" \
  origin/develop.."origin/$DOGFOODING_BRANCH")
if [ -n "$LAST_RESET" ]; then
  SINCE_RESET=("$LAST_RESET..origin/$DOGFOODING_BRANCH" --not origin/develop)
else
  SINCE_RESET=("origin/develop..origin/$DOGFOODING_BRANCH")
fi

# What this reset discards, one line per merged PR (--first-parent skips the commits inside
# each merged branch), leaving out the merge that brought the previous reset in and plain
# sync merges (their second parent is part of develop, so they bring nothing of their own).
AHEAD=$(git log --first-parent --format='%H %s' "${SINCE_RESET[@]}" | while read -r sha subject; do
  if [ -n "$LAST_RESET" ] && git merge-base --is-ancestor "$LAST_RESET" "$sha" \
    && ! git merge-base --is-ancestor "$LAST_RESET" "$sha^1"; then
    continue
  fi
  if git merge-base --is-ancestor "$sha^2" origin/develop 2>/dev/null; then
    continue
  fi
  echo "${sha:0:12} $subject"
done)

# Features dogfooded since the last reset (newest Dogfood-Source trailer per branch) that
# haven't graduated to develop yet. This reset removes them from dogfooding, so they must be
# dogfooded again once it's merged.
IN_FLIGHT=""
# Features whose dogfooded commit can't be found locally or on origin, when it's needed to
# tell whether they graduated or to dogfood them again.
UNKNOWN=""
seen=" "
while IFS=$'\t' read -r source author; do
  [ -n "$source" ] || continue
  branch="${source%@*}"
  sha="${source##*@}"
  case "$seen" in *" $branch "*) continue ;; esac
  seen="$seen$branch "
  unknown_entry="- $branch (dogfooded at ${sha:0:12}) ${author:-}
"
  if git ls-remote --exit-code --heads origin "refs/heads/$branch" >/dev/null 2>&1; then
    command="./ci/scripts/dogfooding/feature.sh $branch"
    needs_sha=false
  else
    command="./ci/scripts/dogfooding/feature.sh $branch --at $sha"
    needs_sha=true
  fi
  # Graduated: a PR from this branch into develop merged everything that was dogfooded, i.e.
  # its head (the branch tip when it merged) is the dogfooded commit, contains it, or is
  # unrelated to it (the branch was rebased before merging). A merged head that is a strict
  # ancestor of the dogfooded commit only brought in part of it: an older PR that reused the
  # branch name, or the branch kept going after an earlier PR merged, so it doesn't count.
  # If gh fails, stop: without the merged PRs there's no reliable way to tell whether the feature
  # graduated (its branch may have been rebased before merging), and nothing has been pushed yet.
  # Comparing the merged heads needs the dogfooded commit; without merged PRs it's only needed
  # for the --at command.
  if ! merged_heads=$(gh pr list --repo "$REPO" --head "$branch" --base develop --state merged \
    --json headRefOid --limit 100 -q '.[].headRefOid'); then
    echo "Couldn't list the PRs merged from $branch into develop (gh failed). Nothing was pushed: re-run once gh works." >&2
    exit 1
  fi
  if [ -z "$merged_heads" ]; then
    graduated=false
  elif ! ensure_commit "$sha"; then
    UNKNOWN="$UNKNOWN$unknown_entry"
    continue
  else
    graduated=false
    while read -r head; do
      [ -n "$head" ] || continue
      if [ "$head" = "$sha" ] || ! git merge-base --is-ancestor "$head" "$sha" 2>/dev/null; then
        graduated=true
      fi
    done <<< "$merged_heads"
  fi
  [ "$graduated" = false ] || continue
  if [ "$needs_sha" = true ] && ! ensure_commit "$sha"; then
    UNKNOWN="$UNKNOWN$unknown_entry"
    continue
  fi
  IN_FLIGHT="$IN_FLIGHT- $branch (dogfooded at ${sha:0:12}) ${author:-}: $command
"
done <<< "$(git log --no-merges --topo-order \
  --format='%(trailers:key=Dogfood-Source,valueonly,separator=%x2C)%x09%(trailers:key=Dogfood-Author,valueonly,separator=%x2C)' \
  "${SINCE_RESET[@]}")"

# Open dogfood PRs were computed against content this reset removes: merging one after it
# would apply a partial feature. They must be closed and re-created once this reset is merged.
OPEN_DOGFOOD=""
older_prs=""
while IFS=$'\t' read -r number head url author; do
  [ -n "$number" ] || continue
  if [[ "$head" =~ ^dogfood-(.+)-[0-9a-f]{12}$ ]]; then
    OPEN_DOGFOOD="$OPEN_DOGFOOD- #$number $head @$author: gh pr close $number --delete-branch, then once this reset is merged ./ci/scripts/dogfooding/feature.sh ${BASH_REMATCH[1]}
"
  elif [[ "$head" == reset-dogfooding-* ]]; then
    older_prs="${older_prs:+$older_prs, }#$number"
  fi
done <<< "$open_prs"

if { [ -n "$AHEAD" ] || [ -n "$OPEN_DOGFOOD" ]; } && [ "$FORCE" = false ]; then
  if [ -n "$AHEAD" ]; then
    echo "⚠️  The following commits on $DOGFOODING_BRANCH will be discarded:"
    echo "$AHEAD"
    echo ""
  fi
  if [ -n "$IN_FLIGHT" ]; then
    echo "⚠️  Dogfooded features not in develop yet. Dogfood each one again once the reset PR is merged:"
    echo "$IN_FLIGHT"
  fi
  if [ -n "$UNKNOWN" ]; then
    echo "⚠️  Dogfooded features with unknown status (the dogfooded commit can't be found locally or on origin). Check manually whether each one is in develop:"
    echo "$UNKNOWN"
  fi
  if [ -n "$OPEN_DOGFOOD" ]; then
    echo "⚠️  Open dogfood PRs, computed against content this reset removes. Close each one without merging, then re-create it once the reset PR is merged:"
    echo "$OPEN_DOGFOOD"
  fi
  read -p "Are you sure you want to reset? [y/N] " confirm
  if [[ "$confirm" != "y" && "$confirm" != "Y" ]]; then
    echo "Aborted."
    exit 0
  fi
fi

repo_dir=$(pwd)

# Do the reset in a separate worktree so this never checks out a branch (or
# touches uncommitted work) in whatever repo the script happens to be run from.
worktree_dir=$(mktemp -d)
trap 'cd "$repo_dir"; git worktree remove --force "$worktree_dir" >/dev/null 2>&1 || true' EXIT
echo "Creating $RESET_BRANCH from origin/$DOGFOODING_BRANCH in a temporary worktree..."
git worktree add --quiet -b "$RESET_BRANCH" "$worktree_dir" "origin/$DOGFOODING_BRANCH"
# Only now do we know this run created $RESET_BRANCH - safe to delete it on exit.
# Removing the worktree doesn't delete the branch it checked out, so without this
# a retry after a failed run would fail at `git worktree add -b` with
# "branch already exists".
trap 'cd "$repo_dir"; git worktree remove --force "$worktree_dir" >/dev/null 2>&1 || true; git branch -D "$RESET_BRANCH" >/dev/null 2>&1 || true' EXIT
cd "$worktree_dir"

echo "Recording origin/develop as merged while keeping our tree for now..."
git merge -s ours --no-commit origin/develop

echo "Overwriting tree/index to match origin/develop exactly..."
git read-tree -u --reset origin/develop

echo "Committing reset..."
git commit --no-verify -m "$RESET_SUBJECT (discard in-flight dogfood-only work)"

echo "Pushing $RESET_BRANCH..."
git push origin "HEAD:refs/heads/$RESET_BRANCH"

pr_body="Discards:
$AHEAD"
if [ -n "$IN_FLIGHT" ]; then
  pr_body="$pr_body

Dogfooded features not in develop yet. This reset removes them from dogfooding; once it's merged, dogfood each one again:
${IN_FLIGHT%$'\n'}"
fi
if [ -n "$UNKNOWN" ]; then
  pr_body="$pr_body

Dogfooded features with unknown status (the dogfooded commit can't be found locally or on origin). Check manually whether each one is in develop, and dogfood it again once this PR is merged if it isn't:
${UNKNOWN%$'\n'}"
fi
if [ -n "$OPEN_DOGFOOD" ]; then
  pr_body="$pr_body

Open dogfood PRs, computed against content this reset removes (merging one after it would apply a partial feature). Close each one without merging and delete its branch, then re-create it once this PR is merged:
${OPEN_DOGFOOD%$'\n'}"
fi
# Older reset PRs were computed against older tips; merging one of them would leave stray
# content behind, so point reviewers away from them.
if [ -n "$older_prs" ]; then
  pr_body="$pr_body

Supersedes $older_prs (computed against older $DOGFOODING_BRANCH/develop tips). Close them without merging."
fi

echo "Opening PR against $DOGFOODING_BRANCH..."
gh pr create --repo "$REPO" --base "$DOGFOODING_BRANCH" --head "$RESET_BRANCH" \
  --title "Reset dogfooding branch to develop" \
  --body "$pr_body"

echo ""
echo "Done. Review and merge the PR above to complete the reset."
