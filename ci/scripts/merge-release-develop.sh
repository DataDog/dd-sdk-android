#!/usr/bin/env bash
#
# Opens a PR merging the release commit for the provided tag back into develop.
# This always conflicts on the version file (develop already carries the next
# Snapshot version, the release commit has the just-cut Release version), so we
# merge develop into a copy of the release commit locally and keep develop's version.
# If anything else also conflicts, we open a PR from the unmerged release commit
# instead and let a developer resolve it manually.

set -euo pipefail

if [ $# -lt 1 ]; then
  echo "usage: $0 <tag>" >&2
  exit 1
fi

tag="$1"
branch_name="release-merge-back/$tag-into-develop"
version_file="build-logic/src/main/kotlin/com/datadog/gradle/config/AndroidConfig.kt"

if gh pr list --repo DataDog/dd-sdk-android --head "$branch_name" --base develop --state all --json number -q '.[0]' | grep -q .; then
  echo "PR into develop already exists for $tag, skipping"
  exit 0
fi

# Shared by both the conflict-fallback path and the clean-merge path below.
push_branch() {
  echo "Pushing $branch_name..."
  if [ -n "${CI:-}" ]; then
    commit-headless push -T DataDog/dd-sdk-android \
      --branch "$branch_name" \
      --head-sha "$(git rev-parse "refs/tags/$tag")" \
      --create-branch \
      "$(git rev-parse HEAD)"
  else
    git push origin "HEAD:refs/heads/$branch_name"
  fi
}

open_manual_resolution_pr() {
  push_branch
  gh pr create --repo DataDog/dd-sdk-android --base develop --head "$branch_name" \
    --title "Merge release/$tag into develop" \
    --body "Merge release/$tag into develop.

:warning: This has conflicts beyond the routine version-file one, so nothing was resolved automatically. To fix locally:

- \`git fetch origin develop $branch_name\`
- \`git checkout -B $branch_name origin/$branch_name\`
- \`git merge origin/develop\`
- \`git checkout --theirs $version_file && git add $version_file\`
- resolve the remaining conflicts
- \`git commit -m \"Merge branch 'develop' into '$branch_name'\"\`
- \`git push --force origin $branch_name\` to update this PR"
}

echo "Fetching develop and tag $tag..."
if [ "$(git rev-parse --is-shallow-repository)" = "true" ]; then
  # CI uses shallow clones; unshallow so the merge below has enough history
  # to find a common ancestor.
  git fetch --unshallow origin
fi
git fetch origin "refs/heads/develop:refs/remotes/origin/develop" "refs/tags/$tag:refs/tags/$tag"

repo_dir=$(pwd)

if [ -n "${CI:-}" ]; then
  # CI's checkout is single-use and thrown away after the job, so it's fine
  # to check the branch out in place.
  git checkout -b "$branch_name" "$tag"
else
  # Do the merge in a separate worktree so this never checks out a branch (or
  # touches uncommitted work) in whatever repo the script happens to be run
  # from - safe to run locally in your own dd-sdk-android checkout.
  worktree_dir=$(mktemp -d)
  trap 'cd "$repo_dir"; git worktree remove --force "$worktree_dir" >/dev/null 2>&1 || true' EXIT
  git worktree add --quiet -b "$branch_name" "$worktree_dir" "$tag"
  # Only now do we know this run created $branch_name, rather than it
  # pre-existing from something else - safe to delete it on exit. Removing
  # the worktree above doesn't delete the branch it checked out, so without
  # this a retry after a failed run would fail at `git worktree add -b` with
  # "branch already exists".
  trap 'cd "$repo_dir"; git worktree remove --force "$worktree_dir" >/dev/null 2>&1 || true; git branch -D "$branch_name" >/dev/null 2>&1 || true' EXIT
  cd "$worktree_dir"
fi

echo "Merging develop into a local copy of tag $tag..."
set +e
merge_output=$(git merge origin/develop --no-commit --no-ff 2>&1)
merge_status=$?
set -e
echo "$merge_output"

if [ $merge_status -eq 0 ]; then
  # Every version bump is its own isolated one-line commit (nothing else in
  # this file changes alongside it), so develop and the release tag should
  # always disagree on VERSION here. A clean merge means that assumption
  # broke - don't silently ship whichever version happened to win.
  # (--no-commit skips MERGE_HEAD entirely for a trivial "Already up to
  # date" merge, so there's nothing to abort in that case.)
  git merge --abort 2>/dev/null || true
  echo "Expected a conflict on $version_file but the merge succeeded cleanly, aborting" >&2
  exit 1
fi

conflicting_files=$(git diff --name-only --diff-filter=U)
if [ -z "$conflicting_files" ]; then
  # Merge failed but left no conflict markers to resolve - not the routine
  # version-file conflict, something else went wrong. Fail loudly instead
  # of silently treating it as resolved.
  git merge --abort
  echo "Merge failed without leaving any conflicts to resolve, aborting:" >&2
  echo "$merge_output" >&2
  exit 1
fi

other_conflicts=$(echo "$conflicting_files" | grep -v -x "$version_file" || true)
if [ -n "$other_conflicts" ]; then
  git merge --abort
  echo "Conflicts beyond $version_file, letting GitHub report the merge as unresolved instead of committing over them"
  open_manual_resolution_pr
  exit 0
fi

# $version_file is the only conflicting file - confirm it's exactly the
# routine single-hunk VERSION disagreement before resolving it automatically.
conflict_hunks=$(grep -c '^<<<<<<< ' "$version_file" || true)
conflict_block=$(sed -n '/^<<<<<<< /,/^>>>>>>> /p' "$version_file")
if [ "$conflict_hunks" -ne 1 ] || ! echo "$conflict_block" | grep -qE 'val VERSION = Version\([^)]*\)'; then
  git merge --abort
  echo "Conflict in $version_file isn't the routine VERSION-line one, letting GitHub report it as unresolved"
  open_manual_resolution_pr
  exit 0
fi

echo "Keeping develop's version in $version_file"
# The single-hunk check above already confirmed the only difference between
# the two sides of this file is the VERSION line, so develop's copy of the
# whole file is exactly what we want here.
git checkout --theirs "$version_file"
git add "$version_file"

if [ -n "${CI:-}" ]; then
  # CI has no git identity configured by default.
  git config user.name "dd-octo-sts"
  git config user.email "dd-octo-sts@datadoghq.com"
fi

git commit -m "Merge branch 'develop' into '$branch_name'"

if git diff --quiet origin/develop HEAD; then
  # develop already carries everything from the release tag.
  echo "Resulting merge is identical to develop, nothing to merge back"
  exit 0
fi

push_branch

echo "Opening PR into develop..."
gh pr create --repo DataDog/dd-sdk-android --base develop --head "$branch_name" \
  --title "Merge release/$tag into develop" \
  --body "Merge release/$tag into develop."
