# Dogfooding scripts

`dogfooding` is a long-lived branch where features that aren't ready for `develop` yet are combined and published as internal builds for Shopist and the Datadog mobile app. These scripts are the only supported way to bring changes into it, apart from reverting a feature (see [Remove a single feature](#remove-a-single-feature)).

| Script | What it does |
|---|---|
| `feature.sh <branch>` | Brings a feature branch into `dogfooding` as a single squash commit. |
| `sync.sh` | Brings `dogfooding` up to date with `develop`, keeping the dogfooded features. |
| `reset.sh` | Makes `dogfooding` exactly match `develop` again, dropping all dogfooded features. |

None of them change `dogfooding` directly: each one pushes a side branch and opens a PR against `dogfooding`, which someone has to review and merge. They work in a temporary worktree (or don't check anything out at all), so your current checkout and uncommitted changes are never touched.

Requirements: `git`, and the [GitHub CLI](https://cli.github.com/) (`gh`) logged in with push access to `DataDog/dd-sdk-android`. Run the scripts from the root of your `dd-sdk-android` checkout.

## Rules

- **Features only reach `dogfooding` through `feature.sh`.** Never open a PR from a feature branch straight into `dogfooding`: see [Dogfood a feature](#dogfood-a-feature).
- **Merge every PR into `dogfooding` with a merge commit**, never squash or rebase: that drops the `develop` parent of sync and reset merges (and can rewrite the dogfood commits' trailers), which the scripts rely on. The repository settings already only allow merge commits.

## Common tasks

### Dogfood a feature

```bash
./ci/scripts/dogfooding/feature.sh feature/my-feature
```

This opens a PR from `dogfood-feature/my-feature-<sha>` with one squash commit containing the whole feature. Review it and merge it with a merge commit.

Always use this script instead of merging the feature branch into `dogfooding`. A reset doesn't force-push, so a feature merged directly keeps its commits in `dogfooding`'s history even after the reset removes its changes. Git then treats it as already merged: merging it again brings nothing. When it later reaches `develop`, the next sync silently leaves it out. The script avoids this by bringing each dogfood in as a new squash commit, never the feature's own commits.

The script refuses to run if your branch is based on `develop` commits that `dogfooding` doesn't have yet. Run `sync.sh` first, merge the sync PR, then run it again.

If the feature conflicts with what's already in `dogfooding` (usually another dogfooded feature touching the same code), nothing is pushed. The temporary worktree is kept, and the script prints its path and the steps to finish: resolve the conflicts, commit with the prepared message, push, and open the PR. **Keep that commit message as is: its trailers are how the scripts track the feature.**

### Update a dogfooded feature

Push your changes to the feature branch and run the same command again. The new PR only contains what changed since the last dogfood (including deleted files), even if you rebased or force-pushed the branch in between. If it conflicts, it stops the same way as above.

### Bring `dogfooding` up to date with `develop`

```bash
./ci/scripts/dogfooding/sync.sh
```

This opens a PR from `sync-dogfooding-<sha>` with `develop`'s latest commits. It does nothing if `dogfooding` already contains `develop`.

If `develop` changed the same code as a dogfooded feature, the PR is opened anyway and GitHub marks it as having conflicts. Resolve them on the sync branch, as described in the PR body: check it out, merge `dogfooding` into it, fix the conflicts, and push to the same branch.

### When a dogfooded feature is merged into `develop`

If the version merged into `develop` is exactly the version you dogfooded, there's nothing to do: the next sync brings `develop` in and the feature's code is identical on both sides.

If you changed the feature after dogfooding it (review fixes, a rebase, removed files), `dogfooding` still has the old version. The sync brings in the new code, but anything that only existed in the old version stays in `dogfooding`: a file you deleted, a line you removed. Git has no reason to remove it, because `develop` never had it.

To clean it up:

1. Run `./ci/scripts/dogfooding/sync.sh`. Its PR lists the feature under "Dogfooded features that graduated to develop at a different commit", with the exact command to run afterwards.
2. Merge that sync PR. If it has conflicts in the feature's code, keep `develop`'s version. The build published after this merge may still contain code from the old version: to test the feature, use the one published after step 3.
3. Run the command from the PR, for example `./ci/scripts/dogfooding/feature.sh feature/my-feature --at <sha>`, and merge the PR it opens. It removes what's left of the old version, so `dogfooding` matches `develop` for that feature. `--at` is needed because the feature branch is usually deleted after merging into `develop`.

The warning only appears in that sync PR. If nobody follows it, the leftovers stay until the next reset, which also removes them (along with every other dogfooded feature, which then has to be dogfooded again).

### Start over from `develop`

```bash
./ci/scripts/dogfooding/reset.sh
```

This opens a PR from `reset-dogfooding-<dogfooding sha>-<develop sha>` whose content is exactly `develop`'s. If the reset would discard anything or there are open dogfood PRs, the script shows the following and asks for confirmation (`--force` skips this). The same lists are always in the PR body:

- What the reset discards.
- The dogfooded features that aren't in `develop` yet, mentioning their authors, with the `feature.sh` command to bring each one back. Run those commands **after** the reset PR is merged.
- Features whose status it can't determine. Check those by hand.
- Open dogfood PRs, which must be closed (deleting their branch) and re-created after the reset.

While a reset PR is open, `feature.sh` refuses to run. If `dogfooding` already matches `develop`, the script does nothing. A reset never conflicts, since it takes `develop`'s files as they are.

### Remove a single feature

Revert, in one PR against `dogfooding`, every merge of a `dogfood-<branch>-<sha>` PR for that feature since the last reset, newest first (`git revert -m 1 <merge sha>`). Or wait for the next reset.

`feature.sh` doesn't detect reverts: it still considers the feature dogfooded. To dogfood it again afterwards, use `--full`:

```bash
./ci/scripts/dogfooding/feature.sh feature/my-feature --full
```

## Publishing

Every build of `dogfooding` is published automatically by the `publish:dogfooding` job in GitLab, once the pipeline on `dogfooding` has passed. The version is `<version>-dogfood-<commit short sha>`, for example `3.15.0-dogfood-1a2b3c4d`, published to `https://binaries.ddbuild.io/dd-sdk-android/dogfood/`, which is only reachable from Datadog's network (VPN on laptops). Add that repository to the consuming app:

```kotlin
maven("https://binaries.ddbuild.io/dd-sdk-android/dogfood/") {
    content { includeGroup("com.datadoghq") }
}
```

Then point Shopist or the Datadog app at that exact version.

## When a script stops

| Message | What to do |
|---|---|
| `... is already open: <url>` | Nothing: a PR for exactly this state already exists. |
| `origin/<branch> already exists but has no open PR` | A previous PR was closed, or a run failed halfway. Check the branch, delete it with the printed command, and re-run. |
| `Couldn't list the PRs merged from <branch> into develop` | GitHub couldn't be queried (rate limit, network, authentication). Nothing was pushed: re-run once `gh` works. |
| `is based on develop commits that dogfooding doesn't have yet` | Run `sync.sh` (or merge the open sync PR), then re-run. |
| `A reset of dogfooding is pending` | Wait until the reset PR is merged, then re-run. |
| `Another dogfood PR for <branch> is still open` | Merge or close it (deleting its branch), then re-run. |
| `Conflicts while applying <branch> onto dogfooding` | See [Dogfood a feature](#dogfood-a-feature). |
| `Can't find <sha>, the previously dogfooded tip` | Your clone doesn't have the old tip, usually because someone else force-pushed the branch. Whoever still has it either re-runs the script from their clone, or pushes it (`git push origin <sha>:refs/heads/tmp-recover`). Then re-run, and delete `tmp-recover`. |
| `Branch <branch> not found on origin` | Pass the commit to dogfood with `--at <sha>`. |
| `Can't find commit <sha> locally or on origin` | Pass the full 40-character SHA to `--at`. |
| `Nothing to dogfood` | Nothing to do, `dogfooding` already has these changes. |
| `already dogfooded` | Nothing to do. If its dogfood commit was reverted, re-run with `--full`. |

## How it works

- `feature.sh` never merges a feature's own commits into `dogfooding`. Each dogfood is a single squash commit with a `Dogfood-Source: <branch>@<sha>` trailer, which records exactly what was dogfooded. Later dogfoods of the same branch only apply the difference from that commit.
- `sync.sh` doesn't merge anything locally. It pushes `develop`'s current tip as a `sync-dogfooding-<sha>` branch and opens a PR, so GitHub computes the merge and a conflicting sync still shows up as a PR to resolve. To find dogfooded features whose leftovers need cleaning up, it reads the `Dogfood-Source` trailers since the last reset and asks GitHub, for each of those branches, which PRs were merged into `develop` in this sync and at which commit. If the branch was at a different commit when its PR merged than the one dogfooded, it's flagged in the PR with the command to run.
- `reset.sh` doesn't force-push. It creates a merge commit with `dogfooding` and `develop` as parents and exactly `develop`'s files, so history is kept and the next syncs see `develop` as already merged. All three scripts find the last reset by that commit's subject, so don't change it.

## Known limits

- The scripts run locally. Running sync and reset from CI is planned for later.
