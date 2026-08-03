# Design: rum-fit Cross-Repo CI Integration

**Date:** 2026-08-03  
**Repos:** `dd-sdk-android`, `rum-sdk-integration-test-framework` (rum-fit)

---

## Goal

Enable `dd-sdk-android` to trigger Android integration tests in `rum-fit` from its own CI, in two modes:

1. **PR mode** — a manual job on any `dd-sdk-android` PR that runs rum-fit Android tests against that branch's commit.
2. **Develop mode** — after every merge to `dd-sdk-android` develop, an automatic job that runs Android tests against the new commit and, if they pass, bumps the `dd-sdk-android` submodule pointer in rum-fit via an auto-merged PR.

If rum-fit tests fail on develop, the `dd-sdk-android` develop pipeline fails (blocking publish).

---

## Context

- Both repos live on **GitHub**. CI runs on **GitLab** via GitHub → GitLab sync.
- rum-fit's Android test app uses a **Gradle composite build** (`includeBuild`) pointing at `submodules/dd-sdk-android` — the SDK is a git submodule, not a published Maven artifact.
- rum-fit's current branch is mid-migration to Bazel. All platform CI jobs (android, ios, etc.) are commented out. The Bazel `BUILD.bazel` and `rum_fit_android` macro already exist.
- rum-fit's main branch has a working (non-Bazel) `android-tests` CI job using `rum-test run android` on a macOS runner — this is the reference for the job structure.

---

## Architecture: CI API via `CI_PIPELINE_KEY` + multi-project triggers

rum-fit adopts the same `CI_PIPELINE_KEY` routing pattern already used by `dd-sdk-android`. Its top-level `.gitlab-ci.yml` routes to different pipeline files based on this variable:

```
CI_PIPELINE_KEY == null              → ci/pipelines/default-pipeline.yml
CI_PIPELINE_KEY == "android-sdk-tests" → ci/pipelines/android-sdk-tests.yml
CI_PIPELINE_KEY == "bump-android-sdk"  → ci/pipelines/bump-android-sdk.yml
```

`dd-sdk-android` uses GitLab's `trigger:` keyword to invoke rum-fit as a downstream multi-project pipeline, passing `CI_PIPELINE_KEY` and a commit SHA variable. GitLab's native trigger mechanism means variables are injected directly — no GitHub sync involved.

---

## Phase 0 (Prerequisite): Enable Android Tests in rum-fit CI via Bazel

**Repo:** `rum-sdk-integration-test-framework`

Restore the `android-tests` CI job to rum-fit's default pipeline, migrated from the main-branch `rum-test run android` approach to Bazel.

**Job structure** (in `ci/pipelines/default-pipeline.yml`, `integration` stage):

```yaml
android-tests:
  stage: integration
  when: manual
  tags: ["macos:sequoia-arm64", "specific:true"]
  timeout: 30m
  rules:
    - if: '$CI_COMMIT_BRANCH =~ /^bump\//'
      when: never
    - when: manual
  artifacts:
    when: always
    expire_in: 1 week
    paths:
      - test-reports/
  script:
    - !reference [.snippets, init]
    - export DD_TAGS="platform:android"
    - !reference [.snippets, setup-ci-visibility]
    - !reference [.snippets, install-bazel]
    - !reference [.snippets, install-android-sdk-components]
    - !reference [.snippets, write-android-dd-config]
    - bazel test //suites/datadog_rum:android_tests
        --config=ci
        --nocache_test_results
```

Bazel is not yet pre-installed on the macOS AMI runner, so an `install-bazel` snippet is added to rum-fit's `.snippets` anchor. It installs Bazelisk (pinned version) as `/usr/local/bin/bazel`; Bazelisk then reads `.bazelversion` (currently `8.7.0`) and downloads the correct Bazel binary on first run. This snippet is called in all macOS jobs that invoke `bazel`. Once the macOS CI image is updated to include Bazel, the snippet is simply removed from those jobs.

The Bazel `android_tests` target handles building the APK (via `./gradlew assembleDebug` inside the `debug_apk` genrule) and running the Python pytest suite.

The `rules:` entry skips this job on bump branch pipelines. After a bump PR merges to main, the job falls through to `when: manual` — no automatic re-run, no loop. The `Rum-Fit-Pipeline:` trailer remains in the bump commit message for git log readability and future extensibility, but is not wired to a CI rule.

**Restructuring:** The existing content of rum-fit's `.gitlab-ci.yml` (variables, `.snippets`, stages, `ci-image`, `web-tests`, `agentic-upgrade-tests`) moves into `ci/pipelines/default-pipeline.yml`. The root `.gitlab-ci.yml` becomes the router only.

---

## Phase 1: rum-fit CI API Pipeline Files

**Repo:** `rum-sdk-integration-test-framework`

### `ci/pipelines/android-sdk-tests.yml`

Triggered by `dd-sdk-android` PRs. Accepts variable `ANDROID_SDK_REF` (commit SHA or branch name).

```yaml
stages:
  - test

android-sdk-tests:
  stage: test
  tags: ["macos:sequoia-arm64", "specific:true"]
  timeout: 30m
  artifacts:
    when: always
    expire_in: 1 week
    paths:
      - test-reports/
  script:
    - !reference [.snippets, init]
    - git -C submodules/dd-sdk-android fetch --depth=1 origin $ANDROID_SDK_REF
    - git -C submodules/dd-sdk-android checkout FETCH_HEAD
    - export DD_TAGS="platform:android"
    - !reference [.snippets, setup-ci-visibility]
    - !reference [.snippets, install-android-sdk-components]
    - !reference [.snippets, write-android-dd-config]
    - bazel test //suites/datadog_rum:android_tests
        --config=ci
        --nocache_test_results
```

Read-only: no commits or pushes. The submodule override is ephemeral to the pipeline run.

### `ci/pipelines/bump-android-sdk.yml`

Triggered by `dd-sdk-android` develop merges. Accepts variable `ANDROID_SDK_COMMIT` (always a full commit SHA).

Two sequential jobs in two stages (`test`, `bump`):

**Job 1 — `test`** (`tags: ["macos:sequoia-arm64", "specific:true"]`): identical flow to `android-sdk-tests.yml` but uses `$ANDROID_SDK_COMMIT` as the ref.

**Job 2 — `bump`** (`tags: ["arch:amd64"]` — no macOS needed, just git + Python; `needs: [test]`):

```yaml
bump:
  stage: bump
  tags: ["arch:amd64"]
  needs: [test]
  id_tokens:
    DDOCTOSTS_ID_TOKEN:
      aud: dd-octo-sts
  script:
    - export GITHUB_TOKEN=$(dd-octo-sts token
        --scope DataDog/rum-sdk-integration-test-framework
        --policy all.gitlab.pr)
    - bazel run //tools/ci:bump_android_sdk --
        --commit=$ANDROID_SDK_COMMIT
        --github-token=$GITHUB_TOKEN
```

The Python script (`tools/ci/bump_android_sdk.py`, `py_binary` Bazel target) handles:
1. Updating `submodules/dd-sdk-android` to `$ANDROID_SDK_COMMIT`
2. Creating a git commit with message:
   ```
   bump: android sdk to <short-sha>

   Rum-Fit-Pipeline: bump-android-sdk
   ```
3. Pushing branch `bump/android-sdk-<short-sha>` to GitHub
4. Creating a GitHub PR targeting `main` via GitHub API
5. Immediately merging the PR via GitHub API

The `Rum-Fit-Pipeline: bump-android-sdk` trailer in the commit message (combined with the `bump/android-sdk-*` branch name) causes rum-fit's default pipeline to skip the `android-tests` job on that branch — see Phase 0 `rules:`.

**GitHub auth:** `dd-octo-sts` with `--scope DataDog/rum-sdk-integration-test-framework --policy all.gitlab.pr`. Requires the `all.gitlab.pr` policy to be configured for this repo in dd-octo-sts (infra/ops step). No long-lived PATs stored as CI variables.

---

## Phase 2: dd-sdk-android Integration

**Repo:** `dd-sdk-android`

Two jobs added to `ci/pipelines/default-pipeline.yml`, `test-pyramid` stage.

### Manual PR job

```yaml
rum-fit:android-tests:
  stage: test-pyramid
  when: manual
  allow_failure: false
  trigger:
    project: DataDog/rum-sdk-integration-test-framework
    branch: main
    strategy: depend
  variables:
    CI_PIPELINE_KEY: android-sdk-tests
    ANDROID_SDK_REF: $CI_COMMIT_SHA
```

`strategy: depend` means once triggered, the PR pipeline waits for the rum-fit child pipeline and reflects its status. `allow_failure: false` means a triggered-and-failed run marks the PR pipeline red. If never triggered, the job is skipped (pipeline stays green).

### Develop post-merge job

```yaml
rum-fit:bump-android-sdk:
  stage: test-pyramid
  only: [ develop ]
  trigger:
    project: DataDog/rum-sdk-integration-test-framework
    branch: main
    strategy: depend
  variables:
    CI_PIPELINE_KEY: bump-android-sdk
    ANDROID_SDK_COMMIT: $CI_COMMIT_SHA
```

Automatic on develop. Blocks the `publish` stage (snapshots, sample app) until rum-fit's pipeline completes. If tests fail or the bump fails, the develop pipeline fails. This is intentional — it enforces that develop stays in a state where rum-fit tests pass.

---

## Data Flow Summary

### PR flow
```
dd-sdk-android PR push
  → GitLab: dd-sdk-android pipeline (rum-fit:android-tests = manual)
  → developer triggers manually
  → GitLab triggers rum-fit android-sdk-tests pipeline
      (CI_PIPELINE_KEY=android-sdk-tests, ANDROID_SDK_REF=<sha>)
  → rum-fit: checkout SDK at <sha>, run bazel android tests
  → result reflected back in dd-sdk-android PR pipeline
```

### Develop flow
```
dd-sdk-android commit merges to develop
  → GitHub → GitLab sync → dd-sdk-android develop pipeline
  → rum-fit:bump-android-sdk triggers automatically (stage: test-pyramid)
  → GitLab triggers rum-fit bump-android-sdk pipeline
      (CI_PIPELINE_KEY=bump-android-sdk, ANDROID_SDK_COMMIT=<sha>)
  → rum-fit: checkout SDK at <sha>, run bazel android tests
      [FAIL] → dd-sdk-android develop pipeline fails, publish blocked
      [PASS] → bump job runs:
          → dd-octo-sts GitHub token
          → bazel run //tools/ci:bump_android_sdk
          → commit "bump: android sdk to <short-sha>\n\nRum-Fit-Pipeline: bump-android-sdk"
          → push branch bump/android-sdk-<short-sha> to GitHub
          → create + auto-merge PR to rum-fit main
  → rum-fit main receives bump commit
  → GitHub → GitLab sync → rum-fit default pipeline triggered
      → android-tests job: branch is main → falls through to when: manual (not triggered automatically)
      → other jobs (lint, etc.) can run normally
  → dd-sdk-android develop pipeline: publish stage proceeds
```

---

## Files Changed

### `rum-sdk-integration-test-framework`

| File | Change |
|------|--------|
| `.gitlab-ci.yml` | Becomes pipeline router only (`CI_PIPELINE_KEY` includes) |
| `ci/pipelines/default-pipeline.yml` | New — existing CI content + `android-tests` Bazel job |
| `ci/pipelines/android-sdk-tests.yml` | New — parameterized android tests pipeline |
| `ci/pipelines/bump-android-sdk.yml` | New — test + bump pipeline |
| `tools/ci/bump_android_sdk.py` | New — Python bump script |
| `tools/ci/BUILD.bazel` | New — `py_binary` target for bump script |

### `dd-sdk-android`

| File | Change |
|------|--------|
| `ci/pipelines/default-pipeline.yml` | Add `rum-fit:android-tests` and `rum-fit:bump-android-sdk` jobs |

---

## Open Items / Infra Prerequisites

1. **dd-octo-sts policy**: `all.gitlab.pr` is declared in the **target repo's** dd-octo-sts configuration (as in `DataDog/datadog-android`, `DataDog/shopist-android`, etc.). The same policy must be explicitly added to `DataDog/rum-sdk-integration-test-framework`'s dd-octo-sts config to allow the bump job to push branches and create PRs. This is a real infra step — it is not inherited automatically. Coordinate with infra/ops.
2. **rum-fit default branch**: Confirmed as `main` (verified from `git remote show origin`).
3. **`.bazelrc` `ci` config**: Confirm that `--config=ci` exists in rum-fit's `.bazelrc` (already used by `web-tests` job).
