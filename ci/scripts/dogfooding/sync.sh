#!/usr/bin/env bash
#
# Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
# This product includes software developed at Datadog (https://www.datadoghq.com/).
# Copyright 2016-Present Datadog, Inc.
#
# Brings `dogfooding` up to date with `develop` without discarding in-flight dogfood-only
# work: pushes a snapshot of develop's current tip as a side branch and opens a PR against
# `dogfooding`. No local merge is attempted — GitHub computes the merge itself when the PR
# is viewed/merged, so a conflicting sync still produces a visible, trackable PR (marked
# "must be resolved") instead of silently failing with nothing to show for it.
#
# Usage: ./ci/scripts/dogfooding/sync.sh
#
# Never checks anything out locally, so it's safe to run from any checkout. Exits without
# pushing or opening a PR if `dogfooding` already contains `develop`.
#
# The sync branch is named after `develop`'s tip. Re-running against the same tip prints the
# already-open PR instead of opening a new one; if that branch exists without an open PR, the
# script fails and asks you to delete it first.
#
# Features dogfooded with dogfooding/feature.sh that graduated to `develop` at a different commit
# than the one dogfooded are flagged in the output and the PR body, mentioning their authors:
# once the sync is merged they must be dogfooded again at their graduated commit, otherwise
# code that only existed in the dogfooded version stays in `dogfooding`.
#
# NOTE: This script does not modify `dogfooding` directly — it opens a PR against it.
# A human must review and merge that PR (branch protection requires this).

set -euo pipefail

REPO="DataDog/dd-sdk-android"
DOGFOODING_BRANCH="dogfooding"
# Subject of the reset commit written by dogfooding/reset.sh; the last reset is found by it.
RESET_SUBJECT="Reset dogfooding to develop"

echo "Fetching latest from origin..."
if [ "$(git rev-parse --is-shallow-repository)" = "true" ]; then
  # Unshallow so the "nothing to sync" check below can see whether develop's tip is
  # already part of dogfooding's history.
  git fetch --unshallow origin
fi
git fetch origin "+refs/heads/develop:refs/remotes/origin/develop" \
  "+refs/heads/$DOGFOODING_BRANCH:refs/remotes/origin/$DOGFOODING_BRANCH"

if git merge-base --is-ancestor origin/develop "origin/$DOGFOODING_BRANCH"; then
  echo "$DOGFOODING_BRANCH already contains origin/develop, nothing to sync."
  exit 0
fi

# Named after develop's tip, so re-running against the same tip maps to the same branch/PR.
DEVELOP_SHA=$(git rev-parse origin/develop)
SYNC_BRANCH="sync-dogfooding-${DEVELOP_SHA:0:12}"

open_prs=$(gh pr list --repo "$REPO" --base "$DOGFOODING_BRANCH" --state open \
  --json number,headRefName,url --limit 200 -q '.[] | "\(.number)\t\(.headRefName)\t\(.url)"')

# Already have a PR for this exact tip: nothing to do.
existing_pr=$(awk -F'\t' -v head="$SYNC_BRANCH" '$2 == head { print $3; exit }' <<< "$open_prs")
if [ -n "$existing_pr" ]; then
  echo "A sync PR for the current develop tip is already open: $existing_pr"
  exit 0
fi

# The branch exists without an open PR (its PR was closed, or a previous run failed before
# opening one). Never overwrite it: someone may have pushed conflict fixes to it.
if git ls-remote --exit-code --heads origin "$SYNC_BRANCH" >/dev/null; then
  echo "origin/$SYNC_BRANCH already exists but has no open PR (its PR was closed, or a previous run failed before opening one)." >&2
  echo "Inspect it, delete it with 'git push origin --delete $SYNC_BRANCH', then re-run." >&2
  exit 1
fi

# Older sync PRs bring in an older develop tip; merging this one includes everything in them.
older_prs=$(awk -F'\t' '$2 ~ /^sync-dogfooding-/ { printf "%s#%s", sep, $1; sep = ", " }' <<< "$open_prs")

# Graduation check: features dogfooded since the last reset whose PR into develop is part of
# this sync, but graduated at a different commit than the one dogfooded (e.g. updated or
# rebased before merging). The sync brings develop's version in, but code that only existed
# in the dogfooded version stays in dogfooding until the feature is dogfooded again.
LAST_RESET=$(git log -1 --topo-order --format=%H --grep="^$RESET_SUBJECT" \
  origin/develop.."origin/$DOGFOODING_BRANCH")
if [ -n "$LAST_RESET" ]; then
  SINCE_RESET=("$LAST_RESET..origin/$DOGFOODING_BRANCH" --not origin/develop)
else
  SINCE_RESET=("origin/develop..origin/$DOGFOODING_BRANCH")
fi
GRADUATED=""
graduation_check_failed=false
seen=" "
while IFS=$'\t' read -r source author; do
  [ -n "$source" ] || continue
  branch="${source%@*}"
  sha="${source##*@}"
  case "$seen" in *" $branch "*) continue ;; esac
  seen="$seen$branch "
  if ! merged=$(gh pr list --repo "$REPO" --head "$branch" --base develop --state merged \
    --json mergeCommit,headRefOid --limit 100 -q '.[] | "\(.mergeCommit.oid)\t\(.headRefOid)"' 2>/dev/null); then
    graduation_check_failed=true
    continue
  fi
  while IFS=$'\t' read -r merge head; do
    [ -n "$merge" ] || continue
    # Only the PR merged as part of this sync counts (not an older PR with the same name).
    git merge-base --is-ancestor "$merge" origin/develop 2>/dev/null || continue
    ! git merge-base --is-ancestor "$merge" "origin/$DOGFOODING_BRANCH" 2>/dev/null || continue
    [ "$head" != "$sha" ] || continue
    # The merged head only brought in part of what was dogfooded (the branch kept going after
    # it): dogfooding already has everything develop now brings, so there's nothing left over.
    ! git merge-base --is-ancestor "$head" "$sha" 2>/dev/null || continue
    GRADUATED="$GRADUATED- $branch ${author:-}: dogfooded at ${sha:0:12}, graduated at ${head:0:12}. After merging this sync, run ./ci/scripts/dogfooding/feature.sh $branch --at $head
"
  done <<< "$merged"
done <<< "$(git log --no-merges --topo-order \
  --format='%(trailers:key=Dogfood-Source,valueonly,separator=%x2C)%x09%(trailers:key=Dogfood-Author,valueonly,separator=%x2C)' \
  "${SINCE_RESET[@]}")"

if [ -n "$GRADUATED" ]; then
  echo ""
  echo "⚠️  Dogfooded features that graduated to develop at a different commit than the one dogfooded:"
  echo "$GRADUATED"
fi
if [ "$graduation_check_failed" = true ]; then
  echo "⚠️  Graduation check skipped for some features: gh couldn't list their PRs into develop."
fi

echo "Pushing origin/develop as $SYNC_BRANCH..."
git push origin "refs/remotes/origin/develop:refs/heads/$SYNC_BRANCH"

pr_body="Brings dogfooding up to date with the latest develop. If this PR shows conflicts, resolve them by checking out $SYNC_BRANCH locally, merging $DOGFOODING_BRANCH into it, fixing the conflicts, and pushing back to the same branch."
if [ -n "$GRADUATED" ]; then
  pr_body="$pr_body

Dogfooded features that graduated to develop at a different commit than the one dogfooded. Code that only existed in the dogfooded version stays in dogfooding after this sync:
${GRADUATED%$'\n'}

- On conflicts in these features' code, take develop's side for their hunks.
- Don't run publish:dogfooding until they're dogfooded again.
- This warning won't be shown again after merging: leftovers stay until the feature is dogfooded again or the next reset."
fi
if [ -n "$older_prs" ]; then
  pr_body="$pr_body

Newer than $older_prs (older develop tip). Merging this PR includes everything in them, so they can be closed without merging."
fi

echo "Opening PR against $DOGFOODING_BRANCH..."
gh pr create --repo "$REPO" --base "$DOGFOODING_BRANCH" --head "$SYNC_BRANCH" \
  --title "Sync dogfooding with develop" \
  --body "$pr_body"

echo ""
echo "Done. Review and merge the PR above to complete the sync."
